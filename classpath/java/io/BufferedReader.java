package java.io;

public class BufferedReader extends Reader {
  private final Reader in;
  private final char[] buffer;
  private int position;
  private int limit;

  public BufferedReader(Reader in, int bufferSize) {
    this.in = in;
    this.buffer = new char[bufferSize];
  }

  protected BufferedReader(Reader in) {
    this(in, 32);
  }
  
  private void fill() throws IOException {
    position = 0;
    limit = in.read(buffer);
  }

  public String readLine() throws IOException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (position >= limit) {
        fill();
      }

      if (position >= limit) {
        return sb.toString();
      }

      for (int i = position; i < limit; ++i) {
        if (buffer[i] == '\n') {
          sb.append(buffer, position, i);
          position = i + 1;
          return sb.toString();
        }
      }
    }
  }

  public int read(char[] b, int offset, int length) throws IOException {
    int count = 0;

    if (position < limit) {
      int remaining = limit - position;
      if (remaining > length) {
        remaining = length;
      }

      System.arraycopy(buffer, position, b, offset, remaining);

      count += remaining;
      position += remaining;
      offset += remaining;
      length -= remaining;
    }

    if (length > 0) {
      int c = in.read(b, offset, length);
      if (c == -1) {
        if (count == 0) {
          count = -1;
        }
      } else {
        count += c;
      }
    }

    return count;
  }

  public void close() throws IOException {
    in.close();
  }
}
