package java.lang;

import vm.VM;

public class Object {
  protected Object clone() {
    return VM.clone(this);
  }

  public boolean equals(Object o) {
    return this == o;
  }

  protected void finalize() { }

  public final Class<? extends Object> getClass() {
    return VM.getClass(this);
  }

  public int hashCode() {
    return VM.hashCode(this);
  }

  public final void notify() {
    VM.notify(this);
  }

  public final void notifyAll() {
    VM.notifyAll(this);
  }

  public String toString() {
    return VM.toString(this);
  }

  public final void wait() {
    VM.wait(this);
  }

  public final void wait(long timeout) {
    VM.wait(this, timeout);
  }

  public final void wait(long timeout, int nanos) {
    VM.wait(this, timeout, nanos);
  }
}
