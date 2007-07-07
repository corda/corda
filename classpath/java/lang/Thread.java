package java.lang;

public class Thread implements Runnable {
  private final Runnable task;
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

  public static native void sleep(long milliseconds)
    throws InterruptedException;
}
