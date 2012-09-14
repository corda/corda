/* Copyright (c) 2008-2012, Avian Contributors

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
  private final boolean readOnly;

  protected ByteBuffer(boolean readOnly) {
    this.readOnly = readOnly;
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
      checkPut(position, src.remaining());

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
    checkPut(offset, 1);
    doPut(offset, val);
    return this;
  }

  public ByteBuffer put(byte val) {
    put(position, val);
    ++ position;
    return this;
  }

  public ByteBuffer put(byte[] arr) {
    return put(arr, 0, arr.length);
  }

  public ByteBuffer putLong(int position, long val) {
    checkPut(position, 8);

    doPut(position    , (byte) ((val >> 56) & 0xff));
    doPut(position + 1, (byte) ((val >> 48) & 0xff));
    doPut(position + 2, (byte) ((val >> 40) & 0xff));
    doPut(position + 3, (byte) ((val >> 32) & 0xff));
    doPut(position + 4, (byte) ((val >> 24) & 0xff));
    doPut(position + 5, (byte) ((val >> 16) & 0xff));
    doPut(position + 6, (byte) ((val >>  8) & 0xff));
    doPut(position + 7, (byte) ((val      ) & 0xff));
    
    return this;
  }

  public ByteBuffer putInt(int position, int val) {
    checkPut(position, 4);

    doPut(position    , (byte) ((val >> 24) & 0xff));
    doPut(position + 1, (byte) ((val >> 16) & 0xff));
    doPut(position + 2, (byte) ((val >>  8) & 0xff));
    doPut(position + 3, (byte) ((val      ) & 0xff));

    return this;
  }

  public ByteBuffer putShort(int position, short val) {
    checkPut(position, 2);

    doPut(position    , (byte) ((val >> 8) & 0xff));
    doPut(position + 1, (byte) ((val     ) & 0xff));

    return this;
  }

  public ByteBuffer putLong(long val) {
    putLong(position, val);
    position += 8;
    return this;
  }

  public ByteBuffer putInt(int val) {
    putInt(position, val);
    position += 4;
    return this;
  }

  public ByteBuffer putShort(short val) {
    putShort(position, val);
    position += 2;
    return this;
  }

  public byte get() {
    return get(position++);
  }

  public byte get(int position) {
    checkGet(position, 1);
    return doGet(position);
  }

  public ByteBuffer get(byte[] dst) {
    return get(dst, 0, dst.length);
  }

  public long getLong(int position) {
    checkGet(position, 8);

    return (((long) (doGet(position    ) & 0xFF)) << 56)
      |    (((long) (doGet(position + 1) & 0xFF)) << 48)
      |    (((long) (doGet(position + 2) & 0xFF)) << 40)
      |    (((long) (doGet(position + 3) & 0xFF)) << 32)
      |    (((long) (doGet(position + 4) & 0xFF)) << 24)
      |    (((long) (doGet(position + 5) & 0xFF)) << 16)
      |    (((long) (doGet(position + 6) & 0xFF)) <<  8)
      |    (((long) (doGet(position + 7) & 0xFF))      );
  }

  public int getInt(int position) {
    checkGet(position, 4);

    return (((int) (doGet(position    ) & 0xFF)) << 24)
      |    (((int) (doGet(position + 1) & 0xFF)) << 16)
      |    (((int) (doGet(position + 2) & 0xFF)) <<  8)
      |    (((int) (doGet(position + 3) & 0xFF))      );
  }

  public short getShort(int position) {
    checkGet(position, 2);

    return (short) ((  ((int) (doGet(position    ) & 0xFF)) << 8)
                    | (((int) (doGet(position + 1) & 0xFF))     ));
  }

  public long getLong() {
    long r = getLong(position);
    position += 8;
    return r;
  }

  public int getInt() {
    int r = getInt(position);
    position += 4;
    return r;
  }

  public short getShort() {
    short r = getShort(position);
    position += 2;
    return r;
  }

  protected void checkPut(int position, int amount) {
    if (readOnly) throw new ReadOnlyBufferException();
    if (position < 0 || position+amount > limit)
      throw new IndexOutOfBoundsException();
  }

  protected void checkGet(int position, int amount) {
    if (amount > limit-position) throw new IndexOutOfBoundsException();
  }
}
