package java.util;

public interface NavigableMap<K, V> extends SortedMap<K, V> {
  Map.Entry<K,V> firstEntry();
  Map.Entry<K,V> lastEntry();
}
