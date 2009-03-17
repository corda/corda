/* Copyright (c) 2008-2009, Avian Contributors

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
const bool DebugResources = false;
const bool DebugFrame = false;
const bool DebugControl = false;
const bool DebugReads = false;
const bool DebugSites = false;
const bool DebugMoves = false;
const bool DebugBuddies = false;

const int AnyFrameIndex = -2;
const int NoFrameIndex = -1;

const unsigned StealRegisterReserveCount = 2;
const unsigned ResolveRegisterReserveCount = 2;

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
      unsigned s1Size, Site* s1Low, Site* s1High);

void
apply(Context* c, BinaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High);

void
apply(Context* c, TernaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High,
      unsigned s3Size, Site* s3Low, Site* s3High);

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
};

class SiteMask {
 public:
  SiteMask(): typeMask(~0), registerMask(~0), frameIndex(AnyFrameIndex) { }

  SiteMask(uint8_t typeMask, uint32_t registerMask, int frameIndex):
    typeMask(typeMask), registerMask(registerMask), frameIndex(frameIndex)
  { }

  uint8_t typeMask;
  uint32_t registerMask;
  int frameIndex;
};

class Site {
 public:
  Site(): next(0) { }
  
  virtual Site* readTarget(Context*, Read*) { return this; }

  virtual unsigned toString(Context*, char*, unsigned) = 0;

  virtual unsigned copyCost(Context*, Site*) = 0;

  virtual bool match(Context*, const SiteMask&) = 0;
  
  virtual void acquire(Context*, Value*) { }

  virtual void release(Context*, Value*) { }

  virtual void freeze(Context*, Value*) { }

  virtual void thaw(Context*, Value*) { }

  virtual bool frozen(Context*) { return false; }

  virtual OperandType type(Context*) = 0;

  virtual void asAssemblerOperand(Context*, Site*, Assembler::Operand*) = 0;

  virtual Site* copy(Context*) = 0;

  virtual Site* copyLow(Context*) = 0;

  virtual Site* copyHigh(Context*) = 0;

  Site* next;
};

class SitePair {
 public:
  Site* low;
  Site* high;
};

class Stack: public Compiler::StackElement {
 public:
  Stack(unsigned index, Value* value, Stack* next):
    index(index), value(value), next(next)
  { }

  unsigned index;
  Value* value;
  Stack* next;
};

class ForkElement {
 public:
  Value* value;
  MultiRead* read;
  bool local;
};

class ForkState: public Compiler::State {
 public:
  ForkState(Stack* stack, Local* locals, Cell* saved, Event* predecessor,
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
  Cell* saved;
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

class Resource {
 public:
  Resource(bool reserved = false):
    value(0), site(0), freezeCount(0), referenceCount(0), reserved(reserved)
  { }

  virtual void freeze(Context*, Value*) = 0;

  virtual void thaw(Context*, Value*) = 0;

  virtual unsigned toString(Context*, char*, unsigned) = 0;

  Value* value;
  Site* site;
  uint8_t freezeCount;
  uint8_t referenceCount;
  bool reserved;
};

class RegisterResource: public Resource {
 public:
  RegisterResource(bool reserved):
    Resource(reserved)
  { }

  virtual void freeze(Context*, Value*);

  virtual void thaw(Context*, Value*);

  virtual unsigned toString(Context*, char*, unsigned);
};

class FrameResource: public Resource {
 public:
  virtual void freeze(Context*, Value*);

  virtual void thaw(Context*, Value*);

  virtual unsigned toString(Context*, char*, unsigned);
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
    value(0), event(0), eventNext(0)
  { }

  virtual bool intersect(SiteMask* mask) = 0;
  
  virtual bool valid() = 0;

  virtual void append(Context* c, Read* r) = 0;

  virtual Read* next(Context* c) = 0;

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

SiteMask
intersect(const SiteMask& a, const SiteMask& b)
{
  return SiteMask(a.typeMask & b.typeMask, a.registerMask & b.registerMask,
                  intersectFrameIndexes(a.frameIndex, b.frameIndex));
}

class Value: public Compiler::Operand {
 public:
  Value(Site* site, Site* target):
    reads(0), lastRead(0), sites(site), source(0), target(target), buddy(this),
    high(0), home(NoFrameIndex)
  { }

  virtual void addPredecessor(Context*, Event*) { }
  
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  Site* target;
  Value* buddy;
  Value* high;
  int8_t home;
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
    registerResources
    (static_cast<RegisterResource*>
     (zone->allocate(sizeof(RegisterResource) * arch->registerCount()))),
    frameResources(0),
    firstConstant(0),
    lastConstant(0),
    machineCode(0),
    firstEvent(0),
    lastEvent(0),
    forkState(0),
    subroutine(0),
    logicalIp(-1),
    constantCount(0),
    logicalCodeLength(0),
    parameterFootprint(0),
    localFootprint(0),
    machineCodeSize(0),
    alignedFrameSize(0),
    availableRegisterCount(arch->registerCount()),
    constantCompare(CompareNone)
  {
    for (unsigned i = 0; i < arch->registerCount(); ++i) {
      new (registerResources + i) RegisterResource(arch->reserved(i));
      if (registerResources[i].reserved) {
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
  Cell* saved;
  Event* predecessor;
  LogicalInstruction** logicalCode;
  RegisterResource* registerResources;
  FrameResource* frameResources;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  uint8_t* machineCode;
  Event* firstEvent;
  Event* lastEvent;
  ForkState* forkState;
  MySubroutine* subroutine;
  int logicalIp;
  unsigned constantCount;
  unsigned logicalCodeLength;
  unsigned parameterFootprint;
  unsigned localFootprint;
  unsigned machineCodeSize;
  unsigned alignedFrameSize;
  unsigned availableRegisterCount;
  ConstantCompare constantCompare;
};

unsigned
RegisterResource::toString(Context* c, char* buffer, unsigned bufferSize)
{
  return snprintf
    (buffer, bufferSize, "register %"LD, this - c->registerResources);
}

unsigned
FrameResource::toString(Context* c, char* buffer, unsigned bufferSize)
{
  return snprintf(buffer, bufferSize, "frame %"LD, this - c->frameResources);
}

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

unsigned
count(Cell* c)
{
  unsigned count = 0;
  while (c) {
    ++ count;
    c = c->next;
  }
  return count;
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

unsigned
usableFrameSize(Context* c)
{
  return c->alignedFrameSize - c->arch->frameFooterSize();
}

int
frameIndex(Context* c, int index)
{
  assert(c, static_cast<int>
         (usableFrameSize(c) + c->parameterFootprint - index - 1) >= 0);

  return usableFrameSize(c) + c->parameterFootprint - index - 1;
}

unsigned
frameIndexToOffset(Context* c, unsigned frameIndex)
{
  return ((frameIndex >= usableFrameSize(c)) ?
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

  return ((normalizedOffset >= c->alignedFrameSize) ?
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
    Element(Value* value, unsigned localIndex):
      value(value), localIndex(localIndex)
    { }

    Value* const value;
    const unsigned localIndex;
  };

  FrameIterator(Context* c, Stack* stack, Local* locals):
    stack(stack), locals(locals), localIndex(c->localFootprint - 1)
  { }

  bool hasMore() {
    while (stack and stack->value == 0) stack = stack->next;

    while (localIndex >= 0 and locals[localIndex].value == 0) -- localIndex;

    return stack != 0 or localIndex >= 0;
  }

  Element next(Context* c) {
    Value* v;
    unsigned li;
    if (stack) {
      Stack* s = stack;
      v = s->value;
      li = s->index + c->localFootprint;
      stack = stack->next;
    } else {
      Local* l = locals + localIndex;
      v = l->value;
      li = localIndex;
      -- localIndex;
    }
    return Element(v, li);
  }

  Stack* stack;
  Local* locals;
  int localIndex;
};

int
frameIndex(Context* c, FrameIterator::Element* element)
{
  return frameIndex(c, element->localIndex);
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
    (*previous)->release(c, originalValue);
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
addSite(Context* c, Value* v, Site* s)
{
  if (not findSite(c, v, s)) {
    if (DebugSites) {
      char buffer[256]; s->toString(c, buffer, 256);
      fprintf(stderr, "add site %s to %p\n", buffer, v);
    }
    s->acquire(c, v);
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
  Value* p = v;
  do {
    if (valid(p->reads)) {
      return p->reads;
    }
    p = p->buddy;
  } while (p != v);

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
deadBuddy(Context* c, Value* v, Read* r UNUSED)
{
  assert(c, v->buddy != v);
  assert(c, r);

  if (DebugBuddies) {
    fprintf(stderr, "remove dead buddy %p from", v);
    for (Value* p = v->buddy; p != v; p = p->buddy) {
      fprintf(stderr, " %p", p);
    }
    fprintf(stderr, "\n");
  }

  Value* next = v->buddy;
  v->buddy = v;
  Value* p = next;
  while (p->buddy != v) p = p->buddy;
  p->buddy = next;

  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
    it.remove(c);
    
    addSite(c, next, s);
  }
}

void
popRead(Context* c, Event* e UNUSED, Value* v)
{
  assert(c, e == v->reads->event);

  if (DebugReads) {
    fprintf(stderr, "pop read %p from %p next %p event %p (%s)\n",
            v->reads, v, v->reads->next(c), e, (e ? e->name() : 0));
  }

  v->reads = v->reads->next(c);

  if (not valid(v->reads)) {
    Read* r = live(v);
    if (r) {
      deadBuddy(c, v, r);
    } else {
      clearSites(c, v);
    }
  }
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
decrementAvailableRegisterCount(Context* c)
{
  assert(c, c->availableRegisterCount);
  -- c->availableRegisterCount;
  
  if (DebugResources) {
    fprintf(stderr, "%d registers available\n", c->availableRegisterCount);
  }
}

void
incrementAvailableRegisterCount(Context* c)
{
  ++ c->availableRegisterCount;

  if (DebugResources) {
    fprintf(stderr, "%d registers available\n", c->availableRegisterCount);
  }
}

void
increment(Context* c, RegisterResource* r)
{
  if (not r->reserved) {
    if (DebugResources) {
      char buffer[256]; r->toString(c, buffer, 256);
      fprintf(stderr, "increment %s to %d\n", buffer, r->referenceCount + 1);
    }

    ++ r->referenceCount;

    if (r->referenceCount == 1) {
      decrementAvailableRegisterCount(c);
    }
  }
}

void
decrement(Context* c, Resource* r)
{
  if (not r->reserved) {
    if (DebugResources) {
      char buffer[256]; r->toString(c, buffer, 256);
      fprintf(stderr, "decrement %s to %d\n", buffer, r->referenceCount - 1);
    }

    assert(c, r->referenceCount > 0);

    -- r->referenceCount;

    if (r->referenceCount == 0) {
      incrementAvailableRegisterCount(c);
    }
  }
}

void
freezeResource(Context* c, Resource* r, Value* v)
{
  if (DebugResources) {
    char buffer[256]; r->toString(c, buffer, 256);
    fprintf(stderr, "%p freeze %s to %d\n", v, buffer, r->freezeCount + 1);
  }
    
  ++ r->freezeCount;
}

void
RegisterResource::freeze(Context* c, Value* v)
{
  if (not reserved) {
    freezeResource(c, this, v);

    if (freezeCount == 1) {
      decrementAvailableRegisterCount(c);
    }
  }
}

void
FrameResource::freeze(Context* c, Value* v)
{
  freezeResource(c, this, v);
}

void
thawResource(Context* c, Resource* r, Value* v)
{
  if (not r->reserved) {
    if (DebugResources) {
      char buffer[256]; r->toString(c, buffer, 256);
      fprintf(stderr, "%p thaw %s to %d\n", v, buffer, r->freezeCount - 1);
    }

    assert(c, r->freezeCount);

    -- r->freezeCount;
  }
}

void
RegisterResource::thaw(Context* c, Value* v)
{
  if (not reserved) {
    thawResource(c, this, v);

    if (freezeCount == 0) {
      incrementAvailableRegisterCount(c);
    }
  }
}

void
FrameResource::thaw(Context* c, Value* v)
{
  thawResource(c, this, v);
}

class Target {
 public:
  static const unsigned Penalty = 10;
  static const unsigned Impossible = 20;

  Target(): cost(Impossible) { }

  Target(int index, OperandType type, unsigned cost):
    index(index), type(type), cost(cost)
  { }

  int16_t index;
  OperandType type;
  uint8_t cost;
};

Target
pickTarget(Context* c, Read* r, bool intersectRead,
           unsigned registerReserveCount);

unsigned
resourceCost(Context* c UNUSED, Value* v, Resource* r)
{
  if (r->reserved or r->freezeCount or r->referenceCount) {
    return Target::Impossible;
  } else if (r->value) {
    assert(c, findSite(c, r->value, r->site));

    if (v and buddies(r->value, v)) {
      return 0;
    } else if (hasMoreThanOneSite(r->value)) {
      return 2;
    } else {
      return 4;
    }
  } else {
    return 0;
  }
}

int
pickRegisterTarget(Context* c, Value* v, uint32_t mask, unsigned* cost)
{
  int target = NoRegister;
  unsigned bestCost = Target::Impossible;
  for (int i = c->arch->registerCount() - 1; i >= 0; --i) {
    if ((1 << i) & mask) {
      RegisterResource* r = c->registerResources + i;
      unsigned myCost = resourceCost(c, v, r);
      if ((static_cast<uint32_t>(1) << i) == mask) {
        *cost = myCost;
        return i;
      } else if (myCost < bestCost) {
        bestCost = myCost;
        target = i;
      }
    }
  }

  *cost = bestCost;
  return target;
}

Target
pickRegisterTarget(Context* c, Value* v, uint32_t mask)
{
  unsigned cost;
  int number = pickRegisterTarget(c, v, mask, &cost);
  return Target(number, RegisterOperand, cost);
}

unsigned
frameCost(Context* c, Value* v, int frameIndex)
{
  return resourceCost(c, v, c->frameResources + frameIndex) + 1;
}

Target
pickFrameTarget(Context* c, Value* v)
{
  Target best;

  Value* p = v;
  do {
    if (p->home >= 0) {
      Target mine(p->home, MemoryOperand, frameCost(c, v, p->home));
      if (mine.cost == 1) {
        return mine;
      } else if (mine.cost < best.cost) {
        best = mine;
      }
    }
    p = p->buddy;
  } while (p != v);

  return best;
}

Target
pickAnyFrameTarget(Context* c, Value* v)
{
  Target best;

  unsigned count = usableFrameSize(c) + c->parameterFootprint;
  for (unsigned i = 0; i < count; ++i) {
    Target mine(i, MemoryOperand, frameCost(c, v, i));
    if (mine.cost == 1) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }    
  }

  return best;
}

Target
pickTarget(Context* c, Read* read, bool intersectRead,
           unsigned registerReserveCount)
{
  SiteMask mask;
  read->intersect(&mask);

  unsigned registerPenalty = (c->availableRegisterCount > registerReserveCount
                              ? 0 : Target::Penalty);
  
  Target best;
  if ((mask.typeMask & (1 << RegisterOperand))) {
    Target mine = pickRegisterTarget(c, read->value, mask.registerMask);

    mine.cost += registerPenalty;

    if (mine.cost == 0) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  if ((mask.typeMask & (1 << MemoryOperand)) && mask.frameIndex >= 0) {
    Target mine(mask.frameIndex, MemoryOperand,
                frameCost(c, read->value, mask.frameIndex));
    if (mine.cost == 0) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  if (intersectRead) {
    return best;
  }

  { Target mine = pickRegisterTarget(c, read->value, ~0);

    mine.cost += registerPenalty;

    if (mine.cost == 0) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  { Target mine = pickFrameTarget(c, read->value);
    if (mine.cost == 0) {
      return mine;
    } else if (mine.cost < best.cost) {
      best = mine;
    }
  }

  if (best.cost > 3 and c->availableRegisterCount == 0) {
    // there are no free registers left, so moving from memory to
    // memory isn't an option - try harder to find an available frame
    // site:
    best = pickAnyFrameTarget(c, read->value);
    assert(c, best.cost <= 3);
  }

  return best;
}

void
acquire(Context* c, Resource* resource, Value* value, Site* site);

void
release(Context* c, Resource* resource, Value* value, Site* site);

ConstantSite*
constantSite(Context* c, Promise* value);

ShiftMaskPromise*
shiftMaskPromise(Context* c, Promise* base, unsigned shift, int64_t mask)
{
  return new (c->zone->allocate(sizeof(ShiftMaskPromise)))
    ShiftMaskPromise(base, shift, mask);
}

CombinedPromise*
combinedPromise(Context* c, Promise* low, Promise* high)
{
  return new (c->zone->allocate(sizeof(CombinedPromise)))
    CombinedPromise(low, high);
}

class ConstantSite: public Site {
 public:
  ConstantSite(Promise* value): value(value) { }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize) {
    if (value->resolved()) {
      return snprintf
        (buffer, bufferSize, "constant %"LLD, value->value());
    } else {
      return snprintf(buffer, bufferSize, "constant unresolved");
    }
  }

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : 3);
  }

  virtual bool match(Context*, const SiteMask& mask) {
    return mask.typeMask & (1 << ConstantOperand);
  }

  virtual OperandType type(Context*) {
    return ConstantOperand;
  }

  virtual void asAssemblerOperand(Context* c, Site* high,
                                  Assembler::Operand* result)
  {
    Promise* v = value;
    if (high) {
      v = combinedPromise(c, value, static_cast<ConstantSite*>(high)->value);
    }
    new (result) Assembler::Constant(v);
  }

  virtual Site* copy(Context* c) {
    return constantSite(c, value);
  }

  virtual Site* copyLow(Context* c) {
    return constantSite(c, shiftMaskPromise(c, value, 0, 0xFFFFFFFF));
  }

  virtual Site* copyHigh(Context* c) {
    return constantSite(c, shiftMaskPromise(c, value, 32, 0xFFFFFFFF));
  }

  Promise* value;
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

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize) {
    if (address->resolved()) {
      return snprintf
        (buffer, bufferSize, "address %"LLD, address->value());
    } else {
      return snprintf(buffer, bufferSize, "address unresolved");
    }
  }

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : 2);
  }

  virtual bool match(Context*, const SiteMask& mask) {
    return mask.typeMask & (1 << AddressOperand);
  }

  virtual OperandType type(Context*) {
    return AddressOperand;
  }

  virtual void asAssemblerOperand(Context* c UNUSED, Site* high UNUSED,
                                  Assembler::Operand* result)
  {
    assert(c, high == 0);

    new (result) Assembler::Address(address);
  }

  virtual Site* copy(Context* c) {
    return addressSite(c, address);
  }

  virtual Site* copyLow(Context* c) {
    abort(c);
  }

  virtual Site* copyHigh(Context* c) {
    abort(c);
  }

  Promise* address;
};

AddressSite*
addressSite(Context* c, Promise* address)
{
  return new (c->zone->allocate(sizeof(AddressSite))) AddressSite(address);
}

RegisterSite*
freeRegisterSite(Context* c, uint32_t mask = ~0);

class RegisterSite: public Site {
 public:
  RegisterSite(uint32_t mask, int number):
    mask(mask), number(number)
  { }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize) {
    if (number != NoRegister) {
      return snprintf(buffer, bufferSize, "%p register %d", this, number);
    } else {
      return snprintf(buffer, bufferSize, "%p register unacquired", this);
    }
  }

  virtual unsigned copyCost(Context* c, Site* s) {
    assert(c, number != NoRegister);

    if (s and
        (this == s or
         (s->type(c) == RegisterOperand
          and (static_cast<RegisterSite*>(s)->mask & (1 << number)))))
    {
      return 0;
    } else {
      return 1;
    }
  }

  virtual bool match(Context* c UNUSED, const SiteMask& mask) {
    assert(c, number != NoRegister);

    if ((mask.typeMask & (1 << RegisterOperand))) {
      return ((static_cast<uint64_t>(1) << number) & mask.registerMask);
    } else {
      return false;
    }
  }

  virtual void acquire(Context* c, Value* v) {
    Target target;
    if (number != NoRegister) {
      target = Target(number, RegisterOperand, 0);
    } else {
      target = pickRegisterTarget(c, v, mask);
      expect(c, target.cost < Target::Impossible);
    }

    RegisterResource* resource = c->registerResources + target.index;
    ::acquire(c, resource, v, this);

    number = target.index;
  }

  virtual void release(Context* c, Value* v) {
    assert(c, number != NoRegister);

    ::release(c, c->registerResources + number, v, this);
  }

  virtual void freeze(Context* c, Value* v) {
    assert(c, number != NoRegister);

    c->registerResources[number].freeze(c, v);
  }

  virtual void thaw(Context* c, Value* v) {
    assert(c, number != NoRegister);

    c->registerResources[number].thaw(c, v);
  }

  virtual bool frozen(Context* c UNUSED) {
    assert(c, number != NoRegister);

    return c->registerResources[number].freezeCount != 0;
  }

  virtual OperandType type(Context*) {
    return RegisterOperand;
  }

  virtual void asAssemblerOperand(Context* c UNUSED, Site* high,
                                  Assembler::Operand* result)
  {
    assert(c, number != NoRegister);

    int highNumber;
    if (high) {
      highNumber = static_cast<RegisterSite*>(high)->number;
      assert(c, highNumber != NoRegister);
    } else {
      highNumber = NoRegister;
    }

    new (result) Assembler::Register(number, highNumber);
  }

  virtual Site* copy(Context* c) {
    uint32_t mask;
    
    if (number != NoRegister) {
      mask = 1 << number;
    } else {
      mask = this->mask;
    }

    return freeRegisterSite(c, mask);
  }

  virtual Site* copyLow(Context* c) {
    abort(c);
  }

  virtual Site* copyHigh(Context* c) {
    abort(c);
  }

  uint32_t mask;
  int number;
};

RegisterSite*
registerSite(Context* c, int number)
{
  assert(c, number >= 0);
  assert(c, number < static_cast<int>(c->arch->registerCount()));

  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(1 << number, number);
}

RegisterSite*
freeRegisterSite(Context* c, uint32_t mask)
{
  return new (c->zone->allocate(sizeof(RegisterSite)))
    RegisterSite(mask, NoRegister);
}

MemorySite*
memorySite(Context* c, int base, int offset = 0, int index = NoRegister,
           unsigned scale = 1);

class MemorySite: public Site {
 public:
  MemorySite(int base, int offset, int index, unsigned scale):
    acquired(false), base(base), offset(offset), index(index), scale(scale)
  { }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize) {
    if (acquired) {
      return snprintf(buffer, bufferSize, "memory %d 0x%x %d %d",
                      base, offset, index, scale);
    } else {
      return snprintf(buffer, bufferSize, "memory unacquired");
    }
  }

  virtual unsigned copyCost(Context* c, Site* s) {
    assert(c, acquired);    

    if (s and
        (this == s or
         (s->type(c) == MemoryOperand
          and static_cast<MemorySite*>(s)->base == base
          and static_cast<MemorySite*>(s)->offset == offset
          and static_cast<MemorySite*>(s)->index == index
          and static_cast<MemorySite*>(s)->scale == scale)))
    {
      return 0;
    } else {
      return 4;
    }
  }

  virtual bool match(Context* c, const SiteMask& mask) {
    assert(c, acquired);

    if (mask.typeMask & (1 << MemoryOperand)) {
      if (base == c->arch->stack()) {
        assert(c, index == NoRegister);
        return mask.frameIndex == AnyFrameIndex
          || (mask.frameIndex != NoFrameIndex
              && static_cast<int>(frameIndexToOffset(c, mask.frameIndex))
              == offset);
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  virtual void acquire(Context* c, Value* v) {
    increment(c, c->registerResources + base);
    if (index != NoRegister) {
      increment(c, c->registerResources + index);
    }

    if (base == c->arch->stack()) {
      assert(c, index == NoRegister);

      ::acquire(c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
    }

    acquired = true;
  }

  virtual void release(Context* c, Value* v) {
    if (base == c->arch->stack()) {
      assert(c, index == NoRegister);

      ::release(c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
    }

    decrement(c, c->registerResources + base);
    if (index != NoRegister) {
      decrement(c, c->registerResources + index);
    }

    acquired = false;
  }

  virtual void freeze(Context* c, Value* v) {
    if (base == c->arch->stack()) {
      c->frameResources[offsetToFrameIndex(c, offset)].freeze(c, v);
    }
  }

  virtual void thaw(Context* c, Value* v) {
    if (base == c->arch->stack()) {
      c->frameResources[offsetToFrameIndex(c, offset)].thaw(c, v);
    }
  }

  virtual bool frozen(Context* c) {
    return base == c->arch->stack()
      and c->frameResources[offsetToFrameIndex(c, offset)].freezeCount != 0;
  }

  virtual OperandType type(Context*) {
    return MemoryOperand;
  }

  virtual void asAssemblerOperand(Context* c UNUSED, Site* high UNUSED,
                                  Assembler::Operand* result)
  {
    assert(c, high == 0
           or (static_cast<MemorySite*>(high)->base == base
               and static_cast<MemorySite*>(high)->offset
               == static_cast<int>(offset + BytesPerWord)
               and static_cast<MemorySite*>(high)->index == index
               and static_cast<MemorySite*>(high)->scale == scale));

    assert(c, acquired);

    new (result) Assembler::Memory(base, offset, index, scale);
  }

  virtual Site* copy(Context* c) {
    return memorySite(c, base, offset, index, scale);
  }

  Site* copyHalf(Context* c, bool add) {
    if (add) {
      return memorySite(c, base, offset + BytesPerWord, index, scale);
    } else {
      return copy(c);
    }
  }

  virtual Site* copyLow(Context* c) {
    return copyHalf(c, c->arch->bigEndian());
  }

  virtual Site* copyHigh(Context* c) {
    return copyHalf(c, not c->arch->bigEndian());
  }

  bool acquired;
  int base;
  int offset;
  int index;
  unsigned scale;
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
    (c, c->arch->stack(), frameIndexToOffset(c, frameIndex), NoRegister, 0);
}

void
move(Context* c, Value* value, Site* src, Site* dst)
{
  src->freeze(c, value);

  addSite(c, value, dst);

  src->thaw(c, value);

  if (dst->type(c) == MemoryOperand
      and (src->type(c) == MemoryOperand
           or src->type(c) == AddressOperand))
  {
    src->freeze(c, value);
    dst->freeze(c, value);

    Site* tmp = freeRegisterSite(c);
    addSite(c, value, tmp);

    tmp->freeze(c, value);

    if (DebugMoves) {
      char srcb[256]; src->toString(c, srcb, 256);
      char tmpb[256]; tmp->toString(c, tmpb, 256);
      fprintf(stderr, "move %s to %s for %p\n", srcb, tmpb, value);
    }
      
    apply(c, Move, BytesPerWord, src, 0, BytesPerWord, tmp, 0);

    tmp->thaw(c, value);
    dst->thaw(c, value);
    src->thaw(c, value);

    src = tmp;
  }

  if (DebugMoves) {
    char srcb[256]; src->toString(c, srcb, 256);
    char dstb[256]; dst->toString(c, dstb, 256);
    fprintf(stderr, "move %s to %s for %p\n", srcb, dstb, value);
  }

  src->freeze(c, value);
  dst->freeze(c, value);
  
  apply(c, Move, BytesPerWord, src, 0, BytesPerWord, dst, 0);

  dst->thaw(c, value);
  src->thaw(c, value);
}

unsigned
sitesToString(Context* c, Site* sites, char* buffer, unsigned size)
{
  unsigned total = 0;
  for (Site* s = sites; s; s = s->next) {
    total += s->toString(c, buffer + total, size - total);

    if (s->next) {
      assert(c, size > total + 2);
      memcpy(buffer + total, ", ", 2);
      total += 2;
    }
  }

  assert(c, size > total);
  buffer[total] = 0;

  return total;
}

unsigned
sitesToString(Context* c, Value* v, char* buffer, unsigned size)
{
  unsigned total = 0;
  Value* p = v;
  do {
    if (total) {
      assert(c, size > total + 2);
      memcpy(buffer + total, "; ", 2);
      total += 2;
    }

    if (p->sites) {
      total += snprintf(buffer + total, size - total, "%p has ", p);
      total += sitesToString(c, p->sites, buffer + total, size - total);
    } else {
      total += snprintf(buffer + total, size - total, "%p has nothing", p);
    }

    p = p->buddy;
  } while (p != v); 

  return total;
}

Site*
pickTargetSite(Context* c, Read* read, bool intersectRead = false,
               unsigned registerReserveCount = 0)
{
  Target target(pickTarget(c, read, intersectRead, registerReserveCount));
  expect(c, target.cost < Target::Impossible);
  if (target.type == MemoryOperand) {
    return frameSite(c, target.index);
  } else {
    return registerSite(c, target.index);
  }
}

void
steal(Context* c, Resource* r, Value* thief)
{
  if (DebugResources) {
    char resourceBuffer[256]; r->toString(c, resourceBuffer, 256);
    char siteBuffer[1024]; sitesToString(c, r->value, siteBuffer, 1024);
    fprintf(stderr, "%p steal %s from %p (%s)\n",
            thief, resourceBuffer, r->value, siteBuffer);
  }

  if (not ((thief and buddies(thief, r->value))
           or hasMoreThanOneSite(r->value)))
  {
    r->site->freeze(c, r->value);

    move(c, r->value, r->site, pickTargetSite
         (c, live(r->value), false, StealRegisterReserveCount));

    r->site->thaw(c, r->value);
  }

  removeSite(c, r->value, r->site);
}

void
acquire(Context* c, Resource* resource, Value* value, Site* site)
{
  assert(c, value);
  assert(c, site);

  if (not resource->reserved) {
    if (DebugResources) {
      char buffer[256]; resource->toString(c, buffer, 256);
      fprintf(stderr, "%p acquire %s\n", value, buffer);
    }

    if (resource->value) {
      assert(c, findSite(c, resource->value, resource->site));
      steal(c, resource, value);
    }

    resource->value = value;
    resource->site = site;
  }
}

void
release(Context* c, Resource* resource, Value* value UNUSED, Site* site UNUSED)
{
  if (not resource->reserved) {
    if (DebugResources) {
      char buffer[256]; resource->toString(c, buffer, 256);
      fprintf(stderr, "%p release %s\n", resource->value, buffer);
    }

    assert(c, resource->value);
    assert(c, resource->site);

    assert(c, buddies(resource->value, value));
    assert(c, site == resource->site);
    
    resource->value = 0;
    resource->site = 0;
  }
}

class SingleRead: public Read {
 public:
  SingleRead(const SiteMask& mask):
    next_(0), mask(mask)
  { }

  virtual bool intersect(SiteMask* mask) {
    *mask = ::intersect(*mask, this->mask);

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
  SiteMask mask;
};

Read*
read(Context* c, const SiteMask& mask)
{
  assert(c, (mask.typeMask != 1 << MemoryOperand) or mask.frameIndex >= 0);

  return new (c->zone->allocate(sizeof(SingleRead)))
    SingleRead(mask);
}

Read*
anyRegisterRead(Context* c)
{
  return read(c, SiteMask(1 << RegisterOperand, ~0, NoFrameIndex));
}

Read*
registerOrConstantRead(Context* c)
{
  return read
    (c, SiteMask
     ((1 << RegisterOperand) | (1 << ConstantOperand), ~0, NoFrameIndex));
}

Read*
fixedRegisterRead(Context* c, int number)
{
  return read(c, SiteMask(1 << RegisterOperand, 1 << number, NoFrameIndex));
}

class MultiRead: public Read {
 public:
  MultiRead():
    reads(0), lastRead(0), firstTarget(0), lastTarget(0), visited(false)
  { }

  virtual bool intersect(SiteMask* mask) {
    bool result = false;
    if (not visited) {
      visited = true;
      for (Cell** cell = &reads; *cell;) {
        Read* r = static_cast<Read*>((*cell)->value);
        bool valid = r->intersect(mask);
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

//     fprintf(stderr, "append %p to %p for %p\n", r, lastTarget, this);

    lastTarget->value = r;
  }

  virtual Read* next(Context* c) {
    abort(c);
  }

  void allocateTarget(Context* c) {
    Cell* cell = cons(c, 0, 0);

//     fprintf(stderr, "allocate target for %p: %p\n", this, cell);

    if (lastTarget) {
      lastTarget->next = cell;
    } else {
      firstTarget = cell;
    }
    lastTarget = cell;
  }

  Read* nextTarget() {
//     fprintf(stderr, "next target for %p: %p\n", this, firstTarget);

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
multiRead(Context* c)
{
  return new (c->zone->allocate(sizeof(MultiRead))) MultiRead;
}

class StubRead: public Read {
 public:
  StubRead():
    next_(0), read(0), visited(false), valid_(true)
  { }

  virtual bool intersect(SiteMask* mask) {
    if (not visited) {
      visited = true;
      if (read) {
        bool valid = read->intersect(mask);
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
stubRead(Context* c)
{
  return new (c->zone->allocate(sizeof(StubRead))) StubRead;
}

void
asAssemblerOperand(Context* c, Site* low, Site* high,
                   Assembler::Operand* result)
{
  low->asAssemblerOperand(c, high, result);
}

class OperandUnion: public Assembler::Operand {
  // must be large enough and aligned properly to hold any operand
  // type (we'd use an actual union type here, except that classes
  // with constructors cannot be used in a union):
  uintptr_t padding[4];
};

void
apply(Context* c, UnaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High)
{
  assert(c, s1High == 0 or s1Low->type(c) == s1High->type(c));

  OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  c->assembler->apply(op, s1Size, s1Type, &s1Union);
}

void
apply(Context* c, BinaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High)
{
  assert(c, s1High == 0 or s1Low->type(c) == s1High->type(c));
  assert(c, s2High == 0 or s2Low->type(c) == s2High->type(c));

  OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  OperandType s2Type = s2Low->type(c);
  OperandUnion s2Union; asAssemblerOperand(c, s2Low, s2High, &s2Union);

  c->assembler->apply(op, s1Size, s1Type, &s1Union,
                      s2Size, s2Type, &s2Union);
}

void
apply(Context* c, TernaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High,
      unsigned s3Size, Site* s3Low, Site* s3High)
{
  assert(c, s1High == 0 or s1Low->type(c) == s1High->type(c));
  assert(c, s2High == 0 or s2Low->type(c) == s2High->type(c));
  assert(c, s3High == 0 or s3Low->type(c) == s3High->type(c));

  OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  OperandType s2Type = s2Low->type(c);
  OperandUnion s2Union; asAssemblerOperand(c, s2Low, s2High, &s2Union);

  OperandType s3Type = s3Low->type(c);
  OperandUnion s3Union; asAssemblerOperand(c, s3Low, s3High, &s3Union);

  c->assembler->apply(op, s1Size, s1Type, &s1Union,
                      s2Size, s2Type, &s2Union,
                      s3Size, s3Type, &s3Union);
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
    if (not (s->match(c, SiteMask(1 << MemoryOperand, 0, AnyFrameIndex))
             and offsetToFrameIndex
             (c, static_cast<MemorySite*>(s)->offset)
             >= popIndex))
    {
      if (false) {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "remove %s from %p at %d pop index %d\n",
                buffer, v, offsetToFrameIndex
                (c, static_cast<MemorySite*>(s)->offset), popIndex);
      }
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
    popRead(c, e, r->value);
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
        fprintf(stderr, "local save read %p at %d of %d\n",
                local->value, ::frameIndex(c, li),
                usableFrameSize(c) + c->parameterFootprint);
      }

      addRead(c, e, local->value, read
              (c, SiteMask(1 << MemoryOperand, 0, ::frameIndex(c, li))));
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
    uint32_t registerMask = ~0;
    Stack* s = argumentStack;
    unsigned index = 0;
    unsigned frameIndex = 0;

    if (argumentCount) {
      while (true) {
        Read* target;
        if (index < c->arch->argumentRegisterCount()) {
          int number = c->arch->argumentRegister(index);
        
          if (DebugReads) {
            fprintf(stderr, "reg %d arg read %p\n", number, s->value);
          }

          target = fixedRegisterRead(c, number);
          registerMask &= ~(1 << number);
        } else {
          if (DebugReads) {
            fprintf(stderr, "stack %d arg read %p\n", frameIndex, s->value);
          }

          target = read(c, SiteMask(1 << MemoryOperand, 0, frameIndex));
          ++ frameIndex;
        }
        addRead(c, this, s->value, target);

        if ((++ index) < argumentCount) {
          s = s->next;
        } else {
          break;
        }
      }
    }

    if (DebugReads) {
      fprintf(stderr, "address read %p\n", address);
    }

    { bool thunk;
      uint8_t typeMask;
      uint64_t planRegisterMask;
      c->arch->plan
        ((flags & Compiler::Aligned) ? AlignedCall : Call, BytesPerWord,
         &typeMask, &planRegisterMask, &thunk);

      assert(c, thunk == 0);

      addRead(c, this, address, read
              (c, SiteMask
               (typeMask, registerMask & planRegisterMask, AnyFrameIndex)));
    }

    int footprint = stackArgumentFootprint;
    for (Stack* s = stackBefore; s; s = s->next) {
      if (s->value) {
        if (footprint > 0) {
          if (DebugReads) {
            fprintf(stderr, "stack arg read %p at %d of %d\n",
                    s->value, frameIndex,
                    usableFrameSize(c) + c->parameterFootprint);
          }

          addRead(c, this, s->value, read
                  (c, SiteMask(1 << MemoryOperand, 0, frameIndex)));
        } else {
          unsigned logicalIndex = ::frameIndex
            (c, s->index + c->localFootprint);

          if (DebugReads) {
            fprintf(stderr, "stack save read %p at %d of %d\n",
                    s->value, logicalIndex,
                    usableFrameSize(c) + c->parameterFootprint);
          }

          addRead(c, this, s->value, read
                  (c, SiteMask(1 << MemoryOperand, 0, logicalIndex)));
        }
      }

      -- footprint;

      if (footprint == 0) {
        unsigned logicalIndex = ::frameIndex(c, s->index + c->localFootprint);

        assert(c, logicalIndex >= frameIndex);

        padding = logicalIndex - frameIndex;
        padIndex = s->index + c->localFootprint;
      }

      ++ frameIndex;
    }

    popIndex
      = usableFrameSize(c)
      + c->parameterFootprint
      - (stackBefore ? stackBefore->index + 1 - stackArgumentFootprint : 0)
      - c->localFootprint;

    assert(c, static_cast<int>(popIndex) >= 0);

    saveLocals(c, this);
  }

  virtual const char* name() {
    return "CallEvent";
  }

  virtual void compile(Context* c) {
    apply(c, (flags & Compiler::Aligned) ? AlignedCall : Call, BytesPerWord,
          address->source, 0);

    if (traceHandler) {
      traceHandler->handleTrace(codePromise(c, c->assembler->offset()),
                                padIndex, padding);
    }

    clean(c, this, stackBefore, localsBefore, reads, popIndex);

    if (resultSize and live(result)) {
      addSite(c, result, registerSite(c, c->arch->returnLow()));
      if (resultSize > BytesPerWord and live(result->high)) {
        addSite(c, result->high, registerSite(c, c->arch->returnHigh()));
      }
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
      addRead(c, this, value, fixedRegisterRead(c, c->arch->returnLow()));
      if (size > BytesPerWord) {
        addRead(c, this, value->high,
                fixedRegisterRead(c, c->arch->returnHigh()));
      }
    }
  }

  virtual const char* name() {
    return "ReturnEvent";
  }

  virtual void compile(Context* c) {
    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
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
addBuddy(Value* original, Value* buddy)
{
  buddy->buddy = original;
  Value* p = original;
  while (p->buddy != original) p = p->buddy;
  p->buddy = buddy;

  if (DebugBuddies) {
    fprintf(stderr, "add buddy %p to", buddy);
    for (Value* p = buddy->buddy; p != buddy; p = p->buddy) {
      fprintf(stderr, " %p", p);
    }
    fprintf(stderr, "\n");
  }
}

void
maybeMove(Context* c, BinaryOperation type, unsigned srcSize,
          unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst,
          const SiteMask& dstMask)
{
  Read* read = live(dst);
  bool isStore = read == 0;

  Site* target;
  if (dst->target) {
    target = dst->target;
  } else if (isStore) {
    return;
  } else {
    target = pickTargetSite(c, read);
  }

  unsigned cost = src->source->copyCost(c, target);

  if (srcSelectSize < dstSize) cost = 1;

  if (cost) {
    bool useTemporary = ((target->type(c) == MemoryOperand
                          and src->source->type(c) == MemoryOperand)
                         or (srcSelectSize < dstSize
                             and target->type(c) != RegisterOperand));

    src->source->freeze(c, src);

    addSite(c, dst, target);

    src->source->thaw(c, src);

    bool addOffset = srcSize != srcSelectSize
      and c->arch->bigEndian()
      and src->source->type(c) == MemoryOperand;

    if (addOffset) {
      static_cast<MemorySite*>(src->source)->offset
        += (srcSize - srcSelectSize);
    }

    target->freeze(c, dst);

    if (target->match(c, dstMask) and not useTemporary) {
      if (DebugMoves) {
        char srcb[256]; src->source->toString(c, srcb, 256);
        char dstb[256]; target->toString(c, dstb, 256);
        fprintf(stderr, "move %s to %s for %p to %p\n",
                srcb, dstb, src, dst);
      }

      src->source->freeze(c, src);

      apply(c, type, min(srcSelectSize, dstSize), src->source, 0,
            dstSize, target, 0);

      src->source->thaw(c, src);
    } else {
      // pick a temporary register which is valid as both a
      // destination and a source for the moves we need to perform:

      bool thunk;
      uint8_t srcTypeMask;
      uint64_t srcRegisterMask;
      uint8_t dstTypeMask;
      uint64_t dstRegisterMask;

      c->arch->plan(type, dstSize, &srcTypeMask, &srcRegisterMask,
                    dstSize, &dstTypeMask, &dstRegisterMask,
                    &thunk);

      assert(c, dstMask.typeMask & srcTypeMask & (1 << RegisterOperand));

      Site* tmpTarget = freeRegisterSite
        (c, dstMask.registerMask & srcRegisterMask);

      src->source->freeze(c, src);

      addSite(c, dst, tmpTarget);

      tmpTarget->freeze(c, dst);

      if (DebugMoves) {
        char srcb[256]; src->source->toString(c, srcb, 256);
        char dstb[256]; tmpTarget->toString(c, dstb, 256);
        fprintf(stderr, "move %s to %s for %p to %p\n",
                srcb, dstb, src, dst);
      }

      apply(c, type, srcSelectSize, src->source, 0, dstSize, tmpTarget, 0);

      tmpTarget->thaw(c, dst);

      src->source->thaw(c, src);

      if (useTemporary or isStore) {
        if (DebugMoves) {
          char srcb[256]; tmpTarget->toString(c, srcb, 256);
          char dstb[256]; target->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p to %p\n",
                  srcb, dstb, src, dst);
        }

        tmpTarget->freeze(c, dst);

        apply(c, Move, dstSize, tmpTarget, 0, dstSize, target, 0);

        tmpTarget->thaw(c, dst);

        if (isStore) {
          removeSite(c, dst, tmpTarget);
        }
      } else {
        removeSite(c, dst, target);
      }
    }

    target->thaw(c, dst);

    if (addOffset) {
      static_cast<MemorySite*>(src->source)->offset
        -= (srcSize - srcSelectSize);
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
}

Value*
value(Context* c, Site* site = 0, Site* target = 0)
{
  return new (c->zone->allocate(sizeof(Value))) Value(site, target);
}

void
split(Context* c, Value* v)
{
  assert(c, v->high == 0);

  v->high = value(c);
  for (SiteIterator it(v); it.hasMore();) {
    Site* s = it.next();
    removeSite(c, v, s);
    
    addSite(c, v, s->copyLow(c));
    addSite(c, v->high, s->copyHigh(c));
  }
}

void
maybeSplit(Context* c, Value* v)
{
  if (v->high == 0) {
    split(c, v);
  }
}

void
grow(Context* c, Value* v)
{
  assert(c, v->high == 0);

  v->high = value(c);
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, BinaryOperation type, unsigned srcSize,
            unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst,
            const SiteMask& srcLowMask, const SiteMask& srcHighMask,
            const SiteMask& dstLowMask, const SiteMask& dstHighMask):
    Event(c), type(type), srcSize(srcSize), srcSelectSize(srcSelectSize),
    src(src), dstSize(dstSize), dst(dst), dstLowMask(dstLowMask),
    dstHighMask(dstHighMask)
  {
    assert(c, srcSelectSize <= srcSize);

    addRead(c, this, src, read(c, srcLowMask));
    if (srcSelectSize > BytesPerWord) {
      maybeSplit(c, src);
      addRead(c, this, src->high, read(c, srcHighMask));
    }
    
    if (dstSize > BytesPerWord) {
      grow(c, dst);
    }
  }

  virtual const char* name() {
    return "MoveEvent";
  }

  virtual void compile(Context* c) {
    if (srcSelectSize <= BytesPerWord and dstSize <= BytesPerWord) {
      maybeMove(c, type, srcSize, srcSelectSize, src, dstSize, dst,
                dstLowMask);
    } else if (srcSelectSize == dstSize) {
      maybeMove(c, Move, BytesPerWord, BytesPerWord, src, BytesPerWord, dst,
                dstLowMask);
      maybeMove(c, Move, BytesPerWord, BytesPerWord, src->high, BytesPerWord,
                dst->high, dstHighMask);
    } else if (srcSize > BytesPerWord) {
      assert(c, dstSize == BytesPerWord);

      maybeMove(c, Move, BytesPerWord, BytesPerWord, src, BytesPerWord, dst,
                dstLowMask);
    } else {
      assert(c, srcSize == BytesPerWord);
      assert(c, srcSelectSize == BytesPerWord);

      if (dst->high->target or live(dst->high)) {
        assert(c, dstLowMask.typeMask & (1 << RegisterOperand));

        Site* low = freeRegisterSite(c, dstLowMask.registerMask);

        src->source->freeze(c, src);

        addSite(c, dst, low);

        low->freeze(c, dst);
          
        if (DebugMoves) {
          char srcb[256]; src->source->toString(c, srcb, 256);
          char dstb[256]; low->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p\n",
                  srcb, dstb, src);
        }

        apply(c, Move, BytesPerWord, src->source, 0, BytesPerWord, low, 0);

        low->thaw(c, dst);

        src->source->thaw(c, src);

        assert(c, dstHighMask.typeMask & (1 << RegisterOperand));

        Site* high = freeRegisterSite(c, dstHighMask.registerMask);

        low->freeze(c, dst);

        addSite(c, dst->high, high);

        high->freeze(c, dst->high);
        
        if (DebugMoves) {
          char srcb[256]; low->toString(c, srcb, 256);
          char dstb[256]; high->toString(c, dstb, 256);
          fprintf(stderr, "extend %s to %s for %p %p\n",
                  srcb, dstb, dst, dst->high);
        }

        apply(c, Move, BytesPerWord, low, 0, dstSize, low, high);

        high->thaw(c, dst->high);

        low->thaw(c, dst);
      } else {
        maybeMove(c, Move, BytesPerWord, BytesPerWord, src, BytesPerWord, dst,
                  dstLowMask);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }

  BinaryOperation type;
  unsigned srcSize;
  unsigned srcSelectSize;
  Value* src;
  unsigned dstSize;
  Value* dst;
  SiteMask dstLowMask;
  SiteMask dstHighMask;
};

void
appendMove(Context* c, BinaryOperation type, unsigned srcSize,
           unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst)
{
  bool thunk;
  uint8_t srcTypeMask;
  uint64_t srcRegisterMask;
  uint8_t dstTypeMask;
  uint64_t dstRegisterMask;

  c->arch->plan(type, srcSelectSize, &srcTypeMask, &srcRegisterMask,
                dstSize, &dstTypeMask, &dstRegisterMask,
                &thunk);

  assert(c, not thunk);

  append(c, new (c->zone->allocate(sizeof(MoveEvent)))
         MoveEvent
         (c, type, srcSize, srcSelectSize, src, dstSize, dst,
          SiteMask(srcTypeMask, srcRegisterMask, AnyFrameIndex),
          SiteMask(srcTypeMask, srcRegisterMask >> 32, AnyFrameIndex),
          SiteMask(dstTypeMask, dstRegisterMask, AnyFrameIndex),
          SiteMask(dstTypeMask, dstRegisterMask >> 32, AnyFrameIndex)));
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
               const SiteMask& firstMask, const SiteMask& secondMask):
    Event(c), size(size), first(first), second(second)
  {
    addRead(c, this, first, read(c, firstMask));
    addRead(c, this, second, read(c, secondMask));
  }

  virtual const char* name() {
    return "CompareEvent";
  }

  virtual void compile(Context* c) {
    ConstantSite* firstConstant = findConstantSite(c, first);
    ConstantSite* secondConstant = findConstantSite(c, second);

    if (firstConstant and secondConstant) {
      int64_t d = firstConstant->value->value()
        - secondConstant->value->value();

      if (d < 0) {
        c->constantCompare = CompareLess;
      } else if (d > 0) {
        c->constantCompare = CompareGreater;
      } else {
        c->constantCompare = CompareEqual;
      }
    } else {
      c->constantCompare = CompareNone;

      apply(c, Compare, size, first->source, 0, size, second->source, 0);
    }

    popRead(c, this, first);
    popRead(c, this, second);
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
          SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
          SiteMask(secondTypeMask, secondRegisterMask, AnyFrameIndex)));
}

void
preserve(Context* c, Value* v, Site* s, Read* r)
{
  s->freeze(c, v);

  move(c, v, s, pickTargetSite(c, r));

  s->thaw(c, v);
}

Site*
getTarget(Context* c, Value* value, Value* result, const SiteMask& resultMask)
{
  Site* s;
  Value* v;
  Read* r = liveNext(c, value);
  if (c->arch->condensedAddressing() or r == 0) {
    s = value->source;
    v = value;
    if (r and not hasMoreThanOneSite(v)) {
      preserve(c, v, s, r);
    }
  } else {
    SingleRead r(resultMask);
    s = pickTargetSite(c, &r, true);
    v = result;
    addSite(c, result, s);
  }

  removeSite(c, v, s);

  s->freeze(c, v);

  return s;
}

Site*
source(Value* v)
{
  return v ? v->source : 0;
}

void
freezeSource(Context* c, unsigned size, Value* v)
{
  v->source->freeze(c, v);
  if (size > BytesPerWord) {
    v->high->source->freeze(c, v->high);
  }
}

void
thawSource(Context* c, unsigned size, Value* v)
{
  v->source->thaw(c, v);
  if (size > BytesPerWord) {
    v->high->source->thaw(c, v->high);
  }
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, TernaryOperation type,
               unsigned firstSize, Value* first,
               unsigned secondSize, Value* second,
               unsigned resultSize, Value* result,
               const SiteMask& firstLowMask,
               const SiteMask& firstHighMask,
               const SiteMask& secondLowMask,
               const SiteMask& secondHighMask,
               const SiteMask& resultLowMask,
               const SiteMask& resultHighMask):
    Event(c), type(type), firstSize(firstSize), first(first),
    secondSize(secondSize), second(second), resultSize(resultSize),
    result(result), resultLowMask(resultLowMask),
    resultHighMask(resultHighMask)
  {
    addRead(c, this, first, read(c, firstLowMask));
    if (firstSize > BytesPerWord) {
      addRead(c, this, first->high, read(c, firstHighMask));
    }

    addRead(c, this, second, read(c, secondLowMask));
    if (secondSize > BytesPerWord) {
      addRead(c, this, second->high, read(c, secondHighMask));
    }

    if (resultSize > BytesPerWord) {
      grow(c, result);
    }
  }

  virtual const char* name() {
    return "CombineEvent";
  }

  virtual void compile(Context* c) {
    freezeSource(c, firstSize, first);

    Site* low = getTarget(c, second, result, resultLowMask);
    Site* high
      = (resultSize > BytesPerWord
         ? getTarget(c, second->high, result->high, resultHighMask)
         : 0);

//     fprintf(stderr, "combine %p and %p into %p\n", first, second, result);
    apply(c, type, firstSize, first->source, source(first->high),
          secondSize, second->source, source(second->high),
          resultSize, low, high);

    thawSource(c, firstSize, first);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, second);
    if (resultSize > BytesPerWord) {
      high->thaw(c, second->high);
    }

    if (live(result)) {
      addSite(c, result, low);
      if (resultSize > BytesPerWord and live(result->high)) {
        addSite(c, result->high, high);
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
  SiteMask resultLowMask;
  SiteMask resultHighMask;
};

void
removeBuddy(Context* c, Value* v)
{
  if (v->buddy != v) {
    if (DebugBuddies) {
      fprintf(stderr, "remove buddy %p from", v);
      for (Value* p = v->buddy; p != v; p = p->buddy) {
        fprintf(stderr, " %p", p);
      }
      fprintf(stderr, "\n");
    }

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
    char buffer[256]; sitesToString(c, value->sites, buffer, 256);
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
stack(Context* c, Value* value, Stack* next)
{
  return new (c->zone->allocate(sizeof(Stack)))
    Stack(next ? next->index + 1 : 0, value, next);
}

Value*
maybeBuddy(Context* c, Value* v);

Value*
pushWord(Context* c, Value* v)
{
  if (v) {
    v = maybeBuddy(c, v);
  }
    
  Stack* s = stack(c, v, c->stack);

  if (DebugFrame) {
    fprintf(stderr, "push %p\n", v);
  }

  if (v) {
    v->home = frameIndex(c, s->index + c->localFootprint);
  }
  c->stack = s;

  return v;
}

Value*
push(Context* c, unsigned footprint, Value* v)
{
  assert(c, footprint);

  bool bigEndian = c->arch->bigEndian();
  
  Value* low = v;
  
  if (bigEndian) {
    v = pushWord(c, v);
  }

  Value* high;
  if (footprint > 1) {
    assert(c, footprint == 2);

    if (BytesPerWord == 4 and low->high == 0) {
      split(c, low);
    }

    high = pushWord(c, low->high);
  } else if (v) {
    high = v->high;
  } else {
    high = 0;
  }
  
  if (not bigEndian) {
    v = pushWord(c, v);
  }

  if (v) {
    v->high = high;
  }

  return v;
}

void
popWord(Context* c)
{
  Stack* s = c->stack;
  assert(c, s->value == 0 or s->value->home >= 0);

  if (DebugFrame) {
    fprintf(stderr, "pop %p\n", s->value);
  }
    
  c->stack = s->next;  
}

Value*
pop(Context* c, unsigned footprint)
{
  assert(c, footprint);

  Stack* s = 0;

  bool bigEndian = c->arch->bigEndian();

  if (not bigEndian) {
    s = c->stack;
  }

  if (footprint > 1) {
    assert(c, footprint == 2);

#ifndef NDEBUG
    Stack* low;
    Stack* high;
    if (bigEndian) {
      high = c->stack;
      low = high->next;
    } else {
      low = c->stack;
      high = low->next;
    }

    assert(c, low->value->high == high->value
           and ((BytesPerWord == 8) xor (low->value->high != 0)));
#endif // not NDEBUG

    popWord(c);
  }

  if (bigEndian) {
    s = c->stack;
  }

  popWord(c);

  return s->value;
}

Value*
storeLocal(Context* c, unsigned footprint, Value* v, unsigned index, bool copy)
{
  assert(c, index + footprint <= c->localFootprint);

  if (copy) {
    unsigned sizeInBytes = sizeof(Local) * c->localFootprint;
    Local* newLocals = static_cast<Local*>(c->zone->allocate(sizeInBytes));
    memcpy(newLocals, c->locals, sizeInBytes);
    c->locals = newLocals;
  }

  Value* high;
  if (footprint > 1) {
    assert(c, footprint == 2);

    unsigned highIndex;
    unsigned lowIndex;
    if (c->arch->bigEndian()) {
      highIndex = index + 1;
      lowIndex = index;
    } else {
      lowIndex = index + 1;
      highIndex = index;      
    }

    if (BytesPerWord == 4) {
      assert(c, v->high);

      high = storeLocal(c, 1, v->high, highIndex, false);
    } else {
      high = 0;
    }

    index = lowIndex;
  } else {
    high = v->high;
  }

  v = maybeBuddy(c, v);
  v->high = high;

  Local* local = c->locals + index;
  local->value = v;

  if (DebugFrame) {
    fprintf(stderr, "store local %p at %d\n", local->value, index);
  }

  local->value->home = frameIndex(c, index);

  return v;
}

Value*
loadLocal(Context* c, unsigned footprint, unsigned index)
{
  assert(c, index + footprint <= c->localFootprint);

  if (footprint > 1) {
    assert(c, footprint == 2);

    if (not c->arch->bigEndian()) {
      ++ index;
    }
  }

  assert(c, c->locals[index].value);
  assert(c, c->locals[index].value->home >= 0);

  if (DebugFrame) {
    fprintf(stderr, "load local %p at %d\n", c->locals[index].value, index);
  }

  return c->locals[index].value;
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
       0, 0, result, resultSize, argumentStack,
       ceiling(secondSize, BytesPerWord) + ceiling(firstSize, BytesPerWord),
       0);
  } else {
    append
      (c, new (c->zone->allocate(sizeof(CombineEvent)))
       CombineEvent
       (c, type,
        firstSize, first,
        secondSize, second,
        resultSize, result,
        SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
        SiteMask(firstTypeMask, firstRegisterMask >> 32, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask >> 32, AnyFrameIndex),
        SiteMask(resultTypeMask, resultRegisterMask, AnyFrameIndex),
        SiteMask(resultTypeMask, resultRegisterMask >> 32, AnyFrameIndex)));
  }
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, BinaryOperation type, unsigned size, Value* value,
                 Value* result,
                 const SiteMask& valueLowMask,
                 const SiteMask& valueHighMask,
                 const SiteMask& resultLowMask,
                 const SiteMask& resultHighMask):
    Event(c), type(type), size(size), value(value), result(result),
    resultLowMask(resultLowMask), resultHighMask(resultHighMask)
  {
    addRead(c, this, value, read(c, valueLowMask));
    if (size > BytesPerWord) {
      addRead(c, this, value->high, read(c, valueHighMask));
      grow(c, result);
    }
  }

  virtual const char* name() {
    return "TranslateEvent";
  }

  virtual void compile(Context* c) {
    Site* low = getTarget(c, value, result, resultLowMask);
    Site* high
      = (size > BytesPerWord
         ? getTarget(c, value->high, result->high, resultHighMask)
         : 0);

    apply(c, type,
          size, value->source, source(value->high),
          size, low, high);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, value);
    if (size > BytesPerWord) {
      high->thaw(c, value->high);
    }

    if (live(result)) {
      addSite(c, result, low);
      if (size > BytesPerWord and live(result->high)) {
        addSite(c, result->high, high);
      }
    }
  }

  BinaryOperation type;
  unsigned size;
  Value* value;
  Value* result;
  Read* resultRead;
  SiteMask resultLowMask;
  SiteMask resultHighMask;
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

  append(c, new (c->zone->allocate(sizeof(TranslateEvent)))
         TranslateEvent
         (c, type, size, value, result,
          SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
          SiteMask(firstTypeMask, firstRegisterMask >> 32, AnyFrameIndex),
          SiteMask(resultTypeMask, resultRegisterMask, AnyFrameIndex),
          SiteMask(resultTypeMask, resultRegisterMask >> 32, AnyFrameIndex)));
}

class BarrierEvent: public Event {
 public:
  BarrierEvent(Context* c, Operation op):
    Event(c), op(op)
  { }

  virtual const char* name() {
    return "BarrierEvent";
  }

  virtual void compile(Context* c) {
    c->assembler->apply(op);
  }

  Operation op;
};

void
appendBarrier(Context* c, Operation op)
{
  append(c, new (c->zone->allocate(sizeof(BarrierEvent))) BarrierEvent(c, op));
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, int displacement, Value* index,
              unsigned scale, Value* result):
    Event(c), base(base), displacement(displacement), index(index),
    scale(scale), result(result)
  {
    addRead(c, this, base, anyRegisterRead(c));
    if (index) {
      addRead(c, this, index, registerOrConstantRead(c));
    }
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
        displacement += (constant->value->value() * scale);
        scale = 1;
      } else {
        assert(c, index->source->type(c) == RegisterOperand);
        indexRegister = static_cast<RegisterSite*>(index->source)->number;
      }
    } else {
      indexRegister = NoRegister;
    }
    assert(c, base->source->type(c) == RegisterOperand);
    int baseRegister = static_cast<RegisterSite*>(base->source)->number;

    popRead(c, this, base);
    if (index) {
      if (BytesPerWord == 8 and indexRegister != NoRegister) {
        apply(c, Move, 4, index->source, 0, 8, index->source, 0);
      }

      popRead(c, this, index);
    }

    Site* site = memorySite
      (c, baseRegister, displacement, indexRegister, scale);

    Site* low;
    if (result->high) {
      Site* high = site->copyHigh(c);
      low = site->copyLow(c);

      result->high->target = high;
      addSite(c, result->high, high);
    } else {
      low = site;
    }

    result->target = low;
    addSite(c, result, low);
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

    bool thunk;
    uint8_t typeMask;
    uint64_t registerMask;
    
    c->arch->plan(type, BytesPerWord, &typeMask, &registerMask, &thunk);

    assert(c, thunk == 0);

    addRead(c, this, address, read
            (c, SiteMask(typeMask, registerMask, AnyFrameIndex)));
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
      apply(c, type, BytesPerWord, address->source, 0);
    }

    popRead(c, this, address);
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
    addRead(c, this, object, anyRegisterRead(c));
    addRead(c, this, index, registerOrConstantRead(c));
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
      expect(c, constant->value->value() >= 0);      
    } else {
      outOfBoundsPromise = codePromise(c, static_cast<Promise*>(0));

      apply(c, Compare, 4, constantSite(c, resolved(c, 0)), 0,
            4, index->source, 0);

      Assembler::Constant outOfBoundsConstant(outOfBoundsPromise);
      a->apply
        (JumpIfLess, BytesPerWord, ConstantOperand, &outOfBoundsConstant);
    }

    assert(c, object->source->type(c) == RegisterOperand);
    MemorySite length(static_cast<RegisterSite*>(object->source)->number,
                      lengthOffset, NoRegister, 1);
    length.acquired = true;

    apply(c, Compare, 4, index->source, 0, 4, &length, 0);

    Assembler::Constant nextConstant(nextPromise);
    a->apply(JumpIfGreater, BytesPerWord, ConstantOperand, &nextConstant);

    if (constant == 0) {
      outOfBoundsPromise->offset = a->offset();
    }

    Assembler::Constant handlerConstant(resolved(c, handler));
    a->apply(Call, BytesPerWord, ConstantOperand, &handlerConstant);

    nextPromise->offset = a->offset();

    popRead(c, this, object);
    popRead(c, this, index);
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
  FrameSiteEvent(Context* c, Value* value, int index):
    Event(c), value(value), index(index)
  { }

  virtual const char* name() {
    return "FrameSiteEvent";
  }

  virtual void compile(Context* c) {
    if (live(value)) {
      addSite(c, value, frameSite(c, index));
    }
  }

  Value* value;
  int index;
};

void
appendFrameSite(Context* c, Value* value, int index)
{
  append(c, new (c->zone->allocate(sizeof(FrameSiteEvent)))
         FrameSiteEvent(c, value, index));
}

unsigned
frameFootprint(Context* c, Stack* s)
{
  return c->localFootprint + (s ? (s->index + 1) : 0);
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
//       fprintf(stderr, "next read %p for %p from %p\n", v->reads, v, p->read);
      if (not live(v)) {
        clearSites(c, v);
      }
    }
  }

  JunctionState* junctionState = link->junctionState;
  if (junctionState) {
    for (unsigned i = 0; i < junctionState->frameFootprint; ++i) {
      StubReadPair* p = junctionState->reads + i;
      
      if (p->value and p->value->reads) {
        assert(c, p->value->reads == p->read);
        popRead(c, 0, p->value);
      }
    }
  }
}

class BuddyEvent: public Event {
 public:
  BuddyEvent(Context* c, Value* original, Value* buddy):
    Event(c), original(original), buddy(buddy)
  {
    addRead(c, this, original, read(c, SiteMask(~0, ~0, AnyFrameIndex)));
  }

  virtual const char* name() {
    return "BuddyEvent";
  }

  virtual void compile(Context* c) {
//     fprintf(stderr, "original %p buddy %p\n", original, buddy);
    assert(c, hasSite(original));

    addBuddy(original, buddy);

    popRead(c, this, original);
  }

  Value* original;
  Value* buddy;
};

void
appendBuddy(Context* c, Value* original, Value* buddy)
{
  append(c, new (c->zone->allocate(sizeof(BuddyEvent)))
         BuddyEvent(c, original, buddy));
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
      popRead(c, this, r->value);
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
            c->stack->index + 1 : 0);
  }

  if (c->lastEvent) {
    c->lastEvent->next = e;
  } else {
    c->firstEvent = e;
  }
  c->lastEvent = e;

  Event* p = c->predecessor;
  if (p) {
    if (DebugAppend) {
      fprintf(stderr, "%d precedes %d\n", p->logicalInstruction->index,
              e->logicalInstruction->index);
    }

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

bool
acceptMatch(Context* c, Site* s, Read*, const SiteMask& mask)
{
  return s->match(c, mask);
}

bool
isHome(Value* v, int frameIndex)
{
  Value* p = v;
  do {
    if (p->home == frameIndex) {
      return true;
    }
    p = p->buddy;
  } while (p != v);

  return false;
}

bool
acceptForResolve(Context* c, Site* s, Read* read, const SiteMask& mask)
{
  if (acceptMatch(c, s, read, mask) and (not s->frozen(c))) {
    if (s->type(c) == RegisterOperand) {
      return c->availableRegisterCount > ResolveRegisterReserveCount;
    } else {
      assert(c, s->match(c, SiteMask(1 << MemoryOperand, 0, AnyFrameIndex)));

      return isHome(read->value, offsetToFrameIndex
                    (c, static_cast<MemorySite*>(s)->offset));
    }
  } else {
    return false;
  }
}

Site*
pickSourceSite(Context* c, Read* read, Site* target = 0,
               unsigned* cost = 0, uint8_t typeMask = ~0,
               bool intersectRead = true, bool includeBuddies = true,
               bool (*accept)(Context*, Site*, Read*, const SiteMask&)
               = acceptMatch)
{
  SiteMask mask(typeMask, ~0, AnyFrameIndex);

  if (intersectRead) {
    read->intersect(&mask);
  }

  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (SiteIterator it(read->value, includeBuddies); it.hasMore();) {
    Site* s = it.next();
    if (accept(c, s, read, mask)) {
      unsigned v = s->copyCost(c, target);
      if (v < copyCost) {
        site = s;
        copyCost = v;
      }
    }
  }

  if (DebugMoves and site and target) {
    char srcb[256]; site->toString(c, srcb, 256);
    char dstb[256]; target->toString(c, dstb, 256);
    fprintf(stderr, "pick source %s to %s for %p cost %d\n",
            srcb, dstb, read->value, copyCost);
  }

  if (cost) *cost = copyCost;
  return site;
}

Site*
readSource(Context* c, Read* r)
{
  if (DebugReads) {
    char buffer[1024]; sitesToString(c, r->value, buffer, 1024);
    fprintf(stderr, "read source for %p from %s\n", r->value, buffer);
  }

  if (not hasSite(r->value)) return 0;

  Site* site = pickSourceSite(c, r);

  if (site) {
    return site;
  } else {
    Site* target = pickTargetSite(c, r, true);
    unsigned copyCost;
    site = pickSourceSite(c, r, target, &copyCost, ~0, false);
    assert(c, copyCost);
    move(c, r->value, site, target);
    return target;    
  }
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
propagateJunctionSites(Context* c, Event* e)
{
  for (Link* sl = e->successors; sl; sl = sl->nextSuccessor) {
    Event* s = sl->successor;
    if (s->predecessors->nextPredecessor) {
      unsigned size = sizeof(Site*) * frameFootprint(c, e->stackAfter);
      Site** junctionSites = static_cast<Site**>
        (c->zone->allocate(size));
      memset(junctionSites, 0, size);

      propagateJunctionSites(c, s, junctionSites);
      break;
    }
  }
}

class SiteRecord {
 public:
  SiteRecord(Site* site, Value* value):
    site(site), value(value)
  { }

  SiteRecord() { }

  Site* site;
  Value* value;
};

class SiteRecordList {
 public:
  SiteRecordList(SiteRecord* records, unsigned capacity):
    records(records), index(0), capacity(capacity)
  { }

  SiteRecord* records;
  unsigned index;
  unsigned capacity;
};

void
freeze(Context* c, SiteRecordList* frozen, Site* s, Value* v)
{
  assert(c, frozen->index < frozen->capacity);

  s->freeze(c, v);
  new (frozen->records + (frozen->index ++)) SiteRecord(s, v);
}

void
thaw(Context* c, SiteRecordList* frozen)
{
  while (frozen->index) {
    SiteRecord* sr = frozen->records + (-- frozen->index);
    sr->site->thaw(c, sr->value);
  }
}

Site*
acquireSite(Context* c, SiteRecordList* frozen, Site* target, Value* v,
            Read* r, bool pickSource)
{
  assert(c, hasSite(v));

  unsigned copyCost;
  Site* source;
  if (pickSource) {
    source = pickSourceSite(c, r, target, &copyCost, ~0, false);
  } else {
    copyCost = 0;
    source = target;
  }

  if (copyCost) {
    target = target->copy(c);
    move(c, v, source, target);
  } else {
    target = source;
  }

  freeze(c, frozen, target, v);

  return target;
}

bool
resolveOriginalSites(Context* c, Event* e, SiteRecordList* frozen,
                     Site** sites)
{
  bool complete = true;
  for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    Value* v = el.value;
    Read* r = live(v);

    if (r) {
      if (sites[el.localIndex]) {
        if (DebugControl) {
          char buffer[256];
          sites[el.localIndex]->toString(c, buffer, 256);
          fprintf(stderr, "resolve original %s for %p local %d frame %d\n",
                  buffer, el.value, el.localIndex, frameIndex(c, &el));
        }

        acquireSite(c, frozen, sites[el.localIndex], v, r, true);
      } else {
        complete = false;
      }
    }
  }

  return complete;
}

bool
resolveSourceSites(Context* c, Event* e, SiteRecordList* frozen, Site** sites)
{
  bool complete = true;
  for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    Value* v = el.value;
    Read* r = live(v);

    if (r and sites[el.localIndex] == 0) {
      const uint32_t mask = (1 << RegisterOperand) | (1 << MemoryOperand);

      Site* s = pickSourceSite
        (c, r, 0, 0, mask, true, false, acceptForResolve);
      if (s == 0) {
        s = pickSourceSite(c, r, 0, 0, mask, false, false, acceptForResolve);
      }

      if (s) {
        if (DebugControl) {
          char buffer[256]; s->toString(c, buffer, 256);
          fprintf(stderr, "resolve source %s from %p local %d frame %d\n",
                  buffer, v, el.localIndex, frameIndex(c, &el));
        }

        sites[el.localIndex] = acquireSite(c, frozen, s, v, r, false)->copy(c);
      } else {
        complete = false;
      }
    }
  }

  return complete;
}

void
resolveTargetSites(Context* c, Event* e, SiteRecordList* frozen, Site** sites)
{
  for (FrameIterator it(c, e->stackAfter, e->localsAfter); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    Value* v = el.value;
    Read* r = live(v);

    if (r and sites[el.localIndex] == 0) {
      const uint32_t mask = (1 << RegisterOperand) | (1 << MemoryOperand);

      bool useTarget = false;
      Site* s = pickSourceSite(c, r, 0, 0, mask, true, true, acceptForResolve);
      if (s == 0) {
        s = pickSourceSite(c, r, 0, 0, mask, false, true, acceptForResolve);
        if (s == 0) {
          s = pickTargetSite(c, r, false, ResolveRegisterReserveCount);
          useTarget = true;
        }
      }

      if (DebugControl) {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "resolve target %s for %p local %d frame %d\n",
                buffer, el.value, el.localIndex, frameIndex(c, &el));
      }

      Site* acquired = acquireSite(c, frozen, s, v, r, useTarget)->copy(c);

      sites[el.localIndex] = (useTarget ? s : acquired->copy(c));
    }
  }
}

void
resolveJunctionSites(Context* c, Event* e, SiteRecordList* frozen)
{
  bool complete;
  if (e->junctionSites) {
    complete = resolveOriginalSites(c, e, frozen, e->junctionSites);
  } else {
    propagateJunctionSites(c, e);
    complete = false;
  }

  if (e->junctionSites) {
    if (not complete) {
      complete = resolveSourceSites(c, e, frozen, e->junctionSites);
      if (not complete) {
        resolveTargetSites(c, e, frozen, e->junctionSites);
      }
    }

    if (DebugControl) {
      fprintf(stderr, "resolved junction sites %p at %d\n",
              e->junctionSites, e->logicalInstruction->index);
    }
  }
}

void
resolveBranchSites(Context* c, Event* e, SiteRecordList* frozen)
{
  if (e->successors->nextSuccessor and e->junctionSites == 0) {
    unsigned footprint = frameFootprint(c, e->stackAfter);
    Site* branchSites[footprint];
    memset(branchSites, 0, sizeof(Site*) * footprint);

    if (not resolveSourceSites(c, e, frozen, branchSites)) {
      resolveTargetSites(c, e, frozen, branchSites);
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

    for (Cell* sv = e->successors->forkState->saved; sv; sv = sv->next) {
      e->snapshots = makeSnapshots
        (c, static_cast<Value*>(sv->value), e->snapshots);
    }

    if (DebugControl) {
      fprintf(stderr, "captured snapshots %p at %d\n",
              e->snapshots, e->logicalInstruction->index);
    }
  }
}

void
populateSiteTables(Context* c, Event* e, SiteRecordList* frozen)
{
  resolveJunctionSites(c, e, frozen);

  resolveBranchSites(c, e, frozen);

  captureBranchSnapshots(c, e);
}

void
setSites(Context* c, Value* v, Site* s)
{
  assert(c, live(v));

  for (; s; s = s->next) {
    addSite(c, v, s->copy(c));
  }

  if (DebugControl) {
    char buffer[256]; sitesToString(c, v->sites, buffer, 256);
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
      if (live(el.value)) {
        setSites(c, el.value, sites[el.localIndex]);
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
//     char buffer[256]; sitesToString(c, s->sites, buffer, 256);
//     fprintf(stderr, "restore %p buddy %p sites %s live %p\n",
//             s->value, s->value->buddy, buffer, live(s->value));

    s->value->buddy = s->buddy;
  }

  resetFrame(c, e);

  for (Snapshot* s = snapshots; s; s = s->next) {
    if (live(s->value)) {
      if (live(s->value) and s->sites and s->value->sites == 0) {
        setSites(c, s->value, s->sites);
      }
    }
  }
}

void
populateSources(Context* c, Event* e)
{
  SiteRecord frozenRecords[e->readCount];
  SiteRecordList frozen(frozenRecords, e->readCount);

  for (Read* r = e->reads; r; r = r->eventNext) {
    r->value->source = readSource(c, r);

    if (r->value->source) {
      if (DebugReads) {
        char buffer[256]; r->value->source->toString(c, buffer, 256);
        fprintf(stderr, "freeze source %s for %p\n",
                buffer, r->value);
      }

      freeze(c, &frozen, r->value->source, r->value);
    }
  }

  thaw(c, &frozen);
}

void
setStubRead(Context* c, StubReadPair* p, Value* v)
{
  if (v) {
    StubRead* r = stubRead(c);
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
    setStubRead(c, state->reads + e.localIndex, e.value);
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
              e->stackBefore ? e->stackBefore->index + 1 : 0);
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

    unsigned footprint = frameFootprint(c, e->stackAfter);
    SiteRecord frozenRecords[footprint];
    SiteRecordList frozen(frozenRecords, footprint);

    bool branch = e->isBranch();
    if (branch and e->successors) {
      populateSiteTables(c, e, &frozen);
    }

    populateSources(c, e);

    thaw(c, &frozen);

    e->compile(c);

    if ((not branch) and e->successors) {
      populateSiteTables(c, e, &frozen);
      thaw(c, &frozen);
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
            and (e->next->logicalInstruction != nextInstruction
                 or e != e->logicalInstruction->lastEvent)))
    {
      Block* b = e->logicalInstruction->firstEvent->block;

      while (b->nextBlock) {
        b = b->nextBlock;
      }

      if (b != block) {
        b->nextBlock = block;
      }

      block->nextInstruction = nextInstruction;
      block->assemblerBlock = a->endBlock(e->next != 0);

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
  }
}

void
addForkElement(Context* c, Value* v, ForkState* state, unsigned index)
{
  MultiRead* r = multiRead(c);
  if (DebugReads) {
    fprintf(stderr, "add multi read %p to %p\n", r, v);
  }
  addRead(c, 0, v, r);

  ForkElement* p = state->elements + index;
  p->value = v;
  p->read = r;
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
      addForkElement(c, e.value, state, count++);
    }

    for (Cell* sv = c->saved; sv; sv = sv->next) {
      addForkElement(c, static_cast<Value*>(sv->value), state, count++);
    }

    state->readCount = count;
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
maybeBuddy(Context* c, Value* v)
{
  if (v->home >= 0) {
    Value* n = value(c);
    appendBuddy(c, v, n);
    return n;
  } else {
    return v;
  }
}

class Client: public Assembler::Client {
 public:
  Client(Context* c): c(c) { }

  virtual int acquireTemporary(uint32_t mask) {
    unsigned cost;
    int r = pickRegisterTarget(c, 0, mask, &cost);
    expect(c, cost < Target::Impossible);
    save(r);
    increment(c, c->registerResources + r);
    return r;
  }

  virtual void releaseTemporary(int r) {
    decrement(c, c->registerResources + r);
  }

  virtual void save(int r) {
    RegisterResource* reg = c->registerResources + r;

    assert(c, reg->referenceCount == 0);
    assert(c, reg->freezeCount == 0);
    assert(c, not reg->reserved);

    if (reg->value) {
      steal(c, reg, 0);
    }
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
    State* s = ::saveState(&c);
    restoreState(s);
    return s;
  }

  virtual void restoreState(State* state) {
    ::restoreState(&c, static_cast<ForkState*>(state));
  }

  virtual Subroutine* startSubroutine() {
    return c.subroutine = new (c.zone->allocate(sizeof(MySubroutine)))
      MySubroutine;
  }

  virtual void endSubroutine(Subroutine* subroutine) {
    static_cast<MySubroutine*>(subroutine)->forkState = ::saveState(&c);
  }

  virtual void restoreFromSubroutine(Subroutine* subroutine) {
    ::restoreState(&c, static_cast<MySubroutine*>(subroutine)->forkState);
  }

  virtual void init(unsigned logicalCodeLength, unsigned parameterFootprint,
                    unsigned localFootprint, unsigned alignedFrameSize)
  {
    c.logicalCodeLength = logicalCodeLength;
    c.parameterFootprint = parameterFootprint;
    c.localFootprint = localFootprint;
    c.alignedFrameSize = alignedFrameSize;

    unsigned frameResourceCount = usableFrameSize(&c) + parameterFootprint;

    c.frameResources = static_cast<FrameResource*>
      (c.zone->allocate(sizeof(FrameResource) * frameResourceCount));
    
    for (unsigned i = 0; i < frameResourceCount; ++i) {
      new (c.frameResources + i) FrameResource;
    }

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
      if (DebugAppend) {
        fprintf(stderr, "visit %d pred %d\n", logicalIp,
                p->logicalInstruction->index);
      }

      p->stackAfter = c.stack;
      p->localsAfter = c.locals;

      Link* link = ::link
        (&c, p, e->predecessors, e, p->successors, c.forkState);
      e->predecessors = link;
      p->successors = link;
      c.lastEvent->visitLinks = cons(&c, link, c.lastEvent->visitLinks);

      if (DebugAppend) {
        fprintf(stderr, "populate junction reads for %d to %d\n",
                p->logicalInstruction->index, logicalIp);
      }

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

  virtual void push(unsigned footprint UNUSED) {
    assert(&c, footprint == 1);

    Value* v = value(&c);
    Stack* s = ::stack(&c, v, c.stack);
    v->home = frameIndex(&c, s->index + c.localFootprint);
    c.stack = s;
  }

  virtual void push(unsigned footprint, Operand* value) {
    ::push(&c, footprint, static_cast<Value*>(value));
  }

  virtual void save(unsigned footprint, Operand* value) {
    c.saved = cons(&c, static_cast<Value*>(value), c.saved);
    if (BytesPerWord == 4 and footprint > 1) {
      assert(&c, footprint == 2);
      assert(&c, static_cast<Value*>(value)->high);

      save(1, static_cast<Value*>(value)->high);
    }
  }

  virtual Operand* pop(unsigned footprint) {
    return ::pop(&c, footprint);
  }

  virtual void pushed() {
    Value* v = value(&c);
    appendFrameSite
      (&c, v, frameIndex
       (&c, (c.stack ? c.stack->index : 0) + c.localFootprint));

    Stack* s = ::stack(&c, v, c.stack);
    v->home = frameIndex(&c, s->index + c.localFootprint);
    c.stack = s;
  }

  virtual void popped(unsigned footprint) {
    assert(&c, c.stack->value->home >= 0);

    if (footprint > 1) {
      assert(&c, footprint == 2);
      assert(&c, c.stack->value->high == c.stack->next->value
             and ((BytesPerWord == 8) xor (c.stack->value->high != 0)));

      popped(1);
    }

    if (DebugFrame) {
      fprintf(stderr, "popped %p\n", c.stack->value);
    }

    c.stack = c.stack->next;
  }

  virtual StackElement* top() {
    return c.stack;
  }

  virtual unsigned footprint(StackElement* e) {
    return (static_cast<Stack*>(e)->next
            and (static_cast<Stack*>(e)->next->value
                 == static_cast<Stack*>(e)->value->high)) ? 2 : 1;
  }

  virtual unsigned index(StackElement* e) {
    return static_cast<Stack*>(e)->index;
  }

  virtual Operand* peek(unsigned footprint, unsigned index) {
    Stack* s = c.stack;
    for (unsigned i = index; i > 0; --i) {
      s = s->next;
    }

    if (footprint > 1) {
      assert(&c, footprint == 2);

      bool bigEndian = c.arch->bigEndian();

#ifndef NDEBUG
      Stack* low;
      Stack* high;
      if (bigEndian) {
        high = s;
        low = s->next;
      } else {
        low = s;
        high = s->next;
      }

      assert(&c, low->value->high == high->value
             and ((BytesPerWord == 8) xor (low->value->high != 0)));
#endif // not NDEBUG

      if (bigEndian) {
        s = s->next;
      }
    }

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

    bool bigEndian = c.arch->bigEndian();

    unsigned footprint = 0;
    unsigned size = BytesPerWord;
    Value* arguments[argumentCount];
    int index = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Value* o = va_arg(a, Value*);
      if (o) {
        if (bigEndian and size > BytesPerWord) {
          arguments[index++] = o->high;
        }
        arguments[index] = o;
        if ((not bigEndian) and size > BytesPerWord) {
          arguments[++index] = o->high;
        }
        size = BytesPerWord;
        ++ index;
      } else {
        size = 8;
      }
      ++ footprint;
    }

    va_end(a);

    Stack* argumentStack = c.stack;
    for (int i = index - 1; i >= 0; --i) {
      argumentStack = ::stack(&c, arguments[i], argumentStack);
    }

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
    assert(&c, index + footprint <= c.localFootprint);

    Value* v = value(&c);

    if (footprint > 1) {
      assert(&c, footprint == 2);

      unsigned highIndex;
      unsigned lowIndex;
      if (c.arch->bigEndian()) {
        highIndex = index + 1;
        lowIndex = index;
      } else {
        lowIndex = index + 1;
        highIndex = index;      
      }

      if (BytesPerWord == 4) {
        initLocal(1, highIndex);
        v->high = c.locals[highIndex].value;
      }

      index = lowIndex;
    }

    if (DebugFrame) {
      fprintf(stderr, "init local %p at %d (%d)\n",
              v, index, frameIndex(&c, index));
    }

    appendFrameSite(&c, v, frameIndex(&c, index));

    Local* local = c.locals + index;
    local->value = v;
    v->home = frameIndex(&c, index);
  }

  virtual void initLocalsFromLogicalIp(unsigned logicalIp) {
    assert(&c, logicalIp < c.logicalCodeLength);

    unsigned footprint = sizeof(Local) * c.localFootprint;
    Local* newLocals = static_cast<Local*>(c.zone->allocate(footprint));
    memset(newLocals, 0, footprint);
    c.locals = newLocals;

    Event* e = c.logicalCode[logicalIp]->firstEvent;
    for (int i = 0; i < static_cast<int>(c.localFootprint); ++i) {
      Local* local = e->localsBefore + i;
      if (local->value) {
        initLocal(1, i);
      }
    }

    for (int i = 0; i < static_cast<int>(c.localFootprint); ++i) {
      Local* local = e->localsBefore + i;
      if (local->value) {
        int highOffset = c.arch->bigEndian() ? 1 : -1;

        if (i + highOffset >= 0
            and i + highOffset < static_cast<int>(c.localFootprint)
            and local->value->high == local[highOffset].value)
        {
          c.locals[i].value->high = c.locals[i + highOffset].value;
        }
      }
    }
  }

  virtual void storeLocal(unsigned footprint, Operand* src, unsigned index) {
    ::storeLocal(&c, footprint, static_cast<Value*>(src), index, true);
  }

  virtual Operand* loadLocal(unsigned footprint, unsigned index) {
    return ::loadLocal(&c, footprint, index);
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

  virtual void store(unsigned srcSize, Operand* src, unsigned dstSize,
                     Operand* dst)
  {
    appendMove(&c, Move, srcSize, srcSize, static_cast<Value*>(src),
               dstSize, static_cast<Value*>(dst));
  }

  virtual Operand* load(unsigned srcSize, unsigned srcSelectSize, Operand* src,
                        unsigned dstSize)
  {
    assert(&c, dstSize >= BytesPerWord);

    Value* dst = value(&c);
    appendMove(&c, Move, srcSize, srcSelectSize, static_cast<Value*>(src),
               dstSize, dst);
    return dst;
  }

  virtual Operand* loadz(unsigned srcSize, unsigned srcSelectSize,
                         Operand* src, unsigned dstSize)
  {
    assert(&c, dstSize >= BytesPerWord);

    Value* dst = value(&c);
    appendMove(&c, MoveZ, srcSize, srcSelectSize, static_cast<Value*>(src),
               dstSize, dst);
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

  virtual void loadBarrier() {
    appendBarrier(&c, LoadBarrier);
  }

  virtual void storeStoreBarrier() {
    appendBarrier(&c, StoreStoreBarrier);
  }

  virtual void storeLoadBarrier() {
    appendBarrier(&c, StoreLoadBarrier);
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
      intptr_t* target = reinterpret_cast<intptr_t*>
        (dst + pad(c.machineCodeSize) + i);

      if (n->promise->resolved()) {
        *target = n->promise->value();
      } else {
        class Listener: public Promise::Listener {
         public:
          Listener(intptr_t* target): target(target){ }

          virtual bool resolve(int64_t value, void** location) {
            *target = value;
            if (location) *location = target;
            return true;
          }

          intptr_t* target;
        };
        new (n->promise->listen(sizeof(Listener))) Listener(target);
      }

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
