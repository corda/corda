package java.lang;

public class ExceptionInInitializerError extends Error {
  public ExceptionInInitializerError(String message) {
    super(message);
  }

  public ExceptionInInitializerError() {
    super();
  }
}
