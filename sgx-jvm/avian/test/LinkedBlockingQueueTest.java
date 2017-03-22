import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingQueueTest {
  private static final int DELAY_TILL_ACTION = 10;
  
  public static void main(String[] args) throws InterruptedException {
    remainingCapacityTest();
    QueueHelper.sizeTest(new LinkedBlockingQueue<Object>());
    QueueHelper.isEmptyTest(new LinkedBlockingQueue<Object>());
    QueueHelper.addTest(new LinkedBlockingQueue<Object>());
    addCapacityFail();
    offerTest();
    offerWithTimeoutTest();
    offerTimeoutTest();
    putTest();
    QueueHelper.addAllTest(new LinkedBlockingQueue<Object>());
    addAllFail();
    QueueHelper.elementTest(new LinkedBlockingQueue<Object>());
    QueueHelper.elementFail(new LinkedBlockingQueue<Object>());
    pollEmptyTest();
    pollTest();
    pollTimeoutTest();
    takeTest();
    QueueHelper.removeEmptyFail(new LinkedBlockingQueue<Object>());
    QueueHelper.removeTest(new LinkedBlockingQueue<Object>());
    drainToTest();
    drainToLimitTest();
    QueueHelper.containsTest(new LinkedBlockingQueue<Object>());
    QueueHelper.containsAllTest(new LinkedBlockingQueue<Object>());
    QueueHelper.removeObjectTest(new LinkedBlockingQueue<Object>());
    QueueHelper.removeAllTest(new LinkedBlockingQueue<Object>());
    QueueHelper.clearTest(new LinkedBlockingQueue<Object>());
    QueueHelper.toArrayTest(new LinkedBlockingQueue<Object>());
  }
  
  private static void verify(boolean val) {
    if (! val) {
      throw new RuntimeException();
    }
  }
  
  private static void remainingCapacityTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(2);
    verify(lbq.remainingCapacity() == 2);

    lbq.add(new Object());
    verify(lbq.remainingCapacity() == 1);
  }
  
  private static void addCapacityFail() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    Object testObject = new Object();
    lbq.add(testObject);
    
    try {
      lbq.add(new Object());
      throw new RuntimeException("Exception should have thrown");
    } catch (IllegalStateException e) {
      // expected
    }
    
    verify(lbq.size() == 1);
    verify(lbq.peek() == testObject);
  }
  
  private static void offerTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    Object testObject = new Object();
    verify(lbq.offer(testObject));
    verify(! lbq.offer(new Object()));
    
    verify(lbq.size() == 1);
    verify(lbq.peek() == testObject);
  }
  
  private static void offerWithTimeoutTest() throws InterruptedException {
    final LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    lbq.add(new Object());
    
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          // sleep to make sure offer call starts first
          Thread.sleep(DELAY_TILL_ACTION);
          lbq.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }).start();
    
    // should accept once thread starts
    verify(lbq.offer(new Object(), 10, TimeUnit.SECONDS));
  }
  
  private static void offerTimeoutTest() throws InterruptedException {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    lbq.add(new Object());
    
    verify(! lbq.offer(new Object(), 10, TimeUnit.MILLISECONDS));
  }
  
  private static void putTest() throws InterruptedException {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.put(testObject);
    
    verify(lbq.size() == 1);
    verify(lbq.peek() == testObject);
  }
  
  private static void addAllFail() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    LinkedList<Object> toAdd = new LinkedList<Object>();
    toAdd.add(new Object());
    toAdd.add(new Object());
    
    try {
      lbq.addAll(toAdd);
      throw new RuntimeException("Exception should have thrown");
    } catch (IllegalStateException e) {
      // expected
    }
  }
  
  private static void pollEmptyTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    
    verify(lbq.poll() == null);
  }
  
  private static void pollTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    verify(lbq.poll() == testObject);
  }
  
  private static void pollTimeoutTest() throws InterruptedException {
    final LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    final Object testObject = new Object();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(DELAY_TILL_ACTION);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        
        lbq.add(testObject);
      }
    }).start();

    
    verify(lbq.poll(DELAY_TILL_ACTION * 10, TimeUnit.MILLISECONDS) == testObject);
  }
  
  private static void takeTest() throws InterruptedException {
    final LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    final Object testObject = new Object();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(DELAY_TILL_ACTION);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        
        lbq.add(testObject);
      }
    }).start();

    
    verify(lbq.take() == testObject);
  }
  
  private static void drainToTest() {
    int objQty = 2;
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    for (int i = 0; i < objQty; i++) {
      lbq.add(new Object());
    }
    
    LinkedList<Object> drainToResult = new LinkedList<Object>();
    verify(lbq.drainTo(drainToResult) == objQty);
    verify(drainToResult.size() == objQty);
  }
  
  private static void drainToLimitTest() {
    int objQty = 4;
    int limit = 2;
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    for (int i = 0; i < objQty; i++) {
      lbq.add(new Object());
    }
    
    LinkedList<Object> drainToResult = new LinkedList<Object>();
    verify(lbq.drainTo(drainToResult, limit) == limit);
    verify(drainToResult.size() == limit);
    verify(lbq.size() == objQty - limit);
  }
}
