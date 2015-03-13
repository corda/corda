/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

import java.util.Map;

public interface ConcurrentMap<K,V> extends Map<K,V> {
  public V putIfAbsent(K key, V value);

  public boolean remove(K key, V value);

  public V replace(K key, V value);

  public boolean replace(K key, V oldValue, V newValue);
}
