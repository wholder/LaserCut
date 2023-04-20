import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.drivers.EpilogZing;
import com.t_oster.liblasercut.utils.BufferedImageAdapter;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

class ZingLaser implements LaserCut.OutputDevice {
  private static final double               ZING_PPI = 500;
  private static final int                  ZING_SPEED_DEFAUlT = 55;
  private static final int                  ZING_FREQ_DEFAUlT = 500;
  private static final int                  ZING_CUT_POWER_DEFAUlT = 85;
  private static final int                  ZING_ENGRAVE_POWER_DEFAUlT = 5;
  private static final int                  ZING_RASTER_POWER_DEFAUlT = 50;
  private static final Rectangle2D.Double   zingFullSize = new Rectangle2D.Double(0, 0, 16, 12);
  private static final Rectangle2D.Double   zing12x12Size = new Rectangle2D.Double(0, 0, 12, 12);
  private final LaserCut                          laserCut;
  private final String                            dUnits;

  ZingLaser (LaserCut laserCut) {
    this.laserCut = laserCut;
    this.dUnits = laserCut.displayUnits;
  }

  // Implemented for LaserCut.OutputDevice
  public String getName () {
    return "Epilog Zing";
  }

  // Implemented for LaserCut.OutputDevice
  public Rectangle2D.Double getWorkspaceSize () {
    return zingFullSize;
  }

  // Implemented for LaserCut.OutputDevice
  public void closeDevice () {
    // Nothing to do
  }

  // Implemented for LaserCut.OutputDevice
  public double getZoomFactor () {
    return 1.0;
  }

  public JMenu getDeviceMenu () {
    JMenu zingMenu = new JMenu(getName());
    // Add "Send to Zing" Submenu Item
    JMenuItem sendToZing = new JMenuItem("Send Job to " + getName());
    sendToZing.addActionListener(ev -> {
      String zingIpAddress = laserCut.prefs.get("zing.ip", "10.0.1.201");
      if (zingIpAddress == null ||  zingIpAddress.length() == 0) {
        laserCut.showErrorDialog("Please set the " + getName() + "'s IP Address in " + getName() + "->Zing Settings");
        return;
      }
      if (laserCut.showWarningDialog("Press OK to Send Job to " + getName())) {
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
        boolean planPath = laserCut.prefs.getBoolean("zing.pathplan", true);
        LaserJob job = new LaserJob("laserCut", "laserCut", "laserCut");   // title, name, user
        // Process raster engrave passes, if any
        for (CADShape shape : laserCut.surface.getDesign()) {
          if (shape instanceof CADRasterImage && shape.engrave) {
            CADRasterImage raster = (CADRasterImage) shape;
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
          List<CADShape> shapes = laserCut.surface.selectLaserItems(doCut, planPath);
          for (CADShape shape : shapes) {
            for (Line2D.Double[] lines : shape.getListOfScaledLines(ZING_PPI, .001)) {
              if (lines.length > 0) {
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
        }
        new ZingSender(laserCut, lasercutter, job);
      }
    });
    zingMenu.add(sendToZing);

    // Build JComboBox List of Materials for "Zing Settings" parameters dialog
    String[] materials = LaserCut.getResourceFile("/materials/zing.materials").split("===");
    JComboBox<Object> matMenu = new JComboBox<>();
    matMenu.addItem("");
    for (String material : materials) {
      Properties props = laserCut.getProperties(material);
      if (props.containsKey("name")) {
        String name = props.getProperty("name");
        matMenu.addItem(name);
      }
    }
    matMenu.setSelectedIndex(0);
    JMenuItem zingSettings = new JMenuItem(getName() + " Settings");
    zingSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem(getName() + " IP Add", laserCut.prefs.get("zing.ip", "10.0.1.201")),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem(matMenu),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Cut Power|%(0-100)", laserCut.prefs.getInt("zing.power", ZING_CUT_POWER_DEFAUlT)),
          new ParameterDialog.ParmItem("Cut Speed", laserCut.prefs.getInt("zing.speed", ZING_SPEED_DEFAUlT)),
          new ParameterDialog.ParmItem("Cut Freq|Hz", laserCut.prefs.getInt("zing.freq", ZING_FREQ_DEFAUlT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Engrave Power|%(0-100)", laserCut.prefs.getInt("zing.epower", ZING_ENGRAVE_POWER_DEFAUlT)),
          new ParameterDialog.ParmItem("Engrave Speed", laserCut.prefs.getInt("zing.espeed", ZING_SPEED_DEFAUlT)),
          new ParameterDialog.ParmItem("Engrave Freq|Hz", laserCut.prefs.getInt("zing.efreq", ZING_FREQ_DEFAUlT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Raster Power|%(0-100)", laserCut.prefs.getInt("zing.rpower", ZING_RASTER_POWER_DEFAUlT)),
          new ParameterDialog.ParmItem("Raster Speed", laserCut.prefs.getInt("zing.rspeed", ZING_SPEED_DEFAUlT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Use Path Planner", laserCut.prefs.getBoolean("zing.pathplan", true)),

      };
      matMenu.addItemListener(ev2 -> {
        if (ev2.getStateChange() == ItemEvent.SELECTED) {
          String name = (String) matMenu.getSelectedItem();
          for (int ii = 0; ii <= materials.length; ii++) {
            boolean none = ii == materials.length;
            Properties props = none ? new Properties() : laserCut.getProperties( materials[ii]);
            if ((name != null && name.equals(props.getProperty("name"))) || none) {
              parmSet[4].setField(props.getProperty("power"));
              parmSet[5].setField(props.getProperty("speed"));
              parmSet[6].setField(props.getProperty("freq"));
              parmSet[8].setField(props.getProperty("epower"));
              parmSet[9].setField(props.getProperty("espeed"));
              parmSet[10].setField(props.getProperty("efreq"));
              parmSet[12].setField(props.getProperty("rpower"));
              parmSet[13].setField(props.getProperty("rspeed"));
              break;
            }
          }
        }
      });
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, laserCut)) {
        laserCut.prefs.put("zing.ip", (String) parmSet[0].value);
        laserCut.prefs.putInt("zing.power", (Integer) parmSet[4].value);
        laserCut.prefs.putInt("zing.speed", (Integer) parmSet[5].value);
        laserCut.prefs.putInt("zing.freq",  (Integer) parmSet[6].value);
        laserCut.prefs.putInt("zing.epower", (Integer) parmSet[8].value);
        laserCut.prefs.putInt("zing.espeed", (Integer) parmSet[9].value);
        laserCut.prefs.putInt("zing.efreq",  (Integer) parmSet[10].value);
        laserCut.prefs.putInt("zing.rpower", (Integer) parmSet[12].value);
        laserCut.prefs.putInt("zing.rspeed", (Integer) parmSet[13].value);
        laserCut.prefs.putBoolean("zing.pathplan", (Boolean) parmSet[15].value);
      }
    });
    zingMenu.add(zingSettings);
    // Add "Resize for Zing" Full Size Submenu Items
    JMenuItem zingResize = new JMenuItem("Resize for " + getName() + " (" + zingFullSize.width  + " x " + zingFullSize.height  + ")");
    zingResize.addActionListener(ev -> {
      laserCut.surface.setZoomFactor(1);
      laserCut.surface.setSurfaceSize(zingFullSize);
    });
    zingMenu.add(zingResize);
    JMenuItem zing12x12 = new JMenuItem("Resize for " + getName() + " (" + zing12x12Size.width + " x " +  zing12x12Size.height + ")");
    zing12x12.addActionListener(ev -> laserCut.surface.setSurfaceSize(zing12x12Size));
    zingMenu.add(zing12x12);
    return zingMenu;
  }

  class ZingSender extends JDialog implements Runnable {
    final EpilogZing              lasercutter;
    final LaserJob                job;
    private final JProgressBar    progress;
    private final JTextArea       status;

    ZingSender (LaserCut laserCut, EpilogZing lasercutter, LaserJob job) {
      super(laserCut);
      setTitle(ZingLaser.this.getName() + " Monitor");
      this.lasercutter = lasercutter;
      this.job = job;
      add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(100);
      JScrollPane sPane = new JScrollPane(status = new JTextArea());
      status.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) status.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      add(sPane, BorderLayout.CENTER);
      Rectangle loc = laserCut.getBounds();
      setSize(300, 150);
      setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 75);
      setVisible(true);
      new Thread(this).start();
      status.append("Starting Job...\n");
      paint(getGraphics());       // Kludge to get JTextArea to update
    }

    public void run () {
      List<String> warnings = new LinkedList<>();
      boolean hadError = false;
      String errMsg = "Unknown error";
      try {
        lasercutter.sendJob(job, new ProgressListener() {
          @Override
          public void progressChanged (Object obj, int ii) {
            progress.setValue(ii);
            status.append("Completed " + ii + "%\n");
          }

          @Override
          public void taskChanged (Object obj, String str) {
            // setProgress(i);
          }
        }, warnings);
      } catch (Exception ex) {
        hadError = true;
        errMsg = ex.getMessage();
      } finally {
        setVisible(false);
        dispose();
        if (hadError) {
          laserCut.showErrorDialog("Unable to send job to " + ZingLaser.this.getName() + ".\n" + errMsg);
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
    }
  }
}
