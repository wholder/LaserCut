import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Crude Excellon Drill File Parser (just enough read the DRILL.TXT from Osmond PCB Gerber files)
  // See: https://web.archive.org/web/20071030075236/http://www.excellon.com/manuals/program.htm

public class GerberZip {
  private String    excellon, outline;

  GerberZip (File zipFile) throws IOException {
    ZipFile zip = new ZipFile(zipFile);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      InputStream stream = zip.getInputStream(entry);
      byte[] data = new byte[stream.available()];
      stream.read(data);
      String tmp = new String(data);
      stream.close();
      if (entry.getName().equals("DRILL.TXT")) {
        excellon = tmp;
      } else if (entry.getName().equals("OUTLINE.GER")) {
        outline = tmp;
      }
    }
    zip.close();
  }

  static class ExcellonHole {
    double  xLoc;
    double yLoc;
    double diameter;

    ExcellonHole (double x, double y, double dia) {
      xLoc = x;
      yLoc = y;
      diameter = dia;
    }
  }

  public static void main (String[] args) throws Exception {
    GerberZip gerber = new GerberZip(new File("Test/Gerber Files/archive.zip"));
      List<ExcellonHole> holes = gerber.parseExcellon();
      for (ExcellonHole hole : holes) {
        System.out.println(hole.xLoc + "," + hole.yLoc + "," + hole.diameter);
      }
      Rectangle2D.Double bounds = getBounds(gerber.parseOutlines());
      System.out.println("PCB Size: " + bounds.getWidth() + " inches, " + bounds.getHeight() + " inches");
  }

  List<ExcellonHole> parseExcellon () {
    int holeType = 0;
    Map<Integer,Double> tools = new TreeMap<>();
    List<ExcellonHole> holes = new ArrayList<>();
    StringTokenizer tok = new StringTokenizer(excellon);
    while (tok.hasMoreElements()) {
      String line = tok.nextToken();
      if (line.startsWith("T")) {
        if (line.contains("C")) {
          // Read hold definition
          int idx = line.indexOf("C");
          String type = line.substring(1, idx);
          String val = line.substring(idx + 1);
          tools.put(Integer.parseInt(type), Double.parseDouble(val));
        } else {
          // Read beginning of hole list marker
          String type = line.substring(1);
          holeType = Integer.parseInt(type);
        }
      } else if (line.startsWith("X")  &&  line.contains("Y")) {
        // Read hole position
        int idx = line.indexOf("Y");
        String xVal = line.substring(1, idx);
        String yVal = line.substring(idx + 1);
        double xx = parseExcellonValue(xVal);
        double yy = parseExcellonValue(yVal);
        double diameter = tools.get(holeType);
        holes.add(new ExcellonHole(xx, yy, diameter));
      }
    }
    return holes;
  }

  List<List<Point2D.Double>> parseOutlines () {
    double lineWid = 0;
    Map<Integer,Double> apertures = new HashMap<>();
    StringTokenizer tok = new StringTokenizer(outline, "\n\r");
    List<List<Point2D.Double>> outlines = new ArrayList<>();
    List<Point2D.Double> points = new ArrayList<>();
    double lastX = 0, lastY = 0;
    while (tok.hasMoreElements()) {
      String line = tok.nextToken();
      if (line.startsWith("%")) {
        line = line.substring(1);
        if (line.startsWith("ADD")) {
          line = line.substring(3);
          int idx = line.indexOf("C");
          int aNum = Integer.parseInt(line.substring(0, idx));
          int idx2 = line.indexOf(",");
          int idx3 = line.indexOf("*");
          if (idx2 > 0 && idx3 > idx2) {
            double aSize = Double.parseDouble(line.substring(idx2 + 1, idx3));
            apertures.put(aNum, aSize);
          }
        }
      } else {
        String[] items = line.split("\\*");
        for (String item : items) {
          if (item.startsWith("G01")) {
            item = item.substring(3);
          }
          if (item.startsWith("D")) {
            int aNum = Integer.parseInt(item.substring(1));
            lineWid = apertures.get(aNum);
            // Hmmm... Omsond has a fixed x/y offset of 1 mil and lineWid has no effect on the outline
          }
          if (item.startsWith("X")  &&  item.contains("Y")  &&  item.contains("D")) {
            int yIdx = item.indexOf("Y");
            int dIdx = item.indexOf("D");
            double xVal = parseExcellonValue(item.substring(1, yIdx)) - lineWid;
            double yVal = parseExcellonValue(item.substring(yIdx + 1, dIdx));
            int dNum = Integer.parseInt(item.substring(dIdx + 1));
            if (dNum == 2) {
              points = new ArrayList<>();
              outlines.add(points);
            }
            points.add(new Point2D.Double(lastX = xVal, lastY = yVal));
          } else if (item.startsWith("X")  &&  item.contains("Y")) {
            int yIdx = item.indexOf("Y");
            double xVal = parseExcellonValue(item.substring(1, yIdx));
            double yVal = parseExcellonValue(item.substring(yIdx + 1));
            points.add(new Point2D.Double(lastX = xVal, lastY = yVal));
          } else if (item.startsWith("X")  &&  item.contains("D")) {
            int dIdx = item.indexOf("D");
            double xVal = parseExcellonValue(item.substring(1, dIdx));
            int dNum = Integer.parseInt(item.substring(dIdx + 1));
            if (dNum == 2) {
              points = new ArrayList<>();
              outlines.add(points);
            }
            points.add(new Point2D.Double(lastX = xVal, lastY));
          } else if (item.startsWith("X")) {
            double xVal = parseExcellonValue(item.substring(1));
            points.add(new Point2D.Double(lastX = xVal, lastY));
          } else if (item.startsWith("Y")  &&  item.contains("D")) {
            int dIdx = item.indexOf("D");
            double yVal = parseExcellonValue(item.substring(1, dIdx));
            int dNum = Integer.parseInt(item.substring(dIdx + 1));
            if (dNum == 2) {
              points = new ArrayList<>();
              outlines.add(points);
            }
            points.add(new Point2D.Double(lastX, lastY = yVal));
          } else if (item.startsWith("Y")) {
            double yVal = parseExcellonValue(item.substring(1));
            points.add(new Point2D.Double(lastX, lastY = yVal));
          }
        }
      }
    }
    return outlines;
  }

  public static Rectangle2D.Double getBounds (List<List<Point2D.Double>> outlines) {
    double xMax = 0, yMax = 0;
    double xMin = Double.MAX_VALUE, yMin = Double.MAX_VALUE;
    for (List<Point2D.Double> points : outlines) {
      for (Point2D.Double point : points) {
        xMax = Math.max(xMax, point.getX());
        xMin = Math.min(xMin, point.getX());
        yMax = Math.max(yMax, point.getY());
        yMin = Math.min(yMin, point.getY());
      }
    }
    return new Rectangle2D.Double(xMin, yMin, xMax, yMax);
  }

  private static double parseExcellonValue(String val) {
    StringBuilder valBuilder = new StringBuilder(val);
    while (valBuilder.length() < 2) {
      valBuilder.append("0");
    }
    val = valBuilder.toString();
    return Double.parseDouble(val.substring(0, 2) + "." + val.substring(2));
  }
}
