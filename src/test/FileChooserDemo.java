package test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.SwingUtilities;

public class FileChooserDemo extends JPanel {
  JButton             openButton;
  JFileChooser        fileChooser;

  static class MyPanel extends JPanel {
    ImageIcon icon;

    MyPanel () {
      //setLayout(new BorderLayout());
      setPreferredSize(new Dimension(200, 200));
      setBorder(BorderFactory.createLineBorder(Color.BLACK));
    }

    void setIcon (String fName) {
      icon = new ImageIcon(fName);
      repaint();
    }

    @Override
    public void paint (Graphics gg) {
      Graphics2D g2 = (Graphics2D) gg;
      Dimension dim = getSize();
      g2.setColor(Color.white);
      g2.fillRect(0, 0, dim.width, dim.height);
      icon.paintIcon(this, g2, (dim.width - icon.getIconWidth()) / 2, (dim.height - icon.getIconHeight()) / 2);

    }
  }

  public FileChooserDemo () {
    super(new BorderLayout());
    fileChooser = new JFileChooser();
    openButton = new JButton("Open a File...");
    openButton.addActionListener(ev -> {
      if (ev.getSource() == openButton) {
        int returnVal = fileChooser.showOpenDialog(FileChooserDemo.this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fileChooser.getSelectedFile();
          System.out.println(file.getAbsolutePath());
        }
      }
    });
    addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange (PropertyChangeEvent evt) {
        System.out.println(evt.getPropertyName());
      }
    });
    JPanel buttonPanel = new JPanel(); //use FlowLayout
    buttonPanel.add(openButton);
    add(buttonPanel, BorderLayout.PAGE_START);
    // Widen JChooser by 50%
    Dimension dim = fileChooser.getPreferredSize();
    fileChooser.setPreferredSize(new Dimension((int) (dim.width * 1.5), dim.height));
    MyPanel panel = new MyPanel();
    panel.setIcon("/Users/wholder/IdeaProjects/LaserCut2/Test/PNG Files/Clipboard-icon.png");
    fileChooser.setAccessory(panel);
  }

  public static void main (String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run () {
        JFrame frame = new JFrame("FileChooserDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new FileChooserDemo());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
      }
    });
  }
}
