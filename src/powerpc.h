/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef POWERPC_H
#define POWERPC_H

#include "types.h"
#include "common.h"

#ifdef __APPLE__
#  if __DARWIN_UNIX03 && defined(_STRUCT_X86_EXCEPTION_STATE32)
#    define IP_REGISTER(context) (context->uc_mcontext->__ss.__srr0)
#    define STACK_REGISTER(context) (context->uc_mcontext->__ss.__r1)
#    define THREAD_REGISTER(context) (context->uc_mcontext->__ss.__r13)
#  else
#    define IP_REGISTER(context) (context->uc_mcontext->ss.srr0)
#    define STACK_REGISTER(context) (context->uc_mcontext->ss.r1)
#    define THREAD_REGISTER(context) (context->uc_mcontext->ss.r13)
#  endif
#else
#  define IP_REGISTER(context) (context->uc_mcontext.gregs[32])
#  define STACK_REGISTER(context) (context->uc_mcontext.gregs[1])
#  define THREAD_REGISTER(context) (context->uc_mcontext.gregs[13])
#endif

extern "C" uint64_t
vmNativeCall(void* function, unsigned stackTotal, void* memoryTable,
             unsigned memoryCount, void* gprTable, void* fprTable,
             unsigned returnType);

namespace vm {

inline void
trap()
{
  asm("trap");
}

inline void
memoryBarrier()
{
  __asm__ __volatile__("sync": : :"memory");
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
syncInstructionCache(const void* start, unsigned size)
{
  const unsigned CacheLineSize = 32;
  const uintptr_t Mask = ~(CacheLineSize - 1);

  uintptr_t cacheLineStart = reinterpret_cast<uintptr_t>(start) & Mask;
  uintptr_t cacheLineEnd
    = (reinterpret_cast<uintptr_t>(start) + size + CacheLineSize - 1) & Mask;

  for (uintptr_t p = cacheLineStart; p < cacheLineEnd; p += CacheLineSize) {
    __asm__ __volatile__("dcbf 0, %0" : : "r" (p));
  }

  __asm__ __volatile__("sync");

  for (uintptr_t p = cacheLineStart; p < cacheLineEnd; p += CacheLineSize) {
    __asm__ __volatile__("icbi 0, %0" : : "r" (p));
  }

  __asm__ __volatile__("isync");
}

#ifdef USE_ATOMIC_OPERATIONS
inline bool
atomicCompareAndSwap(uintptr_t* p, uintptr_t old, uintptr_t new_)
{
#if (__GNUC__ >= 4) && (__GNUC_MINOR__ >= 1)
  return __sync_bool_compare_and_swap(p, old, new_);
#else // not GCC >= 4.1
  // todo: implement using inline assembly
#  undef USE_ATOMIC_OPERATIONS
#endif // not GCC >= 4.1
}
#endif // USE_ATOMIC_OPERATIONS

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned argumentsSize,
            unsigned returnType)
{
  const unsigned LinkageArea = 24;

  const unsigned GprCount = 8;
  uintptr_t gprTable[GprCount];
  unsigned gprIndex = 0;

  const unsigned FprCount = 13;
  uint64_t fprTable[FprCount];
  unsigned fprIndex = 0;

  uintptr_t stack[argumentsSize / BytesPerWord];
  unsigned stackSkip = 0;
  unsigned stackIndex = 0;

  unsigned ai = 0;
  for (unsigned ati = 0; ati < argumentCount; ++ ati) {
    switch (argumentTypes[ati]) {
    case FLOAT_TYPE: {
      if (fprIndex < FprCount) {
        double d = bitsToFloat(arguments[ai]);
        memcpy(fprTable + fprIndex, &d, 8);
        ++ fprIndex;
        ++ gprIndex;
        ++ stackSkip;
      } else {
        stack[stackIndex++] = arguments[ai];
      }
      ++ ai;
    } break;

    case DOUBLE_TYPE: {
      if (fprIndex + (8 / BytesPerWord) <= FprCount) {
        memcpy(fprTable + fprIndex, arguments + ai, 8);
        ++ fprIndex;
        gprIndex += 8 / BytesPerWord;
        stackSkip += 8 / BytesPerWord;
      } else {
        memcpy(stack + stackIndex, arguments + ai, 8);
        stackIndex += 8 / BytesPerWord;
      }
      ai += 8 / BytesPerWord;
    } break;

    case INT64_TYPE: {
      if (gprIndex + (8 / BytesPerWord) <= GprCount) {
        memcpy(gprTable + gprIndex, arguments + ai, 8);
        gprIndex += 8 / BytesPerWord;
        stackSkip += 8 / BytesPerWord;
      } else {
        memcpy(stack + stackIndex, arguments + ai, 8);
        stackIndex += 8 / BytesPerWord;
      }
      ai += 8 / BytesPerWord;
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[ai];
        ++ stackSkip;
      } else {
        stack[stackIndex++] = arguments[ai];
      }
      ++ ai;
    } break;
    }
  }

  return vmNativeCall
    (function,
     - ((((1 + stackSkip + stackIndex) * BytesPerWord) + LinkageArea + 15)
        & -16),
     stack, stackIndex * BytesPerWord,
     (gprIndex ? gprTable : 0),
     (fprIndex ? fprTable : 0), returnType);
}

} // namespace vm

#endif//POWERPC_H
