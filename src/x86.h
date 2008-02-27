/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef X86_H
#define X86_H

#include "types.h"
#include "stdint.h"
#include "common.h"

extern "C" void NO_RETURN
vmJump(void* address, void* base, void* stack, void* thread);

#ifdef __i386__

extern "C" uint64_t
vmNativeCall(void* function, void* stack, unsigned stackSize,
             unsigned returnType);

namespace vm {

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t*,
            unsigned, unsigned argumentsSize, unsigned returnType)
{
  return vmNativeCall(function, arguments, argumentsSize, returnType);
}

} // namespace vm

#elif defined __x86_64__

extern "C" uint64_t
vmNativeCall(void* function, void* stack, unsigned stackSize,
             void* gprTable, void* sseTable, unsigned returnType);

namespace vm {

inline uint64_t
dynamicCall(void* function, uint64_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned, unsigned returnType)
{
  const unsigned GprCount = 6;
  uint64_t gprTable[GprCount];
  unsigned gprIndex = 0;

  const unsigned SseCount = 8;
  uint64_t sseTable[SseCount];
  unsigned sseIndex = 0;

  uint64_t stack[argumentCount];
  unsigned stackIndex = 0;

  for (unsigned i = 0; i < argumentCount; ++i) {
    switch (argumentTypes[i]) {
    case FLOAT_TYPE:
    case DOUBLE_TYPE: {
      if (sseIndex < SseCount) {
        sseTable[sseIndex++] = arguments[i];
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[i];
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;
    }
  }

  return vmNativeCall(function, stack, stackIndex * 8,
                      (gprIndex ? gprTable : 0),
                      (sseIndex ? sseTable : 0), returnType);
}

} // namespace vm

#else
#  error unsupported platform
#endif


#endif//X86_H
