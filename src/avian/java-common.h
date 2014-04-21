/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef JAVA_COMMON_H
#define JAVA_COMMON_H

namespace vm {

class Machine;
class Thread;

struct Object { };

typedef Object* object;

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;

typedef jint jsize;

typedef object* jobject;

typedef jobject jclass;
typedef jobject jthrowable;
typedef jobject jstring;
typedef jobject jweak;

typedef jobject jarray;
typedef jarray jbooleanArray;
typedef jarray jbyteArray;
typedef jarray jcharArray;
typedef jarray jshortArray;
typedef jarray jintArray;
typedef jarray jlongArray;
typedef jarray jfloatArray;
typedef jarray jdoubleArray;
typedef jarray jobjectArray;

typedef uintptr_t jfieldID;
typedef uintptr_t jmethodID;

union jvalue {
  jboolean z;
  jbyte    b;
  jchar    c;
  jshort   s;
  jint     i;
  jlong    j;
  jfloat   f;
  jdouble  d;
  jobject  l;
};

} // namespace vm

#endif // JAVA_COMMON_H
