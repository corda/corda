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
const char* getErrnoDescription(
    int err);  // This function is defined in mingw-extensions.cpp
#endif

using namespace vm;

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_defineVMClass(Thread*, object, uintptr_t*);

namespace {

namespace local {

void* getDirectBufferAddress(Thread* t, object b)
{
  PROTECT(t, b);

  GcField* field
      = resolveField(t, objectClass(t, b), "effectiveDirectAddress", "J");

  return reinterpret_cast<void*>(fieldAtOffset<int64_t>(b, field->offset()));
}

void JNICALL loadLibrary(Thread* t, object, uintptr_t* arguments)
{
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  Thread::LibraryLoadStack stack(
      t, cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[2])));

  unsigned length = name->length(t);
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

void JNICALL finalizeAllEnqueued(Thread*, object, uintptr_t*)
{
  // ignore
}

int64_t JNICALL appLoader(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(roots(t)->appLoader());
}

int64_t JNICALL defineClass(Thread* t, object method, uintptr_t* arguments)
{
  uintptr_t args[] = {arguments[0], arguments[2], arguments[3], arguments[4]};

  int64_t v = Avian_avian_Classes_defineVMClass(t, method, args);

  if (v) {
    return reinterpret_cast<uintptr_t>(
        getJClass(t, cast<GcClass>(t, reinterpret_cast<object>(v))));
  } else {
    return 0;
  }
}

int64_t JNICALL mapData(Thread*, object, uintptr_t*);

void JNICALL closeMemoryMappedFile(Thread*, GcMethod*, uintptr_t*);

object makeMethodOrConstructor(Thread* t, GcJclass* c, unsigned index)
{
  PROTECT(t, c);

  GcMethod* method = cast<GcMethod>(
      t, cast<GcArray>(t, c->vmClass()->methodTable())->body()[index]);
  PROTECT(t, method);

  unsigned parameterCount;
  unsigned returnTypeSpec;
  object parameterTypes = resolveParameterJTypes(t,
                                                 method->class_()->loader(),
                                                 method->spec(),
                                                 &parameterCount,
                                                 &returnTypeSpec);
  PROTECT(t, parameterTypes);

  GcJclass* returnType = resolveJType(
      t,
      method->class_()->loader(),
      reinterpret_cast<char*>(&method->spec()->body()[returnTypeSpec]),
      method->spec()->length() - 1 - returnTypeSpec);
  PROTECT(t, returnType);

  object exceptionTypes = resolveExceptionJTypes(
      t, method->class_()->loader(), method->addendum());

  if (method->name()->body()[0] == '<') {
    return makeJconstructor(
        t, 0, c, parameterTypes, exceptionTypes, 0, 0, 0, 0, index);
  } else {
    PROTECT(t, exceptionTypes);

    GcString* name = t->m->classpath->makeString(
        t, method->name(), 0, method->name()->length() - 1);

    return makeJmethod(t,
                       0,
                       index,
                       c,
                       name,
                       parameterTypes,
                       exceptionTypes,
                       returnType,
                       0,
                       0,
                       0,
                       0,
                       0);
  }
}

object makeField(Thread* t, GcJclass* c, unsigned index)
{
  PROTECT(t, c);

  GcField* field = cast<GcField>(
      t, cast<GcArray>(t, c->vmClass()->fieldTable())->body()[index]);

  PROTECT(t, field);

  GcJclass* type = getJClass(
      t,
      resolveClassBySpec(t,
                         field->class_()->loader(),
                         reinterpret_cast<char*>(field->spec()->body().begin()),
                         field->spec()->length() - 1));
  PROTECT(t, type);

  GcString* name = t->m->classpath->makeString(
      t, field->name(), 0, field->name()->length() - 1);

  return makeJfield(t, 0, c, type, 0, 0, name, index);
}

void initVmThread(Thread* t, GcThread* thread, unsigned offset)
{
  PROTECT(t, thread);

  if (fieldAtOffset<object>(thread, offset) == 0) {
    GcClass* c = resolveClass(t, roots(t)->bootLoader(), "java/lang/VMThread");
    PROTECT(t, c);

    object instance = makeNew(t, c);
    PROTECT(t, instance);

    GcMethod* constructor
        = resolveMethod(t, c, "<init>", "(Ljava/lang/Thread;)V");

    t->m->processor->invoke(t, constructor, instance, thread);

    setField(t, thread, offset, instance);
  }

  if (thread->group() == 0) {
    thread->setGroup(t, t->javaThread->group());
    expect(t, thread->group());
  }
}

void initVmThread(Thread* t, GcThread* thread)
{
  initVmThread(t,
               thread,
               resolveField(t,
                            objectClass(t, thread),
                            "vmThread",
                            "Ljava/lang/VMThread;")->offset());
}

object translateStackTrace(Thread* t, object raw)
{
  PROTECT(t, raw);

  object array = makeObjectArray(
      t,
      resolveClass(t, roots(t)->bootLoader(), "java/lang/StackTraceElement"),
      objectArrayLength(t, raw));
  PROTECT(t, array);

  for (unsigned i = 0; i < objectArrayLength(t, array); ++i) {
    GcStackTraceElement* e = makeStackTraceElement(
        t, cast<GcTraceElement>(t, objectArrayBody(t, raw, i)));

    setField(t, array, ArrayBody + (i * BytesPerWord), e);
  }

  return array;
}

class MyClasspath : public Classpath {
 public:
  MyClasspath(Allocator* allocator)
      : allocator(allocator), tzdata(0), mayInitClasses_(false)
  {
  }

  virtual GcJclass* makeJclass(Thread* t, GcClass* class_)
  {
    PROTECT(t, class_);

    GcJclass* c
        = reinterpret_cast<GcJclass*>(allocate(t, GcJclass::FixedSize, true));
    setObjectClass(t, c, type(t, GcJclass::Type));
    c->setVmClass(t, class_);

    return c;
  }

  virtual GcString* makeString(Thread* t,
                               object array,
                               int32_t offset,
                               int32_t length)
  {
    if (objectClass(t, array) == type(t, GcByteArray::Type)) {
      GcByteArray* byteArray = cast<GcByteArray>(t, array);
      PROTECT(t, array);
      PROTECT(t, byteArray);

      GcCharArray* charArray = makeCharArray(t, length);
      for (int i = 0; i < length; ++i) {
        expect(t, (byteArray->body()[offset + i] & 0x80) == 0);

        charArray->body()[i] = byteArray->body()[offset + i];
      }

      array = charArray;
    } else {
      expect(t, objectClass(t, array) == type(t, GcCharArray::Type));
    }

    return vm::makeString(t, array, offset, length, 0);
  }

  virtual GcThread* makeThread(Thread* t, Thread* parent)
  {
    const unsigned NormalPriority = 5;

    GcThreadGroup* group = 0;
    PROTECT(t, group);
    if (parent) {
      group = parent->javaThread->group();
    } else {
      resolveSystemClass(t,
                         roots(t)->bootLoader(),
                         type(t, GcThreadGroup::Type)->name(),
                         false);

      group = cast<GcThreadGroup>(t, makeNew(t, type(t, GcThreadGroup::Type)));

      GcMethod* constructor
          = resolveMethod(t, type(t, GcThreadGroup::Type), "<init>", "()V");

      t->m->processor->invoke(t, constructor, group);
    }

    resolveSystemClass(
        t, roots(t)->bootLoader(), type(t, GcThread::Type)->name(), false);

    GcThread* thread = cast<GcThread>(t, makeNew(t, type(t, GcThread::Type)));
    PROTECT(t, thread);

    GcMethod* constructor
        = resolveMethod(t,
                        type(t, GcThread::Type),
                        "<init>",
                        "(Ljava/lang/ThreadGroup;Ljava/lang/String;IZ)V");

    t->m->processor->invoke(
        t, constructor, thread, group, 0, NormalPriority, false);

    thread->setContextClassLoader(t, roots(t)->appLoader());

    initVmThread(t, thread);

    return thread;
  }

  virtual object makeJMethod(Thread* t, GcMethod* vmMethod)
  {
    GcArray* table = cast<GcArray>(t, vmMethod->class_()->methodTable());
    for (unsigned i = 0; i < table->length(); ++i) {
      if (vmMethod == table->body()[i]) {
        return makeMethodOrConstructor(t, getJClass(t, vmMethod->class_()), i);
      }
    }
    abort(t);
  }

  virtual GcMethod* getVMMethod(Thread* t, object jmethod)
  {
    return cast<GcMethod>(
        t,
        objectClass(t, jmethod) == type(t, GcJmethod::Type)
            ? cast<GcArray>(t,
                            cast<GcJmethod>(t, jmethod)
                                ->declaringClass()
                                ->vmClass()
                                ->methodTable())
                  ->body()[cast<GcJmethod>(t, jmethod)->slot()]
            : cast<GcArray>(t,
                            cast<GcJconstructor>(t, jmethod)
                                ->declaringClass()
                                ->vmClass()
                                ->methodTable())
                  ->body()[cast<GcJconstructor>(t, jmethod)->slot()]);
  }

  virtual object makeJField(Thread* t, GcField* vmField)
  {
    GcArray* table = cast<GcArray>(t, vmField->class_()->fieldTable());
    for (unsigned i = 0; i < table->length(); ++i) {
      if (vmField == table->body()[i]) {
        return makeField(t, getJClass(t, vmField->class_()), i);
      }
    }
    abort(t);
  }

  virtual GcField* getVMField(Thread* t, GcJfield* jfield)
  {
    return cast<GcField>(
        t,
        cast<GcArray>(t, jfield->declaringClass()->vmClass()->fieldTable())
            ->body()[jfield->slot()]);
  }

  virtual void clearInterrupted(Thread*)
  {
    // ignore
  }

  virtual void runThread(Thread* t)
  {
    // force monitor creation so we don't get an OutOfMemory error
    // later when we try to acquire it:
    objectMonitor(t, t->javaThread, true);

    GcField* field = resolveField(
        t, objectClass(t, t->javaThread), "vmThread", "Ljava/lang/VMThread;");

    unsigned offset = field->offset();

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
      t->clearFlag(Thread::ActiveFlag);
      vm::notifyAll(t, t->javaThread);
      vm::release(t, t->javaThread);
    });

    initVmThread(t, t->javaThread, offset);

    GcMethod* method = resolveMethod(
        t, roots(t)->bootLoader(), "java/lang/Thread", "run", "()V");

    t->m->processor->invoke(t, method, t->javaThread);
  }

  virtual void resolveNative(Thread* t, GcMethod* method)
  {
    vm::resolveNative(t, method);
  }

  void interceptMethods(Thread* t, bool updateRuntimeData)
  {
    {
      GcClass* c
          = resolveClass(t, roots(t)->bootLoader(), "java/lang/Runtime", false);

      if (c) {
        PROTECT(t, c);

        intercept(t,
                  c,
                  "loadLibrary",
                  "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
                  voidPointer(loadLibrary),
                  updateRuntimeData);
      }
    }

    {
      GcClass* c = resolveClass(
          t, roots(t)->bootLoader(), "java/lang/ref/FinalizerReference", false);

      if (c) {
        PROTECT(t, c);

        intercept(t,
                  c,
                  "finalizeAllEnqueued",
                  "()V",
                  voidPointer(finalizeAllEnqueued),
                  updateRuntimeData);
      }
    }

    {
      GcClass* c = resolveClass(
          t, roots(t)->bootLoader(), "java/lang/ClassLoader", false);

      if (c) {
        PROTECT(t, c);

        intercept(t,
                  c,
                  "createSystemClassLoader",
                  "()Ljava/lang/ClassLoader;",
                  voidPointer(appLoader),
                  updateRuntimeData);

        intercept(t,
                  c,
                  "defineClass",
                  "(Ljava/lang/String;[BII)Ljava/lang/Class;",
                  voidPointer(defineClass),
                  updateRuntimeData);
      }
    }

    {
      GcClass* c = resolveClass(
          t, roots(t)->bootLoader(), "libcore/util/ZoneInfoDB", false);

      if (c) {
        PROTECT(t, c);

        intercept(t,
                  c,
                  "mapData",
                  "()Llibcore/io/MemoryMappedFile;",
                  voidPointer(mapData),
                  updateRuntimeData);
      }
    }

    {
      GcClass* c = resolveClass(
          t, roots(t)->bootLoader(), "libcore/io/MemoryMappedFile", false);

      if (c) {
        PROTECT(t, c);

        intercept(t,
                  c,
                  "close",
                  "()V",
                  voidPointer(closeMemoryMappedFile),
                  updateRuntimeData);
      }
    }
  }

  virtual void interceptMethods(Thread* t)
  {
    interceptMethods(t, false);
  }

  virtual void preBoot(Thread* t)
  {
// Android's System.initSystemProperties throws an NPE if
// LD_LIBRARY_PATH is not set as of this writing:
#ifdef PLATFORM_WINDOWS
    _wputenv(L"LD_LIBRARY_PATH=(dummy)");
#elif(!defined AVIAN_IOS)
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

  virtual void boot(Thread* t)
  {
    GcClass* c
        = resolveClass(t, roots(t)->bootLoader(), "java/lang/ClassLoader");
    PROTECT(t, c);

    GcMethod* constructor
        = resolveMethod(t, c, "<init>", "(Ljava/lang/ClassLoader;Z)V");
    PROTECT(t, constructor);

    t->m->processor->invoke(t, constructor, roots(t)->bootLoader(), 0, true);

    t->m->processor->invoke(
        t, constructor, roots(t)->appLoader(), roots(t)->bootLoader(), false);
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
                            static_cast<int>(capacity));

    return instance;
  }

  virtual void* getDirectBufferAddress(Thread* t, object b)
  {
    return local::getDirectBufferAddress(t, b);
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
    return strcmp("java/lang/Runtime",
                  reinterpret_cast<char*>(
                      caller->class_()->name()->body().begin())) == 0
               ? t->libraryLoadStack->classLoader
               : caller->class_()->loader();
  }

  virtual void shutDown(Thread*)
  {
    // ignore
  }

  virtual void dispose()
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

int64_t JNICALL mapData(Thread* t, object, uintptr_t*)
{
  GcClass* c
      = resolveClass(t, roots(t)->bootLoader(), "libcore/io/MemoryMappedFile");
  PROTECT(t, c);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  GcMethod* constructor = resolveMethod(t, c, "<init>", "(JJ)V");

  const char* jar = "javahomeJar";
  Finder* finder = getFinder(t, jar, strlen(jar));
  if (finder) {
    System::Region* r = finder->find("tzdata");
    if (r) {
      MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

      expect(t, cp->tzdata == 0);

      cp->tzdata = r;

      t->m->processor->invoke(t,
                              constructor,
                              instance,
                              reinterpret_cast<int64_t>(r->start()),
                              static_cast<int64_t>(r->length()));

      return reinterpret_cast<uintptr_t>(instance);
    }
  }

  throwNew(t, GcRuntimeException::Type);
}

void JNICALL
    closeMemoryMappedFile(Thread* t, GcMethod* method, uintptr_t* arguments)
{
  object file = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, file);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  if (cp->tzdata) {
    GcField* field = resolveField(t, objectClass(t, file), "address", "J");

    if (fieldAtOffset<int64_t>(file, field->offset())
        == reinterpret_cast<int64_t>(cp->tzdata->start())) {
      cp->tzdata->dispose();
      cp->tzdata = 0;

      fieldAtOffset<int64_t>(file, field->offset()) = 0;
      return;
    }
  }

  t->m->processor->invoke(t,
                          cast<GcMethod>(t,
                                         getMethodRuntimeData(t, method)
                                             ->native()
                                             ->as<GcNativeIntercept>(t)
                                             ->original()),
                          file);
}

bool matchType(Thread* t, GcField* field, object o)
{
  switch (field->code()) {
  case ByteField:
    return objectClass(t, o) == type(t, GcByte::Type);

  case BooleanField:
    return objectClass(t, o) == type(t, GcBoolean::Type);

  case CharField:
    return objectClass(t, o) == type(t, GcChar::Type);

  case ShortField:
    return objectClass(t, o) == type(t, GcShort::Type);

  case IntField:
    return objectClass(t, o) == type(t, GcInt::Type);

  case LongField:
    return objectClass(t, o) == type(t, GcLong::Type);

  case FloatField:
    return objectClass(t, o) == type(t, GcFloat::Type);

  case DoubleField:
    return objectClass(t, o) == type(t, GcDouble::Type);

  case ObjectField:
    if (o == 0) {
      return true;
    } else {
      PROTECT(t, o);

      GcByteArray* spec;
      if (field->spec()->body()[0] == '[') {
        spec = field->spec();
      } else {
        spec = makeByteArray(t, field->spec()->length() - 2);

        memcpy(spec->body().begin(),
               &field->spec()->body()[1],
               field->spec()->length() - 3);

        spec->body()[field->spec()->length() - 3] = 0;
      }

      return instanceOf(t, resolveClass(t, field->class_()->loader(), spec), o);
    }

  default:
    abort(t);
  }
}

object getField(Thread* t, GcField* field, object instance)
{
  PROTECT(t, field);
  PROTECT(t, instance);

  initClass(t, field->class_());

  object target;
  if (field->flags() & ACC_STATIC) {
    target = field->class_()->staticTable();
  } else if (instanceOf(t, field->class_(), instance)) {
    target = instance;
  } else {
    throwNew(t, GcIllegalArgumentException::Type);
  }

  unsigned offset = field->offset();
  switch (field->code()) {
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

  default:
    abort(t);
  }
}

void setField(Thread* t, GcField* field, object instance, object value)
{
  PROTECT(t, field);
  PROTECT(t, instance);
  PROTECT(t, value);

  if (not matchType(t, field, value)) {
    throwNew(t, GcIllegalArgumentException::Type);
  }

  object target;
  if ((field->flags() & ACC_STATIC) != 0) {
    target = field->class_()->staticTable();
  } else if (instanceOf(t, field->class_(), instance)) {
    target = instance;
  } else {
    throwNew(t, GcIllegalArgumentException::Type);
  }
  PROTECT(t, target);

  initClass(t, field->class_());

  unsigned offset = field->offset();
  switch (field->code()) {
  case ByteField:
    fieldAtOffset<int8_t>(target, offset) = cast<GcByte>(t, value)->value();
    break;

  case BooleanField:
    fieldAtOffset<int8_t>(target, offset) = cast<GcBoolean>(t, value)->value();
    break;

  case CharField:
    fieldAtOffset<int16_t>(target, offset) = cast<GcChar>(t, value)->value();
    break;

  case ShortField:
    fieldAtOffset<int16_t>(target, offset) = cast<GcShort>(t, value)->value();
    break;

  case IntField:
    fieldAtOffset<int32_t>(target, offset) = cast<GcInt>(t, value)->value();
    break;

  case LongField:
    fieldAtOffset<int64_t>(target, offset) = cast<GcLong>(t, value)->value();
    break;

  case FloatField:
    fieldAtOffset<int32_t>(target, offset) = cast<GcFloat>(t, value)->value();
    break;

  case DoubleField:
    fieldAtOffset<int64_t>(target, offset) = cast<GcDouble>(t, value)->value();
    break;

  case ObjectField:
    setField(t, target, offset, value);
    break;

  default:
    abort(t);
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

extern "C" int jniRegisterNativeMethods(JNIEnv* e,
                                        const char* className,
                                        const JNINativeMethod* methods,
                                        int methodCount)
{
  jclass c = e->vtable->FindClass(e, className);

  if (c) {
    e->vtable->RegisterNatives(e, c, methods, methodCount);
  } else {
    e->vtable->ExceptionClear(e);
  }

  return 0;
}

extern "C" void jniLogException(JNIEnv*, int, const char*, jthrowable)
{
  // ignore
}

extern "C" int jniThrowException(JNIEnv* e,
                                 const char* className,
                                 const char* message)
{
  jclass c = e->vtable->FindClass(e, className);

  if (c) {
    e->vtable->ThrowNew(e, c, message);
  }

  return 0;
}

extern "C" int jniThrowExceptionFmt(JNIEnv* e,
                                    const char* className,
                                    const char* format,
                                    va_list args)
{
  const unsigned size = 4096;
  char buffer[size];
  ::vsnprintf(buffer, size, format, args);
  return jniThrowException(e, className, buffer);
}

extern "C" int jniThrowNullPointerException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/NullPointerException", message);
}

extern "C" int jniThrowRuntimeException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/RuntimeException", message);
}

extern "C" int jniThrowIOException(JNIEnv* e, const char* message)
{
  return jniThrowException(e, "java/lang/IOException", message);
}

extern "C" const char* jniStrError(int error, char* buffer, size_t length)
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
const char* const androidLogPriorityTitles[] = {"UNKNOWN",
                                                "DEFAULT",
                                                "VERBOSE",
                                                "DEBUG",
                                                "INFO",
                                                "WARNING",
                                                "ERROR",
                                                "FATAL",
                                                "SILENT"};

extern "C" int __android_log_print(int priority,
                                   const char* tag,
                                   const char* format,
                                   ...)
{
  va_list a;
  const unsigned size = 4096;
  char buffer[size];

  va_start(a, format);
  ::vsnprintf(buffer, size, format, a);
  va_end(a);

#ifndef PLATFORM_WINDOWS
  return printf(
      "[%s] %s: %s\n", androidLogPriorityTitles[priority], tag, buffer);
#else
  return __mingw_fprintf(
      stderr, "[%s] %s: %s\n", androidLogPriorityTitles[priority], tag, buffer);
#endif
}

extern "C" int jniGetFDFromFileDescriptor(JNIEnv* e, jobject descriptor)
{
  return e->vtable->GetIntField(
      e,
      descriptor,
      e->vtable->GetFieldID(e,
                            e->vtable->FindClass(e, "java/io/FileDescriptor"),
                            "descriptor",
                            "I"));
}

extern "C" void jniSetFileDescriptorOfFD(JNIEnv* e,
                                         jobject descriptor,
                                         int value)
{
  e->vtable->SetIntField(
      e,
      descriptor,
      e->vtable->GetFieldID(e,
                            e->vtable->FindClass(e, "java/io/FileDescriptor"),
                            "descriptor",
                            "I"),
      value);
}

extern "C" jobject jniCreateFileDescriptor(JNIEnv* e, int fd)
{
  jobject descriptor = e->vtable->NewObject(
      e,
      e->vtable->FindClass(e, "java/io/FileDescriptor"),
      e->vtable->GetMethodID(e,
                             e->vtable->FindClass(e, "java/io/FileDescriptor"),
                             "<init>",
                             "()V"));

  jniSetFileDescriptorOfFD(e, descriptor, fd);

  return descriptor;
}

int register_org_apache_harmony_dalvik_NativeTestTarget(_JNIEnv*)
{
  // ignore
  return 0;
}

int register_java_math_NativeBN(_JNIEnv*)
{
  // ignore
  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_compareTo(Thread* t, object, uintptr_t* arguments)
{
  GcString* a = cast<GcString>(t, reinterpret_cast<object>(arguments[0]));
  GcString* b = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  unsigned length = a->length(t);
  if (length > b->length(t)) {
    length = b->length(t);
  }

  for (unsigned i = 0; i < length; ++i) {
    int d = stringCharAt(t, a, i) - stringCharAt(t, b, i);
    if (d) {
      return d;
    }
  }

  return a->length(t) - b->length(t);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_isEmpty(Thread* t, object, uintptr_t* arguments)
{
  return cast<GcString>(t, reinterpret_cast<object>(arguments[0]))->length(t)
         == 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_length(Thread* t, object, uintptr_t* arguments)
{
  return cast<GcString>(t, reinterpret_cast<object>(arguments[0]))->length(t);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_intern(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>(
      intern(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_charAt(Thread* t, object, uintptr_t* arguments)
{
  return stringCharAt(t,
                      cast<GcString>(t, reinterpret_cast<object>(arguments[0])),
                      arguments[1]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_equals(Thread* t, object, uintptr_t* arguments)
{
  return arguments[1] and stringEqual(t,
                                      reinterpret_cast<object>(arguments[0]),
                                      reinterpret_cast<object>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_String_fastIndexOf(Thread* t, object, uintptr_t* arguments)
{
  GcString* s = cast<GcString>(t, reinterpret_cast<object>(arguments[0]));
  unsigned c = arguments[1];
  unsigned start = arguments[2];

  for (unsigned i = start; i < s->length(t); ++i) {
    if (stringCharAt(t, s, i) == c) {
      return i;
    }
  }

  return -1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getInterfaces(Thread* t, object, uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));

  GcClassAddendum* addendum = c->vmClass()->addendum();
  if (addendum) {
    GcArray* table = cast<GcArray>(t, addendum->interfaceTable());
    if (table) {
      PROTECT(t, table);

      object array = makeObjectArray(t, table->length());
      PROTECT(t, array);

      for (unsigned i = 0; i < table->length(); ++i) {
        GcJclass* c = getJClass(t, cast<GcClass>(t, table->body()[i]));
        setField(t,
                 array,
                 ArrayBody + (i * BytesPerWord),
                 reinterpret_cast<object>(c));
      }

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>(
      makeObjectArray(t, type(t, GcJclass::Type), 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getDeclaredClasses(Thread* t,
                                             object,
                                             uintptr_t* arguments)
{
  return reinterpret_cast<intptr_t>(getDeclaredClasses(
      t,
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass(),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_newInstanceImpl(Thread* t,
                                          object,
                                          uintptr_t* arguments)
{
  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass();

  GcMethod* method = resolveMethod(t, c, "<init>", "()V");
  PROTECT(t, method);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  t->m->processor->invoke(t, method, instance);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getComponentType(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));

  if (c->vmClass()->arrayDimensions()) {
    uint8_t n = c->vmClass()->name()->body()[1];
    if (n != 'L' and n != '[') {
      return reinterpret_cast<uintptr_t>(getJClass(t, primitiveClass(t, n)));
    } else {
      return reinterpret_cast<uintptr_t>(
          getJClass(t, c->vmClass()->arrayElementClass()));
    }
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_classForName(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[2]));
  PROTECT(t, loader);

  GcMethod* method = resolveMethod(
      t,
      roots(t)->bootLoader(),
      "avian/Classes",
      "forName",
      "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");

  return reinterpret_cast<uintptr_t>(t->m->processor->invoke(
      t, method, 0, name, static_cast<int>(arguments[1]), loader));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getDeclaredField(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, c);

  object name = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, name);

  GcMethod* method = resolveMethod(t,
                                   roots(t)->bootLoader(),
                                   "avian/Classes",
                                   "findField",
                                   "(Lavian/VMClass;Ljava/lang/String;)I");

  int index = cast<GcInt>(
                  t, t->m->processor->invoke(t, method, 0, c->vmClass(), name))
                  ->value();

  if (index >= 0) {
    return reinterpret_cast<uintptr_t>(local::makeField(t, c, index));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getDeclaredConstructorOrMethod(Thread* t,
                                                         object,
                                                         uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, c);

  object name = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, name);

  object parameterTypes = reinterpret_cast<object>(arguments[2]);
  PROTECT(t, parameterTypes);

  GcMethod* method
      = resolveMethod(t,
                      roots(t)->bootLoader(),
                      "avian/Classes",
                      "findMethod",
                      "(Lavian/VMClass;Ljava/lang/String;[Ljava/lang/Class;)I");

  int index = cast<GcInt>(t,
                          t->m->processor->invoke(
                              t, method, 0, c->vmClass(), name, parameterTypes))
                  ->value();

  if (index >= 0) {
    return reinterpret_cast<uintptr_t>(
        local::makeMethodOrConstructor(t, c, index));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_SystemClassLoader_findLoadedVMClass(Thread*,
                                                    object,
                                                    uintptr_t*);

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMClassLoader_findLoadedClass(Thread* t,
                                                  object method,
                                                  uintptr_t* arguments)
{
  int64_t v
      = Avian_avian_SystemClassLoader_findLoadedVMClass(t, method, arguments);

  if (v) {
    return reinterpret_cast<uintptr_t>(
        getJClass(t, cast<GcClass>(t, reinterpret_cast<object>(v))));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMClassLoader_defineClass__Ljava_lang_ClassLoader_2Ljava_lang_String_2_3BII(
        Thread* t,
        object method,
        uintptr_t* arguments)
{
  uintptr_t args[] = {arguments[0], arguments[2], arguments[3], arguments[4]};

  int64_t v = Avian_avian_Classes_defineVMClass(t, method, args);

  if (v) {
    return reinterpret_cast<uintptr_t>(
        getJClass(t, cast<GcClass>(t, reinterpret_cast<object>(v))));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_bootClassPath(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(roots(t)->bootLoader());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_classPath(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(roots(t)->appLoader());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_vmVersion(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(makeString(t, "%s", AVIAN_VERSION));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_properties(Thread* t, object, uintptr_t*)
{
  object array
      = makeObjectArray(t, type(t, GcString::Type), t->m->propertyCount + 1);
  PROTECT(t, array);

  unsigned i;
  for (i = 0; i < t->m->propertyCount; ++i) {
    GcString* s = makeString(t, "%s", t->m->properties[i]);
    setField(
        t, array, ArrayBody + (i * BytesPerWord), reinterpret_cast<object>(s));
  }

  {
    GcString* s = makeString(t, "%s", "java.protocol.handler.pkgs=avian");
    setField(t,
             array,
             ArrayBody + (i++ * BytesPerWord),
             reinterpret_cast<object>(s));
  }

  return reinterpret_cast<uintptr_t>(array);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Runtime_gc(Thread* t, object, uintptr_t*)
{
  collect(t, Heap::MajorCollection);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Runtime_nativeExit(Thread* t, object, uintptr_t* arguments)
{
  shutDown(t);

  t->m->system->exit(arguments[0]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Runtime_nativeLoad(Thread* t, object, uintptr_t* arguments)
{
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, name);

  unsigned length = name->length(t);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  if (loadLibrary(t, "", RUNTIME_ARRAY_BODY(n), false, true)) {
    return 0;
  } else {
    return reinterpret_cast<uintptr_t>(name);
  }
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
    Avian_sun_misc_Unsafe_objectFieldOffset(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcJfield* jfield = cast<GcJfield>(t, reinterpret_cast<object>(arguments[1]));
  return cast<GcField>(
             t,
             cast<GcArray>(t, jfield->declaringClass()->vmClass()->fieldTable())
                 ->body()[jfield->slot()])->offset();
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_VMThread_interrupt(Thread* t, object, uintptr_t* arguments)
{
  object vmThread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, vmThread);

  GcField* field = resolveField(
      t, objectClass(t, vmThread), "thread", "Ljava/lang/Thread;");

  interrupt(
      t,
      reinterpret_cast<Thread*>(
          cast<GcThread>(t, fieldAtOffset<object>(vmThread, field->offset()))
              ->peer()));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMThread_interrupted(Thread* t, object, uintptr_t*)
{
  return getAndClearInterrupted(t, t);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMThread_isInterrupted(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  object vmThread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, vmThread);

  GcField* field = resolveField(
      t, objectClass(t, vmThread), "thread", "Ljava/lang/Thread;");

  return cast<GcThread>(t, fieldAtOffset<object>(vmThread, field->offset()))
      ->interrupted();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMThread_getStatus(Thread*, object, uintptr_t*)
{
  // todo
  return 1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMThread_currentThread(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(t->javaThread);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_VMThread_create(Thread* t, object, uintptr_t* arguments)
{
  GcThread* thread = cast<GcThread>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, thread);

  local::initVmThread(t, thread);
  startThread(t, thread);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_VMThread_sleep(Thread* t, object, uintptr_t* arguments)
{
  int64_t milliseconds;
  memcpy(&milliseconds, arguments, 8);
  if (arguments[2] > 0)
    ++milliseconds;
  if (milliseconds <= 0)
    milliseconds = 1;

  if (t->javaThread->sleepLock() == 0) {
    object lock = makeJobject(t);
    t->javaThread->setSleepLock(t, lock);
  }

  acquire(t, t->javaThread->sleepLock());
  vm::wait(t, t->javaThread->sleepLock(), milliseconds);
  release(t, t->javaThread->sleepLock());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_VMThread_holdsLock(Thread* t, object, uintptr_t* arguments)
{
  object vmThread = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, vmThread);

  GcField* field = resolveField(
      t, objectClass(t, vmThread), "thread", "Ljava/lang/Thread;");

  if (cast<GcThread>(t, fieldAtOffset<object>(vmThread, field->offset()))
      != t->javaThread) {
    throwNew(t,
             GcIllegalStateException::Type,
             "VMThread.holdsLock may only be called on current thread");
  }

  GcMonitor* m
      = objectMonitor(t, reinterpret_cast<object>(arguments[1]), false);

  return m and m->owner() == t;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_VMThread_yield(Thread* t, object, uintptr_t*)
{
  t->m->system->yield();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMStack_getThreadStackTrace(Thread* t,
                                                    object,
                                                    uintptr_t* arguments)
{
  Thread* p = reinterpret_cast<Thread*>(
      cast<GcThread>(t, reinterpret_cast<object>(arguments[0]))->peer());

  return reinterpret_cast<uintptr_t>(local::translateStackTrace(
      t, p == t ? makeTrace(t) : t->m->processor->getStackTrace(t, p)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMStack_getCallingClassLoader(Thread* t,
                                                      object,
                                                      uintptr_t*)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t) : t(t), loader(0), counter(2)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      if (counter--) {
        return true;
      } else {
        this->loader = walker->method()->class_()->loader();
        return false;
      }
    }

    Thread* t;
    GcClassLoader* loader;
    unsigned counter;
  } v(t);

  t->m->processor->walkStack(t, &v);

  return reinterpret_cast<uintptr_t>(v.loader);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMStack_getClasses(Thread* t, object, uintptr_t*)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t) : t(t), array(0), counter(0)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      if (counter < 2) {
        return true;
      } else {
        if (array == 0) {
          array = makeObjectArray(t, type(t, GcJclass::Type), walker->count());
        }

        GcJclass* c = getJClass(t, walker->method()->class_());

        assertT(t, counter - 2 < objectArrayLength(t, array));

        setField(t, array, ArrayBody + ((counter - 2) * BytesPerWord), c);

        return true;
      }

      ++counter;
    }

    Thread* t;
    object array;
    unsigned counter;
  } v(t);

  PROTECT(t, v.array);

  t->m->processor->walkStack(t, &v);

  return reinterpret_cast<uintptr_t>(
      v.array ? v.array : makeObjectArray(t, type(t, GcJclass::Type), 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_min(Thread*, object, uintptr_t* arguments)
{
  return min(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_max(Thread*, object, uintptr_t* arguments)
{
  return max(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_cos(Thread*, object, uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  return doubleToBits(cos(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_sin(Thread*, object, uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  return doubleToBits(sin(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_sqrt(Thread*, object, uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  return doubleToBits(sqrt(bitsToDouble(v)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_abs__I(Thread*, object, uintptr_t* arguments)
{
  return abs(static_cast<int32_t>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_abs__J(Thread*, object, uintptr_t* arguments)
{
  return llabs(arguments[0]);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Math_abs__F(Thread*, object, uintptr_t* arguments)
{
  return floatToBits(abs(bitsToFloat(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Float_intBitsToFloat(Thread*, object, uintptr_t* arguments)
{
  return arguments[0];
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Float_floatToIntBits(Thread*, object, uintptr_t* arguments)
{
  if (((arguments[0] & 0x7F800000) == 0x7F800000)
      and ((arguments[0] & 0x007FFFFF) != 0)) {
    return 0x7fc00000;
  } else {
    return arguments[0];
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Double_doubleToRawLongBits(Thread*,
                                               object,
                                               uintptr_t* arguments)
{
  int64_t v;
  memcpy(&v, arguments, 8);
  // todo: do we need to do NaN checks as in
  // Avian_java_lang_Float_floatToIntBits above?  If so, update
  // Double.doubleToRawLongBits in the Avian class library too.
  return v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Object_wait(Thread* t, object, uintptr_t* arguments)
{
  jlong milliseconds;
  memcpy(&milliseconds, arguments + 1, sizeof(jlong));

  wait(t, reinterpret_cast<object>(arguments[0]), milliseconds);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_Object_notifyAll(Thread* t, object, uintptr_t* arguments)
{
  notifyAll(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_getClass(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(
      getJClass(t, objectClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_hashCode(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Object_internalClone(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(
      clone(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getModifiers(Thread* t, object, uintptr_t* arguments)
{
  return classModifiers(
      t, cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getSuperclass(Thread* t, object, uintptr_t* arguments)
{
  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass();
  if (c->flags() & ACC_INTERFACE) {
    return 0;
  } else {
    GcClass* s = c->super();
    return s ? reinterpret_cast<uintptr_t>(getJClass(t, s)) : 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_desiredAssertionStatus(Thread*, object, uintptr_t*)
{
  return 1;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getNameNative(Thread* t, object, uintptr_t* arguments)
{
  GcByteArray* name = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                          ->vmClass()
                          ->name();

  THREAD_RUNTIME_ARRAY(t, char, s, name->length());
  replace('/',
          '.',
          RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(name->body().begin()));

  return reinterpret_cast<uintptr_t>(
      makeString(t, "%s", RUNTIME_ARRAY_BODY(s)));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_isInterface(Thread* t, object, uintptr_t* arguments)
{
  return (cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
              ->vmClass()
              ->flags() & ACC_INTERFACE) != 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_isPrimitive(Thread* t, object, uintptr_t* arguments)
{
  return (cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
              ->vmClass()
              ->vmFlags() & PrimitiveFlag) != 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_isAnonymousClass(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcByteArray* name = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                          ->vmClass()
                          ->name();

  for (unsigned i = 0; i < name->length() - 1; ++i) {
    int c = name->body()[i];
    if (c != '$' and (c < '0' or c > '9')) {
      return false;
    }
  }

  return true;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getClassLoader(Thread* t,
                                         object,
                                         uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
          ->vmClass()
          ->loader());
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_isAssignableFrom(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcJclass* this_ = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  GcJclass* that = cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]));

  if (LIKELY(that)) {
    return isAssignableFrom(t, this_->vmClass(), that->vmClass());
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_isInstance(Thread* t, object, uintptr_t* arguments)
{
  GcJclass* this_ = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  object o = reinterpret_cast<object>(arguments[1]);

  if (o) {
    return instanceOf(t, this_->vmClass(), o);
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getDeclaredMethods(Thread* t,
                                             object,
                                             uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, c);

  bool publicOnly = arguments[1];

  GcMethod* get
      = resolveMethod(t,
                      roots(t)->bootLoader(),
                      "avian/Classes",
                      "getMethods",
                      "(Lavian/VMClass;Z)[Ljava/lang/reflect/Method;");

  return reinterpret_cast<uintptr_t>(
      t->m->processor->invoke(t, get, 0, c->vmClass(), publicOnly));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Class_getDeclaredFields(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcJclass* c = cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]));
  PROTECT(t, c);

  bool publicOnly = arguments[1];

  GcMethod* get = resolveMethod(t,
                                roots(t)->bootLoader(),
                                "avian/Classes",
                                "getFields",
                                "(Lavian/VMClass;Z)[Ljava/lang/reflect/Field;");

  return reinterpret_cast<uintptr_t>(
      t->m->processor->invoke(t, get, 0, c->vmClass(), publicOnly));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_invokeNative(Thread* t,
                                                object,
                                                uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[1]);
  object args = reinterpret_cast<object>(arguments[2]);
  GcMethod* method = cast<GcMethod>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[3]))
                        ->vmClass()
                        ->methodTable())->body()[arguments[6]]);

  return reinterpret_cast<uintptr_t>(invoke(t, method, instance, args));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_getMethodModifiers(Thread* t,
                                                      object,
                                                      uintptr_t* arguments)
{
  return cast<GcMethod>(
             t,
             cast<GcArray>(
                 t,
                 cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                     ->vmClass()
                     ->methodTable())->body()[arguments[1]])->flags();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_isAnnotationPresent(Thread* t,
                                                       object,
                                                       uintptr_t* arguments)
{
  GcMethod* method = cast<GcMethod>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                        ->vmClass()
                        ->methodTable())->body()[arguments[1]]);

  GcMethodAddendum* addendum = method->addendum();
  if (addendum) {
    object table = addendum->annotationTable();
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2])) {
          return true;
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_getAnnotation(Thread* t,
                                                 object,
                                                 uintptr_t* arguments)
{
  GcMethod* method = cast<GcMethod>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                        ->vmClass()
                        ->methodTable())->body()[arguments[1]]);

  GcMethodAddendum* addendum = method->addendum();
  if (addendum) {
    object table = addendum->annotationTable();
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2])) {
          PROTECT(t, method);
          PROTECT(t, table);

          GcMethod* get
              = resolveMethod(t,
                              roots(t)->bootLoader(),
                              "avian/Classes",
                              "getAnnotation",
                              "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
                              "Ljava/lang/annotation/Annotation;");

          return reinterpret_cast<uintptr_t>(
              t->m->processor->invoke(t,
                                      get,
                                      0,
                                      method->class_()->loader(),
                                      objectArrayBody(t, table, i)));
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_getDeclaredAnnotations(Thread* t,
                                                          object,
                                                          uintptr_t* arguments)
{
  GcMethod* method = cast<GcMethod>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                        ->vmClass()
                        ->methodTable())->body()[arguments[1]]);

  GcMethodAddendum* addendum = method->addendum();
  if (addendum) {
    object table = addendum->annotationTable();
    if (table) {
      PROTECT(t, method);
      PROTECT(t, table);

      object array = makeObjectArray(
          t,
          resolveClass(
              t, roots(t)->bootLoader(), "java/lang/annotation/Annotation"),
          objectArrayLength(t, table));
      PROTECT(t, array);

      GcMethod* get
          = resolveMethod(t,
                          roots(t)->bootLoader(),
                          "avian/Classes",
                          "getAnnotation",
                          "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
                          "Ljava/lang/annotation/Annotation;");
      PROTECT(t, get);

      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        object a = t->m->processor->invoke(t,
                                           get,
                                           0,
                                           method->class_()->loader(),
                                           objectArrayBody(t, table, i));

        setField(t, array, ArrayBody + (i * BytesPerWord), a);
      }

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>(makeObjectArray(
      t,
      resolveClass(
          t, roots(t)->bootLoader(), "java/lang/annotation/Annotation"),
      0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getDeclaredAnnotations(Thread* t,
                                                         object,
                                                         uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[1]]);

  GcFieldAddendum* addendum = field->addendum();
  if (addendum) {
    object table = addendum->annotationTable();
    if (table) {
      PROTECT(t, field);
      PROTECT(t, table);

      object array = makeObjectArray(
          t,
          resolveClass(
              t, roots(t)->bootLoader(), "java/lang/annotation/Annotation"),
          objectArrayLength(t, table));
      PROTECT(t, array);

      GcMethod* get
          = resolveMethod(t,
                          roots(t)->bootLoader(),
                          "avian/Classes",
                          "getAnnotation",
                          "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
                          "Ljava/lang/annotation/Annotation;");
      PROTECT(t, get);

      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        object a = t->m->processor->invoke(
            t,
            get,
            0,
            cast<GcClass>(t, reinterpret_cast<object>(arguments[0]))->loader(),
            objectArrayBody(t, table, i));

        setField(t, array, ArrayBody + (i * BytesPerWord), a);
      }

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>(makeObjectArray(
      t,
      resolveClass(
          t, roots(t)->bootLoader(), "java/lang/annotation/Annotation"),
      0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Method_getDefaultValue(Thread* t,
                                                   object,
                                                   uintptr_t* arguments)
{
  GcMethod* method = cast<GcMethod>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))
                        ->vmClass()
                        ->methodTable())->body()[arguments[2]]);

  GcMethodAddendum* addendum = method->addendum();
  if (addendum) {
    GcMethod* get
        = resolveMethod(t,
                        roots(t)->bootLoader(),
                        "avian/Classes",
                        "getAnnotationDefaultValue",
                        "(Ljava/lang/ClassLoader;Lavian/MethodAddendum;)"
                        "Ljava/lang/Object;");

    return reinterpret_cast<uintptr_t>(t->m->processor->invoke(
        t, get, 0, method->class_()->loader(), addendum));
  }

  return 0;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Constructor_constructNative(Thread* t,
                                                        object,
                                                        uintptr_t* arguments)
{
  object args = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, args);

  GcClass* c
      = cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))->vmClass();
  PROTECT(t, c);

  initClass(t, c);

  GcMethod* method = cast<GcMethod>(
      t, cast<GcArray>(t, c->methodTable())->body()[arguments[4]]);
  PROTECT(t, method);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  t->m->processor->invokeArray(t, method, instance, args);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getField(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[4]]);

  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return reinterpret_cast<intptr_t>(local::getField(t, field, instance));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getIField(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[4]]);

  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return cast<GcInt>(t, local::getField(t, field, instance))->value();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getJField(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[4]]);

  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  return cast<GcLong>(t, local::getField(t, field, instance))->value();
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_reflect_Field_setField(Thread* t,
                                           object,
                                           uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[4]]);

  PROTECT(t, field);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  object value = reinterpret_cast<object>(arguments[6]);
  PROTECT(t, value);

  local::setField(t, field, instance, value);
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_java_lang_reflect_Field_setIField(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[2]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[4]]);

  object instance = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, instance);

  object value = reinterpret_cast<object>(makeInt(t, arguments[7]));

  local::setField(t, field, instance, value);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getFieldModifiers(Thread* t,
                                                    object,
                                                    uintptr_t* arguments)
{
  return cast<GcField>(
             t,
             cast<GcArray>(
                 t,
                 cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))
                     ->vmClass()
                     ->fieldTable())->body()[arguments[2]])->flags();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getAnnotation(Thread* t,
                                                object,
                                                uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[1]]);

  GcFieldAddendum* addendum = field->addendum();
  if (addendum) {
    object table = addendum->annotationTable();
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        if (objectArrayBody(t, objectArrayBody(t, table, i), 1)
            == reinterpret_cast<object>(arguments[2])) {
          PROTECT(t, field);
          PROTECT(t, table);

          GcMethod* get
              = resolveMethod(t,
                              roots(t)->bootLoader(),
                              "avian/Classes",
                              "getAnnotation",
                              "(Ljava/lang/ClassLoader;[Ljava/lang/Object;)"
                              "Ljava/lang/annotation/Annotation;");

          return reinterpret_cast<uintptr_t>(
              t->m->processor->invoke(t,
                                      get,
                                      0,
                                      field->class_()->loader(),
                                      objectArrayBody(t, table, i)));
        }
      }
    }
  }

  return false;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Field_getSignatureAnnotation(Thread* t,
                                                         object,
                                                         uintptr_t* arguments)
{
  GcField* field = cast<GcField>(
      t,
      cast<GcArray>(t,
                    cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))
                        ->vmClass()
                        ->fieldTable())->body()[arguments[2]]);

  GcFieldAddendum* addendum = field->addendum();
  if (addendum) {
    GcByteArray* signature = cast<GcByteArray>(t, addendum->signature());
    if (signature) {
      object array = makeObjectArray(t, 1);
      PROTECT(t, array);

      GcString* string = t->m->classpath->makeString(
          t, signature, 0, signature->length() - 1);

      setField(t, array, ArrayBody, string);

      return reinterpret_cast<uintptr_t>(array);
    }
  }

  return reinterpret_cast<uintptr_t>(makeObjectArray(t, 0));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Throwable_nativeFillInStackTrace(Thread* t,
                                                     object,
                                                     uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(getTrace(t, 2));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_Throwable_nativeGetStackTrace(Thread* t,
                                                  object,
                                                  uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(
      local::translateStackTrace(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_makeMethod(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(local::makeMethodOrConstructor(
      t,
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_avian_Classes_makeField(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(local::makeField(
      t,
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_reflect_Array_createObjectArray(Thread* t,
                                                    object,
                                                    uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>(makeObjectArray(
      t,
      cast<GcJclass>(t, reinterpret_cast<object>(arguments[0]))->vmClass(),
      arguments[1]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_nio_ByteOrder_isLittleEndian(Thread*, object, uintptr_t*)
{
  return true;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_newNonMovableArray(Thread* t,
                                                     object,
                                                     uintptr_t* arguments)
{
  if (cast<GcJclass>(t, reinterpret_cast<object>(arguments[1]))->vmClass()
      == type(t, GcJbyte::Type)) {
    GcByteArray* array
        = reinterpret_cast<GcByteArray*>(allocate3(t,
                                                   t->m->heap,
                                                   Machine::FixedAllocation,
                                                   ArrayBody + arguments[2],
                                                   false));

    setObjectClass(t, array, type(t, GcByteArray::Type));
    array->length() = arguments[2];

    return reinterpret_cast<intptr_t>(array);
  } else {
    // todo
    abort(t);
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_dalvik_system_VMRuntime_addressOf(Thread*,
                                            object,
                                            uintptr_t* arguments)
{
  return arguments[1] + ArrayBody;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_libcore_io_Memory_pokeLong(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int64_t v;
  memcpy(&v, arguments + 2, 8);
  if (arguments[4]) {
    v = swapV8(v);
  }
  memcpy(reinterpret_cast<void*>(address), &v, 8);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Memory_peekLong(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int64_t v;
  memcpy(&v, reinterpret_cast<void*>(address), 8);
  return arguments[2] ? swapV8(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_libcore_io_Memory_pokeInt(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int32_t v = arguments[3] ? swapV4(arguments[2]) : arguments[2];
  memcpy(reinterpret_cast<void*>(address), &v, 4);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Memory_peekInt(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int32_t v;
  memcpy(&v, reinterpret_cast<void*>(address), 4);
  return arguments[2] ? swapV4(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_libcore_io_Memory_pokeShort(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int16_t v = arguments[3] ? swapV2(arguments[2]) : arguments[2];
  memcpy(reinterpret_cast<void*>(address), &v, 2);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Memory_peekShort(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  int16_t v;
  memcpy(&v, reinterpret_cast<void*>(address), 2);
  return arguments[2] ? swapV2(v) : v;
}

extern "C" AVIAN_EXPORT void JNICALL
    Avian_libcore_io_Memory_pokeByte(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  *reinterpret_cast<int8_t*>(address) = arguments[2];
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Memory_peekByte(Thread*, object, uintptr_t* arguments)
{
  int64_t address;
  memcpy(&address, arguments, 8);
  return *reinterpret_cast<int8_t*>(address);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_System_nanoTime(Thread* t, object, uintptr_t*)
{
  return t->m->system->now() * 1000 * 1000;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_System_currentTimeMillis(Thread* t, object, uintptr_t*)
{
  return t->m->system->now();
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_lang_System_identityHashCode(Thread* t,
                                            object,
                                            uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8(Thread*,
                                                               object,
                                                               uintptr_t*)
{
  return true;
}

#ifdef PLATFORM_WINDOWS

#include <io.h>

void register_java_io_Console(_JNIEnv*)
{
}
void register_java_lang_ProcessManager(_JNIEnv*)
{
}
void register_libcore_net_RawSocket(_JNIEnv*)
{
}
// void register_org_apache_harmony_xnet_provider_jsse_NativeCrypto(_JNIEnv*) {
// }

extern "C" AVIAN_EXPORT void JNICALL
    Avian_libcore_io_OsConstants_initConstants(Thread* t,
                                               object m,
                                               uintptr_t*)
{
  GcMethod* method = cast<GcMethod>(t, m);
  GcClass* c = method->class_();
  PROTECT(t, c);

  object table = c->staticTable();
  PROTECT(t, table);

  GcField* field = resolveField(t, c, "STDIN_FILENO", "I");
  fieldAtOffset<jint>(table, field->offset()) = 0;

  field = resolveField(t, c, "STDOUT_FILENO", "I");
  fieldAtOffset<jint>(table, field->offset()) = 1;

  field = resolveField(t, c, "STDERR_FILENO", "I");
  fieldAtOffset<jint>(table, field->offset()) = 2;
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Posix_getenv(Thread* t, object, uintptr_t* arguments)
{
  GcString* name = cast<GcString>(t, reinterpret_cast<object>(arguments[1]));

  THREAD_RUNTIME_ARRAY(t, uint16_t, chars, name->length(t) + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(chars));

  wchar_t* value
      = _wgetenv(reinterpret_cast<wchar_t*>(RUNTIME_ARRAY_BODY(chars)));

  if (value) {
    unsigned size = wcslen(value);

    GcCharArray* a = makeCharArray(t, size);
    if (size) {
      memcpy(a->body().begin(), value, size * sizeof(jchar));
    }

    return reinterpret_cast<uintptr_t>(
        t->m->classpath->makeString(t, a, 0, size));
  } else {
    return 0;
  }
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Posix_uname(Thread* t, object, uintptr_t*)
{
  GcClass* c
      = resolveClass(t, roots(t)->bootLoader(), "libcore/io/StructUtsname");
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

  setField(t,
      instance,
      resolveField(t, c, "machine", "Ljava/lang/String;")->offset(),
      arch);

  object platform = makeString(t, "Windows");

  setField(t,
      instance,
      resolveField(t, c, "sysname", "Ljava/lang/String;")->offset(),
      platform);

  object version = makeString(t, "unknown");

  setField(t,
      instance,
      resolveField(t, c, "release", "Ljava/lang/String;")->offset(),
      version);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" AVIAN_EXPORT int64_t JNICALL
    Avian_libcore_io_Posix_writeBytes(Thread* t, object, uintptr_t* arguments)
{
  object fd = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, fd);

  GcByteArray* buffer = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[2]));
  PROTECT(t, buffer);

  int offset = arguments[3];
  int count = arguments[4];

  int d = jniGetFDFromFileDescriptor(t, &fd);

  int r;
  if (objectClass(t, buffer) == type(t, GcByteArray::Type)) {
    void* tmp = t->m->heap->allocate(count);
    memcpy(tmp, &buffer->body()[offset], count);
    {
      ENTER(t, Thread::IdleState);
      r = _write(d, tmp, count);
    }
    t->m->heap->free(tmp, count);
  } else {
    void* p = local::getDirectBufferAddress(t, buffer);
    {
      ENTER(t, Thread::IdleState);
      r = _write(d, p, count);
    }
  }

  if (r < 0) {
    THREAD_RUNTIME_ARRAY(t, char, message, 256);
    throwNew(t,
             GcRuntimeException::Type,
             "writeBytes %d: %s",
             d,
             jniStrError(errno, RUNTIME_ARRAY_BODY(message), 0));
  } else {
    return r;
  }
}

#endif
