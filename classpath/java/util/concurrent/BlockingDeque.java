package java.util.concurrent;

import java.util.Deque;

public interface BlockingDeque<T> extends Deque<T>, BlockingQueue<T> {
  public T takeFirst() throws InterruptedException;
  
  public T takeLast() throws InterruptedException;
  
  public T pollFirst(long timeout, TimeUnit unit) throws InterruptedException;
  
  public T pollLast(long timeout, TimeUnit unit) throws InterruptedException;
}
