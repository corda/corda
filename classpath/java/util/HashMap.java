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

  private Cell<K, V> putCell(K key, V value) {
    Cell<K, V> c = find(key);
    if (c == null) {
      insert(factory.make(key, value, null));
    } else {
      V old = c.getValue();
      c.setValue(value);
    }
    return c;
  }

  public V get(K key) {
    Cell<K, V> c = find(key);
    return (c == null ? null : c.getValue());
  }

  public Cell<K, V> removeCell(K key) {
    Cell<K, V> old = null;
    if (key == null) {
      if (nullCell != null) {
        old = nullCell;
        nullCell = null;
        -- size;
      }
    } else {
      if (array != null) {
        int index = key.hashCode() & (array.length - 1);
        Cell<K, V> p = null;
        for (Cell<K, V> c = array[index]; c != null; c = c.next()) {
          if (key.equals(c.getKey())) {
            old = c;
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

  public V put(K key, V value) {
    Cell<K, V> c = putCell(key, value);
    return (c == null ? null : c.getValue());
  }

  public V remove(K key) {
    Cell<K, V> c = removeCell(key);
    return (c == null ? null : c.getValue());
  }

  public void clear() {
    array = null;
  }

  public Set<Entry<K, V>> entrySet() {
    return new MySet();
  }

  Iterator<Entry<K, V>> iterator() {
    return new MyIterator();
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

  private class MySet implements Set<Entry<K, V>> {
    public int size() {
      return HashMap.this.size();
    }

    public boolean add(Entry<K, V> e) {
      return putCell(e.getKey(), e.getValue()) != null;
    }

    public boolean remove(Entry<K, V> e) {
      return removeCell(e.getKey()) != null;
    }

    public void clear() {
      HashMap.this.clear();
    }

    public Iterator<Entry<K, V>> iterator() {
      return new MyIterator();
    }
  }

  private class MyIterator implements Iterator<Entry<K, V>> {
    private int currentIndex = -1;
    private int nextIndex = -1;
    private Cell<K, V> previousCell;
    private Cell<K, V> currentCell;
    private Cell<K, V> nextCell;

    public MyIterator() {
      hasNext();
    }

    public Entry<K, V> next() {
      if (hasNext()) {
        if (currentCell != null && currentCell.next() != null) {
          previousCell = currentCell;
        } else {
          previousCell = null;
        }

        currentCell = nextCell;
        currentIndex = nextIndex;

        nextCell = nextCell.next();

        return currentCell;
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      if (array != null) {
        while (nextCell == null && ++ nextIndex < array.length) {
          if (array[nextIndex] != null) {
            nextCell = array[nextIndex];
            return true;
          }
        }
      }
      return false;
    }

    public void remove() {
      if (currentCell != null) {
        if (previousCell == null) {
          array[currentIndex] = currentCell.next();
        } else {
          previousCell.setNext(currentCell.next());
          if (previousCell.next() == null) {
            previousCell = null;
          }
        }
        currentCell = null;
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
