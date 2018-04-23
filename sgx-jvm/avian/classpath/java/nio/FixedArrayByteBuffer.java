/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

class FixedArrayByteBuffer extends DirectByteBuffer {
  private final byte[] array;
  private final int arrayOffset;

  private FixedArrayByteBuffer(long address,
                                byte[] array,
                                int offset,
                                int capacity,
                                boolean readOnly)
  {
    super(address, capacity, readOnly);

    this.array = array;
    this.arrayOffset = offset;
  }

  public static FixedArrayByteBuffer make(int capacity) {
    long[] address = new long[1];
    byte[] array = allocateFixed(capacity, address);
    return new FixedArrayByteBuffer(address[0], array, 0, capacity, false);
  }

  private static native byte[] allocateFixed(int capacity, long[] address);

  public ByteBuffer asReadOnlyBuffer() {
    ByteBuffer b = new FixedArrayByteBuffer
      (address, array, arrayOffset, capacity, true);
    b.position(position());
    b.limit(limit());
    return b;
  }

  public ByteBuffer slice() {
    return new FixedArrayByteBuffer
      (address + position, array, arrayOffset + position, remaining(), true);
  }
  
  @Override
  public ByteBuffer duplicate() {
    ByteBuffer b = new FixedArrayByteBuffer(address, array, arrayOffset, capacity, isReadOnly());
    b.limit(this.limit());
    b.position(this.position());
    return b;
  }

  public String toString() {
    return "(FixedArrayByteBuffer with address: " + address
      + " array: " + array
      + " arrayOffset: " + arrayOffset
      + " position: " + position
      + " limit: " + limit
      + " capacity: " + capacity + ")";
  }
}
