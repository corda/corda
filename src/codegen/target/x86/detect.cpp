
/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "context.h"

// Note: this is so that we can build the x86 backend(s) on an arm machine.
// This way, we could (in theory) do a bootimage cross-compile from arm to x86
#ifndef __arm__

#ifndef _MSC_VER
#include <cpuid.h>
#else
// MSVC implementation:
static int __get_cpuid(unsigned int __level,
                       unsigned int* __eax,
                       unsigned int* __ebx,
                       unsigned int* __ecx,
                       unsigned int* __edx)
{
  _asm
  {
    mov eax, __level;
    cpuid;
    mov[__eax], eax;
    mov[__ebx], ebx;
    mov[__ecx], ecx;
    mov[__edx], edx;
  }
  return 1;
}
#define bit_SSE (1 << 25)
#define bit_SSE2 (1 << 26)

#endif  // ndef _MSC_VER

#endif  // ndef __arm__

namespace avian {
namespace codegen {
namespace x86 {

// TODO: this should be moved such that it's called by the client (e.g. whatever
// allocates the Archivecture).  That way, we can link the x86 code generator on
// another architecture (e.g. arm).

bool useSSE(ArchitectureContext* c)
{
#ifdef __arm__
  // We can't link in the detection code on arm (DUH!)
  return vm::TargetBytesPerWord == 8;
#else
  if (vm::TargetBytesPerWord == 8) {
    // amd64 implies SSE2 support
    return true;
  } else if (c->useNativeFeatures) {
    static int supported = -1;
    if (supported == -1) {
      unsigned eax;
      unsigned ebx;
      unsigned ecx;
      unsigned edx;
      supported = __get_cpuid(1, &eax, &ebx, &ecx, &edx) && (edx & bit_SSE) && (edx & bit_SSE2);
    }
    return supported;
  } else {
    return false;
  }
#endif
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
