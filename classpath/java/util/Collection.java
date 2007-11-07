package java.util;

public interface Collection<T> extends Iterable<T> {
  public int size();

  public boolean isEmpty();

  public boolean contains(T element);

  public boolean add(T element);

  public boolean remove(T element);

  public void clear();
}
