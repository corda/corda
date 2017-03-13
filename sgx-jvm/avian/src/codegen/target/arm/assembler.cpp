/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/util/runtime-array.h>

#include <avian/codegen/assembler.h>
#include <avian/codegen/architecture.h>
#include <avian/codegen/registers.h>

#include "context.h"
#include "block.h"
#include "fixup.h"
#include "multimethod.h"
#include "encode.h"
#include "operations.h"
#include "registers.h"
#include "../multimethod.h"

#include "avian/alloc-vector.h"
#include <avian/util/abort.h>

using namespace vm;
using namespace avian::codegen;
using namespace avian::util;

namespace avian {
namespace codegen {
namespace arm {

namespace isa {
// HARDWARE FLAGS
bool vfpSupported()
{
// TODO: Use at runtime detection
#if (defined __ARM_PCS_VFP) || (defined ARCH_arm64)
  // armhf
  return true;
#else
  // armel
  // TODO: allow VFP use for -mfloat-abi=softfp armel builds.
  // GCC -mfloat-abi=softfp flag allows use of VFP while remaining compatible
  // with soft-float code.
  return false;
#endif
}
}  // namespace isa

const RegisterFile MyRegisterFileWithoutFloats(GPR_MASK, 0);
const RegisterFile MyRegisterFileWithFloats(GPR_MASK, FPR_MASK);

const unsigned FrameHeaderSize = TargetBytesPerWord / 4;

const unsigned StackAlignmentInBytes = TargetBytesPerWord * 2;
const unsigned StackAlignmentInWords = StackAlignmentInBytes
                                       / TargetBytesPerWord;

void resolve(MyBlock*);

unsigned padding(MyBlock*, unsigned);

class ConstantPoolEntry;

// BEGIN OPERATION COMPILERS

using namespace isa;

// END OPERATION COMPILERS

unsigned argumentFootprint(unsigned footprint)
{
  return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
}

void nextFrame(ArchitectureContext* con,
               uint32_t* start,
               unsigned size UNUSED,
               unsigned footprint,
               void* link,
               bool,
               int targetParameterFootprint UNUSED,
               void** ip,
               void** stack)
{
  assertT(con, *ip >= start);
  assertT(con, *ip <= start + (size / 4));

  uint32_t* instruction = static_cast<uint32_t*>(*ip);

  if ((*start >> 20) == (TargetBytesPerWord == 8 ? 0xf94 : 0xe59)) {
    // skip stack overflow check
    start += TargetBytesPerWord == 8 ? 4 : 3;
  }

  if (instruction <= start) {
    *ip = link;
    return;
  }

  unsigned offset = footprint + FrameHeaderSize;

  if (instruction <= start + 2) {
    *ip = link;
    *stack = static_cast<void**>(*stack) + offset;
    return;
  }

  if (*instruction == (TargetBytesPerWord == 8 ? 0xd61f03c0 : 0xe12fff1e)) {
    // return
    *ip = link;
    return;
  }

  if (TailCalls and targetParameterFootprint >= 0) {
    if (argumentFootprint(targetParameterFootprint) > StackAlignmentInWords) {
      offset += argumentFootprint(targetParameterFootprint)
                - StackAlignmentInWords;
    }

    // check for post-non-tail-call stack adjustment of the form "sub
    // sp, sp, #offset":
    if (TargetBytesPerWord == 8 and (*instruction & 0xff0003ff) == 0xd10003ff) {
      unsigned value = (*instruction >> 10) & 0xfff;
      unsigned shift = (*instruction >> 22) & 1;
      switch (shift) {
      case 0:
        offset -= value / TargetBytesPerWord;
        break;
      case 1:
        offset -= (value << 12) / TargetBytesPerWord;
        break;
      default:
        abort(con);
      }
    } else if (TargetBytesPerWord == 4 and (*instruction >> 12) == 0xe24dd) {
      unsigned value = *instruction & 0xff;
      unsigned rotation = (*instruction >> 8) & 0xf;
      switch (rotation) {
      case 0:
        offset -= value / TargetBytesPerWord;
        break;
      case 15:
        offset -= value;
        break;
      default:
        abort(con);
      }
    }

    // todo: check for and handle tail calls
  }

  *ip = static_cast<void**>(*stack)[offset - 1];
  *stack = static_cast<void**>(*stack) + offset;
}

class MyArchitecture : public Architecture {
 public:
  MyArchitecture(System* system) : con(system), referenceCount(0)
  {
    populateTables(&con);
  }

  virtual unsigned floatRegisterSize()
  {
    return vfpSupported() ? 8 : 0;
  }

  virtual const RegisterFile* registerFile()
  {
    return vfpSupported() ? &MyRegisterFileWithFloats
                          : &MyRegisterFileWithoutFloats;
  }

  virtual Register scratch()
  {
    return Register(5);
  }

  virtual Register stack()
  {
    return StackRegister;
  }

  virtual Register thread()
  {
    return ThreadRegister;
  }

  virtual Register returnLow()
  {
    return Register(0);
  }

  virtual Register returnHigh()
  {
    return Register(1);
  }

  virtual Register virtualCallTarget()
  {
    return Register(4);
  }

  virtual Register virtualCallIndex()
  {
    return Register(3);
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
    return 0x1FFFFFF;
  }

  virtual bool reserved(Register register_)
  {
    switch (register_.index()) {
    case LinkRegister.index():
    case FrameRegister.index():
    case StackRegister.index():
    case ThreadRegister.index():
    case ProgramCounter.index():
      return true;
    case 18:
      // x18 is a reserved platform register on arm64
      return TargetBytesPerWord == 8;

    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint)
  {
    return max(footprint, StackAlignmentInWords);
  }

  virtual unsigned argumentFootprint(unsigned footprint)
  {
    return arm::argumentFootprint(footprint);
  }

  virtual bool argumentAlignment()
  {
#ifdef __APPLE__
    return false;
#else
    return true;
#endif
  }

  virtual bool argumentRegisterAlignment()
  {
#ifdef __APPLE__
    return false;
#else
    return true;
#endif
  }

  virtual unsigned argumentRegisterCount()
  {
    return TargetBytesPerWord;
  }

  virtual Register argumentRegister(unsigned index)
  {
    assertT(&con, index < argumentRegisterCount());

    return Register(index);
  }

  virtual bool hasLinkRegister()
  {
    return true;
  }

  virtual unsigned stackAlignmentInWords()
  {
    return StackAlignmentInWords;
  }

  virtual bool matchCall(void* returnAddress, void* target)
  {
    uint32_t* instruction = static_cast<uint32_t*>(returnAddress) - 1;

    return *instruction == static_cast<uint32_t>(
                               bl(static_cast<uint8_t*>(target)
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
      updateOffset(con.s,
                   static_cast<uint8_t*>(returnAddress) - 4,
                   reinterpret_cast<intptr_t>(newTarget));
    } break;

    case lir::LongCall:
    case lir::LongJump:
    case lir::AlignedLongCall:
    case lir::AlignedLongJump: {
      uint32_t* p = static_cast<uint32_t*>(returnAddress) - 2;
      if (TargetBytesPerWord == 8) {
        const int32_t mask = (PoolOffsetMask >> 2) << 5;
        *reinterpret_cast<void**>(p + ((*p & mask) >> 5)) = newTarget;
      } else {
        *reinterpret_cast<void**>(p + (((*p & PoolOffsetMask) + 8) / 4))
            = newTarget;
      }
    } break;

    default:
      abort(&con);
    }
  }

  virtual unsigned constantCallSize()
  {
    return 4;
  }

  virtual void setConstant(void* dst, uint64_t constant)
  {
    *static_cast<target_uintptr_t*>(dst) = constant;
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
    arm::nextFrame(&con,
                   static_cast<uint32_t*>(start),
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
    return stack ? static_cast<void**>(stack)[returnAddressOffset()] : 0;
  }

  virtual unsigned frameHeaderSize()
  {
    return FrameHeaderSize;
  }

  virtual unsigned frameReturnAddressSize()
  {
    return 0;
  }

  virtual unsigned frameFooterSize()
  {
    return 0;
  }

  virtual int returnAddressOffset()
  {
    return -1;
  }

  virtual int framePointerOffset()
  {
    return 0;
  }

  virtual bool alwaysCondensed(lir::BinaryOperation)
  {
    return false;
  }

  virtual bool alwaysCondensed(lir::TernaryOperation)
  {
    return false;
  }

  virtual void plan(lir::UnaryOperation,
                    unsigned,
                    OperandMask& aMask,
                    bool* thunk)
  {
    aMask.typeMask = lir::Operand::RegisterPairMask | lir::Operand::ConstantMask;
    aMask.setLowHighRegisterMasks(AnyRegisterMask, AnyRegisterMask);
    *thunk = false;
  }

  virtual void planSource(lir::BinaryOperation op,
                          unsigned aSize,
                          OperandMask& aMask,
                          unsigned bSize,
                          bool* thunk)
  {
    *thunk = false;
    aMask.typeMask = ~0;
    aMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);

    switch (op) {
    case lir::Negate:
      aMask.typeMask = lir::Operand::RegisterPairMask;
      aMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);
      break;

    case lir::Absolute:
      *thunk = true;
      break;

    case lir::FloatAbsolute:
    case lir::FloatSquareRoot:
    case lir::FloatNegate:
    case lir::Float2Float:
      if (vfpSupported()) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FPR_MASK, FPR_MASK);
      } else {
        *thunk = true;
      }
      break;

    case lir::Float2Int:
      // todo: Java requires different semantics than VFP for
      // converting floats to integers, we we need to either use
      // thunks or produce inline machine code which handles edge
      // cases properly.
      if (false && vfpSupported() && bSize <= TargetBytesPerWord) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FPR_MASK, FPR_MASK);
      } else {
        *thunk = true;
      }
      break;

    case lir::Int2Float:
      if (vfpSupported() && aSize <= TargetBytesPerWord) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);
      } else {
        *thunk = true;
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination(lir::BinaryOperation op,
                               unsigned,
                               const OperandMask& aMask,
                               unsigned,
                               OperandMask& bMask)
  {
    bMask.typeMask = lir::Operand::RegisterPairMask | lir::Operand::MemoryMask;
    bMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);

    switch (op) {
    case lir::Negate:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);
      break;

    case lir::FloatAbsolute:
    case lir::FloatSquareRoot:
    case lir::FloatNegate:
    case lir::Float2Float:
    case lir::Int2Float:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.setLowHighRegisterMasks(FPR_MASK, FPR_MASK);
      break;

    case lir::Float2Int:
      bMask.typeMask = lir::Operand::RegisterPairMask;
      bMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);
      break;

    case lir::Move:
      if (!(aMask.typeMask & lir::Operand::RegisterPairMask)) {
        bMask.typeMask = lir::Operand::RegisterPairMask;
      }
      break;

    default:
      break;
    }
  }

  virtual void planMove(unsigned,
                        OperandMask& srcMask,
                        OperandMask& tmpMask,
                        const OperandMask& dstMask)
  {
    srcMask.typeMask = ~0;
    srcMask.setLowHighRegisterMasks(AnyRegisterMask, AnyRegisterMask);

    tmpMask.typeMask = 0;
    tmpMask.setLowHighRegisterMasks(0, 0);

    if (dstMask.typeMask & lir::Operand::MemoryMask) {
      // can't move directly from memory or constant to memory
      srcMask.typeMask = lir::Operand::RegisterPairMask;
      tmpMask.typeMask = lir::Operand::RegisterPairMask;
      tmpMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);
    } else if (vfpSupported() && dstMask.typeMask & lir::Operand::RegisterPairMask
               && dstMask.lowRegisterMask & FPR_MASK) {
      srcMask.typeMask = tmpMask.typeMask = lir::Operand::RegisterPairMask
                                            | lir::Operand::MemoryMask;
      tmpMask.setLowHighRegisterMasks(AnyRegisterMask, AnyRegisterMask);
    }
  }

  virtual void planSource(lir::TernaryOperation op,
                          unsigned,
                          OperandMask& aMask,
                          unsigned bSize,
                          OperandMask& bMask,
                          unsigned,
                          bool* thunk)
  {
    aMask.typeMask = lir::Operand::RegisterPairMask | lir::Operand::ConstantMask;
    aMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);

    bMask.typeMask = lir::Operand::RegisterPairMask;
    bMask.setLowHighRegisterMasks(GPR_MASK, GPR_MASK);

    *thunk = false;

    switch (op) {
    case lir::ShiftLeft:
    case lir::ShiftRight:
    case lir::UnsignedShiftRight:
      if (bSize > TargetBytesPerWord)
        aMask.typeMask = bMask.typeMask = lir::Operand::RegisterPairMask;
      break;

    case lir::Add:
    case lir::Subtract:
    case lir::Or:
    case lir::Xor:
    case lir::Multiply:
      aMask.typeMask = bMask.typeMask = lir::Operand::RegisterPairMask;
      break;

    // todo: Although ARM has instructions for integer division and
    // remainder, they don't trap on division by zero, which is why
    // we use thunks.  Alternatively, we could generate inline code
    // with an explicit zero check, which would probably be a bit
    // faster.
    case lir::Divide:
    case lir::Remainder:
    case lir::FloatRemainder:
      *thunk = true;
      break;

    case lir::FloatAdd:
    case lir::FloatSubtract:
    case lir::FloatMultiply:
    case lir::FloatDivide:
      if (vfpSupported()) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FPR_MASK, FPR_MASK);
        bMask = aMask;
      } else {
        *thunk = true;
      }
      break;

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
      if (vfpSupported()) {
        aMask.typeMask = lir::Operand::RegisterPairMask;
        aMask.setLowHighRegisterMasks(FPR_MASK, FPR_MASK);
        bMask = aMask;
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
                               const OperandMask& aMask UNUSED,
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

  virtual Assembler* makeAssembler(Alloc* allocator, Zone* zone);

  virtual void acquire()
  {
    ++referenceCount;
  }

  virtual void release()
  {
    if (--referenceCount == 0) {
      con.s->free(this);
    }
  }

  ArchitectureContext con;
  unsigned referenceCount;
};

class MyAssembler : public Assembler {
 public:
  MyAssembler(System* s, Alloc* a, Zone* zone, MyArchitecture* arch)
      : con(s, a, zone), arch_(arch)
  {
  }

  virtual void setClient(Client* client)
  {
    assertT(&con, con.client == 0);
    con.client = client;
  }

  virtual Architecture* arch()
  {
    return arch_;
  }

  virtual void checkStackOverflow(uintptr_t handler,
                                  unsigned stackLimitOffsetFromThread)
  {
    lir::RegisterPair stack(StackRegister);
    lir::Memory stackLimit(ThreadRegister, stackLimitOffsetFromThread);
    lir::Constant handlerConstant(new (con.zone) ResolvedPromise(handler));
    branchRM(&con,
             lir::JumpIfGreaterOrEqual,
             TargetBytesPerWord,
             &stack,
             &stackLimit,
             &handlerConstant);
  }

  virtual void saveFrame(unsigned stackOffset, unsigned ipOffset)
  {
    lir::RegisterPair link(LinkRegister);
    lir::Memory linkDst(ThreadRegister, ipOffset);
    moveRM(&con, TargetBytesPerWord, &link, TargetBytesPerWord, &linkDst);

    lir::RegisterPair stack(StackRegister);
    lir::Memory stackDst(ThreadRegister, stackOffset);
    moveRM(&con, TargetBytesPerWord, &stack, TargetBytesPerWord, &stackDst);
  }

  virtual void pushFrame(unsigned argumentCount, ...)
  {
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

        offset += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size,
                                TargetBytesPerWord);
      } else {
        lir::Memory dst(StackRegister, offset * TargetBytesPerWord);

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
    footprint += FrameHeaderSize;

    // larger frames may require multiple subtract/add instructions
    // to allocate/deallocate, and nextFrame will need to be taught
    // how to handle them:
    assertT(&con, footprint < 256);

    // todo: the ARM ABI says the frame preamble should be of the form
    //
    //   stp x29, x30, [sp,#-footprint]!
    //   mov x29, sp
    //
    // and the frame should be popped with e.g.
    //
    //   ldp x29, x30, [sp],#footprint
    //   br x30
    //
    // However, that will invalidate a lot of assumptions elsewhere
    // about the return address being stored at the opposite end of
    // the frame, so lots of other code will need to change before we
    // can do that.  The code below can be enabled as a starting point
    // when we're ready to tackle that.
    if (false and TargetBytesPerWord == 8) {
      // stp x29, x30, [sp,#-footprint]!
      con.code.append4(0xa9800000 | ((-footprint & 0x7f) << 15)
                       | (StackRegister.index() << 5)
                       | (LinkRegister.index() << 10) | FrameRegister.index());

      lir::RegisterPair stack(StackRegister);
      lir::RegisterPair frame(FrameRegister);
      moveRR(&con, TargetBytesPerWord, &stack, TargetBytesPerWord, &frame);
    } else {
      lir::RegisterPair stack(StackRegister);
      ResolvedPromise footprintPromise(footprint * TargetBytesPerWord);
      lir::Constant footprintConstant(&footprintPromise);
      subC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);

      lir::RegisterPair returnAddress(LinkRegister);
      lir::Memory returnAddressDst(StackRegister,
                                   (footprint - 1) * TargetBytesPerWord);
      moveRM(&con,
             TargetBytesPerWord,
             &returnAddress,
             TargetBytesPerWord,
             &returnAddressDst);
    }
  }

  virtual void adjustFrame(unsigned difference)
  {
    lir::RegisterPair stack(StackRegister);
    ResolvedPromise differencePromise(difference * TargetBytesPerWord);
    lir::Constant differenceConstant(&differencePromise);
    subC(&con, TargetBytesPerWord, &differenceConstant, &stack, &stack);
  }

  virtual void popFrame(unsigned footprint)
  {
    footprint += FrameHeaderSize;

    // see comment regarding the ARM64 ABI in allocateFrame
    if (false and TargetBytesPerWord == 8) {
      // ldp x29, x30, [sp],#footprint
      con.code.append4(0xa8c00000 | (footprint << 15) | (31 << 5) | (30 << 10)
                       | 29);
    } else {
      lir::RegisterPair returnAddress(LinkRegister);
      lir::Memory returnAddressSrc(StackRegister,
                                   (footprint - 1) * TargetBytesPerWord);
      moveMR(&con,
             TargetBytesPerWord,
             &returnAddressSrc,
             TargetBytesPerWord,
             &returnAddress);

      lir::RegisterPair stack(StackRegister);
      ResolvedPromise footprintPromise(footprint * TargetBytesPerWord);
      lir::Constant footprintConstant(&footprintPromise);
      addC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);
    }
  }

  virtual void popFrameForTailCall(unsigned footprint,
                                   int offset,
                                   Register returnAddressSurrogate,
                                   Register framePointerSurrogate UNUSED)
  {
    assertT(&con, framePointerSurrogate == NoRegister);

    if (TailCalls) {
      if (offset) {
        footprint += FrameHeaderSize;

        lir::RegisterPair link(LinkRegister);
        lir::Memory returnAddressSrc(StackRegister,
                                     (footprint - 1) * TargetBytesPerWord);
        moveMR(&con,
               TargetBytesPerWord,
               &returnAddressSrc,
               TargetBytesPerWord,
               &link);

        lir::RegisterPair stack(StackRegister);
        ResolvedPromise footprintPromise((footprint - offset)
                                         * TargetBytesPerWord);
        lir::Constant footprintConstant(&footprintPromise);
        addC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);

        if (returnAddressSurrogate != NoRegister) {
          assertT(&con, offset > 0);

          lir::RegisterPair ras(returnAddressSurrogate);
          lir::Memory dst(StackRegister, (offset - 1) * TargetBytesPerWord);
          moveRM(&con, TargetBytesPerWord, &ras, TargetBytesPerWord, &dst);
        }
      } else {
        popFrame(footprint);
      }
    } else {
      abort(&con);
    }
  }

  virtual void popFrameAndPopArgumentsAndReturn(unsigned frameFootprint,
                                                unsigned argumentFootprint)
  {
    popFrame(frameFootprint);

    assertT(&con, argumentFootprint >= StackAlignmentInWords);
    assertT(&con, (argumentFootprint % StackAlignmentInWords) == 0);

    unsigned offset;
    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      offset = argumentFootprint - StackAlignmentInWords;

      lir::RegisterPair stack(StackRegister);
      ResolvedPromise adjustmentPromise(offset * TargetBytesPerWord);
      lir::Constant adjustment(&adjustmentPromise);
      addC(&con, TargetBytesPerWord, &adjustment, &stack, &stack);
    } else {
      offset = 0;
    }

    return_(&con);
  }

  virtual void popFrameAndUpdateStackAndReturn(unsigned footprint,
                                               unsigned stackOffsetFromThread)
  {
    footprint += FrameHeaderSize;

    // see comment regarding the ARM64 ABI in allocateFrame
    if (false and TargetBytesPerWord == 8) {
      // ldp x29, x30, [sp],#footprint
      con.code.append4(0xa8c00000 | (footprint << 15) | (31 << 5) | (30 << 10)
                       | 29);
    } else {
      lir::RegisterPair returnAddress(LinkRegister);
      lir::Memory returnAddressSrc(StackRegister,
                                   (footprint - 1) * TargetBytesPerWord);
      moveMR(&con,
             TargetBytesPerWord,
             &returnAddressSrc,
             TargetBytesPerWord,
             &returnAddress);
    }

    lir::RegisterPair stack(StackRegister);
    lir::Memory newStackSrc(ThreadRegister, stackOffsetFromThread);
    moveMR(&con, TargetBytesPerWord, &newStackSrc, TargetBytesPerWord, &stack);

    return_(&con);
  }

  virtual void apply(lir::Operation op)
  {
    arch_->con.operations[op](&con);
  }

  virtual void apply(lir::UnaryOperation op, OperandInfo a)
  {
    arch_->con.unaryOperations[Multimethod::index(op, a.type)](
        &con, a.size, a.operand);
  }

  virtual void apply(lir::BinaryOperation op, OperandInfo a, OperandInfo b)
  {
    arch_->con.binaryOperations[index(&(arch_->con), op, a.type, b.type)](
        &con, a.size, a.operand, b.size, b.operand);
  }

  virtual void apply(lir::TernaryOperation op,
                     OperandInfo a,
                     OperandInfo b,
                     OperandInfo c)
  {
    if (isBranch(op)) {
      assertT(&con, a.size == b.size);
      assertT(&con, c.size == TargetBytesPerWord);
      assertT(&con, c.type == lir::Operand::Type::Constant);

      arch_->con.branchOperations[branchIndex(&(arch_->con), a.type, b.type)](
          &con, op, a.size, a.operand, b.operand, c.operand);
    } else {
      assertT(&con, b.size == c.size);
      assertT(&con, b.type == lir::Operand::Type::RegisterPair);
      assertT(&con, c.type == lir::Operand::Type::RegisterPair);

      arch_->con.ternaryOperations[index(&(arch_->con), op, a.type)](
          &con, b.size, a.operand, b.operand, c.operand);
    }
  }

  virtual void setDestination(uint8_t* dst)
  {
    con.result = dst;
  }

  virtual void write()
  {
    uint8_t* dst = con.result;
    unsigned dstOffset = 0;
    for (MyBlock* b = con.firstBlock; b; b = b->next) {
      if (DebugPool) {
        fprintf(stderr, "write block %p\n", b);
      }

      unsigned blockOffset = 0;
      for (PoolEvent* e = b->poolEventHead; e; e = e->next) {
        unsigned size = e->offset - blockOffset;
        memcpy(dst + dstOffset,
               con.code.data.begin() + b->offset + blockOffset,
               size);
        blockOffset = e->offset;
        dstOffset += size;

        unsigned poolSize = 0;
        for (PoolOffset* o = e->poolOffsetHead; o; o = o->next) {
          if (DebugPool) {
            fprintf(stderr,
                    "visit pool offset %p %d in block %p\n",
                    o,
                    o->offset,
                    b);
          }

          unsigned entry = dstOffset + poolSize;

          if (needJump(b)) {
            entry += TargetBytesPerWord;
          }

          o->entry->address = dst + entry;

          unsigned instruction = o->block->start + padding(o->block, o->offset)
                                 + o->offset;

          int32_t* p = reinterpret_cast<int32_t*>(dst + instruction);

          if (TargetBytesPerWord == 8) {
            int32_t v = entry - instruction;
            expect(&con, v == (v & PoolOffsetMask));

            const int32_t mask = (PoolOffsetMask >> 2) << 5;
            *p = (((v >> 2) << 5) & mask) | ((~mask) & *p);
          } else {
            int32_t v = (entry - 8) - instruction;
            expect(&con, v == (v & PoolOffsetMask));

            *p = (v & PoolOffsetMask) | ((~PoolOffsetMask) & *p);
          }

          poolSize += TargetBytesPerWord;
        }

        bool jump = needJump(b);
        if (jump) {
          expect(&con, TargetBytesPerWord == 4);

          write4(dst + dstOffset,
                 isa::b((poolSize + TargetBytesPerWord - 8) >> 2));
        }

        dstOffset += poolSize + (jump ? TargetBytesPerWord : 0);
      }

      unsigned size = b->size - blockOffset;

      memcpy(dst + dstOffset,
             con.code.data.begin() + b->offset + blockOffset,
             size);

      dstOffset += size;
    }

    for (Task* t = con.tasks; t; t = t->next) {
      t->run(&con);
    }

    for (ConstantPoolEntry* e = con.constantPool; e; e = e->next) {
      if (e->constant->resolved()) {
        *static_cast<target_uintptr_t*>(e->address) = e->constant->value();
      } else {
        new (e->constant->listen(sizeof(ConstantPoolListener)))
            ConstantPoolListener(
                con.s,
                static_cast<target_uintptr_t*>(e->address),
                e->callOffset ? dst + e->callOffset->value() + 8 : 0);
      }
      if (false) {
        fprintf(stderr,
                "constant %p at %p\n",
                reinterpret_cast<void*>(e->constant->value()),
                e->address);
      }
    }
  }

  virtual Promise* offset(bool forTrace)
  {
    return arm::offsetPromise(&con, forTrace);
  }

  virtual Block* endBlock(bool startNew)
  {
    MyBlock* b = con.lastBlock;
    b->size = con.code.length() - b->offset;
    if (startNew) {
      con.lastBlock = new (con.zone) MyBlock(&con, con.code.length());
    } else {
      con.lastBlock = 0;
    }
    return b;
  }

  virtual void endEvent()
  {
    MyBlock* b = con.lastBlock;
    unsigned thisEventOffset = con.code.length() - b->offset;
    if (b->poolOffsetHead) {
      int32_t v = (thisEventOffset + TargetBytesPerWord - 8)
                  - b->poolOffsetHead->offset;

      if (v > 0 and v != (v & PoolOffsetMask)) {
        appendPoolEvent(&con,
                        b,
                        b->lastEventOffset,
                        b->poolOffsetHead,
                        b->lastPoolOffsetTail);

        if (DebugPool) {
          for (PoolOffset* o = b->poolOffsetHead;
               o != b->lastPoolOffsetTail->next;
               o = o->next) {
            fprintf(stderr,
                    "in endEvent, include %p %d in pool event %p at offset %d "
                    "in block %p\n",
                    o,
                    o->offset,
                    b->poolEventTail,
                    b->lastEventOffset,
                    b);
          }
        }

        b->poolOffsetHead = b->lastPoolOffsetTail->next;
        b->lastPoolOffsetTail->next = 0;
        if (b->poolOffsetHead == 0) {
          b->poolOffsetTail = 0;
        }
      }
    }
    b->lastEventOffset = thisEventOffset;
    b->lastPoolOffsetTail = b->poolOffsetTail;
  }

  virtual unsigned length()
  {
    return con.code.length();
  }

  virtual unsigned footerSize()
  {
    return 0;
  }

  virtual void dispose()
  {
    con.code.dispose();
  }

  Context con;
  MyArchitecture* arch_;
};

Assembler* MyArchitecture::makeAssembler(Alloc* allocator, Zone* zone)
{
  return new (zone) MyAssembler(this->con.s, allocator, zone, this);
}

}  // namespace arm

Architecture* makeArchitectureArm(System* system, bool)
{
  return new (allocate(system, sizeof(arm::MyArchitecture)))
      arm::MyArchitecture(system);
}

}  // namespace codegen
}  // namespace avian
