package java.lang.reflect;

public class Field<T> extends AccessibleObject {
  private byte vmFlags;
  private byte code;
  private short flags;
  private short offset;
  private byte[] name;
  private byte[] spec;
  private Class<T> class_;

  private Field() { }

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

  public Class getType() {
    return Class.forCanonicalName(getName());
  }

  public native Object get(Object instance);

  public native void set(Object instance, Object value);
}
