/* Copyright (c) 2008-2015, Avian Contributors

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

#include "avian/environment.h"

namespace avian {
namespace codegen {
namespace arm {

const uint64_t MASK_LO32 = 0xffffffff;
const unsigned MASK_LO8 = 0xff;

#if TARGET_BYTES_PER_WORD == 8
constexpr Register ThreadRegister(19);
constexpr Register StackRegister(31);
constexpr Register LinkRegister(30);
constexpr Register FrameRegister(29);
constexpr Register ProgramCounter(0xFE);  // i.e. unaddressable

const int N_GPRS = 32;
const int N_FPRS = 32;
const RegisterMask GPR_MASK = 0xffffffff;
const RegisterMask FPR_MASK = 0xffffffff00000000;

#else
constexpr Register ThreadRegister(8);
constexpr Register StackRegister(13);
constexpr Register LinkRegister(14);
constexpr Register FrameRegister(0xFE);  // i.e. there is none
constexpr Register ProgramCounter(15);

const int N_GPRS = 16;
const int N_FPRS = 16;
const RegisterMask GPR_MASK = 0xffff;
const RegisterMask FPR_MASK = 0xffff0000;

inline int fpr64(Register reg)
{
  return reg.index() - N_GPRS;
}
inline int fpr64(lir::RegisterPair* reg)
{
  return fpr64(reg->low);
}
inline int fpr32(Register reg)
{
  return fpr64(reg) << 1;
}
inline int fpr32(lir::RegisterPair* reg)
{
  return fpr64(reg) << 1;
}
#endif

inline bool isFpr(lir::RegisterPair* reg)
{
  return reg->low.index() >= N_GPRS;
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_ARM_REGISTERS_H
