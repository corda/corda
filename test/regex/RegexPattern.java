/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package regex;

/**
 * A minimal implementation of a regular expression engine.
 * 
 * @author Johannes Schindelin
 */
public class RegexPattern extends Pattern {
  private PikeVM vm;

  public RegexMatcher matcher(CharSequence string) {
    return new RegexMatcher(vm, string);
  }

  RegexPattern(String regex, int flags, PikeVM vm) {
    super(regex, flags);
    this.vm = vm;
  }
}
