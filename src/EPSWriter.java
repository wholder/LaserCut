import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This code implements hust enough of the EPS format to write java.awt.Shape pbjects using PathIterator
 *
 * Note: Space in a PDF file, also known as user space, is measured in PDF units. The PDF specification
 *       defines PDF units as 72 PDF units to 1 inch.  Coordinate origin is the lower left corner (X values
 *       increase to the right and Y values increase upwards.
 */

public class EPSWriter {
  private final StringBuilder   doc = new StringBuilder();
  private boolean         closed;
  private Rectangle2D     bounds;
  private final String          title;

  EPSWriter (String title) {
    this.title = title;
  }

  String getEPS () {
    StringBuilder buf = new StringBuilder();
    append(buf, "%!PS-Adobe-3.0 EPSF-3.0");
    append(buf, "%%Creator: LaserCut");
    append(buf, "%%Title: " + title);
    append(buf, "%%CreationDate: " + new Date());
    if (bounds != null) {
      double pad = 10;
      double xOff = bounds.getMinX();
      double yOff = bounds.getMinY();
      double width = bounds.getWidth() + pad * 2;
      double height = bounds.getHeight() + pad * 2;
      append(buf, "%%BoundingBox: 0 0 " + (int) width + " " + (int) height);
      append(buf, "%%HiResBoundingBox: 0.0 0.0 " + width + " " + height);
      append(buf, "%%DocumentData: Clean7Bit");
      append(buf, "%%DocumentProcessColors: Black");
      append(buf, "%%ColorUsage: Color");
      append(buf, "%%Origin: 0 0");
      append(buf, "%%Pages: 1");
      append(buf, "%%Page: 1 1");
      append(buf, "%%EndComments\n");
      append(buf, "gsave");
      //append(buf, xOff + " " + (yOff - height) + " translate");
      append(buf, (-xOff + pad) + " " + (height + yOff - pad) + " translate");
      append(buf, "1 -1 scale");
      append(buf, "0.0 0.0 0.0 setrgbcolor");
      append(buf, "1 setlinewidth");
      append(buf, "1 setmiterlimit");
      append(buf, "0 setlinejoin");                // 0 = Miter join, 1 = Round join, 2 = Bevel join
      append(buf, "2 setlinecap");
      append(buf, "[ ] 0 setdash");
      buf.append(doc);
    }
    if (!closed) {
      append(buf, "grestore");
      append(buf, "showpage");
      append(buf, "\n%%EOF");
      closed = true;
    }
    return buf.toString();
  }

  static void writeEpsFile (File sFile, List<CADShape> list) throws Exception {
    AffineTransform scale = AffineTransform.getScaleInstance(72.0, 72.0);
    EPSWriter eps = new EPSWriter("LaserCut: " + sFile.getName());
    for (CADShape item : list) {
      if (item instanceof CADReference)
        continue;
      Shape shape = item.getWorkspaceTranslatedShape();
      shape = scale.createTransformedShape(shape);
      eps.draw(shape);
    }
    eps.writeEPS(sFile);
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
    bounds = bounds != null ? bounds.createUnion(shape.getBounds2D()) : shape.getBounds2D();
    append("newpath");
    double[] coords = new double[6];
    PathIterator it = shape.getPathIterator(null);
    double x0 = 0;
    double y0 = 0;
    while (!it.isDone()) {
      int type = it.currentSegment(coords);
      switch (type) {
        case PathIterator.SEG_MOVETO:   // 0
          append(coords[0] + " " + coords[1] + " moveto");
          x0 = coords[0];
          y0 = coords[1];
          break;
        case PathIterator.SEG_LINETO:   // 1
          append(coords[0] + " " + coords[1] + " lineto");
          x0 = coords[0];
          y0 = coords[1];
          break;
        case PathIterator.SEG_QUADTO:   // 2
          // Convert quad curve into a cubic.
          double x1 = x0 + 2.0 / 3.0 * (coords[0] - x0);
          double y1 = y0 + 2.0 / 3.0 * (coords[1] - y0);
          double x2 = coords[0] + 1.0 / 3.0 * (coords[2] - coords[0]);
          double y2 = coords[1] + 1.0 / 3.0 * (coords[3] - coords[1]);
          append(x1 + " " + y1 + " " + x2 + " " + y2 + " " + coords[2] + " " + coords[3] + " curveto");
          x0 = coords[2];
          y0 = coords[3];
          break;
        case PathIterator.SEG_CUBICTO:  // 3
          append(coords[0] + " " + coords[1] + " " + coords[2] + " " + coords[3] + " " + coords[4] + " " + coords[5] + " curveto");
          x0 = coords[4];
          y0 = coords[5];
          break;
        case PathIterator.SEG_CLOSE:    // 4
          append("closepath");
          break;
      }
      it.next();
    }
    append("stroke");
  }

  private void append (String line) {
    doc.append(line).append("\n");
  }

  private void append (StringBuilder buf, String line) {
    buf.append(line).append("\n");
  }

  public static void main (String[] args) throws Exception {
    EPSWriter eps = new EPSWriter("EPSWriter");
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
