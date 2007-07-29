package java.io;

public class OutputStreamWriter extends Writer {
  private final OutputStream out;

  public OutputStreamWriter(OutputStream out) {
    this.out = out;
  }
  
  public void write(char[] b, int offset, int length) throws IOException {
    byte[] buffer = new byte[length];
    for (int i = 0; i < length; ++i) {
      buffer[i] = (byte) b[i + offset];
    }
    out.write(buffer);
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }
}
