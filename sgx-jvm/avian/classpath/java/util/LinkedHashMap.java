/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class LinkedHashMap<K, V> extends HashMap<K, V> {
  private static class LinkedKey<K> {
    private final K key;
    private LinkedKey<K> previous, next;

    public LinkedKey(K key) {
      this.key = key;
    }

    public boolean equals(Object other) {
      LinkedKey<K> o = (LinkedKey<K>) other;
      return key.equals(o.key);
    }

    public int hashCode() {
      return key.hashCode();
    }
  }

  private LinkedKey first, last;
  private HashMap<K, LinkedKey<K>> lookup;

  public LinkedHashMap(int capacity) {
    super(capacity);
    lookup = new HashMap<K, LinkedKey<K>>();
  }

  public LinkedHashMap() {
    this(0);
  }

  public LinkedHashMap(Map<K, V> map) {
    this(map.size());
    putAll(map);
  }

  public V put(K key, V value) {
    if (!super.containsKey(key)) {
      LinkedKey<K> k = new LinkedKey<K>(key);
      if (first == null) {
        first = k;
      } else {
        last.next = k;
        k.previous = last;
      }
      last = k;
      lookup.put(key, k);
    }
    return super.put(key, value);
  }

  public V remove(Object key) {
    LinkedKey<K> linked = lookup.get(key);
    if (linked == null) {
      return null;
    }
    if (linked.previous == null) {
      first = linked.next;
    } else {
      linked.previous.next = linked.next;
    }
    if (linked.next == null) {
      last = linked.previous;
    } else {
      linked.next.previous = linked.previous;
    }
    return super.remove(key);
  }

  public void clear() {
    first = last = null;
    super.clear();
  }

  public Set<Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  public Set<K> keySet() {
    return new KeySet();
  }

  public Collection<V> values() {
    return new Values();
  }

  Iterator<Entry<K, V>> iterator() {
    return new MyIterator();
  }

 private class EntrySet extends AbstractSet<Entry<K, V>> {
    public int size() {
      return LinkedHashMap.this.size();
    }

    public boolean isEmpty() {
      return LinkedHashMap.this.isEmpty();
    }

    public boolean contains(Object o) {
      return (o instanceof Entry<?,?>)
        && containsKey(((Entry<?,?>)o).getKey());
    }

    public boolean add(Entry<K, V> e) {
      return put(e.getKey(), e.getValue()) != null;
    }

    public boolean remove(Object o) {
      return (o instanceof Entry<?,?>) && remove((Entry<?,?>)o);
    }

    public boolean remove(Entry<K, V> e) {
      return LinkedHashMap.this.remove(e.getKey()) != null;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);
    }

    public <T> T[] toArray(T[] array) {
      return avian.Data.toArray(this, array);
    }

    public void clear() {
      LinkedHashMap.this.clear();
    }

    public Iterator<Entry<K, V>> iterator() {
      return new MyIterator();
    }
  }

  private class KeySet extends AbstractSet<K> {
    public int size() {
      return LinkedHashMap.this.size();
    }

    public boolean isEmpty() {
      return LinkedHashMap.this.isEmpty();
    }

    public boolean contains(Object key) {
      return containsKey(key);
    }

    public boolean add(K key) {
      return put(key, null) != null;
    }

    public boolean remove(Object key) {
      return LinkedHashMap.this.remove(key) != null;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);
    }

    public <T> T[] toArray(T[] array) {
      return avian.Data.toArray(this, array);
    }

    public void clear() {
      LinkedHashMap.this.clear();
    }

    public Iterator<K> iterator() {
      return new avian.Data.KeyIterator(new MyIterator());
    }
  }

  private class Values implements Collection<V> {
    public int size() {
      return LinkedHashMap.this.size();
    }

    public boolean isEmpty() {
      return LinkedHashMap.this.isEmpty();
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
      LinkedHashMap.this.clear();
    }

    public Iterator<V> iterator() {
      return new avian.Data.ValueIterator(new MyIterator());
    }
  }

  private class MyIterator implements Iterator<Entry<K, V>> {
    private LinkedKey<K> current = first;

    public Entry<K, V> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Entry<K, V> result = find(current.key);
      current = current.next;
      return result;
    }

    public boolean hasNext() {
      return current != null;
    }

    public void remove() {
      LinkedHashMap.this.remove(current == null ?
        last.key : current.previous.key);
    }
  }
}

