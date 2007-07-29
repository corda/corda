package java.io;

public class ObjectOutputStream extends OutputStream {
  private final OutputStream out;

  public ObjectOutputStream(OutputStream out) {
    this.out = out;
  }
  
  public void write(int c) throws IOException {
    out.write(c);
  }

  public void write(byte[] b, int offset, int length) throws IOException {
    out.write(b, offset, length);
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }
  
}
