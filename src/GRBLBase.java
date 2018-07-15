import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static javax.swing.JOptionPane.*;
import static javax.swing.JOptionPane.showMessageDialog;

class GRBLBase {

  JMenuItem getGRBLSettingsMenu (LaserCut parent, JSSCPort jPort) {
    JMenuItem settings = new JMenuItem("Get GRBL Settings");
    settings.addActionListener(ev -> {
      if (jPort.hasSerial()) {
      StringBuilder buf = new StringBuilder();
      new GRBLRunner(jPort, "$I", buf);
      String[] rsps = buf.toString().split("\n");
      String grblBuild = null;
      String grblVersion = null;
      String grblOptions = null;
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
      buf.setLength(0);
      new GRBLRunner(jPort, "$$", buf);
      String[] opts = buf.toString().split("\n");
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
          new ParameterDialog.ParmItem("@Grbl Version",  grblVersion),
          new ParameterDialog.ParmItem("@Grbl Build", grblBuild != null ? grblBuild : "unknown"),
          new ParameterDialog.ParmItem("@Grbl Options", grblOptions != null ? grblOptions : "unknown"),
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
        parmSet[3].sepBefore = true;
        Properties info = parent.getProperties(parent.getResourceFile("grbl/grblparms.props"));
        ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}, false, info));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);              // Note: this call invokes dialog
        if (dialog.doAction()) {
          java.util.List<String> cmds = new ArrayList<>();
          for (ParameterDialog.ParmItem parm : parmSet) {
            Object value = parm.value instanceof Boolean ? ((boolean) parm.value ? "1" : "0") :
              parm.value instanceof ParameterDialog.BField ? Integer.toString(((ParameterDialog.BField) parm.value).getValue()) : parm.value;
            if (!parm.readOnly & !parm.lblValue && !value.equals(sVals.get(parm.key))) {
              //System.out.println(parm.name + ": changed from " + sVals.get(parm.key) + " to " + value);
              cmds.add(parm.key + "=" + value);
            }
          }
          if (cmds.size() > 0) {
            new GRBLSender(parent, jPort, cmds.toArray(new String[0]));
          }
        } else {
          System.out.println("Cancel");
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
          sPanel.add(lbl = new JLabel(padSpace(key + ":", 6) + sVals.get(key)));
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

  private String padSpace (String txt, int len) {
    while (txt.length() < len) {
      txt = txt + " ";
    }
    return txt;
  }

  JMenuItem getGRBLJogMenu (Frame parent, JSSCPort jPort) {
    JMenuItem jogMenu = new JMenuItem("Jog Controls");
    jogMenu.addActionListener((ev) -> {
      if (jPort.hasSerial()) {
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
        int res = showOptionDialog(parent, frame, "Jog Controls", OK_CANCEL_OPTION, PLAIN_MESSAGE, null, options, options[0]);
        if (res == OK_OPTION) {
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
      } else {
        showMessageDialog(parent, "No Serial Port Selected", "Error", PLAIN_MESSAGE);
      }
    });
    return jogMenu;
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
    private final JogButton.Lock lock = new JogButton.Lock();
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
    private CountDownLatch latch = new CountDownLatch(1);
    private JSSCPort        jPort;
    transient boolean       running = true;

    GRBLRunner (JSSCPort jPort, String cmd, StringBuilder response) {
      this.jPort = jPort;
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
    private final GRBLSender.Lock lock = new GRBLSender.Lock();
    private JSSCPort        jPort;
    private boolean         doAbort;

    final class Lock { }

    GRBLSender (Frame parent, JSSCPort jPort, String[] cmds) {
      this.jPort = jPort;
      this.cmds = cmds;
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
