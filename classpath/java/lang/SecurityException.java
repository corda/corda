package java.lang;

public class SecurityException extends RuntimeException {
  public SecurityException(String message, Throwable cause) {
    super(message, cause);
  }

  public SecurityException(String message) {
    this(message, null);
  }

  public SecurityException(Throwable cause) {
    this(null, cause);
  }

  public SecurityException() {
    this(null, null);
  }
}
