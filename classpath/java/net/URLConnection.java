package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class URLConnection {
  protected final URL url;

  protected URLConnection(URL url) {
    this.url = url;
  }

  public Object getContent() throws IOException {
    return getInputStream();
  }

  public InputStream getInputStream() throws IOException {
    throw new UnknownServiceException();
  }

  public OutputStream getOutputStream() throws IOException {
    throw new UnknownServiceException();
  }
}
