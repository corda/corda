/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public interface List<T> extends Collection<T> {
  public T get(int index);

  public T set(int index, T value);

  public T remove(int index);

  public boolean add(T element);

  public void add(int index, T element);

  public boolean isEmpty();
}
