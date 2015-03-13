/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public abstract class AbstractDeque<T> extends AbstractQueue<T> 
                                       implements Deque<T> {

  @Override
  public void push(T e) {
    addFirst(e);
  }
  
  @Override
  public void addLast(T element) {
    if (! offerLast(element)) {
      throw new IllegalStateException();
    }
  }

  @Override
  public void addFirst(T element) {
    if (! offerFirst(element)) {
      throw new IllegalStateException();
    }
  }

  @Override
  public boolean offer(T element) {
    return offerLast(element);
  }

  @Override
  public T poll() {
    return pollFirst();
  }

  @Override
  public T pop() {
    return removeFirst();
  }

  @Override
  public T removeFirst() {
    return remove();
  }

  @Override
  public boolean remove(Object element) {
    return removeFirstOccurrence(element);
  }

  @Override
  public T removeLast() {
    T result = pollLast();
    if (result == null) {
      throw new NoSuchElementException();
    } else {
      return result;
    }
  }

  @Override
  public T getFirst() {
    return element();
  }

  @Override
  public T getLast() {
    T result = peekLast();
    
    if (result == null) {
      throw new NoSuchElementException();
    }
    
    return result;
  }

  @Override
  public T peek() {
    return peekFirst();
  }
}
