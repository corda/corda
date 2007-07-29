package java.io;

public class LineNumberReader extends BufferedReader {
  private int line;

  public LineNumberReader(Reader in, int bufferSize) {
    super(in, bufferSize);
  }

  protected LineNumberReader(Reader in) {
    super(in);
  }

  public int getLineNumber() {
    return line;
  }

  public void setLineNumber(int v) {
    line = v;
  }
  
  public int read(char[] b, int offset, int length) throws IOException {
    int c = super.read(b, offset, length);
    for (int i = 0; i < c; ++i) {
      if (b[i] == '\n') {
        ++ line;
      }
    }
    return c;
  }
}
