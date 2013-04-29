/* Copyright (c) 2009-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/vm/codegen/assembler.h>
#include <avian/vm/codegen/architecture.h>
#include <avian/vm/codegen/registers.h>

#include "avian/alloc-vector.h"
#include <avian/util/abort.h>

#include "encode.h"
#include "context.h"
#include "fixup.h"
#include "block.h"
#include "operations.h"
#include "multimethod.h"
#include "../multimethod.h"

using namespace vm;
using namespace avian::util;

namespace avian {
namespace codegen {
namespace powerpc {

inline int unha16(int32_t high, int32_t low) {
    return ((high - ((low & 0x8000) ? 1 : 0)) << 16) | low; 
}

const RegisterFile MyRegisterFile(0xFFFFFFFF, 0);

#ifdef __APPLE__
const unsigned FrameFooterSize = 6;
const unsigned ReturnAddressOffset = 2;
const unsigned AlignArguments = false;
#else
const unsigned FrameFooterSize = 2;
const unsigned ReturnAddressOffset = 1;
const unsigned AlignArguments = true;
#endif

const unsigned StackAlignmentInBytes = 16;
const unsigned StackAlignmentInWords
= StackAlignmentInBytes / TargetBytesPerWord;

const int StackRegister = 1;
const int ThreadRegister = 13;

const bool DebugJumps = false;

class JumpOffset;

unsigned padding(MyBlock*, unsigned);

bool bounded(int right, int left, int32_t v);

class Task;
class ConstantPoolEntry;

bool
needJump(MyBlock* b)
{
  return b->next or (not bounded(2, 16, b->size));
}

unsigned
padding(MyBlock* b, unsigned offset)
{
  unsigned total = 0;
  for (JumpEvent* e = b->jumpEventHead; e; e = e->next) {
    if (e->offset <= offset) {
      for (JumpOffset* o = e->jumpOffsetHead; o; o = o->next) {
        total += TargetBytesPerWord;
      }

      if (needJump(b)) {
        total += TargetBytesPerWord;
      }
    } else {
      break;
    }
  }

  return total;
}

void
resolve(MyBlock* b)
{
  Context* c = b->context;

  for (JumpEvent** e = &(b->jumpEventHead); *e;) {
    for (JumpOffset** o = &((*e)->jumpOffsetHead); *o;) {
      if ((*o)->task->promise->resolved()
          and (*o)->task->instructionOffset->resolved())
      {
        int32_t v = reinterpret_cast<uint8_t*>((*o)->task->promise->value())
          - (c->result + (*o)->task->instructionOffset->value());

        if (bounded(2, 16, v)) {
          // this conditional jump needs no indirection -- a direct
          // jump will suffice
          *o = (*o)->next;
          continue;
        }
      }

      o = &((*o)->next);
    }

    if ((*e)->jumpOffsetHead == 0) {
      *e = (*e)->next;
    } else {
      e = &((*e)->next);
    }
  }

  if (b->jumpOffsetHead) {
    if (c->jumpOffsetTail) {
      c->jumpOffsetTail->next = b->jumpOffsetHead;
    } else {
      c->jumpOffsetHead = b->jumpOffsetHead;
    }
    c->jumpOffsetTail = b->jumpOffsetTail;
  }

  if (c->jumpOffsetHead) {
    bool append;
    if (b->next == 0 or b->next->jumpEventHead) {
      append = true;
    } else {
      int32_t v = (b->start + b->size + b->next->size + TargetBytesPerWord)
        - (c->jumpOffsetHead->offset + c->jumpOffsetHead->block->start);

      append = not bounded(2, 16, v);

      if (DebugJumps) {
        fprintf(stderr,
                "current %p %d %d next %p %d %d\n",
                b, b->start, b->size, b->next, b->start + b->size,
                b->next->size);
        fprintf(stderr,
                "offset %p %d is of distance %d to next block; append? %d\n",
                c->jumpOffsetHead, c->jumpOffsetHead->offset, v, append);
      }
    }

    if (append) {
#ifndef NDEBUG
      int32_t v = (b->start + b->size)
        - (c->jumpOffsetHead->offset + c->jumpOffsetHead->block->start);
      
      expect(c, bounded(2, 16, v));
#endif // not NDEBUG

      appendJumpEvent(c, b, b->size, c->jumpOffsetHead, c->jumpOffsetTail);

      if (DebugJumps) {
        for (JumpOffset* o = c->jumpOffsetHead; o; o = o->next) {
          fprintf(stderr,
                  "include %p %d in jump event %p at offset %d in block %p\n",
                  o, o->offset, b->jumpEventTail, b->size, b);
        }
      }

      c->jumpOffsetHead = 0;
      c->jumpOffsetTail = 0;
    }
  }
}

// BEGIN OPERATION COMPILERS

using namespace isa;

// END OPERATION COMPILERS

unsigned
argumentFootprint(unsigned footprint)
{
  return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
}

void
nextFrame(ArchitectureContext* c UNUSED, int32_t* start, unsigned size,
          unsigned footprint, void* link, bool,
          unsigned targetParameterFootprint, void** ip, void** stack)
{
  assert(c, *ip >= start);
  assert(c, *ip <= start + (size / BytesPerWord));

  int32_t* instruction = static_cast<int32_t*>(*ip);

  if ((*start >> 26) == 32) {
    // skip stack overflow check
    start += 3;
  }

  if (instruction <= start + 2
      or *instruction == lwz(0, 1, 8)
      or *instruction == mtlr(0)
      or *instruction == blr())
  {
    *ip = link;
    return;
  }

  unsigned offset = footprint;

  if (TailCalls) {
    if (argumentFootprint(targetParameterFootprint) > StackAlignmentInWords) {
      offset += argumentFootprint(targetParameterFootprint)
        - StackAlignmentInWords;
    }

    // check for post-non-tail-call stack adjustment of the form "lwzx
    // r0,0(r1); stwu r0,offset(r1)":
    if (instruction < start + (size / BytesPerWord) - 1
        and (static_cast<uint32_t>(instruction[1]) >> 16) == 0x9401)
    {
      offset += static_cast<int16_t>(instruction[1]) / BytesPerWord;
    } else if ((static_cast<uint32_t>(*instruction) >> 16) == 0x9401) {
      offset += static_cast<int16_t>(*instruction) / BytesPerWord;
    }

    // todo: check for and handle tail calls
  }

  *ip = static_cast<void**>(*stack)[offset + ReturnAddressOffset];
  *stack = static_cast<void**>(*stack) + offset;
}

class MyArchitecture: public Architecture {
 public:
  MyArchitecture(System* system): c(system), referenceCount(0) {
    populateTables(&c);
  }

  virtual unsigned floatRegisterSize() {
    return 0;
  }

  virtual const RegisterFile* registerFile() {
    return &MyRegisterFile;
  }

  virtual int scratch() {
    return 31;
  }

  virtual int stack() {
    return StackRegister;
  }

  virtual int thread() {
    return ThreadRegister;
  }

  virtual int returnLow() {
    return 4;
  }

  virtual int returnHigh() {
    return (TargetBytesPerWord == 4 ? 3 : lir::NoRegister);
  }

  virtual int virtualCallTarget() {
    return 4;
  }

  virtual int virtualCallIndex() {
    return 3;
  }

  virtual bool bigEndian() {
    return true;
  }

  virtual uintptr_t maximumImmediateJump() {
    return 0x1FFFFFF;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case 0: // r0 has special meaning in addi and other instructions
    case StackRegister:
    case ThreadRegister:
#ifndef __APPLE__
      // r2 is reserved for system uses on SYSV
    case 2:
#endif
      return true;

    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint) {
    return max(footprint, StackAlignmentInWords);
  }

  virtual unsigned argumentFootprint(unsigned footprint) {
    return powerpc::argumentFootprint(footprint);
  }

  virtual bool argumentAlignment() {
    return AlignArguments;
  }

  virtual bool argumentRegisterAlignment() {
    return true;
  }

  virtual unsigned argumentRegisterCount() {
    return 8;
  }

  virtual int argumentRegister(unsigned index) {
    assert(&c, index < argumentRegisterCount());

    return index + 3;
  }

  virtual bool hasLinkRegister() {
    return true;
  }
  
  virtual unsigned stackAlignmentInWords() {
    return StackAlignmentInWords;
  }

  virtual bool matchCall(void* returnAddress, void* target) {
    uint32_t* instruction = static_cast<uint32_t*>(returnAddress) - 1;

    return *instruction == static_cast<uint32_t>
      (bl(static_cast<uint8_t*>(target)
          - reinterpret_cast<uint8_t*>(instruction)));
  }

  virtual void updateCall(lir::UnaryOperation op UNUSED,
                          void* returnAddress,
                          void* newTarget)
  {
    switch (op) {
    case lir::Call:
    case lir::Jump:
    case lir::AlignedCall:
    case lir::AlignedJump: {
      updateOffset(c.s, static_cast<uint8_t*>(returnAddress) - 4, false,
                   reinterpret_cast<intptr_t>(newTarget), 0);
    } break;

    case lir::LongCall:
    case lir::LongJump: {
      updateImmediate
        (c.s, static_cast<uint8_t*>(returnAddress) - 12,
         reinterpret_cast<intptr_t>(newTarget), TargetBytesPerWord, false);
    } break;

    case lir::AlignedLongCall:
    case lir::AlignedLongJump: {
      uint32_t* p = static_cast<uint32_t*>(returnAddress) - 4;
      *reinterpret_cast<void**>(unha16(p[0] & 0xFFFF, p[1] & 0xFFFF))
        = newTarget;
    } break;

    default: abort(&c);
    }
  }

  virtual unsigned constantCallSize() {
    return 4;
  }

  virtual void setConstant(void* dst, uint64_t constant) {
    updateImmediate(c.s, dst, constant, TargetBytesPerWord, false);
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    const unsigned alignment = StackAlignmentInWords;
    return (ceilingDivide(sizeInWords + FrameFooterSize, alignment) * alignment);
  }

  virtual void nextFrame(void* start, unsigned size, unsigned footprint,
                         void* link, bool mostRecent,
                         unsigned targetParameterFootprint, void** ip,
                         void** stack)
  {
    powerpc::nextFrame(&c, static_cast<int32_t*>(start), size, footprint, link,
                mostRecent, targetParameterFootprint, ip, stack);
  }

  virtual void* frameIp(void* stack) {
    return stack ? static_cast<void**>(stack)[ReturnAddressOffset] : 0;
  }

  virtual unsigned frameHeaderSize() {
    return 0;
  }

  virtual unsigned frameReturnAddressSize() {
    return 0;
  }

  virtual unsigned frameFooterSize() {
    return FrameFooterSize;
  }

  virtual int returnAddressOffset() {
    return ReturnAddressOffset;
  }

  virtual int framePointerOffset() {
    return 0;
  }
  
  virtual bool alwaysCondensed(lir::BinaryOperation) {
    return false;
  }
  
  virtual bool alwaysCondensed(lir::TernaryOperation) {
    return false;
  }
  
  virtual void plan
  (lir::UnaryOperation,
   unsigned, OperandMask& aMask,
   bool* thunk)
  {
    aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
    aMask.registerMask = ~static_cast<uint64_t>(0);
    *thunk = false;
  }

  virtual void planSource
  (lir::BinaryOperation op,
   unsigned, OperandMask& aMask,
   unsigned, bool* thunk)
  {
    aMask.typeMask = ~0;
    aMask.registerMask = ~static_cast<uint64_t>(0);

    *thunk = false;

    switch (op) {
    case lir::Negate:
      aMask.typeMask = (1 << lir::RegisterOperand);
      break;

    case lir::Absolute:
    case lir::FloatAbsolute:
    case lir::FloatSquareRoot:
    case lir::FloatNegate:
    case lir::Float2Float:
    case lir::Float2Int:
    case lir::Int2Float:
      *thunk = true;
      break;

    default:
      break;
    }
  }
  
  virtual void planDestination
  (lir::BinaryOperation op,
   unsigned, const OperandMask& aMask UNUSED,
   unsigned, OperandMask& bMask)
  {
    bMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
    bMask.registerMask = ~static_cast<uint64_t>(0);

    switch (op) {
    case lir::Negate:
      bMask.typeMask = (1 << lir::RegisterOperand);
      break;

    default:
      break;
    }
  }

  virtual void planMove
  (unsigned, OperandMask& srcMask,
   OperandMask& tmpMask,
   const OperandMask& dstMask)
  {
    srcMask.typeMask = ~0;
    srcMask.registerMask = ~static_cast<uint64_t>(0);

    tmpMask.typeMask = 0;
    tmpMask.registerMask = 0;

    if (dstMask.typeMask & (1 << lir::MemoryOperand)) {
      // can't move directly from memory or constant to memory
      srcMask.typeMask = 1 << lir::RegisterOperand;
      tmpMask.typeMask = 1 << lir::RegisterOperand;
      tmpMask.registerMask = ~static_cast<uint64_t>(0);
    }
  }

  virtual void planSource
  (lir::TernaryOperation op,
   unsigned aSize, OperandMask& aMask,
   unsigned, OperandMask& bMask,
   unsigned, bool* thunk)
  {
    aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
    aMask.registerMask = ~static_cast<uint64_t>(0);

    bMask.typeMask = (1 << lir::RegisterOperand);
    bMask.registerMask = ~static_cast<uint64_t>(0);

    *thunk = false;

    switch (op) {
    case lir::Add:
    case lir::Subtract:
      if (aSize == 8) {
        aMask.typeMask = bMask.typeMask = (1 << lir::RegisterOperand);
      }
      break;

    case lir::Multiply:
      aMask.typeMask = bMask.typeMask = (1 << lir::RegisterOperand);
      break;

    case lir::Divide:
    case lir::Remainder:
      // todo: we shouldn't need to defer to thunks for integers which
      // are smaller than or equal to tne native word size, but
      // PowerPC doesn't generate traps for divide by zero, so we'd
      // need to do the checks ourselves.  Using an inline check
      // should be faster than calling an out-of-line thunk, but the
      // thunk is easier, so they's what we do for now.
      if (true) {//if (TargetBytesPerWord == 4 and aSize == 8) {
        *thunk = true;        
      } else {
        aMask.typeMask = (1 << lir::RegisterOperand);
      }
      break;

    case lir::FloatAdd:
    case lir::FloatSubtract:
    case lir::FloatMultiply:
    case lir::FloatDivide:
    case lir::FloatRemainder:
    case lir::JumpIfFloatEqual:
    case lir::JumpIfFloatNotEqual:
    case lir::JumpIfFloatLess:
    case lir::JumpIfFloatGreater:
    case lir::JumpIfFloatLessOrEqual:
    case lir::JumpIfFloatGreaterOrEqual:
    case lir::JumpIfFloatLessOrUnordered:
    case lir::JumpIfFloatGreaterOrUnordered:
    case lir::JumpIfFloatLessOrEqualOrUnordered:
    case lir::JumpIfFloatGreaterOrEqualOrUnordered:
      *thunk = true;
      break;

    default:
      break;
    }
  }

  virtual void planDestination
  (lir::TernaryOperation op,
   unsigned, const OperandMask& aMask UNUSED,
   unsigned, const OperandMask& bMask UNUSED,
   unsigned, OperandMask& cMask)
  {
    if (isBranch(op)) {
      cMask.typeMask = (1 << lir::ConstantOperand);
      cMask.registerMask = 0;
    } else {
      cMask.typeMask = (1 << lir::RegisterOperand);
      cMask.registerMask = ~static_cast<uint64_t>(0);
    }
  }

  virtual Assembler* makeAssembler(Allocator* allocator, Zone* zone);

  virtual void acquire() {
    ++ referenceCount;
  }

  virtual void release() {
    if (-- referenceCount == 0) {
      c.s->free(this);
    }
  }

  ArchitectureContext c;
  unsigned referenceCount;
};

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s, Allocator* a, Zone* zone, MyArchitecture* arch):
    c(s, a, zone), arch_(arch)
  { }

  virtual void setClient(Client* client) {
    assert(&c, c.client == 0);
    c.client = client;
  }

  virtual Architecture* arch() {
    return arch_;
  }

  virtual void checkStackOverflow(uintptr_t handler,
                                  unsigned stackLimitOffsetFromThread)
  {
    lir::Register stack(StackRegister);
    lir::Memory stackLimit(ThreadRegister, stackLimitOffsetFromThread);
    lir::Constant handlerConstant
      (new(c.zone) ResolvedPromise(handler));
    branchRM(&c, lir::JumpIfGreaterOrEqual, TargetBytesPerWord, &stack, &stackLimit,
             &handlerConstant);
  }

  virtual void saveFrame(unsigned stackOffset, unsigned) {
    lir::Register returnAddress(0);
    emit(&c, mflr(returnAddress.low));

    lir::Memory returnAddressDst
      (StackRegister, ReturnAddressOffset * TargetBytesPerWord);
    moveRM(&c, TargetBytesPerWord, &returnAddress, TargetBytesPerWord,
           &returnAddressDst);

    lir::Register stack(StackRegister);
    lir::Memory stackDst(ThreadRegister, stackOffset);
    moveRM(&c, TargetBytesPerWord, &stack, TargetBytesPerWord, &stackDst);
  }

  virtual void pushFrame(unsigned argumentCount, ...) {
    struct {
      unsigned size;
      lir::OperandType type;
      lir::Operand* operand;
    } arguments[argumentCount];

    va_list a; va_start(a, argumentCount);
    unsigned footprint = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      arguments[i].size = va_arg(a, unsigned);
      arguments[i].type = static_cast<lir::OperandType>(va_arg(a, int));
      arguments[i].operand = va_arg(a, lir::Operand*);
      footprint += ceilingDivide(arguments[i].size, TargetBytesPerWord);
    }
    va_end(a);

    allocateFrame(arch_->alignFrameSize(footprint));
    
    unsigned offset = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      if (i < arch_->argumentRegisterCount()) {
        lir::Register dst(arch_->argumentRegister(i));

        apply(lir::Move,
              OperandInfo(arguments[i].size, arguments[i].type, arguments[i].operand),
              OperandInfo(pad(arguments[i].size, TargetBytesPerWord), lir::RegisterOperand,
                &dst));

        offset += ceilingDivide(arguments[i].size, TargetBytesPerWord);
      } else {
        lir::Memory dst
          (ThreadRegister, (offset + FrameFooterSize) * TargetBytesPerWord);

        apply(lir::Move,
              OperandInfo(arguments[i].size, arguments[i].type, arguments[i].operand),
              OperandInfo(pad(arguments[i].size, TargetBytesPerWord), lir::MemoryOperand, &dst));

        offset += ceilingDivide(arguments[i].size, TargetBytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint) {
    lir::Register returnAddress(0);
    emit(&c, mflr(returnAddress.low));

    lir::Memory returnAddressDst
      (StackRegister, ReturnAddressOffset * TargetBytesPerWord);
    moveRM(&c, TargetBytesPerWord, &returnAddress, TargetBytesPerWord,
           &returnAddressDst);

    lir::Register stack(StackRegister);
    lir::Memory stackDst(StackRegister, -footprint * TargetBytesPerWord);
    moveAndUpdateRM
      (&c, TargetBytesPerWord, &stack, TargetBytesPerWord, &stackDst);
  }

  virtual void adjustFrame(unsigned difference) {
    lir::Register nextStack(0);
    lir::Memory stackSrc(StackRegister, 0);
    moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &nextStack);

    lir::Memory stackDst(StackRegister, -difference * TargetBytesPerWord);
    moveAndUpdateRM
      (&c, TargetBytesPerWord, &nextStack, TargetBytesPerWord, &stackDst);
  }

  virtual void popFrame(unsigned) {
    lir::Register stack(StackRegister);
    lir::Memory stackSrc(StackRegister, 0);
    moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &stack);

    lir::Register returnAddress(0);
    lir::Memory returnAddressSrc
      (StackRegister, ReturnAddressOffset * TargetBytesPerWord);
    moveMR(&c, TargetBytesPerWord, &returnAddressSrc, TargetBytesPerWord,
           &returnAddress);
    
    emit(&c, mtlr(returnAddress.low));
  }

  virtual void popFrameForTailCall(unsigned footprint,
                                   int offset,
                                   int returnAddressSurrogate,
                                   int framePointerSurrogate)
  {
    if (TailCalls) {
      if (offset) {
        lir::Register tmp(0);
        lir::Memory returnAddressSrc
          (StackRegister, (ReturnAddressOffset + footprint)
           * TargetBytesPerWord);
        moveMR(&c, TargetBytesPerWord, &returnAddressSrc, TargetBytesPerWord,
               &tmp);
    
        emit(&c, mtlr(tmp.low));

        lir::Memory stackSrc(StackRegister, footprint * TargetBytesPerWord);
        moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &tmp);

        lir::Memory stackDst
          (StackRegister, (footprint - offset) * TargetBytesPerWord);
        moveAndUpdateRM
          (&c, TargetBytesPerWord, &tmp, TargetBytesPerWord, &stackDst);

        if (returnAddressSurrogate != lir::NoRegister) {
          assert(&c, offset > 0);

          lir::Register ras(returnAddressSurrogate);
          lir::Memory dst
            (StackRegister, (ReturnAddressOffset + offset)
             * TargetBytesPerWord);
          moveRM(&c, TargetBytesPerWord, &ras, TargetBytesPerWord, &dst);
        }

        if (framePointerSurrogate != lir::NoRegister) {
          assert(&c, offset > 0);

          lir::Register fps(framePointerSurrogate);
          lir::Memory dst(StackRegister, offset * TargetBytesPerWord);
          moveRM(&c, TargetBytesPerWord, &fps, TargetBytesPerWord, &dst);
        }
      } else {
        popFrame(footprint);
      }
    } else {
      abort(&c);
    }
  }

  virtual void popFrameAndPopArgumentsAndReturn(unsigned frameFootprint,
                                                unsigned argumentFootprint)
  {
    popFrame(frameFootprint);

    assert(&c, argumentFootprint >= StackAlignmentInWords);
    assert(&c, (argumentFootprint % StackAlignmentInWords) == 0);

    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      lir::Register tmp(0);
      lir::Memory stackSrc(StackRegister, 0);
      moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &tmp);

      lir::Memory stackDst(StackRegister,
                      (argumentFootprint - StackAlignmentInWords)
                      * TargetBytesPerWord);
      moveAndUpdateRM
        (&c, TargetBytesPerWord, &tmp, TargetBytesPerWord, &stackDst);
    }

    return_(&c);
  }

  virtual void popFrameAndUpdateStackAndReturn(unsigned frameFootprint,
                                               unsigned stackOffsetFromThread)
  {
    popFrame(frameFootprint);

    lir::Register tmp1(0);
    lir::Memory stackSrc(StackRegister, 0);
    moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &tmp1);

    lir::Register tmp2(5);
    lir::Memory newStackSrc(ThreadRegister, stackOffsetFromThread);
    moveMR(&c, TargetBytesPerWord, &newStackSrc, TargetBytesPerWord, &tmp2);

    lir::Register stack(StackRegister);
    subR(&c, TargetBytesPerWord, &stack, &tmp2, &tmp2);

    lir::Memory stackDst(StackRegister, 0, tmp2.low);
    moveAndUpdateRM
      (&c, TargetBytesPerWord, &tmp1, TargetBytesPerWord, &stackDst);

    return_(&c);
  }

  virtual void apply(lir::Operation op) {
    arch_->c.operations[op](&c);
  }

  virtual void apply(lir::UnaryOperation op, OperandInfo a)
  {
    arch_->c.unaryOperations[Multimethod::index(op, a.type)]
      (&c, a.size, a.operand);
  }

  virtual void apply(lir::BinaryOperation op, OperandInfo a, OperandInfo b)
  {
    arch_->c.binaryOperations[index(&(arch_->c), op, a.type, b.type)]
      (&c, a.size, a.operand, b.size, b.operand);
  }

  virtual void apply(lir::TernaryOperation op, OperandInfo a, OperandInfo b, OperandInfo c)
  {
    if (isBranch(op)) {
      assert(&this->c, a.size == b.size);
      assert(&this->c, c.size == TargetBytesPerWord);
      assert(&this->c, c.type == lir::ConstantOperand);

      arch_->c.branchOperations[branchIndex(&(arch_->c), a.type, b.type)]
        (&this->c, op, a.size, a.operand, b.operand, c.operand);
    } else {
      assert(&this->c, b.size == c.size);
      assert(&this->c, b.type == lir::RegisterOperand);
      assert(&this->c, c.type == lir::RegisterOperand);
      
      arch_->c.ternaryOperations[index(&(arch_->c), op, a.type)]
        (&this->c, b.size, a.operand, b.operand, c.operand);
    }
  }

  virtual void setDestination(uint8_t* dst) {
    c.result = dst;
  }

  virtual void write() {
    uint8_t* dst = c.result;
    unsigned dstOffset = 0;
    for (MyBlock* b = c.firstBlock; b; b = b->next) {
      if (DebugJumps) {
        fprintf(stderr, "write block %p\n", b);
      }

      unsigned blockOffset = 0;
      for (JumpEvent* e = b->jumpEventHead; e; e = e->next) {
        unsigned size = e->offset - blockOffset;
        memcpy(dst + dstOffset, c.code.data + b->offset + blockOffset, size);
        blockOffset = e->offset;
        dstOffset += size;

        unsigned jumpTableSize = 0;
        for (JumpOffset* o = e->jumpOffsetHead; o; o = o->next) {
          if (DebugJumps) {
            fprintf(stderr, "visit offset %p %d in block %p\n",
                    o, o->offset, b);
          }

          uint8_t* address = dst + dstOffset + jumpTableSize;

          if (needJump(b)) {
            address += TargetBytesPerWord;
          }

          o->task->jumpAddress = address;

          jumpTableSize += TargetBytesPerWord;
        }

        assert(&c, jumpTableSize);

        bool jump = needJump(b);
        if (jump) {
          write4(dst + dstOffset, isa::b(jumpTableSize + TargetBytesPerWord));
        }

        dstOffset += jumpTableSize + (jump ? TargetBytesPerWord : 0);
      }

      unsigned size = b->size - blockOffset;

      memcpy(dst + dstOffset,
             c.code.data + b->offset + blockOffset,
             size);

      dstOffset += size;
    }
    
    unsigned index = dstOffset;
    assert(&c, index % TargetBytesPerWord == 0);
    for (ConstantPoolEntry* e = c.constantPool; e; e = e->next) {
      e->address = dst + index;
      index += TargetBytesPerWord;
    }
    
    for (Task* t = c.tasks; t; t = t->next) {
      t->run(&c);
    }

    for (ConstantPoolEntry* e = c.constantPool; e; e = e->next) {
      *static_cast<uint32_t*>(e->address) = e->constant->value();
//       fprintf(stderr, "constant %p at %p\n", reinterpret_cast<void*>(e->constant->value()), e->address);
    }
  }

  virtual Promise* offset(bool) {
    return powerpc::offsetPromise(&c);
  }

  virtual Block* endBlock(bool startNew) {
    MyBlock* b = c.lastBlock;
    b->size = c.code.length() - b->offset;
    if (startNew) {
      c.lastBlock = new(c.zone) MyBlock(&c, c.code.length());
    } else {
      c.lastBlock = 0;
    }
    return b;
  }

  virtual void endEvent() {
    MyBlock* b = c.lastBlock;
    unsigned thisEventOffset = c.code.length() - b->offset;
    if (b->jumpOffsetHead) {
      int32_t v = (thisEventOffset + TargetBytesPerWord)
        - b->jumpOffsetHead->offset;

      if (v > 0 and not bounded(2, 16, v)) {
        appendJumpEvent
          (&c, b, b->lastEventOffset, b->jumpOffsetHead,
           b->lastJumpOffsetTail);

        if (DebugJumps) {
          for (JumpOffset* o = b->jumpOffsetHead;
               o != b->lastJumpOffsetTail->next; o = o->next)
          {
            fprintf(stderr,
                    "in endEvent, include %p %d in jump event %p "
                    "at offset %d in block %p\n",
                    o, o->offset, b->jumpEventTail, b->lastEventOffset, b);
          }
        }

        b->jumpOffsetHead = b->lastJumpOffsetTail->next;
        b->lastJumpOffsetTail->next = 0;
        if (b->jumpOffsetHead == 0) {
          b->jumpOffsetTail = 0;
        }
      }
    }
    b->lastEventOffset = thisEventOffset;
    b->lastJumpOffsetTail = b->jumpOffsetTail;
  }

  virtual unsigned length() {
    return c.code.length();
  }

  virtual unsigned footerSize() {
    return c.constantPoolCount * TargetBytesPerWord;
  }

  virtual void dispose() {
    c.code.dispose();
  }

  Context c;
  MyArchitecture* arch_;
};

Assembler* MyArchitecture::makeAssembler(Allocator* allocator, Zone* zone) {
  return new(zone) MyAssembler(this->c.s, allocator, zone, this);
}

} // namespace powerpc

Architecture*
makeArchitecturePowerpc(System* system, bool)
{
  return new (allocate(system, sizeof(powerpc::MyArchitecture))) powerpc::MyArchitecture(system);
}

} // namespace codegen
} // namespace avian
