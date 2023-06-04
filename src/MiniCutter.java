import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static javax.swing.JOptionPane.*;

/**
 *  This code implements a driver for TeensyCNC2 (https://github.com/wholder/TeensyCNC2)
 *  which is derived from TeensyCNC by Matt Williams (https://github.com/seishuku/TeensyCNC)
 *
 *  G Codes Implemented by TeensyCNC:
 *    G00 - Rapid positioning
 *    G01 - Linear Interpolation
 *    G02 - Circular interpolation, clockwise
 *    G03 - Circular interpolation, counterclockwise
 *    G04 - Set Dwell (in seconds)
 *    G20 - Set Units to Inches
 *    G21 - Set Units to Millimeters
 *    G28 - Go home
 *    G30 - Go home in two steps
 *    G90 - Set Absolute Mode (default)
 *    G91 - Set Incremental Mode
 *    G92 - Set current position as home
 *
 *  M Codes Implemented by TeensyCNC:
 *    M2  - End of program
 *    M3  - Tool Down
 *    M4  - Tool Down
 *    M5  - Tool Up
 *    M7  - Tool Down
 *    M8  - Tool Up
 *    M30 - End of program
 *    M39 - Load Paper
 *    M40 - Eject Paper
 *
 *  Special M Codes
 *    M112 - Emergency stop / Enter bootloader
 *    M115 - Prints Build Info
 *
 *  Supported Parameters
 *    Fn.n - Feed Rate
 *    Pn.n - Pause in seconds (used by G4 command)
 *    Xn.n - X Coord
 *    Yn.n - Y Coord
 *    Zn.n - Z Coord
 *    In.n - I Coord (arc center X for arc segments)
 *    Jn.n - J Coord (arc center Y for arc segments)
 *
 *  Responses (terminated by "\r\n")
 *    ok        Normal response
 *    cancelled Job cancelled
 *    huh? G    Unknown "Gnn" code
 *    huh? M    Unknown "Mnn" code
 *    * --      Info Response
 *
 *  Teeensy 3.2 VID = 0x16C0, PID = 0x0483, JSSC Port Id is "/dev/cu.usbmodem4201"
 */

class MiniCutter implements LaserCut.OutputDevice {
  private static final int      MINI_PAPER_CUTTER_DEFAULT_SPEED = 90;           // Max feed rate (inches/min)
  private static final int      MINI_PAPER_CUTTER_MAX_SPEED = 200;              // Max feed rate (inches/min)
  private static final boolean  INVERT_Y_AXIS = false;
  private final JSSCPort        jPort;
  private final LaserCut        laserCut;
  private final Preferences     prefs;

  MiniCutter (LaserCut laserCut, Preferences prefs) {
    this.laserCut = laserCut;
    this.prefs = prefs;
    jPort = new JSSCPort(getPrefix(), prefs);
  }

  // Implement abstract method in GRBLBase
  private String getPrefix () {
    return "mini.cutter.";
  }

  // Implement for LaserCut.OutputDevice
  public String getName () {
    return "Mini Cutter";
  }

  // Implement for LaserCut.OutputDevice
  public Rectangle2D.Double getWorkspaceSize () {
    return new Rectangle2D.Double(0, 0, 8.5, 12.0);
  }

  // Implement for LaserCut.OutputDevice
  public double getZoomFactor () {
    return 1.0;
  }

  public JMenu getDeviceMenu () {
    JMenu miniCutterMenu = new JMenu(getName());
    // Add "Send to Mini Cutter" Submenu Item
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
    JTextField tf = new JTextField("1", 4);
    panel.add(tf);
    JMenuItem sendToMiniLazer = new JMenuItem("Send Job to " + getName());
    sendToMiniLazer.addActionListener((ActionEvent ev) -> {
      if (jPort.hasSerial()) {
        if (showConfirmDialog(laserCut, panel, "Send Job to " + getName(), YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
          boolean planPath = prefs.getBoolean(getPrefix() + "pathplan", true);
          int iterations = Integer.parseInt(tf.getText());
          // Cut Settings
          int cutSpeed = prefs.getInt(getPrefix() + "speed", MINI_PAPER_CUTTER_DEFAULT_SPEED);
          cutSpeed = Math.min(MINI_PAPER_CUTTER_MAX_SPEED, cutSpeed);                         // Setting cutting speed
          // Generate G_Code for TeensyCNC
          List<String> cmds = new ArrayList<>();
          cmds.add("G28");                                                                    // Make sure tool is Homed
          cmds.add("G20");                                                                    // Set Inches as Units
          cmds.add("M05");                                                                    // Set Tool Head Up
          cmds.add("F" + cutSpeed);                                                           // Set feed rate (inches/min)
          // Process only cut items
          List<CADShape> shapes = laserCut.surface.selectCutterItems(planPath);
          DecimalFormat fmt = new DecimalFormat("#.###");
          for (CADShape shape : shapes) {
            if (!(shape instanceof CADRasterImage)) {
              for (int ii = 0; ii < iterations; ii++) {
                double lastX = 0, lastY = 0;
                for (Line2D.Double[] lines : shape.getListOfScaledLines(1, .001)) {
                  boolean first = true;
                  for (Line2D.Double line : lines) {
                    String x1 = fmt.format(line.x1);
                    String y1 = fmt.format(INVERT_Y_AXIS ? 12 - line.y1 : line.y1);
                    String x2 = fmt.format(line.x2);
                    String y2 = fmt.format(INVERT_Y_AXIS ? 12 - line.y2 : line.y2);
                    if (first) {
                      cmds.add("M05");                                                        // Tool Up
                      cmds.add("G00 X" + x1 + " Y" + y1);                                     // Move to x1 y1 with tool up
                      cmds.add("M03");                                                        // Tool Down
                      cmds.add("G01 X" + x2 + " Y" + y2);                                     // Draw Line to x2 y2
                      first = false;
                    } else {
                      if (lastX != line.x1 || lastY != line.y1) {
                        cmds.add("M05");                                                      // Tool Up
                        cmds.add("G00 X" + x1 + " Y" + y1);                                   // Move to x1 y1 with tool up
                        cmds.add("M03");                                                      // Tool Down
                        cmds.add("G01 X" + x2 + " Y" + y2);                                   // Draw Line to x2 y2
                      } else {
                        cmds.add("G01 X" + x2 + " Y" + y2);                                   // Draw Line to x2 y2
                      }
                    }
                    lastX = line.x2;
                    lastY = line.y2;
                  }
                }
              }
              cmds.add("M05");                                                                // Set Tool Head Up (just in case)
            }
          }
          // Add ending G-codes
          cmds.add("M05");                                                                    // Set Tool Head Up
          cmds.add("G00 X0 Y0");                                                              // Move back close to Origin
          try {
            new GCodeSender(cmds.toArray(new String[0]), new String[]{"M05", "G28", "M02"});  // Abort commands
          } catch (Exception ex) {
            ex.printStackTrace();
            showMessageDialog(laserCut, "Error sending commands", "Error", PLAIN_MESSAGE);
          }
        }
      } else {
        showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    miniCutterMenu.add(sendToMiniLazer);
    // Add "Mini Cutter Settings" Submenu Item
    JMenuItem miniLazerSettings = new JMenuItem(getName() + " Settings");
    miniLazerSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Use Path Planner", prefs.getBoolean(getPrefix() + "pathplan", true)),
          new ParameterDialog.ParmItem("Cut Speed{inches/minute}", prefs.getInt(getPrefix() + "speed",
                                       MINI_PAPER_CUTTER_DEFAULT_SPEED)),
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, prefs.get("displayUnits", "in"), laserCut)) {
        prefs.putBoolean(getPrefix() + "pathplan", (Boolean) parmSet[0].value);
        prefs.putInt(getPrefix() + "speed", (Integer) parmSet[1].value);
      }
    });
    miniCutterMenu.add(miniLazerSettings);
    // Add "Port" Submenu to MenuBar (baud not needed)
    miniCutterMenu.add(jPort.getPortMenu());
    miniCutterMenu.addSeparator();
    // Add "Load Mat" Menu item
    JMenuItem load = new JMenuItem("Load Mat");
    miniCutterMenu.add(load);
    load.addActionListener(ev -> new GCodeSender(new String[]{"M39"}, new String[]{}));
    // Add "Unload Mat" Menu item
    JMenuItem unload = new JMenuItem("Unload Mat");
    miniCutterMenu.add(unload);
    unload.addActionListener(ev -> new GCodeSender(new String[]{"M40"}, new String[]{}));
    // Add "TeensyCNC Info" Menu item
    JMenuItem info = new JMenuItem("TeensyCNC Info");
    miniCutterMenu.add(info);
    info.addActionListener(ev -> new GCodeSender(new String[]{"M115", "G04 P10"}, new String[]{}, true));
    return miniCutterMenu;
  }

  class GCodeSender extends JDialog implements JSSCPort.RXEvent, Runnable {
    private final StringBuilder response = new StringBuilder();
    private String[]            cmds, abortCmds;
    private JTextArea           gcodePane;
    private JProgressBar        progress;
    private volatile long       cmdQueue;
    private final MiniCutter.GCodeSender.Lock lock = new MiniCutter.GCodeSender.Lock();
    private boolean             doAbort, printInfo;

    final class Lock { }

    GCodeSender (String[] cmds, String[] abortCmd) {
      this(cmds, abortCmd, false);
    }

    GCodeSender (String[] cmds, String[] abortCmds, boolean printInfo) {
      super(laserCut, false);
      if (jPort.hasSerial()) {
        this.printInfo = printInfo;
        setTitle("G-Code Monitor");
        setLocationRelativeTo(laserCut);
        add(progress = new JProgressBar(), BorderLayout.NORTH);
        progress.setMaximum(cmds.length);
        JScrollPane sPane = new JScrollPane(gcodePane = new JTextArea());
        gcodePane.setMargin(new Insets(3, 3, 3, 3));
        DefaultCaret caret = (DefaultCaret) gcodePane.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        gcodePane.setEditable(false);
        add(sPane, BorderLayout.CENTER);
        JButton abort = new JButton("Abort Job");
        add(abort, BorderLayout.SOUTH);
        abort.addActionListener(ev -> doAbort = true);
        Rectangle loc = getBounds();
        setSize(400, 300);
        setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 150);
        validate();
        this.cmds = cmds;
        this.abortCmds = abortCmds;
        new Thread(this).start();
      } else {
        showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        String rsp = response.toString();
        if (rsp.length() > 0) {
          //System.out.println(rsp + "\t");
          if (!rsp.startsWith("*")) {
            if (rsp.toLowerCase().contains("ok")) {
              synchronized (lock) {
                cmdQueue--;
                //System.out.println(rsp + "\t" + cmdQueue);
              }
            } else {
              gcodePane.append(rsp);
              gcodePane.append("\n");
            }
            response.setLength(0);
          } else if (rsp.startsWith("*") && printInfo) {
            if (rsp.contains("TeensyCNC")) {
              gcodePane.append(rsp.substring(2).replace('|', '\n'));
            }
            response.setLength(0);
          } else {
            response.setLength(0);
          }
        }
      } else if (cc != '\r'){
        response.append((char) cc);
      }
    }
    // Wait for command to complete, or 10 second timeout
    private void cmdWait () throws InterruptedException {
      boolean wait;
      int timeout = 10000;
      do {
        synchronized (lock) {
          wait = cmdQueue > 0 && !doAbort;
        }
        if (wait) {
          Thread.sleep(1);
        }
      } while (wait && timeout-- > 0);
    }

    public void run () {
      cmdQueue = 0;
      try {
        setVisible(true);
        // Connect to device and start sending gcode
        jPort.open(this);
        response.setLength(0);
        for (int ii = 0; (ii < cmds.length) && !doAbort; ii++) {
          String gcode = cmds[ii].trim();
          //System.out.println(gcode);
          if (!printInfo) {
            this.gcodePane.append(gcode + '\n');
          }
          // Ignore blank lines
          if (gcode.length() == 0) {
            continue;
          }
          progress.setValue(ii);
          try {
            jPort.sendString(gcode + "\n\r");
          } catch (Exception ex) {
            gcodePane.append("Unable to connect to MiniCutter\n");
          }
          synchronized (lock) {
            cmdQueue++;
            //System.out.println(gcode + "\t" + cmdQueue);
          }
          cmdWait();
        }
        if (doAbort) {
          cmdQueue = 0;
          doAbort = false;
          this.gcodePane.append("-abort-\n");
          for (String cmd : abortCmds) {
            this.gcodePane.append(cmd + '\n');
            jPort.sendString(cmd + "\n\r");     // Set abort command
            synchronized (lock) {
              cmdQueue++;
              //System.out.println(cmd + "\t" + cmdQueue);
            }
            cmdWait();
          }
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      jPort.close();
      setVisible(false);
      dispose();
    }
  }

  // Implemented for LaserCut.OutputDevice
  public void closeDevice () {
    if (jPort != null) {
      jPort.close();
    }
  }
}
