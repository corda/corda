/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public final class Objects {
  private Objects() {
    throw new AssertionError("Instantiating java.long.Objetcs is not allowed!");
  }

  public static <T> int compare(final T x, final T y, final Comparator<? super T> comparator) {
    if (x == y)
      return 0;
    else
      return comparator.compare(x, y);
  }

  public static boolean deepEquals(final Object x, final Object y) {
    if (x == y)
      return true;
    if (x == null || y == null)
      return false;
    if (x.getClass().isArray()) {
      if(x instanceof Object[] && y instanceof Object[])
        return Arrays.deepEquals((Object[])x, (Object[])y);
      if(x instanceof byte[] && y instanceof byte[])
        return Arrays.equals((byte[]) x, (byte[]) y);
      if(x instanceof int[] && y instanceof int[])
        return Arrays.equals((int[]) x, (int[]) y);
      if(x instanceof long[] && y instanceof long[])
        return Arrays.equals((long[]) x, (long[]) y);
      if(x instanceof short[] && y instanceof short[])
        return Arrays.equals((short[]) x, (short[]) y);
      if(x instanceof char[] && y instanceof char[])
        return Arrays.equals((char[]) x, (char[]) y);
      if(x instanceof float[] && y instanceof float[])
        return Arrays.equals((float[]) x, (float[]) y);
      if(x instanceof double[] && y instanceof double[])
        return Arrays.equals((double[]) x, (double[]) y);
    }
    return x.equals(y);
  }

  public static boolean equals(final Object x, final Object y) {
    if (x == y)
      return true;
    if (x != null)
      return x.equals(y);
    return false;
  }

  public static int hash(final Object... values) {
    return Arrays.hashCode(values);
  }

  public static int hashCode(final Object value) {
    if (value == null)
      return 0;
    return value.hashCode();
  }

  public static <T> T requireNonNull(final T value) {
    if (value == null)
      throw new NullPointerException();
    else
      return value;
  }

  public static <T> T requireNonNull(final T value, String message) {
    if (value == null)
      throw new NullPointerException(message);
    else
      return value;
  }

  public static String toString(final Object value) {
    return String.valueOf(value);
  }

  public static String toString(final Object value, final String defaultValue) {
    if (value == null)
      return defaultValue;
    return value.toString();
  }
}
