import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

class CADGear extends CADShape implements Serializable {
  private static final long serialVersionUID = 2334548672295293845L;
  public double module;
  public double pressAngle;
  public double profileShift;
  public double holeSize;
  public double diameter;
  public int    numTeeth;
  public int    numPoints;

  /**
   * Default constructor is used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADGear () {
    // Set typical initial values, which user can edit before saving
    module = .1;
    numTeeth = 15;
    numPoints = 10;
    pressAngle = 20;
    profileShift = .25;
    holeSize = .125;
    diameter = numTeeth * module;
    centered = true;
  }

  CADGear (double xLoc, double yLoc, double module, int numTeeth, int numPoints, double pressAngle, double profileShift,
           double rotation, double holeSize) {
    setLocationAndOrientation(xLoc, yLoc, rotation, true);
    this.module = module;
    this.numTeeth = numTeeth;
    this.numPoints = numPoints;
    this.pressAngle = pressAngle;
    this.profileShift = profileShift;
    this.holeSize = holeSize;
    diameter = numTeeth * module;
  }

  @Override
  String getName () {
    return "Gear";
  }

  @Override
  String[] getParameterNames () {
    return new String[]{"module{module = diameter / numTeeth}", "numTeeth", "numPoints", "pressAngle|deg", "profileShift",
      "*diameter|in{diameter = numTeeth * module}", "holeSize|in"};
  }

  @Override
  Shape buildShape () {
    return GearGen.generateGear(module, numTeeth, numPoints, pressAngle, profileShift, holeSize);
  }

  @Override
  void draw (Graphics g, double zoom, boolean ksyShift) {
    super.draw(g, zoom, ksyShift);
    Graphics2D g2 = (Graphics2D) g;
    // Draw dashed line in magenta to show effective gear diameter
    g2.setColor(Color.MAGENTA);
    BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f}, 0.0f);
    g2.setStroke(dashed);
    double diam = module * numTeeth;
    double scale = zoom * LaserCut.SCREEN_PPI;
    if (centered) {
      g2.draw(new Ellipse2D.Double((xLoc - diam / 2) * scale, (yLoc - diam / 2) * scale, diam * scale, diam * scale));
    } else {
      Rectangle2D bnds = getShapeBounds();
      double cX = xLoc + bnds.getWidth() / 2;
      double cY = yLoc + bnds.getHeight() / 2;
      g2.draw(new Ellipse2D.Double((cX - diam / 2) * scale, (cY - diam / 2) * scale, diam * scale, diam * scale));
    }
  }
}
