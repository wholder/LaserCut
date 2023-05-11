import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.Serializable;

class CADOval extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
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
    centered = true;
  }

  CADOval (double xLoc, double yLoc, double width, double height, double rotation, boolean centered) {
    this.width = width;
    this.height = height;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  @Override
  String getName () {
    return "Oval";
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    width = Math.max(centered ? dx * 2 : dx, .1);
    height = Math.max(centered ? dy * 2 : dy, .1);
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
