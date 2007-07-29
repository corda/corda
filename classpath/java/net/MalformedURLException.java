package java.net;

public class MalformedURLException extends Exception {
  public MalformedURLException(String message) {
    super(message);
  }

  public MalformedURLException() {
    this(null);
  }
}
