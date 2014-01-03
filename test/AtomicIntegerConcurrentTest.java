import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerConcurrentTest {
  private static void runTest(final boolean increment, 
                              final int threadCount, 
                              final int iterationsPerThread) {
    // we assume a 1ms delay per thread to try to get them all to start at the same time
    final long startTime = System.currentTimeMillis() + threadCount;
    final AtomicInteger result = new AtomicInteger();
    final AtomicInteger threadDoneCount = new AtomicInteger();
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            doOperation();
          } finally {
            synchronized (threadDoneCount) {
              threadDoneCount.incrementAndGet();
              
              threadDoneCount.notifyAll();
            }
          }
        }
        
        private void doOperation() {
          long sleepTime = System.currentTimeMillis() - startTime;
          try {
            Thread.sleep(sleepTime);
          } catch (InterruptedException e) {
            // let thread exit
            return;
          }
          
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
