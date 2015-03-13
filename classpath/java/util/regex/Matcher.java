/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

/**
 * This is a work in progress.
 * 
 * @author zsombor and others
 */
public abstract class Matcher {
  protected CharSequence input;
  protected int start;
  protected int end;

  public Matcher(CharSequence input) {
    reset(input);
  }

  public abstract boolean matches();

  public boolean find() {
    return find(end);
  }

  public abstract boolean find(int start);

  public Matcher reset() {
    return reset(input);
  }

  public Matcher reset(CharSequence input) {
    this.input = input;
    start = 0;
    end = 0;
    return this;
  }

  public String replaceAll(String replacement) {
    return replace(replacement, Integer.MAX_VALUE);
  }

  public String replaceFirst(String replacement) {
    return replace(replacement, 1);
  }

  protected String replace(String replacement, int limit) {
    reset();

    StringBuilder sb = null;
    int index = 0;
    int count = 0;
    while (count < limit && index < input.length()) {
      if (find(index)) {
        if (sb == null) {
          sb = new StringBuilder();
        }
        if (start > index) {
          sb.append(input.subSequence(index, start));
        }
        sb.append(replacement);
        index = end;
        ++ count;
      } else if (index == 0) {
        return input.toString();
      } else {
        break;
      }
    }
    if (index < input.length()) {
      sb.append(input.subSequence(index, input.length()));
    }
    return sb.toString();
  }

  public int start() {
    return start;
  }

  public int end() {
    return end;
  }

  public String group() {
    return input.subSequence(start, end).toString();
  }

  public int start(int group) {
    if (group == 0) {
      return start();
    }
    throw new UnsupportedOperationException();
  }

  public int end(int group) {
    if (group == 0) {
      return end();
    }
    throw new UnsupportedOperationException();
  }

  public String group(int group) {
    if (group == 0) {
      return group();
    }
    throw new UnsupportedOperationException();
  }

  public int groupCount() {
    return 0;
  }
}
