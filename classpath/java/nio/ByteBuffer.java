/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

public class ByteBuffer implements Comparable<ByteBuffer> {
  private final byte[] array;
  private int arrayOffset;
  private int capacity;
  private int position;
  private int limit;
  private final boolean readOnly;

  public static ByteBuffer allocate(int capacity) {
    return new ByteBuffer(new byte[capacity], false);
  }

  public static ByteBuffer wrap(byte[] array) {
    return new ByteBuffer(array, false);
  }

  private ByteBuffer(byte[] array, boolean readOnly) {
    this.array = array;
    this.readOnly = readOnly;
    arrayOffset = 0;
    capacity = array.length;
    limit = capacity;
    position = 0;
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
    return array;
  }

  public ByteBuffer clear() {
    position = 0;
    limit = capacity;
    return this;
  }

  public ByteBuffer slice() {
    ByteBuffer buf = new ByteBuffer(array, true);
    buf.arrayOffset = arrayOffset + position;
    buf.position = 0;
    buf.capacity = remaining();
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
    position=remaining();
    limit(capacity());
    
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
    checkPut(1);
    array[arrayOffset+(position++)] = val;
    return this;
  }

  public ByteBuffer put(ByteBuffer src) {
    checkPut(src.remaining());
    put(src.array, src.arrayOffset + src.position, src.remaining());
    src.position += src.remaining();
    return this;
  }

  public ByteBuffer put(byte[] arr) {
    return put(arr, 0, arr.length);
  }

  public ByteBuffer put(byte[] arr, int offset, int len) {
    checkPut(len);
    System.arraycopy(arr, offset, array, arrayOffset+position, len);
    position += len;
    return this;
  }

  public ByteBuffer putInt(int position, int val) {
    checkPut(position, 4);
    array[arrayOffset+position]   = (byte)((val >> 24) & 0xff);
    array[arrayOffset+position+1] = (byte)((val >> 16) & 0xff);
    array[arrayOffset+position+2] = (byte)((val >>  8) & 0xff);
    array[arrayOffset+position+3] = (byte)((val      ) & 0xff);
    return this;
  }

  public ByteBuffer putInt(int val) {
    checkPut(4);
    putInt(position, val);
    position += 4;
    return this;
  }

  public ByteBuffer putShort(short val) {
    checkPut(2);
    put((byte)((val >> 8) & 0xff));
    put((byte)(val & 0xff));
    return this;
  }

  public ByteBuffer putLong(long val) {
    checkPut(8);
    putInt((int)(val >> 32));
    putInt((int)val);
    return this;
  }

  public boolean hasRemaining() {
    return remaining() > 0;
  }

  public byte get() {
    checkGet(1);
    return array[arrayOffset+(position++)];
  }

  public ByteBuffer get(byte[] dst) {
    return get(dst, 0, dst.length);
  }

  public ByteBuffer get(byte[] dst, int offset, int length) {
    checkGet(length);
    System.arraycopy(array, arrayOffset + position, dst, offset, length);
    position += length;
    return this;
  }

  public byte get(int position) {
    checkGet(position, 1);
    return array[arrayOffset+position];
  }

  public ByteBuffer flip() {
    limit = position;
    position = 0;
    return this;
  }

  public int getInt() {
    checkGet(4);
    int i = get() << 24;
    i |= (get() & 0xff) << 16;
    i |= (get() & 0xff) << 8;
    i |= (get() & 0xff);
    return i;
  }

  public short getShort() {
    checkGet(2);
    short s = (short)(get() << 8);
    s |= get() & 0xff;
    return s;
  }

  public long getLong() {
    checkGet(8);
    long l = (long)getInt() << 32;
    l |= (long)getInt() & 0xffffffffL;
    return l;
  }

  private void checkPut(int amount) {
    if (readOnly) throw new ReadOnlyBufferException();
    if (amount > limit-position) throw new IndexOutOfBoundsException();
  }

  private void checkPut(int position, int amount) {
    if (readOnly) throw new ReadOnlyBufferException();
    if (position < 0 || position+amount > limit)
      throw new IndexOutOfBoundsException();
  }

  private void checkGet(int amount) {
    if (amount > limit-position) throw new IndexOutOfBoundsException();
  }

  private void checkGet(int position, int amount) {
    if (position < 0 || position+amount > limit)
      throw new IndexOutOfBoundsException();
  }

  public String toString() {
    return "(ByteBuffer with array: " + array + " arrayOffset: " + arrayOffset + " position: " + position + " remaining; " + remaining() + ")";
  }
}
