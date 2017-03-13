/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.text;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class SimpleDateFormat {
  private String pattern;

  public SimpleDateFormat(String pattern) {
    this.pattern = pattern;
    if (! "yyyy-MM-dd'T'HH:mm:ss".equals(pattern)) {
      throw new UnsupportedOperationException("Unsupported pattern: " + pattern);
    }
  }

  public void setTimeZone(TimeZone tz) {
    if(!tz.getDisplayName().equals("GMT")) {
      throw new UnsupportedOperationException();
    }
  }

  public StringBuffer format(Date date, StringBuffer buffer, FieldPosition position) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    pad(buffer, calendar.get(Calendar.YEAR), 4);
    buffer.append('-');
    pad(buffer, calendar.get(Calendar.MONTH) + 1, 2);
    buffer.append('-');
    pad(buffer, calendar.get(Calendar.DAY_OF_MONTH), 2);
    buffer.append("T");
    pad(buffer, calendar.get(Calendar.HOUR_OF_DAY), 2);
    buffer.append(':');
    pad(buffer, calendar.get(Calendar.MINUTE), 2);
    buffer.append(':');
    pad(buffer, calendar.get(Calendar.SECOND), 2);
    return buffer;
  }

  public Date parse(String text) {
    return parse(text, new ParsePosition(0));
  }

  public Date parse(String text, ParsePosition position) {
    int index = position.getIndex();
    try {
      Calendar calendar = Calendar.getInstance();
      index = parseField(text, index, 4, calendar, Calendar.YEAR, 0);
      index = expectPrefix(text, index, "-");
      index = parseField(text, index, 2, calendar, Calendar.MONTH, -1);
      index = expectPrefix(text, index, "-");
      index = parseField(text, index, 2, calendar, Calendar.DAY_OF_MONTH, 0);
      index = expectPrefix(text, index, "T");
      index = parseField(text, index, 2, calendar, Calendar.HOUR_OF_DAY, 0);
      index = expectPrefix(text, index, ":");
      index = parseField(text, index, 2, calendar, Calendar.MINUTE, 0);
      index = expectPrefix(text, index, ":");
      index = parseField(text, index, 2, calendar, Calendar.SECOND, 0);
      position.setIndex(index);
      return calendar.getTime();
    } catch (ParseException e) {
      position.setErrorIndex(index);
      return null;
    }
  }

  private static void pad(StringBuffer buffer, int value, int digits) {
    int i = value == 0 ? 1 : value;
    while (i > 0) {
      i /= 10;
      --digits;
    }
    while (digits-- > 0) {
      buffer.append('0');
    }
    buffer.append(value);
  }

  private static int parseField(String text, int offset, int length, Calendar calendar, int field, int adjustment) throws ParseException {
    if (text.length() < offset + length) throw new ParseException("Short date: " + text, offset);
    try {
      int value = Integer.parseInt(text.substring(offset, offset + length), 10);
      calendar.set(field, value + adjustment);
    } catch (NumberFormatException e) {
      throw new ParseException("Not a number: " + text, offset);
    }
    return offset + length;
  }

  private static int expectPrefix(String text, int offset, String prefix) throws ParseException {
    if (text.length() <= offset) throw new ParseException("Short date: " + text, offset);
    if (! text.substring(offset).startsWith(prefix)) throw new ParseException("Parse error: " + text, offset);
    return offset + prefix.length();
  }
}
