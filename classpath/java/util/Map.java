package java.util;

public interface Map<K, V> {
  public int size();

  public V get(K key);

  public V put(K key, V value);

  public V remove(K key);

  public void clear();

  public Set<Entry<K, V>> entrySet();

  public interface Entry<K, V> {
    public K getKey();

    public V getValue();

    public void setValue(V value);
  }
}
