/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ALLOCATOR_H
#define ALLOCATOR_H

#include "common.h"

namespace vm {

class Allocator {
 public:
  virtual ~Allocator() { }
  virtual void* tryAllocate(unsigned size, bool executable) = 0;
  virtual void* allocate(unsigned size, bool executable) = 0;
  virtual void free(const void* p, unsigned size, bool executable) = 0;
};

} // namespace vm

#endif//ALLOCATOR_H
