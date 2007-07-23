package java.lang.reflect;

public class Constructor<T> extends AccessibleObject implements Member {
  private Method<T> method;

  private Constructor() { }

  public boolean equals(Object o) {
    return o instanceof Constructor
      && ((Constructor) o).method.equals(method);
  }

  public boolean isAccessible() {
    return method.isAccessible();
  }

  public void setAccessible(boolean v) {
    method.setAccessible(v);
  }

  public Class<T> getDeclaringClass() {
    return method.getDeclaringClass();
  }

  public int getModifiers() {
    return method.getModifiers();
  }

  public String getName() {
    return method.getName();
  }
}
