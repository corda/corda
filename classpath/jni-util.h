/* Copyright (c) 2008, Avian Contributors

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

#undef JNIEXPORT
#ifdef __MINGW32__
#  define JNIEXPORT __declspec(dllexport)
#else
#  define JNIEXPORT __attribute__ ((visibility("default")))
#endif

#define UNUSED __attribute__((unused))

namespace {

inline void
throwNew(JNIEnv* e, const char* class_, const char* message, ...)
{
  jclass c = e->FindClass(class_);
  if (c) {
    if (message) {
      static const unsigned BufferSize = 256;
      char buffer[BufferSize];

      va_list list;
      va_start(list, message);
      vsnprintf(buffer, BufferSize - 1, message, list);
      va_end(list);
      
      e->ThrowNew(c, buffer);
    } else {
      e->ThrowNew(c, 0);
    }
    e->DeleteLocalRef(c);
  }
}

inline void*
allocate(JNIEnv* e, unsigned size)
{
  void* p = malloc(size);
  if (p == 0) {
    throwNew(e, "java/lang/OutOfMemoryError", 0);
  }
  return p;
}

} // namespace

#endif//JNI_UTIL
