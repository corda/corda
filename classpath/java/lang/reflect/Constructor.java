package java.lang.reflect;

public class Constructor<T> extends AccessibleObject {
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
}
