import java.awt.Shape;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.util.ArrayList;

  // Bezier circle: http://spencermortensen.com/articles/bezier-circle/

public class CornerFinder {
  private static final DecimalFormat  df = new DecimalFormat("#.####");

  public static void main (String[] args) {
    Shape shape;
    // Create + cadShape via additive and subtractive geometric operations
    Area sq = new Area(new Rectangle2D.Double(-2, -1, 4, 2));
    // Note: arcHeight and arcWith of .25 gives a radius of .125
    sq.add(new Area(new RoundRectangle2D.Double(-1, -2, 2, 4, .25, .25)));
    shape = sq;
    AffineTransform rot = AffineTransform.getRotateInstance(Math.toRadians(45));
    shape = rot.createTransformedShape(shape);
    shape = SVGParser.removeOffset(new Shape[] {shape})[0];
    ArrayList<Shape> shapes = new ArrayList<>();
    shapes.add(roundCorners(shape, .125));
    new ShapeWindow(shapes.toArray(new Shape[0]), .25);
  }

  static Shape roundCorners (Shape shape, double radius) {
    ArrayList<Point2D.Double> points = getPoints(shape);
    Point2D.Double[] pnts = points.toArray(new Point2D.Double[0]);
    Area a1 = new Area(shape);
    for (int ii = 0; ii < pnts.length; ii++) {
      Point2D.Double p1 = pnts[ii % pnts.length];
      Point2D.Double p2 = pnts[(ii + 1) % pnts.length];
      Point2D.Double p3 = pnts[(ii + 2) % pnts.length];
      if (checkForCorner(p1, p2, p3)) {
        //shapes.add(getCircle(p2, .1));
        // Compute new points for radius start/end and draw '+' to indicate
        Point2D.Double p1b = moveDistance(radius, p2, p1);
        Point2D.Double p3b = moveDistance(radius, p2, p3);
        //shapes.add(getPlus(p1b, .1));
        //shapes.add(getPlus(p3b, .1));
        // Compute point p2m between p1b and p3b
        Point2D.Double p2m = new Point2D.Double(p1b.x + (p3b.x - p1b.x) / 2, p1b.y + (p3b.y - p1b.y) / 2);
        //shapes.add(getPlus(p2m, .1));
        // Compute reflection of p2 needed to complete square
        Point2D.Double p2r = new Point2D.Double(p2.x + (p2m.x - p2.x) * 2, p2.y + (p2m.y - p2.y) * 2);
        //shapes.add(getPlus(p2r, .1));
        // Compute point p1p and p3p that extend slightly in direction from p2r
        Point2D.Double p1p = moveDistance(-.01, p2, p1b);
        Point2D.Double p3p = moveDistance(-.01, p2, p3b);
        //shapes.add(getPlus(p1p, .1));
        //shapes.add(getPlus(p3p, .1));
        // Compute p1x and p3x that extends slightly past p2 in direction from p1 and p3
        Point2D.Double p1x = moveDistance(-.01, p1b, p2r);
        Point2D.Double p3x = moveDistance(-.01, p3b, p2r);
        //shapes.add(getPlus(p1x, .1));
        //shapes.add(getPlus(p3x, .1));
        // Compute corner path to add, or subtract depending on whether inner corner, or outer corner
        Path2D.Double corner = new Path2D.Double();
        // p2r -> p1x -> p1p -> p3p -> p3x -> p2r
        corner.moveTo(p2r.x, p2r.y);  // p2r
        corner.lineTo(p1x.x, p1x.y);  // p1x
        corner.lineTo(p3p.x, p3p.y);  // p3p
        corner.lineTo(p1p.x, p1p.y);  // p1p
        corner.lineTo(p3x.x, p3x.y);  // p3x
        corner.lineTo(p2r.x, p2r.y);  // p2r
        corner.closePath();
        //shapes.add(corner);
        if (!a1.contains(p2m.x, p2m.y)) {
          a1.add(new Area(corner));
          a1.subtract(new Area(new Ellipse2D.Double(p2r.x - radius, p2r.y - radius, radius * 2, radius * 2)));
        } else {
          a1.subtract(new Area(corner));
          a1.add(new Area(new Ellipse2D.Double(p2r.x - radius, p2r.y - radius, radius * 2, radius * 2)));
        }
      }
    }
    return a1;
  }

  private static ArrayList<Point2D.Double> getPoints (Shape shape) {
    PathIterator pi = shape.getPathIterator(null);
    ArrayList<Point2D.Double> points = new ArrayList<>();
    Point2D.Double start = null;
    while (!pi.isDone()) {
      double[] coords = new double[6];
      int type = pi.currentSegment(coords);
      switch (type) {
        case PathIterator.SEG_MOVETO:
          points.add(new Point2D.Double(coords[0], coords[1]));
          if (start == null) {
            start = new Point2D.Double(coords[0], coords[1]);
          }
          break;
        case PathIterator.SEG_LINETO:
          Point2D.Double newLine = new Point2D.Double(coords[0], coords[1]);
          if (!newLine.equals(start)) {
            points.add(newLine);
          }
          break;
        case PathIterator.SEG_CUBICTO:
          points.add(new Point2D.Double(coords[2], coords[3]));
          break;
        case PathIterator.SEG_QUADTO:
          points.add(new Point2D.Double(coords[4], coords[5]));
          break;
        case PathIterator.SEG_CLOSE:
          break;
      }
      pi.next();
    }
    return points;
  }

  private static boolean checkForCorner (Point2D.Double p1, Point2D.Double p2, Point2D.Double p3) {
    double dx12 = Math.abs(p1.x - p2.x);
    double dy12 = Math.abs(p1.y - p2.y);
    double dx23 = Math.abs(p2.x - p3.x);
    double dy23 = Math.abs(p2.y - p3.y);
    double s12 = dx12 < dy12 ? dx12 / dy12 : dy12 / dx12;
    double s23 = dy23 < dx23 ? dy23 / dx23 : dx23 / dy23;
    double dif = Math.abs(s12 - s23);
    //System.out.println("corner at: " + df.format(p2.x) + ", " + df.format(p2.y));
    return dif < .0001;
  }

  /**
   * Returns a new Point2D.Double that's a distance of 'dist' away from point 'from' when
   * moving toward point 'to'.
   * @param dist distance to move along line
   * @param from from point
   * @param to to point
   * @return new point that is 'dist' away from 'from' point
   */
  private static Point2D.Double moveDistance (double dist, Point2D.Double from, Point2D.Double to) {
    double v = from.distance(to);
    double t = dist / v;
    return new Point2D.Double(from.x + (from.x * -t) + (to.x * t), from.y + (from.y * -t) + (to.y * t));
  }
}
