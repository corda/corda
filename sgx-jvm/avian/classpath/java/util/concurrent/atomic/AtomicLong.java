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

public class AtomicLong extends Number implements java.io.Serializable {
  private static final long serialVersionUID = 1927816293512124184L;
  
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final long valueOffset;
  
  static {
    try {
      Field<?> f = AtomicLong.class.getDeclaredField("value");
      valueOffset = unsafe.objectFieldOffset(f);
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
  
  private volatile long value;
  
  public AtomicLong() {
    this(0);
  }
  
  public AtomicLong(long initialValue) {
    this.value = initialValue;
  }
  
  public long get() {
    return value;
  }
  
  public void set(long newValue) {
    this.value = newValue;
  }
  
  public void lazySet(long newValue) {
    unsafe.putOrderedLong(this, valueOffset, newValue);
  }
  
  public boolean compareAndSet(long expect, long update) {
    return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
  }
  
  public boolean weakCompareAndSet(long expect, long update) {
    return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
  }
  
  public long getAndSet(long newValue) {
    while (true) {
      long current = value;
      if (compareAndSet(current, newValue)) {
        return current;
      }
    }
  }
  
  public long getAndAdd(long delta) {
    while (true) {
      long current = value;
      long next = current + delta;
      if (compareAndSet(current, next)) {
        return current;
      }
    }
  }
  
  public long getAndIncrement() {
    return getAndAdd(1);
  }
  
  public long getAndDecrement() {
    return getAndAdd(-1);
  }
  
  public long addAndGet(long delta) {
    while (true) {
      long current = value;
      long next = current + delta;
      if (compareAndSet(current, next)) {
        return next;
      }
    }
  }
  
  public long incrementAndGet() {
    return addAndGet(1);
  }
  
  public long decrementAndGet() {
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
    return (int)value;
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
