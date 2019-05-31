import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

import static javax.swing.JOptionPane.*;

/*
   *  GRBL 1.1 Pinouts and commands
   *     D2 - Step Pulse X-Axis Output
   *     D3 - Step Pulse Y-Axis Output
   *     D4 - Step Pulse Z-Axis Output
   *     D5 - Direction X-Axis Output
   *     D6 - Direction Y-Axis Output
   *     D7 - Direction Z-Axis Output
   *     D8 - Stepper Enable/Disable Output
   *     D9 - Limit X-Axis Input (to Ground, NC if $5=1 or NO  if $5=0)
   *    D10 - Limit Y-Axis Input (to Ground,  NC if $5=1 or NO  if $5=0)
   *  (Note: the function of pins D11 and D12 are swapped from GRBL v0.9)
   *    D11 - Spindle PWM Output
   *    D12 - Limit Z-Axis Input (to Ground, NC if $5=1 or NO  if $5=0)
   *    D13 - Spindle Direction Output
   *     A0 - Reset/Abort Input
   *     A1 - Feed Hold Input
   *     A2 - Cycle Start Input
   *     A3 - Coolant Enable Output (controlled by M8 [on],M9 [off] commands and 0xA0 realtime toggle command)
   *     A4 - Coolant Mist Enable (normally disabled in code "//#define ENABLE_M7")
   *     A5 - Probe Input (to Ground)
   *
   *  Basic G-Code Motion Modes:
   *     G0 - Rapid Move to position (also G00)
   *     G1 - Linear Move to position (used for cutting)
   *     G2 - Arc Move Clockwise
   *     G3 - Arc Move Counterclockwise
   *     G38.2 - Probe toward workpiece, stop on contact, signal error if failure
   *     G38.3 - Probe toward workpiece, stop on contact
   *     G38.4 - Probe away from workpiece, stop on loss of contact, signal error if failure
   *     G38.5 - Probe away from workpiece, stop on loss of contact
   *
   *  Alarm-related commands
   *     $X - Kill Alarm Lock
   *     $H - Run Homing Cycle (requires limit switches)
   *
   *  Units-related commands
   *    G20 - Set Default Units to Inches
   *    G21 - Set Default Units to Millimeters
   *    G90 - Movement commands are absolute
   *    G91 - Movement commands are incremental
   *
   *  Program Modes:
   *     M0 - Pause Program (tool change)
   *     M1 - Pause Program if stop switch enabled
   *     M2 - End Program (obsolete, use M30)
   *    M30 - End Program and Reset
   *
   *  Spindle-related commands: (enabled by $32=0 setting)
   *     M3 - Set CW rotation (CNC only)
   *     M4 - Set CCW rotation (CNC only)
   *     M5 - Spindle Off
   *
   *  Laser-related commands: (enabled by $32=1 setting)
   *     M3 - Laser On
   *     M4 - Laser On (dynamic mode)
   *     M5 - Laser Off
   *
   *  Coolant State commands
   *     M7 - Mist Coolant (pin A4 On, but must #define ENABLE_M7 to use)
   *     M8 - Flood Coolant (pin A3 On)
   *     M9 - All Coolant Off (pin A3 Off)
   *
   *  Real-Time commmands:
   *     ~ - Cycle Start
   *     ! - Feed Hold
   *     ? - Get Current Status (typical response: "<Idle,MPos:5.529,0.560,7.000,WPos:1.529,-5.440,-0.000>")
   *    ^x - Ctrl-x - GRBL Soft Reset (recommended before starting job)
   *  0x90 - Set Feed Rate to 100% of programmed rate
   *  0x91 - Increase Linear Move Feed Rate 10%
   *  0x92 - Decrease Linear Move Feed Rate 10%
   *  0x93 - Increase Linear Move Feed Rate 1%
   *  0x94 - Decrease Linear Move Feed Rate 1%
   *  0x95 - Set Rapid Move Rate to 100% (full)
   *  0x96 - Set Rapid Move Rate to 50%
   *  0x97 - Set Rapid Move Rate to 25%
   *
   *  Probe-related commands:
   *    G38.3 G20 F40 Z-1           Start probe move to Z to 1 inch with feedrate 40, stop on probe contact, or end of move
   *    G0 Z0                       Exit probe command state and move probe back to initial position
   *    $X                          Clear Alarm state
   *
   *  Probe responses:
    *   [PRB:5.000,5.000,-6.000:1]  On probe contact
    *   ALARM:4                     The probe is not in the expected initial state before starting probe cycle
   *    ALARM:5                     Probe fails to contact in within the programmed travel for G38.2 and G38.4
   *    error:9                     G-code locked out during alarm or jog state
   *
   *  G-Code Referances:
   *    http://linuxcnc.org/docs/html/gcode.html
   */

class GRBLBase {

  JMenuItem getGRBLSettingsMenu (LaserCut parent, JSSCPort jPort) {
    JMenuItem settings = new JMenuItem("Get GRBL Settings");
    settings.addActionListener(ev -> {
      if (jPort.hasSerial()) {
      String receive = sendGrbl(jPort, "$I");
      String[] rsps = receive.split("\n");
      String grblBuild = "unknown";
      String grblVersion = "unknown";
      String grblOptions = "unknown";
      for (String rsp : rsps ) {
        int idx1 = rsp.indexOf("[VER:");
        int idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblVersion = rsp.substring(5, rsp.length() - 2);
          if (grblVersion.contains(":")) {
            String[] tmp = grblVersion.split(":");
            grblVersion = tmp[1];
            grblBuild = tmp[0];
          }
        }
        idx1 = rsp.indexOf("[OPT:");
        idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblOptions = rsp.substring(5, rsp.length() - 2);
        }
      }
      receive = sendGrbl(jPort, "$$");
      String[] opts = receive.split("\n");
      HashMap<String,String> sVals = new LinkedHashMap<>();
      for (String opt : opts) {
        String[] vals = opt.split("=");
        if (vals.length == 2) {
          sVals.put(vals[0], vals[1]);
        }
      }
      JPanel sPanel;
      if (grblVersion != null) {
        ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("@Grbl Version",                 grblVersion),
          new ParameterDialog.ParmItem("@Grbl Build",                   grblBuild),
          new ParameterDialog.ParmItem("@Grbl Options",                 grblOptions),
          new ParameterDialog.ParmItem(new JSeparator()),
          new ParameterDialog.ParmItem("Step pulse|usec",               sVals, "$0"),
          new ParameterDialog.ParmItem("Step idle delay|msec",          sVals, "$1"),
          new ParameterDialog.ParmItem("Step port invert",              sVals, "$2", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Direction port invert",         sVals, "$3", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Step enable invert|boolean",    sVals, "$4"),
          new ParameterDialog.ParmItem("Limit pins invert|boolean",     sVals, "$5"),
          new ParameterDialog.ParmItem("Probe pin invert|boolean",      sVals, "$6"),
          new ParameterDialog.ParmItem("Status report|mask",            sVals, "$10"),
          new ParameterDialog.ParmItem("Junction deviation|mm",         sVals, "$11"),
          new ParameterDialog.ParmItem("Arc tolerance|mm",              sVals, "$12"),
          new ParameterDialog.ParmItem("Report inches|boolean",         sVals, "$13"),
          new ParameterDialog.ParmItem("Soft limits|boolean",           sVals, "$20"),
          new ParameterDialog.ParmItem("Hard limits|boolean",           sVals, "$21"),
          new ParameterDialog.ParmItem("Homing cycle|boolean",          sVals, "$22"),
          new ParameterDialog.ParmItem("Homing dir invert",             sVals, "$23", new String[] {"X", "Y", "Z"}),   // Bitfield
          new ParameterDialog.ParmItem("Homing feed|mm/min",            sVals, "$24"),
          new ParameterDialog.ParmItem("Homing seek|mm/min",            sVals, "$25"),
          new ParameterDialog.ParmItem("Homing debounce|msec",          sVals, "$26"),
          new ParameterDialog.ParmItem("Homing pull-off|mm",            sVals, "$27"),
          new ParameterDialog.ParmItem("Max spindle speed|RPM",         sVals, "$30"),
          new ParameterDialog.ParmItem("Min spindle speed|RPM",         sVals, "$31"),
          new ParameterDialog.ParmItem("Laser mode|boolean",            sVals, "$32"),
          new ParameterDialog.ParmItem("X Axis|steps/mm",               sVals, "$100"),
          new ParameterDialog.ParmItem("Y Axis|steps/mm",               sVals, "$101"),
          new ParameterDialog.ParmItem("Z Axis|steps/mm",               sVals, "$102"),
          new ParameterDialog.ParmItem("X Max rate|mm/min",             sVals, "$110"),
          new ParameterDialog.ParmItem("Y Max rate|mm/min",             sVals, "$111"),
          new ParameterDialog.ParmItem("Z Max rate|mm/min",             sVals, "$112"),
          new ParameterDialog.ParmItem("X Acceleration|mm/sec\u00B2",   sVals, "$120"),
          new ParameterDialog.ParmItem("Y Acceleration|mm/sec\u00B2",   sVals, "$121"),
          new ParameterDialog.ParmItem("Z Acceleration|mm/sec\u00B2",   sVals, "$122"),
          new ParameterDialog.ParmItem("X Max travel|mm",               sVals, "$130"),
          new ParameterDialog.ParmItem("Y Max travel|mm",               sVals, "$131"),
          new ParameterDialog.ParmItem("Z Max travel|mm",               sVals, "$132"),
        };
        Properties info = parent.getProperties(LaserCut.getResourceFile("grbl/grblparms.props"));
        ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}, null, info));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);              // Note: this call invokes dialog
        if (dialog.wasPressed()) {
          java.util.List<String> cmds = new ArrayList<>();
          for (ParameterDialog.ParmItem parm : parmSet) {
            String value = parm.getStringValue();
            if (!parm.readOnly & !parm.lblValue && !value.equals(sVals.get(parm.key))) {
              //System.out.println(parm.name + ": changed from " + sVals.get(parm.key) + " to " + value);
              cmds.add(parm.key + "=" + value);
            }
          }
          if (cmds.size() > 0) {
            new GRBLSender(parent, jPort, cmds.toArray(new String[0]));
          }
        //} else {
          //System.out.println("Cancel");
        }
      } else {
        sPanel = new JPanel(new GridLayout(sVals.size() + 5, 2, 4, 0));
        Font font = new Font("Courier", Font.PLAIN, 14);
        JLabel lbl;
        int idx1 = rsps[0].indexOf("[");
        int idx2 = rsps[0].indexOf("]");
        if (rsps.length == 2 && idx1 >= 0 && idx2 > 0) {
          grblVersion = rsps[0].substring(1, rsps[0].length() - 2);
        }
        sPanel.add(new JLabel("GRBL Version: " + (grblVersion != null ? grblVersion : "unknown")));
        sPanel.add(new JSeparator());
        for (String key : sVals.keySet()) {
          sPanel.add(lbl = new JLabel(padSpace(key + ":") + sVals.get(key)));
          lbl.setFont(font);
        }
        sPanel.add(new JSeparator());
        sPanel.add(new JLabel("Note: upgrade to GRBL 1.1, or later"));
        sPanel.add(new JLabel("to enable settings editor."));
        Object[] options = {"OK"};
        showOptionDialog(parent, sPanel, "GRBL Settings", OK_CANCEL_OPTION, PLAIN_MESSAGE, null, options, options[0]);
      }
      } else {
        showMessageDialog(parent, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    return settings;
  }

  private String padSpace (String txt) {
    StringBuilder txtBuilder = new StringBuilder(txt);
    while (txtBuilder.length() < 6) {
      txtBuilder.append(" ");
    }
    return txtBuilder.toString();
  }

  static class DroPanel extends JPanel {
    private LaserCut      laserCut;
    static DecimalFormat  fmtMm = new DecimalFormat("#0.0");
    static DecimalFormat  fmtCm = new DecimalFormat("#0.00");
    static DecimalFormat  fmtIn = new DecimalFormat("#0.000");
    private String[]      vals;
    private JTextField[]  lbl = new JTextField[3];

    DroPanel (LaserCut laserCut) {
      this(laserCut, "0", "0", "0", false);
    }

    DroPanel (LaserCut laserCut, String x, String y, String z, boolean canEdit) {
      this.laserCut = laserCut;
      vals = new String[] {x, y, z};
      setLayout(new GridLayout(1, 3));
      for (int ii = 0; ii < 3; ii++) {
        JPanel axis = new JPanel();
        String[] lblTxt = {"X", "Y", "Z"};
        axis.add(new JLabel(lblTxt[ii]));
        axis.add(lbl[ii] = new JTextField(vals[ii], 6));
        lbl[ii].setEditable(canEdit);
        lbl[ii].setHorizontalAlignment(JTextField.RIGHT);
        if (!canEdit) {
          lbl[ii].setForeground(Color.darkGray);
          lbl[ii].setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        }
        add(axis);
      }
    }

    String[] getInitialVals () {
      return vals;
    }

    String[] getVals () {
      return new String[] {lbl[0].getText(), lbl[1].getText(), lbl[2].getText()};
    }

    // Responses to "?" command
    //  <Run|MPos:0.140,0.000,0.000|FS:20,0|Pn:Z>
    //  <Idle|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>
    //  <Jog|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>
    void setPosition (String rsp) {
      int idx1 = rsp.indexOf("|MPos:");
      if (idx1 >= 0) {
        idx1 += 6;
        int idx2 = rsp.indexOf('|', idx1);
        if (idx2 > idx1) {
          String[] tmp = rsp.substring(idx1, idx2).split(",");
          if (tmp.length == 3) {
            for (int ii = 0; ii < 3; ii++) {
              double mm = Double.parseDouble(tmp[ii]);
              switch (laserCut.displayUnits) {
                case "in":
                  lbl[ii].setText(fmtIn.format(LaserCut.mmToInches(mm)));
                  break;
                case "mm":
                  lbl[ii].setText(fmtMm.format(mm));
                  break;
                case "cm":
                  lbl[ii].setText(fmtCm.format(LaserCut.mmToCm(mm)));
                  break;
              }
            }
          }
        }
      }
    }
  }

  JMenuItem getGRBLCoordsMenu (LaserCut laserCut, JSSCPort jPort) {
    JMenuItem coords = new JMenuItem("Get GRBL Coordinates");
    coords.addActionListener(ev -> {
      if (jPort.hasSerial()) {
        String receive = sendGrbl(jPort, "$#");
        String[] rsps = receive.split("\n");
        List<ParameterDialog.ParmItem> list = new ArrayList<>();
        for (String rsp : rsps) {
          if (rsp.startsWith("[") && rsp.endsWith("]")) {
            rsp = rsp.substring(1, rsp.length() - 1);
            String[] g1 = rsp.split(":");
            if (g1.length >= 2) {
              String name = g1[0];
              String[] vals = g1[1].split(",");
              if (vals.length == 3) {
                boolean canEdit = false;
                boolean addSep = false;
                if (name.startsWith("G")) {
                  int gNum = Integer.parseInt(name.substring(1));
                  canEdit = gNum >= 54 && gNum <= 59;
                  addSep = gNum == 59;
                }
                list.add(new ParameterDialog.ParmItem(name, new DroPanel(laserCut, vals[0], vals[1], vals[2], canEdit)));
                if (addSep) {
                  list.add(new ParameterDialog.ParmItem(new JSeparator()));
                }
              }
            }
          }
        }
        ParameterDialog.ParmItem[] parmSet = list.toArray(new ParameterDialog.ParmItem[0]);
        ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}, null));
        dialog.setLocationRelativeTo(laserCut);
        dialog.setTitle("Workspace Coordinates");
        dialog.setVisible(true);                                                    // Note: this call invokes dialog
        if (dialog.wasPressed()) {
          for (ParameterDialog.ParmItem parm : parmSet) {
            if (parm.value instanceof DroPanel) {
              DroPanel dro = (DroPanel) parm.value;
              String[] oVals = dro.getVals();
              String[] iVals = dro.getInitialVals();
              if (!iVals[0].equals(oVals[0]) || !iVals[1].equals(oVals[1]) || !iVals[2].equals(oVals[2])) {
                if (parm.name.startsWith("G")) {
                  // Update G55 - G59
                  int num = Integer.parseInt(parm.name.substring(1)) - 53;
                  String cmd = "G90 G20 G10 L2 P" + num + " X" + oVals[0] + " Y" + oVals[1] + " Z" + oVals[2];
                  sendGrbl(jPort, cmd);
                } else if ("G28".equals(parm.name)) {
                  // Placeholder
                } else if ("G30".equals(parm.name)) {
                  // Placeholder
                }
              }
            } else {
              System.out.println(parm.name + ": " + parm.value);
            }
          }
        } else {
          System.out.println("Cancel");
        }
      }
    });
    return coords;
  }

  JMenuItem getGRBLJogMenu (LaserCut laserCut, JSSCPort jPort, boolean probeEnabled) {
    JMenuItem jogMenu = new JMenuItem("Jog Controls");
    jogMenu.addActionListener((ev) -> {
      if (jPort.hasSerial()) {
        if (this instanceof MiniLaser) {
          // Make sure laser is off
          sendGrbl(jPort, "M5", 20);
        }
        // Build Jog Controls
        JPanel frame = new JPanel(new BorderLayout(0, 2));
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout(0, 2));
        DroPanel dro = new DroPanel(laserCut);
        dro.setPosition(sendGrbl(jPort, "?"));                                        // Show initial position
        topPanel.add(dro, BorderLayout.NORTH);
        JSlider speed = new JSlider(10, 100, 100);
        topPanel.add(speed, BorderLayout.SOUTH);
        speed.setMajorTickSpacing(10);
        speed.setPaintTicks(true);
        speed.setPaintLabels(true);
        frame.add(topPanel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(3, 4, 4, 4));
        JLabel tmp;
        Font font2 = new Font("Monospaced", Font.PLAIN, 20);
        // Row 1
        buttons.add(new JogButton(new Arrow(135), jPort, speed, dro, "Y-% X-%"));     // Up Left
        buttons.add(new JogButton(new Arrow(180), jPort, speed, dro, "Y-%"));         // Up
        buttons.add(new JogButton(new Arrow(225), jPort, speed, dro, "Y-% X+%"));     // Up Right
        buttons.add(new JogButton(new Arrow(180), jPort, speed, dro, "Z+%"));         // Up
        // Row 2
        buttons.add(new JogButton(new Arrow(90), jPort, speed, dro, "X-%"));          // Left
        buttons.add(tmp = new JLabel("X/Y", JLabel.CENTER));
        tmp.setFont(font2);
        buttons.add(new JogButton(new Arrow(270), jPort, speed, dro, "X+%"));         // Right
        buttons.add(tmp = new JLabel("Z", JLabel.CENTER));
        tmp.setFont(font2);
        // Row 3
        buttons.add(new JogButton(new Arrow(45), jPort, speed, dro, "Y+% X-%"));      // Down Left
        buttons.add(new JogButton(new Arrow(0), jPort, speed, dro, "Y+%"));           // Down
        buttons.add(new JogButton(new Arrow(315), jPort, speed, dro, "Y+% X+%"));     // Down Right
        buttons.add(new JogButton(new Arrow(0), jPort, speed, dro, "Z-%"));           // Down
        frame.add(buttons, BorderLayout.CENTER);
        // Create button for Jog Dialog
        final JButton setOrigin = getButton("Set Origin");
        final JButton cancel = getButton("Cancel");
        if (probeEnabled) {
          // Setup Probe Z controls
          JPanel probePanel = new JPanel(new GridLayout(1, 2));
          JButton probeButton = new JButton("Probe Z Axis");
          JTextField probeDisp = new JTextField();
          probeDisp.setHorizontalAlignment(JTextField.RIGHT);
          probeDisp.setEditable(false);
          probePanel.add(probeButton);
          probePanel.add(probeDisp);
          frame.add(probePanel, BorderLayout.SOUTH);
          probeButton.addActionListener(ev2 -> {
            if (showConfirmDialog(laserCut, "Is Z Axis Probe Target Ready?", "Caution", YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION) {
              Runnable probe = () -> {
                setOrigin.setEnabled(false);
                cancel.setEnabled(false);
                probeDisp.setText("Probing...");
                int state = 0;
                boolean error = false;
                String rsp = "";
                while (state < 3) {
                  switch (state++) {
                  case 0:
                    rsp = sendGrbl(jPort, "G38.3G20F5 Z-1", 20);                      // Lower Z axis until contact
                    break;
                  case 1:
                    rsp = sendGrbl(jPort, "G38.5G20F5 Z0", 20);                       // Raise until contact lost
                    break;
                  case 2:
                    rsp = sendGrbl(jPort, "G38.3G20F1 Z-1", 20);                      // Lower Z axis at slower speed until contact
                    break;
                  }
                  String[] rLines = rsp.split("\n");
                  if (rLines.length < 2 || !"ok".equals(rLines[1])) {
                    error = true;
                    break;
                  }
                  rsp = rLines[0];
                  if (rsp.toLowerCase().contains("alarm")) {
                    error = true;
                    sendGrbl(jPort, "$X");
                  }
                  if (rsp.startsWith("[PRB:") && rsp.endsWith(":1]")) {
                    rsp = rsp.substring(5, rsp.length() - 3);
                    String[] axes = rsp.split(",");
                    if (axes.length == 3) {
                      probeDisp.setText(axes[2]);
                    } else {
                      error = true;
                      break;
                    }
                  } else {
                    error = true;
                    break;
                  }
                }
                if (error) {
                  probeDisp.setText("Error");
                }
                sendGrbl(jPort, "G0 Z0", 20);                                       // Cancel probe mode and return Z to home
                setOrigin.setEnabled(true);
                cancel.setEnabled(true);
             };
              new Thread(probe).start();
            }
          });
        }
        // Bring up Jog Controls
        Object[] options = new Object[] {setOrigin, cancel};
        if (showOptionDialog(laserCut, frame, "Jog Controls", OK_CANCEL_OPTION, PLAIN_MESSAGE, null, options, options[1]) == 0) {
          // User pressed "Set Origin" so set coords to new position after jog
          try {
            sendGrbl(jPort, "G92X0Y0Z0");
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        } else {
          // User pressed "Cancel" so fast move back to old home position
          try {
            sendGrbl(jPort, "G00X0Y0Z0");
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      } else {
        showMessageDialog(laserCut, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    return jogMenu;
  }

  private static JButton getButton (String label) {
    final JButton button = new JButton(label);
    button.addActionListener(ev2 -> {
      Object tmp = ((JComponent) ev2.getSource()).getParent();
      while (!(tmp instanceof JOptionPane)) {
        tmp = ((JComponent) tmp).getParent();
      }
      ((JOptionPane) tmp).setValue(button);
    });
    return button;
  }

  static class Arrow extends ImageIcon {
    Rectangle bounds = new Rectangle(26, 26);

    Arrow (double rotation) {
      Polygon arrow = new Polygon();
      arrow.addPoint(0, 11);
      arrow.addPoint(10, -7);
      arrow.addPoint(-10, -7);
      arrow.addPoint(0, 11);
      BufferedImage bImg = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = bImg.createGraphics();
      g2.setBackground(Color.white);
      g2.clearRect(0, 0, bounds.width, bounds.height);
      g2.setColor(Color.darkGray);
      AffineTransform at = AffineTransform.getTranslateInstance(bounds.width / 2.0, bounds.height / 2.0);
      at.rotate(Math.toRadians(rotation));
      g2.fill(at.createTransformedShape(arrow));
      g2.setColor(Color.white);
      setImage(bImg);
    }
  }

  static class JogButton extends JButton implements Runnable, JSSCPort.RXEvent {
    private JSSCPort      jPort;
    private JSlider       speed;
    private DroPanel      dro;
    private StringBuilder response = new StringBuilder();
    private String        cmd, lastResponse;
    private long          step, nextStep;
    transient boolean     pressed, running;
    private final JogButton.Lock lock = new JogButton.Lock();

    private static final class Lock { }

    JogButton (Icon icon, JSSCPort jPort, JSlider speed, DroPanel dro, String cmd) {
      super(icon);
      this.jPort = jPort;
      this.speed = speed;
      this.dro = dro;
      this.cmd = cmd;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed (MouseEvent e) {
          super.mousePressed(e);
          // Wait for any prior thread to complete
          while (running) {
            try {
              Thread.sleep(50);
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
          }
          pressed = true;
          running = true;
          (new Thread(JogButton.this)).start();
        }

        @Override
        public void mouseReleased (MouseEvent e) {
          super.mouseReleased(e);
          pressed = false;
        }
      });
    }

    public void run () {
      jPort.setRXHandler(JogButton.this);
      nextStep = step = 0;
      boolean firstPress = true;
      try {
        int sp = speed.getValue();
        double ratio = sp / 100.0;
        String fRate = "F" + (int) Math.max(75 * ratio, 5);
        String sDist = LaserCut.df.format(.1 * ratio);
        String jogCmd = "$J=G91 G20 " + fRate + " " + cmd + "\n";
        jogCmd = jogCmd.replaceAll("%", sDist);
        while (pressed) {
          jPort.sendString(jogCmd);
          stepWait();
          dro.setPosition(lastResponse);
          // Minimum move time 20ms
          if (firstPress) {
            firstPress = false;
            Thread.sleep(20);
          }
          jPort.sendString("?");
          stepWait();
          dro.setPosition(lastResponse);
        }
        jPort.sendByte((byte) 0x85);
        do {
          jPort.sendString("?");
          stepWait();
          dro.setPosition(lastResponse);
        } while (lastResponse.contains("<Jog"));
        Thread.sleep(500);
        running = false;
      } catch (Exception ex) {
        ex.printStackTrace(System.out);
      } finally {
        jPort.removeRXHandler(JogButton.this);
      }
    }

    private void stepWait () throws InterruptedException{
      nextStep++;
      synchronized (lock) {
        while (step < nextStep) {
          lock.wait(20);
        }
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        lastResponse = response.toString();
        response.setLength(0);
        synchronized (lock) {
          step++;
        }
      } else {
        response.append((char) cc);
      }
    }
  }

  /**
   * Send one line GRBL command and return buf (terminated by "ok\n" or timeout)
   * @param jPort Open JSSC port
   * @param cmd command string
   * @return buf string (excluding "ok\n")
   */
  private String sendGrbl (JSSCPort jPort, String cmd) {
    StringBuilder buf = new StringBuilder();
    new GRBLRunner(jPort, cmd, buf);
    return buf.toString();
  }

  private String sendGrbl (JSSCPort jPort, String cmd, int seconds) {
    StringBuilder buf = new StringBuilder();
    new GRBLRunner(jPort, cmd, buf, seconds);
    return buf.toString();
  }

  private static class GRBLRunner extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   buf, line = new StringBuilder();
    private JSSCPort        jPort;
    private int             timeout;
    transient boolean       running = true, done;

    GRBLRunner (JSSCPort jPort, String cmd, StringBuilder rsp) {
      this(jPort, cmd, rsp, 1);
    }

    GRBLRunner (JSSCPort jPort, String cmd, StringBuilder rsp, int seconds) {
      this.jPort = jPort;
      this.buf = rsp;
      this.timeout = seconds * 10;
      jPort.setRXHandler(GRBLRunner.this);
      start();
      try {
        jPort.sendString(cmd + '\n');
        while (!done) {
          Thread.sleep(10);
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        if ("ok".equalsIgnoreCase(line.toString().trim())) {
          running = false;
        }
        line.setLength(0);
        buf.append('\n');
      } else if (cc != '\r'){
        line.append((char) cc);
        buf.append((char) cc);
      }
    }

    public void run () {
      while (running) {
        try {
          Thread.sleep(100);
          if (timeout-- < 0) {
            break;
          }
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
      jPort.removeRXHandler(GRBLRunner.this);
      done = true;
    }
  }

  // https://github.com/gnea/grbl/wiki

  static class GRBLSender extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   response = new StringBuilder();
    private String          lastResponse = "";
    private String[]        cmds, abortCmds;
    private JDialog         frame;
    private JTextArea       grbl;
    private JProgressBar    progress;
    private volatile long   cmdQueue;
    private final Lock      lock = new Lock();
    private JSSCPort        jPort;
    private boolean         doAbort;

    final class Lock { }

    GRBLSender (Frame parent, JSSCPort jPort, String[] cmds) {
      this(parent, jPort, cmds, new String[0]);
    }

    GRBLSender (Frame parent, JSSCPort jPort, String[] cmds, String[] abortCmds) {
      this.jPort = jPort;
      this.cmds = cmds;
      this.abortCmds = abortCmds;
      frame = new JDialog(parent, "G-Code Monitor");
      frame.setLocationRelativeTo(parent);
      frame.add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(cmds.length);
      JScrollPane sPane = new JScrollPane(grbl = new JTextArea());
      grbl.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) grbl.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      grbl.setEditable(false);
      frame.add(sPane, BorderLayout.CENTER);
      JButton abort = new JButton("Abort Job");
      frame.add(abort, BorderLayout.SOUTH);
      abort.addActionListener(ev -> doAbort = true);
      Rectangle loc = frame.getBounds();
      frame.setSize(400, 300);
      frame.setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 150);
      frame.setVisible(true);
      start();
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        String rsp = response.toString();
        if (!rsp.toLowerCase().contains("ok")) {
          grbl.append(lastResponse = rsp);
          grbl.append("\n");
        }
        if (rsp.toLowerCase().contains("error")) {
          doAbort = true;
        }
        response.setLength(0);
        synchronized (lock) {
          cmdQueue--;
        }
      } else if (cc != '\r'){
        response.append((char) cc);
      }
    }

    private void stepWait (int top) throws InterruptedException {
      boolean wait;
      do {
        synchronized (lock) {
          wait = cmdQueue > top && !doAbort;
        }
        if (wait) {
          Thread.sleep(0, 400);
        }
      } while (wait);
    }

    // Responses to "?" command
    //  <Run|MPos:0.140,0.000,0.000|FS:20,0|Pn:Z>
    //  <Idle|MPos:0.000,0.000,0.000|FS:0,0|Pn:Z>

    public void run () {
      jPort.setRXHandler(GRBLSender.this);
      cmdQueue = 0;
      try {
        for (int ii = 0; (ii < cmds.length) && !doAbort; ii++) {
          String gcode = cmds[ii].trim();
          grbl.append(gcode + '\n');
          if (gcode.contains(";")) {
            // Remove comments
            gcode = gcode.substring(0, gcode.indexOf(";")).trim();
          }
          // Ignore blank lines
          if (gcode.length() == 0) {
            continue;
          }
          progress.setValue(ii);
          jPort.sendString(gcode + '\n');
          synchronized (lock) {
            cmdQueue++;
          }
          stepWait(5);                        // max number of 25 character gcode lines in GRBL's 128 byte buffer
        }
        stepWait(0);
        // Wait until all commands have been processed
        boolean waiting = true;
        while (waiting && !doAbort) {
          Thread.sleep(200);
          jPort.sendString("?");              // Set ? command to query status
          stepWait(1);
          if (lastResponse.contains("<Idle")) {
            waiting = false;
          }
        }
        if (doAbort) {
          //jPort.sendByte((byte) 0x18);      // Locks up GRBL (can't jog after issued)
          for (String cmd : abortCmds) {
            jPort.sendString(cmd + "\n");     // Set abort command
            stepWait(1);
          }
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      jPort.removeRXHandler(GRBLSender.this);
      frame.setVisible(false);
      frame.dispose();
    }
  }

  /*
   * * * * * * * * RASTER CODE * * * * * * * *
   */

  static class RasterSettings {
    private int   rasterDpi;          // Raster Size used for Engraving
    private int   feedRate;           // in inches/sec
    private int   laserMin;           // Laser Minimum Power for Engraving
    private int   laserMax;           // Laser Maximum Power for Engraving

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

  static List<String> toGCode (LaserCut.CADRasterImage cadRaster, RasterSettings settings) {
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
      if (cadRaster.centered) {
        at.translate(cadRaster.xLoc - xSize / 2, cadRaster.yLoc - ySize / 2);
        at.rotate(Math.toRadians(cadRaster.rotation), xSize / 2, ySize / 2);
      } else {
        at.translate(cadRaster.xLoc, cadRaster.yLoc);
        at.rotate(Math.toRadians(cadRaster.rotation));
      }
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
            int grey = 255 - data.getElem(yy * imgHyt + xx);                                // Read pixel and convert to greyscale
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
            int grey = 255 - data.getElem(yy * imgHyt + xx);                                // Read pixel and convert to greyscale
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
      // Get workspace location of unrotated upper left corner
      double xOff = cadRaster.centered ? cadRaster.xLoc - xSize / 2 : cadRaster.xLoc;
      double yOff = cadRaster.centered ? cadRaster.yLoc - ySize / 2 : cadRaster.yLoc;
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
            int grey = 255 - data.getElem(yy * imgHyt + xx);                                // Read pixel and convert to greyscale
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
            int grey = 255 - data.getElem(yy * imgHyt + xx);                                // Read pixel and convert to greyscale
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
    buf.add("S0");                                                                          // S0 ; Laser off
    return buf;
  }
}
