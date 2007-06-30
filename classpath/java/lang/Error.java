package java.lang;

public class Error extends Throwable {
  public Error(String message, Throwable cause) {
    super(message, cause);
  }

  public Error(String message) {
    this(message, null);
  }

  public Error(Throwable cause) {
    this(null, cause);
  }

  public Error() {
    this(null, null);
  }
}
