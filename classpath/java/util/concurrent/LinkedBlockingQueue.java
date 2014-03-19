/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

public class LinkedBlockingQueue<T> extends AbstractQueue<T> 
                                    implements BlockingQueue<T> {
  private final Object collectionLock;
  private final LinkedList<T> storage;
  private final int capacity;
  
  public LinkedBlockingQueue() {
    this(Integer.MAX_VALUE);
  }
  
  public LinkedBlockingQueue(int capacity) {
    collectionLock = new Object();
    this.capacity = capacity;
    storage = new LinkedList<T>();
  }
  
  // should be synchronized on collectionLock before calling
  private void handleRemove() {
    collectionLock.notifyAll();
  }
  
  // should be synchronized on collectionLock before calling
  private void handleAdd() {
    collectionLock.notifyAll();
  }

  // should be synchronized on collectionLock before calling
  private void blockTillNotFull() throws InterruptedException {
    blockTillNotFull(Long.MAX_VALUE);
  }

  // should be synchronized on collectionLock before calling
  private void blockTillNotFull(long maxWaitInMillis) throws InterruptedException {
    if (capacity > storage.size()) {
      return;
    }
    
    long startTime = 0;
    if (maxWaitInMillis != Long.MAX_VALUE) {
      startTime = System.currentTimeMillis();
    }
    long remainingWait = maxWaitInMillis;
    while (remainingWait > 0) {
      collectionLock.wait(remainingWait);
      
      if (capacity > storage.size()) {
        return;
      } else if (maxWaitInMillis != Long.MAX_VALUE) {
        remainingWait = maxWaitInMillis - (System.currentTimeMillis() - startTime);
      }
    }
  }

  // should be synchronized on collectionLock before calling
  private void blockTillNotEmpty() throws InterruptedException {
    blockTillNotEmpty(Long.MAX_VALUE);
  }

  // should be synchronized on collectionLock before calling
  private void blockTillNotEmpty(long maxWaitInMillis) throws InterruptedException {
    if (! storage.isEmpty()) {
      return;
    }
    
    long startTime = 0;
    if (maxWaitInMillis != Long.MAX_VALUE) {
      startTime = System.currentTimeMillis();
    }
    long remainingWait = maxWaitInMillis;
    while (remainingWait > 0) {
      collectionLock.wait(remainingWait);
      
      if (! storage.isEmpty()) {
        return;
      } else if (maxWaitInMillis != Long.MAX_VALUE) {
        remainingWait = maxWaitInMillis - (System.currentTimeMillis() - startTime);
      }
    }
  }

  @Override
  public boolean offer(T element) {
    synchronized (collectionLock) {
      if (capacity > storage.size()) {
        storage.addLast(element);
        
        handleAdd();
        
        return true;
      } else {
        return false;
      }
    }
  }

  @Override
  public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException {
    long timeoutInMillis = unit.toMillis(timeout);
    synchronized (collectionLock) {
      // block till we can add or have reached timeout
      blockTillNotFull(timeoutInMillis);
      
      return offer(e);
    }
  }

  @Override
  public void put(T e) throws InterruptedException {
    synchronized (collectionLock) {
      // block till we have space
      blockTillNotFull();
      
      storage.add(e);
      handleAdd();
    }
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    synchronized (collectionLock) {
      if (storage.size() + c.size() > capacity) {
        throw new IllegalStateException("Not enough space");
      }
      
      if (c.isEmpty()) {
        return false;
      } else {
        storage.addAll(c);
        
        return true;
      }
    }
  }

  @Override
  public T peek() {
    synchronized (collectionLock) {
      if (storage.isEmpty()) {
        return null;
      } else {
        return storage.getFirst();
      }
    }
  }

  // should be synchronized on collectionLock before calling
  private T removeFirst() {
    T result = storage.removeFirst();
    handleRemove();
    
    return result;
  }

  @Override
  public T poll() {
    synchronized (collectionLock) {
      if (storage.isEmpty()) {
        return null;
      } else {
        return removeFirst();
      }
    }
  }

  @Override
  public T poll(long timeout, TimeUnit unit) throws InterruptedException {
    long timeoutInMillis = unit.toMillis(timeout);
    synchronized (collectionLock) {
      // block till we available or timeout
      blockTillNotEmpty(timeoutInMillis);
      
      return poll();
    }
  }

  @Override
  public T take() throws InterruptedException {
    synchronized (collectionLock) {
      // block till we available
      blockTillNotEmpty();
      
      return removeFirst();
    }
  }

  @Override
  public int drainTo(Collection<? super T> c) {
    return drainTo(c, Integer.MAX_VALUE);
  }

  @Override
  public int drainTo(Collection<? super T> c, int maxElements) {
    int remainingElements = maxElements;
    synchronized (collectionLock) {
      while (remainingElements > 0 && ! storage.isEmpty()) {
        c.add(storage.removeFirst());
        remainingElements--;
      }
      
      if (remainingElements != maxElements) {
        handleRemove();
      }
      
      return maxElements - remainingElements;
    }
  }

  @Override
  public int remainingCapacity() {
    synchronized (collectionLock) {
      return capacity - storage.size();
    }
  }

  @Override
  public int size() {
    synchronized (collectionLock) {
      return storage.size();
    }
  }

  @Override
  public boolean contains(Object element) {
    synchronized (collectionLock) {
      return storage.contains(element);
    }
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    synchronized (collectionLock) {
      return storage.containsAll(c);
    }
  }

  @Override
  public boolean remove(Object element) {
    synchronized (collectionLock) {
      if (storage.remove(element)) {
        handleRemove();
        return true;
      } else {
        return false;
      }
    }
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    synchronized (collectionLock) {
      if (storage.removeAll(c)) {
        handleRemove();
        return true;
      } else {
        return false;
      }
    }
  }

  @Override
  public void clear() {
    synchronized (collectionLock) {
      storage.clear();
    }
  }

  @Override
  public Object[] toArray() {
    synchronized (collectionLock) {
      return storage.toArray();
    }
  }

  @Override
  public <S> S[] toArray(S[] array) {
    synchronized (collectionLock) {
      return storage.toArray(array);
    }
  }

  @Override
  public Iterator<T> iterator() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
