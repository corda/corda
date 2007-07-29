package java.io;

public abstract class Writer {
  public void write(int c) throws IOException {
    char[] buffer = new char[] { (char) c };
    write(buffer);
  }

  public void write(char[] buffer) throws IOException {
    write(buffer, 0, buffer.length);
  }

  public abstract void write(char[] buffer, int offset, int length)
    throws IOException;

  public abstract void flush() throws IOException;

  public abstract void close() throws IOException;
}
