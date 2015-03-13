/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent.atomic;

public class AtomicBoolean implements java.io.Serializable {
  private static final long serialVersionUID = 4654671469794556979L;
  
  private static final int FALSE_VALUE = 0;
  private static final int TRUE_VALUE = 1;
  
  private final AtomicInteger value;
  
  public AtomicBoolean() {
    this(false);
  }
  
  public AtomicBoolean(boolean initialValue) {
    value = new AtomicInteger(intValue(initialValue));
  }
  
  private static int intValue(boolean value) {
    return value ? TRUE_VALUE : FALSE_VALUE;
  }
  
  private static boolean booleanValue(int value) {
    return value == TRUE_VALUE;
  }
  
  public boolean get() {
    return booleanValue(value.get());
  }
  
  public boolean compareAndSet(boolean expect, boolean update) {
    return value.compareAndSet(intValue(expect), intValue(update));
  }
  
  public boolean weakCompareAndSet(boolean expect, boolean update) {
    return value.weakCompareAndSet(intValue(expect), intValue(update));
  }
  
  public void set(boolean newValue) {
    value.set(intValue(newValue));
  }
  
  public void lazySet(boolean newValue) {
    value.lazySet(intValue(newValue));
  }
  
  public boolean getAndSet(boolean newValue) {
    int intResult = value.getAndSet(intValue(newValue));
    
    return booleanValue(intResult);
  }
  
  @Override
  public String toString() {
    return Boolean.toString(get());
  }
}
