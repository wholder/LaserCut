import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CADReference extends CADShape implements Serializable, CADNoDraw {
  private static final long serialVersionUID = 8204176292743368277L;

  /**
   * Default constructor used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADReference () {
    centered = true;
    rotation = 45;
  }

  CADReference (double xLoc, double yLoc) {
    setLocationAndOrientation(xLoc, yLoc, 0, true);
  }

  @Override
  void createAndPlace (DrawSurface surface, LaserCut laserCut) {
    surface.placeShape(this);
  }

  @Override
  String getName () {
    return "Reference Point";
  }

  @Override
  Shape buildShape () {
    return new Rectangle2D.Double(-.1, -.1, .2, .2);
  }

  @Override
  Color getShapeColor () {
    return new Color(0, 128, 0);
  }

  @Override
  BasicStroke getShapeStroke () {
    return Utils2D.getDashedStroke(getStrokeWidth(), 3.0f, 3.0f);
  }

  @Override
  boolean isShapeClicked (Point2D.Double point, double zoomFactor) {
    return super.isShapeClicked(point, zoomFactor) || isPositionClicked(point, zoomFactor);
  }

  @Override
  protected java.util.List<String> getPlaceFields () {
    return new ArrayList<>();
  }

  @Override
  protected List<String> getEditFields () {
    return Arrays.asList(
      "xLoc|in",
      "yLoc|in");
  }
}
