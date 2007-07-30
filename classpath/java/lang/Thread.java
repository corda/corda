package java.lang;

import java.util.Map;
import java.util.WeakHashMap;

public class Thread implements Runnable {
  private long peer;
  private final Runnable task;
  private Map<ThreadLocal, Object> locals;
  private boolean interrupted;
  private Object sleepLock;

  public Thread(Runnable task) {
    this.task = task;

    Map<ThreadLocal, Object> map = currentThread().locals;
    if (map != null) {
      for (Map.Entry<ThreadLocal, Object> e: map.entrySet()) {
        if (e.getKey() instanceof InheritableThreadLocal) {
          InheritableThreadLocal itl = (InheritableThreadLocal) e.getKey();
          locals().put(itl, itl.childValue(e.getValue()));
        }
      }
    }
  }

  public synchronized void start() {
    if (peer != 0) {
      throw new IllegalStateException("thread already started");
    }

    peer = doStart();
    if (peer == 0) {
      throw new RuntimeException("unable to start native thread");
    }
  }

  private native long doStart();

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

  private static native void interrupt(long peer);

  public synchronized void interrupt() {
    if (peer != 0) {
      interrupt(peer);
    } else {
      interrupted = true;
    }
  }

  public static boolean interrupted() {
    Thread t = currentThread();
    
    synchronized (t) {
      boolean v = t.interrupted;
      t.interrupted = false;
      return v;
    }
  }

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
