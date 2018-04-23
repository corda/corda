/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/common.h"

#include <avian/util/abort.h>

#include <avian/codegen/lir.h>

#include "context.h"
#include "operations.h"

#include "multimethod.h"
#include "../multimethod.h"

namespace avian {
namespace codegen {
namespace x86 {

using namespace util;

unsigned index(ArchitectureContext*,
               lir::BinaryOperation operation,
               lir::Operand::Type operand1,
               lir::Operand::Type operand2)
{
  return operation + ((lir::BinaryOperationCount
                       + lir::NonBranchTernaryOperationCount) * (unsigned)operand1)
         + ((lir::BinaryOperationCount + lir::NonBranchTernaryOperationCount)
            * lir::Operand::TypeCount * (unsigned)operand2);
}

unsigned index(ArchitectureContext* c UNUSED,
               lir::TernaryOperation operation,
               lir::Operand::Type operand1,
               lir::Operand::Type operand2)
{
  assertT(c, not isBranch(operation));

  return lir::BinaryOperationCount + operation
         + ((lir::BinaryOperationCount + lir::NonBranchTernaryOperationCount)
            * (unsigned)operand1)
         + ((lir::BinaryOperationCount + lir::NonBranchTernaryOperationCount)
            * lir::Operand::TypeCount * (unsigned)operand2);
}

unsigned branchIndex(ArchitectureContext* c UNUSED,
                     lir::Operand::Type operand1,
                     lir::Operand::Type operand2)
{
  return (unsigned)operand1 + (lir::Operand::TypeCount * (unsigned)operand2);
}

void populateTables(ArchitectureContext* c)
{
  const lir::Operand::Type C = lir::Operand::Type::Constant;
  const lir::Operand::Type A = lir::Operand::Type::Address;
  const lir::Operand::Type R = lir::Operand::Type::RegisterPair;
  const lir::Operand::Type M = lir::Operand::Type::Memory;

  OperationType* zo = c->operations;
  UnaryOperationType* uo = c->unaryOperations;
  BinaryOperationType* bo = c->binaryOperations;
  BranchOperationType* bro = c->branchOperations;

  zo[lir::Return] = return_;
  zo[lir::LoadBarrier] = ignore;
  zo[lir::StoreStoreBarrier] = ignore;
  zo[lir::StoreLoadBarrier] = storeLoadBarrier;
  zo[lir::Trap] = trap;

  uo[Multimethod::index(lir::Call, C)] = CAST1(callC);
  uo[Multimethod::index(lir::Call, R)] = CAST1(callR);
  uo[Multimethod::index(lir::Call, M)] = CAST1(callM);

  uo[Multimethod::index(lir::AlignedCall, C)] = CAST1(alignedCallC);

  uo[Multimethod::index(lir::LongCall, C)] = CAST1(longCallC);

  uo[Multimethod::index(lir::AlignedLongCall, C)] = CAST1(alignedLongCallC);

  uo[Multimethod::index(lir::Jump, R)] = CAST1(jumpR);
  uo[Multimethod::index(lir::Jump, C)] = CAST1(jumpC);
  uo[Multimethod::index(lir::Jump, M)] = CAST1(jumpM);

  uo[Multimethod::index(lir::AlignedJump, C)] = CAST1(alignedJumpC);

  uo[Multimethod::index(lir::LongJump, C)] = CAST1(longJumpC);

  uo[Multimethod::index(lir::AlignedLongJump, C)] = CAST1(alignedLongJumpC);

  bo[index(c, lir::Negate, R, R)] = CAST2(negateRR);

  bo[index(c, lir::FloatNegate, R, R)] = CAST2(floatNegateRR);

  bo[index(c, lir::Move, R, R)] = CAST2(moveRR);
  bo[index(c, lir::Move, C, R)] = CAST2(moveCR);
  bo[index(c, lir::Move, M, R)] = CAST2(moveMR);
  bo[index(c, lir::Move, R, M)] = CAST2(moveRM);
  bo[index(c, lir::Move, C, M)] = CAST2(moveCM);
  bo[index(c, lir::Move, A, R)] = CAST2(moveAR);

  bo[index(c, lir::FloatSquareRoot, R, R)] = CAST2(floatSqrtRR);
  bo[index(c, lir::FloatSquareRoot, M, R)] = CAST2(floatSqrtMR);

  bo[index(c, lir::MoveZ, R, R)] = CAST2(moveZRR);
  bo[index(c, lir::MoveZ, M, R)] = CAST2(moveZMR);
  bo[index(c, lir::MoveZ, C, R)] = CAST2(moveZCR);

  bo[index(c, lir::Add, R, R)] = CAST2(addRR);
  bo[index(c, lir::Add, C, R)] = CAST2(addCR);

  bo[index(c, lir::Subtract, C, R)] = CAST2(subtractCR);
  bo[index(c, lir::Subtract, R, R)] = CAST2(subtractRR);

  bo[index(c, lir::FloatAdd, R, R)] = CAST2(floatAddRR);
  bo[index(c, lir::FloatAdd, M, R)] = CAST2(floatAddMR);

  bo[index(c, lir::FloatSubtract, R, R)] = CAST2(floatSubtractRR);
  bo[index(c, lir::FloatSubtract, M, R)] = CAST2(floatSubtractMR);

  bo[index(c, lir::And, R, R)] = CAST2(andRR);
  bo[index(c, lir::And, C, R)] = CAST2(andCR);

  bo[index(c, lir::Or, R, R)] = CAST2(orRR);
  bo[index(c, lir::Or, C, R)] = CAST2(orCR);

  bo[index(c, lir::Xor, R, R)] = CAST2(xorRR);
  bo[index(c, lir::Xor, C, R)] = CAST2(xorCR);

  bo[index(c, lir::Multiply, R, R)] = CAST2(multiplyRR);
  bo[index(c, lir::Multiply, C, R)] = CAST2(multiplyCR);

  bo[index(c, lir::Divide, R, R)] = CAST2(divideRR);

  bo[index(c, lir::FloatMultiply, R, R)] = CAST2(floatMultiplyRR);
  bo[index(c, lir::FloatMultiply, M, R)] = CAST2(floatMultiplyMR);

  bo[index(c, lir::FloatDivide, R, R)] = CAST2(floatDivideRR);
  bo[index(c, lir::FloatDivide, M, R)] = CAST2(floatDivideMR);

  bo[index(c, lir::Remainder, R, R)] = CAST2(remainderRR);

  bo[index(c, lir::ShiftLeft, R, R)] = CAST2(shiftLeftRR);
  bo[index(c, lir::ShiftLeft, C, R)] = CAST2(shiftLeftCR);

  bo[index(c, lir::ShiftRight, R, R)] = CAST2(shiftRightRR);
  bo[index(c, lir::ShiftRight, C, R)] = CAST2(shiftRightCR);

  bo[index(c, lir::UnsignedShiftRight, R, R)] = CAST2(unsignedShiftRightRR);
  bo[index(c, lir::UnsignedShiftRight, C, R)] = CAST2(unsignedShiftRightCR);

  bo[index(c, lir::Float2Float, R, R)] = CAST2(float2FloatRR);
  bo[index(c, lir::Float2Float, M, R)] = CAST2(float2FloatMR);

  bo[index(c, lir::Float2Int, R, R)] = CAST2(float2IntRR);
  bo[index(c, lir::Float2Int, M, R)] = CAST2(float2IntMR);

  bo[index(c, lir::Int2Float, R, R)] = CAST2(int2FloatRR);
  bo[index(c, lir::Int2Float, M, R)] = CAST2(int2FloatMR);

  bo[index(c, lir::Absolute, R, R)] = CAST2(absoluteRR);
  bo[index(c, lir::FloatAbsolute, R, R)] = CAST2(floatAbsoluteRR);

  bro[branchIndex(c, R, R)] = CAST_BRANCH(branchRR);
  bro[branchIndex(c, C, R)] = CAST_BRANCH(branchCR);
  bro[branchIndex(c, C, M)] = CAST_BRANCH(branchCM);
  bro[branchIndex(c, R, M)] = CAST_BRANCH(branchRM);
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
