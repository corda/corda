package java.util;

public interface Iterator<T> {
  public T next();

  public boolean hasNext();

  public void remove();
}
