/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/machine.h"
#include "avian/classpath-common.h"
#include "avian/process.h"

#include <avian/util/runtime-array.h>

using namespace vm;

namespace {

namespace local {

class MyClasspath : public Classpath {
 public:
  MyClasspath(Allocator* allocator) : allocator(allocator)
  {
  }

  virtual GcJclass* makeJclass(Thread* t, GcClass* class_)
  {
    return vm::makeJclass(t, class_);
  }

  virtual GcString* makeString(Thread* t,
                               object array,
                               int32_t offset,
                               int32_t length)
  {
    return vm::makeString(t, array, offset, length, 0);
  }

  virtual GcThread* makeThread(Thread* t, Thread* parent)
  {
    GcThreadGroup* group;
    if (parent) {
      group = parent->javaThread->group();
    } else {
      group = makeThreadGroup(t, 0, 0, 0);
    }

    const unsigned NewState = 0;
    const unsigned NormalPriority = 5;

    return vm::makeThread(t,
                          0,
                          0,
                          0,
                          0,
                          0,
                          NewState,
                          NormalPriority,
                          0,
                          0,
                          0,
                          roots(t)->appLoader(),
                          0,
                          0,
                          group,
                          0,
                          0);
  }

  virtual object makeJMethod(Thread* t, GcMethod* vmMethod)
  {
    PROTECT(t, vmMethod);

    GcJmethod* jmethod = makeJmethod(t, vmMethod, false);

    return vmMethod->name()->body()[0] == '<'
               ? (object)makeJconstructor(t, jmethod)
               : (object)jmethod;
  }

  virtual GcMethod* getVMMethod(Thread* t, object jmethod)
  {
    return objectClass(t, jmethod) == type(t, GcJmethod::Type)
               ? cast<GcJmethod>(t, jmethod)->vmMethod()
               : cast<GcJconstructor>(t, jmethod)->method()->vmMethod();
  }

  virtual object makeJField(Thread* t, GcField* vmField)
  {
    return makeJfield(t, vmField, false);
  }

  virtual GcField* getVMField(Thread* t UNUSED, GcJfield* jfield)
  {
    return jfield->vmField();
  }

  virtual void clearInterrupted(Thread*)
  {
    // ignore
  }

  virtual void runThread(Thread* t)
  {
    GcMethod* method = resolveMethod(t,
                                     roots(t)->bootLoader(),
                                     "java/lang/Thread",
                                     "run",
                                     "(Ljava/lang/Thread;)V");

    t->m->processor->invoke(t, method, 0, t->javaThread);
  }

  virtual void resolveNative(Thread* t, GcMethod* method)
  {
    vm::resolveNative(t, method);
  }

  virtual void interceptMethods(Thread*)
  {
    // ignore
  }

  virtual void preBoot(Thread*)
  {
    // ignore
  }

  virtual bool mayInitClasses()
  {
    return true;
  }

  virtual void boot(Thread*)
  {
    // ignore
  }

  virtual const char* bootClasspath()
  {
    return AVIAN_CLASSPATH;
  }

  virtual object makeDirectByteBuffer(Thread* t, void* p, jlong capacity)
  {
    GcClass* c
        = resolveClass(t, roots(t)->bootLoader(), "java/nio/DirectByteBuffer");
    PROTECT(t, c);

    object instance = makeNew(t, c);
    PROTECT(t, instance);

    GcMethod* constructor = resolveMethod(t, c, "<init>", "(JI)V");

    t->m->processor->invoke(t,
                            constructor,
                            instance,
                            reinterpret_cast<int64_t>(p),
                            static_cast<int32_t>(capacity));

    return instance;
  }

  virtual void* getDirectBufferAddress(Thread* t, object b)
  {
    PROTECT(t, b);

    GcField* field = resolveField(t, objectClass(t, b), "address", "J");

    return reinterpret_cast<void*>(fieldAtOffset<int64_t>(b, field->offset()));
  }

  virtual int64_t getDirectBufferCapacity(Thread* t, object b)
  {
    PROTECT(t, b);

    GcField* field = resolveField(t, objectClass(t, b), "capacity", "I");

    return fieldAtOffset<int32_t>(b, field->offset());
  }

  virtual bool canTailCall(Thread* t UNUSED,
                           GcMethod*,
                           GcByteArray* calleeClassName,
                           GcByteArray* calleeMethodName,
                           GcByteArray*)
  {
    // we can't tail call System.load[Library] or
    // Runtime.load[Library] due to their use of
    // ClassLoader.getCaller, which gets confused if we elide stack
    // frames.

    return (
        (strcmp("loadLibrary",
                reinterpret_cast<char*>(calleeMethodName->body().begin()))
         and strcmp("load",
                    reinterpret_cast<char*>(calleeMethodName->body().begin())))
        or (strcmp("java/lang/System",
                   reinterpret_cast<char*>(calleeClassName->body().begin()))
            and strcmp(
                    "java/lang/Runtime",
                    reinterpret_cast<char*>(calleeClassName->body().begin()))));
  }

  virtual GcClassLoader* libraryClassLoader(Thread* t, GcMethod* caller)
  {
    return (caller->class_() == type(t, Gc::ClassLoaderType)
            and t->libraryLoadStack)
               ? t->libraryLoadStack->classLoader
               : caller->class_()->loader();
  }

  virtual void shutDown(Thread*)
  {
    // ignore
  }

  virtual void dispose()
  {
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
};

void enumerateThreads(Thread* t,
                      Thread* x,
                      GcArray* array,
                      unsigned* index,
                      unsigned limit)
{
  if (*index < limit) {
    array->setBodyElement(t, *index, x->javaThread);
    ++(*index);

    if (x->peer)
      enumerateThreads(t, x->peer, array, index, limit);

    if (x->child)
      enumerateThreads(t, x->child, array, index, limit);
  }
}

}  // namespace local

}  // namespace

namespace vm {

Classpath* makeClasspath(System*,
                         Allocator* allocator,
                         const char*,
                         const char*)
{
  return new (allocator->allocate(sizeof(local::MyClasspath)))
      local::MyClasspath(allocator);
}

}  // namespace vm

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_io_ObjectInputStream_makeInstance(Thread* t,
                                                 object,
                                                 uintptr_t* arguments)
{
  GcClass* c = cast<GcClass>(t, reinterpret_cast<object>(arguments[0]));

  return reinterpret_cast<int64_t>(make(t, c));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_LegacyObjectInputStream_makeInstance(Thread* t,
                                                     object,
                                                     uintptr_t* arguments)
{
  return Avian_java_io_ObjectInputStream_makeInstance(t, NULL, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getPrimitive(Thread* t,
                                               object,
                                               uintptr_t* arguments)
{
  return getPrimitive(
      t, reinterpret_cast<object>(arguments[0]), arguments[1], arguments[2]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getObject(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(fieldAtOffset<object>(
      reinterpret_cast<object>(arguments[0]), arguments[1]));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_reflect_Field_setPrimitive(Thread* t,
                                               object,
                                               uintptr_t* arguments)
{
  int64_t value;
  memcpy(&value, arguments + 3, 8);

  setPrimitive(t,
               reinterpret_cast<object>(arguments[0]),
               arguments[1],
               arguments[2],
               value);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_reflect_Field_setObject(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  setField(t,
           reinterpret_cast<object>(arguments[0]),
           arguments[1],
           reinterpret_cast<object>(arguments[2]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Constructor_make(Thread* t,
                                             object,
                                             uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      make(t, cast<GcClass>(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_getCaller(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>(getCaller(t, 2));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_invoke(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  return invokeMethod(t,
                      cast<GcMethod>(t, reinterpret_cast<object>(arguments[0])),
                      reinterpret_cast<object>(arguments[1]),
                      reinterpret_cast<object>(arguments[2]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Array_getLength(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  object array = reinterpret_cast<object>(arguments[0]);

  if (LIKELY(array)) {
    unsigned elementSize = objectClass(t, array)->arrayElementSize();

    if (LIKELY(elementSize)) {
      return fieldAtOffset<uintptr_t>(array, BytesPerWord);
    } else {
      throwNew(t, GcIllegalArgumentException::Type);
    }
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Array_makeObjectArray(Thread* t,
                                                  object,
                                                  uintptr_t* arguments)
{
  GcJclass* elementType
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  int length = arguments[1];

  return reinterpret_cast<int64_t>(
      makeObjectArray(t, elementType->vmClass(), length));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Float_floatToRawIntBits(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  return static_cast<int32_t>(*arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Float_intBitsToFloat(Thread*, object, uintptr_t* arguments)
{
  return static_cast<int32_t>(*arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Double_doubleToRawLongBits(Thread*,
                                               object,
                                               uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  return v;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Double_longBitsToDouble(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  return v;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_intern(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  return reinterpret_cast<int64_t>(intern(t, this_));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_System_getVMProperties(Thread* t, object, uintptr_t*)
{
  object array
      = makeObjectArray(t, type(t, GcString::Type), t->m->propertyCount);
  PROTECT(t, array);

  for (unsigned i = 0; i < t->m->propertyCount; ++i) {
    GcString* s = makeString(t, "%s", t->m->properties[i]);
    reinterpret_cast<GcArray*>(array)->setBodyElement(t, i, s);
  }

  return reinterpret_cast<int64_t>(array);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_System_arraycopy(Thread* t, object, uintptr_t* arguments)
{
  arrayCopy(t,
            reinterpret_cast<object>(arguments[0]),
            arguments[1],
            reinterpret_cast<object>(arguments[2]),
            arguments[3],
            arguments[4]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_System_identityHashCode(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[0]);

  if (LIKELY(o)) {
    return objectHash(t, o);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_ClassLoader_getCaller(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>(getJClass(t, getCaller(t, 2)->class_()));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_ClassLoader_load(Thread* t, object, uintptr_t* arguments)
{
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[0]));

  Thread::LibraryLoadStack stack(
      t,
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))
          ->vmClass()
          ->loader());

  bool mapName = arguments[2];

  unsigned length = name->length(t);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  loadLibrary(t, "", RUNTIME_ARRAY_BODY(n), mapName, true);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Runtime_gc(Thread* t, object, uintptr_t*)
{
  collect(t, Heap::MajorCollection);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Runtime_addShutdownHook(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  object hook = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, hook);

  ACQUIRE(t, t->m->shutdownLock);

  GcPair* p = makePair(t, hook, roots(t)->shutdownHooks());
  // sequence point, for gc (don't recombine statements)
  roots(t)->setShutdownHooks(t, p);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Throwable_trace(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(getTrace(t, arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Throwable_resolveTrace(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  object trace = reinterpret_cast<object>(*arguments);
  PROTECT(t, trace);

  unsigned length = objectArrayLength(t, trace);
  GcClass* elementType = type(t, GcStackTraceElement::Type);
  object array = makeObjectArray(t, elementType, length);
  PROTECT(t, array);

  for (unsigned i = 0; i < length; ++i) {
    GcStackTraceElement* ste = makeStackTraceElement(
        t, cast<GcTraceElement>(t, objectArrayBody(t, trace, i)));
    reinterpret_cast<GcArray*>(array)->setBodyElement(t, i, ste);
  }

  return reinterpret_cast<int64_t>(array);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_currentThread(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>(t->javaThread);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_doStart(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      startThread(t, cast<GcThread>(t, reinterpret_cast<object>(*arguments))));
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Thread_interrupt(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);

  threadInterrupt(t, reinterpret_cast<Thread*>(peer)->javaThread);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_interrupted(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);

  return threadIsInterrupted(
      t, reinterpret_cast<Thread*>(peer)->javaThread, true);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_getStackTrace(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  int64_t peer;
  memcpy(&peer, arguments, 8);

  if (reinterpret_cast<Thread*>(peer) == t) {
    return reinterpret_cast<int64_t>(makeTrace(t));
  } else {
    return reinterpret_cast<int64_t>(
        t->m->processor->getStackTrace(t, reinterpret_cast<Thread*>(peer)));
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_activeCount(Thread* t, object, uintptr_t*)
{
  return t->m->liveCount;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_enumerate(Thread* t, object, uintptr_t* arguments)
{
  GcArray* array = cast<GcArray>(t, reinterpret_cast<object>(*arguments));

  ACQUIRE_RAW(t, t->m->stateLock);

  unsigned count = min(t->m->liveCount,
                       objectArrayLength(t, reinterpret_cast<object>(array)));
  unsigned index = 0;
  local::enumerateThreads(t, t->m->rootThread, array, &index, count);
  return count;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Thread_holdsLock(Thread* t, object, uintptr_t* arguments)
{
  GcMonitor* m
      = objectMonitor(t, reinterpret_cast<object>(arguments[0]), false);

  return m and m->owner() == t;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Thread_yield(Thread* t, object, uintptr_t*)
{
  t->m->system->yield();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Atomic_getOffset(Thread* t, object, uintptr_t* arguments)
{
  return cast<GcJfield>(t, reinterpret_cast<object>(arguments[0]))
      ->vmField()
      ->offset();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_sun_misc_Unsafe_objectFieldOffset(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  return cast<GcJfield>(t, reinterpret_cast<object>(arguments[1]))
      ->vmField()
      ->offset();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Atomic_compareAndSwapObject(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[0]);
  int64_t offset;
  memcpy(&offset, arguments + 1, 8);
  uintptr_t expect = arguments[3];
  uintptr_t update = arguments[4];

  bool success = atomicCompareAndSwap(
      &fieldAtOffset<uintptr_t>(target, offset), expect, update);

  if (success) {
    mark(t, target, offset);
  }

  return success;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_isAssignableFrom(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  GcClass* this_ = cast<GcClass>(t, reinterpret_cast<object>(arguments[0]));
  GcClass* that = cast<GcClass>(t, reinterpret_cast<object>(arguments[1]));

  if (LIKELY(that)) {
    return vm::isAssignableFrom(t, this_, that);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_getVMClass(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>(
      objectClass(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_makeMethod(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(
      makeMethod(t,
                 cast<GcJclass>(t, reinterpret_cast<object>(arguments[0])),
                 arguments[1]));
}
