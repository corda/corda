/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

public final class Integer extends Number implements Comparable<Integer> {
  public static final Class TYPE = avian.Classes.forCanonicalName("I");

  public static final int MIN_VALUE = 0x80000000;
  public static final int MAX_VALUE = 0x7FFFFFFF;

  private final int value;

  public Integer(int value) {
    this.value = value;
  }

  public Integer(String s) {
    this.value = parseInt(s);
  }

  public static Integer valueOf(int value) {
    return new Integer(value);
  }

  public static Integer valueOf(String value) {
    return valueOf(parseInt(value));
  }

  public boolean equals(Object o) {
    return o instanceof Integer && ((Integer) o).value == value;
  }

  public int hashCode() {
    return value;
  }

  public int compareTo(Integer other) {
    return value - other.value;
  }

  public String toString() {
    return toString(value);
  }

  public static String toString(int v, int radix) {
    return Long.toString(v, radix);
  }

  public static String toString(int v) {
    return toString(v, 10);
  }

  public static String toHexString(int v) {
    return Long.toString(((long) v) & 0xFFFFFFFFL, 16);
  }

  public static String toOctalString(int v) {
    return Long.toString(((long) v) & 0xFFFFFFFFL, 8);
  }

  public static String toBinaryString(int v) {
    return Long.toString(((long) v) & 0xFFFFFFFFL, 2);
  }

  public byte byteValue() {
    return (byte) value;
  }

  public short shortValue() {
    return (short) value;
  }

  public int intValue() {
    return value;
  }

  public long longValue() {
    return value;
  }

  public float floatValue() {
    return (float) value;
  }

  public double doubleValue() {
    return (double) value;
  }

  public static int signum(int v) {
    if (v == 0)     return  0;
    else if (v > 0) return  1;
    else            return -1;
  }

  // See http://graphics.stanford.edu/~seander/bithacks.html#CountBitsSetParallel
  public static int bitCount(int v) {
    v = v - ((v >> 1) & 0x55555555);
    v = (v & 0x33333333) + ((v >> 2) & 0x33333333);
    return ((v + (v >> 4) & 0xF0F0F0F) * 0x1010101) >> 24;
  }

  public static int reverseBytes(int v) {
    int byte3 =  v >>> 24;
    int byte2 = (v >>> 8) & 0xFF00;
    int byte1 = (v <<  8) & 0xFF00;
    int byte0 =  v << 24;
    return (byte0 | byte1 | byte2 | byte3);
  }

  public static int parseInt(String s) {
    return parseInt(s, 10);
  }

  public static int parseInt(String s, int radix) {
    return (int) Long.parseLong(s, radix);
  }

  public static Integer decode(String string) {
    if (string.startsWith("-")) {
      if (string.startsWith("-0") || string.startsWith("-#")) {
        return new Integer(-decode(string.substring(1)));
      }
    } else if (string.startsWith("0")) {
      char c = string.length() < 2 ? (char)-1 : string.charAt(1);
      if (c == 'x' || c == 'X') {
        return new Integer(parseInt(string.substring(2), 0x10));
      }
      return new Integer(parseInt(string, 010));
    } else if (string.startsWith("#")) {
      return new Integer(parseInt(string.substring(1), 0x10));
    }
    return new Integer(parseInt(string, 10));
  }

  public static int numberOfLeadingZeros(int i) {
    // See nlz5 at http://www.hackersdelight.org/hdcodetxt/nlz.c.txt
    i |= i >> 1;
    i |= i >> 2;
    i |= i >> 4;
    i |= i >> 8;
    i |= i >> 16;
    return bitCount(~i);
  }
}
