import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicReferenceTest {
  private static void runTest(final int threadCount, 
                              final int iterationsPerThread) {
    final AtomicReference<Integer> result = new AtomicReference<Integer>(0);
    final AtomicInteger threadDoneCount = new AtomicInteger(0);
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
          for (int i = 0; i < iterationsPerThread; i++) {
            Integer current = result.get();
            while (! result.compareAndSet(current, current + 1)) {
              current = result.get();
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
