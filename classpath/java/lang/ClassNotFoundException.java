package java.lang;

public class ClassNotFoundException extends Exception {
  public ClassNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public ClassNotFoundException(String message) {
    this(message, null);
  }

  public ClassNotFoundException() {
    this(null, null);
  }
}
