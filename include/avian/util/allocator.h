/* Copyright (c) 2008-2014, Avian Contributors

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

class Allocator {
 public:
  // TODO: use size_t instead of unsigned
  virtual void* tryAllocate(unsigned size) = 0;
  virtual void* allocate(unsigned size) = 0;
  virtual void free(const void* p, unsigned size) = 0;
};

}  // namespace util
}  // namespace avian

inline void* operator new(size_t size, avian::util::Allocator* allocator)
{
  return allocator->allocate(size);
}

#endif  // AVIAN_UTIL_ALLOCATOR_H
