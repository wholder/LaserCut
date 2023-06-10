import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.Serializable;

class CADOval extends CADShape implements Serializable {
  private static final long serialVersionUID = 2518641166287730832L;
  public                    double width, height;

  /**
   * Default constructor used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADOval () {
    // Set typical initial values, which user can edit before saving
    width = .5;
    height = .5;
  }

  CADOval (double xLoc, double yLoc, double width, double height, double rotation) {
    this.width = width;
    this.height = height;
    setLocationAndOrientation(xLoc, yLoc, rotation);
  }

  @Override
  String getMenuName () {
    return "Oval";
  }

  @Override
  public void resize (double dx, double dy) {
    width = Math.max(dx * 2, .1);
    height = Math.max(dy * 2, .1);
  }

  @Override
  String[] getParameterNames () {
    return new String[]{
      "width|in",
      "height|in"};
  }

  @Override
  Shape buildShape () {
    return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
  }
}
