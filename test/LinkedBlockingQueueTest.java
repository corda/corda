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
  
  private static void remainingCapacityTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(2);
    if (lbq.remainingCapacity() != 2) {
      throw new RuntimeException();
    }

    lbq.add(new Object());
    if (lbq.remainingCapacity() != 1) {
      throw new RuntimeException();
    }
  }
  
  private static void sizeTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    if (lbq.size() != 0) {
      throw new RuntimeException();
    }
    
    lbq.add(new Object());
    if (lbq.size() != 1) {
      throw new RuntimeException();
    }
  }
  
  private static void isEmptyTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    if (! lbq.isEmpty()) {
      throw new RuntimeException();
    }
    
    lbq.add(new Object());
    if (lbq.isEmpty()) {
      throw new RuntimeException();
    }
  }
  
  private static void addTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    if (lbq.size() != 1) {
      throw new RuntimeException();
    } else if (lbq.peek() != testObject) {
      throw new RuntimeException();
    }
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
    
    if (lbq.size() != 1) {
      throw new RuntimeException();
    } else if (lbq.peek() != testObject) {
      throw new RuntimeException();
    }
  }
  
  private static void offerTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    Object testObject = new Object();
    if (! lbq.offer(testObject)) {
      throw new RuntimeException();
    }
    if (lbq.offer(new Object())) {
      throw new RuntimeException();
    }
    
    if (lbq.size() != 1) {
      throw new RuntimeException();
    } else if (lbq.peek() != testObject) {
      throw new RuntimeException();
    }
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
    if (! lbq.offer(new Object(), 10, TimeUnit.SECONDS)) {
      throw new RuntimeException();
    }
  }
  
  private static void offerTimeoutTest() throws InterruptedException {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>(1);
    lbq.add(new Object());
    
    if (lbq.offer(new Object(), 10, TimeUnit.MILLISECONDS)) {
      throw new RuntimeException();
    }
  }
  
  private static void putTest() throws InterruptedException {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.put(testObject);
    
    if (lbq.size() != 1) {
      throw new RuntimeException();
    } else if (lbq.peek() != testObject) {
      throw new RuntimeException();
    }
  }
  
  private static void addAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    LinkedList<Object> toAdd = new LinkedList<Object>();
    toAdd.add(new Object());
    toAdd.add(new Object());
    
    lbq.addAll(toAdd);
    
    if (lbq.size() != toAdd.size()) {
      throw new RuntimeException();
    }
    while (! lbq.isEmpty()) {
      if (lbq.remove() != toAdd.remove()) {
        throw new RuntimeException();
      }
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
    
    if (lbq.element() != testObject) {
      throw new RuntimeException();
    }
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
    
    if (lbq.poll() != null) {
      throw new RuntimeException();
    }
  }
  
  private static void pollTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    if (lbq.poll() != testObject) {
      throw new RuntimeException();
    }
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

    
    if (lbq.poll(DELAY_TILL_ACTION * 2, TimeUnit.MILLISECONDS) != testObject) {
      throw new RuntimeException();
    }
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

    
    if (lbq.take() != testObject) {
      throw new RuntimeException();
    }
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
    
    if (lbq.remove() != testObject) {
      throw new RuntimeException();
    }
  }
  
  private static void drainToTest() {
    int objQty = 2;
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    for (int i = 0; i < objQty; i++) {
      lbq.add(new Object());
    }
    
    LinkedList<Object> drainToResult = new LinkedList<Object>();
    if (lbq.drainTo(drainToResult) != objQty) {
      throw new RuntimeException();
    } else if (drainToResult.size() != objQty) {
      throw new RuntimeException();
    }
  }
  
  private static void drainToLimitTest() {
    int objQty = 4;
    int limit = 2;
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    for (int i = 0; i < objQty; i++) {
      lbq.add(new Object());
    }
    
    LinkedList<Object> drainToResult = new LinkedList<Object>();
    if (lbq.drainTo(drainToResult, limit) != limit) {
      throw new RuntimeException();
    } else if (drainToResult.size() != limit) {
      throw new RuntimeException();
    } else if (lbq.size() != objQty - limit) {
      throw new RuntimeException();
    }
  }
  
  private static void containsTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    
    if (lbq.contains(testObject)) {
      throw new RuntimeException();
    }
    
    lbq.add(testObject);
    if (! lbq.contains(testObject)) {
      throw new RuntimeException();
    }
  }
  
  private static void containsAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    if (lbq.containsAll(testList)) {
      throw new RuntimeException();
    }
    
    lbq.addAll(testList);
    if (! lbq.containsAll(testList)) {
      throw new RuntimeException();
    }
  }
  
  private static void removeObjectTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    
    if (lbq.remove(testObject)) {
      throw new RuntimeException();
    }
    
    lbq.add(testObject);
    if (! lbq.remove(testObject)) {
      throw new RuntimeException();
    }
  }
  
  private static void removeAllTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    Object testObject = new Object();
    lbq.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    if (! lbq.removeAll(testList)) {
      throw new RuntimeException();
    }
    
    lbq.addAll(testList);
    if (! lbq.removeAll(testList)) {
      throw new RuntimeException();
    }
  }
  
  private static void clearTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    lbq.add(new Object());
    
    lbq.clear();
    
    if (! lbq.isEmpty()) {
      throw new RuntimeException();
    }
  }
  
  private static void toArrayTest() {
    LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
    
    if (lbq.toArray().length != 0) {
      throw new RuntimeException();
    }

    Object testObject = new Object();
    lbq.add(testObject);
    
    Object[] result = lbq.toArray();
    if (result.length != 1) {
      throw new RuntimeException();
    } else if (result[0] != testObject) {
      throw new RuntimeException();
    }
  }
}
