package java.util;

public interface Collection<T> extends Iterable<T> {
  public int size();

  public boolean add(T entry);

  public boolean remove(T entry);

  public void clear();
}
