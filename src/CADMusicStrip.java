import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Example music file
 *  6E
 *  6D, 4D, 3C
 *  4F#
 *  etc.
 *
 * Resources:
 *    musicboxmaniacs.com/
 *    www.youtube.com/watch?v=apcsggbbBFw
 */

class CADMusicStrip extends CADShape implements Serializable, LaserCut.Updatable, LaserCut.NorResizable {
  private static final long serialVersionUID = 7398125917619364676L;
  private static final Map<String, Integer> noteIndex = new HashMap<>();
  private static final double   xStep = 4.0;
  private static final double   yStep = 2.017;
  private static final double   xOff = Utils2D.mmToInches(12.0);
  private static final double   yOff = Utils2D.mmToInches(6.0);
  private static final double   holeDiam = Utils2D.mmToInches(2.0);
  private boolean               checkClicked;
  public int                    columns = 60;
  public double                 width, height;
  public boolean[][]            notes;
  private transient Shape       rect;
  private transient int         lastCol = 0;

  static {
    // Only notes marked 'true' are available on 30 note music box player
    Note[] noteList = {
      //      Oct Note  Used
      new Note(3, "C",  true),    // 3C
      new Note(3, "C#", false),
      new Note(3, "D#", false),
      new Note(3, "E",  false),
      new Note(3, "F",  false),
      new Note(3, "G",  true),    // 3G
      new Note(3, "G#", false),
      new Note(3, "A",  true),    // 3A
      new Note(3, "A#", false),
      new Note(3, "B",  true),    // 3B
      new Note(4, "C",  true),    // 4C
      new Note(4, "C#", true),    // 4C#
      new Note(4, "D",  true),    // 4D
      new Note(4, "D#", false),
      new Note(4, "E",  true),    // 3E
      new Note(4, "F",  true),    // 4F
      new Note(4, "F#", true),    // 4F#
      new Note(4, "G",  true),    // 4G
      new Note(4, "G#", true),    // 4G#
      new Note(4, "A",  true),    // 4A
      new Note(4, "A#", true),    // 4A#
      new Note(4, "B",  true),    // 4B
      new Note(5, "C",  true),    // 5C
      new Note(5, "C#", true),    // 5C#
      new Note(5, "D",  true),    // 5D
      new Note(5, "D#", true),    // 5D#
      new Note(5, "E",  true),    // 5E
      new Note(5, "F",  true),    // %F
      new Note(5, "F#", true),    // 5F#
      new Note(5, "G",  true),    // 5
      new Note(5, "G#", true),    // 5G#
      new Note(5, "A",  true),    // 5A
      new Note(5, "A#", true),    // 5A#
      new Note(5, "B",  true),    // 5B
      new Note(6, "C",  true),    // 6C
      new Note(6, "C#", false),
      new Note(6, "D",  true),    // 6D
      new Note(6, "D#", false),
      new Note(6, "E",  true),    // 6E
     };
    int idx = 0;
    for (int ii = noteList.length - 1; ii >= 0; ii--) {
      Note note = noteList[ii];
      if (note.used) {
        noteIndex.put(note.note, idx++);
      }
    }
    int dum = 0;
  }

  static class Note {
    String  note;
    boolean used;

    Note (int octave, String note, boolean used) {
      this.note = octave + note;
      this.used = used;
    }
  }

  void readMusicBoxFile (File sFile) throws Exception {
    Scanner lines = new Scanner(Files.newInputStream(sFile.toPath()));
    List<String[]> cols = new ArrayList<>();
    while (lines.hasNextLine()) {
      Scanner line = new Scanner(lines.nextLine().trim());
      List<String> notes = new ArrayList<>();
      while (line.hasNext()) {
        String item = line.next();
        System.out.println(item);
        item = item.endsWith(",") ? item.substring(0, item.length() - 1) : item;
        notes.add(item);
      }
      cols.add(notes.toArray(new String[0]));
      notes = new ArrayList<>();
    }
    String[][] song = cols.toArray(new String[cols.size()][0]);
    notes = new boolean[song.length][30];
    for (int ii = 0; ii < song.length; ii++) {
      for (String note : song[ii]) {
        if (noteIndex.containsKey(note)) {
          notes[ii][noteIndex.get(note)] = true;
        }
      }
    }
    width = Utils2D.mmToInches(song.length * 4 + 16);
    height = Utils2D.mmToInches(70);
  }

  @Override
  String getMenuName () {
    return "Music Strip";
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
    width = Utils2D.mmToInches(columns * 4 + 16);
    height = Utils2D.mmToInches(70);
  }

  @Override
  void draw (Graphics g, double zoom, boolean keyRotate, boolean keyResize, boolean keyOption) {
    Graphics2D g2 = (Graphics2D) g.create();
    Stroke thick = new BasicStroke(1.0f);
    Stroke thin = new BasicStroke(0.8f);
    double mx = (xLoc + xOff) * zoom * LaserCut.SCREEN_PPI;
    double my = (yLoc + yOff) * zoom * LaserCut.SCREEN_PPI;
    double zf = zoom / LaserCut.SCREEN_PPI;
    g2.setFont(new Font("Arial", Font.PLAIN, (int) (7 * zf)));
    for (int ii = 0; ii <= notes.length; ii++) {
      double sx = mx + Utils2D.mmToInches(ii * xStep * zoom * LaserCut.SCREEN_PPI);
      g2.setColor((ii & 1) == 0 ? Color.black : isSelected ? Color.black : Color.lightGray);
      g2.setStroke((ii & 1) == 0 ? thick : thin);
      g2.draw(new Line2D.Double(sx, my, sx, my + Utils2D.mmToInches(29 * yStep * zoom * LaserCut.SCREEN_PPI)));
      for (int jj = 0; jj < 30; jj++) {
        double sy = my + Utils2D.mmToInches(jj * yStep * zoom * LaserCut.SCREEN_PPI);
        g2.setColor(jj == 0 || jj == 29 ? Color.black : isSelected ? Color.black : Color.lightGray);
        g2.setStroke(jj == 0 || jj == 29 ? thick : thin);
        g2.draw(new Line2D.Double(mx, sy, mx + Utils2D.mmToInches(columns * xStep * zoom * LaserCut.SCREEN_PPI), sy));
        if (ii == lastCol) {
          g2.setColor(Color.red);
          //g2.drawString(symb[jj], (int) (sx - 14 * zf), (int) (sy + 2.5 * zf));
        }
      }
    }
    g2.dispose();
    super.draw(g, zoom, false, keyResize, keyOption);
  }

  // Implement Updatable interface
  public boolean updateInternalState (Point2D.Double point) {
    // See if user clicked on one of the note spots (Note: point in screen inch coords)
    double xx = Utils2D.inchesToMM(point.x - xLoc - xOff);
    double yy = Utils2D.inchesToMM(point.y - yLoc - yOff);
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
      double sx = xx + xOff + Utils2D.mmToInches(ii * xStep);
      for (int jj = 0; jj < 30; jj++) {
        double sy = yy + yOff + Utils2D.mmToInches(jj * yStep);
        if (notes[ii][jj]) {
          path.append(new Ellipse2D.Double(sx - rad, sy - rad, holeDiam, holeDiam), false);
        }
      }
    }
    return path;
  }

  @Override
  protected java.util.List<String> getEditFields () {
    return Arrays.asList(
      "columns",
      "xLoc|in",
      "yLoc|in");
  }

  @Override
  protected List<String> getPlaceFields () {
    return Arrays.asList("columns", "xLoc|in", "yLoc|in");
  }
}
