import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.Serializable;

class CADRectangle extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
  private static final long serialVersionUID = 5415641155292738232L;
  public double             width, height, radius;

  /**
   * Default constructor is used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADRectangle () {
    // Set typical initial values, which user can edit before saving
    width = 1;
    height = 1;
  }

  CADRectangle (double xLoc, double yLoc, double width, double height, double radius, double rotation, boolean centered) {
    this.width = width;
    this.height = height;
    this.radius = radius;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  @Override
  String getMenuName () {
    return "Rectangle";
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    width = Math.max(centered ? dx * 2 : dx, .1);
    height = Math.max(centered ? dy * 2 : dy, .1);
  }

  @Override
  String[] getParameterNames () {
    return new String[]{
      "width|in{width of rectangle}",
      "height|in{height of rectangle}",
      "radius|in{radius of corner}"};
  }

  @Override
  Shape buildShape () {
    if (radius > 0) {
      // Note: specifiy 2 x radius for arc height & width
      return new RoundRectangle2D.Double(-width / 2, -height / 2, width, height, radius * 2, radius * 2);
    } else {
      return new Rectangle2D.Double(-width / 2, -height / 2, width, height);
    }
  }
}
