import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class AtomicTests {
  private static final int threadCount = 10;
  private static final int iterationsPerThread = 100;
  
  public static void main(String[] args) {
    runAtomicIntegerTest(true);
    runAtomicIntegerTest(false);
    
    runAtomicLongTest(true);
    runAtomicLongTest(false);
    
    runAtomicReferenceTest();
  }
  
  private static void blockTillThreadsDone(AtomicInteger threadDoneCount) throws InterruptedException {
    synchronized (threadDoneCount) {
      while (threadDoneCount.get() < threadCount) {
        threadDoneCount.wait();
      }
    }
  }
  
  private static void runAtomicIntegerTest(final boolean increment) {
    final AtomicInteger result = new AtomicInteger();
    final AtomicInteger threadDoneCount = new AtomicInteger();
    // only using an AtomicBoolean here so I don't need two variables to do the synchronize/wait/notify
    final AtomicBoolean threadsStart = new AtomicBoolean(false);
    
    Runnable operationRunnable = new Runnable() {
      @Override
      public void run() {
        boolean flip = true;
        for (int i = 0; i < iterationsPerThread; i++) {
          if (flip) {
            if (increment) {
              result.incrementAndGet();
            } else {
              result.decrementAndGet();
            }
            flip = false;
          } else {
            if (increment) {
              result.getAndIncrement();
            } else {
              result.getAndDecrement();
            }
            flip = true;
          }
        }
      }
    };
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new DelayedRunnable(threadsStart, 
                                     operationRunnable, 
                                     threadDoneCount)).start();
    }
    
    synchronized (threadsStart) {
      threadsStart.set(true);
      
      threadsStart.notifyAll();
    }
    
    try {
      blockTillThreadsDone(threadDoneCount);
    } catch (InterruptedException e) {
      // let thread exit
      return;
    }
    
    int expectedResult = threadCount * iterationsPerThread;
    if (! increment) {
      expectedResult *= -1;
    }
    int resultValue = result.get();
    if (resultValue != expectedResult) {
      throw new IllegalStateException(resultValue + " != " + expectedResult);
    }
  }
  
  private static void runAtomicLongTest(final boolean increment) {
    final AtomicLong result = new AtomicLong();
    final AtomicInteger threadDoneCount = new AtomicInteger();
    // only using an AtomicBoolean here so I don't need two variables to do the synchronize/wait/notify
    final AtomicBoolean threadsStart = new AtomicBoolean(false);
    
    Runnable operationRunnable = new Runnable() {
      @Override
      public void run() {
        boolean flip = true;
        for (int i = 0; i < iterationsPerThread; i++) {
          if (flip) {
            if (increment) {
              result.incrementAndGet();
            } else {
              result.decrementAndGet();
            }
            flip = false;
          } else {
            if (increment) {
              result.getAndIncrement();
            } else {
              result.getAndDecrement();
            }
            flip = true;
          }
        }
      }
    };
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new DelayedRunnable(threadsStart, 
                                     operationRunnable, 
                                     threadDoneCount)).start();
    }
    
    synchronized (threadsStart) {
      threadsStart.set(true);
      
      threadsStart.notifyAll();
    }
    
    try {
      blockTillThreadsDone(threadDoneCount);
    } catch (InterruptedException e) {
      // let thread exit
      return;
    }
    
    long expectedResult = threadCount * iterationsPerThread;
    if (! increment) {
      expectedResult *= -1;
    }
    long resultValue = result.get();
    if (resultValue != expectedResult) {
      throw new IllegalStateException(resultValue + " != " + expectedResult);
    }
  }
  
  private static void runAtomicReferenceTest() {
    final AtomicReference<Integer> result = new AtomicReference<Integer>(0);
    final AtomicInteger threadDoneCount = new AtomicInteger(0);
    // only using an AtomicBoolean here so I don't need two variables to do the synchronize/wait/notify
    final AtomicBoolean threadsStart = new AtomicBoolean(false);
    
    Runnable operationRunnable = new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < iterationsPerThread; i++) {
          Integer current = result.get();
          while (! result.compareAndSet(current, current + 1)) {
            current = result.get();
          }
        }
      }
    };
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new DelayedRunnable(threadsStart, 
                                     operationRunnable, 
                                     threadDoneCount)).start();
    }
    
    synchronized (threadsStart) {
      threadsStart.set(true);
      
      threadsStart.notifyAll();
    }
    
    try {
      blockTillThreadsDone(threadDoneCount);
    } catch (InterruptedException e) {
      // let thread exit
      return;
    }
    
    long expectedResult = threadCount * iterationsPerThread;
    Integer resultValue = result.get();
    if (resultValue != expectedResult) {
      throw new IllegalStateException(resultValue + " != " + expectedResult);
    }
  }
  
  private static class DelayedRunnable implements Runnable {
    private final AtomicBoolean threadsStart;
    private final Runnable operationRunnable;
    private final AtomicInteger threadDoneCount;
    
    private DelayedRunnable(AtomicBoolean threadsStart, 
                            Runnable operationRunnable, 
                            AtomicInteger threadDoneCount) {
      this.threadsStart = threadsStart;
      this.operationRunnable = operationRunnable;
      this.threadDoneCount = threadDoneCount;
    }
    
    @Override
    public void run() {
      try {
        try {
          waitTillReady();
        } catch (InterruptedException e) {
          // let thread exit
          return;
        }
        operationRunnable.run();
      } finally {
        synchronized (threadDoneCount) {
          threadDoneCount.incrementAndGet();
          
          threadDoneCount.notifyAll();
        }
      }
    }
    
    private void waitTillReady() throws InterruptedException {
      synchronized (threadsStart) {
        while (! threadsStart.get()) {
          threadsStart.wait();
        }
      }
    }
  }
}
