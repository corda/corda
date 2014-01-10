package java.util.concurrent;

public interface ThreadFactory {
  public Thread newThread(Runnable r);
}
