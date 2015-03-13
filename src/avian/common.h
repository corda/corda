/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_COMMON_H
#define AVIAN_COMMON_H

#ifndef __STDC_CONSTANT_MACROS
#define __STDC_CONSTANT_MACROS
#endif

#ifndef __STDC_FORMAT_MACROS
#define __STDC_FORMAT_MACROS
#endif

#include <new>

#include <stdlib.h>
#include <stdarg.h>
#include <stddef.h>
#include <string.h>
#include <stdio.h>
#include <inttypes.h>
#include "avian/types.h"
#include <math.h>

#ifdef UNUSED
#undef UNUSED
#endif

#ifdef _MSC_VER

#include <float.h>
#include <stdint.h>

#ifdef linux
#undef linux
#endif

// don't complain about using 'this' in member initializers:
#pragma warning(disable : 4355)

#define strncasecmp _strnicmp

#define FP_UNDEF 2

#define not !
#define or ||
#define and &&
#define xor ^

#define LIKELY(v) v
#define UNLIKELY(v) v

#define UNUSED

#define NO_RETURN __declspec(noreturn)

#define PACKED

#define PLATFORM_WINDOWS

#ifdef _M_IX86
typedef int32_t intptr_t;
typedef uint32_t uintptr_t;
#define ARCH_x86_32
#define BYTES_PER_WORD 4
#elif defined _M_X64
typedef int64_t intptr_t;
typedef uint64_t uintptr_t;
#define ARCH_x86_64
#define BYTES_PER_WORD 8
#elif defined _M_ARM_FP
typedef int32_t intptr_t;
typedef uint32_t uintptr_t;
#define ARCH_arm
#define BYTES_PER_WORD 4
#else
#error "unsupported architecture"
#endif

namespace vm {

typedef intptr_t intptr_alias_t;

}  // namespace vm

#else  // not _MSC_VER

#include <stdint.h>

#define BYTES_PER_WORD __SIZEOF_POINTER__

#define LIKELY(v) __builtin_expect((v) != 0, true)
#define UNLIKELY(v) __builtin_expect((v) != 0, false)

#define UNUSED __attribute__((unused))

#define NO_RETURN __attribute__((noreturn))

#define PACKED __attribute__((packed))

#ifdef __MINGW32__
#define PLATFORM_WINDOWS
#endif

#ifdef __i386__
#define ARCH_x86_32
#elif defined __x86_64__
#define ARCH_x86_64
#elif defined __arm__
#define ARCH_arm
#elif defined __aarch64__
#define ARCH_arm64
#else
#error "unsupported architecture"
#endif

namespace vm {

typedef intptr_t __attribute__((__may_alias__)) intptr_alias_t;

}  // namespace vm

#endif  // not _MSC_VER

#ifdef PLATFORM_WINDOWS
#define AVIAN_EXPORT __declspec(dllexport)
#define PATH_SEPARATOR ';'
#else  // not PLATFORM_WINDOWS
#define AVIAN_EXPORT \
  __attribute__((visibility("default"))) __attribute__((used))
#define PATH_SEPARATOR ':'
#endif  // not PLATFORM_WINDOWS

#ifdef PRId64
#define LLD PRId64
#define ULD PRIu64
#define LD PRIdPTR
#define LX PRIxPTR
#else
#if (defined ARCH_x86_32) || (defined ARCH_arm)
#define LD "ld"
#if (defined _MSC_VER) || ((defined __MINGW32__) && __GNUC__ >= 4)
#if (__GNUC__ == 4 && __GNUC_MINOR__ <= 8)
#define LLD "I64d"
#else
#define LLD "lld"
#endif
#else
#define LLD "lld"
#endif
#ifdef __APPLE__
#define ULD "lu"
#define LX "lx"
#else
#define LX "x"
#define ULD "u"
#endif
#elif defined ARCH_x86_64
#define LD "ld"
#define LX "lx"
#if (defined _MSC_VER) || (defined __MINGW32__)
#if (__GNUC__ == 4 && __GNUC_MINOR__ <= 8)
#define LLD "I64d"
#define ULD "I64x"
#else
#define LLD "lld"
#define ULD "llu"
#endif
#else
#ifdef __APPLE__
#define LLD "lld"
#else
#define LLD "ld"
#endif
#define ULD "lu"
#endif
#else
#error "Unsupported architecture"
#endif
#endif

#ifdef PLATFORM_WINDOWS
#define SO_PREFIX ""
#else
#define SO_PREFIX "lib"
#endif

#ifdef __APPLE__
#define SO_SUFFIX ".dylib"
#elif defined PLATFORM_WINDOWS
#define SO_SUFFIX ".dll"
#else
#define SO_SUFFIX ".so"
#endif

#define MACRO_XY(X, Y) X##Y
#define MACRO_MakeNameXY(FX, LINE) MACRO_XY(FX, LINE)
#define MAKE_NAME(FX) MACRO_MakeNameXY(FX, __LINE__)

#define RESOURCE(type, name, release)            \
  class MAKE_NAME(Resource_) {                   \
   public:                                       \
    MAKE_NAME(Resource_)(type name) : name(name) \
    {                                            \
    }                                            \
    ~MAKE_NAME(Resource_)()                      \
    {                                            \
      release;                                   \
    }                                            \
                                                 \
   private:                                      \
    type name;                                   \
  } MAKE_NAME(resource_)(name);

#ifdef _MSC_VER
#pragma warning(disable : 4291)
#endif

namespace vm {

inline intptr_alias_t& alias(void* p, unsigned offset)
{
  return *reinterpret_cast<intptr_alias_t*>(static_cast<uint8_t*>(p) + offset);
}

#ifdef _MSC_VER

inline int vsnprintf(char* dst, size_t size, const char* format, va_list a)
{
  return vsnprintf_s(dst, size, _TRUNCATE, format, a);
}

inline int snprintf(char* dst, size_t size, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  int r = vsnprintf(dst, size, format, a);
  va_end(a);
  return r;
}

inline FILE* fopen(const char* name, const char* mode)
{
  FILE* file;
  if (fopen_s(&file, name, mode) == 0) {
    return file;
  } else {
    return 0;
  }
}

#else  // not _MSC_VER

inline int vsnprintf(char* dst, size_t size, const char* format, va_list a)
{
  return ::vsnprintf(dst, size, format, a);
}

inline int snprintf(char* dst, size_t size, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  int r = vsnprintf(dst, size, format, a);
  va_end(a);
  return r;
}

inline FILE* fopen(const char* name, const char* mode)
{
  return ::fopen(name, mode);
}

#endif  // not _MSC_VER

const unsigned BytesPerWord = sizeof(uintptr_t);
const unsigned BitsPerWord = BytesPerWord * 8;

const uintptr_t PointerMask = ((~static_cast<uintptr_t>(0)) / BytesPerWord)
                              * BytesPerWord;

const unsigned LikelyPageSizeInBytes = 4 * 1024;

inline unsigned pad(unsigned n, unsigned alignment)
{
  return (n + (alignment - 1)) & ~(alignment - 1);
}

inline unsigned pad(unsigned n)
{
  return pad(n, BytesPerWord);
}

inline uintptr_t padWord(uintptr_t n, uintptr_t alignment)
{
  return (n + (alignment - 1)) & ~(alignment - 1);
}

inline uintptr_t padWord(uintptr_t n)
{
  return padWord(n, BytesPerWord);
}

inline bool fitsInInt8(int64_t v)
{
  return v == static_cast<int8_t>(v);
}

inline bool fitsInInt16(int64_t v)
{
  return v == static_cast<int16_t>(v);
}

inline bool fitsInInt32(int64_t v)
{
  return v == static_cast<int32_t>(v);
}
template <class T>
inline unsigned wordOf(unsigned i)
{
  return i / (sizeof(T) * 8);
}

inline unsigned wordOf(unsigned i)
{
  return wordOf<uintptr_t>(i);
}

template <class T>
inline unsigned bitOf(unsigned i)
{
  return i % (sizeof(T) * 8);
}

inline unsigned bitOf(unsigned i)
{
  return bitOf<uintptr_t>(i);
}

template <class T>
inline unsigned indexOf(unsigned word, unsigned bit)
{
  return (word * (sizeof(T) * 8)) + bit;
}

inline unsigned indexOf(unsigned word, unsigned bit)
{
  return indexOf<uintptr_t>(word, bit);
}

template <class T>
inline void markBit(T* map, unsigned i)
{
  map[wordOf<T>(i)] |= static_cast<T>(1) << bitOf<T>(i);
}

template <class T>
inline void clearBit(T* map, unsigned i)
{
  map[wordOf<T>(i)] &= ~(static_cast<T>(1) << bitOf<T>(i));
}

template <class T>
inline unsigned getBit(T* map, unsigned i)
{
  return (map[wordOf<T>(i)] & (static_cast<T>(1) << bitOf<T>(i)))
         >> bitOf<T>(i);
}

// todo: the following (clearBits, setBits, and getBits) could be made
// more efficient by operating on a word at a time instead of a bit at
// a time:

template <class T>
inline void clearBits(T* map, unsigned bitsPerRecord, unsigned index)
{
  for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
    clearBit<T>(map, i);
  }
}

template <class T>
inline void setBits(T* map, unsigned bitsPerRecord, int index, unsigned v)
{
  for (int i = index + bitsPerRecord - 1; i >= index; --i) {
    if (v & 1)
      markBit<T>(map, i);
    else
      clearBit<T>(map, i);
    v >>= 1;
  }
}

template <class T>
inline unsigned getBits(T* map, unsigned bitsPerRecord, unsigned index)
{
  unsigned v = 0;
  for (unsigned i = index, limit = index + bitsPerRecord; i < limit; ++i) {
    v <<= 1;
    v |= getBit<T>(map, i);
  }
  return v;
}

template <class T>
inline T& fieldAtOffset(void* p, unsigned offset)
{
  return *reinterpret_cast<T*>(static_cast<uint8_t*>(p) + offset);
}

template <class T>
inline T* maskAlignedPointer(T* p)
{
  return reinterpret_cast<T*>(reinterpret_cast<uintptr_t>(p) & PointerMask);
}

inline void write4(uint8_t* dst, uint32_t v)
{
  memcpy(dst, &v, 4);
}

inline uint32_t floatToBits(float f)
{
  uint32_t bits;
  memcpy(&bits, &f, 4);
  return bits;
}

inline uint64_t doubleToBits(double d)
{
  uint64_t bits;
  memcpy(&bits, &d, 8);
  return bits;
}

inline double bitsToDouble(uint64_t bits)
{
  double d;
  memcpy(&d, &bits, 8);
  return d;
}

inline float bitsToFloat(uint32_t bits)
{
  float f;
  memcpy(&f, &bits, 4);
  return f;
}

inline int difference(void* a, void* b)
{
  return reinterpret_cast<intptr_t>(a) - reinterpret_cast<intptr_t>(b);
}

template <class T>
inline void* voidPointer(T function)
{
  void* p;
  memcpy(&p, &function, sizeof(void*));
  return p;
}

inline void replace(char a, char b, char* c)
{
  for (; *c; ++c)
    if (*c == a)
      *c = b;
}

inline void replace(char a, char b, char* dst, const char* src)
{
  unsigned i = 0;
  for (; src[i]; ++i) {
    dst[i] = src[i] == a ? b : src[i];
  }
  dst[i] = 0;
}

inline bool equal(const void* a, unsigned al, const void* b, unsigned bl)
{
  if (al == bl) {
    return memcmp(a, b, al) == 0;
  } else {
    return false;
  }
}

}  // namespace vm

#endif  // AVIAN_COMMON_H
