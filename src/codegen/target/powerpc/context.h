/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_CONTEXT_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_CONTEXT_H

#include <avian/vm/codegen/assembler.h>
#include "avian/alloc-vector.h"

#ifdef powerpc
#undef powerpc
#endif

namespace vm {
class System;
class Allocator;
class Zone;
} // namespace vm


namespace avian {
namespace codegen {
namespace powerpc {

class Task;
class JumpOffset;
class ConstantPoolEntry;
class MyBlock;

class Context {
 public:
  Context(vm::System* s, vm::Allocator* a, vm::Zone* zone);

  vm::System* s;
  vm::Zone* zone;
  Assembler::Client* client;
  vm::Vector code;
  Task* tasks;
  uint8_t* result;
  MyBlock* firstBlock;
  MyBlock* lastBlock;
  JumpOffset* jumpOffsetHead;
  JumpOffset* jumpOffsetTail;
  ConstantPoolEntry* constantPool;
  unsigned constantPoolCount;
};

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, lir::Operand*);

typedef void (*BinaryOperationType)
(Context*, unsigned, lir::Operand*, unsigned, lir::Operand*);

typedef void (*TernaryOperationType)
(Context*, unsigned, lir::Operand*, lir::Operand*,
 lir::Operand*);

typedef void (*BranchOperationType)
(Context*, lir::TernaryOperation, unsigned, lir::Operand*,
 lir::Operand*, lir::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(vm::System* s): s(s) { }

  vm::System* s;
  OperationType operations[lir::OperationCount];
  UnaryOperationType unaryOperations[lir::UnaryOperationCount
                                     * lir::OperandTypeCount];
  BinaryOperationType binaryOperations
  [lir::BinaryOperationCount * lir::OperandTypeCount * lir::OperandTypeCount];
  TernaryOperationType ternaryOperations
  [lir::NonBranchTernaryOperationCount * lir::OperandTypeCount];
  BranchOperationType branchOperations
  [lir::BranchOperationCount * lir::OperandTypeCount * lir::OperandTypeCount];
};

inline avian::util::Aborter* getAborter(Context* con) {
  return con->s;
}

inline avian::util::Aborter* getAborter(ArchitectureContext* con) {
  return con->s;
}

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_CONTEXT_H
