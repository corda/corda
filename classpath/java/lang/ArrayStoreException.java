package java.lang;

public class ArrayStoreException extends RuntimeException {
  public ArrayStoreException(String message) {
    super(message, null);
  }

  public ArrayStoreException() {
    this(null);
  }
}
