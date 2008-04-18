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
class Stack;
class Site;
class Event;
class PushEvent;
class Read;

void NO_RETURN abort(Context*);

void
apply(Context* c, UnaryOperation op, unsigned size, Site* a);

void
apply(Context* c, BinaryOperation op, unsigned size, Site* a, Site* b);

class Site {
 public:
  Site(): next(0) { }
  
  virtual ~Site() { }

  virtual Site* readTarget(Context*, Read*) { return this; }

  virtual unsigned copyCost(Context*, Site*) = 0;
  
  virtual void acquire(Context*, Stack*, unsigned, Value*, Site*) { }

  virtual OperandType type(Context*) = 0;

  virtual Assembler::Operand* asAssemblerOperand(Context*) = 0;

  Site* next;
};

class Stack {
 public:
  Stack(Value* value, unsigned size, unsigned index, Stack* next):
    value(value), size(size), index(index), next(next), pushEvent(0),
    pushed(false)
  { }

  Value* value;
  unsigned size;
  unsigned index;
  Stack* next;
  PushEvent* pushEvent;
  bool pushed;
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
  Event* lastEvent;
  unsigned machineOffset;
  int predecessor;
};

class Register {
 public:
  Value* value;
  Site* site;
  unsigned size;
  bool reserved;
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

class Read {
 public:
  Read(unsigned size, Value* value, Site* target, Read* next, Event* event,
       Read* eventNext):
    size(size), value(value), target(target), next(next), event(event),
    eventNext(eventNext)
  { }
  
  unsigned size;
  Value* value;
  Site* target;
  Read* next;
  Event* event;
  Read* eventNext;
};

class Value: public Compiler::Operand {
 public:
  Value(Site* site):
    reads(0), lastRead(0), sites(site), source(0), pushCount(0)
  { }
  
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  unsigned pushCount;
};

class Context {
 public:
  Context(System* system, Assembler* assembler, Zone* zone):
    system(system),
    assembler(assembler),
    zone(zone),
    logicalIp(-1),
    state(new (zone->allocate(sizeof(State))) State(0)),
    firstEvent(0),
    lastEvent(0),
    logicalCode(0),
    logicalCodeLength(0),
    stackOffset(0),
    registers(static_cast<Register*>
              (zone->allocate(sizeof(Register) * assembler->registerCount()))),
    firstConstant(0),
    lastConstant(0),
    constantCount(0),
    junctions(0),
    machineCode(0)
  {
    memset(registers, 0, sizeof(Register) * assembler->registerCount());
    
    registers[assembler->base()].reserved = true;
    registers[assembler->stack()].reserved = true;
    registers[assembler->thread()].reserved = true;
  }

  System* system;
  Assembler* assembler;
  Zone* zone;
  int logicalIp;
  State* state;
  Event* firstEvent;
  Event* lastEvent;
  LogicalInstruction* logicalCode;
  unsigned logicalCodeLength;
  unsigned stackOffset;
  Register* registers;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  unsigned constantCount;
  Junction* junctions;
  uint8_t* machineCode;
};

void
addSite(Context* c, Stack* stack, unsigned size, Value* v, Site* s)
{
  s->acquire(c, stack, size, v, s);
  s->next = v->sites;
  v->sites = s;
}

bool
findSite(Context*, Value* v, Site* site)
{
  for (Site* s = v->sites; s; s = s->next) {
    if (s == site) return true;
  }
  return false;
}

void
removeSite(Context*, Value* v, Site* s)
{
  for (Site** p = &(v->sites); *p;) {
    if (s == *p) {
      *p = (*p)->next;
      break;
    } else {
      p = &((*p)->next);
    }
  }
}

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual unsigned copyCost(Context*, Site*) {
    return 1;
  }

  virtual OperandType type(Context*) {
    return ConstantOperand;
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

  virtual OperandType type(Context*) {
    return AddressOperand;
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
acquire(Context* c, int r, Stack* stack, unsigned newSize, Value* newValue,
        Site* newSite);

class RegisterSite: public Site {
 public:
  RegisterSite(int low, int high): register_(low, high) { }

  virtual unsigned copyCost(Context* c, Site* s) {
    if (s and
        (this == s or
         (s->type(c) == RegisterOperand
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

  virtual void acquire(Context* c, Stack* stack, unsigned size, Value* v,
                       Site* s)
  {
    ::acquire(c, register_.low, stack, size, v, s);
    if (register_.high >= 0) ::acquire(c, register_.high, stack, size, v, s);
  }

  virtual OperandType type(Context*) {
    return RegisterOperand;
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

RegisterSite*
freeRegister(Context* c, unsigned size, bool allowAcquired);

class MemorySite: public Site {
 public:
  MemorySite(int base, int offset, int index, unsigned scale):
    value(base, offset, index, scale)
  { }

  virtual unsigned copyCost(Context* c, Site* s) {
    if (s and
        (this == s or
         (s->type(c) == MemoryOperand
          and static_cast<MemorySite*>(s)->value.base == value.base
          and static_cast<MemorySite*>(s)->value.offset == value.offset
          and static_cast<MemorySite*>(s)->value.index == value.index
          and static_cast<MemorySite*>(s)->value.scale == value.scale)))
    {
      return 0;
    } else {
      return 4;
    }
  }

  virtual OperandType type(Context*) {
    return MemoryOperand;
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

class AbstractSite: public Site {
 public:
  virtual unsigned copyCost(Context* c, Site*) {
    abort(c);
  }

  virtual void copyTo(Context* c, unsigned, Site*) {
    abort(c);
  }

  virtual OperandType type(Context* c) {
    abort(c);
  }

  virtual Assembler::Operand* asAssemblerOperand(Context* c) {
    abort(c);
  }
};

class ValueSite: public AbstractSite {
 public:
  ValueSite(Value* value): value(value) { }

  virtual Site* readTarget(Context* c, Read*) {
    if (value->reads and value->reads->target) {
      return value->reads->target->readTarget(c, value->reads);
    } else {
      return 0;
    }
  }

  Value* value;
};

ValueSite*
valueSite(Context* c, Value* v)
{
  return new (c->zone->allocate(sizeof(ValueSite))) ValueSite(v);
}

class AnyRegisterSite: public AbstractSite {
 public:
  virtual Site* readTarget(Context* c, Read* r) {
    for (Site* s = r->value->sites; s; s = s->next) {
      if (s->type(c) == RegisterOperand) {
        return 0;
      }
    }
    return freeRegister(c, r->size, true);
  }

  Value* value;
};

AnyRegisterSite*
anyRegisterSite(Context* c)
{
  return new (c->zone->allocate(sizeof(AnyRegisterSite))) AnyRegisterSite();
}

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

Value*
value(Context* c, Site* site = 0)
{
  return new (c->zone->allocate(sizeof(Value))) Value(site);
}

Site*
pick(Context* c, Site* sites, Site* target = 0, unsigned* cost = 0)
{
  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (Site* s = sites; s; s = s->next) {
    unsigned v = s->copyCost(c, target);
    if (v < copyCost) {
      site = s;
      copyCost = v;
    }
  }

  if (cost) *cost = copyCost;
  return site;
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
    assert(c, not s->pushed);

    if (s->value) {
      apply(c, Push, s->size * BytesPerWord, pick(c, s->value->sites));
      ++ s->value->pushCount;
    } else {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset(resolved(c, s->size * BytesPerWord));
      c->assembler->apply
        (Subtract, BytesPerWord, ConstantOperand, &offset,
         RegisterOperand, &stack);
    }

    fprintf(stderr, "pushed %p\n", s);

    s->pushed = true;
  }
}

void
syncStack(Context* c, Stack* start)
{
  unsigned count = 0;
  for (Stack* s = start; s and (not s->pushed); s = s->next) {
    ++ count;
  }

  syncStack(c, start, count);
}

void
acquire(Context* c, int r, Stack* stack, unsigned newSize, Value* newValue,
        Site* newSite)
{
  Value* oldValue = c->registers[r].value;
  if (oldValue and findSite(c, oldValue, c->registers[r].site)) {
    if (oldValue->pushCount == 0
        and oldValue->sites->next == 0
        and oldValue->reads)
    {
      unsigned count = 0;
      Stack* start = 0;
      for (Stack* s = stack; s and (not s->pushed); s = s->next) {
        if (s->value == oldValue) {
          start = s;
        }
        if (start) {
          ++ count;
        }
      }

      assert(c, start);

      syncStack(c, start, count);
    }

    removeSite(c, oldValue, c->registers[r].site);
  }

  c->registers[r].size = newSize;
  c->registers[r].value = newValue;
  c->registers[r].site = newSite;
}

void
apply(Context* c, UnaryOperation op, unsigned size, Site* a)
{
  OperandType type = a->type(c);
  Assembler::Operand* operand = a->asAssemblerOperand(c);

  c->assembler->apply(op, size, type, operand);
}

void
apply(Context* c, BinaryOperation op, unsigned size, Site* a, Site* b)
{
  OperandType aType = a->type(c);
  Assembler::Operand* aOperand = a->asAssemblerOperand(c);

  OperandType bType = b->type(c);
  Assembler::Operand* bOperand = b->asAssemblerOperand(c);

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

class Event {
 public:
  Event(Context* c):
    next(0), stack(c->state->stack), promises(0), reads(0),
    logicalIp(c->logicalIp)
  {
    assert(c, c->logicalIp >= 0);

    if (c->lastEvent) {
      c->lastEvent->next = this;
      sequence = c->lastEvent->sequence + 1;
    } else {
      c->firstEvent = this;
      sequence = 0;
    }

    c->lastEvent = this;
  }

  Event(Context*, Event* next):
    next(next), stack(next->stack), promises(0), reads(0),
    sequence(next->sequence), logicalIp(next->logicalIp)
  { }

  virtual ~Event() { }

  virtual void compile(Context* c) = 0;

  Event* next;
  Stack* stack;
  CodePromise* promises;
  Read* reads;
  unsigned sequence;
  unsigned logicalIp;
};

void
insertRead(Context* c, Event* thisEvent, Event* before, Value* v,
           unsigned size, Site* target)
{
  Read* r = new (c->zone->allocate(sizeof(Read)))
    Read(size, v, target, 0, thisEvent, thisEvent->reads);
  thisEvent->reads = r;

  if (before) {
    for (Read** p = &(v->reads); *p;) {
      if ((*p)->event->sequence >= before->sequence) {
        r->next = *p;
        *p = r;
        break;
      } else {
        p = &((*p)->next);
      }
    }
  }

  if (r->next == 0) {
    if (v->lastRead) {
      v->lastRead->next = r;
    } else {
      v->reads = r;
    }
    v->lastRead = r;
  }
}

void
addRead(Context* c, Value* v, unsigned size, Site* target)
{
  insertRead(c, c->lastEvent, 0, v, size, target);
}

void
push(Context* c, unsigned size, Value* v);

class CallEvent: public Event {
 public:
  CallEvent(Context* c, Value* address, void* indirection, unsigned flags,
            TraceHandler* traceHandler, Value* result,
            unsigned argumentFootprint):
    Event(c),
    address(address),
    indirection(indirection),
    traceHandler(traceHandler),
    result(result),
    flags(flags),
    argumentFootprint(argumentFootprint)
  {
    addRead(c, address, BytesPerWord,
            (indirection ? registerSite(c, c->assembler->returnLow()) : 0));
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

    addSite(c, 0, 0, result, registerSite
            (c, c->assembler->returnLow(), c->assembler->returnHigh()));

    if (traceHandler) {
      traceHandler->handleTrace
        (new (c->zone->allocate(sizeof(CodePromise)))
         CodePromise(c, c->assembler->length()));
    }

    if (argumentFootprint) {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset
        (resolved(c, argumentFootprint * BytesPerWord));
      c->assembler->apply
        (Add, BytesPerWord, ConstantOperand, &offset, RegisterOperand, &stack);
    }
  }

  Value* address;
  void* indirection;
  TraceHandler* traceHandler;
  Value* result;
  unsigned flags;
  unsigned argumentFootprint;
};

void
appendCall(Context* c, Value* address, void* indirection, unsigned flags,
           TraceHandler* traceHandler, Value* result,
            unsigned argumentFootprint)
{
  fprintf(stderr, "appendCall\n");

  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, indirection, flags, traceHandler, result,
              argumentFootprint);
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

    c->assembler->apply(Move, BytesPerWord, RegisterOperand, &base,
                        RegisterOperand, &stack);
    c->assembler->apply(Pop, BytesPerWord, RegisterOperand, &base);
    c->assembler->apply(Return);
  }

  Value* value;
};

void
appendReturn(Context* c, unsigned size, Value* value)
{
  fprintf(stderr, "appendReturn\n");

  new (c->zone->allocate(sizeof(ReturnEvent))) ReturnEvent(c, size, value);
}

Site*
writeTarget(Context* c, unsigned size, Value* v)
{
  if (v->sites) {
    assert(c, v->sites->next == 0);
    return v->sites;
  } else if (v->reads and v->reads->target) {
    Site* s = v->reads->target->readTarget(c, v->reads);
    if (s) return s;
  }
  return freeRegister(c, size, true);
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned size, Value* src,
            Value* dst):
    Event(c), type(type), size(size), src(src), dst(dst)
  {
    addRead(c, src, size, 0);
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MoveEvent.compile\n");

    Site* target = writeTarget(c, size, dst);
    apply(c, type, size, src->source, target);
    addSite(c, stack, size, dst, target);
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
  fprintf(stderr, "appendMove\n");

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
  fprintf(stderr, "appendCompare\n");

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
  fprintf(stderr, "appendBranch\n");

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
            valueSite(c, result) :
            static_cast<Site*>(registerSite(c, r2.low, r2.high)));
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "CombineEvent.compile\n");

    apply(c, type, size, first->source, second->source);
    removeSite(c, second, second->source);
    addSite(c, 0, 0, result, second->source);
  }

  BinaryOperation type;
  unsigned size;
  Value* first;
  Value* second;
  Value* result;
};

void
appendCombine(Context* c, BinaryOperation type, unsigned size, Value* first,
              Value* second, Value* result)
{
  fprintf(stderr, "appendCombine\n");

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
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "TranslateEvent.compile\n");

    apply(c, type, size, value->source);
    removeSite(c, value, value->source);
    addSite(c, 0, 0, result, value->source);
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
  fprintf(stderr, "appendTranslate\n");

  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent(c, type, size, value, result);
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, int displacement, Value* index,
              unsigned scale, Value* result):
    Event(c), base(base), displacement(displacement), index(index),
    scale(scale), result(result)
  {
    addRead(c, base, BytesPerWord, anyRegisterSite(c));
    if (index) addRead(c, index, BytesPerWord, anyRegisterSite(c));
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "MemoryEvent.compile\n");
    
    int indexRegister;
    if (index) {
      assert(c, index->source->type(c) == RegisterOperand);
      indexRegister = static_cast<RegisterSite*>(index->source)->register_.low;
    } else {
      indexRegister = NoRegister;
    }
    assert(c, base->source->type(c) == RegisterOperand);
    int baseRegister = static_cast<RegisterSite*>(base->source)->register_.low;

    addSite(c, 0, 0, result, memorySite
            (c, baseRegister, displacement, indexRegister, scale));
  }

  Value* base;
  int displacement;
  Value* index;
  unsigned scale;
  Value* result;
};

void
appendMemory(Context* c, Value* base, int displacement, Value* index,
             unsigned scale, Value* result)
{
  fprintf(stderr, "appendMemory\n");

  new (c->zone->allocate(sizeof(MemoryEvent)))
    MemoryEvent(c, base, displacement, index, scale, result);
}

Site*
stackSyncSite(Context* c, unsigned index, unsigned size)
{
  int high = NoRegister;
  for (int i = c->assembler->registerCount(); i >= 0; --i) {
    if (not c->registers[i].reserved) {
      if (index == 0) {
        if (size == 1) {
          return registerSite(c, i, high);
        } else {
          high = i;
          -- size;
        }
      } else {
        -- index;
      }
    }
  }
  
  abort(c);
}

class StackSyncEvent: public Event {
 public:
  StackSyncEvent(Context* c):
    Event(c)
  {
    unsigned i = 0;
    for (Stack* s = stack; s; s = s->next) {
      addRead(c, s->value, s->size * BytesPerWord,
              stackSyncSite(c, i, s->size));
      i += s->size;
    }
  }

  StackSyncEvent(Context* c, Event* next):
    Event(c, next)
  {
    unsigned i = 0;
    for (Stack* s = stack; s; s = s->next) {
      insertRead(c, this, next, s->value, s->size * BytesPerWord,
                 stackSyncSite(c, i, s->size));
      i += s->size;
    }
  }

  virtual void compile(Context*) {
    fprintf(stderr, "StackSyncEvent.compile\n");

    for (Read* r = reads; r; r = r->eventNext) {
      r->value->sites = r->target;
      r->target->next = 0;
    }
  }
};

void
appendStackSync(Context* c)
{
  fprintf(stderr, "appendStackSync\n");

  new (c->zone->allocate(sizeof(StackSyncEvent))) StackSyncEvent(c);
}

class PushEvent: public Event {
 public:
  PushEvent(Context* c):
    Event(c), active(false)
  {
    stack->pushEvent = this;
  }

  virtual void compile(Context* c) {
    fprintf(stderr, "PushEvent.compile\n");

    if (active) {
      syncStack(c, stack);
    }
  }

  bool active;
};

void
appendPush(Context* c)
{
  fprintf(stderr, "appendPush\n");

  new (c->zone->allocate(sizeof(PushEvent))) PushEvent(c);
}

class PopEvent: public Event {
 public:
  PopEvent(Context* c, unsigned count, bool ignore):
    Event(c), count(count), ignore(ignore)
  { }

  virtual void compile(Context* c) {
    fprintf(stderr, "PopEvent.compile\n");

    Stack* s = stack;
    unsigned ignored = 0;
    for (unsigned i = count; i;) {
      if (s->pushed) {
        if (s->value->reads and (not ignore)) {
          assert(c, ignored == 0);

          fprintf(stderr, "pop %p\n", s);

          Site* target = writeTarget(c, s->size * BytesPerWord, s->value);

          apply(c, Pop, BytesPerWord * s->size, target);
          -- s->value->pushCount;

          addSite(c, stack, s->size * BytesPerWord, s->value, target);
        } else {
          fprintf(stderr, "ignore %p\n", s);
          
          ignored += s->size;
        }

        s->pushed = false;
      } else {
        fprintf(stderr, "%p not pushed\n", s);
      }

      i -= s->size;
      s = s->next;
    }

    if (ignored) {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset(resolved(c, ignored * BytesPerWord));
      c->assembler->apply
        (Add, BytesPerWord, ConstantOperand, &offset, RegisterOperand, &stack);
    }
  }

  unsigned count;
  bool ignore;
};

void
appendPop(Context* c, unsigned count, bool ignore)
{
  fprintf(stderr, "appendPop\n");

  new (c->zone->allocate(sizeof(PopEvent))) PopEvent(c, count, ignore);
}

Site*
readSource(Context* c, Stack* stack, Read* r)
{
  Site* target = (r->target ? r->target->readTarget(c, r) : 0);

  unsigned copyCost;
  Site* site = pick(c, r->value->sites, target, &copyCost);

  if (target) {
    if (copyCost) {
      addSite(c, stack, r->size, r->value, target);

      apply(c, Move, r->size, site, target);
    }

    return target;
  } else {
    return site;
  }
}

void
compile(Context* c)
{
  Assembler* a = c->assembler;

  Assembler::Register base(a->base());
  Assembler::Register stack(a->stack());
  a->apply(Push, BytesPerWord, RegisterOperand, &base);
  a->apply(Move, BytesPerWord, RegisterOperand, &stack,
           RegisterOperand, &base);

  if (c->stackOffset) {
    Assembler::Constant offset(resolved(c, c->stackOffset * BytesPerWord));
    a->apply(Subtract, BytesPerWord, ConstantOperand, &offset,
             RegisterOperand, &stack);
  }

  for (Event* e = c->firstEvent; e; e = e->next) {
    LogicalInstruction* li = c->logicalCode + e->logicalIp;
    li->machineOffset = a->length();

    for (Read* r = e->reads; r; r = r->eventNext) {
      r->value->source = readSource(c, e->stack, r);

      r->value->reads = r->value->reads->next;
    }

    e->compile(c);

    for (CodePromise* p = e->promises; p; p = p->next) {
      p->offset = a->length();
    }
  }
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
stack(Context* c, Value* value, unsigned size, unsigned index, Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(value, size, index, next);
}

Stack*
stack(Context* c, Value* value, unsigned size, Stack* next)
{
  return stack(c, value, size, (next ? next->index + size : 0), next);
}

void
push(Context* c, unsigned size, Value* v)
{
  assert(c, ceiling(size, BytesPerWord));

  c->state->stack = stack(c, v, ceiling(size, BytesPerWord), c->state->stack);

  appendPush(c);
}

Value*
pop(Context* c, unsigned size UNUSED)
{
  Stack* s = c->state->stack;
  assert(c, ceiling(size, BytesPerWord) == s->size);

  appendPop(c, s->size, false);

  c->state->stack = s->next;
  return s->value;
}

void
updateJunctions(Context* c)
{
  for (Junction* j = c->junctions; j; j = j->next) {
    LogicalInstruction* i = c->logicalCode + j->logicalIp;

    if (i->predecessor >= 0) {
      LogicalInstruction* p = c->logicalCode + i->predecessor;

      p->lastEvent = p->lastEvent->next
        = new (c->zone->allocate(sizeof(StackSyncEvent)))
        StackSyncEvent(c, p->lastEvent->next);
    }
  }
}

int
freeRegisterExcept(Context* c, int except, bool allowAcquired)
{
  for (int i = c->assembler->registerCount(); i >= 0; --i) {
    if (i != except
        and (not c->registers[i].reserved)
        and c->registers[i].value == 0)
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

int
freeRegister(Context* c, bool allowAcquired)
{
  return freeRegisterExcept(c, NoRegister, allowAcquired);
}

RegisterSite*
freeRegister(Context* c, unsigned size, bool allowAcquired)
{
  if (BytesPerWord == 4 and size == 8) {
    int low = freeRegister(c, allowAcquired);
    return registerSite(c, low, freeRegisterExcept(c, low, allowAcquired));
  } else {
    return registerSite(c, freeRegister(c, allowAcquired));
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
      expect(c, c->registers[r].value == 0);
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
      c.logicalCode[c.logicalIp].lastEvent = c.lastEvent;
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
    return ::value(&c, ::constantSite(&c, value));
  }

  virtual Operand* address(Promise* address) {
    return value(&c, ::addressSite(&c, address));
  }

  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1)
  {
    Value* result = value(&c);

    appendMemory(&c, static_cast<Value*>(base), displacement,
                 static_cast<Value*>(index), scale, result);

    return result;
  }

  virtual Operand* stack() {
    return value(&c, registerSite(&c, c.assembler->stack()));
  }

  virtual Operand* base() {
    return value(&c, registerSite(&c, c.assembler->base()));
  }

  virtual Operand* thread() {
    return value(&c, registerSite(&c, c.assembler->thread()));
  }

  virtual bool isConstant(Operand* a) {
    for (Site* s = static_cast<Value*>(a)->sites; s; s = s->next) {
      if (s->type(&c) == ConstantOperand) return true;
    }
    return false;
  }

  virtual int64_t constantValue(Operand* a) {
    for (Site* s = static_cast<Value*>(a)->sites; s; s = s->next) {
      if (s->type(&c) == ConstantOperand) {
        return static_cast<ConstantSite*>(s)->value.value->value();
      }
    }
    abort(&c);
  }

  virtual Operand* label() {
    return value(&c, ::constantSite(&c, static_cast<Promise*>(0)));
  }

  Promise* machineIp() {
    return c.lastEvent->promises = new (c.zone->allocate(sizeof(CodePromise)))
      CodePromise(&c, c.lastEvent->promises);
  }

  virtual void mark(Operand* label) {
    appendStackSync(&c);

    for (Site* s = static_cast<Value*>(label)->sites; s; s = s->next) {
      if (s->type(&c) == ConstantOperand) {
        static_cast<ConstantSite*>(s)->value.value = machineIp();
        return;
      }
    }
    abort(&c);
  }

  virtual void push(unsigned size, Operand* value) {
    ::push(&c, size, static_cast<Value*>(value));
  }

  virtual Operand* pop(unsigned size) {
    return ::pop(&c, size);
  }

  virtual void pushed(unsigned count) {
    for (unsigned i = 0; i < count; ++i) {
      Value* a = value(&c);
      ::push(&c, BytesPerWord, a);
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
    return s->value;
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
    Value* arguments[argumentCount];
    unsigned argumentSizes[argumentCount];
    unsigned index = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Value* o = va_arg(a, Value*);
      if (o) {
        arguments[index] = o;
        argumentSizes[index] = size;
        size = BytesPerWord;
        ++ index;
      } else {
        size = 8;
      }
      ++ footprint;
    }

    va_end(a);

    Stack* oldStack = c.state->stack;

    for (int i = index - 1; i >= 0; --i) {
      ::push(&c, argumentSizes[i], arguments[i]);
    }

    unsigned ai = 0;
    Stack* s = c.state->stack;
    for (unsigned i = 0; i < index; ++i) {
      Site* target;
      if (ai < c.assembler->argumentRegisterCount()) {
        target = registerSite(&c, c.assembler->argumentRegister(index));
      } else {
        target = 0;
        s->pushEvent->active = true;
      }
      addRead(&c, s->value, s->size * BytesPerWord, target);
      ai += s->size;
      s = s->next;
    }

    c.state->stack = oldStack;

    for (Stack* s = oldStack; s; s = s->next) {
      s->pushEvent->active = true;
    }

    Value* result = value(&c);
    appendCall(&c, static_cast<Value*>(address), indirection, flags,
               traceHandler, result, footprint);

    return result;
  }

  virtual void return_(unsigned size, Operand* value) {
    appendReturn(&c, size, static_cast<Value*>(value));
  }

  virtual void store(unsigned size, Operand* src, Operand* dst) {
    appendMove(&c, Move, size, static_cast<Value*>(src),
               static_cast<Value*>(dst));
  }

  virtual Operand* load(unsigned size, Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, Move, size, static_cast<Value*>(src), dst);
    return dst;
  }

  virtual Operand* loadz(unsigned size, Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, MoveZ, size, static_cast<Value*>(src), dst);
    return dst;
  }

  virtual Operand* load4To8(Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, Move4To8, 0, static_cast<Value*>(src), dst);
    return dst;
  }

  virtual void cmp(unsigned size, Operand* a, Operand* b) {
    appendCompare(&c, size, static_cast<Value*>(a),
                  static_cast<Value*>(b));
  }

  virtual void jl(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfLess, static_cast<Value*>(address));
  }

  virtual void jg(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfGreater, static_cast<Value*>(address));
  }

  virtual void jle(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfLessOrEqual, static_cast<Value*>(address));
  }

  virtual void jge(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfGreaterOrEqual, static_cast<Value*>(address));
  }

  virtual void je(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfEqual, static_cast<Value*>(address));
  }

  virtual void jne(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, JumpIfNotEqual, static_cast<Value*>(address));
  }

  virtual void jmp(Operand* address) {
    appendStackSync(&c);

    appendBranch(&c, Jump, static_cast<Value*>(address));
  }

  virtual Operand* add(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Add, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* sub(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Subtract, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* mul(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Multiply, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* div(unsigned size, Operand* a, Operand* b)  {
    Value* result = value(&c);
    appendCombine(&c, Divide, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* rem(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Remainder, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* shl(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, ShiftLeft, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* shr(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, ShiftRight, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* ushr(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, UnsignedShiftRight, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* and_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, And, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* or_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Or, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* xor_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Xor, size, static_cast<Value*>(a),
                  static_cast<Value*>(b), result);
    return result;
  }

  virtual Operand* neg(unsigned size, Operand* a) {
    Value* result = value(&c);
    appendTranslate(&c, Negate, size, static_cast<Value*>(a), result);
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
