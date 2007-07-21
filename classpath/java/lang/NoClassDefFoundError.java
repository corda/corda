package java.lang;

public class NoClassDefFoundError extends LinkageError {
  public NoClassDefFoundError(String message) {
    super(message);
  }

  public NoClassDefFoundError() {
    super();
  }
}
