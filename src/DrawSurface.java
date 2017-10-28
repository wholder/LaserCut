import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.CRC32;

public class DrawSurface extends JPanel {
  private LaserCut                        laserCut;
  private Preferences                     prefs;
  private Dimension                       workSize;
  private JTextField                      infoText;
  private java.util.List<LaserCut.CADShape> shapes = new ArrayList<>(), shapesToPlace;
  private LaserCut.CADShape selected, dragged, shapeToPlace;
  private double                          gridSpacing;
  private int                             gridMajor;
  private double[]                        zoomFactors = {1, 2, 4};
  private double                          zoomFactor = 1;
  private Point2D.Double                  scrollPoint, measure1, measure2;
  private boolean                         useDblClkZoom;
  private java.util.List<LaserCut.ShapeSelectListener> selectListerners = new ArrayList<>();
  private java.util.List<LaserCut.ActionUndoListener> undoListerners = new ArrayList<>();
  private java.util.List<LaserCut.ActionRedoListener> redoListerners = new ArrayList<>();
  private java.util.List<LaserCut.ZoomListener> zoomListeners = new ArrayList<>();
  private LinkedList<byte[]> undoStack = new LinkedList<>();
  private LinkedList<byte[]>              redoStack = new LinkedList<>();
  private boolean                         pushedToStack, showMeasure, doSnap, showGrid;

  DrawSurface (LaserCut laserCut, Preferences prefs, JScrollPane scrollPane, Dimension workSize) {
    super(true);
    this.laserCut = laserCut;
    this.prefs = prefs;
    gridSpacing = prefs.getDouble("gridSpacing", 0);
    gridMajor = prefs.getInt("gridMajor", 0);
    useDblClkZoom = prefs.getBoolean("useDblClkZoom", false);
    // Set JPanel size for Zing's maximum work area, or other, if resized by user
    setPreferredSize(this.workSize = workSize);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed (MouseEvent ev) {
        requestFocus();
        Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
        if (shapeToPlace != null || shapesToPlace != null) {
          if (doSnap && gridSpacing > 0) {
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
            for (LaserCut.CADShape shape : shapes) {
              // Check for mouse pointing to shape
              if (shape.isPositionClicked(newLoc) || shape.isShapeClicked(newLoc)) {
                double dx = shape.xLoc - selected.xLoc;
                double dy = shape.yLoc - selected.yLoc;
                if (infoText != null) {
                  infoText.setText(" dx: " + LaserCut.df.format(dx) + " in, dy: " + LaserCut.df.format(dy) +
                      " in (" + LaserCut.df.format(LaserCut.inchesToMM(dx)) + " mm, " + LaserCut.df.format(LaserCut.inchesToMM(dy)) + " mm)");
                }
                measure1 = new Point2D.Double(selected.xLoc * LaserCut.SCREEN_PPI, selected.yLoc * LaserCut.SCREEN_PPI);
                measure2 = new Point2D.Double(shape.xLoc * LaserCut.SCREEN_PPI, shape.yLoc * LaserCut.SCREEN_PPI);
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
            LaserCut.CADShapeGroup selGroup = selected.getGroup();
            for (LaserCut.CADShape shape : shapes) {
              if (shape.isShapeClicked(newLoc)) {
                pushToUndoStack();
                LaserCut.CADShapeGroup shapeGroup = shape.getGroup();
                if (shape == selected) {
                  // clicked shape is selected shape, so remove from group and
                  // assign remaining shape in group as selected shape
                  LaserCut.CADShape newSel;
                  if (selGroup != null) {
                    newSel = selGroup.removeFromGroup(shape);
                    setSelected(newSel);
                  }
                } else {
                  // clicked shape is not selected shape
                  if (selGroup == null) {
                    // No group, so create one and add both shapes to it
                    selGroup = new LaserCut.CADShapeGroup();
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
            for (LaserCut.CADShape shape : shapes) {
              // Check for click and drag of shape's position
              if (shape.isPositionClicked(newLoc)) {
                dragged = shape;
                setSelected(shape);
                if (infoText != null) {
                  infoText.setText("xLoc: " + LaserCut.df.format(dragged.xLoc) + ", yLoc: " + LaserCut.df.format(dragged.yLoc));
                }
                showMeasure = false;
                return;
              }
            }
            if (selected.updateInternalState(DrawSurface.this, newLoc)) {
              repaint();
              return;
            }
          }
          LaserCut.CADShape procShape = null;
          for (LaserCut.CADShape shape : shapes) {
            // Check for selection or deselection of shapes
            if (shape.isShapeClicked(newLoc) || ((shape instanceof LaserCut.CADReference || shape instanceof LaserCut.CADShapeSpline) &&
                shape.isPositionClicked(newLoc)) ) {
              procShape = shape;
              break;
            }
          }
          if ((procShape == null || procShape == selected) && selected != null && selected.selectMovePoint(DrawSurface.this, newLoc, toGrid(newLoc))) {
            dragged = selected;
            repaint();
            return;
          }
          if (procShape != null) {
            pushToUndoStack();
            setSelected(procShape);
            if (infoText != null) {
              infoText.setText(procShape.getInfo());
            }
            showMeasure = false;
          } else {
            pushToUndoStack();
            setSelected(null);
            if (infoText != null) {
              infoText.setText("");
            }
            showMeasure = false;
            scrollPoint = newLoc;
            laserCut.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
        }
        repaint();
      }

      @Override
      public void mouseClicked (MouseEvent ev) {
        super.mouseClicked(ev);
        if (ev.getClickCount() == 2 && useDblClkZoom) {
          // Double click to zoom in or out on location clicked
          double newZoom;
          int newX, newY;
          Point pos = scrollPane.getViewport().getViewPosition();
          if (ev.isShiftDown()) {
            newZoom = Math.max(zoomFactor / 2, 1);
            newX = pos.x - ev.getX() / 2;
            newY = pos.y - ev.getY() / 2;
          } else {
            newZoom = Math.min(zoomFactor * 2, 4);
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
        dragged = null;
        scrollPoint = null;
        laserCut.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        pushedToStack = false;
        if (selected != null) {
          selected.cancelMove();
        }
      }
    });
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged (MouseEvent ev) {
        super.mouseDragged(ev);
        if (dragged != null) {
          if (!pushedToStack) {
            pushedToStack = true;
            pushToUndoStack();
          }
          Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
          if (doSnap && gridSpacing > 0) {
            newLoc = toGrid(newLoc);
          }
          if (!dragged.doMovePoints(newLoc)) {
            Point2D.Double delta = dragged.setPosition(newLoc);
            if (infoText != null) {
              infoText.setText("xLoc: " + LaserCut.df.format(dragged.xLoc) + ", yLoc: " + LaserCut.df.format(dragged.yLoc));
            }
            LaserCut.CADShapeGroup group = dragged.getGroup();
            if (group != null) {
              for (LaserCut.CADShape shape : group.getShapesInGroup()) {
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

      @Override
      public void mouseMoved (MouseEvent ev) {
        super.mouseMoved(ev);
        Point2D.Double newLoc = new Point2D.Double(ev.getX() / getScreenScale(), ev.getY() / getScreenScale());
        if (shapeToPlace != null) {
          shapeToPlace.setPosition(newLoc);
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
    // Add KeyListener to detect ESC key press
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped (KeyEvent ev) {
        super.keyTyped(ev);
        int key = ev.getExtendedKeyCode();
        if (key == KeyEvent.VK_ESCAPE) {
          if (shapeToPlace != null || shapesToPlace != null) {
            shapeToPlace = null;
            shapesToPlace = null;
            setSelected(null);
            if (infoText != null) {
              infoText.setText("Place shape cancelled");
            }
            repaint();
          }
        }
      }
    });
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

  void setSurfaceSize (Dimension size) {
    workSize = size;
    setSize(size);
    double tmp = zoomFactor;
    zoomFactor = 1;
    laserCut.pack();
    zoomFactor = tmp;
    getParent().revalidate();
    repaint();
  }

  double[] getZoomFactors () {
    return zoomFactors;
  }

  void addZoomListener (LaserCut.ZoomListener listener) {
    zoomListeners.add(listener);
  }

  void setZoomFactor (double zoom) {
    if (zoom != zoomFactor) {
      zoomFactor = zoom;
      Dimension zoomSize = new Dimension((int) (workSize.getWidth() * zoomFactor), (int) (workSize.getHeight() * zoomFactor));
      setSize(zoomSize);
      repaint();
    }
  }

  double getZoomFactor () {
    return zoomFactor;
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

  private ArrayList<LaserCut.CADShape> bytesToShapeList(byte[] bytes) throws Exception {
    ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
    ObjectInputStream in = new ObjectInputStream(bIn);
    LaserCut.CADShape sel = (LaserCut.CADShape) in.readObject();
    if (sel != null) {
      setSelected(sel);
    }
    return (ArrayList<LaserCut.CADShape>) in.readObject();
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
    if (shapeToPlace == null && shapesToPlace == null) {
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

  java.util.List<LaserCut.CADShape> getDesign () {
    return shapes;
  }

  ArrayList<LaserCut.CADShape> getSelectedAsDesign () {
    ArrayList<LaserCut.CADShape> design = new ArrayList<>();
    design.add(selected);
    LaserCut.CADShapeGroup grp = selected.getGroup();
    if (grp != null) {
      for (LaserCut.CADShape shape : grp.getGroupList()) {
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

  void setDesign (java.util.List<LaserCut.CADShape> shapes) {
    this.shapes = shapes;
    repaint();
  }

  void addShape (LaserCut.CADShape shape) {
    pushToUndoStack();
    shapes.add(shape);
    repaint();
  }

  private void importShapes (java.util.List<LaserCut.CADShape> addShapes, Point2D.Double newLoc) {
    pushToUndoStack();
    // Determine upper left offset to set of import shapes
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    for (LaserCut.CADShape shape : addShapes) {
      Rectangle2D bounds = shape.getBounds();
      minX = Math.min(minX, shape.xLoc + bounds.getX());
      minY = Math.min(minY, shape.yLoc + bounds.getY());
    }
    // Place all imported shapes so upper left position of set is now where used clicked
    for (LaserCut.CADShape shape : addShapes) {
      shape.xLoc = shape.xLoc - minX + newLoc.x;
      shape.yLoc = shape.yLoc - minY + newLoc.y;
      shapes.add(shape);
    }
  }

  void registerInfoJTextField (JTextField itemInfo) {
    this.infoText = itemInfo;
  }

  void placeShape (LaserCut.CADShape shape) {
    shapeToPlace = shape;
    if (infoText != null) {
      infoText.setText("Click to place Shape");
    }
    requestFocus();
    repaint();
  }

  void placeShapes (List<LaserCut.CADShape> shapes) {
    shapesToPlace = shapes;
    requestFocus();
    if (infoText != null) {
      infoText.setText("Click to place imported Shapes");
    }
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
    LaserCut.CADShape tmp = new LaserCut.CADShape(CornerFinder.roundCorners(oldShape, radius), selected.xLoc, selected.yLoc, 0, selected.centered);
    shapes.remove(selected);
    shapes.add(tmp);
    setSelected(tmp);
    repaint();
  }

  void addOrSubtractSelectedShapes (boolean add) {
    LaserCut.CADShapeGroup group = selected.getGroup();
    if (group != null) {
      pushToUndoStack();
      Shape base = selected.getLocallyTransformedShape();
      Area newShape = new Area(base);
      shapes.remove(selected);
      for (LaserCut.CADShape gItem : group.getGroupList()) {
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
        LaserCut.CADShape tmp = new LaserCut.CADShape(newShape, selected.xLoc, selected.yLoc, 0, true);
        shapes.add(tmp);
        setSelected(tmp);
      } else {
        Rectangle2D bounds = newShape.getBounds2D();
        AffineTransform at = AffineTransform.getTranslateInstance(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
        newShape.transform(at);
        LaserCut.CADShape tmp = new LaserCut.CADShape(newShape, selected.xLoc, selected.yLoc, 0, selected.centered);
        shapes.add(tmp);
        setSelected(tmp);
      }
      repaint();
    }
  }

  void alignSelectedShapes (boolean alignX, boolean alignY) {
    pushToUndoStack();
    LaserCut.CADShapeGroup group = selected.getGroup();
    if (group != null) {
      for (LaserCut.CADShape gItem : group.getGroupList()) {
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
    if (ParameterDialog.showSaveCancelParameterDialog(parmSet, getParent())) {
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
      LaserCut.CADShapeGroup group = selected.getGroup();
      if (group != null) {
        for (LaserCut.CADShape gItem : group.getGroupList()) {
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
    if (ParameterDialog.showSaveCancelParameterDialog(parmSet, getParent())) {
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
      LaserCut.CADShapeGroup group = selected.getGroup();
      if (group != null) {
        for (LaserCut.CADShape gItem : group.getGroupList()) {
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

  void addSelectListener (LaserCut.ShapeSelectListener listener) {
    selectListerners.add(listener);
  }

  void removeSelected () {
    if (selected != null) {
      pushToUndoStack();
      shapes.remove(selected);
      LaserCut.CADShapeGroup group = selected.getGroup();
      if (group != null) {
        shapes.removeAll(group.getGroupList());
      }
      for (LaserCut.ShapeSelectListener listener : selectListerners) {
        listener.shapeSelected(selected, false);
      }
      selected = null;
      repaint();
    }
  }

  void duplicateSelected () {
    if (selected != null) {
      pushToUndoStack();
      LaserCut.CADShapeGroup group = selected.getGroup();
      if (group != null) {
        boolean first = true;
        LaserCut.CADShapeGroup newGroup = new LaserCut.CADShapeGroup();
        for (LaserCut.CADShape shape : group.getGroupList()) {
          LaserCut.CADShape dup = shape.copy();
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
        LaserCut.CADShape dup = selected.copy();
        dup.xLoc += .1;
        dup.yLoc += .1;
        shapes.add(dup);
        setSelected(dup);
      }
      repaint();
    }
  }

  LaserCut.CADShape getSelected () {
    return selected;
  }

  private void setSelected (LaserCut.CADShape newSelected) {
    if (selected != null) {
      selected.setSelected(false);
    }
    selected = newSelected;
    if (selected != null) {
      selected.setSelected(true);
    }
    for (LaserCut.ShapeSelectListener listener : selectListerners) {
      listener.shapeSelected(selected, true);
    }
    repaint();
  }

  void unGroupSelected () {
    if (selected != null) {
      LaserCut.CADShapeGroup group = selected.getGroup();
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
  ArrayList<LaserCut.CADShape> selectLaserItems (boolean cutItems) {
    // Cull out items that will not be cut or that don't match cutItems
    ArrayList<LaserCut.CADShape> cullShapes = new ArrayList<>();
    for (LaserCut.CADShape shape : getDesign()) {
      if (!(shape instanceof LaserCut.CADNoDraw) && shape.engrave != cutItems) {
        cullShapes.add(shape);
      }
    }
    // Reorder shapes by successively finding the next closest point starting from upper left
    ArrayList<LaserCut.CADShape> newShapes = new ArrayList<>();
    double lastX = 0, lastY = 0;
    while (cullShapes.size() > 0) {
      double dist = Double.MAX_VALUE;
      LaserCut.CADShape sel = null;
      for (LaserCut.CADShape shape : cullShapes) {
        double tDist = shape.distanceTo(lastX, lastY);
        if (tDist < dist) {
          sel = shape;
          dist = tDist;
        }
      }
      if (sel != null) {
        lastX = sel.xLoc;
        lastY = sel.yLoc;
        newShapes.add(sel);
        cullShapes.remove(sel);
      }
    }
    return newShapes;
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
      g2.fill(getArrow(measure1.x, maxY, measure2.x, maxY, false));
      g2.fill(getArrow(measure1.x, maxY, measure2.x, maxY, true));
      g2.fill(getArrow(maxX, measure1.y, maxX, measure2.y, false));
      g2.fill(getArrow(maxX, measure1.y, maxX, measure2.y, true));
    }
    if (shapeToPlace != null) {
      g2.setColor(Color.black);
      g2.setStroke(new BasicStroke(1.0f));
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
      shapeToPlace.draw(g2, getScreenScale());
    }
  }

  private Path2D.Double getArrow (double x1, double y1, double x2, double y2, boolean atEnd) {
    Path2D.Double path = new Path2D.Double();
    double angleOff = Math.toRadians(20);
    int barb = 10;
    if (atEnd) {
      double angle = Math.atan2(y2 - y1, x2 - x1);
      double ax1 = x2 - barb * Math.cos(angle - angleOff);
      double ay1 = y2 - barb * Math.sin(angle - angleOff);
      double ax2 = x2 - barb * Math.cos(angle + angleOff);
      double ay2 = y2 - barb * Math.sin(angle + angleOff);
      path.moveTo(ax1, ay1);
      path.lineTo(x2, y2);
      path.lineTo(ax2, ay2);
    } else {
      double angle = Math.atan2(y1 - y2, x1 - x2);
      double ax1 = x1 - barb * Math.cos(angle - angleOff);
      double ay1 = y1 - barb * Math.sin(angle - angleOff);
      double ax2 = x1 - barb * Math.cos(angle + angleOff);
      double ay2 = y1 - barb * Math.sin(angle + angleOff);
      path.moveTo(ax1, ay1);
      path.lineTo(x1, y1);
      path.lineTo(ax2, ay2);
    }
    path.closePath();
    return path;
  }

  void writePDF (File file) throws Exception {
    FileOutputStream output = new FileOutputStream(file);
    double scale = 72;
    PDDocument doc = new PDDocument();
    PDDocumentInformation docInfo = doc.getDocumentInformation();
    docInfo.setCreator("Wayne Holder's LaserCut");
    docInfo.setProducer("Apache PDFBox " + org.apache.pdfbox.util.Version.getVersion());
    docInfo.setCreationDate(Calendar.getInstance());
    double wid = workSize.width / LaserCut.SCREEN_PPI * 72;
    double hyt = workSize.height / LaserCut.SCREEN_PPI * 72;
    PDPage pdpage = new PDPage(new PDRectangle((float) wid, (float) hyt));
    doc.addPage(pdpage);
    PDPageContentStream stream = new PDPageContentStream(doc, pdpage, PDPageContentStream.AppendMode.APPEND, false);
    // Flip Y axis so origin is at upper left
    Matrix flipY = new Matrix();
    flipY.translate(0, pdpage.getBBox().getHeight());
    flipY.scale(1, -1);
    stream.transform(flipY);
    AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
    for (LaserCut.CADShape item : getDesign()) {
      if (item instanceof LaserCut.CADReference)
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
