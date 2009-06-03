/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

public class ThreadGroup implements Thread.UncaughtExceptionHandler {
  private final ThreadGroup parent;
  private final String name;

  public ThreadGroup(ThreadGroup parent, String name) {
    this.parent = parent;
    this.name = name;
  }

  public void uncaughtException(Thread t, Throwable e) {
    if (parent != null) {
      parent.uncaughtException(t, e);
    } else {
      Thread.UncaughtExceptionHandler deh
        = Thread.getDefaultUncaughtExceptionHandler();
      if (deh != null) {
        deh.uncaughtException(t, e);
      } else if (! (e instanceof ThreadDeath)) {
        e.printStackTrace();
      }
    }
  }
}
