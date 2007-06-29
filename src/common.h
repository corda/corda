#ifndef COMMON_H
#define COMMON_H

#include "stdint.h"
#include "stdlib.h"
#include "stdarg.h"
#include "string.h"
#include "stdio.h"
#include "types.h"

#define NO_RETURN __attribute__((noreturn))

#define LIKELY(v) __builtin_expect((v) != 0, true)
#define UNLIKELY(v) __builtin_expect((v) != 0, false)

#define MACRO_XY(X, Y) X##Y
#define MACRO_MakeNameXY(FX, LINE) MACRO_XY(FX, LINE)
#define MAKE_NAME(FX) MACRO_MakeNameXY(FX, __LINE__)

inline void* operator new(size_t, void* p) throw() { return p; }

namespace vm {

typedef void* object;

const unsigned BytesPerWord = sizeof(uintptr_t);
const unsigned BitsPerWord = BytesPerWord * 8;

const unsigned LikelyPageSizeInBytes = 4 * 1024;

inline unsigned
max(unsigned a, unsigned b)
{
  return (a > b ? a : b);
}

inline unsigned
min(unsigned a, unsigned b)
{
  return (a < b ? a : b);
}

inline unsigned
avg(unsigned a, unsigned b)
{
  return (a + b) / 2;
}

inline unsigned
pad(unsigned n)
{
  unsigned extra = n % BytesPerWord;
  return (extra ? n + BytesPerWord - extra : n);
}

inline unsigned
divide(unsigned n, unsigned d)
{
  if (n and d > n) return 1;
  return (n / d) + (n % d ? 1 : 0);
}

inline bool
powerOfTwo(unsigned n)
{
  for (; n > 2; n >>= 1) if (n & 1) return false;
  return true;
}

inline unsigned
nextPowerOfTwo(unsigned n)
{
  unsigned r = 1;
  while (r < n) r <<= 1;
  return r;
}

inline unsigned
log(unsigned n)
{
  if (n < 3) return 1;
  unsigned r = 0;
  for (unsigned i = 1; i < n; ++r) i <<= 1;
  return r;
}

inline unsigned
wordOf(unsigned i)
{
  return i / BitsPerWord;
}

inline unsigned
bitOf(unsigned i)
{
  return i % BitsPerWord;
}

inline unsigned
indexOf(unsigned word, unsigned bit)
{
  return (word * BitsPerWord) + bit;
}

template <class T>
inline T&
cast(object p, unsigned offset)
{
  return *reinterpret_cast<T*>(static_cast<uint8_t*>(p) + offset);
}

}

#endif//COMMON_H
