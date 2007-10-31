package java.io;

public class ByteArrayInputStream extends InputStream {
  private final byte[] array;
  private int position;
  private final int length;

  public ByteArrayInputStream(byte[] array, int offset, int length) {
    this.array = array;
    position = offset;
    this.length = length;
  }

  public int read() {
    if (position < length) {
      return array[position++] & 0xff;
    } else {
      return -1;
    }
  }

  public int read(byte[] buffer, int offset, int bufferLength) {
    if (position >= length) {
      return -1;
    }
    int remaining = length-position;
    if (remaining < bufferLength) {
      bufferLength = remaining;
    }
    System.arraycopy(array, position, buffer, offset, bufferLength);
    position += bufferLength;
    return bufferLength;
  }

  public int available() {
    return length - position;
  }
}
