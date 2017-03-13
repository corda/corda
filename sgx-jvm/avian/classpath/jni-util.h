/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef JNI_UTIL
#define JNI_UTIL

#include "stdio.h"
#include "stdlib.h"
#include "string.h"

#include <avian/util/runtime-array.h>

#undef JNIEXPORT

#ifdef UNUSED
#undef UNUSED
#endif

#if (defined __MINGW32__) || (defined _MSC_VER)
#define PLATFORM_WINDOWS
#define PATH_SEPARATOR ';'
#define JNIEXPORT __declspec(dllexport)
#else  // not (defined __MINGW32__) || (defined _MSC_VER)
#define PLATFORM_POSIX
#define PATH_SEPARATOR ':'
#define JNIEXPORT __attribute__((visibility("default"))) __attribute__((used))
#endif  // not (defined __MINGW32__) || (defined _MSC_VER)

#ifdef _MSC_VER

#define UNUSED

typedef char int8_t;
typedef unsigned char uint8_t;
typedef short int16_t;
typedef unsigned short uint16_t;
typedef int int32_t;
typedef unsigned int uint32_t;
typedef __int64 int64_t;
typedef unsigned __int64 uint64_t;

#define INT32_MAX 2147483647

#define not!
#define or ||
#define and &&
#define xor ^

#ifdef _M_IX86
#define ARCH_x86_32
#elif defined _M_X64
#define ARCH_x86_64
#endif

#else  // not _MSC_VER

#define UNUSED __attribute__((unused))

#include "stdint.h"
#include "errno.h"

#ifdef __i386__
#define ARCH_x86_32
#elif defined __x86_64__
#define ARCH_x86_64
#elif defined __arm__
#define ARCH_arm
#elif defined __aarch64__
#define ARCH_arm64
#endif

#endif  // not _MSC_VER

inline void throwNew(JNIEnv* e, const char* class_, const char* message, ...)
{
  jclass c = e->FindClass(class_);
  if (c) {
    if (message) {
      static const unsigned BufferSize = 256;
      char buffer[BufferSize];

      va_list list;
      va_start(list, message);
#ifdef _MSC_VER
      vsnprintf_s(buffer, BufferSize - 1, _TRUNCATE, message, list);
#else
      vsnprintf(buffer, BufferSize - 1, message, list);
#endif
      va_end(list);

      e->ThrowNew(c, buffer);
    } else {
      e->ThrowNew(c, 0);
    }
    e->DeleteLocalRef(c);
  }
}

inline void throwNewErrno(JNIEnv* e, const char* class_)
{
#ifdef _MSC_VER
  const unsigned size = 128;
  char buffer[size];
  strerror_s(buffer, size, errno);
  throwNew(e, class_, buffer);
#else
  throwNew(e, class_, strerror(errno));
#endif
}

inline void* allocate(JNIEnv* e, unsigned size)
{
  void* p = malloc(size);
  if (p == 0) {
    throwNew(e, "java/lang/OutOfMemoryError", 0);
  }
  return p;
}

#endif  // JNI_UTIL
