package java.lang;

public final class Long extends Number {
  public static final Class TYPE = Class.forCanonicalName("J");

  private final long value;

  public Long(long value) {
    this.value = value;
  }

  public static Long valueOf(long value) {
    return new Long(value);
  }

  public boolean equals(Object o) {
    return o instanceof Long && ((Long) o).value == value;
  }

  public int hashCode() {
    return (int) ((value >> 32) ^ (value & 0xFF));
  }

  public String toString() {
    return String.valueOf(value);
  }

  public static String toString(long v, int radix) {
    if (radix < 1 || radix > 36) {
      throw new IllegalArgumentException("radix " + radix + " not in [1,36]");
    }

    if (v == 0) {
      return "0";
    }

    boolean negative = v < 0;
    if (negative) v = -v;

    int size = (negative ? 1 : 0);
    for (long n = v; n > 0; n /= radix) ++size;

    char[] array = new char[size];

    int i = array.length - 1;
    for (long n = v; n > 0; n /= radix) {
      long digit = n % radix;
      if (digit >= 0 && digit <= 9) {
        array[i] = (char) ('0' + digit);
      } else {
        array[i] = (char) ('a' + (digit - 10));
      }
      --i;
    }

    if (negative) {
      array[i] = '-';
    }

    return new String(array);
  }

  public static String toString(long v) {
    return toString(v, 10);
  }

  public byte byteValue() {
    return (byte) value;
  }

  public short shortValue() {
    return (short) value;
  }

  public int intValue() {
    return (int) value;
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

  private static long pow(long a, long b) {
    long c = 1;
    for (int i = 0; i < b; ++i) c *= a;
    return c;
  }

  public static long parseLong(String s) {
    return parseLong(s, 10);
  } 

  public static long parseLong(String s, int radix) {
    int i = 0;
    long number = 0;
    boolean negative = s.startsWith("-");
    if (negative) {
      i = 1;
    }

    for (; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (((c >= '0') && (c <= '9')) ||
	  ((c >= 'a') && (c <= 'z'))) {
	long digit = ((c >= '0' && c <= '9') ? (c - '0') : (c - 'a' + 10));
        if (digit < radix) {
          number += digit * pow(radix, (s.length() - i - 1));
          continue;
        }
      }
      throw new NumberFormatException("invalid character " + c + " code " +
                                      (int) c);
    }

    if (negative) {
      number = -number;
    }

    return number;
  }
}
