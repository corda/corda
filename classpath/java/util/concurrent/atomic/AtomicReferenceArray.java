/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent.atomic;

import java.util.Arrays;

import sun.misc.Unsafe;

public class AtomicReferenceArray<T> {
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final long arrayOffset = unsafe.arrayBaseOffset(Object.class);
  private static final long arrayScale = unsafe.arrayIndexScale(Object.class);

  private final Object[] array;
  
  public AtomicReferenceArray(int length) {
    array = new Object[length];
  }
  
  public T get(int index) {
    return (T) unsafe.getObjectVolatile
      (array, arrayOffset + (index * arrayScale));
  }
  
  public void set(int index, T newValue) {
    unsafe.putObjectVolatile
      (array, arrayOffset + (index * arrayScale), newValue);
  }
  
  public void lazySet(int index, T newValue) {
    unsafe.putOrderedObject
      (array, arrayOffset + (index * arrayScale), newValue);
  }
  
  public boolean compareAndSet(int index, T expect, T update) {
    return unsafe.compareAndSwapObject
      (array, arrayOffset + (index * arrayScale), expect, update);
  }
  
  public boolean weakCompareAndSet(int index, T expect, T update) {
    return compareAndSet(index, expect, update);
  }
  
  public final T getAndSet(int index, T newValue) {
    while (true) {
      T current = get(index);
      if (compareAndSet(index, current, newValue)) {
        return current;
      }
    }
  }

  public int length() {
    return array.length;
  }
  
  @Override
  public String toString() {
    return Arrays.toString(array);
  }
}
