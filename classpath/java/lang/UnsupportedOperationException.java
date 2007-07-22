package java.lang;

public class UnsupportedOperationException extends RuntimeException {
  public UnsupportedOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnsupportedOperationException(String message) {
    this(message, null);
  }

  public UnsupportedOperationException(Throwable cause) {
    this(null, cause);
  }

  public UnsupportedOperationException() {
    this(null, null);
  }
}
