/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

public class Proxy {
  private static int nextNumber;

  protected InvocationHandler h;

  public static Class getProxyClass(ClassLoader loader,
                                    Class ... interfaces)
  {
    for (Class c: interfaces) {
      if (! c.isInterface()) {
        throw new IllegalArgumentException();
      }
    }

    int number;
    synchronized (Proxy.class) {
      number = nextNumber++;
    }

    return makeClass
      (loader, interfaces, ("Proxy-" + number + "\0").getBytes());
  }

  private static native Class makeClass(ClassLoader loader,
                                        Class[] interfaces,
                                        byte[] name);

  public static Object newProxyInstance(ClassLoader loader,
                                        Class[] interfaces,
                                        InvocationHandler handler)
  {
    try {
      return Proxy.getProxyClass(loader, interfaces)
        .getConstructor(new Class[] { InvocationHandler.class })
        .newInstance(new Object[] { handler });
    } catch (Exception e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }
  }
}
