package java.io;

public abstract class InputStream {
  public abstract int read() throws IOException;

  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  public int read(byte[] buffer, int offset, int length) throws IOException {
    for (int i = 0; i < length; ++i) {
      int c = read();
      if (c == -1) {
        if (i == 0) {
          return -1;
        } else {
          return i;
        }
      } else {
        buffer[offset + i] = (byte) (c & 0xFF);
      }
    }
    return length;
  }

  public void close() throws IOException { }
}
