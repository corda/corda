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

extern "C" struct _JNIEnv;

int
register_org_apache_harmony_dalvik_NativeTestTarget(_JNIEnv*)
{
  // ignore
  return 0;
}
