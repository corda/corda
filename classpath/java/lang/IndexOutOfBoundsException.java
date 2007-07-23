package java.lang;

public class IndexOutOfBoundsException extends RuntimeException {
  public IndexOutOfBoundsException(String message, Throwable cause) {
    super(message, cause);
  }

  public IndexOutOfBoundsException(String message) {
    this(message, null);
  }

  public IndexOutOfBoundsException(Throwable cause) {
    this(null, cause);
  }

  public IndexOutOfBoundsException() {
    this(null, null);
  }
}
