import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.io.Serializable;

class CADNemaMotor extends CADShape implements Serializable {
  private static final long serialVersionUID = 2518641166287730832L;
  private static final double M2 = LaserCut.mmToInches(2);
  private static final double M2_5 = LaserCut.mmToInches(2.5);
  private static final double M3 = LaserCut.mmToInches(3);
  private static final double M4_5 = LaserCut.mmToInches(4.5);
  //                                           8      11     14     17     24
  private static final double[] ringDiameter = {0.5906, 0.865, 0.865, 0.865, 1.5};
  private static final double[] holeSpacing = {0.630, 0.91, 1.02, 1.22, 1.86};
  private static final double[] holeDiameter = {M2, M2_5, M3, M3, M4_5};
  public final String type;   // Note: value is index into tables

  /**
   * Default constructor used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADNemaMotor () {
    // Set typical initial values, which user can edit before saving
    type = "1";
    centered = true;
  }

  CADNemaMotor (double xLoc, double yLoc, String type, double rotation, boolean centered) {
    this.type = type;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  @Override
  String getName () {
    return "NEMA Motor";
  }

  @Override
  String[] getParameterNames () {
    return new String[]{"type:Nema 8|0:Nema 11|1:Nema 14|2:Nema 17|3:Nema 23|4"};
  }

  @Override
  Shape buildShape () {
    int idx = Integer.parseInt(type);
    double diameter = ringDiameter[idx];
    double off = holeSpacing[idx] / 2;
    double hd = holeDiameter[idx];
    double hr = hd / 2;
    Area a1 = new Area(new Ellipse2D.Double(-diameter / 2, -diameter / 2, diameter, diameter));
    a1.add(new Area(new Ellipse2D.Double(-off - hr, -off - hr, hd, hd)));
    a1.add(new Area(new Ellipse2D.Double(+off - hr, -off - hr, hd, hd)));
    a1.add(new Area(new Ellipse2D.Double(-off - hr, +off - hr, hd, hd)));
    a1.add(new Area(new Ellipse2D.Double(+off - hr, +off - hr, hd, hd)));
    return a1;
  }
}
