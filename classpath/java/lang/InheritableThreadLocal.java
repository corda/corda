package java.lang;

import java.util.Map;

public class InheritableThreadLocal<T> extends ThreadLocal<T> {
  protected T childValue(T parentValue) {
    return parentValue;
  }
}
