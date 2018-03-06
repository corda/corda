/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/jnienv.h"
#include "avian/machine.h"
#include "avian/util.h"
#include "avian/processor.h"
#include "avian/constants.h"

#include <avian/util/runtime-array.h>

using namespace vm;

namespace {

namespace local {

jint JNICALL AttachCurrentThread(Machine* m, Thread** t, void*)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t == 0) {
    *t = attachThread(m, false);
  }
  return 0;
}

jint JNICALL AttachCurrentThreadAsDaemon(Machine* m, Thread** t, void*)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t == 0) {
    *t = attachThread(m, true);
  }
  return 0;
}

jint JNICALL DetachCurrentThread(Machine* m)
{
  Thread* t = static_cast<Thread*>(m->localThread->get());
  if (t) {
    // todo: detaching the root thread seems to cause stability
    // problems which I haven't yet had a chance to investigate
    // thoroughly.  Meanwhile, we just ignore requests to detach it,
    // which leaks a bit of memory but should be harmless otherwise.
    if (m->rootThread != t) {
      m->localThread->set(0);

      ACQUIRE_RAW(t, t->m->stateLock);

      enter(t, Thread::ActiveState);

      t->javaThread->peer() = 0;

      enter(t, Thread::ZombieState);

      t->state = Thread::JoinedState;
    }

    return 0;
  } else {
    return -1;
  }
}

uint64_t destroyJavaVM(Thread* t, uintptr_t*)
{
  // wait for other non-daemon threads to exit
  {
    ACQUIRE(t, t->m->stateLock);
    while (t->m->liveCount - t->m->daemonCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  }
  {
    ENTER(t, Thread::ActiveState);

    t->m->classpath->shutDown(t);
  }
  // wait again in case the Classpath::shutDown process started new
  // threads:
  {
    ACQUIRE(t, t->m->stateLock);
    while (t->m->liveCount - t->m->daemonCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }

    enter(t, Thread::ExclusiveState);
  }
  shutDown(t);

  return 1;
}

jint JNICALL DestroyJavaVM(Machine* m)
{
  Thread* t;
  AttachCurrentThread(m, &t, 0);

  if (runRaw(t, destroyJavaVM, 0)) {
    t->exit();
    return 0;
  } else {
    return -1;
  }
}

jint JNICALL GetEnv(Machine* m, Thread** t, jint version)
{
  *t = static_cast<Thread*>(m->localThread->get());
  if (*t) {
    if (version <= JNI_VERSION_1_6) {
      return AVIAN_JNI_OK;
    } else {
      return AVIAN_JNI_EVERSION;
    }
  } else {
    return AVIAN_JNI_EDETACHED;
  }
}

jint JNICALL GetVersion(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return JNI_VERSION_1_6;
}

jsize JNICALL GetStringLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return (*s)->length(t);
}

const jchar* JNICALL GetStringChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  jchar* chars = static_cast<jchar*>(
      t->m->heap->allocate(((*s)->length(t) + 1) * sizeof(jchar)));
  stringChars(t, *s, chars);

  if (isCopy)
    *isCopy = true;
  return chars;
}

void JNICALL ReleaseStringChars(Thread* t, jstring s, const jchar* chars)
{
  ENTER(t, Thread::ActiveState);

  t->m->heap->free(chars, ((*s)->length(t) + 1) * sizeof(jchar));
}

void JNICALL
    GetStringRegion(Thread* t, jstring s, jsize start, jsize length, jchar* dst)
{
  ENTER(t, Thread::ActiveState);

  stringChars(t, *s, start, length, dst);
}

const jchar* JNICALL GetStringCritical(Thread* t, jstring s, jboolean* isCopy)
{
  if (t->criticalLevel == 0) {
    enter(t, Thread::ActiveState);
  }

  ++t->criticalLevel;

  if (isCopy) {
    *isCopy = true;
  }

  object data = (*s)->data();
  if (objectClass(t, data) == type(t, GcByteArray::Type)) {
    return GetStringChars(t, s, isCopy);
  } else {
    return &cast<GcCharArray>(t, data)->body()[(*s)->offset(t)];
  }
}

void JNICALL ReleaseStringCritical(Thread* t, jstring s, const jchar* chars)
{
  if (objectClass(t, (*s)->data()) == type(t, GcByteArray::Type)) {
    ReleaseStringChars(t, s, chars);
  }

  if ((--t->criticalLevel) == 0) {
    enter(t, Thread::IdleState);
  }
}

jsize JNICALL GetStringUTFLength(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return stringUTFLength(t, *s);
}

const char* JNICALL GetStringUTFChars(Thread* t, jstring s, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  int length = stringUTFLength(t, *s);
  char* chars = static_cast<char*>(t->m->heap->allocate(length + 1));
  stringUTFChars(t, *s, chars, length);

  if (isCopy)
    *isCopy = true;
  return chars;
}

void JNICALL ReleaseStringUTFChars(Thread* t, jstring s, const char* chars)
{
  ENTER(t, Thread::ActiveState);

  t->m->heap->free(chars, stringUTFLength(t, *s) + 1);
}

void JNICALL GetStringUTFRegion(Thread* t,
                                jstring s,
                                jsize start,
                                jsize length,
                                char* dst)
{
  ENTER(t, Thread::ActiveState);

  stringUTFChars(
      t, *s, start, length, dst, stringUTFLength(t, *s, start, length));
}

jsize JNICALL GetArrayLength(Thread* t, jarray array)
{
  ENTER(t, Thread::ActiveState);

  return fieldAtOffset<uintptr_t>(*array, BytesPerWord);
}

uint64_t newString(Thread* t, uintptr_t* arguments)
{
  const jchar* chars = reinterpret_cast<const jchar*>(arguments[0]);
  jsize size = arguments[1];

  GcCharArray* a = makeCharArray(t, size);
  if (size) {
    memcpy(a->body().begin(), chars, size * sizeof(jchar));
  }

  return reinterpret_cast<uint64_t>(
      makeLocalReference(t, t->m->classpath->makeString(t, a, 0, size)));
}

jstring JNICALL NewString(Thread* t, const jchar* chars, jsize size)
{
  if (chars == 0)
    return 0;

  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(chars), static_cast<uintptr_t>(size)};

  return reinterpret_cast<jstring>(run(t, newString, arguments));
}

uint64_t newStringUTF(Thread* t, uintptr_t* arguments)
{
  const char* chars = reinterpret_cast<const char*>(arguments[0]);

  object array = parseUtf8(t, chars, strlen(chars));

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t,
      t->m->classpath->makeString(
          t, array, 0, fieldAtOffset<uintptr_t>(array, BytesPerWord) - 1)));
}

jstring JNICALL NewStringUTF(Thread* t, const char* chars)
{
  if (chars == 0)
    return 0;

  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(chars)};

  return reinterpret_cast<jstring>(run(t, newStringUTF, arguments));
}

void replace(int a, int b, const char* in, int8_t* out)
{
  while (*in) {
    *out = (*in == a ? b : *in);
    ++in;
    ++out;
  }
  *out = 0;
}

uint64_t defineClass(Thread* t, uintptr_t* arguments)
{
  jobject loader = reinterpret_cast<jobject>(arguments[0]);
  const uint8_t* buffer = reinterpret_cast<const uint8_t*>(arguments[1]);
  jsize length = arguments[2];

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t,
      getJClass(
          t,
          cast<GcClass>(t,
                        defineClass(t,
                                    loader ? cast<GcClassLoader>(t, *loader)
                                           : roots(t)->bootLoader(),
                                    buffer,
                                    length)))));
}

jclass JNICALL DefineClass(Thread* t,
                           const char*,
                           jobject loader,
                           const jbyte* buffer,
                           jsize length)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(loader),
                           reinterpret_cast<uintptr_t>(buffer),
                           static_cast<uintptr_t>(length)};

  return reinterpret_cast<jclass>(run(t, defineClass, arguments));
}

uint64_t findClass(Thread* t, uintptr_t* arguments)
{
  const char* name = reinterpret_cast<const char*>(arguments[0]);

  GcByteArray* n = makeByteArray(t, strlen(name) + 1);
  replace('.', '/', name, n->body().begin());

  GcMethod* caller = getCaller(t, 0);

  GcClass* c
      = resolveClass(t,
                     caller ? t->m->classpath->libraryClassLoader(t, caller)
                            : roots(t)->appLoader(),
                     n);

  if (t->m->classpath->mayInitClasses()) {
    PROTECT(t, c);

    initClass(t, c);
  }

  return reinterpret_cast<uint64_t>(makeLocalReference(t, getJClass(t, c)));
}

jclass JNICALL FindClass(Thread* t, const char* name)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(name)};

  return reinterpret_cast<jclass>(run(t, findClass, arguments));
}

uint64_t throwNew(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  const char* message = reinterpret_cast<const char*>(arguments[1]);

  GcString* m = 0;
  PROTECT(t, m);

  if (message) {
    m = makeString(t, "%s", message);
  }

  object trace = makeTrace(t);
  PROTECT(t, trace);

  t->exception = cast<GcThrowable>(t, make(t, (*c)->vmClass()));
  t->exception->setMessage(t, m);
  t->exception->setTrace(t, trace);

  return 1;
}

jint JNICALL ThrowNew(Thread* t, jclass c, const char* message)
{
  if (t->exception) {
    return -1;
  }

  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), reinterpret_cast<uintptr_t>(message)};

  return run(t, throwNew, arguments) ? 0 : -1;
}

jint JNICALL Throw(Thread* t, jthrowable throwable)
{
  if (t->exception) {
    return -1;
  }

  ENTER(t, Thread::ActiveState);

  t->exception = *throwable;

  return 0;
}

jobject JNICALL NewLocalRef(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, *o);
}

void JNICALL DeleteLocalRef(Thread* t, jobject r)
{
  ENTER(t, Thread::ActiveState);

  disposeLocalReference(t, r);
}

jboolean JNICALL ExceptionCheck(Thread* t)
{
  return t->exception != 0;
}

uint64_t getObjectClass(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);

  return reinterpret_cast<uint64_t>(
      makeLocalReference(t, getJClass(t, objectClass(t, *o))));
}

jclass JNICALL GetObjectClass(Thread* t, jobject o)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o)};

  return reinterpret_cast<jclass>(run(t, getObjectClass, arguments));
}

uint64_t getSuperclass(Thread* t, uintptr_t* arguments)
{
  GcClass* class_ = (*reinterpret_cast<jclass>(arguments[0]))->vmClass();
  if (class_->flags() & ACC_INTERFACE) {
    return 0;
  } else {
    GcClass* super = class_->super();
    return super ? reinterpret_cast<uint64_t>(
                       makeLocalReference(t, getJClass(t, super)))
                 : 0;
  }
}

jclass JNICALL GetSuperclass(Thread* t, jclass c)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c)};

  return reinterpret_cast<jclass>(run(t, getSuperclass, arguments));
}

uint64_t isInstanceOf(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jclass c = reinterpret_cast<jclass>(arguments[1]);

  return instanceOf(t, (*c)->vmClass(), *o);
}

jboolean JNICALL IsInstanceOf(Thread* t, jobject o, jclass c)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), reinterpret_cast<uintptr_t>(c)};

  return run(t, isInstanceOf, arguments);
}

uint64_t isAssignableFrom(Thread* t, uintptr_t* arguments)
{
  jclass b = reinterpret_cast<jclass>(arguments[0]);
  jclass a = reinterpret_cast<jclass>(arguments[1]);

  return isAssignableFrom(t, (*a)->vmClass(), (*b)->vmClass());
}

jboolean JNICALL IsAssignableFrom(Thread* t, jclass b, jclass a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(b), reinterpret_cast<uintptr_t>(a)};

  return run(t, isAssignableFrom, arguments);
}

GcMethod* findMethod(Thread* t, jclass c, const char* name, const char* spec)
{
  GcByteArray* n = makeByteArray(t, "%s", name);
  PROTECT(t, n);

  GcByteArray* s = makeByteArray(t, "%s", spec);
  return vm::findMethod(t, (*c)->vmClass(), n, s);
}

jint methodID(Thread* t, GcMethod* method)
{
  int id = method->nativeID();

  loadMemoryBarrier();

  if (id == 0) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->referenceLock);

    if (method->nativeID() == 0) {
      GcVector* v = vectorAppend(t, roots(t)->jNIMethodTable(), method);
      // sequence point, for gc (don't recombine statements)
      roots(t)->setJNIMethodTable(t, v);

      storeStoreMemoryBarrier();

      method->nativeID() = roots(t)->jNIMethodTable()->size();
    }
  }

  return method->nativeID();
}

uint64_t getMethodID(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  const char* name = reinterpret_cast<const char*>(arguments[1]);
  const char* spec = reinterpret_cast<const char*>(arguments[2]);

  GcMethod* method = findMethod(t, c, name, spec);

  assertT(t, (method->flags() & ACC_STATIC) == 0);

  return methodID(t, method);
}

jmethodID JNICALL
    GetMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           reinterpret_cast<uintptr_t>(name),
                           reinterpret_cast<uintptr_t>(spec)};

  return run(t, getMethodID, arguments);
}

uint64_t getStaticMethodID(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  const char* name = reinterpret_cast<const char*>(arguments[1]);
  const char* spec = reinterpret_cast<const char*>(arguments[2]);

  GcMethod* method = findMethod(t, c, name, spec);

  assertT(t, method->flags() & ACC_STATIC);

  return methodID(t, method);
}

jmethodID JNICALL
    GetStaticMethodID(Thread* t, jclass c, const char* name, const char* spec)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           reinterpret_cast<uintptr_t>(name),
                           reinterpret_cast<uintptr_t>(spec)};

  return run(t, getStaticMethodID, arguments);
}

GcMethod* getMethod(Thread* t, jmethodID m)
{
  assertT(t, m);

  GcMethod* method
      = cast<GcMethod>(t, roots(t)->jNIMethodTable()->body()[m - 1]);

  assertT(t, (method->flags() & ACC_STATIC) == 0);

  return method;
}

uint64_t newObjectV(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  jmethodID m = arguments[1];
  va_list* a = reinterpret_cast<va_list*>(arguments[2]);

  object o = make(t, (*c)->vmClass());
  PROTECT(t, o);

  t->m->processor->invokeList(t, getMethod(t, m), o, true, *a);

  return reinterpret_cast<uint64_t>(makeLocalReference(t, o));
}

jobject JNICALL NewObjectV(Thread* t, jclass c, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return reinterpret_cast<jobject>(run(t, newObjectV, arguments));
}

jobject JNICALL NewObject(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = NewObjectV(t, c, m, a);

  va_end(a);

  return r;
}

uint64_t newObjectA(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  jmethodID m = arguments[1];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[2]);

  object o = make(t, (*c)->vmClass());
  PROTECT(t, o);

  t->m->processor->invokeArray(t, getMethod(t, m), o, a);

  return reinterpret_cast<uint64_t>(makeLocalReference(t, o));
}

jobject JNICALL NewObjectA(Thread* t, jclass c, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), m, reinterpret_cast<uintptr_t>(a)};

  return reinterpret_cast<jobject>(run(t, newObjectA, arguments));
}

uint64_t callObjectMethodV(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  va_list* a = reinterpret_cast<va_list*>(arguments[2]);

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t, t->m->processor->invokeList(t, getMethod(t, m), *o, true, *a)));
}

jobject JNICALL CallObjectMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return reinterpret_cast<jobject>(run(t, callObjectMethodV, arguments));
}

jobject JNICALL CallObjectMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallObjectMethodV(t, o, m, a);

  va_end(a);

  return r;
}

uint64_t callObjectMethodA(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[2]);

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t, t->m->processor->invokeArray(t, getMethod(t, m), *o, a)));
}

jobject JNICALL
    CallObjectMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return reinterpret_cast<jobject>(run(t, callObjectMethodA, arguments));
}

uint64_t callIntMethodV(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  va_list* a = reinterpret_cast<va_list*>(arguments[2]);

  return cast<GcInt>(
             t, t->m->processor->invokeList(t, getMethod(t, m), *o, true, *a))
      ->value();
}

jboolean JNICALL
    CallBooleanMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callIntMethodV, arguments) != 0;
}

jboolean JNICALL CallBooleanMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallBooleanMethodV(t, o, m, a);

  va_end(a);

  return r;
}

uint64_t callIntMethodA(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[2]);

  return cast<GcInt>(t, t->m->processor->invokeArray(t, getMethod(t, m), *o, a))
      ->value();
}

jboolean JNICALL
    CallBooleanMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callIntMethodA, arguments) != 0;
}

jbyte JNICALL CallByteMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callIntMethodV, arguments);
}

jbyte JNICALL CallByteMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallByteMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
    CallByteMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callIntMethodA, arguments);
}

jchar JNICALL CallCharMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callIntMethodV, arguments);
}

jchar JNICALL CallCharMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallCharMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
    CallCharMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callIntMethodA, arguments);
}

jshort JNICALL CallShortMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callIntMethodV, arguments);
}

jshort JNICALL CallShortMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallShortMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
    CallShortMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callIntMethodA, arguments);
}

jint JNICALL CallIntMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callIntMethodV, arguments);
}

jint JNICALL CallIntMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallIntMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jint JNICALL CallIntMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callIntMethodA, arguments);
}

uint64_t callLongMethodV(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  va_list* a = reinterpret_cast<va_list*>(arguments[2]);

  return cast<GcLong>(
             t, t->m->processor->invokeList(t, getMethod(t, m), *o, true, *a))
      ->value();
}

jlong JNICALL CallLongMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callLongMethodV, arguments);
}

jlong JNICALL CallLongMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallLongMethodV(t, o, m, a);

  va_end(a);

  return r;
}

uint64_t callLongMethodA(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[2]);

  return cast<GcLong>(t,
                      t->m->processor->invokeArray(t, getMethod(t, m), *o, a))
      ->value();
}

jlong JNICALL
    CallLongMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callLongMethodA, arguments);
}

jfloat JNICALL CallFloatMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return bitsToFloat(run(t, callIntMethodV, arguments));
}

jfloat JNICALL CallFloatMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallFloatMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
    CallFloatMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return bitsToFloat(run(t, callIntMethodA, arguments));
}

jdouble JNICALL CallDoubleMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return bitsToDouble(run(t, callLongMethodV, arguments));
}

jdouble JNICALL CallDoubleMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallDoubleMethodV(t, o, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
    CallDoubleMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  return bitsToDouble(run(t, callLongMethodA, arguments));
}

uint64_t callVoidMethodV(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  va_list* a = reinterpret_cast<va_list*>(arguments[2]);

  t->m->processor->invokeList(t, getMethod(t, m), *o, true, *a);

  return 0;
}

void JNICALL CallVoidMethodV(Thread* t, jobject o, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o),
                           m,
                           reinterpret_cast<uintptr_t>(VA_LIST(a))};

  run(t, callVoidMethodV, arguments);
}

void JNICALL CallVoidMethod(Thread* t, jobject o, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallVoidMethodV(t, o, m, a);

  va_end(a);
}

uint64_t callVoidMethodA(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jmethodID m = arguments[1];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[2]);

  t->m->processor->invokeArray(t, getMethod(t, m), *o, a);

  return 0;
}

void JNICALL CallVoidMethodA(Thread* t, jobject o, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), m, reinterpret_cast<uintptr_t>(a)};

  run(t, callVoidMethodA, arguments);
}

GcMethod* getStaticMethod(Thread* t, jmethodID m)
{
  assertT(t, m);

  GcMethod* method
      = cast<GcMethod>(t, roots(t)->jNIMethodTable()->body()[m - 1]);

  assertT(t, method->flags() & ACC_STATIC);

  return method;
}

uint64_t callStaticObjectMethodV(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  va_list* a = reinterpret_cast<va_list*>(arguments[1]);

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t, t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, *a)));
}

jobject JNICALL
    CallStaticObjectMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return reinterpret_cast<jobject>(run(t, callStaticObjectMethodV, arguments));
}

jobject JNICALL CallStaticObjectMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jobject r = CallStaticObjectMethodV(t, c, m, a);

  va_end(a);

  return r;
}

uint64_t callStaticObjectMethodA(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[1]);

  return reinterpret_cast<uint64_t>(makeLocalReference(
      t, t->m->processor->invokeArray(t, getStaticMethod(t, m), 0, a)));
}

jobject JNICALL
    CallStaticObjectMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return reinterpret_cast<jobject>(run(t, callStaticObjectMethodA, arguments));
}

uint64_t callStaticIntMethodV(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  va_list* a = reinterpret_cast<va_list*>(arguments[1]);

  return cast<GcInt>(t,
                     t->m->processor->invokeList(
                         t, getStaticMethod(t, m), 0, true, *a))->value();
}

jboolean JNICALL
    CallStaticBooleanMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticIntMethodV, arguments) != 0;
}

jboolean JNICALL CallStaticBooleanMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jboolean r = CallStaticBooleanMethodV(t, c, m, a);

  va_end(a);

  return r;
}

uint64_t callStaticIntMethodA(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[1]);

  return cast<GcInt>(
             t, t->m->processor->invokeArray(t, getStaticMethod(t, m), 0, a))
      ->value();
}

jboolean JNICALL
    CallStaticBooleanMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticIntMethodA, arguments) != 0;
}

jbyte JNICALL CallStaticByteMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticIntMethodV, arguments);
}

jbyte JNICALL CallStaticByteMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jbyte r = CallStaticByteMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jbyte JNICALL
    CallStaticByteMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticIntMethodA, arguments);
}

jchar JNICALL CallStaticCharMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticIntMethodV, arguments);
}

jchar JNICALL CallStaticCharMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jchar r = CallStaticCharMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jchar JNICALL
    CallStaticCharMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticIntMethodA, arguments);
}

jshort JNICALL CallStaticShortMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticIntMethodV, arguments);
}

jshort JNICALL CallStaticShortMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jshort r = CallStaticShortMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jshort JNICALL
    CallStaticShortMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticIntMethodA, arguments);
}

jint JNICALL CallStaticIntMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticIntMethodV, arguments);
}

jint JNICALL CallStaticIntMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jint r = CallStaticIntMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jint JNICALL
    CallStaticIntMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticIntMethodA, arguments);
}

uint64_t callStaticLongMethodV(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  va_list* a = reinterpret_cast<va_list*>(arguments[1]);

  return cast<GcLong>(t,
                      t->m->processor->invokeList(
                          t, getStaticMethod(t, m), 0, true, *a))->value();
}

jlong JNICALL CallStaticLongMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return run(t, callStaticLongMethodV, arguments);
}

jlong JNICALL CallStaticLongMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jlong r = CallStaticLongMethodV(t, c, m, a);

  va_end(a);

  return r;
}

uint64_t callStaticLongMethodA(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[1]);

  return cast<GcLong>(
             t, t->m->processor->invokeArray(t, getStaticMethod(t, m), 0, a))
      ->value();
}

jlong JNICALL
    CallStaticLongMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return run(t, callStaticLongMethodA, arguments);
}

jfloat JNICALL CallStaticFloatMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return bitsToFloat(run(t, callStaticIntMethodV, arguments));
}

jfloat JNICALL CallStaticFloatMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jfloat r = CallStaticFloatMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jfloat JNICALL
    CallStaticFloatMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return bitsToFloat(run(t, callStaticIntMethodA, arguments));
}

jdouble JNICALL
    CallStaticDoubleMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  return bitsToDouble(run(t, callStaticLongMethodV, arguments));
}

jdouble JNICALL CallStaticDoubleMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  jdouble r = CallStaticDoubleMethodV(t, c, m, a);

  va_end(a);

  return r;
}

jdouble JNICALL
    CallStaticDoubleMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  return bitsToDouble(run(t, callStaticLongMethodA, arguments));
}

uint64_t callStaticVoidMethodV(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  va_list* a = reinterpret_cast<va_list*>(arguments[1]);

  t->m->processor->invokeList(t, getStaticMethod(t, m), 0, true, *a);

  return 0;
}

void JNICALL CallStaticVoidMethodV(Thread* t, jclass, jmethodID m, va_list a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(VA_LIST(a))};

  run(t, callStaticVoidMethodV, arguments);
}

void JNICALL CallStaticVoidMethod(Thread* t, jclass c, jmethodID m, ...)
{
  va_list a;
  va_start(a, m);

  CallStaticVoidMethodV(t, c, m, a);

  va_end(a);
}

uint64_t callStaticVoidMethodA(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[0];
  const jvalue* a = reinterpret_cast<const jvalue*>(arguments[1]);

  t->m->processor->invokeArray(t, getStaticMethod(t, m), 0, a);

  return 0;
}

void JNICALL
    CallStaticVoidMethodA(Thread* t, jclass, jmethodID m, const jvalue* a)
{
  uintptr_t arguments[] = {m, reinterpret_cast<uintptr_t>(a)};

  run(t, callStaticVoidMethodA, arguments);
}

jint fieldID(Thread* t, GcField* field)
{
  int id = field->nativeID();

  loadMemoryBarrier();

  if (id == 0) {
    PROTECT(t, field);

    ACQUIRE(t, t->m->referenceLock);

    if (field->nativeID() == 0) {
      GcVector* v = vectorAppend(t, roots(t)->jNIFieldTable(), field);
      // sequence point, for gc (don't recombine statements)
      roots(t)->setJNIFieldTable(t, v);

      storeStoreMemoryBarrier();

      field->nativeID() = roots(t)->jNIFieldTable()->size();
    }
  }

  return field->nativeID();
}

uint64_t getFieldID(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  const char* name = reinterpret_cast<const char*>(arguments[1]);
  const char* spec = reinterpret_cast<const char*>(arguments[2]);

  return fieldID(t, resolveField(t, (*c)->vmClass(), name, spec));
}

jfieldID JNICALL
    GetFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           reinterpret_cast<uintptr_t>(name),
                           reinterpret_cast<uintptr_t>(spec)};

  return run(t, getFieldID, arguments);
}

jfieldID JNICALL
    GetStaticFieldID(Thread* t, jclass c, const char* name, const char* spec)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           reinterpret_cast<uintptr_t>(name),
                           reinterpret_cast<uintptr_t>(spec)};

  return run(t, getFieldID, arguments);
}

GcField* getField(Thread* t, jfieldID f)
{
  assertT(t, f);

  GcField* field = cast<GcField>(t, roots(t)->jNIFieldTable()->body()[f - 1]);

  assertT(t, (field->flags() & ACC_STATIC) == 0);

  return field;
}

uint64_t getObjectField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return reinterpret_cast<uintptr_t>(
      makeLocalReference(t, fieldAtOffset<object>(*o, field->offset())));
}

jobject JNICALL GetObjectField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return reinterpret_cast<jobject>(run(t, getObjectField, arguments));
}

uint64_t getBooleanField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jboolean>(*o, field->offset());
}

jboolean JNICALL GetBooleanField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getBooleanField, arguments);
}

uint64_t getByteField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jbyte>(*o, field->offset());
}

jbyte JNICALL GetByteField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getByteField, arguments);
}

uint64_t getCharField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jchar>(*o, field->offset());
}

jchar JNICALL GetCharField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getCharField, arguments);
}

uint64_t getShortField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jshort>(*o, field->offset());
}

jshort JNICALL GetShortField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getShortField, arguments);
}

uint64_t getIntField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jint>(*o, field->offset());
}

jint JNICALL GetIntField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getIntField, arguments);
}

uint64_t getLongField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jlong>(*o, field->offset());
}

jlong JNICALL GetLongField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return run(t, getLongField, arguments);
}

uint64_t getFloatField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return floatToBits(fieldAtOffset<jfloat>(*o, field->offset()));
}

jfloat JNICALL GetFloatField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return bitsToFloat(run(t, getFloatField, arguments));
}

uint64_t getDoubleField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return doubleToBits(fieldAtOffset<jdouble>(*o, field->offset()));
}

jdouble JNICALL GetDoubleField(Thread* t, jobject o, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field};

  return bitsToDouble(run(t, getDoubleField, arguments));
}

uint64_t setObjectField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jobject v = reinterpret_cast<jobject>(arguments[2]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setField(t, *o, field->offset(), (v ? *v : 0));

  return 1;
}

void JNICALL SetObjectField(Thread* t, jobject o, jfieldID field, jobject v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), field, reinterpret_cast<uintptr_t>(v)};

  run(t, setObjectField, arguments);
}

uint64_t setBooleanField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jboolean v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jboolean>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetBooleanField(Thread* t, jobject o, jfieldID field, jboolean v)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field, v};

  run(t, setBooleanField, arguments);
}

uint64_t setByteField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jbyte v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jbyte>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetByteField(Thread* t, jobject o, jfieldID field, jbyte v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), field, static_cast<uintptr_t>(v)};

  run(t, setByteField, arguments);
}

uint64_t setCharField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jchar v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jchar>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetCharField(Thread* t, jobject o, jfieldID field, jchar v)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(o), field, v};

  run(t, setCharField, arguments);
}

uint64_t setShortField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jshort v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jshort>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetShortField(Thread* t, jobject o, jfieldID field, jshort v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), field, static_cast<uintptr_t>(v)};

  run(t, setShortField, arguments);
}

uint64_t setIntField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jint v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jint>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetIntField(Thread* t, jobject o, jfieldID field, jint v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), field, static_cast<uintptr_t>(v)};

  run(t, setIntField, arguments);
}

uint64_t setLongField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jlong v;
  memcpy(&v, arguments + 2, sizeof(jlong));

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jlong>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetLongField(Thread* t, jobject o, jfieldID field, jlong v)
{
  uintptr_t arguments[2 + (sizeof(jlong) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(o);
  arguments[1] = field;
  memcpy(arguments + 2, &v, sizeof(jlong));

  run(t, setLongField, arguments);
}

uint64_t setFloatField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jfloat v = bitsToFloat(arguments[2]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jfloat>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetFloatField(Thread* t, jobject o, jfieldID field, jfloat v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(o), field, floatToBits(v)};

  run(t, setFloatField, arguments);
}

uint64_t setDoubleField(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  GcField* field = getField(t, arguments[1]);
  jdouble v;
  memcpy(&v, arguments + 2, sizeof(jdouble));

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jdouble>(*o, field->offset()) = v;

  return 1;
}

void JNICALL SetDoubleField(Thread* t, jobject o, jfieldID field, jdouble v)
{
  uintptr_t arguments[2 + (sizeof(jdouble) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(o);
  arguments[1] = field;
  memcpy(arguments + 2, &v, sizeof(jdouble));

  run(t, setDoubleField, arguments);
}

GcField* getStaticField(Thread* t, jfieldID f)
{
  assertT(t, f);

  GcField* field = cast<GcField>(t, roots(t)->jNIFieldTable()->body()[f - 1]);

  assertT(t, field->flags() & ACC_STATIC);

  return field;
}

uint64_t getStaticObjectField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return reinterpret_cast<uintptr_t>(makeLocalReference(
      t, fieldAtOffset<object>(c->vmClass()->staticTable(), field->offset())));
}

jobject JNICALL GetStaticObjectField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return reinterpret_cast<jobject>(run(t, getStaticObjectField, arguments));
}

uint64_t getStaticBooleanField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jboolean>(c->vmClass()->staticTable(), field->offset());
}

jboolean JNICALL GetStaticBooleanField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticBooleanField, arguments);
}

uint64_t getStaticByteField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jbyte>(c->vmClass()->staticTable(), field->offset());
}

jbyte JNICALL GetStaticByteField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticByteField, arguments);
}

uint64_t getStaticCharField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jchar>(c->vmClass()->staticTable(), field->offset());
}

jchar JNICALL GetStaticCharField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticCharField, arguments);
}

uint64_t getStaticShortField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jshort>(c->vmClass()->staticTable(), field->offset());
}

jshort JNICALL GetStaticShortField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticShortField, arguments);
}

uint64_t getStaticIntField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jint>(c->vmClass()->staticTable(), field->offset());
}

jint JNICALL GetStaticIntField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticIntField, arguments);
}

uint64_t getStaticLongField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return fieldAtOffset<jlong>(c->vmClass()->staticTable(), field->offset());
}

jlong JNICALL GetStaticLongField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return run(t, getStaticLongField, arguments);
}

uint64_t getStaticFloatField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return floatToBits(
      fieldAtOffset<jfloat>(c->vmClass()->staticTable(), field->offset()));
}

jfloat JNICALL GetStaticFloatField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return bitsToFloat(run(t, getStaticFloatField, arguments));
}

uint64_t getStaticDoubleField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_READ(t, field);

  return doubleToBits(
      fieldAtOffset<jdouble>(c->vmClass()->staticTable(), field->offset()));
}

jdouble JNICALL GetStaticDoubleField(Thread* t, jclass c, jfieldID field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field};

  return bitsToDouble(run(t, getStaticDoubleField, arguments));
}

uint64_t setStaticObjectField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jobject v = reinterpret_cast<jobject>(arguments[2]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setField(t, c->vmClass()->staticTable(), field->offset(), (v ? *v : 0));

  return 1;
}

void JNICALL
    SetStaticObjectField(Thread* t, jclass c, jfieldID field, jobject v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), field, reinterpret_cast<uintptr_t>(v)};

  run(t, setStaticObjectField, arguments);
}

uint64_t setStaticBooleanField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jboolean v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jboolean>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL
    SetStaticBooleanField(Thread* t, jclass c, jfieldID field, jboolean v)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field, v};

  run(t, setStaticBooleanField, arguments);
}

uint64_t setStaticByteField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jbyte v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jbyte>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticByteField(Thread* t, jclass c, jfieldID field, jbyte v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), field, static_cast<uintptr_t>(v)};

  run(t, setStaticByteField, arguments);
}

uint64_t setStaticCharField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jchar v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jchar>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticCharField(Thread* t, jclass c, jfieldID field, jchar v)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c), field, v};

  run(t, setStaticCharField, arguments);
}

uint64_t setStaticShortField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jshort v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jshort>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticShortField(Thread* t, jclass c, jfieldID field, jshort v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), field, static_cast<uintptr_t>(v)};

  run(t, setStaticShortField, arguments);
}

uint64_t setStaticIntField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jint v = arguments[2];

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jint>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticIntField(Thread* t, jclass c, jfieldID field, jint v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), field, static_cast<uintptr_t>(v)};

  run(t, setStaticIntField, arguments);
}

uint64_t setStaticLongField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jlong v;
  memcpy(&v, arguments + 2, sizeof(jlong));

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jlong>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticLongField(Thread* t, jclass c, jfieldID field, jlong v)
{
  uintptr_t arguments[2 + (sizeof(jlong) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(c);
  arguments[1] = field;
  memcpy(arguments + 2, &v, sizeof(jlong));

  run(t, setStaticLongField, arguments);
}

uint64_t setStaticFloatField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jfloat v = bitsToFloat(arguments[2]);

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jfloat>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL SetStaticFloatField(Thread* t, jclass c, jfieldID field, jfloat v)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(c), field, floatToBits(v)};

  run(t, setStaticFloatField, arguments);
}

uint64_t setStaticDoubleField(Thread* t, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, *reinterpret_cast<jobject>(arguments[0]));

  initClass(t, c->vmClass());

  GcField* field = getStaticField(t, arguments[1]);
  jdouble v;
  memcpy(&v, arguments + 2, sizeof(jdouble));

  PROTECT(t, field);
  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<jdouble>(c->vmClass()->staticTable(), field->offset()) = v;

  return 1;
}

void JNICALL
    SetStaticDoubleField(Thread* t, jclass c, jfieldID field, jdouble v)
{
  uintptr_t arguments[2 + (sizeof(jdouble) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(c);
  arguments[1] = field;
  memcpy(arguments + 2, &v, sizeof(jdouble));

  run(t, setStaticDoubleField, arguments);
}

jobject JNICALL newGlobalRef(Thread* t, jobject o, bool weak)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->referenceLock);

  if (o) {
    for (Reference* r = t->m->jniReferences; r; r = r->next) {
      if (r->target == *o and r->weak == weak) {
        acquire(t, r);

        return &(r->target);
      }
    }

    Reference* r = new (t->m->heap->allocate(sizeof(Reference)))
        Reference(*o, &(t->m->jniReferences), weak);

    acquire(t, r);

    return &(r->target);
  } else {
    return 0;
  }
}

jobject JNICALL NewGlobalRef(Thread* t, jobject o)
{
  return newGlobalRef(t, o, false);
}

void JNICALL DeleteGlobalRef(Thread* t, jobject r)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->referenceLock);

  if (r) {
    release(t, reinterpret_cast<Reference*>(r));
  }
}

jobject JNICALL NewWeakGlobalRef(Thread* t, jobject o)
{
  return newGlobalRef(t, o, true);
}

void JNICALL DeleteWeakGlobalRef(Thread* t, jobject r)
{
  DeleteGlobalRef(t, r);
}

jint JNICALL EnsureLocalCapacity(Thread*, jint)
{
  return 0;
}

jthrowable JNICALL ExceptionOccurred(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return reinterpret_cast<jthrowable>(makeLocalReference(t, t->exception));
}

void JNICALL ExceptionDescribe(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  return printTrace(t, t->exception);
}

void JNICALL ExceptionClear(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  t->exception = 0;
}

uint64_t newObjectArray(Thread* t, uintptr_t* arguments)
{
  jsize length = arguments[0];
  jclass class_ = reinterpret_cast<jclass>(arguments[1]);
  jobject init = reinterpret_cast<jobject>(arguments[2]);

  object a = makeObjectArray(t, (*class_)->vmClass(), length);
  object value = (init ? *init : 0);
  for (jsize i = 0; i < length; ++i) {
    reinterpret_cast<GcArray*>(a)->setBodyElement(t, i, value);
  }
  return reinterpret_cast<uint64_t>(makeLocalReference(t, a));
}

jobjectArray JNICALL
    NewObjectArray(Thread* t, jsize length, jclass class_, jobject init)
{
  uintptr_t arguments[] = {static_cast<uintptr_t>(length),
                           reinterpret_cast<uintptr_t>(class_),
                           reinterpret_cast<uintptr_t>(init)};

  return reinterpret_cast<jobjectArray>(run(t, newObjectArray, arguments));
}

jobject JNICALL
    GetObjectArrayElement(Thread* t, jobjectArray array, jsize index)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(
      t, objectArrayBody(t, reinterpret_cast<object>(*array), index));
}

void JNICALL SetObjectArrayElement(Thread* t,
                                   jobjectArray array,
                                   jsize index,
                                   jobject value)
{
  ENTER(t, Thread::ActiveState);

  setField(t,
           reinterpret_cast<object>(*array),
           ArrayBody + (index * BytesPerWord),
           (value ? *value : 0));
}

uint64_t newArray(Thread* t, uintptr_t* arguments)
{
  object (*constructor)(Thread*, unsigned)
      = reinterpret_cast<object (*)(Thread*, unsigned)>(arguments[0]);

  jsize length = arguments[1];

  return reinterpret_cast<uint64_t>(
      makeLocalReference(t, constructor(t, length)));
}

jbooleanArray JNICALL NewBooleanArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeBooleanArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jbooleanArray>(run(t, newArray, arguments));
}

object makeByteArray0(Thread* t, unsigned length)
{
  return makeByteArray(t, length);
}

jbyteArray JNICALL NewByteArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeByteArray0)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jbyteArray>(run(t, newArray, arguments));
}

jcharArray JNICALL NewCharArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeCharArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jcharArray>(run(t, newArray, arguments));
}

jshortArray JNICALL NewShortArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeShortArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jshortArray>(run(t, newArray, arguments));
}

jintArray JNICALL NewIntArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeIntArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jintArray>(run(t, newArray, arguments));
}

jlongArray JNICALL NewLongArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeLongArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jlongArray>(run(t, newArray, arguments));
}

jfloatArray JNICALL NewFloatArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeFloatArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jfloatArray>(run(t, newArray, arguments));
}

jdoubleArray JNICALL NewDoubleArray(Thread* t, jsize length)
{
  uintptr_t arguments[]
      = {reinterpret_cast<uintptr_t>(voidPointer(makeDoubleArray)),
         static_cast<uintptr_t>(length)};

  return reinterpret_cast<jdoubleArray>(run(t, newArray, arguments));
}

jboolean* JNICALL
    GetBooleanArrayElements(Thread* t, jbooleanArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jboolean);
  jboolean* p = static_cast<jboolean*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jbyte);
  jbyte* p = static_cast<jbyte*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jchar);
  jchar* p = static_cast<jchar*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jshort);
  jshort* p = static_cast<jshort*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

jint* JNICALL GetIntArrayElements(Thread* t, jintArray array, jboolean* isCopy)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jint);
  jint* p = static_cast<jint*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jlong);
  jlong* p = static_cast<jlong*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jfloat);
  jfloat* p = static_cast<jfloat*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
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

  unsigned size = (*array)->length() * sizeof(jdouble);
  jdouble* p = static_cast<jdouble*>(t->m->heap->allocate(size));
  if (size) {
    memcpy(p, (*array)->body().begin(), size);
  }

  if (isCopy) {
    *isCopy = true;
  }

  return p;
}

void JNICALL ReleaseBooleanArrayElements(Thread* t,
                                         jbooleanArray array,
                                         jboolean* p,
                                         jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jboolean);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
    ReleaseByteArrayElements(Thread* t, jbyteArray array, jbyte* p, jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jbyte);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
    ReleaseCharArrayElements(Thread* t, jcharArray array, jchar* p, jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jchar);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL ReleaseShortArrayElements(Thread* t,
                                       jshortArray array,
                                       jshort* p,
                                       jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jshort);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
    ReleaseIntArrayElements(Thread* t, jintArray array, jint* p, jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jint);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL
    ReleaseLongArrayElements(Thread* t, jlongArray array, jlong* p, jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jlong);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL ReleaseFloatArrayElements(Thread* t,
                                       jfloatArray array,
                                       jfloat* p,
                                       jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jfloat);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL ReleaseDoubleArrayElements(Thread* t,
                                        jdoubleArray array,
                                        jdouble* p,
                                        jint mode)
{
  ENTER(t, Thread::ActiveState);

  unsigned size = (*array)->length() * sizeof(jdouble);

  if (mode == 0 or mode == AVIAN_JNI_COMMIT) {
    if (size) {
      memcpy((*array)->body().begin(), p, size);
    }
  }

  if (mode == 0 or mode == AVIAN_JNI_ABORT) {
    t->m->heap->free(p, size);
  }
}

void JNICALL GetBooleanArrayRegion(Thread* t,
                                   jbooleanArray array,
                                   jint offset,
                                   jint length,
                                   jboolean* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jboolean));
  }
}

void JNICALL GetByteArrayRegion(Thread* t,
                                jbyteArray array,
                                jint offset,
                                jint length,
                                jbyte* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jbyte));
  }
}

void JNICALL GetCharArrayRegion(Thread* t,
                                jcharArray array,
                                jint offset,
                                jint length,
                                jchar* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jchar));
  }
}

void JNICALL GetShortArrayRegion(Thread* t,
                                 jshortArray array,
                                 jint offset,
                                 jint length,
                                 jshort* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jshort));
  }
}

void JNICALL GetIntArrayRegion(Thread* t,
                               jintArray array,
                               jint offset,
                               jint length,
                               jint* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jint));
  }
}

void JNICALL GetLongArrayRegion(Thread* t,
                                jlongArray array,
                                jint offset,
                                jint length,
                                jlong* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jlong));
  }
}

void JNICALL GetFloatArrayRegion(Thread* t,
                                 jfloatArray array,
                                 jint offset,
                                 jint length,
                                 jfloat* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jfloat));
  }
}

void JNICALL GetDoubleArrayRegion(Thread* t,
                                  jdoubleArray array,
                                  jint offset,
                                  jint length,
                                  jdouble* dst)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(dst, &(*array)->body()[offset], length * sizeof(jdouble));
  }
}

void JNICALL SetBooleanArrayRegion(Thread* t,
                                   jbooleanArray array,
                                   jint offset,
                                   jint length,
                                   const jboolean* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jboolean));
  }
}

void JNICALL SetByteArrayRegion(Thread* t,
                                jbyteArray array,
                                jint offset,
                                jint length,
                                const jbyte* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jbyte));
  }
}

void JNICALL SetCharArrayRegion(Thread* t,
                                jcharArray array,
                                jint offset,
                                jint length,
                                const jchar* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jchar));
  }
}

void JNICALL SetShortArrayRegion(Thread* t,
                                 jshortArray array,
                                 jint offset,
                                 jint length,
                                 const jshort* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jshort));
  }
}

void JNICALL SetIntArrayRegion(Thread* t,
                               jintArray array,
                               jint offset,
                               jint length,
                               const jint* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jint));
  }
}

void JNICALL SetLongArrayRegion(Thread* t,
                                jlongArray array,
                                jint offset,
                                jint length,
                                const jlong* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jlong));
  }
}

void JNICALL SetFloatArrayRegion(Thread* t,
                                 jfloatArray array,
                                 jint offset,
                                 jint length,
                                 const jfloat* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jfloat));
  }
}

void JNICALL SetDoubleArrayRegion(Thread* t,
                                  jdoubleArray array,
                                  jint offset,
                                  jint length,
                                  const jdouble* src)
{
  ENTER(t, Thread::ActiveState);

  if (length) {
    memcpy(&(*array)->body()[offset], src, length * sizeof(jdouble));
  }
}

void* JNICALL
    GetPrimitiveArrayCritical(Thread* t, jarray array, jboolean* isCopy)
{
  if (t->criticalLevel == 0) {
    enter(t, Thread::ActiveState);
  }

  ++t->criticalLevel;

  if (isCopy) {
    *isCopy = true;
  }

  expect(t, *array);

  return reinterpret_cast<uintptr_t*>(*array) + 2;
}

void JNICALL ReleasePrimitiveArrayCritical(Thread* t, jarray, void*, jint)
{
  if ((--t->criticalLevel) == 0) {
    enter(t, Thread::IdleState);
  }
}

uint64_t fromReflectedMethod(Thread* t, uintptr_t* arguments)
{
  jobject m = reinterpret_cast<jobject>(arguments[0]);

  return methodID(t, t->m->classpath->getVMMethod(t, *m));
}

jmethodID JNICALL FromReflectedMethod(Thread* t, jobject method)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(method)};

  return static_cast<jmethodID>(run(t, fromReflectedMethod, arguments));
}

uint64_t toReflectedMethod(Thread* t, uintptr_t* arguments)
{
  jmethodID m = arguments[1];
  jboolean isStatic = arguments[2];

  return reinterpret_cast<uintptr_t>(makeLocalReference(
      t,
      t->m->classpath->makeJMethod(
          t, isStatic ? getStaticMethod(t, m) : getMethod(t, m))));
}

jobject JNICALL
    ToReflectedMethod(Thread* t, jclass c, jmethodID method, jboolean isStatic)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           static_cast<uintptr_t>(method),
                           static_cast<uintptr_t>(isStatic)};

  return reinterpret_cast<jobject>(run(t, toReflectedMethod, arguments));
}

uint64_t fromReflectedField(Thread* t, uintptr_t* arguments)
{
  jobject f = reinterpret_cast<jobject>(arguments[0]);

  return fieldID(t, t->m->classpath->getVMField(t, cast<GcJfield>(t, *f)));
}

jfieldID JNICALL FromReflectedField(Thread* t, jobject field)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(field)};

  return static_cast<jfieldID>(run(t, fromReflectedField, arguments));
}

uint64_t toReflectedField(Thread* t, uintptr_t* arguments)
{
  jfieldID f = arguments[1];
  jboolean isStatic = arguments[2];

  return reinterpret_cast<uintptr_t>(makeLocalReference(
      t,
      t->m->classpath->makeJField(
          t, isStatic ? getStaticField(t, f) : getField(t, f))));
}

jobject JNICALL
    ToReflectedField(Thread* t, jclass c, jfieldID field, jboolean isStatic)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           static_cast<uintptr_t>(field),
                           static_cast<uintptr_t>(isStatic)};

  return reinterpret_cast<jobject>(run(t, toReflectedField, arguments));
}

uint64_t registerNatives(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  const JNINativeMethod* methods
      = reinterpret_cast<const JNINativeMethod*>(arguments[1]);
  jint methodCount = arguments[2];

  for (int i = 0; i < methodCount; ++i) {
    if (methods[i].function) {
      // Android's class library sometimes prepends a mysterious "!"
      // to the method signature, which we happily ignore:
      const char* sig = methods[i].signature;
      if (*sig == '!')
        ++sig;

      GcMethod* method
          = findMethodOrNull(t, (*c)->vmClass(), methods[i].name, sig);

      if (method == 0 or (method->flags() & ACC_NATIVE) == 0) {
        // The JNI spec says we must throw a NoSuchMethodError in this
        // case, but that would prevent using a code shrinker like
        // ProGuard effectively.  Instead, we just ignore it.

        if (false) {
          fprintf(stderr,
                  "not found: %s.%s%s\n",
                  (*c)->vmClass()->name()->body().begin(),
                  methods[i].name,
                  sig);
          abort(t);
        }
      } else {
        registerNative(t, method, methods[i].function);
      }
    }
  }

  return 1;
}

jint JNICALL RegisterNatives(Thread* t,
                             jclass c,
                             const JNINativeMethod* methods,
                             jint methodCount)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(c),
                           reinterpret_cast<uintptr_t>(methods),
                           static_cast<uintptr_t>(methodCount)};

  return run(t, registerNatives, arguments) ? 0 : -1;
}

jint JNICALL UnregisterNatives(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  unregisterNatives(t, (*c)->vmClass());

  return 0;
}

uint64_t monitorOp(Thread* t, uintptr_t* arguments)
{
  void (*op)(Thread*, object)
      = reinterpret_cast<void (*)(Thread*, object)>(arguments[0]);

  jobject o = reinterpret_cast<jobject>(arguments[1]);

  op(t, *o);

  return 1;
}

void acquire0(Thread* t, object o)
{
  return acquire(t, o);
}

jint JNICALL MonitorEnter(Thread* t, jobject o)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(voidPointer(acquire0)),
                           reinterpret_cast<uintptr_t>(o)};

  return run(t, monitorOp, arguments) ? 0 : -1;
}

void release0(Thread* t, object o)
{
  return release(t, o);
}

jint JNICALL MonitorExit(Thread* t, jobject o)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(voidPointer(release0)),
                           reinterpret_cast<uintptr_t>(o)};

  return run(t, monitorOp, arguments) ? 0 : -1;
}

jint JNICALL GetJavaVM(Thread* t, Machine** m)
{
  *m = t->m;
  return 0;
}

jboolean JNICALL IsSameObject(Thread* t, jobject a, jobject b)
{
  if (a and b) {
    ENTER(t, Thread::ActiveState);

    return *a == *b;
  } else {
    return a == b;
  }
}

uint64_t pushLocalFrame(Thread* t, uintptr_t* arguments)
{
  if (t->m->processor->pushLocalFrame(t, arguments[0])) {
    return 1;
  } else {
    throw_(t, roots(t)->outOfMemoryError());
  }
}

jint JNICALL PushLocalFrame(Thread* t, jint capacity)
{
  uintptr_t arguments[] = {static_cast<uintptr_t>(capacity)};

  return run(t, pushLocalFrame, arguments) ? 0 : -1;
}

uint64_t popLocalFrame(Thread* t, uintptr_t* arguments)
{
  uint64_t r;
  jobject presult = reinterpret_cast<jobject>(arguments[0]);
  if (presult != NULL) {
    object result = *presult;
    PROTECT(t, result);

    t->m->processor->popLocalFrame(t);

    r = reinterpret_cast<uint64_t>(makeLocalReference(t, result));
  } else {
    t->m->processor->popLocalFrame(t);
    r = 0;
  }

  return r;
}

jobject JNICALL PopLocalFrame(Thread* t, jobject result)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(result)};

  return reinterpret_cast<jobject>(run(t, popLocalFrame, arguments));
}

uint64_t newDirectByteBuffer(Thread* t, uintptr_t* arguments)
{
  jlong capacity;
  memcpy(&capacity, arguments + 1, sizeof(jlong));

  return reinterpret_cast<uintptr_t>(makeLocalReference(
      t,
      t->m->classpath->makeDirectByteBuffer(
          t, reinterpret_cast<void*>(arguments[0]), capacity)));
}

jobject JNICALL NewDirectByteBuffer(Thread* t, void* p, jlong capacity)
{
  uintptr_t arguments[1 + (sizeof(jlong) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(p);
  memcpy(arguments + 1, &capacity, sizeof(jlong));

  return reinterpret_cast<jobject>(run(t, newDirectByteBuffer, arguments));
}

uint64_t getDirectBufferAddress(Thread* t, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(t->m->classpath->getDirectBufferAddress(
      t, *reinterpret_cast<jobject>(arguments[0])));
}

void* JNICALL GetDirectBufferAddress(Thread* t, jobject b)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(b)};

  return reinterpret_cast<void*>(run(t, getDirectBufferAddress, arguments));
}

uint64_t getDirectBufferCapacity(Thread* t, uintptr_t* arguments)
{
  return t->m->classpath->getDirectBufferCapacity(
      t, *reinterpret_cast<jobject>(arguments[0]));
}

jlong JNICALL GetDirectBufferCapacity(Thread* t, jobject b)
{
  uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(b)};

  return run(t, getDirectBufferCapacity, arguments);
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

int parseSize(const char* s)
{
  unsigned length = strlen(s);
  RUNTIME_ARRAY(char, buffer, length + 1);

  if (length == 0)
    return 0;

  char suffix = s[length - 1];
  if (suffix== 'k' or suffix == 'K') {
    memcpy(RUNTIME_ARRAY_BODY(buffer), s, length - 1);
    RUNTIME_ARRAY_BODY(buffer)[length - 1] = 0;
    return atoi(RUNTIME_ARRAY_BODY(buffer)) * 1024;
  }

  if (suffix == 'm' or suffix == 'M') {
    memcpy(RUNTIME_ARRAY_BODY(buffer), s, length - 1);
    RUNTIME_ARRAY_BODY(buffer)[length - 1] = 0;
    return atoi(RUNTIME_ARRAY_BODY(buffer)) * 1024 * 1024;
  }

  if (suffix == 'g' or suffix == 'G') {
    memcpy(RUNTIME_ARRAY_BODY(buffer), s, length - 1);
    RUNTIME_ARRAY_BODY(buffer)[length - 1] = 0;
    return atoi(RUNTIME_ARRAY_BODY(buffer)) * 1024 * 1024 * 1024;
  }

  return atoi(s);
}

void append(char** p, const char* value, unsigned length, char tail)
{
  if (length) {
    memcpy(*p, value, length);
    *p += length;
    *((*p)++) = tail;
  }
}

uint64_t boot(Thread* t, uintptr_t*)
{
  GcThrowable* throwable = makeThrowable(t, GcNullPointerException::Type);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setNullPointerException(t, throwable);

  throwable = makeThrowable(t, GcArithmeticException::Type);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setArithmeticException(t, throwable);

  throwable = makeThrowable(t, GcArrayIndexOutOfBoundsException::Type);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setArrayIndexOutOfBoundsException(t, throwable);

  throwable = makeThrowable(t, GcOutOfMemoryError::Type);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setOutOfMemoryError(t, throwable);

  throwable = makeThrowable(t, GcThrowable::Type);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setShutdownInProgress(t, throwable);

  t->m->classpath->preBoot(t);

  t->javaThread = t->m->classpath->makeThread(t, 0);

  t->javaThread->peer() = reinterpret_cast<jlong>(t);

#ifndef SGX
  GcThread* jthread = t->m->classpath->makeThread(t, t);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setFinalizerThread(t, jthread);
  roots(t)->finalizerThread()->daemon() = true;
#endif

  t->m->classpath->boot(t);

  const char* port = findProperty(t, "avian.trace.port");
  if (port) {
    GcString* host = makeString(t, "0.0.0.0");
    PROTECT(t, host);

    GcMethod* method = resolveMethod(t,
                                     roots(t)->bootLoader(),
                                     "avian/Traces",
                                     "startTraceListener",
                                     "(Ljava/lang/String;I)V");

    t->m->processor->invoke(t, method, 0, host, atoi(port));
  }

  enter(t, Thread::IdleState);

  return 1;
}

}  // namespace local

}  // namespace

namespace vm {

void populateJNITables(JavaVMVTable* vmTable, JNIEnvVTable* envTable)
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
  envTable->NewDirectByteBuffer = local::NewDirectByteBuffer;
  envTable->GetDirectBufferAddress = local::GetDirectBufferAddress;
  envTable->GetDirectBufferCapacity = local::GetDirectBufferCapacity;
  envTable->NewLocalRef = local::NewLocalRef;
  envTable->DeleteLocalRef = local::DeleteLocalRef;
  envTable->GetObjectClass = local::GetObjectClass;
  envTable->GetSuperclass = local::GetSuperclass;
  envTable->IsInstanceOf = local::IsInstanceOf;
  envTable->IsAssignableFrom = local::IsAssignableFrom;
  envTable->GetFieldID = local::GetFieldID;
  envTable->GetMethodID = local::GetMethodID;
  envTable->GetStaticMethodID = local::GetStaticMethodID;
  envTable->NewObjectV = local::NewObjectV;
  envTable->NewObjectA = local::NewObjectA;
  envTable->NewObject = local::NewObject;
  envTable->CallObjectMethodV = local::CallObjectMethodV;
  envTable->CallObjectMethodA = local::CallObjectMethodA;
  envTable->CallObjectMethod = local::CallObjectMethod;
  envTable->CallBooleanMethodV = local::CallBooleanMethodV;
  envTable->CallBooleanMethodA = local::CallBooleanMethodA;
  envTable->CallBooleanMethod = local::CallBooleanMethod;
  envTable->CallByteMethodV = local::CallByteMethodV;
  envTable->CallByteMethodA = local::CallByteMethodA;
  envTable->CallByteMethod = local::CallByteMethod;
  envTable->CallCharMethodV = local::CallCharMethodV;
  envTable->CallCharMethodA = local::CallCharMethodA;
  envTable->CallCharMethod = local::CallCharMethod;
  envTable->CallShortMethodV = local::CallShortMethodV;
  envTable->CallShortMethodA = local::CallShortMethodA;
  envTable->CallShortMethod = local::CallShortMethod;
  envTable->CallIntMethodV = local::CallIntMethodV;
  envTable->CallIntMethodA = local::CallIntMethodA;
  envTable->CallIntMethod = local::CallIntMethod;
  envTable->CallLongMethodV = local::CallLongMethodV;
  envTable->CallLongMethodA = local::CallLongMethodA;
  envTable->CallLongMethod = local::CallLongMethod;
  envTable->CallFloatMethodV = local::CallFloatMethodV;
  envTable->CallFloatMethodA = local::CallFloatMethodA;
  envTable->CallFloatMethod = local::CallFloatMethod;
  envTable->CallDoubleMethodV = local::CallDoubleMethodV;
  envTable->CallDoubleMethodA = local::CallDoubleMethodA;
  envTable->CallDoubleMethod = local::CallDoubleMethod;
  envTable->CallVoidMethodV = local::CallVoidMethodV;
  envTable->CallVoidMethodA = local::CallVoidMethodA;
  envTable->CallVoidMethod = local::CallVoidMethod;
  envTable->CallStaticObjectMethodV = local::CallStaticObjectMethodV;
  envTable->CallStaticObjectMethodA = local::CallStaticObjectMethodA;
  envTable->CallStaticObjectMethod = local::CallStaticObjectMethod;
  envTable->CallStaticBooleanMethodV = local::CallStaticBooleanMethodV;
  envTable->CallStaticBooleanMethodA = local::CallStaticBooleanMethodA;
  envTable->CallStaticBooleanMethod = local::CallStaticBooleanMethod;
  envTable->CallStaticByteMethodV = local::CallStaticByteMethodV;
  envTable->CallStaticByteMethodA = local::CallStaticByteMethodA;
  envTable->CallStaticByteMethod = local::CallStaticByteMethod;
  envTable->CallStaticCharMethodV = local::CallStaticCharMethodV;
  envTable->CallStaticCharMethodA = local::CallStaticCharMethodA;
  envTable->CallStaticCharMethod = local::CallStaticCharMethod;
  envTable->CallStaticShortMethodV = local::CallStaticShortMethodV;
  envTable->CallStaticShortMethodA = local::CallStaticShortMethodA;
  envTable->CallStaticShortMethod = local::CallStaticShortMethod;
  envTable->CallStaticIntMethodV = local::CallStaticIntMethodV;
  envTable->CallStaticIntMethodA = local::CallStaticIntMethodA;
  envTable->CallStaticIntMethod = local::CallStaticIntMethod;
  envTable->CallStaticLongMethodV = local::CallStaticLongMethodV;
  envTable->CallStaticLongMethodA = local::CallStaticLongMethodA;
  envTable->CallStaticLongMethod = local::CallStaticLongMethod;
  envTable->CallStaticFloatMethodV = local::CallStaticFloatMethodV;
  envTable->CallStaticFloatMethodA = local::CallStaticFloatMethodA;
  envTable->CallStaticFloatMethod = local::CallStaticFloatMethod;
  envTable->CallStaticDoubleMethodV = local::CallStaticDoubleMethodV;
  envTable->CallStaticDoubleMethodA = local::CallStaticDoubleMethodA;
  envTable->CallStaticDoubleMethod = local::CallStaticDoubleMethod;
  envTable->CallStaticVoidMethodV = local::CallStaticVoidMethodV;
  envTable->CallStaticVoidMethodA = local::CallStaticVoidMethodA;
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
  envTable->NewWeakGlobalRef = local::NewWeakGlobalRef;
  envTable->DeleteGlobalRef = local::DeleteGlobalRef;
  envTable->DeleteWeakGlobalRef = local::DeleteWeakGlobalRef;
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
  envTable->PushLocalFrame = local::PushLocalFrame;
  envTable->PopLocalFrame = local::PopLocalFrame;
  envTable->FromReflectedMethod = local::FromReflectedMethod;
  envTable->ToReflectedMethod = local::ToReflectedMethod;
  envTable->FromReflectedField = local::FromReflectedField;
  envTable->ToReflectedField = local::ToReflectedField;
}

}  // namespace vm

extern "C" AVIAN_EXPORT jint JNICALL JNI_GetDefaultJavaVMInitArgs(void*)
{
  return 0;
}

extern "C" AVIAN_EXPORT jint JNICALL
    JNI_GetCreatedJavaVMs(Machine**, jsize, jsize*)
{
  // todo
  return -1;
}

extern "C" AVIAN_EXPORT jint JNICALL
    JNI_CreateJavaVM(Machine** m, Thread** t, void* args)
{
  local::JavaVMInitArgs* a = static_cast<local::JavaVMInitArgs*>(args);

  unsigned heapLimit = 0;
  unsigned stackLimit = 0;
  const char* bootLibraries = 0;
  const char* classpath = 0;
  const char* javaHome = AVIAN_JAVA_HOME;
  bool reentrant = false;
  const char* embedPrefix = AVIAN_EMBED_PREFIX;
  const char* bootClasspathPrepend = "";
  const char* bootClasspath = 0;
  const char* bootClasspathAppend = "";
  const char* crashDumpDirectory = 0;

  unsigned propertyCount = 0;

  for (int i = 0; i < a->nOptions; ++i) {
    if (strncmp(a->options[i].optionString, "-X", 2) == 0) {
      const char* p = a->options[i].optionString + 2;
      if (strncmp(p, "mx", 2) == 0) {
        heapLimit = local::parseSize(p + 2);
      } else if (strncmp(p, "ss", 2) == 0) {
        stackLimit = local::parseSize(p + 2);
      } else if (strncmp(p,
                         BOOTCLASSPATH_PREPEND_OPTION ":",
                         sizeof(BOOTCLASSPATH_PREPEND_OPTION)) == 0) {
        bootClasspathPrepend = p + sizeof(BOOTCLASSPATH_PREPEND_OPTION);
      } else if (strncmp(
                     p, BOOTCLASSPATH_OPTION ":", sizeof(BOOTCLASSPATH_OPTION))
                 == 0) {
        bootClasspath = p + sizeof(BOOTCLASSPATH_OPTION);
      } else if (strncmp(p,
                         BOOTCLASSPATH_APPEND_OPTION ":",
                         sizeof(BOOTCLASSPATH_APPEND_OPTION)) == 0) {
        bootClasspathAppend = p + sizeof(BOOTCLASSPATH_APPEND_OPTION);
      }
    } else if (strncmp(a->options[i].optionString, "-D", 2) == 0) {
      const char* p = a->options[i].optionString + 2;
      if (strncmp(p, BOOTSTRAP_PROPERTY "=", sizeof(BOOTSTRAP_PROPERTY)) == 0) {
        bootLibraries = p + sizeof(BOOTSTRAP_PROPERTY);
      } else if (strncmp(p,
                         JAVA_COMMAND_PROPERTY "=",
                         sizeof(JAVA_COMMAND_PROPERTY)) == 0
                 or strncmp(p,
                            JAVA_LAUNCHER_PROPERTY "=",
                            sizeof(JAVA_LAUNCHER_PROPERTY)) == 0) {
        // this means we're being invoked via the javac or java
        // command, so the bootstrap library should be e.g. libjvm.so
        bootLibraries = SO_PREFIX "jvm" SO_SUFFIX;
      } else if (strncmp(p, CRASHDIR_PROPERTY "=", sizeof(CRASHDIR_PROPERTY))
                 == 0) {
        crashDumpDirectory = p + sizeof(CRASHDIR_PROPERTY);
      } else if (strncmp(p, CLASSPATH_PROPERTY "=", sizeof(CLASSPATH_PROPERTY))
                 == 0) {
        classpath = p + sizeof(CLASSPATH_PROPERTY);
      } else if (strncmp(p, JAVA_HOME_PROPERTY "=", sizeof(JAVA_HOME_PROPERTY))
                 == 0) {
        javaHome = p + sizeof(JAVA_HOME_PROPERTY);
      } else if (strncmp(p, REENTRANT_PROPERTY "=", sizeof(REENTRANT_PROPERTY))
                 == 0) {
        reentrant = strcmp(p + sizeof(REENTRANT_PROPERTY), "true") == 0;
      } else if (strncmp(p,
                         EMBED_PREFIX_PROPERTY "=",
                         sizeof(EMBED_PREFIX_PROPERTY)) == 0) {
        embedPrefix = p + sizeof(EMBED_PREFIX_PROPERTY);
      }

      ++propertyCount;
    }
  }

  if (heapLimit == 0)
    heapLimit = 128 * 1024 * 1024;

  if (stackLimit == 0)
    stackLimit = 128 * 1024;

  bool addClasspathProperty = classpath == 0;
  if (addClasspathProperty) {
    classpath = ".";
    ++propertyCount;
  }

  System* s = makeSystem(reentrant);
  Heap* h = makeHeap(s, heapLimit);
  Classpath* c = makeClasspath(s, h, javaHome, embedPrefix);

  if (bootClasspath == 0) {
    bootClasspath = c->bootClasspath();
  }

  unsigned bcppl = strlen(bootClasspathPrepend);
  unsigned bcpl = strlen(bootClasspath);
  unsigned bcpal = strlen(bootClasspathAppend);

  unsigned bootClasspathBufferSize = bcppl + bcpl + bcpal + 3;
  RUNTIME_ARRAY(char, bootClasspathBuffer, bootClasspathBufferSize);
  char* bootClasspathPointer = RUNTIME_ARRAY_BODY(bootClasspathBuffer);
  if (bootClasspathBufferSize > 3) {
    local::append(&bootClasspathPointer,
                  bootClasspathPrepend,
                  bcppl,
                  bcpl + bcpal ? PATH_SEPARATOR : 0);
    local::append(
        &bootClasspathPointer, bootClasspath, bcpl, bcpal ? PATH_SEPARATOR : 0);
    local::append(&bootClasspathPointer, bootClasspathAppend, bcpal, 0);
  } else {
    *RUNTIME_ARRAY_BODY(bootClasspathBuffer) = 0;
  }

  char* bootLibrary = bootLibraries ? strdup(bootLibraries) : 0;
  char* bootLibraryEnd = bootLibrary ? strchr(bootLibrary, PATH_SEPARATOR) : 0;
  if (bootLibraryEnd)
    *bootLibraryEnd = 0;

  Finder* bf
      = makeFinder(s, h, RUNTIME_ARRAY_BODY(bootClasspathBuffer), bootLibrary);
  Finder* af = makeFinder(s, h, classpath, bootLibrary);
  if (bootLibrary)
    free(bootLibrary);
  Processor* p = makeProcessor(s, h, crashDumpDirectory, true);

  // reserve space for avian.version and file.encoding:
  propertyCount += 2;

  const char** properties = static_cast<const char**>(
      h->allocate(sizeof(const char*) * propertyCount));

  const char** propertyPointer = properties;

  const char** arguments = static_cast<const char**>(
      h->allocate(sizeof(const char*) * a->nOptions));

  const char** argumentPointer = arguments;

  for (int i = 0; i < a->nOptions; ++i) {
    if (strncmp(a->options[i].optionString, "-D", 2) == 0) {
      *(propertyPointer++) = a->options[i].optionString + 2;
    }
    *(argumentPointer++) = a->options[i].optionString;
  }

  unsigned cpl = strlen(classpath);
  RUNTIME_ARRAY(char, classpathProperty, cpl + strlen(CLASSPATH_PROPERTY) + 2);
  if (addClasspathProperty) {
    char* p = RUNTIME_ARRAY_BODY(classpathProperty);
    local::append(&p, CLASSPATH_PROPERTY, strlen(CLASSPATH_PROPERTY), '=');
    local::append(&p, classpath, cpl, 0);
    *(propertyPointer++) = RUNTIME_ARRAY_BODY(classpathProperty);
  }

  *(propertyPointer++) = "avian.version=" AVIAN_VERSION;

  // todo: should this be derived from the OS locale?  Should it be
  // overrideable via JavaVMInitArgs?
  *(propertyPointer++) = "file.encoding=UTF-8";

  *m = new (h->allocate(sizeof(Machine))) Machine(s,
                                                  h,
                                                  bf,
                                                  af,
                                                  p,
                                                  c,
                                                  properties,
                                                  propertyCount,
                                                  arguments,
                                                  a->nOptions,
                                                  stackLimit);

  h->free(properties, sizeof(const char*) * propertyCount);

  *t = p->makeThread(*m, 0, 0);

  enter(*t, Thread::ActiveState);
  enter(*t, Thread::IdleState);

  return run(*t, local::boot, 0) ? 0 : -1;
}

extern "C" AVIAN_EXPORT jstring JNICALL JVM_GetTemporaryDirectory(JNIEnv* e UNUSED)
{
  // Unimplemented
  // This is used in newer builds of openjdk8, as a place to store statistics or something...
  abort();
}

extern "C" AVIAN_EXPORT jboolean JNICALL JVM_KnownToNotExist(JNIEnv* e UNUSED, jobject loader UNUSED, jstring classname UNUSED)
{
  // Unimplemented
  abort();
}

extern "C" AVIAN_EXPORT jintArray JNICALL JVM_GetResourceLookupCache(JNIEnv* e UNUSED, jobject loader UNUSED, jstring resourcename UNUSED)
{
  // Unimplemented
  abort();
}
