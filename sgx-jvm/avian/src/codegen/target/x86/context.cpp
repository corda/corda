/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/util/allocator.h"
#include "avian/zone.h"

#include "context.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace x86 {

ArchitectureContext::ArchitectureContext(vm::System* s, bool useNativeFeatures)
    : s(s), useNativeFeatures(useNativeFeatures)
{
}

Context::Context(vm::System* s,
                 util::Alloc* a,
                 vm::Zone* zone,
                 ArchitectureContext* ac)
    : s(s),
      zone(zone),
      client(0),
      code(s, a, 1024),
      tasks(0),
      result(0),
      firstBlock(new (zone) MyBlock(0)),
      lastBlock(firstBlock),
      ac(ac)
{
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
