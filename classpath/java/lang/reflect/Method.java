package java.lang.reflect;

public class Method<T> extends AccessibleObject implements Member {
  private byte vmFlags;
  private byte parameterCount;
  private short parameterFootprint;
  private short flags;
  private short offset;
  private byte[] name;
  private byte[] spec;
  private Class<T> class_;
  private Object code;

  private Method() { }

  public boolean isAccessible() {
    return (vmFlags & Accessible) != 0;
  }

  public void setAccessible(boolean v) {
    if (v) vmFlags |= Accessible; else vmFlags &= ~Accessible;
  }

  public Class<T> getDeclaringClass() {
    return class_;
  }

  public int getModifiers() {
    return flags;
  }

  public String getName() {
    return new String(name, 0, name.length - 1, false);
  }
}
