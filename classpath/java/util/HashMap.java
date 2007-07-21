package java.util;

public class HashMap<K, V> implements Map<K, V> {
  private int size;
  private Cell[] array;
  private Cell<K, V> nullCell;
  private final CellFactory factory;

  HashMap(int capacity, CellFactory<K, V> factory) {
    if (capacity > 0) {
      array = new Cell[nextPowerOfTwo(capacity)];
    }
    this.factory = factory;
  }

  public HashMap(int capacity) {
    this(capacity, new MyCellFactory());
  }

  public HashMap() {
    this(0);
  }

  private static int nextPowerOfTwo(int n) {
    int r = 1;
    while (r < n) r <<= 1;
    return r;
  }

  public int size() {
    return size;
  }

  private void resize() {
    if (array == null || size >= array.length * 2) {
      resize(array == null ? 16 : array.length * 2);
    } else if (size <= array.length / 3) {
      resize(array.length / 2);
    }
  }

  private void resize(int capacity) {
    Cell<K, V>[] newArray = null;
    if (capacity != 0) {
      capacity = nextPowerOfTwo(capacity);
      if (array != null && array.length == capacity) {
        return;
      }

      newArray = new Cell[capacity];
      if (array != null) {
        for (int i = 0; i < array.length; ++i) {
          Cell<K, V> next;
          for (Cell<K, V> c = array[i]; c != null; c = next) {
            next = c.next();
            int index = c.getKey().hashCode() & (capacity - 1);
            c.setNext(array[index]);
            array[index] = c;
          }
        }
      }
    }
    array = newArray;
  }

  private Cell<K, V> find(K key) {
    if (key == null) {
      return nullCell;
    } else {
      if (array != null) {
        int index = key.hashCode() & (array.length - 1);
        for (Cell<K, V> c = array[index]; c != null; c = c.next()) {
          if (key.equals(c.getKey())) {
            return c;
          }
        }
      }

      return null;
    }
  }

  private void insert(Cell<K, V> cell) {
    ++ size;

    if (cell.getKey() == null) {
      nullCell = cell;
    } else {
      resize();

      int index = cell.hashCode() & (array.length - 1);
      cell.setNext(array[index]);
      array[index] = cell;
    }
  }

  // primarily for use by WeakHashMap:
  void remove(Cell<K, V> cell) { 
    if (cell == nullCell) {
      nullCell = null;
      -- size;
    } else {
      int index = cell.hashCode() & (array.length - 1);
      Cell<K, V> p = null;
      for (Cell<K, V> c = array[index]; c != null; c = c.next()) {
        if (c == cell) {
          if (p == null) {
            array[index] = c.next();
          } else {
            p.setNext(c.next());
          }
          -- size;
          break;
        }
      }

      resize();
    }
  }

  public V get(K key) {
    Cell<K, V> c = find(key);
    return (c == null ? null : c.getValue());
  }

  public V put(K key, V value) {
    Cell<K, V> c = find(key);
    if (c == null) {
      insert(factory.make(key, value, null));
      return null;
    } else {
      V old = c.getValue();
      c.setValue(value);
      return old;
    }
  }

  public V remove(K key) {
    V old = null;
    if (key == null) {
      if (nullCell != null) {
        old = nullCell.getValue();
        nullCell = null;
        -- size;
      }
    } else {
      if (array != null) {
        int index = key.hashCode() & (array.length - 1);
        Cell<K, V> p = null;
        for (Cell<K, V> c = array[index]; c != null; c = c.next()) {
          if (key.equals(c.getKey())) {
            old = c.getValue();
            if (p == null) {
              array[index] = c.next();
            } else {
              p.setNext(c.next());
            }
            -- size;
            break;
          }
        }

        resize();
      }
    }
    return old;
  }

  interface Cell<K, V> extends Entry<K, V> {
    public HashMap.Cell<K, V> next();

    public void setNext(HashMap.Cell<K, V> next);
  }

  interface CellFactory<K, V> {
    public Cell<K, V> make(K key, V value, Cell<K, V> next);
  }

  private static class MyCell<K, V> implements Cell<K, V> {
    public final K key;
    public V value;
    public Cell<K, V> next;

    public MyCell(K key, V value, Cell<K, V> next) {
      this.key = key;
      this.value = value;
      this.next = next;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public void setValue(V value) {
      this.value = value;
    }

    public HashMap.Cell<K, V> next() {
      return next;
    }

    public void setNext(HashMap.Cell<K, V> next) {
      this.next = next;
    }

    public int hashCode() {
      return key.hashCode();
    }
  }

  private static class MyCellFactory<K, V> implements CellFactory<K, V> {
    public Cell<K, V> make(K key, V value, Cell<K, V> next) {
      return new MyCell(key, value, next);
    }
  }
}
