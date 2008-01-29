package java.lang;

public class OutOfMemoryError extends Error {
  public OutOfMemoryError(String message) {
    super(message, null);
  }

  public OutOfMemoryError() {
    this(null);
  }
}
