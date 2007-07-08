package java.lang;

public class UnsatisfiedLinkError extends LinkageError {
  public UnsatisfiedLinkError(String message) {
    super(message);
  }

  public UnsatisfiedLinkError() {
    this(null);
  }
}
