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
class RegisterSite;
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
  
  virtual void acquire(Context*, Stack*, unsigned, Value*) { }

  virtual void release(Context*) { }

  virtual void freeze(Context*) { }

  virtual void thaw(Context*) { }

  virtual void validate(Context*, Stack*, unsigned, Value*) { }

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
  Register(int number):
    value(0), site(0), number(number), size(0), refCount(0),
    freezeCount(0), reserved(false), pushed(false)
  { }

  Value* value;
  RegisterSite* site;
  int number;
  unsigned size;
  unsigned refCount;
  unsigned freezeCount;
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
  Context(System* system, Assembler* assembler, Zone* zone, void* indirection):
    system(system),
    assembler(assembler),
    zone(zone),
    indirection(indirection),
    logicalIp(-1),
    state(new (zone->allocate(sizeof(State))) State(0, 0)),
    logicalCode(0),
    logicalCodeLength(0),
    stackOffset(0),
    registers
    (static_cast<Register**>
     (zone->allocate(sizeof(Register*) * assembler->registerCount()))),
    firstConstant(0),
    lastConstant(0),
    constantCount(0),
    nextSequence(0),
    junctions(0),
    machineCode(0),
    stackReset(false)
  {
    for (unsigned i = 0; i < assembler->registerCount(); ++i) {
      registers[i] = new (zone->allocate(sizeof(Register))) Register(i);
    }

    registers[assembler->base()]->reserved = true;
    registers[assembler->stack()]->reserved = true;
    registers[assembler->thread()]->reserved = true;
  }

  System* system;
  Assembler* assembler;
  Zone* zone;
  void* indirection;
  int logicalIp;
  State* state;
  LogicalInstruction* logicalCode;
  unsigned logicalCodeLength;
  unsigned stackOffset;
  Register** registers;
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
    next(0), stack(c->state->stack), promises(0), reads(0), readCount(0),
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
    next(0), stack(stack), promises(0), reads(0), readCount(0),
    sequence(sequence), stackReset(false)
  { }

  virtual ~Event() { }

  virtual void compile(Context* c) = 0;

  virtual bool skipMove(unsigned) { return false; }

  Event* next;
  Stack* stack;
  CodePromise* promises;
  Read* reads;
  unsigned readCount;
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

void
addSite(Context* c, Stack* stack, unsigned size, Value* v, Site* s)
{
  if (not findSite(c, v, s)) {
//     fprintf(stderr, "add site %p (%d) to %p\n", s, s->type(c), v);
    s->acquire(c, stack, size, v);
    s->next = v->sites;
    v->sites = s;
  }
}

void
removeSite(Context* c, Value* v, Site* s)
{
  for (Site** p = &(v->sites); *p;) {
    if (s == *p) {
//       fprintf(stderr, "remove site %p (%d) from %p\n", s, s->type(c), v);
      s->release(c);
      *p = (*p)->next;
      break;
    } else {
      p = &((*p)->next);
    }
  }
}

void
removeMemorySites(Context* c, Value* v)
{
  for (Site** p = &(v->sites); *p;) {
    if ((*p)->type(c) == MemoryOperand) {
//       fprintf(stderr, "remove site %p (%d) from %p\n", *p, (*p)->type(c), v);
      (*p)->release(c);
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

void
freeze(Register* r)
{
  if (DebugRegisters) {
    fprintf(stderr, "freeze %d to %d\n", r->number, r->freezeCount + 1);
  }

  ++ r->freezeCount;
}

void
thaw(Register* r)
{
  if (DebugRegisters) {
    fprintf(stderr, "thaw %d to %d\n", r->number, r->freezeCount - 1);
  }

  -- r->freezeCount;
}

Register*
acquire(Context* c, uint32_t mask, Stack* stack, unsigned newSize,
        Value* newValue, RegisterSite* newSite);

void
release(Context* c, Register* r);

Register*
validate(Context* c, uint32_t mask, Stack* stack, unsigned size,
         Value* value, RegisterSite* site, Register* current);

class RegisterSite: public Site {
 public:
  RegisterSite(uint64_t mask, Register* low = 0, Register* high = 0):
    mask(mask), low(low), high(high), register_(NoRegister, NoRegister)
  { }

  void sync(Context* c UNUSED) {
    assert(c, low);

    register_.low = low->number;
    register_.high = (high? high->number : NoRegister);
  }

  virtual unsigned copyCost(Context* c, Site* s) {
    sync(c);

    if (s and
        (this == s or
         (s->type(c) == RegisterOperand
          and (static_cast<RegisterSite*>(s)->mask
               & (static_cast<uint64_t>(1) << register_.low))
          and (register_.high == NoRegister
               or (static_cast<RegisterSite*>(s)->mask
                   & (static_cast<uint64_t>(1) << (register_.high + 32)))))))
    {
      return 0;
    } else {
      return 2;
    }
  }

  virtual void acquire(Context* c, Stack* stack, unsigned size, Value* v) {
    validate(c, stack, size, v);
  }

  virtual void release(Context* c) {
    assert(c, low);

    ::release(c, low);
    if (high) {
      ::release(c, high);
    }
  }

  virtual void freeze(Context* c UNUSED) {
    assert(c, low);

    ::freeze(low);
    if (high) {
      ::freeze(high);
    }
  }

  virtual void thaw(Context* c UNUSED) {
    assert(c, low);

    ::thaw(low);
    if (high) {
      ::thaw(high);
    }
  }

  virtual void validate(Context* c, Stack* stack, unsigned size, Value* v) {
    low = ::validate(c, mask, stack, size, v, this, low);
    if (size > BytesPerWord) {
      ::freeze(low);
      high = ::validate(c, mask >> 32, stack, size, v, this, high);
      ::thaw(low);
    }
  }

  virtual OperandType type(Context*) {
    return RegisterOperand;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context* c) {
    sync(c);
    return &register_;
  }

  uint64_t mask;
  Register* low;
  Register* high;
  Assembler::Register register_;
};

RegisterSite*
registerSite(Context* c, int low, int high = NoRegister)
{
  assert(c, low != NoRegister);
  assert(c, low < static_cast<int>(c->assembler->registerCount()));
  assert(c, high == NoRegister
         or high < static_cast<int>(c->assembler->registerCount()));

  Register* hr;
  if (high == NoRegister) {
    hr = 0;
  } else {
    hr = c->registers[high];
  }
  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(~static_cast<uint64_t>(0), c->registers[low], hr);
}

RegisterSite*
freeRegisterSite(Context* c, uint64_t mask = ~static_cast<uint64_t>(0))
{
  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(mask);
}

RegisterSite*
fixedRegisterSite(Context* c, int low, int high = NoRegister)
{
  uint64_t mask = static_cast<uint64_t>(1) << low;
  if (high != NoRegister) {
    mask |= static_cast<uint64_t>(1) << (high + 32);
  }

  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(mask);
}

Register*
increment(Context* c, int i)
{
  Register* r = c->registers[i];

  if (DebugRegisters) {
    fprintf(stderr, "increment %d to %d\n", r->number, r->refCount + 1);
  }

  ++ r->refCount;

  return r;
}

void
decrement(Context* c UNUSED, Register* r)
{
  assert(c, r->refCount > 0);

  if (DebugRegisters) {
    fprintf(stderr, "decrement %d to %d\n", r->number, r->refCount - 1);
  }

  -- r->refCount;
}

class MemorySite: public Site {
 public:
  MemorySite(int base, int offset, int index, unsigned scale):
    base(0), index(0), value(base, offset, index, scale)
  { }

  void sync(Context* c UNUSED) {
    assert(c, base);

    value.base = base->number;
    value.index = (index? index->number : NoRegister);
  }

  virtual unsigned copyCost(Context* c, Site* s) {
    sync(c);

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

  virtual void acquire(Context* c, Stack*, unsigned, Value*) {
    base = increment(c, value.base);
    if (value.index != NoRegister) {
      index = increment(c, value.index);
    }
  }

  virtual void release(Context* c) {
    decrement(c, base);
    if (index) {
      decrement(c, index);
    }
  }

  virtual OperandType type(Context*) {
    return MemoryOperand;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context* c) {
    sync(c);
    return &value;
  }

  Register* base;
  Register* index;
  Assembler::Memory value;
};

MemorySite*
memorySite(Context* c, int base, int offset, int index, unsigned scale)
{
  return new (c->zone->allocate(sizeof(MemorySite)))
    MemorySite(base, offset, index, scale);
}

bool
matchRegister(Context* c UNUSED, Site* s, uint64_t mask)
{
  assert(c, s->type(c) == RegisterOperand);

  RegisterSite* r = static_cast<RegisterSite*>(s);
  return ((static_cast<uint64_t>(1) << r->register_.low) & mask)
    and (r->register_.high == NoRegister
         or ((static_cast<uint64_t>(1) << (r->register_.high + 32)) & mask));
}

bool
match(Context* c, Site* s, uint8_t typeMask, uint64_t registerMask)
{
  OperandType t = s->type(c);
  return ((1 << t) & typeMask)
    and (t != RegisterOperand or matchRegister(c, s, registerMask));
}

Site*
targetOrNull(Context* c, Read* r)
{
  Value* v = r->value;
  if (v->target) {
    return v->target;
  } else if (r->target) {
    return r->target->readTarget(c, r);
  } else {
    return 0;
  }
}

Site*
targetOrNull(Context* c, Value* v)
{
  if (v->target) {
    return v->target;
  } else if (v->reads and v->reads->target) {
    return v->reads->target->readTarget(c, v->reads);
  } else {
    return 0;
  }
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

class VirtualSite: public AbstractSite {
 public:
  VirtualSite(Value* value, uint8_t typeMask, uint64_t registerMask):
    value(value), registerMask(registerMask), typeMask(typeMask)
  { }

  virtual Site* readTarget(Context* c, Read* r) {
    if (value) {
      Site* s = targetOrNull(c, value);
      if (s and match(c, s, typeMask, registerMask)) {
        return s;
      }
    }

    Site* site = 0;
    unsigned copyCost = 0xFFFFFFFF;
    for (Site* s = r->value->sites; s; s = s->next) {
      if (match(c, s, typeMask, registerMask)) {
        unsigned v = s->copyCost(c, 0);
        if (v < copyCost) {
          site = s;
          copyCost = v;
        }
      }
    }

    if (site) {
      return site;
    } else {
      assert(c, typeMask & (1 << RegisterOperand));
      return freeRegisterSite(c, registerMask);
    }
  }

  Value* value;
  uint64_t registerMask;
  uint8_t typeMask;
};

VirtualSite*
virtualSite(Context* c, Value* v = 0,
            uint8_t typeMask = ~static_cast<uint8_t>(0),
            uint64_t registerMask = ~static_cast<uint64_t>(0))
{
  return new (c->zone->allocate(sizeof(VirtualSite)))
    VirtualSite(v, typeMask, registerMask);
}

VirtualSite*
anyRegisterSite(Context* c)
{
  return virtualSite(c, 0, 1 << RegisterOperand, ~static_cast<uint64_t>(0));
}

Site*
targetOrRegister(Context* c, Value* v)
{
  Site* s = targetOrNull(c, v);
  if (s) {
    return s;
  } else {
    return freeRegisterSite(c);
  }
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

      removeMemorySites(c, s->value);

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
trySteal(Context* c, Register* r, Stack* stack)
{
  assert(c, r->refCount == 0);

  Value* v = r->value;

  if (DebugRegisters) {
    fprintf(stderr, "try steal %d from %p: next: %p\n",
            r->number, v, v->sites->next);
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
      if (DebugRegisters) {
        fprintf(stderr, "push %p\n", v);
      }
      pushNow(c, start, count);
    } else {
      if (DebugRegisters) {
        fprintf(stderr, "unable to steal %d from %p\n", r->number, v);
      }
      return false;
    }
  }

  removeSite(c, v, r->site);

  return true;
}

bool
used(Context* c, Register* r)
{
  Value* v = r->value;
  return v and findSite(c, v, r->site);
}

bool
usedExclusively(Context* c, Register* r)
{
  return used(c, r) and r->value->sites->next == 0;
}

unsigned
registerCost(Context* c, Register* r)
{
  if (r->reserved or r->freezeCount) {
    return 6;
  }

  unsigned cost = 0;

  if (used(c, r)) {
    ++ cost;
    if (usedExclusively(c, r)) {
      cost += 2;
    }
  }

  if (r->refCount) {
    cost += 2;
  }

  return cost;
}

Register*
pickRegister(Context* c, uint32_t mask)
{
  Register* register_ = 0;
  unsigned cost = 5;
  for (int i = c->assembler->registerCount() - 1; i >= 0; --i) {
    if ((1 << i) & mask) {
      Register* r = c->registers[i];
      if ((static_cast<uint32_t>(1) << i) == mask) {
        return r;
      }

      unsigned myCost = registerCost(c, r);
      if (myCost < cost) {
        register_ = r;
        cost = myCost;
      }
    }
  }

  expect(c, register_);

  return register_;
}

void
swap(Context* c, Register* a, Register* b)
{
  assert(c, a != b);
  assert(c, a->number != b->number);

  Assembler::Register ar(a->number);
  Assembler::Register br(b->number);
  c->assembler->apply
    (Swap, BytesPerWord, RegisterOperand, &ar, RegisterOperand, &br);
  
  c->registers[a->number] = b;
  c->registers[b->number] = a;

  int t = a->number;
  a->number = b->number;
  b->number = t;
}

Register*
replace(Context* c, Stack* stack, Register* r)
{
  uint32_t mask = (r->freezeCount? r->site->mask : ~0);

  freeze(r);
  Register* s = acquire(c, mask, stack, r->size, r->value, r->site);
  thaw(r);

  if (DebugRegisters) {
    fprintf(stderr, "replace %d with %d\n", r->number, s->number);
  }

  swap(c, r, s);

  return s;
}

Register*
acquire(Context* c, uint32_t mask, Stack* stack, unsigned newSize,
        Value* newValue, RegisterSite* newSite)
{
  Register* r = pickRegister(c, mask);

  if (r->reserved) return r;

  if (DebugRegisters) {
    fprintf(stderr, "acquire %d, value %p, site %p freeze count %d ref count %d used %d used exclusively %d\n",
            r->number, newValue, newSite, r->freezeCount, r->refCount, used(c, r), usedExclusively(c, r));
  }

  if (r->refCount) {
    r = replace(c, stack, r);
  } else {
    Value* oldValue = r->value;
    if (oldValue
        and oldValue != newValue
        and findSite(c, oldValue, r->site))
    {
      if (not trySteal(c, r, stack)) {
        r = replace(c, stack, r);
      }
    }
  }

  r->size = newSize;
  r->value = newValue;
  r->site = newSite;

  return r;
}

void
release(Context*, Register* r)
{
  if (DebugRegisters) {
    fprintf(stderr, "release %d\n", r->number);
  }

  r->size = 0;
  r->value = 0;
  r->site = 0;  
}

Register*
validate(Context* c, uint32_t mask, Stack* stack, unsigned size,
         Value* value, RegisterSite* site, Register* current)
{
  if (current and (mask & (1 << current->number))) {
    if (current->reserved or current->value == value) {
      return current;
    }

    if (current->value == 0) {
      current->size = size;
      current->value = value;
      current->site = site;
      return current;
    } else {
      abort(c);
    }
  }

  Register* r = acquire(c, mask, stack, size, value, site);

  if (current and current != r) {
    release(c, current);
    
    Assembler::Register rr(r->number);
    Assembler::Register cr(current->number);
    c->assembler->apply
      (Move, BytesPerWord, RegisterOperand, &cr, RegisterOperand, &rr);
  }

  return r;
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
insertRead(Context* c, Event* event, int sequence, Value* v,
           unsigned size, Site* target)
{
  Read* r = new (c->zone->allocate(sizeof(Read)))
    Read(size, v, target, 0, event, event->reads);
  event->reads = r;
  ++ event->readCount;

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

  virtual bool skipMove(unsigned size) {
    return active and size >= BytesPerWord;
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
  CallEvent(Context* c, Value* address, unsigned flags,
            TraceHandler* traceHandler, Value* result, unsigned resultSize,
            Stack* argumentStack, unsigned argumentCount):
    Event(c),
    address(address),
    traceHandler(traceHandler),
    result(result),
    flags(flags),
    resultSize(resultSize),
    argumentFootprint(0)
  {
    uint32_t mask = ~0;
    Stack* s = argumentStack;
    unsigned index = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Site* target;
      if (index < c->assembler->argumentRegisterCount()) {
        int r = c->assembler->argumentRegister(index);
        target = fixedRegisterSite(c, r);
        mask &= ~(1 << r);
      } else {
        target = 0;
        s->pushEvent->active = true;
        argumentFootprint += s->size;
      }
      addRead(c, s->value, s->size * BytesPerWord, target);
      index += s->size;
      s = s->next;
    }

    if (flags & Compiler::Indirect) {
      int r = c->assembler->returnLow();
      addRead(c, address, BytesPerWord, fixedRegisterSite(c, r));
      mask &= ~(1 << r);
    } else {
      addRead(c, address, BytesPerWord, virtualSite
              (c, 0, ~0, (static_cast<uint64_t>(mask) << 32) | mask));
    }

    for (Stack* s = stack; s; s = s->next) {
      s->pushEvent->active = true;
      addRead(c, s->value, s->size * BytesPerWord, virtualSite
              (c, 0, ~0, (static_cast<uint64_t>(mask) << 32) | mask));
    }
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CallEvent.compile\n");
    }

    pushNow(c, stack);
    
    UnaryOperation type = ((flags & Compiler::Aligned) ? AlignedCall : Call);
    if (flags & Compiler::Indirect) {
      apply(c, type, BytesPerWord,
            constantSite(c, reinterpret_cast<intptr_t>(c->indirection)));
    } else {
      apply(c, type, BytesPerWord, address->source);
    }

    if (traceHandler) {
      traceHandler->handleTrace
        (new (c->zone->allocate(sizeof(CodePromise)))
         CodePromise(c, c->assembler->length()));
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

    if (argumentFootprint and ((flags & Compiler::NoReturn) == 0)) {
      ignore(c, argumentFootprint);
    }
  }

  Value* address;
  TraceHandler* traceHandler;
  Value* result;
  unsigned flags;
  unsigned resultSize;
  unsigned argumentFootprint;
};

void
appendCall(Context* c, Value* address, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount)
{
  if (DebugAppend) {
    fprintf(stderr, "appendCall\n");
  }

  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, flags, traceHandler, result,
              resultSize, argumentStack, argumentCount);
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, unsigned size, Value* value):
    Event(c), value(value)
  {
    if (value) {
      addRead(c, value, size, fixedRegisterSite
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
            Value* dst, Site* srcTarget, VirtualSite* dstTarget):
    Event(c), type(type), size(size), src(src), dst(dst), dstTarget(dstTarget)
  {
    addRead(c, src, size, srcTarget);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "MoveEvent.compile\n");
    }

    Site* target;
    unsigned cost;
    if (type == Move
        and dst->reads
        and next == dst->reads->event
        and dst->reads->event->skipMove(size))
    {
      target = src->source;
      cost = 0;
    } else {
      target = targetOrRegister(c, dst);
      cost = src->source->copyCost(c, target);
      if (cost == 0) {
        target = src->source;
      }
    }

    if (target == src->source) {
      nextRead(c, src);
    }

    if (dst->reads) {
      addSite(c, stack, size, dst, target);
    }

    if (cost or type != Move) {
      if (match(c, target, dstTarget->typeMask, dstTarget->registerMask)) {
        apply(c, type, size, src->source, target);
      } else {
        assert(c, dstTarget->typeMask & (1 << RegisterOperand));

        Site* tmpTarget = freeRegisterSite(c, dstTarget->registerMask);

        addSite(c, stack, size, dst, tmpTarget);

        apply(c, type, size, src->source, tmpTarget);

        if (dst->reads == 0) {
          removeSite(c, dst, tmpTarget);

          apply(c, Move, size, tmpTarget, target);
        } else {
          removeSite(c, dst, target);          
        }
      }
    }

    if (dst->reads == 0) {
      removeSite(c, dst, target);
    }

    if (target != src->source) {
      nextRead(c, src);
    }
  }

  BinaryOperation type;
  unsigned size;
  Value* src;
  Value* dst;
  VirtualSite* dstTarget;
};

void
appendMove(Context* c, BinaryOperation type, unsigned size, Value* src,
           Value* dst)
{
  if (DebugAppend) {
    fprintf(stderr, "appendMove\n");
  }

  VirtualSite* srcTarget = virtualSite(c, dst);
  VirtualSite* dstTarget = virtualSite(c);
  uintptr_t procedure;

  c->assembler->plan(type, size,
                     &(srcTarget->typeMask), &(srcTarget->registerMask),
                     &(dstTarget->typeMask), &(dstTarget->registerMask),
                     &procedure);

  assert(c, procedure == 0); // todo

  new (c->zone->allocate(sizeof(MoveEvent)))
    MoveEvent(c, type, size, src, dst, srcTarget, dstTarget);
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

void
maybePreserve(Context* c, Stack* stack, unsigned size, Value* v, Site* s)
{
  if (v->reads->next and v->sites->next == 0) {
    assert(c, v->sites == s);
    Site* r = targetOrNull(c, v->reads->next);
    if (r == 0 or r == s) r = freeRegisterSite(c);
    addSite(c, stack, size, v, r);
    apply(c, Move, size, s, r);    
  }
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, BinaryOperation type, unsigned size, Value* first,
               Value* second, Value* result, Site* firstTarget,
               Site* secondTarget):
    Event(c), type(type), size(size), first(first), second(second),
    result(result)
  {
    // todo: we should really specify the sizes of each operand
    // seperately for binary operations.  The following is a hack
    // until then.
    unsigned firstSize;
    switch (type) {
    case ShiftLeft:
    case ShiftRight:
    case UnsignedShiftRight:
      firstSize = 4;
      break;

    default:
      firstSize = size;
      break;
    }

    addRead(c, first, firstSize, firstTarget);
    addRead(c, second, size, secondTarget);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CombineEvent.compile\n");
    }

    maybePreserve(c, stack, size, second, second->source);

    apply(c, type, size, first->source, second->source);

    nextRead(c, first);
    nextRead(c, second);

    removeSite(c, second, second->source);
    if (result->reads) {
      addSite(c, 0, 0, result, second->source);
    }
  }

  BinaryOperation type;
  unsigned size;
  Value* first;
  Value* second;
  Value* result;
};

void
appendStackSync(Context* c);

Value*
value(Context* c, Site* site = 0, Site* target = 0)
{
  return new (c->zone->allocate(sizeof(Value))) Value(site, target);
}

void
appendCombine(Context* c, BinaryOperation type, unsigned size, Value* first,
              Value* second, Value* result)
{
  VirtualSite* firstTarget = virtualSite(c);
  VirtualSite* secondTarget = virtualSite(c, result);
  uintptr_t procedure;

  c->assembler->plan(type, size,
                     &(firstTarget->typeMask), &(firstTarget->registerMask),
                     &(secondTarget->typeMask), &(secondTarget->registerMask),
                     &procedure);

  if (procedure) {
    secondTarget->value = 0;

    Stack* oldStack = c->state->stack;

    ::push(c, size, first);
    ::push(c, size, second);

    Stack* argumentStack = c->state->stack;
    c->state->stack = oldStack;

    appendCall(c, value(c, constantSite(c, procedure)), Compiler::Indirect,
               0, result, size, argumentStack, 2);
  } else {
    if (DebugAppend) {
      fprintf(stderr, "appendCombine\n");
    }

    new (c->zone->allocate(sizeof(CombineEvent)))
      CombineEvent(c, type, size, first, second, result, firstTarget,
                   secondTarget);
  }
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, UnaryOperation type, unsigned size, Value* value,
                 Value* result, Site* target):
    Event(c), type(type), size(size), value(value), result(result)
  {
    addRead(c, value, size, target);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "TranslateEvent.compile\n");
    }

    maybePreserve(c, stack, size, value, value->source);

    apply(c, type, size, value->source);
    
    nextRead(c, value);

    removeSite(c, value, value->source);
    if (result->reads) {
      addSite(c, 0, 0, result, value->source);
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

  VirtualSite* target = virtualSite(c, result);
  uintptr_t procedure;

  c->assembler->plan
    (type, size, &(target->typeMask), &(target->registerMask), &procedure);

  assert(c, procedure == 0); // todo

  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent(c, type, size, value, result, target);
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
      if (BytesPerWord == 8) {
        apply(c, Move4To8, 8, index->source, index->source);
      }

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
popNow(Context* c, Stack* stack, unsigned count, bool ignore)
{
  Stack* s = stack;
  unsigned ignored = 0;
  for (unsigned i = count; i and s;) {
    if (s->pushed) {
      removeSite(c, s->value, s->pushSite);
      s->pushSite = 0;
      s->pushed = false;

      if (s->value->reads and (not ignore)) {
        ::ignore(c, ignored);

        Site* target = targetOrRegister(c, s->value);

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

  virtual Site* readTarget(Context* c, Read* r) {
    if (r->next and (not event->active)) {
      return targetOrNull(c, r->next);
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

    popNow(c, stack, count, ignore);
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

Site*
readSource(Context* c, Stack* stack, Read* r)
{
  if (r->value->sites == 0) {
    return 0;
  }

  Site* target = (r->target ? r->target->readTarget(c, r) : 0);

  unsigned copyCost;
  Site* site = pick(c, r->value->sites, target, &copyCost);

  if (target and copyCost) {
    addSite(c, stack, r->size, r->value, target);
    apply(c, Move, r->size, site, target);
    return target;
  } else {
    site->validate(c, stack, r->size, r->value);
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
              s->value->sites->acquire(c, 0, s->size * BytesPerWord, s->value);
            }
          }
        }

        Site* sites[e->readCount];
        unsigned si = 0;
        for (Read* r = e->reads; r; r = r->eventNext) {
          r->value->source = readSource(c, e->stack, r);

          if (r->value->source) {
            assert(c, si < e->readCount);
            sites[si++] = r->value->source;
            r->value->source->freeze(c);
          }
        }

        while (si) {
          sites[--si]->thaw(c);
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
  if (DebugAppend) {
    unsigned count = 0; for (State* s = c->state; s; s = s->next) ++ count;
    fprintf(stderr, "push at level %d\n", count);
    count = 0; for (Stack* s = c->state->stack; s; s = s->next) ++ count;
    fprintf(stderr, "stack count: %d\n", count);
  }

  c->state = new (c->zone->allocate(sizeof(State)))
    State(c->state, c->state->stack);
}

void
saveStack(Context* c)
{
  if (c->logicalIp >= 0 and not c->logicalCode[c->logicalIp].stackSaved) {
    c->logicalCode[c->logicalIp].stackSaved = true;
    c->logicalCode[c->logicalIp].stack = c->state->stack;

    if (DebugAppend) {
      unsigned count = 0;
      for (Stack* s = c->state->stack; s; s = s->next) ++ count;
      fprintf(stderr, "stack count after ip %d: %d\n", c->logicalIp, count);
    }
  }
}

void
popState(Context* c)
{
  c->state = new (c->zone->allocate(sizeof(State)))
    State(c->state->next->next, c->state->next->stack);
 
  if (DebugAppend) {
    unsigned count = 0; for (State* s = c->state; s; s = s->next) ++ count;
    fprintf(stderr, "pop to level %d\n", count);
    count = 0; for (Stack* s = c->state->stack; s; s = s->next) ++ count;
    fprintf(stderr, "stack count: %d\n", count);
  }
}

Stack*
stack(Context* c, Value* value, unsigned size, Stack* next)
{
  return stack(c, value, size, (next ? next->index + next->size : 0), next);
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

class Client: public Assembler::Client {
 public:
  Client(Context* c): c(c) { }

  virtual int acquireTemporary(uint32_t mask) {
    int r = pickRegister(c, mask)->number;
    save(r);
    increment(c, r);
    return r;
  }

  virtual void releaseTemporary(int r) {
    decrement(c, c->registers[r]);
    restore(r);
  }

  virtual void save(int r) {
    if (c->registers[r]->refCount or c->registers[r]->value) {
      Assembler::Register operand(r);
      c->assembler->apply(Push, BytesPerWord, RegisterOperand, &operand);
      c->registers[r]->pushed = true;
    }
  }

  virtual void restore(int r) {
    if (c->registers[r]->pushed) {
      Assembler::Register operand(r);
      c->assembler->apply(Pop, BytesPerWord, RegisterOperand, &operand);
      c->registers[r]->pushed = false;
    }
  }

  Context* c;
};

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Assembler* assembler, Zone* zone, void* indirection):
    c(s, assembler, zone, indirection), client(&c)
  {
    assembler->setClient(&client);
  }

  virtual void pushState() {
    ::pushState(&c);
  }

  virtual void popState() {
    ::popState(&c);
  }

  virtual void saveStack() {
    ::saveStack(&c);
  }

  virtual void resetStack() {
    ::resetStack(&c);
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

    ::saveStack(&c);

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
    ::resetStack(&c);

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
//       v->sites = pushSite(&c, c.state->stack->index);
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
      i -= s->size;
      s = s->next;
    }
    assert(&c, s->size == ceiling(size, BytesPerWord));
    return s->value;
  }

  virtual Operand* call(Operand* address,
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

    for (int i = index - 1; i >= 0; --i) {
      ::push(&c, argumentSizes[i], arguments[i]);
    }

    Stack* argumentStack = c.state->stack;
    c.state->stack = oldStack;

    Value* result = value(&c);
    appendCall(&c, static_cast<Value*>(address), flags,
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
makeCompiler(System* system, Assembler* assembler, Zone* zone,
             void* indirection)
{
  return new (zone->allocate(sizeof(MyCompiler)))
    MyCompiler(system, assembler, zone, indirection);
}

} // namespace vm
