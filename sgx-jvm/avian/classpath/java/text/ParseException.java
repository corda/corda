/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.text;

public class ParseException extends Exception {
  private int errorOffset;

  public ParseException(String message, int errorOffset) {
    super(message);
    this.errorOffset = errorOffset;
  }

  public int getErrorOffset() {
    return errorOffset;
  }
}
