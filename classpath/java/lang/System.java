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
  private static final int FileSeparator = 101;
  private static final int OsName = 102;
  private static final int JavaIoTmpdir = 103;
  private static final int UserHome = 104;

  private static Property properties;
  
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
    for (Property p = properties; p != null; p = p.next) {
      if (p.name.equals(name)) {
        return p.value;
      }
    }

    int code = Unknown;
    if (name.equals("java.class.path")) {
      code = JavaClassPath;
    } else if (name.equals("java.io.tmpdir")) {
      code = JavaIoTmpdir;
    } else if (name.equals("line.separator")) {
      code = LineSeparator;
    } else if (name.equals("file.separator")) {
      code = FileSeparator;
    } else if (name.equals("user.home")) {
      code = UserHome;
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

  public static String setProperty(String name, String value) {
    for (Property p = properties; p != null; p = p.next) {
      if (p.name.equals(name)) {
        String oldValue = p.value;
        p.value = value;
        return oldValue;
      }
    }

    properties = new Property(name, value, properties);
    return null;
  }

  private static native String getProperty(int code);

  private static native String getVMProperty(int code);

  public static native long currentTimeMillis();

  public static native int identityHashCode(Object o);

  public static String mapLibraryName(String name) {
    if (name != null) {
      return doMapLibraryName(name);
    } else {
      throw new NullPointerException();
    }
  }

  private static native String doMapLibraryName(String name);

  public static void load(String path) {
    Runtime.getRuntime().load(path);
  }

  public static void loadLibrary(String name) {
    Runtime.getRuntime().loadLibrary(name);
  }

  public static void gc() {
    Runtime.getRuntime().gc();
  }

  public static void exit(int code) {
    Runtime.getRuntime().exit(code);
  }

  private static class Property {
    public final String name;
    public String value;
    public final Property next;

    public Property(String name, String value, Property next) {
      this.name = name;
      this.value = value;
      this.next = next;
    }
  }
}
