#include <jni.h>
#include "jni-util.h"

extern "C" JNIEXPORT jdouble JNICALL
Java_JNI_addDoubles
(JNIEnv*, jclass,
 jdouble a1, jdouble a2, jdouble a3, jdouble a4, jdouble a5, jdouble a6,
 jdouble a7, jdouble a8, jdouble a9, jdouble a10, jdouble a11, jdouble a12,
 jdouble a13, jdouble a14, jdouble a15, jdouble a16, jdouble a17, jdouble a18,
 jdouble a19, jdouble a20)
{
  return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13
    + a14 + a15 + a16 + a17 + a18 + a19 + a20;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_JNI_addFloats
(JNIEnv*, jclass,
 jfloat a1, jfloat a2, jfloat a3, jfloat a4, jfloat a5, jfloat a6,
 jfloat a7, jfloat a8, jfloat a9, jfloat a10, jfloat a11, jfloat a12,
 jfloat a13, jfloat a14, jfloat a15, jfloat a16, jfloat a17, jfloat a18,
 jfloat a19, jfloat a20)
{
  return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13
    + a14 + a15 + a16 + a17 + a18 + a19 + a20;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_JNI_addMix
(JNIEnv*, jclass,
 jfloat a1, jdouble a2, jfloat a3, jdouble a4, jfloat a5, jfloat a6,
 jfloat a7, jfloat a8, jfloat a9, jfloat a10, jfloat a11, jfloat a12,
 jfloat a13, jfloat a14, jfloat a15, jdouble a16, jfloat a17, jfloat a18,
 jfloat a19, jfloat a20)
{
  return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13
    + a14 + a15 + a16 + a17 + a18 + a19 + a20;
}

extern "C" JNIEXPORT jobject JNICALL
Java_Buffers_allocateNative(JNIEnv* e, jclass, jint capacity)
{
  void* p = allocate(e, capacity);
  if (p == 0) return 0;

  return e->NewDirectByteBuffer(p, capacity);
}

extern "C" JNIEXPORT void JNICALL
Java_Buffers_freeNative(JNIEnv* e, jclass, jobject b)
{
  free(e->GetDirectBufferAddress(b));
}
