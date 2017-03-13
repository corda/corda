/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef PROCESSOR_H
#define PROCESSOR_H

#include "avian/common.h"
#include <avian/system/system.h>
#include <avian/heap/heap.h>
#include <avian/util/allocator.h>
#include "bootimage.h"
#include "avian/heapwalk.h"
#include "avian/zone.h"

namespace avian {
namespace codegen {
class DelayedPromise;
}

namespace util {
template <class T>
class Slice;
}
}

namespace vm {

class GcByteArray;
class GcCode;
class GcClass;
class GcMethod;
class GcMethodAddendum;
class GcIntArray;
class GcContinuation;
class GcThrowable;
class GcThread;
class GcClassAddendum;
class GcClassLoader;
class GcArray;
class GcSingleton;
class GcTriple;

class Processor {
 public:
  class StackWalker;

  class StackVisitor {
   public:
    virtual bool visit(StackWalker* walker) = 0;
  };

  class StackWalker {
   public:
    virtual void walk(StackVisitor* v) = 0;

    virtual GcMethod* method() = 0;

    virtual int ip() = 0;

    virtual unsigned count() = 0;
  };

  class CompilationHandler {
   public:
    virtual void compiled(const void* code,
                          unsigned size,
                          unsigned frameSize,
                          const char* name) = 0;

    virtual void dispose() = 0;
  };

  virtual Thread* makeThread(Machine* m, GcThread* javaThread, Thread* parent)
      = 0;

  virtual GcMethod* makeMethod(Thread* t,
                               uint8_t vmFlags,
                               uint8_t returnCode,
                               uint8_t parameterCount,
                               uint8_t parameterFootprint,
                               uint16_t flags,
                               uint16_t offset,
                               GcByteArray* name,
                               GcByteArray* spec,
                               GcMethodAddendum* addendum,
                               GcClass* class_,
                               GcCode* code) = 0;

  virtual GcClass* makeClass(Thread* t,
                             uint16_t flags,
                             uint16_t vmFlags,
                             uint16_t fixedSize,
                             uint8_t arrayElementSize,
                             uint8_t arrayDimensions,
                             GcClass* arrayElementClass,
                             GcIntArray* objectMask,
                             GcByteArray* name,
                             GcByteArray* sourceFile,
                             GcClass* super,
                             object interfaceTable,
                             object virtualTable,
                             object fieldTable,
                             object methodTable,
                             GcClassAddendum* addendum,
                             GcSingleton* staticTable,
                             GcClassLoader* loader,
                             unsigned vtableLength) = 0;

  virtual void initVtable(Thread* t, GcClass* c) = 0;

  virtual void visitObjects(Thread* t, Heap::Visitor* v) = 0;

  virtual void walkStack(Thread* t, StackVisitor* v) = 0;

  virtual int lineNumber(Thread* t, GcMethod* method, int ip) = 0;

  virtual object* makeLocalReference(Thread* t, object o) = 0;

  virtual void disposeLocalReference(Thread* t, object* r) = 0;

  virtual bool pushLocalFrame(Thread* t, unsigned capacity) = 0;

  virtual void popLocalFrame(Thread* t) = 0;

  virtual object invokeArray(Thread* t,
                             GcMethod* method,
                             object this_,
                             object arguments) = 0;

  virtual object invokeArray(Thread* t,
                             GcMethod* method,
                             object this_,
                             const jvalue* arguments) = 0;

  virtual object invokeList(Thread* t,
                            GcMethod* method,
                            object this_,
                            bool indirectObjects,
                            va_list arguments) = 0;

  virtual object invokeList(Thread* t,
                            GcClassLoader* loader,
                            const char* className,
                            const char* methodName,
                            const char* methodSpec,
                            object this_,
                            va_list arguments) = 0;

  virtual void dispose(Thread* t) = 0;

  virtual void dispose() = 0;

  virtual object getStackTrace(Thread* t, Thread* target) = 0;

  virtual void initialize(BootImage* image, avian::util::Slice<uint8_t> code)
      = 0;

  virtual void addCompilationHandler(CompilationHandler* handler) = 0;

  virtual void compileMethod(Thread* t,
                             Zone* zone,
                             GcTriple** constants,
                             GcTriple** calls,
                             avian::codegen::DelayedPromise** addresses,
                             GcMethod* method,
                             OffsetResolver* resolver,
                             Machine* hostVM) = 0;

  virtual void visitRoots(Thread* t, HeapWalker* w) = 0;

  virtual void normalizeVirtualThunks(Thread* t) = 0;

  virtual unsigned* makeCallTable(Thread* t, HeapWalker* w) = 0;

  virtual void boot(Thread* t, BootImage* image, uint8_t* code) = 0;

  virtual void callWithCurrentContinuation(Thread* t, object receiver) = 0;

  virtual void dynamicWind(Thread* t, object before, object thunk, object after)
      = 0;

  virtual void feedResultToContinuation(Thread* t,
                                        GcContinuation* continuation,
                                        object result) = 0;

  virtual void feedExceptionToContinuation(Thread* t,
                                           GcContinuation* continuation,
                                           GcThrowable* exception) = 0;

  virtual void walkContinuationBody(Thread* t,
                                    Heap::Walker* w,
                                    object o,
                                    unsigned start) = 0;

  object invoke(Thread* t, GcMethod* method, object this_, ...)
  {
    va_list a;
    va_start(a, this_);

    object r = invokeList(t, method, this_, false, a);

    va_end(a);

    return r;
  }

  object invoke(Thread* t,
                GcClassLoader* loader,
                const char* className,
                const char* methodName,
                const char* methodSpec,
                object this_,
                ...)
  {
    va_list a;
    va_start(a, this_);

    object r
        = invokeList(t, loader, className, methodName, methodSpec, this_, a);

    va_end(a);

    return r;
  }
};

Processor* makeProcessor(System* system,
                         avian::util::Allocator* allocator,
                         const char* crashDumpDirectory,
                         bool useNativeFeatures);

}  // namespace vm

#endif  // PROCESSOR_H
