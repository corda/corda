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

  virtual void
  dispose()
  {
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
};

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
Avian_java_lang_Class_getDeclaredField
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, c);

  object name = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, name);
  
  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Android", "findField",
     "(Lavian/VMClass;Ljava/lang/String;)Lavian/VMField;");

  object field = t->m->processor->invoke
    (t, method, 0, jclassVmClass(t, c), name);

  if (field) {
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
