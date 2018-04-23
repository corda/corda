package dalvik.system;

public class BaseDexClassLoader extends ClassLoader {
  public native String getLdLibraryPath();
}
