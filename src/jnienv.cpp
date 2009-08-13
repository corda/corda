/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "jnienv.h"
#include "machine.h"
#include "util.h"
#include "processor.h"
#include "constants.h"
#include "processor.h"

using namespace vm;

namespace {

const uintptr_t InterfaceMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 1));

const uintptr_t NonVirtualMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 2));

jint JNICALL
DestroyJavaVM(Machine* m)
{
  System* s = m->system;
  Heap* h = m->heap;
  Processor* p = m->processor;
  Finder* f = m->finder;
  Thread* t = m->rootThread;

  // wait for other threads to exit
  { ACQUIRE(t, m->stateLock);

    while (m->liveCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  }

  int exitCode = (t->exception ? -1 : 0);
  enter(t, Thread::ActiveState);
  t->exit();

  m->dispose();
  h->disposeFixies();
  p->dispose();
  h->dispose();
  f->dispose();
  s->dispose();

  return exitCode;
}

jint JNICALL
AttachCurrentThread(Machine* m, Thread** t, void*)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t == 0) {
    *t = m->processor->makeThread(m, 0, m->rootThread);
    m->system->attach(&((*t)->runnable));

    enter(*t, Thread::ActiveState);
    enter(*t, Thread::IdleState);

    m->localThread->set(*t);
  }
  return 0;
}

jint JNICALL
DetachCurrentThread(Machine* m)
{
  Thread* t = static_cast<Thread*>(m->localThread->get());
  if (t) {
    t->exit();
    return 0;
  } else {
    return -1;
  }
}

jint JNICALL
GetEnv(Machine* m, Thread** t, jint version)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t) {
    if (version <= JNI_VERSION_1_4) {
      return JNI_OK;
    } else {
      return JNI_EVERSION;
    }
  } else {
    return JNI_EDETACHED;
  }
}

jsize JNICALL
GetVersion(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return JNI_VERSION_1_6;
}

jsize JNICALL
GetStringLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringLength(t, *s);
}

const jchar* JNICALL
GetStringChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  jchar* chars = static_cast<jchar*>
    (t->m->heap->allocate((stringLength(t, *s) + 1) * sizeof(jchar)));
  stringChars(t, *s, chars);

  if (isCopy) *isCopy = true;
  return chars;
}

void JNICALL
ReleaseStringChars(Thread* t, jstring s, const jchar* chars)
{
  ENTER(t, Thread::ActiveState);

  t->m->heap->free(chars, (stringLength(t, *s) + 1) * sizeof(jchar));
}

jsize JNICALL
GetStringUTFLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringLength(t, *s);
}

const char* JNICALL
GetStringUTFChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  char* chars = static_cast<char*>
    (t->m->heap->allocate(stringLength(t, *s) + 1));
  stringChars(t, *s, chars);

  if (isCopy) *isCopy = true;
  return chars;
}

void JNICALL
ReleaseStringUTFChars(Thread* t, jstring s, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  t->m->heap->free(chars, stringLength(t, *s) + 1);
}

jsize JNICALL
GetArrayLength(Thread* t, jarray array)
{
  ENTER(t, Thread::ActiveState);

  return cast<uintptr_t>(*array, BytesPerWord);
}

jstring JNICALL
NewString(Thread* t, const jchar* chars, jsize size)
{
  ENTER(t, Thread::ActiveState);

  object a = 0;
  if (size) {
    a = makeCharArray(t, size);
    memcpy(&charArrayBody(t, a, 0), chars, size * sizeof(jchar));
  }
  object s = makeString(t, a, 0, size, 0);

  return makeLocalReference(t, s);
}

jstring JNICALL
NewStringUTF(Thread* t, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  object a = 0;
  unsigned size = strlen(chars);
  if (size) {
    a = makeByteArray(t, size);
    memcpy(&byteArrayBody(t, a, 0), chars, size);
  }
  object s = makeString(t, a, 0, size, 0);

  return makeLocalReference(t, s);
}

void
replace(int a, int b, const char* in, int8_t* out)
{
  while (*in) {
    *out = (*in == a ? b : *in);
    ++ in;
    ++ out;
  }
  *out = 0;
}

jclass JNICALL
FindClass(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  object n = makeByteArray(t, strlen(name) + 1);
  replace('.', '/', name, &byteArrayBody(t, n, 0));

  return makeLocalReference(t, resolveClass(t, t->m->loader, n));
}

jint JNICALL
ThrowNew(Thread* t, jclass c, const char* message)
{
  if (t->exception) {
    return -1;
  }

  ENTER(t, Thread::ActiveState);
  
  object m = 0;
  PROTECT(t, m);

  if (message) {
    m = makeString(t, "%s", message);
  }

  object trace = makeTrace(t);
  PROTECT(t, trace);

  t->exception = make(t, *c);
  set(t, t->exception, ThrowableMessage, m);
  set(t, t->exception, ThrowableTrace, trace);

  return 0;
}

void JNICALL
DeleteLocalRef(Thread* t, jobject r)
{
  ENTER(t, Thread::ActiveState);

  disposeLocalReference(t, r);
}

jboolean JNICALL
ExceptionCheck(Thread* t)
{
  return t->exception != 0;
}

#ifndef AVIAN_GNU
jobject JNICALL
NewDirectByteBuffer(Thread*, void*, jlong)
{
  return 0;
}

void* JNICALL
GetDirectBufferAddress(Thread*, jobject)
{
  return 0;
}

jlong JNICALL
GetDirectBufferCapacity(Thread*, jobject)
{
  return -1;
}
#endif// not AVIAN_GNU

jclass JNICALL
GetObjectClass(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, objectClass(t, *o));
}

jboolean JNICALL
IsInstanceOf(Thread* t, jobject o, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return instanceOf(t, *c, *o);
}

object
findMethod(Thread* t, jclass c, const char* name, const char* spec)
{
  object n = makeByteArray(t, "%s", name);
  PROTECT(t, n);

  object s = makeByteArray(t, "%s", spec);
  return vm::findMethod(t, *c, n, s);
}

jint
methodID(Thread* t, object method)
{
  if (methodNativeID(t, method) == 0) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->referenceLock);
    
    if (methodNativeID(t, method) == 0) {
      t->m->jniMethodTable = vectorAppend(t, t->m->jniMethodTable, method);
      methodNativeID(t, method) = vectorSize(t, t->m->jniMethodTable);
    }
  }

  return methodNativeID(t, method);
}

jmethodID JNICALL
GetMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  assert(t, (methodFlags(t, method) & ACC_STATIC) == 0);

  return methodID(t, method);
}

jmethodID JNICALL
GetStaticMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  assert(t, methodFlags(t, method) & ACC_STATIC);

  return methodID(t, method);
}

inline object
getMethod(Thread* t, jmethodID m)
{
  object method = vectorBody(t, t->m->jniMethodTable, m - 1);

  assert(t, (methodFlags(t, method) & ACC_STATIC) == 0);

  return method;
}

jobject JNICALL
NewObjectV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object o = make(t, *c);
  PROTECT(t, o);

  t->m->processor->invokeList(t, getMethod(t, m), o, true, a);

  return makeLocalReference(t, o);
}

jobject JNICALL
NewObject(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = NewObjectV(t, c, m, a);

  va_end(a);

  return r;
}

jobject JNICALL
CallObjectMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  return makeLocalReference
    (t, t->m->processor->invokeList(t, method, *o, true, a));
}

jobject JNICALL
CallObjectMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallObjectMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jboolean JNICALL
CallBooleanMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? false : (intValue(t, r) != 0));
}

jboolean JNICALL
CallBooleanMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallBooleanMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
CallByteMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jbyte JNICALL
CallByteMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallByteMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
CallCharMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jchar JNICALL
CallCharMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallCharMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
CallShortMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jshort JNICALL
CallShortMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallShortMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jint JNICALL
CallIntMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jint JNICALL
CallIntMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallIntMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jlong JNICALL
CallLongMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : longValue(t, r));
}

jlong JNICALL
CallLongMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallLongMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
CallFloatMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : bitsToFloat(intValue(t, r)));
}

jfloat JNICALL
CallFloatMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallFloatMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
CallDoubleMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  object r = t->m->processor->invokeList(t, method, *o, true, a);
  return (t->exception ? 0 : bitsToDouble(longValue(t, r)));
}

jdouble JNICALL
CallDoubleMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallDoubleMethodV(t, o, m, a);

  va_end(a);

  return r;
}

void JNICALL
CallVoidMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object method = getMethod(t, m);
  t->m->processor->invokeList(t, method, *o, true, a);
}

void JNICALL
CallVoidMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallVoidMethodV(t, o, m, a);

  va_end(a);
}

inline object
getStaticMethod(Thread* t, jmethodID m)
{
  object method = vectorBody(t, t->m->jniMethodTable, m - 1);

  assert(t, methodFlags(t, method) & ACC_STATIC);

  return method;
}

jobject JNICALL
CallStaticObjectMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->m->processor->invokeList
                            (t, getStaticMethod(t, m), 0, true, a));
}

jobject JNICALL
CallStaticObjectMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallStaticObjectMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jboolean JNICALL
CallStaticBooleanMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : (intValue(t, r) != 0));
}

jboolean JNICALL
CallStaticBooleanMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallStaticBooleanMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
CallStaticByteMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jbyte JNICALL
CallStaticByteMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallStaticByteMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
CallStaticCharMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jchar JNICALL
CallStaticCharMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallStaticCharMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
CallStaticShortMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jshort JNICALL
CallStaticShortMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallStaticShortMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jint JNICALL
CallStaticIntMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : intValue(t, r));
}

jint JNICALL
CallStaticIntMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallStaticIntMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jlong JNICALL
CallStaticLongMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : longValue(t, r));
}

jlong JNICALL
CallStaticLongMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallStaticLongMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
CallStaticFloatMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : bitsToFloat(intValue(t, r)));
}

jfloat JNICALL
CallStaticFloatMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallStaticFloatMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
CallStaticDoubleMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object r = t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
  return (t->exception ? 0 : bitsToDouble(longValue(t, r)));
}

jdouble JNICALL
CallStaticDoubleMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallStaticDoubleMethodV(t, c, m, a);

  va_end(a);

  return r;
}

void JNICALL
CallStaticVoidMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, a);
}

void JNICALL
CallStaticVoidMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallStaticVoidMethodV(t, c, m, a);

  va_end(a);
}

jfieldID JNICALL
GetFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object field = resolveField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jfieldID JNICALL
GetStaticFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object field = resolveField(t, *c, name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jobject JNICALL
GetObjectField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, cast<object>(*o, field));
}

jboolean JNICALL
GetBooleanField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jboolean>(*o, field);
}

jbyte JNICALL
GetByteField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jbyte>(*o, field);
}

jchar JNICALL
GetCharField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jchar>(*o, field);
}

jshort JNICALL
GetShortField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jshort>(*o, field);
}

jint JNICALL
GetIntField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jint>(*o, field);
}

jlong JNICALL
GetLongField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jlong>(*o, field);
}

jfloat JNICALL
GetFloatField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jfloat>(*o, field);
}

jdouble JNICALL
GetDoubleField(Thread* t, jobject o, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<jdouble>(*o, field);
}

void JNICALL
SetObjectField(Thread* t, jobject o, jfieldID field, jobject v)
{
  ENTER(t, Thread::ActiveState);

  set(t, *o, field, (v ? *v : 0));
}

void JNICALL
SetBooleanField(Thread* t, jobject o, jfieldID field, jboolean v)
{
  ENTER(t, Thread::ActiveState);

  cast<jboolean>(*o, field) = v;
}

void JNICALL
SetByteField(Thread* t, jobject o, jfieldID field, jbyte v)
{
  ENTER(t, Thread::ActiveState);

  cast<jbyte>(*o, field) = v;
}

void JNICALL
SetCharField(Thread* t, jobject o, jfieldID field, jchar v)
{
  ENTER(t, Thread::ActiveState);

  cast<jchar>(*o, field) = v;
}

void JNICALL
SetShortField(Thread* t, jobject o, jfieldID field, jshort v)
{
  ENTER(t, Thread::ActiveState);

  cast<jshort>(*o, field) = v;
}

void JNICALL
SetIntField(Thread* t, jobject o, jfieldID field, jint v)
{
  ENTER(t, Thread::ActiveState);

  cast<jint>(*o, field) = v;
}

void JNICALL
SetLongField(Thread* t, jobject o, jfieldID field, jlong v)
{
  ENTER(t, Thread::ActiveState);

  cast<jlong>(*o, field) = v;
}

void JNICALL
SetFloatField(Thread* t, jobject o, jfieldID field, jfloat v)
{
  ENTER(t, Thread::ActiveState);

  cast<jfloat>(*o, field) = v;
}

void JNICALL
SetDoubleField(Thread* t, jobject o, jfieldID field, jdouble v)
{
  ENTER(t, Thread::ActiveState);

  cast<jdouble>(*o, field) = v;
}

jobject JNICALL
GetStaticObjectField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, cast<object>(classStaticTable(t, *c), field));
}

jboolean JNICALL
GetStaticBooleanField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int8_t>(classStaticTable(t, *c), field);
}

jbyte JNICALL
GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int8_t>(classStaticTable(t, *c), field);
}

jchar JNICALL
GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<uint16_t>(classStaticTable(t, *c), field);
}

jshort JNICALL
GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int16_t>(classStaticTable(t, *c), field);
}

jint JNICALL
GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int32_t>(classStaticTable(t, *c), field);
}

jlong JNICALL
GetStaticLongField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int64_t>(classStaticTable(t, *c), field);
}

jfloat JNICALL
GetStaticFloatField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<float>(classStaticTable(t, *c), field);
}

jdouble JNICALL
GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<double>(classStaticTable(t, *c), field);
}

void JNICALL
SetStaticObjectField(Thread* t, jclass c, jfieldID field, jobject v)
{
  ENTER(t, Thread::ActiveState);

  set(t, classStaticTable(t, *c), field, (v ? *v : 0));
}

void JNICALL
SetStaticBooleanField(Thread* t, jclass c, jfieldID field, jboolean v)
{
  ENTER(t, Thread::ActiveState);
  
  cast<int8_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticByteField(Thread* t, jclass c, jfieldID field, jbyte v)
{
  ENTER(t, Thread::ActiveState);

  cast<int8_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticCharField(Thread* t, jclass c, jfieldID field, jchar v)
{
  ENTER(t, Thread::ActiveState);

  cast<uint16_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticShortField(Thread* t, jclass c, jfieldID field, jshort v)
{
  ENTER(t, Thread::ActiveState);

  cast<int16_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticIntField(Thread* t, jclass c, jfieldID field, jint v)
{
  ENTER(t, Thread::ActiveState);

  cast<int32_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticLongField(Thread* t, jclass c, jfieldID field, jlong v)
{
  ENTER(t, Thread::ActiveState);

  cast<int64_t>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticFloatField(Thread* t, jclass c, jfieldID field, jfloat v)
{
  ENTER(t, Thread::ActiveState);

  cast<float>(classStaticTable(t, *c), field) = v;
}

void JNICALL
SetStaticDoubleField(Thread* t, jclass c, jfieldID field, jdouble v)
{
  ENTER(t, Thread::ActiveState);

  cast<double>(classStaticTable(t, *c), field) = v;
}

jobject JNICALL
NewGlobalRef(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->referenceLock);
  
  if (o) {
    Reference* r = new (t->m->heap->allocate(sizeof(Reference)))
      Reference(*o, &(t->m->jniReferences));

    return &(r->target);
  } else {
    return 0;
  }
}

void JNICALL
DeleteGlobalRef(Thread* t, jobject r)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->referenceLock);
  
  if (r) {
    dispose(t, reinterpret_cast<Reference*>(r));
  }
}

jthrowable JNICALL
ExceptionOccurred(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->exception);
}

void JNICALL
ExceptionDescribe(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return printTrace(t, t->exception);
}

void JNICALL
ExceptionClear(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  t->exception = 0;
}

jobjectArray JNICALL
NewObjectArray(Thread* t, jsize length, jclass class_, jobject init)
{
  ENTER(t, Thread::ActiveState);

  object a = makeObjectArray(t, classLoader(t, *class_), *class_, length);
  object value = (init ? *init : 0);
  for (jsize i = 0; i < length; ++i) {
    set(t, a, ArrayBody + (i * BytesPerWord), value);
  }
  return makeLocalReference(t, a);
}

jobject JNICALL
GetObjectArrayElement(Thread* t, jobjectArray array, jsize index)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, objectArrayBody(t, *array, index));
}

void JNICALL
SetObjectArrayElement(Thread* t, jobjectArray array, jsize index,
                      jobject value)
{
  ENTER(t, Thread::ActiveState);

  set(t, *array, ArrayBody + (index * BytesPerWord), *value);
}

jbooleanArray JNICALL
NewBooleanArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeBooleanArray(t, length));
}

jbyteArray JNICALL
NewByteArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeByteArray(t, length));
}

jcharArray JNICALL
NewCharArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeCharArray(t, length));
}

jshortArray JNICALL
NewShortArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeShortArray(t, length));
}

jintArray JNICALL
NewIntArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeIntArray(t, length));
}

jlongArray JNICALL
NewLongArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeLongArray(t, length));
}

jfloatArray JNICALL
NewFloatArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeFloatArray(t, length));
}

jdoubleArray JNICALL
NewDoubleArray(Thread* t, jsize length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeDoubleArray(t, length));
}

jboolean* JNICALL
GetBooleanArrayElements(Thread* t, jbooleanArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = booleanArrayLength(t, *array) * sizeof(jboolean);
  jboolean* p = static_cast<jboolean*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &booleanArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jbyte* JNICALL
GetByteArrayElements(Thread* t, jbyteArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = byteArrayLength(t, *array) * sizeof(jbyte);
  jbyte* p = static_cast<jbyte*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &byteArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jchar* JNICALL
GetCharArrayElements(Thread* t, jcharArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = charArrayLength(t, *array) * sizeof(jchar);
  jchar* p = static_cast<jchar*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &charArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jshort* JNICALL
GetShortArrayElements(Thread* t, jshortArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = shortArrayLength(t, *array) * sizeof(jshort);
  jshort* p = static_cast<jshort*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &shortArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jint* JNICALL
GetIntArrayElements(Thread* t, jintArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = intArrayLength(t, *array) * sizeof(jint);
  jint* p = static_cast<jint*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &intArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jlong* JNICALL
GetLongArrayElements(Thread* t, jlongArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = longArrayLength(t, *array) * sizeof(jlong);
  jlong* p = static_cast<jlong*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &longArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jfloat* JNICALL
GetFloatArrayElements(Thread* t, jfloatArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = floatArrayLength(t, *array) * sizeof(jfloat);
  jfloat* p = static_cast<jfloat*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &floatArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jdouble* JNICALL
GetDoubleArrayElements(Thread* t, jdoubleArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = doubleArrayLength(t, *array) * sizeof(jdouble);
  jdouble* p = static_cast<jdouble*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, &doubleArrayBody(t, *array, 0), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

void JNICALL
ReleaseBooleanArrayElements(Thread* t, jbooleanArray array, jboolean* p,
                            jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = booleanArrayLength(t, *array) * sizeof(jboolean);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&booleanArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseByteArrayElements(Thread* t, jbyteArray array, jbyte* p, jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = byteArrayLength(t, *array) * sizeof(jbyte);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&byteArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseCharArrayElements(Thread* t, jcharArray array, jchar* p, jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = charArrayLength(t, *array) * sizeof(jchar);

  if (mode == 0 or mode == JNI_COMMIT) {    
    if (size) {
      memcpy(&charArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseShortArrayElements(Thread* t, jshortArray array, jshort* p, jint mode)
{
  ENTER(t, Thread::ActiveState);  

  unsigned size = shortArrayLength(t, *array) * sizeof(jshort);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&shortArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseIntArrayElements(Thread* t, jintArray array, jint* p, jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = intArrayLength(t, *array) * sizeof(jint);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&intArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseLongArrayElements(Thread* t, jlongArray array, jlong* p, jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = longArrayLength(t, *array) * sizeof(jlong);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&longArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseFloatArrayElements(Thread* t, jfloatArray array, jfloat* p, jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = floatArrayLength(t, *array) * sizeof(jfloat);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&floatArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
ReleaseDoubleArrayElements(Thread* t, jdoubleArray array, jdouble* p,
                           jint mode)
{
  ENTER(t, Thread::ActiveState);
    
  unsigned size = doubleArrayLength(t, *array) * sizeof(jdouble);

  if (mode == 0 or mode == JNI_COMMIT) {
    if (size) {
      memcpy(&doubleArrayBody(t, *array, 0), p, size);
    }
  }

  if (mode == 0 or mode == JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
GetBooleanArrayRegion(Thread* t, jbooleanArray array, jint offset, jint length,
                      jboolean* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &booleanArrayBody(t, *array, offset),
           length * sizeof(jboolean));
  }
}

void JNICALL
GetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   jbyte* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &byteArrayBody(t, *array, offset), length * sizeof(jbyte));
  }
}

void JNICALL
GetCharArrayRegion(Thread* t, jcharArray array, jint offset, jint length,
                   jchar* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &charArrayBody(t, *array, offset), length * sizeof(jchar));
  }
}

void JNICALL
GetShortArrayRegion(Thread* t, jshortArray array, jint offset, jint length,
                    jshort* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &shortArrayBody(t, *array, offset), length * sizeof(jshort));
  }
}

void JNICALL
GetIntArrayRegion(Thread* t, jintArray array, jint offset, jint length,
                  jint* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &intArrayBody(t, *array, offset), length * sizeof(jint));
  }
}

void JNICALL
GetLongArrayRegion(Thread* t, jlongArray array, jint offset, jint length,
                   jlong* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &longArrayBody(t, *array, offset), length * sizeof(jlong));
  }
}

void JNICALL
GetFloatArrayRegion(Thread* t, jfloatArray array, jint offset, jint length,
                    jfloat* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &floatArrayBody(t, *array, offset), length * sizeof(jfloat));
  }
}

void JNICALL
GetDoubleArrayRegion(Thread* t, jdoubleArray array, jint offset, jint length,
                     jdouble* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &doubleArrayBody(t, *array, offset), length * sizeof(jdouble));
  }
}

void JNICALL
SetBooleanArrayRegion(Thread* t, jbooleanArray array, jint offset, jint length,
                      const jboolean* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&booleanArrayBody(t, *array, offset), src,
           length * sizeof(jboolean));
  }
}

void JNICALL
SetByteArrayRegion(Thread* t, jbyteArray array, jint offset, jint length,
                   const jbyte* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&byteArrayBody(t, *array, offset), src, length * sizeof(jbyte));
  }
}

void JNICALL
SetCharArrayRegion(Thread* t, jcharArray array, jint offset, jint length,
                   const jchar* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&charArrayBody(t, *array, offset), src, length * sizeof(jchar));
  }
}

void JNICALL
SetShortArrayRegion(Thread* t, jshortArray array, jint offset, jint length,
                    const jshort* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&shortArrayBody(t, *array, offset), src, length * sizeof(jshort));
  }
}

void JNICALL
SetIntArrayRegion(Thread* t, jintArray array, jint offset, jint length,
                  const jint* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&intArrayBody(t, *array, offset), src, length * sizeof(jint));
  }
}

void JNICALL
SetLongArrayRegion(Thread* t, jlongArray array, jint offset, jint length,
                   const jlong* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&longArrayBody(t, *array, offset), src, length * sizeof(jlong));
  }
}

void JNICALL
SetFloatArrayRegion(Thread* t, jfloatArray array, jint offset, jint length,
                    const jfloat* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&floatArrayBody(t, *array, offset), src, length * sizeof(jfloat));
  }
}

void JNICALL
SetDoubleArrayRegion(Thread* t, jdoubleArray array, jint offset, jint length,
                     const jdouble* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&doubleArrayBody(t, *array, offset), src, length * sizeof(jdouble));
  }
}

void* JNICALL
GetPrimitiveArrayCritical(Thread* t, jarray array, jboolean* isCopy)
{
  if ((t->criticalLevel ++) == 0) {
    enter(t, Thread::ActiveState);
  }
  
  if (isCopy) {
    *isCopy = true;
  }

  return reinterpret_cast<uintptr_t*>(*array) + 2;
}

void JNICALL
ReleasePrimitiveArrayCritical(Thread* t, jarray, void*, jint)
{
  if ((-- t->criticalLevel) == 0) {
    enter(t, Thread::IdleState);
  }
}

jint JNICALL
MonitorEnter(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  acquire(t, *o);

  return 0;
}

jint JNICALL
MonitorExit(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  release(t, *o);

  return 0;
}

jint JNICALL
GetJavaVM(Thread* t, Machine** m)
{
  *m = t->m;
  return 0;
}

jboolean JNICALL
IsSameObject(Thread* t, jobject a, jobject b)
{
  if (a and b) {
    ENTER(t, Thread::ActiveState);

    return *a == *b;
  } else {
    return a == b;
  }
}

struct JavaVMOption {
  char* optionString;
  void* extraInfo;
};

struct JavaVMInitArgs {
  jint version;

  jint nOptions;
  JavaVMOption* options;
  jboolean ignoreUnrecognized;
};

int
parseSize(const char* s)
{
  unsigned length = strlen(s);
  char buffer[length + 1];
  if (length == 0) {
    return 0;
  } else if (s[length - 1] == 'k') {
    memcpy(buffer, s, length - 1);
    buffer[length] = 0;
    return atoi(buffer) * 1024;
  } else if (s[length - 1] == 'm') {
    memcpy(buffer, s, length - 1);
    buffer[length] = 0;
    return atoi(buffer) * 1024 * 1024;
  } else {
    return atoi(s);
  }
}

void
append(char** p, const char* value, unsigned length, char tail)
{
  if (length) {
    memcpy(*p, value, length);
    *p += length;
    *((*p)++) = tail;
  }
}

} // namespace

namespace vm {

#ifdef AVIAN_GNU
jobject JNICALL
NewDirectByteBuffer(Thread*, void*, jlong);

void* JNICALL
GetDirectBufferAddress(Thread*, jobject);

jlong JNICALL
GetDirectBufferCapacity(Thread*, jobject);
#endif//AVIAN_GNU

void
populateJNITables(JavaVMVTable* vmTable, JNIEnvVTable* envTable)
{
  memset(vmTable, 0, sizeof(JavaVMVTable));

  vmTable->DestroyJavaVM = DestroyJavaVM;
  vmTable->AttachCurrentThread = AttachCurrentThread;
  vmTable->DetachCurrentThread = DetachCurrentThread;
  vmTable->GetEnv = GetEnv;

  memset(envTable, 0, sizeof(JNIEnvVTable));

  envTable->GetVersion = ::GetVersion;
  envTable->GetStringLength = ::GetStringLength;
  envTable->GetStringChars = ::GetStringChars;
  envTable->ReleaseStringChars = ::ReleaseStringChars;
  envTable->GetStringUTFLength = ::GetStringUTFLength;
  envTable->GetStringUTFChars = ::GetStringUTFChars;
  envTable->ReleaseStringUTFChars = ::ReleaseStringUTFChars;
  envTable->GetArrayLength = ::GetArrayLength;
  envTable->NewString = ::NewString;
  envTable->NewStringUTF = ::NewStringUTF;
  envTable->FindClass = ::FindClass;
  envTable->ThrowNew = ::ThrowNew;
  envTable->ExceptionCheck = ::ExceptionCheck;
  envTable->NewDirectByteBuffer = ::NewDirectByteBuffer;
  envTable->GetDirectBufferAddress = ::GetDirectBufferAddress;
  envTable->GetDirectBufferCapacity = ::GetDirectBufferCapacity;
  envTable->DeleteLocalRef = ::DeleteLocalRef;
  envTable->GetObjectClass = ::GetObjectClass;
  envTable->IsInstanceOf = ::IsInstanceOf;
  envTable->GetFieldID = ::GetFieldID;
  envTable->GetMethodID = ::GetMethodID;
  envTable->GetStaticMethodID = ::GetStaticMethodID;
  envTable->NewObject = ::NewObject;
  envTable->NewObjectV = ::NewObjectV;
  envTable->CallObjectMethodV = ::CallObjectMethodV;
  envTable->CallObjectMethod = ::CallObjectMethod;
  envTable->CallBooleanMethodV = ::CallBooleanMethodV;
  envTable->CallBooleanMethod = ::CallBooleanMethod;
  envTable->CallByteMethodV = ::CallByteMethodV;
  envTable->CallByteMethod = ::CallByteMethod;
  envTable->CallCharMethodV = ::CallCharMethodV;
  envTable->CallCharMethod = ::CallCharMethod;
  envTable->CallShortMethodV = ::CallShortMethodV;
  envTable->CallShortMethod = ::CallShortMethod;
  envTable->CallIntMethodV = ::CallIntMethodV;
  envTable->CallIntMethod = ::CallIntMethod;
  envTable->CallLongMethodV = ::CallLongMethodV;
  envTable->CallLongMethod = ::CallLongMethod;
  envTable->CallFloatMethodV = ::CallFloatMethodV;
  envTable->CallFloatMethod = ::CallFloatMethod;
  envTable->CallDoubleMethodV = ::CallDoubleMethodV;
  envTable->CallDoubleMethod = ::CallDoubleMethod;
  envTable->CallVoidMethodV = ::CallVoidMethodV;
  envTable->CallVoidMethod = ::CallVoidMethod;
  envTable->CallStaticObjectMethodV = ::CallStaticObjectMethodV;
  envTable->CallStaticObjectMethod = ::CallStaticObjectMethod;
  envTable->CallStaticBooleanMethodV = ::CallStaticBooleanMethodV;
  envTable->CallStaticBooleanMethod = ::CallStaticBooleanMethod;
  envTable->CallStaticByteMethodV = ::CallStaticByteMethodV;
  envTable->CallStaticByteMethod = ::CallStaticByteMethod;
  envTable->CallStaticCharMethodV = ::CallStaticCharMethodV;
  envTable->CallStaticCharMethod = ::CallStaticCharMethod;
  envTable->CallStaticShortMethodV = ::CallStaticShortMethodV;
  envTable->CallStaticShortMethod = ::CallStaticShortMethod;
  envTable->CallStaticIntMethodV = ::CallStaticIntMethodV;
  envTable->CallStaticIntMethod = ::CallStaticIntMethod;
  envTable->CallStaticLongMethodV = ::CallStaticLongMethodV;
  envTable->CallStaticLongMethod = ::CallStaticLongMethod;
  envTable->CallStaticFloatMethodV = ::CallStaticFloatMethodV;
  envTable->CallStaticFloatMethod = ::CallStaticFloatMethod;
  envTable->CallStaticDoubleMethodV = ::CallStaticDoubleMethodV;
  envTable->CallStaticDoubleMethod = ::CallStaticDoubleMethod;
  envTable->CallStaticVoidMethodV = ::CallStaticVoidMethodV;
  envTable->CallStaticVoidMethod = ::CallStaticVoidMethod;
  envTable->GetStaticFieldID = ::GetStaticFieldID;
  envTable->GetObjectField = ::GetObjectField;
  envTable->GetBooleanField = ::GetBooleanField;
  envTable->GetByteField = ::GetByteField;
  envTable->GetCharField = ::GetCharField;
  envTable->GetShortField = ::GetShortField;
  envTable->GetIntField = ::GetIntField;
  envTable->GetLongField = ::GetLongField;
  envTable->GetFloatField = ::GetFloatField;
  envTable->GetDoubleField = ::GetDoubleField;
  envTable->SetObjectField = ::SetObjectField;
  envTable->SetBooleanField = ::SetBooleanField;
  envTable->SetByteField = ::SetByteField;
  envTable->SetCharField = ::SetCharField;
  envTable->SetShortField = ::SetShortField;
  envTable->SetIntField = ::SetIntField;
  envTable->SetLongField = ::SetLongField;
  envTable->SetFloatField = ::SetFloatField;
  envTable->SetDoubleField = ::SetDoubleField;
  envTable->GetStaticObjectField = ::GetStaticObjectField;
  envTable->GetStaticBooleanField = ::GetStaticBooleanField;
  envTable->GetStaticByteField = ::GetStaticByteField;
  envTable->GetStaticCharField = ::GetStaticCharField;
  envTable->GetStaticShortField = ::GetStaticShortField;
  envTable->GetStaticIntField = ::GetStaticIntField;
  envTable->GetStaticLongField = ::GetStaticLongField;
  envTable->GetStaticFloatField = ::GetStaticFloatField;
  envTable->GetStaticDoubleField = ::GetStaticDoubleField;
  envTable->SetStaticObjectField = ::SetStaticObjectField;
  envTable->SetStaticBooleanField = ::SetStaticBooleanField;
  envTable->SetStaticByteField = ::SetStaticByteField;
  envTable->SetStaticCharField = ::SetStaticCharField;
  envTable->SetStaticShortField = ::SetStaticShortField;
  envTable->SetStaticIntField = ::SetStaticIntField;
  envTable->SetStaticLongField = ::SetStaticLongField;
  envTable->SetStaticFloatField = ::SetStaticFloatField;
  envTable->SetStaticDoubleField = ::SetStaticDoubleField;
  envTable->NewGlobalRef = ::NewGlobalRef;
  envTable->NewWeakGlobalRef = ::NewGlobalRef;
  envTable->DeleteGlobalRef = ::DeleteGlobalRef;
  envTable->ExceptionOccurred = ::ExceptionOccurred;
  envTable->ExceptionDescribe = ::ExceptionDescribe;
  envTable->ExceptionClear = ::ExceptionClear;
  envTable->NewObjectArray = ::NewObjectArray;
  envTable->GetObjectArrayElement = ::GetObjectArrayElement;
  envTable->SetObjectArrayElement = ::SetObjectArrayElement;
  envTable->NewBooleanArray = ::NewBooleanArray;
  envTable->NewByteArray = ::NewByteArray;
  envTable->NewCharArray = ::NewCharArray;
  envTable->NewShortArray = ::NewShortArray;
  envTable->NewIntArray = ::NewIntArray;
  envTable->NewLongArray = ::NewLongArray;
  envTable->NewFloatArray = ::NewFloatArray;
  envTable->NewDoubleArray = ::NewDoubleArray;
  envTable->GetBooleanArrayElements = ::GetBooleanArrayElements;
  envTable->GetByteArrayElements = ::GetByteArrayElements;
  envTable->GetCharArrayElements = ::GetCharArrayElements;
  envTable->GetShortArrayElements = ::GetShortArrayElements;
  envTable->GetIntArrayElements = ::GetIntArrayElements;
  envTable->GetLongArrayElements = ::GetLongArrayElements;
  envTable->GetFloatArrayElements = ::GetFloatArrayElements;
  envTable->GetDoubleArrayElements = ::GetDoubleArrayElements;
  envTable->ReleaseBooleanArrayElements = ::ReleaseBooleanArrayElements;
  envTable->ReleaseByteArrayElements = ::ReleaseByteArrayElements;
  envTable->ReleaseCharArrayElements = ::ReleaseCharArrayElements;
  envTable->ReleaseShortArrayElements = ::ReleaseShortArrayElements;
  envTable->ReleaseIntArrayElements = ::ReleaseIntArrayElements;
  envTable->ReleaseLongArrayElements = ::ReleaseLongArrayElements;
  envTable->ReleaseFloatArrayElements = ::ReleaseFloatArrayElements;
  envTable->ReleaseDoubleArrayElements = ::ReleaseDoubleArrayElements;
  envTable->GetBooleanArrayRegion = ::GetBooleanArrayRegion;
  envTable->GetByteArrayRegion = ::GetByteArrayRegion;
  envTable->GetCharArrayRegion = ::GetCharArrayRegion;
  envTable->GetShortArrayRegion = ::GetShortArrayRegion;
  envTable->GetIntArrayRegion = ::GetIntArrayRegion;
  envTable->GetLongArrayRegion = ::GetLongArrayRegion;
  envTable->GetFloatArrayRegion = ::GetFloatArrayRegion;
  envTable->GetDoubleArrayRegion = ::GetDoubleArrayRegion;
  envTable->SetBooleanArrayRegion = ::SetBooleanArrayRegion;
  envTable->SetByteArrayRegion = ::SetByteArrayRegion;
  envTable->SetCharArrayRegion = ::SetCharArrayRegion;
  envTable->SetShortArrayRegion = ::SetShortArrayRegion;
  envTable->SetIntArrayRegion = ::SetIntArrayRegion;
  envTable->SetLongArrayRegion = ::SetLongArrayRegion;
  envTable->SetFloatArrayRegion = ::SetFloatArrayRegion;
  envTable->SetDoubleArrayRegion = ::SetDoubleArrayRegion;
  envTable->GetPrimitiveArrayCritical = ::GetPrimitiveArrayCritical;
  envTable->ReleasePrimitiveArrayCritical = ::ReleasePrimitiveArrayCritical;
  envTable->MonitorEnter = MonitorEnter;
  envTable->MonitorExit = MonitorExit;
  envTable->GetJavaVM = ::GetJavaVM;
  envTable->IsSameObject = ::IsSameObject;
}

} // namespace vm

#define BOOTSTRAP_PROPERTY "avian.bootstrap"
#define CRASHDIR_PROPERTY "avian.crash.dir"
#define CLASSPATH_PROPERTY "java.class.path"
#define BOOTCLASSPATH_PREPEND_OPTION "bootclasspath/p"
#define BOOTCLASSPATH_OPTION "bootclasspath"
#define BOOTCLASSPATH_APPEND_OPTION "bootclasspath/a"
#define BOOTCLASSPATH_APPEND_OPTION "bootclasspath/a"

extern "C" JNIEXPORT jint JNICALL
JNI_GetDefaultJavaVMInitArgs(void*)
{
  return 0;
}

extern "C" JNIEXPORT jint JNICALL
JNI_CreateJavaVM(Machine** m, Thread** t, void* args)
{
  JavaVMInitArgs* a = static_cast<JavaVMInitArgs*>(args);

  unsigned heapLimit = 0;
  const char* bootLibrary = 0;
  const char* classpath = 0;
  const char* bootClasspathPrepend = "";
  const char* bootClasspath = "";
  const char* bootClasspathAppend = "";
  const char* crashDumpDirectory = 0;

  unsigned propertyCount = 0;

  for (int i = 0; i < a->nOptions; ++i) {
    if (strncmp(a->options[i].optionString, "-X", 2) == 0) {
      const char* p = a->options[i].optionString + 2;
      if (strncmp(p, "mx", 2) == 0) {
        heapLimit = parseSize(p + 2);
      } else if (strncmp(p, BOOTCLASSPATH_PREPEND_OPTION ":",
                         sizeof(BOOTCLASSPATH_PREPEND_OPTION)) == 0)
      {
        bootClasspathPrepend = p + sizeof(BOOTCLASSPATH_PREPEND_OPTION);
      } else if (strncmp(p, BOOTCLASSPATH_OPTION ":",
                         sizeof(BOOTCLASSPATH_OPTION)) == 0)
      {
        bootClasspath = p + sizeof(BOOTCLASSPATH_OPTION);
      } else if (strncmp(p, BOOTCLASSPATH_APPEND_OPTION ":",
                         sizeof(BOOTCLASSPATH_APPEND_OPTION)) == 0)
      {
        bootClasspathAppend = p + sizeof(BOOTCLASSPATH_APPEND_OPTION);
      }
    } else if (strncmp(a->options[i].optionString, "-D", 2) == 0) {
      const char* p = a->options[i].optionString + 2;
      if (strncmp(p, BOOTSTRAP_PROPERTY "=",
                  sizeof(BOOTSTRAP_PROPERTY)) == 0)
      {
        bootLibrary = p + sizeof(BOOTSTRAP_PROPERTY);
      } else if (strncmp(p, CRASHDIR_PROPERTY "=",
                         sizeof(CRASHDIR_PROPERTY)) == 0)
      {
        crashDumpDirectory = p + sizeof(CRASHDIR_PROPERTY);
      } else if (strncmp(p, CLASSPATH_PROPERTY "=",
                         sizeof(CLASSPATH_PROPERTY)) == 0)
      {
        classpath = p + sizeof(CLASSPATH_PROPERTY);
      }

      ++ propertyCount;
    }
  }

  if (heapLimit == 0) heapLimit = 128 * 1024 * 1024;
  
  if (classpath == 0) classpath = ".";
  
  unsigned bcppl = strlen(bootClasspathPrepend);
  unsigned bcpl = strlen(bootClasspath);
  unsigned bcpal = strlen(bootClasspathAppend);
  unsigned cpl = strlen(classpath);

  unsigned classpathBufferSize = bcppl + bcpl + bcpal + cpl + 4;
  char classpathBuffer[classpathBufferSize];
  char* classpathPointer = classpathBuffer;

  append(&classpathPointer, bootClasspathPrepend, bcppl, PATH_SEPARATOR);
  append(&classpathPointer, bootClasspath, bcpl, PATH_SEPARATOR);
  append(&classpathPointer, bootClasspathAppend, bcpal, PATH_SEPARATOR);
  append(&classpathPointer, classpath, cpl, 0);

  System* s = makeSystem(crashDumpDirectory);
  Heap* h = makeHeap(s, heapLimit);
  Finder* f = makeFinder(s, classpathBuffer, bootLibrary);
  Processor* p = makeProcessor(s, h);

  const char** properties = static_cast<const char**>
    (h->allocate(sizeof(const char*) * propertyCount));
  const char** propertyPointer = properties;
  for (int i = 0; i < a->nOptions; ++i) {
    if (strncmp(a->options[i].optionString, "-D", 2) == 0) {
      *(propertyPointer++) = a->options[i].optionString + 2;
    }
  }

  *m = new (h->allocate(sizeof(Machine)))
    Machine(s, h, f, p, properties, propertyCount);

  *t = p->makeThread(*m, 0, 0);

  enter(*t, Thread::ActiveState);
  enter(*t, Thread::IdleState);

  return 0;
}
