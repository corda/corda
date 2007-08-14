package java.lang;

public class Object {
  protected native Object clone() throws CloneNotSupportedException;

  public boolean equals(Object o) {
    return this == o;
  }

  protected void finalize() { }

  public native final Class<? extends Object> getClass();

  public native int hashCode();

  public native final void notify();

  public native final void notifyAll();

  public native String toString();

  public final void wait() {
    wait(0);
  }

  public native final void wait(long timeout);
}
