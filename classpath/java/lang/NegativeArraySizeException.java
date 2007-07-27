package java.lang;

public class NegativeArraySizeException extends RuntimeException {
  public NegativeArraySizeException(String message) {
    super(message);
  }

  public NegativeArraySizeException() {
    super();
  }
}
