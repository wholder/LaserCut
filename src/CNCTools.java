import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class CNCTools {

  static class PLine {
    private static double LARGE = 1.0e12;   // Avoid divide by zero...
    private double        slope, intercept;

    private PLine (Line2D.Double line, Point2D.Double point) {
      double dx = line.x2 - line.x1;
      slope = dx == 0 ? LARGE : (line.y2 - line.y1) / dx;
      intercept = point.y - slope * point.x;
      if (Double.isNaN(slope) || Double.isNaN(intercept)) {
        System.out.println("Not a number");
      }
    }

    Point2D.Double intersects (PLine l2) {
      double div = slope - l2.slope;
      double x = div == 0 ? LARGE : (l2.intercept - intercept) / div;
      return new Point2D.Double(x, slope * x + intercept);
    }
  }

  /**
   * Takes an array of lines that forms a closed shape and computes a parallel path around either the
   * interior, or exterior of the shape depending on the setting of the parameter outside.
   * @param lines array of lines for a shape for which this code will compute a parallel path
   * @param radius offset distance for parallel path (radius of CNC tool)
   * @param outside true if parallel path should be around the outside of the shape, else inside
   * @return array of points for the parallel path
   */
  static Point2D.Double[] getParallelPath (Line2D.Double[] lines, double radius, boolean outside) {
    List<PLine> pLines = new ArrayList<>();
    boolean clockwise = isClockwise(lines);
    for (int ii = 0; ii < lines.length; ii++) {
      Line2D.Double line = lines[ii];
      double d = line.getP1().distance(line.getP2());
      double t = radius / d;
      // Compute point "ext" then extends "radius" distance from point shape[n] toward point shape[n+1]
      Point2D.Double ext = new Point2D.Double((1 - t) * line.x1 + t * line.x2, (1 - t) * line.y1 + t * line.y2);
      // Compute the normal to point shape[n] by rotating point "ext" +/- 90 degrees around point shape[n]
      Point2D.Double normal;
      if (clockwise ^ outside) {
        // Rotate point ext -90 degrees (clockwise) around point shape[ii]
        normal = new Point2D.Double(-(ext.y - line.y1) + line.x1, (ext.x - line.x1) + line.y1);
      } else {
        // Rotate point ext 90 degrees (counter clockwise) around point shape[ii]
        normal = new Point2D.Double((ext.y - line.y1) + line.x1, -(ext.x - line.x1) + line.y1);
      }
      if (line.x1 != line.x2 || line.y1 != line.y2) {
        pLines.add(new PLine(line, normal));
      }
    }
    Point2D.Double[] path = new Point2D.Double[pLines.size()];
    for (int ii = 0; ii < path.length; ii++) {
      int jj = (ii + 1) % path.length;
      path[ii] = pLines.get(ii).intersects(pLines.get(jj));
    }
    return path;
  }

  /**
   * Scans a set of lines forming a closed shape to detect if points are in clockwise, or counterclockwise order
   * @param lines a set of lines forming a closed shape
   * @return true if shape is drawn in clockwise order, else false
   */
  private static boolean isClockwise (Line2D.Double[] lines) {
    double sum = 0;
    for (int ii = 0; ii < lines.length; ii++) {
      Line2D.Double line = lines[ii];
      sum += (line.x2 - line.x1) * (line.y2 + line.y1);
    }
    return sum < 0;
  }
}
