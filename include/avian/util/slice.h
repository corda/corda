/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_SLICE_H
#define AVIAN_UTIL_SLICE_H

#include "allocator.h"
#include "math.h"

namespace avian {
namespace util {

template <class T>
class Slice {
 public:
  T* items;
  size_t count;

  inline Slice(T* items, size_t count) : items(items), count(count)
  {
  }

  inline Slice(const Slice<T>& copy) : items(copy.items), count(copy.count)
  {
  }

  inline T& operator[](size_t index)
  {
    return items[index];
  }

  inline T* begin()
  {
    return items;
  }

  inline T* end()
  {
    return items + count;
  }

  static Slice<T> alloc(Allocator* a, size_t count)
  {
    return Slice<T>((T*)a->allocate(sizeof(T) * count), count);
  }

  Slice<T> clone(Allocator* a)
  {
    Slice<T> ret((T*)a->allocate(count * sizeof(T)), count);
    memcpy(ret.items, items, count * sizeof(T));
    return ret;
  }

  void resize(Allocator* a, size_t newCount)
  {
    T* newItems = (T*)a->allocate(newCount * sizeof(T));
    memcpy(newItems, items, min(count, newCount));
    a->free(items, count);
    items = newItems;
    count = newCount;
  }
};

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_SLICE_H
