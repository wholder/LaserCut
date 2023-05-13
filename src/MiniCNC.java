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
  import java.awt.geom.Rectangle2D;
  import java.text.DecimalFormat;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.prefs.Preferences;

  import static javax.swing.JOptionPane.*;

  class MiniCNC extends GRBLBase implements LaserCut.OutputDevice {
    private static final int      MINI_CNC_FEED_DEFAULT = 255;
    private static final int      MINI_CNC_RPM_DEFAULT = 100;
    private JSSCPort              jPort;

    MiniCNC (LaserCut laserCut, Preferences prefs) {
      super(laserCut, prefs);
    }

    // Implement for GRBLBase to define Preferences prefix, such as "mini.laser."
    String getPrefix () {
      return "mini.cnc.";
    }

    // Implement for LaserCut.OutputDevice
    public String getName () {
      return "Mini CNC";
    }

    // Implement for LaserCut.OutputDevice
    public Rectangle2D.Double getWorkspaceSize () {
      return new Rectangle2D.Double(0, 0, getDouble("workwidth", 6.0), getDouble("workheight", 6.0));
    }

    // Implement for LaserCut.OutputDevice
    public double getZoomFactor () {
      return getDouble("workzoom", 1.0);
    }

    public JMenu getDeviceMenu () {
      JMenu miniCncMenu = new JMenu(getName());
      jPort = new JSSCPort(getPrefix(), laserCut.prefs);
      // Add "Send to Mini Laser" Submenu Item
      JPanel panel = new JPanel(new GridLayout(1, 2));
      panel.add(new JLabel("Z Depth: ", JLabel.RIGHT));
      JTextField tf = new JTextField("1", 4);
      panel.add(tf);
      JMenuItem sendToMiniCnc = new JMenuItem("Send GRBL to " + getName());
      sendToMiniCnc.addActionListener((ActionEvent ev) -> {
        if (jPort.hasSerial()) {
          if (showConfirmDialog(laserCut, panel, "Send GRBL to " + getName(), YES_NO_OPTION, PLAIN_MESSAGE, null) == OK_OPTION) {
            double zDepth = Double.parseDouble(tf.getText());
            zDepth = zDepth > 0 ? -zDepth : zDepth;                         // Make sure Z depth is negative (move down)
            // Generate G_Code for GRBL 1.1
            List<String> cmds = new ArrayList<>();
            // Add starting G-codes
            cmds.add("G20");                                                // Set Inches as Units
            int rpm = getInt("rpm", MINI_CNC_RPM_DEFAULT);
            rpm = Math.min(1000, rpm);                                      // Max RPM == 1000
            int feed = getInt("feed", MINI_CNC_FEED_DEFAULT);
            feed = Math.max(1, feed);                                       // Min feed = 1 inches/min
            DecimalFormat fmt = new DecimalFormat("#.#####");
            for (CADShape shape : laserCut.surface.selectCncItems()) {
              for (Line2D.Double[] lines : shape.getListOfScaledLines(1, .001)) {
                String x1 = fmt.format(lines[0].x1);
                String y1 = fmt.format(lines[0].y1);
                String z1 = fmt.format(zDepth);
                cmds.add("S" + rpm);                                        // Set Spindle RPM (0 - 1000)
                cmds.add("F" + feed);                                       // Set feed rate (inches/minute)
                cmds.add("G00X" + x1 + "Y" + y1);                           // Fast Move to first x1 y1
                cmds.add("G01Z" + z1);                                      // Slow Move Z Axis down to cutting position
                for (Line2D.Double line : lines) {
                  String x2 = fmt.format(line.x2);
                  String y2 = fmt.format(line.y2);
                  cmds.add("G01X" + x2 + "Y" + y2);                         // Slow cut Line to next x2 y2
                }
              }
              cmds.add("G00Z0");                                            // Fast Retract Z axis before move to next position
            }
            // Add ending G-codes
            cmds.add("G00Z0");                                              // Fast Retract Z axis (in case of abort)
            cmds.add("S0");                                                 // Set Spindle to zero RPM
            cmds.add("G00X0Y0");                                            // Move back to Origin
            try {
              new GRBLSender(cmds.toArray(new String[0]),                   // Send commands to Mini CNC
                  new String[]{"F0"});                                      // On abort, Spindle to zero RPM
            } catch (Exception ex) {
              ex.printStackTrace();
              showMessageDialog(laserCut, "Error sending commands", "Error", PLAIN_MESSAGE);
            }
          }
        } else {
          showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
        }
      });
      miniCncMenu.add(sendToMiniCnc);
      // Add "Mini CNC Settings" Submenu Item
      JMenuItem miniLazerSettings = new JMenuItem(getName() + " Settings");
      miniLazerSettings.addActionListener(ev -> {
        Rectangle2D.Double workspace = getWorkspaceSize();
        ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Speed{RPM}", getInt("rpm", MINI_CNC_FEED_DEFAULT)),
          new ParameterDialog.ParmItem("Feed{inches/minute}", getInt("feed", MINI_CNC_RPM_DEFAULT)),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Workspace Zoom:1 ; 1|1:2 ; 1|2:4 ; 1|4:8 ; 1|8", Integer.toString((int) getZoomFactor())),
          new ParameterDialog.ParmItem("Workspace Width{inches}", workspace.width),
          new ParameterDialog.ParmItem("Workspace Height{inches}", workspace.height),
        };
        if (ParameterDialog.showSaveCancelParameterDialog(parmSet, prefs.get("displayUnits", "in"), laserCut)) {
          putInt("rpm", (Integer) parmSet[0].value);
          putInt("feed", (Integer) parmSet[1].value);
          // Separator
          putDouble("workzoom", Double.parseDouble((String) parmSet[3].value));
          laserCut.surface.setZoomFactor(getZoomFactor());
          putDouble("workwidth", (Double) parmSet[4].value);
          putDouble("workheight", (Double) parmSet[5].value);
          laserCut.surface.setSurfaceSize(getWorkspaceSize());
        }
      });
      miniCncMenu.add(miniLazerSettings);
      // Add "Jog Controls" Submenu Item
      miniCncMenu.add(getGRBLJogMenu(true));
      // Add "Get GRBL Settings" Menu Item
      miniCncMenu.add(getGRBLSettingsMenu());
      // Add "Get GRBL Coordinates" Menu Item
      miniCncMenu.add(getGRBLCoordsMenu());
      // Add "Port" and "Baud" Submenu to MenuBar
      miniCncMenu.add(jPort.getPortMenu());
      miniCncMenu.add(jPort.getBaudMenu());
      return miniCncMenu;
    }

    // Implemented for LaserCut.OutputDevice
    public void closeDevice () {
      if (jPort != null) {
        jPort.close();
      }
    }
  }
