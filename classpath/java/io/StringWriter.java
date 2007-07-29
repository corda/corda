package java.io;

public class StringWriter extends Writer {
  private final StringBuilder out = new StringBuilder();
  
  public void write(char[] b, int offset, int length) throws IOException {
    out.append(new String(b, offset, length));
  }

  public String toString() {
    return out.toString();
  }

  public void flush() throws IOException { }

  public void close() throws IOException { }
}
