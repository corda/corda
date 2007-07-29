package java.io;

public class InputStreamReader extends Reader {
  private final InputStream in;

  public InputStreamReader(InputStream in) {
    this.in = in;
  }
  
  public int read(char[] b, int offset, int length) throws IOException {
    byte[] buffer = new byte[length];
    int c = in.read(buffer);
    for (int i = 0; i < c; ++i) {
      b[i + offset] = (char) buffer[i];
    }
    return c;
  }

  public void close() throws IOException {
    in.close();
  }
}
