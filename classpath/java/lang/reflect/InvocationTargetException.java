package java.lang.reflect;

public class InvocationTargetException extends Exception {
  public InvocationTargetException(Throwable targetException, String message) {
    super(message, targetException);
  }

  public InvocationTargetException(Throwable targetException) {
    this(targetException, null);
  }

  public InvocationTargetException() {
    this(null, null);
  }

  public Throwable getTargetException() {
    return getCause();
  }
}
