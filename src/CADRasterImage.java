import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

import static javax.swing.JOptionPane.*;

class CADRasterImage extends CADShape implements Serializable, LaserCut.Resizable, LaserCut.Rotatable {
  private static final long serialVersionUID = 2309856254388651139L;
  public double             width, height, scale = 100.0;
  public boolean            engrave3D;
  public String             imagePpi;
  Dimension                 ppi;
  transient BufferedImage   img;

  CADRasterImage () {
    engrave = true;
  }

  @Override
  void createAndPlace (DrawSurface surface, LaserCut laserCut) {
    // Prompt for Image file
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select an Image File");
    fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
    FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("Image files (jpg, jpeg, png, gif, bmp)",
      "jpg", "jpeg", "png", "gif", "bmp");
    fileChooser.addChoosableFileFilter(nameFilter);
    fileChooser.setFileFilter(nameFilter);
    fileChooser.setSelectedFile(new File(laserCut.prefs.get("image.dir", "/")));
    if (fileChooser.showOpenDialog(laserCut) == JFileChooser.APPROVE_OPTION) {
      try {
        File imgFile = fileChooser.getSelectedFile();
        laserCut.prefs.put("image.dir", imgFile.getAbsolutePath());
        ppi = getImageDPI(imgFile);
        imagePpi = ppi.width + "x" + ppi.height;
        img = ImageIO.read(imgFile);
        width = (double) img.getWidth() / ppi.width;
        height = (double) img.getHeight() / ppi.height;
        boolean placed = false;
        do {
          if (placeParameterDialog(surface, laserCut.displayUnits)) {
            // Make sure image will fit in work area
            Dimension dim = surface.getWorkSize();
            if (width > dim.width / LaserCut.SCREEN_PPI || height > dim.height / LaserCut.SCREEN_PPI) {
              if (showConfirmDialog(laserCut, "Image is too large for work area\nPlace anyway?", "Caution",
                                    YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION) {
                surface.placeShape(this);
                placed = true;
              }
            } else {
              surface.placeShape(this);
              placed = true;
            }
          } else {
            surface.setInfoText("Image load cancelled");
            break;
          }
        } while (!placed);
      } catch (Exception ex) {
        laserCut.showErrorDialog("Unable to load file");
        ex.printStackTrace(System.out);
      }
    }
  }

  @Override
  protected java.util.List<String> getEditFields () {
    width = (double) img.getWidth() / ppi.width * (scale / 100);
    height = (double) img.getHeight() / ppi.height * (scale / 100);
    return Arrays.asList("xLoc|in", "yLoc|in", "*width|in", "*height|in", "*imagePpi", "rotation|deg",
      "scale|%", "centered", "engrave", "engrave3D");
  }

  @Override
  protected List<String> getPlaceFields () {
    return Arrays.asList("*width|in", "*height|in", "*imagePpi", "rotation|deg", "scale|%", "centered", "engrave", "engrave3D");
  }

  @Override
  void hookParameters (Map<String, ParameterDialog.ParmItem> pNames) {
    pNames.get("scale").addParmListener(parm -> {
      String val = ((JTextField) parm.field).getText();
      JTextField wid = (JTextField) pNames.get("width").field;
      JTextField hyt = (JTextField) pNames.get("height").field;
      try {
        double ratio = Double.parseDouble(val) / 100.0;
        double rawWid = (double) img.getWidth() / ppi.width;
        double rawHyt = (double) img.getHeight() / ppi.height;
        wid.setText(LaserCut.df.format(rawWid * ratio));
        hyt.setText(LaserCut.df.format(rawHyt * ratio));
      } catch (NumberFormatException ex) {
        wid.setText("-");
        hyt.setText("-");
      }
    });
  }

  @Override
  String getName () {
    return "Raster Image";
  }

  // Implement Resizable interface
  public void resize (double dx, double dy) {
    double newWid = centered ? dx * 2 : dx;
    double newHyt = centered ? dy * 2 : dy;
    double rawWid = (double) img.getWidth() / ppi.width;
    double rawHyt = (double) img.getHeight() / ppi.height;
    double ratioX = newWid / rawWid;
    double ratioY = newHyt / rawHyt;
    double ratio = Math.min(ratioX, ratioY);
    if (rawWid * ratio >= .2 && rawHyt * ratio >= .2) {
      width = rawWid * ratio;
      height = rawHyt * ratio;
      scale = ratio * 100;
    }
  }

  @Override
  void updateStateAfterParameterEdit () {
    double rawWid = (double) img.getWidth() / ppi.width;
    double rawHyt = (double) img.getHeight() / ppi.height;
    double ratio = scale / 100.0;
    width = rawWid * ratio;
    height = rawHyt * ratio;
  }

  @Override
  void draw (Graphics g, double zoom, boolean keyShift) {
    Graphics2D g2 = (Graphics2D) g.create();
    BufferedImage bufimg;
    if (engrave) {
      // Convert Image to greyscale
      bufimg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g2d = bufimg.createGraphics();
      g2d.drawImage(img, 0, 0, null);
      g2d.dispose();
    } else {
      bufimg = img;
    }
    // Transform image for centering, rotation and scale
    AffineTransform at = new AffineTransform();
    if (centered) {
      at.translate(xLoc * zoom * LaserCut.SCREEN_PPI, yLoc * zoom * LaserCut.SCREEN_PPI);
      at.scale(zoom * scale / 100 * LaserCut.SCREEN_PPI / ppi.width, zoom * scale / 100 * LaserCut.SCREEN_PPI / ppi.height);
      at.rotate(Math.toRadians(rotation));
      at.translate(-bufimg.getWidth() / 2.0, -bufimg.getHeight() / 2.0);
    } else {
      at.translate(xLoc * zoom * LaserCut.SCREEN_PPI, yLoc * zoom * LaserCut.SCREEN_PPI);
      at.scale(zoom * scale / 100 * LaserCut.SCREEN_PPI / ppi.width, zoom * scale / 100 * LaserCut.SCREEN_PPI / ppi.height);
      at.rotate(Math.toRadians(rotation));
    }
    // Draw with 40% Alpha to make image semi transparent
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
    g2.drawImage(bufimg, at, null);
    g2.dispose();
    super.draw(g, zoom, keyShift);
  }

  /**
   * Used to compute scale factors needed to engrave image on Zing
   *
   * @param destPpi destination ppi/dpi (usually ZING_PPI)
   * @return array of double where [0] is x scale and [1] is y scale
   */
  double[] getScale (double destPpi) {
    return new double[]{(destPpi * width) / img.getWidth(), (destPpi * height) / img.getHeight()};
  }

  /**
   * Computes the zero-centered bounding box (in inches * scale value) after the imagea is scaled and rotated
   * Note: used by ZingLaser
   *
   * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
   * @return Bounding box for scaled and rotated image
   */
  Rectangle2D getScaledRotatedBounds (double[] scale) {
    AffineTransform at = new AffineTransform();
    at.scale(scale[0], scale[1]);
    at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
    Rectangle2D.Double rect = new Rectangle2D.Double(0, 0, img.getWidth(), img.getHeight());
    Path2D.Double tShape = (Path2D.Double) at.createTransformedShape(rect);
    return tShape.getBounds2D();
  }

  /**
   * Compute the AffineTransform needed to scale and rotate a zero-centered image so that upper left corner is at 0,0
   * Note: used by ZingLaser
   *
   * @param bb    Bounding box computed by getScaledRotatedBounds()
   * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
   * @return AffineTransform which will scale and rotate image into bounding box computed by getScaledRotatedBounds()
   */
  AffineTransform getScaledRotatedTransform (Rectangle2D bb, double[] scale) {
    AffineTransform at = new AffineTransform();
    at.translate(-bb.getX(), -bb.getY());
    at.scale(scale[0], scale[1]);
    at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
    return at;
  }

  /**
   * Compute the origin point on the edge of the scaled and rotated image
   * Note: used by ZingLaser
   *
   * @param at AffineTransform used to scale and rotate
   * @param bb Bounding box computed by getScaledRotatedBounds()
   * @return Origin point on the edge of the image (offset by the negative of these amounts when drawing)
   */
  Point2D.Double getScaledRotatedOrigin (AffineTransform at, Rectangle2D bb) {
    if (centered) {
      return new Point2D.Double(bb.getWidth() / 2, bb.getHeight() / 2);
    } else {
      Point2D.Double origin = new Point2D.Double(0, 0);
      at.transform(origin, origin);
      return origin;
    }
  }

  /**
   * Generate a scaled and rotated image that fits inside the bounding box computed by getScaledRotatedBounds()
   * Note: used by ZingLaser
   *
   * @param at    AffineTransform used to scale and rotate
   * @param bb    Bounding box computed by getScaledRotatedBounds()
   * @param scale Array of double from getScale() where [0] is x scale and [1] is y scale
   * @return BufferedImage containing scaled and rotated image
   */
  BufferedImage getScaledRotatedImage (AffineTransform at, Rectangle2D bb, double[] scale) {
    // Create new BufferedImage the size of the bounding for for the scaled and rotated image
    int wid = (int) Math.round(bb.getWidth());
    int hyt = (int) Math.round(bb.getHeight());
    BufferedImage bufImg = new BufferedImage(wid, hyt, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2 = bufImg.createGraphics();
    g2.setColor(Color.white);
    g2.fillRect(0, 0, wid, hyt);
    // Draw scaled and rotated image into newly-created BufferedImage
    at = new AffineTransform();
    at.translate(-bb.getX(), -bb.getY());
    at.scale(scale[0], scale[1]);
    at.rotate(Math.toRadians(rotation), (double) img.getWidth() / 2, (double) img.getHeight() / 2);
    g2.drawImage(img, at, null);
    return bufImg;
  }

  @Override
  Shape buildShape () {
    AffineTransform at = new AffineTransform();
    return at.createTransformedShape(new Rectangle2D.Double(-width / 2, -height / 2, width, height));
  }

  // Custom write serializer for BufferedImage
  private void writeObject (ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    ImageIO.write(img, "png", out);
  }

  // Custom read serializer for BufferedImage
  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    img = ImageIO.read(in);
    width = (double) img.getWidth() / ppi.width * (scale / 100);
    height = (double) img.getHeight() / ppi.height * (scale / 100);
  }

  @Override
  Color getShapeColor () {
    return isSelected ? Color.blue : Color.lightGray;
  }

  @Override
  BasicStroke getShapeStroke (float strokeWidth) {
    final float[] dash1 = {8.0f};
    return new BasicStroke(strokeWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, dash1, 0.5f);
  }

  /**
   * Examine image metadata and try to determine image DPI.  Handle JPEG, PNG and BMP files
   *
   * @param file File comtaining image
   * @return Detected DPI, or 72x72 DPI as default
   * @throws IOException
   */
  static Dimension getImageDPI (File file) throws IOException {
    ImageInputStream iis = ImageIO.createImageInputStream(file);
    try {
      Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
      if (it.hasNext()) {
        ImageReader reader = (ImageReader) it.next();
        try {
          reader.setInput(iis);
          IIOMetadata meta = reader.getImageMetadata(0);
          String formatName = meta.getNativeMetadataFormatName();
          Element tree = (Element) meta.getAsTree(formatName);
          NodeList nodes;
          if ((nodes = tree.getElementsByTagName("app0JFIF")).getLength() > 0) {
            // Read DPI for JPEG File (if it's contained needed Metadata)
            Element jfif = (Element) nodes.item(0);
            int dpiH = Integer.parseInt(jfif.getAttribute("Xdensity"));
            int dpiV = Integer.parseInt(jfif.getAttribute("Ydensity"));
            return new Dimension(dpiH, dpiV);
          } else if ((nodes = tree.getElementsByTagName("pHYs")).getLength() > 0) {
            // Read DPI for PNG File (if it contains Metadata pixelsPerUnitXAxis and pixelsPerUnitYAxis)
            Element jfif = (Element) nodes.item(0);
            long dpiH = Math.round(Double.parseDouble(jfif.getAttribute("pixelsPerUnitXAxis")) / 39.3701);
            long dpiV = Math.round(Double.parseDouble(jfif.getAttribute("pixelsPerUnitYAxis")) / 39.3701);
            return new Dimension((int) dpiH, (int) dpiV);
          } else if (tree.getElementsByTagName("BMPVersion").getLength() > 0) {
            // Note: there must be a more efficient way to do this...
            NodeList bmp = tree.getElementsByTagName("PixelsPerMeter");
            Map<String, Double> map = new HashMap<>();
            for (int ii = 0; ii < bmp.getLength(); ii++) {
              Node item = bmp.item(ii);
              NodeList bmp2 = item.getChildNodes();
              for (int jj = 0; jj < bmp2.getLength(); jj++) {
                Node xy = bmp2.item(jj);
                map.put(xy.getNodeName().toLowerCase(), Double.parseDouble(xy.getNodeValue()));
              }
            }
            if (map.size() == 2) {
              return new Dimension((int) Math.round(map.get("x") / 39.3701), (int) Math.round(map.get("y") / 39.3701));
            }
          }
        } finally {
          reader.dispose();
        }
      }
    } finally {
      iis.close();
    }
    // Assume it's 72 DPI if there's no Metadata that specifies it
    return new Dimension(72, 72);
  }
}
