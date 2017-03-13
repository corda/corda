/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "operations.h"

#include "multimethod.h"
#include "../multimethod.h"

namespace avian {
namespace codegen {
namespace arm {

using namespace util;

unsigned index(ArchitectureContext*,
               lir::BinaryOperation operation,
               lir::Operand::Type operand1,
               lir::Operand::Type operand2)
{
  return operation + (lir::BinaryOperationCount * (unsigned)operand1)
         + (lir::BinaryOperationCount * lir::Operand::TypeCount * (unsigned)operand2);
}

unsigned index(ArchitectureContext* con UNUSED,
               lir::TernaryOperation operation,
               lir::Operand::Type operand1)
{
  assertT(con, not isBranch(operation));

  return operation + (lir::NonBranchTernaryOperationCount * (unsigned)operand1);
}

unsigned branchIndex(ArchitectureContext* con UNUSED,
                     lir::Operand::Type operand1,
                     lir::Operand::Type operand2)
{
  return (unsigned)operand1 + (lir::Operand::TypeCount * (unsigned)operand2);
}

void populateTables(ArchitectureContext* con)
{
  const lir::Operand::Type C = lir::Operand::Type::Constant;
  const lir::Operand::Type A = lir::Operand::Type::Address;
  const lir::Operand::Type R = lir::Operand::Type::RegisterPair;
  const lir::Operand::Type M = lir::Operand::Type::Memory;

  OperationType* zo = con->operations;
  UnaryOperationType* uo = con->unaryOperations;
  BinaryOperationType* bo = con->binaryOperations;
  TernaryOperationType* to = con->ternaryOperations;
  BranchOperationType* bro = con->branchOperations;

  zo[lir::Return] = return_;
  zo[lir::LoadBarrier] = loadBarrier;
  zo[lir::StoreStoreBarrier] = storeStoreBarrier;
  zo[lir::StoreLoadBarrier] = storeLoadBarrier;
  zo[lir::Trap] = trap;

  uo[Multimethod::index(lir::LongCall, C)] = CAST1(longCallC);

  uo[Multimethod::index(lir::AlignedLongCall, C)] = CAST1(longCallC);

  uo[Multimethod::index(lir::LongJump, C)] = CAST1(longJumpC);

  uo[Multimethod::index(lir::AlignedLongJump, C)] = CAST1(longJumpC);

  uo[Multimethod::index(lir::Jump, R)] = CAST1(jumpR);
  uo[Multimethod::index(lir::Jump, C)] = CAST1(jumpC);

  uo[Multimethod::index(lir::AlignedJump, R)] = CAST1(jumpR);
  uo[Multimethod::index(lir::AlignedJump, C)] = CAST1(jumpC);

  uo[Multimethod::index(lir::Call, C)] = CAST1(callC);
  uo[Multimethod::index(lir::Call, R)] = CAST1(callR);

  uo[Multimethod::index(lir::AlignedCall, C)] = CAST1(callC);
  uo[Multimethod::index(lir::AlignedCall, R)] = CAST1(callR);

  bo[index(con, lir::Move, R, R)] = CAST2(moveRR);
  bo[index(con, lir::Move, C, R)] = CAST2(moveCR);
  bo[index(con, lir::Move, C, M)] = CAST2(moveCM);
  bo[index(con, lir::Move, M, R)] = CAST2(moveMR);
  bo[index(con, lir::Move, R, M)] = CAST2(moveRM);
  bo[index(con, lir::Move, A, R)] = CAST2(moveAR);

  bo[index(con, lir::MoveZ, R, R)] = CAST2(moveZRR);
  bo[index(con, lir::MoveZ, M, R)] = CAST2(moveZMR);
  bo[index(con, lir::MoveZ, C, R)] = CAST2(moveCR);

  bo[index(con, lir::Negate, R, R)] = CAST2(negateRR);

  bo[index(con, lir::FloatAbsolute, R, R)] = CAST2(floatAbsoluteRR);
  bo[index(con, lir::FloatNegate, R, R)] = CAST2(floatNegateRR);
  bo[index(con, lir::Float2Float, R, R)] = CAST2(float2FloatRR);
  bo[index(con, lir::Float2Int, R, R)] = CAST2(float2IntRR);
  bo[index(con, lir::Int2Float, R, R)] = CAST2(int2FloatRR);
  bo[index(con, lir::FloatSquareRoot, R, R)] = CAST2(floatSqrtRR);

  to[index(con, lir::Add, R)] = CAST3(addR);

  to[index(con, lir::Subtract, R)] = CAST3(subR);

  to[index(con, lir::Multiply, R)] = CAST3(multiplyR);

  to[index(con, lir::FloatAdd, R)] = CAST3(floatAddR);
  to[index(con, lir::FloatSubtract, R)] = CAST3(floatSubtractR);
  to[index(con, lir::FloatMultiply, R)] = CAST3(floatMultiplyR);
  to[index(con, lir::FloatDivide, R)] = CAST3(floatDivideR);

  to[index(con, lir::ShiftLeft, R)] = CAST3(shiftLeftR);
  to[index(con, lir::ShiftLeft, C)] = CAST3(shiftLeftC);

  to[index(con, lir::ShiftRight, R)] = CAST3(shiftRightR);
  to[index(con, lir::ShiftRight, C)] = CAST3(shiftRightC);

  to[index(con, lir::UnsignedShiftRight, R)] = CAST3(unsignedShiftRightR);
  to[index(con, lir::UnsignedShiftRight, C)] = CAST3(unsignedShiftRightC);

  to[index(con, lir::And, R)] = CAST3(andR);
  to[index(con, lir::And, C)] = CAST3(andC);

  to[index(con, lir::Or, R)] = CAST3(orR);

  to[index(con, lir::Xor, R)] = CAST3(xorR);

  bro[branchIndex(con, R, R)] = CAST_BRANCH(branchRR);
  bro[branchIndex(con, C, R)] = CAST_BRANCH(branchCR);
  bro[branchIndex(con, C, M)] = CAST_BRANCH(branchCM);
  bro[branchIndex(con, R, M)] = CAST_BRANCH(branchRM);
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian
