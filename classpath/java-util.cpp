/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "time.h"
#include "jni.h"
#include "jni-util.h"

namespace {

void
removeNewline(char* s)
{
  for (; s; ++s) {
    if (*s == '\n') {
      *s = 0;
      break;
    }
  }
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_java_util_Date_toString(JNIEnv* e, jclass c UNUSED, jlong when)
{
  time_t time = when / 1000;

#ifdef WIN32
  e->MonitorEnter(c);
  char* s = ctime(&time);
  removeNewline(s);
  jstring r = e->NewStringUTF(s);
  e->MonitorExit(c);
  return r;
#else
  char buffer[27];
  ctime_r(&time, buffer);
  removeNewline(buffer);
  return e->NewStringUTF(buffer);
#endif
}
