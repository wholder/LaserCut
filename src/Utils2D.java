import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

public class Utils2D {
  static Shape getCircle (Point2D.Double pnt, double radius) {
    return new Ellipse2D.Double(pnt.x - radius, pnt.y - radius, radius * 2, radius * 2);
  }

  static Shape getPlus (Point2D.Double pnt, double size) {
    Path2D.Double plus = new Path2D.Double();
    plus.moveTo(pnt.x, pnt.y - size);
    plus.lineTo(pnt.x, pnt.y + size);
    plus.moveTo(pnt.x - size, pnt.y);
    plus.lineTo(pnt.x + size, pnt.y);
    return plus;
  }

  static Shape getDiamond (Point2D.Double pnt, double size) {
    Path2D.Double diamone = new Path2D.Double();
    diamone.moveTo(pnt.x, pnt.y - size);          // upper
    diamone.lineTo(pnt.x + size, pnt.y);          // right
    diamone.lineTo(pnt.x, pnt.y + size);          // lower
    diamone.lineTo(pnt.x - size, pnt.y);          // left
    diamone.lineTo(pnt.x, pnt.y - size);          // upper
    return diamone;
  }
}
