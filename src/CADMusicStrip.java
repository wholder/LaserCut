import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CADMusicStrip extends CADShape implements Serializable, LaserCut.Updatable {
  private static final long serialVersionUID = 7398125917619364676L;
  private static final String[] symb = {"6E", "6D", "6C", "5B", "5A#", "5A", "5G#", "5G", "5F#", "5F", "5E", "5D#", "5D", "5C#", "5C",
                                        "4B", "4A#", "4A", "4G#", "4G", "4F#", "4F", "4E", "4D", "4C", "3B", "3A", "3G", "3D", "3C"};
  private static Map<String, Integer> noteIndex = new HashMap<>();
  private static double               xStep = 4.0;
  private static double               yStep = 2.017;
  private static double               xOff = LaserCut.mmToInches(12);
  private static double               yOff = LaserCut.mmToInches(6);
  private static double               holeDiam = LaserCut.mmToInches(2.4);
  private boolean                     checkClicked;
  public int                          columns = 60;
  public double                       width, height;
  public boolean[][]                  notes;
  private transient Shape             rect;
  private transient int               lastCol = 0;

  static {
    for (int ii = 0; ii < symb.length; ii++) {
      noteIndex.put(symb[ii], ii);
    }
  }

  CADMusicStrip () {
  }

  @Override
  String getName () {
    return "Music Strip";
  }

  void setNotes (String[][] song) {
    notes = new boolean[song.length][30];
    for (int ii = 0; ii < song.length; ii++) {
      for (String note : song[ii]) {
        if (noteIndex.containsKey(note)) {
          notes[ii][noteIndex.get(note)] = true;
        }
      }
    }
    width = LaserCut.mmToInches(song.length * 4 + 16);
    height = LaserCut.mmToInches(70);
  }

  @Override
  void updateStateAfterParameterEdit () {
    if (notes == null) {
      notes = new boolean[columns][30];
    } else {
      // Resize array and copy notes from old array
      boolean[][] nNotes = new boolean[columns][30];
      for (int ii = 0; ii < Math.min(notes.length, nNotes.length); ii++) {
        System.arraycopy(notes[ii], 0, nNotes[ii], 0, notes[ii].length);
      }
      notes = nNotes;
    }
    width = LaserCut.mmToInches(columns * 4 + 16);
    height = LaserCut.mmToInches(70);
  }

  @Override
  void draw (Graphics g, double zoom) {
    Graphics2D g2 = (Graphics2D) g.create();
    Stroke thick = new BasicStroke(1.0f);
    Stroke thin = new BasicStroke(0.8f);
    double mx = (xLoc + xOff) * zoom * LaserCut.SCREEN_PPI;
    double my = (yLoc + yOff) * zoom * LaserCut.SCREEN_PPI;
    double zf = zoom / LaserCut.SCREEN_PPI;
    g2.setFont(new Font("Arial", Font.PLAIN, (int) (7 * zf)));
    for (int ii = 0; ii <= notes.length; ii++) {
      double sx = mx + LaserCut.mmToInches(ii * xStep * zoom * LaserCut.SCREEN_PPI);
      g2.setColor((ii & 1) == 0 ? Color.black : isSelected ? Color.black : Color.lightGray);
      g2.setStroke((ii & 1) == 0 ? thick : thin);
      g2.draw(new Line2D.Double(sx, my, sx, my + LaserCut.mmToInches(29 * yStep * zoom * LaserCut.SCREEN_PPI)));
      for (int jj = 0; jj < 30; jj++) {
        double sy = my + LaserCut.mmToInches(jj * yStep * zoom * LaserCut.SCREEN_PPI);
        g2.setColor(jj == 0 || jj == 29 ? Color.black : isSelected ? Color.black : Color.lightGray);
        g2.setStroke(jj == 0 || jj == 29 ? thick : thin);
        g2.draw(new Line2D.Double(mx, sy, mx + LaserCut.mmToInches(columns * xStep * zoom * LaserCut.SCREEN_PPI), sy));
        if (ii == lastCol) {
          g2.setColor(Color.red);
          g2.drawString(symb[jj], (int) (sx - 14 * zf), (int) (sy + 2.5 * zf));
        }
      }
    }
    g2.dispose();
    super.draw(g, zoom);
  }

  // Implement Updatable interface
  public boolean updateInternalState (Point2D.Double point) {
    // See if user clicked on one of the note spots (Note: point in screen inch coords)
    double xx = LaserCut.inchesToMM(point.x - xLoc - xOff);
    double yy = LaserCut.inchesToMM(point.y - yLoc - yOff);
    double gridX = Math.floor((xx / xStep) + 0.5);
    double gridY = Math.floor((yy / yStep) + 0.5);
    double dX = xx - gridX * xStep;
    double dY = yy - gridY * yStep;
    double dist = Math.sqrt(dX * dX + dY * dY);
    //System.out.println(df.format(gridX) + ", " + df.format(gridY) + " - " +  df.format(dist));
    if (dist <= 1.5 && gridX >= 0 && gridX < notes.length && gridY >= 0 && gridY < 30) {
      // Used has clicked in a note circle
      notes[(int) gridX][(int) gridY] ^= true;
      lastCol = (int) gridX;
      updateShape();
      return true;
    }
    return gridX >= 0 && gridX < notes.length && gridY >= 0 && gridY < 30;
  }

  @Override
  Shape getShape () {
    if (checkClicked && rect != null) {
      return rect;
    }
    return super.getShape();
  }

  @Override
  boolean isShapeClicked (Point2D.Double point, double zoomFactor) {
    checkClicked = true;
    boolean clicked = super.isShapeClicked(point, zoomFactor);
    checkClicked = false;
    return clicked;
  }

  @Override
  Shape buildShape () {
    Path2D.Double path = new Path2D.Double();
    double xx = -width / 2;
    double yy = -height / 2;
    // Draw enclosing box with notched corner to indicate orientation of strip
    path.moveTo(xx, yy);
    path.lineTo(xx + width, yy);
    path.lineTo(xx + width, yy + height);
    path.lineTo(xx + .2, yy + height);
    path.lineTo(xx, yy - .4 + height);
    path.lineTo(xx, yy);
    // Draw the holes that need to be cut for active notes
    double rad = holeDiam / 2;
    for (int ii = 0; ii < notes.length; ii++) {
      double sx = xx + xOff + LaserCut.mmToInches(ii * xStep);
      for (int jj = 0; jj < 30; jj++) {
        double sy = yy + yOff + LaserCut.mmToInches(jj * yStep);
        if (notes[ii][jj]) {
          path.append(new Ellipse2D.Double(sx - rad, sy - rad, holeDiam, holeDiam), false);
        }
      }
    }
    return path;
  }

  @Override
  String[] getParameterNames () {
    return new String[0];
  }

  @Override
  protected java.util.List<String> getEditFields () {
    return Arrays.asList("columns", "xLoc|in", "yLoc|in");
  }

  @Override
  protected List<String> getPlaceFields () {
    return Arrays.asList("columns", "xLoc|in", "yLoc|in");
  }
}
