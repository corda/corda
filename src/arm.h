/* Copyright (c) 2008-2010, Avian Contributors

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

#define IP_REGISTER(context) (context->uc_mcontext.arm_pc)
#define STACK_REGISTER(context) (context->uc_mcontext.arm_sp)
#define THREAD_REGISTER(context) (context->uc_mcontext.arm_ip)

extern "C" uint64_t
vmNativeCall(void* function, unsigned stackTotal, void* memoryTable,
             unsigned memoryCount, void* gprTable);

namespace vm {

inline void
trap()
{
  asm("bkpt");
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

typedef int (__kernel_cmpxchg_t)(int oldval, int newval, int *ptr);
#define __kernel_cmpxchg (*(__kernel_cmpxchg_t *)0xffff0fc0)

inline bool
atomicCompareAndSwap32(uint32_t* p, uint32_t old, uint32_t new_)
{
  int r = __kernel_cmpxchg(static_cast<int>(old), static_cast<int>(new_), reinterpret_cast<int*>(p));
  return (!r ? true : false);
}

inline bool
atomicCompareAndSwap(uintptr_t* p, uintptr_t old, uintptr_t new_)
{
  return atomicCompareAndSwap32(reinterpret_cast<uint32_t*>(p), old, new_);
}

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned argumentsSize UNUSED,
            unsigned returnType UNUSED)
{
  const unsigned GprCount = 4;
  uintptr_t gprTable[GprCount];
  unsigned gprIndex = 0;

  uintptr_t stack[(argumentCount * 8) / BytesPerWord]; // is > argumentSize to account for padding
  unsigned stackIndex = 0;

  unsigned ai = 0;
  for (unsigned ati = 0; ati < argumentCount; ++ ati) {
    switch (argumentTypes[ati]) {
    case DOUBLE_TYPE:
    case INT64_TYPE: {
      if (gprIndex + (8 / BytesPerWord) <= GprCount) { // pass argument on registers
        if (gprIndex & 1) {                            // 8-byte alignment
          memset(gprTable + gprIndex, 0, 4);           // probably not necessary, but for good luck
          ++gprIndex;
        }
        memcpy(gprTable + gprIndex, arguments + ai, 8);
        gprIndex += 8 / BytesPerWord;
      } else {                                         // pass argument on stack
        gprIndex = GprCount;
        if (stackIndex & 1) {                          // 8-byte alignment
          memset(stack + stackIndex, 0, 4);            // probably not necessary, but for good luck
          ++stackIndex;
        }
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
