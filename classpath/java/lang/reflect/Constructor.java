package java.lang.reflect;

public class Constructor<T> extends AccessibleObject implements Member {
  private Method<T> method;

  public Constructor(Method<T> method) {
    this.method = method;
  }

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

  public Class[] getParameterTypes() {
    return method.getParameterTypes();
  }

  public int getModifiers() {
    return method.getModifiers();
  }

  public String getName() {
    return method.getName();
  }

  private static native <T> T make(Class<T> c);

  public T newInstance(Object ... arguments)
    throws InvocationTargetException, InstantiationException,
    IllegalAccessException
  {
    T v = make(method.getDeclaringClass());
    method.invoke(v, arguments);
    return v;
  }
}
