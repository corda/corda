/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import avian.Data;
import avian.PersistentSet;
import avian.PersistentSet.Path;

import sun.misc.Unsafe;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ConcurrentHashMap<K,V>
  extends AbstractMap<K,V>
  implements ConcurrentMap<K,V>
{
  private static final Unsafe unsafe = Unsafe.getUnsafe();
  private static final long Content;
  private static final Content Empty = new Content(new PersistentSet(), 0);

  static {
    try {
      Content = unsafe.objectFieldOffset
        (ConcurrentHashMap.class.getDeclaredField("content"));
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }

  private volatile Content<K,V> content;

  public ConcurrentHashMap() {
    content = Empty;
  }

  public ConcurrentHashMap(int initialCapacity) {
    this();
  }

  public ConcurrentHashMap(int initialCapacity,  float loadFactor) {
    this();
  }

  public ConcurrentHashMap(int initialCapacity,  float loadFactor, int concurrencyLevel) {
    this();
  }

  public boolean isEmpty() {
    return content.size == 0;
  }

  public int size() {
    return content.size;
  }

  public boolean containsKey(Object key) {
    return find(key) != null;
  }

  public boolean containsValue(Object value) {
    for (V v: values()) {
      if (value.equals(v)) {
        return true;
      }
    }
    return false;
  }

  public V get(Object key) {
    Cell<K,V> cell = find(key);
    return cell == null ? null : cell.value;
  }

  private Cell<K,V> find(Object key) {
    Content<K,V> c = content;

    Path<Node<Cell<K,V>>> path = c.set.find(new Node(key.hashCode()));
    for (Cell<K,V> cell = path.value().value; 
         cell != null;
         cell = cell.next)
    {
      if (key.equals(cell.key)) {
        return cell;
      }
    }
    return null;
  }

  public V putIfAbsent(K key, V value) {
    Cell<K,V> cell = put(key, value, PutCondition.IfAbsent, null);
    return cell == null ? null : cell.value;
  }

  public boolean remove(K key, V value) {
    Cell<K,V> cell = remove(key, RemoveCondition.IfEqual, value);
    return cell != null && cell.value.equals(value);
  }

  public V replace(K key, V value) {
    Cell<K,V> cell = put(key, value, PutCondition.IfPresent, null);
    return cell == null ? null : cell.value;
  }

  public boolean replace(K key, V oldValue, V newValue) {
    Cell<K,V> cell = put(key, newValue, PutCondition.IfEqual, oldValue);
    return cell != null && cell.value.equals(oldValue);    
  }

  public V put(K key, V value) {
    Cell<K,V> cell = put(key, value, PutCondition.Always, null);
    return cell == null ? null : cell.value;
  }

  public V remove(Object key) {
    Cell<K,V> cell = remove(key, RemoveCondition.Always, null);
    return cell == null ? null : cell.value;
  }

  private enum PutCondition {
    Always() {
      public boolean addIfAbsent() { return true; }
      public <V> boolean addIfPresent(V a, V b) { return true; }
    }, IfAbsent() {
      public boolean addIfAbsent() { return true; }
      public <V> boolean addIfPresent(V a, V b) { return false; }      
    }, IfPresent() {
      public boolean addIfAbsent() { return false; }
      public <V> boolean addIfPresent(V a, V b) { return true; }
    }, IfEqual() {
      public boolean addIfAbsent() { return false; }
      public <V> boolean addIfPresent(V a, V b) { return a.equals(b); }
    };

    public boolean addIfAbsent() { throw new AssertionError(); }
    public <V> boolean addIfPresent(V a, V b) { throw new AssertionError(); }
  }

  private enum RemoveCondition {
    Always() {
      public <V> boolean remove(V a, V b) { return true; }
    }, IfEqual() {
      public <V> boolean remove(V a, V b) { return a.equals(b); }
    };

    public <V> boolean remove(V a, V b) { throw new AssertionError(); }
  }

  private Cell<K,V> put(K key, V value, PutCondition condition, V oldValue) {
    Node<Cell<K,V>> node = new Node(key.hashCode());

    loop: while (true) {
      node.value = null;
      Content content = this.content;
      Path<Node<Cell<K,V>>> path = content.set.find(node);
      for (Cell<K,V> cell = path.value().value;
           cell != null;
           cell = cell.next)
      {
        if (key.equals(cell.key)) {
          if (! condition.addIfPresent(cell.value, oldValue)) {
            return cell;
          }

          Cell<K,V> start = null;
          Cell<K,V> last = null;
          for (Cell<K,V> cell2 = path.value().value;
               true;
               cell2 = cell2.next)
          {
            Cell<K,V> c;
            c = cell2.clone();

            if (last == null) {
              last = start = c;
            } else {
              last.next = c;
              last = c;
            }

            if (cell2 == cell) {
              c.value = value;
              break;
            }
          }

          node.value = start;
          if (unsafe.compareAndSwapObject
              (this, Content, content, new Content
               (path.replaceWith(node), content.size)))
          {
            return cell;
          } else {
            continue loop;
          }
        }
      }

      // no mapping found -- add a new one if appropriate
      if (! condition.addIfAbsent()) {
        return null;
      }

      node.value = new Cell(key, value, null);
      if (unsafe.compareAndSwapObject
          (this, Content, content, new Content
           (path.fresh() ? path.add() : path.replaceWith(node),
            content.size + 1)))
      {
        return null;
      }
    }
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    for (Map.Entry<? extends K, ? extends V> e: map.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  private Cell<K,V> remove(Object key, RemoveCondition condition,
                           V oldValue)
  {
    Node<Cell<K,V>> node = new Node(key.hashCode());

    loop: while (true) {
      node.value = null;
      Content content = this.content;
      Path<Node<Cell<K,V>>> path = content.set.find(node);
      for (Cell<K,V> cell = path.value().value;
           cell != null;
           cell = cell.next)
      {
        if (key.equals(cell.key)) {
          if (! condition.remove(cell.value, oldValue)) {
            return cell;
          }
          
          Cell<K,V> start = null;
          Cell<K,V> last = null;
          for (Cell<K,V> cell2 = path.value().value;
               cell2 != cell;
               cell2 = cell2.next)
          {
            Cell<K,V> c = cell2.clone();
            if (last == null) {
              last = start = c;
            } else {
              last.next = c;
              last = c;
            }
          }

          if (last == null) {
            start = last = cell.next;
          } else {
            last.next = cell.next;
          }

          node.value = start;
          if (unsafe.compareAndSwapObject
              (this, Content, content, new Content
               (start == null ? path.remove() : path.replaceWith(node),
                content.size - 1)))
          {
            return cell;
          } else {
            continue loop;
          }
        }
      }

      return null;
    }
  }

  public void clear() {
    content = Empty;
  }

  public Set<Map.Entry<K, V>> entrySet() {
    return new Data.EntrySet(new MyEntryMap());
  }

  public Set<K> keySet() {
    return new Data.KeySet(new MyEntryMap());
  }

  public Collection<V> values() {
    return new Data.Values(new MyEntryMap());
  }

  private class MyEntryMap implements Data.EntryMap<K, V> {
    public int size() {
      return ConcurrentHashMap.this.size();
    }

    public Map.Entry<K,V> find(Object key) {
      return new MyEntry(ConcurrentHashMap.this.find(key));
    }

    public Map.Entry<K,V> remove(Object key) {
      return new MyEntry
        (ConcurrentHashMap.this.remove(key, RemoveCondition.Always, null));
    }

    public void clear() {
      ConcurrentHashMap.this.clear();
    }

    public Iterator<Map.Entry<K,V>> iterator() {
      return new MyIterator(content);
    }
  }

  private static class Content<K,V> {
    private final PersistentSet<Node<Cell<K,V>>> set;
    private final int size;

    public Content(PersistentSet<Node<Cell<K,V>>> set,
                   int size)
    {
      this.set = set;
      this.size = size;
    }
  }

  private static class Cell<K,V> implements Cloneable {
    public final K key;
    public V value;
    public Cell<K,V> next;

    public Cell(K key, V value, Cell<K,V> next) {
      this.key = key;
      this.value = value;
      this.next = next;
    }

    public Cell<K,V> clone() {
      try {
        return (Cell<K,V>) super.clone();
      } catch (CloneNotSupportedException e) {
        throw new AssertionError();
      }
    }
  }

  private static class Node<T> implements Comparable<Node<T>> {
    public final int key;
    public T value;

    public Node(int key) {
      this.key = key;
    }

    public int compareTo(Node<T> n) {
      return key - n.key;
    }
  }

  private class MyEntry implements Map.Entry<K,V> {
    private final K key;
    private V value;
    
    public MyEntry(Cell<K,V> cell) {
      key = cell.key;
      value = cell.value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      V v = value;
      this.value = value;
      put(key, value);
      return v;
    }
  }

  private class MyIterator implements Iterator<Map.Entry<K, V>> {
    private final Content<K,V> content;
    private final Iterator<Node<Cell<K, V>>> iterator;
    private Cell<K, V> currentCell;
    private Cell<K, V> nextCell;

    public MyIterator(Content<K,V> content) {
      this.content = content;
      this.iterator = content.set.iterator();
      hasNext();
    }

    public Map.Entry<K, V> next() {
      if (hasNext()) {
        currentCell = nextCell;

        nextCell = nextCell.next;

        return new MyEntry(currentCell);
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      if (nextCell == null && iterator.hasNext()) {
        nextCell = iterator.next().value;
      }
      return nextCell != null;
    }

    public void remove() {
      if (currentCell != null) {
        ConcurrentHashMap.this.remove
          (currentCell.key, RemoveCondition.Always, null);
        currentCell = null;
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
