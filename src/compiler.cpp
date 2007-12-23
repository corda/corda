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

enum SelectionType {
  Select1,
  Select2,
  Select2z,
  Select4,
  Select8,
  Select8From4
};

const bool Verbose = false;

const unsigned RegisterCount = BytesPerWord * 2;
const unsigned GprParameterCount = 6;
const SelectionType DefaultSelection
= (BytesPerWord == 8 ? Select8 : Select4);

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

inline unsigned
footprint(SelectionType selection)
{
  switch (selection) {
  case Select8:
  case Select8From4:
    return 8;

  default:
    return BytesPerWord;
  }
}

int64_t
divideLong(int64_t a, int64_t b)
{
  return a / b;
}

int64_t
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
  Context(System* s, void* indirectCaller):
    s(s),
    constantPool(s, BytesPerWord * 32),
    plan(s, 1024),
    code(s, 1024),
    zone(s, 8 * 1024),
    indirectCaller(reinterpret_cast<intptr_t>(indirectCaller)),
    segmentTable(0),
    reserved(0),
    codeLength(-1)
  {
    plan.appendAddress(new (zone.allocate(sizeof(Segment))) Segment
                       (-1, new (zone.allocate(sizeof(Event))) Event(0)));

    registers[rsp].reserved = true;
    registers[rbp].reserved = true;
    registers[rbx].reserved = true;
  }

  void dispose() {
    zone.dispose();
    plan.dispose();
    code.dispose();
    constantPool.dispose();
    if (segmentTable) s->free(segmentTable);
  }

  System* s;
  Vector constantPool;
  Vector plan;
  Vector code;
  Zone zone;
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
immediate(Context* c, int64_t v, SelectionType = DefaultSelection);

AbsoluteOperand*
absolute(Context* c, MyPromise* v);

RegisterOperand*
register_(Context* c, RegisterReference*, SelectionType = DefaultSelection);

RegisterOperand*
register_(Context* c, Register = NoRegister, Register = NoRegister,
          SelectionType = DefaultSelection);

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale, SelectionType = DefaultSelection);

class MyOperand: public Operand {
 public:
  enum Operation {
    push,
    pop,
    call,
    alignedCall,
    ret,
    mov,
    cmp,
    jl,
    jg,
    jle,
    jge,
    je,
    jne,
    jmp,
    add,
    addc,
    sub,
    subb,
    mul,
    div,
    rem,
    shl,
    shr,
    ushr,
    and_,
    or_,
    xor_,
    neg
  };

  virtual ~MyOperand() { }

  virtual unsigned footprint(Context*) {
    return BytesPerWord;
  }

  virtual Register asRegister(Context* c) { abort(c); }

  virtual MyOperand* select(Context* c, SelectionType) { abort(c); }

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
  RegisterOperand(RegisterReference* reference, SelectionType selection):
    reference(reference), selection(selection)
  { }

  Register value(Context* c) {
    return reference->value(c);
  }

  Register high(Context* c) {
    return reference->high(c);
  }

  virtual unsigned footprint(Context*) {
    return ::footprint(selection);
  }

  virtual Register asRegister(Context* c) {
    return value(c);
  }

  virtual MyOperand* select(Context* c, SelectionType selection) {
    if (selection == this->selection) {
      return this;
    } else {
      return register_(c, reference, selection);
    }
  }

  virtual RegisterNode* dependencies(Context* c, RegisterNode* next) {
    return new (c->zone.allocate(sizeof(RegisterNode)))
      RegisterNode(value(c), next);
  }

  virtual void release(Context* c) {
    reference->release(c);
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AddressOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context*, Operation, MemoryOperand*);

  RegisterReference* reference;
  SelectionType selection;
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(int64_t value, SelectionType selection):
    value(value), selection(selection)
  { }

  virtual unsigned footprint(Context*) {
    return ::footprint(selection);
  }

  virtual MyOperand* select(Context* c, SelectionType selection) {
    return immediate(c, value, selection);
  }

  virtual void apply(Context* c, Operation operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AddressOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }

  int64_t value;
  SelectionType selection;
};

class AddressOperand: public MyOperand {
 public:
  AddressOperand(MyPromise* promise):
    promise(promise)
  { }

  virtual Register asRegister(Context* c);

  virtual void setLabelValue(Context*, MyPromise*);

  virtual void apply(Context*, Operation);
  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
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

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
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
                unsigned scale, SelectionType selection):
    base(base),
    displacement(displacement),
    index(index),
    scale(scale),
    selection(selection)
  { }

  virtual unsigned footprint(Context*) {
    return ::footprint(selection);
  }

  virtual Register asRegister(Context*);

  virtual MyOperand* select(Context* c, SelectionType selection) {
    return memory(c, base, displacement, index, scale, selection);
  }

  virtual RegisterNode* dependencies(Context* c, RegisterNode* next) {
    next = base->dependencies(c, next);
    if (index) {
      return index->dependencies(c, next);
    } else {
      return next;
    }
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
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
  SelectionType selection;
};

AddressOperand*
address(Context* c, MyPromise* p)
{
  return new (c->zone.allocate(sizeof(AddressOperand))) AddressOperand(p);
}

ImmediateOperand*
immediate(Context* c, int64_t v, SelectionType selection)
{
  return new (c->zone.allocate(sizeof(ImmediateOperand)))
    ImmediateOperand(v, selection);
}

AbsoluteOperand*
absolute(Context* c, MyPromise* v)
{
  return new (c->zone.allocate(sizeof(AbsoluteOperand))) AbsoluteOperand(v);
}

RegisterOperand*
register_(Context* c, RegisterReference* r, SelectionType selection)
{
  return new (c->zone.allocate(sizeof(RegisterOperand)))
    RegisterOperand(r, selection);
}

RegisterOperand*
register_(Context* c, Register v, Register h, SelectionType selection)
{
  RegisterReference* r = new (c->zone.allocate(sizeof(RegisterReference)))
    RegisterReference(v, h);
  return register_(c, r, selection);
}

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale, SelectionType selection)
{
  return new (c->zone.allocate(sizeof(MemoryOperand)))
    MemoryOperand(base, displacement, index, scale, selection);
}

RegisterOperand*
temporary(Context* c)
{
  return register_(c, acquire(c));
}

RegisterOperand*
temporary(Context* c, SelectionType selection)
{
  if (BytesPerWord == 4 and selection == Select8) {
    return register_(c, acquire(c), acquire(c), selection);
  } else {
    return register_(c, acquire(c), NoRegister, selection);
  }
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
  OpEvent(MyOperand::Operation operation, Event* next):
    Event(next), operation(operation)
  { }

  virtual void run(Context* c) {
    apply(c, operation);
  }

  MyOperand::Operation operation;
};

class UnaryOpEvent: public Event {
 public:
  UnaryOpEvent(MyOperand::Operation operation, Operand* operand, Event* next):
    Event(next),
    operation(operation),
    operand(static_cast<MyOperand*>(operand))
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "unary %d\n", operation);
    }
    operand->apply(c, operation);
  }

  MyOperand::Operation operation;
  MyOperand* operand; 
};

class BinaryOpEvent: public Event {
 public:
  BinaryOpEvent(MyOperand::Operation operation, Operand* a, Operand* b,
                Event* next):
    Event(next),
    operation(operation),
    a(static_cast<MyOperand*>(a)),
    b(static_cast<MyOperand*>(b))
  { }

  virtual void run(Context* c) {
    if (Verbose) {
      fprintf(stderr, "binary %d\n", operation);
    }
    a->apply(c, operation, b);
  }

  MyOperand::Operation operation;
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

    mi->source->apply(c, MyOperand::mov, register_(c, mi->destination));
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
          arguments[i]->apply(c, MyOperand::push);
        }
      }

      push(c, moveTable, size);
    } else {
      for (int i = count - 1; i >= 0; --i) {
        arguments[i]->apply(c, MyOperand::push);
      }
    }
  }

  MyOperand** arguments; 
  unsigned count;
};

void
appendOperation(Context* c, MyOperand::Operation operation)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(OpEvent)))
    OpEvent(operation, s->event);
}

void
appendOperation(Context* c, MyOperand::Operation operation, Operand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(UnaryOpEvent)))
    UnaryOpEvent(operation, operand, s->event);
}

void
appendOperation(Context* c, MyOperand::Operation operation, Operand* a, Operand* b)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(BinaryOpEvent)))
    BinaryOpEvent(operation, a, b, s->event);
}

void
appendAcquire(Context* c, RegisterOperand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(AcquireEvent)))
    AcquireEvent(operand, s->event);
}

void
appendRelease(Context* c, Operand* operand)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(ReleaseEvent)))
    ReleaseEvent(operand, s->event);
}

void
appendArgumentEvent(Context* c, MyOperand** arguments, unsigned count)
{
  Segment* s = currentSegment(c);
  s->event = new (c->zone.allocate(sizeof(ArgumentEvent)))
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
    (c, register_(c, rbp), - (c->reserved + index + 1) * BytesPerWord, 0, 1);

  stack = new (c->zone.allocate(sizeof(MyStack))) MyStack(value, index, stack);

  if (Verbose) {
    logStack(c, stack);
  }

  return stack;
}

MyStack*
push(Context* c, MyStack* stack, MyOperand* v)
{
  appendOperation(c, MyOperand::push, v);

  if ((v->footprint(c) / BytesPerWord) == 2) {
    stack = pushed(c, stack);
  }
  return pushed(c, stack);
}

MyStack*
pop(Context* c, MyStack* stack, int count)
{
  appendOperation
    (c, MyOperand::add, immediate(c, count * BytesPerWord), register_(c, rsp));

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
pop(Context* c, MyStack* stack, MyOperand* dst)
{
  appendOperation(c, MyOperand::pop, dst);

  if (dst->footprint(c) / BytesPerWord == 2) {
    stack = stack->next;
  }

  if (Verbose) {
    logStack(c, stack->next);
  }

  return stack->next;
}

unsigned
pushArguments(Context* c, unsigned count, va_list list)
{
  MyOperand** arguments = static_cast<MyOperand**>
    (c->zone.allocate(count * BytesPerWord));
  unsigned footprint = 0;
  for (unsigned i = 0; i < count; ++i) {
    arguments[i] = va_arg(list, MyOperand*);
    footprint += pad(arguments[i]->footprint(c));
  }

  appendArgumentEvent(c, arguments, count);

  if (BytesPerWord == 8) {
    if (footprint > GprParameterCount * BytesPerWord) {
      return footprint - GprParameterCount * BytesPerWord;
    } else {
      return 0;
    }
  } else {
    return footprint;
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
rex(Context* c, SelectionType selection)
{
  if (selection == Select8) {
    assert(c, BytesPerWord == 8);
    rex(c);
  } else {
    assert(c, selection == Select4);
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
  if (rex) {
    ::rex(c);
  }
  encode(c, &instruction, 1, a, r, b->displacement, index, b->scale);
}

void
encode(Context* c, uint8_t instruction, int a, MemoryOperand* b,
       SelectionType selection)
{
  assert(c, selection == DefaultSelection or selection == Select4);

  encode(c, instruction, a, b, selection == DefaultSelection);
}

void
encode2(Context* c, uint16_t instruction, int a, MemoryOperand* b, bool rex)
{
  Register r = b->base->asRegister(c);
  int index = b->index ? b->index->asRegister(c) : -1;
  if (rex) {
    ::rex(c);
  }
  uint8_t i[2] = { instruction >> 8, instruction & 0xff };
  encode(c, i, 2, a, r, b->displacement, index, b->scale);
}

void
RegisterOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    assert(c, selection == DefaultSelection);

    c->code.append(0xff);
    c->code.append(0xd0 | value(c));
    break;

  case jmp:
    assert(c, selection == DefaultSelection);

    c->code.append(0xff);
    c->code.append(0xe0 | value(c));
    break;

  case pop:
    if (selection == DefaultSelection or selection == Select4) {
      c->code.append(0x58 | value(c));
    } else {
      assert(c, selection == Select8);

      register_(c, value(c))->apply(c, pop);
      register_(c, high(c))->apply(c, pop);
    }
    break;

  case push:
    if (selection == DefaultSelection or selection == Select4
        or (BytesPerWord == 8 and selection == Select8From4))
    {
      c->code.append(0x50 | value(c));
    } else {
      switch (selection) {
      case Select8:
        register_(c, high(c))->apply(c, push);
        register_(c, value(c))->apply(c, push);
        break;

      case Select8From4: {
        RegisterOperand* ax = temporary(c, rax);
        RegisterOperand* dx = temporary(c, rdx);
        
        ax->accept(c, mov, register_(c, value(c)));
        c->code.append(0x99); // cdq
        dx->apply(c, push);
        ax->apply(c, push);

        ax->release(c);
        dx->release(c);
      } break;

      default: abort(c);
      }
    }
    break;

  case neg:
    rex(c, selection);
    c->code.append(0xf7);
    c->code.append(0xd8 | value(c));
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        RegisterOperand* operand)
{
  switch (operation) {
  case add:
    assert(c, selection == operand->selection);
  
    rex(c, operand->selection);
    c->code.append(0x01);
    c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    break;

  case cmp:
    assert(c, selection == operand->selection);
  
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      rex(c, operand->selection);
      c->code.append(0x39);
      c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
    } else {
      assert(c, operand->selection == Select8);

      register_(c, high(c))->accept
        (c, cmp, register_(c, operand->high(c)));

      // if the high order bits are equal, we compare the low order
      // bits; otherwise, we jump past that comparison
      c->code.append(0x0f);
      c->code.append(0x85); // jne
      c->code.append4(2);

      register_(c, value(c))->accept
        (c, cmp, register_(c, operand->value(c)));
    }
    break;

  case mov:
    if (value(c) != operand->value(c) or selection != operand->selection) {
      if (operand->selection == DefaultSelection
        or operand->selection == Select4)
      {
        rex(c, selection);
        c->code.append(0x89);
        c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
      } else {
        switch (operand->selection) {
        case Select1:
          c->code.append(0xbe);
          c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
          break;

        case Select2:
          c->code.append(0xbf);
          c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
          break;

        case Select2z:
          c->code.append(0xb7);
          c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
          break;

        case Select4:
          c->code.append(0x89);
          c->code.append(0xc0 | (operand->value(c) << 3) | value(c));
          break;

        case Select8:
          register_(c, value(c))->accept
            (c, mov, register_(c, operand->value(c)));

          register_(c, high(c))->accept
            (c, mov, register_(c, operand->high(c)));
          break;

        default: abort(c);
        }
      }
    }
    break;

  case mul:
    rex(c, selection);
    c->code.append(0x0f);
    c->code.append(0xaf);
    c->code.append(0xc0 | (value(c) << 3) | operand->value(c));
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        ImmediateOperand* operand)
{
  assert(c, operand->selection == DefaultSelection);

  switch (operation) {
  case add: {
    if (operand->value) {
      rex(c, selection);
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
  } break;

  case addc:
    if (isInt8(operand->value)) {
      c->code.append(0x83);
      c->code.append(0xd0 | value(c));
      c->code.append(operand->value);
    } else {
      abort(c);
    }
    break;

  case and_: {
    rex(c, selection);
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
  } break;

  case cmp: {
    assert(c, isInt8(operand->value)); // todo

    rex(c, selection);
    c->code.append(0x83);
    c->code.append(0xf8 | value(c));
    c->code.append(operand->value);
  } break;

  case mov: {
    rex(c);
    c->code.append(0xb8 | value(c));
    c->code.appendAddress(operand->value);
  } break;

  case shl: {
    if (operand->value) {
      rex(c, selection);
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

  case sub: {
    if (operand->value) {
      rex(c, selection);
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
  if (operand->promise->resolved(c)) {
    return immediate(c, operand->promise->value(c));
  } else {
    return immediate(c, 0);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        AddressOperand* operand)
{
  assert(c, selection == DefaultSelection);

  switch (operation) {
  case mov: {
    accept(c, operation, ::value(c, operand));
  } break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        MemoryOperand* operand)
{
  switch (operation) {
  case cmp: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {    
      encode(c, 0x3b, value(c), operand, operand->selection);
    } else {
      abort(c);
    }
  } break;

  case mov: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x8b, value(c), operand, true);
    } else {
      switch (operand->selection) {
      case Select1:
        encode2(c, 0x0fbe, value(c), operand, true);      
        break;

      case Select2:
        encode2(c, 0x0fbf, value(c), operand, true);      
        break;

      case Select2z:
        encode2(c, 0x0fb7, value(c), operand, true);      
        break;

      case Select4:
        encode(c, 0x63, value(c), operand, true);
        break;

      case Select8:
        register_(c, value(c))->accept
          (c, mov, memory
           (c, operand->base, operand->displacement,
            operand->index, operand->scale));

        register_(c, high(c))->accept
          (c, mov, memory
           (c, operand->base, operand->displacement + BytesPerWord,
            operand->index, operand->scale));
        break;

      default: abort(c);
      }
    }
  } break;

  case mul: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode2(c, 0x0faf, value(c), operand, operand->selection);
    } else {
      abort(c);
    }
  } break;

  default: abort(c);
  }
}

ImmediateOperand*
value(Context* c, AbsoluteOperand* operand)
{
  if (operand->promise->resolved(c)) {
    return immediate(c, operand->promise->value(c));
  } else {
    return immediate(c, 0);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        AbsoluteOperand* operand)
{
  switch (operation) {
  case cmp: {
    RegisterOperand* tmp = temporary(c);
    tmp->accept(c, mov, ::value(c, operand));
    accept(c, cmp, memory(c, tmp, 0, 0, 1));
    tmp->release(c);
  } break;

  case mov: {
    accept(c, mov, ::value(c, operand));
    accept(c, mov, memory(c, this, 0, 0, 1));
  } break;

  default: abort(c);
  }
}

void
unconditional(Context* c, unsigned jump, AddressOperand* operand)
{
  intptr_t v;
  if (operand->promise->resolved(c)) {
    uint8_t* instruction = c->code.data + c->code.length();
    v = reinterpret_cast<uint8_t*>(operand->promise->value(c))
      - instruction - 5;
  } else {
    v = 0;
  }
  
  c->code.append(jump);
  c->code.append4(v);
}

void
conditional(Context* c, unsigned condition, AddressOperand* operand)
{
  intptr_t v;
  if (operand->promise->resolved(c)) {
    uint8_t* instruction = c->code.data + c->code.length();
    v = reinterpret_cast<uint8_t*>(operand->promise->value(c))
      - instruction - 6;
  } else {
    v = 0;
  }
  
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
AddressOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case alignedCall: {
    while ((c->code.length() + 1) % 4) {
      c->code.append(0x90);
    }
    apply(c, call);
  } break;

  case call:
    unconditional(c, 0xe8, this);
    break;

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

  case push: {
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
ImmediateOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case alignedCall:
  case call:
  case jmp:
    assert(c, selection == DefaultSelection);

    address(c, new (c->zone.allocate(sizeof(ResolvedPromise)))
            ResolvedPromise(value))->apply(c, operation);
    break;

  case push: {
    if (selection == DefaultSelection) {
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
    } else {
      assert(c, selection == Select8);

      immediate(c, (value >> 32) & 0xFFFFFFFF)->apply(c, push);
      immediate(c, (value      ) & 0xFFFFFFFF)->apply(c, push);
    }
  } break;
    
  default: abort(c);
  }
}

Register
AbsoluteOperand::asRegister(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, mov, this);
  Register v = tmp->value(c);
  tmp->release(c);
  return v;
}

void
absoluteApply(Context* c, MyOperand::Operation operation,
              AbsoluteOperand* operand)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, MyOperand::mov, value(c, operand));
  memory(c, tmp, 0, 0, 1)->apply(c, operation);
  tmp->release(c);
}

void
AbsoluteOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case push:
    absoluteApply(c, operation, this);
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
MemoryOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    assert(c, selection == DefaultSelection);

    encode(c, 0xff, 2, this, false);
    break;

  case jmp:
    assert(c, selection == DefaultSelection);

    encode(c, 0xff, 4, this, false);
    break;

  case neg:
    if (selection == DefaultSelection) {
      encode(c, 0xf7, 2, this, selection);
    } else {
      assert(c, selection == Select8);

      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      MemoryOperand* low = memory(c, base, displacement, index, scale);
      MemoryOperand* high = memory
        (c, base, displacement + BytesPerWord, index, scale);

      ax->accept(c, mov, low);
      dx->accept(c, mov, high);

      ax->apply(c, neg);
      dx->accept(c, addc, immediate(c, 0));
      dx->apply(c, neg);

      low->accept(c, mov, ax);
      high->accept(c, mov, dx);

      ax->release(c);
      dx->release(c);
    }
    break;

  case pop:
    if (selection == DefaultSelection) {
      encode(c, 0x8f, 0, this, false);
    } else {
      assert(c, selection == Select8);

      MemoryOperand* low = memory(c, base, displacement, index, scale);
      MemoryOperand* high = memory
        (c, base, displacement + BytesPerWord, index, scale);

      low->apply(c, pop);
      high->apply(c, pop);
    }
    break;

  case push:
    if (selection == DefaultSelection) {
      encode(c, 0xff, 6, this, false);
    } else {
      switch (selection) {
      case Select8: {
        MemoryOperand* low = memory(c, base, displacement, index, scale);
        MemoryOperand* high = memory
          (c, base, displacement + BytesPerWord, index, scale);
        
        high->apply(c, push);
        low->apply(c, push);
      } break;
        
      default: {
        RegisterOperand* tmp = temporary
          (c, selection == Select8 ? Select8 : DefaultSelection);
        tmp->accept(c, mov, this);
        tmp->apply(c, operation);
        tmp->release(c);
      } break;
      }
    }
    break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      RegisterOperand* operand)
{
  switch (operation) {
  case and_: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x21, operand->value(c), this, operand->selection);
    } else {
      abort(c);
    }
  } break;

  case add: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x01, operand->value(c), this, operand->selection);
    } else {
      assert(c, operand->selection == Select8);

      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      ax->accept(c, mov, register_(c, operand->value(c)));
      dx->accept(c, mov, register_(c, operand->high(c)));

      memory(c, base, displacement, index, scale)->accept(c, add, ax);
      memory(c, base, displacement + BytesPerWord, index, scale)->accept
        (c, addc, dx);

      ax->release(c);
      dx->release(c);
    }
  } break;

  case addc:
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x11, operand->value(c), this, operand->selection);
    } else {
      abort(c);
    }
    break;

  case div: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);
      ax->accept(c, mov, this);
    
      rex(c, operand->selection);
      c->code.append(0x99);
      rex(c, operand->selection);
      c->code.append(0xf7);
      c->code.append(0xf8 | operand->value(c));

      accept(c, mov, ax);

      ax->release(c);
      dx->release(c);
    } else {
      assert(c, operand->selection == Select8);

      operand->apply(c, push);
      memory(c, base, displacement, index, scale, Select8)->apply(c, push);
      immediate(c, reinterpret_cast<intptr_t>(divideLong))->apply(c, call);
      register_(c, rsp)->accept(c, add, immediate(c, 16));
      accept(c, mov, register_(c, rax, rdx, Select8));
    }
  } break;

  case mov: {
    if (operand->selection == DefaultSelection) {
      encode(c, 0x89, operand->value(c), this, true);
    } else {
      switch (operand->selection) {
      case Select1:
        if (BytesPerWord == 8) {
          if (operand->value(c) > rbx) {
            c->code.append(0x40);
          }
          encode(c, 0x88, operand->value(c), this, false);
        } else {
          if (operand->value(c) > rbx) {
            RegisterOperand* ax = temporary(c, rax);
            ax->accept(c, mov, register_(c, operand->value(c)));
            accept(c, mov, register_(c, rax, NoRegister, Select1));
            ax->release(c);
          } else {
            encode(c, 0x88, operand->value(c), this, false);
          }
        }
        break;

      case Select2:
      case Select2z:
        c->code.append(0x66);
        encode(c, 0x89, operand->value(c), this, false);
        break;

      case Select4:
        encode(c, 0x89, operand->value(c), this, false);
        break;

      case Select8:
        memory(c, base, displacement, index, scale)->accept
          (c, mov, register_(c, operand->value(c)));

        memory(c, base, displacement + BytesPerWord, index, scale)->accept
          (c, mov, register_(c, operand->high(c)));
        break;

      default: abort(c);
      }
    }
  } break;

  case mul: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      RegisterOperand* tmp = temporary(c);

      tmp->accept(c, mov, this);
      tmp->accept(c, mul, operand);
      accept(c, mov, tmp);
    
      tmp->release(c);
    } else {
      assert(c, operand->selection == Select8);

      RegisterOperand* tmp = temporary(c, rcx);
      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      RegisterOperand* lowSrc = register_(c, operand->value(c));
      RegisterOperand* highSrc = register_(c, operand->high(c));

      MemoryOperand* lowDst = memory(c, base, displacement, index, scale);
      MemoryOperand* highDst = memory
        (c, base, displacement + BytesPerWord, index, scale);
      
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
    }
  } break;

  case or_: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x09, operand->value(c), this, operand->selection);
    } else {
      abort(c);
    }
  } break;

  case rem: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
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
    } else {
      assert(c, operand->selection == Select8);

      operand->apply(c, push);
      memory(c, base, displacement, index, scale, Select8)->apply(c, push);
      immediate(c, reinterpret_cast<intptr_t>(moduloLong))->apply(c, call);
      register_(c, rsp)->accept(c, add, immediate(c, 16));
      accept(c, mov, register_(c, rax, rdx, Select8));
    }
  } break;

  case shl: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 4, this, operand->selection);
      cx->release(c);
    } else {
      abort(c);
    }
  } break;

  case shr: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 5, this, operand->selection);
      cx->release(c);
    } else {
      abort(c);
    }
  } break;

  case ushr: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      RegisterOperand* cx = temporary(c, rcx);
      cx->accept(c, mov, operand);
      encode(c, 0xd3, 7, this, operand->selection);
      cx->release(c);
    } else {
      abort(c);
    }
  } break;

  case sub: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x29, operand->value(c), this, operand->selection);
    } else {
      assert(c, operand->selection == Select8);

      RegisterOperand* ax = temporary(c, rax);
      RegisterOperand* dx = temporary(c, rdx);

      ax->accept(c, mov, register_(c, operand->value(c)));
      dx->accept(c, mov, register_(c, operand->high(c)));

      memory(c, base, displacement, index, scale)->accept(c, sub, ax);
      memory(c, base, displacement + BytesPerWord, index, scale)->accept
        (c, subb, dx);

      ax->release(c);
      dx->release(c);
    }
  } break;

  case subb:
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x19, operand->value(c), this, operand->selection);
    } else {
      abort(c);
    }
    break;

  case xor_: {
    if (operand->selection == DefaultSelection
        or operand->selection == Select4)
    {
      encode(c, 0x31, operand->value(c), this, operand->selection);
    } else {
      abort(c);
    }
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      ImmediateOperand* operand)
{
  assert(c, selection == DefaultSelection);
  assert(c, operand->selection == DefaultSelection);

  switch (operation) {
  case add: {
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

  case mov: {
    assert(c, isInt32(operand->value)); // todo

    encode(c, 0xc7, 0, this, true);
    c->code.append4(operand->value);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      AbsoluteOperand* operand)
{
  RegisterOperand* tmp = temporary(c);
    
  tmp->accept(c, mov, operand);
  accept(c, operation, tmp);

  tmp->release(c);
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      MemoryOperand* operand)
{
  RegisterOperand* tmp = temporary(c);
    
  tmp->accept(c, mov, operand);
  accept(c, operation, tmp);
    
  tmp->release(c);
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
    c->segmentTable = static_cast<Segment**>(c->s->allocate(c->plan.length()));
    
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

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, void* indirectCaller):
    c(s, indirectCaller)
  { }

  virtual Promise* machineIp() {
    CodePromise* p = new (c.zone.allocate(sizeof(CodePromise))) CodePromise();

    Segment* s = currentSegment(&c);
    s->event->task = new (c.zone.allocate(sizeof(CodePromiseTask)))
      CodePromiseTask(p, s->event->task);

    return p;
  }

  virtual Promise* machineIp(unsigned logicalIp) {
    return new (c.zone.allocate(sizeof(IpPromise))) IpPromise(logicalIp);
  }

  virtual Promise* poolAppend(intptr_t v) {
    return poolAppendPromise
      (new (c.zone.allocate(sizeof(ResolvedPromise))) ResolvedPromise(v));
  }

  virtual Promise* poolAppendPromise(Promise* v) {
    Promise* p = new (c.zone.allocate(sizeof(PoolPromise)))
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
    sub(immediate(&c, count * BytesPerWord), register_(&c, rsp));

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

  virtual Stack* push(Stack* s, Operand* v) {
    return ::push(&c, static_cast<MyStack*>(s), static_cast<MyOperand*>(v));
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

  virtual Stack* pop(Stack* s, Operand* dst) {
    return ::pop(&c, static_cast<MyStack*>(s), static_cast<MyOperand*>(dst));
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

  virtual void mark(Operand* label) {
    static_cast<MyOperand*>(label)->setLabelValue
      (&c, static_cast<MyPromise*>(machineIp()));
  }

  virtual Promise* indirectCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    mov(address, register_(&c, rax));
    call(immediate(&c, c.indirectCaller));
    Promise* p = machineIp();

    add(immediate(&c, footprint), register_(&c, rsp));
    return p;
  }

  virtual void indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);    
    va_end(a);

    mov(address, register_(&c, rax));

    call(immediate(&c, c.indirectCaller));
  }

  virtual Promise* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    call(address);
    Promise* p = machineIp();

    add(immediate(&c, footprint), register_(&c, rsp));
    return p;
  }

  virtual Operand* result() {
    RegisterOperand* r = register_(&c, rax, rdx);
    appendAcquire(&c, r);
    return r;
  }

  virtual void return_(Operand* v) {
    mov(v, register_(&c, rax, rdx));
    epilogue();
    ret();
  }

  virtual void call(Operand* v) {
    appendOperation(&c, MyOperand::call, v);
  }

  virtual void alignedCall(Operand* v) {
    appendOperation(&c, MyOperand::alignedCall, v);
  }

  virtual void ret() {
    appendOperation(&c, MyOperand::ret);
  }

  virtual void mov(Operand* src, Operand* dst) {
    appendOperation(&c, MyOperand::mov, src, dst);
  }

  virtual void cmp(Operand* subtrahend, Operand* minuend) {
    appendOperation(&c, MyOperand::cmp, subtrahend, minuend);
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

  virtual void add(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::add, v, dst);
  }

  virtual void sub(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::sub, v, dst);
  }

  virtual void mul(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::mul, v, dst);
  }

  virtual void div(Operand* v, Operand* dst) {
    appendOperation(&c, MyOperand::div, v, dst);
  }

  virtual void rem(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::rem, v, dst);
  }

  virtual void shl(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shl, v, dst);
  }

  virtual void shr(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::shr, v, dst);
  }

  virtual void ushr(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::ushr, v, dst);
  }

  virtual void and_(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::and_, v, dst);
  }

  virtual void or_(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::or_, v, dst);
  }

  virtual void xor_(Operand* v, Operand* dst)  {
    appendOperation(&c, MyOperand::xor_, v, dst);
  }

  virtual void neg(Operand* v)  {
    appendOperation(&c, MyOperand::neg, v);
  }

  virtual Operand* memory(Operand* base, int displacement,
                          Operand* index, unsigned scale)
  {
    return ::memory(&c, static_cast<MyOperand*>(base), displacement,
                    static_cast<MyOperand*>(index), scale);
  }

  virtual Operand* select1(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select1);
  }

  virtual Operand* select2(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select2);
  }

  virtual Operand* select2z(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select2z);
  }

  virtual Operand* select4(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select4);
  }

  virtual Operand* select8(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select8);
  }

  virtual Operand* select8From4(Operand* v) {
    return static_cast<MyOperand*>(v)->select(&c, Select8From4);
  }

  virtual void prologue() {
    appendOperation(&c, MyOperand::push, register_(&c, rbp));
    appendOperation
      (&c, MyOperand::mov, register_(&c, rsp), register_(&c, rbp));
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
      (new (c.zone.allocate(sizeof(Segment)))
       Segment(ip, new (c.zone.allocate(sizeof(Event))) Event(0)));
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

    c.s->free(this);
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
makeCompiler(System* system, void* indirectCaller)
{
  return new (system->allocate(sizeof(MyCompiler)))
    MyCompiler(system, indirectCaller);
}

} // namespace v
