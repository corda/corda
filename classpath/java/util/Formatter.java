/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Formatter {
  private final Locale locale;

  public Formatter(Locale locale) {
    this.locale = locale;
  }

  public Formatter format(String format, Object ... args) {
    throw new UnsupportedOperationException();
  }
}
