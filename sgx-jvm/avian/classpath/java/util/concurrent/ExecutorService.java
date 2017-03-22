/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;
import java.util.List;
import java.util.Collection;

public interface ExecutorService extends Executor {
    public void shutdown();
    
    public List<Runnable> shutdownNow();

    public boolean isShutdown();

    public boolean isTerminated();

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    public <T> Future<T> submit(Callable<T> task);

    public <T> Future<T> submit(Runnable task, T result);

    public Future<?> submit(Runnable task);

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit) throws InterruptedException;

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
