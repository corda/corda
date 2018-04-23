/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_ALLOCATOR_H
#define AVIAN_UTIL_ALLOCATOR_H

#include <stddef.h>

namespace avian {
namespace util {

class AllocOnly {
 public:
  virtual void* allocate(size_t size) = 0;
};

class Alloc : public AllocOnly {
 public:
  virtual void free(const void* p, size_t size) = 0;
};

class Allocator : public Alloc {
 public:
  virtual void* tryAllocate(size_t size) = 0;
};

}  // namespace util
}  // namespace avian

inline void* operator new(size_t size, avian::util::AllocOnly* allocator)
{
  return allocator->allocate(size);
}

#endif  // AVIAN_UTIL_ALLOCATOR_H
