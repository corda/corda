/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public interface Future<V> {
  public boolean cancel(boolean mayInterruptIfRunning);
  
  public boolean isCancelled();
  
  public boolean isDone();
  
  public V get() throws InterruptedException, ExecutionException;
  
  public V get(long timeout, TimeUnit unit) throws InterruptedException, 
                                                   ExecutionException, 
                                                   TimeoutException;
}
