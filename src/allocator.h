#ifndef ALLOCATOR_H
#define ALLOCATOR_H

#include "common.h"

namespace vm {

class Allocator {
 public:
  virtual ~Allocator() { }
  virtual void* tryAllocate(unsigned size) = 0;
  virtual void* allocate(unsigned size) = 0;
  virtual void free(const void* p, unsigned size) = 0;
};

} // namespace vm

#endif//ALLOCATOR_H
