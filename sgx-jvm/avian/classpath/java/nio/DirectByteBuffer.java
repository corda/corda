/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

import sun.misc.Unsafe;

class DirectByteBuffer extends ByteBuffer {
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final int baseOffset = unsafe.arrayBaseOffset(byte[].class);

  protected final long address;

  protected DirectByteBuffer(long address, int capacity, boolean readOnly) {
    super(readOnly);

    this.address = address;
    this.capacity = capacity;
    this.limit = capacity;
    this.position = 0;
  }

  protected DirectByteBuffer(long address, int capacity) {
    this(address, capacity, false);
  }

  public ByteBuffer asReadOnlyBuffer() {
    ByteBuffer b = new DirectByteBuffer(address, capacity, true);
    b.position(position());
    b.limit(limit());
    return b;
  }

  public ByteBuffer slice() {
    return new DirectByteBuffer(address + position, remaining(), true);
  }

  protected void doPut(int position, byte val) {
    unsafe.putByte(address + position, val);
  }

  public ByteBuffer put(ByteBuffer src) {
    if (src instanceof DirectByteBuffer) {
      checkPut(position, src.remaining(), false);

      DirectByteBuffer b = (DirectByteBuffer) src;

      unsafe.copyMemory
        (b.address + b.position, address + position, b.remaining());

      position += b.remaining();
      b.position += b.remaining();

      return this;
    } else {
      return super.put(src);
    }
  }

  public ByteBuffer put(byte[] src, int offset, int length) {
    if (offset < 0 || offset + length > src.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    checkPut(position, length, false);

    unsafe.copyMemory
      (src, baseOffset + offset, null, address + position, length);

    position += length;

    return this;
  }

  public ByteBuffer get(byte[] dst, int offset, int length) {
    if (offset < 0 || offset + length > dst.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    checkGet(position, length, false);

    unsafe.copyMemory
      (null, address + position, dst, baseOffset + offset, length);

    return this;
  }

  protected byte doGet(int position) {
    return unsafe.getByte(address + position);
  }

  public String toString() {
    return "(DirectByteBuffer with address: " + address
      + " position: " + position
      + " limit: " + limit
      + " capacity: " + capacity + ")";
  }

  @Override
  public ByteBuffer duplicate() {
    ByteBuffer b = new DirectByteBuffer(address, capacity, isReadOnly());
    b.limit(this.limit());
    b.position(this.position());
    return b;
  }
}
