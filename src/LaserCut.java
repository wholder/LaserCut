import com.jsevy.jdxf.DXFDocument;
import com.jsevy.jdxf.DXFGraphics;
import jssc.SerialNativeInterface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
//import java.awt.desktop.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import static javax.swing.JOptionPane.*;

  /*
  Links to potentially useful stuff

  See: https://docs.oracle.com/javase/7/docs/api/java/awt/geom/package-summary.html
  Gears: http://lcamtuf.coredump.cx/gcnc/ch6/
  PDF export: http://trac.erichseifert.de/vectorgraphics2d/wiki/Usage
  Export to EPS, SVG, PDF: https://github.com/eseifert/vectorgraphics2d
  Zoom & Pan: https://community.oracle.com/thread/1263955
  https://developer.apple.com/library/content/documentation/Java/Conceptual/Java14Development/07-NativePlatformIntegration/NativePlatformIntegration.html
  http://www.ntu.edu.sg/home/ehchua/programming/java/j4a_gui_2.html

  Marlin Renderer: https://github.com/bourgesl/marlin-renderer

  Bézier Curves:
    https://pomax.github.io/bezierinfo/

  Fun with Java2D - Strokes:
    http://www.jhlabs.com/java/java2d/strokes/

  Engraving with G-Code:
    https://github.com/nebarnix/img2gco/
    https://github.com/magdesign/Raster2Gcode
    https://github.com/Uthayne/3dpBurner-Image2Gcode
    https://www.picengrave.com/Pic%20Programs%20Page/PDF%20Files/misc/Understanding%20Gcode.pdf

  Import/Export to/from DXF
    https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/
    https://www.codeproject.com/Articles/3398/CadLib-for-creating-DXF-Drawing-Interchange-Format

  Mini Laser Engravers:
    K3 Laser:   https://github.com/RBEGamer/K3_LASER_ENGRAVER_PROTOCOL
    EzGrazer:   https://github.com/camrein/EzGraver
                https://github.com/camrein/EzGraver/issues/43
    nejePrint:  https://github.com/AxelTB/nejePrint
    LaserGRBL:  http://lasergrbl.com/en/ and https://github.com/arkypita/LaserGRBL
  */

public class LaserCut extends JFrame {
  static final String           VERSION = "1.1 beta";
  static final double           SCREEN_PPI = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
  static final DecimalFormat    df = new DecimalFormat("#0.0###");
  private static final int      cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
  final transient Preferences   prefs = Preferences.userRoot().node(this.getClass().getName());
  DrawSurface                   surface;
  private final JScrollPane     scrollPane;
  private final JMenuBar        menuBar = new JMenuBar();
  private final JMenuItem       gerberZip;
  private int                   pxDpi = prefs.getInt("svg.pxDpi", 96);
  private long                  savedCrc;
  String                        displayUnits = prefs.get("displayUnits", "in");
  private boolean               useMouseWheel = prefs.getBoolean("useMouseWheel", false);
  private boolean               snapToGrid = prefs.getBoolean("snapToGrid", true);
  private boolean               displayGrid = prefs.getBoolean("displayGrid", true);
  private String                onStartup = prefs.get("onStartup", "demo");
  private OutputDevice          outputDevice;
  private final int                   deviceMenuSlot;
  private final Map<String,String>    menuToShape = new HashMap<>();
  private final Map<String,String>    shapeNames = new LinkedHashMap<>();

  {
    shapeNames.put("CADReference",      "Reference Point");
    shapeNames.put("CADRectangle",      "Rectangle");
    shapeNames.put("CADOval",           "Oval");
    shapeNames.put("CADPolygon",        "Regular Polygon");
    shapeNames.put("CADText",           "Text");
    shapeNames.put("CADShapeSpline",    "Spline Curve");
    shapeNames.put("CADGear",           "Gear");
    shapeNames.put("CADNemaMotor",      "NEMA Stepper");
    shapeNames.put("CADBobbin",         "Bobbin");
    shapeNames.put("CADRasterImage",    "Raster Image");
    shapeNames.put("CADMusicStrip",     "Music Box Strip");
  }

  static class SurfaceSettings implements Serializable {
    private static final long   serialVersionUID = 1281736566222122122L;
    public final Point                viewPoint;
    public final double               zoomFactor;
    public final double gridStep;
    public final int                  gridMajor;

    SurfaceSettings (Point viewPoint, double zoomFactor, double gridStep, int gridMajor) {
      this.viewPoint = viewPoint;
      this.zoomFactor = zoomFactor;
      this.gridStep = gridStep;
      this.gridMajor = gridMajor;
    }
  }

  interface OutputDevice {
    String getName();
    JMenu getDeviceMenu () throws Exception;
    void closeDevice () throws Exception;
    Rectangle2D.Double getWorkspaceSize ();
    double getZoomFactor ();
  }

  private boolean quitHandler () {
    if (savedCrc == surface.getDesignChecksum() || showWarningDialog("You have unsaved changes!\nDo you really want to quit?")) {
      try {
        if (outputDevice != null) {
          outputDevice.closeDevice();
        }
      } catch (Throwable ex) {
        ex.printStackTrace();
      }
      return true;
    }
    return false;
  }

  private void showAboutBox () {
    ImageIcon icon = null;
    try {
      icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/LaserCut Logo.png")));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    String jsscInfo = null;
    try {
      jsscInfo = "  Java Simple Serial Connector " + SerialNativeInterface.getLibraryVersion() + "\n" +
                 "  JSSC Native Code DLL Version " + SerialNativeInterface.getNativeLibraryVersion() + "\n";
    } catch (Throwable ex) {
      ex.printStackTrace();
    }
    showMessageDialog(this,
        "By: Wayne Holder\n" +
        "  Java Version: " + System.getProperty("java.version") + "\n" +
        "  LibLaserCut " + com.t_oster.liblasercut.LibInfo.getVersion() + "\n" +
        (jsscInfo != null ? jsscInfo : "") +
        "  Apache PDFBox " + org.apache.pdfbox.util.Version.getVersion() + "\n" +
        "  Screen PPI " + SCREEN_PPI,
        "LaserCut " + VERSION,
        INFORMATION_MESSAGE,
        icon);
  }

  private void showPreferencesBox () {
    Map<String,ParameterDialog.ParmItem> items = new LinkedHashMap<>();
    items.put("onStartup", new ParameterDialog.ParmItem("On Startup:Blank Page|blank:Reopen Last File|reopen:Demo Page|demo", onStartup));
    String device = Integer.toString(prefs.getInt("outputDevice", 0));
    items.put("outputDevice", new ParameterDialog.ParmItem("Output Device:None|0:Epilog Zing|1:Mini Laser|2:" +
                                                           "Micro Laser|3:MiniCNC|4:Silhouette|5:Mini Cutter|6", device));
    items.put("useMouseWheel", new ParameterDialog.ParmItem("Mouse Wheel Scrolling", prefs.getBoolean("useMouseWheel", false)));
    items.put("useDblClkZoom", new ParameterDialog.ParmItem("Double-click Zoom{Dbl click to Zoom 2x, Shift + dbl click to unZoom}",
        prefs.getBoolean("useDblClkZoom", false)));
    items.put("enableGerber", new ParameterDialog.ParmItem("Enable Gerber ZIP Import", prefs.getBoolean("gerber.import", false)));
    items.put("pxDpi", new ParameterDialog.ParmItem("px per Inch (SVG Import/Export)", prefs.getInt("svg.pxDpi", 96)));
    ParameterDialog.ParmItem[] parmSet = items.values().toArray(new ParameterDialog.ParmItem[0]);
    ParameterDialog dialog = (new ParameterDialog("LaserCut Preferences", parmSet, new String[] {"Save", "Cancel"}, displayUnits));
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);              // Note: this call invokes dialog
    if (dialog.wasPressed() ) {
      for (String name : items.keySet()) {
        ParameterDialog.ParmItem parm = items.get(name);
        if ("useMouseWheel".equals(name)) {
          prefs.putBoolean("useMouseWheel", useMouseWheel = (Boolean) parm.value);
          configureMouseWheel();
        } else if ("outputDevice".equals(name)) {
          String newDevice = (String) parm.value;
          if (!newDevice.equals(device)) {
            prefs.putInt("outputDevice", Integer.parseInt(newDevice));
            menuBar.setEnabled(false);
            try {
              if (outputDevice != null) {
                outputDevice.closeDevice();
                menuBar.remove(deviceMenuSlot);
                menuBar.revalidate();
                menuBar.repaint();
                outputDevice = null;
              }
            } catch (Throwable ex) {
              ex.printStackTrace();
            }
            JMenu deviceMenu = getOutputDeviceMenu();
            if (deviceMenu != null) {
              menuBar.add(deviceMenu, deviceMenuSlot);
            }
            menuBar.setEnabled(true);
          }
        } else if ("useDblClkZoom".equals(name)) {
          surface.setDoubleClickZoomEnable((Boolean) parm.value);
        } else if ("enableGerber".equals(name)) {
          boolean enabled = (Boolean) parm.value;
          prefs.putBoolean("gerber.import", (Boolean) parm.value);
          gerberZip.setVisible(enabled);
        } else if ("pxDpi".equals(name)) {
          pxDpi = (Integer) parm.value;
          prefs.putInt("svg.pxDpi", pxDpi);
        } else if ("onStartup".equals(name)) {
          onStartup = (String) parm.value;
          prefs.put("onStartup", onStartup);
        } else {
          System.out.println(name + ": " + parm.value);
        }
      }
    }
  }

  private void configureMouseWheel () {
    if (useMouseWheel) {
      // Set units so scroll isn't sluggish
      scrollPane.getVerticalScrollBar().setUnitIncrement(16);
      scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
    } else {
      scrollPane.setWheelScrollingEnabled(false);
    }
  }

  abstract class FileChooserMenu extends JMenuItem {
    final JFileChooser  fileChooser;
    String        currentPath;

    FileChooserMenu (String type, String ext, boolean save) {
      super(save ? "Export to " + type + " File" :  "Import " + type + " File");
      fileChooser = new JFileChooser();
      buildInterface(type, ext, save);
    }

    void buildInterface (String type, String ext, boolean save) {
      addActionListener(ev -> {
        fileChooser.setDialogTitle(save ? "Export " + type + " File" :  "Import " + type + " File");
        fileChooser.setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
        FileNameExtensionFilter nameFilter = new FileNameExtensionFilter(type + " files (*." + ext + ")", ext);
        fileChooser.addChoosableFileFilter(nameFilter);
        fileChooser.setFileFilter(nameFilter);
        fileChooser.setCurrentDirectory(new File(currentPath = prefs.get("default." + ext + ".dir", "/")));
        currentPath = fileChooser.getCurrentDirectory().getPath();
        fileChooser.addPropertyChangeListener(new PropertyChangeListener() {
          @Override
          public void propertyChange (PropertyChangeEvent evt) {
            String path = fileChooser.getCurrentDirectory().getPath();
            if (!path.equals(currentPath)) {
              currentPath = path;
              prefs.put("default." + ext + ".dir", currentPath = path);
            }
          }
        });
        if (openDialog(save)) {
          File sFile = fileChooser.getSelectedFile();
          if (save && !sFile.exists()) {
            String fPath = sFile.getPath();
            if (!fPath.contains(".")) {
              sFile = new File(fPath + "." + ext);
            }
          }
          try {
            if (!save || (!sFile.exists() || showWarningDialog("Overwrite Existing file?"))) {
              processFile(sFile);
            }
          } catch (Exception ex) {
            showErrorDialog(save ? "Unable to save file" : "Unable to open file");
            ex.printStackTrace();
          }
          prefs.put("default." + ext, sFile.getAbsolutePath());
        }
      });
    }

    private boolean openDialog (boolean save) {
      if (save) {
        return fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION;
      } else {
        return fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION;
      }
    }

    abstract void processFile (File sFile) throws Exception;

    // Override in subclass
    void addAccessory () {}
  }

  abstract class DxfFileChooserMenu extends FileChooserMenu {
    private List<JCheckBox> checkboxes;
    private String          selected;
    private final String dUnits;

    DxfFileChooserMenu (String type, String ext, boolean save, String dUnits) {
      super(type, ext, save);
      this.dUnits = dUnits;
    }

    @Override
    void buildInterface (String type, String ext, boolean save){
      super.buildInterface(type, ext, save);
      checkboxes = new ArrayList<>();
      // Widen JChooser by 25%
      Dimension dim = getPreferredSize();
      setPreferredSize(new Dimension((int) (dim.width * 1.25), dim.height));
      String[] units = {"Inches:in", "Centimeters:cm", "Millimeters:mm"};
      JPanel unitsPanel = new JPanel(new GridLayout(0, 1));
      ButtonGroup group = new ButtonGroup();
      for (String unit : units) {
        String[] parts = unit.split(":");
        JRadioButton button = new JRadioButton(parts[0]);
        if (parts[1].equals(dUnits)){
          button.setSelected(true);
          selected = parts[1];
        }
        group.add(button);
        unitsPanel.add(button);
        button.addActionListener(ev -> selected = parts[1]);
      }
      JPanel panel = new JPanel(new GridLayout(0, 1));
      panel.add(getPanel(save ? "File Units:" : "Default Units:", unitsPanel));
      if (save) {
        panel.add(new JPanel());
      } else {
        String[] options = {"TEXT", "MTEXT", "DIMENSION"};
        JPanel importPanel = new JPanel(new GridLayout(0, 1));
        for (String option : options) {
          JCheckBox checkbox = new JCheckBox(option);
          importPanel.add(checkbox);
          checkboxes.add(checkbox);
        }
        panel.add(getPanel("Include:", importPanel));
      }
      fileChooser.setAccessory(panel);
    }

    private JPanel getPanel (String heading, JComponent guts) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBackground(Color.WHITE);
      JLabel label = new JLabel(heading, JLabel.CENTER);
      label.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      panel.add(label, BorderLayout.NORTH);
      panel.add(guts, BorderLayout.CENTER);
      return panel;
    }

    String getSelectedUnits () {
      return selected;
    }

    boolean isOptionSelected (String name) {
      for (JCheckBox checkbox : checkboxes) {
        if (checkbox.getText().equals(name)) {
          return checkbox.isSelected();
        }
      }
      return false;
    }
  }

  /**
   * Custom file choose for DXF files that allows selective import of TEXT, MTEXT and DIMENSION elements
   */

  static class DxfFileChooser extends JFileChooser {
    private final List<JCheckBox> checkboxes = new ArrayList<>();
    private String          selected;

    public DxfFileChooser (String dUnits, boolean save) {
      setDialogTitle(save ? "Export DXF File" : "Import DXF File");
      setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("AutoCad DXF files (*.dxf)", "dxf");
      addChoosableFileFilter(nameFilter);
      // Widen JChooser by 25%
      Dimension dim = getPreferredSize();
      setPreferredSize(new Dimension((int) (dim.width * 1.25), dim.height));
      setFileFilter(nameFilter);
      setAcceptAllFileFilterUsed(true);
      String[] units = {"Inches:in", "Centimeters:cm", "Millimeters:mm"};
      JPanel unitsPanel = new JPanel(new GridLayout(0, 1));
      ButtonGroup group = new ButtonGroup();
      for (String unit : units) {
        String[] parts = unit.split(":");
        JRadioButton button = new JRadioButton(parts[0]);
        if (parts[1].equals(dUnits)){
          button.setSelected(true);
          selected = parts[1];
        }
        group.add(button);
        unitsPanel.add(button);
        button.addActionListener(ev -> selected = parts[1]);
      }
      JPanel panel = new JPanel(new GridLayout(0, 1));
      panel.add(getPanel(save ? "File Units:" : "Default Units:", unitsPanel));
      if (save) {
        panel.add(new JPanel());
      } else {
        String[] options = {"TEXT", "MTEXT", "DIMENSION"};
        JPanel importPanel = new JPanel(new GridLayout(0, 1));
        for (String option : options) {
          JCheckBox checkbox = new JCheckBox(option);
          importPanel.add(checkbox);
          checkboxes.add(checkbox);
        }
        panel.add(getPanel("Include:", importPanel));
      }
      setAccessory(panel);
    }

    private JPanel getPanel (String heading, JComponent guts) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBackground(Color.WHITE);
      JLabel label = new JLabel(heading, JLabel.CENTER);
      label.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      panel.add(label, BorderLayout.NORTH);
      panel.add(guts, BorderLayout.CENTER);
      return panel;
    }

    String getSelectedUnits () {
      return selected;
    }

    boolean isOptionSelected (String name) {
      for (JCheckBox checkbox : checkboxes) {
        if (checkbox.getText().equals(name)) {
          return checkbox.isSelected();
        }
      }
      return false;
    }
  }

  //   Note: unable to use CMD-A, CMD-C, CMD-H, CMD-Q as shortcut keys
  static class MyMenuItem extends JMenuItem {
    private static final Map<String,String> keys = new HashMap<>();

    MyMenuItem (String name, int key, int mask, boolean enabled) {
      this(name, key, mask);
      setEnabled(enabled);
    }

    MyMenuItem (String name, int key, int mask) {
      super(name);
      String code = key + ":" + mask;
      if (keys.containsKey(code)) {
        throw new IllegalStateException("Duplicate accelerator key for '" + keys.get(code) + "' & '" + name + "'");
      }
      keys.put(code, name);
      setAccelerator(KeyStroke.getKeyStroke(key, mask));
    }
  }

  private LaserCut () {
    setTitle("LaserCut");
    surface = new DrawSurface(prefs, scrollPane = new JScrollPane());
    scrollPane.setViewportView(surface);
    JTextField itemInfo = new JTextField();
    surface.registerInfoJTextField(itemInfo);
    configureMouseWheel();
    add(scrollPane, BorderLayout.CENTER);
    JPanel bottomPane = new JPanel(new BorderLayout());
    bottomPane.setBorder(new EmptyBorder(1, 4, 1, 1));
    bottomPane.add(new JLabel("Info: "), BorderLayout.WEST);
    bottomPane.add(itemInfo, BorderLayout.CENTER);
    itemInfo.setEditable(false);
    itemInfo.setFocusable(false);
    bottomPane.setFocusable(false);
    add(bottomPane, BorderLayout.SOUTH);
    requestFocusInWindow();
    boolean hasAboutHandler = false;
    boolean hasPreferencesHandler = false;
    boolean hasQuitHandler = false;
    /*
    if (Taskbar.isTaskbarSupported()) {
      Taskbar taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        taskbar.setIconImage(new ImageIcon(getClass().getResource("/images/laser_wip_black.png")).getImage());
      }
    }
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.APP_SUDDEN_TERMINATION)) {
        desktop.disableSuddenTermination();
      }
      if (hasAboutHandler = desktop.isSupported(Desktop.Action.APP_ABOUT)) {
        desktop.setAboutHandler(new AboutHandler() {
          @Override
          public void handleAbout (AboutEvent e) {
            showAboutBox();
          }
        });
      }
      if (hasQuitHandler = desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        desktop.setQuitHandler(new QuitHandler() {
          @Override
          public void handleQuitRequestWith (QuitEvent e, QuitResponse response) {
            if (quitHandler()) {
              response.performQuit();
            } else {
              response.cancelQuit();
            }
          }
        });
      }
      if (hasPreferencesHandler = desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
        desktop.setPreferencesHandler(new PreferencesHandler() {
          @Override
          public void handlePreferences (PreferencesEvent e) {
            showPreferencesBox();
          }
        });
      }
    }*/
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing (WindowEvent e) {
        if (quitHandler()) {
          System.exit(0);
        }
      }
    });
    // Add Menu Bar to Window
    setJMenuBar(menuBar);
    surface.addPlacerListener(new DrawSurface.PlacerListener() {
      @Override
      public void placeActive (boolean placing) {
        for (int ii = 0; ii < menuBar.getMenuCount(); ii++) {
          menuBar.getMenu(ii).setEnabled(!placing);
        }
      }
    });
    /*
     *  Add "File" Menu
     */
    JMenu fileMenu = new JMenu("File");
    if (!hasAboutHandler) {
      //
      // Add "About" Item to File Menu
      //
      JMenuItem aboutBox = new JMenuItem("About " + getClass().getSimpleName());
      aboutBox.addActionListener(ev -> showAboutBox());
      fileMenu.add(aboutBox);
    }
    if (!hasPreferencesHandler) {
      //
      // Add "Preferences" Item to File Menu
      //
      JMenuItem preferencesBox = new JMenuItem("Preferences");
      preferencesBox.addActionListener(ev -> {
        showPreferencesBox();
      });
      fileMenu.add(preferencesBox);
    }
    fileMenu.addSeparator();
    //
    // Add "New" Item to File Menu
    //
    JMenuItem newObj = new MyMenuItem("New", KeyEvent.VK_N, cmdMask);
    newObj.addActionListener(ev -> {
      if (surface.hasData()) {
        if (!showWarningDialog("Discard current design?")) {
          return;
        }
        surface.clear();
        savedCrc = surface.getDesignChecksum();
        setTitle("LaserCut");
      }
    });
    fileMenu.add(newObj);
    //
    // Add "Open" Item to File menu
    //
    JMenuItem loadObj = new MyMenuItem("Open", KeyEvent.VK_O, cmdMask);
    loadObj.addActionListener(ev -> {
      if (surface.hasData()) {
        if (!showWarningDialog("Discard current design?"))
          return;
      }
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Open a LaserCut File");
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        surface.pushToUndoStack();
        File tFile = fileChooser.getSelectedFile();
        try {
          FileInputStream fileIn = new FileInputStream(tFile);
          ObjectInputStream in = new FixInputStream(fileIn);
          ArrayList<CADShape> design = (ArrayList<CADShape>) in.readObject();
          SurfaceSettings settings = null;
          try {
            // Read DrawSurface setting and JScrollPane position, if available
            settings = (SurfaceSettings) in.readObject();
            scrollPane.getViewport().setViewPosition(settings.viewPoint);
          } catch (Exception ex) {
            // Ignore (catching EOFException is the only way to tell if SurfaceSettings object was serialized)
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
          }
          in.close();
          fileIn.close();
          surface.setDesign(design, settings);
          savedCrc = surface.getDesignChecksum();
          prefs.put("default.dir", tFile.getAbsolutePath());
          prefs.put("lastFile", tFile.getAbsolutePath());
          setTitle("LaserCut - (" + tFile + ")");
        } catch (Exception ex) {
          showErrorDialog("Unable to load file: " + tFile);
          ex.printStackTrace(System.out);
        }
      }
    });
    fileMenu.add(loadObj);
    //
    // Add "Save As" Item to File menu
    //
    JMenuItem saveAs = new MyMenuItem("Save As", KeyEvent.VK_S, cmdMask);
    saveAs.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Save a LaserCut File");
      fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fPath = sFile.getPath();
        if (!fPath.contains(".")) {
          sFile = new File(fPath + ".lzr");
        }
        try {
          if (!sFile.exists() || showWarningDialog("Overwrite Existing file?")) {
            FileOutputStream fileOut = new FileOutputStream(sFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(surface.getDesign());
            // Save DrawSurface setting and JScrollPane position
            JViewport viewPort = scrollPane.getViewport();
            Point viewPosition = viewPort.getViewPosition();
            double zoomFactor = surface.getZoomFactor();
            double gridSize = surface.getGridSize();
            int gridMajor = surface.getGridMajor();
            SurfaceSettings settings = new SurfaceSettings(viewPosition, zoomFactor, gridSize, gridMajor);
            out.writeObject(settings);
            out.close();
            fileOut.close();
            savedCrc = surface.getDesignChecksum();
          }
          setTitle("LaserCut - (" + sFile + ")");
        } catch (IOException ex) {
          showErrorDialog("Unable to save file");
          ex.printStackTrace();
        }
        prefs.put("default.dir", sFile.getAbsolutePath());
      }
    });
    fileMenu.add(saveAs);
    //
    // Add "Save Selected As" Item to File menu
    //
    JMenuItem saveSelected = new JMenuItem("Save Selected As");
    saveSelected.setEnabled(false);
    saveSelected.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Save Selected Shape as LaserCut File");
      fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setCurrentDirectory(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fPath = sFile.getPath();
        if (!fPath.contains(".")) {
          sFile = new File(fPath + ".lzr");
        }
        try {
          if (!sFile.exists() || showWarningDialog("Overwrite Existing file?")) {
            FileOutputStream fileOut = new FileOutputStream(sFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(surface.getSelectedAsDesign());
            out.close();
            fileOut.close();
            savedCrc = surface.getDesignChecksum();
            setTitle("LaserCut - (" + sFile + ")");
          }
        } catch (IOException ex) {
          showErrorDialog("Unable to save file");
          ex.printStackTrace();
        }
        prefs.put("default.dir", sFile.getAbsolutePath());
      }
    });
    fileMenu.add(saveSelected);
    if (!hasQuitHandler) {
      // Add "Quit" Item to File menu
      JMenuItem quitObj = new MyMenuItem("Quit", KeyEvent.VK_Q, cmdMask);
      quitObj.addActionListener(ev -> {
        if (quitHandler()) {
          System.exit(0);
        }
      });
      fileMenu.add(quitObj);
    }
    menuBar.add(fileMenu);
    /*
     *  Add "Shapes" Menu
     */
    JMenu shapesMenu = new JMenu("Shapes");
    // Build menuToShape HashMap
    Map<String,String> menuToShape = new HashMap<>();
    for (String className : shapeNames.keySet()) {
      String menuName = shapeNames.get(className);
      try {
        Class<?> ref = Class.forName(className);
        CADShape shp = (CADShape) ref.getDeclaredConstructor().newInstance();
        String sName = shp.getName();
        menuToShape.put(menuName, className);

      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    // Build "Shapes" menu
    for (String className : shapeNames.keySet()) {
      JMenuItem mItem = new JMenuItem(shapeNames.get(className));
      mItem.addActionListener(ev -> {
        try {
          String menuName = mItem.getText();
          Class<?> ref = Class.forName(menuToShape.get(menuName));
          CADShape shp = (CADShape) ref.getDeclaredConstructor().newInstance();
          shp.createAndPlace(surface, this);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      shapesMenu.add(mItem);
    }
    menuBar.add(shapesMenu);
    /*
     *  Add "Grid" menu
     */
    JMenu gridMenu = new JMenu("Grid");
    //
    // Add "Snap to Grid" Menu Item
    //
    JCheckBoxMenuItem gridSnap = new JCheckBoxMenuItem("Snap to Grid", snapToGrid);
    gridSnap.addActionListener(ev -> {
      prefs.putBoolean("snapToGrid", snapToGrid = gridSnap.getState());
      surface.enableGridSnap(snapToGrid);
    });
    surface.enableGridSnap(snapToGrid);
    gridMenu.add(gridSnap);
    //
    // Add "Show Grid" Menu Item
    //
    JCheckBoxMenuItem gridShow = new JCheckBoxMenuItem("Show Grid", displayGrid);
    gridShow.addActionListener(ev -> {
      prefs.putBoolean("displayGrid", displayGrid = gridShow.getState());
      surface.enableGridDisplay(displayGrid);
    });
    surface.enableGridDisplay(displayGrid);
    gridMenu.add(gridShow);
    surface.addGridMenu(gridMenu);
    menuBar.add(gridMenu);
    //
    // Add "Zoom" Menu
    //
    menuBar.add(surface.getZoomMenu());
    /*
     *  Add "Edit" Menu
    */
    JMenu editMenu = new JMenu("Edit");
    //
    // Add "Undo" Menu Item
    //
    JMenuItem undo = new MyMenuItem("Undo", KeyEvent.VK_Z, cmdMask, false);
    undo.addActionListener((ev) -> surface.popFromUndoStack());
    editMenu.add(undo);
    surface.addUndoListener(undo::setEnabled);
    //
    // Add "Redo" Menu Item
    //
    JMenuItem redo = new MyMenuItem("Redo", KeyEvent.VK_Z, cmdMask + InputEvent.SHIFT_DOWN_MASK, false);
    redo.addActionListener((ev) -> surface.popFromRedoStack());
    editMenu.add(redo);
    surface.addRedoListener(redo::setEnabled);
    //
    // Add "Remove Selected" Menu Item
    //
    JMenuItem removeSelected = new MyMenuItem("Delete Selected", KeyEvent.VK_X, cmdMask, false);
    removeSelected.addActionListener((ev) -> surface.removeSelected());
    editMenu.add(removeSelected);
    //
    // Add "Duplicate Selected" Menu Item
    //
    JMenuItem dupSelected = new MyMenuItem("Duplicate Selected", KeyEvent.VK_D, cmdMask, false);
    dupSelected.addActionListener((ev) -> surface.duplicateSelected());
    editMenu.add(dupSelected);
    //
    // Add "Edit Selected" Menu Item
    //
    JMenuItem editSelected = new MyMenuItem("Edit Selected", KeyEvent.VK_E, cmdMask, false);
    editSelected.addActionListener((ev) -> {
      CADShape sel = surface.getSelected();
      boolean centered = sel.centered;
      if (sel.editParameterDialog(surface, displayUnits)) {
        // User clicked dialog's OK button
        if (centered != sel.centered) {
          Rectangle2D bounds = sel.getShape().getBounds2D();
          if (sel.centered) {
            sel.movePosition(new Point2D.Double(bounds.getWidth() / 2, bounds.getHeight() / 2));
          } else {
            sel.movePosition(new Point2D.Double(-bounds.getWidth() / 2, -bounds.getHeight() / 2));
          }
        }
        sel.updateShape();
        surface.repaint();
      }
    });
    editMenu.add(editSelected);
    //
    // Add "Move Selected" Menu Item
    //
    JMenuItem moveSelected = new MyMenuItem("Move Selected", KeyEvent.VK_M, cmdMask, false);
    moveSelected.addActionListener((ev) -> surface.moveSelected());
    editMenu.add(moveSelected);
    //
    // Add "Group Selected" Menu Item
    //
    JMenuItem groupDragSelected = new MyMenuItem("Group Selected", KeyEvent.VK_G, cmdMask, false);
    groupDragSelected.addActionListener((ev) -> surface.groupDragSelected());
    editMenu.add(groupDragSelected);
    //
    // Add "Ungroup Selected" Menu Item
    //
    JMenuItem unGroupSelected = new MyMenuItem("Ungroup Selected", KeyEvent.VK_U, cmdMask, false);
    unGroupSelected.addActionListener((ev) -> surface.unGroupSelected());
    editMenu.add(unGroupSelected);
    //
    // Add "Add Grouped Shapes" Menu Item
    //
    JMenuItem addSelected = new MyMenuItem("Add Grouped Shape{s) to Selected Shape", KeyEvent.VK_A, cmdMask, false);
    addSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(true));
    editMenu.add(addSelected);
    //
    // Add "Subtract Group from Selected" Menu Item
    //
    JMenuItem subtractSelected = new MyMenuItem("Subtract Grouped Shape(s) from Selected Shape", KeyEvent.VK_T, cmdMask, false);
    subtractSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(false));
    editMenu.add(subtractSelected);
    //
    // Add "Combine Selected Paths" Menu Item
    //
    JMenuItem combineDragSelected = new MyMenuItem("Combine Selected Paths", KeyEvent.VK_C, cmdMask, false);
    combineDragSelected.setToolTipText("Experimental Feature");
    combineDragSelected.addActionListener((ev) -> surface.combineDragSelected());
    editMenu.add(combineDragSelected);
    //
    // Add "Align Grouped Shape(s) to Selected Shape's" submenu
    //
    JMenu alignMenu = new JMenu("Align Grouped Shape(s) to Selected Shape's");
    alignMenu.setEnabled(false);
    //
    // Add "X Coord" Submenu Item
    //
    JMenuItem alignXSelected = new JMenuItem("X Coord");
    alignXSelected.addActionListener((ev) -> surface.alignSelectedShapes(true, false));
    alignMenu.add(alignXSelected);
    //
    // Add "Y Coord" Submenu Item
    //
    JMenuItem alignYSelected = new JMenuItem("Y Coord");
    alignYSelected.addActionListener((ev) -> surface.alignSelectedShapes(false, true));
    alignMenu.add(alignYSelected);
    //
    // Add "X & Y Coord" Submenu Item
    //
    JMenuItem alignXYSelected = new JMenuItem("X & Y Coords");
    alignXYSelected.addActionListener((ev) -> surface.alignSelectedShapes(true, true));
    alignMenu.add(alignXYSelected);
    editMenu.add(alignMenu);
    //
    // Add "Rotate Group Around Selected Shape" Menu Item
    //
    JMenuItem rotateSelected = new MyMenuItem("Rotate Group Around Selected Shape's Origin", KeyEvent.VK_R, cmdMask, false);
    rotateSelected.addActionListener((ev) ->  surface.rotateGroupAroundSelected());
    editMenu.add(rotateSelected);
    //
    // Add "Round Corners of Selected Shape" Menu Item
    //
    JMenuItem roundCorners = new MyMenuItem("Round Corners of Selected Shape", KeyEvent.VK_B, cmdMask, false);
    roundCorners.addActionListener((ev) -> {
      ParameterDialog.ParmItem[] rParms = {new ParameterDialog.ParmItem("radius|in", 0d)};
      ParameterDialog rDialog = (new ParameterDialog("Edit Parameters", rParms, new String[] {"Round", "Cancel"}, displayUnits));
      rDialog.setLocationRelativeTo(surface.getParent());
      rDialog.setVisible(true);              // Note: this call invokes dialog
      if (rDialog.wasPressed()) {
        surface.pushToUndoStack();
        double val = (Double) rParms[0].value;
        surface.roundSelected(val);
      }
    });
    editMenu.add(roundCorners);
    //
    // Add "Generate CNC Path from Selected" Menu Item
    //
    JMenuItem cncSelected = new JMenuItem("Generate CNC Path from Selected");
    cncSelected.setEnabled(false);
    cncSelected.addActionListener((ev) -> {
      // Display dialog to get tool radius and inset flag
      ParameterDialog.ParmItem[] rParms = {new ParameterDialog.ParmItem("radius|in{radius of tool}", 0d),
          new ParameterDialog.ParmItem("inset{If checked, toolpath routes interior" +
              " of cadShape, else outside}", true)};
      ParameterDialog rDialog = (new ParameterDialog("Edit Parameters", rParms, new String[] {"OK", "Cancel"}, displayUnits));
      rDialog.setLocationRelativeTo(surface.getParent());
      rDialog.setVisible(true);              // Note: this call invokes dialog
      if (rDialog.wasPressed()) {
        surface.pushToUndoStack();
        double radius = (Double) rParms[0].value;
        boolean inset = (Boolean) rParms[1].value;
        CADShape shape = surface.getSelected();
        CNCPath path = new CNCPath(shape, radius, inset);
        surface.addShape(path);
      }
    });
    editMenu.add(cncSelected);
    //
    // Add SelectListener to enable/disable menus, as needed
    //
    surface.addSelectListener((shape, selected) -> {
      boolean canSelect = !(shape instanceof CNCPath) & selected;
      removeSelected.setEnabled(selected);
      cncSelected.setEnabled(selected);
      dupSelected.setEnabled(canSelect);
      editSelected.setEnabled(selected);
      moveSelected.setEnabled(canSelect);
      roundCorners.setEnabled(canSelect);
      saveSelected.setEnabled(canSelect);
      boolean hasGroup = surface.getSelected() != null && surface.getSelected().getGroup() != null;
      unGroupSelected.setEnabled(hasGroup);
      addSelected.setEnabled(hasGroup);
      alignMenu.setEnabled(hasGroup);
      subtractSelected.setEnabled(hasGroup);
      rotateSelected.setEnabled(hasGroup);
    });
    surface.addDragSelectListener((selected) -> {
      moveSelected.setEnabled(selected);
      removeSelected.setEnabled(selected);
      combineDragSelected.setEnabled(selected);
      groupDragSelected.setEnabled(selected);
      unGroupSelected.setEnabled(selected);
      dupSelected.setEnabled(selected);
    });
    menuBar.add(editMenu);
    /*
     *  Add "Import" Menu
     */
    JMenu importMenu = new JMenu("Import");
    //
    // Add "Import LaserCut File" Item to File menu
    //
    JMenuItem importObj = new MyMenuItem("Import LaserCut File", KeyEvent.VK_I, cmdMask);
    importObj.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select a LaserCut File");
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          FileInputStream fileIn = new FileInputStream(tFile);
          ObjectInputStream in = new FixInputStream(fileIn);
          ArrayList<CADShape> design = (ArrayList<CADShape>) in.readObject();
          in.close();
          fileIn.close();
          surface.placeShapes(design);
          prefs.put("default.dir", tFile.getAbsolutePath());
          setTitle("LaserCut - (" + tFile + ")");
        } catch (Exception ex) {
          showErrorDialog("Unable to import LaserCut file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(importObj);
    //
    // Add "Import SVG File" menu item
    //
    importMenu.add(new FileChooserMenu("SVG", "svg", false) {
      void processFile (File sFile) throws Exception {
        SVGParser parser = new SVGParser();
        parser.setPxDpi(pxDpi);
        //parser.enableDebug(true);
        Shape[] shapes = parser.parseSVG(sFile);
        shapes = SVGParser.removeOffset(shapes);
        Shape shape = SVGParser.combinePaths(shapes);
        Rectangle2D bounds = BetterBoundingBox.getBounds(shape);
        double offX = bounds.getWidth() / 2;
        double offY = bounds.getHeight() / 2;
        AffineTransform at = AffineTransform.getTranslateInstance(-offX, -offY);
        shape = at.createTransformedShape(shape);
        CADShape shp = new CADScaledShape(shape, offX, offY, 0, false, 100.0);
        if (shp.placeParameterDialog(surface, displayUnits)) {
          surface.placeShape(shp);
        } else {
          surface.setInfoText("Import cancelled");
        }
      }
    });
    //
    // Add "Import DXF File" menu item
    //
    importMenu.add(new DxfFileChooserMenu("DXF", "dxf", false, displayUnits) {
      void processFile (File sFile) throws Exception {
        String importUnits = getSelectedUnits();
        DXFReader dxf = new DXFReader(importUnits);
        if (isOptionSelected("TEXT")) {
          dxf.enableText(true);
        }
        if (isOptionSelected("MTEXT")) {
          dxf.enableMText(true);
        }
        if (isOptionSelected("DIMENSION")) {
          dxf.enableDimen(true);
        }
        Shape[] shapes = SVGParser.removeOffset(dxf.parseFile(sFile, 12, 0));
        CADShapeGroup group = new CADShapeGroup();
        List<CADShape> gShapes = new ArrayList<>();
        for (Shape shape : shapes) {
          Rectangle2D sBnds = shape.getBounds2D();
          double xLoc = sBnds.getX();
          double yLoc = sBnds.getY();
          double wid = sBnds.getWidth();
          double hyt = sBnds.getHeight();
          AffineTransform at = AffineTransform.getTranslateInstance(-xLoc - (wid / 2), -yLoc - (hyt / 2));
          shape = at.createTransformedShape(shape);
          CADShape cShape = new CADShape(shape, xLoc, yLoc, 0, false);
          group.addToGroup(cShape);
          gShapes.add(cShape);
        }
        surface.placeShapes(gShapes);
      }
    });
    //
    // Add "Import Gerber Zip" menu item
    //
    importMenu.add(gerberZip = new FileChooserMenu("Gerber Zip", "zip", false) {
      void processFile (File sFile) throws Exception {
        GerberZip gerber = new GerberZip(sFile);
        List<GerberZip.ExcellonHole> holes = gerber.parseExcellon();
        List<List<Point2D.Double>> outlines = gerber.parseOutlines();
        Rectangle2D.Double bounds = GerberZip.getBounds(outlines);
        // System.out.println("PCB Size: " + bounds.getWidth() + " inches, " + bounds.getHeight() + " inches");
        double yBase = bounds.getHeight();
        List<CADShape> gShapes = new ArrayList<>();
        for (GerberZip.ExcellonHole hole : holes) {
          gShapes.add(new CADOval(hole.xLoc, yBase - hole.yLoc, hole.diameter, hole.diameter, 0, true));
        }
        // Build shapes for all outlines
        for (List<Point2D.Double> points : outlines) {
          Path2D.Double path = new Path2D.Double();
          boolean first = true;
          for (Point2D.Double point : points) {
            if (first) {
              path.moveTo(point.getX() - bounds.width / 2, yBase - point.getY() - bounds.height / 2);
              first = false;
            } else {
              path.lineTo(point.getX() - bounds.width / 2, yBase - point.getY() - bounds.height / 2);
            }
          }
          CADShape outline = new CADShape(path, 0, 0, 0, false);
          gShapes.add(outline);
        }
        CADShapeGroup group = new CADShapeGroup();
        for (CADShape cShape : gShapes) {
          group.addToGroup(cShape);
        }
        surface.placeShapes(gShapes);
      }
    });
    /*
     *  Add "Import Notes to MusicBox Strip" Menu
     */
    importMenu.add(new FileChooserMenu("Notes to MusicBox Strip Text", "txt", false) {
      void processFile (File sFile) throws Exception {
        Scanner lines = new Scanner(Files.newInputStream(sFile.toPath()));
        List<String[]> cols = new ArrayList<>();
        int col = 0;
        while (lines.hasNextLine()) {
          Scanner line = new Scanner(lines.nextLine().trim());
          int time = line.nextInt();
          List<String> notes = new ArrayList<>();
          while (line.hasNext()) {
            String item = line.next();
            item = item.endsWith(",") ? item.substring(0, item.length() - 1) : item;
            notes.add(item);
          }
          while (time-- > 0) {
            cols.add(notes.toArray(new String[0]));
            notes = new ArrayList<>();
          }
        }
        String[][] song = cols.toArray(new String[cols.size()][0]);
        CADMusicStrip mStrip = new CADMusicStrip();
        mStrip.setNotes(song);
        if (mStrip.placeParameterDialog(surface, displayUnits)) {
          surface.placeShape(mStrip);
        } else {
          surface.setInfoText("Import cancelled");
        }
      }
    });
    menuBar.add(importMenu);
    /*
     *  Add "Export" Menu
     */
    JMenu exportMenu = new JMenu("Export");
    //
    // Add "Export to PDF File" Menu Item
    //
    exportMenu.add(new FileChooserMenu("PDF", "pdf", true) {
      void processFile (File sFile) throws Exception {
        PDFTools.writePDF(surface.getDesign(), surface.getWorkSize(), sFile);
      }
    });
    //
    // Add "Export to SVG File"" Menu Item
    //
    exportMenu.add(new FileChooserMenu("SVG", "svg", true) {
      void processFile (File sFile) throws Exception {
        List<Shape> sList = new ArrayList<>();
        for (CADShape item : surface.getDesign()) {
          if (item instanceof CADReference)
            continue;
          Shape shape = item.getWorkspaceTranslatedShape();
          sList.add(shape);
        }
        Shape[] shapes = sList.toArray(new Shape[0]);
        String xml = SVGParser.shapesToSVG(shapes, surface.getWorkSize(), pxDpi);
        FileOutputStream out = new FileOutputStream(sFile);
        out.write(xml.getBytes(StandardCharsets.UTF_8));
        out.close();
      }
    });
    //
    // Add "Export to DXF File"" Menu Item
    // Based on: https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/
    //  by Jonathan Sevy (released under the MIT License; https://choosealicense.com/licenses/mit/)
    //
    exportMenu.add(new DxfFileChooserMenu("DXF", "dxf", true, displayUnits) {
      void processFile (File sFile) throws Exception {
        String units = getSelectedUnits();
        AffineTransform atExport = null;
        int dxfUnitCode = 1;
        if ("cm".equals(units)) {
          atExport = AffineTransform.getScaleInstance(2.54, 2.54);
          dxfUnitCode = 5;
        } else if ("mm".equals(units)) {
          atExport = AffineTransform.getScaleInstance(25.4, 25.4);
          dxfUnitCode = 4;
        }
        List<Shape> sList = new ArrayList<>();
        for (CADShape item : surface.getDesign()) {
          if (item instanceof CADReference)
            continue;
          Shape shape = item.getWorkspaceTranslatedShape();
          if (atExport != null) {
            shape = atExport.createTransformedShape(shape);
          }
          sList.add(shape);
        }
        DXFDocument dxfDocument = new DXFDocument("Exported from LaserCut " + VERSION);
        dxfDocument.setUnits(dxfUnitCode);
        DXFGraphics dxfGraphics = dxfDocument.getGraphics();
        for (Shape shape : sList) {
          dxfGraphics.draw(shape);
        }
        String dxfText = dxfDocument.toDXFString();
        FileWriter fileWriter = new FileWriter(sFile);
        fileWriter.write(dxfText);
        fileWriter.flush();
        fileWriter.close();
      }
    });
    //
    // Add "Export to EPS File"" Menu Item
    //
    exportMenu.add(new FileChooserMenu("EPS", "eps", true) {
      void processFile (File sFile) throws Exception {
        List<Shape> sList = new ArrayList<>();
        AffineTransform scale = AffineTransform.getScaleInstance(72.0, 72.0);
        Dimension workArea = surface.getWorkSize();
        EPSWriter eps = new EPSWriter("LaserCut: " + sFile.getName());
        for (CADShape item : surface.getDesign()) {
          if (item instanceof CADReference)
            continue;
          Shape shape = item.getWorkspaceTranslatedShape();
          shape = scale.createTransformedShape(shape);
          eps.draw(shape);
        }
        eps.writeEPS(sFile);
      }
    });
    menuBar.add(exportMenu);

    /*
     *  Add Menu for selected output device
     */
    deviceMenuSlot = menuBar.getMenuCount();
    JMenu deviceMenu = getOutputDeviceMenu();
    if (deviceMenu != null) {
      menuBar.add(deviceMenu);
    }
    /*
     *  Add "Units" Menu
     */
    JMenu unitsMenu = new JMenu("Units");
    unitsMenu.setToolTipText("Select units to be used in controls");
    ButtonGroup unitGroup = new ButtonGroup();
    String[] unitSet = {"Inches:in", "Centimeters:cm", "Millimeters:mm"};
    displayUnits = prefs.get("displayUnits", "in");
    surface.setUnits(displayUnits);
    for (String unit : unitSet) {
      String[] parts = unit.split(":");
      boolean select = parts[1].equals(displayUnits);
      JMenuItem uItem = new JRadioButtonMenuItem(parts[0], select);
      unitGroup.add(uItem);
      unitsMenu.add(uItem);
      uItem.addActionListener(ev -> {
        prefs.put("displayUnits", displayUnits = parts[1]);
        surface.setUnits(displayUnits);
      });
    }
    menuBar.add(unitsMenu);
    // Track window move events and save in prefs
    addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
      }
    });
    setLocation(prefs.getInt("window.x", 10), prefs.getInt("window.y", 10));
    pack();
    setVisible(true);
    String reopen = prefs.get("lastFile", null);
    if ("reopen".equals(onStartup) && reopen != null) {
      try {
        FileInputStream fileIn = new FileInputStream(new File(reopen));
        ObjectInputStream in = new FixInputStream(fileIn);
        ArrayList<CADShape> design = (ArrayList<CADShape>) in.readObject();
        SurfaceSettings settings = null;
        try {
          // Read DrawSurface setting and JScrollPane position, if available
          settings = (SurfaceSettings) in.readObject();
          scrollPane.getViewport().setViewPosition(settings.viewPoint);
        } catch (Exception ex) {
          // Ignore (catching EOFException is the only way to tell if SurfaceSettings object was serialized)
          scrollPane.getViewport().setViewPosition(new Point(0, 0));
        }
        in.close();
        fileIn.close();
        surface.setDesign(design, settings);
      } catch (Exception ex) {
        //ex.printStackTrace();
        System.out.println("Unable to reopen: " + reopen);
      }
    } else if ("demo".equals(onStartup) && outputDevice instanceof ZingLaser) {
      /*
       * * * * * * * * * * * * * * * * * * *
       * Add some test shapes to DrawSurface
       * * * * * * * * * * * * * * * * * * *
       */
      // Create + cadShape via additive and subtractive geometric operations
      RoundRectangle2D.Double c1 = new RoundRectangle2D.Double(-.80, -.30, 1.60, .60, .40, .40);
      RoundRectangle2D.Double c2 = new RoundRectangle2D.Double(-.30, -.80, .60, 1.60, .40, .40);
      Area a1 = new Area(c1);
      a1.add(new Area(c2));
      Point2D.Double[] quadrant = {new Point2D.Double(-1, -1), new Point2D.Double(1, -1),
                                   new Point2D.Double(-1, 1), new Point2D.Double(1, 1)};
      double radius = .2;
      double sqWid = radius + .1;
      for (Point2D.Double qq : quadrant) {
        Point2D.Double RectPnt = new Point2D.Double(qq.x * .4, qq.y * .4);
        Point2D.Double RndPnt = new Point2D.Double(qq.x * .5, qq.y * .5);
        Rectangle2D.Double t1 = new Rectangle2D.Double(RectPnt.x - sqWid / 2, RectPnt.y - sqWid / 2, sqWid, sqWid);
        a1.add(new Area(t1));
        RoundRectangle2D.Double t2 = new RoundRectangle2D.Double(RndPnt.x - radius, RndPnt.y - radius, .40, .40, .20, .20);
        a1.subtract(new Area(t2));
      }
      surface.addShape(new CADShape(a1, 5.75, 2.5, 0, true));
      // Add two concentric circles in center
      CADShape circle1 = new CADOval(5.75, 2.5, 1.20, 1.20, 0, true);
      CADShape circle2 = new CADOval(5.75, 2.5, 0.60, 0.60, 0, true);
      CADShapeGroup group = new CADShapeGroup();
      group.addToGroup(circle1);
      group.addToGroup(circle2);
      surface.addShape(circle1);
      surface.addShape(circle2);
      // Add RoundedRectange
      surface.addShape(new CADRectangle(.25, .25, 4, 4, .25, 0, false));
      // Add 72 Point, 1 inch tall Text
      surface.addShape(new CADText(4.625, .25, "Belle", "Helvetica", "bold", 72, 0, 0, false));
      // Add Test Gear
      surface.addShape(new CADGear(2.25, 2.25, .1, 30, 10, 20, .25, 0, mmToInches(3)));
    }
    savedCrc = surface.getDesignChecksum();   // Allow quit if unchanged
  }

  void updateWorkspace () {
    if (outputDevice != null) {
      surface.setZoomFactor(outputDevice.getZoomFactor());
      surface.setSurfaceSize(outputDevice.getWorkspaceSize());
    }
  }

  private JMenu getOutputDeviceMenu () {
    String device = "";
    JMenu menu = null;
    try {
      switch (prefs.getInt("outputDevice", 0)) {
        case 1:
          outputDevice = new ZingLaser(this);
          break;
        case 2:
          outputDevice = new MiniLaser(this);
          break;
        case 3:
          outputDevice = new MicroLaser(this);
          break;
        case 4:
          outputDevice = new MiniCNC(this);
          break;
        case 5:
          outputDevice = new Silhouette(this);
          break;
        case 6:
          outputDevice = new MiniCutter(this);
          break;
        default:
          outputDevice = null;
          break;
      }
      if (outputDevice != null) {
        device = outputDevice.getName();
        surface.setZoomFactor(outputDevice.getZoomFactor());
        surface.setSurfaceSize(outputDevice.getWorkspaceSize());
        menu = outputDevice.getDeviceMenu();
        menu.setToolTipText("Use 'Preferences' menu to change output device");
      }
    } catch (Throwable ex) {
      ex.printStackTrace();
      if (showWarningDialog("Unable to initialize the " + device + " so it will be disabled.\n" +
          "Note: you can use the Preferences dialog box to renable output to the " + device + ".\n" +
          "Do you want to view the error message ?")) {
        // Save stack trace for "Error" menu
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter pout = new PrintWriter(bout);
        ex.printStackTrace(pout);
        try {
          pout.close();
          bout.close();
        } catch (Exception ex2) {
          ex2.printStackTrace();
        }
        String errorMsg = device + " Error:\n" + bout;
        ex.printStackTrace();
        showScrollingDialog(errorMsg);
      }
      // Disable output device for future restarts
      prefs.putInt("outputDevice", 0);
      return null;
    }
    return menu;
  }

  private static java.lang.reflect.Field getField(Class mClass, String fieldName) throws NoSuchFieldException {
    try {
      return mClass.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class superClass = mClass.getSuperclass();
      if (superClass == null) {
        throw e;
      } else {
        return getField(superClass, fieldName);
      }
    }
  }

  public static void setFieldValue(Object object, String fieldName, Object valueTobeSet) throws NoSuchFieldException, IllegalAccessException {
    java.lang.reflect.Field field = getField(object.getClass(), fieldName);
    field.setAccessible(true);
    field.set(object, valueTobeSet);
  }

  /**
   * This class attempts to fix loading of serialized CADScaledShape objects that did not have
   * the proper serialVersionUID value assigned, as well as renameing classes refactored inner
   * classes changes to default classes
   */
  public static class FixInputStream extends ObjectInputStream {
    public FixInputStream (InputStream in) throws IOException {
      super(in);
    }

    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
      ObjectStreamClass resultClassDescriptor = super.readClassDescriptor(); // initially streams descriptor
      Class localClass; // the class in the local JVM that this descriptor represents.
      try {
        String descName = resultClassDescriptor.getName();
        String realName = descName.substring(9);
        if (descName.startsWith("LaserCut$")) {
          setFieldValue(resultClassDescriptor, "name", realName);
        }
        localClass = Class.forName(descName);
      } catch (Exception e) {
        return resultClassDescriptor;
      }
      ObjectStreamClass localClassDescriptor = ObjectStreamClass.lookup(localClass);
      if (localClassDescriptor != null) {
        final long localSUID = localClassDescriptor.getSerialVersionUID();
        final long streamSUID = resultClassDescriptor.getSerialVersionUID();
        String className = resultClassDescriptor.getName();
        if (className.startsWith("LaserCut$")) {
          int dum = 0;
        }
        if (streamSUID != localSUID && "LaserCut$CADScaledShape".equals(className)) {
          // If mismatch, use local class descriptor for deserialization
          resultClassDescriptor = localClassDescriptor;
        }
      }
      return resultClassDescriptor;
    }
  }

  public boolean showWarningDialog (String msg) {
    return showConfirmDialog(this, msg, "Warning", YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION;
  }

  public void showErrorDialog (String msg) {
    showMessageDialog(this, msg, "Error", PLAIN_MESSAGE);
  }

  public void showInfoDialog (String msg) {
    showMessageDialog(this, msg);
  }

  public void showScrollingDialog (String msg) {
    JTextArea textArea = new JTextArea(30, 122);
    textArea.setMargin(new Insets(2, 4, 4, 2));
    textArea.setFont(new Font("Courier", Font.PLAIN, 12));
    textArea.setTabSize(4);
    JScrollPane scrollPane = new JScrollPane(textArea);
    textArea.setText(msg);
    textArea.setEditable(false);
    textArea.setCaretPosition(0);
    showMessageDialog(this, scrollPane);
  }

  static double mmToInches (double mm) {
    return mm / 25.4;
  }

  static double inchesToMM (double inches) {
    return inches * 25.4;
  }

  static double cmToInches (double cm) {
    return cm / 2.54;
  }

  static double inchesToCm (double inches) {
    return inches * 2.54;
  }

  static double mmToCm (double mm) {
    return mm / 10;
  }

  public static String getResourceFile (String file) {
    try {
      InputStream fis = LaserCut.class.getResourceAsStream(file);
      if (fis != null) {
        byte[] data = new byte[fis.available()];
        fis.read(data);
        fis.close();
        return new String(data);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "";
  }

  public Properties getProperties (String content) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(content));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return props;
  }

  /*
   * CADShape interfaces
   */

  interface Resizable {   // Interface for CADShape subclasses that support resizing
    void resize(double dx, double dy);
  }

  interface Rotatable {}  // Marker inteface for CADShape subclasses that support rotation

  interface ChangeListener {
    void shapeChanged (CADShape cadShape);
  }

  interface Updatable {
    boolean updateInternalState (Point2D.Double point);
  }

  interface StateMessages {
    String getStateMsg ();
  }

  static class VectorFont {
    private static final Map<String,VectorFont> vFonts = new HashMap<>();
    int[][][] font;
    final int       height;

    VectorFont (String name) {
      height = 32;
      switch (name) {
      case "Vector 1":
        font = getFontData("hershey1.txt");
        break;
      case "Vector 2":
        font = getFontData("hershey2.txt");
        break;
      case "Vector 3":
        font = getFontData("hershey3.txt");
        break;
      }
    }

    static VectorFont getFont (String name) {
      VectorFont font = vFonts.get(name);
      if (font == null) {
        vFonts.put(name, font = new VectorFont(name));
      }
      return font;
    }

    private int[][][] getFontData (String name) {
      String data = getResourceFile("fonts/" + name);
      StringTokenizer tok = new StringTokenizer(data, "\n");
      int[][][] font = new int[128][][];
      while (tok.hasMoreElements()) {
        String line = tok.nextToken();
        if (line.charAt(3) == ':') {
          char cc = line.charAt(1);
          line = line.substring(4);
          String[] vecs = line.split("\\|");
          int[][] vec = new int[vecs.length][];
          font[cc - 32] = vec;
          for (int ii = 0; ii < vecs.length; ii++) {
            String[] coords = vecs[ii].split(",");
            int[] tmp = new int[coords.length];
            for (int jj = 0; jj < tmp.length; jj++) {
              tmp[jj] = Integer.parseInt(coords[jj]);
              vec[ii] = tmp;
            }
          }
        }
      }
      return font;
    }
  }

  /*
   * * * * * * * Implemented Interfaces * * * * * * * *
   */

  interface ShapeSelectListener {
    void shapeSelected (CADShape shape, boolean selected);
  }

  interface ShapeDragSelectListener {
    void shapeDragSelect (boolean selected);
  }

  interface ActionUndoListener {
    void undoEnable (boolean enable);
  }

  interface ActionRedoListener {
    void redoEnable (boolean enable);
  }

  interface ZoomListener {
    void setZoom (int zIndex);
  }

  public static void main (String[] s) {
    SwingUtilities.invokeLater(LaserCut::new);
  }
}