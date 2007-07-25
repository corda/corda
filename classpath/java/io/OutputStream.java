package java.io;

public abstract class OutputStream {
  public abstract void write(int c) throws IOException;

  public void write(byte[] buffer) throws IOException {
    write(buffer, 0, buffer.length);
  }

  public void write(byte[] buffer, int offset, int length) throws IOException {
    for (int i = 0; i < length; ++i) {
      write(buffer[offset + i]);
    }
  }

  public void flush() throws IOException { }

  public void close() throws IOException { }
}
