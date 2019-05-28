import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    void unravel (List<LaserCut.CADShape> list) {
      for (PathTrie item : items) {
        item.unravel(list);
      }
      list.add(cadShape);
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
    for (PathTrie group : groups) {
      group.unravel(output);
    }
    return output;
  }

  public static void main (String[] args) {
    // A nests in E, B nests in F and D, D, E & F nest in G
    List<LaserCut.CADShape> shapes = new ArrayList<>();
    shapes.add(new LaserCut.CADRectangle(15, 15, 5, 5, 0, 0, false));   // A
    shapes.add(new LaserCut.CADRectangle(35, 15, 5, 5, 0, 0, false));   // B
    shapes.add(new LaserCut.CADRectangle(50, 10, 5, 5, 0, 0, false));   // C
    shapes.add(new LaserCut.CADRectangle(50, 20, 5, 5, 0, 0, false));   // D
    shapes.add(new LaserCut.CADRectangle(10, 10, 15, 15, 0, 0, false)); // E
    shapes.add(new LaserCut.CADRectangle(30, 10, 15, 15, 0, 0, false)); // F
    shapes.add(new LaserCut.CADRectangle(5, 5, 55, 25, 0, 0, false));   // G
    List<LaserCut.CADShape>  oShapes = optimize(shapes);
    for (LaserCut.CADShape shape : oShapes) {
      Rectangle2D bnds = shape.getShapeBounds();
      System.out.println(bnds.getX() + ", " + bnds.getY() + " (" + bnds.getWidth() + ", " + bnds.getHeight() + ")");
    }
  }
}
