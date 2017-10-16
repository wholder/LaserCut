import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.geom.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

  // https://www.w3schools.com/graphics/svg_examples.asp
  // http://ptolemy.eecs.berkeley.edu/ptolemyII/ptII8.1/ptII8.0.1/diva/canvas/toolbox/SVGParser.java
  // https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial/Paths
  // https://www.w3.org/TR/SVG/paths.html
  // http://tutorials.jenkov.com/svg/svg-viewport-view-box.html

/**
 * This is my attempt at a crude parser that tries to extract the vector portion of an SVG file
 */

public class SVGParser {
  private boolean         debug, pxIs72;
  private DecimalFormat   df = new DecimalFormat("#.###");
  private DecimalFormat   sf = new DecimalFormat("#.########");
  private double          scaleX = 1, scaleY = 1;
  private boolean         inMarker;
  private AffineTransform gTrans;

  public SVGParser (boolean pxIs72) {
    this.pxIs72 = pxIs72;
  }

  public void enableDebug (boolean enable) {
    debug = enable;
  }

  public Shape[] parseSVG (File file) throws Exception {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    // Turn off validation to improve parsing speed
    factory.setNamespaceAware(false);
    factory.setValidating(false);
    factory.setFeature("http://xml.org/sax/features/namespaces", false);
    factory.setFeature("http://xml.org/sax/features/validation", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    SAXParser saxParser = factory.newSAXParser();
    ArrayList<Shape> shapes = new ArrayList<>();
    DefaultHandler handler = new DefaultHandler() {
      AffineTransform atScale = AffineTransform.getScaleInstance(1 / 96d, 1 / 96d);
      public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {
        debugPrintln("Start Element: " + qName);
        double xLoc, yLoc, xLast = 0, yLast = 0, width, height, rx, ry;
        Point2D.Double p1, p2, p3, reflect = new Point2D.Double(0, 0);
        String pointData;
        Point2D.Double[] points;
        Shape shape = null;
        switch (qName.toLowerCase()) {
          case "marker":
            inMarker = true;
            break;
          case "g":
            String trans = attributes.getValue("transform");
            if (trans != null) {
              gTrans = getSVGTransform(trans, atScale);
            }
            break;
          case "svg":
            String wAttr = attributes.getValue("width");
            String hAttr = attributes.getValue("height");
            String vBox = attributes.getValue("viewBox");
            if (vBox != null && wAttr != null && hAttr != null) {
              double[] vb = parseRawCoords(vBox);
              double wid = getInches(attributes.getValue("width"));
              double hyt = getInches(attributes.getValue("height"));
              atScale = AffineTransform.getScaleInstance(scaleX = wid / vb[2], scaleY = hyt / vb[3]);
              debugPrintln("svg: scale " + sf.format(atScale.getScaleX()) + ", " + sf.format(atScale.getScaleY()));
            } else if (vBox != null) {
              double[] vb = parseRawCoords(vBox);
              debugPrintln("viewBox: " + vb[0] + ", " + vb[1] + ", " + vb[2] + ", " + vb[3]);
            } else {
              //atScale.setToScale(wid, hyt);
              //debugPrintln("svg: scale " + sf.format(atScale.getScaleX()) + ", " + sf.format(atScale.getScaleY()));
            }
            break;
          case "path":
            if (!inMarker) {
              String[] data = parsePathData(attributes.getValue("d"));
              debugPrintln("path:");
              Path2D.Double path = new Path2D.Double();
              for (int ii = 0; ii < data.length; ii++) {
                String item = data[ii];
                if (item.length() == 1 && Character.isAlphabetic(item.charAt(0))) {
                  boolean relative = item.equals(item.toLowerCase());
                  switch (item.toLowerCase()) {
                    case "m":
                      xLoc = relative ? parseCoord(data[++ii]) + xLast : parseCoord(data[++ii]);
                      yLoc = relative ? parseCoord(data[++ii]) + yLast : parseCoord(data[++ii]);
                      path.moveTo(xLast = xLoc, yLast = yLoc);
                      debugPrintln("  MoveTo: " + scX(xLoc) + ", " + scY(yLoc));
                      while (ii < (data.length - 2) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0)))) {
                        xLoc = relative ? parseCoord(data[++ii]) + xLast : parseCoord(data[++ii]);
                        yLoc = relative ? parseCoord(data[++ii]) + yLast : parseCoord(data[++ii]);
                        path.lineTo(xLast = xLoc, yLast = yLoc);
                        debugPrintln("  LineTo: " + scX(xLoc) + ", " + scY(yLoc));
                      }
                      break;
                    case "c":
                      do {
                        // Cubic Bézier curve (start point is lastPoint)
                        p1 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // 1st ctrl point
                        p2 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // 2nd ctrl point
                        p3 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // End point
                        if (relative) {
                          p1.setLocation(p1.x + xLast, p1.y + yLast);
                          p2.setLocation(p2.x + xLast, p2.y + yLast);
                          p3.setLocation(p3.x + xLast, p3.y + yLast);
                        }
                        path.curveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y);
                        debugPrintln("  Cubic-" + item + ": " + scp(p1) + ", " + scp(p2) + ", " + scp(p3));
                        reflect = reflectControlPoint(p3, p2);
                        xLast = p3.x;
                        yLast = p3.y;
                      } while (ii < (data.length - 6) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0))));
                      break;
                    case "s":
                      do {
                        // Shorthand Cubic Bézier curve (start point is lastPoint, 1st ctrl point is reflected from prior C curve)
                        p1 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // 2nd ctrl point
                        p2 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // End point
                        if (relative) {
                          p1.setLocation(p1.x + xLast, p1.y + yLast);
                          p2.setLocation(p2.x + xLast, p2.y + yLast);
                        }
                        path.curveTo(reflect.x, reflect.y, p1.x, p1.y, p2.x, p2.y);
                        debugPrintln("  Cubic-" + item + ": " + scp(reflect) + ", " + scp(p1) + ", " + scp(p2));
                        reflect = reflectControlPoint(p2, p1);
                        xLast = p2.x;
                        yLast = p2.y;
                      } while (ii < (data.length - 4) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0))));
                      break;
                    case "q":
                      do {
                        // Quadratic Bézier curveto (start point is lastPoint)
                        p1 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // ctrl point
                        p2 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // End point
                        if (relative) {
                          p1.setLocation(p1.x + xLast, p1.y + yLast);
                          p2.setLocation(p2.x + xLast, p2.y + yLast);
                        }
                        path.quadTo(p1.x, p1.y, p2.x, p2.y);
                        debugPrintln("  Quad-" + item + ": " + scp(p1) + ", " + scp(p2));
                        reflect = reflectControlPoint(p2, p1);
                        xLast = p2.x;
                        yLast = p2.y;
                      } while (ii < (data.length - 4) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0))));
                      break;
                    case "t":
                      do {
                        // Shorthand quadratic Bézier curveto  (start point is lastPoint, ctrl point is reflected from prior Q curve)
                        p1 = new Point2D.Double(parseCoord(data[++ii]), parseCoord(data[++ii]));  // End point
                        if (relative) {
                          p1.setLocation(p1.x + xLast, p1.y + yLast);
                        }
                        path.quadTo(reflect.x, reflect.y, p1.x, p1.y);
                        debugPrintln("  Quad-" + item + ": " + scp(reflect) + ", " + scp(p1));
                        reflect = reflectControlPoint(p1, reflect);
                        xLast = p1.x;
                        yLast = p1.y;
                      } while (ii < (data.length - 2) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0))));
                      break;
                    case "a":
                      // Elliptical Arc (7 values?)
                      //
                      break;
                    case "l":
                      do {
                        // LineTo (2 points)
                        xLoc = relative ? parseCoord(data[++ii]) + xLast : parseCoord(data[++ii]);
                        yLoc = relative ? parseCoord(data[++ii]) + yLast : parseCoord(data[++ii]);
                        path.lineTo(xLast = xLoc, yLast = yLoc);
                        debugPrintln("  LineTo: " + scX(xLoc) + ", " + scY(yLoc));
                      } while (ii < (data.length - 2) && (data[ii + 1].length() > 1 || !Character.isAlphabetic(data[ii + 1].charAt(0))));
                      break;
                    case "h":
                      // Horizontal LineTo (xLoc)
                      xLoc = relative ? parseCoord(data[++ii]) + xLast : parseCoord(data[++ii]);
                      path.lineTo(xLast = xLoc, yLast);
                      debugPrintln("  HLineTo: " + scX(xLoc) + ", " + scY(yLast));
                      break;
                    case "v":
                      // Vertical LineTo (yLoc)
                      yLoc = relative ? parseCoord(data[++ii]) + yLast : parseCoord(data[++ii]);
                      path.lineTo(xLast, yLast = yLoc);
                      debugPrintln("  VLineTo: " + scX(xLast) + ", " + scY(yLoc));
                      break;
                    case "z":
                      // Close Path
                      path.closePath();
                      break;
                    default:
                      debugPrintln("  *** unknown cmd '" + item + "' ***");
                      break;
                  }
                }
              }
              shape = path;
            }
            break;
          case "rect":
            xLoc = parseCoord(attributes.getValue("x"));
            yLoc = parseCoord(attributes.getValue("y"));
            width = parseCoord(attributes.getValue("width"));
            height = parseCoord(attributes.getValue("height"));
            rx = parseCoord(attributes.getValue("rx"));
            ry = parseCoord(attributes.getValue("ry"));
            if (rx > 0 && ry == 0) ry = rx;
            if (rx > 0 || ry > 0) {
              shape = new RoundRectangle2D.Double(xLoc, yLoc, width, height, rx, ry);
            } else {
              shape = new Rectangle2D.Double(xLoc, yLoc, width, height);
            }
            debugPrintln("rect: " + scX(xLoc) + ", " + scY(yLoc) + ", " + scX(width) + ", " + scY(height) + ", " + scX(rx) + ", " + scY(ry));
            break;
          case "ellipse":
            xLoc = parseCoord(attributes.getValue("cx"));
            yLoc = parseCoord(attributes.getValue("cy"));
            rx = parseCoord(attributes.getValue("rx"));
            ry = parseCoord(attributes.getValue("ry"));
            if (rx > 0 && ry == 0) ry = rx;
            shape = new Ellipse2D.Double(xLoc - rx, yLoc - ry, 2 * rx, 2 * ry);
            debugPrintln("ellipse: " + scX(xLoc) + ", " + scY(yLoc) + ", " + scX(rx) + ", " + scY(ry));
            break;
          case "circle":
            xLoc = parseCoord(attributes.getValue("cx"));
            yLoc = parseCoord(attributes.getValue("cy"));
            double radius = parseCoord(attributes.getValue("r"));
            shape = new Ellipse2D.Double(xLoc - radius, yLoc - radius, 2 * radius, 2 * radius);
            debugPrintln("circle: " + scX(xLoc) + ", " + scY(yLoc) + ", " + scX(radius));
            break;
          case "line":
            xLoc = parseCoord(attributes.getValue("x1"));
            yLoc = parseCoord(attributes.getValue("y1"));
            double xLoc2 = parseCoord(attributes.getValue("x2"));
            double yLoc2 = parseCoord(attributes.getValue("y2"));
            shape = new Line2D.Double(xLoc, yLoc, xLoc2, yLoc2);
            debugPrintln("line: " + scX(xLoc) + ", " + scY(yLoc) + ", " + scX(xLoc2) + ", " + scY(yLoc2));
            break;
          case "polyline":
            pointData = attributes.getValue("points");
            points = getPoints(pointData);
            Path2D.Double polyline = new Path2D.Double();
            polyline.moveTo(points[0].x, points[0].y);
            for (int ii = 1; ii < points.length; ii++) {
              polyline.lineTo(points[ii].x, points[ii].y);
            }
            shape = polyline;
            debugPrintln("polyline: " + pointData);
            break;
          case "polygon":
            pointData = attributes.getValue("points");
            points = getPoints(pointData);
            Path2D.Double polygon = new Path2D.Double();
            polygon.moveTo(points[0].x, points[0].y);
            for (int ii = 1; ii < points.length; ii++) {
              polygon.lineTo(points[ii].x, points[ii].y);
            }
            polygon.closePath();
            shape = polygon;
            debugPrintln("polygon: " + pointData);
            break;
          case "text":
            xLoc = parseCoord(attributes.getValue("x"));
            yLoc = parseCoord(attributes.getValue("y"));
            String fontFamily = attributes.getValue("font-family");
            String fontSize = attributes.getValue("font-size");
            // Note: capture text in characters() call
            debugPrintln("text: " + scX(xLoc) + ", " + scY(yLoc) + ", " + fontFamily + ", " + fontSize);
            break;
          default:
            debugPrintln(qName + ": unknown element");
            break;
        }
        if (shape != null) {
          if (gTrans != null) {
            shapes.add(gTrans.createTransformedShape(shape));
          } else {
            String trans = attributes.getValue("transform");
            if (trans != null) {
              shapes.add(getSVGTransform(trans, atScale).createTransformedShape(shape));
            } else {
              shapes.add(atScale.createTransformedShape(shape));
            }
          }
        }
      }

      public void endElement (String uri, String localName, String qName) throws SAXException {
        switch (qName.toLowerCase()) {
          case "marker":
            inMarker = false;
            break;
          case "g":
            gTrans = null;
            break;
        }
        debugPrintln("End Element: " + qName);
      }

      public void characters (char ch[], int start, int length) throws SAXException {
        debugPrintln("Chars: " + new String(ch, start, length));
      }
    };
    saxParser.parse(file, handler);
    return shapes.toArray(new Shape[shapes.size()]);
  }

  private double getInches (String data) {
    if (data != null && data.length() > 0) {
      data = data.toLowerCase();
      if (data.endsWith("pt")) {
        return Double.parseDouble(data.substring(0, data.length() - 2)) / 72;     // 72pt = 1 inch
      } else if (data.endsWith("px")) {
        if (pxIs72) {
          return Double.parseDouble(data.substring(0, data.length() - 2)) / 72;   // 72px = 1 inch (Abobe uses 72px/inch!)
        } else {
          return Double.parseDouble(data.substring(0, data.length() - 2)) / 96;   // 96px = 1 inch (Abobe uses 72px/inch!)
        }
      } else if (data.endsWith("pc")) {
        return Double.parseDouble(data.substring(0, data.length() - 2)) / 6;      // 6pc = 1 inch
      } else if (data.endsWith("cm")) {
        return Double.parseDouble(data.substring(0, data.length() - 2)) / 2.52;   // 2.54cm = 1 inch
      } else if (data.endsWith("mm")) {
        return Double.parseDouble(data.substring(0, data.length() - 2)) / 25.4;   // 25.4mm = 1 inch
      } else if (data.endsWith("in")) {
        return Double.parseDouble(data.substring(0, data.length() - 2));          // inches
      } else {
        return Double.parseDouble(data) / 96;                                     // 96px = 1 inch
      }
    }
    return 0;
  }

  // In development
  private AffineTransform getSVGTransform (String trans, AffineTransform inTrans) {
    AffineTransform at = new AffineTransform(inTrans);
    Pattern iPat = Pattern.compile("((\\w+)\\(((\\-?\\d+\\.?\\d*e?\\-?\\d*,?)+)\\))+");
    Matcher mat = iPat.matcher(trans.toLowerCase());
    while (mat.find()) {
      String func = mat.group(2);
      String[] tmp = mat.group(3).split(",");
      double[] parms = new double[tmp.length];
      for (int ii = 0; ii < tmp.length; ii++) {
        parms[ii] = Double.parseDouble(tmp[ii]);
      }
      switch (func) {
        case "translate":   // 1 or 2 parameters
          if (parms.length == 1) {
            at.translate(parms[0], 0);
          } else if (parms.length == 2) {
            at.translate(parms[0], parms[1]);
          }
          break;
        case "scale":       // 1 or 2 parameters
          if (parms.length == 1) {
            at.scale(parms[0], parms[0]);
          } else if (parms.length == 2) {
            at.scale(parms[0], parms[1]);
          }
          break;
        case "rotate":      // 1 or 3 parameters
          if (parms.length == 1) {
            at.rotate(parms[0]);
          } else if (parms.length == 3) {
            at.rotate(parms[0], parms[1], parms[2]);
          }
          break;
        case "skewX":       // 1 parameter
          at.shear(parms[0], at.getShearY());
          break;
        case "skewY":       // 1 parameter
          at.shear(at.getShearX(), parms[0]);
          break;
        case "matrix":      // 6 parameters
          if (parms.length == 6) {
            at.setTransform(parms[0] * inTrans.getScaleX(), parms[1], parms[2], parms[3] * inTrans.getScaleY(), parms[4], parms[5]);
          }
          break;
      }
    }
    return at;
  }

  /**
   * Scan all Shapes objects to determine x/y offset, if any, then remove offset.
   * @param shapes Array of Shape objects
   * @return Array of transformed Shape objects
   */
  public static Shape[] removeOffset (Shape[] shapes) {
    Rectangle2D bounds = null;
    for (Shape shape : shapes) {
      bounds = bounds == null ? BetterBoundingBox.getBounds(shape) : bounds.createUnion(BetterBoundingBox.getBounds(shape));
    }
    if (bounds.getMinX() != 0 || bounds.getMinY() != 0) {
      AffineTransform atScale = AffineTransform.getTranslateInstance(-bounds.getMinX(), -bounds.getMinY());
      for (int ii = 0; ii < shapes.length; ii++) {
        shapes[ii] = atScale.createTransformedShape(shapes[ii]);
      }
    }
    return shapes;
  }

  /**
   * Create a new Shape (Path2D.Double) object that combines all the elements of the Array of Shapes
   * @param shapes Array of Shape objects
   * @return New Shape object that contains all the features of the Array of Shapes
   */
  public static Shape combinePaths (Shape[] shapes) {
    Path2D.Double newShape = new Path2D.Double();
    AffineTransform atScale = AffineTransform.getTranslateInstance(0, 0);
    for (Shape shape : shapes) {
      newShape.append(shape.getPathIterator(atScale), false);
    }
    return newShape;
  }

  /**
   * Compute a reflected Bezier control point using the last end point and prior control point
   * @param end Bezier End Point
   * @param ctrl Last Bezier control point
   * @return reflected control point
   */
  private Point2D.Double reflectControlPoint (Point2D.Double end, Point2D.Double ctrl) {
    return new Point2D.Double(end.x + (end.x - ctrl.x), end.y + (end.y - ctrl.y));
  }

  private double[] parseRawCoords (String data) {
    String[] tmp = parsePathData(data);
    double[] points = new double[tmp.length];
    for (int ii = 0; ii < tmp.length; ii++) {
      points[ii] = parseCoord(tmp[ii]);
    }
    return points;
  }

  private Point2D.Double[] getPoints (String data) {
    String[] tmp = parsePathData(data);
    ArrayList<Point2D.Double> points = new ArrayList<>();
    for (int ii = 0; ii < tmp.length; ii += 2) {
      points.add(new Point2D.Double(parseCoord(tmp[ii]), parseCoord(tmp[ii + 1])));
    }
    return points.toArray(new Point2D.Double[points.size()]);
  }

  private double parseCoord (String val) {
    if (val != null && val.length() > 0) {
      return Double.parseDouble(val);
    }
    return 0;
  }

  private String scX (double val) {
    return df.format(val * scaleX);
  }

  private String scY (double val) {
    return df.format(val * scaleY);
  }

  private String scp (Point2D.Double pnt) {
    return df.format(pnt.x * scaleX) + ", " + df.format(pnt.y * scaleY);
  }

  private String[] parsePathData (String data) {
    Pattern iPat = Pattern.compile("([a-zA-Z]|-?[0-9.]+)");
    ArrayList<String> tmp = new ArrayList<>();
    Matcher mat = iPat.matcher(data);
    while (mat.find()) {
      tmp.add(mat.group());
    }
    return tmp.toArray(new String[tmp.size()]);
  }

  private void debugPrintln (String txt) {
    if (debug) {
      System.out.println(txt);
    }
  }

  public static void main (String[] args) throws Exception {
    SVGParser parser = new SVGParser(true);
    parser.enableDebug(true);
    Shape[] shapes = parser.parseSVG(new File("Test/SVG Files/ArtSupplies.svg"));
    shapes = removeOffset(shapes);
    shapes = new Shape[]{combinePaths(shapes)};
    new ShapeWindow(shapes, .25);
  }
}
