/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Collections {
  private Collections() { }

  static <T> T[] toArray(Collection collection, T[] array) {
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

  static String toString(Collection c) {
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

  static class IteratorEnumeration<T> implements Enumeration<T> {
    private final Iterator<T> it;

    public IteratorEnumeration(Iterator<T> it) {
      this.it = it;
    }

    public T nextElement() {
      return it.next();
    }

    public boolean hasMoreElements() {
      return it.hasNext();
    }
  }

  static class SynchronizedCollection<T> implements Collection<T> {
    protected final Object lock;
    protected final Collection<T> collection;

    public SynchronizedCollection(Object lock, Collection<T> collection) {
      this.lock = lock;
      this.collection = collection;
    }

    public int size() {
      synchronized (lock) { return collection.size(); }
    }

    public boolean isEmpty() {
      return size() == 0;
    }

    public boolean contains(T e) {
      synchronized (lock) { return collection.contains(e); }
    }

    public boolean add(T e) {
      synchronized (lock) { return collection.add(e); }
    }

    public boolean addAll(Collection<? extends T> collection) {
      synchronized (lock) { return this.collection.addAll(collection); }
    }

    public boolean remove(T e) {
      synchronized (lock) { return collection.remove(e); }
    }

    public <T> T[] toArray(T[] array) {
      synchronized (lock) { return collection.toArray(array); }
    }

    public void clear() {
      synchronized (lock) { collection.clear(); }
    }

    public Iterator<T> iterator() {
      return new SynchronizedIterator(lock, collection.iterator());
    }
  }

  static class SynchronizedSet<T>
    extends SynchronizedCollection<T>
    implements Set<T>
  {
    public SynchronizedSet(Object lock, Set<T> set) {
      super(lock, set);
    }
  }

  static class SynchronizedIterator<T> implements Iterator<T> {
    private final Object lock;
    private final Iterator<T> it;

    public SynchronizedIterator(Object lock, Iterator<T> it) {
      this.lock = lock;
      this.it = it;
    }

    public T next() {
      synchronized (lock) { return it.next(); }
    }

    public boolean hasNext() {
      synchronized (lock) { return it.hasNext(); }
    }

    public void remove() {
      synchronized (lock) { it.remove(); }
    }
  }

  static class ArrayListIterator<T> implements ListIterator<T> {
    private final List<T> list;
    private boolean canRemove = false;
    private int index;

    public ArrayListIterator(List<T> list) {
      this(list, 0);
    }

    public ArrayListIterator(List<T> list, int index) {
      this.list = list;
      this.index = index - 1;
    }

    public boolean hasPrevious() {
      return index >= 0;
    }

    public T previous() {
      if (hasPrevious()) {
        canRemove = true;
        return list.get(index--);
      } else {
        throw new NoSuchElementException();
      }
    }

    public T next() {
      if (hasNext()) {
        canRemove = true;
        return list.get(++index);
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      return index + 1 < list.size();
    }

    public void remove() {
      if (canRemove) {
        canRemove = false;
        list.remove(index--);
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
