package java.lang;

public class NoSuchFieldError extends IncompatibleClassChangeError {
  public NoSuchFieldError(String message) {
    super(message);
  }

  public NoSuchFieldError() {
    super();
  }
}
