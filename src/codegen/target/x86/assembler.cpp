/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdarg.h>
#include <string.h>

#include "avian/environment.h"
#include "avian/target.h"
#include "avian/alloc-vector.h"
#include "avian/common.h"
#include "avian/util/allocator.h"
#include "avian/zone.h"

#include <avian/util/runtime-array.h>
#include <avian/util/abort.h>
#include <avian/util/math.h>

#include <avian/codegen/assembler.h>
#include <avian/codegen/architecture.h>
#include <avian/codegen/registers.h>
#include <avian/codegen/lir.h>
#include <avian/codegen/promise.h>

#include <avian/system/system.h>

#include "context.h"
#include "block.h"
#include "fixup.h"
#include "padding.h"
#include "registers.h"
#include "operations.h"
#include "detect.h"
#include "multimethod.h"
#include "../multimethod.h"

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)
#define CAST_BRANCH(x) reinterpret_cast<BranchOperationType>(x)

using namespace vm;
using namespace avian::util;

namespace avian {
namespace codegen {
namespace x86 {

const unsigned FrameHeaderSize = (UseFramePointer ? 2 : 1);

const unsigned StackAlignmentInBytes = 16;
const unsigned StackAlignmentInWords = StackAlignmentInBytes
                                       / TargetBytesPerWord;

unsigned argumentFootprint(unsigned footprint)
{
  return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
}

uint32_t read4(uint8_t* p)
{
  uint32_t v;
  memcpy(&v, p, 4);
  return v;
}

void nextFrame(ArchitectureContext* c UNUSED,
               uint8_t* start,
               unsigned size UNUSED,
               unsigned footprint,
               void*,
               bool mostRecent,
               int targetParameterFootprint,
               void** ip,
               void** stack)
{
  assertT(c, *ip >= start);
  assertT(c, *ip <= start + size);

  uint8_t* instruction = static_cast<uint8_t*>(*ip);

  // skip stack overflow check, if present:
  if (TargetBytesPerWord == 4) {
    if (*start == 0x39) {
      start += 12;
    }
  } else if (*start == 0x48 and start[1] == 0x39) {
    start += 13;
  }

  if (instruction <= start) {
    assertT(c, mostRecent);
    *ip = static_cast<void**>(*stack)[0];
    return;
  }

  if (UseFramePointer) {
    // skip preamble
    start += (TargetBytesPerWord == 4 ? 3 : 4);

    if (instruction <= start or *instruction == 0x5d) {
      assertT(c, mostRecent);

      *ip = static_cast<void**>(*stack)[1];
      *stack = static_cast<void**>(*stack) + 1;
      return;
    }
  }

  if (*instruction == 0xc3) {  // return
    *ip = static_cast<void**>(*stack)[0];
    return;
  }

  unsigned offset = footprint + FrameHeaderSize - (mostRecent ? 1 : 0);

  if (TailCalls and targetParameterFootprint >= 0) {
    if (argumentFootprint(targetParameterFootprint) > StackAlignmentInWords) {
      offset += argumentFootprint(targetParameterFootprint)
                - StackAlignmentInWords;
    }

    // check for post-non-tail-call stack adjustment of the form "sub
    // $offset,%rsp":
    if (TargetBytesPerWord == 4) {
      if ((*instruction == 0x83 or *instruction == 0x81)
          and instruction[1] == 0xec) {
        offset
            -= (*instruction == 0x83 ? instruction[2] : read4(instruction + 2))
               / TargetBytesPerWord;
      }
    } else if (*instruction == 0x48
               and (instruction[1] == 0x83 or instruction[1] == 0x81)
               and instruction[2] == 0xec) {
      offset
          -= (instruction[1] == 0x83 ? instruction[3] : read4(instruction + 3))
             / TargetBytesPerWord;
    }

    // todo: check for and handle tail calls
  }

  if (UseFramePointer and not mostRecent) {
    assertT(c,
            static_cast<void***>(*stack)[-1] + 1
            == static_cast<void**>(*stack) + offset);

    assertT(c,
            static_cast<void***>(*stack)[-1][1]
            == static_cast<void**>(*stack)[offset]);
  }

  *ip = static_cast<void**>(*stack)[offset];
  *stack = static_cast<void**>(*stack) + offset;
}

class MyArchitecture : public Architecture {
 public:
  MyArchitecture(System* system, bool useNativeFeatures)
      : c(system, useNativeFeatures),
        referenceCount(0),
        myRegisterFile(GeneralRegisterMask, useSSE(&c) ? FloatRegisterMask : 0)
  {
    populateTables(&c);
  }

  virtual unsigned floatRegisterSize()
  {
    if (useSSE(&c)) {
      return 8;
    } else {
      return 0;
    }
  }

  virtual const RegisterFile* registerFile()
  {
    return &myRegisterFile;
  }

  virtual Register scratch()
  {
    return rax;
  }

  virtual Register stack()
  {
    return rsp;
  }

  virtual Register thread()
  {
    return rbx;
  }

  virtual Register returnLow()
  {
    return rax;
  }

  virtual Register returnHigh()
  {
    return (TargetBytesPerWord == 4 ? rdx : NoRegister);
  }

  virtual Register virtualCallTarget()
  {
    return rax;
  }

  virtual Register virtualCallIndex()
  {
    return rdx;
  }

  virtual ir::TargetInfo targetInfo()
  {
    return ir::TargetInfo(TargetBytesPerWord);
  }

  virtual bool bigEndian()
  {
    return false;
  }

  virtual uintptr_t maximumImmediateJump()
  {
    return 0x7FFFFFFF;
  }

  virtual bool reserved(Register register_)
  {
    switch (register_.index()) {
    case rbp.index():
      return UseFramePointer;

    case rsp.index():
    case rbx.index():
      return true;

    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint)
  {
#if AVIAN_TARGET_FORMAT == AVIAN_FORMAT_PE
    return max(footprint, StackAlignmentInWords);
#else
    return max(footprint > argumentRegisterCount()
                   ? footprint - argumentRegisterCount()
                   : 0,
               StackAlignmentInWords);
#endif
  }

  virtual unsigned argumentFootprint(unsigned footprint)
  {
    return x86::argumentFootprint(footprint);
  }

  virtual bool argumentAlignment()
  {
    return false;
  }

  virtual bool argumentRegisterAlignment()
  {
    return false;
  }

  virtual unsigned argumentRegisterCount()
  {
#if AVIAN_TARGET_FORMAT == AVIAN_FORMAT_PE
    if (TargetBytesPerWord == 8)
      return 4;
    else
#else
    if (TargetBytesPerWord == 8)
      return 6;
    else
#endif
      return 0;
  }

  virtual Register argumentRegister(unsigned index)
  {
    assertT(&c, TargetBytesPerWord == 8);
    switch (index) {
#if AVIAN_TARGET_FORMAT == AVIAN_FORMAT_PE
    case 0:
      return rcx;
    case 1:
      return rdx;
    case 2:
      return r8;
    case 3:
      return r9;
#else
    case 0:
      return rdi;
    case 1:
      return rsi;
    case 2:
      return rdx;
    case 3:
      return rcx;
    case 4:
      return r8;
    case 5:
      return r9;
#endif
    default:
      abort(&c);
    }
  }

  virtual bool hasLinkRegister()
  {
    return false;
  }

  virtual unsigned stackAlignmentInWords()
  {
    return StackAlignmentInWords;
  }

  virtual bool matchCall(void* returnAddress, void* target)
  {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    int32_t actualOffset;
    memcpy(&actualOffset, instruction + 1, 4);
    void* actualTarget = static_cast<uint8_t*>(returnAddress) + actualOffset;

    return *instruction == 0xE8 and actualTarget == target;
  }

  virtual void updateCall(lir::UnaryOperation op,
                          void* returnAddress,
                          void* newTarget)
  {
    bool assertTAlignment UNUSED;
    switch (op) {
    case lir::AlignedCall:
      op = lir::Call;
      assertTAlignment = true;
      break;

    case lir::AlignedJump:
      op = lir::Jump;
      assertTAlignment = true;
      break;

    case lir::AlignedLongCall:
      op = lir::LongCall;
      assertTAlignment = true;
      break;

    case lir::AlignedLongJump:
      op = lir::LongJump;
      assertTAlignment = true;
      break;

    default:
      assertTAlignment = false;
    }

    if (TargetBytesPerWord == 4 or op == lir::Call or op == lir::Jump) {
      uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;

      assertT(
          &c,
          ((op == lir::Call or op == lir::LongCall) and *instruction == 0xE8)
          or ((op == lir::Jump or op == lir::LongJump)
              and *instruction == 0xE9));

      assertT(&c,
              (not assertTAlignment)
              or reinterpret_cast<uintptr_t>(instruction + 1) % 4 == 0);

      intptr_t v = static_cast<uint8_t*>(newTarget)
                   - static_cast<uint8_t*>(returnAddress);

      assertT(&c, vm::fitsInInt32(v));

      int32_t v32 = v;

      memcpy(instruction + 1, &v32, 4);
    } else {
      uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 13;

      assertT(&c, instruction[0] == 0x49 and instruction[1] == 0xBA);
      assertT(&c, instruction[10] == 0x41 and instruction[11] == 0xFF);
      assertT(&c,
              (op == lir::LongCall and instruction[12] == 0xD2)
              or (op == lir::LongJump and instruction[12] == 0xE2));

      assertT(&c,
              (not assertTAlignment)
              or reinterpret_cast<uintptr_t>(instruction + 2) % 8 == 0);

      memcpy(instruction + 2, &newTarget, 8);
    }
  }

  virtual void setConstant(void* dst, uint64_t constant)
  {
    target_uintptr_t v = targetVW(constant);
    memcpy(dst, &v, TargetBytesPerWord);
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords)
  {
    return pad(sizeInWords + FrameHeaderSize, StackAlignmentInWords)
           - FrameHeaderSize;
  }

  virtual void nextFrame(void* start,
                         unsigned size,
                         unsigned footprint,
                         void* link,
                         bool mostRecent,
                         int targetParameterFootprint,
                         void** ip,
                         void** stack)
  {
    x86::nextFrame(&c,
                   static_cast<uint8_t*>(start),
                   size,
                   footprint,
                   link,
                   mostRecent,
                   targetParameterFootprint,
                   ip,
                   stack);
  }

  virtual void* frameIp(void* stack)
  {
    return stack ? *static_cast<void**>(stack) : 0;
  }

  virtual unsigned frameHeaderSize()
  {
    return FrameHeaderSize;
  }

  virtual unsigned frameReturnAddressSize()
  {
    return 1;
  }

  virtual unsigned frameFooterSize()
  {
    return 0;
  }

  virtual bool alwaysCondensed(lir::BinaryOperation op)
  {
    switch (op) {
    case lir::Float2Float:
    case lir::Float2Int:
    case lir::Int2Float:
    case lir::FloatAbsolute:
    case lir::FloatNegate:
    case lir::FloatSquareRoot:
      return false;

    case lir::Negate:
    case lir::Absolute:
      return true;

    default:
      abort(&c);
    }
  }

  virtual bool alwaysCondensed(lir::TernaryOperation)
  {
    return true;
  }

  virtual int returnAddressOffset()
  {
    return 0;
  }

  virtual int framePointerOffset()
  {
    return UseFramePointer ? -1 : 0;
  }

  virtual void plan(lir::UnaryOperation,
                    unsigned,
                    OperandMask& aMask,
                    bool* thunk)
  {
    aMask.typeMask = lir::Operand::RegisterPairMask | lir::Operand::MemoryMask
                     | lir::Operand::ConstantMask;
    *thunk = false;
  }

  virtual void planSource(lir::BinaryOperation op,
                          unsigned aSize,
                          OperandMask& aMask,
                          unsigned bSize,
                          bool* thunk)
  {
    aMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);

    *thunk = false;

    switch (op) {
    case lir::Negate:
      aMask.typeMask = lir::Operand::RegisterPairMask;
      aMask.setLowHighRegisterMasks(rax, rdx);
      break;

    case lir::Absolute:
      if (aSize <= TargetBytesPerWord) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(rax, 0);
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatAbsolute:
      if (useSSE(&c)) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatNegate:
      // floatNegateRR does not support doubles
      if (useSSE(&c) and aSize == 4 and bSize == 4) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, 0);
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatSquareRoot:
      if (useSSE(&c)) {
        aMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::Float2Float:
      if (useSSE(&c)) {
        aMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::Float2Int:
      // todo: Java requires different semantics than SSE for
      // converting floats to integers, we we need to either use
      // thunks or produce inline machine code which handles edge
      // cases properly.
      if (false and useSSE(&c) and bSize <= TargetBytesPerWord) {
        aMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::Int2Float:
      if (useSSE(&c) and aSize <= TargetBytesPerWord) {
        aMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        aMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::Move:
      aMask.typeMask = ~0;
      aMask.setLowHighRegisterMasks(AnyRegisterMask, AnyRegisterMask);

      if (TargetBytesPerWord == 4) {
        if (aSize == 4 and bSize == 8) {
          aMask.typeMask = lir::Operand::RegisterPairMask
                           | lir::Operand::MemoryMask;
          const RegisterMask mask = GeneralRegisterMask
                                .excluding(rax).excluding(rdx);
          aMask.setLowHighRegisterMasks(mask, mask);
        } else if (aSize == 1 or bSize == 1) {
          aMask.typeMask = lir::Operand::RegisterPairMask
                           | lir::Operand::MemoryMask;
          const RegisterMask mask = rax | rcx | rdx | rbx;
          aMask.setLowHighRegisterMasks(mask, mask);
        }
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination(lir::BinaryOperation op,
                               unsigned aSize,
                               const OperandMask& aMask,
                               unsigned bSize,
                               OperandMask& bMask)
  {
    bMask.typeMask = ~0;
    bMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);

    switch (op) {
    case lir::Absolute:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.setLowHighRegisterMasks(rax, 0);
      break;

    case lir::FloatAbsolute:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.lowRegisterMask = aMask.lowRegisterMask;
      bMask.highRegisterMask = aMask.highRegisterMask;
      break;

    case lir::Negate:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.lowRegisterMask = aMask.lowRegisterMask;
      bMask.highRegisterMask = aMask.highRegisterMask;
      break;

    case lir::FloatNegate:
    case lir::FloatSquareRoot:
    case lir::Float2Float:
    case lir::Int2Float:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      break;

    case lir::Float2Int:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      break;

    case lir::Move:
      if (aMask.typeMask
          & (lir::Operand::MemoryMask | lir::Operand::AddressMask)) {
        bMask.typeMask = lir::Operand::RegisterPairMask;
        bMask.setLowHighRegisterMasks(GeneralRegisterMask | FloatRegisterMask, GeneralRegisterMask);
      } else if (aMask.typeMask & lir::Operand::RegisterPairMask) {
        bMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        if (aMask.lowRegisterMask & FloatRegisterMask) {
          bMask.setLowHighRegisterMasks(FloatRegisterMask, 0);
        } else {
          bMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);
        }
      } else {
        bMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
      }

      if (TargetBytesPerWord == 4) {
        if (aSize == 4 and bSize == 8) {
          bMask.setLowHighRegisterMasks(rax, rdx);
        } else if (aSize == 1 or bSize == 1) {
          const RegisterMask mask = rax | rcx | rdx | rbx;
          bMask.setLowHighRegisterMasks(mask, mask);
        }
      }
      break;

    default:
      break;
    }
  }

  virtual void planMove(unsigned size,
                        OperandMask& srcMask,
                        OperandMask& tmpMask,
                        const OperandMask& dstMask)
  {
    srcMask.typeMask = ~0;
    srcMask.setLowHighRegisterMasks(AnyRegisterMask, AnyRegisterMask);

    tmpMask.typeMask = 0;
    tmpMask.setLowHighRegisterMasks(0, 0);

    if (dstMask.typeMask & lir::Operand::MemoryMask) {
      // can't move directly from memory to memory
      srcMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::ConstantMask;
      tmpMask.typeMask = lir::Operand::RegisterPairMask;
      tmpMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);
    } else if (dstMask.typeMask & lir::Operand::RegisterPairMask) {
      if (size > TargetBytesPerWord) {
        // can't move directly from FPR to GPR or vice-versa for
        // values larger than the GPR size
        if (dstMask.lowRegisterMask & FloatRegisterMask) {
          srcMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
          tmpMask.typeMask = lir::Operand::MemoryMask;
        } else if (dstMask.lowRegisterMask & GeneralRegisterMask) {
          srcMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);
          tmpMask.typeMask = lir::Operand::MemoryMask;
        }
      }
      if (dstMask.lowRegisterMask & FloatRegisterMask) {
        // can't move directly from constant to FPR
        srcMask.typeMask &= ~lir::Operand::ConstantMask;
        if (size > TargetBytesPerWord) {
          tmpMask.typeMask = lir::Operand::MemoryMask;
        } else {
          tmpMask.typeMask = lir::Operand::RegisterPairMask
                             | lir::Operand::MemoryMask;
          tmpMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);
        }
      }
    }
  }

  virtual void planSource(lir::TernaryOperation op,
                          unsigned aSize,
                          OperandMask& aMask,
                          unsigned bSize,
                          OperandMask& bMask,
                          unsigned,
                          bool* thunk)
  {
    aMask.typeMask = lir::Operand::RegisterPairMask | lir::Operand::ConstantMask;
    aMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);

    bMask.typeMask = lir::Operand::RegisterPairMask;
    bMask.setLowHighRegisterMasks(GeneralRegisterMask, GeneralRegisterMask);

    *thunk = false;

    switch (op) {
    case lir::FloatAdd:
    case lir::FloatSubtract:
    case lir::FloatMultiply:
    case lir::FloatDivide:
      if (useSSE(&c)) {
        aMask.typeMask = lir::Operand::RegisterPairMask
                         | lir::Operand::MemoryMask;
        bMask.typeMask = lir::Operand::RegisterPairMask;

        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
        bMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatRemainder:
      *thunk = true;
      break;

    case lir::Multiply:
      if (TargetBytesPerWord == 4 and aSize == 8) {
        const RegisterMask mask = GeneralRegisterMask .excluding(rax).excluding(rdx);
        aMask.setLowHighRegisterMasks(mask, mask);
        bMask.setLowHighRegisterMasks(mask, rdx);
      } else {
        aMask.setLowHighRegisterMasks(GeneralRegisterMask, 0);
        bMask.setLowHighRegisterMasks(GeneralRegisterMask, 0);
      }
      break;

    case lir::Divide:
      if (TargetBytesPerWord == 4 and aSize == 8) {
        *thunk = true;
      } else {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(GeneralRegisterMask .excluding(rax).excluding(rdx), 0);
        bMask.setLowHighRegisterMasks(rax, 0);
      }
      break;

    case lir::Remainder:
      if (TargetBytesPerWord == 4 and aSize == 8) {
        *thunk = true;
      } else {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(GeneralRegisterMask .excluding(rax).excluding(rdx), 0);
        bMask.setLowHighRegisterMasks(rax, 0);
      }
      break;

    case lir::ShiftLeft:
    case lir::ShiftRight:
    case lir::UnsignedShiftRight: {
      if (TargetBytesPerWord == 4 and bSize == 8) {
        const RegisterMask mask = GeneralRegisterMask.excluding(rcx);
        aMask.setLowHighRegisterMasks(mask, mask);
        bMask.setLowHighRegisterMasks(mask, mask);
      } else {
        aMask.setLowHighRegisterMasks(rcx, GeneralRegisterMask);
        const RegisterMask mask = GeneralRegisterMask.excluding(rcx);
        bMask.setLowHighRegisterMasks(mask, mask);
      }
    } break;

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
      if (useSSE(&c)) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FloatRegisterMask, FloatRegisterMask);
        bMask.typeMask = aMask.typeMask;
        bMask.lowRegisterMask = aMask.lowRegisterMask;
        bMask.highRegisterMask = aMask.highRegisterMask;
      } else {
        *thunk = true;
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination(lir::TernaryOperation op,
                               unsigned,
                               const OperandMask&,
                               unsigned,
                               const OperandMask& bMask,
                               unsigned,
                               OperandMask& cMask)
  {
    if (isBranch(op)) {
      cMask.typeMask = lir::Operand::ConstantMask;
      cMask.setLowHighRegisterMasks(0, 0);
    } else {
      cMask.typeMask = lir::Operand::RegisterPairMask;
      cMask.lowRegisterMask = bMask.lowRegisterMask;
      cMask.highRegisterMask = bMask.highRegisterMask;
    }
  }

  virtual Assembler* makeAssembler(util::Alloc* allocator, Zone* zone);

  virtual void acquire()
  {
    ++referenceCount;
  }

  virtual void release()
  {
    if (--referenceCount == 0) {
      c.s->free(this);
    }
  }

  ArchitectureContext c;
  unsigned referenceCount;
  const RegisterFile myRegisterFile;
};

class MyAssembler : public Assembler {
 public:
  MyAssembler(System* s, util::Alloc* a, Zone* zone, MyArchitecture* arch)
      : c(s, a, zone, &(arch->c)), arch_(arch)
  {
  }

  virtual void setClient(Client* client)
  {
    assertT(&c, c.client == 0);
    c.client = client;
  }

  virtual Architecture* arch()
  {
    return arch_;
  }

  virtual void checkStackOverflow(uintptr_t handler,
                                  unsigned stackLimitOffsetFromThread)
  {
    lir::RegisterPair stack(rsp);
    lir::Memory stackLimit(rbx, stackLimitOffsetFromThread);
    lir::Constant handlerConstant(resolvedPromise(&c, handler));
    branchRM(&c,
             lir::JumpIfGreaterOrEqual,
             TargetBytesPerWord,
             &stack,
             &stackLimit,
             &handlerConstant);
  }

  virtual void saveFrame(unsigned stackOffset, unsigned)
  {
    lir::RegisterPair stack(rsp);
    lir::Memory stackDst(rbx, stackOffset);
    apply(lir::Move,
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack),
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::Memory, &stackDst));
  }

  virtual void pushFrame(unsigned argumentCount, ...)
  {
    // TODO: Argument should be replaced by OperandInfo...
    struct Argument {
      unsigned size;
      lir::Operand::Type type;
      lir::Operand* operand;
    };
    RUNTIME_ARRAY(Argument, arguments, argumentCount);
    va_list a;
    va_start(a, argumentCount);
    unsigned footprint = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      RUNTIME_ARRAY_BODY(arguments)[i].size = va_arg(a, unsigned);
      RUNTIME_ARRAY_BODY(arguments)[i].type
          = static_cast<lir::Operand::Type>(va_arg(a, int));
      RUNTIME_ARRAY_BODY(arguments)[i].operand = va_arg(a, lir::Operand*);
      footprint += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size,
                                 TargetBytesPerWord);
    }
    va_end(a);

    allocateFrame(arch_->alignFrameSize(footprint));

    unsigned offset = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      if (i < arch_->argumentRegisterCount()) {
        lir::RegisterPair dst(arch_->argumentRegister(i));
        apply(lir::Move,
              OperandInfo(RUNTIME_ARRAY_BODY(arguments)[i].size,
                          RUNTIME_ARRAY_BODY(arguments)[i].type,
                          RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(pad(RUNTIME_ARRAY_BODY(arguments)[i].size,
                              TargetBytesPerWord),
                          lir::Operand::Type::RegisterPair,
                          &dst));
      } else {
        lir::Memory dst(rsp, offset * TargetBytesPerWord);
        apply(lir::Move,
              OperandInfo(RUNTIME_ARRAY_BODY(arguments)[i].size,
                          RUNTIME_ARRAY_BODY(arguments)[i].type,
                          RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(pad(RUNTIME_ARRAY_BODY(arguments)[i].size,
                              TargetBytesPerWord),
                          lir::Operand::Type::Memory,
                          &dst));
        offset += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size,
                                TargetBytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint)
  {
    lir::RegisterPair stack(rsp);

    if (UseFramePointer) {
      lir::RegisterPair base(rbp);
      pushR(&c, TargetBytesPerWord, &base);

      apply(lir::Move,
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack),
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &base));
    }

    lir::Constant footprintConstant(
        resolvedPromise(&c, footprint * TargetBytesPerWord));
    apply(lir::Subtract,
          OperandInfo(
              TargetBytesPerWord, lir::Operand::Type::Constant, &footprintConstant),
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack),
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack));
  }

  virtual void adjustFrame(unsigned difference)
  {
    lir::RegisterPair stack(rsp);
    lir::Constant differenceConstant(
        resolvedPromise(&c, difference * TargetBytesPerWord));
    apply(lir::Subtract,
          OperandInfo(
              TargetBytesPerWord, lir::Operand::Type::Constant, &differenceConstant),
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack),
          OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack));
  }

  virtual void popFrame(unsigned frameFootprint)
  {
    if (UseFramePointer) {
      lir::RegisterPair base(rbp);
      lir::RegisterPair stack(rsp);
      apply(lir::Move,
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &base),
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack));

      popR(&c, TargetBytesPerWord, &base);
    } else {
      lir::RegisterPair stack(rsp);
      lir::Constant footprint(
          resolvedPromise(&c, frameFootprint * TargetBytesPerWord));
      apply(lir::Add,
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::Constant, &footprint),
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack),
            OperandInfo(TargetBytesPerWord, lir::Operand::Type::RegisterPair, &stack));
    }
  }

  virtual void popFrameForTailCall(unsigned frameFootprint,
                                   int offset,
                                   Register returnAddressSurrogate,
                                   Register framePointerSurrogate)
  {
    if (TailCalls) {
      if (offset) {
        lir::RegisterPair tmp(c.client->acquireTemporary());

        unsigned baseSize = UseFramePointer ? 1 : 0;

        lir::Memory returnAddressSrc(
            rsp, (frameFootprint + baseSize) * TargetBytesPerWord);
        moveMR(&c,
               TargetBytesPerWord,
               &returnAddressSrc,
               TargetBytesPerWord,
               &tmp);

        lir::Memory returnAddressDst(
            rsp, (frameFootprint - offset + baseSize) * TargetBytesPerWord);
        moveRM(&c,
               TargetBytesPerWord,
               &tmp,
               TargetBytesPerWord,
               &returnAddressDst);

        c.client->releaseTemporary(tmp.low);

        if (UseFramePointer) {
          lir::Memory baseSrc(rsp, frameFootprint * TargetBytesPerWord);
          lir::RegisterPair base(rbp);
          moveMR(&c, TargetBytesPerWord, &baseSrc, TargetBytesPerWord, &base);
        }

        lir::RegisterPair stack(rsp);
        lir::Constant footprint(resolvedPromise(
            &c, (frameFootprint - offset + baseSize) * TargetBytesPerWord));

        addCR(&c, TargetBytesPerWord, &footprint, TargetBytesPerWord, &stack);

        if (returnAddressSurrogate != NoRegister) {
          assertT(&c, offset > 0);

          lir::RegisterPair ras(returnAddressSurrogate);
          lir::Memory dst(rsp, offset * TargetBytesPerWord);
          moveRM(&c, TargetBytesPerWord, &ras, TargetBytesPerWord, &dst);
        }

        if (framePointerSurrogate != NoRegister) {
          assertT(&c, offset > 0);

          lir::RegisterPair fps(framePointerSurrogate);
          lir::Memory dst(rsp, (offset - 1) * TargetBytesPerWord);
          moveRM(&c, TargetBytesPerWord, &fps, TargetBytesPerWord, &dst);
        }
      } else {
        popFrame(frameFootprint);
      }
    } else {
      abort(&c);
    }
  }

  virtual void popFrameAndPopArgumentsAndReturn(unsigned frameFootprint,
                                                unsigned argumentFootprint)
  {
    popFrame(frameFootprint);

    assertT(&c, argumentFootprint >= StackAlignmentInWords);
    assertT(&c, (argumentFootprint % StackAlignmentInWords) == 0);

    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      lir::RegisterPair returnAddress(rcx);
      popR(&c, TargetBytesPerWord, &returnAddress);

      lir::RegisterPair stack(rsp);
      lir::Constant adjustment(resolvedPromise(
          &c,
          (argumentFootprint - StackAlignmentInWords) * TargetBytesPerWord));
      addCR(&c, TargetBytesPerWord, &adjustment, TargetBytesPerWord, &stack);

      jumpR(&c, TargetBytesPerWord, &returnAddress);
    } else {
      return_(&c);
    }
  }

  virtual void popFrameAndUpdateStackAndReturn(unsigned frameFootprint,
                                               unsigned stackOffsetFromThread)
  {
    popFrame(frameFootprint);

    lir::RegisterPair returnAddress(rcx);
    popR(&c, TargetBytesPerWord, &returnAddress);

    lir::RegisterPair stack(rsp);
    lir::Memory stackSrc(rbx, stackOffsetFromThread);
    moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &stack);

    jumpR(&c, TargetBytesPerWord, &returnAddress);
  }

  virtual void apply(lir::Operation op)
  {
    arch_->c.operations[op](&c);
  }

  virtual void apply(lir::UnaryOperation op, OperandInfo a)
  {
    arch_->c.unaryOperations[Multimethod::index(op, a.type)](
        &c, a.size, a.operand);
  }

  virtual void apply(lir::BinaryOperation op, OperandInfo a, OperandInfo b)
  {
    arch_->c.binaryOperations[index(&(arch_->c), op, a.type, b.type)](
        &c, a.size, a.operand, b.size, b.operand);
  }

  virtual void apply(lir::TernaryOperation op,
                     OperandInfo a,
                     OperandInfo b,
                     OperandInfo c)
  {
    if (isBranch(op)) {
      assertT(&this->c, a.size == b.size);
      assertT(&this->c, c.size == TargetBytesPerWord);
      assertT(&this->c, c.type == lir::Operand::Type::Constant);

      arch_->c.branchOperations[branchIndex(&(arch_->c), a.type, b.type)](
          &this->c, op, a.size, a.operand, b.operand, c.operand);
    } else {
      assertT(&this->c, b.size == c.size);
      assertT(&this->c, b.type == c.type);

      arch_->c.binaryOperations[index(&(arch_->c), op, a.type, b.type)](
          &this->c, a.size, a.operand, b.size, b.operand);
    }
  }

  virtual void setDestination(uint8_t* dst)
  {
    c.result = dst;
  }

  virtual void write()
  {
    uint8_t* dst = c.result;
    for (MyBlock* b = c.firstBlock; b; b = b->next) {
      unsigned index = 0;
      unsigned padding = 0;
      for (AlignmentPadding* p = b->firstPadding; p; p = p->next) {
        unsigned size = p->offset - b->offset - index;

        memcpy(dst + b->start + index + padding,
               c.code.data.begin() + b->offset + index,
               size);

        index += size;

        while ((b->start + index + padding + p->instructionOffset)
               % p->alignment) {
          *(dst + b->start + index + padding) = 0x90;
          ++padding;
        }
      }

      memcpy(dst + b->start + index + padding,
             c.code.data.begin() + b->offset + index,
             b->size - index);
    }

    for (Task* t = c.tasks; t; t = t->next) {
      t->run(&c);
    }
  }

  virtual Promise* offset(bool)
  {
    return x86::offsetPromise(&c);
  }

  virtual Block* endBlock(bool startNew)
  {
    MyBlock* b = c.lastBlock;
    b->size = c.code.length() - b->offset;
    if (startNew) {
      c.lastBlock = new (c.zone) MyBlock(c.code.length());
    } else {
      c.lastBlock = 0;
    }
    return b;
  }

  virtual void endEvent()
  {
    // ignore
  }

  virtual unsigned length()
  {
    return c.code.length();
  }

  virtual unsigned footerSize()
  {
    return 0;
  }

  virtual void dispose()
  {
    c.code.dispose();
  }

  Context c;
  MyArchitecture* arch_;
};

Assembler* MyArchitecture::makeAssembler(util::Alloc* allocator, Zone* zone)
{
  return new (zone) MyAssembler(c.s, allocator, zone, this);
}

}  // namespace x86

Architecture* makeArchitectureX86(System* system, bool useNativeFeatures)
{
  return new (allocate(system, sizeof(x86::MyArchitecture)))
      x86::MyArchitecture(system, useNativeFeatures);
}

}  // namespace codegen
}  // namespace avian
