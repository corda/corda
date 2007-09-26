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

  public int available() {
    returns length - position;
  }
}
