/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public interface Deque<T> extends Queue<T> {
  public boolean offerFirst(T e);
  public void push(T e);
  public void addFirst(T element);
  public boolean offerLast(T e);
  public void addLast(T element);
  public T peekFirst();
  public T getFirst();
  public T peekLast();
  public T getLast();
  public T pollFirst();
  public T removeFirst();
  public T pop();
  public T pollLast();
  public T removeLast();
  public Iterator<T> descendingIterator();
  public boolean removeLastOccurrence(Object o);
  public boolean removeFirstOccurrence(Object o);
}
