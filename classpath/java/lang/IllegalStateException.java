package java.lang;

public class IllegalStateException extends RuntimeException {
  public IllegalStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public IllegalStateException(String message) {
    this(message, null);
  }

  public IllegalStateException(Throwable cause) {
    this(null, cause);
  }

  public IllegalStateException() {
    this(null, null);
  }
}
