/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_CONTEXT_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_CONTEXT_H

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)
#define CAST_BRANCH(x) reinterpret_cast<BranchOperationType>(x)

#include <stdint.h>

#include "avian/alloc-vector.h"

#include <avian/codegen/lir.h>
#include <avian/codegen/assembler.h>

#include <avian/system/system.h>

namespace vm {
class System;
class Alloc;
class Zone;
}  // namespace vm

namespace avian {

namespace util {
class Aborter;
}  // namespace util

namespace codegen {
namespace x86 {

class Context;
class MyBlock;
class Task;

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, lir::Operand*);

typedef void (*BinaryOperationType)(Context*,
                                    unsigned,
                                    lir::Operand*,
                                    unsigned,
                                    lir::Operand*);

typedef void (*BranchOperationType)(Context*,
                                    lir::TernaryOperation,
                                    unsigned,
                                    lir::Operand*,
                                    lir::Operand*,
                                    lir::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(vm::System* s, bool useNativeFeatures);

  vm::System* s;
  bool useNativeFeatures;
  OperationType operations[lir::OperationCount];
  UnaryOperationType
      unaryOperations[lir::UnaryOperationCount * lir::Operand::TypeCount];
  BinaryOperationType binaryOperations
      [(lir::BinaryOperationCount + lir::NonBranchTernaryOperationCount)
       * lir::Operand::TypeCount * lir::Operand::TypeCount];
  BranchOperationType branchOperations[lir::BranchOperationCount
                                       * lir::Operand::TypeCount
                                       * lir::Operand::TypeCount];
};

class Context {
 public:
  Context(vm::System* s,
          util::Alloc* a,
          vm::Zone* zone,
          ArchitectureContext* ac);

  vm::System* s;
  vm::Zone* zone;
  Assembler::Client* client;
  vm::Vector code;
  Task* tasks;
  uint8_t* result;
  MyBlock* firstBlock;
  MyBlock* lastBlock;
  ArchitectureContext* ac;
};

inline avian::util::Aborter* getAborter(Context* c)
{
  return c->s;
}

inline avian::util::Aborter* getAborter(ArchitectureContext* c)
{
  return c->s;
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_CONTEXT_H
