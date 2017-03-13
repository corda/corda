/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.Collection;
import java.util.Queue;

public interface BlockingQueue<T> extends Queue<T> {
  public void put(T e) throws InterruptedException;
  
  public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException;
  
  public T take() throws InterruptedException;
  
  public T poll(long timeout, TimeUnit unit) throws InterruptedException;
  
  public int remainingCapacity();
  
  public int drainTo(Collection<? super T> c);
  
  public int drainTo(Collection<? super T> c, int maxElements);
}
