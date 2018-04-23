/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/common.h"

#include <avian/codegen/targets.h>

#include "avian/environment.h"

namespace avian {
namespace codegen {

Architecture* makeArchitectureNative(vm::System* system,
                                     bool useNativeFeatures UNUSED)
{
#ifndef AVIAN_TARGET_ARCH
#error "Must specify native target!"
#endif

#if AVIAN_TARGET_ARCH == AVIAN_ARCH_UNKNOWN
  system->abort();
  return 0;
#elif(AVIAN_TARGET_ARCH == AVIAN_ARCH_X86) \
    || (AVIAN_TARGET_ARCH == AVIAN_ARCH_X86_64)
  return makeArchitectureX86(system, useNativeFeatures);
#elif (AVIAN_TARGET_ARCH == AVIAN_ARCH_ARM) \
    || (AVIAN_TARGET_ARCH == AVIAN_ARCH_ARM64)
  return makeArchitectureArm(system, useNativeFeatures);
#else
#error "Unsupported codegen target"
#endif
}

}  // namespace codegen
}  // namespace avian
