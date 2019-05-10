import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.Date;

/**
 * This code implements hust enough of the EPS format to write java.awt.Shape pbjects using PathIterator
 *
 * Note: EPS Units are 72 DPI (inches/72)
 */

public class EPSWriter {
  private StringBuilder   doc = new StringBuilder();
  private boolean         closed;

  EPSWriter (String title, int width, int height) {
    append("%!PS-Adobe-3.0 EPSF-3.0");
    append("%%Creator: LaserCut");
    append("%%Title: " + title + "");
    append("%%CreationDate: " + new Date());
    append("%%BoundingBox: 0 0 " + width + ", " + height);
    append("%%DocumentData: Clean7Bit");
    append("%%DocumentProcessColors: Black");
    append("%%ColorUsage: Color");
    append("%%Origin: 0 0");
    append("%%Pages: 1");
    append("%%Page: 1 1");
    append("%%EndComments\n");
    append("gsave");
    append("0.0 " + height + " translate");
    append("1 -1 scale");
    append("0.0 0.0 0.0 setrgbcolor");
    append("0.001 setlinewidth");
    append("10.0 setmiterlimit");
    append("0 setlinejoin");
    append("2 setlinecap");
    append("[ ] 0 setdash");
  }

  String getEPS () {
    if (!closed) {
      append("grestore");
      append("showpage");
      append("\n%%EOF");
      closed = true;
    }
    return doc.toString();
  }

  void writeEPS (File file) throws FileNotFoundException {
    PrintWriter out = new PrintWriter(file);
    out.println(getEPS());
    out.close();
  }

  public void draw (Shape shape) {
    if (closed) {
      throw new IllegalStateException("EPSWriter closed");
    }
    append("newpath");
    double[] coords = new double[6];
    PathIterator it = shape.getPathIterator(null);
    double x0 = 0;
    double y0 = 0;
    while (!it.isDone()) {
      int type = it.currentSegment(coords);
      switch (type) {
        case PathIterator.SEG_MOVETO:
          append(coords[0] + " " + coords[1] + " moveto");
          x0 = coords[0];
          y0 = coords[1];
          break;
        case PathIterator.SEG_LINETO:
          append(coords[0] + " " + coords[1] + " lineto");
          x0 = coords[0];
          y0 = coords[1];
          break;
        case PathIterator.SEG_CLOSE:
          append("closepath");
          break;
        case PathIterator.SEG_CUBICTO:
          append(coords[0] + " " + coords[1] + " " + coords[2] + " " + coords[3] + " " + coords[4] + " " + coords[5] + " curveto");
          x0 = coords[4];
          y0 = coords[5];
          break;
        case PathIterator.SEG_QUADTO:
          // Convert quad curve into a cubic.
          double x1 = x0 + 2 / 3f * (coords[0] - x0);
          double y1 = y0 + 2 / 3f * (coords[1] - y0);
          double x2 = coords[0] + 1 / 3f * (coords[2] - coords[0]);
          double y2 = coords[1] + 1 / 3f * (coords[3] - coords[1]);
          append(x1 + " " + y1 + " " + x2 + " " + y2 + " " + coords[2] + " " + coords[3] + " curveto");
          x0 = coords[2];
          y0 = coords[3];
          break;
      }
      it.next();
    }
    append("stroke");
  }

  private void append (String line) {
    doc.append(line).append("\n");
  }

  public static void main (String[] args) throws Exception {
    EPSWriter eps = new EPSWriter("EPSWriter", 500, 500);
    Path2D.Double path = new Path2D.Double();
    path.moveTo(100, 100);
    path.lineTo(400, 100);
    path.lineTo(400, 400);
    path.lineTo(100, 400);
    path.lineTo(100, 100);
    path.closePath();
    eps.draw(path);
    eps.draw(new Rectangle2D.Double(200, 200, 100, 100));
    path.moveTo(225, 225);
    path.lineTo(225 + 50, 225);
    path.lineTo(225 + 25, 225 + 50);
    path.lineTo(225, 225);
    path.closePath();
    eps.draw(path);
    eps.writeEPS(new File("Test/EPS Files/EPSWriter.eps"));
  }
}
