package java.io;

public class FileNotFoundException extends IOException {
  public FileNotFoundException(String message) {
    super(message);
  }

  public FileNotFoundException() {
    this(null);
  }
}
