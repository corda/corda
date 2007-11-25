#ifndef PROCESSOR_H
#define PROCESSOR_H

#include "common.h"
#include "system.h"
#include "heap.h"

namespace vm {

class Processor {
 public:
  class StackWalker;

  class StackVisitor {
   public:
    virtual ~StackVisitor() { }

    virtual bool visit(StackWalker* walker) = 0;
  };

  class StackWalker {
   public:
    virtual ~StackWalker() { }

    virtual void walk(StackVisitor* v) = 0;

    virtual object method() = 0;

    virtual int ip() = 0;

    virtual unsigned count() = 0;
  };

  virtual ~Processor() { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent) = 0;

  virtual object
  makeMethod(Thread* t,
             uint8_t vmFlags,
             uint8_t returnCode,
             uint8_t parameterCount,
             uint8_t parameterFootprint,
             uint16_t flags,
             uint16_t offset,
             object name,
             object spec,
             object class_,
             object code) = 0;

  virtual object
  makeClass(Thread* t,
            uint16_t flags,
            uint8_t vmFlags,
            uint8_t arrayDimensions,
            uint16_t fixedSize,
            uint16_t arrayElementSize,
            object objectMask,
            object name,
            object super,
            object interfaceTable,
            object virtualTable,
            object fieldTable,
            object methodTable,
            object staticTable,
            object loader,
            unsigned vtableLength) = 0;

  virtual void
  initClass(Thread* t, object c) = 0;

  virtual void
  visitObjects(Thread* t, Heap::Visitor* v) = 0;

  virtual void
  walkStack(Thread* t, StackVisitor* v) = 0;

  virtual int
  lineNumber(Thread* t, object method, int ip) = 0;

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
