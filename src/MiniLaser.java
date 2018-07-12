import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

class MiniLaser {
  private static final int      MINI_POWER_DEFAULT = 255;
  private static final int      MINI_SPEED_DEFAULT = 10;
  private static Dimension      miniSize = new Dimension((int) (7 * LaserCut.SCREEN_PPI), (int) (8 * LaserCut.SCREEN_PPI));
  private static Map<String,String> grblSettings = new LinkedHashMap<>();
  private JSSCPort              jPort;
  private LaserCut              laserCut;

  static {
    // Settings map for GRBL .9, or later
    grblSettings.put("$0",   "Step pulse, usec");
    grblSettings.put("$1",   "Step idle delay, msec");
    grblSettings.put("$2",   "Step port invert, mask");
    grblSettings.put("$3",   "Direction port invert, mask");
    grblSettings.put("$4",   "Step enable invert, boolean");
    grblSettings.put("$5",   "Limit pins invert, boolean");
    grblSettings.put("$6",   "Probe pin invert, boolean");
    grblSettings.put("$10",  "Status report, mask");
    grblSettings.put("$11",  "Junction deviation, mm");
    grblSettings.put("$12",  "Arc tolerance, mm");
    grblSettings.put("$13",  "Report inches, boolean");
    grblSettings.put("$20",  "Soft limits, boolean");
    grblSettings.put("$21",  "Hard limits, boolean");
    grblSettings.put("$22",  "Homing cycle, boolean");
    grblSettings.put("$23",  "Homing dir invert, mask");
    grblSettings.put("$24",  "Homing feed, mm/min");
    grblSettings.put("$25",  "Homing seek, mm/min");
    grblSettings.put("$26",  "Homing debounce, msec");
    grblSettings.put("$27",  "Homing pull-off, mm");
    grblSettings.put("$30",  "Max spindle speed, RPM");
    grblSettings.put("$31",  "Min spindle speed, RPM");
    grblSettings.put("$32",  "Laser mode, boolean");
    grblSettings.put("$100", "X steps/mm");
    grblSettings.put("$101", "Y steps/mm");
    grblSettings.put("$102", "Z steps/mm");
    grblSettings.put("$110", "X Max rate, mm/min");
    grblSettings.put("$111", "Y Max rate, mm/min");
    grblSettings.put("$112", "Z Max rate, mm/min");
    grblSettings.put("$120", "X Acceleration, mm/sec^2");
    grblSettings.put("$121", "Y Acceleration, mm/sec^2");
    grblSettings.put("$122", "Z Acceleration, mm/sec^2");
    grblSettings.put("$130", "X Max travel, mm");
    grblSettings.put("$131", "Y Max travel, mm");
    grblSettings.put("$132", "Z Max travel, mm");
  }

  MiniLaser (LaserCut laserCut) {
    this.laserCut = laserCut;
  }

  JMenu getMiniLaserMenu () throws Exception {
    JMenu miniLaserMenu = new JMenu("Mini Laser");
    jPort = new JSSCPort(laserCut.prefs);
    // Add "Send to Mini Laser" Submenu Item
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
    JTextField tf = new JTextField("1", 4);
    panel.add(tf);
    JMenuItem sendToMiniLazer = new JMenuItem("Send to GRBL to Mini Laser");
    sendToMiniLazer.addActionListener((ActionEvent ex) -> {
      int result = JOptionPane.showConfirmDialog(laserCut, panel, "Send GRBL to Mini Laser", JOptionPane.YES_NO_OPTION,
          JOptionPane.PLAIN_MESSAGE, null);
      if (result == JOptionPane.OK_OPTION) {
        try {
          boolean miniDynamicLaser = laserCut.prefs.getBoolean("dynamicLaser", true);
          int iterations = Integer.parseInt(tf.getText());
          // Generate G_Code for GRBL 1.1
          ArrayList<String> cmds = new ArrayList<>();
          // Add starting G-codes
          cmds.add("G20");                                              // Set Inches as Units
          int speed = Math.max(1, laserCut.prefs.getInt("mini.power", MINI_POWER_DEFAULT));
          cmds.add("M05");                                              // Set Laser Off
          int power = Math.min(1000, laserCut.prefs.getInt("mini.power", MINI_POWER_DEFAULT));
          cmds.add("S" + power);                                        // Set Laser Power (0 - 255)
          cmds.add("F" + speed);                                        // Set cut speed
          double lastX = 0, lastY = 0;
          for (int ii = 0; ii < iterations; ii++) {
            boolean laserOn = false;
            for (LaserCut.CADShape shape : laserCut.surface.selectLaserItems(true)) {
              ArrayList<Line2D.Double> lines = shape.getScaledLines(1);
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
          }
          // Add ending G-codes
          cmds.add("M5");                                               // Set Laser Off
          cmds.add("G00 X0 Y0");                                        // Move back to Origin
          new GRBLSender(cmds.toArray(new String[cmds.size()]));
        } catch (Exception ex2) {
          laserCut.showErrorDialog("Invalid parameter " + tf.getText());
        }
      }
    });
    miniLaserMenu.add(sendToMiniLazer);
    // Add "Mini Lazer Settings" Submenu Item
    JMenuItem miniLazerSettings = new JMenuItem("Mini Lazer Settings");
    miniLazerSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Dynamic Laser", laserCut.prefs.getBoolean("dynamicLaser", true)),
          new ParameterDialog.ParmItem("Power|%", laserCut.prefs.getInt("mini.power", MINI_POWER_DEFAULT)),
          new ParameterDialog.ParmItem("Speed", laserCut.prefs.getInt("mini.speed", MINI_SPEED_DEFAULT))
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, laserCut)) {
        int ii = 0;
        laserCut.prefs.putBoolean("dynamicLaser", (Boolean) parmSet[ii++].value);
        laserCut.prefs.putInt("mini.power", (Integer) parmSet[ii++].value);
        laserCut.prefs.putInt("mini.speed", (Integer) parmSet[ii++].value);
      }
    });
    miniLaserMenu.add(miniLazerSettings);
    // Add "Resize for Mini Lazer" Submenu Item
    JMenuItem miniResize = new JMenuItem("Resize for Mini Lazer (" + (miniSize.width / LaserCut.SCREEN_PPI) +
          "x" + (miniSize.height / LaserCut.SCREEN_PPI) + ")");
    miniResize.addActionListener(ev -> laserCut.surface.setSurfaceSize(miniSize));
    miniLaserMenu.add(miniResize);
    // Add "Jog Controls" Submenu Item
    JMenuItem jog = new JMenuItem("Jog Controls");
    jog.addActionListener((ev) -> {
      // Build Jog Controls
      JPanel frame = new JPanel(new BorderLayout(0, 2));
      JSlider speed = new JSlider(10, 100, 100);
      speed.setMajorTickSpacing(10);
      speed.setPaintTicks(true);
      speed.setPaintLabels(true);
      frame.add(speed, BorderLayout.NORTH);
      JPanel buttons = new JPanel(new GridLayout(3, 4, 4, 4));
      JLabel tmp;
      Font font2 = new Font("Monospaced", Font.PLAIN, 20);
      // Row 1
      buttons.add(new JogButton(new Arrow(135), jPort, speed, "Y-% X-%"));   // Up Left
      buttons.add(new JogButton(new Arrow(180), jPort, speed, "Y-%"));       // Up
      buttons.add(new JogButton(new Arrow(225), jPort, speed, "Y-% X+%"));   // Up Right
      buttons.add(new JogButton(new Arrow(180), jPort, speed, "Z+%"));       // Up
      // Row 2
      buttons.add(new JogButton(new Arrow(90), jPort, speed, "X-%"));        // Left
      buttons.add(tmp = new JLabel("X/Y", JLabel.CENTER));
      tmp.setFont(font2);
      buttons.add(new JogButton(new Arrow(270), jPort, speed, "X+%"));       // Right
      buttons.add(tmp = new JLabel("Z", JLabel.CENTER));
      tmp.setFont(font2);
      // Row 3
      buttons.add(new JogButton(new Arrow(45), jPort, speed, "Y+% X-%"));    // Down Left
      buttons.add(new JogButton(new Arrow(0), jPort, speed, "Y+%"));         // Down
      buttons.add(new JogButton(new Arrow(315), jPort, speed, "Y+% X+%"));   // Down Right
      buttons.add(new JogButton(new Arrow(0), jPort, speed, "Z-%"));         // Down
      frame.add(buttons, BorderLayout.CENTER);
      // Bring up Jog Controls
      Object[] options = {"Set Origin", "Cancel"};
      int res = JOptionPane.showOptionDialog(laserCut, frame, "Jog Controls",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
          null, options, options[0]);
      if (res == JOptionPane.OK_OPTION) {
        // Reset coords to new position after jog
        try {
          jPort.sendString("G92 X0 Y0 Z0\n");
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      } else {
        // Return to old home position
        try {
          jPort.sendString("G00 X0 Y0 Z0\n");
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    });
    miniLaserMenu.add(jog);
    // Add "Get GRBL Settings" Menu Item
    JMenuItem settings = new JMenuItem("Get GRBL Settings");
    settings.addActionListener(ev -> {
      StringBuilder buf = new StringBuilder();
      new GRBLRunner("$I", buf);
      String[] rsps = buf.toString().split("\n");
      String grblVersion = null;
      String grblOptions = null;
      for (String rsp : rsps ) {
        int idx1 = rsp.indexOf("[VER:");
        int idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblVersion = rsp.substring(5, rsp.length() - 2);
          if (grblVersion.contains(":")) {
            grblVersion = grblVersion.split(":")[0];
          }
        }
        idx1 = rsp.indexOf("[OPT:");
        idx2 = rsp.indexOf("]");
        if (idx1 >= 0 && idx2 > 0) {
          grblOptions = rsp.substring(5, rsp.length() - 2);
        }
      }
      buf.setLength(0);
      new GRBLRunner("$$", buf);
      String[] opts = buf.toString().split("\n");
      HashMap<String,String> map = new LinkedHashMap<>();
      for (String opt : opts) {
        String[] vals = opt.split("=");
        if (vals.length == 2) {
          map.put(vals[0], vals[1]);
        }
      }
      JPanel sPanel;
      if (grblVersion != null) {
        sPanel = new JPanel(new GridLayout(grblSettings.size() + 2, 2, 4, 0));
        sPanel.add(new JLabel("GRBL Version: "));
        sPanel.add(new JLabel(grblVersion));
        sPanel.add(new JLabel("GRBL Options: "));
        sPanel.add(new JLabel(grblOptions));
        for (String key : grblSettings.keySet()) {
          sPanel.add(new JLabel(key + " - " + grblSettings.get(key) + ": "));
          sPanel.add(new JLabel(map.get(key)));
        }
      } else {
        sPanel = new JPanel(new GridLayout(map.size() + 1, 2, 4, 0));
        sPanel.add(new JLabel("GRBL Version: unknown"));
        for (String key : map.keySet()) {
          sPanel.add(new JLabel(map.get(key)));
        }
      }
      Object[] options = {"OK"};
      JOptionPane.showOptionDialog(laserCut, sPanel, "GRBL Settings",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
          null, options, options[0]);
    });
    miniLaserMenu.add(settings);
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

  static class Arrow extends ImageIcon {
    Rectangle bounds = new Rectangle(26, 26);
    private Polygon arrow;

    Arrow (double rotation) {
      arrow = new Polygon();
      arrow.addPoint(0, 11);
      arrow.addPoint(10, -7);
      arrow.addPoint(-10, -7);
      arrow.addPoint(0, 11);
      BufferedImage bImg = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = bImg.createGraphics();
      g2.setBackground(Color.white);
      g2.clearRect(0, 0, bounds.width, bounds.height);
      g2.setColor(Color.darkGray);
      AffineTransform at = AffineTransform.getTranslateInstance(bounds.width / 2, bounds.height / 2);
      at.rotate(Math.toRadians(rotation));
      g2.fill(at.createTransformedShape(arrow));
      g2.setColor(Color.white);
      setImage(bImg);
    }
  }

  static class JogButton extends JButton implements Runnable, JSSCPort.RXEvent {
    private JSSCPort    jPort;
    private JSlider     speed;
    private String      cmd;
    private long        step;
    private final Lock  lock = new Lock();
    transient boolean   running;

    private static final class Lock { }

    JogButton (Icon icon, JSSCPort jPort, JSlider speed, String cmd) {
      super(icon);
      this.jPort = jPort;
      this.speed = speed;
      this.cmd = cmd;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed (MouseEvent e) {
          super.mousePressed(e);
          running = true;
          (new Thread(JogButton.this)).start();
        }

        @Override
        public void mouseReleased (MouseEvent e) {
          super.mouseReleased(e);
          running = false;
        }
      });
    }

    public void run () {
      jPort.setRXHandler(JogButton.this);
      step = 0;
      int nextStep = 0;
      boolean firstPress = true;
      try {
        int sp = speed.getValue();
        double ratio = sp / 100.0;
        String fRate = "F" + (int) Math.max(75 * ratio, 5);
        String sDist = LaserCut.df.format(.1 * ratio);
        String jogCmd = "$J=G91 G20 " + fRate + " " + cmd + "\n";
        jogCmd = jogCmd.replaceAll("%", sDist);
        while (running) {
          jPort.sendString(jogCmd);
          nextStep++;
          synchronized (lock) {
            while (step < nextStep) {
              lock.wait(20);
            }
            if (firstPress) {
              Thread.sleep(100);
            }
            firstPress = false;
          }
        }
        jPort.sendByte((byte) 0x85);
        Thread.sleep(500);
      } catch (Exception ex) {
        ex.printStackTrace(System.out);
      } finally {
        jPort.removeRXHandler(JogButton.this);
      }
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        synchronized (lock) {
          step++;
        }
      }
    }
  }

  class GRBLRunner extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   response, line = new StringBuilder();
    private CountDownLatch  latch = new CountDownLatch(1);
    transient boolean       running = true;

    GRBLRunner (String cmd, StringBuilder response) {
      this.response = response;
      jPort.setRXHandler(GRBLRunner.this);
      start();
      try {
        jPort.sendString(cmd + '\n');
        latch.await();
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
        response.append('\n');
      } else if (cc != '\r'){
        line.append((char) cc);
        response.append((char) cc);
      }
    }

    public void run () {
      int timeout = 10;
      while (running) {
        try {
          Thread.sleep(100);
          if (timeout-- < 0)
            break;
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
      latch.countDown();
      jPort.removeRXHandler(GRBLRunner.this);
    }
  }

  // https://github.com/gnea/grbl/wiki

  class GRBLSender extends Thread implements JSSCPort.RXEvent {
    private StringBuilder   response = new StringBuilder();
    private String[]        cmds;
    private JDialog         frame;
    private JTextArea       grbl;
    private JProgressBar    progress;
    private long            step, nextStep;
    private final Lock      lock = new Lock();
    private boolean         doAbort;

    final class Lock { }

    GRBLSender (String[] cmds) {
      this.cmds = cmds;
      frame = new JDialog(laserCut, "G-Code Monitor");
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
      Rectangle loc = laserCut.getBounds();
      frame.setSize(300, 300);
      frame.setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 150);
      frame.setVisible(true);
      start();
    }

    public void rxChar (byte cc) {
      if (cc == '\n') {
        grbl.append(response.toString());
        grbl.append("\n");
        response.setLength(0);
        synchronized (lock) {
          step++;
        }
      } else {
        response.append((char) cc);
      }
    }

    private void stepWait () throws InterruptedException{
      nextStep++;
      synchronized (lock) {
        while (step < nextStep) {
          lock.wait(100);
        }
      }
    }

    public void run () {
      jPort.setRXHandler(GRBLSender.this);
      step = 0;
      nextStep = 0;
      try {
        for (int ii = 0; (ii < cmds.length) && !doAbort; ii++) {
          String gcode = cmds[ii];
          progress.setValue(ii);
          grbl.append(gcode + '\n');
          jPort.sendString(gcode + '\n');
          stepWait();
        }
        //jPort.sendByte((byte) 0x18);      // Locks up GRBL (can't jog after issued)
        jPort.sendString("M5\n");           // Set Laser Off
        stepWait();
        jPort.sendString("G00 X0 Y0\n");    // Move back to Origin
        stepWait();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      jPort.removeRXHandler(GRBLSender.this);
      frame.setVisible(false);
      frame.dispose();
    }
  }
}
