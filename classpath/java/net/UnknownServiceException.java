package java.net;

import java.io.IOException;

public class UnknownServiceException extends IOException {
  public UnknownServiceException(String message) {
    super(message);
  }

  public UnknownServiceException() {
    this(null);
  }
}
