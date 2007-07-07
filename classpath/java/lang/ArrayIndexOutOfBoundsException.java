package java.lang;

public class ArrayIndexOutOfBoundsException extends RuntimeException {
  public ArrayIndexOutOfBoundsException(String message, Throwable cause) {
    super(message, cause);
  }

  public ArrayIndexOutOfBoundsException(String message) {
    this(message, null);
  }

  public ArrayIndexOutOfBoundsException(Throwable cause) {
    this(null, cause);
  }

  public ArrayIndexOutOfBoundsException() {
    this(null, null);
  }
}
