import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/*
 * CADScaledShape is a container for a resizable Shape.  Currently used to encapsulate designed loaded
 * by the "Import from SVG" feature.
 * Note: scale is in percent (%)
 */
class CADScaledShape extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
  private static final long serialVersionUID = -8732521357598212914L;
  public double             scale = 100.0;

  CADScaledShape (Shape shape, double xLoc, double yLoc, double rotation) {
    super(shape, xLoc, yLoc, rotation);
  }

  @Override
  String getMenuName () {
    return "Scaled Shape";
  }

  // Translate Shape to screen position
  @Override
  protected Shape getWorkspaceTranslatedShape () {
    AffineTransform at = new AffineTransform();
    at.translate(xLoc, yLoc);
    at.scale(scale / 100.0, scale / 100.0);
    return at.createTransformedShape(getLocallyTransformedShape());
  }

  @Override
  protected java.util.List<String> getPlaceFields () {
    ArrayList<String> list = new ArrayList<>(super.getPlaceFields());
    list.add("scale|%");
    return list;
  }

  @Override
  protected List<String> getEditFields () {
    ArrayList<String> list = new ArrayList<>(super.getEditFields());
    list.add("scale|%");
    return list;
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    Rectangle2D bnds = shape.getBounds2D();
    scale = (Math.min(dx / bnds.getWidth(), dy / bnds.getHeight())) * 100;
  }
}
