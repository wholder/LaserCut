import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.List;

class PDFTools {
  static void writePDF (List<LaserCut.CADShape> design,  Dimension workSize, File file) throws Exception {
    FileOutputStream output = new FileOutputStream(file);
    double scale = 72;
    PDDocument doc = new PDDocument();
    PDDocumentInformation docInfo = doc.getDocumentInformation();
    docInfo.setCreator("Wayne Holder's LaserCut");
    docInfo.setProducer("Apache PDFBox " + org.apache.pdfbox.util.Version.getVersion());
    docInfo.setCreationDate(Calendar.getInstance());
    double wid = workSize.width / LaserCut.SCREEN_PPI * 72;
    double hyt = workSize.height / LaserCut.SCREEN_PPI * 72;
    PDPage pdpage = new PDPage(new PDRectangle((float) wid, (float) hyt));
    doc.addPage(pdpage);
    PDPageContentStream stream = new PDPageContentStream(doc, pdpage, PDPageContentStream.AppendMode.APPEND, false);
    // Flip Y axis so origin is at upper left
    Matrix flipY = new Matrix();
    flipY.translate(0, pdpage.getBBox().getHeight());
    flipY.scale(1, -1);
    stream.transform(flipY);
    AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
    for (LaserCut.CADShape item : design) {
      if (item instanceof LaserCut.CADReference)
        continue;
      if (item.engrave) {
        stream.setStrokingColor(Color.lightGray);
        stream.setLineWidth(1.0f);
      } else {
        stream.setStrokingColor(Color.black);
        stream.setLineWidth(0.001f);
      }
      Shape shape = item.getWorkspaceTranslatedShape();
      // Use PathIterator to generate sequence of line or curve segments
      PathIterator pi = shape.getPathIterator(at);
      while (!pi.isDone()) {
        float[] coords = new float[6];      // p1.x, p1.y, p2.x, p2.y, p3.x, p3.y
        int type = pi.currentSegment(coords);
        switch (type) {
          case PathIterator.SEG_MOVETO:   // 0
            // Move to start of a line, or bezier curve segment
            stream.moveTo(coords[0], coords[1]);
            break;
          case PathIterator.SEG_LINETO:   // 1
            // Draw line from previous point to new point
            stream.lineTo(coords[0], coords[1]);
            break;
          case PathIterator.SEG_QUADTO:   // 2
            // Write 3 point, quadratic bezier curve from previous point to new point using one control point
            stream.curveTo2(coords[0], coords[1], coords[2], coords[3]);
            break;
          case PathIterator.SEG_CUBICTO:  // 3
            // Write 4 point, cubic bezier curve from previous point to new point using two control points
            stream.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
            break;
          case PathIterator.SEG_CLOSE:    // 4
            // Close and write out the current curve
            stream.closeAndStroke();
            break;
          default:
            System.out.println("Error, Unknown PathIterator Type: " + type);
            break;
        }
        pi.next();
      }
    }
    stream.close();
    doc.save(output);
    doc.close();
    output.close();
  }
}
