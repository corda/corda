package java.io;

public class ObjectInputStream extends InputStream {
  private final InputStream in;

  public ObjectInputStream(InputStream in) {
    this.in = in;
  }

  public int read() throws IOException {
    return in.read();
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    return in.read(b, offset, length);
  }

  public void close() throws IOException {
    in.close();
  }
}
