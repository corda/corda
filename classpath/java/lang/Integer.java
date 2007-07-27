package java.lang;

public final class Integer extends Number {
  public static final Class TYPE = Class.forName("I");

  private final int value;

  public Integer(int value) {
    this.value = value;
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

  public static int parseInt(String s, int radix) {
    return (int) Long.parseLong(s, radix);
  }
}
