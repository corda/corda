package java.lang;

import java.util.Map;
import java.util.WeakHashMap;

public class Thread implements Runnable {
  private final Runnable task;
  private Map<ThreadLocal, Object> locals;
  private Object sleepLock;
  private long peer;

  public Thread(Runnable task) {
    this.task = task;
  }

  public synchronized void start() {
    Map<ThreadLocal, Object> map = currentThread().locals;
    if (map != null) {
      for (Map.Entry<ThreadLocal, Object> e: map.entrySet()) {
        if (e.getKey() instanceof InheritableThreadLocal) {
          InheritableThreadLocal itl = (InheritableThreadLocal) e.getKey();
          locals().put(itl, itl.childValue(e.getValue()));
        }
      }
    }

    doStart();
  }

  private native void doStart();

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

  public static void sleep(long milliseconds) throws InterruptedException {
    Thread t = currentThread();
    if (t.sleepLock == null) {
      t.sleepLock = new Object();
    }
    synchronized (t.sleepLock) {
      t.sleepLock.wait(milliseconds);
    }
  }
}
