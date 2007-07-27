package java.lang;

public final class Float extends Number {
  public static final Class TYPE = Class.forName("F");

  private final float value;

  public Float(float value) {
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
    return (long) value;
  }

  public float floatValue() {
    return value;
  }

  public double doubleValue() {
    return (double) value;
  }
}
