/* Copyright (c) 2008-2015, Avian Contributors

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

constexpr Register rax((int)0);
constexpr Register rcx(1);
constexpr Register rdx(2);
constexpr Register rbx(3);
constexpr Register rsp(4);
constexpr Register rbp(5);
constexpr Register rsi(6);
constexpr Register rdi(7);
constexpr Register r8(8);
constexpr Register r9(9);
constexpr Register r10(10);
constexpr Register r11(11);
constexpr Register r12(12);
constexpr Register r13(13);
constexpr Register r14(14);
constexpr Register r15(15);
constexpr Register xmm0(16);
constexpr Register xmm1(16 + 1);
constexpr Register xmm2(16 + 2);
constexpr Register xmm3(16 + 3);
constexpr Register xmm4(16 + 4);
constexpr Register xmm5(16 + 5);
constexpr Register xmm6(16 + 6);
constexpr Register xmm7(16 + 7);
constexpr Register xmm8(16 + 8);
constexpr Register xmm9(16 + 9);
constexpr Register xmm10(16 + 10);
constexpr Register xmm11(16 + 11);
constexpr Register xmm12(16 + 12);
constexpr Register xmm13(16 + 13);
constexpr Register xmm14(16 + 14);
constexpr Register xmm15(16 + 15);

constexpr Register LongJumpRegister = r10;

constexpr RegisterMask GeneralRegisterMask = vm::TargetBytesPerWord == 4 ? 0x000000ff
                                                                 : 0x0000ffff;

constexpr RegisterMask FloatRegisterMask = vm::TargetBytesPerWord == 4 ? 0x00ff0000
                                                               : 0xffff0000;

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_REGISTERS_H
