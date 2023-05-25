package test;

import javax.swing.*;
import java.awt.*;
import java.beans.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.filechooser.*;
import java.awt.image.*;
import javax.imageio.*;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.awt.event.*;

// /Users/wholder/IdeaProjects/LaserCut2/Test/PNG Files/Clipboard-icon.png

class ImagePreviewJFileChooser extends JFrame {
  private static final String FILE_DIR = "/Users/wholder/IdeaProjects/LaserCut2/Test/PNG Files/";
  private static final int    IMG_WID = 200;
  private static final int    IMG_HYT = 200;
  private static final int    IMG_BORDER = 30;

  public ImagePreviewJFileChooser (boolean preview) {
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif"));
    fileChooser.setCurrentDirectory(new File(FILE_DIR));
    fileChooser.setDialogTitle("Read file");
    if (preview) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setPreferredSize(new Dimension(IMG_WID + IMG_BORDER, IMG_HYT + IMG_BORDER));
      panel.setBorder(BorderFactory.createLineBorder(Color.black));
      setLayout(new FlowLayout());
      JLabel imgLabel = new JLabel();
      imgLabel.setHorizontalAlignment(JLabel.CENTER);
      imgLabel.setVerticalAlignment(JLabel.CENTER);
      panel.add(imgLabel, BorderLayout.CENTER);
      fileChooser.setAccessory(panel);
      Dimension dim = fileChooser.getPreferredSize();
      fileChooser.setPreferredSize(new Dimension((int) (dim.width * 1.25), dim.height));
      fileChooser.addPropertyChangeListener(new PropertyChangeListener() {

        public void propertyChange (PropertyChangeEvent pe) {
          if (pe.getPropertyName().equals("SelectedFileChangedProperty")) {
            SwingWorker<Image, Void> worker = new SwingWorker<Image, Void>() {

              protected Image doInBackground () {
                if (pe.getPropertyName().equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)) {
                  File file = fileChooser.getSelectedFile();
                  try {
                    BufferedImage buf = ImageIO.read(Files.newInputStream(file.toPath()));
                    return buf.getScaledInstance(IMG_WID, IMG_WID, BufferedImage.SCALE_FAST);
                  } catch (Exception e) {
                    imgLabel.setText(" Not valid image/Unable to read");
                  }
                }
                return null;
              }

              protected void done () {
                try {
                  Image img = get(1L, TimeUnit.NANOSECONDS);
                  if (img != null) {
                    imgLabel.setIcon(new ImageIcon(img));
                  }
                } catch (Exception e) {
                  imgLabel.setText(" Error");
                }
              }
            };
            worker.execute();
          }
        }
      });
    }
    JButton open = new JButton("Open File Chooser");
    JPanel outer = new JPanel(new BorderLayout());
    outer.setBorder(BorderFactory.createEmptyBorder(50, 20, 50, 20));
    outer.add(open, BorderLayout.CENTER);
    add(outer);
    open.addActionListener(ae -> {
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      fileChooser.showDialog(null, null);
    });
    pack();
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setVisible(true);
  }


  public static void main (String args[]) {
    new ImagePreviewJFileChooser(true);
  }
}
