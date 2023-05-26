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
  JLabel                    imgLabel;
  JFileChooser              fileChooser;

  FileChooserMenu (Component comp, Preferences prefs, String type, String ext, boolean save, boolean preview) {
    super(save ? "Export to " + type + " File" : "Import " + type + " File");
    fileChooser = new JFileChooser();
    imgLabel = new JLabel();
    addActionListener(ev -> {
      fileChooser.setDialogTitle(save ? "Export " + type + " File" : "Import " + type + " File");
      fileChooser.setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter(type + " files (*." + ext + ")", ext);
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setCurrentDirectory(new File(currentPath = prefs.get("default." + ext + ".dir", "/")));
      currentPath = fileChooser.getCurrentDirectory().getPath();
      if (preview) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(IMG_WID + IMG_BORDER, IMG_HYT + IMG_BORDER));
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        imgLabel.setHorizontalAlignment(JLabel.CENTER);
        imgLabel.setVerticalAlignment(JLabel.CENTER);
        panel.add(imgLabel, BorderLayout.CENTER);
        fileChooser.setAccessory(panel);
        Dimension dim1 = fileChooser.getPreferredSize();
        fileChooser.setPreferredSize(new Dimension((int) (dim1.width * 1.25), dim1.height));
      }
      fileChooser.addPropertyChangeListener(evt -> {
        String path = fileChooser.getCurrentDirectory().getPath();
        if (!path.equals(currentPath)) {
          prefs.put("default." + ext + ".dir", currentPath = path);
        }
        if (preview) {
          if (evt.getPropertyName().equals("SelectedFileChangedProperty")) {
            SwingWorker<Image, Void> worker = new SwingWorker<Image, Void>() {

              protected Image doInBackground () {
                if (evt.getPropertyName().equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)) {
                  File file = fileChooser.getSelectedFile();
                  try {
                    BufferedImage buf = getPreview(file);
                    return buf.getScaledInstance(IMG_WID, IMG_WID, BufferedImage.SCALE_FAST);
                  } catch (Exception e) {
                    imgLabel.setText(" Invalid image/Unable to read");
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
      if (openDialog(this, save)) {
        File sFile = fileChooser.getSelectedFile();
        if (save && !sFile.exists()) {
          String fPath = sFile.getPath();
          if (!fPath.contains(".")) {
            sFile = new File(fPath + "." + ext);
          }
        }
        try {
          if (!save || (!sFile.exists() || showConfirmDialog(this, "Overwrite Existing file?", "Warning", YES_NO_OPTION, PLAIN_MESSAGE) == OK_OPTION)) {
            processFile(sFile);
          }
        } catch (Exception ex) {
          showMessageDialog(this, save ? "Unable to save file" : "Unable to open file", "Error", PLAIN_MESSAGE);
          ex.printStackTrace();
        }
        prefs.put("default." + ext, sFile.getAbsolutePath());
      }
    });
  }

  private boolean openDialog (Component comp, boolean save) {
    if (save) {
      return fileChooser.showSaveDialog(comp) == JFileChooser.APPROVE_OPTION;
    } else {
      return fileChooser.showOpenDialog(comp) == JFileChooser.APPROVE_OPTION;
    }
  }

  // Override, as needed
  void processFile (File sFile) throws Exception {
  }

  // Override, as needed
  BufferedImage getPreview (File file) throws IOException {
    return ImageIO.read(Files.newInputStream(file.toPath()));
  }
}
