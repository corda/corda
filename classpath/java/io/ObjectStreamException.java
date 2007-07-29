package java.io;

public class ObjectStreamException extends IOException {
  public ObjectStreamException(String message) {
    super(message);
  }

  public ObjectStreamException() {
    this(null);
  }
}
