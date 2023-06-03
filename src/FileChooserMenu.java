import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import static javax.swing.JOptionPane.*;

public class FileChooserMenu extends JMenuItem {
  private static final int  IMG_WID = 200;
  private static final int  IMG_HYT = 200;
  private static final int  IMG_BORDER = 30;
  String                    currentPath;
  String                    lastFile;
  JLabel                    imgLabel;
  JFileChooser              fileChooser;

  FileChooserMenu (LaserCut lcut, String msg, String ext, boolean save, boolean preview) {
    super(msg);
    Preferences prefs = lcut.getPreferences();
    fileChooser = new JFileChooser();
    imgLabel = new JLabel();
    addActionListener(ev -> {
      fileChooser = new JFileChooser();
      imgLabel = new JLabel();
      fileChooser.setDialogTitle(msg);
      fileChooser.setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter(ext.toUpperCase() + " files (*." + ext + ")", ext);
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setCurrentDirectory(new File(currentPath = prefs.get("default." + ext + ".dir", "/")));
      currentPath = fileChooser.getCurrentDirectory().getPath();
      JPanel accessories = new JPanel(new BorderLayout());
      boolean hasAccesory = false;
      if (preview) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(IMG_WID + IMG_BORDER, IMG_HYT + IMG_BORDER));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        imgLabel.setHorizontalAlignment(JLabel.CENTER);
        imgLabel.setVerticalAlignment(JLabel.CENTER);
        panel.add(imgLabel, BorderLayout.CENTER);
        Dimension dim1 = fileChooser.getPreferredSize();
        fileChooser.setPreferredSize(new Dimension((int) (dim1.width * 1.25), dim1.height));
        accessories.add(panel, BorderLayout.WEST);
        hasAccesory = true;
      }
      JComponent temp = getAccessory(prefs, save);
      if (temp != null) {
        accessories.add(temp, BorderLayout.EAST);
        hasAccesory = true;
      }
      if (hasAccesory) {
        fileChooser.setAccessory(accessories);
      }
      fileChooser.addPropertyChangeListener(evt -> {
        // Update currentPath as the user navigates directories
        String path = fileChooser.getCurrentDirectory().getPath();
        if (!path.equals(currentPath)) {
          prefs.put("default." + ext + ".dir", currentPath = path);
        }
        if (preview) {
          // Display a file preview image
          String propName = evt.getPropertyName();
          if ("SelectedFileChangedProperty".equals(propName)) {
            SwingWorker<Image, Void> worker = new SwingWorker<Image, Void>() {

              protected Image doInBackground () {
                // LOad the preview image
                if (evt.getPropertyName().equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)) {
                  File file = fileChooser.getSelectedFile();
                  String newFile = file.getAbsolutePath();
                  if (!newFile.equals(lastFile)) {
                    lastFile = newFile;
                    try {
                      BufferedImage buf = getPreview(file);
                      if (buf != null) {
                        int wid = buf.getWidth();
                        int hyt = buf.getHeight();
                        if (wid < hyt) {
                          double ratio = (double) wid / hyt;
                          return buf.getScaledInstance((int) (IMG_WID * ratio), IMG_HYT, BufferedImage.SCALE_FAST);
                        } else {
                          double ratio = (double) hyt / wid;
                          return buf.getScaledInstance(IMG_WID, (int) (IMG_HYT * ratio), BufferedImage.SCALE_FAST);
                        }
                      }
                    } catch (Exception e) {
                      imgLabel.setText(" Invalid image/Unable to read");
                    }
                  }
                }
                return null;
              }

              protected void done () {
                // display the preview image
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
      if (openDialog(lcut, save)) {
        File sFile = fileChooser.getSelectedFile();
        if (save && !sFile.exists()) {
          String fPath = sFile.getPath();
          if (!fPath.contains(".")) {
            sFile = new File(fPath + "." + ext);
          }
        }
        try {
          if (!save || (!sFile.exists() || showConfirmDialog(lcut, "Overwrite Existing file?", "Warning", YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION)) {
            processFile(sFile);
          }
        } catch (Exception ex) {
          showMessageDialog(lcut, save ? "Unable to save file" : "Unable to open file", "Error", PLAIN_MESSAGE);
          ex.printStackTrace();
        }
        prefs.put("default." + ext, sFile.getAbsolutePath());
      }
    });
  }

  // Override in subclass to get an accessory component
  JComponent getAccessory (Preferences prefs, boolean save) {
    return null;
  }

  private boolean openDialog (Component comp, boolean save) {
    // Open an open or save dialog
    if (save) {
      return fileChooser.showSaveDialog(comp) == JFileChooser.APPROVE_OPTION;
    } else {
      return fileChooser.showOpenDialog(comp) == JFileChooser.APPROVE_OPTION;
    }
  }

  // Override, as needed to create an accessory component, such as a JPanel (see DXFReader)
  void processFile (File sFile) throws Exception {
  }

  // Override, as needed to load a preview image
  BufferedImage getPreview (File file) throws IOException {
    return ImageIO.read(Files.newInputStream(file.toPath()));
  }
}
