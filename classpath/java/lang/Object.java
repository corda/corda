package java.lang;

public class Object {
  protected Object clone() throws CloneNotSupportedException {
    if (this instanceof Cloneable) {
      return clone(this);
    } else {
      throw new CloneNotSupportedException();
    }
  }

  private static native Object clone(Object o);

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
