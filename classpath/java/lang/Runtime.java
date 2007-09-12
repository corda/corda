package java.lang;

public class Runtime {
  private static final Runtime instance = new Runtime();

  private Runtime() { }

  public static Runtime getRuntime() {
    return instance;
  }

  public void load(String path) {
    if (path != null) {
      load(path, false);
    } else {
      throw new NullPointerException();
    }
  }

  public void loadLibrary(String path) {
    if (path != null) {
      load(path, true);
    } else {
      throw new NullPointerException();
    }
  }

  private static native void load(String name, boolean mapName);

  public native void gc();

  public native void exit(int code);

  public native long freeMemory();
}
