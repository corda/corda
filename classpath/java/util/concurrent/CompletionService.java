package java.util.concurrent;

public interface CompletionService<T> {
  public Future<T> submit(Callable<T> task);
  
  public Future<T> submit(Runnable task, T result);
  
  public Future<T> take() throws InterruptedException;
  
  public Future<T> poll();
  
  public Future<T> poll(long timeout, TimeUnit unit) throws InterruptedException;
}
