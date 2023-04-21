import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class used to organize CADShape objects into a items
 */
class CADShapeGroup implements Serializable {
  private static final long         serialVersionUID = 3210128656295452345L;
  private final ArrayList<CADShape> shapesInGroup = new ArrayList<>();

  void addToGroup (CADShape shape) {
    CADShapeGroup old = shape.getGroup();
    if (old != null) {
      old.removeFromGroup(shape);
    }
    if (!shapesInGroup.contains(shape)) {
      shapesInGroup.add(shape);
      shape.setGroup(this);
    }
  }

  private CADShape removeFromGroup (CADShape shape) {
    shapesInGroup.remove(shape);
    shape.setGroup(null);
    if (shapesInGroup.size() == 1) {
      CADShape newSelected = shapesInGroup.get(0);
      newSelected.setGroup(null);
      shapesInGroup.clear();
      return newSelected;
    }
    return shapesInGroup.get(0);
  }

  void removeAllFromGroup () {
    for (CADShape shape : shapesInGroup) {
      shape.setGroup(null);
    }
    shapesInGroup.clear();
  }

  boolean contains (CADShape shape) {
    return shapesInGroup.contains(shape);
  }

  ArrayList<CADShape> getGroupList () {
    return shapesInGroup;
  }

  ArrayList<CADShape> getShapesInGroup () {
    return shapesInGroup;
  }
}
