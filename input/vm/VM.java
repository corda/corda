package vm;

public class VM {
  public static native Object clone(Object o);

  public static native Class<? extends Object> getClass(Object o);

  public static native int hashCode(Object o);

  public static native void notify(Object o);

  public static native void notifyAll(Object o);

  public static native String toString(Object o);

  public static native void wait(Object o);

  public static native void wait(Object o, long timeout);

  public static native void wait(Object o, long timeout, int nanos);
}
