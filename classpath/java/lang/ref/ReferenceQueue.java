package java.lang.ref;

public abstract class ReferenceQueue<T> {
  private Reference<? extends T> front;
  private Reference<? extends T> rear;

  public Reference<? extends T> poll() {
    Reference<? extends T> r = front;
    if (front != null) {
      if (front == front.next) {
        front = rear = null;
      } else {
        front = front.next;
      }
    }
    return r;
  }

  void add(Reference<? extends T> r) {
    r.next = r;
    if (front == null) {
      front = rear = r;
    } else {
      rear.next = r;
      rear = r;
    }
  }
}
