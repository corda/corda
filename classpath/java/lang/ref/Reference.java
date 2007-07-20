package java.lang.ref;

public abstract class Reference<T> {
  private T target;
  private ReferenceQueue<? super T> queue;
  private Object vmNext;
  Reference<? extends T> next;

  protected Reference(T target, ReferenceQueue<? super T> queue) {
    this.target = target;
    this.queue = queue;
  }

  public T get() {
    return target;
  }

  public void clear() {
    target = 0;
  }

  public boolean isEnqueued() {
    return next != null;
  }

  public boolean enqueue() {
    if (queue != null) {
      queue.add(this);
      queue = null;
      return true;
    } else {
      return false;
    }
  }
}
