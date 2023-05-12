import java.awt.*;
import java.awt.geom.Path2D;
import java.io.Serializable;

class CADBobbin extends CADShape implements Serializable {
  private static final long serialVersionUID = 8835012456785552127L;
  public double             width;
  public double             height;
  public double             slotDepth;
  public double             radius;

  /**
   * Default constructor is used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADBobbin () {
    // Set typical initial values, which user can edit before saving
    width = 3.75;
    height = 5.8;
    slotDepth = 1.8;
    radius = 0.125;
  }

  CADBobbin (double xLoc, double yLoc, double width, double height, double slotDepth, double radius, double rotation, boolean centered) {
    this.width = width;
    this.height = height;
    this.slotDepth = slotDepth;
    this.radius = radius;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  @Override
  String getMenuName () {
    return "Bobbin";
  }

  @Override
  String[] getParameterNames () {
    return new String[]{"width|in", "height|in", "slotDepth|in", "radius|in"};
  }

  @Override
  Shape buildShape () {
    // Note: Draw cadShape as if centered on origin
    double xx = -width / 2;
    double yy = -height / 2;
    double tab = .75;
    Path2D.Double polygon = new Path2D.Double();
    if (radius > 0) {
      polygon.moveTo(xx, yy + radius);
      polygon.quadTo(xx, yy, xx + radius, yy);
      polygon.lineTo(xx + tab - radius, yy);
      polygon.quadTo(xx + tab, yy, xx + tab, yy + radius);
      polygon.lineTo(xx + tab, yy + slotDepth - radius);
      polygon.quadTo(xx + tab, yy + slotDepth, xx + tab + radius, yy + slotDepth);
      polygon.lineTo(xx + width - tab - radius, yy + slotDepth);
      polygon.quadTo(xx + width - tab, yy + slotDepth, xx + width - tab, yy + slotDepth - radius);
      polygon.lineTo(xx + width - tab, yy + radius);
      polygon.quadTo(xx + width - tab, yy, xx + width - tab + radius, yy);
      polygon.lineTo(xx + width - radius, yy);
      polygon.quadTo(xx + width, yy, xx + width, yy + radius);
      polygon.lineTo(xx + width, yy + height - radius);
      polygon.quadTo(xx + width, yy + height, xx + width - radius, yy + height);
      polygon.lineTo(xx + width - tab + radius, yy + height);
      polygon.quadTo(xx + width - tab, yy + height, xx + width - tab, yy + height - radius);
      polygon.lineTo(xx + width - tab, yy + height - slotDepth + radius);
      polygon.quadTo(xx + width - tab, yy + height - slotDepth, xx + width - tab - radius, yy + height - slotDepth);
      polygon.lineTo(xx + tab + radius, yy + height - slotDepth);
      polygon.quadTo(xx + tab, yy + height - slotDepth, xx + tab, yy + height - slotDepth + radius);
      polygon.lineTo(xx + tab, yy + height - radius);
      polygon.quadTo(xx + tab, yy + height, xx + tab - radius, yy + height);
      polygon.lineTo(xx + radius, yy + height);
      polygon.quadTo(xx, yy + height, xx, yy + height - radius);
    } else {
      polygon.moveTo(xx, yy);
      polygon.lineTo(xx + tab, yy);
      polygon.lineTo(xx + tab, yy + slotDepth);
      polygon.lineTo(xx + width - tab, yy + slotDepth);
      polygon.lineTo(xx + width - tab, yy);
      polygon.lineTo(xx + width, yy);
      polygon.lineTo(xx + width, yy + height);
      polygon.lineTo(xx + width - tab, yy + height);
      polygon.lineTo(xx + width - tab, yy + height - slotDepth);
      polygon.lineTo(xx + tab, yy + height - slotDepth);
      polygon.lineTo(xx + tab, yy + height);
      polygon.lineTo(xx, yy + height);
    }
    polygon.closePath();
    return polygon;
  }
}
