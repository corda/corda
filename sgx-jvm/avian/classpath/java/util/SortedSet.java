/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public interface SortedSet<T> extends Collection<T>, Iterable<T>, Set<T> {
  public Comparator<? super T> comparator();
  public T first();
  public SortedSet<T> headSet(T toElement);
  public T last();
  public SortedSet<T> subSet(T fromElement, T toElement);
  public SortedSet<T> tailSet(T fromElement);
}
