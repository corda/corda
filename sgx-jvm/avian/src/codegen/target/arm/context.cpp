/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace arm {

Context::Context(vm::System* s, util::Alloc* a, vm::Zone* zone)
    : s(s),
      zone(zone),
      client(0),
      code(s, a, 1024),
      tasks(0),
      result(0),
      firstBlock(new (zone) MyBlock(this, 0)),
      lastBlock(firstBlock),
      poolOffsetHead(0),
      poolOffsetTail(0),
      constantPool(0),
      constantPoolCount(0)
{
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian
