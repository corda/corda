package java.nio;

public class ByteBuffer {
  private final byte[] array;
  private int arrayOffset;
  private int capacity;
  private int position;
  private int limit;

  public static ByteBuffer allocate(int capacity) {
    return new ByteBuffer(new byte[capacity]);
  }

  public static ByteBuffer wrap(byte[] array) {
    return new ByteBuffer(array);
  }

  private ByteBuffer(byte[] array) {
    this.array = array;
    arrayOffset = 0;
    capacity = array.length;
    limit = capacity;
    position = 0;
  }

  public byte[] array() {
    return array;
  }

  public ByteBuffer slice() {
    ByteBuffer buf = new ByteBuffer(array);
    buf.arrayOffset = arrayOffset + position;
    buf.position = 0;
    buf.capacity = capacity - position;
    buf.limit = buf.capacity;
    return buf;
  }

  public int limit() {
    return limit;
  }

  public int remaining() {
    return limit-position;
  }

  public int position() {
    return position;
  }

  public int capacity() {
    return capacity;
  }

  public int arrayOffset() {
    return arrayOffset;
  }

  public ByteBuffer compact() {
    if (position != 0) {
      System.arraycopy(array, arrayOffset+position, array, arrayOffset, remaining());
    }
    position=0;
    return this;
  }

  public ByteBuffer limit(int newLimit) {
    limit = newLimit;
    return this;
  }

  public ByteBuffer position(int newPosition) {
    position = newPosition;
    return this;
  }

  public ByteBuffer put(byte val) {
    array[arrayOffset+(position++)] = val;
    return this;
  }

  public ByteBuffer put(ByteBuffer src) {
    put(src.array, src.arrayOffset + src.position, src.remaining());
    position += src.remaining();
    return this;
  }

  public ByteBuffer put(byte[] arr) {
    return put(arr, 0, arr.length);
  }

  public ByteBuffer put(byte[] arr, int offset, int len) {
    System.arraycopy(arr, offset, array, arrayOffset+position, len);
    position += len;
    return this;
  }

  public ByteBuffer putInt(int position, int val) {
    array[arrayOffset+position]   = (byte)((val >> 24) & 0xff);
    array[arrayOffset+position+1] = (byte)((val >> 16) & 0xff);
    array[arrayOffset+position+2] = (byte)((val >>  8) & 0xff);
    array[arrayOffset+position+3] = (byte)((val      ) & 0xff);
    return this;
  }

  public ByteBuffer putInt(int val) {
    putInt(position, val);
    position += 4;
    return this;
  }

  public ByteBuffer putShort(short val) {
    put((byte)((val >> 8) & 0xff));
    put((byte)(val & 0xff));
    return this;
  }

  public ByteBuffer putLong(long val) {
    putInt((int)(val >> 32));
    putInt((int)val);
    return this;
  }


  public boolean hasRemaining() {
    return remaining() > 0;
  }

  public byte get() {
    return array[arrayOffset+(position++)];
  }

  public byte get(int position) {
    return array[arrayOffset+position];
  }

  public ByteBuffer flip() {
    limit = position;
    position = 0;
    return this;
  }

  public int getInt() {
    int i = get() << 24;
    i |= (get() & 0xff) << 16;
    i |= (get() & 0xff) << 8;
    i |= (get() & 0xff);
    return i;
  }

  public short getShort() {
    short s = (short)(get() << 8);
    s |= get() & 0xff;
    return s;
  }

  public long getLong() {
    long l = getInt() << 32;
    l |= getInt() & 0xffffffff;
    return l;
  }
}
