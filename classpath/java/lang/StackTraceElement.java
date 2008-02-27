/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

public class StackTraceElement {
  private static int NativeLine = -1;

  private String class_;
  private String method;
  private String file;
  private int line;

  private StackTraceElement(String class_, String method, String file,
                            int line)
  {
    this.class_ = class_;
    this.method = method;
    this.file = file;
    this.line = line;
  }

  public String getClassName() {
    return class_;
  }

  public String getMethodName() {
    return method;
  }

  public String getFileName() {
    return file;
  }

  public int getLineNumber() {
    return line;
  }

  public boolean isNativeMethod() {
    return line == NativeLine;
  }
}
