package java.lang;

public class Throwable {
  private String message;
  private StackTraceElement[] trace;
  private Throwable cause;

  public Throwable(String message, Throwable cause) {
    this.message = message;
    this.trace = trace(1);
    this.cause = cause;
  }

  public Throwable(String message) {
    this(message, null);
  }

  public Throwable(Throwable cause) {
    this(null, cause);
  }

  public Throwable() {
    this(null, null);
  }

  private static native StackTraceElement[] trace(int skipCount);
}
