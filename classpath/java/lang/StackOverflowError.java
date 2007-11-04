package java.lang;

public class StackOverflowError extends Error {
  public StackOverflowError(String message) {
    super(message, null);
  }

  public StackOverflowError() {
    this(null);
  }
}
