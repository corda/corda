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
    QueueHelper.sizeTest(new ArrayDeque<Object>());
    QueueHelper.isEmptyTest(new ArrayDeque<Object>());
    QueueHelper.addTest(new ArrayDeque<Object>());
    QueueHelper.addAllTest(new ArrayDeque<Object>());
    QueueHelper.elementTest(new ArrayDeque<Object>());
    QueueHelper.elementFail(new ArrayDeque<Object>());
    QueueHelper.removeEmptyFail(new ArrayDeque<Object>());
    QueueHelper.removeTest(new ArrayDeque<Object>());
    QueueHelper.containsTest(new ArrayDeque<Object>());
    QueueHelper.containsAllTest(new ArrayDeque<Object>());
    QueueHelper.removeObjectTest(new ArrayDeque<Object>());
    QueueHelper.removeAllTest(new ArrayDeque<Object>());
    QueueHelper.clearTest(new ArrayDeque<Object>());
    QueueHelper.toArrayTest(new ArrayDeque<Object>());
    
    DequeHelper.addFirstTest(new ArrayDeque<Object>());
    DequeHelper.addLastTest(new ArrayDeque<Object>());
    DequeHelper.removeFirstTest(new ArrayDeque<Object>());
    DequeHelper.removeLastTest(new ArrayDeque<Object>());
    
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
