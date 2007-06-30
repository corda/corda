package java.lang;

public class Exception extends Throwable {
  public Exception(String message, Throwable cause) {
    super(message, cause);
  }

  public Exception(String message) {
    this(message, null);
  }

  public Exception(Throwable cause) {
    this(null, cause);
  }

  public Exception() {
    this(null, null);
  }
}
