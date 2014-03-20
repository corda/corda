import java.util.Deque;

public class DequeHelper {
  private static void verify(boolean val) {
    if (! val) {
      throw new RuntimeException();
    }
  }
  
  public static void main(String args[]) {
    // prevents unit test failure
  }
  
  public static void addFirstTest(Deque<Object> q) {
    Object firstObject = new Object();
    Object lastObject = new Object();
    q.addFirst(lastObject);
    q.addFirst(firstObject);
    
    verify(q.size() == 2);
    verify(q.peekFirst() == firstObject);
    verify(q.peekLast() == lastObject);
  }
  
  public static void addLastTest(Deque<Object> q) {
    Object firstObject = new Object();
    Object lastObject = new Object();
    q.addLast(firstObject);
    q.addLast(lastObject);
    
    verify(q.size() == 2);
    verify(q.peekFirst() == firstObject);
    verify(q.peekLast() == lastObject);
  }
  
  public static void removeFirstTest(Deque<Object> q) {
    Object firstObject = new Object();
    Object lastObject = new Object();
    q.addLast(firstObject);
    q.addLast(lastObject);
    
    verify(q.removeFirst() == firstObject);
    verify(q.removeFirst() == lastObject);
  }
  
  public static void removeLastTest(Deque<Object> q) {
    Object firstObject = new Object();
    Object lastObject = new Object();
    q.addLast(firstObject);
    q.addLast(lastObject);

    verify(q.removeLast() == lastObject);
    verify(q.removeLast() == firstObject);
  }
}
