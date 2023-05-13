import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/*
 * CADScaledShape is a container for a resizable Shape.  Currently used to encapsulate designed loaded
 * by the "Import from SVG, or DXF" features.
 * Note: scale is in percent (%)
 */
class CADScaledShape extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
  private static final long serialVersionUID = -8732521357598212914L;
  public double             scale = 100.0;

  CADScaledShape (Shape shape, double xLoc, double yLoc, double rotation, boolean centered) {
    super(shape, xLoc, yLoc, rotation, centered);
  }

  @Override
  String getMenuName () {
    return "Scaled Shape";
  }

  @Override
  // Translate Shape to screen position
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

  @Override
  protected Point2D.Double getGrabPoint () {
    Rectangle2D bnds = getShapeBounds();
    return new Point2D.Double(bnds.getX() + bnds.getWidth() * scale / 100, bnds.getY() + bnds.getHeight() * scale / 100);
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    Rectangle2D bnds = shape.getBounds2D();
    scale = (Math.min(dx / bnds.getWidth(), dy / bnds.getHeight())) * 100;
  }
}
