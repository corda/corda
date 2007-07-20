package java.lang.ref;

public class PhantomReference<T> extends Reference<T> {
  public PhantomReference(T target, ReferenceQueue<? super T> queue) {
    super(target, queue);    
  }

  public PhantomReference(T target) {
    this(target, null);
  }
}
