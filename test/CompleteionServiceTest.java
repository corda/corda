import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.TimeUnit;

public class CompleteionServiceTest {
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
  
  private static void pollNoResultTest(Executor executor) {
    ExecutorCompletionService<Object> ecs = new ExecutorCompletionService<Object>(executor);
    
    if (ecs.poll() != null) {
      throw new RuntimeException();
    }
  }
  
  private static void pollTimeoutNoResultTest(Executor executor) throws InterruptedException {
    long delayTime = 0;
    ExecutorCompletionService<Object> ecs = new ExecutorCompletionService<Object>(executor);
    
    long startTime = System.currentTimeMillis();
    if (ecs.poll(delayTime, TimeUnit.MILLISECONDS) != null) {
      throw new RuntimeException();
    }
    if (System.currentTimeMillis() - startTime < delayTime) {
      throw new RuntimeException();
    }
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
    
    if (ecs.take().get() != result) {
      throw new RuntimeException();
    }
  }
}
