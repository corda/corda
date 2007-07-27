package java.util;

public class LinkedList<T> implements List<T> {
  private Cell<T> front;
  private Cell<T> rear;
  private int size;

  private Cell<T> find(int index) {
    int i = 0;
    for (Cell<T> c = front; c != null; c = c.next) {
      if (i == index) {
        return c;
      }
      ++ i;
    }
    throw new IndexOutOfBoundsException(index + " not in [0, " + size + ")");
  }

  private static boolean equal(Object a, Object b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  private void addFirst(Cell<T> c) {
    ++ size;

    if (front == null) {
      front = rear = c;
    } else {
      c.prev = rear;
      rear = c;
    }
  }
  
  private void addLast(Cell<T> c) {
    ++ size;

    if (front == null) {
      front = rear = c;
    } else {
      c.next = front;
      front = c;
    }
  }
  
  private Cell<T> find(T element) {
    for (Cell<T> c = front; c != null; c = c.next) {
      if (equal(c.value, element)) {
        return c;
      }
    }
    return null;
  }
  
  private void remove(Cell<T> c) {
    -- size;

    if (c.prev == null) {
      front = c.next;
    } else {
      c.prev.next = c.next;
    }

    if (c.next == null) {
      rear = c.prev;
    } else {
      c.next.prev = c.prev;
    }
  }

  public int size() {
    return size;
  }

  public boolean contains(T element) {
    return find(element) != null;
  }

  public boolean add(T element) {
    addFirst(element);
    return true;
  }

  public void addFirst(T element) {
    addFirst(new Cell(element, null, null));
  }

  public void addLast(T element) {
    addLast(new Cell(element, null, null));
  }

  public T get(int index) {
    return find(index).value;
  }

  public T getFirst() {
    if (front != null) {
      return front.value;
    } else {
      throw new NoSuchElementException();
    }
  }

  public T getLast() {
    if (rear != null) {
      return rear.value;
    } else {
      throw new NoSuchElementException();
    }
  }

  public T remove(int index) {
    Cell<T> c = find(index);
    remove(c);
    return c.value;
  }

  public T removeFirst() {
    if (front != null) {
      T v = front.value;
      front = front.next;
      if (front != null) front.prev = null;
      return v;
    } else {
      throw new NoSuchElementException();
    }
  }

  public T removeLast() {
    if (rear != null) {
      T v = rear.value;
      rear = rear.prev;
      if (rear != null) rear.next = null;
      return v;
    } else {
      throw new NoSuchElementException();
    }
  }

  public boolean remove(T element) {
    Cell<T> c = find(element);
    if (c == null) {
      return false;
    } else {
      remove(c);
      return true;
    }
  }

  public void clear() {
    front = rear = null;
    size = 0;
  }

  public Iterator<T> iterator() {
    return new MyIterator(front);
  }

  private static class Cell<T> {
    public T value;
    public Cell<T> prev;
    public Cell<T> next;

    public Cell(T value, Cell<T> prev, Cell<T> next) {
      this.value = value;
      this.prev = prev;
      this.next = next;
    }
  }

  private class MyIterator implements Iterator<T> {
    private Cell<T> current;
    private Cell<T> next;

    public MyIterator(Cell<T> start) {
      next = start;
    }

    public T next() {
      if (hasNext()) {
        current = next;
        next = next.next;
        return current.value;
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      return next != null;
    }

    public void remove() {
      if (current != null) {
        LinkedList.this.remove(current);
        current = null;
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
