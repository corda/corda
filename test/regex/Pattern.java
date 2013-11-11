/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package regex;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a work in progress.
 * 
 * @author zsombor and others
 * 
 */
public abstract class Pattern implements PikeVMOpcodes {

  public static final int UNIX_LINES       = 1;
  public static final int CASE_INSENSITIVE = 2;
  public static final int COMMENTS         = 4;
  public static final int MULTILINE        = 8;
  public static final int LITERAL          = 16;
  public static final int DOTALL           = 32;
  public static final int UNICODE_CASE     = 64;
  public static final int CANON_EQ         = 128;

  private final int patternFlags;
  private final String pattern;

  protected Pattern(String pattern, int flags) {
    this.pattern = pattern;
    this.patternFlags = flags;
  }

  public static Pattern compile(String regex) {
    return compile(regex, 0);
  }

  public static Pattern compile(String regex, int flags) {
    try {
      return new TrivialPattern(regex, flags);
    } catch (UnsupportedOperationException handledBelow) { }
    if (flags != 0) {
      throw new UnsupportedOperationException("TODO");
    }
    if ("a(bb)?a".equals(regex)) {
      return new RegexPattern(regex, flags, new PikeVM(new int[] {
        SAVE_OFFSET, 0,
        'a',
        SPLIT, 11,
        SAVE_OFFSET, 2,
        'b',
        'b',
        SAVE_OFFSET, 3,
        /* 11 */ 'a',
        SAVE_OFFSET, 1
      }, 1));
    } else if ("a(a*?)(a?)(a??)(a+)(a*)a".equals(regex)) {
      return new RegexPattern(regex, flags, new PikeVM(new int[] {
        SAVE_OFFSET, 0,
        'a',
        SAVE_OFFSET, 2,
        SPLIT_JMP, 10,
        /* 7 */ 'a',
        SPLIT, 7,
        /* 10 */ SAVE_OFFSET, 3,
        SAVE_OFFSET, 4,
        SPLIT, 17,
        'a',
        /* 17 */ SAVE_OFFSET, 5,
        SAVE_OFFSET, 6,
        SPLIT_JMP, 24,
        'a',
        /* 24 */ SAVE_OFFSET, 7,
        SAVE_OFFSET, 8,
        /* 28 */ 'a',
        SPLIT_JMP, 28,
        SAVE_OFFSET, 9,
        SAVE_OFFSET, 10,
        SPLIT, 40,
        /* 37 */ 'a',
        SPLIT_JMP, 37,
        /* 40 */ SAVE_OFFSET, 11,
        'a',
        SAVE_OFFSET, 1
      }, 5));
    }
    throw new UnsupportedOperationException("Cannot handle regex " + regex);
  }

  public int flags() {
    return patternFlags;
  }

  public abstract Matcher matcher(CharSequence input);

  public static boolean matches(String regex, CharSequence input) {
    return Pattern.compile(regex).matcher(input).matches();
  }

  public String pattern() {
    return pattern;
  }

  public String[] split(CharSequence input) {
    return split(input, 0);
  }

  public String[] split(CharSequence input, int limit) {
    if (limit <= 0) {
      limit = Integer.MAX_VALUE;
    }
    Matcher matcher = matcher(input);
    List<String> result = new ArrayList<String>();
    int offset = 0;
    for (;;) {
      if (result.size() >= limit || !matcher.find()) {
        break;
      }
      result.add(input.subSequence(offset, matcher.start()).toString());
      offset = matcher.end();
    }
    if (offset == 0 || offset < input.length()) {
      result.add(input.subSequence(offset, input.length()).toString());
    }
    return result.toArray(new String[result.size()]);
  }
}
