package test;

import javax.swing.*;
import java.awt.*;

/**
 * Use JFileChooser, as it's intended for use with Swing components
 */
public class FileDialogDemo {
  public static void main (String[] args) {
    FileDialog fd = new FileDialog(new JFrame(), "Choose a file", FileDialog.LOAD);
    fd.setDirectory("C:\\");
    fd.setFile("*.xml");
    fd.setVisible(true);
    String filename = fd.getFile();
    if (filename == null)
      System.out.println("You cancelled the choice");
    else
      System.out.println("You chose " + filename);
  }
}
