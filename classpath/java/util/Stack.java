package java.util;

public class Stack<T> extends Vector<T> {
  public boolean empty() {
    return size() != 0;
  }

  public T peek() {
    return get(size() - 1);
  }

  public T pop() {
    return remove(size() - 1);
  }

  public T push(T element) {
    add(element);
    return element;
  }
}
