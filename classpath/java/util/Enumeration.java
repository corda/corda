package java.util;

public interface Enumeration<T> {
  public T nextElement();

  public boolean hasMoreElements();
}
