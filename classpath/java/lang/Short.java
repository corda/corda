package java.lang;

public final class Short extends Number implements Comparable<Short> {
  public static final Class TYPE = Class.forCanonicalName("S");
  public static final short MAX_VALUE = 32767;

  private final short value;

  public Short(short value) {
    this.value = value;
  }

  public static Short valueOf(short value) {
    return new Short(value);
  }

  public int compareTo(Short o) {
    return value - o.value;
  }

  public boolean equals(Object o) {
    return o instanceof Short && ((Short) o).value == value;
  }

  public int hashCode() {
    return value;
  }

  public String toString() {
    return toString(value);
  }

  public static String toString(short v, int radix) {
    return Long.toString(v, radix);
  }

  public static String toString(short v) {
    return toString(v, 10);
  }

  public byte byteValue() {
    return (byte) value;
  }

  public short shortValue() {
    return value;
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
}
