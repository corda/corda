package java.util.concurrent;

import java.util.concurrent.atomic.AtomicReference;

public class FutureTask<T> implements RunnableFuture<T> {
  private enum State { New, Canceled, Running, Done };
  
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
        currentState.compareAndSet(State.Running, State.Done);
        handleDone();
        runningThread = null; // must be set after state changed to done
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
    } else if (mayInterruptIfRunning) {
      Thread runningThread = this.runningThread;
      if (runningThread != null) {
        runningThread.interrupt();
        
        return true;
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
    try {
      return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // not possible
      throw new RuntimeException(e);
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, 
                                                   ExecutionException,
                                                   TimeoutException {
    long timeoutInMillis = unit.toMillis(timeout);
    long startTime = 0;
    if (timeoutInMillis < Long.MAX_VALUE) {
      startTime = System.currentTimeMillis();
    }
    long remainingTime;
    synchronized (notifyLock) {
      remainingTime = timeoutInMillis;
      while (! isDone() && remainingTime > 0) {
        notifyLock.wait(remainingTime);
        
        if (timeoutInMillis < Long.MAX_VALUE) {
          remainingTime = timeoutInMillis - (System.currentTimeMillis() - startTime);
        }
      }
    }
    
    if (remainingTime <= 0) {
      throw new TimeoutException();
    } else if (currentState.get() == State.Canceled) {
      throw new CancellationException();
    } else if (failure != null) {
      throw new ExecutionException(failure);
    } else {
      return result;
    }
  }
}
