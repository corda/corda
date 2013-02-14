/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "target.h"
#include "util/runtime-array.h"

#include "codegen/compiler/context.h"
#include "codegen/compiler/event.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/read.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/promise.h"
#include "codegen/compiler/frame.h"
#include "codegen/compiler/ir.h"

namespace avian {
namespace codegen {
namespace compiler {

SiteMask generalRegisterMask(Context* c);
SiteMask generalRegisterOrConstantMask(Context* c);

CodePromise* codePromise(Context* c, Promise* offset);

void saveLocals(Context* c, Event* e);

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


void append(Context* c, Event* e);


void clean(Context* c, Event* e, Stack* stack, Local* locals, Read* reads,
      unsigned popIndex);

Read* live(Context* c UNUSED, Value* v);

void popRead(Context* c, Event* e UNUSED, Value* v);

void
maybeMove(Context* c, lir::BinaryOperation type, unsigned srcSize,
          unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst,
          const SiteMask& dstMask);

Site*
maybeMove(Context* c, Value* v, const SiteMask& mask, bool intersectMask,
          bool includeNextWord, unsigned registerReserveCount = 0);

Site*
maybeMove(Context* c, Read* read, bool intersectRead, bool includeNextWord,
          unsigned registerReserveCount = 0);
Site*
pickSiteOrMove(Context* c, Value* src, Value* dst, Site* nextWord,
               unsigned index);

void push(Context* c, unsigned footprint, Value* v);

Site*
pickTargetSite(Context* c, Read* read, bool intersectRead = false,
               unsigned registerReserveCount = 0,
               CostCalculator* costCalculator = 0);
Value*
register_(Context* c, int number);

Event::Event(Context* c):
  next(0), stackBefore(c->stack), localsBefore(c->locals),
  stackAfter(0), localsAfter(0), promises(0), reads(0),
  junctionSites(0), snapshots(0), predecessors(0), successors(0),
  visitLinks(0), block(0), logicalInstruction(c->logicalCode[c->logicalIp]),
  readCount(0)
{ }

void Event::addRead(Context* c, Value* v, Read* r) {
  if (DebugReads) {
    fprintf(stderr, "add read %p to %p last %p event %p (%s)\n",
            r, v, v->lastRead, this, (this ? this->name() : 0));
  }

  r->value = v;
  if (this) {
    r->event = this;
    r->eventNext = this->reads;
    this->reads = r;
    ++ this->readCount;
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

void Event::addRead(Context* c, Value* v, const SiteMask& mask, Value* successor) {
  this->addRead(c, v, read(c, mask, successor));
}

void Event::addReads(Context* c, Value* v, unsigned size,
         const SiteMask& lowMask, Value* lowSuccessor,
         const SiteMask& highMask, Value* highSuccessor)
{
  SingleRead* r = read(c, lowMask, lowSuccessor);
  this->addRead(c, v, r);
  if (size > vm::TargetBytesPerWord) {
    r->high_ = v->nextWord;
    this->addRead(c, v->nextWord, highMask, highSuccessor);
  }
}

void Event::addReads(Context* c, Value* v, unsigned size,
         const SiteMask& lowMask, const SiteMask& highMask)
{
  this->addReads(c, v, size, lowMask, 0, highMask, 0);
}

CodePromise* Event::makeCodePromise(Context* c) {
  return this->promises = new(c->zone) CodePromise(c, this->promises);
}

bool Event::isUnreachable() {
  for (Link* p = this->predecessors; p; p = p->nextPredecessor) {
    if (not p->predecessor->allExits()) return false;
  }
  return this->predecessors != 0;
}

unsigned Link::countPredecessors() {
  Link* link = this;
  unsigned c = 0;
  for (; link; link = link->nextPredecessor) {
    ++ c;
  }
  return c;
}

Link* Link::lastPredecessor() {
  Link* link = this;
  while (link->nextPredecessor) {
    link = link->nextPredecessor;
  }
  return link;
}

unsigned Link::countSuccessors() {
  Link* link = this;
  unsigned c = 0;
  for (; link; link = link->nextSuccessor) {
    ++ c;
  }
  return c;
}

Link* link(Context* c, Event* predecessor, Link* nextPredecessor, Event* successor,
     Link* nextSuccessor, ForkState* forkState)
{
  return new(c->zone) Link
    (predecessor, nextPredecessor, successor, nextSuccessor, forkState);
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
    returnAddressSurrogate(0),
    framePointerSurrogate(0),
    popIndex(0),
    stackArgumentIndex(0),
    flags(flags),
    resultSize(resultSize),
    stackArgumentFootprint(stackArgumentFootprint)
  {
    uint32_t registerMask = c->regFile->generalRegisters.mask;

    if (argumentCount) {
      assert(c, (flags & Compiler::TailJump) == 0);
      assert(c, stackArgumentFootprint == 0);

      Stack* s = argumentStack;
      unsigned index = 0;
      unsigned argumentIndex = 0;

      while (true) {
        unsigned footprint
          = (argumentIndex + 1 < argumentCount
             and s->value->nextWord == s->next->value)
          ? 2 : 1;

        if (index % (c->arch->argumentAlignment() ? footprint : 1)) {
          ++ index;
        }

        SiteMask targetMask;
        if (index + (c->arch->argumentRegisterAlignment() ? footprint : 1)
            <= c->arch->argumentRegisterCount())
        {
          int number = c->arch->argumentRegister(index);
        
          if (DebugReads) {
            fprintf(stderr, "reg %d arg read %p\n", number, s->value);
          }

          targetMask = SiteMask::fixedRegisterMask(number);
          registerMask &= ~(1 << number);
        } else {
          if (index < c->arch->argumentRegisterCount()) {
            index = c->arch->argumentRegisterCount();
          }

          unsigned frameIndex = index - c->arch->argumentRegisterCount();

          if (DebugReads) {
            fprintf(stderr, "stack %d arg read %p\n", frameIndex, s->value);
          }

          targetMask = SiteMask(1 << lir::MemoryOperand, 0, frameIndex);
        }

        this->addRead(c, s->value, targetMask);

        ++ index;

        if ((++ argumentIndex) < argumentCount) {
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
        ((flags & Compiler::Aligned) ? lir::AlignedCall : lir::Call, vm::TargetBytesPerWord,
         &typeMask, &planRegisterMask, &thunk);

      assert(c, not thunk);

      this->addRead(c, address, SiteMask
               (typeMask, registerMask & planRegisterMask, AnyFrameIndex));
    }

    Stack* stack = stackBefore;

    if (stackArgumentFootprint) {
      RUNTIME_ARRAY(Value*, arguments, stackArgumentFootprint);
      for (int i = stackArgumentFootprint - 1; i >= 0; --i) {
        Value* v = stack->value;
        stack = stack->next;

        if ((vm::TargetBytesPerWord == 8
             and (v == 0 or (i >= 1 and stack->value == 0)))
            or (vm::TargetBytesPerWord == 4 and v->nextWord != v))
        {
          assert(c, vm::TargetBytesPerWord == 8 or v->nextWord == stack->value);

          RUNTIME_ARRAY_BODY(arguments)[i--] = stack->value;
          stack = stack->next;
        }
        RUNTIME_ARRAY_BODY(arguments)[i] = v;
      }

      int returnAddressIndex;
      int framePointerIndex;
      int frameOffset;

      if (TailCalls and (flags & Compiler::TailJump)) {
        assert(c, argumentCount == 0);

        int base = frameBase(c);
        returnAddressIndex = base + c->arch->returnAddressOffset();
        if (UseFramePointer) {
          framePointerIndex = base + c->arch->framePointerOffset();
        } else {
          framePointerIndex = -1;
        }

        frameOffset = totalFrameSize(c)
          - c->arch->argumentFootprint(stackArgumentFootprint);
      } else {
        returnAddressIndex = -1;
        framePointerIndex = -1;
        frameOffset = 0;
      }

      for (unsigned i = 0; i < stackArgumentFootprint; ++i) {
        Value* v = RUNTIME_ARRAY_BODY(arguments)[i];
        if (v) {
          int frameIndex = i + frameOffset;

          if (DebugReads) {
            fprintf(stderr, "stack arg read %p at %d of %d\n",
                    v, frameIndex, totalFrameSize(c));
          }

          if (static_cast<int>(frameIndex) == returnAddressIndex) {
            returnAddressSurrogate = v;
            this->addRead(c, v, generalRegisterMask(c));
          } else if (static_cast<int>(frameIndex) == framePointerIndex) {
            framePointerSurrogate = v;
            this->addRead(c, v, generalRegisterMask(c));
          } else {
            this->addRead(c, v, SiteMask(1 << lir::MemoryOperand, 0, frameIndex));
          }
        }
      }
    }

    if ((not TailCalls) or (flags & Compiler::TailJump) == 0) {
      stackArgumentIndex = c->localFootprint;
      if (stackBefore) {
        stackArgumentIndex += stackBefore->index + 1 - stackArgumentFootprint;
      }

      popIndex
        = c->alignedFrameSize
        + c->parameterFootprint
        - c->arch->frameFooterSize()
        - stackArgumentIndex;

      assert(c, static_cast<int>(popIndex) >= 0);

      while (stack) {
        if (stack->value) {
          unsigned logicalIndex = compiler::frameIndex
            (c, stack->index + c->localFootprint);

          if (DebugReads) {
            fprintf(stderr, "stack save read %p at %d of %d\n",
                    stack->value, logicalIndex, totalFrameSize(c));
          }

          this->addRead(c, stack->value, SiteMask
                  (1 << lir::MemoryOperand, 0, logicalIndex));
        }

        stack = stack->next;
      }

      saveLocals(c, this);
    }
  }

  virtual const char* name() {
    return "CallEvent";
  }

  virtual void compile(Context* c) {
    lir::UnaryOperation op;

    if (TailCalls and (flags & Compiler::TailJump)) {
      if (flags & Compiler::LongJumpOrCall) {
        if (flags & Compiler::Aligned) {
          op = lir::AlignedLongJump;
        } else {
          op = lir::LongJump;
        }
      } else if (flags & Compiler::Aligned) {
        op = lir::AlignedJump;
      } else {
        op = lir::Jump;
      }

      assert(c, returnAddressSurrogate == 0
             or returnAddressSurrogate->source->type(c) == lir::RegisterOperand);
      assert(c, framePointerSurrogate == 0
             or framePointerSurrogate->source->type(c) == lir::RegisterOperand);

      int ras;
      if (returnAddressSurrogate) {
        returnAddressSurrogate->source->freeze(c, returnAddressSurrogate);

        ras = static_cast<RegisterSite*>
          (returnAddressSurrogate->source)->number;
      } else {
        ras = lir::NoRegister;
      }

      int fps;
      if (framePointerSurrogate) {
        framePointerSurrogate->source->freeze(c, framePointerSurrogate);

        fps = static_cast<RegisterSite*>
          (framePointerSurrogate->source)->number;
      } else {
        fps = lir::NoRegister;
      }

      int offset
        = static_cast<int>(c->arch->argumentFootprint(stackArgumentFootprint))
        - static_cast<int>(c->arch->argumentFootprint(c->parameterFootprint));

      c->assembler->popFrameForTailCall(c->alignedFrameSize, offset, ras, fps);
    } else if (flags & Compiler::LongJumpOrCall) {
      if (flags & Compiler::Aligned) {
        op = lir::AlignedLongCall;
      } else {
        op = lir::LongCall;
      }
    } else if (flags & Compiler::Aligned) {
      op = lir::AlignedCall;
    } else {
      op = lir::Call;
    }

    apply(c, op, vm::TargetBytesPerWord, address->source, address->source);

    if (traceHandler) {
      traceHandler->handleTrace(codePromise(c, c->assembler->offset(true)),
                                stackArgumentIndex);
    }

    if (TailCalls) {
      if (flags & Compiler::TailJump) {
        if (returnAddressSurrogate) {
          returnAddressSurrogate->source->thaw(c, returnAddressSurrogate);
        }

        if (framePointerSurrogate) {
          framePointerSurrogate->source->thaw(c, framePointerSurrogate);
        }
      } else {
        unsigned footprint = c->arch->argumentFootprint
          (stackArgumentFootprint);

        if (footprint > c->arch->stackAlignmentInWords()) {
          c->assembler->adjustFrame
            (footprint - c->arch->stackAlignmentInWords());
        }
      }
    }

    clean(c, this, stackBefore, localsBefore, reads, popIndex);

    if (resultSize and live(c, result)) {
      result->addSite(c, registerSite(c, c->arch->returnLow()));
      if (resultSize > vm::TargetBytesPerWord and live(c, result->nextWord)) {
        result->nextWord->addSite(c, registerSite(c, c->arch->returnHigh()));
      }
    }
  }

  virtual bool allExits() {
    return (flags & Compiler::TailJump) != 0;
  }

  Value* address;
  TraceHandler* traceHandler;
  Value* result;
  Value* returnAddressSurrogate;
  Value* framePointerSurrogate;
  unsigned popIndex;
  unsigned stackArgumentIndex;
  unsigned flags;
  unsigned resultSize;
  unsigned stackArgumentFootprint;
};

void
appendCall(Context* c, Value* address, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount,
           unsigned stackArgumentFootprint)
{
  append(c, new(c->zone)
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
      this->addReads(c, value, size,
        SiteMask::fixedRegisterMask(c->arch->returnLow()),
        SiteMask::fixedRegisterMask(c->arch->returnHigh()));
    }
  }

  virtual const char* name() {
    return "ReturnEvent";
  }

  virtual void compile(Context* c) {
    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
    
    if (not this->isUnreachable()) {
      c->assembler->popFrameAndPopArgumentsAndReturn
        (c->alignedFrameSize,
         c->arch->argumentFootprint(c->parameterFootprint));
    }
  }

  Value* value;
};

void appendReturn(Context* c, unsigned size, Value* value) {
  append(c, new(c->zone) ReturnEvent(c, size, value));
}

class MoveEvent: public Event {
 public:
  MoveEvent(Context* c, lir::BinaryOperation type, unsigned srcSize,
            unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst,
            const SiteMask& srcLowMask, const SiteMask& srcHighMask):
    Event(c), type(type), srcSize(srcSize), srcSelectSize(srcSelectSize),
    src(src), dstSize(dstSize), dst(dst)
  {
    assert(c, srcSelectSize <= srcSize);

    bool noop = srcSelectSize >= dstSize;
    
    if (dstSize > vm::TargetBytesPerWord) {
      dst->grow(c);
    }

    if (srcSelectSize > vm::TargetBytesPerWord) {
      src->maybeSplit(c);
    }

    this->addReads(c, src, srcSelectSize, srcLowMask, noop ? dst : 0,
             srcHighMask,
             noop and dstSize > vm::TargetBytesPerWord ? dst->nextWord : 0);
  }

  virtual const char* name() {
    return "MoveEvent";
  }

  virtual void compile(Context* c) {
    uint8_t dstTypeMask;
    uint64_t dstRegisterMask;

    c->arch->planDestination
      (type,
       srcSelectSize,
       1 << src->source->type(c), 
       (static_cast<uint64_t>(src->nextWord->source->registerMask(c)) << 32)
       | static_cast<uint64_t>(src->source->registerMask(c)),
       dstSize,
       &dstTypeMask,
       &dstRegisterMask);

    SiteMask dstLowMask(dstTypeMask, dstRegisterMask, AnyFrameIndex);
    SiteMask dstHighMask(dstTypeMask, dstRegisterMask >> 32, AnyFrameIndex);

    if (srcSelectSize >= vm::TargetBytesPerWord
        and dstSize >= vm::TargetBytesPerWord
        and srcSelectSize >= dstSize)
    {
      if (dst->target) {
        if (dstSize > vm::TargetBytesPerWord) {
          if (src->source->registerSize(c) > vm::TargetBytesPerWord) {
            apply(c, lir::Move, srcSelectSize, src->source, src->source,
                  dstSize, dst->target, dst->target);
            
            if (live(c, dst) == 0) {
              dst->removeSite(c, dst->target);
              if (dstSize > vm::TargetBytesPerWord) {
                dst->nextWord->removeSite(c, dst->nextWord->target);
              }
            }
          } else {
            src->nextWord->source->freeze(c, src->nextWord);

            maybeMove(c, lir::Move, vm::TargetBytesPerWord, vm::TargetBytesPerWord, src,
                      vm::TargetBytesPerWord, dst, dstLowMask);

            src->nextWord->source->thaw(c, src->nextWord);

            maybeMove
              (c, lir::Move, vm::TargetBytesPerWord, vm::TargetBytesPerWord, src->nextWord,
               vm::TargetBytesPerWord, dst->nextWord, dstHighMask);
          }
        } else {
          maybeMove(c, lir::Move, vm::TargetBytesPerWord, vm::TargetBytesPerWord, src,
                    vm::TargetBytesPerWord, dst, dstLowMask);
        }
      } else {
        Site* low = pickSiteOrMove(c, src, dst, 0, 0);
        if (dstSize > vm::TargetBytesPerWord) {
          pickSiteOrMove(c, src->nextWord, dst->nextWord, low, 1);
        }
      }
    } else if (srcSelectSize <= vm::TargetBytesPerWord
               and dstSize <= vm::TargetBytesPerWord)
    {
      maybeMove(c, type, srcSize, srcSelectSize, src, dstSize, dst,
                dstLowMask);
    } else {
      assert(c, srcSize == vm::TargetBytesPerWord);
      assert(c, srcSelectSize == vm::TargetBytesPerWord);

      if (dst->nextWord->target or live(c, dst->nextWord)) {
        assert(c, dstLowMask.typeMask & (1 << lir::RegisterOperand));

        Site* low = freeRegisterSite(c, dstLowMask.registerMask);

        src->source->freeze(c, src);

        dst->addSite(c, low);

        low->freeze(c, dst);
          
        if (DebugMoves) {
          char srcb[256]; src->source->toString(c, srcb, 256);
          char dstb[256]; low->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p\n",
                  srcb, dstb, src);
        }

        apply(c, lir::Move, vm::TargetBytesPerWord, src->source, src->source,
              vm::TargetBytesPerWord, low, low);

        low->thaw(c, dst);

        src->source->thaw(c, src);

        assert(c, dstHighMask.typeMask & (1 << lir::RegisterOperand));

        Site* high = freeRegisterSite(c, dstHighMask.registerMask);

        low->freeze(c, dst);

        dst->nextWord->addSite(c, high);

        high->freeze(c, dst->nextWord);
        
        if (DebugMoves) {
          char srcb[256]; low->toString(c, srcb, 256);
          char dstb[256]; high->toString(c, dstb, 256);
          fprintf(stderr, "extend %s to %s for %p %p\n",
                  srcb, dstb, dst, dst->nextWord);
        }

        apply(c, lir::Move, vm::TargetBytesPerWord, low, low, dstSize, low, high);

        high->thaw(c, dst->nextWord);

        low->thaw(c, dst);
      } else {
        pickSiteOrMove(c, src, dst, 0, 0);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }

  lir::BinaryOperation type;
  unsigned srcSize;
  unsigned srcSelectSize;
  Value* src;
  unsigned dstSize;
  Value* dst;
};

void
appendMove(Context* c, lir::BinaryOperation type, unsigned srcSize,
           unsigned srcSelectSize, Value* src, unsigned dstSize, Value* dst)
{
  bool thunk;
  uint8_t srcTypeMask;
  uint64_t srcRegisterMask;

  c->arch->planSource
    (type, srcSelectSize, &srcTypeMask, &srcRegisterMask, dstSize, &thunk);

  assert(c, not thunk);

  append(c, new(c->zone)
         MoveEvent
         (c, type, srcSize, srcSelectSize, src, dstSize, dst,
          SiteMask(srcTypeMask, srcRegisterMask, AnyFrameIndex),
          SiteMask(srcTypeMask, srcRegisterMask >> 32, AnyFrameIndex)));
}


void
freezeSource(Context* c, unsigned size, Value* v)
{
  v->source->freeze(c, v);
  if (size > vm::TargetBytesPerWord) {
    v->nextWord->source->freeze(c, v->nextWord);
  }
}

void
thawSource(Context* c, unsigned size, Value* v)
{
  v->source->thaw(c, v);
  if (size > vm::TargetBytesPerWord) {
    v->nextWord->source->thaw(c, v->nextWord);
  }
}

Read* liveNext(Context* c, Value* v) {
  assert(c, v->buddy->hasBuddy(c, v));

  Read* r = v->reads->next(c);
  if (valid(r)) return r;

  for (Value* p = v->buddy; p != v; p = p->buddy) {
    if (valid(p->reads)) return p->reads;
  }

  return 0;
}

void preserve(Context* c, Value* v, Read* r, Site* s) {
  s->freeze(c, v);

  maybeMove(c, r, false, true, 0);

  s->thaw(c, v);
}

Site* getTarget(Context* c, Value* value, Value* result, const SiteMask& resultMask) {
  Site* s;
  Value* v;
  Read* r = liveNext(c, value);
  if (value->source->match
      (c, static_cast<const SiteMask&>(resultMask))
      and (r == 0 or value->source->loneMatch
           (c, static_cast<const SiteMask&>(resultMask))))
  {
    s = value->source;
    v = value;
    if (r and v->uniqueSite(c, s)) {
      preserve(c, v, r, s);
    }
  } else {
    SingleRead r(resultMask, 0);
    r.value = result;
    r.successor_ = result;
    s = pickTargetSite(c, &r, true);
    v = result;
    result->addSite(c, s);
  }

  v->removeSite(c, s);

  s->freeze(c, v);

  return s;
}

class CombineEvent: public Event {
 public:
  CombineEvent(Context* c, lir::TernaryOperation type,
               unsigned firstSize, Value* first,
               unsigned secondSize, Value* second,
               unsigned resultSize, Value* result,
               const SiteMask& firstLowMask,
               const SiteMask& firstHighMask,
               const SiteMask& secondLowMask,
               const SiteMask& secondHighMask):
    Event(c), type(type), firstSize(firstSize), first(first),
    secondSize(secondSize), second(second), resultSize(resultSize),
    result(result)
  {
    this->addReads(c, first, firstSize, firstLowMask, firstHighMask);

    if (resultSize > vm::TargetBytesPerWord) {
      result->grow(c);
    }

    bool condensed = c->arch->alwaysCondensed(type);

    this->addReads(c, second, secondSize,
             secondLowMask, condensed ? result : 0,
             secondHighMask, condensed ? result->nextWord : 0);
  }

  virtual const char* name() {
    return "CombineEvent";
  }

  virtual void compile(Context* c) {
    assert(c, first->source->type(c) == first->nextWord->source->type(c));

    // if (second->source->type(c) != second->nextWord->source->type(c)) {
    //   fprintf(stderr, "%p %p %d : %p %p %d\n",
    //           second, second->source, second->source->type(c),
    //           second->nextWord, second->nextWord->source,
    //           second->nextWord->source->type(c));
    // }

    assert(c, second->source->type(c) == second->nextWord->source->type(c));
    
    freezeSource(c, firstSize, first);
    
    uint8_t cTypeMask;
    uint64_t cRegisterMask;

    c->arch->planDestination
      (type,
       firstSize,
       1 << first->source->type(c),
       (static_cast<uint64_t>(first->nextWord->source->registerMask(c)) << 32)
       | static_cast<uint64_t>(first->source->registerMask(c)),
       secondSize,
       1 << second->source->type(c),
       (static_cast<uint64_t>(second->nextWord->source->registerMask(c)) << 32)
       | static_cast<uint64_t>(second->source->registerMask(c)),
       resultSize,
       &cTypeMask,
       &cRegisterMask);

    SiteMask resultLowMask(cTypeMask, cRegisterMask, AnyFrameIndex);
    SiteMask resultHighMask(cTypeMask, cRegisterMask >> 32, AnyFrameIndex);

    Site* low = getTarget(c, second, result, resultLowMask);
    unsigned lowSize = low->registerSize(c);
    Site* high
      = (resultSize > lowSize
         ? getTarget(c, second->nextWord, result->nextWord, resultHighMask)
         : low);

//     fprintf(stderr, "combine %p:%p and %p:%p into %p:%p\n",
//             first, first->nextWord,
//             second, second->nextWord,
//             result, result->nextWord);

    apply(c, type,
          firstSize, first->source, first->nextWord->source,
          secondSize, second->source, second->nextWord->source,
          resultSize, low, high);

    thawSource(c, firstSize, first);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, second);
    if (resultSize > lowSize) {
      high->thaw(c, second->nextWord);
    }

    if (live(c, result)) {
      result->addSite(c, low);
      if (resultSize > lowSize and live(c, result->nextWord)) {
        result->nextWord->addSite(c, high);
      }
    }
  }

  lir::TernaryOperation type;
  unsigned firstSize;
  Value* first;
  unsigned secondSize;
  Value* second;
  unsigned resultSize;
  Value* result;
};

void
appendCombine(Context* c, lir::TernaryOperation type,
              unsigned firstSize, Value* first,
              unsigned secondSize, Value* second,
              unsigned resultSize, Value* result)
{
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t secondTypeMask;
  uint64_t secondRegisterMask;

  c->arch->planSource(type, firstSize, &firstTypeMask, &firstRegisterMask,
                      secondSize, &secondTypeMask, &secondRegisterMask,
                      resultSize, &thunk);

  if (thunk) {
    Stack* oldStack = c->stack;

    bool threadParameter;
    intptr_t handler = c->client->getThunk
      (type, firstSize, resultSize, &threadParameter);

    unsigned stackSize = vm::ceilingDivide(secondSize, vm::TargetBytesPerWord)
      + vm::ceilingDivide(firstSize, vm::TargetBytesPerWord);

    compiler::push(c, vm::ceilingDivide(secondSize, vm::TargetBytesPerWord), second);
    compiler::push(c, vm::ceilingDivide(firstSize, vm::TargetBytesPerWord), first);

    if (threadParameter) {
      ++ stackSize;

      compiler::push(c, 1, register_(c, c->arch->thread()));
    }

    Stack* argumentStack = c->stack;
    c->stack = oldStack;

    appendCall
      (c, value(c, lir::ValueGeneral, constantSite(c, handler)), 0, 0, result,
       resultSize, argumentStack, stackSize, 0);
  } else {
    append
      (c, new(c->zone)
       CombineEvent
       (c, type,
        firstSize, first,
        secondSize, second,
        resultSize, result,
        SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
        SiteMask(firstTypeMask, firstRegisterMask >> 32, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask >> 32, AnyFrameIndex)));
  }
}

class TranslateEvent: public Event {
 public:
  TranslateEvent(Context* c, lir::BinaryOperation type, unsigned valueSize,
                 Value* value, unsigned resultSize, Value* result,
                 const SiteMask& valueLowMask,
                 const SiteMask& valueHighMask):
    Event(c), type(type), valueSize(valueSize), resultSize(resultSize),
    value(value), result(result)
  {
    bool condensed = c->arch->alwaysCondensed(type);

    if (resultSize > vm::TargetBytesPerWord) {
      result->grow(c);
    }

    this->addReads(c, value, valueSize, valueLowMask, condensed ? result : 0,
             valueHighMask, condensed ? result->nextWord : 0);
  }

  virtual const char* name() {
    return "TranslateEvent";
  }

  virtual void compile(Context* c) {
    assert(c, value->source->type(c) == value->nextWord->source->type(c));

    uint8_t bTypeMask;
    uint64_t bRegisterMask;
    
    c->arch->planDestination
      (type,
       valueSize,
       1 << value->source->type(c),
       (static_cast<uint64_t>(value->nextWord->source->registerMask(c)) << 32)
       | static_cast<uint64_t>(value->source->registerMask(c)),
       resultSize,
       &bTypeMask,
       &bRegisterMask);

    SiteMask resultLowMask(bTypeMask, bRegisterMask, AnyFrameIndex);
    SiteMask resultHighMask(bTypeMask, bRegisterMask >> 32, AnyFrameIndex);
    
    Site* low = getTarget(c, value, result, resultLowMask);
    unsigned lowSize = low->registerSize(c);
    Site* high
      = (resultSize > lowSize
         ? getTarget(c, value->nextWord, result->nextWord, resultHighMask)
         : low);

    apply(c, type, valueSize, value->source, value->nextWord->source,
          resultSize, low, high);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, value);
    if (resultSize > lowSize) {
      high->thaw(c, value->nextWord);
    }

    if (live(c, result)) {
      result->addSite(c, low);
      if (resultSize > lowSize and live(c, result->nextWord)) {
        result->nextWord->addSite(c, high);
      }
    }
  }

  lir::BinaryOperation type;
  unsigned valueSize;
  unsigned resultSize;
  Value* value;
  Value* result;
  Read* resultRead;
  SiteMask resultLowMask;
  SiteMask resultHighMask;
};

void
appendTranslate(Context* c, lir::BinaryOperation type, unsigned firstSize,
                Value* first, unsigned resultSize, Value* result)
{
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;

  c->arch->planSource(type, firstSize, &firstTypeMask, &firstRegisterMask,
                resultSize, &thunk);

  if (thunk) {
    Stack* oldStack = c->stack;

    compiler::push(c, vm::ceilingDivide(firstSize, vm::TargetBytesPerWord), first);

    Stack* argumentStack = c->stack;
    c->stack = oldStack;

    appendCall
      (c, value
       (c, lir::ValueGeneral, constantSite
        (c, c->client->getThunk(type, firstSize, resultSize))),
       0, 0, result, resultSize, argumentStack,
       vm::ceilingDivide(firstSize, vm::TargetBytesPerWord), 0);
  } else {
    append(c, new(c->zone)
           TranslateEvent
           (c, type, firstSize, first, resultSize, result,
            SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
            SiteMask(firstTypeMask, firstRegisterMask >> 32, AnyFrameIndex)));
  }
}

class OperationEvent: public Event {
 public:
  OperationEvent(Context* c, lir::Operation op):
    Event(c), op(op)
  { }

  virtual const char* name() {
    return "OperationEvent";
  }

  virtual void compile(Context* c) {
    c->assembler->apply(op);
  }

  lir::Operation op;
};

void
appendOperation(Context* c, lir::Operation op)
{
  append(c, new(c->zone) OperationEvent(c, op));
}

ConstantSite* findConstantSite(Context* c, Value* v) {
  for (SiteIterator it(c, v); it.hasMore();) {
    Site* s = it.next();
    if (s->type(c) == lir::ConstantOperand) {
      return static_cast<ConstantSite*>(s);
    }
  }
  return 0;
}

void
moveIfConflict(Context* c, Value* v, MemorySite* s)
{
  if (v->reads) {
    SiteMask mask(1 << lir::RegisterOperand, ~0, AnyFrameIndex);
    v->reads->intersect(&mask);
    if (s->conflicts(mask)) {
      maybeMove(c, v->reads, true, false);
      v->removeSite(c, s);
    }
  }
}

class MemoryEvent: public Event {
 public:
  MemoryEvent(Context* c, Value* base, int displacement, Value* index,
              unsigned scale, Value* result):
    Event(c), base(base), displacement(displacement), index(index),
    scale(scale), result(result)
  {
    this->addRead(c, base, generalRegisterMask(c));
    if (index) {
      this->addRead(c, index, generalRegisterOrConstantMask(c));
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
        indexRegister = lir::NoRegister;
        displacement += (constant->value->value() * scale);
        scale = 1;
      } else {
        assert(c, index->source->type(c) == lir::RegisterOperand);
        indexRegister = static_cast<RegisterSite*>(index->source)->number;
      }
    } else {
      indexRegister = lir::NoRegister;
    }
    assert(c, base->source->type(c) == lir::RegisterOperand);
    int baseRegister = static_cast<RegisterSite*>(base->source)->number;

    popRead(c, this, base);
    if (index) {
      if (vm::TargetBytesPerWord == 8 and indexRegister != lir::NoRegister) {
        apply(c, lir::Move, 4, index->source, index->source,
              8, index->source, index->source);
      }

      popRead(c, this, index);
    }

    MemorySite* site = memorySite
      (c, baseRegister, displacement, indexRegister, scale);

    MemorySite* low;
    if (result->nextWord != result) {
      MemorySite* high = static_cast<MemorySite*>(site->copyHigh(c));
      low = static_cast<MemorySite*>(site->copyLow(c));

      result->nextWord->target = high;
      result->nextWord->addSite(c, high);
      moveIfConflict(c, result->nextWord, high);
    } else {
      low = site;
    }

    result->target = low;
    result->addSite(c, low);
    moveIfConflict(c, result, low);
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
  append(c, new(c->zone)
         MemoryEvent(c, base, displacement, index, scale, result));
}

double asFloat(unsigned size, int64_t v) {
  if (size == 4) {
    return vm::bitsToFloat(v);
  } else {
    return vm::bitsToDouble(v);
  }
}

bool
unordered(double a, double b)
{
  return not (a >= b or a < b);
}

bool
shouldJump(Context* c, lir::TernaryOperation type, unsigned size, int64_t b,
           int64_t a)
{
  switch (type) {
  case lir::JumpIfEqual:
    return a == b;

  case lir::JumpIfNotEqual:
    return a != b;

  case lir::JumpIfLess:
    return a < b;

  case lir::JumpIfGreater:
    return a > b;

  case lir::JumpIfLessOrEqual:
    return a <= b;

  case lir::JumpIfGreaterOrEqual:
    return a >= b;

  case lir::JumpIfFloatEqual:
    return asFloat(size, a) == asFloat(size, b);

  case lir::JumpIfFloatNotEqual:
    return asFloat(size, a) != asFloat(size, b);

  case lir::JumpIfFloatLess:
    return asFloat(size, a) < asFloat(size, b);

  case lir::JumpIfFloatGreater:
    return asFloat(size, a) > asFloat(size, b);

  case lir::JumpIfFloatLessOrEqual:
    return asFloat(size, a) <= asFloat(size, b);

  case lir::JumpIfFloatGreaterOrEqual:
    return asFloat(size, a) >= asFloat(size, b);

  case lir::JumpIfFloatLessOrUnordered:
    return asFloat(size, a) < asFloat(size, b)
      or unordered(asFloat(size, a), asFloat(size, b));

  case lir::JumpIfFloatGreaterOrUnordered:
    return asFloat(size, a) > asFloat(size, b)
      or unordered(asFloat(size, a), asFloat(size, b));

  case lir::JumpIfFloatLessOrEqualOrUnordered:
    return asFloat(size, a) <= asFloat(size, b)
      or unordered(asFloat(size, a), asFloat(size, b));

  case lir::JumpIfFloatGreaterOrEqualOrUnordered:
    return asFloat(size, a) >= asFloat(size, b)
      or unordered(asFloat(size, a), asFloat(size, b));

  default:
    abort(c);
  }
}

lir::TernaryOperation
thunkBranch(Context* c, lir::TernaryOperation type)
{
  switch (type) {
  case lir::JumpIfFloatEqual:
    return lir::JumpIfEqual;

  case lir::JumpIfFloatNotEqual:
    return lir::JumpIfNotEqual;

  case lir::JumpIfFloatLess:
  case lir::JumpIfFloatLessOrUnordered:
    return lir::JumpIfLess;

  case lir::JumpIfFloatGreater:
  case lir::JumpIfFloatGreaterOrUnordered:
    return lir::JumpIfGreater;

  case lir::JumpIfFloatLessOrEqual:
  case lir::JumpIfFloatLessOrEqualOrUnordered:
    return lir::JumpIfLessOrEqual;

  case lir::JumpIfFloatGreaterOrEqual:
  case lir::JumpIfFloatGreaterOrEqualOrUnordered:
    return lir::JumpIfGreaterOrEqual;

  default:
    abort(c);
  }
}

class BranchEvent: public Event {
 public:
  BranchEvent(Context* c, lir::TernaryOperation type, unsigned size,
              Value* first, Value* second, Value* address,
              const SiteMask& firstLowMask,
              const SiteMask& firstHighMask,
              const SiteMask& secondLowMask,
              const SiteMask& secondHighMask):
    Event(c), type(type), size(size), first(first), second(second),
    address(address)
  {
    this->addReads(c, first, size, firstLowMask, firstHighMask);
    this->addReads(c, second, size, secondLowMask, secondHighMask);

    uint8_t typeMask;
    uint64_t registerMask;
    c->arch->planDestination(type, size, 0, 0, size, 0, 0, vm::TargetBytesPerWord,
                             &typeMask, &registerMask);

    this->addRead(c, address, SiteMask(typeMask, registerMask, AnyFrameIndex));
  }

  virtual const char* name() {
    return "BranchEvent";
  }

  virtual void compile(Context* c) {
    ConstantSite* firstConstant = findConstantSite(c, first);
    ConstantSite* secondConstant = findConstantSite(c, second);

    if (not this->isUnreachable()) {
      if (firstConstant
          and secondConstant
          and firstConstant->value->resolved()
          and secondConstant->value->resolved())
      {
        int64_t firstValue = firstConstant->value->value();
        int64_t secondValue = secondConstant->value->value();

        if (size > vm::TargetBytesPerWord) {
          firstValue |= findConstantSite
            (c, first->nextWord)->value->value() << 32;
          secondValue |= findConstantSite
            (c, second->nextWord)->value->value() << 32;
        }

        if (shouldJump(c, type, size, firstValue, secondValue)) {
          apply(c, lir::Jump, vm::TargetBytesPerWord, address->source, address->source);
        }      
      } else {
        freezeSource(c, size, first);
        freezeSource(c, size, second);
        freezeSource(c, vm::TargetBytesPerWord, address);

        apply(c, type, size, first->source, first->nextWord->source,
              size, second->source, second->nextWord->source,
              vm::TargetBytesPerWord, address->source, address->source);

        thawSource(c, vm::TargetBytesPerWord, address);
        thawSource(c, size, second);
        thawSource(c, size, first);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }

  virtual bool isBranch() { return true; }

  lir::TernaryOperation type;
  unsigned size;
  Value* first;
  Value* second;
  Value* address;
};

void
appendBranch(Context* c, lir::TernaryOperation type, unsigned size, Value* first,
             Value* second, Value* address)
{
  bool thunk;
  uint8_t firstTypeMask;
  uint64_t firstRegisterMask;
  uint8_t secondTypeMask;
  uint64_t secondRegisterMask;

  c->arch->planSource(type, size, &firstTypeMask, &firstRegisterMask,
                      size, &secondTypeMask, &secondRegisterMask,
                      vm::TargetBytesPerWord, &thunk);

  if (thunk) {
    Stack* oldStack = c->stack;

    bool threadParameter;
    intptr_t handler = c->client->getThunk
      (type, size, size, &threadParameter);

    assert(c, not threadParameter);

    compiler::push(c, vm::ceilingDivide(size, vm::TargetBytesPerWord), second);
    compiler::push(c, vm::ceilingDivide(size, vm::TargetBytesPerWord), first);

    Stack* argumentStack = c->stack;
    c->stack = oldStack;

    Value* result = value(c, lir::ValueGeneral);
    appendCall
      (c, value
       (c, lir::ValueGeneral, constantSite(c, handler)), 0, 0, result, 4,
       argumentStack, vm::ceilingDivide(size, vm::TargetBytesPerWord) * 2, 0);

    appendBranch(c, thunkBranch(c, type), 4, value
                 (c, lir::ValueGeneral, constantSite(c, static_cast<int64_t>(0))),
                 result, address);
  } else {
    append
      (c, new(c->zone)
       BranchEvent
       (c, type, size, first, second, address,
        SiteMask(firstTypeMask, firstRegisterMask, AnyFrameIndex),
        SiteMask(firstTypeMask, firstRegisterMask >> 32, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask, AnyFrameIndex),
        SiteMask(secondTypeMask, secondRegisterMask >> 32, AnyFrameIndex)));
  }
}

void clean(Context* c, Value* v, unsigned popIndex) {
  for (SiteIterator it(c, v); it.hasMore();) {
    Site* s = it.next();
    if (not (s->match(c, SiteMask(1 << lir::MemoryOperand, 0, AnyFrameIndex))
             and offsetToFrameIndex
             (c, static_cast<MemorySite*>(s)->offset)
             >= popIndex))
    {
      if (false and
          s->match(c, SiteMask(1 << lir::MemoryOperand, 0, AnyFrameIndex)))
      {
        char buffer[256]; s->toString(c, buffer, 256);
        fprintf(stderr, "remove %s from %p at %d pop offset 0x%x\n",
                buffer, v, offsetToFrameIndex
                (c, static_cast<MemorySite*>(s)->offset),
                frameIndexToOffset(c, popIndex));
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

class JumpEvent: public Event {
 public:
  JumpEvent(Context* c, lir::UnaryOperation type, Value* address, bool exit,
            bool cleanLocals):
    Event(c), type(type), address(address), exit(exit),
    cleanLocals(cleanLocals)
  {
    bool thunk;
    uint8_t typeMask;
    uint64_t registerMask;
    c->arch->plan(type, vm::TargetBytesPerWord, &typeMask, &registerMask, &thunk);

    assert(c, not thunk);

    this->addRead(c, address, SiteMask(typeMask, registerMask, AnyFrameIndex));
  }

  virtual const char* name() {
    return "JumpEvent";
  }

  virtual void compile(Context* c) {
    if (not this->isUnreachable()) {
      apply(c, type, vm::TargetBytesPerWord, address->source, address->source);
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    if (cleanLocals) {
      for (FrameIterator it(c, 0, c->locals); it.hasMore();) {
        FrameIterator::Element e = it.next(c);
        clean(c, e.value, 0);
      }
    }
  }

  virtual bool isBranch() { return true; }

  virtual bool allExits() {
    return exit or this->isUnreachable();
  }

  lir::UnaryOperation type;
  Value* address;
  bool exit;
  bool cleanLocals;
};

void appendJump(Context* c, lir::UnaryOperation type, Value* address, bool exit, bool cleanLocals) {
  append(c, new(c->zone) JumpEvent(c, type, address, exit, cleanLocals));
}

class BoundsCheckEvent: public Event {
 public:
  BoundsCheckEvent(Context* c, Value* object, unsigned lengthOffset,
                   Value* index, intptr_t handler):
    Event(c), object(object), lengthOffset(lengthOffset), index(index),
    handler(handler)
  {
    this->addRead(c, object, generalRegisterMask(c));
    this->addRead(c, index, generalRegisterOrConstantMask(c));
  }

  virtual const char* name() {
    return "BoundsCheckEvent";
  }

  virtual void compile(Context* c) {
    Assembler* a = c->assembler;

    ConstantSite* constant = findConstantSite(c, index);
    CodePromise* outOfBoundsPromise = 0;

    if (constant) {
      if (constant->value->value() < 0) {
        lir::Constant handlerConstant(resolvedPromise(c, handler));
        a->apply(lir::Call,
          OperandInfo(vm::TargetBytesPerWord, lir::ConstantOperand, &handlerConstant));
      }
    } else {
      outOfBoundsPromise = compiler::codePromise(c, static_cast<Promise*>(0));

      ConstantSite zero(resolvedPromise(c, 0));
      ConstantSite oob(outOfBoundsPromise);
      apply(c, lir::JumpIfLess,
        4, &zero, &zero,
        4, index->source, index->source,
        vm::TargetBytesPerWord, &oob, &oob);
    }

    if (constant == 0 or constant->value->value() >= 0) {
      assert(c, object->source->type(c) == lir::RegisterOperand);
      MemorySite length(static_cast<RegisterSite*>(object->source)->number,
                        lengthOffset, lir::NoRegister, 1);
      length.acquired = true;

      CodePromise* nextPromise = compiler::codePromise(c, static_cast<Promise*>(0));

      freezeSource(c, vm::TargetBytesPerWord, index);

      ConstantSite next(nextPromise);
      apply(c, lir::JumpIfGreater,
        4, index->source,
        index->source, 4, &length,
        &length, vm::TargetBytesPerWord, &next, &next);

      thawSource(c, vm::TargetBytesPerWord, index);

      if (constant == 0) {
        outOfBoundsPromise->offset = a->offset();
      }

      lir::Constant handlerConstant(resolvedPromise(c, handler));
      a->apply(lir::Call,
        OperandInfo(vm::TargetBytesPerWord, lir::ConstantOperand, &handlerConstant));

      nextPromise->offset = a->offset();
    }

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
  append(c, new(c->zone) BoundsCheckEvent(c, object, lengthOffset, index, handler));
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
    if (live(c, value)) {
      value->addSite(c, frameSite(c, index));
    }
  }

  Value* value;
  int index;
};

void
appendFrameSite(Context* c, Value* value, int index)
{
  append(c, new(c->zone) FrameSiteEvent(c, value, index));
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
  append(c, new(c->zone) SaveLocalsEvent(c));
}

class DummyEvent: public Event {
 public:
  DummyEvent(Context* c, Local* locals):
  Event(c),
  locals_(locals)
  { }

  virtual const char* name() {
    return "DummyEvent";
  }

  virtual void compile(Context*) { }

  virtual Local* locals() {
    return locals_;
  }

  Local* locals_;
};

void
appendDummy(Context* c)
{
  Stack* stack = c->stack;
  Local* locals = c->locals;
  LogicalInstruction* i = c->logicalCode[c->logicalIp];

  c->stack = i->stack;
  c->locals = i->locals;

  append(c, new(c->zone) DummyEvent(c, locals));

  c->stack = stack;
  c->locals = locals;  
}

} // namespace compiler
} // namespace codegen
} // namespace avian
