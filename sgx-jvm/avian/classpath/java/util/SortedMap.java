package java.util;

public interface SortedMap<K, V> extends Map<K, V> {
  public Comparator<? super K> comparator();
  
  public K firstKey();
  
  public K lastKey();
  
  public SortedMap<K, V> headMap(K toKey);
  
  public SortedMap<K, V> tailMap(K fromKey);
  
  public SortedMap<K, V> subMap(K fromKey, K toKey);
}
