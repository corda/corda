/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.text;

import java.util.Locale;

/**
 * A minimalist Java message formatter.
 * The string is a sequence of letters, copied verbatim unless the letter
 * is "{" or "'".  If the letter is a "{", this begins a parameter, which
 * is a base-10 number.  {0} will be replaced by the first argument to
 * format, {1} is the second argument, and so on.
 * If the letter is a single tick ("'"), then all characters
 * until the mathcing tick are outputted verbatim(this is useful for escaping
 * the { character).
 * <h3>Examples</h3>
 * <table>
 * <tr><th>format</th><th>Args</th><th>Result</th></tr>
 * <tr><td>There are {0} grapes</td><td>six</td><td>There are six grapes</td></tr>
 * <tr><td>{2} + {1} = {0}</td><td>5 2 3</td><td>3 + 2 = 5</td></tr>
 * <tr><td>{0} and {0} and {0}</td><td>again</td><td>again and again and again</td></tr>
 * <tr><td>Joe''s age is {0}, not '{0}'</td><td>30</td><td>Joe's age is 30, not {0}</td></tr>
 * </table>
 */
public class MessageFormat extends Format {
  private String pattern;
  private final Locale locale;

  public MessageFormat(String pattern, Locale locale) {
    this.pattern = pattern;
    this.locale = locale;
  }

  public MessageFormat(String pattern) {
    this(pattern, Locale.getDefault());
  }

  public StringBuffer format(Object args[], StringBuffer target, FieldPosition pos) {
    int i=0;
    int len=pattern.length();

    while (i < len) {
      char ch = pattern.charAt(i);
      if (ch == '{') {
        // Param should be a number
        int num=0;
        while (i < (len-1)) {
          i++;
          ch = pattern.charAt(i);
          if ((ch >= '0') && (ch <= '9')) {
            num = num * 10 + (ch - '0');
          } else if (ch == '}') {
            target.append((args[num] == null) ? "null" : args[num].toString());
            break;
          } else {
            throw new IllegalArgumentException("Character within {} isn't digit: " + ch);
          }
        }
      } else if (ch == '\'') {
        // Char is a literal string
        i++;
        ch = pattern.charAt(i);
        if (ch == '\'') {
          target.append('\'');
        } else {
          while (ch != '\'') {
            target.append(ch);
            i++;
            ch = pattern.charAt(i);
          }
        }
      } else {
        target.append(ch);
      }
      i++;
    }
    return target;
  }

  public static String format(String message, Object... args) {
    return new MessageFormat(message).format(args, new StringBuffer(), new FieldPosition(0)).toString();
  }

  public StringBuffer format(Object args, StringBuffer target, FieldPosition p) {
    return format((Object[]) args, target, p);
  }

  public void applyPattern(String pattern) {
    this.pattern = pattern;
  }

  public String toPattern() {
    return pattern;
  }
}
