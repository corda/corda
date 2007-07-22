package java.util;

public interface List<T> extends Collection<T> {
  public T get(int index);

  public T remove(int index);
}
