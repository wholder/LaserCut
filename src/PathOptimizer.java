import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathOptimizer {
  static class ShapeSeg {
    int       type;
    double    sx, sy, ex, ey;
    double[]  coords;
    boolean   used;

    ShapeSeg (int type, double sx, double sy, double[] coords) {
      this.type = type;
      this.sx = sx;
      this.sy = sy;
      this.coords = coords;
      switch (type) {
        case PathIterator.SEG_LINETO:   // 1 (sx c01)
          ex = coords[0];
          ey = coords[1];
          break;
        case PathIterator.SEG_QUADTO:   // 2 (sx c01 c02)
          ex = coords[2];
          ey = coords[3];
          break;
        case PathIterator.SEG_CUBICTO:  // 3 (sx c01 c02 c03)
          ex = coords[4];
          ey = coords[5];
          break;
      }
    }

    // Reverse path order
    void flip () {
      switch (type) {
        case PathIterator.SEG_LINETO:   // 1
          coords[0] = sx;
          coords[1] = sy;
          sx = ex;
          sy = ey;
          ex = coords[0];
          ey = coords[1];
          break;
        case PathIterator.SEG_QUADTO:   // 2
          coords[2] = sx;
          coords[3] = sy;
          sx = ex;
          sy = ey;
          ex = coords[2];
          ey = coords[3];
          break;
        case PathIterator.SEG_CUBICTO:  // 3
          double tx = coords[0];
          double ty = coords[1];
          coords[0] = coords[2];
          coords[1] = coords[3];
          coords[2] = tx;
          coords[3] = ty;
          //
          coords[4] = sx;
          coords[5] = sy;
          sx = ex;
          sy = ey;
          ex = coords[4];
          ey = coords[5];
          break;
      }
    }

    Point2D.Float getStart () {
      return new Point2D.Float((float) sx, (float) sy);
    }

    Point2D.Float getEnd () {
      return new Point2D.Float((float) ex, (float) ey);
    }
  }

  static class Vert {
    List<ShapeSeg>  segs = new ArrayList<>();

    ShapeSeg getSeg (ShapeSeg from, Point2D.Float vert) {
      for (ShapeSeg seg : segs) {
        if (seg == from) {
          // Don't connect to self
          continue;
        }
        Point2D.Float start = seg.getStart();
        if (!seg.used && start.equals(vert)) {
          segs.remove(seg);
          return seg;
        }
        Point2D.Float end = seg.getEnd();
        if (!seg.used && end.equals(vert)) {
          seg.flip();
          segs.remove(seg);
          return seg;
        }
      }
      return null;
    }
  }

  /**
   * Experimental code designed to connect a set line segments into a continous path
   * Note: this code can only connect line segments when the end points have identical 'float' precision
   * x and y coordinates.
   * @param shape Shape onject containing the line segments to analyze
   * @return List of Shape objects (some may be rebuilt into continuous paths)
   */
  static List<Shape> optimizeShape (Shape shape) {
    List<ShapeSeg> segs = new ArrayList<>();
    // Break Shape into List of ShapeSeg objects
    PathIterator pi = shape.getPathIterator(new AffineTransform());
    double ex = 0, ey = 0;
    while (!pi.isDone()) {
      double[] coords = new double[6];      // p1.x, p1.y, p2.x, p2.y, p3.x, p3.y
      int type = pi.currentSegment(coords);
      switch (type) {
        case PathIterator.SEG_MOVETO:
          ex = coords[0];
          ey = coords[1];
          break;
        case PathIterator.SEG_LINETO:
          // lineTo(coords[0], coords[1]);
          segs.add(new ShapeSeg(type, ex, ey, coords));
          ex = coords[0];
          ey = coords[1];
          break;
        case PathIterator.SEG_QUADTO:
          // quadTo(coords[0], coords[1], coords[2], coords[3]);
          segs.add(new ShapeSeg(type, ex, ey, coords));
          ex = coords[2];
          ey = coords[3];
          break;
        case PathIterator.SEG_CUBICTO:
          // curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
          segs.add(new ShapeSeg(type, ex, ey, coords));
          ex = coords[4];
          ey = coords[5];
          break;
        case PathIterator.SEG_CLOSE:
          // closePath();
          break;
      }
      pi.next();
    }
    // Build Map of vertices with a list of segment (key is float precision)
    Map<Point2D.Float,Vert> verts = new HashMap<>();
    for (ShapeSeg seg : segs) {
      // Insert starting point
      Point2D.Float start = seg.getStart();
      Vert vert = verts.get(start);
      if (vert == null) {
        verts.put(start, vert = new Vert());
      }
      vert.segs.add(seg);
      // Insert ending point
      Point2D.Float end = seg.getEnd();
      vert = verts.get(end);
      if (vert == null) {
        verts.put(end, vert = new Vert());
      }
      vert.segs.add(seg);
    }
    // Scan for connected segments and organize into sequences
    List<ShapeSeg> opts = new ArrayList<>();
    for (ShapeSeg seg : segs) {
      if (!seg.used) {
        Point2D.Float end = seg.getEnd();
        Vert vert = verts.get(end);
        ShapeSeg con = vert.getSeg(seg, end);
        if (con != null) {
          opts.add(seg);
          seg.used = true;
          while (con != null && !con.used) {
            con.used = true;
            opts.add(con);
            end = con.getEnd();
            vert = verts.get(end);
            con = vert.getSeg(con, end);
          }
        } else {
          opts.add(seg);
          seg.used = true;
        }
      }
    }
    segs = opts;
    List<Shape> out = new ArrayList<>();
    // Combine list of reorganized ShapeSeg objects into a List of Shape objects
    Path2D.Double path = new Path2D.Double();
    ex = Double.MAX_VALUE; ey = Double.MAX_VALUE;
    for (ShapeSeg seg : segs) {
      if ((float) seg.sx != (float) ex || (float) seg.sy != (float) ey) {
        path = new Path2D.Double();
        out.add(path);
        path.moveTo(ex = seg.sx, ey = seg.sy);
      }
      switch (seg.type) {
        case PathIterator.SEG_LINETO:   // 1
          path.lineTo(ex = seg.coords[0], ey = seg.coords[1]);
          break;
        case PathIterator.SEG_QUADTO:   // 2
          path.quadTo(seg.coords[0], seg.coords[1], ex = seg.coords[2], ey = seg.coords[3]);
          break;
        case PathIterator.SEG_CUBICTO:  // 3
          path.curveTo(seg.coords[0], seg.coords[1], seg.coords[2], seg.coords[3], ex = seg.coords[4], ey = seg.coords[5]);
          break;
        case PathIterator.SEG_CLOSE:    // 4
          // Close and write out the current curve
          path.closePath();
          break;
      }
    }
    return out;
  }

  public static void main (String[] args) {
    // Create square with disconnected and misordered line segments
    Path2D.Double path = new Path2D.Double();
    path.moveTo(1, 1);  // 1,1 -> 2,1 top
    path.lineTo(2, 1);
    path.moveTo(1, 2);  // 1,2 -> 2,2 bot
    path.lineTo(2, 2);
    path.moveTo(2, 2);  // 2,2 -> 2,1 right
    path.lineTo(2, 1);
    path.moveTo(1, 1);  // 1,1 -> 1,2 left
    path.lineTo(1, 2);
    // Combine segments into continuous path
    List<Shape> list = optimizeShape(path);
    int dum = 0;
    }
}
