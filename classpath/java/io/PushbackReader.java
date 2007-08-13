package java.io;

public class PushbackReader extends Reader {
  private final Reader in;
  private final char[] buffer;
  private int position;
  private int limit;

  public PushbackReader(Reader in, int bufferSize) {
    this.in = in;
    this.buffer = new char[bufferSize];
  }

  public PushbackReader(Reader in) {
    this(in, 1);
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

  public void unread(char[] b, int offset, int length) throws IOException {
    if (position < length) {
      throw new IOException(length + " not in [0," + position + "]");
    } else {
      System.arraycopy(buffer, position - length, b, offset, length);
      position -= length;
    }
  }

  public void unread(char[] b) throws IOException {
    unread(b, 0, b.length);
  }

  public void unread(int c) throws IOException {
    unread(new char[] { (char) c });
  }

  public void close() throws IOException {
    in.close();
  }
}
