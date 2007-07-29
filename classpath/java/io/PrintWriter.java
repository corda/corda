package java.io;

public class PrintWriter extends Writer {
  private static final char[] newline
    = System.getProperty("line.separator").toCharArray();

  private final Writer out;
  private final boolean autoFlush;

  public PrintWriter(Writer out, boolean autoFlush) {
    this.out = out;
    this.autoFlush = true;
  }

  public PrintWriter(Writer out) {
    this(out, false);
  }

  public synchronized void print(String s) {
    try {
      out.write(s.toCharArray());
      if (autoFlush) flush();
    } catch (IOException e) { }
  }

  public synchronized void println(String s) {
    try {
      out.write(s.toCharArray());    
      out.write(newline);
      if (autoFlush) flush();
    } catch (IOException e) { }
  }
  
  public void write(char[] buffer, int offset, int length) throws IOException {
    out.write(buffer, offset, length);
    if (autoFlush) flush();
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }
}
