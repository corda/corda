package java.util;

public class Arrays {
  private Arrays() { }

  public static String toString(Object[] a) {
    return asList(a).toString();
  }

  private static boolean equal(Object a, Object b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  public static <T> List<T> asList(final T ... array) {
    return new List<T>() {
      public String toString() {
        return Collections.toString(this);
      }

      public int size() {
        return array.length;
      }

      public boolean add(T element) {
        throw new UnsupportedOperationException();
      }

      public boolean contains(T element) {
        for (int i = 0; i < array.length; ++i) {
          if (equal(element, array[i])) {
            return true;
          }
        }
        return false;
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
