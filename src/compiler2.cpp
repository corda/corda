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

class Value {
 public:
  virtual bool equals(Value* o) { return false; }
  virtual bool equals(RegisterValue* o) { return false; }

  virtual void preserve(Context*) { }
  virtual void acquire(Context*, MyOperand*) { }
  virtual void release(Context*, MyOperand*) { }

  virtual RegisterValue* toRegister(Context*, unsigned size) = 0;

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand) = 0;
};

class MyOperand: public Operand {
 public:
  MyOperand(unsigned size, Value* value):
    size(size), event(0), value(value), index(0), next(0)
  { }
  
  unsigned size;
  Event* event;
  Value* value;
  unsigned index;
  MyOperand* next;
};

class State {
 public:
  State(State* s):
    stack(s ? s->stack : 0),
    next(s)
  { }

  MyOperand* stack;
  State* next;
};

class LogicalInstruction {
 public:
  unsigned visits;
  Event* firstEvent;
  Event* lastEvent;
};

class Register {
 public:
  bool reserved;
  MyOperand* operand;
};

class Context {
 public:
  Context(System* s, Assembler* assembler, Zone* zone):
    system(system),
    assembler(assembler),
    zone(zone),
    logicalIp(-1),
    state(new (c->zone->allocate(sizeof(State))) State(0)),
    event(0),
    logicalCode(0),
    registers(static_cast<Register*>
              (zone->allocate(sizeof(Register) * assembler->registerCount()))),
    firstConstant(0),
    lastConstant(0),
    constantCount(0)
  {
    memset(registers, 0, sizeof(Register) * assembler->registerCount());
    
    registers[assembler->base()].reserved = true;
    registers[assembler->stack()].reserved = true;
    registers[assembler->thread()].reserved = true;
  }

  System* system;
  Assembler* assembler;
  Zone* zone;
  unsigned logicalIp;
  State* state;
  Event* event;
  LogicalInstruction* logicalCode;
  Register* registers;
  Constant* firstConstant;
  Constant* lastConstant;
  unsigned constantCount;
};

void
apply(Context* c, UnaryOperand op, unsigned size, Value* a)
{
  OperandType type;
  Assembler::Operand* operand;
  a->asAssemblerOperand(&type, &operand);

  c->assembler->apply(op, size, type, operand);
}

void
apply(Context* c, BinaryOperand op, unsigned size, Value* a, Value* b)
{
  OperandType aType;
  Assembler::Operand* aOperand;
  a->asAssemblerOperand(&aType, &aOperand);

  OperandType bType;
  Assembler::Operand* bOperand;
  b->asAssemblerOperand(&bType, &bOperand);

  c->assembler->apply(op, size, aType, aOperand, bType, bOperand);
}

class ResolvedPromise: public Promise {
 public:
  ResolvedPromise(int64_t value): value_(value) { }

  virtual int64_t value() {
    return value_;
  }

  virtual bool resolved() {
    return true;
  }

  int64_t value_;
};

class PoolPromise: public Promise {
 public:
  PoolPromise(Context* c, int key): c(c), key(key) { }

  virtual int64_t value() {
    if (resolved(c)) {
      return reinterpret_cast<intptr_t>(c->code + c->codeLength + key);
    }
    
    abort(c);
  }

  virtual bool resolved() {
    return c->code != 0;
  }

  Context* c;
  int key;
};

class CodePromise: public Promise {
 public:
  CodePromise(Context* c): c(c), offset(-1) { }

  virtual int64_t value() {
    if (resolved(c)) {
      return reinterpret_cast<intptr_t>(c->code + offset);
    }
    
    abort(c);
  }

  virtual bool resolved(Context* c) {
    return c->code != 0 and offset >= 0;
  }

  Context* c;
  int offset;
};

class IpPromise: public Promise {
 public:
  IpPromise(Context* c, int logicalIp):
    c(c),
    logicalIp(logicalIp)
  { }

  virtual int64_t value() {
    if (resolved(c)) {
      unsigned bottom = 0;
      unsigned top = c->plan.length() / BytesPerWord;
      for (unsigned span = top - bottom; span; span = top - bottom) {
        unsigned middle = bottom + (span / 2);
        Segment* s = c->segmentTable[middle];

        if (logicalIp == s->logicalIp) {
          return reinterpret_cast<intptr_t>(c->code + s->offset);
        } else if (logicalIp < s->logicalIp) {
          top = middle;
        } else if (logicalIp > s->logicalIp) {
          bottom = middle + 1;
        }
      }
    }

    abort(c);
  }

  virtual bool resolved() {
    return c->code != 0;
  }

  Context* c;
  int logicalIp;
};

class ConstantValue: public Value {
 public:
  ConstantValue(Promise* value): value(value) { }

  virtual RegisterValue* toRegister(Context* c, unsigned size) {
    RegisterValue* v = freeRegister(c, size);
    apply(c, Move, size, this, v);
    return v;
  }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  void** operand)
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

class AddressValue: public Value {
 public:
  AddressValue(Promise* address): address(address) { }

  virtual RegisterValue* toRegister(Context* c, unsigned size) {
    RegisterValue* v = freeRegister(c, size);
    apply(c, Move, size, this, v);
    return v;
  }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  void** operand)
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

void preserve(Context* c, int reg);

class RegisterValue: public Value {
 public:
  RegisterValue(int low, int high): register_(low, high) { }

  virtual bool equals(Value* o) { return o->equals(this); }

  virtual bool equals(RegisterValue* o) {
    return o->register_.low == register_.low
      and o->register_.high == register_.high;
  }

  virtual void preserve(Context* c) {
    ::preserve(c, register_.low);
    if (high >= 0) ::preserve(c, register_.high);
  }

  virtual void acquire(Context* c, MyOperand* a) {
    preserve(c);
    c->registers[register_.low].operand = a;
    if (high >= 0) c->registers[register_.high].operand = a;
  }

  virtual void release(Context* c, MyOperand* a) {
    c->registers[register_.low].operand = 0; 
    if (high >= 0) c->registers[register_.high].operand = 0;   
  }

  virtual RegisterValue* toRegister(Context*, unsigned) {
    return this;
  }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  void** operand)
  {
    *type = Register;
    *operand = &register_;
  }

  Assembler::Register register_;
};

RegisterValue*
register_(Context* c, int low, int high)
{
  return new (c->zone->allocate(sizeof(RegisterValue)))
    RegisterValue(low, high);
}

class MemoryValue: public Value {
 public:
  MemoryValue(int base, int offset, int index, unsigned scale,
              TraceHandler* traceHandler):
    value(base, offset, index. scale, traceHandler)
  { }

  virtual RegisterValue* toRegister(Context* c, unsigned size) {
    RegisterValue* v = freeRegister(c, size);
    apply(c, Move, size, this, v);
    return v;
  }

  virtual int base(Context* c) {
    return value.base;
  }

  virtual int index(Context* c) {
    return value.index;
  }

  virtual void asAssemblerrOperand(Context* c,
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

class AbstractMemoryValue: public MemoryValue {
 public:
  AbstractMemoryValue(MyOperand* base, int offset, MyOperand* index,
                      unsigned scale, TraceHandler* traceHandler):
    MemoryValue(NoRegister, offset, NoRegister, scale, traceHandler)
    base_(base), index_(index)
  { }

  virtual int base(Context* c) {
    return base_->toRegister(c);
  }

  virtual int index(Context* c) {
    return index_ ? index_->toRegister(c) : NoRegister;
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
  Event(Context* c): next(0) {
    if (c->event) {
      c->event->next = this;
    }

    if (c->logicalCode[c->logicalIp].firstEvent == 0) {
      c->logicalCode[c->logicalIp].firstEvent = this;
    }

    c->event = this;
  }

  Event(Event* next): next(next) { }

  virtual void target(Context* c, MyOperand* value) = 0;
  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) = 0;
  virtual void compile(Context* c) = 0;

  Event* next;
};

class ArgumentEvent: public Event {
 public:
  ArgumentEvent(Context* c, MyOperand* a, unsigned index):
    Event(c), a(a), index(index)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a);

    if (index < c->assembler->argumentRegisterCount()) {
      return register_(c, c->assembler->argumentRegister(index));
    } else {
      return memory(c, c->assembler->base(),
                    (v->index + c->stackOffset) * BytesPerWord,
                    NoRegister, 0, 0);
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == a);
    a = new_;
    a->target = old->target;
  }

  virtual void compile(Context* c) {
    a->value->release(c, a);
    a->target->preserve(c);

    if (not a->target->equals(a->value)) {
      apply(c, Move, a->size, a->value, a->target);
    }
  }

  MyOperand* a;
  unsigned index;
};

void
appendArgument(Context* c, MyOperand* value, unsigned index)
{
  new (c->zone->allocate(sizeof(ArgumentEvent)))
    ArgumentEvent(c, value, index);
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, MyOperand* a):
    Event(c), a(a)
  { }

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
    a->value->release(c, a);

    if (not a->target->equals(a->value)) {
      apply(c, Move, a->size, a->value, a->target);
    }
    c->assembler->apply(Return);
  }

  MyOperand* a;
};

void
appendReturn(Context* c, MyOperand* value)
{
  new (c->zone->allocate(sizeof(ReturnEvent))) ReturnEvent(c, value);
}

class SyncForCallEvent: public Event {
 public:
  SyncForCallEvent(Context* c, MyOperand* src, MyOperand* dst):
    Event(c), src(src), dst(dst)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    return memory(c, register_(c, BaseRegister),
                  (v->index + c->stackOffset) * BytesPerWord, 0, 0, 0);
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    src->value->release(c, src);

    if (not src->target->equals(src->value)) {
      apply(c, Move, src->size, src->value, src->target);
    }
  }

  MyOperand* src;
  MyOperand* dst;
};

void
appendSyncForCall(Context* c, MyOperand* src, MyOperand* dst)
{
  new (c->zone->allocate(sizeof(SyncForCallEvent)))
    SyncForCallEvent(c, src, dst);
}

class SyncForJumpEvent: public Event {
 public:
  SyncForJumpEvent(Context* c, MyOperand* src, MyOperand* dst):
    Event(c), src(src), dst(dst)
  { }

  SyncForJumpEvent(Event* next, MyOperand* src, MyOperand* dst):
    Event(next), src(src), dst(dst)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    if (BytesPerWord == 4 and v->size == 8) {
      return register_(c, c->assembler->stackSyncRegister(c, v->index),
                       c->assembler->stackSyncRegister(c, v->index + 4));
    } else {
      return register_(c, c->assembler->stackSyncRegister(c, v->index));
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    src->value->release(c, src);
    src->target->acquire(c, dst);

    if (not src->target->equals(src->value)) {
      apply(c, Move, src->size, src->value, src->target);
    }

    dst->value = src->target;
  }

  MyOperand* src;
  MyOperand* dst;
};

void
appendSyncForJump(Context* c, MyOperand* src, MyOperand* dst)
{
  new (c->zone->allocate(sizeof(SyncForJumpEvent)))
    SyncForJumpEvent(c, src, dst);
}

class CallEvent: public Event {
 public:
  CallEvent(Context* c, MyOperand* address, MyOperand* result,
            unsigned stackOffset, bool alignCall,
            TraceHandler* traceHandler):
    Event(c),
    address(address),
    result(result),
    stackOffset(stackOffset),
    alignCall(alignCall),
    traceHandler(traceHandler)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == address);

    return 0;
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == address);
    address = new_;
  }

  virtual void compile(Context* c) {
    address->value->release(c, address);

    if (result->event) {
      result->value = register_
        (c, c->assembler->returnLow(), c->assembler->returnHigh());
      result->value->acquire(c, result);
    }

    apply(c, LoadAddress, BytesPerWord, register_(c, StackRegister),
          memory(c, c->assembler->base(), stackOffset * BytesPerWord,
                 NoRegister, 0, 0));

    apply(c, Call, address->size, address->value);
  }

  MyOperand* address;
  MyOperand* result;
  unsigned stackOffset;
  bool alignCall;
  TraceHandler* traceHandler;
};

void
appendCall(Context* c, MyOperand* address, MyOperand* result,
           unsigned stackOffset, bool alignCall,
           TraceHandler* traceHandler)
{
  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, result, alignCall, traceHandler);
}

int
freeRegister(Context* c)
{
  for (unsigned i = 0; i < c->assembler->registerCount(); ++i) {
    if ((not c->registers[i].reserved)
        and c->registers[i].operand == 0)
    {
      return i;
    }
  }

  for (unsigned i = 0; i < c->assembler->registerCount(); ++i) {
    if (not c->registers[i].reserved) {
      return i;
    }
  }
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
  MoveEvent(Context* c, OperationType type, MyOperand* src, MyOperand* dst):
    Event(c), type(type), src(src), dst(dst)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == src);

    return v->event->target(c, dst);
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == src);
    src = new_;
    src->target = old->target;
  }

  virtual void compile(Context* c) {
    if (src->target == 0) {
      src->target = freeRegister(c, src->size);
    }

    src->value->release(c, src);
    src->target->acquire(c, dst);

    apply(c, type, src->size, src->value, src->target);

    dst->value = src->target;
  }

  OperationType type;
  MyOperand* src;
  MyOperand* dst;
};

void
appendMove(Context* c, OperationType type, MyOperand* src, MyOperand* dst)
{
  new (c->zone->allocate(sizeof(MoveEvent))) MoveEvent(c, type, src, dst);
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, OperationType type, MyOperand* a, MyOperand* b,
              MyOperand* address):
    Event(c), type(type), a(a), b(b), address(address)
  { }

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
    a->value->release(c, a);
    b->value->release(c, b);
    address->value->release(c, address);

    apply(c, Compare, a->size, a->value, b->value);
    apply(c, type, address->size, address->value);
  }

  OperationType type;
  MyOperand* a;
  MyOperand* b;
  MyOperand* address;
};

void
appendBranch(Context* c, MoveType type, MyOperand* a, MyOperand* b,
             MyOperand* address)
{
  new (c->zone->allocate(sizeof(BranchEvent)))
    BranchEvent(c, type, a, b, address);
}

class JumpEvent: public Event {
 public:
  Jumpvent(Context* c, MyOperand* address):
    Event(c),
    address(address)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == address);

    return 0;
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == address);
    address = new_;
  }

  virtual void compile(Context* c) {
    address->value->release(c, address);

    apply(c, Jump, address->size, address->value);
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
  CombineEvent(Context* c, OperationType type, MyOperand* a, MyOperand* b,
               MyOperand* result):
    Event(c), type(type), a(a), b(b), result(result)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    int aLow, aHigh, bLow, bHigh;
    c->assembler->getTargets(type, v->size, &aLow, &aHigh, &bLow, &bHigh);

    if (v == a) {
      if (aLow == NoRegister) {
        return 0;
      } else {
        return register_(c, aLow, aHigh);
      }
    } else {
      assert(c, v == b);

      if (bLow == NoRegister) {
        return result->event->target(c, result);
      } else {
        return register_(c, bLow, bHigh);
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
    a->value->release(c, a);
    b->value->release(c, b);
    b->value->acquire(c, result);

    if (a->target and not a->target->equals(a->value)) {
      apply(c, Move, a->size, a->value, a->target);
    }
    if (b->target and not b->target->equals(b->value)) {
      apply(c, Move, b->size, b->value, b->target);
    }

    apply(c, type, a->size, a->value, b->value);

    result->value = b->value;
  }

  OperationType type;
  MyOperand* a;
  MyOperand* b;
  MyOperand* result;
};

void
appendCombine(Context* c, MoveType type, MyOperand* a, MyOperand* b,
              MyOperand* result)
{
  new (c->zone->allocate(sizeof(CombineEvent)))
    CombineEvent(c, type, a, b, result);
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, OperationType type, MyOperand* a,
                 MyOperand* result):
    Event(c), type(type), a(a), result(result)
  { }

  virtual Value* target(Context* c, MyOperand* v) {
    assert(c, v == a);
      
    int low, high;
    c->assembler->getTargets(type, v->size, &low, &high);

    if (low == NoRegister) {
      return result->event->target(c, result);
    } else {
      return register_(c, low, high);
    }
  }

  virtual void replace(Context* c, MyOperand* old, MyOperand* new_) {
    assert(c, old == a);
    a = new_;
    a->target = old->target;
  }

  virtual void compile(Context* c) {
    result->value->acquire(c, result);

    apply(c, type, a->size, a->value);

    result->value = b->value;
  }

  OperationType type;
  MyOperand* a;
  MyOperand* result;
};

void
appendTranslate(Context* c, MoveType type, MyOperand* a, MyOperand* result)
{
  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent(c, type, a, result);
}

class Junction {
 public:
  Junction(unsigned logicalIp, MyOperand* stack, Junction* next):
    logicalIp(logicalIp),
    stack(stack),
    next(next)
  { }

  unsigned logicalIp;
  MyOperand* stack;
  Junction* next;
};

void
preserve(Context* c, int reg)
{
  MyOperand* a = c->registers[reg].operand;
  if (a) {
    MemoryValue* dst = memory
      (c, assembler->base(), (a->index + c->stackOffset) * BytesPerWord,
       -1, 0, 0);

    c->assembler->appendRM
      (Move, a->size,
       static_cast<RegisterValue*>(a->value)->low,
       static_cast<RegisterValue*>(a->value)->high,
       dst->base(c), dst->offset, dst->index(c), dst->scale,
       dst->traceHandler);

    a->value = dst;
    c->registers[reg].operand = 0;
  }
}

MyOperand*
operand(Context* c, unsigned size, Value* value)
{
  return new (c->zone->allocate(sizeof(MyOperand))) MyOperand(size, value);
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

void
push(Context* c, Operand* o)
{
  static_cast<MyOperand*>(o)->next = c->stack;
  c->stack = static_cast<MyOperand*>(o);
}

MyOperand*
pop(Context* c)
{
  MyOperand* o = c->stack;
  c->stack = o->next;
  return o;
}

void
syncStack(Context* c, MoveType type)
{
  MyOperand* top = 0;
  MyOperand* new_ = 0;
  for (MyOperand* old = c->state->stack; old; old = old->next) {
    MyOperand* n = operand(c, old->size, 0);
    if (new_) {
      new_->next = n;
    } else {
      top = n;
    }
    new_ = n;
    new_->index = old->index;
      
    if (type == SyncForCall) {
      appendSyncForCall(&c, old, new_);
    } else {
      appendSyncForJump(&c, old, new_);
    }
  }

  c->state->stack = top;
}

void
updateJunctions(Context* c)
{
  for (Junction* j = c->junctions; j; j = j->next) {
    LogicalInstruction* i = c->logicalCode[j->logicalIp];

    if (i->predecessor >= 0) {
      LogicalInstruction* p = c->logicalCode[i->predecessor];

      MyOperand* new_ = 0;
      for (MyOperand* old = i->firstEvent->stack; old; old = old->next) {
        MyOperand* n = operand(c, old->size, 0);
        if (new_) new_->next = n;
        new_ = n;
        new_->index = old->index;

        if (old->event) {
          old->event->replace(o, new_);
        }

        p->lastEvent = p->lastEvent->next = new
          (c->zone->allocate(sizeof(SyncForJumpEvent)))
          SyncForJumpEvent(p->lastEvent->next, old, new_);
      }
    }
  }
}

void
compile(Context* c)
{
  for (Event* e = c->logicalCode[0].firstEvent; e; e = e->next) {
    e->compile(c);
  }
}

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Allocator* allocator, Zone* zone,
             void* indirectCaller):
    c(s, allocator, zone, indirectCaller)
  { }

  virtual void pushState() {
    ::pushState(&c);
  }

  virtual void popState() {
    ::pushState(&c);
  }

  virtual void init(unsigned logicalCodeSize, unsigned localFootprint) {
    c->logicalCodeSize = logicalCodeSize;
    c->logicalCode = static_cast<LogicalInstruction*>
      (c->zone->allocate(sizeof(LogicalInstruction) * logicalCodeSize));
    memset(c->logicalCode, 0, sizeof(LogicalInstruction) * logicalCodeSize);
  }

  virtual void visitLogicalIp(unsigned logicalIp) {
    if ((++ c.logicalCode[logicalIp].visits) == 1) {
      c.junctions = new (c.zone->allocate(sizeof(Junction)))
        Junction(logicalIp, c.logicalCode[logicalIp].stack, c.junctions);
    }
  }

  virtual void startLogicalIp(unsigned logicalIp) {
    if (c.logicalIp >= 0) {
      c->logicalCode[c->logicalIp].lastEvent = c.event;
    }

    c.logicalIp = logicalIp;
  }

  virtual Promise* machineIp(unsigned logicalIp) {
    return new (c.zone->allocate(sizeof(IpPromise))) IpPromise(&c, logicalIp);
  }

  virtual Promise* poolAppend(intptr_t value) {
    return poolAppendPromise(resolved(&c, v));
  }

  virtual Promise* poolAppendPromise(Promise* value) {
    Promise* p = new (c.zone->allocate(sizeof(PoolPromise)))
      PoolPromise(&c, c.constantPool.length());

    Constant* constant = new (c.zone->allocate(sizeof(Constant)))
      Constant(value);

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
    return operand
      (&c, ::constant(&c, new (c.zone->allocate(sizeof(ResolvedPromise)))
                      ResolvedPromise(&c, value)));
  }

  virtual Operand* promiseConstant(Promise* value) {
    return operand
      (&c, ::constant(&c, static_cast<Promise*>(value)));
  }

  virtual Operand* absolute(Promise* address) {
    return operand
      (&c, ::absolute(&c, static_cast<Promise*>(address)));
  }

  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1,
                          TraceHandler* traceHandler = 0)
  {
    return operand
      (&c, memory(&c, static_cast<MyOperand*>(base), displacement,
                  static_cast<MyOperand*>(index), scale, traceHandler));
  }

  virtual Operand* stack() {
    return operand(&c, BytesPerWord, register_(&c, c.machine->stack()));
  }

  virtual Operand* base() {
    return operand(&c, BytesPerWord, register_(&c, c.machine->base()));
  }

  virtual Operand* thread() {
    return operand(&c, BytesPerWord, register_(&c, c.machine->thread()));
  }

  virtual Operand* label() {
    return operand(&c, BytesPerWord, ::constant(&c, 0));
  }

  Promise* machineIp() {
    return c.event->promises = new (c.zone->allocate(sizeof(CodePromise)))
      CodePromise(&c, c.event->promises);
  }

  virtual void mark(Operand* label) {
    static_cast<ConstantValue*>(static_cast<MyOperand*>(label))->value
      = machineIp();
  }

  virtual void push(Operand* value) {
    ::push(&c, value);
  }

  virtual Operand* pop() {
    return ::pop(&c);
  }

  virtual void push(unsigned count) {
    for (unsigned i = 0; i < count; ++i) ::push(&c, 0);
  }

  virtual void pop(unsigned count) {
    for (unsigned i = 0; i < count; ++i) ::pop(&c);
  }

  virtual Operand* call(Operand* address,
                        unsigned resultSize = 4,
                        unsigned argumentFootprint = 0,
                        bool alignCall = false,
                        TraceHandler* traceHandler = 0)
  {
    for (unsigned i = 0; i < argumentFootprint; ++i) {
      MyOperand* a = ::pop(&c);
      appendArgument(&c, a, i);
      if (BytesPerWord == 4 and a->size == 8) {
        ++ i;
      }
    }

    syncStack(&c, SyncForCall);

    unsigned stackOffset = c.stackOffset + c.stack->index
      + (BytesPerWord == 8 ?
         (argumentFootprint > GprParameterCount ?
          argumentFootprint - GprParameterCount : 0) : argumentFootprint);

    MyOperand* result = operand(&c, resultSize, 0);
    appendCall(&c, static_cast<MyOperand*>(address), result, stackOffset,
               alignCall, traceHandler);
    return result;
  }

  virtual void return_(Operand* value) {
    appendReturn(&c, static_cast<MyOperand*>(value));
  }

  virtual void store1(Operand* src, Operand* dst) {
    appendMove(&c, Store1, static_cast<MyOperand*>(src),
               static_cast<MyOperand*>(dst));
  }

  virtual void store2(Operand* src, Operand* dst) {
    appendMove(&c, Store2, static_cast<MyOperand*>(src),
               static_cast<MyOperand*>(dst));
  }

  virtual void store4(Operand* src, Operand* dst) {
    appendMove(&c, Store4, static_cast<MyOperand*>(src),
               static_cast<MyOperand*>(dst));
  }

  virtual void store8(Operand* src, Operand* dst) {
    appendMove(&c, Store8, static_cast<MyOperand*>(src),
               static_cast<MyOperand*>(dst));
  }

  virtual Operand* load1(Operand* src) {
    MyOperand* dst = operand(&c, 4);
    appendMove(&c, Load1, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* load2(Operand* src) {
    MyOperand* dst = operand(&c, 4);
    appendMove(&c, Load2, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* load2z(Operand* src) {
    MyOperand* dst = operand(&c, 4);
    appendMove(&c, Load2z, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* load4(Operand* src) {
    MyOperand* dst = operand(&c, 4);
    appendMove(&c, Load4, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual Operand* load8(Operand* src) {
    MyOperand* dst = operand(&c, 8);
    appendMove(&c, Load8, static_cast<MyOperand*>(src), dst);
    return dst;
  }

  virtual void jl(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfLess, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void jg(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfGreater, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void jle(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfLessOrEqual, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void jge(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfGreaterOrEqual, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void je(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfEqual, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void jne(Operand* a, Operand* b, Operand* address) {
    syncStack(&c, SyncForJump);

    appendBranch(&c, JumpIfNotEqual, static_cast<MyOperand*>(a),
                 static_cast<MyOperand*>(b), static_cast<MyOperand*>(address));
  }

  virtual void jmp(Operand* address) {
    syncStack(&c, SyncForJump);

    appendJump(&c, static_cast<MyOperand*>(address));
  }

  virtual Operand* add(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Add, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* sub(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Subtract, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* mul(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Multiply, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* div(Operand* a, Operand* b)  {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Divide, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* rem(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Remainder, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* shl(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, ShiftLeft, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* shr(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, ShiftRight, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* ushr(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, UnsignedShiftRight, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* and_(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, And, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* or_(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Or, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* xor_(Operand* a, Operand* b) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendCombine(&c, Xor, static_cast<MyOperand*>(a),
                  static_cast<MyOperand*>(b), result);
    return result;
  }

  virtual Operand* neg(Operand* a) {
    MyOperand* result = operand(&c, static_cast<MyOperand*>(a)->size);
    appendTranslate(&c, Negate, static_cast<MyOperand*>(a), result);
    return result;
  }

  virtual unsigned compile() {
    updateJunctions(&c);
    return ::compile(&c);
  }

  virtual unsigned poolSize() {
    return c.constantCount;
  }

  virtual void writeTo(uint8_t* dst) {
    c.assembler->writeTo(dst);

    for (Constant* n = c.firstConstant; n; n = n->next) {
      *reinterpret_cast<intptr_t*>(dst + c.code.length() + i)
        = n->promise->value(this);
    }
  }

  virtual void dispose() {
    c.dispose();
  }

  Context c;
};

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, Zone* zone)
{
  return new (zone->allocate(sizeof(MyCompiler)))
    MyCompiler(system, zone);
}

} // namespace vm
