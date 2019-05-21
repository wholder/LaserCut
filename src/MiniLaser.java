import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

import static javax.swing.JOptionPane.*;

class MiniLaser extends GRBLBase {
  private static final int      MINI_POWER_DEFAULT = 255;
  private static final int      MINI_SPEED_DEFAULT = 100;
  private static Dimension      miniSize = new Dimension((int) (7 * LaserCut.SCREEN_PPI), (int) (8 * LaserCut.SCREEN_PPI));
  private JSSCPort              jPort;
  private LaserCut              laserCut;
  private String                dUnits;

  MiniLaser (LaserCut laserCut) {
    this.laserCut = laserCut;
    this.dUnits = laserCut.displayUnits;
  }

  JMenu getMiniLaserMenu () throws Exception {
    JMenu miniLaserMenu = new JMenu("Mini Laser");
    jPort = new JSSCPort("mini.laser.", laserCut.prefs);
    // Add "Send to Mini Laser" Submenu Item
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
    JTextField tf = new JTextField("1", 4);
    panel.add(tf);
    JMenuItem sendToMiniLazer = new JMenuItem("Send GRBL to Mini Laser");
    sendToMiniLazer.addActionListener((ActionEvent ex) -> {
      if (jPort.hasSerial()) {
        if (showConfirmDialog(laserCut, panel, "Send GRBL to Mini Laser", YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
          try {
            boolean miniDynamicLaser = laserCut.prefs.getBoolean("mini.laser.dynamic", true);
            boolean planPath = laserCut.prefs.getBoolean("mini.laser.pathplan", true);
            int iterations = Integer.parseInt(tf.getText());
            // Generate G_Code for GRBL 1.1
            List<String> cmds = new ArrayList<>();
            // Add starting G-codes
            cmds.add("G20");                                                // Set Inches as Units
            int speed = laserCut.prefs.getInt("mini.laser.speed", MINI_SPEED_DEFAULT);
            speed = Math.max(10, speed);                                    // Min speed = 10 inches/min
            cmds.add("M05");                                                // Set Laser Off
            int power = laserCut.prefs.getInt("mini.laser.power", MINI_POWER_DEFAULT);
            power = Math.min(1000, power);                                  // Max power == 1000
            cmds.add("S" + power);                                          // Set Laser Power (0 - 1000)
            cmds.add("F" + speed);                                          // Set feed rate (inches/minute)
            double lastX = 0, lastY = 0;
            for (int ii = 0; ii < iterations; ii++) {
              boolean laserOn = false;
              List<LaserCut.CADShape> shapes = laserCut.surface.selectLaserItems(true);
              if (planPath) {
                shapes = PathPlanner.optimize(shapes);
              }
              for (LaserCut.CADShape shape : shapes) {
                for (Line2D.Double[] lines : shape.getListOfScaledLines(1, .001)) {
                  boolean first = true;
                  for (Line2D.Double line : lines) {
                    String x1 = LaserCut.df.format(line.x1);
                    String y1 = LaserCut.df.format(line.y1);
                    String x2 = LaserCut.df.format(line.x2);
                    String y2 = LaserCut.df.format(line.y2);
                    if (first) {
                      cmds.add("M05");                                      // Set Laser Off
                      cmds.add("G00 X" + x1 + " Y" + y1);                   // Move to x1 y1
                      if (power > 0) {
                        cmds.add(miniDynamicLaser ? "M04" : "M03");         // Set Laser On
                        laserOn = true;                                     // Leave Laser On
                      }
                      cmds.add("G01 X" + x2 + " Y" + y2);                   // Line to x2 y2
                      lastX = line.x2;
                      lastY = line.y2;
                    } else {
                      if (lastX != line.x1 || lastY != line.y1) {
                        cmds.add("M05");                                    // Set Laser Off
                        cmds.add("G00 X" + x1 + " Y" + y1);                 // Move to x1 y1
                        laserOn = false;                                    // Leave Laser Off
                      }
                      if (!laserOn && power > 0) {
                        cmds.add(miniDynamicLaser ? "M04" : "M03");         // Set Laser On
                        laserOn = true;                                     // Leave Laser On
                      }
                      cmds.add("G01 X" + x2 + " Y" + y2);                   // Line to x2 y2
                      lastX = line.x2;
                      lastY = line.y2;
                    }
                    first = false;
                  }
                }
                if (laserOn) {
                  cmds.add("M05");                                          // Set Laser Off
                  laserOn = false;
                }
              }
            }
            // Add ending G-codes
            cmds.add("M5");                                                 // Set Laser Off
            cmds.add("G00 X0 Y0");                                          // Move back to Origin
            new GRBLSender(laserCut, jPort, cmds.toArray(new String[0]), new String[] {"M5"});
          } catch (Exception ex2) {
            laserCut.showErrorDialog("Invalid parameter " + tf.getText());
          }
        }
      } else {
        showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    miniLaserMenu.add(sendToMiniLazer);
    // Add "Mini Lazer Settings" Submenu Item
    JMenuItem miniLazerSettings = new JMenuItem("Mini Lazer Settings");
    miniLazerSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Dynamic Laser", laserCut.prefs.getBoolean("mini.laser.dynamic", true)),
          new ParameterDialog.ParmItem("Use Path Planner", laserCut.prefs.getBoolean("mini.laser.pathplan", true)),
          new ParameterDialog.ParmItem("Power|%", laserCut.prefs.getInt("mini.laser.power", MINI_POWER_DEFAULT)),
          new ParameterDialog.ParmItem("Speed{inches/minute}", laserCut.prefs.getInt("mini.laser.speed", MINI_SPEED_DEFAULT))
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, laserCut)) {
        int ii = 0;
        laserCut.prefs.putBoolean("mini.laser.dynamic", (Boolean) parmSet[ii++].value);
        laserCut.prefs.putBoolean("mini.laser.pathplan", (Boolean) parmSet[ii++].value);
        laserCut.prefs.putInt("mini.laser.power", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("mini.laser.speed", (Integer) parmSet[ii].value);
      }
    });
    miniLaserMenu.add(miniLazerSettings);
    // Add "Resize for Mini Lazer" Submenu Item
    JMenuItem miniResize = new JMenuItem("Resize for Mini Lazer (" + (miniSize.width / LaserCut.SCREEN_PPI) +
          " x " + (miniSize.height / LaserCut.SCREEN_PPI) + ")");
    miniResize.addActionListener(ev -> laserCut.surface.setSurfaceSize(miniSize));
    miniLaserMenu.add(miniResize);
    // Add "Jog Controls" Submenu Item
    miniLaserMenu.add(getGRBLJogMenu(laserCut, jPort, false));
    // Add "Get GRBL Settings" Menu Item
    miniLaserMenu.add(getGRBLSettingsMenu(laserCut, jPort));
    // Add "Port" and "Baud" Submenu to MenuBar
    miniLaserMenu.add(jPort.getPortMenu());
    miniLaserMenu.add(jPort.getBaudMenu());
    return miniLaserMenu;
  }

  void close () {
    if (jPort != null) {
      jPort.close();
    }
  }
}
