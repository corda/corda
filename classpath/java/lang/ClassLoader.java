package java.lang;

public class ClassLoader {
  private static final ClassLoader instance = new ClassLoader();

  private ClassLoader() { }

  public static ClassLoader getSystemClassLoader() {
    return instance;
  }  
}
