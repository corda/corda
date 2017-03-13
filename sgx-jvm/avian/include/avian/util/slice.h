/* Copyright (c) 2008-2015, Avian Contributors

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
#include "assert.h"
#include "cpp.h"

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

  inline Slice(const Slice<typename NonConst<T>::Type>& copy)
      : items(copy.items), count(copy.count)
  {
  }

  inline T& operator[](size_t index)
  {
    ASSERT(index < count);
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

  inline Slice<T> subslice(size_t begin, size_t count)
  {
    ASSERT(begin <= this->count);
    ASSERT(begin + count <= this->count);
    return Slice<T>(this->begin() + begin, count);
  }

  static Slice<T> alloc(AllocOnly* a, size_t count)
  {
    return Slice<T>((T*)a->allocate(sizeof(T) * count), count);
  }

  static Slice<T> allocAndSet(AllocOnly* a, size_t count, const T& item)
  {
    Slice<T> slice(alloc(a, count));
    for (size_t i = 0; i < count; i++) {
      slice[i] = item;
    }
    return slice;
  }

  Slice<T> clone(AllocOnly* a, size_t newCount)
  {
    T* newItems = (T*)a->allocate(newCount * sizeof(T));
    memcpy(newItems, items, min(count, newCount) * sizeof(T));
    return Slice<T>(newItems, newCount);
  }

  Slice<T> cloneAndSet(AllocOnly* a, size_t newCount, const T& item)
  {
    Slice<T> slice(clone(a, newCount));
    for (size_t i = count; i < newCount; i++) {
      slice[i] = item;
    }
    return slice;
  }

  void resize(Alloc* a, size_t newCount)
  {
    Slice<T> slice(clone(a, newCount));
    a->free(items, count);
    *this = slice;
  }
};

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_SLICE_H
