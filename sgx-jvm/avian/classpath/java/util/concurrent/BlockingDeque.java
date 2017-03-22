/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.Deque;

public interface BlockingDeque<T> extends Deque<T>, BlockingQueue<T> {
  public T takeFirst() throws InterruptedException;
  
  public T takeLast() throws InterruptedException;
  
  public T pollFirst(long timeout, TimeUnit unit) throws InterruptedException;
  
  public T pollLast(long timeout, TimeUnit unit) throws InterruptedException;
}
