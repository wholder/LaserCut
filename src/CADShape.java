import java.awt.*;
import java.awt.geom.*;
import java.awt.geom.Point2D.Double;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * This is the base class for all the CAD objects.  JavaCut uses serialization to save and restore
 * a design to/from a a file, so be aware of that kinds of changes are allowable in order to be able
 * to continue to load files made using older versions of this code:
 * <p>
 * A compatible change is one that can be made to a new version of the class, which still keeps the
 * stream compatible with older versions of the class. Examples of compatible changes are:
 * <p>
 * Addition of new fields or classes does not affect serialization, as any new data in the stream is
 * simply ignored by older versions. When the instance of an older version of the class is deserialized,
 * the newly added field will be set to its default value.
 * <p>
 * You can field change access modifiers like private, public, protected or package as they are not
 * reflected to the serial stream.
 * <p>
 * You can change a transient or static field to a non-transient or non-static field, as it is similar
 * to adding a field.
 * <p>
 * You can change the access modifiers for constructors and methods of the class. For instance a
 * previously private method can now be made public, an instance method can be changed to static, etc.
 * The only exception is that you cannot change the default signatures for readObject() and writeObject()
 * if you are implementing custom serialization. The serialization process looks at only instance data,
 * and not the methods of a class.
 * <p>
 * Changes which would render the stream incompatible are:
 * <p>
 * Once a class implements the Serializable interface, you cannot later make it implement the Externalizable
 * interface, since this will result in the creation of an incompatible stream.
 * <p>
 * Deleting fields can cause a problem. Now, when the object is serialized, an earlier version of the class
 * would set the old field to its default value since nothing was available within the stream. Consequently,
 * this default data may lead the newly created object to assume an invalid state.
 * <p>
 * Changing a non-static into static or non-transient into transient is not permitted as it is equivalent
 * to deleting fields.
 * <p>
 * You also cannot change the field types within a class, as this would cause a failure when attempting to
 * read in the original field into the new field.
 * <p>
 * You cannot alter the position of the class in the class hierarchy. Since the fully-qualified class name
 * is written as part of the bytestream, this change will result in the creation of an incompatible stream.
 * <p>
 * You cannot change the name of the class or the package it belongs to, as that information is written
 * to the stream during serialization.
 *
 * Notes:
 *  * Shapes are defined around a cented coordinate and position using the offset values xLoc and yLoc
 *  * The xLoc and yLoc values are changed when the "centered: value in the edit GUI for each type is toggled
 *    on and off.  NOte: planning to refactor this so the centered value is handled in the draw functios
 *    only and the xLoc and yLoc values are kept relative to the shapes center
 *  *
 *
 * void 			createAndPlace (DrawSurface surface, LaserCut laserCut, LaserCut laserCut)
 * String 		getName ()
 * String 		getShapePositionInfo ()
 * Shape 		  buildShape ()
 * Color 		  getShapeColor ()
 * float 		  getStrokeWidth ()
 * Stroke 		getShapeStroke (float strokeWidth)
 * boolean 	  doMovePoints (Point2D.Double point)
 * boolean 	  selectMovePoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint)
 * void 			cancelMove ()
 * void 			updateStateAfterParameterEdit ()
 * String[] 	getParameterNames ()
 * void 			hookParameters (Map<String, ParameterDialog.ParmItem> pNames)
 */
class CADShape implements Serializable {
  private static final long serialVersionUID = 3716741066289930874L;
  public double             xLoc, yLoc, rotation;   // Note: must be public for reflection
  public boolean            engrave;                // Note: must be public for reflection
  public boolean            centered;               // Note: must preserve to be compatible with older files
  CADShapeGroup             group;
  Shape                     shape;
  static final Color        DRAG_COLOR = new Color(238, 54, 199);
  transient Shape           builtShape;
  transient boolean         isSelected, inGroup, dragged;
  transient List<LaserCut.ChangeListener> changeSubscribers;

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
  CADShape (Shape shape, double xLoc, double yLoc, double rotation) {
    if (shape instanceof Area) {
      // Workaround for Area not Serializable problem
      this.shape = AffineTransform.getTranslateInstance(0, 0).createTransformedShape(shape);
    } else {
      this.shape = shape;
    }
    setLocationAndOrientation(xLoc, yLoc, rotation);
  }

  // Override in subclasses such as CADRasterImage and CADShapeSpline
  void createAndPlace (DrawSurface surface, LaserCut laserCut, Preferences prefs) {
    if (placeParameterDialog(surface, prefs.get("displayUnits", "in"))) {
      surface.placeShape(this);
    }
  }

  // Override in subclasses
  String getMenuName () {
    return "Shape";
  }

  void addChangeListener (LaserCut.ChangeListener subscriber) {
    if (changeSubscribers == null) {
      changeSubscribers = new LinkedList<>();
    }
    changeSubscribers.add(subscriber);
  }

  void notifyChangeListeners () {
    if (changeSubscribers != null) {
      for (LaserCut.ChangeListener subscriber : changeSubscribers) {
        subscriber.shapeChanged(this);
      }
    }
  }

  void setLocationAndOrientation (double xLoc, double yLoc, double rotation) {
    this.xLoc = xLoc;
    this.yLoc = yLoc;
    this.rotation = rotation;
  }

  /**
   * Get absolute bounds of cadShape in workspace coords
   *
   * @return absolute bounding rectangle
   */
  Rectangle2D.Double getShapeBounds () {
    return (Rectangle2D.Double) getShape().getBounds2D();
  }

  /**
   * Uses reflection to deep copy subclasses of CADShape
   *
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
   * Override in subclass to regenerate cadShape when parameters are changed
   *
   * @return Shape built using current parameter settings
   */
  Shape buildShape () {
    return shape;
  }

  /**
   * Get cadShape, if not build, build cadShape first
   *
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
   *
   * @return translated Shape
   */
  protected Shape getLocallyTransformedShape () {
    Shape dShape = getShape();
    AffineTransform at = new AffineTransform();
    // Position Shape centered on xLoc/yLoc in inches (x from left, y from top)
    at.rotate(Math.toRadians(rotation));
    return at.createTransformedShape(dShape);
  }

  // Translate Shape to Workspace position
  protected Shape getWorkspaceTranslatedShape () {
    Shape shape = getLocallyTransformedShape();
    AffineTransform at = AffineTransform.getTranslateInstance(xLoc, yLoc);
    return at.createTransformedShape(shape);
  }

  /**
   * Use PathIterator to find coordinates where drawing will start for this shape
   * Note: used by PathPlanner to optimise overall cutting path
   *
   * @return starting location for cut
   */
  Point2D.Double getStartCoords () {
    PathIterator pi = getWorkspaceTranslatedShape().getPathIterator(new AffineTransform());
    double[] coords = new double[4];
    pi.currentSegment(coords);
    return new Point2D.Double(coords[0], coords[1]);
  }

  /**
   * Transform cadShape to workspace and return as list of arrays of line segments where each array
   * in the list is the set of lines for a closed cadShape.
   *
   * @param scale scale factor
   * @return list of arrays of line segments
   */
  List<Line2D.Double[]> getListOfScaledLines (double scale, double flatten) {
    return Utils2D.transformShapeToLines(getWorkspaceTranslatedShape(), scale, flatten);
  }

  /**
   * Override in subclass, as needed
   *
   * @return Color used to draw cadShape in its current state
   */
  Color getShapeColor () {
    if (dragged) {
      return new Color(238, 54, 199);           // Dragged color
    } else {
      if (isSelected) {
        if (engrave) {
          return new Color(255, 113, 21);       // isSelected and engrave color
        } else {
          return new Color(29, 40, 255);        // isSelected color
        }
      } else if (inGroup) {
        if (engrave) {
          return new Color(255, 170, 45);       // isSelected, InGroup and engrave color
        } else {
          //return new Color(57, 108, 255);       // Selected and ingroup color
          return new Color(238, 54, 199);
        }
      } else {
        if (engrave) {
          return new Color(255, 200, 0);        // Not isSelected Engrave color
        } else {
          return new Color(0, 0, 0);            // Not isSelected color
        }
      }
    }
  }

  /**
   * Override in subclass, as needed
   *
   * @return width of stroke used to draw cadShape in its current state
   */
  float getStrokeWidth () {
    if (dragged) {
      return 1.8f;              // dragged
    } else {
      if (isSelected) {
        return 1.8f;            // isSelected
      } else if (inGroup) {
        return 1.4f;            // Not isSelected and inGroup
      } else {
        return 1.0f;            // Not isSelected and not inGroup
      }
    }
  }

  /**
   * Override in subclass, as needed
   *
   * @return Stroke used to draw cadShape in its current state
   */
  Stroke getShapeStroke () {
    return new BasicStroke(getStrokeWidth());
  }

  /**
   * Override in subclass to let mouse drag move internal control points
   */
  void doMovePoints (Double point) {
  }

  /**
   * Override in subclass to determine if user has selected a control point to move
   * @return true if moving a control point
   */
  boolean isMovingControlPoint () {
    return false;
  }

  /**
   * Override in subclass to check if a moveable internal point was clicked
   *
   * @return true if a moveable internal point is was clicked, else false
   */
  boolean isControlPoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint) {
    return false;
  }

  /**
   * Override in subclass to cancel selection of a moveable internal point
   */
  void cancelMove () {
  }

  /**
   * Override in subclass to update object's internal state after parameter edit
   *  Overriden in: CADRasterImage, CADMusicStrip,
   */
  void updateStateAfterParameterEdit () {
  }

  /**
   * Move cadShape's position by amount specified in 'delta'
   *
   * @param delta amount to move CADShape
   */
  void movePosition (Point2D.Double delta) {
    setPosition(xLoc + delta.x, yLoc + delta.y);
  }

  void setPosition (double newX, double newY) {
    xLoc = newX;
    yLoc = newY;
    notifyChangeListeners();
  }

  /**
   * Set position of cadShape to a new location, but keep anchor inside working area
   *
   * @param newLoc   new x/y position (in cadShape coordinates, inches)
   * @param workSize size of workspace in screen units
   * @return delta position change in a Point2D.Double object
   */
  Point2D.Double dragPosition (Point2D.Double newLoc, Dimension workSize) {
    double x = Math.max(Math.min(newLoc.x, workSize.width / LaserCut.SCREEN_PPI), 0);
    double y = Math.max(Math.min(newLoc.y, workSize.height / LaserCut.SCREEN_PPI), 0);
    Point2D.Double delta = new Point2D.Double(x - xLoc, y - yLoc);
    setPosition(x, y);
    return delta;
  }

  /**
   * Check if 'point' is close to cadShape's xLoc/yLoc position
   *
   * @param point      Location click on screen in model coordinates (inches)
   * @param zoomFactor Zoom factor (ratio)
   * @return true if close enough to consider a 'touch'
   */
  boolean isPositionClicked (Point2D.Double point, double zoomFactor) {
    double dist = point.distance(xLoc, yLoc) * LaserCut.SCREEN_PPI;
    return dist < 5 / zoomFactor;
  }

  boolean clickInsideToSelect () {
    return false;
  }

  /**
   * Check if 'point' is close to one of the segments that make up the cadShape
   *
   * @param point      Location click on screen in model coordinates (inches)
   * @param zoomFactor Zoom factor (ratio)
   * @return true if close enough to consider a 'touch'
   */
  boolean isShapeClicked (Point2D.Double point, double zoomFactor) {
    // Scale Shape to Screen scale and scan all line segments in the cadShape
    Shape lShape = getWorkspaceTranslatedShape();
    // Compute slightly expanded bounding rectangle for cadShape
    Rectangle2D bnds = lShape.getBounds2D();
    bnds = new Rectangle2D.Double(bnds.getX() - .1, bnds.getY() - .1, bnds.getWidth() + .2, bnds.getHeight() + .2);
    // Check if point clicked is within  bounding rectangle of cadShape
    if (bnds.contains(point)) {
      if (clickInsideToSelect()) {
        return true;
      }
      Point2D.Double sPoint = new Point2D.Double(point.x * zoomFactor * LaserCut.SCREEN_PPI, point.y * zoomFactor * LaserCut.SCREEN_PPI);
      for (Line2D.Double[] lines : Utils2D.transformShapeToLines(lShape, zoomFactor * LaserCut.SCREEN_PPI, .01)) {
        for (Line2D.Double line : lines) {
          double dist = line.ptSegDist(sPoint);
          // return true if any is closer than 5 pixels to point
          if (dist < 5) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Override in subclass to define which subclass fields are editable parameters
   *
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
    return Arrays.asList(
      "rotation|deg",
      "engrave"
    );
  }

  // Note: override in subclass, as needed
  protected List<String> getEditFields () {
    return Arrays.asList(
      "xLoc|in",
      "yLoc|in",
      "rotation|deg{degrees to rotate}",
      "engrave");
  }

  /**
   * Bring up editable parameter dialog box so user can edit fields.  Uses reflection to read and save
   * parameter values before clicking the mouse to place the cadShape.
   *
   * @return true if used clicked OK to save
   */
  boolean placeParameterDialog (DrawSurface surface, String dUnits) {
    return displayShapeParameterDialog(surface, new ArrayList<>(getPlaceFields()), "Place", dUnits);
  }

  /**
   * Bring up editable parameter dialog box do user can edit fields.  Uses reflection to read and save
   * parameter values.
   *
   * @return true if used clicked OK to save
   */
  boolean editParameterDialog (DrawSurface surface, String dUnits) {
    return displayShapeParameterDialog(surface, new ArrayList<>(getEditFields()),
      "Save",
      dUnits);
  }

  // Override in subclass to attach Parameter listeners
  void hookParameters (Map<String, ParameterDialog.ParmItem> pNames) {
    // Does nothing by default
  }

  /**
   * Bring up editable parameter dialog box do user can edit fields using reflection to read and update parameter values.
   *
   * @param surface      parent Component for Dialog
   * @param parmNames    List of parameter names
   * @param actionButton Text for action button, such as "Save" or "Place"
   * @param dUnits prefs.get("displayUnits"
   * @return true if used clicked action button, else false if they clicked cancel.
   */
  private boolean displayShapeParameterDialog (DrawSurface surface, List<String> parmNames, String actionButton, String dUnits) {
    parmNames.addAll(Arrays.asList(getParameterNames()));
    ParameterDialog.ParmItem[] parmSet = new ParameterDialog.ParmItem[parmNames.size()];
    for (int ii = 0; ii < parmSet.length; ii++) {
      String parmName = parmNames.get(ii);
      try {
        parmSet[ii] = new ParameterDialog.ParmItem(parmName, null);
        Object val = this.getClass().getField(parmSet[ii].name).get(this);
        parmSet[ii].setValue(val);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    Map<String, ParameterDialog.ParmItem> pNames = new HashMap<>();
    for (ParameterDialog.ParmItem parm : parmSet) {
      pNames.put(parm.name, parm);
    }
    hookParameters(pNames);
    ParameterDialog dialog = (new ParameterDialog("Edit Parameters", parmSet, new String[]{actionButton, "Cancel"}, dUnits));
    dialog.setLocationRelativeTo(surface.getParent());
    dialog.setVisible(true);              // Note: this call invokes dialog
    if (dialog.wasPressed()) {
      surface.pushToUndoStack();
      for (ParameterDialog.ParmItem parm : parmSet) {
        try {
          Field fld = this.getClass().getField(parm.name);
          fld.set(this, parm.value);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
      // Update cadShape's internal state after parameter edit
      updateStateAfterParameterEdit();
      if ("Place".equals(actionButton)) {
        Point dLoc = surface.getLocationOnScreen();
        Point mLoc = dialog.getMouseLoc();
        if (dLoc != null && mLoc != null) {
          double xLoc = (mLoc.x - dLoc.x) / surface.getScreenScale();
          double yLoc = (mLoc.y - dLoc.y) / surface.getScreenScale();
          setPosition(xLoc, yLoc);
        }
      }
      return true;
    }
    return false;
  }

  /*
   * * * * * * * Resize and Rotate Logic * * * * * * * *
   */

  /**
   * Get shape's anchor point
   * @return adjusted location of anchor point
   */
  private Point2D.Double getShapeMoveHandle () {
    return new Point2D.Double(xLoc , yLoc);
    // This doesn't work... Breaks rotate/resize drag controls
    //Rectangle2D bnds = getShapeBounds();
    //return new Point2D.Double(bnds.getX() + bnds.getWidth() / 2, bnds.getY() + bnds.getHeight() / 2);
  }

  /**
   * Get location of unrotated point for resize/rotate control
   * @return unrotated location of grab point
   */
  // Override, as needed
  protected Point2D.Double getResizeOrRotateHandle () {
    Rectangle2D bnds = getShapeBounds();
    return new Point2D.Double(xLoc + bnds.getX() + bnds.getWidth(), yLoc + bnds.getY() + bnds.getHeight());
  }

  /**
   * Get location of rotated point for resize/rotate control
   * @return rotated location of grab point
   */
  private Point2D.Double getRotateddResizeOrRotateHandle () {
    return Utils2D.rotateAroundPoint(getShapeMoveHandle(), getResizeOrRotateHandle(), rotation);
  }

  public boolean isResizeOrRotateHandleClicked (Point2D.Double point) {
    Point2D.Double grab = getRotateddResizeOrRotateHandle();
    double dist = point.distance(grab.x, grab.y) * LaserCut.SCREEN_PPI;
    return dist < 5;
  }

  // Implement in subclass, as needed
  void resize (double dx, double dy) {}

  /**
   * Resize cadShape using newLoc to compute change
   *
   * @param newLoc new x/y position (in workspace coordinates, inches)
   * @param workSize size of workspace in screen units
   */
  public void resizeShape (Point2D.Double newLoc, Dimension workSize) {
    // Counter rotate mouse loc into cadShape's coordinate space to measure stretch/shrink
    double tx = Math.max(Math.min(newLoc.x, workSize.width / LaserCut.SCREEN_PPI), 0);
    double ty = Math.max(Math.min(newLoc.y, workSize.height / LaserCut.SCREEN_PPI), 0);
    Point2D.Double grab = Utils2D.rotateAroundPoint(getShapeMoveHandle(), new Point2D.Double(tx, ty), -rotation);
    double dx = grab.x - xLoc;
    double dy = grab.y - yLoc;
    resize(dx, dy);
    updateShape();
  }

  /**
   * Rotate cadShape using newLoc to compute change
   *
   * @param newLoc new x/y position (in workspace coordinates, inches)
   */
  public void rotateShape (Point2D.Double newLoc) {
    Point2D.Double anchor = getShapeMoveHandle();
    Point2D.Double grab = getResizeOrRotateHandle();
    double angle1 = Math.toDegrees(Math.atan2(anchor.y - newLoc.y, anchor.x - newLoc.x));
    double angle2 = Math.toDegrees(Math.atan2(anchor.y - grab.y, anchor.x - grab.x));
    // Change angle in even, 1 degree steps
    rotation = Math.floor((angle1 - angle2) + 0.5);
    rotation = rotation >= 360 ? rotation - 360 : rotation < 0 ? rotation + 360 : rotation;
    updateShape();
  }



  /**
   * Draw cadShape to screen
   *
   * @param g    Graphics object
   * @param zoom Zoom factor (ratio)
   */
  void draw (Graphics g, double zoom, boolean keyRotate, boolean keyResize, boolean keyOption) {
    Graphics2D g2 = (Graphics2D) g.create();
    Shape dShape = getWorkspaceTranslatedShape();
    // Resize Shape to scale and draw it
    AffineTransform atScale = AffineTransform.getScaleInstance(zoom * LaserCut.SCREEN_PPI, zoom * LaserCut.SCREEN_PPI);
    dShape = atScale.createTransformedShape(dShape);
    g2.setStroke(new BasicStroke(getStrokeWidth()));
    g2.setColor(getShapeColor());
    g2.draw(dShape);
    if (isSelected || this instanceof CADReference) {
      // Draw (+) grab point for move option
      g2.draw(Utils2D.getPlusShape(new Point2D.Double(xLoc * zoom * LaserCut.SCREEN_PPI, yLoc * zoom * LaserCut.SCREEN_PPI), 4));
    }
    if (isSelected) {
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // Draw grab point for resizing image
      Point2D.Double rGrab = getRotateddResizeOrRotateHandle();
      double mx = rGrab.x * zoom * LaserCut.SCREEN_PPI;
      double my = rGrab.y * zoom * LaserCut.SCREEN_PPI;
      g2d.setColor(new Color(0, 153, 0));
      if (keyRotate) {
        // Draw circle for rotate interface
        g2d.draw(Utils2D.getCircleShape(new Point2D.Double(mx, my), 4));
        // Draw dashed line to connect axis if rotation to grap point
        g2d.setStroke(Utils2D.getDashedStroke(1, 5.0f, 9.0f));
        double cx = xLoc * zoom * LaserCut.SCREEN_PPI;
        double cy = yLoc * zoom * LaserCut.SCREEN_PPI;
        g2d.draw(new Line2D.Double(cx, cy, mx, my));
        // Draw text indicating current angle of rotation
        int angle = (int) rotation;
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("(θ=" + angle + ")", (float) mx + 10, (float) my + 4);
      } else if (keyResize && !(this instanceof LaserCut.NorResizable)) {
        // Draw diamond grap point for resize interface
        g2d.draw(Utils2D.getDiamondShape(new Point2D.Double(mx, my), 4));
        // Draw text indicating current angle of rotation
        int angle = (int) rotation;
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("Resize", (float) mx + 10, (float) my + 4);
      }
    }
    g2.dispose();
  }
}
