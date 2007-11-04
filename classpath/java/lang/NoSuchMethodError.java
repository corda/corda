package java.lang;

public class NoSuchMethodError extends IncompatibleClassChangeError {
  public NoSuchMethodError(String message) {
    super(message);
  }

  public NoSuchMethodError() {
    super();
  }
}
