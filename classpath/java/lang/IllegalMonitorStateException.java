package java.lang;

public class IllegalMonitorStateException extends RuntimeException {
  public IllegalMonitorStateException(String message) {
    super(message, null);
  }

  public IllegalMonitorStateException() {
    this(null);
  }
}
