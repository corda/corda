package java.util;

public class Hashtable<K, V> implements Map<K, V> {
  private final HashMap<K, V> map;

  public Hashtable(int capacity) {
    map = new HashMap(capacity);
  }

  public Hashtable() {
    this(0);
  }

  public synchronized int size() {
    return map.size();
  }

  public synchronized V get(K key) {
    return map.get(key);
  }

  public synchronized V put(K key, V value) {
    return map.put(key, value);
  }

  public synchronized V remove(K key) {
    return map.remove(key);
  }

  public synchronized void clear() {
    map.clear();
  }

  public Enumeration<K> keys() {
    return new Collections.IteratorEnumeration(keySet().iterator());
  }

  public Enumeration<V> elements() {
    return new Collections.IteratorEnumeration(values().iterator());
  }

  public Set<Entry<K, V>> entrySet() {
    return new Collections.SynchronizedSet(this, map.entrySet());
  }

  public Set<K> keySet() {
    return new Collections.SynchronizedSet(this, map.keySet());
  }

  public Collection<V> values() {
    return new Collections.SynchronizedCollection(this, map.values());
  }

}
