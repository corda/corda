import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.TimeUnit;

public class CompletionServiceTest {
  public static void main(String args[]) throws InterruptedException, ExecutionException {
    Executor dumbExecutor = new Executor() {
      @Override
      public void execute(Runnable task) {
        new Thread(task).start();
      }
    };
    
    pollNoResultTest(dumbExecutor);
    pollTimeoutNoResultTest(dumbExecutor);
    takeTest(dumbExecutor);
  }
  
  private static void verify(boolean val) {
    if (! val) {
      throw new RuntimeException();
    }
  }
  
  private static void pollNoResultTest(Executor executor) {
    ExecutorCompletionService<Object> ecs = new ExecutorCompletionService<Object>(executor);
    
    verify(ecs.poll() == null);
  }
  
  private static void pollTimeoutNoResultTest(Executor executor) throws InterruptedException {
    long delayTime = 0;
    ExecutorCompletionService<Object> ecs = new ExecutorCompletionService<Object>(executor);
    
    long startTime = System.currentTimeMillis();
    verify(ecs.poll(delayTime, TimeUnit.MILLISECONDS) == null);
    verify(System.currentTimeMillis() - startTime >= delayTime);
  }
  
  private static void takeTest(Executor executor) throws InterruptedException, ExecutionException {
    ExecutorCompletionService<Object> ecs = new ExecutorCompletionService<Object>(executor);
    final Object result = new Object();
    ecs.submit(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        return result;
      }
    });
    
    verify(ecs.take().get() == result);
  }
}
