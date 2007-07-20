import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;

public class References {
  public static void main(String[] args) {
    Object a = new Object();
    Object b = new Object();
    Object c = new Object();
    Object d = new Object();

    ReferenceQueue q = new ReferenceQueue();

    Reference ar = new WeakReference(a);
    Reference br = new WeakReference(b, q);
    Reference cr = new WeakReference(c, q);
    Reference dr = new PhantomReference(d, q);

    a = b = c = d = cr = null;
    
    System.out.println("a: " + ar.get());
    System.out.println("b: " + br.get());
    System.out.println("d: " + dr.get());

    System.gc();

    System.out.println("a: " + ar.get());
    System.out.println("b: " + br.get());
    System.out.println("d: " + dr.get());

    for (Reference r = q.poll(); r != null; r = q.poll()) {
      System.out.println("polled: " + r.get());      
    }
  }
}
