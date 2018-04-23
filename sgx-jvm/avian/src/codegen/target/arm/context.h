/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_ARM_CONTEXT_H
#define AVIAN_CODEGEN_ASSEMBLER_ARM_CONTEXT_H

#include <avian/codegen/lir.h>
#include <avian/codegen/assembler.h>
#include "avian/alloc-vector.h"

namespace vm {
class System;
class Zone;
}  // namespace vm

namespace avian {

namespace util {
class Aborter;
class Alloc;
}  // namespace util

namespace codegen {
namespace arm {

class Task;
class MyBlock;
class PoolOffset;
class ConstantPoolEntry;

class Context {
 public:
  Context(vm::System* s, util::Alloc* a, vm::Zone* zone);

  vm::System* s;
  vm::Zone* zone;
  Assembler::Client* client;
  vm::Vector code;
  Task* tasks;
  uint8_t* result;
  MyBlock* firstBlock;
  MyBlock* lastBlock;
  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  ConstantPoolEntry* constantPool;
  unsigned constantPoolCount;
};

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, lir::Operand*);

typedef void (*BinaryOperationType)(Context*,
                                    unsigned,
                                    lir::Operand*,
                                    unsigned,
                                    lir::Operand*);

typedef void (*TernaryOperationType)(Context*,
                                     unsigned,
                                     lir::Operand*,
                                     lir::Operand*,
                                     lir::Operand*);

typedef void (*BranchOperationType)(Context*,
                                    lir::TernaryOperation,
                                    unsigned,
                                    lir::Operand*,
                                    lir::Operand*,
                                    lir::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(vm::System* s) : s(s)
  {
  }

  vm::System* s;
  OperationType operations[lir::OperationCount];
  UnaryOperationType
      unaryOperations[lir::UnaryOperationCount * lir::Operand::TypeCount];
  BinaryOperationType binaryOperations[lir::BinaryOperationCount
                                       * lir::Operand::TypeCount
                                       * lir::Operand::TypeCount];
  TernaryOperationType ternaryOperations[lir::NonBranchTernaryOperationCount
                                         * lir::Operand::TypeCount];
  BranchOperationType branchOperations[lir::BranchOperationCount
                                       * lir::Operand::TypeCount
                                       * lir::Operand::TypeCount];
};

inline avian::util::Aborter* getAborter(Context* c)
{
  return c->s;
}

inline avian::util::Aborter* getAborter(ArchitectureContext* c)
{
  return c->s;
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_ARM_CONTEXT_H
