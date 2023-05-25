import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

class CADGear extends CADShape implements Serializable, LaserCut.Rotatable {
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
  }

  CADGear (double xLoc, double yLoc, double module, int numTeeth, int numPoints, double pressAngle, double profileShift,
           double rotation, double holeSize) {
    setLocationAndOrientation(xLoc, yLoc, rotation);
    this.module = module;
    this.numTeeth = numTeeth;
    this.numPoints = numPoints;
    this.pressAngle = pressAngle;
    this.profileShift = profileShift;
    this.holeSize = holeSize;
    diameter = numTeeth * module;
  }

  @Override
  String getMenuName () {
    return "Gear";
  }

  @Override
  String[] getParameterNames () {
    return new String[]{
      "module{module = diameter / numTeeth}",
      "numTeeth",
      "numPoints",
      "pressAngle|deg",
      "profileShift",
      "*diameter|in{diameter = numTeeth * module}",
      "holeSize|in"};
  }

  @Override
  Shape buildShape () {
    return GearGen.generateGear(module, numTeeth, numPoints, pressAngle, profileShift, holeSize);
  }

  @Override
  void draw (Graphics g, double zoom, boolean keyRotate, boolean keyResize, boolean keyOption) {
    super.draw(g, zoom, keyRotate, keyResize, keyOption);
    Graphics2D g2 = (Graphics2D) g;
    // Draw dashed line in magenta to show effective gear diameter
    g2.setColor(Color.MAGENTA);
    g2.setStroke(Utils2D.getDashedStroke(getStrokeWidth(), 108.0f, 10.0f));
    double diam = module * numTeeth;
    double screen = zoom * LaserCut.SCREEN_PPI;
    g2.draw(new Ellipse2D.Double((xLoc - diam / 2) * screen, (yLoc - diam / 2) * screen, diam * screen, diam * screen));
  }
}
