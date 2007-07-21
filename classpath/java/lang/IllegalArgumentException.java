package java.lang;

public class IllegalArgumentException extends RuntimeException {
  public IllegalArgumentException(String message, Throwable cause) {
    super(message, cause);
  }

  public IllegalArgumentException(String message) {
    this(message, null);
  }

  public IllegalArgumentException(Throwable cause) {
    this(null, cause);
  }

  public IllegalArgumentException() {
    this(null, null);
  }
}
