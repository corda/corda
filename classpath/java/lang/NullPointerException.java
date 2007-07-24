package java.lang;

public class NullPointerException extends RuntimeException {
  public NullPointerException(String message, Throwable cause) {
    super(message, cause);
  }

  public NullPointerException(String message) {
    this(message, null);
  }

  public NullPointerException(Throwable cause) {
    this(null, cause);
  }

  public NullPointerException() {
    this(null, null);
  }
}
