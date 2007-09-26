package java.util;

public interface ListIterator<E> extends Iterator<E> {
  public boolean hasPrevious();
  public E previous();
}
