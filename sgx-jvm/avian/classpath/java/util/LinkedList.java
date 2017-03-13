/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class LinkedList<T> extends AbstractSequentialList<T> implements Deque<T> {
  private Cell<T> front;
  private Cell<T> rear;
  private int size;

  public LinkedList(Collection<? extends T> c) {
    addAll(c);
  }

  public LinkedList() { }

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
      c.next = front;
      front.prev = c;
      front = c;
    }
  }
  
  private void addLast(Cell<T> c) {
    ++ size;

    if (front == null) {
      front = rear = c;
    } else {
      c.prev = rear;
      rear.next = c;
      rear = c;
    }
  }
  
  private Cell<T> find(Object element) {
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

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean contains(Object element) {
    return find(element) != null;
  }

  @Override
  public int indexOf(Object element) {
    int i = 0;
    for (Cell<T> c = front; c != null; c = c.next) {
      if (equal(c.value, element)) {
        return i;
      }
      ++ i;
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object element) {
    int i = size;
    for (Cell<T> c = rear; c != null; c = c.prev) {
      -- i;
      if (equal(c.value, element)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean offer(T element) {
    return add(element);
  }

  @Override
  public boolean add(T element) {
    addLast(element);
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends T> collection) {
    for (T t: collection) add(t);
    return true;
  }

  @Override
  public void add(int index, T element) {
    if (index == 0) {
      addFirst(element);
    } else {
      Cell<T> cell = find(index);
      Cell<T> newCell = new Cell(element, cell.prev, cell);
      cell.prev.next = newCell;
    }
  }

  @Override
  public boolean offerFirst(T e) {
    addFirst(e);
    
    return true;
  }

  @Override
  public void push(T e) {
    addFirst(e);
  }

  @Override
  public void addFirst(T element) {
    addFirst(new Cell(element, null, null));
  }

  @Override
  public boolean offerLast(T e) {
    addLast(e);
    
    return true;
  }

  @Override
  public void addLast(T element) {
    addLast(new Cell(element, null, null));
  }

  public T get(int index) {
    return find(index).value;
  }

  @Override
  public T set(int index, T value) {
    Cell<T> c = find(index);
    T old = c.value;
    c.value = value;
    return old;
  }

  @Override
  public T peek() {
    return peekFirst();
  }

  @Override
  public T peekFirst() {
    if (front != null) {
      return front.value;
    } else {
      return null;
    }
  }

  @Override
  public T getFirst() {
    if (front != null) {
      return front.value;
    } else {
      throw new NoSuchElementException();
    }
  }
  
  @Override
  public T peekLast() {
    if (rear != null) {
      return rear.value;
    } else {
      return null;
    }
  }

  @Override
  public T getLast() {
    if (rear != null) {
      return rear.value;
    } else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public T remove(int index) {
    Cell<T> c = find(index);
    remove(c);
    return c.value;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public T poll() {
    return pollFirst();
  }

  @Override
  public T pollFirst() {
    if (front != null) {
      T v = front.value;
      remove(front);
      return v;
    } else {
      return null;
    }
  }

  @Override
  public T removeFirst() {
    T result = pollFirst();
    
    if (result == null) {
      throw new NoSuchElementException();
    } else {
      return result;
    }
  }

  @Override
  public T pop() {
    return removeFirst();
  }

  @Override
  public T remove() {
    return removeFirst();
  }

  @Override
  public T pollLast() {
    if (rear != null) {
      T v = rear.value;
      remove(rear);
      return v;
    } else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public T removeLast() {
    T result = pollLast();
    
    if (result == null) {
      throw new NoSuchElementException();
    } else {
      return result;
    }
  }

  @Override
  public boolean remove(Object element) {
    Cell<T> c = find(element);
    if (c == null) {
      return false;
    } else {
      remove(c);
      return true;
    }
  }

  @Override
  public void clear() {
    front = rear = null;
    size = 0;
  }

  @Override
  public Iterator<T> iterator() {
    return listIterator();
  }

  @Override
  public ListIterator<T> listIterator() {
    return listIterator(0);
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    MyIterator it = new MyIterator();
    for (int i = 0; i < index; ++i) {
      it.next();
    }
    return it;
  }

  @Override
  public Iterator<T> descendingIterator() {
    final ListIterator<T> li = listIterator(size());
    
    return new Iterator<T>() {
      @Override
      public T next() {
        return li.previous();
      }

      @Override
      public boolean hasNext() {
        return li.hasPrevious();
      }

      @Override
      public void remove() {
        li.remove();
      }
    };
  }

  @Override
  public String toString() {
    return avian.Data.toString(this);
  }

  @Override
  public T element() {
    T result = peek();
    if (result == null) {
      throw new NoSuchElementException();
    } else {
      return result;
    }
  }

  @Override
  public boolean removeFirstOccurrence(Object o) {
    int index = indexOf(o);
    if (index > 0) {
      remove(index);
      
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    int lastIndex = lastIndexOf(o);
    if (lastIndex > 0) {
      remove(lastIndex);
      
      return true;
    } else {
      return false;
    }
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

  private class MyIterator implements ListIterator<T> {
    private Cell<T> toRemove;
    private Cell<T> current;

    public T previous() {
      if (hasPrevious()) {
        T v = current.value;
        toRemove = current;
        current = current.prev;
        return v;
      } else {
        throw new NoSuchElementException();
      }
    }

    public T next() {
      if (hasNext()) {
        if (current == null) {
          current = front;
        } else {
          current = current.next;
        }
        toRemove = current;
        return current.value;
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      if (current == null) {
        return front != null;
      } else {
        return current.next != null;
      }
    }

    public boolean hasPrevious() {
      return current != null;
    }

    public void remove() {
      if (toRemove != null) {
        current = toRemove.prev;
        LinkedList.this.remove(toRemove);
        toRemove = null;
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
