/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_H
#define AVIAN_CODEGEN_COMPILER_H

#include <avian/system/system.h>
#include <avian/util/slice.h>
#include "avian/zone.h"
#include "assembler.h"
#include "ir.h"

namespace avian {
namespace codegen {

class TraceHandler {
 public:
  virtual void handleTrace(Promise* address, unsigned argumentIndex) = 0;
};

template <size_t N>
class Args {
 public:
  ir::Value* values[N];

  template <class... Ts>
  Args(Ts... ts)
#ifndef _MSC_VER
      : values{ts...}
#endif
  {
#ifdef _MSC_VER
    setArrayElements(values, ts...);
#endif
  }

  operator util::Slice<ir::Value*>()
  {
    return util::Slice<ir::Value*>(&values[0], N);
  }
};

inline Args<0> args()
{
  return Args<0>();
}

inline Args<1> args(ir::Value* first)
{
  return Args<1>{first};
}

template <class... Ts>
inline Args<1 + util::ArgumentCount<Ts...>::Result> args(ir::Value* first,
                                                         Ts... rest)
{
  return Args<1 + util::ArgumentCount<Ts...>::Result>{first, rest...};
}

class Compiler {
 public:
  class Client {
   public:
    virtual intptr_t getThunk(lir::UnaryOperation op, unsigned size) = 0;
    virtual intptr_t getThunk(lir::BinaryOperation op,
                              unsigned size,
                              unsigned resultSize) = 0;
    virtual intptr_t getThunk(lir::TernaryOperation op,
                              unsigned size,
                              unsigned resultSize,
                              bool* threadParameter) = 0;
  };

  static const unsigned Aligned = 1 << 0;
  static const unsigned NoReturn = 1 << 1;
  static const unsigned TailJump = 1 << 2;
  static const unsigned LongJumpOrCall = 1 << 3;

  class State {
  };

  virtual State* saveState() = 0;
  virtual void restoreState(State* state) = 0;

  virtual void init(unsigned logicalCodeSize,
                    unsigned parameterFootprint,
                    unsigned localFootprint,
                    unsigned alignedFrameSize) = 0;

  virtual void extendLogicalCode(unsigned more) = 0;

  virtual void visitLogicalIp(unsigned logicalIp) = 0;
  virtual void startLogicalIp(unsigned logicalIp) = 0;

  virtual Promise* machineIp(unsigned logicalIp) = 0;

  virtual Promise* poolAppend(intptr_t value) = 0;
  virtual Promise* poolAppendPromise(Promise* value) = 0;

  virtual ir::Value* constant(int64_t value, ir::Type type) = 0;
  virtual ir::Value* promiseConstant(Promise* value, ir::Type type) = 0;
  virtual ir::Value* address(ir::Type type, Promise* address) = 0;
  virtual ir::Value* memory(ir::Value* base,
                            ir::Type type,
                            int displacement = 0,
                            ir::Value* index = 0) = 0;

  virtual ir::Value* threadRegister() = 0;

  virtual void push(ir::Type type, ir::Value* value) = 0;
  virtual void save(ir::Type type, ir::Value* value) = 0;
  virtual ir::Value* pop(ir::Type type) = 0;
  virtual void pushed(ir::Type type) = 0;
  virtual void popped(unsigned footprint) = 0;
  virtual unsigned topOfStack() = 0;
  virtual ir::Value* peek(unsigned footprint, unsigned index) = 0;

  virtual ir::Value* nativeCall(ir::Value* address,
                                unsigned flags,
                                TraceHandler* traceHandler,
                                ir::Type resultType,
                                util::Slice<ir::Value*> arguments) = 0;

  virtual ir::Value* stackCall(ir::Value* address,
                               unsigned flags,
                               TraceHandler* traceHandler,
                               ir::Type resultType,
                               util::Slice<ir::Value*> arguments) = 0;

  virtual void return_(ir::Value* value) = 0;
  virtual void return_() = 0;

  virtual void initLocal(unsigned index, ir::Type type) = 0;
  virtual void initLocalsFromLogicalIp(unsigned logicalIp) = 0;
  virtual void storeLocal(ir::Value* src, unsigned index) = 0;
  virtual ir::Value* loadLocal(ir::Type type, unsigned index) = 0;
  virtual void saveLocals() = 0;

  virtual void checkBounds(ir::Value* object,
                           unsigned lengthOffset,
                           ir::Value* index,
                           intptr_t handler) = 0;

  virtual ir::Value* truncateThenExtend(ir::ExtendMode extendMode,
                                        ir::Type extendType,
                                        ir::Type truncateType,
                                        ir::Value* src) = 0;

  virtual ir::Value* truncate(ir::Type type, ir::Value* src) = 0;

  virtual void store(ir::Value* src, ir::Value* dst) = 0;
  virtual ir::Value* load(ir::ExtendMode extendMode,
                          ir::Value* src,
                          ir::Type dstType) = 0;

  virtual void condJump(lir::TernaryOperation op,
                        ir::Value* a,
                        ir::Value* b,
                        ir::Value* address) = 0;

  virtual void jmp(ir::Value* address) = 0;
  virtual void exit(ir::Value* address) = 0;

  virtual ir::Value* binaryOp(lir::TernaryOperation op,
                              ir::Type type,
                              ir::Value* a,
                              ir::Value* b) = 0;
  virtual ir::Value* unaryOp(lir::BinaryOperation op, ir::Value* a) = 0;
  virtual void nullaryOp(lir::Operation op) = 0;

  virtual ir::Value* f2f(ir::Type resType, ir::Value* a) = 0;
  virtual ir::Value* f2i(ir::Type resType, ir::Value* a) = 0;
  virtual ir::Value* i2f(ir::Type resType, ir::Value* a) = 0;

  virtual void compile(uintptr_t stackOverflowHandler,
                       unsigned stackLimitOffset) = 0;
  virtual unsigned resolve(uint8_t* dst) = 0;
  virtual unsigned poolSize() = 0;
  virtual void write() = 0;

  virtual void dispose() = 0;
};

Compiler* makeCompiler(vm::System* system,
                       Assembler* assembler,
                       vm::Zone* zone,
                       Compiler::Client* client);

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_H
