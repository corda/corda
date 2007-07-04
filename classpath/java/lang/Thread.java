package java.lang;

public class Thread implements Runnable {
  private final Runnable task;

  public Thread(Runnable task) {
    this.task = task;
  }

  public native void start();

  public void run() {
    if (task != null) {
      task.run();
    }
  }

  public static native void sleep(long milliseconds)
    throws InterruptedException;
}
