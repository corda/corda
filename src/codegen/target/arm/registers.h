/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_ARM_REGISTERS_H
#define AVIAN_CODEGEN_ASSEMBLER_ARM_REGISTERS_H

#include <avian/codegen/lir.h>
#include <avian/codegen/assembler.h>

namespace avian {
namespace codegen {
namespace arm {


const uint64_t MASK_LO32 = 0xffffffff;
const unsigned MASK_LO16 = 0xffff;
const unsigned MASK_LO8  = 0xff;

const int N_GPRS = 16;
const int N_FPRS = 16;
const uint32_t GPR_MASK = 0xffff;
const uint32_t FPR_MASK = 0xffff0000;

const uint64_t GPR_MASK64 = GPR_MASK | (uint64_t)GPR_MASK << 32;
const uint64_t FPR_MASK64 = FPR_MASK | (uint64_t)FPR_MASK << 32;

inline bool isFpr(lir::Register* reg) {
  return reg->low >= N_GPRS;
}

inline int fpr64(int reg) { return reg - N_GPRS; }
inline int fpr64(lir::Register* reg) { return fpr64(reg->low); }
inline int fpr32(int reg) { return fpr64(reg) << 1; }
inline int fpr32(lir::Register* reg) { return fpr64(reg) << 1; }

const int ThreadRegister = 8;
const int StackRegister = 13;
const int LinkRegister = 14;
const int ProgramCounter = 15;

} // namespace arm
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_ARM_REGISTERS_H
