/* Copyright (c) 2010-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

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
  boot(Thread*)
  {
    // ignore
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
