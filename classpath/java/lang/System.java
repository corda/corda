package java.lang;

import java.io.PrintStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;

public abstract class System {
  private static final int Unknown = 0;
  private static final int JavaClassPath = 1;
  private static final int LineSeparator = 100;
  private static final int OsName = 101;
  

  static {
    loadLibrary("natives");
  }

  public static final PrintStream out = new PrintStream
    (new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), true);

  public static final PrintStream err = new PrintStream
    (new BufferedOutputStream(new FileOutputStream(FileDescriptor.err)), true);

  public static final InputStream in
    = new BufferedInputStream(new FileInputStream(FileDescriptor.in));

  public static native void arraycopy(Object src, int srcOffset, Object dst,
                                      int dstOffset, int length);

  public static String getProperty(String name) {
    int code = Unknown;
    if (name.equals("java.class.path")) {
      code = JavaClassPath;
    } else if (name.equals("line.separator")) {
      code = LineSeparator;
    } else if (name.equals("os.name")) {
      code = OsName;
    }

    if (code == Unknown) {
      return null;
    } else if (code == JavaClassPath) {
      return getVMProperty(code);
    } else {
      return getProperty(code);
    }
  }

  private static native String getProperty(int code);

  private static native String getVMProperty(int code);

  public static native long currentTimeMillis();

  public static native int identityHashCode(Object o);

  public static void loadLibrary(String name) {
    Runtime.getRuntime().loadLibrary(name);
  }

  public static void gc() {
    Runtime.getRuntime().gc();
  }

  public static void exit(int code) {
    Runtime.getRuntime().exit(code);
  }
}
