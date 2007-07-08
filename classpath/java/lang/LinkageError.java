package java.lang;

public class LinkageError extends Error {
  public LinkageError(String message) {
    super(message, null);
  }

  public LinkageError() {
    this(null);
  }
}
