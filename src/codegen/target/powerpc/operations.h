/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_OPERATIONS_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_OPERATIONS_H

#include "context.h"

namespace avian {
namespace codegen {
namespace powerpc {

inline void emit(Context* con, int code) { con->code.append4(vm::targetV4(code)); }
inline int newTemp(Context* con) { return con->client->acquireTemporary(); }
inline void freeTemp(Context* con, int r) { con->client->releaseTemporary(r); }
inline int64_t getValue(lir::Constant* c) { return c->value->value(); }

void andC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void shiftLeftR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void moveRR(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void shiftLeftC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t);

void shiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void shiftRightC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t);

void unsignedShiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void unsignedShiftRightC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t);

void jumpR(Context* c, unsigned size UNUSED, lir::Register* target);

void swapRR(Context* c, unsigned aSize, lir::Register* a,
       unsigned bSize, lir::Register* b);

void moveRR(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void moveZRR(Context* c, unsigned srcSize, lir::Register* src,
        unsigned, lir::Register* dst);

void moveCR2(Context* c, unsigned, lir::Constant* src,
       unsigned dstSize, lir::Register* dst, unsigned promiseOffset);

void moveCR(Context* c, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Register* dst);

void addR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void addC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t);

void subR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void subC(Context* c, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t);

void multiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

void divideR(Context* con, unsigned size UNUSED, lir::Register* a, lir::Register* b, lir::Register* t);

void remainderR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t);

int
normalize(Context* c, int offset, int index, unsigned scale, 
          bool* preserveIndex, bool* release);

void store(Context* c, unsigned size, lir::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex);

void moveRM(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize UNUSED, lir::Memory* dst);

void moveAndUpdateRM(Context* c, unsigned srcSize UNUSED, lir::Register* src,
                unsigned dstSize UNUSED, lir::Memory* dst);

void load(Context* c, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, lir::Register* dst,
     bool preserveIndex, bool signExtend);

void moveMR(Context* c, unsigned srcSize, lir::Memory* src,
       unsigned dstSize, lir::Register* dst);

void moveZMR(Context* c, unsigned srcSize, lir::Memory* src,
        unsigned dstSize, lir::Register* dst);

void andR(Context* c, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst);

void andC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void orR(Context* c, unsigned size, lir::Register* a,
    lir::Register* b, lir::Register* dst);

void orC(Context* c, unsigned size, lir::Constant* a,
    lir::Register* b, lir::Register* dst);

void xorR(Context* c, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst);

void xorC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void moveAR2(Context* c, unsigned srcSize UNUSED, lir::Address* src,
        unsigned dstSize, lir::Register* dst, unsigned promiseOffset);

void moveAR(Context* c, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst);

void compareRR(Context* c, unsigned aSize UNUSED, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b);

void compareCR(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b);

void compareCM(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Memory* b);

void compareRM(Context* c, unsigned aSize, lir::Register* a,
          unsigned bSize, lir::Memory* b);

void compareUnsignedRR(Context* c, unsigned aSize UNUSED, lir::Register* a,
                  unsigned bSize UNUSED, lir::Register* b);

void compareUnsignedCR(Context* c, unsigned aSize, lir::Constant* a,
                  unsigned bSize, lir::Register* b);

int32_t branch(Context* c, lir::TernaryOperation op);

void conditional(Context* c, int32_t branch, lir::Constant* target);

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target);

void branchLong(Context* c, lir::TernaryOperation op, lir::Operand* al,
           lir::Operand* ah, lir::Operand* bl,
           lir::Operand* bh, lir::Constant* target,
           BinaryOperationType compareSigned,
           BinaryOperationType compareUnsigned);

void branchRR(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Register* b,
         lir::Constant* target);

void branchCR(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Register* b,
         lir::Constant* target);

void branchRM(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Memory* b,
         lir::Constant* target);

void branchCM(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Memory* b,
         lir::Constant* target);

void moveCM(Context* c, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Memory* dst);

void negateRR(Context* c, unsigned srcSize, lir::Register* src,
         unsigned dstSize UNUSED, lir::Register* dst);

void callR(Context* c, unsigned size UNUSED, lir::Register* target);

void callC(Context* c, unsigned size UNUSED, lir::Constant* target);

void longCallC(Context* c, unsigned size UNUSED, lir::Constant* target);

void alignedLongCallC(Context* c, unsigned size UNUSED, lir::Constant* target);

void longJumpC(Context* c, unsigned size UNUSED, lir::Constant* target);

void alignedLongJumpC(Context* c, unsigned size UNUSED, lir::Constant* target);

void jumpC(Context* c, unsigned size UNUSED, lir::Constant* target);

void return_(Context* c);

void trap(Context* c);

void memoryBarrier(Context* c);

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_OPERATIONS_H
