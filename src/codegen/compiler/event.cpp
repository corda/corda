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
#include "codegen/compiler/stack.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/read.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/promise.h"

namespace avian {
namespace codegen {
namespace compiler {


unsigned frameBase(Context* c);
unsigned totalFrameSize(Context* c);
int frameIndex(Context* c, int localIndex);

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
pickSiteOrMove(Context* c, Value* src, Value* dst, Site* nextWord,
               unsigned index);

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

} // namespace compiler
} // namespace codegen
} // namespace avian
