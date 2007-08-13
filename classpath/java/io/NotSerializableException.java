package java.io;

public class NotSerializableException extends ObjectStreamException {
  public NotSerializableException(String message) {
    super(message);
  }

  public NotSerializableException() {
    this(null);
  }
}
