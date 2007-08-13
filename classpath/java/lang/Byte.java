package java.lang;

public final class Byte extends Number {
  public static final Class TYPE = Class.forCanonicalName("B");

  private final byte value;

  public Byte(byte value) {
    this.value = value;
  }

  public static Byte valueOf(byte value) {
    return new Byte(value);
  }

  public boolean equals(Object o) {
    return o instanceof Byte && ((Byte) o).value == value;
  }

  public int hashCode() {
    return value;
  }

  public String toString() {
    return toString(value);
  }

  public static String toString(byte v, int radix) {
    return Long.toString(v, radix);
  }

  public static String toString(byte v) {
    return toString(v, 10);
  }

  public byte byteValue() {
    return value;
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
