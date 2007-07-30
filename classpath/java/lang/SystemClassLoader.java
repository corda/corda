package java.lang;

public class SystemClassLoader extends ClassLoader {
  private Object map;

  protected native Class findClass(String name) throws ClassNotFoundException;

  protected native Class findLoadedClass(String name);
}
