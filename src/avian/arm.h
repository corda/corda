/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ARM_H
#define ARM_H

#include "avian/types.h"
#include "avian/common.h"
#include <avian/util/runtime-array.h>

#ifdef __APPLE__
#  include "libkern/OSAtomic.h"
#  include "libkern/OSCacheControl.h"
#  include "mach/mach_types.h"
#  include "mach/thread_act.h"
#  include "mach/thread_status.h"

#  define THREAD_STATE ARM_THREAD_STATE
#  define THREAD_STATE_TYPE arm_thread_state_t
#  define THREAD_STATE_COUNT ARM_THREAD_STATE_COUNT

#  if __DARWIN_UNIX03 && defined(_STRUCT_ARM_EXCEPTION_STATE)
#    define FIELD(x) __##x
#  else
#    define FIELD(x) x
#  endif

#  define THREAD_STATE_IP(state) ((state).FIELD(pc))
#  define THREAD_STATE_STACK(state) ((state).FIELD(sp))
#  define THREAD_STATE_THREAD(state) ((state).FIELD(r[8]))
#  define THREAD_STATE_LINK(state) ((state).FIELD(lr))

#  define IP_REGISTER(context) \
  THREAD_STATE_IP(context->uc_mcontext->FIELD(ss))
#  define STACK_REGISTER(context) \
  THREAD_STATE_STACK(context->uc_mcontext->FIELD(ss))
#  define THREAD_REGISTER(context) \
  THREAD_STATE_THREAD(context->uc_mcontext->FIELD(ss))
#  define LINK_REGISTER(context) \
  THREAD_STATE_LINK(context->uc_mcontext->FIELD(ss))
#elif (defined __QNX__)
#  include "arm/smpxchg.h"
#  include "sys/mman.h"

#  define IP_REGISTER(context) (context->uc_mcontext.cpu.gpr[ARM_REG_PC])
#  define STACK_REGISTER(context) (context->uc_mcontext.cpu.gpr[ARM_REG_SP])
#  define THREAD_REGISTER(context) (context->uc_mcontext.cpu.gpr[ARM_REG_IP])
#  define LINK_REGISTER(context) (context->uc_mcontext.cpu.gpr[ARM_REG_LR])
#else
#  define IP_REGISTER(context) (context->uc_mcontext.arm_pc)
#  define STACK_REGISTER(context) (context->uc_mcontext.arm_sp)
#  define THREAD_REGISTER(context) (context->uc_mcontext.arm_ip)
#  define LINK_REGISTER(context) (context->uc_mcontext.arm_lr)
#endif

#define VA_LIST(x) (&(x))

extern "C" uint64_t
vmNativeCall(void* function, unsigned stackTotal, void* memoryTable,
             unsigned memoryCount, void* gprTable, void* vfpTable,
             unsigned returnType);

namespace vm {

inline void
trap()
{
#ifdef _MSC_VER
  __debugbreak();
#else
  asm("bkpt");
#endif
}

// todo: determine the minimal operation types and domains needed to
// implement the following barriers (see
// http://community.arm.com/groups/processors/blog/2011/10/19/memory-access-ordering-part-3--memory-access-ordering-in-the-arm-architecture).
// For now, we just use DMB SY as a conservative but not necessarily
// performant choice.

#ifndef _MSC_VER
inline void
memoryBarrier()
{
#ifdef __APPLE__
  OSMemoryBarrier();
#elif (__GNUC__ >= 4) && (__GNUC_MINOR__ >= 1)
  return __sync_synchronize();
#elif (! defined AVIAN_ASSUME_ARMV6)
  __asm__ __volatile__ ("dmb" : : : "memory");
#else
  __asm__ __volatile__ ("" : : : "memory");
#endif
}
#endif

inline void
storeStoreMemoryBarrier()
{
#ifdef _MSC_VER
  _ReadWriteBarrier();
#else
  memoryBarrier();
#endif
}

inline void
storeLoadMemoryBarrier()
{
#ifdef _MSC_VER
  MemoryBarrier();
#else
  memoryBarrier();
#endif
}

inline void
loadMemoryBarrier()
{
#ifdef _MSC_VER
  _ReadWriteBarrier();
#else
  memoryBarrier();
#endif
}

#if !defined(AVIAN_AOT_ONLY)

#if defined(__ANDROID__)
// http://code.google.com/p/android/issues/detail?id=1803
extern "C" void __clear_cache (void *beg __attribute__((__unused__)), void *end __attribute__((__unused__)));
#endif
inline void
syncInstructionCache(const void* start, unsigned size)
{
#ifdef __APPLE__
  sys_icache_invalidate(const_cast<void*>(start), size);
#elif (defined __QNX__)
  msync(const_cast<void*>(start), size, MS_INVALIDATE_ICACHE);
#else
  __clear_cache
    (const_cast<void*>(start),
     const_cast<uint8_t*>(static_cast<const uint8_t*>(start) + size));
#endif
}

#endif // AVIAN_AOT_ONLY

#ifndef __APPLE__
typedef int (__kernel_cmpxchg_t)(int oldval, int newval, int *ptr);
#  define __kernel_cmpxchg (*(__kernel_cmpxchg_t *)0xffff0fc0)
#endif

inline bool
atomicCompareAndSwap32(uint32_t* p, uint32_t old, uint32_t new_)
{
#ifdef __APPLE__
  return OSAtomicCompareAndSwap32Barrier(old, new_, reinterpret_cast<int32_t*>(p));
#elif (defined __QNX__)
  return old == _smp_cmpxchg(p, old, new_);
#else
  int r = __kernel_cmpxchg(static_cast<int>(old), static_cast<int>(new_), reinterpret_cast<int*>(p));
  return (!r ? true : false);
#endif
}

inline bool
atomicCompareAndSwap(uintptr_t* p, uintptr_t old, uintptr_t new_)
{
  return atomicCompareAndSwap32(reinterpret_cast<uint32_t*>(p), old, new_);
}

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned argumentsSize UNUSED,
            unsigned returnType)
{
#ifdef __APPLE__
  const unsigned Alignment = 1;
#else
  const unsigned Alignment = 2;
#endif

  const unsigned GprCount = 4;
  uintptr_t gprTable[GprCount];
  unsigned gprIndex = 0;

  const unsigned VfpCount = 16;
  uintptr_t vfpTable[VfpCount];
  unsigned vfpIndex = 0;
  unsigned vfpBackfillIndex UNUSED = 0;

  RUNTIME_ARRAY(uintptr_t, stack, (argumentCount * 8) / BytesPerWord); // is > argumentSize to account for padding
  unsigned stackIndex = 0;

  unsigned ai = 0;
  for (unsigned ati = 0; ati < argumentCount; ++ ati) {
    switch (argumentTypes[ati]) {
    case DOUBLE_TYPE:
#if defined(__ARM_PCS_VFP)
      {
        if (vfpIndex + Alignment <= VfpCount) {
          if (vfpIndex % Alignment) {
            vfpBackfillIndex = vfpIndex;
            ++ vfpIndex;
          }
          
          memcpy(vfpTable + vfpIndex, arguments + ai, 8);
          vfpIndex += 8 / BytesPerWord;
        } else {
          vfpIndex = VfpCount;
          if (stackIndex % Alignment) {
            ++ stackIndex;
          }

          memcpy(RUNTIME_ARRAY_BODY(stack) + stackIndex, arguments + ai, 8);
          stackIndex += 8 / BytesPerWord;
        }
        ai += 8 / BytesPerWord;
      } break;

    case FLOAT_TYPE:
      if (vfpBackfillIndex) {
        vfpTable[vfpBackfillIndex] = arguments[ai];
        vfpBackfillIndex = 0;
      } else if (vfpIndex < VfpCount) {
        vfpTable[vfpIndex++] = arguments[ai];
      } else {
        RUNTIME_ARRAY_BODY(stack)[stackIndex++] = arguments[ai];
      }
      ++ ai;
      break;
#endif
    case INT64_TYPE: {
      if (gprIndex + Alignment <= GprCount) { // pass argument in register(s)
        if (Alignment == 1
            and BytesPerWord < 8
            and gprIndex + Alignment == GprCount)
        {
          gprTable[gprIndex++] = arguments[ai];
          RUNTIME_ARRAY_BODY(stack)[stackIndex++] = arguments[ai + 1];
        } else {
          if (gprIndex % Alignment) {
            ++gprIndex;
          }
          
          memcpy(gprTable + gprIndex, arguments + ai, 8);
          gprIndex += 8 / BytesPerWord;
        }
      } else {                                // pass argument on stack
        gprIndex = GprCount;
        if (stackIndex % Alignment) {
          ++stackIndex;
        }

        memcpy(RUNTIME_ARRAY_BODY(stack) + stackIndex, arguments + ai, 8);
        stackIndex += 8 / BytesPerWord;
      }
      ai += 8 / BytesPerWord;
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[ai];
      } else {
        RUNTIME_ARRAY_BODY(stack)[stackIndex++] = arguments[ai];
      }
      ++ ai;
    } break;
    }
  }

  if (gprIndex < GprCount) { // pad since assembly loads all GPRs
    memset(gprTable + gprIndex, 0, (GprCount-gprIndex)*4);
    gprIndex = GprCount;
  }
  if (vfpIndex < VfpCount) {
    memset(vfpTable + vfpIndex, 0, (VfpCount-vfpIndex)*4);
    vfpIndex = VfpCount;
  }

  unsigned stackSize = stackIndex*BytesPerWord + ((stackIndex & 1) << 2);
  return vmNativeCall
    (function, stackSize, RUNTIME_ARRAY_BODY(stack), stackIndex * BytesPerWord,
     (gprIndex ? gprTable : 0),
     (vfpIndex ? vfpTable : 0), returnType);
}

} // namespace vm

#endif // ARM_H
