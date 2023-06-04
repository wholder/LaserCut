package test;

//  contains(): : x = -0.238, y = -0.129, wid = 1.248, hyt = 1.139
//   : x = -0.250, y = -0.250, wid = 0.500, hyt = 0.500

//  contains(): : x = 0.000, y = 0.020, wid = 2.020, hyt = 1.960
//    : x = -1.500, y = -0.750, wid = 3.000, hyt = 1.500


import java.awt.geom.Rectangle2D;

public class ContainsTest {
  public static void main (String[] args) {
    Rectangle2D.Double rect1 = new Rectangle2D.Double(-0.000, 0.000 , 2.000, 2.000);
    Rectangle2D.Double rect2 = new Rectangle2D.Double(-0.250, -0.250, 0.500, 0.500);
    System.out.println(rect1.contains(rect2));
  }
}
