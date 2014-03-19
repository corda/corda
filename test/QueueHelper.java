import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

public class QueueHelper {
  private static void verify(boolean val) {
    if (! val) {
      throw new RuntimeException();
    }
  }
  
  public static void main(String args[]) {
    // prevents unit test failure
  }
  
  public static void sizeTest(Queue<Object> q) {
    verify(q.size() == 0);
    
    q.add(new Object());
    verify(q.size() == 1);
  }
  
  public static void isEmptyTest(Queue<Object> q) {
    verify(q.isEmpty());
    
    q.add(new Object());
    verify(! q.isEmpty());
  }
  
  public static void addTest(Queue<Object> q) {
    Object testObject = new Object();
    q.add(testObject);
    
    verify(q.size() == 1);
    verify(q.peek() == testObject);
  }
  
  public static void addAllTest(Queue<Object> q) {
    LinkedList<Object> toAdd = new LinkedList<Object>();
    toAdd.add(new Object());
    toAdd.add(new Object());
    
    q.addAll(toAdd);
    
    verify(q.size() == toAdd.size());
    while (! q.isEmpty()) {
      verify(q.remove() == toAdd.remove());
    }
  }
  
  public static void elementTest(Queue<Object> q) {
    Object testObject = new Object();
    q.add(testObject);
    
    verify(q.element() == testObject);
  }
  
  public static void elementFail(Queue<Object> q) {
    try {
      q.element();
      throw new RuntimeException("Exception should have thrown");
    } catch (NoSuchElementException e) {
      // expected
    }
  }
  
  public static void removeTest(Queue<Object> q) {
    Object testObject = new Object();
    q.add(testObject);
    
    verify(q.remove() == testObject);
  }
  
  public static void removeEmptyFail(Queue<Object> q) {
    try {
      q.remove();
      throw new RuntimeException("Exception should have thrown");
    } catch (NoSuchElementException e) {
      // expected
    }
  }
  
  public static void containsTest(Queue<Object> q) {
    Object testObject = new Object();
    
    verify(! q.contains(testObject));
    
    q.add(testObject);
    verify(q.contains(testObject));
  }
  
  public static void containsAllTest(Queue<Object> q) {
    Object testObject = new Object();
    q.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    verify(! q.containsAll(testList));
    
    q.addAll(testList);
    verify(q.containsAll(testList));
  }
  
  public static void removeObjectTest(Queue<Object> q) {
    Object testObject = new Object();
    
    verify(! q.remove(testObject));
    
    q.add(testObject);
    verify(q.remove(testObject));
  }
  
  public static void removeAllTest(Queue<Object> q) {
    Object testObject = new Object();
    q.add(testObject);
    
    LinkedList<Object> testList = new LinkedList<Object>();
    testList.add(testObject);
    testList.add(new Object());
    
    verify(q.removeAll(testList));
    
    q.addAll(testList);
    verify(q.removeAll(testList));
  }
  
  public static void clearTest(Queue<Object> q) {
    q.add(new Object());
    
    q.clear();
    
    verify(q.isEmpty());
  }
  
  public static void toArrayTest(Queue<Object> q) {
    if (q.toArray().length != 0) {
      throw new RuntimeException();
    }

    Object testObject = new Object();
    q.add(testObject);
    
    Object[] result = q.toArray();
    verify(result.length == 1);
    verify(result[0] == testObject);
  }
}
