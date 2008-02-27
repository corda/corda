/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef JNI_UTIL
#define JNI_UTIL

#undef JNIEXPORT
#ifdef __MINGW32__
#  define JNIEXPORT __declspec(dllexport)
#else
#  define JNIEXPORT __attribute__ ((visibility("default")))
#endif

namespace {

inline void
throwNew(JNIEnv* e, const char* class_, const char* message)
{
  jclass c = e->FindClass(class_);
  if (c) {
    e->ThrowNew(c, message);
    e->DeleteLocalRef(c);
  }
}

} // namespace

#endif//JNI_UTIL
