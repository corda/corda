#include "compiler.h"
#include "assembler.h"

using namespace vm;

namespace {

class Context;
class MyOperand;
class ConstantValue;
class AddressValue;
class RegisterValue;
class MemoryValue;
class Event;

enum SyncType {
  SyncForCall,
  SyncForJump
};

class Value {
 public:
  virtual ~Value() { }

  virtual OperandType type() = 0;

  virtual bool equals(Value*) { return false; }

  virtual void preserve(Context*, MyOperand*) { }
  virtual void acquire(Context*, MyOperand*) { }
  virtual void release(Context*, MyOperand*) { }

  virtual RegisterValue* toRegister(Context*) = 0;

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand) = 0;
};

class MyOperand: public Compiler::Operand {
 public:
  MyOperand(Value* value):
    event(0), value(value), target(0)
  { }
  
  Event* event;
  Value* value;
  Value* target;
};

class Stack {
 public:
  Stack(MyOperand* operand, unsigned size, unsigned index, Stack* next):
    operand(operand), size(size), index(index), next(next)
  { }

  MyOperand* operand;
  unsigned size;
  unsigned index;
  Stack* next;
};

class State {
 public:
  State(State* s):
    stack(s ? s->stack : 0),
    next(s)
  { }

  Stack* stack;
  State* next;
};

class LogicalInstruction {
 public:
  unsigned visits;
  Event* firstEvent;
  Event* lastEvent;
  unsigned machineOffset;
  int predecessor;
};

class RegisterElement {
 public:
  bool reserved;
  MyOperand* operand;
};

class ConstantPoolNode {
 public:
  ConstantPoolNode(Promise* promise): promise(promise), next(0) { }

  Promise* promise;
  ConstantPoolNode* next;
};

class Junction {
 public:
  Junction(unsigned logicalIp, Junction* next):
    logicalIp(logicalIp),
    next(next)
  { }

  unsigned logicalIp;
  Junction* next;
};

class Context {
 public:
  Context(System* system, Assembler* assembler, Zone* zone):
    system(system),
    assembler(assembler),
    zone(zone),
    logicalIp(-1),
    state(new (zone->allocate(sizeof(State))) State(0)),
    event(0),
    logicalCode(0),
    logicalCodeLength(0),
    stackOffset(0),
    registers(static_cast<RegisterElement*>
              (zone->allocate
               (sizeof(RegisterElement) * assembler->registerCount()))),
    firstConstant(0),
    lastConstant(0),
    constantCount(0),
    junctions(0),
    machineCode(0)
  {
    memset(registers, 0, sizeof(RegisterElement) * assembler->registerCount());
    
    registers[assembler->base()].reserved = true;
    registers[assembler->stack()].reserved = true;
    registers[assembler->thread()].reserved = true;
  }

  System* system;
  Assembler* assembler;
  Zone* zone;
  int logicalIp;
  State* state;
  Event* event;
  LogicalInstruction* logicalCode;
  unsigned logicalCodeLength;
  unsigned stackOffset;
  RegisterElement* registers;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  unsigned constantCount;
  Junction* junctions;
  uint8_t* machineCode;
};

inline void NO_RETURN
abort(Context* c)
{
  abort(c->system);
}

#ifndef NDEBUG
inline void
assert(Context* c, bool v)
{
  assert(c->system, v);
}
#endif // not NDEBUG

inline void
expect(Context* c, bool v)
{
  expect(c->system, v);
}

void
apply(Context* c, UnaryOperation op, unsigned size, Value* a)
{
  OperandType type;
  Assembler::Operand* operand;
  a->asAssemblerOperand(c, &type, &operand);

  c->assembler->apply(op, size, type, operand);
}

void
apply(Context* c, BinaryOperation op, unsigned size, Value* a, Value* b)
{
  OperandType aType;
  Assembler::Operand* aOperand;
  a->asAssemblerOperand(c, &aType, &aOperand);

  OperandType bType;
  Assembler::Operand* bOperand;
  b->asAssemblerOperand(c, &bType, &bOperand);

  c->assembler->apply(op, size, aType, aOperand, bType, bOperand);
}

class PoolPromise: public Promise {
 public:
  PoolPromise(Context* c, int key): c(c), key(key) { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>
        (c->machineCode + pad(c->assembler->length()) + key);
    }
    
    abort(c);
  }

  virtual bool resolved() {
    return c->machineCode != 0;
  }

  Context* c;
  int key;
};

class CodePromise: public Promise {
 public:
  CodePromise(Context* c, CodePromise* next): c(c), offset(-1), next(next) { }

  CodePromise(Context* c, int offset): c(c), offset(offset), next(0) { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>(c->machineCode + offset);
    }
    
    abort(c);
  }

  virtual bool resolved() {
    return c->machineCode != 0 and offset >= 0;
  }

  Context* c;
  int offset;
  CodePromise* next;
};

class IpPromise: public Promise {
 public:
  IpPromise(Context* c, int logicalIp):
    c(c),
    logicalIp(logicalIp)
  { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>
        (c->machineCode + c->logicalCode[logicalIp].machineOffset);
    }

    abort(c);
  }

  virtual bool resolved() {
    return c->machineCode != 0;
  }

  Context* c;
  int logicalIp;
};

RegisterValue*
freeRegister(Context* c, unsigned size);

class ConstantValue: public Value {
 public:
  ConstantValue(Promise* value): value(value) { }

  virtual OperandType type() { return Constant; }

  virtual RegisterValue* toRegister(Context* c);

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    *type = Constant;
    *operand = &value;
  }

  Assembler::Constant value;
};

ConstantValue*
constant(Context* c, Promise* value)
{
  return new (c->zone->allocate(sizeof(ConstantValue))) ConstantValue(value);
}

ConstantValue*
constant(Context* c, int64_t value)
{
  return constant(c, new (c->zone->allocate(sizeof(ResolvedPromise)))
                  ResolvedPromise(value));
}

class AddressValue: public Value {
 public:
  AddressValue(Promise* address): address(address) { }

  virtual OperandType type() { return Address; }

  virtual RegisterValue* toRegister(Context* c);

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    *type = Address;
    *operand = &address;
  }

  Assembler::Address address;
};

AddressValue*
address(Context* c, Promise* address)
{
  return new (c->zone->allocate(sizeof(AddressValue))) AddressValue(address);
}

void preserve(Context* c, int reg, MyOperand* a);

class RegisterValue: public Value {
 public:
  RegisterValue(int low, int high): register_(low, high) { }

  virtual OperandType type() { return Register; }

  virtual bool equals(Value* o) {
    return this == o or
      (o->type() == Register
       and static_cast<RegisterValue*>(o)->register_.low == register_.low
       and static_cast<RegisterValue*>(o)->register_.high == register_.high);
  }

  virtual void preserve(Context* c, MyOperand* a) {
    ::preserve(c, register_.low, a);
    if (register_.high >= 0) ::preserve(c, register_.high, a);
  }

  virtual void acquire(Context* c, MyOperand* a) {
    if (a != c->registers[register_.low].operand) {
      fprintf(stderr, "%p acquire %d\n", a, register_.low);

      preserve(c, a);
      c->registers[register_.low].operand = a;
      if (register_.high >= 0) c->registers[register_.high].operand = a;
    }
  }

  virtual void release(Context* c, MyOperand* a UNUSED) {
    if (a == c->registers[register_.low].operand) {
      fprintf(stderr, "%p release %d\n", a, register_.low);

      c->registers[register_.low].operand = 0;
      if (register_.high >= 0) c->registers[register_.high].operand = 0;
    }
  }

  virtual RegisterValue* toRegister(Context*) {
    return this;
  }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    *type = Register;
    *operand = &register_;
  }

  Assembler::Register register_;
};

RegisterValue*
register_(Context* c, int low, int high = NoRegister)
{
  return new (c->zone->allocate(sizeof(RegisterValue)))
    RegisterValue(low, high);
}

class MemoryValue: public Value {
 public:
  MemoryValue(int base, int offset, int index, unsigned scale,
              TraceHandler* traceHandler):
    value(base, offset, index, scale, traceHandler)
  { }

  virtual OperandType type() { return Memory; }

  virtual bool equals(Value* o) {
    return this == o or
      (o->type() == Memory
       and static_cast<MemoryValue*>(o)->value.base == value.base
       and static_cast<MemoryValue*>(o)->value.offset == value.offset
       and static_cast<MemoryValue*>(o)->value.index == value.index
       and static_cast<MemoryValue*>(o)->value.scale == value.scale);
  }

  virtual RegisterValue* toRegister(Context* c) {
    RegisterValue* v = freeRegister(c, BytesPerWord);
    apply(c, Move, BytesPerWord, this, v);
    return v;
  }

  virtual int base(Context*) {
    return value.base;
  }

  virtual int index(Context*) {
    return value.index;
  }

  virtual void asAssemblerOperand(Context* c,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    value.base = base(c);
    value.index = index(c);
    *type = Memory;
    *operand = &value;
  }

  Assembler::Memory value;
};

MemoryValue*
memory(Context* c, int base, int offset, int index, unsigned scale,
       TraceHandler* traceHandler)
{
  return new (c->zone->allocate(sizeof(MemoryValue)))
    MemoryValue(base, offset, index, scale, traceHandler);
}

int
toRegister(Context* c, MyOperand* a)
{
  return a->value->toRegister(c)->register_.low;
}

class AbstractMemoryValue: public MemoryValue {
 public:
  AbstractMemoryValue(MyOperand* base, int offset, MyOperand* index,
                      unsigned scale, TraceHandler* traceHandler):
    MemoryValue(NoRegister, offset, NoRegister, scale, traceHandler),
    base_(base), index_(index)
  { }

  virtual int base(Context* c) {
    return ::toRegister(c, base_);
  }

  virtual int index(Context* c) {
    return index_ ? ::toRegister(c, base_) : NoRegister;
  }

  MyOperand* base_;
  MyOperand* index_;
};

AbstractMemoryValue*
memory(Context* c, MyOperand* base, int offset, MyOperand* index,
       unsigned scale, TraceHandler* traceHandler)
{
  return new (c->zone->allocate(sizeof(AbstractMemoryValue)))
    AbstractMemoryValue(base, offset, index, scale, traceHandler);
}

class Event {
 public:
  Event(Context* c): next(0), stack(c->state->stack), promises(0) {
    if (c->event) {
      c->event->next = this;
    }

    if (c->logicalCode[c->logicalIp].firstEvent == 0) {
      c->logicalCode[c->logicalIp].firstEvent = this;
    }

    c->event = this;
  }

  Event(Event* next): next(next) { }

  virtual ~Event() { }

  virtual Value* target(Context* c, MyOperand* value) = 0;
  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) = 0;
  virtual void compile(Context* c) = 0;

  Event* next;
  Stack* stack;
  CodePromise* promises;
};

class ArgumentEvent: public Event {
 public:
  ArgumentEvent(Context* c, unsigned size, MyOperand* a, unsigned index):
    Event(c), size(size), a(a), index(index)
  {
    assert(c, a->event == 0);
    a->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a);

    if (index < c->assembler->argumentRegisterCount()) {
      return register_(c, c->assembler->argumentRegister(index));
    } else {
      return memory(c, c->assembler->base(),
                    -(index + ((c->stackOffset + 1) * BytesPerWord)),
                    NoRegister, 0, 0);
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == a);
    a = new_;
    new_->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "ArgumentEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);

    a->value->release(c, a);
    a->target->preserve(c, a);

    if (not a->target->equals(a->value)) {
      apply(c, Move, size, a->value, a->target);
    }
  }

  unsigned size;
  MyOperand* a;
  unsigned index;
};

void
appendArgument(Context* c, unsigned size, MyOperand* value, unsigned index)
{
  new (c->zone->allocate(sizeof(ArgumentEvent)))
    ArgumentEvent(c, size, value, index);
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, unsigned size, MyOperand* a):
    Event(c), size(size), a(a)
  {
    if (a) {
      assert(c, a->event == 0);
      a->event = this;
    }
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a);

    return register_(c, c->assembler->returnLow(), c->assembler->returnHigh());
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == a);
    a = new_;
    a->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "ReturnEvent.compile\n");

    if (a) {
      if (a->target == 0) a->target = target(c, a);

      a->value->release(c, a);

      if (not a->target->equals(a->value)) {
        apply(c, Move, size, a->value, a->target);
      }
    }

    Assembler::Register base(c->assembler->base());
    Assembler::Register stack(c->assembler->stack());

    c->assembler->apply(Move, BytesPerWord, Register, &base, Register, &stack);
    c->assembler->apply(Pop, BytesPerWord, Register, &base);
    c->assembler->apply(Return);
  }

  unsigned size;
  MyOperand* a;
};

void
appendReturn(Context* c, unsigned size, MyOperand* value)
{
  new (c->zone->allocate(sizeof(ReturnEvent))) ReturnEvent(c, size, value);
}

class SyncForCallEvent: public Event {
 public:
  SyncForCallEvent(Context* c, unsigned size, unsigned index, MyOperand* src,
                   MyOperand* dst):
    Event(c), size(size), index(index), src(src), dst(dst)
  {
    assert(c, src->event == 0);
    src->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    return memory(c, c->assembler->base(),
                  -(index + ((c->stackOffset + 1) * BytesPerWord)),
                  NoRegister, 0, 0);
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "SyncForCallEvent.compile\n");

    if (src->target == 0) src->target = target(c, src);

    src->value->release(c, src);

    if (not src->target->equals(src->value)) {
      if (src->value->type() == Memory and src->target->type() == Memory) {
        RegisterValue* tmp = freeRegister(c, size);
        tmp->preserve(c, 0);
        apply(c, Move, size, src->value, tmp);
        src->value = tmp;
      }

      apply(c, Move, size, src->value, src->target);
    }

    dst->value = src->target;
  }

  unsigned size;
  unsigned index;
  MyOperand* src;
  MyOperand* dst;
};

void
appendSyncForCall(Context* c, unsigned size, unsigned index, MyOperand* src,
                  MyOperand* dst)
{
  new (c->zone->allocate(sizeof(SyncForCallEvent)))
    SyncForCallEvent(c, size, index, src, dst);
}

class SyncForJumpEvent: public Event {
 public:
  SyncForJumpEvent(Context* c, unsigned size, unsigned index, MyOperand* src,
                   MyOperand* dst):
    Event(c), size(size), index(index), src(src), dst(dst)
  {
    assert(c, src->event == 0);
    src->event = this;
  }

  SyncForJumpEvent(Context* c, Event* next, unsigned size, unsigned index,
                   MyOperand* src, MyOperand* dst):
    Event(next), size(size), index(index), src(src), dst(dst)
  {
    assert(c, src->event == 0);
    src->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    if (BytesPerWord == 4 and size == 8) {
      return register_
        (c, c->assembler->stackSyncRegister(index / BytesPerWord),
         c->assembler->stackSyncRegister((index / BytesPerWord) + 1));
    } else {
      return register_
        (c, c->assembler->stackSyncRegister(index / BytesPerWord));
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "SyncForJumpEvent.compile\n");

    if (src->target == 0) src->target = target(c, src);

    src->value->release(c, src);
    src->target->acquire(c, dst);

    if (not src->target->equals(src->value)) {
      apply(c, Move, size, src->value, src->target);
    }

    dst->value = src->target;
  }

  unsigned size;
  unsigned index;
  MyOperand* src;
  MyOperand* dst;
};

void
appendSyncForJump(Context* c, unsigned size, unsigned index, MyOperand* src,
                  MyOperand* dst)
{
  new (c->zone->allocate(sizeof(SyncForJumpEvent)))
    SyncForJumpEvent(c, size, index, src, dst);
}

class CallEvent: public Event {
 public:
  CallEvent(Context* c, MyOperand* address, void* indirection, unsigned flags,
            TraceHandler* traceHandler, MyOperand* result,
            unsigned stackOffset):
    Event(c),
    address(address),
    indirection(indirection),
    flags(flags),
    traceHandler(traceHandler),
    result(result),
    stackOffset(stackOffset)
  {
    assert(c, address->event == 0);
    address->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == address);

    if (indirection) {
      return register_(c, c->assembler->returnLow(), NoRegister);
    } else {
      return 0;
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == address);
    address = new_;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CallEvent.compile\n");

    if (indirection and address->target == 0) {
      address->target = target(c, address);
    }

    address->value->release(c, address);

    if (result->event) {
      result->value = register_
        (c, c->assembler->returnLow(), c->assembler->returnHigh());
      result->value->acquire(c, result);
    }

    if (stackOffset != c->stackOffset) {
      apply(c, LoadAddress, BytesPerWord,
            memory(c, c->assembler->base(),
                   -((stackOffset + 1) * BytesPerWord),
                   NoRegister, 0, 0),
            register_(c, c->assembler->stack()));
    }

    UnaryOperation type = ((flags & Compiler::Aligned) ? AlignedCall : Call);

    if (indirection) {
      if (not address->target->equals(address->value)) {
        apply(c, Move, BytesPerWord, address->value, address->target);
      }
      apply(c, type, BytesPerWord,
            constant(c, reinterpret_cast<intptr_t>(indirection)));
    } else {
      apply(c, type, BytesPerWord, address->value);
    }

    if (traceHandler) {
      traceHandler->handleTrace
        (new (c->zone->allocate(sizeof(CodePromise)))
         CodePromise(c, c->assembler->length()));
    }
  }

  MyOperand* address;
  void* indirection;
  unsigned flags;
  TraceHandler* traceHandler;
  MyOperand* result;
  unsigned stackOffset;
};

void
appendCall(Context* c, MyOperand* address, void* indirection, unsigned flags,
           TraceHandler* traceHandler, MyOperand* result,
           unsigned stackOffset)
{
  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, indirection, flags, traceHandler, result,
              stackOffset);
}

int
freeRegister(Context* c)
{
  for (int i = c->assembler->registerCount(); i >= 0; --i) {
    if ((not c->registers[i].reserved)
        and c->registers[i].operand == 0)
    {
      return i;
    }
  }

  for (int i = c->assembler->registerCount(); i >= 0; --i) {
    if (not c->registers[i].reserved) {
      return i;
    }
  }

  abort(c);
}

RegisterValue*
freeRegister(Context* c, unsigned size)
{
  if (BytesPerWord == 4 and size == 8) {
    return register_(c, freeRegister(c), freeRegister(c));
  } else {
    return register_(c, freeRegister(c));
  }
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned size, MyOperand* src,
            MyOperand* dst):
    Event(c), type(type), size(size), src(src), dst(dst)
  {
    assert(c, src->event == 0);
    src->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    if (dst->value) {
      return dst->value;
    } else if (dst->event) {
      return dst->event->target(c, dst);
    } else {
      return 0;
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MoveEvent.compile\n");

    if (src->target == 0) src->target = target(c, src);

    if (src->target == 0) {
      src->target = freeRegister(c, size);
    } else if (src->value->type() == Memory and src->target->type() == Memory)
    {
      RegisterValue* tmp = freeRegister(c, size);
      tmp->preserve(c, 0);
      apply(c, Move, size, src->value, tmp);
      src->value = tmp;
    }

    src->value->release(c, src);
    src->target->acquire(c, dst);

    apply(c, type, size, src->value, src->target);

    dst->value = src->target;
  }

  BinaryOperation type;
  unsigned size;
  MyOperand* src;
  MyOperand* dst;
};

void
appendMove(Context* c, BinaryOperation type, unsigned size, MyOperand* src,
           MyOperand* dst)
{
  new (c->zone->allocate(sizeof(MoveEvent)))
    MoveEvent(c, type, size, src, dst);
}

class DupEvent: public Event {
 public:
  DupEvent(Context* c, unsigned size, MyOperand* src, MyOperand* dst):
    Event(c), size(size), src(src), dst(dst)
  { }

  virtual Value* target(Context* c, MyOperand*) {
    abort(c);
  }

  virtual void replace(Context* c, MyOperand*, MyOperand*) {
    abort(c);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "DupEvent.compile\n");

    Value* value = src->value;
    Value* target = dst->value;

    if (target == 0) {
      if (dst->event) {
        target = dst->event->target(c, dst);
      } else {
        target = freeRegister(c, size);
      }
    } else if (value->type() == Memory and target->type() == Memory) {
      RegisterValue* tmp = freeRegister(c, size);
      tmp->preserve(c, 0);
      apply(c, Move, size, value, tmp);
      value = tmp;
    }

    target->acquire(c, dst);

    apply(c, Move, size, value, target);

    dst->value = target;
  }

  unsigned size;
  MyOperand* src;
  MyOperand* dst;
};

void
appendDup(Context* c, unsigned size, MyOperand* src, MyOperand* dst)
{
  new (c->zone->allocate(sizeof(DupEvent))) DupEvent(c, size, src, dst);
}

class CompareEvent: public Event {
 public:
  CompareEvent(Context* c, unsigned size, MyOperand* a, MyOperand* b):
    Event(c), size(size), a(a), b(b)
  {
    assert(c, a->event == 0);
    a->event = this;
    assert(c, b->event == 0);
    b->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a or v == b);

    return 0;
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    if (old == a) {
      a = new_;
      a->target = old->target;      
    } else {
      assert(c, old == b);
      b = new_;
      b->target = old->target;
    }
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CompareEvent.compile\n");

    a->value->release(c, a);
    b->value->release(c, b);

    apply(c, Compare, size, a->value, b->value);
  }

  unsigned size;
  MyOperand* a;
  MyOperand* b;
};

void
appendCompare(Context* c, unsigned size, MyOperand* a, MyOperand* b)
{
  new (c->zone->allocate(sizeof(CompareEvent))) CompareEvent(c, size, a, b);
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, UnaryOperation type, MyOperand* address):
    Event(c), type(type), address(address)
  {
    assert(c, address->event == 0);
    address->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == address);

    return 0;
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == address);
    address = new_;
    address->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "BranchEvent.compile\n");

    address->value->release(c, address);

    apply(c, type, BytesPerWord, address->value);
  }

  UnaryOperation type;
  MyOperand* address;
};

void
appendBranch(Context* c, UnaryOperation type, MyOperand* address)
{
  new (c->zone->allocate(sizeof(BranchEvent))) BranchEvent(c, type, address);
}

class JumpEvent: public Event {
 public:
  JumpEvent(Context* c, MyOperand* address):
    Event(c),
    address(address)
  {
    assert(c, address->event == 0);
    address->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == address);

    return 0;
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == address);
    address = new_;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "JumpEvent.compile\n");

    address->value->release(c, address);

    apply(c, Jump, BytesPerWord, address->value);
  }

  MyOperand* address;
  unsigned stackOffset;
  bool alignCall;
  TraceHandler* traceHandler;
};

void
appendJump(Context* c, MyOperand* address)
{
  new (c->zone->allocate(sizeof(BranchEvent))) JumpEvent(c, address);
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, BinaryOperation type, unsigned size, MyOperand* a,
               MyOperand* b, MyOperand* result):
    Event(c), type(type), size(size), a(a), b(b), result(result)
  {
    assert(c, a->event == 0);
    a->event = this;
    assert(c, b->event == 0);
    b->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    Assembler::Register ar(NoRegister);
    Assembler::Register br(NoRegister);
    c->assembler->getTargets(type, size, &ar, &br);

    if (v == a) {
      if (ar.low == NoRegister) {
        return 0;
      } else {
        return register_(c, ar.low, ar.high);
      }
    } else {
      assert(c, v == b);

      if (br.low == NoRegister) {
        if (result->event) {
          Value* v = result->event->target(c, result);
          if (v->type() == Register) {
            return v;
          } else {
            return 0;
          }
        } else {
          return 0;
        }
      } else {
        return register_(c, br.low, br.high);
      }
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    if (old == a) {
      a = new_;
      a->target = old->target;      
    } else {
      assert(c, old == b);
      b = new_;
      b->target = old->target;
    }
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CombineEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);
    if (b->target == 0) b->target = target(c, b);

    a->value->release(c, a);
    b->value->release(c, b);
    b->value->acquire(c, result);

    if (a->target and not a->target->equals(a->value)) {
      apply(c, Move, size, a->value, a->target);
    }
    if (b->target and not b->target->equals(b->value)) {
      apply(c, Move, size, b->value, b->target);
    }

    apply(c, type, size, a->value, b->value);

    result->value = b->value;
  }

  BinaryOperation type;
  unsigned size;
  MyOperand* a;
  MyOperand* b;
  MyOperand* result;
};

void
appendCombine(Context* c, BinaryOperation type, unsigned size, MyOperand* a,
              MyOperand* b, MyOperand* result)
{
  new (c->zone->allocate(sizeof(CombineEvent)))
    CombineEvent(c, type, size, a, b, result);
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, UnaryOperation type, unsigned size, MyOperand* a,
                 MyOperand* result):
    Event(c), type(type), size(size), a(a), result(result)
  {
    assert(c, a->event == 0);
    a->event = this;
  }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a);
      
    Assembler::Register r(NoRegister);
    c->assembler->getTargets(type, size, &r);

    if (r.low == NoRegister) {
      return result->event->target(c, result);
    } else {
      return register_(c, r.low, r.high);
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == a);
    a = new_;
    a->target = old->target;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "TranslateEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);

    result->value->acquire(c, result);

    if (a->target and not a->target->equals(a->value)) {
      apply(c, Move, size, a->value, a->target);
    }
    apply(c, type, size, a->value);

    result->value = a->value;
  }

  UnaryOperation type;
  unsigned size;
  MyOperand* a;
  MyOperand* result;
};

void
appendTranslate(Context* c, UnaryOperation type, unsigned size, MyOperand* a,
                MyOperand* result)
{
  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent(c, type, size, a, result);
}

RegisterValue*
ConstantValue::toRegister(Context* c)
{
  RegisterValue* v = freeRegister(c, BytesPerWord);
  apply(c, Move, BytesPerWord, this, v);
  return v;
}

RegisterValue*
AddressValue::toRegister(Context* c)
{
  RegisterValue* v = freeRegister(c, BytesPerWord);
  apply(c, Move, BytesPerWord, this, v);
  return v;
}

void
preserve(Context* c, int reg, MyOperand* a)
{
  MyOperand* b = c->registers[reg].operand;
  if (b and a != b) {
    fprintf(stderr, "%p preserve %d for %p\n", a, reg, b);

    abort(c);
//     MemoryValue* dst = memory
//       (c, c->assembler->base(), (b->index + c->stackOffset) * BytesPerWord,
//        -1, 0, 0);

//     apply(c, Move, b->size, b->value, dst);

//     b->value = dst;
//     c->registers[reg].operand = 0;
  }
}

MyOperand*
operand(Context* c, Value* value = 0)
{
  return new (c->zone->allocate(sizeof(MyOperand))) MyOperand(value);
}

void
pushState(Context* c)
{
  c->state = new (c->zone->allocate(sizeof(State)))
    State(c->state);
}

void
popState(Context* c)
{
  c->state = new (c->zone->allocate(sizeof(State)))
    State(c->state->next);
}

Stack*
stack(Context* c, MyOperand* operand, unsigned size, unsigned index,
      Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(operand, size, index, next);
}

Stack*
stack(Context* c, MyOperand* operand, unsigned size, Stack* next)
{
  return stack(c, operand, size, (next ? next->index + size : 0), next);
}

void
push(Context* c, unsigned size, MyOperand* o)
{
  assert(c, ceiling(size, BytesPerWord));
  assert(c, o->event == 0);

  c->state->stack = stack(c, o, ceiling(size, BytesPerWord), c->state->stack);
}

MyOperand*
pop(Context* c, unsigned size UNUSED)
{
  Stack* s = c->state->stack;
  assert(c, ceiling(size, BytesPerWord) == s->size);

  c->state->stack = s->next;
  return s->operand;
}

void
syncStack(Context* c, SyncType type)
{
  Stack* newStack = 0;
  for (Stack* s = c->state->stack; s; s = s->next) {
    MyOperand* old = s->operand;
    MyOperand* new_ = operand(c);
    Stack* ns = stack(c, new_, s->size, s->index, 0);
    if (newStack) {
      newStack->next = ns;
    } else {
      newStack = c->state->stack = ns;
    }
      
    if (type == SyncForCall) {
      appendSyncForCall
        (c, s->size * BytesPerWord, s->index * BytesPerWord, old, new_);
    } else {
      appendSyncForJump
        (c, s->size * BytesPerWord, s->index * BytesPerWord, old, new_);
    }
  }
}

void
updateJunctions(Context* c)
{
  for (Junction* j = c->junctions; j; j = j->next) {
    LogicalInstruction* i = c->logicalCode + j->logicalIp;

    if (i->predecessor >= 0) {
      LogicalInstruction* p = c->logicalCode + i->predecessor;

      for (Stack* s = c->state->stack; s; s = s->next) {
        MyOperand* old = s->operand;
        MyOperand* new_ = operand(c);

        if (old->event) {
          old->event->replace(c, old, new_);
        }

        p->lastEvent = p->lastEvent->next = new
          (c->zone->allocate(sizeof(SyncForJumpEvent)))
          SyncForJumpEvent
          (c, p->lastEvent->next, s->size * BytesPerWord,
           s->index * BytesPerWord, old, new_);
      }
    }
  }
}

void
compile(Context* c)
{
  Assembler* a = c->assembler;

  Assembler::Register base(a->base());
  Assembler::Register stack(a->stack());
  a->apply(Push, BytesPerWord, Register, &base);
  a->apply(Move, BytesPerWord, Register, &stack, Register, &base);

  for (unsigned i = 0; i < c->logicalCodeLength; ++ i) {
    fprintf(stderr, "compile ip %d\n", i);
    for (Event* e = c->logicalCode[i].firstEvent; e; e = e->next) {
      e->compile(c);

      if (e == c->logicalCode[i].lastEvent) break;
    }
  }
}

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Assembler* assembler, Zone* zone):
    c(s, assembler, zone)
  { }

  virtual void pushState() {
    ::pushState(&c);
  }

  virtual void popState() {
    ::pushState(&c);
  }

  virtual void init(unsigned logicalCodeLength, unsigned stackOffset) {
    c.logicalCodeLength = logicalCodeLength;
    c.stackOffset = stackOffset;
    c.logicalCode = static_cast<LogicalInstruction*>
      (c.zone->allocate(sizeof(LogicalInstruction) * logicalCodeLength));
    memset(c.logicalCode, 0, sizeof(LogicalInstruction) * logicalCodeLength);
  }

  virtual void visitLogicalIp(unsigned logicalIp) {
    if ((++ c.logicalCode[logicalIp].visits) == 1) {
      c.junctions = new (c.zone->allocate(sizeof(Junction)))
        Junction(logicalIp, c.junctions);
    }
  }

  virtual void startLogicalIp(unsigned logicalIp) {
    if (c.logicalIp >= 0) {
      c.logicalCode[c.logicalIp].lastEvent = c.event;
    }

    c.logicalIp = logicalIp;
  }

  virtual Promise* machineIp(unsigned logicalIp) {
    return new (c.zone->allocate(sizeof(IpPromise))) IpPromise(&c, logicalIp);
  }

  virtual Promise* poolAppend(intptr_t value) {
    return poolAppendPromise(new (c.zone->allocate(sizeof(ResolvedPromise)))
                             ResolvedPromise(value));
  }

  virtual Promise* poolAppendPromise(Promise* value) {
    Promise* p = new (c.zone->allocate(sizeof(PoolPromise)))
      PoolPromise(&c, c.constantCount);

    ConstantPoolNode* constant
      = new (c.zone->allocate(sizeof(ConstantPoolNode)))
      ConstantPoolNode(value);

    if (c.firstConstant) {
      c.lastConstant->next = constant;
    } else {
      c.firstConstant = constant;
    }
    c.lastConstant = constant;
    ++ c.constantCount;

    return p;
  }

  virtual Operand* constant(int64_t value) {
    return promiseConstant(new (c.zone->allocate(sizeof(ResolvedPromise)))
                           ResolvedPromise(value));
  }

  virtual Operand* promiseConstant(Promise* value) {
    return operand(&c, ::constant(&c, value));
  }

  virtual Operand* address(Promise* address) {
    return operand(&c, ::address(&c, address));
  }

  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1,
                          TraceHandler* traceHandler = 0)
  {
    return operand
      (&c, ::memory
       (&c, static_cast<MyOperand*>(base), displacement,
        static_cast<MyOperand*>(index), scale, traceHandler));
  }

  virtual Operand* stack() {
    return operand(&c, register_(&c, c.assembler->stack()));
  }

  virtual Operand* base() {
    return operand(&c, register_(&c, c.assembler->base()));
  }

  virtual Operand* thread() {
    return operand(&c, register_(&c, c.assembler->thread()));
  }

  virtual Operand* label() {
    return operand(&c, ::constant(&c, static_cast<Promise*>(0)));
  }

  Promise* machineIp() {
    return c.event->promises = new (c.zone->allocate(sizeof(CodePromise)))
      CodePromise(&c, c.event->promises);
  }

  virtual void mark(Operand* label) {
    static_cast<ConstantValue*>(static_cast<MyOperand*>(label)->value)->value
      = machineIp();
  }

  virtual void push(unsigned size, Operand* value) {
    ::push(&c, size, static_cast<MyOperand*>(value));
  }

  virtual Operand* pop(unsigned size) {
    return ::pop(&c, size);
  }

  virtual void pushed(unsigned count) {
    for (unsigned i = 0; i < count; ++i) ::push(&c, BytesPerWord, operand(&c));
  }

  virtual void popped(unsigned count) {
    for (unsigned i = count; i > 0;) {
      Stack* s = c.state->stack;
      c.state->stack = s->next;
      i -= s->size;
    }
  }

  virtual Operand* peek(unsigned size, unsigned index) {
    Stack* s = c.state->stack;
    for (unsigned i = index; i > 0;) {
      s = s->next;
      i -= s->size;
    }
    assert(&c, s->size == ceiling(size, BytesPerWord));
    return s->operand;
  }

  virtual Operand* call(Operand* address,
                        void* indirection,
                        unsigned flags,
                        TraceHandler* traceHandler,
                        unsigned,
                        unsigned argumentCount,
                        ...)
  {
    va_list a; va_start(a, argumentCount);

    unsigned footprint = 0;
    unsigned size = BytesPerWord;
    for (unsigned i = 0; i < argumentCount; ++i) {
      MyOperand* o = va_arg(a, MyOperand*);
      if (o) {
        appendArgument(&c, size, o, footprint);
        size = BytesPerWord;
      } else {
        size = 8;
      }
      ++ footprint;
    }

    va_end(a);

    syncStack(&c, SyncForCall);

    unsigned stackOffset = c.stackOffset
      + (c.state->stack ? c.state->stack->index + c.state->stack->size : 0)
      + (footprint > c.assembler->argumentRegisterCount() ?
         footprint - c.assembler->argumentRegisterCount() : 0);

    MyOperand* result = operand(&c);
    appendCall(&c, static_cast<MyOperand*>(address), indirection, flags,
               traceHandler, result, stackOffset);
    return result;
  }

  virtual void return_(unsigned size, Operand* value) {
    appendReturn(&c, size, static_cast<MyOperand*>(value));
  }

  virtual void store(unsigned size, Operand* src, Operand* dst) {
    appendMove(&c, Move, size, static_cast<MyOperand*>(src),
               static_cast<MyOperand*>(dst));
  }

  virtual Operand* load(unsigned size, Operand* src) {
    MyOperand* dst = operand(&c);
    appendMove(&c, Move, size, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* loadz(unsigned size, Operand* src) {
    MyOperand* dst = operand(&c);
    appendMove(&c, MoveZ, size, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* load4To8(Operand* src) {
    MyOperand* dst = operand(&c);
    appendMove(&c, Move4To8, 0, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* dup(unsigned size, Operand* src) {
    MyOperand* dst = operand(&c);
    appendDup(&c, size, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual void cmp(unsigned size, Operand* a, Operand* b) {
    appendCompare(&c, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b));
  }

  virtual void jl(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfLess, static_cast<MyOperand*>(address));
  }

  virtual void jg(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfGreater, static_cast<MyOperand*>(address));
  }

  virtual void jle(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfLessOrEqual, static_cast<MyOperand*>(address));
  }

  virtual void jge(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfGreaterOrEqual, static_cast<MyOperand*>(address));
  }

  virtual void je(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfEqual, static_cast<MyOperand*>(address));
  }

  virtual void jne(Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfNotEqual, static_cast<MyOperand*>(address));
  }

  virtual void jmp(Operand* address) {
    syncStack(&c, SyncForJump);

    appendJump(&c, static_cast<MyOperand*>(address));
  }

  virtual Operand* add(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Add, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* sub(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Subtract, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* mul(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Multiply, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* div(unsigned size, Operand* a, Operand* b)  {
    MyOperand* result = operand(&c);
    appendCombine(&c, Divide, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* rem(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Remainder, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* shl(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, ShiftLeft, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* shr(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, ShiftRight, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* ushr(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, UnsignedShiftRight, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* and_(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, And, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* or_(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Or, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* xor_(unsigned size, Operand* a, Operand* b) {
    MyOperand* result = operand(&c);
    appendCombine(&c, Xor, size, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* neg(unsigned size, Operand* a) {
    MyOperand* result = operand(&c);
    appendTranslate(&c, Negate, size, static_cast<MyOperand*>(a), result);
    return result;
  }

  virtual unsigned compile() {
    updateJunctions(&c);
    ::compile(&c);
    return c.assembler->length();
  }

  virtual unsigned poolSize() {
    return c.constantCount;
  }

  virtual void writeTo(uint8_t* dst) {
    c.machineCode = dst;
    c.assembler->writeTo(dst);

    int i = 0;
    for (ConstantPoolNode* n = c.firstConstant; n; n = n->next) {
      *reinterpret_cast<intptr_t*>(dst + pad(c.assembler->length()) + (i++))
        = n->promise->value();
    }
  }

  virtual void dispose() {
    // ignore
  }

  Context c;
};

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, Assembler* assembler, Zone* zone)
{
  return new (zone->allocate(sizeof(MyCompiler)))
    MyCompiler(system, assembler, zone);
}

} // namespace vm
