package java.lang;

public class RuntimeException extends Exception {
  public RuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public RuntimeException(String message) {
    this(message, null);
  }

  public RuntimeException(Throwable cause) {
    this(null, cause);
  }

  public RuntimeException() {
    this(null, null);
  }
}
