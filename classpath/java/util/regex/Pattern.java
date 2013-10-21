/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

/**
 * This is a work in progress.
 * 
 * @author zsombor and others
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

  private final int patternFlags;
  private final String pattern;

  protected Pattern(String pattern, int flags) {
    this.pattern = trivial(pattern);
    this.patternFlags = flags;
  }

  private static String trivial(String pattern) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < pattern.length(); ++i) {
      char c = pattern.charAt(i);
      switch (c) {
      case '\\':
        if (++i == pattern.length()) {
          break;
        }
        c = pattern.charAt(i);
        if (c == '0') {
          int len = digits(pattern, ++i, 3, 8);
          if (len == 3 && pattern.charAt(i) > '3') {
            --len;
          }
          c = (char)Integer.parseInt(pattern.substring(i, i + len), 8);
          i += len - 1;
        } else if (c == 'x' || c == 'u') {
          int len = digits(pattern, ++i, 4, 16);
          c = (char)Integer.parseInt(pattern.substring(i, i + len), 16);
          i += len - 1;
        } else {
          c = unescape(pattern.charAt(i));
        }
        if (c != -1) {
          break;
        }
        // fallthru
      case '.':
      case '*':
      case '+':
      case '?':
      case '|':
      case '[':
      case ']':
      case '{':
      case '}':
      case '(':
      case ')':
      case '^':
      case '$':
        throw new UnsupportedOperationException
          ("only trivial regular expressions are supported so far (" + pattern + ")");
      }
      buffer.append(c);
    }
    return buffer.toString();
  }

  private static int digits(String s, int offset, int maxLength, int base) {
    for (int i = 0; ; ++i) {
      if (i == maxLength || offset + i >= s.length()) {
        return i;
      }
      int value = s.charAt(offset + i) - '0';
      if (value < 0) {
        return i;
      }
      if (base > 10 && value >= 10) {
        value += 10 - (value >= 'a' - '0' ? 'a' - '0' : 'A' - '0');
      }
      if (value >= base) {
        return i;
      }
    }
  }

  private static char unescape(char c) {
    switch (c) {
    case '\\':
       return c;
    case 'a':
       return 0x0007;
    case 'e':
       return 0x001B;
    case 'f':
       return 0x000C;
    case 'n':
       return 0x000A;
    case 'r':
       return 0x000D;
    case 't':
       return 0x0009;
    }
    return (char)-1;
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
    return new Matcher(this, input);
  }

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
    boolean strip;
    if (limit < 0) {
      strip = false;
      limit = Integer.MAX_VALUE;
    } else if (limit == 0) {
      strip = true;
      limit = Integer.MAX_VALUE;
    } else {
      strip = false;
    }

    List<CharSequence> list = new LinkedList();
    int index = 0;
    int trailing = 0;
    int patternLength = pattern.length();
    while (index < input.length() && list.size() < limit - 1) {
      int i;
      if (patternLength == 0) {
        if (list.size() == 0) {
          i = 0;
        } else {
          i = index + 1;
        }
      } else {
        i = indexOf(input, pattern, index);
      }

      if (i >= 0) {
        if (patternLength != 0 && i == index) {
          ++ trailing;
        } else {
          trailing = 0;
        }

        list.add(input.subSequence(index, i));
        index = i + patternLength;
      } else {
        break;
      }
    }

    if (strip && index > 0 && index == input.length()) {
      ++ trailing;
    } else {
      trailing = 0;
    }
    list.add(input.subSequence(index, input.length()));

    String[] result = new String[list.size() - trailing];
    int i = 0;
    for (Iterator<CharSequence> it = list.iterator();
         it.hasNext() && i < result.length; ++ i)
    {
      result[i] = it.next().toString();
    }
    return result;
  }

  static int indexOf(CharSequence haystack, CharSequence needle, int start) {
    if (needle.length() == 0) return start;

    for (int i = start; i < haystack.length() - needle.length() + 1; ++i) {
      int j = 0;
      for (; j < needle.length(); ++j) {
        if (haystack.charAt(i + j) != needle.charAt(j)) {
          break;
        }
      }
      if (j == needle.length()) {
        return i;
      }
    }

    return -1;
  }
}
