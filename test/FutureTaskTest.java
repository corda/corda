import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTaskTest {
  private static final int DELAY_TIME = 10;
  
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    isDoneTest(false);
    isDoneTest(true);
    getCallableResultTest();
    getRunnableResultTest();
    getTimeoutFail();
    getExecutionExceptionTest();
  }
  
  private static void isDoneTest(final boolean throwException) {
    RunnableFuture<?> future = new FutureTask<Object>(new Runnable() {
      @Override
      public void run() {
        if (throwException) {
          throw new RuntimeException();
        }
      }
    }, null);
    
    // should finish the future
    future.run();
    
    if (! future.isDone()) {
      throw new RuntimeException("Future should be done");
    }
  }
  
  private static void getCallableResultTest() throws InterruptedException, ExecutionException {
    final Object result = new Object();
    FutureTask<Object> future = new FutureTask<Object>(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        return result;
      }
    });
    
    future.run();
    if (future.get() != result) {
      throw new RuntimeException("Bad result returned: " + future.get());
    }
  }
  
  private static void getRunnableResultTest() throws InterruptedException, ExecutionException {
    final Object result = new Object();
    FutureTask<Object> future = new FutureTask<Object>(new Runnable() {
      @Override
      public void run() {
        // nothing here
      }
    }, result);
    
    future.run();
    if (future.get() != result) {
      throw new RuntimeException("Bad result returned: " + future.get());
    }
  }

  private static void getTimeoutFail() throws InterruptedException, 
                                              ExecutionException {
    RunnableFuture<?> future = new FutureTask<Object>(new Runnable() {
      @Override
      public void run() {
        // wont run
      }
    }, null);
    
    long startTime = System.currentTimeMillis();
    try {
      future.get(DELAY_TIME, TimeUnit.MILLISECONDS);
      throw new RuntimeException("Exception should have been thrown");
    } catch (TimeoutException e) {
      long catchTime = System.currentTimeMillis();
      if (catchTime - startTime < DELAY_TIME) {
        throw new RuntimeException("get with timeout did not block long enough");
      }
    }
  }
  
  private static void getExecutionExceptionTest() throws InterruptedException, ExecutionException {
    FutureTask<Object> future = new FutureTask<Object>(new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException();
      }
    }, null);
    
    future.run();
    try {
      future.get();
      throw new RuntimeException("Exception should have thrown");
    } catch (ExecutionException e) {
      // expected
    }
  }
}
