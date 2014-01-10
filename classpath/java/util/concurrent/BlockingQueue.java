package java.util.concurrent;

import java.util.Collection;
import java.util.Queue;

public interface BlockingQueue<T> extends Queue<T> {
  public void put(T e) throws InterruptedException;
  
  public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException;
  
  public T take() throws InterruptedException;
  
  public T poll(long timeout, TimeUnit unit) throws InterruptedException;
  
  public int remainingCapacity();
  
  public int drainTo(Collection<? super T> c);
  
  public int drainTo(Collection<? super T> c, int maxElements);
}
