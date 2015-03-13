/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_LIST_H
#define AVIAN_UTIL_LIST_H

#include "allocator.h"

namespace avian {
namespace util {

template <class T>
class List {
 public:
  List(const T& item, List<T>* next) : item(item), next(next)
  {
  }

  unsigned count()
  {
    unsigned count = 0;
    List<T>* c = this;
    while (c) {
      ++count;
      c = c->next;
    }
    return count;
  }

  T item;
  List<T>* next;
};

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_LIST_H
