import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/*
 * CADScaledShape is a container for a resizable Shape.  Currently used to encapsulate designed loaded
 * by the "Import from SVG" feature.
 */
class CADScaledShape extends CADShape implements Serializable {
  private static final long serialVersionUID = -8732521357598212914L;
  public double             scale;

  CADScaledShape (Shape shape, double xLoc, double yLoc, double rotation, boolean centered, double scale) {
    super(shape, xLoc, yLoc, rotation, centered);
    this.scale = scale;
  }

  @Override
  String getName () {
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
}
