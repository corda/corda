package java.io;

public class IOException extends Exception {
  public IOException(String message, Throwable cause) {
    super(message, cause);
  }

  public IOException(String message) {
    this(message, null);
  }

  public IOException(Throwable cause) {
    this(null, cause);
  }

  public IOException() {
    this(null, null);
  }
}
