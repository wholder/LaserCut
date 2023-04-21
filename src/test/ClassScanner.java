package test;

import java.io.*;

public class ClassScanner {
  public static void main (String[] args) throws Exception {
    ClassLoader loader = ClassScanner.class.getClassLoader();
    loader = loader.getParent();
    InputStream stream = loader.getResourceAsStream("/");
    int dum = 0;
  }
}
