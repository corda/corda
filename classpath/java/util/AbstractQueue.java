/* Copyright (c) 2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public abstract class AbstractQueue<T> extends AbstractCollection<T> implements Queue<T> {
  
  protected AbstractQueue() {
    super();
  }
  
  public boolean add(T element) {
    if (offer(element)) {
      return true;
    } else {
      throw new IllegalStateException();
    }
  }
  
  public boolean addAll(Collection <? extends T> collection) {
    if (collection == null) {
      throw new NullPointerException();
    }

    for (T element : collection) {
      add(element);
    }

    return true;
  }
  
  public void clear() {
    while (size() > 0) {
      poll();
    }
  }
  
  public T element() {
    emptyCheck();
    return peek();
  }
  
  public T remove() {
    emptyCheck();
    return poll();
  }
  
  private void emptyCheck() {
    if (size() == 0) {
      throw new NoSuchElementException();
    }
  }
}
