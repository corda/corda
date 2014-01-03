import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicReferenceTest {
  private static void runTest(final int threadCount, 
                              final int iterationsPerThread) {
    // we assume a 1ms delay per thread to try to get them all to start at the same time
    final long startTime = System.currentTimeMillis() + threadCount + 10;
    final AtomicReference<Integer> result = new AtomicReference<Integer>(0);
    final AtomicInteger threadDoneCount = new AtomicInteger(0);
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            doOperation();
            waitTillReady();
          } finally {
            synchronized (threadDoneCount) {
              threadDoneCount.incrementAndGet();
              
              threadDoneCount.notifyAll();
            }
          }
        }
        
        private void waitTillReady() {
          long sleepTime = System.currentTimeMillis() - startTime;
          if (sleepTime > 0) {
            try {
              Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
              // let thread exit
              return;
            }
          }
        }
        
        private void doOperation() {
          for (int i = 0; i < iterationsPerThread; i++) {
            Integer current = result.get();
            while (! result.compareAndSet(current, current + 1)) {
              current = result.get();
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
    
    long expectedResult = threadCount * iterationsPerThread;
    Integer resultValue = result.get();
    if (resultValue != expectedResult) {
      throw new IllegalStateException(resultValue + " != " + expectedResult);
    }
  }
  
  public static void main(String[] args) {
    runTest(10, 100);
  }
}
