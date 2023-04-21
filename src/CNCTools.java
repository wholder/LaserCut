import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

class CNCTools {

  static class PLine {
    private static final double LARGE = 1.0e12;   // Avoid divide by zero...
    private double              slope;
    private double              intercept;

    /**
     * PLine class represents a line of infinite length.  This constructor creates a line that passes
     * through <point) and is parallel to <line>
     * @param line parallel reference line
     * @param point point that PLine passes through
     */
    private PLine (Line2D.Double line, Point2D.Double point) {
      double dx = line.x2 - line.x1;
      slope = dx == 0 ? LARGE : (line.y2 - line.y1) / dx;
      intercept = point.y - slope * point.x;
    }

    /**
     * PLine class represents a line of infinite length.  This constructor creates a line that passes through
     * point dst and, if perpendicular is true, rotates the line 90 degrees so that is perpendicular to the line
     * that passes from point src to point dst, else like passes through both points
     * @param src point from here to dst establishes the perpendicular
     * @param dst point that PLine passes through
     */
    private PLine (Point2D.Double src, Point2D.Double dst, boolean perpendicular) {
      double dx = dst.x - src.x;
      slope = dx == 0 ? LARGE : (dst.y - src.y) / dx;
      if (perpendicular) {
        slope = -1 / (slope == 0 ? LARGE : slope);
      }
      intercept = dst.y - slope * dst.x;
    }

    /**
     * PLine class represents a line of infinite length.  This constructor creates a line that passes through line
     * @param line line specifing ininite line
     */
    private PLine (Line2D.Double line) {
      this(new Point2D.Double(line.x1, line.y1), new Point2D.Double(line.x2, line.y2), false);
    }

    Point2D.Double intersects (PLine l2) {
      if (slope == l2.slope) {
        return null;
      }
      double div = slope - l2.slope;
      double x = div == 0 ? LARGE : (l2.intercept - intercept) / div;
      return new Point2D.Double(x, slope * x + intercept);
    }
  }

  /**
   * Takes an array of lines that forms a closed cadShape and computes a parallel path around either the
   * interior, or exterior of the cadShape depending on the setting of the parameter outside.
   * @param lines array of lines for a cadShape for which this code will compute a parallel path
   * @param radius offset distance for parallel path (radius of CNC tool)
   * @param outside true if parallel path should be around the outside of the cadShape, else inside
   * @return array of points for the parallel path
   */
  static Point2D.Double[] getParallelPath (Line2D.Double[] lines, double radius, boolean outside) {
    // Prune any between points that are parallel (on the line from prior point to next point)
    List<Point2D.Double> tmp = new ArrayList<>();
    for (int ii = 0; ii < lines.length; ii++) {
      Point2D.Double p1 = (Point2D.Double) lines[ii].getP1();
      Point2D.Double p2 = (Point2D.Double) lines[(ii + 1) % lines.length].getP1();
      Point2D.Double p3 = (Point2D.Double) lines[(ii + 2) % lines.length].getP1();
      PLine l1 = new PLine(p1, p2, false);
      PLine l2 = new PLine(p2, p3, false);
      if (l1.slope != l2.slope) {
        tmp.add(p2);
      }
    }
    Point2D.Double[] points = tmp.toArray(new Point2D.Double[0]);
    PLine[] pLines = new PLine[points.length];
    boolean clockwise = isClockwise(points);
    for (int ii = 0; ii < points.length; ii++) {
      int jj = (ii + 1) % points.length;
      double d = points[ii].distance(points[jj]);
      double t = radius / d;
      // Compute point "ext" that extends "radius" distance from point cadShape[n] toward point cadShape[n+1]
      Point2D.Double ext = new Point2D.Double((1 - t) * points[ii].x + t * points[jj].x, (1 - t) * points[ii].y + t * points[jj].y);
      // Compute the normal to point cadShape[n] by rotating point "ext" +/- 90 degrees around point cadShape[n]
      Point2D.Double normal;
      if (clockwise ^ outside) {
        // Rotate point ext -90 degrees (clockwise) around point cadShape[ii]
        normal = new Point2D.Double(-(ext.y - points[ii].y) + points[ii].x, (ext.x - points[ii].x) + points[ii].y);
      } else {
        // Rotate point ext 90 degrees (counter clockwise) around point cadShape[ii]
        normal = new Point2D.Double((ext.y - points[ii].y) + points[ii].x, -(ext.x - points[ii].x) + points[ii].y);
      }
      pLines[ii] = new PLine(new Line2D.Double(points[ii], points[jj]), normal);
    }
    // Check for shortcut paths and compute path intersections
    List<Point2D.Double> oPoints = new ArrayList<>();
    for (int ii = 0; ii < points.length; ii++) {
      int jj = (ii + 1) % points.length;
      Point2D.Double np1 = pLines[ii].intersects(pLines[(ii + 1) % points.length]);
      Point2D.Double np2 = pLines[(ii + 1) % points.length].intersects(pLines[(ii + 2) % points.length]);
      Point2D.Double ref = points[jj];
      // Compute point "tip" that extends "radius" distance from cadShape's point to the intersection
      double d = ref.distance(np1);
      double t = radius / d;
      Point2D.Double tip = new Point2D.Double((1 - t) * ref.x + t * np1.x, (1 - t) * ref.y + t * np1.y);
      // Compute line through tip that's perpendicular to the line from cadShape's point to the intersection
      PLine perp = new PLine(ref, tip, true);
      // Compute intersection with perpendicular and pLines[ii] and pLines[jj]
      Point2D.Double p1 = pLines[ii].intersects(perp);
      Point2D.Double p2 = pLines[jj].intersects(perp);
      double dist = np1.distance(p2) + p2.distance(np2) - np1.distance(np2);
      if (dist < .0001) {
        oPoints.add(p1);
        oPoints.add(p2);
      } else {
        oPoints.add(np1);
      }
    }
    return oPoints.toArray(new Point2D.Double[0]);
  }

  /**
   * Prunes sections of a closed path that cross back over themselves by scanning for overlapping line segments
   * Note: extremely inefficient algorithm (approx n^2 / 2 where n is numebr of input points), but it works
   * Todo: https://www.geeksforgeeks.org/given-a-set-of-line-segments-find-if-any-two-segments-intersect/
   * @param points set of input points to prune
   * @return pruned array of points
   */
  static Point2D.Double[] pruneOverlap (Point2D.Double[] points) {
    List<Point2D.Double> oPoints = new ArrayList<>();
    int exclude = 0;
    loop1:
    for (int ii = 0; ii < points.length - 1; ii++) {
      Line2D.Double l1 = new Line2D.Double(points[ii], points[(ii + 1) % points.length]);
      for (int jj = ii + 1; jj < points.length; jj++) {
        Line2D.Double l2 = new Line2D.Double(points[jj], points[(jj + 1) % points.length]);
        if (l1.intersectsLine(l2) && !l1.getP2().equals(l2.getP1()) && !l1.getP1().equals(l2.getP2())) {
          Point2D.Double xx = getIntersection(l1, l2);
          oPoints.add(xx);
          ii = jj - 1;
          exclude = jj + 1;
          continue loop1;
        }
      }
      if (ii >= exclude) {
        oPoints.add(points[ii]);
      }
    }
    oPoints.add(points[points.length - 1]);
    return oPoints.toArray(new Point2D.Double[0]);
  }

  /*
   * Computes intersection point for two lines
   */
  private static  Point2D.Double getIntersection (Line2D.Double l1, Line2D.Double l2) {
    return (new PLine(l1)).intersects(new PLine(l2));
  }

  /**
   * Scans a set of points forming a closed cadShape to detect if points are in clockwise, or counterclockwise order
   * @param points a set of lines forming a closed cadShape
   * @return true if cadShape is drawn in clockwise order, else false
   */
  private static boolean isClockwise (Point2D.Double[] points) {
    double sum = 0;
    for (int ii = 0; ii < points.length; ii++) {
      double x1 = points[ii].x;
      double y1 = points[ii].y;
      double x2 = points[(ii + 1) % points.length].x;
      double y2 = points[(ii + 1) % points.length].y;
      sum += (x2 - x1) * (y2 + y1);
    }
    return sum < 0;
  }
}
