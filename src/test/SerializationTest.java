package test;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;

public class SerializationTest {

  public static class SurfaceSettings implements Serializable {
    private static final long serialVersionUID = 1281736566222122122L;
    public final Point        viewPoint;
    public final double       zoomFactor;
    public final double       gridStep;
    public final int          gridMajor;
    public final String       version;

    public SurfaceSettings () {
      this(new Point(0, 1), 1, 0.1, 1);
    }

    public SurfaceSettings (Point viewPoint, double zoomFactor, double gridStep, int gridMajor) {
      this.viewPoint = viewPoint;
      this.zoomFactor = zoomFactor;
      this.gridStep = gridStep;
      this.gridMajor = gridMajor;
      this.version = "dummy2";
    }

    public String toString () {
      return "viewPoint.x: " + viewPoint.x +  ", viewPoint.y: " + viewPoint.y +  ", zoomFactor: " + zoomFactor +
              ", gridStep: " + gridStep +", gridStep: " + gridMajor +", gridMajor: " + gridMajor + ", version: " + version;
    }
  }

  public static void main (String[] args) throws Exception {
    ArrayList<String> s1 = new ArrayList<>();
    s1.add("s1a");
    s1.add("s2a");
    s1.add("s3a");
    ArrayList<String> s2 = new ArrayList<>();
    s2.add("s1b");
    s2.add("s2b");
    s2.add("s3b");
    SurfaceSettings settings1 = new SurfaceSettings();
    //
    // Write serialized data
    //
    File wFile = new File("Test/SerializationTest.obj");
    FileOutputStream fileOut = new FileOutputStream(wFile);
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(s1);
    out.writeObject(s2);
    out.writeObject(settings1);
    out.close();
    fileOut.close();
    //
    // Readback serialized data
    //
    File rFile = new File("Test/SerializationTest.obj");
    FileInputStream fileIn = new FileInputStream(rFile);
    ObjectInputStream in = new ObjectInputStream(fileIn);
    ArrayList<String> r1 = (ArrayList<String>) in.readObject();
    ArrayList<String> r2 = (ArrayList<String>) in.readObject();
    SurfaceSettings settings2 = (SurfaceSettings) in.readObject();
    in.close();
    fileIn.close();
    System.out.println("List r1");
    for (String string : r1) {
      System.out.println("  " + string);
    }
    System.out.println("List r2");
    for (String string : r2) {
      System.out.println("  " + string);
    }
    System.out.println("  settings2: " + settings2);
  }
}
