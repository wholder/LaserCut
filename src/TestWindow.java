import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Displays a pop up window that lists all the CADShapeGroup and CADShape objects in the lists of
 * CADSgape objects in the DrawSurface object's "shapes" List.
 *
 *  TODO:
 *    [ ] Remember window position (need prefs as a parameter)
 *    [ ] Handle selecting and unselecting groups and shapes better
 *    [X] Allow Menu/keyCode toggle window on and off
 *    [X] Update list when list in DrawSurface changes (List listener)
 */

public class TestWindow implements DrawSurface.DrawSurfaceListener{
  Map<Integer, CADShape>        shapeLookup = new HashMap<>();
  Map<Integer, CADShapeGroup>   groupLookup = new HashMap<>();
  JEditorPane                   text = new JEditorPane();
  JDialog                       results = new JDialog();
  JScrollPane                   scroll;
  DrawSurface                   surface;
  private static final int      WIDTH = 150;

  public TestWindow (DrawSurface surface) {
    this.surface = surface;
    surface.setShaoeListListener(this);
    text.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    text.setContentType("text/html");
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
    // Add scrollbar
    scroll = new JScrollPane(text);
    scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    results.add(scroll);
    results.setResizable(false);
    results.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped (KeyEvent ev) {
        if (windowIsOpen() && ev.getKeyChar() == KeyEvent.VK_ENTER) {
          closeWindow();
        }
      }
    });
    results.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent windowEvent) {
        closeWindow();
      }
    });
    // Setup Hyperlink listener
    text.addHyperlinkListener(ev -> {
      if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String link = ev.getURL().toString();
        String[] parts = link.split(":");
        if (parts.length == 3) {
          String type = parts[1];
          String id = parts[2];
          switch (type) {
            case "shape":
              CADShape shape = shapeLookup.get(Integer.parseInt(id));
              if (shape != null) {
                //System.out.println(shape.getMenuName() + ": " + id);
                if (shape.isSelected) {
                  surface.setUnselected(shape);
                } else {
                  surface.setSelected(shape);
                }
              }
              break;
            case "group":
              CADShapeGroup group = groupLookup.get(Integer.parseInt(id));
              //System.out.println("Group: " + id);
              break;
          }
        }
      }
    });
  }

  public void listUpdated () {
    if (text != null) {
      text.setText(getList());
    }
  }

  public boolean windowIsOpen () {
    return results.isVisible();
  }

  public void closeWindow () {
    if (results.isVisible()) {
      results.setVisible(false);
    }
  }

  public void openWindow (Rectangle bnds) {
    // Pop up results window
    text.setText(getList());
    int wHyt = bnds.height - 200;
    results.setSize(new Dimension(WIDTH, wHyt));
    results.setPreferredSize(results.getSize());
    // Position text frame on right side of main window
    results.setLocation(new Point(bnds.x + bnds.width + WIDTH / 2 - WIDTH, bnds.y + bnds.height / 2 - wHyt / 2));
    results.setVisible(true);
    results.toFront();
    results.requestFocus();
    // Position scroll pane at the top
    SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
  }

  private String getList () {
    List<CADShape> shapes = surface.getDesign();
    List<CADShape> others = new ArrayList<>();
    // Build list of shape groups and ungrouped shapes
    HashMap<CADShapeGroup, List<CADShape>> groups = new LinkedHashMap<>();
    for (CADShape shape : shapes) {
      CADShapeGroup group = shape.getGroup();
      if (group != null) {
        if (groups.containsKey(group)) {
          List<CADShape> list = groups.get(group);
          list.add(shape);
        } else {
          List<CADShape> list = new ArrayList<>();
          list.add(shape);
          groups.put(group, list);
        }
      } else {
        others.add(shape);
      }
    }
    // List grouped shapes
    StringBuilder buf = new StringBuilder("<!DOCTYPE html>\n<html>\n<body>\n<pre>\n");
    shapeLookup.clear();
    groupLookup.clear();
    int groupId = 1;
    int shapeId = 1;
    buf.append("Groups:\n");
    if (groups.size() > 1) {
      for (CADShapeGroup group : groups.keySet()) {
        buf.append(" ").append(getGroupLink(groupId++));
        List<CADShape> list = groups.get(group);
        for (CADShape shape : list) {
          buf.append("  ").append(getShapeLink(shape.getMenuName(), shapeId));
          shapeLookup.put(shapeId++, shape);
        }
      }
    } else {
      buf.append(" none\n");
    }
    // List ungrouped items, if any
    buf.append("Other:\n");
    if (others.size() > 0) {
      for (CADShape shape : others) {
        buf.append(" ").append(getShapeLink(shape.getMenuName(), shapeId));
        shapeLookup.put(shapeId++, shape);
      }
    } else {
      buf.append("  none\n");
    }
    buf.append("</pre>\n</body>\n</html>");
    return buf.toString();
  }

  private static String getGroupLink (int code) {
    return "<a href=\"file:group:" + code + "\">Group: " + code + "</a>\n";
  }

  private static String getShapeLink (String test, int code) {
    return "<a href=\"file:shape:" + code + "\">" + test + "</a>\n";
  }
}
