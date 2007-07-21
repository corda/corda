package java.lang;

import java.util.Map;
import java.util.WeakHashMap;

public class Thread implements Runnable {
  private final Runnable task;
  private Map<ThreadLocal, Object> locals;
  private long peer;

  public Thread(Runnable task) {
    this.task = task;
  }

  public synchronized native void start();

  public void run() {
    if (task != null) {
      task.run();
    }
  }

  public Map<ThreadLocal, Object> locals() {
    if (locals == null) {
      locals = new WeakHashMap();
    }
    return locals;
  }

  public static native Thread currentThread();

  public static native void sleep(long milliseconds)
    throws InterruptedException;
}
