/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public interface CompletionService<T> {
  public Future<T> submit(Callable<T> task);
  
  public Future<T> submit(Runnable task, T result);
  
  public Future<T> take() throws InterruptedException;
  
  public Future<T> poll();
  
  public Future<T> poll(long timeout, TimeUnit unit) throws InterruptedException;
}
