/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"
#include <avian/util/runtime-array.h>
#include <avian/util/math.h>

#include "codegen/compiler/context.h"
#include "codegen/compiler/event.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/read.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/promise.h"
#include "codegen/compiler/frame.h"
#include "codegen/compiler/ir.h"

using namespace avian::util;

namespace avian {
namespace codegen {
namespace compiler {

SiteMask generalRegisterMask(Context* c);
SiteMask generalRegisterOrConstantMask(Context* c);

CodePromise* codePromise(Context* c, Promise* offset);

void saveLocals(Context* c, Event* e);

void apply(Context* c,
           lir::UnaryOperation op,
           unsigned s1Size,
           Site* s1Low,
           Site* s1High);

void apply(Context* c,
           lir::BinaryOperation op,
           unsigned s1Size,
           Site* s1Low,
           Site* s1High,
           unsigned s2Size,
           Site* s2Low,
           Site* s2High);

void apply(Context* c,
           lir::TernaryOperation op,
           unsigned s1Size,
           Site* s1Low,
           Site* s1High,
           unsigned s2Size,
           Site* s2Low,
           Site* s2High,
           unsigned s3Size,
           Site* s3Low,
           Site* s3High);

void append(Context* c, Event* e);

void clean(Context* c,
           Event* e,
           Stack* stack,
           Local* locals,
           Read* reads,
           unsigned popIndex);

Read* live(Context* c UNUSED, Value* v);

void popRead(Context* c, Event* e UNUSED, Value* v);

void maybeMove(Context* c,
               lir::BinaryOperation op,
               unsigned srcSize,
               unsigned srcSelectSize,
               Value* src,
               unsigned dstSize,
               Value* dst,
               const SiteMask& dstMask);

Site* maybeMove(Context* c,
                Value* v,
                const SiteMask& mask,
                bool intersectMask,
                bool includeNextWord,
                unsigned registerReserveCount = 0);

Site* maybeMove(Context* c,
                Read* read,
                bool intersectRead,
                bool includeNextWord,
                unsigned registerReserveCount = 0);
Site* pickSiteOrMove(Context* c,
                     Value* src,
                     Value* dst,
                     Site* nextWord,
                     unsigned index);

Site* pickTargetSite(Context* c,
                     Read* read,
                     bool intersectRead = false,
                     unsigned registerReserveCount = 0,
                     CostCalculator* costCalculator = 0);
Value* threadRegister(Context* c);

Event::Event(Context* c)
    : next(0),
      stackBefore(c->stack),
      localsBefore(c->locals),
      stackAfter(0),
      localsAfter(0),
      promises(0),
      reads(0),
      junctionSites(0),
      snapshots(0),
      predecessors(0),
      successors(0),
      visitLinks(0),
      block(0),
      logicalInstruction(c->logicalCode[c->logicalIp]),
      readCount(0)
{
}

void Event::addRead(Context* c, Value* v, Read* r)
{
  if (DebugReads) {
    fprintf(stderr,
            "add read %p to %p last %p event %p (%s)\n",
            r,
            v,
            v->lastRead,
            this,
            this->name());
  }

  r->event = this;
  r->eventNext = this->reads;
  this->reads = r;
  ++this->readCount;

  finishAddRead(c, v, r);
}

void finishAddRead(Context* c, Value* v, Read* r)
{
  r->value = v;

  if (v->lastRead) {
    if (DebugReads) {
      fprintf(stderr, "append %p to %p for %p\n", r, v->lastRead, v);
    }

    v->lastRead->append(c, r);
  } else {
    v->reads = r;
  }
  v->lastRead = r;
}

void Event::addRead(Context* c,
                    Value* v,
                    const SiteMask& mask,
                    Value* successor)
{
  this->addRead(c, v, read(c, mask, successor));
}

void Event::addReads(Context* c,
                     Value* v,
                     unsigned size,
                     const SiteMask& lowMask,
                     Value* lowSuccessor,
                     const SiteMask& highMask,
                     Value* highSuccessor)
{
  SingleRead* r = read(c, lowMask, lowSuccessor);
  this->addRead(c, v, r);
  if (size > c->targetInfo.pointerSize) {
    r->high_ = v->nextWord;
    this->addRead(c, v->nextWord, highMask, highSuccessor);
  }
}

void Event::addReads(Context* c,
                     Value* v,
                     unsigned size,
                     const SiteMask& lowMask,
                     const SiteMask& highMask)
{
  this->addReads(c, v, size, lowMask, 0, highMask, 0);
}

CodePromise* Event::makeCodePromise(Context* c)
{
  return this->promises = new (c->zone) CodePromise(c, this->promises);
}

bool Event::isUnreachable()
{
  for (Link* p = this->predecessors; p; p = p->nextPredecessor) {
    if (not p->predecessor->allExits())
      return false;
  }
  return this->predecessors != 0;
}

unsigned Link::countPredecessors()
{
  Link* link = this;
  unsigned c = 0;
  for (; link; link = link->nextPredecessor) {
    ++c;
  }
  return c;
}

Link* Link::lastPredecessor()
{
  Link* link = this;
  while (link->nextPredecessor) {
    link = link->nextPredecessor;
  }
  return link;
}

unsigned Link::countSuccessors()
{
  Link* link = this;
  unsigned c = 0;
  for (; link; link = link->nextSuccessor) {
    ++c;
  }
  return c;
}

Link* link(Context* c,
           Event* predecessor,
           Link* nextPredecessor,
           Event* successor,
           Link* nextSuccessor,
           ForkState* forkState)
{
  return new (c->zone)
      Link(predecessor, nextPredecessor, successor, nextSuccessor, forkState);
}

Value* maybeBuddySlice(Context* c, Value* v)
{
  if (v->home >= 0) {
    Value* n = value(c, v->type);
    appendBuddy(c, v, n);
    return n;
  } else {
    return v;
  }
}

template <class T>
struct SliceStack : public Slice<T> {
  size_t capacity;

  SliceStack(T* items, size_t capacity)
      : Slice<T>(items + capacity, 0), capacity(capacity)
  {
  }

  void push(const T& item)
  {
    ASSERT(Slice<T>::count < capacity);
    --Slice<T>::items;
    ++Slice<T>::count;
    *Slice<T>::items = item;
  }
};

template <class T, size_t Capacity>
struct FixedSliceStack : public SliceStack<T> {
  T itemArray[Capacity];

  FixedSliceStack() : SliceStack<T>(&itemArray[0], Capacity)
  {
  }
};

Value* slicePushWord(Context* c,
                     Value* v,
                     size_t stackBase UNUSED,
                     SliceStack<ir::Value*>& slice)
{
  if (v) {
    v = maybeBuddySlice(c, v);
  }

  size_t index UNUSED = slice.count;

  assertT(c, slice.count < slice.capacity);
  slice.push(v);

  if (false) {
    fprintf(stderr, "push %p\n", v);
  }

  if (v) {
    v->home = frameIndex(c, index + stackBase + c->localFootprint);
  }

  return v;
}

void slicePush(Context* c,
               unsigned footprint,
               Value* v,
               size_t stackBase,
               SliceStack<ir::Value*>& slice)
{
  assertT(c, footprint);

  bool bigEndian = c->arch->bigEndian();

  Value* low = v;

  if (bigEndian) {
    v = slicePushWord(c, v, stackBase, slice);
  }

  Value* high;
  if (footprint > 1) {
    assertT(c, footprint == 2);

    if (c->targetInfo.pointerSize == 4) {
      low->maybeSplit(c);
      high = slicePushWord(c, low->nextWord, stackBase, slice);
    } else {
      high = slicePushWord(c, 0, stackBase, slice);
    }
  } else {
    high = 0;
  }

  if (not bigEndian) {
    v = slicePushWord(c, v, stackBase, slice);
  }

  if (high) {
    v->nextWord = high;
    high->nextWord = v;
    high->wordIndex = 1;
  }
}

class CallEvent : public Event {
 public:
  CallEvent(Context* c,
            Value* address,
            ir::CallingConvention callingConvention,
            unsigned flags,
            TraceHandler* traceHandler,
            Value* resultValue,
            util::Slice<ir::Value*> arguments)
      : Event(c),
        address(address),
        traceHandler(traceHandler),
        resultValue(resultValue),
        returnAddressSurrogate(0),
        framePointerSurrogate(0),
        popIndex(0),
        stackArgumentIndex(0),
        flags(flags),
        stackArgumentFootprint(callingConvention == ir::CallingConvention::Avian
                                   ? arguments.count
                                   : 0)
  {
    RegisterMask registerMask = c->regFile->generalRegisters;

    if (callingConvention == ir::CallingConvention::Native) {
      assertT(c, (flags & Compiler::TailJump) == 0);
      assertT(c, stackArgumentFootprint == 0);

      unsigned index = 0;
      unsigned argumentIndex = 0;

      while (true) {
        Value* v = static_cast<Value*>(arguments[argumentIndex]);

        unsigned footprint = (argumentIndex + 1 < arguments.count
                              and v->nextWord == arguments[argumentIndex + 1])
                                 ? 2
                                 : 1;

        if (index % (c->arch->argumentAlignment() ? footprint : 1)) {
          ++index;
        }

        SiteMask targetMask;
        if (index + (c->arch->argumentRegisterAlignment() ? footprint : 1)
            <= c->arch->argumentRegisterCount()) {
          Register number = c->arch->argumentRegister(index);

          if (DebugReads) {
            fprintf(stderr, "reg %d arg read %p\n", number.index(), v);
          }

          targetMask = SiteMask::fixedRegisterMask(number);
          registerMask = registerMask.excluding(number);
        } else {
          if (index < c->arch->argumentRegisterCount()) {
            index = c->arch->argumentRegisterCount();
          }

          unsigned frameIndex = index - c->arch->argumentRegisterCount();

          if (DebugReads) {
            fprintf(stderr, "stack %d arg read %p\n", frameIndex, v);
          }

          targetMask = SiteMask(lir::Operand::MemoryMask, 0, frameIndex);
        }

        this->addRead(c, v, targetMask);

        ++index;

        if ((++argumentIndex) >= arguments.count) {
          break;
        }
      }
    }

    if (DebugReads) {
      fprintf(stderr, "address read %p\n", address);
    }

    {
      bool thunk;
      OperandMask op;
      c->arch->plan((flags & Compiler::Aligned) ? lir::AlignedCall : lir::Call,
                    c->targetInfo.pointerSize,
                    op,
                    &thunk);

      assertT(c, not thunk);

      this->addRead(
          c,
          address,
          SiteMask(op.typeMask, registerMask & op.lowRegisterMask, AnyFrameIndex));
    }

    Stack* stack = stackBefore;

    if (callingConvention == ir::CallingConvention::Avian) {
      for (size_t i = 0; i < arguments.count; i++) {
        stack = stack->next;
      }
      for (int i = stackArgumentFootprint - 1; i >= 0; --i) {
        Value* v = static_cast<Value*>(arguments[i]);

        if ((c->targetInfo.pointerSize == 8
             && (v == 0 || (i >= 1 && arguments[i - 1] == 0)))
            || (c->targetInfo.pointerSize == 4 && v->nextWord != v)) {
          assertT(c,
                  c->targetInfo.pointerSize == 8
                  or v->nextWord == arguments[i - 1]);

          arguments[i] = arguments[i - 1];
          --i;
        }
        arguments[i] = v;
      }

      int returnAddressIndex;
      int framePointerIndex;
      int frameOffset;

      if (TailCalls and (flags & Compiler::TailJump)) {
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
        Value* v = static_cast<Value*>(arguments[i]);
        if (v) {
          int frameIndex = i + frameOffset;

          if (DebugReads) {
            fprintf(stderr,
                    "stack arg read %p at %d of %d\n",
                    v,
                    frameIndex,
                    totalFrameSize(c));
          }

          if (static_cast<int>(frameIndex) == returnAddressIndex) {
            returnAddressSurrogate = v;
            this->addRead(c, v, generalRegisterMask(c));
          } else if (static_cast<int>(frameIndex) == framePointerIndex) {
            framePointerSurrogate = v;
            this->addRead(c, v, generalRegisterMask(c));
          } else {
            this->addRead(
                c, v, SiteMask(lir::Operand::MemoryMask, 0, frameIndex));
          }
        }
      }
    }

    if ((not TailCalls) or (flags & Compiler::TailJump) == 0) {
      stackArgumentIndex = c->localFootprint;
      if (stackBefore) {
        stackArgumentIndex += stackBefore->index + 1 - stackArgumentFootprint;
      }

      popIndex = c->alignedFrameSize + c->parameterFootprint
                 - c->arch->frameFooterSize() - stackArgumentIndex;

      assertT(c, static_cast<int>(popIndex) >= 0);

      while (stack) {
        if (stack->value) {
          unsigned logicalIndex
              = compiler::frameIndex(c, stack->index + c->localFootprint);

          if (DebugReads) {
            fprintf(stderr,
                    "stack save read %p at %d of %d\n",
                    stack->value,
                    logicalIndex,
                    totalFrameSize(c));
          }

          this->addRead(c,
                        stack->value,
                        SiteMask(lir::Operand::MemoryMask, 0, logicalIndex));
        }

        stack = stack->next;
      }

      saveLocals(c, this);
    }
  }

  virtual const char* name()
  {
    return "CallEvent";
  }

  virtual void compile(Context* c)
  {
    lir::UnaryOperation op;

    unsigned footprint = c->arch->argumentFootprint(stackArgumentFootprint);

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

      assertT(
          c,
          returnAddressSurrogate == 0
          or returnAddressSurrogate->source->type(c) == lir::Operand::Type::RegisterPair);
      assertT(
          c,
          framePointerSurrogate == 0
          or framePointerSurrogate->source->type(c) == lir::Operand::Type::RegisterPair);

      Register ras;
      if (returnAddressSurrogate) {
        returnAddressSurrogate->source->freeze(c, returnAddressSurrogate);

        ras = static_cast<RegisterSite*>(returnAddressSurrogate->source)
                  ->number;
      } else {
        ras = NoRegister;
      }

      Register fps;
      if (framePointerSurrogate) {
        framePointerSurrogate->source->freeze(c, framePointerSurrogate);

        fps = static_cast<RegisterSite*>(framePointerSurrogate->source)->number;
      } else {
        fps = NoRegister;
      }

      int offset = static_cast<int>(footprint)
                   - static_cast<int>(
                         c->arch->argumentFootprint(c->parameterFootprint));

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

    apply(c, op, c->targetInfo.pointerSize, address->source, address->source);

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
      } else if (footprint > c->arch->stackAlignmentInWords()) {
        c->assembler->adjustFrame(footprint - c->arch->stackAlignmentInWords());
      }
    }

    clean(c, this, stackBefore, localsBefore, reads, popIndex);

    if (resultValue->type.size(c->targetInfo) and live(c, resultValue)) {
      resultValue->addSite(c, registerSite(c, c->arch->returnLow()));
      if (resultValue->type.size(c->targetInfo) > c->targetInfo.pointerSize
          and live(c, resultValue->nextWord)) {
        resultValue->nextWord->addSite(c,
                                       registerSite(c, c->arch->returnHigh()));
      }
    }
  }

  virtual bool allExits()
  {
    return (flags & Compiler::TailJump) != 0;
  }

  Value* address;
  TraceHandler* traceHandler;
  Value* resultValue;
  Value* returnAddressSurrogate;
  Value* framePointerSurrogate;
  unsigned popIndex;
  unsigned stackArgumentIndex;
  unsigned flags;
  unsigned stackArgumentFootprint;
};

void appendCall(Context* c,
                Value* address,
                ir::CallingConvention callingConvention,
                unsigned flags,
                TraceHandler* traceHandler,
                Value* result,
                util::Slice<ir::Value*> arguments)
{
  append(c,
         new (c->zone) CallEvent(c,
                                 address,
                                 callingConvention,
                                 flags,
                                 traceHandler,
                                 result,
                                 arguments));
}

class ReturnEvent : public Event {
 public:
  ReturnEvent(Context* c, Value* value) : Event(c), value(value)
  {
    if (value) {
      this->addReads(c,
                     value,
                     value->type.size(c->targetInfo),
                     SiteMask::fixedRegisterMask(c->arch->returnLow()),
                     SiteMask::fixedRegisterMask(c->arch->returnHigh()));
    }
  }

  virtual const char* name()
  {
    return "ReturnEvent";
  }

  virtual void compile(Context* c)
  {
    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    if (not this->isUnreachable()) {
      c->assembler->popFrameAndPopArgumentsAndReturn(
          c->alignedFrameSize,
          c->arch->argumentFootprint(c->parameterFootprint));
    }
  }

  Value* value;
};

void appendReturn(Context* c, Value* value)
{
  append(c, new (c->zone) ReturnEvent(c, value));
}

class MoveEvent : public Event {
 public:
  MoveEvent(Context* c,
            lir::BinaryOperation op,
            unsigned srcSize,
            unsigned srcSelectSize,
            Value* srcValue,
            unsigned dstSize,
            Value* dstValue,
            const SiteMask& srcLowMask,
            const SiteMask& srcHighMask)
      : Event(c),
        op(op),
        srcSize(srcSize),
        srcSelectSize(srcSelectSize),
        srcValue(srcValue),
        dstSize(dstSize),
        dstValue(dstValue)
  {
    assertT(c, srcSelectSize <= srcSize);

    bool noop = srcSelectSize >= dstSize;

    if (dstSize > c->targetInfo.pointerSize) {
      dstValue->grow(c);
    }

    if (srcSelectSize > c->targetInfo.pointerSize) {
      srcValue->maybeSplit(c);
    }

    this->addReads(
        c,
        srcValue,
        srcSelectSize,
        srcLowMask,
        noop ? dstValue : 0,
        srcHighMask,
        noop and dstSize > c->targetInfo.pointerSize ? dstValue->nextWord : 0);
  }

  virtual const char* name()
  {
    return "MoveEvent";
  }

  virtual void compile(Context* c)
  {
    OperandMask dst;

    c->arch->planDestination(
        op,
        srcSelectSize,
        OperandMask(
            1 << (unsigned)srcValue->source->type(c),
            srcValue->source->registerMask(c),
            srcValue->nextWord->source->registerMask(c)),
        dstSize,
        dst);

    SiteMask dstLowMask = SiteMask::lowPart(dst);
    SiteMask dstHighMask = SiteMask::highPart(dst);

    if (srcSelectSize >= c->targetInfo.pointerSize
        and dstSize >= c->targetInfo.pointerSize and srcSelectSize >= dstSize) {
      if (dstValue->target) {
        if (dstSize > c->targetInfo.pointerSize) {
          if (srcValue->source->registerSize(c) > c->targetInfo.pointerSize) {
            apply(c,
                  lir::Move,
                  srcSelectSize,
                  srcValue->source,
                  srcValue->source,
                  dstSize,
                  dstValue->target,
                  dstValue->target);

            if (live(c, dstValue) == 0) {
              dstValue->removeSite(c, dstValue->target);
              if (dstSize > c->targetInfo.pointerSize) {
                dstValue->nextWord->removeSite(c, dstValue->nextWord->target);
              }
            }
          } else {
            srcValue->nextWord->source->freeze(c, srcValue->nextWord);

            maybeMove(c,
                      lir::Move,
                      c->targetInfo.pointerSize,
                      c->targetInfo.pointerSize,
                      srcValue,
                      c->targetInfo.pointerSize,
                      dstValue,
                      dstLowMask);

            srcValue->nextWord->source->thaw(c, srcValue->nextWord);

            maybeMove(c,
                      lir::Move,
                      c->targetInfo.pointerSize,
                      c->targetInfo.pointerSize,
                      srcValue->nextWord,
                      c->targetInfo.pointerSize,
                      dstValue->nextWord,
                      dstHighMask);
          }
        } else {
          maybeMove(c,
                    lir::Move,
                    c->targetInfo.pointerSize,
                    c->targetInfo.pointerSize,
                    srcValue,
                    c->targetInfo.pointerSize,
                    dstValue,
                    dstLowMask);
        }
      } else {
        Site* low = pickSiteOrMove(c, srcValue, dstValue, 0, 0);
        if (dstSize > c->targetInfo.pointerSize) {
          pickSiteOrMove(c, srcValue->nextWord, dstValue->nextWord, low, 1);
        }
      }
    } else if (srcSelectSize <= c->targetInfo.pointerSize
               and dstSize <= c->targetInfo.pointerSize) {
      maybeMove(c,
                op,
                srcSize,
                srcSelectSize,
                srcValue,
                dstSize,
                dstValue,
                dstLowMask);
    } else {
      assertT(c, srcSize == c->targetInfo.pointerSize);
      assertT(c, srcSelectSize == c->targetInfo.pointerSize);

      if (dstValue->nextWord->target or live(c, dstValue->nextWord)) {
        assertT(c, dstLowMask.typeMask & lir::Operand::RegisterPairMask);

        Site* low = freeRegisterSite(c, dstLowMask.registerMask);

        srcValue->source->freeze(c, srcValue);

        dstValue->addSite(c, low);

        low->freeze(c, dstValue);

        if (DebugMoves) {
          char srcb[256];
          srcValue->source->toString(c, srcb, 256);
          char dstb[256];
          low->toString(c, dstb, 256);
          fprintf(stderr, "move %s to %s for %p\n", srcb, dstb, srcValue);
        }

        apply(c,
              lir::Move,
              c->targetInfo.pointerSize,
              srcValue->source,
              srcValue->source,
              c->targetInfo.pointerSize,
              low,
              low);

        low->thaw(c, dstValue);

        srcValue->source->thaw(c, srcValue);

        assertT(c, dstHighMask.typeMask & lir::Operand::RegisterPairMask);

        Site* high = freeRegisterSite(c, dstHighMask.registerMask);

        low->freeze(c, dstValue);

        dstValue->nextWord->addSite(c, high);

        high->freeze(c, dstValue->nextWord);

        if (DebugMoves) {
          char srcb[256];
          low->toString(c, srcb, 256);
          char dstb[256];
          high->toString(c, dstb, 256);
          fprintf(stderr,
                  "extend %s to %s for %p %p\n",
                  srcb,
                  dstb,
                  dstValue,
                  dstValue->nextWord);
        }

        apply(c,
              lir::Move,
              c->targetInfo.pointerSize,
              low,
              low,
              dstSize,
              low,
              high);

        high->thaw(c, dstValue->nextWord);

        low->thaw(c, dstValue);
      } else {
        pickSiteOrMove(c, srcValue, dstValue, 0, 0);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }

  lir::BinaryOperation op;
  unsigned srcSize;
  unsigned srcSelectSize;
  Value* srcValue;
  unsigned dstSize;
  Value* dstValue;
};

void appendMove(Context* c,
                lir::BinaryOperation op,
                unsigned srcSize,
                unsigned srcSelectSize,
                Value* srcValue,
                unsigned dstSize,
                Value* dstValue)
{
  bool thunk;
  OperandMask src;

  c->arch->planSource(op, srcSelectSize, src, dstSize, &thunk);

  assertT(c, not thunk);

  append(c,
         new (c->zone) MoveEvent(c,
                                 op,
                                 srcSize,
                                 srcSelectSize,
                                 srcValue,
                                 dstSize,
                                 dstValue,
                                 SiteMask::lowPart(src),
                                 SiteMask::highPart(src)));
}

void freezeSource(Context* c, unsigned size, Value* v)
{
  v->source->freeze(c, v);
  if (size > c->targetInfo.pointerSize) {
    v->nextWord->source->freeze(c, v->nextWord);
  }
}

void thawSource(Context* c, unsigned size, Value* v)
{
  v->source->thaw(c, v);
  if (size > c->targetInfo.pointerSize) {
    v->nextWord->source->thaw(c, v->nextWord);
  }
}

Read* liveNext(Context* c, Value* v)
{
  assertT(c, v->buddy->hasBuddy(c, v));

  Read* r = v->reads->next(c);
  if (valid(r))
    return r;

  for (Value* p = v->buddy; p != v; p = p->buddy) {
    if (valid(p->reads))
      return p->reads;
  }

  return 0;
}

void preserve(Context* c, Value* v, Read* r, Site* s)
{
  s->freeze(c, v);

  maybeMove(c, r, false, true, 0);

  s->thaw(c, v);
}

Site* getTarget(Context* c,
                Value* value,
                Value* result,
                const SiteMask& resultMask)
{
  Site* s;
  Value* v;
  Read* r = liveNext(c, value);
  if (value->source->match(c, static_cast<const SiteMask&>(resultMask))
      and (r == 0
           or value->source->loneMatch(
                  c, static_cast<const SiteMask&>(resultMask)))) {
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

class CombineEvent : public Event {
 public:
  CombineEvent(Context* c,
               lir::TernaryOperation op,
               Value* firstValue,
               Value* secondValue,
               Value* resultValue,
               const SiteMask& firstLowMask,
               const SiteMask& firstHighMask,
               const SiteMask& secondLowMask,
               const SiteMask& secondHighMask)
      : Event(c),
        op(op),
        firstValue(firstValue),
        secondValue(secondValue),
        resultValue(resultValue)
  {
    this->addReads(c,
                   firstValue,
                   firstValue->type.size(c->targetInfo),
                   firstLowMask,
                   firstHighMask);

    if (resultValue->type.size(c->targetInfo) > c->targetInfo.pointerSize) {
      resultValue->grow(c);
    }

    bool condensed = c->arch->alwaysCondensed(op);

    this->addReads(c,
                   secondValue,
                   secondValue->type.size(c->targetInfo),
                   secondLowMask,
                   condensed ? resultValue : 0,
                   secondHighMask,
                   condensed ? resultValue->nextWord : 0);
  }

  virtual const char* name()
  {
    return "CombineEvent";
  }

  virtual void compile(Context* c)
  {
    assertT(
        c,
        firstValue->source->type(c) == firstValue->nextWord->source->type(c));

    if (false) {
      if (secondValue->source->type(c)
          != secondValue->nextWord->source->type(c)) {
        fprintf(stderr,
                "%p %p %d : %p %p %d\n",
                secondValue,
                secondValue->source,
                static_cast<int>(secondValue->source->type(c)),
                secondValue->nextWord,
                secondValue->nextWord->source,
                static_cast<int>(secondValue->nextWord->source->type(c)));
      }
    }

    assertT(
        c,
        secondValue->source->type(c) == secondValue->nextWord->source->type(c));

    freezeSource(c, firstValue->type.size(c->targetInfo), firstValue);

    OperandMask cMask;

    c->arch->planDestination(
        op,
        firstValue->type.size(c->targetInfo),
        OperandMask(
            1 << (unsigned)firstValue->source->type(c),
            firstValue->source->registerMask(c),
            firstValue->nextWord->source->registerMask(c)),
        secondValue->type.size(c->targetInfo),
        OperandMask(
            1 << (unsigned)secondValue->source->type(c),
            secondValue->source->registerMask(c),
            secondValue->nextWord->source->registerMask(c)),
        resultValue->type.size(c->targetInfo),
        cMask);

    SiteMask resultLowMask = SiteMask::lowPart(cMask);
    SiteMask resultHighMask = SiteMask::highPart(cMask);

    Site* low = getTarget(c, secondValue, resultValue, resultLowMask);
    unsigned lowSize = low->registerSize(c);
    Site* high = (resultValue->type.size(c->targetInfo) > lowSize
                      ? getTarget(c,
                                  secondValue->nextWord,
                                  resultValue->nextWord,
                                  resultHighMask)
                      : low);

    if (false) {
      fprintf(stderr,
              "combine %p:%p and %p:%p into %p:%p\n",
              firstValue,
              firstValue->nextWord,
              secondValue,
              secondValue->nextWord,
              resultValue,
              resultValue->nextWord);
    }

    apply(c,
          op,
          firstValue->type.size(c->targetInfo),
          firstValue->source,
          firstValue->nextWord->source,
          secondValue->type.size(c->targetInfo),
          secondValue->source,
          secondValue->nextWord->source,
          resultValue->type.size(c->targetInfo),
          low,
          high);

    thawSource(c, firstValue->type.size(c->targetInfo), firstValue);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, secondValue);
    if (resultValue->type.size(c->targetInfo) > lowSize) {
      high->thaw(c, secondValue->nextWord);
    }

    if (live(c, resultValue)) {
      resultValue->addSite(c, low);
      if (resultValue->type.size(c->targetInfo) > lowSize
          and live(c, resultValue->nextWord)) {
        resultValue->nextWord->addSite(c, high);
      }
    }
  }

  lir::TernaryOperation op;
  Value* firstValue;
  Value* secondValue;
  Value* resultValue;
};

void appendCombine(Context* c,
                   lir::TernaryOperation op,
                   Value* firstValue,
                   Value* secondValue,
                   Value* resultValue)
{
  bool thunk;
  OperandMask firstMask;
  OperandMask secondMask;
  c->arch->planSource(op,
                      firstValue->type.size(c->targetInfo),
                      firstMask,
                      secondValue->type.size(c->targetInfo),
                      secondMask,
                      resultValue->type.size(c->targetInfo),
                      &thunk);

  if (thunk) {
    const size_t MaxValueCount = 6;
    FixedSliceStack<ir::Value*, MaxValueCount> slice;
    size_t stackBase = c->stack ? c->stack->index + 1 : 0;

    bool threadParameter;
    intptr_t handler
        = c->client->getThunk(op,
                              firstValue->type.size(c->targetInfo),
                              resultValue->type.size(c->targetInfo),
                              &threadParameter);

    unsigned stackSize = ceilingDivide(secondValue->type.size(c->targetInfo),
                                       c->targetInfo.pointerSize)
                         + ceilingDivide(firstValue->type.size(c->targetInfo),
                                         c->targetInfo.pointerSize);

    slicePush(c,
              ceilingDivide(secondValue->type.size(c->targetInfo),
                            c->targetInfo.pointerSize),
              secondValue,
              stackBase,
              slice);
    slicePush(c,
              ceilingDivide(firstValue->type.size(c->targetInfo),
                            c->targetInfo.pointerSize),
              firstValue,
              stackBase,
              slice);

    if (threadParameter) {
      ++stackSize;

      slicePush(c, 1, threadRegister(c), stackBase, slice);
    }

    appendCall(c,
               value(c, ir::Type::addr(), constantSite(c, handler)),
               ir::CallingConvention::Native,
               0,
               0,
               resultValue,
               slice);
  } else {
    append(c,
           new (c->zone) CombineEvent(c,
                                      op,
                                      firstValue,
                                      secondValue,
                                      resultValue,
                                      SiteMask::lowPart(firstMask),
                                      SiteMask::highPart(firstMask),
                                      SiteMask::lowPart(secondMask),
                                      SiteMask::highPart(secondMask)));
  }
}

class TranslateEvent : public Event {
 public:
  TranslateEvent(Context* c,
                 lir::BinaryOperation op,
                 Value* firstValue,
                 Value* resultValue,
                 const SiteMask& valueLowMask,
                 const SiteMask& valueHighMask)
      : Event(c), op(op), firstValue(firstValue), resultValue(resultValue)
  {
    bool condensed = c->arch->alwaysCondensed(op);

    if (resultValue->type.size(c->targetInfo) > c->targetInfo.pointerSize) {
      resultValue->grow(c);
    }

    this->addReads(c,
                   firstValue,
                   firstValue->type.size(c->targetInfo),
                   valueLowMask,
                   condensed ? resultValue : 0,
                   valueHighMask,
                   condensed ? resultValue->nextWord : 0);
  }

  virtual const char* name()
  {
    return "TranslateEvent";
  }

  virtual void compile(Context* c)
  {
    assertT(
        c,
        firstValue->source->type(c) == firstValue->nextWord->source->type(c));

    OperandMask bMask;

    c->arch->planDestination(
        op,
        firstValue->type.size(c->targetInfo),
        OperandMask(
            1 << (unsigned)firstValue->source->type(c),
            firstValue->source->registerMask(c),
            firstValue->nextWord->source->registerMask(c)),
        resultValue->type.size(c->targetInfo),
        bMask);

    SiteMask resultLowMask = SiteMask::lowPart(bMask);
    SiteMask resultHighMask = SiteMask::highPart(bMask);

    Site* low = getTarget(c, firstValue, resultValue, resultLowMask);
    unsigned lowSize = low->registerSize(c);
    Site* high = (resultValue->type.size(c->targetInfo) > lowSize
                      ? getTarget(c,
                                  firstValue->nextWord,
                                  resultValue->nextWord,
                                  resultHighMask)
                      : low);

    apply(c,
          op,
          firstValue->type.size(c->targetInfo),
          firstValue->source,
          firstValue->nextWord->source,
          resultValue->type.size(c->targetInfo),
          low,
          high);

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }

    low->thaw(c, firstValue);
    if (resultValue->type.size(c->targetInfo) > lowSize) {
      high->thaw(c, firstValue->nextWord);
    }

    if (live(c, resultValue)) {
      resultValue->addSite(c, low);
      if (resultValue->type.size(c->targetInfo) > lowSize
          and live(c, resultValue->nextWord)) {
        resultValue->nextWord->addSite(c, high);
      }
    }
  }

  lir::BinaryOperation op;
  Value* firstValue;
  Value* resultValue;
  Read* resultRead;
  SiteMask resultLowMask;
  SiteMask resultHighMask;
};

void appendTranslate(Context* c,
                     lir::BinaryOperation op,
                     Value* firstValue,
                     Value* resultValue)
{
  assertT(c,
          firstValue->type.size(c->targetInfo)
          == firstValue->type.size(c->targetInfo));
  assertT(c,
          resultValue->type.size(c->targetInfo)
          == resultValue->type.size(c->targetInfo));

  bool thunk;
  OperandMask first;

  c->arch->planSource(op,
                      firstValue->type.size(c->targetInfo),
                      first,
                      resultValue->type.size(c->targetInfo),
                      &thunk);

  if (thunk) {
    size_t stackBase = c->stack ? c->stack->index + 1 : 0;
    FixedSliceStack<ir::Value*, 2> slice;

    slicePush(c,
              ceilingDivide(firstValue->type.size(c->targetInfo),
                            c->targetInfo.pointerSize),
              firstValue,
              stackBase,
              slice);

    appendCall(c,
               value(c,
                     ir::Type::addr(),
                     constantSite(c,
                                  c->client->getThunk(
                                      op,
                                      firstValue->type.size(c->targetInfo),
                                      resultValue->type.size(c->targetInfo)))),
               ir::CallingConvention::Native,
               0,
               0,
               resultValue,
               slice);
  } else {
    append(c,
           new (c->zone) TranslateEvent(c,
                                        op,
                                        firstValue,
                                        resultValue,
                                        SiteMask::lowPart(first),
                                        SiteMask::highPart(first)));
  }
}

class OperationEvent : public Event {
 public:
  OperationEvent(Context* c, lir::Operation op) : Event(c), op(op)
  {
  }

  virtual const char* name()
  {
    return "OperationEvent";
  }

  virtual void compile(Context* c)
  {
    c->assembler->apply(op);
  }

  lir::Operation op;
};

void appendOperation(Context* c, lir::Operation op)
{
  append(c, new (c->zone) OperationEvent(c, op));
}

ConstantSite* findConstantSite(Context* c, Value* v)
{
  for (SiteIterator it(c, v); it.hasMore();) {
    Site* s = it.next();
    if (s->type(c) == lir::Operand::Type::Constant) {
      return static_cast<ConstantSite*>(s);
    }
  }
  return 0;
}

void moveIfConflict(Context* c, Value* v, MemorySite* s)
{
  if (v->reads) {
    SiteMask mask(lir::Operand::RegisterPairMask, ~0, AnyFrameIndex);
    v->reads->intersect(&mask);
    if (s->conflicts(mask)) {
      maybeMove(c, v->reads, true, false);
      v->removeSite(c, s);
    }
  }
}

class MemoryEvent : public Event {
 public:
  MemoryEvent(Context* c,
              Value* base,
              int displacement,
              Value* index,
              unsigned scale,
              Value* result)
      : Event(c),
        base(base),
        displacement(displacement),
        index(index),
        scale(scale),
        result(result)
  {
    this->addRead(c, base, generalRegisterMask(c));
    if (index) {
      this->addRead(c, index, generalRegisterOrConstantMask(c));
    }
  }

  virtual const char* name()
  {
    return "MemoryEvent";
  }

  virtual void compile(Context* c)
  {
    Register indexRegister;
    int displacement = this->displacement;
    unsigned scale = this->scale;
    if (index) {
      ConstantSite* constant = findConstantSite(c, index);

      if (constant) {
        indexRegister = NoRegister;
        displacement += (constant->value->value() * scale);
        scale = 1;
      } else {
        assertT(c, index->source->type(c) == lir::Operand::Type::RegisterPair);
        indexRegister = static_cast<RegisterSite*>(index->source)->number;
      }
    } else {
      indexRegister = NoRegister;
    }
    assertT(c, base->source->type(c) == lir::Operand::Type::RegisterPair);
    Register baseRegister = static_cast<RegisterSite*>(base->source)->number;

    popRead(c, this, base);
    if (index) {
      if (c->targetInfo.pointerSize == 8 and indexRegister != NoRegister) {
        apply(c,
              lir::Move,
              4,
              index->source,
              index->source,
              8,
              index->source,
              index->source);
      }

      popRead(c, this, index);
    }

    MemorySite* site
        = memorySite(c, baseRegister, displacement, indexRegister, scale);

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

void appendMemory(Context* c,
                  Value* base,
                  int displacement,
                  Value* index,
                  unsigned scale,
                  Value* result)
{
  append(c,
         new (c->zone)
         MemoryEvent(c, base, displacement, index, scale, result));
}

double asFloat(unsigned size, int64_t v)
{
  if (size == 4) {
    return vm::bitsToFloat(v);
  } else {
    return vm::bitsToDouble(v);
  }
}

bool unordered(double a, double b)
{
  return not(a >= b or a < b);
}

bool shouldJump(Context* c,
                lir::TernaryOperation op,
                unsigned size,
                int64_t b,
                int64_t a)
{
  switch (op) {
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

lir::TernaryOperation thunkBranch(Context* c, lir::TernaryOperation op)
{
  switch (op) {
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

class BranchEvent : public Event {
 public:
  BranchEvent(Context* c,
              lir::TernaryOperation op,
              Value* firstValue,
              Value* secondValue,
              Value* addressValue,
              const SiteMask& firstLowMask,
              const SiteMask& firstHighMask,
              const SiteMask& secondLowMask,
              const SiteMask& secondHighMask)
      : Event(c),
        op(op),
        firstValue(firstValue),
        secondValue(secondValue),
        addressValue(addressValue)
  {
    this->addReads(c,
                   firstValue,
                   firstValue->type.size(c->targetInfo),
                   firstLowMask,
                   firstHighMask);
    this->addReads(c,
                   secondValue,
                   firstValue->type.size(c->targetInfo),
                   secondLowMask,
                   secondHighMask);

    OperandMask dstMask;
    c->arch->planDestination(op,
                             firstValue->type.size(c->targetInfo),
                             OperandMask(0, 0, 0),
                             firstValue->type.size(c->targetInfo),
                             OperandMask(0, 0, 0),
                             c->targetInfo.pointerSize,
                             dstMask);

    this->addRead(c, addressValue, SiteMask::lowPart(dstMask));
  }

  virtual const char* name()
  {
    return "BranchEvent";
  }

  virtual void compile(Context* c)
  {
    ConstantSite* firstConstant = findConstantSite(c, firstValue);
    ConstantSite* secondConstant = findConstantSite(c, secondValue);

    if (not this->isUnreachable()) {
      if (firstConstant and secondConstant and firstConstant->value->resolved()
          and secondConstant->value->resolved()) {
        int64_t firstConstVal = firstConstant->value->value();
        int64_t secondConstVal = secondConstant->value->value();

        if (firstValue->type.size(c->targetInfo) > c->targetInfo.pointerSize) {
          firstConstVal
              |= findConstantSite(c, firstValue->nextWord)->value->value()
                 << 32;
          secondConstVal
              |= findConstantSite(c, secondValue->nextWord)->value->value()
                 << 32;
        }

        if (shouldJump(c,
                       op,
                       firstValue->type.size(c->targetInfo),
                       firstConstVal,
                       secondConstVal)) {
          apply(c,
                lir::Jump,
                c->targetInfo.pointerSize,
                addressValue->source,
                addressValue->source);
        }
      } else {
        freezeSource(c, firstValue->type.size(c->targetInfo), firstValue);
        freezeSource(c, firstValue->type.size(c->targetInfo), secondValue);
        freezeSource(c, c->targetInfo.pointerSize, addressValue);

        apply(c,
              op,
              firstValue->type.size(c->targetInfo),
              firstValue->source,
              firstValue->nextWord->source,
              firstValue->type.size(c->targetInfo),
              secondValue->source,
              secondValue->nextWord->source,
              c->targetInfo.pointerSize,
              addressValue->source,
              addressValue->source);

        thawSource(c, c->targetInfo.pointerSize, addressValue);
        thawSource(c, firstValue->type.size(c->targetInfo), secondValue);
        thawSource(c, firstValue->type.size(c->targetInfo), firstValue);
      }
    }

    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }

  virtual bool isBranch()
  {
    return true;
  }

  lir::TernaryOperation op;
  Value* firstValue;
  Value* secondValue;
  Value* addressValue;
};

void appendBranch(Context* c,
                  lir::TernaryOperation op,
                  Value* firstValue,
                  Value* secondValue,
                  Value* addressValue)
{
  bool thunk;
  OperandMask firstMask;
  OperandMask secondMask;

  c->arch->planSource(op,
                      firstValue->type.size(c->targetInfo),
                      firstMask,
                      firstValue->type.size(c->targetInfo),
                      secondMask,
                      c->targetInfo.pointerSize,
                      &thunk);

  if (thunk) {
    const size_t MaxValueCount = 4;
    FixedSliceStack<ir::Value*, MaxValueCount> slice;
    size_t stackBase = c->stack ? c->stack->index + 1 : 0;

    bool threadParameter;
    intptr_t handler = c->client->getThunk(op,
                                           firstValue->type.size(c->targetInfo),
                                           firstValue->type.size(c->targetInfo),
                                           &threadParameter);

    assertT(c, not threadParameter);

    slicePush(c,
              ceilingDivide(firstValue->type.size(c->targetInfo),
                            c->targetInfo.pointerSize),
              secondValue,
              stackBase,
              slice);
    slicePush(c,
              ceilingDivide(firstValue->type.size(c->targetInfo),
                            c->targetInfo.pointerSize),
              firstValue,
              stackBase,
              slice);

    Value* result = value(c, ir::Type::addr());
    appendCall(c,
               value(c, ir::Type::addr(), constantSite(c, handler)),
               ir::CallingConvention::Native,
               0,
               0,
               result,
               slice);

    appendBranch(
        c,
        thunkBranch(c, op),
        value(c, ir::Type::addr(), constantSite(c, static_cast<int64_t>(0))),
        result,
        addressValue);
  } else {
    append(c,
           new (c->zone) BranchEvent(c,
                                     op,
                                     firstValue,
                                     secondValue,
                                     addressValue,
                                     SiteMask::lowPart(firstMask),
                                     SiteMask::highPart(firstMask),
                                     SiteMask::lowPart(secondMask),
                                     SiteMask::highPart(secondMask)));
  }
}

void clean(Context* c, Value* v, unsigned popIndex)
{
  for (SiteIterator it(c, v); it.hasMore();) {
    Site* s = it.next();
    if (not(s->match(c, SiteMask(lir::Operand::MemoryMask, 0, AnyFrameIndex))
            and offsetToFrameIndex(c, static_cast<MemorySite*>(s)->offset)
                >= popIndex)) {
      if (false
          and s->match(c,
                       SiteMask(lir::Operand::MemoryMask, 0, AnyFrameIndex))) {
        char buffer[256];
        s->toString(c, buffer, 256);
        fprintf(stderr,
                "remove %s from %p at %d pop offset 0x%x\n",
                buffer,
                v,
                offsetToFrameIndex(c, static_cast<MemorySite*>(s)->offset),
                frameIndexToOffset(c, popIndex));
      }
      it.remove(c);
    }
  }
}

void clean(Context* c,
           Event* e,
           Stack* stack,
           Local* locals,
           Read* reads,
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

class JumpEvent : public Event {
 public:
  JumpEvent(Context* c,
            lir::UnaryOperation op,
            Value* address,
            bool exit,
            bool cleanLocals)
      : Event(c), op(op), address(address), exit(exit), cleanLocals(cleanLocals)
  {
    bool thunk;
    OperandMask mask;
    c->arch->plan(op, c->targetInfo.pointerSize, mask, &thunk);

    assertT(c, not thunk);

    this->addRead(c, address, SiteMask::lowPart(mask));
  }

  virtual const char* name()
  {
    return "JumpEvent";
  }

  virtual void compile(Context* c)
  {
    if (not this->isUnreachable()) {
      apply(c, op, c->targetInfo.pointerSize, address->source, address->source);
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

  virtual bool isBranch()
  {
    return true;
  }

  virtual bool allExits()
  {
    return exit or this->isUnreachable();
  }

  lir::UnaryOperation op;
  Value* address;
  bool exit;
  bool cleanLocals;
};

void appendJump(Context* c,
                lir::UnaryOperation op,
                Value* address,
                bool exit,
                bool cleanLocals)
{
  append(c, new (c->zone) JumpEvent(c, op, address, exit, cleanLocals));
}

class BoundsCheckEvent : public Event {
 public:
  BoundsCheckEvent(Context* c,
                   Value* object,
                   unsigned lengthOffset,
                   Value* index,
                   intptr_t handler)
      : Event(c),
        object(object),
        lengthOffset(lengthOffset),
        index(index),
        handler(handler)
  {
    this->addRead(c, object, generalRegisterMask(c));
    this->addRead(c, index, generalRegisterOrConstantMask(c));
  }

  virtual const char* name()
  {
    return "BoundsCheckEvent";
  }

  virtual void compile(Context* c)
  {
    Assembler* a = c->assembler;

    ConstantSite* constant = findConstantSite(c, index);
    CodePromise* outOfBoundsPromise = 0;

    if (constant) {
      if (constant->value->value() < 0) {
        lir::Constant handlerConstant(resolvedPromise(c, handler));
        a->apply(lir::Call,
                 OperandInfo(c->targetInfo.pointerSize,
                             lir::Operand::Type::Constant,
                             &handlerConstant));
      }
    } else {
      outOfBoundsPromise = compiler::codePromise(c, static_cast<Promise*>(0));

      ConstantSite zero(resolvedPromise(c, 0));
      ConstantSite oob(outOfBoundsPromise);
      apply(c,
            lir::JumpIfLess,
            4,
            &zero,
            &zero,
            4,
            index->source,
            index->source,
            c->targetInfo.pointerSize,
            &oob,
            &oob);
    }

    if (constant == 0 or constant->value->value() >= 0) {
      assertT(c, object->source->type(c) == lir::Operand::Type::RegisterPair);
      MemorySite length(static_cast<RegisterSite*>(object->source)->number,
                        lengthOffset,
                        NoRegister,
                        1);
      length.acquired = true;

      CodePromise* nextPromise
          = compiler::codePromise(c, static_cast<Promise*>(0));

      freezeSource(c, c->targetInfo.pointerSize, index);

      ConstantSite next(nextPromise);
      apply(c,
            lir::JumpIfGreater,
            4,
            index->source,
            index->source,
            4,
            &length,
            &length,
            c->targetInfo.pointerSize,
            &next,
            &next);

      thawSource(c, c->targetInfo.pointerSize, index);

      if (constant == 0) {
        outOfBoundsPromise->offset = a->offset();
      }

      lir::Constant handlerConstant(resolvedPromise(c, handler));
      a->apply(lir::Call,
               OperandInfo(c->targetInfo.pointerSize,
                           lir::Operand::Type::Constant,
                           &handlerConstant));

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

void appendBoundsCheck(Context* c,
                       Value* object,
                       unsigned lengthOffset,
                       Value* index,
                       intptr_t handler)
{
  append(c,
         new (c->zone)
         BoundsCheckEvent(c, object, lengthOffset, index, handler));
}

class FrameSiteEvent : public Event {
 public:
  FrameSiteEvent(Context* c, Value* value, int index)
      : Event(c), value(value), index(index)
  {
  }

  virtual const char* name()
  {
    return "FrameSiteEvent";
  }

  virtual void compile(Context* c)
  {
    if (live(c, value)) {
      value->addSite(c, frameSite(c, index));
    }
  }

  Value* value;
  int index;
};

void appendFrameSite(Context* c, Value* value, int index)
{
  append(c, new (c->zone) FrameSiteEvent(c, value, index));
}

class SaveLocalsEvent : public Event {
 public:
  SaveLocalsEvent(Context* c) : Event(c)
  {
    saveLocals(c, this);
  }

  virtual const char* name()
  {
    return "SaveLocalsEvent";
  }

  virtual void compile(Context* c)
  {
    for (Read* r = reads; r; r = r->eventNext) {
      popRead(c, this, r->value);
    }
  }
};

void appendSaveLocals(Context* c)
{
  append(c, new (c->zone) SaveLocalsEvent(c));
}

class DummyEvent : public Event {
 public:
  DummyEvent(Context* c, Local* locals) : Event(c), locals_(locals)
  {
  }

  virtual const char* name()
  {
    return "DummyEvent";
  }

  virtual void compile(Context*)
  {
  }

  virtual Local* locals()
  {
    return locals_;
  }

  Local* locals_;
};

void appendDummy(Context* c)
{
  Stack* stack = c->stack;
  Local* locals = c->locals;
  LogicalInstruction* i = c->logicalCode[c->logicalIp];

  c->stack = i->stack;
  c->locals = i->locals;

  append(c, new (c->zone) DummyEvent(c, locals));

  c->stack = stack;
  c->locals = locals;
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
