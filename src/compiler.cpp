#include "compiler.h"
#include "vector.h"
#include "zone.h"

using namespace vm;

namespace {

enum Register {
  NoRegister = -1,
  rax = 0,
  rcx = 1,
  rdx = 2,
  rbx = 3,
  rsp = 4,
  rbp = 5,
  rsi = 6,
  rdi = 7,
  r8 = 8,
  r9 = 9,
  r10 = 10,
  r11 = 11,
  r12 = 12,
  r13 = 13,
  r14 = 14,
  r15 = 15,
};

const bool Verbose = false;

const unsigned RegisterCount = BytesPerWord * 2;
const unsigned GprParameterCount = 6;

class Context;
class MyOperand;
class AddressOperand;
class ImmediateOperand;
class AbsoluteOperand;
class RegisterOperand;
class MemoryOperand;
class CodePromise;
class MyPromise;
class RegisterReference;

int64_t FORCE_ALIGN
divideLong(int64_t a, int64_t b)
{
  return a / b;
}

int64_t FORCE_ALIGN
moduloLong(int64_t a, int64_t b)
{
  return a % b;
}

inline bool
isInt8(intptr_t v)
{
  return v == static_cast<int8_t>(v);
}

inline bool
isInt32(intptr_t v)
{
  return v == static_cast<int32_t>(v);
}

class RegisterNode {
 public:
  RegisterNode(Register value, RegisterNode* next):
    value(value), next(next)
  { }

  Register value;
  RegisterNode* next;
};

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual ~Task() { }

  virtual void run(Context*, unsigned) = 0;

  Task* next;
};

class Event {
 public:
  Event(Event* next): next(next), task(0) {
    if (next) {
      count = next->count + 1;
    } else {
      count = 1;
    }
  }

  virtual ~Event() { }

  virtual void run(Context*) { }

  Event* next;
  Task* task;
  unsigned count;
};

class Segment {
 public:
  Segment(int logicalIp, Event* event):
    logicalIp(logicalIp), offset(-1), event(event)
  { }

  int logicalIp;
  int offset;
  Event* event;
};

class MyStack: public Stack {
 public:
  MyStack(MyOperand* value, int index, MyStack* next):
    value(value), index(index), next(next)
  { }

  MyOperand* value;
  int index;
  MyStack* next;
};

class RegisterData {
 public:
  RegisterData(): reserved(false) { }

  bool reserved;
};

class Context {
 public:
  Context(System* s, Zone* zone, void* indirectCaller):
    s(s),
    constantPool(s, BytesPerWord * 32),
    plan(s, 1024),
    code(s, 1024),
    zone(zone),
    indirectCaller(reinterpret_cast<intptr_t>(indirectCaller)),
    segmentTable(0),
    reserved(0),
    codeLength(-1)
  {
    plan.appendAddress(new (zone->allocate(sizeof(Segment))) Segment
                       (-1, new (zone->allocate(sizeof(Event))) Event(0)));

    registers[rsp].reserved = true;
    registers[rbp].reserved = true;
    registers[rbx].reserved = true;
  }

  void dispose() {
    plan.dispose();
    code.dispose();
    constantPool.dispose();
  }

  System* s;
  Vector constantPool;
  Vector plan;
  Vector code;
  Zone* zone;
  intptr_t indirectCaller;
  Segment** segmentTable;
  unsigned reserved;
  int codeLength;
  RegisterData registers[RegisterCount];
};

inline void NO_RETURN
abort(Context* c)
{
  abort(c->s);
}

#ifndef NDEBUG
inline void
assert(Context* c, bool v)
{
  assert(c->s, v);
}
#endif // not NDEBUG

inline void
expect(Context* c, bool v)
{
  expect(c->s, v);
}

class MyPromise: public Promise {
 public:
  virtual intptr_t value(Compiler*);

  virtual intptr_t value(Context*) = 0;

  virtual bool resolved(Context*) = 0;
};

class ResolvedPromise: public MyPromise {
 public:
  ResolvedPromise(intptr_t value): value_(value) { }

  virtual intptr_t value(Context*) {
    return value_;
  }

  virtual bool resolved(Context*) {
    return true;
  }

  intptr_t value_;
};

ResolvedPromise*
resolved(Context* c, intptr_t value)
{
  return new (c->zone->allocate(sizeof(ResolvedPromise)))
    ResolvedPromise(value);
}

class PoolPromise: public MyPromise {
 public:
  PoolPromise(intptr_t key): key(key) { }

  virtual intptr_t value(Context* c) {
    if (resolved(c)) {
      return reinterpret_cast<intptr_t>(c->code.data + c->codeLength + key);
    }
    
    abort(c);
  }

  virtual bool resolved(Context* c) {
    return c->codeLength >= 0;
  }

  intptr_t key;
};

class CodePromise: public MyPromise {
 public:
  CodePromise():
    offset(-1)
  { }

  virtual intptr_t value(Context* c) {
    if (resolved(c)) {
      return reinterpret_cast<intptr_t>(c->code.data + offset);
    }
    
    abort(c);
  }

  virtual bool resolved(Context*) {
    return offset >= 0;
  }

  intptr_t offset;
};

class IpPromise: public MyPromise {
 public:
  IpPromise(intptr_t logicalIp):
    logicalIp(logicalIp)
  { }

  virtual intptr_t value(Context* c) {
    if (resolved(c)) {
      unsigned bottom = 0;
      unsigned top = c->plan.length() / BytesPerWord;
      for (unsigned span = top - bottom; span; span = top - bottom) {
        unsigned middle = bottom + (span / 2);
        Segment* s = c->segmentTable[middle];

        if (logicalIp == s->logicalIp) {
          return reinterpret_cast<intptr_t>(c->code.data + s->offset);
        } else if (logicalIp < s->logicalIp) {
          top = middle;
        } else if (logicalIp > s->logicalIp) {
          bottom = middle + 1;
        }
      }
    }

    abort(c);
  }

  virtual bool resolved(Context* c) {
    return c->codeLength >= 0;
  }

  intptr_t logicalIp;
};

AddressOperand*
address(Context* c, MyPromise* p);

ImmediateOperand*
immediate(Context* c, int64_t v);

AbsoluteOperand*
absolute(Context* c, MyPromise* v);

RegisterOperand*
register_(Context* c, RegisterReference*);

RegisterOperand*
register_(Context* c, Register = NoRegister, Register = NoRegister);

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale, Compiler::TraceHandler* traceHandler);

class MyOperand: public Operand {
 public:
  enum Operation {
    push1,
    push2,
    push2z,
    push4,
    push8,
    pop4,
    pop8,
    call,
    alignedCall,
    ret,
    mov1,
    mov2,
    mov4,
    mov8,
    mov1ToW,
    mov2ToW,
    mov2zToW,
    mov4To8,
    cmp4,
    cmp8,
    jl,
    jg,
    jle,
    jge,
    je,
    jne,
    jmp,
    add4,
    add8,
    sub4,
    sub8,
    mul4,
    mul8,
    div4,
    div8,
    rem4,
    rem8,
    shl4,
    shl8,
    shr4,
    shr8,
    ushr4,
    ushr8,
    and4,
    and8,
    or4,
    or8,
    xor4,
    xor8,
    neg4,
    neg8,
    addc,
    subb
  };

  static const Operation push = (BytesPerWord == 8 ? push8 : push4);
  static const Operation pop = (BytesPerWord == 8 ? pop8 : pop4);
  static const Operation mov = (BytesPerWord == 8 ? mov8 : mov4);
  static const Operation cmp = (BytesPerWord == 8 ? cmp8 : cmp4);
  static const Operation add = (BytesPerWord == 8 ? add8 : add4);
  static const Operation sub = (BytesPerWord == 8 ? sub8 : sub4);
  static const Operation mul = (BytesPerWord == 8 ? mul8 : mul4);
  static const Operation neg = (BytesPerWord == 8 ? neg8 : neg4);

  virtual ~MyOperand() { }

  virtual Register asRegister(Context* c) { abort(c); }

  virtual RegisterNode* dependencies(Context*, RegisterNode* next)
  { return next; }

  virtual void release(Context*) { /* ignore */ }

  virtual void setLabelValue(Context* c, MyPromise*) { abort(c); }

  virtual void apply(Context*, Operation) = 0;

  virtual void apply(Context*, Operation, MyOperand*) = 0;

  virtual void accept(Context* c, Operation, RegisterOperand*) = 0;

  virtual void accept(Context* c, Operation, ImmediateOperand*) = 0;

  virtual void accept(Context* c, Operation, AddressOperand*) = 0;

  virtual void accept(Context* c, Operation, AbsoluteOperand*) = 0;

  virtual void accept(Context* c, Operation, MemoryOperand*) = 0;
};

void
acquire(Context* c, Register v)
{
  assert(c, not c->registers[v].reserved);
  if (Verbose) {
    fprintf(stderr, "acquire %d\n", v);
  }
  c->registers[v].reserved = true;
}

Register
acquire(Context* c)
{
  // we don't yet support using r9-r15
  for (int i = 8/*RegisterCount*/ - 1; i >= 0; --i) {
    if (not c->registers[i].reserved) {
      acquire(c, static_cast<Register>(i));
      return static_cast<Register>(i);
    }
  }

  abort(c);
}

void
release(Context* c, Register v)
{
  assert(c, c->registers[v].reserved);
  if (Verbose) {
    fprintf(stderr, "release %d\n", v);
  }
  c->registers[v].reserved = false;
}

class RegisterReference {
 public:
  RegisterReference(Register value = NoRegister, Register high = NoRegister):
    value_(value), defaultValue(value), high_(high), defaultHigh(high),
    acquired(true)
  { }

  void acquire(Context* c) {
    if (defaultValue != NoRegister) {
      ::acquire(c, defaultValue);
    }

    if (defaultHigh != NoRegister) {
      ::acquire(c, defaultHigh);
    }

    value_ = defaultValue;
    high_ = defaultHigh;

    acquired = true;
  }

  void release(Context* c) {
    assert(c, acquired);

    if (value_ != NoRegister) {
      ::release(c, value_);
    }

    if (high_ != NoRegister) {
      ::release(c, high_);
    }

    value_ = NoRegister;
    high_ = NoRegister;

    acquired = false;
  }

  Register value(Context* c) {
    assert(c, acquired);
    if (value_ == NoRegister) {
      value_ = ::acquire(c);
    }
    return value_;
  }

  Register high(Context* c) {
    assert(c, acquired);
    if (high_ == NoRegister) {
      high_ = ::acquire(c);
    }
    return high_;
  }

  Register value_;
  Register defaultValue;
  Register high_;
  Register defaultHigh;
  bool acquired;
};

class RegisterOperand: public MyOperand {
 public:
  RegisterOperand(RegisterReference* reference):
    reference(reference)
  { }

  Register value(Context* c) {
    return reference->value(c);
  }

  Register high(Context* c) {
    return reference->high(c);
  }

  virtual Register asRegister(Context* c) {
    return value(c);
  }

  virtual RegisterNode* dependencies(Context* c, RegisterNode* next) {
    return new (c->zone->allocate(sizeof(RegisterNode)))
      RegisterNode(value(c), next);
  }

  virtual void release(Context* c) {
    reference->release(c);
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation op, MyOperand* operand) {
    operand->accept(c, op, this);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AddressOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context*, Operation, MemoryOperand*);

  RegisterReference* reference;
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(int64_t value):
    value(value)
  { }

  virtual void apply(Context* c, Operation op);

  virtual void apply(Context* c, Operation op, MyOperand* operand) {
    operand->accept(c, op, this);
  }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AddressOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }

  int64_t value;
};

class AddressOperand: public MyOperand {
 public:
  AddressOperand(MyPromise* promise):
    promise(promise)
  { }

  virtual Register asRegister(Context* c);

  virtual void setLabelValue(Context*, MyPromise*);

  virtual void apply(Context*, Operation);
  virtual void apply(Context* c, Operation op, MyOperand* operand) {
    operand->accept(c, op, this);
  }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AddressOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }

  MyPromise* promise;
};

class AbsoluteOperand: public MyOperand {
 public:
  AbsoluteOperand(MyPromise* promise):
    promise(promise)
  { }

  virtual Register asRegister(Context* c);

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation op, MyOperand* operand) {
    operand->accept(c, op, this);
  }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AddressOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }

  MyPromise* promise;
};

class MemoryOperand: public MyOperand {
 public:
  MemoryOperand(MyOperand* base, int displacement, MyOperand* index,
                unsigned scale, Compiler::TraceHandler* traceHandler):
    base(base),
    displacement(displacement),
    index(index),
    scale(scale),
    traceHandler(traceHandler)
  { }

  MemoryOperand* high(Context* c) {
    return memory
      (c, base, displacement + BytesPerWord, index, scale, traceHandler);
  }

  virtual Register asRegister(Context*);

  virtual RegisterNode* dependencies(Context* c, RegisterNode* next) {
    next = base->dependencies(c, next);
    if (index) {
      return index->dependencies(c, next);
    } else {
      return next;
    }
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation op, MyOperand* operand) {
    operand->accept(c, op, this);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context* c, Operation, AddressOperand*) { abort(c); }
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context*, Operation, MemoryOperand*);

  MyOperand* base;
  int displacement;
  MyOperand* index;
  unsigned scale;
  Compiler::TraceHandler* traceHandler;
};

class CodePromiseTask: public Task {
 public:
  CodePromiseTask(CodePromise* promise, Task* next):
    Task(next), promise(promise)
  { }

  virtual void run(Context* c UNUSED, unsigned offset) {
    promise->offset = offset;
  }

  CodePromise* promise;
};

AddressOperand*
address(Context* c, MyPromise* p)
{
  return new (c->zone->allocate(sizeof(AddressOperand))) AddressOperand(p);
}

ImmediateOperand*
immediate(Context* c, int64_t v)
{
  return new (c->zone->allocate(sizeof(ImmediateOperand)))
    ImmediateOperand(v);
}

AbsoluteOperand*
absolute(Context* c, MyPromise* v)
{
  return new (c->zone->allocate(sizeof(AbsoluteOperand))) AbsoluteOperand(v);
}

RegisterOperand*
register_(Context* c, RegisterReference* r)
{
  return new (c->zone->allocate(sizeof(RegisterOperand)))
    RegisterOperand(r);
}

RegisterOperand*
register_(Context* c, Register v, Register h)
{
  RegisterReference* r = new (c->zone->allocate(sizeof(RegisterReference)))
    RegisterReference(v, h);
  return register_(c, r);
}

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale, Compiler::TraceHandler* traceHandler)
{
  return new (c->zone->allocate(sizeof(MemoryOperand)))
    MemoryOperand(base, displacement, index, scale, traceHandler);
}

RegisterOperand*
temporary(Context* c)
{
  return register_(c, acquire(c));
}

RegisterOperand*
temporary(Context* c, Register v)
{
  acquire(c, v);
  return register_(c, v);
}

RegisterOperand*
temporary(Context* c, Register v, Register h)
{
  acquire(c, v);
  acquire(c, h);
  return register_(c, v, h);
}

Segment*
currentSegment(Context* c)
{
  Segment* s; c->plan.get(c->plan.length() - BytesPerWord, &s, BytesPerWord);
  return s;
}

Promise*
machineIp(Context* c)
{
  CodePromise* p = new (c->zone->allocate(sizeof(CodePromise))) CodePromise();
  
  Segment* s = currentSegment(c);
  s->event->task = new (c->zone->allocate(sizeof(CodePromiseTask)))
    CodePromiseTask(p, s->event->task);
  
  return p;
}

void
apply(Context* c, MyOperand::Operation op)
{
  switch (op) {
  case MyOperand::ret:
    c->code.append(0xc3);
    break;

  default: abort(c);
  }
}

class OpEvent: public Event {
 public:
  OpEvent(MyOperand::Operation op, Event* next):
    Event(next), op(op)
  { }

  virtual void run(Context* c) {
    apply(c, op);
  }

  MyOperand::Operation op;
};

class UnaryOpEvent: public Event {
 public:
  UnaryOpEvent(MyOperand::Operation op, Operand* operand, Event* next):
    Event(next),
    op(op),
    operand(static_cast<MyOperand*>(operand))
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "unary %d\n", op);
    }
    operand->apply(c, op);
  }

  MyOperand::Operation op;
  MyOperand* operand; 
};

class BinaryOpEvent: public Event {
 public:
  BinaryOpEvent(MyOperand::Operation op, Operand* a, Operand* b,
                Event* next):
    Event(next),
    op(op),
    a(static_cast<MyOperand*>(a)),
    b(static_cast<MyOperand*>(b))
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "binary %d\n", op);
    }
    a->apply(c, op, b);
  }

  MyOperand::Operation op;
  MyOperand* a; 
  MyOperand* b;
};

class AcquireEvent: public Event {
 public:
  AcquireEvent(RegisterOperand* operand, Event* next):
    Event(next),
    operand(operand)
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "acquire register\n");
    }
    operand->reference->acquire(c);
  }

  RegisterOperand* operand; 
};

class ReleaseEvent: public Event {
 public:
  ReleaseEvent(Operand* operand, Event* next):
    Event(next),
    operand(static_cast<MyOperand*>(operand))
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "release register\n");
    }
    operand->release(c);
  }

  MyOperand* operand; 
};


class Movement {
 public:
  MyOperand* source;
  Register destination;
  RegisterNode* dependencies;
};

void
push(Context* c, Movement* table, unsigned size)
{
  int pushed[size];
  unsigned pushIndex = 0;
  for (unsigned i = 0; i < size; ++i) {
    Movement* mi = table + i;
    for (unsigned j = i + 1; j < size; ++j) {
      Movement* mj = table + j;
      for (RegisterNode* d = mj->dependencies; d; d = d->next) {
        if (mi->destination == d->value) {
          mi->source->apply(c, MyOperand::push);
          pushed[pushIndex++] = i;
          goto loop;
        }
      }
    }

    mi->source->apply
      (c, MyOperand::mov, register_(c, mi->destination));
  loop:;    
  }

  for (int i = pushIndex - 1; i >= 0; --i) {
    register_(c, table[pushed[i]].destination)->apply
      (c, MyOperand::pop);
  }
}

Register
gpRegister(Context* c, unsigned index)
{
  switch (index) {
  case 0:
    return rdi;
  case 1:
    return rsi;
  case 2:
    return rdx;
  case 3:
    return rcx;
  case 4:
    return r8;
  case 5:
    return r9;
  default:
    abort(c);
  }
}

class ArgumentEvent: public Event {
 public:
  ArgumentEvent(MyOperand** arguments, unsigned count, Event* next):
    Event(next),
    arguments(arguments),
    count(count)
  { }

  virtual void run(Context* c) {
    if (BytesPerWord == 8) {
      const unsigned size = min(count, GprParameterCount);
      Movement moveTable[size];

      for (int i = count - 1; i >= 0; --i) {
        if (static_cast<unsigned>(i) < GprParameterCount) {
          Movement* m = moveTable + (size - i - 1);
          m->source = arguments[i];
          m->destination = gpRegister(c, i);
          m->dependencies = arguments[i]->dependencies(c, 0);
        } else {
          arguments[i]->apply(c, MyOperand::push8);
        }
      }

      push(c, moveTable, size);
    } else {
      for (int i = count - 1; i >= 0; --i) {
        if (i > 0 and arguments[i - 1] == 0) {
          arguments[i]->apply(c, MyOperand::push8);
          -- i;
        } else {
          arguments[i]->apply(c, MyOperand::push4);
        }
      }
    }
  }

  MyOperand** arguments; 
  unsigned count;
};

void
appendOperation(Context* c, MyOperand::Operation op)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(OpEvent)))
    OpEvent(op, s->event);
}

void
appendOperation(Context* c, MyOperand::Operation op, Operand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(UnaryOpEvent)))
    UnaryOpEvent(op, operand, s->event);
}

void
appendOperation(Context* c, MyOperand::Operation op, Operand* a, Operand* b)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(BinaryOpEvent)))
    BinaryOpEvent(op, a, b, s->event);
}

void
appendAcquire(Context* c, RegisterOperand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(AcquireEvent)))
    AcquireEvent(operand, s->event);
}

void
appendRelease(Context* c, Operand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(ReleaseEvent)))
    ReleaseEvent(operand, s->event);
}

void
appendArgumentEvent(Context* c, MyOperand** arguments, unsigned count)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone->allocate(sizeof(ArgumentEvent)))
    ArgumentEvent(arguments, count, s->event);
}

void
logStack(Context* c, MyStack* stack)
{
  fprintf(stderr, "ip %3d: ", currentSegment(c)->logicalIp);

  if (stack) {
    fprintf(stderr, " %d",
            static_cast<MemoryOperand*>(stack->value)->displacement);
  }

  for (MyStack* s = stack; s; s = s->next) {
    fprintf(stderr, "*");
  }

  fprintf(stderr, "\n");
}

MyStack*
pushed(Context* c, MyStack* stack)
{
  int index = (stack ? stack->index + 1 : 0);

  MyOperand* value = memory
    (c, register_(c, rbp), - (c->reserved + index + 1) * BytesPerWord, 0, 1,
     0);

  stack = new (c->zone->allocate(sizeof(MyStack)))
    MyStack(value, index, stack);

  if (Verbose) {
    logStack(c, stack);
  }

  return stack;
}

MyStack*
push(Context* c, MyStack* stack, MyOperand::Operation op, MyOperand* v)
{
  appendOperation(c, op, v);

  if (BytesPerWord == 4 and op == MyOperand::push8) {
    stack = pushed(c, stack);
  }
  return pushed(c, stack);
}

MyStack*
pop(Context* c, MyStack* stack, int count)
{
  appendOperation
    (c, MyOperand::add,
     immediate(c, count * BytesPerWord),
     register_(c, rsp));

  while (count) {
    -- count;
    assert(c, count >= 0);
    stack = stack->next;
  }

  if (Verbose) {
    logStack(c, stack);
  }

  return stack;
}

MyStack*
pop(Context* c, MyStack* stack, MyOperand::Operation op, MyOperand* dst)
{
  appendOperation(c, op, dst);

  if (BytesPerWord == 4 and op == MyOperand::pop8) {
    stack = stack->next;
  }

  if (Verbose) {
    logStack(c, stack->next);
  }

  return stack->next;
}

void
pushArguments(Context* c, unsigned count, va_list list)
{
  MyOperand** arguments = static_cast<MyOperand**>
    (c->zone->allocate(count * BytesPerWord));

  unsigned index = 0;
  for (unsigned i = 0; i < count; ++i) {
    if (BytesPerWord == 8) {
      arguments[index] = va_arg(list, MyOperand*);
      if (arguments[index]) {
        ++ index;
      }
    } else {
      arguments[index++] = va_arg(list, MyOperand*);
    }
  }

  appendArgumentEvent(c, arguments, index);
}

unsigned
argumentFootprint(unsigned count)
{
  if (BytesPerWord == 8) {
    if (count > GprParameterCount) {
      return (count - GprParameterCount) * BytesPerWord;
    } else {
      return 0;
    }
  } else {
    return count * BytesPerWord;
  }
}

void
rex(Context* c)
{
  if (BytesPerWord == 8) {
    c->code.append(0x48);
  }
}

void
encode(Context* c, uint8_t* instruction, unsigned length, int a, Register b,
       int32_t displacement, int index, unsigned scale)
{
  c->code.append(instruction, length);

  uint8_t width;
  if (displacement == 0 and b != rbp) {
    width = 0;
  } else if (isInt8(displacement)) {
    width = 0x40;
  } else {
    width = 0x80;
  }

  if (index == -1) {
    c->code.append(width | (a << 3) | b);
    if (b == rsp) {
      c->code.append(0x24);
    }
  } else {
    assert(c, b != rsp);
    c->code.append(width | (a << 3) | 4);
    c->code.append((log(scale) << 6) | (index << 3) | b);
  }

  if (displacement == 0 and b != rbp) {
    // do nothing
  } else if (isInt8(displacement)) {
    c->code.append(displacement);
  } else {
    c->code.append4(displacement);
  }
}

void
encode(Context* c, uint8_t instruction, int a, MemoryOperand* b, bool rex)
{
  Register r = b->base->asRegister(c);
  int index = b->index ? b->index->asRegister(c) : -1;

  if (b->traceHandler and c->codeLength >= 0) {
    b->traceHandler->handleTrace
      (resolved(c, reinterpret_cast<intptr_t>
                (c->code.data + c->code.length())));
  }

  if (rex) {
    ::rex(c);
  }
  encode(c, &instruction, 1, a, r, b->displacement, index, b->scale);
}

void
encode2(Context* c, uint16_t instruction, int a, MemoryOperand* b, bool rex)
{
  Register r = b->base->asRegister(c);
  int index = b->index ? b->index->asRegister(c) : -1;

  if (b->traceHandler and c->codeLength >= 0) {
    b->traceHandler->handleTrace
      (resolved(c, reinterpret_cast<intptr_t>
                (c->code.data + c->code.length())));
  }

  if (rex) {
    ::rex(c);
  }
  uint8_t i[2] = { instruction >> 8, instruction & 0xff };
  encode(c, i, 2, a, r, b->displacement, index, b->scale);
}

void
RegisterOperand::apply(Context* c, Operation op)
{
  switch (op) {
  case call: {
    c->code.append(0xff);
    c->code.append(0xd0 | value(c));
  } break;

  case jmp:
    c->code.append(0xff);
    c->code.append(0xe0 | value(c));
    break;

  case pop4:
  case pop8:
    if (BytesPerWord == 4 and op == pop8) {
      apply(c, pop);
      register_(c, high(c))->apply(c, pop);
    } else {
      c->code.append(0x58 | value(c));
      if (BytesPerWord == 8 and op == pop4) {
        accept(c, mov4To8, this);
      }
    }
    break;

  case push4:
  case push8:
    if (BytesPerWord == 4 and op == push8) {
      register_(c, high(c))->apply(c, push);
      apply(c, push);
    } else {
      c->code.append(0x50 | value(c));      
    }
    break;

  case neg4:
  case neg8:
    assert(c, BytesPerWord == 8 or op == neg4); // todo

    rex(c);
    c->code.append(0xf7);
    c->code.append(0xd8 | value(c));
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation op,
                        RegisterOperand* operand)
{
  switch (op) {
  case add:
    rex(c);
    c->code.append(0x01);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case cmp4:
  case cmp8:
    if (BytesPerWord == 4 and op == cmp8) {
      register_(c, high(c))->accept
        (c, cmp, register_(c, operand->high(c)));

      // if the high order bits are equal, we compare the low order
      // bits; otherwise, we jump past that comparison
      c->code.append(0x0f);
      c->code.append(0x85); // jne
      c->code.append4(2);

      accept(c, cmp, operand);
    } else {
      if (op == cmp8) rex(c);
      c->code.append(0x39);
      c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    }
    break;

  case mov4:
  case mov8:
    if (BytesPerWord == 4 and op == mov8) {
      accept(c, mov, operand);
      
      register_(c, high(c))->accept
        (c, mov, register_(c, operand->high(c)));
    } else if (value(c) != operand->value(c)) {
      rex(c);
      c->code.append(0x89);
      c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    }
    break;

  case mov1ToW:
    c->code.append(0xbe);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case mov2ToW:
    c->code.append(0xbf);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case mov2zToW:
    c->code.append(0xb7);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case mov4To8:
    assert(c, BytesPerWord == 8);
    rex(c);
    c->code.append(0x63);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case mul4:
  case mul8:
    assert(c, BytesPerWord == 8 or op == mul4); // todo

    rex(c);
    c->code.append(0x0f);
    c->code.append(0xaf);
    c->code.append(0xc0 | (value(c) << 3) | operand->value(c));
    break;

  case xor4:
    rex(c);
    c->code.append(0x31);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation op,
                        ImmediateOperand* operand)
{
  switch (op) {
  case add4:
  case add8:
    assert(c, BytesPerWord == 8 or op == add4); // todo

    if (operand->value) {
      rex(c);
      if (isInt8(operand->value)) {
        c->code.append(0x83);
        c->code.append(0xc0 | value(c));
        c->code.append(operand->value);
      } else if (isInt32(operand->value)) {
        c->code.append(0x81);
        c->code.append(0xc0 | value(c));
        c->code.append4(operand->value);        
      } else {
        abort(c);
      }
    }
    break;

  case addc:
    if (isInt8(operand->value)) {
      c->code.append(0x83);
      c->code.append(0xd0 | value(c));
      c->code.append(operand->value);
    } else {
      abort(c);
    }
    break;

  case and4:
  case and8:
    assert(c, BytesPerWord == 8 or op == and4); // todo

    rex(c);
    if (isInt8(operand->value)) {
      c->code.append(0x83);
      c->code.append(0xe0 | value(c));
      c->code.append(operand->value);
    } else {
      assert(c, isInt32(operand->value));

      c->code.append(0x81);
      c->code.append(0xe0 | value(c));
      c->code.append(operand->value);
    }
    break;

  case cmp4:
  case cmp8: {
    assert(c, BytesPerWord == 8 or op == cmp4); // todo

    if (op == cmp8) rex(c);
    if (isInt8(operand->value)) {
      c->code.append(0x83);
      c->code.append(0xf8 | value(c));
      c->code.append(operand->value);
    } else {
      assert(c, isInt32(operand->value));

      c->code.append(0x81);
      c->code.append(0xf8 | value(c));
      c->code.append4(operand->value);
    }
  } break;

  case mov4:
  case mov8: {
    assert(c, BytesPerWord == 8 or op == mov4); // todo

    rex(c);
    c->code.append(0xb8 | value(c));
    c->code.appendAddress(operand->value);
  } break;

  case shl4:
  case shl8: {
    assert(c, BytesPerWord == 8 or op == shl4); // todo

    if (operand->value) {
      rex(c);
      if (operand->value == 1) {
        c->code.append(0xd1);
        c->code.append(0xe0 | value(c));
      } else {
        assert(c, isInt8(operand->value));

        c->code.append(0xc1);
        c->code.append(0xe0 | value(c));
        c->code.append(operand->value);
      }
    }
  } break;

  case sub4:
  case sub8: {
    assert(c, BytesPerWord == 8 or op == sub4); // todo

    if (operand->value) {
      rex(c);
      if (isInt8(operand->value)) {
        c->code.append(0x83);
        c->code.append(0xe8 | value(c));
        c->code.append(operand->value);
      } else if (isInt32(operand->value)) {
        c->code.append(0x81);
        c->code.append(0xe8 | value(c));
        c->code.append4(operand->value);        
      } else {
        abort(c);
      }
    }
  } break;

  default: abort(c);
  }
}

ImmediateOperand*
value(Context* c, AddressOperand* operand)
{
  if (c->codeLength and operand->promise->resolved(c)) {
    return immediate(c, operand->promise->value(c));
  } else {
    return immediate(c, 0);
  }
}

void
RegisterOperand::accept(Context* c, Operation op,
                        AddressOperand* operand)
{
  switch (op) {
  case mov: {
    accept(c, op, ::value(c, operand));
  } break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation op,
                        MemoryOperand* operand)
{
  switch (op) {
  case cmp4:
  case cmp8:
    assert(c, BytesPerWord == 8 or op == cmp4); // todo

    encode(c, 0x3b, value(c), operand, true);
    break;

  case mov4:
  case mov8:
    if (BytesPerWord == 4 and op == mov8) {
      accept(c, mov, operand);

      register_(c, high(c))->accept(c, mov, operand->high(c));
    } else if (BytesPerWord == 8 and op == mov4) {
      encode(c, 0x63, value(c), operand, true);
    } else {
      encode(c, 0x8b, value(c), operand, true);
    }
    break;

  case mov1ToW:
    encode2(c, 0x0fbe, value(c), operand, true);
    break;

  case mov2ToW:
    encode2(c, 0x0fbf, value(c), operand, true);
  break;

  case mov2zToW:
    encode2(c, 0x0fb7, value(c), operand, true);
  break;

  case mov4To8:
    assert(c, BytesPerWord == 8); // todo

    encode(c, 0x63, value(c), operand, true);
    break;

  case mul4:
  case mul8:
    assert(c, BytesPerWord == 8 or op == mul4); // todo

    encode2(c, 0x0faf, value(c), operand, true);
    break;

  default: abort(c);
  }
}

ImmediateOperand*
value(Context* c, AbsoluteOperand* operand)
{
  if (c->codeLength and operand->promise->resolved(c)) {
    return immediate(c, operand->promise->value(c));
  } else {
    return immediate(c, 0);
  }
}

void
RegisterOperand::accept(Context* c, Operation op,
                        AbsoluteOperand* operand)
{
  switch (op) {
  case cmp4:
  case cmp8: {
    assert(c, BytesPerWord == 8 or op == cmp4); // todo

    RegisterOperand* tmp = temporary(c);
    tmp->accept(c, mov, ::value(c, operand));
    accept(c, cmp, memory(c, tmp, 0, 0, 1, 0));
    tmp->release(c);
  } break;

  case mov4:
  case mov8: {
    assert(c, BytesPerWord == 8 or op == mov4); // todo

    accept(c, mov, ::value(c, operand));
    accept(c, mov, memory(c, this, 0, 0, 1, 0));
  } break;

  default: abort(c);
  }
}

void
unconditional(Context* c, unsigned jump, AddressOperand* operand)
{
  intptr_t v;
  if (c->codeLength and operand->promise->resolved(c)) {
    uint8_t* instruction = c->code.data + c->code.length();
    v = reinterpret_cast<uint8_t*>(operand->promise->value(c))
      - instruction - 5;
  } else {
    v = 0;
  }

  expect(c, isInt32(v));
  
  c->code.append(jump);
  c->code.append4(v);
}

void
conditional(Context* c, unsigned condition, AddressOperand* operand)
{
  intptr_t v;
  if (c->codeLength and operand->promise->resolved(c)) {
    uint8_t* instruction = c->code.data + c->code.length();
    v = reinterpret_cast<uint8_t*>(operand->promise->value(c))
      - instruction - 6;
  } else {
    v = 0;
  }

  expect(c, isInt32(v));
  
  c->code.append(0x0f);
  c->code.append(condition);
  c->code.append4(v);
}

void
AddressOperand::setLabelValue(Context*, MyPromise* p)
{
  promise = p;
}

void
AddressOperand::apply(Context* c, Operation op)
{
  switch (op) {
  case alignedCall: {
    while ((c->code.length() + 1) % 4) {
      c->code.append(0x90);
    }
    apply(c, call);
  } break;

  case call: {
    unconditional(c, 0xe8, this);
  } break;

  case jmp:
    unconditional(c, 0xe9, this);
    break;

  case je:
    conditional(c, 0x84, this);
    break;

  case jne:
    conditional(c, 0x85, this);
    break;

  case jg:
    conditional(c, 0x8f, this);
    break;

  case jge:
    conditional(c, 0x8d, this);
    break;


  case jl:
    conditional(c, 0x8c, this);
    break;

  case jle:
    conditional(c, 0x8e, this);
    break;

  case push4:
  case push8: {
    assert(c, BytesPerWord == 8 or op == push4); // todo

    RegisterOperand* tmp = temporary(c);
    tmp->accept(c, mov, this);
    tmp->apply(c, push);
    tmp->release(c);
  } break;
    
  default: abort(c);
  }
}

Register
AddressOperand::asRegister(Context* c)
{
  intptr_t v;
  if (c->codeLength >= 0) {
    v = promise->value(c);
  } else {
    v = 0;
  }
  
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, mov, immediate(c, v));
  Register r = tmp->value(c);
  tmp->release(c);
  return r;
}

void
ImmediateOperand::apply(Context* c, Operation op)
{
  switch (op) {
  case alignedCall:
  case call:
  case jmp:
    address(c, resolved(c, value))->apply(c, op);
    break;

  case push4:
  case push8:
    if (BytesPerWord == 4 and op == push8) {
      immediate(c, (value >> 32) & 0xFFFFFFFF)->apply
        (c, push);
      immediate(c, (value      ) & 0xFFFFFFFF)->apply
        (c, push);
    } else {
      if (isInt8(value)) {
        c->code.append(0x6a);
        c->code.append(value);
      } else if (isInt32(value)) {
        c->code.append(0x68);
        c->code.append4(value);
      } else {
        RegisterOperand* tmp = temporary(c);
        tmp->accept(c, mov, this);
        tmp->apply(c, push);
        tmp->release(c);
      }
    }
    break;
    
  default: abort(c);
  }
}

Register
AbsoluteOperand::asRegister(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, MyOperand::mov, this);
  Register v = tmp->value(c);
  tmp->release(c);
  return v;
}

void
absoluteApply(Context* c, MyOperand::Operation op,
              AbsoluteOperand* operand)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, MyOperand::mov, value(c, operand));
  memory(c, tmp, 0, 0, 1, 0)->apply(c, op);
  tmp->release(c);
}

void
AbsoluteOperand::apply(Context* c, Operation op)
{
  switch (op) {
  case push:
    absoluteApply(c, op, this);
    break;

  default: abort(c);
  }
}

Register
MemoryOperand::asRegister(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, mov, this);
  Register v = tmp->value(c);
  tmp->release(c);
  return v;
}

void
MemoryOperand::apply(Context* c, Operation op)
{
  switch (op) {
  case call:
    encode(c, 0xff, 2, this, false);
    break;

  case jmp:
    encode(c, 0xff, 4, this, false);
    break;

  case neg4:
  case neg8:
    if (BytesPerWord == 4 and op == neg8) {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      MemoryOperand* low = this;
      MemoryOperand* high = this->high(c);

      ax->accept(c, mov, low);
      dx->accept(c, mov, high);

      ax->apply(c, neg);
      dx->accept(c, addc, immediate(c, 0));
      dx->apply(c, neg);

      low->accept(c, mov, ax);
      high->accept(c, mov, dx);

      ax->release(c);
      dx->release(c);
    } else {
      encode(c, 0xf7, 3, this, true);
    }
    break;

  case pop4:
  case pop8:
    if (BytesPerWord == 4 and op == pop8) {
      MemoryOperand* low = this;
      MemoryOperand* high = this->high(c);

      low->apply(c, pop);
      high->apply(c, pop);
    } else if (BytesPerWord == 8 and op == pop4) {
      abort(c);
    } else {
      encode(c, 0x8f, 0, this, false);
    }
    break;

  case push4:
  case push8:
    if (BytesPerWord == 4 and op == push8) {
      MemoryOperand* low = this;
      MemoryOperand* high = this->high(c);
        
      high->apply(c, push);
      low->apply(c, push);
    } else if (BytesPerWord == 8 and op == push4) {
      RegisterOperand* tmp = temporary(c);
      tmp->accept(c, mov4, this);
      tmp->apply(c, op);
      tmp->release(c);
    } else {
      encode(c, 0xff, 6, this, false);
    }
    break;
        
  case push1:
  case push2:
  case push2z: {
    RegisterOperand* tmp = temporary(c);
    switch (op) {
    case push1:
      tmp->accept(c, mov1ToW, this);
      break;

    case push2:
      tmp->accept(c, mov2ToW, this);
      break;

    case push2z:
      tmp->accept(c, mov2zToW, this);
      break;

    default: abort(c);
    }
    tmp->apply(c, push);
    tmp->release(c);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation op, RegisterOperand* operand)
{
  switch (op) {
  case and4:
  case and8:
    if (BytesPerWord == 4 and op == and8) {
      accept(c, and4, operand);
      high(c)->accept(c, and4, register_(c, operand->high(c)));
    } else {
      encode(c, 0x21, operand->value(c), this, true);
    }
    break;

  case add4:
  case add8:
    if (BytesPerWord == 4 and op == add8) {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      ax->accept(c, mov, operand);
      dx->accept(c, mov, register_(c, operand->high(c)));

      accept(c, add, ax);
      high(c)->accept(c, addc, dx);

      ax->release(c);
      dx->release(c);
    } else {
      encode(c, 0x01, operand->value(c), this, true);
    }
    break;

  case addc:
    encode(c, 0x11, operand->value(c), this, true);
    break;

  case div4:
  case div8:
    if (BytesPerWord == 4 and op == div8) {
      RegisterOperand* axdx = temporary(c, rax, rdx);

      operand->apply(c, push8);
      apply(c, push8);
      immediate(c, reinterpret_cast<intptr_t>(divideLong))->apply
        (c, call);
      register_(c, rsp)->accept
        (c, add, immediate(c, 16));
      accept(c, mov8, axdx);

      axdx->release(c);
    } else {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);
      ax->accept(c, mov, this);
    
      rex(c);
      c->code.append(0x99);
      rex(c);
      c->code.append(0xf7);
      c->code.append(0xf8 | operand->value(c));

      accept(c, mov, ax);

      ax->release(c);
      dx->release(c);
    }
    break;

  case mov4:
  case mov8:
    if (BytesPerWord == 4 and op == mov8) {
      accept(c, mov, operand);

      high(c)->accept
        (c, mov, register_(c, operand->high(c)));
    } else if (BytesPerWord == 8 and op == mov4) {
      encode(c, 0x89, operand->value(c), this, false);
    } else {
      encode(c, 0x89, operand->value(c), this, true);
    }
    break;

  case mov1:
    if (BytesPerWord == 8) {
      if (operand->value(c) > rbx) {
        c->code.append(0x40);
      }
      encode(c, 0x88, operand->value(c), this, false);
    } else {
      if (operand->value(c) > rbx) {
        RegisterOperand* ax = temporary(c, rax);
        ax->accept(c, mov, operand);
        accept(c, mov1, register_(c, rax));
        ax->release(c);
      } else {
        encode(c, 0x88, operand->value(c), this, false);
      }
    }
    break;

  case mov2:
    encode2(c, 0x6689, operand->value(c), this, false);
    break;

  case mov4To8:
    assert(c, BytesPerWord == 8);
    encode(c, 0x89, operand->value(c), this, false);
    break;

  case mul4:
  case mul8:
    if (BytesPerWord == 4 and op == mul8) {
      RegisterOperand* tmp = temporary(c, rcx);
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      RegisterOperand* lowSrc = operand;
      RegisterOperand* highSrc = register_(c, operand->high(c));

      MemoryOperand* lowDst = this;
      MemoryOperand* highDst = this->high(c);
      
      tmp->accept(c, mov, highSrc);
      tmp->accept(c, mul, lowDst);
      ax->accept(c, mov, highDst);
      ax->accept(c, mul, lowSrc);
      tmp->accept(c, add, ax);
      ax->accept(c, mov, lowDst);

      // mul lowSrc,%eax
      c->code.append(0xf7);
      c->code.append(0xe8 | lowSrc->value(c));

      dx->accept(c, add, tmp);

      lowDst->accept(c, mov, ax);
      highDst->accept(c, mov, dx);

      tmp->release(c);
      ax->release(c);
      dx->release(c);
    } else {
      RegisterOperand* tmp = temporary(c);

      tmp->accept(c, mov, this);
      tmp->accept(c, mul, operand);
      accept(c, mov, tmp);
    
      tmp->release(c);
    }
    break;

  case or4:
  case or8:
    if (BytesPerWord == 4 and op == or8) {
      accept(c, or4, operand);
      high(c)->accept(c, or4, register_(c, operand->high(c)));
    } else {
      encode(c, 0x09, operand->value(c), this, true);
    }
    break;

  case rem4:
  case rem8:
    if (BytesPerWord == 4 and op == rem8) {
      RegisterOperand* axdx = temporary(c, rax, rdx);

      operand->apply(c, push8);
      apply(c, push8);
      immediate(c, reinterpret_cast<intptr_t>(moduloLong))->apply
        (c, call);
      register_(c, rsp)->accept
        (c, add, immediate(c, 16));
      accept(c, mov8, axdx);

      axdx->release(c);
    } else {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);
      ax->accept(c, mov, this);
    
      rex(c);
      c->code.append(0x99);
      rex(c);
      c->code.append(0xf7);
      c->code.append(0xf8 | operand->value(c));

      accept(c, mov, dx);

      ax->release(c);
      dx->release(c);
    }
    break;

  case shl4:
  case shl8: {
    if (BytesPerWord == 4 and op == shl8) {
      RegisterOperand* count = temporary(c, rcx);
      RegisterOperand* low = temporary(c);
      RegisterOperand* high = temporary(c);

      count->accept(c, mov, operand);
      low->accept(c, mov, this);
      high->accept(c, mov, this->high(c));

      // shld
      c->code.append(0x0f);
      c->code.append(0xa5);
      c->code.append(0xc0 | (low->value(c) << 3) | high->value(c));

      // shl
      c->code.append(0xd3);
      c->code.append(0xe0 | low->value(c));

      count->accept(c, cmp, immediate(c, 32));
      c->code.append(0x0f);
      c->code.append(0x8c); // jl
      c->code.append4(2 + 2);

      high->accept(c, mov, low); // 2 bytes
      low->accept(c, xor4, low); // 2 bytes

      this->accept(c, mov, low);
      this->high(c)->accept(c, mov, high);

      high->release(c);
      low->release(c);
      count->release(c);
    } else {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 4, this, true);
      cx->release(c);
    }
  } break;

  case shr4:
  case shr8: {
    if (BytesPerWord == 4 and op == shr8) {
      RegisterOperand* count = temporary(c, rcx);
      RegisterOperand* low = temporary(c);
      RegisterOperand* high = temporary(c);

      count->accept(c, mov, operand);
      low->accept(c, mov, this);
      high->accept(c, mov, this->high(c));

      // shrd
      c->code.append(0x0f);
      c->code.append(0xad);
      c->code.append(0xc0 | (high->value(c) << 3) | low->value(c));

      // sar
      c->code.append(0xd3);
      c->code.append(0xf8 | high->value(c));

      count->accept(c, cmp, immediate(c, 32));
      c->code.append(0x0f);
      c->code.append(0x8c); // jl
      c->code.append4(2 + 3);

      low->accept(c, mov, high); // 2 bytes
      // sar 31,high
      c->code.append(0xc1);
      c->code.append(0xf8 | high->value(c));
      c->code.append(31);

      this->accept(c, mov, low);
      this->high(c)->accept(c, mov, high);

      high->release(c);
      low->release(c);
      count->release(c);
    } else {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 7, this, true);
      cx->release(c);
    }
  } break;

  case ushr4:
  case ushr8: {
    if (BytesPerWord == 4 and op == ushr8) {
      RegisterOperand* count = temporary(c, rcx);
      RegisterOperand* low = temporary(c);
      RegisterOperand* high = temporary(c);

      count->accept(c, mov, operand);
      low->accept(c, mov, this);
      high->accept(c, mov, this->high(c));

      // shld
      c->code.append(0x0f);
      c->code.append(0xa5);
      c->code.append(0xc0 | (high->value(c) << 3) | low->value(c));

      // shl
      c->code.append(0xd3);
      c->code.append(0xe8 | high->value(c));

      count->accept(c, cmp, immediate(c, 32));
      c->code.append(0x0f);
      c->code.append(0x8c); // jl
      c->code.append4(2 + 2);

      low->accept(c, mov, high); // 2 bytes
      high->accept(c, xor4, high); // 2 bytes

      this->accept(c, mov, low);
      this->high(c)->accept(c, mov, high);

      high->release(c);
      low->release(c);
      count->release(c);
    } else {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 5, this, true);
      cx->release(c);
    }
  } break;

  case sub4:
  case sub8:
    if (BytesPerWord == 4 and op == sub8) {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      ax->accept(c, mov, operand);
      dx->accept(c, mov, register_(c, operand->high(c)));

      accept(c, sub, ax);
      high(c)->accept(c, subb, dx);

      ax->release(c);
      dx->release(c);
    } else {
      encode(c, 0x29, operand->value(c), this, true);
    }
    break;

  case subb:
    encode(c, 0x19, operand->value(c), this, true);
    break;

  case xor4:
  case xor8: {
    if (BytesPerWord == 4 and op == xor8) {
      accept(c, xor4, operand);
      high(c)->accept(c, xor4, register_(c, operand->high(c)));
    } else {
      encode(c, 0x31, operand->value(c), this, true);
    }
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation op,
                      ImmediateOperand* operand)
{
  switch (op) {
  case add4:
  case add8: {
    assert(c, BytesPerWord == 8 or op == add4); // todo

    unsigned i = (isInt8(operand->value) ? 0x83 : 0x81);

    encode(c, i, 0, this, true);
    if (isInt8(operand->value)) {
      c->code.append(operand->value);
    } else if (isInt32(operand->value)) {
      c->code.append4(operand->value);
    } else {
      abort(c);
    }
  } break;

  case mov4:
  case mov8: {
    assert(c, isInt32(operand->value)); // todo
    assert(c, BytesPerWord == 8 or op == mov4); // todo

    encode(c, 0xc7, 0, this, true);
    c->code.append4(operand->value);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation op,
                      AbsoluteOperand* operand)
{
  RegisterOperand* tmp = temporary(c);
    
  tmp->accept(c, mov, operand);
  accept(c, op, tmp);

  tmp->release(c);
}

void
MemoryOperand::accept(Context* c, Operation op,
                      MemoryOperand* operand)
{
  switch (op) {
  case mov1ToW:
  case mov2ToW:
  case mov2zToW:
  case mov4To8: {
    if (BytesPerWord == 4 and op == mov4To8) {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);
          
      ax->accept(c, mov4, operand);
      c->code.append(0x99); // cdq
      accept(c, mov8, register_(c, rax, rdx));
          
      ax->release(c);
      dx->release(c);
    } else {
      RegisterOperand* tmp = temporary(c);
      tmp->accept(c, op, operand);
      accept(c, mov, tmp);
      tmp->release(c);
    }
  } break;

  case mov4:
  case mov8:
  case and4: {
    RegisterOperand* tmp = temporary(c);
    tmp->accept(c, mov, operand);
    accept(c, op, tmp);
    tmp->release(c);
  } break;

  default: abort(c);
  }
}

int
compareSegmentPointers(const void* a, const void* b)
{
  return (*static_cast<Segment* const*>(a))->logicalIp
    - (*static_cast<Segment* const*>(b))->logicalIp;
}

void
writeCode(Context* c)
{
  unsigned tableSize = (c->plan.length() / BytesPerWord);

  if (c->codeLength < 0) {
    c->segmentTable = static_cast<Segment**>
      (c->zone->allocate(c->plan.length()));
    
    for (unsigned i = 0; i < tableSize; ++i) {
      c->plan.get(i * BytesPerWord, c->segmentTable + i, BytesPerWord);
    }
    
    qsort(c->segmentTable, tableSize, BytesPerWord, compareSegmentPointers);
  }

  for (unsigned i = 0; i < tableSize; ++i) {
    Segment* s = c->segmentTable[i];
    if (Verbose) {
      fprintf(stderr, "\nip %d\n", s->logicalIp);
    }

    if (c->codeLength >= 0) {
      assert(c, s->offset == static_cast<int>(c->code.length()));
    } else {
      s->offset = c->code.length();
    }
      
    Event* events[s->event->count];
    unsigned ei = s->event->count;
    for (Event* e = s->event; e; e = e->next) {
      events[--ei] = e;
    }

    for (unsigned ei = 0; ei < s->event->count; ++ei) {
      if (Verbose and ei) {
        fprintf(stderr, "address %p\n", c->code.data + c->code.length());
      }

      events[ei]->run(c);

      if (c->codeLength < 0) {
        for (Task* t = events[ei]->task; t; t = t->next) {
          t->run(c, c->code.length());
        }
      }
    }
  }

  c->codeLength = pad(c->code.length());
}

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Zone* zone, void* indirectCaller):
    c(s, zone, indirectCaller)
  { }

  virtual Promise* machineIp(unsigned logicalIp) {
    return new (c.zone->allocate(sizeof(IpPromise))) IpPromise(logicalIp);
  }

  virtual Promise* poolAppend(intptr_t v) {
    return poolAppendPromise(resolved(&c, v));
  }

  virtual Promise* poolAppendPromise(Promise* v) {
    Promise* p = new (c.zone->allocate(sizeof(PoolPromise)))
      PoolPromise(c.constantPool.length());
    c.constantPool.appendAddress(v);
    return p;
  }

  virtual Operand* constant(int64_t v) {
    return immediate(&c, v);
  }

  virtual Operand* promiseConstant(Promise* p) {
    return address(&c, static_cast<MyPromise*>(p));
  }

  virtual Operand* absolute(Promise* p) {
    return ::absolute(&c, static_cast<MyPromise*>(p));
  }

  virtual Stack* push(Stack* s, unsigned count) {
    appendOperation
      (&c, MyOperand::sub,
       immediate(&c, count * BytesPerWord),
       register_(&c, rsp));

    return pushed(s, count);
  }

  virtual Stack* pushed(Stack* s, unsigned count) {
    MyStack* stack = static_cast<MyStack*>(s);
    while (count) {
      -- count;
      stack = ::pushed(&c, stack);
    }
    return stack;
  }

  virtual Stack* push1(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), MyOperand::push1,
                  static_cast<MyOperand*>(v));
  }

  virtual Stack* push2(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), MyOperand::push2,
                  static_cast<MyOperand*>(v));
  }

  virtual Stack* push2z(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), MyOperand::push2z,
                  static_cast<MyOperand*>(v));
  }

  virtual Stack* push4(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), MyOperand::push4,
                  static_cast<MyOperand*>(v));
  }

  virtual Stack* push8(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), MyOperand::push8,
                  static_cast<MyOperand*>(v));
  }

  virtual Operand* stack(Stack* s, unsigned index) {
    MyStack* stack = static_cast<MyStack*>(s);

    while (index) {
      -- index;
      stack = stack->next;
    }

    return stack->value;
  }

  virtual Stack* pop(Stack* s, unsigned count) {
    return ::pop(&c, static_cast<MyStack*>(s), count);
  }

  virtual Stack* pop4(Stack* s, Operand* dst) {
    return ::pop(&c, static_cast<MyStack*>(s), MyOperand::pop4,
                 static_cast<MyOperand*>(dst));
  }

  virtual Stack* pop8(Stack* s, Operand* dst) {
    return ::pop(&c, static_cast<MyStack*>(s), MyOperand::pop8,
                 static_cast<MyOperand*>(dst));
  }

  virtual Operand* stack() {
    return register_(&c, rsp);
  }

  virtual Operand* base() {
    return register_(&c, rbp);
  }

  virtual Operand* thread() {
    return register_(&c, rbx);
  }

  virtual Operand* indirectTarget() {
    return register_(&c, rax);
  }

  virtual Operand* temporary() {
    RegisterOperand* r = register_(&c);
    appendAcquire(&c, r);
    return r;
  }

  virtual void release(Operand* v) {
    appendRelease(&c, v);
  }

  virtual Operand* label() {
    return address(&c, 0);
  }

  Promise* machineIp() {
    CodePromise* p = new (c.zone->allocate(sizeof(CodePromise))) CodePromise();

    Segment* s = currentSegment(&c);
    s->event->task = new (c.zone->allocate(sizeof(CodePromiseTask)))
      CodePromiseTask(p, s->event->task);

    return p;
  }

  virtual void mark(Operand* label) {
    static_cast<MyOperand*>(label)->setLabelValue
      (&c, static_cast<MyPromise*>(machineIp()));
  }

  virtual void indirectCall
  (Operand* address, TraceHandler* traceHandler, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);
    va_end(a);

    appendOperation
      (&c, MyOperand::mov, address, register_(&c, rax));
    call(immediate(&c, c.indirectCaller), traceHandler);

    appendOperation
      (&c, MyOperand::add,
       immediate(&c, argumentFootprint(argumentCount)),
       register_(&c, rsp));
  }

  virtual void indirectCallNoReturn
  (Operand* address, TraceHandler* traceHandler, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);    
    va_end(a);

    appendOperation
      (&c, MyOperand::mov, address, register_(&c, rax));

    call(immediate(&c, c.indirectCaller), traceHandler);
  }

  virtual void directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);
    va_end(a);

    call(address, 0);

    appendOperation
      (&c, MyOperand::add,
       immediate(&c, argumentFootprint(argumentCount)),
       register_(&c, rsp));
  }

  virtual Operand* result4() {
    RegisterOperand* r = register_(&c, rax);
    appendAcquire(&c, r);
    return r;
  }

  virtual Operand* result8() {
    if (BytesPerWord == 8) {
      return result4();
    } else {
      RegisterOperand* r = register_(&c, rax, rdx);
      appendAcquire(&c, r);
      return r;
    }
  }

  virtual void return4(Operand* v) {
    appendOperation(&c, MyOperand::mov, v, register_(&c, rax));
    epilogue();
    ret();
  }

  virtual void return8(Operand* v) {
    if (BytesPerWord == 8) {
      return4(v);
    } else {
      appendOperation(&c, MyOperand::mov8, v, register_(&c, rax, rdx));
      epilogue();
      ret();
    }
  }

  virtual void call(Operand* v, TraceHandler* traceHandler) {
    appendOperation(&c, MyOperand::call, v);
    if (traceHandler) {
      traceHandler->handleTrace(machineIp());
    }
  }

  virtual void alignedCall(Operand* v, TraceHandler* traceHandler) {
    appendOperation(&c, MyOperand::alignedCall, v);
    if (traceHandler) {
      traceHandler->handleTrace(machineIp());
    }
  }

  virtual void ret() {
    appendOperation(&c, MyOperand::ret);
  }

  virtual void mov1(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov1, src, dst);
  }

  virtual void mov2(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov2, src, dst);
  }

  virtual void mov4(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov4, src, dst);
  }

  virtual void mov8(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov8, src, dst);
  }

  virtual void mov1ToW(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov1ToW, src, dst);
  }

  virtual void mov2ToW(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov2ToW, src, dst);
  }

  virtual void mov2zToW(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov2zToW, src, dst);
  }

  virtual void mov4To8(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov4To8, src, dst);
  }

  virtual void cmp4(Operand* subtrahend, Operand* minuend) {
    appendOperation(&c, MyOperand::cmp4, subtrahend, minuend);
  }

  virtual void cmp8(Operand* subtrahend, Operand* minuend) {
    appendOperation(&c, MyOperand::cmp8, subtrahend, minuend);
  }

  virtual void jl(Operand* v) {
    appendOperation(&c, MyOperand::jl, v);
  }

  virtual void jg(Operand* v) {
    appendOperation(&c, MyOperand::jg, v);
  }

  virtual void jle(Operand* v) {
    appendOperation(&c, MyOperand::jle, v);
  }

  virtual void jge(Operand* v) {
    appendOperation(&c, MyOperand::jge, v);
  }

  virtual void je(Operand* v) {
    appendOperation(&c, MyOperand::je, v);
  }

  virtual void jne(Operand* v) {
    appendOperation(&c, MyOperand::jne, v);
  }

  virtual void jmp(Operand* v) {
    appendOperation(&c, MyOperand::jmp, v);
  }

  virtual void add4(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::add4, v, dst);
  }

  virtual void add8(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::add8, v, dst);
  }

  virtual void sub4(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::sub4, v, dst);
  }

  virtual void sub8(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::sub8, v, dst);
  }

  virtual void mul4(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::mul4, v, dst);
  }

  virtual void mul8(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::mul8, v, dst);
  }

  virtual void div4(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::div4, v, dst);
  }

  virtual void div8(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::div8, v, dst);
  }

  virtual void rem4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::rem4, v, dst);
  }

  virtual void rem8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::rem8, v, dst);
  }

  virtual void shl4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shl4, v, dst);
  }

  virtual void shl8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shl8, v, dst);
  }

  virtual void shr4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shr4, v, dst);
  }

  virtual void shr8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shr8, v, dst);
  }

  virtual void ushr4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::ushr4, v, dst);
  }

  virtual void ushr8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::ushr8, v, dst);
  }

  virtual void and4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::and4, v, dst);
  }

  virtual void and8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::and8, v, dst);
  }

  virtual void or4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::or4, v, dst);
  }

  virtual void or8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::or8, v, dst);
  }

  virtual void xor4(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::xor4, v, dst);
  }

  virtual void xor8(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::xor8, v, dst);
  }

  virtual void neg4(Operand* v)  {
    appendOperation(&c, MyOperand::neg4, v);
  }

  virtual void neg8(Operand* v)  {
    appendOperation(&c, MyOperand::neg8, v);
  }

  virtual Operand* memory(Operand* base, int displacement,
                          Operand* index, unsigned scale,
                          TraceHandler* trace)
  {
    return ::memory(&c, static_cast<MyOperand*>(base), displacement,
                    static_cast<MyOperand*>(index), scale, trace);
  }

  virtual void prologue() {
    appendOperation(&c, MyOperand::push, register_(&c, rbp));
    appendOperation
      (&c, MyOperand::mov,  register_(&c, rsp), register_(&c, rbp));
  }

  virtual void reserve(unsigned size) {
    appendOperation
      (&c, MyOperand::sub, immediate(&c, size * BytesPerWord),
       register_(&c, rsp));

    c.reserved = size;
  }

  virtual void epilogue() {
    appendOperation
      (&c, MyOperand::mov, register_(&c, rbp), register_(&c, rsp));
    appendOperation(&c, MyOperand::pop, register_(&c, rbp));
  }

  virtual void startLogicalIp(unsigned ip) {
    c.plan.appendAddress
      (new (c.zone->allocate(sizeof(Segment)))
       Segment(ip, new (c.zone->allocate(sizeof(Event))) Event(0)));
  }

  virtual unsigned codeSize() {
    if (c.codeLength < 0) {
      assert(&c, c.code.length() == 0);
      writeCode(&c);
    }
    return c.codeLength;
  }

  virtual unsigned poolSize() {
    return c.constantPool.length();
  }

  virtual void writeTo(uint8_t* out) {
    c.code.wrap(out, codeSize());
    writeCode(&c);

    for (unsigned i = 0; i < c.constantPool.length(); i += BytesPerWord) {
      Promise* p; c.constantPool.get(i, &p, BytesPerWord);
      *reinterpret_cast<intptr_t*>(out + codeSize() + i) = p->value(this);
    }
  }

  virtual void updateCall(void* returnAddress, void* newTarget) {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    assert(&c, *instruction == 0xE8);
    assert(&c, reinterpret_cast<uintptr_t>(instruction + 1) % 4 == 0);

    int32_t v = static_cast<uint8_t*>(newTarget)
      - static_cast<uint8_t*>(returnAddress);
    memcpy(instruction + 1, &v, 4);
  }

  virtual void dispose() {
    c.dispose();
  }

  Context c;
};

intptr_t
MyPromise::value(Compiler* compiler)
{
  return value(&(static_cast<MyCompiler*>(compiler)->c));
}

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, Zone* zone, void* indirectCaller)
{
  return new (zone->allocate(sizeof(MyCompiler)))
    MyCompiler(system, zone, indirectCaller);
}

} // namespace v
