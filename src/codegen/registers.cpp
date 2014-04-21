/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/codegen/registers.h>

namespace avian {
namespace codegen {

unsigned
RegisterMask::maskStart(uint32_t mask)
{
  for (int i = 0; i <= 31; ++i) {
    if (mask & (1 << i)) return i;
  }
  return 32;
}

unsigned
RegisterMask::maskLimit(uint32_t mask)
{
  for (int i = 31; i >= 0; --i) {
    if (mask & (1 << i)) return i + 1;
  }
  return 0;
}

} // namespace codegen
} // namespace avian
