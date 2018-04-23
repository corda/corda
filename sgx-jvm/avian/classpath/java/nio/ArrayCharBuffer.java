/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

class ArrayCharBuffer extends CharBuffer {
  private final char[] array;
  private final int arrayOffset;

  ArrayCharBuffer(char[] array, int offset, int length, boolean readOnly) {
    super(readOnly);

    this.array = array;
    this.arrayOffset = offset;
    this.capacity = length;
    this.limit = length;
    this.position = 0;
  }

  public CharBuffer asReadOnlyBuffer() {
    CharBuffer b = new ArrayCharBuffer(array, arrayOffset, capacity, true);
    b.position(position());
    b.limit(limit());
    return b;
  }

  public boolean hasArray() {
    return true;
  }

  public char[] array() {
    return array;
  }

  public CharBuffer slice() {
    return new ArrayCharBuffer
      (array, arrayOffset + position, remaining(), true);
  }

  public int arrayOffset() {
    return arrayOffset;
  }

  protected void doPut(int position, char val) {
    array[arrayOffset + position] = val;
  }

  public CharBuffer put(CharBuffer src) {
    int length = src.remaining();
    checkPut(position, length, false);
    src.get(array, arrayOffset + position, length);
    position += length;
    return this;
  }

  public CharBuffer put(char[] src, int offset, int length) {
    checkPut(position, length, false);

    System.arraycopy(src, offset, array, arrayOffset + position, length);
    position += length;

    return this;
  }

  public CharBuffer get(char[] dst, int offset, int length) {
    checkGet(position, length, false);

    System.arraycopy(array, arrayOffset + position, dst, offset, length);
    position += length;

    return this;
  }

  protected char doGet(int position) {
    return array[arrayOffset+position];
  }

  public String toString() {
    return "(ArrayCharBuffer with array: " + array
      + " arrayOffset: " + arrayOffset
      + " position: " + position
      + " limit: " + limit
      + " capacity: " + capacity + ")";
  }
}
