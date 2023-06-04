import jssc.SerialNativeInterface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
//import java.awt.desktop.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
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

  Marlin Renderer: https://github.com/bourgesl/marlin-renderer

  Fun with Java2D - Strokes:
    http://www.jhlabs.com/java/java2d/strokes/
  */

public class LaserCut extends JFrame {
  static final String                 VERSION = "1.2 beta";
  static final String                 USER = "wholder";
  static final double                 SCREEN_PPI = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
  static final DecimalFormat          df = new DecimalFormat("#0.0###");
  private static final int            cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
  private final transient Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
  DrawSurface surface;
  private final JScrollPane           scrollPane;
  private final JMenuBar              menuBar = new JMenuBar();
  private JMenuItem                   gerberZip;
  private int                         pxDpi = prefs.getInt("svg.pxDpi", 96);
  private long                        savedCrc;
  private boolean                     useMouseWheel = prefs.getBoolean("useMouseWheel", false);
  private boolean                     snapToGrid = prefs.getBoolean("snapToGrid", true);
  private boolean                     displayGrid = prefs.getBoolean("displayGrid", true);
  private String                      onStartup = prefs.get("onStartup", "demo");
  private OutputDevice                outputDevice;
  private final int                   deviceMenuSlot;
  private final List<CADShape>        shapeClasses = new ArrayList<>();

  {
    try {
      shapeClasses.add(new CADReference());
      shapeClasses.add(new CADRectangle());
      shapeClasses.add(new CADOval());
      shapeClasses.add(new CADPolygon());
      shapeClasses.add(new CADArbitraryPolygon());
      shapeClasses.add(new CADShapeSpline());
      shapeClasses.add(new CADText());
      shapeClasses.add(new CADRasterImage());
      shapeClasses.add(new CADGear());
      shapeClasses.add(new CADNemaMotor());
      shapeClasses.add(new CADBobbin());
      if (USER.equals(System.getProperty("user.name"))) {
        shapeClasses.add(new CADMusicStrip());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  interface OutputDevice {
    String getName ();

    JMenu getDeviceMenu ();

    void closeDevice () throws Exception;

    Rectangle2D.Double getWorkspaceSize ();

    double getZoomFactor ();
  }

  public Preferences getPreferences () {
    return prefs;
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
    Map<String, ParameterDialog.ParmItem> items = new LinkedHashMap<>();
    items.put("onStartup", new ParameterDialog.ParmItem("On Startup:Blank Page|blank:Reopen Last File|reopen:Demo Page|demo",
                                                         onStartup));
    String device = Integer.toString(prefs.getInt("outputDevice", 0));
    items.put("outputDevice", new ParameterDialog.ParmItem("Output Device:None|0:Epilog Zing|1:Mini Laser|2:" +
      "Micro Laser|3:MiniCNC|4:Silhouette|5:Mini Cutter|6", device));
    items.put("useMouseWheel", new ParameterDialog.ParmItem("Mouse Wheel Scrolling", prefs.getBoolean("useMouseWheel", false)));
    items.put("useDblClkZoom", new ParameterDialog.ParmItem("Double-click Zoom{Dbl click to Zoom 2x, Shift + dbl click to unZoom}",
      prefs.getBoolean("useDblClkZoom", false)));
    if (USER.equals(System.getProperty("user.name"))) {
      items.put("enableGerber", new ParameterDialog.ParmItem("Enable Gerber ZIP Import",
                prefs.getBoolean("gerber.import", false)));
    }
    items.put("macMenuBar", new ParameterDialog.ParmItem("Enable Mac Menu Bar", prefs.getBoolean("macMenuBar", false)));
    items.put("pxDpi", new ParameterDialog.ParmItem("px per Inch (SVG Import/Export)", prefs.getInt("svg.pxDpi", 96)));
    ParameterDialog.ParmItem[] parmSet = items.values().toArray(new ParameterDialog.ParmItem[0]);
    ParameterDialog dialog = (new ParameterDialog("LaserCut Preferences", parmSet, new String[]{"Save", "Cancel"},
                              prefs.get("displayUnits", "in")));
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);              // Note: this call invokes dialog
    if (dialog.wasPressed()) {
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
        } else if ("macMenuBar".equals(name)) {
          boolean enabled = (Boolean) parm.value;
          boolean restart = enabled != prefs.getBoolean("macMenuBar", false);
          prefs.putBoolean("macMenuBar", (Boolean) parm.value);
          System.setProperty("apple.laf.useScreenMenuBar", enabled ? "true" : "false");
          if (restart) {
            showErrorDialog("Restart LaserCut for Manu Bar change");
          }
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

  //   Note: unable to use CMD-A, CMD-C, CMD-H, CMD-Q as shortcut keys
  static class MyMenuItem extends JMenuItem {
    private static final Map<String, String> keys = new HashMap<>();

    MyMenuItem (String name, int key) {
      this(name, key, cmdMask, true);
    }

    MyMenuItem (String name, int key, boolean enabled) {
      this(name, key, cmdMask);
      setEnabled(enabled);
    }

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
    // Set Icon image for LaserCut
    if (Taskbar.isTaskbarSupported()) {
      Taskbar taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        taskbar.setIconImage(new ImageIcon(getClass().getResource("/images/LaserCut Logo.png")).getImage());
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
            if (quitHandler() && showWarningDialog("Really quit?")) {
              response.performQuit();
            } else {
              response.cancelQuit();
            }
          }
        });
      }
      // Display "About Box" in Mac Menu Bar
      if (prefs.getBoolean("macMenuBar", false)) {
        if (hasPreferencesHandler = desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
          desktop.setPreferencesHandler(new PreferencesHandler() {
            @Override
            public void handlePreferences (PreferencesEvent e) {
              showPreferencesBox();
            }
          });
        }
      }
    }
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing (WindowEvent e) {
        if (quitHandler() && showWarningDialog("Really exit?")) {
          System.exit(0);
        }
      }
    });
*/
    // Use Mac Menu Bar
    System.setProperty("apple.laf.useScreenMenuBar", prefs.getBoolean("macMenuBar", false) ? "true" : "false");
    // Add Menu Bar
    setJMenuBar(menuBar);
    surface.addPlacerListener(placing -> {
      for (int ii = 0; ii < menuBar.getMenuCount(); ii++) {
        menuBar.getMenu(ii).setEnabled(!placing);
      }
    });
    /*
     *  Add "File" Menu
     */
    JMenu fileMenu = new JMenu("File");
    menuBar.add(fileMenu);
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
      preferencesBox.addActionListener(ev -> showPreferencesBox());
      fileMenu.add(preferencesBox);
    }
    fileMenu.addSeparator();
    //
    // Add "New" Item to File Menu
    //
    JMenuItem newObj = new MyMenuItem("New LaserCut Project", KeyEvent.VK_N);
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
    FileChooserMenu openMenu = new FileChooserMenu(this, "Open LaserCut File", "lzr", false, true) {
      void processFile (File oFile) throws Exception {
        if (showWarningDialog("Discard current design?")) {
          surface.pushToUndoStack();
          SurfaceSettings settings = loadLaserCutFile(oFile);
          surface.setDesign(settings);
          setTitle("LaserCut - " + oFile.getAbsolutePath());
          savedCrc = surface.getDesignChecksum();
          prefs.put("default.dir", oFile.getAbsolutePath());
          prefs.put("lastFile", oFile.getAbsolutePath());
          setTitle("LaserCut - (" + oFile + ")");
        }
      }
      @Override
      BufferedImage getPreview (File file) throws Exception {
        SurfaceSettings settings = loadLaserCutFile(file);
        List<CADShape> shapes = settings.getDesign();
        List<Shape> shps = new ArrayList<>();
        for (CADShape shape : shapes) {
          shps.add(shape.getWorkspaceTranslatedShape());
        }
        return Utils2D.getPreviewImage(shps);
      }
    };
    openMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, cmdMask));
    fileMenu.add(openMenu);
    //
    // Add "Save As" Item to File menu
    //
    FileChooserMenu saveAsMenu = new FileChooserMenu(this, "Save LaserCut File As", "lzr", true, false) {
      void processFile (File sFile) throws Exception {
        writeToLaserCutFile(sFile, false);
        savedCrc = surface.getDesignChecksum();
        setTitle("LaserCut - " + sFile);
        prefs.put("lastFile", sFile.getAbsolutePath());
        prefs.put("default.dir", sFile.getAbsolutePath());
      }
    };
    saveAsMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, cmdMask));
    fileMenu.add(saveAsMenu);
    //
    // Add "Save Selected As" Item to File menu
    //
    FileChooserMenu saveSelectedMenu = new FileChooserMenu(this, "Save Selected As", "lzr", true, false) {
      void processFile (File sFile) throws Exception {
        writeToLaserCutFile(sFile, true);
        savedCrc = surface.getDesignChecksum();
        setTitle("LaserCut - " + sFile);
        prefs.put("lastFile", sFile.getAbsolutePath());
        prefs.put("default.dir", sFile.getAbsolutePath());
      }
    };
    saveSelectedMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, cmdMask));
    fileMenu.add(saveSelectedMenu);
    //
    // Add "Quit" Item to File menu
    //
    if (!hasQuitHandler) {
      // Add "Quit" Item to File menu
      JMenuItem quitObj = new MyMenuItem("Quit", KeyEvent.VK_Q);
      quitObj.addActionListener(ev -> {
        if (quitHandler()) {
          System.exit(0);
        }
      });
      fileMenu.add(quitObj);
    }
    /*
     *  Add "Shapes" Menu
     */
    JMenu shapesMenu = new JMenu("Shapes");
    menuBar.add(shapesMenu);
    for (CADShape shapeClass : shapeClasses) {
      JMenuItem mItem = new JMenuItem(shapeClass.getMenuName());
      mItem.addActionListener(ev -> {
        try {
          CADShape shp = shapeClass.getClass().newInstance();
          shp.createAndPlace(surface, this, prefs);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      shapesMenu.add(mItem);
    }
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
    menuBar.add(editMenu);

    //
    // Add "Undo" Menu Item
    //
    JMenuItem undo = new MyMenuItem("Undo", KeyEvent.VK_Z, false);
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
    JMenuItem removeSelected = new MyMenuItem("Delete Selected", KeyEvent.VK_X, false);
    removeSelected.addActionListener((ev) -> surface.removeSelected());
    editMenu.add(removeSelected);
    //
    // Add "Duplicate Selected" Menu Item
    //
    JMenuItem dupSelected = new MyMenuItem("Duplicate Selected", KeyEvent.VK_D, false);
    dupSelected.addActionListener((ev) -> surface.duplicateSelected());
    editMenu.add(dupSelected);
    //
    // Add "Edit Selected" Menu Item
    //
    JMenuItem editSelected = new MyMenuItem("Edit Selected", KeyEvent.VK_E, false);
    editSelected.addActionListener((ev) -> {
      CADShape sel = surface.getSelected();
      if (sel.editParameterDialog(surface, prefs.get("displayUnits", "in"))) {
        // User clicked dialog's OK button
        sel.updateShape();
        surface.repaint();
      }
    });
    editMenu.add(editSelected);
    //
    // Add "Move Selected" Menu Item
    //
    JMenuItem moveSelected = new MyMenuItem("Move Selected", KeyEvent.VK_M, false);
    moveSelected.addActionListener((ev) -> surface.moveSelected());
    editMenu.add(moveSelected);
    //
    // Add "Group Selected" Menu Item
    //
    JMenuItem groupDragSelected = new MyMenuItem("Group Selected", KeyEvent.VK_G, false);
    groupDragSelected.addActionListener((ev) -> surface.groupDragSelected());
    editMenu.add(groupDragSelected);
    //
    // Add "Ungroup Selected" Menu Item
    //
    JMenuItem unGroupSelected = new MyMenuItem("Ungroup Selected", KeyEvent.VK_U, false);
    unGroupSelected.addActionListener((ev) -> surface.unGroupSelected());
    editMenu.add(unGroupSelected);
    //
    // Add "Add Grouped Shapes" Menu Item
    //
    JMenuItem addSelected = new MyMenuItem("Add Grouped Shape{s) to Selected Shape", KeyEvent.VK_A, false);
    addSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(true));
    editMenu.add(addSelected);
    //
    // Add "Subtract Group from Selected" Menu Item
    //
    JMenuItem subtractSelected = new MyMenuItem("Subtract Grouped Shape(s) from Selected Shape", KeyEvent.VK_T, false);
    subtractSelected.addActionListener((ev) -> surface.addOrSubtractSelectedShapes(false));
    editMenu.add(subtractSelected);
    //
    // Add "Combine Selected Paths" Menu Item
    //
    JMenuItem combineDragSelected = new MyMenuItem("Combine Selected Paths", KeyEvent.VK_C, false);
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
    JMenuItem rotateSelected = new MyMenuItem("Rotate Group Around Selected Shape's Origin", KeyEvent.VK_R, false);
    rotateSelected.addActionListener((ev) -> surface.rotateGroupAroundSelected());
    editMenu.add(rotateSelected);
    //
    // Add "Round Corners of Selected Shape" Menu Item
    //
    JMenuItem roundCorners = new MyMenuItem("Round Corners of Selected Shape", KeyEvent.VK_B, false);
    roundCorners.addActionListener((ev) -> {
      ParameterDialog.ParmItem[] rParms = {new ParameterDialog.ParmItem("radius|in", 0d)};
      ParameterDialog rDialog = (new ParameterDialog("Edit Parameters", rParms, new String[]{"Round", "Cancel"}, prefs.get("displayUnits", "in")));
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
    // Add SelectListener to enable/disable menus, as needed
    //
    surface.addSelectListener((shape, selected) -> {
      removeSelected.setEnabled(selected);
      dupSelected.setEnabled(selected);
      editSelected.setEnabled(selected);
      moveSelected.setEnabled(selected);
      roundCorners.setEnabled(selected);
      saveSelectedMenu.setEnabled(selected);
      boolean hasGroup = surface.getSelected() != null && surface.getSelected().getGroup() != null;
      unGroupSelected.setEnabled(hasGroup);
      addSelected.setEnabled(hasGroup);
      alignMenu.setEnabled(hasGroup);
      subtractSelected.setEnabled(hasGroup);
      rotateSelected.setEnabled(hasGroup);
    });
    surface.addDragSelectListener((selected) -> {
      saveSelectedMenu.setEnabled(selected);
      moveSelected.setEnabled(selected);
      removeSelected.setEnabled(selected);
      combineDragSelected.setEnabled(selected);
      groupDragSelected.setEnabled(selected);
      unGroupSelected.setEnabled(selected);
      dupSelected.setEnabled(selected);
    });
    /*
     *  Add "Import" Menu
     */
    JMenu importMenu = new JMenu("Import");
    menuBar.add(importMenu);
    //
    // Add "Import LaserCut File" Item to File menu
    //
    importMenu.add(new FileChooserMenu(this, "Import LaserCut file", "lzr", false, true) {
      void processFile (File sFile) throws Exception {
        SurfaceSettings settings = loadLaserCutFile(sFile);
        surface.placeLaserCutFile(settings);
      }
      @Override
      BufferedImage getPreview (File file) throws Exception {
        SurfaceSettings settings = loadLaserCutFile(file);
        List<CADShape> shapes = settings.getDesign();
        List<Shape> shps = new ArrayList<>();
        for (CADShape shape : shapes) {
          shps.add(shape.getWorkspaceTranslatedShape());
        }
        return Utils2D.getPreviewImage(shps);
      }
    });
    //
    // Add "Import SVG File" menu item
    //
    importMenu.add(new FileChooserMenu(this, "Import SVG file", "svg", false, true) {
      void processFile (File sFile) throws Exception {
        SVGParser parser = new SVGParser();
        parser.setPxDpi(pxDpi);
        CADShape shp = parser.parseSvgFile(sFile);
        if (shp.placeParameterDialog(surface, prefs.get("displayUnits", "in"))) {
          surface.placeShape(shp);
        } else {
          surface.setInfoText("Import cancelled");
        }
      }
      @Override
      BufferedImage getPreview (File file) throws Exception {
        SVGParser parser = new SVGParser();
        parser.setPxDpi(pxDpi);
        CADShape shp = parser.parseSvgFile(file);
        Shape shape = shp.getWorkspaceTranslatedShape();
        return Utils2D.getPreviewImage(shape);
      }
    });
    //
    // Add "Import DXF File" menu item
    //
    importMenu.add(new DXFReader.DxfFileChooserMenu(this, "Import DXF file", "dxf", false, true) {
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
        List<CADShape> list = dxf.readDxfFile(sFile);
        surface.placeShapes(list);
      }
      @Override
      BufferedImage getPreview (File file) throws Exception {
        DXFReader dxf = new DXFReader("in");
        List<CADShape> shapes = dxf.readDxfFile (file);
        Shape shape = Utils2D.listOfCADShapesToArea(shapes);
        return Utils2D.getPreviewImage(shape);
      }
    });
    //
    // Add "Import Gerber Zip" menu item
    //
    if (USER.equals(System.getProperty("user.name"))) {
      importMenu.add(gerberZip = new FileChooserMenu(this, "Import Gerber Zip file", "zip", false, false) {
        void processFile (File sFile) throws Exception {
          GerberZip gerber = new GerberZip(sFile);
          surface.placeShapes(gerber.getShapes());
        }
      });
    }
    /*
     *  Add "Import Notes to MusicBox Strip" Menu
     */
    if (USER.equals(System.getProperty("user.name"))) {
      importMenu.add(new FileChooserMenu(this, "Import MusicBox file", "mus", false, false) {
        void processFile (File sFile) throws Exception {
          CADMusicStrip mStrip = new CADMusicStrip();
          mStrip.readMusicBoxFile(sFile);
          if (mStrip.placeParameterDialog(surface, prefs.get("displayUnits", "in"))) {
            surface.placeShape(mStrip);
          } else {
            surface.setInfoText("Import cancelled");
          }
        }
      });
    }
    /*
     *  Add "Export" Menu
     */
    JMenu exportMenu = new JMenu("Export");
    menuBar.add(exportMenu);
    //
    // Add "Export to PDF File" Menu Item
    //
    exportMenu.add(new FileChooserMenu(this, "Export PDF file", "pdf", true, false) {
      void processFile (File sFile) throws Exception {
        PDFTools.writePDF(surface.getDesign(), surface.getWorkSize(), sFile);
      }
    });
    //
    // Add "Export to SVG File"" Menu Item
    //
    exportMenu.add(new FileChooserMenu(this, "Export SVG file", "svg", true, false) {
      void processFile (File sFile) throws Exception {
        SVGParser.saveSvgFile(sFile, surface.getDesign(), surface.getWorkSize(), pxDpi);
      }
    });
    //
    // Add "Export to DXF File"" Menu Item
    // Based on: https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/
    //  by Jonathan Sevy (released under the MIT License; https://choosealicense.com/licenses/mit/)
    //
    exportMenu.add(new DXFReader.DxfFileChooserMenu(this, "Export DXF file", "dxf", true, false) {
      void processFile (File sFile) throws Exception {
        String units = getSelectedUnits();
        DXFReader.writeDxfFile(sFile, surface.getDesign(), units);
      }
    });
    //
    // Add "Export to EPS File"" Menu Item
    //
    exportMenu.add(new FileChooserMenu(this, "Export EPS file", "eps", true, false) {
      void processFile (File sFile) throws Exception {
        EPSWriter.writeEpsFile(sFile, surface.getDesign());
      }
    });
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
    menuBar.add(unitsMenu);
    unitsMenu.setToolTipText("Select units to be used in controls");
    ButtonGroup unitGroup = new ButtonGroup();
    String[] unitSet = {"Inches:in", "Centimeters:cm", "Millimeters:mm"};
    surface.setUnits(prefs.get("displayUnits", "in"));
    for (String unit : unitSet) {
      String[] parts = unit.split(":");
      boolean select = parts[1].equals(prefs.get("displayUnits", "in"));
      JMenuItem uItem = new JRadioButtonMenuItem(parts[0], select);
      unitGroup.add(uItem);
      unitsMenu.add(uItem);
      uItem.addActionListener(ev -> {
        prefs.put("displayUnits", parts[1]);
        surface.setUnits(parts[1]);
      });
    }
    /*
     * Track window move events and save in prefs
    */
    addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev) {
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
      File tFile = new File(reopen);
      try {
        SurfaceSettings settings = loadLaserCutFile(tFile);
        surface.setDesign(settings);
        setTitle("LaserCut - " + tFile.getAbsolutePath());
      } catch (Exception ex) {
        showErrorDialog("Error loading LaserCut file");
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
      surface.addShape(new CADShape(a1, 5.75, 2.5, 0));
      // Add two concentric circles in center
      CADShape circle1 = new CADOval(5.75, 2.5, 1.20, 1.20, 0);
      CADShape circle2 = new CADOval(5.75, 2.5, 0.60, 0.60, 0);
      CADShapeGroup group = new CADShapeGroup();
      group.addToGroup(circle1);
      group.addToGroup(circle2);
      surface.addShape(circle1);
      surface.addShape(circle2);
      // Add RoundedRectange
      surface.addShape(new CADRectangle(.25, .25, 4, 4, .25, 0));
      // Add 72 Point, 1 inch tall Text
      surface.addShape(new CADText(4.625, .25, "Belle", "Helvetica", "bold", 72, 0, 0));
      // Add Test Gear
      surface.addShape(new CADGear(2.25, 2.25, .1, 30, 10, 20, .25, 0, Utils2D.mmToInches(3)));
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
          outputDevice = new ZingLaser(this, prefs);
          break;
        case 2:
          outputDevice = new MiniLaser(this, prefs);
          break;
        case 3:
          outputDevice = new MicroLaser(this, prefs);
          break;
/*
        case 4:
          outputDevice = new MiniCNC(this, prefs);
          break;
*/
        case 5:
          outputDevice = new Silhouette(this, prefs);
          break;
        case 6:
          outputDevice = new MiniCutter(this, prefs);
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
        JTextArea textArea = new JTextArea(30, 122);
        textArea.setMargin(new Insets(2, 4, 4, 2));
        textArea.setFont(new Font("Courier", Font.PLAIN, 12));
        textArea.setTabSize(4);
        JScrollPane scrollPane1 = new JScrollPane(textArea);
        textArea.setText(errorMsg);
        textArea.setEditable(false);
        textArea.setCaretPosition(0);
        showMessageDialog(this, scrollPane1);
      }
      // Disable output device for future restarts
      prefs.putInt("outputDevice", 0);
      return null;
    }
    return menu;
  }

  private static java.lang.reflect.Field getField (Class<CADShape> mClass, String fieldName) throws NoSuchFieldException {
    try {
      return mClass.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<CADShape> superClass = (Class<CADShape>) mClass.getSuperclass();
      if (superClass == null) {
        throw e;
      } else {
        return getField(superClass, fieldName);
      }
    }
  }

  private static void setFieldValue (Object object, String fieldName, Object valueTobeSet) throws NoSuchFieldException, IllegalAccessException {
    java.lang.reflect.Field field = getField((Class<CADShape>) object.getClass(), fieldName);
    field.setAccessible(true);
    field.set(object, valueTobeSet);
  }

  //  Size1: 16,695 nytes (with SurfaceSettings)
  //  Size2: 16,494 bytes (without SurfaceSettings)
  private void writeToLaserCutFile (File saveFile, boolean selected) {
    try {
      String fPath = saveFile.getPath();
      if (!fPath.contains(".")) {
        saveFile = new File(fPath + ".lzr");
      }
      FileOutputStream fileOut = new FileOutputStream(saveFile);
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      List<CADShape> design = selected ? surface.getSelectedAsDesign() : surface.getDesign();
      out.writeObject(design);
      // Save DrawSurface setting and JScrollPane position
      JViewport viewPort = scrollPane.getViewport();
      SurfaceSettings settings = new SurfaceSettings(surface, viewPort);
      out.writeObject(settings);
      out.close();
      fileOut.close();
    } catch (Exception ex) {
      showErrorDialog("Unable to save LaserCut file");
      ex.printStackTrace();
    }
  }

  private static SurfaceSettings loadLaserCutFile (File readFile) throws Exception {
    SurfaceSettings settings = null;
    FileInputStream fileIn = new FileInputStream(readFile);
    ObjectInputStream in = new FixInputStream(fileIn);
    List<CADShape> design = (ArrayList<CADShape>) in.readObject();
    try {
      // Read DrawSurface setting and JScrollPane position, if available
      Object obj = in.readObject();
      settings = (SurfaceSettings) obj;
    } catch (Exception ex) {
      // Ignore (catching EOFException is the only way to tell if SurfaceSettings object was serialized)
      //ex.printStackTrace();
    }
    in.close();
    fileIn.close();
    if (settings == null) {
      //
      // Need code here XY offsets to make old files compatible with new ones
      //
      for (CADShape shape : design) {
        Rectangle2D bnds = shape.getShapeBounds();
        if (!shape.centered) {
          shape.xLoc = shape.xLoc + bnds.getWidth() / 2;
          shape.yLoc = shape.yLoc + bnds.getHeight() / 2;
        }
      }
      settings = new SurfaceSettings();
    }
    settings.setDesign(design);
    //System.out.println(settings);
    return settings;
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

    protected ObjectStreamClass readClassDescriptor () throws IOException, ClassNotFoundException {
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

  /*
   * Pop Up Message handlers
   */

  public boolean showWarningDialog (String msg) {
    return showConfirmDialog(this, msg, "Warning", YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION;
  }

  public void showErrorDialog (String msg) {
    showMessageDialog(this, msg, "Error", PLAIN_MESSAGE);
  }

  public void showInfoDialog (String msg) {
    showMessageDialog(this, msg);
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
    //System.setProperty("apple.laf.useScreenMenuBar", "true");
    //System.setProperty("com.apple.mrj.application.apple.menu.about.name", "LaserCut");
    //System.setProperty("apple.awt.textantialiasing", "true");
    //System.setProperty("apple.awt.graphics.EnableQ2DX","true");
    SwingUtilities.invokeLater(LaserCut::new);
  }
}