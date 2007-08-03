package java.net;

import java.io.IOException;
import java.io.InputStream;

public final class URL {
  private final URLStreamHandler handler;
  private String protocol;
  private String host;
  private int port;
  private String file;
  private String ref;

  public URL(String s) throws MalformedURLException {
    int colon = s.indexOf(':');
    int slash = s.indexOf('/');
    if (colon > 0 && (slash < 0 || colon < slash)) {
      handler = findHandler(s.substring(0, colon));
      handler.parseURL(this, s, colon + 1, s.length());
    } else {
      throw new MalformedURLException(s);
    }
  }

  public String getProtocol() {
    return protocol;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getFile() {
    return file;
  }

  public String getRef() {
    return ref;
  }

  public URLConnection openConnection() throws IOException {
    return handler.openConnection(this);
  }

  public Object getContent() throws IOException {
    return openConnection().getContent();
  }

  private static URLStreamHandler findHandler(String protocol)
    throws MalformedURLException
  {
    if ("resource".equals(protocol)) {
      return new ResourceHandler();
    } else {
      throw new MalformedURLException("unknown protocol: " + protocol);
    }
  }

  protected void set(String protocol, String host, int port, String file,
                     String ref)
  {
    this.protocol = protocol;
    this.host = host;
    this.port = port;
    this.file = file;
    this.ref = ref;
  }

  private static class ResourceHandler extends URLStreamHandler {
    protected URLConnection openConnection(URL url) {
      return new ResourceConnection(url);
    }
  }

  private static class ResourceConnection extends URLConnection {
    public ResourceConnection(URL url) {
      super(url);
    }

    public InputStream getInputStream() throws IOException {
      return new ResourceInputStream(url.getFile());
    }
  }

  private static class ResourceInputStream extends InputStream {
    private long peer;

    public ResourceInputStream(String path) throws IOException {
      peer = open(path);
    }

    private static native long open(String path) throws IOException;

    private static native int read(long peer) throws IOException;

    private static native int read(long peer, byte[] b, int offset, int length)
      throws IOException;

    public static native void close(long peer) throws IOException;

    public int read() throws IOException {
      return read(peer);
    }

    public int read(byte[] b, int offset, int length) throws IOException {
      if (b == null) {
        throw new NullPointerException();
      }

      if (offset < 0 || offset + length > b.length) {
        throw new ArrayIndexOutOfBoundsException();
      }

      return read(peer, b, offset, length);
    }

    public void close() throws IOException {
      close(peer);
      peer = 0;
    }
  }
}
