import java.awt.*;
import java.awt.geom.*;

public class BetterBoundingBox {

  static Rectangle2D getBounds (Shape shape) {
    Rectangle2D bounds = null;
    PathIterator pi = shape.getPathIterator(null);
    double xLoc = 0, yLoc = 0;
    double mX = 0, mY = 0;
    while (!pi.isDone()) {
      double[] crds = new double[6];
      int type = pi.currentSegment(crds);
      switch (type) {
        case PathIterator.SEG_CLOSE:
          bounds = bounds.createUnion((new Line2D.Double(xLoc, yLoc, mX, mY)).getBounds2D());
          break;
        case PathIterator.SEG_MOVETO:
          mX = xLoc = crds[0];
          mY = yLoc = crds[1];
          if (bounds == null) {
            bounds = new Rectangle2D.Double(xLoc, yLoc, 0, 0);
          } else {
            bounds = bounds.createUnion(new Rectangle2D.Double(xLoc, yLoc, 0, 0));
          }
          break;
        case PathIterator.SEG_LINETO:
          bounds = bounds.createUnion((new Line2D.Double(xLoc, yLoc, xLoc = crds[0], yLoc = crds[1])).getBounds2D());
          break;
        case PathIterator.SEG_CUBICTO:
          // Decompose 4 point, cubic bezier curve into line segments
          Point2D.Double[] tmp = new Point2D.Double[4];
          Point2D.Double[] cControl = {
              new Point2D.Double(xLoc, yLoc),
              new Point2D.Double(crds[0], crds[1]),
              new Point2D.Double(crds[2], crds[3]),
              new Point2D.Double(crds[4], crds[5])};
          int segments = 6;
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
            bounds = bounds.createUnion((new Line2D.Double(xLoc, yLoc, xLoc = tmp[0].x, yLoc = tmp[0].y)).getBounds2D());
          }
          break;
        case PathIterator.SEG_QUADTO:
          // Decompose 3 point, quadratic bezier curve into line segments
          segments = 5;
          Point2D.Double start = new Point2D.Double(xLoc, yLoc);
          Point2D.Double control = new Point2D.Double(crds[0], crds[1]);
          Point2D.Double end = new Point2D.Double(crds[2], crds[3]);
          double xLast = start.x;
          double yLast = start.y;
          for (int ii = 1; ii < segments; ii++) {
            // Use step as a ratio to subdivide lines
            double step = (double) ii / segments;
            double x = (1 - step) * (1 - step) * start.x + 2 * (1 - step) * step * control.x + step * step * end.x;
            double y = (1 - step) * (1 - step) * start.y + 2 * (1 - step) * step * control.y + step * step * end.y;
            bounds = bounds.createUnion((new Line2D.Double(xLast, yLast, x, y)).getBounds2D());
            xLast = x;
            yLast = y;
          }
          bounds = bounds.createUnion((new Line2D.Double(xLast, yLast, xLoc = end.x, yLoc = end.y)).getBounds2D());
          break;
      }
      pi.next();
    }
    return bounds;
  }
}
