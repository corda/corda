
/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "context.h"

namespace avian {
namespace codegen {
namespace x86 {

extern "C" bool
detectFeature(unsigned ecx, unsigned edx);

bool useSSE(ArchitectureContext* c) {
  if (vm::TargetBytesPerWord == 8) {
    // amd64 implies SSE2 support
    return true;
  } else if (c->useNativeFeatures) {
    static int supported = -1;
    if (supported == -1) {
      supported = detectFeature(0, 0x2000000) // SSE 1
        and detectFeature(0, 0x4000000); // SSE 2
    }
    return supported;
  } else {
    return false;
  }
}

} // namespace x86
} // namespace codegen
} // namespace avian
