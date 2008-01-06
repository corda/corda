#ifndef COMMON_H
#define COMMON_H

#include "stdint.h"
#include "stdlib.h"
#include "stdarg.h"
#include "stddef.h"
#include "string.h"
#include "stdio.h"
#include "types.h"
#include "math.h"

#undef JNIEXPORT
#ifdef __MINGW32__
#  define JNIEXPORT __declspec(dllexport)
#else
#  define JNIEXPORT __attribute__ ((visibility("default")))
#endif

#ifdef __i386__
#  define LD "d"
#ifdef __APPLE__
#  define ULD "lu"
#else
#  define ULD "u"
#endif
#  define LLD "lld"
#elif defined __x86_64__
#  define LD "ld"
#  define ULD "lu"
#  define LLD "ld"
#else
#  error "Unsupported architecture"
#endif

#ifdef __MINGW32__
#  define SO_PREFIX ""
#else
#  define SO_PREFIX "lib"
#endif

#ifdef __APPLE__
#  define SO_SUFFIX ".jnilib"
#elif defined __MINGW32__
#  define SO_SUFFIX ".dll"
#else
#  define SO_SUFFIX ".so"
#endif

#ifdef __APPLE__
#  define FORCE_ALIGN __attribute__((force_align_arg_pointer))
#else
#  define FORCE_ALIGN
#endif

#define NO_RETURN __attribute__((noreturn))

#define LIKELY(v) __builtin_expect((v) != 0, true)
#define UNLIKELY(v) __builtin_expect((v) != 0, false)

#define MACRO_XY(X, Y) X##Y
#define MACRO_MakeNameXY(FX, LINE) MACRO_XY(FX, LINE)
#define MAKE_NAME(FX) MACRO_MakeNameXY(FX, __LINE__)

#define UNUSED __attribute__((unused))

inline void* operator new(size_t, void* p) throw() { return p; }

namespace vm {

const unsigned BytesPerWord = sizeof(uintptr_t);
const unsigned BitsPerWord = BytesPerWord * 8;

const uintptr_t PointerMask
= ((~static_cast<uintptr_t>(0)) / BytesPerWord) * BytesPerWord;

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
  return (n + (BytesPerWord - 1)) & ~(BytesPerWord - 1);
}

inline unsigned
ceiling(unsigned n, unsigned d)
{
  return (n + d - 1) / d;
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

inline void
markBit(uintptr_t* map, unsigned i)
{
  map[wordOf(i)] |= static_cast<uintptr_t>(1) << bitOf(i);
}

inline void
clearBit(uintptr_t* map, unsigned i)
{
  map[wordOf(i)] &= ~(static_cast<uintptr_t>(1) << bitOf(i));
}

inline unsigned
getBit(uintptr_t* map, unsigned i)
{
  return (map[wordOf(i)] & (static_cast<uintptr_t>(1) << bitOf(i)))
    >> bitOf(i);
}

inline void
clearBits(uintptr_t* map, unsigned bitsPerRecord, unsigned index)
{
  for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
    clearBit(map, i);
  }
}

inline void
setBits(uintptr_t* map, unsigned bitsPerRecord, int index, unsigned v)
{
  for (int i = index + bitsPerRecord - 1; i >= index; --i) {
    if (v & 1) markBit(map, i); else clearBit(map, i);
    v >>= 1;
  }
}

inline unsigned
getBits(uintptr_t* map, unsigned bitsPerRecord, unsigned index)
{
  unsigned v = 0;
  for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
    v <<= 1;
    v |= getBit(map, i);
  }
  return v;
}

template <class T>
inline T&
cast(void* p, unsigned offset)
{
  return *reinterpret_cast<T*>(static_cast<uint8_t*>(p) + offset);
}

template <class T>
inline T*
mask(T* p)
{
  return reinterpret_cast<T*>(reinterpret_cast<uintptr_t>(p) & PointerMask);
}

inline uint32_t
hash(const char* s)
{
  uint32_t h = 0;
  for (unsigned i = 0; s[i]; ++i) {
    h = (h * 31) + s[i];
  }
  return h;  
}

inline uint32_t
hash(const uint8_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) {
    h = (h * 31) + s[i];
  }
  return h;
}

inline uint32_t
hash(const int8_t* s, unsigned length)
{
  return hash(reinterpret_cast<const uint8_t*>(s), length);
}

inline uint32_t
hash(const uint16_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) {
    h = (h * 31) + s[i];
  }
  return h;
}

inline uint32_t
floatToBits(float f)
{
  uint32_t bits; memcpy(&bits, &f, 4);
  return bits;
}

inline uint64_t
doubleToBits(double d)
{
  uint64_t bits; memcpy(&bits, &d, 8);
  return bits;
}

inline double
bitsToDouble(uint64_t bits)
{
  double d; memcpy(&d, &bits, 8);
  return d;
}

inline float
bitsToFloat(uint32_t bits)
{
  float f; memcpy(&f, &bits, 4);
  return f;
}

inline intptr_t
difference(void* a, void* b)
{
  return reinterpret_cast<intptr_t>(a) - reinterpret_cast<intptr_t>(b);
}

class Machine;
class Thread;

struct Object { };

typedef Object* object;

} // namespace vm

#endif//COMMON_H
