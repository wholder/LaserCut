import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.io.*;

/**
 * ShapeWindow: Used by test code in SVGParser, GearGen and CornerFinder
 */

class ShapeWindow extends JFrame {
  static class ShapeCanvas extends Canvas {
    private transient Image       offScr;
    private Dimension             lastDim;
    private final List<Shape>     shapes;
    private final double          border;

    ShapeCanvas (List<Shape> shapes, double border) {
      this.shapes = shapes;
      this.border = border;
      Rectangle2D bounds = null;
      // Create a bounding box that's the union of all shapes in the shapes array
      for (Shape shape : shapes) {
        bounds = bounds == null ? BetterBoundingBox.getBounds(shape) : bounds.createUnion(BetterBoundingBox.getBounds(shape));
      }
      if (bounds != null) {
        int wid = (int) Math.round((bounds.getWidth() + border * 2) * LaserCut.SCREEN_PPI);
        int hyt = (int) Math.round((bounds.getHeight() + border * 2) * LaserCut.SCREEN_PPI);
        setSize(new Dimension(wid, hyt));
      }
    }

    public void paint (Graphics g) {
      Dimension d = getSize();
      if (offScr == null || (lastDim != null && (d.width != lastDim.width || d.height != lastDim.height)))
        offScr = createImage(d.width, d.height);
      lastDim = d;
      Graphics2D g2 = (Graphics2D) offScr.getGraphics();
      g2.setBackground(getBackground());
      g2.clearRect(0, 0, d.width, d.height);
      g2.setColor(Color.black);
      AffineTransform atScale = new AffineTransform();
      atScale.translate(border * LaserCut.SCREEN_PPI, border * LaserCut.SCREEN_PPI);
      atScale.scale(LaserCut.SCREEN_PPI, LaserCut.SCREEN_PPI);
      for (Shape shape : shapes) {
        g2.draw(atScale.createTransformedShape(shape));
      }
      g.drawImage(offScr, 0, 0, this);
    }
  }

  ShapeWindow (List<Shape> shapes, double border) {
    shapes = Utils2D.removeOffset(shapes);
    add(new ShapeCanvas(shapes, border), BorderLayout.CENTER);
    setLocationRelativeTo(null);
    pack();
    setVisible(true);
  }

  public static void main (String[] args) throws Exception {
    SVGParser parser = new SVGParser();
    List<Shape> shapes = parser.parseSVG(new File("Test/SVG Files/heart.svg"));
    shapes = Utils2D.removeOffset(shapes);
    new ShapeWindow(Utils2D.removeOffset(shapes), 0.125);
  }
}
