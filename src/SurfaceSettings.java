import java.awt.*;
import java.io.Serializable;

public class SurfaceSettings implements Serializable {
  private static final long serialVersionUID = 1281736566222122122L;
  public final Point viewPoint;
  public final double zoomFactor;
  public final double gridStep;
  public final int gridMajor;
  public final String version;

  public SurfaceSettings () {
    this(new Point(0, 0), 1, 0.1, 1);
  }

  public SurfaceSettings (Point viewPoint, double zoomFactor, double gridStep, int gridMajor) {
    this.viewPoint = viewPoint;
    this.zoomFactor = zoomFactor;
    this.gridStep = gridStep;
    this.gridMajor = gridMajor;
    this.version = LaserCut.VERSION;
  }

  public String toString () {
    return "viewPoint.x: " + viewPoint.x +  ", viewPoint.y: " + viewPoint.y +  ", zoomFactor: " + zoomFactor +
      ", gridStep: " + gridStep +", gridStep: " + gridMajor +", gridMajor: " + gridMajor + ", version: " + version;
  }
}
