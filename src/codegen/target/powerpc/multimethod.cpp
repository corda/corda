/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"
#include "avian/common.h"
#include "operations.h"

#include "multimethod.h"
#include "../multimethod.h"

namespace avian {
namespace codegen {
namespace powerpc {

using namespace util;

unsigned index(ArchitectureContext*,
      lir::BinaryOperation operation,
      lir::OperandType operand1,
      lir::OperandType operand2)
{
  return operation
    + (lir::BinaryOperationCount * operand1)
    + (lir::BinaryOperationCount * lir::OperandTypeCount * operand2);
}

unsigned index(ArchitectureContext* c UNUSED,
      lir::TernaryOperation operation,
      lir::OperandType operand1)
{
  assert(c, not isBranch(operation));

  return operation + (lir::NonBranchTernaryOperationCount * operand1);
}

unsigned branchIndex(ArchitectureContext* c UNUSED, lir::OperandType operand1,
            lir::OperandType operand2)
{
  return operand1 + (lir::OperandTypeCount * operand2);
}

void populateTables(ArchitectureContext* c) {
  const lir::OperandType C = lir::ConstantOperand;
  const lir::OperandType A = lir::AddressOperand;
  const lir::OperandType R = lir::RegisterOperand;
  const lir::OperandType M = lir::MemoryOperand;

  OperationType* zo = c->operations;
  UnaryOperationType* uo = c->unaryOperations;
  BinaryOperationType* bo = c->binaryOperations;
  TernaryOperationType* to = c->ternaryOperations;
  BranchOperationType* bro = c->branchOperations;

  zo[lir::Return] = return_;
  zo[lir::LoadBarrier] = memoryBarrier;
  zo[lir::StoreStoreBarrier] = memoryBarrier;
  zo[lir::StoreLoadBarrier] = memoryBarrier;
  zo[lir::Trap] = trap;

  uo[Multimethod::index(lir::LongCall, C)] = CAST1(longCallC);

  uo[Multimethod::index(lir::AlignedLongCall, C)] = CAST1(alignedLongCallC);

  uo[Multimethod::index(lir::LongJump, C)] = CAST1(longJumpC);

  uo[Multimethod::index(lir::AlignedLongJump, C)] = CAST1(alignedLongJumpC);

  uo[Multimethod::index(lir::Jump, R)] = CAST1(jumpR);
  uo[Multimethod::index(lir::Jump, C)] = CAST1(jumpC);

  uo[Multimethod::index(lir::AlignedJump, R)] = CAST1(jumpR);
  uo[Multimethod::index(lir::AlignedJump, C)] = CAST1(jumpC);

  uo[Multimethod::index(lir::Call, C)] = CAST1(callC);
  uo[Multimethod::index(lir::Call, R)] = CAST1(callR);

  uo[Multimethod::index(lir::AlignedCall, C)] = CAST1(callC);
  uo[Multimethod::index(lir::AlignedCall, R)] = CAST1(callR);

  bo[index(c, lir::Move, R, R)] = CAST2(moveRR);
  bo[index(c, lir::Move, C, R)] = CAST2(moveCR);
  bo[index(c, lir::Move, C, M)] = CAST2(moveCM);
  bo[index(c, lir::Move, M, R)] = CAST2(moveMR);
  bo[index(c, lir::Move, R, M)] = CAST2(moveRM);
  bo[index(c, lir::Move, A, R)] = CAST2(moveAR);

  bo[index(c, lir::MoveZ, R, R)] = CAST2(moveZRR);
  bo[index(c, lir::MoveZ, M, R)] = CAST2(moveZMR);
  bo[index(c, lir::MoveZ, C, R)] = CAST2(moveCR);

  bo[index(c, lir::Negate, R, R)] = CAST2(negateRR);

  to[index(c, lir::Add, R)] = CAST3(addR);
  to[index(c, lir::Add, C)] = CAST3(addC);

  to[index(c, lir::Subtract, R)] = CAST3(subR);
  to[index(c, lir::Subtract, C)] = CAST3(subC);

  to[index(c, lir::Multiply, R)] = CAST3(multiplyR);

  to[index(c, lir::Divide, R)] = CAST3(divideR);

  to[index(c, lir::Remainder, R)] = CAST3(remainderR);

  to[index(c, lir::ShiftLeft, R)] = CAST3(shiftLeftR);
  to[index(c, lir::ShiftLeft, C)] = CAST3(shiftLeftC);

  to[index(c, lir::ShiftRight, R)] = CAST3(shiftRightR);
  to[index(c, lir::ShiftRight, C)] = CAST3(shiftRightC);

  to[index(c, lir::UnsignedShiftRight, R)] = CAST3(unsignedShiftRightR);
  to[index(c, lir::UnsignedShiftRight, C)] = CAST3(unsignedShiftRightC);

  to[index(c, lir::And, C)] = CAST3(andC);
  to[index(c, lir::And, R)] = CAST3(andR);

  to[index(c, lir::Or, C)] = CAST3(orC);
  to[index(c, lir::Or, R)] = CAST3(orR);

  to[index(c, lir::Xor, C)] = CAST3(xorC);
  to[index(c, lir::Xor, R)] = CAST3(xorR);

  bro[branchIndex(c, R, R)] = CAST_BRANCH(branchRR);
  bro[branchIndex(c, C, R)] = CAST_BRANCH(branchCR);
  bro[branchIndex(c, C, M)] = CAST_BRANCH(branchCM);
  bro[branchIndex(c, R, M)] = CAST_BRANCH(branchRM);
}

} // namespace powerpc
} // namespace codegen
} // namespace avian
