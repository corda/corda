/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class HashSet<T> implements Set<T> {
  private static final Object Value = new Object();

  private final HashMap<T, Object> map;

  public HashSet(Collection<T> c) {
    map = new HashMap(c.size());
    addAll(c);
  }

  public HashSet(int capacity) {
    map = new HashMap(capacity);
  }

  public HashSet() {
    this(0);
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean contains(T element) {
    return map.containsKey(element);
  }

  public void addAll(Collection<T> c) {
    for (T t: c) add(t);
  }

  public boolean add(T element) {
    return map.put(element, Value) != Value;
  }

  public boolean remove(T element) {
    return map.remove(element) != Value;
  }

  public void clear() {
    map.clear();
  }

  public Iterator<T> iterator() {
    return new MyIterator(map.iterator());
  }

  private static class MyIterator<T> implements Iterator<T> {
    private final Iterator<Map.Entry<T, Object>> it;

    public MyIterator(Iterator<Map.Entry<T, Object>> it) {
      this.it = it;
    }

    public T next() {
      return it.next().getKey();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public void remove() {
      it.remove();
    }
  }
}
