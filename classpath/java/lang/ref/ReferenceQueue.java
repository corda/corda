package java.lang.ref;

public class ReferenceQueue<T> {
  private Reference<? extends T> front;
  private Reference<? extends T> rear;

  public Reference<? extends T> poll() {
    Reference<? extends T> r = front;
    if (front != null) {
      if (front == front.jNext) {
        front = rear = null;
      } else {
        front = front.jNext;
      }
    }
    return r;
  }

  void add(Reference<? extends T> r) {
    r.jNext = r;
    if (front == null) {
      front = r;
    } else {
      rear.jNext = r;
    }
    rear = r;
  }
}
