package java.lang;

public final class Byte extends Number {
  public static final Class TYPE = Class.forName("B");

  private final byte value;

  public Byte(byte value) {
    this.value = value;
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
