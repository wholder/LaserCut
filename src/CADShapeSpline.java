import java.awt.*;
import java.awt.geom.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

class CADShapeSpline extends CADShape implements Serializable, LaserCut.StateMessages, LaserCut.Resizable, LaserCut.Rotatable {
  private static final long           serialVersionUID = 1175193935200692376L;
  private List<Point2D.Double>        points = new ArrayList<>();
  private Point2D.Double              movePoint;
  private boolean                     pathClosed;
  private Path2D.Double               path = new Path2D.Double();
  public double                       scale = 100.0;
  transient public double             lastScale;



  CADShapeSpline () {
    lastScale = scale;
  }

  @Override
  void createAndPlace (DrawSurface surface, LaserCut laserCut, Preferences prefs) {
    surface.placeShape(this);
  }

  @Override
  String getMenuName () {
    return "Spline Curve";
  }

  // Implement StateMessages interface
  public String getStateMsg () {
    if (pathClosed) {
      return "Click and drag am existing point to move it\n - - \nclick on line to add new point" +
             "\n - - \nor SHIFT click on lower right bound point to rotate";
    } else {
      String[] nextPnt = {"first", "second", "third", "additional"};
      return "Click to add " + (nextPnt[Math.min(nextPnt.length - 1, points.size())]) + " control point" +
        (points.size() >= (nextPnt.length - 1) ? "\n - - \nor click 1st control point to complete spline" : "");
    }
  }

  @Override
  protected List<String> getEditFields () {
    return Arrays.asList(
      "xLoc|in",
      "yLoc|in",
      "rotation|deg{degrees to rotate}",
      "scale|%"
      );
  }

  List<Point2D.Double>  getScaledPoints () {
    List<Point2D.Double> scaledPts = new ArrayList<>();
    for (Point2D.Double cp : points) {
      scaledPts.add(new Point2D.Double(cp.x * scale / 100, cp.y * scale / 100));
    }
    return scaledPts;
  };

  @Override
  boolean isShapeClicked (Point2D.Double point, double zoomFactor) {
    return super.isShapeClicked(point, zoomFactor) || isPositionClicked(point, zoomFactor);
  }

  boolean isPathClosed () {
    return pathClosed;
  }

  /**
   * See if we clicked on an existing Catmull-Rom Control Point other than origin
   *
   * @param surface Reference to DrawSurface
   * @param point   Point clicked in Workspace coordinates (inches)
   * @param gPoint  Closest grid point clicked in Workspace coordinates (inches)
   * @return true if clicked
   */
  @Override
  boolean selectMovePoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint) {
    // Note: mse is in unrotated coords relative to the points, such as mse: x = 0.327, y = -0.208
    Point2D.Double mse = rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
    for (int ii = 0; ii < points.size(); ii++) {
      Point2D.Double cp = points.get(ii);
      double dist = mse.distance(cp) * LaserCut.SCREEN_PPI;
      if (dist < 5) {
        if (ii == 0 && !pathClosed) {
          surface.pushToUndoStack();
          pathClosed = true;
          updatePath();
        }
        // Note: movePoint is relative to coords of points
        movePoint = cp;
        return true;
      }
    }
    int idx;
    if (pathClosed && (idx = getInsertionPoint(point)) >= 0) {
      surface.pushToUndoStack();
      points.add(idx + 1, movePoint = rotatePoint(new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
      updatePath();
      return true;
    }
    if (!pathClosed) {
      surface.pushToUndoStack();
      points.add(rotatePoint(movePoint = new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
      updatePath();
      return true;
    }
    return false;
  }

  /**
   * See if we clicked on spline cadShape to add new control point
   *
   * @param point Point clicked in Workspace coordinates (inches)
   * @return index into points List where we need to add new point
   */
  int getInsertionPoint (Point2D.Double point) {
    Point2D.Double mse = rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
    int idx = 1;
    Point2D.Double chk = points.get(idx);
    for (Line2D.Double[] lines : Utils2D.transformShapeToLines(getShape(), 1, .01)) {
      for (Line2D.Double line : lines) {
        double dist = line.ptSegDist(mse) * LaserCut.SCREEN_PPI;
        if (dist < 5) {
          return idx - 1;
        }
        // Advance idx as we pass control points
        if (idx < points.size() && chk.distance(line.getP2()) < .000001) {
          chk = points.get(Math.min(points.size() - 1, ++idx));
        }
      }
    }
    return -1;
  }

  /**
   * Rotate 2D point around 0,0 point
   *
   * @param point Point to rotate
   * @param angle Angle to rotate
   * @return Rotated 2D point
   */
  private Point2D.Double rotatePoint (Point2D.Double point, double angle) {
    AffineTransform center = AffineTransform.getRotateInstance(Math.toRadians(angle), 0, 0);
    Point2D.Double np = new Point2D.Double();
    center.transform(point, np);
    return np;
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

  @Override
  void updateShape () {
    super.updateShape();
    if (scale != lastScale) {
      // transform all the points to new scale;
      points = getScaledPoints();
      lastScale = scale;
      updatePath();
    }
  }

  private void updatePath () {
    path = Utils2D.convertPointsToBezier(points, pathClosed);
    updateShape();
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    Rectangle2D.Double bnds = Utils2D.boundsOf(points);
    scale = (Math.min(dx / bnds.getWidth(), dy / bnds.getHeight())) * 200;
    if (scale != lastScale) {
      // transform all the points to new scale;
      points = getScaledPoints();
      lastScale = scale;
      updatePath();
    }
  }

    @Override
  void draw (Graphics g, double zoom, boolean keyRotate, boolean keyResize, boolean keyOption) {
    super.draw(g, zoom, keyRotate, keyResize, keyOption);
    Graphics2D g2 = (Graphics2D) g;
    // Draw all Catmull-Rom Control Points
    g2.setColor(isSelected ? Color.red : pathClosed ? Color.lightGray : Color.darkGray);
    if (isSelected){
      for (Point2D.Double cp : points) {
        Point2D.Double np = rotatePoint(cp, rotation);
        double mx = (xLoc + np.x) * zoom * LaserCut.SCREEN_PPI;
        double my = (yLoc + np.y) * zoom * LaserCut.SCREEN_PPI;
        double mWid = 3;
        g2.fill(new Rectangle.Double(mx - mWid, my - mWid, mWid * 2, mWid * 2));
      }
    }
  }
}
