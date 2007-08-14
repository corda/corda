package java.io;

public class StringReader extends Reader {
  private final String in;
  private int position = 0;

  public StringReader(String in) {
    this.in = in;
  }
  
  public int read(char[] b, int offset, int length) throws IOException {
    if (length > in.length() - position) {
      length = in.length() - position;
      if (length <= 0) {
        return -1;
      }
    }
    in.getChars(position, length, b, offset);
    position += length;
    return length;
  }

  public void close() throws IOException { }
}
