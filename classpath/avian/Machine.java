/* Copyright (c) 2008-2014, Avian Contributors

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

}
