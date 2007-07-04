package java.lang;

public abstract class System {
  public static final Output out = new Output();
  public static final Output err = out;

  static {
    loadLibrary("natives");
  }

  public static native void loadLibrary(String name);

  public static native String getProperty(String name);

  public static class Output {
    public native void print(String s);

    public void println(String s) {
      print(s);
      print(getProperty("line.separator"));
    }
  }
}
