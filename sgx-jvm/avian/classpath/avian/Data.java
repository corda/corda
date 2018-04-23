/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import java.util.Map;
import java.util.Map.Entry;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Collections;

public class Data {
  public static int nextPowerOfTwo(int n) {
    int r = 1;
    while (r < n) r <<= 1;
    return r;
  }

  public static <V> boolean equal(V a, V b) {
    return a == null ? b == null : a.equals(b);
  }

  public static <T> T[] toArray(Collection collection, T[] array) {
    Class c = array.getClass().getComponentType();

    if (array.length < collection.size()) {
      array = (T[]) java.lang.reflect.Array.newInstance(c, collection.size());
    }

    int i = 0;
    for (Object o: collection) {
      if (c.isInstance(o)) {
        array[i++] = (T) o;
      } else {
        throw new ArrayStoreException();
      }
    }

    return array;
  }

  public static String toString(Collection c) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (Iterator it = c.iterator(); it.hasNext();) {
      sb.append(it.next());
      if (it.hasNext()) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  public static String toString(Map m) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    for (Iterator<Entry> it = m.entrySet().iterator(); it.hasNext();) {
      Entry e = it.next();
      sb.append(e.getKey())
        .append("=")
        .append(e.getValue());
      if (it.hasNext()) {
        sb.append(",");
      }
    }
    sb.append("}");
    return sb.toString();
  }

  public interface EntryMap<K,V> {
    public int size();

    public Entry<K,V> find(Object key);

    public Entry<K,V> remove(Object key);

    public void clear();

    public Iterator<Entry<K,V>> iterator();
  }

  public static class EntrySet<K, V> extends AbstractSet<Entry<K, V>> {
    private final EntryMap<K, V> map;

    public EntrySet(EntryMap<K, V> map) {
      this.map = map;
    }

    public int size() {
      return map.size();
    }

    public boolean isEmpty() {
      return map.size() == 0;
    }

    public boolean contains(Object o) {
      return (o instanceof Entry<?,?>)
        && map.find(((Entry<?,?>)o).getKey()) != null;
    }

    public boolean add(Entry<K, V> e) {
      throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
      return (o instanceof Entry<?,?>)
        && map.remove(((Entry<?,?>) o).getKey()) != null;
    }

    public boolean remove(Entry<K, V> e) {
      return map.remove(e.getKey()) != null;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      return Data.toArray(this, array);      
    }

    public void clear() {
      map.clear();
    }

    public Iterator<Entry<K, V>> iterator() {
      return map.iterator();
    }
  }

  public static class KeySet<K> extends AbstractSet<K> {
    private final EntryMap<K, ?> map;

    public KeySet(EntryMap<K, ?> map) {
      this.map = map;
    }

    public int size() {
      return map.size();
    }

    public boolean isEmpty() {
      return map.size() == 0;
    }

    public boolean contains(Object key) {
      return map.find(key) != null;
    }

    public boolean add(K key) {
      throw new UnsupportedOperationException();
    }

    public boolean remove(Object key) {
      return map.remove(key) != null;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      return Data.toArray(this, array);      
    }

    public void clear() {
      map.clear();
    }

    public Iterator<K> iterator() {
      return new KeyIterator(map.iterator());
    }
  }

  public static class Values<K, V> implements Collection<V> {
    private final EntryMap<K, V> map;

    public Values(EntryMap<K, V> map) {
      this.map = map;
    }

    public int size() {
      return map.size();
    }

    public boolean isEmpty() {
      return map.size() == 0;
    }

    public boolean contains(Object value) {
      for (Iterator<Entry<K, V>> it = map.iterator(); it.hasNext();) {
        if (equal(it.next().getValue(), value)) {
          return true;
        }
      }
      return false;
    }

    public boolean containsAll(Collection<?> c) {
      if (c == null) {
        throw new NullPointerException("collection is null");
      }
      
      for (Iterator<?> it = c.iterator(); it.hasNext();) {
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
      for (Iterator<Entry<K, V>> it = map.iterator();
           it.hasNext();)
      {
        if (equal(it.next().getValue(), value)) {
          it.remove();
          return true;
        }
      }
      return false;
    }

    public boolean removeAll(Collection<?> c) {
      boolean changed = false;
      for (Iterator<Entry<K, V>> it = map.iterator(); it.hasNext();) {
        if (c.contains(it.next().getValue())) {
          it.remove();
          changed = true;
        }
      }
      return changed;
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      return Data.toArray(this, array);      
    }

    public void clear() {
      map.clear();
    }

    public Iterator<V> iterator() {
      return new ValueIterator(map.iterator());
    }
  }

  public static class KeyIterator<K, V> implements Iterator<K> {
    private final Iterator<Entry<K, V>> it;

    public KeyIterator(Iterator<Entry<K, V>> it) {
      this.it = it;
    }

    public K next() {
      return it.next().getKey();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public void remove() {
      it.remove();
    }
  }

  public static class ValueIterator<K, V> implements Iterator<V> {
    private final Iterator<Entry<K, V>> it;

    public ValueIterator(Iterator<Entry<K, V>> it) {
      this.it = it;
    }

    public V next() {
      return it.next().getValue();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public void remove() {
      it.remove();
    }
  }
}
