package java.lang;

public final class Integer extends Number {
  public static final Class TYPE = Class.forCanonicalName("I");

  public static final int MIN_VALUE = 0x80000000;
  public static final int MAX_VALUE = 0x7FFFFFFF;

  private final int value;

  public Integer(int value) {
    this.value = value;
  }

  public boolean equals(Object o) {
    return o instanceof Integer && ((Integer) o).value == value;
  }

  public int hashCode() {
    return value;
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

  public static int parseInt(String s) {
    return parseInt(s, 10);
  }

  public static int parseInt(String s, int radix) {
    return (int) Long.parseLong(s, radix);
  }
}
