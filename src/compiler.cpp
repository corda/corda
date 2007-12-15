#include "compiler.h"
#include "vector.h"
#include "zone.h"

using namespace vm;

namespace {

enum Register {
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
  S1Selection,
  S2Selection,
  Z2Selection,
  S4Selection,
  S8Selection
};

const bool Verbose = false;

const unsigned RegisterCount = BytesPerWord * 2;

class Context;
class ImmediateOperand;
class AbsoluteOperand;
class RegisterOperand;
class MemoryOperand;
class StackOperand;
class CodePromise;
class MyPromise;

void NO_RETURN abort(Context*);

#ifndef NDEBUG
void assert(Context*, bool);
#endif // not NDEBUG

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
    sub,
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

  virtual unsigned footprint() {
    return BytesPerWord;
  }

  virtual Register asRegister(Context* c) { abort(c); }

  virtual void release(Context*) { /* ignore */ }

  virtual void setLabelValue(Context* c, CodePromise*) { abort(c); }

  virtual void apply(Context*, Operation) = 0;

  virtual void apply(Context*, Operation, MyOperand*) = 0;

  virtual void apply(Context*, Operation, SelectionType) = 0;

  virtual void apply(Context*, Operation, MyOperand*, SelectionType) = 0;

  virtual void accept(Context* c, Operation, RegisterOperand*) = 0;

  virtual void accept(Context* c, Operation, ImmediateOperand*) = 0;

  virtual void accept(Context* c, Operation, AbsoluteOperand*) = 0;

  virtual void accept(Context* c, Operation, MemoryOperand*) = 0;

  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType)
  = 0;

  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  = 0;
};

class RegisterOperand: public MyOperand {
 public:
  RegisterOperand(Register value):
    value(value), reserved(false)
  { }

  virtual Register asRegister(Context*) {
    return value;
  }

  void acquire(Context* c UNUSED) {
    assert(c, not reserved);
//     fprintf(stderr, "acquire %d\n", value);
    reserved = true;
  }

  virtual void release(Context* c UNUSED) {
    assert(c, reserved);
//     fprintf(stderr, "release %d\n", value);
    reserved = false;
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void apply(Context* c, Operation, SelectionType) { abort(c); }

  virtual void apply(Context* c, Operation operation, MyOperand* operand,
                     SelectionType selection)
  {
    operand->accept(c, operation, this, selection);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context*, Operation, MemoryOperand*);
  virtual void accept(Context*, Operation, RegisterOperand*, SelectionType);
  virtual void accept(Context*, Operation, MemoryOperand*, SelectionType);

  Register value;
  bool reserved;
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(intptr_t value):
    value(value)
  { }

  virtual void apply(Context* c, Operation operation);

  virtual void apply(Context* c, Operation, SelectionType) { abort(c); }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void apply(Context* c, Operation, MyOperand*, SelectionType)
  { abort(c); }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  { abort(c); }

  intptr_t value;
};

class AddressOperand: public MyOperand {
 public:
  AddressOperand(MyPromise* promise):
    promise(promise)
  { }

  virtual void setLabelValue(Context*, CodePromise*);

  virtual void apply(Context*, Operation);
  virtual void apply(Context* c, Operation, MyOperand*) { abort(c); }
  virtual void apply(Context* c, Operation, SelectionType) { abort(c); }
  virtual void apply(Context* c, Operation, MyOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  { abort(c); }

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

  virtual void apply(Context* c, Operation, SelectionType) { abort(c); }
  virtual void apply(Context* c, Operation, MyOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  { abort(c); }

  MyPromise* promise;
};

class MemoryOperand: public MyOperand {
 public:
  MemoryOperand(MyOperand* base, int displacement, MyOperand* index,
                unsigned scale):
    base(base),
    displacement(displacement),
    index(index),
    scale(scale)
  { }

  virtual Register asRegister(Context*);

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void apply(Context* c, Operation, SelectionType);

  virtual void apply(Context* c, Operation operation, MyOperand* operand,
                     SelectionType selection)
  {
    operand->accept(c, operation, this, selection);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType);
  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  { abort(c); }

  MyOperand* base;
  int displacement;
  MyOperand* index;
  unsigned scale;
};

class SelectionOperand: public MyOperand {
 public:
  SelectionOperand(SelectionType type, MyOperand* base):
    selectionType(type), base(base)
  { }

  virtual unsigned footprint() {
    if (selectionType == S8Selection) {
      return 8;
    } else {
      return 4;
    }
  }

  virtual void apply(Context* c, Operation operation) {
    base->apply(c, operation, selectionType);
  }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    base->apply(c, operation, operand, selectionType);
  }

  virtual void apply(Context* c, Operation, SelectionType) { abort(c); }
  virtual void apply(Context* c, Operation, MyOperand*, SelectionType)
  { abort(c); }

  virtual void accept(Context* c, Operation operation,
                      RegisterOperand* operand)
  {
    base->accept(c, operation, operand, selectionType);
  }

  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }
  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }

  virtual void accept(Context* c, Operation operation, MemoryOperand* operand)
  {
    base->accept(c, operation, operand, selectionType);
  }

  virtual void accept(Context* c, Operation, RegisterOperand*, SelectionType)
  { abort(c); }
  virtual void accept(Context* c, Operation, MemoryOperand*, SelectionType)
  { abort(c); }

  SelectionType selectionType;
  MyOperand* base;
};

class WrapperOperand: public MyOperand {
 public:
  WrapperOperand(MyOperand* base):
    base(base)
  { }

  virtual unsigned footprint() {
    return base->footprint();
  }

  virtual Register asRegister(Context* c) {
    return base->asRegister(c);
  }

  virtual void apply(Context* c, Operation operation) {
    base->apply(c, operation);
  }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    base->apply(c, operation, operand);
  }

  virtual void apply(Context* c, Operation operation, SelectionType selection)
  {
    base->apply(c, operation, selection);
  }

  virtual void apply(Context* c, Operation operation, MyOperand* operand,
                     SelectionType selection)
  {
    base->apply(c, operation, operand, selection);
  }

  virtual void accept(Context* c, Operation operation,
                      RegisterOperand* operand)
  {
    base->accept(c, operation, operand);
  }

  virtual void accept(Context* c, Operation operation,
                      ImmediateOperand* operand)
  {
    base->accept(c, operation, operand);
  }

  virtual void accept(Context* c, Operation operation,
                      AbsoluteOperand* operand)
  {
    base->accept(c, operation, operand);
  }

  virtual void accept(Context* c, Operation operation, MemoryOperand* operand)
  {
    base->accept(c, operation, operand);
  }

  virtual void accept(Context* c, Operation operation,
                      RegisterOperand* operand, SelectionType selection)
  {
    base->accept(c, operation, operand, selection);
  }

  virtual void accept(Context* c, Operation operation, MemoryOperand* operand,
                      SelectionType selection)
  {
    base->accept(c, operation, operand, selection);
  }

  MyOperand* base;
};

class StackOperand: public WrapperOperand {
 public:
  StackOperand(MyOperand* base, int index, StackOperand* next):
    WrapperOperand(base), index(index), next(next)
  { }

  int index;
  StackOperand* next;
};

class TemporaryOperand: public WrapperOperand {
 public:
  TemporaryOperand(MyOperand* base):
    WrapperOperand(base)
  { }

  virtual unsigned footprint() {
    return BytesPerWord;
  }

  virtual void release(Context* c) {
    base->release(c);
    base = 0;
  }
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
    stack(0),
    reserved(0),
    codeLength(-1)
  {
    plan.appendAddress(new (zone.allocate(sizeof(Segment))) Segment
                       (-1, new (zone.allocate(sizeof(Event))) Event(0)));

    for (unsigned i = 0; i < RegisterCount; ++i) {
      registers[i] = new (zone.allocate(sizeof(RegisterOperand)))
        RegisterOperand(static_cast<Register>(i));
    }

    registers[rsp]->acquire(this);
    registers[rbp]->acquire(this);
    registers[rbx]->acquire(this);
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
  StackOperand* stack;
  unsigned reserved;
  int codeLength;
  RegisterOperand* registers[RegisterCount];
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
      return c->codeLength + key;
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
  CodePromise(bool absolute):
    offset(-1), absolute(absolute)
  { }

  virtual intptr_t value(Context* c) {
    if (resolved(c)) {
      if (absolute) {
        return reinterpret_cast<intptr_t>(c->code.data + offset);
      } else {
        return offset;
      }
    }
    
    abort(c);
  }

  virtual bool resolved(Context*) {
    return offset >= 0;
  }

  intptr_t offset;
  bool absolute;
};

class IpPromise: public MyPromise {
 public:
  IpPromise(intptr_t logicalIp, bool absolute):
    logicalIp(logicalIp), absolute(absolute)
  { }

  virtual intptr_t value(Context* c) {
    if (resolved(c)) {
      unsigned bottom = 0;
      unsigned top = c->plan.length() / BytesPerWord;
      for (unsigned span = top - bottom; span; span = top - bottom) {
        unsigned middle = bottom + (span / 2);
        Segment* s = c->segmentTable[middle];

        if (logicalIp == s->logicalIp) {
          if (absolute) {
            return reinterpret_cast<intptr_t>(c->code.data + s->offset);
          } else {
            return s->offset;
          }
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
  bool absolute;
};

AddressOperand*
address(Context* c, MyPromise* p)
{
  return new (c->zone.allocate(sizeof(AddressOperand))) AddressOperand(p);
}

ImmediateOperand*
immediate(Context* c, intptr_t v)
{
  return new (c->zone.allocate(sizeof(ImmediateOperand))) ImmediateOperand(v);
}

AbsoluteOperand*
absolute(Context* c, MyPromise* v)
{
  return new (c->zone.allocate(sizeof(AbsoluteOperand))) AbsoluteOperand(v);
}

RegisterOperand*
register_(Context* c, Register v)
{
  return c->registers[v];
}

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale)
{
  return new (c->zone.allocate(sizeof(MemoryOperand)))
    MemoryOperand(base, displacement, index, scale);
}

MyOperand*
selection(Context* c, SelectionType type, MyOperand* base)
{
  if ((type == S4Selection and BytesPerWord == 4)
      or (type == S8Selection and BytesPerWord == 8))
  {
    return base;
  } else {
    return new (c->zone.allocate(sizeof(SelectionOperand)))
      SelectionOperand(type, base);
  }
}

Segment*
currentSegment(Context* c)
{
  Segment* s; c->plan.get(c->plan.length() - BytesPerWord, &s, BytesPerWord);
  return s;
}

RegisterOperand*
temporary(Context* c)
{
  // we don't yet support using r9-r15
  for (int i = 8/*RegisterCount*/ - 1; i >= 0; --i) {
    if (not c->registers[i]->reserved) {
      c->registers[i]->acquire(c);
      return c->registers[i];
    }
  }

  abort(c);
}

RegisterOperand*
acquire(Context* c, Register v)
{
  RegisterOperand* r = register_(c, v);
  r->acquire(c);
  return r;
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
    if (Verbose and c->codeLength >= 0) {
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
    if (Verbose and c->codeLength >= 0) {
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
  AcquireEvent(TemporaryOperand* operand, Event* next):
    Event(next),
    operand(operand)
  { }

  virtual void run(Context* c) {
    if (Verbose and c->codeLength >= 0) {
      fprintf(stderr, "acquire\n");
    }
    operand->base = temporary(c);
  }

  TemporaryOperand* operand; 
};

class ReleaseEvent: public Event {
 public:
  ReleaseEvent(Operand* operand, Event* next):
    Event(next),
    operand(static_cast<MyOperand*>(operand))
  { }

  virtual void run(Context* c) {
    if (Verbose and c->codeLength >= 0) {
      fprintf(stderr, "release\n");
    }
    operand->release(c);
  }

  MyOperand* operand; 
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
appendAcquire(Context* c, TemporaryOperand* operand)
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

StackOperand*
pushed(Context* c)
{
  int index = (c->stack ?
               c->stack->index + (c->stack->footprint() / BytesPerWord) :
               0);

  MyOperand* base = memory
    (c, register_(c, rbp), - (c->reserved + index + 1) * BytesPerWord, 0, 1);

  return c->stack = new (c->zone.allocate(sizeof(StackOperand)))
    StackOperand(base, index, c->stack);
}

void
push(Context* c, int count)
{
  appendOperation
    (c, MyOperand::sub, immediate(c, count * BytesPerWord), register_(c, rsp));

  while (count) {
    -- count;
    pushed(c);
  }
}

StackOperand*
push(Context* c, MyOperand* v)
{
  appendOperation(c, MyOperand::push, v);

  return pushed(c);
}

void
pop(Context* c, int count)
{
  appendOperation
    (c, MyOperand::add, immediate(c, count * BytesPerWord), register_(c, rsp));

  while (count) {
    count -= (c->stack->footprint() / BytesPerWord);
    assert(c, count >= 0);
    c->stack = c->stack->next;
  }
}

void
pop(Context* c, MyOperand* dst)
{
  appendOperation(c, MyOperand::pop, dst);

  c->stack = c->stack->next;
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

unsigned
pushArguments(Context* c, unsigned count, va_list list)
{
  MyOperand* arguments[count];
  unsigned footprint = 0;
  for (unsigned i = 0; i < count; ++i) {
    arguments[i] = va_arg(list, MyOperand*);
    footprint += pad(arguments[i]->footprint());
  }

  const int GprCount = 6;
  for (int i = count - 1; i >= 0; --i) {
    if (BytesPerWord == 8 and i < GprCount) {
      appendOperation
        (c, MyOperand::mov, arguments[i], register_(c, gpRegister(c, i)));
    } else {
      appendOperation(c, MyOperand::push, arguments[i]);
    }
  }

  if (BytesPerWord == 8) {
    if (footprint > GprCount * BytesPerWord) {
      return footprint - GprCount * BytesPerWord;
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
encode(Context* c, uint8_t instruction, int a, Register b,
       int32_t displacement, int index, unsigned scale)
{
  c->code.append(instruction);

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
  encode(c, instruction, a, r, b->displacement, index, b->scale);
}

void
RegisterOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    c->code.append(0xff);
    c->code.append(0xd0 | value);
    break;

  case jmp:
    c->code.append(0xff);
    c->code.append(0xe0 | value);
    break;

  case pop:
    c->code.append(0x58 | value);
    break;

  case push:
    c->code.append(0x50 | value);
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
    rex(c);
    c->code.append(0x01);
    c->code.append(0xc0 | (operand->value << 3) | value);
    break;

  case cmp:
    rex(c);
    c->code.append(0x39);
    c->code.append(0xc0 | (operand->value << 3) | value);
    break;

  case mov:
    if (value != operand->value) {
      rex(c);
      c->code.append(0x89);
      c->code.append(0xc0 | (operand->value << 3) | value);
    }
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        RegisterOperand* operand, SelectionType selection)
{
  switch (operation) {
  case mov: {
    rex(c);
    c->code.append(0x0f);

    switch (selection) {
    case S1Selection:
      c->code.append(0xbe);
      break;

    case S2Selection:
      c->code.append(0xbf);
      break;

    case Z2Selection:
      c->code.append(0xb7);
      break;

    default: abort(c);
    }

    c->code.append(0xc0 | (value << 3) | operand->value);
  } break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        ImmediateOperand* operand)
{
  switch (operation) {
  case add: {
    if (operand->value) {
      assert(c, isInt8(operand->value)); // todo

      rex(c);
      c->code.append(0x83);
      c->code.append(0xc0 | value);
      c->code.append(operand->value);
    }
  } break;

  case and_: {
    if (operand->value) {
      rex(c);
      if (isInt8(operand->value)) {
        c->code.append(0x83);
        c->code.append(0xe0 | value);
        c->code.append(operand->value);
      } else {
        assert(c, isInt32(operand->value));

        c->code.append(0x81);
        c->code.append(0xe0 | value);
        c->code.append(operand->value);
      }
    }
  } break;

  case cmp: {
    assert(c, isInt8(operand->value)); // todo

    rex(c);
    c->code.append(0x83);
    c->code.append(0xf8 | value);
    c->code.append(operand->value);
  } break;

  case mov: {
    rex(c);
    c->code.append(0xb8 | value);
    c->code.appendAddress(operand->value);
  } break;

  case sub: {
    if (operand->value) {
      assert(c, isInt8(operand->value)); // todo

      rex(c);
      c->code.append(0x83);
      c->code.append(0xe8 | value);
      c->code.append(operand->value);
    }
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
    encode(c, 0x3b, value, operand, true);
  } break;

  case mov: {
    encode(c, 0x8b, value, operand, true);
  } break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        MemoryOperand* operand, SelectionType selection)
{
  switch (operation) {
  case mov: {
    switch (selection) {
    case S1Selection:
      c->code.append(0x0f);
      encode(c, 0xbe, value, operand, false);      
      break;

    case S2Selection:
      c->code.append(0x0f);
      encode(c, 0xbf, value, operand, false);      
      break;

    case Z2Selection:
      c->code.append(0x0f);
      encode(c, 0xb7, value, operand, false);      
      break;

    case S4Selection:
      encode(c, 0x63, value, operand, true);      
      break;

    default: abort(c);
    }
  } break;

  default: abort(c);
  }
}

ImmediateOperand*
value(Context* c, AbsoluteOperand* operand)
{
  if (c->codeLength >= 0) {
    return immediate
      (c, reinterpret_cast<intptr_t>
       (c->code.data + operand->promise->value(c)));
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
  if (c->codeLength >= 0) {
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
  if (c->codeLength >= 0) {
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
AddressOperand::setLabelValue(Context*, CodePromise* p)
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
    
  default: abort(c);
  }
}

void
ImmediateOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case alignedCall:
  case call:
  case jmp:
    address(c, new (c->zone.allocate(sizeof(ResolvedPromise)))
            ResolvedPromise(value))->apply(c, operation);
    break;

  case push: {
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
  } break;
    
  default: abort(c);
  }
}

Register
AbsoluteOperand::asRegister(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, mov, this);
  tmp->release(c);
  return tmp->value;  
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
  tmp->release(c);
  return tmp->value;
}

void
MemoryOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    encode(c, 0xff, 2, this, false);
    break;

  case neg:
    encode(c, 0xf7, 2, this, true);
    break;

  case pop:
    encode(c, 0x8f, 0, this, false);
    break;

  case push:
    encode(c, 0xff, 6, this, false);
    break;

  default: abort(c);
  }
}

void
MemoryOperand::apply(Context* c, Operation operation, SelectionType selection)
{
  switch (operation) {
  case push: {
    RegisterOperand* tmp = temporary(c);
    tmp->accept(c, mov, this, selection);
    tmp->apply(c, operation);
    tmp->release(c);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      RegisterOperand* operand)
{
  switch (operation) {
  case add: {
    encode(c, 0x01, operand->value, this, true);
  } break;

  case div: {
    RegisterOperand* tmp = acquire(c, rax);
    tmp->accept(c, mov, this);
    
    rex(c);
    c->code.append(0x99);
    rex(c);
    c->code.append(0xf7);
    c->code.append(0xf8 | operand->value);

    accept(c, mov, tmp);
    tmp->release(c);
  } break;

  case mov: {
    encode(c, 0x89, operand->value, this, true);
  } break;

  case sub: {
    encode(c, 0x29, operand->value, this, true);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      ImmediateOperand* operand)
{
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
  switch (operation) {
  case mov: {
    RegisterOperand* tmp = temporary(c);
    
    tmp->accept(c, mov, operand);
    accept(c, mov, tmp);

    tmp->release(c);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      RegisterOperand* operand, SelectionType selection)
{
  switch (operation) {
  case mov: {
    switch (selection) {
    case S1Selection:
    case S2Selection:
    case Z2Selection: { 
      RegisterOperand* tmp = temporary(c);
      
      tmp->accept(c, mov, operand, selection);
      accept(c, mov, tmp);
      
      tmp->release(c); 
    } break;

    case S4Selection:
      encode(c, 0x89, operand->value, this, false);
      break;

    default: abort(c);
    }
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
    c->segmentTable = static_cast<Segment**>(c->s->allocate(c->plan.length()));
    
    for (unsigned i = 0; i < tableSize; ++i) {
      c->plan.get(i * BytesPerWord, c->segmentTable + i, BytesPerWord);
    }
    
    qsort(c->segmentTable, tableSize, BytesPerWord, compareSegmentPointers);
  }

  for (unsigned i = 0; i < tableSize; ++i) {
    Segment* s = c->segmentTable[i];
    if (Verbose and c->codeLength >= 0) {
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
      if (Verbose and c->codeLength >= 0 and ei) {
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

  virtual Promise* poolOffset() {
    return new (c.zone.allocate(sizeof(PoolPromise)))
      PoolPromise(c.constantPool.length());
  }

  virtual Promise* codeOffset() {
    CodePromise* p = new (c.zone.allocate(sizeof(CodePromise)))
      CodePromise(false);

    Segment* s = currentSegment(&c);
    s->event->task = new (c.zone.allocate(sizeof(CodePromiseTask)))
      CodePromiseTask(p, s->event->task);

    return p;
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = absolute(&c, static_cast<MyPromise*>(poolOffset()));
    c.constantPool.appendAddress(v);
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return immediate(&c, v);
  }

  virtual void push(unsigned count) {
    ::push(&c, count);
  }

  virtual void push(Operand* v) {
    ::push(&c, static_cast<MyOperand*>(v));
  }

  virtual void push2(Operand* v) {
    push(v);
    if (BytesPerWord == 8) push(immediate(&c, 0));
  }

  virtual Operand* stack(unsigned index) {
    StackOperand* s = c.stack;
    unsigned i = 0;
    if (s->footprint() / BytesPerWord == 2) ++ i;

    for (; i < index; ++i) {
      s = s->next;
      if (s->footprint() / BytesPerWord == 2) ++ i;
    }

    return s;
  }

  virtual void pop(unsigned count) {
    ::pop(&c, count);
  }

  virtual Operand* pop() {
    Operand* tmp = static_cast<MyOperand*>(temporary());
    pop(tmp);
    return tmp;
  }

  virtual Operand* pop2() {
    if (BytesPerWord == 8) pop(1);
    return pop();
  }

  virtual void pop(Operand* dst) {
    ::pop(&c, static_cast<MyOperand*>(dst));
  }

  virtual void pop2(Operand* dst) {
    if (BytesPerWord == 8) pop(1);
    pop(dst);
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
    TemporaryOperand* r = new (c.zone.allocate(sizeof(TemporaryOperand)))
      TemporaryOperand(0);
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
    CodePromise* p = new (c.zone.allocate(sizeof(CodePromise)))
      CodePromise(true);

    Segment* s = currentSegment(&c);
    s->event->task = new (c.zone.allocate(sizeof(CodePromiseTask)))
      CodePromiseTask(p, s->event->task);

    static_cast<MyOperand*>(label)->setLabelValue(&c, p);
  }

  virtual Operand* indirectCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    mov(address, register_(&c, rax));
    call(immediate(&c, c.indirectCaller));

    add(immediate(&c, footprint), register_(&c, rsp));

    return register_(&c, rax);
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

  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    call(address);

    add(immediate(&c, footprint), register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void return_(Operand* v) {
    mov(v, register_(&c, rax));
    epilogue();
    ret();
  }

  virtual Operand* call(Operand* v) {
    appendOperation(&c, MyOperand::call, v);
    return register_(&c, rax);
  }

  virtual Operand* alignedCall(Operand* v) {
    appendOperation(&c, MyOperand::alignedCall, v);
    return register_(&c, rax);
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
    return selection(&c, S1Selection, static_cast<MyOperand*>(v));
  }

  virtual Operand* select2(Operand* v) {
    return selection(&c, S2Selection, static_cast<MyOperand*>(v));
  }

  virtual Operand* select2z(Operand* v) {
    return selection(&c, Z2Selection, static_cast<MyOperand*>(v));
  }

  virtual Operand* select4(Operand* v) {
    return selection(&c, S4Selection, static_cast<MyOperand*>(v));
  }

  virtual Operand* select8(Operand* v) {
    return selection(&c, S8Selection, static_cast<MyOperand*>(v));
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

  virtual Operand* logicalIp(unsigned ip) {
    return address
      (&c, new (c.zone.allocate(sizeof(IpPromise))) IpPromise(ip, true));
  }

  virtual Promise* logicalIpToOffset(unsigned ip) {
    return new (c.zone.allocate(sizeof(IpPromise))) IpPromise(ip, false);
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

    memcpy(out + codeSize(),
           c.constantPool.data,
           c.constantPool.length());
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
