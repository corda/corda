/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/machine.h"
#include "avian/util.h"
#include "avian/alloc-vector.h"
#include "avian/process.h"
#include "avian/target.h"
#include "avian/arch.h"

#include <avian/system/memory.h>

#include <avian/codegen/assembler.h>
#include <avian/codegen/architecture.h>
#include <avian/codegen/compiler.h>
#include <avian/codegen/targets.h>
#include <avian/codegen/lir.h>
#include <avian/codegen/runtime.h>

#include <avian/util/runtime-array.h>
#include <avian/util/list.h>
#include <avian/util/slice.h>
#include <avian/util/fixed-allocator.h>

#include "debug-util.h"

using namespace vm;

extern "C" uint64_t vmInvoke(void* thread,
                             void* function,
                             void* arguments,
                             unsigned argumentFootprint,
                             unsigned frameSize,
                             unsigned returnType);

extern "C" void vmInvoke_returnAddress();

extern "C" void vmInvoke_safeStack();

extern "C" void vmJumpAndInvoke(void* thread,
                                void* function,
                                void* stack,
                                unsigned argumentFootprint,
                                uintptr_t* arguments,
                                unsigned frameSize);

using namespace avian::codegen;
using namespace avian::system;

namespace {

namespace local {

const bool DebugCompile = false;
const bool DebugNatives = false;
const bool DebugCallTable = false;
const bool DebugMethodTree = false;
const bool DebugInstructions = false;

#ifndef AVIAN_AOT_ONLY
const bool DebugFrameMaps = false;
const bool CheckArrayBounds = true;
const unsigned ExecutableAreaSizeInBytes = 30 * 1024 * 1024;
#endif

#ifdef AVIAN_CONTINUATIONS
const bool Continuations = true;
#else
const bool Continuations = false;
#endif

const unsigned MaxNativeCallFootprint = TargetBytesPerWord == 8 ? 4 : 5;

const unsigned InitialZoneCapacityInBytes = 64 * 1024;

enum ThunkIndex {
  compileMethodIndex,
  compileVirtualMethodIndex,
  linkDynamicMethodIndex,
  invokeNativeIndex,
  throwArrayIndexOutOfBoundsIndex,
  throwStackOverflowIndex,

#define THUNK(s) s##Index,
#include "thunks.cpp"
#undef THUNK
  dummyIndex
};

inline bool isVmInvokeUnsafeStack(void* ip)
{
  return reinterpret_cast<uintptr_t>(ip)
         >= reinterpret_cast<uintptr_t>(voidPointer(vmInvoke_returnAddress))
         and reinterpret_cast<uintptr_t>(ip)
             < reinterpret_cast<uintptr_t>(voidPointer(vmInvoke_safeStack));
}

class MyThread;

void* getIp(MyThread*);

class MyThread : public Thread {
 public:
  class CallTrace {
   public:
    CallTrace(MyThread* t, GcMethod* method)
        : t(t),
          ip(getIp(t)),
          stack(t->stack),
          scratch(t->scratch),
          continuation(t->continuation),
          nativeMethod((method->flags() & ACC_NATIVE) ? method : 0),
          targetMethod(0),
          originalMethod(method),
          next(t->trace)
    {
      doTransition(t, 0, 0, 0, this);
    }

    ~CallTrace()
    {
      assertT(t, t->stack == 0);

      t->scratch = scratch;

      doTransition(t, ip, stack, continuation, next);
    }

    MyThread* t;
    void* ip;
    void* stack;
    void* scratch;
    GcContinuation* continuation;
    GcMethod* nativeMethod;
    GcMethod* targetMethod;
    GcMethod* originalMethod;
    CallTrace* next;
  };

  class Context {
   public:
    class MyProtector : public Thread::Protector {
     public:
      MyProtector(MyThread* t, Context* context)
          : Protector(t), context(context)
      {
      }

      virtual void visit(Heap::Visitor* v)
      {
        v->visit(&(context->continuation));
      }

      Context* context;
    };

    Context(MyThread* t,
            void* ip,
            void* stack,
            GcContinuation* continuation,
            CallTrace* trace)
        : ip(ip),
          stack(stack),
          continuation(continuation),
          trace(trace),
          protector(t, this)
    {
    }

    void* ip;
    void* stack;
    GcContinuation* continuation;
    CallTrace* trace;
    MyProtector protector;
  };

  class TraceContext : public Context {
   public:
    TraceContext(MyThread* t,
                 void* ip,
                 void* stack,
                 GcContinuation* continuation,
                 CallTrace* trace)
        : Context(t, ip, stack, continuation, trace),
          t(t),
          link(0),
          next(t->traceContext),
          methodIsMostRecent(false)
    {
      t->traceContext = this;
    }

    TraceContext(MyThread* t, void* link)
        : Context(t, t->ip, t->stack, t->continuation, t->trace),
          t(t),
          link(link),
          next(t->traceContext),
          methodIsMostRecent(false)
    {
      t->traceContext = this;
    }

    ~TraceContext()
    {
      t->traceContext = next;
    }

    MyThread* t;
    void* link;
    TraceContext* next;
    bool methodIsMostRecent;
  };

  static void doTransition(MyThread* t,
                           void* ip,
                           void* stack,
                           GcContinuation* continuation,
                           MyThread::CallTrace* trace)
  {
    // in this function, we "atomically" update the thread context
    // fields in such a way to ensure that another thread may
    // interrupt us at any time and still get a consistent, accurate
    // stack trace.  See MyProcessor::getStackTrace for details.

    assertT(t, t->transition == 0);

    Context c(t, ip, stack, continuation, trace);

    compileTimeMemoryBarrier();

    t->transition = &c;

    compileTimeMemoryBarrier();

    t->ip = ip;
    t->stack = stack;
    t->continuation = continuation;
    t->trace = trace;

    compileTimeMemoryBarrier();

    t->transition = 0;
  }

  MyThread(Machine* m,
           GcThread* javaThread,
           MyThread* parent,
           bool useNativeFeatures)
      : Thread(m, javaThread, parent),
        ip(0),
        stack(0),
        newStack(0),
        scratch(0),
        continuation(0),
        exceptionStackAdjustment(0),
        exceptionOffset(0),
        exceptionHandler(0),
        tailAddress(0),
        virtualCallTarget(0),
        virtualCallIndex(0),
        heapImage(0),
        codeImage(0),
        thunkTable(0),
        dynamicTable(0),
        trace(0),
        reference(0),
        arch(parent ? parent->arch : avian::codegen::makeArchitectureNative(
                                         m->system,
                                         useNativeFeatures)),
        transition(0),
        traceContext(0),
        stackLimit(0),
        referenceFrame(0),
        methodLockIsClean(true)
  {
    arch->acquire();
  }

  void* ip;
  void* stack;
  void* newStack;
  void* scratch;
  GcContinuation* continuation;
  uintptr_t exceptionStackAdjustment;
  uintptr_t exceptionOffset;
  void* exceptionHandler;
  void* tailAddress;
  void* virtualCallTarget;
  uintptr_t virtualCallIndex;
  uintptr_t* heapImage;
  uint8_t* codeImage;
  void** thunkTable;
  void** dynamicTable;
  CallTrace* trace;
  Reference* reference;
  avian::codegen::Architecture* arch;
  Context* transition;
  TraceContext* traceContext;
  uintptr_t stackLimit;
  List<Reference*>* referenceFrame;
  bool methodLockIsClean;
};

void transition(MyThread* t,
                void* ip,
                void* stack,
                GcContinuation* continuation,
                MyThread::CallTrace* trace)
{
  MyThread::doTransition(t, ip, stack, continuation, trace);
}

object resolveThisPointer(MyThread* t, void* stack)
{
  return reinterpret_cast<object*>(
      stack)[t->arch->frameFooterSize() + t->arch->frameReturnAddressSize()];
}

GcMethod* findMethod(Thread* t, GcMethod* method, object instance)
{
  if ((method->flags() & ACC_STATIC) == 0) {
    if (method->class_()->flags() & ACC_INTERFACE) {
      return findInterfaceMethod(t, method, objectClass(t, instance));
    } else if (methodVirtual(t, method)) {
      return findVirtualMethod(t, method, objectClass(t, instance));
    }
  }
  return method;
}

GcMethod* resolveTarget(MyThread* t, void* stack, GcMethod* method)
{
  GcClass* class_ = objectClass(t, resolveThisPointer(t, stack));

  if (class_->vmFlags() & BootstrapFlag) {
    PROTECT(t, method);
    PROTECT(t, class_);

    resolveSystemClass(t, roots(t)->bootLoader(), class_->name());
  }

  if (method->class_()->flags() & ACC_INTERFACE) {
    return findInterfaceMethod(t, method, class_);
  } else {
    return findVirtualMethod(t, method, class_);
  }
}

GcMethod* resolveTarget(MyThread* t, GcClass* class_, unsigned index)
{
  if (class_->vmFlags() & BootstrapFlag) {
    PROTECT(t, class_);

    resolveSystemClass(t, roots(t)->bootLoader(), class_->name());
  }

  return cast<GcMethod>(
      t, cast<GcArray>(t, class_->virtualTable())->body()[index]);
}

GcCompileRoots* compileRoots(Thread* t);

intptr_t methodCompiled(Thread* t UNUSED, GcMethod* method)
{
  return method->code()->compiled();
}

unsigned methodCompiledSize(Thread* t UNUSED, GcMethod* method)
{
  return method->code()->compiledSize();
}

intptr_t compareIpToMethodBounds(Thread* t, intptr_t ip, object om)
{
  GcMethod* method = cast<GcMethod>(t, om);
  intptr_t start = methodCompiled(t, method);

  if (DebugMethodTree) {
    fprintf(stderr,
            "find %p in (%p,%p)\n",
            reinterpret_cast<void*>(ip),
            reinterpret_cast<void*>(start),
            reinterpret_cast<void*>(start + methodCompiledSize(t, method)));
  }

  if (ip < start) {
    return -1;
  } else if (ip < start
                  + static_cast<intptr_t>(methodCompiledSize(t, method))) {
    return 0;
  } else {
    return 1;
  }
}

GcMethod* methodForIp(MyThread* t, void* ip)
{
  if (DebugMethodTree) {
    fprintf(stderr, "query for method containing %p\n", ip);
  }

  // we must use a version of the method tree at least as recent as the
  // compiled form of the method containing the specified address (see
  // compile(MyThread*, FixedAllocator*, BootContext*, object)):
  loadMemoryBarrier();

  return cast<GcMethod>(t,
                        treeQuery(t,
                                  compileRoots(t)->methodTree(),
                                  reinterpret_cast<intptr_t>(ip),
                                  compileRoots(t)->methodTreeSentinal(),
                                  compareIpToMethodBounds));
}

unsigned localSize(MyThread* t UNUSED, GcMethod* method)
{
  unsigned size = method->code()->maxLocals();
  if ((method->flags() & (ACC_SYNCHRONIZED | ACC_STATIC)) == ACC_SYNCHRONIZED) {
    ++size;
  }
  return size;
}

unsigned alignedFrameSize(MyThread* t, GcMethod* method)
{
  return t->arch->alignFrameSize(
      localSize(t, method) - method->parameterFootprint()
      + method->code()->maxStack()
      + t->arch->frameFootprint(MaxNativeCallFootprint));
}

void nextFrame(MyThread* t,
               void** ip,
               void** sp,
               GcMethod* method,
               GcMethod* target,
               bool mostRecent)
{
  GcCode* code = method->code();
  intptr_t start = code->compiled();
  void* link;
  bool methodIsMostRecent;

  if (t->traceContext) {
    link = t->traceContext->link;
    methodIsMostRecent = mostRecent and t->traceContext->methodIsMostRecent;
  } else {
    link = 0;
    methodIsMostRecent = false;
  }

  if (false) {
    fprintf(stderr,
            "nextFrame %s.%s%s target %s.%s%s ip %p sp %p\n",
            method->class_()->name()->body().begin(),
            method->name()->body().begin(),
            method->spec()->body().begin(),
            target ? target->class_()->name()->body().begin() : 0,
            target ? target->name()->body().begin() : 0,
            target ? target->spec()->body().begin() : 0,
            *ip,
            *sp);
  }

  t->arch->nextFrame(reinterpret_cast<void*>(start),
                     code->compiledSize(),
                     alignedFrameSize(t, method),
                     link,
                     methodIsMostRecent,
                     target ? target->parameterFootprint() : -1,
                     ip,
                     sp);

  if (false) {
    fprintf(stderr, "next frame ip %p sp %p\n", *ip, *sp);
  }
}

void* getIp(MyThread* t, void* ip, void* stack)
{
  // Here we use the convention that, if the return address is neither
  // pushed on to the stack automatically as part of the call nor
  // stored in the caller's frame, it will be saved in MyThread::ip
  // instead of on the stack.  See the various implementations of
  // Assembler::saveFrame for details on how this is done.
  return t->arch->returnAddressOffset() < 0 ? ip : t->arch->frameIp(stack);
}

void* getIp(MyThread* t)
{
  return getIp(t, t->ip, t->stack);
}

class MyStackWalker : public Processor::StackWalker {
 public:
  enum State { Start, Next, Trace, Continuation, Method, NativeMethod, Finish };

  class MyProtector : public Thread::Protector {
   public:
    MyProtector(MyStackWalker* walker) : Protector(walker->t), walker(walker)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      v->visit(&(walker->method_));
      v->visit(&(walker->target));
      v->visit(&(walker->continuation));
    }

    MyStackWalker* walker;
  };

  MyStackWalker(MyThread* t)
      : t(t), state(Start), method_(0), target(0), count_(0), protector(this)
  {
    if (t->traceContext) {
      ip_ = t->traceContext->ip;
      stack = t->traceContext->stack;
      trace = t->traceContext->trace;
      continuation = t->traceContext->continuation;
    } else {
      ip_ = getIp(t);
      stack = t->stack;
      trace = t->trace;
      continuation = t->continuation;
    }
  }

  MyStackWalker(MyStackWalker* w)
      : t(w->t),
        state(w->state),
        ip_(w->ip_),
        stack(w->stack),
        trace(w->trace),
        method_(w->method_),
        target(w->target),
        continuation(w->continuation),
        count_(w->count_),
        protector(this)
  {
  }

  virtual void walk(Processor::StackVisitor* v)
  {
    for (MyStackWalker it(this); it.valid();) {
      MyStackWalker walker(&it);
      if (not v->visit(&walker)) {
        break;
      }
      it.next();
    }
  }

  bool valid()
  {
    while (true) {
      if (false) {
        fprintf(stderr, "state: %d\n", state);
      }
      switch (state) {
      case Start:
        if (trace and trace->nativeMethod) {
          method_ = trace->nativeMethod;
          state = NativeMethod;
        } else {
          state = Next;
        }
        break;

      case Next:
        if (stack) {
          target = method_;
          method_ = methodForIp(t, ip_);
          if (method_) {
            state = Method;
          } else if (continuation) {
            method_ = continuation->method();
            state = Continuation;
          } else {
            state = Trace;
          }
        } else {
          state = Trace;
        }
        break;

      case Trace: {
        if (trace) {
          continuation = trace->continuation;
          stack = trace->stack;
          ip_ = trace->ip;
          trace = trace->next;

          state = Start;
        } else {
          state = Finish;
        }
      } break;

      case Continuation:
      case Method:
      case NativeMethod:
        return true;

      case Finish:
        return false;

      default:
        abort(t);
      }
    }
  }

  void next()
  {
    expect(t, count_ <= stackSizeInWords(t));

    switch (state) {
    case Continuation:
      continuation = continuation->next();
      break;

    case Method:
      nextFrame(t, &ip_, &stack, method_, target, count_ == 0);
      break;

    case NativeMethod:
      break;

    default:
      abort(t);
    }

    ++count_;

    state = Next;
  }

  virtual GcMethod* method()
  {
    if (false) {
      fprintf(stderr,
              "method %s.%s\n",
              method_->class_()->name()->body().begin(),
              method_->name()->body().begin());
    }
    return method_;
  }

  virtual int ip()
  {
    switch (state) {
    case Continuation:
      return reinterpret_cast<intptr_t>(continuation->address())
             - methodCompiled(t, continuation->method());

    case Method:
      return reinterpret_cast<intptr_t>(ip_) - methodCompiled(t, method_);

    case NativeMethod:
      return 0;

    default:
      abort(t);
    }
  }

  virtual unsigned count()
  {
    unsigned count = 0;

    for (MyStackWalker walker(this); walker.valid();) {
      walker.next();
      ++count;
    }

    return count;
  }

  MyThread* t;
  State state;
  void* ip_;
  void* stack;
  MyThread::CallTrace* trace;
  GcMethod* method_;
  GcMethod* target;
  GcContinuation* continuation;
  unsigned count_;
  MyProtector protector;
};

int localOffset(MyThread* t, int v, GcMethod* method)
{
  int parameterFootprint = method->parameterFootprint();
  int frameSize = alignedFrameSize(t, method);

  int offset
      = ((v < parameterFootprint)
             ? (frameSize + parameterFootprint + t->arch->frameFooterSize()
                + t->arch->frameHeaderSize() - v - 1)
             : (frameSize + parameterFootprint - v - 1));

  assertT(t, offset >= 0);
  return offset;
}

int localOffsetFromStack(MyThread* t, int index, GcMethod* method)
{
  return localOffset(t, index, method) + t->arch->frameReturnAddressSize();
}

object* localObject(MyThread* t, void* stack, GcMethod* method, unsigned index)
{
  return static_cast<object*>(stack) + localOffsetFromStack(t, index, method);
}

int stackOffsetFromFrame(MyThread* t, GcMethod* method)
{
  return alignedFrameSize(t, method) + t->arch->frameHeaderSize();
}

void* stackForFrame(MyThread* t, void* frame, GcMethod* method)
{
  return static_cast<void**>(frame) - stackOffsetFromFrame(t, method);
}

class PoolElement : public avian::codegen::Promise {
 public:
  PoolElement(Thread* t, object target, PoolElement* next)
      : t(t), target(target), address(0), next(next)
  {
  }

  virtual int64_t value()
  {
    assertT(t, resolved());
    return address;
  }

  virtual bool resolved()
  {
    return address != 0;
  }

  Thread* t;
  object target;
  intptr_t address;
  PoolElement* next;
};

class Subroutine {
 public:
  Subroutine(unsigned index,
             unsigned returnAddress,
             unsigned methodSize,
             Subroutine* outer)
      : index(index),
        outer(outer),
        returnAddress(returnAddress),
        duplicatedBaseIp(methodSize * index),
        visited(false)
  {
  }

  // Index of this subroutine, in the (unmaterialized) list of subroutines in
  // this method.
  // Note that in the presence of nested finallys, this could theoretically end
  // up being greater than the number of jsr instructions (but this will be
  // extremely rare - I don't think we've seen this in practice).
  const unsigned index;

  // Subroutine outer to this one (if, for instance, we have nested finallys)
  Subroutine* const outer;

  // Starting ip in the original bytecode (always < original bytecode size)
  const unsigned returnAddress;

  // Starting ip for this subroutine's copy of the method bytecode
  const unsigned duplicatedBaseIp;

  bool visited;
};

class Context;

class TraceElement : public avian::codegen::TraceHandler {
 public:
  static const unsigned VirtualCall = 1 << 0;
  static const unsigned TailCall = 1 << 1;
  static const unsigned LongCall = 1 << 2;

  TraceElement(Context* context,
               unsigned ip,
               GcMethod* target,
               unsigned flags,
               TraceElement* next,
               unsigned mapSize)
      : context(context),
        address(0),
        next(next),
        target(target),
        ip(ip),
        argumentIndex(0),
        flags(flags),
        watch(false)
  {
    memset(map, 0xFF, mapSize * BytesPerWord);
  }

  virtual void handleTrace(avian::codegen::Promise* address,
                           unsigned argumentIndex)
  {
    if (this->address == 0) {
      this->address = address;
      this->argumentIndex = argumentIndex;
    }
  }

  Context* context;
  avian::codegen::Promise* address;
  TraceElement* next;
  GcMethod* target;
  unsigned ip;
  unsigned argumentIndex;
  unsigned flags;
  bool watch;
  uintptr_t map[0];
};

class TraceElementPromise : public avian::codegen::Promise {
 public:
  TraceElementPromise(System* s, TraceElement* trace) : s(s), trace(trace)
  {
  }

  virtual int64_t value()
  {
    assertT(s, resolved());
    return trace->address->value();
  }

  virtual bool resolved()
  {
    return trace->address != 0 and trace->address->resolved();
  }

  System* s;
  TraceElement* trace;
};

enum Event {
  PushContextEvent,
  PopContextEvent,
  IpEvent,
  MarkEvent,
  ClearEvent,
  PushExceptionHandlerEvent,
  TraceEvent,
};

unsigned frameMapSizeInBits(MyThread* t, GcMethod* method)
{
  return localSize(t, method) + method->code()->maxStack();
}

unsigned frameMapSizeInWords(MyThread* t, GcMethod* method)
{
  return ceilingDivide(frameMapSizeInBits(t, method), BitsPerWord);
}

enum Thunk {
#define THUNK(s) s##Thunk,

#include "thunks.cpp"

#undef THUNK
};

const unsigned ThunkCount = idleIfNecessaryThunk + 1;

intptr_t getThunk(MyThread* t, Thunk thunk);

class BootContext {
 public:
  class MyProtector : public Thread::Protector {
   public:
    MyProtector(Thread* t, BootContext* c) : Protector(t), c(c)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      v->visit(&(c->constants));
      v->visit(&(c->calls));
    }

    BootContext* c;
  };

  BootContext(Thread* t,
              GcTriple* constants,
              GcTriple* calls,
              avian::codegen::DelayedPromise* addresses,
              Zone* zone,
              OffsetResolver* resolver,
              JavaVM* hostVM)
      : protector(t, this),
        constants(constants),
        calls(calls),
        addresses(addresses),
        addressSentinal(addresses),
        zone(zone),
        resolver(resolver),
        hostVM(hostVM)
  {
  }

  MyProtector protector;
  GcTriple* constants;
  GcTriple* calls;
  avian::codegen::DelayedPromise* addresses;
  avian::codegen::DelayedPromise* addressSentinal;
  Zone* zone;
  OffsetResolver* resolver;
  JavaVM* hostVM;
};

class Context {
 public:
  class MyResource : public Thread::AutoResource {
   public:
    MyResource(Context* c) : AutoResource(c->thread), c(c)
    {
    }

    virtual void release()
    {
      c->dispose();
    }

    Context* c;
  };

  class MyProtector : public Thread::Protector {
   public:
    MyProtector(Context* c) : Protector(c->thread), c(c)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      v->visit(&(c->method));

      for (PoolElement* p = c->objectPool; p; p = p->next) {
        v->visit(&(p->target));
      }

      for (TraceElement* p = c->traceLog; p; p = p->next) {
        v->visit(&(p->target));
      }
    }

    Context* c;
  };

  class MyClient : public Compiler::Client {
   public:
    MyClient(MyThread* t) : t(t)
    {
    }

    virtual intptr_t getThunk(avian::codegen::lir::UnaryOperation, unsigned)
    {
      abort(t);
    }

    virtual intptr_t getThunk(avian::codegen::lir::BinaryOperation op,
                              unsigned size,
                              unsigned resultSize)
    {
      if (size == 8) {
        switch (op) {
        case avian::codegen::lir::Absolute:
          assertT(t, resultSize == 8);
          return local::getThunk(t, absoluteLongThunk);

        case avian::codegen::lir::FloatNegate:
          assertT(t, resultSize == 8);
          return local::getThunk(t, negateDoubleThunk);

        case avian::codegen::lir::FloatSquareRoot:
          assertT(t, resultSize == 8);
          return local::getThunk(t, squareRootDoubleThunk);

        case avian::codegen::lir::Float2Float:
          assertT(t, resultSize == 4);
          return local::getThunk(t, doubleToFloatThunk);

        case avian::codegen::lir::Float2Int:
          if (resultSize == 8) {
            return local::getThunk(t, doubleToLongThunk);
          } else {
            assertT(t, resultSize == 4);
            return local::getThunk(t, doubleToIntThunk);
          }

        case avian::codegen::lir::Int2Float:
          if (resultSize == 8) {
            return local::getThunk(t, longToDoubleThunk);
          } else {
            assertT(t, resultSize == 4);
            return local::getThunk(t, longToFloatThunk);
          }

        default:
          abort(t);
        }
      } else {
        assertT(t, size == 4);

        switch (op) {
        case avian::codegen::lir::Absolute:
          assertT(t, resultSize == 4);
          return local::getThunk(t, absoluteIntThunk);

        case avian::codegen::lir::FloatNegate:
          assertT(t, resultSize == 4);
          return local::getThunk(t, negateFloatThunk);

        case avian::codegen::lir::FloatAbsolute:
          assertT(t, resultSize == 4);
          return local::getThunk(t, absoluteFloatThunk);

        case avian::codegen::lir::Float2Float:
          assertT(t, resultSize == 8);
          return local::getThunk(t, floatToDoubleThunk);

        case avian::codegen::lir::Float2Int:
          if (resultSize == 4) {
            return local::getThunk(t, floatToIntThunk);
          } else {
            assertT(t, resultSize == 8);
            return local::getThunk(t, floatToLongThunk);
          }

        case avian::codegen::lir::Int2Float:
          if (resultSize == 4) {
            return local::getThunk(t, intToFloatThunk);
          } else {
            assertT(t, resultSize == 8);
            return local::getThunk(t, intToDoubleThunk);
          }

        default:
          abort(t);
        }
      }
    }

    virtual intptr_t getThunk(avian::codegen::lir::TernaryOperation op,
                              unsigned size,
                              unsigned,
                              bool* threadParameter)
    {
      *threadParameter = false;

      if (size == 8) {
        switch (op) {
        case avian::codegen::lir::Divide:
          *threadParameter = true;
          return local::getThunk(t, divideLongThunk);

        case avian::codegen::lir::Remainder:
          *threadParameter = true;
          return local::getThunk(t, moduloLongThunk);

        case avian::codegen::lir::FloatAdd:
          return local::getThunk(t, addDoubleThunk);

        case avian::codegen::lir::FloatSubtract:
          return local::getThunk(t, subtractDoubleThunk);

        case avian::codegen::lir::FloatMultiply:
          return local::getThunk(t, multiplyDoubleThunk);

        case avian::codegen::lir::FloatDivide:
          return local::getThunk(t, divideDoubleThunk);

        case avian::codegen::lir::FloatRemainder:
          return local::getThunk(t, moduloDoubleThunk);

        case avian::codegen::lir::JumpIfFloatEqual:
        case avian::codegen::lir::JumpIfFloatNotEqual:
        case avian::codegen::lir::JumpIfFloatLess:
        case avian::codegen::lir::JumpIfFloatGreater:
        case avian::codegen::lir::JumpIfFloatLessOrEqual:
        case avian::codegen::lir::JumpIfFloatGreaterOrUnordered:
        case avian::codegen::lir::JumpIfFloatGreaterOrEqualOrUnordered:
          return local::getThunk(t, compareDoublesGThunk);

        case avian::codegen::lir::JumpIfFloatGreaterOrEqual:
        case avian::codegen::lir::JumpIfFloatLessOrUnordered:
        case avian::codegen::lir::JumpIfFloatLessOrEqualOrUnordered:
          return local::getThunk(t, compareDoublesLThunk);

        default:
          abort(t);
        }
      } else {
        assertT(t, size == 4);
        switch (op) {
        case avian::codegen::lir::Divide:
          *threadParameter = true;
          return local::getThunk(t, divideIntThunk);

        case avian::codegen::lir::Remainder:
          *threadParameter = true;
          return local::getThunk(t, moduloIntThunk);

        case avian::codegen::lir::FloatAdd:
          return local::getThunk(t, addFloatThunk);

        case avian::codegen::lir::FloatSubtract:
          return local::getThunk(t, subtractFloatThunk);

        case avian::codegen::lir::FloatMultiply:
          return local::getThunk(t, multiplyFloatThunk);

        case avian::codegen::lir::FloatDivide:
          return local::getThunk(t, divideFloatThunk);

        case avian::codegen::lir::FloatRemainder:
          return local::getThunk(t, moduloFloatThunk);

        case avian::codegen::lir::JumpIfFloatEqual:
        case avian::codegen::lir::JumpIfFloatNotEqual:
        case avian::codegen::lir::JumpIfFloatLess:
        case avian::codegen::lir::JumpIfFloatGreater:
        case avian::codegen::lir::JumpIfFloatLessOrEqual:
        case avian::codegen::lir::JumpIfFloatGreaterOrUnordered:
        case avian::codegen::lir::JumpIfFloatGreaterOrEqualOrUnordered:
          return local::getThunk(t, compareFloatsGThunk);

        case avian::codegen::lir::JumpIfFloatGreaterOrEqual:
        case avian::codegen::lir::JumpIfFloatLessOrUnordered:
        case avian::codegen::lir::JumpIfFloatLessOrEqualOrUnordered:
          return local::getThunk(t, compareFloatsLThunk);

        default:
          abort(t);
        }
      }
    }

    MyThread* t;
  };

  Context(MyThread* t, BootContext* bootContext, GcMethod* method)
      : thread(t),
        zone(t->m->heap, InitialZoneCapacityInBytes),
        assembler(t->arch->makeAssembler(t->m->heap, &zone)),
        client(t),
        compiler(makeCompiler(t->m->system, assembler, &zone, &client)),
        method(method),
        bootContext(bootContext),
        objectPool(0),
        subroutineCount(0),
        traceLog(0),
        visitTable(
            Slice<uint16_t>::allocAndSet(&zone, method->code()->length(), 0)),
        rootTable(Slice<uintptr_t>::allocAndSet(
            &zone,
            method->code()->length() * frameMapSizeInWords(t, method),
            ~(uintptr_t)0)),
        executableAllocator(0),
        executableStart(0),
        executableSize(0),
        objectPoolCount(0),
        traceLogCount(0),
        dirtyRoots(false),
        leaf(true),
        eventLog(t->m->system, t->m->heap, 1024),
        protector(this),
        resource(this),
        argumentBuffer(
            (ir::Value**)t->m->heap->allocate(256 * sizeof(ir::Value*)),
            256)  // below the maximal allowed parameter count for Java
  {
  }

  Context(MyThread* t)
      : thread(t),
        zone(t->m->heap, InitialZoneCapacityInBytes),
        assembler(t->arch->makeAssembler(t->m->heap, &zone)),
        client(t),
        compiler(0),
        method(0),
        bootContext(0),
        objectPool(0),
        subroutineCount(0),
        traceLog(0),
        visitTable(0, 0),
        rootTable(0, 0),
        executableAllocator(0),
        executableStart(0),
        executableSize(0),
        objectPoolCount(0),
        traceLogCount(0),
        dirtyRoots(false),
        leaf(true),
        eventLog(t->m->system, t->m->heap, 0),
        protector(this),
        resource(this),
        argumentBuffer(0, 0)
  {
  }

  ~Context()
  {
    dispose();
  }

  void dispose()
  {
    if (compiler) {
      compiler->dispose();
    }

    assembler->dispose();

    if (executableAllocator) {
      executableAllocator->free(executableStart, executableSize);
    }

    eventLog.dispose();

    zone.dispose();

    if (argumentBuffer.begin()) {
      thread->m->heap->free(argumentBuffer.begin(), 256 * sizeof(ir::Value*));
    }
  }

  void extendLogicalCode(unsigned more)
  {
    compiler->extendLogicalCode(more);
    visitTable = visitTable.cloneAndSet(&zone, visitTable.count + more, 0);
    rootTable = rootTable.cloneAndSet(
        &zone,
        rootTable.count + more * frameMapSizeInWords(thread, method),
        ~(uintptr_t)0);
  }

  MyThread* thread;
  Zone zone;
  avian::codegen::Assembler* assembler;
  MyClient client;
  avian::codegen::Compiler* compiler;
  GcMethod* method;
  BootContext* bootContext;
  PoolElement* objectPool;
  unsigned subroutineCount;
  TraceElement* traceLog;
  Slice<uint16_t> visitTable;
  Slice<uintptr_t> rootTable;
  Alloc* executableAllocator;
  void* executableStart;
  unsigned executableSize;
  unsigned objectPoolCount;
  unsigned traceLogCount;
  bool dirtyRoots;
  bool leaf;
  Vector eventLog;
  MyProtector protector;
  MyResource resource;
  Slice<ir::Value*> argumentBuffer;
};

unsigned& dynamicIndex(MyThread* t);

void**& dynamicTable(MyThread* t);

unsigned& dynamicTableSize(MyThread* t);

void updateDynamicTable(MyThread* t, MyThread* o)
{
  o->dynamicTable = dynamicTable(t);
  if (t->peer)
    updateDynamicTable(static_cast<MyThread*>(t->peer), o);
  if (t->child)
    updateDynamicTable(static_cast<MyThread*>(t->child), o);
}

uintptr_t defaultDynamicThunk(MyThread* t);

uintptr_t compileVirtualThunk(MyThread* t,
                              unsigned index,
                              unsigned* size,
                              uintptr_t thunk,
                              const char* baseName);

Allocator* allocator(MyThread* t);

unsigned addDynamic(MyThread* t, GcInvocation* invocation)
{
  ACQUIRE(t, t->m->classLock);

  int index = invocation->index();
  if (index == -1) {
    index = dynamicIndex(t)++;
    invocation->index() = index;

    unsigned oldCapacity = roots(t)->invocations()
                               ? roots(t)->invocations()->length()
                               : 0;

    if (static_cast<unsigned>(index) >= oldCapacity) {
      unsigned newCapacity = oldCapacity ? 2 * oldCapacity : 4096;

      void** newTable = static_cast<void**>(
          allocator(t)->allocate(newCapacity * BytesPerWord));

      GcArray* newData = makeArray(t, newCapacity);
      PROTECT(t, newData);

      GcWordArray* newThunks = makeWordArray(t, newCapacity * 2);
      PROTECT(t, newThunks);

      if (dynamicTable(t)) {
        memcpy(newTable, dynamicTable(t), oldCapacity * BytesPerWord);

        for(size_t i = 0; i < oldCapacity; i++) {
          newData->setBodyElement(t, i,
               roots(t)->invocations()->body()[i]);
        }


        mark(t, newData, ArrayBody, oldCapacity);

        memcpy(newThunks->body().begin(),
               compileRoots(t)->dynamicThunks()->body().begin(),
               compileRoots(t)->dynamicThunks()->length() * BytesPerWord);
      }

      ENTER(t, Thread::ExclusiveState);

      if (dynamicTable(t)) {
        allocator(t)->free(dynamicTable(t), dynamicTableSize(t));
      }
      dynamicTable(t) = newTable;
      dynamicTableSize(t) = newCapacity * BytesPerWord;
      roots(t)->setInvocations(t, newData);

      updateDynamicTable(static_cast<MyThread*>(t->m->rootThread), t);

      compileRoots(t)->setDynamicThunks(t, newThunks);
    }

    unsigned size;
    uintptr_t thunk = compileVirtualThunk(
        t, index, &size, defaultDynamicThunk(t), "dynamicThunk");
    compileRoots(t)->dynamicThunks()->body()[index * 2] = thunk;
    compileRoots(t)->dynamicThunks()->body()[(index * 2) + 1] = size;

    t->dynamicTable[index] = reinterpret_cast<void*>(thunk);

    roots(t)->invocations()->setBodyElement(t, index, invocation);
  }

  return index;
}

unsigned translateLocalIndex(Context* context,
                             unsigned footprint,
                             unsigned index)
{
  unsigned parameterFootprint = context->method->parameterFootprint();

  if (index < parameterFootprint) {
    return parameterFootprint - index - footprint;
  } else {
    return index;
  }
}

ir::Value* loadLocal(Context* context,
                     unsigned footprint,
                     ir::Type type,
                     unsigned index)
{
  ir::Value* result = context->compiler->loadLocal(
      type, translateLocalIndex(context, footprint, index));

  assertT(context->thread, type == result->type);
  return result;
}

void storeLocal(Context* context,
                unsigned footprint,
                ir::Type type UNUSED,
                ir::Value* value,
                unsigned index)
{
  assertT(context->thread, type == value->type);
  context->compiler->storeLocal(value,
                                translateLocalIndex(context, footprint, index));
}

avian::util::FixedAllocator* codeAllocator(MyThread* t);

ir::Type operandTypeForFieldCode(Thread* t, unsigned code)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case IntField:
    return ir::Type::i4();
  case LongField:
    return ir::Type::i8();

  case ObjectField:
    return ir::Type::object();

  case FloatField:
    return ir::Type::f4();
  case DoubleField:
    return ir::Type::f8();

  case VoidField:
    return ir::Type::void_();

  default:
    abort(t);
  }
}

unsigned methodReferenceParameterFootprint(Thread* t,
                                           GcReference* reference,
                                           bool isStatic)
{
  return parameterFootprint(
      t,
      reinterpret_cast<const char*>(reference->spec()->body().begin()),
      isStatic);
}

int methodReferenceReturnCode(Thread* t, GcReference* reference)
{
  unsigned parameterCount;
  unsigned parameterFootprint;
  unsigned returnCode;
  scanMethodSpec(
      t,
      reinterpret_cast<const char*>(reference->spec()->body().begin()),
      true,
      &parameterCount,
      &parameterFootprint,
      &returnCode);

  return returnCode;
}

class Frame {
 public:
  Frame(Context* context, ir::Type* stackMap)
      : context(context),
        t(context->thread),
        c(context->compiler),
        subroutine(0),
        stackMap(stackMap),
        ip(0),
        sp(localSize()),
        level(0)
  {
    memset(stackMap, 0, context->method->code()->maxStack() * sizeof(ir::Type));
  }

  Frame(Frame* f, ir::Type* stackMap)
      : context(f->context),
        t(context->thread),
        c(context->compiler),
        subroutine(f->subroutine),
        stackMap(stackMap),
        ip(f->ip),
        sp(f->sp),
        level(f->level + 1)
  {
    memcpy(stackMap,
           f->stackMap,
           context->method->code()->maxStack() * sizeof(ir::Type));

    if (level > 1) {
      context->eventLog.append(PushContextEvent);
    }
  }

  ~Frame()
  {
    dispose();
  }

  void dispose()
  {
    if (level > 1) {
      context->eventLog.append(PopContextEvent);
    }
  }

  ir::Value* append(object o)
  {
    BootContext* bc = context->bootContext;
    if (bc) {
      avian::codegen::Promise* p = new (bc->zone)
          avian::codegen::ListenPromise(t->m->system, bc->zone);

      PROTECT(t, o);
      object pointer = makePointer(t, p);
      bc->constants = makeTriple(t, o, pointer, bc->constants);

      return c->binaryOp(
          lir::Add,
          ir::Type::object(),
          c->memory(
              c->threadRegister(), ir::Type::object(), TARGET_THREAD_HEAPIMAGE),
          c->promiseConstant(p, ir::Type::object()));
    } else {
      for (PoolElement* e = context->objectPool; e; e = e->next) {
        if (o == e->target) {
          return c->address(ir::Type::object(), e);
        }
      }

      context->objectPool = new (&context->zone)
          PoolElement(t, o, context->objectPool);

      ++context->objectPoolCount;

      return c->address(ir::Type::object(), context->objectPool);
    }
  }

  unsigned localSize()
  {
    return local::localSize(t, context->method);
  }

  unsigned stackSize()
  {
    return context->method->code()->maxStack();
  }

  unsigned frameSize()
  {
    return localSize() + stackSize();
  }

  void set(unsigned index, ir::Type type)
  {
    assertT(t, index < frameSize());

    if (type == ir::Type::object()) {
      context->eventLog.append(MarkEvent);
      context->eventLog.append2(index);
    } else {
      context->eventLog.append(ClearEvent);
      context->eventLog.append2(index);
    }

    int si = index - localSize();
    if (si >= 0) {
      stackMap[si] = type;
    }
  }

  ir::Type get(unsigned index)
  {
    assertT(t, index < frameSize());
    int si = index - localSize();
    assertT(t, si >= 0);
    return stackMap[si];
  }

  void popped(unsigned count)
  {
    assertT(t, sp >= count);
    assertT(t, sp - count >= localSize());
    while (count) {
      set(--sp, ir::Type::i4());
      --count;
    }
  }

  avian::codegen::Promise* addressPromise(avian::codegen::Promise* p)
  {
    BootContext* bc = context->bootContext;
    if (bc) {
      bc->addresses = new (bc->zone) avian::codegen::DelayedPromise(
          t->m->system, bc->zone, p, bc->addresses);
      return bc->addresses;
    } else {
      return p;
    }
  }

  ir::Value* addressOperand(avian::codegen::Promise* p)
  {
    return c->promiseConstant(p, ir::Type::iptr());
  }

  ir::Value* absoluteAddressOperand(avian::codegen::Promise* p)
  {
    return context->bootContext
               ? c->binaryOp(
                     lir::Add,
                     ir::Type::iptr(),
                     c->memory(c->threadRegister(),
                               ir::Type::iptr(),
                               TARGET_THREAD_CODEIMAGE),
                     c->promiseConstant(
                         new (&context->zone) avian::codegen::OffsetPromise(
                             p,
                             -reinterpret_cast<intptr_t>(
                                 codeAllocator(t)->memory.begin())),
                         ir::Type::iptr()))
               : addressOperand(p);
  }

  ir::Value* machineIpValue(unsigned logicalIp)
  {
    return c->promiseConstant(machineIp(logicalIp), ir::Type::iptr());
  }

  unsigned duplicatedIp(unsigned bytecodeIp)
  {
    if (UNLIKELY(subroutine)) {
      return bytecodeIp + subroutine->duplicatedBaseIp;
    } else {
      return bytecodeIp;
    }
  }

  Promise* machineIp(unsigned bytecodeIp)
  {
    return c->machineIp(duplicatedIp(bytecodeIp));
  }

  void visitLogicalIp(unsigned bytecodeIp)
  {
    unsigned dupIp = duplicatedIp(bytecodeIp);
    c->visitLogicalIp(dupIp);

    context->eventLog.append(IpEvent);
    context->eventLog.append2(bytecodeIp);
  }

  void startLogicalIp(unsigned bytecodeIp)
  {
    unsigned dupIp = duplicatedIp(bytecodeIp);
    c->startLogicalIp(dupIp);

    context->eventLog.append(IpEvent);
    context->eventLog.append2(bytecodeIp);

    this->ip = bytecodeIp;
  }

  void push(ir::Type type, ir::Value* o)
  {
    assertT(t, type == o->type);
    c->push(o->type, o);
    assertT(t, sp + 1 <= frameSize());
    set(sp++, type);
  }

  void pushObject()
  {
    c->pushed(ir::Type::object());

    assertT(t, sp + 1 <= frameSize());
    set(sp++, ir::Type::object());
  }

  void pushLarge(ir::Type type, ir::Value* o)
  {
    assertT(t, o->type == type);
    c->push(type, o);
    assertT(t, sp + 2 <= frameSize());
    set(sp++, type);
    set(sp++, type);
  }

  void popFootprint(unsigned count)
  {
    popped(count);
    c->popped(count);
  }

  ir::Value* pop(ir::Type type)
  {
    assertT(t, sp >= 1);
    assertT(t, sp - 1 >= localSize());
    assertT(t, get(sp - 1) == type);
    set(--sp, ir::Type::i4());
    return c->pop(type);
  }

  ir::Value* popLarge(ir::Type type)
  {
    assertT(t, sp >= 1);
    assertT(t, sp - 2 >= localSize());
    assertT(t, get(sp - 1) == type);
    assertT(t, get(sp - 2) == type);
    sp -= 2;
    return c->pop(type);
  }

  void load(ir::Type type, unsigned index)
  {
    assertT(t, index < localSize());
    push(type, loadLocal(context, 1, type, index));
  }

  void loadLarge(ir::Type type, unsigned index)
  {
    assertT(t, index < static_cast<unsigned>(localSize() - 1));
    pushLarge(type, loadLocal(context, 2, type, index));
  }

  void store(ir::Type type, unsigned index)
  {
    assertT(t,
            type == ir::Type::i4() || type == ir::Type::f4()
            || type == ir::Type::object());
    storeLocal(context, 1, type, pop(type), index);
    unsigned ti = translateLocalIndex(context, 1, index);
    assertT(t, ti < localSize());
    set(ti, type);
  }

  void storeLarge(ir::Type type, unsigned index)
  {
    assertT(t, type.rawSize() == 8);
    storeLocal(context, 2, type, popLarge(type), index);
    unsigned ti = translateLocalIndex(context, 2, index);
    assertT(t, ti + 1 < localSize());
    set(ti, type);
    set(ti + 1, type);
  }

  void dup()
  {
    c->push(ir::Type::i4(), c->peek(1, 0));

    assertT(t, sp + 1 <= frameSize());
    assertT(t, sp - 1 >= localSize());
    set(sp, get(sp - 1));
    ++sp;
  }

  void dupX1()
  {
    ir::Value* s0 = c->pop(ir::Type::i4());
    ir::Value* s1 = c->pop(ir::Type::i4());

    c->push(ir::Type::i4(), s0);
    c->push(ir::Type::i4(), s1);
    c->push(ir::Type::i4(), s0);

    assertT(t, sp + 1 <= frameSize());
    assertT(t, sp - 2 >= localSize());

    ir::Type b2 = get(sp - 2);
    ir::Type b1 = get(sp - 1);

    set(sp - 1, b2);
    set(sp - 2, b1);
    set(sp, b1);

    ++sp;
  }

  void dupX2()
  {
    ir::Value* s0 = c->pop(ir::Type::i4());

    if (get(sp - 2).rawSize() == 8) {
      ir::Value* s1 = c->pop(ir::Type::i8());

      c->push(ir::Type::i4(), s0);
      c->push(ir::Type::i8(), s1);
      c->push(ir::Type::i4(), s0);
    } else {
      ir::Value* s1 = c->pop(ir::Type::i4());
      ir::Value* s2 = c->pop(ir::Type::i4());

      c->push(ir::Type::i4(), s0);
      c->push(ir::Type::i4(), s2);
      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
    }

    assertT(t, sp + 1 <= frameSize());
    assertT(t, sp - 3 >= localSize());

    ir::Type b3 = get(sp - 3);
    ir::Type b2 = get(sp - 2);
    ir::Type b1 = get(sp - 1);

    set(sp - 2, b3);
    set(sp - 1, b2);
    set(sp - 3, b1);
    set(sp, b1);

    ++sp;
  }

  void dup2()
  {
    if (get(sp - 1).rawSize() == 8) {
      c->push(ir::Type::i8(), c->peek(2, 0));
    } else {
      ir::Value* s0 = c->pop(ir::Type::i4());
      ir::Value* s1 = c->pop(ir::Type::i4());

      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
    }

    assertT(t, sp + 2 <= frameSize());
    assertT(t, sp - 2 >= localSize());

    ir::Type b2 = get(sp - 2);
    ir::Type b1 = get(sp - 1);

    set(sp, b2);
    set(sp + 1, b1);

    sp += 2;
  }

  void dup2X1()
  {
    if (get(sp - 1).rawSize() == 8) {
      ir::Value* s0 = c->pop(ir::Type::i8());
      ir::Value* s1 = c->pop(ir::Type::i4());

      c->push(ir::Type::i8(), s0);
      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i8(), s0);
    } else {
      ir::Value* s0 = c->pop(ir::Type::i4());
      ir::Value* s1 = c->pop(ir::Type::i4());
      ir::Value* s2 = c->pop(ir::Type::i4());

      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
      c->push(ir::Type::i4(), s2);
      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
    }

    assertT(t, sp + 2 <= frameSize());
    assertT(t, sp - 3 >= localSize());

    ir::Type b3 = get(sp - 3);
    ir::Type b2 = get(sp - 2);
    ir::Type b1 = get(sp - 1);

    set(sp - 1, b3);
    set(sp - 3, b2);
    set(sp, b2);
    set(sp - 2, b1);
    set(sp + 1, b1);

    sp += 2;
  }

  void dup2X2()
  {
    if (get(sp - 1).rawSize() == 8) {
      ir::Value* s0 = c->pop(ir::Type::i8());

      if (get(sp - 3).rawSize() == 8) {
        ir::Value* s1 = c->pop(ir::Type::i8());

        c->push(ir::Type::i8(), s0);
        c->push(ir::Type::i8(), s1);
        c->push(ir::Type::i8(), s0);
      } else {
        ir::Value* s1 = c->pop(ir::Type::i4());
        ir::Value* s2 = c->pop(ir::Type::i4());

        c->push(ir::Type::i8(), s0);
        c->push(ir::Type::i4(), s2);
        c->push(ir::Type::i4(), s1);
        c->push(ir::Type::i8(), s0);
      }
    } else {
      ir::Value* s0 = c->pop(ir::Type::i4());
      ir::Value* s1 = c->pop(ir::Type::i4());
      ir::Value* s2 = c->pop(ir::Type::i4());
      ir::Value* s3 = c->pop(ir::Type::i4());

      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
      c->push(ir::Type::i4(), s3);
      c->push(ir::Type::i4(), s2);
      c->push(ir::Type::i4(), s1);
      c->push(ir::Type::i4(), s0);
    }

    assertT(t, sp + 2 <= frameSize());
    assertT(t, sp - 4 >= localSize());

    ir::Type b4 = get(sp - 4);
    ir::Type b3 = get(sp - 3);
    ir::Type b2 = get(sp - 2);
    ir::Type b1 = get(sp - 1);

    set(sp - 2, b4);
    set(sp - 1, b3);
    set(sp - 4, b2);
    set(sp, b2);
    set(sp - 3, b1);
    set(sp + 1, b1);

    sp += 2;
  }

  void swap()
  {
    ir::Value* s0 = c->pop(ir::Type::i4());
    ir::Value* s1 = c->pop(ir::Type::i4());

    c->push(ir::Type::i4(), s0);
    c->push(ir::Type::i4(), s1);

    assertT(t, sp - 2 >= localSize());

    ir::Type saved = get(sp - 1);

    set(sp - 1, get(sp - 2));
    set(sp - 2, saved);
  }

  TraceElement* trace(GcMethod* target, unsigned flags)
  {
    unsigned mapSize = frameMapSizeInWords(t, context->method);

    TraceElement* e = context->traceLog = new (
        context->zone.allocate(sizeof(TraceElement) + (mapSize * BytesPerWord)))
        TraceElement(context,
                     duplicatedIp(ip),
                     target,
                     flags,
                     context->traceLog,
                     mapSize);

    ++context->traceLogCount;

    context->eventLog.append(TraceEvent);
    context->eventLog.appendAddress(e);

    return e;
  }

  void pushReturnValue(unsigned code, ir::Value* result)
  {
    switch (code) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case IntField:
      return push(ir::Type::i4(), result);
    case FloatField:
      return push(ir::Type::f4(), result);

    case ObjectField:
      return push(ir::Type::object(), result);

    case LongField:
      return pushLarge(ir::Type::i8(), result);
    case DoubleField:
      return pushLarge(ir::Type::f8(), result);

    default:
      abort(t);
    }
  }

  Slice<ir::Value*> peekMethodArguments(unsigned footprint)
  {
    ir::Value** ptr = context->argumentBuffer.items;

    for (unsigned i = 0; i < footprint; i++) {
      *(ptr++) = c->peek(1, footprint - i - 1);
    }

    return Slice<ir::Value*>(context->argumentBuffer.items, footprint);
  }

  void stackCall(ir::Value* methodValue,
                 GcMethod* methodObject,
                 unsigned flags,
                 TraceElement* trace)
  {
    unsigned footprint = methodObject->parameterFootprint();
    unsigned returnCode = methodObject->returnCode();
    ir::Value* result = c->stackCall(methodValue,
                                     flags,
                                     trace,
                                     operandTypeForFieldCode(t, returnCode),
                                     peekMethodArguments(footprint));

    popFootprint(footprint);

    if (returnCode != VoidField) {
      pushReturnValue(returnCode, result);
    }
  }

  void referenceStackCall(bool isStatic,
                          ir::Value* methodValue,
                          GcReference* methodReference,
                          unsigned flags,
                          TraceElement* trace)
  {
    unsigned footprint
        = methodReferenceParameterFootprint(t, methodReference, isStatic);
    unsigned returnCode = methodReferenceReturnCode(t, methodReference);
    ir::Value* result = c->stackCall(methodValue,
                                     flags,
                                     trace,
                                     operandTypeForFieldCode(t, returnCode),
                                     peekMethodArguments(footprint));

    popFootprint(footprint);

    if (returnCode != VoidField) {
      pushReturnValue(returnCode, result);
    }
  }

  void startSubroutine(unsigned ip, unsigned returnAddress)
  {
    // Push a dummy value to the stack, representing the return address (which
    // we don't need, since we're expanding everything statically).
    // TODO: in the future, push a value that we can track through type checking
    push(ir::Type::object(), c->constant(0, ir::Type::object()));

    if (DebugInstructions) {
      fprintf(stderr, "startSubroutine %u %u\n", ip, returnAddress);
    }

    Subroutine* subroutine = new (&context->zone)
        Subroutine(context->subroutineCount++,
                   returnAddress,
                   context->method->code()->length(),
                   this->subroutine);

    context->extendLogicalCode(context->method->code()->length());

    this->subroutine = subroutine;
  }

  unsigned endSubroutine(unsigned returnAddressLocal UNUSED)
  {
    // TODO: use returnAddressLocal to decide which subroutine we're returning
    // from (in case it's ever not the most recent one entered).  I'm unsure of
    // whether such a subroutine pattern would pass bytecode verification.

    unsigned returnAddress = subroutine->returnAddress;

    if (DebugInstructions) {
      fprintf(stderr, "endSubroutine %u %u\n", ip, returnAddress);
    }

    subroutine = subroutine->outer;

    return returnAddress;
  }

  Context* context;
  MyThread* t;
  avian::codegen::Compiler* c;

  // Innermost subroutine we're compiling code for
  Subroutine* subroutine;

  ir::Type* stackMap;
  unsigned ip;
  unsigned sp;
  unsigned level;
};

unsigned savedTargetIndex(MyThread* t UNUSED, GcMethod* method)
{
  return method->code()->maxLocals();
}

GcCallNode* findCallNode(MyThread* t, void* address);

void* findExceptionHandler(Thread* t, GcMethod* method, void* ip)
{
  if (t->exception) {
    GcArray* table = cast<GcArray>(t, method->code()->exceptionHandlerTable());
    if (table) {
      GcIntArray* index = cast<GcIntArray>(t, table->body()[0]);

      uint8_t* compiled = reinterpret_cast<uint8_t*>(methodCompiled(t, method));

      for (unsigned i = 0; i < table->length() - 1; ++i) {
        unsigned start = index->body()[i * 3];
        unsigned end = index->body()[(i * 3) + 1];
        unsigned key = difference(ip, compiled) - 1;

        if (key >= start and key < end) {
          GcClass* catchType = cast<GcClass>(t, table->body()[i + 1]);

          if (exceptionMatch(t, catchType, t->exception)) {
            return compiled + index->body()[(i * 3) + 2];
          }
        }
      }
    }
  }

  return 0;
}

void releaseLock(MyThread* t, GcMethod* method, void* stack)
{
  if (method->flags() & ACC_SYNCHRONIZED) {
    if (t->methodLockIsClean) {
      object lock;
      if (method->flags() & ACC_STATIC) {
        lock = getJClass(t, method->class_());
      } else {
        lock = *localObject(t,
                            stackForFrame(t, stack, method),
                            method,
                            savedTargetIndex(t, method));
      }

      release(t, lock);
    } else {
      // got an exception while trying to acquire the lock for a
      // synchronized method -- don't try to release it, since we
      // never succeeded in acquiring it.
      t->methodLockIsClean = true;
    }
  }
}

void findUnwindTarget(MyThread* t,
                      void** targetIp,
                      void** targetFrame,
                      void** targetStack,
                      GcContinuation** targetContinuation)
{
  void* ip;
  void* stack;
  GcContinuation* continuation;

  if (t->traceContext) {
    ip = t->traceContext->ip;
    stack = t->traceContext->stack;
    continuation = t->traceContext->continuation;
  } else {
    ip = getIp(t);
    stack = t->stack;
    continuation = t->continuation;
  }

  GcMethod* target = t->trace->targetMethod;
  bool mostRecent = true;

  *targetIp = 0;
  while (*targetIp == 0) {
    GcMethod* method = methodForIp(t, ip);
    if (method) {
      void* handler = findExceptionHandler(t, method, ip);

      if (handler) {
        *targetIp = handler;

        nextFrame(t, &ip, &stack, method, target, mostRecent);

        void** sp = static_cast<void**>(stackForFrame(t, stack, method))
                    + t->arch->frameReturnAddressSize();

        *targetFrame = static_cast<void**>(stack)
                       + t->arch->framePointerOffset();
        *targetStack = sp;
        *targetContinuation = continuation;

        sp[localOffset(t, localSize(t, method), method)] = t->exception;

        t->exception = 0;
      } else {
        nextFrame(t, &ip, &stack, method, target, mostRecent);

        if (t->exception) {
          releaseLock(t, method, stack);
        }

        target = method;
      }
    } else {
      expect(t, ip);
      *targetIp = ip;
      *targetFrame = 0;
      *targetStack = static_cast<void**>(stack)
                     + t->arch->frameReturnAddressSize();
      *targetContinuation = continuation;

      while (Continuations and *targetContinuation) {
        GcContinuation* c = *targetContinuation;

        GcMethod* method = c->method();

        void* handler = findExceptionHandler(t, method, c->address());

        if (handler) {
          t->exceptionHandler = handler;

          t->exceptionStackAdjustment
              = (stackOffsetFromFrame(t, method)
                 - ((c->framePointerOffset() / BytesPerWord)
                    - t->arch->framePointerOffset()
                    + t->arch->frameReturnAddressSize())) * BytesPerWord;

          t->exceptionOffset = localOffset(t, localSize(t, method), method)
                               * BytesPerWord;

          break;
        } else if (t->exception) {
          releaseLock(t,
                      method,
                      reinterpret_cast<uint8_t*>(c) + ContinuationBody
                      + c->returnAddressOffset()
                      - t->arch->returnAddressOffset());
        }

        *targetContinuation = c->next();
      }
    }

    mostRecent = false;
  }
}

GcContinuation* makeCurrentContinuation(MyThread* t,
                                        void** targetIp,
                                        void** targetStack)
{
  void* ip = getIp(t);
  void* stack = t->stack;

  GcContinuationContext* context
      = t->continuation
            ? t->continuation->context()
            : makeContinuationContext(t, 0, 0, 0, 0, t->trace->originalMethod);
  PROTECT(t, context);

  GcMethod* target = t->trace->targetMethod;
  PROTECT(t, target);

  GcContinuation* first = 0;
  PROTECT(t, first);

  GcContinuation* last = 0;
  PROTECT(t, last);

  bool mostRecent = true;

  *targetIp = 0;
  while (*targetIp == 0) {
    assertT(t, ip);

    GcMethod* method = methodForIp(t, ip);
    if (method) {
      PROTECT(t, method);

      void** top = static_cast<void**>(stack)
                   + t->arch->frameReturnAddressSize()
                   + t->arch->frameFooterSize();
      unsigned argumentFootprint
          = t->arch->argumentFootprint(target->parameterFootprint());
      unsigned alignment = t->arch->stackAlignmentInWords();
      if (avian::codegen::TailCalls and argumentFootprint > alignment) {
        top += argumentFootprint - alignment;
      }

      void* nextIp = ip;
      nextFrame(t, &nextIp, &stack, method, target, mostRecent);

      void** bottom = static_cast<void**>(stack)
                      + t->arch->frameReturnAddressSize();
      unsigned frameSize = bottom - top;
      unsigned totalSize
          = frameSize + t->arch->frameFooterSize()
            + t->arch->argumentFootprint(method->parameterFootprint());

      GcContinuation* c = makeContinuation(
          t,
          0,
          context,
          method,
          ip,
          (frameSize + t->arch->frameFooterSize()
           + t->arch->returnAddressOffset() - t->arch->frameReturnAddressSize())
          * BytesPerWord,
          (frameSize + t->arch->frameFooterSize()
           + t->arch->framePointerOffset() - t->arch->frameReturnAddressSize())
          * BytesPerWord,
          totalSize);

      memcpy(c->body().begin(), top, totalSize * BytesPerWord);

      if (last) {
        last->setNext(t, c);
      } else {
        first = c;
      }
      last = c;

      ip = nextIp;

      target = method;
    } else {
      *targetIp = ip;
      *targetStack = static_cast<void**>(stack)
                     + t->arch->frameReturnAddressSize();
    }

    mostRecent = false;
  }

  expect(t, last);
  last->setNext(t, t->continuation);

  return first;
}

void NO_RETURN unwind(MyThread* t)
{
  void* ip;
  void* frame;
  void* stack;
  GcContinuation* continuation;
  findUnwindTarget(t, &ip, &frame, &stack, &continuation);

  t->trace->targetMethod = 0;
  t->trace->nativeMethod = 0;

  transition(t, ip, stack, continuation, t->trace);

  vmJump(ip, frame, stack, t, 0, 0);
}

class MyCheckpoint : public Thread::Checkpoint {
 public:
  MyCheckpoint(MyThread* t) : Checkpoint(t)
  {
  }

  virtual void unwind()
  {
    local::unwind(static_cast<MyThread*>(t));
  }
};

uintptr_t defaultThunk(MyThread* t);

uintptr_t nativeThunk(MyThread* t);

uintptr_t bootNativeThunk(MyThread* t);

uintptr_t virtualThunk(MyThread* t, unsigned index);

bool unresolved(MyThread* t, uintptr_t methodAddress);

uintptr_t methodAddress(Thread* t, GcMethod* method)
{
  if (method->flags() & ACC_NATIVE) {
    return bootNativeThunk(static_cast<MyThread*>(t));
  } else {
    return methodCompiled(t, method);
  }
}

void tryInitClass(MyThread* t, GcClass* class_)
{
  initClass(t, class_);
}

void compile(MyThread* t,
             FixedAllocator* allocator,
             BootContext* bootContext,
             GcMethod* method);

GcMethod* resolveMethod(Thread* t, GcPair* pair)
{
  GcReference* reference = cast<GcReference>(t, pair->second());
  PROTECT(t, reference);

  GcClass* class_ = resolveClassInObject(
      t,
      cast<GcMethod>(t, pair->first())->class_()->loader(),
      reference,
      ReferenceClass);

  return cast<GcMethod>(t,
                        findInHierarchy(t,
                                        class_,
                                        reference->name(),
                                        reference->spec(),
                                        findMethodInClass,
                                        GcNoSuchMethodError::Type));
}

bool methodAbstract(Thread* t UNUSED, GcMethod* method)
{
  return method->code() == 0 and (method->flags() & ACC_NATIVE) == 0;
}

int64_t prepareMethodForCall(MyThread* t, GcMethod* target)
{
  if (methodAbstract(t, target)) {
    throwNew(t,
             GcAbstractMethodError::Type,
             "%s.%s%s",
             target->class_()->name()->body().begin(),
             target->name()->body().begin(),
             target->spec()->body().begin());
  } else {
    if (unresolved(t, methodAddress(t, target))) {
      PROTECT(t, target);

      compile(t, codeAllocator(t), 0, target);
    }

    if (target->flags() & ACC_NATIVE) {
      t->trace->nativeMethod = target;
    }

    return methodAddress(t, target);
  }
}

int64_t findInterfaceMethodFromInstance(MyThread* t,
                                        GcMethod* method,
                                        object instance)
{
  if (instance) {
    return prepareMethodForCall(
        t, findInterfaceMethod(t, method, objectClass(t, instance)));
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

int64_t findInterfaceMethodFromInstanceAndReference(MyThread* t,
                                                    GcPair* pair,
                                                    object instance)
{
  PROTECT(t, instance);

  GcMethod* method = resolveMethod(t, pair);

  return findInterfaceMethodFromInstance(t, method, instance);
}

void checkMethod(Thread* t, GcMethod* method, bool shouldBeStatic)
{
  if (((method->flags() & ACC_STATIC) == 0) == shouldBeStatic) {
    throwNew(t,
             GcIncompatibleClassChangeError::Type,
             "expected %s.%s%s to be %s",
             method->class_()->name()->body().begin(),
             method->name()->body().begin(),
             method->spec()->body().begin(),
             shouldBeStatic ? "static" : "non-static");
  }
}

int64_t findSpecialMethodFromReference(MyThread* t, GcPair* pair)
{
  PROTECT(t, pair);

  GcMethod* target = resolveMethod(t, pair);

  GcClass* class_ = cast<GcMethod>(t, pair->first())->class_();
  if (isSpecialMethod(t, target, class_)) {
    target = findVirtualMethod(t, target, class_->super());
  }

  checkMethod(t, target, false);

  return prepareMethodForCall(t, target);
}

int64_t findStaticMethodFromReference(MyThread* t, GcPair* pair)
{
  GcMethod* target = resolveMethod(t, pair);

  checkMethod(t, target, true);

  return prepareMethodForCall(t, target);
}

int64_t findVirtualMethodFromReference(MyThread* t,
                                       GcPair* pair,
                                       object instance)
{
  PROTECT(t, instance);

  GcMethod* target = resolveMethod(t, pair);

  target = findVirtualMethod(t, target, objectClass(t, instance));

  checkMethod(t, target, false);

  return prepareMethodForCall(t, target);
}

int64_t getMethodAddress(MyThread* t, GcMethod* target)
{
  return prepareMethodForCall(t, target);
}

int64_t getJClassFromReference(MyThread* t, GcPair* pair)
{
  return reinterpret_cast<intptr_t>(getJClass(
      t,
      resolveClass(t,
                   cast<GcMethod>(t, pair->first())->class_()->loader(),
                   cast<GcReference>(t, pair->second())->name())));
}

unsigned traceSize(Thread* t)
{
  class Counter : public Processor::StackVisitor {
   public:
    Counter() : count(0)
    {
    }

    virtual bool visit(Processor::StackWalker*)
    {
      ++count;
      return true;
    }

    unsigned count;
  } counter;

  t->m->processor->walkStack(t, &counter);

  return pad(GcArray::FixedSize)
         + (counter.count * pad(ArrayElementSizeOfArray))
         + (counter.count * pad(GcTraceElement::FixedSize));
}

void NO_RETURN throwArithmetic(MyThread* t)
{
  if (ensure(t, GcArithmeticException::FixedSize + traceSize(t))) {
    t->setFlag(Thread::TracingFlag);
    THREAD_RESOURCE0(t, t->clearFlag(Thread::TracingFlag));

    throwNew(t, GcArithmeticException::Type);
  } else {
    // not enough memory available for a new exception and stack trace
    // -- use a preallocated instance instead
    throw_(t, roots(t)->arithmeticException());
  }
}

int64_t divideLong(MyThread* t, int64_t b, int64_t a)
{
  if (LIKELY(b)) {
    return a / b;
  } else {
    throwArithmetic(t);
  }
}

int64_t divideInt(MyThread* t, int32_t b, int32_t a)
{
  if (LIKELY(b)) {
    return a / b;
  } else {
    throwArithmetic(t);
  }
}

int64_t moduloLong(MyThread* t, int64_t b, int64_t a)
{
  if (LIKELY(b)) {
    return a % b;
  } else {
    throwArithmetic(t);
  }
}

int64_t moduloInt(MyThread* t, int32_t b, int32_t a)
{
  if (LIKELY(b)) {
    return a % b;
  } else {
    throwArithmetic(t);
  }
}

uint64_t makeBlankObjectArray(MyThread* t, GcClass* class_, int32_t length)
{
  if (length >= 0) {
    return reinterpret_cast<uint64_t>(makeObjectArray(t, class_, length));
  } else {
    throwNew(t, GcNegativeArraySizeException::Type, "%d", length);
  }
}

uint64_t makeBlankObjectArrayFromReference(MyThread* t,
                                           GcPair* pair,
                                           int32_t length)
{
  return makeBlankObjectArray(
      t,
      resolveClass(t,
                   cast<GcMethod>(t, pair->first())->class_()->loader(),
                   cast<GcReference>(t, pair->second())->name()),
      length);
}

uint64_t makeBlankArray(MyThread* t, unsigned type, int32_t length)
{
  if (length >= 0) {
    switch (type) {
    case T_BOOLEAN:
      return reinterpret_cast<uintptr_t>(makeBooleanArray(t, length));
    case T_CHAR:
      return reinterpret_cast<uintptr_t>(makeCharArray(t, length));
    case T_FLOAT:
      return reinterpret_cast<uintptr_t>(makeFloatArray(t, length));
    case T_DOUBLE:
      return reinterpret_cast<uintptr_t>(makeDoubleArray(t, length));
    case T_BYTE:
      return reinterpret_cast<uintptr_t>(makeByteArray(t, length));
    case T_SHORT:
      return reinterpret_cast<uintptr_t>(makeShortArray(t, length));
    case T_INT:
      return reinterpret_cast<uintptr_t>(makeIntArray(t, length));
    case T_LONG:
      return reinterpret_cast<uintptr_t>(makeLongArray(t, length));
    default:
      abort(t);
    }
  } else {
    throwNew(t, GcNegativeArraySizeException::Type, "%d", length);
  }
}

uint64_t lookUpAddress(int32_t key,
                       uintptr_t* start,
                       int32_t count,
                       uintptr_t default_)
{
  int32_t bottom = 0;
  int32_t top = count;
  for (int32_t span = top - bottom; span; span = top - bottom) {
    int32_t middle = bottom + (span / 2);
    uintptr_t* p = start + (middle * 2);
    int32_t k = *p;

    if (key < k) {
      top = middle;
    } else if (key > k) {
      bottom = middle + 1;
    } else {
      return p[1];
    }
  }

  return default_;
}

void setMaybeNull(MyThread* t, object o, unsigned offset, object value)
{
  if (LIKELY(o)) {
    setField(t, o, offset, value);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void acquireMonitorForObject(MyThread* t, object o)
{
  if (LIKELY(o)) {
    acquire(t, o);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void acquireMonitorForObjectOnEntrance(MyThread* t, object o)
{
  if (LIKELY(o)) {
    t->methodLockIsClean = false;
    acquire(t, o);
    t->methodLockIsClean = true;
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void releaseMonitorForObject(MyThread* t, object o)
{
  if (LIKELY(o)) {
    release(t, o);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void acquireMonitorForClassOnEntrance(MyThread* t, GcClass* o)
{
  if (LIKELY(o)) {
    t->methodLockIsClean = false;
    acquire(t, getJClass(t, o));
    t->methodLockIsClean = true;
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void releaseMonitorForClass(MyThread* t, GcClass* o)
{
  if (LIKELY(o)) {
    release(t, getJClass(t, o));
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

object makeMultidimensionalArray2(MyThread* t,
                                  GcClass* class_,
                                  uintptr_t* countStack,
                                  int32_t dimensions)
{
  PROTECT(t, class_);

  THREAD_RUNTIME_ARRAY(t, int32_t, counts, dimensions);
  for (int i = dimensions - 1; i >= 0; --i) {
    RUNTIME_ARRAY_BODY(counts)[i] = countStack[dimensions - i - 1];
    if (UNLIKELY(RUNTIME_ARRAY_BODY(counts)[i] < 0)) {
      throwNew(t,
               GcNegativeArraySizeException::Type,
               "%d",
               RUNTIME_ARRAY_BODY(counts)[i]);
      return 0;
    }
  }

  object array = makeArray(t, RUNTIME_ARRAY_BODY(counts)[0]);
  setObjectClass(t, array, class_);
  PROTECT(t, array);

  populateMultiArray(t, array, RUNTIME_ARRAY_BODY(counts), 0, dimensions);

  return array;
}

uint64_t makeMultidimensionalArray(MyThread* t,
                                   GcClass* class_,
                                   int32_t dimensions,
                                   int32_t offset)
{
  return reinterpret_cast<uintptr_t>(makeMultidimensionalArray2(
      t, class_, static_cast<uintptr_t*>(t->stack) + offset, dimensions));
}

uint64_t makeMultidimensionalArrayFromReference(MyThread* t,
                                                GcPair* pair,
                                                int32_t dimensions,
                                                int32_t offset)
{
  return makeMultidimensionalArray(
      t,
      resolveClass(t,
                   cast<GcMethod>(t, pair->first())->class_()->loader(),
                   cast<GcReference>(t, pair->second())->name()),
      dimensions,
      offset);
}

void NO_RETURN throwArrayIndexOutOfBounds(MyThread* t)
{
  if (ensure(t, GcArrayIndexOutOfBoundsException::FixedSize + traceSize(t))) {
    t->setFlag(Thread::TracingFlag);
    THREAD_RESOURCE0(t, t->clearFlag(Thread::TracingFlag));

    throwNew(t, GcArrayIndexOutOfBoundsException::Type);
  } else {
    // not enough memory available for a new exception and stack trace
    // -- use a preallocated instance instead
    throw_(t, roots(t)->arrayIndexOutOfBoundsException());
  }
}

void NO_RETURN throwStackOverflow(MyThread* t)
{
  throwNew(t, GcStackOverflowError::Type);
}

void NO_RETURN throw_(MyThread* t, GcThrowable* o)
{
  if (LIKELY(o)) {
    vm::throw_(t, o);
  } else {
    throwNew(t, GcNullPointerException::Type);
  }
}

void checkCast(MyThread* t, GcClass* class_, object o)
{
  if (UNLIKELY(o and not isAssignableFrom(t, class_, objectClass(t, o)))) {
    GcByteArray* classNameFrom = objectClass(t, o)->name();
    GcByteArray* classNameTo = class_->name();
    THREAD_RUNTIME_ARRAY(t, char, classFrom, classNameFrom->length());
    THREAD_RUNTIME_ARRAY(t, char, classTo, classNameTo->length());
    replace('/',
            '.',
            RUNTIME_ARRAY_BODY(classFrom),
            reinterpret_cast<char*>(classNameFrom->body().begin()));
    replace('/',
            '.',
            RUNTIME_ARRAY_BODY(classTo),
            reinterpret_cast<char*>(classNameTo->body().begin()));
    throwNew(t,
             GcClassCastException::Type,
             "%s cannot be cast to %s",
             RUNTIME_ARRAY_BODY(classFrom),
             RUNTIME_ARRAY_BODY(classTo));
  }
}

void checkCastFromReference(MyThread* t, GcPair* pair, object o)
{
  PROTECT(t, o);

  GcClass* c
      = resolveClass(t,
                     cast<GcMethod>(t, pair->first())->class_()->loader(),
                     cast<GcReference>(t, pair->second())->name());

  checkCast(t, c, o);
}

GcField* resolveField(Thread* t, GcPair* pair)
{
  GcReference* reference = cast<GcReference>(t, pair->second());
  PROTECT(t, reference);

  GcClass* class_ = resolveClassInObject(
      t,
      cast<GcMethod>(t, pair->first())->class_()->loader(),
      reference,
      ReferenceClass);

  return cast<GcField>(t,
                       findInHierarchy(t,
                                       class_,
                                       reference->name(),
                                       reference->spec(),
                                       findFieldInClass,
                                       GcNoSuchFieldError::Type));
}

uint64_t getFieldValue(Thread* t, object target, GcField* field)
{
  switch (field->code()) {
  case ByteField:
  case BooleanField:
    return fieldAtOffset<int8_t>(target, field->offset());

  case CharField:
  case ShortField:
    return fieldAtOffset<int16_t>(target, field->offset());

  case FloatField:
  case IntField:
    return fieldAtOffset<int32_t>(target, field->offset());

  case DoubleField:
  case LongField:
    return fieldAtOffset<int64_t>(target, field->offset());

  case ObjectField:
    return fieldAtOffset<intptr_t>(target, field->offset());

  default:
    abort(t);
  }
}

uint64_t getStaticFieldValueFromReference(MyThread* t, GcPair* pair)
{
  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  initClass(t, field->class_());

  ACQUIRE_FIELD_FOR_READ(t, field);

  return getFieldValue(t, field->class_()->staticTable(), field);
}

uint64_t getFieldValueFromReference(MyThread* t, GcPair* pair, object instance)
{
  PROTECT(t, instance);

  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  ACQUIRE_FIELD_FOR_READ(t, field);

  return getFieldValue(t, instance, field);
}

void setStaticLongFieldValueFromReference(MyThread* t,
                                          GcPair* pair,
                                          uint64_t value)
{
  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  initClass(t, field->class_());

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<int64_t>(field->class_()->staticTable(), field->offset())
      = value;
}

void setLongFieldValueFromReference(MyThread* t,
                                    GcPair* pair,
                                    object instance,
                                    uint64_t value)
{
  PROTECT(t, instance);

  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  fieldAtOffset<int64_t>(instance, field->offset()) = value;
}

void setStaticObjectFieldValueFromReference(MyThread* t,
                                            GcPair* pair,
                                            object value)
{
  PROTECT(t, value);

  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  initClass(t, field->class_());

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setField(t, field->class_()->staticTable(), field->offset(), value);
}

void setObjectFieldValueFromReference(MyThread* t,
                                      GcPair* pair,
                                      object instance,
                                      object value)
{
  PROTECT(t, instance);
  PROTECT(t, value);

  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setField(t, instance, field->offset(), value);
}

void setFieldValue(MyThread* t, object target, GcField* field, uint32_t value)
{
  switch (field->code()) {
  case ByteField:
  case BooleanField:
    fieldAtOffset<int8_t>(target, field->offset()) = value;
    break;

  case CharField:
  case ShortField:
    fieldAtOffset<int16_t>(target, field->offset()) = value;
    break;

  case FloatField:
  case IntField:
    fieldAtOffset<int32_t>(target, field->offset()) = value;
    break;

  default:
    abort(t);
  }
}

void setStaticFieldValueFromReference(MyThread* t, GcPair* pair, uint32_t value)
{
  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  initClass(t, field->class_());

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setFieldValue(t, field->class_()->staticTable(), field, value);
}

void setFieldValueFromReference(MyThread* t,
                                GcPair* pair,
                                object instance,
                                uint32_t value)
{
  PROTECT(t, instance);
  GcField* field = resolveField(t, pair);
  PROTECT(t, field);

  ACQUIRE_FIELD_FOR_WRITE(t, field);

  setFieldValue(t, instance, field, value);
}

uint64_t instanceOf64(Thread* t, GcClass* class_, object o)
{
  return instanceOf(t, class_, o);
}

uint64_t instanceOfFromReference(Thread* t, GcPair* pair, object o)
{
  PROTECT(t, o);

  GcClass* c
      = resolveClass(t,
                     cast<GcMethod>(t, pair->first())->class_()->loader(),
                     cast<GcReference>(t, pair->second())->name());

  return instanceOf64(t, c, o);
}

uint64_t makeNewGeneral64(Thread* t, GcClass* class_)
{
  PROTECT(t, class_);

  initClass(t, class_);

  return reinterpret_cast<uintptr_t>(makeNewGeneral(t, class_));
}

uint64_t makeNew64(Thread* t, GcClass* class_)
{
  PROTECT(t, class_);

  initClass(t, class_);

  return reinterpret_cast<uintptr_t>(makeNew(t, class_));
}

uint64_t makeNewFromReference(Thread* t, GcPair* pair)
{
  GcClass* class_
      = resolveClass(t,
                     cast<GcMethod>(t, pair->first())->class_()->loader(),
                     cast<GcReference>(t, pair->second())->name());

  PROTECT(t, class_);

  initClass(t, class_);

  return makeNewGeneral64(t, class_);
}

uint64_t getJClass64(Thread* t, GcClass* class_)
{
  return reinterpret_cast<uintptr_t>(getJClass(t, class_));
}

void gcIfNecessary(MyThread* t)
{
  stress(t);

  if (UNLIKELY(t->getFlags() & Thread::UseBackupHeapFlag)) {
    collect(t, Heap::MinorCollection);
  }
}

void idleIfNecessary(MyThread* t)
{
  if (UNLIKELY(t->m->exclusive)) {
    ENTER(t, Thread::IdleState);
  }
}

bool useLongJump(MyThread* t, uintptr_t target)
{
  uintptr_t reach = t->arch->maximumImmediateJump();
  FixedAllocator* a = codeAllocator(t);
  uintptr_t start = reinterpret_cast<uintptr_t>(a->memory.begin());
  uintptr_t end = reinterpret_cast<uintptr_t>(a->memory.begin())
                  + a->memory.count;
  assertT(t, end - start < reach);

  return (target > end && (target - start) > reach)
         or (target < start && (end - target) > reach);
}

FILE* compileLog = 0;

void logCompile(MyThread* t,
                const void* code,
                unsigned size,
                const char* class_,
                const char* name,
                const char* spec);

unsigned simpleFrameMapTableSize(MyThread* t, GcMethod* method, GcIntArray* map)
{
  int size = frameMapSizeInBits(t, method);
  return ceilingDivide(map->length() * size, 32 + size);
}

#ifndef AVIAN_AOT_ONLY
unsigned resultSize(MyThread* t, unsigned code)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    return 4;

  case ObjectField:
    return TargetBytesPerWord;

  case LongField:
  case DoubleField:
    return 8;

  case VoidField:
    return 0;

  default:
    abort(t);
  }
}

ir::Value* popField(MyThread* t, Frame* frame, int code)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case IntField:
    return frame->pop(ir::Type::i4());
  case FloatField:
    return frame->pop(ir::Type::f4());

  case LongField:
    return frame->popLarge(ir::Type::i8());
  case DoubleField:
    return frame->popLarge(ir::Type::f8());

  case ObjectField:
    return frame->pop(ir::Type::object());

  default:
    abort(t);
  }
}

void compileSafePoint(MyThread* t, Compiler* c, Frame* frame)
{
  c->nativeCall(
      c->constant(getThunk(t, idleIfNecessaryThunk), ir::Type::iptr()),
      0,
      frame->trace(0, 0),
      ir::Type::void_(),
      args(c->threadRegister()));
}

void compileDirectInvoke(MyThread* t,
                         Frame* frame,
                         GcMethod* target,
                         bool tailCall,
                         bool useThunk,
                         avian::codegen::Promise* addressPromise)
{
  avian::codegen::Compiler* c = frame->c;

  unsigned flags
      = (avian::codegen::TailCalls and tailCall ? Compiler::TailJump : 0);
  unsigned traceFlags;

  if (addressPromise == 0 and useLongJump(t, methodAddress(t, target))) {
    flags |= Compiler::LongJumpOrCall;
    traceFlags = TraceElement::LongCall;
  } else {
    traceFlags = 0;
  }

  if (useThunk or (avian::codegen::TailCalls and tailCall
                   and (target->flags() & ACC_NATIVE))) {
    if (frame->context->bootContext == 0) {
      flags |= Compiler::Aligned;
    }

    if (avian::codegen::TailCalls and tailCall) {
      traceFlags |= TraceElement::TailCall;

      TraceElement* trace = frame->trace(target, traceFlags);

      avian::codegen::Promise* returnAddressPromise
          = new (frame->context->zone.allocate(sizeof(TraceElementPromise)))
          TraceElementPromise(t->m->system, trace);

      frame->stackCall(
          c->promiseConstant(returnAddressPromise, ir::Type::iptr()),
          target,
          flags,
          trace);

      c->store(frame->absoluteAddressOperand(returnAddressPromise),
               c->memory(c->threadRegister(),
                         ir::Type::iptr(),
                         TARGET_THREAD_TAILADDRESS));

      c->exit(c->constant(
          (target->flags() & ACC_NATIVE) ? nativeThunk(t) : defaultThunk(t),
          ir::Type::iptr()));
    } else {
      return frame->stackCall(c->constant(defaultThunk(t), ir::Type::iptr()),
                              target,
                              flags,
                              frame->trace(target, traceFlags));
    }
  } else {
    ir::Value* address
        = (addressPromise
               ? c->promiseConstant(addressPromise, ir::Type::iptr())
               : c->constant(methodAddress(t, target), ir::Type::iptr()));

    frame->stackCall(
        address,
        target,
        flags,
        tailCall ? 0 : frame->trace((target->flags() & ACC_NATIVE) ? target : 0,
                                    0));
  }
}

bool compileDirectInvoke(MyThread* t,
                         Frame* frame,
                         GcMethod* target,
                         bool tailCall)
{
  // don't bother calling an empty method unless calling it might
  // cause the class to be initialized, which may have side effects
  if (emptyMethod(t, target) and (not classNeedsInit(t, target->class_()))) {
    frame->popFootprint(target->parameterFootprint());
    tailCall = false;
  } else {
    BootContext* bc = frame->context->bootContext;
    if (bc) {
      if ((target->class_() == frame->context->method->class_()
           or (not classNeedsInit(t, target->class_())))
          and (not(avian::codegen::TailCalls and tailCall
                   and (target->flags() & ACC_NATIVE)))) {
        avian::codegen::Promise* p = new (bc->zone)
            avian::codegen::ListenPromise(t->m->system, bc->zone);

        PROTECT(t, target);
        object pointer = makePointer(t, p);
        bc->calls = makeTriple(t, target, pointer, bc->calls);

        compileDirectInvoke(t, frame, target, tailCall, false, p);
      } else {
        compileDirectInvoke(t, frame, target, tailCall, true, 0);
      }
    } else if (unresolved(t, methodAddress(t, target))
               or classNeedsInit(t, target->class_())) {
      compileDirectInvoke(t, frame, target, tailCall, true, 0);
    } else {
      compileDirectInvoke(t, frame, target, tailCall, false, 0);
    }
  }

  return tailCall;
}

void compileReferenceInvoke(Frame* frame,
                            ir::Value* method,
                            GcReference* reference,
                            bool isStatic,
                            bool tailCall)
{
  frame->referenceStackCall(isStatic,
                            method,
                            reference,
                            tailCall ? Compiler::TailJump : 0,
                            frame->trace(0, 0));
}

void compileDirectReferenceInvoke(MyThread* t,
                                  Frame* frame,
                                  Thunk thunk,
                                  GcReference* reference,
                                  bool isStatic,
                                  bool tailCall)
{
  avian::codegen::Compiler* c = frame->c;

  PROTECT(t, reference);

  GcPair* pair = makePair(t, frame->context->method, reference);

  compileReferenceInvoke(
      frame,
      c->nativeCall(c->constant(getThunk(t, thunk), ir::Type::iptr()),
                    0,
                    frame->trace(0, 0),
                    ir::Type::iptr(),
                    args(c->threadRegister(), frame->append(pair))),
      reference,
      isStatic,
      tailCall);
}

void compileAbstractInvoke(Frame* frame,
                           ir::Value* method,
                           GcMethod* target,
                           bool tailCall)
{
  frame->stackCall(
      method, target, tailCall ? Compiler::TailJump : 0, frame->trace(0, 0));
}

void compileDirectAbstractInvoke(MyThread* t,
                                 Frame* frame,
                                 Thunk thunk,
                                 GcMethod* target,
                                 bool tailCall)
{
  avian::codegen::Compiler* c = frame->c;

  compileAbstractInvoke(
      frame,
      c->nativeCall(c->constant(getThunk(t, thunk), ir::Type::iptr()),
                    0,
                    frame->trace(0, 0),
                    ir::Type::iptr(),
                    args(c->threadRegister(), frame->append(target))),
      target,
      tailCall);
}

void handleMonitorEvent(MyThread* t, Frame* frame, intptr_t function)
{
  avian::codegen::Compiler* c = frame->c;
  GcMethod* method = frame->context->method;

  if (method->flags() & ACC_SYNCHRONIZED) {
    ir::Value* lock;
    if (method->flags() & ACC_STATIC) {
      PROTECT(t, method);

      lock = frame->append(method->class_());
    } else {
      lock = loadLocal(
          frame->context, 1, ir::Type::object(), savedTargetIndex(t, method));
    }

    c->nativeCall(c->constant(function, ir::Type::iptr()),
                  0,
                  frame->trace(0, 0),
                  ir::Type::void_(),
                  args(c->threadRegister(), lock));
  }
}

void handleEntrance(MyThread* t, Frame* frame)
{
  GcMethod* method = frame->context->method;

  if ((method->flags() & (ACC_SYNCHRONIZED | ACC_STATIC)) == ACC_SYNCHRONIZED) {
    // save 'this' pointer in case it is overwritten.
    unsigned index = savedTargetIndex(t, method);
    storeLocal(frame->context,
               1,
               ir::Type::object(),
               loadLocal(frame->context, 1, ir::Type::object(), 0),
               index);
    frame->set(index, ir::Type::object());
  }

  handleMonitorEvent(t,
                     frame,
                     getThunk(t,
                              method->flags() & ACC_STATIC
                                  ? acquireMonitorForClassOnEntranceThunk
                                  : acquireMonitorForObjectOnEntranceThunk));
}

void handleExit(MyThread* t, Frame* frame)
{
  handleMonitorEvent(t,
                     frame,
                     getThunk(t,
                              frame->context->method->flags() & ACC_STATIC
                                  ? releaseMonitorForClassThunk
                                  : releaseMonitorForObjectThunk));
}

bool inTryBlock(MyThread* t UNUSED, GcCode* code, unsigned ip)
{
  GcExceptionHandlerTable* table
      = cast<GcExceptionHandlerTable>(t, code->exceptionHandlerTable());
  if (table) {
    unsigned length = table->length();
    for (unsigned i = 0; i < length; ++i) {
      uint64_t eh = table->body()[i];
      if (ip >= exceptionHandlerStart(eh) and ip < exceptionHandlerEnd(eh)) {
        return true;
      }
    }
  }
  return false;
}

bool needsReturnBarrier(MyThread* t UNUSED, GcMethod* method)
{
  return (method->flags() & ConstructorFlag)
         and (method->class_()->vmFlags() & HasFinalMemberFlag);
}

bool returnsNext(MyThread* t, GcCode* code, unsigned ip)
{
  switch (code->body()[ip]) {
  case return_:
  case areturn:
  case ireturn:
  case freturn:
  case lreturn:
  case dreturn:
    return true;

  case goto_: {
    uint32_t offset = codeReadInt16(t, code, ++ip);
    uint32_t newIp = (ip - 3) + offset;
    assertT(t, newIp < code->length());

    return returnsNext(t, code, newIp);
  }

  case goto_w: {
    uint32_t offset = codeReadInt32(t, code, ++ip);
    uint32_t newIp = (ip - 5) + offset;
    assertT(t, newIp < code->length());

    return returnsNext(t, code, newIp);
  }

  default:
    return false;
  }
}

bool isTailCall(MyThread* t,
                GcCode* code,
                unsigned ip,
                GcMethod* caller,
                int calleeReturnCode,
                GcByteArray* calleeClassName,
                GcByteArray* calleeMethodName,
                GcByteArray* calleeMethodSpec)
{
  return avian::codegen::TailCalls
         and ((caller->flags() & ACC_SYNCHRONIZED) == 0)
         and (not inTryBlock(t, code, ip - 1))
         and (not needsReturnBarrier(t, caller))
         and (caller->returnCode() == VoidField
              or caller->returnCode() == calleeReturnCode)
         and returnsNext(t, code, ip)
         and t->m->classpath->canTailCall(t,
                                          caller,
                                          calleeClassName,
                                          calleeMethodName,
                                          calleeMethodSpec);
}

bool isTailCall(MyThread* t,
                GcCode* code,
                unsigned ip,
                GcMethod* caller,
                GcMethod* callee)
{
  return isTailCall(t,
                    code,
                    ip,
                    caller,
                    callee->returnCode(),
                    callee->class_()->name(),
                    callee->name(),
                    callee->spec());
}

bool isReferenceTailCall(MyThread* t,
                         GcCode* code,
                         unsigned ip,
                         GcMethod* caller,
                         GcReference* calleeReference)
{
  return isTailCall(t,
                    code,
                    ip,
                    caller,
                    methodReferenceReturnCode(t, calleeReference),
                    calleeReference->class_(),
                    calleeReference->name(),
                    calleeReference->spec());
}

lir::TernaryOperation toCompilerJumpOp(MyThread* t, unsigned instruction)
{
  switch (instruction) {
  case ifeq:
  case if_icmpeq:
  case if_acmpeq:
  case ifnull:
    return lir::JumpIfEqual;
  case ifne:
  case if_icmpne:
  case if_acmpne:
  case ifnonnull:
    return lir::JumpIfNotEqual;
  case ifgt:
  case if_icmpgt:
    return lir::JumpIfGreater;
  case ifge:
  case if_icmpge:
    return lir::JumpIfGreaterOrEqual;
  case iflt:
  case if_icmplt:
    return lir::JumpIfLess;
  case ifle:
  case if_icmple:
    return lir::JumpIfLessOrEqual;
  default:
    abort(t);
  }
}

bool integerBranch(MyThread* t,
                   Frame* frame,
                   GcCode* code,
                   unsigned& ip,
                   ir::Value* a,
                   ir::Value* b,
                   unsigned* newIpp)
{
  if (ip + 3 > code->length()) {
    return false;
  }

  avian::codegen::Compiler* c = frame->c;
  unsigned instruction = code->body()[ip++];
  uint32_t offset = codeReadInt16(t, code, ip);
  uint32_t newIp = (ip - 3) + offset;
  assertT(t, newIp < code->length());

  ir::Value* target = frame->machineIpValue(newIp);

  switch (instruction) {
  case ifeq:
  case ifne:
  case ifgt:
  case ifge:
  case iflt:
  case ifle:
    c->condJump(toCompilerJumpOp(t, instruction), a, b, target);
    break;

  default:
    ip -= 3;
    return false;
  }

  *newIpp = newIp;
  return true;
}

lir::TernaryOperation toCompilerFloatJumpOp(MyThread* t,
                                            unsigned instruction,
                                            bool lessIfUnordered)
{
  switch (instruction) {
  case ifeq:
    return lir::JumpIfFloatEqual;
  case ifne:
    return lir::JumpIfFloatNotEqual;
  case ifgt:
    if (lessIfUnordered) {
      return lir::JumpIfFloatGreater;
    } else {
      return lir::JumpIfFloatGreaterOrUnordered;
    }
  case ifge:
    if (lessIfUnordered) {
      return lir::JumpIfFloatGreaterOrEqual;
    } else {
      return lir::JumpIfFloatGreaterOrEqualOrUnordered;
    }
  case iflt:
    if (lessIfUnordered) {
      return lir::JumpIfFloatLessOrUnordered;
    } else {
      return lir::JumpIfFloatLess;
    }
  case ifle:
    if (lessIfUnordered) {
      return lir::JumpIfFloatLessOrEqualOrUnordered;
    } else {
      return lir::JumpIfFloatLessOrEqual;
    }
  default:
    abort(t);
  }
}

bool floatBranch(MyThread* t,
                 Frame* frame,
                 GcCode* code,
                 unsigned& ip,
                 bool lessIfUnordered,
                 ir::Value* a,
                 ir::Value* b,
                 unsigned* newIpp)
{
  if (ip + 3 > code->length()) {
    return false;
  }

  avian::codegen::Compiler* c = frame->c;
  unsigned instruction = code->body()[ip++];
  uint32_t offset = codeReadInt16(t, code, ip);
  uint32_t newIp = (ip - 3) + offset;
  assertT(t, newIp < code->length());

  ir::Value* target = frame->machineIpValue(newIp);

  switch (instruction) {
  case ifeq:
  case ifne:
  case ifgt:
  case ifge:
  case iflt:
  case ifle:
    c->condJump(
        toCompilerFloatJumpOp(t, instruction, lessIfUnordered), a, b, target);
    break;

  default:
    ip -= 3;
    return false;
  }

  *newIpp = newIp;
  return true;
}

ir::Value* popLongAddress(Frame* frame)
{
  return TargetBytesPerWord == 8
             ? frame->popLarge(ir::Type::i8())
             : frame->c->load(ir::ExtendMode::Signed,
                              frame->popLarge(ir::Type::i8()),
                              ir::Type::iptr());
}

bool intrinsic(MyThread* t UNUSED, Frame* frame, GcMethod* target)
{
#define MATCH(name, constant)         \
  (name->length() == sizeof(constant) \
   and ::strcmp(reinterpret_cast<char*>(name->body().begin()), constant) == 0)

  GcByteArray* className = target->class_()->name();
  if (UNLIKELY(MATCH(className, "java/lang/Math"))) {
    avian::codegen::Compiler* c = frame->c;
    if (MATCH(target->name(), "sqrt") and MATCH(target->spec(), "(D)D")) {
      frame->pushLarge(
          ir::Type::f8(),
          c->unaryOp(lir::FloatSquareRoot, frame->popLarge(ir::Type::f8())));
      return true;
    } else if (MATCH(target->name(), "abs")) {
      if (MATCH(target->spec(), "(I)I")) {
        frame->push(ir::Type::i4(),
                    c->unaryOp(lir::Absolute, frame->pop(ir::Type::i4())));
        return true;
      } else if (MATCH(target->spec(), "(J)J")) {
        frame->pushLarge(
            ir::Type::i8(),
            c->unaryOp(lir::Absolute, frame->popLarge(ir::Type::i8())));
        return true;
      } else if (MATCH(target->spec(), "(F)F")) {
        frame->push(ir::Type::f4(),
                    c->unaryOp(lir::FloatAbsolute, frame->pop(ir::Type::f4())));
        return true;
      }
    }
  } else if (UNLIKELY(MATCH(className, "sun/misc/Unsafe"))) {
    avian::codegen::Compiler* c = frame->c;
    if (MATCH(target->name(), "getByte") and MATCH(target->spec(), "(J)B")) {
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      frame->push(ir::Type::i4(),
                  c->load(ir::ExtendMode::Signed,
                          c->memory(address, ir::Type::i1()),
                          ir::Type::i4()));
      return true;
    } else if (MATCH(target->name(), "putByte")
               and MATCH(target->spec(), "(JB)V")) {
      ir::Value* value = frame->pop(ir::Type::i4());
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      c->store(value, c->memory(address, ir::Type::i1()));
      return true;
    } else if ((MATCH(target->name(), "getShort")
                and MATCH(target->spec(), "(J)S"))
               or (MATCH(target->name(), "getChar")
                   and MATCH(target->spec(), "(J)C"))) {
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      frame->push(ir::Type::i4(),
                  c->load(ir::ExtendMode::Signed,
                          c->memory(address, ir::Type::i2()),
                          ir::Type::i4()));
      return true;
    } else if ((MATCH(target->name(), "putShort")
                and MATCH(target->spec(), "(JS)V"))
               or (MATCH(target->name(), "putChar")
                   and MATCH(target->spec(), "(JC)V"))) {
      ir::Value* value = frame->pop(ir::Type::i4());
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      c->store(value, c->memory(address, ir::Type::i2()));
      return true;
    } else if ((MATCH(target->name(), "getInt")
                and MATCH(target->spec(), "(J)I"))
               or (MATCH(target->name(), "getFloat")
                   and MATCH(target->spec(), "(J)F"))) {
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      ir::Type type = MATCH(target->name(), "getInt") ? ir::Type::i4()
                                                      : ir::Type::f4();
      frame->push(
          type,
          c->load(ir::ExtendMode::Signed, c->memory(address, type), type));
      return true;
    } else if ((MATCH(target->name(), "putInt")
                and MATCH(target->spec(), "(JI)V"))
               or (MATCH(target->name(), "putFloat")
                   and MATCH(target->spec(), "(JF)V"))) {
      ir::Type type = MATCH(target->name(), "putInt") ? ir::Type::i4()
                                                      : ir::Type::f4();
      ir::Value* value = frame->pop(type);
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      c->store(value, c->memory(address, type));
      return true;
    } else if ((MATCH(target->name(), "getLong")
                and MATCH(target->spec(), "(J)J"))
               or (MATCH(target->name(), "getDouble")
                   and MATCH(target->spec(), "(J)D"))) {
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      ir::Type type = MATCH(target->name(), "getLong") ? ir::Type::i8()
                                                       : ir::Type::f8();
      frame->pushLarge(
          type,
          c->load(ir::ExtendMode::Signed, c->memory(address, type), type));
      return true;
    } else if ((MATCH(target->name(), "putLong")
                and MATCH(target->spec(), "(JJ)V"))
               or (MATCH(target->name(), "putDouble")
                   and MATCH(target->spec(), "(JD)V"))) {
      ir::Type type = MATCH(target->name(), "putLong") ? ir::Type::i8()
                                                       : ir::Type::f8();
      ir::Value* value = frame->popLarge(type);
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      c->store(value, c->memory(address, type));
      return true;
    } else if (MATCH(target->name(), "getAddress")
               and MATCH(target->spec(), "(J)J")) {
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      frame->pushLarge(ir::Type::i8(),
                       c->load(ir::ExtendMode::Signed,
                               c->memory(address, ir::Type::iptr()),
                               ir::Type::i8()));
      return true;
    } else if (MATCH(target->name(), "putAddress")
               and MATCH(target->spec(), "(JJ)V")) {
      ir::Value* value = frame->popLarge(ir::Type::i8());
      ir::Value* address = popLongAddress(frame);
      frame->pop(ir::Type::object());
      c->store(value, c->memory(address, ir::Type::iptr()));
      return true;
    }
  }
  return false;
}

unsigned targetFieldOffset(Context* context, GcField* field)
{
  if (context->bootContext) {
    return context->bootContext->resolver->fieldOffset(context->thread, field);
  } else {
    return field->offset();
  }
}

class Stack {
 public:
  class MyResource : public Thread::AutoResource {
   public:
    MyResource(Stack* s) : AutoResource(s->thread), s(s)
    {
    }

    virtual void release()
    {
      s->zone.dispose();
    }

    Stack* s;
  };

  Stack(MyThread* t) : thread(t), zone(t->m->heap, 0), resource(this)
  {
  }

  ~Stack()
  {
    zone.dispose();
  }

  void pushValue(uintptr_t v)
  {
    *static_cast<uintptr_t*>(push(BytesPerWord)) = v;
  }

  uintptr_t peekValue(unsigned offset)
  {
    return *static_cast<uintptr_t*>(peek((offset + 1) * BytesPerWord));
  }

  uintptr_t popValue()
  {
    uintptr_t v = peekValue(0);
    pop(BytesPerWord);
    return v;
  }

  void* push(unsigned size)
  {
    return zone.allocate(size);
  }

  void* peek(unsigned size)
  {
    return zone.peek(size);
  }

  void pop(unsigned size)
  {
    zone.pop(size);
  }

  MyThread* thread;
  Zone zone;
  MyResource resource;
};

class SwitchState {
 public:
  SwitchState(Compiler::State* state,
              unsigned count,
              unsigned defaultIp,
              ir::Value* key,
              avian::codegen::Promise* start,
              int bottom,
              int top)
      : state(state),
        count(count),
        defaultIp(defaultIp),
        key(key),
        start(start),
        bottom(bottom),
        top(top),
        index(0)
  {
  }

  Frame* frame()
  {
    return reinterpret_cast<Frame*>(reinterpret_cast<uint8_t*>(this)
                                    - pad(count * 4) - pad(sizeof(Frame)));
  }

  uint32_t* ipTable()
  {
    return reinterpret_cast<uint32_t*>(reinterpret_cast<uint8_t*>(this)
                                       - pad(count * 4));
  }

  Compiler::State* state;
  unsigned count;
  unsigned defaultIp;
  ir::Value* key;
  avian::codegen::Promise* start;
  int bottom;
  int top;
  unsigned index;
};

lir::TernaryOperation toCompilerBinaryOp(MyThread* t, unsigned instruction)
{
  switch (instruction) {
  case iadd:
  case ladd:
    return lir::Add;
  case ior:
  case lor:
    return lir::Or;
  case ishl:
  case lshl:
    return lir::ShiftLeft;
  case ishr:
  case lshr:
    return lir::ShiftRight;
  case iushr:
  case lushr:
    return lir::UnsignedShiftRight;
  case fadd:
  case dadd:
    return lir::FloatAdd;
  case fsub:
  case dsub:
    return lir::FloatSubtract;
  case fmul:
  case dmul:
    return lir::FloatMultiply;
  case fdiv:
  case ddiv:
    return lir::FloatDivide;
  case frem:
  case vm::drem:
    return lir::FloatRemainder;
  case iand:
  case land:
    return lir::And;
  case isub:
  case lsub:
    return lir::Subtract;
  case ixor:
  case lxor:
    return lir::Xor;
  case imul:
  case lmul:
    return lir::Multiply;
  default:
    abort(t);
  }
}

uintptr_t aioobThunk(MyThread* t);

uintptr_t stackOverflowThunk(MyThread* t);

void checkField(Thread* t, GcField* field, bool shouldBeStatic)
{
  if (((field->flags() & ACC_STATIC) == 0) == shouldBeStatic) {
    throwNew(t,
             GcIncompatibleClassChangeError::Type,
             "expected %s.%s to be %s",
             field->class_()->name()->body().begin(),
             field->name()->body().begin(),
             shouldBeStatic ? "static" : "non-static");
  }
}

bool isLambda(Thread* t,
              GcClassLoader* loader,
              GcCharArray* bootstrapArray,
              GcInvocation* invocation)
{
  GcMethod* bootstrap = cast<GcMethodHandle>(t,
                                       resolve(t,
                                               loader,
                                               invocation->pool(),
                                               bootstrapArray->body()[0],
                                               findMethodInClass,
                                               GcNoSuchMethodError::Type))->method();
  PROTECT(t, bootstrap);

  return vm::strcmp(reinterpret_cast<const int8_t*>(
                        "java/lang/invoke/LambdaMetafactory"),
                    bootstrap->class_()->name()->body().begin()) == 0
         and ((vm::strcmp(reinterpret_cast<const int8_t*>("metafactory"),
                          bootstrap->name()->body().begin()) == 0
               and vm::strcmp(
                       reinterpret_cast<const int8_t*>(
                           "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/"
                           "String;Ljava/lang/invoke/MethodType;Ljava/lang/"
                           "invoke/"
                           "MethodType;Ljava/lang/invoke/MethodHandle;Ljava/"
                           "lang/"
                           "invoke/MethodType;)Ljava/lang/invoke/CallSite;"),
                       bootstrap->spec()->body().begin()) == 0)
              or (vm::strcmp(reinterpret_cast<const int8_t*>("altMetafactory"),
                             bootstrap->name()->body().begin()) == 0
                  and vm::strcmp(
                          reinterpret_cast<const int8_t*>(
                              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/"
                              "lang/"
                              "String;Ljava/lang/invoke/MethodType;[Ljava/lang/"
                              "Object;)Ljava/lang/invoke/CallSite;"),
                          bootstrap->spec()->body().begin()) == 0));
}

void compile(MyThread* t,
             Frame* initialFrame,
             unsigned initialIp,
             int exceptionHandlerStart = -1)
{
  enum { Return, Unbranch, Unsubroutine, Untable0, Untable1, Unswitch };

  Frame* frame = initialFrame;
  avian::codegen::Compiler* c = frame->c;
  Context* context = frame->context;
  unsigned stackSize = context->method->code()->maxStack();
  Stack stack(t);
  unsigned ip = initialIp;
  unsigned newIp;
  stack.pushValue(Return);

start:
  ir::Type* stackMap
      = static_cast<ir::Type*>(stack.push(stackSize * sizeof(ir::Type)));
  frame = new (stack.push(sizeof(Frame))) Frame(frame, stackMap);

loop:
  GcCode* code = context->method->code();
  PROTECT(t, code);

  while (ip < code->length()) {
    if (context->visitTable[frame->duplicatedIp(ip)]++) {
      // we've already visited this part of the code
      frame->visitLogicalIp(ip);
      goto next;
    }

    frame->startLogicalIp(ip);

    if (exceptionHandlerStart >= 0) {
      c->initLocalsFromLogicalIp(exceptionHandlerStart);

      exceptionHandlerStart = -1;

      frame->pushObject();

      c->nativeCall(
          c->constant(getThunk(t, gcIfNecessaryThunk), ir::Type::iptr()),
          0,
          frame->trace(0, 0),
          ir::Type::void_(),
          args(c->threadRegister()));
    }

    if (DebugInstructions) {
      unsigned startingIp = ip;
      fprintf(stderr, " stack: [");
      for (size_t i = frame->localSize(); i < frame->sp; i++) {
        ir::Type ty = frame->get(i);
        if (ty == ir::Type::i4()) {
          fprintf(stderr, "I");
        } else if (ty == ir::Type::i8()) {
          fprintf(stderr, "L");
        } else if (ty == ir::Type::f4()) {
          fprintf(stderr, "F");
        } else if (ty == ir::Type::f8()) {
          fprintf(stderr, "D");
        } else if (ty == ir::Type::object()) {
          fprintf(stderr, "O");
        } else {
          fprintf(stderr, "?");
        }
      }
      fprintf(stderr, "]\n");
      fprintf(stderr, "% 5d: ", startingIp);
      avian::jvm::debug::printInstruction(code->body().begin(), startingIp);
      fprintf(stderr, "\n");
    }

    unsigned instruction = code->body()[ip++];

    switch (instruction) {
    case aaload:
    case baload:
    case caload:
    case daload:
    case faload:
    case iaload:
    case laload:
    case saload: {
      ir::Value* index = frame->pop(ir::Type::i4());
      ir::Value* array = frame->pop(ir::Type::object());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      if (CheckArrayBounds) {
        c->checkBounds(array, TargetArrayLength, index, aioobThunk(t));
      }

      switch (instruction) {
      case aaload:
        frame->push(
            ir::Type::object(),
            c->load(
                ir::ExtendMode::Signed,
                c->memory(array, ir::Type::object(), TargetArrayBody, index),
                ir::Type::object()));
        break;

      case faload:
        frame->push(
            ir::Type::f4(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::f4(), TargetArrayBody, index),
                    ir::Type::f4()));
        break;

      case iaload:
        frame->push(
            ir::Type::i4(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::i4(), TargetArrayBody, index),
                    ir::Type::i4()));
        break;

      case baload:
        frame->push(
            ir::Type::i4(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::i1(), TargetArrayBody, index),
                    ir::Type::i4()));
        break;

      case caload:
        frame->push(
            ir::Type::i4(),
            c->load(ir::ExtendMode::Unsigned,
                    c->memory(array, ir::Type::i2(), TargetArrayBody, index),
                    ir::Type::i4()));
        break;

      case daload:
        frame->pushLarge(
            ir::Type::f8(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::f8(), TargetArrayBody, index),
                    ir::Type::f8()));
        break;

      case laload:
        frame->pushLarge(
            ir::Type::i8(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::i8(), TargetArrayBody, index),
                    ir::Type::i8()));
        break;

      case saload:
        frame->push(
            ir::Type::i4(),
            c->load(ir::ExtendMode::Signed,
                    c->memory(array, ir::Type::i2(), TargetArrayBody, index),
                    ir::Type::i4()));
        break;
      }
    } break;

    case aastore:
    case bastore:
    case castore:
    case dastore:
    case fastore:
    case iastore:
    case lastore:
    case sastore: {
      ir::Value* value;
      if (instruction == lastore) {
        value = frame->popLarge(ir::Type::i8());
      } else if (instruction == dastore) {
        value = frame->popLarge(ir::Type::f8());
      } else if (instruction == aastore) {
        value = frame->pop(ir::Type::object());
      } else if (instruction == fastore) {
        value = frame->pop(ir::Type::f4());
      } else {
        value = frame->pop(ir::Type::i4());
      }

      ir::Value* index = frame->pop(ir::Type::i4());
      ir::Value* array = frame->pop(ir::Type::object());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      if (CheckArrayBounds) {
        c->checkBounds(array, TargetArrayLength, index, aioobThunk(t));
      }

      switch (instruction) {
      case aastore: {
        c->nativeCall(
            c->constant(getThunk(t, setMaybeNullThunk), ir::Type::iptr()),
            0,
            frame->trace(0, 0),
            ir::Type::void_(),
            args(c->threadRegister(),
                 array,
                 c->binaryOp(lir::Add,
                             ir::Type::i4(),
                             c->constant(TargetArrayBody, ir::Type::i4()),
                             c->binaryOp(lir::ShiftLeft,
                                         ir::Type::i4(),
                                         c->constant(log(TargetBytesPerWord),
                                                     ir::Type::i4()),
                                         index)),
                 value));
      } break;

      case fastore:
        c->store(value,
                 c->memory(array, ir::Type::f4(), TargetArrayBody, index));
        break;

      case iastore:
        c->store(value,
                 c->memory(array, ir::Type::i4(), TargetArrayBody, index));
        break;

      case bastore:
        c->store(value,
                 c->memory(array, ir::Type::i1(), TargetArrayBody, index));
        break;

      case castore:
      case sastore:
        c->store(value,
                 c->memory(array, ir::Type::i2(), TargetArrayBody, index));
        break;

      case dastore:
        c->store(value,
                 c->memory(array, ir::Type::f8(), TargetArrayBody, index));
        break;

      case lastore:
        c->store(value,
                 c->memory(array, ir::Type::i8(), TargetArrayBody, index));
        break;
      }
    } break;

    case aconst_null:
      frame->push(ir::Type::object(), c->constant(0, ir::Type::object()));
      break;

    case aload:
      frame->load(ir::Type::object(), code->body()[ip++]);
      break;

    case aload_0:
      frame->load(ir::Type::object(), 0);
      break;

    case aload_1:
      frame->load(ir::Type::object(), 1);
      break;

    case aload_2:
      frame->load(ir::Type::object(), 2);
      break;

    case aload_3:
      frame->load(ir::Type::object(), 3);
      break;

    case anewarray: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcClass* class_
          = resolveClassInPool(t, context->method, index - 1, false);

      ir::Value* length = frame->pop(ir::Type::i4());

      object argument;
      Thunk thunk;
      if (LIKELY(class_)) {
        argument = class_;
        thunk = makeBlankObjectArrayThunk;
      } else {
        argument = makePair(t, context->method, reference);
        thunk = makeBlankObjectArrayFromReferenceThunk;
      }

      frame->push(
          ir::Type::object(),
          c->nativeCall(
              c->constant(getThunk(t, thunk), ir::Type::iptr()),
              0,
              frame->trace(0, 0),
              ir::Type::object(),
              args(c->threadRegister(), frame->append(argument), length)));
    } break;

    case areturn: {
      handleExit(t, frame);
      c->return_(frame->pop(ir::Type::object()));
    }
      goto next;

    case arraylength: {
      frame->push(ir::Type::i4(),
                  c->load(ir::ExtendMode::Signed,
                          c->memory(frame->pop(ir::Type::object()),
                                    ir::Type::iptr(),
                                    TargetArrayLength),
                          ir::Type::i4()));
    } break;

    case astore:
      frame->store(ir::Type::object(), code->body()[ip++]);
      break;

    case astore_0:
      frame->store(ir::Type::object(), 0);
      break;

    case astore_1:
      frame->store(ir::Type::object(), 1);
      break;

    case astore_2:
      frame->store(ir::Type::object(), 2);
      break;

    case astore_3:
      frame->store(ir::Type::object(), 3);
      break;

    case athrow: {
      ir::Value* target = frame->pop(ir::Type::object());
      c->nativeCall(c->constant(getThunk(t, throw_Thunk), ir::Type::iptr()),
                    Compiler::NoReturn,
                    frame->trace(0, 0),
                    ir::Type::void_(),
                    args(c->threadRegister(), target));

      c->nullaryOp(lir::Trap);
    }
      goto next;

    case bipush:
      frame->push(
          ir::Type::i4(),
          c->constant(static_cast<int8_t>(code->body()[ip++]), ir::Type::i4()));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcClass* class_
          = resolveClassInPool(t, context->method, index - 1, false);

      object argument;
      Thunk thunk;
      if (LIKELY(class_)) {
        argument = class_;
        thunk = checkCastThunk;
      } else {
        argument = makePair(t, context->method, reference);
        thunk = checkCastFromReferenceThunk;
      }

      ir::Value* instance = c->peek(1, 0);

      c->nativeCall(
          c->constant(getThunk(t, thunk), ir::Type::iptr()),
          0,
          frame->trace(0, 0),
          ir::Type::void_(),
          args(c->threadRegister(), frame->append(argument), instance));
    } break;

    case d2f: {
      frame->push(ir::Type::f4(),
                  c->f2f(ir::Type::f4(), frame->popLarge(ir::Type::f8())));
    } break;

    case d2i: {
      frame->push(ir::Type::i4(),
                  c->f2i(ir::Type::i4(), frame->popLarge(ir::Type::f8())));
    } break;

    case d2l: {
      frame->pushLarge(ir::Type::i8(),
                       c->f2i(ir::Type::i8(), frame->popLarge(ir::Type::f8())));
    } break;

    case dadd:
    case dsub:
    case dmul:
    case ddiv:
    case vm::drem: {
      ir::Value* a = frame->popLarge(ir::Type::f8());
      ir::Value* b = frame->popLarge(ir::Type::f8());

      frame->pushLarge(
          ir::Type::f8(),
          c->binaryOp(
              toCompilerBinaryOp(t, instruction), ir::Type::f8(), a, b));
    } break;

    case dcmpg: {
      ir::Value* a = frame->popLarge(ir::Type::f8());
      ir::Value* b = frame->popLarge(ir::Type::f8());

      if (floatBranch(t, frame, code, ip, false, a, b, &newIp)) {
        goto branch;
      } else {
        frame->push(ir::Type::i4(),
                    c->nativeCall(c->constant(getThunk(t, compareDoublesGThunk),
                                              ir::Type::iptr()),
                                  0,
                                  0,
                                  ir::Type::i4(),
                                  args(nullptr, a, nullptr, b)));
      }
    } break;

    case dcmpl: {
      ir::Value* a = frame->popLarge(ir::Type::f8());
      ir::Value* b = frame->popLarge(ir::Type::f8());

      if (floatBranch(t, frame, code, ip, true, a, b, &newIp)) {
        goto branch;
      } else {
        frame->push(ir::Type::i4(),
                    c->nativeCall(c->constant(getThunk(t, compareDoublesLThunk),
                                              ir::Type::iptr()),
                                  0,
                                  0,
                                  ir::Type::i4(),
                                  args(nullptr, a, nullptr, b)));
      }
    } break;

    case dconst_0:
      frame->pushLarge(ir::Type::f8(),
                       c->constant(doubleToBits(0.0), ir::Type::f8()));
      break;

    case dconst_1:
      frame->pushLarge(ir::Type::f8(),
                       c->constant(doubleToBits(1.0), ir::Type::f8()));
      break;

    case dneg: {
      frame->pushLarge(
          ir::Type::f8(),
          c->unaryOp(lir::FloatNegate, frame->popLarge(ir::Type::f8())));
    } break;

    case vm::dup:
      frame->dup();
      break;

    case dup_x1:
      frame->dupX1();
      break;

    case dup_x2:
      frame->dupX2();
      break;

    case vm::dup2:
      frame->dup2();
      break;

    case dup2_x1:
      frame->dup2X1();
      break;

    case dup2_x2:
      frame->dup2X2();
      break;

    case f2d: {
      frame->pushLarge(ir::Type::f8(),
                       c->f2f(ir::Type::f8(), frame->pop(ir::Type::f4())));
    } break;

    case f2i: {
      frame->push(ir::Type::i4(),
                  c->f2i(ir::Type::i4(), frame->pop(ir::Type::f4())));
    } break;

    case f2l: {
      frame->pushLarge(ir::Type::i8(),
                       c->f2i(ir::Type::i8(), frame->pop(ir::Type::f4())));
    } break;

    case fadd:
    case fsub:
    case fmul:
    case fdiv:
    case frem: {
      ir::Value* a = frame->pop(ir::Type::f4());
      ir::Value* b = frame->pop(ir::Type::f4());

      frame->push(
          ir::Type::f4(),
          c->binaryOp(
              toCompilerBinaryOp(t, instruction), ir::Type::f4(), a, b));
    } break;

    case fcmpg: {
      ir::Value* a = frame->pop(ir::Type::f4());
      ir::Value* b = frame->pop(ir::Type::f4());

      if (floatBranch(t, frame, code, ip, false, a, b, &newIp)) {
        goto branch;
      } else {
        frame->push(ir::Type::i4(),
                    c->nativeCall(c->constant(getThunk(t, compareFloatsGThunk),
                                              ir::Type::iptr()),
                                  0,
                                  0,
                                  ir::Type::i4(),
                                  args(a, b)));
      }
    } break;

    case fcmpl: {
      ir::Value* a = frame->pop(ir::Type::f4());
      ir::Value* b = frame->pop(ir::Type::f4());

      if (floatBranch(t, frame, code, ip, true, a, b, &newIp)) {
        goto branch;
      } else {
        frame->push(ir::Type::i4(),
                    c->nativeCall(c->constant(getThunk(t, compareFloatsLThunk),
                                              ir::Type::iptr()),
                                  0,
                                  0,
                                  ir::Type::i4(),
                                  args(a, b)));
      }
    } break;

    case fconst_0:
      frame->push(ir::Type::f4(),
                  c->constant(floatToBits(0.0), ir::Type::f4()));
      break;

    case fconst_1:
      frame->push(ir::Type::f4(),
                  c->constant(floatToBits(1.0), ir::Type::f4()));
      break;

    case fconst_2:
      frame->push(ir::Type::f4(),
                  c->constant(floatToBits(2.0), ir::Type::f4()));
      break;

    case fneg: {
      frame->push(ir::Type::f4(),
                  c->unaryOp(lir::FloatNegate, frame->pop(ir::Type::f4())));
    } break;

    case getfield:
    case getstatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcField* field = resolveField(t, context->method, index - 1, false);

      if (LIKELY(field)) {
        if ((field->flags() & ACC_VOLATILE) and TargetBytesPerWord == 4
            and (field->code() == DoubleField or field->code() == LongField)) {
          PROTECT(t, field);

          c->nativeCall(c->constant(getThunk(t, acquireMonitorForObjectThunk),
                                    ir::Type::iptr()),
                        0,
                        frame->trace(0, 0),
                        ir::Type::void_(),
                        args(c->threadRegister(), frame->append(field)));
        }

        ir::Value* table;

        if (instruction == getstatic) {
          checkField(t, field, true);

          PROTECT(t, field);

          if (classNeedsInit(t, field->class_())) {
            c->nativeCall(
                c->constant(getThunk(t, tryInitClassThunk), ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                ir::Type::void_(),
                args(c->threadRegister(), frame->append(field->class_())));
          }

          table = frame->append(field->class_()->staticTable());
        } else {
          checkField(t, field, false);

          table = frame->pop(ir::Type::object());

          if (inTryBlock(t, code, ip - 3)) {
            c->saveLocals();
            frame->trace(0, 0);
          }
        }

        switch (field->code()) {
        case ByteField:
        case BooleanField:
          frame->push(ir::Type::i4(),
                      c->load(ir::ExtendMode::Signed,
                              c->memory(table,
                                        ir::Type::i1(),
                                        targetFieldOffset(context, field)),
                              ir::Type::i4()));
          break;

        case CharField:
          frame->push(ir::Type::i4(),
                      c->load(ir::ExtendMode::Unsigned,
                              c->memory(table,
                                        ir::Type::i2(),
                                        targetFieldOffset(context, field)),
                              ir::Type::i4()));
          break;

        case ShortField:
          frame->push(ir::Type::i4(),
                      c->load(ir::ExtendMode::Signed,
                              c->memory(table,
                                        ir::Type::i2(),
                                        targetFieldOffset(context, field)),
                              ir::Type::i4()));
          break;

        case FloatField:
          frame->push(ir::Type::f4(),
                      c->load(ir::ExtendMode::Signed,
                              c->memory(table,
                                        ir::Type::f4(),
                                        targetFieldOffset(context, field)),
                              ir::Type::f4()));
          break;

        case IntField:
          frame->push(ir::Type::i4(),
                      c->load(ir::ExtendMode::Signed,
                              c->memory(table,
                                        ir::Type::i4(),
                                        targetFieldOffset(context, field)),
                              ir::Type::i4()));
          break;

        case DoubleField:
          frame->pushLarge(ir::Type::f8(),
                           c->load(ir::ExtendMode::Signed,
                                   c->memory(table,
                                             ir::Type::f8(),
                                             targetFieldOffset(context, field)),
                                   ir::Type::f8()));
          break;

        case LongField:
          frame->pushLarge(ir::Type::i8(),
                           c->load(ir::ExtendMode::Signed,
                                   c->memory(table,
                                             ir::Type::i8(),
                                             targetFieldOffset(context, field)),
                                   ir::Type::i8()));
          break;

        case ObjectField:
          frame->push(ir::Type::object(),
                      c->load(ir::ExtendMode::Signed,
                              c->memory(table,
                                        ir::Type::object(),
                                        targetFieldOffset(context, field)),
                              ir::Type::object()));
          break;

        default:
          abort(t);
        }

        if (field->flags() & ACC_VOLATILE) {
          if (TargetBytesPerWord == 4 and (field->code() == DoubleField
                                           or field->code() == LongField)) {
            c->nativeCall(c->constant(getThunk(t, releaseMonitorForObjectThunk),
                                      ir::Type::iptr()),
                          0,
                          frame->trace(0, 0),
                          ir::Type::void_(),
                          args(c->threadRegister(), frame->append(field)));
          } else {
            c->nullaryOp(lir::LoadBarrier);
          }
        }
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, ref);
        int fieldCode = vm::fieldCode(t, ref->spec()->body()[0]);

        GcPair* pair = makePair(t, context->method, reference);

        ir::Type rType = operandTypeForFieldCode(t, fieldCode);

        ir::Value* result;
        if (instruction == getstatic) {
          result = c->nativeCall(
              c->constant(getThunk(t, getStaticFieldValueFromReferenceThunk),
                          ir::Type::iptr()),
              0,
              frame->trace(0, 0),
              rType,
              args(c->threadRegister(), frame->append(pair)));
        } else {
          ir::Value* instance = frame->pop(ir::Type::object());

          result = c->nativeCall(
              c->constant(getThunk(t, getFieldValueFromReferenceThunk),
                          ir::Type::iptr()),
              0,
              frame->trace(0, 0),
              rType,
              args(c->threadRegister(), frame->append(pair), instance));
        }

        frame->pushReturnValue(fieldCode, result);
      }
    } break;

    case goto_: {
      uint32_t offset = codeReadInt16(t, code, ip);
      uint32_t newIp = (ip - 3) + offset;
      assertT(t, newIp < code->length());

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      c->jmp(frame->machineIpValue(newIp));
      ip = newIp;
    } break;

    case goto_w: {
      uint32_t offset = codeReadInt32(t, code, ip);
      uint32_t newIp = (ip - 5) + offset;
      assertT(t, newIp < code->length());

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      c->jmp(frame->machineIpValue(newIp));
      ip = newIp;
    } break;

    case i2b: {
      frame->push(ir::Type::i4(),
                  c->truncateThenExtend(ir::ExtendMode::Signed,
                                        ir::Type::i4(),
                                        ir::Type::i1(),
                                        frame->pop(ir::Type::i4())));
    } break;

    case i2c: {
      frame->push(ir::Type::i4(),
                  c->truncateThenExtend(ir::ExtendMode::Unsigned,
                                        ir::Type::i4(),
                                        ir::Type::i2(),
                                        frame->pop(ir::Type::i4())));
    } break;

    case i2d: {
      frame->pushLarge(ir::Type::f8(),
                       c->i2f(ir::Type::f8(), frame->pop(ir::Type::i4())));
    } break;

    case i2f: {
      frame->push(ir::Type::f4(),
                  c->i2f(ir::Type::f4(), frame->pop(ir::Type::i4())));
    } break;

    case i2l:
      frame->pushLarge(ir::Type::i8(),
                       c->truncateThenExtend(ir::ExtendMode::Signed,
                                             ir::Type::i8(),
                                             ir::Type::i4(),
                                             frame->pop(ir::Type::i4())));
      break;

    case i2s: {
      frame->push(ir::Type::i4(),
                  c->truncateThenExtend(ir::ExtendMode::Signed,
                                        ir::Type::i4(),
                                        ir::Type::i2(),
                                        frame->pop(ir::Type::i4())));
    } break;

    case iadd:
    case iand:
    case ior:
    case ishl:
    case ishr:
    case iushr:
    case isub:
    case ixor:
    case imul: {
      ir::Value* a = frame->pop(ir::Type::i4());
      ir::Value* b = frame->pop(ir::Type::i4());
      frame->push(
          ir::Type::i4(),
          c->binaryOp(
              toCompilerBinaryOp(t, instruction), ir::Type::i4(), a, b));
    } break;

    case iconst_m1:
      frame->push(ir::Type::i4(), c->constant(-1, ir::Type::i4()));
      break;

    case iconst_0:
      frame->push(ir::Type::i4(), c->constant(0, ir::Type::i4()));
      break;

    case iconst_1:
      frame->push(ir::Type::i4(), c->constant(1, ir::Type::i4()));
      break;

    case iconst_2:
      frame->push(ir::Type::i4(), c->constant(2, ir::Type::i4()));
      break;

    case iconst_3:
      frame->push(ir::Type::i4(), c->constant(3, ir::Type::i4()));
      break;

    case iconst_4:
      frame->push(ir::Type::i4(), c->constant(4, ir::Type::i4()));
      break;

    case iconst_5:
      frame->push(ir::Type::i4(), c->constant(5, ir::Type::i4()));
      break;

    case idiv: {
      ir::Value* a = frame->pop(ir::Type::i4());
      ir::Value* b = frame->pop(ir::Type::i4());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      frame->push(ir::Type::i4(),
                  c->binaryOp(lir::Divide, ir::Type::i4(), a, b));
    } break;

    case if_acmpeq:
    case if_acmpne: {
      uint32_t offset = codeReadInt16(t, code, ip);
      newIp = (ip - 3) + offset;
      assertT(t, newIp < code->length());

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      ir::Value* a = frame->pop(ir::Type::object());
      ir::Value* b = frame->pop(ir::Type::object());
      ir::Value* target = frame->machineIpValue(newIp);

      c->condJump(toCompilerJumpOp(t, instruction), a, b, target);
    }
      goto branch;

    case if_icmpeq:
    case if_icmpne:
    case if_icmpgt:
    case if_icmpge:
    case if_icmplt:
    case if_icmple: {
      uint32_t offset = codeReadInt16(t, code, ip);
      newIp = (ip - 3) + offset;
      assertT(t, newIp < code->length());

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      ir::Value* a = frame->pop(ir::Type::i4());
      ir::Value* b = frame->pop(ir::Type::i4());
      ir::Value* target = frame->machineIpValue(newIp);

      c->condJump(toCompilerJumpOp(t, instruction), a, b, target);
    }
      goto branch;

    case ifeq:
    case ifne:
    case ifgt:
    case ifge:
    case iflt:
    case ifle: {
      uint32_t offset = codeReadInt16(t, code, ip);
      newIp = (ip - 3) + offset;
      assertT(t, newIp < code->length());

      ir::Value* target = frame->machineIpValue(newIp);

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      ir::Value* a = c->constant(0, ir::Type::i4());
      ir::Value* b = frame->pop(ir::Type::i4());

      c->condJump(toCompilerJumpOp(t, instruction), a, b, target);
    }
      goto branch;

    case ifnull:
    case ifnonnull: {
      uint32_t offset = codeReadInt16(t, code, ip);
      newIp = (ip - 3) + offset;
      assertT(t, newIp < code->length());

      if (newIp <= ip) {
        compileSafePoint(t, c, frame);
      }

      ir::Value* a = c->constant(0, ir::Type::object());
      ir::Value* b = frame->pop(ir::Type::object());
      ir::Value* target = frame->machineIpValue(newIp);

      c->condJump(toCompilerJumpOp(t, instruction), a, b, target);
    }
      goto branch;

    case iinc: {
      uint8_t index = code->body()[ip++];
      int8_t count = code->body()[ip++];

      storeLocal(context,
                 1,
                 ir::Type::i4(),
                 c->binaryOp(lir::Add,
                             ir::Type::i4(),
                             c->constant(count, ir::Type::i4()),
                             loadLocal(context, 1, ir::Type::i4(), index)),
                 index);
    } break;

    case iload:
      frame->load(ir::Type::i4(), code->body()[ip++]);
      break;
    case fload:
      frame->load(ir::Type::f4(), code->body()[ip++]);
      break;

    case iload_0:
      frame->load(ir::Type::i4(), 0);
      break;
    case fload_0:
      frame->load(ir::Type::f4(), 0);
      break;

    case iload_1:
      frame->load(ir::Type::i4(), 1);
      break;
    case fload_1:
      frame->load(ir::Type::f4(), 1);
      break;

    case iload_2:
      frame->load(ir::Type::i4(), 2);
      break;
    case fload_2:
      frame->load(ir::Type::f4(), 2);
      break;

    case iload_3:
      frame->load(ir::Type::i4(), 3);
      break;
    case fload_3:
      frame->load(ir::Type::f4(), 3);
      break;

    case ineg: {
      frame->push(ir::Type::i4(),
                  c->unaryOp(lir::Negate, frame->pop(ir::Type::i4())));
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcClass* class_
          = resolveClassInPool(t, context->method, index - 1, false);

      ir::Value* instance = frame->pop(ir::Type::object());

      object argument;
      Thunk thunk;
      if (LIKELY(class_)) {
        argument = class_;
        thunk = instanceOf64Thunk;
      } else {
        argument = makePair(t, context->method, reference);
        thunk = instanceOfFromReferenceThunk;
      }

      frame->push(
          ir::Type::i4(),
          c->nativeCall(
              c->constant(getThunk(t, thunk), ir::Type::iptr()),
              0,
              frame->trace(0, 0),
              ir::Type::i4(),
              args(c->threadRegister(), frame->append(argument), instance)));
    } break;

    case invokedynamic: {
      context->leaf = false;

      uint16_t poolIndex = codeReadInt16(t, code, ip);
      ip += 2;

      GcInvocation* invocation = cast<GcInvocation>(
          t,
          singletonObject(t, context->method->code()->pool(), poolIndex - 1));

      PROTECT(t, invocation);

      invocation->setClass(t, context->method->class_());

      BootContext* bc = context->bootContext;
      if (bc) {
        // When we're AOT-compiling an application, we can't handle
        // invokedynamic in general, since it usually implies runtime
        // code generation.  However, Java 8 lambda expressions are a
        // special case for which we can generate code ahead of time.
        //
        // The only tricky part about it is that the class synthesis
        // code resides in LambdaMetaFactory, which means we need to
        // call out to a separate Java VM to execute it (the VM we're
        // currently executing in won't work because it only knows how
        // to compile code for the target machine, which might not be
        // the same as the host; plus we don't want to pollute the
        // runtime heap image with stuff that's only needed at compile
        // time).

        GcClass* c = context->method->class_();
        PROTECT(t, c);

        GcMethod* target
            = c->addendum()->bootstrapLambdaTable()
                  ? cast<GcMethod>(
                        t,
                        cast<GcArray>(t, c->addendum()->bootstrapLambdaTable())
                            ->body()[invocation->bootstrap()])
                  : nullptr;
        PROTECT(t, target);

        if (target == nullptr) {
          GcCharArray* bootstrapArray = cast<GcCharArray>(
              t,
              cast<GcArray>(t, c->addendum()->bootstrapMethodTable())
                  ->body()[invocation->bootstrap()]);
          PROTECT(t, bootstrapArray);

          if (isLambda(t, c->loader(), bootstrapArray, invocation)) {
            if (bc->hostVM == 0) {
              throwNew(
                  t,
                  GcVirtualMachineError::Type,
                  "lambda expression encountered, but host VM is not "
                  "available; use -hostvm option to bootimage-generator to "
                  "fix this");
            }

            JNIEnv* e;
            if (bc->hostVM->vtable->AttachCurrentThread(bc->hostVM, &e, 0)
                == 0) {
              e->vtable->PushLocalFrame(e, 256);

              jclass lmfClass = e->vtable->FindClass(
                  e, "java/lang/invoke/LambdaMetafactory");
              jmethodID makeLambda
                  = e->vtable->GetStaticMethodID(e,
                                                 lmfClass,
                                                 "makeLambda",
                                                 "(Ljava/lang/String;"
                                                 "Ljava/lang/String;"
                                                 "Ljava/lang/String;"
                                                 "Ljava/lang/String;"
                                                 "Ljava/lang/String;"
                                                 "Ljava/lang/String;"
                                                 "I"
                                                 ")[B");

              GcMethodHandle* handle
                  = cast<GcMethodHandle>(t,
                                   resolve(t,
                                           c->loader(),
                                           invocation->pool(),
                                           bootstrapArray->body()[2],
                                           findMethodInClass,
                                           GcNoSuchMethodError::Type));

              int kind = handle->kind();

              GcMethod* method = handle->method();

              jarray lambda = e->vtable->CallStaticObjectMethod(
                  e,
                  lmfClass,
                  makeLambda,
                  e->vtable->NewStringUTF(
                      e,
                      reinterpret_cast<const char*>(
                          invocation->template_()->name()->body().begin())),
                  e->vtable->NewStringUTF(
                      e,
                      reinterpret_cast<const char*>(
                          invocation->template_()->spec()->body().begin())),
                  e->vtable->NewStringUTF(
                      e,
                      reinterpret_cast<const char*>(
                          cast<GcByteArray>(
                              t,
                              singletonObject(t,
                                              invocation->pool(),
                                              bootstrapArray->body()[1]))
                              ->body()
                              .begin())),
                  e->vtable->NewStringUTF(
                      e,
                      reinterpret_cast<const char*>(
                          method->class_()->name()->body().begin())),
                  e->vtable->NewStringUTF(e,
                                          reinterpret_cast<const char*>(
                                              method->name()->body().begin())),
                  e->vtable->NewStringUTF(e,
                                          reinterpret_cast<const char*>(
                                              method->spec()->body().begin())),
                  kind);

              uint8_t* bytes = reinterpret_cast<uint8_t*>(
                  e->vtable->GetPrimitiveArrayCritical(e, lambda, 0));

              GcClass* lambdaClass
                  = defineClass(t,
                                roots(t)->appLoader(),
                                bytes,
                                e->vtable->GetArrayLength(e, lambda));

              bc->resolver->addClass(
                  t, lambdaClass, bytes, e->vtable->GetArrayLength(e, lambda));

              e->vtable->ReleasePrimitiveArrayCritical(e, lambda, bytes, 0);

              e->vtable->PopLocalFrame(e, 0);

              THREAD_RUNTIME_ARRAY(
                  t, char, spec, invocation->template_()->spec()->length());
              memcpy(RUNTIME_ARRAY_BODY(spec),
                     invocation->template_()->spec()->body().begin(),
                     invocation->template_()->spec()->length());

              target = resolveMethod(
                  t, lambdaClass, "make", RUNTIME_ARRAY_BODY(spec));

              GcArray* table
                  = cast<GcArray>(t, c->addendum()->bootstrapLambdaTable());
              if (table == nullptr) {
                table = makeArray(
                    t,
                    cast<GcArray>(t, c->addendum()->bootstrapMethodTable())
                        ->length());
                c->addendum()->setBootstrapLambdaTable(t, table);
              }

              table->setBodyElement(t, invocation->bootstrap(), target);
            } else {
              throwNew(t,
                       GcVirtualMachineError::Type,
                       "unable to attach to host VM");
            }
          } else {
            throwNew(t,
                     GcVirtualMachineError::Type,
                     "invokedynamic not supported for AOT-compiled code except "
                     "in the case of lambda expressions");
          }
        }

        bool tailCall = isTailCall(t, code, ip, context->method, target);
        compileDirectInvoke(t, frame, target, tailCall);
      } else {
        unsigned index = addDynamic(t, invocation);

        GcMethod* template_ = invocation->template_();
        unsigned returnCode = template_->returnCode();
        unsigned rSize = resultSize(t, returnCode);
        unsigned parameterFootprint = template_->parameterFootprint();

        // TODO: can we allow tailCalls in general?
        // e.g. what happens if the call site is later bound to a method that
        // can't be tail called?
        // NOTE: calling isTailCall right now would cause an segfault, since
        // invocation->template_()->class_() will be null.
        // bool tailCall
        //     = isTailCall(t, code, ip, context->method,
        //     invocation->template_());
        bool tailCall = false;

        // todo: do we need to tell the compiler to add a load barrier
        // here for VolatileCallSite instances?

        ir::Value* result
            = c->stackCall(c->memory(c->memory(c->threadRegister(),
                                               ir::Type::object(),
                                               TARGET_THREAD_DYNAMICTABLE),
                                     ir::Type::object(),
                                     index * TargetBytesPerWord),
                           tailCall ? Compiler::TailJump : 0,
                           frame->trace(0, 0),
                           operandTypeForFieldCode(t, returnCode),
                           frame->peekMethodArguments(parameterFootprint));

        frame->popFootprint(parameterFootprint);

        if (rSize) {
          frame->pushReturnValue(returnCode, result);
        }
      }
    } break;

    case invokeinterface: {
      context->leaf = false;

      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcMethod* target = resolveMethod(t, context->method, index - 1, false);

      object argument;
      Thunk thunk;
      unsigned parameterFootprint;
      int returnCode;
      bool tailCall;
      if (LIKELY(target)) {
        checkMethod(t, target, false);

        argument = target;
        thunk = findInterfaceMethodFromInstanceThunk;
        parameterFootprint = target->parameterFootprint();
        returnCode = target->returnCode();
        tailCall = isTailCall(t, code, ip, context->method, target);
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, ref);
        argument = makePair(t, context->method, reference);
        thunk = findInterfaceMethodFromInstanceAndReferenceThunk;
        parameterFootprint = methodReferenceParameterFootprint(t, ref, false);
        returnCode = methodReferenceReturnCode(t, ref);
        tailCall = isReferenceTailCall(t, code, ip, context->method, ref);
      }

      unsigned rSize = resultSize(t, returnCode);

      ir::Value* result = c->stackCall(
          c->nativeCall(c->constant(getThunk(t, thunk), ir::Type::iptr()),
                        0,
                        frame->trace(0, 0),
                        ir::Type::iptr(),
                        args(c->threadRegister(),
                             frame->append(argument),
                             c->peek(1, parameterFootprint - 1))),
          tailCall ? Compiler::TailJump : 0,
          frame->trace(0, 0),
          operandTypeForFieldCode(t, returnCode),
          frame->peekMethodArguments(parameterFootprint));

      frame->popFootprint(parameterFootprint);

      if (rSize) {
        frame->pushReturnValue(returnCode, result);
      }
    } break;

    case invokespecial: {
      context->leaf = false;

      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcMethod* target = resolveMethod(t, context->method, index - 1, false);

      if (LIKELY(target)) {
        GcClass* class_ = context->method->class_();
        if (isSpecialMethod(t, target, class_)) {
          target = findVirtualMethod(t, target, class_->super());
        }

        checkMethod(t, target, false);

        bool tailCall = isTailCall(t, code, ip, context->method, target);

        if (UNLIKELY(methodAbstract(t, target))) {
          compileDirectAbstractInvoke(
              t, frame, getMethodAddressThunk, target, tailCall);
        } else {
          compileDirectInvoke(t, frame, target, tailCall);
        }
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, ref);
        compileDirectReferenceInvoke(
            t,
            frame,
            findSpecialMethodFromReferenceThunk,
            ref,
            false,
            isReferenceTailCall(t, code, ip, context->method, ref));
      }
    } break;

    case invokestatic: {
      context->leaf = false;

      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcMethod* target = resolveMethod(t, context->method, index - 1, false);

      if (LIKELY(target)) {
        checkMethod(t, target, true);

        if (not intrinsic(t, frame, target)) {
          bool tailCall = isTailCall(t, code, ip, context->method, target);
          compileDirectInvoke(t, frame, target, tailCall);
        }
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, ref);
        compileDirectReferenceInvoke(
            t,
            frame,
            findStaticMethodFromReferenceThunk,
            ref,
            true,
            isReferenceTailCall(t, code, ip, context->method, ref));
      }
    } break;

    case invokevirtual: {
      context->leaf = false;

      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcMethod* target = resolveMethod(t, context->method, index - 1, false);

      if (LIKELY(target)) {
        checkMethod(t, target, false);

        if (not intrinsic(t, frame, target)) {
          bool tailCall = isTailCall(t, code, ip, context->method, target);

          if (LIKELY(methodVirtual(t, target))) {
            unsigned parameterFootprint = target->parameterFootprint();

            unsigned offset = TargetClassVtable
                              + (target->offset() * TargetBytesPerWord);

            ir::Value* instance = c->peek(1, parameterFootprint - 1);

            frame->stackCall(
                c->memory(c->binaryOp(
                              lir::And,
                              ir::Type::iptr(),
                              c->constant(TargetPointerMask, ir::Type::iptr()),
                              c->memory(instance, ir::Type::object())),
                          ir::Type::object(),
                          offset),
                target,
                tailCall ? Compiler::TailJump : 0,
                frame->trace(0, 0));
          } else {
            // OpenJDK generates invokevirtual calls to private methods
            // (e.g. readObject and writeObject for serialization), so
            // we must handle such cases here.

            compileDirectInvoke(t, frame, target, tailCall);
          }
        }
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, reference);
        PROTECT(t, ref);

        GcPair* pair = makePair(t, context->method, reference);

        compileReferenceInvoke(
            frame,
            c->nativeCall(
                c->constant(getThunk(t, findVirtualMethodFromReferenceThunk),
                            ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                ir::Type::iptr(),
                args(c->threadRegister(),
                     frame->append(pair),
                     c->peek(1,
                             methodReferenceParameterFootprint(t, ref, false)
                             - 1))),
            ref,
            false,
            isReferenceTailCall(t, code, ip, context->method, ref));
      }
    } break;

    case irem: {
      ir::Value* a = frame->pop(ir::Type::i4());
      ir::Value* b = frame->pop(ir::Type::i4());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      frame->push(ir::Type::i4(),
                  c->binaryOp(lir::Remainder, ir::Type::i4(), a, b));
    } break;

    case ireturn: {
      handleExit(t, frame);
      c->return_(frame->pop(ir::Type::i4()));
    }
      goto next;

    case freturn: {
      handleExit(t, frame);
      c->return_(frame->pop(ir::Type::f4()));
    }
      goto next;

    case istore:
      frame->store(ir::Type::i4(), code->body()[ip++]);
      break;
    case fstore:
      frame->store(ir::Type::f4(), code->body()[ip++]);
      break;

    case istore_0:
      frame->store(ir::Type::i4(), 0);
      break;
    case fstore_0:
      frame->store(ir::Type::f4(), 0);
      break;

    case istore_1:
      frame->store(ir::Type::i4(), 1);
      break;
    case fstore_1:
      frame->store(ir::Type::f4(), 1);
      break;

    case istore_2:
      frame->store(ir::Type::i4(), 2);
      break;
    case fstore_2:
      frame->store(ir::Type::f4(), 2);
      break;

    case istore_3:
      frame->store(ir::Type::i4(), 3);
      break;
    case fstore_3:
      frame->store(ir::Type::f4(), 3);
      break;

    case jsr:
    case jsr_w: {
      uint32_t thisIp;

      if (instruction == jsr) {
        uint32_t offset = codeReadInt16(t, code, ip);
        thisIp = ip - 3;
        newIp = thisIp + offset;
      } else {
        uint32_t offset = codeReadInt32(t, code, ip);
        thisIp = ip - 5;
        newIp = thisIp + offset;
      }

      assertT(t, newIp < code->length());

      frame->startSubroutine(newIp, ip);

      c->jmp(frame->machineIpValue(newIp));

      ip = newIp;
    } break;

    case l2d: {
      frame->pushLarge(ir::Type::f8(),
                       c->i2f(ir::Type::f8(), frame->popLarge(ir::Type::i8())));
    } break;

    case l2f: {
      frame->push(ir::Type::f4(),
                  c->i2f(ir::Type::f4(), frame->popLarge(ir::Type::i8())));
    } break;

    case l2i:
      frame->push(ir::Type::i4(),
                  c->truncate(ir::Type::i4(), frame->popLarge(ir::Type::i8())));
      break;

    case ladd:
    case land:
    case lor:
    case lsub:
    case lxor:
    case lmul: {
      ir::Value* a = frame->popLarge(ir::Type::i8());
      ir::Value* b = frame->popLarge(ir::Type::i8());
      frame->pushLarge(
          ir::Type::i8(),
          c->binaryOp(
              toCompilerBinaryOp(t, instruction), ir::Type::i8(), a, b));
    } break;

    case lcmp: {
      ir::Value* a = frame->popLarge(ir::Type::i8());
      ir::Value* b = frame->popLarge(ir::Type::i8());

      if (integerBranch(t, frame, code, ip, a, b, &newIp)) {
        goto branch;
      } else {
        frame->push(ir::Type::i4(),
                    c->nativeCall(c->constant(getThunk(t, compareLongsThunk),
                                              ir::Type::iptr()),
                                  0,
                                  0,
                                  ir::Type::i4(),
                                  args(nullptr, a, nullptr, b)));
      }
    } break;

    case lconst_0:
      frame->pushLarge(ir::Type::i8(), c->constant(0, ir::Type::i8()));
      break;

    case lconst_1:
      frame->pushLarge(ir::Type::i8(), c->constant(1, ir::Type::i8()));
      break;

    case ldc:
    case ldc_w: {
      uint16_t index;

      if (instruction == ldc) {
        index = code->body()[ip++];
      } else {
        index = codeReadInt16(t, code, ip);
      }

      GcSingleton* pool = code->pool();

      if (singletonIsObject(t, pool, index - 1)) {
        object v = singletonObject(t, pool, index - 1);

        loadMemoryBarrier();

        if (objectClass(t, v) == type(t, GcReference::Type)) {
          GcReference* reference = cast<GcReference>(t, v);
          PROTECT(t, reference);

          v = resolveClassInPool(t, context->method, index - 1, false);

          if (UNLIKELY(v == 0)) {
            frame->push(
                ir::Type::object(),
                c->nativeCall(
                    c->constant(getThunk(t, getJClassFromReferenceThunk),
                                ir::Type::iptr()),
                    0,
                    frame->trace(0, 0),
                    ir::Type::object(),
                    args(c->threadRegister(),
                         frame->append(
                             makePair(t, context->method, reference)))));
          }
        }

        if (v) {
          if (objectClass(t, v) == type(t, GcClass::Type)) {
            frame->push(
                ir::Type::object(),
                c->nativeCall(c->constant(getThunk(t, getJClass64Thunk),
                                          ir::Type::iptr()),
                              0,
                              frame->trace(0, 0),
                              ir::Type::object(),
                              args(c->threadRegister(), frame->append(v))));
          } else {
            frame->push(ir::Type::object(), frame->append(v));
          }
        }
      } else {
        ir::Type type = singletonBit(t, pool, poolSize(t, pool), index - 1)
                            ? ir::Type::f4()
                            : ir::Type::i4();
        frame->push(type,
                    c->constant(singletonValue(t, pool, index - 1), type));
      }
    } break;

    case ldc2_w: {
      uint16_t index = codeReadInt16(t, code, ip);

      GcSingleton* pool = code->pool();

      uint64_t v;
      memcpy(&v, &singletonValue(t, pool, index - 1), 8);
      ir::Type type = singletonBit(t, pool, poolSize(t, pool), index - 1)
                          ? ir::Type::f8()
                          : ir::Type::i8();
      frame->pushLarge(type, c->constant(v, type));
    } break;

    case ldiv_: {
      ir::Value* a = frame->popLarge(ir::Type::i8());
      ir::Value* b = frame->popLarge(ir::Type::i8());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      frame->pushLarge(ir::Type::i8(),
                       c->binaryOp(lir::Divide, ir::Type::i8(), a, b));
    } break;

    case lload:
      frame->loadLarge(ir::Type::i8(), code->body()[ip++]);
      break;
    case dload:
      frame->loadLarge(ir::Type::f8(), code->body()[ip++]);
      break;

    case lload_0:
      frame->loadLarge(ir::Type::i8(), 0);
      break;
    case dload_0:
      frame->loadLarge(ir::Type::f8(), 0);
      break;

    case lload_1:
      frame->loadLarge(ir::Type::i8(), 1);
      break;
    case dload_1:
      frame->loadLarge(ir::Type::f8(), 1);
      break;

    case lload_2:
      frame->loadLarge(ir::Type::i8(), 2);
      break;
    case dload_2:
      frame->loadLarge(ir::Type::f8(), 2);
      break;

    case lload_3:
      frame->loadLarge(ir::Type::i8(), 3);
      break;
    case dload_3:
      frame->loadLarge(ir::Type::f8(), 3);
      break;

    case lneg:
      frame->pushLarge(
          ir::Type::i8(),
          c->unaryOp(lir::Negate, frame->popLarge(ir::Type::i8())));
      break;

    case lookupswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3;  // pad to four byte boundary

      ir::Value* key = frame->pop(ir::Type::i4());

      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assertT(t, defaultIp < code->length());

      int32_t pairCount = codeReadInt32(t, code, ip);

      if (pairCount) {
        ir::Value* default_ = frame->addressOperand(
            frame->addressPromise(frame->machineIp(defaultIp)));

        avian::codegen::Promise* start = 0;
        uint32_t* ipTable
            = static_cast<uint32_t*>(stack.push(sizeof(uint32_t) * pairCount));
        for (int32_t i = 0; i < pairCount; ++i) {
          unsigned index = ip + (i * 8);
          int32_t key = codeReadInt32(t, code, index);
          uint32_t newIp = base + codeReadInt32(t, code, index);
          assertT(t, newIp < code->length());

          ipTable[i] = newIp;

          avian::codegen::Promise* p = c->poolAppend(key);
          if (i == 0) {
            start = p;
          }
          c->poolAppendPromise(frame->addressPromise(frame->machineIp(newIp)));
        }
        assertT(t, start);

        ir::Value* address = c->nativeCall(
            c->constant(getThunk(t, lookUpAddressThunk), ir::Type::iptr()),
            0,
            0,
            ir::Type::iptr(),
            args(key,
                 frame->absoluteAddressOperand(start),
                 c->constant(pairCount, ir::Type::i4()),
                 default_));

        c->jmp(context->bootContext
                   ? c->binaryOp(lir::Add,
                                 ir::Type::iptr(),
                                 c->memory(c->threadRegister(),
                                           ir::Type::iptr(),
                                           TARGET_THREAD_CODEIMAGE),
                                 address)
                   : address);

        new (stack.push(sizeof(SwitchState)))
            SwitchState(c->saveState(), pairCount, defaultIp, 0, 0, 0, 0);

        goto switchloop;
      } else {
        // a switch statement with no cases, apparently
        c->jmp(frame->machineIpValue(defaultIp));
        ip = defaultIp;
      }
    } break;

    case lrem: {
      ir::Value* a = frame->popLarge(ir::Type::i8());
      ir::Value* b = frame->popLarge(ir::Type::i8());

      if (inTryBlock(t, code, ip - 1)) {
        c->saveLocals();
        frame->trace(0, 0);
      }

      frame->pushLarge(ir::Type::i8(),
                       c->binaryOp(lir::Remainder, ir::Type::i8(), a, b));
    } break;

    case lreturn: {
      handleExit(t, frame);
      c->return_(frame->popLarge(ir::Type::i8()));
    }
      goto next;

    case dreturn: {
      handleExit(t, frame);
      c->return_(frame->popLarge(ir::Type::f8()));
    }
      goto next;

    case lshl:
    case lshr:
    case lushr: {
      ir::Value* a = frame->pop(ir::Type::i4());
      ir::Value* b = frame->popLarge(ir::Type::i8());
      frame->pushLarge(
          ir::Type::i8(),
          c->binaryOp(
              toCompilerBinaryOp(t, instruction), ir::Type::i8(), a, b));
    } break;

    case lstore:
      frame->storeLarge(ir::Type::i8(), code->body()[ip++]);
      break;
    case dstore:
      frame->storeLarge(ir::Type::f8(), code->body()[ip++]);
      break;

    case lstore_0:
      frame->storeLarge(ir::Type::i8(), 0);
      break;
    case dstore_0:
      frame->storeLarge(ir::Type::f8(), 0);
      break;

    case lstore_1:
      frame->storeLarge(ir::Type::i8(), 1);
      break;
    case dstore_1:
      frame->storeLarge(ir::Type::f8(), 1);
      break;

    case lstore_2:
      frame->storeLarge(ir::Type::i8(), 2);
      break;
    case dstore_2:
      frame->storeLarge(ir::Type::f8(), 2);
      break;

    case lstore_3:
      frame->storeLarge(ir::Type::i8(), 3);
      break;
    case dstore_3:
      frame->storeLarge(ir::Type::f8(), 3);
      break;

    case monitorenter: {
      ir::Value* target = frame->pop(ir::Type::object());
      c->nativeCall(c->constant(getThunk(t, acquireMonitorForObjectThunk),
                                ir::Type::iptr()),
                    0,
                    frame->trace(0, 0),
                    ir::Type::void_(),
                    args(c->threadRegister(), target));
    } break;

    case monitorexit: {
      ir::Value* target = frame->pop(ir::Type::object());
      c->nativeCall(c->constant(getThunk(t, releaseMonitorForObjectThunk),
                                ir::Type::iptr()),
                    0,
                    frame->trace(0, 0),
                    ir::Type::void_(),
                    args(c->threadRegister(), target));
    } break;

    case multianewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      uint8_t dimensions = code->body()[ip++];

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcClass* class_
          = resolveClassInPool(t, context->method, index - 1, false);

      object argument;
      Thunk thunk;
      if (LIKELY(class_)) {
        argument = class_;
        thunk = makeMultidimensionalArrayThunk;
      } else {
        argument = makePair(t, context->method, reference);
        thunk = makeMultidimensionalArrayFromReferenceThunk;
      }

      unsigned offset
          = localOffset(t,
                        localSize(t, context->method) + c->topOfStack(),
                        context->method) + t->arch->frameReturnAddressSize();

      ir::Value* result
          = c->nativeCall(c->constant(getThunk(t, thunk), ir::Type::iptr()),
                          0,
                          frame->trace(0, 0),
                          ir::Type::object(),
                          args(c->threadRegister(),
                               frame->append(argument),
                               c->constant(dimensions, ir::Type::i4()),
                               c->constant(offset, ir::Type::i4())));

      frame->popFootprint(dimensions);
      frame->push(ir::Type::object(), result);
    } break;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcClass* class_
          = resolveClassInPool(t, context->method, index - 1, false);

      object argument;
      Thunk thunk;
      if (LIKELY(class_)) {
        argument = class_;
        if (class_->vmFlags() & (WeakReferenceFlag | HasFinalizerFlag)) {
          thunk = makeNewGeneral64Thunk;
        } else {
          thunk = makeNew64Thunk;
        }
      } else {
        argument = makePair(t, context->method, reference);
        thunk = makeNewFromReferenceThunk;
      }

      frame->push(
          ir::Type::object(),
          c->nativeCall(c->constant(getThunk(t, thunk), ir::Type::iptr()),
                        0,
                        frame->trace(0, 0),
                        ir::Type::object(),
                        args(c->threadRegister(), frame->append(argument))));
    } break;

    case newarray: {
      uint8_t type = code->body()[ip++];

      ir::Value* length = frame->pop(ir::Type::i4());

      frame->push(ir::Type::object(),
                  c->nativeCall(c->constant(getThunk(t, makeBlankArrayThunk),
                                            ir::Type::iptr()),
                                0,
                                frame->trace(0, 0),
                                ir::Type::object(),
                                args(c->threadRegister(),
                                     c->constant(type, ir::Type::i4()),
                                     length)));
    } break;

    case nop:
      break;

    case pop_:
      frame->popFootprint(1);
      break;

    case pop2:
      frame->popFootprint(2);
      break;

    case putfield:
    case putstatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object reference
          = singletonObject(t, context->method->code()->pool(), index - 1);

      PROTECT(t, reference);

      GcField* field = resolveField(t, context->method, index - 1, false);

      if (LIKELY(field)) {
        int fieldCode = field->code();

        object staticTable = 0;

        if (instruction == putstatic) {
          checkField(t, field, true);

          if (classNeedsInit(t, field->class_())) {
            PROTECT(t, field);

            c->nativeCall(
                c->constant(getThunk(t, tryInitClassThunk), ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                ir::Type::void_(),
                args(c->threadRegister(), frame->append(field->class_())));
          }

          staticTable = field->class_()->staticTable();
        } else {
          checkField(t, field, false);

          if (inTryBlock(t, code, ip - 3)) {
            c->saveLocals();
            frame->trace(0, 0);
          }
        }

        if (field->flags() & ACC_VOLATILE) {
          if (TargetBytesPerWord == 4
              and (fieldCode == DoubleField or fieldCode == LongField)) {
            PROTECT(t, field);

            c->nativeCall(c->constant(getThunk(t, acquireMonitorForObjectThunk),
                                      ir::Type::iptr()),
                          0,
                          frame->trace(0, 0),
                          ir::Type::void_(),
                          args(c->threadRegister(), frame->append(field)));
          } else {
            c->nullaryOp(lir::StoreStoreBarrier);
          }
        }

        ir::Value* value = popField(t, frame, fieldCode);

        ir::Value* table;

        if (instruction == putstatic) {
          PROTECT(t, field);

          table = frame->append(staticTable);
        } else {
          table = frame->pop(ir::Type::object());
        }

        switch (fieldCode) {
        case ByteField:
        case BooleanField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::i1(), targetFieldOffset(context, field)));
          break;

        case CharField:
        case ShortField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::i2(), targetFieldOffset(context, field)));
          break;

        case FloatField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::f4(), targetFieldOffset(context, field)));
          break;

        case IntField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::i4(), targetFieldOffset(context, field)));
          break;

        case DoubleField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::f8(), targetFieldOffset(context, field)));
          break;

        case LongField:
          c->store(
              value,
              c->memory(
                  table, ir::Type::i8(), targetFieldOffset(context, field)));
          break;

        case ObjectField:
          if (instruction == putfield) {
            c->nativeCall(
                c->constant(getThunk(t, setMaybeNullThunk), ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                ir::Type::void_(),
                args(c->threadRegister(),
                     table,
                     c->constant(targetFieldOffset(context, field),
                                 ir::Type::i4()),
                     value));
          } else {
            c->nativeCall(
                c->constant(getThunk(t, setObjectThunk), ir::Type::iptr()),
                0,
                0,
                ir::Type::void_(),
                args(c->threadRegister(),
                     table,
                     c->constant(targetFieldOffset(context, field),
                                 ir::Type::i4()),
                     value));
          }
          break;

        default:
          abort(t);
        }

        if (field->flags() & ACC_VOLATILE) {
          if (TargetBytesPerWord == 4
              and (fieldCode == DoubleField or fieldCode == LongField)) {
            c->nativeCall(c->constant(getThunk(t, releaseMonitorForObjectThunk),
                                      ir::Type::iptr()),
                          0,
                          frame->trace(0, 0),
                          ir::Type::void_(),
                          args(c->threadRegister(), frame->append(field)));
          } else {
            c->nullaryOp(lir::StoreLoadBarrier);
          }
        }
      } else {
        GcReference* ref = cast<GcReference>(t, reference);
        PROTECT(t, ref);
        int fieldCode = vm::fieldCode(t, ref->spec()->body()[0]);

        ir::Value* value = popField(t, frame, fieldCode);
        ir::Type rType = operandTypeForFieldCode(t, fieldCode);

        GcPair* pair = makePair(t, context->method, reference);

        switch (fieldCode) {
        case ByteField:
        case BooleanField:
        case CharField:
        case ShortField:
        case FloatField:
        case IntField: {
          if (instruction == putstatic) {
            c->nativeCall(
                c->constant(getThunk(t, setStaticFieldValueFromReferenceThunk),
                            ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(c->threadRegister(), frame->append(pair), value));
          } else {
            ir::Value* instance = frame->pop(ir::Type::object());

            c->nativeCall(
                c->constant(getThunk(t, setFieldValueFromReferenceThunk),
                            ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(
                    c->threadRegister(), frame->append(pair), instance, value));
          }
        } break;

        case DoubleField:
        case LongField: {
          if (instruction == putstatic) {
            c->nativeCall(
                c->constant(
                    getThunk(t, setStaticLongFieldValueFromReferenceThunk),
                    ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(c->threadRegister(), frame->append(pair), nullptr, value));
          } else {
            ir::Value* instance = frame->pop(ir::Type::object());

            c->nativeCall(
                c->constant(getThunk(t, setLongFieldValueFromReferenceThunk),
                            ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(c->threadRegister(),
                     frame->append(pair),
                     instance,
                     nullptr,
                     value));
          }
        } break;

        case ObjectField: {
          if (instruction == putstatic) {
            c->nativeCall(
                c->constant(
                    getThunk(t, setStaticObjectFieldValueFromReferenceThunk),
                    ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(c->threadRegister(), frame->append(pair), value));
          } else {
            ir::Value* instance = frame->pop(ir::Type::object());

            c->nativeCall(
                c->constant(getThunk(t, setObjectFieldValueFromReferenceThunk),
                            ir::Type::iptr()),
                0,
                frame->trace(0, 0),
                rType,
                args(
                    c->threadRegister(), frame->append(pair), instance, value));
          }
        } break;

        default:
          abort(t);
        }
      }
    } break;

    case ret: {
      unsigned index = code->body()[ip];

      unsigned returnAddress = frame->endSubroutine(index);
      c->jmp(frame->machineIpValue(returnAddress));
      ip = returnAddress;
    } break;

    case return_:
      if (needsReturnBarrier(t, context->method)) {
        c->nullaryOp(lir::StoreStoreBarrier);
      }

      handleExit(t, frame);
      c->return_();
      goto next;

    case sipush:
      frame->push(ir::Type::i4(),
                  c->constant(static_cast<int16_t>(codeReadInt16(t, code, ip)),
                              ir::Type::i4()));
      break;

    case swap:
      frame->swap();
      break;

    case tableswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3;  // pad to four byte boundary

      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assertT(t, defaultIp < code->length());

      int32_t bottom = codeReadInt32(t, code, ip);
      int32_t top = codeReadInt32(t, code, ip);

      avian::codegen::Promise* start = 0;
      unsigned count = top - bottom + 1;
      uint32_t* ipTable
          = static_cast<uint32_t*>(stack.push(sizeof(uint32_t) * count));
      for (int32_t i = 0; i < top - bottom + 1; ++i) {
        unsigned index = ip + (i * 4);
        uint32_t newIp = base + codeReadInt32(t, code, index);
        assertT(t, newIp < code->length());

        ipTable[i] = newIp;

        avian::codegen::Promise* p = c->poolAppendPromise(
            frame->addressPromise(frame->machineIp(newIp)));
        if (i == 0) {
          start = p;
        }
      }
      assertT(t, start);

      ir::Value* key = frame->pop(ir::Type::i4());

      c->condJump(lir::JumpIfLess,
                  c->constant(bottom, ir::Type::i4()),
                  key,
                  frame->machineIpValue(defaultIp));

      c->save(ir::Type::i4(), key);

      new (stack.push(sizeof(SwitchState))) SwitchState(
          c->saveState(), count, defaultIp, key, start, bottom, top);

      stack.pushValue(Untable0);
      ip = defaultIp;
    }
      goto start;

    case wide: {
      switch (code->body()[ip++]) {
      case aload: {
        frame->load(ir::Type::object(), codeReadInt16(t, code, ip));
      } break;

      case astore: {
        frame->store(ir::Type::object(), codeReadInt16(t, code, ip));
      } break;

      case iinc: {
        uint16_t index = codeReadInt16(t, code, ip);
        int16_t count = codeReadInt16(t, code, ip);

        storeLocal(context,
                   1,
                   ir::Type::i4(),
                   c->binaryOp(lir::Add,
                               ir::Type::i4(),
                               c->constant(count, ir::Type::i4()),
                               loadLocal(context, 1, ir::Type::i4(), index)),
                   index);
      } break;

      case iload: {
        frame->load(ir::Type::i4(), codeReadInt16(t, code, ip));
      } break;

      case istore: {
        frame->store(ir::Type::i4(), codeReadInt16(t, code, ip));
      } break;

      case lload: {
        frame->loadLarge(ir::Type::i8(), codeReadInt16(t, code, ip));
      } break;

      case lstore: {
        frame->storeLarge(ir::Type::i8(), codeReadInt16(t, code, ip));
      } break;

      case ret: {
        unsigned index = codeReadInt16(t, code, ip);

        unsigned returnAddress = frame->endSubroutine(index);
        c->jmp(frame->machineIpValue(returnAddress));
        ip = returnAddress;
      } break;

      default:
        abort(t);
      }
    } break;

    default:
      abort(t);
    }
  }

next:
  frame->dispose();
  frame = 0;
  stack.pop(sizeof(Frame));
  stack.pop(stackSize * sizeof(ir::Type));
  switch (stack.popValue()) {
  case Return:
    return;

  case Unbranch:
    if (DebugInstructions) {
      fprintf(stderr, "Unbranch\n");
    }
    ip = stack.popValue();
    c->restoreState(reinterpret_cast<Compiler::State*>(stack.popValue()));
    frame = static_cast<Frame*>(stack.peek(sizeof(Frame)));
    goto loop;

  case Untable0: {
    if (DebugInstructions) {
      fprintf(stderr, "Untable0\n");
    }
    SwitchState* s = static_cast<SwitchState*>(stack.peek(sizeof(SwitchState)));

    frame = s->frame();

    c->restoreState(s->state);

    c->condJump(lir::JumpIfGreater,
                c->constant(s->top, ir::Type::i4()),
                s->key,
                frame->machineIpValue(s->defaultIp));

    c->save(ir::Type::i4(), s->key);
    ip = s->defaultIp;
    stack.pushValue(Untable1);
  }
    goto start;

  case Untable1: {
    if (DebugInstructions) {
      fprintf(stderr, "Untable1\n");
    }
    SwitchState* s = static_cast<SwitchState*>(stack.peek(sizeof(SwitchState)));

    frame = s->frame();

    c->restoreState(s->state);

    ir::Value* normalizedKey
        = (s->bottom
               ? c->binaryOp(lir::Subtract,
                             ir::Type::i4(),
                             c->constant(s->bottom, ir::Type::i4()),
                             s->key)
               : s->key);

    ir::Value* entry = c->memory(frame->absoluteAddressOperand(s->start),
                                 ir::Type::iptr(),
                                 0,
                                 normalizedKey);

    c->jmp(c->load(ir::ExtendMode::Signed,
                   context->bootContext
                       ? c->binaryOp(lir::Add,
                                     ir::Type::iptr(),
                                     c->memory(c->threadRegister(),
                                               ir::Type::iptr(),
                                               TARGET_THREAD_CODEIMAGE),
                                     entry)
                       : entry,
                   ir::Type::iptr()));

    s->state = c->saveState();
  }
    goto switchloop;

  case Unswitch: {
    if (DebugInstructions) {
      fprintf(stderr, "Unswitch\n");
    }
    SwitchState* s = static_cast<SwitchState*>(stack.peek(sizeof(SwitchState)));

    frame = s->frame();

    c->restoreState(
        static_cast<SwitchState*>(stack.peek(sizeof(SwitchState)))->state);
  }
    goto switchloop;

  case Unsubroutine: {
    if (DebugInstructions) {
      fprintf(stderr, "Unsubroutine\n");
    }
    ip = stack.popValue();
    unsigned start = stack.popValue();
    frame = reinterpret_cast<Frame*>(stack.peek(sizeof(Frame)));
    frame->endSubroutine(start);
  }
    goto loop;

  default:
    abort(t);
  }

switchloop : {
  SwitchState* s = static_cast<SwitchState*>(stack.peek(sizeof(SwitchState)));

  if (s->index < s->count) {
    ip = s->ipTable()[s->index++];
    stack.pushValue(Unswitch);
    goto start;
  } else {
    ip = s->defaultIp;
    unsigned count = s->count * 4;
    stack.pop(sizeof(SwitchState));
    stack.pop(count);
    frame = reinterpret_cast<Frame*>(stack.peek(sizeof(Frame)));
    goto loop;
  }
}

branch:
  stack.pushValue(reinterpret_cast<uintptr_t>(c->saveState()));
  stack.pushValue(ip);
  stack.pushValue(Unbranch);
  ip = newIp;
  goto start;
}

int resolveIpForwards(Context* context, int start, int end)
{
  if (start < 0) {
    start = 0;
  }

  while (start < end and context->visitTable[start] == 0) {
    ++start;
  }

  if (start >= end) {
    return -1;
  } else {
    return start;
  }
}

int resolveIpBackwards(Context* context, int start, int end)
{
  if (start >= static_cast<int>(context->method->code()->length()
                                * (context->subroutineCount + 1))) {
    start = context->method->code()->length();
  } else {
    while (start >= end and context->visitTable[start] == 0) {
      --start;
    }
  }

  if (start < end) {
    return -1;
  } else {
    return start;
  }
}

GcIntArray* truncateIntArray(Thread* t, GcIntArray* array, unsigned length)
{
  expect(t, array->length() > length);

  PROTECT(t, array);

  GcIntArray* newArray = makeIntArray(t, length);
  if (length) {
    memcpy(newArray->body().begin(), array->body().begin(), length * 4);
  }

  return newArray;
}

GcArray* truncateArray(Thread* t, GcArray* array, unsigned length)
{
  expect(t, array->length() > length);

  PROTECT(t, array);

  GcArray* newArray = makeArray(t, length);
  if (length) {
    for (size_t i = 0; i < length; i++) {
      newArray->setBodyElement(t, i, array->body()[i]);
    }
  }

  return newArray;
}

GcLineNumberTable* truncateLineNumberTable(Thread* t,
                                           GcLineNumberTable* table,
                                           unsigned length)
{
  expect(t, table->length() > length);

  PROTECT(t, table);

  GcLineNumberTable* newTable = makeLineNumberTable(t, length);
  if (length) {
    memcpy(newTable->body().begin(),
           table->body().begin(),
           length * sizeof(uint64_t));
  }

  return newTable;
}

GcArray* translateExceptionHandlerTable(MyThread* t,
                                        Context* context,
                                        intptr_t start,
                                        intptr_t end)
{
  avian::codegen::Compiler* c = context->compiler;

  GcExceptionHandlerTable* oldTable = cast<GcExceptionHandlerTable>(
      t, context->method->code()->exceptionHandlerTable());

  if (oldTable) {
    PROTECT(t, oldTable);

    unsigned length = oldTable->length();

    GcIntArray* newIndex
        = makeIntArray(t, length * (context->subroutineCount + 1) * 3);
    PROTECT(t, newIndex);

    GcArray* newTable
        = makeArray(t, length * (context->subroutineCount + 1) + 1);
    PROTECT(t, newTable);

    unsigned ni = 0;
    for (unsigned subI = 0; subI <= context->subroutineCount; ++subI) {
      unsigned duplicatedBaseIp = subI * context->method->code()->length();

      for (unsigned oi = 0; oi < length; ++oi) {
        uint64_t oldHandler = oldTable->body()[oi];

        int handlerStart = resolveIpForwards(
            context,
            duplicatedBaseIp + exceptionHandlerStart(oldHandler),
            duplicatedBaseIp + exceptionHandlerEnd(oldHandler));

        if (LIKELY(handlerStart >= 0)) {
          assertT(t,
                  handlerStart
                  < static_cast<int>(context->method->code()->length()
                                     * (context->subroutineCount + 1)));

          int handlerEnd = resolveIpBackwards(
              context,
              duplicatedBaseIp + exceptionHandlerEnd(oldHandler),
              duplicatedBaseIp + exceptionHandlerStart(oldHandler));

          assertT(t, handlerEnd >= 0);
          assertT(
              t,
              handlerEnd <= static_cast<int>(context->method->code()->length()
                                             * (context->subroutineCount + 1)));

          newIndex->body()[ni * 3] = c->machineIp(handlerStart)->value()
                                     - start;

          newIndex->body()[(ni * 3) + 1]
              = (handlerEnd
                     == static_cast<int>(context->method->code()->length())
                     ? end
                     : c->machineIp(handlerEnd)->value()) - start;

          newIndex->body()[(ni * 3) + 2]
              = c->machineIp(exceptionHandlerIp(oldHandler))->value() - start;

          object type;
          if (exceptionHandlerCatchType(oldHandler)) {
            type = resolveClassInPool(
                t, context->method, exceptionHandlerCatchType(oldHandler) - 1);
          } else {
            type = 0;
          }

          newTable->setBodyElement(t, ni + 1, type);

          ++ni;
        }
      }
    }

    if (UNLIKELY(ni < length)) {
      newIndex = truncateIntArray(t, newIndex, ni * 3);
      newTable = truncateArray(t, newTable, ni + 1);
    }

    newTable->setBodyElement(t, 0, newIndex);

    return newTable;
  } else {
    return 0;
  }
}

GcLineNumberTable* translateLineNumberTable(MyThread* t,
                                            Context* context,
                                            intptr_t start)
{
  GcLineNumberTable* oldTable = context->method->code()->lineNumberTable();
  if (oldTable) {
    PROTECT(t, oldTable);

    unsigned length = oldTable->length();
    GcLineNumberTable* newTable = makeLineNumberTable(t, length);
    unsigned ni = 0;
    for (unsigned oi = 0; oi < length; ++oi) {
      uint64_t oldLine = oldTable->body()[oi];

      int ip = resolveIpForwards(
          context,
          lineNumberIp(oldLine),
          oi + 1 < length ? lineNumberIp(oldTable->body()[oi + 1]) - 1
                          : lineNumberIp(oldLine) + 1);

      if (LIKELY(ip >= 0)) {
        newTable->body()[ni++]
            = lineNumber(context->compiler->machineIp(ip)->value() - start,
                         lineNumberLine(oldLine));
      }
    }

    if (UNLIKELY(ni < length)) {
      newTable = truncateLineNumberTable(t, newTable, ni);
    }

    return newTable;
  } else {
    return 0;
  }
}

void printSet(uintptr_t* m, unsigned limit)
{
  if (limit) {
    for (unsigned i = 0; i < 32; ++i) {
      if ((*m >> i) & 1) {
        fprintf(stderr, "1");
      } else {
        fprintf(stderr, "_");
      }
    }
  }
}

void calculateTryCatchRoots(Context* context,
                            uintptr_t* roots,
                            unsigned mapSize,
                            unsigned start,
                            unsigned end)
{
  memset(roots, 0xFF, mapSize * BytesPerWord);

  if (DebugFrameMaps) {
    fprintf(stderr, "calculate try/catch roots from %d to %d\n", start, end);
  }

  for (TraceElement* te = context->traceLog; te; te = te->next) {
    if (te->ip >= start and te->ip < end) {
      uintptr_t* traceRoots = 0;
      traceRoots = te->map;
      te->watch = true;

      if (traceRoots) {
        if (DebugFrameMaps) {
          fprintf(stderr, "   use roots at ip %3d: ", te->ip);
          printSet(traceRoots, mapSize);
          fprintf(stderr, "\n");
        }

        for (unsigned wi = 0; wi < mapSize; ++wi) {
          roots[wi] &= traceRoots[wi];
        }
      } else {
        if (DebugFrameMaps) {
          fprintf(stderr, "  skip roots at ip %3d\n", te->ip);
        }
      }
    }
  }

  if (DebugFrameMaps) {
    fprintf(stderr, "result roots          : ");
    printSet(roots, mapSize);
    fprintf(stderr, "\n");
  }
}

unsigned calculateFrameMaps(MyThread* t,
                            Context* context,
                            uintptr_t* originalRoots,
                            unsigned eventIndex,
                            uintptr_t* resultRoots)
{
  // for each instruction with more than one predecessor, and for each
  // stack position, determine if there exists a path to that
  // instruction such that there is not an object pointer left at that
  // stack position (i.e. it is uninitialized or contains primitive
  // data).

  unsigned mapSize = frameMapSizeInWords(t, context->method);

  THREAD_RUNTIME_ARRAY(t, uintptr_t, roots, mapSize);
  if (originalRoots) {
    memcpy(RUNTIME_ARRAY_BODY(roots), originalRoots, mapSize * BytesPerWord);
  } else {
    memset(RUNTIME_ARRAY_BODY(roots), 0, mapSize * BytesPerWord);
  }

  int32_t ip = -1;

  // invariant: for each stack position, roots contains a zero at that
  // position if there exists some path to the current instruction
  // such that there is definitely not an object pointer at that
  // position.  Otherwise, roots contains a one at that position,
  // meaning either all known paths result in an object pointer at
  // that position, or the contents of that position are as yet
  // unknown.

  unsigned length = context->eventLog.length();
  while (eventIndex < length) {
    Event e = static_cast<Event>(context->eventLog.get(eventIndex++));
    switch (e) {
    case PushContextEvent: {
      eventIndex = calculateFrameMaps(t,
                                      context,
                                      RUNTIME_ARRAY_BODY(roots),
                                      eventIndex, /*subroutinePath,*/
                                      resultRoots);
    } break;

    case PopContextEvent:
      goto exit;

    case IpEvent: {
      ip = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      if (DebugFrameMaps) {
        fprintf(stderr, "       roots at ip %3d: ", ip);
        printSet(RUNTIME_ARRAY_BODY(roots), mapSize);
        fprintf(stderr, "\n");
      }

      assertT(context->thread, ip * mapSize <= context->rootTable.count);
      uintptr_t* tableRoots = context->rootTable.begin() + (ip * mapSize);

      if (context->visitTable[ip] > 1) {
        for (unsigned wi = 0; wi < mapSize; ++wi) {
          uintptr_t newRoots = tableRoots[wi] & RUNTIME_ARRAY_BODY(roots)[wi];

          if ((eventIndex == length
               or context->eventLog.get(eventIndex) == PopContextEvent)
              and newRoots != tableRoots[wi]) {
            if (DebugFrameMaps) {
              fprintf(stderr, "dirty roots!\n");
            }

            context->dirtyRoots = true;
          }

          tableRoots[wi] = newRoots;
          RUNTIME_ARRAY_BODY(roots)[wi] &= tableRoots[wi];
        }

        if (DebugFrameMaps) {
          fprintf(stderr, " table roots at ip %3d: ", ip);
          printSet(tableRoots, mapSize);
          fprintf(stderr, "\n");
        }
      } else {
        memcpy(tableRoots, RUNTIME_ARRAY_BODY(roots), mapSize * BytesPerWord);
      }
    } break;

    case MarkEvent: {
      unsigned i = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      markBit(RUNTIME_ARRAY_BODY(roots), i);
    } break;

    case ClearEvent: {
      unsigned i = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      clearBit(RUNTIME_ARRAY_BODY(roots), i);
    } break;

    case PushExceptionHandlerEvent: {
      unsigned start = context->eventLog.get2(eventIndex);
      eventIndex += 2;
      unsigned end = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      calculateTryCatchRoots(
          context, RUNTIME_ARRAY_BODY(roots), mapSize, start, end);

      eventIndex = calculateFrameMaps(
          t, context, RUNTIME_ARRAY_BODY(roots), eventIndex, 0);
    } break;

    case TraceEvent: {
      TraceElement* te;
      context->eventLog.get(eventIndex, &te, BytesPerWord);
      if (DebugFrameMaps) {
        fprintf(stderr, " trace roots at ip %3d: ", ip);
        printSet(RUNTIME_ARRAY_BODY(roots), mapSize);
        fprintf(stderr, "\n");
      }

      uintptr_t* map;
      bool watch;
      map = te->map;
      watch = te->watch;

      for (unsigned wi = 0; wi < mapSize; ++wi) {
        uintptr_t v = RUNTIME_ARRAY_BODY(roots)[wi];

        if (watch and map[wi] != v) {
          if (DebugFrameMaps) {
            fprintf(stderr, "dirty roots due to trace watch!\n");
          }

          context->dirtyRoots = true;
        }

        map[wi] = v;
      }

      eventIndex += BytesPerWord;
    } break;

    default:
      abort(t);
    }
  }

exit:
  if (resultRoots and ip != -1) {
    if (DebugFrameMaps) {
      fprintf(stderr, "result roots at ip %3d: ", ip);
      printSet(RUNTIME_ARRAY_BODY(roots), mapSize);
      fprintf(stderr, "\n");
    }

    memcpy(resultRoots, RUNTIME_ARRAY_BODY(roots), mapSize * BytesPerWord);
  }

  return eventIndex;
}

int compareTraceElementPointers(const void* va, const void* vb)
{
  TraceElement* a = *static_cast<TraceElement* const*>(va);
  TraceElement* b = *static_cast<TraceElement* const*>(vb);
  if (a->address->value() > b->address->value()) {
    return 1;
  } else if (a->address->value() < b->address->value()) {
    return -1;
  } else {
    return 0;
  }
}

uint8_t* finish(MyThread* t,
                FixedAllocator* allocator,
                avian::codegen::Assembler* a,
                const char* name,
                unsigned length)
{
  uint8_t* start
      = static_cast<uint8_t*>(allocator->allocate(length, TargetBytesPerWord));

  a->setDestination(start);
  a->write();

  logCompile(t, start, length, 0, name, 0);

  return start;
}

void setBit(int32_t* dst, unsigned index)
{
  dst[index / 32] |= static_cast<int32_t>(1) << (index % 32);
}

void clearBit(int32_t* dst, unsigned index)
{
  dst[index / 32] &= ~(static_cast<int32_t>(1) << (index % 32));
}

void copyFrameMap(int32_t* dst,
                  uintptr_t* src,
                  unsigned mapSizeInBits,
                  unsigned offset,
                  TraceElement* p)
{
  if (DebugFrameMaps) {
    fprintf(stderr, "  orig roots at ip %3d: ", p->ip);
    printSet(src, ceilingDivide(mapSizeInBits, BitsPerWord));
    fprintf(stderr, "\n");

    fprintf(stderr, " final roots at ip %3d: ", p->ip);
  }

  for (unsigned j = 0; j < p->argumentIndex; ++j) {
    if (getBit(src, j)) {
      if (DebugFrameMaps) {
        fprintf(stderr, "1");
      }
      setBit(dst, offset + j);
    } else {
      if (DebugFrameMaps) {
        fprintf(stderr, "_");
      }
      clearBit(dst, offset + j);
    }
  }

  if (DebugFrameMaps) {
    fprintf(stderr, "\n");
  }
}

class FrameMapTableHeader {
 public:
  FrameMapTableHeader(unsigned indexCount) : indexCount(indexCount)
  {
  }

  unsigned indexCount;
};

class FrameMapTableIndexElement {
 public:
  FrameMapTableIndexElement(int offset, unsigned base, unsigned path)
      : offset(offset), base(base), path(path)
  {
  }

  int offset;
  unsigned base;
  unsigned path;
};

class FrameMapTablePath {
 public:
  FrameMapTablePath(unsigned stackIndex, unsigned elementCount, unsigned next)
      : stackIndex(stackIndex), elementCount(elementCount), next(next)
  {
  }

  unsigned stackIndex;
  unsigned elementCount;
  unsigned next;
  int32_t elements[0];
};

GcIntArray* makeSimpleFrameMapTable(MyThread* t,
                                    Context* context,
                                    uint8_t* start,
                                    TraceElement** elements,
                                    unsigned elementCount)
{
  unsigned mapSize = frameMapSizeInBits(t, context->method);
  GcIntArray* table = makeIntArray(
      t, elementCount + ceilingDivide(elementCount * mapSize, 32));

  assertT(t,
          table->length()
          == elementCount + simpleFrameMapTableSize(t, context->method, table));

  for (unsigned i = 0; i < elementCount; ++i) {
    TraceElement* p = elements[i];

    table->body()[i] = static_cast<intptr_t>(p->address->value())
                       - reinterpret_cast<intptr_t>(start);

    assertT(
        t,
        elementCount + ceilingDivide((i + 1) * mapSize, 32) <= table->length());

    if (mapSize) {
      copyFrameMap(
          &table->body()[elementCount], p->map, mapSize, i * mapSize, p);
    }
  }

  return table;
}

void insertCallNode(MyThread* t, GcCallNode* node);

void finish(MyThread* t, FixedAllocator* allocator, Context* context)
{
  avian::codegen::Compiler* c = context->compiler;

  if (false) {
    logCompile(
        t,
        0,
        0,
        reinterpret_cast<const char*>(
            context->method->class_()->name()->body().begin()),
        reinterpret_cast<const char*>(context->method->name()->body().begin()),
        reinterpret_cast<const char*>(context->method->spec()->body().begin()));
  }

  // for debugging:
  if (false
      and ::strcmp(reinterpret_cast<const char*>(
                       context->method->class_()->name()->body().begin()),
                   "java/lang/System") == 0
      and ::strcmp(reinterpret_cast<const char*>(
                       context->method->name()->body().begin()),
                   "<clinit>") == 0) {
    trap();
  }

  // todo: this is a CPU-intensive operation, so consider doing it
  // earlier before we've acquired the global class lock to improve
  // parallelism (the downside being that it may end up being a waste
  // of cycles if another thread compiles the same method in parallel,
  // which might be mitigated by fine-grained, per-method locking):
  c->compile(context->leaf ? 0 : stackOverflowThunk(t),
             TARGET_THREAD_STACKLIMIT);

  // we must acquire the class lock here at the latest

  unsigned codeSize = c->resolve(allocator->memory.begin() + allocator->offset);

  unsigned total = pad(codeSize, TargetBytesPerWord)
                   + pad(c->poolSize(), TargetBytesPerWord);

  target_uintptr_t* code = static_cast<target_uintptr_t*>(
      allocator->allocate(total, TargetBytesPerWord));
  uint8_t* start = reinterpret_cast<uint8_t*>(code);

  context->executableAllocator = allocator;
  context->executableStart = code;
  context->executableSize = total;

  if (context->objectPool) {
    object pool = allocate3(
        t,
        allocator,
        Machine::ImmortalAllocation,
        GcArray::FixedSize + ((context->objectPoolCount + 1) * BytesPerWord),
        true);

    context->executableSize = (allocator->memory.begin() + allocator->offset)
                              - static_cast<uint8_t*>(context->executableStart);

    initArray(
        t, reinterpret_cast<GcArray*>(pool), context->objectPoolCount + 1);
    mark(t, pool, 0);

    setField(t, pool, ArrayBody, compileRoots(t)->objectPools());
    compileRoots(t)->setObjectPools(t, pool);

    unsigned i = 1;
    for (PoolElement* p = context->objectPool; p; p = p->next) {
      unsigned offset = ArrayBody + ((i++) * BytesPerWord);

      p->address = reinterpret_cast<uintptr_t>(pool) + offset;

      setField(t, pool, offset, p->target);
    }
  }

  c->write();

  BootContext* bc = context->bootContext;
  if (bc) {
    for (avian::codegen::DelayedPromise* p = bc->addresses;
         p != bc->addressSentinal;
         p = p->next) {
      p->basis = new (bc->zone)
          avian::codegen::ResolvedPromise(p->basis->value());
    }
  }

  {
    GcArray* newExceptionHandlerTable = translateExceptionHandlerTable(
        t,
        context,
        reinterpret_cast<intptr_t>(start),
        reinterpret_cast<intptr_t>(start) + codeSize);

    PROTECT(t, newExceptionHandlerTable);

    GcLineNumberTable* newLineNumberTable = translateLineNumberTable(
        t, context, reinterpret_cast<intptr_t>(start));

    GcCode* code = context->method->code();

    code = makeCode(t,
                    0,
                    0,
                    newExceptionHandlerTable,
                    newLineNumberTable,
                    reinterpret_cast<uintptr_t>(start),
                    codeSize,
                    code->maxStack(),
                    code->maxLocals(),
                    0);

    context->method->setCode(t, code);
  }

  if (context->traceLogCount) {
    THREAD_RUNTIME_ARRAY(t, TraceElement*, elements, context->traceLogCount);
    unsigned index = 0;
    // unsigned pathFootprint = 0;
    // unsigned mapCount = 0;
    for (TraceElement* p = context->traceLog; p; p = p->next) {
      assertT(t, index < context->traceLogCount);

      if (p->address) {
        RUNTIME_ARRAY_BODY(elements)[index++] = p;

        if (p->target) {
          insertCallNode(
              t, makeCallNode(t, p->address->value(), p->target, p->flags, 0));
        }
      }
    }

    qsort(RUNTIME_ARRAY_BODY(elements),
          index,
          sizeof(TraceElement*),
          compareTraceElementPointers);

    GcIntArray* map = makeSimpleFrameMapTable(
        t, context, start, RUNTIME_ARRAY_BODY(elements), index);

    context->method->code()->setStackMap(t, map);
  }

  logCompile(
      t,
      start,
      codeSize,
      reinterpret_cast<const char*>(
          context->method->class_()->name()->body().begin()),
      reinterpret_cast<const char*>(context->method->name()->body().begin()),
      reinterpret_cast<const char*>(context->method->spec()->body().begin()));

  // for debugging:
  if (false
      and ::strcmp(reinterpret_cast<const char*>(
                       context->method->class_()->name()->body().begin()),
                   "java/lang/System") == 0
      and ::strcmp(reinterpret_cast<const char*>(
                       context->method->name()->body().begin()),
                   "<clinit>") == 0) {
    trap();
  }
  syncInstructionCache(start, codeSize);
}

void compile(MyThread* t, Context* context)
{
  avian::codegen::Compiler* c = context->compiler;

  if (false) {
    fprintf(stderr,
            "compiling %s.%s%s\n",
            context->method->class_()->name()->body().begin(),
            context->method->name()->body().begin(),
            context->method->spec()->body().begin());
  }

  unsigned footprint = context->method->parameterFootprint();
  unsigned locals = localSize(t, context->method);
  c->init(context->method->code()->length(),
          footprint,
          locals,
          alignedFrameSize(t, context->method));

  ir::Type* stackMap = (ir::Type*)malloc(sizeof(ir::Type)
                                         * context->method->code()->maxStack());
  Frame frame(context, stackMap);

  unsigned index = context->method->parameterFootprint();
  if ((context->method->flags() & ACC_STATIC) == 0) {
    frame.set(--index, ir::Type::object());
    c->initLocal(index, ir::Type::object());
  }

  for (MethodSpecIterator it(t,
                             reinterpret_cast<const char*>(
                                 context->method->spec()->body().begin()));
       it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      frame.set(--index, ir::Type::object());
      c->initLocal(index, ir::Type::object());
      break;

    case 'J':
      frame.set(--index, ir::Type::i8());
      frame.set(--index, ir::Type::i8());
      c->initLocal(index, ir::Type::i8());
      break;

    case 'D':
      frame.set(--index, ir::Type::f8());
      frame.set(--index, ir::Type::f8());
      c->initLocal(index, ir::Type::f8());
      break;

    case 'F':
      frame.set(--index, ir::Type::i4());
      c->initLocal(index, ir::Type::f4());
      break;

    default:
      frame.set(--index, ir::Type::i4());
      c->initLocal(index, ir::Type::i4());
      break;
    }
  }

  handleEntrance(t, &frame);

  Compiler::State* state = c->saveState();

  compile(t, &frame, 0);

  context->dirtyRoots = false;
  unsigned eventIndex = calculateFrameMaps(t, context, 0, 0, 0);

  GcExceptionHandlerTable* eht = cast<GcExceptionHandlerTable>(
      t, context->method->code()->exceptionHandlerTable());
  if (eht) {
    PROTECT(t, eht);

    unsigned visitCount = eht->length();

    THREAD_RUNTIME_ARRAY(t, bool, visited, visitCount);
    memset(RUNTIME_ARRAY_BODY(visited), 0, visitCount * sizeof(bool));

    bool progress = true;
    while (progress) {
      progress = false;

      for (unsigned subI = 0; subI <= context->subroutineCount; ++subI) {
        unsigned duplicatedBaseIp = subI * context->method->code()->length();

        for (unsigned i = 0; i < eht->length(); ++i) {
          uint64_t eh = eht->body()[i];
          int start
              = resolveIpForwards(context,
                                  duplicatedBaseIp + exceptionHandlerStart(eh),
                                  duplicatedBaseIp + exceptionHandlerEnd(eh));

          if ((not RUNTIME_ARRAY_BODY(visited)[i]) and start >= 0
              and context->visitTable[start]) {
            RUNTIME_ARRAY_BODY(visited)[i] = true;
            progress = true;

            c->restoreState(state);

            ir::Type* stackMap2 = (ir::Type*)malloc(
                sizeof(ir::Type) * context->method->code()->maxStack());
            Frame frame2(&frame, stackMap2);

            unsigned end = duplicatedBaseIp + exceptionHandlerEnd(eh);
            if (exceptionHandlerIp(eh) >= static_cast<unsigned>(start)
                and exceptionHandlerIp(eh) < end) {
              end = duplicatedBaseIp + exceptionHandlerIp(eh);
            }

            context->eventLog.append(PushExceptionHandlerEvent);
            context->eventLog.append2(start);
            context->eventLog.append2(end);

            for (unsigned i = 1; i < context->method->code()->maxStack(); ++i) {
              frame2.set(localSize(t, context->method) + i, ir::Type::i4());
            }

            compile(t, &frame2, exceptionHandlerIp(eh), start);

            context->eventLog.append(PopContextEvent);

            eventIndex = calculateFrameMaps(t, context, 0, eventIndex, 0);
            free(stackMap2);
          }
        }
      }
    }
  }

  while (context->dirtyRoots) {
    context->dirtyRoots = false;
    calculateFrameMaps(t, context, 0, 0, 0);
  }
  free(stackMap);
}
#endif // not AVIAN_AOT_ONLY

void updateCall(MyThread* t,
                avian::codegen::lir::UnaryOperation op,
                void* returnAddress,
                void* target)
{
  t->arch->updateCall(op, returnAddress, target);
}

void* compileMethod2(MyThread* t, void* ip);

uint64_t compileMethod(MyThread* t)
{
  void* ip;
  if (t->tailAddress) {
    ip = t->tailAddress;
    t->tailAddress = 0;
  } else {
    ip = getIp(t);
  }

  return reinterpret_cast<uintptr_t>(compileMethod2(t, ip));
}

void* compileVirtualMethod2(MyThread* t, GcClass* class_, unsigned index)
{
  // If class_ has BootstrapFlag set, that means its vtable is not yet
  // available.  However, we must set t->trace->targetMethod to an
  // appropriate method to ensure we can accurately scan the stack for
  // GC roots.  We find such a method by looking for a superclass with
  // a vtable and using it instead:

  GcClass* c = class_;
  while (c->vmFlags() & BootstrapFlag) {
    c = c->super();
  }
  t->trace->targetMethod
      = cast<GcMethod>(t, cast<GcArray>(t, c->virtualTable())->body()[index]);

  THREAD_RESOURCE0(t, static_cast<MyThread*>(t)->trace->targetMethod = 0;);

  PROTECT(t, class_);

  GcMethod* target = resolveTarget(t, class_, index);
  PROTECT(t, target);

  compile(t, codeAllocator(t), 0, target);

  void* address = reinterpret_cast<void*>(methodAddress(t, target));
  if (target->flags() & ACC_NATIVE) {
    t->trace->nativeMethod = target;
  } else {
    class_->vtable()[target->offset()] = address;
  }
  return address;
}

uint64_t compileVirtualMethod(MyThread* t)
{
  GcClass* class_ = objectClass(t, static_cast<object>(t->virtualCallTarget));
  t->virtualCallTarget = 0;

  unsigned index = t->virtualCallIndex;
  t->virtualCallIndex = 0;

  return reinterpret_cast<uintptr_t>(compileVirtualMethod2(t, class_, index));
}

void* linkDynamicMethod2(MyThread* t, unsigned index)
{
  GcInvocation* invocation
      = cast<GcInvocation>(t, roots(t)->invocations()->body()[index]);

  GcCallSite* site = invocation->site();

  loadMemoryBarrier();

  if (site == 0) {
    t->trace->targetMethod = invocation->template_();

    THREAD_RESOURCE0(t, static_cast<MyThread*>(t)->trace->targetMethod = 0;);

    PROTECT(t, invocation);

    site = resolveDynamic(t, invocation);
    PROTECT(t, site);

    compile(t, codeAllocator(t), 0, site->target()->method());

    ACQUIRE(t, t->m->classLock);

    if (invocation->site() == 0) {
      void* address
          = reinterpret_cast<void*>(methodAddress(t, site->target()->method()));

      if ((site->target()->method()->flags() & ACC_NATIVE) == 0) {
        t->dynamicTable[index] = address;
      }
    }

    storeStoreMemoryBarrier();

    invocation->setSite(t, site);
    site->setInvocation(t, invocation);
  }

  GcMethod* target = invocation->site()->target()->method();

  if (target->flags() & ACC_NATIVE) {
    t->trace->nativeMethod = target;
  }

  return reinterpret_cast<void*>(methodAddress(t, target));
}

uint64_t linkDynamicMethod(MyThread* t)
{
  unsigned index = t->virtualCallIndex;
  t->virtualCallIndex = 0;

  return reinterpret_cast<uintptr_t>(linkDynamicMethod2(t, index));
}

uint64_t invokeNativeFast(MyThread* t, GcMethod* method, void* function)
{
  FastNativeFunction f;
  memcpy(&f, &function, sizeof(void*));
  return f(t,
           method,
           static_cast<uintptr_t*>(t->stack) + t->arch->frameFooterSize()
           + t->arch->frameReturnAddressSize());
}

uint64_t invokeNativeSlow(MyThread* t, GcMethod* method, void* function)
{
  PROTECT(t, method);

  unsigned footprint = method->parameterFootprint() + 1;
  if (method->flags() & ACC_STATIC) {
    ++footprint;
  }
  unsigned count = method->parameterCount() + 2;

  THREAD_RUNTIME_ARRAY(t, uintptr_t, args, footprint);
  unsigned argOffset = 0;
  THREAD_RUNTIME_ARRAY(t, uint8_t, types, count);
  unsigned typeOffset = 0;

  RUNTIME_ARRAY_BODY(args)[argOffset++] = reinterpret_cast<uintptr_t>(t);
  RUNTIME_ARRAY_BODY(types)[typeOffset++] = POINTER_TYPE;

  uintptr_t* sp = static_cast<uintptr_t*>(t->stack) + t->arch->frameFooterSize()
                  + t->arch->frameReturnAddressSize();

  GcJclass* jclass = 0;
  PROTECT(t, jclass);

  if (method->flags() & ACC_STATIC) {
    jclass = getJClass(t, method->class_());
    RUNTIME_ARRAY_BODY(args)[argOffset++]
        = reinterpret_cast<uintptr_t>(&jclass);
  } else {
    RUNTIME_ARRAY_BODY(args)[argOffset++] = reinterpret_cast<uintptr_t>(sp++);
  }
  RUNTIME_ARRAY_BODY(types)[typeOffset++] = POINTER_TYPE;

  MethodSpecIterator it(
      t, reinterpret_cast<const char*>(method->spec()->body().begin()));

  while (it.hasNext()) {
    unsigned type = RUNTIME_ARRAY_BODY(types)[typeOffset++]
        = fieldType(t, fieldCode(t, *it.next()));

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      RUNTIME_ARRAY_BODY(args)[argOffset++] = *(sp++);
      break;

    case INT64_TYPE:
    case DOUBLE_TYPE: {
      memcpy(RUNTIME_ARRAY_BODY(args) + argOffset, sp, 8);
      argOffset += (8 / BytesPerWord);
      sp += 2;
    } break;

    case POINTER_TYPE: {
      if (*sp) {
        RUNTIME_ARRAY_BODY(args)[argOffset++] = reinterpret_cast<uintptr_t>(sp);
      } else {
        RUNTIME_ARRAY_BODY(args)[argOffset++] = 0;
      }
      ++sp;
    } break;

    default:
      abort(t);
    }
  }

  unsigned returnCode = method->returnCode();
  unsigned returnType = fieldType(t, returnCode);
  uint64_t result;

  if (DebugNatives) {
    fprintf(stderr,
            "invoke native method %s.%s\n",
            method->class_()->name()->body().begin(),
            method->name()->body().begin());
  }

  if (method->flags() & ACC_SYNCHRONIZED) {
    if (method->flags() & ACC_STATIC) {
      acquire(t, getJClass(t, method->class_()));
    } else {
      acquire(t, *reinterpret_cast<object*>(RUNTIME_ARRAY_BODY(args)[1]));
    }
  }

  Reference* reference = t->reference;

  {
    ENTER(t, Thread::IdleState);

    bool noThrow = t->checkpoint->noThrow;
    t->checkpoint->noThrow = true;
    THREAD_RESOURCE(t, bool, noThrow, t->checkpoint->noThrow = noThrow);

    result = vm::dynamicCall(function,
                             RUNTIME_ARRAY_BODY(args),
                             RUNTIME_ARRAY_BODY(types),
                             count,
                             footprint * BytesPerWord,
                             returnType);
  }

  if (method->flags() & ACC_SYNCHRONIZED) {
    if (method->flags() & ACC_STATIC) {
      release(t, getJClass(t, method->class_()));
    } else {
      release(t, *reinterpret_cast<object*>(RUNTIME_ARRAY_BODY(args)[1]));
    }
  }

  if (DebugNatives) {
    fprintf(stderr,
            "return from native method %s.%s\n",
            method->class_()->name()->body().begin(),
            method->name()->body().begin());
  }

  if (UNLIKELY(t->exception)) {
    GcThrowable* exception = t->exception;
    t->exception = 0;
    vm::throw_(t, exception);
  }

  switch (returnCode) {
  case ByteField:
  case BooleanField:
    result = static_cast<int8_t>(result);
    break;

  case CharField:
    result = static_cast<uint16_t>(result);
    break;

  case ShortField:
    result = static_cast<int16_t>(result);
    break;

  case FloatField:
  case IntField:
    result = static_cast<int32_t>(result);
    break;

  case LongField:
  case DoubleField:
    break;

  case ObjectField:
    result = static_cast<uintptr_t>(result)
                 ? *reinterpret_cast<uintptr_t*>(static_cast<uintptr_t>(result))
                 : 0;
    break;

  case VoidField:
    result = 0;
    break;

  default:
    abort(t);
  }

  while (t->reference != reference) {
    dispose(t, t->reference);
  }

  return result;
}

uint64_t invokeNative2(MyThread* t, GcMethod* method)
{
  GcNative* native = getMethodRuntimeData(t, method)->native();
  if (native->fast()) {
    return invokeNativeFast(t, method, native->function());
  } else {
    return invokeNativeSlow(t, method, native->function());
  }
}

uint64_t invokeNative(MyThread* t)
{
  if (t->trace->nativeMethod == 0) {
    void* ip;
    if (t->tailAddress) {
      ip = t->tailAddress;
      t->tailAddress = 0;
    } else {
      ip = getIp(t);
    }

    GcCallNode* node = findCallNode(t, ip);
    GcMethod* target = node->target();
    if (node->flags() & TraceElement::VirtualCall) {
      target = resolveTarget(t, t->stack, target);
    }
    t->trace->nativeMethod = target;
  }

  assertT(t, t->tailAddress == 0);

  uint64_t result = 0;

  t->trace->targetMethod = t->trace->nativeMethod;

  t->m->classpath->resolveNative(t, t->trace->nativeMethod);

  result = invokeNative2(t, t->trace->nativeMethod);

  unsigned parameterFootprint = t->trace->targetMethod->parameterFootprint();

  uintptr_t* stack = static_cast<uintptr_t*>(t->stack);

  if (avian::codegen::TailCalls
      and t->arch->argumentFootprint(parameterFootprint)
          > t->arch->stackAlignmentInWords()) {
    stack += t->arch->argumentFootprint(parameterFootprint)
             - t->arch->stackAlignmentInWords();
  }

  stack += t->arch->frameReturnAddressSize();

  t->trace->targetMethod = 0;
  t->trace->nativeMethod = 0;

  t->newStack = stack;

  return result;
}

void findFrameMapInSimpleTable(MyThread* t,
                               GcMethod* method,
                               GcIntArray* table,
                               int32_t offset,
                               int32_t** map,
                               unsigned* start)
{
  unsigned tableSize = simpleFrameMapTableSize(t, method, table);
  unsigned indexSize = table->length() - tableSize;

  *map = &table->body()[indexSize];

  unsigned bottom = 0;
  unsigned top = indexSize;
  for (unsigned span = top - bottom; span; span = top - bottom) {
    unsigned middle = bottom + (span / 2);
    int32_t v = table->body()[middle];

    if (offset == v) {
      *start = frameMapSizeInBits(t, method) * middle;
      return;
    } else if (offset < v) {
      top = middle;
    } else {
      bottom = middle + 1;
    }
  }

  abort(t);
}

void findFrameMap(MyThread* t,
                  void* stack UNUSED,
                  GcMethod* method,
                  int32_t offset,
                  int32_t** map,
                  unsigned* start)
{
  findFrameMapInSimpleTable(
      t, method, method->code()->stackMap(), offset, map, start);
}

void visitStackAndLocals(MyThread* t,
                         Heap::Visitor* v,
                         void* frame,
                         GcMethod* method,
                         void* ip)
{
  unsigned count = frameMapSizeInBits(t, method);

  if (count) {
    void* stack = stackForFrame(t, frame, method);

    int32_t* map;
    unsigned offset;
    findFrameMap(
        t,
        stack,
        method,
        difference(ip, reinterpret_cast<void*>(methodAddress(t, method))),
        &map,
        &offset);

    for (unsigned i = 0; i < count; ++i) {
      int j = offset + i;
      if (map[j / 32] & (static_cast<int32_t>(1) << (j % 32))) {
        v->visit(localObject(t, stack, method, i));
      }
    }
  }
}

void visitArgument(MyThread* t, Heap::Visitor* v, void* stack, unsigned index)
{
  v->visit(static_cast<object*>(stack) + index
           + t->arch->frameReturnAddressSize() + t->arch->frameFooterSize());
}

void visitArguments(MyThread* t,
                    Heap::Visitor* v,
                    void* stack,
                    GcMethod* method)
{
  unsigned index = 0;

  if ((method->flags() & ACC_STATIC) == 0) {
    visitArgument(t, v, stack, index++);
  }

  for (MethodSpecIterator it(
           t, reinterpret_cast<const char*>(method->spec()->body().begin()));
       it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      visitArgument(t, v, stack, index++);
      break;

    case 'J':
    case 'D':
      index += 2;
      break;

    default:
      ++index;
      break;
    }
  }
}

void visitStack(MyThread* t, Heap::Visitor* v)
{
  void* ip = getIp(t);
  void* stack = t->stack;

  MyThread::CallTrace* trace = t->trace;
  GcMethod* targetMethod = (trace ? trace->targetMethod : 0);
  GcMethod* target = targetMethod;
  bool mostRecent = true;

  while (stack) {
    if (targetMethod) {
      visitArguments(t, v, stack, targetMethod);
      targetMethod = 0;
    }

    GcMethod* method = methodForIp(t, ip);
    if (method) {
      PROTECT(t, method);

      void* nextIp = ip;
      nextFrame(t, &nextIp, &stack, method, target, mostRecent);

      visitStackAndLocals(t, v, stack, method, ip);

      ip = nextIp;

      target = method;
    } else if (trace) {
      stack = trace->stack;
      ip = trace->ip;
      trace = trace->next;

      if (trace) {
        targetMethod = trace->targetMethod;
        target = targetMethod;
      } else {
        target = 0;
      }
    } else {
      break;
    }

    mostRecent = false;
  }
}

void walkContinuationBody(MyThread* t,
                          Heap::Walker* w,
                          GcContinuation* c,
                          int start)
{
  const int BodyOffset = ContinuationBody / BytesPerWord;

  GcMethod* method = t->m->heap->follow(c->method());
  int count = frameMapSizeInBits(t, method);

  if (count) {
    int stack = BodyOffset + (c->framePointerOffset() / BytesPerWord)
                - t->arch->framePointerOffset()
                - stackOffsetFromFrame(t, method);

    int first = stack + localOffsetFromStack(t, count - 1, method);
    if (start > first) {
      count -= start - first;
    }

    int32_t* map;
    unsigned offset;
    findFrameMap(t,
                 reinterpret_cast<uintptr_t*>(c) + stack,
                 method,
                 difference(c->address(),
                            reinterpret_cast<void*>(methodAddress(t, method))),
                 &map,
                 &offset);

    for (int i = count - 1; i >= 0; --i) {
      int j = offset + i;
      if (map[j / 32] & (static_cast<int32_t>(1) << (j % 32))) {
        if (not w->visit(stack + localOffsetFromStack(t, i, method))) {
          return;
        }
      }
    }
  }
}

void callContinuation(MyThread* t,
                      GcContinuation* continuation,
                      object result,
                      GcThrowable* exception,
                      void* ip,
                      void* stack)
{
  assertT(t, t->exception == 0);

  if (exception) {
    t->exception = exception;

    MyThread::TraceContext c(t, ip, stack, continuation, t->trace);

    void* frame;
    findUnwindTarget(t, &ip, &frame, &stack, &continuation);
  }

  t->trace->nativeMethod = 0;
  t->trace->targetMethod = 0;

  popResources(t);

  transition(t, ip, stack, continuation, t->trace);

  vmJump(ip, 0, stack, t, reinterpret_cast<uintptr_t>(result), 0);
}

int8_t* returnSpec(MyThread* t, GcMethod* method)
{
  int8_t* s = method->spec()->body().begin();
  while (*s and *s != ')')
    ++s;
  expect(t, *s == ')');
  return s + 1;
}

GcClass* returnClass(MyThread* t, GcMethod* method)
{
  PROTECT(t, method);

  int8_t* spec = returnSpec(t, method);
  unsigned length = strlen(reinterpret_cast<char*>(spec));
  GcByteArray* name;
  if (*spec == '[') {
    name = makeByteArray(t, length + 1);
    memcpy(name->body().begin(), spec, length);
  } else {
    assertT(t, *spec == 'L');
    assertT(t, spec[length - 1] == ';');
    name = makeByteArray(t, length - 1);
    memcpy(name->body().begin(), spec + 1, length - 2);
  }

  return resolveClass(t, method->class_()->loader(), name);
}

bool compatibleReturnType(MyThread* t, GcMethod* oldMethod, GcMethod* newMethod)
{
  if (oldMethod == newMethod) {
    return true;
  } else if (oldMethod->returnCode() == newMethod->returnCode()) {
    if (oldMethod->returnCode() == ObjectField) {
      PROTECT(t, newMethod);

      GcClass* oldClass = returnClass(t, oldMethod);
      PROTECT(t, oldClass);

      GcClass* newClass = returnClass(t, newMethod);

      return isAssignableFrom(t, oldClass, newClass);
    } else {
      return true;
    }
  } else {
    return oldMethod->returnCode() == VoidField;
  }
}

void jumpAndInvoke(MyThread* t, GcMethod* method, void* stack, ...)
{
  t->trace->targetMethod = 0;

  if (method->flags() & ACC_NATIVE) {
    t->trace->nativeMethod = method;
  } else {
    t->trace->nativeMethod = 0;
  }

  unsigned argumentCount = method->parameterFootprint();
  THREAD_RUNTIME_ARRAY(t, uintptr_t, arguments, argumentCount);
  va_list a;
  va_start(a, stack);
  for (unsigned i = 0; i < argumentCount; ++i) {
    RUNTIME_ARRAY_BODY(arguments)[i] = va_arg(a, uintptr_t);
  }
  va_end(a);

  assertT(t, t->exception == 0);

  popResources(t);

  vmJumpAndInvoke(
      t,
      reinterpret_cast<void*>(methodAddress(t, method)),
      stack,
      argumentCount * BytesPerWord,
      RUNTIME_ARRAY_BODY(arguments),
      (t->arch->alignFrameSize(t->arch->argumentFootprint(argumentCount))
       + t->arch->frameReturnAddressSize()) * BytesPerWord);
}

void callContinuation(MyThread* t,
                      GcContinuation* continuation,
                      object result,
                      GcThrowable* exception)
{
  enum { Call, Unwind, Rewind } action;

  GcContinuation* nextContinuation = 0;

  if (t->continuation == 0
      or t->continuation->context() != continuation->context()) {
    PROTECT(t, continuation);
    PROTECT(t, result);
    PROTECT(t, exception);

    if (compatibleReturnType(
            t, t->trace->originalMethod, continuation->context()->method())) {
      GcContinuationContext* oldContext;
      GcContinuationContext* unwindContext;

      if (t->continuation) {
        oldContext = t->continuation->context();
        unwindContext = oldContext;
      } else {
        oldContext = 0;
        unwindContext = 0;
      }

      GcContinuationContext* rewindContext = 0;

      for (GcContinuationContext* newContext = continuation->context();
           newContext;
           newContext = newContext->next()) {
        if (newContext == oldContext) {
          unwindContext = 0;
          break;
        } else {
          rewindContext = newContext;
        }
      }

      if (unwindContext and unwindContext->continuation()) {
        nextContinuation
            = cast<GcContinuation>(t, unwindContext->continuation());
        result = makeUnwindResult(t, continuation, result, exception);
        action = Unwind;
      } else if (rewindContext and rewindContext->continuation()) {
        nextContinuation
            = cast<GcContinuation>(t, rewindContext->continuation());
        action = Rewind;

        if (compileRoots(t)->rewindMethod() == 0) {
          PROTECT(t, nextContinuation);

          GcMethod* method = resolveMethod(
              t,
              roots(t)->bootLoader(),
              "avian/Continuations",
              "rewind",
              "(Ljava/lang/Runnable;Lavian/Callback;Ljava/lang/Object;"
              "Ljava/lang/Throwable;)V");

          PROTECT(t, method);

          compile(t, local::codeAllocator(t), 0, method);

          compileRoots(t)->setRewindMethod(t, method);
        }
      } else {
        action = Call;
      }
    } else {
      throwNew(t, GcIncompatibleContinuationException::Type);
    }
  } else {
    action = Call;
  }

  void* ip;
  void* frame;
  void* stack;
  GcContinuation* threadContinuation;
  findUnwindTarget(t, &ip, &frame, &stack, &threadContinuation);

  switch (action) {
  case Call: {
    callContinuation(t, continuation, result, exception, ip, stack);
  } break;

  case Unwind: {
    callContinuation(t, nextContinuation, result, 0, ip, stack);
  } break;

  case Rewind: {
    transition(t, 0, 0, nextContinuation, t->trace);

    jumpAndInvoke(t,
                  compileRoots(t)->rewindMethod(),
                  stack,
                  nextContinuation->context()->before(),
                  continuation,
                  result,
                  exception);
  } break;

  default:
    abort(t);
  }
}

void callWithCurrentContinuation(MyThread* t, object receiver)
{
  GcMethod* method = 0;
  void* ip = 0;
  void* stack = 0;

  {
    PROTECT(t, receiver);

    if (compileRoots(t)->receiveMethod() == 0) {
      GcMethod* m = resolveMethod(t,
                                  roots(t)->bootLoader(),
                                  "avian/Function",
                                  "call",
                                  "(Ljava/lang/Object;)Ljava/lang/Object;");

      if (m) {
        compileRoots(t)->setReceiveMethod(t, m);

        GcClass* continuationClass = type(t, GcContinuation::Type);

        if (continuationClass->vmFlags() & BootstrapFlag) {
          resolveSystemClass(
              t, roots(t)->bootLoader(), continuationClass->name());
        }
      }
    }

    method = findInterfaceMethod(
        t, compileRoots(t)->receiveMethod(), objectClass(t, receiver));
    PROTECT(t, method);

    compile(t, local::codeAllocator(t), 0, method);

    t->continuation = makeCurrentContinuation(t, &ip, &stack);
  }

  jumpAndInvoke(t, method, stack, receiver, t->continuation);
}

void dynamicWind(MyThread* t, object before, object thunk, object after)
{
  void* ip = 0;
  void* stack = 0;

  {
    PROTECT(t, before);
    PROTECT(t, thunk);
    PROTECT(t, after);

    if (compileRoots(t)->windMethod() == 0) {
      GcMethod* method = resolveMethod(
          t,
          roots(t)->bootLoader(),
          "avian/Continuations",
          "wind",
          "(Ljava/lang/Runnable;Ljava/util/concurrent/Callable;"
          "Ljava/lang/Runnable;)Lavian/Continuations$UnwindResult;");

      if (method) {
        compileRoots(t)->setWindMethod(t, method);
        compile(t, local::codeAllocator(t), 0, method);
      }
    }

    t->continuation = makeCurrentContinuation(t, &ip, &stack);

    GcContinuationContext* newContext
        = makeContinuationContext(t,
                                  t->continuation->context(),
                                  before,
                                  after,
                                  t->continuation,
                                  t->trace->originalMethod);

    t->continuation->setContext(t, newContext);
  }

  jumpAndInvoke(t, compileRoots(t)->windMethod(), stack, before, thunk, after);
}

class ArgumentList {
 public:
  ArgumentList(Thread* t,
               uintptr_t* array,
               unsigned size,
               bool* objectMask,
               object this_,
               const char* spec,
               bool indirectObjects,
               va_list arguments)
      : t(static_cast<MyThread*>(t)),
        array(array),
        objectMask(objectMask),
        size(size),
        position(0),
        protector(this)
  {
    if (this_) {
      addObject(this_);
    }

    for (MethodSpecIterator it(t, spec); it.hasNext();) {
      switch (*it.next()) {
      case 'L':
      case '[':
        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;

      case 'J':
        addLong(va_arg(arguments, uint64_t));
        break;

      case 'D':
        addLong(doubleToBits(va_arg(arguments, double)));
        break;

      case 'F':
        addInt(floatToBits(va_arg(arguments, double)));
        break;

      default:
        addInt(va_arg(arguments, uint32_t));
        break;
      }
    }
  }

  ArgumentList(Thread* t,
               uintptr_t* array,
               unsigned size,
               bool* objectMask,
               object this_,
               const char* spec,
               const jvalue* arguments)
      : t(static_cast<MyThread*>(t)),
        array(array),
        objectMask(objectMask),
        size(size),
        position(0),
        protector(this)
  {
    if (this_) {
      addObject(this_);
    }

    unsigned index = 0;
    for (MethodSpecIterator it(t, spec); it.hasNext();) {
      switch (*it.next()) {
      case 'L':
      case '[': {
        object* v = arguments[index++].l;
        addObject(v ? *v : 0);
      } break;

      case 'J':
        addLong(arguments[index++].j);
        break;

      case 'D':
        addLong(doubleToBits(arguments[index++].d));
        break;

      case 'F':
        addInt(floatToBits(arguments[index++].f));
        break;

      default:
        addInt(arguments[index++].i);
        break;
      }
    }
  }

  ArgumentList(Thread* t,
               uintptr_t* array,
               unsigned size,
               bool* objectMask,
               object this_,
               const char* spec,
               object arguments)
      : t(static_cast<MyThread*>(t)),
        array(array),
        objectMask(objectMask),
        size(size),
        position(0),
        protector(this)
  {
    if (this_) {
      addObject(this_);
    }

    unsigned index = 0;
    for (MethodSpecIterator it(t, spec); it.hasNext();) {
      switch (*it.next()) {
      case 'L':
      case '[':
        addObject(objectArrayBody(t, arguments, index++));
        break;

      case 'J':
      case 'D':
        addLong(
            fieldAtOffset<int64_t>(objectArrayBody(t, arguments, index++), 8));
        break;

      default:
        addInt(fieldAtOffset<int32_t>(objectArrayBody(t, arguments, index++),
                                      BytesPerWord));
        break;
      }
    }
  }

  void addObject(object v)
  {
    assertT(t, position < size);

    array[position] = reinterpret_cast<uintptr_t>(v);
    objectMask[position] = true;
    ++position;
  }

  void addInt(uintptr_t v)
  {
    assertT(t, position < size);

    array[position] = v;
    objectMask[position] = false;
    ++position;
  }

  void addLong(uint64_t v)
  {
    assertT(t, position < size - 1);

    memcpy(array + position, &v, 8);

    objectMask[position] = false;
    objectMask[position + 1] = false;

    position += 2;
  }

  MyThread* t;
  uintptr_t* array;
  bool* objectMask;
  unsigned size;
  unsigned position;

  class MyProtector : public Thread::Protector {
   public:
    MyProtector(ArgumentList* list) : Protector(list->t), list(list)
    {
    }

    virtual void visit(Heap::Visitor* v)
    {
      for (unsigned i = 0; i < list->position; ++i) {
        if (list->objectMask[i]) {
          v->visit(reinterpret_cast<object*>(list->array + i));
        }
      }
    }

    ArgumentList* list;
  } protector;
};

object invoke(Thread* thread, GcMethod* method, ArgumentList* arguments)
{
  MyThread* t = static_cast<MyThread*>(thread);

  if (false) {
    PROTECT(t, method);

    compile(
        t,
        local::codeAllocator(static_cast<MyThread*>(t)),
        0,
        resolveMethod(
            t, roots(t)->appLoader(), "foo/ClassName", "methodName", "()V"));
  }

  uintptr_t stackLimit = t->stackLimit;
  uintptr_t stackPosition = reinterpret_cast<uintptr_t>(&t);
  if (stackLimit == 0) {
    t->stackLimit = stackPosition - t->m->stackSizeInBytes;
  } else if (stackPosition < stackLimit) {
    throwNew(t, GcStackOverflowError::Type);
  }

  THREAD_RESOURCE(t,
                  uintptr_t,
                  stackLimit,
                  static_cast<MyThread*>(t)->stackLimit = stackLimit);

  unsigned returnCode = method->returnCode();
  unsigned returnType = fieldType(t, returnCode);

  uint64_t result;

  {
    MyThread::CallTrace trace(t, method);

    MyCheckpoint checkpoint(t);

    assertT(t, arguments->position == arguments->size);

    result = vmInvoke(
        t,
        reinterpret_cast<void*>(methodAddress(t, method)),
        arguments->array,
        arguments->position * BytesPerWord,
        t->arch->alignFrameSize(t->arch->argumentFootprint(arguments->position))
        * BytesPerWord,
        returnType);
  }

  if (t->exception) {
    if (UNLIKELY(t->getFlags() & Thread::UseBackupHeapFlag)) {
      collect(t, Heap::MinorCollection);
    }

    GcThrowable* exception = t->exception;
    t->exception = 0;
    vm::throw_(t, exception);
  }

  object r;
  switch (returnCode) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    r = makeInt(t, result);
    break;

  case LongField:
  case DoubleField:
    r = makeLong(t, result);
    break;

  case ObjectField:
    r = reinterpret_cast<object>(result);
    break;

  case VoidField:
    r = 0;
    break;

  default:
    abort(t);
  }

  return r;
}

class SignalHandler : public SignalRegistrar::Handler {
 public:
  typedef GcThrowable* (GcRoots::*ExceptionGetter)();
  SignalHandler(Gc::Type type, ExceptionGetter exc, unsigned fixedSize)
      : m(0), type(type), exc(exc), fixedSize(fixedSize)
  {
  }

  void setException(MyThread* t) {
    if (ensure(t, pad(fixedSize) + traceSize(t))) {
      t->setFlag(Thread::TracingFlag);
      t->exception = makeThrowable(t, type);
      t->clearFlag(Thread::TracingFlag);
    } else {
      // not enough memory available for a new exception and stack
      // trace -- use a preallocated instance instead
      t->exception = (vm::roots(t)->*exc)();
    }
  }

  virtual bool handleSignal(void** ip,
                            void** frame,
                            void** stack,
                            void** thread)
  {
    MyThread* t = static_cast<MyThread*>(m->localThread->get());
    if (t and t->state == Thread::ActiveState) {
      if (t->getFlags() & Thread::TryNativeFlag) {
        setException(t);

        popResources(t);

        GcContinuation* continuation;
        findUnwindTarget(t, ip, frame, stack, &continuation);

        t->trace->targetMethod = 0;
        t->trace->nativeMethod = 0;

        transition(t, *ip, *stack, continuation, t->trace);

        *thread = t;

        return true;
      } else if (methodForIp(t, *ip)) {
        // add one to the IP since findLineNumber will subtract one
        // when we make the trace:
        MyThread::TraceContext context(
            t,
            static_cast<uint8_t*>(*ip) + 1,
            static_cast<void**>(*stack) - t->arch->frameReturnAddressSize(),
            t->continuation,
            t->trace);

        setException(t);

        // printTrace(t, t->exception);

        GcContinuation* continuation;
        findUnwindTarget(t, ip, frame, stack, &continuation);

        transition(t, *ip, *stack, continuation, t->trace);

        *thread = t;

        return true;
      }
    }

    if (compileLog) {
      fflush(compileLog);
    }

    return false;
  }

  Machine* m;
  Gc::Type type;
  ExceptionGetter exc;
  unsigned fixedSize;
};

bool isThunk(MyThread* t, void* ip);

bool isVirtualThunk(MyThread* t, void* ip);

bool isThunkUnsafeStack(MyThread* t, void* ip);

void boot(MyThread* t, BootImage* image, uint8_t* code);

class MyProcessor;

MyProcessor* processor(MyThread* t);

#ifndef AVIAN_AOT_ONLY
void compileThunks(MyThread* t, FixedAllocator* allocator);
#endif

class CompilationHandlerList {
 public:
  CompilationHandlerList(CompilationHandlerList* next,
                         Processor::CompilationHandler* handler)
      : next(next), handler(handler)
  {
  }

  void dispose(Allocator* allocator)
  {
    if (next) {
      next->dispose(allocator);
    }
    handler->dispose();
    allocator->free(this, sizeof(*this));
  }

  CompilationHandlerList* next;
  Processor::CompilationHandler* handler;
};

template <class T, class C>
int checkConstant(MyThread* t, size_t expected, T C::*field, const char* name)
{
  size_t actual = reinterpret_cast<uint8_t*>(&(t->*field))
                  - reinterpret_cast<uint8_t*>(t);
  if (expected != actual) {
    fprintf(stderr,
            "constant mismatch (%s): \n\tconstant says: %d\n\tc++ compiler "
            "says: %d\n",
            name,
            (unsigned)expected,
            (unsigned)actual);
    return 1;
  }
  return 0;
}

class MyProcessor : public Processor {
 public:
  class Thunk {
   public:
    Thunk() : start(0), frameSavedOffset(0), length(0)
    {
    }

    Thunk(uint8_t* start, unsigned frameSavedOffset, unsigned length)
        : start(start), frameSavedOffset(frameSavedOffset), length(length)
    {
    }

    uint8_t* start;
    unsigned frameSavedOffset;
    unsigned length;
  };

  class ThunkCollection {
   public:
    Thunk default_;
    Thunk defaultVirtual;
    Thunk defaultDynamic;
    Thunk native;
    Thunk aioob;
    Thunk stackOverflow;
    Thunk table;
  };

  MyProcessor(System* s,
              Allocator* allocator,
              const char* crashDumpDirectory,
              bool useNativeFeatures)
      : s(s),
        allocator(allocator),
        roots(0),
        bootImage(0),
        heapImage(0),
        codeImage(0),
        codeImageSize(0),
        segFaultHandler(GcNullPointerException::Type,
                        &GcRoots::nullPointerException,
                        GcNullPointerException::FixedSize),
        divideByZeroHandler(GcArithmeticException::Type,
                            &GcRoots::arithmeticException,
                            GcArithmeticException::FixedSize),
        codeAllocator(s, Slice<uint8_t>(0, 0)),
        callTableSize(0),
        dynamicIndex(0),
        useNativeFeatures(useNativeFeatures),
        compilationHandlers(0),
        dynamicTable(0),
        dynamicTableSize(0)
  {
    thunkTable[compileMethodIndex] = voidPointer(local::compileMethod);
    thunkTable[compileVirtualMethodIndex] = voidPointer(compileVirtualMethod);
    thunkTable[linkDynamicMethodIndex] = voidPointer(linkDynamicMethod);
    thunkTable[invokeNativeIndex] = voidPointer(invokeNative);
    thunkTable[throwArrayIndexOutOfBoundsIndex]
        = voidPointer(throwArrayIndexOutOfBounds);
    thunkTable[throwStackOverflowIndex] = voidPointer(throwStackOverflow);

    using namespace avian::codegen::runtime;

#define THUNK(s) thunkTable[s##Index] = voidPointer(s);
#include "thunks.cpp"
#undef THUNK
    // Set the dummyIndex entry to a constant which should require the
    // maximum number of bytes to represent in assembly code
    // (i.e. can't be represented by a smaller number of bytes and
    // implicitly sign- or zero-extended).  We'll use this property
    // later to determine the maximum size of a thunk in the thunk
    // table.
    thunkTable[dummyIndex] = reinterpret_cast<void*>(
        static_cast<uintptr_t>(UINT64_C(0x5555555555555555)));

    signals.setCrashDumpDirectory(crashDumpDirectory);
  }

  virtual Thread* makeThread(Machine* m, GcThread* javaThread, Thread* parent)
  {
    MyThread* t = new (m->heap->allocate(sizeof(MyThread))) MyThread(
        m, javaThread, static_cast<MyThread*>(parent), useNativeFeatures);

    t->heapImage = heapImage;
    t->codeImage = codeImage;
    t->thunkTable = thunkTable;
    t->dynamicTable = local::dynamicTable(t);

#if TARGET_BYTES_PER_WORD == BYTES_PER_WORD

    int mismatches
        = checkConstant(t,
                        TARGET_THREAD_EXCEPTION,
                        &Thread::exception,
                        "TARGET_THREAD_EXCEPTION")
          + checkConstant(t,
                          TARGET_THREAD_EXCEPTIONSTACKADJUSTMENT,
                          &MyThread::exceptionStackAdjustment,
                          "TARGET_THREAD_EXCEPTIONSTACKADJUSTMENT")
          + checkConstant(t,
                          TARGET_THREAD_EXCEPTIONOFFSET,
                          &MyThread::exceptionOffset,
                          "TARGET_THREAD_EXCEPTIONOFFSET")
          + checkConstant(t,
                          TARGET_THREAD_EXCEPTIONHANDLER,
                          &MyThread::exceptionHandler,
                          "TARGET_THREAD_EXCEPTIONHANDLER")
          + checkConstant(
                t, TARGET_THREAD_IP, &MyThread::ip, "TARGET_THREAD_IP")
          + checkConstant(
                t, TARGET_THREAD_STACK, &MyThread::stack, "TARGET_THREAD_STACK")
          + checkConstant(t,
                          TARGET_THREAD_NEWSTACK,
                          &MyThread::newStack,
                          "TARGET_THREAD_NEWSTACK")
          + checkConstant(t,
                          TARGET_THREAD_TAILADDRESS,
                          &MyThread::tailAddress,
                          "TARGET_THREAD_TAILADDRESS")
          + checkConstant(t,
                          TARGET_THREAD_VIRTUALCALLTARGET,
                          &MyThread::virtualCallTarget,
                          "TARGET_THREAD_VIRTUALCALLTARGET")
          + checkConstant(t,
                          TARGET_THREAD_VIRTUALCALLINDEX,
                          &MyThread::virtualCallIndex,
                          "TARGET_THREAD_VIRTUALCALLINDEX")
          + checkConstant(t,
                          TARGET_THREAD_HEAPIMAGE,
                          &MyThread::heapImage,
                          "TARGET_THREAD_HEAPIMAGE")
          + checkConstant(t,
                          TARGET_THREAD_CODEIMAGE,
                          &MyThread::codeImage,
                          "TARGET_THREAD_CODEIMAGE")
          + checkConstant(t,
                          TARGET_THREAD_THUNKTABLE,
                          &MyThread::thunkTable,
                          "TARGET_THREAD_THUNKTABLE")
          + checkConstant(t,
                          TARGET_THREAD_DYNAMICTABLE,
                          &MyThread::dynamicTable,
                          "TARGET_THREAD_DYNAMICTABLE")
          + checkConstant(t,
                          TARGET_THREAD_STACKLIMIT,
                          &MyThread::stackLimit,
                          "TARGET_THREAD_STACKLIMIT");

    if (mismatches > 0) {
      fprintf(stderr, "%d constant mismatches\n", mismatches);
      abort(t);
    }

    expect(t, TargetClassArrayElementSize == ClassArrayElementSize);
    expect(t, TargetClassFixedSize == ClassFixedSize);
    expect(t, TargetClassVtable == ClassVtable);

#endif

    t->init();

    return t;
  }

  virtual GcMethod* makeMethod(vm::Thread* t,
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
                               GcCode* code)
  {
    if (code) {
      code->compiled() = local::defaultThunk(static_cast<MyThread*>(t));
    }

    return vm::makeMethod(t,
                          vmFlags,
                          returnCode,
                          parameterCount,
                          parameterFootprint,
                          flags,
                          offset,
                          0,
                          0,
                          name,
                          spec,
                          addendum,
                          class_,
                          code);
  }

  virtual GcClass* makeClass(vm::Thread* t,
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
                             unsigned vtableLength)
  {
    return vm::makeClass(t,
                         flags,
                         vmFlags,
                         fixedSize,
                         arrayElementSize,
                         arrayDimensions,
                         arrayElementClass,
                         0,
                         objectMask,
                         name,
                         sourceFile,
                         super,
                         interfaceTable,
                         virtualTable,
                         fieldTable,
                         methodTable,
                         addendum,
                         staticTable,
                         loader,
                         0,
                         vtableLength);
  }

  virtual void initVtable(Thread* t, GcClass* c)
  {
    PROTECT(t, c);
    for (int i = c->length() - 1; i >= 0; --i) {
      void* thunk
          = reinterpret_cast<void*>(virtualThunk(static_cast<MyThread*>(t), i));
      c->vtable()[i] = thunk;
    }
  }

  virtual void visitObjects(Thread* vmt, Heap::Visitor* v)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    if (t == t->m->rootThread) {
      v->visit(&roots);
    }

    for (MyThread::CallTrace* trace = t->trace; trace; trace = trace->next) {
      v->visit(&(trace->continuation));
      v->visit(&(trace->nativeMethod));
      v->visit(&(trace->targetMethod));
      v->visit(&(trace->originalMethod));
    }

    v->visit(&(t->continuation));

    for (Reference* r = t->reference; r; r = r->next) {
      v->visit(&(r->target));
    }

    visitStack(t, v);
  }

  virtual void walkStack(Thread* vmt, StackVisitor* v)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    MyStackWalker walker(t);
    walker.walk(v);
  }

  virtual int lineNumber(Thread* vmt, GcMethod* method, int ip)
  {
    return findLineNumber(static_cast<MyThread*>(vmt), method, ip);
  }

  virtual object* makeLocalReference(Thread* vmt, object o)
  {
    if (o) {
      MyThread* t = static_cast<MyThread*>(vmt);

      for (Reference* r = t->reference; r; r = r->next) {
        if (r->target == o) {
          acquire(t, r);

          return &(r->target);
        }
      }

      Reference* r = new (t->m->heap->allocate(sizeof(Reference)))
          Reference(o, &(t->reference), false);

      acquire(t, r);

      return &(r->target);
    } else {
      return 0;
    }
  }

  virtual void disposeLocalReference(Thread* t, object* r)
  {
    if (r) {
      release(t, reinterpret_cast<Reference*>(r));
    }
  }

  virtual bool pushLocalFrame(Thread* vmt, unsigned)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    t->referenceFrame = new (t->m->heap->allocate(sizeof(List<Reference*>)))
        List<Reference*>(t->reference, t->referenceFrame);

    return true;
  }

  virtual void popLocalFrame(Thread* vmt)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    List<Reference*>* f = t->referenceFrame;
    t->referenceFrame = f->next;
    while (t->reference != f->item) {
      vm::dispose(t, t->reference);
    }

    t->m->heap->free(f, sizeof(List<Reference*>));
  }

  virtual object invokeArray(Thread* t,
                             GcMethod* method,
                             object this_,
                             object arguments)
  {
    assertT(t, t->exception == 0);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());

    unsigned size = method->parameterFootprint();
    THREAD_RUNTIME_ARRAY(t, uintptr_t, array, size);
    THREAD_RUNTIME_ARRAY(t, bool, objectMask, size);
    ArgumentList list(t,
                      RUNTIME_ARRAY_BODY(array),
                      size,
                      RUNTIME_ARRAY_BODY(objectMask),
                      this_,
                      spec,
                      arguments);

    PROTECT(t, method);

    method = findMethod(t, method, this_);

    compile(static_cast<MyThread*>(t),
            local::codeAllocator(static_cast<MyThread*>(t)),
            0,
            method);

    return local::invoke(t, method, &list);
  }

  virtual object invokeArray(Thread* t,
                             GcMethod* method,
                             object this_,
                             const jvalue* arguments)
  {
    assertT(t, t->exception == 0);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());

    unsigned size = method->parameterFootprint();
    THREAD_RUNTIME_ARRAY(t, uintptr_t, array, size);
    THREAD_RUNTIME_ARRAY(t, bool, objectMask, size);
    ArgumentList list(t,
                      RUNTIME_ARRAY_BODY(array),
                      size,
                      RUNTIME_ARRAY_BODY(objectMask),
                      this_,
                      spec,
                      arguments);

    PROTECT(t, method);

    method = findMethod(t, method, this_);

    compile(static_cast<MyThread*>(t),
            local::codeAllocator(static_cast<MyThread*>(t)),
            0,
            method);

    return local::invoke(t, method, &list);
  }

  virtual object invokeList(Thread* t,
                            GcMethod* method,
                            object this_,
                            bool indirectObjects,
                            va_list arguments)
  {
    assertT(t, t->exception == 0);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>(method->spec()->body().begin());

    unsigned size = method->parameterFootprint();
    THREAD_RUNTIME_ARRAY(t, uintptr_t, array, size);
    THREAD_RUNTIME_ARRAY(t, bool, objectMask, size);
    ArgumentList list(t,
                      RUNTIME_ARRAY_BODY(array),
                      size,
                      RUNTIME_ARRAY_BODY(objectMask),
                      this_,
                      spec,
                      indirectObjects,
                      arguments);

    PROTECT(t, method);

    method = findMethod(t, method, this_);

    compile(static_cast<MyThread*>(t),
            local::codeAllocator(static_cast<MyThread*>(t)),
            0,
            method);

    return local::invoke(t, method, &list);
  }

  virtual object invokeList(Thread* t,
                            GcClassLoader* loader,
                            const char* className,
                            const char* methodName,
                            const char* methodSpec,
                            object this_,
                            va_list arguments)
  {
    assertT(t, t->exception == 0);

    assertT(
        t,
        t->state == Thread::ActiveState or t->state == Thread::ExclusiveState);

    unsigned size = parameterFootprint(t, methodSpec, this_ == 0);
    THREAD_RUNTIME_ARRAY(t, uintptr_t, array, size);
    THREAD_RUNTIME_ARRAY(t, bool, objectMask, size);
    ArgumentList list(t,
                      RUNTIME_ARRAY_BODY(array),
                      size,
                      RUNTIME_ARRAY_BODY(objectMask),
                      this_,
                      methodSpec,
                      false,
                      arguments);

    GcMethod* method
        = resolveMethod(t, loader, className, methodName, methodSpec);

    assertT(t, ((method->flags() & ACC_STATIC) == 0) xor (this_ == 0));

    PROTECT(t, method);

    compile(static_cast<MyThread*>(t),
            local::codeAllocator(static_cast<MyThread*>(t)),
            0,
            method);

    return local::invoke(t, method, &list);
  }

  virtual void dispose(Thread* vmt)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    while (t->reference) {
      vm::dispose(t, t->reference);
    }

    t->arch->release();

    t->m->heap->free(t, sizeof(*t));
  }

  virtual void dispose()
  {
    if (codeAllocator.memory.begin()) {
#ifndef AVIAN_AOT_ONLY
      Memory::free(codeAllocator.memory);
#endif
    }

    if(compilationHandlers) {
      compilationHandlers->dispose(allocator);
    }

    signals.unregisterHandler(SignalRegistrar::SegFault);
    signals.unregisterHandler(SignalRegistrar::DivideByZero);
    signals.setCrashDumpDirectory(0);

    if (dynamicTable) {
      allocator->free(dynamicTable, dynamicTableSize);
    }

    this->~MyProcessor();

    allocator->free(this, sizeof(*this));
  }

  virtual object getStackTrace(Thread* vmt, Thread* vmTarget)
  {
    MyThread* t = static_cast<MyThread*>(vmt);
    MyThread* target = static_cast<MyThread*>(vmTarget);
    MyProcessor* p = this;

    class Visitor : public System::ThreadVisitor {
     public:
      Visitor(MyThread* t, MyProcessor* p, MyThread* target)
          : t(t), p(p), target(target), trace(0)
      {
      }

      virtual void visit(void* ip, void* stack, void* link)
      {
        MyThread::TraceContext c(target, link);

        if (methodForIp(t, ip)) {
          // we caught the thread in Java code - use the register values
          c.ip = ip;
          c.stack = stack;
          c.methodIsMostRecent = true;
        } else if (target->transition) {
          // we caught the thread in native code while in the middle
          // of updating the context fields (MyThread::stack, etc.)
          static_cast<MyThread::Context&>(c) = *(target->transition);
        } else if (isVmInvokeUnsafeStack(ip)) {
          // we caught the thread in native code just after returning
          // from java code, but before clearing MyThread::stack
          // (which now contains a garbage value), and the most recent
          // Java frame, if any, can be found in
          // MyThread::continuation or MyThread::trace
          c.ip = 0;
          c.stack = 0;
        } else if (target->stack and (not isThunkUnsafeStack(t, ip))
                   and (not isVirtualThunk(t, ip))) {
          // we caught the thread in a thunk or native code, and the
          // saved stack pointer indicates the most recent Java frame
          // on the stack
          c.ip = getIp(target);
          c.stack = target->stack;
        } else if (isThunk(t, ip) or isVirtualThunk(t, ip)) {
          // we caught the thread in a thunk where the stack register
          // indicates the most recent Java frame on the stack

          // On e.g. x86, the return address will have already been
          // pushed onto the stack, in which case we use getIp to
          // retrieve it.  On e.g. ARM, it will be in the
          // link register.  Note that we can't just check if the link
          // argument is null here, since we use ecx/rcx as a
          // pseudo-link register on x86 for the purpose of tail
          // calls.
          c.ip = t->arch->hasLinkRegister() ? link : getIp(t, link, stack);
          c.stack = stack;
        } else {
          // we caught the thread in native code, and the most recent
          // Java frame, if any, can be found in
          // MyThread::continuation or MyThread::trace
          c.ip = 0;
          c.stack = 0;
        }

        if (ensure(t, traceSize(target))) {
          t->setFlag(Thread::TracingFlag);
          trace = makeTrace(t, target);
          t->clearFlag(Thread::TracingFlag);
        }
      }

      MyThread* t;
      MyProcessor* p;
      MyThread* target;
      object trace;
    } visitor(t, p, target);

    t->m->system->visit(t->systemThread, target->systemThread, &visitor);

    if (UNLIKELY(t->getFlags() & Thread::UseBackupHeapFlag)) {
      PROTECT(t, visitor.trace);

      collect(t, Heap::MinorCollection);
    }

    return visitor.trace ? visitor.trace : makeObjectArray(t, 0);
  }

  virtual void initialize(BootImage* image, Slice<uint8_t> code)
  {
    bootImage = image;
    codeAllocator.memory = code;
  }

  virtual void addCompilationHandler(CompilationHandler* handler)
  {
    compilationHandlers
        = new (allocator->allocate(sizeof(CompilationHandlerList)))
        CompilationHandlerList(compilationHandlers, handler);
  }

  virtual void compileMethod(Thread* vmt,
                             Zone* zone,
                             GcTriple** constants,
                             GcTriple** calls,
                             avian::codegen::DelayedPromise** addresses,
                             GcMethod* method,
                             OffsetResolver* resolver,
                             JavaVM* hostVM)
  {
    MyThread* t = static_cast<MyThread*>(vmt);
    BootContext bootContext(
        t, *constants, *calls, *addresses, zone, resolver, hostVM);

    compile(t, &codeAllocator, &bootContext, method);

    *constants = bootContext.constants;
    *calls = bootContext.calls;
    *addresses = bootContext.addresses;
  }

  virtual void visitRoots(Thread* t, HeapWalker* w)
  {
    bootImage->methodTree = w->visitRoot(compileRoots(t)->methodTree());
    bootImage->methodTreeSentinal
        = w->visitRoot(compileRoots(t)->methodTreeSentinal());
    bootImage->virtualThunks = w->visitRoot(compileRoots(t)->virtualThunks());
  }

  virtual void normalizeVirtualThunks(Thread* t)
  {
    GcWordArray* a = compileRoots(t)->virtualThunks();
    for (unsigned i = 0; i < a->length(); i += 2) {
      if (a->body()[i]) {
        a->body()[i]
            -= reinterpret_cast<uintptr_t>(codeAllocator.memory.begin());
      }
    }
  }

  virtual unsigned* makeCallTable(Thread* t, HeapWalker* w)
  {
    bootImage->codeSize = codeAllocator.offset;
    bootImage->callCount = callTableSize;

    unsigned* table = static_cast<unsigned*>(
        t->m->heap->allocate(callTableSize * sizeof(unsigned) * 2));

    unsigned index = 0;
    GcArray* callTable = compileRoots(t)->callTable();
    for (unsigned i = 0; i < callTable->length(); ++i) {
      for (GcCallNode* p = cast<GcCallNode>(t, callTable->body()[i]); p;
           p = p->next()) {
        table[index++]
            = targetVW(p->address() - reinterpret_cast<uintptr_t>(
                                          codeAllocator.memory.begin()));
        table[index++] = targetVW(
            w->map()->find(p->target())
            | (static_cast<unsigned>(p->flags()) << TargetBootShift));
      }
    }

    return table;
  }

  virtual void boot(Thread* t, BootImage* image, uint8_t* code)
  {
#ifndef AVIAN_AOT_ONLY
    if (codeAllocator.memory.begin() == 0) {
      codeAllocator.memory = Memory::allocate(ExecutableAreaSizeInBytes,
                                              Memory::ReadWriteExecute);

      expect(t, codeAllocator.memory.begin());
    }
#endif

    if (image and code) {
      local::boot(static_cast<MyThread*>(t), image, code);
    } else {
      roots = makeCompileRoots(t, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

      {
        GcArray* ct = makeArray(t, 128);
        // sequence point, for gc (don't recombine statements)
        compileRoots(t)->setCallTable(t, ct);
      }

      GcTreeNode* tree = makeTreeNode(t, 0, 0, 0);
      compileRoots(t)->setMethodTreeSentinal(t, tree);
      compileRoots(t)->setMethodTree(t, tree);
      tree->setLeft(t, tree);
      tree->setRight(t, tree);
    }

#ifdef AVIAN_AOT_ONLY
    thunks = bootThunks;
#else
    local::compileThunks(static_cast<MyThread*>(t), &codeAllocator);

    if (not(image and code)) {
      bootThunks = thunks;
    }
#endif

    segFaultHandler.m = t->m;
    expect(
        t,
        signals.registerHandler(SignalRegistrar::SegFault, &segFaultHandler));

    divideByZeroHandler.m = t->m;
    expect(t,
           signals.registerHandler(SignalRegistrar::DivideByZero,
                                   &divideByZeroHandler));
  }

  virtual void callWithCurrentContinuation(Thread* t, object receiver)
  {
    if (Continuations) {
      local::callWithCurrentContinuation(static_cast<MyThread*>(t), receiver);
    } else {
      abort(t);
    }
  }

  virtual void dynamicWind(Thread* t, object before, object thunk, object after)
  {
    if (Continuations) {
      local::dynamicWind(static_cast<MyThread*>(t), before, thunk, after);
    } else {
      abort(t);
    }
  }

  virtual void feedResultToContinuation(Thread* t,
                                        GcContinuation* continuation,
                                        object result)
  {
    if (Continuations) {
      callContinuation(static_cast<MyThread*>(t), continuation, result, 0);
    } else {
      abort(t);
    }
  }

  virtual void feedExceptionToContinuation(Thread* t,
                                           GcContinuation* continuation,
                                           GcThrowable* exception)
  {
    if (Continuations) {
      callContinuation(static_cast<MyThread*>(t), continuation, 0, exception);
    } else {
      abort(t);
    }
  }

  virtual void walkContinuationBody(Thread* t,
                                    Heap::Walker* w,
                                    object o,
                                    unsigned start)
  {
    if (Continuations) {
      local::walkContinuationBody(
          static_cast<MyThread*>(t), w, cast<GcContinuation>(t, o), start);
    } else {
      abort(t);
    }
  }

  System* s;
  SignalRegistrar signals;
  Allocator* allocator;
  GcCompileRoots* roots;
  BootImage* bootImage;
  uintptr_t* heapImage;
  uint8_t* codeImage;
  unsigned codeImageSize;
  SignalHandler segFaultHandler;
  SignalHandler divideByZeroHandler;
  FixedAllocator codeAllocator;
  ThunkCollection thunks;
  ThunkCollection bootThunks;
  unsigned callTableSize;
  unsigned dynamicIndex;
  bool useNativeFeatures;
  void* thunkTable[dummyIndex + 1];
  CompilationHandlerList* compilationHandlers;
  void** dynamicTable;
  unsigned dynamicTableSize;
};

unsigned& dynamicIndex(MyThread* t)
{
  return static_cast<MyProcessor*>(t->m->processor)->dynamicIndex;
}

void**& dynamicTable(MyThread* t)
{
  return static_cast<MyProcessor*>(t->m->processor)->dynamicTable;
}

unsigned& dynamicTableSize(MyThread* t)
{
  return static_cast<MyProcessor*>(t->m->processor)->dynamicTableSize;
}

const char* stringOrNull(const char* str)
{
  if (str) {
    return str;
  } else {
    return "(null)";
  }
}

size_t stringOrNullSize(const char* str)
{
  return strlen(stringOrNull(str));
}

void logCompile(MyThread* t,
                const void* code,
                unsigned size,
                const char* class_,
                const char* name,
                const char* spec)
{
  static bool open = false;
  if (not open) {
    open = true;
    const char* path = findProperty(t, "avian.jit.log");
    if (path) {
      compileLog = vm::fopen(path, "wb");
    } else if (DebugCompile) {
      compileLog = stderr;
    }
  }

  if (compileLog) {
    fprintf(compileLog,
            "%p,%p %s.%s%s\n",
            code,
            static_cast<const uint8_t*>(code) + size,
            class_,
            name,
            spec);
  }

  size_t nameLength = stringOrNullSize(class_) + stringOrNullSize(name)
                      + stringOrNullSize(spec) + 2;

  THREAD_RUNTIME_ARRAY(t, char, completeName, nameLength);

  sprintf(RUNTIME_ARRAY_BODY(completeName),
          "%s.%s%s",
          stringOrNull(class_),
          stringOrNull(name),
          stringOrNull(spec));

  MyProcessor* p = static_cast<MyProcessor*>(t->m->processor);
  for (CompilationHandlerList* h = p->compilationHandlers; h; h = h->next) {
    h->handler->compiled(code, 0, 0, RUNTIME_ARRAY_BODY(completeName));
  }
}

void* compileMethod2(MyThread* t, void* ip)
{
  GcCallNode* node = findCallNode(t, ip);
  GcMethod* target = node->target();

  PROTECT(t, node);
  PROTECT(t, target);

  t->trace->targetMethod = target;

  THREAD_RESOURCE0(t, static_cast<MyThread*>(t)->trace->targetMethod = 0);

  compile(t, codeAllocator(t), 0, target);

  uint8_t* updateIp = static_cast<uint8_t*>(ip);

  MyProcessor* p = processor(t);

  bool updateCaller = updateIp < p->codeImage
                      or updateIp >= p->codeImage + p->codeImageSize;

  uintptr_t address;
  if (target->flags() & ACC_NATIVE) {
    address = useLongJump(t, reinterpret_cast<uintptr_t>(ip))
                  or (not updateCaller)
                  ? bootNativeThunk(t)
                  : nativeThunk(t);
  } else {
    address = methodAddress(t, target);
  }

  if (updateCaller) {
    avian::codegen::lir::UnaryOperation op;
    if (node->flags() & TraceElement::LongCall) {
      if (node->flags() & TraceElement::TailCall) {
        op = avian::codegen::lir::AlignedLongJump;
      } else {
        op = avian::codegen::lir::AlignedLongCall;
      }
    } else if (node->flags() & TraceElement::TailCall) {
      op = avian::codegen::lir::AlignedJump;
    } else {
      op = avian::codegen::lir::AlignedCall;
    }

    updateCall(t, op, updateIp, reinterpret_cast<void*>(address));
  }

  return reinterpret_cast<void*>(address);
}

bool isThunk(MyProcessor::ThunkCollection* thunks, void* ip)
{
  uint8_t* thunkStart = thunks->default_.start;
  uint8_t* thunkEnd = thunks->table.start + (thunks->table.length * ThunkCount);

  return (reinterpret_cast<uintptr_t>(ip)
          >= reinterpret_cast<uintptr_t>(thunkStart)
          and reinterpret_cast<uintptr_t>(ip)
              < reinterpret_cast<uintptr_t>(thunkEnd));
}

bool isThunk(MyThread* t, void* ip)
{
  MyProcessor* p = processor(t);

  return isThunk(&(p->thunks), ip) or isThunk(&(p->bootThunks), ip);
}

bool isThunkUnsafeStack(MyProcessor::Thunk* thunk, void* ip)
{
  return reinterpret_cast<uintptr_t>(ip)
         >= reinterpret_cast<uintptr_t>(thunk->start)
         and reinterpret_cast<uintptr_t>(ip)
             < reinterpret_cast<uintptr_t>(thunk->start
                                           + thunk->frameSavedOffset);
}

bool isThunkUnsafeStack(MyProcessor::ThunkCollection* thunks, void* ip)
{
  const unsigned NamedThunkCount = 6;

  MyProcessor::Thunk table[NamedThunkCount + ThunkCount];

  table[0] = thunks->default_;
  table[1] = thunks->defaultVirtual;
  table[2] = thunks->defaultDynamic;
  table[3] = thunks->native;
  table[4] = thunks->aioob;
  table[5] = thunks->stackOverflow;

  for (unsigned i = 0; i < ThunkCount; ++i) {
    new (table + NamedThunkCount + i)
        MyProcessor::Thunk(thunks->table.start + (i * thunks->table.length),
                           thunks->table.frameSavedOffset,
                           thunks->table.length);
  }

  for (unsigned i = 0; i < NamedThunkCount + ThunkCount; ++i) {
    if (isThunkUnsafeStack(table + i, ip)) {
      return true;
    }
  }

  return false;
}

bool isVirtualThunk(MyThread* t, void* ip)
{
  GcWordArray* a = compileRoots(t)->virtualThunks();
  for (unsigned i = 0; i < a->length(); i += 2) {
    uintptr_t start = a->body()[i];
    uintptr_t end = start + a->body()[i + 1];

    if (reinterpret_cast<uintptr_t>(ip) >= start
        and reinterpret_cast<uintptr_t>(ip) < end) {
      return true;
    }
  }

  return false;
}

bool isThunkUnsafeStack(MyThread* t, void* ip)
{
  MyProcessor* p = processor(t);

  return isThunk(t, ip) and (isThunkUnsafeStack(&(p->thunks), ip)
                             or isThunkUnsafeStack(&(p->bootThunks), ip));
}

GcCallNode* findCallNode(MyThread* t, void* address)
{
  if (DebugCallTable) {
    fprintf(stderr, "find call node %p\n", address);
  }

  // we must use a version of the call table at least as recent as the
  // compiled form of the method containing the specified address (see
  // compile(MyThread*, Allocator*, BootContext*, object)):
  loadMemoryBarrier();

  GcArray* table = compileRoots(t)->callTable();

  intptr_t key = reinterpret_cast<intptr_t>(address);
  unsigned index = static_cast<uintptr_t>(key) & (table->length() - 1);

  for (GcCallNode* n = cast<GcCallNode>(t, table->body()[index]); n;
       n = n->next()) {
    intptr_t k = n->address();

    if (k == key) {
      return n;
    }
  }

  return 0;
}

GcArray* resizeTable(MyThread* t, GcArray* oldTable, unsigned newLength)
{
  PROTECT(t, oldTable);

  GcCallNode* oldNode = 0;
  PROTECT(t, oldNode);

  GcArray* newTable = makeArray(t, newLength);
  PROTECT(t, newTable);

  for (unsigned i = 0; i < oldTable->length(); ++i) {
    for (oldNode = cast<GcCallNode>(t, oldTable->body()[i]); oldNode;
         oldNode = oldNode->next()) {
      intptr_t k = oldNode->address();

      unsigned index = k & (newLength - 1);

      GcCallNode* newNode
          = makeCallNode(t,
                         oldNode->address(),
                         oldNode->target(),
                         oldNode->flags(),
                         cast<GcCallNode>(t, newTable->body()[index]));

      newTable->setBodyElement(t, index, newNode);
    }
  }

  return newTable;
}

GcArray* insertCallNode(MyThread* t,
                        GcArray* table,
                        unsigned* size,
                        GcCallNode* node)
{
  if (DebugCallTable) {
    fprintf(stderr,
            "insert call node %p\n",
            reinterpret_cast<void*>(node->address()));
  }

  PROTECT(t, table);
  PROTECT(t, node);

  ++(*size);

  if (*size >= table->length() * 2) {
    table = resizeTable(t, table, table->length() * 2);
  }

  intptr_t key = node->address();
  unsigned index = static_cast<uintptr_t>(key) & (table->length() - 1);

  node->setNext(t, cast<GcCallNode>(t, table->body()[index]));
  table->setBodyElement(t, index, node);

  return table;
}

GcHashMap* makeClassMap(Thread* t,
                        unsigned* table,
                        unsigned count,
                        uintptr_t* heap)
{
  GcArray* array = makeArray(t, nextPowerOfTwo(count));
  GcHashMap* map = makeHashMap(t, 0, array);
  PROTECT(t, map);

  for (unsigned i = 0; i < count; ++i) {
    GcClass* c = cast<GcClass>(t, bootObject(heap, table[i]));
    hashMapInsert(t, map, c->name(), c, byteArrayHash);
  }

  return map;
}

GcArray* makeStaticTableArray(Thread* t,
                              unsigned* bootTable,
                              unsigned bootCount,
                              unsigned* appTable,
                              unsigned appCount,
                              uintptr_t* heap)
{
  GcArray* array = makeArray(t, bootCount + appCount);

  for (unsigned i = 0; i < bootCount; ++i) {
    array->setBodyElement(
        t, i, cast<GcClass>(t, bootObject(heap, bootTable[i]))->staticTable());
  }

  for (unsigned i = 0; i < appCount; ++i) {
    array->setBodyElement(
        t,
        (bootCount + i),
        cast<GcClass>(t, bootObject(heap, appTable[i]))->staticTable());
  }

  return array;
}

GcHashMap* makeStringMap(Thread* t,
                         unsigned* table,
                         unsigned count,
                         uintptr_t* heap)
{
  GcArray* array = makeArray(t, nextPowerOfTwo(count));
  GcHashMap* map = makeWeakHashMap(t, 0, array)->as<GcHashMap>(t);
  PROTECT(t, map);

  for (unsigned i = 0; i < count; ++i) {
    object s = bootObject(heap, table[i]);
    hashMapInsert(t, map, s, 0, stringHash);
  }

  return map;
}

GcArray* makeCallTable(MyThread* t,
                       uintptr_t* heap,
                       unsigned* calls,
                       unsigned count,
                       uintptr_t base)
{
  GcArray* table = makeArray(t, nextPowerOfTwo(count));
  PROTECT(t, table);

  unsigned size = 0;
  for (unsigned i = 0; i < count; ++i) {
    unsigned address = calls[i * 2];
    unsigned target = calls[(i * 2) + 1];

    GcCallNode* node
        = makeCallNode(t,
                       base + address,
                       cast<GcMethod>(t, bootObject(heap, target & BootMask)),
                       target >> BootShift,
                       0);

    table = insertCallNode(t, table, &size, node);
  }

  return table;
}

void fixupHeap(MyThread* t UNUSED,
               uintptr_t* map,
               unsigned size,
               uintptr_t* heap)
{
  for (unsigned word = 0; word < size; ++word) {
    uintptr_t w = map[word];
    if (w) {
      for (unsigned bit = 0; bit < BitsPerWord; ++bit) {
        if (w & (static_cast<uintptr_t>(1) << bit)) {
          unsigned index = indexOf(word, bit);

          uintptr_t* p = heap + index;
          assertT(t, *p);

          uintptr_t number = *p & BootMask;
          uintptr_t mark = *p >> BootShift;

          if (number) {
            *p = reinterpret_cast<uintptr_t>(heap + (number - 1)) | mark;
            if (false) {
              fprintf(stderr,
                      "fixup %d: %d 0x%x\n",
                      index,
                      static_cast<unsigned>(number),
                      static_cast<unsigned>(*p));
            }
          } else {
            *p = mark;
          }
        }
      }
    }
  }
}

void resetClassRuntimeState(Thread* t,
                            GcClass* c,
                            uintptr_t* heap,
                            unsigned heapSize)
{
  c->runtimeDataIndex() = 0;

  if (c->arrayElementSize() == 0) {
    GcSingleton* staticTable = c->staticTable()->as<GcSingleton>(t);
    if (staticTable) {
      for (unsigned i = 0; i < singletonCount(t, staticTable); ++i) {
        if (singletonIsObject(t, staticTable, i)
            and (reinterpret_cast<uintptr_t*>(
                     singletonObject(t, staticTable, i)) < heap
                 or reinterpret_cast<uintptr_t*>(singletonObject(
                        t, staticTable, i)) > heap + heapSize)) {
          singletonObject(t, staticTable, i) = 0;
        }
      }
    }
  }

  if (GcArray* mtable = cast<GcArray>(t, c->methodTable())) {
    PROTECT(t, mtable);
    for (unsigned i = 0; i < mtable->length(); ++i) {
      GcMethod* m = cast<GcMethod>(t, mtable->body()[i]);

      m->nativeID() = 0;
      m->runtimeDataIndex() = 0;

      if (m->vmFlags() & ClassInitFlag) {
        c->vmFlags() |= NeedInitFlag;
        c->vmFlags() &= ~InitErrorFlag;
      }
    }
  }

  t->m->processor->initVtable(t, c);
}

void resetRuntimeState(Thread* t,
                       GcHashMap* map,
                       uintptr_t* heap,
                       unsigned heapSize)
{
  for (HashMapIterator it(t, map); it.hasMore();) {
    resetClassRuntimeState(
        t, cast<GcClass>(t, it.next()->second()), heap, heapSize);
  }
}

void fixupMethods(Thread* t,
                  GcHashMap* map,
                  BootImage* image UNUSED,
                  uint8_t* code)
{
  for (HashMapIterator it(t, map); it.hasMore();) {
    GcClass* c = cast<GcClass>(t, it.next()->second());

    if (GcArray* mtable = cast<GcArray>(t, c->methodTable())) {
      PROTECT(t, mtable);
      for (unsigned i = 0; i < mtable->length(); ++i) {
        GcMethod* method = cast<GcMethod>(t, mtable->body()[i]);
        if (method->code()) {
          assertT(t,
                  methodCompiled(t, method)
                  <= static_cast<int32_t>(image->codeSize));

          method->code()->compiled() = methodCompiled(t, method)
                                       + reinterpret_cast<uintptr_t>(code);

          if (DebugCompile) {
            logCompile(static_cast<MyThread*>(t),
                       reinterpret_cast<uint8_t*>(methodCompiled(t, method)),
                       methodCompiledSize(t, method),
                       reinterpret_cast<char*>(
                           method->class_()->name()->body().begin()),
                       reinterpret_cast<char*>(method->name()->body().begin()),
                       reinterpret_cast<char*>(method->spec()->body().begin()));
          }
        }
      }
    }

    t->m->processor->initVtable(t, c);
  }
}

MyProcessor::Thunk thunkToThunk(const BootImage::Thunk& thunk, uint8_t* base)
{
  return MyProcessor::Thunk(
      base + thunk.start, thunk.frameSavedOffset, thunk.length);
}

void findThunks(MyThread* t, BootImage* image, uint8_t* code)
{
  MyProcessor* p = processor(t);

  p->bootThunks.default_ = thunkToThunk(image->thunks.default_, code);
  p->bootThunks.defaultVirtual = thunkToThunk(image->thunks.defaultVirtual, code);
  p->bootThunks.defaultDynamic = thunkToThunk(image->thunks.defaultDynamic, code);
  p->bootThunks.native = thunkToThunk(image->thunks.native, code);
  p->bootThunks.aioob = thunkToThunk(image->thunks.aioob, code);
  p->bootThunks.stackOverflow = thunkToThunk(image->thunks.stackOverflow, code);
  p->bootThunks.table = thunkToThunk(image->thunks.table, code);
}

void fixupVirtualThunks(MyThread* t, uint8_t* code)
{
  GcWordArray* a = compileRoots(t)->virtualThunks();
  for (unsigned i = 0; i < a->length(); i += 2) {
    if (a->body()[i]) {
      a->body()[i] += reinterpret_cast<uintptr_t>(code);
    }
  }
}

void boot(MyThread* t, BootImage* image, uint8_t* code)
{
  assertT(t, image->magic == BootImage::Magic);

  unsigned* bootClassTable = reinterpret_cast<unsigned*>(image + 1);
  unsigned* appClassTable = bootClassTable + image->bootClassCount;
  unsigned* stringTable = appClassTable + image->appClassCount;
  unsigned* callTable = stringTable + image->stringCount;

  uintptr_t* heapMap = reinterpret_cast<uintptr_t*>(
      padWord(reinterpret_cast<uintptr_t>(callTable + (image->callCount * 2))));

  unsigned heapMapSizeInWords
      = ceilingDivide(heapMapSize(image->heapSize), BytesPerWord);
  uintptr_t* heap = heapMap + heapMapSizeInWords;

  MyProcessor* p = static_cast<MyProcessor*>(t->m->processor);

  t->heapImage = p->heapImage = heap;

  if (false) {
    fprintf(stderr,
            "heap from %p to %p\n",
            heap,
            heap + ceilingDivide(image->heapSize, BytesPerWord));
  }

  t->codeImage = p->codeImage = code;
  p->codeImageSize = image->codeSize;

  if (false) {
    fprintf(stderr, "code from %p to %p\n", code, code + image->codeSize);
  }

  if (not image->initialized) {
    fixupHeap(t, heapMap, heapMapSizeInWords, heap);
  }

  t->m->heap->setImmortalHeap(heap, image->heapSize / BytesPerWord);

  t->m->types = reinterpret_cast<GcArray*>(bootObject(heap, image->types));

  t->m->roots = GcRoots::makeZeroed(t);

  roots(t)->setBootLoader(
      t, cast<GcClassLoader>(t, bootObject(heap, image->bootLoader)));
  roots(t)->setAppLoader(
      t, cast<GcClassLoader>(t, bootObject(heap, image->appLoader)));

  p->roots = GcCompileRoots::makeZeroed(t);

  compileRoots(t)->setMethodTree(
      t, cast<GcTreeNode>(t, bootObject(heap, image->methodTree)));
  compileRoots(t)->setMethodTreeSentinal(
      t, cast<GcTreeNode>(t, bootObject(heap, image->methodTreeSentinal)));

  compileRoots(t)->setVirtualThunks(
      t, cast<GcWordArray>(t, bootObject(heap, image->virtualThunks)));

  {
    GcHashMap* map
        = makeClassMap(t, bootClassTable, image->bootClassCount, heap);
    // sequence point, for gc (don't recombine statements)
    roots(t)->bootLoader()->setMap(t, map);
  }

  roots(t)->bootLoader()->as<GcSystemClassLoader>(t)->finder()
      = t->m->bootFinder;

  {
    GcHashMap* map = makeClassMap(t, appClassTable, image->appClassCount, heap);
    // sequence point, for gc (don't recombine statements)
    roots(t)->appLoader()->setMap(t, map);
  }

  roots(t)->appLoader()->as<GcSystemClassLoader>(t)->finder() = t->m->appFinder;

  {
    GcHashMap* map = makeStringMap(t, stringTable, image->stringCount, heap);
    // sequence point, for gc (don't recombine statements)
    roots(t)->setStringMap(t, map);
  }

  p->callTableSize = image->callCount;

  {
    GcArray* ct = makeCallTable(t,
                                heap,
                                callTable,
                                image->callCount,
                                reinterpret_cast<uintptr_t>(code));
    // sequence point, for gc (don't recombine statements)
    compileRoots(t)->setCallTable(t, ct);
  }

  {
    GcArray* staticTableArray = makeStaticTableArray(t,
                                                     bootClassTable,
                                                     image->bootClassCount,
                                                     appClassTable,
                                                     image->appClassCount,
                                                     heap);
    // sequence point, for gc (don't recombine statements)
    compileRoots(t)->setStaticTableArray(t, staticTableArray);
  }

  findThunks(t, image, code);

  if (image->initialized) {
    resetRuntimeState(t,
                      cast<GcHashMap>(t, roots(t)->bootLoader()->map()),
                      heap,
                      image->heapSize);

    resetRuntimeState(t,
                      cast<GcHashMap>(t, roots(t)->appLoader()->map()),
                      heap,
                      image->heapSize);

    for (unsigned i = 0; i < t->m->types->length(); ++i) {
      resetClassRuntimeState(
          t, type(t, static_cast<Gc::Type>(i)), heap, image->heapSize);
    }
  } else {
    fixupVirtualThunks(t, code);

    fixupMethods(
        t, cast<GcHashMap>(t, roots(t)->bootLoader()->map()), image, code);

    fixupMethods(
        t, cast<GcHashMap>(t, roots(t)->appLoader()->map()), image, code);
  }

  image->initialized = true;

  GcHashMap* map = makeHashMap(t, 0, 0);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setBootstrapClassMap(t, map);
}

intptr_t getThunk(MyThread* t, Thunk thunk)
{
  MyProcessor* p = processor(t);

  return reinterpret_cast<intptr_t>(p->thunks.table.start
                                    + (thunk * p->thunks.table.length));
}

#ifndef AVIAN_AOT_ONLY
void insertCallNode(MyThread* t, GcCallNode* node)
{
  GcArray* newArray = insertCallNode(
      t, compileRoots(t)->callTable(), &(processor(t)->callTableSize), node);
  // sequence point, for gc (don't recombine statements)
  compileRoots(t)->setCallTable(t, newArray);
}

BootImage::Thunk thunkToThunk(const MyProcessor::Thunk& thunk, uint8_t* base)
{
  return BootImage::Thunk(
      thunk.start - base, thunk.frameSavedOffset, thunk.length);
}

using avian::codegen::OperandInfo;
namespace lir = avian::codegen::lir;

void compileCall(MyThread* t, Context* c, ThunkIndex index, bool call = true)
{
  avian::codegen::Assembler* a = c->assembler;

  if (processor(t)->bootImage) {
    lir::Memory table(t->arch->thread(), TARGET_THREAD_THUNKTABLE);
    lir::RegisterPair scratch(t->arch->scratch());
    a->apply(lir::Move,
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::Memory, &table),
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &scratch));
    lir::Memory proc(scratch.low, index * TargetBytesPerWord);
    a->apply(lir::Move,
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::Memory, &proc),
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &scratch));
    a->apply(call ? lir::Call : lir::Jump,
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &scratch));
  } else {
    lir::Constant proc(new (&c->zone) avian::codegen::ResolvedPromise(
        reinterpret_cast<intptr_t>(t->thunkTable[index])));

    a->apply(call ? lir::LongCall : lir::LongJump,
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::Constant, &proc));
  }
}

void compileDefaultThunk(MyThread* t,
                         FixedAllocator* allocator,
                         MyProcessor::Thunk* thunk,
                         const char* name,
                         ThunkIndex thunkIndex,
                         bool hasTarget)
{
  Context context(t);
  avian::codegen::Assembler* a = context.assembler;

  if(hasTarget) {
    lir::RegisterPair class_(t->arch->virtualCallTarget());
    lir::Memory virtualCallTargetSrc(
        t->arch->stack(),
        (t->arch->frameFooterSize() + t->arch->frameReturnAddressSize())
        * TargetBytesPerWord);

    a->apply(lir::Move,
             OperandInfo(
                 TargetBytesPerWord, lir::Operand::Type::Memory, &virtualCallTargetSrc),
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &class_));

    lir::Memory virtualCallTargetDst(t->arch->thread(),
                                     TARGET_THREAD_VIRTUALCALLTARGET);

    a->apply(
        lir::Move,
        OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &class_),
        OperandInfo(
            TargetBytesPerWord, lir::Operand::Type::Memory, &virtualCallTargetDst));
  }

  lir::RegisterPair index(t->arch->virtualCallIndex());
  lir::Memory virtualCallIndex(t->arch->thread(),
                               TARGET_THREAD_VIRTUALCALLINDEX);

  a->apply(
      lir::Move,
      OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &index),
      OperandInfo(TargetBytesPerWord, lir::Operand::Type::Memory, &virtualCallIndex));

  a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

  thunk->frameSavedOffset = a->length();

  lir::RegisterPair thread(t->arch->thread());
  a->pushFrame(1, TargetBytesPerWord, lir::Operand::Type::RegisterPair, &thread);

  compileCall(t, &context, thunkIndex);

  a->popFrame(t->arch->alignFrameSize(1));

  lir::RegisterPair result(t->arch->returnLow());
  a->apply(lir::Jump,
           OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &result));

  thunk->length = a->endBlock(false)->resolve(0, 0);

  thunk->start = finish(
      t, allocator, a, name, thunk->length);
}

void compileThunks(MyThread* t, FixedAllocator* allocator)
{
  MyProcessor* p = processor(t);

  {
    Context context(t);
    avian::codegen::Assembler* a = context.assembler;

    a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

    p->thunks.default_.frameSavedOffset = a->length();

    lir::RegisterPair thread(t->arch->thread());
    a->pushFrame(1, TargetBytesPerWord, lir::Operand::Type::RegisterPair, &thread);

    compileCall(t, &context, compileMethodIndex);

    a->popFrame(t->arch->alignFrameSize(1));

    lir::RegisterPair result(t->arch->returnLow());
    a->apply(lir::Jump,
             OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &result));

    p->thunks.default_.length = a->endBlock(false)->resolve(0, 0);

    p->thunks.default_.start
        = finish(t, allocator, a, "default", p->thunks.default_.length);
  }

  compileDefaultThunk
    (t, allocator, &(p->thunks.defaultVirtual), "defaultVirtual",
     compileVirtualMethodIndex, true);

  compileDefaultThunk
    (t, allocator, &(p->thunks.defaultDynamic), "defaultDynamic",
     linkDynamicMethodIndex, false);

  {
    Context context(t);
    avian::codegen::Assembler* a = context.assembler;

    a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

    p->thunks.native.frameSavedOffset = a->length();

    lir::RegisterPair thread(t->arch->thread());
    a->pushFrame(1, TargetBytesPerWord, lir::Operand::Type::RegisterPair, &thread);

    compileCall(t, &context, invokeNativeIndex);

    a->popFrameAndUpdateStackAndReturn(t->arch->alignFrameSize(1),
                                       TARGET_THREAD_NEWSTACK);

    p->thunks.native.length = a->endBlock(false)->resolve(0, 0);

    p->thunks.native.start
        = finish(t, allocator, a, "native", p->thunks.native.length);
  }

  {
    Context context(t);
    avian::codegen::Assembler* a = context.assembler;

    a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

    p->thunks.aioob.frameSavedOffset = a->length();

    lir::RegisterPair thread(t->arch->thread());
    a->pushFrame(1, TargetBytesPerWord, lir::Operand::Type::RegisterPair, &thread);

    compileCall(t, &context, throwArrayIndexOutOfBoundsIndex);

    p->thunks.aioob.length = a->endBlock(false)->resolve(0, 0);

    p->thunks.aioob.start
        = finish(t, allocator, a, "aioob", p->thunks.aioob.length);
  }

  {
    Context context(t);
    avian::codegen::Assembler* a = context.assembler;

    a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

    p->thunks.stackOverflow.frameSavedOffset = a->length();

    lir::RegisterPair thread(t->arch->thread());
    a->pushFrame(1, TargetBytesPerWord, lir::Operand::Type::RegisterPair, &thread);

    compileCall(t, &context, throwStackOverflowIndex);

    p->thunks.stackOverflow.length = a->endBlock(false)->resolve(0, 0);

    p->thunks.stackOverflow.start = finish(
        t, allocator, a, "stackOverflow", p->thunks.stackOverflow.length);
  }

  {
    {
      Context context(t);
      avian::codegen::Assembler* a = context.assembler;

      a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);

      p->thunks.table.frameSavedOffset = a->length();

      compileCall(t, &context, dummyIndex, false);

      p->thunks.table.length = a->endBlock(false)->resolve(0, 0);

      p->thunks.table.start = static_cast<uint8_t*>(allocator->allocate(
          p->thunks.table.length * ThunkCount, TargetBytesPerWord));
    }

    uint8_t* start = p->thunks.table.start;

#define THUNK(s)                                                            \
  {                                                                         \
    Context context(t);                                                     \
    avian::codegen::Assembler* a = context.assembler;                       \
                                                                            \
    a->saveFrame(TARGET_THREAD_STACK, TARGET_THREAD_IP);                    \
                                                                            \
    p->thunks.table.frameSavedOffset = a->length();                         \
                                                                            \
    compileCall(t, &context, s##Index, false);                              \
                                                                            \
    expect(t, a->endBlock(false)->resolve(0, 0) <= p->thunks.table.length); \
                                                                            \
    a->setDestination(start);                                               \
    a->write();                                                             \
                                                                            \
    logCompile(t, start, p->thunks.table.length, 0, #s, 0);                 \
                                                                            \
    start += p->thunks.table.length;                                        \
  }
#include "thunks.cpp"
#undef THUNK
  }

  BootImage* image = p->bootImage;

  if (image) {
    uint8_t* imageBase = p->codeAllocator.memory.begin();

    image->thunks.default_ = thunkToThunk(p->thunks.default_, imageBase);
    image->thunks.defaultVirtual
        = thunkToThunk(p->thunks.defaultVirtual, imageBase);
    image->thunks.native = thunkToThunk(p->thunks.native, imageBase);
    image->thunks.aioob = thunkToThunk(p->thunks.aioob, imageBase);
    image->thunks.stackOverflow
        = thunkToThunk(p->thunks.stackOverflow, imageBase);
    image->thunks.table = thunkToThunk(p->thunks.table, imageBase);
  }
}

uintptr_t aioobThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.aioob.start);
}

uintptr_t stackOverflowThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.stackOverflow.start);
}
#endif // not AVIAN_AOT_ONLY

MyProcessor* processor(MyThread* t)
{
  return static_cast<MyProcessor*>(t->m->processor);
}

uintptr_t defaultThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.default_.start);
}

uintptr_t bootDefaultThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->bootThunks.default_.start);
}

uintptr_t defaultVirtualThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.defaultVirtual.start);
}

uintptr_t defaultDynamicThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.defaultDynamic.start);
}

uintptr_t nativeThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->thunks.native.start);
}

uintptr_t bootNativeThunk(MyThread* t)
{
  return reinterpret_cast<uintptr_t>(processor(t)->bootThunks.native.start);
}

bool unresolved(MyThread* t, uintptr_t methodAddress)
{
  return methodAddress == defaultThunk(t)
         or methodAddress == bootDefaultThunk(t);
}

uintptr_t compileVirtualThunk(MyThread* t,
                              unsigned index,
                              unsigned* size,
                              uintptr_t thunk,
                              const char* baseName)
{
  Context context(t);
  avian::codegen::Assembler* a = context.assembler;

  avian::codegen::ResolvedPromise indexPromise(index);
  lir::Constant indexConstant(&indexPromise);
  lir::RegisterPair indexRegister(t->arch->virtualCallIndex());
  a->apply(
      lir::Move,
      OperandInfo(TargetBytesPerWord, lir::Operand::Type::Constant, &indexConstant),
      OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &indexRegister));

  avian::codegen::ResolvedPromise promise(thunk);
  lir::Constant target(&promise);
  a->apply(lir::Jump,
           OperandInfo(TargetBytesPerWord, lir::Operand::Type::Constant, &target));

  *size = a->endBlock(false)->resolve(0, 0);

  uint8_t* start = static_cast<uint8_t*>(
      codeAllocator(t)->allocate(*size, TargetBytesPerWord));

  a->setDestination(start);
  a->write();

  const size_t virtualThunkBaseNameLength = strlen(baseName);
  const size_t maxIntStringLength = 10;

  THREAD_RUNTIME_ARRAY(t,
                       char,
                       virtualThunkName,
                       virtualThunkBaseNameLength + maxIntStringLength);

  sprintf(RUNTIME_ARRAY_BODY(virtualThunkName),
          "%s%d",
          baseName,
          index);

  logCompile(t, start, *size, 0, RUNTIME_ARRAY_BODY(virtualThunkName), 0);

  return reinterpret_cast<uintptr_t>(start);
}

uintptr_t virtualThunk(MyThread* t, unsigned index)
{
  ACQUIRE(t, t->m->classLock);

  GcWordArray* oldArray = compileRoots(t)->virtualThunks();
  if (oldArray == 0 or oldArray->length() <= index * 2) {
    GcWordArray* newArray = makeWordArray(t, nextPowerOfTwo((index + 1) * 2));
    if (compileRoots(t)->virtualThunks()) {
      memcpy(newArray->body().begin(),
             oldArray->body().begin(),
             oldArray->length() * BytesPerWord);
    }
    compileRoots(t)->setVirtualThunks(t, newArray);
    oldArray = newArray;
  }

  if (oldArray->body()[index * 2] == 0) {
    unsigned size;
    uintptr_t thunk = compileVirtualThunk(t, index, &size, defaultVirtualThunk(t), "virtualThunk");
    oldArray->body()[index * 2] = thunk;
    oldArray->body()[(index * 2) + 1] = size;
  }

  return oldArray->body()[index * 2];
}

void compile(MyThread* t,
             FixedAllocator* allocator UNUSED,
             BootContext* bootContext,
             GcMethod* method)
{
  PROTECT(t, method);

  if (bootContext == 0 and method->flags() & ACC_STATIC) {
    initClass(t, method->class_());
  }

  if (methodAddress(t, method) != defaultThunk(t)) {
    return;
  }

  assertT(t, (method->flags() & ACC_NATIVE) == 0);

#ifdef AVIAN_AOT_ONLY
  abort(t);
#else

  // We must avoid acquiring any locks until after the first pass of
  // compilation, since this pass may trigger classloading operations
  // involving application classloaders and thus the potential for
  // deadlock.  To make this safe, we use a private clone of the
  // method so that we won't be confused if another thread updates the
  // original while we're working.

  GcMethod* clone = methodClone(t, method);

  loadMemoryBarrier();

  if (methodAddress(t, method) != defaultThunk(t)) {
    return;
  }

  PROTECT(t, clone);

  Context context(t, bootContext, clone);
  compile(t, &context);

  {
    GcExceptionHandlerTable* ehTable = cast<GcExceptionHandlerTable>(
        t, clone->code()->exceptionHandlerTable());

    if (ehTable) {
      PROTECT(t, ehTable);

      // resolve all exception handler catch types before we acquire
      // the class lock:
      for (unsigned i = 0; i < ehTable->length(); ++i) {
        uint64_t handler = ehTable->body()[i];
        if (exceptionHandlerCatchType(handler)) {
          resolveClassInPool(t, clone, exceptionHandlerCatchType(handler) - 1);
        }
      }
    }
  }

  ACQUIRE(t, t->m->classLock);

  if (methodAddress(t, method) != defaultThunk(t)) {
    return;
  }

  finish(t, allocator, &context);

  if (DebugMethodTree) {
    fprintf(stderr,
            "insert method at %p\n",
            reinterpret_cast<void*>(methodCompiled(t, clone)));
  }

  // We can't update the MethodCode field on the original method
  // before it is placed into the method tree, since another thread
  // might call the method, from which stack unwinding would fail
  // (since there is not yet an entry in the method tree).  However,
  // we can't insert the original method into the tree before updating
  // the MethodCode field on it since we rely on that field to
  // determine its position in the tree.  Therefore, we insert the
  // clone in its place.  Later, we'll replace the clone with the
  // original to save memory.

  GcTreeNode* newTree = treeInsert(t,
                                   &(context.zone),
                                   compileRoots(t)->methodTree(),
                                   methodCompiled(t, clone),
                                   clone,
                                   compileRoots(t)->methodTreeSentinal(),
                                   compareIpToMethodBounds);
  // sequence point, for gc (don't recombine statements)
  compileRoots(t)->setMethodTree(t, newTree);

  storeStoreMemoryBarrier();

  method->setCode(t, clone->code());

  if (methodVirtual(t, method)) {
    method->class_()->vtable()[method->offset()]
        = reinterpret_cast<void*>(methodCompiled(t, clone));
  }

  // we've compiled the method and inserted it into the tree without
  // error, so we ensure that the executable area not be deallocated
  // when we dispose of the context:
  context.executableAllocator = 0;

  treeUpdate(t,
             compileRoots(t)->methodTree(),
             methodCompiled(t, clone),
             method,
             compileRoots(t)->methodTreeSentinal(),
             compareIpToMethodBounds);
#endif // not AVIAN_AOT_ONLY
}

GcCompileRoots* compileRoots(Thread* t)
{
  return processor(static_cast<MyThread*>(t))->roots;
}

avian::util::FixedAllocator* codeAllocator(MyThread* t)
{
  return &(processor(t)->codeAllocator);
}

Allocator* allocator(MyThread* t)
{
  return processor(t)->allocator;
}

}  // namespace local

}  // namespace

namespace vm {

Processor* makeProcessor(System* system,
                         Allocator* allocator,
                         const char* crashDumpDirectory,
                         bool useNativeFeatures)
{
  return new (allocator->allocate(sizeof(local::MyProcessor)))
      local::MyProcessor(
          system, allocator, crashDumpDirectory, useNativeFeatures);
}

}  // namespace vm
