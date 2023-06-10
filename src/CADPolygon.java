import java.awt.*;
import java.awt.geom.Path2D;
import java.io.Serializable;

class CADPolygon extends CADShape implements Serializable {
  private static final long serialVersionUID = 973284612591842108L;
  public int                sides;
  public double             diameter;

  /**
   * Default constructor used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADPolygon () {
    // Set typical initial values, which user can edit before saving
    diameter = 1.0;
    sides = 6;
  }

  CADPolygon (double xLoc, double yLoc, double diameter, int sides, double rotation) {
    this.diameter = diameter;
    this.sides = sides;
    setLocationAndOrientation(xLoc, yLoc, rotation);
  }

  @Override
  String getMenuName () {
    return "Regular Polygon";
  }

  @Override
  public void resize (double dx, double dy) {
    diameter = Math.max(Math.sqrt(dx * dx + dy + dy), .1);
  }

  @Override
  String[] getParameterNames () {
    return new String[]{
      "sides{number of sides}",
      "diameter|in"};
  }

  @Override
  Shape buildShape () {
    //return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
    Path2D.Double poly = new Path2D.Double();
    double radius = diameter / 2;
    double theta = 2 * Math.PI / sides;
    double angle = -Math.PI / 2;
    // Adjust angle, where needed, to draw shapes in familiar orientation
    if (sides % 2 == 0) {
      angle += Math.toRadians(180.0 / sides);
    }
    boolean first = true;
    for (int i = 0; i < sides; ++i) {
      double x = Math.cos(angle) * radius;
      double y = Math.sin(angle) * radius;
      angle += theta;
      if (first) {
        poly.moveTo(x, y);
      } else {
        poly.lineTo(x, y);
      }
      first = false;
    }
    poly.closePath();
    return poly;
  }
}
