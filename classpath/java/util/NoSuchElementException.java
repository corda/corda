package java.util;

public class NoSuchElementException extends RuntimeException {
  public NoSuchElementException(String message) {
    super(message);
  }

  public NoSuchElementException() {
    super();
  }
}
