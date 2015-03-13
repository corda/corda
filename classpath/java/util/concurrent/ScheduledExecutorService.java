/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public interface ScheduledExecutorService extends ExecutorService {
  public ScheduledFuture<?> schedule(Runnable command,
                                     long delay, TimeUnit unit);
  
  public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                         long delay, TimeUnit unit);
  
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                long initialDelay,
                                                long period,
                                                TimeUnit unit);
  
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                   long initialDelay,
                                                   long delay,
                                                   TimeUnit unit);
}
