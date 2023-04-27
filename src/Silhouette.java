import org.usb4java.LibUsbException;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

import static javax.swing.JOptionPane.*;

/**
 * Experimental code to send cut, or draw commands to a Silhouette device.  Currently the code only support the
 * devices's standard size mat.  Future revisions will add the ability to select other mat sizes.
 * This code has only been tested with the Curio.  Other Silhouette devices can be selected in the Settings menu,
 * but the code has not yet been tested against them.
 */

class Silhouette implements LaserCut.OutputDevice {
  private static final DecimalFormat      df = new DecimalFormat("0.#");
  private static final double             SCALE = 508;   // Silhouette unit
  private static final List<Cutter>       cutters = new LinkedList<>();
  private static final Map<String,Cutter> devices = new HashMap<>();
  private static String                   deviceName = "Curio";
  private static int                      action, pen, pens, speed, pressure, media, landscape;
  private final LaserCut                  laserCut;
  private final String                    dUnits;
  private Rectangle2D.Double              workspaceSize;
  private USBIO                           usb;
  private final boolean                   simulate = false;

  static class Cutter {
    String        name;
    final short   vend = (short) 0x0B4D;
    short         prod;
    byte          intFace = 0;
    byte          outEnd = (byte) 0x01;
    byte          inEnd = (byte) 0x82;
    int           pens;
    Mat[]         mats;

    /**
     * Silhouette, or Graphtec device Id and capabilities
     * @param name Product name
     * @param prod USB Product Id
     * @param pens Number of pens/tools supported
     * @param mats Array of standard mat sizes available
     */
    Cutter (String name, short prod, int pens, Mat[] mats) {
      this.name = name;
      this.prod = prod;
      devices.put(name, this);
      this.pens = pens;
      this.mats = mats;
    }

    Rectangle2D.Double getWorkspaceSize (int idx, boolean landscape) {
      Mat mat = mats[idx];
      if (landscape) {
        return new Rectangle2D.Double(0, 0, mat.wid, mat.hyt);          // Size in Landscape mode
      } else {
        return new Rectangle2D.Double(0, 0, mat.hyt, mat.wid);          // Size in Portrait mode
      }
    }
  }

  static class Mat {
    private final double  wid;
    private final double hyt;

    Mat (double wid, double hyt) {
      this.wid = wid;
      this.hyt = hyt;
    }
  }

  static {
    //                      Name                  Prod Id            Standard Mat Sizes
    cutters.add(new Cutter("Curio",       (short) 0x112C, 2, new Mat[] {new Mat(8.5, 6.0), new Mat(8.5, 12.0)}));       // Tested
    cutters.add(new Cutter("Cameo",       (short) 0x1121, 1, new Mat[] {new Mat(12.0, 12.0), new Mat(12.0, 24.0)}));
    cutters.add(new Cutter("Cameo 2",     (short) 0x112B, 1, new Mat[] {new Mat(12.0, 12.0), new Mat(12.0, 24.0)}));
    cutters.add(new Cutter("Cameo 3",     (short) 0x112F, 2, new Mat[] {new Mat(12.0, 12.0), new Mat(12.0, 24.0)}));    // Tested
    cutters.add(new Cutter("Portrait",    (short) 0x1123, 1, new Mat[] {new Mat(8.0, 12.0)}));
    cutters.add(new Cutter("Portrait 2",  (short) 0x1132, 1, new Mat[] {new Mat(8.0, 12.0)}));
    cutters.add(new Cutter("SD-1",        (short) 0x111C, 1, new Mat[] {new Mat(7.4, 10.0)}));
    cutters.add(new Cutter("SD-2",        (short) 0x111D, 1, new Mat[] {new Mat(7.4, 10.0)}));
    cutters.add(new Cutter("CC200-20",    (short) 0x110A, 1, new Mat[] {new Mat(7.4, 10.0)}));
    cutters.add(new Cutter("CC300-20",    (short) 0x111A, 1, new Mat[] {new Mat(7.4, 10.0)}));
  }

  private String getDeviceNames () {
    StringBuilder buf = new StringBuilder();
    for (Cutter device : cutters) {
      buf.append(":");
      buf.append(device.name);
    }
    return buf.toString();
  }

  Silhouette (LaserCut laserCut) {
    this.laserCut = laserCut;
    this.dUnits = laserCut.displayUnits;
    // Setup currently selected, or default Silhouette Device and its parameters
    deviceName = getString("deviceName", cutters.get(0).name);
    if (!devices.containsKey(deviceName)) {
      deviceName = cutters.get(0).name;
    }
    Cutter cutter = devices.get(deviceName);
    landscape = getInt("landscape", 1);                 // Set Orientation (0 = Portrait, 1 = Landscape)
    workspaceSize = cutter.getWorkspaceSize(0, landscape == 1);
    laserCut.updateWorkspace();
    pens = cutter.pens;                                 // Number of pens supported by selected device
    action = getInt("action", 0);                       // Set Offset for Tool 1 (18 = cutter, 0 = pen)
    media = getInt("media", 300);                       // 300 = Custom Media
    pen = getInt("pen", Math.min(1, pens));             // 1 selects left pen, 2 selects right pen
    speed = getInt("speed", 5);                         // Drawing speed (value times 10 is centimeters/second)
    pressure = getInt("pressure", 10);                  // Tool pressure (value times 7 is grams of force, or 7-230 grams)
  }

  // Implement for GRBLBase to define Preferences prefix, such as "mini.laser."
  String getPrefix () {
    return "silhouette.";
  }

  // Implement for LaserCut.OutputDevice
  public String getName () {
    return "Silhouette";
  }

  // Implement for LaserCut.OutputDevice
  public Rectangle2D.Double getWorkspaceSize () {
    return workspaceSize;
  }

  // Implement for LaserCut.OutputDevice
  public double getZoomFactor () {
    return 1.0;
  }

  private int getInt(String name, int def) {
    return laserCut.prefs.getInt(getPrefix() + name, def);
  }

  private void putInt (String name, int value) {
    laserCut.prefs.putInt(getPrefix() + name, value);
  }

  private String getString(String name, String def) {
    return laserCut.prefs.get(getPrefix() + name, def);
  }

  private void putString (String name, String value) {
    laserCut.prefs.put(getPrefix() + name, value);
  }

  public JMenu getDeviceMenu () {
    JMenu silhouetteMenu = new JMenu(getName());
    // Add "Send to Silhouette" Submenu Item
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
    JTextField tf = new JTextField("1", 4);
    panel.add(tf);
    JMenuItem sendToSilhouette = new JMenuItem("Send Job to " + getName());
    sendToSilhouette.addActionListener((ActionEvent ev) -> {
      if (laserCut.showWarningDialog("Press OK to Send Job to " + getName())) {
        // Convert Shape objects into Silhouette drawing commands
        List<String> cmds = new ArrayList<>();
        if (landscape == 1) {
          cmds.add("FN0");                                          // Set Landscape
          cmds.add("TB50,0");                                       //  "     "
        } else {
          cmds.add("FN0");                                          // Set Portrait
          cmds.add("TB50,1");                                       //  "     "
        }
        cmds.add("FC" + action);                                    // Set Offset for Tool 1 (18 = cutter, 0 = pen)
        cmds.add("FW" + Math.min(Math.max(media, 100), 300));       // 300 = Custom Media
        cmds.add("FX" + Math.min(Math.max(pressure, 1), 33));       // Tool pressure (value times 7 is grams of force, or 7-230 grams)
        cmds.add("!" + Math.min(Math.max(speed, 1), 10));           // Drawing speed (value times 10 is centimeters/second)
        cmds.add("J" + Math.min(pen, pens));                        // 1 selects left pen, 2 selects right pen
        List<CADShape> cadShapes = laserCut.surface.selectLaserItems(true, false);
        for (CADShape cadShape : cadShapes) {
          if (!(cadShape instanceof CADRasterImage)) {
            Shape shape = cadShape.getWorkspaceTranslatedShape();
            cmds.addAll(shapeToSilhouette(shape));
          }
        }
        if (simulate) {
          // do nothing;
        } else if (deviceName != null) {
          Cutter dev = devices.get(deviceName);
          new SilhouetteSender(dev, cmds.toArray(new String[0]));
        } else {
          showMessageDialog(laserCut, "No Silhouette Device Selected", "Error", PLAIN_MESSAGE);
        }
      }
    });
    silhouetteMenu.add(sendToSilhouette);
    // Add "Silhouette Settings" Submenu Item
    JMenuItem silhouetteSettings = new JMenuItem(getName() + " Settings");
    silhouetteSettings.addActionListener(ev -> {
      List<ParameterDialog.ParmItem> parms = new ArrayList<>();
      ParameterDialog.ParmItem parm0, parm3;
      parms.add(parm0 = new ParameterDialog.ParmItem("Device" + getDeviceNames(), deviceName));       // Device
      parms.add(new ParameterDialog.ParmItem("Orientation:Portrait|0:Landscape|1", landscape));       // Orientation
      parms.add(new ParameterDialog.ParmItem("Action:Draw|0:Cut|18", action));                        // Action
      parms.add(parm3 = new ParameterDialog.ParmItem("Pen:Left|1:Right|2", Math.min(pen, pens)));     // Pen
      parms.add(new ParameterDialog.ParmItem("Speed[1-10]", speed));                                  // Speed
      parms.add(new ParameterDialog.ParmItem("Pressure[1-33]", pressure));                            // Pressure
      ParameterDialog.ParmItem[] parmSet = parms.toArray(new ParameterDialog.ParmItem[0]);
      parm3.setEnabled(devices.get(deviceName).pens > 1);
      parm0.addParmListener(parm -> {
        String device = (String) ((JComboBox<?>) parm.field).getSelectedItem();
        Cutter cutter = devices.get(device);
        parmSet[3].setEnabled(cutter.pens > 1);
      });
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, laserCut)) {
        int idx = 0;
        deviceName = (String) parmSet[idx++].value;                                                   // Device
        putString("deviceName", deviceName);
        putInt("landscape", landscape = Integer.parseInt((String) parmSet[idx++].value));             // Orientation
        Cutter cutter = devices.get(deviceName);
        workspaceSize = cutter.getWorkspaceSize(0, landscape == 1);
        laserCut.updateWorkspace();
        pens = cutter.pens;
        putInt("action", action = Integer.parseInt((String) parmSet[idx++].value));                   // Action
        putInt("pen", pen = Integer.parseInt((String) parmSet[idx++].value));                         // Pen
        putInt("speed", speed = (Integer) parmSet[idx++].value);                                      // Speed
        putInt("pressure", pressure = (Integer) parmSet[idx++].value);                                // Pressure
      }
    });
    silhouetteMenu.add(silhouetteSettings);
    return silhouetteMenu;
  }

  /**
   * Convert a Shape object into the Silhouette commands needed to draw them
   * Note: each command must be terminated by 0x03 byte ("\u0003")
   * @param shape Shape object to convert
   * @return List of Silhouette command Strings
   */
  private List<String> shapeToSilhouette (Shape shape) {
    AffineTransform at = new AffineTransform();
    at.scale(SCALE, SCALE);
    List<String> cmds = new ArrayList<>();
    double firstX = 0;
    double firstY = 0;
    double lastX = 0;
    double lastY = 0;
    boolean hasFirst = false;
    // Use PathIterator to generate sequence of line or curve segments
    PathIterator pi = shape.getPathIterator(at);
    while (!pi.isDone()) {
      double[] coords = new double[6];      // p1.x, p1.y, p2.x, p2.y, p3.x, p3.y
      int type = pi.currentSegment(coords);
      // Reverse x/y values for Silhouette
      coords = new double[] {coords[1], coords[0], coords[3], coords[2], coords[5], coords[4]};
      int lastCmdsSize = cmds.size();
      switch (type) {
        case PathIterator.SEG_MOVETO:   // 0
          // Move to start of a line, or Bezier curve segment
          if (lastX != coords[0] || lastY != coords[1]) {
            cmds.add("M" + df.format(lastX = coords[0]) + "," + df.format(lastY = coords[1]));
          }
          if (!hasFirst) {
            firstX = coords[0];
            firstY = coords[1];
            hasFirst = true;
          }
          break;
        case PathIterator.SEG_LINETO:   // 1
          // Draw line from previous point to new point
          cmds.add("D" + df.format(coords[0]) + "," + df.format(coords[1]));
          lastX = coords[0];
          lastY = coords[1];
          break;
        case PathIterator.SEG_QUADTO:   // 2
          // Convert 3 point, quadratic Bezier curve into 4 point, cubic Bezier curve
          coords = new double[] {
              lastX + (2.0 * (coords[0] - lastX) / 3.0),
              lastY + (2.0 * (coords[1] - lastY) / 3.0),
              coords[2] + (2.0 * (coords[0] - coords[2]) / 3.0),
              coords[3] + (2.0 * (coords[1] - coords[3]) / 3.0),
              coords[2],
              coords[3]
          };
          // Fall through to draw converted Bezier curve
        case PathIterator.SEG_CUBICTO:  // 3
          // Write 4 point, cubic Bezier curve
            // Decompose 4 point, cubic Bezier curve into line segments
            Point2D.Double[] tmp = new Point2D.Double[4];
            Point2D.Double[] cControl = {
              new Point2D.Double(lastX, lastY),                               // p1 (start)
              new Point2D.Double(coords[0], coords[1]),                       // p2 (control point)
              new Point2D.Double(coords[2], coords[3]),                       // p3 (control point)
              new Point2D.Double(coords[4], coords[5])};                      // p4 (end)
            double dist = cControl[0].distance(cControl[3]);
            // Kludge: to increase number of line segments for larger curves
            int segments = 16 * Math.max((int) Math.sqrt(dist / 100), 1);
            if (false) {
              System.out.printf("        dist: %.2f, segments: %d\n", dist, segments);
            }
            for (int ii = 0; ii < segments; ii++) {
              double t = ((double) ii) / (segments - 1);
              for (int jj = 0; jj < cControl.length; jj++) {
                tmp[jj] = new Point2D.Double(cControl[jj].x, cControl[jj].y);
              }
              for (int jj = 0; jj < cControl.length - 1; jj++) {
                for (int kk = 0; kk < cControl.length - 1; kk++) {
                  // Subdivide points
                  tmp[kk].x -= (tmp[kk].x - tmp[kk + 1].x) * t;
                  tmp[kk].y -= (tmp[kk].y - tmp[kk + 1].y) * t;
                }
              }
              if (ii == 0) {
                if (lastX != tmp[0].x || lastY != tmp[0].y) {
                  // move to lastX, lastY
                  cmds.add("M" + df.format(lastX = tmp[0].x) + "," + df.format(lastY = tmp[0].y));
                }
              } else {
                // line to lastX, lastY
                cmds.add("D" + df.format(lastX = tmp[0].x) + "," + df.format(lastY = tmp[0].y));
              }
            }
          break;
        case PathIterator.SEG_CLOSE:    // 4
          // Close and write out the current curve
          if (lastX != firstX || lastY != firstY) {
            cmds.add("D" + df.format(firstX) + "," + df.format(firstY));
          }
          hasFirst = false;
          break;
      }
      if (simulate) {
        for (int ii = lastCmdsSize; ii < cmds.size(); ii++) {
          System.out.println(cmds.get(ii));
        }
      }
      pi.next();
    }
    return cmds;
  }

  class SilhouetteSender extends JDialog implements Runnable {
    private final Cutter          device;
    private final String[]        cmds;
    private final JTextArea       monitor;
    private final JProgressBar    progress;
    private boolean         doAbort;

    SilhouetteSender (Cutter device, String[] cmds) {
      this.device = device;
      setTitle(Silhouette.this.getName() + " Monitor");
      setLocationRelativeTo(laserCut);
      add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(cmds.length);
      JScrollPane sPane = new JScrollPane(monitor = new JTextArea());
      monitor.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) monitor.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      monitor.setEditable(false);
      add(sPane, BorderLayout.CENTER);
      JButton abort = new JButton("Abort Job");
      add(abort, BorderLayout.SOUTH);
      abort.addActionListener(ev -> doAbort = true);
      Rectangle loc = getBounds();
      setSize(500, 300);
      setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 150);
      validate();
      setVisible(true);
      this.cmds = cmds;
      new Thread(this).start();
    }

    public void run () {
      try {
        usb = new USBIO(device.vend, device.prod, device.intFace, device.outEnd, device.inEnd);
        // Gobble up any leftover responses from a prior command sequence, if any
        while (usb.receive().length > 0) {
          try {
            Thread.sleep(1);
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }
        sendCmd("FQ0");
        if (getResponse().length() == 0) {
          showMessageDialog(laserCut, "Empty Tray", "Error", ERROR_MESSAGE);
          return;
        }
        monitor.append(getVersionString() + "\n");
        Rectangle2D.Double dim = getWorkspaceSize();
        monitor.append("Workspace: " + df.format(dim.width) + " x " + df.format(dim.height) + "\n");
        initDevice();
        moveHome();
        StringBuilder buf = new StringBuilder();
        for (int ii = 0; (ii < cmds.length) && !doAbort; ii++) {
          String cmd = cmds[ii].trim();
          monitor.append(cmd + '\n');
          // Ignore blank lines
          if (cmd.length() > 0) {
            if (buf.length() + cmd.length() + 1 > 100) {
              usb.send(buf.toString().getBytes());
              buf.setLength(0);
            } else {
              buf.append(cmd).append("\u0003");
            }
          }
          progress.setValue(ii);
        }
        if (doAbort) {
          initDevice();
        } else {
          // Flush through any remaining commands
          if (buf.length() > 0) {
            usb.send(buf.toString().getBytes());
          }
        }
        moveHome();
      } catch (LibUsbException ex) {
        showMessageDialog(laserCut, ex.getMessage(), "Error", ERROR_MESSAGE);
      } catch (Exception ex) {
        ex.printStackTrace();
      } finally {
        if (usb != null) {
          usb.close();
        }
      }
      setVisible(false);
      dispose();
    }
  }

  private void initDevice () {
    usb.send(new byte[]{0x1B, 0x04});               // Initialize Device
  }

  /**
   * Returns carriage to home position
   */
  private void moveHome () {
    sendCmd("!10");
    sendCmd("H");
    doWait();
  }

  private Rectangle2D.Double getWorkArea () {
    sendCmd("[");                                   // Read Lower Left
    String[] v1 = getResponse().split(",");
    double x = v1.length == 2 ? Double.parseDouble(v1[1].trim()) : 0;
    double y = v1.length == 2 ? Double.parseDouble(v1[0].trim()) : 0;
    sendCmd("U");                                   // Read Upper Right
    String[] v2 = getResponse().split(",");
    double wid = v2.length == 2 ? Double.parseDouble(v2[1].trim()) : 0;
    double hyt = v2.length == 2 ? Double.parseDouble(v2[0].trim()) : 0;
    // Note: reverse X/Y axes so tool head moves on X axis
    return new Rectangle2D.Double(x, y, wid, hyt);
  }

  private void sendCmd (String cmd) {
    usb.send((cmd + "\u0003").getBytes());
  }

  /**
   * Query device for version string
   * @return version string, such as "CURIO V1.20"
   */
  private String getVersionString() {
    sendCmd("FG");
    return getResponse();
  }

  private String getResponse () {
    byte[] data = usb.receive();
    if (data.length > 0) {
      return (new String(data)).substring(0, data.length - 1);
    }
    return "";
  }

  /**
   * Used by doWait() to get status of plotter
   * @return '1' if plotter is executing a move or draw command
   */
  private byte getStatus () {
    usb.send(new byte[]{0x1B, 0x05});               // Status Request
    byte[] data;
    do {
      data = usb.receive();
    } while (data.length == 0);
    return data[0];
  }

  /**
   * Waits until move or draw command is complete and motion is stopped
   */
  private void doWait () {
    while (getStatus() == '1') {
      try {
        Thread.sleep(1);
      } catch (InterruptedException ex) {
        ex.printStackTrace();
      }
    }
  }

  // Implemented for LaserCut.OutputDevice
  public void closeDevice () {
  }
}
