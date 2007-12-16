#include "machine.h"
#include "util.h"
#include "vector.h"
#include "process.h"
#include "compiler.h"

using namespace vm;

extern "C" uint64_t
vmInvoke(void* thread, void* function, void* stack, unsigned stackSize,
         unsigned returnType);

extern "C" void
vmCall();

extern "C" void NO_RETURN
vmJump(void* address, void* base, void* stack, void* thread);

namespace {

const bool Verbose = true;
const bool DebugTraces = false;

class MyThread: public Thread {
 public:
  class CallTrace {
   public:
    CallTrace(MyThread* t):
      t(t),
      base(t->base),
      stack(t->stack),
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
    void* base;
    void* stack;
    CallTrace* next;
  };

  MyThread(Machine* m, object javaThread, Thread* parent):
    Thread(m, javaThread, parent),
    base(0),
    stack(0),
    trace(0),
    reference(0)
  { }

  void* base;
  void* stack;
  CallTrace* trace;
  Reference* reference;
};

object
resolveTarget(MyThread* t, void* stack, object method)
{
  if (method and methodVirtual(t, method)) {
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    object class_ = objectClass
      (t, reinterpret_cast<object*>(stack)[parameterFootprint]);

    if (classVmFlags(t, class_) & BootstrapFlag) {
      resolveClass(t, className(t, class_));
      if (UNLIKELY(t->exception)) return 0;
    }

    if (classFlags(t, methodClass(t, method)) & ACC_INTERFACE) {
      return findInterfaceMethod(t, method, class_);
    } else {
      return findMethod(t, method, class_);
    }
  }

  return method;
}

object
findTraceNode(MyThread* t, void* address);

void
insertTraceNode(MyThread* t, object node);

class MyStackWalker: public Processor::StackWalker {
 public:
  class MyProtector: public Thread::Protector {
   public:
    MyProtector(MyStackWalker* walker):
      Protector(walker->t), walker(walker)
    { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(walker->node));
      v->visit(&(walker->nativeMethod));
    }

    MyStackWalker* walker;
  };

  MyStackWalker(MyThread* t):
    t(t),
    base(t->base),
    stack(t->stack),
    trace(t->trace),
    node(stack ? findTraceNode(t, *static_cast<void**>(stack)) : 0),
    nativeMethod(resolveNativeMethod(t, stack, node)),
    protector(this)
  { }

  MyStackWalker(MyStackWalker* w):
    t(w->t),
    base(w->base),
    stack(w->stack),
    trace(w->trace),
    node(w->node),
    nativeMethod(w->nativeMethod),
    protector(this)
  { }

  static object resolveNativeMethod(MyThread* t, void* stack, object node) {
    if (node) {
      object target = resolveTarget(t, stack, traceNodeTarget(t, node));
      if (target and methodFlags(t, target) & ACC_NATIVE) {
        return target;
      }
    }
    return 0;
  }

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
      node = findTraceNode(t, *static_cast<void**>(stack));
      if (node == 0) {
        if (trace and trace->stack) {
          base = trace->base;
          stack = static_cast<void**>(trace->stack);
          trace = trace->next;
          node = findTraceNode(t, *static_cast<void**>(stack));
          nativeMethod = resolveNativeMethod(t, stack, node);
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
      return traceNodeMethod(t, node);
    }
  }

  virtual int ip() {
    if (nativeMethod) {
      return 0;
    } else {
      intptr_t start = reinterpret_cast<intptr_t>
        (&singletonValue(t, methodCompiled(t, traceNodeMethod(t, node)), 0));
      return traceNodeAddress(t, node) - start;
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
  void* base;
  void* stack;
  MyThread::CallTrace* trace;
  object node;
  object nativeMethod;
  MyProtector protector;
};

uintptr_t*
makeCodeMask(MyThread* t, unsigned length)
{
  unsigned size = ceiling(length, BytesPerWord) * BytesPerWord;
  uintptr_t* mask = static_cast<uintptr_t*>(t->m->system->allocate(size));
  memset(mask, 0, size);
  return mask;
}

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

class PoolElement {
 public:
  PoolElement(object value, Promise* address):
    value(value), address(address)
  { }

  object value;
  Promise* address;
};

class TraceElement {
 public:
  TraceElement(object target, Promise* machineIp, bool virtualCall):
    target(target), machineIp(machineIp), virtualCall(virtualCall) { }

  object target;
  Promise* machineIp;
  bool virtualCall;
  uint8_t map[0];
};

class Frame {
 public:
  class MyProtector: public Thread::Protector {
   public:
    MyProtector(Frame* frame): Protector(frame->t), frame(frame) { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(frame->method));

      if (next == 0) {
        Vector* pool = frame->objectPool;
        for (unsigned i = 0; i < pool->length(); i += sizeof(PoolElement)) {
          v->visit(&(pool->peek<PoolElement>(i)->value));
        }

        Vector* log = frame->traceLog;
        unsigned traceSize = traceSizeInBytes(t, frame->method);
        for (unsigned i = 0; i < log->length(); i += traceSize) {
          v->visit(&(pool->peek<TraceElement>(i)->target));
        }
      }
    }

    Frame* frame;
  };

  Frame(MyThread* t, Compiler* c, object method, uintptr_t* map,
        Vector* objectPool, Vector* traceLog):
    next(0),
    t(t),
    c(c),
    stack(0),
    method(method),
    map(map),
    objectPool(objectPool),
    traceLog(traceLog),
    codeMask(makeCodeMask(t, codeLength(t, methodCode(t, method)))),
    ip(0),
    sp(localSize(t, method)),
    protector(this)
  {
    memset(map, 0, mapSizeInBytes(t, method));
  }

  Frame(Frame* f, uintptr_t* map):
    next(f),
    t(f->t),
    c(f->c),
    stack(f->stack),
    method(f->method),
    map(map),
    objectPool(f->objectPool),
    traceLog(f->traceLog),
    codeMask(f->codeMask),
    ip(f->ip),
    sp(f->sp),
    protector(this)
  {
    memcpy(map, f->map, mapSizeInBytes(t, method));
  }

  ~Frame() {
    if (next == 0) {
      t->m->system->free(codeMask);
    }
  }

  Operand* append(object o) {
    Promise* p = c->poolAppend(0);
    new (objectPool->allocate(sizeof(PoolElement))) PoolElement(o, p);
    return c->absolute(p);
  }

  static unsigned parameterFootprint(Thread* t, object method) {
    return methodParameterFootprint(t, method);
  }

  static unsigned localSize(Thread* t, object method) {
    return codeMaxLocals(t, methodCode(t, method))
      - parameterFootprint(t, method);
  }

  static unsigned stackSize(Thread* t, object method) {
    return codeMaxStack(t, methodCode(t, method));
  }

  static unsigned mapSize(Thread* t, object method) {
    return stackSize(t, method) + localSize(t, method);
  }

  static unsigned mapSizeInWords(Thread* t, object method) {
    return ceiling(mapSize(t, method), BytesPerWord);
  }

  static unsigned mapSizeInBytes(Thread* t, object method) {
    return mapSizeInWords(t, method) * BytesPerWord;
  }

  static unsigned traceSizeInBytes(Thread* t, object method) {
    return sizeof(TraceElement) + mapSizeInWords(t, method);
  }

  void pushedInt() {
    assert(t, sp + 1 <= mapSize(t, method));
    assert(t, getBit(map, sp) == 0);
    ++ sp;
  }

  void pushedObject() {
    assert(t, sp + 1 <= mapSize(t, method));
    markBit(map, sp++);
  }

  void popped(unsigned count) {
    assert(t, sp >= count);
    assert(t, sp - count >= localSize(t, method));
    while (count) {
      clearBit(map, -- sp);
      -- count;
    }
  }
  
  void poppedInt() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize(t, method));
    assert(t, getBit(map, sp - 1) == 0);
    -- sp;
  }
  
  void poppedObject() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize(t, method));
    assert(t, getBit(map, sp - 1) != 0);
    clearBit(map, -- sp);
  }

  void storedInt(unsigned index) {
    if (index >= parameterFootprint(t, method)) {
      assert(t, index - parameterFootprint(t, method) < localSize(t, method));
      clearBit(map, index - parameterFootprint(t, method));
    }
  }

  void storedObject(unsigned index) {
    if (index >= parameterFootprint(t, method)) {
      assert(t, index - parameterFootprint(t, method) < localSize(t, method));
      markBit(map, index - parameterFootprint(t, method));
    }
  }

  void dupped() {
    assert(t, sp + 1 <= mapSize(t, method));
    assert(t, sp - 1 >= localSize(t, method));
    if (getBit(map, sp - 1)) {
      markBit(map, sp);
    }
    ++ sp;
  }

  void duppedX1() {
    assert(t, sp + 1 <= mapSize(t, method));
    assert(t, sp - 2 >= localSize(t, method));

    unsigned b2 = getBit(map, sp - 2);
    unsigned b1 = getBit(map, sp - 1);

    if (b2) {
      markBit(map, sp - 1);
    } else {
      clearBit(map, sp - 1);
    }

    if (b1) {
      markBit(map, sp - 2);
      markBit(map, sp);
    } else {
      clearBit(map, sp - 2);
    }

    ++ sp;
  }

  void duppedX2() {
    assert(t, sp + 1 <= mapSize(t, method));
    assert(t, sp - 3 >= localSize(t, method));

    unsigned b3 = getBit(map, sp - 3);
    unsigned b2 = getBit(map, sp - 2);
    unsigned b1 = getBit(map, sp - 1);

    if (b3) {
      markBit(map, sp - 2);
    } else {
      clearBit(map, sp - 2);
    }

    if (b2) {
      markBit(map, sp - 1);
    } else {
      clearBit(map, sp - 1);
    }

    if (b1) {
      markBit(map, sp - 3);
      markBit(map, sp);
    } else {
      clearBit(map, sp - 3);
    }

    ++ sp;
  }

  void dupped2() {
    assert(t, sp + 2 <= mapSize(t, method));
    assert(t, sp - 2 >= localSize(t, method));

    unsigned b2 = getBit(map, sp - 2);
    unsigned b1 = getBit(map, sp - 1);

    if (b2) {
      markBit(map, sp);
    }

    if (b1) {
      markBit(map, sp + 1);
    }

    sp += 2;
  }

  void dupped2X1() {
    assert(t, sp + 2 <= mapSize(t, method));
    assert(t, sp - 3 >= localSize(t, method));

    unsigned b3 = getBit(map, sp - 3);
    unsigned b2 = getBit(map, sp - 2);
    unsigned b1 = getBit(map, sp - 1);

    if (b3) {
      markBit(map, sp - 1);
    } else {
      clearBit(map, sp - 1);
    }

    if (b2) {
      markBit(map, sp - 3);
      markBit(map, sp);
    } else {
      clearBit(map, sp - 3);
    }

    if (b1) {
      markBit(map, sp - 2);
      markBit(map, sp + 1);
    } else {
      clearBit(map, sp - 2);
    }

    sp += 2;
  }

  void dupped2X2() {
    assert(t, sp + 2 <= mapSize(t, method));
    assert(t, sp - 4 >= localSize(t, method));

    unsigned b4 = getBit(map, sp - 4);
    unsigned b3 = getBit(map, sp - 3);
    unsigned b2 = getBit(map, sp - 2);
    unsigned b1 = getBit(map, sp - 1);

    if (b4) {
      markBit(map, sp - 2);
    } else {
      clearBit(map, sp - 2);
    }

    if (b3) {
      markBit(map, sp - 1);
    } else {
      clearBit(map, sp - 1);
    }

    if (b2) {
      markBit(map, sp - 4);
      markBit(map, sp);
    } else {
      clearBit(map, sp - 4);
    }

    if (b1) {
      markBit(map, sp - 3);
      markBit(map, sp + 1);
    } else {
      clearBit(map, sp - 3);
    }

    sp += 2;
  }

  void swapped() {
    assert(t, sp - 1 >= localSize(t, method));
    assert(t, sp - 2 >= localSize(t, method));

    bool savedBit = getBit(map, sp - 1);
    if (getBit(map, sp - 2)) {
      markBit(map, sp - 1);
    } else {
      clearBit(map, sp - 1);
    }

    if (savedBit) {
      markBit(map, sp - 2);
    } else {
      clearBit(map, sp - 2);
    }
  }

  void trace(object target, bool virtualCall) {
    TraceElement* e = new (traceLog->allocate(traceSizeInBytes(t, method)))
      TraceElement(target, c->machineIp(), virtualCall);
    memcpy(e->map, map, mapSizeInWords(t, method) * BytesPerWord);
  }

  void trace() {
    trace(0, false);
  }

  Operand* machineIp(unsigned logicalIp) {
    return c->promiseConstant(c->machineIp(logicalIp));
  }

  void startLogicalIp(unsigned ip) {
    c->startLogicalIp(ip);
    this->ip = ip;
  }

  void pushInt(Operand* o) {
    stack = c->push(stack, o);
    pushedInt();
  }

  void pushObject(Operand* o) {
    stack = c->push(stack, o);
    pushedObject();
  }

  void pushObject() {
    stack = c->push(stack, 1);
    pushedObject();
  }

  void pushLong(Operand* o) {
    stack = c->push(stack, c->select8(o));
    if (BytesPerWord == 8) {
      stack = c->push(stack, 1);
    }

    pushedInt();
    pushedInt();
  }

  void pop(unsigned count) {
    popped(count);
    stack = c->pop(stack, count);
  }

  Operand* topInt() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize(t, method));
    assert(t, getBit(map, sp - 1) == 0);
    return c->stack(stack, 0);
  }

  Operand* topLong() {
    assert(t, sp >= 2);
    assert(t, sp - 2 >= localSize(t, method));
    assert(t, getBit(map, sp - 1) == 0);
    assert(t, getBit(map, sp - 2) == 0);
    return c->stack(stack, 1);
  }

  Operand* topObject() {
    assert(t, sp >= 1);
    assert(t, sp - 1 >= localSize(t, method));
    assert(t, getBit(map, sp - 1) != 0);
    return c->stack(stack, 0);
  }

  Operand* popInt() {
    Operand* tmp = c->temporary();
    popInt(tmp);
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
    stack = c->pop(stack, o);
    poppedInt();
  }

  void popLong(Operand* o) {
    if (BytesPerWord == 8) {
      stack = c->pop(stack, 1);
    }
    stack = c->pop(stack, o);

    poppedInt();
    poppedInt();
  }

  void popObject(Operand* o) {
    stack = c->pop(stack, o);
    poppedObject();
  }

  void loadInt(unsigned index) {
    assert(t, index < codeMaxLocals(t, methodCode(t, method)));
    assert(t, index < parameterFootprint(t, method)
           or getBit(map, index - parameterFootprint(t, method)) == 0);
    pushInt(c->memory(c->base(), localOffset(t, index, method)));
  }

  void loadLong(unsigned index) {
    assert(t, index < static_cast<unsigned>
           (codeMaxLocals(t, methodCode(t, method)) - 1));
    assert(t, index < parameterFootprint(t, method)
           or getBit(map, index - parameterFootprint(t, method)) == 0);
    assert(t, index < parameterFootprint(t, method)
           or getBit(map, index + 1 - parameterFootprint(t, method)) == 0);
    pushLong(c->select8(c->memory(c->base(), localOffset(t, index, method))));
  }

  void loadObject(unsigned index) {
    assert(t, index < codeMaxLocals(t, methodCode(t, method)));
    assert(t, index < parameterFootprint(t, method)
           or getBit(map, index - parameterFootprint(t, method)) != 0);
    pushObject(c->memory(c->base(), localOffset(t, index, method)));
  }

  void storeInt(unsigned index) {
    popInt(c->memory(c->base(), localOffset(t, index, method)));
    storedInt(index);
  }

  void storeLong(unsigned index) {
    popLong(c->select8(c->memory(c->base(), localOffset(t, index, method))));
    storedInt(index);
    storedInt(index + 1);
  }

  void storeObject(unsigned index) {
    popObject(c->memory(c->base(), localOffset(t, index, method)));
    storedObject(index);
  }

  void increment(unsigned index, int count) {
    assert(t, index < codeMaxLocals(t, methodCode(t, method)));
    assert(t, index < parameterFootprint(t, method)
           or getBit(map, index - parameterFootprint(t, method)) == 0);
    c->add(c->constant(count),
           c->memory(c->base(), localOffset(t, index, method)));
  }

  void dup() {
    stack = c->push(stack, c->stack(stack, 0));
    dupped();
  }

  void dupX1() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);

    c->mov(s0, s1);
    c->mov(s1, s0);
    stack = c->push(stack, s0);

    duppedX1();
  }

  void dupX2() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);
    Operand* s2 = c->stack(stack, 2);

    c->mov(s0, s2);
    c->mov(s2, s1);
    c->mov(s1, s0);
    stack = c->push(stack, s0);

    duppedX2();
  }

  void dup2() {
    Operand* s0 = c->stack(stack, 0);

    stack = c->push(stack, s0);
    stack = c->push(stack, s0);

    dupped2();
  }

  void dup2X1() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);
    Operand* s2 = c->stack(stack, 2);

    c->mov(s1, s2);
    c->mov(s0, s1);
    c->mov(s2, s0);
    stack = c->push(stack, s1);
    stack = c->push(stack, s0);

    dupped2X1();
  }

  void dup2X2() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);
    Operand* s2 = c->stack(stack, 2);
    Operand* s3 = c->stack(stack, 3);

    c->mov(s1, s3);
    c->mov(s0, s2);
    c->mov(s3, s1);
    c->mov(s2, s0);
    stack = c->push(stack, s1);
    stack = c->push(stack, s0);

    dupped2X2();
  }

  void swap() {
    Operand* s0 = c->stack(stack, 0);
    Operand* s1 = c->stack(stack, 1);
    Operand* tmp = c->temporary();

    c->mov(s0, tmp);
    c->mov(s1, s0);
    c->mov(tmp, s1);

    c->release(tmp);

    swapped();
  }
  
  Frame* next;
  MyThread* t;
  Compiler* c;
  Stack* stack;
  object method;
  uintptr_t* map;
  Vector* objectPool;
  Vector* traceLog;
  uintptr_t* codeMask;
  unsigned ip;
  unsigned sp;
  MyProtector protector;
};

void NO_RETURN
unwind(MyThread* t)
{
  void* base = t->base;
  void** stack = static_cast<void**>(t->stack);
  while (true) {
    void* returnAddress = *stack;
    object node = findTraceNode(t, returnAddress);
    if (node) {
      object method = traceNodeMethod(t, node);
      uint8_t* compiled = reinterpret_cast<uint8_t*>
        (&singletonValue(t, methodCompiled(t, method), 0));

      ExceptionHandler* handler = findExceptionHandler
        (t, method, difference(returnAddress, compiled));

      if (handler) {
        unsigned parameterFootprint = methodParameterFootprint(t, method);
        unsigned localFootprint = codeMaxLocals(t, methodCode(t, method));

        stack = static_cast<void**>(base)
          - (localFootprint - parameterFootprint);

        *(--stack) = t->exception;
        t->exception = 0;

        vmJump(compiled + exceptionHandlerIp(handler), base, stack, t);
      } else {
        stack = static_cast<void**>(base) + 1;
        base = *static_cast<void**>(base);
      }
    } else {
      vmJump(returnAddress, base, stack + 1, 0);
    }
  }
}

object
findInterfaceMethodFromInstance(Thread* t, object method, object instance)
{
  return findInterfaceMethod(t, method, objectClass(t, instance));
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

object
makeBlankObjectArray(Thread* t, object class_, int32_t length)
{
  return makeObjectArray(t, class_, length, true);
}

object
makeBlankArray(Thread* t, object (*constructor)(Thread*, uintptr_t, bool),
               int32_t length)
{
  return constructor(t, length, true);
}

uintptr_t
lookUpAddress(int32_t key, uintptr_t* start, int32_t count,
              uintptr_t* default_)
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

  return *default_;
}

void
acquireMonitorForObject(Thread* t, object o)
{
  acquire(t, o);
}

void
releaseMonitorForObject(Thread* t, object o)
{
  release(t, o);
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

object
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

void NO_RETURN
throwNew(MyThread* t, object class_)
{
  t->exception = makeNew(t, class_);
  object trace = makeTrace(t);
  set(t, t->exception, ThrowableTrace, trace);
  unwind(t);
}

void NO_RETURN
throw_(MyThread* t, object o)
{
  if (o) {
    t->exception = o;
  } else {
    t->exception = makeNullPointerException(t);
  }
  unwind(t);
}

void
compileThrowNew(MyThread* t, Frame* frame, Machine::Type type)
{
  Operand* class_ = frame->append(arrayBody(t, t->m->types, type));
  Compiler* c = frame->c;
  c->indirectCallNoReturn
    (c->constant(reinterpret_cast<intptr_t>(throwNew)),
     2, c->thread(), class_);

  frame->trace();
}

void
pushReturnValue(MyThread* t, Frame* frame, unsigned code, Operand* result)
{
  switch (code) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    frame->pushInt(result);
    break;

  case ObjectField:
    frame->pushObject(result);
    break;

  case LongField:
  case DoubleField:
    frame->pushLong(result);
    break;

  case VoidField:
    break;

  default:
    abort(t);
  }
}

void
compileDirectInvoke(MyThread* t, Frame* frame, object target)
{
  Operand* result = frame->c->alignedCall
    (frame->c->constant
     (reinterpret_cast<intptr_t>
      (&singletonBody(t, methodCompiled(t, target), 0))));

  frame->trace(target, false);

  frame->pop(methodParameterFootprint(t, target));

  pushReturnValue(t, frame, methodReturnCode(t, target), result);
}

void
compile(MyThread* t, Frame* initialFrame, unsigned ip)
{
  uintptr_t map[Frame::mapSizeInWords(t, initialFrame->method)];
  Frame myFrame(initialFrame, map);
  Frame* frame = &myFrame;
  Compiler* c = frame->c;

  object code = methodCode(t, frame->method);
  PROTECT(t, code);
    
  while (ip < codeLength(t, code)) {
    if (getBit(frame->codeMask, ip)) {
      // we've already visited this part of the code
      return;
    }

    markBit(frame->codeMask, ip);

    frame->startLogicalIp(ip);

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
      Operand* next = c->label();
      Operand* outOfBounds = c->label();

      Operand* index = frame->popInt();
      Operand* array = frame->popObject();

      c->cmp(c->constant(0), index);
      c->jl(outOfBounds);

      c->cmp(c->memory(array, ArrayLength), index);
      c->jge(outOfBounds);

      switch (instruction) {
      case aaload:
        frame->pushObject(c->memory(array, ArrayBody, index, BytesPerWord));
        break;

      case faload:
      case iaload:
        frame->pushInt(c->select4(c->memory(array, ArrayBody, index, 4)));
        break;

      case baload:
        frame->pushInt(c->select1(c->memory(array, ArrayBody, index, 1)));
        break;

      case caload:
        frame->pushInt(c->select2z(c->memory(array, ArrayBody, index, 2)));
        break;

      case daload:
      case laload:
        frame->pushLong(c->select8(c->memory(array, ArrayBody, index, 8)));
        break;

      case saload:
        frame->pushInt(c->select2(c->memory(array, ArrayBody, index, 2)));
        break;
      }

      c->release(index);
      c->release(array);

      c->jmp(next);

      c->mark(outOfBounds);
      compileThrowNew(t, frame, Machine::ArrayIndexOutOfBoundsExceptionType);

      c->mark(next);
    } break;

    case aastore:
    case bastore:
    case castore:
    case dastore:
    case fastore:
    case iastore:
    case lastore:
    case sastore: {
      Operand* next = c->label();
      Operand* outOfBounds = c->label();

      Operand* value;
      if (instruction == dastore or instruction == lastore) {
        value = frame->popLong();
      } else if (instruction == aastore) {
        value = frame->popObject();
      } else {
        value = frame->popInt();
      }

      Operand* index = frame->popInt();
      Operand* array = frame->popObject();

      c->cmp(c->constant(0), index);
      c->jl(outOfBounds);

      c->cmp(c->memory(array, BytesPerWord), index);
      c->jge(outOfBounds);

      switch (instruction) {
      case aastore:
        c->shl(c->constant(log(BytesPerWord)), index);
        c->add(c->constant(ArrayBody), index);
          
        c->directCall
          (c->constant(reinterpret_cast<intptr_t>(set)),
           4, c->thread(), array, index, value);

        frame->trace();
        break;

      case fastore:
      case iastore:
        c->mov(value, c->select4(c->memory(array, ArrayBody, index, 4)));
        break;

      case bastore:
        c->mov(value, c->select1(c->memory(array, ArrayBody, index, 1)));
        break;

      case castore:
      case sastore:
        c->mov(value, c->select2(c->memory(array, ArrayBody, index, 2)));
        break;

      case dastore:
      case lastore:
        c->mov(value, c->select8(c->memory(array, ArrayBody, index, 8)));
        break;
      }

      c->release(value);
      c->release(index);
      c->release(array);

      c->jmp(next);

      c->mark(outOfBounds);
      compileThrowNew(t, frame, Machine::ArrayIndexOutOfBoundsExceptionType);

      c->mark(next);
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

      Operand* length = frame->popInt();
      c->cmp(c->constant(0), length);
      c->jge(nonnegative);

      compileThrowNew(t, frame, Machine::NegativeArraySizeExceptionType);

      c->mark(nonnegative);

      Operand* r = c->indirectCall
         (c->constant(reinterpret_cast<intptr_t>(makeBlankObjectArray)),
          3, c->thread(), frame->append(class_), length);

      c->release(length);

      frame->trace();

      frame->pushObject(r);
    } break;

    case areturn: {
      Operand* result = frame->popObject();
      c->return_(result);
      c->release(result);
    } return;

    case arraylength: {
      Operand* array = frame->popObject();
      frame->pushInt(c->memory(array, ArrayLength));
      c->release(array);
    } break;

    case astore:
      frame->storeObject(codeBody(t, code, ip++));
      break;

    case astore_0:
      frame->storeObject(0);
      break;

    case astore_1:
      frame->storeObject(1);
      break;

    case astore_2:
      frame->storeObject(2);
      break;

    case astore_3:
      frame->storeObject(3);
      break;

    case athrow: {
      Operand* e = frame->popObject();
      c->indirectCallNoReturn
        (c->constant(reinterpret_cast<intptr_t>(throw_)),
         2, c->thread(), e);
      c->release(e);

      frame->trace();
    } return;

    case bipush:
      frame->pushInt
        (c->constant(static_cast<int8_t>(codeBody(t, code, ip++))));
      break;

    case checkcast: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* next = c->label();

      Operand* instance = frame->topObject();
      Operand* tmp = c->temporary();

      c->mov(instance, tmp);

      c->cmp(c->constant(0), tmp);
      c->je(next);

      Operand* classOperand = frame->append(class_);

      c->mov(c->memory(tmp), tmp);
      c->and_(c->constant(PointerMask), tmp);

      c->cmp(classOperand, tmp);
      c->je(next);

      Operand* result = c->directCall
        (c->constant(reinterpret_cast<intptr_t>(isAssignableFrom)),
         3, c->thread(), classOperand, tmp);

      c->release(tmp);

      c->cmp(c->constant(0), result);
      c->jne(next);
        
      compileThrowNew(t, frame, Machine::ClassCastExceptionType);

      c->mark(next);
    } break;

    case d2f: {
      Operand* a = frame->popLong();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(doubleToFloat)), 1, a));
      c->release(a);
    } break;

    case d2i: {
      Operand* a = frame->popLong();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(doubleToInt)), 1, a));
      c->release(a);
    } break;

    case d2l: {
      Operand* a = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(doubleToLong)), 1, a));
      c->release(a);
    } break;

    case dadd: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(addDouble)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case dcmpg: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(compareDoublesG)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case dcmpl: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(compareDoublesL)), 2, a, b));
      c->release(a);
      c->release(b);
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
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(divideDouble)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case dmul: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(multiplyDouble)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case dneg: {
      Operand* a = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(negateDouble)), 1, a));
      c->release(a);
    } break;

    case vm::drem: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(moduloDouble)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case dsub: {
      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(subtractDouble)), 2, a, b));
      c->release(a);
      c->release(b);
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
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(floatToDouble)), 1, a));
      c->release(a);
    } break;

    case f2i: {
      Operand* a = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(floatToInt)), 1, a));
      c->release(a);
    } break;

    case f2l: {
      Operand* a = frame->popInt();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(floatToLong)), 1, a));
      c->release(a);
    } break;

    case fadd: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(addFloat)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case fcmpg: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(compareFloatsG)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case fcmpl: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(compareFloatsL)), 2, a, b));
      c->release(a);
      c->release(b);
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
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(divideFloat)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case fmul: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(multiplyFloat)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case fneg: {
      Operand* a = frame->popLong();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(negateFloat)), 1, a));
      c->release(a);
    } break;

    case vm::frem: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(moduloFloat)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case fsub: {
      Operand* a = frame->popInt();
      Operand* b = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(subtractFloat)), 2, a, b));
      c->release(a);
      c->release(b);
    } break;

    case getfield:
    case getstatic: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* table;

      if (instruction == getstatic) {
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return;

        table = frame->append(classStaticTable(t, fieldClass(t, field)));
      } else {
        table = frame->popObject();
      }

      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        frame->pushInt(c->select1(c->memory(table, fieldOffset(t, field))));
        break;

      case CharField:
        frame->pushInt(c->select2z(c->memory(table, fieldOffset(t, field))));
        break;

      case ShortField:
        frame->pushInt(c->select2(c->memory(table, fieldOffset(t, field))));
        break;

      case FloatField:
      case IntField:
        frame->pushInt(c->select4(c->memory(table, fieldOffset(t, field))));
        break;

      case DoubleField:
      case LongField:
        frame->pushLong(c->select8(c->memory(table, fieldOffset(t, field))));
        break;

      case ObjectField:
        frame->pushObject(c->memory(table, fieldOffset(t, field)));
        break;

      default:
        abort(t);
      }

      if (instruction != getstatic) {
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
      c->mov(c->select1(top), top);
    } break;

    case i2c: {
      Operand* top = frame->topInt();
      c->mov(c->select2z(top), top);
    } break;

    case i2d: {
      Operand* a = frame->popInt();
      frame->pushLong
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(intToDouble)), 1, a));
      c->release(a);
    } break;

    case i2f: {
      Operand* a = frame->popInt();
      frame->pushInt
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(intToFloat)), 1, a));
      c->release(a);
    } break;

    case i2l: {
      Operand* a = frame->popInt();
      frame->pushLong(a);
      c->release(a);
    } break;

    case i2s: {
      Operand* top = frame->topInt();
      c->mov(c->select2(top), top);
    } break;
      
    case iadd: {
      Operand* a = frame->popInt();
      c->add(a, frame->topInt());
      c->release(a);
    } break;
      
    case iand: {
      Operand* a = frame->popInt();
      c->and_(a, frame->topInt());
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
      c->div(a, frame->topInt());
      c->release(a);
    } break;

    case if_acmpeq:
    case if_acmpne: {
      uint32_t newIp = (ip - 3) + codeReadInt16(t, code, ip);
      assert(t, newIp < codeLength(t, code));
        
      Operand* a = frame->popObject();
      Operand* b = frame->popObject();
      c->cmp(a, b);
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
      c->cmp(a, b);
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
      c->cmp(c->constant(0), a);
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
      c->cmp(c->constant(0), a);
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

      frame->increment(index, count);
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
      c->mul(a, frame->topInt());
      c->release(a);
    } break;

    case instanceof: {
      uint16_t index = codeReadInt16(t, code, ip);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      Operand* call = c->label();
      Operand* next = c->label();
      Operand* zero = c->label();

      Operand* instance = frame->popObject();
      Operand* tmp = c->temporary();
      Operand* result = c->temporary();

      c->mov(instance, tmp);

      c->cmp(c->constant(0), tmp);
      c->je(zero);

      Operand* classOperand = frame->append(class_);

      c->mov(c->memory(tmp), tmp);
      c->and_(c->constant(PointerMask), tmp);

      c->cmp(classOperand, tmp);
      c->jne(call);

      c->mov(c->constant(1), result);
      c->jmp(next);

      c->mark(call);

      c->mov
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(isAssignableFrom)),
          3, c->thread(), classOperand, tmp), result);

      c->release(tmp);
      c->release(instance);

      c->jmp(next);
        
      c->mark(zero);

      c->mov(c->constant(0), result);

      c->mark(next);
      frame->pushInt(result);

      c->release(result);
    } break;

    case invokeinterface: {
      uint16_t index = codeReadInt16(t, code, ip);
      ip += 2;

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      unsigned parameterFootprint = methodParameterFootprint(t, target);

      unsigned instance = parameterFootprint - 1;

      Operand* found = c->directCall
        (c->constant
         (reinterpret_cast<intptr_t>(findInterfaceMethodFromInstance)),
         3, c->thread(), frame->append(target),
         c->stack(frame->stack, instance));

      c->mov(c->memory(found, MethodCompiled), found);

      Operand* result = c->call(c->memory(found, SingletonBody));

      frame->trace(target, true);

      frame->pop(parameterFootprint);

      pushReturnValue(t, frame, methodReturnCode(t, target), result);
    } break;

    case invokespecial: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;

      object class_ = methodClass(t, target);
      if (isSpecialMethod(t, target, class_)) {
        target = findMethod(t, target, classSuper(t, class_));
      }

      compileDirectInvoke(t, frame, target);
    } break;

    case invokestatic: {
      uint16_t index = codeReadInt16(t, code, ip);

      object target = resolveMethod(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, target);

      initClass(t, methodClass(t, target));
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
      
      c->mov(c->memory(instance), class_);
      c->and_(c->constant(PointerMask), class_);

      Operand* result = c->call(c->memory(class_, offset));

      frame->trace(target, true);

      c->release(class_);

      frame->pop(parameterFootprint);

      pushReturnValue(t, frame, methodReturnCode(t, target), result);
    } break;

    case ior: {
      Operand* a = frame->popInt();
      c->or_(a, frame->topInt());
      c->release(a);
    } break;

    case irem: {
      Operand* a = frame->popInt();
      c->rem(a, frame->topInt());
      c->release(a);
    } break;

    case ireturn:
    case freturn: {
      Operand* a = frame->popInt();
      c->return_(a);
      c->release(a);
    } return;

    case ishl: {
      Operand* a = frame->popInt();
      c->shl(a, frame->topInt());
      c->release(a);
    } break;

    case ishr: {
      Operand* a = frame->popInt();
      c->shr(a, frame->topInt());
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
      c->sub(a, frame->topInt());
      c->release(a);
    } break;

    case iushr: {
      Operand* a = frame->popInt();
      c->ushr(a, frame->topInt());
      c->release(a);
    } break;

    case ixor: {
      Operand* a = frame->popInt();
      c->xor_(a, frame->topInt());
      c->release(a);
    } break;

    case jsr:
    case jsr_w:
    case ret:
      // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4381996
      abort(t);

    case l2i: {
      Operand* a = frame->popLong();
      frame->pushInt(a);
      c->release(a);
    } break;

    case ladd: {
      Operand* a = frame->popLong();
      c->add(a, frame->topLong());
      c->release(a);
    } break;

    case lcmp: {
      Operand* next = c->label();
      Operand* less = c->label();
      Operand* greater = c->label();

      Operand* a = frame->popLong();
      Operand* b = frame->popLong();
      Operand* result = c->temporary();
          
      c->cmp(a, b);
      c->release(a);
      c->release(b);

      c->jl(less);
      c->jg(greater);

      c->mov(c->constant(0), result);
      c->jmp(next);
          
      c->mark(less);
      c->mov(c->constant(-1), result);
      c->jmp(next);

      c->mark(greater);
      c->mov(c->constant(1), result);

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
      c->div(a, frame->topLong());
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
      c->mul(a, frame->topLong());
      c->release(a);
    } break;

    case lneg:
      c->neg(frame->topLong());
      break;

    case lookupswitch: {
      int32_t base = ip - 1;

      ip = (ip + 3) & ~3; // pad to four byte boundary

      Operand* key = frame->popInt();
    
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

      c->jmp
        (c->directCall
         (c->constant(reinterpret_cast<intptr_t>(lookUpAddress)),
          4, key, start, c->constant(pairCount), default_));

      c->release(key);

      for (int32_t i = 0; i < pairCount; ++i) {
        compile(t, frame, ipTable[i]);
        if (UNLIKELY(t->exception)) return;
      }

      compile(t, frame, defaultIp);
      if (UNLIKELY(t->exception)) return;
    } return;

    case lor: {
      Operand* a = frame->popLong();
      c->or_(a, frame->topLong());
      c->release(a);
    } break;

    case lrem: {
      Operand* a = frame->popLong();
      c->rem(a, frame->topLong());
      c->release(a);
    } break;

    case lreturn:
    case dreturn: {
      Operand* a = frame->popLong();
      c->return_(a);
      c->release(a);
    } return;

    case lshl: {
      Operand* a = frame->popLong();
      c->shl(a, frame->topLong());
      c->release(a);
    } break;

    case lshr: {
      Operand* a = frame->popLong();
      c->shr(a, frame->topLong());
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
      c->sub(a, frame->topLong());
      c->release(a);
    } break;

    case lushr: {
      Operand* a = frame->popLong();
      c->ushr(a, frame->topLong());
      c->release(a);
    } break;

    case lxor: {
      Operand* a = frame->popLong();
      c->xor_(a, frame->topLong());
      c->release(a);
    } break;

    case monitorenter: {
      Operand* a = frame->popObject();
      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(acquireMonitorForObject)),
         2, c->thread(), a);
      c->release(a);

      frame->trace();
    } break;

    case monitorexit: {
      Operand* a = frame->popObject();
      c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(releaseMonitorForObject)),
         2, c->thread(), a);
      c->release(a);

      frame->trace();
    } break;

    case multianewarray: {
      uint16_t index = codeReadInt16(t, code, ip);
      uint8_t dimensions = codeBody(t, code, ip++);

      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);

      Operand* result = c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(makeMultidimensionalArray)),
         4, c->thread(), frame->append(class_), c->stack(),
         c->constant(dimensions));

      frame->trace();

      frame->pop(dimensions);
      frame->pushObject(result);
    } break;

    case new_: {
      uint16_t index = codeReadInt16(t, code, ip);
        
      object class_ = resolveClassInPool(t, codePool(t, code), index - 1);
      if (UNLIKELY(t->exception)) return;
      PROTECT(t, class_);
        
      initClass(t, class_);
      if (UNLIKELY(t->exception)) return;

      Operand* result;
      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        result = c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(makeNewWeakReference)),
           2, c->thread(), frame->append(class_));
      } else {
        result = c->indirectCall
          (c->constant(reinterpret_cast<intptr_t>(makeNew)),
           2, c->thread(), frame->append(class_));
      }

      frame->trace();

      frame->pushObject(result);
    } break;

    case newarray: {
      uint8_t type = codeBody(t, code, ip++);

      Operand* nonnegative = c->label();

      Operand* size = frame->popInt();
      c->cmp(c->constant(0), size);

      c->jge(nonnegative);

      compileThrowNew(t, frame, Machine::NegativeArraySizeExceptionType);

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

      Operand* result = c->indirectCall
        (c->constant(reinterpret_cast<intptr_t>(makeBlankArray)),
         3, c->thread(), c->constant(reinterpret_cast<intptr_t>(constructor)),
         size);

      c->release(size);

      frame->trace();

      frame->pushObject(result);
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

      object staticTable;

      if (instruction == putstatic) {
        PROTECT(t, field);
        initClass(t, fieldClass(t, field));
        if (UNLIKELY(t->exception)) return;  

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
        c->mov(value, c->select1(c->memory(table, fieldOffset(t, field))));
        break;

      case CharField:
      case ShortField:
        c->mov(value, c->select2(c->memory(table, fieldOffset(t, field))));
        break;
            
      case FloatField:
      case IntField:
        c->mov(value, c->select4(c->memory(table, fieldOffset(t, field))));
        break;

      case DoubleField:
      case LongField:
        c->mov(value, c->select8(c->memory(table, fieldOffset(t, field))));
        break;

      case ObjectField:
        c->directCall
          (c->constant(reinterpret_cast<intptr_t>(set)),
           4, c->thread(), table, c->constant(fieldOffset(t, field)), value);
        break;

      default: abort(t);
      }

      if (instruction != putstatic) {
        c->release(table);
      }
      c->release(value);
    } break;

    case return_:
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

      Operand* key = frame->popInt();

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
      
      c->cmp(c->constant(bottom), key);
      c->jl(defaultCase);

      c->cmp(c->constant(top), key);
      c->jg(defaultCase);

      c->sub(c->constant(bottom), key);
      c->jmp(c->memory(start, 0, key, BytesPerWord));

      c->mark(defaultCase);
      c->jmp(frame->machineIp(defaultIp));

      c->release(key);

      for (int32_t i = 0; i < top - bottom + 1; ++i) {
        compile(t, frame, ipTable[i]);
        if (UNLIKELY(t->exception)) return;
      }

      compile(t, frame, defaultIp);
      if (UNLIKELY(t->exception)) return;
    } return;

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

        frame->increment(index, count);
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
        // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4381996
        abort(t);

      default: abort(t);
      }
    } break;
    }
  }
}

object
finish(MyThread* t, Compiler* c, object method, Vector* objectPool,
       Vector* traceLog)
{
  unsigned count = ceiling(c->codeSize() + c->poolSize(), BytesPerWord);
  unsigned size = count + singletonMaskSize(count);
  object result = allocate2
    (t, SingletonBody + size * BytesPerWord, true, true);
  initSingleton(t, result, size, true); 
  singletonMask(t, result)[0] = 1;

  uint8_t* start = reinterpret_cast<uint8_t*>(&singletonValue(t, result, 0));

  c->writeTo(start);

  if (method) {
    PROTECT(t, method);

    for (unsigned i = 0; i < objectPool->length(); i += sizeof(PoolElement)) {
      PoolElement* e = objectPool->peek<PoolElement>(i);
      intptr_t offset = e->address->value(c)
        - reinterpret_cast<intptr_t>(start);

      singletonMarkObject(t, result, offset / BytesPerWord);

      set(t, result, SingletonBody + offset, e->value);
    }

    unsigned traceSize = Frame::traceSizeInBytes(t, method);
    unsigned mapSize = Frame::mapSizeInBytes(t, method);
    for (unsigned i = 0; i < traceLog->length(); i += traceSize) {
      TraceElement* e = traceLog->peek<TraceElement>(i);

      object node = makeTraceNode
        (t, e->machineIp->value(c), 0, method, e->target, e->virtualCall,
         mapSize / BytesPerWord, false);

      if (mapSize) {
        memcpy(&traceNodeMap(t, node, 0), e->map, mapSize);
      }

      insertTraceNode(t, node);
    }

    object code = methodCode(t, method);
    PROTECT(t, code);

    {
      object oldTable = codeExceptionHandlerTable(t, code);
      if (oldTable) {
        PROTECT(t, oldTable);

        unsigned length = exceptionHandlerTableLength(t, oldTable);
        object newTable = makeExceptionHandlerTable(t, length, false);
        for (unsigned i = 0; i < length; ++i) {
          ExceptionHandler* oldHandler = exceptionHandlerTableBody
            (t, oldTable, i);
          ExceptionHandler* newHandler = exceptionHandlerTableBody
            (t, newTable, i);

          exceptionHandlerStart(newHandler)
            = c->machineIp(exceptionHandlerStart(oldHandler))->value(c)
            - reinterpret_cast<intptr_t>(start);

          exceptionHandlerEnd(newHandler)
            = c->machineIp(exceptionHandlerEnd(oldHandler))->value(c)
            - reinterpret_cast<intptr_t>(start);

          exceptionHandlerIp(newHandler)
            = c->machineIp(exceptionHandlerIp(oldHandler))->value(c)
            - reinterpret_cast<intptr_t>(start);

          exceptionHandlerCatchType(newHandler)
            = exceptionHandlerCatchType(oldHandler);
        }

        set(t, code, CodeExceptionHandlerTable, newTable);
      }
    }

    {
      object oldTable = codeLineNumberTable(t, code);
      if (oldTable) {
        PROTECT(t, oldTable);

        unsigned length = lineNumberTableLength(t, oldTable);
        object newTable = makeLineNumberTable(t, length, false);
        for (unsigned i = 0; i < length; ++i) {
          LineNumber* oldLine = lineNumberTableBody(t, oldTable, i);
          LineNumber* newLine = lineNumberTableBody(t, newTable, i);

          lineNumberIp(newLine)
            = c->machineIp(lineNumberIp(oldLine))->value(c)
            - reinterpret_cast<intptr_t>(start);

          lineNumberLine(newLine) = lineNumberLine(oldLine);
        }

        set(t, code, CodeLineNumberTable, newTable);
      }
    }

    if (Verbose) {
      fprintf(stderr, "%s.%s from %p to %p\n",
              &byteArrayBody(t, className(t, methodClass(t, method)), 0),
              &byteArrayBody(t, methodName(t, method), 0),
              start,
              start + c->codeSize());
    }

    // for debugging:
    if (false and
        strcmp(reinterpret_cast<const char*>
               (&byteArrayBody(t, className(t, methodClass(t, method)), 0)),
               "java/lang/Long") == 0 and
        strcmp(reinterpret_cast<const char*>
               (&byteArrayBody(t, methodName(t, method), 0)),
               "toString") == 0)
    {
      asm("int3");
    }
  }

  return result;
}

object
finish(MyThread* t, Compiler* c)
{
  return finish(t, c, 0, 0, 0);
}

object
compile(MyThread* t, Compiler* c, object method)
{
  PROTECT(t, method);
  
  c->prologue();

  object code = methodCode(t, method);
  PROTECT(t, code);

  unsigned footprint = methodParameterFootprint(t, method);
  unsigned locals = codeMaxLocals(t, code);
  c->reserve(locals - footprint);

  Vector objectPool(t->m->system, 256);
  Vector traceLog(t->m->system, 1024);
  uintptr_t map[Frame::mapSizeInWords(t, method)];
  Frame frame(t, c, method, map, &objectPool, &traceLog);

  compile(t, &frame, 0);
  if (UNLIKELY(t->exception)) return 0;

  object eht = codeExceptionHandlerTable(t, methodCode(t, method));
  if (eht) {
    PROTECT(t, eht);

    for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);

      assert(t, getBit(frame.codeMask, exceptionHandlerStart(eh)));
        
      uintptr_t map[Frame::mapSizeInWords(t, method)];
      Frame frame2(&frame, map);
      frame2.pushObject();

      compile(t, &frame2, exceptionHandlerIp(eh));
      if (UNLIKELY(t->exception)) return 0;
    }
  }

  return finish(t, c, method, &objectPool, &traceLog);
}

void
compile(MyThread* t, object method);

void*
compileMethod(MyThread* t)
{
  object node = findTraceNode(t, *static_cast<void**>(t->stack));
  object target = resolveTarget(t, t->stack, traceNodeTarget(t, node));

  if (LIKELY(t->exception == 0)) {
    compile(t, target);
  }

  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    if (not traceNodeVirtualCall(t, node)) {
      Compiler* c = makeCompiler(t->m->system, 0);
      c->updateCall(reinterpret_cast<void*>(traceNodeAddress(t, node)),
                    &singletonValue(t, methodCompiled(t, target), 0));
      c->dispose();
    }
    return &singletonValue(t, methodCompiled(t, target), 0);
  }
}

uint64_t
invokeNative2(MyThread* t, object method)
{
  PROTECT(t, method);

  if (objectClass(t, methodCode(t, method))
      == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    void* function = resolveNativeMethod(t, method);
    if (UNLIKELY(function == 0)) {
      object message = makeString
        (t, "%s", &byteArrayBody(t, methodCode(t, method), 0));
      t->exception = makeUnsatisfiedLinkError(t, message);
      return 0;
    }

    object p = makePointer(t, function);
    set(t, method, MethodCode, p);
  }

  object class_ = methodClass(t, method);
  PROTECT(t, class_);

  unsigned footprint = methodParameterFootprint(t, method) + 1;
  unsigned count = methodParameterCount(t, method) + 1;
  if (methodFlags(t, method) & ACC_STATIC) {
    ++ footprint;
    ++ count;
  }

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
      if (BytesPerWord == 8) {
        uint64_t a = *(sp--);
        uint64_t b = *(sp--);
        args[argOffset++] = (a << 32) | b;
      } else {
        memcpy(args + argOffset, sp, 8);
        argOffset += 2;
        sp -= 2;
      }
    } break;

    case POINTER_TYPE: {
      args[argOffset++] = reinterpret_cast<uintptr_t>(sp--);
    } break;

    default: abort(t);
    }
  }

  void* function = pointerValue(t, methodCode(t, method));
  unsigned returnType = fieldType(t, methodReturnCode(t, method));
  uint64_t result;
  
  if (Verbose) {
    fprintf(stderr, "invoke native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  { ENTER(t, Thread::IdleState);

    result = t->m->system->call
      (function,
       args,
       types,
       count + 1,
       footprint * BytesPerWord,
       returnType);
  }

  if (Verbose) {
    fprintf(stderr, "return from native method %s.%s\n",
            &byteArrayBody(t, className(t, methodClass(t, method)), 0),
            &byteArrayBody(t, methodName(t, method), 0));
  }

  if (LIKELY(t->exception == 0) and returnType == POINTER_TYPE) {
    return result ? *reinterpret_cast<uintptr_t*>(result) : 0;
  } else {
    return result;
  }
}

uint64_t
invokeNative(MyThread* t)
{
  object node = findTraceNode(t, *static_cast<void**>(t->stack));
  object target = resolveTarget(t, t->stack, traceNodeTarget(t, node));
  uint64_t result;

  if (LIKELY(t->exception == 0)) {
    result = invokeNative2(t, target);
  }

  if (UNLIKELY(t->exception)) {
    unwind(t);
  } else {
    return result;
  }
}

inline object*
localObject(MyThread* t, void* base, object method, unsigned index)
{
  return reinterpret_cast<object*>
    (static_cast<uint8_t*>(base) + localOffset(t, index, method));
}

void
visitParameters(MyThread* t, Heap::Visitor* v, void* base, object method)
{
  const char* spec = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));

  unsigned index = 0;
  if ((methodFlags(t, method) & ACC_STATIC) == 0) {
    v->visit(localObject(t, base, method, index++));        
  }

  for (MethodSpecIterator it(t, spec); it.hasNext();) {
    switch (*it.next()) {
    case 'L':
    case '[':
      v->visit(localObject(t, base, method, index++));
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

  assert(t, index == methodParameterFootprint(t, method));
}

void
visitStackAndLocals(MyThread* t, Heap::Visitor* v, void* base, object node)
{
  object method = traceNodeMethod(t, node);

  unsigned parameterFootprint = methodParameterFootprint(t, method);
  unsigned count = codeMaxStack(t, methodCode(t, method))
    + codeMaxLocals(t, methodCode(t, method))
    - parameterFootprint;
      
  if (count) {
    uintptr_t* map = &traceNodeMap(t, node, 0);

    for (unsigned i = 0; i < count; ++i) {
      if (getBit(map, i)) {
        v->visit(localObject(t, base, method, i + parameterFootprint));
      }
    }
  }
}

void
visitStack(MyThread* t, Heap::Visitor* v)
{
  void* base = t->base;
  void** stack = static_cast<void**>(t->stack);
  MyThread::CallTrace* trace = t->trace;

  while (true) {
    object node = findTraceNode(t, *stack);
    if (node) {
      PROTECT(t, node);

      // we only need to visit the parameters of this method if the
      // caller is native.  Otherwise, the caller owns them.
      object next = findTraceNode(t, static_cast<void**>(base)[1]);
      object method = traceNodeMethod(t, node);
      
      if (next == 0) {
        visitParameters(t, v, base, method);
      }
      
      visitStackAndLocals(t, v, base, method);

      stack = static_cast<void**>(base) + 1;
      base = *static_cast<void**>(base);
    } else if (trace) {
      base = trace->base;
      stack = static_cast<void**>(trace->stack);
      trace = trace->next;
    } else {
      break;
    }
  }  
}

object
compileDefault(MyThread* t, Compiler* c)
{
  c->mov(c->base(), c->memory(c->thread(), difference(&(t->base), t)));
  c->mov(c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

  c->jmp
    (c->directCall
     (c->constant(reinterpret_cast<intptr_t>(compileMethod)),
      1, c->thread()));

  return finish(t, c);
}

object
compileNative(MyThread* t, Compiler* c)
{
  c->mov(c->base(), c->memory(c->thread(), difference(&(t->base), t)));
  c->mov(c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

  c->directCall
    (c->constant(reinterpret_cast<intptr_t>(invokeNative)), 1, c->thread());
  
  c->ret();

  return finish(t, c);
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
    memcpy(array + position, &v, 8);
    objectMask[position] = false;
    objectMask[position] = false;
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

  Reference* reference = t->reference;
  MyThread::CallTrace trace(t);

  uint64_t result = vmInvoke
    (t, &singletonValue(t, methodCompiled(t, method), 0), arguments->array,
     arguments->position * BytesPerWord, returnType);

  while (t->reference != reference) {
    dispose(t, t->reference);
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

class MyProcessor: public Processor {
 public:
  MyProcessor(System* s):
    s(s),
    defaultCompiled(0),
    nativeCompiled(0),
    addressTable(0),
    addressCount(0),
    indirectCaller(0)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    MyThread* t = new (s->allocate(sizeof(MyThread)))
      MyThread(m, javaThread, parent);
    t->init();
    return t;
  }

  object getDefaultCompiled(MyThread* t) {
    if (defaultCompiled == 0) {
      Compiler* c = makeCompiler(t->m->system, 0);
      defaultCompiled = compileDefault(t, c);
      c->dispose();
    }
    return defaultCompiled;
  }

  object getNativeCompiled(MyThread* t) {
    if (nativeCompiled == 0) {
      Compiler* c = makeCompiler(t->m->system, 0);
      nativeCompiled = compileNative(t, c);
      c->dispose();
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
    object compiled
      = ((flags & ACC_NATIVE)
         ? getNativeCompiled(static_cast<MyThread*>(t))
         : getDefaultCompiled(static_cast<MyThread*>(t)));

    return vm::makeMethod
      (t, vmFlags, returnCode, parameterCount, parameterFootprint, flags,
       offset, name, spec, class_, code, compiled);
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
    for (unsigned i = 0; i < classLength(t, c); ++i) {
      object compiled
        = ((classFlags(t, c) & ACC_NATIVE)
           ? getNativeCompiled(static_cast<MyThread*>(t))
           : getDefaultCompiled(static_cast<MyThread*>(t)));

      classVtable(t, c, i) = &singletonBody(t, compiled, 0);
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
      v->visit(&addressTable);
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

      Reference* r = new (t->m->system->allocate(sizeof(Reference)))
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

  virtual void dispose(Thread* thread) {
    MyThread* t = static_cast<MyThread*>(thread);
    while (t->reference) {
      vm::dispose(t, t->reference);
    }
  }

  virtual void dispose() {
    if (indirectCaller) {
      s->free(indirectCaller);
    }

    s->free(this);
  }
  
  System* s;
  object defaultCompiled;
  object nativeCompiled;
  object addressTable;
  unsigned addressCount;
  uint8_t* indirectCaller;
};

MyProcessor*
processor(MyThread* t)
{
  MyProcessor* p = static_cast<MyProcessor*>(t->m->processor);
  if (p->addressTable == 0) {
    ACQUIRE(t, t->m->classLock);

    if (p->addressTable == 0) {
      p->addressTable = makeArray(t, 128, true);

      Compiler* c = makeCompiler(t->m->system, 0);

      c->mov(c->base(), c->memory(c->thread(), difference(&(t->base), t)));
      c->mov(c->stack(), c->memory(c->thread(), difference(&(t->stack), t)));

      c->jmp(c->indirectTarget());

      p->indirectCaller = static_cast<uint8_t*>
        (t->m->system->allocate(c->codeSize()));
      c->writeTo(p->indirectCaller);

      c->dispose();
    }
  }
  return p;
}

void
compile(MyThread* t, object method)
{
  MyProcessor* p = processor(t);

  if (methodCompiled(t, method) == p->getDefaultCompiled(t)) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);
    
    if (methodCompiled(t, method) == p->getDefaultCompiled(t)) {
      PROTECT(t, method);

      Compiler* c = makeCompiler(t->m->system, p->indirectCaller);
    
      object compiled = compile(t, c, method);
      set(t, method, MethodCompiled, compiled);

      if (methodVirtual(t, method)) {
        classVtable(t, methodClass(t, method), methodOffset(t, method))
          = &singletonValue(t, compiled, 0);
      }

      c->dispose();
    }
  }
}

object
findTraceNode(MyThread* t, void* address)
{
  if (DebugTraces) {
    fprintf(stderr, "find trace node %p\n", address);
  }

  MyProcessor* p = processor(t);
  ACQUIRE(t, t->m->classLock);

  intptr_t key = reinterpret_cast<intptr_t>(address);
  unsigned index = static_cast<uintptr_t>(key) 
    & (arrayLength(t, p->addressTable) - 1);

  for (object n = arrayBody(t, p->addressTable, index);
       n; n = traceNodeNext(t, n))
  {
    intptr_t k = traceNodeAddress(t, n);

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

  object newTable = makeArray(t, newLength, true);

  for (unsigned i = 0; i < arrayLength(t, oldTable); ++i) {
    object next;
    for (object p = arrayBody(t, oldTable, i); p; p = next) {
      next = traceNodeNext(t, p);

      intptr_t k = traceNodeAddress(t, p);

      unsigned index = k & (newLength - 1);

      set(t, p, TraceNodeNext, arrayBody(t, newTable, index));
      set(t, newTable, ArrayBody + (index * BytesPerWord), p);
    }
  }

  return newTable;
}

void
insertTraceNode(MyThread* t, object node)
{
  if (DebugTraces) {
    fprintf(stderr, "insert trace node %p\n",
            reinterpret_cast<void*>(traceNodeAddress(t, node)));
  }

  MyProcessor* p = processor(t);
  PROTECT(t, node);
  ACQUIRE(t, t->m->classLock);

  ++ p->addressCount;

  if (p->addressCount >= arrayLength(t, p->addressTable) * 2) { 
    p->addressTable = resizeTable
      (t, p->addressTable, arrayLength(t, p->addressTable) * 2);
  }

  intptr_t key = traceNodeAddress(t, node);
  unsigned index = static_cast<uintptr_t>(key)
    & (arrayLength(t, p->addressTable) - 1);

  set(t, node, TraceNodeNext, arrayBody(t, p->addressTable, index));
  set(t, p->addressTable, ArrayBody + (index * BytesPerWord), node);
}

} // namespace

namespace vm {

Processor*
makeProcessor(System* system)
{
  return new (system->allocate(sizeof(MyProcessor))) MyProcessor(system);
}

} // namespace vm
