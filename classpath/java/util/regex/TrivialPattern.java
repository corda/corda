/* Copyright (c) 2008-2015, Avian Contributors

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
public class TrivialPattern extends Pattern {

  private final String unescaped;

  TrivialPattern(String pattern, String unescaped, int flags) {
    super(pattern, flags);
    this.unescaped = unescaped;
  }

  public Matcher matcher(CharSequence input) {
    return new TrivialMatcher(unescaped, input);
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

    List<CharSequence> list = new LinkedList<CharSequence>();
    int index = 0;
    int trailing = 0;
    int patternLength = unescaped.length();
    while (index < input.length() && list.size() < limit - 1) {
      int i;
      if (patternLength == 0) {
        if (list.size() == 0) {
          i = 0;
        } else {
          i = index + 1;
        }
      } else {
        i = indexOf(input, unescaped, index);
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
