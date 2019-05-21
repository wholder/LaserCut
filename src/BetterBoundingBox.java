import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

class BetterBoundingBox {

  static Rectangle2D getBounds (Shape shape) {
    Rectangle2D bounds = null;    // Note: must be null
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

  static class BetterBoundingTest extends JFrame {
    private transient Image offScr;
    private Dimension lastDim;

    private BetterBoundingTest () {
      setSize(200, 260);
      setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      setVisible(true);
    }

    public void paint (Graphics g) {
      Dimension d = getSize();
      if (offScr == null || (lastDim != null && (d.width != lastDim.width || d.height != lastDim.height)))
        offScr = createImage(d.width, d.height);
      lastDim = d;
      Graphics2D g2 = (Graphics2D) offScr.getGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setBackground(getBackground());
      g2.clearRect(0, 0, d.width, d.height);
      g2.setColor(Color.black);
      // Draw a cubic Bezier cadShape using Path2D.Double
      Path2D.Double path = new Path2D.Double();
      path.moveTo(40, 140);
      path.curveTo(40, 60, 160, 60, 160, 140);
      path.curveTo(160, 220, 40, 220, 40, 140);
      path.closePath();
      g2.draw(path);
      // Draw the bounding box computed for the path by getBounds2D()
      //Rectangle2D bounds = path.getBounds2D();
      // Work around
      Rectangle2D bounds = BetterBoundingBox.getBounds(path);
      int x = (int) bounds.getMinX();
      int y = (int) bounds.getMinY();
      int wid = (int) bounds.getWidth();
      int hyt = (int) bounds.getHeight();
      g2.setColor(Color.blue);
      g2.drawRect(x, y, wid, hyt);
      // Draw the location of Bezier control points as small circles
      // Control points are: 40,60 - 160,60 - 160,220 - 40,220
      g2.setColor(Color.red);
      g2.drawOval(40-2, 60-3, 5, 5);
      g2.drawOval(160-2, 60-3, 5, 5);
      g2.drawOval(160-2, 220-3, 5, 5);
      g2.drawOval(40-2, 220-3, 5, 5);
      g.drawImage(offScr, 0, 0, this);
    }
  }

  public static void main (String[] args) {
    new BetterBoundingTest();
  }
}
