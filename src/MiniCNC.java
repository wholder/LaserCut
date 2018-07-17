  /*
   *  Place holder for GRBL-based MIniCNC driver
   *
   *  Notes on MiniCNC:
   *    Spindle shaft diameter: 5mm
   *
   *  Defaults from controller received with MiniCNC:
   *    Grbl 0.9j ['$' for help]
   *    $0=10         (step pulse, usec)
   *    $1=25         (step idle delay, msec)
   *    $2=0          (step port invert mask:00000000)
   *    $3=0          (dir port invert mask:00000000)
   *    $4=0          (step enable invert, bool)
   *    $5=0          (limit pins invert, bool)
   *    $6=0          (probe pin invert, bool)
   *    $10=3         (status report mask:00000011)
   *    $11=0.010     (junction deviation, mm)
   *    $12=0.002     (arc tolerance, mm)
   *    $13=0         (report inches, bool)
   *    $20=0         (soft limits, bool)
   *    $21=0         (hard limits, bool)
   *    $22=0         (homing cycle, bool)
   *    $23=0         (homing dir invert mask:00000000)
   *    $24=25.000    (homing feed, mm/min)
   *    $25=500.000   (homing seek, mm/min)
   *    $26=250       (homing debounce, msec)
   *    $27=1.000     (homing pull-off, mm)
   *    $100=800.000  (x, step/mm)
   *    $101=800.000  (y, step/mm)
   *    $102=800.000  (z, step/mm)
   *    $110=500.000  (x max rate, mm/min)
   *    $111=500.000  (y max rate, mm/min)
   *    $112=500.000  (z max rate, mm/min)
   *    $120=300.000  (x accel, mm/sec^2)
   *    $121=300.000  (y accel, mm/sec^2)
   *    $122=300.000  (z accel, mm/sec^2)
   *    $130=200.000  (x max travel, mm)
   *    $131=200.000  (y max travel, mm)
   *    $132=200.000  (z max travel, mm)
   */

  import javax.swing.*;
  import java.awt.*;
  import java.awt.event.ActionEvent;
  import java.awt.geom.Line2D;
  import java.util.ArrayList;
  import java.util.List;

  import static javax.swing.JOptionPane.*;

  class MiniCNC extends GRBLBase {
    private static final int      MINI_CNC_FEED_DEFAULT = 255;
    private static final int      MINI_CNC_RPM_DEFAULT = 100;
    private static Dimension      miniCncSize = new Dimension((int) (7 * LaserCut.SCREEN_PPI), (int) (8 * LaserCut.SCREEN_PPI));
    private JSSCPort              jPort;
    private LaserCut              laserCut;

    MiniCNC (LaserCut laserCut) {
      this.laserCut = laserCut;
    }

    JMenu getMiniCncMenu () throws Exception {
      JMenu miniCncMenu = new JMenu("Mini CNC");
      jPort = new JSSCPort("mini.cnc.", laserCut.prefs);
      // Add "Send to Mini Laser" Submenu Item
      JPanel panel = new JPanel(new GridLayout(1, 2));
      panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
      JTextField tf = new JTextField("1", 4);
      panel.add(tf);
      JMenuItem sendToMiniCnc = new JMenuItem("Send GRBL to Mini CNC");
      sendToMiniCnc.addActionListener((ActionEvent ex) -> {
        if (jPort.hasSerial()) {
          if (showConfirmDialog(laserCut, panel, "Send GRBL to Mini CNC", YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
            try {
              int iterations = Integer.parseInt(tf.getText());
              // Generate G_Code for GRBL 1.1
              List<String> cmds = new ArrayList<>();
              // Add starting G-codes
              cmds.add("G20");                                                // Set Inches as Units
              int rpm = laserCut.prefs.getInt("mini.cnc.rpm", MINI_CNC_RPM_DEFAULT);
              rpm = Math.max(10, rpm);                                        // Min speed = 10 inches/min
              cmds.add("M05");                                                // Set Laser Off
              int feed = laserCut.prefs.getInt("mini.cnc.feed", MINI_CNC_FEED_DEFAULT);
              feed = Math.min(1000, feed);                                    // Max power == 1000
              cmds.add("S" + feed);                                           // Set Spindle Speed (0 - 1000)
              cmds.add("F" + rpm);                                            // Set feed rate (inches/minute)
              for (int ii = 0; ii < iterations; ii++) {
                for (LaserCut.CADShape shape : laserCut.surface.selectCncItems()) {
                  for (Line2D.Double[] lines : shape.getListOfScaledLines(1)) {
                    String x1 = LaserCut.df.format(lines[0].x1);
                    String y1 = LaserCut.df.format(lines[0].y1);
                    cmds.add("G00 X" + x1 + " Y" + y1);                       // Move to first x1 y1
                    // Move Z Axis down to cutting position
                    //  ** Need code here
                    for (Line2D.Double line : lines) {
                      String x2 = LaserCut.df.format(line.x2);
                      String y2 = LaserCut.df.format(line.y2);
                      cmds.add("G01 X" + x2 + " Y" + y2);                     // Line to next x2 y2
                    }
                  }
                  // Retract Z Axis before move to next position
                  //  ** Need code nere
                }
              }
              // Add ending G-codes
              cmds.add("M5");                                                 // Set Laser Off
              cmds.add("G00 X0 Y0");                                          // Move back to Origin
              new GRBLSender(laserCut, jPort, cmds.toArray(new String[0]));
            } catch (Exception ex2) {
              laserCut.showErrorDialog("Invalid parameter " + tf.getText());
            }
          }
        } else {
          showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
        }
      });
      miniCncMenu.add(sendToMiniCnc);
      // Add "Mini CNC Settings" Submenu Item
      JMenuItem miniLazerSettings = new JMenuItem("Mini CNC Settings");
      miniLazerSettings.addActionListener(ev -> {
        ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Speed|RPM", laserCut.prefs.getInt("mini.cnc.rpm", MINI_CNC_FEED_DEFAULT)),
          new ParameterDialog.ParmItem("Feed|inches/minute", laserCut.prefs.getInt("mini.cnc.feed", MINI_CNC_RPM_DEFAULT))
        };
        if (ParameterDialog.showSaveCancelParameterDialog(parmSet, laserCut)) {
          int ii = 0;
          laserCut.prefs.putInt("mini.cnc.rpm", (Integer) parmSet[ii++].value);
          laserCut.prefs.putInt("mini.cnc.feed", (Integer) parmSet[ii].value);
        }
      });
      miniCncMenu.add(miniLazerSettings);
      // Add "Resize for Mini Lazer" Submenu Item
      JMenuItem miniResize = new JMenuItem("Resize for Mini Lazer (" + (miniCncSize.width / LaserCut.SCREEN_PPI) +
        "x" + (miniCncSize.height / LaserCut.SCREEN_PPI) + ")");
      miniResize.addActionListener(ev -> laserCut.surface.setSurfaceSize(miniCncSize));
      miniCncMenu.add(miniResize);
      // Add "Jog Controls" Submenu Item
      miniCncMenu.add(getGRBLJogMenu(laserCut, jPort));
      // Add "Get GRBL Settings" Menu Item
      miniCncMenu.add(getGRBLSettingsMenu(laserCut, jPort));
      // Add "Port" and "Baud" Submenu to MenuBar
      miniCncMenu.add(jPort.getPortMenu());
      miniCncMenu.add(jPort.getBaudMenu());
      return miniCncMenu;
    }

    void close () {
      if (jPort != null) {
        jPort.close();
      }
    }
  }
