import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * PathPlanner: This class tries to organize the cutting order of CADShape objects so that interior
 * details of a CADShape object, such as other, nested CADShape objects are cut before the path of
 * the outer CADShape.  The algorithm workd by first sorting shape into descending order by the area
 * of the shape's bounding box.  Then, it it recursively tries to organize shapes into nested groups
 * by checking if a the bounding box of a potentially nested shape fits inside the outline of the
 * enclosing shape.  As it works it builds a list of Tries where each Trie represents a unique area
 * of the workspace that is not nested in any other shape and the contents of the Trie contain the
 * shapes nested inside the outermost shape.
 *
 * todo: add code to minimize travel time when cutting shapes in the same level in a Trie
 * See:
 *
 * Ref: https://en.wikipedia.org/wiki/Trie
 */

public class PathPlanner {

  static class PathTrie {
    CADShape cadShape;
    List<PathTrie>   items = new ArrayList<>();

    PathTrie (CADShape shape) {
      this.cadShape = shape;
    }

    private boolean contains (CADShape shape) {
      return cadShape.getWorkspaceTranslatedShape().contains(shape.getShapeBounds());
    }

    boolean addShape (CADShape shape) {
      if (contains(shape)) {
        for (PathTrie item : items) {
          if (item.contains(shape)) {
            if (item.addShape(shape)) {
              return true;
            }
          }
        }
        items.add(new PathTrie(shape));
        return true;
      }
      return false;
    }

    Point2D.Double unravel (List<CADShape> list, Point2D.Double startPos) {
      startPos = reorderGroups(startPos, items);
      for (PathTrie item : items) {
        item.unravel(list, startPos);
      }
      list.add(cadShape);
      return startPos;
    }
  }

  static class ShapeArea implements Comparable<ShapeArea> {
    CADShape cadShape;
    double  area;

    ShapeArea (CADShape cadShape) {
      this.cadShape = cadShape;
      Rectangle2D bnds = cadShape.getShape().getBounds2D();
      area = bnds.getWidth() * bnds.getHeight();
    }

    public int compareTo (ShapeArea obj) {
      return Double.compare(this.area, ((ShapeArea) obj).area);
    }
  }

  private static Point2D.Double reorderGroups (Point2D.Double startPos, List<PathTrie> inList) {
    List<PathTrie> oldItems = new ArrayList<>(inList);
    List<PathTrie>  newItems = new ArrayList<>();
    while (oldItems.size() > 0) {
      PathTrie closest = null;
      double minDist = Double.MAX_VALUE;
      for (PathTrie item : oldItems) {
        CADShape cadShape = item.cadShape;
        Point2D.Double pnt = cadShape.getStartCoords();
        double dist = startPos.distance(pnt);
        if (dist < minDist) {
          minDist = dist;
          closest = item;
        }
      }
      newItems.add(closest);
      oldItems.remove(closest);
      assert closest != null;
      startPos = closest.cadShape.getStartCoords();
    }
    inList.clear();
    inList.addAll(newItems);
    return startPos;
  }

  static List<CADShape> optimize (List<CADShape> shapes) {
    // Sort CADShape objects into descending order by the area of each's bounding box
    List<ShapeArea>  byArea = new ArrayList<>();
    for (CADShape shape : shapes) {
      byArea.add(new ShapeArea(shape));
    }
    Collections.sort(byArea);
    Collections.reverse((byArea));
    // Organize CADShape objects into hierarchical groups
    List<PathTrie> groups = new ArrayList<>();
    for (ShapeArea item : byArea) {
      boolean added = false;
      for (PathTrie group : groups) {
        if (added = group.addShape(item.cadShape)) {
          break;
        }
      }
      if (!added) {
        groups.add(new PathTrie(item.cadShape));
      }
    }
    List<CADShape> output = new ArrayList<>();
    Point2D.Double startPos = new Point2D.Double(0, 0);
    startPos = reorderGroups(startPos, groups);
    for (PathTrie group : groups) {
      startPos = group.unravel(output, startPos);
    }
    return output;
  }

  private static final Map<CADShape,String> map = new HashMap<>();

  private static CADShape add (String name, CADShape val) {
    map.put(val, name);
    return val;
  }

  public static void main (String[] args) {
    // A nests in E, B nests in F and D, D, E & F nest in G
    List<CADShape> shapes = new ArrayList<>();
    if (false) {
      shapes.add(add("A", new CADRectangle(9, 5, 1, 1, 0, 0)));
      shapes.add(add("B", new CADRectangle(7, 3, 1, 1, 0, 0)));
      shapes.add(add("C", new CADRectangle(5, 2, 1, 1, 0, 0)));
      shapes.add(add("D", new CADRectangle(3, 2, 1, 1, 0, 0)));
      shapes.add(add("E", new CADRectangle(1, 1, 1, 1, 0, 0)));
      shapes.add(add("F", new CADRectangle(2, 4, 1, 1, 0, 0)));
      shapes.add(add("G", new CADRectangle(4, 5, 1, 1, 0, 0)));
    } else {
      shapes.add(add("A", new CADRectangle(15, 15, 5, 5, 0, 0)));
      shapes.add(add("B", new CADRectangle(35, 15, 5, 5, 0, 0)));
      shapes.add(add("C", new CADRectangle(50, 10, 5, 5, 0, 0)));
      shapes.add(add("D", new CADRectangle(50, 20, 5, 5, 0, 0)));
      shapes.add(add("E", new CADRectangle(10, 10, 15, 15, 0, 0)));
      shapes.add(add("F", new CADRectangle(30, 10, 15, 15, 0, 0)));
      shapes.add(add("G", new CADRectangle(5, 5, 55, 25, 0, 0)));
    }
    List<CADShape>  oShapes = optimize(shapes);
    for (CADShape shape : oShapes) {
      Rectangle2D bnds = shape.getShapeBounds();
      System.out.println(map.get(shape) + ": " + bnds.getX() + ", " + bnds.getY() + " (" + bnds.getWidth() + ", " + bnds.getHeight() + ")");
    }
  }
}
