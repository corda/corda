/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/util/fixed-allocator.h>

#include <avian/common.h>

namespace avian {
namespace util {

FixedAllocator::FixedAllocator(Aborter* a, Slice<uint8_t> memory)
    : a(a), memory(memory), offset(0)
{
}

void* FixedAllocator::tryAllocate(size_t size)
{
  return allocate(size);
}

void* FixedAllocator::allocate(size_t size, unsigned padAlignment)
{
  size_t paddedSize = vm::pad(size, padAlignment);
  expect(a, offset + paddedSize < memory.count);

  void* p = memory.begin() + offset;
  offset += paddedSize;
  return p;
}

void* FixedAllocator::allocate(size_t size)
{
  return allocate(size, vm::BytesPerWord);
}

void FixedAllocator::free(const void* p, size_t size)
{
  if (p >= memory.begin()
      and static_cast<const uint8_t*>(p) + size == memory.begin() + offset) {
    offset -= size;
  } else {
    abort(a);
  }
}

}  // namespace util
}  // namespace avian
