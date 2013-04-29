/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"
#include "avian/common.h"

namespace avian {
namespace codegen {
namespace powerpc {


Context::Context(vm::System* s, vm::Allocator* a, vm::Zone* zone):
  s(s), zone(zone), client(0), code(s, a, 1024), tasks(0), result(0),
  firstBlock(new(zone) MyBlock(this, 0)),
  lastBlock(firstBlock), jumpOffsetHead(0), jumpOffsetTail(0),
  constantPool(0), constantPoolCount(0)
{ }

} // namespace powerpc
} // namespace codegen
} // namespace avian
