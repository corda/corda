/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"
#include "util.h"
#include "vector.h"
#include "process.h"
#include "assembler.h"
#include "compiler.h"
#include "x86.h"

using namespace vm;

extern "C" uint64_t
vmInvoke(void* thread, void* function, void* stack, unsigned stackSize,
         unsigned returnType);

extern "C" void
vmCall();

namespace {

const bool Verbose = false;
const bool DebugNatives = false;
const bool DebugCallTable = false;
const bool DebugMethodTree = false;
const bool DebugFrameMaps = false;

const bool CheckArrayBounds = true;

class MyThread: public Thread {
 public:
  class CallTrace {
   public:
    CallTrace(MyThread* t):
      t(t),
      base(t->base),
      stack(t->stack),
      nativeMethod(0),
      next(t->trace)
    {
      t->trace = this;
      t->base = 0;
      t->stack = 0;
    }

    ~CallTrace() {
      t->stack = stack;
      t->base = base;
      t->trace = next;
    }

    MyThread* t;
    void* ip;
    void* base;
    void* stack;
    object nativeMethod;
    CallTrace* next;
  };

  MyThread(Machine* m, object javaThread, Thread* parent):
    Thread(m, javaThread, parent),
    ip(0),
    base(0),
    stack(0),
    trace(0),
    reference(0)
  { }

  void* ip;
  void* base;
  void* stack;
  CallTrace* trace;
  Reference* reference;
};

object
resolveThisPointer(MyThread* t, void* stack, object method)
{
  return reinterpret_cast<object*>(stack)[methodParameterFootprint(t, method)];
}

object
resolveTarget(MyThread* t, void* stack, object method)
{
  object class_ = objectClass(t, resolveThisPointer(t, stack, method));

  if (classVmFlags(t, class_) & BootstrapFlag) {
    PROTECT(t, method);
    PROTECT(t, class_);

    resolveClass(t, className(t, class_));
    if (UNLIKELY(t->exception)) return 0;
  }

  if (classFlags(t, methodClass(t, method)) & ACC_INTERFACE) {
    return findInterfaceMethod(t, method, class_);
  } else {
    return findMethod(t, method, class_);
  }
}

object&
methodTree(MyThread* t);

object
methodTreeSentinal(MyThread* t);

intptr_t
compareIpToMethodBounds(Thread* t, intptr_t ip, object method)
{
  intptr_t start = reinterpret_cast<intptr_t>
    (&singletonValue(t, methodCompiled(t, method), 0));

  if (DebugMethodTree) {
    fprintf(stderr, "find 0x%"LX" in (0x%"LX",0x%"LX")\n", ip, start,
            start + (singletonCount(t, methodCompiled(t, method))
                     * BytesPerWord));
  }

  if (ip < start) {
    return -1;
  } else if (ip < start + static_cast<intptr_t>
             (singletonCount(t, methodCompiled(t, method))
              * BytesPerWord))
  {
    return 0;
  } else {
    return 1;
  }
}

object
methodForIp(MyThread* t, void* ip)
{
  if (DebugMethodTree) {
    fprintf(stderr, "query for method containing %p\n", ip);
  }

  return treeQuery(t, methodTree(t), reinterpret_cast<intptr_t>(ip),
                   methodTreeSentinal(t), compareIpToMethodBounds);
}

class MyStackWalker: public Processor::StackWalker {
 public:
  enum State {
    Start,
    Next,
    Method,
    NativeMethod,
    Finish
  };

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(MyStackWalker* walker):
      Protector(walker->t), walker(walker)
    { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(walker->method_));
    }

    MyStackWalker* walker;
  };

  MyStackWalker(MyThread* t):
    t(t),
    state(Start),
    ip_(t->ip),
    base(t->base),
    stack(t->stack),
    trace(t->trace),
    method_(0),
    protector(this)
  { }

  MyStackWalker(MyStackWalker* w):
    t(w->t),
    state(w->state),
    ip_(w->ip_),
    base(w->base),
    stack(w->stack),
    trace(w->trace),
    method_(w->method_),
    protector(this)
  { }

  virtual void walk(Processor::StackVisitor* v) {
    for (MyStackWalker it(this); it.valid();) {
      MyStackWalker walker(it);
      if (not v->visit(&walker)) {
        break;
      }
      it.next();
    }
  }
    
  bool valid() {
    while (true) {
//       fprintf(stderr, "state: %d\n", state);
      switch (state) {
      case Start:
        if (ip_ == 0 and stack) {
          ip_ = *static_cast<void**>(stack);
        }

        if (trace and trace->nativeMethod) {
          method_ = trace->nativeMethod;
          state = NativeMethod;
        } else if (ip_) {
          state = Next;
        } else {
          state = Finish;
        }
        break;

      case Next:
        if (stack) {
          method_ = methodForIp(t, ip_);
          if (method_) {
            state = Method;
          } else if (trace) {
            base = trace->base;
            stack = static_cast<void**>(trace->stack);
            ip_ = (stack ? *static_cast<void**>(stack) : 0);

            if (trace->nativeMethod) {
              method_ = trace->nativeMethod;
              state = NativeMethod;
            } else {
              trace = trace->next;
              state = Next;
            }
          } else {
            state = Finish;
          }
        } else {
          state = Finish;
        }
        break;

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
    
  void next() {
    switch (state) {
    case Method:
      stack = static_cast<void**>(base) + 1;
      ip_ = (stack ? *static_cast<void**>(stack) : 0);
      base = *static_cast<void**>(base);
      state = Next;
      break;

    case NativeMethod:
      trace = trace->next;
      state = Next;
      break;
   
    default:
      abort(t);
    }
  }

  virtual object method() {
    switch (state) {
    case Method:
      return method_;
        
    case NativeMethod:
      return trace->nativeMethod;

    default:
      abort(t);
    }
  }

  virtual int ip() {
    switch (state) {
    case Method:
      return reinterpret_cast<intptr_t>(ip_) - reinterpret_cast<intptr_t>
        (&singletonValue(t, methodCompiled(t, method_), 0));
        
    case NativeMethod:
      return 0;

    default:
      abort(t);
    }
  }

  virtual unsigned count() {
    unsigned count = 0;

    for (MyStackWalker walker(this); walker.valid();) {
      walker.next();
      ++ count;
    }
    
    return count;
  }

  MyThread* t;
  State state;
  void* ip_;
  void* base;
  void* stack;
  MyThread::CallTrace* trace;
  object method_;
  MyProtector protector;
};

int
localOffset(MyThread* t, int v, object method)
{
  int parameterFootprint = methodParameterFootprint(t, method) * BytesPerWord;

  v *= BytesPerWord;
  if (v < parameterFootprint) {
    return (parameterFootprint - v - BytesPerWord) + (BytesPerWord * 2);
  } else {
    return -(v + BytesPerWord - parameterFootprint);
  }
}

inline object*
localObject(MyThread* t, void* base, object method, unsigned index)
{
  return reinterpret_cast<object*>
    (static_cast<uint8_t*>(base) + localOffset(t, index, method));
}

class PoolElement {
 public:
  PoolElement(object value, Promise* address, PoolElement* next):
    value(value), address(address), next(next)
  { }

  object value;
  Promise* address;
  PoolElement* next;
};

class Context;

class TraceElement: public TraceHandler {
 public:
  TraceElement(Context* context, object target,
               bool virtualCall, TraceElement* next):
    context(context),
    address(0),
    target(target),
    virtualCall(virtualCall),
    next(next)
  { }

  virtual void handleTrace(Promise* address) {
    if (this->address == 0) {
      this->address = address;
    }
  }

  Context* context;
  Promise* address;
  object target;
  bool virtualCall;
  TraceElement* next;
  uintptr_t map[0];
};

enum Event {
  PushEvent,
  PopEvent,
  IpEvent,
  MarkEvent,
  ClearEvent,
  TraceEvent
};

unsigned
localSize(MyThread* t, object method)
{
  unsigned size = codeMaxLocals(t, methodCode(t, method));
  if ((methodFlags(t, method) & (ACC_SYNCHRONIZED | ACC_STATIC))
      == ACC_SYNCHRONIZED)
  {
    ++ size;
  }
  return size;
}

unsigned
frameSize(MyThread* t, object method)
{
  return localSize(t, method) + codeMaxStack(t, methodCode(t, method));
}

unsigned
frameMapSizeInWords(MyThread* t, object method)
{
  return ceiling(frameSize(t, method), BitsPerWord) * BytesPerWord;
}

uint16_t*
makeVisitTable(MyThread* t, Zone* zone, object method)
{
  unsigned size = codeLength(t, methodCode(t, method)) * 2;
  uint16_t* table = static_cast<uint16_t*>(zone->allocate(size));
  memset(table, 0, size);
  return table;
}

uintptr_t*
makeRootTable(MyThread* t, Zone* zone, object method)
{
  unsigned size = frameMapSizeInWords(t, method)
    * codeLength(t, methodCode(t, method))
    * BytesPerWord;
  uintptr_t* table = static_cast<uintptr_t*>(zone->allocate(size));
  memset(table, 0xFF, size);
  return table;
}

enum Thunk {
#define THUNK(s) s##Thunk,

#include "thunks.cpp"

#undef THUNK
};

const unsigned ThunkCount = gcIfNecessaryThunk + 1;

intptr_t
getThunk(MyThread* t, Thunk thunk);

class Context {
 public:
  class MyProtector: public Thread::Protector {
   public:
    MyProtector(Context* c): Protector(c->thread), c(c) { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(c->method));

      for (PoolElement* p = c->objectPool; p; p = p->next) {
        v->visit(&(p->value));
      }

      for (TraceElement* p = c->traceLog; p; p = p->next) {
        v->visit(&(p->target));
      }
    }

    Context* c;
  };

  class MyClient: public Compiler::Client {
   public:
    MyClient(MyThread* t): t(t) { }

    virtual intptr_t getThunk(UnaryOperation, unsigned) {
      abort(t);
    }

    virtual intptr_t getThunk(BinaryOperation op, unsigned size) {
      switch (op) {
      case Divide:
        if (size == 8) {
          return ::getThunk(t, divideLongThunk);
        }
        break;

      case Remainder:
        if (size == 8) {
          return ::getThunk(t, moduloLongThunk);
        }
        break;

      default: break;
      }

      abort(t);
    }

    MyThread* t;
  };

  Context(MyThread* t, object method):
    thread(t),
    zone(t->m->system, t->m->heap, 16 * 1024),
    assembler(makeAssembler(t->m->system, t->m->heap, &zone)),
    client(t),
    compiler(makeCompiler(t->m->system, assembler, &zone, &client)),
    method(method),
    objectPool(0),
    traceLog(0),
    traceLogCount(0),
    visitTable(makeVisitTable(t, &zone, method)),
    rootTable(makeRootTable(t, &zone, method)),
    eventLog(t->m->system, t->m->heap, 1024),
    protector(this)
  { }

  Context(MyThread* t):
    thread(t),
    zone(t->m->system, t->m->heap, LikelyPageSizeInBytes),
    assembler(makeAssembler(t->m->system, t->m->heap, &zone)),
    client(t),
    compiler(0),
    method(0),
    objectPool(0),
    traceLog(0),
    traceLogCount(0),
    visitTable(0),
    rootTable(0),
    eventLog(t->m->system, t->m->heap, 0),
    protector(this)
  { }

  ~Context() {
    if (compiler) compiler->dispose();
    assembler->dispose();
  }

  MyThread* thread;
  Zone zone;
  Assembler* assembler;
  MyClient client;
  Compiler* compiler;
  object method;
  PoolElement* objectPool;
  TraceElement* traceLog;
  unsigned traceLogCount;
  uint16_t* visitTable;
  uintptr_t* rootTable;
  bool dirtyRoots;
  Vector eventLog;
  MyProtector protector;
};

class Frame {
 public:
  enum StackType {
    Integer,
    Long,
    Object
  };

  Frame(Context* context, uint8_t* stackMap):
    context(context),
    t(context->thread),
    c(context->compiler),
    stackMap(stackMap),
    ip(0),
    sp(localSize()),
    level(0)
  {
    memset(stackMap, 0, codeMaxStack(t, methodCode(t, context->method)));
  }

  Frame(Frame* f, uint8_t* stackMap):
    context(f->context),
    t(context->thread),
    c(context->compiler),
    stackMap(stackMap),
    ip(f->ip),
    sp(f->sp),
    level(f->level + 1)
  {
    c->pushState();

    memcpy(stackMap, f->stackMap, codeMaxStack
           (t, methodCode(t, context->method)));

    if (level > 1) {
      context->eventLog.append(PushEvent);
    }
  }

  ~Frame() {
    if (t->exception == 0) {
      if (level > 0) {
        c->saveStack();
        c->popState();
        c->resetStack();
      }

      if (level > 1) {
        context->eventLog.append(PopEvent);      
      }
    }
  }

  Compiler::Operand* append(object o) {
    Promise* p = c->poolAppend(0);
    context->objectPool = new
      (context->zone.allocate(sizeof(PoolElement)))
      PoolElement(o, p, context->objectPool);
    return c->address(p);
  }

  unsigned localSize() {
    return ::localSize(t, context->method);
  }

  unsigned stackSize() {
    return codeMaxStack(t, methodCode(t, context->method));
  }

  unsigned frameSize() {
    return localSize() + stackSize();
  }

  void set(unsigned index, uint8_t type) {
    assert(t, index < frameSize());

    if (type == Object) {
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

  uint8_t get(unsigned index) {
    assert(t, index < frameSize());
    int si = index - localSize();
    assert(t, si >= 0);
    return stackMap[si];
  }

  void pushedInt() {
    assert(t, sp + 1 <= frameSize());
    set(sp++, Integer);
  }

  void pushedLong() {
    assert(t, sp + 2 <= frameSize());
    set(sp++, Long);
    set(sp++, Long);
  }

  void pushedObject() {
    assert(t, sp + 1 <= frameSize());
    set(sp++, Object);
  }

  void popped(unsigned count) {
    assert(t, sp >= count);
    assert(t, sp - count >= localSize());
    while (count) {
      set(--sp, Integer);
      -- count;
    }
  }
  
  void poppedInt() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) == Integer);
    -- sp;
  }
  
  void poppedLong() {
    assert(t, sp >= 1);
    assert(t, sp - 2 >= localSize());
    assert(t, get(sp - 1) == Long);
    assert(t, get(sp - 2) == Long);
    sp -= 2;
  }
  
  void poppedObject() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) == Object);
    set(--sp, Integer);
  }

  void storedInt(unsigned index) {
    assert(t, index < localSize());
    set(index, Integer);
  }

  void storedLong(unsigned index) {
    assert(t, index + 1 < localSize());
    set(index, Long);
    set(index + 1, Long);
  }

  void storedObject(unsigned index) {
    assert(t, index < localSize());
    set(index, Object);
  }

  void dupped() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 1 >= localSize());
    set(sp++, get(sp - 1));
  }

  void duppedX1() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 2 >= localSize());

    uint8_t b2 = get(sp - 2);
    uint8_t b1 = get(sp - 1);

    set(sp - 1, b2);
    set(sp - 2, b1);
    set(sp    , b1);

    ++ sp;
  }

  void duppedX2() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 3 >= localSize());

    uint8_t b3 = get(sp - 3);
    uint8_t b2 = get(sp - 2);
    uint8_t b1 = get(sp - 1);

    set(sp - 2, b3);
    set(sp - 1, b2);
    set(sp - 3, b1);
    set(sp    , b1);

    ++ sp;
  }

  void dupped2() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 2 >= localSize());

    uint8_t b2 = get(sp - 2);
    uint8_t b1 = get(sp - 1);

    set(sp, b2);
    set(sp + 1, b1);

    sp += 2;
  }

  void dupped2X1() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 3 >= localSize());

    uint8_t b3 = get(sp - 3);
    uint8_t b2 = get(sp - 2);
    uint8_t b1 = get(sp - 1);

    set(sp - 1, b3);
    set(sp - 3, b2);
    set(sp    , b2);
    set(sp - 2, b1);
    set(sp + 1, b1);

    sp += 2;
  }

  void dupped2X2() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 4 >= localSize());

    uint8_t b4 = get(sp - 4);
    uint8_t b3 = get(sp - 3);
    uint8_t b2 = get(sp - 2);
    uint8_t b1 = get(sp - 1);

    set(sp - 2, b4);
    set(sp - 1, b3);
    set(sp - 4, b2);
    set(sp    , b2);
    set(sp - 3, b1);
    set(sp + 1, b1);

    sp += 2;
  }

  void swapped() {
    assert(t, sp - 2 >= localSize());

    uint8_t saved = get(sp - 1);

    set(sp - 1, get(sp - 2));
    set(sp - 2, saved);
  }

  Compiler::Operand* machineIp(unsigned logicalIp) {
    return c->promiseConstant(c->machineIp(logicalIp));
  }

  void visitLogicalIp(unsigned ip) {
    c->visitLogicalIp(ip);

    context->eventLog.append(IpEvent);
    context->eventLog.append2(ip);
  }

  void startLogicalIp(unsigned ip) {
    c->startLogicalIp(ip);

    context->eventLog.append(IpEvent);
    context->eventLog.append2(ip);

    this->ip = ip;
  }

  void pushInt(Compiler::Operand* o) {
    c->push(4, o);
    pushedInt();
  }

  void pushAddress(Compiler::Operand* o) {
    c->push(BytesPerWord, o);
    pushedInt();
  }

  void pushObject(Compiler::Operand* o) {
    c->push(BytesPerWord, o);
    pushedObject();
  }

  void pushObject() {
    c->pushed(1);
    pushedObject();
  }

  void pushLongQuiet(Compiler::Operand* o) {
    if (BytesPerWord == 8) {
      c->push(8);
    }
    c->push(8, o);
  }

  void pushLong(Compiler::Operand* o) {
    pushLongQuiet(o);
    pushedLong();
  }

  void pop(unsigned count) {
    popped(count);
    c->popped(count);
  }

  Compiler::Operand* popInt() {
    poppedInt();
    return c->pop(4);
  }

  Compiler::Operand* popLongQuiet() {
    Compiler::Operand* r = c->pop(8);
    if (BytesPerWord == 8) {
      c->pop(8);
    }
    return r;
  }

  Compiler::Operand* peekLong(unsigned index) {
    return c->peek(8, index);
  }

  Compiler::Operand* popLong() {
    poppedLong();
    return popLongQuiet();
  }

  Compiler::Operand* popObject() {
    poppedObject();
    return c->pop(BytesPerWord);
  }

  void loadInt(unsigned index) {
    assert(t, index < localSize());
    pushInt(c->loadLocal(BytesPerWord, index));
  }

  void loadLong(unsigned index) {
    assert(t, index < static_cast<unsigned>(localSize() - 1));
    pushLong(c->loadLocal(8, index + 1));
  }

  void loadObject(unsigned index) {
    assert(t, index < localSize());
    pushObject(c->loadLocal(BytesPerWord, index));
  }

  void storeInt(unsigned index) {
    c->storeLocal(BytesPerWord, popInt(), index);
    storedInt(index);
  }

  void storeLong(unsigned index) {
    c->storeLocal(8, popLong(), index + 1);
    storedLong(index);
  }

  void storeObject(unsigned index) {
    c->storeLocal(BytesPerWord, popObject(), index);
    storedObject(index);
  }

  void storeObjectOrAddress(unsigned index) {
    c->storeLocal(BytesPerWord, c->pop(BytesPerWord), index);

    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    if (get(sp - 1) == Object) {
      storedObject(index);
    } else {
      storedInt(index);
    }

    popped(1);
  }

  void dup() {
    c->push(BytesPerWord, c->peek(BytesPerWord, 0));

    dupped();
  }

  void dupX1() {
    Compiler::Operand* s0 = c->pop(BytesPerWord);
    Compiler::Operand* s1 = c->pop(BytesPerWord);

    c->push(BytesPerWord, s0);
    c->push(BytesPerWord, s1);
    c->push(BytesPerWord, s0);

    duppedX1();
  }

  void dupX2() {
    Compiler::Operand* s0 = c->pop(BytesPerWord);

    if (get(sp - 2) == Long) {
      Compiler::Operand* s1 = popLongQuiet();

      c->push(BytesPerWord, s0);
      pushLongQuiet(s1);
      c->push(BytesPerWord, s0);
    } else {
      Compiler::Operand* s1 = c->pop(BytesPerWord);
      Compiler::Operand* s2 = c->pop(BytesPerWord);

      c->push(BytesPerWord, s0);
      c->push(BytesPerWord, s2);
      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
    }

    duppedX2();
  }

  void dup2() {
    if (get(sp - 1) == Long) {
      pushLongQuiet(peekLong(0));
    } else {
      Compiler::Operand* s0 = c->pop(BytesPerWord);
      Compiler::Operand* s1 = c->pop(BytesPerWord);

      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
    }

    dupped2();
  }

  void dup2X1() {
    if (get(sp - 1) == Long) {
      Compiler::Operand* s0 = popLongQuiet();
      Compiler::Operand* s1 = c->pop(BytesPerWord);

      pushLongQuiet(s0);
      c->push(BytesPerWord, s1);
      pushLongQuiet(s0);
    } else {
      Compiler::Operand* s0 = c->pop(BytesPerWord);
      Compiler::Operand* s1 = c->pop(BytesPerWord);
      Compiler::Operand* s2 = c->pop(BytesPerWord);

      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
      c->push(BytesPerWord, s2);
      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
    }

    dupped2X1();
  }

  void dup2X2() {
    if (get(sp - 1) == Long) {
      Compiler::Operand* s0 = popLongQuiet();

      if (get(sp - 3) == Long) {
        Compiler::Operand* s1 = popLongQuiet();

        pushLongQuiet(s0);
        pushLongQuiet(s1);
        pushLongQuiet(s0);
      } else {
        Compiler::Operand* s1 = c->pop(BytesPerWord);
        Compiler::Operand* s2 = c->pop(BytesPerWord);

        pushLongQuiet(s0);
        c->push(BytesPerWord, s2);
        c->push(BytesPerWord, s1);
        pushLongQuiet(s0);
      }
    } else {
      Compiler::Operand* s0 = c->pop(BytesPerWord);
      Compiler::Operand* s1 = c->pop(BytesPerWord);
      Compiler::Operand* s2 = c->pop(BytesPerWord);
      Compiler::Operand* s3 = c->pop(BytesPerWord);

      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
      c->push(BytesPerWord, s3);
      c->push(BytesPerWord, s2);
      c->push(BytesPerWord, s1);
      c->push(BytesPerWord, s0);
    }

    dupped2X2();
  }

  void swap() {
    Compiler::Operand* s0 = c->pop(BytesPerWord);
    Compiler::Operand* s1 = c->pop(BytesPerWord);

    c->push(BytesPerWord, s0);
    c->push(BytesPerWord, s1);

    swapped();
  }

  TraceElement* trace(object target, bool virtualCall) {
    unsigned mapSize = frameMapSizeInWords(t, context->method);

    TraceElement* e = context->traceLog = new
      (context->zone.allocate(sizeof(TraceElement) + (mapSize * BytesPerWord)))
      TraceElement(context, target, virtualCall, context->traceLog);

    ++ context->traceLogCount;

    context->eventLog.append(TraceEvent);
    context->eventLog.appendAddress(e);

    return e;
  }
  
  Context* context;
  MyThread* t;
  Compiler* c;
  uint8_t* stackMap;
  unsigned ip;
  unsigned sp;
  unsigned level;
};

unsigned
savedTargetIndex(MyThread* t, object method)
{
  return codeMaxLocals(t, methodCode(t, method));
}

object
findCallNode(MyThread* t, void* address);

void
insertCallNode(MyThread* t, object node);

void*
findExceptionHandler(Thread* t, object method, void* ip)
{
  object table = codeExceptionHandlerTable(t, methodCode(t, method));
  if (table) {
    object index = arrayBody(t, table, 0);
      
    uint8_t* compiled = reinterpret_cast<uint8_t*>
      (&singletonValue(t, methodCompiled(t, method), 0));

    for (unsigned i = 0; i < arrayLength(t, table) - 1; ++i) {
      unsigned start = intArrayBody(t, index, i * 3);
      unsigned end = intArrayBody(t, index, (i * 3) + 1);
      unsigned key = difference(ip, compiled) - 1;

      if (key >= start and key < end) {
        object catchType = arrayBody(t, table, i + 1);

        if (catchType == 0 or instanceOf(t, catchType, t->exception)) {
          return compiled + intArrayBody(t, index, (i * 3) + 2);
        }
      }
    }
  }

  return 0;
}

void
findUnwindTarget(MyThread* t, void** targetIp, void** targetBase,
                 void** targetStack)
{
  void* ip = t->ip;
  void* base = t->base;
  void** stack = static_cast<void**>(t->stack);
  if (ip == 0) {
    ip = *stack;
  }

  *targetIp = 0;
  while (*targetIp == 0) {
    object method = methodForIp(t, ip);
    if (method) {
      PROTECT(t, method);

      void* handler = findExceptionHandler(t, method, ip);

      if (handler) {
        unsigned parameterFootprint = methodParameterFootprint(t, method);
        unsigned localFootprint = localSize(t, method);

        stack = static_cast<void**>(base)
          - (localFootprint - parameterFootprint);

        *(--stack) = t->exception;
        t->exception = 0;

        *targetIp = handler;
        *targetBase = base;
        *targetStack = stack;
      } else {
        if (methodFlags(t, method) & ACC_SYNCHRONIZED) {
          object lock;
          if (methodFlags(t, method) & ACC_STATIC) {
            lock = methodClass(t, method);
          } else {
            lock = *localObject(t, base, method, savedTargetIndex(t, method));
          }
    
          release(t, lock);
        }

        stack = static_cast<void**>(base) + 1;
        ip = *stack;
        base = *static_cast<void**>(base);
      }
    } else {
      *targetIp = ip;
      *targetBase = base;
      *targetStack = stack + 1;
    }
  }
}

void NO_RETURN
unwind(MyThread* t)
{
  void* ip;
  void* base;
  void* stack;
  findUnwindTarget(t, &ip, &base, &stack);
  vmJump(ip, base, stack, t);
}

void FORCE_ALIGN
tryInitClass(MyThread* t, object class_)
{
  initClass(t, class_);
  if (UNLIKELY(t->exception)) unwind(t);
}

void* FORCE_ALIGN
findInterfaceMethodFromInstance(MyThread* t, object method, object instance)
{
  if (instance) {
    return &singletonValue
      (t, methodCompiled
       (t, findInterfaceMethod(t, method, objectClass(t, instance))), 0);
  } else {
    t->exception = makeNullPointerException(t);
    unwind(t);
  }
}

intptr_t FORCE_ALIGN
compareDoublesG(uint64_t bi, uint64_t ai)
{
  double a = bitsToDouble(ai);
  double b = bitsToDouble(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

intptr_t FORCE_ALIGN
compareDoublesL(uint64_t bi, uint64_t ai)
{
  double a = bitsToDouble(ai);
  double b = bitsToDouble(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

intptr_t FORCE_ALIGN
compareFloatsG(uint32_t bi, uint32_t ai)
{
  float a = bitsToFloat(ai);
  float b = bitsToFloat(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return 1;
  }
}

intptr_t FORCE_ALIGN
compareFloatsL(uint32_t bi, uint32_t ai)
{
  float a = bitsToFloat(ai);
  float b = bitsToFloat(bi);
  
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

uint64_t FORCE_ALIGN
addDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) + bitsToDouble(b));
}

uint64_t FORCE_ALIGN
subtractDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) - bitsToDouble(b));
}

uint64_t FORCE_ALIGN
multiplyDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) * bitsToDouble(b));
}

uint64_t FORCE_ALIGN
divideDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) / bitsToDouble(b));
}

uint64_t FORCE_ALIGN
moduloDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(fmod(bitsToDouble(a), bitsToDouble(b)));
}

uint64_t FORCE_ALIGN
negateDouble(uint64_t a)
{
  return doubleToBits(- bitsToDouble(a));
}

uint32_t FORCE_ALIGN
doubleToFloat(int64_t a)
{
  return floatToBits(static_cast<float>(bitsToDouble(a)));
}

int32_t FORCE_ALIGN
doubleToInt(int64_t a)
{
  return static_cast<int32_t>(bitsToDouble(a));
}

int64_t FORCE_ALIGN
doubleToLong(int64_t a)
{
  return static_cast<int64_t>(bitsToDouble(a));
}

uint32_t FORCE_ALIGN
addFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) + bitsToFloat(b));
}

uint32_t FORCE_ALIGN
subtractFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) - bitsToFloat(b));
}

uint32_t FORCE_ALIGN
multiplyFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) * bitsToFloat(b));
}

uint32_t FORCE_ALIGN
divideFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) / bitsToFloat(b));
}

uint32_t FORCE_ALIGN
moduloFloat(uint32_t b, uint32_t a)
{
  return floatToBits(fmod(bitsToFloat(a), bitsToFloat(b)));
}

uint32_t FORCE_ALIGN
negateFloat(uint32_t a)
{
  return floatToBits(- bitsToFloat(a));
}

int64_t FORCE_ALIGN
divideLong(int64_t b, int64_t a)
{
  return a / b;
}

int64_t FORCE_ALIGN
moduloLong(int64_t b, int64_t a)
{
  return a % b;
}

uint64_t FORCE_ALIGN
floatToDouble(int32_t a)
{
  return doubleToBits(static_cast<double>(bitsToFloat(a)));
}

int32_t FORCE_ALIGN
floatToInt(int32_t a)
{
  return static_cast<int32_t>(bitsToFloat(a));
}

int64_t FORCE_ALIGN
floatToLong(int32_t a)
{
  return static_cast<int64_t>(bitsToFloat(a));
}

uint64_t FORCE_ALIGN
intToDouble(int32_t a)
{
  return doubleToBits(static_cast<double>(a));
}

uint32_t FORCE_ALIGN
intToFloat(int32_t a)
{
  return floatToBits(static_cast<float>(a));
}

uint64_t FORCE_ALIGN
longToDouble(int64_t a)
{
  return doubleToBits(static_cast<double>(a));
}

uint32_t FORCE_ALIGN
longToFloat(int64_t a)
{
  return floatToBits(static_cast<float>(a));
}

object FORCE_ALIGN
makeBlankObjectArray(MyThread* t, object class_, int32_t length)
{
  if (length >= 0) {
    return makeObjectArray(t, class_, length, true);
  } else {
    object message = makeString(t, "%d", length);
    t->exception = makeNegativeArraySizeException(t, message);
    unwind(t);
  }
}

object FORCE_ALIGN
makeBlankArray(MyThread* t, object (*constructor)(Thread*, uintptr_t, bool),
               int32_t length)
{
  if (length >= 0) {
    return constructor(t, length, true);
  } else {
    object message = makeString(t, "%d", length);
    t->exception = makeNegativeArraySizeException(t, message);
    unwind(t);
  }
}

uintptr_t FORCE_ALIGN
lookUpAddress(int32_t key, uintptr_t* start, int32_t count,
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

void FORCE_ALIGN
setMaybeNull(MyThread* t, object o, unsigned offset, object value)
{
  if (LIKELY(o)) {
    set(t, o, offset, value);
  } else {
    t->exception = makeNullPointerException(t);
    unwind(t);
  }
}

void FORCE_ALIGN
acquireMonitorForObject(MyThread* t, object o)
{
  if (LIKELY(o)) {
    acquire(t, o);
  } else {
    t->exception = makeNullPointerException(t);
    unwind(t);
  }
}

void FORCE_ALIGN
releaseMonitorForObject(MyThread* t, object o)
{
  if (LIKELY(o)) {
    release(t, o);
  } else {
    t->exception = makeNullPointerException(t);
    unwind(t);
  }
}

object
makeMultidimensionalArray2(MyThread* t, object class_, uintptr_t* stack,
                           int32_t dimensions)
{
  PROTECT(t, class_);

  int32_t counts[dimensions];
  for (int i = dimensions - 1; i >= 0; --i) {
    counts[i] = stack[dimensions - i - 1];
    if (UNLIKELY(counts[i] < 0)) {
      object message = makeString(t, "%d", counts[i]);
      t->exception = makeNegativeArraySizeException(t, message);
      return 0;
    }
  }

  object array = makeArray(t, counts[0], true);
  setObjectClass(t, array, class_);
  PROTECT(t, array);

  populateMultiArray(t, array, counts, 0, dimensions);

  return array;
}

object FORCE_ALIGN
makeMultidimensionalArray(MyThread* t, object class_, int32_t dimensions,
                          uintptr_t* stack)
{
  object r = makeMultidimensionalArray2(t, class_, stack, dimensions);
  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return r;
  }
}

unsigned
traceSize(Thread* t)
{
  class Counter: public Processor::StackVisitor {
   public:
    Counter(): count(0) { }

    virtual bool visit(Processor::StackWalker*) {
      ++ count;
      return true;
    }

    unsigned count;
  } counter;

  t->m->processor->walkStack(t, &counter);

  return FixedSizeOfArray + (counter.count * ArrayElementSizeOfArray)
    + (counter.count * FixedSizeOfTraceElement);
}

void NO_RETURN FORCE_ALIGN
throwArrayIndexOutOfBounds(MyThread* t)
{
  ensure(t, FixedSizeOfArrayIndexOutOfBoundsException + traceSize(t));
  
  t->tracing = true;
  t->exception = makeArrayIndexOutOfBoundsException(t, 0);
  t->tracing = false;

  unwind(t);
}

void NO_RETURN FORCE_ALIGN
throw_(MyThread* t, object o)
{
  if (LIKELY(o)) {
    t->exception = o;
  } else {
    t->exception = makeNullPointerException(t);
  }
  unwind(t);
}

void FORCE_ALIGN
checkCast(MyThread* t, object class_, object o)
{
  if (UNLIKELY(o and not isAssignableFrom(t, class_, objectClass(t, o)))) {
    object message = makeString
      (t, "%s as %s",
       &byteArrayBody(t, className(t, objectClass(t, o)), 0),
       &byteArrayBody(t, className(t, class_), 0));
    t->exception = makeClassCastException(t, message);
    unwind(t);
  }
}

void FORCE_ALIGN
gcIfNecessary(MyThread* t)
{
  if (UNLIKELY(t->backupHeap)) {
    collect(t, Heap::MinorCollection);
  }
}

unsigned
resultSize(MyThread* t, unsigned code)
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
    return BytesPerWord;

  case LongField:
  case DoubleField:
    return 8;

  case VoidField:
    return 0;

  default:
    abort(t);
  }
}

void
pushReturnValue(MyThread* t, Frame* frame, unsigned code,
                Compiler::Operand* result)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    return frame->pushInt(result);

  case ObjectField:
    return frame->pushObject(result);

  case LongField:
  case DoubleField:
    return frame->pushLong(result);

  default:
    abort(t);
  }
}

bool
emptyMethod(MyThread* t, object method)
{
  return ((methodFlags(t, method) & ACC_NATIVE) == 0)
    and (codeLength(t, methodCode(t, method)) == 1)
    and (codeBody(t, methodCode(t, method), 0) == return_);
}

object
defaultThunk(MyThread* t);

object
nativeThunk(MyThread* t);

object
aioobThunk(MyThread* t);

void
compileDirectInvoke(MyThread* t, Frame* frame, object target)
{
  Compiler* c = frame->c;

  unsigned rSize = resultSize(t, methodReturnCode(t, target));

  Compiler::Operand* result = 0;

  if (not emptyMethod(t, target)) {
    if (methodFlags(t, target) & ACC_NATIVE) {
      result = c->call
        (c->constant
         (reinterpret_cast<intptr_t>
          (&singletonBody(t, nativeThunk(t), 0))),
         0,
         frame->trace(target, false),
         rSize,
         0);
    } else if (methodCompiled(t, target) == defaultThunk(t)) {
      result = c->call
        (c->constant
         (reinterpret_cast<intptr_t>
          (&singletonBody(t, defaultThunk(t), 0))),
         Compiler::Aligned,
         frame->trace(target, false),
         rSize,
         0);
    } else {
      result = c->call
        (c->constant
         (reinterpret_cast<intptr_t>
          (&singletonBody(t, methodCompiled(t, target), 0))),
         0,
         frame->trace(0, false),
         rSize,
         0);
    }
  }

  frame->pop(methodParameterFootprint(t, target));

  if (rSize) {
    pushReturnValue(t, frame, methodReturnCode(t, target), result);
  }
}

void
handleMonitorEvent(MyThread* t, Frame* frame, intptr_t function)
{
  Compiler* c = frame->c;
  object method = frame->context->method;

  if (methodFlags(t, method) & ACC_SYNCHRONIZED) {
    Compiler::Operand* lock;
    if (methodFlags(t, method) & ACC_STATIC) {
      lock = frame->append(methodClass(t, method));
    } else {
      lock = c->memory
        (c->base(), localOffset(t, savedTargetIndex(t, method), method));
    }
    
    c->call(c->constant(function),
            0,
            frame->trace(0, false),
            0,
            2, c->thread(), lock);
  }
}

void
handleEntrance(MyThread* t, Frame* frame)
{
  object method = frame->context->method;

  if ((methodFlags(t, method) & (ACC_SYNCHRONIZED | ACC_STATIC))
      == ACC_SYNCHRONIZED)
  {
    Compiler* c = frame->c;

    // save 'this' pointer in case it is overwritten.
    unsigned index = savedTargetIndex(t, method);
    c->store(BytesPerWord,
             c->memory(c->base(), localOffset(t, 0, method)),
             c->memory(c->base(), localOffset(t, index, method)));
    frame->set(index, Frame::Object);
  }

  handleMonitorEvent
    (t, frame, getThunk(t, acquireMonitorForObjectThunk));
}

void
handleExit(MyThread* t, Frame* frame)
{
  handleMonitorEvent
    (t, frame, getThunk(t, releaseMonitorForObjectThunk));
}

void
compile(MyThread* t, Frame* initialFrame, unsigned ip,
        bool exceptionHandler = false)
{
  uint8_t stackMap
    [codeMaxStack(t, methodCode(t, initialFrame->context->method))];
  Frame myFrame(initialFrame, stackMap);
  Frame* frame = &myFrame;
  Compiler* c = frame->c;
  Context* context = frame->context;

  object code = methodCode(t, context->method);
  PROTECT(t, code);
    
  while (ip < codeLength(t, code)) {
    if (context->visitTable[ip] ++) {
      // we've already visited this part of the code
      frame->visitLogicalIp(ip);
      return;
    }

    frame->startLogicalIp(ip);

    if (ip == 0) {
      handleEntrance(t, frame);
    } else if (exceptionHandler) {
      exceptionHandler = false;

      frame->pushObject();
      
      c->call
        (c->constant(getThunk(t, gcIfNecessaryThunk)),
         0,
         frame->trace(0, false),
         0,
         1, c->thread());
    }

//     fprintf(stderr, "ip: %d map: %ld\n", ip, *(frame->map));

    unsigned instruction = codeBody(t, code, ip++);

    switch (instruction) {
    case aaload:
    case baload:
    case caload:
    case daload:
    case faload:
    case iaload:
    case laload:
    case saload: {
      Compiler::Operand* index = frame->popInt();
      Compiler::Operand* array = frame->popObject();

      if (CheckArrayBounds) {
        c->checkBounds(array, ArrayLength, index, reinterpret_cast<intptr_t>
                       (&singletonValue(t, aioobThunk(t), 0)));
      }

      switch (instruction) {
      case aaload:
        frame->pushObject
          (c->load
           (BytesPerWord, c->memory(array, ArrayBody, index, BytesPerWord)));
        break;

      case faload:
      case iaload:
        frame->pushInt(c->load(4, c->memory(array, ArrayBody, index, 4)));
        break;

      case baload:
        frame->pushInt(c->load(1, c->memory(array, ArrayBody, index, 1)));
        break;

      case caload:
        frame->pushInt(c->loadz(2, c->memory(array, ArrayBody, index, 2)));
        break;

      case daload:
      case laload:
        frame->pushLong(c->load(8, c->memory(array, ArrayBody, index, 8)));
        break;

      case saload:
        frame->pushInt(c->load(2, c->memory(array, ArrayBody, index, 2)));
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
      Compiler::Operand* value;
      if (instruction == dastore or instruction == lastore) {
        value = frame->popLong();
      } else if (instruction == aastore) {
        value = frame->popObject();
      } else {
        value = frame->popInt();
      }

      Compiler::Operand* index = frame->popInt();
      Compiler::Operand* array = frame->popObject();

      if (CheckArrayBounds) {
        c->checkBounds(array, ArrayLength, index, reinterpret_cast<intptr_t>
                       (&singletonValue(t, aioobThunk(t), 0)));
      }

      switch (instruction) {
      case aastore: {
        c->call
          (c->constant(getThunk(t, setMaybeNullThunk)),
           0,
           frame->trace(0, false),
           0,
           4, c->thread(), array,
           c->add(4, c->constant(ArrayBody),
                  c->shl(4, c->constant(log(BytesPerWord)), index)),
           value);
      } break;

      case fastore:
      case iastore:
        c->store(4, value, c->memory(array, ArrayBody, index, 4));
        break;

      case bastore:
        c->store(1, value, c->memory(array, ArrayBody, index, 1));
        break;

      case castore:
      case sastore:
        c->store(2, value, c->memory(array, ArrayBody, index, 2));
        break;

      case dastore:
      case lastore:
        c->store(8, value, c->memory(array, ArrayBody, index, 8));
        break;
      }
    } break;

    case aconst_null:
      frame->pushObject(c->constant(0));
      break;

    case aload:
      frame->loadObject(codeBody(t, code, ip++));
      break;

    case aload_0:
      frame->loadObject(0);
      break;

    case aload_1:
      frame->loadObject(1);
      break;

    case aload_2:
      frame->loadObject(2);
      break;

    case aload_3:
      frame->loadObject(3);
      break;

    case anewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Compiler::Operand* length = frame->popInt();

      frame->pushObject
        (c->call
         (c->constant(getThunk(t, makeBlankObjectArrayThunk)),
          0,
          frame->trace(0, false),
          BytesPerWord,
          3, c->thread(), frame->append(class_), length));
    } break;

    case areturn: {
      handleExit(t, frame);
      c->return_(BytesPerWord, frame->popObject());
    } return;

    case arraylength: {
      frame->pushInt
        (c->load
         (BytesPerWord, c->memory(frame->popObject(), ArrayLength, 0, 1)));
    } break;

    case astore:
      frame->storeObjectOrAddress(codeBody(t, code, ip++));
      break;

    case astore_0:
      frame->storeObjectOrAddress(0);
      break;

    case astore_1:
      frame->storeObjectOrAddress(1);
      break;

    case astore_2:
      frame->storeObjectOrAddress(2);
      break;

    case astore_3:
      frame->storeObjectOrAddress(3);
      break;

    case athrow: {
      c->call
        (c->constant(getThunk(t, throw_Thunk)),
         Compiler::NoReturn,
         frame->trace(0, false),
         0,
         2, c->thread(), frame->popObject());
    } return;

    case bipush:
      frame->pushInt
        (c->constant(static_cast<int8_t>(codeBody(t, code, ip++))));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Compiler::Operand* instance = c->peek(BytesPerWord, 0);

      c->call
        (c->constant(getThunk(t, checkCastThunk)),
         0,
         frame->trace(0, false),
         0,
         3, c->thread(), frame->append(class_), instance);
    } break;

    case d2f: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, doubleToFloatThunk)),
          0, 0, 4, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case d2i: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, doubleToIntThunk)),
          0, 0, 4, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case d2l: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, doubleToLongThunk)),
          0, 0, 8, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case dadd: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushLong
        (c->call
         (c->constant(getThunk(t, addDoubleThunk)),
          0, 0, 8, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dcmpg: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, compareDoublesGThunk)),
          0, 0, 4, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dcmpl: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, compareDoublesLThunk)),
          0, 0, 4, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dconst_0:
      frame->pushLong(c->constant(doubleToBits(0.0)));
      break;
      
    case dconst_1:
      frame->pushLong(c->constant(doubleToBits(1.0)));
      break;

    case ddiv: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushLong
        (c->call
         (c->constant(getThunk(t, divideDoubleThunk)),
          0, 0, 8, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dmul: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushLong
        (c->call
         (c->constant(getThunk(t, multiplyDoubleThunk)),
          0, 0, 8, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dneg: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, negateDoubleThunk)),
          0, 0, 8, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case vm::drem: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushLong
        (c->call
         (c->constant(getThunk(t, moduloDoubleThunk)),
          0, 0, 8, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dsub: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushLong
        (c->call
         (c->constant(getThunk(t, subtractDoubleThunk)),
          0, 0, 8, 4,
          static_cast<Compiler::Operand*>(0), a,
          static_cast<Compiler::Operand*>(0), b));
    } break;

    case dup:
      frame->dup();
      break;

    case dup_x1:
      frame->dupX1();
      break;

    case dup_x2:
      frame->dupX2();
      break;

    case dup2:
      frame->dup2();
      break;

    case dup2_x1:
      frame->dup2X1();
      break;

    case dup2_x2:
      frame->dup2X2();
      break;

    case f2d: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, floatToDoubleThunk)),
          0, 0, 8, 1, frame->popInt()));
    } break;

    case f2i: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, floatToIntThunk)),
          0, 0, 4, 1, frame->popInt()));
    } break;

    case f2l: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, floatToLongThunk)),
          0, 0, 8, 1, frame->popInt()));
    } break;

    case fadd: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, addFloatThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fcmpg: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, compareFloatsGThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fcmpl: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, compareFloatsLThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fconst_0:
      frame->pushInt(c->constant(floatToBits(0.0)));
      break;
      
    case fconst_1:
      frame->pushInt(c->constant(floatToBits(1.0)));
      break;
      
    case fconst_2:
      frame->pushInt(c->constant(floatToBits(2.0)));
      break;

    case fdiv: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, divideFloatThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fmul: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, multiplyFloatThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fneg: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, negateFloatThunk)),
          0, 0, 4, 1, frame->popInt()));
    } break;

    case vm::frem: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, moduloFloatThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case fsub: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, subtractFloatThunk)),
          0, 0, 4, 2, a, b));
    } break;

    case getfield:
    case getstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Compiler::Operand* table;

      if (instruction == getstatic) {
        if ((classVmFlags(t, fieldClass(t, field)) & NeedInitFlag)
            and (classVmFlags(t, fieldClass(t, field)) & InitFlag) == 0)
        {
          c->call
            (c->constant(getThunk(t, tryInitClassThunk)),
             0,
             frame->trace(0, false),
             0,
             2, c->thread(), frame->append(fieldClass(t, field)));
        }

        table = frame->append(classStaticTable(t, fieldClass(t, field)));
      } else {
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        frame->pushInt
          (c->load(1, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      case CharField:
        frame->pushInt
          (c->loadz(2, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      case ShortField:
        frame->pushInt
          (c->load(2, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      case FloatField:
      case IntField:
        frame->pushInt
          (c->load(4, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      case DoubleField:
      case LongField:
        frame->pushLong
          (c->load(8, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      case ObjectField:
        frame->pushObject
          (c->load
           (BytesPerWord, c->memory(table, fieldOffset(t, field), 0, 1)));
        break;

      default:
        abort(t);
      }
    } break;

    case goto_: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->jmp(frame->machineIp(newIp));
      ip = newIp;
    } break;

    case goto_w: {
      uint32_t newIp = (ip - 5) + codeReadInt32(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      c->jmp(frame->machineIp(newIp));
      ip = newIp;
    } break;

    case i2b: {
      frame->pushInt(c->load(1, frame->popInt()));
    } break;

    case i2c: {
      frame->pushInt(c->loadz(2, frame->popInt()));
    } break;

    case i2d: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, intToDoubleThunk)),
          0, 0, 8, 1, frame->popInt()));
    } break;

    case i2f: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, intToFloatThunk)),
          0, 0, 4, 1, frame->popInt()));
    } break;

    case i2l:
      frame->pushLong(c->load4To8(frame->popInt()));
      break;

    case i2s: {
      frame->pushInt(c->load(2, frame->popInt()));
    } break;
      
    case iadd: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->add(4, a, b));
    } break;
      
    case iand: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->and_(4, a, b));
    } break;

    case iconst_m1:
      frame->pushInt(c->constant(-1));
      break;

    case iconst_0:
      frame->pushInt(c->constant(0));
      break;

    case iconst_1:
      frame->pushInt(c->constant(1));
      break;

    case iconst_2:
      frame->pushInt(c->constant(2));
      break;

    case iconst_3:
      frame->pushInt(c->constant(3));
      break;

    case iconst_4:
      frame->pushInt(c->constant(4));
      break;

    case iconst_5:
      frame->pushInt(c->constant(5));
      break;

    case idiv: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->div(4, a, b));
    } break;

    case if_acmpeq:
    case if_acmpne: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Compiler::Operand* a = frame->popObject();
      Compiler::Operand* b = frame->popObject();
      Compiler::Operand* target = frame->machineIp(newIp);

      c->cmp(BytesPerWord, a, b);
      if (instruction == if_acmpeq) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case if_icmpeq:
    case if_icmpne:
    case if_icmpgt:
    case if_icmpge:
    case if_icmplt:
    case if_icmple: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      Compiler::Operand* target = frame->machineIp(newIp);

      c->cmp(4, a, b);
      switch (instruction) {
      case if_icmpeq:
        c->je(target);
        break;
      case if_icmpne:
        c->jne(target);
        break;
      case if_icmpgt:
        c->jg(target);
        break;
      case if_icmpge:
        c->jge(target);
        break;
      case if_icmplt:
        c->jl(target);
        break;
      case if_icmple:
        c->jle(target);
        break;
      }
      
      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case ifeq:
    case ifne:
    case ifgt:
    case ifge:
    case iflt:
    case ifle: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* target = frame->machineIp(newIp);

      c->cmp(4, c->constant(0), a);
      switch (instruction) {
      case ifeq:
        c->je(target);
        break;
      case ifne:
        c->jne(target);
        break;
      case ifgt:
        c->jg(target);
        break;
      case ifge:
        c->jge(target);
        break;
      case iflt:
        c->jl(target);
        break;
      case ifle:
        c->jle(target);
        break;
      }

      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case ifnull:
    case ifnonnull: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));

      Compiler::Operand* a = frame->popObject();
      Compiler::Operand* target = frame->machineIp(newIp);

      c->cmp(BytesPerWord, c->constant(0), a);
      if (instruction == ifnull) {
        c->je(target);
      } else {
        c->jne(target);
      }
      
      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;
    } break;

    case iinc: {
      uint8_t index = codeBody(t, code, ip++);
      int8_t count = codeBody(t, code, ip++);

      Compiler::Operand* a = c->memory
        (c->base(), localOffset(t, index, context->method));

      c->storeLocal(4, c->add(4, c->constant(count), a), index);
    } break;

    case iload:
    case fload:
      frame->loadInt(codeBody(t, code, ip++));
      break;

    case iload_0:
    case fload_0:
      frame->loadInt(0);
      break;

    case iload_1:
    case fload_1:
      frame->loadInt(1);
      break;

    case iload_2:
    case fload_2:
      frame->loadInt(2);
      break;

    case iload_3:
    case fload_3:
      frame->loadInt(3);
      break;

    case imul: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->mul(4, a, b));
    } break;

    case ineg: {
      frame->pushInt(c->neg(4, frame->popInt()));
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      frame->pushInt
        (c->call
         (c->constant(getThunk(t, instanceOfThunk)),
          0, 0, 4,
          3, c->thread(), frame->append(class_), frame->popObject()));
    } break;

    case invokeinterface: {
      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint = methodParameterFootprint(t, target);

      unsigned instance = parameterFootprint - 1;

      unsigned rSize = resultSize(t, methodReturnCode(t, target));

      Compiler::Operand* result = c->call
        (c->call
         (c->constant
          (getThunk(t, findInterfaceMethodFromInstanceThunk)),
          0,
          frame->trace(0, false),
          BytesPerWord,
          3, c->thread(), frame->append(target),
          c->peek(BytesPerWord, instance)),
         0,
         frame->trace(target, true),
         rSize,
         0);

      frame->pop(parameterFootprint);

      if (rSize) {
        pushReturnValue(t, frame, methodReturnCode(t, target), result);
      }
    } break;

    case invokespecial: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      object class_ = methodClass(t, context->method);
      if (isSpecialMethod(t, target, class_)) {
        target = findMethod(t, target, classSuper(t, class_));
      }

      compileDirectInvoke(t, frame, target);
    } break;

    case invokestatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      compileDirectInvoke(t, frame, target);
    } break;

    case invokevirtual: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint = methodParameterFootprint(t, target);

      unsigned offset = ClassVtable + (methodOffset(t, target) * BytesPerWord);

      Compiler::Operand* instance = c->peek
        (BytesPerWord, parameterFootprint - 1);

      unsigned rSize = resultSize(t, methodReturnCode(t, target));

      Compiler::Operand* result = c->call
        (c->memory
         (c->and_
          (BytesPerWord, c->constant(PointerMask),
           c->memory(instance, 0, 0, 1)), offset, 0, 1),
         0,
         frame->trace(target, true),
         rSize,
         0);

      frame->pop(parameterFootprint);

      if (rSize) {
        pushReturnValue(t, frame, methodReturnCode(t, target), result);
      }
    } break;

    case ior: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->or_(4, a, b));
    } break;

    case irem: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->rem(4, a, b));
    } break;

    case ireturn:
    case freturn: {
      handleExit(t, frame);
      c->return_(4, frame->popInt());
    } return;

    case ishl: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->shl(4, a, b));
    } break;

    case ishr: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->shr(4, a, b));
    } break;

    case istore:
    case fstore:
      frame->storeInt(codeBody(t, code, ip++));
      break;

    case istore_0:
    case fstore_0:
      frame->storeInt(0);
      break;

    case istore_1:
    case fstore_1:
      frame->storeInt(1);
      break;

    case istore_2:
    case fstore_2:
      frame->storeInt(2);
      break;

    case istore_3:
    case fstore_3:
      frame->storeInt(3);
      break;

    case isub: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->sub(4, a, b));
    } break;

    case iushr: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->ushr(4, a, b));
    } break;

    case ixor: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popInt();
      frame->pushInt(c->xor_(4, a, b));
    } break;

    case jsr:
    case jsr_w: {
      uint32_t newIp;

      if (instruction == jsr) {
        newIp = (ip - 3) + codeReadInt16(t, code, ip);
      } else {
        newIp = (ip - 5) + codeReadInt32(t, code, ip);
      }

      assert(t, newIp < codeLength(t, code));

      c->saveStack();

      frame->pushAddress(frame->machineIp(ip));
      c->jmp(frame->machineIp(newIp));

      // NB: we assume that the stack will look the same on return
      // from the subroutine as at call time.
      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;

      frame->pop(1);
    } break;

    case l2d: {
      frame->pushLong
        (c->call
         (c->constant(getThunk(t, longToDoubleThunk)),
          0, 0, 8, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case l2f: {
      frame->pushInt
        (c->call
         (c->constant(getThunk(t, longToFloatThunk)),
          0, 0, 4, 2,
          static_cast<Compiler::Operand*>(0), frame->popLong()));
    } break;

    case l2i:
      frame->pushInt(c->load(4, frame->popLong()));
      break;

    case ladd: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->add(8, a, b));
    } break;

    case land: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->and_(8, a, b));
    } break;

    case lcmp: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();

      frame->pushInt(c->load(4, c->lcmp(a, b)));
    } break;

    case lconst_0:
      frame->pushLong(c->constant(0));
      break;

    case lconst_1:
      frame->pushLong(c->constant(1));
      break;

    case ldc:
    case ldc_w: {
      uint16_t index;

      if (instruction == ldc) {
        index = codeBody(t, code, ip++);
      } else {
        index = codeReadInt16(t, code, ip);
      }

      object pool = codePool(t, code);

      if (singletonIsObject(t, pool, index - 1)) {
        object v = singletonObject(t, pool, index - 1);
        if (objectClass(t, v)
            == arrayBody(t, t->m->types, Machine::ByteArrayType))
        {
          object class_ = resolveClassInPool(t, pool, index - 1); 
          if (UNLIKELY(t->exception)) return;

          frame->pushObject(frame->append(class_));
        } else {
          frame->pushObject(frame->append(v));
        }
      } else {
        frame->pushInt(c->constant(singletonValue(t, pool, index - 1)));
      }
    } break;

    case ldc2_w: {
      uint16_t index = codeReadInt16(t, code, ip);

      object pool = codePool(t, code);

      uint64_t v;
      memcpy(&v, &singletonValue(t, pool, index - 1), 8);
      frame->pushLong(c->constant(v));
    } break;

    case ldiv_: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->div(8, a, b));
    } break;

    case lload:
    case dload:
      frame->loadLong(codeBody(t, code, ip++));
      break;

    case lload_0:
    case dload_0:
      frame->loadLong(0);
      break;

    case lload_1:
    case dload_1:
      frame->loadLong(1);
      break;

    case lload_2:
    case dload_2:
      frame->loadLong(2);
      break;

    case lload_3:
    case dload_3:
      frame->loadLong(3);
      break;

    case lmul: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->mul(8, a, b));
    } break;

    case lneg:
      frame->pushLong(c->neg(8, frame->popLong()));
      break;

    case lookupswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      Compiler::Operand* key = frame->popInt();
    
      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));

      Compiler::Operand* default_ = c->address
        (c->poolAppendPromise(c->machineIp(defaultIp)));

      int32_t pairCount = codeReadInt32(t, code, ip);

      Compiler::Operand* start = 0;
      uint32_t ipTable[pairCount];
      for (int32_t i = 0; i < pairCount; ++i) {
        unsigned index = ip + (i * 8);
        int32_t key = codeReadInt32(t, code, index);
        uint32_t newIp = base + codeReadInt32(t, code, index);
        assert(t, newIp < codeLength(t, code));

        ipTable[i] = newIp;

        Promise* p = c->poolAppend(key);
        if (i == 0) {
          start = c->promiseConstant(p);
        }
        c->poolAppendPromise(c->machineIp(newIp));
      }
      assert(t, start);

      c->jmp
        (c->call
         (c->constant(getThunk(t, lookUpAddressThunk)),
          0, 0, BytesPerWord,
          4, key, start, c->constant(pairCount), default_));

      for (int32_t i = 0; i < pairCount; ++i) {
        compile(t, frame, ipTable[i]);
        if (UNLIKELY(t->exception)) return;
      }

      ip = defaultIp;
    } break;

    case lor: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->or_(8, a, b));
    } break;

    case lrem: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->rem(8, a, b));
    } break;

    case lreturn:
    case dreturn: {
      handleExit(t, frame);
      c->return_(8, frame->popLong());
    } return;

    case lshl: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->shl(8, a, b));
    } break;

    case lshr: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->shr(8, a, b));
    } break;

    case lstore:
    case dstore:
      frame->storeLong(codeBody(t, code, ip++));
      break;

    case lstore_0:
    case dstore_0:
      frame->storeLong(0);
      break;

    case lstore_1:
    case dstore_1:
      frame->storeLong(1);
      break;

    case lstore_2:
    case dstore_2:
      frame->storeLong(2);
      break;

    case lstore_3:
    case dstore_3:
      frame->storeLong(3);
      break;

    case lsub: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->sub(8, a, b));
    } break;

    case lushr: {
      Compiler::Operand* a = frame->popInt();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->ushr(8, a, b));
    } break;

    case lxor: {
      Compiler::Operand* a = frame->popLong();
      Compiler::Operand* b = frame->popLong();
      frame->pushLong(c->xor_(8, a, b));
    } break;

    case monitorenter: {
      c->call
        (c->constant(getThunk(t, acquireMonitorForObjectThunk)),
         0,
         frame->trace(0, false), 0, 2, c->thread(), frame->popObject());
    } break;

    case monitorexit: {
      c->call
        (c->constant(getThunk(t, releaseMonitorForObjectThunk)),
         0,
         frame->trace(0, false), 0, 2, c->thread(), frame->popObject());
    } break;

    case multianewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      uint8_t dimensions = codeBody(t, code, ip++);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);

      Compiler::Operand* result = c->call
        (c->constant(getThunk(t, makeMultidimensionalArrayThunk)),
         0,
         frame->trace(0, false),
         BytesPerWord,
         4, c->thread(), frame->append(class_), c->constant(dimensions),
         c->stack());

      frame->pop(dimensions);
      frame->pushObject(result);
    } break;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        frame->pushObject
          (c->call
           (c->constant(getThunk(t, makeNewWeakReferenceThunk)),
            0,
            frame->trace(0, false),
            BytesPerWord,
            2, c->thread(), frame->append(class_)));
      } else {
        frame->pushObject
          (c->call
           (c->constant(getThunk(t, makeNewThunk)),
            0,
            frame->trace(0, false),
            BytesPerWord,
            2, c->thread(), frame->append(class_)));
      }
    } break;

    case newarray: {
      uint8_t type = codeBody(t, code, ip++);

      Compiler::Operand* length = frame->popInt();

      object (*constructor)(Thread*, uintptr_t, bool);
      switch (type) {
      case T_BOOLEAN:
        constructor = makeBooleanArray;
        break;

      case T_CHAR:
        constructor = makeCharArray;
        break;

      case T_FLOAT:
        constructor = makeFloatArray;
        break;

      case T_DOUBLE:
        constructor = makeDoubleArray;
        break;

      case T_BYTE:
        constructor = makeByteArray;
        break;

      case T_SHORT:
        constructor = makeShortArray;
        break;

      case T_INT:
        constructor = makeIntArray;
        break;

      case T_LONG:
        constructor = makeLongArray;
        break;

      default: abort(t);
      }

      frame->pushObject
        (c->call
         (c->constant(getThunk(t, makeBlankArrayThunk)),
          0,
          frame->trace(0, false),
          BytesPerWord,
          3, c->thread(), c->constant(reinterpret_cast<intptr_t>(constructor)),
          length));
    } break;

    case nop: break;

    case pop_:
      frame->pop(1);
      break;

    case pop2:
      frame->pop(2);
      break;

    case putfield:
    case putstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      object staticTable = 0;

      if (instruction == putstatic) {
        if ((classVmFlags(t, fieldClass(t, field)) & NeedInitFlag)
            and (classVmFlags(t, fieldClass(t, field)) & InitFlag) == 0)
        {
          c->call
            (c->constant(getThunk(t, tryInitClassThunk)),
             0,
             frame->trace(0, false),
             0,
             2, c->thread(), frame->append(fieldClass(t, field)));
        }

        staticTable = classStaticTable(t, fieldClass(t, field));      
      }

      Compiler::Operand* value;
      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
      case CharField:
      case ShortField:
      case FloatField:
      case IntField: {
        value = frame->popInt();
      } break;

      case DoubleField:
      case LongField: {
        value = frame->popLong();
      } break;

      case ObjectField: {
        value = frame->popObject();
      } break;

      default: abort(t);
      }

      Compiler::Operand* table;

      if (instruction == putstatic) {
        table = frame->append(staticTable);
      } else {
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        c->store(1, value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case CharField:
      case ShortField:
        c->store(2, value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;
            
      case FloatField:
      case IntField:
        c->store(4, value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case DoubleField:
      case LongField:
        c->store(8, value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case ObjectField:
        if (instruction == putfield) {
          c->call
            (c->constant(getThunk(t, setMaybeNullThunk)),
             0,
             frame->trace(0, false),
             0,
             4, c->thread(), table, c->constant(fieldOffset(t, field)), value);
        } else {
          c->call
            (c->constant(getThunk(t, setThunk)),
             0, 0, 0,
             4, c->thread(), table, c->constant(fieldOffset(t, field)), value);
        }
        break;

      default: abort(t);
      }
    } break;

    case ret:
      c->jmp
        (c->memory
         (c->base(), localOffset
          (t, codeBody(t, code, ip), context->method)));
      return;

    case return_:
      handleExit(t, frame);
      c->return_(0, 0);
      return;

    case sipush:
      frame->pushInt
        (c->constant(static_cast<int16_t>(codeReadInt16(t, code, ip))));
      break;

    case swap:
      frame->swap();
      break;

    case tableswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));
      
      int32_t bottom = codeReadInt32(t, code, ip);
      int32_t top = codeReadInt32(t, code, ip);
        
      Compiler::Operand* start = 0;
      uint32_t ipTable[top - bottom + 1];
      for (int32_t i = 0; i < top - bottom + 1; ++i) {
        unsigned index = ip + (i * 4);
        uint32_t newIp = base + codeReadInt32(t, code, index);
        assert(t, newIp < codeLength(t, code));

        ipTable[i] = newIp;

        Promise* p = c->poolAppendPromise(c->machineIp(newIp));
        if (i == 0) {
          start = c->promiseConstant(p);
        }
      }
      assert(t, start);

      Compiler::Operand* defaultCase = c->label();

      Compiler::Operand* key = frame->popInt();
      
      c->cmp(4, c->constant(bottom), key);
      c->jl(defaultCase);

      c->cmp(4, c->constant(top), key);
      c->jg(defaultCase);

      c->jmp(c->memory(start, 0, c->sub(4, c->constant(bottom), key),
                       BytesPerWord));

      c->mark(defaultCase);
      c->jmp(frame->machineIp(defaultIp));

      for (int32_t i = 0; i < top - bottom + 1; ++i) {
        compile(t, frame, ipTable[i]);
        if (UNLIKELY(t->exception)) return;
      }

      ip = defaultIp;
    } break;

    case wide: {
      switch (codeBody(t, code, ip++)) {
      case aload: {
        frame->loadObject(codeReadInt16(t, code, ip));
      } break;

      case astore: {
        frame->storeObject(codeReadInt16(t, code, ip));
      } break;

      case iinc: {
        uint16_t index = codeReadInt16(t, code, ip);
        uint16_t count = codeReadInt16(t, code, ip);

        Compiler::Operand* a = c->memory
          (c->base(), localOffset(t, index, context->method));

        c->storeLocal(4, c->add(4, c->constant(count), a), index);
      } break;

      case iload: {
        frame->loadInt(codeReadInt16(t, code, ip));
      } break;

      case istore: {
        frame->storeInt(codeReadInt16(t, code, ip));
      } break;

      case lload: {
        frame->loadLong(codeReadInt16(t, code, ip));
      } break;

      case lstore: {
        frame->storeLong(codeReadInt16(t, code, ip));
      } break;

      case ret:
        c->jmp
          (c->memory
           (c->base(), localOffset
            (t, codeReadInt16(t, code, ip), context->method)));
        return;

      default: abort(t);
      }
    } break;

    default: abort(t);
    }
  }
}

void
logCompile(const void* code, unsigned size, const char* class_,
           const char* name, const char* spec)
{
  fprintf(stderr, "%s.%s%s: %p %p\n",
          class_, name, spec, code,
          static_cast<const uint8_t*>(code) + size);
}

void
translateExceptionHandlerTable(MyThread* t, Compiler* c, object code,
                               intptr_t start)
{
  object oldTable = codeExceptionHandlerTable(t, code);
  if (oldTable) {
    PROTECT(t, code);
    PROTECT(t, oldTable);

    unsigned length = exceptionHandlerTableLength(t, oldTable);

    object newIndex = makeIntArray(t, length * 3, false);
    PROTECT(t, newIndex);

    object newTable = makeArray(t, length + 1, true);
    PROTECT(t, newTable);

    set(t, newTable, ArrayBody, newIndex);

    for (unsigned i = 0; i < length; ++i) {
      ExceptionHandler* oldHandler = exceptionHandlerTableBody
        (t, oldTable, i);

      intArrayBody(t, newIndex, i * 3)
        = c->machineIp(exceptionHandlerStart(oldHandler))->value() - start;

      intArrayBody(t, newIndex, (i * 3) + 1)
        = c->machineIp(exceptionHandlerEnd(oldHandler))->value() - start;

      intArrayBody(t, newIndex, (i * 3) + 2)
        = c->machineIp(exceptionHandlerIp(oldHandler))->value() - start;

      object type;
      if (exceptionHandlerCatchType(oldHandler)) {
        type = resolveClassInPool
          (t, codePool(t, code), exceptionHandlerCatchType(oldHandler) - 1);
        if (UNLIKELY(t->exception)) return;
      } else {
        type = 0;
      }

      set(t, newTable, ArrayBody + ((i + 1) * BytesPerWord), type);
    }

    set(t, code, CodeExceptionHandlerTable, newTable);
  }
}

void
translateLineNumberTable(MyThread* t, Compiler* c, object code, intptr_t start)
{
  object oldTable = codeLineNumberTable(t, code);
  if (oldTable) {
    PROTECT(t, code);
    PROTECT(t, oldTable);

    unsigned length = lineNumberTableLength(t, oldTable);
    object newTable = makeLineNumberTable(t, length, false);
    for (unsigned i = 0; i < length; ++i) {
      LineNumber* oldLine = lineNumberTableBody(t, oldTable, i);
      LineNumber* newLine = lineNumberTableBody(t, newTable, i);

      lineNumberIp(newLine)
        = c->machineIp(lineNumberIp(oldLine))->value() - start;

      lineNumberLine(newLine) = lineNumberLine(oldLine);
    }

    set(t, code, CodeLineNumberTable, newTable);
  }
}

void
printSet(uintptr_t m)
{
  for (unsigned i = 0; i < 16; ++i) {
    if ((m >> i) & 1) {
      fprintf(stderr, "1");
    } else {
      fprintf(stderr, "_");
    }
  }
}

unsigned
calculateFrameMaps(MyThread* t, Context* context, uintptr_t* originalRoots,
                   unsigned eventIndex)
{
  // for each instruction with more than one predecessor, and for each
  // stack position, determine if there exists a path to that
  // instruction such that there is not an object pointer left at that
  // stack position (i.e. it is uninitialized or contains primitive
  // data).

  unsigned mapSize = frameMapSizeInWords(t, context->method);

  uintptr_t roots[mapSize];
  if (originalRoots) {
    memcpy(roots, originalRoots, mapSize * BytesPerWord);
  } else {
    memset(roots, 0, mapSize * BytesPerWord);
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
    case PushEvent: {
      eventIndex = calculateFrameMaps(t, context, roots, eventIndex);
    } break;

    case PopEvent:
      return eventIndex;

    case IpEvent: {
      ip = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      if (DebugFrameMaps) {
        fprintf(stderr, "      roots at ip %3d: ", ip);
        printSet(*roots);
        fprintf(stderr, "\n");
      }

      uintptr_t* tableRoots = context->rootTable + (ip * mapSize);  

      if (context->visitTable[ip] > 1) {
        for (unsigned wi = 0; wi < mapSize; ++wi) {
          uintptr_t newRoots = tableRoots[wi] & roots[wi];

          if ((eventIndex == length
               or context->eventLog.get(eventIndex) == PopEvent)
              and newRoots != tableRoots[wi])
          {
            if (DebugFrameMaps) {
              fprintf(stderr, "dirty roots!\n");
            }

            context->dirtyRoots = true;
          }

          tableRoots[wi] = newRoots;
          roots[wi] &= tableRoots[wi];
        }

        if (DebugFrameMaps) {
          fprintf(stderr, "table roots at ip %3d: ", ip);
          printSet(*tableRoots);
          fprintf(stderr, "\n");
        }
      } else {
        memcpy(tableRoots, roots, mapSize * BytesPerWord);
      }
    } break;

    case MarkEvent: {
      unsigned i = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      markBit(roots, i);
    } break;

    case ClearEvent: {
      unsigned i = context->eventLog.get2(eventIndex);
      eventIndex += 2;

      clearBit(roots, i);
    } break;

    case TraceEvent: {
      TraceElement* te; context->eventLog.get(eventIndex, &te, BytesPerWord);
      memcpy(te->map, roots, mapSize * BytesPerWord);

      eventIndex += BytesPerWord;
    } break;

    default: abort(t);
    }
  }

  return eventIndex;
}

Zone*
codeZone(MyThread* t);

int
compareTraceElementPointers(const void* va, const void* vb)
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

intptr_t
compareMethodBounds(Thread* t, object a, object b)
{
  if (DebugMethodTree) {
    fprintf(stderr, "compare %p to %p\n",
            &singletonValue(t, methodCompiled(t, a), 0),
            &singletonValue(t, methodCompiled(t, b), 0));
  }

  return reinterpret_cast<intptr_t>
    (&singletonValue(t, methodCompiled(t, a), 0))
    -  reinterpret_cast<intptr_t>
    (&singletonValue(t, methodCompiled(t, b), 0));
}

unsigned
frameObjectMapSize(MyThread* t, object method, object map)
{
  int size = frameSize(t, method);
  return ceiling(intArrayLength(t, map) * size, 32 + size);
}

unsigned
codeSingletonSizeInBytes(MyThread*, unsigned codeSizeInBytes)
{
  unsigned count = ceiling(codeSizeInBytes, BytesPerWord);
  unsigned size = count + singletonMaskSize(count);
  return pad(SingletonBody + (size * BytesPerWord));
}

object
allocateCode(MyThread* t, unsigned codeSizeInBytes)
{
  unsigned count = ceiling(codeSizeInBytes, BytesPerWord);
  unsigned size = count + singletonMaskSize(count);
  object result = allocate3
    (t, codeZone(t), Machine::ImmortalAllocation,
     SingletonBody + (size * BytesPerWord), true);
  initSingleton(t, result, size, true);
  mark(t, result, 0);
  singletonMask(t, result)[0] = 1;
  return result;
}

object
finish(MyThread* t, Assembler* a, const char* name)
{
  object result = allocateCode(t, a->length());
  uint8_t* start = reinterpret_cast<uint8_t*>(&singletonValue(t, result, 0));

  a->writeTo(start);

  if (Verbose) {
    logCompile(start, a->length(), 0, name, 0);
  }

  return result;
}

object
finish(MyThread* t, Context* context)
{
  Compiler* c = context->compiler;

  unsigned codeSize = c->compile();
  object result = allocateCode(t, pad(codeSize) + c->poolSize());
  PROTECT(t, result);

  uint8_t* start = reinterpret_cast<uint8_t*>(&singletonValue(t, result, 0));

  c->writeTo(start);

  translateExceptionHandlerTable(t, c, methodCode(t, context->method),
                                 reinterpret_cast<intptr_t>(start));
  if (UNLIKELY(t->exception)) return 0;

  translateLineNumberTable(t, c, methodCode(t, context->method),
                           reinterpret_cast<intptr_t>(start));

  { object code = methodCode(t, context->method);

    code = makeCode(t, 0,
                    codeExceptionHandlerTable(t, code),
                    codeLineNumberTable(t, code),
                    codeMaxStack(t, code),
                    codeMaxLocals(t, code),
                    0, false);

    set(t, context->method, MethodCode, code);
  }

  if (context->traceLogCount) {
    TraceElement* elements[context->traceLogCount];
    unsigned index = 0;
    for (TraceElement* p = context->traceLog; p; p = p->next) {
      assert(t, index < context->traceLogCount);

      elements[index++] = p;

      if (p->target) {
        insertCallNode
          (t, makeCallNode
           (t, p->address->value(), p->target, p->virtualCall, 0));
      }
    }

    qsort(elements, context->traceLogCount, sizeof(TraceElement*),
          compareTraceElementPointers);

    unsigned size = frameSize(t, context->method);
    object map = makeIntArray
      (t, context->traceLogCount
       + ceiling(context->traceLogCount * size, 32),
       false);

    assert(t, intArrayLength(t, map) == context->traceLogCount
           + frameObjectMapSize(t, context->method, map));

    for (unsigned i = 0; i < context->traceLogCount; ++i) {
      TraceElement* p = elements[i];

      intArrayBody(t, map, i) = static_cast<intptr_t>(p->address->value())
        - reinterpret_cast<intptr_t>(start);

      for (unsigned j = 0; j < size; ++j) {
        unsigned index = ((i * size) + j);
        int32_t* v = &intArrayBody
          (t, map, context->traceLogCount + (index / 32));

        if (getBit(p->map, j)) {
          *v |= static_cast<int32_t>(1) << (index % 32);
        } else {
          *v &= ~(static_cast<int32_t>(1) << (index % 32));
        }
      }
    }

    set(t, methodCode(t, context->method), CodePool, map);
  }

  for (PoolElement* p = context->objectPool; p; p = p->next) {
    intptr_t offset = p->address->value() - reinterpret_cast<intptr_t>(start);

    singletonMarkObject(t, result, offset / BytesPerWord);

    set(t, result, SingletonBody + offset, p->value);
  }

  if (Verbose) {
    logCompile
      (start, codeSize,
       reinterpret_cast<const char*>
       (&byteArrayBody(t, className(t, methodClass(t, context->method)), 0)),
       reinterpret_cast<const char*>
       (&byteArrayBody(t, methodName(t, context->method), 0)),
       reinterpret_cast<const char*>
       (&byteArrayBody(t, methodSpec(t, context->method), 0)));
  }

  // for debugging:
  if (false and
      strcmp
      (reinterpret_cast<const char*>
       (&byteArrayBody(t, className(t, methodClass(t, context->method)), 0)),
       "java/lang/String") == 0 and
      strcmp
      (reinterpret_cast<const char*>
       (&byteArrayBody(t, methodName(t, context->method), 0)),
       "compareTo") == 0)
  {
    asm("int3");
  }

  return result;
}

object
compile(MyThread* t, Context* context)
{
  Compiler* c = context->compiler;

//   fprintf(stderr, "compiling %s.%s%s\n",
//           &byteArrayBody(t, className(t, methodClass(t, context->method)), 0),
//           &byteArrayBody(t, methodName(t, context->method), 0),
//           &byteArrayBody(t, methodSpec(t, context->method), 0));

  unsigned footprint = methodParameterFootprint(t, context->method);
  unsigned locals = localSize(t, context->method);
  c->init(codeLength(t, methodCode(t, context->method)), footprint, locals);

  uint8_t stackMap[codeMaxStack(t, methodCode(t, context->method))];
  Frame frame(context, stackMap);

  unsigned index = 0;
  if ((methodFlags(t, context->method) & ACC_STATIC) == 0) {
    frame.set(index++, Frame::Object);    
  }

  for (MethodSpecIterator it
         (t, reinterpret_cast<const char*>
          (&byteArrayBody(t, methodSpec(t, context->method), 0)));
       it.hasNext();)
  {
    switch (*it.next()) {
    case 'L':
    case '[':
      frame.set(index++, Frame::Object);
      break;
      
    case 'J':
    case 'D':
      frame.set(index++, Frame::Long);
      frame.set(index++, Frame::Long);
      break;

    default:
      frame.set(index++, Frame::Integer);
      break;
    }
  }

  compile(t, &frame, 0);
  if (UNLIKELY(t->exception)) return 0;

  context->dirtyRoots = false;
  unsigned eventIndex = calculateFrameMaps(t, context, 0, 0);

  object eht = codeExceptionHandlerTable(t, methodCode(t, context->method));
  if (eht) {
    PROTECT(t, eht);

    unsigned visitCount = exceptionHandlerTableLength(t, eht);
    bool visited[visitCount];
    memset(visited, 0, visitCount);

    while (visitCount) {
      bool progress = false;

      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
        unsigned start = exceptionHandlerStart(eh);

        if (not visited[i] and context->visitTable[start]) {
          -- visitCount;
          visited[i] = true;
          progress = true;

          uint8_t stackMap[codeMaxStack(t, methodCode(t, context->method))];
          Frame frame2(&frame, stackMap);

          uintptr_t* roots = context->rootTable
            + (start * frameMapSizeInWords(t, context->method));

          for (unsigned i = 0; i < localSize(t, context->method); ++ i) {
            if (getBit(roots, i)) {
              frame2.set(i, Frame::Object);
            } else {
              frame2.set(i, Frame::Integer);
            }
          }

          for (unsigned i = 1;
               i < codeMaxStack(t, methodCode(t, context->method));
               ++i)
          {
            frame2.set(localSize(t, context->method) + i, Frame::Integer);
          }

          compile(t, &frame2, exceptionHandlerIp(eh), true);
          if (UNLIKELY(t->exception)) return 0;

          eventIndex = calculateFrameMaps(t, context, 0, eventIndex);
        }
      }

      assert(t, progress);
    }
  }

  while (context->dirtyRoots) {
    context->dirtyRoots = false;
    calculateFrameMaps(t, context, 0, 0);
  }

  return finish(t, context);
}

void
compile(MyThread* t, object method);

void*
compileMethod2(MyThread* t)
{
  object node = findCallNode(t, *static_cast<void**>(t->stack));
  PROTECT(t, node);

  object target = callNodeTarget(t, node);
  PROTECT(t, target);

  if (callNodeVirtualCall(t, node)) {
    target = resolveTarget(t, t->stack, target);
  }

  if (LIKELY(t->exception == 0)) {
    compile(t, target);
  }

  if (UNLIKELY(t->exception)) {
    return 0;
  } else {
    if (callNodeVirtualCall(t, node)) {
      classVtable
        (t, objectClass
         (t, resolveThisPointer(t, t->stack, target)), methodOffset(t, target))
        = &singletonValue(t, methodCompiled(t, target), 0);
    } else {
      Context context(t);
      context.assembler->updateCall
        (reinterpret_cast<void*>(callNodeAddress(t, node)),
         &singletonValue(t, methodCompiled(t, target), 0));
    }
    return &singletonValue(t, methodCompiled(t, target), 0);
  }
}

void* FORCE_ALIGN
compileMethod(MyThread* t)
{
  void* r = compileMethod2(t);

  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return r;
  }
}

uint64_t
invokeNative2(MyThread* t, object method)
{
  PROTECT(t, method);

  assert(t, methodFlags(t, method) & ACC_NATIVE);

  initClass(t, methodClass(t, method));
  if (UNLIKELY(t->exception)) return 0;

  if (methodCode(t, method) == 0) {
    void* function = resolveNativeMethod(t, method);
    if (UNLIKELY(function == 0)) {
      object message = makeString
        (t, "%s.%s%s",
         &byteArrayBody(t, className(t, methodClass(t, method)), 0),
         &byteArrayBody(t, methodName(t, method), 0),
         &byteArrayBody(t, methodSpec(t, method), 0));
      t->exception = makeUnsatisfiedLinkError(t, message);
      return 0;
    }

    object p = makePointer(t, function);
    set(t, method, MethodCode, p);
  }

  object class_ = methodClass(t, method);
  PROTECT(t, class_);

  unsigned footprint = methodParameterFootprint(t, method) + 1;
  if (methodFlags(t, method) & ACC_STATIC) {
    ++ footprint;
  }
  unsigned count = methodParameterCount(t, method) + 2;

  uintptr_t args[footprint];
  unsigned argOffset = 0;
  uint8_t types[count];
  unsigned typeOffset = 0;

  args[argOffset++] = reinterpret_cast<uintptr_t>(t);
  types[typeOffset++] = POINTER_TYPE;

  uintptr_t* sp = static_cast<uintptr_t*>(t->stack)
    + methodParameterFootprint(t, method);

  if (methodFlags(t, method) & ACC_STATIC) {
    args[argOffset++] = reinterpret_cast<uintptr_t>(&class_);
  } else {
    args[argOffset++] = reinterpret_cast<uintptr_t>(sp--);
  }
  types[typeOffset++] = POINTER_TYPE;

  MethodSpecIterator it
    (t, reinterpret_cast<const char*>
     (&byteArrayBody(t, methodSpec(t, method), 0)));
  
  while (it.hasNext()) {
    unsigned type = types[typeOffset++]
      = fieldType(t, fieldCode(t, *it.next()));

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      args[argOffset++] = *(sp--);
      break;

    case INT64_TYPE:
    case DOUBLE_TYPE: {
      memcpy(args + argOffset, sp - 1, 8);
      argOffset += (8 / BytesPerWord);
      sp -= 2;
    } break;

    case POINTER_TYPE: {
      if (*sp) {
        args[argOffset++] = reinterpret_cast<uintptr_t>(sp);
      } else {
        args[argOffset++] = 0;
      }
      -- sp;
    } break;

    default: abort(t);
    }
  }

  void* function = pointerValue(t, methodCode(t, method));
  unsigned returnCode = methodReturnCode(t, method);
  unsigned returnType = fieldType(t, returnCode);
  uint64_t result;

  if (DebugNatives) {
    fprintf(stderr, "invoke native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  if (methodFlags(t, method) & ACC_SYNCHRONIZED) {
    if (methodFlags(t, method) & ACC_STATIC) {
      acquire(t, methodClass(t, method));
    } else {
      acquire(t, *reinterpret_cast<object*>(args[0]));
    }
  }

  Reference* reference = t->reference;

  { ENTER(t, Thread::IdleState);

    result = t->m->system->call
      (function,
       args,
       types,
       count,
       footprint * BytesPerWord,
       returnType);
  }

  if (methodFlags(t, method) & ACC_SYNCHRONIZED) {
    if (methodFlags(t, method) & ACC_STATIC) {
      release(t, methodClass(t, method));
    } else {
      release(t, *reinterpret_cast<object*>(args[0]));
    }
  }

  if (DebugNatives) {
    fprintf(stderr, "return from native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  if (LIKELY(t->exception == 0)) {
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
      result = result;
      break;

    case ObjectField:
      result = static_cast<uintptr_t>(result) ? *reinterpret_cast<uintptr_t*>
        (static_cast<uintptr_t>(result)) : 0;
      break;

    case VoidField:
      result = 0;
      break;

    default: abort(t);
    }
  } else {
    result = 0;
  }

  while (t->reference != reference) {
    dispose(t, t->reference);
  }

  return result;
}

uint64_t FORCE_ALIGN
invokeNative(MyThread* t)
{
  if (t->trace->nativeMethod == 0) {
    object node = findCallNode(t, *static_cast<void**>(t->stack));
    object target = callNodeTarget(t, node);
    if (callNodeVirtualCall(t, node)) {
      target = resolveTarget(t, t->stack, target);
    }
    t->trace->nativeMethod = target;
  }

  uint64_t result = 0;

  if (LIKELY(t->exception == 0)) {
    result = invokeNative2(t, t->trace->nativeMethod);
  }

  t->trace->nativeMethod = 0;

  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return result;
  }
}

unsigned
frameMapIndex(MyThread* t, object method, int32_t offset)
{
  object map = codePool(t, methodCode(t, method));
  unsigned mapSize = frameObjectMapSize(t, method, map);
  unsigned indexSize = intArrayLength(t, map) - mapSize;
    
  unsigned bottom = 0;
  unsigned top = indexSize;
  for (unsigned span = top - bottom; span; span = top - bottom) {
    unsigned middle = bottom + (span / 2);
    int32_t v = intArrayBody(t, map, middle);
      
    if (offset == v) {
      return (indexSize * 32) + (frameSize(t, method) * middle);
    } else if (offset < v) {
      top = middle;
    } else {
      bottom = middle + 1;
    }
  }

  abort(t);
}

void
visitStackAndLocals(MyThread* t, Heap::Visitor* v, void* base, object method,
                    void* ip, void* calleeBase, unsigned argumentFootprint)
{
  unsigned count;
  if (calleeBase) {
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    unsigned height = static_cast<uintptr_t*>(base)
      - static_cast<uintptr_t*>(calleeBase) - 2;

    count = parameterFootprint + height - argumentFootprint;
  } else {
    count = frameSize(t, method);
  }
      
  if (count) {
    object map = codePool(t, methodCode(t, method));
    int index = frameMapIndex
      (t, method, difference
       (ip, &singletonValue(t, methodCompiled(t, method), 0)));

    for (unsigned i = 0; i < count; ++i) {
      int j = index + i;
      if ((intArrayBody(t, map, j / 32)
           & (static_cast<int32_t>(1) << (j % 32))))
      {
        v->visit(localObject(t, base, method, i));        
      }
    }
  }
}

void
visitStack(MyThread* t, Heap::Visitor* v)
{
  void* ip = t->ip;
  void* base = t->base;
  void** stack = static_cast<void**>(t->stack);
  if (ip == 0 and stack) {
    ip = *stack;
  }

  MyThread::CallTrace* trace = t->trace;
  void* calleeBase = 0;
  unsigned argumentFootprint = 0;

  while (stack) {
    object method = methodForIp(t, ip);
    if (method) {
      PROTECT(t, method);

      visitStackAndLocals
        (t, v, base, method, ip, calleeBase, argumentFootprint);

      calleeBase = base;
      argumentFootprint = methodParameterFootprint(t, method);

      stack = static_cast<void**>(base) + 1;
      if (stack) {
        ip = *stack;
      }
      base = *static_cast<void**>(base);
    } else if (trace) {
      calleeBase = 0;
      argumentFootprint = 0;
      base = trace->base;
      stack = static_cast<void**>(trace->stack);
      if (stack) {
        ip = *stack;
      }
      trace = trace->next;
    } else {
      break;
    }
  }
}

void
saveStackAndBase(MyThread* t, Assembler* a)
{
  Assembler::Register base(a->base());
  Assembler::Memory baseDst(a->thread(), difference(&(t->base), t));
  a->apply(Move, BytesPerWord, RegisterOperand, &base,
           MemoryOperand, &baseDst);

  Assembler::Register stack(a->stack());
  Assembler::Memory stackDst(a->thread(), difference(&(t->stack), t));
  a->apply(Move, BytesPerWord, RegisterOperand, &stack,
           MemoryOperand, &stackDst);
}

void
pushThread(MyThread*, Assembler* a)
{
  Assembler::Register thread(a->thread());

  if (a->argumentRegisterCount()) {
    Assembler::Register arg(a->argumentRegister(0));
    a->apply(Move, BytesPerWord, RegisterOperand, &thread,
             RegisterOperand, &arg);
  } else {
    a->apply(Push, BytesPerWord, RegisterOperand, &thread);
  }
}

void
popThread(MyThread*, Assembler* a)
{
  if (a->argumentRegisterCount() == 0) {
    ResolvedPromise bpwPromise(BytesPerWord);
    Assembler::Constant bpw(&bpwPromise);
    Assembler::Register stack(a->stack());
    a->apply(Add, BytesPerWord, ConstantOperand, &bpw,
             RegisterOperand, &stack);
  }
}

class ArgumentList {
 public:
  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, bool indirectObjects, va_list arguments):
    t(static_cast<MyThread*>(t)),
    array(array),
    objectMask(objectMask),
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
      case 'D':
        addLong(va_arg(arguments, uint64_t));
        break;

      default:
        addInt(va_arg(arguments, uint32_t));
        break;        
      }
    }
  }

  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, object arguments):
    t(static_cast<MyThread*>(t)),
    array(array),
    objectMask(objectMask),
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
        addLong(cast<int64_t>(objectArrayBody(t, arguments, index++),
                              BytesPerWord));
        break;

      default:
        addInt(cast<int32_t>(objectArrayBody(t, arguments, index++),
                             BytesPerWord));
        break;        
      }
    }
  }

  void addObject(object v) {
    array[position] = reinterpret_cast<uintptr_t>(v);
    objectMask[position] = true;
    ++ position;
  }

  void addInt(uintptr_t v) {
    array[position] = v;
    objectMask[position] = false;
    ++ position;
  }

  void addLong(uint64_t v) {
    if (BytesPerWord == 8) {
      memcpy(array + position + 1, &v, 8);
    } else {
      // push words in reverse order, since they will be switched back
      // when pushed on the stack:
      array[position] = v >> 32;
      array[position + 1] = v;
    }
    objectMask[position] = false;
    objectMask[position + 1] = false;
    position += 2;
  }

  MyThread* t;
  uintptr_t* array;
  bool* objectMask;
  unsigned position;

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(ArgumentList* list): Protector(list->t), list(list) { }

    virtual void visit(Heap::Visitor* v) {
      for (unsigned i = 0; i < list->position; ++i) {
        if (list->objectMask[i]) {
          v->visit(reinterpret_cast<object*>(list->array + i));
        }
      }
    }

    ArgumentList* list;
  } protector;
};

object
invoke(Thread* thread, object method, ArgumentList* arguments)
{
  MyThread* t = static_cast<MyThread*>(thread);
  
  unsigned returnCode = methodReturnCode(t, method);
  unsigned returnType = fieldType(t, returnCode);

  uint64_t result;

  { MyThread::CallTrace trace(t);

    if (methodFlags(t, method) & ACC_NATIVE) {
      trace.nativeMethod = method;
    }

    result = vmInvoke
      (t, &singletonValue(t, methodCompiled(t, method), 0), arguments->array,
       arguments->position, returnType);
  }

  if (t->exception) { 
    if (t->backupHeap) {
      collect(t, Heap::MinorCollection);
    }
    return 0;
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
  };

  return r;
}

class SegFaultHandler: public System::SignalHandler {
 public:
  SegFaultHandler(): m(0) { }

  virtual bool handleSignal(void** ip, void** base, void** stack,
                            void** thread)
  {
    MyThread* t = static_cast<MyThread*>(m->localThread->get());
    if (t->state == Thread::ActiveState) {
      object node = methodForIp(t, *ip);
      if (node) {
        void* oldIp = t->ip;
        void* oldBase = t->base;
        void* oldStack = t->stack;

        t->ip = *ip;
        t->base = *base;
        t->stack = *stack;

        ensure(t, FixedSizeOfNullPointerException + traceSize(t));

        t->tracing = true;
        t->exception = makeNullPointerException(t);
        t->tracing = false;

        findUnwindTarget(t, ip, base, stack);

        t->ip = oldIp;
        t->base = oldBase;
        t->stack = oldStack;

        *thread = t;
        return true;
      }
    }
    return false;
  }

  Machine* m;
};

class MyProcessor;

MyProcessor*
processor(MyThread* t);

class MyProcessor: public Processor {
 public:
  class CodeAllocator: public Allocator {
   public:
    CodeAllocator(System* s): s(s) { }

    virtual void* tryAllocate(unsigned size) {
      return s->tryAllocateExecutable(size);
    }

    virtual void* allocate(unsigned size) {
      void* p = tryAllocate(size);
      expect(s, p);
      return p;
    }

    virtual void free(const void* p, unsigned size) {
      s->freeExecutable(p, size);
    }

    System* s;
  };

  MyProcessor(System* s, Allocator* allocator):
    s(s),
    allocator(allocator),
    defaultThunk(0),
    nativeThunk(0),
    aioobThunk(0),
    callTable(0),
    callTableSize(0),
    methodTree(0),
    methodTreeSentinal(0),
    codeAllocator(s),
    codeZone(s, &codeAllocator, 64 * 1024)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    MyThread* t = new (m->heap->allocate(sizeof(MyThread)))
      MyThread(m, javaThread, parent);
    t->init();
    return t;
  }

  virtual object
  makeMethod(vm::Thread* t,
             uint8_t vmFlags,
             uint8_t returnCode,
             uint8_t parameterCount,
             uint8_t parameterFootprint,
             uint16_t flags,
             uint16_t offset,
             object name,
             object spec,
             object class_,
             object code)
  {
    return vm::makeMethod
      (t, vmFlags, returnCode, parameterCount, parameterFootprint, flags,
       offset, name, spec, class_, code,
       ::defaultThunk(static_cast<MyThread*>(t)));
  }

  virtual object
  makeClass(vm::Thread* t,
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
            unsigned vtableLength)
  {
    return vm::makeClass
      (t, flags, vmFlags, arrayDimensions, fixedSize, arrayElementSize,
       objectMask, name, super, interfaceTable, virtualTable, fieldTable,
       methodTable, staticTable, loader, vtableLength, false);
  }

  virtual void
  initVtable(Thread* t, object c)
  {
    void* compiled = &singletonBody
      (t, ::defaultThunk(static_cast<MyThread*>(t)), 0);

    for (unsigned i = 0; i < classLength(t, c); ++i) {
      classVtable(t, c, i) = compiled;
    }
  }

  virtual void
  initClass(Thread* t, object c)
  {
    PROTECT(t, c);
    
    ACQUIRE(t, t->m->classLock);
    if (classVmFlags(t, c) & NeedInitFlag
        and (classVmFlags(t, c) & InitFlag) == 0)
    {
      classVmFlags(t, c) |= InitFlag;
      invoke(t, classInitializer(t, c), 0);
      if (t->exception) {
        t->exception = makeExceptionInInitializerError(t, t->exception);
      }
      classVmFlags(t, c) &= ~(NeedInitFlag | InitFlag);
    }
  }

  virtual void
  visitObjects(Thread* vmt, Heap::Visitor* v)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    if (t == t->m->rootThread) {
      v->visit(&defaultThunk);
      v->visit(&nativeThunk);
      v->visit(&aioobThunk);
      v->visit(&thunkTable);
      v->visit(&callTable);
      v->visit(&methodTree);
      v->visit(&methodTreeSentinal);
    }

    for (MyThread::CallTrace* trace = t->trace; trace; trace = trace->next) {
      v->visit(&(trace->nativeMethod));
    }

    for (Reference* r = t->reference; r; r = r->next) {
      v->visit(&(r->target));
    }

    visitStack(t, v);
  }

  virtual void
  walkStack(Thread* vmt, StackVisitor* v)
  {
    MyThread* t = static_cast<MyThread*>(vmt);

    MyStackWalker walker(t);
    walker.walk(v);
  }

  virtual int
  lineNumber(Thread* vmt, object method, int ip)
  {
    return findLineNumber(static_cast<MyThread*>(vmt), method, ip);
  }

  virtual object*
  makeLocalReference(Thread* vmt, object o)
  {
    if (o) {
      MyThread* t = static_cast<MyThread*>(vmt);
      PROTECT(t, o);

      Reference* r = new (t->m->heap->allocate(sizeof(Reference)))
        Reference(o, &(t->reference));

      return &(r->target);
    } else {
      return 0;
    }
  }

  virtual void
  disposeLocalReference(Thread* t, object* r)
  {
    if (r) {
      vm::dispose(t, reinterpret_cast<Reference*>(r));
    }
  }

  virtual object
  invokeArray(Thread* t, object method, object this_, object arguments)
  {
    if (UNLIKELY(t->exception)) return 0;

    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterFootprint(t, method);
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list(t, array, objectMask, this_, spec, arguments);
    
    PROTECT(t, method);

    compile(static_cast<MyThread*>(t), method);

    if (LIKELY(t->exception == 0)) {
      return ::invoke(t, method, &list);
    }

    return 0;
  }

  virtual object
  invokeList(Thread* t, object method, object this_, bool indirectObjects,
             va_list arguments)
  {
    if (UNLIKELY(t->exception)) return 0;

    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));
    
    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterFootprint(t, method);
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, spec, indirectObjects, arguments);

    PROTECT(t, method);

    compile(static_cast<MyThread*>(t), method);

    if (LIKELY(t->exception == 0)) {
      return ::invoke(t, method, &list);
    }

    return 0;
  }

  virtual object
  invokeList(Thread* t, const char* className, const char* methodName,
             const char* methodSpec, object this_, va_list arguments)
  {
    if (UNLIKELY(t->exception)) return 0;

    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    unsigned size = parameterFootprint(t, methodSpec, false);
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, methodSpec, false, arguments);

    object method = resolveMethod(t, className, methodName, methodSpec);
    if (LIKELY(t->exception == 0)) {
      assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

      PROTECT(t, method);
      
      compile(static_cast<MyThread*>(t), method);

      if (LIKELY(t->exception == 0)) {
        return ::invoke(t, method, &list);
      }
    }

    return 0;
  }

  virtual void dispose(Thread* vmt) {
    MyThread* t = static_cast<MyThread*>(vmt);

    while (t->reference) {
      vm::dispose(t, t->reference);
    }

    t->m->heap->free(t, sizeof(*t));
  }

  virtual void dispose() {
    codeZone.dispose();
    
    s->handleSegFault(0);

    allocator->free(this, sizeof(*this));
  }

  virtual object getStackTrace(Thread* vmt, Thread* vmTarget) {
    MyThread* t = static_cast<MyThread*>(vmt);
    MyThread* target = static_cast<MyThread*>(vmTarget);
    MyProcessor* p = processor(t);

    class Visitor: public System::ThreadVisitor {
     public:
      Visitor(MyThread* t, MyProcessor* p, MyThread* target):
        t(t), p(p), target(target)
      { }

      virtual void visit(void* ip, void* base, void* stack) {
        void* oldIp = target->ip;
        void* oldBase = target->base;
        void* oldStack = target->stack;

        if (methodForIp(t, ip)) {
          target->ip = ip;
          target->base = base;
          target->stack = stack;
        } else {
          uint8_t* thunkStart = reinterpret_cast<uint8_t*>
            (&singletonValue(t, p->thunkTable, 0));
          uint8_t* thunkEnd = thunkStart + (p->thunkSize * ThunkCount);

          if (static_cast<uint8_t*>(ip) >= thunkStart
              and static_cast<uint8_t*>(ip) < thunkEnd)
          {
            target->ip = *static_cast<void**>(stack);
            target->base = base;
            target->stack = stack;            
          }
        }

        ensure(t, traceSize(target));

        t->tracing = true;
        trace = makeTrace(t, target);
        t->tracing = false;

        target->ip = oldIp;
        target->base = oldBase;
        target->stack = oldStack;
      }

      MyThread* t;
      MyProcessor* p;
      MyThread* target;
      object trace;
    } visitor(t, p, target);

    t->m->system->visit(t->systemThread, target->systemThread, &visitor);

    if (t->backupHeap) {
      PROTECT(t, visitor.trace);

      collect(t, Heap::MinorCollection);
    }

    return visitor.trace;
  }
  
  System* s;
  Allocator* allocator;
  object defaultThunk;
  object nativeThunk;
  object aioobThunk;
  object thunkTable;
  unsigned thunkSize;
  object callTable;
  unsigned callTableSize;
  object methodTree;
  object methodTreeSentinal;
  SegFaultHandler segFaultHandler;
  CodeAllocator codeAllocator;
  Zone codeZone;
};

intptr_t
getThunk(MyThread* t, Thunk thunk)
{
  MyProcessor* p = processor(t);
  
  return reinterpret_cast<intptr_t>
    (&singletonValue(t, p->thunkTable, (thunk * p->thunkSize) / BytesPerWord));
}

void
compileThunks(MyThread* t, MyProcessor* p)
{
  class ThunkContext {
   public:
    class MyPromise: public Promise {
     public:
      MyPromise(): resolved_(false) { }

      virtual int64_t value() {
        return value_;
      }
    
      virtual bool resolved() {
        return resolved_;
      }
    
      int64_t value_;
      bool resolved_;
    };

    ThunkContext(MyThread* t): context(t) { }

    Context context;
    MyPromise promise;
  };

  ThunkContext defaultContext(t);

  { Assembler* a = defaultContext.context.assembler;
      
    saveStackAndBase(t, a);
    pushThread(t, a);
  
    defaultContext.promise.resolved_ = true;
    defaultContext.promise.value_ = reinterpret_cast<intptr_t>(compileMethod);

    Assembler::Constant proc(&(defaultContext.promise));
    a->apply(LongCall, BytesPerWord, ConstantOperand, &proc);

    popThread(t, a);

    Assembler::Register result(a->returnLow());
    a->apply(Jump, BytesPerWord, RegisterOperand, &result);
  }

  ThunkContext nativeContext(t);

  { Assembler* a = nativeContext.context.assembler;
      
    saveStackAndBase(t, a);
    pushThread(t, a);

    nativeContext.promise.resolved_ = true;
    nativeContext.promise.value_ = reinterpret_cast<intptr_t>(invokeNative);

    Assembler::Constant proc(&(nativeContext.promise));
    a->apply(LongCall, BytesPerWord, ConstantOperand, &proc);
  
    popThread(t, a);

    a->apply(Return);
  }

  ThunkContext aioobContext(t);

  { Assembler* a = aioobContext.context.assembler;
      
    saveStackAndBase(t, a);
    pushThread(t, a);

    aioobContext.promise.resolved_ = true;
    aioobContext.promise.value_ = reinterpret_cast<intptr_t>
      (throwArrayIndexOutOfBounds);

    Assembler::Constant proc(&(aioobContext.promise));
    a->apply(LongCall, BytesPerWord, ConstantOperand, &proc);
  }

  ThunkContext tableContext(t);

  { Assembler* a = tableContext.context.assembler;
  
    saveStackAndBase(t, a);

    Assembler::Constant proc(&(tableContext.promise));
    a->apply(LongJump, BytesPerWord, ConstantOperand, &proc);
  }

  p->thunkSize = pad(tableContext.context.assembler->length());

  expect(t, codeZone(t)->ensure
         (codeSingletonSizeInBytes
          (t, defaultContext.context.assembler->length())
          + codeSingletonSizeInBytes
          (t, nativeContext.context.assembler->length())
          + codeSingletonSizeInBytes
          (t, aioobContext.context.assembler->length())
          + codeSingletonSizeInBytes
          (t, p->thunkSize * ThunkCount)));

  p->defaultThunk = finish(t, defaultContext.context.assembler, "default");
  p->nativeThunk = finish(t, nativeContext.context.assembler, "native");
  p->aioobThunk = finish(t, aioobContext.context.assembler, "aioob");

  p->thunkTable = allocateCode(t, p->thunkSize * ThunkCount);
  uint8_t* start = reinterpret_cast<uint8_t*>
    (&singletonValue(t, p->thunkTable, 0));

  if (Verbose) {
    logCompile(start, p->thunkSize * ThunkCount, 0, "thunkTable", 0);
    fprintf(stderr, "thunk size: %d\n", p->thunkSize);
  }

  tableContext.promise.resolved_ = true;

#define THUNK(s)                                                \
  tableContext.promise.value_ = reinterpret_cast<intptr_t>(s);  \
  tableContext.context.assembler->writeTo(start);               \
  start += p->thunkSize;

#include "thunks.cpp"

#undef THUNK
}

MyProcessor*
processor(MyThread* t)
{
  MyProcessor* p = static_cast<MyProcessor*>(t->m->processor);
  if (p->callTable == 0) {
    ACQUIRE(t, t->m->classLock);

    if (p->callTable == 0) {
      p->callTable = makeArray(t, 128, true);

      p->methodTree = p->methodTreeSentinal = makeTreeNode(t, 0, 0, 0);
      set(t, p->methodTree, TreeNodeLeft, p->methodTreeSentinal);
      set(t, p->methodTree, TreeNodeRight, p->methodTreeSentinal);

      compileThunks(t, p);

      p->segFaultHandler.m = t->m;
      expect(t, t->m->system->success
             (t->m->system->handleSegFault(&(p->segFaultHandler))));
    }
  }
  return p;
}

object
defaultThunk(MyThread* t)
{
  return processor(t)->defaultThunk;
}

object
nativeThunk(MyThread* t)
{
  return processor(t)->nativeThunk;
}

object
aioobThunk(MyThread* t)
{
  return processor(t)->aioobThunk;
}

void
compile(MyThread* t, object method)
{
  MyProcessor* p = processor(t);

  PROTECT(t, method);

  ACQUIRE(t, t->m->classLock);
    
  if (methodCompiled(t, method) == p->defaultThunk) {
    initClass(t, methodClass(t, method));
    if (UNLIKELY(t->exception)) return;

    if (methodCompiled(t, method) == p->defaultThunk) {
      object compiled;
      if (methodFlags(t, method) & ACC_NATIVE) {
        compiled = p->nativeThunk;
      } else {
        Context context(t, method);
        compiled = compile(t, &context);
        if (UNLIKELY(t->exception)) return;
      }

      set(t, method, MethodCompiled, compiled);

      if ((methodFlags(t, method) & ACC_NATIVE) == 0) {
        if (DebugMethodTree) {
          fprintf(stderr, "insert method at %p\n",
                  &singletonValue(t, methodCompiled(t, method), 0));
        }
          
        methodTree(t) = treeInsert
          (t, methodTree(t), method, methodTreeSentinal(t),
           compareMethodBounds);
      }

      if (methodVirtual(t, method)) {
        classVtable(t, methodClass(t, method), methodOffset(t, method))
          = &singletonValue(t, methodCompiled(t, method), 0);
      }
    }
  }
}

object
findCallNode(MyThread* t, void* address)
{
  if (DebugCallTable) {
    fprintf(stderr, "find call node %p\n", address);
  }

  MyProcessor* p = processor(t);
  object table = p->callTable;

  intptr_t key = reinterpret_cast<intptr_t>(address);
  unsigned index = static_cast<uintptr_t>(key) 
    & (arrayLength(t, table) - 1);

  for (object n = arrayBody(t, table, index);
       n; n = callNodeNext(t, n))
  {
    intptr_t k = callNodeAddress(t, n);

    if (k == key) {
      return n;
    }
  }

  return 0;
}

object
resizeTable(MyThread* t, object oldTable, unsigned newLength)
{
  PROTECT(t, oldTable);

  object oldNode = 0;
  PROTECT(t, oldNode);

  object newTable = makeArray(t, newLength, true);
  PROTECT(t, newTable);

  for (unsigned i = 0; i < arrayLength(t, oldTable); ++i) {
    for (oldNode = arrayBody(t, oldTable, i);
         oldNode;
         oldNode = callNodeNext(t, oldNode))
    {
      intptr_t k = callNodeAddress(t, oldNode);

      unsigned index = k & (newLength - 1);

      object newNode = makeCallNode
        (t, callNodeAddress(t, oldNode),
         callNodeTarget(t, oldNode),
         callNodeVirtualCall(t, oldNode),
         arrayBody(t, newTable, index));

      set(t, newTable, ArrayBody + (index * BytesPerWord), newNode);
    }
  }

  return newTable;
}

void
insertCallNode(MyThread* t, object node)
{
  if (DebugCallTable) {
    fprintf(stderr, "insert call node %p\n",
            reinterpret_cast<void*>(callNodeAddress(t, node)));
  }

  MyProcessor* p = processor(t);
  PROTECT(t, node);

  ++ p->callTableSize;

  if (p->callTableSize >= arrayLength(t, p->callTable) * 2) { 
    p->callTable = resizeTable
      (t, p->callTable, arrayLength(t, p->callTable) * 2);
  }

  intptr_t key = callNodeAddress(t, node);
  unsigned index = static_cast<uintptr_t>(key)
    & (arrayLength(t, p->callTable) - 1);

  set(t, node, CallNodeNext, arrayBody(t, p->callTable, index));
  set(t, p->callTable, ArrayBody + (index * BytesPerWord), node);
}

object&
methodTree(MyThread* t)
{
  return processor(t)->methodTree;
}

object
methodTreeSentinal(MyThread* t)
{
  return processor(t)->methodTreeSentinal;
}

Zone*
codeZone(MyThread* t) {
  return &(processor(t)->codeZone);
}

} // namespace

namespace vm {

Processor*
makeProcessor(System* system, Allocator* allocator)
{
  return new (allocator->allocate(sizeof(MyProcessor)))
    MyProcessor(system, allocator);
}

} // namespace vm
