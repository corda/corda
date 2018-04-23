  /* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_SYSTEM_MEMORY_H
#define AVIAN_SYSTEM_MEMORY_H

#include <avian/util/slice.h>

#include <stdint.h>

namespace avian {
namespace system {

class Memory {
 public:
  enum Permissions {
    Read = 1 << 0,
    Write = 1 << 1,
    Execute = 1 << 2,

    // Utility munged constants
    ReadWrite = Read | Write,
    ReadExecute = Read | Execute,
    ReadWriteExecute = Read | Write | Execute
  };

  static const size_t PageSize;

  // Allocate a contiguous range of pages.
  static util::Slice<uint8_t> allocate(size_t sizeInBytes, Permissions perms = ReadWrite);

  // Free a contiguous range of pages.
  static void free(util::Slice<uint8_t> pages);

  // TODO: In the future:
  // static void setPermissions(util::Slice<uint8_t> pages, Permissions perms);
};

}  // namespace system
}  // namespace avian

#endif
