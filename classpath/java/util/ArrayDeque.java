/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class ArrayDeque<T> extends AbstractDeque<T> 
                           implements Deque<T> {
  private Object[] dataArray;
  // both indexes are inclusive, except when size == 0
  private int startIndex;
  private int endIndex;
  private int size;
  private int modCount;
  
  public ArrayDeque() {
    this(16);
  }
  
  public ArrayDeque(int intialSize) {
    dataArray = new Object[intialSize];
    modCount = 0;
    clear();
  }
  
  public ArrayDeque(Collection<? extends T> c) {
    this(c.size());
    
    addAll(c);
  }
  
  private void copyInto(Object[] array) {
    if (startIndex <= endIndex) {
      // only one copy needed
      System.arraycopy(dataArray, startIndex, array, 0, size);
    } else {
      int firstCopyCount = dataArray.length - startIndex;
      System.arraycopy(dataArray, startIndex, array, 0, firstCopyCount);
      System.arraycopy(dataArray, 0, array, firstCopyCount, endIndex + 1);
    }
  }
  
  private void ensureCapacity(int newSize) {
    if (dataArray.length < newSize) {
      Object[] newArray = new Object[dataArray.length * 2];
      copyInto(newArray);
      
      dataArray = newArray;
      startIndex = 0;
      endIndex = size - 1;
    }
  }

  @Override
  public boolean offerFirst(T e) {
    ensureCapacity(size() + 1);
    modCount++;
    
    if (size > 0) {
      // we don't need to move the head index for the first one
      startIndex--;
      if (startIndex < 0) { // wrapping to the end of the array
        startIndex = dataArray.length - 1;
      }
    }
    size++;
    dataArray[startIndex] = e;
    
    return true;
  }

  @Override
  public boolean offerLast(T e) {
    ensureCapacity(size() + 1);
    modCount++;
    
    if (size > 0) {
      // we don't need to move the tail index for the first one
      endIndex = (endIndex + 1) % dataArray.length;
    }
    size++;
    dataArray[endIndex] = e;
    
    return true;
  }

  @Override
  public T pollFirst() {
    modCount++;
    
    if (size == 0) {
      return null;
    }
    
    @SuppressWarnings("unchecked")
    T result = (T)dataArray[startIndex];
    size--;
    if (size == 0) {
      startIndex = endIndex = 0;
    } else {
      startIndex = (startIndex + 1) % dataArray.length;
    }
    
    return result;
  }

  @Override
  public T pollLast() {
    modCount++;
    
    if (size == 0) {
      return null;
    }
    
    @SuppressWarnings("unchecked")
    T result = (T)dataArray[endIndex];
    size--;
    if (size == 0) {
      startIndex = endIndex = 0;
    } else {
      endIndex--;
      if (endIndex < 0) {
        endIndex = dataArray.length - 1;
      }
    }
    
    return result;
  }

  @Override
  public T peekFirst() {
    if (size == 0) {
      return null;
    } else {
      @SuppressWarnings("unchecked")
      T result = (T)dataArray[startIndex];
      return result;
    }
  }

  @Override
  public T peekLast() {
    if (size == 0) {
      return null;
    } else {
      @SuppressWarnings("unchecked")
      T result = (T)dataArray[endIndex];
      return result;
    }
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    if (c == null || c.isEmpty()) {
      return false;
    }
    
    ensureCapacity(size() + c.size());
    
    Iterator<? extends T> it = c.iterator();
    while (it.hasNext()) {
      add(it.next());
    }
    
    return true;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean removed = false;
    Iterator<?> it = c.iterator();
    while (it.hasNext()) {
      removed = remove(it.next()) || removed;
    }
    
    return removed;
  }
  
  private boolean remove(Object o, boolean first) {
    modCount++;
    
    Iterator<T> it;
    if (first) {
      it = iterator();
    } else {
      it = descendingIterator();
    }
    while (it.hasNext()) {
      T next = it.next();
      if (next == null) {
        if (o == null) {
          it.remove();
          return true;
        }
      } else if (next.equals(o)) {
        it.remove();
        return true;
      }
    }
    
    return false;
  }

  @Override
  public boolean removeFirstOccurrence(Object o) {
    return remove(o, true);
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    return remove(o, false);
  }

  @Override
  public void clear() {
    size = 0;
    startIndex = endIndex = 0;
    modCount++;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean contains(Object element) {
    Iterator<T> it = iterator();
    while (it.hasNext()) {
      T next = it.next();
      if (next == null) {
        if (element == null) {
          return true;
        }
      } else if (next.equals(element)) {
        return true;
      }
    }
    
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    Iterator<?> it = c.iterator();
    while (it.hasNext()) {
      if (! contains(it.next())) {
        return false;
      }
    }
    
    return true;
  }

  @Override
  public Object[] toArray() {
    Object[] result = new Object[size];
    copyInto(result);
    
    return result;
  }

  @Override
  public <S> S[] toArray(S[] array) {
    return avian.Data.toArray(this, array);
  }

  public Iterator<T> iterator() {
    return new GenericIterator() {
      @Override
      protected int getArrayIndex() {
        int result = (currentIndex + startIndex) % dataArray.length;
        return result;
      }
    };
  }

  public Iterator<T> descendingIterator() {
    return new GenericIterator() {
      @Override
      protected int getArrayIndex() {
        int result = (endIndex - currentIndex) % dataArray.length;
        return result;
      }
    };
  }
  
  private abstract class GenericIterator implements Iterator<T> {
    protected int expectedModCount = modCount;
    protected int currentIndex = 0;
    
    protected abstract int getArrayIndex();
    
    @Override
    public T next() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      } else if (currentIndex == size) {
        throw new NoSuchElementException();
      }
      
      @SuppressWarnings("unchecked")
      T result = (T)dataArray[getArrayIndex()];
      currentIndex++;
      
      return result;
    }

    @Override
    public boolean hasNext() {
      return currentIndex < size;
    }
    
    @Override
    public void remove() {
      currentIndex--;
      
      int removalIndex = getArrayIndex();
      if (removalIndex == startIndex) {
        // avoid array copy
        pollFirst();
        expectedModCount = modCount;
      } else if (removalIndex == endIndex) {
        // avoid array copy
        pollLast();
        expectedModCount = modCount;
      } else {  // array must be copied
        Object[] newArray = new Object[dataArray.length];
        if (startIndex <= endIndex) {
          int firstCopyCount = removalIndex - startIndex;
          System.arraycopy(dataArray, startIndex, 
                           newArray, 0, firstCopyCount);
          System.arraycopy(dataArray, removalIndex + 1, 
                           newArray, firstCopyCount, size - firstCopyCount);
        } else if (removalIndex > startIndex) {
          int firstCopyCount = removalIndex - startIndex;
          System.arraycopy(dataArray, startIndex, 
                           newArray, 0, firstCopyCount);
          System.arraycopy(dataArray, startIndex + firstCopyCount + 1, 
                           newArray, firstCopyCount, dataArray.length - removalIndex - 1);
          System.arraycopy(dataArray, 0, newArray, size - removalIndex, endIndex + 1);
        } else {
          int firstCopyCount = dataArray.length - startIndex;
          System.arraycopy(dataArray, startIndex, 
                           newArray, 0, firstCopyCount);
          System.arraycopy(dataArray, 0, 
                           newArray, firstCopyCount, removalIndex);
          System.arraycopy(dataArray, removalIndex + 1, 
                           newArray, firstCopyCount + removalIndex, endIndex - removalIndex);
        }
        
        dataArray = newArray;
        startIndex = 0;
        endIndex = --size - 1;
      }
    }
  }
}
