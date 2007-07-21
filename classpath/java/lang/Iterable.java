package java.lang;

import java.util.Iterator;

public interface Iterable<T> {
  public Iterator<T> iterator();
}
