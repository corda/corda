#ifndef COMMON_H
#define COMMON_H

#include "stdint.h"
#include "stdlib.h"
#include "stdarg.h"
#include "string.h"
#include "stdio.h"

#define NO_RETURN __attribute__((noreturn))
#define LIKELY(v) __builtin_expect((v) != 0, true)
#define UNLIKELY(v) __builtin_expect((v) == 0, true)

#define MACRO_XY(X, Y) X##Y
#define MACRO_MakeNameXY(FX, LINE) MACRO_XY(FX, LINE)
#define MAKE_NAME(FX) MACRO_MakeNameXY(FX, __LINE__)

namespace vm {

typedef void* object;

const unsigned BytesPerWord = sizeof(uintptr_t);
const unsigned BitsPerWord = BytesPerWord * 8;

const unsigned LikelyPageSize = 4 * 1024;

}

#endif//COMMON_H
