package java.lang.reflect;

public abstract class AccessibleObject {
  protected static final int Accessible = 1 << 0;

  public abstract boolean isAccessible();

  public abstract void setAccessible(boolean v);
}
