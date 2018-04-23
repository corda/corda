/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

public abstract class Machine {

  private static final Unsafe unsafe;

  static {
    Unsafe u;
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      u = (Unsafe)f.get(null);
    } catch (Exception e) {
      u = null;
    }
    unsafe = u;
  }

  public static native void dumpHeap(String outputFile);

  public static Unsafe getUnsafe() {
    return unsafe;
  }

  /**
   * Short version: Don't use this function -- it's evil.
   *
   * Long version: This is kind of a poor man's, cross-platform
   * version of Microsoft's Structured Exception Handling.  The idea
   * is that you can call a native function with the specified
   * argument such that any OS signals raised (e.g. SIGSEGV, SIGBUS,
   * SIGFPE, EXC_ACCESS_VIOLATION, etc.) prior to the function
   * returning are converted into the appropriate Java exception
   * (e.g. NullPointerException, ArithmeticException, etc.) and thrown
   * from this method.  This may be useful in very specific
   * circumstances, e.g. to work around a bug in a library that would
   * otherwise crash your app.  On the other hand, you'd be much
   * better off just fixing the library if possible.
   *
   * Caveats: The specified function should return quickly without
   * blocking, since it will block critical VM features such as
   * garbage collection.  The implementation is equivalent to using
   * setjmp/longjmp to achieve a non-local return from the signal
   * handler, meaning C++ destructors and other cleanup code might not
   * be run if a signal is raised.  It might melt your keyboard and
   * burn your fingertips.  Caveat lector.
   *
   * @param function a function pointer of type int64_t (*)(int64_t)
   * @param argument the argument to pass to the function
   * @return the return value of the function
   */
  public static native long tryNative(long function, long argument);

}
