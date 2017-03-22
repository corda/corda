/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/system/memory.h>

#include <avian/util/assert.h>

#include "sys/mman.h"

namespace avian {
namespace system {

const size_t Memory::PageSize = 1 << 12;

util::Slice<uint8_t> Memory::allocate(size_t sizeInBytes,
                               Permissions perms)
{
  unsigned prot = 0;
  if(perms & Read) {
    prot |= PROT_READ;
  }
  if(perms & Write) {
    prot |= PROT_WRITE;
  }
  if(perms & Execute) {
    prot |= PROT_EXEC;
  }
#ifdef MAP_32BIT
  // map to the lower 32 bits of memory when possible so as to avoid
  // expensive relative jumps
  const unsigned Extra = MAP_32BIT;
#else
  const unsigned Extra = 0;
#endif

  void* p = mmap(0,
                 sizeInBytes,
                 prot,
                 MAP_PRIVATE | MAP_ANON | Extra,
                 -1,
                 0);

  if (p == MAP_FAILED) {
    return util::Slice<uint8_t>(0, 0);
  } else {
    return util::Slice<uint8_t>(static_cast<uint8_t*>(p), sizeInBytes);
  }
}

void Memory::free(util::Slice<uint8_t> pages)
{
  munmap(const_cast<uint8_t*>(pages.begin()), pages.count);
}

}  // namespace system
}  // namespace avian
