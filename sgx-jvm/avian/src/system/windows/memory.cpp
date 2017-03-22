/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/system/memory.h>

#include <avian/util/assert.h>

#include <windows.h>

namespace avian {
namespace system {

const size_t Memory::PageSize = 1 << 12;

util::Slice<uint8_t> Memory::allocate(size_t sizeInBytes,
                               Permissions perms)
{
  unsigned prot;
  switch(perms) {
  case Read:
    prot = PAGE_READONLY;
    break;
  case ReadWrite:
    prot = PAGE_READWRITE;
    break;
  case ReadExecute:
    prot = PAGE_EXECUTE_READ;
    break;
  case ReadWriteExecute:
    prot = PAGE_EXECUTE_READWRITE;
    break;
  default:
    UNREACHABLE_;
  }
  void* ret = VirtualAlloc(
      0, sizeInBytes, MEM_COMMIT | MEM_RESERVE, prot);
  return util::Slice<uint8_t>((uint8_t*)ret, sizeInBytes);
}

void Memory::free(util::Slice<uint8_t> pages)
{
  int r = VirtualFree(pages.begin(), 0, MEM_RELEASE);
  (void) r;
  ASSERT(r);
}

}  // namespace system
}  // namespace avian
