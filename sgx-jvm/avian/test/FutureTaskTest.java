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
}
