/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ARM_H
#define ARM_H

#include "types.h"
#include "common.h"

#define IP_REGISTER(context) (context->uc_mcontext.gregs[15])
#define STACK_REGISTER(context) (context->uc_mcontext.gregs[13])
#define THREAD_REGISTER(context) (context->uc_mcontext.gregs[12])

extern "C" uint64_t
vmNativeCall(void* function, unsigned stackTotal, void* memoryTable,
             unsigned memoryCount, void* gprTable);

namespace vm {

inline void
trap()
{
  asm("nop");
}

inline void
memoryBarrier()
{
  asm("nop");
}

inline void
storeStoreMemoryBarrier()
{
  memoryBarrier();
}

inline void
storeLoadMemoryBarrier()
{
  memoryBarrier();
}

inline void
loadMemoryBarrier()
{
  memoryBarrier();
}

inline void
syncInstructionCache(const void* start UNUSED, unsigned size UNUSED)
{
  asm("nop");
}

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned argumentsSize,
            unsigned returnType UNUSED)
{
  const unsigned GprCount = 4;
  uintptr_t gprTable[GprCount];
  unsigned gprIndex = 0;

  uintptr_t stack[argumentsSize / BytesPerWord];
  unsigned stackIndex = 0;

  unsigned ai = 0;
  for (unsigned ati = 0; ati < argumentCount; ++ ati) {
    switch (argumentTypes[ati]) {
    case DOUBLE_TYPE:
    case INT64_TYPE: {
      if (gprIndex + (8 / BytesPerWord) <= GprCount) {
        memcpy(gprTable + gprIndex, arguments + ai, 8);
        gprIndex += 8 / BytesPerWord;
      } else if (gprIndex == GprCount-1) { // split between last GPR and stack
        memcpy(gprTable + gprIndex, arguments + ai, 4);
        ++gprIndex;
        memcpy(stack + stackIndex, arguments + ai + 4, 4);
        ++stackIndex;
      } else {
        memcpy(stack + stackIndex, arguments + ai, 8);
        stackIndex += 8 / BytesPerWord;
      }
      ai += 8 / BytesPerWord;
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[ai];
      } else {
        stack[stackIndex++] = arguments[ai];
      }
      ++ ai;
    } break;
    }
  }

  if (gprIndex < GprCount) { // pad since assembly loads all GPRs
    memset(gprTable + gprIndex, 0, (GprCount-gprIndex)*4);
    gprIndex = GprCount;
  }

  unsigned stackSize = stackIndex*BytesPerWord + ((stackIndex & 1) << 2);
  return vmNativeCall
    (function, stackSize, stack, stackIndex * BytesPerWord,
     (gprIndex ? gprTable : 0));
}

} // namespace vm

#endif // ARM_H
