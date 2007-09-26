package java.lang;

public class IncompatibleClassChangeError extends LinkageError {
  public IncompatibleClassChangeError(String message) {
    super(message);
  }

  public IncompatibleClassChangeError() {
    super();
  }
}
