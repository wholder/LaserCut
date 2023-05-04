import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

public class Utils2D {
  /**
   * Generate a Circle Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Circle shape
   * @return Shape object
   */
  static Shape getCircle (Point2D.Double pnt, double radius) {
    return new Ellipse2D.Double(pnt.x - radius, pnt.y - radius, radius * 2, radius * 2);
  }

  /**
   * Generate a Plus sign Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Plus sign shape
   * @return Shape object
   */
  static Shape getPlus (Point2D.Double pnt, double size) {
    Path2D.Double plus = new Path2D.Double();
    plus.moveTo(pnt.x, pnt.y - size);
    plus.lineTo(pnt.x, pnt.y + size);
    plus.moveTo(pnt.x - size, pnt.y);
    plus.lineTo(pnt.x + size, pnt.y);
    return plus;
  }

  /**
   * Generate a Diamond Shape centered on Point of defined size
   * @param pnt Point to center shape on
   * @param size size of Diamond shape
   * @return Shape object
   */
  static Shape getDiamond (Point2D.Double pnt, double size) {
    Path2D.Double diamone = new Path2D.Double();
    diamone.moveTo(pnt.x, pnt.y - size);          // upper
    diamone.lineTo(pnt.x + size, pnt.y);          // right
    diamone.lineTo(pnt.x, pnt.y + size);          // lower
    diamone.lineTo(pnt.x - size, pnt.y);          // left
    diamone.lineTo(pnt.x, pnt.y - size);          // upper
    return diamone;
  }

  /**
   *  CAP_BUTT -   Ends unclosed subpaths and dash segments with no added decoration.
   *  CAP_ROUND -  Ends unclosed subpaths and dash segments with a round decoration that has a radius equal
   *               to half of the width of the pen.
   *  CAP_SQUARE - Ends unclosed subpaths and dash segments with a square projection that extends beyond
   *               the end of the segment to a distance equal to half of the line width
   *  JOIN_BEVEL - Joins path segments by connecting the outer corners of their wide outlines with a
   *               straight segment.
   *  JOIN_MITER - Joins path segments by extending their outside edges until they meet.
   * @param strokeWidth Width of stroke
   * @param dashOn Length of drawn portion of dashed line
   * @param dashOff Length of transparent portion of dashed line
   * @return BasicStroke object
   */
  static BasicStroke getDashedStroke (float strokeWidth, float dashOn, float dashOff) {
    // Alternate between these two values for length (first is drawn, second is transparent)
    final float[] dash1 = {dashOn, dashOff};
    // BasicStroke(float width, int cap, int join, float miterlimit, float[] dash, float dash_phase)
    //  width - the width of this BasicStroke (must be >= 0.0f)
    //  cap - the decoration of the ends of a BasicStroke (must be CAP_BUTT, CAP_ROUND or CAP_SQUARE)
    //  join - the decoration applied where path segments meet (must be JOIN_ROUND, JOIN_BEVEL, or JOIN_MITER)
    //  miterlimit - the limit to trim the miter join (must be >= 1.0f)
    //  dash - the array representing the dashing pattern
    //  dash_phase - the offset to start the dashing pattern
    return new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, dash1, 1.0f);
  }
}
