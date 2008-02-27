/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public interface Map<K, V> {
  public boolean isEmpty();

  public int size();

  public boolean containsKey(K key);

  public boolean containsValue(V value);

  public V get(K key);

  public V put(K key, V value);

  public void putAll(Map<? extends K,? extends V> elts);

  public V remove(K key);

  public void clear();

  public Set<Entry<K, V>> entrySet();

  public Set<K> keySet();

  public Collection<V> values();

  public interface Entry<K, V> {
    public K getKey();

    public V getValue();

    public void setValue(V value);
  }
}
