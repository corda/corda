package java.util;

public interface Collection<T> extends Iterable<T> {
  public int size();

  public boolean add(T element);

  public boolean remove(T element);

  public void clear();
}
