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
const bool DebugRegisters = false;
const bool DebugFrameIndexes = false;
const bool DebugFrame = false;
const bool DebugControl = false;
const bool DebugReads = false;
const bool DebugSites = false;
const bool DebugMoves = false;

const int AnyFrameIndex = -2;
const int NoFrameIndex = -1;

class Context;
class Value;
class Stack;
class Site;
class ConstantSite;
class AddressSite;
class RegisterSite;
class MemorySite;
class Event;
class PushEvent;
class Read;
class MultiRead;
class StubRead;
class Block;
class Snapshot;

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

class Local {
 public:
  Value* value;
  unsigned footprint;
};

class Site {
 public:
  Site(): next(0) { }
  
  virtual Site* readTarget(Context*, Read*) { return this; }

  virtual void toString(Context*, char*, unsigned) = 0;

  virtual unsigned copyCost(Context*, Site*) = 0;

  virtual bool match(Context*, uint8_t, uint64_t, int) = 0;
  
  virtual void acquire(Context*, Stack*, Local*, unsigned, Value*) { }

  virtual void release(Context*) { }

  virtual void freeze(Context*, Value*, unsigned) { }

  virtual void thaw(Context*, Value*, unsigned) { }

  virtual bool usesRegister(Context*, int) { return false; }

  virtual OperandType type(Context*) = 0;

  virtual Assembler::Operand* asAssemblerOperand(Context*) = 0;

  virtual Site* copy(Context*) = 0;

  Site* next;
};

class Stack: public Compiler::StackElement {
 public:
  Stack(unsigned index, unsigned footprint, Value* value, Stack* next):
    index(index), footprint(footprint), value(value), next(next)
  { }

  unsigned index;
  unsigned footprint;
  Value* value;
  Stack* next;
};

class SavedValue {
 public:
  SavedValue(unsigned footprint, Value* value, SavedValue* next):
    footprint(footprint), value(value), next(next)
  { }

  unsigned footprint;
  Value* value;
  SavedValue* next;
};

class ForkElement {
 public:
  Value* value;
  MultiRead* read;
  bool local;
};

class ForkState: public Compiler::State {
 public:
  ForkState(Stack* stack, Local* locals, SavedValue* saved, Event* predecessor,
            unsigned logicalIp):
    stack(stack),
    locals(locals),
    saved(saved),
    predecessor(predecessor),
    logicalIp(logicalIp),
    readCount(0)
  { }

  Stack* stack;
  Local* locals;
  SavedValue* saved;
  Event* predecessor;
  unsigned logicalIp;
  unsigned readCount;
  ForkElement elements[0];
};

class MySubroutine: public Compiler::Subroutine {
 public:
  MySubroutine(): forkState(0) { }

  ForkState* forkState;
};

class LogicalInstruction {
 public:
  LogicalInstruction(int index, Stack* stack, Local* locals):
    firstEvent(0), lastEvent(0), immediatePredecessor(0), stack(stack),
    locals(locals), machineOffset(0), subroutine(0), index(index)
  { }

  Event* firstEvent;
  Event* lastEvent;
  LogicalInstruction* immediatePredecessor;
  Stack* stack;
  Local* locals;
  Promise* machineOffset;
  MySubroutine* subroutine;
  int index;
};

class Register {
 public:
  Register(int number):
    value(0), site(0), number(number), size(0), refCount(0),
    freezeCount(0), acquireCount(0), reserved(false)
  { }

  Value* value;
  RegisterSite* site;
  int number;
  unsigned size;
  unsigned refCount;
  unsigned freezeCount;
  unsigned acquireCount;
  bool reserved;
};

class FrameResource {
 public:
  Value* value;
  MemorySite* site;
  unsigned size;
  unsigned freezeCount;
  unsigned acquireCount;
  bool includeNeighbor;
};

class ConstantPoolNode {
 public:
  ConstantPoolNode(Promise* promise): promise(promise), next(0) { }

  Promise* promise;
  ConstantPoolNode* next;
};

class Read {
 public:
  Read(unsigned size):
    value(0), event(0), eventNext(0), size(size)
  { }

  virtual Site* pickSite(Context* c, Value* v, bool includeBuddies) = 0;

  virtual Site* allocateSite(Context* c) = 0;

  virtual bool intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex) = 0;
  
  virtual bool valid() = 0;

  virtual void append(Context* c, Read* r) = 0;

  virtual Read* next(Context* c) = 0;

  Value* value;
  Event* event;
  Read* eventNext;
  unsigned size;
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
    reads(0), lastRead(0), sites(site), source(0), target(target), buddy(this),
    local(false), thief(false)
  { }

  virtual void addPredecessor(Context*, Event*) { }
  
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  Site* target;
  Value* buddy;
  bool local;
  bool thief;
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
    stack(0),
    locals(0),
    saved(0),
    predecessor(0),
    logicalCode(0),
    registers
    (static_cast<Register**>
     (zone->allocate(sizeof(Register*) * arch->registerCount()))),
    frameResources(0),
    firstConstant(0),
    lastConstant(0),
    machineCode(0),
    firstEvent(0),
    lastEvent(0),
    forkState(0),
    subroutine(0),
    forfeitedSite(0),
    logicalIp(-1),
    constantCount(0),
    logicalCodeLength(0),
    parameterFootprint(0),
    localFootprint(0),
    machineCodeSize(0),
    alignedFrameSize(0),
    availableRegisterCount(arch->registerCount()),
    constantCompare(CompareNone),
    pass(ScanPass)
  {
    for (unsigned i = 0; i < arch->registerCount(); ++i) {
      registers[i] = new (zone->allocate(sizeof(Register))) Register(i);
      if (arch->reserved(i)) {
        registers[i]->reserved = true;
        -- availableRegisterCount;
      }
    }
  }

  System* system;
  Assembler* assembler;
  Assembler::Architecture* arch;
  Zone* zone;
  Compiler::Client* client;
  Stack* stack;
  Local* locals;
  SavedValue* saved;
  Event* predecessor;
  LogicalInstruction** logicalCode;
  Register** registers;
  FrameResource* frameResources;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  uint8_t* machineCode;
  Event* firstEvent;
  Event* lastEvent;
  ForkState* forkState;
  MySubroutine* subroutine;
  Site* forfeitedSite;
  int logicalIp;
  unsigned constantCount;
  unsigned logicalCodeLength;
  unsigned parameterFootprint;
  unsigned localFootprint;
  unsigned machineCodeSize;
  unsigned alignedFrameSize;
  unsigned availableRegisterCount;
  ConstantCompare constantCompare;
  Pass pass;
};

class PoolPromise: public Promise {
 public:
  PoolPromise(Context* c, int key): c(c), key(key) { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>
        (c->machineCode + pad(c->machineCodeSize) + (key * BytesPerWord));
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

  CodePromise(Context* c, Promise* offset):
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
  Promise* offset;
  CodePromise* next;
};

unsigned
machineOffset(Context* c, int logicalIp)
{
  return c->logicalCode[logicalIp]->machineOffset->value();
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

Cell*
append(Context* c, Cell* first, Cell* second)
{
  if (first) {
    if (second) {
      Cell* start = cons(c, first->value, second);
      Cell* end = start;
      for (Cell* cell = first->next; cell; cell = cell->next) {
        Cell* n = cons(c, cell->value, second);
        end->next = n;
        end = n;
      }
      return start;
    } else {
      return first;
    }
  } else {
    return second;
  }
}

Cell*
reverseDestroy(Cell* cell)
{
  Cell* previous = 0;
  while (cell) {
    Cell* next = cell->next;
    cell->next = previous;
    previous = cell;
    cell = next;
  }
  return previous;
}

class StubReadPair {
 public:
  Value* value;
  StubRead* read;
};

class JunctionState {
 public:
  JunctionState(unsigned frameFootprint): frameFootprint(frameFootprint) { }

  unsigned frameFootprint;
  StubReadPair reads[0];
};

class Link {
 public:
  Link(Event* predecessor, Link* nextPredecessor, Event* successor,
       Link* nextSuccessor, ForkState* forkState):
    predecessor(predecessor), nextPredecessor(nextPredecessor),
    successor(successor), nextSuccessor(nextSuccessor), forkState(forkState),
    junctionState(0)
  { }

  Event* predecessor;
  Link* nextPredecessor;
  Event* successor;
  Link* nextSuccessor;
  ForkState* forkState;
  JunctionState* junctionState;
};

Link*
link(Context* c, Event* predecessor, Link* nextPredecessor, Event* successor,
     Link* nextSuccessor, ForkState* forkState)
{
  return new (c->zone->allocate(sizeof(Link))) Link
    (predecessor, nextPredecessor, successor, nextSuccessor, forkState);
}

unsigned
countPredecessors(Link* link)
{
  unsigned c = 0;
  for (; link; link = link->nextPredecessor) ++ c;
  return c;
}

Link*
lastPredecessor(Link* link)
{
  while (link->nextPredecessor) link = link->nextPredecessor;
  return link;
}

unsigned
countSuccessors(Link* link)
{
  unsigned c = 0;
  for (; link; link = link->nextSuccessor) ++ c;
  return c;
}

class Event {
 public:
  Event(Context* c):
    next(0), stackBefore(c->stack), localsBefore(c->locals),
    stackAfter(0), localsAfter(0), promises(0), reads(0),
    junctionSites(0), snapshots(0), predecessors(0), successors(0),
    visitLinks(0), block(0), logicalInstruction(c->logicalCode[c->logicalIp]),
    readCount(0)
  { }

  virtual const char* name() = 0;

  virtual void compile(Context* c) = 0;

  virtual bool isBranch() { return false; }

  Event* next;
  Stack* stackBefore;
  Local* localsBefore;
  Stack* stackAfter;
  Local* localsAfter;
  CodePromise* promises;
  Read* reads;
  Site** junctionSites;
  Snapshot* snapshots;
  Link* predecessors;
  Link* successors;
  Cell* visitLinks;
  Block* block;
  LogicalInstruction* logicalInstruction;
  unsigned readCount;
};

int
frameIndex(Context* c, int index, unsigned footprint)
{
  return c->alignedFrameSize + c->parameterFootprint - index - footprint;
}

unsigned
frameIndexToOffset(Context* c, unsigned frameIndex)
{
  return ((frameIndex >= c->alignedFrameSize) ?
          (frameIndex
           + (c->arch->frameFooterSize() * 2)
           + c->arch->frameHeaderSize()) :
          (frameIndex
           + c->arch->frameFooterSize())) * BytesPerWord;
}

unsigned
offsetToFrameIndex(Context* c, unsigned offset)
{
  unsigned normalizedOffset = offset / BytesPerWord;

  return ((normalizedOffset
           >= c->alignedFrameSize
           + c->arch->frameFooterSize()) ?
          (normalizedOffset
           - (c->arch->frameFooterSize() * 2)
           - c->arch->frameHeaderSize()) :
          (normalizedOffset
           - c->arch->frameFooterSize()));
}

class FrameIterator {
 public:
  class Element {
   public:
    Element(Value* value, unsigned localIndex, unsigned footprint):
      value(value), localIndex(localIndex), footprint(footprint)
    { }

    Value* const value;
    const unsigned localIndex;
    const unsigned footprint;
  };

  FrameIterator(Context* c, Stack* stack, Local* locals):
    stack(stack), locals(locals), localIndex(c->localFootprint - 1)
  { }

  bool hasMore() {
    while (localIndex >= 0 and locals[localIndex].value == 0) -- localIndex;

    return stack != 0 or localIndex >= 0;
  }

  Element next(Context* c) {
    Value* v;
    unsigned li;
    unsigned footprint;
    if (stack) {
      Stack* s = stack;
      v = s->value;
      li = s->index + c->localFootprint;
      footprint = s->footprint;
      stack = stack->next;
    } else {
      Local* l = locals + localIndex;
      v = l->value;
      li = localIndex;
      footprint = l->footprint;
      -- localIndex;
    }
    return Element(v, li, footprint);
  }

  Stack* stack;
  Local* locals;
  int localIndex;
};

int
frameIndex(Context* c, FrameIterator::Element* element)
{
  return frameIndex(c, element->localIndex, element->footprint);
}

class SiteIterator {
 public:
  SiteIterator(Value* v, bool includeBuddies = true):
    originalValue(v),
    currentValue(v),
    includeBuddies(includeBuddies),
    next_(findNext(&(v->sites))),
    previous(0)
  { }

  Site** findNext(Site** p) {
    if (*p) {
      return p;
    } else {
      if (includeBuddies) {
        for (Value* v = currentValue->buddy;
             v != originalValue;
             v = v->buddy)
        {
          if (v->sites) {
            currentValue = v;
            return &(v->sites);
          }
        }
      }
      return 0;
    }
  }

  bool hasMore() {
    if (previous) {
      next_ = findNext(&((*previous)->next));
      previous = 0;
    }
    return next_ != 0;
  }

  Site* next() {
    previous = next_;
    return *previous;
  }

  void remove(Context* c) {
    (*previous)->release(c);
    *previous = (*previous)->next;
    next_ = findNext(previous);
    previous = 0;
  }

  Value* originalValue;
  Value* currentValue;
  bool includeBuddies;
  Site** next_;
  Site** previous;
};

bool
hasMoreThanOneSite(Value* v)
{
  SiteIterator it(v);
  if (it.hasMore()) {
    it.next();
    return it.hasMore();
  } else {
    return false;
  }
}

bool
hasSite(Value* v)
{
  SiteIterator it(v);
  return it.hasMore();
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
addSite(Context* c, Stack* stack, Local* locals, unsigned size, Value* v,
        Site* s)
{
  if (not findSite(c, v, s)) {
    if (DebugSites) {
      char buffer[256]; s->toString(c, buffer, 256);
      fprintf(stderr, "add site %s to %p\n", buffer, v);
    }
    s->acquire(c, stack, locals, size, v);
    s->next = v->sites;
    v->sites = s;
  }
}

void
removeSite(Context* c, Value* v, Site* s)
{
  for (SiteIterator it(v); it.hasMore();) {
    if (s == it.next()) {
      if (DebugSites) {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "remove site %s from %p\n", buffer, v);
      }
      it.remove(c);
      break;
    }
  }
  if (DebugSites) {
    fprintf(stderr, "%p has more: %d\n", v, hasSite(v));
  }
  assert(c, not findSite(c, v, s));
}

void
clearSites(Context* c, Value* v)
{
  if (DebugSites) {
    fprintf(stderr, "clear sites for %p\n", v);
  }
  for (SiteIterator it(v); it.hasMore();) {
    it.next();
    it.remove(c);
  }
}

bool
valid(Read* r)
{
  return r and r->valid();
}

Read*
live(Value* v)
{
  if (valid(v->reads)) return v->reads;

  for (Value* p = v->buddy; p != v; p = p->buddy) {
    if (valid(p->reads)) return p->reads;
  }

  return 0;
}

Read*
liveNext(Context* c, Value* v)
{
  Read* r = v->reads->next(c);
  if (valid(r)) return r;

  for (Value* p = v->buddy; p != v; p = p->buddy) {
    if (valid(p->reads)) return p->reads;
  }

  return 0;
}

void
nextRead(Context* c, Event* e UNUSED, Value* v)
{
  assert(c, e == v->reads->event);

  if (DebugReads) {
    fprintf(stderr, "pop read %p from %p next %p event %p (%s)\n",
            v->reads, v, v->reads->next(c), e, (e ? e->name() : 0));
  }

  v->reads = v->reads->next(c);
  if (not live(v)) {
    clearSites(c, v);
  }
}

ConstantSite*
constantSite(Context* c, Promise* value);

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual void toString(Context*, char* buffer, unsigned bufferSize) {
    if (value.value->resolved()) {
      snprintf(buffer, bufferSize, "constant %"LLD, value.value->value());
    } else {
      snprintf(buffer, bufferSize, "constant unresolved");
    }
  }

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

  virtual Site* copy(Context* c) {
    return constantSite(c, value.value);
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

AddressSite*
addressSite(Context* c, Promise* address);

class AddressSite: public Site {
 public:
  AddressSite(Promise* address): address(address) { }

  virtual void toString(Context*, char* buffer, unsigned bufferSize) {
    if (address.address->resolved()) {
      snprintf(buffer, bufferSize, "address %"LLD, address.address->value());
    } else {
      snprintf(buffer, bufferSize, "address unresolved");
    }
  }

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

  virtual Site* copy(Context* c) {
    return addressSite(c, address.address);
  }

  Assembler::Address address;
};

AddressSite*
addressSite(Context* c, Promise* address)
{
  return new (c->zone->allocate(sizeof(AddressSite))) AddressSite(address);
}

bool
buddies(Value* a, Value* b)
{
  if (a == b) return true;
  for (Value* p = a->buddy; p != a; p = p->buddy) {
    if (p == b) return true;
  }
  return false;
}

void
freeze(Context* c, Register* r, Value* v)
{
  if (DebugRegisters) {
    fprintf(stderr, "freeze %d to %d for %p\n",
            r->number, r->freezeCount + 1, v);
  }

  ++ r->freezeCount;
  if ((not r->reserved) and r->freezeCount == 1) {
    assert(c, c->availableRegisterCount);
    -- c->availableRegisterCount;
  }
}

void
thaw(Context* c, Register* r, Value* v)
{
  assert(c, r->freezeCount);

  if (DebugRegisters) {
    fprintf(stderr, "thaw %d to %d for %p\n",
            r->number, r->freezeCount - 1, v);
  }

  -- r->freezeCount;
  if ((not r->reserved) and r->freezeCount == 0) {
    ++ c->availableRegisterCount;
  }
}

void
release(Context* c, Register* r);

Register*
validate(Context* c, uint32_t mask, Stack* stack, Local* locals,
         unsigned size, Value* value, RegisterSite* site, Register* current);

RegisterSite*
freeRegisterSite(Context* c, uint64_t mask = ~static_cast<uint64_t>(0));

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

  virtual void toString(Context* c, char* buffer, unsigned bufferSize) {
    if (low) {
      sync(c);

      snprintf(buffer, bufferSize, "%p register %d %d",
               this, register_.low, register_.high);
    } else {
      snprintf(buffer, bufferSize, "%p register unacquired", this);
    }
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

  virtual void acquire(Context* c, Stack* stack, Local* locals, unsigned size,
                       Value* v)
  {
    low = ::validate(c, mask, stack, locals, size, v, this, low);
    if (size > BytesPerWord) {
      ::freeze(c, low, v);
      high = ::validate(c, mask >> 32, stack, locals, size, v, this, high);
      ::thaw(c, low, v);
    }
  }

  virtual void release(Context* c) {
    assert(c, low);

    ::release(c, low);
    if (high) {
      ::release(c, high);
    }
  }

  virtual void freeze(Context* c, Value* v, unsigned size) {
    assert(c, low);

    ::freeze(c, low, v);
    if (size > BytesPerWord) {
      ::freeze(c, high, v);
    }
  }

  virtual void thaw(Context* c, Value* v, unsigned size) {
    assert(c, low);

    ::thaw(c, low, v);
    if (size > BytesPerWord) {
      ::thaw(c, high, v);
    }
  }

  virtual bool usesRegister(Context* c, int r) {
    sync(c);
    return register_.low == r or register_.high == r;
  }

  virtual OperandType type(Context*) {
    return RegisterOperand;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context* c) {
    sync(c);
    return &register_;
  }

  virtual Site* copy(Context* c) {
    uint64_t mask;
    
    if (low) {
      sync(c);
      mask = static_cast<uint64_t>(1) << register_.low;
      if (register_.high != NoRegister) {
        mask |= static_cast<uint64_t>(1) << (register_.high + 32);
      }
    } else {
      mask = this->mask;
    }

    return freeRegisterSite(c, mask);
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
freeRegisterSite(Context* c, uint64_t mask)
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

void
acquireFrameIndex(Context* c, int index, Stack* stack, Local* locals,
                  unsigned newSize, Value* newValue, MemorySite* newSite,
                  bool includeNeighbor);

void
releaseFrameIndex(Context* c, int index);

void
freezeFrameIndex(Context* c, int index, Value* v, unsigned size);

void
thawFrameIndex(Context* c, int index, Value* v, unsigned size);

MemorySite*
memorySite(Context* c, int base, int offset = 0, int index = NoRegister,
           unsigned scale = 1);

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

  virtual void toString(Context* c, char* buffer, unsigned bufferSize) {
    if (base) {
      sync(c);

      snprintf(buffer, bufferSize, "memory %d 0x%x %d %d",
               value.base, value.offset, value.index, value.scale);
    } else {
      snprintf(buffer, bufferSize, "memory unacquired");
    }
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
              && static_cast<int>(frameIndexToOffset(c, frameIndex))
              == value.offset);
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  virtual void acquire(Context* c, Stack* stack, Local* locals, unsigned size,
                       Value* v)
  {
    base = increment(c, value.base);
    if (value.index != NoRegister) {
      index = increment(c, value.index);
    }

    if (value.base == c->arch->stack()) {
      assert(c, value.index == NoRegister);
      acquireFrameIndex
        (c, offsetToFrameIndex(c, value.offset), stack, locals, size, v,
         this, true);
    }
  }

  virtual void release(Context* c) {
    if (value.base == c->arch->stack()) {
      assert(c, value.index == NoRegister);
      releaseFrameIndex(c, offsetToFrameIndex(c, value.offset));
    }

    decrement(c, base);
    if (index) {
      decrement(c, index);
    }
  }

  virtual void freeze(Context* c, Value* v, unsigned size) {
    if (value.base == c->arch->stack()) {
      freezeFrameIndex(c, offsetToFrameIndex(c, value.offset), v, size);
    }
  }

  virtual void thaw(Context* c, Value* v, unsigned size) {
    if (value.base == c->arch->stack()) {
      thawFrameIndex(c, offsetToFrameIndex(c, value.offset), v, size);
    }
  }

  virtual bool usesRegister(Context* c, int r) {
    sync(c);
    return value.base == r or value.index == r;
  }

  virtual OperandType type(Context*) {
    return MemoryOperand;
  }

  virtual Assembler::Operand* asAssemblerOperand(Context* c) {
    sync(c);
    return &value;
  }

  virtual Site* copy(Context* c) {
    return memorySite(c, value.base, value.offset, value.index, value.scale);
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

MemorySite*
frameSite(Context* c, int frameIndex)
{
  assert(c, frameIndex >= 0);
  return memorySite
    (c, c->arch->stack(), frameIndexToOffset(c, frameIndex));
}

Site*
targetOrNull(Context* c, Value* v, Read* r)
{
  if (v->target) {
    return v->target;
  } else {
    return r->allocateSite(c);
  }
}

Site*
targetOrNull(Context* c, Value* v)
{
  if (v->target) {
    return v->target;
  } else if (live(v)) {
    return v->reads->allocateSite(c);
  }
  return 0;
}

Site*
pickSite(Context* c, Value* value, uint8_t typeMask, uint64_t registerMask,
         int frameIndex, bool includeBuddies)
{
  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (SiteIterator it(value, includeBuddies); it.hasMore();) {
    Site* s = it.next();
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
    return 0;
  }
}

class SingleRead: public Read {
 public:
  SingleRead(unsigned size, uint8_t typeMask, uint64_t registerMask,
             int frameIndex):
    Read(size), next_(0), typeMask(typeMask), registerMask(registerMask),
    frameIndex(frameIndex)
  { }

  virtual Site* pickSite(Context* c, Value* value, bool includeBuddies) {
    return ::pickSite
      (c, value, typeMask, registerMask, frameIndex, includeBuddies);
  }

  virtual Site* allocateSite(Context* c) {
    return ::allocateSite(c, typeMask, registerMask, frameIndex);
  }

  virtual bool intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex)
  {
    *typeMask &= this->typeMask;
    *registerMask &= this->registerMask;
    *frameIndex = intersectFrameIndexes(*frameIndex, this->frameIndex);
    return true;
  }
  
  virtual bool valid() {
    return true;
  }

  virtual void append(Context* c UNUSED, Read* r) {
    assert(c, next_ == 0);
    next_ = r;
  }

  virtual Read* next(Context*) {
    return next_;
  }

  Read* next_;
  uint8_t typeMask;
  uint64_t registerMask;
  int frameIndex;
};

Read*
read(Context* c, unsigned size, uint8_t typeMask, uint64_t registerMask,
     int frameIndex)
{
  assert(c, (typeMask != 1 << MemoryOperand) or frameIndex >= 0);
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
  MultiRead(unsigned size):
    Read(size), reads(0), lastRead(0), firstTarget(0), lastTarget(0),
    visited(false)
  { }

  virtual Site* pickSite(Context* c, Value* value, bool includeBuddies) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::pickSite
      (c, value, typeMask, registerMask, frameIndex, includeBuddies);
  }

  virtual Site* allocateSite(Context* c) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::allocateSite(c, typeMask, registerMask, frameIndex);
  }

  virtual bool intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex)
  {
    bool result = false;
    if (not visited) {
      visited = true;
      for (Cell** cell = &reads; *cell;) {
        Read* r = static_cast<Read*>((*cell)->value);
        bool valid = r->intersect(typeMask, registerMask, frameIndex);
        if (valid) {
          result = true;
          cell = &((*cell)->next);
        } else {
          *cell = (*cell)->next;
        }
      }
      visited = false;
    }
    return result;
  }

  virtual bool valid() {
    bool result = false;
    if (not visited) {
      visited = true;
      for (Cell** cell = &reads; *cell;) {
        Read* r = static_cast<Read*>((*cell)->value);
        if (r->valid()) {
          result = true;
          cell = &((*cell)->next);
        } else {
          *cell = (*cell)->next;
        }
      }
      visited = false;
    }
    return result;
  }

  virtual void append(Context* c, Read* r) {
    Cell* cell = cons(c, r, 0);
    if (lastRead == 0) {
      reads = cell;
    } else {
      lastRead->next = cell;
    }
    lastRead = cell;

    lastTarget->value = r;
  }

  virtual Read* next(Context* c) {
    abort(c);
  }

  void allocateTarget(Context* c) {
    Cell* cell = cons(c, 0, 0);
    if (lastTarget) {
      lastTarget->next = cell;
    } else {
      firstTarget = cell;
    }
    lastTarget = cell;
  }

  Read* nextTarget() {
    Read* r = static_cast<Read*>(firstTarget->value);
    firstTarget = firstTarget->next;
    return r;
  }

  Cell* reads;
  Cell* lastRead;
  Cell* firstTarget;
  Cell* lastTarget;
  bool visited;
};

MultiRead*
multiRead(Context* c, unsigned size)
{
  return new (c->zone->allocate(sizeof(MultiRead))) MultiRead(size);
}

class StubRead: public Read {
 public:
  StubRead(unsigned size):
    Read(size), next_(0), read(0), visited(false), valid_(true)
  { }

  virtual Site* pickSite(Context* c, Value* value, bool includeBuddies) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::pickSite
      (c, value, typeMask, registerMask, frameIndex, includeBuddies);
  }

  virtual Site* allocateSite(Context* c) {
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    intersect(&typeMask, &registerMask, &frameIndex);

    return ::allocateSite(c, typeMask, registerMask, frameIndex);
  }

  virtual bool intersect(uint8_t* typeMask, uint64_t* registerMask,
                         int* frameIndex)
  {
    if (not visited) {
      visited = true;
      if (read) {
        bool valid = read->intersect(typeMask, registerMask, frameIndex);
        if (not valid) {
          read = 0;
        }
      }
      visited = false;
    }
    return valid_;
  }

  virtual bool valid() {
    return valid_;
  }

  virtual void append(Context* c UNUSED, Read* r) {
    assert(c, next_ == 0);
    next_ = r;
  }

  virtual Read* next(Context*) {
    return next_;
  }

  Read* next_;
  Read* read;
  bool visited;
  bool valid_;
};

StubRead*
stubRead(Context* c, unsigned size)
{
  return new (c->zone->allocate(sizeof(StubRead))) StubRead(size);
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
targetOrRegister(Context* c, Value* v, Read* r)
{
  Site* s = targetOrNull(c, v, r);
  if (s) {
    return s;
  } else {
    return freeRegisterSite(c);
  }
}

Site*
pick(Context* c, Value* value, Site* target, unsigned* cost,
     bool includeBuddies)
{
  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (SiteIterator it(value, includeBuddies); it.hasMore();) {
    Site* s = it.next();
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
move(Context* c, Stack* stack, Local* locals, unsigned size, Value* value,
     Site* src, Site* dst)
{
  src->freeze(c, value, size);

  addSite(c, stack, locals, size, value, dst);

  src->thaw(c, value, size);

  if (dst->type(c) == MemoryOperand
      and (src->type(c) == MemoryOperand
           or src->type(c) == AddressOperand))
  {
    src->freeze(c, value, size);

    Site* tmp = freeRegisterSite(c);
    addSite(c, stack, locals, size, value, tmp);

    src->thaw(c, value, size);

    if (DebugMoves) {
      char srcb[256]; src->toString(c, srcb, 256);
      char tmpb[256]; tmp->toString(c, tmpb, 256);
      fprintf(stderr, "move %s to %s for %p\n", srcb, tmpb, value);
    }
      
    apply(c, Move, size, src, size, tmp);

    src = tmp;
  }

  if (DebugMoves) {
    char srcb[256]; src->toString(c, srcb, 256);
    char dstb[256]; dst->toString(c, dstb, 256);
    fprintf(stderr, "move %s to %s for %p\n", srcb, dstb, value);
  }
  
  apply(c, Move, size, src, size, dst);
}

void
toString(Context* c, Site* sites, char* buffer, unsigned size)
{
  if (sites) {
    sites->toString(c, buffer, size);
    if (sites->next) {
      unsigned length = strlen(buffer);
      assert(c, length + 2 < size);
      memcpy(buffer + length, ", ", 2);
      length += 2;
      toString(c, sites->next, buffer + length, size - length);
    }
  } else {
    assert(c, size);
    *buffer = 0;
  }
}

bool
find(Value* needle, Value* haystack)
{
  if (haystack) {
    if (needle == haystack) return true;
    
    for (Value* p = haystack->buddy; p != haystack; p = p->buddy) {
      if (needle == p) return true;
    }
  }

  return false;
}

bool
frameIndexAvailable(Context* c, int index)
{
  return c->frameResources[index].freezeCount == 0;
}

bool
save(Context* c, Site* src, Value* v, unsigned size, Stack* stack,
     Local* locals)
{
  int index = NoFrameIndex;
  for (unsigned li = 0; li < c->localFootprint; ++li) {
    Local* local = locals + li;
    if (find(v, local->value)) {
      int fi = frameIndex(c, li, local->footprint);
      if (frameIndexAvailable(c, fi)) {
        index = fi;
        break;
      }
    }
  }

  if (index == NoFrameIndex) {
    for (Stack* s = stack; s; s = s->next) {
      if (find(v, s->value)) {
        int fi = ::frameIndex
          (c, s->index + c->localFootprint, s->footprint);
        if (frameIndexAvailable(c, fi)) {
          index = fi;
          break;
        }
      }
    }
  }

  if (index != NoFrameIndex) {
    move(c, stack, locals, size, v, src, frameSite(c, index));
    return true;
  } else {
    if (DebugRegisters or DebugFrameIndexes) {
      fprintf(stderr, "unable to save %p\n", v);
    }
    return false;
  }
}

bool
registerAvailable(Context* c, uint32_t mask)
{
  for (int i = c->arch->registerCount() - 1; i >= 0; --i) {
    if ((1 << i) & mask) {
      Register* r = c->registers[i];
      if (not (r->reserved or r->freezeCount)) {
        return true;
      } else if ((static_cast<uint32_t>(1) << i) == mask) {
        return false;
      }
    }
  }
  return false;
}

bool
available(Context* c, unsigned size, uint8_t typeMask, uint64_t registerMask,
          int frameIndex)
{
  return
    ((typeMask & (1 << RegisterOperand))
     and registerMask
     and (size <= BytesPerWord or registerAvailable(c, registerMask >> 32))
     and registerAvailable(c, registerMask))
    or ((typeMask & (1 << MemoryOperand))
        and (frameIndex == AnyFrameIndex
             or (frameIndex >= 0 and frameIndexAvailable(c, frameIndex))));
}

bool
registerUnused(Context* c, uint32_t mask)
{
  for (int i = c->arch->registerCount() - 1; i >= 0; --i) {
    if ((1 << i) & mask) {
      Register* r = c->registers[i];
      if (not (r->reserved or r->value or r->refCount)) {
        return true;
      } else if ((static_cast<uint32_t>(1) << i) == mask) {
        return false;
      }
    }
  }
  return false;
}

bool
frameIndexUnused(Context* c, int index)
{
  return c->frameResources[index].value == 0;
}

bool
unused(Context* c, unsigned size, uint8_t typeMask, uint64_t registerMask,
       int frameIndex)
{
  return
    ((typeMask & (1 << RegisterOperand))
     and registerMask
     and (size <= BytesPerWord or registerUnused(c, registerMask >> 32))
     and registerUnused(c, registerMask))
    or ((typeMask & (1 << MemoryOperand))
        and (frameIndex == AnyFrameIndex
             or (frameIndex >= 0 and frameIndexUnused(c, frameIndex))));
}

bool
trySteal(Context* c, Site* site, Value* thief, Value* victim, unsigned size,
         Stack* stack, Local* locals)
{
  bool success = true;
  if (not hasMoreThanOneSite(victim)) {
    thief->thief = true;

    Read* read = live(victim);
    uint8_t typeMask = ~static_cast<uint8_t>(0);
    uint64_t registerMask = ~static_cast<uint64_t>(0);
    int frameIndex = AnyFrameIndex;
    read->intersect(&typeMask, &registerMask, &frameIndex);

    if (pickSite(c, victim, typeMask, registerMask, frameIndex, true)
        or victim->thief)
    {
      success = save(c, site, victim, size, stack, locals);
    } else if (unused(c, size, typeMask, registerMask, frameIndex)) {
      move(c, stack, locals, size, victim, site,
           allocateSite(c, typeMask, registerMask, frameIndex));
      success = true;
    } else {
      success = save(c, site, victim, size, stack, locals);
      if ((not success)
          and available(c, size, typeMask, registerMask, frameIndex))
      {
        move(c, stack, locals, size, victim, site,
             allocateSite(c, typeMask, registerMask, frameIndex));
        success = true;
      }
    }

    thief->thief = false;
  }

  if (success) {
    removeSite(c, victim, site);
  }

  return success;
}

bool
trySteal(Context* c, Register* r, Value* thief, Stack* stack, Local* locals)
{
  assert(c, r->refCount == 0);

  Value* v = r->value;
  assert(c, live(v));

  if (DebugRegisters) {
    fprintf(stderr, "try steal %d from %p\n", r->number, v);
  }

  return trySteal(c, r->site, thief, r->value, r->size, stack, locals);
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
  return used(c, r) and not hasMoreThanOneSite(r->value);
}

void
releaseRegister(Context* c, Value* v, unsigned frameIndex,
                unsigned sizeInBytes, int r)
{
  Site* source = 0;
  bool remaining = false;
  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
    if (s->usesRegister(c, r)) {
      if (DebugRegisters) {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "%p (%s) in %p at %d uses %d\n",
                s, buffer, v, frameIndex, r);
      }

      source = s;
      it.remove(c);
    } else {
      if (DebugRegisters) {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "%p (%s) in %p at %d does not use %d\n",
                s, buffer, v, frameIndex, r);
      }

      if (s != c->forfeitedSite) {
        remaining = true;
      }
    }
  }

  if (live(v) and not remaining) {
    addSite(c, 0, 0, sizeInBytes, v, source);
    move(c, c->stack, c->locals, sizeInBytes, v, source,
         frameSite(c, frameIndex));
    removeSite(c, v, source);
  }

  if (DebugRegisters) {
    char buffer[256]; toString(c, v->sites, buffer, 256);
    fprintf(stderr, "%p is left with %s\n", v, buffer);
  }
}

unsigned
footprintSizeInBytes(unsigned footprint)
{
  if (BytesPerWord == 8) {
    return 8;
  } else {
    return footprint * 4;
  }
}

bool
remainingForfeited(Context* c, Value* v, Site* toRemove)
{
  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
    if (not (s == toRemove or s == c->forfeitedSite)) {
      return false;
    }
  }
  return true;
}

void
releaseRegister(Context* c, int r)
{
  Register* reg = c->registers[r];
  if (used(c, reg)
      and (not usedExclusively(c, reg))
      and (not remainingForfeited(c, reg->value, reg->site)))
  {
    removeSite(c, reg->value, reg->site);

    if (reg->refCount == 0) return;
  }

  for (FrameIterator it(c, c->stack, c->locals); it.hasMore();) {
    FrameIterator::Element e = it.next(c);
    releaseRegister(c, e.value, frameIndex(c, &e),
                    footprintSizeInBytes(e.footprint), r);
  }
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
acquire(Context* c, uint32_t mask, Stack* stack, Local* locals,
        unsigned newSize, Value* newValue, RegisterSite* newSite);

Register*
replace(Context* c, Register* r, Value* newValue, Stack* stack, Local* locals)
{
  uint32_t mask = (r->freezeCount ? r->site->mask : ~0);

  freeze(c, r, r->value);
  Register* s = acquire(c, mask, stack, locals, r->size, newValue, r->site);
  thaw(c, r, r->value);

  if (DebugRegisters) {
    fprintf(stderr, "replace %d with %d\n", r->number, s->number);
  }

  swap(c, r, s);

  return s;
}

Register*
acquire(Context* c, uint32_t mask, Stack* stack, Local* locals,
        unsigned newSize, Value* newValue, RegisterSite* newSite)
{
  Register* r = pickRegister(c, mask);

  if (r->reserved) return r;

  if (r->refCount) {
    r = replace(c, r, newValue, stack, locals);
  } else {
    Value* oldValue = r->value;
    if (oldValue
        and oldValue != newValue
        and findSite(c, oldValue, r->site))
    {
      if (buddies(oldValue, newValue)) {
        removeSite(c, oldValue, r->site);
      } else {
        assert(c, r->freezeCount == 0);
        if (not trySteal(c, r, newValue, stack, locals)) {
          r = replace(c, r, newValue, stack, locals);
        }
      }
    }
  }

  if (r->value == newValue) {
    ++ r->acquireCount;
  } else {
    r->acquireCount = 1;
  }

  if (DebugRegisters) {
    fprintf(stderr, "acquire %d to %d for %p\n",
            r->number, r->acquireCount, newValue);
  }

  r->size = newSize;
  r->value = newValue;
  r->site = newSite;

  return r;
}

void
release(Context* c UNUSED, Register* r)
{
  if (r->reserved) return;

  assert(c, r->acquireCount > 0);

  if (DebugRegisters) {
    fprintf(stderr, "release %d to %d for %p\n",
            r->number, r->acquireCount - 1, r->value);
  }

  if ((-- r->acquireCount) == 0) {
    r->size = 0;
    r->value = 0;
    r->site = 0;
  }
}

Register*
validate(Context* c, uint32_t mask, Stack* stack, Local* locals,
         unsigned size, Value* value, RegisterSite* site, Register* current)
{
  if (current and (mask & (1 << current->number))) {
    if (DebugRegisters and (current->value == value or current->value == 0)) {
      fprintf(stderr,
              "validate acquire %d to %d for %p\n",
              current->number, current->value ? current->acquireCount + 1 : 1,
              value);
    }

    if (current->reserved or current->value == value) {
      if (not current->reserved) {
        ++ current->acquireCount;
      }

      return current;
    } else if (current->value == 0) {
      current->acquireCount = 1;
      current->size = size;
      current->value = value;
      current->site = site;

      return current;
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

bool
trySteal(Context* c, FrameResource* r, Value* thief, Stack* stack,
         Local* locals)
{
  assert(c, live(r->value));

  int index =  r - c->frameResources;

  if (DebugFrameIndexes) {
    fprintf(stderr,
            "try steal frame index %d offset 0x%x from value %p site %p\n",
            index, frameIndexToOffset(c, index), r->value, r->site);
  }

  assert(c, r->freezeCount == 0);

  Value* victim = r->value;
  freezeFrameIndex(c, index, victim, r->size);
    
  bool success = trySteal
    (c, r->site, thief, victim, r->size, stack, locals);
    
  thawFrameIndex(c, index, victim, r->size);

  return success;
}

void
acquireFrameIndex(Context* c, int frameIndex, Stack* stack, Local* locals,
                  unsigned newSize, Value* newValue, MemorySite* newSite,
                  bool includeNeighbor)
{
  assert(c, frameIndex >= 0);
  assert(c, frameIndex < static_cast<int>
         (c->alignedFrameSize + c->parameterFootprint));

  FrameResource* r = c->frameResources + frameIndex;

  includeNeighbor &= newSize > BytesPerWord;

  if (includeNeighbor) {
    acquireFrameIndex
      (c, frameIndex + 1, stack, locals, newSize, newValue, newSite, false);
  }

  Value* oldValue = r->value;
  if (oldValue
      and oldValue != newValue
      and findSite(c, oldValue, r->site))
  {
    if (buddies(oldValue, newValue)) {
      removeSite(c, oldValue, r->site);
    } else {
      assert(c, r->freezeCount == 0);
      if (not trySteal(c, r, newValue, stack, locals)) {
        abort(c);
      }
    }
  }

  if (oldValue == newValue) {
    ++ r->acquireCount;
  } else {
    r->acquireCount = 1;
  }

  if (DebugFrameIndexes) {
    fprintf(stderr,
            "acquire frame index %d offset 0x%x to %d for %p\n",
            frameIndex, frameIndexToOffset(c, frameIndex), r->acquireCount,
            newValue);
  }

  r->includeNeighbor = includeNeighbor;
  r->size = newSize;
  r->value = newValue;
  r->site = newSite;
}

void
releaseFrameIndex(Context* c, int frameIndex)
{
  assert(c, frameIndex >= 0);
  assert(c, frameIndex < static_cast<int>
         (c->alignedFrameSize + c->parameterFootprint));

  FrameResource* r = c->frameResources + frameIndex;
  assert(c, r->acquireCount > 0);

  if (r->includeNeighbor) {
    releaseFrameIndex(c, frameIndex + 1);
  }

  if (DebugFrameIndexes) {
    fprintf(stderr, "release frame index %d to %d for %p\n",
            frameIndex, r->acquireCount - 1, r->value);
  }

  if ((-- r->acquireCount) == 0) {
    r->size = 0;
    r->value = 0;
    r->site = 0;
  }
}

void
freezeFrameIndex(Context* c, int frameIndex, Value* v, unsigned size)
{
  assert(c, frameIndex >= 0);
  assert(c, frameIndex < static_cast<int>
         (c->alignedFrameSize + c->parameterFootprint));

  FrameResource* r = c->frameResources + frameIndex;

  if (r->includeNeighbor and (size > BytesPerWord)) {
    freezeFrameIndex(c, frameIndex + 1, v, BytesPerWord);
  }

  if (DebugFrameIndexes) {
    fprintf(stderr, "freeze frame index %d offset 0x%x to %d for %p\n",
            frameIndex, frameIndexToOffset(c, frameIndex), r->freezeCount + 1,
            v);
  }
  
  ++ r->freezeCount;
}

void
thawFrameIndex(Context* c, int frameIndex, Value* v, unsigned size)
{
  assert(c, frameIndex >= 0);
  assert(c, frameIndex < static_cast<int>
         (c->alignedFrameSize + c->parameterFootprint));

  FrameResource* r = c->frameResources + frameIndex;
  assert(c, r->freezeCount);

  if (r->includeNeighbor and (size > BytesPerWord)) {
    thawFrameIndex(c, frameIndex + 1, v, BytesPerWord);
  }

  if (DebugFrameIndexes) {
    fprintf(stderr, "thaw frame index %d offset 0x%x to %d for %p\n",
            frameIndex, frameIndexToOffset(c, frameIndex), r->freezeCount - 1,
            v);
  }
  
  -- r->freezeCount;
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
addRead(Context* c, Event* e, Value* v, Read* r)
{
  if (DebugReads) {
    fprintf(stderr, "add read %p to %p last %p event %p (%s)\n", r, v, v->lastRead, e, (e ? e->name() : 0));
  }

  r->value = v;
  if (e) {
    r->event = e;
    r->eventNext = e->reads;
    e->reads = r;
    ++ e->readCount;
  }

  if (v->lastRead) {
//     if (DebugReads) {
//       fprintf(stderr, "append %p to %p for %p\n", r, v->lastRead, v);
//     }

    v->lastRead->append(c, r);
  } else {
    v->reads = r;
  }
  v->lastRead = r;
}

void
clean(Context* c, Value* v, unsigned popIndex)
{
  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
    if (not (s->match(c, 1 << MemoryOperand, 0, AnyFrameIndex)
             and offsetToFrameIndex
             (c, static_cast<MemorySite*>(s)->value.offset)
             >= popIndex))
    {
//       char buffer[256]; s->toString(c, buffer, 256);
//       fprintf(stderr, "remove %s from %p at %d pop index %d\n",
//               buffer, v, offsetToFrameIndex
//               (c, static_cast<MemorySite*>(s)->value.offset), popIndex);
      it.remove(c);
    }
  }
}

void
clean(Context* c, Event* e, Stack* stack, Local* locals, Read* reads,
      unsigned popIndex)
{
  for (FrameIterator it(c, stack, locals); it.hasMore();) {
    FrameIterator::Element e = it.next(c);
    clean(c, e.value, popIndex);
  }

  for (Read* r = reads; r; r = r->eventNext) {
    nextRead(c, e, r->value);
  }
}

CodePromise*
codePromise(Context* c, Event* e)
{
  return e->promises = new (c->zone->allocate(sizeof(CodePromise)))
    CodePromise(c, e->promises);
}

CodePromise*
codePromise(Context* c, Promise* offset)
{
  return new (c->zone->allocate(sizeof(CodePromise))) CodePromise(c, offset);
}

void
append(Context* c, Event* e);

void
saveLocals(Context* c, Event* e)
{
  for (unsigned li = 0; li < c->localFootprint; ++li) {
    Local* local = e->localsBefore + li;
    if (local->value) {
      if (DebugReads) {
        fprintf(stderr, "local save read %p of footprint %d at %d of %d\n",
                local->value, local->footprint,
                ::frameIndex(c, li, local->footprint),
                c->alignedFrameSize + c->parameterFootprint);
      }

      addRead(c, e, local->value, read
              (c, footprintSizeInBytes(local->footprint), 1 << MemoryOperand,
               0, ::frameIndex(c, li, local->footprint)));
    }
  }
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
    popIndex(0),
    padIndex(0),
    padding(0),
    flags(flags),
    resultSize(resultSize)
  {
    uint32_t mask = ~0;
    Stack* s = argumentStack;
    unsigned index = 0;
    unsigned frameIndex = 0;

    if (argumentCount) {
      unsigned ai = 0;
      while (true) {
        Read* target;
        if (index < c->arch->argumentRegisterCount()) {
          int r = c->arch->argumentRegister(index);
        
          if (DebugReads) {
            fprintf(stderr, "reg %d arg read %p\n", r, s->value);
          }

          target = fixedRegisterRead(c, footprintSizeInBytes(s->footprint), r);
          mask &= ~(1 << r);
        } else {
          if (DebugReads) {
            fprintf(stderr, "stack %d arg read %p\n", frameIndex, s->value);
          }

          target = read(c, footprintSizeInBytes(s->footprint),
                        1 << MemoryOperand, 0, frameIndex);
          frameIndex += s->footprint;
        }
        addRead(c, this, s->value, target);
        index += s->footprint;

        if ((++ ai) < argumentCount) {
          s = s->next;
        } else {
          break;
        }
      }
    }

    if (DebugReads) {
      fprintf(stderr, "address read %p\n", address);
    }

    addRead(c, this, address, read
            (c, BytesPerWord, ~0, (static_cast<uint64_t>(mask) << 32) | mask,
             AnyFrameIndex));

    int footprint = stackArgumentFootprint;
    for (Stack* s = stackBefore; s; s = s->next) {
      if (footprint > 0) {
        if (DebugReads) {
          fprintf(stderr, "stack arg read %p of footprint %d at %d of %d\n",
                  s->value, s->footprint, frameIndex,
                  c->alignedFrameSize + c->parameterFootprint);
        }

        addRead(c, this, s->value, read
                (c, footprintSizeInBytes(s->footprint),
                 1 << MemoryOperand, 0, frameIndex));
      } else {
        unsigned logicalIndex = ::frameIndex
          (c, s->index + c->localFootprint, s->footprint);

        if (DebugReads) {
          fprintf(stderr, "stack save read %p of footprint %d at %d of %d\n",
                  s->value, s->footprint, logicalIndex,
                  c->alignedFrameSize + c->parameterFootprint);
        }

        addRead(c, this, s->value, read
                (c, footprintSizeInBytes(s->footprint), 1 << MemoryOperand,
                 0, logicalIndex));
      }

      footprint -= s->footprint;

      if (footprint == 0) {
        unsigned logicalIndex = ::frameIndex
          (c, s->index + c->localFootprint, s->footprint);

        assert(c, logicalIndex >= frameIndex);

        padding = logicalIndex - frameIndex;
        padIndex = s->index + c->localFootprint;
      }

      frameIndex += s->footprint;
    }

    popIndex = ::frameIndex
      (c, (stackBefore
           ? stackBefore->index
           + stackBefore->footprint
           - stackArgumentFootprint
           : 0)
       + c->localFootprint, 0);

    saveLocals(c, this);
  }

  virtual const char* name() {
    return "CallEvent";
  }

  virtual void compile(Context* c) {
    apply(c, (flags & Compiler::Aligned) ? AlignedCall : Call, BytesPerWord,
          address->source);

    if (traceHandler) {
      traceHandler->handleTrace(codePromise(c, c->assembler->offset()),
                                padIndex, padding);
    }

    clean(c, this, stackBefore, localsBefore, reads, popIndex);

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
  unsigned popIndex;
  unsigned padIndex;
  unsigned padding;
  unsigned flags;
  unsigned resultSize;
};

void
appendCall(Context* c, Value* address, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount,
           unsigned stackArgumentFootprint)
{
  append(c, new (c->zone->allocate(sizeof(CallEvent)))
         CallEvent(c, address, flags, traceHandler, result,
                   resultSize, argumentStack, argumentCount,
                   stackArgumentFootprint));
}

class ReturnEvent: public Event {
 public:
  ReturnEvent(Context* c, unsigned size, Value* value):
    Event(c), value(value)
  {
    if (value) {
      addRead(c, this, value, fixedRegisterRead
              (c, size, c->arch->returnLow(),
               size > BytesPerWord ?
               c->arch->returnHigh() : NoRegister));
    }
  }

  virtual const char* name() {
    return "ReturnEvent";
  }

  virtual void compile(Context* c) {
    if (value) {
      nextRead(c, this, value);
    }

    c->assembler->popFrame();
    c->assembler->apply(Return);
  }

  Value* value;
};

void
appendReturn(Context* c, unsigned size, Value* value)
{
  append(c, new (c->zone->allocate(sizeof(ReturnEvent)))
         ReturnEvent(c, size, value));
}

void
preserve(Context* c, Stack* stack, Local* locals, unsigned size, Value* v,
         Site* s, Read* read)
{
  Site* r = targetOrRegister(c, v, read);
  move(c, stack, locals, size, v, s, r);
}

void
maybePreserve(Context* c, Stack* stack, Local* locals, unsigned size,
              Value* v, Site* s)
{
  Read* r = liveNext(c, v);
  if (r and not hasMoreThanOneSite(v)) {
    preserve(c, stack, locals, size, v, s, r);
  }
}

void
addBuddy(Value* original, Value* buddy)
{
  buddy->buddy = original;
  Value* p = original;
  while (p->buddy != original) p = p->buddy;
  p->buddy = buddy;

//     fprintf(stderr, "add buddy %p to", buddy);
//     for (Value* p = buddy->buddy; p != buddy; p = p->buddy) {
//       fprintf(stderr, " %p", p);
//     }
//     fprintf(stderr, "\n");
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned srcSize, Value* src,
            unsigned dstSize, Value* dst, Read* srcRead, Read* dstRead):
    Event(c), type(type), srcSize(srcSize), src(src), dstSize(dstSize),
    dst(dst), dstRead(dstRead)
  {
    addRead(c, this, src, srcRead);
  }

  virtual const char* name() {
    return "MoveEvent";
  }

  virtual void compile(Context* c) {
    bool isStore = not live(dst);

    Site* target = targetOrRegister(c, dst);
    unsigned cost = src->source->copyCost(c, target);

    if (srcSize != dstSize) cost = 1;

    if (cost) { 
      addSite(c, stackBefore, localsBefore, dstSize, dst, target);
   
      uint8_t typeMask = ~static_cast<uint8_t>(0);
      uint64_t registerMask = ~static_cast<uint64_t>(0);
      int frameIndex = AnyFrameIndex;
      dstRead->intersect(&typeMask, &registerMask, &frameIndex);

      bool useTemporary = ((target->type(c) == MemoryOperand
                            and src->source->type(c) == MemoryOperand)
                           or (srcSize != dstSize
                               and target->type(c) != RegisterOperand));

      if (target->match(c, typeMask, registerMask, frameIndex)
          and not useTemporary)
      {
        if (DebugMoves) {
          char srcb[256]; src->source->toString(c, srcb, 256);
          char dstb[256]; target->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p to %p\n",
                  srcb, dstb, src, dst);
        }

        apply(c, type, srcSize, src->source, dstSize, target);
      } else {
        assert(c, typeMask & (1 << RegisterOperand));

        Site* tmpTarget = freeRegisterSite(c, registerMask);

        addSite(c, stackBefore, localsBefore, dstSize, dst, tmpTarget);

        if (DebugMoves) {
          char srcb[256]; src->source->toString(c, srcb, 256);
          char dstb[256]; tmpTarget->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p to %p\n",
                  srcb, dstb, src, dst);
        }

        apply(c, type, srcSize, src->source, dstSize, tmpTarget);

        if (useTemporary or isStore) {
          if (DebugMoves) {
            char srcb[256]; tmpTarget->toString(c, srcb, 256);
            char dstb[256]; target->toString(c, dstb, 256);
            fprintf(stderr, "move %s to %s for %p to %p\n",
                    srcb, dstb, src, dst);
          }

          apply(c, Move, dstSize, tmpTarget, dstSize, target);

          if (isStore) {
            removeSite(c, dst, tmpTarget);
          }
        } else {
          removeSite(c, dst, target);          
        }
      }
    } else {
      target = src->source;

      addBuddy(src, dst);

      if (DebugMoves) {
        char dstb[256]; target->toString(c, dstb, 256);
        fprintf(stderr, "null move in %s for %p to %p\n", dstb, src, dst);
      }
    }

    if (isStore) {
      removeSite(c, dst, target);
    }

    nextRead(c, this, src);
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
  bool thunk;
  uint8_t srcTypeMask;
  uint64_t srcRegisterMask;
  uint8_t dstTypeMask;
  uint64_t dstRegisterMask;

  c->arch->plan(type, srcSize, &srcTypeMask, &srcRegisterMask,
                dstSize, &dstTypeMask, &dstRegisterMask,
                &thunk);

  assert(c, not thunk); // todo

  append(c, new (c->zone->allocate(sizeof(MoveEvent)))
         MoveEvent
         (c, type, srcSize, src, dstSize, dst,
          read(c, srcSize, srcTypeMask, srcRegisterMask, AnyFrameIndex),
          read(c, dstSize, dstTypeMask, dstRegisterMask, AnyFrameIndex)));
}

ConstantSite*
findConstantSite(Context* c, Value* v)
{
  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
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
    addRead(c, this, first, firstRead);
    addRead(c, this, second, secondRead);
  }

  virtual const char* name() {
    return "CompareEvent";
  }

  virtual void compile(Context* c) {
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

    nextRead(c, this, first);
    nextRead(c, this, second);
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

  append(c, new (c->zone->allocate(sizeof(CompareEvent)))
         CompareEvent
         (c, size, first, second,
          read(c, size, firstTypeMask, firstRegisterMask, AnyFrameIndex),
          read(c, size, secondTypeMask, secondRegisterMask, AnyFrameIndex)));
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
    addRead(c, this, first, firstRead);
    addRead(c, this, second, secondRead);
  }

  virtual const char* name() {
    return "CombineEvent";
  }

  virtual void compile(Context* c) {
    first->source->freeze(c, first, firstSize);

    Site* target;
    if (c->arch->condensedAddressing()) {
      maybePreserve(c, stackBefore, localsBefore, secondSize, second,
                    second->source);
      target = second->source;
      if (liveNext(c, second)) {
        c->forfeitedSite = target;
      }
    } else {
      target = resultRead->allocateSite(c);
      addSite(c, stackBefore, localsBefore, resultSize, result, target);
    }

    first->source->thaw(c, first, firstSize);

//     fprintf(stderr, "combine %p and %p into %p\n", first, second, result);
    apply(c, type, firstSize, first->source, secondSize, second->source,
          resultSize, target);

    nextRead(c, this, first);
    nextRead(c, this, second);

    if (c->arch->condensedAddressing()) {
      c->forfeitedSite = 0;
      removeSite(c, second, target);
      if (live(result)) {
        addSite(c, 0, 0, resultSize, result, target);
      }
    }
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

void
removeBuddy(Context* c, Value* v)
{
  if (v->buddy != v) {
//     fprintf(stderr, "remove buddy %p from", v);
//     for (Value* p = v->buddy; p != v; p = p->buddy) {
//       fprintf(stderr, " %p", p);
//     }
//     fprintf(stderr, "\n");

    Value* next = v->buddy;
    v->buddy = v;
    Value* p = next;
    while (p->buddy != v) p = p->buddy;
    p->buddy = next;

    if (not live(next)) {
      clearSites(c, next);
    }

    if (not live(v)) {
      clearSites(c, v);
    }
  }
}

Site*
copy(Context* c, Site* s)
{
  Site* start = 0;
  Site* end = 0;
  for (; s; s = s->next) {
    Site* n = s->copy(c);
    if (end) {
      end->next = n;
    } else {
      start = n;
    }
    end = n;
  }
  return start;
}

class Snapshot {
 public:
  Snapshot(Context* c, Value* value, Snapshot* next):
    value(value), buddy(value->buddy), sites(copy(c, value->sites)), next(next)
  { }

  Value* value;
  Value* buddy;
  Site* sites;
  Snapshot* next;
};

Snapshot*
snapshot(Context* c, Value* value, Snapshot* next)
{
  if (DebugControl) {
    char buffer[256]; toString(c, value->sites, buffer, 256);
    fprintf(stderr, "snapshot %p buddy %p sites %s\n",
            value, value->buddy, buffer);
  }

  return new (c->zone->allocate(sizeof(Snapshot))) Snapshot(c, value, next);
}

Snapshot*
makeSnapshots(Context* c, Value* value, Snapshot* next)
{
  next = snapshot(c, value, next);
  for (Value* p = value->buddy; p != value; p = p->buddy) {
    next = snapshot(c, p, next);
  }
  return next;
}

Stack*
stack(Context* c, Value* value, unsigned footprint, unsigned index,
      Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(index, footprint, value, next);
}

Stack*
stack(Context* c, Value* value, unsigned footprint, Stack* next)
{
  return stack
    (c, value, footprint, (next ? next->index + next->footprint : 0), next);
}

SavedValue*
savedValue(Context* c, Value* value, unsigned footprint, SavedValue* next)
{
  return new (c->zone->allocate(sizeof(SavedValue)))
    SavedValue(footprint, value, next);
}

Value*
maybeBuddy(Context* c, Value* v, unsigned sizeInBytes);

void
push(Context* c, unsigned footprint, Value* v)
{
  assert(c, footprint);

  v = maybeBuddy(c, v, footprintSizeInBytes(footprint));

  if (DebugFrame) {
    fprintf(stderr, "push %p of footprint %d\n", v, footprint);
  }

  v->local = true;
  c->stack = stack(c, v, footprint, c->stack);
}

Value*
pop(Context* c, unsigned footprint UNUSED)
{
  Stack* s = c->stack;
  assert(c, footprint == s->footprint);
  assert(c, s->value->local);

  if (DebugFrame) {
    fprintf(stderr, "pop %p of size %d\n", s->value, footprint);
  }

  c->stack = s->next;
  s->value->local = false;
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
    Stack* oldStack = c->stack;

    ::push(c, ceiling(secondSize, BytesPerWord), second);
    ::push(c, ceiling(firstSize, BytesPerWord), first);

    Stack* argumentStack = c->stack;
    c->stack = oldStack;

    appendCall
      (c, value(c, constantSite(c, c->client->getThunk(type, resultSize))),
       0, 0, result, resultSize, argumentStack, 2, 0);
  } else {
    Read* resultRead = read
      (c, resultSize, resultTypeMask, resultRegisterMask, AnyFrameIndex);
    Read* secondRead;
    if (c->arch->condensedAddressing()) {
      secondRead = resultRead;
    } else {
      secondRead = read
        (c, secondSize, secondTypeMask, secondRegisterMask, AnyFrameIndex);
    }

    append
      (c, new (c->zone->allocate(sizeof(CombineEvent)))
       CombineEvent
       (c, type,
        firstSize, first,
        secondSize, second,
        resultSize, result,
        read(c, firstSize, firstTypeMask, firstRegisterMask, AnyFrameIndex),
        secondRead,
        resultRead));
  }
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, BinaryOperation type, unsigned size, Value* value,
                 Value* result, Read* valueRead, Read* resultRead):
    Event(c), type(type), size(size), value(value), result(result),
    resultRead(resultRead)
  {
    addRead(c, this, value, valueRead);
  }

  virtual const char* name() {
    return "TranslateEvent";
  }

  virtual void compile(Context* c) {
    Site* target;
    if (c->arch->condensedAddressing()) {
      maybePreserve(c, stackBefore, localsBefore, size, value, value->source);
      target = value->source;
      if (liveNext(c, value)) {
        c->forfeitedSite = target;
      }
    } else {
      target = resultRead->allocateSite(c);
      addSite(c, stackBefore, localsBefore, size, result, target);
    }

    apply(c, type, size, value->source, size, target);
    
    nextRead(c, this, value);

    if (c->arch->condensedAddressing()) {
      c->forfeitedSite = 0;
      removeSite(c, value, target);
      if (live(result)) {
        addSite(c, 0, 0, size, result, target);
      }
    }
  }

  BinaryOperation type;
  unsigned size;
  Value* value;
  Value* result;
  Read* resultRead;
};

void
appendTranslate(Context* c, BinaryOperation type, unsigned size, Value* value,
                Value* result)
{
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t resultTypeMask;
  uint64_t resultRegisterMask;

  c->arch->plan(type, size, &firstTypeMask, &firstRegisterMask,
                size, &resultTypeMask, &resultRegisterMask,
                &thunk);

  assert(c, not thunk); // todo

  Read* resultRead = read
    (c, size, resultTypeMask, resultRegisterMask, AnyFrameIndex);
  Read* firstRead;
  if (c->arch->condensedAddressing()) {
    firstRead = resultRead;
  } else {
    firstRead = read
      (c, size, firstTypeMask, firstRegisterMask, AnyFrameIndex);
  }
  // todo: respect resultTypeMask and resultRegisterMask

  append(c, new (c->zone->allocate(sizeof(TranslateEvent)))
         TranslateEvent
         (c, type, size, value, result, firstRead, resultRead));
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, int displacement, Value* index,
              unsigned scale, Value* result):
    Event(c), base(base), displacement(displacement), index(index),
    scale(scale), result(result)
  {
    addRead(c, this, base, anyRegisterRead(c, BytesPerWord));
    if (index) addRead(c, this, index, registerOrConstantRead(c, BytesPerWord));
  }

  virtual const char* name() {
    return "MemoryEvent";
  }

  virtual void compile(Context* c) {
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

    nextRead(c, this, base);
    if (index) {
      if (BytesPerWord == 8 and indexRegister != NoRegister) {
        apply(c, Move, 4, index->source, 8, index->source);
      }

      nextRead(c, this, index);
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
  append(c, new (c->zone->allocate(sizeof(MemoryEvent)))
         MemoryEvent(c, base, displacement, index, scale, result));
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, UnaryOperation type, Value* address):
    Event(c), type(type), address(address)
  {
    address->addPredecessor(c, this);

    addRead(c, this, address, read
            (c, BytesPerWord, ~0, ~static_cast<uint64_t>(0), AnyFrameIndex));
  }

  virtual const char* name() {
    return "BranchEvent";
  }

  virtual void compile(Context* c) {
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

    nextRead(c, this, address);
  }

  virtual bool isBranch() { return true; }

  UnaryOperation type;
  Value* address;
};

void
appendBranch(Context* c, UnaryOperation type, Value* address)
{
  append(c, new (c->zone->allocate(sizeof(BranchEvent)))
         BranchEvent(c, type, address));
}

class BoundsCheckEvent: public Event {
 public:
  BoundsCheckEvent(Context* c, Value* object, unsigned lengthOffset,
                   Value* index, intptr_t handler):
    Event(c), object(object), lengthOffset(lengthOffset), index(index),
    handler(handler)
  {
    addRead(c, this, object, anyRegisterRead(c, BytesPerWord));
    addRead(c, this, index, registerOrConstantRead(c, BytesPerWord));
  }

  virtual const char* name() {
    return "BoundsCheckEvent";
  }

  virtual void compile(Context* c) {
    Assembler* a = c->assembler;

    ConstantSite* constant = findConstantSite(c, index);
    CodePromise* nextPromise = codePromise
      (c, static_cast<Promise*>(0));
    CodePromise* outOfBoundsPromise = 0;

    if (constant) {
      expect(c, constant->value.value->value() >= 0);      
    } else {
      outOfBoundsPromise = codePromise(c, static_cast<Promise*>(0));

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

    nextRead(c, this, object);
    nextRead(c, this, index);
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
  append(c, new (c->zone->allocate(sizeof(BoundsCheckEvent)))
         BoundsCheckEvent(c, object, lengthOffset, index, handler));
}

class FrameSiteEvent: public Event {
 public:
  FrameSiteEvent(Context* c, Value* value, unsigned size, int index):
    Event(c), value(value), size(size), index(index)
  { }

  virtual const char* name() {
    return "FrameSiteEvent";
  }

  virtual void compile(Context* c) {
    if (live(value)) {
      addSite(c, stackBefore, localsBefore, size, value, frameSite(c, index));
    }
  }

  Value* value;
  unsigned size;
  int index;
};

void
appendFrameSite(Context* c, Value* value, unsigned size, int index)
{
  append(c, new (c->zone->allocate(sizeof(FrameSiteEvent)))
         FrameSiteEvent(c, value, size, index));
}

unsigned
frameFootprint(Context* c, Stack* s)
{
  return c->localFootprint + (s ? (s->index + s->footprint) : 0);
}

void
visit(Context* c, Link* link)
{
//   fprintf(stderr, "visit link from %d to %d fork %p junction %p\n",
//           link->predecessor->logicalInstruction->index,
//           link->successor->logicalInstruction->index,
//           link->forkState,
//           link->junctionState);

  ForkState* forkState = link->forkState;
  if (forkState) {
    for (unsigned i = 0; i < forkState->readCount; ++i) {
      ForkElement* p = forkState->elements + i;
      Value* v = p->value;
      v->reads = p->read->nextTarget();
//       fprintf(stderr, "next read %p for %p\n", v->reads, v);
      if (not live(v)) {
        clearSites(c, v);
      }
    }
  }

  JunctionState* junctionState = link->junctionState;
  if (junctionState) {
    for (unsigned i = 0; i < junctionState->frameFootprint; ++i) {
      StubReadPair* p = junctionState->reads + i;
      
      if (p->value) {
        assert(c, p->value->reads == p->read);
        nextRead(c, 0, p->value);
      }
    }
  }
}

class BuddyEvent: public Event {
 public:
  BuddyEvent(Context* c, Value* original, Value* buddy, unsigned size):
    Event(c), original(original), buddy(buddy)
  {
    addRead(c, this, original,
            read(c, size, ~0, ~static_cast<uint64_t>(0), AnyFrameIndex));
  }

  virtual const char* name() {
    return "BuddyEvent";
  }

  virtual void compile(Context* c) {
//     fprintf(stderr, "original %p buddy %p\n", original, buddy);
    assert(c, hasSite(original));

    addBuddy(original, buddy);

    nextRead(c, this, original);
  }

  Value* original;
  Value* buddy;
};

void
appendBuddy(Context* c, Value* original, Value* buddy, unsigned size)
{
  append(c, new (c->zone->allocate(sizeof(BuddyEvent)))
         BuddyEvent(c, original, buddy, size));
}

class SaveLocalsEvent: public Event {
 public:
  SaveLocalsEvent(Context* c):
    Event(c)
  {
    saveLocals(c, this);
  }

  virtual const char* name() {
    return "SaveLocalsEvent";
  }

  virtual void compile(Context* c) {
    for (Read* r = reads; r; r = r->eventNext) {
      nextRead(c, this, r->value);
    }
  }
};

void
appendSaveLocals(Context* c)
{
  append(c, new (c->zone->allocate(sizeof(SaveLocalsEvent)))
         SaveLocalsEvent(c));
}

class DummyEvent: public Event {
 public:
  DummyEvent(Context* c):
    Event(c)
  { }

  virtual const char* name() {
    return "DummyEvent";
  }

  virtual void compile(Context*) { }
};

void
appendDummy(Context* c)
{
  Stack* stack = c->stack;
  Local* locals = c->locals;
  LogicalInstruction* i = c->logicalCode[c->logicalIp];

  c->stack = i->stack;
  c->locals = i->locals;

  append(c, new (c->zone->allocate(sizeof(DummyEvent))) DummyEvent(c));

  c->stack = stack;
  c->locals = locals;  
}

void
append(Context* c, Event* e)
{
  LogicalInstruction* i = c->logicalCode[c->logicalIp];
  if (c->stack != i->stack or c->locals != i->locals) {
    appendDummy(c);
  }

  if (DebugAppend) {
    fprintf(stderr, " -- append %s at %d with %d stack before\n",
            e->name(), e->logicalInstruction->index, c->stack ?
            c->stack->index + c->stack->footprint : 0);
  }

  if (c->lastEvent) {
    c->lastEvent->next = e;
  } else {
    c->firstEvent = e;
  }
  c->lastEvent = e;

  Event* p = c->predecessor;
  if (p) {
    Link* link = ::link(c, p, e->predecessors, e, p->successors, c->forkState);
    e->predecessors = link;
    p->successors = link;
  }
  c->forkState = 0;

  c->predecessor = e;

  if (e->logicalInstruction->firstEvent == 0) {
    e->logicalInstruction->firstEvent = e;
  }
  e->logicalInstruction->lastEvent = e;
}

Site*
readSource(Context* c, Stack* stack, Local* locals, Read* r)
{
  if (not hasSite(r->value)) return 0;

  if (DebugReads) {
    char buffer[256]; toString(c, r->value->sites, buffer, 256);
    fprintf(stderr, "read source for %p from %s\n", r->value, buffer);
  }

  Site* site = r->pickSite(c, r->value, true);

  if (site) {
    return site;
  } else {
    Site* target = r->allocateSite(c);
    unsigned copyCost;
    site = pick(c, r->value, target, &copyCost, true);
    assert(c, copyCost);
    move(c, stack, locals, r->size, r->value, site, target);
    return target;    
  }
}

bool
acceptJunctionSite(Context* c, Site* s)
{
  return s
    and (c->availableRegisterCount > 1 or s->type(c) != RegisterOperand);
}

Site*
pickJunctionSite(Context* c, Value* v, Read* r, unsigned frameIndex)
{
  uint8_t typeMask = ~static_cast<uint8_t>(0);
  uint64_t registerMask = ~static_cast<uint64_t>(0);
  int frameIndexDesired = AnyFrameIndex;
  r->intersect(&typeMask, &registerMask, &frameIndexDesired);

  Site* s = pickSite(c, v, typeMask, registerMask, frameIndexDesired, false);

  if (not acceptJunctionSite(c, s)) {
    s = pick(c, v, 0, 0, false);
  }

  if (acceptJunctionSite(c, s)
      and s->match(c, (1 << RegisterOperand)
                   | (1 << MemoryOperand), ~0, AnyFrameIndex))
  {
    if (DebugControl) {
      fprintf(stderr, "use picked\n");
    }
    return s;
  } else if (unused(c, r->size, typeMask, registerMask, frameIndexDesired)) {
    s = allocateSite(c, typeMask, registerMask, frameIndexDesired);
    if (acceptJunctionSite(c, s)) {
      if (DebugControl) {
        fprintf(stderr, "use allocated\n");
      }
      return s;
    }
  }

  if (c->availableRegisterCount > 1) {
    if (DebugControl) {
      fprintf(stderr, "use register\n");
    }
    return freeRegisterSite(c);
  } else if (frameIndexAvailable(c, frameIndex)) {
    if (DebugControl) {
      fprintf(stderr, "use frame\n");
    }
    return frameSite(c, frameIndex);
  }
}

class SiteRecord {
 public:
  SiteRecord(Site* site, Value* value, unsigned size):
    site(site), value(value), size(size)
  { }

  SiteRecord() { }

  Site* site;
  Value* value;
  unsigned size;
};

unsigned
resolveJunctionSite(Context* c, Event* e, Value* v,
                    unsigned siteIndex, unsigned frameIndex,
                    SiteRecord* frozenSites, unsigned frozenSiteIndex)
{
  assert(c, siteIndex < frameFootprint(c, e->stackAfter));

  if (live(v)) {
    assert(c, hasSite(v));
    
    Read* r = live(v);
    Site* original = e->junctionSites[siteIndex];
    Site* target;

    if (original) {
      target = original;
    } else {
      target = pickJunctionSite(c, v, r, frameIndex);
    }

    unsigned copyCost;
    Site* site = pick(c, v, target, &copyCost, true);

    if (copyCost or not (original or findSite(c, v, site))) {
      if (original) {
        target = target->copy(c);
      }
      move(c, e->stackAfter, e->localsAfter, r->size, v, site, target);
    } else {
      target = site;
    }

    if (original == 0) {
      new (frozenSites + (frozenSiteIndex++)) SiteRecord(target, v, r->size);
      target->freeze(c, v, r->size);
      e->junctionSites[siteIndex] = target->copy(c);
    }

    if (DebugControl) {
      char buffer[256]; target->toString(c, buffer, 256);
      fprintf(stderr, "resolved junction site local %d frame %d %s %p\n",
              siteIndex, frameIndex, buffer, v);
    }
  } else if (DebugControl) {
    fprintf(stderr, "skip dead junction site local %d frame %d %p\n",
            siteIndex, frameIndex, v);
  }

  return frozenSiteIndex;
}

void
propagateJunctionSites(Context* c, Event* e, Site** sites)
{
  for (Link* pl = e->predecessors; pl; pl = pl->nextPredecessor) {
    Event* p = pl->predecessor;
    if (p->junctionSites == 0) {
      p->junctionSites = sites;
      for (Link* sl = p->successors; sl; sl = sl->nextSuccessor) {
        Event* s = sl->successor;
        propagateJunctionSites(c, s, sites);
      }
    }
  }
}

void
resolveJunctionSites(Context* c, Event* e)
{
  unsigned frameFootprint = ::frameFootprint(c, e->stackAfter);
  SiteRecord frozenSites[frameFootprint];
  unsigned frozenSiteIndex = 0;

  if (e->junctionSites) {
    for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
      FrameIterator::Element el = it.next(c);
      if (e->junctionSites[el.localIndex]) {
        frozenSiteIndex = resolveJunctionSite
          (c, e, el.value, el.localIndex, frameIndex(c, &el), frozenSites,
           frozenSiteIndex);
      }
    }
  } else {
    for (Link* sl = e->successors; sl; sl = sl->nextSuccessor) {
      Event* s = sl->successor;
      if (s->predecessors->nextPredecessor) {
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
    for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
      FrameIterator::Element el = it.next(c);
      if (e->junctionSites[el.localIndex] == 0) {
        frozenSiteIndex = resolveJunctionSite
          (c, e, el.value, el.localIndex, frameIndex(c, &el), frozenSites,
           frozenSiteIndex);
      }
    }

    if (DebugControl) {
      fprintf(stderr, "resolved junction sites %p at %d\n",
              e->junctionSites, e->logicalInstruction->index);
    }

    while (frozenSiteIndex) {
      SiteRecord* sr = frozenSites + (--frozenSiteIndex);
      sr->site->thaw(c, sr->value, sr->size);
    }
  }
}

void
captureBranchSnapshots(Context* c, Event* e)
{
  if (e->successors->nextSuccessor) {
    for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
      FrameIterator::Element el = it.next(c);
      e->snapshots = makeSnapshots(c, el.value, e->snapshots);
    }

    for (SavedValue* sv = e->successors->forkState->saved; sv; sv = sv->next) {
      e->snapshots = makeSnapshots(c, sv->value, e->snapshots);
    }

    if (DebugControl) {
      fprintf(stderr, "captured snapshots %p at %d\n",
              e->snapshots, e->logicalInstruction->index);
    }
  }
}

void
populateSiteTables(Context* c, Event* e)
{
  resolveJunctionSites(c, e);

  captureBranchSnapshots(c, e);
}

void
setSites(Context* c, Event* e, Value* v, Site* s, unsigned size)
{
  assert(c, live(v));

  for (; s; s = s->next) {
    addSite(c, e->stackBefore, e->localsBefore, size, v, s->copy(c));
  }

  if (DebugControl) {
    char buffer[256]; toString(c, v->sites, buffer, 256);
    fprintf(stderr, "set sites %s for %p\n", buffer, v);
  }
}

void
resetFrame(Context* c, Event* e)
{
  for (FrameIterator it(c, e->stackBefore, e->localsBefore); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    clearSites(c, el.value);
  }
}

void
setSites(Context* c, Event* e, Site** sites)
{
  resetFrame(c, e);

  for (FrameIterator it(c, e->stackBefore, e->localsBefore); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    if (sites[el.localIndex]) {
      Read* r = live(el.value);
      if (r) {
        setSites(c, e, el.value, sites[el.localIndex], r->size);
      }
    }
  }
}

void
removeBuddies(Context* c)
{
  for (FrameIterator it(c, c->stack, c->locals); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    removeBuddy(c, el.value);
  }
}

void
restore(Context* c, Event* e, Snapshot* snapshots)
{
  for (Snapshot* s = snapshots; s; s = s->next) {
//     char buffer[256]; toString(c, s->sites, buffer, 256);
//     fprintf(stderr, "restore %p buddy %p sites %s\n",
//             s->value, s->value->buddy, buffer);

    s->value->buddy = s->buddy;
  }

  resetFrame(c, e);

  for (Snapshot* s = snapshots; s; s = s->next) {
    if (live(s->value)) {
      Read* r = live(s->value);
      if (r and s->sites and s->value->sites == 0) {
        setSites(c, e, s->value, s->sites, r->size);
      }
    }
  }
}

void
populateSources(Context* c, Event* e)
{
  SiteRecord frozenSites[e->readCount];
  unsigned frozenSiteIndex = 0;
  for (Read* r = e->reads; r; r = r->eventNext) {
    r->value->source = readSource(c, e->stackBefore, e->localsBefore, r);

    if (r->value->source) {
      if (DebugReads) {
        char buffer[256]; r->value->source->toString(c, buffer, 256);
        fprintf(stderr, "freeze source %s for %p\n",
                buffer, r->value);
      }

      assert(c, frozenSiteIndex < e->readCount);

      new (frozenSites + (frozenSiteIndex++))
        SiteRecord(r->value->source, r->value, r->size);

      r->value->source->freeze(c, r->value, r->size);
    }
  }

  while (frozenSiteIndex) {
    SiteRecord* sr = frozenSites + (--frozenSiteIndex);
    sr->site->thaw(c, sr->value, sr->size);
  }
}

void
setStubRead(Context* c, StubReadPair* p, Value* v, unsigned size)
{
  if (v) {
    StubRead* r = stubRead(c, size);
    if (DebugReads) {
      fprintf(stderr, "add stub read %p to %p\n", r, v);
    }
    addRead(c, 0, v, r);

    p->value = v;
    p->read = r;
  }
}

void
populateJunctionReads(Context* c, Link* link)
{
  JunctionState* state = new
    (c->zone->allocate
     (sizeof(JunctionState)
      + (sizeof(StubReadPair) * frameFootprint(c, c->stack))))
    JunctionState(frameFootprint(c, c->stack));

  memset(state->reads, 0, sizeof(StubReadPair) * frameFootprint(c, c->stack));

  link->junctionState = state;

  for (FrameIterator it(c, c->stack, c->locals); it.hasMore();) {
    FrameIterator::Element e = it.next(c);
    setStubRead(c, state->reads + e.localIndex, e.value,
                footprintSizeInBytes(e.footprint));
  }
}

void
updateJunctionReads(Context* c, JunctionState* state)
{
  for (FrameIterator it(c, c->stack, c->locals); it.hasMore();) {
    FrameIterator::Element e = it.next(c);
    StubReadPair* p = state->reads + e.localIndex;
    if (p->value and p->read->read == 0) {
      Read* r = live(e.value);
      if (r) {
        if (DebugReads) {
          fprintf(stderr, "stub read %p for %p valid: %p\n",
                  p->read, p->value, r);
        }
        p->read->read = r;
      }
    }
  }

  for (unsigned i = 0; i < frameFootprint(c, c->stack); ++i) {
    StubReadPair* p = state->reads + i;
    if (p->value and p->read->read == 0) {
      if (DebugReads) {
        fprintf(stderr, "stub read %p for %p invalid\n", p->read, p->value);
      }
      p->read->valid_ = false;
    }
  }
}

LogicalInstruction*
next(Context* c, LogicalInstruction* i)
{
  for (unsigned n = i->index + 1; n < c->logicalCodeLength; ++n) {
    i = c->logicalCode[n];
    if (i) return i;
  }
  return 0;
}

class Block {
 public:
  Block(Event* head):
    head(head), nextBlock(0), nextInstruction(0), assemblerBlock(0), start(0)
  { }

  Event* head;
  Block* nextBlock;
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
  if (c->logicalCode[c->logicalIp]->lastEvent == 0) {
    appendDummy(c);
  }

  Assembler* a = c->assembler;

  c->pass = CompilePass;

  Block* firstBlock = block(c, c->firstEvent);
  Block* block = firstBlock;

  a->allocateFrame(c->alignedFrameSize);

  for (Event* e = c->firstEvent; e; e = e->next) {
    if (DebugCompile) {
      fprintf(stderr,
              " -- compile %s at %d with %d preds %d succs %d stack\n",
              e->name(), e->logicalInstruction->index,
              countPredecessors(e->predecessors),
              countSuccessors(e->successors),
              e->stackBefore ?
              e->stackBefore->index + e->stackBefore->footprint : 0);
    }

    e->block = block;

    c->stack = e->stackBefore;
    c->locals = e->localsBefore;

    if (e->logicalInstruction->machineOffset == 0) {
      e->logicalInstruction->machineOffset = a->offset();
    }

    if (e->predecessors) {
      visit(c, lastPredecessor(e->predecessors));

      Event* first = e->predecessors->predecessor;
      if (e->predecessors->nextPredecessor) {
        for (Link* pl = e->predecessors;
             pl->nextPredecessor;
             pl = pl->nextPredecessor)
        {
          updateJunctionReads(c, pl->junctionState);
        }

        if (DebugControl) {
          fprintf(stderr, "set sites to junction sites %p at %d\n",
                  first->junctionSites, first->logicalInstruction->index);
        }

        setSites(c, e, first->junctionSites);
        removeBuddies(c);
      } else if (first->successors->nextSuccessor) {
        if (DebugControl) {
          fprintf(stderr, "restore snapshots %p at %d\n",
                  first->snapshots, first->logicalInstruction->index);
        }

        restore(c, e, first->snapshots);
      }
    }

    populateSources(c, e);

    bool branch = e->isBranch();
    if (branch and e->successors) {
      populateSiteTables(c, e);
    }

    e->compile(c);

    if ((not branch) and e->successors) {
      populateSiteTables(c, e);
    }

    if (e->visitLinks) {
      for (Cell* cell = reverseDestroy(e->visitLinks); cell; cell = cell->next)
      {
        visit(c, static_cast<Link*>(cell->value));
      }
      e->visitLinks = 0;
    }

    for (CodePromise* p = e->promises; p; p = p->next) {
      p->offset = a->offset();
    }
    
    LogicalInstruction* nextInstruction = next(c, e->logicalInstruction);
    if (e->next == 0
        or (e->next->logicalInstruction != e->logicalInstruction
            and (e->logicalInstruction->lastEvent == e
                 or e->next->logicalInstruction != nextInstruction)))
    {
      Block* b = e->logicalInstruction->firstEvent->block;
      if (b != block) {
        while (b->nextBlock) b = b->nextBlock;
        b->nextBlock = block;
      }
      block->nextInstruction = nextInstruction;
      block->assemblerBlock = a->endBlock(e->next != 0);
//       fprintf(stderr, "end block %p at %d\n", block->assemblerBlock, e->logicalInstruction->index);

      if (e->next) {
        block = ::block(c, e->next);
      }
    }
  }

  block = firstBlock;
  while (block->nextBlock or block->nextInstruction) {
    Block* next = block->nextBlock
      ? block->nextBlock
      : block->nextInstruction->firstEvent->block;
    next->start = block->assemblerBlock->resolve
      (block->start, next->assemblerBlock);
//     fprintf(stderr, "resolve block %p\n", block->assemblerBlock);
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
restore(Context* c, ForkState* state)
{
  for (unsigned i = 0; i < state->readCount; ++i) {
    ForkElement* p = state->elements + i;
    p->value->lastRead = p->read;
    p->read->allocateTarget(c);
    p->value->local = p->local;
  }
}

void
addForkElement(Context* c, Value* v, unsigned size, ForkState* state,
               unsigned index)
{
  MultiRead* r = multiRead(c, size);
  if (DebugReads) {
    fprintf(stderr, "add multi read %p to %p\n", r, v);
  }
  addRead(c, 0, v, r);

  ForkElement* p = state->elements + index;
  p->value = v;
  p->local = v->local;
  p->read = r;
}

unsigned
count(SavedValue* sv)
{
  unsigned count = 0;
  while (sv) {
    ++ count;
    sv = sv->next;
  }
  return count;
}

ForkState*
saveState(Context* c)
{
  unsigned elementCount = frameFootprint(c, c->stack) + count(c->saved);

  ForkState* state = new
    (c->zone->allocate
     (sizeof(ForkState) + (sizeof(ForkElement) * elementCount)))
    ForkState(c->stack, c->locals, c->saved, c->predecessor, c->logicalIp);

  if (c->predecessor) {
    c->forkState = state;

    unsigned count = 0;

    for (FrameIterator it(c, c->stack, c->locals); it.hasMore();) {
      FrameIterator::Element e = it.next(c);
      addForkElement
        (c, e.value, footprintSizeInBytes(e.footprint), state, count++);
    }

    for (SavedValue* sv = c->saved; sv; sv = sv->next) {
      addForkElement
        (c, sv->value, footprintSizeInBytes(sv->footprint), state, count++);
    }

    state->readCount = count;

    restore(c, state);
  }

  c->saved = 0;

  return state;
}

void
restoreState(Context* c, ForkState* s)
{
  if (c->logicalCode[c->logicalIp]->lastEvent == 0) {
    appendDummy(c);
  }

  c->stack = s->stack;
  c->locals = s->locals;
  c->predecessor = s->predecessor;
  c->logicalIp = s->logicalIp;

  if (c->predecessor) {
    c->forkState = s;
    restore(c, s);
  }
}

Value*
maybeBuddy(Context* c, Value* v, unsigned sizeInBytes)
{
  if (v->local) {
    Value* n = value(c);
    appendBuddy(c, v, n, sizeInBytes);
    return n;
  } else {
    return v;
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
    Register* reg = c->registers[r];
    if (reg->refCount or reg->value) {
      releaseRegister(c, r);
    }
    assert(c, reg->refCount == 0);
    assert(c, reg->value == 0);
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

  virtual State* saveState() {
    return ::saveState(&c);
  }

  virtual void restoreState(State* state) {
    ::restoreState(&c, static_cast<ForkState*>(state));
  }

  virtual Subroutine* startSubroutine() {
    return c.subroutine = new (c.zone->allocate(sizeof(MySubroutine)))
      MySubroutine;
  }

  virtual void endSubroutine(Subroutine* subroutine) {
    MySubroutine* sr = static_cast<MySubroutine*>(subroutine);
    if (sr->forkState) {
      ::restoreState(&c, sr->forkState);
    } else {
      sr->forkState = ::saveState(&c);
    }
  }

  virtual void init(unsigned logicalCodeLength, unsigned parameterFootprint,
                    unsigned localFootprint, unsigned alignedFrameSize)
  {
    c.logicalCodeLength = logicalCodeLength;
    c.parameterFootprint = parameterFootprint;
    c.localFootprint = localFootprint;
    c.alignedFrameSize = alignedFrameSize;

    unsigned frameResourceSize = sizeof(FrameResource)
      * (alignedFrameSize + parameterFootprint);

    c.frameResources = static_cast<FrameResource*>
      (c.zone->allocate(frameResourceSize));

    memset(c.frameResources, 0, frameResourceSize);

    // leave room for logical instruction -1
    unsigned codeSize = sizeof(LogicalInstruction*)
      * (logicalCodeLength + 1);
    c.logicalCode = static_cast<LogicalInstruction**>
      (c.zone->allocate(codeSize));
    memset(c.logicalCode, 0, codeSize);
    c.logicalCode++;

    c.locals = static_cast<Local*>
      (c.zone->allocate(sizeof(Local) * localFootprint));

    memset(c.locals, 0, sizeof(Local) * localFootprint);

    c.logicalCode[-1] = new 
      (c.zone->allocate(sizeof(LogicalInstruction)))
      LogicalInstruction(-1, c.stack, c.locals);
  }

  virtual void visitLogicalIp(unsigned logicalIp) {
    assert(&c, logicalIp < c.logicalCodeLength);

    if (c.logicalCode[c.logicalIp]->lastEvent == 0) {
      appendDummy(&c);
    }

    Event* e = c.logicalCode[logicalIp]->firstEvent;

    Event* p = c.predecessor;
    if (p) {
//       fprintf(stderr, "visit %d pred %d\n", logicalIp,
//               p->logicalInstruction->index);

      p->stackAfter = c.stack;
      p->localsAfter = c.locals;

      Link* link = ::link
        (&c, p, e->predecessors, e, p->successors, c.forkState);
      e->predecessors = link;
      p->successors = link;
      c.lastEvent->visitLinks = cons(&c, link, c.lastEvent->visitLinks);

//       fprintf(stderr, "populate junction reads for %d to %d\n",
//               p->logicalInstruction->index, logicalIp);
      populateJunctionReads(&c, link);
    }

    if (c.subroutine) {
      c.subroutine->forkState
        = c.logicalCode[logicalIp]->subroutine->forkState;
      c.subroutine = 0;
    }

    c.forkState = 0;
  }

  virtual void startLogicalIp(unsigned logicalIp) {
    assert(&c, logicalIp < c.logicalCodeLength);
    assert(&c, c.logicalCode[logicalIp] == 0);

    if (c.logicalCode[c.logicalIp]->lastEvent == 0) {
      appendDummy(&c);
    }

    Event* p = c.predecessor;
    if (p) {
      p->stackAfter = c.stack;
      p->localsAfter = c.locals;
    }

    c.logicalCode[logicalIp] = new 
      (c.zone->allocate(sizeof(LogicalInstruction)))
      LogicalInstruction(logicalIp, c.stack, c.locals);

    if (c.subroutine) {
      c.logicalCode[logicalIp]->subroutine = c.subroutine;
      c.subroutine = 0;
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
    Site* s = registerSite(&c, c.arch->stack());
    return value(&c, s, s);
  }

  virtual Operand* thread() {
    Site* s = registerSite(&c, c.arch->thread());
    return value(&c, s, s);
  }

  Promise* machineIp() {
    return codePromise(&c, c.logicalCode[c.logicalIp]->lastEvent);
  }

  virtual void push(unsigned footprint) {
    assert(&c, footprint);

    Value* v = value(&c);
    v->local = true;
    c.stack = ::stack(&c, v, footprint, c.stack);
  }

  virtual void push(unsigned footprint, Operand* value) {
    ::push(&c, footprint, static_cast<Value*>(value));
  }

  virtual void save(unsigned footprint, Operand* value) {
    c.saved = savedValue(&c, static_cast<Value*>(value), footprint, c.saved);
  }

  virtual Operand* pop(unsigned footprint) {
    return ::pop(&c, footprint);
  }

  virtual void pushed() {
    Value* v = value(&c);
    v->local = true;
    appendFrameSite
      (&c, v, BytesPerWord,
       frameIndex(&c, (c.stack ? c.stack->index : 0) + c.localFootprint, 1));

    c.stack = ::stack(&c, v, 1, c.stack);
  }

  virtual void popped() {
    assert(&c, c.stack->value->local);
    c.stack = c.stack->next;
  }

  virtual StackElement* top() {
    return c.stack;
  }

  virtual unsigned footprint(StackElement* e) {
    return static_cast<Stack*>(e)->footprint;
  }

  virtual unsigned index(StackElement* e) {
    return static_cast<Stack*>(e)->index;
  }

  virtual Operand* peek(unsigned footprint UNUSED, unsigned index) {
    Stack* s = c.stack;
    for (unsigned i = index; i > 0;) {
      i -= s->footprint;
      s = s->next;
    }
    assert(&c, s->footprint == footprint);
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

    Stack* oldStack = c.stack;
    Stack* bottomArgument = 0;

    for (int i = index - 1; i >= 0; --i) {
      ::push(&c, ceiling(argumentSizes[i], BytesPerWord), arguments[i]);
      if (i == index - 1) {
        bottomArgument = c.stack;
      }
    }
    Stack* argumentStack = c.stack;
    c.stack = oldStack;

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
               resultSize, c.stack, 0, argumentFootprint);
    return result;
  }

  virtual void return_(unsigned size, Operand* value) {
    appendReturn(&c, size, static_cast<Value*>(value));
  }

  virtual void initLocal(unsigned footprint, unsigned index) {
    assert(&c, index < c.localFootprint);

    Value* v = value(&c);

    if (DebugFrame) {
      fprintf(stderr, "init local %p of footprint %d at %d (%d)\n",
              v, footprint, index, frameIndex(&c, index, footprint));
    }

    appendFrameSite
      (&c, v, footprintSizeInBytes(footprint),
       frameIndex(&c, index, footprint));

    Local* local = c.locals + index;
    local->value = v;
    v->local = true;
    local->footprint = footprint;
  }

  virtual void initLocalsFromLogicalIp(unsigned logicalIp) {
    assert(&c, logicalIp < c.logicalCodeLength);

    unsigned footprint = sizeof(Local) * c.localFootprint;
    Local* newLocals = static_cast<Local*>(c.zone->allocate(footprint));
    memset(newLocals, 0, footprint);
    c.locals = newLocals;

    Event* e = c.logicalCode[logicalIp]->firstEvent;
    for (unsigned i = 0; i < c.localFootprint; ++i) {
      Local* local = e->localsBefore + i;
      if (local->value) {
        initLocal(local->footprint, i);
      }
    }
  }

  virtual void storeLocal(unsigned footprint, Operand* src, unsigned index) {
    assert(&c, index < c.localFootprint);

    Local* local = c.locals + index;
    if (local->value) local->value->local = false;

    unsigned sizeInBytes = sizeof(Local) * c.localFootprint;
    Local* newLocals = static_cast<Local*>(c.zone->allocate(sizeInBytes));
    memcpy(newLocals, c.locals, sizeInBytes);
    c.locals = newLocals;

    local = c.locals + index;
    local->value = maybeBuddy
      (&c, static_cast<Value*>(src), footprintSizeInBytes(footprint));

    if (footprint == 2) {
      Local* clobber = local + 1;
      clobber->value = 0;
      clobber->footprint = 0;
    }

    if (DebugFrame) {
      fprintf(stderr, "store local %p of footprint %d at %d\n",
              local->value, footprint, index);
    }

    local->value->local = true;
    local->footprint = footprint;
  }

  virtual Operand* loadLocal(unsigned footprint UNUSED, unsigned index) {
    assert(&c, index < c.localFootprint);
    assert(&c, c.locals[index].value);
    assert(&c, c.locals[index].value->local);
    assert(&c, c.locals[index].footprint == footprint);

    if (DebugFrame) {
      fprintf(stderr, "load local %p of size %d at %d\n",
              c.locals[index].value, footprint, index);
    }

    return c.locals[index].value;
  }

  virtual void saveLocals() {
    appendSaveLocals(&c);
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

  virtual Operand* load(unsigned srcSize, unsigned dstSize, Operand* src) {
    assert(&c, dstSize >= BytesPerWord);

    Value* dst = value(&c);
    appendMove(&c, Move, srcSize, static_cast<Value*>(src), dstSize, dst);
    return dst;
  }

  virtual Operand* loadz(unsigned srcSize, unsigned dstSize, Operand* src) {
    assert(&c, dstSize >= BytesPerWord);

    Value* dst = value(&c);
    appendMove(&c, MoveZ, srcSize, static_cast<Value*>(src), dstSize, dst);
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
    return c.machineCodeSize = ::compile(&c);
  }

  virtual unsigned poolSize() {
    return c.constantCount * BytesPerWord;
  }

  virtual void writeTo(uint8_t* dst) {
    c.machineCode = dst;
    c.assembler->writeTo(dst);

    int i = 0;
    for (ConstantPoolNode* n = c.firstConstant; n; n = n->next) {
      *reinterpret_cast<intptr_t*>(dst + pad(c.machineCodeSize) + i)
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
