#ifndef PROCESSOR_H
#define PROCESSOR_H

#include "common.h"
#include "system.h"
#include "heap.h"

namespace vm {

class FrameIterator {
 public:
  FrameIterator():
    base(0),
    method(0),
    ip(0)
  { }

  FrameIterator(FrameIterator* it):
    base(it->base),
    method(it->method),
    ip(it->ip)
  { }

  bool valid() {
    return base != 0;
  }

  uintptr_t base;
  object method;
  unsigned ip;
};

class Processor {
 public:
  virtual ~Processor() { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent) = 0;

  virtual void
  visitObjects(Thread* t, Heap::Visitor* v) = 0;

  virtual void 
  start(Thread* t, FrameIterator* it) = 0;

  virtual void 
  next(Thread* t, FrameIterator* it) = 0;

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
