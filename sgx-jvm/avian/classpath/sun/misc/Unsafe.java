/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package sun.misc;

import java.lang.reflect.Field;

public final class Unsafe {
  private void Unsafe() { }

  private static final Unsafe theUnsafe = new Unsafe();

  public static Unsafe getUnsafe() {
    return theUnsafe;
  }

  public native long allocateMemory(long bytes);

  public native void setMemory
    (Object base, long offset, long count, byte value);

  public native void freeMemory(long address);

  public native byte getByte(long address);

  public native void putByte(long address, byte x);

  public native short getShort(long address);

  public native void putShort(long address, short x);

  public native char getChar(long address);

  public native void putChar(long address, char x);

  public native int getInt(long address);

  public native void putInt(long address, int x);

  public native long getLong(long address);

  public native void putLong(long address, long x);

  public native float getFloat(long address);

  public native void putFloat(long address, float x);

  public native double getDouble(long address);

  public native void putDouble(long address, double x);

  public native boolean getBooleanVolatile(Object o, long offset);

  public native void putBooleanVolatile(Object o, long offset, boolean x);

  public native byte getByteVolatile(Object o, long offset);

  public native void putByteVolatile(Object o, long offset, byte x);

  public native short getShortVolatile(Object o, long offset);

  public native void putShortVolatile(Object o, long offset, short x);

  public native char getCharVolatile(Object o, long offset);

  public native void putCharVolatile(Object o, long offset, char x);

  public native int getIntVolatile(Object o, long offset);

  public native void putIntVolatile(Object o, long offset, int x);

  public native float getFloatVolatile(Object o, long offset);

  public native void putFloatVolatile(Object o, long offset, float x);

  public native double getDoubleVolatile(Object o, long offset);

  public native void putDoubleVolatile(Object o, long offset, double x);

  public native long getLongVolatile(Object o, long offset);

  public native void putLongVolatile(Object o, long offset, long x);

  public long getLong(Object o, long offset) {
    return getLongVolatile(o, offset);
  }

  public void putLong(Object o, long offset, long x) {
    putLongVolatile(o, offset, x);
  }

  public double getDouble(Object o, long offset) {
    return getDoubleVolatile(o, offset);
  }

  public void putDouble(Object o, long offset, double x) {
    putDoubleVolatile(o, offset, x);
  }

  public native void putOrderedLong(Object o, long offset, long x);

  public native void putOrderedInt(Object o, long offset, int x);

  public native Object getObject(Object o, long offset);

  public native void putObject(Object o, long offset, Object x);

  public native void putObjectVolatile(Object o, long offset, Object x);

  public native void putOrderedObject(Object o, long offset, Object x);

  public native Object getObjectVolatile(Object o, long offset);

  public native long getAddress(long address);

  public native void putAddress(long address, long x);

  public native int arrayBaseOffset(Class arrayClass);

  public native int arrayIndexScale(Class arrayClass);

  public native long objectFieldOffset(Field field);

  public native void park(boolean absolute, long time);

  public native void unpark(Object target);

  public native void copyMemory(Object srcBase, long srcOffset,
                                Object destBase, long destOffset,
                                long count);

  public native boolean compareAndSwapInt(Object o, long offset, int old,
                                          int new_);

  public native boolean compareAndSwapLong(Object o, long offset,
                                                 long old, long new_);

  public native boolean compareAndSwapObject(Object o, long offset, Object old,
                                             Object new_);

  public void copyMemory(long src, long dst, long count) {
    copyMemory(null, src, null, dst, count);
  }

  public native void throwException(Throwable t);
}
