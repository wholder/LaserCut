import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;

/**
 * ShapeWindow: Used by test code in SVGParser, GearGen and CornerFinder
 */

class ShapeWindow extends JFrame {
  private static final double   SCREEN_PPI = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();

  static class ShapeCanvas extends Canvas {
    private transient Image   offScr;
    private Dimension         lastDim;
    private final Shape[]           shapes;
    private final double            border;

    ShapeCanvas (Shape[] shapes, double border) {
      this.shapes = shapes;
      this.border = border;
      Rectangle2D bounds = null;
      // Create a bounding box that's the union of all shapes in the shapes array
      for (Shape shape : shapes) {
        bounds = bounds == null ? BetterBoundingBox.getBounds(shape) : bounds.createUnion(BetterBoundingBox.getBounds(shape));
      }
      if (bounds != null) {
        int wid = (int) Math.round((bounds.getWidth() + border * 2) * SCREEN_PPI);
        int hyt = (int) Math.round((bounds.getHeight() + border * 2) * SCREEN_PPI);
        setSize(wid, hyt);
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
      atScale.translate(border * SCREEN_PPI, border * SCREEN_PPI);
      atScale.scale(SCREEN_PPI, SCREEN_PPI);
      for (Shape shape : shapes) {
        g2.draw(atScale.createTransformedShape(shape));
      }
      g.drawImage(offScr, 0, 0, this);
    }
  }

  ShapeWindow (Shape[] shapes, double border) {
    add(new ShapeCanvas(shapes, border), BorderLayout.CENTER);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    pack();
    setVisible(true);
  }

  public static void main (String[] args) throws Exception {
    SVGParser parser = new SVGParser();
    Shape[] shapes = parser.parseSVG(new File("Test/SVG Files/heart.svg"));
    new ShapeWindow(SVGParser.removeOffset(shapes), 0.125);
  }
}
