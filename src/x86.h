/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef X86_H
#define X86_H

#include "types.h"
#include "common.h"

#ifdef _MSC_VER
#  include "windows.h"
#  pragma push_macro("assert")
#  include "intrin.h"
#  pragma pop_macro("assert")
#  undef interface
#endif

#ifdef ARCH_x86_32

#  ifdef __APPLE__
#    if __DARWIN_UNIX03 && defined(_STRUCT_X86_EXCEPTION_STATE32)
#      define IP_REGISTER(context) (context->uc_mcontext->__ss.__eip)
#      define BASE_REGISTER(context) (context->uc_mcontext->__ss.__ebp)
#      define STACK_REGISTER(context) (context->uc_mcontext->__ss.__esp)
#      define THREAD_REGISTER(context) (context->uc_mcontext->__ss.__ebx)
#    else
#      define IP_REGISTER(context) (context->uc_mcontext->ss.eip)
#      define BASE_REGISTER(context) (context->uc_mcontext->ss.ebp)
#      define STACK_REGISTER(context) (context->uc_mcontext->ss.esp)
#      define THREAD_REGISTER(context) (context->uc_mcontext->ss.ebx)
#    endif
#  else
#    define IP_REGISTER(context) (context->uc_mcontext.gregs[REG_EIP])
#    define BASE_REGISTER(context) (context->uc_mcontext.gregs[REG_EBP])
#    define STACK_REGISTER(context) (context->uc_mcontext.gregs[REG_ESP])
#    define THREAD_REGISTER(context) (context->uc_mcontext.gregs[REG_EBX])
#  endif

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

#elif defined ARCH_x86_64

#  ifdef __APPLE__
#    if __DARWIN_UNIX03 && defined(_STRUCT_X86_EXCEPTION_STATE32)
#      define IP_REGISTER(context) (context->uc_mcontext->__ss.__rip)
#      define BASE_REGISTER(context) (context->uc_mcontext->__ss.__rbp)
#      define STACK_REGISTER(context) (context->uc_mcontext->__ss.__rsp)
#      define THREAD_REGISTER(context) (context->uc_mcontext->__ss.__rbx)
#    else
#      define IP_REGISTER(context) (context->uc_mcontext->ss.rip)
#      define BASE_REGISTER(context) (context->uc_mcontext->ss.rbp)
#      define STACK_REGISTER(context) (context->uc_mcontext->ss.rsp)
#      define THREAD_REGISTER(context) (context->uc_mcontext->ss.rbx)
#    endif
#  else
#    define IP_REGISTER(context) (context->uc_mcontext.gregs[REG_RIP])
#    define BASE_REGISTER(context) (context->uc_mcontext.gregs[REG_RBP])
#    define STACK_REGISTER(context) (context->uc_mcontext.gregs[REG_RSP])
#    define THREAD_REGISTER(context) (context->uc_mcontext.gregs[REG_RBX])
#  endif

extern "C" uint64_t
#  ifdef PLATFORM_WINDOWS
vmNativeCall(void* function, void* stack, unsigned stackSize,
             unsigned returnType);
#  else
vmNativeCall(void* function, void* stack, unsigned stackSize,
             void* gprTable, void* sseTable, unsigned returnType);
#  endif

namespace vm {

#  ifdef PLATFORM_WINDOWS
inline uint64_t
dynamicCall(void* function, uint64_t* arguments, UNUSED uint8_t* argumentTypes,
            unsigned argumentCount, unsigned, unsigned returnType)
{
  return vmNativeCall(function, arguments, argumentCount, returnType);
}
#  else
inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
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

  return vmNativeCall(function, stack, stackIndex * BytesPerWord,
                      (gprIndex ? gprTable : 0),
                      (sseIndex ? sseTable : 0), returnType);
}
#endif

} // namespace vm

#else
#  error unsupported architecture
#endif

namespace vm {

inline void
trap()
{
#ifdef _MSC_VER
  __asm int 3
#else
  asm("int3");
#endif
}

inline void
programOrderMemoryBarrier()
{
#ifdef _MSC_VER
  _ReadWriteBarrier();
#else
  __asm__ __volatile__("": : :"memory");
#endif
}

inline void
storeStoreMemoryBarrier()
{
  programOrderMemoryBarrier();
}

inline void
storeLoadMemoryBarrier()
{
#ifdef _MSC_VER
  MemoryBarrier();
#elif defined ARCH_x86_32
  __asm__ __volatile__("lock; addl $0,0(%%esp)": : :"memory");
#elif defined ARCH_x86_64
  __asm__ __volatile__("mfence": : :"memory");
#endif // ARCH_x86_64
}

inline void
loadMemoryBarrier()
{
  programOrderMemoryBarrier();
}

inline void
syncInstructionCache(const void*, unsigned)
{
  programOrderMemoryBarrier();
}

#ifdef USE_ATOMIC_OPERATIONS
inline bool
atomicCompareAndSwap32(uint32_t* p, uint32_t old, uint32_t new_)
{
#ifdef _MSC_VER
  InterlockedCompareExchange(p, new_, old);
#elif (__GNUC__ >= 4) && (__GNUC_MINOR__ >= 1)
  return __sync_bool_compare_and_swap(p, old, new_);
#else
  uint8_t result;

  __asm__ __volatile__("lock; cmpxchgl %2, %0; setz %1"
                       : "=m"(*p), "=q"(result)
                       : "r"(new_), "a"(old), "m"(*p)
                       : "memory");

  return result != 0;
#endif
}

inline bool
atomicCompareAndSwap64(uint64_t* p, uint64_t old, uint64_t new_)
{
#ifdef _MSC_VER
  InterlockedCompareExchange64(p, new_, old);
#elif (__GNUC__ >= 4) && (__GNUC_MINOR__ >= 1)
  return __sync_bool_compare_and_swap(p, old, new_);
#else
  uint8_t result;

  __asm__ __volatile__("lock; cmpxchgq %2, %0; setz %1"
                       : "=m"(*p), "=q"(result)
                       : "r"(new_), "a"(old), "m"(*p)
                       : "memory");

  return result != 0;
#endif
}

inline bool
atomicCompareAndSwap(uintptr_t* p, uintptr_t old, uintptr_t new_)
{
#ifdef ARCH_x86_32
  return atomicCompareAndSwap32(reinterpret_cast<uint32_t*>(p), old, new_);
#elif defined ARCH_x86_64
  return atomicCompareAndSwap64(reinterpret_cast<uint64_t*>(p), old, new_);
#endif // ARCH_x86_64
}
#endif // USE_ATOMIC_OPERATIONS

} // namespace vm

#endif//X86_H
