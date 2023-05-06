import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

class CNCPath extends CADShape implements Serializable, LaserCut.ChangeListener {
  private static final long serialVersionUID = 940023721688314265L;
  private final CADShape          baseShape;
  public double             radius;
  public boolean            inset;

  CNCPath (CADShape base, double radius, boolean inset) {
    this.baseShape = base;
    this.radius = Math.abs(radius);
    this.inset = inset;
    baseShape.addChangeListener(this);
  }

  @Override
  String getName () {
    return "CNC Path";
  }

  /*
   * Reattach ChangeListener after deserialization
   */
  private void readObject (java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    baseShape.addChangeListener(this);
  }

  public void shapeChanged (CADShape base) {
    updateShape();
  }

  @Override
  protected List<String> getEditFields () {
    return Arrays.asList("radius|in{radius of tool}", "inset{If checked, toolpath is inside cadShape, else outside}");
  }

  @Override
  Color getShapeColor () {
    return new Color(0, 153, 0);
  }

  @Override
  protected Shape getLocallyTransformedShape () {
    Shape dShape = getShape();
    AffineTransform at = new AffineTransform();
    // Position Shape centered on xLoc/yLoc in inches (x from left, y from top)
    at.rotate(Math.toRadians(rotation));
    if (!centered) {
      // Translate relative to the baseShape's coordinates so the generated cnc path aligns with it
      Rectangle2D bounds = baseShape.getShape().getBounds2D();
      at.translate(bounds.getWidth() / 2, bounds.getHeight() / 2);
    }
    return at.createTransformedShape(dShape);
  }

  @Override
  Shape buildShape () {
    xLoc = baseShape.xLoc;
    yLoc = baseShape.yLoc;
    centered = baseShape.centered;
    rotation = baseShape.rotation;
    Path2D.Double path = new Path2D.Double();
    boolean first = true;
    for (Line2D.Double[] lines : transformShapeToLines(baseShape.getShape(), 1.0, .01)) {
      if (false) {
        DecimalFormat df = new DecimalFormat("#.###");
        for (Line2D.Double line : lines) {
          if (line.x1 == line.x2 && line.y1 == line.y2) {
            int dum = 0;
          } else {
            double x1 = (line.x1 + .15) * 4000;
            double y1 = (line.y1 + .15) * 4000;
            double x2 = (line.x2 + .15) * 4000;
            double y2 = (line.y2 + .15) * 4000;
          }
        }
      }
      Point2D.Double[] points = CNCTools.pruneOverlap(CNCTools.getParallelPath(lines, radius, !inset));
      for (Point2D.Double point : points) {
        if (first) {
          path.moveTo(point.x, point.y);
          first = false;
        } else {
          path.lineTo(point.x, point.y);
        }
      }
      // Connect back to beginning
      path.lineTo(points[0].x, points[0].y);
      first = true;
    }
    return path;
  }
}
