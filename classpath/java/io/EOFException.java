package java.io;

public class EOFException extends IOException {
  public EOFException(String message) {
    super(message);
  }

  public EOFException() {
    this(null);
  }
}
