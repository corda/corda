/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent.locks;

import sun.misc.Unsafe;

public class LockSupport {
  private LockSupport() {
    // can't construct
  }
  
  private static final Unsafe unsafe;
  private static final long parkBlockerOffset;
  
  static {
    unsafe = Unsafe.getUnsafe();
    try {
      parkBlockerOffset = unsafe.objectFieldOffset(Thread.class.getDeclaredField("parkBlocker"));
    } catch (Exception ex) { 
      throw new Error(ex);
    }
  }
  
  public static void unpark(Thread thread) {
    if (thread != null) {
      unsafe.unpark(thread);
    }
  }
  
  public static void park(Object blocker) {
    doParkNanos(blocker, 0L);
  }
  
  public static void parkNanos(Object blocker, long nanos) {
    if (nanos <= 0) {
      return;
    }
    
    doParkNanos(blocker, nanos);
  }
  
  private static void doParkNanos(Object blocker, long nanos) {
    Thread t = Thread.currentThread();
    unsafe.putObject(t, parkBlockerOffset, blocker);
    unsafe.park(false, nanos);
    unsafe.putObject(t, parkBlockerOffset, null);
  }
  
  public static void parkUntil(Object blocker, long deadline) {
    Thread t = Thread.currentThread();
    unsafe.putObject(t, parkBlockerOffset, blocker);
    unsafe.park(true, deadline);
    unsafe.putObject(t, parkBlockerOffset, null);
  }
  
  public static Object getBlocker(Thread t) {
    if (t == null) {
      throw new NullPointerException();
    }
    
    return unsafe.getObjectVolatile(t, parkBlockerOffset);
  }
  
  public static void park() {
    unsafe.park(false, 0L);
  }
  
  public static void parkNanos(long nanos) {
    if (nanos > 0) {
      unsafe.park(false, nanos);
    }
  }
  
  public static void parkUntil(long deadline) {
    unsafe.park(true, deadline);
  }
}
