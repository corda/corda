/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent.atomic;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class AtomicInteger extends Number implements java.io.Serializable {
  private static final long serialVersionUID = 6214790243416807050L;
  
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final long valueOffset;
  
  static {
    try {
      Field<?> f = AtomicInteger.class.getDeclaredField("value");
      valueOffset = unsafe.objectFieldOffset(f);
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
  
  private volatile int value;
  
  public AtomicInteger() {
    this(0);
  }
  
  public AtomicInteger(int initialValue) {
    this.value = initialValue;
  }
  
  public int get() {
    return value;
  }
  
  public void set(int newValue) {
    this.value = newValue;
  }
  
  public void lazySet(int newValue) {
    unsafe.putOrderedInt(this, valueOffset, newValue);
  }
  
  public boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
  }
  
  public boolean weakCompareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
  }
  
  public int getAndSet(int newValue) {
    while (true) {
      int current = value;
      if (compareAndSet(current, newValue)) {
        return current;
      }
    }
  }
  
  public int getAndAdd(int delta) {
    while (true) {
      int current = value;
      int next = current + delta;
      if (compareAndSet(current, next)) {
        return current;
      }
    }
  }
  
  public int getAndIncrement() {
    return getAndAdd(1);
  }
  
  public int getAndDecrement() {
    return getAndAdd(-1);
  }
  
  public int addAndGet(int delta) {
    while (true) {
      int current = value;
      int next = current + delta;
      if (compareAndSet(current, next)) {
        return next;
      }
    }
  }
  
  public int incrementAndGet() {
    return addAndGet(1);
  }
  
  public int decrementAndGet() {
    return addAndGet(-1);
  }
  
  @Override
  public byte byteValue() {
    return (byte)value;
  }

  @Override
  public short shortValue() {
    return (short)value;
  }

  @Override
  public int intValue() {
    return value;
  }

  @Override
  public long longValue() {
    return value;
  }

  @Override
  public float floatValue() {
    return value;
  }

  @Override
  public double doubleValue() {
    return value;
  }
}
