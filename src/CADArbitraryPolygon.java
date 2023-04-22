import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CADArbitraryPolygon extends CADShape implements Serializable, LaserCut.StateMessages, LaserCut.Rotatable {
  private static final long           serialVersionUID = 1175193935200692376L;
  private final List<Point2D.Double>  points = new ArrayList<>();
  private Point2D.Double              movePoint;
  private boolean                     closePath;
  private Path2D.Double               path = new Path2D.Double();

  CADArbitraryPolygon () {
    centered = true;
  }

  @Override
  void createAndPlace (DrawSurface surface, LaserCut laserCut) {
    surface.placeShape(this);
  }

  @Override
  String getName () {
    return "Arbitrary Polygon";
  }

  // Implement StateMessages interface
  public String getStateMsg () {
    if (closePath) {
      return "Click and drag am existing point to move it\n - - \nclick on line to add new point" +
        "\n - - \nor SHIFT click on lower right bound point to rotate";
    } else {
      String[] nextPnt = {"first", "second", "third", "additional"};
      return "Click to add " + (nextPnt[Math.min(nextPnt.length - 1, points.size())]) + " point" +
        (points.size() >= (nextPnt.length - 1) ? " (or click 1st point to complete polygon)" : "");
    }
  }

  @Override
  protected List<String> getEditFields () {
    return Arrays.asList("xLoc|in", "yLoc|in", "rotation|deg");
  }

  @Override
  boolean isShapeClicked (Point2D.Double point, double zoomFactor) {
    return super.isShapeClicked(point, zoomFactor) || isPositionClicked(point, zoomFactor);
  }

  boolean isPathClosed () {
    return closePath;
  }

  /**
   * See if we clicked on an existing Catmull-Rom Control Point other than origin
   *
   * @param surface Reference to DrawSurface
   * @param point   Point clicked in Workspace coordinates (inches)
   * @param gPoint  Closest grid point clicked in Workspace coordinates
   * @return true if clicked
   */
  @Override
  boolean selectMovePoint (DrawSurface surface, Point2D.Double point, Point2D.Double gPoint) {
    Point2D.Double mse = rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
    for (int ii = 0; ii < points.size(); ii++) {
      Point2D.Double cp = points.get(ii);
      double dist = mse.distance(cp.x, cp.y) * LaserCut.SCREEN_PPI;
      if (dist < 5) {
        if (ii == 0 && !closePath) {
          surface.pushToUndoStack();
          closePath = true;
         updatePath();
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
    for (Line2D.Double[] lines : transformShapeToLines(getShape(), 1, .01)) {
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

  private void updatePath () {
    if (closePath) {
      path = convert(points.toArray(new Point2D.Double[0]), true);
    } else {
      Point2D.Double[] pnts = points.toArray(new Point2D.Double[points.size() + 1]);
      // Duplicate last point so we can draw a curve through all points in the path
      pnts[pnts.length - 1] = pnts[pnts.length - 2];
      path = convert(pnts, false);
    }
    updateShape();
  }

  private Path2D.Double convert (Point2D.Double[] points, boolean close) {
    Path2D.Double path = new Path2D.Double();
    path.moveTo(points[0].x, points[0].y);
    for (int ii = 0; ii < points.length; ii++) {
      Point2D.Double point = points[ii];
      path.lineTo(point.x, point.y);
    }
    /*
    int end = close ? points.length + 1 : points.length - 1;
    for (int ii = 0; ii < end - 1; ii++) {
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
      double x2 = (p1.x + 6 * p2.x - p3.x) / 6;  // Second control point
      double y2 = (p1.y + 6 * p2.y - p3.y) / 6;
      double x3 = p2.x;                           // End point
      double y3 = p2.y;
      path.curveTo(x1, y1, x2, y2, x3, y3);
    }
     */
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
      double mx = (xLoc + np.x) * zoom * LaserCut.SCREEN_PPI;
      double my = (yLoc + np.y) * zoom * LaserCut.SCREEN_PPI;
      double mWid = 2 * zoom;
      g2.fill(new Rectangle.Double(mx - mWid, my - mWid, mWid * 2, mWid * 2));
    }
  }
}
