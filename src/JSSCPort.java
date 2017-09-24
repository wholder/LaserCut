/*
 * Created by wholder on 10/30/15.
 *
 * Encapsulates JSSC functionality into an easy to use class
 * See: https://code.google.com/p/java-simple-serial-connector/
 * And: https://github.com/scream3r/java-simple-serial-connector/releases
 */

// Note: does not seem to receive characters when using a serial adapter based on a Prolific chip (or clone)

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import jssc.*;

import javax.swing.*;

public class JSSCPort implements SerialPortEventListener {
  private static final Map<String,Integer> baudRates = new LinkedHashMap<>();
  private ArrayBlockingQueue<Integer>  queue = new ArrayBlockingQueue<>(1000);
  private Pattern             macPat = Pattern.compile("cu.");
  private Preferences         prefs;
  private String              portName;
  private int                 baudRate, dataBits = 8, stopBits = 1, parity = 0;
  private int                 eventMasks = SerialPort.MASK_RXCHAR;                // Also, SerialPort.MASK_CTS, SerialPort.MASK_DSR
  private int                 flowCtrl = SerialPort.FLOWCONTROL_NONE;
  private SerialPort          serialPort;
  private final ArrayList<RXEvent>  rxHandlers = new ArrayList<>();

  interface RXEvent {
    void rxChar (byte cc);
  }

  static {
    baudRates.put("110",    SerialPort.BAUDRATE_110);
    baudRates.put("300",    SerialPort.BAUDRATE_300);
    baudRates.put("600",    SerialPort.BAUDRATE_600);
    baudRates.put("1200",   SerialPort.BAUDRATE_1200);
    baudRates.put("2400",   2400);    // Note: constant missing in JSSC
    baudRates.put("4800",   SerialPort.BAUDRATE_4800);
    baudRates.put("9600",   SerialPort.BAUDRATE_9600);
    baudRates.put("14400",  SerialPort.BAUDRATE_14400);
    baudRates.put("19200",  SerialPort.BAUDRATE_19200);
    baudRates.put("38400",  SerialPort.BAUDRATE_38400);
    baudRates.put("57600",  SerialPort.BAUDRATE_57600);
    baudRates.put("115200", SerialPort.BAUDRATE_115200);
    baudRates.put("128000", SerialPort.BAUDRATE_128000);
    baudRates.put("256000", SerialPort.BAUDRATE_256000);
    //baudRates.put("460800", 460800);
    //baudRates.put("921600", 921600);
  }

  public JSSCPort (Preferences prefs) throws SerialPortException {
    this.prefs = prefs;
    // Determine OS Type
    switch (SerialNativeInterface.getOsType()) {
      case SerialNativeInterface.OS_LINUX:
        macPat = Pattern.compile("(ttyS|ttyUSB|ttyACM|ttyAMA|rfcomm)[0-9]{1,3}");
        break;
      case SerialNativeInterface.OS_MAC_OS_X:
        break;
      case SerialNativeInterface.OS_WINDOWS:
        macPat = Pattern.compile("");
        break;
      default:
        macPat = Pattern.compile("tty.*");
        break;
    }
    portName = prefs.get("serial.port", null);
    baudRate = prefs.getInt("serial.baud", 9600);
    for (String name : SerialPortList.getPortNames(macPat)) {
      if (name.equals(portName)) {
        serialPort = new SerialPort(portName);
        serialPort.openPort();
        serialPort.setParams(baudRate, dataBits, stopBits, parity, false, false);  // baud, 8 bits, 1 stop bit, no parity
        serialPort.setEventsMask(eventMasks);
        serialPort.setFlowControlMode(flowCtrl);
        serialPort.addEventListener(this);
      }
    }
  }

  public void close () {
    if (serialPort != null) {
      try {
        serialPort.closePort();
      } catch (SerialPortException ex) {
        ex.printStackTrace();
      }
    }
  }

  public void serialEvent (SerialPortEvent se) {
    try {
      if (se.getEventType() == SerialPortEvent.RXCHAR) {
        int rxCount = se.getEventValue();
        byte[] inChars = serialPort.readBytes(rxCount);
        if (rxHandlers.size() > 0) {
          for (byte cc : inChars) {
            for (RXEvent handler : new ArrayList<>(rxHandlers)) {
              handler.rxChar(cc);
            }
          }
        } else {
          for (byte cc : inChars) {
            if (queue.remainingCapacity() > 0) {
              queue.add((int) cc);
            }
          }
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace(System.out);
    }
  }

  public void setRXHandler (RXEvent handler) {
    synchronized (rxHandlers) {
      rxHandlers.add(handler);
    }
  }

  public void removeRXHandler (RXEvent handler) {
    synchronized (rxHandlers) {
      rxHandlers.remove(handler);
    }
  }

  public void sendByte (byte data) throws SerialPortException {
    serialPort.writeByte(data);
  }

  public void sendBytes (byte[] data) throws SerialPortException {
    serialPort.writeBytes(data);
  }

  public void sendString (String data) throws SerialPortException {
    serialPort.writeString(data);
  }

  public byte getChar () {
    int val = 0;
    if (rxHandlers.size() > 0) {
      throw new IllegalStateException("Can't call when RXEvent is defined");
    } else {
      try {
        val = queue.take();
      } catch (InterruptedException ex) {
        ex.printStackTrace(System.out);
      }
    }
    return (byte) val;
  }

  public JMenu getPortMenu () {
    JMenu menu = new JMenu("Port");
    ButtonGroup group = new ButtonGroup();
    for (String pName : SerialPortList.getPortNames(macPat)) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(pName, pName.equals(portName));
      menu.setVisible(true);
      menu.add(item);
      group.add(item);
      item.addActionListener((ev) -> {
        portName = ev.getActionCommand();
        try {
          if (serialPort != null && serialPort.isOpened()) {
            serialPort.removeEventListener();
            serialPort.closePort();
          }
          serialPort = new SerialPort(portName);
          serialPort.openPort();
          serialPort.setParams(baudRate, dataBits, stopBits, parity, false, false);  // baud, 8 bits, 1 stop bit, no parity
          serialPort.setEventsMask(eventMasks);
          serialPort.setFlowControlMode(flowCtrl);
          serialPort.addEventListener(JSSCPort.this);
          prefs.put("serial.port", portName);
        } catch (Exception ex) {
          ex.printStackTrace(System.out);
        }
      });
    }
    return menu;
  }

  public JMenu getBaudMenu () {
    JMenu menu = new JMenu("Baud Rate");
    ButtonGroup group = new ButtonGroup();
    for (String bRate : baudRates.keySet()) {
      int rate = baudRates.get(bRate);
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(bRate, baudRate == rate);
      menu.add(item);
      menu.setVisible(true);
      group.add(item);
      item.addActionListener((ev) -> {
        String cmd = ev.getActionCommand();
        prefs.putInt("serial.baud", baudRate = Integer.parseInt(cmd));
        if (serialPort != null && serialPort.isOpened()) {
          try {
            serialPort.setParams(baudRate, dataBits, stopBits, parity, false, false);  // baud, 8 bits, 1 stop bit, no parity
          } catch (Exception ex) {
            ex.printStackTrace(System.out);
          }
        }
      });
    }
    return menu;
  }
}
