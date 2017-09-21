import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Test code to demonstrate how Bezier control points are incorrectly included in the
 * bounding box calculated by the method getBounds2D()
 */

class BetterBoundingTest extends JFrame {
  private transient Image offScr;
  private Dimension lastDim;

  BetterBoundingTest () {
    setSize(200, 260);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setVisible(true);
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
    // Draw a cubic Bezier shape using Path2D.Double
    Path2D.Double path = new Path2D.Double();
    path.moveTo(40, 140);
    path.curveTo(40, 60, 160, 60, 160, 140);
    path.curveTo(160, 220, 40, 220, 40, 140);
    path.closePath();
    g2.draw(path);
    // Draw the bounding box computed for the path by getBounds2D()
    //Rectangle2D bounds = path.getBounds2D();
    // Work around
    Rectangle2D bounds = BetterBoundingBox.getBounds(path);
    int x = (int) bounds.getMinX();
    int y = (int) bounds.getMinY();
    int wid = (int) bounds.getWidth();
    int hyt = (int) bounds.getHeight();
    g2.setColor(Color.blue);
    g2.drawRect(x, y, wid, hyt);
    // Draw the location of Bezier control points as small circles
    // Control points are: 40,60 - 160,60 - 160,220 - 40,220
    g2.setColor(Color.red);
    g2.drawOval(40-2, 60-3, 5, 5);
    g2.drawOval(160-2, 60-3, 5, 5);
    g2.drawOval(160-2, 220-3, 5, 5);
    g2.drawOval(40-2, 220-3, 5, 5);
    g.drawImage(offScr, 0, 0, this);
  }

  public static void main (String[] args) throws Exception {
    new BetterBoundingTest();
  }
}
