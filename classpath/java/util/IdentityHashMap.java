package java.util;

public class IdentityHashMap<K, V> implements Map<K, V> {
  private final HashMap<K, V> map;

  public IdentityHashMap(int capacity) {
    map = new HashMap(capacity, new MyHelper());
  }

  public IdentityHashMap() {
    this(0);
  }

  public int size() {
    return map.size();
  }

  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public boolean containsValue(V value) {
    return map.containsValue(value);
  }

  public V get(K key) {
    return map.get(key);
  }

  public V put(K key, V value) {
    return map.put(key, value);
  }

  public V remove(K key) {
    return map.remove(key);
  }

  public void clear() {
    map.clear();
  }

  public Set<Entry<K, V>> entrySet() {
    return map.entrySet();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<V> values() {
    return map.values();
  }

  private static class MyHelper<K, V>
    extends HashMap.MyHelper<K, V>
  {
    public int hash(K a) {
      return (a == null ? 0 : System.identityHashCode(a));
    }

    public boolean equal(K a, K b) {
      return a == b;
    }    
  }
}
