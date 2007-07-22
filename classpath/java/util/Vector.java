package java.util;

public class Vector<T> implements List<T> {
  private final ArrayList<T> list;

  public Vector(int capacity) {
    list = new ArrayList(capacity);
  }

  public Vector() {
    this(0);
  }

  public synchronized int size() {
    return list.size();
  }

  public synchronized boolean contains(T element) {
    return list.contains(element);
  }

  public synchronized boolean add(T element) {
    return list.add(element);
  }

  public void addElement(T element) {
    add(element);
  }

  public synchronized T get(int index) {
    return list.get(index);
  }

  public T elementAt(int index) {
    return get(index);
  }

  public synchronized T remove(int index) {
    return list.remove(index);
  }

  public synchronized boolean remove(T element) {
    return list.remove(element);
  }

  public synchronized void clear() {
    list.clear();
  }

  public synchronized void copyInto(Object[] array) {
    for (int i = 0; i < size(); ++i) {
      array[i] = list.get(i);
    }
  }

  public Iterator<T> iterator() {
    return new Collections.ArrayListIterator(this);
  }

  public Enumeration<T> elements() {
    return new Collections.IteratorEnumeration(iterator());
  }
  
}
