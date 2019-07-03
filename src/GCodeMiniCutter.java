import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static javax.swing.JOptionPane.*;

/**
 *  This code implements a driver for TeensyCNC by Matt Williams (https://github.com/seishuku/TeensyCNC)
 *
 *  G Codes Implemented by TeensyCNC:
 *    G00 - Rapid positioning
 *    G01 - Linear Interpolation
 *    G02 - Circular interpolation, clockwise
 *    G03 - Circular interpolation, counterclockwise
 *    G04 - Set Dwell (in seconds)
 *    G20 - Set Units to Inches
 *    G21 - Set Units to Millimaters
 *    G28 - Go home
 *    G30 - Go home in two steps
 *    G90 - Set Absolute Mode (default)
 *    G91 - Set Incremental Mode
 *    G92 - Set current position as home
 *
 *  M Codes Implemented by TeensyCNC:
 *    M2  - End of program
 *    M30 - End of program with reset
 *    M3  - Tool Down
 *    M4  - Tool Down
 *    M7  - Tool Down
 *    M5  - Tool Up
 *    M8  - Tool Up
 *    M39 - Load Paper
 *    M40 - Eject Paper
 *
 *  Special M Codes
 *    M111 - Report Job Mode (example response: "* Job mode: CUT\r\nok\r\n")
 *    M112 - Emergency stop / Enter bootloader
 *    M115 - Prints Info and Macro Commands available
 *
 *  Responses (terminated by "\r\n")
 *    ok      Normal response
 *    huh? G  Unknown "Gnn" code
 *    huh?    Error response
 *    * --    Info Response
 *
 *  Note: the g-code parser in TeensyCNC expects a space, or newline after each element of a command.  This means
 *  that TeensyCNC will not respond to "G00X1Y1\r\n".  Send as "G00 X1 Y1\r\n" instead.
 *
 *  Teeensy 3.2 VID = 0x2504, PID = 0x0300, JSSC Port Id is "cu.usbmodem4201"
 */

class GCodeMiniCutter implements LaserCut.OutputDevice {
  private static final int      MINI_PAPER_CUTTER_DEFAULT_SPEED = 90;           // Max feed rate (inches/min)
  private static final int      MINI_PAPER_CUTTER_MAX_SPEED = 200;              // Max feed rate (inches/min)
  private JSSCPort              jPort;
  private LaserCut              laserCut;
  private String                dUnits;

  GCodeMiniCutter (LaserCut laserCut) {
    this.laserCut = laserCut;
    this.dUnits = laserCut.displayUnits;
    jPort = new JSSCPort(getPrefix(), laserCut.prefs);
  }

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

  private boolean getBoolean(String name, boolean def) {
    return laserCut.prefs.getBoolean(getPrefix() + name, def);
  }

  private void putBoolean (String name, boolean value) {
    laserCut.prefs.putBoolean(getPrefix() + name, value);
  }

  private int getInt(String name, int def) {
    return laserCut.prefs.getInt(getPrefix() + name, def);
  }

  private void putInt (String name, int value) {
    laserCut.prefs.putInt(getPrefix() + name, value);
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
          boolean planPath = getBoolean("pathplan", true);
          int iterations = Integer.parseInt(tf.getText());
          // Cut Settings
          int cutSpeed = getInt("speed", MINI_PAPER_CUTTER_DEFAULT_SPEED);
          cutSpeed = Math.min(MINI_PAPER_CUTTER_MAX_SPEED, cutSpeed);                         // Setting cutting speed
          // Generate G_Code for TeensyCNC
          List<String> cmds = new ArrayList<>();
          cmds.add("G28");                                                                    // Make sure tool is Homed
          cmds.add("G20");                                                                    // Set Inches as Units
          cmds.add("M05");                                                                    // Set Tool Head Up
          cmds.add("F" + cutSpeed);                                                           // Set feed rate (inches/min)
          // Process only cut items
          List<LaserCut.CADShape> shapes = laserCut.surface.selectCutterItems(planPath);
          DecimalFormat fmt = new DecimalFormat("#.###");
          for (LaserCut.CADShape shape : shapes) {
            if (!(shape instanceof LaserCut.CADRasterImage)) {
              for (int ii = 0; ii < iterations; ii++) {
                double lastX = 0, lastY = 0;
                for (Line2D.Double[] lines : shape.getListOfScaledLines(1, .001)) {
                  boolean first = true;
                  for (Line2D.Double line : lines) {
                    String x1 = fmt.format(line.x1);
                    String y1 = fmt.format(line.y1);
                    String x2 = fmt.format(line.x2);
                    String y2 = fmt.format(line.y2);
                    if (first) {
                      cmds.add("M05");                                                        // Tool Up
                      cmds.add("G00 X" + x1 + " Y" + y1);                                     // Move to x1 y1 with laser off
                      cmds.add("M03");                                                        // Tool Down
                      cmds.add("G01 X" + x2 + " Y" + y2);                                     // Draw Line to x2 y2
                      first = false;
                    } else {
                      if (lastX != line.x1 || lastY != line.y1) {
                        cmds.add("M05");                                                      // Tool Up
                        cmds.add("G00 X" + x1 + " Y" + y1);                                   // Move to x1 y1 with laser off
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
          cmds.add("M5");                                                                     // Set Tool Head Up
          cmds.add("G00 X0 Y0");                                                              // Move back to Origin
          try {
            new GCodeSender(cmds.toArray(new String[0]), new String[]{"M5", "G28", "M2"});    // Abort commands
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
          new ParameterDialog.ParmItem("Use Path Planner", getBoolean("pathplan", true)),
          new ParameterDialog.ParmItem("Cut Speed{inches/minute}", getInt("speed", MINI_PAPER_CUTTER_DEFAULT_SPEED)),
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, laserCut)) {
        putBoolean("pathplan", (Boolean) parmSet[0].value);
        putInt("speed", (Integer) parmSet[1].value);
      }
    });
    miniCutterMenu.add(miniLazerSettings);
    // Add "Port" Submenu to MenuBar (baud not needed)
    miniCutterMenu.add(jPort.getPortMenu());
    return miniCutterMenu;
  }

  class GCodeSender extends JDialog implements JSSCPort.RXEvent, Runnable {
    private StringBuilder   response = new StringBuilder();
    private String[]        cmds, abortCmds;
    private JTextArea gcode;
    private JProgressBar    progress;
    private volatile long   cmdQueue;
    private final GCodeMiniCutter.GCodeSender.Lock lock = new GCodeMiniCutter.GCodeSender.Lock();
    private boolean         doAbort;

    final class Lock { }

    GCodeSender (String[] cmds, String[] abortCmds) {
      super(laserCut, false);
      setTitle("G-Code Monitor");
      setLocationRelativeTo(laserCut);
      add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(cmds.length);
      JScrollPane sPane = new JScrollPane(gcode = new JTextArea());
      gcode.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) gcode.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      gcode.setEditable(false);
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
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        String rsp = response.toString();
        if (!rsp.startsWith("*") && rsp.length() > 0) {
          if (rsp.toLowerCase().contains("ok")) {
            synchronized (lock) {
              cmdQueue--;
              //System.out.println(rsp + "\t" + cmdQueue);
            }
          } else {
            gcode.append(rsp);
            gcode.append("\n");
          }
          response.setLength(0);
        } else {
          response.setLength(0);
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
          this.gcode.append(gcode + '\n');
          // Ignore blank lines
          if (gcode.length() == 0) {
            continue;
          }
          progress.setValue(ii);
          jPort.sendString(gcode + "\n\r");
          synchronized (lock) {
            cmdQueue++;
            //System.out.println(gcode + "\t" + cmdQueue);
          }
          cmdWait();
        }
        if (doAbort) {
          cmdQueue = 0;
          doAbort = false;
          this.gcode.append("-abort-\n");
          for (String cmd : abortCmds) {
            this.gcode.append(cmd + '\n');
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
