/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include <avian/util/runtime-array.h>

#include <avian/codegen/compiler.h>
#include <avian/codegen/assembler.h>
#include <avian/codegen/architecture.h>
#include <avian/codegen/promise.h>

#include "codegen/compiler/regalloc.h"
#include "codegen/compiler/context.h"
#include "codegen/compiler/resource.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/read.h"
#include "codegen/compiler/event.h"
#include "codegen/compiler/promise.h"
#include "codegen/compiler/frame.h"
#include "codegen/compiler/ir.h"

using namespace vm;

namespace avian {
namespace codegen {
namespace compiler {

const bool DebugAppend = false;
const bool DebugCompile = false;
const bool DebugResources = false;
const bool DebugFrame = false;
const bool DebugControl = false;
const bool DebugBuddies = false;

const unsigned StealRegisterReserveCount = 2;

// this should be equal to the largest number of registers used by a
// compare instruction:
const unsigned ResolveRegisterReserveCount = (TargetBytesPerWord == 8 ? 2 : 4);

void
apply(Context* c, lir::UnaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High);

void
apply(Context* c, lir::BinaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High);

void
apply(Context* c, lir::TernaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High,
      unsigned s3Size, Site* s3Low, Site* s3High);


class ConstantPoolNode {
 public:
  ConstantPoolNode(Promise* promise): promise(promise), next(0) { }

  Promise* promise;
  ConstantPoolNode* next;
};

Read*
live(Context* c UNUSED, Value* v)
{
  assert(c, v->buddy->hasBuddy(c, v));

  Value* p = v;
  do {
    if (valid(p->reads)) {
      return p->reads;
    }
    p = p->buddy;
  } while (p != v);

  return 0;
}

unsigned
sitesToString(Context* c, Value* v, char* buffer, unsigned size);

void
deadWord(Context* c, Value* v)
{
  Value* nextWord = v->nextWord;
  assert(c, nextWord != v);

  for (SiteIterator it(c, v, true, false); it.hasMore();) {
    Site* s = it.next();
    
    if (s->registerSize(c) > TargetBytesPerWord) {
      it.remove(c);
      nextWord->addSite(c, s);
    }
  }
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

  assert(c, v->buddy);

  Value* next = v->buddy;
  v->buddy = v;
  Value* p = next;
  while (p->buddy != v) p = p->buddy;
  p->buddy = next;

  assert(c, p->buddy);

  for (SiteIterator it(c, v, false, false); it.hasMore();) {
    Site* s = it.next();
    it.remove(c);
    
    next->addSite(c, s);
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
    Value* nextWord = v->nextWord;
    if (nextWord != v) {
      if (valid(nextWord->reads)) {
        deadWord(c, v);
      } else {
        deadWord(c, nextWord);        
      }
    }

    Read* r = live(c, v);
    if (r) {
      deadBuddy(c, v, r);
    } else {
      v->clearSites(c);
    }
  }
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

lir::ValueType
valueType(Context* c, Compiler::OperandType type)
{
  switch (type) {
  case Compiler::ObjectType:
  case Compiler::AddressType:
  case Compiler::IntegerType:
  case Compiler::VoidType:
    return lir::ValueGeneral;
  case Compiler::FloatType:
    return lir::ValueFloat;
  default:
    abort(c);
  }
}

void
move(Context* c, Value* value, Site* src, Site* dst);

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
      total += vm::snprintf(buffer + total, size - total, "%p has ", p);
      total += sitesToString(c, p->sites, buffer + total, size - total);
    } else {
      total += vm::snprintf(buffer + total, size - total, "%p has nothing", p);
    }

    p = p->buddy;
  } while (p != v);

  return total;
}

Site*
pickTargetSite(Context* c, Read* read, bool intersectRead = false,
               unsigned registerReserveCount = 0,
               CostCalculator* costCalculator = 0)
{
  Target target
    (pickTarget
     (c, read, intersectRead, registerReserveCount, costCalculator));

  expect(c, target.cost < Target::Impossible);

  if (target.type == lir::MemoryOperand) {
    return frameSite(c, target.index);
  } else {
    return registerSite(c, target.index);
  }
}

bool
acceptMatch(Context* c, Site* s, Read*, const SiteMask& mask)
{
  return s->match(c, mask);
}

Site*
pickSourceSite(Context* c, Read* read, Site* target = 0,
               unsigned* cost = 0, SiteMask* extraMask = 0,
               bool intersectRead = true, bool includeBuddies = true,
               bool includeNextWord = true,
               bool (*accept)(Context*, Site*, Read*, const SiteMask&)
               = acceptMatch)
{
  SiteMask mask;

  if (extraMask) {
    mask = mask.intersectionWith(*extraMask);
  }

  if (intersectRead) {
    read->intersect(&mask);
  }

  Site* site = 0;
  unsigned copyCost = 0xFFFFFFFF;
  for (SiteIterator it(c, read->value, includeBuddies, includeNextWord);
       it.hasMore();)
  {
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
maybeMove(Context* c, Read* read, bool intersectRead, bool includeNextWord,
          unsigned registerReserveCount = 0)
{
  Value* value = read->value;
  unsigned size = value == value->nextWord ? TargetBytesPerWord : 8;

  class MyCostCalculator: public CostCalculator {
   public:
    MyCostCalculator(Value* value, unsigned size, bool includeNextWord):
      value(value),
      size(size),
      includeNextWord(includeNextWord)
    { }

    virtual unsigned cost(Context* c, SiteMask dstMask)
    {
      OperandMask src;
      OperandMask tmp;
      c->arch->planMove
        (size, src, tmp,
         OperandMask(dstMask.typeMask, dstMask.registerMask));

      SiteMask srcMask = SiteMask::lowPart(src);
      for (SiteIterator it(c, value, true, includeNextWord); it.hasMore();) {
        Site* s = it.next();
        if (s->match(c, srcMask) or s->match(c, dstMask)) {
          return 0;
        }
      }

      return Target::IndirectMovePenalty;
    }

    Value* value;
    unsigned size;
    bool includeNextWord;
  } costCalculator(value, size, includeNextWord);

  Site* dstSite = pickTargetSite
    (c, read, intersectRead, registerReserveCount, &costCalculator);

  OperandMask src;
  OperandMask tmp;
  c->arch->planMove
    (size, src, tmp,
     OperandMask(1 << dstSite->type(c), dstSite->registerMask(c)));

  SiteMask srcMask = SiteMask::lowPart(src);
  unsigned cost = 0xFFFFFFFF;
  Site* srcSite = 0;
  for (SiteIterator it(c, value, true, includeNextWord); it.hasMore();) {
    Site* s = it.next();
    unsigned v = s->copyCost(c, dstSite);
    if (v == 0) {
      srcSite = s;
      cost = 0;
      break;
    }
    if (not s->match(c, srcMask)) {
      v += CopyPenalty;
    }
    if (v < cost) {
      srcSite = s;
      cost = v;
    }
  }
 
  if (cost) {
    if (DebugMoves) {
      char srcb[256]; srcSite->toString(c, srcb, 256);
      char dstb[256]; dstSite->toString(c, dstb, 256);
      fprintf(stderr, "maybe move %s to %s for %p to %p\n",
              srcb, dstb, value, value);
    }

    srcSite->freeze(c, value);

    value->addSite(c, dstSite);
    
    srcSite->thaw(c, value);    

    if (not srcSite->match(c, srcMask)) {
      srcSite->freeze(c, value);
      dstSite->freeze(c, value);

      SiteMask tmpMask = SiteMask::lowPart(tmp);
      SingleRead tmpRead(tmpMask, 0);
      tmpRead.value = value;
      tmpRead.successor_ = value;

      Site* tmpSite = pickTargetSite(c, &tmpRead, true);

      value->addSite(c, tmpSite);

      move(c, value, srcSite, tmpSite);
      
      dstSite->thaw(c, value);
      srcSite->thaw(c, value);

      srcSite = tmpSite;
    }

    move(c, value, srcSite, dstSite);
  }

  return dstSite;
}

Site*
maybeMove(Context* c, Value* v, const SiteMask& mask, bool intersectMask,
          bool includeNextWord, unsigned registerReserveCount = 0)
{
  SingleRead read(mask, 0);
  read.value = v;
  read.successor_ = v;

  return maybeMove
    (c, &read, intersectMask, includeNextWord, registerReserveCount);
}

Site*
pickSiteOrMove(Context* c, Read* read, bool intersectRead,
               bool includeNextWord, unsigned registerReserveCount = 0)
{
  Site* s = pickSourceSite
    (c, read, 0, 0, 0, intersectRead, true, includeNextWord);
  
  if (s) {
    return s;
  } else {
    return maybeMove
      (c, read, intersectRead, includeNextWord, registerReserveCount);
  }
}

Site*
pickSiteOrMove(Context* c, Value* v, const SiteMask& mask, bool intersectMask,
               bool includeNextWord, unsigned registerReserveCount = 0)
{
  SingleRead read(mask, 0);
  read.value = v;
  read.successor_ = v;

  return pickSiteOrMove
    (c, &read, intersectMask, includeNextWord, registerReserveCount);
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

  if ((not (thief and thief->isBuddyOf(r->value))
       and r->value->uniqueSite(c, r->site)))
  {
    r->site->freeze(c, r->value);

    maybeMove(c, live(c, r->value), false, true, StealRegisterReserveCount);

    r->site->thaw(c, r->value);
  }

  r->value->removeSite(c, r->site);
}

SiteMask
generalRegisterMask(Context* c)
{
  return SiteMask
    (1 << lir::RegisterOperand, c->regFile->generalRegisters.mask, NoFrameIndex);
}

SiteMask
generalRegisterOrConstantMask(Context* c)
{
  return SiteMask
    ((1 << lir::RegisterOperand) | (1 << lir::ConstantOperand),
     c->regFile->generalRegisters.mask, NoFrameIndex);
}

MultiRead*
multiRead(Context* c)
{
  return new(c->zone) MultiRead;
}

StubRead*
stubRead(Context* c)
{
  return new(c->zone) StubRead;
}

Site*
pickSite(Context* c, Value* v, Site* s, unsigned index, bool includeNextWord)
{
  for (SiteIterator it(c, v, true, includeNextWord); it.hasMore();) {
    Site* candidate = it.next();
    if (s->matchNextWord(c, candidate, index)) {
      return candidate;
    }
  }

  return 0;
}

Site*
pickSiteOrMove(Context* c, Value* v, Site* s, unsigned index)
{
  Site* n = pickSite(c, v, s, index, false);
  if (n) {
    return n;
  }

  return maybeMove(c, v, s->nextWordMask(c, index), true, false);
}

Site*
pickSiteOrMove(Context* c, Value* v, Site* s, Site** low, Site** high)
{
  if (v->wordIndex == 0) {
    *low = s;
    *high = pickSiteOrMove(c, v->nextWord, s, 1);
    return *high;
  } else {
    *low = pickSiteOrMove(c, v->nextWord, s, 0);
    *high = s;
    return *low;
  }
}

Site*
pickSiteOrGrow(Context* c, Value* v, Site* s, unsigned index)
{
  Site* n = pickSite(c, v, s, index, false);
  if (n) {
    return n;
  }

  n = s->makeNextWord(c, index);
  v->addSite(c, n);
  return n;
}

Site*
pickSiteOrGrow(Context* c, Value* v, Site* s, Site** low, Site** high)
{
  if (v->wordIndex == 0) {
    *low = s;
    *high = pickSiteOrGrow(c, v->nextWord, s, 1);
    return *high;
  } else {
    *low = pickSiteOrGrow(c, v->nextWord, s, 0);
    *high = s;
    return *low;
  }
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
    if (s->type(c) == lir::RegisterOperand) {
      return c->availableGeneralRegisterCount > ResolveRegisterReserveCount;
    } else {
      assert(c, s->match(c, SiteMask(1 << lir::MemoryOperand, 0, AnyFrameIndex)));

      return isHome(read->value, offsetToFrameIndex
                    (c, static_cast<MemorySite*>(s)->offset));
    }
  } else {
    return false;
  }
}

void
move(Context* c, Value* value, Site* src, Site* dst)
{
  if (DebugMoves) {
    char srcb[256]; src->toString(c, srcb, 256);
    char dstb[256]; dst->toString(c, dstb, 256);
    fprintf(stderr, "move %s to %s for %p to %p\n",
            srcb, dstb, value, value);
  }

  assert(c, value->findSite(dst));

  src->freeze(c, value);
  dst->freeze(c, value);
  
  unsigned srcSize;
  unsigned dstSize;
  if (value->nextWord == value) {
    srcSize = TargetBytesPerWord;
    dstSize = TargetBytesPerWord;
  } else {
    srcSize = src->registerSize(c);
    dstSize = dst->registerSize(c);
  }

  if (srcSize == dstSize) {
    apply(c, lir::Move, srcSize, src, src, dstSize, dst, dst);
  } else if (srcSize > TargetBytesPerWord) {
    Site* low, *high, *other = pickSiteOrGrow(c, value, dst, &low, &high);
    other->freeze(c, value->nextWord);

    apply(c, lir::Move, srcSize, src, src, srcSize, low, high);

    other->thaw(c, value->nextWord);
  } else {
    Site* low, *high, *other = pickSiteOrMove(c, value, src, &low, &high);
    other->freeze(c, value->nextWord);

    apply(c, lir::Move, dstSize, low, high, dstSize, dst, dst);

    other->thaw(c, value->nextWord);
  }

  dst->thaw(c, value);
  src->thaw(c, value);
}

void
asAssemblerOperand(Context* c, Site* low, Site* high,
                   lir::Operand* result)
{
  low->asAssemblerOperand(c, high, result);
}

class OperandUnion: public lir::Operand {
 public:
  // must be large enough and aligned properly to hold any operand
  // type (we'd use an actual union type here, except that classes
  // with constructors cannot be used in a union):
  uintptr_t padding[4];
};

void
apply(Context* c, lir::UnaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High)
{
  assert(c, s1Low->type(c) == s1High->type(c));

  lir::OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  c->assembler->apply(op,
    OperandInfo(s1Size, s1Type, &s1Union));
}

void
apply(Context* c, lir::BinaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High)
{
  assert(c, s1Low->type(c) == s1High->type(c));
  assert(c, s2Low->type(c) == s2High->type(c));

  lir::OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  lir::OperandType s2Type = s2Low->type(c);
  OperandUnion s2Union; asAssemblerOperand(c, s2Low, s2High, &s2Union);

  c->assembler->apply(op,
    OperandInfo(s1Size, s1Type, &s1Union),
    OperandInfo(s2Size, s2Type, &s2Union));
}

void
apply(Context* c, lir::TernaryOperation op,
      unsigned s1Size, Site* s1Low, Site* s1High,
      unsigned s2Size, Site* s2Low, Site* s2High,
      unsigned s3Size, Site* s3Low, Site* s3High)
{
  assert(c, s1Low->type(c) == s1High->type(c));
  assert(c, s2Low->type(c) == s2High->type(c));
  assert(c, s3Low->type(c) == s3High->type(c));

  lir::OperandType s1Type = s1Low->type(c);
  OperandUnion s1Union; asAssemblerOperand(c, s1Low, s1High, &s1Union);

  lir::OperandType s2Type = s2Low->type(c);
  OperandUnion s2Union; asAssemblerOperand(c, s2Low, s2High, &s2Union);

  lir::OperandType s3Type = s3Low->type(c);
  OperandUnion s3Union; asAssemblerOperand(c, s3Low, s3High, &s3Union);

  c->assembler->apply(op,
    OperandInfo(s1Size, s1Type, &s1Union),
    OperandInfo(s2Size, s2Type, &s2Union),
    OperandInfo(s3Size, s3Type, &s3Union));
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
                local->value, compiler::frameIndex(c, li), totalFrameSize(c));
      }

      e->addRead(c, local->value, SiteMask
              (1 << lir::MemoryOperand, 0, compiler::frameIndex(c, li)));
    }
  }
}

void
maybeMove(Context* c, lir::BinaryOperation type, unsigned srcSize,
          unsigned srcSelectSize, Value* srcValue, unsigned dstSize, Value* dstValue,
          const SiteMask& dstMask)
{
  Read* read = live(c, dstValue);
  bool isStore = read == 0;

  Site* target;
  if (dstValue->target) {
    target = dstValue->target;
  } else if (isStore) {
    return;
  } else {
    target = pickTargetSite(c, read);
  }

  unsigned cost = srcValue->source->copyCost(c, target);

  if (srcSelectSize < dstSize) cost = 1;

  if (cost) {
    // todo: let c->arch->planMove decide this:
    bool useTemporary = ((target->type(c) == lir::MemoryOperand
                          and srcValue->source->type(c) == lir::MemoryOperand)
                         or (srcSelectSize < dstSize
                             and target->type(c) != lir::RegisterOperand));

    srcValue->source->freeze(c, srcValue);

    dstValue->addSite(c, target);

    srcValue->source->thaw(c, srcValue);

    bool addOffset = srcSize != srcSelectSize
      and c->arch->bigEndian()
      and srcValue->source->type(c) == lir::MemoryOperand;

    if (addOffset) {
      static_cast<MemorySite*>(srcValue->source)->offset
        += (srcSize - srcSelectSize);
    }

    target->freeze(c, dstValue);

    if (target->match(c, dstMask) and not useTemporary) {
      if (DebugMoves) {
        char srcb[256]; srcValue->source->toString(c, srcb, 256);
        char dstb[256]; target->toString(c, dstb, 256);
        fprintf(stderr, "move %s to %s for %p to %p\n",
                srcb, dstb, srcValue, dstValue);
      }

      srcValue->source->freeze(c, srcValue);

      apply(c, type, min(srcSelectSize, dstSize), srcValue->source, srcValue->source,
            dstSize, target, target);

      srcValue->source->thaw(c, srcValue);
    } else {
      // pick a temporary register which is valid as both a
      // destination and a source for the moves we need to perform:
      
      dstValue->removeSite(c, target);

      bool thunk;
      OperandMask src;

      c->arch->planSource(type, dstSize, src, dstSize, &thunk);

      if (srcValue->type == lir::ValueGeneral) {
        src.registerMask &= c->regFile->generalRegisters.mask;
      }

      assert(c, thunk == 0);
      assert(c, dstMask.typeMask & src.typeMask & (1 << lir::RegisterOperand));

      Site* tmpTarget = freeRegisterSite
        (c, dstMask.registerMask & src.registerMask);

      srcValue->source->freeze(c, srcValue);

      dstValue->addSite(c, tmpTarget);

      tmpTarget->freeze(c, dstValue);

      if (DebugMoves) {
        char srcb[256]; srcValue->source->toString(c, srcb, 256);
        char dstb[256]; tmpTarget->toString(c, dstb, 256);
        fprintf(stderr, "move %s to %s for %p to %p\n",
                srcb, dstb, srcValue, dstValue);
      }

      apply(c, type, srcSelectSize, srcValue->source, srcValue->source,
            dstSize, tmpTarget, tmpTarget);

      tmpTarget->thaw(c, dstValue);

      srcValue->source->thaw(c, srcValue);

      if (useTemporary or isStore) {
        if (DebugMoves) {
          char srcb[256]; tmpTarget->toString(c, srcb, 256);
          char dstb[256]; target->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p to %p\n",
                  srcb, dstb, srcValue, dstValue);
        }

        dstValue->addSite(c, target);

        tmpTarget->freeze(c, dstValue);

        apply(c, lir::Move, dstSize, tmpTarget, tmpTarget, dstSize, target, target);

        tmpTarget->thaw(c, dstValue);

        if (isStore) {
          dstValue->removeSite(c, tmpTarget);
        }
      }
    }

    target->thaw(c, dstValue);

    if (addOffset) {
      static_cast<MemorySite*>(srcValue->source)->offset
        -= (srcSize - srcSelectSize);
    }
  } else {
    target = srcValue->source;

    if (DebugMoves) {
      char dstb[256]; target->toString(c, dstb, 256);
      fprintf(stderr, "null move in %s for %p to %p\n", dstb, srcValue, dstValue);
    }
  }

  if (isStore) {
    dstValue->removeSite(c, target);
  }
}

Site*
pickMatchOrMove(Context* c, Read* r, Site* nextWord, unsigned index,
                bool intersectRead)
{
  Site* s = pickSite(c, r->value, nextWord, index, true);
  SiteMask mask;
  if (intersectRead) {
    r->intersect(&mask);
  }
  if (s and s->match(c, mask)) {
    return s;
  }

  return pickSiteOrMove
    (c, r->value, mask.intersectionWith(nextWord->nextWordMask(c, index)),
     true, true);
}

Site*
pickSiteOrMove(Context* c, Value* src, Value* dst, Site* nextWord,
               unsigned index)
{
  if (live(c, dst)) {
    Read* read = live(c, src);
    Site* s;
    if (nextWord) {
      s = pickMatchOrMove(c, read, nextWord, index, false);
    } else {
      s = pickSourceSite(c, read, 0, 0, 0, false, true, true);

      if (s == 0 or s->isVolatile(c)) {
        s = maybeMove(c, read, false, true);
      }
    }
    assert(c, s);

    addBuddy(src, dst);

    if (src->source->isVolatile(c)) {
      src->removeSite(c, src->source);
    }

    return s;
  } else {
    return 0;
  }
}

ConstantSite*
findConstantSite(Context* c, Value* v);

Site*
getTarget(Context* c, Value* value, Value* result, const SiteMask& resultMask);

void
freezeSource(Context* c, unsigned size, Value* v);

void
thawSource(Context* c, unsigned size, Value* v);

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

    assert(c, v->buddy);

    Value* next = v->buddy;
    v->buddy = v;
    Value* p = next;
    while (p->buddy != v) p = p->buddy;
    p->buddy = next;

    assert(c, p->buddy);

    if (not live(c, next)) {
      next->clearSites(c);
    }

    if (not live(c, v)) {
      v->clearSites(c);
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

  return new(c->zone) Snapshot(c, value, next);
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

void
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

    if (TargetBytesPerWord == 4) {
      low->maybeSplit(c);
      high = pushWord(c, low->nextWord);
    } else {
      high = pushWord(c, 0);
    }
  } else {
    high = 0;
  }
  
  if (not bigEndian) {
    v = pushWord(c, v);
  }

  if (high) {
    v->nextWord = high;
    high->nextWord = v;
    high->wordIndex = 1;
  }
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

    assert(c, (TargetBytesPerWord == 8
               and low->value->nextWord == low->value and high->value == 0)
           or (TargetBytesPerWord == 4 and low->value->nextWord == high->value));
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

    if (TargetBytesPerWord == 4) {
      assert(c, v->nextWord != v);

      high = storeLocal(c, 1, v->nextWord, highIndex, false);
    } else {
      high = 0;
    }

    index = lowIndex;
  } else {
    high = 0;
  }

  v = maybeBuddy(c, v);

  if (high != 0) {
    v->nextWord = high;
    high->nextWord = v;
    high->wordIndex = 1;
  }

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

Value*
register_(Context* c, int number)
{
  assert(c, (1 << number) & (c->regFile->generalRegisters.mask
                             | c->regFile->floatRegisters.mask));

  Site* s = registerSite(c, number);
  lir::ValueType type = ((1 << number) & c->regFile->floatRegisters.mask)
    ? lir::ValueFloat: lir::ValueGeneral;

  return value(c, type, s, s);
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
      if (not live(c, v)) {
        v->clearSites(c);
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
    this->addRead(c, original, SiteMask(~0, ~0, AnyFrameIndex), buddy);
  }

  virtual const char* name() {
    return "BuddyEvent";
  }

  virtual void compile(Context* c) {
    if (DebugBuddies) {
      fprintf(stderr, "original %p buddy %p\n", original, buddy);
    }

    assert(c, original->hasSite(c));

    assert(c, original);
    assert(c, buddy);

    addBuddy(original, buddy);

    popRead(c, this, original);
  }

  Value* original;
  Value* buddy;
};

void
appendBuddy(Context* c, Value* original, Value* buddy)
{
  append(c, new(c->zone) BuddyEvent(c, original, buddy));
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

    Link* link = compiler::link
      (c, p, e->predecessors, e, p->successors, c->forkState);
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
readSource(Context* c, Read* r)
{
  Value* v = r->value;

  if (DebugReads) {
    char buffer[1024]; sitesToString(c, v, buffer, 1024);
    fprintf(stderr, "read source for %p from %s\n", v, buffer);
  }

  if (not v->hasSite(c)) {
    if (DebugReads) {
      fprintf(stderr, "no sites found for %p\n", v);
    }
    return 0;
  }

  Value* high = r->high(c);
  if (high) {
    return pickMatchOrMove(c, r, high->source, 0, true);
  } else {
    return pickSiteOrMove(c, r, true, true);
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
  Site* site;
  Value* value;
};

void
init(SiteRecord* r, Site* s, Value* v)
{
  r->site = s;
  r->value = v;
}

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
  init(new (frozen->records + (frozen->index ++)) SiteRecord, s, v);
}

void
thaw(Context* c, SiteRecordList* frozen)
{
  while (frozen->index) {
    SiteRecord* sr = frozen->records + (-- frozen->index);
    sr->site->thaw(c, sr->value);
  }
}

bool
resolveOriginalSites(Context* c, Event* e, SiteRecordList* frozen,
                     Site** sites)
{
  bool complete = true;
  for (FrameIterator it(c, e->stackAfter, e->localsAfter, true);
       it.hasMore();)
  {
    FrameIterator::Element el = it.next(c);
    Value* v = el.value;
    Read* r = v ? live(c, v) : 0;
    Site* s = sites[el.localIndex];

    if (r) {
      if (s) {
        if (DebugControl) {
          char buffer[256];
          s->toString(c, buffer, 256);
          fprintf(stderr, "resolve original %s for %p local %d frame %d\n",
                  buffer, v, el.localIndex, el.frameIndex(c));
        }

        Site* target = pickSiteOrMove
          (c, v, s->mask(c), true, true, ResolveRegisterReserveCount);

        freeze(c, frozen, target, v);
      } else {
        complete = false;
      }
    } else if (s) {
      if (DebugControl) {
        char buffer[256];
        s->toString(c, buffer, 256);
        fprintf(stderr, "freeze original %s for %p local %d frame %d\n",
                buffer, v, el.localIndex, el.frameIndex(c));
      }
      
      Value dummy(0, 0, lir::ValueGeneral);
      dummy.addSite(c, s);
      dummy.removeSite(c, s);
      freeze(c, frozen, s, 0);
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
    Read* r = live(c, v);

    if (r and sites[el.localIndex] == 0) {
      SiteMask mask((1 << lir::RegisterOperand) | (1 << lir::MemoryOperand),
                    c->regFile->generalRegisters.mask, AnyFrameIndex);

      Site* s = pickSourceSite
        (c, r, 0, 0, &mask, true, false, true, acceptForResolve);

      if (s) {
        if (DebugControl) {
          char buffer[256]; s->toString(c, buffer, 256);
          fprintf(stderr, "resolve source %s from %p local %d frame %d\n",
                  buffer, v, el.localIndex, el.frameIndex(c));
        }

        freeze(c, frozen, s, v);

        sites[el.localIndex] = s->copy(c);
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
    Read* r = live(c, v);

    if (r and sites[el.localIndex] == 0) {
      SiteMask mask((1 << lir::RegisterOperand) | (1 << lir::MemoryOperand),
                    c->regFile->generalRegisters.mask, AnyFrameIndex);

      Site* s = pickSourceSite
        (c, r, 0, 0, &mask, false, true, true, acceptForResolve);

      if (s == 0) {
        s = maybeMove(c, v, mask, false, true, ResolveRegisterReserveCount);
      }

      freeze(c, frozen, s, v);

      sites[el.localIndex] = s->copy(c);

      if (DebugControl) {
        char buffer[256]; sites[el.localIndex]->toString(c, buffer, 256);
        fprintf(stderr, "resolve target %s for %p local %d frame %d\n",
                buffer, el.value, el.localIndex, el.frameIndex(c));
      }
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
    RUNTIME_ARRAY(Site*, branchSites, footprint);
    memset(RUNTIME_ARRAY_BODY(branchSites), 0, sizeof(Site*) * footprint);

    if (not resolveSourceSites(c, e, frozen, RUNTIME_ARRAY_BODY(branchSites)))
    {
      resolveTargetSites(c, e, frozen, RUNTIME_ARRAY_BODY(branchSites));
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

    for (List<Value*>* sv = e->successors->forkState->saved; sv; sv = sv->next) {
      e->snapshots = makeSnapshots(c, sv->item, e->snapshots);
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
}

void
setSites(Context* c, Value* v, Site* s)
{
  assert(c, live(c, v));

  for (; s; s = s->next) {
    v->addSite(c, s->copy(c));
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
    el.value->clearSites(c);
  }

  while (c->acquiredResources) {
    c->acquiredResources->value->clearSites(c);
  }
}

void
setSites(Context* c, Event* e, Site** sites)
{
  resetFrame(c, e);

  for (FrameIterator it(c, e->stackBefore, e->localsBefore); it.hasMore();) {
    FrameIterator::Element el = it.next(c);
    if (sites[el.localIndex]) {
      if (live(c, el.value)) {
        setSites(c, el.value, sites[el.localIndex]);
      } else if (DebugControl) {
        char buffer[256]; sitesToString(c, sites[el.localIndex], buffer, 256);
        fprintf(stderr, "skip sites %s for %p local %d frame %d\n",
                buffer, el.value, el.localIndex, el.frameIndex(c));
      }
    } else if (DebugControl) {
      fprintf(stderr, "no sites for %p local %d frame %d\n",
              el.value, el.localIndex, el.frameIndex(c));
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
    Value* v = s->value;
    Value* next = v->buddy;
    if (v != next) {
      v->buddy = v;
      Value* p = next;
      while (p->buddy != v) p = p->buddy;
      p->buddy = next;
    }
  }

  for (Snapshot* s = snapshots; s; s = s->next) {
    assert(c, s->buddy);

    s->value->buddy = s->buddy;
  }

  resetFrame(c, e);

  for (Snapshot* s = snapshots; s; s = s->next) {
    if (live(c, s->value)) {
      if (live(c, s->value) and s->sites and s->value->sites == 0) {
        setSites(c, s->value, s->sites);
      }
    }

    // char buffer[256]; sitesToString(c, s->sites, buffer, 256);
    // fprintf(stderr, "restore %p buddy %p sites %s live %p\n",
    //         s->value, s->value->buddy, buffer, live(c, s->value));
  }
}

void
populateSources(Context* c, Event* e)
{
  RUNTIME_ARRAY(SiteRecord, frozenRecords, e->readCount);
  SiteRecordList frozen(RUNTIME_ARRAY_BODY(frozenRecords), e->readCount);

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
    // TODO: this is rather icky looking... but despite how it looks, it will not cause an NPE
    ((Event*)0)->addRead(c, v, r);

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
      Read* r = live(c, e.value);
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

void
compile(Context* c, uintptr_t stackOverflowHandler, unsigned stackLimitOffset)
{
  if (c->logicalCode[c->logicalIp]->lastEvent == 0) {
    appendDummy(c);
  }

  Assembler* a = c->assembler;

  Block* firstBlock = block(c, c->firstEvent);
  Block* block = firstBlock;

  if (stackOverflowHandler) {
    a->checkStackOverflow(stackOverflowHandler, stackLimitOffset);
  }

  a->allocateFrame(c->alignedFrameSize);

  for (Event* e = c->firstEvent; e; e = e->next) {
    if (DebugCompile) {
      fprintf(stderr,
              " -- compile %s at %d with %d preds %d succs %d stack\n",
              e->name(), e->logicalInstruction->index,
              e->predecessors->countPredecessors(),
              e->successors->countSuccessors(),
              e->stackBefore ? e->stackBefore->index + 1 : 0);
    }

    e->block = block;

    c->stack = e->stackBefore;
    c->locals = e->localsBefore;

    if (e->logicalInstruction->machineOffset == 0) {
      e->logicalInstruction->machineOffset = a->offset();
    }

    if (e->predecessors) {
      visit(c, e->predecessors->lastPredecessor());

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
    RUNTIME_ARRAY(SiteRecord, frozenRecords, footprint);
    SiteRecordList frozen(RUNTIME_ARRAY_BODY(frozenRecords), footprint);

    bool branch = e->isBranch();
    if (branch and e->successors) {
      populateSiteTables(c, e, &frozen);
    }

    populateSources(c, e);

    if (branch and e->successors) {
      captureBranchSnapshots(c, e);
    }

    thaw(c, &frozen);

    e->compile(c);

    if ((not branch) and e->successors) {
      populateSiteTables(c, e, &frozen);
      captureBranchSnapshots(c, e);
      thaw(c, &frozen);
    }

    if (e->visitLinks) {
      for (List<Link*>* cell = reverseDestroy(e->visitLinks); cell; cell = cell->next) {
        visit(c, cell->item);
      }
      e->visitLinks = 0;
    }

    for (CodePromise* p = e->promises; p; p = p->next) {
      p->offset = a->offset();
    }
    
    a->endEvent();

    LogicalInstruction* nextInstruction = e->logicalInstruction->next(c);
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
        block = compiler::block(c, e->next);
      }
    }
  }

  c->firstBlock = firstBlock;
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
  // TODO: this is rather icky looking... but despite how it looks, it will not cause an NPE
  ((Event*)0)->addRead(c, v, r);

  ForkElement* p = state->elements + index;
  p->value = v;
  p->read = r;
}

ForkState*
saveState(Context* c)
{
  if (c->logicalCode[c->logicalIp]->lastEvent == 0) {
    appendDummy(c);
  }

  unsigned elementCount = frameFootprint(c, c->stack) + c->saved->count();

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

    for (List<Value*>* sv = c->saved; sv; sv = sv->next) {
      addForkElement(c, sv->item, state, count++);
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
    Value* n = value(c, v->type);
    appendBuddy(c, v, n);
    return n;
  } else {
    return v;
  }
}

void
linkLocals(Context* c, Local* oldLocals, Local* newLocals)
{
  for (int i = 0; i < static_cast<int>(c->localFootprint); ++i) {
    Local* local = oldLocals + i;
    if (local->value) {
      int highOffset = c->arch->bigEndian() ? 1 : -1;

      if (i + highOffset >= 0
          and i + highOffset < static_cast<int>(c->localFootprint)
          and local->value->nextWord == local[highOffset].value)
      {
        Value* v = newLocals[i].value;
        Value* next = newLocals[i + highOffset].value;
        v->nextWord = next;
        next->nextWord = v;
        next->wordIndex = 1;
      }
    }
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
    c->registerResources[r].increment(c);
    return r;
  }

  virtual void releaseTemporary(int r) {
    c->registerResources[r].decrement(c);
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
    State* s = compiler::saveState(&c);
    restoreState(s);
    return s;
  }

  virtual void restoreState(State* state) {
    compiler::restoreState(&c, static_cast<ForkState*>(state));
  }

  virtual Subroutine* startSubroutine() {
    return c.subroutine = new(c.zone) MySubroutine;
  }

  virtual void returnFromSubroutine(Subroutine* subroutine, Operand* address) {
    appendSaveLocals(&c);
    appendJump(&c, lir::Jump, static_cast<Value*>(address), false, true);
    static_cast<MySubroutine*>(subroutine)->forkState = compiler::saveState(&c);
  }

  virtual void linkSubroutine(Subroutine* subroutine) {
    Local* oldLocals = c.locals;
    restoreState(static_cast<MySubroutine*>(subroutine)->forkState);
    linkLocals(&c, oldLocals, c.locals);
  }

  virtual void init(unsigned logicalCodeLength, unsigned parameterFootprint,
                    unsigned localFootprint, unsigned alignedFrameSize)
  {
    c.logicalCodeLength = logicalCodeLength;
    c.parameterFootprint = parameterFootprint;
    c.localFootprint = localFootprint;
    c.alignedFrameSize = alignedFrameSize;

    unsigned frameResourceCount = totalFrameSize(&c);

    c.frameResources = static_cast<FrameResource*>
      (c.zone->allocate(sizeof(FrameResource) * frameResourceCount));
    
    for (unsigned i = 0; i < frameResourceCount; ++i) {
      new (c.frameResources + i) FrameResource;
    }

    unsigned base = frameBase(&c);
    c.frameResources[base + c.arch->returnAddressOffset()].reserved = true;
    c.frameResources[base + c.arch->framePointerOffset()].reserved
      = UseFramePointer;

    // leave room for logical instruction -1
    unsigned codeSize = sizeof(LogicalInstruction*) * (logicalCodeLength + 1);
    c.logicalCode = static_cast<LogicalInstruction**>
      (c.zone->allocate(codeSize));
    memset(c.logicalCode, 0, codeSize);
    c.logicalCode++;

    c.locals = static_cast<Local*>
      (c.zone->allocate(sizeof(Local) * localFootprint));

    memset(c.locals, 0, sizeof(Local) * localFootprint);

    c.logicalCode[-1] = new(c.zone) LogicalInstruction(-1, c.stack, c.locals);
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

      Link* link = compiler::link
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

    c.logicalCode[logicalIp] = new(c.zone) LogicalInstruction(logicalIp, c.stack, c.locals);

    bool startSubroutine = c.subroutine != 0;
    if (startSubroutine) {
      c.logicalCode[logicalIp]->subroutine = c.subroutine;
      c.subroutine = 0;
    }

    c.logicalIp = logicalIp;

    if (startSubroutine) {
      // assume all local variables are initialized on entry to a
      // subroutine, since other calls to the subroutine may
      // initialize them:
      unsigned sizeInBytes = sizeof(Local) * c.localFootprint;
      Local* newLocals = static_cast<Local*>(c.zone->allocate(sizeInBytes));
      memcpy(newLocals, c.locals, sizeInBytes);
      c.locals = newLocals;

      for (unsigned li = 0; li < c.localFootprint; ++li) {
        Local* local = c.locals + li;
        if (local->value == 0) {
          initLocal(1, li, IntegerType); 
        }
      }
    }
  }

  virtual Promise* machineIp(unsigned logicalIp) {
    return ipPromise(&c, logicalIp);
  }

  virtual Promise* poolAppend(intptr_t value) {
    return poolAppendPromise(resolvedPromise(&c, value));
  }

  virtual Promise* poolAppendPromise(Promise* value) {
    Promise* p = poolPromise(&c, c.constantCount);

    ConstantPoolNode* constant = new (c.zone) ConstantPoolNode(value);

    if (c.firstConstant) {
      c.lastConstant->next = constant;
    } else {
      c.firstConstant = constant;
    }
    c.lastConstant = constant;
    ++ c.constantCount;

    return p;
  }

  virtual Operand* constant(int64_t value, Compiler::OperandType type) {
    return promiseConstant(resolvedPromise(&c, value), type);
  }

  virtual Operand* promiseConstant(Promise* value, Compiler::OperandType type) {
    return compiler::value
      (&c, valueType(&c, type), compiler::constantSite(&c, value));
  }

  virtual Operand* address(Promise* address) {
    return value(&c, lir::ValueGeneral, compiler::addressSite(&c, address));
  }

  virtual Operand* memory(Operand* base,
                          OperandType type,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1)
  {
    Value* result = value(&c, valueType(&c, type));

    appendMemory(&c, static_cast<Value*>(base), displacement,
                 static_cast<Value*>(index), scale, result);

    return result;
  }

  virtual Operand* register_(int number) {
    return compiler::register_(&c, number);
  }

  Promise* machineIp() {
    return c.logicalCode[c.logicalIp]->lastEvent->makeCodePromise(&c);
  }

  virtual void push(unsigned footprint UNUSED) {
    assert(&c, footprint == 1);

    Value* v = value(&c, lir::ValueGeneral);
    Stack* s = compiler::stack(&c, v, c.stack);

    v->home = frameIndex(&c, s->index + c.localFootprint);
    c.stack = s;
  }

  virtual void push(unsigned footprint, Operand* value) {
    compiler::push(&c, footprint, static_cast<Value*>(value));
  }

  virtual void save(unsigned footprint, Operand* value) {
    c.saved = cons(&c, static_cast<Value*>(value), c.saved);
    if (TargetBytesPerWord == 4 and footprint > 1) {
      assert(&c, footprint == 2);
      assert(&c, static_cast<Value*>(value)->nextWord);

      save(1, static_cast<Value*>(value)->nextWord);
    }
  }

  virtual Operand* pop(unsigned footprint) {
    return compiler::pop(&c, footprint);
  }

  virtual void pushed() {
    Value* v = value(&c, lir::ValueGeneral);
    appendFrameSite
      (&c, v, frameIndex
       (&c, (c.stack ? c.stack->index : 0) + c.localFootprint));

    Stack* s = compiler::stack(&c, v, c.stack);
    v->home = frameIndex(&c, s->index + c.localFootprint);
    c.stack = s;
  }

  virtual void popped(unsigned footprint) {
    for (; footprint; -- footprint) {
      assert(&c, c.stack->value == 0 or c.stack->value->home >= 0);

      if (DebugFrame) {
        fprintf(stderr, "popped %p\n", c.stack->value);
      }
      
      c.stack = c.stack->next;
    }
  }

  virtual unsigned topOfStack() {
    return c.stack->index;
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

      assert(&c, (TargetBytesPerWord == 8
                  and low->value->nextWord == low->value and high->value == 0)
             or (TargetBytesPerWord == 4
                 and low->value->nextWord == high->value));
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
                        OperandType resultType,
                        unsigned argumentCount,
                        ...)
  {
    va_list a; va_start(a, argumentCount);

    bool bigEndian = c.arch->bigEndian();

    unsigned footprint = 0;
    unsigned size = TargetBytesPerWord;
    RUNTIME_ARRAY(Value*, arguments, argumentCount);
    int index = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      Value* o = va_arg(a, Value*);
      if (o) {
        if (bigEndian and size > TargetBytesPerWord) {
          RUNTIME_ARRAY_BODY(arguments)[index++] = o->nextWord;
        }
        RUNTIME_ARRAY_BODY(arguments)[index] = o;
        if ((not bigEndian) and size > TargetBytesPerWord) {
          RUNTIME_ARRAY_BODY(arguments)[++index] = o->nextWord;
        }
        size = TargetBytesPerWord;
        ++ index;
      } else {
        size = 8;
      }
      ++ footprint;
    }

    va_end(a);

    Stack* argumentStack = c.stack;
    for (int i = index - 1; i >= 0; --i) {
      argumentStack = compiler::stack
        (&c, RUNTIME_ARRAY_BODY(arguments)[i], argumentStack);
    }

    Value* result = value(&c, valueType(&c, resultType));
    appendCall(&c, static_cast<Value*>(address), flags, traceHandler, result,
               resultSize, argumentStack, index, 0);

    return result;
  }

  virtual Operand* stackCall(Operand* address,
                             unsigned flags,
                             TraceHandler* traceHandler,
                             unsigned resultSize,
                             OperandType resultType,
                             unsigned argumentFootprint)
  {
    Value* result = value(&c, valueType(&c, resultType));
    appendCall(&c, static_cast<Value*>(address), flags, traceHandler, result,
               resultSize, c.stack, 0, argumentFootprint);
    return result;
  }

  virtual void return_(unsigned size, Operand* value) {
    appendReturn(&c, size, static_cast<Value*>(value));
  }

  virtual void initLocal(unsigned footprint, unsigned index, OperandType type)
  {
    assert(&c, index + footprint <= c.localFootprint);

    Value* v = value(&c, valueType(&c, type));

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

      if (TargetBytesPerWord == 4) {
        initLocal(1, highIndex, type);
        Value* next = c.locals[highIndex].value;
        v->nextWord = next;
        next->nextWord = v;
        next->wordIndex = 1;
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
      Local* local = e->locals() + i;
      if (local->value) {
        initLocal
          (1, i, local->value->type == lir::ValueGeneral ? IntegerType : FloatType);
      }
    }

    linkLocals(&c, e->locals(), newLocals);
  }

  virtual void storeLocal(unsigned footprint, Operand* src, unsigned index) {
    compiler::storeLocal(&c, footprint, static_cast<Value*>(src), index, true);
  }

  virtual Operand* loadLocal(unsigned footprint, unsigned index) {
    return compiler::loadLocal(&c, footprint, index);
  }

  virtual void saveLocals() {
    appendSaveLocals(&c);
  }

  virtual void checkBounds(Operand* object, unsigned lengthOffset,
                           Operand* index, intptr_t handler)
  {
    appendBoundsCheck(&c, static_cast<Value*>(object), lengthOffset,
                      static_cast<Value*>(index), handler);
  }

  virtual void store(unsigned srcSize, Operand* src, unsigned dstSize,
                     Operand* dst)
  {
    appendMove(&c, lir::Move, srcSize, srcSize, static_cast<Value*>(src),
               dstSize, static_cast<Value*>(dst));
  }

  virtual Operand* load(unsigned srcSize, unsigned srcSelectSize, Operand* src,
                        unsigned dstSize)
  {
    assert(&c, dstSize >= TargetBytesPerWord);

    Value* dst = value(&c, static_cast<Value*>(src)->type);
    appendMove(&c, lir::Move, srcSize, srcSelectSize, static_cast<Value*>(src),
               dstSize, dst);
    return dst;
  }

  virtual Operand* loadz(unsigned srcSize, unsigned srcSelectSize,
                         Operand* src, unsigned dstSize)
  {
    assert(&c, dstSize >= TargetBytesPerWord);

    Value* dst = value(&c, static_cast<Value*>(src)->type);
    appendMove(&c, lir::MoveZ, srcSize, srcSelectSize, static_cast<Value*>(src),
               dstSize, dst);
    return dst;
  }

  virtual void condJump(lir::TernaryOperation type, unsigned size, Operand* a, Operand* b,
                           Operand* address)
  {
    assert(&c,
      (isGeneralBranch(type) and isGeneralValue(a) and isGeneralValue(b))
      or (isFloatBranch(type) and isFloatValue(a) and isFloatValue(b)));

    appendBranch(&c, type, size, static_cast<Value*>(a),
                 static_cast<Value*>(b), static_cast<Value*>(address));
  }

  virtual void jmp(Operand* address) {
    appendJump(&c, lir::Jump, static_cast<Value*>(address));
  }

  virtual void exit(Operand* address) {
    appendJump(&c, lir::Jump, static_cast<Value*>(address), true);
  }

  virtual Operand* binaryOp(lir::TernaryOperation type, unsigned size, Operand* a, Operand* b) {
    assert(&c,
      (isGeneralBinaryOp(type) and isGeneralValue(a) and isGeneralValue(b))
      or (isFloatBinaryOp(type) and isFloatValue(a) and isFloatValue(b)));

    Value* result = value(&c, static_cast<Value*>(a)->type);
    
    appendCombine(&c, type, size, static_cast<Value*>(a),
                  size, static_cast<Value*>(b), size, result);
    return result;
  }

  virtual Operand* unaryOp(lir::BinaryOperation type, unsigned size, Operand* a) {
    assert(&c, (isGeneralUnaryOp(type) and isGeneralValue(a))or(
                   isFloatUnaryOp(type) and isFloatValue(a)));
    Value* result = value(&c, static_cast<Value*>(a)->type);
    appendTranslate(&c, type, size, static_cast<Value*>(a), size, result);
    return result;
  }
  
  virtual Operand* f2f(unsigned aSize, unsigned resSize, Operand* a) {
    assert(&c, static_cast<Value*>(a)->type == lir::ValueFloat);
    Value* result = value(&c, lir::ValueFloat);
    appendTranslate
      (&c, lir::Float2Float, aSize, static_cast<Value*>(a), resSize, result);
    return result;
  }
  
  virtual Operand* f2i(unsigned aSize, unsigned resSize, Operand* a) {
    assert(&c, static_cast<Value*>(a)->type == lir::ValueFloat);
    Value* result = value(&c, lir::ValueGeneral);
    appendTranslate
      (&c, lir::Float2Int, aSize, static_cast<Value*>(a), resSize, result);
    return result;
  }
  
  virtual Operand* i2f(unsigned aSize, unsigned resSize, Operand* a) {
    assert(&c, static_cast<Value*>(a)->type == lir::ValueGeneral);
    Value* result = value(&c, lir::ValueFloat);
    appendTranslate
      (&c, lir::Int2Float, aSize, static_cast<Value*>(a), resSize, result);
    return result;
  }

  virtual void nullaryOp(lir::Operation type) {
    appendOperation(&c, type);
  }

  virtual void compile(uintptr_t stackOverflowHandler,
                       unsigned stackLimitOffset)
  {
    compiler::compile(&c, stackOverflowHandler, stackLimitOffset);
  }

  virtual unsigned resolve(uint8_t* dst) {
    c.machineCode = dst;
    c.assembler->setDestination(dst);

    Block* block = c.firstBlock;
    while (block->nextBlock or block->nextInstruction) {
      Block* next = block->nextBlock
        ? block->nextBlock
        : block->nextInstruction->firstEvent->block;

      next->start = block->assemblerBlock->resolve
        (block->start, next->assemblerBlock);

      block = next;
    }

    return c.machineCodeSize = block->assemblerBlock->resolve
      (block->start, 0) + c.assembler->footerSize();
  }

  virtual unsigned poolSize() {
    return c.constantCount * TargetBytesPerWord;
  }

  virtual void write() {
    c.assembler->write();

    int i = 0;
    for (ConstantPoolNode* n = c.firstConstant; n; n = n->next) {
      target_intptr_t* target = reinterpret_cast<target_intptr_t*>
        (c.machineCode + pad(c.machineCodeSize, TargetBytesPerWord) + i);

      if (n->promise->resolved()) {
        *target = targetVW(n->promise->value());
      } else {
        class Listener: public Promise::Listener {
         public:
          Listener(target_intptr_t* target): target(target){ }

          virtual bool resolve(int64_t value, void** location) {
            *target = targetVW(value);
            if (location) *location = target;
            return true;
          }

          target_intptr_t* target;
        };
        new (n->promise->listen(sizeof(Listener))) Listener(target);
      }

      i += TargetBytesPerWord;
    }
  }

  virtual void dispose() {
    // ignore
  }

  Context c;
  compiler::Client client;
};

} // namespace compiler

Compiler*
makeCompiler(System* system, Assembler* assembler, Zone* zone,
             Compiler::Client* client)
{
  return new(zone) compiler::MyCompiler(system, assembler, zone, client);
}

} // namespace codegen
} // namespace avian
