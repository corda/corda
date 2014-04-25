/* Copyright (c) 2008-2014, Avian Contributors

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

void maybeRex(Context* c, unsigned size, int a, int index, int base, bool always);

void maybeRex(Context* c, unsigned size, lir::Register* a, lir::Register* b);

void alwaysRex(Context* c, unsigned size, lir::Register* a, lir::Register* b);

void maybeRex(Context* c, unsigned size, lir::Register* a);

void maybeRex(Context* c, unsigned size, lir::Register* a, lir::Memory* b);

void maybeRex(Context* c, unsigned size, lir::Memory* a);

inline int regCode(int a) {
  return a & 7;
}

inline int regCode(lir::Register* a) {
  return regCode(a->low);
}

inline bool isFloatReg(lir::Register* a) {
  return a->low >= xmm0;
}

void modrm(Context* c, uint8_t mod, int a, int b);

void modrm(Context* c, uint8_t mod, lir::Register* a, lir::Register* b);

void sib(Context* c, unsigned scale, int index, int base);

void modrmSib(Context* c, int width, int a, int scale, int index, int base);

void modrmSibImm(Context* c, int a, int scale, int index, int base, int offset);
  
void modrmSibImm(Context* c, lir::Register* a, lir::Memory* b);

void opcode(Context* c, uint8_t op);

void opcode(Context* c, uint8_t op1, uint8_t op2);

void unconditional(Context* c, unsigned jump, lir::Constant* a);

void conditional(Context* c, unsigned condition, lir::Constant* a);

void sseMoveRR(Context* c, unsigned aSize, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b);

void sseMoveCR(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b);

void sseMoveMR(Context* c, unsigned aSize, lir::Memory* a,
          unsigned bSize UNUSED, lir::Register* b);

void sseMoveRM(Context* c, unsigned aSize, lir::Register* a,
       UNUSED unsigned bSize, lir::Memory* b);

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target);

void branchFloat(Context* c, lir::TernaryOperation op, lir::Constant* target);

void floatRegOp(Context* c, unsigned aSize, lir::Register* a, unsigned bSize,
           lir::Register* b, uint8_t op, uint8_t mod = 0xc0);

void floatMemOp(Context* c, unsigned aSize, lir::Memory* a, unsigned bSize,
           lir::Register* b, uint8_t op);

void moveCR2(Context* c, UNUSED unsigned aSize, lir::Constant* a,
        UNUSED unsigned bSize, lir::Register* b, unsigned promiseOffset);

} // namespace x86
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_X86_ENCODE_H
