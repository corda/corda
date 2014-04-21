/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public class ExecutorCompletionService<T> implements CompletionService<T> {
  private final Executor executor;
  private final BlockingQueue<Future<T>> completionQueue;
  
  public ExecutorCompletionService(Executor executor) {
    this(executor, new LinkedBlockingQueue<Future<T>>());
  }
  
  public ExecutorCompletionService(Executor executor, BlockingQueue<Future<T>> completionQueue) {
    this.executor = executor;
    this.completionQueue = completionQueue;
  }
  
  @Override
  public Future<T> submit(Callable<T> task) {
    ECSFuture f = new ECSFuture(task);
    
    executor.execute(f);
    
    return f;
  }

  @Override
  public Future<T> submit(Runnable task, T result) {
    ECSFuture f = new ECSFuture(task, result);
    
    executor.execute(f);
    
    return f;
  }

  @Override
  public Future<T> take() throws InterruptedException {
    return completionQueue.take();
  }

  @Override
  public Future<T> poll() {
    return completionQueue.poll();
  }

  @Override
  public Future<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
    return completionQueue.poll(timeout, unit);
  }
  
  private class ECSFuture extends FutureTask<T> implements Future<T> {
    private ECSFuture(Runnable r, T result) {
      super(r, result);
    }
    
    private ECSFuture(Callable<T> callable) {
      super(callable);
    }
    
    @Override
    protected void done() {
      completionQueue.add(this);
    }
  }
}
