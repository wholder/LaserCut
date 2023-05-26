import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class Utils2D {
  /**
   * Generate a Circle Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Circle shape
   * @return Shape object
   */
  static Shape getCircle (Point2D.Double pnt, double radius) {
    return new Ellipse2D.Double(pnt.x - radius, pnt.y - radius, radius * 2, radius * 2);
  }

  /**
   * Generate a Plus sign Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Plus sign shape
   * @return Shape object
   */
  static Shape getPlus (Point2D.Double pnt, double size) {
    Path2D.Double plus = new Path2D.Double();
    plus.moveTo(pnt.x, pnt.y - size);
    plus.lineTo(pnt.x, pnt.y + size);
    plus.moveTo(pnt.x - size, pnt.y);
    plus.lineTo(pnt.x + size, pnt.y);
    return plus;
  }

  /**
   * Generate a Diamond Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Diamond shape
   * @return Shape object
   */
  static Shape getDiamond (Point2D.Double pnt, double size) {
    Path2D.Double diamone = new Path2D.Double();
    diamone.moveTo(pnt.x, pnt.y - size);          // upper
    diamone.lineTo(pnt.x + size, pnt.y);          // right
    diamone.lineTo(pnt.x, pnt.y + size);          // lower
    diamone.lineTo(pnt.x - size, pnt.y);          // left
    diamone.lineTo(pnt.x, pnt.y - size);          // upper
    return diamone;
  }


  public static Path2D.Double getArrow (double x1, double y1, double x2, double y2, boolean atEnd) {
    Path2D.Double path = new Path2D.Double();
    double angleOff = Math.toRadians(10);
    int barb = 10;
    if (atEnd) {
      double angle = Math.atan2(y2 - y1, x2 - x1);
      double ax1 = x2 - barb * Math.cos(angle - angleOff);
      double ay1 = y2 - barb * Math.sin(angle - angleOff);
      double ax2 = x2 - barb * Math.cos(angle + angleOff);
      double ay2 = y2 - barb * Math.sin(angle + angleOff);
      path.moveTo(ax1, ay1);
      path.lineTo(x2, y2);
      path.lineTo(ax2, ay2);
    } else {
      double angle = Math.atan2(y1 - y2, x1 - x2);
      double ax1 = x1 - barb * Math.cos(angle - angleOff);
      double ay1 = y1 - barb * Math.sin(angle - angleOff);
      double ax2 = x1 - barb * Math.cos(angle + angleOff);
      double ay2 = y1 - barb * Math.sin(angle + angleOff);
      path.moveTo(ax1, ay1);
      path.lineTo(x1, y1);
      path.lineTo(ax2, ay2);
    }
    path.closePath();
    return path;
  }

  /**
   *  CAP_BUTT -   Ends unclosed subpaths and dash segments with no added decoration.
   *  CAP_ROUND -  Ends unclosed subpaths and dash segments with a round decoration that has a radius equal
   *               to half of the width of the pen.
   *  CAP_SQUARE - Ends unclosed subpaths and dash segments with a square projection that extends beyond
   *               the end of the segment to a distance equal to half of the line width
   *  JOIN_BEVEL - Joins path segments by connecting the outer corners of their wide outlines with a
   *               straight segment.
   *  JOIN_MITER - Joins path segments by extending their outside edges until they meet.
   * @param strokeWidth Width of stroke
   * @param dashOn Length of drawn portion of dashed line
   * @param dashOff Length of transparent portion of dashed line
   * @return BasicStroke object
   */
  static BasicStroke getDashedStroke (float strokeWidth, float dashOn, float dashOff) {
    // Alternate between these two values for length (first is drawn, second is transparent)
    final float[] dash1 = {dashOn, dashOff};
    // BasicStroke(float width, int cap, int join, float miterlimit, float[] dash, float dash_phase)
    //  width - the width of this BasicStroke (must be >= 0.0f)
    //  cap - the decoration of the ends of a BasicStroke (must be CAP_BUTT, CAP_ROUND or CAP_SQUARE)
    //  join - the decoration applied where path segments meet (must be JOIN_ROUND, JOIN_BEVEL, or JOIN_MITER)
    //  miterlimit - the limit to trim the miter join (must be >= 1.0f)
    //  dash - the array representing the dashing pattern
    //  dash_phase - the offset to start the dashing pattern
    return new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, dash1, 1.0f);
  }

  /**
   * Checks, whether the given rectangle1 fully contains rectangle 2 (even if rectangle 2 has a height or
   * width of zero!). Unlike the way Java2D handlies this!
   *
   * @param rect1 the first rectangle.
   * @param rect2 the second rectangle.
   * @return true if first contains second.
   * @author David Gilbert
   */

  public static boolean contains (Rectangle2D rect1, Rectangle2D rect2) {
    final double x0 = rect1.getX();
    final double y0 = rect1.getY();
    final double x = rect2.getX();
    final double y = rect2.getY();
    final double w = rect2.getWidth();
    final double h = rect2.getHeight();
    return ((x >= x0) && (y >= y0) && ((x + w) <= (x0 + rect1.getWidth())) && ((y + h) <= (y0 + rect1.getHeight())));
  }

  /**
   * Convert inches to centimeters
   * @param inches measurement in inches
   * @return measurement in centimeters
   */
  static double inchesToCm (double inches) {
    return inches * 2.54;
  }

  /**
   * Convert millimeters to inches
   * @param mm measurement in millimeters
   * @return measurement in inches
   */
  static double mmToInches (double mm) {
    return mm / 25.4;
  }

  /**
   * Convert inches to millimeters
   * @param inches measurement in inches
   * @return measurement in millimeters
   */
  static double inchesToMM (double inches) {
    return inches * 25.4;
  }

  /**
   * Convert millimeters to inches
   * @param cm measurement in centimeters
   * @return measurement in inches
   */
  static double cmToInches (double cm) {
    return cm / 2.54;
  }

  /**
   * Convert millimeters to centimeters
   * @param mm measurement in millimeters
   * @return measurement in centimeters
   */
  static double mmToCm (double mm) {
    return mm / 10;
  }

  /**
   *
   * @param file path/name of resource file
   * @return String object of file contents
   */
  public static String getResourceFile (String file) {
    try {
      InputStream fis = LaserCut.class.getResourceAsStream(file);
      if (fis != null) {
        byte[] data = new byte[fis.available()];
        fis.read(data);
        fis.close();
        return new String(data);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "";
  }

  /**
   * Concert String object's contents into a Properties object
   * @param content
   * @return
   */
  public static Properties getProperties (String content) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(content));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return props;
  }

  /**
   * Uses FlatteningPathIterator to convert the Shape into List of arrays of lines.  The size input Shape
   * is assumed to be defined in inches, but the AffineTransform parameter can be used to scale up to the
   * final render resolution.  Note: cubic and quadratic bezier curves calculate an approximaiton of the
   * arc length of the curve to determine the number of line segments used to approximate the curve.
   *
   * @param shape   Shape path to render
   * @param scale   used to scale from inches to the render resolution, such as Screen or Laser DPI.
   * @param flatten controls how closely the line segments follow the curve (smaller is closer)
   * @return List of array of lines
   */
  static java.util.List<Line2D.Double[]> transformShapeToLines (Shape shape, double scale, double flatten) {
    // Convert Shape into a series of lines defining a path
    java.util.List<Line2D.Double[]> paths = new ArrayList<>();
    AffineTransform at = scale != 1.0 ? AffineTransform.getScaleInstance(scale, scale) : null;
    ArrayList<Line2D.Double> lines = new ArrayList<>();
    // Use FlatteningPathIterator to convert to line segments
    PathIterator pi = shape.getPathIterator(at);
    FlatteningPathIterator fpi = new FlatteningPathIterator(pi, flatten, 8);
    double[] coords = new double[4];
    double lastX = 0, lastY = 0, firstX = 0, firstY = 0;
    while (!fpi.isDone()) {
      int type = fpi.currentSegment(coords);
      switch (type) {
        case FlatteningPathIterator.SEG_MOVETO:
          if (lines.size() > 0) {
            paths.add(lines.toArray(new Line2D.Double[0]));
            lines = new ArrayList<>();
          }
          firstX = lastX = coords[0];
          firstY = lastY = coords[1];
          lines = new ArrayList<>();
          break;
        case FlatteningPathIterator.SEG_LINETO:
          lines.add(new Line2D.Double(lastX, lastY, coords[0], coords[1]));
          lastX = coords[0];
          lastY = coords[1];
          break;
        case FlatteningPathIterator.SEG_CLOSE:
          if (lastX != firstX || lastY != firstY) {
            lines.add(new Line2D.Double(lastX, lastY, firstX, firstY));
          }
          break;
      }
      fpi.next();
    }
    if (lines.size() > 0) {
      paths.add(lines.toArray(new Line2D.Double[0]));
      lines = new ArrayList<>();
    }
    return paths;
  }

  /**
   * Rotate 2D point around anchor point
   *
   * @param point Point to rotate
   * @param angle Angle to rotate
   * @return Rotated 2D point
   */
  public static Point2D.Double rotateAroundPoint (Point2D.Double anchor, Point2D.Double point, double angle) {
    AffineTransform center = AffineTransform.getRotateInstance(Math.toRadians(angle), anchor.x, anchor.y);
    Point2D.Double np = new Point2D.Double();
    center.transform(point, np);
    return np;
  }

  /**
   * Convert a boolean array into an integer where the each value in the array is converted to a bit
   * in the integer by successively converting each boolean value into a 1 or 0 and then left shifting
   * into the value.  Note: the first value in the array becomes the left-most bit in the value.
   * @param bools boolean array
   * @return integer value
   */
  public static int booleansToInt (boolean[] bools) {
    int value = 0;
    for (boolean bool : bools) {
      value *= 2;
      value |= bool ? 1 : 0;
    }
    return value;
  }

  public static Path2D.Double convertPointsToPath (Point2D.Double[] points, boolean close) {
    Path2D.Double path = new Path2D.Double();
    path.moveTo(points[0].x, points[0].y);
    for (Point2D.Double point : points) {
      path.lineTo(point.x, point.y);
    }
    if (close) {
      path.closePath();
    }
    return path;
  }

  /**
   * Rotate 2D point around 0,0 point
   *
   * @param point Point to rotate
   * @param angle Angle to rotate
   * @return Rotated 2D point
   */
  public static Point2D.Double rotatePoint (Point2D.Double point, double angle) {
    AffineTransform center = AffineTransform.getRotateInstance(Math.toRadians(angle), 0, 0);
    Point2D.Double np = new Point2D.Double();
    center.transform(point, np);
    return np;
  }

  /**
   * Scan all Shapes objects to determine x/y offset, if any, then remove offset.
   * @param shapes Array of Shape objects
   * @return Array of transformed Shape objects
   */
  static Shape[] removeOffset (Shape[] shapes) {
    if (shapes.length > 0) {
      Rectangle2D bounds = null;
      for (Shape shape : shapes) {
        if (bounds == null) {
          bounds = BetterBoundingBox.getBounds(shape);
        } else {
          try {
            bounds = bounds.createUnion(BetterBoundingBox.getBounds(shape));
          } catch (NullPointerException ex) {
            ex.printStackTrace();
          }
        }
      }
      if (bounds != null) {
        double minX = bounds.getX();
        double minY = bounds.getY();
        if (minX != 0 || minY != 0) {
          AffineTransform atScale = AffineTransform.getTranslateInstance(-minX, -minY);
          for (int ii = 0; ii < shapes.length; ii++) {
            shapes[ii] = atScale.createTransformedShape(shapes[ii]);
          }
        }
      }
    }
    return shapes;
  }

  /**
   * Create a new Shape (Path2D.Double) object that combines all the elements of the Array of Shapes
   * @param shapes Array of Shape objects
   * @return New Shape object that contains all the features of the Array of Shapes
   */
  static Shape combinePaths (Shape[] shapes) {
    Path2D.Double newShape = new Path2D.Double();
    AffineTransform atScale = AffineTransform.getTranslateInstance(0, 0);
    for (Shape shape : shapes) {
      newShape.append(shape.getPathIterator(atScale), false);
    }
    return newShape;
  }

  /**
   * Report the bounding box for all points
   *
   * @param points the provided points
   * @return the smallest rectangle that really contains all the points.
   */
  public static Rectangle2D.Double boundsOf (Collection<? extends Point2D.Double> points) {
    if ((points == null) || points.isEmpty()) {
      return null;
    }
    Rectangle2D.Double bounds = new Rectangle2D.Double();
    for (Point2D.Double point : points) {
      bounds.add(point);
    }
    return bounds;
  }

  /**
   * Scale a Point2D.Double object
   * @param pnt Point2D.Double to scale
   * @param scale scale factor (0 - 100)
   * @return scaled Point2D.Double object
   */
  public static Point2D.Double scalePoint (Point2D.Double pnt, double scale) {
    return new Point2D.Double(pnt.x * scale / 100, pnt.y * scale / 100);
  }

  /**
   * Scale a Rectangle2D.Double object
   * @param rect Rectangle2D.Double object to scale
   * @param scale scale factor (0 - 100)
   * @return scaled Rectangle2D.Double object
   */
  public static Rectangle2D.Double scaleRect (Rectangle2D.Double rect, double scale) {
    double sc = scale / 100;
    return new Rectangle2D.Double(rect.x * sc, rect.y * sc, rect.width * sc, rect.height * sc);
  }

  /**
   * Creat preview image for FileChooserMenu (if implemented)
   * @param inShape
   * @return
   */
  public static BufferedImage getPreviewImage (CADShape inShape) {
    Shape shape = inShape.getWorkspaceTranslatedShape();
    Rectangle2D.Double bnds1 = (Rectangle2D.Double) shape.getBounds2D();
    AffineTransform at = new AffineTransform();
    at.translate(-bnds1.x, -bnds1.y);
    shape = at.createTransformedShape(shape);
    at = AffineTransform.getScaleInstance(LaserCut.SCREEN_PPI, LaserCut.SCREEN_PPI);
    shape = at.createTransformedShape(shape);
    Rectangle2D.Double bnds2 = (Rectangle2D.Double) shape.getBounds2D();
    int wid = (int) bnds2.width;
    int hyt = (int) bnds2.height;
    BufferedImage buf = new BufferedImage(wid, (int) hyt, Image.SCALE_FAST);
    Graphics2D g2 = buf.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(Color.white);
    g2.fill(new Rectangle2D.Double(0, 0, wid, hyt));
    g2.setColor(Color.black);
    g2.setStroke(new BasicStroke(3));
    g2.drawRect(1, 1, wid - 2, hyt - 2);
    g2.draw(shape);
    return buf;
  }

  /*
   *  Debuging tools
   */

  public static void printPoint (Point2D.Double point) {
    System.out.printf("x = %3.3f, y = %3.3f\n", point.x, point.y);
  }

  public static void printPoint (String prefix, Point2D.Double point) {
    System.out.printf("%s: x = %3.3f, y = %3.3f\n", prefix, point.x, point.y);
  }

  public static void printRect (Rectangle2D.Double rect) {
    System.out.printf("x = %3.3f, y = %3.3f, wid = %3.3f, hyt = %3.3f\n", rect.x, rect.y, rect.width, rect.height);
  }

  public static void printRect (String prefix, Rectangle2D.Double rect) {
    System.out.printf("%s: x = %3.3f, y = %3.3f, wid = %3.3f, hyt = %3.3f\n",prefix,  rect.x, rect.y, rect.width, rect.height);
  }
}
