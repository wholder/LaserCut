import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

// https://www.cnccookbook.com/g-code-basics-program-format-structure-blocks/

import static javax.swing.JOptionPane.*;

  /*
    Mini Laser Engravers:
      K3 Laser:   https://github.com/RBEGamer/K3_LASER_ENGRAVER_PROTOCOL
      EzGrazer:   https://github.com/camrein/EzGraver
                  https://github.com/camrein/EzGraver/issues/43
      nejePrint:  https://github.com/AxelTB/nejePrint
    L aserGRBL:  http://lasergrbl.com/en/ and https://github.com/arkypita/LaserGRBL
   */

class MiniLaser extends GRBLBase implements LaserCut.OutputDevice {
  private static final int      MINI_CPOWER_DEFAULT = 50;     // Default Cutting Power (%)
  private static final int      MINI_CSPEED_DEFAULT = 100;    // Default Cutting Speed (inches/min)
  private static final int      MINI_EPOWER_DEFAULT = 50;     // Default Engraving Power (%)
  private static final int      MINI_ESPEED_DEFAULT = 100;    // Default Engraving Speed (inches/min)
  private static final int      MINI_DPI_DEFAULT = 200;       // Default Engraving DPI (dots/inch)
  private static final int      MINI_MAX_POWER = 255;         // Laser control value for 100% power
  private static final int      MINI_MAX_SPEED = 200;         // Max feed rate (inches/min)

  MiniLaser (LaserCut laserCut, Preferences prefs) {
    super(laserCut, prefs);
  }

  // Implement for GRBLBase to define Preferences prefix, such as "mini.laser."
  String getPrefix () {
    return "mini.laser.";
  }

  // Implement for LaserCut.OutputDevice
  public String getName () {
    return "Mini Laser";
  }

  // Implement for LaserCut.OutputDevice
  public Rectangle2D.Double getWorkspaceSize () {
    return new Rectangle2D.Double(0, 0, getDouble("workwidth", 7.0), getDouble("workheight", 8.0));
  }

  // Implement for LaserCut.OutputDevice
  public double getZoomFactor () {
    return getDouble("workzoom", 1.0);
  }

  int getGuidePower () {
    return getInt("guide", 0);
  }

  public JMenu getDeviceMenu () {
    JMenu miniLaserMenu = new JMenu(getName());
    // Add "Send to Mini Laser" Submenu Item
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
    JTextField tf = new JTextField("1", 4);
    panel.add(tf);
    JMenuItem sendToMiniLazer = new JMenuItem("Send Job to " + getName());
    sendToMiniLazer.addActionListener((ActionEvent ev) -> {
      if (jPort.hasSerial()) {
        if (showConfirmDialog(laserCut, panel, "Send Job to " + getName(), YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
          boolean dynamicLaser = getBoolean("dynamic", true);
          boolean planPath = getBoolean("pathplan", true);
          int iterations = Integer.parseInt(tf.getText());
          // Cut Settings
          int cutSpeed = getInt("speed", MINI_CSPEED_DEFAULT);
          cutSpeed = Math.min(MINI_MAX_SPEED, cutSpeed);                                      // Min speed = 10 inches/min
          int cutPower = getInt("power", MINI_CPOWER_DEFAULT) * MINI_MAX_POWER / 100;         // Max power == 255
          // Engrave Settings
          int engraveSpeed = getInt("espeed", MINI_ESPEED_DEFAULT);
          engraveSpeed = Math.min(MINI_MAX_SPEED, engraveSpeed);                              // Min speed = 10 inches/min
          int engravePower = getInt("epower", MINI_EPOWER_DEFAULT) * MINI_MAX_POWER / 100;    // Max power == 255
          int engraveDpi = getInt("dpi", MINI_DPI_DEFAULT);
          // Generate G_Code for GRBL 1.1
          List<String> cmds = new ArrayList<>();
          // Add starting G-codes
          cmds.add("G20");                                                                    // Set Inches as Units
          cmds.add("M05");                                                                    // Set Laser Off
          // Process engraved items first, then cut items
          List<CADShape> shapes = laserCut.surface.selectLaserItems(false, planPath);
          shapes.addAll(laserCut.surface.selectLaserItems(true, planPath));
          DecimalFormat fmt = new DecimalFormat("#.#####");
          int lastSpeed = -1;
          int lastPower = -1;
          for (CADShape shape : shapes) {
            if (shape instanceof CADRasterImage) {
              RasterSettings settings = new RasterSettings(engraveDpi, engraveSpeed, 1, engravePower);
              CADRasterImage raster = (CADRasterImage) shape;
              List<String>  rList = toGCode(raster, settings);
              cmds.addAll(rList);
              lastSpeed = -1;
              lastPower = -1;
            } else {
              String cmd = "";
              if (shape.engrave) {
                if (engravePower != lastPower) {
                  cmd = "S" + engravePower;                                                   // Set Laser Power (0 - 255)
                  lastPower = engravePower;
                }
                if (engraveSpeed != lastSpeed) {
                  cmd += "F" + engraveSpeed;                                                  // Set feed rate (inches/min)
                  lastSpeed = engraveSpeed;
                }
              } else {
                if (cutPower != lastPower) {
                  cmd = "S" + cutPower;                                                       // Set Laser Power (0 - 255)
                  lastPower = cutPower;
                }
                if (cutSpeed != lastSpeed) {
                  cmd += "F" + cutSpeed;                                                      // Set feed rate (inches/min)
                  lastSpeed = cutSpeed;
                }
              }
              if (cmd.length() > 0) {
                cmds.add(cmd);
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
                      cmds.add("M05G00X" + x1 + "Y" + y1);                                    // Move to x1 y1 with laser off
                      cmds.add((dynamicLaser ? "M04" : "M03") + "G01X" + x2 + "Y" + y2);      // Draw Line to x2 y2
                      first = false;
                    } else {
                      if (lastX != line.x1 || lastY != line.y1) {
                        cmds.add("M05G00X" + x1 + "Y" + y1);                                  // Move to x1 y1 with laser off
                        cmds.add((dynamicLaser ? "M04" : "M03") + "G01X" + x2 + "Y" + y2);    // Draw Line to x2 y2
                      } else {
                        cmds.add("G01X" + x2 + "Y" + y2);                                     // Draw Line to x2 y2
                      }
                    }
                    lastX = line.x2;
                    lastY = line.y2;
                  }
                }
              }
              cmds.add("M05");                                                                // Set Laser Off
            }
          }
          // Add ending G-codes
          cmds.add("M5");                                                                     // Set Laser Off
          cmds.add("G00X0Y0");                                                                // Move back to Origin
          try {
            new GRBLSender(cmds.toArray(new String[0]),
                          new String[]{"M5", "G00X0Y0"});                                     // Abort commands
          } catch (Exception ex) {
            ex.printStackTrace();
            showMessageDialog(laserCut, "Error sending commands", "Error", PLAIN_MESSAGE);
          }
        }
      } else {
        showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    miniLaserMenu.add(sendToMiniLazer);
    // Add "Mini Lazer Settings" Submenu Item
    JMenuItem miniLazerSettings = new JMenuItem(getName() + " Settings");
    miniLazerSettings.addActionListener(ev -> {
      Rectangle2D.Double workspace = getWorkspaceSize();
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Use Path Planner", getBoolean("pathplan", true)),
          new ParameterDialog.ParmItem("Guide Beam Power|%(0-10)", getInt("guide", 0)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Dynamic Laser", getBoolean("dynamic", true)),
          new ParameterDialog.ParmItem("Cut Power|%(0-100)", getInt("power", MINI_CPOWER_DEFAULT)),
          new ParameterDialog.ParmItem("Cut Speed{inches/minute}", getInt("speed", MINI_CSPEED_DEFAULT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Engrave Power|%(0-100)", getInt("epower", MINI_EPOWER_DEFAULT)),
          new ParameterDialog.ParmItem("Engrave Speed{inches/minute}", getInt("espeed", MINI_ESPEED_DEFAULT)),
          new ParameterDialog.ParmItem("Engrave DPI{dots/inch}", getInt("dpi", MINI_DPI_DEFAULT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Workspace Zoom:1 ; 1|1:2 ; 1|2:4 ; 1|4:8 ; 1|8", Integer.toString((int) getZoomFactor())),
          new ParameterDialog.ParmItem("Workspace Width{inches}", workspace.width),
          new ParameterDialog.ParmItem("Workspace Height{inches}", workspace.height),
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, prefs.get("displayUnits", "in"), laserCut)) {
        putBoolean("pathplan", (Boolean) parmSet[0].value);
        putInt("guide", (Integer) parmSet[1].value);
        // Separator
        putBoolean("dynamic", (Boolean) parmSet[3].value);
        putInt("power", (Integer) parmSet[4].value);
        putInt("speed", (Integer) parmSet[5].value);
        // Separator
        putInt("epower", (Integer) parmSet[7].value);
        putInt("espeed", (Integer) parmSet[8].value);
        putInt("dpi", (Integer) parmSet[9].value);
        // Separator
        putDouble("workzoom", Double.parseDouble((String) parmSet[11].value));
        laserCut.surface.setZoomFactor(getZoomFactor());
        putDouble("workwidth", (Double) parmSet[12].value);
        putDouble("workheight", (Double) parmSet[13].value);
        laserCut.surface.setSurfaceSize(getWorkspaceSize());
      }
    });
    miniLaserMenu.add(miniLazerSettings);
    // Add "Jog Controls" Submenu Item
    miniLaserMenu.add(getGRBLJogMenu(false));
    // Add "Get GRBL Settings" Menu Item
    miniLaserMenu.add(getGRBLSettingsMenu());
    // Add "Port" and "Baud" Submenu to MenuBar
    miniLaserMenu.add(jPort.getPortMenu());
    miniLaserMenu.add(jPort.getBaudMenu());
    return miniLaserMenu;
  }

  /*
   * * * * * * * * RASTER CODE * * * * * * * *
   */

  static class RasterSettings {
    private final int   rasterDpi;          // Raster Size used for Engraving
    private final int   feedRate;           // in inches/sec
    private final int   laserMin;           // Laser Minimum Power for Engraving
    private final int   laserMax;           // Laser Maximum Power for Engraving

    RasterSettings (int rasterDpi, int feedRate, int laserMin, int laserMax) {
      this.rasterDpi = rasterDpi;
      this.feedRate = feedRate;
      this.laserMin = laserMin;
      this.laserMax = laserMax;
    }
  }

  static private int map (int value, int minIn, int maxIn, int minOut, int maxOut) {
    return (value - minIn) * (maxOut - minOut) / (maxIn - minIn) + minOut;
  }

  static private List<String> toGCode (CADRasterImage cadRaster, RasterSettings settings) {
    BufferedImage imgIn = cadRaster.img;
    double xSize = cadRaster.width;
    double ySize = cadRaster.height;
    if (settings == null) {
      settings = new RasterSettings(100, 100, 1, 255);                    // Default settings 100 dpi, 100 in/min, 1 min, 255 max
    }
    // Resize image to match DPI specified for engraving
    int imgWid = (int) Math.round(xSize * settings.rasterDpi);
    int imgHyt = (int) Math.round(ySize * settings.rasterDpi);
    BufferedImage img = new BufferedImage(imgWid, imgHyt, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.drawImage(imgIn, 0, 0, imgWid, imgHyt, 0, 0, imgIn.getWidth(), imgIn.getHeight(), null);
    g2.dispose();
    WritableRaster raster = img.getRaster();
    DataBuffer data = raster.getDataBuffer();
    DecimalFormat fmt = new DecimalFormat("#.####");
    List<String> buf = new ArrayList<>();
    buf.add("G20");                                                                         // Set units to inches
    buf.add("M4");                                                                          // Dynamic Laser Mode
    buf.add("S0");                                                                          // S0 ; Laser off
    buf.add("F" + settings.feedRate);                                                       // Fnn ; Set feedrate for engraving
    // Compute step sizes for raster
    double xStep = 1.0 / settings.rasterDpi;
    double yStep = 1.0 / settings.rasterDpi;
    if (cadRaster.rotation != 0) {
      // Compute AffineTransform for rotation
      AffineTransform at = new AffineTransform();
      at.translate(cadRaster.xLoc - xSize / 2, cadRaster.yLoc - ySize / 2);
      at.rotate(Math.toRadians(cadRaster.rotation), xSize / 2, ySize / 2);

      int prevValue = 0;
      Point2D.Double loc = new Point2D.Double(0, 0);
      // Output GRBL Commands to Draw Raster Image
      for (int yy = 0; yy < imgHyt; yy++) {
        double yLoc = yStep * yy;
        if ((yy & 1) == 0) {                                                                // Scan left to right for even lines
          for (int xx = 0; xx < imgWid; xx++) {
            if (xx == 0) {
              // Move quickly to start of next even scan line
              double xLoc = xx * xStep;
              loc.setLocation(xLoc, yLoc);
              at.transform(loc, loc);
              buf.add("G00X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));                // G00Xn.nYn.n
            }
            double xLoc = xx * xStep;
            loc.setLocation(xLoc, yLoc);
            at.transform(loc, loc);
            int grey = 255 - data.getElem(yy * imgWid + xx);                                // Read pixel and convert to greyscale
            grey = map(grey, 0, 255, settings.laserMin, settings.laserMax);                 // Map 8 bit range to Laser Power Level range
            if (grey != prevValue) {                                                        // Only send Command if power has changed
              buf.add("S" + grey + "G01X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));   // G01Xn.nYn.n ; Set Laser Power and start draw
            } else if (xx == imgWid - 1) {
              buf.add("G01X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));                // G01Xn.nYn.n ; continue draw at last power
            }
            prevValue = grey;                                                               // Save the laser power for the next loop
          }
        } else {                                                                            // Scan right to left for off lines
          for (int xx = imgWid - 1; xx >= 0; xx--) {
            if (xx == imgWid - 1) {
              // Move quickly to start of next odd scan line
              double xLoc = xx * xStep;
              loc.setLocation(xLoc, yLoc);
              at.transform(loc, loc);
              buf.add("G00X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));                // G00Xn.nYn.n
            }
            double xLoc = xx * xStep;
            loc.setLocation(xLoc, yLoc);
            at.transform(loc, loc);
            int grey = 255 - data.getElem(yy * imgWid + xx);                                // Read pixel and convert to greyscale
            grey = map(grey, 0, 255, settings.laserMin, settings.laserMax);                 // Map 8 bit range to Laser Power Level range
            if (grey != prevValue) {                                                        // Only send Command if power has changed
              buf.add("S" + grey + "G01X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));   // G01Xn.nYn.n ; Set Laser Power and start draw
            } else if (xx == imgWid - 1) {
              buf.add("G01X" + fmt.format(loc.x) + "Y" + fmt.format(loc.y));                // G01Xn.nYn.n ; continue draw at last power
            }
            prevValue = grey;                                                               // Save the laser power for the next loop
          }
        }
      }
    } else {
      // Get workspace location of upper left corner
      double xOff = cadRaster.xLoc - xSize / 2;
      double yOff = cadRaster.yLoc - ySize / 2;
      int prevValue = 0;
      // Move quickly to start of next scan line
      buf.add("G00X" + fmt.format(xOff) + "Y" + fmt.format(yOff));                          // G00Xn.nYn.n
      // Output GRBL Commands to Draw Raster Image
      for (int yy = 0; yy < imgHyt; yy++) {
        double yLoc = yOff + yStep * yy;
        if ((yy & 1) == 0) {                                                                // Scan left to right for even lines
          // Step down to start of next scan line
          buf.add("G00Y" + fmt.format(yLoc));                                               // G00Yn.n
          for (int xx = 0; xx < imgWid; xx++) {
            double xLoc = xOff + xx * xStep;
            int grey = 255 - data.getElem(yy * imgWid + xx);                                // Read pixel and convert to greyscale
            grey = map(grey, 0, 255, settings.laserMin, settings.laserMax);                 // Map 8 bit range to Laser Power Level range
            if (grey != prevValue) {                                                        // Only send Command if power has changed
              buf.add("S" + grey + "G01X" + fmt.format(xLoc));                              // Sn ; Set Laser Power and start draw
            } else if (xx == imgWid - 1) {
              buf.add("G01X" + fmt.format(xLoc));                                           // G01Xn.n ; continue draw at last power
            }
            prevValue = grey;                                                               // Save the laser power for the next loop
          }
        } else {                                                                            // Scan right to left for off lines
          // Step down to end of next scan line
          buf.add("G00Y" + fmt.format(yLoc));                                               // G00Yn.n
          for (int xx = imgWid - 1; xx >= 0; xx--) {
            double xLoc = xOff + xx * xStep;
            int grey = 255 - data.getElem(yy * imgWid + xx);                                // Read pixel and convert to greyscale
            grey = map(grey, 0, 255, settings.laserMin, settings.laserMax);                 // Map 8 bit range to Laser Power Level range
            if (grey != prevValue) {                                                        // Only send Command if power has changed
              buf.add("S" + grey + "G01X" + fmt.format(xLoc));                              // Sn ; Set Laser Power and start draw
            } else if (xx == 0) {
              buf.add("G01X" + fmt.format(xLoc));                                           // G01Xn.n ; continue draw at last power
            }
            prevValue = grey;                                                               // Save the laser power for the next loop
          }
        }
      }
    }
    buf.add("S0M5");                                                                        // S0M5 ; Laser off
    return buf;
  }

  // Implemented for LaserCut.OutputDevice
  public void closeDevice () {
    if (jPort != null) {
      jPort.close();
    }
  }
}
