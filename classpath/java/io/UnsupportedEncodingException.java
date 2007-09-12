package java.io;

public class UnsupportedEncodingException extends IOException {
  public UnsupportedEncodingException(String message) {
    super(message);
  }

  public UnsupportedEncodingException() {
    this(null);
  }
}
