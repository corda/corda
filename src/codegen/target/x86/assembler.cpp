/* Copyright (c) 2008-2014, Avian Contributors

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
const unsigned StackAlignmentInWords = StackAlignmentInBytes / TargetBytesPerWord;

unsigned
argumentFootprint(unsigned footprint)
{
  return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
}

uint32_t
read4(uint8_t* p)
{
  uint32_t v; memcpy(&v, p, 4);
  return v;
}

void
nextFrame(ArchitectureContext* c UNUSED, uint8_t* start, unsigned size UNUSED,
          unsigned footprint, void*, bool mostRecent,
          int targetParameterFootprint, void** ip, void** stack)
{
  assert(c, *ip >= start);
  assert(c, *ip <= start + size);

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
    assert(c, mostRecent);
    *ip = static_cast<void**>(*stack)[0];
    return;
  }

  if (UseFramePointer) {
    // skip preamble
    start += (TargetBytesPerWord == 4 ? 3 : 4);

    if (instruction <= start or *instruction == 0x5d) {
      assert(c, mostRecent);

      *ip = static_cast<void**>(*stack)[1];
      *stack = static_cast<void**>(*stack) + 1;
      return;
    }
  }

  if (*instruction == 0xc3) { // return
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
          and instruction[1] == 0xec)
      {
        offset
          -= (*instruction == 0x83 ? instruction[2] : read4(instruction + 2))
          / TargetBytesPerWord;
      }
    } else if (*instruction == 0x48
               and (instruction[1] == 0x83 or instruction[1] == 0x81)
               and instruction[2] == 0xec)
    {
      offset
        -= (instruction[1] == 0x83 ? instruction[3] : read4(instruction + 3))
        / TargetBytesPerWord;
    }

    // todo: check for and handle tail calls
  }
  
  if (UseFramePointer and not mostRecent) {
    assert(c, static_cast<void***>(*stack)[-1] + 1
           == static_cast<void**>(*stack) + offset);

    assert(c, static_cast<void***>(*stack)[-1][1]
           == static_cast<void**>(*stack)[offset]);
  }

  *ip = static_cast<void**>(*stack)[offset];
  *stack = static_cast<void**>(*stack) + offset;
}

class MyArchitecture: public Architecture {
 public:
  MyArchitecture(System* system, bool useNativeFeatures):
    c(system, useNativeFeatures),
    referenceCount(0),
    myRegisterFile(GeneralRegisterMask, useSSE(&c) ? FloatRegisterMask : 0)
  {
    populateTables(&c);
  }

  virtual unsigned floatRegisterSize() {
    if (useSSE(&c)) {
      return 8;
    } else {
      return 0;
    }
  }

  virtual const RegisterFile* registerFile() {
    return &myRegisterFile;
  }

  virtual int scratch() {
    return rax;
  }

  virtual int stack() {
    return rsp;
  }

  virtual int thread() {
    return rbx;
  }

  virtual int returnLow() {
    return rax;
  }

  virtual int returnHigh() {
    return (TargetBytesPerWord == 4 ? rdx : lir::NoRegister);
  }

  virtual int virtualCallTarget() {
    return rax;
  }

  virtual int virtualCallIndex() {
    return rdx;
  }

  virtual bool bigEndian() {
    return false;
  }

  virtual uintptr_t maximumImmediateJump() {
    return 0x7FFFFFFF;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case rbp:
      return UseFramePointer;

    case rsp:
    case rbx:
      return true;
   	  
    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint) {
#if AVIAN_TARGET_FORMAT == AVIAN_FORMAT_PE
    return max(footprint, StackAlignmentInWords);
#else
    return max(footprint > argumentRegisterCount() ?
               footprint - argumentRegisterCount() : 0,
               StackAlignmentInWords);
#endif
  }

  virtual unsigned argumentFootprint(unsigned footprint) {
    return x86::argumentFootprint(footprint);
  }

  virtual bool argumentAlignment() {
    return false;
  }

  virtual bool argumentRegisterAlignment() {
    return false;
  }

  virtual unsigned argumentRegisterCount() {
#if AVIAN_TARGET_FORMAT == AVIAN_FORMAT_PE
    if (TargetBytesPerWord == 8) return 4; else
#else
    if (TargetBytesPerWord == 8) return 6; else
#endif
    return 0;
  }

  virtual int argumentRegister(unsigned index) {
    assert(&c, TargetBytesPerWord == 8);
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

  virtual bool hasLinkRegister() {
    return false;
  }

  virtual unsigned stackAlignmentInWords() {
    return StackAlignmentInWords;
  }

  virtual bool matchCall(void* returnAddress, void* target) {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    int32_t actualOffset; memcpy(&actualOffset, instruction + 1, 4);
    void* actualTarget = static_cast<uint8_t*>(returnAddress) + actualOffset;

    return *instruction == 0xE8 and actualTarget == target;
  }

  virtual void updateCall(lir::UnaryOperation op, void* returnAddress,
                          void* newTarget)
  {
    bool assertAlignment UNUSED;
    switch (op) {
    case lir::AlignedCall:
      op = lir::Call;
      assertAlignment = true;
      break;

    case lir::AlignedJump:
      op = lir::Jump;
      assertAlignment = true;
      break;

    case lir::AlignedLongCall:
      op = lir::LongCall;
      assertAlignment = true;
      break;

    case lir::AlignedLongJump:
      op = lir::LongJump;
      assertAlignment = true;
      break;

    default:
      assertAlignment = false;
    }

    if (TargetBytesPerWord == 4 or op == lir::Call or op == lir::Jump) {
      uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
      
      assert(&c, ((op == lir::Call or op == lir::LongCall) and *instruction == 0xE8)
             or ((op == lir::Jump or op == lir::LongJump) and *instruction == 0xE9));

      assert(&c, (not assertAlignment)
             or reinterpret_cast<uintptr_t>(instruction + 1) % 4 == 0);
      
      intptr_t v = static_cast<uint8_t*>(newTarget)
        - static_cast<uint8_t*>(returnAddress);

      assert(&c, vm::fitsInInt32(v));

      int32_t v32 = v;

      memcpy(instruction + 1, &v32, 4);
    } else {
      uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 13;

      assert(&c, instruction[0] == 0x49 and instruction[1] == 0xBA);
      assert(&c, instruction[10] == 0x41 and instruction[11] == 0xFF);
      assert(&c, (op == lir::LongCall and instruction[12] == 0xD2)
             or (op == lir::LongJump and instruction[12] == 0xE2));

      assert(&c, (not assertAlignment)
             or reinterpret_cast<uintptr_t>(instruction + 2) % 8 == 0);
      
      memcpy(instruction + 2, &newTarget, 8);
    }
  }

  virtual void setConstant(void* dst, uint64_t constant) {
    target_uintptr_t v = targetVW(constant);
    memcpy(dst, &v, TargetBytesPerWord);
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    return pad(sizeInWords + FrameHeaderSize, StackAlignmentInWords)
      - FrameHeaderSize;
  }

  virtual void nextFrame(void* start, unsigned size, unsigned footprint,
                         void* link, bool mostRecent,
                         int targetParameterFootprint, void** ip,
                         void** stack)
  {
    x86::nextFrame(&c, static_cast<uint8_t*>(start), size, footprint,
                     link, mostRecent, targetParameterFootprint, ip, stack);
  }

  virtual void* frameIp(void* stack) {
    return stack ? *static_cast<void**>(stack) : 0;
  }

  virtual unsigned frameHeaderSize() {
    return FrameHeaderSize;
  }

  virtual unsigned frameReturnAddressSize() {
    return 1;
  }

  virtual unsigned frameFooterSize() {
    return 0;
  }

  virtual bool alwaysCondensed(lir::BinaryOperation op) {
    switch(op) {
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
  
  virtual bool alwaysCondensed(lir::TernaryOperation) {
    return true;
  }

  virtual int returnAddressOffset() {
    return 0;
  }

  virtual int framePointerOffset() {
    return UseFramePointer ? -1 : 0;
  }

  virtual void plan
  (lir::UnaryOperation,
   unsigned, OperandMask& aMask,
   bool* thunk)
  {
    aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand) | (1 << lir::ConstantOperand);
    *thunk = false;
  }

  virtual void planSource
  (lir::BinaryOperation op,
   unsigned aSize, OperandMask& aMask,
   unsigned bSize, bool* thunk)
  {
    aMask.registerMask = GeneralRegisterMask |
      (static_cast<uint64_t>(GeneralRegisterMask) << 32);

    *thunk = false;

    switch (op) {
    case lir::Negate:
      aMask.typeMask = (1 << lir::RegisterOperand);
      aMask.registerMask = (static_cast<uint64_t>(1) << (rdx + 32))
        | (static_cast<uint64_t>(1) << rax);
      break;

    case lir::Absolute:
      if (aSize <= TargetBytesPerWord) {
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = (static_cast<uint64_t>(1) << rax);
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatAbsolute:
      if (useSSE(&c)) {
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
      } else {
        *thunk = true;
      }
      break;  
  
    case lir::FloatNegate:
      // floatNegateRR does not support doubles
      if (useSSE(&c) and aSize == 4 and bSize == 4) {
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = FloatRegisterMask;
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatSquareRoot:
      if (useSSE(&c)) {
        aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        aMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
      } else {
        *thunk = true;
      }
      break;

    case lir::Float2Float:
      if (useSSE(&c)) {
        aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        aMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
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
        aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        aMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
      } else {
        *thunk = true;
      }
      break;

    case lir::Int2Float:
      if (useSSE(&c) and aSize <= TargetBytesPerWord) {
        aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        aMask.registerMask = GeneralRegisterMask
          | (static_cast<uint64_t>(GeneralRegisterMask) << 32);
      } else {
        *thunk = true;
      }
      break;

    case lir::Move:
      aMask.typeMask = ~0;
      aMask.registerMask = ~static_cast<uint64_t>(0);

      if (TargetBytesPerWord == 4) {
        if (aSize == 4 and bSize == 8) {
          aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
          const uint32_t mask
            = GeneralRegisterMask & ~((1 << rax) | (1 << rdx));
          aMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;    
        } else if (aSize == 1 or bSize == 1) {
          aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
          const uint32_t mask
            = (1 << rax) | (1 << rcx) | (1 << rdx) | (1 << rbx);
          aMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;     
        }
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination
  (lir::BinaryOperation op,
   unsigned aSize, const OperandMask& aMask,
   unsigned bSize, OperandMask& bMask)
  {
    bMask.typeMask = ~0;
    bMask.registerMask = GeneralRegisterMask
      | (static_cast<uint64_t>(GeneralRegisterMask) << 32);

    switch (op) {
    case lir::Absolute:
      bMask.typeMask = (1 << lir::RegisterOperand);
      bMask.registerMask = (static_cast<uint64_t>(1) << rax);
      break;

    case lir::FloatAbsolute:
      bMask.typeMask = (1 << lir::RegisterOperand);
      bMask.registerMask = aMask.registerMask;
      break;

    case lir::Negate:
      bMask.typeMask = (1 << lir::RegisterOperand);
      bMask.registerMask = aMask.registerMask;
      break;

    case lir::FloatNegate:
    case lir::FloatSquareRoot:
    case lir::Float2Float:
    case lir::Int2Float:
      bMask.typeMask = (1 << lir::RegisterOperand);
      bMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
        | FloatRegisterMask;
      break;

    case lir::Float2Int:
      bMask.typeMask = (1 << lir::RegisterOperand);
      break;

    case lir::Move:
      if (aMask.typeMask & ((1 << lir::MemoryOperand) | 1 << lir::AddressOperand)) {
        bMask.typeMask = (1 << lir::RegisterOperand);
        bMask.registerMask = GeneralRegisterMask
          | (static_cast<uint64_t>(GeneralRegisterMask) << 32)
          | FloatRegisterMask;
      } else if (aMask.typeMask & (1 << lir::RegisterOperand)) {
        bMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        if (aMask.registerMask & FloatRegisterMask) {
          bMask.registerMask = FloatRegisterMask;          
        } else {
          bMask.registerMask = GeneralRegisterMask
            | (static_cast<uint64_t>(GeneralRegisterMask) << 32);
        }
      } else {
        bMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
      }

      if (TargetBytesPerWord == 4) {
        if (aSize == 4 and bSize == 8) {
          bMask.registerMask = (static_cast<uint64_t>(1) << (rdx + 32))
            | (static_cast<uint64_t>(1) << rax);
        } else if (aSize == 1 or bSize == 1) {
          const uint32_t mask
            = (1 << rax) | (1 << rcx) | (1 << rdx) | (1 << rbx);
          bMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;
        }
      }
      break;

    default:
      break;
    }
  }

  virtual void planMove
  (unsigned size, OperandMask& srcMask,
   OperandMask& tmpMask,
   const OperandMask& dstMask)
  {
    srcMask.typeMask = ~0;
    srcMask.registerMask = ~static_cast<uint64_t>(0);

    tmpMask.typeMask = 0;
    tmpMask.registerMask = 0;

    if (dstMask.typeMask & (1 << lir::MemoryOperand)) {
      // can't move directly from memory to memory
      srcMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
      tmpMask.typeMask = 1 << lir::RegisterOperand;
      tmpMask.registerMask = GeneralRegisterMask
        | (static_cast<uint64_t>(GeneralRegisterMask) << 32);
    } else if (dstMask.typeMask & (1 << lir::RegisterOperand)) {
      if (size > TargetBytesPerWord) {
        // can't move directly from FPR to GPR or vice-versa for
        // values larger than the GPR size
        if (dstMask.registerMask & FloatRegisterMask) {
          srcMask.registerMask = FloatRegisterMask
            | (static_cast<uint64_t>(FloatRegisterMask) << 32);
          tmpMask.typeMask = 1 << lir::MemoryOperand;          
        } else if (dstMask.registerMask & GeneralRegisterMask) {
          srcMask.registerMask = GeneralRegisterMask
            | (static_cast<uint64_t>(GeneralRegisterMask) << 32);
          tmpMask.typeMask = 1 << lir::MemoryOperand;
        }
      }
      if (dstMask.registerMask & FloatRegisterMask) {
        // can't move directly from constant to FPR
        srcMask.typeMask &= ~(1 << lir::ConstantOperand);
        if (size > TargetBytesPerWord) {
          tmpMask.typeMask = 1 << lir::MemoryOperand;
        } else {
          tmpMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
          tmpMask.registerMask = GeneralRegisterMask
            | (static_cast<uint64_t>(GeneralRegisterMask) << 32);
        }
      }
    }
  }

  virtual void planSource
  (lir::TernaryOperation op,
   unsigned aSize, OperandMask& aMask,
   unsigned bSize, OperandMask& bMask,
   unsigned, bool* thunk)
  {
    aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
    aMask.registerMask = GeneralRegisterMask
      | (static_cast<uint64_t>(GeneralRegisterMask) << 32);

    bMask.typeMask = (1 << lir::RegisterOperand);
    bMask.registerMask = GeneralRegisterMask
      | (static_cast<uint64_t>(GeneralRegisterMask) << 32);

    *thunk = false;

    switch (op) {
    case lir::FloatAdd:
    case lir::FloatSubtract:
    case lir::FloatMultiply:
    case lir::FloatDivide:
      if (useSSE(&c)) {
        aMask.typeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
        bMask.typeMask = (1 << lir::RegisterOperand);

        const uint64_t mask
          = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
        aMask.registerMask = mask;
        bMask.registerMask = mask;
      } else {
        *thunk = true;
      }
      break;

    case lir::FloatRemainder:
      *thunk = true;
      break;
   	  
    case lir::Multiply:
      if (TargetBytesPerWord == 4 and aSize == 8) { 
        const uint32_t mask = GeneralRegisterMask & ~((1 << rax) | (1 << rdx));
        aMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;
        bMask.registerMask = (static_cast<uint64_t>(1) << (rdx + 32)) | mask;
      } else {
        aMask.registerMask = GeneralRegisterMask;
        bMask.registerMask = GeneralRegisterMask;
      }
      break;

    case lir::Divide:
      if (TargetBytesPerWord == 4 and aSize == 8) {
        *thunk = true;        			
      } else {
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = GeneralRegisterMask & ~((1 << rax) | (1 << rdx));
        bMask.registerMask = 1 << rax;      
      }
      break;

    case lir::Remainder:
      if (TargetBytesPerWord == 4 and aSize == 8) {
        *thunk = true;
      } else {
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = GeneralRegisterMask & ~((1 << rax) | (1 << rdx));
        bMask.registerMask = 1 << rax;
      }
      break;

    case lir::ShiftLeft:
    case lir::ShiftRight:
    case lir::UnsignedShiftRight: {
      if (TargetBytesPerWord == 4 and bSize == 8) {
        const uint32_t mask = GeneralRegisterMask & ~(1 << rcx);
        aMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;
        bMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;
      } else {
        aMask.registerMask = (static_cast<uint64_t>(GeneralRegisterMask) << 32)
          | (static_cast<uint64_t>(1) << rcx);
        const uint32_t mask = GeneralRegisterMask & ~(1 << rcx);
        bMask.registerMask = (static_cast<uint64_t>(mask) << 32) | mask;
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
        aMask.typeMask = (1 << lir::RegisterOperand);
        aMask.registerMask = (static_cast<uint64_t>(FloatRegisterMask) << 32)
          | FloatRegisterMask;
        bMask.typeMask = aMask.typeMask;
        bMask.registerMask = aMask.registerMask;
      } else {
        *thunk = true;
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination
  (lir::TernaryOperation op,
    unsigned, const OperandMask&,
    unsigned, const OperandMask& bMask,
    unsigned, OperandMask& cMask)
  {
    if (isBranch(op)) {
      cMask.typeMask = (1 << lir::ConstantOperand);
      cMask.registerMask = 0;
    } else {
      cMask.typeMask = (1 << lir::RegisterOperand);
      cMask.registerMask = bMask.registerMask;
    }
  }

  virtual Assembler* makeAssembler(util::Allocator* allocator, Zone* zone);

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
  const RegisterFile myRegisterFile;
};

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s, util::Allocator* a, Zone* zone, MyArchitecture* arch)
      : c(s, a, zone, &(arch->c)), arch_(arch)
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
    lir::Register stack(rsp);
    lir::Memory stackLimit(rbx, stackLimitOffsetFromThread);
    lir::Constant handlerConstant(resolvedPromise(&c, handler));
    branchRM(&c, lir::JumpIfGreaterOrEqual, TargetBytesPerWord, &stack, &stackLimit,
             &handlerConstant);
  }

  virtual void saveFrame(unsigned stackOffset, unsigned) {
    lir::Register stack(rsp);
    lir::Memory stackDst(rbx, stackOffset);
    apply(lir::Move,
      OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack),
      OperandInfo(TargetBytesPerWord, lir::MemoryOperand, &stackDst));
  }

  virtual void pushFrame(unsigned argumentCount, ...) {
    // TODO: Argument should be replaced by OperandInfo...
    struct Argument {
      unsigned size;
      lir::OperandType type;
      lir::Operand* operand;
    };
    RUNTIME_ARRAY(Argument, arguments, argumentCount);
    va_list a; va_start(a, argumentCount);
    unsigned footprint = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      RUNTIME_ARRAY_BODY(arguments)[i].size = va_arg(a, unsigned);
      RUNTIME_ARRAY_BODY(arguments)[i].type
        = static_cast<lir::OperandType>(va_arg(a, int));
      RUNTIME_ARRAY_BODY(arguments)[i].operand = va_arg(a, lir::Operand*);
      footprint += ceilingDivide
        (RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord);
    }
    va_end(a);

    allocateFrame(arch_->alignFrameSize(footprint));
    
    unsigned offset = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      if (i < arch_->argumentRegisterCount()) {
        lir::Register dst(arch_->argumentRegister(i));
        apply(lir::Move,
              OperandInfo(
                RUNTIME_ARRAY_BODY(arguments)[i].size,
                RUNTIME_ARRAY_BODY(arguments)[i].type,
                RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(
                pad(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord),
                lir::RegisterOperand,
                &dst));
      } else {
        lir::Memory dst(rsp, offset * TargetBytesPerWord);
        apply(lir::Move,
              OperandInfo(
                RUNTIME_ARRAY_BODY(arguments)[i].size,
                RUNTIME_ARRAY_BODY(arguments)[i].type,
                RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(
                pad(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord),
                lir::MemoryOperand,
                &dst));
        offset += ceilingDivide
          (RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint) {
    lir::Register stack(rsp);

    if (UseFramePointer) {
      lir::Register base(rbp);
      pushR(&c, TargetBytesPerWord, &base);

      apply(lir::Move,
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack),
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &base));
    }

    lir::Constant footprintConstant(resolvedPromise(&c, footprint * TargetBytesPerWord));
    apply(lir::Subtract,
      OperandInfo(TargetBytesPerWord, lir::ConstantOperand, &footprintConstant),
      OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack),
      OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack));
  }

  virtual void adjustFrame(unsigned difference) {
    lir::Register stack(rsp);
    lir::Constant differenceConstant(resolvedPromise(&c, difference * TargetBytesPerWord));
    apply(lir::Subtract, 
      OperandInfo(TargetBytesPerWord, lir::ConstantOperand, &differenceConstant),
      OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack),
      OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack));
  }

  virtual void popFrame(unsigned frameFootprint) {
    if (UseFramePointer) {
      lir::Register base(rbp);
      lir::Register stack(rsp);
      apply(lir::Move,
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &base),
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack));

      popR(&c, TargetBytesPerWord, &base);
    } else {
      lir::Register stack(rsp);
      lir::Constant footprint(resolvedPromise(&c, frameFootprint * TargetBytesPerWord));
      apply(lir::Add,
        OperandInfo(TargetBytesPerWord, lir::ConstantOperand, &footprint),
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack),
        OperandInfo(TargetBytesPerWord, lir::RegisterOperand, &stack));
    }
  }

  virtual void popFrameForTailCall(unsigned frameFootprint,
                                   int offset,
                                   int returnAddressSurrogate,
                                   int framePointerSurrogate)
  {
    if (TailCalls) {
      if (offset) {
        lir::Register tmp(c.client->acquireTemporary());
      
        unsigned baseSize = UseFramePointer ? 1 : 0;

        lir::Memory returnAddressSrc
          (rsp, (frameFootprint + baseSize) * TargetBytesPerWord);
        moveMR(&c, TargetBytesPerWord, &returnAddressSrc, TargetBytesPerWord,
               &tmp);
    
        lir::Memory returnAddressDst
          (rsp, (frameFootprint - offset + baseSize) * TargetBytesPerWord);
        moveRM(&c, TargetBytesPerWord, &tmp, TargetBytesPerWord,
               &returnAddressDst);

        c.client->releaseTemporary(tmp.low);

        if (UseFramePointer) {
          lir::Memory baseSrc(rsp, frameFootprint * TargetBytesPerWord);
          lir::Register base(rbp);
          moveMR(&c, TargetBytesPerWord, &baseSrc, TargetBytesPerWord, &base);
        }

        lir::Register stack(rsp);
        lir::Constant footprint
          (resolvedPromise
           (&c, (frameFootprint - offset + baseSize) * TargetBytesPerWord));

        addCR(&c, TargetBytesPerWord, &footprint, TargetBytesPerWord, &stack);

        if (returnAddressSurrogate != lir::NoRegister) {
          assert(&c, offset > 0);

          lir::Register ras(returnAddressSurrogate);
          lir::Memory dst(rsp, offset * TargetBytesPerWord);
          moveRM(&c, TargetBytesPerWord, &ras, TargetBytesPerWord, &dst);
        }

        if (framePointerSurrogate != lir::NoRegister) {
          assert(&c, offset > 0);

          lir::Register fps(framePointerSurrogate);
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

    assert(&c, argumentFootprint >= StackAlignmentInWords);
    assert(&c, (argumentFootprint % StackAlignmentInWords) == 0);

    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      lir::Register returnAddress(rcx);
      popR(&c, TargetBytesPerWord, &returnAddress);

      lir::Register stack(rsp);
      lir::Constant adjustment
        (resolvedPromise(&c, (argumentFootprint - StackAlignmentInWords)
                  * TargetBytesPerWord));
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

    lir::Register returnAddress(rcx);
    popR(&c, TargetBytesPerWord, &returnAddress);

    lir::Register stack(rsp);
    lir::Memory stackSrc(rbx, stackOffsetFromThread);
    moveMR(&c, TargetBytesPerWord, &stackSrc, TargetBytesPerWord, &stack);

    jumpR(&c, TargetBytesPerWord, &returnAddress);
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
      assert(&this->c, b.type == c.type);

      arch_->c.binaryOperations[index(&(arch_->c), op, a.type, b.type)]
        (&this->c, a.size, a.operand, b.size, b.operand);
    }
  }

  virtual void setDestination(uint8_t* dst) {
    c.result = dst;
  }

  virtual void write() {
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
               % p->alignment)
        {
          *(dst + b->start + index + padding) = 0x90;
          ++ padding;
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

  virtual Promise* offset(bool) {
    return x86::offsetPromise(&c);
  }

  virtual Block* endBlock(bool startNew) {
    MyBlock* b = c.lastBlock;
    b->size = c.code.length() - b->offset;
    if (startNew) {
      c.lastBlock = new(c.zone) MyBlock(c.code.length());
    } else {
      c.lastBlock = 0;
    }
    return b;
  }

  virtual void endEvent() {
    // ignore
  }

  virtual unsigned length() {
    return c.code.length();
  }

  virtual unsigned footerSize() {
    return 0;
  }

  virtual void dispose() {
    c.code.dispose();
  }

  Context c;
  MyArchitecture* arch_;
};

Assembler* MyArchitecture::makeAssembler(util::Allocator* allocator, Zone* zone)
{
  return
    new(zone) MyAssembler(c.s, allocator, zone, this);
}

} // namespace x86

Architecture* makeArchitectureX86(System* system, bool useNativeFeatures)
{
  return new (allocate(system, sizeof(x86::MyArchitecture)))
    x86::MyArchitecture(system, useNativeFeatures);
}

} // namespace codegen
} // namespace avian
