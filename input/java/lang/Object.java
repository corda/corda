package java.lang;

public class Object {
  protected native Object clone();

  public boolean equals(Object o) {
    return this == o;
  }

  protected void finalize() { }

  public native final Class<? extends Object> getClass();

  public native int hashCode();

  public native final void notify();

  public native final void notifyAll();

  public native String toString();

  public native final void wait();

  public native final void wait(long timeout);

  public native final void wait(long timeout, int nanos);
}
