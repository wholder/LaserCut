
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Scanner;

// See: http://printobjects.me/catalogue/ujava-gear-generator-involute-and-fillet_520801/
// Also: http://khkgears.net/gear-knowledge/gear-technical-reference/calculation-gear-dimensions/
// And: http://fab.cba.mit.edu/classes/863.09/people/cranor/How_to_Make_(Almost)_Anything/David_Cranor/Entries/2009/10/12_Entry_1_files/module.pdf

public class GearGen {
  private static final double  PPI = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
  private static boolean       DEBUG;

  // Derived from GearGen.java in LibLasercut

  /**
   * Test code for GearGen
   */
  @SuppressWarnings("ConstantIfStatement")
  public static void main (String[] args) {
    DEBUG = true;
    ShapeWindow sWin = new ShapeWindow();
    if (true) {
      Shape gear = generateGear(.45, 30, 10, 20, .25, LaserCut.mmToInches(3));
      sWin.addShape(gear);
    } else {
      Scanner sc = new Scanner(System.in);
      System.out.print("Module (mm diameter/teeth, commonly 0.5, 0.8, 1.00, 1.25, 1.50, 2.50, or 3 ): ");
      double module = sc.nextDouble();
      System.out.print("Pressure Angle in degrees (commonly 14.5, 20, or 25): ");
      double pressAngle = sc.nextDouble();
      System.out.print("Number of Teeth: ");
      int numTeeth = sc.nextInt();
      double minShift = Math.max(-0.5, (30.0 - numTeeth) / 40.0);
      double maxShift = 0.6;
      System.out.print("Profile Shift (recommended [" + minShift + ", " + maxShift + "]): ");
      // Note: profileShift needed for gears with small number of teeth
      // See: http://khkgears.net/gear-knowledge/abcs-gears-b/gear-profile-shift/
      double profileShift = sc.nextDouble();
      System.out.print("Number of Output Points for Involute Curve and Fillet Segments: ");
      int numPoints = sc.nextInt();
      Shape gear = generateGear(module, numTeeth, numPoints, pressAngle, profileShift, 3);
      sWin.addShape(gear);
    }
  }

  /**
   * Generate Involute Spur Gear based on provided parameters
   * @param module module = reference diameter / number of teeth (gears must have same module to mesh)
   * @param numTeeth total number of teeth in gear
   * @param numPoints number of points used when drawing tooth profile
   * @param pressAngle pressure angle (typically 20 degrees)
   * @param profileShift adjusts center distance
   * @param holeSize size of hole in center of gear
   * @return generated gear Shape
   */
  public static Shape generateGear (double module, int numTeeth, int numPoints, double pressAngle, double profileShift, double holeSize) {
    pressAngle = Math.PI / 180.0 * pressAngle;
    double pitchDiameter = module * numTeeth;                       // Pitch Diameter = Module * Teeth
    double baseDiameter = pitchDiameter * Math.cos(pressAngle);     // Base Circle Diameter = Pitch Diameter × Cosine(Pressure Angle)
    double dedendum = 1.157 * module;                               // Dedendum = 1.157 × Module
    double workDepth = 2 * module;                                  // Working Depth = 2 × Module
    double wholeDepth = 2.157 * module;                             // Whole Depth = 2.157 × Module
    double outerDiameter = module * (numTeeth + 2);                 // Outside Diameter = Module × (Teeth + 2)
    double tipRadius = 0.25 * module;                               // Tip Radius = 0.25 x Module
    double U = -(Math.PI / 4.0 + (1.0 - 0.25) * Math.tan(pressAngle) + 0.25 / Math.cos(pressAngle));
    double V = 0.25 - 1.0;
    ArrayList<Point2D.Double> points = new ArrayList<>();
    // Generate Involute Points
    double thetaMin = 2.0 / numTeeth * (U + (V + profileShift) * 1.0 / Math.tan(pressAngle));
    double thetaMax = 1.0 / (numTeeth * Math.cos(pressAngle)) * Math.sqrt(Math.pow((2 + numTeeth + 2 * profileShift), 2) -
        Math.pow(numTeeth * Math.cos(pressAngle), 2)) - (1 + 2 * profileShift / numTeeth) * Math.tan(pressAngle) -
        Math.PI / (2.0 * numTeeth);
    double thetaInc = (thetaMax - thetaMin) / numPoints;
    double lastY = 0;
    for (int ii = numPoints - 1; ii > 0; ii--) {
      double theta = thetaMin + thetaInc * ii;
      double xx = numTeeth * module / 2.0 * (Math.sin(theta) - ((theta + Math.PI / (2.0 * numTeeth)) * Math.cos(pressAngle) +
          2 * profileShift / numTeeth * Math.sin(pressAngle)) * Math.cos(theta + pressAngle));
      double yy = numTeeth * module / 2.0 * (Math.cos(theta) + ((theta + Math.PI / (2.0 * numTeeth)) * Math.cos(pressAngle) +
          2 * profileShift / numTeeth * Math.sin(pressAngle)) * Math.sin(theta + pressAngle));
      points.add(new Point2D.Double(xx, yy));
      lastY = yy;
    }
    // Generate Fillet Points
    thetaMin = 2.0 / numTeeth * (U + (V + profileShift) / Math.tan(pressAngle));
    thetaMax = 2.0 * U / numTeeth;
    thetaInc = (thetaMax - thetaMin) / numPoints;
    for (int ii = numPoints; ii < 2 * numPoints; ii++) {
      double theta = thetaMin + thetaInc * (ii - numPoints);
      double L = Math.sqrt(1 + 4 * Math.pow((V + profileShift) / (2 * U - numTeeth * theta), 2));
      double Q = 2 * 0.25 / L * (V + profileShift) / (2 * U - numTeeth * theta) + V + numTeeth / 2.0 + profileShift;
      double P = 0.25 / L + (U - numTeeth * theta / 2.0);
      double xx = module *  (P * Math.cos(theta) + Q * Math.sin(theta));
      double yy = module * (-P * Math.sin(theta) + Q * Math.cos(theta));
      if (yy < lastY) {
        // Prevents backtracking line when numTeeth < 8
        points.add(new Point2D.Double(xx, yy));
      }
    }
    Point2D.Double[] pointArray = points.toArray(new Point2D.Double[0]);
    // Generate tooth Shape from left/right outlines
    Path2D.Double tooth = new Path2D.Double();
    tooth.moveTo(pointArray[pointArray.length - 1].x, pointArray[pointArray.length - 1].y);
    // Draw left section
    for (int ii = pointArray.length - 2; ii >= 0; ii--) {
      tooth.lineTo(pointArray[ii].x, pointArray[ii].y);
    }
    // Mirror left section around Y axis to draw right section and fill gap at tip of tooth
    for (Point2D.Double point : pointArray) {
      tooth.lineTo(-point.x, point.y);
    }
    // Rotate and place teeth around pitch diameter to generate gear
    Path2D.Double gear = new Path2D.Double();
    AffineTransform rot = new AffineTransform();
    boolean connect = false;
    for (int angle = 0; angle < numTeeth; angle++) {
      // Use 'connect' to control adding line from prior tooth to newly-placed tooth
      gear.append(tooth.getPathIterator(rot), connect);
      connect = true;
      rot.rotate(Math.toRadians(360.0 - 360.0 / numTeeth));
    }
    gear.closePath();
    Area area = new Area(gear);
    if (holeSize > 0) {
      Ellipse2D.Double hole = new Ellipse2D.Double(-holeSize / 2, -holeSize / 2, holeSize, holeSize);
      gear.append(hole.getPathIterator(new AffineTransform()), false);
    }
    if (DEBUG) {
      System.out.println("module:                   " + module);
      System.out.println("pitch diameter:           " + pitchDiameter);
      System.out.println("circular pitch:           " + pitchDiameter * Math.PI / numTeeth);
      System.out.println("extended pitch diameter:  " + (pitchDiameter + 2 * profileShift));
      System.out.println("profile shift:            " + profileShift);
      System.out.println("base diameter:            " + baseDiameter);
      System.out.println("addendum:                 " + module);
      System.out.println("dedendum:                 " + dedendum);
      System.out.println("working depth:            " + workDepth);
      System.out.println("whole depth:              " + wholeDepth);
      System.out.println("outer diameter:           " + outerDiameter);
      System.out.println("tip radius:               " + tipRadius);
    }
    return gear;
  }

  static class ShapeWindow extends JFrame {
    private transient Image   offScr;
    private Dimension         lastDim;
    private final ArrayList<Shape>  shapes = new ArrayList<>();

    ShapeWindow () {
      setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      setSize(2000, 2000);
      setVisible(true);
    }

    void addShape (Shape shape) {
      shapes.add(shape);
      repaint();
    }

    public void paint (Graphics g) {
      Dimension d = getSize();
      if (offScr == null || (lastDim != null && (d.width != lastDim.width || d.height != lastDim.height)))
        offScr = createImage(d.width, d.height);
      lastDim = d;
      double cx = d.getWidth() / 2;
      double cy = d.getHeight() / 2;
      Graphics2D g2 = (Graphics2D) offScr.getGraphics();
      g2.setBackground(getBackground());
      g2.clearRect(0, 0, d.width, d.height);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setStroke(new BasicStroke(0.5f));
      g2.setColor(Color.black);
      // Translate Shape to position and scale up to true size on screen using mm units
      AffineTransform at = AffineTransform.getTranslateInstance(cx, cy);
      at.scale(PPI, PPI);
      for (Shape shape :shapes) {
        shape = at.createTransformedShape(shape);
        g2.draw(shape);
      }
      g.drawImage(offScr, 0, 0, this);
    }
  }
}












