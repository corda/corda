/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

class ArrayByteBuffer extends ByteBuffer {
  private final byte[] array;
  private final int arrayOffset;

  ArrayByteBuffer(byte[] array, int offset, int length, boolean readOnly) {
    super(readOnly);

    this.array = array;
    this.arrayOffset = offset;
    this.capacity = length;
    this.limit = length;
    this.position = 0;
  }

  public ByteBuffer asReadOnlyBuffer() {
    ByteBuffer b = new ArrayByteBuffer(array, arrayOffset, capacity, true);
    b.position(position());
    b.limit(limit());
    return b;
  }

  public boolean hasArray() {
    return true;
  }

  public byte[] array() {
    return array;
  }

  public ByteBuffer slice() {
    return new ArrayByteBuffer
      (array, arrayOffset + position, remaining(), true);
  }

  public int arrayOffset() {
    return arrayOffset;
  }

  protected void doPut(int position, byte val) {
    array[arrayOffset + position] = val;
  }

  public ByteBuffer put(ByteBuffer src) {
    int length = src.remaining();
    checkPut(position, length, false);
    src.get(array, arrayOffset + position, length);
    position += length;
    return this;
  }

  public ByteBuffer put(byte[] src, int offset, int length) {
    checkPut(position, length, false);

    System.arraycopy(src, offset, array, arrayOffset + position, length);
    position += length;

    return this;
  }

  public ByteBuffer get(byte[] dst, int offset, int length) {
    checkGet(position, length, false);

    System.arraycopy(array, arrayOffset + position, dst, offset, length);
    position += length;

    return this;
  }

  protected byte doGet(int position) {
    return array[arrayOffset+position];
  }

  public String toString() {
    return "(ArrayByteBuffer with array: " + array
      + " arrayOffset: " + arrayOffset
      + " position: " + position
      + " limit: " + limit
      + " capacity: " + capacity + ")";
  }
  
  @Override
  public ByteBuffer duplicate() {
    ByteBuffer b = new ArrayByteBuffer(array, arrayOffset, capacity, isReadOnly());
    b.limit(this.limit());
    b.position(this.position());
    return b;
  }
}
