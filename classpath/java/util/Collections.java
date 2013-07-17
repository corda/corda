/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Collections {

  private Collections() { }

  public static void shuffle(List list, Random random) {
    Object[] array = toArray(list, new Object[list.size()]);
    for (int i = 0; i < array.length; ++i) {
      int j = random.nextInt(array.length);
      Object tmp = array[i];
      array[i] = array[j];
      array[j] = tmp;
    }
 
    list.clear();
    for (int i = 0; i < array.length; ++i) {
      list.add(array[i]);
    }
  }

  public static void shuffle(List list) {
    shuffle(list, new Random());
  }

  static <T> T[] toArray(Collection collection, T[] array) {
    Class c = array.getClass().getComponentType();

    if (array.length < collection.size()) {
      array = (T[]) java.lang.reflect.Array.newInstance(c, collection.size());
    }

    int i = 0;
    for (Object o: collection) {
      if (c.isInstance(o)) {
        array[i++] = (T) o;
      } else {
        throw new ArrayStoreException();
      }
    }

    return array;
  }

  public static final List EMPTY_LIST
    = new UnmodifiableList<Object>(new ArrayList<Object>(0));

  public static final <E> List<E> emptyList() {
    return EMPTY_LIST;
  }

  public static final <K,V> Map<K,V> emptyMap() {
    return (Map<K, V>) new UnmodifiableMap<Object, Object>(
      new HashMap<Object, Object>(0));
  }

  public static final <T> Set<T> emptySet() {
    return (Set<T>) new UnmodifiableSet<Object>(
      new HashSet<Object>(0));
  }

  static String toString(Collection c) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (Iterator it = c.iterator(); it.hasNext();) {
      sb.append(it.next());
      if (it.hasNext()) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  static String toString(Map m) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    for (Iterator<Map.Entry> it = m.entrySet().iterator(); it.hasNext();) {
      Map.Entry e = it.next();
      sb.append(e.getKey())
        .append("=")
        .append(e.getValue());
      if (it.hasNext()) {
        sb.append(",");
      }
    }
    sb.append("}");
    return sb.toString();
  }
  
  public static <T> Enumeration<T> enumeration(Collection<T> c) {
    return new IteratorEnumeration<T> (c.iterator());
  }

  public static <T> Comparator<T> reverseOrder(Comparator<T> cmp) {
    return new ReverseComparator<T>(cmp);
  }
  
  static class IteratorEnumeration<T> implements Enumeration<T> {
    private final Iterator<T> it;

    public IteratorEnumeration(Iterator<T> it) {
      this.it = it;
    }

    public T nextElement() {
      return it.next();
    }

    public boolean hasMoreElements() {
      return it.hasNext();
    }
  }

  static class SynchronizedCollection<T> implements Collection<T> {
    protected final Object lock;
    protected final Collection<T> collection;

    public SynchronizedCollection(Object lock, Collection<T> collection) {
      this.lock = lock;
      this.collection = collection;
    }

    public int size() {
      synchronized (lock) { return collection.size(); }
    }

    public boolean isEmpty() {
      return size() == 0;
    }

    public boolean contains(Object e) {
      synchronized (lock) { return collection.contains(e); }
    }

    public boolean add(T e) {
      synchronized (lock) { return collection.add(e); }
    }

    public boolean addAll(Collection<? extends T> collection) {
      synchronized (lock) { return this.collection.addAll(collection); }
    }

    public boolean remove(Object e) {
      synchronized (lock) { return collection.remove((T)e); }
    }

    public Object[] toArray() {
      return toArray(new Object[size()]);      
    }

    public <T> T[] toArray(T[] array) {
      synchronized (lock) { return collection.toArray(array); }
    }

    public void clear() {
      synchronized (lock) { collection.clear(); }
    }

    public Iterator<T> iterator() {
      return new SynchronizedIterator<T>(lock, collection.iterator());
    }

    public boolean containsAll(Collection<?> c) {
      synchronized (lock) { return collection.containsAll(c); }
    }

    public boolean removeAll(Collection<?> c) {
      synchronized (lock) { return collection.removeAll(c); }
    }
  }
  
  static class SynchronizedMap<K,V> implements Map<K,V> {
    protected final Object lock;
    protected final Map<K,V> map;

    SynchronizedMap(Map<K,V> map) {
      this.map = map;
      this.lock = this;
    }

    SynchronizedMap(Object lock, Map<K,V> map) {
      this.lock = lock;
      this.map = map;
    }
    
    public void clear() {
      synchronized (lock) { map.clear(); }
    }
    public boolean containsKey(Object key) {
      synchronized (lock) { return map.containsKey(key); }
    }
    public boolean containsValue(Object value) {
      synchronized (lock) { return map.containsValue(value); }
    }
    public Set<java.util.Map.Entry<K, V>> entrySet() {
      synchronized (lock) { return new SynchronizedSet<java.util.Map.Entry<K, V>>(lock, map.entrySet()); }
    }
    public V get(Object key) {
      synchronized (lock) { return map.get(key); }
    }
    public boolean isEmpty() {
      synchronized (lock) { return map.isEmpty(); }
    }
    public Set<K> keySet() {
      synchronized (lock) { return new SynchronizedSet<K>(lock, map.keySet()); }
    }
    public V put(K key, V value) {
      synchronized (lock) { return map.put(key, value); }
    }
    public void putAll(Map<? extends K, ? extends V> elts) {
      synchronized (lock) { map.putAll(elts); }
    }
    public V remove(Object key) {
      synchronized (lock) { return map.remove(key); }
    }
    public int size() {
      synchronized (lock) { return map.size(); }
    }
    public Collection<V> values() {
      synchronized (lock) { return new SynchronizedCollection<V>(lock, map.values()); }
    }
  }
  
  public static <K,V> Map<K,V> synchronizedMap(Map<K,V> map) {
    return new SynchronizedMap<K, V> (map); 
  }
  
  static class SynchronizedSet<T>
    extends SynchronizedCollection<T>
    implements Set<T>
  {
    public SynchronizedSet(Object lock, Set<T> set) {
      super(lock, set);
    }
  }

  static class SynchronizedIterator<T> implements Iterator<T> {
    private final Object lock;
    private final Iterator<T> it;

    public SynchronizedIterator(Object lock, Iterator<T> it) {
      this.lock = lock;
      this.it = it;
    }

    public T next() {
      synchronized (lock) { return it.next(); }
    }

    public boolean hasNext() {
      synchronized (lock) { return it.hasNext(); }
    }

    public void remove() {
      synchronized (lock) { it.remove(); }
    }
  }

  static class ArrayListIterator<T> implements ListIterator<T> {
    private final List<T> list;
    private int toRemove = -1;
    private int index;

    public ArrayListIterator(List<T> list) {
      this(list, 0);
    }

    public ArrayListIterator(List<T> list, int index) {
      this.list = list;
      this.index = index - 1;
    }

    public boolean hasPrevious() {
      return index >= 0;
    }

    public T previous() {
      if (hasPrevious()) {
        toRemove = index;
        return list.get(index--);
      } else {
        throw new NoSuchElementException();
      }
    }

    public T next() {
      if (hasNext()) {
        toRemove = ++index;
        return list.get(index);
      } else {
        throw new NoSuchElementException();
      }
    }

    public boolean hasNext() {
      return index + 1 < list.size();
    }

    public void remove() {
      if (toRemove != -1) {
        list.remove(toRemove);
        index = toRemove - 1;
        toRemove = -1;
      } else {
        throw new IllegalStateException();
      }
    }
  }

  public static <T> List<T> unmodifiableList(List<T> list)  {
    return new UnmodifiableList<T>(list);
  }

  static class UnmodifiableList<T> implements List<T> {

    private List<T> inner;

    UnmodifiableList(List<T> l) {
      this.inner = l;
    }

    public T get(int index) {
      return inner.get(index);
    }

    public T set(int index, T value) {
      throw new UnsupportedOperationException();
    }

    public T remove(int index) {
      throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    public boolean add(T element) {
      throw new UnsupportedOperationException();
    }

    public void add(int index, T element) {
      throw new UnsupportedOperationException();
    }

    public Iterator<T> iterator() {
      return new UnmodifiableIterator<T>(inner.iterator());
    }

    public int indexOf(Object value) {
      return inner.indexOf(value);
    }

    public int lastIndexOf(Object value) {
      return inner.lastIndexOf(value);
    }

    public boolean isEmpty() {
      return inner.isEmpty();
    }

    public ListIterator<T> listIterator(int index) {
      return new UnmodifiableListIterator<T>(inner.listIterator(index));
    }

    public ListIterator<T> listIterator() {
      return new UnmodifiableListIterator<T>(inner.listIterator());
    }

    public int size() {
      return inner.size();
    }

    public boolean contains(Object element) {
      return inner.contains(element);
    }

    public boolean addAll(Collection<? extends T> collection) {
      throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
      return inner.toArray();
    }

    public <S> S[] toArray(S[] array) {
      return inner.toArray(array);
    }

    public void clear() {
      throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    public boolean addAll(int startIndex, Collection<? extends T> c) {
      throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> c) {
      return inner.containsAll(c);
    }
  }

  public static <K,V> Map<K,V> unmodifiableMap(Map<K,V> m) {
	  return new UnmodifiableMap<K, V>(m);
  }

  static class UnmodifiableMap<K, V> implements Map<K, V> {
	  private Map<K, V> inner;

	  UnmodifiableMap(Map<K, V> m) {
	    this.inner = m;
	  }

    public void clear() {
      throw new UnsupportedOperationException();
    }

    public boolean containsKey(Object key) {
      return inner.containsKey(key);
    }

    public boolean containsValue(Object value) {
      return inner.containsValue(value);
    }

    public Set<Map.Entry<K, V>> entrySet() {
      return unmodifiableSet(inner.entrySet());
    }

    public V get(Object key) {
      return inner.get(key);
    }

    public boolean isEmpty() {
      return inner.isEmpty();
    }

    public Set<K> keySet() {
      return unmodifiableSet(inner.keySet());
    }

    public V put(K key, V value) {
      throw new UnsupportedOperationException();
    }

    public void putAll(Map<? extends K, ? extends V> t) {
      throw new UnsupportedOperationException();
    }

    public V remove(Object key) {
      throw new UnsupportedOperationException();
    }

    public int size() {
      return inner.size();
    }

    public Collection<V> values() {
      return unmodifiableCollection(inner.values());
    }
  }
  
  static class UnmodifiableIterator<T> implements Iterator<T> {
    private final Iterator<T> inner;
    
    UnmodifiableIterator(Iterator<T> inner) {
      this.inner = inner;
    }
    
    @Override
    public T next() {
      return inner.next();
    }

    @Override
    public boolean hasNext() {
      return inner.hasNext();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
  
  
  static class UnmodifiableListIterator<T> extends UnmodifiableIterator<T> 
                                                   implements ListIterator<T> {
    private final ListIterator<T> innerListIterator;
    
    UnmodifiableListIterator(ListIterator<T> listIterator) {
      super(listIterator);
      
      this.innerListIterator = listIterator;
    }

    @Override
    public boolean hasPrevious() {
      return innerListIterator.hasPrevious();
    }

    @Override
    public T previous() {
      return innerListIterator.previous();
    }
  }
  
  static class UnmodifiableCollection<T> implements Collection<T> {
    private final Collection<T> inner;
    
    UnmodifiableCollection(Collection<T> inner) {
      this.inner = inner;
    }
    
    @Override
    public Iterator<T> iterator() {
      return new UnmodifiableIterator<T>(inner.iterator());
    }

    @Override
    public int size() {
      return inner.size();
    }

    @Override
    public boolean isEmpty() {
      return inner.isEmpty();
    }

    @Override
    public boolean contains(Object element) {
      return inner.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return inner.containsAll(c);
    }

    @Override
    public boolean add(T element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
      return inner.toArray();
    }

    @Override
    public <S> S[] toArray(S[] array) {
      return inner.toArray(array);
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }
  }
  
  public static <T> UnmodifiableCollection<T> unmodifiableCollection(Collection<T> collection) {
    return new UnmodifiableCollection<T>(collection);
  }

  static class UnmodifiableSet<T> extends UnmodifiableCollection<T> 
                                  implements Set<T> {
    UnmodifiableSet(Set<T> inner) {
      super(inner);
    }  
  }
  
  public static <T> Set<T> unmodifiableSet(Set<T> hs) {
    return new UnmodifiableSet<T>(hs);
  }

  static class KeyIterator<K, V> implements Iterator<K> {
    private final Iterator<Map.Entry<K, V>> it;

    public KeyIterator(Iterator<Map.Entry<K, V>> it) {
      this.it = it;
    }

    public K next() {
      return it.next().getKey();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public void remove() {
      it.remove();
    }
  }

  static class ValueIterator<K, V> implements Iterator<V> {
    private final Iterator<Map.Entry<K, V>> it;

    public ValueIterator(Iterator<Map.Entry<K, V>> it) {
      this.it = it;
    }

    public V next() {
      return it.next().getValue();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public void remove() {
      it.remove();
    }
  }
  
  private static final class ReverseComparator<T> implements Comparator<T> {

    Comparator<T> cmp;
    
    public ReverseComparator(Comparator<T> cmp) {
      this.cmp = cmp;
    }
    
    public int compare(T o1, T o2) {
      return - cmp.compare(o1, o2);
    }
  }
}
