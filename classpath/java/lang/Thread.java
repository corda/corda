/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.util.Map;
import java.util.WeakHashMap;

public class Thread implements Runnable {
  private long peer;
  private final Runnable task;
  private Map<ThreadLocal, Object> locals;
  private boolean interrupted;
  private Object sleepLock;
  private ClassLoader classLoader;
  private String name;

  public Thread(Runnable task, String name) {
    this(task);
    this.name = name;

    Thread current = currentThread();

    Map<ThreadLocal, Object> map = current.locals;
    if (map != null) {
      for (Map.Entry<ThreadLocal, Object> e: map.entrySet()) {
        if (e.getKey() instanceof InheritableThreadLocal) {
          InheritableThreadLocal itl = (InheritableThreadLocal) e.getKey();
          locals().put(itl, itl.childValue(e.getValue()));
        }
      }
    }

    classLoader = current.classLoader;
  }

  public Thread(Runnable task) {
    this(task, "Thread["+task+"]");
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

  public ClassLoader getContextClassLoader() {
    return classLoader;
  }

  public void setContextClassLoader(ClassLoader v) {
    classLoader = v;
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

  public StackTraceElement[] getStackTrace() {
    return Throwable.resolveTrace(getStackTrace(peer));
  }

  private static native Object getStackTrace(long peer);

  public static native int activeCount();

  public static native int enumerate(Thread[] array);
  
  public String getName() {
    return name;
  }
  
}
