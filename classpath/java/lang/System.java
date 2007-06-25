package java.lang;

public abstract class System {
  public static final Output out = new Output();

  static {
    loadLibrary("natives");
  }

  public static native void loadLibrary(String name);

  public static class Output {
    public native void println(String s);
  }
}
