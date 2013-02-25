/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_MULTIMETHOD_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_MULTIMETHOD_H

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)
#define CAST3(x) reinterpret_cast<TernaryOperationType>(x)
#define CAST_BRANCH(x) reinterpret_cast<BranchOperationType>(x)

namespace avian {
namespace codegen {
namespace powerpc {

unsigned index(ArchitectureContext*,
      lir::BinaryOperation operation,
      lir::OperandType operand1,
      lir::OperandType operand2);

unsigned index(ArchitectureContext* c UNUSED,
      lir::TernaryOperation operation,
      lir::OperandType operand1);

unsigned branchIndex(ArchitectureContext* c UNUSED, lir::OperandType operand1,
            lir::OperandType operand2);

void populateTables(ArchitectureContext* c);

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_MULTIMETHOD_H
