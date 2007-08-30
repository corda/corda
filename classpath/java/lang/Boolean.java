package java.lang;

public final class Boolean {
  public static final Class TYPE = Class.forCanonicalName("Z");

  public static final Boolean FALSE = new Boolean(false);
  public static final Boolean TRUE = new Boolean(true);

  private final boolean value;

  public Boolean(boolean value) {
    this.value = value;
  }

  public static Boolean valueOf(boolean value) {
    return (value ? Boolean.TRUE : Boolean.FALSE);
  }

  public static Boolean valueOf(String s) {
    return ("true".equals(s) ? Boolean.TRUE : Boolean.FALSE);
  }

  public boolean equals(Object o) {
    return o instanceof Boolean && ((Boolean) o).value == value;
  }

  public int hashCode() {
    return (value ? 1 : 0);
  }

  public String toString() {
    return toString(value);
  }

  public static String toString(boolean v) {
    return (v ? "true" : "false");
  }

  public boolean booleanValue() {
    return value;
  }
}
