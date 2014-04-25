/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_ARM_OPERATIONS_H
#define AVIAN_CODEGEN_ASSEMBLER_ARM_OPERATIONS_H

#include "registers.h"

namespace vm {
class System;
}

namespace avian {
namespace codegen {
namespace arm {

class Context;

// shortcut functions

inline int newTemp(Context* con) {
  return con->client->acquireTemporary(GPR_MASK);
}

inline int newTemp(Context* con, unsigned mask) {
  return con->client->acquireTemporary(mask);
}

inline void freeTemp(Context* con, int r) {
  con->client->releaseTemporary(r);
}

inline int64_t getValue(lir::Constant* con) {
  return con->value->value();
}

inline lir::Register makeTemp(Context* con) {
  lir::Register tmp(newTemp(con));
  return tmp;
}

inline lir::Register makeTemp64(Context* con) {
  lir::Register tmp(newTemp(con), newTemp(con));
  return tmp;
}

inline void freeTemp(Context* con, const lir::Register& tmp) {
  if (tmp.low != lir::NoRegister) freeTemp(con, tmp.low);
  if (tmp.high != lir::NoRegister) freeTemp(con, tmp.high);
}

void shiftLeftR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void moveRR(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void shiftLeftC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t);

void shiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void shiftRightC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t);

void unsignedShiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void unsignedShiftRightC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t);

bool needJump(MyBlock* b);

unsigned padding(MyBlock* b, unsigned offset);

void resolve(MyBlock* b);

void jumpR(Context* con, unsigned size UNUSED, lir::Register* target);

void swapRR(Context* con, unsigned aSize, lir::Register* a,
       unsigned bSize, lir::Register* b);

void moveRR(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void moveZRR(Context* con, unsigned srcSize, lir::Register* src,
        unsigned, lir::Register* dst);

void moveCR(Context* con, unsigned size, lir::Constant* src,
            unsigned, lir::Register* dst);

void moveCR2(Context* con, unsigned size, lir::Constant* src,
        lir::Register* dst, Promise* callOffset);

void moveCR(Context* con, unsigned size, lir::Constant* src,
       unsigned, lir::Register* dst);

void addR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void subR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void addC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void subC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void multiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void floatAbsoluteRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b);

void floatNegateRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b);

void float2FloatRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b);

void float2IntRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b);

void int2FloatRR(Context* con, unsigned, lir::Register* a, unsigned size, lir::Register* b);

void floatSqrtRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b);

void floatAddR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void floatSubtractR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void floatMultiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void floatDivideR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

int normalize(Context* con, int offset, int index, unsigned scale, 
          bool* preserveIndex, bool* release);

void store(Context* con, unsigned size, lir::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex);

void moveRM(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize UNUSED, lir::Memory* dst);

void load(Context* con, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, lir::Register* dst,
     bool preserveIndex, bool signExtend);

void moveMR(Context* con, unsigned srcSize, lir::Memory* src,
       unsigned dstSize, lir::Register* dst);

void moveZMR(Context* con, unsigned srcSize, lir::Memory* src,
        unsigned dstSize, lir::Register* dst);

void andR(Context* con, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst);

void andC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void orR(Context* con, unsigned size, lir::Register* a,
    lir::Register* b, lir::Register* dst);

void xorR(Context* con, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst);

void moveAR2(Context* con, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst);

void moveAR(Context* con, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst);

void compareRR(Context* con, unsigned aSize, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b);

void compareCR(Context* con, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b);

void compareCM(Context* con, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Memory* b);

void compareRM(Context* con, unsigned aSize, lir::Register* a,
          unsigned bSize, lir::Memory* b);

int32_t
branch(Context* con, lir::TernaryOperation op);

void conditional(Context* con, int32_t branch, lir::Constant* target);

void branch(Context* con, lir::TernaryOperation op, lir::Constant* target);

void branchLong(Context* con, lir::TernaryOperation op, lir::Operand* al,
           lir::Operand* ah, lir::Operand* bl,
           lir::Operand* bh, lir::Constant* target,
           BinaryOperationType compareSigned,
           BinaryOperationType compareUnsigned);

void branchRR(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Register* b,
         lir::Constant* target);

void branchCR(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Register* b,
         lir::Constant* target);

void branchRM(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Memory* b,
         lir::Constant* target);

void branchCM(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Memory* b,
         lir::Constant* target);

ShiftMaskPromise*
shiftMaskPromise(Context* con, Promise* base, unsigned shift, int64_t mask);

void moveCM(Context* con, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Memory* dst);

void negateRR(Context* con, unsigned srcSize, lir::Register* src,
         unsigned dstSize UNUSED, lir::Register* dst);

void callR(Context* con, unsigned size UNUSED, lir::Register* target);

void callC(Context* con, unsigned size UNUSED, lir::Constant* target);

void longCallC(Context* con, unsigned size UNUSED, lir::Constant* target);

void longJumpC(Context* con, unsigned size UNUSED, lir::Constant* target);

void jumpC(Context* con, unsigned size UNUSED, lir::Constant* target);

void return_(Context* con);

void trap(Context* con);

void loadBarrier(Context*);

void storeStoreBarrier(Context*);

void storeLoadBarrier(Context*);

} // namespace arm
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_ARM_OPERATIONS_H

