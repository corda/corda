/* Copyright (c) 2010-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

struct JavaVM;

extern "C" int JNI_OnLoad(JavaVM*, void*);

#define _POSIX_C_SOURCE 200112L
#undef _GNU_SOURCE
#include "machine.h"
#include "classpath-common.h"
#include "process.h"

#include "util/runtime-array.h"

using namespace vm;

namespace {

namespace local {

void JNICALL
loadLibrary(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  unsigned length = stringLength(t, name);
  THREAD_RUNTIME_ARRAY(t, char, n, length + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  loadLibrary(t, "", RUNTIME_ARRAY_BODY(n), true, true);
}

int64_t JNICALL
appLoader(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::AppLoader));
}

class MyClasspath : public Classpath {
 public:
  MyClasspath(Allocator* allocator):
    allocator(allocator)
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
    const unsigned MaxPriority = 10;
    const unsigned NormalPriority = 5;

    object group;
    if (parent) {
      group = threadGroup(t, parent->javaThread);
    } else {
      group = allocate(t, FixedSizeOfThreadGroup, true);
      setObjectClass(t, group, type(t, Machine::ThreadGroupType));
      threadGroupMaxPriority(t, group) = MaxPriority;
    }

    PROTECT(t, group);
    object thread = allocate(t, FixedSizeOfThread, true);
    setObjectClass(t, thread, type(t, Machine::ThreadType));
    threadPriority(t, thread) = NormalPriority;
    threadGroup(t, thread) = group;
    PROTECT(t, thread);

    { object listClass = resolveClass
        (t, root(t, Machine::BootLoader), "java/util/ArrayList");
      PROTECT(t, listClass);

      object instance = makeNew(t, listClass);
      PROTECT(t, instance);

      object constructor = resolveMethod(t, listClass, "<init>", "()V");

      t->m->processor->invoke(t, constructor, instance);

      set(t, thread, ThreadInterruptActions, instance);
    }

    return thread;
  }

  virtual object
  makeJMethod(Thread* t, object)
  {
    abort(t); // todo
  }

  virtual object
  getVMMethod(Thread* t, object)
  {
    abort(t); // todo
  }

  virtual object
  makeJField(Thread* t, object)
  {
    abort(t); // todo
  }

  virtual object
  getVMField(Thread* t, object)
  {
    abort(t); // todo
  }

  virtual void
  clearInterrupted(Thread*)
  {
    // ignore
  }

  virtual void
  runThread(Thread* t)
  {
    object method = resolveMethod
      (t, root(t, Machine::BootLoader), "java/lang/Thread", "run",
       "(Ljava/lang/Thread;)V");

    t->m->processor->invoke(t, method, 0, t->javaThread);
  }

  virtual void
  resolveNative(Thread* t, object method)
  {
    vm::resolveNative(t, method);
  }

  virtual void
  boot(Thread* t)
  {
    { object runtimeClass = resolveClass
        (t, root(t, Machine::BootLoader), "java/lang/Runtime", false);

      if (runtimeClass) {
        PROTECT(t, runtimeClass);

        intercept(t, runtimeClass, "loadLibrary",
                  "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
                  voidPointer(loadLibrary));
      }
    }

    { object classLoaderClass = resolveClass
        (t, root(t, Machine::BootLoader), "java/lang/ClassLoader", false);

      if (classLoaderClass) {
        PROTECT(t, classLoaderClass);

        intercept(t, classLoaderClass, "createSystemClassLoader",
                  "()Ljava/lang/ClassLoader;",
                  voidPointer(appLoader));
      }
    }
    
    JNI_OnLoad(reinterpret_cast< ::JavaVM*>(t->m), 0);
  }

  virtual const char*
  bootClasspath()
  {
    return AVIAN_CLASSPATH;
  }

  virtual void
  updatePackageMap(Thread*, object)
  {
    // ignore
  }

  virtual object
  makeDirectByteBuffer(Thread* t, void* p, jlong capacity)
  {
    object c = resolveClass
      (t, root(t, Machine::BootLoader), "java/nio/ReadWriteDirectByteBuffer");
    PROTECT(t, c);

    object instance = makeNew(t, c);
    PROTECT(t, instance);

    object constructor = resolveMethod(t, c, "<init>", "(II)V");

    t->m->processor->invoke
      (t, constructor, instance, reinterpret_cast<int>(p),
       static_cast<int>(capacity));

    return instance;
  }

  virtual void*
  getDirectBufferAddress(Thread* t, object b)
  {
    PROTECT(t, b);

    object field = resolveField
      (t, objectClass(t, b), "effectiveDirectAddress", "I");

    return reinterpret_cast<void*>
      (fieldAtOffset<int32_t>(b, fieldOffset(t, field)));
  }

  virtual int64_t
  getDirectBufferCapacity(Thread* t, object b)
  {
    PROTECT(t, b);

    object field = resolveField
      (t, objectClass(t, b), "capacity", "I");

    return fieldAtOffset<int32_t>(b, fieldOffset(t, field));
  }

  virtual void
  dispose()
  {
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
};

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
  if (static_cast<int>(strerror_r(error, buffer, length)) == 0) {
    return buffer;
  } else {
    return 0;
  }
}

extern "C" int
__android_log_print(int priority, const char* tag,  const char* format, ...)
{
  va_list a;
  const unsigned size = 4096;
  char buffer[size];

  va_start(a, format);
  ::vsnprintf(buffer, size, format, a);
  va_end(a);

  return fprintf(stderr, "%d %s %s\n", priority, tag, buffer);
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

struct _JNIEnv;

int
register_org_apache_harmony_dalvik_NativeTestTarget(_JNIEnv*)
{
  // ignore
  return 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_String_isEmpty
(Thread* t, object, uintptr_t* arguments)
{
  return stringLength(t, reinterpret_cast<object>(arguments[0])) == 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_String_length
(Thread* t, object, uintptr_t* arguments)
{
  return stringLength(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_String_charAt
(Thread* t, object, uintptr_t* arguments)
{
  return stringCharAt(t, reinterpret_cast<object>(arguments[0]), arguments[1]);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_String_equals
(Thread* t, object, uintptr_t* arguments)
{
  return stringEqual(t, reinterpret_cast<object>(arguments[0]),
                     reinterpret_cast<object>(arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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
    object field = arrayBody
      (t, classFieldTable(t, jclassVmClass(t, c)), index);

    PROTECT(t, field);

    object type = resolveClassBySpec
      (t, classLoader(t, fieldClass(t, field)),
       reinterpret_cast<char*>
       (&byteArrayBody(t, fieldSpec(t, field), 0)),
       byteArrayLength(t, fieldSpec(t, field)) - 1);
    PROTECT(t, type);

    unsigned index = 0xFFFFFFFF;
    object table = classFieldTable(t, fieldClass(t, field));
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      if (field == arrayBody(t, table, i)) {
        index = i;
        break;
      }
    }

    return reinterpret_cast<uintptr_t>
      (makeJfield(t, 0, c, type, 0, 0, name, index));
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findLoadedVMClass
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Classes_defineVMClass
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_bootClassPath
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::BootLoader));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_classPath
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(root(t, Machine::AppLoader));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_vmVersion
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(makeString(t, "%s", AVIAN_VERSION));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_properties
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>
    (makeObjectArray(t, type(t, Machine::StringType), 0));
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_System_arraycopy
(Thread* t, object, uintptr_t* arguments)
{
  arrayCopy(t, reinterpret_cast<object>(arguments[0]),
            arguments[1],
            reinterpret_cast<object>(arguments[2]),
            arguments[3],
            arguments[4]);
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMThread_currentThread
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(t->javaThread);
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Math_min
(Thread*, object, uintptr_t* arguments)
{
  return min(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Math_max
(Thread*, object, uintptr_t* arguments)
{
  return max(static_cast<int>(arguments[0]), static_cast<int>(arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_getClass
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (getJClass(t, objectClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_hashCode
(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_internalClone
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (clone(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_getModifiers
(Thread* t, object, uintptr_t* arguments)
{
  return classFlags
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_desiredAssertionStatus
(Thread*, object, uintptr_t*)
{
  return 1;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_getNameNative
(Thread* t, object, uintptr_t* arguments)
{
  object name = className
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));

  return reinterpret_cast<uintptr_t>
    (t->m->classpath->makeString(t, name, 0, byteArrayLength(t, name) - 1));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_isInterface
(Thread* t, object, uintptr_t* arguments)
{
  return (classFlags
          (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])))
          & ACC_INTERFACE) != 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_isPrimitive
(Thread* t, object, uintptr_t* arguments)
{
  return (classVmFlags
          (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])))
          & PrimitiveFlag) != 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_getClassLoader
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (classLoader
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_isAssignableFrom
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object that = reinterpret_cast<object>(arguments[1]);

  if (LIKELY(that)) {
    return vm::isAssignableFrom
      (t, jclassVmClass(t, this_), jclassVmClass(t, that));
  } else {
    throwNew(t, Machine::NullPointerExceptionType);
  }
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getMethodModifiers
(Thread* t, object, uintptr_t* arguments)
{
  return methodFlags
    (t, arrayBody
     (t, classMethodTable
      (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0]))),
      arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Constructor_constructNative
(Thread* t, object, uintptr_t* arguments)
{
  object args = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, args);

  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[2]));

  object method = arrayBody(t, classMethodTable(t, c), arguments[4]);
  PROTECT(t, method);

  object instance = makeNew(t, c);
  PROTECT(t, instance);

  t->m->processor->invokeArray(t, method, instance, args);

  return reinterpret_cast<uintptr_t>(instance);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Throwable_nativeFillInStackTrace
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>(getTrace(t, 2));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_Classes_makeMethod
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (local::makeMethodOrConstructor
     (t, reinterpret_cast<object>(arguments[0]), arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Array_createObjectArray
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<uintptr_t>
    (makeObjectArray
     (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])),
      arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_nio_ByteOrder_isLittleEndian
(Thread*, object, uintptr_t*)
{
#ifdef ARCH_powerpc
  return false;
#else
  return true;
#endif
}

extern "C" JNIEXPORT int64_t JNICALL
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

extern "C" JNIEXPORT int64_t JNICALL
Avian_dalvik_system_VMRuntime_addressOf
(Thread*, object, uintptr_t* arguments)
{
  return arguments[1] + ArrayBody;
}

extern "C" JNIEXPORT void JNICALL
Avian_libcore_io_Memory_pokeLong
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments + 1, 8);
  if (arguments[3]) {
    v = swapV8(v);
  }
  memcpy(reinterpret_cast<void*>(arguments[0]), &v, 8);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekLong
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, reinterpret_cast<void*>(arguments[0]), 8);
  return arguments[1] ? swapV8(v) : v;
}

extern "C" JNIEXPORT void JNICALL
Avian_libcore_io_Memory_pokeInt
(Thread*, object, uintptr_t* arguments)
{
  int32_t v = arguments[2] ? swapV4(arguments[1]) : arguments[1];
  memcpy(reinterpret_cast<void*>(arguments[0]), &v, 4);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekInt
(Thread*, object, uintptr_t* arguments)
{
  int32_t v; memcpy(&v, reinterpret_cast<void*>(arguments[0]), 4);
  return arguments[1] ? swapV4(v) : v;
}

extern "C" JNIEXPORT void JNICALL
Avian_libcore_io_Memory_pokeShort
(Thread*, object, uintptr_t* arguments)
{
  int16_t v = arguments[2] ? swapV2(arguments[1]) : arguments[1];
  memcpy(reinterpret_cast<void*>(arguments[0]), &v, 2);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekShort
(Thread*, object, uintptr_t* arguments)
{
  int16_t v; memcpy(&v, reinterpret_cast<void*>(arguments[0]), 2);
  return arguments[1] ? swapV2(v) : v;
}

extern "C" JNIEXPORT void JNICALL
Avian_libcore_io_Memory_pokeByte
(Thread*, object, uintptr_t* arguments)
{
  *reinterpret_cast<int8_t*>(arguments[0]) = arguments[1];
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_libcore_io_Memory_peekByte
(Thread*, object, uintptr_t* arguments)
{
  return *reinterpret_cast<int8_t*>(arguments[0]);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_nanoTime
(Thread* t, object, uintptr_t*)
{
  return t->m->system->now() * 1000 * 1000;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_currentTimeMillis
(Thread* t, object, uintptr_t*)
{
  return t->m->system->now();
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_identityHashCode
(Thread* t, object, uintptr_t* arguments)
{
  return objectHash(t, reinterpret_cast<object>(arguments[0]));
}
