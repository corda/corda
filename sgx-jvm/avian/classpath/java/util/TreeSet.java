/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

import avian.PersistentSet;
import avian.Cell;

public class TreeSet<T> extends AbstractSet<T> implements Collection<T> {
  private PersistentSet<Cell<T>> set;

  public TreeSet(final Comparator<T> comparator) {
    set = new PersistentSet(new Comparator<Cell<T>>() {
      public int compare(Cell<T> a, Cell<T> b) {
        return comparator.compare(a.value, b.value);
      }
    });
  }

  public TreeSet() {
    this(new Comparator<T>() {
        public int compare(T a, T b) {
          return ((Comparable) a).compareTo(b);
        }
    });
  }

  public TreeSet(Collection<? extends T> collection) {
    this();

    for (T item: collection) {
      add(item);
    }
  }

  public T first() {
    if (isEmpty()) throw new NoSuchElementException();

    return set.first().value().value;
  }

  public T last() {
    if (isEmpty()) throw new NoSuchElementException();

    return set.last().value().value;
  }
  
  public Iterator<T> iterator() {
    return new MyIterator<T>(set.first());
  }

  public Iterator<T> descendingIterator() {
    return new MyIterator<T>(set.last(), true);
  }

  public String toString() {
    return avian.Data.toString(this);
  }

  public boolean add(T value) {
    PersistentSet.Path<Cell<T>> p = set.find(new Cell(value, null));
    if (p.fresh()) {
      set = p.add();
      return true;
    }
    return false;
  }

  T addAndReplace(T value) {
    PersistentSet.Path<Cell<T>> p = set.find(new Cell(value, null));
    if (p.fresh()) {
      set = p.add();
      return null;
    } else {
      T old = p.value().value;
      set = p.replaceWith(new Cell(value, null));
      return old;
    }
  }
    
  T find(T value) {
    PersistentSet.Path<Cell<T>> p = set.find(new Cell(value, null));
    return p.fresh() ? null : p.value().value;
  }

  T removeAndReturn(T value) {
    Cell<T> cell = removeCell(value);
    return cell == null ? null : cell.value;
  }

  private Cell<T> removeCell(Object value) {
    PersistentSet.Path<Cell<T>> p = set.find(new Cell(value, null));
    if (p.fresh()) {
      return null;
    } else {
      Cell<T> old = p.value();

      if (p.value().next != null) {
        set = p.replaceWith(p.value().next);
      } else {
        set = p.remove();
      }

      return old;
    }
  }

  public boolean remove(Object value) {
    return removeCell(value) != null;
  }

  public int size() {
    return set.size();
  }

  public boolean isEmpty() {
    return set.size() == 0;
  }

  public boolean contains(Object value) {
    return !set.find(new Cell(value, null)).fresh();
  }

  public void clear() {
    set = new PersistentSet(set.comparator());
  }

  private class MyIterator<T> implements java.util.Iterator<T> {
    private PersistentSet.Path<Cell<T>> path;
    private PersistentSet.Path<Cell<T>> nextPath;
    private Cell<T> cell;
    private Cell<T> prevCell;
    private Cell<T> prevPrevCell;
    private boolean canRemove = false;
    private final boolean reversed;

    private MyIterator(PersistentSet.Path<Cell<T>> path) {
      this(path, false);
    }

    private MyIterator(PersistentSet.Path<Cell<T>> path, boolean reversed) {
      this.path = path;
      this.reversed = reversed;
      if (path != null) {
        cell = path.value();
        nextPath = nextPath();
      }
    }

    private MyIterator(MyIterator<T> start) {
      path = start.path;
      nextPath = start.nextPath;
      cell = start.cell;
      prevCell = start.prevCell;
      prevPrevCell = start.prevPrevCell;
      canRemove = start.canRemove;
      reversed = start.reversed;
    }

    public boolean hasNext() {
      return cell != null || nextPath != null;
    }

    public T next() {
      if (cell == null) {
        path = nextPath;
        nextPath = nextPath();
        cell = path.value();
      }
      prevPrevCell = prevCell;
      prevCell = cell;
      cell = cell.next;
      canRemove = true;
      return prevCell.value;
    }

    private PersistentSet.Path nextPath() {
      return reversed ? path.predecessor() : path.successor();
    }

    public void remove() {
      if (! canRemove) throw new IllegalStateException();

      if (prevPrevCell != null && prevPrevCell.next == prevCell) {
        // cell to remove is not the first in the list.
        prevPrevCell.next = prevCell.next;
        prevCell = prevPrevCell;
      } else if (prevCell.next == cell && cell != null) {
        // cell to remove is the first in the list, but not the last.
        set = (PersistentSet) path.replaceWith(cell);
        prevCell = null;
      } else {
        // cell is alone in the list.
        set = (PersistentSet) path.remove();
        path = nextPath;
        if (path != null) {
          prevCell = null;
          cell = path.value();
          path = (PersistentSet.Path) set.find((Cell) cell);
          nextPath = nextPath();
        }
      }

      canRemove = false;
    }
  }    
}
