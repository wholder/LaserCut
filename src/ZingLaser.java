import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.drivers.EpilogZing;
import com.t_oster.liblasercut.utils.BufferedImageAdapter;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

class ZingLaser {
  private static final double   ZING_PPI = 500;
  private static final int      ZING_SPEED_DEFAUlT = 55;
  private static final int      ZING_FREQ_DEFAUlT = 500;
  private static final int      ZING_CUT_POWER_DEFAUlT = 85;
  private static final int      ZING_ENGRAVE_POWER_DEFAUlT = 5;
  private static final int      ZING_RASTER_POWER_DEFAUlT = 50;
  static final Dimension        zingFullSize = new Dimension((int) (16 * LaserCut.SCREEN_PPI), (int) (12 * LaserCut.SCREEN_PPI));
  static final Dimension        zing12x12Size = new Dimension((int) (12 * LaserCut.SCREEN_PPI), (int) (12 * LaserCut.SCREEN_PPI));

  static JMenu getZingMenu (LaserCut laserCut) {
    JMenu zingMenu = new JMenu("Zing Laser");
    // Add "Send to Zing" Submenu Item
    JMenuItem sendToZing = new JMenuItem("Send Job to Zing");
    sendToZing.addActionListener(ev -> {
      String zingIpAddress = laserCut.prefs.get("zing.ip", "10.0.1.201");
      if (zingIpAddress == null ||  zingIpAddress.length() == 0) {
        laserCut.showErrorDialog("Please set the Zing's IP Address in Export->Zing Settings");
        return;
      }
      if (laserCut.showWarningDialog("Press OK to Send Job to Zing")) {
        EpilogZing lasercutter = new EpilogZing(zingIpAddress);
        // Set Properties for Materials, such as for 3 mm birch plywood, Set: 60% speed, 80% power, 0 focus, 500 Hz.
        PowerSpeedFocusFrequencyProperty cutProperties = new PowerSpeedFocusFrequencyProperty();
        cutProperties.setProperty("speed", laserCut.prefs.getInt("zing.speed", ZING_SPEED_DEFAUlT));
        cutProperties.setProperty("power", laserCut.prefs.getInt("zing.power", ZING_CUT_POWER_DEFAUlT));
        cutProperties.setProperty("frequency", laserCut.prefs.getInt("zing.freq", ZING_FREQ_DEFAUlT));
        cutProperties.setProperty("focus", 0.0f);
        PowerSpeedFocusFrequencyProperty engraveProperties = new PowerSpeedFocusFrequencyProperty();
        engraveProperties.setProperty("speed", laserCut.prefs.getInt("zing.espeed", ZING_SPEED_DEFAUlT));
        engraveProperties.setProperty("power", laserCut.prefs.getInt("zing.epower", ZING_ENGRAVE_POWER_DEFAUlT));
        engraveProperties.setProperty("frequency", laserCut.prefs.getInt("zing.efreq", ZING_FREQ_DEFAUlT));
        engraveProperties.setProperty("focus", 0.0f);
        PowerSpeedFocusFrequencyProperty rasterProperties = new PowerSpeedFocusFrequencyProperty();
        rasterProperties.setProperty("speed", laserCut.prefs.getInt("zing.rspeed", ZING_SPEED_DEFAUlT));
        rasterProperties.setProperty("power", laserCut.prefs.getInt("zing.rpower", ZING_RASTER_POWER_DEFAUlT));
        rasterProperties.setProperty("frequency", ZING_FREQ_DEFAUlT);
        rasterProperties.setProperty("focus", 0.0f);
        LaserJob job = new LaserJob("laserCut", "laserCut", "laserCut");   // title, name, user
        // Process raster engrave passes, if any
        for (LaserCut.CADShape shape : laserCut.surface.getDesign()) {
          if (shape instanceof LaserCut.CADRasterImage && shape.engrave) {
            LaserCut.CADRasterImage raster = (LaserCut.CADRasterImage) shape;
            double[] scale = raster.getScale(ZING_PPI);
            Rectangle2D bb = raster.getScaledRotatedBounds(scale);
            AffineTransform at = raster.getScaledRotatedTransform(bb, scale);
            BufferedImage scaledImg = raster.getScaledRotatedImage(at, bb, scale);
            Point2D.Double offset = raster.getScaledRotatedOrigin(at, bb);
            int xLoc = (int) Math.round(shape.xLoc * ZING_PPI - offset.x);
            int yLoc = (int) Math.round(shape.yLoc * ZING_PPI - offset.y);
            com.t_oster.liblasercut.platform.Point loc = new com.t_oster.liblasercut.platform.Point(xLoc, yLoc);
            if (raster.engrave3D) {
              Raster3dPart rp = new Raster3dPart(new BufferedImageAdapter(scaledImg),
                  rasterProperties, new com.t_oster.liblasercut.platform.Point(xLoc, yLoc), ZING_PPI);
              job.addPart(rp);
            } else {
              RasterPart rp = new RasterPart(new BlackWhiteRaster(new BufferedImageAdapter(scaledImg),
                  BlackWhiteRaster.DitherAlgorithm.AVERAGE), new PowerSpeedFocusProperty(), loc, ZING_PPI);
              job.addPart(rp);
            }
          }
        }
        // Process cut and vector engrave passes
        for (int ii = 0; ii < 2; ii++) {
          boolean doCut = ii == 1;
          // Transform all the shapesInGroup into a series of line segments
          int lastX = 0, lastY = 0;
          VectorPart vp = new VectorPart(doCut ? cutProperties : engraveProperties, ZING_PPI);
          // Loop detects pen up/pen down based on start and end points of line segments
          boolean hasVector = false;
          for (LaserCut.CADShape shape : laserCut.surface.selectLaserItems(doCut)) {
            ArrayList<Line2D.Double> lines = shape.getScaledLines(ZING_PPI);
            if (lines.size() > 0) {
              hasVector = true;
              boolean first = true;
              for (Line2D.Double line : lines) {
                Point p1 = new Point((int) Math.round(line.x1), (int) Math.round(line.y1));
                Point p2 = new Point((int) Math.round(line.x2), (int) Math.round(line.y2));
                if (first) {
                  vp.moveto(p1.x, p1.y);
                  vp.lineto(lastX = p2.x, lastY = p2.y);
                } else {
                  if (lastX != p1.x || lastY != p1.y) {
                    vp.moveto(p1.x, p1.y);
                  }
                  vp.lineto(lastX = p2.x, lastY = p2.y);
                }
                first = false;
              }
            }
          }
          if (hasVector) {
            job.addPart(vp);
          }
        }
        ZingMonitor zMon = new ZingMonitor(laserCut);
        new Thread(() -> {
          List<String> warnings = new LinkedList<>();
          boolean hadError = false;
          String errMsg = "Unknown error";
          try {
            lasercutter.sendJob(job, new ProgressListener() {
              @Override
              public void progressChanged (Object obj, int ii) {
                zMon.setProgress(ii);
              }

              @Override
              public void taskChanged (Object obj, String str) {
                //zMon.setProgress(i);
              }
            }, warnings);
          } catch (Exception ex) {
            hadError = true;
            errMsg = ex.getMessage();
          } finally {
            zMon.setVisible(false);
            zMon.dispose();
            if (hadError) {
              laserCut.showErrorDialog("Unable to send job to Zing.\n" + errMsg);
            } else if (warnings.size() > 0) {
              StringBuilder buf = new StringBuilder();
              boolean addLf = false;
              for (String warning : warnings) {
                if (addLf) {
                  buf.append('\n');
                }
                addLf = true;
                buf.append(warning);
              }
              laserCut.showInfoDialog(buf.toString());
            }
          }
        }).start();
      }
    });
    zingMenu.add(sendToZing);
    // Add "Zing Settings" Submenu Item
    JMenuItem zingSettings = new JMenuItem("Zing Settings");
    zingSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Zing IP Add", laserCut.prefs.get("zing.ip", "10.0.1.201")),
          new ParameterDialog.ParmItem("Cut Power|%", laserCut.prefs.getInt("zing.power", ZING_CUT_POWER_DEFAUlT), true),
          new ParameterDialog.ParmItem("Cut Speed", laserCut.prefs.getInt("zing.speed", ZING_SPEED_DEFAUlT)),
          new ParameterDialog.ParmItem("Cut Freq|Hz", laserCut.prefs.getInt("zing.freq", ZING_FREQ_DEFAUlT)),
          new ParameterDialog.ParmItem("Engrave Power|%", laserCut.prefs.getInt("zing.epower", ZING_ENGRAVE_POWER_DEFAUlT), true),
          new ParameterDialog.ParmItem("Engrave Speed", laserCut.prefs.getInt("zing.espeed", ZING_SPEED_DEFAUlT)),
          new ParameterDialog.ParmItem("Engrave Freq|Hz", laserCut.prefs.getInt("zing.efreq", ZING_FREQ_DEFAUlT)),
          new ParameterDialog.ParmItem("Raster Power|%", laserCut.prefs.getInt("zing.rpower", ZING_RASTER_POWER_DEFAUlT), true),
          new ParameterDialog.ParmItem("Raster Speed", laserCut.prefs.getInt("zing.rspeed", ZING_SPEED_DEFAUlT)),
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, laserCut)) {
        int ii = 0;
        laserCut.prefs.put("zing.ip", (String) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.power", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.speed", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.freq",  (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.epower", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.espeed", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.efreq",  (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.rpower", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("zing.rspeed", (Integer) parmSet[ii++].value);
      }
    });
    zingMenu.add(zingSettings);
    // Add "Materials" Submenu Item
    JMenu matMenu = new JMenu("Zing Materials");
    try {
      String[] list = laserCut.getResourceList("/materials/zing/");
      for (String material : list) {
        Properties props = laserCut.getResourceProperties(material);
        JMenuItem matItem = new JMenuItem(props.getProperty("name"));
        matMenu.add(matItem);
        matItem.addActionListener(ev -> {
          //System.out.println(props.getProperty("name"));
          laserCut.prefs.putInt("zing.power", Integer.parseInt(props.getProperty("power")));
          laserCut.prefs.putInt("zing.speed", Integer.parseInt(props.getProperty("speed")));
          laserCut.prefs.putInt("zing.freq",  Integer.parseInt(props.getProperty("freq")));
          laserCut.prefs.putInt("zing.epower", Integer.parseInt(props.getProperty("epower")));
          laserCut.prefs.putInt("zing.espeed", Integer.parseInt(props.getProperty("espeed")));
          laserCut.prefs.putInt("zing.efreq",  Integer.parseInt(props.getProperty("efreq")));
          laserCut.prefs.putInt("zing.rpower", Integer.parseInt(props.getProperty("rpower")));
          laserCut.prefs.putInt("zing.rspeed", Integer.parseInt(props.getProperty("rspeed")));
        });
      }
      zingMenu.add(matMenu);
    } catch (Exception ex) {
      ex.printStackTrace(System.err);
    }
    // Add "Resize for Zing" Full Size Submenu Items
    JMenuItem zingResize = new JMenuItem("Resize for Zing (" + (zingFullSize.width / LaserCut.SCREEN_PPI) + "x" +
          (zingFullSize.height / LaserCut.SCREEN_PPI) + ")");
    zingResize.addActionListener(ev -> laserCut.surface.setSurfaceSize(zingFullSize));
    zingMenu.add(zingResize);
    JMenuItem zing12x12 = new JMenuItem("Resize for Zing (" + (zing12x12Size.width / LaserCut.SCREEN_PPI) + "x" +
          (zing12x12Size.height / LaserCut.SCREEN_PPI) + ")");
    zing12x12.addActionListener(ev -> laserCut.surface.setSurfaceSize(zing12x12Size));
    zingMenu.add(zing12x12);
    return zingMenu;
  }

  static class ZingMonitor extends JDialog {
    private JProgressBar    progress;
    private JTextArea       status;

    ZingMonitor (LaserCut laserCut) {
      super(laserCut, "Zing Monitor");
      add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(100);
      JScrollPane sPane = new JScrollPane(status = new JTextArea());
      status.append("Starting Job...\n");
      status.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) status.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      add(sPane, BorderLayout.CENTER);
      Rectangle loc = laserCut.getBounds();
      setSize(300, 150);
      setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 75);
      setVisible(true);
    }

    void setProgress(int prog) {
      progress.setValue(prog);
      status.append("Completed prog" + prog + "%\n");
    }
  }
}
