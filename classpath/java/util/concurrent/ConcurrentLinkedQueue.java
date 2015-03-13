/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;

import avian.Atomic;

public class ConcurrentLinkedQueue<T> extends AbstractQueue<T> {
  private static final long QueueHead;
  private static final long QueueTail;
  private static final long NodeNext;

  static {
    try {
      QueueHead = Atomic.getOffset
        (ConcurrentLinkedQueue.class.getField("head"));

      QueueTail = Atomic.getOffset
        (ConcurrentLinkedQueue.class.getField("tail"));

      NodeNext = Atomic.getOffset
        (Node.class.getField("next"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private volatile Node<T> head = new Node<T>(null, null);
  private volatile Node<T> tail = head;

  @Override
  public void clear() {
    // TODO - can we safely make this O(1)?
    while (poll() != null) { }
  }

  @Override
  public boolean offer(T element) {
    add(element);
    
    return true;
  }

  @Override
  public boolean add(T value) {
    Node<T> n = new Node<T>(value, null);
    while (true) {
      Node<T> t = tail;
      Node<T> next = tail.next;
      if (t == tail) {
        if (next != null) {
          Atomic.compareAndSwapObject(this, QueueTail, t, next);
        } else if (Atomic.compareAndSwapObject(tail, NodeNext, null, n)) {
          Atomic.compareAndSwapObject(this, QueueTail, t, n);
          break;
        }
      }
    }

    return true;
  }

  @Override
  public T peek() {
    return poll(false);
  }

  @Override
  public T poll() {
    return poll(true);
  }

  private T poll(boolean remove) {
    while (true) {
      Node<T> h = head;
      Node<T> t = tail;
      Node<T> next = head.next;

      if (h == head) {
        if (h == t) {
          if (next != null) {
            Atomic.compareAndSwapObject(this, QueueTail, t, next);
          } else {
            return null;
          }
        } else {
          T value = next.value;
          if ((! remove)
              || Atomic.compareAndSwapObject(this, QueueHead, h, next))
          {
            return value;
          }
        }
      }
    }
  }

  private static class Node<T> {
    public volatile T value;
    public volatile Node<T> next;

    public Node(T value, Node<T> next) {
      this.value = value;
      this.next = next;
    }
  }

  @Override
  public int size() {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean contains(Object element) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object element) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public Object[] toArray() {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public <S> S[] toArray(S[] array) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<T> iterator() {
    // TODO - implement
    throw new UnsupportedOperationException();
  }
}
