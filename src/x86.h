#ifndef X86_H
#define X86_H

#include "types.h"
#include "stdint.h"

#ifdef __i386__

extern "C" uint64_t
cdeclCall(void* function, void* stack, unsigned stackSize,
          unsigned returnType);

namespace vm {

inline uint64_t
dynamicCall(void* function, uintptr_t* arguments, uint8_t*,
            unsigned, unsigned argumentsSize, unsigned returnType)
{
  return cdeclCall(function, arguments, argumentsSize, returnType);
}

} // namespace vm

#elif defined __x86_64__

extern "C" uint64_t
amd64Call(void* function, void* stack, unsigned stackSize,
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

  return amd64Call(function, stack, stackIndex * 8, (gprIndex ? gprTable : 0),
                   (sseIndex ? sseTable : 0), returnType);
}

} // namespace vm

#else
#  error unsupported platform
#endif


#endif//X86_H
