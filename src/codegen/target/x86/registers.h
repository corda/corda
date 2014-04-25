/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_REGISTERS_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_REGISTERS_H

namespace avian {
namespace codegen {
namespace x86 {

enum {
  rax = 0,
  rcx = 1,
  rdx = 2,
  rbx = 3,
  rsp = 4,
  rbp = 5,
  rsi = 6,
  rdi = 7,
  r8 = 8,
  r9 = 9,
  r10 = 10,
  r11 = 11,
  r12 = 12,
  r13 = 13,
  r14 = 14,
  r15 = 15,
};

enum {
  xmm0 = r15 + 1,
  xmm1,
  xmm2,
  xmm3,
  xmm4,
  xmm5,
  xmm6,
  xmm7,
  xmm8,
  xmm9,
  xmm10,
  xmm11,
  xmm12,
  xmm13,
  xmm14,
  xmm15,
};

const int LongJumpRegister = r10;

const unsigned GeneralRegisterMask = vm::TargetBytesPerWord == 4 ? 0x000000ff : 0x0000ffff;

const unsigned FloatRegisterMask = vm::TargetBytesPerWord == 4 ? 0x00ff0000 : 0xffff0000;


} // namespace x86
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_X86_REGISTERS_H
