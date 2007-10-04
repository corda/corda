#ifndef PROCESSOR_H
#define PROCESSOR_H

#include "common.h"
#include "system.h"
#include "heap.h"

namespace vm {

class Processor {
 public:
  virtual ~Processor() { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent) = 0;

  virtual void*
  methodStub(Thread* t) = 0;

  virtual void*
  nativeInvoker(Thread* t) = 0;

  virtual unsigned
  parameterFootprint(Thread* t, const char* spec, bool static_) = 0;

  virtual void
  initClass(Thread* t, object c) = 0;

  virtual void
  visitObjects(Thread* t, Heap::Visitor* v) = 0;

  virtual uintptr_t
  frameStart(Thread* t) = 0;

  virtual uintptr_t
  frameNext(Thread* t, uintptr_t frame) = 0;

  virtual bool
  frameValid(Thread* t, uintptr_t frame) = 0;

  virtual object
  frameMethod(Thread* t, uintptr_t frame) = 0;

  virtual unsigned
  frameIp(Thread* t, uintptr_t frame) = 0;

  virtual int
  lineNumber(Thread* t, object method, unsigned ip) = 0;

  virtual object*
  makeLocalReference(Thread* t, object o) = 0;

  virtual void
  disposeLocalReference(Thread* t, object* r) = 0;

  virtual object
  invokeArray(Thread* t, object method, object this_, object arguments) = 0;

  virtual object
  invokeList(Thread* t, object method, object this_, bool indirectObjects,
             va_list arguments) = 0;

  virtual object
  invokeList(Thread* t, const char* className, const char* methodName,
             const char* methodSpec, object this_, va_list arguments) = 0;

  virtual void
  dispose() = 0;

  object
  invoke(Thread* t, object method, object this_, ...)
  {
    va_list a;
    va_start(a, this_);

    object r = invokeList(t, method, this_, false, a);

    va_end(a);

    return r;
  }

  object
  invoke(Thread* t, const char* className, const char* methodName,
         const char* methodSpec, object this_, ...)
  {
    va_list a;
    va_start(a, this_);

    object r = invokeList(t, className, methodName, methodSpec, this_, a);

    va_end(a);

    return r;
  }
};

Processor*
makeProcessor(System* system);

} // namespace vm

#endif//PROCESSOR_H
