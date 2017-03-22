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

public class AtomicReference<T> implements java.io.Serializable {
  private static final long serialVersionUID = -1848883965231344442L;
  
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final long valueOffset;
  
  static {
    try {
      Field<?> f = AtomicReference.class.getDeclaredField("value");
      valueOffset = unsafe.objectFieldOffset(f);
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
  
  private volatile T value;
  
  public AtomicReference() {
    this(null);
  }
  
  public AtomicReference(T initialValue) {
    this.value = initialValue;
  }
  
  public T get() {
    return value;
  }
  
  public void set(T newValue) {
    value = newValue;
  }
  
  public void lazySet(T newValue) {
    unsafe.putOrderedObject(this, valueOffset, newValue);
  }
  
  public boolean compareAndSet(T expect, T update) {
    return unsafe.compareAndSwapObject(this, valueOffset, expect, update);
  }
  
  public boolean weakCompareAndSet(T expect, T update) {
    return unsafe.compareAndSwapObject(this, valueOffset, expect, update);
  }
  
  public final T getAndSet(T newValue) {
    while (true) {
      T current = value;
      if (compareAndSet(current, newValue)) {
        return current;
      }
    }
  }
  
  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
