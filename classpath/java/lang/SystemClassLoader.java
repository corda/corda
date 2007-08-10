package java.lang;

import java.net.URL;
import java.net.MalformedURLException;

public class SystemClassLoader extends ClassLoader {
  private Object map;

  protected native Class findClass(String name) throws ClassNotFoundException;

  protected native Class findLoadedClass(String name);

  private native boolean resourceExists(String name);

  protected URL findResource(String name) {
    if (resourceExists(name)) {
      try {
        return new URL("resource://" + name);
      } catch (MalformedURLException ignored) { }
    }
    return null;
  }
}
