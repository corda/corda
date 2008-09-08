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

const bool DebugAppend = true;
const bool DebugCompile = true;
const bool DebugStack = false;
const bool DebugRegisters = false;

const int AnyFrameIndex = -2;
const int NoFrameIndex = -1;

class Context;
class Value;
class Stack;
class Site;
class RegisterSite;
class Event;
class PushEvent;
class Read;
class Block;

void NO_RETURN abort(Context*);

void
apply(Context* c, UnaryOperation op,
      unsigned s1Size, Site* s1);

void
apply(Context* c, BinaryOperation op,
      unsigned s1Size, Site* s1,
      unsigned s2Size, Site* s2);

void
apply(Context* c, TernaryOperation op,
      unsigned s1Size, Site* s1,
      unsigned s2Size, Site* s2,
      unsigned s3Size, Site* s3);

enum ConstantCompare {
  CompareNone,
  CompareLess,
  CompareGreater,
  CompareEqual
};

class Cell {
 public:
  Cell(Cell* next, void* value): next(next), value(value) { }

  Cell* next;
  void* value;
};

class Site {
 public:
  Site(): next(0) { }
  
  virtual ~Site() { }

  virtual Site* readTarget(Context*, Read*) { return this; }

  virtual unsigned copyCost(Context*, Site*) = 0;

  virtual bool match(Context*, uint8_t, uint64_t, int) = 0;
  
  virtual void acquire(Context*, Stack*, Value**, unsigned, Value*) { }

  virtual void release(Context*) { }

  virtual void freeze(Context*) { }

  virtual void thaw(Context*) { }

  virtual OperandType type(Context*) = 0;

  virtual Assembler::Operand* asAssemblerOperand(Context*) = 0;

  Site* next;
};

class Stack: public Compiler::StackElement {
 public:
  Stack(unsigned index, unsigned size, Value* value, Stack* next):
    index(index), size(size), padding(0), value(value), next(next)
  { }

  unsigned index;
  unsigned size;
  unsigned padding;
  Value* value;
  Stack* next;
};

class State {
 public:
  State(State* next, Stack* stack, Value** locals):
    stack(stack),
    locals(locals),
    next(next)
  { }

  Stack* stack;
  Value** locals;
  State* next;
};

class LogicalInstruction {
 public:
  LogicalInstruction(int index):
    firstEvent(0), lastEvent(0), immediatePredecessor(0), stack(0), locals(0),
    machineOffset(0), index(index), stackSaved(false)
  { }

  Event* firstEvent;
  Event* lastEvent;
  LogicalInstruction* immediatePredecessor;
  Stack* stack;
  Value** locals;
  Assembler::Offset* machineOffset;
  int index;
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

class Read {
 public:
  Read():
    next(0), value(0), event(0), eventNext(0)
  { }

  virtual ~Read() { }

  virtual Site* pickSite(Context* c, Value* v) = 0;

  virtual Site* allocateSite(Context* c) = 0;

  virtual void intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex) = 0;
  
  virtual bool valid() = 0;

  virtual unsigned size(Context* c) = 0;
  
  Read* next;
  Value* value;
  Event* event;
  Read* eventNext;
};

int
intersectFrameIndexes(int a, int b)
{
  if (a == NoFrameIndex or b == NoFrameIndex) return NoFrameIndex;
  if (a == AnyFrameIndex) return b;
  if (b == AnyFrameIndex) return a;
  if (a == b) return a;
  return NoFrameIndex;
}

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

enum Pass {
  ScanPass,
  CompilePass
};

class Context {
 public:
  Context(System* system, Assembler* assembler, Zone* zone,
          Compiler::Client* client):
    system(system),
    assembler(assembler),
    arch(assembler->arch()),
    zone(zone),
    client(client),
    state(new (zone->allocate(sizeof(State))) State(0, 0, 0)),
    logicalCode(0),
    registers
    (static_cast<Register**>
     (zone->allocate(sizeof(Register*) * arch->registerCount()))),
    firstConstant(0),
    lastConstant(0),
    machineCode(0),
    firstEvent(0),
    lastEvent(0),
    logicalIp(-1),
    constantCount(0),
    nextSequence(0),
    logicalCodeLength(0),
    parameterFootprint(0),
    localFootprint(0),
    maxStackFootprint(0),
    stackPadding(0),
    constantCompare(CompareNone),
    pass(ScanPass)
  {
    for (unsigned i = 0; i < arch->registerCount(); ++i) {
      registers[i] = new (zone->allocate(sizeof(Register))) Register(i);
      registers[i]->reserved = arch->reserved(i);
    }
  }

  System* system;
  Assembler* assembler;
  Assembler::Architecture* arch;
  Zone* zone;
  Compiler::Client* client;
  State* state;
  LogicalInstruction** logicalCode;
  Register** registers;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  uint8_t* machineCode;
  Event* firstEvent;
  Event* lastEvent;
  int logicalIp;
  unsigned constantCount;
  unsigned nextSequence;
  unsigned logicalCodeLength;
  unsigned parameterFootprint;
  unsigned localFootprint;
  unsigned maxStackFootprint;
  unsigned stackPadding;
  ConstantCompare constantCompare;
  Pass pass;
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
  CodePromise(Context* c, CodePromise* next):
    c(c), offset(0), next(next)
  { }

  CodePromise(Context* c, Assembler::Offset* offset):
    c(c), offset(offset), next(0)
  { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>(c->machineCode + offset->value());
    }
    
    abort(c);
  }

  virtual bool resolved() {
    return c->machineCode != 0 and offset and offset->resolved();
  }

  Context* c;
  Assembler::Offset* offset;
  CodePromise* next;
};

unsigned
machineOffset(Context* c, int logicalIp)
{
  for (unsigned n = logicalIp; n < c->logicalCodeLength; ++n) {
    LogicalInstruction* i = c->logicalCode[n];
    if (i and i->machineOffset) return i->machineOffset->value();
  }

  abort(c);
}

class IpPromise: public Promise {
 public:
  IpPromise(Context* c, int logicalIp):
    c(c),
    logicalIp(logicalIp)
  { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>
        (c->machineCode + machineOffset(c, logicalIp));
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

Cell*
cons(Context* c, void* value, Cell* next)
{
  return new (c->zone->allocate(sizeof(Cell))) Cell(next, value);
}

class Event {
 public:
  Event(Context* c):
    next(0), stack(c->state->stack), locals(c->state->locals), promises(0),
    reads(0), junctionSites(0), savedSites(0), predecessors(0), successors(0),
    block(0), logicalInstruction(c->logicalCode[c->logicalIp]), readCount(0),
    sequence(c->nextSequence++)
  {
    assert(c, c->logicalIp >= 0);

    if (c->lastEvent) {
      c->lastEvent->next = this;
      predecessors = cons(c, c->lastEvent, 0);
      c->lastEvent->successors = cons(c, this, c->lastEvent->successors);
    } else {
      c->firstEvent = this;
    }
    c->lastEvent = this;

    if (logicalInstruction->firstEvent == 0) {
      logicalInstruction->firstEvent = this;
    }
    logicalInstruction->lastEvent = this;
  }

  Event(Context*, unsigned sequence, Stack* stack, Value** locals):
    next(0), stack(stack), locals(locals), promises(0), reads(0),
    junctionSites(0), savedSites(0), predecessors(0), successors(0), block(0),
    logicalInstruction(0), readCount(0), sequence(sequence)
  { }

  virtual ~Event() { }

  virtual void compile(Context* c) = 0;

  virtual void compilePostsync(Context*) { }

  Event* next;
  Stack* stack;
  Value** locals;
  CodePromise* promises;
  Read* reads;
  Site** junctionSites;
  Site** savedSites;
  Cell* predecessors;
  Cell* successors;
  Block* block;
  LogicalInstruction* logicalInstruction;
  unsigned readCount;
  unsigned sequence;
};

unsigned
alignedFrameSize(Context* c)
{
  return c->arch->alignFrameSize
    (c->localFootprint
     - c->parameterFootprint
     + c->maxStackFootprint);
}

int
localOffset(Context* c, int v)
{
  int parameterFootprint = c->parameterFootprint;
  int frameSize = alignedFrameSize(c);

  if (v < parameterFootprint) {
    return (frameSize
            + parameterFootprint
            + (c->arch->frameFooterSize() * 2)
            + c->arch->frameHeaderSize()
            - v) * BytesPerWord;
  } else {
    return (frameSize
            + parameterFootprint
            + c->arch->frameFooterSize()
            - v) * BytesPerWord;
  }
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
addSite(Context* c, Stack* stack, Value** locals, unsigned size, Value* v,
        Site* s)
{
  if (not findSite(c, v, s)) {
//     fprintf(stderr, "add site %p (%d) to %p\n", s, s->type(c), v);
    s->acquire(c, stack, locals, size, v);
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

bool
valid(Read* r)
{
  return r and r->valid();
}

bool
live(Value* v)
{
  return valid(v->reads);
}

void
nextRead(Context* c, Value* v)
{
//   fprintf(stderr, "pop read %p from %p; next: %p\n", v->reads, v, v->reads->next);

  v->reads = v->reads->next;
  if (not live(v)) {
    clearSites(c, v);
  }
}

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : 1);
  }

  virtual bool match(Context*, uint8_t typeMask, uint64_t, int) {
    return typeMask & (1 << ConstantOperand);
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

  virtual bool match(Context*, uint8_t typeMask, uint64_t, int) {
    return typeMask & (1 << AddressOperand);
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
acquire(Context* c, uint32_t mask, Stack* stack, Value** locals,
        unsigned newSize, Value* newValue, RegisterSite* newSite);

void
release(Context* c, Register* r);

Register*
validate(Context* c, uint32_t mask, Stack* stack, Value** locals,
         unsigned size, Value* value, RegisterSite* site, Register* current);

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

  virtual bool match(Context* c, uint8_t typeMask, uint64_t registerMask, int)
  {
    if ((typeMask & (1 << RegisterOperand)) and low) {
      sync(c);
      return ((static_cast<uint64_t>(1) << register_.low) & registerMask)
        and (register_.high == NoRegister
             or ((static_cast<uint64_t>(1) << (register_.high + 32))
                 & registerMask));
    } else {
      return false;
    }
  }

  virtual void acquire(Context* c, Stack* stack, Value** locals, unsigned size,
                       Value* v)
  {
    low = ::validate(c, mask, stack, locals, size, v, this, low);
    if (size > BytesPerWord) {
      ::freeze(low);
      high = ::validate(c, mask >> 32, stack, locals, size, v, this, high);
      ::thaw(low);
    }
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
  assert(c, low < static_cast<int>(c->arch->registerCount()));
  assert(c, high == NoRegister
         or high < static_cast<int>(c->arch->registerCount()));

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

  virtual bool match(Context* c, uint8_t typeMask, uint64_t, int frameIndex) {
    if (typeMask & (1 << MemoryOperand)) {
      sync(c);
      if (value.base == c->arch->stack()) {
        assert(c, value.index == NoRegister);
        return frameIndex == AnyFrameIndex
          || (frameIndex != NoFrameIndex
              && localOffset(c, frameIndex) == value.offset);
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  virtual void acquire(Context* c, Stack*, Value**, unsigned, Value*) {
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
memorySite(Context* c, int base, int offset = 0, int index = NoRegister,
           unsigned scale = 1)
{
  return new (c->zone->allocate(sizeof(MemorySite)))
    MemorySite(base, offset, index, scale);
}

MemorySite*
frameSite(Context* c, int frameIndex)
{
  assert(c, frameIndex >= 0);
  return memorySite(c, c->arch->stack(), localOffset(c, frameIndex));
}

Site*
targetOrNull(Context* c, Value* v, Read* r)
{
  if (v->target) {
    return v->target;
  } else {
    Site* s = r->pickSite(c, v);
    if (s) return s;
    return r->allocateSite(c);
  }
}

Site*
targetOrNull(Context* c, Value* v)
{
  if (v->target) {
    return v->target;
  } else if (live(v)) {
    Read* r = v->reads;
    Site* s = r->pickSite(c, v);
    if (s) return s;
    return r->allocateSite(c);
  }
  return 0;
}

Site*
pickSite(Context* c, Value* value, uint8_t typeMask, uint64_t registerMask,
         int frameIndex)
{
  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (Site* s = value->sites; s; s = s->next) {
    if (s->match(c, typeMask, registerMask, frameIndex)) {
      unsigned v = s->copyCost(c, 0);
      if (v < copyCost) {
        site = s;
        copyCost = v;
      }
    }
  }
  return site;
}

Site*
allocateSite(Context* c, uint8_t typeMask, uint64_t registerMask,
             int frameIndex)
{
  if ((typeMask & (1 << RegisterOperand)) and registerMask) {
    return freeRegisterSite(c, registerMask);
  } else if (frameIndex >= 0) {
    return frameSite(c, frameIndex);
  } else {
    abort(c);
  }
}

class SingleRead: public Read {
 public:
  SingleRead(unsigned size, uint8_t typeMask, uint64_t registerMask,
             int frameIndex):
    size_(size), typeMask(typeMask), registerMask(registerMask),
    frameIndex(frameIndex)
  { }

  virtual Site* pickSite(Context* c, Value* value) {
    return ::pickSite(c, value, typeMask, registerMask, frameIndex);
  }

  virtual Site* allocateSite(Context* c) {
    return ::allocateSite(c, typeMask, registerMask, frameIndex);
  }

  virtual void intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex)
  {
    *typeMask &= this->typeMask;
    *registerMask &= this->registerMask;
    *frameIndex = intersectFrameIndexes(*frameIndex, this->frameIndex);
  }
  
  virtual bool valid() {
    return true;
  }

  virtual unsigned size(Context*) {
    return size_;
  }

  unsigned size_;
  uint8_t typeMask;
  uint64_t registerMask;
  int frameIndex;
};

Read*
read(Context* c, unsigned size, uint8_t typeMask, uint64_t registerMask,
     int frameIndex)
{
  return new (c->zone->allocate(sizeof(SingleRead)))
    SingleRead(size, typeMask, registerMask, frameIndex);
}

Read*
anyRegisterRead(Context* c, unsigned size)
{
  return read(c, size, 1 << RegisterOperand, ~static_cast<uint64_t>(0),
              NoFrameIndex);
}

Read*
registerOrConstantRead(Context* c, unsigned size)
{
  return read(c, size, (1 << RegisterOperand) | (1 << ConstantOperand),
              ~static_cast<uint64_t>(0), NoFrameIndex);
}

Read*
fixedRegisterRead(Context* c, unsigned size, int low, int high = NoRegister)
{
  uint64_t mask;
  if (high == NoRegister) {
    mask = (~static_cast<uint64_t>(0) << 32)
      | (static_cast<uint64_t>(1) << low);
  } else {
    mask = (static_cast<uint64_t>(1) << (high + 32))
      | (static_cast<uint64_t>(1) << low);
  }

  return read(c, size, 1 << RegisterOperand, mask, NoFrameIndex);
}

class MultiRead: public Read {
 public:
  MultiRead():
    reads(0)
  { }

  virtual Site* pickSite(Context* c, Value* value) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::pickSite(c, value, typeMask, registerMask, frameIndex);
  }

  virtual Site* allocateSite(Context* c) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::allocateSite(c, typeMask, registerMask, frameIndex);
  }

  virtual void intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex)
  {
    for (Cell* cell = reads; cell; cell = cell->next) {
      Read* r = static_cast<Read*>(cell->value);
      r->intersect(typeMask, registerMask, frameIndex);
    }
  }

  virtual bool valid() {
    for (Cell* cell = reads; cell; cell = cell->next) {
      Read* r = static_cast<Read*>(cell->value);
      if (r->valid()) {
        return true;
      }
    }
    return false;
  }

  virtual unsigned size(Context* c) {
    return static_cast<Read*>(reads->value)->size(c);
  }

  Cell* reads;
};

MultiRead*
multiRead(Context* c)
{
  return new (c->zone->allocate(sizeof(MultiRead))) MultiRead;
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

bool
trySteal(Context* c, Register* r, Stack* stack, Value** locals)
{
  assert(c, r->refCount == 0);

  Value* v = r->value;
  assert(c, v->reads);

  if (DebugRegisters) {
    fprintf(stderr, "try steal %d from %p: next: %p\n",
            r->number, v, v->sites->next);
  }

  if (v->sites->next == 0) {
    Site* saveSite = 0;
    for (unsigned i = 0; i < c->localFootprint; ++i) {
      if (locals[i] == v) {
        saveSite = frameSite(c, i);
        break;
      }
    }

    if (saveSite == 0) {
      for (Stack* s = stack; s; s = s->next) {
        if (s->value == v) {
          uint8_t typeMask;
          uint64_t registerMask;
          int frameIndex = AnyFrameIndex;
          v->reads->intersect(&typeMask, &registerMask, &frameIndex);

          if (frameIndex >= 0) {
            saveSite = frameSite(c, frameIndex);
          } else {
            saveSite = frameSite(c, s->index + c->localFootprint);
          }
          break;
        }
      }
    }

    if (saveSite) {
      apply(c, Move, r->size, r->site, r->size, saveSite);
      addSite(c, 0, 0, r->size, v, saveSite);
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
  for (int i = c->arch->registerCount() - 1; i >= 0; --i) {
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
    (Swap, BytesPerWord, RegisterOperand, &ar,
     BytesPerWord, RegisterOperand, &br);
  
  c->registers[a->number] = b;
  c->registers[b->number] = a;

  int t = a->number;
  a->number = b->number;
  b->number = t;
}

Register*
replace(Context* c, Stack* stack, Value** locals, Register* r)
{
  uint32_t mask = (r->freezeCount? r->site->mask : ~0);

  freeze(r);
  Register* s = acquire(c, mask, stack, locals, r->size, r->value, r->site);
  thaw(r);

  if (DebugRegisters) {
    fprintf(stderr, "replace %d with %d\n", r->number, s->number);
  }

  swap(c, r, s);

  return s;
}

Register*
acquire(Context* c, uint32_t mask, Stack* stack, Value** locals,
        unsigned newSize, Value* newValue, RegisterSite* newSite)
{
  Register* r = pickRegister(c, mask);

  if (r->reserved) return r;

  if (DebugRegisters) {
    fprintf(stderr, "acquire %d, value %p, site %p freeze count %d "
            "ref count %d used %d used exclusively %d\n",
            r->number, newValue, newSite, r->freezeCount, r->refCount,
            used(c, r), usedExclusively(c, r));
  }

  if (r->refCount) {
    r = replace(c, stack, locals, r);
  } else {
    Value* oldValue = r->value;
    if (oldValue
        and oldValue != newValue
        and findSite(c, oldValue, r->site))
    {
      if (not trySteal(c, r, stack, locals)) {
        r = replace(c, stack, locals, r);
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
validate(Context* c, uint32_t mask, Stack* stack, Value** locals,
         unsigned size, Value* value, RegisterSite* site, Register* current)
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

  Register* r = acquire(c, mask, stack, locals, size, value, site);

  if (current and current != r) {
    release(c, current);
    
    Assembler::Register rr(r->number);
    Assembler::Register cr(current->number);
    c->assembler->apply
      (Move, BytesPerWord, RegisterOperand, &cr,
       BytesPerWord, RegisterOperand, &rr);
  }

  return r;
}

void
apply(Context* c, UnaryOperation op,
      unsigned s1Size, Site* s1)
{
  OperandType s1Type = s1->type(c);
  Assembler::Operand* s1Operand = s1->asAssemblerOperand(c);

  c->assembler->apply(op, s1Size, s1Type, s1Operand);
}

void
apply(Context* c, BinaryOperation op,
      unsigned s1Size, Site* s1,
      unsigned s2Size, Site* s2)
{
  OperandType s1Type = s1->type(c);
  Assembler::Operand* s1Operand = s1->asAssemblerOperand(c);

  OperandType s2Type = s2->type(c);
  Assembler::Operand* s2Operand = s2->asAssemblerOperand(c);

  c->assembler->apply(op, s1Size, s1Type, s1Operand,
                      s2Size, s2Type, s2Operand);
}

void
apply(Context* c, TernaryOperation op,
      unsigned s1Size, Site* s1,
      unsigned s2Size, Site* s2,
      unsigned s3Size, Site* s3)
{
  OperandType s1Type = s1->type(c);
  Assembler::Operand* s1Operand = s1->asAssemblerOperand(c);

  OperandType s2Type = s2->type(c);
  Assembler::Operand* s2Operand = s2->asAssemblerOperand(c);

  OperandType s3Type = s3->type(c);
  Assembler::Operand* s3Operand = s3->asAssemblerOperand(c);

  c->assembler->apply(op, s1Size, s1Type, s1Operand,
                      s2Size, s2Type, s2Operand,
                      s3Size, s3Type, s3Operand);
}

void
insertRead(Context*, Event* event, int sequence, Value* v, Read* r)
{
  r->value = v;
  r->event = event;
  r->eventNext = event->reads;
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
addRead(Context* c, Value* v, Read* r)
{
  insertRead(c, c->logicalCode[c->logicalIp]->lastEvent, -1, v, r);
}

void
clean(Context* c, Value* v)
{
  for (Site** s = &(v->sites); *s;) {
    if ((*s)->match(c, 1 << MemoryOperand, 0, AnyFrameIndex)) {
      s = &((*s)->next);
    } else {
      (*s)->release(c);
      *s = (*s)->next;
    }
  }
}

void
clean(Context* c, Stack* stack, Value** locals, Read* reads)
{
  for (unsigned i = 0; i < c->localFootprint; ++i) {
    if (locals[i]) clean(c, locals[i]);
  }

  for (Stack* s = stack; s; s = s->next) {
    clean(c, s->value);
  }

  for (Read* r = reads; r; r = r->eventNext) {
    nextRead(c, r->value);
  }  
}

CodePromise*
codePromise(Context* c, Event* e)
{
  return e->promises = new (c->zone->allocate(sizeof(CodePromise)))
    CodePromise(c, e->promises);
}

CodePromise*
codePromise(Context* c, Assembler::Offset* offset)
{
  return new (c->zone->allocate(sizeof(CodePromise))) CodePromise(c, offset);
}

class CallEvent: public Event {
 public:
  CallEvent(Context* c, Value* address, unsigned flags,
            TraceHandler* traceHandler, Value* result, unsigned resultSize,
            Stack* argumentStack, unsigned argumentCount,
            unsigned stackArgumentFootprint):
    Event(c),
    address(address),
    traceHandler(traceHandler),
    result(result),
    flags(flags),
    resultSize(resultSize)
  {
    uint32_t mask = ~0;
    Stack* s = argumentStack;
    unsigned index = 0;
    unsigned frameIndex = alignedFrameSize(c) + c->parameterFootprint;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Read* target;
      if (index < c->arch->argumentRegisterCount()) {
        int r = c->arch->argumentRegister(index);
        target = fixedRegisterRead(c, s->size * BytesPerWord, r);
        mask &= ~(1 << r);
      } else {
        frameIndex -= s->size;
        target = read(c, s->size * BytesPerWord, 1 << MemoryOperand, 0,
                      frameIndex);
      }
      addRead(c, s->value, target);
      index += s->size;
      s = s->next;
    }

    addRead(c, address, read
            (c, BytesPerWord, ~0, (static_cast<uint64_t>(mask) << 32) | mask,
             AnyFrameIndex));

    int footprint = stackArgumentFootprint;
    for (Stack* s = stack; s; s = s->next) {
      if (footprint) {
        addRead(c, s->value, read(c, s->size * BytesPerWord,
                                  1 << MemoryOperand, 0, frameIndex));
      } else {
        unsigned index = s->index + c->localFootprint;
        if (footprint == 0) {
          assert(c, index <= frameIndex);
          s->padding = frameIndex - index;
        }
        addRead(c, s->value, read(c, s->size * BytesPerWord,
                                  1 << MemoryOperand, 0, index));
      }
      frameIndex -= s->size;
      footprint -= s->size;
    }
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CallEvent.compile\n");
    }
    
    apply(c, (flags & Compiler::Aligned) ? AlignedCall : Call, BytesPerWord,
          address->source);

    if (traceHandler) {
      traceHandler->handleTrace(codePromise(c, c->assembler->offset()));
    }

    clean(c, stack, locals, reads);

    if (resultSize and live(result)) {
      addSite(c, 0, 0, resultSize, result, registerSite
              (c, c->arch->returnLow(),
               resultSize > BytesPerWord ?
               c->arch->returnHigh() : NoRegister));
    }
  }

  Value* address;
  TraceHandler* traceHandler;
  Value* result;
  unsigned flags;
  unsigned resultSize;
};

void
appendCall(Context* c, Value* address, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount,
           unsigned stackArgumentFootprint)
{
  if (DebugAppend) {
    fprintf(stderr, "appendCall\n");
  }

  new (c->zone->allocate(sizeof(CallEvent)))
    CallEvent(c, address, flags, traceHandler, result,
              resultSize, argumentStack, argumentCount,
              stackArgumentFootprint);
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, unsigned size, Value* value):
    Event(c), value(value)
  {
    if (value) {
      addRead(c, value, fixedRegisterRead
              (c, size, c->arch->returnLow(),
               size > BytesPerWord ?
               c->arch->returnHigh() : NoRegister));
    }
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "ReturnEvent.compile\n");
    }

    if (value) {
      nextRead(c, value);
    }

    c->assembler->popFrame();
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
  MoveEvent(Context* c, BinaryOperation type, unsigned srcSize, Value* src,
            unsigned dstSize, Value* dst, Read* srcRead, Read* dstRead):
    Event(c), type(type), srcSize(srcSize), src(src), dstSize(dstSize),
    dst(dst), dstRead(dstRead)
  {
    addRead(c, src, srcRead);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "MoveEvent.compile\n");
    }

    bool isLoad = not valid(src->reads->next);
    bool isStore = not valid(dst->reads);

    Site* target = targetOrRegister(c, dst);
    unsigned cost = src->source->copyCost(c, target);
    if (cost == 0 and (isLoad or isStore)) {
      target = src->source;
    }

    assert(c, isLoad or isStore or target != src->source);

    if (target == src->source) {
      removeSite(c, src, target);
    }

    if (not isStore) {
      addSite(c, stack, locals, dstSize, dst, target);
    }

    if (cost or type != Move) {    
      uint8_t typeMask = ~static_cast<uint8_t>(0);
      uint64_t registerMask = ~static_cast<uint64_t>(0);
      int frameIndex = AnyFrameIndex;
      dstRead->intersect(&typeMask, &registerMask, &frameIndex);

      if (target->match(c, typeMask, registerMask, frameIndex)) {
        apply(c, type, srcSize, src->source, dstSize, target);
      } else {
        assert(c, typeMask & (1 << RegisterOperand));

        Site* tmpTarget = freeRegisterSite(c, registerMask);

        addSite(c, stack, locals, dstSize, dst, tmpTarget);

        apply(c, type, srcSize, src->source, dstSize, tmpTarget);

        if (isStore) {
          removeSite(c, dst, tmpTarget);

          apply(c, Move, dstSize, tmpTarget, dstSize, target);
        } else {
          removeSite(c, dst, target);          
        }
      }
    }

    if (isStore) {
      removeSite(c, dst, target);
    }

    nextRead(c, src);
  }

  BinaryOperation type;
  unsigned srcSize;
  Value* src;
  unsigned dstSize;
  Value* dst;
  Read* dstRead;
};

void
appendMove(Context* c, BinaryOperation type, unsigned srcSize, Value* src,
           unsigned dstSize, Value* dst)
{
  if (DebugAppend) {
    fprintf(stderr, "appendMove\n");
  }

  bool thunk;
  uint8_t srcTypeMask;
  uint64_t srcRegisterMask;
  uint8_t dstTypeMask;
  uint64_t dstRegisterMask;

  c->arch->plan(type, srcSize, &srcTypeMask, &srcRegisterMask,
                dstSize, &dstTypeMask, &dstRegisterMask,
                &thunk);

  assert(c, not thunk); // todo

  new (c->zone->allocate(sizeof(MoveEvent)))
    MoveEvent(c, type, srcSize, src, dstSize, dst,
              read(c, srcSize, srcTypeMask, srcRegisterMask, AnyFrameIndex),
              read(c, dstSize, dstTypeMask, dstRegisterMask, AnyFrameIndex));
}

ConstantSite*
findConstantSite(Context* c, Value* v)
{
  for (Site* s = v->sites; s; s = s->next) {
    if (s->type(c) == ConstantOperand) {
      return static_cast<ConstantSite*>(s);
    }
  }
  return 0;
}

class CompareEvent: public Event {
 public:
  CompareEvent(Context* c, unsigned size, Value* first, Value* second,
               Read* firstRead, Read* secondRead):
    Event(c), size(size), first(first), second(second)
  {
    addRead(c, first, firstRead);
    addRead(c, second, secondRead);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CompareEvent.compile\n");
    }

    ConstantSite* firstConstant = findConstantSite(c, first);
    ConstantSite* secondConstant = findConstantSite(c, second);

    if (firstConstant and secondConstant) {
      int64_t d = firstConstant->value.value->value()
        - secondConstant->value.value->value();

      if (d < 0) {
        c->constantCompare = CompareLess;
      } else if (d > 0) {
        c->constantCompare = CompareGreater;
      } else {
        c->constantCompare = CompareEqual;
      }
    } else {
      c->constantCompare = CompareNone;

      apply(c, Compare, size, first->source, size, second->source);
    }

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
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t secondTypeMask;
  uint64_t secondRegisterMask;

  c->arch->plan(Compare, size, &firstTypeMask, &firstRegisterMask,
                size, &secondTypeMask, &secondRegisterMask,
                &thunk);

  assert(c, not thunk); // todo

  if (DebugAppend) {
    fprintf(stderr, "appendCompare\n");
  }

  new (c->zone->allocate(sizeof(CompareEvent)))
    CompareEvent
    (c, size, first, second,
     read(c, size, firstTypeMask, firstRegisterMask, AnyFrameIndex),
     read(c, size, secondTypeMask, secondRegisterMask, AnyFrameIndex));
}

void
preserve(Context* c, Stack* stack, Value** locals, unsigned size, Value* v,
         Site* s, Read* read)
{
  assert(c, v->sites == s);
  Site* r = targetOrNull(c, v, read);
  if (r == 0 or r == s) r = freeRegisterSite(c);
  addSite(c, stack, locals, size, v, r);
  apply(c, Move, size, s, size, r);
}

void
maybePreserve(Context* c, Stack* stack, Value** locals, unsigned size,
              Value* v, Site* s)
{
  if (valid(v->reads->next) and v->sites->next == 0) {
    preserve(c, stack, locals, size, v, s, v->reads->next);
  }
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, TernaryOperation type,
               unsigned firstSize, Value* first,
               unsigned secondSize, Value* second,
               unsigned resultSize, Value* result,
               Read* firstRead,
               Read* secondRead,
               Read* resultRead):
    Event(c), type(type), firstSize(firstSize), first(first),
    secondSize(secondSize), second(second), resultSize(resultSize),
    result(result), resultRead(resultRead)
  {
    addRead(c, first, firstRead);
    addRead(c, second, secondRead);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "CombineEvent.compile\n");
    }

    Site* target;
    if (c->arch->condensedAddressing()) {
      maybePreserve(c, stack, locals, secondSize, second, second->source);
      target = second->source;
    } else {
      target = resultRead->allocateSite(c);
      addSite(c, stack, locals, resultSize, result, target);
    }

    apply(c, type, firstSize, first->source, secondSize, second->source,
          resultSize, target);

    nextRead(c, first);
    nextRead(c, second);
  }

  TernaryOperation type;
  unsigned firstSize;
  Value* first;
  unsigned secondSize;
  Value* second;
  unsigned resultSize;
  Value* result;
  Read* resultRead;
};

Value*
value(Context* c, Site* site = 0, Site* target = 0)
{
  return new (c->zone->allocate(sizeof(Value))) Value(site, target);
}

Stack*
stack(Context* c, Value* value, unsigned size, unsigned index, Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(index, size, value, next);
}

Stack*
stack(Context* c, Value* value, unsigned size, Stack* next)
{
  return stack
    (c, value, size, (next ? next->index + next->size : 0), next);
}

void
push(Context* c, unsigned size, Value* v)
{
  assert(c, ceiling(size, BytesPerWord));

  c->state->stack = stack(c, v, ceiling(size, BytesPerWord), c->state->stack);
}

Value*
pop(Context* c, unsigned size UNUSED)
{
  Stack* s = c->state->stack;
  assert(c, ceiling(size, BytesPerWord) == s->size);

  c->state->stack = s->next;
  return s->value;
}

void
appendCombine(Context* c, TernaryOperation type,
              unsigned firstSize, Value* first,
              unsigned secondSize, Value* second,
              unsigned resultSize, Value* result)
{
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t secondTypeMask;
  uint64_t secondRegisterMask;
  uint8_t resultTypeMask;
  uint64_t resultRegisterMask;

  c->arch->plan(type, firstSize, &firstTypeMask, &firstRegisterMask,
                secondSize, &secondTypeMask, &secondRegisterMask,
                resultSize, &resultTypeMask, &resultRegisterMask,
                &thunk);

  if (thunk) {
    Stack* oldStack = c->state->stack;

    ::push(c, secondSize, second);
    ::push(c, firstSize, first);

    Stack* argumentStack = c->state->stack;
    c->state->stack = oldStack;

    appendCall
      (c, value(c, constantSite(c, c->client->getThunk(type, resultSize))),
       0, 0, result, resultSize, argumentStack, 2, 0);
  } else {
    if (DebugAppend) {
      fprintf(stderr, "appendCombine\n");
    }

    Read* resultRead = read
      (c, resultSize, resultTypeMask, resultRegisterMask, AnyFrameIndex);
    Read* secondRead;
    if (c->arch->condensedAddressing()) {
      secondRead = resultRead;
    } else {
      secondRead = read
        (c, secondSize, secondTypeMask, secondRegisterMask, AnyFrameIndex);
    }

    new (c->zone->allocate(sizeof(CombineEvent)))
      CombineEvent
      (c, type,
       firstSize, first,
       secondSize, second,
       resultSize, result,
       read(c, firstSize, firstTypeMask, firstRegisterMask, AnyFrameIndex),
       secondRead,
       resultRead);
  }
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, BinaryOperation type, unsigned size, Value* value,
                 Value* result, Read* read):
    Event(c), type(type), size(size), value(value), result(result)
  {
    addRead(c, value, read);
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "TranslateEvent.compile\n");
    }

    maybePreserve(c, stack, locals, size, value, value->source);

    Site* target = targetOrRegister(c, result);
    apply(c, type, size, value->source, size, target);
    
    nextRead(c, value);

    removeSite(c, value, value->source);
    if (live(result)) {
      addSite(c, 0, 0, size, result, value->source);
    }
  }

  BinaryOperation type;
  unsigned size;
  Value* value;
  Value* result;
};

void
appendTranslate(Context* c, BinaryOperation type, unsigned size, Value* value,
                Value* result)
{
  if (DebugAppend) {
    fprintf(stderr, "appendTranslate\n");
  }

  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t resultTypeMask;
  uint64_t resultRegisterMask;

  c->arch->plan(type, size, &firstTypeMask, &firstRegisterMask,
                size, &resultTypeMask, &resultRegisterMask,
                &thunk);

  assert(c, not thunk); // todo

  // todo: respect resultTypeMask and resultRegisterMask

  new (c->zone->allocate(sizeof(TranslateEvent)))
    TranslateEvent
    (c, type, size, value, result,
     read(c, size, firstTypeMask, firstRegisterMask, AnyFrameIndex));
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, int displacement, Value* index,
              unsigned scale, Value* result):
    Event(c), base(base), displacement(displacement), index(index),
    scale(scale), result(result)
  {
    addRead(c, base, anyRegisterRead(c, BytesPerWord));
    if (index) addRead(c, index, registerOrConstantRead(c, BytesPerWord));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "MemoryEvent.compile\n");
    }
    
    int indexRegister;
    int displacement = this->displacement;
    unsigned scale = this->scale;
    if (index) {
      ConstantSite* constant = findConstantSite(c, index);

      if (constant) {
        indexRegister = NoRegister;
        displacement += (constant->value.value->value() * scale);
        scale = 1;
      } else {
        assert(c, index->source->type(c) == RegisterOperand);
        indexRegister = static_cast<RegisterSite*>
          (index->source)->register_.low;
      }
    } else {
      indexRegister = NoRegister;
    }
    assert(c, base->source->type(c) == RegisterOperand);
    int baseRegister = static_cast<RegisterSite*>(base->source)->register_.low;

    nextRead(c, base);
    if (index) {
      if (BytesPerWord == 8 and indexRegister != NoRegister) {
        apply(c, Move, 4, index->source, 8, index->source);
      }

      nextRead(c, index);
    }

    result->target = memorySite
      (c, baseRegister, displacement, indexRegister, scale);
    addSite(c, 0, 0, 0, result, result->target);
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

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, UnaryOperation type, Value* address):
    Event(c), type(type), address(address)
  {
    addRead(c, address, read(c, BytesPerWord, ~0, ~static_cast<uint64_t>(0),
                             AnyFrameIndex));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "BranchEvent.compile\n");
    }

    bool jump;
    UnaryOperation type = this->type;
    if (type != Jump) {
      switch (c->constantCompare) {
      case CompareLess:
        switch (type) {
        case JumpIfLess:
        case JumpIfLessOrEqual:
        case JumpIfNotEqual:
          jump = true;
          type = Jump;
          break;

        default:
          jump = false;
        }
        break;

      case CompareGreater:
        switch (type) {
        case JumpIfGreater:
        case JumpIfGreaterOrEqual:
        case JumpIfNotEqual:
          jump = true;
          type = Jump;
          break;

        default:
          jump = false;
        }
        break;

      case CompareEqual:
        switch (type) {
        case JumpIfEqual:
        case JumpIfLessOrEqual:
        case JumpIfGreaterOrEqual:
          jump = true;
          type = Jump;
          break;

        default:
          jump = false;
        }
        break;

      case CompareNone:
        jump = true;
        break;

      default: abort(c);
      }
    } else {
      jump = true;
    }

    if (jump) {
      apply(c, type, BytesPerWord, address->source);
    }

    nextRead(c, address);
  }

  UnaryOperation type;
  Value* address;
};

void
appendBranch(Context* c, UnaryOperation type, Value* address)
{
  if (DebugAppend) {
    fprintf(stderr, "appendBranch\n");
  }

  new (c->zone->allocate(sizeof(BranchEvent))) BranchEvent(c, type, address);
}

class BoundsCheckEvent: public Event {
 public:
  BoundsCheckEvent(Context* c, Value* object, unsigned lengthOffset,
                   Value* index, intptr_t handler):
    Event(c), object(object), lengthOffset(lengthOffset), index(index),
    handler(handler)
  {
    addRead(c, object, anyRegisterRead(c, BytesPerWord));
    addRead(c, index, registerOrConstantRead(c, BytesPerWord));
  }

  virtual void compile(Context* c) {
    if (DebugCompile) {
      fprintf(stderr, "BoundsCheckEvent.compile\n");
    }

    Assembler* a = c->assembler;

    ConstantSite* constant = findConstantSite(c, index);
    CodePromise* nextPromise = codePromise
      (c, static_cast<Assembler::Offset*>(0));
    CodePromise* outOfBoundsPromise = 0;

    if (constant) {
      expect(c, constant->value.value->value() >= 0);      
    } else {
      outOfBoundsPromise = codePromise(c, static_cast<Assembler::Offset*>(0));

      apply(c, Compare, 4, constantSite(c, resolved(c, 0)), 4, index->source);

      Assembler::Constant outOfBoundsConstant(outOfBoundsPromise);
      a->apply
        (JumpIfLess, BytesPerWord, ConstantOperand, &outOfBoundsConstant);
    }

    assert(c, object->source->type(c) == RegisterOperand);
    int base = static_cast<RegisterSite*>(object->source)->register_.low;

    Site* length = memorySite(c, base, lengthOffset);
    length->acquire(c, 0, 0, 0, 0);

    apply(c, Compare, 4, index->source, 4, length);

    length->release(c);

    Assembler::Constant nextConstant(nextPromise);
    a->apply(JumpIfGreater, BytesPerWord, ConstantOperand, &nextConstant);

    if (constant == 0) {
      outOfBoundsPromise->offset = a->offset();
    }

    Assembler::Constant handlerConstant(resolved(c, handler));
    a->apply(Call, BytesPerWord, ConstantOperand, &handlerConstant);

    nextPromise->offset = a->offset();

    nextRead(c, object);
    nextRead(c, index);
  }

  Value* object;
  unsigned lengthOffset;
  Value* index;
  intptr_t handler;
};

void
appendBoundsCheck(Context* c, Value* object, unsigned lengthOffset,
                  Value* index, intptr_t handler)
{
  if (DebugAppend) { 
    fprintf(stderr, "appendBoundsCheck\n");
  }

  new (c->zone->allocate(sizeof(BoundsCheckEvent))) BoundsCheckEvent
    (c, object, lengthOffset, index, handler);
}

// class ClobberLocalEvent: public Event {
//  public:
//   ClobberLocalEvent(Context* c, unsigned size, int index):
//     Event(c), size(size), index(index)
//   { }

//   virtual void compile(Context* c) {
//     if (DebugCompile) {
//       fprintf(stderr, "ClobberLocalEvent.compile\n");
//     }

//     Value* v = locals[index];
//     if (live(v)
//         and v->sites->next == 0
//         and v->sites->match(c, 1 << MemoryOperand, 0, index))
//     {
//       preserve(c, stack, locals, size, v, v->sites, v->reads);
//       removeSite(c, v, v->sites);
//     }
//   }

//   unsigned size;
//   int index;
// };

// void
// appendClobberLocal(Context* c, unsigned size, int index)
// {
//   if (DebugAppend) {
//     fprintf(stderr, "appendClobberLocal\n");
//   }

//   new (c->zone->allocate(sizeof(ClobberLocalEvent)))
//     ClobberLocalEvent(c, size, index);
// }

Site*
readSource(Context* c, Stack* stack, Value** locals, Read* r)
{
  if (r->value->sites == 0) {
    return 0;
  }

  Site* site = r->pickSite(c, r->value);

  if (site) {
    return site;
  } else {
    Site* target = r->allocateSite(c);
    unsigned copyCost;
    site = pick(c, r->value->sites, target, &copyCost);
    assert(c, copyCost);

    addSite(c, stack, locals, r->size(c), r->value, target);
    apply(c, Move, r->size(c), site, r->size(c), target);
    return target;    
  }
}

Site*
pickJunctionSite(Context* c, Value* v, Read* r)
{
  Site* s = r->pickSite(c, v);
  if (s) return s;
  return r->allocateSite(c);  
}

unsigned
resolveJunctionSite(Context* c, Event* e, Event* successor, Value* v,
                    unsigned index, Site** frozenSites,
                    unsigned frozenSiteIndex)
{
  if (live(v)) {
    Read* r = v->reads;
    Site* original = e->junctionSites[index];

    if (original == 0) {
      e->junctionSites[index] = pickJunctionSite(c, v, r);
    }

    Site* target = e->junctionSites[index];
    unsigned copyCost;
    Site* site = pick(c, v->sites, target, &copyCost);
    if (copyCost) {
      addSite(c, successor->stack, successor->locals, r->size(c), v, target);
      apply(c, Move, r->size(c), site, r->size(c), target);
    }

    if (original == 0) {
      frozenSites[frozenSiteIndex++] = target;
      target->freeze(c);
    }
  }

  return frozenSiteIndex;
}

void
propagateJunctionSites(Context* c, Event* e, Site** sites)
{
  for (Cell* pc = e->predecessors; pc; pc = pc->next) {
    Event* p = static_cast<Event*>(pc->value);
    if (p->junctionSites == 0) {
      p->junctionSites = sites;
      for (Cell* sc = p->successors; sc; sc = sc->next) {
        Event* s = static_cast<Event*>(sc->value);
        propagateJunctionSites(c, s, sites);
      }
    }
  }
}

unsigned
frameFootprint(Context* c, Stack* s)
{
  return c->localFootprint + (s ? (s->index + s->size) : 0);
}

void
populateSiteTables(Context* c, Event* e)
{
  Event* successor = static_cast<Event*>(e->successors->value);

  unsigned frameFootprint = ::frameFootprint(c, successor->stack);

  { Site* frozenSites[frameFootprint];
    unsigned frozenSiteIndex = 0;

    if (e->junctionSites) {
      for (unsigned i = 0; i < frameFootprint; ++i) {
        Site* site = e->junctionSites[i];
        if (site) {
          frozenSites[frozenSiteIndex++] = site;
          site->freeze(c);
        }
      }
    } else {
      for (Cell* sc = e->successors; sc; sc = sc->next) {
        Event* s = static_cast<Event*>(sc->value);
        if (s->predecessors->next) {
          unsigned size = sizeof(Site*) * frameFootprint;
          Site** junctionSites = static_cast<Site**>
            (c->zone->allocate(size));
          memset(junctionSites, 0, size);

          propagateJunctionSites(c, s, junctionSites);
          break;
        }
      }
    }

    if (e->junctionSites) {
      Event* s = e->next;
      for (unsigned i = 0; i < c->localFootprint; ++i) {
        frozenSiteIndex = resolveJunctionSite
          (c, e, s, s->locals[i], i, frozenSites, frozenSiteIndex);
      }

      unsigned i = s->stack->index + c->localFootprint;
      for (Stack* stack = s->stack; stack; stack = stack->next) {
        frozenSiteIndex = resolveJunctionSite
          (c, e, s, stack->value, i, frozenSites, frozenSiteIndex);

        i -= stack->size;
      }
    }

    while (frozenSiteIndex) {
      frozenSites[--frozenSiteIndex]->thaw(c);
    }
  }

  if (e->successors->next) {
    unsigned size = sizeof(Site*) * frameFootprint;
    Site** savedSites = static_cast<Site**>(c->zone->allocate(size));

    for (unsigned i = 0; i < c->localFootprint; ++i) {
      savedSites[i] = successor->locals[i]->sites;
    }

    unsigned i = successor->stack->index + c->localFootprint;
    for (Stack* stack = successor->stack; stack; stack = stack->next) {
      savedSites[i] = stack->value->sites;

      i -= stack->size;
    }
  }
}

void
setSites(Context* c, Event* e, Site** sites)
{
  for (unsigned i = 0; i < c->localFootprint; ++i) {
    Value* v = e->locals[i];
    clearSites(c, v);
    addSite(c, 0, 0, v->reads->size(c), v, sites[i]);
  }

  unsigned i = e->stack->index + c->localFootprint;
  for (Stack* stack = e->stack; stack; stack = stack->next) {
    Value* v = stack->value;
    clearSites(c, v);
    addSite(c, 0, 0, v->reads->size(c), v, sites[i]);
    i -= stack->size;
  }
}

void
populateSources(Context* c, Event* e)
{
  Site* frozenSites[e->readCount];
  unsigned frozenSiteIndex = 0;
  for (Read* r = e->reads; r; r = r->eventNext) {
    r->value->source = readSource(c, e->stack, e->locals, r);

    if (r->value->source) {
      assert(c, frozenSiteIndex < e->readCount);
      frozenSites[frozenSiteIndex++] = r->value->source;
      r->value->source->freeze(c);
    }
  }

  while (frozenSiteIndex) {
    frozenSites[--frozenSiteIndex]->thaw(c);
  }
}

LogicalInstruction*
next(Context* c, LogicalInstruction* i)
{
  for (unsigned n = i->index + 1; n < c->logicalCodeLength; ++n) {
    i = c->logicalCode[n];
    if (i and i->firstEvent) return i;
  }
  return 0;
}

class Block {
 public:
  Block(Event* head):
    head(head), nextInstruction(0), assemblerBlock(0), start(0)
  { }

  Event* head;
  LogicalInstruction* nextInstruction;
  Assembler::Block* assemblerBlock;
  unsigned start;
};

Block*
block(Context* c, Event* head)
{
  return new (c->zone->allocate(sizeof(Block))) Block(head);
}

unsigned
compile(Context* c)
{
  Assembler* a = c->assembler;

  c->pass = CompilePass;

  Block* firstBlock = block(c, c->firstEvent);
  Block* block = firstBlock;

  a->allocateFrame(alignedFrameSize(c));

  for (Event* e = c->firstEvent; e; e = e->next) {
    e->block = block;

    if (e->logicalInstruction->machineOffset == 0) {
      e->logicalInstruction->machineOffset = a->offset();
    }

    if (e->predecessors) {
      Event* predecessor = static_cast<Event*>(e->predecessors->value);
      if (e->predecessors->next) {
        setSites(c, e, predecessor->junctionSites);
      } else if (predecessor->successors->next) {
        setSites(c, e, predecessor->savedSites);
      }
    }

    populateSources(c, e);

    e->compile(c);

    if (e->successors) {
      populateSiteTables(c, e);
    }

    e->compilePostsync(c);

    for (CodePromise* p = e->promises; p; p = p->next) {
      p->offset = a->offset();
    }

    if (e->next and e->logicalInstruction->lastEvent == e) {
      LogicalInstruction* nextInstruction = next(c, e->logicalInstruction);
      if (nextInstruction != e->next->logicalInstruction) {
        block->nextInstruction = nextInstruction;
        block->assemblerBlock = a->endBlock();
        block = ::block(c, e->next);
      }
    }
  }

  block->nextInstruction = 0;
  block->assemblerBlock = a->endBlock();

  block = firstBlock;
  while (block->nextInstruction) {
    Block* next = block->nextInstruction->firstEvent->block;
    next->start = block->assemblerBlock->resolve
      (block->start, next->assemblerBlock);
    block = next;
  }

  return block->assemblerBlock->resolve(block->start, 0);
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
    State(c->state, c->state->stack, c->state->locals);
}

void
saveStack(Context* c)
{
  if (c->logicalIp >= 0 and not c->logicalCode[c->logicalIp]->stackSaved) {
    LogicalInstruction* i = c->logicalCode[c->logicalIp];
    i->stackSaved = true;
    i->stack = c->state->stack;
    i->locals = c->state->locals;

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
    State(c->state->next->next, c->state->next->stack, c->state->next->locals);
 
  if (DebugAppend) {
    unsigned count = 0; for (State* s = c->state; s; s = s->next) ++ count;
    fprintf(stderr, "pop to level %d\n", count);
    count = 0; for (Stack* s = c->state->stack; s; s = s->next) ++ count;
    fprintf(stderr, "stack count: %d\n", count);
  }
}

void
visit(Context* c, unsigned logicalIp)
{
  assert(c, logicalIp < c->logicalCodeLength);

  if (c->logicalCode[logicalIp] == 0) {
    c->logicalCode[logicalIp] = new 
      (c->zone->allocate(sizeof(LogicalInstruction)))
      LogicalInstruction(logicalIp);
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
    // todo
    expect(c, c->registers[r]->refCount == 0);
    expect(c, c->registers[r]->value == 0);
  }

  virtual void restore(int) {
    // todo
  }

  Context* c;
};

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, Assembler* assembler, Zone* zone,
             Compiler::Client* compilerClient):
    c(s, assembler, zone, compilerClient), client(&c)
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
    // todo: anything?
  }

  virtual void init(unsigned logicalCodeLength, unsigned parameterFootprint,
                    unsigned localFootprint, unsigned maxStackFootprint)
  {
    c.logicalCodeLength = logicalCodeLength;
    c.parameterFootprint = parameterFootprint;
    c.localFootprint = localFootprint;
    c.maxStackFootprint = maxStackFootprint;

    c.logicalCode = static_cast<LogicalInstruction**>
      (c.zone->allocate(sizeof(LogicalInstruction*) * logicalCodeLength));
    memset(c.logicalCode, 0, sizeof(LogicalInstruction*) * logicalCodeLength);

    c.state->locals = static_cast<Value**>
      (c.zone->allocate(sizeof(Value*) * localFootprint));
    memset(c.state->locals, 0, sizeof(Value*) * localFootprint);
  }

  virtual void visitLogicalIp(unsigned logicalIp) {
    visit(&c, logicalIp);
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
    Site* s = registerSite(&c, c.arch->stack());
    return value(&c, s, s);
  }

  virtual Operand* thread() {
    Site* s = registerSite(&c, c.arch->thread());
    return value(&c, s, s);
  }

  virtual Operand* stackTop() {
    Site* s = frameSite(&c, c.state->stack->index);
    return value(&c, s, s);
  }

  virtual Operand* label() {
    return value(&c, ::constantSite(&c, static_cast<Promise*>(0)));
  }

  Promise* machineIp() {
    return codePromise(&c, c.logicalCode[c.logicalIp]->lastEvent);
  }

  virtual void mark(Operand* label) {
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

  virtual void pushed() {
    Value* v = value(&c);
    c.state->stack = ::stack(&c, v, 1, c.state->stack);
  }

  virtual void popped() {
    c.state->stack = c.state->stack->next;
  }

  virtual StackElement* top() {
    return c.state->stack;
  }

  virtual unsigned size(StackElement* e) {
    return static_cast<Stack*>(e)->size;
  }

  virtual unsigned padding(StackElement* e) {
    return static_cast<Stack*>(e)->padding;
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
    int index = 0;
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
    Stack* bottomArgument = 0;

    for (int i = index - 1; i >= 0; --i) {
      ::push(&c, argumentSizes[i], arguments[i]);
      if (i == index - 1) {
        bottomArgument = c.state->stack;
      }
    }
    Stack* argumentStack = c.state->stack;
    c.state->stack = oldStack;

    Value* result = value(&c);
    appendCall(&c, static_cast<Value*>(address), flags, traceHandler, result,
               resultSize, argumentStack, index, 0);

    return result;
  }

  virtual Operand* stackCall(Operand* address,
                             unsigned flags,
                             TraceHandler* traceHandler,
                             unsigned resultSize,
                             unsigned argumentFootprint)
  {
    Value* result = value(&c);
    appendCall(&c, static_cast<Value*>(address), flags, traceHandler, result,
               resultSize, c.state->stack, 0, argumentFootprint);

    return result;
  }

  virtual void return_(unsigned size, Operand* value) {
    appendReturn(&c, size, static_cast<Value*>(value));
  }

  virtual void storeLocal(unsigned, Operand* src, unsigned index) {
    assert(&c, index < c.localFootprint);

//     if (c.state->locals[index]) {
//       appendClobberLocal(&c, size, index);
//     }

//     Value* v = static_cast<Value*>(memory(base(), localOffset(&c, index)));
//     store(size, src, v);

    c.state->locals[index] = static_cast<Value*>(src);
  }

  virtual Operand* loadLocal(unsigned, unsigned index) {
    assert(&c, index < c.localFootprint);
    assert(&c, c.state->locals[index]);

    return c.state->locals[index];
  }

  virtual void checkBounds(Operand* object, unsigned lengthOffset,
                           Operand* index, intptr_t handler)
  {
    appendBoundsCheck(&c, static_cast<Value*>(object),
                      lengthOffset, static_cast<Value*>(index), handler);
  }

  virtual void store(unsigned size, Operand* src, Operand* dst) {
    appendMove(&c, Move, size, static_cast<Value*>(src),
               size, static_cast<Value*>(dst));
  }

  virtual Operand* load(unsigned size, Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, Move, size, static_cast<Value*>(src), size, dst);
    return dst;
  }

  virtual Operand* loadz(unsigned size, Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, MoveZ, size, static_cast<Value*>(src), size, dst);
    return dst;
  }

  virtual Operand* load4To8(Operand* src) {
    Value* dst = value(&c);
    appendMove(&c, Move, 4, static_cast<Value*>(src), 8, dst);
    return dst;
  }

  virtual Operand* lcmp(Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, LongCompare, 8, static_cast<Value*>(a),
                  8, static_cast<Value*>(b), 8, result);
    return result;
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
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* sub(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Subtract, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* mul(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Multiply, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* div(unsigned size, Operand* a, Operand* b)  {
    Value* result = value(&c);
    appendCombine(&c, Divide, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* rem(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Remainder, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* shl(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, ShiftLeft, BytesPerWord, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* shr(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, ShiftRight, BytesPerWord, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* ushr(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, UnsignedShiftRight, BytesPerWord, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* and_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, And, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* or_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Or, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* xor_(unsigned size, Operand* a, Operand* b) {
    Value* result = value(&c);
    appendCombine(&c, Xor, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* neg(unsigned size, Operand* a) {
    Value* result = value(&c);
    appendTranslate(&c, Negate, size, static_cast<Value*>(a), result);
    return result;
  }

  virtual unsigned compile() {
    return ::compile(&c);
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
  ::Client client;
};

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, Assembler* assembler, Zone* zone,
             Compiler::Client* client)
{
  return new (zone->allocate(sizeof(MyCompiler)))
    MyCompiler(system, assembler, zone, client);
}

} // namespace vm
