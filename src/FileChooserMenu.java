import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;
import static javax.swing.JOptionPane.*;

public class FileChooserMenu extends JMenuItem {
  final JFileChooser fileChooser;
  String currentPath;

  FileChooserMenu (Component comp, Preferences prefs, String type, String ext, boolean save) {
    super(save ? "Export to " + type + " File" : "Import " + type + " File");
    fileChooser = new JFileChooser();
    addActionListener(ev -> {
      fileChooser.setDialogTitle(save ? "Export " + type + " File" : "Import " + type + " File");
      fileChooser.setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter(type + " files (*." + ext + ")", ext);
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setCurrentDirectory(new File(currentPath = prefs.get("default." + ext + ".dir", "/")));
      currentPath = fileChooser.getCurrentDirectory().getPath();
      fileChooser.addPropertyChangeListener(evt -> {
        String path = fileChooser.getCurrentDirectory().getPath();
        if (!path.equals(currentPath)) {
          prefs.put("default." + ext + ".dir", currentPath = path);
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
}
