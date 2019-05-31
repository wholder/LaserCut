import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static javax.swing.JOptionPane.*;

class MiniLaser extends GRBLBase {
  private static final int      MINI_CPOWER_DEFAULT = 50;     // Default Cutting Power (%)
  private static final int      MINI_CSPEED_DEFAULT = 100;    // Default Cutting Speed (inches/min)
  private static final int      MINI_EPOWER_DEFAULT = 50;     // Default Engraving Power (%)
  private static final int      MINI_ESPEED_DEFAULT = 100;    // Default Engraving Speed (inches/min)
  private static final int      MINI_DPI_DEFAULT = 200;       // Default Engraving DPI (dots/inch)
  private static final int      MINI_MAX_POWER = 255;         // Laser control value for 100% power
  private static final int      MINI_MAX_SPEED = 200;         // Max feed rate (inches/min)
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
    JMenuItem sendToMiniLazer = new JMenuItem("Send Job to Mini Laser");
    sendToMiniLazer.addActionListener((ActionEvent ex) -> {
      if (jPort.hasSerial()) {
        if (showConfirmDialog(laserCut, panel, "Send Job to Mini Laser", YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
          boolean miniDynamicLaser = laserCut.prefs.getBoolean("mini.laser.dynamic", true);
          boolean planPath = laserCut.prefs.getBoolean("mini.laser.pathplan", true);
          int iterations = Integer.parseInt(tf.getText());
          // Cut Settings
          int cutSpeed = laserCut.prefs.getInt("mini.laser.speed", MINI_CSPEED_DEFAULT);
          cutSpeed = Math.min(MINI_MAX_SPEED, cutSpeed);                                      // Min speed = 10 inches/min
          int cutPower = laserCut.prefs.getInt("mini.laser.power", MINI_CPOWER_DEFAULT);
          cutPower = Math.min(MINI_MAX_POWER, MINI_MAX_POWER * cutPower / 100);               // Max power == 1000
          // Engrave Settings
          int engraveSpeed = laserCut.prefs.getInt("mini.laser.espeed", MINI_ESPEED_DEFAULT);
          engraveSpeed = Math.min(MINI_MAX_SPEED, engraveSpeed);                              // Min speed = 10 inches/min
          int engravePower = laserCut.prefs.getInt("mini.laser.epower", MINI_EPOWER_DEFAULT);
          engravePower = Math.min(MINI_MAX_POWER, MINI_MAX_POWER * engravePower / 100);       // Max power == 1000
          int engraveDpi = laserCut.prefs.getInt("mini.laser.dpi", MINI_DPI_DEFAULT);
          // Generate G_Code for GRBL 1.1
          List<String> cmds = new ArrayList<>();
          // Add starting G-codes
          cmds.add("G20");                                                                    // Set Inches as Units
          cmds.add("M05");                                                                    // Set Laser Off
          boolean laserOn = false;
          // Process engraved items first, then cut items
          List<LaserCut.CADShape> shapes = laserCut.surface.selectLaserItems(false);
          shapes.addAll(laserCut.surface.selectLaserItems(true));
          if (planPath) {
            shapes = PathPlanner.optimize(shapes);
          }
          DecimalFormat fmt = new DecimalFormat("#.#####");
          for (LaserCut.CADShape shape : shapes) {
            if (shape instanceof LaserCut.CADRasterImage) {
              RasterSettings settings = new RasterSettings(engraveDpi, engraveSpeed, 1, engravePower);
              LaserCut.CADRasterImage raster = (LaserCut.CADRasterImage) shape;
              List<String>  rList = toGCode(raster, settings);
              cmds.addAll(rList);
            } else {
              if (shape.engrave) {
                cmds.add("S" + engravePower);                                                 // Set Laser Power (0 - 1000)
                cmds.add("F" + engraveSpeed);                                                 // Set feed rate (inches/min)
              } else {
                cmds.add("S" + cutPower);                                                     // Set Laser Power (0 - 1000)
                cmds.add("F" + cutSpeed);                                                     // Set feed rate (inches/min)
              }
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
                      cmds.add("M05");                                                        // Set Laser Off
                      cmds.add("G00X" + x1 + "Y" + y1);                                       // Move to x1 y1
                      if (cutPower > 0) {
                        cmds.add(miniDynamicLaser ? "M04" : "M03");                           // Set Laser On
                        laserOn = true;                                                       // Leave Laser On
                      }
                      cmds.add("G01X" + x2 + "Y" + y2);                                       // Line to x2 y2
                      lastX = line.x2;
                      lastY = line.y2;
                    } else {
                      if (lastX != line.x1 || lastY != line.y1) {
                        cmds.add("M05");                                                      // Set Laser Off
                        cmds.add("G00X" + x1 + "Y" + y1);                                     // Move to x1 y1
                        laserOn = false;                                                      // Leave Laser Off
                      }
                      if (!laserOn && cutPower > 0) {
                        cmds.add(miniDynamicLaser ? "M04" : "M03");                           // Set Laser On
                        laserOn = true;                                                       // Leave Laser On
                      }
                      cmds.add("G01X" + x2 + "Y" + y2);                                       // Line to x2 y2
                      lastX = line.x2;
                      lastY = line.y2;
                    }
                    first = false;
                  }
                }
              }
              if (laserOn) {
                cmds.add("M05");                                                              // Set Laser Off
                laserOn = false;
              }
            }
          }
          // Add ending G-codes
          cmds.add("M5");                                                                     // Set Laser Off
          cmds.add("G00X0Y0");                                                                // Move back to Origin
          new GRBLSender(laserCut, jPort, cmds.toArray(new String[0]),
                         new String[] {"M5", "G00X0Y0"});                                     // Abort commands
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
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Cut Power|%", Math.min(100, laserCut.prefs.getInt("mini.laser.power", MINI_CPOWER_DEFAULT))),
          new ParameterDialog.ParmItem("Cut Speed{inches/minute}", laserCut.prefs.getInt("mini.laser.speed", MINI_CSPEED_DEFAULT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Engrave Power|%", Math.min(100, laserCut.prefs.getInt("mini.laser.epower", MINI_EPOWER_DEFAULT))),
          new ParameterDialog.ParmItem("Engrave Speed{inches/minute}", laserCut.prefs.getInt("mini.laser.espeed", MINI_ESPEED_DEFAULT)),
          new ParameterDialog.ParmItem("Engrave DPI{dots/inch}", laserCut.prefs.getInt("mini.laser.dpi", MINI_DPI_DEFAULT))
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, laserCut)) {
        laserCut.prefs.putBoolean("mini.laser.dynamic", (Boolean) parmSet[0].value);
        laserCut.prefs.putBoolean("mini.laser.pathplan", (Boolean) parmSet[1].value);
        laserCut.prefs.putInt("mini.laser.power", Math.min(100, (Integer) parmSet[3].value));
        laserCut.prefs.putInt("mini.laser.speed", (Integer) parmSet[4].value);
        laserCut.prefs.putInt("mini.laser.epower", Math.min(100, (Integer) parmSet[6].value));
        laserCut.prefs.putInt("mini.laser.espeed", (Integer) parmSet[7].value);
        laserCut.prefs.putInt("mini.laser.dpi", (Integer) parmSet[8].value);
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
