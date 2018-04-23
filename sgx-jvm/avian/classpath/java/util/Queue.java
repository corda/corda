/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public interface Queue<T> extends Collection<T>, Iterable<T> {
  public boolean add(T element);
  public T element();
  public boolean offer(T element);
  public T peek();
  public T poll();
  public T remove();
}
