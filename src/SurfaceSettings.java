import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.List;

/**
 * Serializable object used to save and restore settings from ".lzr" files
 */

public class SurfaceSettings implements Serializable {
  private static final long serialVersionUID = 1281736566222122122L;
  public final Point        viewPoint;
  public final double       zoomFactor;
  public final double       gridStep;
  public final int          gridMajor;
  public final String       version;
  public List<CADShape>     design;

  public SurfaceSettings () {
    this.viewPoint = new Point(0, 0);
    this.zoomFactor = 1;
    this.gridStep =  0.1;
    this.gridMajor = 10;
    this.version = null;
  }

  public SurfaceSettings (DrawSurface surface, JViewport viewPort) {
    this.viewPoint = viewPort.getViewPosition();
    this.zoomFactor = surface.getZoomFactor();
    this.gridStep = surface.getGridSize();
    this.gridMajor = surface.getGridMajor();
    this.version = LaserCut.VERSION;
  }

  public List<CADShape> getDesign () {
    return design;
  }

  public void setDesign (List<CADShape> design) {
    this.design = design;
  }

  public String toString () {
    return "SurfaceSettingsn:" +
    "  viewPoint:  " + viewPoint + "\n" +
    "  zoomFactor: " + zoomFactor + "\n" +
    "  gridStep:   " + gridStep + "\n" +
    "  gridMajor:  " + gridMajor + "\n" +
    "  version:    " + version + "\n";
  }
}
