import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class LinkedBlockingQueueTest {
  private static final int DELAY_TILL_ACTION = 10;
  
  public static void main(String[] args) throws InterruptedException {
    remainingCapacityTest();
    sizeTest();
    isEmptyTest();
    addTest();
    addCapacityFail();
    offerTest();
    offerWithTimeoutTest();
    offerTimeoutTest();
    putTest();
    addAllTest();
    addAllFail();
    elementTest();
    elementFail();
    pollEmptyTest();
    pollTest();
    pollTimeoutTest();
    takeTest();
    removeEmptyTest();
    removeTest();
    drainToTest();
    drainToLimitTest();
    containsTest();
    containsAllTest();
    removeObjectTest();
    removeAllTest();
    clearTest();
    toArrayTest();
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
  
  private static void sizeTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    verify(lbq.size() == 0);
    
    lbq.add(new Object());
    verify(lbq.size() == 1);
  }
  
  private static void isEmptyTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    verify(lbq.isEmpty());
    
    lbq.add(new Object());
    verify(! lbq.isEmpty());
  }
  
  private static void addTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    verify(lbq.size() == 1);
    verify(lbq.peek() == testObject);
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
  
  private static void addAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    LinkedList<Object> toAdd = new LinkedList<Object>();
    toAdd.add(new Object());
    toAdd.add(new Object());
    
    lbq.addAll(toAdd);
    
    verify(lbq.size() == toAdd.size());
    while (! lbq.isEmpty()) {
      verify(lbq.remove() == toAdd.remove());
    }
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
  
  private static void elementTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    verify(lbq.element() == testObject);
  }
  
  private static void elementFail() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    
    try {
      lbq.element();
      throw new RuntimeException("Exception should have thrown");
    } catch (NoSuchElementException e) {
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

    
    verify(lbq.poll(DELAY_TILL_ACTION * 2, TimeUnit.MILLISECONDS) == testObject);
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
  
  private static void removeEmptyTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    
    try {
      lbq.remove();
      throw new RuntimeException("Exception should have thrown");
    } catch (NoSuchElementException e) {
      // expected
    }
  }
  
  private static void removeTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    verify(lbq.remove() == testObject);
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
  
  private static void containsTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    
    verify(! lbq.contains(testObject));
    
    lbq.add(testObject);
    verify(lbq.contains(testObject));
  }
  
  private static void containsAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    verify(! lbq.containsAll(testList));
    
    lbq.addAll(testList);
    verify(lbq.containsAll(testList));
  }
  
  private static void removeObjectTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    
    verify(! lbq.remove(testObject));
    
    lbq.add(testObject);
    verify(lbq.remove(testObject));
  }
  
  private static void removeAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    verify(lbq.removeAll(testList));
    
    lbq.addAll(testList);
    verify(lbq.removeAll(testList));
  }
  
  private static void clearTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    lbq.add(new Object());
    
    lbq.clear();
    
    verify(lbq.isEmpty());
  }
  
  private static void toArrayTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    
    if (lbq.toArray().length != 0) {
      throw new RuntimeException();
    }

    Object testObject = new Object();
    lbq.add(testObject);
    
    Object[] result = lbq.toArray();
    verify(result.length == 1);
    verify(result[0] == testObject);
  }
}
