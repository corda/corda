package java.util;

import java.io.InputStream;
import java.io.IOException;

public class PropertyResourceBundle extends ResourceBundle {
  private final Properties map = new Properties();

  public PropertyResourceBundle(InputStream in) throws IOException {
    map.load(in);
  }

  public Object handleGetObject(String key) {
    return map.get(key);
  }
}
