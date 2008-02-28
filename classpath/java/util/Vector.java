/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Vector<T> implements List<T> {
  private final ArrayList<T> list;

  public Vector(int capacity) {
    list = new ArrayList(capacity);
  }

  public Vector() {
    this(0);
  }

  public Vector(Collection<? extends T> source) {
    list = new ArrayList(source);
  }

  public synchronized int size() {
    return list.size();
  }

  public synchronized boolean contains(T element) {
    return list.contains(element);
  }

  public synchronized void add(int index, T element) {
    list.add(index, element);
  }

  public synchronized boolean add(T element) {
    return list.add(element);
  }

  public synchronized boolean addAll(Collection<? extends T> collection) {
    return list.addAll(collection);
  }

  public void addElement(T element) {
    add(element);
  }

  public synchronized T get(int index) {
    return list.get(index);
  }

  public synchronized T set(int index, T value) {
      return list.set(index, value);
  }

  public T elementAt(int index) {
    return get(index);
  }

  public synchronized T remove(int index) {
    return list.remove(index);
  }

  public synchronized boolean isEmpty() {
    return list.isEmpty();
  }

  public synchronized <S> S[] toArray(S[] a) {
    return list.toArray(a);
  }

  public void removeElementAt(int index) {
    remove(index);
  }

  public synchronized boolean remove(T element) {
    return list.remove(element);
  }

  public boolean removeElement(T element) {
    return remove(element);
  }

  public synchronized void clear() {
    list.clear();
  }

  public synchronized int indexOf(T element) {
    return list.indexOf(element);
  }

  public synchronized int lastIndexOf(T element) {
    return list.lastIndexOf(element);
  }

  public synchronized void copyInto(Object[] array) {
    for (int i = 0; i < size(); ++i) {
      array[i] = list.get(i);
    }
  }

  public Iterator<T> iterator() {
    return new Collections.ArrayListIterator(this);
  }

  public Enumeration<T> elements() {
    return new Collections.IteratorEnumeration(iterator());
  }
  
}
