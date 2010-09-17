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

using namespace vm;

namespace {

namespace local {

const uintptr_t InterfaceMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 1));

const uintptr_t NonVirtualMethodID
= (static_cast<uintptr_t>(1) << (BitsPerWord - 2));

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
AttachCurrentThreadAsDaemon(Machine* m, Thread** t, void* parameters)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t == 0) {
    AttachCurrentThread(m, t, parameters);

    ENTER(*t, Thread::ActiveState);
    setDaemon(*t, (*t)->javaThread, true);
  }
  return 0;
}

jint JNICALL
DetachCurrentThread(Machine* m)
{
  Thread* t = static_cast<Thread*>(m->localThread->get());
  if (t) {
    expect(t, t != m->rootThread);

    m->localThread->set(0);

    ACQUIRE_RAW(t, t->m->stateLock);

    enter(t, Thread::ActiveState);

    threadPeer(t, t->javaThread) = 0;

    enter(t, Thread::ZombieState);

    t->state = Thread::JoinedState;

    return 0;
  } else {
    return -1;
  }
}

jint JNICALL
DestroyJavaVM(Machine* m)
{
  Thread* t; AttachCurrentThread(m, &t, 0);

  // wait for other non-daemon threads to exit
  { ACQUIRE(t, t->m->stateLock);
    while (t->m->liveCount - t->m->daemonCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  }

  { ENTER(t, Thread::ActiveState);

    shutDown(t);
  }

  int exitCode = (t->exception ? -1 : 0);

  t->exit();

  return exitCode;
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

jint JNICALL
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

void JNICALL
GetStringRegion(Thread* t, jstring s, jsize start, jsize length, jchar* dst)
{
  ENTER(t, Thread::ActiveState);

  stringChars(t, *s, start, length, dst);
}

const jchar* JNICALL
GetStringCritical(Thread* t, jstring s, jboolean* isCopy)
{
  if ((t->criticalLevel ++) == 0) {
    enter(t, Thread::ActiveState);
  }

  if (isCopy) {
    *isCopy = true;
  }
  
  object data = stringData(t, *s);
  if (objectClass(t, data) == type(t, Machine::ByteArrayType)) {
    return GetStringChars(t, s, isCopy);
  } else {
    return &charArrayBody(t, data, stringOffset(t, *s));
  }
}

void JNICALL
ReleaseStringCritical(Thread* t, jstring s, const jchar* chars)
{
  if (objectClass(t, stringData(t, *s)) == type(t, Machine::ByteArrayType)) {
    ReleaseStringChars(t, s, chars);
  }

  if ((-- t->criticalLevel) == 0) {
    enter(t, Thread::IdleState);
  }
}

jsize JNICALL
GetStringUTFLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringUTFLength(t, *s);
}

const char* JNICALL
GetStringUTFChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  int length = stringUTFLength(t, *s);
  char* chars = static_cast<char*>
    (t->m->heap->allocate(length + 1));
  stringUTFChars(t, *s, chars, length);

  if (isCopy) *isCopy = true;
  return chars;
}

void JNICALL
ReleaseStringUTFChars(Thread* t, jstring s, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  t->m->heap->free(chars, stringLength(t, *s) + 1);
}

void JNICALL
GetStringUTFRegion(Thread* t, jstring s, jsize start, jsize length, char* dst)
{
  ENTER(t, Thread::ActiveState);

  stringUTFChars
    (t, *s, start, length, dst, stringUTFLength(t, *s, start, length));
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
  if (chars == 0) return 0;

  ENTER(t, Thread::ActiveState);

  object a = 0;
  if (size) {
    a = makeCharArray(t, size);
    memcpy(&charArrayBody(t, a, 0), chars, size * sizeof(jchar));
  }

  return makeLocalReference(t, t->m->classpath->makeString(t, a, 0, size));
}

jstring JNICALL
NewStringUTF(Thread* t, const char* chars)
{
  if (chars == 0) return 0;

  ENTER(t, Thread::ActiveState);

  object array = parseUtf8(t, chars, strlen(chars));

  return makeLocalReference
    (t, t->m->classpath->makeString
     (t, array, 0, cast<uintptr_t>(array, BytesPerWord) - 1));
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
DefineClass(Thread* t, const char*, jobject loader, const jbyte* buffer,
            jsize length)
{
  ENTER(t, Thread::ActiveState);

  object c = defineClass
    (t, loader ? *loader : root(t, Machine::BootLoader),
     reinterpret_cast<const uint8_t*>(buffer), length);

  return makeLocalReference(t, c == 0 ? 0 : getJClass(t, c));
}

jclass JNICALL
FindClass(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  object n = makeByteArray(t, strlen(name) + 1);
  replace('.', '/', name, &byteArrayBody(t, n, 0));

  object c = resolveClass(t, root(t, Machine::AppLoader), n);

  return makeLocalReference(t, c == 0 ? 0 : getJClass(t, c));
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

  t->exception = make(t, jclassVmClass(t, *c));
  set(t, t->exception, ThrowableMessage, m);
  set(t, t->exception, ThrowableTrace, trace);

  return 0;
}

jint JNICALL
Throw(Thread* t, jthrowable throwable)
{
  if (t->exception) {
    return -1;
  }

  ENTER(t, Thread::ActiveState);
  
  t->exception = *throwable;

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

  return makeLocalReference(t, getJClass(t, objectClass(t, *o)));
}

jboolean JNICALL
IsInstanceOf(Thread* t, jobject o, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return instanceOf(t, jclassVmClass(t, *c), *o);
}

jboolean JNICALL
IsAssignableFrom(Thread* t, jclass b, jclass a)
{
  ENTER(t, Thread::ActiveState);

  return isAssignableFrom(t, jclassVmClass(t, *a), jclassVmClass(t, *b));
}

object
findMethod(Thread* t, jclass c, const char* name, const char* spec)
{
  object n = makeByteArray(t, "%s", name);
  PROTECT(t, n);

  object s = makeByteArray(t, "%s", spec);
  return vm::findMethod(t, jclassVmClass(t, *c), n, s);
}

jint
methodID(Thread* t, object method)
{
  if (methodNativeID(t, method) == 0) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->referenceLock);
    
    if (methodNativeID(t, method) == 0) {
      setRoot(t, Machine::JNIMethodTable, vectorAppend
              (t, root(t, Machine::JNIMethodTable), method));
      methodNativeID(t, method) = vectorSize(t, root(t, Machine::JNIMethodTable));
    }
  }

  return methodNativeID(t, method);
}

jmethodID JNICALL
GetMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object method = findMethod(t, c, name, spec);
  if (UNLIKELY(t->exception)) {
    printTrace(t, t->exception);
    return 0;
  }

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
  object method = vectorBody(t, root(t, Machine::JNIMethodTable), m - 1);

  assert(t, (methodFlags(t, method) & ACC_STATIC) == 0);

  return method;
}

jobject JNICALL
NewObjectV(Thread* t, jclass c, jmethodID m, va_list a)
{
  ENTER(t, Thread::ActiveState);

  object o = make(t, jclassVmClass(t, *c));
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
  return (r ? (intValue(t, r) != 0) : false);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? longValue(t, r) : 0);
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
  return (r ? bitsToFloat(intValue(t, r)) : 0);
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
  return (r ? bitsToDouble(longValue(t, r)) : 0);
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
  object method = vectorBody(t, root(t, Machine::JNIMethodTable), m - 1);

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
  return (r ? (intValue(t, r) != 0) : false);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? intValue(t, r) : 0);
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
  return (r ? longValue(t, r) : 0);
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
  return (r ? bitsToFloat(intValue(t, r)) : 0);
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
  return (r ? bitsToDouble(longValue(t, r)) : 0);
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

  object field = resolveField(t, jclassVmClass(t, *c), name, spec);
  if (UNLIKELY(t->exception)) return 0;

  return fieldOffset(t, field);
}

jfieldID JNICALL
GetStaticFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  ENTER(t, Thread::ActiveState);

  object field = resolveField(t, jclassVmClass(t, *c), name, spec);
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

  return makeLocalReference
    (t, cast<object>(classStaticTable(t, jclassVmClass(t, *c)), field));
}

jboolean JNICALL
GetStaticBooleanField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int8_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jbyte JNICALL
GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int8_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jchar JNICALL
GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<uint16_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jshort JNICALL
GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int16_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jint JNICALL
GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int32_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jlong JNICALL
GetStaticLongField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<int64_t>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jfloat JNICALL
GetStaticFloatField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<float>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

jdouble JNICALL
GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  ENTER(t, Thread::ActiveState);

  return cast<double>(classStaticTable(t, jclassVmClass(t, *c)), field);
}

void JNICALL
SetStaticObjectField(Thread* t, jclass c, jfieldID field, jobject v)
{
  ENTER(t, Thread::ActiveState);

  set(t, classStaticTable(t, jclassVmClass(t, *c)), field, (v ? *v : 0));
}

void JNICALL
SetStaticBooleanField(Thread* t, jclass c, jfieldID field, jboolean v)
{
  ENTER(t, Thread::ActiveState);
  
  cast<int8_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticByteField(Thread* t, jclass c, jfieldID field, jbyte v)
{
  ENTER(t, Thread::ActiveState);

  cast<int8_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticCharField(Thread* t, jclass c, jfieldID field, jchar v)
{
  ENTER(t, Thread::ActiveState);

  cast<uint16_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticShortField(Thread* t, jclass c, jfieldID field, jshort v)
{
  ENTER(t, Thread::ActiveState);

  cast<int16_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticIntField(Thread* t, jclass c, jfieldID field, jint v)
{
  ENTER(t, Thread::ActiveState);

  cast<int32_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticLongField(Thread* t, jclass c, jfieldID field, jlong v)
{
  ENTER(t, Thread::ActiveState);

  cast<int64_t>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticFloatField(Thread* t, jclass c, jfieldID field, jfloat v)
{
  ENTER(t, Thread::ActiveState);

  cast<float>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

void JNICALL
SetStaticDoubleField(Thread* t, jclass c, jfieldID field, jdouble v)
{
  ENTER(t, Thread::ActiveState);

  cast<double>(classStaticTable(t, jclassVmClass(t, *c)), field) = v;
}

jobject JNICALL
NewGlobalRef(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->referenceLock);
  
  if (o) {
    for (Reference* r = t->m->jniReferences; r; r = r->next) {
      if (r->target == *o) {
        acquire(t, r);

        return &(r->target);
      }
    }

    Reference* r = new (t->m->heap->allocate(sizeof(Reference)))
      Reference(*o, &(t->m->jniReferences));

    acquire(t, r);

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
    release(t, reinterpret_cast<Reference*>(r));
  }
}

jint JNICALL
EnsureLocalCapacity(Thread*, jint)
{
  return 0;
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

  object a = makeObjectArray(t, jclassVmClass(t, *class_), length);
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

  set(t, *array, ArrayBody + (index * BytesPerWord), (value ? *value : 0));
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
RegisterNatives(Thread* t, jclass c, const JNINativeMethod* methods,
                jint methodCount)
{
  ENTER(t, Thread::ActiveState);

  for (int i = 0; i < methodCount; ++i) {
    if (methods[i].function) {
      object method = findMethod(t, c, methods[i].name, methods[i].signature);
      if (UNLIKELY(t->exception)) return -1;

      registerNative(t, method, methods[i].function);
    }
  }

  return 0;
}

jint JNICALL
UnregisterNatives(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  unregisterNatives(t, *c);

  return 0;
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
  RUNTIME_ARRAY(char, buffer, length + 1);
  if (length == 0) {
    return 0;
  } else if (s[length - 1] == 'k') {
    memcpy(RUNTIME_ARRAY_BODY(buffer), s, length - 1);
    RUNTIME_ARRAY_BODY(buffer)[length - 1] = 0;
    return atoi(RUNTIME_ARRAY_BODY(buffer)) * 1024;
  } else if (s[length - 1] == 'm') {
    memcpy(RUNTIME_ARRAY_BODY(buffer), s, length - 1);
    RUNTIME_ARRAY_BODY(buffer)[length - 1] = 0;
    return atoi(RUNTIME_ARRAY_BODY(buffer)) * 1024 * 1024;
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

} // namespace local

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

  vmTable->DestroyJavaVM = local::DestroyJavaVM;
  vmTable->AttachCurrentThread = local::AttachCurrentThread;
  vmTable->AttachCurrentThreadAsDaemon = local::AttachCurrentThreadAsDaemon;
  vmTable->DetachCurrentThread = local::DetachCurrentThread;
  vmTable->GetEnv = local::GetEnv;

  memset(envTable, 0, sizeof(JNIEnvVTable));

  envTable->GetVersion = local::GetVersion;
  envTable->GetStringLength = local::GetStringLength;
  envTable->GetStringChars = local::GetStringChars;
  envTable->ReleaseStringChars = local::ReleaseStringChars;
  envTable->GetStringRegion = local::GetStringRegion;
  envTable->GetStringCritical = local::GetStringCritical;
  envTable->ReleaseStringCritical = local::ReleaseStringCritical;
  envTable->GetStringUTFLength = local::GetStringUTFLength;
  envTable->GetStringUTFChars = local::GetStringUTFChars;
  envTable->ReleaseStringUTFChars = local::ReleaseStringUTFChars;
  envTable->GetStringUTFRegion = local::GetStringUTFRegion;
  envTable->GetArrayLength = local::GetArrayLength;
  envTable->NewString = local::NewString;
  envTable->NewStringUTF = local::NewStringUTF;
  envTable->DefineClass = local::DefineClass;
  envTable->FindClass = local::FindClass;
  envTable->ThrowNew = local::ThrowNew;
  envTable->Throw = local::Throw;
  envTable->ExceptionCheck = local::ExceptionCheck;
#ifdef AVIAN_GNU
  envTable->NewDirectByteBuffer = vm::NewDirectByteBuffer;
  envTable->GetDirectBufferAddress = vm::GetDirectBufferAddress;
  envTable->GetDirectBufferCapacity = vm::GetDirectBufferCapacity;
#else
  envTable->NewDirectByteBuffer = local::NewDirectByteBuffer;
  envTable->GetDirectBufferAddress = local::GetDirectBufferAddress;
  envTable->GetDirectBufferCapacity = local::GetDirectBufferCapacity;
#endif
  envTable->DeleteLocalRef = local::DeleteLocalRef;
  envTable->GetObjectClass = local::GetObjectClass;
  envTable->IsInstanceOf = local::IsInstanceOf;
  envTable->IsAssignableFrom = local::IsAssignableFrom;
  envTable->GetFieldID = local::GetFieldID;
  envTable->GetMethodID = local::GetMethodID;
  envTable->GetStaticMethodID = local::GetStaticMethodID;
  envTable->NewObject = local::NewObject;
  envTable->NewObjectV = local::NewObjectV;
  envTable->CallObjectMethodV = local::CallObjectMethodV;
  envTable->CallObjectMethod = local::CallObjectMethod;
  envTable->CallBooleanMethodV = local::CallBooleanMethodV;
  envTable->CallBooleanMethod = local::CallBooleanMethod;
  envTable->CallByteMethodV = local::CallByteMethodV;
  envTable->CallByteMethod = local::CallByteMethod;
  envTable->CallCharMethodV = local::CallCharMethodV;
  envTable->CallCharMethod = local::CallCharMethod;
  envTable->CallShortMethodV = local::CallShortMethodV;
  envTable->CallShortMethod = local::CallShortMethod;
  envTable->CallIntMethodV = local::CallIntMethodV;
  envTable->CallIntMethod = local::CallIntMethod;
  envTable->CallLongMethodV = local::CallLongMethodV;
  envTable->CallLongMethod = local::CallLongMethod;
  envTable->CallFloatMethodV = local::CallFloatMethodV;
  envTable->CallFloatMethod = local::CallFloatMethod;
  envTable->CallDoubleMethodV = local::CallDoubleMethodV;
  envTable->CallDoubleMethod = local::CallDoubleMethod;
  envTable->CallVoidMethodV = local::CallVoidMethodV;
  envTable->CallVoidMethod = local::CallVoidMethod;
  envTable->CallStaticObjectMethodV = local::CallStaticObjectMethodV;
  envTable->CallStaticObjectMethod = local::CallStaticObjectMethod;
  envTable->CallStaticBooleanMethodV = local::CallStaticBooleanMethodV;
  envTable->CallStaticBooleanMethod = local::CallStaticBooleanMethod;
  envTable->CallStaticByteMethodV = local::CallStaticByteMethodV;
  envTable->CallStaticByteMethod = local::CallStaticByteMethod;
  envTable->CallStaticCharMethodV = local::CallStaticCharMethodV;
  envTable->CallStaticCharMethod = local::CallStaticCharMethod;
  envTable->CallStaticShortMethodV = local::CallStaticShortMethodV;
  envTable->CallStaticShortMethod = local::CallStaticShortMethod;
  envTable->CallStaticIntMethodV = local::CallStaticIntMethodV;
  envTable->CallStaticIntMethod = local::CallStaticIntMethod;
  envTable->CallStaticLongMethodV = local::CallStaticLongMethodV;
  envTable->CallStaticLongMethod = local::CallStaticLongMethod;
  envTable->CallStaticFloatMethodV = local::CallStaticFloatMethodV;
  envTable->CallStaticFloatMethod = local::CallStaticFloatMethod;
  envTable->CallStaticDoubleMethodV = local::CallStaticDoubleMethodV;
  envTable->CallStaticDoubleMethod = local::CallStaticDoubleMethod;
  envTable->CallStaticVoidMethodV = local::CallStaticVoidMethodV;
  envTable->CallStaticVoidMethod = local::CallStaticVoidMethod;
  envTable->GetStaticFieldID = local::GetStaticFieldID;
  envTable->GetObjectField = local::GetObjectField;
  envTable->GetBooleanField = local::GetBooleanField;
  envTable->GetByteField = local::GetByteField;
  envTable->GetCharField = local::GetCharField;
  envTable->GetShortField = local::GetShortField;
  envTable->GetIntField = local::GetIntField;
  envTable->GetLongField = local::GetLongField;
  envTable->GetFloatField = local::GetFloatField;
  envTable->GetDoubleField = local::GetDoubleField;
  envTable->SetObjectField = local::SetObjectField;
  envTable->SetBooleanField = local::SetBooleanField;
  envTable->SetByteField = local::SetByteField;
  envTable->SetCharField = local::SetCharField;
  envTable->SetShortField = local::SetShortField;
  envTable->SetIntField = local::SetIntField;
  envTable->SetLongField = local::SetLongField;
  envTable->SetFloatField = local::SetFloatField;
  envTable->SetDoubleField = local::SetDoubleField;
  envTable->GetStaticObjectField = local::GetStaticObjectField;
  envTable->GetStaticBooleanField = local::GetStaticBooleanField;
  envTable->GetStaticByteField = local::GetStaticByteField;
  envTable->GetStaticCharField = local::GetStaticCharField;
  envTable->GetStaticShortField = local::GetStaticShortField;
  envTable->GetStaticIntField = local::GetStaticIntField;
  envTable->GetStaticLongField = local::GetStaticLongField;
  envTable->GetStaticFloatField = local::GetStaticFloatField;
  envTable->GetStaticDoubleField = local::GetStaticDoubleField;
  envTable->SetStaticObjectField = local::SetStaticObjectField;
  envTable->SetStaticBooleanField = local::SetStaticBooleanField;
  envTable->SetStaticByteField = local::SetStaticByteField;
  envTable->SetStaticCharField = local::SetStaticCharField;
  envTable->SetStaticShortField = local::SetStaticShortField;
  envTable->SetStaticIntField = local::SetStaticIntField;
  envTable->SetStaticLongField = local::SetStaticLongField;
  envTable->SetStaticFloatField = local::SetStaticFloatField;
  envTable->SetStaticDoubleField = local::SetStaticDoubleField;
  envTable->NewGlobalRef = local::NewGlobalRef;
  envTable->NewWeakGlobalRef = local::NewGlobalRef;
  envTable->DeleteGlobalRef = local::DeleteGlobalRef;
  envTable->EnsureLocalCapacity = local::EnsureLocalCapacity;
  envTable->ExceptionOccurred = local::ExceptionOccurred;
  envTable->ExceptionDescribe = local::ExceptionDescribe;
  envTable->ExceptionClear = local::ExceptionClear;
  envTable->NewObjectArray = local::NewObjectArray;
  envTable->GetObjectArrayElement = local::GetObjectArrayElement;
  envTable->SetObjectArrayElement = local::SetObjectArrayElement;
  envTable->NewBooleanArray = local::NewBooleanArray;
  envTable->NewByteArray = local::NewByteArray;
  envTable->NewCharArray = local::NewCharArray;
  envTable->NewShortArray = local::NewShortArray;
  envTable->NewIntArray = local::NewIntArray;
  envTable->NewLongArray = local::NewLongArray;
  envTable->NewFloatArray = local::NewFloatArray;
  envTable->NewDoubleArray = local::NewDoubleArray;
  envTable->GetBooleanArrayElements = local::GetBooleanArrayElements;
  envTable->GetByteArrayElements = local::GetByteArrayElements;
  envTable->GetCharArrayElements = local::GetCharArrayElements;
  envTable->GetShortArrayElements = local::GetShortArrayElements;
  envTable->GetIntArrayElements = local::GetIntArrayElements;
  envTable->GetLongArrayElements = local::GetLongArrayElements;
  envTable->GetFloatArrayElements = local::GetFloatArrayElements;
  envTable->GetDoubleArrayElements = local::GetDoubleArrayElements;
  envTable->ReleaseBooleanArrayElements = local::ReleaseBooleanArrayElements;
  envTable->ReleaseByteArrayElements = local::ReleaseByteArrayElements;
  envTable->ReleaseCharArrayElements = local::ReleaseCharArrayElements;
  envTable->ReleaseShortArrayElements = local::ReleaseShortArrayElements;
  envTable->ReleaseIntArrayElements = local::ReleaseIntArrayElements;
  envTable->ReleaseLongArrayElements = local::ReleaseLongArrayElements;
  envTable->ReleaseFloatArrayElements = local::ReleaseFloatArrayElements;
  envTable->ReleaseDoubleArrayElements = local::ReleaseDoubleArrayElements;
  envTable->GetBooleanArrayRegion = local::GetBooleanArrayRegion;
  envTable->GetByteArrayRegion = local::GetByteArrayRegion;
  envTable->GetCharArrayRegion = local::GetCharArrayRegion;
  envTable->GetShortArrayRegion = local::GetShortArrayRegion;
  envTable->GetIntArrayRegion = local::GetIntArrayRegion;
  envTable->GetLongArrayRegion = local::GetLongArrayRegion;
  envTable->GetFloatArrayRegion = local::GetFloatArrayRegion;
  envTable->GetDoubleArrayRegion = local::GetDoubleArrayRegion;
  envTable->SetBooleanArrayRegion = local::SetBooleanArrayRegion;
  envTable->SetByteArrayRegion = local::SetByteArrayRegion;
  envTable->SetCharArrayRegion = local::SetCharArrayRegion;
  envTable->SetShortArrayRegion = local::SetShortArrayRegion;
  envTable->SetIntArrayRegion = local::SetIntArrayRegion;
  envTable->SetLongArrayRegion = local::SetLongArrayRegion;
  envTable->SetFloatArrayRegion = local::SetFloatArrayRegion;
  envTable->SetDoubleArrayRegion = local::SetDoubleArrayRegion;
  envTable->GetPrimitiveArrayCritical = local::GetPrimitiveArrayCritical;
  envTable->ReleasePrimitiveArrayCritical
    = local::ReleasePrimitiveArrayCritical;
  envTable->RegisterNatives = local::RegisterNatives;
  envTable->UnregisterNatives = local::UnregisterNatives;
  envTable->MonitorEnter = local::MonitorEnter;
  envTable->MonitorExit = local::MonitorExit;
  envTable->GetJavaVM = local::GetJavaVM;
  envTable->IsSameObject = local::IsSameObject;
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
  local::JavaVMInitArgs* a = static_cast<local::JavaVMInitArgs*>(args);

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
        heapLimit = local::parseSize(p + 2);
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

  unsigned bootClasspathBufferSize = bcppl + bcpl + bcpal + 3;
  RUNTIME_ARRAY(char, bootClasspathBuffer, bootClasspathBufferSize);
  char* bootClasspathPointer = RUNTIME_ARRAY_BODY(bootClasspathBuffer);
  local::append
    (&bootClasspathPointer, bootClasspathPrepend, bcppl, PATH_SEPARATOR);
  local::append(&bootClasspathPointer, bootClasspath, bcpl,
                bcpal ? PATH_SEPARATOR : 0);
  local::append(&bootClasspathPointer, bootClasspathAppend, bcpal, 0);

  System* s = makeSystem(crashDumpDirectory);
  Heap* h = makeHeap(s, heapLimit);
  Finder* bf = makeFinder
    (s, RUNTIME_ARRAY_BODY(bootClasspathBuffer), bootLibrary);
  Finder* af = makeFinder(s, classpath, bootLibrary);
  Processor* p = makeProcessor(s, h, true);
  Classpath* c = makeClasspath(s, h);

  const char** properties = static_cast<const char**>
    (h->allocate(sizeof(const char*) * propertyCount));
  const char** propertyPointer = properties;
  for (int i = 0; i < a->nOptions; ++i) {
    if (strncmp(a->options[i].optionString, "-D", 2) == 0) {
      *(propertyPointer++) = a->options[i].optionString + 2;
    }
  }

  *m = new (h->allocate(sizeof(Machine)))
    Machine(s, h, bf, af, p, c, properties, propertyCount);

  *t = p->makeThread(*m, 0, 0);

  return 0;
}
