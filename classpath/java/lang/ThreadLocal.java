package java.lang;

import java.util.Map;

public class ThreadLocal<T> {
  private static final Object Null = new Object();

  protected T initialValue() {
    return null;
  }

  public T get() {
    Map<ThreadLocal, Object> map = Thread.currentThread().locals();
    Object o = map.get(this);
    if (o == null) {
      o = initialValue();
      if (o == null) {
        o = Null;
      }
      map.put(this, o);
    }
    if (o == Null) {
      o = null;
    }
    return (T) o;
  }

  public void set(T value) {
    Map<ThreadLocal, Object> map = Thread.currentThread().locals();
    Object o = value;
    if (o == null) {
      o = Null;
    }
    map.put(this, o);
  }
}
