package java.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class WeakHashMap<K, V> implements Map<K, V> {
  private final HashMap<K, V> map;
  private final ReferenceQueue queue;

  public WeakHashMap(int capacity) {
    map = new HashMap(capacity, new MyCellFactory());
    queue = new ReferenceQueue();
  }

  public WeakHashMap() {
    this(0);
  }

  private void poll() {
    for (MyCell<K, V> c = (MyCell<K, V>) queue.poll();
         c != null;
         c = (MyCell<K, V>) queue.poll())
    {
      map.remove(c);
    }
  }

  public int size() {
    return map.size();
  }

  public V get(K key) {
    poll();
    return map.get(key);
  }

  public V put(K key, V value) {
    poll();
    return map.put(key, value);
  }

  public V remove(K key) {
    poll();
    return map.remove(key);
  }

  public void clear() {
    map.clear();
  }

  public Set<Entry<K, V>> entrySet() {
    return map.entrySet();
  }

  private static class MyCell<K, V>
    extends WeakReference<K>
    implements HashMap.Cell<K, V>
  {
    public V value;
    public HashMap.Cell<K, V> next;
    public int hashCode;

    public MyCell(K key, V value, HashMap.Cell<K, V> next) {
      super(key);
      this.value = value;
      this.next = next;
      this.hashCode = key.hashCode();
    }

    public K getKey() {
      return get();
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
      return hashCode;
    }
  }

  private static class MyCellFactory<K, V>
    implements HashMap.CellFactory<K, V>
  {
    public HashMap.Cell<K, V> make(K key, V value, HashMap.Cell<K, V> next) {
      return new MyCell(key, value, next);
    }
  }
}
