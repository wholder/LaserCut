import jssc.SerialNativeInterface;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
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

  Note: unable to use CMD-A, CMD-C, CMD-H, CMD-Q as shortcut keys
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
  static final DecimalFormat    df = new DecimalFormat("#0.0###");
  private static int            cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
  transient Preferences         prefs = Preferences.userRoot().node(this.getClass().getName());
  DrawSurface                   surface;
  private JScrollPane           scrollPane;
  private JTextField            itemInfo = new JTextField();
  private JMenuItem             gerberZip;
  private int                   pxDpi = prefs.getInt("svg.pxDpi", 96);
  private long                  savedCrc;
  private boolean               useMillimeters = prefs.getBoolean("useMillimeters", false);
  private boolean               useMouseWheel = prefs.getBoolean("useMouseWheel", false);
  private boolean               miniDynamicLaser = prefs.getBoolean("mini.dynamicLaser", true);
  private boolean               snapToGrid = prefs.getBoolean("snapToGrid", true);
  private boolean               displayGrid = prefs.getBoolean("displayGrid", true);
  private boolean               enableMiniLaser = prefs.getBoolean("enableMiniLaser", true);
  private boolean               enableMiniCnc = prefs.getBoolean("enableMiniCnc", false);
  private MiniLaser             miniLaser;
  private MiniCNC               miniCnc;
  private String                errorMsg;

  private boolean quitHandler () {
    if (savedCrc == surface.getDesignChecksum() || showWarningDialog("You have unsaved changes!\nDo you really want to quit?")) {
      if (enableMiniLaser && miniLaser != null) {
        try {
          miniLaser.close();
        } catch (Throwable ex) {
          ex.printStackTrace();
        }
      }
      if (enableMiniCnc && miniCnc != null) {
        try {
          miniCnc.close();
        } catch (Throwable ex) {
          ex.printStackTrace();
        }
      }
      return true;
    }
    return false;
  }

  private void showAboutBox () {
    ImageIcon icon = null;
    try {
      icon = new ImageIcon(getClass().getResource("/images/laser_wip_black.png"));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    String jsscInfo = null;
    if (enableMiniLaser || enableMiniCnc) {
      try {
        jsscInfo = "  Java Simple Serial Connector " + SerialNativeInterface.getLibraryVersion() + "\n" +
                   "  JSSC Native Code DLL Version " + SerialNativeInterface.getNativeLibraryVersion() + "\n";
      } catch (Throwable ex) {
        ex.printStackTrace();
      }
    }
    showMessageDialog(this,
        "By: Wayne Holder\n" +
            "  Java Runtime  " + Runtime.version() + "\n" +
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
    items.put("enableMiniLaser", new ParameterDialog.ParmItem("Mini Laser (restart to enable)", enableMiniLaser));
    items.put("enableMiniCnc", new ParameterDialog.ParmItem("Mini CNC (restart to enabled)", enableMiniCnc));
    items.put("useMouseWheel", new ParameterDialog.ParmItem("Mouse Wheel Scrolling", prefs.getBoolean("useMouseWheel", false)));
    items.put("useDblClkZoom", new ParameterDialog.ParmItem("Double-click Zoom{Dbl click to Zoom 2x, Shift + dbl click to unZoom}",
        prefs.getBoolean("useDblClkZoom", false)));
    items.put("enableGerber", new ParameterDialog.ParmItem("Enable Gerber ZIP Import", prefs.getBoolean("gerber.import", false)));
    items.put("pxDpi", new ParameterDialog.ParmItem("px per Inch (SVG Import/Export)", prefs.getInt("svg.pxDpi", 96)));
    ParameterDialog.ParmItem[] parmSet = items.values().toArray(new ParameterDialog.ParmItem[items.size()]);
    ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {"Save", "Cancel"}, useMillimeters));
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);              // Note: this call invokes dialog
    if (dialog.doAction() ) {
      for (String name : items.keySet()) {
        ParameterDialog.ParmItem parm = items.get(name);
        if ("useMouseWheel".equals(name)) {
          prefs.putBoolean("useMouseWheel", useMouseWheel = (Boolean) parm.value);
          configureMouseWheel();
        } else if ("enableMiniLaser".equals(name)) {
          prefs.putBoolean("enableMiniLaser", enableMiniLaser = (Boolean) parm.value);
        } else if ("enableMiniCnc".equals(name)) {
          prefs.putBoolean("enableMiniCnc", enableMiniCnc = (Boolean) parm.value);
        } else if ("useDblClkZoom".equals(name)) {
          surface.setDoubleClickZoomEnable((Boolean) parm.value);
        } else if ("enableGerber".equals(name)) {
          boolean enabled = (Boolean) parm.value;
          prefs.putBoolean("gerber.import", (Boolean) parm.value);
          gerberZip.setVisible(enabled);
        } else if ("pxDpi".equals(name)) {
          pxDpi = (Integer) parm.value;
          prefs.putInt("svg.pxDpi", pxDpi);
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
    surface = new DrawSurface(this, prefs, scrollPane = new JScrollPane(), ZingLaser.zingFullSize);
    scrollPane.setViewportView(surface);
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
      //
      // Add "About" Item to File Menu
      //
      JMenuItem aboutBox = new JMenuItem("About " + getClass().getSimpleName());
      aboutBox.addActionListener(ev -> {
        showAboutBox();
      });
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
    if (!hasAboutHandler || !hasPreferencesHandler) {
      fileMenu.addSeparator();
    }
    //
    // Add "New" Item to File Menu
    //
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
    //
    // Add "Open" Item to File menu
    //
    JMenuItem loadObj = new JMenuItem("Open");
    loadObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, cmdMask));
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
    //
    // Add "Save As" Item to File menu
    //
    JMenuItem saveAs = new JMenuItem("Save As");
    saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, cmdMask));
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
          if (sFile.exists()) {
            if (showWarningDialog("Overwrite Existing file?")) {
              saveDesign(sFile, surface.getDesign());
              savedCrc = surface.getDesignChecksum();
            }
          } else {
            saveDesign(sFile, surface.getDesign());
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
    // Add "Save As" Item to File menu
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
    String[][] shapeNames = new String[][] {
        {"Reference Point",     "CADReference"},
        {"Rectangle",           "CADRectangle"},
        {"Polygon",             "CADPolygon"},
        {"Oval",                "CADOval"},
        {"Text",                "CADText"},
        {"Gear",                "CADGear"},
        {"NEMA Stepper",        "CADNemaMotor"},
        {"Bobbin",              "CADBobbin"},
        {"Raster Image",        "CADRasterImage"},
        {"Spline Curve",        "CADShapeSpline"},
        {"Music Box Strip",     "CADMusicStrip"}
    };
    for (String[] parts : shapeNames) {
      JMenuItem mItem = new JMenuItem(parts[0]);
      mItem.addActionListener(ev -> {
        try {
          Class ref = Class.forName(LaserCut.class.getSimpleName() + '$' + parts[1]);
          CADShape shp = (CADShape) ref.getDeclaredConstructor().newInstance();
          if (shp instanceof CADShapeSpline) {
            surface.placeShape(shp);
          } else if (shp instanceof CADRasterImage) {
            // Prompt for Image file
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select an Image File");
            fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
            FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Image files (jpg, jpeg, png, gif, bmp)", "jpg", "jpeg", "png", "gif", "bmp");
            fileChooser.addChoosableFileFilter(nameFilter);
            fileChooser.setFileFilter(nameFilter);
            fileChooser.setSelectedFile(new File(prefs.get("image.dir", "/")));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
              try {
                File iFile = fileChooser.getSelectedFile();
                prefs.put("image.dir", iFile.getAbsolutePath());
                ((CADRasterImage) shp).loadImage(iFile);
                if (shp.placeParameterDialog(surface, useMillimeters)) {
                  surface.placeShape(shp);
                }
              } catch (Exception ex) {
                showErrorDialog("Unable to load file");
                ex.printStackTrace(System.out);
              }
            }
          } else if (shp.placeParameterDialog(surface, useMillimeters)) {
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
    //
    // Add grid size options
    //
    String[] gridSizes = new String[] {"|", "0.0625/16 in", "0.1/10 in", "0.125/8 in", "0.25/4 in", "0.5/2 in", "|",
        "1/10 mm", "2/10 mm", "2.5/5 mm", "5/10 mm", "10/0 mm"};
    for (String gridItem : gridSizes) {
      if (gridItem.equals("|")) {
        gridMenu.addSeparator();
      } else {
        double gridMinor;
        int gridMajor;
        String units = gridItem.substring(gridItem.length() - 2);
        String[] tmp = gridItem.substring(0, gridItem.length() - 3).split("/");
        if ("in".equals(units)) {
          gridMinor = Double.parseDouble(tmp[0]);
          gridMajor = Integer.parseInt(tmp[1]);
        } else if ("mm".equals(units)) {
          gridMinor = mmToInches(Double.parseDouble(tmp[0]));
          gridMajor = Integer.parseInt(tmp[1]);
        } else {
          gridMinor = 0;
          gridMajor = 0;
        }
        JMenuItem mItem = new JRadioButtonMenuItem("Off".equals(gridItem) ? "Off" : tmp[0] + " " + units);
        mItem.setSelected(gridMinor == surface.getGridSize());
        gridGroup.add(mItem);
        gridMenu.add(mItem);
        mItem.addActionListener(ev -> {
          try {
            surface.setGridSize(gridMinor, gridMajor);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
      }
    }
    menuBar.add(gridMenu);
    //
    // Add "Zoom" Menu
    //
    JMenu zoomMenu = new JMenu("Zoom");
    ButtonGroup zoomGroup = new ButtonGroup();
    for (double zoom : surface.getZoomFactors()) {
      JRadioButtonMenuItem zItem = new JRadioButtonMenuItem(zoom + " : 1");
      zItem.setSelected(zoom == surface.getZoomFactor());
      zoomMenu.add(zItem);
      zoomGroup.add(zItem);
      zItem.addActionListener(ev -> surface.setZoomFactor(zoom));
    }
    surface.addZoomListener((index) -> {
      for (int ii = 0; ii < zoomMenu.getMenuComponentCount(); ii++) {
        JRadioButtonMenuItem item = (JRadioButtonMenuItem) zoomMenu.getMenuComponent(ii);
        item.setSelected(ii == index);
      }
    });
    menuBar.add(zoomMenu);
    /*
     *  Add "Edit" Menu
    */
    JMenu editMenu = new JMenu("Edit");
    //
    // Add "Undo" Menu Item
    //
    JMenuItem undo = new JMenuItem("Undo");
    undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask));
    undo.setEnabled(false);
    undo.addActionListener((ev) -> surface.popFromUndoStack());
    editMenu.add(undo);
    surface.addUndoListener(undo::setEnabled);
    //
    // Add "Redo" Menu Item
    //
    JMenuItem redo = new JMenuItem("Redo");
    redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask + InputEvent.SHIFT_DOWN_MASK));
    redo.setEnabled(false);
    redo.addActionListener((ev) -> surface.popFromRedoStack());
    editMenu.add(redo);
    surface.addRedoListener(redo::setEnabled);
    //
    // Add "Remove Selected" Menu Item
    //
    JMenuItem removeSelected = new JMenuItem("Delete Selected");
    removeSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, cmdMask));
    removeSelected.setEnabled(false);
    removeSelected.addActionListener((ev) -> surface.removeSelected());
    editMenu.add(removeSelected);
    //
    // Add "Duplicate Selected" Menu Item
    //
    JMenuItem dupSelected = new JMenuItem("Duplicate Selected");
    dupSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, cmdMask));
    dupSelected.setEnabled(false);
    dupSelected.addActionListener((ev) -> surface.duplicateSelected());
    editMenu.add(dupSelected);
    //
    // Add "Edit Selected" Menu Item
    //
    JMenuItem editSelected = new JMenuItem("Edit Selected");
    editSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, cmdMask));
    editSelected.setEnabled(false);
    editSelected.addActionListener((ev) -> {
      CADShape sel = surface.getSelected();
      boolean centered = sel.centered;
      if (sel != null && sel.editParameterDialog(surface, useMillimeters)) {
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
    JMenuItem moveSelected = new JMenuItem("Move Selected");
    moveSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, cmdMask));
    moveSelected.setEnabled(false);
    moveSelected.addActionListener((ev) -> {
      surface.moveSelected();
    });
    editMenu.add(moveSelected);
    //
    // Add "Move Selected" Menu Item
    //
    JMenuItem rotateSelected = new JMenuItem("Rotate Group Around Selected");
    rotateSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, cmdMask));
    rotateSelected.setEnabled(false);
    rotateSelected.addActionListener((ev) -> {
      surface.rotateGroupAroundSelected();
    });
    editMenu.add(rotateSelected);
    //
    // Add "Round Corners of Selected Shape" Menu Item
    //
    JMenuItem roundCorners = new JMenuItem("Round Corners of Selected Shape");
    roundCorners.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, cmdMask));
    roundCorners.setEnabled(false);
    roundCorners.addActionListener((ev) -> {
      ParameterDialog.ParmItem[] rParms = {new ParameterDialog.ParmItem("radius|in", 0d)};
      ParameterDialog rDialog = (new ParameterDialog(rParms, new String[] {"Round", "Cancel"}, useMillimeters));
      rDialog.setLocationRelativeTo(surface.getParent());
      rDialog.setVisible(true);              // Note: this call invokes dialog
      if (rDialog.doAction()) {
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
    if (enableMiniCnc) {
      cncSelected.setEnabled(false);
      cncSelected.addActionListener((ev) -> {
        // Display dialog to get tool radius and inset flag
        ParameterDialog.ParmItem[] rParms = {new ParameterDialog.ParmItem("radius|in{radius of tool}", 0d),
                                             new ParameterDialog.ParmItem("inset{If checked, toolpath routes interior" +
                                                                          " of shape, else outside}", true)};
        ParameterDialog rDialog = (new ParameterDialog(rParms, new String[] {"OK", "Cancel"}, useMillimeters));
        rDialog.setLocationRelativeTo(surface.getParent());
        rDialog.setVisible(true);              // Note: this call invokes dialog
        if (rDialog.doAction()) {
          surface.pushToUndoStack();
          double radius = (Double) rParms[0].value;
          boolean inset = (Boolean) rParms[1].value;
          CADShape shape = surface.getSelected();
          CNCPath path = new CNCPath(shape, radius, inset);
          surface.addShape(path);
        }
      });
      editMenu.add(cncSelected);
    }
    //
    // Add "Ungroup Selected" Menu Item
    //
    JMenuItem unGroupSelected = new JMenuItem("Ungroup Selected");
    unGroupSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, cmdMask));
    unGroupSelected.setEnabled(false);
    unGroupSelected.addActionListener((ev) -> surface.unGroupSelected());
    editMenu.add(unGroupSelected);
    // Add "Align Grouped Shape(s) to Selected Shape's" submenu
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
    // Add "Add Grouped Shapes" Menu Item
    //
    JMenuItem addSelected = new JMenuItem("Add Grouped Shape{s) to Selected Shape");
    addSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, cmdMask));
    addSelected.setEnabled(false);
    addSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(true));
    editMenu.add(addSelected);
    //
    // Add "Subtract Group from Selected" Menu Item
    //
    JMenuItem subtractSelected = new JMenuItem("Take Away Grouped Shape(s) from Selected Shape");
    subtractSelected.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, cmdMask));
    subtractSelected.setEnabled(false);
    subtractSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(false));
    editMenu.add(subtractSelected);
    //
    // Add SelectListener to enable/disable menus, as needed
    //
    surface.addSelectListener((shape, selected) -> {
      boolean canSelect = !(shape instanceof CNCPath) & selected;
      removeSelected.setEnabled(selected);
      dupSelected.setEnabled(canSelect);
      editSelected.setEnabled(selected);
      moveSelected.setEnabled(canSelect);
      roundCorners.setEnabled(canSelect);
      if (enableMiniCnc) {
        cncSelected.setEnabled(canSelect);
      }
      saveSelected.setEnabled(canSelect);
      boolean hasGroup = surface.getSelected() != null && surface.getSelected().getGroup() != null;
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
    //
    // Add "Import LaserCut File" Item to File menu
    //
    JMenuItem importObj = new JMenuItem("Import LaserCut File");
    importObj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, cmdMask));
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
    //
    // Add "Import SVG File" menu item
    //
    JMenuItem svgRead = new JMenuItem("Import SVG File");
    svgRead.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select a SVG File");
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Scalable Vector Graphics files (*.svg)", "svg");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.svg.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          prefs.put("default.svg.dir", tFile.getAbsolutePath());
          SVGParser parser = new SVGParser();
          parser.setPxDpi(pxDpi);
          //parser.enableDebug(true);
          Shape[] shapes = parser.parseSVG(tFile);
          shapes = SVGParser.removeOffset(shapes);
          Shape shape = SVGParser.combinePaths(shapes);
          Rectangle2D bounds = BetterBoundingBox.getBounds(shape);
          double offX = bounds.getWidth() / 2;
          double offY = bounds.getHeight() / 2;
          AffineTransform at = AffineTransform.getTranslateInstance(-offX, -offY);
          shape = at.createTransformedShape(shape);
          CADShape shp = new CADScaledShape(shape, offX, offY, 0, false, 100.0);
          if (shp.placeParameterDialog(surface, useMillimeters)) {
            surface.placeShape(shp);
          }
        } catch (Exception ex) {
          showErrorDialog("Unable to load SVG file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(svgRead);
    //
    // Add "Import DXF File" menu item
    //
    JMenuItem dxfRead = new JMenuItem("Import DXF File");
    dxfRead.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select a DXF File");
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("AutoCad DXF files (*.dxf)", "dxf");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dxf.dir", "/")));
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
          File tFile = fileChooser.getSelectedFile();
          prefs.put("default.dxf.dir", tFile.getAbsolutePath());
          DXFReader dxf = new DXFReader();
          Shape[] shapes = dxf.parseFile(tFile, 12, 2);
          shapes = SVGParser.removeOffset(shapes);
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
        } catch (Exception ex) {
          showErrorDialog("Unable to load DXF file");
          ex.printStackTrace(System.out);
        }
      }
    });
    importMenu.add(dxfRead);
    //
    // Add "Import Gerber Zip" menu item
    //
    gerberZip = new JMenuItem("Import Gerber Zip");
    gerberZip.setVisible(prefs.getBoolean("gerber.import", false));
    gerberZip.addActionListener((ActionEvent ev) -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select a Zip-encoded Gerber File");
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
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
    //
    // Add "Zing Laser" Menu to Export Menu
    //
    exportMenu.add(ZingLaser.getZingMenu(this));
    //
    // Add "Mini Laser" Menu to Export Menu
    //
    if (enableMiniLaser) {
      try {
        exportMenu.add((miniLaser = new MiniLaser(this)).getMiniLaserMenu());
      } catch (Throwable ex) {
        if (showWarningDialog("Unable to initialize JSSCPort serial port (See \"Errors\" menu for more details)\n" +
                              "Click OK to disable the Mini Laser and prevent this error from happening again.\n" +
                              "Note; you can use the Preferences dialog box to renable the Mini Laser.\n" +
                              "Do you want to disable the Mini Laser features?")) {
          prefs.putBoolean("enableMiniLaser", enableMiniLaser = false);
        }
        // Save stack trace for "Error" menu
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter pout = new PrintWriter(bout);
        ex.printStackTrace(pout);
        try {
          pout.close();
          bout.close();
        } catch (Exception ex2) {}
        errorMsg = "Mini Laser Error:\n" + bout.toString();
        ex.printStackTrace();
      }
    }
    //
    // Add "Mini CNC" Menu to Export Menu
    //
    if (enableMiniCnc) {
      try {
        exportMenu.add((miniCnc = new MiniCNC(this)).getMiniCncMenu());
      } catch (Throwable ex) {
        if (showWarningDialog("Unable to initialize JSSCPort serial port (See \"Errors\" menu for more details)\n" +
                              "Click OK to disable the Mini CNC and prevent this error from happening again.\n" +
                              "Note; you can use the Preferences dialog box to renable the Mini CNC.\n" +
                              "Do you want to disable the Mini CNC features?")) {
          prefs.putBoolean("enableMiniCnc", enableMiniCnc = false);
        }
        // Save stack trace for "Error" menu
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter pout = new PrintWriter(bout);
        ex.printStackTrace(pout);
        try {
          pout.close();
          bout.close();
        } catch (Exception ex2) {}
        errorMsg = (errorMsg != null ? errorMsg + "\n" : "") + "Mini CNC Error:\n" + bout.toString();
        ex.printStackTrace();
      }
    }
    //
    // Add "Export to PDF File" Menu Item
    //
    JMenuItem pdfOutput = new JMenuItem("Export to PDF File");
    pdfOutput.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Export to PDF File");
      fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.pdf)", "pdf");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.pdf", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fPath = sFile.getPath();
        if (!fPath.contains(".")) {
          sFile = new File(fPath + ".pdf");
        }
        try {
          if (!sFile.exists() || showWarningDialog("Overwrite Existing file?")) {
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
    //
    // Add "Export to SVG File"" Menu Item
    //
    JMenuItem svgOutput = new JMenuItem("Export to SVG File");
    svgOutput.addActionListener(ev -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Export to SVG File");
      fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("LaserCut files (*.svg)", "svg");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.svg", "/")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File sFile = fileChooser.getSelectedFile();
        String fPath = sFile.getPath();
        if (!fPath.contains(".")) {
          sFile = new File(fPath + ".svg");
        }
        try {
          if (!sFile.exists() || showWarningDialog("Overwrite Existing file?")) {
            List<Shape> sList = new ArrayList<>();
            for (CADShape item : surface.getDesign()) {
              if (item instanceof CADReference)
                continue;
              Shape shape = item.getWorkspaceTranslatedShape();
              sList.add(shape);
            }
            Shape[] shapes = sList.toArray(new Shape[sList.size()]);
            String xml = SVGParser.shapesToSVG(shapes, surface.getWorkSize(), pxDpi);
            FileOutputStream out = new FileOutputStream(sFile);
            out.write(xml.getBytes("UTF8"));
            out.close();
          }
        } catch (Exception ex) {
          showErrorDialog("Unable to save file");
          ex.printStackTrace();
        }
        prefs.put("default.svg", sFile.getAbsolutePath());
      }
    });
    exportMenu.add(svgOutput);
    menuBar.add(exportMenu);
    /*
     *  Add "Units" Menu
     */
    JMenu unitsMenu = new JMenu("Units");
    ButtonGroup unitGroup = new ButtonGroup();
    String[] unitSet = {"Inches", "Millimeters"};
    for (String unit : unitSet) {
      boolean select = ( useMillimeters && unit.equals("Millimeters")) | ( !useMillimeters && unit.equals("Inches"));
      JMenuItem uItem = new JRadioButtonMenuItem(unit, select);
      unitGroup.add(uItem);
      unitsMenu.add(uItem);
      uItem.addActionListener(ev -> {
        prefs.putBoolean("useMillimeters", useMillimeters = unit.equals("Millimeters"));
      });
    }
    menuBar.add(unitsMenu);
    if (errorMsg != null) {
      // Add "Error" Menu if JSSC error thrown
      JMenu errorMenu = new JMenu("Errors");
      JMenuItem eItem = new JMenuItem("Error Stacktrace");
      errorMenu.add(eItem);
      eItem.addActionListener(ev -> {
        showScrollingDialog(errorMsg);
      });
      menuBar.add(errorMenu);
    }
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
      surface.addShape(new CADText(4.625, .25, "Belle", "Helvetica", "bold", 72, 0, 0, false));
      // Add Test Gear
      surface.addShape(new CADGear(2.25, 2.25, .1, 30, 10, 20, .25, 0, mmToInches(3)));
      savedCrc = surface.getDesignChecksum();   // Allow quit if unchanged
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

  public static String getResourceFile (String file) {
    try {
      InputStream fis = LaserCut.class.getResourceAsStream(file);
      if (fis != null) {
        byte[] data = new byte[fis.available()];
        fis.read(data);
        fis.close();
        return new String(data);
      }
    } catch (Exception ex) {}
    return "";
  }

  public Properties getProperties (String content) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(content));
    } catch (Exception ex) {}
    return props;
  }

  /*
   * CADShape interfaces
   */

  interface CADNoDraw {}  // Marker Interface

  interface ChangeListener {
    void shapeChanged (CADShape cadShape);
  }

  static class CADShape implements Serializable {
    private static final long serialVersionUID = 3716741066289930874L;
    public double     xLoc, yLoc, rotation;   // Note: must be public for reflection
    public boolean    centered, engrave;      // Note: must be public for reflection
    CADShapeGroup     group;
    Shape             shape;
    transient Shape   builtShape;
    transient boolean isSelected;
    transient List<ChangeListener> changeSubscribers;

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

    void addChangeListener (ChangeListener subscriber) {
      if (changeSubscribers == null) {
        changeSubscribers = new LinkedList<>();
      }
      changeSubscribers.add(subscriber);
    }

    void notifyChangeListeners () {
      if (changeSubscribers != null) {
        for (ChangeListener subscriber : changeSubscribers) {
          subscriber.shapeChanged(this);
        }
      }
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

    String getShapePositionInfo () {
      return "xLoc: " + LaserCut.df.format(xLoc) + ", yLoc: " + LaserCut.df.format(yLoc);
    }

    /**
     * Get absolute bounds of shape in workspace coords
     * @return absolute bounding rectangle
     */
    Rectangle2D getShapeBounds () {
      Rectangle2D bounds = getShape().getBounds2D();
      if (centered) {
        return new Rectangle2D.Double(xLoc - bounds.getWidth() / 2, yLoc - bounds.getHeight() / 2, bounds.getWidth(), bounds.getHeight());
      } else {
        return new Rectangle2D.Double(xLoc, yLoc, bounds.getWidth(), bounds.getHeight());
      }
    }

    /**
     * Return the distance from point cx/cy to the upper-left corner of shape's bounding box.
     * Note: used only by DrawSurface.selectLaserItems() to optimize cutting sequence
     * @param cx x coord
     * @param cy y coord
     * @return distance from upper-left corner of shape's bounding box to cx/cy
     */
    public double distanceToUpperLeftPoint (double cx, double cy) {
      Rectangle2D bounds = getShapeBounds();
      double dx = cx - bounds.getX();
      double dy = cy - bounds.getY();
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
    protected Shape getLocallyTransformedShape () {
      Shape dShape = getShape();
      AffineTransform at = new AffineTransform();
      // Position Shape centered on xLoc/yLoc in inches (x from left, y from top)
      at.rotate(Math.toRadians(rotation));
      if (!centered) {
        // Position shape relative to its upper left bounding box at position xLoc/yLoc in inches
        Rectangle2D bounds = dShape.getBounds2D();
        at.translate(bounds.getWidth() / 2, bounds.getHeight() / 2);
      }
      return at.createTransformedShape(dShape);
    }

    // Translate Shape to Workspace position
    protected Shape getWorkspaceTranslatedShape () {
      Shape shape = getLocallyTransformedShape();
      AffineTransform at = AffineTransform.getTranslateInstance(xLoc, yLoc);
      return at.createTransformedShape(shape);
    }

    /**
     * Uses Shape.pathIterator() to convert the Shape into List of arrays of lines.  The size input Shape is
     * assumed to be defined in inches, but the AffineTransform parameter can be used to scale up to the
     * final render resolution.  Note: cubic and quadratic bezier curves calculate an approximaiton of the
     * arc length of the curve to determine the number of line segments used to approximate the curve.
     * @param shape Shape path to render
     * @param scale used to scale from inches to the render resolution, such as Screen or Laser DPI.
     * @return List of array of lines
     */
    static List<Line2D.Double[]> transformShapeToLines (Shape shape, double scale) {
      // Use PathIterator to convert Shape into a series of lines defining a path
      List<Line2D.Double[]> paths = new ArrayList<>();
      AffineTransform at = scale != 0 ? AffineTransform.getScaleInstance(scale, scale) : null;
      double maxSegSize = .01;                                                  // Max size of a bezier segment, in inches
      ArrayList<Line2D.Double> lines = new ArrayList<>();
      PathIterator pi = shape.getPathIterator(at);
      double xx = 0, yy = 0;
      double mX = 0, mY = 0;
      while (!pi.isDone()) {
        double[] coords = new double[6];
        int type = pi.currentSegment(coords);
        switch (type) {
          case PathIterator.SEG_CLOSE:
            lines.add(new Line2D.Double(xx, yy, mX, mY));
            break;
          case PathIterator.SEG_MOVETO:
            if (lines.size() > 0) {
              paths.add(lines.toArray(new Line2D.Double[lines.size()]));
              lines = new ArrayList<>();
            }
            mX = xx = coords[0];
            mY = yy = coords[1];
            break;
          case PathIterator.SEG_LINETO:
            lines.add(new Line2D.Double(xx, yy, xx = coords[0], yy = coords[1]));
            break;
          case PathIterator.SEG_CUBICTO:
            // Decompose 4 point, cubic bezier curve into line segments
            Point2D.Double c1 = new Point2D.Double(xx, yy);                     // Start point
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
              lines.add(new Line2D.Double(xx, yy, xx = tmp[0].x, yy = tmp[0].y));
            }
            break;
          case PathIterator.SEG_QUADTO:
            // Decompose 3 point, quadratic bezier curve into line segments
            Point2D.Double q1 = new Point2D.Double(xx, yy);                     // Start point
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
            lines.add(new Line2D.Double(xLast, yLast, xx = q3.x, yy = q3.y));
            break;
          default:
            System.out.println("Error, Unknown PathIterator Type: " + type);
            break;
        }
        pi.next();
      }
      if (lines.size() > 0) {
        paths.add(lines.toArray(new Line2D.Double[lines.size()]));
        lines = new ArrayList<>();
      }
      return paths;
    }

    void draw (Graphics g, double zoom) {
      Graphics2D g2 = (Graphics2D) g.create();
      Shape dShape = getWorkspaceTranslatedShape();
      boolean inGroup = getGroup() != null && getGroup().isGroupSelected();
      boolean highlight = isSelected || inGroup;
      g2.setStroke(getShapeStroke(highlight, isSelected));
      g2.setColor(getShapeColor(highlight, engrave));
      // Scale Shape to scale and draw it
      //if (false) {
        // Use PathIterator to draw Shape line by line
        // Note: used to debug transformShapeToLines()
        //for (Line2D.Double line : transformShapeToLines(dShape, zoom)) {
        //  g2.draw(line);
        //}
      //} else {
        // Draw Area directly
        AffineTransform atScale = AffineTransform.getScaleInstance(zoom, zoom);
        g2.draw(atScale.createTransformedShape(dShape));
      //}
      if (!(this instanceof CNCPath)) {
        if (isSelected || this instanceof CADReference || this instanceof CADShapeSpline) {
          double mx = xLoc * zoom;
          double my = yLoc * zoom;
          double mWid = 3 * zoom / SCREEN_PPI;
          g2.setStroke(new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f));
          g2.draw(new Line2D.Double(mx - mWid, my, mx + mWid, my));
          g2.draw(new Line2D.Double(mx, my - mWid, mx, my + mWid));
        }
      }
      g2.dispose();
    }

    /**
     * Override in subclass, as needed
     * @return Color used to draw shape based on state of highlight and engrave
     */
    Color getShapeColor (boolean highlight, boolean engrave) {
      return highlight ? engrave ? Color.RED : Color.blue : engrave ? Color.ORANGE : Color.black;
    }

    /**
     * Override in subclass, as needed
     * @return BasicStroke used to draw shape based on state of highlight and engrave
     */
    BasicStroke getShapeStroke (boolean highlight, boolean isSelected) {
      return new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f);
    }

    /**
     * Transform shape to workspace and return as list of arrays of line segments where each array
     * in the list is the set of lines for a closed shape.
     * @param scale scale factor
     * @return list of arrays of line segments
     */
    List<Line2D.Double[]> getListOfScaledLines (double scale) {
      return transformShapeToLines(getWorkspaceTranslatedShape(), scale);
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
    boolean selectMovePoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint) {
      return false;
    }

    boolean updateInternalState (Point2D.Double point) {
      // override in subclass, as needed
      return false;
    }

    /**
     * Override in subclass to cancel selection of a moveable internal point
     */
    void cancelMove () { }


    void setPosition (double newX, double newY) {
      if (!(this instanceof CNCPath)) {
        xLoc = newX;
        yLoc = newY;
        notifyChangeListeners();
      }
    }

    /**
     * Set position of shape to a new location, but keep anchor inside working area
     * @param newLoc new x/y position (in shape coordinates, inches)
     * @return delta position change in a Point2D.Double object
     */
    Point2D.Double dragPosition (Point2D.Double newLoc, Dimension workSize) {
      double x = Math.max(Math.min(newLoc.x, workSize.width / SCREEN_PPI), 0);
      double y = Math.max(Math.min(newLoc.y, workSize.height / SCREEN_PPI), 0);
      Point2D.Double delta = new Point2D.Double(x - xLoc, y - yLoc);
      setPosition(x, y);
      return delta;
    }

    /**
     * Move shape's position by amount specified in 'delta'
     * @param delta amount to move CADShape
     */
    void movePosition (Point2D.Double delta) {
      setPosition(xLoc + delta.x, yLoc + delta.y);
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
    boolean isPositionClicked (Point2D.Double point, double zoom) {
      double dist = point.distance(xLoc, yLoc) * SCREEN_PPI;
      return dist < 5 / zoom;
    }

    /**
     * Check if 'point' is close to one of the segments that make up the shape
     * @param point Location click on screen in model coordinates (inches)
     * @return true if close enough to consider a 'touch'
     */
    boolean isShapeClicked (Point2D.Double point, double zoom) {
      // Translate Shape to position, Note: point is in screen units (scale)
      Shape lShape = getWorkspaceTranslatedShape();
      // Scale Shape to Screen scale and scan all line segments in the shape
      // return true if any is closer than 4 pixels to point
      for (Line2D.Double[] lines : transformShapeToLines(lShape, 1)) {
        for (Line2D.Double line : lines) {
          double dist = line.ptSegDist(point) * SCREEN_PPI;
          if (dist < 5 / zoom) {
            return true;
          }
        }
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
      notifyChangeListeners();
    }

    // Note: override in subclass, as needed
    protected List<String> getPlaceFields () {
      return Arrays.asList("rotation|deg", "centered", "engrave");
    }

    // Note: override in subclass, as needed
    protected List<String> getEditFields () {
      return Arrays.asList("xLoc|in", "yLoc|in", "rotation|deg", "centered", "engrave");
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields.  Uses reflection to read and save
     * parameter values before clicking the mouse to place the shape.
     * @return true if used clicked OK to save
     */
    boolean placeParameterDialog (DrawSurface surface, boolean mmUnits) {
      return displayShapeParameterDialog(surface, new ArrayList<>(getPlaceFields()), "Place", mmUnits);
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields.  Uses reflection to read and save
     * parameter values.
     * @return true if used clicked OK to save
     */
    boolean editParameterDialog (DrawSurface surface, boolean mmUnits) {
      return displayShapeParameterDialog(surface, new ArrayList<>(getEditFields()), "Save", mmUnits);
    }

    /**
     * Bring up editable parameter dialog box do user can edit fields using reflection to read and update parameter values.
     * @param surface parent Component for Dialog
     * @param parmNames List of parameter names
     * @param actionButton Text for action button, such as "Save" or "Place"
     * @return true if used clicked action button, else false if they clicked cancel.
     */
    boolean displayShapeParameterDialog (DrawSurface surface, ArrayList<String> parmNames, String actionButton, boolean mmUnits) {
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
      ParameterDialog dialog = (new ParameterDialog(parmSet, new String[] {actionButton, "Cancel"}, mmUnits));
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
        // Position shape where user clicked on the "Place" butto
        Point dLoc = surface.getLocationOnScreen();
        Point mLoc = dialog.getMouseLoc();
        if ("Place".equals(actionButton) && dLoc != null && mLoc != null) {
          setPosition((mLoc.x - dLoc.x) / surface.getScreenScale(), (mLoc.y - dLoc.y) / surface.getScreenScale());
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
    protected List<String> getPlaceFields () {
      return new ArrayList<>();
    }

    @Override
    protected List<String> getEditFields () {
      return Arrays.asList("xLoc|in", "yLoc|in");
    }
  }

  static class CADRasterImage extends CADShape implements Serializable, CADNoDraw {
    private static final long serialVersionUID = 2309856254388651139L;
    public double             width, height, scale = 100.0;
    public boolean            engrave3D;
    public String             imagePpi;
    Dimension                 ppi;
    transient BufferedImage   img;

    CADRasterImage () {
      engrave = true;
    }

    void loadImage (File imgFile) throws IOException {
      ppi = getImageDPI(imgFile);
      imagePpi = ppi.width + "x" + ppi.height;
      img = ImageIO.read(imgFile);
      ColorModel cm = img.getColorModel();
      width = (double) img.getWidth() / ppi.width;
      height = (double) img.getHeight() / ppi.height;
    }

    @Override
    void draw (Graphics g, double zoom) {
      Graphics2D g2 =  (Graphics2D) g.create();
      BufferedImage bufimg;
      if (engrave) {
        // Convert Image to greyscale
        bufimg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bufimg.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
      } else {
        bufimg = img;
      }
      // Transform image for centering, rotation and scale
      AffineTransform at = new AffineTransform();
      if (centered) {
        at.translate(xLoc * zoom, yLoc * zoom);
        at.scale(scale / 100 * SCREEN_PPI / ppi.width, scale / 100 * SCREEN_PPI / ppi.height);
        at.rotate(Math.toRadians(rotation));
        at.translate(-bufimg.getWidth() / 2.0, -bufimg.getHeight() / 2.0);
      } else {
        at.translate(xLoc * zoom, yLoc * zoom);
        at.scale(scale / 100 * SCREEN_PPI / ppi.width, scale / 100 * SCREEN_PPI / ppi.height);
        at.rotate(Math.toRadians(rotation));
      }
      // Draw with 40% Alpha to make image semi transparent
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      g2.drawImage(bufimg, at, null);
      g2.dispose();
      super.draw(g, zoom);
    }

    /**
     * Used to compute scale factors needed to engrave image on Zing
     * @param destPpi destination ppi (usually ZING_PPI)
     * @return array of double where [0] is x scale and [1] is y scale
     */
    double[] getScale (double destPpi) {
      return new double[] {(double) destPpi / ppi.getWidth() * scale / 100, (double) destPpi / ppi.getHeight() * scale / 100};
    }

    /**
     * Computes the bounding box (in pixels) after the imagea is scaled and rotated
     * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
     * @return Bounding box for scaled and rotated image
     */
    Rectangle2D getScaledRotatedBounds (double[] scale) {
      AffineTransform at = new AffineTransform();
      at.scale(scale[0], scale[1]);
      at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
      Path2D.Double tShape = (Path2D.Double) at.createTransformedShape(new Rectangle2D.Double(0, 0, img.getWidth(), img.getHeight()));
      return tShape.getBounds2D();
    }

    /**
     * Compute the AffineTransform needed to scale and rotate the image
     * @param bb Bounding box computed by getScaledRotatedBounds()
     * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
     * @return AffineTransform which will scale and rotate image into bounding box computed by getScaledRotatedBounds()
     */
    AffineTransform getScaledRotatedTransform (Rectangle2D bb, double[] scale) {
      AffineTransform at = new AffineTransform();
      at.translate(-bb.getX(), -bb.getY());
      at.scale(scale[0], scale[1]);
      at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
      return at;
    }

    /**
     * Compute the origin point on the edge of the scaled and rotated image
     * @param at AffineTransform used to scale and rotate
     * @param bb Bounding box computed by getScaledRotatedBounds()
     * @return Origin point on the edge of the image (offset by the negative of these amounts when drawing)
     */
    Point2D.Double getScaledRotatedOrigin (AffineTransform at, Rectangle2D bb) {
      if (centered) {
        return new Point2D.Double(bb.getWidth() / 2, bb.getHeight() / 2);
      } else {
        Point2D.Double origin = new Point2D.Double(0, 0);
        at.transform(origin, origin);
        return origin;
      }
    }

    /**
     * Generate a scaled and rotated image that fits inside the bounding box computed by getScaledRotatedBounds()
     * @param at AffineTransform used to scale and rotate
     * @param bb Bounding box computed by getScaledRotatedBounds()
     * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
     * @return BufferedImage containing scaled and rotated image
     */
    BufferedImage getScaledRotatedImage (AffineTransform at, Rectangle2D bb, double[] scale) {
      // Create new BufferedImage the size of the bounding for for the scaled and rotated image
      int wid = (int) Math.round(bb.getWidth());
      int hyt = (int) Math.round(bb.getHeight());
      BufferedImage bufImg = new BufferedImage(wid, hyt, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = bufImg.createGraphics();
      g2.setColor(Color.white);
      g2.fillRect(0, 0, wid, hyt);
      // Draw scaled and rotated image into newly-created BufferedImage
      at = new AffineTransform();
      at.translate(-bb.getX(), -bb.getY());
      at.scale(scale[0], scale[1]);
      at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
      g2.drawImage(img, at, null);
      return bufImg;
    }

    @Override
    Shape buildShape () {
      AffineTransform at = new AffineTransform();
      at.scale(scale / 100, scale / 100);
      return at.createTransformedShape(new Rectangle2D.Double(-width / 2, -height / 2, width, height));
    }

    @Override
    protected List<String> getEditFields () {
      return Arrays.asList("xLoc|in", "yLoc|in", "*width|in", "*height|in", "*imagePpi", "rotation|deg", "scale|%", "centered", "engrave", "engrave3D");
    }

    @Override
    protected List<String> getPlaceFields () {
      return Arrays.asList("*imagePpi", "rotation|deg", "scale|%", "centered", "engrave", "engrave3D");
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
            } else if ((nodes = tree.getElementsByTagName("BMPVersion")).getLength() > 0) {
              // Note: there must be a more efficient way to do this...
              NodeList bmp = tree.getElementsByTagName("PixelsPerMeter");
              Map<String, Double> map = new HashMap<>();
              for (int ii = 0; ii < bmp.getLength(); ii++) {
                Node item = bmp.item(ii);
                NodeList bmp2 = item.getChildNodes();
                for (int jj = 0; jj < bmp2.getLength(); jj++) {
                  Node xy = bmp2.item(jj);
                  map.put(xy.getNodeName().toLowerCase(), Double.parseDouble(xy.getNodeValue()));
                }
              }
              if (map.size() == 2) {
                return new Dimension((int) Math.round(map.get("x") / 39.3701), (int) Math.round(map.get("y") / 39.3701));
              }
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

  static class CADMusicStrip extends CADShape implements Serializable {
    private static final long serialVersionUID = 7398125917619364676L;
    private static String[] symb = {"E ", "D ", "C ", "B ", "A#", "A ", "G#", "G ", "F#", "F ", "E ", "D#", "D ", "C#", "C ",
                                    "B ", "A#", "A ", "G#", "G ", "F#", "F ", "E ", "D ", "C ", "B ", "A ", "G ", "D ", "C "};
    private static double     xStep = 4.0;
    private static double     yStep = 2.017;
    private static double     xOff = mmToInches(8);
    private static double     yOff = mmToInches(6);
    private static double     holeDiam = mmToInches(2.4);
    private static final int  cols = 60;
    private boolean           checkClicked;
    public double width,      height;
    public boolean[][]        notes = new boolean[cols][30];
    private transient Shape   rect;

    @SuppressWarnings("unused")
    CADMusicStrip () {
      width = mmToInches(cols * 4 + 16);
      height = mmToInches(70);
    }

    @Override
    void draw (Graphics g, double zoom) {
      Graphics2D g2 = (Graphics2D) g.create();
      Stroke thick = new BasicStroke(1.0f);
      Stroke thin = new BasicStroke(0.8f);
      double mx = (xLoc + xOff) * zoom;
      double my = (yLoc + yOff) * zoom;
      double zf = zoom / SCREEN_PPI;
      g2.setFont(new Font("Arial", Font.PLAIN, (int) (8 * zf)));
      for (int ii = 0; ii <= notes.length; ii++) {
        double sx = mx + mmToInches(ii * xStep * zoom);
        g2.setColor((ii & 1) == 0 ? Color.black : Color.lightGray);
        g2.setStroke((ii & 1) == 0 ? thick : thin);
        g2.draw(new Line2D.Double(sx, my, sx, my + mmToInches(29 * yStep * zoom)));
        for (int jj = 0; jj < 30; jj++) {
          double sy = my + mmToInches(jj * yStep * zoom);
          g2.setColor(jj == 0 || jj == 29 ? Color.black : Color.lightGray);
          g2.setStroke(jj == 0 || jj == 29 ? thick : thin);
          g2.draw(new Line2D.Double(mx, sy, mx + mmToInches(cols * xStep * zoom), sy));
          if (ii == 0 ) {
            g2.setColor(Color.orange);
            g2.drawString(symb[jj], (int) (sx - 16 * zf), (int) (sy + 2.5 * zf));
          }
        }
      }
      g2.dispose();
      super.draw(g, zoom);
    }

    @Override
    boolean updateInternalState (Point2D.Double point) {
      // See if user clicked on one of the note spots (Note: point in screen inch coords)
      double xx = inchesToMM(point.x - xLoc - xOff);
      double yy = inchesToMM(point.y - yLoc - yOff);
      double gridX = Math.floor((xx / xStep) + 0.5);
      double gridY = Math.floor((yy / yStep) + 0.5);
      double dX = xx - gridX * xStep;
      double dY = yy - gridY * yStep;
      double dist = Math.sqrt(dX * dX + dY * dY);
      //System.out.println(df.format(gridX) + ", " + df.format(gridY) + " - " +  df.format(dist));
      if (dist <= .75 && gridX < notes.length && gridY < 30) {
        // Used has clicked in a note circle
        notes[(int) gridX][(int) gridY] ^= true;
        updateShape();
        return true;
      }
      return false;
    }

    @Override
    Shape getShape () {
      if (checkClicked && rect != null) {
        return rect;
      }
      return super.getShape();
    }

    @Override
    boolean isShapeClicked (Point2D.Double point, double zoom) {
      checkClicked = true;
      boolean clicked = super.isShapeClicked(point, zoom);
      checkClicked = false;
      return clicked;
    }

    @Override
    Shape buildShape () {
      Path2D.Double path = new Path2D.Double();
      double xx = -width / 2;
      double yy = -height / 2;
      // Draw enclosing box with notched corner to indicate orientation of strip
      path.moveTo(xx, yy);
      path.lineTo(xx + width, yy);
      path.lineTo(xx + width, yy + height);
      path.lineTo(xx + .2, yy + height);
      path.lineTo(xx, yy - .4 + height);
      path.lineTo(xx, yy);
      // Draw the holes that need to be cut for active notes
      double rad = holeDiam / 2;
      for (int ii = 0; ii < notes.length; ii++) {
        double sx = xx + xOff + mmToInches(ii * xStep);
        for (int jj = 0; jj < 30; jj++) {
          double sy = yy + yOff + mmToInches(jj * yStep);
          if (notes[ii][jj]) {
            path.append(new Ellipse2D.Double(sx - rad, sy - rad, holeDiam, holeDiam), false);
          }
        }
      }
      return path;
    }

    @Override
    String[] getParameterNames () {
      return new String[0];
    }

    @Override
    protected List<String> getEditFields () {
      return Arrays.asList("xLoc|in", "yLoc|in");
    }

    @Override
    protected List<String> getPlaceFields () {
      return Arrays.asList("xLoc|in", "yLoc|in");
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

  static class VectorFont {
    private static Map<String,VectorFont> vFonts = new HashMap();
    int[][][] font;
    int       height;

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

  static class CADText extends CADShape implements Serializable {
    private static final long serialVersionUID = 4314642313295298841L;
    public String   text, fontName, fontStyle;
    public int      fontSize;
    public double   tracking;
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
      fonts.add("Vector 1");
      fonts.add("Vector 2");
      fonts.add("Vector 3");
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
      tracking = 0;
      engrave = true;
    }

    CADText (double xLoc, double yLoc, String text, String fontName, String fontStyle, int fontSize, double tracking,
             double rotation, boolean centered) {
      this.text = text;
      this.fontName = fontName;
      this.fontStyle = fontStyle;
      this.fontSize = fontSize;
      this.tracking = tracking;
      setLocationAndOrientation(xLoc, yLoc, rotation, centered);
    }

    @Override
    String[] getParameterNames () {
      StringBuilder fontNames = new StringBuilder("fontName");
      for (String font : fonts) {
        fontNames.append(":");
        fontNames.append(font);
      }
      return new String[]{"text", fontNames.toString(), "fontStyle:plain:bold:italic:bold-italic", "fontSize|pts", "tracking"};
    }

    @Override
    Shape buildShape () {
      if (fontName.startsWith("Vector")) {
        Path2D.Double path = new Path2D.Double();
        VectorFont font = VectorFont.getFont(fontName);
        int[][][] stroke = font.font;
        int lastX = 1000, lastY = 1000;
        int xOff = 0;
        for (int ii = 0; ii < text.length(); ii++) {
          char cc = text.charAt(ii);
          cc = cc >= 32 & cc <= 127 ? cc : '_';   // Substitute '_' for codes outside printable ASCII range
          if (cc >= 32 && cc <= 127) {
            int[][] glyph = stroke[cc - 32];
            int left = glyph[0][0];
            int right = glyph[0][1];
            for (int jj = 1; jj < glyph.length; jj++) {
              int x1 = glyph[jj][0] - left;
              int y1 = glyph[jj][1];
              int x2 = glyph[jj][2] - left;
              int y2 = glyph[jj][3];

              if (x1 != lastX || y1 != lastY) {
                path.moveTo(x1 + xOff, y1);
              }
              path.lineTo(x2 + xOff, lastY = y2);
              lastX = x2;
            }
            int step = right - left;
            xOff += step;
          }
        }
        AffineTransform at = new AffineTransform();
        double scale = fontSize / (72.0 * font.height);
        at.scale(scale, scale);
        Shape text = at.createTransformedShape(path);
        Rectangle2D bounds = text.getBounds2D();
        at = new AffineTransform();
        at.translate(-bounds.getX(), -bounds.getY());
        text = at.createTransformedShape(text);
        bounds = text.getBounds2D();
        at = new AffineTransform();
        at.translate(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
        return at.createTransformedShape(text);
      } else {
        // Code from: http://www.java2s.com/Tutorial/Java/0261__2D-Graphics/GenerateShapeFromText.htm
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Font font = new Font(fontName, styles.get(fontStyle), fontSize);
        HashMap<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attrs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        attrs.put(TextAttribute.TRACKING, tracking);
        font = font.deriveFont(attrs);
        g2.setFont(font);
        try {
          GlyphVector vect = font.createGlyphVector(g2.getFontRenderContext(), text);
          AffineTransform at = new AffineTransform();
          at.scale(1 / 72.0, 1 / 72.0);
          Shape text = at.createTransformedShape(vect.getOutline());
          Rectangle2D bounds = text.getBounds2D();
          at = new AffineTransform();
          at.translate(-bounds.getX(), -bounds.getY());
          text = at.createTransformedShape(text);
          bounds = text.getBounds2D();
          at = new AffineTransform();
          at.translate(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
          return at.createTransformedShape(text);
        } finally {
          g2.dispose();
        }
      }
    }
  }

  static class CADScaledShape extends CADShape implements Serializable {
    public double   scale = 100.0;

    CADScaledShape (Shape shape, double xLoc, double yLoc, double rotation, boolean centered, double scale) {
      super(shape, xLoc, yLoc, rotation, centered);
      this.scale = scale;
    }

    @Override
    // Translate Shape to screen position
    protected Shape getWorkspaceTranslatedShape () {
      AffineTransform at = new AffineTransform();
      at.translate(xLoc, yLoc);
      at.scale(scale / 100.0, scale / 100.0);
      return at.createTransformedShape(getLocallyTransformedShape());
    }

    @Override
    protected List<String> getPlaceFields () {
      ArrayList<String> list = new ArrayList(super.getPlaceFields());
      list.add("scale|%");
      return list;
    }

    @Override
    protected List<String> getEditFields () {
      ArrayList<String> list = new ArrayList(super.getEditFields());
      list.add("scale|%");
      return list;
    }
  }

  static class CADShapeSpline extends CADShape implements Serializable {
    private static final long serialVersionUID = 1175193935200692376L;
    private List<Point2D.Double>  points = new ArrayList<>();
    private Point2D.Double        movePoint;
    private boolean               closePath;
    private Path2D.Double         path = new Path2D.Double();

    CADShapeSpline () {
      centered = true;
    }

    @Override
    protected List<String> getEditFields () {
      return Arrays.asList("xLoc|in", "yLoc|in", "rotation|deg");
    }

    @Override
    boolean selectMovePoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint) {
      // See if we clicked on an existing Catmull-Rom Control Point other than origin
      Point2D.Double mse = new Point2D.Double(point.x - xLoc, point.y - yLoc);
      for (int ii = 0; ii < points.size(); ii++) {
        Point2D.Double cp = points.get(ii);
        Point2D.Double np = rotatePoint(cp, rotation);
        double dist = mse.distance(np.x, np.y) * LaserCut.SCREEN_PPI;
        if (dist < 5) {
          if (ii == 0 && !closePath) {
            surface.pushToUndoStack();
            closePath = true;
            updatePath();
          } else {
          }
          movePoint = cp;
          return true;
        }
      }
      int idx;
      if (closePath && (idx = getInsertionPoint(point)) >= 0) {
        surface.pushToUndoStack();
        points.add(idx + 1, movePoint = rotatePoint(new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
        updatePath();
        return true;
      }
      if (!closePath) {
        surface.pushToUndoStack();
        points.add(rotatePoint(movePoint = new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
        updatePath();
        return true;
      }
      return false;
    }

    int getInsertionPoint (Point2D.Double point) {
      Point2D.Double mse = rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
      int idx = 1;
      Point2D.Double chk = points.get(idx);
      for (Line2D.Double[] lines : transformShapeToLines(getShape(), 1)) {
        for (Line2D.Double line : lines) {
          double dist = line.ptSegDist(mse);
          if (dist < 5 / SCREEN_PPI) {
            return idx - 1;
          }
          if (idx < points.size() && chk.distance(line.getP2()) < .000001) {
            chk = points.get(Math.min(points.size() - 1, ++idx));
          }
        }
      }
      return -1;
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
      updateShape();
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
    void draw (Graphics g, double zoom) {
      super.draw(g, zoom);
      Graphics2D g2 = (Graphics2D) g;
      // Draw all Catmull-Rom Control Points
      g2.setColor(isSelected ? Color.red : closePath ? Color.lightGray : Color.darkGray);
      for (Point2D.Double cp : points) {
        Point2D.Double np = rotatePoint(cp, rotation);
        double mx = (xLoc + np.x) * zoom;
        double my = (yLoc + np.y) * zoom;
        double mWid = 2 * zoom / LaserCut.SCREEN_PPI;
        g2.fill(new Rectangle.Double(mx - mWid, my - mWid, mWid * 2, mWid * 2));
      }
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
    void draw (Graphics g, double zoom) {
      super.draw(g, zoom);
      Graphics2D g2 = (Graphics2D) g;
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

  static class CNCPath extends CADShape implements Serializable, ChangeListener {
    private static final long serialVersionUID = 940023721688314265L;
    private CADShape  baseShape;
    public double     radius;
    public boolean    inset;

    CNCPath (CADShape base, double radius, boolean inset) {
      this.baseShape = base;
      this.radius = Math.abs(radius);
      this.inset = inset;
      baseShape.addChangeListener(this);
    }

    /*
     * Reattach ChangeListener after deserialization
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      baseShape.addChangeListener(this);
    }

    public void shapeChanged (CADShape base) {
      updateShape();
    }

    @Override
    protected List<String> getEditFields () {
      return Arrays.asList("radius|in{radius of tool}", "inset{If checked, toolpath is inside shape, else outside}");
    }

    @Override
    Color getShapeColor (boolean highlight, boolean engrave) {
      return new Color(0, 153, 0);
    }

      @Override
    BasicStroke getShapeStroke (boolean highlight, boolean isSelected) {
      return new BasicStroke(highlight ? isSelected ? 1.8f : 1.4f : 1.0f, BasicStroke.CAP_BUTT,
                             BasicStroke.JOIN_MITER,  5.0f, new float[] {5.0f}, 0.0f);
    }

    @Override
    protected Shape getLocallyTransformedShape () {
      Shape dShape = getShape();
      AffineTransform at = new AffineTransform();
      // Position Shape centered on xLoc/yLoc in inches (x from left, y from top)
      at.rotate(Math.toRadians(rotation));
      if (!centered) {
        // Translate relative to the baseShape's coordinates so the generated cnc path aligns with it
        Rectangle2D bounds = baseShape.getShape().getBounds2D();
        at.translate(bounds.getWidth() / 2, bounds.getHeight() / 2);
      }
      return at.createTransformedShape(dShape);
    }

    @Override
    Shape buildShape () {
      xLoc = baseShape.xLoc;
      yLoc = baseShape.yLoc;
      centered = baseShape.centered;
      rotation = baseShape.rotation;
      Path2D.Double path = new Path2D.Double();
      boolean first = true;
      for (Line2D.Double[] lines : transformShapeToLines(baseShape.getShape(), 1.0)) {
        Point2D.Double[] points = CNCTools.getParallelPath(lines, radius, !inset);
        for (Point2D.Double point : points) {
          if (first) {
            path.moveTo(point.x, point.y);
            first = false;
          } else {
            path.lineTo(point.x, point.y);
          }
        }
        // Connect back to beginning
        path.lineTo(points[0].x, points[0].y);
        first = true;
      }
      return path;
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

    ArrayList<CADShape> getShapesInGroup () {
      return shapesInGroup;
    }
  }

  /*
   * * * * * * * Implemented Interfaces * * * * * * * *
   */

  interface ShapeSelectListener {
    void shapeSelected (CADShape shape, boolean selected);
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