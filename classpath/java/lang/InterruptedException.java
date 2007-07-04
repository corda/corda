package java.lang;

public class InterruptedException extends Exception {
  public InterruptedException(String message, Throwable cause) {
    super(message, cause);
  }

  public InterruptedException(String message) {
    this(message, null);
  }

  public InterruptedException(Throwable cause) {
    this(null, cause);
  }

  public InterruptedException() {
    this(null, null);
  }
}
