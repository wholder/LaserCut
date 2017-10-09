import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.drivers.EpilogZing;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

import jssc.SerialNativeInterface;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.desktop.*;
import java.awt.event.*;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;
import java.util.zip.CRC32;

  /*
  Links to potentially useful stuff

  See: https://docs.oracle.com/javase/7/docs/api/java/awt/geom/package-summary.html
  Gears: http://lcamtuf.coredump.cx/gcnc/ch6/
  PDF export: http://trac.erichseifert.de/vectorgraphics2d/wiki/Usage
  Export to EPS, SVG, PDF: https://github.com/eseifert/vectorgraphics2d
  Zoom & Pan: https://community.oracle.com/thread/1263955
  https://developer.apple.com/library/content/documentation/Java/Conceptual/Java14Development/07-NativePlatformIntegration/NativePlatformIntegration.html

  Note: unable to use CMD-A, CMD-C, CMD-H, CMD-Q, CMD-X as shortcut keys
  */

  /*
      CMD-A - Add Grouped Shapes to Selected
      CMD-B - Round Corners of Selected Shape
      CMD-C
      CMD-D - Duplicate Selected
      CMD-E - Edit Selected
      CMD-F
      CMD-G
      CMD-H
      CMD-I
      CMD-J
      CMD-K
      CMD-L - Align Grouped Shapes to Selected Shape
      CMD-M - Move Selected
      CMD-N - New
      CMD-O - Open
      CMD-P
      CMD-Q - Quit
      CMD-R - Rotate Group Around Selected
      CMD-S - Save
      CMD-T - Take Away Grouped Shapes from Selected
      CMD-U - Ungroup Selected Shapes
      CMD-V
      CMD-W
      CMD-X - Delete Selected
      CMD-Y
      CMD-Z - Undo (+ Shift is Redo)
   */

public class LaserCut extends JFrame {
  static final String           VERSION = "1.0 beta";
  static final double           SCREEN_PPI = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
  static final double           ZING_PPI = 500;
  static final DecimalFormat    df = new DecimalFormat("#0.0###");
  private static Dimension      zingFullSize = new Dimension((int) (16 * SCREEN_PPI), (int) (12 * SCREEN_PPI));
  private static Dimension      zing12x12Size = new Dimension((int) (12 * SCREEN_PPI), (int) (12 * SCREEN_PPI));
  private static Dimension      miniSize = new Dimension((int) (7 * SCREEN_PPI), (int) (8 * SCREEN_PPI));
  private static int            cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
  private transient Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
  private JSSCPort              jPort;
  private DrawSurface           surface;
  private JScrollPane           scrollPane;
  private JTextField            itemInfo = new JTextField();
  private JMenuItem             gerberZip;
  private String                zingIpAddress = prefs.get("zing.ip", "10.0.1.201");
  private int                   zingCutPower = prefs.getInt("zing.power", 85);
  private int                   zingCutSpeed = prefs.getInt("zing.speed", 55);
  private int                   zingCutFreq = prefs.getInt("zing.freq", 500);
  private int                   zingEngravePower = prefs.getInt("zing.epower", 5);
  private int                   zingEngraveSpeed = prefs.getInt("zing.espeed", 55);
  private int                   zingEngraveFreq = prefs.getInt("zing.efreq", 500);
  private int                   miniPower = prefs.getInt("mini.power", 255);
  private int                   miniSpeed = prefs.getInt("mini.speed", 10);
  private long                  savedCrc;
  private boolean               useMouseWheel = prefs.getBoolean("useMouseWheel", false);
  private boolean               miniDynamicLaser = prefs.getBoolean("mini.dynamicLaser", true);
  private static final boolean  enableMiniLazer = true;
  private static Map<String,String> grblSettings = new LinkedHashMap<>();

  static {
    // Settings map for GRBL .9, or later
    grblSettings.put("$0", "Step pulse, usec");
    grblSettings.put("$1", "Step idle delay, msec");
    grblSettings.put("$2", "Step port invert, mask");
    grblSettings.put("$3", "Direction port invert, mask");
    grblSettings.put("$4", "Step enable invert, boolean");
    grblSettings.put("$5", "Limit pins invert, boolean");
    grblSettings.put("$6", "Probe pin invert, boolean");
    grblSettings.put("$10", "Status report, mask");
    grblSettings.put("$11", "Junction deviation, mm");
    grblSettings.put("$12", "Arc tolerance, mm");
    grblSettings.put("$13", "Report inches, boolean");
    grblSettings.put("$20", "Soft limits, boolean");
    grblSettings.put("$21", "Hard limits, boolean");
    grblSettings.put("$22", "Homing cycle, boolean");
    grblSettings.put("$23", "Homing dir invert, mask");
    grblSettings.put("$24", "Homing feed, mm/min");
    grblSettings.put("$25", "Homing seek, mm/min");
    grblSettings.put("$26", "Homing debounce, msec");
    grblSettings.put("$27", "Homing pull-off, mm");
    grblSettings.put("$30", "Max spindle speed, RPM");
    grblSettings.put("$31", "Min spindle speed, RPM");
    grblSettings.put("$32", "Laser mode, boolean");
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
    grblSettings.put("$132", "Z Max travel, mm");
  }

  private boolean quitHandler () {
    if (savedCrc == surface.getDesignChecksum() || showWarningDialog("You have unsaved changes!\nDo you really want to quit?")) {
      if (jPort != null) {
        jPort.close();
      }
      return true;
    }
    return false;
  }

  private void showAboutBox () {
    JOptionPane.showMessageDialog(this,
        "By: Wayne Holder\n" +
            "  Java Runtime  " + Runtime.version() + "\n" +
            "  LibLaserCut " + com.t_oster.liblasercut.LibInfo.getVersion() + "\n" +
            "  Java Simple Serial Connector " + SerialNativeInterface.getLibraryVersion() + "\n" +
            "  JSSC Native Code DLL Version " + SerialNativeInterface.getNativeLibraryVersion() + "\n" +
            "  Apache PDFBox " + org.apache.pdfbox.util.Version.getVersion(),
        "LaserCut " + VERSION,
        JOptionPane.INFORMATION_MESSAGE,
        new ImageIcon(getClass().getResource("/images/laser_wip_black.png")));
  }

  private void showPreferencesBox () {
    Map<String,ParameterDialog.ParmItem> items = new LinkedHashMap<>();
    items.put("useMouseWheel", new ParameterDialog.ParmItem("Enable Mouse Wheel Scrolling", prefs.getBoolean("useMouseWheel", false)));
    items.put("enableGerber", new ParameterDialog.ParmItem("Enable Gerber ZIP Import", prefs.getBoolean("gerber.import", false)));
    ParameterDialog.ParmItem[] parmSet = items.values().toArray(new ParameterDialog.ParmItem[items.size()]);
    ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}));
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);              // Note: this call invokes dialog
    if (dialog.doAction() ) {
      for (String name : items.keySet()) {
        ParameterDialog.ParmItem parm = items.get(name);
        if ("useMouseWheel".equals(name)) {
          prefs.putBoolean("useMouseWheel", useMouseWheel = (Boolean) parm.value);
          configureMouseWheel();
        } else if ("enableGerber".equals(name)) {
          boolean enabled = (Boolean) parm.value;
          prefs.putBoolean("gerber.import", enabled);
          gerberZip.setVisible(enabled);
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

  private LaserCut () {
    setTitle("LaserCut");
    surface = new DrawSurface();
    scrollPane = new JScrollPane(surface);
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
    Dimension sSize = surface.getSize();
    if (!sSize.equals(zingFullSize)  &&  !sSize.equals(miniSize)) {
      surface.setSurfaceSize(zingFullSize);
    }
    requestFocusInWindow();
    if (Taskbar.isTaskbarSupported()) {
      Taskbar taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        taskbar.setIconImage(new ImageIcon(getClass().getResource("/images/laser_wip_black.png")).getImage());
      }
    }
    boolean hasAboutHandler = false;
    boolean hasPreferencesHandler = false;
    boolean hasQuitHandler = false;
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      desktop.disableSuddenTermination();
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
    }
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing (WindowEvent e) {
        if (quitHandler()) {
          System.exit(0);
        }
      }
    });
    // Add Menu Bar to Window
    JMenuBar menuBar = new JMenuBar();
    setJMenuBar(menuBar);
    /*
     *  Add "File" Menu
     */
    JMenu fileMenu = new JMenu("File");
    if (!hasAboutHandler) {
      // Add "About" Item to File Menu
      JMenuItem aboutBox = new JMenuItem("About " + getClass().getSimpleName());
      aboutBox.addActionListener(ev -> {
        showAboutBox();
      });
      fileMenu.add(aboutBox);
    }
    if (!hasPreferencesHandler) {
      // Add "Preferences" Item to File Menu
      JMenuItem preferencesBox = new JMenuItem("Preferences");
      preferencesBox.addActionListener(ev -> {
        showPreferencesBox();
      });
      fileMenu.add(preferencesBox);
    }
    if (!hasAboutHandler || !hasPreferencesHandler) {
      fileMenu.addSeparator();
    }
    // Add "New" Item to File Menu
    JMenuItem newObj = new JMenuItem("New");
    newObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, cmdMask));
    newObj.addActionListener(ev -> {
      if (surface.hasData()) {
        if (!showWarningDialog("Discard current design?"))
          return;
        surface.clear();
        savedCrc = surface.getDesignChecksum();
        setTitle("LaserCut");
      }
    });
    fileMenu.add(newObj);
    // Add "Open" Item to File menu
    JMenuItem loadObj = new JMenuItem("Open");
    loadObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, cmdMask));
    loadObj.addActionListener(ev -> {
      if (surface.hasData()) {
        if (!showWarningDialog("Discard current design?"))
          return;
      }
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        surface.pushToUndoStack();
        try {
          File tFile = fileChooser.getSelectedFile();
          surface.setDesign(loadDesign(tFile));
          savedCrc = surface.getDesignChecksum();
          prefs.put("default.dir", tFile.getAbsolutePath());
          setTitle("LaserCut - (" + tFile + ")");
        } catch (Exception ex) {
          showErrorDialog("Unable to load file");
          ex.printStackTrace(System.out);
        }
      }
    });
    fileMenu.add(loadObj);
    // Add "Save As" Item to File menu
    JMenuItem saveAs = new JMenuItem("Save As");
    saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, cmdMask));
    saveAs.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fName = sFile.getName();
        if (!fName.contains(".")) {
          sFile = new File(fName + ".lzr");
        }
        try {
          if (sFile.exists()) {
            if (showWarningDialog("Overwrite Existing file?")) {
              saveDesign(sFile, surface.getDesign());
            }
          } else {
            saveDesign(sFile, surface.getDesign());
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
    // Add "Save As" Item to File menu
    JMenuItem saveSelected = new JMenuItem("Save Selected");
    saveSelected.setEnabled(false);
    saveSelected.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setCurrentDirectory(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fName = sFile.getName();
        if (!fName.contains(".")) {
          sFile = new File(fName + ".lzr");
        }
        try {
          if (sFile.exists()) {
            if (showWarningDialog("Overwrite Existing file?")) {
              saveDesign(sFile, surface.getSelectedAsDesign());
            }
          } else {
            saveDesign(sFile, surface.getSelectedAsDesign());
          }
          savedCrc = surface.getDesignChecksum();
          setTitle("LaserCut - (" + sFile + ")");
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
      JMenuItem quitObj = new JMenuItem("Quit");
      quitObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, cmdMask));
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
    // Add options for other Shapes
    String[] sItems = new String[] {"Reference Point/LaserCut$CADReference", "Rectangle/LaserCut$CADRectangle", "Polygon/LaserCut$CADPolygon",
                                    "Oval/LaserCut$CADOval", "Gear/LaserCut$CADGear", "Text/LaserCut$CADText", "NEMA Stepper/LaserCut$CADNemaMotor",
                                    "Bobbin/LaserCut$CADBobbin", "Raster Image (beta)/LaserCut$CADRasterImage",
                                    "Reference Image (beta)/LaserCut$CADReferenceImage", "Spline Curve/CADShapeSpline"};
    for (String sItem : sItems) {
      String[] parts = sItem.split("/");
      JMenuItem mItem = new JMenuItem(parts[0]);
      mItem.addActionListener(ev -> {
        try {
          Class ref = Class.forName(parts[1]);
          CADShape shp = (CADShape) ref.getDeclaredConstructor().newInstance();
          if (shp instanceof CADReference || shp instanceof CADShapeSpline) {
            surface.placeShape(shp);
          } else if (shp instanceof CADRasterImage) {
            // Prompt for Image file
            JFileChooser fileChooser = new JFileChooser();
            FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Image files (jpg,jpeg,png,gif)", "jpg", "jpeg", "png", "gif");
            fileChooser.addChoosableFileFilter(nameFilter);
            fileChooser.setFileFilter(nameFilter);
            fileChooser.setSelectedFile(new File(prefs.get("image.dir", "/")));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
              try {
                File iFile = fileChooser.getSelectedFile();
                prefs.put("image.dir", iFile.getAbsolutePath());
                ((CADRasterImage) shp).loadImage(iFile);
                surface.placeShape(shp);
              } catch (Exception ex) {
                showErrorDialog("Unable to load file");
                ex.printStackTrace(System.out);
              }
            }
          } else if (shp.placeParameterDialog(surface)) {
            surface.placeShape(shp);
          }
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
    ButtonGroup gridGroup = new ButtonGroup();
    String[] gridSizes = new String[] {"Off", "|", "0.0625/16 in", "0.1/10 in", "0.125/8 in", "0.25/4 in", "0.5/2 in", "|",
                                                   "1/10 mm", "2/10 mm", "2.5/5 mm", "5/10 mm", "10/0 mm"};
    for (String gridItem : gridSizes) {
      if (gridItem.equals("|")) {
        gridMenu.addSeparator();
      } else {
        double gridSize;
        int major;
        String units = gridItem.substring(gridItem.length() - 2);
        String[] tmp = gridItem.substring(0, gridItem.length() - 3).split("/");
        if ("in".equals(units)) {
          gridSize = Double.parseDouble(tmp[0]);
          major = Integer.parseInt(tmp[1]);
        } else if ("mm".equals(units)) {
          gridSize = mmToInches(Double.parseDouble(tmp[0]));
          major = Integer.parseInt(tmp[1]);
        } else {
          gridSize = 0;
          major = 0;
        }
        JMenuItem mItem = new JRadioButtonMenuItem("Off".equals(gridItem) ? "Off" : tmp[0] + " " + units);
        mItem.setSelected(gridSize == surface.getGridSize());
        gridGroup.add(mItem);
        gridMenu.add(mItem);
        mItem.addActionListener(ev -> {
          try {
            surface.setGridSize(gridSize, major);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
      }
    }
    menuBar.add(gridMenu);
    // Add "Zoom" Menu
    JMenu zoomMenu = new JMenu("Zoom");
    ButtonGroup zoomGroup = new ButtonGroup();
    double[] zoomFactors = {1, 2, 3, 4};
    for (double zoom : zoomFactors) {
      JMenuItem zItem = new JRadioButtonMenuItem(zoom + " : 1");
      zItem.setSelected(zoom == surface.getZoomFactor());
      zoomMenu.add(zItem);
      zoomGroup.add(zItem);
      zItem.addActionListener(ev -> surface.setZoomFactor(zoom));
    }
    menuBar.add(zoomMenu);
    // Add "Edit" Menu
    JMenu editMenu = new JMenu("Edit");
    // Add "Undo" Menu Item
    JMenuItem undo = new JMenuItem("Undo");
    undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask));
    undo.setEnabled(false);
    undo.addActionListener((ev) -> surface.popFromUndoStack());
    editMenu.add(undo);
    surface.addUndoListener(undo::setEnabled);
    // Add "Redo" Menu Item
    JMenuItem redo = new JMenuItem("Redo");
    redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask + InputEvent.SHIFT_DOWN_MASK));
    redo.setEnabled(false);
    redo.addActionListener((ev) -> surface.popFromRedoStack());
    editMenu.add(redo);
    surface.addRedoListener(redo::setEnabled);
    // Add "Remove Selected" Menu Item
    JMenuItem removeSelected = new JMenuItem("Delete Selected");
    removeSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, cmdMask));
    removeSelected.setEnabled(false);
    removeSelected.addActionListener((ev) -> surface.removeSelected());
    editMenu.add(removeSelected);
    // Add "Duplicate Selected" Menu Item
    JMenuItem dupSelected = new JMenuItem("Duplicate Selected");
    dupSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, cmdMask));
    dupSelected.setEnabled(false);
    dupSelected.addActionListener((ev) -> surface.duplicateSelected());
    editMenu.add(dupSelected);
    // Add "Edit Selected" Menu Item
    JMenuItem editSelected = new JMenuItem("Edit Selected");
    editSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, cmdMask));
    editSelected.setEnabled(false);
    editSelected.addActionListener((ev) -> {
      CADShape sel = surface.getSelected();
      if (sel != null && sel.editParameterDialog(surface)) {
        // User clicked dialog's OK button
        surface.getSelected().updateShape();
        surface.repaint();
      }
    });
    editMenu.add(editSelected);
    // Add "Move Selected" Menu Item
    JMenuItem moveSelected = new JMenuItem("Move Selected");
    moveSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, cmdMask));
    moveSelected.setEnabled(false);
    moveSelected.addActionListener((ev) -> {
      surface.moveSelected();
    });
    editMenu.add(moveSelected);
    // Add "Move Selected" Menu Item
    JMenuItem rotateSelected = new JMenuItem("Rotate Group Around Selected");
    rotateSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, cmdMask));
    rotateSelected.setEnabled(false);
    rotateSelected.addActionListener((ev) -> {
      surface.rotateGroupAroundSelected();
    });
    editMenu.add(rotateSelected);
    // Add "Round Corners of Selected Shape" Menu Item
    JMenuItem roundCorners = new JMenuItem("Round Corners of Selected Shape");
    roundCorners.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, cmdMask));
    roundCorners.setEnabled(false);
    ParameterDialog.ParmItem[] parms = {new ParameterDialog.ParmItem("radius|in", 0d)};
    ParameterDialog dialog = (new ParameterDialog(parms, new String[] {"Round", "Cancel"}));
    dialog.setLocationRelativeTo(surface.getParent());
    roundCorners.addActionListener((ev) -> {
      dialog.setVisible(true);              // Note: this call invokes dialog
      if (dialog.doAction()) {
        surface.pushToUndoStack();
        double val = (Double) parms[0].value;
        surface.roundSelected(val);
      }
    });
    editMenu.add(roundCorners);
    // Add "Ungroup Selected" Menu Item
    JMenuItem unGroupSelected = new JMenuItem("Ungroup Selected");
    unGroupSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, cmdMask));
    unGroupSelected.setEnabled(false);
    unGroupSelected.addActionListener((ev) -> surface.unGroupSelected());
    editMenu.add(unGroupSelected);
    // Add "Align Grouped Shape(s) to Selected Shape's" submenu
    JMenu alignMenu = new JMenu("Align Grouped Shape(s) to Selected Shape's");
    alignMenu.setEnabled(false);
    // Add "X Coord" Submenu Item
    JMenuItem alignXSelected = new JMenuItem("X Coord");
    alignXSelected.addActionListener((ev) -> surface.alignSelectedShapes(true, false));
    alignMenu.add(alignXSelected);
    // Add "Y Coord" Submenu Item
    JMenuItem alignYSelected = new JMenuItem("Y Coord");
    alignYSelected.addActionListener((ev) -> surface.alignSelectedShapes(false, true));
    alignMenu.add(alignYSelected);
    // Add "X & Y Coord" Submenu Item
    JMenuItem alignXYSelected = new JMenuItem("X & Y Coords");
    alignXYSelected.addActionListener((ev) -> surface.alignSelectedShapes(true, true));
    alignMenu.add(alignXYSelected);
    editMenu.add(alignMenu);
    // Add "Add Grouped Shapes" Menu Item
    JMenuItem addSelected = new JMenuItem("Add Grouped Shape{s) to Selected Shape");
    addSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, cmdMask));
    addSelected.setEnabled(false);
    addSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(true));
    editMenu.add(addSelected);
    // Add "Subtract Group from Selected" Menu Item
    JMenuItem subtractSelected = new JMenuItem("Take Away Grouped Shape(s) from Selected Shape");
    subtractSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, cmdMask));
    subtractSelected.setEnabled(false);
    subtractSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(false));
    editMenu.add(subtractSelected);
    // Add SelectListener to enable/disable menus, as needed
    surface.addSelectListener((shape, selected) -> {
      removeSelected.setEnabled(selected);
      dupSelected.setEnabled(selected);
      editSelected.setEnabled(selected);
      moveSelected.setEnabled(selected);
      roundCorners.setEnabled(selected);
      saveSelected.setEnabled(selected);
      boolean hasGroup = surface.selected != null && surface.selected.getGroup() != null;
      unGroupSelected.setEnabled(hasGroup);
      addSelected.setEnabled(hasGroup);
      alignMenu.setEnabled(hasGroup);
      subtractSelected.setEnabled(hasGroup);
      rotateSelected.setEnabled(hasGroup);
    });
    menuBar.add(editMenu);
    /*
     *  Add "Import" Menu
     */
    JMenu importMenu = new JMenu("Import");
    // Add "LaserCut File" Item to File menu
    JMenuItem importObj = new JMenuItem("LaserCut File");
    importObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, cmdMask));
    importObj.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.lzr)", "lzr");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          surface.placeShapes(loadDesign(tFile));
          prefs.put("default.dir", tFile.getAbsolutePath());
          setTitle("LaserCut - (" + tFile + ")");
        } catch (Exception ex) {
          showErrorDialog("Unable to import LaserCut file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(importObj);
    // Add "SVG File" menu item
    JMenuItem svgRead = new JMenuItem("SVG File");
    svgRead.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Scalable Vector Graphics files (*.svg)", "svg");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.svg.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          prefs.put("default.svg.dir", tFile.getAbsolutePath());
          SVGParser parser = new SVGParser(true);
          //parser.enableDebug(true);
          Shape[] shapes = parser.parseSVG(tFile);
          shapes = SVGParser.removeOffset(shapes);
          Shape shape = SVGParser.combinePaths(shapes);
          Rectangle2D bounds = BetterBoundingBox.getBounds(shape);
          double offX = bounds.getWidth() / 2;
          double offY = bounds.getHeight() / 2;
          AffineTransform at = AffineTransform.getTranslateInstance(-offX, -offY);
          shape = at.createTransformedShape(shape);
          CADShape shp = new CADShape(shape, 0.125 + offX, 0.125 + offY, 0, true);
          if (shp.placeParameterDialog(surface)) {
            surface.placeShape(shp);
          }
        } catch (Exception ex) {
          showErrorDialog("Unable to load SVG file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(svgRead);
     // Add Gerber menu item
    gerberZip = new JMenuItem("Gerber Zip");
    gerberZip.setVisible(prefs.getBoolean("gerber.import", false));
    gerberZip.addActionListener((ActionEvent ev) -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Gerber .zip files (*.zip)", "zip");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.zip.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          prefs.put("default.zip.dir", tFile.getAbsolutePath());
          GerberZip gerber = new GerberZip(tFile);
          List<GerberZip.ExcellonHole> holes = gerber.parseExcellon();
          List<List<Point2D.Double>> outlines = gerber.parseOutlines();
          Rectangle2D.Double bounds = GerberZip.getBounds(outlines);
          // System.out.println("PCB Size: " + bounds.getWidth() + " inches, " + bounds.getHeight() + " inches");
          double yBase = bounds.getHeight();
          double xOff = .125, yOff = .125;
          CADShapeGroup group = new CADShapeGroup();
          List<CADShape> gShapes = new ArrayList<>();
          for (GerberZip.ExcellonHole hole : holes) {
            CADShape circle = new CADOval(hole.xLoc + xOff, yBase - hole.yLoc + yOff, hole.diameter, hole.diameter, 0, true);
            group.addToGroup(circle);
            gShapes.add(circle);
          }
          if (false) {
            CADRectangle rect = new CADRectangle(xOff, yOff, bounds.getWidth(), bounds.getHeight(), .050, 0, false);
            group.addToGroup(rect);
            gShapes.add(rect);
          } else {
            // Build shapes for all outlines
            for (List<Point2D.Double> points : outlines) {
              Path2D.Double path = new Path2D.Double();
              boolean first = true;
              for (Point2D.Double point : points) {
                if (first) {
                  path.moveTo(point.getX(), yBase - point.getY());
                  first = false;
                } else {
                  path.lineTo(point.getX(), yBase - point.getY());
                }
              }
              CADShape outline = new CADShape(path, xOff, yOff, 0, true);
              group.addToGroup(outline);
              gShapes.add(outline);
            }
          }
          surface.placeShapes(gShapes);
          repaint();
        } catch (Exception ex) {
          showErrorDialog("Unable to load Gerber file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(gerberZip);
    menuBar.add(importMenu);
    /*
     *  Add "Export" Menu
     */
    JMenu exportMenu = new JMenu("Export");
     // Add "Zing Laser" Menu Item
    JMenu zingMenu = new JMenu("Zing Laser");
        /*
    // Add "Test Optimize Path" Menu Item
    JMenuItem optimizePath = new JMenuItem("Test Optimize Path");
    optimizePath.addActionListener(ev -> {
      surface.optimizePath();
      for (CADShape shape : surface.getDesign()) {
        double dist = Math.sqrt(shape.xLoc * shape.xLoc + shape.yLoc * shape.yLoc);
        System.out.println(dist);
      }
    });
    zingMenu.add(optimizePath);
    */
    // Add "Send to Zing" Submenu Item
    JMenuItem sendToZing = new JMenuItem("Send Job to Zing");
    sendToZing.addActionListener(ev -> {
      if (zingIpAddress == null ||  zingIpAddress.length() == 0) {
        showErrorDialog("Please set the Zing's IP Address in Export->Zing Settings");
        return;
      }
      if (showWarningDialog("Press OK to Send Job to Zing")) {
        EpilogZing lasercutter = new EpilogZing(zingIpAddress);
        // Set Properties for Materla, such as for 3 mm birch plywood, Set: 60% speed, 80% power, 0 focus, 500 Hz.
        PowerSpeedFocusFrequencyProperty cutProperties = new PowerSpeedFocusFrequencyProperty();
        cutProperties.setProperty("speed", zingCutSpeed);
        cutProperties.setProperty("power", zingCutPower);
        cutProperties.setProperty("frequency", zingCutFreq);
        cutProperties.setProperty("focus", 0.0f);
        PowerSpeedFocusFrequencyProperty engraveProperties = new PowerSpeedFocusFrequencyProperty();
        engraveProperties.setProperty("speed", zingEngraveSpeed);
        engraveProperties.setProperty("power", zingEngravePower);
        engraveProperties.setProperty("frequency", zingEngraveFreq);
        engraveProperties.setProperty("focus", 0.0f);
        LaserJob job = new LaserJob("laserCut", "laserCut", "laserCut");   // title, name, user
        // Process raster engrave passes, if any

        // Process cut and vector engrave passes
        for (int ii = 0; ii < 2; ii++) {
          boolean doCut = ii == 1;
          // Transform all the shapesInGroup into a series of line segments
          int lastX = 0, lastY = 0;
          VectorPart vp = new VectorPart(doCut ? cutProperties : engraveProperties, ZING_PPI);
          // Loop detects pen up/pen down based on start and end points of line segments
          for (CADShape shape : surface.selectLaserItems(doCut)) {
            ArrayList<Line2D.Double> lines = shape.getScaledLines(ZING_PPI);
            if (lines.size() > 0) {
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
          job.addPart(vp);
        }
        ZingMonitor zMon = new ZingMonitor();
        new Thread(() -> {
          try {
            lasercutter.sendJob(job, new ProgressListener() {
              @Override
              public void progressChanged (Object o, int i) {
                zMon.setProgress(i);
              }

              @Override
              public void taskChanged (Object o, String s) {
                //zMon.setProgress(i);
              }
            });
          } catch (Exception ex) {
            showErrorDialog("Unable to send job to Zing");
          } finally {
            zMon.setVisible(false);
            zMon.setVisible(false);
            zMon.dispose();
          }
        }).start();
      }
    });
    zingMenu.add(sendToZing);
    // Add "Zing Settings" Submenu Item
    JMenuItem zingSettings = new JMenuItem("Zing Settings");
    zingSettings.addActionListener(ev -> {
      ParameterDialog.ParmItem[] parmSet = {
          new ParameterDialog.ParmItem("Zing IP Add", zingIpAddress),
          new ParameterDialog.ParmItem("Cut Power|%", zingCutPower),
          new ParameterDialog.ParmItem("Cut Speed", zingCutSpeed),
          new ParameterDialog.ParmItem("Cut Freq|Hz", zingCutFreq),
          new ParameterDialog.ParmItem("Engrave Power|%", zingEngravePower),
          new ParameterDialog.ParmItem("Engrave Speed", zingEngraveSpeed),
          new ParameterDialog.ParmItem("Engrave Freq|Hz", zingEngraveFreq),
      };
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, this)) {
        prefs.put("zing.ip", zingIpAddress = (String) parmSet[0].value);
        prefs.putInt("zing.power", zingCutPower = (Integer) parmSet[1].value);
        prefs.putInt("zing.speed", zingCutSpeed = (Integer) parmSet[2].value);
        prefs.putInt("zing.freq",  zingCutFreq =  (Integer) parmSet[3].value);
        prefs.putInt("zing.epower", zingCutPower = (Integer) parmSet[4].value);
        prefs.putInt("zing.espeed", zingCutSpeed = (Integer) parmSet[5].value);
        prefs.putInt("zing.efreq",  zingCutFreq =  (Integer) parmSet[6].value);
      }
    });
    zingMenu.add(zingSettings);
    // Add "Resize for Zing" Full Size Submenu Items
    JMenuItem zingResize = new JMenuItem("Resize for Zing (" + (zingFullSize.width / SCREEN_PPI) + "x" + (zingFullSize.height / SCREEN_PPI) + ")");
    zingResize.addActionListener(ev -> {
      surface.setSurfaceSize(zingFullSize);
      pack();
      repaint();
    });
    zingMenu.add(zingResize);
    JMenuItem zing12x12 = new JMenuItem("Resize for Zing (" + (zing12x12Size.width / SCREEN_PPI) + "x" + (zing12x12Size.height / SCREEN_PPI) + ")");
    zing12x12.addActionListener(ev -> {
      surface.setSurfaceSize(zing12x12Size);
      pack();
      repaint();
    });
    zingMenu.add(zing12x12);
    exportMenu.add(zingMenu);
    boolean jPortError = false;
    if (enableMiniLazer) {
      try {
        jPort = new JSSCPort(prefs);
         // Add "Mini Laser" Menu Item
        JMenu miniLaserMenu = new JMenu("Mini Laser");
        // Add "Send to Mini Laser" Submenu Item
        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JLabel("Iterations: ", JLabel.RIGHT));
        JTextField tf = new JTextField("1", 4);
        panel.add(tf);
        JMenuItem sendToMiniLazer = new JMenuItem("Send to GRBL to Mini Laser");
        sendToMiniLazer.addActionListener((ActionEvent ex) -> {
          int result = JOptionPane.showConfirmDialog(this, panel, "Send GRBL to Mini Laser", JOptionPane.YES_NO_OPTION,
              JOptionPane.PLAIN_MESSAGE, null);
          if (result == JOptionPane.OK_OPTION) {
            try {
              int iterations = Integer.parseInt(tf.getText());
              // Generate G_Code for GRBL 1.1
              ArrayList<String> cmds = new ArrayList<>();
              // Add starting G-codes
              cmds.add("G20");                                          // Set Inches as Units
              int speed = Math.max(1, miniSpeed);
              cmds.add("M05");                                          // Set Laser Off
              int power = Math.min(1000, miniPower);
              cmds.add("S" + power);                                    // Set Laser Power (0 - 255)
              cmds.add("F" + speed);                                    // Set cut speed
              double lastX = 0, lastY = 0;
              for (int ii = 0; ii < iterations; ii++) {
                boolean laserOn = false;
                for (CADShape shape : surface.selectLaserItems(true)) {
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
                        cmds.add(miniDynamicLaser ? "M04" : "M03");        // Set Laser On
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
                        cmds.add(miniDynamicLaser ? "M04" : "M03");        // Set Laser On
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
              cmds.add("M5");                                           // Set Laser Off
              cmds.add("G00 X0 Y0");                                    // Move back to Origin
              new GRBLSender(cmds.toArray(new String[cmds.size()]));
            } catch (Exception ex2) {
              showErrorDialog("Invalid parameter " + tf.getText());
            }
          }
        });
        miniLaserMenu.add(sendToMiniLazer);
        // Add "Mini Lazer Settings" Submenu Item
        JMenuItem miniLazerSettings = new JMenuItem("Mini Lazer Settings");
        miniLazerSettings.addActionListener(ev -> {
          // Edit Mini Lazer Settings
          ParameterDialog.ParmItem[] parmSet = {new ParameterDialog.ParmItem("Dynamic Laser", miniDynamicLaser),
                                                new ParameterDialog.ParmItem("Power|%", miniPower),
                                                new ParameterDialog.ParmItem("Speed", miniSpeed)};
          if (ParameterDialog.showSaveCancelParameterDialog(parmSet, this)) {
            prefs.putBoolean("dynamicLaser", miniDynamicLaser = (Boolean) parmSet[0].value);
            prefs.putInt("mini.power", miniPower = (Integer) parmSet[1].value);
            prefs.putInt("mini.speed", miniSpeed = (Integer) parmSet[2].value);
          }
        });
        miniLaserMenu.add(miniLazerSettings);
        // Add "Resize for Mini Lazer" Submenu Item
        JMenuItem miniResize = new JMenuItem("Resize for Mini Lazer (" + (miniSize.width / SCREEN_PPI) + "x" + (miniSize.height / SCREEN_PPI) + ")");
        miniResize.addActionListener(ev -> {
          surface.setSurfaceSize(miniSize);
          pack();
          repaint();
        });
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
          int res = JOptionPane.showOptionDialog(this, frame, "Jog Controls",
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
          JOptionPane.showOptionDialog(this, sPanel, "GRBL Settings",
              JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
              null, options, options[0]);
        });
        miniLaserMenu.add(settings);
        // Add "Port" and "Baud" Submenu to MenuBar
        miniLaserMenu.add(jPort.getPortMenu());
        miniLaserMenu.add(jPort.getBaudMenu());
        // Add Mini Laser Menu Item to MenuBar
        exportMenu.add(miniLaserMenu);
      } catch (Exception ex) {
        jPortError = true;
      }
    }
    // Add "Export to PDF File" Menu Item
    JMenuItem pdfOutput = new JMenuItem("Export to PDF File");
    pdfOutput.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.pdf)", "pdf");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.pdf", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fName = sFile.getName();
        if (!fName.contains(".")) {
          sFile = new File(fName + ".pdf");
        }
        try {
          if (sFile.exists()) {
            if (showWarningDialog("Overwrite Existing file?")) {
              surface.writePDF(sFile);
            }
          } else {
            surface.writePDF(sFile);
          }
        } catch (Exception ex) {
          showErrorDialog("Unable to save file");
          ex.printStackTrace();
        }
        prefs.put("default.pdf", sFile.getAbsolutePath());
      }
    });
    exportMenu.add(pdfOutput);
    menuBar.add(exportMenu);
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
    if (jPortError) {
      showErrorDialog("Unable to initialize JSSCPort serial port");
    }
    if (true) {
    /*
     * * * * * * * * * * * * * * * * * * *
     * Add some test shapes to DrawSurface
     * * * * * * * * * * * * * * * * * * *
     */
      // Create + shape via additive and subtractive geometric operations
      RoundRectangle2D.Double c1 = new RoundRectangle2D.Double(-.80, -.30, 1.60, .60, .40, .40);
      RoundRectangle2D.Double c2 = new RoundRectangle2D.Double(-.30, -.80, .60, 1.60, .40, .40);
      Area a1 = new Area(c1);
      a1.add(new Area(c2));
      Point2D.Double[] quadrant = {new Point2D.Double(-1, -1), new Point2D.Double(1, -1), new Point2D.Double(-1, 1), new Point2D.Double(1, 1)};
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
      surface.addShape(new CADText(4.625, .25, "Belle", "Helvetica", "bold", 72, 0, false));
      // Add Test Gear
      surface.addShape(new CADGear(2.25, 2.25, .1, 30, 10, 20, .25, 0, mmToInches(3)));
      savedCrc = surface.getDesignChecksum();   // Allow quit if unchanged
    }
  }

  class ZingMonitor extends JDialog {
    private JProgressBar    progress;
    private JTextArea       status;

    ZingMonitor () {
      super(LaserCut.this, "Zing Monitor");
      add(progress = new JProgressBar(), BorderLayout.NORTH);
      progress.setMaximum(100);
      JScrollPane sPane = new JScrollPane(status = new JTextArea());
      status.append("Starting Job...\n");
      status.setMargin(new Insets(3, 3, 3, 3));
      DefaultCaret caret = (DefaultCaret) status.getCaret();
      caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      add(sPane, BorderLayout.CENTER);
      Rectangle loc = LaserCut.this.getBounds();
      setSize(300, 150);
      setLocation(loc.x + loc.width / 2 - 150, loc.y + loc.height / 2 - 75);
      setVisible(true);
    }

    void setProgress(int prog) {
      progress.setValue(prog);
      status.append("Completed prog" + prog + "%\n");
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
    private int         wState;
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
      frame = new JDialog(LaserCut.this, "G-Code Monitor");
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
      Rectangle loc = LaserCut.this.getBounds();
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

  private List<CADShape> loadDesign (File fName) throws IOException, ClassNotFoundException {
    FileInputStream fileIn = new FileInputStream(fName);
    ObjectInputStream in = new ObjectInputStream(fileIn);
    ArrayList<CADShape> design = (ArrayList<CADShape>) in.readObject();
    in.close();
    fileIn.close();
    return design;
  }

  private void saveDesign (File fName, List<CADShape> shapes) throws IOException {
    FileOutputStream fileOut = new FileOutputStream(fName);
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(shapes);
    out.close();
    fileOut.close();
  }

  private boolean showWarningDialog (String msg) {
    return JOptionPane.showConfirmDialog(this, msg,
        "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
  }

  private void showErrorDialog (String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.PLAIN_MESSAGE);
  }

  static double mmToInches (double mm) {
    return mm / 25.4;
  }

  static double inchesToMM (double inches) {
    return inches * 25.4;
  }

  static class CADShape implements Serializable {
    private static final long serialVersionUID = 3716741066289930874L;
    public double     xLoc, yLoc, rotation;   // Note: must pubic for reflection
    public boolean    centered, engrave;      // Note: must pubic for reflection
    CADShapeGroup     group;
    Shape             shape;
    transient Shape   builtShape;
    transient boolean isSelected;

    /**
     * Default constructor is used to instantiate subclasses in "Shapes" Menu
     */
    CADShape () {
      // Set typical initial values, which user can edit before saving
      this(.2, .2);
    }

    CADShape (double x, double y) {
      xLoc = x;
      yLoc = y;
    }

    @SuppressWarnings("unused")
    CADShape (Shape shape, double xLoc, double yLoc, double rotation, boolean centered) {
      if (shape instanceof Area) {
        // Workaround for Area not Serializable problem
        this.shape = AffineTransform.getTranslateInstance(0, 0).createTransformedShape(shape);
      } else {
        this.shape = shape;
      }
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    void setLocationAndOrientation (double xLoc, double yLoc, double rotation, boolean centered) {
      this.xLoc = xLoc;
      this.yLoc = yLoc;
      this.rotation = rotation;
      this.centered = centered;
    }

    Rectangle2D getBounds () {
      return getShape().getBounds2D();
    }

    /**
     * Return the distance from point cx/cy to starting point of shape's path
     * @param cx x coord
     * @param cy y reference
     * @return distance to cx/cy
     */
    public double distanceTo (double cx, double cy) {
      Shape shape = getShape();
      Rectangle2D bounds = shape.getBounds2D();
      PathIterator pi = shape.getPathIterator(new AffineTransform());
      double x = this.xLoc;
      double y = this.yLoc;
      loop:
      while (!pi.isDone()) {
        double[] coords = new double[6];
        int type = pi.currentSegment(coords);
        switch (type) {
          case PathIterator.SEG_MOVETO:
            if (centered) {
              x += coords[0];
              y += coords[1];
            } else {
              x += coords[0] - bounds.getX();
              y += coords[1] - bounds.getY();
            }
            break loop;
        }
      }
      double dx = cx - x;
      double dy = cy - y;
      return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Uses reflection to deep copy subclasses of CADShape
     * @return copy of CADShape
     */
    CADShape copy () {
      try {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bin);
        CADShape newShape = (CADShape) ois.readObject();
        newShape.setGroup(null);
        return newShape;
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      return null;
    }

    CADShapeGroup getGroup () {
      return group;
    }

    void setGroup (CADShapeGroup group) {
      this.group = group;
    }

    /**
     * Override in subclass to regenerate shape when parameters are changed
     * @return Shape built using current parameter settings
     */
    Shape buildShape () {
      return shape;
    }

    /**
     * Get shape, if not build, build shape first
     * @return Shape
     */
    Shape getShape () {
      if (builtShape == null) {
        builtShape = buildShape();
      }
      return builtShape;
    }

    /**
     * Used to create a translated Shape so it can be drawn relative to either its center, or its upper left corner.
     * For efficiency, the Shape used as input is cached as 'builtShape', or generated by calling buildShape().
     * This reduces the size of saved files as only the parameters that define the Shape need to be saved and loaded.
     * buildShape() is also called to regenerate a Shape's outline after any of its parameters are edited.
     * Note: not all Shapes are built from parameters, however.  See buildShape() for details.
     * @return translated Shape
     */
    private Shape getLocallyTransformedShape () {
      Shape dShape = getShape();
      AffineTransform at = new AffineTransform();
      if (centered) {
        // Position Shape centered on xLoc/yLoc in inches (x from left, y from top)
        at.rotate(Math.toRadians(rotation));
      } else {
        // Position shape relative to its upper left bounding box at position xLoc/yLoc in inches
        Rectangle2D bounds = dShape.getBounds2D();
        at.rotate(Math.toRadians(rotation));
        at.translate(bounds.getWidth() / 2, bounds.getHeight() / 2);
      }
      return at.createTransformedShape(dShape);
    }

    // Translate Shape to screen position
    private Shape getScreenTranslatedShape () {
      AffineTransform at = AffineTransform.getTranslateInstance(xLoc, yLoc);
      return at.createTransformedShape(getLocallyTransformedShape());
    }

    /**
     * Uses Shape.pathIterator() to convert the Shape into a series of lines.  The size input Shape is
     * assumed to be defined in inches, but the AffineTransform parameter can be used to scale up to the
     * final render resolution.  Note: cubic and quadratic bezier curves calculate an approximaiton of the
     * arc length of the curve to determine the number of line segments used to approximate the curve.
     * @param shape Shape path to render
     * @param scale used to scale from inches to the render resolution, such as Screen or Laser DPI.
     * @return array of lines
     */
    static Line2D.Double[] transformShapeToLines (Shape shape, double scale) {
      // Use PathIterator to convert Shape into a series of lines defining a path
      AffineTransform at = scale != 0 ? AffineTransform.getScaleInstance(scale, scale) : null;
      double maxSegSize = .01;                                                  // Max size of a bezier segment, in inches
      ArrayList<Line2D.Double> lines = new ArrayList<>();
      PathIterator pi = shape.getPathIterator(at);
      double xLoc = 0, yLoc = 0;
      double mX = 0, mY = 0;
      while (!pi.isDone()) {
        double[] coords = new double[6];
        int type = pi.currentSegment(coords);
        switch (type) {
          case PathIterator.SEG_CLOSE:
            lines.add(new Line2D.Double(xLoc, yLoc, mX, mY));
            break;
          case PathIterator.SEG_MOVETO:
            mX = xLoc = coords[0];
            mY = yLoc = coords[1];
            break;
          case PathIterator.SEG_LINETO:
            lines.add(new Line2D.Double(xLoc, yLoc, xLoc = coords[0], yLoc = coords[1]));
            break;
          case PathIterator.SEG_CUBICTO:
            // Decompose 4 point, cubic bezier curve into line segments
            Point2D.Double c1 = new Point2D.Double(xLoc, yLoc);                 // Start point
            Point2D.Double c2 = new Point2D.Double(coords[0], coords[1]);       // Control point 1
            Point2D.Double c3 = new Point2D.Double(coords[2], coords[3]);       // Control point 2
            Point2D.Double c4 = new Point2D.Double(coords[4], coords[5]);       // End point
            Point2D.Double[] cControl = {c1, c2, c3, c4};
            Point2D.Double[] tmp = new Point2D.Double[4];
            // number of segments used in line approximaiton of the curve is based on an approximation of arc length
            double cubicArcLength = (c1.distance(c2) + c2.distance(c3) + c3.distance(c4) + c1.distance(c4)) / (2 * scale);
            int segments = Math.max(3, (int) Math.round(cubicArcLength / maxSegSize));
            for (int ii = 0; ii < segments; ii++) {
              double t = ((double) ii) / (segments - 1);
              for (int jj = 0; jj < cControl.length; jj++)
                tmp[jj] = new Point2D.Double(cControl[jj].x, cControl[jj].y);
              for (int qq = 0; qq < cControl.length - 1; qq++) {
                for (int jj = 0; jj < cControl.length - 1; jj++) {
                  // Subdivide points
                  tmp[jj].x -= (tmp[jj].x - tmp[jj + 1].x) * t;
                  tmp[jj].y -= (tmp[jj].y - tmp[jj + 1].y) * t;
                }
              }
              lines.add(new Line2D.Double(xLoc, yLoc, xLoc = tmp[0].x, yLoc = tmp[0].y));
            }
            break;
          case PathIterator.SEG_QUADTO:
            // Decompose 3 point, quadratic bezier curve into line segments
            Point2D.Double q1 = new Point2D.Double(xLoc, yLoc);                 // Start point
            Point2D.Double q2 = new Point2D.Double(coords[0], coords[1]);       // Control point
            Point2D.Double q3 = new Point2D.Double(coords[2], coords[3]);       // End point
            double xLast = q1.x;
            double yLast = q1.y;
            // number of segments used in line approximaiton of the curve is based on an approximation of arc length
            double quadArcLength = (q1.distance(q2) + q2.distance(q3) + q1.distance(q3)) / (2 * scale);
            segments = Math.max(3, (int) Math.round(quadArcLength / maxSegSize));
            for (int ii = 1; ii < segments; ii++) {
              // Use step as a ratio to subdivide lines
              double step = (double) ii / segments;
              double x = (1 - step) * (1 - step) * q1.x + 2 * (1 - step) * step * q2.x + step * step * q3.x;
              double y = (1 - step) * (1 - step) * q1.y + 2 * (1 - step) * step * q2.y + step * step * q3.y;
              lines.add(new Line2D.Double(xLast, yLast, x, y));
              xLast = x;
              yLast = y;
            }
            lines.add(new Line2D.Double(xLast, yLast, xLoc = q3.x, yLoc = q3.y));
            break;
          default:
            System.out.println("Error, Unknown PathIterator Type: " + type);
            break;
        }
        pi.next();
      }
      return lines.toArray(new Line2D.Double[lines.size()]);
    }

    void draw (Graphics2D g2, double zoom) {
      Stroke saveStroke = g2.getStroke();
      Shape dShape = getScreenTranslatedShape();
      boolean inGroup = getGroup() != null && getGroup().isGroupSelected();
      boolean highlight = isSelected || inGroup;
      g2.setStroke(getShapeStroke(highlight, isSelected));
      g2.setColor(getShapeColor (highlight, engrave));
      // Scale Shape to scale and draw it
      if (false) {
        // Use PathIterator to draw Shape line by line
        for (Line2D.Double line : transformShapeToLines(dShape, zoom)) {
          g2.draw(line);
        }
      } else {
        // Draw Area directly
        AffineTransform atScale = AffineTransform.getScaleInstance(zoom, zoom);
        g2.draw(atScale.createTransformedShape(dShape));
      }
      if (isSelected || this instanceof CADReference) {
        double mx = xLoc * zoom;
        double my = yLoc * zoom;
        double mWid = 3 * zoom / SCREEN_PPI;
        g2.setStroke(new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f));
        g2.draw(new Line2D.Double(mx - mWid, my, mx + mWid, my));
        g2.draw(new Line2D.Double(mx, my - mWid, mx, my + mWid));
      }
      g2.setStroke(saveStroke);
    }

    Color getShapeColor (boolean highlight, boolean engrave) {
      return highlight ? engrave ? Color.RED : Color.blue : engrave ? Color.ORANGE : Color.black;
    }

    BasicStroke getShapeStroke (boolean highlight, boolean isSelected) {
      return new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f);
    }

    ArrayList<Line2D.Double> getScaledLines (double scale) {
      Shape dShape = getScreenTranslatedShape();
      // Use PathIterator to get lines from Shape
      ArrayList<Line2D.Double> lines = new ArrayList<>();
      Collections.addAll(lines, transformShapeToLines(dShape, scale));
      return lines;
    }

    /**
     * Override in subclass to let mouse drag move internal control points
     * @return true if an internal point is was dragged, else false
     */
    boolean doMovePoints (Point2D.Double point) {
      return false;
    }

    /**
     * Override in sunclass to check if a moveable internal point was clicked
     * @return true if a moveable internal point is was clicked, else false
     */
    boolean selectMovePoint (Point2D.Double point, Point2D.Double gPoint) {
      return false;
    }

    /**
     * Override in sunclass to cancel selection of a moveable internal point
     */
    void cancelMove () {
    }

    /**
     * Set position of shape to a new location
     * @param newLoc new x/y position (in shape coordinates, inches)
     * @return delta position change in a Point2D.Double object
     */
    Point2D.Double setPosition (Point2D.Double newLoc) {
      Point2D.Double delta = new Point2D.Double(newLoc.x - xLoc, newLoc.y - yLoc);
      xLoc = newLoc.x;
      yLoc = newLoc.y;
      return delta;
    }

    /**
     * Move shape's position by amount specified in 'delta'
     * @param delta amount to move CADShape
     */
    void movePosition (Point2D.Double delta) {
      xLoc += delta.x;
      yLoc += delta.y;
    }

    void setSelected (boolean selected) {
      this.isSelected = selected;
    }

    boolean isSelected () {
      return isSelected;
    }

    /**
     * Check if 'point' is close to shape's xLoc/yLoc position
     * @param point Location click on screen in model coordinates (inches)
     * @return true if close enough to consider a 'touch'
     */
    boolean isPositionClicked (Point2D.Double point) {
      double dist = point.distance(xLoc, yLoc) * SCREEN_PPI;
      return dist < 5;
    }

    /**
     * Check if 'point' is close to one of the segments that make up the shape
     * @param point Location click on screen in model coordinates (inches)
     * @return true if close enough to consider a 'touch'
     */
    boolean isShapeClicked (Point2D.Double point) {
      // Translate Shape to position, Note: point is in screen units (scale)
      Shape lShape = getScreenTranslatedShape();
      // Scale Shape to Screen scale and scan all line segments in the shape
      // return true if any is closer than 4 pixels to point
      for (Line2D.Double line : transformShapeToLines(lShape, 1)) {
        double dist = line.ptSegDist(point) * SCREEN_PPI;
        if (dist < 5)
          return true;
      }
      return false;
    }

    /**
     * Override in subclass to define which subclass fields are editable parameters
     * @return String[] or editable parameter fields
     */
    String[] getParameterNames () {
      return new String[0];
    }

    void updateShape () {
      builtShape = null;
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields.  Uses reflection to read and save
     * parameter values before clicking the mouse to place the shape.
     * @return true if used clicked OK to save
     */
    boolean placeParameterDialog (DrawSurface surface) {
      return displayShapeParameterDialog(surface, new ArrayList<>(Arrays.asList("rotation|deg", "centered", "engrave")), "Place");
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields.  Uses reflection to read and save
     * parameter values.
     * @return true if used clicked OK to save
     */
    boolean editParameterDialog (DrawSurface surface) {
      return displayShapeParameterDialog(surface, new ArrayList<>(Arrays.asList("xLoc|in", "yLoc|in", "rotation|deg", "centered", "engrave")), "Save");
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields using reflection to read and update parameter values.
     * @param surface parent Component for Dialog
     * @param parmNames List of parameter names
     * @param actionButton Text for action button, such as "Save" or "Place"
     * @return true if used clicked action button, else false if they clicked cancel.
     */
    boolean displayShapeParameterDialog (DrawSurface surface, ArrayList<String> parmNames, String actionButton) {
      parmNames.addAll(Arrays.asList(getParameterNames()));
      ParameterDialog.ParmItem[] parmSet = new ParameterDialog.ParmItem[parmNames.size()];
      for (int ii = 0; ii < parmSet.length; ii++) {
        String name = parmNames.get(ii);
        try {
          parmSet[ii] = new ParameterDialog.ParmItem(name, null);
          parmSet[ii].setValue(this.getClass().getField(parmSet[ii].name).get(this));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
      ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {actionButton, "Cancel"}));
      dialog.setLocationRelativeTo(surface.getParent());
      dialog.setVisible(true);              // Note: this call invokes dialog
      if (dialog.doAction()) {
        surface.pushToUndoStack();
        for (ParameterDialog.ParmItem parm : parmSet) {
          try {
            Field fld = this.getClass().getField(parm.name);
            fld.set(this, parm.value);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        return true;
      }
      return false;
    }

    /**
     * Used reflection to return a string showing shape's current parameters
     * @return String of comma-separated name/value pairs
     */
    String getInfo () {
      String clsName = getClass().getName();
      int idx = clsName.lastIndexOf('$');
      if (idx > 0) {
        clsName = clsName.substring(idx + 1);
      }
      StringBuilder buf = new StringBuilder(clsName + ": ");
      ArrayList<String> parmNames = new ArrayList<>(Arrays.asList("xLoc|in", "yLoc|in", "rotation|deg", "centered"));
      parmNames.addAll(Arrays.asList(getParameterNames()));
      boolean first = true;
      for (String name : parmNames) {
        ParameterDialog.ParmItem item = new ParameterDialog.ParmItem(name, null);
        if (!first) {
          buf.append(", ");
        }
        first = false;
        try {
          buf.append(item.name);
          buf.append(": ");
          Field fld = this.getClass().getField(item.name);
          Object value = fld.get(this);
          if (item.valueType != null  && item.valueType instanceof String[]) {
            String[] labels = ParameterDialog.getLabels((String[]) item.valueType);
            String[] values = ParameterDialog.getValues((String[]) item.valueType);
            value = labels[Arrays.asList(values).indexOf(value)];
          }
          buf.append(((value instanceof Double) ? LaserCut.df.format(value) : value) + (item.units.length() > 0 ? " " + item.units : ""));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
      return buf.toString();
    }
  }

  interface CADNoDraw {}  // Marker Interface

  static class CADReference extends CADShape implements Serializable, CADNoDraw {
    private static final long serialVersionUID = 8204176292743368277L;
    /**
     * Default constructor used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADReference () {
      centered = true;
      rotation = 45;
    }

    CADReference (double xLoc, double yLoc) {
      setLocationAndOrientation(xLoc, yLoc, 0, true);
    }

    @Override
    Shape buildShape () {
      return new Rectangle2D.Double(-.1, -.1, .2, .2);
    }

    @Override
    Color getShapeColor (boolean highlight, boolean engrave) {
      return new Color(0, 128, 0);
    }

    @Override
    BasicStroke getShapeStroke (boolean highlight, boolean isSelected) {
      final float dash1[] = {3.0f};
      return new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, dash1, 0.5f);
    }

    @Override
    boolean placeParameterDialog (DrawSurface surface) {
      return displayShapeParameterDialog(surface, new ArrayList<>(), "Save");
    }

    @Override
    boolean editParameterDialog (DrawSurface surface) {
      return displayShapeParameterDialog(surface, new ArrayList<>(Arrays.asList("xLoc|in", "yLoc|in")), "Place");
    }
  }

  static class CADRasterImage extends CADShape implements Serializable, CADNoDraw {
    private static final long serialVersionUID = 2309856254388651139L;
    public double             width, height, scale = 100.0;
    public String             imageDpi;
    Dimension                 dpi;
    transient BufferedImage   img;

    CADRasterImage () {
      engrave = true;
    }

    void loadImage (File imgFile) throws IOException {
      dpi = getImageDPI(imgFile);
      imageDpi = dpi.width + "x" + dpi.height;
      img = ImageIO.read(imgFile);
      ColorModel cm = img.getColorModel();
      width = (double) img.getWidth() / dpi.width;
      height = (double) img.getHeight() / dpi.height;
    }

    @Override
    void draw (Graphics2D g, double zoom) {
      Graphics2D g2 =  (Graphics2D)g.create();
      BufferedImage bufimg = getImage();
      // Transform image for centering, rotation and scale
      AffineTransform at = new AffineTransform();
      if (centered) {
        at.translate(xLoc * zoom, yLoc * zoom);
        at.scale(scale / 100 * SCREEN_PPI / dpi.width, scale / 100 * SCREEN_PPI / dpi.height);
        at.rotate(Math.toRadians(rotation));
        at.translate(-bufimg.getWidth() / 2.0, -bufimg.getHeight() / 2.0);
      } else {
        at.translate(xLoc * zoom, yLoc * zoom);
        at.scale(scale / 100 * SCREEN_PPI / dpi.width, scale / 100 * SCREEN_PPI / dpi.height);
        at.rotate(Math.toRadians(rotation));
      }
      // Draw with 40% Alpha to make image semi transparent
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      g2.drawImage(bufimg, at, null);
      g2.dispose();
      super.draw(g, zoom);
    }

    BufferedImage getImage () {
      // Convert Image to greyscale
      BufferedImage bufimg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g2d = bufimg.createGraphics();
      g2d.drawImage(img, 0, 0, null);
      g2d.dispose();
      return bufimg;
    }

    @Override
    Shape buildShape () {
      AffineTransform at = new AffineTransform();
      at.scale(scale / 100, scale / 100);
      return at.createTransformedShape(new Rectangle2D.Double(-width / 2, -height / 2, width, height));
    }

    @Override
    boolean editParameterDialog (DrawSurface surface) {
      return displayShapeParameterDialog(surface, new ArrayList<>(Arrays.asList("xLoc|in", "yLoc|in", "*width|in", "*height|in",
                                                                                "*imageDpi", "rotation|deg", "scale|%", "centered")), "Save");
    }

    // Custom write serializer for BufferedImage
    private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      ImageIO.write(img, "png", out);
    }

    // Custom read serializer for BufferedImage
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      img = ImageIO.read(in);
    }

    @Override
    Color getShapeColor (boolean highlight, boolean engrave) {
      return highlight ? Color.blue : Color.lightGray;
    }

    @Override
    BasicStroke getShapeStroke (boolean highlight, boolean isSelected) {
      final float dash1[] = {8.0f};
      return new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, dash1, 0.5f);
    }

    static Dimension getImageDPI (File file) throws IOException {
      ImageInputStream iis = ImageIO.createImageInputStream(file);
      try {
        Iterator it = ImageIO.getImageReaders(iis);
        if (it.hasNext()) {
          ImageReader reader = (ImageReader) it.next();
          try {
            reader.setInput(iis);
            IIOMetadata meta = reader.getImageMetadata(0);
            String formatName = meta.getNativeMetadataFormatName();
            Element tree = (Element) meta.getAsTree(formatName);
            NodeList nodes;
            if ((nodes = tree.getElementsByTagName("app0JFIF")).getLength() > 0) {
              // Read DPI for JPEG File (if it contained needed Metadata)
              Element jfif = (Element) nodes.item(0);
              int dpiH = Integer.parseInt(jfif.getAttribute("Xdensity"));
              int dpiV = Integer.parseInt(jfif.getAttribute("Ydensity"));
              return new Dimension(dpiH, dpiV);
            } else if ((nodes = tree.getElementsByTagName("pHYs")).getLength() > 0) {
              // Read DPI for PNG File (if it contained needed Metadata)
              Element jfif = (Element) nodes.item(0);
              long dpiH = Math.round(Double.parseDouble(jfif.getAttribute("pixelsPerUnitXAxis")) / 39.3701);
              long dpiV = Math.round(Double.parseDouble(jfif.getAttribute("pixelsPerUnitYAxis")) / 39.3701);
              return new Dimension((int) dpiH, (int) dpiV);
            }
          } finally {
            reader.dispose();
          }
        }
      } finally {
        iis.close();
      }
      // Assume it's 72 DPI if there's no Metadata that specifies it
      return new Dimension(72, 72);
    }
  }

  static class CADReferenceImage extends CADRasterImage implements Serializable {
    private static final long serialVersionUID = 1171659976420013588L;

    CADReferenceImage () {
      engrave = false;
    }

    @Override
    BufferedImage getImage () {
      return img;
    }
  }

  static class CADRectangle extends CADShape implements Serializable {
    private static final long serialVersionUID = 5415641155292738232L;
    public double width, height, radius;

    /**
     * Default constructor is used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADRectangle () {
      // Set typical initial values, which user can edit before saving
      width = 1;
      height = 1;
    }

    CADRectangle (double xLoc, double yLoc, double width, double height, double radius, double rotation, boolean centered) {
      this.width = width;
      this.height = height;
      this.radius = radius;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"width|in", "height|in", "radius|in"};
    }

    @Override
    Shape buildShape () {
      if (radius > 0) {
        // Note: specifiy 2 x radius for arc height & width
        return new RoundRectangle2D.Double(-width / 2, -height / 2, width, height, radius * 2, radius * 2);
      } else {
        return new Rectangle2D.Double(-width / 2, -height / 2, width, height);
      }
    }
  }

  static class CADOval extends CADShape implements Serializable {
    private static final long serialVersionUID = 2518641166287730832L;
    public double width, height;

    /**
     * Default constructor used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADOval () {
      // Set typical initial values, which user can edit before saving
      width = .5;
      height = .5;
      centered = true;
    }

    CADOval (double xLoc, double yLoc, double width, double height, double rotation, boolean centered) {
      this.width = width;
      this.height = height;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"width|in", "height|in"};
    }

    @Override
    Shape buildShape () {
      return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
    }
  }

  static class CADPolygon extends CADShape implements Serializable {
    private static final long serialVersionUID = 973284612591842108L;
    public int    sides;
    public double diameter;

    /**
     * Default constructor used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADPolygon () {
      // Set typical initial values, which user can edit before saving
      diameter = 1.0;
      sides = 6;
      centered = true;
    }

    CADPolygon (double xLoc, double yLoc, double diameter, int sides, double rotation, boolean centered) {
      this.diameter = diameter;
      this.sides = sides;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"sides", "diameter|in"};
    }

    @Override
    Shape buildShape () {
      //return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
      Path2D.Double poly = new Path2D.Double();
      double radius = diameter / 2;
      double theta = 2 * Math.PI / sides;
      double angle = -Math.PI / 2;
      // Adjust angle, where needed, to draw shapes in familiar orientation
      if (sides %2 == 0) {
        angle += Math.toRadians(180.0 / sides);
      } else if (sides == 4) {
        angle += Math.toRadians(45.0);
      }
      boolean first = true;
      for (int i = 0; i < sides; ++i) {
        double x = Math.cos(angle) * radius;
        double y = Math.sin(angle) * radius;
        angle += theta;
        if (first) {
          poly.moveTo(x, y);
        } else {
          poly.lineTo(x, y);
        }
        first = false;
      }
      poly.closePath();
      return poly;
    }
  }

  static class CADBobbin extends CADShape implements Serializable {
    private static final long serialVersionUID = 8835012456785552127L;
    public double width, height, slotDepth, radius;

    /**
     * Default constructor is used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADBobbin () {
      // Set typical initial values, which user can edit before saving
      width = 3.75;
      height = 5.8;
      slotDepth = 1.8;
      radius = 0.125;
    }

    CADBobbin (double xLoc, double yLoc, double width, double height, double slotDepth, double radius, double rotation, boolean centered) {
      this.width = width;
      this.height = height;
      this.slotDepth = slotDepth;
      this.radius = radius;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"width|in", "height|in", "slotDepth|in", "radius|in"};
    }

    @Override
    Shape buildShape () {
      // Note: Draw shape as if centered on origin
      double xx = -width / 2;
      double yy = -height / 2;
      double tab = .75;
      Path2D.Double polygon = new Path2D.Double();
      if (radius > 0) {
        polygon.moveTo(xx, yy + radius);
        polygon.quadTo(xx, yy, xx + radius, yy);
        polygon.lineTo(xx + tab - radius, yy);
        polygon.quadTo(xx + tab, yy, xx + tab, yy + radius);
        polygon.lineTo(xx + tab, yy + slotDepth - radius);
        polygon.quadTo(xx + tab, yy + slotDepth, xx + tab + radius, yy + slotDepth);
        polygon.lineTo(xx + width - tab - radius, yy + slotDepth);
        polygon.quadTo(xx + width - tab, yy + slotDepth, xx + width - tab, yy + slotDepth - radius);
        polygon.lineTo(xx + width - tab, yy + radius);
        polygon.quadTo(xx + width - tab, yy, xx + width - tab + radius, yy);
        polygon.lineTo(xx + width - radius, yy);
        polygon.quadTo(xx + width, yy, xx + width, yy + radius);
        polygon.lineTo(xx + width, yy + height - radius);
        polygon.quadTo(xx + width, yy + height, xx + width - radius, yy + height);
        polygon.lineTo(xx + width - tab + radius, yy + height);
        polygon.quadTo(xx + width - tab, yy + height, xx + width - tab, yy + height - radius);
        polygon.lineTo(xx + width - tab, yy + height - slotDepth + radius);
        polygon.quadTo(xx + width - tab, yy + height - slotDepth, xx + width - tab - radius, yy + height - slotDepth);
        polygon.lineTo(xx + tab + radius, yy + height - slotDepth);
        polygon.quadTo(xx + tab, yy + height - slotDepth, xx + tab, yy + height - slotDepth + radius);
        polygon.lineTo(xx + tab, yy + height - radius);
        polygon.quadTo(xx + tab, yy + height, xx + tab - radius, yy + height);
        polygon.lineTo(xx + radius, yy + height);
        polygon.quadTo(xx, yy + height, xx, yy + height - radius);
      } else {
        polygon.moveTo(xx, yy);
        polygon.lineTo(xx + tab, yy);
        polygon.lineTo(xx + tab, yy + slotDepth);
        polygon.lineTo(xx + width - tab, yy + slotDepth);
        polygon.lineTo(xx + width - tab, yy);
        polygon.lineTo(xx + width, yy);
        polygon.lineTo(xx + width, yy + height);
        polygon.lineTo(xx + width - tab, yy + height);
        polygon.lineTo(xx + width - tab, yy + height - slotDepth);
        polygon.lineTo(xx + tab, yy + height - slotDepth);
        polygon.lineTo(xx + tab, yy + height);
        polygon.lineTo(xx, yy + height);
      }
      polygon.closePath();
      return polygon;
    }
  }

  static class CADNemaMotor extends CADShape implements Serializable {
    private static final long serialVersionUID = 2518641166287730832L;
    private static final double M2 = mmToInches(2);
    private static final double M2_5 = mmToInches(2.5);
    private static final double M3 = mmToInches(3);
    private static final double M4_5 = mmToInches(4.5);
    //                                           8      11     14     17     24
    private static double[]   ringDiameter =  {0.5906, 0.865, 0.865, 0.865, 1.5};
    private static double[]   holeSpacing =   {0.630,  0.91,  1.02,  1.22,  1.86};
    private static double[]   holeDiameter =  { M2,     M2_5,  M3,    M3,    M4_5};
    public String             type;   // Note: value is index into tables

    /**
     * Default constructor used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADNemaMotor () {
      // Set typical initial values, which user can edit before saving
      type = "1";
      centered = true;
    }

    CADNemaMotor (double xLoc, double yLoc, String type, double rotation, boolean centered) {
      this.type = type;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"type:Nema 8|0:Nema 11|1:Nema 14|2:Nema 17|3:Nema 23|4"};
    }

    @Override
    Shape buildShape () {
      int idx = Integer.parseInt(type);
      double diameter = ringDiameter[idx];
      double off = holeSpacing[idx] / 2;
      double hd = holeDiameter[idx];
      double hr = hd / 2;
      Area a1 = new Area(new Ellipse2D.Double(-diameter / 2, -diameter / 2, diameter, diameter));
      a1.add(new Area(new Ellipse2D.Double(-off - hr, -off - hr, hd, hd)));
      a1.add(new Area(new Ellipse2D.Double(+off - hr, -off - hr, hd, hd)));
      a1.add(new Area(new Ellipse2D.Double(-off - hr, +off - hr, hd, hd)));
      a1.add(new Area(new Ellipse2D.Double(+off - hr, +off - hr, hd, hd)));
      return a1;
    }
  }

  static class CADText extends CADShape implements Serializable {
    private static final long serialVersionUID = 4314642313295298841L;
    public String   text, fontName, fontStyle;
    public int      fontSize;
    private static Map<String,Integer>  styles = new HashMap<>();
    private static List<String> fonts = new ArrayList<>();

    static {
      // Define available font styles
      styles.put("plain", Font.PLAIN);
      styles.put("bold", Font.BOLD);
      styles.put("italic", Font.ITALIC);
      styles.put("bold-italic", Font.BOLD + Font.ITALIC);
      // Define available fonts
      String[] availFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
      Map<String,String> aMap = new HashMap<>();
      for (String tmp : availFonts) {
        aMap.put(tmp, tmp);
      }
      addIfAvailable(aMap, "");
      addIfAvailable(aMap, "American Typewriter");
      addIfAvailable(aMap, "Arial");
      addIfAvailable(aMap, "Arial Black");
      addIfAvailable(aMap, "Bauhaus 93");
      addIfAvailable(aMap, "Bradley Hand");
      addIfAvailable(aMap, "Brush Script");
      addIfAvailable(aMap, "Casual");
      addIfAvailable(aMap, "Chalkboard");
      addIfAvailable(aMap, "Comic Sans MS");
      addIfAvailable(aMap, "Edwardian Script ITC");
      addIfAvailable(aMap, "Freehand");
      addIfAvailable(aMap, "Giddyup Std");
      addIfAvailable(aMap, "Helvetica");
      addIfAvailable(aMap, "Hobo Std");
      addIfAvailable(aMap, "Impact");
      addIfAvailable(aMap, "Marker Felt");
      addIfAvailable(aMap, "OCR A Std");
      addIfAvailable(aMap, "Times New Roman");
      addIfAvailable(aMap, "Stencil");
    }

    private static void addIfAvailable (Map avail, String font) {
      if (avail.containsKey(font)) {
        fonts.add(font);
      }
    }

    /**
     * Default constructor is used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADText () {
      // Set typical initial values, which user can edit before saving
      text = "Test";
      fontName = "Helvetica";
      fontStyle = "plain";
      fontSize = 24;
      engrave = true;
    }

    CADText (double xLoc, double yLoc, String text, String fontName, String fontStyle, int fontSize,
             double rotation, boolean centered) {
      this.text = text;
      this.fontName = fontName;
      this.fontStyle = fontStyle;
      this.fontSize = fontSize;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      StringBuilder fontNames = new StringBuilder("fontName");
      for (String font : fonts) {
        fontNames.append(":");
        fontNames.append(font);
      }
      return new String[]{"text", fontNames.toString(), "fontStyle:plain:bold:italic:bold-italic", "fontSize|pts"};
    }

    @Override
    Shape buildShape () {
      // Code from: http://www.java2s.com/Tutorial/Java/0261__2D-Graphics/GenerateShapeFromText.htm
      BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = img.createGraphics();
      Font font = new Font(fontName, styles.get(fontStyle), fontSize);
      HashMap<TextAttribute, Object> attrs = new HashMap<>();
      attrs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
      attrs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
      font = font.deriveFont(attrs);
      g2.setFont(font);
      try {
        GlyphVector vect = font.createGlyphVector(g2.getFontRenderContext(), text);
        AffineTransform at = new AffineTransform();
        at.scale(1 / 72.0, 1 / 72.0);
        Shape text = at.createTransformedShape(vect.getOutline());
        Rectangle2D bounds = text.getBounds2D();
        at = new AffineTransform();
        at.translate(-bounds.getWidth() / 2, bounds.getHeight() / 2);
        return at.createTransformedShape(text);
      } finally {
        g2.dispose();
      }
    }
  }

  static class CADShapeSpline extends LaserCut.CADShape implements Serializable {
    private static final long serialVersionUID = 1175193935200692376L;
    private List<Point2D.Double>  points = new ArrayList<>();
    private Point2D.Double        movePoint;
    private boolean               closePath;
    private Path2D.Double         path = new Path2D.Double();

    CADShapeSpline () {
      centered = true;
    }

    @Override
    Point2D.Double setPosition (Point2D.Double newLoc) {
      return super.setPosition(newLoc);
    }

    @Override
    boolean selectMovePoint (Point2D.Double point, Point2D.Double gPoint) {
      // See if we clicked on an existing Catmull-Rom Control Point other than origin
      Point2D.Double mse = new Point2D.Double(point.x - xLoc, point.y - yLoc);
      for (int ii = 0; ii < points.size(); ii++) {
        Point2D.Double cp = points.get(ii);
        Point2D.Double np = rotatePoint(cp, rotation);
        double dist = mse.distance(np.x, np.y) * LaserCut.SCREEN_PPI;
        if (dist < 5) {
          if (ii == 0 && !closePath) {
            closePath = true;
            updatePath();
          } else {
            movePoint = cp;
          }
          return true;
        }
      }
      if (!closePath) {
        points.add(movePoint = new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc));
        updatePath();
        return true;
      }
      return false;
    }

    @Override
    boolean doMovePoints (Point2D.Double point) {
      Point2D.Double mse = rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
      if (movePoint != null) {
        double dx = mse.x - movePoint.x;
        double dy = mse.y - movePoint.y;
        movePoint.x += dx;
        movePoint.y += dy;
        updatePath();
        return true;
      }
      return false;
    }

    @Override
    void cancelMove () {
      movePoint = null;
    }

    @Override
    Shape getShape () {
      return path;
    }

    private void updatePath () {
      if (closePath) {
        path = convert(points.toArray(new Point2D.Double[points.size()]), true);
      } else {
        Point2D.Double[] pnts = points.toArray(new Point2D.Double[points.size() + 1]);
        // Duplicate last point so we can draw a curve through all points in the path
        pnts[pnts.length -1 ] = pnts[pnts.length - 2];
        path = convert(pnts, false);
      }
    }

    private Point2D.Double rotatePoint (Point2D.Double point, double angle) {
      AffineTransform center = AffineTransform.getRotateInstance(Math.toRadians(angle), 0, 0);
      Point2D.Double np = new Point2D.Double();
      center.transform(point, np);
      return np;
    }

    private Path2D.Double convert (Point2D.Double[] points, boolean close) {
      Path2D.Double path = new Path2D.Double();
      path.moveTo(points[0].x, points[0].y);
      int end = close ? points.length + 1 : points.length - 1;
      for (int ii = 0;  ii < end - 1; ii++) {
        Point2D.Double p0, p1, p2, p3;
        if (close) {
          int idx0 = Math.floorMod(ii - 1, points.length);
          int idx1 = Math.floorMod(idx0 + 1, points.length);
          int idx2 = Math.floorMod(idx1 + 1, points.length);
          int idx3 = Math.floorMod(idx2 + 1, points.length);
          p0 = new Point2D.Double(points[idx0].x, points[idx0].y);
          p1 = new Point2D.Double(points[idx1].x, points[idx1].y);
          p2 = new Point2D.Double(points[idx2].x, points[idx2].y);
          p3 = new Point2D.Double(points[idx3].x, points[idx3].y);
        } else {
          p0 = new Point2D.Double(points[Math.max(ii - 1, 0)].x, points[Math.max(ii - 1, 0)].y);
          p1 = new Point2D.Double(points[ii].x, points[ii].y);
          p2 = new Point2D.Double(points[ii + 1].x, points[ii + 1].y);
          p3 = new Point2D.Double(points[Math.min(ii + 2, points.length - 1)].x, points[Math.min(ii + 2, points.length - 1)].y);
        }
        // Catmull-Rom to Cubic Bezier conversion matrix
        //    0       1       0       0
        //  -1/6      1      1/6      0
        //    0      1/6      1     -1/6
        //    0       0       1       0
        double x1 = (-p0.x + 6 * p1.x + p2.x) / 6;  // First control point
        double y1 = (-p0.y + 6 * p1.y + p2.y) / 6;
        double x2 = ( p1.x + 6 * p2.x - p3.x) / 6;  // Second control point
        double y2 = ( p1.y + 6 * p2.y - p3.y) / 6;
        double x3 = p2.x;                           // End point
        double y3 = p2.y;
        path.curveTo(x1, y1, x2, y2, x3, y3);
      }
      if (close) {
        path.closePath();
      }
      return path;
    }

    @Override
    void draw (Graphics2D g2, double zoom) {
      // Draw all Catmull-Rom Control Points
      g2.setColor(isSelected ? Color.blue : Color.lightGray);
      for (Point2D.Double cp : points) {
        Point2D.Double np = rotatePoint(cp, rotation);
        double mx = (xLoc + np.x) * zoom;
        double my = (yLoc + np.y) * zoom;
        double mWid = 2 * zoom / LaserCut.SCREEN_PPI;
        g2.fill(new Rectangle.Double(mx - mWid, my - mWid, mWid * 2, mWid * 2));
      }
      super.draw(g2, zoom);
    }
  }
  static class CADGear extends CADShape implements Serializable {
    private static final long serialVersionUID = 2334548672295293845L;
    public double module, pressAngle, profileShift, holeSize, diameter;
    public int numTeeth, numPoints;

    /**
     * Default constructor is used to instantiate subclasses in "Shapes" Menu
     */
    @SuppressWarnings("unused")
    CADGear () {
      // Set typical initial values, which user can edit before saving
      module = .1;
      numTeeth = 15;
      numPoints = 10;
      pressAngle = 20;
      profileShift = .25;
      holeSize = .125;
      diameter = numTeeth * module;
    }

    CADGear (double xLoc, double yLoc, double module, int numTeeth, int numPoints, double pressAngle, double profileShift,
             double rotation, double holeSize) {
      setLocationAndOrientation(xLoc, yLoc, rotation, true);
      this.module = module;
      this.numTeeth = numTeeth;
      this.numPoints = numPoints;
      this.pressAngle = pressAngle;
      this.profileShift = profileShift;
      this.holeSize = holeSize;
      diameter = numTeeth * module;
    }

    @Override
    String[] getParameterNames () {
      return new String[]{"module{module = diameter / numTeeth}", "numTeeth", "numPoints", "pressAngle|deg", "profileShift",
                          "*diameter|in{diameter = numTeeth * module}", "holeSize|in"};
    }

    @Override
    Shape buildShape () {
      return GearGen.generateGear(module, numTeeth, numPoints, pressAngle, profileShift, holeSize);
    }

    @Override
    void draw (Graphics2D g2, double zoom) {
      super.draw(g2, zoom);
      // Draw dashed line in magenta to show gear diameter
      g2.setColor(Color.MAGENTA);
      BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {10.0f}, 0.0f);
      g2.setStroke(dashed);
      double diameter = module * numTeeth;
      if (centered) {
        g2.draw(new Ellipse2D.Double((xLoc - diameter / 2) * zoom, (yLoc - diameter / 2) * zoom, diameter * zoom, diameter * zoom));
      } else {
        g2.draw(new Ellipse2D.Double(xLoc * zoom, yLoc * zoom, diameter * zoom, diameter * zoom));
      }
    }
  }

  static class CADShapeGroup implements Serializable {
    private static final long serialVersionUID = 3210128656295452345L;
    private ArrayList<CADShape> shapesInGroup = new ArrayList<>();

    void addToGroup (CADShape shape) {
      CADShapeGroup old = shape.getGroup();
      if (old != null) {
        old.removeFromGroup(shape);
      }
      if (!shapesInGroup.contains(shape)) {
        shapesInGroup.add(shape);
        shape.setGroup(this);
      }
    }

    CADShape removeFromGroup (CADShape shape) {
      shapesInGroup.remove(shape);
      shape.setGroup(null);
      if (shapesInGroup.size() == 1) {
        CADShape newSelected = shapesInGroup.get(0);
        newSelected.setGroup(null);
        shapesInGroup.clear();
        return newSelected;
      }
      return shapesInGroup.get(0);
    }

    void removeAllFromGroup () {
      for (CADShape shape : shapesInGroup) {
        shape.setGroup(null);
      }
      shapesInGroup.clear();
    }

    boolean contains (CADShape shape) {
      return shapesInGroup.contains(shape);
    }

    boolean isGroupSelected () {
      for (CADShape shape : shapesInGroup) {
        if (shape.isSelected())
          return true;
      }
      return false;
    }

    ArrayList<CADShape> getGroupList () {
      return shapesInGroup;
    }
  }

  interface ShapeSelectListener {
    void shapeSelected (CADShape shape, boolean selected);
  }

  interface ActionUndoListener {
    void undoEnable (boolean enable);
  }

  interface ActionRedoListener {
    void redoEnable (boolean enable);
  }

  public class DrawSurface extends JPanel {
    private Dimension                       workSize;
    private List<CADShape>                  shapes = new ArrayList<>(), shapesToPlace;
    private CADShape                        selected, dragged, shapeToPlace;
    private double                          gridSpacing = prefs.getDouble("gridSpacing", 0);
    private int                             gridMajor = prefs.getInt("gridMajor", 0);
    private double                          zoomFactor = 1;
    private Point2D.Double                  scrollPoint, measure1, measure2;
    private List<ShapeSelectListener>       selectListerners = new ArrayList<>();
    private List<ActionUndoListener>        undoListerners = new ArrayList<>();
    private List<ActionRedoListener>        redoListerners = new ArrayList<>();
    private LinkedList<byte[]>              undoStack = new LinkedList<>();
    private LinkedList<byte[]>              redoStack = new LinkedList<>();
    private boolean                         pushedToStack, showMeasure;

    DrawSurface () {
      super(true);
      // Set JPanel size for Zing's maximum work area, or other, if resized by user
      setPreferredSize(getSize());
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed (MouseEvent ev) {
          requestFocus();
          Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
          if (shapeToPlace != null || shapesToPlace != null) {
            if (gridSpacing > 0) {
              newLoc = toGrid(newLoc);
            }
            if (shapeToPlace != null) {
              // Set location of shape to location user clicked
              shapeToPlace.setPosition(newLoc);
              addShape(shapeToPlace);
              setSelected(shapeToPlace);
              shapeToPlace = null;
            } else {
              importShapes(shapesToPlace, newLoc);
              shapesToPlace = null;
            }
          } else if (ev.isControlDown()) {
            // Select shape and then do CTRL-Click on second shape to measure distance from origin to origin
            if (selected != null) {
              for (CADShape shape : shapes) {
                // Check for mouse pointing to shape
                if (shape.isPositionClicked(newLoc) || shape.isShapeClicked(newLoc)) {
                  double dx = shape.xLoc - selected.xLoc;
                  double dy = shape.yLoc - selected.yLoc;
                  itemInfo.setText(" dx: " + LaserCut.df.format(dx) + " in, dy: " + LaserCut.df.format(dy) + " in (" +
                      LaserCut.df.format(inchesToMM(dx)) + " mm, " + LaserCut.df.format(inchesToMM(dy)) + " mm)");
                  measure1 = new Point2D.Double(selected.xLoc * SCREEN_PPI, selected.yLoc * SCREEN_PPI);
                  measure2 = new Point2D.Double(shape.xLoc * SCREEN_PPI, shape.yLoc * SCREEN_PPI);
                  showMeasure = true;
                  break;
                }
              }
            }
          } else if (ev.isShiftDown()) {
            if (selected != null) {
              /*
                Cases:
                  shape to add is in an existing group
                    remove shape from old group
                    delete old group if this leaves it with only one member
                    add shape to selected's group
                  shape to add is not in an existing group
                    add shape to selected's group
                  shape is selected shape
                    remove shape from its group
                    make one remaining member of group the new selected shape, or
                    delete group if this leaves it with only one member
               */
              CADShapeGroup selGroup = selected.getGroup();
              for (CADShape shape : shapes) {
                if (shape.isShapeClicked(newLoc)) {
                  pushToUndoStack();
                  CADShapeGroup shapeGroup = shape.getGroup();
                  if (shape == selected) {
                    // clicked shape is selected shape, so remove from group and
                    // assign remaining shape in group as selected shape
                    CADShape newSel;
                    if (selGroup != null) {
                      newSel = selGroup.removeFromGroup(shape);
                      setSelected(newSel);
                    }
                  } else {
                    // clicked shape is not selected shape
                    if (selGroup == null) {
                      // No group, so create one and add both shapes to it
                      selGroup = new CADShapeGroup();
                      selGroup.addToGroup(selected);
                      selGroup.addToGroup(shape);
                      setSelected(selected);
                      break;
                    } else {
                      if (selGroup.contains(shape)) {
                        // clicked shape in already in group, so remove it
                        selGroup.removeFromGroup(shape);
                        break;
                      } else {
                        if (shapeGroup != null) {
                          // clicked was already in another group, so remove it from it
                          shapeGroup.removeFromGroup(shape);
                        }
                        // Add clicked shape to selected group
                        selGroup.addToGroup(shape);
                        break;
                      }
                    }
                  }
                }
              }
            }
          } else {
            if (selected != null) {
              if (selected.isPositionClicked(newLoc)) {
                dragged = selected;
                showMeasure = false;
                return;
              }
              for (CADShape shape : shapes) {
                // Check for click and drag of shape's position
                if (shape.isPositionClicked(newLoc)) {
                  dragged = shape;
                  setSelected(shape);
                  itemInfo.setText("xLoc: " + dragged.xLoc + ", yLoc: " + dragged.yLoc);
                  showMeasure = false;
                  return;
                }
              }
            }
            boolean processed = false;
            for (CADShape shape : shapes) {
              // Check for selection or deselection of shapes
              if (shape.isShapeClicked(newLoc) ||
                  (shape instanceof CADReference && shape.isPositionClicked(newLoc)) ) {
                pushToUndoStack();
                setSelected(shape);
                itemInfo.setText(shape.getInfo());
                showMeasure = false;
                processed = true;
                break;
              }
            }
            if (selected != null && selected.selectMovePoint(newLoc, toGrid(newLoc))) {
              dragged = selected;
              repaint();
              return;
            }
            if (!processed) {
              pushToUndoStack();
              setSelected(null);
              itemInfo.setText("");
              showMeasure = false;
              scrollPoint = newLoc;
              LaserCut.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
          }
          repaint();
        }

        @Override
        public void mouseReleased (MouseEvent ev) {
          dragged = null;
          scrollPoint = null;
          LaserCut.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          pushedToStack = false;
          if (selected != null) {
            selected.cancelMove();
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged (MouseEvent ev) {
          if (dragged != null) {
            if (!pushedToStack) {
              pushedToStack = true;
              pushToUndoStack();
            }
            Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
            if (gridSpacing > 0) {
              newLoc = toGrid(newLoc);
            }
            if (!dragged.doMovePoints(newLoc)) {
              Point2D.Double delta = dragged.setPosition(newLoc);
              itemInfo.setText("xLoc: " + dragged.xLoc + ", yLoc: " + dragged.yLoc);
              CADShapeGroup group = dragged.getGroup();
              if (group != null) {
                for (CADShape shape : group.shapesInGroup) {
                  if (shape != dragged) {
                    shape.movePosition(delta);
                  }
                }
              }
            }
            repaint();
          } else if (scrollPoint != null) {
            // Drag the mouse to move the JScrollPane
            double deltaX = scrollPoint.x * getScreenScale() - ev.getX();
            double deltaY = scrollPoint.y * getScreenScale() - ev.getY();
            Rectangle view = getVisibleRect();
            view.x += (int) deltaX;
            view.y += (int) deltaY;
            scrollRectToVisible(view);
            repaint();
          }
        }
      });
      // Track JPanel resize events and save in prefs
      addComponentListener(new ComponentAdapter() {
        public void componentResized (ComponentEvent ev)  {
          Rectangle bounds = ev.getComponent().getBounds();
          prefs.putInt("window.width", bounds.width);
          prefs.putInt("window.height", bounds.height);
        }
      });
    }

    void setSurfaceSize (Dimension size) {
      workSize = size;
      setSize(size);
      setPreferredSize(size);
      setMaximumSize(size);
      repaint();
    }

    private double getScreenScale () {
      return SCREEN_PPI * zoomFactor;
    }

    void setZoomFactor (double zoom) {
      if (zoom != zoomFactor) {
        zoomFactor = zoom;
        Dimension zoomSize = new Dimension((int) (workSize.getWidth() * zoomFactor), (int) (workSize.getHeight() * zoomFactor));
        setSize(zoomSize);
        setPreferredSize(zoomSize);
        repaint();
      }
    }

    double getZoomFactor () {
      return zoomFactor;
    }

    void setGridSize (double gridSize, int majorStep) {
      gridSpacing = gridSize;
      gridMajor = majorStep;
      prefs.putDouble("gridSpacing", gridSize);
      prefs.putInt("gridMajor", gridMajor);
      repaint();
    }

    double getGridSize () {
      return gridSpacing;
    }

    private double toGrid (double coord) {
      // From: https://stackoverflow.com/a/5421681 (answer #2)
      return gridSpacing * Math.floor((coord / gridSpacing) + 0.5);
    }

    private Point2D.Double toGrid (Point2D.Double loc) {
      return new Point2D.Double(toGrid(loc.x), toGrid(loc.y));
    }

    void addUndoListener (ActionUndoListener lst) {
      undoListerners.add(lst);
    }

    void addRedoListener (ActionRedoListener lst) {
      redoListerners.add(lst);
    }

    private byte[] shapesListToBytes () throws IOException {
      // Use ObjectOutputStream to make a deep copy
      ByteArrayOutputStream bOut = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bOut);
      out.writeObject(selected);
      out.writeObject(shapes);
      out.close();
      bOut.close();
      return bOut.toByteArray();
    }

    private ArrayList<CADShape> bytesToShapeList(byte[] bytes) throws Exception {
      ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
      ObjectInputStream in = new ObjectInputStream(bIn);
      CADShape sel = (CADShape) in.readObject();
      if (sel != null) {
        setSelected(sel);
      }
      return (ArrayList<CADShape>) in.readObject();
    }

    void pushToUndoStack () {
      try {
        byte[] bytes = shapesListToBytes();
        undoStack.addFirst(bytes);
        redoStack.clear();
        if (undoStack.size() > 200) {
          undoStack.removeLast();
        }
        for (ActionUndoListener lst : undoListerners) {
          lst.undoEnable(undoStack.size() > 0);
        }
        for (ActionRedoListener lst : redoListerners) {
          lst.redoEnable(redoStack.size() > 0);
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }

    void popFromUndoStack () {
      if (undoStack.size() > 0) {
        try {
          redoStack.addFirst(shapesListToBytes());
          shapes = bytesToShapeList(undoStack.pollFirst());
          for (ActionUndoListener lst : undoListerners) {
            lst.undoEnable(undoStack.size() > 0);
          }
          for (ActionRedoListener lst : redoListerners) {
            lst.redoEnable(redoStack.size() > 0);
          }
          repaint();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }

    void popFromRedoStack () {
      try {
        undoStack.addFirst(shapesListToBytes());
        shapes = bytesToShapeList(redoStack.pollFirst());
        for (ActionUndoListener lst : undoListerners) {
          lst.undoEnable(undoStack.size() > 0);
        }
        for (ActionRedoListener lst : redoListerners) {
          lst.redoEnable(redoStack.size() > 0);
        }
        repaint();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    List<CADShape> getDesign () {
      return shapes;
    }

    ArrayList<CADShape> getSelectedAsDesign () {
      ArrayList<CADShape> design = new ArrayList<>();
      design.add(selected);
      CADShapeGroup grp = selected.getGroup();
      if (grp != null) {
        for (CADShape shape : grp.getGroupList()) {
          if (shape != selected) {
            design.add(shape);
          }
        }
      }
      return design;
    }

    /**
     * Uses Serilazation to convert design to byte artay and then uses this to compute a checkum
     * @return
     */
    long getDesignChecksum () {
      CRC32 crc = new CRC32();
      try {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ObjectOutputStream oOut = new ObjectOutputStream(bOut);
        oOut.writeObject(shapes);
        crc.update(bOut.toByteArray());
        oOut.close();
        bOut.close();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      return crc.getValue();
    }

    void setDesign (List<CADShape> shapes) {
      this.shapes = shapes;
      repaint();
    }

    void addShape (CADShape shape) {
      pushToUndoStack();
      shapes.add(shape);
      repaint();
    }

    void importShapes (List<CADShape> addShapes, Point2D.Double newLoc) {
      pushToUndoStack();
      // Determine upper left offset to set of import shapes
      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      for (CADShape shape : addShapes) {
        Rectangle2D bounds = shape.getBounds();
        minX = Math.min(minX, shape.xLoc + bounds.getX());
        minY = Math.min(minY, shape.yLoc + bounds.getY());
      }
      // Place all imported shapes so upper left position of set is now where used clicked
      for (CADShape shape : addShapes) {
        shape.xLoc = shape.xLoc - minX + newLoc.x;
        shape.yLoc = shape.yLoc - minY + newLoc.y;
        shapes.add(shape);
      }
    }

    void placeShape (CADShape shape) {
      shapeToPlace = shape;
      itemInfo.setText("Click to place Shape");
    }

    void placeShapes (List<CADShape> shapes) {
      shapesToPlace = shapes;
      itemInfo.setText("Click to place imported Shapes");
    }

    void addShapeNoPush (CADShape shape) {
      shapes.add(shape);
    }

    boolean hasData () {
      return shapes.size() > 0;
    }

    void clear () {
      pushToUndoStack();
      shapes.clear();
      repaint();
    }

    void roundSelected (double radius) {
      pushToUndoStack();
      Shape oldShape = selected.buildShape();
      CADShape tmp = new CADShape(CornerFinder.roundCorners(oldShape, radius), selected.xLoc, selected.yLoc, 0, selected.centered);
      shapes.remove(selected);
      shapes.add(tmp);
      setSelected(tmp);
      repaint();
    }

    void addOrSubtractSelectedShapes (boolean add) {
      CADShapeGroup group = selected.getGroup();
      if (group != null) {
        pushToUndoStack();
        Shape base = selected.getLocallyTransformedShape();
        Area newShape = new Area(base);
        shapes.remove(selected);
        for (CADShape gItem : group.getGroupList()) {
          if (gItem != selected) {
            Shape shape = gItem.getLocallyTransformedShape();
            AffineTransform at = AffineTransform.getTranslateInstance(gItem.xLoc - selected.xLoc, gItem.yLoc - selected.yLoc);
            if (add) {
              newShape.add(new Area(at.createTransformedShape(shape)));
            } else {
              newShape.subtract(new Area(at.createTransformedShape(shape)));
            }
            shapes.remove(gItem);
          }
        }
        if (selected.centered) {
          CADShape tmp = new CADShape(newShape, selected.xLoc, selected.yLoc, 0, true);
          shapes.add(tmp);
          setSelected(tmp);
        } else {
          Rectangle2D bounds = newShape.getBounds2D();
          AffineTransform at = AffineTransform.getTranslateInstance(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
          newShape.transform(at);
          CADShape tmp = new CADShape(newShape, selected.xLoc, selected.yLoc, 0, selected.centered);
          shapes.add(tmp);
          setSelected(tmp);
        }
        repaint();
      }
    }

    void alignSelectedShapes (boolean alignX, boolean alignY) {
      pushToUndoStack();
      CADShapeGroup group = selected.getGroup();
      if (group != null) {
        for (CADShape gItem : group.getGroupList()) {
          if (gItem != selected) {
            if (alignX) {
              gItem.xLoc = selected.xLoc;
            }
            if (alignY) {
              gItem.yLoc = selected.yLoc;
            }
          }
        }
        repaint();
      }
    }

    void moveSelected () {
      ParameterDialog.ParmItem[] parmSet = new ParameterDialog.ParmItem[2];
      parmSet[0] = new ParameterDialog.ParmItem("xOff|in", 0d);
      parmSet[1] = new ParameterDialog.ParmItem("yOff|in", 0d);
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, surface.getParent())) {
        double xOff = 0;
        double yOff = 0;
        for (ParameterDialog.ParmItem parm : parmSet) {
          try {
              if ("xOff".equals(parm.name)) {
                xOff = (Double) parm.value;
              } else if ("yOff".equals(parm.name)) {
                yOff = (Double) parm.value;
              }
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        pushToUndoStack();
        selected.xLoc += xOff;
        selected.yLoc += yOff;
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          for (CADShape gItem : group.getGroupList()) {
            if (gItem != selected) {
              gItem.xLoc += xOff;
              gItem.yLoc += yOff;
            }
          }
        }
        repaint();
      }
    }

    void rotateGroupAroundSelected () {
      ParameterDialog.ParmItem[] parmSet = {new ParameterDialog.ParmItem("angle|deg", 0d), new ParameterDialog.ParmItem("rotateSelected", true)};
      if (ParameterDialog.showSaveCancelParameterDialog(parmSet, surface.getParent())) {
        double angle = 0;
        boolean rotateSelected = true;
        for (ParameterDialog.ParmItem parm : parmSet) {
          try {
            if ("angle".equals(parm.name)) {
              angle = (Double) parm.value;
            }
            if ("rotateSelected".equals(parm.name)) {
              rotateSelected = (Boolean) parm.value;
            }
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        pushToUndoStack();
        AffineTransform center = AffineTransform.getRotateInstance(Math.toRadians(angle), selected.xLoc, selected.yLoc);
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          for (CADShape gItem : group.getGroupList()) {
            if (gItem != selected || rotateSelected) {
              Point2D.Double pt = new Point2D.Double(gItem.xLoc, gItem.yLoc);
              center.transform(pt, pt);
              gItem.xLoc = pt.x;
              gItem.yLoc = pt.y;
              gItem.rotation += angle;
            }
          }
        }
        repaint();
      }
    }

    void addSelectListener (ShapeSelectListener listener) {
      selectListerners.add(listener);
    }

    void removeSelected () {
      if (selected != null) {
        pushToUndoStack();
        shapes.remove(selected);
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          shapes.removeAll(group.getGroupList());
        }
        for (ShapeSelectListener listener : selectListerners) {
          listener.shapeSelected(selected, false);
        }
        selected = null;
        repaint();
      }
    }

    void duplicateSelected () {
      if (selected != null) {
        pushToUndoStack();
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          boolean first = true;
          CADShapeGroup newGroup = new CADShapeGroup();
          for (CADShape shape : group.getGroupList()) {
            CADShape dup = shape.copy();
            if (first) {
              setSelected(dup);
              first = false;
            }
            newGroup.addToGroup(dup);
            dup.xLoc += .1;
            dup.yLoc += .1;
            shapes.add(dup);
          }
        } else {
          CADShape dup = selected.copy();
          dup.xLoc += .1;
          dup.yLoc += .1;
          shapes.add(dup);
          setSelected(dup);
        }
        repaint();
      }
    }

    CADShape getSelected () {
      return selected;
    }

    void setSelected (CADShape newSelected) {
      if (selected != null) {
        selected.setSelected(false);
      }
      selected = newSelected;
      if (selected != null) {
        selected.setSelected(true);
      }
      for (ShapeSelectListener listener : selectListerners) {
        listener.shapeSelected(selected, true);
      }
      repaint();
    }

    void unGroupSelected () {
      if (selected != null) {
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          group.removeAllFromGroup();
          repaint();
        }
      }
    }

    /**
     * Called by "Send to Zing" and "Send to Mini Laser" menu options.  Culls out shapes that are not processed, such as
     * shapes that implement CADNoDraw interface and, if cutItems is true, also culls shapes with engrave set to true; or,
     * if cutItems is false, culls shapes where engrave is set to false.  Then, code reorders shapes in list using a crude
     * type of "travelling salesman" algorithm that tries to minimize laser head seek by successively finding the next
     * closest shape.
     * @param cutItems if true, only process shapes with 'engrave' set to false.
     * @return List of culled and reordered shapes CADShape objects cut
     */
    ArrayList<CADShape> selectLaserItems (boolean cutItems) {
      // Cull out items that will not be cut or that don't match cutItems
      ArrayList<CADShape> cullShapes = new ArrayList<>();
      for (CADShape shape : surface.getDesign()) {
        if (!(shape instanceof CADNoDraw) && shape.engrave != cutItems) {
          cullShapes.add(shape);
        }
      }
      // Reorder shapes by successively finding the next closest point starting from upper left
      ArrayList<CADShape> newShapes = new ArrayList<>();
      double lastX = 0, lastY = 0;
      while (cullShapes.size() > 0) {
        double dist = Double.MAX_VALUE;
        CADShape sel = null;
        for (CADShape shape : cullShapes) {
          double tDist = shape.distanceTo(lastX, lastY);
          if (tDist < dist) {
            sel = shape;
            dist = tDist;
          }
        }
        lastX = sel.xLoc;
        lastY = sel.yLoc;
        newShapes.add(sel);
        cullShapes.remove(sel);
      }
      return newShapes;
    }

    public void paint (Graphics g) {
      Dimension d = getSize();
      Graphics2D g2 = (Graphics2D) g;
      g2.setBackground(Color.white);
      g2.clearRect(0, 0, d.width, d.height);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (gridSpacing > 0) {
        Stroke bold = new BasicStroke(2.5f);
        Stroke mild = new BasicStroke(1.0f);
        g2.setColor(new Color(224, 222, 254));
        int mCnt = 0;
        for (double xx = 0; xx <= workSize.width / zoomFactor; xx += gridSpacing) {
          double col = xx * getScreenScale();
          g2.setStroke(gridMajor > 0 && (mCnt++ % gridMajor) == 0 ? bold :mild);
          g2.draw(new Line2D.Double(col, 0, col, workSize.height * zoomFactor));
        }
        mCnt = 0;
        for (double yy = 0; yy <= workSize.height / zoomFactor; yy += gridSpacing) {
          double row = yy  * getScreenScale();
          g2.setStroke(gridMajor > 0 && (mCnt++ % gridMajor) == 0 ? bold :mild);
          g2.draw(new Line2D.Double(0, row, workSize.width * zoomFactor, row));
        }
      }
      new ArrayList<>(shapes).forEach(shape -> shape.draw(g2, getScreenScale()));
      if (showMeasure) {
        g2.setColor(Color.gray);
        g2.setStroke(new BasicStroke(0.5f));
        double extend = 10;
        double minX = Math.min(measure1.x, measure2.x);
        double minY = Math.min(measure1.y, measure2.y);
        g2.draw(new Line2D.Double(measure1.x - extend, minY, measure2.x + extend, minY));
        g2.draw(new Line2D.Double(minX, measure1.y - extend, minX, measure2.y + extend));
        g2.setColor(new Color(127, 100, 49));
        g2.setStroke(new BasicStroke(0.8f));
        double maxX = Math.max(measure1.x, measure2.x);
        double maxY = Math.max(measure1.y, measure2.y);
        g2.draw(new Line2D.Double(measure1.x, maxY, measure2.x, maxY));
        g2.draw(new Line2D.Double(maxX, measure1.y, maxX, measure2.y));
        g2.fill(LineWithArrow.getArrow(measure1.x, maxY, measure2.x, maxY, false));
        g2.fill(LineWithArrow.getArrow(measure1.x, maxY, measure2.x, maxY, true));
        g2.fill(LineWithArrow.getArrow(maxX, measure1.y, maxX, measure2.y, false));
        g2.fill(LineWithArrow.getArrow(maxX, measure1.y, maxX, measure2.y, true));
      }
    }

    void writePDF (File file) throws Exception {
      FileOutputStream output = new FileOutputStream(file);
      double scale = 72;
      PDDocument doc = new PDDocument();
      PDDocumentInformation docInfo = doc.getDocumentInformation();
      docInfo.setCreator("Wayne Holder's LaserCut");
      docInfo.setProducer("Apache PDFBox " + org.apache.pdfbox.util.Version.getVersion());
      docInfo.setCreationDate(Calendar.getInstance());
      double wid = workSize.width / SCREEN_PPI * 72;
      double hyt = workSize.height / SCREEN_PPI * 72;
      PDPage pdpage = new PDPage(new PDRectangle((float) wid, (float) hyt));
      doc.addPage(pdpage);
      PDPageContentStream stream = new PDPageContentStream(doc, pdpage, PDPageContentStream.AppendMode.APPEND, false);
      // Flip Y axis so origin is at upper left
      Matrix flipY = new Matrix();
      flipY.translate(0, pdpage.getBBox().getHeight());
      flipY.scale(1, -1);
      stream.transform(flipY);
      AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
      for (CADShape item : getDesign()) {
        if (item instanceof CADReference)
          continue;
        if (item.engrave) {
          stream.setStrokingColor(Color.lightGray);
          stream.setLineWidth(1.0f);
        } else {
          stream.setStrokingColor(Color.black);
          stream.setLineWidth(0.001f);
        }
        Shape shape = item.getScreenTranslatedShape();
        // Use PathIterator to generate sequence of line or curve segments
        PathIterator pi = shape.getPathIterator(at);
        while (!pi.isDone()) {
          float[] coords = new float[6];      // p1.x, p1.y, p2.x, p2.y, p3.x, p3.y
          int type = pi.currentSegment(coords);
          switch (type) {
            case PathIterator.SEG_MOVETO:   // 0
              // Move to start of a line, or bezier curve segment
              stream.moveTo(coords[0], coords[1]);
              break;
            case PathIterator.SEG_LINETO:   // 1
              // Draw line from previous point to new point
              stream.lineTo(coords[0], coords[1]);
              break;
            case PathIterator.SEG_QUADTO:   // 2
              // Write 3 point, quadratic bezier curve from previous point to new point using one control point
              stream.curveTo2(coords[0], coords[1], coords[2], coords[3]);
              break;
            case PathIterator.SEG_CUBICTO:  // 3
              // Write 4 point, cubic bezier curve from previous point to new point using two control points
              stream.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
              break;
            case PathIterator.SEG_CLOSE:    // 4
              // Close and write out the current curve
              stream.closeAndStroke();
              break;
            default:
              System.out.println("Error, Unknown PathIterator Type: " + type);
              break;
          }
          pi.next();
        }
      }
      stream.close();
      doc.save(output);
      doc.close();
      output.close();
    }
  }

  public static void main (String[] s) {
    SwingUtilities.invokeLater(LaserCut::new);
  }
}