/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Random {
  public int nextInt(int n) {
    return nextInt() % n;
  }

  public int nextInt() {
    return (int)(Math.random()*Integer.MAX_VALUE);
  }

  public double nextDouble() {
    return Math.random();
  }
}
