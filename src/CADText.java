import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CADText extends CADShape implements Serializable, LaserCut.Rotatable, LaserCut.Resizable {
  private static final long serialVersionUID = 4314642313295298841L;
  public String text, fontName, fontStyle;
  public int    fontSize;
  public double tracking;
  private static final Map<String, Integer> styles = new HashMap<>();
  private static final List<String> fonts = new ArrayList<>();

  static {
    // Define available font styles
    styles.put("plain", Font.PLAIN);
    styles.put("bold", Font.BOLD);
    styles.put("italic", Font.ITALIC);
    styles.put("bold-italic", Font.BOLD + Font.ITALIC);
    // Define available fonts
    String[] availFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Map<String, String> aMap = new HashMap<>();
    for (String tmp : availFonts) {
      aMap.put(tmp, tmp);
    }
    addIfAvailable(aMap, "American Typewriter");
    addIfAvailable(aMap, "Arial");
    addIfAvailable(aMap, "Arial Black");
    addIfAvailable(aMap, "Bauhaus 93");
    addIfAvailable(aMap, "Bradley Hand");
    addIfAvailable(aMap, "Brush Script");
    addIfAvailable(aMap, "Casual");
    addIfAvailable(aMap, "Chalkboard");
    addIfAvailable(aMap, "Comic Sans MS");
    addIfAvailable(aMap, "Edwardian Script ITC");
    addIfAvailable(aMap, "Freehand");
    addIfAvailable(aMap, "Giddyup Std");
    addIfAvailable(aMap, "Helvetica");
    addIfAvailable(aMap, "Hobo Std");
    addIfAvailable(aMap, "Impact");
    addIfAvailable(aMap, "Marker Felt");
    addIfAvailable(aMap, "OCR A Std");
    addIfAvailable(aMap, "Times New Roman");
    addIfAvailable(aMap, "Stencil");
    fonts.add("Vector 1");
    fonts.add("Vector 2");
    fonts.add("Vector 3");
  }

  @Override
  String getName () {
    return "Text";
  }

  private static void addIfAvailable (Map<String, String> avail, String font) {
    if (avail.containsKey(font)) {
      fonts.add(font);
    }
  }

  /**
   * Default constructor is used to instantiate subclasses in "Shapes" Menu
   */
  @SuppressWarnings("unused")
  CADText () {
    // Set typical initial values, which user can edit before saving
    text = "Test";
    fontName = "Helvetica";
    fontStyle = "plain";
    fontSize = 24;
    tracking = 0;
    engrave = true;
  }

  CADText (double xLoc, double yLoc, String text, String fontName, String fontStyle, int fontSize, double tracking,
           double rotation, boolean centered) {
    this.text = text;
    this.fontName = fontName;
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.tracking = tracking;
    setLocationAndOrientation(xLoc, yLoc, rotation, centered);
  }

  public void resize (double dx, double dy) {
    double width = centered ? dx * 2 : dx;
    int newPnts = fontSize;
    boolean changed = false;
    double wid;
    double sWid = getSWid(fontSize);
    if (sWid < width) {
      while ((wid = getSWid(++newPnts)) < width) {
        fontSize = newPnts;
        changed = true;
      }
    } else {
      while (newPnts > 8 && (wid = getSWid(--newPnts)) > width) {
        fontSize = newPnts;
        changed = true;
      }
    }
    if (changed) {
      buildShape();
    }
  }

  private double getSWid (int points) {
    BufferedImage bi = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
    Graphics gg = bi.getGraphics();
    Font fnt = new Font(fontName, styles.get(fontStyle), points);
    FontMetrics fm = gg.getFontMetrics(fnt);
    return (double) fm.stringWidth(text) / 72.0;
  }

  @Override
  String[] getParameterNames () {
    StringBuilder fontNames = new StringBuilder("fontName");
    for (String font : fonts) {
      fontNames.append(":");
      fontNames.append(font);
    }
    return new String[]{
      "text{text to display}",
      fontNames + "{font name}",
      "fontStyle:plain:bold:italic:bold-italic{font style}",
      "fontSize|pts{font size in points}",
      "tracking{controls spacing of glyphs}"};
  }

  @Override
  Shape buildShape () {
    if (fontName.startsWith("Vector")) {
      Path2D.Double path = new Path2D.Double();
      LaserCut.VectorFont font = LaserCut.VectorFont.getFont(fontName);
      int[][][] stroke = font.font;
      int lastX = 1000, lastY = 1000;
      int xOff = 0;
      for (int ii = 0; ii < text.length(); ii++) {
        char cc = text.charAt(ii);
        cc = cc >= 32 & cc <= 127 ? cc : '_';   // Substitute '_' for codes outside printable ASCII range
        int[][] glyph = stroke[cc - 32];
        int left = glyph[0][0];
        int right = glyph[0][1];
        for (int jj = 1; jj < glyph.length; jj++) {
          int x1 = glyph[jj][0] - left;
          int y1 = glyph[jj][1];
          int x2 = glyph[jj][2] - left;
          int y2 = glyph[jj][3];

          if (x1 != lastX || y1 != lastY) {
            path.moveTo(x1 + xOff, y1);
          }
          path.lineTo(x2 + xOff, lastY = y2);
          lastX = x2;
        }
        int step = right - left;
        xOff += step;
      }
      AffineTransform at = new AffineTransform();
      double scale = fontSize / (72.0 * font.height);
      at.scale(scale, scale);
      Shape text = at.createTransformedShape(path);
      Rectangle2D bounds = text.getBounds2D();
      at = new AffineTransform();
      at.translate(-bounds.getX(), -bounds.getY());
      text = at.createTransformedShape(text);
      bounds = text.getBounds2D();
      at = new AffineTransform();
      at.translate(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
      return at.createTransformedShape(text);
    } else {
      // Code from: http://www.java2s.com/Tutorial/Java/0261__2D-Graphics/GenerateShapeFromText.htm
      BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = img.createGraphics();
      Font font = new Font(fontName, styles.get(fontStyle), fontSize);
      HashMap<TextAttribute, Object> attrs = new HashMap<>();
      attrs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
      attrs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
      attrs.put(TextAttribute.TRACKING, tracking);
      font = font.deriveFont(attrs);
      g2.setFont(font);
      try {
        GlyphVector vect = font.createGlyphVector(g2.getFontRenderContext(), text);
        AffineTransform at = new AffineTransform();
        at.scale(1 / 72.0, 1 / 72.0);
        Shape text = at.createTransformedShape(vect.getOutline());
        Rectangle2D bounds = text.getBounds2D();
        at = new AffineTransform();
        at.translate(-bounds.getX(), -bounds.getY());
        text = at.createTransformedShape(text);
        bounds = text.getBounds2D();
        at = new AffineTransform();
        at.translate(-bounds.getWidth() / 2, -bounds.getHeight() / 2);
        return at.createTransformedShape(text);
      } finally {
        g2.dispose();
      }
    }
  }
}
