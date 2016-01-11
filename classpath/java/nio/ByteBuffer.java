/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

public abstract class ByteBuffer
  extends Buffer
  implements Comparable<ByteBuffer>
{

  protected ByteBuffer(boolean readOnly) {
    this.readonly = readOnly;
  }

  public static ByteBuffer allocate(int capacity) {
    return new ArrayByteBuffer(new byte[capacity], 0, capacity, false);
  }

  public static ByteBuffer allocateDirect(int capacity) {
    return FixedArrayByteBuffer.make(capacity);
  }

  public static ByteBuffer wrap(byte[] array) {
    return wrap(array, 0, array.length);
  }

  public static ByteBuffer wrap(byte[] array, int offset, int length) {
    return new ArrayByteBuffer(array, offset, length, false);
  }

  public abstract ByteBuffer asReadOnlyBuffer();

  public abstract ByteBuffer slice();
  
  public abstract ByteBuffer duplicate();

  protected abstract void doPut(int offset, byte val);

  public abstract ByteBuffer put(byte[] arr, int offset, int len);

  protected abstract byte doGet(int offset);

  public abstract ByteBuffer get(byte[] dst, int offset, int length);

  public boolean hasArray() {
    return false;
  }

  public ByteBuffer compact() {
    int remaining = remaining();

    if (position != 0) {
      ByteBuffer b = slice();
      position = 0;
      put(b);
    }

    position = remaining;
    limit(capacity());
    
    return this;
  }

  public ByteBuffer put(ByteBuffer src) {
    if (src.hasArray()) {
      checkPut(position, src.remaining(), false);

      put(src.array(), src.arrayOffset() + src.position, src.remaining());
      src.position(src.position() + src.remaining());

      return this;
    } else {
      byte[] buffer = new byte[src.remaining()];
      src.get(buffer);
      return put(buffer);
    }
  }

  public int compareTo(ByteBuffer o) {
    int end = (remaining() < o.remaining() ? remaining() : o.remaining());

    for (int i = 0; i < end; ++i) {
      int d = get(position + i) - o.get(o.position + i);
      if (d != 0) {
        return d;
      }
    }
    return remaining() - o.remaining();
  }

  public boolean equals(Object o) {
    return o instanceof ByteBuffer && compareTo((ByteBuffer) o) == 0;
  }

  public byte[] array() {
    throw new UnsupportedOperationException();
  }

  public int arrayOffset() {
    throw new UnsupportedOperationException();
  }

  public ByteBuffer put(int offset, byte val) {
    checkPut(offset, 1, true);
    doPut(offset, val);
    return this;
  }

  public ByteBuffer put(byte val) {
    checkPut(position, 1, false);
    doPut(position, val);
    ++ position;
    return this;
  }

  public ByteBuffer put(byte[] arr) {
    return put(arr, 0, arr.length);
  }
  
  private void rawPutLong(int position, long val) {
    doPut(position    , (byte) ((val >> 56) & 0xff));
    doPut(position + 1, (byte) ((val >> 48) & 0xff));
    doPut(position + 2, (byte) ((val >> 40) & 0xff));
    doPut(position + 3, (byte) ((val >> 32) & 0xff));
    doPut(position + 4, (byte) ((val >> 24) & 0xff));
    doPut(position + 5, (byte) ((val >> 16) & 0xff));
    doPut(position + 6, (byte) ((val >>  8) & 0xff));
    doPut(position + 7, (byte) ((val      ) & 0xff));
  }

  private void rawPutInt(int position, int val) {
    doPut(position    , (byte) ((val >> 24) & 0xff));
    doPut(position + 1, (byte) ((val >> 16) & 0xff));
    doPut(position + 2, (byte) ((val >>  8) & 0xff));
    doPut(position + 3, (byte) ((val      ) & 0xff));
  }

  private void rawPutShort(int position, short val) {
    doPut(position    , (byte) ((val >> 8) & 0xff));
    doPut(position + 1, (byte) ((val     ) & 0xff));
  }
  
  public ByteBuffer putDouble(int position, double val) {
    return putLong(position, Double.doubleToRawLongBits(val));
  }
  
  public ByteBuffer putFloat(int position, float val) {
    return putInt(position, Float.floatToRawIntBits(val));
  }

  public ByteBuffer putLong(int position, long val) {
    checkPut(position, 8, true);

    rawPutLong(position, val);

    return this;
  }

  public ByteBuffer putInt(int position, int val) {
    checkPut(position, 4, true);

    rawPutInt(position, val);

    return this;
  }

  public ByteBuffer putShort(int position, short val) {
    checkPut(position, 2, true);

    rawPutShort(position, val);

    return this;
  }
  
  public ByteBuffer putDouble(double val) {
    return putLong(Double.doubleToRawLongBits(val));
  }
  
  public ByteBuffer putFloat(float val) {
    return putInt(Float.floatToRawIntBits(val));
  }

  public ByteBuffer putLong(long val) {
    checkPut(position, 8, false);

    rawPutLong(position, val);
    position += 8;
    return this;
  }

  public ByteBuffer putInt(int val) {
    checkPut(position, 4, false);

    rawPutInt(position, val);
    position += 4;
    return this;
  }

  public ByteBuffer putShort(short val) {
    checkPut(position, 2, false);

    rawPutShort(position, val);
    position += 2;
    return this;
  }

  public byte get() {
    checkGet(position, 1, false);
    return doGet(position++);
  }

  public byte get(int position) {
    checkGet(position, 1, true);
    return doGet(position);
  }

  public ByteBuffer get(byte[] dst) {
    return get(dst, 0, dst.length);
  }
  
  public double getDouble(int position) {
    return Double.longBitsToDouble(getLong(position));
  }
  
  public float getFloat(int position) {
    return Float.intBitsToFloat(getInt(position));
  }

  public long getLong(int position) {
    checkGet(position, 8, true);

    return rawGetLong(position);
  }

  public int getInt(int position) {
    checkGet(position, 4, true);

    return rawGetInt(position);
  }

  public short getShort(int position) {
    checkGet(position, 2, true);

    return rawGetShort(position);
  }

  private long rawGetLong(int position) {
    return (((long) (doGet(position    ) & 0xFF)) << 56)
      |    (((long) (doGet(position + 1) & 0xFF)) << 48)
      |    (((long) (doGet(position + 2) & 0xFF)) << 40)
      |    (((long) (doGet(position + 3) & 0xFF)) << 32)
      |    (((long) (doGet(position + 4) & 0xFF)) << 24)
      |    (((long) (doGet(position + 5) & 0xFF)) << 16)
      |    (((long) (doGet(position + 6) & 0xFF)) <<  8)
      |    (((long) (doGet(position + 7) & 0xFF))      );
  }

  private int rawGetInt(int position) {
    return (((int) (doGet(position    ) & 0xFF)) << 24)
      |    (((int) (doGet(position + 1) & 0xFF)) << 16)
      |    (((int) (doGet(position + 2) & 0xFF)) <<  8)
      |    (((int) (doGet(position + 3) & 0xFF))      );
  }

  private short rawGetShort(int position) {
    return (short) ((  ((int) (doGet(position    ) & 0xFF)) << 8)
                    | (((int) (doGet(position + 1) & 0xFF))     ));
  }
  
  public double getDouble() {
    return Double.longBitsToDouble(getLong());
  }
  
  public float getFloat() {
    return Float.intBitsToFloat(getInt());
  }
  
  public long getLong() {
    checkGet(position, 8, false);

    long r = rawGetLong(position);
    position += 8;
    return r;
  }

  public int getInt() {
    checkGet(position, 4, false);

    int r = rawGetInt(position);
    position += 4;
    return r;
  }

  public short getShort() {
    checkGet(position, 2, false);

    short r = rawGetShort(position);
    position += 2;
    return r;
  }

  protected void checkPut(int position, int amount, boolean absolute) {
    if (isReadOnly()) {
      throw new ReadOnlyBufferException();
    }

    if (position < 0 || position+amount > limit) {
      throw absolute
        ? new IndexOutOfBoundsException()
        : new BufferOverflowException();
    }
  }

  protected void checkGet(int position, int amount, boolean absolute) {
    if (amount > limit-position) {
      throw absolute
        ? new IndexOutOfBoundsException()
        : new BufferUnderflowException();
    }
  }

  public ByteBuffer order(ByteOrder order) {
    if (order != ByteOrder.BIG_ENDIAN) throw new UnsupportedOperationException();
    return this;
  }

  public ByteOrder order() {
    return ByteOrder.BIG_ENDIAN;
  }
}
