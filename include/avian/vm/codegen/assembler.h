/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_H
#define AVIAN_CODEGEN_ASSEMBLER_H

#include <avian/vm/system/system.h>
#include "zone.h"

#include <avian/vm/codegen/lir.h>
#include <avian/vm/codegen/promise.h>

namespace avian {
namespace codegen {

class RegisterFile;

class OperandInfo {
public:
  const unsigned size;
  const lir::OperandType type;
  lir::Operand* const operand;

  inline OperandInfo(unsigned size, lir::OperandType type, lir::Operand* operand):
    size(size),
    type(type),
    operand(operand)
  { }
};

#ifdef AVIAN_TAILS
const bool TailCalls = true;
#else
const bool TailCalls = false;
#endif

#if (defined AVIAN_USE_FRAME_POINTER) || (defined ARCH_powerpc)
const bool UseFramePointer = true;
#else
const bool UseFramePointer = false;
#endif

class Assembler {
 public:

  class Client {
   public:
    virtual int acquireTemporary
    (uint32_t mask = ~static_cast<uint32_t>(0)) = 0;
    virtual void releaseTemporary(int r) = 0;

    virtual void save(int r) = 0;
  };

  class Block {
   public:
    virtual unsigned resolve(unsigned start, Block* next) = 0;
  };

  class Architecture {
   public:
    virtual unsigned floatRegisterSize() = 0;

    virtual const RegisterFile* registerFile() = 0;

    virtual int scratch() = 0;
    virtual int stack() = 0;
    virtual int thread() = 0;
    virtual int returnLow() = 0;
    virtual int returnHigh() = 0;
    virtual int virtualCallTarget() = 0;
    virtual int virtualCallIndex() = 0;

    virtual bool bigEndian() = 0;

    virtual uintptr_t maximumImmediateJump() = 0;

    virtual bool alwaysCondensed(lir::BinaryOperation op) = 0;
    virtual bool alwaysCondensed(lir::TernaryOperation op) = 0;

    virtual bool reserved(int register_) = 0;

    virtual unsigned frameFootprint(unsigned footprint) = 0;
    virtual unsigned argumentFootprint(unsigned footprint) = 0;
    virtual bool argumentAlignment() = 0;
    virtual bool argumentRegisterAlignment() = 0;
    virtual unsigned argumentRegisterCount() = 0;
    virtual int argumentRegister(unsigned index) = 0;

    virtual bool hasLinkRegister() = 0;

    virtual unsigned stackAlignmentInWords() = 0;

    virtual bool matchCall(void* returnAddress, void* target) = 0;

    virtual void updateCall(lir::UnaryOperation op, void* returnAddress,
                            void* newTarget) = 0;

    virtual void setConstant(void* dst, uint64_t constant) = 0;

    virtual unsigned alignFrameSize(unsigned sizeInWords) = 0;

    virtual void nextFrame(void* start, unsigned size, unsigned footprint,
                           void* link, bool mostRecent,
                           unsigned targetParameterFootprint, void** ip,
                           void** stack) = 0;
    virtual void* frameIp(void* stack) = 0;
    virtual unsigned frameHeaderSize() = 0;
    virtual unsigned frameReturnAddressSize() = 0;
    virtual unsigned frameFooterSize() = 0;
    virtual int returnAddressOffset() = 0;
    virtual int framePointerOffset() = 0;

    virtual void plan
    (lir::UnaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     bool* thunk) = 0;

    virtual void planSource
    (lir::BinaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     unsigned bSize, bool* thunk) = 0;
     
    virtual void planDestination
    (lir::BinaryOperation op,
     unsigned aSize, uint8_t aTypeMask, uint64_t aRegisterMask,
     unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask) = 0;

    virtual void planMove
    (unsigned size, uint8_t* srcTypeMask, uint64_t* srcRegisterMask,
     uint8_t* tmpTypeMask, uint64_t* tmpRegisterMask,
     uint8_t dstTypeMask, uint64_t dstRegisterMask) = 0; 

    virtual void planSource
    (lir::TernaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask,
     unsigned cSize, bool* thunk) = 0; 

    virtual void planDestination
    (lir::TernaryOperation op,
     unsigned aSize, uint8_t aTypeMask, uint64_t aRegisterMask,
     unsigned bSize, uint8_t bTypeMask, uint64_t bRegisterMask,
     unsigned cSize, uint8_t* cTypeMask, uint64_t* cRegisterMask) = 0;

    virtual Assembler* makeAssembler(vm::Allocator*, vm::Zone*) = 0;

    virtual void acquire() = 0;
    virtual void release() = 0;
  };

  virtual void setClient(Client* client) = 0;

  virtual Architecture* arch() = 0;

  virtual void checkStackOverflow(uintptr_t handler,
                                  unsigned stackLimitOffsetFromThread) = 0;
  virtual void saveFrame(unsigned stackOffset, unsigned ipOffset) = 0;
  virtual void pushFrame(unsigned argumentCount, ...) = 0;
  virtual void allocateFrame(unsigned footprint) = 0;
  virtual void adjustFrame(unsigned difference) = 0;
  virtual void popFrame(unsigned footprint) = 0;
  virtual void popFrameForTailCall(unsigned footprint, int offset,
                                   int returnAddressSurrogate,
                                   int framePointerSurrogate) = 0;
  virtual void popFrameAndPopArgumentsAndReturn(unsigned frameFootprint,
                                                unsigned argumentFootprint)
  = 0;
  virtual void popFrameAndUpdateStackAndReturn(unsigned frameFootprint,
                                               unsigned stackOffsetFromThread)
  = 0;

  virtual void apply(lir::Operation op) = 0;
  virtual void apply(lir::UnaryOperation op, OperandInfo a) = 0;
  virtual void apply(lir::BinaryOperation op, OperandInfo a, OperandInfo b) = 0;
  virtual void apply(lir::TernaryOperation op, OperandInfo a, OperandInfo b, OperandInfo c) = 0;

  virtual void setDestination(uint8_t* dst) = 0;

  virtual void write() = 0;

  virtual Promise* offset(bool forTrace = false) = 0;

  virtual Block* endBlock(bool startNew) = 0;

  virtual void endEvent() = 0;

  virtual unsigned length() = 0;

  virtual unsigned footerSize() = 0;

  virtual void dispose() = 0;
};

} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_H
