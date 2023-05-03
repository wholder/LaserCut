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
      old.shapesInGroup.remove(shape);
      shape.setGroup(null);
      if (old.shapesInGroup.size() == 1) {
        CADShape newSelected = old.shapesInGroup.get(0);
        newSelected.setGroup(null);
        old.shapesInGroup.clear();
      }
    }
    if (!shapesInGroup.contains(shape)) {
      shapesInGroup.add(shape);
      shape.setGroup(this);
    }
  }

  void removeAllFromGroup () {
    for (CADShape shape : shapesInGroup) {
      shape.setGroup(null);
    }
    shapesInGroup.clear();
  }

  boolean containsShape (CADShape shape) {
    return shapesInGroup.contains(shape);
  }

  ArrayList<CADShape> getGroupList () {
    return shapesInGroup;
  }
}
