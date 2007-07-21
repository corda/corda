package java.lang;

public class Runtime {
  private static final Runtime instance = new Runtime();

  private Runtime() { }

  public static Runtime getRuntime() {
    return instance;
  }

  public native void loadLibrary(String name);

  public native void gc();

  public native void exit(int code);
}
