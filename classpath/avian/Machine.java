/* Copyright (c) 2009-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import sun.misc.Unsafe;

public abstract class Machine {

  private static final Unsafe unsafe = Unsafe.getUnsafe();

  public static native void dumpHeap(String outputFile);

  public static Unsafe getUnsafe() {
    return unsafe;
  }

}
