/* Copyright (c) 2008-2012, Avian Contributors

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

int64_t
search(Thread* t, object loader, object name,
       object (*op)(Thread*, object, object), bool replaceDots)
{
  if (LIKELY(name)) {
    PROTECT(t, loader);
    PROTECT(t, name);

    object n = makeByteArray(t, stringLength(t, name) + 1);
    char* s = reinterpret_cast<char*>(&byteArrayBody(t, n, 0));
    stringChars(t, name, s);
    
    if (replaceDots) {
      replace('.', '/', s);
    }

    return reinterpret_cast<int64_t>(op(t, loader, n));
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
  }
}

object
resolveSystemClassThrow(Thread* t, object loader, object spec)
{
  return resolveSystemClass
    (t, loader, spec, true, Machine::ClassNotFoundExceptionType);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Avian_avian_Classes_initialize
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  initClass(t, this_);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Classes_acquireClassLock
(Thread* t, object, uintptr_t*)
{
  acquire(t, t->m->classLock);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Classes_releaseClassLock
(Thread* t, object, uintptr_t*)
{
  release(t, t->m->classLock);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Classes_resolveVMClass
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  object spec = reinterpret_cast<object>(arguments[1]);

  return reinterpret_cast<int64_t>
    (resolveClass(t, loader, spec, true, Machine::ClassNotFoundExceptionType));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Classes_defineVMClass
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  object b = reinterpret_cast<object>(arguments[1]);
  int offset = arguments[2];
  int length = arguments[3];

  uint8_t* buffer = static_cast<uint8_t*>
    (t->m->heap->allocate(length));
  
  THREAD_RESOURCE2(t, uint8_t*, buffer, int, length,
                   t->m->heap->free(buffer, length));

  memcpy(buffer, &byteArrayBody(t, b, offset), length);

  return reinterpret_cast<int64_t>(defineClass(t, loader, buffer, length));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findLoadedVMClass
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  object name = reinterpret_cast<object>(arguments[1]);

  return search(t, loader, name, findLoadedClass, true);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_vmClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>
    (jclassVmClass(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findVMClass
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  object name = reinterpret_cast<object>(arguments[1]);

  return search(t, loader, name, resolveSystemClassThrow, true);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_resourceURLPrefix
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  object name = reinterpret_cast<object>(arguments[1]);

  if (LIKELY(name)) {
    THREAD_RUNTIME_ARRAY(t, char, n, stringLength(t, name) + 1);
    stringChars(t, name, RUNTIME_ARRAY_BODY(n));

    const char* name = static_cast<Finder*>
      (systemClassLoaderFinder(t, loader))->urlPrefix(RUNTIME_ARRAY_BODY(n));

    return name ? reinterpret_cast<uintptr_t>(makeString(t, "%s", name)) : 0;
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_getClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>
    (getJClass(t, reinterpret_cast<object>(arguments[0])));
}

#ifdef AVIAN_HEAPDUMP

extern "C" JNIEXPORT void JNICALL
Avian_avian_Machine_dumpHeap
(Thread* t, object, uintptr_t* arguments)
{
  object outputFile = reinterpret_cast<object>(*arguments);

  unsigned length = stringLength(t, outputFile);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, outputFile, RUNTIME_ARRAY_BODY(n));
  FILE* out = vm::fopen(RUNTIME_ARRAY_BODY(n), "wb");
  if (out) {
    { ENTER(t, Thread::ExclusiveState);
      dumpHeap(t, out);
    }
    fclose(out);
  } else {
    throwNew(t, Machine::RuntimeExceptionType, "file not found: %s",
             RUNTIME_ARRAY_BODY(n));
  }
}

#endif//AVIAN_HEAPDUMP

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_exit
(Thread* t, object, uintptr_t* arguments)
{
  shutDown(t);

  t->m->system->exit(arguments[1]);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_getContentLength
(Thread* t, object, uintptr_t* arguments)
{
  object path = reinterpret_cast<object>(*arguments);

  if (LIKELY(path)) {
    THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_open
(Thread* t, object, uintptr_t* arguments)
{
  object path = reinterpret_cast<object>(*arguments);

  if (LIKELY(path)) {
    THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
    stringChars(t, path, RUNTIME_ARRAY_BODY(p));

    System::Region* r = t->m->bootFinder->find(RUNTIME_ARRAY_BODY(p));
    if (r == 0) {
      r = t->m->appFinder->find(RUNTIME_ARRAY_BODY(p));
    }

    return reinterpret_cast<int64_t>(r);
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_available
(Thread*, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  return static_cast<jint>(region->length()) - position;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI
(Thread*, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (position >= static_cast<jint>(region->length())) {
    return -1;
  } else {
    return region->start()[position];
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_read__JI_3BII
(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];
  object buffer = reinterpret_cast<object>(arguments[3]);
  int32_t offset = arguments[4];
  int32_t length = arguments[5];

  if (length == 0) return 0;
  
  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (length > static_cast<jint>(region->length()) - position) {
    length = static_cast<jint>(region->length()) - position;
  }
  if (length <= 0) {
    return -1;
  } else {
    memcpy(&byteArrayBody(t, buffer, offset), region->start() + position,
           length);
    return length;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_avianvmresource_Handler_00024ResourceInputStream_close
(Thread*, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  reinterpret_cast<System::Region*>(peer)->dispose();
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_callWithCurrentContinuation
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->callWithCurrentContinuation
    (t, reinterpret_cast<object>(*arguments));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_dynamicWind2
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->dynamicWind
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]),
     reinterpret_cast<object>(arguments[2]));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_00024Continuation_handleResult
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->feedResultToContinuation
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_00024Continuation_handleException
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->feedExceptionToContinuation
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]));

  abort(t);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Singleton_getObject
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>
    (singletonObject(t, reinterpret_cast<object>(arguments[0]), arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Singleton_getInt
(Thread* t, object, uintptr_t* arguments)
{
  return singletonValue
    (t, reinterpret_cast<object>(arguments[0]), arguments[1]);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Singleton_getLong
(Thread* t, object, uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, &singletonValue
         (t, reinterpret_cast<object>(arguments[0]), arguments[1]), 8);
  return v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_allocateMemory
(Thread* t, object, uintptr_t* arguments)
{
  int64_t size; memcpy(&size, arguments + 1, 8);
  void* p = malloc(size);
  if (p) {
    return reinterpret_cast<int64_t>(p);
  } else {
    throwNew(t, Machine::OutOfMemoryErrorType);
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_freeMemory
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  if (p) {
    free(reinterpret_cast<void*>(p));
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_setMemory
(Thread* t, object, uintptr_t* arguments)
{
  object base = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int64_t count; memcpy(&count, arguments + 4, 8);
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

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putByte__JB
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int8_t v = arguments[3];

  *reinterpret_cast<int8_t*>(p) = v;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putShort__JS
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int16_t v = arguments[3];

  *reinterpret_cast<int16_t*>(p) = v;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putChar__JC
(Thread* t, object method, uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putShort__JS(t, method, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putInt__JI
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int32_t v = arguments[3];

  *reinterpret_cast<int32_t*>(p) = v;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putFloat__JF
(Thread* t, object method, uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putInt__JI(t, method, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putLong__JJ
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int64_t v; memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<int64_t*>(p) = v;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putDouble__JD
(Thread* t, object method, uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putLong__JJ(t, method, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putAddress__JJ
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int64_t v; memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<intptr_t*>(p) = v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getByte__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int8_t*>(p);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getShort__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int16_t*>(p);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getChar__J
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getShort__J(t, method, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getInt__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int32_t*>(p);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getFloat__J
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getInt__J(t, method, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getLong__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int64_t*>(p);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getDouble__J
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getLong__J(t, method, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getAddress__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<intptr_t*>(p);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_copyMemory
(Thread* t, object, uintptr_t* arguments)
{
  object srcBase = reinterpret_cast<object>(arguments[1]);
  int64_t srcOffset; memcpy(&srcOffset, arguments + 2, 8);
  object dstBase = reinterpret_cast<object>(arguments[4]);
  int64_t dstOffset; memcpy(&dstOffset, arguments + 5, 8);
  int64_t count; memcpy(&count, arguments + 7, 8);

  PROTECT(t, srcBase);
  PROTECT(t, dstBase);

  ACQUIRE(t, t->m->referenceLock);

  void* src = srcBase
    ? &fieldAtOffset<uint8_t>(srcBase, srcOffset)
    : reinterpret_cast<uint8_t*>(srcOffset);

  void* dst = dstBase
    ? &fieldAtOffset<uint8_t>(dstBase, dstOffset)
    : reinterpret_cast<uint8_t*>(dstOffset);

  memcpy(dst, src, count);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_arrayBaseOffset
(Thread*, object, uintptr_t*)
{
  return ArrayBody;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_nio_FixedArrayByteBuffer_allocateFixed
(Thread* t, object, uintptr_t* arguments)
{
  int capacity = arguments[0];
  object address = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, address);

  object array = allocate3
    (t, t->m->heap, Machine::FixedAllocation, ArrayBody + capacity, false);

  setObjectClass(t, array, type(t, Machine::ByteArrayType));
  byteArrayLength(t, array) = capacity;

  longArrayBody(t, address, 0) = reinterpret_cast<intptr_t>(array) + ArrayBody;

  return reinterpret_cast<intptr_t>(array);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapInt
(Thread*, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uint32_t expect = arguments[4];
  uint32_t update = arguments[5];

  return atomicCompareAndSwap32
    (&fieldAtOffset<uint32_t>(target, offset), expect, update);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Classes_primitiveClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(primitiveClass(t, arguments[0]));
}
