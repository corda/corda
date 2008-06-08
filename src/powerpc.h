/* Copyright (c) 2008, Avian Contributors

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
#    define BASE_REGISTER(context) (context->uc_mcontext->__ss.__r13)
#    define STACK_REGISTER(context) (context->uc_mcontext->__ss.__r1)
#    define THREAD_REGISTER(context) (context->uc_mcontext->__ss.__r14)
#  else
#    define IP_REGISTER(context) (context->uc_mcontext->ss.srr0)
#    define BASE_REGISTER(context) (context->uc_mcontext->ss.r13)
#    define STACK_REGISTER(context) (context->uc_mcontext->ss.r1)
#    define THREAD_REGISTER(context) (context->uc_mcontext->ss.r14)
#  endif
#else
#  define IP_REGISTER(context) (context->uc_mcontext.gregs[32])
#  define BASE_REGISTER(context) (context->uc_mcontext.gregs[13])
#  define STACK_REGISTER(context) (context->uc_mcontext.gregs[1])
#  define THREAD_REGISTER(context) (context->uc_mcontext.gregs[14])
#endif

extern "C" uint64_t
vmNativeCall(void* function, unsigned stackTotal, void* memoryTable,
             unsigned memoryCount, void* gprTable, void* fprTable,
             unsigned returnType);

namespace vm {

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t* argumentTypes,
            unsigned, unsigned argumentsSize, unsigned returnType)
{
  const unsigned GprCount = 8;
  uintptr_t gprTable[GprCount];
  unsigned gprIndex = 0;

  const unsigned FprCount = 13;
  uint64_t fprTable[FprCount];
  unsigned fprIndex = 0;

  uint64_t stack[argumentsSize];
  unsigned stackSkip = 0;
  unsigned stackIndex = 0;

  for (unsigned i = 0; i < argumentsSize; ++i) {
    switch (argumentTypes[i]) {
    case FLOAT_TYPE: {
      if (fprIndex < FprCount) {
        fprTable[fprIndex++] = arguments[i];
        ++ gprIndex;
        ++ stackSkip;
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;

    case DOUBLE_TYPE: {
      if (fprIndex < FprCount) {
        memcpy(fprTable + fprIndex, arguments + i, 8);
        ++ fprIndex;
        gprIndex += BytesPerWord / 4;
        stackSkip += BytesPerWord / 4;
        i += (BytesPerWord / 4) - 1;
      } else {
        memcpy(stack + stackIndex, arguments + i, 8);
        stackIndex += BytesPerWord / 4;
        i += (BytesPerWord / 4) - 1;
      }
    } break;

    case INT64_TYPE: {
      if (gprIndex < GprCount) {
        memcpy(gprTable + gprIndex, arguments + i, 8);
        gprIndex += BytesPerWord / 4;
        stackSkip += BytesPerWord / 4;
        i += (BytesPerWord / 4) - 1;
      } else {
        memcpy(stack + stackIndex, arguments + i, 8);
        stackIndex += BytesPerWord / 4;
        i += (BytesPerWord / 4) - 1;
      }
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[i];
        ++ stackSkip;
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;
    }
  }

  return vmNativeCall(function, (stackSkip + stackIndex) * BytesPerWord,
                      stack, stackIndex,
                      (gprIndex ? gprTable : 0),
                      (fprIndex ? fprTable : 0), returnType);
}

} // namespace vm

#endif//POWERPC_H
