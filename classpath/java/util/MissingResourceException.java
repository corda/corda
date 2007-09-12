package java.util;

public class MissingResourceException extends RuntimeException {
  private final String class_;
  private final String key;

  public MissingResourceException(String message, String class_, String key) {
    super(message);
    this.class_ = class_;
    this.key = key;
  }

  public String getClassName() {
    return class_;
  }

  public String getKey() {
    return key;
  }
}
