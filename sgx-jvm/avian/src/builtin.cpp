/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/machine.h"
#include "avian/constants.h"
#include "avian/processor.h"
#include "avian/util.h"

#include <avian/util/runtime-array.h>

using namespace vm;

namespace {

int64_t search(Thread* t,
               GcClassLoader* loader,
               GcString* name,
               GcClass* (*op)(Thread*, GcClassLoader*, GcByteArray*),
               bool replaceDots)
{
  if (LIKELY(name)) {
    PROTECT(t, loader);
    PROTECT(t, name);

    GcByteArray* n = makeByteArray(t, name->length(t) + 1);
    char* s = reinterpret_cast<char*>(n->body().begin());
    stringChars(t, name, s);

    if (replaceDots) {
      replace('.', '/', s);
    }

    return reinterpret_cast<int64_t>(op(t, loader, n));
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

GcClass* resolveSystemClassThrow(Thread* t,
                                 GcClassLoader* loader,
                                 GcByteArray* spec)
{
  return resolveSystemClass(
      t, loader, spec, true, GcClassNotFoundException::Type);
}

GcField* fieldForOffsetInClass(Thread* t, GcClass* c, unsigned offset)
{
  GcClass* super = c->super();
  if (super) {
    GcField* field = fieldForOffsetInClass(t, super, offset);
    if (field) {
      return field;
    }
  }

  object table = c->fieldTable();
  if (table) {
    for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
      GcField* field = cast<GcField>(t, objectArrayBody(t, table, i));
      if ((field->flags() & ACC_STATIC) == 0 and field->offset() == offset) {
        return field;
      }
    }
  }

  return 0;
}

GcField* fieldForOffset(Thread* t, object o, unsigned offset)
{
  GcClass* c = objectClass(t, o);
  if (c->vmFlags() & SingletonFlag) {
    GcSingleton* s = cast<GcSingleton>(t, o);

    // If the object is a Singleton, we assume it's the static table of a class -
    // which will always have the parent class as the first (0th) element.
    c = cast<GcClass>(t, singletonObject(t, s, 0));

    object table = c->fieldTable();
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        GcField* field = cast<GcField>(t, objectArrayBody(t, table, i));
        if ((field->flags() & ACC_STATIC) and field->offset() == offset) {
          return field;
        }
      }
    }
    abort(t);
  } else {
    GcField* field = fieldForOffsetInClass(t, c, offset);
    if (field) {
      return field;
    } else {
      abort(t);
    }
  }
}

}  // namespace

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_toVMClass(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>(
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_toVMMethod(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>(t->m->classpath->getVMMethod(
      t, cast<GcJmethod>(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Classes_initialize(Thread* t, object, uintptr_t* arguments)
{
  GcClass* this_ = cast<GcClass>(t, reinterpret_cast<object>(arguments[0]));

  initClass(t, this_);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Classes_acquireClassLock(Thread* t, object, uintptr_t*)
{
  acquire(t, t->m->classLock);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Classes_releaseClassLock(Thread* t, object, uintptr_t*)
{
  release(t, t->m->classLock);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_resolveVMClass(Thread* t, object, uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  GcByteArray* spec
      = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[1]));

  return reinterpret_cast<int64_t>(
      resolveClass(t, loader, spec, true, GcClassNotFoundException::Type));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_defineVMClass(Thread* t, object, uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  GcByteArray* b = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[1]));
  int offset = arguments[2];
  int length = arguments[3];

  uint8_t* buffer = static_cast<uint8_t*>(t->m->heap->allocate(length));

  THREAD_RESOURCE2(
      t, uint8_t*, buffer, int, length, t->m->heap->free(buffer, length));

  memcpy(buffer, &b->body()[offset], length);

  return reinterpret_cast<int64_t>(defineClass(t, loader, buffer, length));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_makeString(Thread* t, object, uintptr_t* arguments)
{
  GcByteArray* array
      = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[0]));
  int offset = arguments[1];
  int length = arguments[2];

  return reinterpret_cast<int64_t>(
      t->m->classpath->makeString(t, array, offset, length));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_SystemClassLoader_startBlacklisting0(Thread *t,
                                                     object,
                                                     uintptr_t* arguments)
{
  GcThread *jt = cast<GcThread>(t, reinterpret_cast<object>(arguments[0]));
  if (jt == NULL) {
    throwNew(t, GcNullPointerException::Type);
  }
  jt->setBlacklisting(t, 1);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_appLoader(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>(roots(t)->appLoader());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_findLoadedVMClass(Thread* t,
                                                    object,
                                                    uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  return search(t, loader, name, findLoadedClass, true);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_vmClass(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_findVMClass(Thread* t,
                                              object,
                                              uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  return search(t, loader, name, resolveSystemClassThrow, true);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_resourceURLPrefix(Thread* t,
                                                    object,
                                                    uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  if (LIKELY(name)) {
    THREAD_RUNTIME_ARRAY(t, char, n, name->length(t) + 1);
    stringChars(t, name, RUNTIME_ARRAY_BODY(n));

    const char* name
        = static_cast<Finder*>(loader->as<GcSystemClassLoader>(t)->finder())
              ->urlPrefix(RUNTIME_ARRAY_BODY(n));

    return name ? reinterpret_cast<uintptr_t>(makeString(t, "%s", name)) : 0;
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_00024ResourceEnumeration_nextResourceURLPrefix(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[1]));
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[2]));
  GcLongArray* finderElementPtrPtr
      = cast<GcLongArray>(t, reinterpret_cast<object>(arguments[3]));

  if (LIKELY(name) && LIKELY(finderElementPtrPtr)) {
    THREAD_RUNTIME_ARRAY(t, char, n, name->length(t) + 1);
    stringChars(t, name, RUNTIME_ARRAY_BODY(n));

    void*& finderElementPtr
        = reinterpret_cast<void*&>(finderElementPtrPtr->body()[0]);
    const char* name
        = static_cast<Finder*>(loader->as<GcSystemClassLoader>(t)->finder())
              ->nextUrlPrefix(RUNTIME_ARRAY_BODY(n), finderElementPtr);

    return name ? reinterpret_cast<uintptr_t>(makeString(t, "%s", name)) : 0;
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_getClass(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      getJClass(t, cast<GcClass>(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_getPackageSource(Thread* t,
                                                   object,
                                                   uintptr_t* arguments)
{
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, name);

  ACQUIRE(t, t->m->classLock);

  THREAD_RUNTIME_ARRAY(t, char, chars, name->length(t) + 2);
  stringChars(t, name, RUNTIME_ARRAY_BODY(chars));
  replace('.', '/', RUNTIME_ARRAY_BODY(chars));
  RUNTIME_ARRAY_BODY(chars)[name->length(t)] = '/';
  RUNTIME_ARRAY_BODY(chars)[name->length(t) + 1] = 0;

  GcByteArray* key = makeByteArray(t, RUNTIME_ARRAY_BODY(chars));

  GcByteArray* array = cast<GcByteArray>(
      t,
      hashMapFind(
          t, roots(t)->packageMap(), key, byteArrayHash, byteArrayEqual));

  if (array) {
    return reinterpret_cast<uintptr_t>(makeLocalReference(
        t, t->m->classpath->makeString(t, array, 0, array->length())));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Machine_dumpHeap(Thread* t, object, uintptr_t* arguments)
{
  GcString* outputFile
      = static_cast<GcString*>(reinterpret_cast<object>(*arguments));

  unsigned length = outputFile->length(t);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, outputFile, RUNTIME_ARRAY_BODY(n));
  FILE* out = vm::fopen(RUNTIME_ARRAY_BODY(n), "wb");
  if (out) {
    {
      ENTER(t, Thread::ExclusiveState);
      dumpHeap(t, out);
    }
    fclose(out);
  } else {
    throwNew(t,
             GcRuntimeException::Type,
             "file not found: %s",
             RUNTIME_ARRAY_BODY(n));
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Machine_tryNative(Thread* t, object, uintptr_t* arguments)
{
  int64_t function;
  memcpy(&function, arguments, 8);
  int64_t argument;
  memcpy(&argument, arguments + 2, 8);

  t->setFlag(Thread::TryNativeFlag);
  THREAD_RESOURCE0(t, t->clearFlag(Thread::TryNativeFlag));

  return reinterpret_cast<int64_t (*)(int64_t)>(function)(argument);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Runtime_exit(Thread* t, object, uintptr_t* arguments)
{
  shutDown(t);

  t->m->system->exit(arguments[1]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Runtime_freeMemory(Thread* t, object, uintptr_t*)
{
  return t->m->heap->remaining();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Runtime_totalMemory(Thread* t, object, uintptr_t*)
{
  return t->m->heap->limit();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Runtime_maxMemory(Thread* t, object, uintptr_t*)
{
  return t->m->heap->limit();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_getContentLength(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  GcString* path = cast<GcString>(t, reinterpret_cast<object>(*arguments));

  if (LIKELY(path)) {
    THREAD_RUNTIME_ARRAY(t, char, p, path->length(t) + 1);
    stringChars(t, path, RUNTIME_ARRAY_BODY(p));

    System::Region* r = t->m->bootFinder->find(RUNTIME_ARRAY_BODY(p));
    if (r == 0) {
      r = t->m->appFinder->find(RUNTIME_ARRAY_BODY(p));
    }

    if (r) {
      jint rSize = r->length();
      r->dispose();
      return rSize;
    }
  }
  return -1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_open(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  GcString* path = cast<GcString>(t, reinterpret_cast<object>(*arguments));

  if (LIKELY(path)) {
    THREAD_RUNTIME_ARRAY(t, char, p, path->length(t) + 1);
    stringChars(t, path, RUNTIME_ARRAY_BODY(p));

    System::Region* r = t->m->bootFinder->find(RUNTIME_ARRAY_BODY(p));
    if (r == 0) {
      r = t->m->appFinder->find(RUNTIME_ARRAY_BODY(p));
    }

    return reinterpret_cast<int64_t>(r);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_available(
        Thread*,
        object,
        uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  return static_cast<jint>(region->length()) - position;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI(
        Thread*,
        object,
        uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (position >= static_cast<jint>(region->length())) {
    return -1;
  } else {
    return region->start()[position];
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI_3BII(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];
  GcByteArray* buffer
      = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[3]));
  int32_t offset = arguments[4];
  int32_t length = arguments[5];

  if (length == 0)
    return 0;

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (length > static_cast<jint>(region->length()) - position) {
    length = static_cast<jint>(region->length()) - position;
  }
  if (length <= 0) {
    return -1;
  } else {
    memcpy(&buffer->body()[offset], region->start() + position, length);
    return length;
  }
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_avianvmresource_Handler_00024ResourceInputStream_close(
        Thread*,
        object,
        uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);
  reinterpret_cast<System::Region*>(peer)->dispose();
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Continuations_callWithCurrentContinuation(Thread* t,
                                                          object,
                                                          uintptr_t* arguments)
{
  t->m->processor->callWithCurrentContinuation(
      t, reinterpret_cast<object>(*arguments));

  abort(t);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Continuations_dynamicWind2(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  t->m->processor->dynamicWind(t,
                               reinterpret_cast<object>(arguments[0]),
                               reinterpret_cast<object>(arguments[1]),
                               reinterpret_cast<object>(arguments[2]));

  abort(t);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Continuations_00024Continuation_handleResult(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  t->m->processor->feedResultToContinuation(
      t,
      cast<GcContinuation>(t, reinterpret_cast<object>(arguments[0])),
      reinterpret_cast<object>(arguments[1]));

  abort(t);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_avian_Continuations_00024Continuation_handleException(
        Thread* t,
        object,
        uintptr_t* arguments)
{
  t->m->processor->feedExceptionToContinuation(
      t,
      cast<GcContinuation>(t, reinterpret_cast<object>(arguments[0])),
      cast<GcThrowable>(t, reinterpret_cast<object>(arguments[1])));

  abort(t);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Singleton_getObject(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(singletonObject(
      t,
      cast<GcSingleton>(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Singleton_getInt(Thread* t, object, uintptr_t* arguments)
{
  return singletonValue(
      t,
      cast<GcSingleton>(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Singleton_getLong(Thread* t, object, uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v,
         &singletonValue(
             t,
             cast<GcSingleton>(t, reinterpret_cast<object>(arguments[0])),
             arguments[1]),
         8);
  return v;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_allocateMemory(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  int64_t size;
  memcpy(&size, arguments + 1, 8);
  void* p = malloc(size);
  if (p) {
    return reinterpret_cast<int64_t>(p);
  } else {
    throwNew(t, GcOutOfMemoryError::Type);
  }
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_freeMemory(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  if (p) {
    free(reinterpret_cast<void*>(p));
  }
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_setMemory(Thread* t, object, uintptr_t* arguments)
{
  object base = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  int64_t count;
  memcpy(&count, arguments + 4, 8);
  int8_t value = arguments[6];

  PROTECT(t, base);

  ACQUIRE(t, t->m->referenceLock);

  if (base) {
    memset(&fieldAtOffset<int8_t>(base, offset), value, count);
  } else {
    memset(reinterpret_cast<int8_t*>(offset), value, count);
  }
}

// NB: The following primitive get/put methods are only used by the
// interpreter.  The JIT/AOT compiler implements them as intrinsics,
// so these versions will be ignored.

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putByte__JB(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  int8_t v = arguments[3];

  *reinterpret_cast<int8_t*>(p) = v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putShort__JS(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  int16_t v = arguments[3];

  *reinterpret_cast<int16_t*>(p) = v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putChar__JC(Thread* t,
                                      object method,
                                      uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putShort__JS(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putInt__JI(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  int32_t v = arguments[3];

  *reinterpret_cast<int32_t*>(p) = v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putFloat__JF(Thread* t,
                                       object method,
                                       uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putInt__JI(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putLong__JJ(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  int64_t v;
  memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<int64_t*>(p) = v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putDouble__JD(Thread* t,
                                        object method,
                                        uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putLong__JJ(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putAddress__JJ(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);
  int64_t v;
  memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<intptr_t*>(p) = v;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getByte__J(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int8_t*>(p);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getShort__J(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int16_t*>(p);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getChar__J(Thread* t,
                                     object method,
                                     uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getShort__J(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getInt__J(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int32_t*>(p);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getFloat__J(Thread* t,
                                      object method,
                                      uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getInt__J(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getLong__J(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int64_t*>(p);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getDouble__J(Thread* t,
                                       object method,
                                       uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getLong__J(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getAddress__J(Thread*, object, uintptr_t* arguments)
{
  int64_t p;
  memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<intptr_t*>(p);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_copyMemory(Thread* t, object, uintptr_t* arguments)
{
  object srcBase = reinterpret_cast<object>(arguments[1]);
  int64_t srcOffset;
  memcpy(&srcOffset, arguments + 2, 8);
  object dstBase = reinterpret_cast<object>(arguments[4]);
  int64_t dstOffset;
  memcpy(&dstOffset, arguments + 5, 8);
  int64_t count;
  memcpy(&count, arguments + 7, 8);

  PROTECT(t, srcBase);
  PROTECT(t, dstBase);

  ACQUIRE(t, t->m->referenceLock);

  void* src = srcBase ? &fieldAtOffset<uint8_t>(srcBase, srcOffset)
                      : reinterpret_cast<uint8_t*>(srcOffset);

  void* dst = dstBase ? &fieldAtOffset<uint8_t>(dstBase, dstOffset)
                      : reinterpret_cast<uint8_t*>(dstOffset);

  memcpy(dst, src, count);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_arrayBaseOffset(Thread*, object, uintptr_t*)
{
  return ArrayBody;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_arrayIndexScale(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))->vmClass();

  if (c == type(t, GcBooleanArray::Type) || c == type(t, GcByteArray::Type))
    return 1;
  else if (c == type(t, GcShortArray::Type) || c == type(t, GcCharArray::Type))
    return 2;
  else if (c == type(t, GcIntArray::Type) || c == type(t, GcFloatArray::Type))
    return 4;
  else if (c == type(t, GcLongArray::Type) || c == type(t, GcDoubleArray::Type))
    return 8;
  else
    return BytesPerWord;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_nio_FixedArrayByteBuffer_allocateFixed(Thread* t,
                                                      object,
                                                      uintptr_t* arguments)
{
  int capacity = arguments[0];
  GcLongArray* address
      = cast<GcLongArray>(t, reinterpret_cast<object>(arguments[1]));
  PROTECT(t, address);

  GcArray* array = reinterpret_cast<GcArray*>(allocate3(
      t, t->m->heap, Machine::FixedAllocation, ArrayBody + capacity, false));

  setObjectClass(
      t, reinterpret_cast<object>(array), type(t, GcByteArray::Type));
  array->length() = capacity;

  address->body()[0] = reinterpret_cast<intptr_t>(array) + ArrayBody;

  return reinterpret_cast<intptr_t>(array);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getObject(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  return fieldAtOffset<uintptr_t>(o, offset);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putObject(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  uintptr_t value = arguments[4];

  setField(t, o, offset, reinterpret_cast<object>(value));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putObjectVolatile(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  object value = reinterpret_cast<object>(arguments[4]);

  storeStoreMemoryBarrier();
  setField(t, o, offset, value);
  storeLoadMemoryBarrier();
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putOrderedObject(Thread* t,
                                           object method,
                                           uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putObjectVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getObjectVolatile(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  uintptr_t value = fieldAtOffset<uintptr_t>(o, offset);
  loadMemoryBarrier();
  return value;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_compareAndSwapObject(Thread* t,
                                               object,
                                               uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  uintptr_t expect = arguments[4];
  uintptr_t update = arguments[5];

  bool success = atomicCompareAndSwap(
      &fieldAtOffset<uintptr_t>(target, offset), expect, update);

  if (success) {
    mark(t, target, offset);
  }

  return success;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_compareAndSwapInt(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  uint32_t expect = arguments[4];
  uint32_t update = arguments[5];

  return atomicCompareAndSwap32(
      &fieldAtOffset<uint32_t>(target, offset), expect, update);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_compareAndSwapLong(Thread* t UNUSED,
                                             object,
                                             uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  uint64_t expect;
  memcpy(&expect, arguments + 4, 8);
  uint64_t update;
  memcpy(&update, arguments + 6, 8);

#ifdef AVIAN_HAS_CAS64
  return atomicCompareAndSwap64(
      &fieldAtOffset<uint64_t>(target, offset), expect, update);
#else
  PROTECT(t, target);
  ACQUIRE_FIELD_FOR_WRITE(t, fieldForOffset(t, target, offset));
  if (fieldAtOffset<uint64_t>(target, offset) == expect) {
    fieldAtOffset<uint64_t>(target, offset) = update;
    return true;
  } else {
    return false;
  }
#endif
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getLongVolatile(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  object lock;
  if (BytesPerWord < 8) {
    if (objectClass(t, o)->arrayDimensions()) {
      lock = objectClass(t, o);
    } else {
      lock = fieldForOffset(t, cast<GcSingleton>(t, o), offset);
    }

    PROTECT(t, o);
    PROTECT(t, lock);
    acquire(t, lock);
  }

  int64_t result = fieldAtOffset<int64_t>(o, offset);

  if (BytesPerWord < 8) {
    release(t, lock);
  } else {
    loadMemoryBarrier();
  }

  return result;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putLongVolatile(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  int64_t value;
  memcpy(&value, arguments + 4, 8);

  object lock;
  if (BytesPerWord < 8) {
    if (objectClass(t, o)->arrayDimensions()) {
      lock = objectClass(t, o);
    } else {
      lock = fieldForOffset(t, cast<GcSingleton>(t, o), offset);
    }

    PROTECT(t, o);
    PROTECT(t, lock);
    acquire(t, lock);
  } else {
    storeStoreMemoryBarrier();
  }

  fieldAtOffset<int64_t>(o, offset) = value;

  if (BytesPerWord < 8) {
    release(t, lock);
  } else {
    storeLoadMemoryBarrier();
  }
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putOrderedLong(Thread* t,
                                         object method,
                                         uintptr_t* arguments)
{
  // todo: we might be able to use weaker barriers here than
  // putLongVolatile does
  Avian_sun_misc_Unsafe_putLongVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_unpark(Thread* t, object, uintptr_t* arguments)
{
  GcThread* thread = cast<GcThread>(t, reinterpret_cast<object>(arguments[1]));

  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, thread)));
  thread->unparked() = true;
  monitorNotify(t, cast<GcMonitor>(t, interruptLock(t, thread)));
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, thread)));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_park(Thread* t, object, uintptr_t* arguments)
{
  bool absolute = arguments[1];
  int64_t time;
  memcpy(&time, arguments + 2, 8);

  int64_t then = t->m->system->now();

  if (absolute) {
    time -= then;
    if (time <= 0) {
      return;
    }
  } else if (time) {
    // if not absolute, interpret time as nanoseconds, but make sure
    // it doesn't become zero when we convert to milliseconds, since
    // zero is interpreted as infinity below
    time = (time / (1000 * 1000)) + 1;
  }

  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
  bool interrupted = false;
  while (time >= 0
         and (not(t->javaThread->unparked() or t->javaThread->interrupted()
                  or (interrupted = monitorWait(
                          t,
                          cast<GcMonitor>(t, interruptLock(t, t->javaThread)),
                          time))))) {
    int64_t now = t->m->system->now();
    time -= now - then;
    then = now;

    if (time == 0) {
      break;
    }
  }
  if (interrupted) {
    t->javaThread->interrupted() = true;
  }
  t->javaThread->unparked() = false;
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putIntVolatile(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  int32_t value = arguments[4];

  storeStoreMemoryBarrier();
  fieldAtOffset<int32_t>(o, offset) = value;
  storeLoadMemoryBarrier();
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putOrderedInt(Thread* t,
                                        object method,
                                        uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putIntVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getIntVolatile(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  int32_t result = fieldAtOffset<int32_t>(o, offset);
  loadMemoryBarrier();
  return result;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putByteVolatile(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  int8_t value = arguments[4];

  storeStoreMemoryBarrier();
  fieldAtOffset<int8_t>(o, offset) = value;
  storeLoadMemoryBarrier();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getByteVolatile(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  int8_t result = fieldAtOffset<int8_t>(o, offset);
  loadMemoryBarrier();
  return result;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putBooleanVolatile(Thread* t,
                                             object method,
                                             uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putByteVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getBooleanVolatile(Thread* t,
                                             object method,
                                             uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getByteVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putShortVolatile(Thread*,
                                           object,
                                           uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);
  int16_t value = arguments[4];

  storeStoreMemoryBarrier();
  fieldAtOffset<int16_t>(o, offset) = value;
  storeLoadMemoryBarrier();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getShortVolatile(Thread*,
                                           object,
                                           uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset;
  memcpy(&offset, arguments + 2, 8);

  int16_t result = fieldAtOffset<int16_t>(o, offset);
  loadMemoryBarrier();
  return result;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putCharVolatile(Thread* t,
                                          object method,
                                          uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putShortVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getCharVolatile(Thread* t,
                                          object method,
                                          uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getShortVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putFloatVolatile(Thread* t,
                                           object method,
                                           uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putIntVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getFloatVolatile(Thread* t,
                                           object method,
                                           uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getIntVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_putDoubleVolatile(Thread* t,
                                            object method,
                                            uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putLongVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_getDoubleVolatile(Thread* t,
                                            object method,
                                            uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getLongVolatile(t, method, arguments);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_sun_misc_Unsafe_throwException(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  vm::throw_(t, cast<GcThrowable>(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_primitiveClass(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(primitiveClass(t, arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getEnclosingMethod(Thread* t,
                                             object,
                                             uintptr_t* arguments)
{
  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass();
  PROTECT(t, c);

  GcClassAddendum* addendum = c->addendum();
  if (addendum) {
    PROTECT(t, addendum);

    GcByteArray* enclosingClass
        = cast<GcByteArray>(t, addendum->enclosingClass());

    if (enclosingClass) {
      GcClass* enclosing = resolveClass(t, c->loader(), enclosingClass);

      GcPair* enclosingMethod = cast<GcPair>(t, addendum->enclosingMethod());

      if (enclosingMethod) {
        return reinterpret_cast<uintptr_t>(t->m->classpath->makeJMethod(
            t,
            cast<GcMethod>(
                t,
                findMethodInClass(
                    t,
                    enclosing,
                    cast<GcByteArray>(t, enclosingMethod->first()),
                    cast<GcByteArray>(t, enclosingMethod->second())))));
      }
    }
  }
  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getEnclosingClass(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass();
  PROTECT(t, c);

  GcClassAddendum* addendum = c->addendum();
  if (addendum) {
    GcByteArray* enclosingClass
        = cast<GcByteArray>(t, addendum->enclosingClass());

    if (enclosingClass) {
      return reinterpret_cast<uintptr_t>(
          getJClass(t, resolveClass(t, c->loader(), enclosingClass)));
    }
  }
  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getEnclosingConstructor(Thread* t,
                                                  object method,
                                                  uintptr_t* arguments)
{
  return Avian_java_lang_Class_getEnclosingMethod(t, method, arguments);
}


extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_toString(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  unsigned hash = objectHash(t, this_);
  GcString* s = makeString(
      t, "%s@0x%x", objectClass(t, this_)->name()->body().begin(), hash);

  return reinterpret_cast<int64_t>(s);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_getVMClass(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      objectClass(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Object_wait(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  int64_t milliseconds;
  memcpy(&milliseconds, arguments + 1, 8);

  vm::wait(t, this_, milliseconds);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Object_notify(Thread* t, object, uintptr_t* arguments)
{
  notify(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Object_notifyAll(Thread* t, object, uintptr_t* arguments)
{
  notifyAll(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_hashCode(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_clone(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      clone(t, reinterpret_cast<object>(arguments[0])));
}
