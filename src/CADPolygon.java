import java.awt.*;
import java.awt.geom.Path2D;
import java.io.Serializable;

class CADPolygon extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
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
    centered = true;
  }

  CADPolygon (double xLoc, double yLoc, double diameter, int sides, double rotation, boolean centered) {
    this.diameter = diameter;
    this.sides = sides;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  @Override
  String getName () {
    return "Regular Polygon";
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    diameter = Math.max(Math.sqrt(dx * dx + dy + dy), .1);
  }

  @Override
  String[] getParameterNames () {
    return new String[]{"sides", "diameter|in"};
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
    } else if (sides == 4) {                        // WRH: is this correct
      angle += Math.toRadians(45.0);
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