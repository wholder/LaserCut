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
    LaserCut.CADShape cadShape;
    List<PathTrie>   items = new ArrayList<>();

    PathTrie (LaserCut.CADShape shape) {
      this.cadShape = shape;
    }

    private boolean contains (LaserCut.CADShape shape) {
      return cadShape.getWorkspaceTranslatedShape().contains(shape.getShapeBounds());
    }

    boolean addShape (LaserCut.CADShape shape) {
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

    Point2D.Double unravel (List<LaserCut.CADShape> list, Point2D.Double startPos) {
      startPos = reorderGroups(startPos, items);
      for (PathTrie item : items) {
        item.unravel(list, startPos);
      }
      list.add(cadShape);
      return startPos;
    }
  }

  static class ShapeArea implements Comparable<ShapeArea> {
    LaserCut.CADShape cadShape;
    double  area;

    ShapeArea (LaserCut.CADShape cadShape) {
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
        LaserCut.CADShape cadShape = item.cadShape;
        Point2D.Double pnt = cadShape.getStartCoords();
        double dist = startPos.distance(pnt);
        if (dist < minDist) {
          minDist = dist;
          closest = item;
        }
      }
      newItems.add(closest);
      oldItems.remove(closest);
      startPos = closest.cadShape.getStartCoords();
    }
    inList.clear();
    inList.addAll(newItems);
    return startPos;
  }

  static List<LaserCut.CADShape> optimize (List<LaserCut.CADShape> shapes) {
    // Sort CADShape objects into descending order by the area of each's bounding box
    List<ShapeArea>  byArea = new ArrayList<>();
    for (LaserCut.CADShape shape : shapes) {
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
    List<LaserCut.CADShape> output = new ArrayList<>();
    Point2D.Double startPos = new Point2D.Double(0, 0);
    startPos = reorderGroups(startPos, groups);
    for (PathTrie group : groups) {
      startPos = group.unravel(output, startPos);
    }
    return output;
  }

  private static Map<LaserCut.CADShape,String> map = new HashMap<>();

  private static LaserCut.CADShape add (String name, LaserCut.CADShape val) {
    map.put(val, name);
    return val;
  }

  public static void main (String[] args) {
    // A nests in E, B nests in F and D, D, E & F nest in G
    List<LaserCut.CADShape> shapes = new ArrayList<>();
    if (false) {
      shapes.add(add("A", new LaserCut.CADRectangle(9, 5, 1, 1, 0, 0, false)));
      shapes.add(add("B", new LaserCut.CADRectangle(7, 3, 1, 1, 0, 0, false)));
      shapes.add(add("C", new LaserCut.CADRectangle(5, 2, 1, 1, 0, 0, false)));
      shapes.add(add("D", new LaserCut.CADRectangle(3, 2, 1, 1, 0, 0, false)));
      shapes.add(add("E", new LaserCut.CADRectangle(1, 1, 1, 1, 0, 0, false)));
      shapes.add(add("F", new LaserCut.CADRectangle(2, 4, 1, 1, 0, 0, false)));
      shapes.add(add("G", new LaserCut.CADRectangle(4, 5, 1, 1, 0, 0, false)));
    } else {
      shapes.add(add("A", new LaserCut.CADRectangle(15, 15, 5, 5, 0, 0, false)));
      shapes.add(add("B", new LaserCut.CADRectangle(35, 15, 5, 5, 0, 0, false)));
      shapes.add(add("C", new LaserCut.CADRectangle(50, 10, 5, 5, 0, 0, false)));
      shapes.add(add("D", new LaserCut.CADRectangle(50, 20, 5, 5, 0, 0, false)));
      shapes.add(add("E", new LaserCut.CADRectangle(10, 10, 15, 15, 0, 0, false)));
      shapes.add(add("F", new LaserCut.CADRectangle(30, 10, 15, 15, 0, 0, false)));
      shapes.add(add("G", new LaserCut.CADRectangle(5, 5, 55, 25, 0, 0, false)));
    }
    List<LaserCut.CADShape>  oShapes = optimize(shapes);
    for (LaserCut.CADShape shape : oShapes) {
      Rectangle2D bnds = shape.getShapeBounds();
      System.out.println(map.get(shape) + ": " + bnds.getX() + ", " + bnds.getY() + " (" + bnds.getWidth() + ", " + bnds.getHeight() + ")");
    }
  }
}
