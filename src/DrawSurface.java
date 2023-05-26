import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.zip.CRC32;

import static javax.swing.JOptionPane.showMessageDialog;

public class DrawSurface extends JPanel {
  private final Preferences                   prefs;
  private Dimension                           workSize;
  private JTextField                          infoText;
  private List<CADShape>                      shapes = new ArrayList<>();
  private CADShape                            selected, dragged, resizeOrRotate;
  private Placer                              placer;
  private PlacerListener                      placerListener;
  private double                              gridSpacing = 0.1;
  private int                                 gridMajor;
  private JMenu                               gridMenu;
  private final double[]                      zoomFactors = {1, 2, 3, 4, 8, 16};
  private double                              zoomFactor = 1;
  private Point2D.Double                      scrollPoint, measure1, measure2, dragStart;
  private Rectangle2D.Double                  dragBox;
  private boolean                             useDblClkZoom;
  private final List<CADShape>                dragList = new ArrayList<>();
  private final List<LaserCut.ShapeSelectListener>  selectListerners = new ArrayList<>();
  private final List<LaserCut.ShapeDragSelectListener>  dragSelectListerners = new ArrayList<>();
  private final List<LaserCut.ActionUndoListener>   undoListerners = new ArrayList<>();
  private final List<LaserCut.ActionRedoListener>   redoListerners = new ArrayList<>();
  private final List<LaserCut.ZoomListener>         zoomListeners = new ArrayList<>();
  private final LinkedList<byte[]>                  undoStack = new LinkedList<>();
  private final LinkedList<byte[]>                  redoStack = new LinkedList<>();
  private boolean                             pushedToStack, showMeasure, doSnap, showGrid;
  private String                              dUnits;
  private int                                 tipTimer;
  private boolean                             mouseDown = false;
  private Point2D.Double                      tipLoc;
  private String                              tipText;
  private boolean                             keyShift;     // Shift key down
  private boolean                             keyCtrl;      // Control key down
  private boolean                             keyOption;    // Mac Option key, else ALT key down
  private boolean                             keyMeta;      // Mac CMD key down

  /**
   *  State keys mapping
   *    Shift:    Diaplay Resize drag point
   *    Control:  Display Rotate drag point
   *    Option:   See code
   *    Meta:     See code
   */

  static class Placer {
    private final List<CADShape>    shapes;
    private Point2D.Double          curLoc = new Point2D.Double(0, 0);

    Placer (List<CADShape> shapes) {
      this.shapes = shapes;
    }

    void setPlacePosition (Point2D.Double newLoc) {
      Point2D.Double delta = new Point2D.Double(newLoc.getX() - curLoc.getX(), newLoc.getY() - curLoc.getY());
      for (CADShape shape : shapes) {
        shape.movePosition(delta);
      }
      curLoc = newLoc;
    }

    void draw (Graphics2D g2, double scale) {
      for (CADShape shape : shapes) {
        shape.draw(g2, scale, false, false, false);
      }
    }

    void addToSurface (DrawSurface surface) {
      surface.addShapes(shapes);
      if (shapes.size() > 0) {
        surface.setSelected(shapes.get(0));
      }
    }
  }

  interface PlacerListener {
    void placeActive (boolean placing);
  }

  void addPlacerListener (PlacerListener listener) {
    placerListener = listener;
  }

  private void setPlacerActive (boolean placing) {
    if (placerListener != null)  {
      placerListener.placeActive(placing);
    }
  }

  DrawSurface (Preferences prefs, JScrollPane scrollPane) {
    super(true);
    this.prefs = prefs;
    useDblClkZoom = prefs.getBoolean("useDblClkZoom", false);
    DrawSurface thisSurface = this;
    // Set JPanel size to a temprary default size
    setPreferredSize(workSize = new Dimension(500, 500));
    // Implement KeyListener to track state of shift key
    addKeyListener(new KeyAdapter() {
      @Override
         public void keyPressed (KeyEvent ev) {
        switch (ev.getKeyCode()) {
          case KeyEvent.VK_SHIFT:
            keyShift = true;
            repaint();
            break;
          case KeyEvent.VK_CONTROL:
            keyCtrl = true;
            repaint();
            break;
          case KeyEvent.VK_ALT:
            keyOption = true;
            repaint();
            break;
          case KeyEvent.VK_META:
            keyMeta = true;
            repaint();
            break;
        }
         }

         @Override
         public void keyReleased (KeyEvent ev) {
           switch (ev.getKeyCode()) {
             case KeyEvent.VK_SHIFT:
               keyShift = false;
               repaint();
               break;
             case KeyEvent.VK_CONTROL:
               keyCtrl = false;
               repaint();
               break;
             case KeyEvent.VK_ALT:
               keyOption = false;
               repaint();
               break;
             case KeyEvent.VK_META:
               keyMeta = false;
               repaint();
               break;
           }
         }
       });
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed (MouseEvent ev) {
          mouseDown = true;
          cancelTip();
          requestFocus();
          // Note: newLoc is in workspace coords (inches)
          Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
          if (placer != null) {
            if (placer != null) {
              // Place into DrawSurface
              pushToUndoStack();
              placer.setPlacePosition(toGrid(newLoc));
              placer.addToSurface(thisSurface);
              placer = null;
              setPlacerActive(false);
            }
          } else if (keyOption) {
            // Process Option, or ALT key
            // Select CADShape and then do CTRL-Click on second CADShape to measure distance from origin to origin
            if (selected != null) {
              for (CADShape shape : shapes) {
                // Check for mouse pointing to CADShape
                if (shape.isPositionClicked(newLoc, zoomFactor) || shape.isShapeClicked(newLoc, zoomFactor)) {
                  double dx = shape.xLoc - selected.xLoc;
                  double dy = shape.yLoc - selected.yLoc;
                  double diag = Math.sqrt(dx * dx + dy * dy);
                  setInfoText(" dx: " + LaserCut.df.format(dx) + "in," +
                    " dy: " + LaserCut.df.format(dy) + "in, " +
                    " diagonal: " + LaserCut.df.format(diag) + "in  " +
                    "(" + LaserCut.df.format(Utils2D.inchesToMM(dx)) + " mm," +
                    " " + LaserCut.df.format(Utils2D.inchesToMM(dy)) + " mm," +
                    " " + LaserCut.df.format(Utils2D.inchesToMM(diag)) + " mm)"
                  );
                  measure1 = new Point2D.Double(selected.xLoc * zoomFactor * LaserCut.SCREEN_PPI,
                    selected.yLoc * zoomFactor * LaserCut.SCREEN_PPI);
                  measure2 = new Point2D.Double(shape.xLoc * zoomFactor * LaserCut.SCREEN_PPI,
                    shape.yLoc * zoomFactor * LaserCut.SCREEN_PPI);
                  showMeasure = true;
                  break;
                }
              }
            }
          } else if (keyShift) {        // Process SHIFT key (VK_SHIFT)
            if (selected instanceof LaserCut.Resizable || selected instanceof LaserCut.Rotatable) {
              // Check for click on resizeOrRotate point (used to drag a CADShape to new size, or orientation)
              if (selected.isResizeOrRotateHandleClicked(newLoc)) {
                resizeOrRotate = selected;
                setInfoText(selected.getInfo());
                repaint();
                return;
              }
            }
            for (CADShape shape : shapes) {
              // Add or remove clicked CADShape from dragList
              if (shape.isShapeClicked(newLoc, zoomFactor)) {
                if (shape != selected && selected != null) {
                  dragList.add(selected);
                }
                if (dragList.contains(shape)) {
                  dragList.remove(shape);
                } else {
                  dragList.add(shape);
                }
                setSelected(null);
                for (LaserCut.ShapeDragSelectListener listener : dragSelectListerners) {
                  listener.shapeDragSelect(dragList.size() > 0);
                }
                repaint();
                return;
              }
            }
            // Setup for shift-drag to add to dragList
            dragStart = toGrid(new Point2D.Double(newLoc.x, newLoc.y));
            setSelected(null);
          } else {
            if (selected != null) {
              // Check for click and drag on selected CADShape's position anchor
              if (selected.isPositionClicked(newLoc, zoomFactor)) {
                dragged = selected;
                showMeasure = false;
                return;
              }
              for (CADShape shape : shapes) {
                // Check for click and drag of another CADShape's position anchor
                if (shape.isPositionClicked(newLoc, zoomFactor)) {
                  dragged = shape;
                  setSelected(shape);
                  setInfoText(shape.getShapePositionInfo());
                  showMeasure = false;
                  return;
                }
              }
              // Check for click inside CADShape if it implements Updatable (used by CADMusicStrip)
              if (selected instanceof LaserCut.Updatable && ((LaserCut.Updatable) selected).updateInternalState(newLoc)) {
                pushToUndoStack();
                repaint();
                return;
              }
              // Check for click on anchor point (used to drag CADShape to new location)
              if (selected.selectMovePoint(DrawSurface.this, newLoc, toGrid(newLoc))) {
                dragged = selected;
                repaint();
                return;
              }
              if (selected instanceof LaserCut.Resizable || selected instanceof LaserCut.Rotatable) {
                // Check for click on resizeOrRotate point (used to drag CADShape to new size or orientation)
                if (selected.isResizeOrRotateHandleClicked(newLoc)) {
                  resizeOrRotate = selected;
                  setInfoText(selected.getInfo());
                  repaint();
                  return;
                }
              }
            }
            for (CADShape shape : shapes) {
              // Check for selection or deselection of shapes
              if (shape.isShapeClicked(newLoc, zoomFactor)) {
                pushToUndoStack();
                setSelected(shape);
                showMeasure = false;
                repaint();
                return;
              }
            }
            if (keyMeta) {      // Process CMD key (VK_META)
              // Clicked on nothing with Meta Down, so setup to drag workspace
              pushToUndoStack();
              setSelected(null);
              setInfoText("");
              showMeasure = false;
              scrollPoint = newLoc;
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
              // Clicked on nothing, so clear dragging and selection
              dragStart = toGrid(new Point2D.Double(newLoc.x, newLoc.y));
              clearDragList();
              setSelected(null);
              setInfoText("");
              showMeasure = false;
            }
          }
          repaint();
        }

        @Override
        public void mouseClicked (MouseEvent ev) {
          super.mouseClicked(ev);
          if (useDblClkZoom && ev.getClickCount() == 2) {
            // Double click to zoom in or out on location clicked
            double newZoom;
            int newX, newY;
            Point pos = scrollPane.getViewport().getViewPosition();
            if (ev.isShiftDown()) {
              newZoom = Math.max(zoomFactor / 2, 1);
              newX = pos.x - ev.getX() / 2;
              newY = pos.y - ev.getY() / 2;
            } else {
              newZoom = Math.min(zoomFactor * 2, (zoomFactors[zoomFactors.length - 1]));
              newX = pos.x + ev.getX();
              newY = pos.y + ev.getY();
            }
            if (newZoom != zoomFactor) {
              zoomFactor = newZoom;
              setSize(new Dimension((int) (workSize.getWidth() * zoomFactor), (int) (workSize.getHeight() * zoomFactor)));
              scrollPane.getViewport().setViewPosition(new Point(newX, newY));
              scrollPane.revalidate();
              repaint();
              for (int ii = 0; ii < zoomFactors.length; ii++) {
                if (newZoom == zoomFactors[ii]) {
                  for (LaserCut.ZoomListener listener : zoomListeners) {
                    listener.setZoom(ii);
                  }
                }
              }
            }
          }
        }

        @Override
        public void mouseReleased (MouseEvent ev) {
          mouseDown = false;
          cancelTip();
          dragged = null;
          resizeOrRotate = null;
          scrollPoint = null;
          setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          pushedToStack = false;
          if (selected != null) {
            selected.cancelMove();
          }
          if (dragBox != null) {
            setSelected(null);
            // Add all Shapes inside dragBox to dragList
            for (CADShape shape : shapes) {
              Rectangle2D.Double rect = shape.getShapeBounds();
              rect.x += shape.xLoc;
              rect.y += shape.yLoc;
              if (dragBox.contains(rect)) {
                if (dragList.contains(shape)) {
                  dragList.remove(shape);
                } else {
                  dragList.add(shape);
                }
              }
            }
            dragBox = null;
            for (LaserCut.ShapeDragSelectListener listener : dragSelectListerners) {
              listener.shapeDragSelect(dragList.size() > 0);
            }
            repaint();
          }
          dragStart = null;
        }
      });
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged (MouseEvent ev) {
        super.mouseDragged(ev);
        Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
        if (dragged != null) {
          if (!pushedToStack) {
            pushedToStack = true;
            pushToUndoStack();
          }
          newLoc = toGrid(newLoc);
          if (!dragged.doMovePoints(newLoc)) {
            Point2D.Double delta = dragged.dragPosition(newLoc, workSize);
            setInfoText(dragged.getShapePositionInfo());
            CADShapeGroup group = dragged.getGroup();
            if (group != null) {
              for (CADShape shape : group.getGroupList()) {
                if (shape != dragged) {
                  shape.movePosition(delta);
                }
              }
            }
          }
          repaint();
        } else if (resizeOrRotate != null) {
          if (!pushedToStack) {
            pushedToStack = true;
            pushToUndoStack();
          }
          if (keyCtrl) {       // Process SHIFT key (VK_SHIFT)
            resizeOrRotate.resizeOrRotateShape(newLoc, workSize, true);   // Do rotate
          } else {
            newLoc = toGrid(newLoc);
            resizeOrRotate.resizeOrRotateShape(newLoc, workSize, false);  // Do resize
          }
          setInfoText(resizeOrRotate.getInfo());
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
        } else {
          if (dragStart != null) {
            newLoc = toGrid(newLoc);
            double ulx = Math.min(dragStart.x, newLoc.x);
            double uly = Math.min(dragStart.y, newLoc.y);
            double wid = Math.abs(newLoc.x - dragStart.x);
            double hyt = Math.abs(newLoc.y - dragStart.y);
            dragBox = new Rectangle2D.Double(ulx, uly, wid, hyt);
          }
          repaint();
        }
      }

      @Override
      public void mouseMoved (MouseEvent ev) {
        super.mouseMoved(ev);
        Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
        tipTracker(newLoc);
        if (placer != null) {
          placer.setPlacePosition(newLoc);
          repaint();
        }
      }
    });
    // Track JPanel resizeOrRotate events and save in prefs
    addComponentListener(new ComponentAdapter() {
      public void componentResized (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.width", bounds.width);
        prefs.putInt("window.height", bounds.height);
      }
    });
    // Add KeyListener to detect ESC key press
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped (KeyEvent ev) {
        super.keyTyped(ev);
        int key = ev.getExtendedKeyCode();
        if (key == KeyEvent.VK_ESCAPE) {
          if (placer != null) {
            placer = null;
            setPlacerActive(false);
            setSelected(null);
            showInfoDialog("Placing shape cancelled");
            repaint();
          }
        }
      }
    });
    // Start periodic event for tip timer
    PeriodicEvent pe = new PeriodicEvent();
    pe.runPeriodic();
  }

  void setInfoText (String text) {
    if (infoText != null) {
      infoText.setText(text);
    }
  }

  void setDoubleClickZoomEnable (boolean enable) {
    prefs.putBoolean("useDblClkZoom", useDblClkZoom = enable);
  }

  @Override
  public Dimension getPreferredSize () {
    return new Dimension((int) (workSize.getWidth() * zoomFactor), (int) (workSize.getHeight() * zoomFactor));
  }

  @Override
  public Dimension getMaximumSize () {
    return workSize;
  }

  Dimension getWorkSize () {
    return workSize;
  }

  void setSurfaceSize (Rectangle2D.Double size) {
    workSize = new Dimension((int) (size.getWidth() * LaserCut.SCREEN_PPI), (int) (size.getHeight() * LaserCut.SCREEN_PPI));
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    if (workSize.width > screenSize.width || workSize.height > screenSize.height) {
      workSize = screenSize;
    }
    setInfoText("");
    setSize(workSize);
    JFrame container = (JFrame) getFocusCycleRootAncestor();
    container.pack();
    getParent().revalidate();
    repaint();
  }

  private void addZoomListener (LaserCut.ZoomListener listener) {
    zoomListeners.add(listener);
  }

  JMenu getZoomMenu () {
    JMenu zoomMenu = new JMenu("Zoom");
    ButtonGroup zoomGroup = new ButtonGroup();
    for (double zoomFactor : zoomFactors) {
      JRadioButtonMenuItem zItem = new JRadioButtonMenuItem(zoomFactor + " : 1");
      zItem.setSelected(zoomFactor == getZoomFactor());
      zoomMenu.add(zItem);
      zoomGroup.add(zItem);
      zItem.addActionListener(ev -> setZoomFactor(zoomFactor));
    }
    addZoomListener((index) -> {
      for (int ii = 0; ii < zoomMenu.getMenuComponentCount(); ii++) {
        JRadioButtonMenuItem item = (JRadioButtonMenuItem) zoomMenu.getMenuComponent(ii);
        item.setSelected(ii == index);
      }
    });
    return zoomMenu;
  }

  double getZoomFactor () {
    return zoomFactor;
  }

  void setZoomFactor (double zoom) {
    if (zoom != zoomFactor) {
      double change = zoom / zoomFactor;
      zoomFactor = zoom;
      Dimension zoomSize = new Dimension((int) (workSize.getWidth() * zoomFactor), (int) (workSize.getHeight() * zoomFactor));
      setSize(zoomSize);
      if (showMeasure) {
        measure1.x *= change;
        measure1.y *= change;
        measure2.x *= change;
        measure2.y *= change;
      }
      repaint();
      for (int ii = 0; ii < zoomFactors.length; ii++) {
        if (zoom == zoomFactors[ii]) {
          for (LaserCut.ZoomListener listener : zoomListeners) {
            listener.setZoom(ii);
          }
        }
      }
    }
  }

  void setUnits (String dUnits) {
    this.dUnits = dUnits;
  }

  double getScreenScale () {
    return LaserCut.SCREEN_PPI * zoomFactor;
  }

  void enableGridSnap (boolean doSnap) {
    this.doSnap = doSnap;
  }

  void enableGridDisplay (boolean showGrid) {
    this.showGrid = showGrid;
    repaint();
  }

  private final Map<Double,JMenuItem>   gridMap = new HashMap<>();

  /**
   * Generate and add Grid Menu Items to LaserCut Grid Menu
   * @param gridMenu JMenu where Grid Menu items are added
   */
  void addGridMenu (JMenu gridMenu) {
    this.gridMenu = gridMenu;
    ButtonGroup gridGroup = new ButtonGroup();
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
          gridMinor = Utils2D.mmToInches(Double.parseDouble(tmp[0]));
          gridMajor = Integer.parseInt(tmp[1]);
        } else {
          gridMinor = 0;
          gridMajor = 0;
        }
        String label = tmp[0] + " " + units;
        JMenuItem mItem = new JRadioButtonMenuItem(label);
        gridMap.put(gridMinor, mItem);
        mItem.setSelected(gridMinor == getGridSize());
        gridGroup.add(mItem);
        gridMenu.add(mItem);
        mItem.addActionListener(ev -> {
          try {
            setGridSize(gridMinor, gridMajor);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
      }
    }
  }

  private void setGridSize (double gridSize, int majorStep) {
    gridSpacing = gridSize;
    gridMajor = majorStep;
    repaint();
    JMenuItem mItem = gridMap.get(gridSpacing);
    if (mItem != null) {
      for (int ii = 0; ii < gridMenu.getItemCount(); ii++) {
        JMenuItem tmp = gridMenu.getItem(ii);
        if (tmp == mItem) {
          mItem.setSelected(true);
        }
      }
    }
  }

  double getGridSize () {
    return gridSpacing;
  }

  int getGridMajor () {
    return gridMajor;
  }

  private double toGrid (double coord) {
    // From: https://stackoverflow.com/a/5421681 (answer #2)
    if (doSnap && gridSpacing > 0) {
      return gridSpacing * Math.floor(coord / gridSpacing + 0.5);
    }
    return coord;
  }

  private Point2D.Double toGrid (Point2D.Double loc) {
    return new Point2D.Double(toGrid(loc.x), toGrid(loc.y));
  }

  void addUndoListener (LaserCut.ActionUndoListener lst) {
    undoListerners.add(lst);
  }

  void addRedoListener (LaserCut.ActionRedoListener lst) {
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
      for (LaserCut.ActionUndoListener lst : undoListerners) {
        lst.undoEnable(undoStack.size() > 0);
      }
      for (LaserCut.ActionRedoListener lst : redoListerners) {
        lst.redoEnable(redoStack.size() > 0);
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  void popFromUndoStack () {
    // Suppress Undo while placing objects
    if (placer == null) {
      if (undoStack.size() > 0) {
        try {
          redoStack.addFirst(shapesListToBytes());
          shapes = bytesToShapeList(undoStack.pollFirst());
          for (LaserCut.ActionUndoListener lst : undoListerners) {
            lst.undoEnable(undoStack.size() > 0);
          }
          for (LaserCut.ActionRedoListener lst : redoListerners) {
            lst.redoEnable(redoStack.size() > 0);
          }
          repaint();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  void popFromRedoStack () {
    try {
      undoStack.addFirst(shapesListToBytes());
      shapes = bytesToShapeList(redoStack.pollFirst());
      for (LaserCut.ActionUndoListener lst : undoListerners) {
        lst.undoEnable(undoStack.size() > 0);
      }
      for (LaserCut.ActionRedoListener lst : redoListerners) {
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
   * @return checksum for current state of design
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

  void setDesign (List<CADShape> shapes, SurfaceSettings settings) {
    this.shapes = shapes;
    if (settings != null) {
      setZoomFactor(settings.zoomFactor);
      setGridSize(settings.gridStep, settings.gridMajor);
    } else {
      setZoomFactor(1);         // 1:1 scale
      setGridSize( 0.1, 10);    // 1/10th inch, 10 per inch
    }
    repaint();
  }

  void addShape (CADShape addShape) {
    pushToUndoStack();
    shapes.add(addShape);
    repaint();
  }

  private void addShapes (List<CADShape> addShapes) {
    pushToUndoStack();
    shapes.addAll(addShapes);
    repaint();
  }

  void registerInfoJTextField (JTextField itemInfo) {
    this.infoText = itemInfo;
  }

  void placeShape (CADShape shape) {
    List<CADShape> items = new ArrayList<>();
    items.add(shape);
    // Copy location of CADShape to Placer object, then zero CADShape's location
    Point2D.Double newLoc = new Point2D.Double(shape.xLoc, shape.yLoc);
    shape.setPosition(0, 0);
    placer = new Placer(items);
    placer.setPlacePosition(newLoc);
    setPlacerActive(true);
    requestFocus();
    repaint();
  }

  void placeShapes (List<CADShape> shapes) {
    if (shapes.size() > 0) {
      Rectangle2D bounds = getSetBounds(shapes);
      // Subtract offset from all the shapes to position set at 0,0
      for (CADShape shape : shapes) {
        shape.movePosition(new Point2D.Double(-bounds.getX(), -bounds.getY()));
      }
      placer = new Placer(shapes);
      setPlacerActive(true);
      requestFocus();
    }
    repaint();
  }

  // Compute bounds for a set of CADShape objects
  private static Rectangle2D getSetBounds (List<CADShape> shapes) {
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxWid = 0;
    double maxHyt = 0;
    for (CADShape shape : shapes) {
      Rectangle2D bounds = shape.getShapeBounds();
      minX = Math.min(minX, bounds.getX());
      minY = Math.min(minY, bounds.getY());
      maxWid = Math.max(maxWid, bounds.getX() + bounds.getWidth());
      maxHyt = Math.max(maxHyt, bounds.getY() + bounds.getHeight());
    }
    return new Rectangle2D.Double(minX, minY, maxWid, maxHyt);
  }

  boolean hasData () {
    return shapes.size() > 0;
  }

  void clear () {
    pushToUndoStack();
    shapes.clear();
    setZoomFactor(1);
    setGridSize(0.1, 10);
    repaint();
  }

  void roundSelected (double radius) {
    pushToUndoStack();
    Shape oldShape = selected.buildShape();
    CADShape tmp = new CADShape(CornerFinder.roundCorners(oldShape, radius), selected.xLoc, selected.yLoc, 0);
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
      CADShape tmp = new CADShape(newShape, selected.xLoc, selected.yLoc, 0);
      shapes.add(tmp);
      setSelected(tmp);
      repaint();
    }
  }

  void alignSelectedShapes (boolean alignX, boolean alignY) {
    CADShapeGroup group = selected.getGroup();
    if (group != null) {
      pushToUndoStack();
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
    if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, getParent())) {
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
      Point2D.Double delta = new Point2D.Double(xOff, yOff);
      if (dragList.size() > 0) {
        for (CADShape shape : dragList) {
          shape.movePosition(delta);
        }
      } else {
        selected.movePosition(delta);
        CADShapeGroup group = selected.getGroup();
        if (group != null) {
          for (CADShape shape : group.getGroupList()) {
            if (shape != selected) {
              shape.movePosition(delta);
            }
          }
        }
      }
      repaint();
    }
  }

  void rotateGroupAroundSelected () {
    ParameterDialog.ParmItem[] parmSet = {new ParameterDialog.ParmItem("angle|deg", 0d),
                                          new ParameterDialog.ParmItem("rotateSelected", true)};
    if (ParameterDialog.showSaveCancelParameterDialog(parmSet, dUnits, getParent())) {
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
            gItem.setPosition(pt.x, pt.y);
            gItem.rotation += angle;
          }
        }
      }
      repaint();
    }
  }

  void addSelectListener (LaserCut.ShapeSelectListener listener) {
    selectListerners.add(listener);
  }

  void addDragSelectListener (LaserCut.ShapeDragSelectListener listener) {
    dragSelectListerners.add(listener);
  }

  private void clearDragList () {
    dragList.clear();
    // Update all ShapeDragSelectListeners
    for (LaserCut.ShapeDragSelectListener listener : dragSelectListerners) {
      listener.shapeDragSelect(false);
    }
    repaint();
  }

  void removeSelected () {
    if (dragList.size() > 0) {
      pushToUndoStack();
      shapes.removeAll(dragList);
      clearDragList();
      repaint();
    } else  if (selected != null) {
      pushToUndoStack();
      shapes.remove(selected);
      CADShapeGroup group = selected.getGroup();
      if (group != null) {
        shapes.removeAll(group.getGroupList());
      }
      setSelected(null);
      repaint();
    }
  }

  void groupDragSelected () {
    pushToUndoStack();
    CADShapeGroup group = new CADShapeGroup();
    for (CADShape shape : dragList) {
      group.addToGroup(shape);
      shape.setGroup(group);
    }
    clearDragList();
    repaint();
  }

  void combineDragSelected () {
    if (dragList.size() > 0) {
      pushToUndoStack();
      Path2D.Double path = new Path2D.Double();
      AffineTransform at = AffineTransform.getTranslateInstance(0, 0);
      for (CADShape shape : dragList) {
        if (!(shape instanceof CADReference)) {
          Shape work = shape.getWorkspaceTranslatedShape();
          path.append(work.getPathIterator(at), false);
          shapes.remove(shape);
        }
      }
      List<Shape> opt = ShapeOptimizer.optimizeShape(path);
      // Group Optimized shapes
      for (Shape shape : opt) {
        Rectangle2D bnds = shape.getBounds2D();
        double xLoc = bnds.getX();
        double yLoc = bnds.getY();
        AffineTransform at2 = AffineTransform.getTranslateInstance(-xLoc - bnds.getWidth() / 2, -yLoc - bnds.getHeight() / 2);
        shape = at2.createTransformedShape(shape);
        CADShape cadShape = new CADShape(shape, xLoc, yLoc, 0);
        shapes.add(cadShape);
      }
      clearDragList();
      repaint();
    }
  }

  void duplicateSelected () {
    if (dragList.size() > 0) {
      pushToUndoStack();
      List<CADShape> dupList = new ArrayList<>();
      Map<CADShape, CADShape> alreadyDuped = new HashMap<>();
      for (CADShape shape : dragList) {
        CADShapeGroup group = shape.getGroup();
        if (group != null) {
          CADShapeGroup newGroup = new CADShapeGroup();
          for (CADShape gShape : group.getGroupList()) {
            if (!alreadyDuped.containsKey(gShape)) {
              alreadyDuped.put(gShape, gShape);
              CADShape dup = gShape.copy();
              newGroup.addToGroup(dup);
              dupList.add(dup);
              shapes.add(dup);
            }
          }
        } else {
          if (!alreadyDuped.containsKey(shape)) {
            alreadyDuped.put(shape, shape);
            CADShape dup = shape.copy();
            dupList.add(dup);
            shapes.add(dup);
          }
        }
      }
      dragList.clear();
      if (dupList.size() > 0) {
        dragList.addAll(dupList);
        moveSelected();
      }
    } else if (selected != null) {
      pushToUndoStack();
      CADShapeGroup group = selected.getGroup();
      if (group != null) {
        CADShapeGroup newGroup = new CADShapeGroup();
        List<CADShape> dupList = new ArrayList<>();
        for (CADShape shape : group.getGroupList()) {
          CADShape dup = shape.copy();
          if (shape == selected) {
            setSelected(dup);
          }
          newGroup.addToGroup(dup);
          dupList.add(dup);
          shapes.add(dup);
        }
        if (dupList.size() > 0) {
          dragList.addAll(dupList);
          moveSelected();
        }
      } else {
        CADShape dup = selected.copy();
        setSelected(dup);
        placeShape(dup);
      }
      repaint();
    }
  }

  CADShape getSelected () {
    return selected;
  }

  private void setSelected (CADShape newSelected) {
    selected = newSelected;
    //System.out.printf("selected: x = %3.2f, x = %3.2f\n", selected.xLoc, selected.yLoc);
    for (LaserCut.ShapeSelectListener listener : selectListerners) {
      listener.shapeSelected(selected, selected != null);
    }
    repaint();
  }

  void unGroupSelected () {
    if (dragList.size() > 0) {
      pushToUndoStack();
      for (CADShape shape : dragList) {
        CADShapeGroup group = shape.getGroup();
        if (group != null) {
          group.removeAllFromGroup();
        }
      }
      repaint();
      dragList.clear();
    } else if (selected != null) {
      pushToUndoStack();
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
   * if cutItems is false, culls shapes where engrave is set to false.
   * @param cutItems if true, only process shapes with 'engrave' set to false.
   * @param planPath if true, use PathPlanner to organize nested shapes
   * @return List of CADShape objects minus culled items
   */
  List<CADShape> selectLaserItems (boolean cutItems, boolean planPath) {
    // Cull out items that will not be cut or that don't match cutItems
    ArrayList<CADShape> cullShapes = new ArrayList<>();
    for (CADShape shape : getDesign()) {
      if (!(shape instanceof CADNoDraw) && shape.engrave != cutItems) {
        cullShapes.add(shape);
      }
    }
    return planPath ? PathPlanner.optimize(cullShapes) : cullShapes;
  }

  List<CADShape> selectCutterItems (boolean planPath) {
    // Cull out items that will not be cut or that don't match cutItems
    ArrayList<CADShape> cullShapes = new ArrayList<>();
    for (CADShape shape : getDesign()) {
      if (!(shape instanceof CADNoDraw)) {
        cullShapes.add(shape);
      }
    }
    return planPath ? PathPlanner.optimize(cullShapes) : cullShapes;
  }

  private void cancelTip () {
    if (tipText != null) {
      repaint();
    }
    tipText = null;
    tipTimer = 0;
  }

  private void tipTracker (Point2D.Double loc) {
    if (tipText != null && loc != null) {
      if (tipLoc.distance(loc) * LaserCut.SCREEN_PPI > 3) {
        cancelTip();
      }
    } else {
      tipLoc = new Point2D.Double(loc.x, loc.y);
      cancelTip();
    }
  }

  /**
   * Starts periodic process that's used to bring up pop up hint text for the selected object
   */
  class PeriodicEvent {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void runPeriodic () {
      final Runnable periodic = () -> {
        if (tipTimer < 10) {
          tipTimer++;
        } else if (tipTimer == 10) {
          if (!mouseDown) {
            if (placer != null) {
              tipText = "Click to place " + placer.shapes.get(0).getMenuName() + "\n - - \n" +
                "or ESC to cancel";
              repaint();
            } else {
              tipTimer = 0;
              if (selected instanceof LaserCut.StateMessages) {
                tipText = ((LaserCut.StateMessages) selected).getStateMsg();
              } else {
                if (selected != null) {
                  if (selected.isPositionClicked(tipLoc, getZoomFactor())) {
                    tipText = "Click and drag to\nreposition the " + selected.getMenuName() + ".";
                  } else if (selected.isResizeOrRotateHandleClicked(tipLoc)) {
                    if (selected instanceof LaserCut.Resizable && selected instanceof LaserCut.Rotatable) {
                      tipText = "Click and drag to resize the " + selected.getMenuName() + ".\n" +
                        "Hold shift and drag to rotate it.";
                    } else if (selected instanceof LaserCut.Rotatable) {
                      tipText = "Click and drag to rotate the " + selected.getMenuName() + ".";
                    }
                  } else if (selected.isShapeClicked(tipLoc, getZoomFactor())) {
                    tipText = "Click outline of " + selected.getMenuName() + " to select it, or click\n" +
                      "anywhere else to deselect it.\n - - \n" +
                      "Click another shape's outline while Shift is\n" +
                      "down to group or ungroup with any already\n" +
                      "selected shapes.";
                  }
                } else {
                  repaint();
                }
              }
              if (tipText != null) {
                repaint();
              }
            }
          }
        }
      };
      // Run every second for 100 milliseconds'with initial an delay of 0 seconds
      final ScheduledFuture<?> beeperHandle = scheduler.scheduleAtFixedRate(periodic, 0, 100, TimeUnit.MILLISECONDS);
    }
  }

  public void paint (Graphics g) {
    Dimension d = getSize();
    Graphics2D g2 = (Graphics2D) g;
    g2.setBackground(Color.white);
    g2.clearRect(0, 0, d.width, d.height);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (showGrid && gridSpacing > 0) {
      Stroke bold = new BasicStroke(2.5f);
      Stroke mild = new BasicStroke(1.0f);
      g2.setColor(new Color(224, 222, 254));
      int mCnt = 0;
      for (double xx = 0; xx <= workSize.width / LaserCut.SCREEN_PPI; xx += gridSpacing) {
        double col = xx * getScreenScale();
        double cHyt = workSize.height * zoomFactor;
        g2.setStroke(gridMajor > 0 && (mCnt++ % gridMajor) == 0 ? bold :mild);
        g2.draw(new Line2D.Double(col, 0, col, cHyt));
      }
      mCnt = 0;
      for (double yy = 0; yy <= workSize.height / LaserCut.SCREEN_PPI; yy += gridSpacing) {
        double row = yy  * getScreenScale();
        double rWid = workSize.width * zoomFactor;
        g2.setStroke(gridMajor > 0 && (mCnt++ % gridMajor) == 0 ? bold :mild);
        g2.draw(new Line2D.Double(0, row, rWid, row));
      }
    }
    for (CADShape shape : new ArrayList<>(shapes)) {
      shape.isSelected = shape == selected;
      shape.inGroup = selected != null && selected.getGroup() != null && selected.getGroup().containsShape(shape);
      shape.dragged = dragList != null && dragList.contains(shape);
      shape.draw(g2, zoomFactor, keyCtrl, keyShift, keyOption);
    }
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
      g2.fill(Utils2D.getArrow(measure1.x, maxY, measure2.x, maxY, false));
      g2.fill(Utils2D.getArrow(measure1.x, maxY, measure2.x, maxY, true));
      g2.fill(Utils2D.getArrow(maxX, measure1.y, maxX, measure2.y, false));
      g2.fill(Utils2D.getArrow(maxX, measure1.y, maxX, measure2.y, true));
      // Draw diagonal
      g2.draw(new Line2D.Double(measure1.x, measure1.y, measure2.x, measure2.y));
    }
    if (tipText != null && tipLoc != null) {
      // todo: need code to reposition tooltip when near lower, or right edges
      JTextArea tip = new JTextArea();
      tip.setFont(new Font("Ariel", Font.PLAIN, 12));
      tip.setText(tipText);
      tip.validate();
      Dimension dim = tip.getPreferredSize();
      tip.setSize(dim);
      BufferedImage img = new BufferedImage(dim.width + 14, dim.height + 14, BufferedImage.TYPE_INT_RGB);
      Graphics gg = img.createGraphics();
      gg.setColor(Color.white);
      gg.fillRect(0, 0, img.getWidth(), img.getHeight());
      gg.setColor(Color.black);
      gg.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
      gg.translate(7, 7);
      tip.paint(gg);
      gg.dispose();
      double scale = getScreenScale();
      g2.drawImage(img, (int) (tipLoc.x * scale) + 7, (int) (tipLoc.y * scale) + 7, null);
    }
    if (placer != null) {
      g2.setColor(Color.black);
      g2.setStroke(new BasicStroke(1.0f));
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
      placer.draw(g2, zoomFactor);
    }
    if (dragBox != null) {
      g2.setColor(Color.black);
      g2.setStroke(Utils2D.getDashedStroke(1, 10.0f, 10.0f));
      double scale = getScreenScale();
      g2.draw(new Rectangle2D.Double(dragBox.x * scale, dragBox.y * scale, dragBox.width * scale, dragBox.height * scale));
    }
  }

  public void showInfoDialog (String msg) {
    showMessageDialog(this, msg);
  }
}
