/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

import java.io.IOException;

public class TreeMap<K,V> implements NavigableMap<K,V> {
  private final Comparator<K> comparator;
  private transient TreeSet<MyEntry<K,V>> set;

  public TreeMap(Comparator<K> comparator) {
    this.comparator = comparator;
    initializeSet();
  }

  private void initializeSet() {
    final Comparator<K> comparator = this.comparator != null ?
      this.comparator : new Comparator<K>() {
        public int compare(K a, K b) {
          return ((Comparable) a).compareTo(b);
        }
      };
    set = new TreeSet(new Comparator<MyEntry<K,V>>() {
      public int compare(MyEntry<K,V> a, MyEntry<K,V> b) {
        return comparator.compare(a.key, b.key);
      }
    });
  }

  public TreeMap() {
    this(null);
  }

  public String toString() {
    return avian.Data.toString(this);
  }

  @Override
  public Comparator<? super K> comparator() {
    return comparator;
  }

  @Override
  public Map.Entry<K,V> firstEntry() {
    return set.first();
  }

  @Override
  public Map.Entry<K,V> lastEntry() {
    return set.last();
  }

  @Override
  public K firstKey() {
    return set.first().key;
  }

  @Override
  public K lastKey() {
    return set.last().key;
  }

  @Override
  public SortedMap<K, V> headMap(K toKey) {
    // TODO - this should be implemented, the trick is making the returned SortedMap backed by this TreeSet
    throw new UnsupportedOperationException();
  }

  @Override
  public SortedMap<K, V> tailMap(K fromKey) {
    // TODO - this should be implemented, the trick is making the returned SortedMap backed by this TreeSet
    throw new UnsupportedOperationException();
  }

  @Override
  public SortedMap<K, V> subMap(K fromKey, K toKey) {
    // TODO - this should be implemented, the trick is making the returned SortedMap backed by this TreeSet
    throw new UnsupportedOperationException();
  }

  public V get(Object key) {
    MyEntry<K,V> e = set.find(new MyEntry(key, null));
    return e == null ? null : e.value;
  }

  public V put(K key, V value) {
    MyEntry<K,V> e = set.addAndReplace(new MyEntry(key, value));
    return e == null ? null : e.value;
  }

  public void putAll(Map<? extends K,? extends V> elts) {
    for (Map.Entry<? extends K, ? extends V> entry : elts.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }
    
  public V remove(Object key) {
    MyEntry<K,V> e = set.removeAndReturn(new MyEntry(key, null));
    return e == null ? null : e.value;
  }

  public void clear() {
    set.clear();
  }

  public int size() {
    return set.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public boolean containsKey(Object key) {
    return set.contains(new MyEntry(key, null));
  }

  private boolean equal(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }

  public boolean containsValue(Object value) {
    for (V v: values()) {
      if (equal(v, value)) {
        return true;
      }
    }
    return false;
  }

  public Set<Entry<K, V>> entrySet() {
    return (Set<Entry<K, V>>) (Set) set;
  }

  public Set<K> keySet() {
    return new KeySet();
  }

  public Collection<V> values() {
    return new Values();
  }

  private static class MyEntry<K,V> implements Entry<K,V> {
    public final K key;
    public V value;

    public MyEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      V old = this.value;
      this.value = value;
      return old;
    }
    
  }

  private class KeySet extends AbstractSet<K> {
    public int size() {
      return TreeMap.this.size();
    }

    public boolean isEmpty() {
      return TreeMap.this.isEmpty();
    }

    public boolean contains(Object key) {
      return containsKey(key);
    }

    public boolean add(K key) {
      return set.addAndReplace(new MyEntry(key, null)) != null;
    }

    public boolean addAll(Collection<? extends K> collection) {
      boolean change = false;
      for (K k: collection) if (add(k)) change = true;
      return change;
    }

    public boolean remove(Object key) {
      return set.removeAndReturn(new MyEntry(key, null)) != null;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      return avian.Data.toArray(this, array);      
    }

    public void clear() {
      TreeMap.this.clear();
    }

    public Iterator<K> iterator() {
      return new avian.Data.KeyIterator(set.iterator());
    }
  }

  private class Values implements Collection<V> {
    public int size() {
      return TreeMap.this.size();
    }

    public boolean isEmpty() {
      return TreeMap.this.isEmpty();
    }

    public boolean contains(Object value) {
      return containsValue(value);
    }

    public boolean containsAll(Collection<?> c) {
      if (c == null) {
        throw new NullPointerException("collection is null");
      }
      
      Iterator<?> it = c.iterator();
      while (it.hasNext()) {
        if (! contains(it.next())) {
          return false;
        }
      }
      
      return true;
    }

    public boolean add(V value) {
      throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection<? extends V> collection) {
      throw new UnsupportedOperationException();      
    }

    public boolean remove(Object value) {
      throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      return avian.Data.toArray(this, array);      
    }

    public void clear() {
      TreeMap.this.clear();
    }

    public Iterator<V> iterator() {
      return new avian.Data.ValueIterator(set.iterator());
    }
  }
}
