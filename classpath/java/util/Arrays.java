package java.util;

public class Arrays {
  private Arrays() { }

  public static <T> List<T> asList(final T ... array) {
    return new List<T>() {
      public int size() {
        return array.length;
      }

      public boolean add(T element) {
        throw new UnsupportedOperationException();
      }

      public T get(int index) {
        return array[index];
      }

      public T remove(int index) {
        throw new UnsupportedOperationException();        
      }

      public boolean remove(T element) {
        throw new UnsupportedOperationException();
      }

      public void clear() {
        throw new UnsupportedOperationException();
      }

      public Iterator<T> iterator() {
        return new Collections.ArrayListIterator(this);
      }
    };
  }
}
