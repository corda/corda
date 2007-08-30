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

  public long skip(long count) throws IOException {
    final long Max = 8 * 1024;
    int size = (int) (count < Max ? count : Max);
    byte[] buffer = new byte[size];
    long remaining = count;
    int c;
    while ((c = read(buffer, 0, (int) (size < remaining ? size : remaining)))
           >= 0
           && remaining > 0)
    {
      remaining -= c;
    }
    return count - remaining;    
  }

  public int available() throws IOException {
    return 0;
  }

  public void close() throws IOException { }
}
