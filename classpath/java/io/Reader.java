package java.io;

public abstract class Reader {
  public int read() throws IOException {
    char[] buffer = new char[1];
    int c = read(buffer);
    if (c <= 0) {
      return -1;
    } else {
      return (int) buffer[0];
    }
  }

  public int read(char[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  public abstract int read(char[] buffer, int offset, int length)
    throws IOException;

  public abstract void close() throws IOException;
}
