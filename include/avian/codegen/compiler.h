/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_H
#define AVIAN_CODEGEN_COMPILER_H

#include <avian/system/system.h>
#include "avian/zone.h"
#include "assembler.h"

namespace avian {
namespace codegen {

class TraceHandler {
 public:
  virtual void handleTrace(Promise* address, unsigned argumentIndex) = 0;
};

class Compiler {
 public:
  class Client {
   public:
    virtual intptr_t getThunk(lir::UnaryOperation op, unsigned size) = 0;
    virtual intptr_t getThunk(lir::BinaryOperation op, unsigned size,
                              unsigned resultSize) = 0;
    virtual intptr_t getThunk(lir::TernaryOperation op, unsigned size,
                              unsigned resultSize, bool* threadParameter) = 0;
  };
  
  static const unsigned Aligned  = 1 << 0;
  static const unsigned NoReturn = 1 << 1;
  static const unsigned TailJump = 1 << 2;
  static const unsigned LongJumpOrCall = 1 << 3;

  enum OperandType {
    ObjectType,
    AddressType,
    IntegerType,
    FloatType,
    VoidType
  };

  class Operand { };
  class State { };
  class Subroutine { };

  virtual State* saveState() = 0;
  virtual void restoreState(State* state) = 0;

  virtual Subroutine* startSubroutine() = 0;
  virtual void returnFromSubroutine(Subroutine* subroutine, Operand* address)
  = 0;
  virtual void linkSubroutine(Subroutine* subroutine) = 0;

  virtual void init(unsigned logicalCodeSize, unsigned parameterFootprint,
                    unsigned localFootprint, unsigned alignedFrameSize) = 0;

  virtual void visitLogicalIp(unsigned logicalIp) = 0;
  virtual void startLogicalIp(unsigned logicalIp) = 0;

  virtual Promise* machineIp(unsigned logicalIp) = 0;

  virtual Promise* poolAppend(intptr_t value) = 0;
  virtual Promise* poolAppendPromise(Promise* value) = 0;

  virtual Operand* constant(int64_t value, OperandType type) = 0;
  virtual Operand* promiseConstant(Promise* value, OperandType type) = 0;
  virtual Operand* address(Promise* address) = 0;
  virtual Operand* memory(Operand* base,
                          OperandType type,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1) = 0;

  virtual Operand* register_(int number) = 0;

  virtual void push(unsigned footprint) = 0;
  virtual void push(unsigned footprint, Operand* value) = 0;
  virtual void save(unsigned footprint, Operand* value) = 0;
  virtual Operand* pop(unsigned footprint) = 0;
  virtual void pushed() = 0;
  virtual void popped(unsigned footprint) = 0;
  virtual unsigned topOfStack() = 0;
  virtual Operand* peek(unsigned footprint, unsigned index) = 0;

  virtual Operand* call(Operand* address,
                        unsigned flags,
                        TraceHandler* traceHandler,
                        unsigned resultSize,
                        OperandType resultType,
                        unsigned argumentCount,
                        ...) = 0;

  virtual Operand* stackCall(Operand* address,
                             unsigned flags,
                             TraceHandler* traceHandler,
                             unsigned resultSize,
                             OperandType resultType,
                             unsigned argumentFootprint) = 0;

  virtual void return_(unsigned size, Operand* value) = 0;

  virtual void initLocal(unsigned size, unsigned index, OperandType type) = 0;
  virtual void initLocalsFromLogicalIp(unsigned logicalIp) = 0;
  virtual void storeLocal(unsigned footprint, Operand* src,
                          unsigned index) = 0;
  virtual Operand* loadLocal(unsigned footprint, unsigned index) = 0;
  virtual void saveLocals() = 0;

  virtual void checkBounds(Operand* object, unsigned lengthOffset,
                           Operand* index, intptr_t handler) = 0;

  virtual void store(unsigned srcSize, Operand* src, unsigned dstSize,
                     Operand* dst) = 0;
  virtual Operand* load(unsigned srcSize, unsigned srcSelectSize, Operand* src,
                        unsigned dstSize) = 0;
  virtual Operand* loadz(unsigned size, unsigned srcSelectSize, Operand* src,
                         unsigned dstSize) = 0;


  virtual void condJump(lir::TernaryOperation type, unsigned size, Operand* a, Operand* b, Operand* address) = 0;

  virtual void jmp(Operand* address) = 0;
  virtual void exit(Operand* address) = 0;

  virtual Operand* binaryOp(lir::TernaryOperation type, unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* unaryOp(lir::BinaryOperation type, unsigned size, Operand* a) = 0;
  virtual void nullaryOp(lir::Operation type) = 0;

  virtual Operand* f2f(unsigned aSize, unsigned resSize, Operand* a) = 0;
  virtual Operand* f2i(unsigned aSize, unsigned resSize, Operand* a) = 0;
  virtual Operand* i2f(unsigned aSize, unsigned resSize, Operand* a) = 0;

  virtual void compile(uintptr_t stackOverflowHandler,
                       unsigned stackLimitOffset) = 0;
  virtual unsigned resolve(uint8_t* dst) = 0;
  virtual unsigned poolSize() = 0;
  virtual void write() = 0;

  virtual void dispose() = 0;
};

Compiler*
makeCompiler(vm::System* system, Assembler* assembler, vm::Zone* zone,
             Compiler::Client* client);

} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_H
