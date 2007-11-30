package java.lang;

public class IllegalThreadStateException extends IllegalArgumentException {
  public IllegalThreadStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public IllegalThreadStateException(String message) {
    this(message, null);
  }

  public IllegalThreadStateException(Throwable cause) {
    this(null, cause);
  }

  public IllegalThreadStateException() {
    this(null, null);
  }

}
