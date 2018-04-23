/* Copyright (c) 2008-2016, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.invoke;

import avian.Classes;
import avian.SystemClassLoader;

public class MethodHandle {
  static final int REF_invokeVirtual = 5;
  static final int REF_invokeStatic = 6;
  static final int REF_invokeSpecial = 7;
  static final int REF_newInvokeSpecial = 8;
  static final int REF_invokeInterface = 9;

  final int kind;
  private final ClassLoader loader;
  final avian.VMMethod method;
  private volatile MethodType type;

  MethodHandle(int kind, ClassLoader loader, avian.VMMethod method) {
    this.kind = kind;
    this.loader = loader;
    this.method = method;
  }

  MethodHandle(String class_,
               String name,
               String spec,
               int kind)
  {
    this.kind = kind;
    this.loader = SystemClassLoader.appLoader();
    try {
      this.method = Classes.findMethod(this.loader, class_, name, spec);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (method.class_ != null) {
      sb.append(Classes.makeString(method.class_.name, 0,
                                   method.class_.name.length - 1));
      sb.append(".");
    }
    sb.append(Classes.makeString(method.name, 0,
                                 method.name.length - 1));
    sb.append(Classes.makeString(method.spec, 0,
                                 method.spec.length - 1));
    return sb.toString();
  }

  public MethodType type() {
    if (type == null) {
      type = new MethodType(loader, method.spec);
    }
    return type;
  }
}
