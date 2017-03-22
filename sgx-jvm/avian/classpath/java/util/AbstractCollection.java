/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

/**
 * @author zsombor
 * 
 */
public abstract class AbstractCollection<T> implements Collection<T> {
  public boolean add(T element) {
    throw new UnsupportedOperationException("adding to "
                                            + this.getClass().getName());
  }

  public boolean addAll(Collection<? extends T> collection) {
    boolean result = false;
    for (T obj : collection) {
      result |= add(obj);
    }
    return result;
  }

  public void clear() {
    throw new UnsupportedOperationException("clear() in "
                                            + this.getClass().getName());
  }

  public boolean contains(Object element) {
    if (element != null) {
      for (Iterator<T> iter = iterator(); iter.hasNext();) {
        if (element.equals(iter.next())) {
          return true;
        }
      }
    } else {
      for (Iterator<T> iter = iterator(); iter.hasNext();) {
        if (iter.next()==null) {
          return true;
        }
      }
                        
    }
    return false;
  }

  public boolean containsAll(Collection<?> c) {
    if (c == null) {
      throw new NullPointerException("Collection is null");
    }

    Iterator<?> it = c.iterator();
    while (it.hasNext()) {
      if (! contains(it.next())) {
        return false;
      }
    }

    return true;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public boolean remove(Object element) {
    throw new UnsupportedOperationException("remove(T) in "
                                            + this.getClass().getName());
  }

  public boolean removeAll(Collection<?> c) {
    if (c == null) {
      throw new NullPointerException("Collection is null");
    }

    boolean changed = false;

    Iterator<?> it = c.iterator();
    while (it.hasNext()) {
      changed = remove(it.next()) || changed;
    }

    return changed;
  }

  public abstract int size();

  public Object[] toArray() {
    return toArray(new Object[size()]);      
  }

  public <S> S[] toArray(S[] array) {
    return avian.Data.toArray(this, array);
  }

  public abstract Iterator<T> iterator();

  public String toString() {
    return avian.Data.toString(this);
  }
}
