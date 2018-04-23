/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.text;

public class ParsePosition {
  private int index, errorIndex = -1;

  public ParsePosition(int index) {
    this.index = index;
  }

  public int getErrorIndex() {
    return errorIndex;
  }

  public int getIndex() {
    return index;
  }

  public void setErrorIndex(int i) {
    errorIndex = i;
  }

  public void setIndex(int i) {
    index = i;
  }

  public String toString() {
    return "index: " + index + "(error index: " + errorIndex + ")";
  }
}
