import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class ArrayDequeTest {
  private static void verify(boolean val) {
    if (! val) {
      throw new RuntimeException();
    }
  }
  
  public static void main(String[] args) throws InterruptedException {
    QueueTest.sizeTest(new ArrayDeque<Object>());
    QueueTest.isEmptyTest(new ArrayDeque<Object>());
    QueueTest.addTest(new ArrayDeque<Object>());
    QueueTest.addAllTest(new ArrayDeque<Object>());
    QueueTest.elementTest(new ArrayDeque<Object>());
    QueueTest.elementFail(new ArrayDeque<Object>());
    QueueTest.removeEmptyFail(new ArrayDeque<Object>());
    QueueTest.removeTest(new ArrayDeque<Object>());
    QueueTest.containsTest(new ArrayDeque<Object>());
    QueueTest.containsAllTest(new ArrayDeque<Object>());
    QueueTest.removeObjectTest(new ArrayDeque<Object>());
    QueueTest.removeAllTest(new ArrayDeque<Object>());
    QueueTest.clearTest(new ArrayDeque<Object>());
    QueueTest.toArrayTest(new ArrayDeque<Object>());
    
    DequeTest.addFirstTest(new ArrayDeque<Object>());
    DequeTest.addLastTest(new ArrayDeque<Object>());
    DequeTest.removeFirstTest(new ArrayDeque<Object>());
    DequeTest.removeLastTest(new ArrayDeque<Object>());
    
    iterateTest(false);
    iterateTest(true);
    iteratorRemoveTest(false);
    iteratorRemoveTest(true);
    iteratorNoElementFail(false);
    iteratorNoElementFail(true);
  }
  
  private static void iterateTest(boolean desc) {
    int testQty = 10;
    LinkedList<Object> compareList = new LinkedList<Object>();
    ArrayDeque<Object> ad = new ArrayDeque<Object>();
    
    for (int i = 0; i < testQty; i++) {
      Object o = new Object();
      compareList.add(o);
      ad.add(o);
    }
    
    Iterator<Object> compIt;
    Iterator<Object> testIt;
    if (desc) {
      compIt = compareList.descendingIterator();
      testIt = ad.descendingIterator();
    } else {
      compIt = compareList.iterator();
      testIt = ad.iterator();
    }
    while (testIt.hasNext()) {
      verify(testIt.next() == compIt.next());
    }
    
    // remove from the front
    compareList.removeFirst();
    ad.removeFirst();

    if (desc) {
      compIt = compareList.descendingIterator();
      testIt = ad.descendingIterator();
    } else {
      compIt = compareList.iterator();
      testIt = ad.iterator();
    }
    while (testIt.hasNext()) {
      verify(testIt.next() == compIt.next());
    }
    
    // remove from the end
    compareList.removeLast();
    ad.removeLast();

    if (desc) {
      compIt = compareList.descendingIterator();
      testIt = ad.descendingIterator();
    } else {
      compIt = compareList.iterator();
      testIt = ad.iterator();
    }
    while (testIt.hasNext()) {
      verify(testIt.next() == compIt.next());
    }
  }
  
  private static void iteratorRemoveTest(boolean desc) {
    int testQty = 20;
    LinkedList<Object> compareList = new LinkedList<Object>();
    ArrayDeque<Object> ad = new ArrayDeque<Object>();
    
    for (int i = 0; i < testQty; i++) {
      Object o = new Object();
      compareList.add(o);
      ad.add(o);
    }

    Iterator<Object> compIt;
    Iterator<Object> testIt;
    if (desc) {
      compIt = compareList.descendingIterator();
      testIt = ad.descendingIterator();
    } else {
      compIt = compareList.iterator();
      testIt = ad.iterator();
    }
    boolean flip = true;  // start with true to ensure first is removed
    while (testIt.hasNext()) {
      // advance iterators
      testIt.next();
      compIt.next();
      
      if (flip || ! testIt.hasNext()) {
        compIt.remove();
        testIt.remove();
        flip = false;
      } else {
        flip = true;
      }
    }

    if (desc) {
      compIt = compareList.descendingIterator();
      testIt = ad.descendingIterator();
    } else {
      compIt = compareList.iterator();
      testIt = ad.iterator();
    }
    while (testIt.hasNext()) {
      verify(testIt.next() == compIt.next());
    }
  }
  
  private static void iteratorNoElementFail(boolean desc) {
    ArrayDeque<Object> ad = new ArrayDeque<Object>();
    
    Iterator<Object> testIt;
    if (desc) {
      testIt = ad.descendingIterator();
    } else {
      testIt = ad.iterator();
    }
    
    try {
      testIt.next();
      throw new RuntimeException("Exception should have thrown");
    } catch (NoSuchElementException e) {
      // expected
    }
  }
}
