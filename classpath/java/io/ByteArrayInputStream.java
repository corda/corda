package java.io;

public class ByteArrayInputStream extends InputStream {
  private final byte[] array;
  private int position;
  private final int limit;

  public ByteArrayInputStream(byte[] array, int offset, int length) {
    this.array = array;
    position = offset;
    this.limit = offset + length;
  }

  public int read() {
    if (position < limit) {
      return array[position++] & 0xff;
    } else {
      return -1;
    }
  }

  public int read(byte[] buffer, int offset, int bufferLength) {
    if (bufferLength == 0) {
      return 0;
    }
    if (position >= limit) {
      return -1;
    }
    int remaining = limit-position;
    if (remaining < bufferLength) {
      bufferLength = remaining;
    }
    System.arraycopy(array, position, buffer, offset, bufferLength);
    position += bufferLength;
    return bufferLength;
  }

  public int available() {
    return limit - position;
  }
}
