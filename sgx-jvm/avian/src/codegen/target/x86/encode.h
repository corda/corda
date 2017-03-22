/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_ENCODE_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_ENCODE_H

#include <stdint.h>

#include "avian/common.h"

#include <avian/codegen/lir.h>

#include "registers.h"

namespace avian {
namespace codegen {
namespace x86 {

class Context;

void maybeRex(Context* c,
              unsigned size,
              int a,
              int index,
              int base,
              bool always);

void maybeRex(Context* c, unsigned size, lir::RegisterPair* a, lir::RegisterPair* b);

void alwaysRex(Context* c, unsigned size, lir::RegisterPair* a, lir::RegisterPair* b);

void maybeRex(Context* c, unsigned size, lir::RegisterPair* a);

void maybeRex(Context* c, unsigned size, lir::RegisterPair* a, lir::Memory* b);

void maybeRex(Context* c, unsigned size, lir::Memory* a);

inline int regCode(Register a)
{
  return a.index() & 7;
}

inline int regCode(lir::RegisterPair* a)
{
  return regCode(a->low);
}

inline bool isFloatReg(lir::RegisterPair* a)
{
  return a->low >= xmm0;
}

void modrm(Context* c, uint8_t mod, Register a, Register b);

void modrm(Context* c, uint8_t mod, lir::RegisterPair* a, lir::RegisterPair* b);

void sib(Context* c, unsigned scale, Register index, Register base);

void modrmSib(Context* c, int width, Register a, int scale, Register index, Register base);

void modrmSibImm(Context* c, Register a, int scale, Register index, Register base, int offset);

void modrmSibImm(Context* c, lir::RegisterPair* a, lir::Memory* b);

void opcode(Context* c, uint8_t op);

void opcode(Context* c, uint8_t op1, uint8_t op2);

void unconditional(Context* c, unsigned jump, lir::Constant* a);

void conditional(Context* c, unsigned condition, lir::Constant* a);

void sseMoveRR(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b);

void sseMoveCR(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::RegisterPair* b);

void sseMoveMR(Context* c,
               unsigned aSize,
               lir::Memory* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b);

void sseMoveRM(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               UNUSED unsigned bSize,
               lir::Memory* b);

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target);

void branchFloat(Context* c, lir::TernaryOperation op, lir::Constant* target);

void floatRegOp(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize,
                lir::RegisterPair* b,
                uint8_t op,
                uint8_t mod = 0xc0);

void floatMemOp(Context* c,
                unsigned aSize,
                lir::Memory* a,
                unsigned bSize,
                lir::RegisterPair* b,
                uint8_t op);

void moveCR2(Context* c,
             UNUSED unsigned aSize,
             lir::Constant* a,
             UNUSED unsigned bSize,
             lir::RegisterPair* b,
             unsigned promiseOffset);

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_ENCODE_H
