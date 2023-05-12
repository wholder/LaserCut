import java.awt.*;
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
  String getMenuName () {
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
        (points.size() >= (nextPnt.length - 1) ? "\n - -\nor click 1st point to complete polygon)" : "");
    }
  }

  @Override
  protected List<String> getEditFields () {
    return Arrays.asList(
      "xLoc|in",
      "yLoc|in",
      "rotation|deg{degrees to rotate}");
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
    Point2D.Double mse = Utils2D.rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
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
      points.add(idx + 1, movePoint = Utils2D.rotatePoint(new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
      updatePath();
      return true;
    }
    if (!closePath) {
      surface.pushToUndoStack();
      points.add(Utils2D.rotatePoint(movePoint = new Point2D.Double(gPoint.x - xLoc, gPoint.y - yLoc), -rotation));
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
    Point2D.Double mse = Utils2D.rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
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

  @Override
  boolean doMovePoints (Point2D.Double point) {
    Point2D.Double mse = Utils2D.rotatePoint(new Point2D.Double(point.x - xLoc, point.y - yLoc), -rotation);
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
      path = Utils2D.convertPointsToPath(points.toArray(new Point2D.Double[0]), true);
    } else {
      Point2D.Double[] pnts = points.toArray(new Point2D.Double[points.size() + 1]);
      // Duplicate last point so we can draw a curve through all points in the path
      pnts[pnts.length - 1] = pnts[pnts.length - 2];
      path = Utils2D.convertPointsToPath(pnts, false);
    }
    updateShape();
  }

  @Override
  void draw (Graphics g, double zoom, boolean keyShift) {
    super.draw(g, zoom, keyShift);
    Graphics2D g2 = (Graphics2D) g;
    // Draw all the Control Points
    g2.setColor(isSelected ? Color.red : closePath ? Color.lightGray : Color.darkGray);
    for (Point2D.Double cp : points) {
      Point2D.Double np = Utils2D.rotatePoint(cp, rotation);
      double mx = (xLoc + np.x) * zoom * LaserCut.SCREEN_PPI;
      double my = (yLoc + np.y) * zoom * LaserCut.SCREEN_PPI;
      double mWid = 2 * zoom;
      g2.fill(new Rectangle.Double(mx - mWid, my - mWid, mWid * 2, mWid * 2));
    }
  }
}
