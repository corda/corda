import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerTest {
  private static void runTest(final boolean increment, 
                              final int threadCount, 
                              final int iterationsPerThread) {
    final AtomicInteger result = new AtomicInteger();
    final AtomicInteger threadDoneCount = new AtomicInteger();
    // only using an AtomicBoolean here so I don't need two variables to do the synchronize/wait/notify
    final AtomicBoolean threadsStart = new AtomicBoolean(false);
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            try {
              waitTillReady();
            } catch (InterruptedException e) {
              // let thread exit
              return;
            }
            doOperation();
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
        
        private void doOperation() {
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
      }).start();
    }
    
    synchronized (threadsStart) {
      threadsStart.set(true);
      
      threadsStart.notifyAll();
    }
    
    synchronized (threadDoneCount) {
      while (threadDoneCount.get() < threadCount) {
        try {
          threadDoneCount.wait();
        } catch (InterruptedException e) {
          // let thread exit
          return;
        }
      }
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
  
  public static void main(String[] args) {
    runTest(true, 10, 100);
    runTest(false, 10, 100);
  }
}
