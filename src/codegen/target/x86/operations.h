/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_OPERATIONS_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_OPERATIONS_H

#include "avian/common.h"

#include <avian/codegen/lir.h>

#include "context.h"

namespace avian {
namespace codegen {
namespace x86 {

void return_(Context* c);

void trap(Context* c);

void ignore(Context*);

void storeLoadBarrier(Context* c);

void callC(Context* c, unsigned size UNUSED, lir::Constant* a);

void longCallC(Context* c, unsigned size, lir::Constant* a);

void jumpR(Context* c, unsigned size UNUSED, lir::RegisterPair* a);

void jumpC(Context* c, unsigned size UNUSED, lir::Constant* a);

void jumpM(Context* c, unsigned size UNUSED, lir::Memory* a);

void longJumpC(Context* c, unsigned size, lir::Constant* a);

void callR(Context* c, unsigned size UNUSED, lir::RegisterPair* a);

void callM(Context* c, unsigned size UNUSED, lir::Memory* a);

void alignedCallC(Context* c, unsigned size, lir::Constant* a);

void alignedLongCallC(Context* c, unsigned size, lir::Constant* a);

void alignedJumpC(Context* c, unsigned size, lir::Constant* a);

void alignedLongJumpC(Context* c, unsigned size, lir::Constant* a);

void pushR(Context* c, unsigned size, lir::RegisterPair* a);

void popR(Context* c, unsigned size, lir::RegisterPair* a);

void negateR(Context* c, unsigned size, lir::RegisterPair* a);

void negateRR(Context* c,
              unsigned aSize,
              lir::RegisterPair* a,
              unsigned bSize UNUSED,
              lir::RegisterPair* b UNUSED);

void moveCR(Context* c,
            unsigned aSize,
            lir::Constant* a,
            unsigned bSize,
            lir::RegisterPair* b);

void moveZCR(Context* c,
             unsigned aSize,
             lir::Constant* a,
             unsigned bSize,
             lir::RegisterPair* b);

void swapRR(Context* c,
            unsigned aSize UNUSED,
            lir::RegisterPair* a,
            unsigned bSize UNUSED,
            lir::RegisterPair* b);

void moveRR(Context* c,
            unsigned aSize,
            lir::RegisterPair* a,
            UNUSED unsigned bSize,
            lir::RegisterPair* b);

void moveMR(Context* c,
            unsigned aSize,
            lir::Memory* a,
            unsigned bSize,
            lir::RegisterPair* b);

void moveRM(Context* c,
            unsigned aSize,
            lir::RegisterPair* a,
            unsigned bSize UNUSED,
            lir::Memory* b);

void moveAR(Context* c,
            unsigned aSize,
            lir::Address* a,
            unsigned bSize,
            lir::RegisterPair* b);

void moveCM(Context* c,
            unsigned aSize UNUSED,
            lir::Constant* a,
            unsigned bSize,
            lir::Memory* b);

void moveZRR(Context* c,
             unsigned aSize,
             lir::RegisterPair* a,
             unsigned bSize UNUSED,
             lir::RegisterPair* b);

void moveZMR(Context* c,
             unsigned aSize UNUSED,
             lir::Memory* a,
             unsigned bSize UNUSED,
             lir::RegisterPair* b);

void addCarryRR(Context* c, unsigned size, lir::RegisterPair* a, lir::RegisterPair* b);

void addRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b);

void addCarryCR(Context* c, unsigned size, lir::Constant* a, lir::RegisterPair* b);

void addCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b);

void subtractBorrowCR(Context* c,
                      unsigned size UNUSED,
                      lir::Constant* a,
                      lir::RegisterPair* b);

void subtractCR(Context* c,
                unsigned aSize,
                lir::Constant* a,
                unsigned bSize,
                lir::RegisterPair* b);

void subtractBorrowRR(Context* c,
                      unsigned size,
                      lir::RegisterPair* a,
                      lir::RegisterPair* b);

void subtractRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b);

void andRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b);

void andCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b);

void orRR(Context* c,
          unsigned aSize,
          lir::RegisterPair* a,
          unsigned bSize UNUSED,
          lir::RegisterPair* b);

void orCR(Context* c,
          unsigned aSize,
          lir::Constant* a,
          unsigned bSize,
          lir::RegisterPair* b);

void xorRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b);

void xorCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b);

void multiplyRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b);

void compareRR(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b);

void compareCR(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::RegisterPair* b);

void compareRM(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::Memory* b);

void compareCM(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::Memory* b);

void compareFloatRR(Context* c,
                    unsigned aSize,
                    lir::RegisterPair* a,
                    unsigned bSize UNUSED,
                    lir::RegisterPair* b);

void branchLong(Context* c,
                lir::TernaryOperation op,
                lir::Operand* al,
                lir::Operand* ah,
                lir::Operand* bl,
                lir::Operand* bh,
                lir::Constant* target,
                BinaryOperationType compare);

void branchRR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::RegisterPair* b,
              lir::Constant* target);

void branchCR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::RegisterPair* b,
              lir::Constant* target);

void branchRM(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::Memory* b,
              lir::Constant* target);

void branchCM(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::Memory* b,
              lir::Constant* target);

void multiplyCR(Context* c,
                unsigned aSize,
                lir::Constant* a,
                unsigned bSize,
                lir::RegisterPair* b);

void divideRR(Context* c,
              unsigned aSize,
              lir::RegisterPair* a,
              unsigned bSize UNUSED,
              lir::RegisterPair* b UNUSED);

void remainderRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b);

void doShift(Context* c,
             UNUSED void (*shift)(Context*,
                                  unsigned,
                                  lir::RegisterPair*,
                                  unsigned,
                                  lir::RegisterPair*),
             int type,
             UNUSED unsigned aSize,
             lir::Constant* a,
             unsigned bSize,
             lir::RegisterPair* b);

void shiftLeftRR(Context* c,
                 UNUSED unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void shiftLeftCR(Context* c,
                 unsigned aSize,
                 lir::Constant* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void shiftRightRR(Context* c,
                  UNUSED unsigned aSize,
                  lir::RegisterPair* a,
                  unsigned bSize,
                  lir::RegisterPair* b);

void shiftRightCR(Context* c,
                  unsigned aSize,
                  lir::Constant* a,
                  unsigned bSize,
                  lir::RegisterPair* b);

void unsignedShiftRightRR(Context* c,
                          UNUSED unsigned aSize,
                          lir::RegisterPair* a,
                          unsigned bSize,
                          lir::RegisterPair* b);

void unsignedShiftRightCR(Context* c,
                          unsigned aSize UNUSED,
                          lir::Constant* a,
                          unsigned bSize,
                          lir::RegisterPair* b);

void floatSqrtRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b);

void floatSqrtMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b);

void floatAddRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b);

void floatAddMR(Context* c,
                unsigned aSize,
                lir::Memory* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b);

void floatSubtractRR(Context* c,
                     unsigned aSize,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b);

void floatSubtractMR(Context* c,
                     unsigned aSize,
                     lir::Memory* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b);

void floatMultiplyRR(Context* c,
                     unsigned aSize,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b);

void floatMultiplyMR(Context* c,
                     unsigned aSize,
                     lir::Memory* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b);

void floatDivideRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b);

void floatDivideMR(Context* c,
                   unsigned aSize,
                   lir::Memory* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b);

void float2FloatRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b);

void float2FloatMR(Context* c,
                   unsigned aSize,
                   lir::Memory* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b);

void float2IntRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void float2IntMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void int2FloatRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void int2FloatMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize,
                 lir::RegisterPair* b);

void floatNegateRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b);

void floatAbsoluteRR(Context* c,
                     unsigned aSize UNUSED,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b);

void absoluteRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b UNUSED);

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_OPERATIONS_H
