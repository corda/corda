/* Copyright (c) 2008-2016, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.invoke;

public class MethodHandles {
  public static class Lookup {
    final avian.VMClass class_;
    private final int modes;

    private Lookup(avian.VMClass class_, int modes) {
      this.class_ = class_;
      this.modes = modes;
    }

    public String toString() {
      return "lookup[" + avian.SystemClassLoader.getClass(class_) + ", "
        + modes + "]";
    }
  }
}
