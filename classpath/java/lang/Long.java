package java.lang;

public final class Long extends Number {
  public static final Class TYPE = Class.forName("J");

  private final long value;

  public Long(long value) {
    this.value = value;
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

  public static long parseLong(String s, int radix) {    
    long number = 0;

    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (((c >= '0') && (c <= '9')) ||
	  ((c >= 'a') && (c <= 'z'))) {
	long digit = ((c >= '0' && c <= '9') ? (c - '0') : (c - 'a' + 10));
	number += digit * pow(radix, (s.length() - i - 1));
      } else {
	throw new NumberFormatException("Invalid character " + c + " code " +
					(int) c);
      }
    }

    return number;
  }
}
