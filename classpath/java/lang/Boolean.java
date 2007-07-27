package java.lang;

public final class Boolean {
  public static final Class TYPE = Class.forName("Z");

  private final boolean value;

  public Boolean(boolean value) {
    this.value = value;
  }

  public boolean booleanValue() {
    return value;
  }
}
