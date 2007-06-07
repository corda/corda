#ifndef COMMON_H
#define COMMON_H

#include "stdint.h"
#include "stdarg.h"
#include "string.h"
#include "stdio.h"

#define NO_RETURN __attribute__((noreturn))
#define UNLIKELY(v) __builtin_expect(v, 0)

#define MACRO_XY(X, Y) X##Y
#define MACRO_MakeNameXY(FX, LINE) MACRO_XY(FX, LINE)
#define MAKE_NAME(FX) MACRO_MakeNameXY(FX, __LINE__)

#endif//COMMON_H
