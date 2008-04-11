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
      ip(t->ip),
      base(t->base),
      stack(t->stack),
      nativeMethod(0),
      next(t->trace)
    {
      t->trace = this;
      t->ip = 0;
      t->base = 0;
      t->stack = 0;
    }

    ~CallTrace() {
      t->stack = stack;
      t->base = base;
      t->ip = ip;
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
    fprintf(stderr, "find 0x%lx in (0x%lx,0x%lx)\n", ip, start,
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
  class MyProtector: public Thread::Protector {
   public:
    MyProtector(MyStackWalker* walker):
      Protector(walker->t), walker(walker)
    { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(walker->method_));
      v->visit(&(walker->nativeMethod));
    }

    MyStackWalker* walker;
  };

  MyStackWalker(MyThread* t):
    t(t),
    ip_(t->ip ? t->ip : (t->stack ? *static_cast<void**>(t->stack) : 0)),
    base(t->base),
    stack(t->stack),
    trace(t->trace),
    nativeMethod(trace->nativeMethod),
    method_(ip_ ? methodForIp(t, ip_) : 0),
    protector(this)
  { }

  MyStackWalker(MyStackWalker* w):
    t(w->t),
    ip_(w->ip_),
    base(w->base),
    stack(w->stack),
    trace(w->trace),
    nativeMethod(w->nativeMethod),
    method_(w->method_),
    protector(this)
  { }

  virtual void walk(Processor::StackVisitor* v) {
    if (stack == 0) {
      return;
    }

    if (not v->visit(this)) {
      return;
    }

    for (MyStackWalker it(this); it.next();) {
      MyStackWalker walker(it);
      if (not v->visit(&walker)) {
        break;
      }
    }
  }
    
  bool next() {
    if (nativeMethod) {
      nativeMethod = 0;
    } else {
      stack = static_cast<void**>(base) + 1;
      base = *static_cast<void**>(base);
      ip_ = *static_cast<void**>(stack);
      method_ = methodForIp(t, *static_cast<void**>(stack));
      if (method_ == 0) {
        if (trace and trace->stack) {
          base = trace->base;
          stack = static_cast<void**>(trace->stack);
          nativeMethod = trace->nativeMethod;
          trace = trace->next;
        } else {
          return false;
        }
      }
    }
    return true;
  }

  virtual object method() {
    if (nativeMethod) {
      return nativeMethod;
    } else {
      return method_;
    }
  }

  virtual int ip() {
    if (nativeMethod) {
      return 0;
    } else {
      return reinterpret_cast<intptr_t>(ip_) - reinterpret_cast<intptr_t>
        (&singletonValue(t, methodCompiled(t, method_), 0));
    }
  }

  virtual unsigned count() {
    class Visitor: public Processor::StackVisitor {
     public:
      Visitor(): count(0) { }

      virtual bool visit(Processor::StackWalker*) {
        ++ count;
        return true;
      }

      unsigned count;
    } v;

    MyStackWalker walker(this);
    walker.walk(&v);
    
    return v.count;
  }

  MyThread* t;
  void* ip_;
  void* base;
  void* stack;
  MyThread::CallTrace* trace;
  object nativeMethod;
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

class TraceElement: public Compiler::TraceHandler {
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
  intptr_t addressValue;
  object target;
  bool virtualCall;
  TraceElement* next;
  uintptr_t map[0];
};

inline Stack*
push(Compiler* c, Stack* s, Operand* v)
{
  if (BytesPerWord == 8) {
    return c->push8(s, v);
  } else {
    return c->push4(s, v);
  }
}

inline Stack*
pop(Compiler* c, Stack* s, Operand* v)
{
  if (BytesPerWord == 8) {
    return c->pop8(s, v);
  } else {
    return c->pop4(s, v);
  }
}

inline void
mov(Compiler* c, Operand* src, Operand* dst)
{
  if (BytesPerWord == 8) {
    c->mov8(src, dst);
  } else {
    c->mov4(src, dst);
  }
}

inline Operand*
result(Compiler* c)
{
  if (BytesPerWord == 8) {
    return c->result8();
  } else {
    return c->result4();
  }
}

inline void
returnW(Compiler* c, Operand* v)
{
  if (BytesPerWord == 8) {
    c->return8(v);
  } else {
    c->return4(v);
  }
}

inline void
cmp(Compiler* c, Operand* src, Operand* dst)
{
  if (BytesPerWord == 8) {
    c->cmp8(src, dst);
  } else {
    c->cmp4(src, dst);
  }
}

inline void
and_(Compiler* c, Operand* src, Operand* dst)
{
  if (BytesPerWord == 8) {
    c->and8(src, dst);
  } else {
    c->and4(src, dst);
  }
}

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
stackMapSizeInWords(MyThread* t, object method)
{
  return ceiling(codeMaxStack(t, methodCode(t, method)), BitsPerWord)
    * BytesPerWord;
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

class Context {
 public:
  class MyProtector: public Thread::Protector {
   public:
    MyProtector(Context* c): Protector(c->t), c(c) { }

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

  Context(MyThread* t, object method, uint8_t* indirectCaller):
    t(t),
    zone(t->m->system, t->m->heap, false, 16 * 1024),
    c(makeCompiler(t->m->system, t->m->heap, &zone, indirectCaller)),
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
    t(t),
    zone(t->m->system, t->m->heap, false, LikelyPageSizeInBytes),
    c(makeCompiler(t->m->system, t->m->heap, &zone, 0)),
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
    c->dispose();
  }

  MyThread* t;
  Zone zone;
  Compiler* c;
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
  Frame(Context* context, uintptr_t* stackMap):
    context(context),
    t(context->t),
    c(context->c),
    stack(0),
    stackMap(stackMap),
    ip(0),
    sp(localSize()),
    level(0)
  {
    memset(stackMap, 0,
           stackMapSizeInWords(t, context->method) * BytesPerWord);
  }

  Frame(Frame* f, uintptr_t* stackMap):
    context(f->context),
    t(context->t),
    c(context->c),
    stack(f->stack),
    stackMap(stackMap),
    ip(f->ip),
    sp(f->sp),
    level(f->level + 1)
  {
    memcpy(stackMap, f->stackMap,
           stackMapSizeInWords(t, context->method) * BytesPerWord);

    if (level > 1) {
      context->eventLog.append(PushEvent);
    }
  }

  ~Frame() {
    if (level > 1 and t->exception == 0) {
      context->eventLog.append(PopEvent);      
    }
  }

  Operand* append(object o) {
    Promise* p = c->poolAppend(0);
    context->objectPool = new
      (context->zone.allocate(sizeof(PoolElement)))
      PoolElement(o, p, context->objectPool);
    return c->absolute(p);
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

  void mark(unsigned index) {
    assert(t, index < frameSize());

    context->eventLog.append(MarkEvent);
    context->eventLog.append2(index);

    int si = index - localSize();
    if (si >= 0) {
      markBit(stackMap, si);
    }
  }

  void clear(unsigned index) {
    assert(t, index < frameSize());

    context->eventLog.append(ClearEvent);
    context->eventLog.append2(index);

    int si = index - localSize();
    if (si >= 0) {
      clearBit(stackMap, si);
    }
  }

  unsigned get(unsigned index) {
    assert(t, index < frameSize());
    int si = index - localSize();
    assert(t, si >= 0);
    return getBit(stackMap, si);
  }

  void pushedInt() {
    assert(t, sp + 1 <= frameSize());
    assert(t, get(sp) == 0);
    ++ sp;
  }

  void pushedObject() {
    assert(t, sp + 1 <= frameSize());
    mark(sp++);
  }

  void popped(unsigned count) {
    assert(t, sp >= count);
    assert(t, sp - count >= localSize());
    while (count) {
      clear(-- sp);
      -- count;
    }
  }
  
  void poppedInt() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) == 0);
    -- sp;
  }
  
  void poppedObject() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) != 0);
    clear(-- sp);
  }

  void storedInt(unsigned index) {
    assert(t, index < localSize());
    clear(index);
  }

  void storedObject(unsigned index) {
    assert(t, index < localSize());
    mark(index);
  }

  void dupped() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 1 >= localSize());
    if (get(sp - 1)) {
      mark(sp);
    }
    ++ sp;
  }

  void duppedX1() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 2 >= localSize());

    unsigned b2 = get(sp - 2);
    unsigned b1 = get(sp - 1);

    if (b2) {
      mark(sp - 1);
    } else {
      clear(sp - 1);
    }

    if (b1) {
      mark(sp - 2);
      mark(sp);
    } else {
      clear(sp - 2);
    }

    ++ sp;
  }

  void duppedX2() {
    assert(t, sp + 1 <= frameSize());
    assert(t, sp - 3 >= localSize());

    unsigned b3 = get(sp - 3);
    unsigned b2 = get(sp - 2);
    unsigned b1 = get(sp - 1);

    if (b3) {
      mark(sp - 2);
    } else {
      clear(sp - 2);
    }

    if (b2) {
      mark(sp - 1);
    } else {
      clear(sp - 1);
    }

    if (b1) {
      mark(sp - 3);
      mark(sp);
    } else {
      clear(sp - 3);
    }

    ++ sp;
  }

  void dupped2() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 2 >= localSize());

    unsigned b2 = get(sp - 2);
    unsigned b1 = get(sp - 1);

    if (b2) {
      mark(sp);
    }

    if (b1) {
      mark(sp + 1);
    }

    sp += 2;
  }

  void dupped2X1() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 3 >= localSize());

    unsigned b3 = get(sp - 3);
    unsigned b2 = get(sp - 2);
    unsigned b1 = get(sp - 1);

    if (b3) {
      mark(sp - 1);
    } else {
      clear(sp - 1);
    }

    if (b2) {
      mark(sp - 3);
      mark(sp);
    } else {
      clear(sp - 3);
    }

    if (b1) {
      mark(sp - 2);
      mark(sp + 1);
    } else {
      clear(sp - 2);
    }

    sp += 2;
  }

  void dupped2X2() {
    assert(t, sp + 2 <= frameSize());
    assert(t, sp - 4 >= localSize());

    unsigned b4 = get(sp - 4);
    unsigned b3 = get(sp - 3);
    unsigned b2 = get(sp - 2);
    unsigned b1 = get(sp - 1);

    if (b4) {
      mark(sp - 2);
    } else {
      clear(sp - 2);
    }

    if (b3) {
      mark(sp - 1);
    } else {
      clear(sp - 1);
    }

    if (b2) {
      mark(sp - 4);
      mark(sp);
    } else {
      clear(sp - 4);
    }

    if (b1) {
      mark(sp - 3);
      mark(sp + 1);
    } else {
      clear(sp - 3);
    }

    sp += 2;
  }

  void swapped() {
    assert(t, sp - 2 >= localSize());

    bool savedBit = get(sp - 1);
    if (get(sp - 2)) {
      mark(sp - 1);
    } else {
      clear(sp - 1);
    }

    if (savedBit) {
      mark(sp - 2);
    } else {
      clear(sp - 2);
    }
  }

  Operand* machineIp(unsigned logicalIp) {
    return c->promiseConstant(c->machineIp(logicalIp));
  }

  void visitLogicalIp(unsigned ip) {
    context->eventLog.append(IpEvent);
    context->eventLog.append2(ip);
  }

  void startLogicalIp(unsigned ip) {
    c->startLogicalIp(ip);
    this->ip = ip;
  }

  void topIntToLong() {
    dup();
    if (BytesPerWord == 4) {
      c->mov4To8(c->stack(stack, 0), c->stack(stack, 0));
    }
  }

  void topLongToInt() {
    mov(c, c->stack(stack, 0), c->stack(stack, 1));
    stack = c->pop(stack, 1);
    poppedInt();
  }

  void pushInt(Operand* o) {
    stack = push(c, stack, o);
    pushedInt();
  }

  void pushInt1(Operand* o) {
    stack = c->push1(stack, o);
    pushedInt();
  }

  void pushInt2(Operand* o) {
    stack = c->push2(stack, o);
    pushedInt();
  }

  void pushInt2z(Operand* o) {
    stack = c->push2z(stack, o);
    pushedInt();
  }

  void pushInt4(Operand* o) {
    stack = c->push4(stack, o);
    pushedInt();
  }

  void pushAddress(Operand* o) {
    stack = push(c, stack, o);
    pushedInt();
  }

  void pushObject(Operand* o) {
    stack = push(c, stack, o);
    pushedObject();
  }

  void pushObject() {
    stack = c->pushed(stack, 1);
    pushedObject();
  }

  void pushLong(Operand* o) {
    if (BytesPerWord == 8) {
      stack = c->push(stack, 1);
    }
    stack = c->push8(stack, o);

    pushedInt();
    pushedInt();    
  }

  void pop(unsigned count) {
    popped(count);
    stack = c->pop(stack, count);
  }

  Operand* topInt() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) == 0);
    return c->stack(stack, 0);
  }

  Operand* topLong() {
    assert(t, sp >= 2);
    assert(t, sp - 2 >= localSize());
    assert(t, get(sp - 1) == 0);
    assert(t, get(sp - 2) == 0);
    return c->stack(stack, 0);
  }

  Operand* topObject() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    assert(t, get(sp - 1) != 0);
    return c->stack(stack, 0);
  }

  Operand* popInt() {
    Operand* tmp = c->temporary();
    popInt(tmp);
    return tmp;
  }

  Operand* popInt4() {
    Operand* tmp = c->temporary();
    popInt4(tmp);
    return tmp;
  }

  Operand* popLong() {
    Operand* tmp = c->temporary();
    popLong(tmp);
    return tmp;
  }

  Operand* popObject() {
    Operand* tmp = c->temporary();
    popObject(tmp);
    return tmp;
  }

  void popInt(Operand* o) {
    stack = ::pop(c, stack, o);
    poppedInt();
  }

  void popInt4(Operand* o) {
    stack = c->pop4(stack, o);
    poppedInt();
  }

  void popLong(Operand* o) {
    stack = c->pop8(stack, o);
    if (BytesPerWord == 8) {
      stack = c->pop(stack, 1);
    }

    poppedInt();
    poppedInt();
  }

  void popObject(Operand* o) {
    stack = ::pop(c, stack, o);
    poppedObject();
  }

  void loadInt(unsigned index) {
    assert(t, index < localSize());
    pushInt(c->memory(c->base(), localOffset(t, index, context->method)));
  }

  void loadLong(unsigned index) {
    assert(t, index < static_cast<unsigned>
           (localSize() - 1));
    pushLong(c->memory(c->base(), localOffset(t, index + 1, context->method)));
  }

  void loadObject(unsigned index) {
    assert(t, index < localSize());
    pushObject(c->memory(c->base(), localOffset(t, index, context->method)));
  }

  void storeInt(unsigned index) {
    popInt(c->memory(c->base(), localOffset(t, index, context->method)));
    storedInt(index);
  }

  void storeLong(unsigned index) {
    popLong(c->memory(c->base(), localOffset(t, index + 1, context->method)));
    storedInt(index);
    storedInt(index + 1);
  }

  void storeObject(unsigned index) {
    popObject(c->memory(c->base(), localOffset(t, index, context->method)));
    storedObject(index);
  }

  void storeObjectOrAddress(unsigned index) {
    stack = ::pop
      (c, stack, c->memory(c->base(), localOffset(t, index, context->method)));

    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize());
    if (get(sp - 1)) {
      storedObject(index);
    } else {
      storedInt(index);
    }

    popped(1);
  }

  void dup() {
    stack = push(c, stack, c->stack(stack, 0));
    dupped();
  }

  void dupX1() {
    stack = push(c, stack, c->stack(stack, 0));
    mov(c, c->stack(stack, 2), c->stack(stack, 1));
    mov(c, c->stack(stack, 0), c->stack(stack, 2));

    duppedX1();
  }

  void dupX2() {
    stack = push(c, stack, c->stack(stack, 0));
    mov(c, c->stack(stack, 2), c->stack(stack, 1));
    mov(c, c->stack(stack, 3), c->stack(stack, 2));
    mov(c, c->stack(stack, 0), c->stack(stack, 3));

    duppedX2();
  }

  void dup2() {
    stack = push(c, stack, c->stack(stack, 1));
    stack = push(c, stack, c->stack(stack, 1));

    dupped2();
  }

  void dup2X1() {
    stack = push(c, stack, c->stack(stack, 1));
    stack = push(c, stack, c->stack(stack, 1));
    mov(c, c->stack(stack, 4), c->stack(stack, 2));
    mov(c, c->stack(stack, 1), c->stack(stack, 4));
    mov(c, c->stack(stack, 0), c->stack(stack, 3));

    dupped2X1();
  }

  void dup2X2() {
    stack = push(c, stack, c->stack(stack, 1));
    stack = push(c, stack, c->stack(stack, 1));
    mov(c, c->stack(stack, 5), c->stack(stack, 3));
    mov(c, c->stack(stack, 4), c->stack(stack, 2));
    mov(c, c->stack(stack, 1), c->stack(stack, 5));
    mov(c, c->stack(stack, 0), c->stack(stack, 4));

    dupped2X2();
  }

  void swap() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);
    Operand* tmp = c->temporary();

    mov(c, s0, tmp);
    mov(c, s1, s0);
    mov(c, tmp, s1);

    c->release(tmp);

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
  Stack* stack;
  uintptr_t* stackMap;
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

void
removeCallNode(MyThread* t, object node);

void*
findExceptionHandler(Thread* t, object method, void* ip)
{
  PROTECT(t, method);

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
        object catchType = 0;
        if (arrayBody(t, table, i + 1)) {
          object e = t->exception;
          t->exception = 0;
          PROTECT(t, e);

          PROTECT(t, table);
          PROTECT(t, index);

          catchType = resolveClassInObject
            (t, table, ArrayBody + ((i + 1) * BytesPerWord));

          if (catchType) {
            t->exception = e;
          } else {
            // can't find what we're supposed to catch - move on.
            continue;
          }
        }

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
  if (ip) {
    t->ip = 0;
  } else {
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

intptr_t
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

intptr_t
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

intptr_t
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

intptr_t
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

uint64_t
addDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) + bitsToDouble(b));
}

uint64_t
subtractDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) - bitsToDouble(b));
}

uint64_t
multiplyDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) * bitsToDouble(b));
}

uint64_t
divideDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(bitsToDouble(a) / bitsToDouble(b));
}

uint64_t
moduloDouble(uint64_t b, uint64_t a)
{
  return doubleToBits(fmod(bitsToDouble(a), bitsToDouble(b)));
}

uint64_t
negateDouble(uint64_t a)
{
  return doubleToBits(- bitsToDouble(a));
}

uint32_t
doubleToFloat(int64_t a)
{
  return floatToBits(static_cast<float>(bitsToDouble(a)));
}

int32_t
doubleToInt(int64_t a)
{
  return static_cast<int32_t>(bitsToDouble(a));
}

int64_t
doubleToLong(int64_t a)
{
  return static_cast<int64_t>(bitsToDouble(a));
}

uint32_t
addFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) + bitsToFloat(b));
}

uint32_t
subtractFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) - bitsToFloat(b));
}

uint32_t
multiplyFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) * bitsToFloat(b));
}

uint32_t
divideFloat(uint32_t b, uint32_t a)
{
  return floatToBits(bitsToFloat(a) / bitsToFloat(b));
}

uint32_t
moduloFloat(uint32_t b, uint32_t a)
{
  return floatToBits(fmod(bitsToFloat(a), bitsToFloat(b)));
}

uint32_t
negateFloat(uint32_t a)
{
  return floatToBits(- bitsToFloat(a));
}

int64_t
divideLong(int64_t b, int64_t a)
{
  return a / b;
}

int64_t
moduloLong(int64_t b, int64_t a)
{
  return a % b;
}

uint64_t
floatToDouble(int32_t a)
{
  return doubleToBits(static_cast<double>(bitsToFloat(a)));
}

int32_t
floatToInt(int32_t a)
{
  return static_cast<int32_t>(bitsToFloat(a));
}

int64_t
floatToLong(int32_t a)
{
  return static_cast<int64_t>(bitsToFloat(a));
}

uint64_t
intToDouble(int32_t a)
{
  return doubleToBits(static_cast<double>(a));
}

uint32_t
intToFloat(int32_t a)
{
  return floatToBits(static_cast<float>(a));
}

uint64_t
longToDouble(int64_t a)
{
  return doubleToBits(static_cast<double>(a));
}

uint32_t
longToFloat(int64_t a)
{
  return floatToBits(static_cast<float>(a));
}

object FORCE_ALIGN
makeBlankObjectArray(Thread* t, object class_, int32_t length)
{
  return makeObjectArray(t, class_, length, true);
}

object FORCE_ALIGN
makeBlankArray(Thread* t, object (*constructor)(Thread*, uintptr_t, bool),
               int32_t length)
{
  return constructor(t, length, true);
}

uintptr_t
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
makeMultidimensionalArray(MyThread* t, object class_, uintptr_t* stack,
                          int32_t dimensions)
{
  object r = makeMultidimensionalArray2(t, class_, stack, dimensions);
  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return r;
  }
}

void NO_RETURN FORCE_ALIGN
throwArrayIndexOutOfBounds(MyThread* t, object array, int32_t index)
{
  object message = makeString
    (t, "array of length %d indexed at %d", arrayLength(t, array), index);
  t->exception = makeArrayIndexOutOfBoundsException(t, message);
  unwind(t);
}

void NO_RETURN FORCE_ALIGN
throwNegativeArraySize(MyThread* t, int32_t length)
{
  object message = makeString(t, "%d", length);
  t->exception = makeArrayIndexOutOfBoundsException(t, message);
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

void
pushReturnValue(MyThread* t, Frame* frame, unsigned code)
{
  Compiler* c = frame->c;

  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField: {
    Operand* result = c->result4();
    frame->pushInt(result);
    c->release(result);
  } break;

  case ObjectField: {
    Operand* result = ::result(c);
    frame->pushObject(result);
    c->release(result);
  } break;

  case LongField:
  case DoubleField: {
    Operand* result = c->result8();
    frame->pushLong(result);
    c->release(result);
  } break;

  case VoidField:
    break;

  default:
    abort(t);
  }
}

object
defaultCompiled(MyThread* t);

object
nativeCompiled(MyThread* t);

void
compileDirectInvoke(MyThread* t, Frame* frame, object target)
{
  Compiler* c = frame->c;

  if (methodFlags(t, target) & ACC_NATIVE) {
    c->call
      (c->constant
       (reinterpret_cast<intptr_t>
        (&singletonBody(t, nativeCompiled(t), 0))),
       frame->trace(target, false));
  } else if (methodCompiled(t, target) == defaultCompiled(t)) {
    c->alignedCall
      (c->constant
       (reinterpret_cast<intptr_t>
        (&singletonBody(t, defaultCompiled(t), 0))),
       frame->trace(target, false));
  } else {
    c->call
      (c->constant
       (reinterpret_cast<intptr_t>
        (&singletonBody(t, methodCompiled(t, target), 0))),
       frame->trace(0, false));
  }

  frame->pop(methodParameterFootprint(t, target));

  pushReturnValue(t, frame, methodReturnCode(t, target));
}

void
handleMonitorEvent(MyThread* t, Frame* frame, intptr_t function)
{
  Compiler* c = frame->c;
  object method = frame->context->method;

  if (methodFlags(t, method) & ACC_SYNCHRONIZED) {
    Operand* lock;
    if (methodFlags(t, method) & ACC_STATIC) {
      lock = frame->append(methodClass(t, method));
    } else {
      lock = c->memory
        (c->base(), localOffset(t, savedTargetIndex(t, method), method));
    }
    
    c->indirectCall
      (c->constant(function),
       frame->trace(0, false),
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
    mov(c, c->memory(c->base(), localOffset(t, 0, method)),
        c->memory(c->base(), localOffset(t, index, method)));
    frame->mark(index);
  }

  handleMonitorEvent
    (t, frame, reinterpret_cast<intptr_t>(acquireMonitorForObject));
}

void
handleExit(MyThread* t, Frame* frame)
{
  handleMonitorEvent
    (t, frame, reinterpret_cast<intptr_t>(releaseMonitorForObject));
}

void
compile(MyThread* t, Frame* initialFrame, unsigned ip,
        bool exceptionHandler = false)
{
  uintptr_t stackMap[stackMapSizeInWords(t, initialFrame->context->method)];
  Frame myFrame(initialFrame, stackMap);
  Frame* frame = &myFrame;
  Compiler* c = frame->c;
  Context* context = frame->context;

  object code = methodCode(t, context->method);
  PROTECT(t, code);
    
  while (ip < codeLength(t, code)) {
    frame->visitLogicalIp(ip);

    if (context->visitTable[ip] ++) {
      // we've already visited this part of the code
      return;
    }

    frame->startLogicalIp(ip);

    if (exceptionHandler) {
      exceptionHandler = false;
      
      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(gcIfNecessary)),
         frame->trace(0, false),
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
      Operand* index = frame->popInt4();
      Operand* array = frame->popObject();

      if (CheckArrayBounds) {
        Operand* load = c->label();
        Operand* throw_ = c->label();

        c->cmp4(c->constant(0), index);
        c->jl(throw_);

        c->cmp4(c->memory(array, ArrayLength, 0, 1), index);
        c->jl(load);

        c->mark(throw_);

        c->indirectCallNoReturn
          (c->constant(reinterpret_cast<intptr_t>(throwArrayIndexOutOfBounds)),
           frame->trace(0, false),
           3, c->thread(), array, index);

        c->mark(load);
      }

      switch (instruction) {
      case aaload:
        frame->pushObject
          (c->memory(array, ArrayBody, index, BytesPerWord));
        break;

      case faload:
      case iaload:
        frame->pushInt4(c->memory(array, ArrayBody, index, 4));
        break;

      case baload:
        frame->pushInt1(c->memory(array, ArrayBody, index, 1));
        break;

      case caload:
        frame->pushInt2z(c->memory(array, ArrayBody, index, 2));
        break;

      case daload:
      case laload:
        frame->pushLong(c->memory(array, ArrayBody, index, 8));
        break;

      case saload:
        frame->pushInt2(c->memory(array, ArrayBody, index, 2));
        break;
      }

      c->release(index);
      c->release(array);
    } break;

    case aastore:
    case bastore:
    case castore:
    case dastore:
    case fastore:
    case iastore:
    case lastore:
    case sastore: {
      Operand* value;
      if (instruction == dastore or instruction == lastore) {
        value = frame->popLong();
      } else if (instruction == aastore) {
        value = frame->popObject();
      } else {
        value = frame->popInt();
      }

      Operand* index = frame->popInt4();
      Operand* array = frame->popObject();

      if (CheckArrayBounds) {
        Operand* store = c->label();
        Operand* throw_ = c->label();

        c->cmp4(c->constant(0), index);
        c->jl(throw_);

        c->cmp4(c->memory(array, ArrayLength, 0, 1), index);
        c->jl(store);

        c->mark(throw_);

        c->indirectCallNoReturn
          (c->constant(reinterpret_cast<intptr_t>(throwArrayIndexOutOfBounds)),
           frame->trace(0, false),
           3, c->thread(), array, index);

        c->mark(store);
      }

      switch (instruction) {
      case aastore: {
        c->shl4(c->constant(log(BytesPerWord)), index);
        c->add4(c->constant(ArrayBody), index);
          
        c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(setMaybeNull)),
           frame->trace(0, false),
           4, c->thread(), array, index, value);
      } break;

      case fastore:
      case iastore:
        c->mov4(value, c->memory(array, ArrayBody, index, 4));
        break;

      case bastore:
        c->mov1(value, c->memory(array, ArrayBody, index, 1));
        break;

      case castore:
      case sastore:
        c->mov2(value, c->memory(array, ArrayBody, index, 2));
        break;

      case dastore:
      case lastore:
        c->mov8(value, c->memory(array, ArrayBody, index, 8));
        break;
      }

      c->release(value);
      c->release(index);
      c->release(array);
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

      Operand* nonnegative = c->label();

      Operand* length = frame->popInt4();
      c->cmp4(c->constant(0), length);
      c->jge(nonnegative);

      c->indirectCallNoReturn
        (c->constant(reinterpret_cast<intptr_t>(throwNegativeArraySize)),
         frame->trace(0, false),
         2, c->thread(), length);

      c->mark(nonnegative);

      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(makeBlankObjectArray)),
         frame->trace(0, false),
         3, c->thread(), frame->append(class_), length);

      Operand* result = ::result(c);

      c->release(length);

      frame->pushObject(result);
      c->release(result);
    } break;

    case areturn: {
      handleExit(t, frame);
      Operand* result = frame->popObject();
      returnW(c, result);
      c->release(result);
    } return;

    case arraylength: {
      Operand* array = frame->popObject();
      frame->pushInt4(c->memory(array, ArrayLength, 0, 1));
      c->release(array);
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
      Operand* e = frame->popObject();
      c->indirectCallNoReturn
        (c->constant(reinterpret_cast<intptr_t>(throw_)),
         frame->trace(0, false),
         2, c->thread(), e);
      c->release(e);
    } return;

    case bipush:
      frame->pushInt
        (c->constant(static_cast<int8_t>(codeBody(t, code, ip++))));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* instance = frame->topObject();

      Operand* classOperand = frame->append(class_);

      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(checkCast)),
         frame->trace(0, false),
         3, c->thread(), classOperand, instance);
    } break;

    case d2f: {
      Operand* a = frame->popLong();

      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(doubleToFloat)), 2, 0, a);
      c->release(a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case d2i: {
      Operand* a = frame->popLong();

      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(doubleToInt)), 2, 0, a);
      c->release(a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case d2l: {
      Operand* a = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(doubleToLong)), 2, 0, a);
      c->release(a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case dadd: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(addDouble)), 4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case dcmpg: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(compareDoublesG)),
         4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case dcmpl: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();

      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(compareDoublesL)),
         4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case dconst_0:
      frame->pushLong(c->constant(doubleToBits(0.0)));
      break;
      
    case dconst_1:
      frame->pushLong(c->constant(doubleToBits(1.0)));
      break;

    case ddiv: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
         (c->constant(reinterpret_cast<intptr_t>(divideDouble)),
          4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case dmul: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(multiplyDouble)),
         4, 0, a, 0, b);
      c->release(a);
      c->release(b);
      
      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case dneg: {
      Operand* a = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(negateDouble)), 2, 0, a);
      c->release(a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case vm::drem: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(moduloDouble)), 4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case dsub: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(subtractDouble)),
         4, 0, a, 0, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
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
      Operand* a = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(floatToDouble)), 1, a);
      c->release(a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case f2i: {
      Operand* a = frame->popInt();
      
      c->directCall
         (c->constant(reinterpret_cast<intptr_t>(floatToInt)), 1, a);
      c->release(a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case f2l: {
      Operand* a = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(floatToLong)), 1, a);
      c->release(a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
    } break;

    case fadd: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(addFloat)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case fcmpg: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(compareFloatsG)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case fcmpl: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(compareFloatsL)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
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
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(divideFloat)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case fmul: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(multiplyFloat)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case fneg: {
      Operand* a = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(negateFloat)), 1, a);
      c->release(a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case vm::frem: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(moduloFloat)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case fsub: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(subtractFloat)), 2, a, b);
      c->release(a);
      c->release(b);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
    } break;

    case getfield:
    case getstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* table;

      if (instruction == getstatic) {
        c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(tryInitClass)),
           frame->trace(0, false),
           2, c->thread(), frame->append(fieldClass(t, field)));

        table = frame->append(classStaticTable(t, fieldClass(t, field)));
      } else {
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        frame->pushInt1(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case CharField:
        frame->pushInt2z(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case ShortField:
        frame->pushInt2(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case FloatField:
      case IntField:
        frame->pushInt4(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case DoubleField:
      case LongField:
        frame->pushLong(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case ObjectField:
        frame->pushObject(c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      default:
        abort(t);
      }

      if (instruction == getfield) {
        c->release(table);
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
      Operand* top = frame->topInt();
      c->mov1ToW(top, top);
    } break;

    case i2c: {
      Operand* top = frame->topInt();
      c->mov2zToW(top, top);
    } break;

    case i2d: {
      Operand* a = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(intToDouble)), 1, a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
      c->release(a);
    } break;

    case i2f: {
      Operand* a = frame->popInt();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(intToFloat)), 1, a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
      c->release(a);
    } break;

    case i2l:
      frame->topIntToLong();
      break;

    case i2s: {
      Operand* top = frame->topInt();
      c->mov2ToW(top, top);
    } break;
      
    case iadd: {
      Operand* a = frame->popInt();
      c->add4(a, frame->topInt());
      c->release(a);
    } break;
      
    case iand: {
      Operand* a = frame->popInt();
      c->and4(a, frame->topInt());
      c->release(a);
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
      Operand* a = frame->popInt();
      c->div4(a, frame->topInt());
      c->release(a);
    } break;

    case if_acmpeq:
    case if_acmpne: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = frame->popObject();
      Operand* b = frame->popObject();
      cmp(c, a, b);
      c->release(a);
      c->release(b);

      Operand* target = frame->machineIp(newIp);
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
        
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      c->cmp4(a, b);
      c->release(a);
      c->release(b);

      Operand* target = frame->machineIp(newIp);
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

      Operand* a = frame->popInt();
      c->cmp4(c->constant(0), a);
      c->release(a);

      Operand* target = frame->machineIp(newIp);
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

      Operand* a = frame->popObject();
      cmp(c, c->constant(0), a);
      c->release(a);

      Operand* target = frame->machineIp(newIp);
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

      c->add4(c->constant(count),
              c->memory(c->base(), localOffset(t, index, context->method)));
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
      Operand* a = frame->popInt();
      c->mul4(a, frame->topInt());
      c->release(a);
    } break;

    case ineg: {
      c->neg4(frame->topInt());
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* instance = frame->popObject();

      Operand* classOperand = frame->append(class_);

      c->directCall
         (c->constant(reinterpret_cast<intptr_t>(instanceOf)),
          3, c->thread(), classOperand, instance);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
      c->release(instance);
    } break;

    case invokeinterface: {
      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint = methodParameterFootprint(t, target);

      unsigned instance = parameterFootprint - 1;

      c->indirectCall
         (c->constant
          (reinterpret_cast<intptr_t>(findInterfaceMethodFromInstance)),
          frame->trace(0, false),
          3, c->thread(), frame->append(target),
          c->stack(frame->stack, instance));

      Operand* result = ::result(c);
      c->call(result, frame->trace(target, true));
      c->release(result);

      frame->pop(parameterFootprint);

      pushReturnValue(t, frame, methodReturnCode(t, target));
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

      Operand* instance = c->stack(frame->stack, parameterFootprint - 1);
      Operand* class_ = c->temporary();
      
      mov(c, c->memory(instance, 0, 0, 1), class_);
      and_(c, c->constant(PointerMask), class_);

      c->call(c->memory(class_, offset, 0, 1), frame->trace(target, true));

      c->release(class_);

      frame->pop(parameterFootprint);

      pushReturnValue(t, frame, methodReturnCode(t, target));
    } break;

    case ior: {
      Operand* a = frame->popInt();
      c->or4(a, frame->topInt());
      c->release(a);
    } break;

    case irem: {
      Operand* a = frame->popInt();
      c->rem4(a, frame->topInt());
      c->release(a);
    } break;

    case ireturn:
    case freturn: {
      handleExit(t, frame);
      Operand* a = frame->popInt();
      c->return4(a);
      c->release(a);
    } return;

    case ishl: {
      Operand* a = frame->popInt();
      c->shl4(a, frame->topInt());
      c->release(a);
    } break;

    case ishr: {
      Operand* a = frame->popInt();
      c->shr4(a, frame->topInt());
      c->release(a);
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
      Operand* a = frame->popInt();
      c->sub4(a, frame->topInt());
      c->release(a);
    } break;

    case iushr: {
      Operand* a = frame->popInt();
      c->ushr4(a, frame->topInt());
      c->release(a);
    } break;

    case ixor: {
      Operand* a = frame->popInt();
      c->xor4(a, frame->topInt());
      c->release(a);
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

      frame->pushAddress(frame->machineIp(ip));
      c->jmp(frame->machineIp(newIp));

      // NB: we assume that the stack will look the same on return
      // from the subroutine as at call time.
      compile(t, frame, newIp);
      if (UNLIKELY(t->exception)) return;

      frame->pop(1);
    } break;

    case l2d: {
      Operand* a = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(longToDouble)), 2, 0, a);

      Operand* result = c->result8();
      frame->pushLong(result);
      c->release(result);
      c->release(a);
    } break;

    case l2f: {
      Operand* a = frame->popLong();
      
      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(longToDouble)), 2, 0, a);

      Operand* result = c->result4();
      frame->pushInt(result);
      c->release(result);
      c->release(a);
    } break;

    case l2i:
      frame->topLongToInt();
      break;

    case ladd: {
      Operand* a = frame->popLong();
      c->add8(a, frame->topLong());
      c->release(a);
    } break;

    case land: {
      Operand* a = frame->popLong();
      c->and8(a, frame->topLong());
      c->release(a);
    } break;

    case lcmp: {
      Operand* next = c->label();
      Operand* less = c->label();
      Operand* greater = c->label();

      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      Operand* result = c->temporary();
          
      c->cmp8(a, b);
      c->release(a);
      c->release(b);

      c->jl(less);
      c->jg(greater);

      c->mov4(c->constant(0), result);
      c->jmp(next);
          
      c->mark(less);
      c->mov4(c->constant(-1), result);
      c->jmp(next);

      c->mark(greater);
      c->mov4(c->constant(1), result);

      c->mark(next);
      frame->pushInt(result);

      c->release(result);
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
      Operand* a = frame->popLong();
      c->div8(a, frame->topLong());
      c->release(a);
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
      Operand* a = frame->popLong();
      c->mul8(a, frame->topLong());
      c->release(a);
    } break;

    case lneg:
      c->neg8(frame->topLong());
      break;

    case lookupswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      Operand* key = frame->popInt4();
    
      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));

      Operand* default_ = c->absolute
        (c->poolAppendPromise(c->machineIp(defaultIp)));

      int32_t pairCount = codeReadInt32(t, code, ip);

      Operand* start = 0;
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

      c->directCall
        (c->constant(reinterpret_cast<intptr_t>(lookUpAddress)),
         4, key, start, c->constant(pairCount), default_);

      Operand* result = ::result(c);
      c->jmp(result);
      c->release(result);

      c->release(key);

      for (int32_t i = 0; i < pairCount; ++i) {
        compile(t, frame, ipTable[i]);
        if (UNLIKELY(t->exception)) return;
      }

      ip = defaultIp;
    } break;

    case lor: {
      Operand* a = frame->popLong();
      c->or8(a, frame->topLong());
      c->release(a);
    } break;

    case lrem: {
      Operand* a = frame->popLong();
      c->rem8(a, frame->topLong());
      c->release(a);
    } break;

    case lreturn:
    case dreturn: {
      handleExit(t, frame);
      Operand* a = frame->popLong();
      c->return8(a);
      c->release(a);
    } return;

    case lshl: {
      Operand* a = frame->popInt();
      c->shl8(a, frame->topLong());
      c->release(a);
    } break;

    case lshr: {
      Operand* a = frame->popInt();
      c->shr8(a, frame->topLong());
      c->release(a);
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
      Operand* a = frame->popLong();
      c->sub8(a, frame->topLong());
      c->release(a);
    } break;

    case lushr: {
      Operand* a = frame->popInt();
      c->ushr8(a, frame->topLong());
      c->release(a);
    } break;

    case lxor: {
      Operand* a = frame->popLong();
      c->xor8(a, frame->topLong());
      c->release(a);
    } break;

    case monitorenter: {
      Operand* a = frame->popObject();
      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(acquireMonitorForObject)),
         frame->trace(0, false),
         2, c->thread(), a);
      c->release(a);
    } break;

    case monitorexit: {
      Operand* a = frame->popObject();
      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(releaseMonitorForObject)),
         frame->trace(0, false),
         2, c->thread(), a);
      c->release(a);
    } break;

    case multianewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      uint8_t dimensions = codeBody(t, code, ip++);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);

      Operand* stack = c->temporary();
      mov(c, c->stack(), stack);

      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(makeMultidimensionalArray)),
         frame->trace(0, false),
         4, c->thread(), frame->append(class_), stack,
         c->constant(dimensions));
      
      c->release(stack);

      Operand* result = ::result(c);

      frame->pop(dimensions);
      frame->pushObject(result);
      c->release(result);
    } break;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(makeNewWeakReference)),
           frame->trace(0, false),
           2, c->thread(), frame->append(class_));
      } else {
        c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(makeNew)),
           frame->trace(0, false),
           2, c->thread(), frame->append(class_));
      }

      Operand* result = ::result(c);
      frame->pushObject(result);
      c->release(result);
    } break;

    case newarray: {
      uint8_t type = codeBody(t, code, ip++);

      Operand* nonnegative = c->label();

      Operand* length = frame->popInt4();
      c->cmp4(c->constant(0), length);

      c->jge(nonnegative);

      c->indirectCallNoReturn
        (c->constant(reinterpret_cast<intptr_t>(throwNegativeArraySize)),
         frame->trace(0, false),
         2, c->thread(), length);

      c->mark(nonnegative);

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

      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(makeBlankArray)),
         frame->trace(0, false),
         3, c->thread(), c->constant(reinterpret_cast<intptr_t>(constructor)),
         length);
      
      Operand* result = ::result(c);

      c->release(length);

      frame->pushObject(result);
      c->release(result);
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
        c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(tryInitClass)),
           frame->trace(0, false),
           2, c->thread(), frame->append(fieldClass(t, field)));

        staticTable = classStaticTable(t, fieldClass(t, field));      
      }

      Operand* value;
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

      Operand* table;

      if (instruction == putstatic) {
        table = frame->append(staticTable);
      } else {
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        c->mov1(value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case CharField:
      case ShortField:
        c->mov2(value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;
            
      case FloatField:
      case IntField:
        c->mov4(value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case DoubleField:
      case LongField:
        c->mov8(value, c->memory(table, fieldOffset(t, field), 0, 1));
        break;

      case ObjectField:
        if (instruction == putfield) {
          c->indirectCall
            (c->constant(reinterpret_cast<intptr_t>(setMaybeNull)),
             frame->trace(0, false),
             4, c->thread(), table, c->constant(fieldOffset(t, field)), value);
        } else {
          c->directCall
            (c->constant(reinterpret_cast<intptr_t>(set)),
             4, c->thread(), table, c->constant(fieldOffset(t, field)), value);
        }
        break;

      default: abort(t);
      }

      if (instruction == putfield) {
        c->release(table);
      }
      c->release(value);
    } break;

    case ret:
      c->jmp
        (c->memory
         (c->base(), localOffset
          (t, codeBody(t, code, ip), context->method)));
      return;

    case return_:
      handleExit(t, frame);
      c->epilogue();
      c->ret();
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

      Operand* key = frame->popInt4();

      uint32_t defaultIp = base + codeReadInt32(t, code, ip);
      assert(t, defaultIp < codeLength(t, code));
      
      int32_t bottom = codeReadInt32(t, code, ip);
      int32_t top = codeReadInt32(t, code, ip);
        
      Operand* start = 0;
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

      Operand* defaultCase = c->label();
      
      c->cmp4(c->constant(bottom), key);
      c->jl(defaultCase);

      c->cmp4(c->constant(top), key);
      c->jg(defaultCase);

      c->sub4(c->constant(bottom), key);
      c->jmp(c->memory(start, 0, key, BytesPerWord));

      c->mark(defaultCase);
      c->jmp(frame->machineIp(defaultIp));

      c->release(key);

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

        c->add4(c->constant(count),
                c->memory(c->base(), localOffset(t, index, context->method)));
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
  fprintf(stderr, "%s.%s%s from %p to %p\n",
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

    object newTable = makeArray(t, length + 1, false);
    set(t, newTable, ArrayBody, newIndex);

    for (unsigned i = 0; i < length; ++i) {
      ExceptionHandler* oldHandler = exceptionHandlerTableBody
        (t, oldTable, i);

      intArrayBody(t, newIndex, i * 3)
        = c->machineIp(exceptionHandlerStart(oldHandler))->value(c) - start;

      intArrayBody(t, newIndex, (i * 3) + 1)
        = c->machineIp(exceptionHandlerEnd(oldHandler))->value(c) - start;

      intArrayBody(t, newIndex, (i * 3) + 2)
        = c->machineIp(exceptionHandlerIp(oldHandler))->value(c) - start;

      object type =
        (exceptionHandlerCatchType(oldHandler) ?
         singletonObject(t, codePool(t, code),
                         exceptionHandlerCatchType(oldHandler) - 1) : 0);

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
        = c->machineIp(lineNumberIp(oldLine))->value(c) - start;

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

Allocator*
codeAllocator(MyThread* t);

int
compareTraceElementPointers(const void* va, const void* vb)
{
  TraceElement* a = *static_cast<TraceElement* const*>(va);
  TraceElement* b = *static_cast<TraceElement* const*>(vb);
  if (a->addressValue > b->addressValue) {
    return 1;
  } else if (a->addressValue < b->addressValue) {
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

object
finish(MyThread* t, Context* context, const char* name)
{
  Compiler* c = context->c;

  unsigned count = ceiling(c->codeSize() + c->poolSize(), BytesPerWord);
  unsigned size = count + singletonMaskSize(count);
  object result = allocate3
    (t, codeAllocator(t), Machine::ImmortalAllocation,
     SingletonBody + (size * BytesPerWord), true, true);
  initSingleton(t, result, size, true);
  mark(t, result, 0);
  singletonMask(t, result)[0] = 1;

  uint8_t* start = reinterpret_cast<uint8_t*>(&singletonValue(t, result, 0));

  c->writeTo(start);

  if (context->method) {
    PROTECT(t, result);

    translateExceptionHandlerTable(t, c, methodCode(t, context->method),
                                   reinterpret_cast<intptr_t>(start));

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
        p->addressValue = p->address->value(c);

        if (p->target) {
          insertCallNode
            (t, makeCallNode
             (t, p->address->value(c), p->target, p->virtualCall, 0));
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

        intArrayBody(t, map, i) = static_cast<intptr_t>(p->addressValue)
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
      intptr_t offset = p->address->value(c)
        - reinterpret_cast<intptr_t>(start);

      singletonMarkObject(t, result, offset / BytesPerWord);

      set(t, result, SingletonBody + offset, p->value);
    }

    if (Verbose) {
      logCompile
        (start, c->codeSize(),
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
         "java/lang/System") == 0 and
        strcmp
        (reinterpret_cast<const char*>
         (&byteArrayBody(t, methodName(t, context->method), 0)),
         "getProperty") == 0)
    {
      asm("int3");
    }
  } else {
    if (Verbose) {
      logCompile(start, c->codeSize(), 0, name, 0);
    }
  }

  return result;
}

object
compile(MyThread* t, Context* context)
{
  Compiler* c = context->c;

//   fprintf(stderr, "compiling %s.%s%s\n",
//           &byteArrayBody(t, className(t, methodClass(t, context->method)), 0),
//           &byteArrayBody(t, methodName(t, context->method), 0),
//           &byteArrayBody(t, methodSpec(t, context->method), 0));

  c->prologue();

  unsigned footprint = methodParameterFootprint(t, context->method);
  unsigned locals = localSize(t, context->method);
  c->reserve(locals - footprint);

  uintptr_t stackMap[stackMapSizeInWords(t, context->method)];
  Frame frame(context, stackMap);

  unsigned index = 0;
  if ((methodFlags(t, context->method) & ACC_STATIC) == 0) {
    frame.mark(index++);    
  }

  for (MethodSpecIterator it
         (t, reinterpret_cast<const char*>
          (&byteArrayBody(t, methodSpec(t, context->method), 0)));
       it.hasNext();)
  {
    switch (*it.next()) {
    case 'L':
    case '[':
      frame.mark(index++);
      break;
      
    case 'J':
    case 'D':
      index += 2;
      break;

    default:
      ++ index;
      break;
    }
  }

  handleEntrance(t, &frame);

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

          uintptr_t stackMap[stackMapSizeInWords(t, context->method)];
          Frame frame2(&frame, stackMap);

          uintptr_t* roots = context->rootTable
            + (start * frameMapSizeInWords(t, context->method));

          for (unsigned i = 0; i < localSize(t, context->method); ++ i) {
            if (getBit(roots, i)) {
              frame2.mark(i);
            } else {
              frame2.clear(i);
            }
          }

          frame2.pushObject();

          for (unsigned i = 1;
               i < codeMaxStack(t, methodCode(t, context->method));
               ++i)
          {
            frame2.clear(localSize(t, context->method) + i);
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

  return finish(t, context, 0);
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
      { ACQUIRE(t, t->m->classLock);
        removeCallNode(t, node);
      }

      Context context(t);
      context.c->updateCall
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
    t->trace->nativeMethod = callNodeTarget(t, node);
    if (callNodeVirtualCall(t, node)) {
      t->trace->nativeMethod = resolveTarget
        (t, t->stack, t->trace->nativeMethod);
    }
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

object
compileDefault(MyThread* t, Context* context)
{
  Compiler* c = context->c;

  mov(c, c->base(), c->memory(c->thread(), difference(&(t->base), t)));
  mov(c, c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

  c->directCall
     (c->constant(reinterpret_cast<intptr_t>(compileMethod)),
      1, c->thread());

  Operand* result = ::result(c);
  c->jmp(result);
  c->release(result);

  return finish(t, context, "default");
}

object
compileNative(MyThread* t, Context* context)
{
  Compiler* c = context->c;

  mov(c, c->base(), c->memory(c->thread(), difference(&(t->base), t)));
  mov(c, c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

  c->directCall
    (c->constant(reinterpret_cast<intptr_t>(invokeNative)), 1, c->thread());
  
  c->ret();

  return finish(t, context, "native");
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

unsigned
traceSize(Thread* t)
{
  class Counter: public Processor::StackVisitor {
   public:
    Counter(Thread* t): t(t), count(0) { }

    virtual bool visit(Processor::StackWalker*) {
      ++ count;
      return true;
    }

    Thread* t;
    unsigned count;
  } counter(t);

  t->m->processor->walkStack(t, &counter);

  return FixedSizeOfArray + (counter.count * ArrayElementSizeOfArray)
    + (counter.count * FixedSizeOfTraceElement);
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
        t->ip = *ip;
        t->base = *base;
        t->stack = *stack;

        ensure(t, FixedSizeOfNullPointerException + traceSize(t));

        t->exception = makeNullPointerException(t);

        findUnwindTarget(t, ip, base, stack);
        *thread = t;
        return true;
      }
    }
    return false;
  }

  Machine* m;
};

class MyProcessor: public Processor {
 public:
  MyProcessor(System* s, Allocator* allocator):
    s(s),
    allocator(allocator),
    defaultCompiled(0),
    nativeCompiled(0),
    callTable(0),
    callTableSize(0),
    methodTree(0),
    methodTreeSentinal(0),
    indirectCaller(0),
    indirectCallerSize(0),
    codeAllocator(s, allocator, true, 64 * 1024)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    MyThread* t = new (m->heap->allocate(sizeof(MyThread), false))
      MyThread(m, javaThread, parent);
    t->init();
    return t;
  }

  object getDefaultCompiled(MyThread* t) {
    if (defaultCompiled == 0) {
      Context context(t);
      defaultCompiled = compileDefault(t, &context);
    }
    return defaultCompiled;
  }

  object getNativeCompiled(MyThread* t) {
    if (nativeCompiled == 0) {
      Context context(t);
      nativeCompiled = compileNative(t, &context);
    }
    return nativeCompiled;
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
       getDefaultCompiled(static_cast<MyThread*>(t)));
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
      (t, getDefaultCompiled(static_cast<MyThread*>(t)), 0);

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
      v->visit(&defaultCompiled);
      v->visit(&nativeCompiled);
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

      Reference* r = new (t->m->heap->allocate(sizeof(Reference), false))
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

    t->m->heap->free(t, sizeof(*t), false);
  }

  virtual void dispose() {
    codeAllocator.dispose();
    
    s->handleSegFault(0);

    allocator->free(this, sizeof(*this), false);
  }

  virtual object getStackTrace(Thread* vmt, Thread* vmTarget) {
    MyThread* t = static_cast<MyThread*>(vmt);
    MyThread* target = static_cast<MyThread*>(vmTarget);

    class Visitor: public System::ThreadVisitor {
     public:
      Visitor(MyThread* t, MyThread* target): t(t), target(target) { }

      virtual void visit(void* ip, void* base, void* stack) {
        ensure(t, traceSize(t));

        void* oldIp = target->ip;
        void* oldBase = target->ip;
        void* oldStack = target->stack;

        target->ip = ip;
        target->base = base;
        target->stack = stack;

        trace = makeTrace(t, target);

        target->ip = oldIp;
        target->base = oldBase;
        target->stack = oldStack;
      }

      MyThread* t;
      MyThread* target;
      object trace;
    } visitor(t, target);

    if (t->backupHeap) {
      PROTECT(t, visitor.trace);

      collect(t, Heap::MinorCollection);
    }

    return visitor.trace;
  }
  
  System* s;
  Allocator* allocator;
  object defaultCompiled;
  object nativeCompiled;
  object callTable;
  unsigned callTableSize;
  object methodTree;
  object methodTreeSentinal;
  uint8_t* indirectCaller;
  unsigned indirectCallerSize;
  SegFaultHandler segFaultHandler;
  Zone codeAllocator;
};

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

      Context context(t);
      Compiler* c = context.c;

      mov(c, c->base(), c->memory(c->thread(), difference(&(t->base), t)));
      mov(c, c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

      c->jmp(c->indirectTarget());

      p->indirectCallerSize = c->codeSize();
      p->indirectCaller = static_cast<uint8_t*>
        (p->codeAllocator.allocate(p->indirectCallerSize));
      c->writeTo(p->indirectCaller);

      if (Verbose) {
        logCompile(p->indirectCaller, c->codeSize(), 0, "indirect caller", 0);
      }

      p->segFaultHandler.m = t->m;
      expect(t, t->m->system->success
             (t->m->system->handleSegFault(&(p->segFaultHandler))));
    }
  }
  return p;
}

object
defaultCompiled(MyThread* t)
{
  return processor(t)->getDefaultCompiled(t);
}

object
nativeCompiled(MyThread* t)
{
  return processor(t)->getNativeCompiled(t);
}

void
compile(MyThread* t, object method)
{
  MyProcessor* p = processor(t);

  if (methodCompiled(t, method) == p->getDefaultCompiled(t)) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);
    
    if (methodCompiled(t, method) == p->getDefaultCompiled(t)) {
      initClass(t, methodClass(t, method));
      if (UNLIKELY(t->exception)) return;

      if (methodCompiled(t, method) == p->getDefaultCompiled(t)) {
        object compiled;
        if (methodFlags(t, method) & ACC_NATIVE) {
          compiled = p->getNativeCompiled(t);
        } else {
          Context context(t, method, p->indirectCaller);
          compiled = compile(t, &context);
          if (UNLIKELY(t->exception)) return;
        }

        set(t, method, MethodCompiled, compiled);

        if (methodVirtual(t, method)) {
          classVtable(t, methodClass(t, method), methodOffset(t, method))
            = &singletonValue(t, compiled, 0);
        }

        if ((methodFlags(t, method) & ACC_NATIVE) == 0) {
          if (DebugMethodTree) {
            fprintf(stderr, "insert method at %p\n",
                    &singletonValue(t, methodCompiled(t, method), 0));
          }
          
          methodTree(t) = treeInsert
            (t, methodTree(t), method, methodTreeSentinal(t),
             compareMethodBounds);
        }
      }
    }
  }
}

object
findCallNode(MyThread* t, void* address)
{
  if (DebugCallTable) {
    fprintf(stderr, "find trace node %p\n", address);
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
    fprintf(stderr, "insert trace node %p\n",
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

void
removeCallNode(MyThread* t, object node)
{
  if (DebugCallTable) {
    fprintf(stderr, "remove call node %p\n",
            reinterpret_cast<void*>(callNodeAddress(t, node)));
  }

  MyProcessor* p = processor(t);
  PROTECT(t, node);

  object oldNode = 0;
  PROTECT(t, oldNode);

  object newNode = 0;
  PROTECT(t, newNode);

  intptr_t key = callNodeAddress(t, node);
  unsigned index = static_cast<uintptr_t>(key)
    & (arrayLength(t, p->callTable) - 1);

  for (oldNode = arrayBody(t, p->callTable, index);
       oldNode;
       oldNode = callNodeNext(t, oldNode))
  {
    if (oldNode != node) {
      newNode = makeCallNode
        (t, callNodeAddress(t, oldNode),
         callNodeTarget(t, oldNode),
         callNodeVirtualCall(t, oldNode),
         newNode);
    }
  }

  set(t, p->callTable, ArrayBody + (index * BytesPerWord), newNode);

  -- p->callTableSize;

  if (p->callTableSize <= arrayLength(t, p->callTable) / 3) {
    p->callTable = resizeTable
      (t, p->callTable, arrayLength(t, p->callTable) / 2);
  }
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

Allocator*
codeAllocator(MyThread* t) {
  return &(processor(t)->codeAllocator);
}

} // namespace

namespace vm {

Processor*
makeProcessor(System* system, Allocator* allocator)
{
  return new (allocator->allocate(sizeof(MyProcessor), false))
    MyProcessor(system, allocator);
}

} // namespace vm
