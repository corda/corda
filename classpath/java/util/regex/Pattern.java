/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

/**
 * This implementation is a skeleton, useful only for compilation. At runtime it
 * is need to be replaced by a working implementation, for example one from the
 * Apache Harmony project.
 * 
 * @author zsombor
 * 
 */
public class Pattern {

  public static final int UNIX_LINES       = 1;
  public static final int CASE_INSENSITIVE = 2;
  public static final int COMMENTS         = 4;
  public static final int MULTILINE        = 8;
  public static final int LITERAL          = 16;
  public static final int DOTALL           = 32;
  public static final int UNICODE_CASE     = 64;
  public static final int CANON_EQ         = 128;

  private int             patternFlags;
  private String          pattern;

  protected Pattern(String pattern, int flags) {
    this.pattern = pattern;
    this.patternFlags = flags;
  }

  public static Pattern compile(String regex) {
    return new Pattern(regex, 0);
  }

  public static Pattern compile(String regex, int flags) {
    return new Pattern(regex, flags);
  }

  public int flags() {
    return patternFlags;
  }

  public Matcher matcher(CharSequence input) {
    throw new UnsupportedOperationException();
  }

  public static boolean matches(String regex, CharSequence input) {
    return Pattern.compile(regex).matcher(input).matches();
  }

  public String pattern() {
    return pattern;
  }

  public static String quote(String s) {
    throw new UnsupportedOperationException();
  }

  public String[] split(CharSequence input) {
    throw new UnsupportedOperationException();
  }

  public String[] split(CharSequence input, int limit) {
    throw new UnsupportedOperationException();
  }
}
