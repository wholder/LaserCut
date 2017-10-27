package test;

import java.awt.geom.Point2D;

  // See: http://math.stackexchange.com/questions/175896/finding-a-point-along-a-line-a-certain-distance-away-from-another-point

public class LineMove {
  @SuppressWarnings("PointlessArithmeticExpression")
  public static void main (String[] args) {
    double radius = Math.sqrt(1 * 1 + 2 * 2);
    Point2D.Double p1 = new Point2D.Double(20, 20);
    Point2D.Double p2 = new Point2D.Double(28, 24);
    Point2D.Double p3 = moveDistance(radius, p1, p2);
    System.out.println("Move distance " + radius + " from " + p1 + " to " + p2);
    System.out.println("Produces: " + p3);
  }

  /**
   * Returns a new Point2D.Double that's a distance of 'dist' away from point 'from' when
   * moving toward point 'to'.
   * @param dist distance to move along line
   * @param from from point
   * @param to to point
   * @return new point that is 'dist' away from 'from' point
   */
  static Point2D.Double moveDistance (double dist, Point2D.Double from, Point2D.Double to) {
    double v = from.distance(to);
    double t = dist / v;
    return new Point2D.Double(from.x + from.x * -t + to.x * t, from.y + from.y * -t + to.y * t);
  }
}