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

<<<<<<< HEAD:classpath/java/util/Map.java
  public boolean containsKey(Object obj);

  public boolean containsValue(Object obj);
=======
  public boolean containsKey(Object key);

  public boolean containsValue(Object value);
>>>>>>> af784f4cbc18911e29127bcda8110de9bb56d654:classpath/java/util/Map.java

  public V get(Object key);

  public V put(K key, V value);

  public void putAll(Map<? extends K,? extends V> elts);

  public V remove(Object key);

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
