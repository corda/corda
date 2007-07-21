package java.lang.reflect;

public final class Modifier {
  public static final int PUBLIC       = 1 <<  0;
  public static final int PRIVATE      = 1 <<  1;
  public static final int PROTECTED    = 1 <<  2;
  public static final int STATIC       = 1 <<  3;
  public static final int FINAL        = 1 <<  4;
  public static final int SUPER        = 1 <<  5;
  public static final int SYNCHRONIZED = SUPER;
  public static final int VOLATILE     = 1 <<  6;
  public static final int TRANSIENT    = 1 <<  7;
  public static final int NATIVE       = 1 <<  8;
  public static final int INTERFACE    = 1 <<  9;
  public static final int ABSTRACT     = 1 << 10;
  public static final int STRICT       = 1 << 11;

  private Modifier() { }
}
