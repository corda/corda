package java.net;

import java.io.IOException;
import java.io.FileNotFoundException;
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

  public InputStream openStream() throws IOException {
    return openConnection().getInputStream();
  }

  public Object getContent() throws IOException {
    return openStream();
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
    private int position;

    public ResourceInputStream(String path) throws IOException {
      peer = open(path);
      if (peer == 0) {
        throw new FileNotFoundException(path);
      }
    }

    private static native long open(String path) throws IOException;

    private static native int read(long peer, int position) throws IOException;

    private static native int read(long peer, int position,
                                   byte[] b, int offset, int length)
      throws IOException;

    public static native void close(long peer) throws IOException;

    public int read() throws IOException {
      if (peer != 0) {
        int c = read(peer, position);
        if (c >= 0) {
          ++ position;
        }
        return c;
      } else {
        throw new IOException();
      }
    }

    public int read(byte[] b, int offset, int length) throws IOException {
      if (peer != 0) {
        if (b == null) {
          throw new NullPointerException();
        }

        if (offset < 0 || offset + length > b.length) {
          throw new ArrayIndexOutOfBoundsException();
        }

        int c = read(peer, position, b, offset, length);
        if (c >= 0) {
          position += c;
        }
        return c;
      } else {
        throw new IOException();
      }
    }

    public void close() throws IOException {
      if (peer != 0) {
        close(peer);
        peer = 0;
      }
    }
  }
}
