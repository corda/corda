/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.concurrent.atomic.AtomicReference;

public class FutureTask<T> implements RunnableFuture<T> {
  private enum State { New, Canceling, Canceled, Running, Done };
  
  private final AtomicReference<State> currentState;
  private final Callable<T> callable;
  private final Object notifyLock;
  private volatile Thread runningThread;
  private volatile T result;
  private volatile Throwable failure;
  
  public FutureTask(final Runnable r, final T result) {
    this(new Callable<T>() {
      @Override
      public T call() {
        r.run();
        
        return result;
      }
    });
  }
  
  public FutureTask(Callable<T> callable) {
    currentState = new AtomicReference<State>(State.New);
    this.callable = callable;
    notifyLock = new Object();
    runningThread = null;
    result = null;
    failure = null;
  }

  @Override
  public void run() {
    if (currentState.compareAndSet(State.New, State.Running)) {
      runningThread = Thread.currentThread();
      try {
        result = callable.call();
      } catch (Throwable t) {
        failure = t;
      } finally {
        if (currentState.compareAndSet(State.Running, State.Done) || 
            currentState.get() == State.Canceled) {
          /* in either of these conditions we either were not canceled 
           * or we already were interrupted.  The thread may or MAY NOT
           * be in an interrupted status depending on when it was 
           * interrupted and what the callable did with the state. 
           */
        } else {
          /* Should be in canceling state, so block forever till we are 
           * interrupted.  If state already transitioned into canceled 
           * and thus thread is in interrupted status, the exception should 
           * throw immediately on the sleep call.
           */
          throw new UnsupportedOperationException("Blocking not supported.");
        }

        Thread.interrupted(); // reset interrupted status if set
        handleDone();
        runningThread = null; // must be last operation
      }
    }
  }
  
  private void handleDone() {
    done();
    
    synchronized (notifyLock) {
      notifyLock.notifyAll();
    }
  }
  
  protected void done() {
    // default implementation does nothing, designed to be overridden
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (currentState.compareAndSet(State.New, State.Canceled)) {
      handleDone();
      
      return true;
    } else if (mayInterruptIfRunning && 
               currentState.compareAndSet(State.Running, State.Canceling)) {
      // handleDone will be called from running thread
      try {
        Thread runningThread = this.runningThread;
        if (runningThread != null) {
          runningThread.interrupt();
          
          return true;
        }
      } finally {
        // we can not set to canceled until interrupt status has been set
        currentState.set(State.Canceled);
      }
    }
    
    return false;
  }

  @Override
  public boolean isCancelled() {
    return currentState.get() == State.Canceled;
  }

  @Override
  public boolean isDone() {
    return currentState.get() == State.Done || isCancelled();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException("System clock unavailable");
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, 
                                                   ExecutionException,
                                                   TimeoutException {
    throw new UnsupportedOperationException("System clock unavailable");
  }
}
