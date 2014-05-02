/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

struct JavaVM;
struct _JNIEnv;

struct JniConstants {
  static void init(_JNIEnv* env);
};

extern "C" int JNI_OnLoad(JavaVM*, void*);

#define _POSIX_C_SOURCE 200112L
#undef _GNU_SOURCE
#include "avian/machine.h"
#include "avian/classpath-common.h"
#include "avian/process.h"
#include "avian/util.h"

#ifdef PLATFORM_WINDOWS
const char* getErrnoDescription(int err);		// This function is defined in mingw-extensions.cpp
#endif

using namespace vm;

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_avian_Classes_defineVMClass
(Thread*, object, uintptr_t*);

namespace {

namespace local {

void*
getDirectBufferAddress(Thread* t, object b)
{
  PROTECT(t, b);

  object field = resolveField
    (t, objectClass(t, b), "effectiveDirectAddress", "J");

  return reinterpret_cast<void*>
    (fieldAtOffset<int64_t>(b, fieldOffset(t, field)));
}

void JNICALL
loadLibrary(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  unsigned length = stringLength(t, name);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  /* org_conscrypt_NativeCrypto.o is linked statically, and in Avian build
  the package is named org.conscrypt.NativeCrypto. When Android code sees
  that name it thinks the library isn't linked as a part of Android, so it
  tries to load in dynamically, but there's actually no need to, so we
  just ignore this request. */
  if (strcmp(RUNTIME_ARRAY_BODY(n), "conscrypt_jni") != 0) {
    loadLibrary(t, "", RUNTIME_ARRAY_BODY(n), true, true);
  }
}

void JNICALL
finalizeAllEnqueued(Thread*, object, uintptr_t*)
{
  // ignore
}

int64_t JNICALL
appLoader(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::AppLoader));
}

int64_t JNICALL
defineClass(Thread* t, object method, uintptr_t* arguments)
{
  uintptr_t args[]
    = { arguments[0], arguments[2], arguments[3], arguments[4] };

  int64_t v = Avian_avian_Classes_defineVMClass(t, method, args);

  if (v) {
    return reinterpret_cast<uintptr_t>
      (getJClass(t, reinterpret_cast<object>(v)));
  } else {
    return 0;
  }  
}

int64_t JNICALL
mapData(Thread*, object, uintptr_t*);

void JNICALL
closeMemoryMappedFile(Thread*, object, uintptr_t*);

object
makeMethodOrConstructor(Thread* t, object c, unsigned index)
{
  PROTECT(t, c);

  object method = arrayBody
    (t, classMethodTable(t, jclassVmClass(t, c)), index);
  PROTECT(t, method);

  unsigned parameterCount;
  unsigned returnTypeSpec;
  object parameterTypes = resolveParameterJTypes
    (t, classLoader(t, methodClass(t, method)), methodSpec(t, method),
     &parameterCount, &returnTypeSpec);
  PROTECT(t, parameterTypes);

  object returnType = resolveJType
    (t, classLoader(t, methodClass(t, method)), reinterpret_cast<char*>
     (&byteArrayBody(t, methodSpec(t, method), returnTypeSpec)),
     byteArrayLength(t, methodSpec(t, method)) - 1 - returnTypeSpec);
  PROTECT(t, returnType);

  object exceptionTypes = resolveExceptionJTypes
    (t, classLoader(t, methodClass(t, method)), methodAddendum(t, method));

  if (byteArrayBody(t, methodName(t, method), 0) == '<') {
    return makeJconstructor
      (t, 0, c, parameterTypes, exceptionTypes, 0, 0, 0, 0, index);
  } else {
    PROTECT(t, exceptionTypes);
 
    object name = t->m->classpath->makeString
      (t, methodName(t, method), 0,
       byteArrayLength(t, methodName(t, method)) - 1);

    return makeJmethod
      (t, 0, index, c, name, parameterTypes, exceptionTypes, returnType, 0, 0,
       0, 0, 0);
  }
}

object
makeField(Thread* t, object c, unsigned index)
{
  PROTECT(t, c);

  object field = arrayBody
    (t, classFieldTable(t, jclassVmClass(t, c)), index);

  PROTECT(t, field);

  object type = getJClass
    (t, resolveClassBySpec
     (t, classLoader(t, fieldClass(t, field)),
      reinterpret_cast<char*>
      (&byteArrayBody(t, fieldSpec(t, field), 0)),
      byteArrayLength(t, fieldSpec(t, field)) - 1));
  PROTECT(t, type);
 
  object name = t->m->classpath->makeString
    (t, fieldName(t, field), 0,
     byteArrayLength(t, fieldName(t, field)) - 1);

  return makeJfield(t, 0, c, type, 0, 0, name, index);
}

void initVmThread(Thread* t, object thread, unsigned offset)
{
  PROTECT(t, thread);

  if (fieldAtOffset<object>(thread, offset) == 0) {
    object c = resolveClass
      (t, root(t, Machine::BootLoader), "java/lang/VMThread");
    PROTECT(t, c);

    object instance = makeNew(t, c);
    PROTECT(t, instance);

    object constructor = resolveMethod
      (t, c, "<init>", "(Ljava/lang/Thread;)V");

    t->m->processor->invoke(t, constructor, instance, thread);

    set(t, thread, offset, instance);
  }

  if (threadGroup(t, thread) == 0) {
    set(t, thread, ThreadGroup, threadGroup(t, t->javaThread));
    expect(t, threadGroup(t, thread));
  }
}

void initVmThread(Thread* t, object thread)
{
  initVmThread(
      t,
      thread,
      fieldOffset(
          t,
          resolveField(
              t, objectClass(t, thread), "vmThread", "Ljava/lang/VMThread;")));
}

object
translateStackTrace(Thread* t, object raw)
{
  PROTECT(t, raw);
  
  object array = makeObjectArray
    (t, resolveClass
     (t, root(t, Machine::BootLoader), "java/lang/StackTraceElement"),
     objectArrayLength(t, raw));
  PROTECT(t, array);

  for (unsigned i = 0; i < objectArrayLength(t, array); ++i) {
    object e = makeStackTraceElement(t, objectArrayBody(t, raw, i));

    set(t, array, ArrayBody + (i * BytesPerWord), e);
  }

  return array;
}

class MyClasspath : public Classpath {
 public:
  MyClasspath(Allocator* allocator)
      : allocator(allocator), tzdata(0), mayInitClasses_(false)
  { }

  virtual object
  makeJclass(Thread* t, object class_)
  {
    PROTECT(t, class_);

    object c = allocate(t, FixedSizeOfJclass, true);
    setObjectClass(t, c, type(t, Machine::JclassType));
    set(t, c, JclassVmClass, class_);

    return c;
  }

  virtual object
  makeString(Thread* t, object array, int32_t offset, int32_t length)
  {
    if (objectClass(t, array) == type(t, Machine::ByteArrayType)) {
      PROTECT(t, array);
      
      object charArray = makeCharArray(t, length);
      for (int i = 0; i < length; ++i) {
        expect(t, (byteArrayBody(t, array, offset + i) & 0x80) == 0);

        charArrayBody(t, charArray, i) = byteArrayBody(t, array, offset + i);
      }

      array = charArray;
    } else {
      expect(t, objectClass(t, array) == type(t, Machine::CharArrayType));
    }

    return vm::makeString(t, array, offset, length, 0);
  }

  virtual object
  makeThread(Thread* t, Thread* parent)
  {
    const unsigned NormalPriority = 5;

    object group = 0;
    PROTECT(t, group);
    if (parent) {
      group = threadGroup(t, parent->javaThread);
    } else {
      resolveSystemClass
        (t, root(t, Machine::BootLoader),
         className(t, type(t, Machine::ThreadGroupType)), false);

      group = makeNew(t, type(t, Machine::ThreadGroupType));

      object constructor = resolveMethod
        (t, type(t, Machine::ThreadGroupType), "<init>", "()V");

      t->m->processor->invoke(t, constructor, group);
    }

    resolveSystemClass
      (t, root(t, Machine::BootLoader),
       className(t, type(t, Machine::ThreadType)), false);
    
    object thread = makeNew(t, type(t, Machine::ThreadType));
    PROTECT(t, thread);

    object constructor = resolveMethod
      (t, type(t, Machine::ThreadType), "<init>",
       "(Ljava/lang/ThreadGroup;Ljava/lang/String;IZ)V");

    t->m->processor->invoke
      (t, constructor, thread, group, 0, NormalPriority, false);

    set(t, thread, ThreadContextClassLoader, root(t, Machine::AppLoader));

    initVmThread(t, thread);

    return thread;
  }

  virtual object
  makeJMethod(Thread* t, object vmMethod)
  {
    object table = classMethodTable(t, methodClass(t, vmMethod));
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      if (vmMethod == arrayBody(t, table, i)) {
        return makeMethodOrConstructor
          (t, getJClass(t, methodClass(t, vmMethod)), i);
      }
    }
    abort(t);
  }

  virtual object
  getVMMethod(Thread* t, object jmethod)
  {
    return objectClass(t, jmethod) == type(t, Machine::JmethodType)
      ? arrayBody
      (t, classMethodTable
       (t, jclassVmClass(t, jmethodDeclaringClass(t, jmethod))),
       jmethodSlot(t, jmethod))
      : arrayBody
      (t, classMethodTable
       (t, jclassVmClass(t, jconstructorDeclaringClass(t, jmethod))),
       jconstructorSlot(t, jmethod));
  }

  virtual object
  makeJField(Thread* t, object vmField)
  {
    object table = classFieldTable(t, fieldClass(t, vmField));
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      if (vmField == arrayBody(t, table, i)) {
        return makeField(t, getJClass(t, fieldClass(t, vmField)), i);
      }
    }
    abort(t);
  }

  virtual object
  getVMField(Thread* t, object jfield)
  {
    return arrayBody
      (t, classFieldTable
       (t, jclassVmClass(t, jfieldDeclaringClass(t, jfield))),
       jfieldSlot(t, jfield));
  }

  virtual void
  clearInterrupted(Thread*)
  {
    // ignore
  }

  virtual void
  runThread(Thread* t)
  {
    // force monitor creation so we don't get an OutOfMemory error
    // later when we try to acquire it:
    objectMonitor(t, t->javaThread, true);

    object field = resolveField(
        t, objectClass(t, t->javaThread), "vmThread", "Ljava/lang/VMThread;");

    unsigned offset = fieldOffset(t, field);

    THREAD_RESOURCE(t, unsigned, offset, {
      object vmt = fieldAtOffset<object>(t->javaThread, offset);
      if (vmt) {
        PROTECT(t, vmt);
        vm::acquire(t, vmt);
        fieldAtOffset<object>(t->javaThread, offset) = 0;
        vm::notifyAll(t, vmt);
        vm::release(t, vmt);
      }

      vm::acquire(t, t->javaThread);
      t->flags &= ~Thread::ActiveFlag;
      vm::notifyAll(t, t->javaThread);
      vm::release(t, t->javaThread);
    });

    initVmThread(t, t->javaThread, offset);

    object method = resolveMethod
      (t, root(t, Machine::BootLoader), "java/lang/Thread", "run", "()V");

    t->m->processor->invoke(t, method, t->javaThread);
  }

  virtual void
  resolveNative(Thread* t, object method)
  {
    vm::resolveNative(t, method);
  }

  void
  interceptMethods(Thread* t, bool updateRuntimeData)
  {
    { object c = resolveClass
        (t, root(t, Machine::BootLoader), "java/lang/Runtime", false);

      if (c) {
        PROTECT(t, c);

        intercept(t, c, "loadLibrary",
                  "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
                  voidPointer(loadLibrary), updateRuntimeData);
      }
    }

    { object c = resolveClass
        (t, root(t, Machine::BootLoader), "java/lang/ref/FinalizerReference",
         false);

      if (c) {
        PROTECT(t, c);

        intercept(t, c, "finalizeAllEnqueued", "()V",
                  voidPointer(finalizeAllEnqueued), updateRuntimeData);
      }
    }

    { object c = resolveClass
        (t, root(t, Machine::BootLoader), "java/lang/ClassLoader", false);

      if (c) {
        PROTECT(t, c);

        intercept(t, c, "createSystemClassLoader", "()Ljava/lang/ClassLoader;",
                  voidPointer(appLoader), updateRuntimeData);

        intercept(t, c, "defineClass",
                  "(Ljava/lang/String;[BII)Ljava/lang/Class;",
                  voidPointer(defineClass), updateRuntimeData);
      }
    }

    { object c = resolveClass
        (t, root(t, Machine::BootLoader), "libcore/util/ZoneInfoDB", false);

      if (c) {
        PROTECT(t, c);

        intercept(t, c, "mapData", "()Llibcore/io/MemoryMappedFile;",
                  voidPointer(mapData), updateRuntimeData);
      }
    }

    { object c = resolveClass
        (t, root(t, Machine::BootLoader), "libcore/io/MemoryMappedFile",
         false);

      if (c) {
        PROTECT(t, c);

        intercept(t, c, "close", "()V",  voidPointer(closeMemoryMappedFile),
                  updateRuntimeData);
      }
    }
  }

  virtual void
  interceptMethods(Thread* t)
  {
    interceptMethods(t, false);
  }

  virtual void
  preBoot(Thread* t)
  {
    // Android's System.initSystemProperties throws an NPE if
    // LD_LIBRARY_PATH is not set as of this writing:
#ifdef PLATFORM_WINDOWS
    _wputenv(L"LD_LIBRARY_PATH=(dummy)");
#elif (! defined AVIAN_IOS)
    setenv("LD_LIBRARY_PATH", "", false);
#endif
    
    interceptMethods(t, true);

    JniConstants::init(reinterpret_cast<_JNIEnv*>(t));

    JNI_OnLoad(reinterpret_cast< ::JavaVM*>(t->m), 0);

    mayInitClasses_ = true;
  }

  virtual bool mayInitClasses()
  {
    return mayInitClasses_;
  }

  virtual void
  boot(Thread* t)
  {
    object c = resolveClass
      (t, root(t, Machine::BootLoader), "java/lang/ClassLoader");
    PROTECT(t, c);

    object constructor = resolveMethod
      (t, c, "<init>", "(Ljava/lang/ClassLoader;Z)V");
    PROTECT(t, constructor);

    t->m->processor->invoke
      (t, constructor, root(t, Machine::BootLoader), 0, true);

    t->m->processor->invoke
      (t, constructor, root(t, Machine::AppLoader),
       root(t, Machine::BootLoader), false);
  }

  virtual const char*
  bootClasspath()
  {
    return AVIAN_CLASSPATH;
  }

  virtual object
  makeDirectByteBuffer(Thread* t, void* p, jlong capacity)
  {
    object c = resolveClass
      (t, root(t, Machine::BootLoader), "java/nio/DirectByteBuffer");
    PROTECT(t, c);

    object instance = makeNew(t, c);
    PROTECT(t, instance);

    object constructor = resolveMethod(t, c, "<init>", "(JI)V");

    t->m->processor->invoke
      (t, constructor, instance, reinterpret_cast<int64_t>(p),
       static_cast<int>(capacity));

    return instance;
  }

  virtual void*
  getDirectBufferAddress(Thread* t, object b)
  {
    return local::getDirectBufferAddress(t, b);
  }

  virtual int64_t
  getDirectBufferCapacity(Thread* t, object b)
  {
    PROTECT(t, b);

    object field = resolveField
      (t, objectClass(t, b), "capacity", "I");

    return fieldAtOffset<int32_t>(b, fieldOffset(t, field));
  }

  virtual bool
  canTailCall(Thread*, object, object, object, object)
  {
    return true;
  }

  virtual void
  shutDown(Thread*)
  {
    // ignore
  }

  virtual void
  dispose()
  {
    if (tzdata) {
      tzdata->dispose();
    }
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
  System::Region* tzdata;
  bool mayInitClasses_;
};

int64_t JNICALL
mapData(Thread* t, object, uintptr_t*)
{
  object c = resolveClass
    (t, root(t, Machine::BootLoader), "libcore/io/MemoryMappedFile");
  PROTECT(t, c);
  
  object instance = makeNew(t, c);
  PROTECT(t, instance);
  
  object constructor = resolveMethod(t, c, "<init>", "(JJ)V");
  
  const char* jar = "javahomeJar";
  Finder* finder = getFinder(t, jar, strlen(jar));
  if (finder) {
    System::Region* r = finder->find("tzdata");
    if (r) {
      MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

      expect(t, cp->tzdata == 0);

      cp->tzdata = r;

      t->m->processor->invoke
        (t, constructor, instance, reinterpret_cast<int64_t>(r->start()),
         static_cast<int64_t>(r->length()));

      return reinterpret_cast<uintptr_t>(instance);
    }
  }

  throwNew(t, Machine::RuntimeExceptionType);
}

void JNICALL
closeMemoryMappedFile(Thread* t, object method, uintptr_t* arguments)
{
  object file = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, file);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  if (cp->tzdata) {
    object field = resolveField(t, objectClass(t, file), "address", "J");
  
    if (fieldAtOffset<int64_t>(file, fieldOffset(t, field))
        == reinterpret_cast<int64_t>(cp->tzdata->start()))
    {
      cp->tzdata->dispose();
      cp->tzdata = 0;

      fieldAtOffset<int64_t>(file, fieldOffset(t, field)) = 0;
      return;
    }
  }

  t->m->processor->invoke
    (t, nativeInterceptOriginal
     (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
     file);
}

bool
matchType(Thread* t, object field, object o)
{
  switch (fieldCode(t, field)) {
  case ByteField:
    return objectClass(t, o) == type(t, Machine::ByteType);

  case BooleanField:
    return objectClass(t, o) == type(t, Machine::BooleanType);

  case CharField:
    return objectClass(t, o) == type(t, Machine::CharType);

  case ShortField:
    return objectClass(t, o) == type(t, Machine::ShortType);

  case IntField:
    return objectClass(t, o) == type(t, Machine::IntType);

  case LongField:
    return objectClass(t, o) == type(t, Machine::LongType);

  case FloatField:
    return objectClass(t, o) == type(t, Machine::FloatType);

  case DoubleField:
    return objectClass(t, o) == type(t, Machine::DoubleType);

  case ObjectField:
    if (o == 0) {
      return true;
    } else {
      PROTECT(t, o);

      object spec;
      if (byteArrayBody(t, fieldSpec(t, field), 0) == '[') {
        spec = fieldSpec(t, field);;
      } else {
        spec = makeByteArray(t, byteArrayLength(t, fieldSpec(t, field)) - 2);
      
        memcpy(&byteArrayBody(t, spec, 0),
               &byteArrayBody(t, fieldSpec(t, field), 1),
               byteArrayLength(t, fieldSpec(t, field)) - 3);

        byteArrayBody
          (t, spec, byteArrayLength(t, fieldSpec(t, field)) - 3) = 0;
      }

      return instanceOf
        (t, resolveClass(t, classLoader(t, fieldClass(t, field)), spec), o);
    }

  default: abort(t);
  }
}

object
getField(Thread* t, object field, object instance)
{
  PROTECT(t, field);
  PROTECT(t, instance);

  initClass(t, fieldClass(t, field));

  object target;
  if (fieldFlags(t, field) & ACC_STATIC) {
    target = classStaticTable(t, fieldClass(t, field));
  } else if (instanceOf(t, fieldClass(t, field), instance)){
    target = instance;
  } else {
    throwNew(t, Machine::IllegalArgumentExceptionType);
  }

  unsigned offset = fieldOffset(t, field);
  switch (fieldCode(t, field)) {
  case ByteField:
    return makeByte(t, fieldAtOffset<int8_t>(target, offset));

  case BooleanField:
    return makeBoolean(t, fieldAtOffset<int8_t>(target, offset));

  case CharField:
    return makeChar(t, fieldAtOffset<int16_t>(target, offset));

  case ShortField:
    return makeShort(t, fieldAtOffset<int16_t>(target, offset));

  case IntField:
    return makeInt(t, fieldAtOffset<int32_t>(target, offset));

  case LongField:
    return makeLong(t, fieldAtOffset<int64_t>(target, offset));

  case FloatField:
    return makeFloat(t, fieldAtOffset<int32_t>(target, offset));

  case DoubleField:
    return makeDouble(t, fieldAtOffset<int64_t>(target, offset));

  case ObjectField:
    return fieldAtOffset<object>(target, offset);

  default: abort(t);
  }
}

void
setField(Thread* t, object field, object instance, object value)
{
  PROTECT(t, field);
  PROTECT(t, instance);
  PROTECT(t, value);

  if (not matchType(t, field, value)) {
    throwNew(t, Machine::IllegalArgumentExceptionType);
  }

  object target;
  if ((fieldFlags(t, field) & ACC_STATIC) != 0) {
    target = classStaticTable(t, fieldClass(t, field));
  } else if (instanceOf(t, fieldClass(t, field), instance)){
    target = instance;
  } else {
    throwNew(t, Machine::IllegalArgumentExceptionType);
  }
  PROTECT(t, target);

  initClass(t, fieldClass(t, field));

  unsigned offset = fieldOffset(t, field);
  switch (fieldCode(t, field)) {
  case ByteField:
    fieldAtOffset<int8_t>(target, offset) = byteValue(t, value);
    break;

  case BooleanField:
    fieldAtOffset<int8_t>(target, offset) = booleanValue(t, value);
    break;

  case CharField:
    fieldAtOffset<int16_t>(target, offset) = charValue(t, value);
    break;

  case ShortField:
    fieldAtOffset<int16_t>(target, offset) = shortValue(t, value);
    break;

  case IntField:
    fieldAtOffset<int32_t>(target, offset) = intValue(t, value);
    break;

  case LongField:
    fieldAtOffset<int64_t>(target, offset) = longValue(t, value);
    break;

  case FloatField:
    fieldAtOffset<int32_t>(target, offset) = floatValue(t, value);
    break;

  case DoubleField:
    fieldAtOffset<int64_t>(target, offset) = doubleValue(t, value);
    break;

  case ObjectField:
    set(t, target, offset, value);
    break;

  default: abort(t);
  }
}

} // namespace local

} // namespace

namespace vm {

Classpath*
makeClasspath(System*, Allocator* allocator, const char*, const char*)
{
  return new (allocator->allocate(sizeof(local::MyClasspath)))
    local::MyClasspath(allocator);
}

} // namespace vm

extern "C" int
jniRegisterNativeMethods(JNIEnv* e, const char* className,
                         const JNINativeMethod* methods, int methodCount)
{
  jclass c = e->vtable->FindClass(e, className);

  if (c) {
    e->vtable->RegisterNatives(e, c, methods, methodCount);
  } else {
    e->vtable->ExceptionClear(e);
  }

  return 0;
}

extern "C" void
jniLogException(JNIEnv*, int, const char*, jthrowable)
{
  // ignore
}

extern "C" int
jniThrowException(JNIEnv* e, const char* className, const char* message)
{
  jclass c = e->vtable->FindClass(e, className);

  if (c) {
    e->vtable->ThrowNew(e, c, message);
  }

  return 0;
}

extern "C" int
jniThrowExceptionFmt(JNIEnv* e, const char* className, const char* format,
                     va_list args)
{
  const unsigned size = 4096;
  char buffer[size];
  ::vsnprintf(buffer, size, format, args);
  return jniThrowException(e, className, buffer);
}

extern "C" int
jniThrowNullPointerException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/NullPointerException", message);
}

extern "C" int
jniThrowRuntimeException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/RuntimeException", message);
}

extern "C" int
jniThrowIOException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/IOException", message);
}

extern "C" const char*
jniStrError(int error, char* buffer, size_t length)
{
#ifdef PLATFORM_WINDOWS
  const char* s = getErrnoDescription(error);
  if (strlen(s) < length) {
    strncpy(buffer, s, length);
    return buffer;
  } else {
    return 0;
  }
#else
  if (static_cast<int>(strerror_r(error, buffer, length)) == 0) {
    return buffer;
  } else {
    return 0;
  }
#endif
}

/*
 * Android log priority values (as text)
 */
const char * const androidLogPriorityTitles[] = {
    "UNKNOWN",
    "DEFAULT",
    "VERBOSE",
    "DEBUG",
    "INFO",
    "WARNING",
    "ERROR",
    "FATAL",
    "SILENT"
};

extern "C" int
__android_log_print(int priority, const char* tag,  const char* format, ...)
{
  va_list a;
  const unsigned size = 4096;
  char buffer[size];

  va_start(a, format);
  ::vsnprintf(buffer, size, format, a);
  va_end(a);

#ifndef PLATFORM_WINDOWS
  return printf("[%s] %s: %s\n", androidLogPriorityTitles[priority], tag, buffer);
#else
  return __mingw_fprintf(stderr, "[%s] %s: %s\n", androidLogPriorityTitles[priority], tag, buffer);
#endif
}

extern "C" int
jniGetFDFromFileDescriptor(JNIEnv* e, jobject descriptor)
{
  return e->vtable->GetIntField
    (e, descriptor, e->vtable->GetFieldID
     (e, e->vtable->FindClass
      (e, "java/io/FileDescriptor"), "descriptor", "I"));
}

extern "C" void
jniSetFileDescriptorOfFD(JNIEnv* e, jobject descriptor, int value)
{
  e->vtable->SetIntField
    (e, descriptor, e->vtable->GetFieldID
     (e, e->vtable->FindClass
      (e, "java/io/FileDescriptor"), "descriptor", "I"), value);
}

extern "C" jobject
jniCreateFileDescriptor(JNIEnv* e, int fd)
{
  jobject descriptor = e->vtable->NewObject
    (e, e->vtable->FindClass(e, "java/io/FileDescriptor"),
     e->vtable->GetMethodID
     (e, e->vtable->FindClass(e, "java/io/FileDescriptor"), "<init>", "()V"));

  jniSetFileDescriptorOfFD(e, descriptor, fd);

  return descriptor;
}

int
register_org_apache_harmony_dalvik_NativeTestTarget(_JNIEnv*)
{
  // ignore
  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_compareTo
(Thread* t, object, uintptr_t* arguments)
{
  object a = reinterpret_cast<object>(arguments[0]);
  object b = reinterpret_cast<object>(arguments[1]);

  unsigned length = stringLength(t, a);
  if (length > stringLength(t, b)) {
    length = stringLength(t, b);
  }

  for (unsigned i = 0; i < length; ++i) {
    int d = stringCharAt(t, a, i) - stringCharAt(t, b, i);
    if (d) {
      return d;
    }
  }

  return stringLength(t, a) - stringLength(t, b);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_isEmpty
(Thread* t, object, uintptr_t* arguments)
{
  return stringLength(t, reinterpret_cast<object>(arguments[0])) == 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_length
(Thread* t, object, uintptr_t* arguments)
{
  return stringLength(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_intern
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>
    (intern(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_charAt
(Thread* t, object, uintptr_t* arguments)
{
  return stringCharAt(t, reinterpret_cast<object>(arguments[0]), arguments[1]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_equals
(Thread* t, object, uintptr_t* arguments)
{
  return arguments[1] and stringEqual
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_String_fastIndexOf
(Thread* t, object, uintptr_t* arguments)
{
  object s = reinterpret_cast<object>(arguments[0]);
  unsigned c = arguments[1];
  unsigned start = arguments[2];

  for (unsigned i = start; i < stringLength(t, s); ++i) {
    if (stringCharAt(t, s, i) == c) {
      return i;
    }
  }

  return -1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getInterfaces
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);

  object addendum = classAddendum(t, jclassVmClass(t, c));
  if (addendum) {
    object table = classAddendumInterfaceTable(t, addendum);
    if (table) {
      PROTECT(t, table);

      object array = makeObjectArray(t, arrayLength(t, table));
      PROTECT(t, array);

      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object c = getJClass(t, arrayBody(t, table, i));
        set(t, array, ArrayBody + (i * BytesPerWord), c);
      }

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>
    (makeObjectArray(t, type(t, Machine::JclassType), 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaredClasses
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>
    (getDeclaredClasses
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaringClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>
    (getDeclaringClass
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getEnclosingMethod
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, c);

  object addendum = classAddendum(t, c);
  if (addendum) {
    object enclosingClass = classAddendumEnclosingClass(t, addendum);
    if (enclosingClass) {
      PROTECT(t, enclosingClass);

      enclosingClass = getJClass
        (t, resolveClass(t, classLoader(t, c), enclosingClass));

      object enclosingMethod = classAddendumEnclosingMethod(t, addendum);
      if (enclosingMethod) {
        PROTECT(t, enclosingMethod);

        return reinterpret_cast<uintptr_t>
          (t->m->classpath->makeJMethod
           (t, findMethodInClass
            (t, enclosingClass, pairFirst(t, enclosingMethod),
             pairSecond(t, enclosingMethod))));
      }
    }
  }
  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getEnclosingConstructor
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_java_lang_Class_getEnclosingMethod(t, method, arguments);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_newInstanceImpl
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[0]));

  object method = resolveMethod(t, c, "<init>", "()V");
  PROTECT(t, method);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  t->m->processor->invoke(t, method, instance);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getComponentType
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);

  if (classArrayDimensions(t, jclassVmClass(t, c))) {
    uint8_t n = byteArrayBody(t, className(t, jclassVmClass(t, c)), 1);
    if (n != 'L' and n != '[') {
      return reinterpret_cast<uintptr_t>
        (getJClass(t, primitiveClass(t, n)));
    } else {
      return reinterpret_cast<uintptr_t>
        (getJClass(t, classStaticTable(t, jclassVmClass(t, c))));
    }    
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_classForName
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  object loader = reinterpret_cast<object>(arguments[2]);
  PROTECT(t, loader);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "forName",
     "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");

  return reinterpret_cast<uintptr_t>
    (t->m->processor->invoke
     (t, method, 0, name, static_cast<int>(arguments[1]), loader));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaredField
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, c);

  object name = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, name);
  
  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "findField",
     "(Lavian/VMClass;Ljava/lang/String;)I");

  int index = intValue
    (t, t->m->processor->invoke
     (t, method, 0, jclassVmClass(t, c), name));

  if (index >= 0) {
    return reinterpret_cast<uintptr_t>(local::makeField(t, c, index));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaredConstructorOrMethod
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, c);

  object name = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, name);

  object parameterTypes = reinterpret_cast<object>(arguments[2]);
  PROTECT(t, parameterTypes);
  
  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "findMethod",
     "(Lavian/VMClass;Ljava/lang/String;[Ljava/lang/Class;)I");

  int index = intValue
    (t, t->m->processor->invoke
     (t, method, 0, jclassVmClass(t, c), name, parameterTypes));

  if (index >= 0) {
    return reinterpret_cast<uintptr_t>
      (local::makeMethodOrConstructor(t, c, index));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findLoadedVMClass
(Thread*, object, uintptr_t*);

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_findLoadedClass
(Thread* t, object method, uintptr_t* arguments)
{
  int64_t v = Avian_avian_SystemClassLoader_findLoadedVMClass
    (t, method, arguments);

  if (v) {
    return reinterpret_cast<uintptr_t>
      (getJClass(t, reinterpret_cast<object>(v)));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_defineClass__Ljava_lang_ClassLoader_2Ljava_lang_String_2_3BII
(Thread* t, object method, uintptr_t* arguments)
{
  uintptr_t args[]
    = { arguments[0], arguments[2], arguments[3], arguments[4] };

  int64_t v = Avian_avian_Classes_defineVMClass(t, method, args);

  if (v) {
    return reinterpret_cast<uintptr_t>
      (getJClass(t, reinterpret_cast<object>(v)));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_bootClassPath
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::BootLoader));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_classPath
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::AppLoader));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_vmVersion
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(makeString(t, "%s", AVIAN_VERSION));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_properties
(Thread* t, object, uintptr_t*)
{
  object array = makeObjectArray(
      t, type(t, Machine::StringType), t->m->propertyCount + 1);
  PROTECT(t, array);

  unsigned i;
  for (i = 0; i < t->m->propertyCount; ++i) {
    object s = makeString(t, "%s", t->m->properties[i]);
    set(t, array, ArrayBody + (i * BytesPerWord), s);
  }

  {
    object s = makeString(t, "%s", "java.protocol.handler.pkgs=avian");
    set(t, array, ArrayBody + (i++ * BytesPerWord), s);
  }

  return reinterpret_cast<uintptr_t>(array);
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_Runtime_gc
(Thread* t, object, uintptr_t*)
{
  collect(t, Heap::MajorCollection);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Runtime_nativeLoad
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  unsigned length = stringLength(t, name);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  if (loadLibrary(t, "", RUNTIME_ARRAY_BODY(n), false, true)) {
    return 0;
  } else {
    return reinterpret_cast<uintptr_t>(name);
  }
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_System_arraycopy
(Thread* t, object, uintptr_t* arguments)
{
  arrayCopy(t, reinterpret_cast<object>(arguments[0]),
            arguments[1],
            reinterpret_cast<object>(arguments[2]),
            arguments[3],
            arguments[4]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_objectFieldOffset
(Thread* t, object, uintptr_t* arguments)
{
  object jfield = reinterpret_cast<object>(arguments[1]);
  return fieldOffset
    (t, arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, jfieldDeclaringClass(t, jfield))),
      jfieldSlot(t, jfield)));
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_VMThread_interrupt
(Thread* t, object, uintptr_t* arguments)
{
  object vmThread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, vmThread);

  object field = resolveField
    (t, objectClass(t, vmThread), "thread", "Ljava/lang/Thread;");

  interrupt
    (t, reinterpret_cast<Thread*>
     (threadPeer(t, fieldAtOffset<object>(vmThread, fieldOffset(t, field)))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMThread_interrupted
(Thread* t, object, uintptr_t*)
{
  return getAndClearInterrupted(t, t);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMThread_isInterrupted
(Thread* t, object, uintptr_t* arguments)
{
  object vmThread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, vmThread);

  object field = resolveField
    (t, objectClass(t, vmThread), "thread", "Ljava/lang/Thread;");

  return threadInterrupted
    (t, fieldAtOffset<object>(vmThread, fieldOffset(t, field)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMThread_getStatus
(Thread*, object, uintptr_t*)
{
  // todo
  return 1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_VMThread_currentThread
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(t->javaThread);
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_VMThread_create
(Thread* t, object, uintptr_t* arguments)
{
  object thread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, thread);

  local::initVmThread(t, thread);
  startThread(t, thread);
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_VMThread_sleep
(Thread* t, object, uintptr_t* arguments)
{
  int64_t milliseconds; memcpy(&milliseconds, arguments, 8);
  if (arguments[2] > 0) ++ milliseconds;
  if (milliseconds <= 0) milliseconds = 1;

  if (threadSleepLock(t, t->javaThread) == 0) {
    object lock = makeJobject(t);
    set(t, t->javaThread, ThreadSleepLock, lock);
  }

  acquire(t, threadSleepLock(t, t->javaThread));
  vm::wait(t, threadSleepLock(t, t->javaThread), milliseconds);
  release(t, threadSleepLock(t, t->javaThread));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMStack_getThreadStackTrace
(Thread* t, object, uintptr_t* arguments)
{
  Thread* p = reinterpret_cast<Thread*>
    (threadPeer(t, reinterpret_cast<object>(arguments[0])));

  return reinterpret_cast<uintptr_t>
    (local::translateStackTrace
     (t, p == t
      ? makeTrace(t)
      : t->m->processor->getStackTrace(t, p)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMStack_getCallingClassLoader
(Thread* t, object, uintptr_t*)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t):
    t(t), loader(0), counter(2)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (counter--) {
        return true;
      } else {
        this->loader = classLoader(t, methodClass(t, walker->method()));
        return false;
      }
    }

    Thread* t;
    object loader;
    unsigned counter;
  } v(t);

  t->m->processor->walkStack(t, &v);

  return reinterpret_cast<uintptr_t>(v.loader);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMStack_getClasses
(Thread* t, object, uintptr_t*)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t):
    t(t), array(0), counter(0)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (counter < 2) {
        return true;
      } else {
        if (array == 0) {
          array = makeObjectArray
            (t, type(t, Machine::JclassType), walker->count());
        }

        object c = getJClass(t, methodClass(t, walker->method()));
        
        assert(t, counter - 2 < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((counter - 2) * BytesPerWord), c);

        return true;
      }

      ++ counter;
    }

    Thread* t;
    object array;
    unsigned counter;
  } v(t);

  PROTECT(t, v.array);

  t->m->processor->walkStack(t, &v);

  return reinterpret_cast<uintptr_t>
    (v.array ? v.array : makeObjectArray(t, type(t, Machine::JclassType), 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_min
(Thread*, object, uintptr_t* arguments)
{
  return min(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_max
(Thread*, object, uintptr_t* arguments)
{
  return max(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_cos
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  return doubleToBits(cos(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_sin
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  return doubleToBits(sin(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_sqrt
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  return doubleToBits(sqrt(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_abs__I
(Thread*, object, uintptr_t* arguments)
{
  return abs(static_cast<int32_t>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_abs__J
(Thread*, object, uintptr_t* arguments)
{
  return llabs(arguments[0]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Math_abs__F
(Thread*, object, uintptr_t* arguments)
{
  return floatToBits(abs(bitsToFloat(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Float_intBitsToFloat
(Thread*, object, uintptr_t* arguments)
{
  return arguments[0];
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Float_floatToIntBits
(Thread*, object, uintptr_t* arguments)
{
  if (((arguments[0] & 0x7F800000) == 0x7F800000)
      and ((arguments[0] & 0x007FFFFF) != 0))
  {
    return 0x7fc00000;
  } else {
    return arguments[0];
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Double_doubleToRawLongBits
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  // todo: do we need to do NaN checks as in
  // Avian_java_lang_Float_floatToIntBits above?  If so, update
  // Double.doubleToRawLongBits in the Avian class library too.
  return v;
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_Object_wait
(Thread* t, object, uintptr_t* arguments)
{
  jlong milliseconds; memcpy(&milliseconds, arguments + 1, sizeof(jlong));

  wait(t, reinterpret_cast<object>(arguments[0]), milliseconds);
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_Object_notifyAll
(Thread* t, object, uintptr_t* arguments)
{
  notifyAll(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Object_getClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (getJClass(t, objectClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Object_hashCode
(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Object_internalClone
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (clone(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getModifiers
(Thread* t, object, uintptr_t* arguments)
{
  return classModifiers
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getSuperclass
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[0]));
  if (classFlags(t, c) & ACC_INTERFACE) {
    return 0;
  } else {
    object s = classSuper(t, c);
    return s ? reinterpret_cast<uintptr_t>(getJClass(t, s)) : 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_desiredAssertionStatus
(Thread*, object, uintptr_t*)
{
  return 1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getNameNative
(Thread* t, object, uintptr_t* arguments)
{
  object name = className
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));

  THREAD_RUNTIME_ARRAY(t, char, s, byteArrayLength(t, name));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, name, 0)));

  return reinterpret_cast<uintptr_t>
    (makeString(t, "%s", RUNTIME_ARRAY_BODY(s)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_isInterface
(Thread* t, object, uintptr_t* arguments)
{
  return (classFlags
          (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])))
          & ACC_INTERFACE) != 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_isPrimitive
(Thread* t, object, uintptr_t* arguments)
{
  return (classVmFlags
          (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])))
          & PrimitiveFlag) != 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_isAnonymousClass
(Thread* t, object, uintptr_t* arguments)
{
  object name = className
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));

  for (unsigned i = 0; i < byteArrayLength(t, name) - 1; ++i) {
    int c = byteArrayBody(t, name, i);
    if (c != '$' and (c < '0' or c > '9')) {
      return false;
    }
  }

  return true;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getClassLoader
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (classLoader
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_isAssignableFrom
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object that = reinterpret_cast<object>(arguments[1]);

  if (LIKELY(that)) {
    return isAssignableFrom
      (t, jclassVmClass(t, this_), jclassVmClass(t, that));
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_isInstance
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object o = reinterpret_cast<object>(arguments[1]);

  if (o) {
    return instanceOf(t, jclassVmClass(t, this_), o);
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaredMethods
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, c);

  bool publicOnly = arguments[1];

  object get = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "getMethods",
     "(Lavian/VMClass;Z)[Ljava/lang/reflect/Method;");

  return reinterpret_cast<uintptr_t>
    (t->m->processor->invoke(t, get, 0, jclassVmClass(t, c), publicOnly));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Class_getDeclaredFields
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, c);

  bool publicOnly = arguments[1];

  object get = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "getFields",
     "(Lavian/VMClass;Z)[Ljava/lang/reflect/Field;");

  return reinterpret_cast<uintptr_t>
    (t->m->processor->invoke(t, get, 0, jclassVmClass(t, c), publicOnly));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_invokeNative
(Thread* t, object, uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[1]);
  object args = reinterpret_cast<object>(arguments[2]);
  object method = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[3]))),
      arguments[6]);

  return reinterpret_cast<uintptr_t>(invoke(t, method, instance, args));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getMethodModifiers
(Thread* t, object, uintptr_t* arguments)
{
  return methodFlags
    (t, arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_isAnnotationPresent
(Thread* t, object, uintptr_t* arguments)
{
  object method = arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]);

  object addendum = methodAddendum(t, method);
  if (addendum) {
    object table = addendumAnnotationTable(t, addendum);
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2]))
        {
          return true;
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getAnnotation
(Thread* t, object, uintptr_t* arguments)
{
  object method = arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]);

  object addendum = methodAddendum(t, method);
  if (addendum) {
    object table = addendumAnnotationTable(t, addendum);
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2]))
        {
          PROTECT(t, method);
          PROTECT(t, table);

          object get = resolveMethod
            (t, root(t, Machine::BootLoader), "avian/Classes", "getAnnotation",
             "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
             "Ljava/lang/annotation/Annotation;");

          return reinterpret_cast<uintptr_t>
            (t->m->processor->invoke
             (t, get, 0, classLoader(t, methodClass(t, method)),
              objectArrayBody(t, table, i)));
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getDeclaredAnnotations
(Thread* t, object, uintptr_t* arguments)
{
  object method = arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]);

  object addendum = methodAddendum(t, method);
  if (addendum) {
    object table = addendumAnnotationTable(t, addendum);
    if (table) {
      PROTECT(t, method);
      PROTECT(t, table);

      object array = makeObjectArray
        (t, resolveClass
         (t, root(t, Machine::BootLoader), "java/lang/annotation/Annotation"),
         objectArrayLength(t, table));
      PROTECT(t, array);
      
      object get = resolveMethod
        (t, root(t, Machine::BootLoader), "avian/Classes", "getAnnotation",
         "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
         "Ljava/lang/annotation/Annotation;");
      PROTECT(t, get);

      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        object a = t->m->processor->invoke
          (t, get, 0, classLoader(t, methodClass(t, method)),
           objectArrayBody(t, table, i));

        set(t, array, ArrayBody + (i * BytesPerWord), a);
      }

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>
    (makeObjectArray
     (t, resolveClass
      (t, root(t, Machine::BootLoader), "java/lang/annotation/Annotation"),
      0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getDefaultValue
(Thread* t, object, uintptr_t* arguments)
{
  object method = arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[1]))),
      arguments[2]);

  object addendum = methodAddendum(t, method);
  if (addendum) {
    object get = resolveMethod
      (t, root(t, Machine::BootLoader), "avian/Classes",
       "getAnnotationDefaultValue",
       "(Ljava/lang/ClassLoader;Lavian/MethodAddendum;)"
       "Ljava/lang/Object;");

    return reinterpret_cast<uintptr_t>
      (t->m->processor->invoke
       (t, get, 0, classLoader(t, methodClass(t, method)), addendum));
  }

  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Constructor_constructNative
(Thread* t, object, uintptr_t* arguments)
{
  object args = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, args);

  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[2]));
  PROTECT(t, c);

  initClass(t, c);

  object method = arrayBody(t, classMethodTable(t, c), arguments[4]);
  PROTECT(t, method);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  t->m->processor->invokeArray(t, method, instance, args);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getField
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
    (t, classFieldTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[2]))),
     arguments[4]);
  
  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return reinterpret_cast<intptr_t>(local::getField(t, field, instance));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getIField
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
    (t, classFieldTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[2]))),
     arguments[4]);
  
  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return intValue(t, local::getField(t, field, instance));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getJField
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
    (t, classFieldTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[2]))),
     arguments[4]);
  
  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return longValue(t, local::getField(t, field, instance));
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_reflect_Field_setField
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
    (t, classFieldTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[2]))),
     arguments[4]);
  
  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  object value = reinterpret_cast<object>(arguments[6]);
  PROTECT(t, value);

  local::setField(t, field, instance, value);
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_java_lang_reflect_Field_setIField
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
    (t, classFieldTable
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[2]))),
     arguments[4]);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  object value = makeInt(t, arguments[7]);

  local::setField(t, field, instance, value);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getFieldModifiers
(Thread* t, object, uintptr_t* arguments)
{
  return fieldFlags
    (t, arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[1]))),
      arguments[2]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getAnnotation
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]);

  object addendum = fieldAddendum(t, field);
  if (addendum) {
    object table = addendumAnnotationTable(t, addendum);
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2]))
        {
          PROTECT(t, field);
          PROTECT(t, table);

          object get = resolveMethod
            (t, root(t, Machine::BootLoader), "avian/Classes", "getAnnotation",
             "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
             "Ljava/lang/annotation/Annotation;");

          return reinterpret_cast<uintptr_t>
            (t->m->processor->invoke
             (t, get, 0, classLoader(t, fieldClass(t, field)),
              objectArrayBody(t, table, i)));
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getSignatureAnnotation
(Thread* t, object, uintptr_t* arguments)
{
  object field = arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[1]))),
      arguments[2]);

  object addendum = fieldAddendum(t, field);
  if (addendum) {
    object signature = addendumSignature(t, addendum);
    if (signature) {
      object array = makeObjectArray(t, 1);
      PROTECT(t, array);
      
      object string = t->m->classpath->makeString
        (t, signature, 0, byteArrayLength(t, signature) - 1);
      
      set(t, array, ArrayBody, string);

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>(makeObjectArray(t, 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Throwable_nativeFillInStackTrace
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(getTrace(t, 2));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_Throwable_nativeGetStackTrace
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (local::translateStackTrace(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_avian_Classes_makeMethod
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (local::makeMethodOrConstructor
     (t, reinterpret_cast<object>(arguments[0]), arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_avian_Classes_makeField
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (local::makeField
     (t, reinterpret_cast<object>(arguments[0]), arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_reflect_Array_createObjectArray
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (makeObjectArray
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_nio_ByteOrder_isLittleEndian
(Thread*, object, uintptr_t*)
{
  return true;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_newNonMovableArray
(Thread* t, object, uintptr_t* arguments)
{
  if (jclassVmClass(t, reinterpret_cast<object>(arguments[1]))
      == type(t, Machine::JbyteType))
  {
    object array = allocate3
      (t, t->m->heap, Machine::FixedAllocation,
       ArrayBody + arguments[2], false);

    setObjectClass(t, array, type(t, Machine::ByteArrayType));
    byteArrayLength(t, array) = arguments[2];

    return reinterpret_cast<intptr_t>(array);
  } else {
    // todo
    abort(t);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_addressOf
(Thread*, object, uintptr_t* arguments)
{
  return arguments[1] + ArrayBody;
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_libcore_io_Memory_pokeLong
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int64_t v; memcpy(&v, arguments + 2, 8);
  if (arguments[4]) {
    v = swapV8(v);
  }
  memcpy(reinterpret_cast<void*>(address), &v, 8);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekLong
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int64_t v; memcpy(&v, reinterpret_cast<void*>(address), 8);
  return arguments[2] ? swapV8(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_libcore_io_Memory_pokeInt
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int32_t v = arguments[3] ? swapV4(arguments[2]) : arguments[2];
  memcpy(reinterpret_cast<void*>(address), &v, 4);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekInt
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int32_t v; memcpy(&v, reinterpret_cast<void*>(address), 4);
  return arguments[2] ? swapV4(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_libcore_io_Memory_pokeShort
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int16_t v = arguments[3] ? swapV2(arguments[2]) : arguments[2];
  memcpy(reinterpret_cast<void*>(address), &v, 2);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekShort
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  int16_t v; memcpy(&v, reinterpret_cast<void*>(address), 2);
  return arguments[2] ? swapV2(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
Avian_libcore_io_Memory_pokeByte
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  *reinterpret_cast<int8_t*>(address) = arguments[2];
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekByte
(Thread*, object, uintptr_t* arguments)
{
  int64_t address; memcpy(&address, arguments, 8);
  return *reinterpret_cast<int8_t*>(address);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_System_nanoTime
(Thread* t, object, uintptr_t*)
{
  return t->m->system->now() * 1000 * 1000;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_System_currentTimeMillis
(Thread* t, object, uintptr_t*)
{
  return t->m->system->now();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_lang_System_identityHashCode
(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8
(Thread*, object, uintptr_t*)
{
  return true;
}

#ifdef PLATFORM_WINDOWS

#  include <io.h>

void register_java_io_Console(_JNIEnv*) { }
void register_java_lang_ProcessManager(_JNIEnv*) { }
void register_libcore_net_RawSocket(_JNIEnv*) { }
//void register_org_apache_harmony_xnet_provider_jsse_NativeCrypto(_JNIEnv*) { }

extern "C" AVIAN_EXPORT void JNICALL
Avian_libcore_io_OsConstants_initConstants
(Thread* t, object method, uintptr_t*)
{
  object c = methodClass(t, method);
  PROTECT(t, c);

  object table = classStaticTable(t, c);
  PROTECT(t, table);

  object field = resolveField(t, c, "STDIN_FILENO", "I");
  fieldAtOffset<jint>(table, fieldOffset(t, field)) = 0;

  field = resolveField(t, c, "STDOUT_FILENO", "I");
  fieldAtOffset<jint>(table, fieldOffset(t, field)) = 1;

  field = resolveField(t, c, "STDERR_FILENO", "I");
  fieldAtOffset<jint>(table, fieldOffset(t, field)) = 2;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Posix_getenv(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  THREAD_RUNTIME_ARRAY(t, uint16_t, chars, stringLength(t, name) + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(chars));

  wchar_t* value = _wgetenv
    (reinterpret_cast<wchar_t*>(RUNTIME_ARRAY_BODY(chars)));

  if (value) {
    unsigned size = wcslen(value);
    
    object a = makeCharArray(t, size);
    if (size) {
      memcpy(&charArrayBody(t, a, 0), value, size * sizeof(jchar));
    }
    
    return reinterpret_cast<uintptr_t>
      (t->m->classpath->makeString(t, a, 0, size));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Posix_uname(Thread* t, object, uintptr_t*)
{
  object c = resolveClass
    (t, root(t, Machine::BootLoader), "libcore/io/StructUtsname");
  PROTECT(t, c);
  
  object instance = makeNew(t, c);
  PROTECT(t, instance);
  
#ifdef ARCH_x86_32
  object arch = makeString(t, "x86");
#elif defined ARCH_x86_64
  object arch = makeString(t, "x86_64");
#elif defined ARCH_arm
  object arch = makeString(t, "arm");
#else
  object arch = makeString(t, "unknown");
#endif

  set(t, instance, fieldOffset
      (t, resolveField(t, c, "machine", "Ljava/lang/String;")), arch);

  object platform = makeString(t, "Windows");

  set(t, instance, fieldOffset
      (t, resolveField(t, c, "sysname", "Ljava/lang/String;")), platform);

  object version = makeString(t, "unknown");

  set(t, instance, fieldOffset
      (t, resolveField(t, c, "release", "Ljava/lang/String;")), version);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
Avian_libcore_io_Posix_writeBytes(Thread* t, object, uintptr_t* arguments)
{
  object fd = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, fd);

  object buffer = reinterpret_cast<object>(arguments[2]);
  PROTECT(t, buffer);

  int offset = arguments[3];
  int count = arguments[4];

  int d = jniGetFDFromFileDescriptor(t, &fd);

  int r;
  if (objectClass(t, buffer) == type(t, Machine::ByteArrayType)) {
    void* tmp = t->m->heap->allocate(count);
    memcpy(tmp, &byteArrayBody(t, buffer, offset), count);
    { ENTER(t, Thread::IdleState);
      r = _write(d, tmp, count);
    }
    t->m->heap->free(tmp, count);
  } else {
    void* p = local::getDirectBufferAddress(t, buffer);
    { ENTER(t, Thread::IdleState);
      r = _write(d, p, count);
    }
  }

  if (r < 0) {
    THREAD_RUNTIME_ARRAY(t, char, message, 256);
    throwNew(t, Machine::RuntimeExceptionType, "writeBytes %d: %s", d,
             jniStrError(errno, RUNTIME_ARRAY_BODY(message), 0));
  } else {
    return r;
  }
}

#endif
