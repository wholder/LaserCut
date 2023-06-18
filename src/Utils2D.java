import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.List;

import static java.awt.ComponentOrientation.getOrientation;

public class Utils2D {
  /**
   * Generate a Circle Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Circle shape
   * @return Shape object
   */
  static Shape getCircleShape (Point2D.Double pnt, double radius) {
    return new Ellipse2D.Double(pnt.x - radius, pnt.y - radius, radius * 2, radius * 2);
  }

  /**
   * Generate a Plus sign Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Plus sign shape
   * @return Shape object
   */
  static Shape getPlusShape (Point2D.Double pnt, double size) {
    Path2D.Double plus = new Path2D.Double();
    plus.moveTo(pnt.x, pnt.y - size);             // upper (0,-)
    plus.lineTo(pnt.x, pnt.y + size);             // lower (0,+)
    plus.moveTo(pnt.x - size, pnt.y);             // left  (-,0)
    plus.lineTo(pnt.x + size, pnt.y);             // right (+,0)
    return plus;
  }

  /**
   * Generate a Diamond Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Diamond shape
   * @return Shape object
   */
  static Shape getDiamondShape (Point2D.Double pnt, double size) {
    Path2D.Double diamone = new Path2D.Double();
    diamone.moveTo(pnt.x, pnt.y - size);          // upper (0,-)
    diamone.lineTo(pnt.x + size, pnt.y);          // right (+,0)
    diamone.lineTo(pnt.x, pnt.y + size);          // lower (0,+)
    diamone.lineTo(pnt.x - size, pnt.y);          // left  (-,0)
    diamone.lineTo(pnt.x, pnt.y - size);          // upper (0,-)
    return diamone;
  }

  /**
   * Generate an "X" Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of "X" Shape
   * @return Shape object
   */
  static Shape getXShape (Point2D.Double pnt, double size) {
    Path2D.Double xPnt = new Path2D.Double();
    xPnt.moveTo(pnt.x - size, pnt.y - size);   // upper left  (-,-)
    xPnt.lineTo(pnt.x + size, pnt.y + size);   // lower right (+,+)
    xPnt.moveTo(pnt.x - size, pnt.y + size);   // lower left  (-,+)
    xPnt.lineTo(pnt.x + size, pnt.y - size);   // upper right (+,-)
    return xPnt;
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
   * @param List Array of Shape objects
   * @return List of transformed Shape objects
   */
  static List<Shape> removeOffset (List<Shape> shapes) {
    Shape[] shapes1 = shapes.toArray(new Shape[0]);
    if (shapes1.length > 0) {
      Rectangle2D bounds = null;
      for (Shape shape : shapes1) {
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
          for (int ii = 0; ii < shapes1.length; ii++) {
            shapes1[ii] = atScale.createTransformedShape(shapes1[ii]);
          }
        }
      }
    }
    return Arrays.asList(shapes1);
  }

  /**
   * Create a new Shape (Path2D.Double) object that combines all the elements of the List of Shapes
   * @param shapes List of Shape objects
   * @return New Shape object that contains all the features of the Array of Shapes
   */
  static Shape combinePaths (List<Shape> shapes) {
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
   * Combine a List of CADShape object into an Area Shape object
   * @param shapes List of CADShape object
   * @return an Area Shape object
   */
  public static Shape listOfCADShapesToArea (List<CADShape> shapes) {
    Area area = new Area();
    for (CADShape shape : shapes) {
      area.add(new Area(shape.getShape()));
    }
    return area;
  }

  /**
   * Creat preview image for FileChooserMenu (if implemented)
   * @param shape Shape object
   * @return Preview image
   */
  public static BufferedImage getPreviewImage (Shape shape) {
    List<Shape> shapes = new ArrayList<>();
    shapes.add(shape);
    return getPreviewImage(shapes);
  }

  /**
   * Creat preview image for FileChooserMenu (if implemented)
   * @param shapes List of Shape objects
   *               TODO: Needs code to properly scale the image
   * @return Preview image
   */
  public static BufferedImage getPreviewImage (List<Shape> shapes) {
    double scale = 200;
    double border = 0.02;
    if (shapes.size() > 0) {
      Rectangle2D bounds = null;
      // Create a bounding box that's the union of all shapes in the shapes array
      for (Shape shape : shapes) {
        bounds = bounds == null ? BetterBoundingBox.getBounds(shape) : bounds.createUnion(BetterBoundingBox.getBounds(shape));
      }
      int wid = (int) Math.round((bounds.getWidth()  + border * 2) * scale);
      int hyt = (int) Math.round((bounds.getHeight() + border * 2) * scale);
      int maxDim = Math.max(wid, hyt);

      int stroke = Math.max((int) ((double) maxDim / 130), 2);
      //System.out.println("maxDim: " + maxDim + ", stroke: " + stroke);
      BufferedImage img = new BufferedImage(wid, hyt, BufferedImage.SCALE_FAST);
      shapes = removeOffset(shapes);
      Graphics2D g2 = img.createGraphics();
      g2.setColor(Color.white);
      g2.fillRect(0, 0, wid, hyt);
      AffineTransform at = new AffineTransform();
      at.translate(border * scale, border * scale);
      at.scale(scale, scale);
      g2.setStroke(new BasicStroke(stroke));
      g2.setColor(Color.black);
      for (Shape shape : shapes) {
        g2.draw(at.createTransformedShape(shape));
      }
      return img;
    }
    return null;
  }

  /**
   * Convert a set of points for a Catmmull-ROM spline into an equivalent Bézier Curve
   *  See: https://pomax.github.io/bezierinfo/
   *  And: https://gist.github.com/njvack/6925609
   * @param points List of Point objects containing the control points
   * @param close true if curve is closed, else false
   * @return Path2D object containing the Bézier curve
   */
  static Path2D.Double convertPointsToBezier (List<Point2D.Double> points, boolean close) {
    Point2D.Double[] pnts = points.toArray(new Point2D.Double[0]);
    if (!close) {
      // Duplicate last point so we can draw a curve through all points in the path
      Point2D.Double[] newpnts = new Point2D.Double[pnts.length + 1];
      System.arraycopy(pnts, 0, newpnts, 0, pnts.length);
      newpnts[newpnts.length - 1] = pnts[pnts.length - 1];
      pnts = newpnts;
    }
    Path2D.Double path = new Path2D.Double();
    path.moveTo(pnts[0].x, pnts[0].y);
    int end = close ? pnts.length + 1 : pnts.length - 1;
    for (int ii = 0; ii < end - 1; ii++) {
      Point2D.Double p0, p1, p2, p3;
      if (close) {
        int idx0 = Math.floorMod(ii - 1, pnts.length);
        int idx1 = Math.floorMod(idx0 + 1, pnts.length);
        int idx2 = Math.floorMod(idx1 + 1, pnts.length);
        int idx3 = Math.floorMod(idx2 + 1, pnts.length);
        p0 = new Point2D.Double(pnts[idx0].x, pnts[idx0].y);
        p1 = new Point2D.Double(pnts[idx1].x, pnts[idx1].y);
        p2 = new Point2D.Double(pnts[idx2].x, pnts[idx2].y);
        p3 = new Point2D.Double(pnts[idx3].x, pnts[idx3].y);
      } else {
        p0 = new Point2D.Double(pnts[Math.max(ii - 1, 0)].x, pnts[Math.max(ii - 1, 0)].y);
        p1 = new Point2D.Double(pnts[ii].x, pnts[ii].y);
        p2 = new Point2D.Double(pnts[ii + 1].x, pnts[ii + 1].y);
        p3 = new Point2D.Double(pnts[Math.min(ii + 2, pnts.length - 1)].x, pnts[Math.min(ii + 2, pnts.length - 1)].y);
      }
      // Catmull-Rom to Cubic Bezier conversion matrix
      //    0       1       0       0
      //  -1/6      1      1/6      0
      //    0      1/6      1     -1/6
      //    0       0       1       0
      double x1 = (-p0.x + 6 * p1.x + p2.x) / 6;  // First control point
      double y1 = (-p0.y + 6 * p1.y + p2.y) / 6;
      double x2 = (p1.x + 6 * p2.x - p3.x) / 6;  // Second control point
      double y2 = (p1.y + 6 * p2.y - p3.y) / 6;
      double x3 = p2.x;                           // End point
      double y3 = p2.y;
      path.curveTo(x1, y1, x2, y2, x3, y3);
    }
    if (close) {
      path.closePath();
    }
    return path;
  }

  /*
   *  Debuging tools
   *  See: https://alvinalexander.com/programming/printf-format-cheat-sheet/
   *  Note: in this format string "%7.3f" the value of 7 sets the total space used
   */

  public static void printPoint (Point2D.Double point) {
    System.out.printf("x = %7.3f, y = %7.3f\n", point.x, point.y);
  }

  public static void printPoint (String prefix, Point2D.Double point) {
    System.out.printf("%s: x = %7.3f, y = %7.3f\n", prefix, point.x, point.y);
  }

  public static void printDimension (Dimension dimension) {
    System.out.printf("width = %d, width = %d\n", dimension.width, dimension.height);
  }

  public static void printDimension (String prefix, Dimension dimension) {
    System.out.printf("%s: width = %d, width = %d\n", prefix, dimension.width, dimension.height);
  }


  public static void printRect (Rectangle2D.Double rect) {
    System.out.printf("x = %7.3f, y = %7.3f, wid = %7.3f, hyt = %7.3f\n", rect.x, rect.y, rect.width, rect.height);
  }

  public static void printRect (String prefix, Rectangle2D.Double rect) {
    System.out.printf("%s: x = %7.3f, y = %7.3f, wid = %7.3f, hyt = %7.3f\n",prefix,  rect.x, rect.y, rect.width, rect.height);
  }
}
