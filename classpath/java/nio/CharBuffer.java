/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

public abstract class CharBuffer
  extends Buffer
  implements Comparable<CharBuffer>
{
  private final boolean readOnly;

  protected CharBuffer(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public static CharBuffer allocate(int capacity) {
    return new ArrayCharBuffer(new char[capacity], 0, capacity, false);
  }

  public static CharBuffer wrap(char[] array) {
    return wrap(array, 0, array.length);
  }

  public static CharBuffer wrap(char[] array, int offset, int length) {
    return new ArrayCharBuffer(array, offset, length, false);
  }

  public abstract CharBuffer asReadOnlyBuffer();

  public abstract CharBuffer slice();

  protected abstract void doPut(int offset, char value);

  public abstract CharBuffer put(char[] src, int offset, int length);

  protected abstract char doGet(int offset);

  public abstract CharBuffer get(char[] dst, int offset, int length);

  public boolean hasArray() {
    return false;
  }

  public CharBuffer compact() {
    int remaining = remaining();

    if (position != 0) {
      CharBuffer b = slice();
      position = 0;
      put(b);
    }

    position = remaining;
    limit(capacity());
    
    return this;
  }

  public CharBuffer put(CharBuffer src) {
    if (src.hasArray()) {
      checkPut(position, src.remaining(), false);

      put(src.array(), src.arrayOffset() + src.position, src.remaining());
      src.position(src.position() + src.remaining());

      return this;
    } else {
      char[] buffer = new char[src.remaining()];
      src.get(buffer);
      return put(buffer);
    }
  }

  public int compareTo(CharBuffer o) {
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
    return o instanceof CharBuffer && compareTo((CharBuffer) o) == 0;
  }

  public char[] array() {
    throw new UnsupportedOperationException();
  }

  public int arrayOffset() {
    throw new UnsupportedOperationException();
  }

  public CharBuffer put(int offset, char val) {
    checkPut(offset, 1, true);
    doPut(offset, val);
    return this;
  }

  public CharBuffer put(char val) {
    put(position, val);
    ++ position;
    return this;
  }

  public CharBuffer put(char[] arr) {
    return put(arr, 0, arr.length);
  }

  public char get() {
    checkGet(position, 1, false);
    return doGet(position++);
  }

  public char get(int position) {
    checkGet(position, 1, true);
    return doGet(position);
  }

  public CharBuffer get(char[] dst) {
    return get(dst, 0, dst.length);
  }

  protected void checkPut(int position, int amount, boolean absolute) {
    if (readOnly) {
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
}
