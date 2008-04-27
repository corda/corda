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

const bool DebugAppend = false;
const bool DebugCompile = false;
const bool DebugStack = false;
const bool DebugRegisters = false;

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

  virtual Site* readTarget(Context*, Read*, Event*) { return this; }

  virtual unsigned copyCost(Context*, Site*) = 0;
  
  virtual bool tryAcquire(Context*, Stack*, unsigned, Value*) { return true; }

  virtual void release(Context*) { }

  virtual OperandType type(Context*) = 0;

  virtual Assembler::Operand* asAssemblerOperand(Context*) = 0;

  Site* next;
};

class Stack {
 public:
  Stack(Value* value, unsigned size, unsigned index, Stack* next):
    value(value), size(size), index(index), next(next), pushEvent(0),
    pushSite(0), pushed(false)
  { }

  Value* value;
  unsigned size;
  unsigned index;
  Stack* next;
  PushEvent* pushEvent;
  Site* pushSite;
  bool pushed;
};

class State {
 public:
  State(State* next, Stack* stack):
    stack(stack),
    next(next)
  { }

  Stack* stack;
  State* next;
};

class LogicalInstruction {
 public:
  Event* firstEvent;
  Event* lastEvent;
  LogicalInstruction* immediatePredecessor;
  Stack* stack;
  unsigned machineOffset;
  bool stackSaved;
};

class Register {
 public:
  Value* value;
  Site* site;
  unsigned size;
  unsigned refCount;
  bool reserved;
  bool pushed;
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
  Value(Site* site, Site* target):
    reads(0), lastRead(0), sites(site), source(0), target(target)
  { }
  
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  Site* target;
};

class Context {
 public:
  Context(System* system, Assembler* assembler, Zone* zone):
    system(system),
    assembler(assembler),
    zone(zone),
    logicalIp(-1),
    state(new (zone->allocate(sizeof(State))) State(0, 0)),
    logicalCode(0),
    logicalCodeLength(0),
    stackOffset(0),
    registers(static_cast<Register*>
              (zone->allocate(sizeof(Register) * assembler->registerCount()))),
    freeRegisterCount(assembler->registerCount() - 3),
    freeRegisters(static_cast<int*>
                  (zone->allocate(sizeof(int) * freeRegisterCount))),
    firstConstant(0),
    lastConstant(0),
    constantCount(0),
    nextSequence(0),
    junctions(0),
    machineCode(0),
    stackReset(false)
  {
    memset(registers, 0, sizeof(Register) * assembler->registerCount());
    
    registers[assembler->base()].refCount = 1;
    registers[assembler->base()].reserved = true;
    registers[assembler->stack()].refCount = 1;
    registers[assembler->stack()].reserved = true;
    registers[assembler->thread()].refCount = 1;
    registers[assembler->thread()].reserved = true;

    unsigned fri = 0;
    for (int i = assembler->registerCount() - 1; i >= 0; --i) {
      if (not registers[i].reserved) {
        freeRegisters[fri++] = i;
      }
    }
  }

  System* system;
  Assembler* assembler;
  Zone* zone;
  int logicalIp;
  State* state;
  LogicalInstruction* logicalCode;
  unsigned logicalCodeLength;
  unsigned stackOffset;
  Register* registers;
  unsigned freeRegisterCount;
  int* freeRegisters;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  unsigned constantCount;
  unsigned nextSequence;
  Junction* junctions;
  uint8_t* machineCode;
  bool stackReset;
};

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

class Event {
 public:
  Event(Context* c):
    next(0), stack(c->state->stack), promises(0), reads(0),
    sequence(c->nextSequence++), stackReset(c->stackReset)
  {
    assert(c, c->logicalIp >= 0);

    LogicalInstruction* i = c->logicalCode + c->logicalIp;
    if (i->lastEvent) {
      i->lastEvent->next = this;
    } else {
      i->firstEvent = this;
    }
    i->lastEvent = this;

    if (c->stackReset) {
//       fprintf(stderr, "stack reset\n");
      c->stackReset = false;
    }
  }

  Event(Context*, unsigned sequence, Stack* stack):
    next(0), stack(stack), promises(0), reads(0),
    sequence(sequence), stackReset(false)
  { }

  virtual ~Event() { }

  virtual void compile(Context* c) = 0;

  virtual bool isBranch() { return false; };

  Event* next;
  Stack* stack;
  CodePromise* promises;
  Read* reads;
  unsigned sequence;
  bool stackReset;
};

bool
findSite(Context*, Value* v, Site* site)
{
  for (Site* s = v->sites; s; s = s->next) {
    if (s == site) return true;
  }
  return false;
}

bool
tryAddSite(Context* c, Stack* stack, unsigned size, Value* v, Site* s)
{
  if (not findSite(c, v, s)) {
//     fprintf(stderr, "add site %p to %p\n", s, v);
    if (s->tryAcquire(c, stack, size, v)) {
      s->next = v->sites;
      v->sites = s;
    } else {
      return false;
    }
  }
  return true;
}

void
addSite(Context* c, Stack* stack, unsigned size, Value* v, Site* s)
{
  expect(c, tryAddSite(c, stack, size, v, s));
}

void
removeSite(Context* c, Value* v, Site* s)
{
  for (Site** p = &(v->sites); *p;) {
    if (s == *p) {
//       fprintf(stderr, "remove site %p from %p\n", s, v);
      s->release(c);
      *p = (*p)->next;
      break;
    } else {
      p = &((*p)->next);
    }
  }
}

void
clearSites(Context* c, Value* v)
{
  for (Site* s = v->sites; s; s = s->next) {
    s->release(c);
  }
  v->sites = 0;
}

void
nextRead(Context* c, Value* v)
{
//   fprintf(stderr, "pop read %p from %p; next: %p\n", v->reads, v, v->reads->next);

  v->reads = v->reads->next;
  if (v->reads == 0) {
    clearSites(c, v);
  }
}

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : 1);
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

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : 3);
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

bool
tryAcquire(Context* c, int r, Stack* stack, unsigned newSize, Value* newValue,
           Site* newSite);

void
release(Context* c, int r);

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

  virtual bool tryAcquire(Context* c, Stack* stack, unsigned size, Value* v) {
    if (::tryAcquire(c, register_.low, stack, size, v, this)) {
      if (register_.high != NoRegister) {
        if (::tryAcquire(c, register_.high, stack, size, v, this)) {
          return true;
        } else {
          ::release(c, register_.low);
        }
      } else {
        return true;
      }
    }
    return false;
  }

  virtual void release(Context* c) {
    ::release(c, register_.low);
    if (register_.high >= 0) {
      ::release(c, register_.high);
    }    
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
  assert(c, low != NoRegister);
  assert(c, low < static_cast<int>(c->assembler->registerCount()));
  assert(c, high == NoRegister
         or high < static_cast<int>(c->assembler->registerCount()));

  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(low, high);
}

RegisterSite*
freeRegister(Context* c, unsigned size, bool allowAcquired);

void
increment(Context* c, int r)
{
  if (DebugRegisters) {
    fprintf(stderr, "increment %d to %d\n", r, c->registers[r].refCount + 1);
  }
  ++ c->registers[r].refCount;
}

void
decrement(Context* c, int r)
{
  assert(c, c->registers[r].refCount > 0);
  assert(c, c->registers[r].refCount > 1 or (not c->registers[r].reserved));
  if (DebugRegisters) {
    fprintf(stderr, "decrement %d to %d\n", r, c->registers[r].refCount - 1);
  }
  -- c->registers[r].refCount;
}

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

  virtual bool tryAcquire(Context* c, Stack*, unsigned, Value*) {
    increment(c, value.base);
    if (value.index != NoRegister) {
      increment(c, value.index);
    }
    return true;
  }

  virtual void release(Context* c) {
    decrement(c, value.base);
    if (value.index != NoRegister) {
      decrement(c, value.index);
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

Site*
targetOrNull(Context* c, Read* r, Event* event)
{
  Value* v = r->value;
  if (v->target) {
    return v->target;
  } else if (r->target) {
    return r->target->readTarget(c, r, event);
  } else {
    return 0;
  }
}

Site*
targetOrNull(Context* c, Value* v, Event* event)
{
  if (v->target) {
    return v->target;
  } else if (v->reads and v->reads->target) {
    return v->reads->target->readTarget(c, v->reads, event);
  } else {
    return 0;
  }
}

Site*
targetOrRegister(Context* c, unsigned size, Value* v, Event* event)
{
  Site* s = targetOrNull(c, v, event);
  if (s) {
    return s;
  } else {
    return freeRegister(c, size, true);
  }
}

class ValueSite: public AbstractSite {
 public:
  ValueSite(Value* value): value(value) { }

  virtual Site* readTarget(Context* c, Read* r, Event* event) {
    Site* s = targetOrNull(c, value, event);
    if (s and s->type(c) == RegisterOperand) {
      return s;
    } else {
      return freeRegister(c, r->size, true);
    }
  }

  Value* value;
};

ValueSite*
valueSite(Context* c, Value* v)
{
  return new (c->zone->allocate(sizeof(ValueSite))) ValueSite(v);
}

class MoveSite: public AbstractSite {
 public:
  MoveSite(Value* value): value(value)  { }

  virtual Site* readTarget(Context* c, Read* read, Event* event) {
    if (event->next == read->event) {
      return targetOrNull(c, value, event);
    } else {
      return 0;
    }
  }

  Value* value;
};

MoveSite*
moveSite(Context* c, Value* v)
{
  return new (c->zone->allocate(sizeof(MoveSite))) MoveSite(v);
}

class AnyRegisterSite: public AbstractSite {
 public:
  virtual Site* readTarget(Context* c, Read* r, Event*) {
    for (Site* s = r->value->sites; s; s = s->next) {
      if (s->type(c) == RegisterOperand) {
        return s;
      }
    }
    return freeRegister(c, r->size, true);
  }
};

AnyRegisterSite*
anyRegisterSite(Context* c)
{
  return new (c->zone->allocate(sizeof(AnyRegisterSite))) AnyRegisterSite();
}

class ConstantOrRegisterSite: public AbstractSite {
 public:
  virtual Site* readTarget(Context* c, Read* r, Event*) {
    for (Site* s = r->value->sites; s; s = s->next) {
      OperandType t = s->type(c);
      if (t == ConstantOperand or t == RegisterOperand) {
        return s;
      }
    }
    return freeRegister(c, r->size, true);
  }
};

ConstantOrRegisterSite*
constantOrRegisterSite(Context* c)
{
  return new (c->zone->allocate(sizeof(ConstantOrRegisterSite)))
    ConstantOrRegisterSite();
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

Site*
pushSite(Context* c, unsigned index)
{
  return memorySite
    (c, c->assembler->base(),
     - (c->stackOffset + index + 1) * BytesPerWord, NoRegister, 1);
}

void
pushNow(Context* c, Stack* start, unsigned count)
{
  Stack* segment[count];
  unsigned index = count;
  for (Stack* s = start; s and index; s = s->next) {
    segment[--index] = s;
  }

  for (unsigned i = 0; i < count; ++i) {
    Stack* s = segment[i];
    assert(c, not s->pushed);

    if (s->value and s->value->sites) {
      Site* source = pick(c, s->value->sites);

      s->pushSite = pushSite(c, s->index);
      addSite(c, 0, s->size * BytesPerWord, s->value, s->pushSite);

      apply(c, Push, s->size * BytesPerWord, source);
    } else {
      Assembler::Register stack(c->assembler->stack());
      Assembler::Constant offset(resolved(c, s->size * BytesPerWord));
      c->assembler->apply
        (Subtract, BytesPerWord, ConstantOperand, &offset,
         RegisterOperand, &stack);
    }

    if (DebugStack) {
      fprintf(stderr, "pushed %p value: %p sites: %p\n",
              s, s->value, s->value->sites);
    }

    s->pushed = true;
  }
}

void
pushNow(Context* c, Stack* start)
{
  unsigned count = 0;
  for (Stack* s = start; s and (not s->pushed); s = s->next) {
    ++ count;
  }

  pushNow(c, start, count);
}

bool
trySteal(Context* c, int r, Stack* stack)
{
  Value* v = c->registers[r].value;

  assert(c, c->registers[r].refCount == 0);

  if (DebugRegisters) {
    fprintf(stderr, "try steal %d from %p: next: %p\n",
            r, v, v->sites->next);
  }

  if (v->sites->next == 0) {
    unsigned count = 0;
    Stack* start = 0;
    for (Stack* s = stack; s and (not s->pushed); s = s->next) {
      if (s->value == v) {
        start = s;
      }
      if (start) {
        ++ count;
      }
    }

    if (start) {
      pushNow(c, start, count);
    } else {
      return false;
    }
  }

  removeSite(c, v, c->registers[r].site);

  return true;
}

bool
tryAcquire(Context* c, int r, Stack* stack, unsigned newSize, Value* newValue,
           Site* newSite)
{
  if (c->registers[r].reserved) return true;

  if (DebugRegisters) {
    fprintf(stderr, "try acquire %d, value %p, site %p\n",
            r, newValue, newSite);
  }

  Value* oldValue = c->registers[r].value;
  if (oldValue
      and oldValue != newValue
      and findSite(c, oldValue, c->registers[r].site))
  {
    if (not trySteal(c, r, stack)) {
      return false;
    }
  }

  c->registers[r].size = newSize;
  c->registers[r].value = newValue;
  c->registers[r].site = newSite;

  return true;
}

void
release(Context* c, int r)
{
  if (DebugRegisters) {
    fprintf(stderr, "release %d\n", r);
  }

  c->registers[r].size = 0;
  c->registers[r].value = 0;
  c->registers[r].site = 0;  
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

void
insertRead(Context* c, Event* thisEvent, int sequence, Value* v,
           unsigned size, Site* target)
{
  Read* r = new (c->zone->allocate(sizeof(Read)))
    Read(size, v, target, 0, thisEvent, thisEvent->reads);
  thisEvent->reads = r;

  //  fprintf(stderr, "add read %p to %p\n", r, v);

  if (sequence >= 0) {
    for (Read** p = &(v->reads); *p;) {
      if ((*p)->event->sequence > static_cast<unsigned>(sequence)) {
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
  insertRead(c, c->logicalCode[c->logicalIp].lastEvent, -1, v, size, target);
}

Site*
pushSite(Context*, PushEvent*);

class PushEvent: public Event {
 public:
  PushEvent(Context* c, Stack* s):
    Event(c), s(s), active(false)
  {
    assert(c, s->pushEvent == 0);

    s->pushEvent = this;
    addRead(c, s->value, s->size * BytesPerWord, pushSite(c, this));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "PushEvent.compile active: %d\n", active);
    }

    if (active) {
      pushNow(c, s);
    }

    nextRead(c, s->value);
  }

  Stack* s;
  bool active;
};

void
push(Context* c, unsigned size, Value* v);

void
ignore(Context* c, unsigned count)
{
  if (count) {
    Assembler::Register stack(c->assembler->stack());
    Assembler::Constant offset(resolved(c, count * BytesPerWord));
    c->assembler->apply
      (Add, BytesPerWord, ConstantOperand, &offset, RegisterOperand, &stack);
  }
}

class CallEvent: public Event {
 public:
  CallEvent(Context* c, Value* address, void* indirection, unsigned flags,
            TraceHandler* traceHandler, Value* result, unsigned resultSize,
            Stack* argumentStack, unsigned argumentCount):
    Event(c),
    address(address),
    indirection(indirection),
    traceHandler(traceHandler),
    result(result),
    flags(flags),
    resultSize(resultSize),
    argumentFootprint(0)
  {
    Stack* s = argumentStack;
    unsigned index = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Site* target;
      if (index < c->assembler->argumentRegisterCount()) {
        target = registerSite
          (c, c->assembler->argumentRegister(index));
      } else {
        target = 0;
        s->pushEvent->active = true;
        ++ argumentFootprint;
      }
      addRead(c, s->value, s->size * BytesPerWord, target);
      index += s->size;
      s = s->next;
    }

    for (Stack* s = stack; s; s = s->next) {
      addRead(c, s->value, s->size * BytesPerWord, 0);
    }

    addRead(c, address, BytesPerWord,
            (indirection ? registerSite(c, c->assembler->returnLow()) : 0));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CallEvent.compile\n");
    }

    pushNow(c, stack);
    
    UnaryOperation type = ((flags & Compiler::Aligned) ? AlignedCall : Call);
    if (indirection) {
      apply(c, type, BytesPerWord,
            constantSite(c, reinterpret_cast<intptr_t>(indirection)));
    } else {
      apply(c, type, BytesPerWord, address->source);
    }

    for (Stack* s = stack; s; s = s->next) {
      clearSites(c, s->value);
    }

    for (Stack* s = stack; s; s = s->next) {
      if (s->pushSite) {
        addSite(c, 0, s->size * BytesPerWord, s->value, s->pushSite);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      nextRead(c, r->value);
    }

    if (resultSize and result->reads) {
      addSite(c, 0, resultSize, result, registerSite
              (c, c->assembler->returnLow(),
               resultSize > BytesPerWord ?
               c->assembler->returnHigh() : NoRegister));
    }

    if (traceHandler) {
      traceHandler->handleTrace
        (new (c->zone->allocate(sizeof(CodePromise)))
         CodePromise(c, c->assembler->length()));
    }

    if (argumentFootprint and ((flags & Compiler::NoReturn) == 0)) {
      ignore(c, argumentFootprint);
    }
  }

  Value* address;
  void* indirection;
  TraceHandler* traceHandler;
  Value* result;
  unsigned flags;
  unsigned resultSize;
  unsigned argumentFootprint;
};

void
appendCall(Context* c, Value* address, void* indirection, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount)
{
  if (DebugAppend) {
    fprintf(stderr, "appendCall\n");
  }

  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, indirection, flags, traceHandler, result,
              resultSize, argumentStack, argumentCount);
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
    if (DebugCompile) {
      fprintf(stderr, "ReturnEvent.compile\n");
    }

    if (value) {
      nextRead(c, value);
    }

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
  if (DebugAppend) {
    fprintf(stderr, "appendReturn\n");
  }

  new (c->zone->allocate(sizeof(ReturnEvent))) ReturnEvent(c, size, value);
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned size, Value* src,
            Value* dst):
    Event(c), type(type), size(size), src(src), dst(dst)
  {
    Site* target;
    if (type == Move and size >= BytesPerWord) {
      target = moveSite(c, dst);
    } else {
      target = 0;
    }
    addRead(c, src, size, target);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "MoveEvent.compile\n");
    }

    Site* target;
    unsigned cost;
    if (type == Move
        and size >= BytesPerWord
        and dst->reads
        and next == dst->reads->event)
    {
      target = src->source;
      cost = 0;
    } else {
      target = targetOrRegister(c, size, dst, this);
      cost = src->source->copyCost(c, target);
    }

    nextRead(c, src);

    if (cost) {
      apply(c, type, size, src->source, target);
    }

    if (dst->reads) {
      addSite(c, stack, size, dst, target);
    } else {
      removeSite(c, dst, target);
    }
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
  if (DebugAppend) {
    fprintf(stderr, "appendMove\n");
  }

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
    if (DebugCompile) {
      fprintf(stderr, "CompareEvent.compile\n");
    }

    apply(c, Compare, size, first->source, second->source);

    nextRead(c, first);
    nextRead(c, second);
  }

  unsigned size;
  Value* first;
  Value* second;
};

void
appendCompare(Context* c, unsigned size, Value* first, Value* second)
{
  if (DebugAppend) {
    fprintf(stderr, "appendCompare\n");
  }

  new (c->zone->allocate(sizeof(CompareEvent)))
    CompareEvent(c, size, first, second);
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
            r1.low == NoRegister ?
            constantOrRegisterSite(c) :
            static_cast<Site*>(registerSite(c, r1.low, r1.high)));
    addRead(c, second, size,
            r2.low == NoRegister ?
            valueSite(c, result) :
            static_cast<Site*>(registerSite(c, r2.low, r2.high)));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CombineEvent.compile\n");
    }

    apply(c, type, size, first->source, second->source);

    nextRead(c, first);
    nextRead(c, second);

    removeSite(c, second, second->source);
    if (result->reads) {
      addSite(c, 0, size, result, second->source);
    }
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
  if (DebugAppend) {
    fprintf(stderr, "appendCombine\n");
  }

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
    if (DebugCompile) {
      fprintf(stderr, "TranslateEvent.compile\n");
    }

    apply(c, type, size, value->source);
    
    nextRead(c, value);

    removeSite(c, value, value->source);
    if (result->reads) {
      addSite(c, 0, size, result, value->source);
    }
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
  if (DebugAppend) {
    fprintf(stderr, "appendTranslate\n");
  }

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
    if (DebugCompile) {
      fprintf(stderr, "MemoryEvent.compile\n");
    }
    
    int indexRegister;
    if (index) {
      assert(c, index->source->type(c) == RegisterOperand);
      indexRegister = static_cast<RegisterSite*>(index->source)->register_.low;
    } else {
      indexRegister = NoRegister;
    }
    assert(c, base->source->type(c) == RegisterOperand);
    int baseRegister = static_cast<RegisterSite*>(base->source)->register_.low;

    nextRead(c, base);
    if (index) {
      nextRead(c, index);
    }

    result->target = memorySite
      (c, baseRegister, displacement, indexRegister, scale);
    addSite(c, 0, 0, result, result->target);
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
  if (DebugAppend) {
    fprintf(stderr, "appendMemory\n");
  }

  new (c->zone->allocate(sizeof(MemoryEvent)))
    MemoryEvent(c, base, displacement, index, scale, result);
}

Stack*
stack(Context* c, Value* value, unsigned size, unsigned index, Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(value, size, index, next);
}

Value*
value(Context* c, Site* site = 0, Site* target = 0)
{
  return new (c->zone->allocate(sizeof(Value))) Value(site, target);
}

void
resetStack(Context* c)
{
  unsigned i = 0;
  Stack* p = 0;
  for (Stack* s = c->state->stack; s; s = s->next) {
    Stack* n = stack(c, value(c), s->size, s->index, 0);
    n->value->sites = n->pushSite = pushSite(c, s->index);
    n->pushed = true;

    if (p) {
      p->next = n;
    } else {
      c->state->stack = n;
    }
    p = n;

    i += s->size;
  }

  c->stackReset = true;
}

void
popNow(Context* c, Event* event, Stack* stack, unsigned count, bool ignore)
{
  Stack* s = stack;
  unsigned ignored = 0;
  for (unsigned i = count; i and s;) {
    if (s->pushed) {
      if (s->value->reads and (not ignore)) {
        ::ignore(c, ignored);

        Site* target = targetOrRegister
          (c, s->size * BytesPerWord, s->value, event);

        if (DebugStack) {
          fprintf(stderr, "pop %p value: %p target: %p\n",
                  s, s->value, target);
        }

        addSite(c, stack, s->size * BytesPerWord, s->value, target);

        apply(c, Pop, BytesPerWord * s->size, target);
      } else {
        if (DebugStack) {
          fprintf(stderr, "ignore %p value: %p\n", s, s->value);
        }
          
        ignored += s->size;
      }

      removeSite(c, s->value, s->pushSite);
      s->pushSite = 0;
      s->pushed = false;
    } else {
      if (DebugStack) {
        fprintf(stderr, "%p not pushed\n", s);
      }
    }

    i -= s->size;
    s = s->next;
  }

  ::ignore(c, ignored);
}

class StackSyncEvent: public Event {
 public:
  StackSyncEvent(Context* c):
    Event(c)
  {
    for (Stack* s = stack; s; s = s->next) {
      if (s->pushEvent) s->pushEvent->active = true;
      addRead(c, s->value, s->size * BytesPerWord, 0);
    } 
  }

  StackSyncEvent(Context* c, unsigned sequence, Stack* stack):
    Event(c, sequence, stack)
  {
    for (Stack* s = stack; s; s = s->next) {
      if (s->pushEvent) s->pushEvent->active = true;
      insertRead(c, this, sequence, s->value, s->size * BytesPerWord, 0);
    }
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "StackSyncEvent.compile\n");
    }

    for (Stack* s = stack; s; s = s->next) {
      clearSites(c, s->value);
    }

    for (Stack* s = stack; s; s = s->next) {
      if (s->pushSite) {
        addSite(c, 0, s->size * BytesPerWord, s->value, s->pushSite);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      nextRead(c, r->value);
    }
  }
};

void
appendStackSync(Context* c)
{
  if (DebugAppend) {
    fprintf(stderr, "appendStackSync\n");
  }

  new (c->zone->allocate(sizeof(StackSyncEvent))) StackSyncEvent(c);
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, UnaryOperation type, Value* address):
    Event(c), type(type), address(address)
  {
    addRead(c, address, BytesPerWord, 0);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "BranchEvent.compile\n");
    }

    apply(c, type, BytesPerWord, address->source);

    nextRead(c, address);
  }

  virtual bool isBranch() { return true; };

  UnaryOperation type;
  Value* address;
};

void
appendBranch(Context* c, UnaryOperation type, Value* address)
{
  appendStackSync(c);

  if (DebugAppend) {
    fprintf(stderr, "appendBranch\n");
  }

  new (c->zone->allocate(sizeof(BranchEvent))) BranchEvent(c, type, address);

  resetStack(c);
}

class PushSite: public AbstractSite {
 public:
  PushSite(PushEvent* event): event(event) { }

  virtual Site* readTarget(Context* c, Read* r, Event* e) {
    if (r->next and (not event->active)) {
      return targetOrNull(c, r->next, e);
    } else {
      return 0;
    }
  }

  PushEvent* event;
};

Site*
pushSite(Context* c, PushEvent* e)
{
  return new (c->zone->allocate(sizeof(PushSite))) PushSite(e);
}

void
appendPush(Context* c, Stack* s)
{
  if (DebugAppend) {
    fprintf(stderr, "appendPush\n");
  }

  new (c->zone->allocate(sizeof(PushEvent))) PushEvent(c, s);
}

void
appendPush(Context* c)
{
  appendPush(c, c->state->stack);
}

class PopEvent: public Event {
 public:
  PopEvent(Context* c, unsigned count, bool ignore):
    Event(c), count(count), ignore(ignore)
  { }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "PopEvent.compile\n");
    }

    popNow(c, this, stack, count, ignore);
  }

  unsigned count;
  bool ignore;
};

void
appendPop(Context* c, unsigned count, bool ignore)
{
  if (DebugAppend) {
    fprintf(stderr, "appendPop\n");
  }

  new (c->zone->allocate(sizeof(PopEvent))) PopEvent(c, count, ignore);
}

// void
// swapRegisters(Context* c, int* ap, int* bp)
// {
//   Assembler::Register a(*ap);
//   Assembler::Register b(*bp);

//   c->assembler->apply
//     (Xor, BytesPerWord, RegisterOperand, &a, RegisterOperand, &b);
//   c->assembler->apply
//     (Xor, BytesPerWord, RegisterOperand, &b, RegisterOperand, &a);
//   c->assembler->apply
//     (Xor, BytesPerWord, RegisterOperand, &a, RegisterOperand, &b);

//   int t = *ap;
//   *ap = *bp;
//   *bp = t;
// }

Site*
readSource(Context* c, Stack* stack, Read* r, Event* e)
{
  Site* target = (r->target ? r->target->readTarget(c, r, e) : 0);

  unsigned copyCost;
  Site* site = pick(c, r->value->sites, target, &copyCost);

  if (target) {
    if (copyCost) {
      if (tryAddSite(c, stack, r->size, r->value, target)) {
        apply(c, Move, r->size, site, target);
        return target;
      } else {
        abort(c);
//         // this is a filthy hack, but I think the core idea is right:

//         assert(c, target->type(c) == RegisterOperand);

//         RegisterSite* ts = static_cast<RegisterSite*>(target);

//         for (Site* s = r->value->sites; s; s = s->next) {
//           if (s->type(c) == RegisterOperand) {
//             RegisterSite* rs = static_cast<RegisterSite*>(s);

//             RegisterSite* os = static_cast<RegisterSite*>
//               (c->registers[ts->register_.low].site);

//             assert(c, os->register_.low == ts->register_.low);
//             assert(c, os->register_.high == NoRegister);
//             assert(c, ts->register_.high == NoRegister);
//             assert(c, rs->register_.high == NoRegister);

//             swapRegisters(c, &(os->register_.low), &(rs->register_.low));

//             return rs;
//           }
//         }
      }
      
      abort(c);
    } else {
      return target;
    }
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

  for (unsigned i = 0; i < c->logicalCodeLength; ++i) {
    LogicalInstruction* li = c->logicalCode + i;
    if (li->firstEvent) {
      li->machineOffset = a->length();

      if (DebugCompile) {
        fprintf(stderr, " -- ip: %d\n", i);
      }

      for (Event* e = li->firstEvent; e; e = e->next) {
        if (e->stackReset) {
//           fprintf(stderr, "stack reset\n");
          for (Stack* s = e->stack; s; s = s->next) {
            if (s->value->sites) {
              assert(c, s->value->sites->next == 0);
              if (s->value->reads) {
                expect(c, s->value->sites->tryAcquire
                       (c, 0, s->size * BytesPerWord, s->value));
              }
            }
          }
        }

        for (Read* r = e->reads; r; r = r->eventNext) {
          r->value->source = readSource(c, e->stack, r, e);
        }

        e->compile(c);
        
        for (CodePromise* p = e->promises; p; p = p->next) {
          p->offset = a->length();
        }
      }
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
    State(c->state, c->state->stack);
}

void
saveStack(Context* c)
{
  if (c->logicalIp >= 0 and not c->logicalCode[c->logicalIp].stackSaved) {
    c->logicalCode[c->logicalIp].stackSaved = true;
    c->logicalCode[c->logicalIp].stack = c->state->stack;
  }
}

void
popState(Context* c)
{
  saveStack(c);

  c->state = new (c->zone->allocate(sizeof(State)))
    State(c->state->next->next, c->state->next->stack);
 
  resetStack(c);
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
    LogicalInstruction* p = i->immediatePredecessor;

    p->lastEvent = p->lastEvent->next
      = new (c->zone->allocate(sizeof(StackSyncEvent)))
      StackSyncEvent(c, p->lastEvent->sequence, p->stack);
  }
}

bool
used(Context* c, int r)
{
  Value* v = c->registers[r].value;
//   fprintf(stderr, "v: %p found: %d\n",
//           v, v and findSite(c, v, c->registers[r].site));
  return v and findSite(c, v, c->registers[r].site);
}

bool
usedExclusively(Context* c, int r)
{
  Value* v = c->registers[r].value;
  return used(c, r) and v->sites->next == 0;
}

int
freeRegisterExcept(Context* c, int except, bool allowAcquired)
{
  for (int i = c->assembler->registerCount() - 1; i >= 0; --i) {
    if (i != except
        and c->registers[i].refCount == 0
        and (not used(c, i)))
    {
      return i;
    }
  }

  if (allowAcquired) {
    for (int i = c->assembler->registerCount() - 1; i >= 0; --i) {
      if (i != except
          and c->registers[i].refCount == 0
          and (not usedExclusively(c, i)))
      {
        return i;
      }
    }

    for (int i = c->assembler->registerCount() - 1; i >= 0; --i) {
      if (i != except
          and c->registers[i].refCount == 0)
      {
        return i;
      }
    }
  }

  abort(c);
}

void
visit(Context* c, unsigned logicalIp)
{
  assert(c, logicalIp < c->logicalCodeLength);

  if (c->logicalIp >= 0 and (not c->stackReset)) {
    assert(c, c->logicalCode[logicalIp].immediatePredecessor == 0);
    c->logicalCode[logicalIp].immediatePredecessor
      = c->logicalCode + c->logicalIp;
  }
}

int
freeRegister(Context* c, bool allowAcquired)
{
  int r = freeRegisterExcept(c, NoRegister, allowAcquired);
//   fprintf(stderr, "free reg: %d\n", r);
  return r;
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
      expect(c, (c->registers[r].refCount == 0
                 and c->registers[r].value == 0)
             or c->registers[r].pushed);
    }
    increment(c, r);
    return r;
  }

  virtual void releaseTemporary(int r) {
    decrement(c, r);
  }

  virtual void save(int r) {
    if (c->registers[r].refCount or c->registers[r].value) {
      Assembler::Register operand(r);
      c->assembler->apply(Push, BytesPerWord, RegisterOperand, &operand);
      c->registers[r].pushed = true;
    }
  }

  virtual void restore(int r) {
    if (c->registers[r].pushed) {
      Assembler::Register operand(r);
      c->assembler->apply(Pop, BytesPerWord, RegisterOperand, &operand);
      c->registers[r].pushed = false;
    }
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
    visit(&c, logicalIp);

    c.stackReset = false;

    if (c.logicalCode[logicalIp].immediatePredecessor) {
      c.junctions = new (c.zone->allocate(sizeof(Junction)))
        Junction(logicalIp, c.junctions);
    }
  }

  virtual void startLogicalIp(unsigned logicalIp) {
    if (DebugAppend) {
      fprintf(stderr, " -- ip: %d\n", logicalIp);
    }

    visit(&c, logicalIp);

    saveStack(&c);

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
    Site* s = registerSite(&c, c.assembler->stack());
    return value(&c, s, s);
  }

  virtual Operand* base() {
    Site* s = registerSite(&c, c.assembler->base());
    return value(&c, s, s);
  }

  virtual Operand* thread() {
    Site* s = registerSite(&c, c.assembler->thread());
    return value(&c, s, s);
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
    Event* e = c.logicalCode[c.logicalIp].lastEvent;
    return e->promises = new (c.zone->allocate(sizeof(CodePromise)))
      CodePromise(&c, e->promises);
  }

  virtual void mark(Operand* label) {
    appendStackSync(&c);
    resetStack(&c);

    for (Site* s = static_cast<Value*>(label)->sites; s; s = s->next) {
      if (s->type(&c) == ConstantOperand) {
        static_cast<ConstantSite*>(s)->value.value = machineIp();
        return;
      }
    }
    abort(&c);
  }

  virtual void push(unsigned size) {
    assert(&c, ceiling(size, BytesPerWord));

    c.state->stack = ::stack
      (&c, value(&c), ceiling(size, BytesPerWord), c.state->stack);
  }

  virtual void push(unsigned size, Operand* value) {
    ::push(&c, size, static_cast<Value*>(value));
  }

  virtual Operand* pop(unsigned size) {
    return ::pop(&c, size);
  }

  virtual void pushed(unsigned count) {
    for (unsigned i = 0; i < count; ++i) {
      Value* v = value(&c);
      c.state->stack = ::stack(&c, v, 1, c.state->stack);
      c.state->stack->pushed = true;
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
                        unsigned resultSize,
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

    for (Stack* s = c.state->stack; s; s = s->next) {
      if (s->pushEvent == 0) {
        appendPush(&c, s);
      }
      s->pushEvent->active = true;
    }

    Stack* oldStack = c.state->stack;

    for (int i = argumentCount - 1; i >= 0; --i) {
      ::push(&c, argumentSizes[i], arguments[i]);
    }

    Stack* argumentStack = c.state->stack;
    c.state->stack = oldStack;

    Value* result = value(&c);
    appendCall(&c, static_cast<Value*>(address), indirection, flags,
               traceHandler, result, resultSize, argumentStack,
               index);

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
    appendMove(&c, Move4To8, 8, static_cast<Value*>(src), dst);
    return dst;
  }

  virtual void cmp(unsigned size, Operand* a, Operand* b) {
    appendCompare(&c, size, static_cast<Value*>(a),
                  static_cast<Value*>(b));
  }

  virtual void jl(Operand* address) {
    appendBranch(&c, JumpIfLess, static_cast<Value*>(address));
  }

  virtual void jg(Operand* address) {
    appendBranch(&c, JumpIfGreater, static_cast<Value*>(address));
  }

  virtual void jle(Operand* address) {
    appendBranch(&c, JumpIfLessOrEqual, static_cast<Value*>(address));
  }

  virtual void jge(Operand* address) {
    appendBranch(&c, JumpIfGreaterOrEqual, static_cast<Value*>(address));
  }

  virtual void je(Operand* address) {
    appendBranch(&c, JumpIfEqual, static_cast<Value*>(address));
  }

  virtual void jne(Operand* address) {
    appendBranch(&c, JumpIfNotEqual, static_cast<Value*>(address));
  }

  virtual void jmp(Operand* address) {
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
      *reinterpret_cast<intptr_t*>(dst + pad(c.assembler->length()) + i)
        = n->promise->value();
      i += BytesPerWord;
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
