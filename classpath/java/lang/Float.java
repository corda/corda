package java.lang;

public final class Float extends Number {
  public static final Class TYPE = Class.forCanonicalName("F");

  private final float value;

  public Float(float value) {
    this.value = value;
  }

  public static Float valueOf(float value) {
    return new Float(value);
  }

  public boolean equals(Object o) {
    return o instanceof Float && ((Float) o).value == value;
  }

  public int hashCode() {
    return floatToRawIntBits(value);
  }

  public String toString() {
    return toString(value);
  }

  public static String toString(float v) {
    return "todo";
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
    return (long) value;
  }

  public float floatValue() {
    return value;
  }

  public double doubleValue() {
    return (double) value;
  }

  public static native int floatToRawIntBits(float value);

  public static native float intBitsToFloat(int bits);
}
