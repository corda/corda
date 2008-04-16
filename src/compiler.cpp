/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "compiler.h"
#include "assembler.h"

using namespace vm;

namespace {

class Context;
class Value;

void NO_RETURN abort(Context*);

// scratch

class Site {
 public:
  Site(): next(0) { }
  
  virtual ~Site() { }

  virtual unsigned copyCost(Context*, Site*) = 0;

  virtual void copyTo(Context*, unsigned, Site*) = 0;
  
  virtual void acquire(Context*, unsigned, Value*, Site*) { }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand) = 0;

  Site* next;
};

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual unsigned copyCost(Context*, Site*) {
    return 1;
  }

  virtual void copyTo(Context* c, unsigned size, Site* dst) {
    apply(c, Move, size, this, dst);
  }

  virtual OperandType type(Context*) {
    return Constant;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context*) {
    return &value;
  }

  Assembler::Constant value;
};

ConstantSite*
constantSite(Context* c, Promise* value)
{
  return new (c->zone->allocate(sizeof(ConstantSite))) ConstantSite(value);
}

ResolvedPromise*
resolved(Context* c, int64_t value)
{
  return new (c->zone->allocate(sizeof(ResolvedPromise)))
    ResolvedPromise(value);
}

ConstantSite*
constantSite(Context* c, int64_t value)
{
  return constantSite(c, resolved(c, value));
}

class AddressSite: public Site {
 public:
  AddressSite(Promise* address): address(address) { }

  virtual unsigned copyCost(Context*, Site*) {
    return 3;
  }

  virtual void copyTo(Context* c, unsigned size, Site* dst) {
    apply(c, Move, size, this, dst);
  }

  virtual OperandType type(Context*) {
    return Address;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context*) {
    return &address;
  }

  Assembler::Address address;
};

AddressSite*
addressSite(Context* c, Promise* address)
{
  return new (c->zone->allocate(sizeof(AddressSite))) AddressSite(address);
}

void
acquire(Context* c, int r, unsigned newSize, Value* newValue, Site* newSite)
{
  Value* oldValue = c->registers[r].value;
  if (oldValue) {
    for (Site** p = &(oldValue->sites); *p;) {
      if (c->registers[r].site == *p) {
        site = *p;
        *p = (*p)->next;
        break;
      } else {
        p = &((*p)->next);
      }
    }

    if (old->sites == 0 and old->reads) {
      apply(c, Push, c->registers[r].size, c->registers[r].site);
      old->sites = ???;
    }
  }

  c->registers[r].size = newSize;
  c->registers[r].value = newValue;
  c->registers[r].site = newSite;
}

class RegisterSite: public Site {
 public:
  RegisterSite(int low, int high): register_(low, high) { }

  virtual unsigned copyCost(Context* c, Site* s) {
    if (s and
        (this == s or
         (s->type(c) == Register
          and static_cast<RegisterSite*>(s)->register_.low
          == register_.low
          and static_cast<RegisterSite*>(s)->register_.high
          == register_.high)))
    {
      return 0;
    } else {
      return 2;
    }
  }

  virtual void copyTo(Context* c, unsigned size, Site* dst) {
    apply(c, Move, size, this, dst);
  }

  virtual void acquire(Context* c, unsigned size, Value* v, Site* s) {
    ::acquire(c, register_.low, size, v, s);
    if (register_.high >= 0) ::acquire(c, register_.high, size, v, s);
  }

  virtual OperandType type(Context*) {
    return Register;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context*) {
    return &register_;
  }

  Assembler::Register register_;
};

RegisterSite*
registerSite(Context* c, int low, int high = NoRegister)
{
  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(low, high);
}

class MemorySite: public Site {
 public:
  RegisterSite(int base, int offset, int index, unsigned scale):
    value(base, offset, index, scale)
  { }

  virtual unsigned copyCost(Context* c, Site* s) {
    if (s and
        (this == s or
         (o->type(c) == Memory
          and static_cast<MemorySite*>(o)->value.base == value.base
          and static_cast<MemorySite*>(o)->value.offset == value.offset
          and static_cast<MemorySite*>(o)->value.index == value.index
          and static_cast<MemorySite*>(o)->value.scale == value.scale)))
    {
      return 0;
    } else {
      return 4;
    }
  }

  virtual void copyTo(Context* c, unsigned size, Site* dst) {
    apply(c, Move, size, this, dst);
  }

  virtual OperandType type(Context*) {
    return Memory;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context*) {
    return &value;
  }

  Assembler::Memory value;
};

MemorySite*
memorySite(Context* c, int base, int offset, int index, unsigned scale)
{
  return new (c->zone->allocate(sizeof(MemorySite)))
    MemorySite(base, offset, index, scale);
}

class Read {
 public:
  Read(unsigned size, Value* value, Site* target):
    size(size), value(value), target(target), next(0)
  { }
  
  unsigned size;
  Value* value;
  Site* target;
  Read* next;
};

class Write {
 public:
  Write(unsigned size, Value* value):
    size(size), value(value), next(0)
  { }
  
  unsigned size;
  Value* value;
  Write* next;
};

class Value: public Compiler::Operand {
 public:
  Value(Site* site):
    reads(0), sites(site), source(0), target(0)
  { }
  
  Read* reads;
  Site* sites;
  Site* source;
  Site* target;
};

class Event {
 public:
  Event(Context* c): next(0), stack(c->state->stack), promises(0) {
    assert(c, c->logicalIp >= 0);

    if (c->event) {
      c->event->next = this;
    }

    if (c->logicalCode[c->logicalIp].firstEvent == 0) {
      c->logicalCode[c->logicalIp].firstEvent = this;
    }

    c->event = this;
  }

  virtual ~Event() { }

  virtual void compile(Context* c) = 0;

  Event* next;
  Stack* stack;
  CodePromise* promises;
};

class Stack {
 public:
  Stack(Value* value, unsigned size, unsigned index, Stack* next):
    value(value), size(size), index(index), next(next)
  { }

  Value* value;
  unsigned size;
  unsigned index;
  Stack* next;
};

class CallEvent: public Event {
 public:
  CallEvent(Context* c, Value* address, void* indirection, unsigned flags,
            TraceHandler* traceHandler, Value* result, unsigned resultSize,
            unsigned argumentCount):
    Event(c),
    address(address),
    indirection(indirection),
    flags(flags),
    traceHandler(traceHandler),
    result(result)
  {
    addRead(c, address, BytesPerWord,
            (indirection ? registerSite(c, c->assembler->returnLow()) : 0));

    unsigned index = 0;
    Stack* s = stack;
    for (unsigned i = 0; i < argumentCount; ++i) {
      addRead(c, s->value, s->size * BytesPerWord,
              index < c->assembler->argumentRegisterCount() ?
              registerSite(c, c->assembler->argumentRegister(index)) :
              stackSite(c, s));
      index += s->size;
      s = s->next;
    }

    if (result) {
      addWrite(c, result, resultSize);
    }
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CallEvent.compile\n");
    
    UnaryOperation type = ((flags & Compiler::Aligned) ? AlignedCall : Call);
    if (indirection) {
      apply(c, type, BytesPerWord,
            constantSite(c, reinterpret_cast<intptr_t>(indirection)));
    } else {
      apply(c, type, BytesPerWord, address->source);
    }

    if (traceHandler) {
      traceHandler->handleTrace
        (new (c->zone->allocate(sizeof(CodePromise)))
         CodePromise(c, c->assembler->length()));
    }
  }

  Value* address;
  void* indirection;
  unsigned flags;
  TraceHandler* traceHandler;
  Value* result;
};

void
appendCall(Context* c, Value* address, void* indirection, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned argumentCount)
{
  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, indirection, flags, traceHandler, result,
              argumentCount);
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, unsigned size, Value* value):
    Event(c), value(value)
  {
    if (value) {
      addRead(c, value, size, registerSite
              (c, c->assembler->returnLow(),
               size > BytesPerWord ?
               c->assembler->returnHigh() : NoRegister));
    }
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "ReturnEvent.compile\n");

    Assembler::Register base(c->assembler->base());
    Assembler::Register stack(c->assembler->stack());

    c->assembler->apply(Move, BytesPerWord, Register, &base, Register, &stack);
    c->assembler->apply(Pop, BytesPerWord, Register, &base);
    c->assembler->apply(Return);
  }

  Value* value;
};

void
appendReturn(Context* c, unsigned size, MyOperand* value)
{
  new (c->zone->allocate(sizeof(ReturnEvent))) ReturnEvent(c, size, value);
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned size, Value* src,
            Value* dst):
    Event(c), type(type), size(size), src(src), dst(dst)
  {
    addRead(c, src, size, 0);
    addWrite(c, dst, size);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MoveEvent.compile\n");

    apply(c, type, size, src->source, dst->target);
  }

  BinaryOperation type;
  unsigned size;
  Value* src;
  Value* dst;
};

void
appendMove(Context* c, BinaryOperation type, unsigned size, Value* src,
           Value* dst)
{
  new (c->zone->allocate(sizeof(MoveEvent)))
    MoveEvent(c, type, size, src, dst);
}

class CompareEvent: public Event {
 public:
  CompareEvent(Context* c, unsigned size, Value* first, Value* second):
    Event(c), size(size), first(first), second(second)
  {
    addRead(c, first, size, 0);
    addRead(c, second, size, 0);
  }


  virtual void compile(Context* c) {
    fprintf(stderr, "CompareEvent.compile\n");

    apply(c, Compare, size, first->source, second->source);
  }

  unsigned size;
  Value* first;
  Value* second;
};

void
appendCompare(Context* c, unsigned size, Value* first, Value* second)
{
  new (c->zone->allocate(sizeof(CompareEvent)))
    CompareEvent(c, size, first, second);
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, UnaryOperation type, Value* address):
    Event(c), type(type), address(address)
  {
    addRead(c, address, BytesPerWord, 0);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "BranchEvent.compile\n");

    apply(c, type, BytesPerWord, address->source);
  }

  UnaryOperation type;
  Value* address;
};

void
appendBranch(Context* c, UnaryOperation type, Value* address)
{
  new (c->zone->allocate(sizeof(BranchEvent))) BranchEvent(c, type, address);
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, BinaryOperation type, unsigned size, Value* first,
               Value* second, Value* result):
    Event(c), type(type), size(size), first(first), second(second),
    result(result)
  {
    Assembler::Register r1(NoRegister);
    Assembler::Register r2(NoRegister);
    c->assembler->getTargets(type, size, &r1, &r2);

    addRead(c, first, size,
            r1.low == NoRegister ? 0 : registerSite(c, r1.low, r1.high));
    addRead(c, second, size,
            r2.low == NoRegister ?
            valueSite(c, result) : registerSite(c, r2.low, r2.high));
    addWrite(c, result, size);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CombineEvent.compile\n");

    apply(c, type, size, first->source, second->source);
  }

  BinaryOperation type;
  unsigned size;
  Value* first;
  Value* second;
  MyOperand* result;
};

void
appendCombine(Context* c, BinaryOperation type, unsigned size, Value* first,
              Value* second, Value* result)
{
  new (c->zone->allocate(sizeof(CombineEvent)))
    CombineEvent(c, type, size, first, second, result);
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, UnaryOperation type, unsigned size, Value* value,
                 Value* result):
    Event(c), type(type), size(size), value(value), result(result)
  {
    addRead(c, value, size, valueSite(c, result));
    addWrite(c, result, size);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "TranslateEvent.compile\n");

    apply(c, type, size, value->source);
  }

  UnaryOperation type;
  unsigned size;
  Value* value;
  Value* result;
};

void
appendTranslate(Context* c, UnaryOperation type, unsigned size, Value* value,
                Value* result)
{
  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent(c, type, size, value, result);
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, Value* index, Value* result):
    Event(c), base(base), index(index), result(result)
  {
    addRead(c, base, BytesPerWord, anyRegisterSite(c));
    if (index) addRead(c, index, BytesPerWord, anyRegisterSite(c));
    addWrite(c, BytesPerWord, size);
  }

  virtual void compile(Context*) {
    fprintf(stderr, "MemoryEvent.compile\n");
  }

  Value* base;
  Value* index;
  Value* result;
};

void
appendMemory(Context* c, Value* base, Value* index, Value* result)
{
  new (c->zone->allocate(sizeof(MemoryEvent)))
    MemoryEvent(c, base, index, result);
}

void
addSite(Context* c, int size, Value* v, Site* s)
{
  s->acquire(c, size, v, s);
  s->next = v->sites;
  v->sites = s;
}

void
compile(Context* c)
{
  Assembler* a = c->assembler;

  Assembler::Register base(a->base());
  Assembler::Register stack(a->stack());
  a->apply(Push, BytesPerWord, Register, &base);
  a->apply(Move, BytesPerWord, Register, &stack, Register, &base);

  if (c->stackOffset) {
    Assembler::Constant offset(resolved(c, c->stackOffset * BytesPerWord));
    a->apply(Subtract, BytesPerWord, Constant, &offset, Register, &stack);
  }

  for (Event* e = c->firstEvent; e; e = e->next) {
    LogicalInstruction* li = c->logicalCode + e->logicalIp;
    li->machineOffset = a->length();

    for (Read* r = e->reads; r; r = r->next) {
      Site* site = 0;
      unsigned copyCost = Site::MaxCopyCost;
      for (Site* s = r->value->sites; s; s = s->next) {
        unsigned c = s->copyCost(c, r->target);
        if (c < copyCost) {
          site = s;
          copyCost = c;
        }
      }

      if (r->target) {
        if (copyCost) {
          addSite(c, r->size, r->value, r->target);

          site->copyTo(c, r->size, r->target);
        }

        r->value->source = r->target;
      } else {
        r->value->source = site;
      }

      r->value->reads = r->value->reads->next;
    }

    for (Write* w = e->writes; w; w = w->next) {
      if (w->value->reads and w->value->reads->target) {
        w->value->target = w->value->reads->target;
      } else {
        w->value->target = freeRegister(c, w->size);
      }

      addSite(c, w->size, w->value, w->value->target);
    }

    e->compile(c);

    for (CodePromise* p = e->promises; p; p = p->next) {
      p->offset = a->length();
    }
  }
}

// end scratch

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
        (c->machineCode + pad(c->assembler->length()) + (key * BytesPerWord));
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
freeRegister(Context* c, unsigned size, bool allowAcquired);

class ConstantValue: public Value {
 public:
  ConstantValue(Promise* value): value(value) { }

  virtual OperandType type(Context*) { return Constant; }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    *type = Constant;
    *operand = &value;
  }

  virtual int64_t constantValue(Context*) {
    return value.value->value();
  }

  Assembler::Constant value;
};

ConstantValue*
constant(Context* c, Promise* value)
{
  return new (c->zone->allocate(sizeof(ConstantValue))) ConstantValue(value);
}

ResolvedPromise*
resolved(Context* c, int64_t value)
{
  return new (c->zone->allocate(sizeof(ResolvedPromise)))
    ResolvedPromise(value);
}

ConstantValue*
constant(Context* c, int64_t value)
{
  return constant(c, resolved(c, value));
}

class AddressValue: public Value {
 public:
  AddressValue(Promise* address): address(address) { }

  virtual OperandType type(Context*) { return Address; }

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

void preserve(Context*, Stack*, int, MyOperand*);

class RegisterValue: public Value {
 public:
  RegisterValue(int low, int high): register_(low, high) { }

  virtual OperandType type(Context*) { return Register; }

  virtual bool equals(Context* c, Value* o) {
    return this == o or
      (o->type(c) == Register
       and static_cast<RegisterValue*>(o)->register_.low == register_.low
       and static_cast<RegisterValue*>(o)->register_.high == register_.high);
  }

  virtual void preserve(Context* c, Stack* s, MyOperand* a) {
    ::preserve(c, s, register_.low, a);
    if (register_.high >= 0) ::preserve(c, s, register_.high, a);
  }

  virtual void acquire(Context* c, Stack* s, MyOperand* a) {
    if (a != c->registers[register_.low].operand) {
      fprintf(stderr, "%p acquire %d\n", a, register_.low);

      preserve(c, s, a);
      c->registers[register_.low].operand = a;
      if (register_.high >= 0) {
        c->registers[register_.high].operand = a;
      }
    }
  }

  virtual void release(Context* c, MyOperand* a) {
    if (a == c->registers[register_.low].operand) {
      fprintf(stderr, "%p release %d\n", a, register_.low);

      c->registers[register_.low].operand = 0;
      if (register_.high >= 0) c->registers[register_.high].operand = 0;
    }
  }

  virtual int registerValue(Context*) {
    return register_.low;
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
  MemoryValue(int base, int offset, int index, unsigned scale):
    value(base, offset, index, scale)
  { }

  virtual OperandType type(Context*) { return Memory; }

  virtual bool equals(Context* c, Value* o) {
    return this == o or
      (o->type(c) == Memory
       and static_cast<MemoryValue*>(o)->value.base == value.base
       and static_cast<MemoryValue*>(o)->value.offset == value.offset
       and static_cast<MemoryValue*>(o)->value.index == value.index
       and static_cast<MemoryValue*>(o)->value.scale == value.scale);
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

class AbstractMemoryValue: public MemoryValue {
 public:
  AbstractMemoryValue(MyOperand* base, int offset, MyOperand* index,
                      unsigned scale):
    MemoryValue(NoRegister, offset, NoRegister, scale),
    base_(base), index_(index)
  { }

  virtual void preserve(Context* c, Stack* s, MyOperand*) {
    base_->value->preserve(c, s, base_);
    if (index_) {
      index_->value->preserve(c, s, index_);
    }
  }

  virtual void release(Context* c, MyOperand*) {
    base_->value->release(c, base_);
    if (index_) {
      index_->value->release(c, index_);
    }
  }

  virtual bool ready(Context* c) {
    return base_->value->registerValue(c) != NoRegister
      and (index_ == 0 or index_->value->registerValue(c) != NoRegister);
  }

  virtual int base(Context* c) {
    int r = base_->value->registerValue(c);
    assert(c, r != NoRegister);
    return r;
  }

  virtual int index(Context* c) {
    if (index_) {
      int r = index_->value->registerValue(c);
      assert(c, r != NoRegister);
      return r;
    } else {
      return NoRegister;
    }
  }

  MyOperand* base_;
  MyOperand* index_;
};

AbstractMemoryValue*
memory(Context* c, MyOperand* base, int offset, MyOperand* index,
       unsigned scale)
{
  return new (c->zone->allocate(sizeof(AbstractMemoryValue)))
    AbstractMemoryValue(base, offset, index, scale);
}

class StackValue: public Value {
 public:
  StackValue(Context* c, Stack* stack):
    stack(stack),
    value
    (c->assembler->base(),
     - (c->stackOffset + stack->index + 1) * BytesPerWord,
     NoRegister, 0, 0)
  { }

  virtual OperandType type(Context*) { return Memory; }

  virtual void asAssemblerOperand(Context*,
                                  OperandType* type,
                                  Assembler::Operand** operand)
  {
    *type = Memory;
    *operand = &value;
  }

  Stack* stack;
  Assembler::Memory value;
};

StackValue*
stackValue(Context* c, Stack* stack)
{
  return new (c->zone->allocate(sizeof(StackValue))) StackValue(c, stack);
}

class Event {
 public:
  Event(Context* c): next(0), stack(c->state->stack), promises(0) {
    assert(c, c->logicalIp >= 0);

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
  virtual unsigned operandSize(Context* c) = 0;
  virtual void compile(Context* c) = 0;
  virtual bool isCritical(Context*) { return false; }

  Event* next;
  Stack* stack;
  CodePromise* promises;
};

class NullEvent: public Event {
 public:
  NullEvent(Context* c):
    Event(c)
  { }

  virtual Value* target(Context*, MyOperand*) {
    return 0;
  }

  virtual unsigned operandSize(Context*) {
    return 0;
  }

  virtual void compile(Context*) {
    // ignore
  }
};

void
setEvent(Context* c, MyOperand* a, Event* e)
{
  if (a->event) {
    a->event = new (c->zone->allocate(sizeof(NullEvent))) NullEvent(c);
  } else{
    a->event = e;
  }
}

class ArgumentEvent: public Event {
 public:
  ArgumentEvent(Context* c, unsigned size, MyOperand* a, unsigned index):
    Event(c), size(size), a(a), index(index)
  {
    setEvent(c, a, this);
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == a);

    if (index < c->assembler->argumentRegisterCount()) {
      return register_(c, c->assembler->argumentRegister(index));
    } else {
      return 0;
    }
  }

  virtual unsigned operandSize(Context*) {
    return size;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "ArgumentEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);

    if (a->target == 0) {
      apply(c, Push, size, a->value);
      a->value = 0;
    } else {
      if (not a->target->equals(c, a->value)) {
        a->target->preserve(c, stack, a);
        apply(c, Move, size, a->value, a->target);
      }
      a->value->release(c, a);
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
      setEvent(c, a, this);
    }
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == a);

    return register_(c, c->assembler->returnLow(), c->assembler->returnHigh());
  }

  virtual unsigned operandSize(Context*) {
    return size;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "ReturnEvent.compile\n");

    if (a) {
      if (a->target == 0) a->target = target(c, a);

      if (not a->target->equals(c, a->value)) {
        apply(c, Move, size, a->value, a->target);
      }
      a->value->release(c, a);
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

void
syncStack(Context* c, Stack* start, unsigned count)
{
  Stack* segment[count];
  unsigned index = count;
  for (Stack* s = start; s and index; s = s->next) {
    segment[--index] = s;
  }

  for (unsigned i = 0; i < count; ++i) {
    Stack* s = segment[i];
    if (s->operand->value) {
      apply(c, Push, s->size * BytesPerWord, s->operand->value);
      s->operand->value->release(c, s->operand);
    } else {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset(resolved(c, s->size * BytesPerWord));
      c->assembler->apply
        (Subtract, BytesPerWord, Constant, &offset, Register, &stack);
    }
    s->operand->pushed = true;
    s->operand->value = stackValue(c, s);
  }
}

void
syncStack(Context* c, Stack* start)
{
  unsigned count = 0;
  for (Stack* s = start; s and (not s->operand->pushed); s = s->next) {
    ++ count;
  }

  syncStack(c, start, count);
}

class PushEvent: public Event {
 public:
  PushEvent(Context* c):
    Event(c), active(false)
  {
    assert(c, stack->operand->push == 0);
    stack->operand->push = this;
  }

  virtual Value* target(Context*, MyOperand*) {
    return 0;
  }

  virtual unsigned operandSize(Context*) {
    return 0;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "PushEvent.compile\n");

    if (active) {
      fprintf(stderr, "PushEvent.compile: active\n");
      syncStack(c, stack);
    }
  }

  void markStack(Context*) {
    active = true;
  }

  bool active;
};

void
appendPush(Context* c)
{
  new (c->zone->allocate(sizeof(PushEvent))) PushEvent(c);
}

class CallEvent: public Event {
 public:
  CallEvent(Context* c, MyOperand* address, void* indirection, unsigned flags,
            TraceHandler* traceHandler, MyOperand* result):
    Event(c),
    address(address),
    indirection(indirection),
    flags(flags),
    traceHandler(traceHandler),
    result(result)
  {
    setEvent(c, address, this);
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == address);

    if (indirection) {
      return register_(c, c->assembler->returnLow(), NoRegister);
    } else {
      return 0;
    }
  }

  virtual unsigned operandSize(Context*) {
    return BytesPerWord;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CallEvent.compile\n");

    if (indirection and address->target == 0) {
      address->target = target(c, address);
    }

    UnaryOperation type = ((flags & Compiler::Aligned) ? AlignedCall : Call);

    if (indirection) {
      if (not address->target->equals(c, address->value)) {
        apply(c, Move, BytesPerWord, address->value, address->target);
      }
      apply(c, type, BytesPerWord,
            constant(c, reinterpret_cast<intptr_t>(indirection)));
    } else {
      apply(c, type, BytesPerWord, address->value);
    }

    address->value->release(c, address);

    if (result->event or (result->push and result->push->active)) {
      result->value = register_
        (c, c->assembler->returnLow(), c->assembler->returnHigh());
      result->value->acquire(c, stack, result);
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
};

void
appendCall(Context* c, MyOperand* address, void* indirection, unsigned flags,
           TraceHandler* traceHandler, MyOperand* result)
{
  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, indirection, flags, traceHandler, result);
}

int
freeRegisterExcept(Context* c, int except, bool allowAcquired)
{
  for (int i = c->assembler->registerCount(); i >= 0; --i) {
    if (i != except
        and (not c->registers[i].reserved)
        and c->registers[i].operand == 0)
    {
      return i;
    }
  }

  if (allowAcquired) {
    for (int i = c->assembler->registerCount(); i >= 0; --i) {
      if (i != except
          and (not c->registers[i].reserved))
      {
        return i;
      }
    }
  }

  abort(c);
}

inline int
freeRegister(Context* c, bool allowAcquired)
{
  return freeRegisterExcept(c, NoRegister, allowAcquired);
}

RegisterValue*
freeRegister(Context* c, unsigned size, bool allowAcquired)
{
  if (BytesPerWord == 4 and size == 8) {
    int low = freeRegister(c, allowAcquired);
    return register_(c, low, freeRegisterExcept(c, low, allowAcquired));
  } else {
    return register_(c, freeRegister(c, allowAcquired));
  }
}

class PopEvent: public Event {
 public:
  PopEvent(Context* c, unsigned count, bool ignore):
    Event(c), count(count), ignore(ignore)
  { }

  virtual Value* target(Context* c, MyOperand*) {
    abort(c);
  }

  virtual unsigned operandSize(Context* c) {
    abort(c);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "PopEvent.compile\n");

    Stack* s = stack;
    unsigned ignored = 0;
    for (unsigned i = count; i;) {
      MyOperand* dst = s->operand;
      if (dst->pushed) {
        if (dst->event and (not ignore)) {
          assert(c, ignored == 0);

          Value* target = 0;

          if (dst->event->operandSize(c) == BytesPerWord) {
            target = dst->event->target(c, dst);
          }
          if (target == 0 or (not target->ready(c))) {
            target = freeRegister(c, BytesPerWord * s->size, false);
          }

          target->acquire(c, 0, dst);

          apply(c, Pop, BytesPerWord * s->size, target);

          dst->value = target;
        } else {
          ignored += s->size;
        }
      }

      i -= s->size;
      s = s->next;
    }

    if (ignored) {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset(resolved(c, ignored * BytesPerWord));
      c->assembler->apply
        (Add, BytesPerWord, Constant, &offset, Register, &stack);
    }
  }

  unsigned count;
  bool ignore;
};

void
appendPop(Context* c, unsigned count, bool ignore)
{
  new (c->zone->allocate(sizeof(PopEvent))) PopEvent(c, count, ignore);
}

bool
safeToSkipMove(Context* c, MyOperand* a, Event* e)
{
  for (; a->push and a->push != e; e = e->next) {
    if (e->isCritical(c)) return false;
  }
  return true;
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned size, MyOperand* src,
            MyOperand* dst):
    Event(c), type(type), size(size), src(src), dst(dst)
  {
    setEvent(c, src, this);
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == src);

    if (dst->value) {
      return dst->value;
    } else if (dst->event) {
      return dst->event->target(c, dst);
    }

    return 0;
  }

  virtual unsigned operandSize(Context*) {
    return size;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MoveEvent.compile\n");

    if (src->target == 0) src->target = target(c, src);

    if (src->target == 0) {
      if (type == Move
          and size == BytesPerWord
          and safeToSkipMove(c, dst, next))
      {
        dst->value = src->value;
        return;
      }
    } else if (type == Move
               and size == BytesPerWord
               and src->target->type(c) == Register
               and src->target->equals(c, src->value))
    {
      dst->value = src->value;
      return;
    }

    src->value->release(c, src);

    if (src->target == 0 or (not src->target->ready(c))) {
      src->target = freeRegister(c, size, false);
    }

    src->target->acquire(c, stack, dst);

    apply(c, type, size, src->value, src->target);

    dst->value = src->target;
  }

  virtual bool isCritical(Context* c) {
    if (src->target == 0) src->target = target(c, src);

    return src->target != 0;
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

  virtual unsigned operandSize(Context* c) {
    abort(c);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "DupEvent.compile\n");

    Value* value = src->value;
    assert(c, dst->value == 0);

    Value* target = 0;

    if (safeToSkipMove(c, dst, next)) {
      dst->value = src->value;
      return;
    }

    if (dst->event) {
      target = dst->event->target(c, dst);
    }
    if (target == 0 or (not target->ready(c))) {
      target = freeRegister(c, size, true);
    }

    target->acquire(c, stack, dst);

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
    setEvent(c, a, this);
    setEvent(c, b, this);
  }

  virtual Value* target(Context* c UNUSED, MyOperand* v UNUSED) {
    assert(c, v == a or v == b);

    return 0;
  }

  virtual unsigned operandSize(Context*) {
    return size;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CompareEvent.compile\n");

    apply(c, Compare, size, a->value, b->value);

    a->value->release(c, a);
    b->value->release(c, b);
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
    setEvent(c, address, this);
  }

  virtual Value* target(Context* c UNUSED, MyOperand* v UNUSED) {
    assert(c, v == address);

    return 0;
  }

  virtual unsigned operandSize(Context*) {
    return BytesPerWord;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "BranchEvent.compile\n");

    apply(c, type, BytesPerWord, address->value);

    address->value->release(c, address);
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
    setEvent(c, address, this);
  }

  virtual unsigned operandSize(Context*) {
    return BytesPerWord;
  }

  virtual Value* target(Context* c UNUSED, MyOperand* v UNUSED) {
    assert(c, v == address);

    return 0;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "JumpEvent.compile\n");

    apply(c, Jump, BytesPerWord, address->value);

    address->value->release(c, address);
  }

  MyOperand* address;
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
    setEvent(c, a, this);
    setEvent(c, b, this);
  }

  virtual unsigned operandSize(Context*) {
    return size;
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
          if (v and v->type(c) == Register) {
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

  virtual void compile(Context* c) {
    fprintf(stderr, "CombineEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);
    if (b->target == 0) b->target = target(c, b);

    if (b->target == 0 or (not b->target->ready(c))) {
      b->target = freeRegister(c, BytesPerWord, true);
    }

    if (a->target and not a->target->equals(c, a->value)) {
      apply(c, Move, size, a->value, a->target);
      a->value->release(c, a);
      a->value = a->target;
      a->value->acquire(c, stack, a);
    }

    if (b->target and not b->target->equals(c, b->value)) {
      apply(c, Move, size, b->value, b->target);
      b->value->release(c, b);
      b->value = b->target;
      b->value->acquire(c, stack, b);
    }

    apply(c, type, size, a->value, b->value);

    a->value->release(c, a);
    b->value->release(c, b);
    b->value->acquire(c, stack, result);

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
    setEvent(c, a, this);
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == a);
      
    Assembler::Register r(NoRegister);
    c->assembler->getTargets(type, size, &r);

    if (r.low == NoRegister) {
      return result->event->target(c, result);
    } else {
      return register_(c, r.low, r.high);
    }
  }

  virtual unsigned operandSize(Context*) {
    return size;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "TranslateEvent.compile\n");

    if (a->target == 0) a->target = target(c, a);

    if (not a->target->ready(c)) {
      a->target = a->value;
    }

    result->value->acquire(c, stack, result);

    if (a->target and not a->target->equals(c, a->value)) {
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

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, MyOperand* base, MyOperand* index,
              MyOperand* result):
    Event(c), base(base), index(index), result(result)
  {
    setEvent(c, base, this);
    if (index) setEvent(c, index, this);
  }

  virtual unsigned operandSize(Context*) {
    return BytesPerWord;
  }

  virtual Value* target(Context* c, MyOperand* v UNUSED) {
    assert(c, v == base or v == index);
    return 0;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MemoryEvent.compile\n");

    if (base->value->type(c) != Register) {
      base->target = freeRegister(c, BytesPerWord, true);
      apply(c, Move, BytesPerWord, base->value, base->target);  
      base->value->release(c, base);
      base->value = base->target;
    }

    if (index and index->value->type(c) != Register) {
      index->target = freeRegister(c, BytesPerWord, true);
      apply(c, Move, BytesPerWord, index->value, index->target);  
      index->value->release(c, index);
      index->value = index->target;
    }
  }

  MyOperand* base;
  MyOperand* index;
  MyOperand* result;
};

void
appendMemory(Context* c, MyOperand* a, MyOperand* b, MyOperand* result)
{
  new (c->zone->allocate(sizeof(MemoryEvent))) MemoryEvent(c, a, b, result);
}

void
preserve(Context* c, Stack* stack, int reg, MyOperand* a)
{
  MyOperand* b = c->registers[reg].operand;
  if (b and a != b) {
    fprintf(stderr, "%p preserve %d for %p\n", a, reg, b);

    unsigned count = 0;
    Stack* start = 0;
    for (Stack* s = stack; s and (not s->operand->pushed); s = s->next) {
      if (s->operand == b) {
        start = s;
      }
      if (start) {
        ++ count;
      }
    }

    assert(c, start);

    syncStack(c, start, count);
  }
}

MyOperand*
operand(Context* c, Value* value = 0)
{
  return new (c->zone->allocate(sizeof(MyOperand))) MyOperand(value);
}

unsigned
count(Stack* s)
{
  unsigned c = 0;
  while (s) {
    ++ c;
    s = s->next;
  }
  return c;
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

  c->state->stack = stack(c, o, ceiling(size, BytesPerWord), c->state->stack);
  
  appendPush(c);
}

MyOperand*
pop(Context* c, unsigned size UNUSED)
{
  Stack* s = c->state->stack;
  assert(c, ceiling(size, BytesPerWord) == s->size);

  appendPop(c, s->size, false);

  c->state->stack = s->next;
  return s->operand;
}

void
markStack(Context* c, Stack* stack)
{
  for (Stack* s = stack; s; s = s->next) {
    if (s->operand->push) {
      s->operand->push->markStack(c);
    }
  }
}

void
markStack(Context* c)
{
  markStack(c, c->state->stack);
}

void
updateJunctions(Context* c)
{
  for (Junction* j = c->junctions; j; j = j->next) {
    LogicalInstruction* i = c->logicalCode + j->logicalIp;

    if (i->predecessor >= 0) {
      LogicalInstruction* p = c->logicalCode + i->predecessor;

      markStack(c, p->lastEvent->stack);
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

  if (c->stackOffset) {
    Assembler::Constant offset(resolved(c, c->stackOffset * BytesPerWord));
    a->apply(Subtract, BytesPerWord, Constant, &offset, Register, &stack);
  }

  for (unsigned i = 0; i < c->logicalCodeLength; ++ i) {
    LogicalInstruction* li = c->logicalCode + i;
    li->machineOffset = a->length();

    for (Event* e = li->firstEvent; e; e = e->next) {
      fprintf(stderr, "compile event at ip %d with stack count %d\n",
              i, count(e->stack));
      e->compile(c);

      for (CodePromise* p = e->promises; p; p = p->next) {
        p->offset = a->length();
      }

      if (e == li->lastEvent) break;
    }
  }
}

class Client: public Assembler::Client {
 public:
  Client(Context* c): c(c) { }

  virtual int acquireTemporary(int r) {
    if (r == NoRegister) {
      r = freeRegisterExcept(c, NoRegister, false);
    } else {
      expect(c, not c->registers[r].reserved);
      expect(c, c->registers[r].operand == 0);
    }
    c->registers[r].reserved = true;
    return r;
  }

  virtual void releaseTemporary(int r) {
    c->registers[r].reserved = false;
  }

  Context* c;
};

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Assembler* assembler, Zone* zone):
    c(s, assembler, zone), client(&c)
  {
    assembler->setClient(&client);
  }

  virtual void pushState() {
    ::pushState(&c);
  }

  virtual void popState() {
    ::popState(&c);
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
    return poolAppendPromise(resolved(&c, value));
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
    return promiseConstant(resolved(&c, value));
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
                          unsigned scale = 1)
  {
    MyOperand* result = operand
      (&c, ::memory
       (&c, static_cast<MyOperand*>(base), displacement,
        static_cast<MyOperand*>(index), scale));

    appendMemory(&c, static_cast<MyOperand*>(base),
                 static_cast<MyOperand*>(index), result);

    return result;
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

  virtual bool isConstant(Operand* a) {
    return static_cast<MyOperand*>(a)->value
      and static_cast<MyOperand*>(a)->value->type(&c) == Constant;
  }

  virtual int64_t constantValue(Operand* a) {
    assert(&c, isConstant(a));
    return static_cast<MyOperand*>(a)->value->constantValue(&c);
  }

  virtual Operand* label() {
    return operand(&c, ::constant(&c, static_cast<Promise*>(0)));
  }

  Promise* machineIp() {
    return c.event->promises = new (c.zone->allocate(sizeof(CodePromise)))
      CodePromise(&c, c.event->promises);
  }

  virtual void mark(Operand* label) {
    markStack(&c);

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
    for (unsigned i = 0; i < count; ++i) {
      MyOperand* a = operand(&c);
      ::push(&c, BytesPerWord, a);
      a->value = stackValue(&c, c.state->stack);
    }
  }

  virtual void popped(unsigned count) {
    appendPop(&c, count, true);

    for (unsigned i = count; i;) {
      Stack* s = c.state->stack;
      c.state->stack = s->next;
      i -= s->size;
    }
  }

  virtual Operand* peek(unsigned size UNUSED, unsigned index) {
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

    markStack(&c);

    MyOperand* result = operand(&c);
    appendCall(&c, static_cast<MyOperand*>(address), indirection, flags,
               traceHandler, result);
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
    markStack(&c);

    appendBranch(&c, JumpIfLess, static_cast<MyOperand*>(address));
  }

  virtual void jg(Operand* address) {
    markStack(&c);

    appendBranch(&c, JumpIfGreater, static_cast<MyOperand*>(address));
  }

  virtual void jle(Operand* address) {
    markStack(&c);

    appendBranch(&c, JumpIfLessOrEqual, static_cast<MyOperand*>(address));
  }

  virtual void jge(Operand* address) {
    markStack(&c);

    appendBranch(&c, JumpIfGreaterOrEqual, static_cast<MyOperand*>(address));
  }

  virtual void je(Operand* address) {
    markStack(&c);

    appendBranch(&c, JumpIfEqual, static_cast<MyOperand*>(address));
  }

  virtual void jne(Operand* address) {
    markStack(&c);

    appendBranch(&c, JumpIfNotEqual, static_cast<MyOperand*>(address));
  }

  virtual void jmp(Operand* address) {
    markStack(&c);

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
    return c.constantCount * BytesPerWord;
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
  Client client;
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
