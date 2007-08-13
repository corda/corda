package java.io;

public class StreamCorruptedException extends IOException {
  public StreamCorruptedException(String message) {
    super(message);
  }

  public StreamCorruptedException() {
    this(null);
  }
}
