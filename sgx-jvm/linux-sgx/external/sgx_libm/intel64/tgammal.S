/*
* Math library
*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above copyright
*     notice, this list of conditions and the following disclaimer in
*     the documentation and/or other materials provided with the
*     distribution.
*   * Neither the name of Intel Corporation nor the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*
* Author Name <jingwei.zhang@intel.com>
*   History:
*   03-14-2016 Initial version. numerics svn rev. 12864
*/
	.file "tgammal.c"
	.text
..TXTST0:
# -- Begin  tgammal
	.text
       .align    16,0x90
	.globl tgammal
tgammal:
# parameter 1: 368 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_tgammal.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $320, %rsp
	.cfi_def_cfa_offset 368
        xorb      %bpl, %bpl
        fldt      368(%rsp)
        xorb      %r14b, %r14b
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 304(%rsp)
        fstpt     240(%rsp)
..B1.2:
        fnstcw    258(%rsp)
..B1.3:
        movzwl    258(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 256(%rsp)
..B1.5:
        fldcw     256(%rsp)
..B1.6:
        movb      $1, %r14b
..B1.7:
..___tag_value_tgammal.14:
        call      fegetround@PLT
..___tag_value_tgammal.15:
..B1.195:
        movl      %eax, %r13d
..B1.8:
        testl     %r13d, %r13d
        je        ..B1.10
..B1.9:
        xorl      %edi, %edi
        movb      $1, %bpl
..___tag_value_tgammal.16:
        call      fesetround@PLT
..___tag_value_tgammal.17:
..B1.10:
        movl      376(%rsp), %ebx
        movzwl    376(%rsp), %ecx
        shrl      $15, %ebx
        andl      $32767, %ecx
        andl      $1, %ebx
        cmpl      $32767, %ecx
        je        ..B1.178
..B1.11:
        fldt      368(%rsp)
        lea       _zeros(%rip), %rax
        andl      $32767, 248(%rsp)
        fldl      (%rax)
        movsd     (%rax), %xmm0
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fucomip   %st(1), %st
        jp        ..B1.12
        je        ..B1.171
..B1.12:
        testl     %ebx, %ebx
        je        ..B1.24
..B1.13:
        cmpl      $16446, %ecx
        jge       ..B1.142
..B1.14:
        fldt      240(%rsp)
        lea       _TWO_63H(%rip), %rax
        fld       %st(0)
        fldl      (%rax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(1)
        movl      112(%rsp), %r15d
        fcomi     %st(1), %st
        jbe       ..B1.16
..B1.15:
        lea       _ones(%rip), %rax
        incl      %r15d
        fsubl     (%rax)
..B1.16:
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.17
        je        ..B1.135
..B1.17:
        fldt      .L_2il0floatpacket.0(%rip)
        fcomip    %st(1), %st
        jbe       ..B1.24
..B1.18:
        fstp      %st(0)
        testb     %bpl, %bpl
        je        ..B1.20
..B1.19:
        movl      %r13d, %edi
..___tag_value_tgammal.18:
        call      fesetround@PLT
..___tag_value_tgammal.19:
..B1.20:
        notl      %r15d
        lea       _small_value_80(%rip), %rax
        andl      $1, %r15d
        shlq      $4, %r15
        fldt      (%rax)
        testb     %r14b, %r14b
        fldt      (%rax,%r15)
        fmulp     %st, %st(1)
        fstpt     64(%rsp)
        je        ..B1.22
..B1.21:
        fldcw     258(%rsp)
..B1.22:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.23:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.24:
        lea       _POS_OVERFLOW(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        jp        ..B1.25
        jb        ..B1.164
..B1.25:
        testl     %ecx, %ecx
        jne       ..B1.28
..B1.26:
        cmpl      $536870912, 372(%rsp)
        jb        ..B1.149
..B1.27:
        movq      $0x2000000000000000, %rax
        cmpq      368(%rsp), %rax
        je        ..B1.149
..B1.28:
        fldt      240(%rsp)
        fldz      
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fld       %st(0)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        testl     %ebx, %ebx
        fxch      %st(2)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fxch      %st(2)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fldt      224(%rsp)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        je        ..B1.32
..B1.29:
        fstp      %st(1)
        lea       _TWO_63H(%rip), %rax
        cmpl      $16299, %ecx
        fldl      (%rax)
        jle       ..B1.31
..B1.30:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _TWO_53H(%rip), %rax
        fxch      %st(3)
        fstps     96(%rsp)
        fld       %st(3)
        lea       96+_tgammal_S(%rip), %rdx
        fadd      %st(1), %st
        lea       80+_tgammal_S(%rip), %rsi
        fsub      %st(1), %st
        lea       64+_tgammal_S(%rip), %r8
        fsubr     %st(4), %st
        fstpt     80(%rsp)
        lea       48+_tgammal_S(%rip), %r9
        andl      $32767, 88(%rsp)
        lea       32+_tgammal_S(%rip), %r10
        fstpt     48(%rsp)
        lea       16+_tgammal_S(%rip), %r11
        fstpt     (%rsp)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      80(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        fchs      
        fsubrp    %st, %st(3)
        fld       %st(2)
        fld       %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(4)
        fmul      %st(5), %st
        fxch      %st(3)
        fstpt     16(%rsp)
        fld       %st(2)
        fldl      (%rax)
        lea       _tgammal_S(%rip), %rax
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(1), %st
        fld       %st(5)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        fstpt     160(%rsp)
        fldt      (%rdx)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       _tgammal_S2(%rip), %rax
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      (%rax)
        lea       8+_tgammal_S2(%rip), %rax
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     (%rax)
        fld       %st(0)
        lea       8+_tgammal_S1(%rip), %rax
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fldl      (%rax)
        fld       %st(0)
        lea       _tgammal_S1(%rip), %rax
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        lea       _ones(%rip), %rax
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(5)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fmul      %st(7), %st
        fmul      %st, %st(6)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fmul      %st, %st(4)
        fldt      16(%rsp)
        fld       %st(0)
        fmulp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fld       %st(3)
        fxch      %st(2)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fldt      176(%rsp)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpt     128(%rsp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fldt      160(%rsp)
        fmul      %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      (%rsp)
        fldt      48(%rsp)
        flds      96(%rsp)
        fldt      64(%rsp)
        fxch      %st(4)
        fxch      %st(2)
        fxch      %st(3)
        fxch      %st(1)
        fxch      %st(3)
        jmp       ..B1.33
..B1.31:
        fldt      208(%rsp)
        fstpt     192(%rsp)
        fldt      224(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fxch      %st(1)
        jmp       ..B1.33
..B1.32:
        lea       _TWO_63H(%rip), %rax
        fldl      (%rax)
..B1.33:
        fldt      .L_2il0floatpacket.2(%rip)
        fcomip    %st(5), %st
        jp        ..B1.34
        jbe       ..B1.95
..B1.34:
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(1)
        movl      112(%rsp), %edx
        fcomi     %st(4), %st
        jbe       ..B1.36
..B1.35:
        lea       _ones(%rip), %rax
        decl      %edx
        fsubl     (%rax)
..B1.36:
        cmpl      $2, %edx
        lea       1(%rdx), %r15d
        jl        ..B1.41
..B1.37:
        fstp      %st(3)
        testb     $1, %dl
        je        ..B1.39
..B1.38:
        lea       _ones(%rip), %rax
        lea       -1(%rdx), %r9d
        fldl      (%rax)
        fsubr     %st, %st(4)
        fxch      %st(4)
        fstpt     208(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        jmp       ..B1.40
..B1.39:
        fstp      %st(3)
        movl      %edx, %r9d
..B1.40:
        fldt      .L_2il0floatpacket.3(%rip)
        addl      $-2, %r9d
        sarl      $1, %r9d
        lea       _tgammal_T2(%rip), %rsi
        movl      %r9d, %eax
        faddp     %st, %st(2)
        fldt      208(%rsp)
        lea       (,%r9,8), %r8d
        shll      $4, %eax
        lea       _tgammal_A2(%rip), %r10
        subl      %r9d, %eax
        lea       (%r8,%r9,4), %r9d
        movslq    %eax, %rax
        fsubp     %st, %st(2)
        shlq      $4, %rax
        movslq    %r9d, %r9
        addq      %rsi, %rax
        lea       (%r10,%r9,8), %rsi
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.55
..B1.41:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.4(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.43
..B1.42:
        movl      $3, %r9d
        jmp       ..B1.54
..B1.43:
        fldt      .L_2il0floatpacket.6(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.45
..B1.44:
        xorl      %r9d, %r9d
        jmp       ..B1.54
..B1.45:
        fldt      .L_2il0floatpacket.7(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.47
..B1.46:
        fldt      .L_2il0floatpacket.3(%rip)
        movl      $1, %r9d
        fsubrp    %st, %st(4)
        jmp       ..B1.54
..B1.47:
        fldt      .L_2il0floatpacket.8(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.49
..B1.48:
        lea       _ones(%rip), %rax
        movl      $2, %r9d
        fldl      (%rax)
        fsubp     %st, %st(4)
        jmp       ..B1.54
..B1.49:
        fldt      .L_2il0floatpacket.9(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.51
..B1.50:
        lea       _ones(%rip), %rax
        xorl      %r9d, %r9d
        fxch      %st(3)
        fsubl     (%rax)
        fxch      %st(3)
        jmp       ..B1.54
..B1.51:
        fldt      .L_2il0floatpacket.10(%rip)
        fcomip    %st(4), %st
        jbe       ..B1.53
..B1.52:
        fldt      .L_2il0floatpacket.11(%rip)
        movl      $1, %r9d
        fsubrp    %st, %st(4)
        jmp       ..B1.54
..B1.53:
        fldt      .L_2il0floatpacket.5(%rip)
        movl      $2, %r9d
        fsubp     %st, %st(4)
..B1.54:
        movl      %r9d, %eax
        lea       _tgammal_T1(%rip), %rsi
        shll      $4, %eax
        lea       (,%r9,8), %r8d
        subl      %r9d, %eax
        lea       (%r8,%r9,4), %r9d
        lea       _tgammal_A1(%rip), %r10
        shlq      $4, %rax
        fxch      %st(2)
        fstpt     208(%rsp)
        addq      %rsi, %rax
        lea       (%r10,%r9,8), %rsi
..B1.55:
        cmpl      $16299, %ecx
        jle       ..B1.61
..B1.56:
        fstp      %st(1)
        fldt      224(%rax)
        testb     $1, %dl
        fmul      %st(2), %st
        fldt      208(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      192(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      176(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      160(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      144(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      128(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      112(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      96(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      80(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      64(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      32(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      16(%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(4)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(4), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fsubr     %st, %st(4)
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(5), %st
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fldl      88(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      80(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      72(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      64(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fldl      56(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      48(%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(1)
        fmul      %st(5), %st
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fldl      40(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(4), %st
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fldl      32(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldl      24(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      16(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fld       %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      8(%rsi)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(1)
        fadd      %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      (%rsi)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        je        ..B1.59
..B1.57:
        cmpl      $1, %edx
        jle       ..B1.59
..B1.58:
        fstp      %st(0)
        fstp      %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      96(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fldt      208(%rsp)
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(4)
        fxch      %st(3)
        fmul      %st, %st(5)
        fsubr     %st, %st(3)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        jmp       ..B1.63
..B1.59:
        fstpt     112(%rsp)
        fxch      %st(1)
        jmp       ..B1.62
..B1.61:
        lea       _ones(%rip), %rax
        fldl      (%rax)
        fstpt     96(%rsp)
..B1.62:
        testl     %edx, %edx
        jle       ..B1.76
..B1.63:
        fstp      %st(0)
        testl     %ebx, %ebx
        jne       ..B1.70
..B1.64:
        testb     %bpl, %bpl
        je        ..B1.66
..B1.65:
        movl      %r13d, %edi
        fstpt     (%rsp)
        fstpt     64(%rsp)
..___tag_value_tgammal.37:
        call      fesetround@PLT
..___tag_value_tgammal.38:
..B1.206:
        fldt      64(%rsp)
        fldt      (%rsp)
..B1.66:
        fldt      96(%rsp)
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        je        ..B1.212
..B1.67:
        fstpt     80(%rsp)
..B1.196:
        fldcw     258(%rsp)
        jmp       ..B1.68
..B1.212:
        fstp      %st(0)
..B1.68:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.69:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.70:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _ones(%rip), %rax
        fldt      .L_2il0floatpacket.1(%rip)
        testb     %bpl, %bpl
        fldt      128(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        fldt      144(%rsp)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(2)
        fxch      %st(1)
        fdivrl    (%rax)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(6), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st(4), %st
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(1), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(1)
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     288(%rsp)
        fldt      288(%rsp)
        fld       %st(3)
        fmul      %st(2), %st
        fstpt     16(%rsp)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        je        ..B1.72
..B1.71:
        movl      %r13d, %edi
        fstpt     (%rsp)
        fstpt     64(%rsp)
..___tag_value_tgammal.56:
        call      fesetround@PLT
..___tag_value_tgammal.57:
..B1.205:
        fldt      64(%rsp)
        fldt      (%rsp)
..B1.72:
        fldt      16(%rsp)
        andl      $1, %r15d
        shll      $15, %r15d
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        xorl      %r15d, 72(%rsp)
        testb     %r14b, %r14b
        je        ..B1.213
..B1.73:
        fldt      48(%rsp)
        fstpt     112(%rsp)
        fstpt     80(%rsp)
..B1.197:
        fldcw     258(%rsp)
        jmp       ..B1.74
..B1.213:
        fstp      %st(0)
..B1.74:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.75:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.76:
        lea       _ones(%rip), %rax
        testl     %ebx, %ebx
        fldl      (%rax)
        fstpt     (%rsp)
        jne       ..B1.86
..B1.77:
        fstp      %st(0)
        fldt      176(%rsp)
        cmpl      $100, %ecx
        fldt      160(%rsp)
        faddp     %st, %st(1)
        jge       ..B1.79
..B1.78:
        fstp      %st(1)
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        jmp       ..B1.80
..B1.79:
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(2), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      176(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fsubr     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(5)
        fldt      160(%rsp)
        faddp     %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(3), %st
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(5)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(6)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
        fstpt     288(%rsp)
        fld       %st(0)
        fxch      %st(2)
        fstpt     272(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      96(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(4)
        fxch      %st(3)
        fmul      %st, %st(4)
        fsubr     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fld       %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpt     96(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     224(%rsp)
        fldt      64(%rsp)
..B1.80:
        testb     %bpl, %bpl
        je        ..B1.82
..B1.81:
        movl      %r13d, %edi
        fstpt     64(%rsp)
..___tag_value_tgammal.75:
        call      fesetround@PLT
..___tag_value_tgammal.76:
..B1.208:
        fldt      64(%rsp)
..B1.82:
        fldt      224(%rsp)
        testb     %r14b, %r14b
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        je        ..B1.214
..B1.83:
        fstpt     80(%rsp)
..B1.198:
        fldcw     258(%rsp)
        jmp       ..B1.84
..B1.214:
        fstp      %st(0)
..B1.84:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.85:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.86:
        cmpl      $100, %ecx
        jge       ..B1.88
..B1.87:
        fstp      %st(0)
        fldt      192(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.89
..B1.88:
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      96(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fldt      192(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(4)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fmul      %st(4), %st
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(1), %st
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(1), %st
        fsubp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(4)
        fmul      %st(3), %st
        fstpt     (%rsp)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     224(%rsp)
        fstpt     272(%rsp)
        fstpt     288(%rsp)
..B1.89:
        testb     %bpl, %bpl
        je        ..B1.91
..B1.90:
        movl      %r13d, %edi
        fstpt     64(%rsp)
..___tag_value_tgammal.94:
        call      fesetround@PLT
..___tag_value_tgammal.95:
..B1.207:
        fldt      64(%rsp)
..B1.91:
        fldt      224(%rsp)
        andl      $1, %r15d
        fldt      (%rsp)
        shll      $15, %r15d
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        xorl      %r15d, 72(%rsp)
        testb     %r14b, %r14b
        je        ..B1.215
..B1.92:
        fstpt     80(%rsp)
..B1.199:
        fldcw     258(%rsp)
        jmp       ..B1.93
..B1.215:
        fstp      %st(0)
..B1.93:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.94:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.95:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(1)
        lea       _ones(%rip), %rax
        fld       %st(1)
        fldl      (%rax)
        fdiv      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fstpt     (%rsp)
        fstpl     80(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 400
        lea       224(%rsp), %rdi
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      32(%rdi)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     48(%rsp)
        fstpt     80(%rsp)
..___tag_value_tgammal.114:
        call      __libm_logl_k80@PLT
..___tag_value_tgammal.115:
..B1.200:
        fstp      %st(0)
        fldt      80(%rsp)
        fldt      48(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 368
..B1.96:
        fldt      .L_2il0floatpacket.3(%rip)
        lea       56+_tgammal_B(%rip), %rax
        fxch      %st(2)
        fstpt     48(%rsp)
        lea       48+_tgammal_B(%rip), %rdx
        fldt      (%rsp)
        lea       40+_tgammal_B(%rip), %rcx
        fldt      192(%rsp)
        lea       32+_tgammal_B(%rip), %rsi
        fldl      (%rax)
        lea       24+_tgammal_B(%rip), %r8
        fmul      %st(2), %st
        lea       16+_tgammal_B(%rip), %r9
        fldt      .L_2il0floatpacket.1(%rip)
        lea       8+_tgammal_B(%rip), %r10
        lea       _tgammal_B(%rip), %r11
        lea       _tgammal_B0(%rip), %rdi
        lea       8+_tgammal_C(%rip), %rax
        testl     %ebx, %ebx
        fxch      %st(1)
        faddl     (%rdx)
        lea       _tgammal_C(%rip), %rdx
        fmul      %st(3), %st
        fldt      (%rdi)
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(4), %st
        faddl     (%rsi)
        fmul      %st(4), %st
        faddl     (%r8)
        fmul      %st(4), %st
        faddl     (%r9)
        fmul      %st(4), %st
        faddl     (%r10)
        fmul      %st(4), %st
        faddl     (%r11)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fld       %st(0)
        fldt      240(%rsp)
        fsub      %st, %st(5)
        fxch      %st(3)
        fmul      %st(5), %st
        fld       %st(5)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(5)
        fxch      %st(3)
        fchs      
        fldt      208(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(4), %st
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fld       %st(4)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st, %st(4)
        fld       %st(6)
        fmul      %st(1), %st
        faddp     %st, %st(5)
        fxch      %st(2)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(3)
        fstpt     208(%rsp)
        fld       %st(3)
        fadd      %st(2), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        fadd      %st(1), %st
        fxch      %st(1)
        fsubrp    %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(3), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        fadd      %st(1), %st
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      (%rdx)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        jne       ..B1.108
..B1.97:
        fxch      %st(1)
        fstpt     288(%rsp)
        lea       160(%rsp), %rdi
        fxch      %st(1)
        fstpt     112(%rdi)
        lea       272(%rsp), %rsi
        fstpt     -112(%rdi)
..___tag_value_tgammal.117:
        call      __libm_expl_k80@PLT
..___tag_value_tgammal.118:
..B1.201:
        fldt      48(%rsp)
..B1.98:
        testb     %bpl, %bpl
        fxch      %st(1)
        je        ..B1.101
..B1.99:
        movl      %r13d, %edi
        fstpt     (%rsp)
        fstpt     48(%rsp)
..___tag_value_tgammal.119:
        call      fesetround@PLT
..___tag_value_tgammal.120:
..B1.100:
        fldt      48(%rsp)
        fldt      (%rsp)
..B1.101:
        fldt      160(%rsp)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      176(%rsp)
        movl      (%rsp), %edx
        cmpl      $16383, %edx
        faddp     %st, %st(1)
        fldl      80(%rsp)
        fstpt     (%rsp)
        jg        ..B1.103
..B1.102:
        addl      $16383, %edx
        movzwl    8(%rsp), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        orl       %edx, %eax
        movw      %ax, 8(%rsp)
        fldt      (%rsp)
        fmulp     %st, %st(1)
        jmp       ..B1.104
..B1.103:
        lea       _TWO_POW_16383(%rip), %rax
        andl      $32767, %edx
        movzwl    8(%rsp), %ecx
        andl      $-32768, %ecx
        orl       %edx, %ecx
        fldt      (%rax)
        fmulp     %st, %st(1)
        movw      %cx, 8(%rsp)
        fldt      (%rsp)
        fmulp     %st, %st(1)
..B1.104:
        fstpt     64(%rsp)
        testb     %r14b, %r14b
        je        ..B1.106
..B1.105:
        fldt      16(%rsp)
        fstpt     112(%rsp)
..B1.202:
        fldcw     258(%rsp)
..B1.106:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.107:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.108:
        fxch      %st(2)
        fchs      
        lea       160(%rsp), %rdi
        fstpt     112(%rdi)
        lea       272(%rsp), %rsi
        fchs      
        incl      %r15d
        fstpt     16(%rsi)
        fstpt     -112(%rdi)
..___tag_value_tgammal.138:
        call      __libm_expl_k80@PLT
..___tag_value_tgammal.139:
..B1.203:
        fldt      48(%rsp)
..B1.109:
        fldt      128(%rsp)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      144(%rsp)
        movl      (%rsp), %eax
        fadd      %st, %st(2)
        fxch      %st(2)
        fdivrl    80(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        fxch      %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(1), %st
        fld       %st(4)
        fmul      %st(3), %st
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.5(%rip)
        fsub      %st(1), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fld       %st(4)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fstpt     288(%rsp)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      160(%rsp)
        fld       %st(0)
        fldt      176(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(3), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fsubr     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        movzwl    72(%rsp), %edx
        andl      $32767, %edx
        lea       -1(%rax,%rdx), %ecx
        cmpl      $32765, %ecx
        lea       (%rax,%rdx), %ebx
        ja        ..B1.115
..B1.110:
        fstp      %st(1)
        fstp      %st(1)
        fldl      80(%rsp)
        lea       16382(%rax), %edx
        fstpt     (%rsp)
        cmpl      $32765, %edx
        ja        ..B1.112
..B1.111:
        addl      $16383, %eax
        movzwl    8(%rsp), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 8(%rsp)
        fldt      (%rsp)
        fmulp     %st, %st(1)
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.112:
        cmpl      $16383, %eax
        jle       ..B1.114
..B1.113:
        lea       _TWO_POW_16383(%rip), %rdx
        andl      $32767, %eax
        movzwl    8(%rsp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        fldt      (%rdx)
        fmulp     %st, %st(1)
        movw      %cx, 8(%rsp)
        fldt      (%rsp)
        fmulp     %st, %st(1)
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.114:
        lea       _TWO_POW_M16382(%rip), %rdx
        addl      $32765, %eax
        movzwl    8(%rsp), %ecx
        andl      $32767, %eax
        andl      $-32768, %ecx
        orl       %eax, %ecx
        fldt      (%rdx)
        fmulp     %st, %st(1)
        movw      %cx, 8(%rsp)
        fldt      (%rsp)
        fmulp     %st, %st(1)
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.115:
        fstp      %st(0)
        cmpl      $32766, %ebx
        jle       ..B1.117
..B1.116:
        fstp      %st(0)
        fstp      %st(0)
        lea       _TWO_POW_16383(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.117:
        cmpl      $-65, %ebx
        jg        ..B1.119
..B1.118:
        fstp      %st(0)
        fstp      %st(0)
        lea       _TWO_POW_M16382(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.119:
        fldl      80(%rsp)
        cmpl      $-16382, %eax
        fstpt     (%rsp)
        jl        ..B1.124
..B1.120:
        addl      $16383, %eax
        fld       %st(0)
        movzwl    8(%rsp), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        orl       %eax, %edx
        lea       _TWO_53H(%rip), %rax
        movw      %dx, 8(%rsp)
        testb     %bpl, %bpl
        fldt      (%rsp)
        fldl      (%rax)
        fmul      %st(3), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.123
..B1.121:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value_tgammal.140:
        call      fesetround@PLT
..___tag_value_tgammal.141:
..B1.122:
        fldt      (%rsp)
        xorb      %bpl, %bpl
..B1.123:
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        jmp       ..B1.128
..B1.124:
        lea       _TWO_POW_M16000(%rip), %rcx
        negl      %eax
        addl      $-15999, %eax
        lea       _TWO_32H(%rip), %rsi
        movzwl    8(%rsp), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        lea       _small_value_80(%rip), %rbx
        fldt      (%rcx)
        orl       %eax, %edx
        fmul      %st, %st(1)
        lea       _TWO_32HP(%rip), %rax
        fld       %st(1)
        testb     %bpl, %bpl
        fxch      %st(1)
        fmulp     %st, %st(3)
        movw      %dx, 8(%rsp)
        fadd      %st(2), %st
        fldl      (%rax)
        fldt      (%rbx)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        fldt      (%rsp)
        fadd      %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fmull     (%rsi)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        je        ..B1.127
..B1.125:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value_tgammal.142:
        call      fesetround@PLT
..___tag_value_tgammal.143:
..B1.126:
        fldt      (%rsp)
        xorb      %bpl, %bpl
..B1.127:
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        movzwl    72(%rsp), %eax
        andl      $-32768, %eax
        andl      $2147483647, 68(%rsp)
        movw      %ax, 72(%rsp)
..B1.128:
        andl      $1, %r15d
        shll      $15, %r15d
        xorl      %r15d, 72(%rsp)
        testb     %bpl, %bpl
        je        ..B1.130
..B1.129:
        movl      %r13d, %edi
..___tag_value_tgammal.144:
        call      fesetround@PLT
..___tag_value_tgammal.145:
..B1.130:
        testb     %r14b, %r14b
        je        ..B1.132
..B1.131:
        fldt      48(%rsp)
        fstpt     112(%rsp)
..B1.204:
        fldcw     258(%rsp)
..B1.132:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.133:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.134:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.135:
        fstp      %st(0)
        testb     %bpl, %bpl
        je        ..B1.137
..B1.136:
        movl      %r13d, %edi
        movsd     %xmm0, (%rsp)
..___tag_value_tgammal.163:
        call      fesetround@PLT
..___tag_value_tgammal.164:
..B1.211:
        movsd     (%rsp), %xmm0
..B1.137:
        divsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
        testb     %r14b, %r14b
        fldl      (%rsp)
        fstpt     64(%rsp)
        je        ..B1.139
..B1.138:
        fldcw     258(%rsp)
..B1.139:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
        jmp       ..B1.23
..B1.142:
        fstp      %st(0)
        testb     %bpl, %bpl
        je        ..B1.144
..B1.143:
        movl      %r13d, %edi
        movsd     %xmm0, (%rsp)
..___tag_value_tgammal.165:
        call      fesetround@PLT
..___tag_value_tgammal.166:
..B1.210:
        movsd     (%rsp), %xmm0
..B1.144:
        divsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
        testb     %r14b, %r14b
        fldl      (%rsp)
        fstpt     64(%rsp)
        je        ..B1.146
..B1.145:
        fldcw     258(%rsp)
..B1.146:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
        jmp       ..B1.23
..B1.149:
        testb     %bpl, %bpl
        je        ..B1.151
..B1.150:
        movl      %r13d, %edi
..___tag_value_tgammal.167:
        call      fesetround@PLT
..___tag_value_tgammal.168:
..B1.151:
        movl      %ebx, %eax
        lea       _large_value_80(%rip), %rdx
        shlq      $4, %rax
        testl     %ebx, %ebx
        fldt      (%rdx)
        fldt      (%rdx,%rax)
        fmulp     %st, %st(1)
        fstpt     64(%rsp)
        je        ..B1.153
..B1.152:
        shll      $15, %ebx
        orl       %ebx, 72(%rsp)
..B1.153:
        testb     %r14b, %r14b
        je        ..B1.155
..B1.154:
        fldcw     258(%rsp)
..B1.155:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
        jmp       ..B1.23
..B1.164:
        testb     %bpl, %bpl
        je        ..B1.166
..B1.165:
        movl      %r13d, %edi
..___tag_value_tgammal.169:
        call      fesetround@PLT
..___tag_value_tgammal.170:
..B1.166:
        lea       _large_value_80(%rip), %rax
        testb     %r14b, %r14b
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     64(%rsp)
        je        ..B1.168
..B1.167:
        fldcw     258(%rsp)
..B1.168:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
        jmp       ..B1.23
..B1.171:
        fstp      %st(0)
        testb     %bpl, %bpl
        je        ..B1.173
..B1.172:
        movl      %r13d, %edi
        movsd     %xmm0, (%rsp)
..___tag_value_tgammal.171:
        call      fesetround@PLT
..___tag_value_tgammal.172:
..B1.209:
        movsd     (%rsp), %xmm0
..B1.173:
        lea       _ones(%rip), %rax
        testb     %r14b, %r14b
        movsd     (%rax,%rbx,8), %xmm1
        divsd     %xmm0, %xmm1
        movsd     %xmm1, (%rsp)
        fldl      (%rsp)
        fstpt     64(%rsp)
        je        ..B1.175
..B1.174:
        fldcw     258(%rsp)
..B1.175:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
        jmp       ..B1.23
..B1.178:
        testl     %ebx, %ebx
        je        ..B1.181
..B1.179:
        cmpl      $-2147483648, 372(%rsp)
        jne       ..B1.181
..B1.180:
        cmpl      $0, 368(%rsp)
        je        ..B1.187
..B1.181:
        fldt      368(%rsp)
        lea       _ones(%rip), %rax
        testb     %r14b, %r14b
        fmull     (%rax)
        fstpt     64(%rsp)
        je        ..B1.183
..B1.182:
        fldcw     258(%rsp)
..B1.183:
        testb     %bpl, %bpl
        je        ..B1.185
..B1.184:
        movl      %r13d, %edi
..___tag_value_tgammal.173:
        call      fesetround@PLT
..___tag_value_tgammal.174:
..B1.185:
        fldt      64(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.134
..B1.186:
        addq      $320, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 368
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.187:
        testb     %bpl, %bpl
        je        ..B1.189
..B1.188:
        movl      %r13d, %edi
..___tag_value_tgammal.192:
        call      fesetround@PLT
..___tag_value_tgammal.193:
..B1.189:
        lea       8+_zeros(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        divsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        movq      304(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B1.134
        jmp       ..B1.23
        .align    16,0x90
	.cfi_endproc
	.type	tgammal,@function
	.size	tgammal,.-tgammal
	.data
# -- End  tgammal
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xdc,0x09,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xd0,0x02,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfc,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xa0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xe0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 16
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_tgammal_S2:
	.long	1076610156
	.long	1072298443
	.long	2174450924
	.long	1012377779
	.type	_tgammal_S2,@object
	.size	_tgammal_S2,16
	.align 4
_tgammal_S1:
	.long	1649608659
	.long	3220853158
	.long	544854255
	.long	3162597952
	.type	_tgammal_S1,@object
	.size	_tgammal_S1,16
	.align 4
_tgammal_A2:
	.long	1833415567
	.long	1073038586
	.long	2552883231
	.long	1018148522
	.long	1595568275
	.long	1072556376
	.long	458238143
	.long	1015211580
	.long	3568820252
	.long	1071968804
	.long	1244365593
	.long	1010337025
	.long	799339768
	.long	1070612333
	.long	3666663601
	.long	1014637144
	.long	2303274075
	.long	1069290379
	.long	1349342802
	.long	1013375288
	.long	3297989586
	.long	1067256475
	.long	254004781
	.long	1007721194
	.long	3884346469
	.long	1076314993
	.long	442477728
	.long	1020223370
	.long	318512670
	.long	1076897708
	.long	471158811
	.long	1020359281
	.long	2510778168
	.long	1076450444
	.long	1901825555
	.long	1020381374
	.long	1635478559
	.long	1075598710
	.long	824259222
	.long	1018719588
	.long	1081324001
	.long	1074338163
	.long	3447625061
	.long	1017363587
	.long	3947156014
	.long	1072861090
	.long	1379190805
	.long	1015115048
	.long	420607958
	.long	1081212458
	.long	3477976495
	.long	1025491940
	.long	3806068795
	.long	1082138936
	.long	4217379562
	.long	1025598832
	.long	3604563515
	.long	1082026571
	.long	2373575058
	.long	1026208390
	.long	2007420787
	.long	1081336307
	.long	1677630540
	.long	1025930127
	.long	1505609351
	.long	1080304853
	.long	3680187356
	.long	1024342399
	.long	1798798240
	.long	1079036631
	.long	724429118
	.long	1022004699
	.long	573661072
	.long	1087072564
	.long	1175535566
	.long	1031320119
	.long	3742832273
	.long	1088193077
	.long	2163102943
	.long	1030481607
	.long	1347160184
	.long	1088323961
	.long	872134502
	.long	1032553403
	.long	3527047012
	.long	1087786342
	.long	974187100
	.long	1028831099
	.long	1718309677
	.long	1086914107
	.long	744179587
	.long	1031128181
	.long	2775103229
	.long	1085724862
	.long	1153587355
	.long	1029745229
	.long	1670522313
	.long	1093749470
	.long	3603089619
	.long	1038783597
	.long	2795800948
	.long	1094969738
	.long	1113103030
	.long	1039749901
	.long	3483985892
	.long	1095195750
	.long	707212107
	.long	1037942235
	.long	3634938287
	.long	1094882573
	.long	3822913920
	.long	1039689416
	.long	1233016495
	.long	1094091821
	.long	3838048750
	.long	1037461559
	.long	817468048
	.long	1093018536
	.long	360059332
	.long	1037856380
	.long	3404619429
	.long	1101025312
	.long	2324006265
	.long	1046266629
	.long	1757595371
	.long	1102333247
	.long	3317464771
	.long	1041601336
	.long	2951620935
	.long	1102677785
	.long	2114793479
	.long	1046829217
	.long	1356962213
	.long	1102426961
	.long	3236647680
	.long	1046257529
	.long	378361594
	.long	1101790815
	.long	2326245451
	.long	1045004339
	.long	704627613
	.long	1100821691
	.long	771850637
	.long	1043974839
	.type	_tgammal_A2,@object
	.size	_tgammal_A2,576
	.align 4
_tgammal_A1:
	.long	4294967295
	.long	1072693247
	.long	4294254158
	.long	1017118719
	.long	4235179544
	.long	3219290252
	.long	1647137719
	.long	3164508998
	.long	3258652024
	.long	1072670296
	.long	2787078119
	.long	1015928435
	.long	2402437986
	.long	3219982865
	.long	3119239167
	.long	3164440263
	.long	89167536
	.long	1072654929
	.long	3734368228
	.long	1015667286
	.long	3963139337
	.long	3220139136
	.long	2050876349
	.long	3162686202
	.long	2444554090
	.long	1072454648
	.long	2100370444
	.long	1016394631
	.long	2397397425
	.long	1067486888
	.long	3603318790
	.long	1011860326
	.long	3126988118
	.long	1071287373
	.long	3410508918
	.long	1015153675
	.long	480439205
	.long	3216734124
	.long	3426889025
	.long	3160197739
	.long	927904425
	.long	1069712317
	.long	1130465219
	.long	1014845612
	.long	4266680347
	.long	3216234636
	.long	1626218864
	.long	3161277677
	.long	0
	.long	1072693248
	.long	1528870101
	.long	964807786
	.long	119575502
	.long	3218804454
	.long	1881625935
	.long	3159804816
	.long	2341912255
	.long	1071274903
	.long	3500227044
	.long	1010193322
	.long	2554745004
	.long	3216302649
	.long	3304224511
	.long	3156439793
	.long	2968672914
	.long	1068696059
	.long	4206584367
	.long	1012030447
	.long	1090398824
	.long	1060208439
	.long	842313838
	.long	1004803849
	.long	4294967295
	.long	1072693247
	.long	4294967295
	.long	1017118719
	.long	4235179544
	.long	3219290252
	.long	4177388891
	.long	3164508998
	.long	3258652024
	.long	1072670296
	.long	1161858311
	.long	1015929131
	.long	2402437986
	.long	3219982865
	.long	3056812941
	.long	3164521903
	.long	89167541
	.long	1072654929
	.long	2427235615
	.long	1014999039
	.long	3963139706
	.long	3220139136
	.long	3024603167
	.long	3163626566
	.type	_tgammal_A1,@object
	.size	_tgammal_A1,384
	.align 4
_tgammal_B:
	.long	381774871
	.long	3211182444
	.long	436314138
	.long	1061814688
	.long	327235604
	.long	3208872248
	.long	723058467
	.long	1061917982
	.long	3650698365
	.long	3210701488
	.long	440509466
	.long	1064976804
	.long	3406779288
	.long	3214819974
	.long	941491840
	.long	1070005910
	.type	_tgammal_B,@object
	.size	_tgammal_B,64
	.align 4
_tgammal_C:
	.long	3362045620
	.long	1072523249
	.long	4064281872
	.long	1016386130
	.type	_tgammal_C,@object
	.size	_tgammal_C,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_TWO_32HP:
	.long	1048576
	.long	1106771968
	.type	_TWO_32HP,@object
	.size	_TWO_32HP,8
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.word	0
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,32
	.align 2
_POS_OVERFLOW:
	.word	11809
	.word	27445
	.word	35846
	.word	56177
	.word	16393
	.word	0
	.word	0
	.word	0
	.type	_POS_OVERFLOW,@object
	.size	_POS_OVERFLOW,16
	.align 2
_tgammal_S:
	.word	24210
	.word	1753
	.word	29241
	.word	50004
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	51350
	.word	39166
	.word	743
	.word	54836
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	3640
	.word	30975
	.word	46417
	.word	39360
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	53672
	.word	10841
	.word	35989
	.word	39843
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	36569
	.word	52859
	.word	829
	.word	59922
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	48029
	.word	59092
	.word	17416
	.word	34773
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	51219
	.word	52454
	.word	61951
	.word	62382
	.word	49123
	.word	0
	.word	0
	.word	0
	.type	_tgammal_S,@object
	.size	_tgammal_S,112
	.align 2
_tgammal_T2:
	.word	46722
	.word	54455
	.word	58933
	.word	43588
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	11656
	.word	9670
	.word	26887
	.word	53935
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	37336
	.word	10857
	.word	59094
	.word	50631
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	42778
	.word	63737
	.word	45056
	.word	33859
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	26755
	.word	45167
	.word	18275
	.word	32926
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	44721
	.word	19678
	.word	48085
	.word	54982
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	51053
	.word	39319
	.word	36729
	.word	62310
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	45874
	.word	5043
	.word	3955
	.word	43983
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	10698
	.word	13153
	.word	62730
	.word	37178
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	47802
	.word	34638
	.word	48577
	.word	58524
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	5552
	.word	49084
	.word	18209
	.word	47263
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	27946
	.word	40146
	.word	45659
	.word	36839
	.word	49127
	.word	0
	.word	0
	.word	0
	.word	43027
	.word	63179
	.word	35272
	.word	58163
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	51497
	.word	52569
	.word	40730
	.word	59709
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	50075
	.word	12939
	.word	25687
	.word	50566
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	50229
	.word	1163
	.word	49491
	.word	48597
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	65107
	.word	33673
	.word	57229
	.word	54934
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	40234
	.word	4993
	.word	58268
	.word	55864
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	37170
	.word	65173
	.word	7022
	.word	51747
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	26509
	.word	26471
	.word	6311
	.word	44116
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	19430
	.word	24800
	.word	9769
	.word	34884
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	11267
	.word	5159
	.word	36913
	.word	51530
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	64725
	.word	57237
	.word	10436
	.word	35732
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	9979
	.word	17874
	.word	14609
	.word	46771
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	48661
	.word	14142
	.word	37902
	.word	57970
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	44331
	.word	229
	.word	33140
	.word	34177
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	18505
	.word	49699
	.word	39129
	.word	38384
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	55009
	.word	45387
	.word	16863
	.word	41316
	.word	16352
	.word	0
	.word	0
	.word	0
	.word	32472
	.word	62749
	.word	3488
	.word	43302
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	38507
	.word	49300
	.word	64329
	.word	42149
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	54685
	.word	59022
	.word	19355
	.word	48977
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	11322
	.word	9818
	.word	14110
	.word	61121
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	35672
	.word	18289
	.word	56841
	.word	34043
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	40623
	.word	27826
	.word	27872
	.word	34330
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	23588
	.word	8736
	.word	44949
	.word	63368
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	65458
	.word	32180
	.word	45318
	.word	53999
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	7083
	.word	36157
	.word	35044
	.word	42795
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	41937
	.word	53706
	.word	63704
	.word	63466
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	56782
	.word	14002
	.word	58756
	.word	44258
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	50000
	.word	929
	.word	21531
	.word	58307
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	29164
	.word	41738
	.word	63210
	.word	36416
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	22318
	.word	7527
	.word	19550
	.word	43267
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	36459
	.word	32677
	.word	20918
	.word	49089
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	54142
	.word	37469
	.word	39035
	.word	54353
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	43987
	.word	37066
	.word	46591
	.word	55009
	.word	16353
	.word	0
	.word	0
	.word	0
	.word	23593
	.word	355
	.word	54000
	.word	36277
	.word	16394
	.word	0
	.word	0
	.word	0
	.word	38981
	.word	26613
	.word	65277
	.word	49004
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	37709
	.word	48898
	.word	35761
	.word	58794
	.word	16390
	.word	0
	.word	0
	.word	0
	.word	20062
	.word	54760
	.word	43546
	.word	63578
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	4376
	.word	65424
	.word	42405
	.word	62684
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	53174
	.word	32360
	.word	45557
	.word	56872
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	50961
	.word	21643
	.word	2055
	.word	47845
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	44504
	.word	53228
	.word	208
	.word	37559
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	22458
	.word	24129
	.word	6745
	.word	55322
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	45571
	.word	12909
	.word	9586
	.word	38400
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	63967
	.word	2945
	.word	37284
	.word	50443
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	40100
	.word	52281
	.word	37909
	.word	62907
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	14124
	.word	37910
	.word	18802
	.word	37402
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	28370
	.word	35717
	.word	42940
	.word	43396
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	57898
	.word	23307
	.word	6391
	.word	45620
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	46932
	.word	12112
	.word	28768
	.word	37645
	.word	16401
	.word	0
	.word	0
	.word	0
	.word	4289
	.word	37433
	.word	59606
	.word	54245
	.word	16399
	.word	0
	.word	0
	.word	0
	.word	64482
	.word	38480
	.word	36982
	.word	34595
	.word	16398
	.word	0
	.word	0
	.word	0
	.word	60245
	.word	51970
	.word	24673
	.word	39653
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	5116
	.word	50868
	.word	49618
	.word	41328
	.word	16394
	.word	0
	.word	0
	.word	0
	.word	41766
	.word	46400
	.word	3930
	.word	39543
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	10780
	.word	29057
	.word	64174
	.word	35004
	.word	16390
	.word	0
	.word	0
	.word	0
	.word	53671
	.word	48824
	.word	23962
	.word	57716
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	53678
	.word	31584
	.word	52783
	.word	44557
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	6190
	.word	44351
	.word	42084
	.word	64733
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	38744
	.word	53501
	.word	23462
	.word	44426
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	5624
	.word	56691
	.word	10270
	.word	57805
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	37833
	.word	48670
	.word	48257
	.word	35818
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	2793
	.word	44149
	.word	23652
	.word	43316
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	36532
	.word	60099
	.word	3358
	.word	47101
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	26210
	.word	6795
	.word	18267
	.word	52540
	.word	16408
	.word	0
	.word	0
	.word	0
	.word	4852
	.word	29729
	.word	14912
	.word	39944
	.word	16407
	.word	0
	.word	0
	.word	0
	.word	5407
	.word	41984
	.word	60375
	.word	53635
	.word	16405
	.word	0
	.word	0
	.word	0
	.word	50093
	.word	12566
	.word	13387
	.word	64582
	.word	16403
	.word	0
	.word	0
	.word	0
	.word	49147
	.word	13632
	.word	405
	.word	35287
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	19308
	.word	51191
	.word	49444
	.word	35336
	.word	16400
	.word	0
	.word	0
	.word	0
	.word	42358
	.word	64331
	.word	14656
	.word	65374
	.word	16397
	.word	0
	.word	0
	.word	0
	.word	20975
	.word	39047
	.word	28983
	.word	56232
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	29936
	.word	14466
	.word	41903
	.word	45232
	.word	16393
	.word	0
	.word	0
	.word	0
	.word	29960
	.word	52783
	.word	56228
	.word	34190
	.word	16391
	.word	0
	.word	0
	.word	0
	.word	35121
	.word	10599
	.word	28021
	.word	48776
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	24922
	.word	26565
	.word	63850
	.word	32942
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	35914
	.word	31178
	.word	25984
	.word	42348
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	32787
	.word	34531
	.word	38943
	.word	53136
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	12957
	.word	55078
	.word	57592
	.word	59546
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_tgammal_T2,@object
	.size	_tgammal_T2,1440
	.align 2
_tgammal_T1:
	.word	33333
	.word	22332
	.word	1335
	.word	65087
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	47908
	.word	35098
	.word	63659
	.word	65273
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	46380
	.word	12569
	.word	56008
	.word	65411
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	48850
	.word	62280
	.word	7688
	.word	65472
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	39250
	.word	51171
	.word	14264
	.word	65504
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	1152
	.word	29364
	.word	36762
	.word	65519
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	3017
	.word	25231
	.word	24799
	.word	65523
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	25322
	.word	65334
	.word	59366
	.word	65496
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	56776
	.word	58800
	.word	13007
	.word	65323
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	19307
	.word	43631
	.word	60167
	.word	64530
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	36275
	.word	49480
	.word	28369
	.word	61762
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	21913
	.word	46254
	.word	26555
	.word	54444
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	35908
	.word	6208
	.word	45367
	.word	40294
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	27036
	.word	5944
	.word	26542
	.word	42848
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	29582
	.word	26316
	.word	64938
	.word	47331
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	2023
	.word	42870
	.word	23457
	.word	61457
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	65015
	.word	19522
	.word	60134
	.word	39847
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	37347
	.word	35810
	.word	25431
	.word	54185
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	59586
	.word	38885
	.word	5307
	.word	36118
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	55581
	.word	26604
	.word	61099
	.word	48328
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	26530
	.word	8530
	.word	11497
	.word	64510
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	5884
	.word	49309
	.word	23213
	.word	43047
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	39008
	.word	7698
	.word	42498
	.word	57424
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	52706
	.word	42552
	.word	1743
	.word	38295
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	36375
	.word	51085
	.word	41378
	.word	51088
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	45665
	.word	51480
	.word	46902
	.word	34067
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	45474
	.word	25613
	.word	9050
	.word	44941
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	10137
	.word	18971
	.word	38654
	.word	59779
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	53562
	.word	56969
	.word	3619
	.word	46768
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	12931
	.word	40754
	.word	53864
	.word	63282
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	16582
	.word	62753
	.word	30052
	.word	46783
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	41763
	.word	18743
	.word	29826
	.word	47859
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	61445
	.word	12179
	.word	9444
	.word	35298
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	19195
	.word	47291
	.word	36415
	.word	61711
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	13845
	.word	40089
	.word	27018
	.word	32909
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	12017
	.word	58217
	.word	13416
	.word	64677
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	35895
	.word	43222
	.word	31657
	.word	65323
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	20613
	.word	26232
	.word	50069
	.word	65270
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	35252
	.word	65344
	.word	3340
	.word	65456
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	10600
	.word	13283
	.word	40541
	.word	64928
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	11880
	.word	32674
	.word	50280
	.word	34435
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	63446
	.word	286
	.word	54268
	.word	48349
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	8548
	.word	23982
	.word	1825
	.word	63713
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	34666
	.word	29375
	.word	64038
	.word	41244
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	59811
	.word	4152
	.word	44775
	.word	65267
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	18717
	.word	22920
	.word	1335
	.word	65087
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	45811
	.word	56269
	.word	63659
	.word	65273
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	65298
	.word	48931
	.word	56016
	.word	65411
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	32491
	.word	12018
	.word	7860
	.word	65472
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	14368
	.word	44485
	.word	16906
	.word	65504
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	41970
	.word	59715
	.word	3074
	.word	65520
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	4801
	.word	21592
	.word	65211
	.word	65527
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	62592
	.word	41973
	.word	39337
	.word	65531
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	13381
	.word	40677
	.word	15011
	.word	65529
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	46580
	.word	36519
	.word	54875
	.word	65490
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	37477
	.word	29387
	.word	41414
	.word	65216
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	16797
	.word	56146
	.word	15453
	.word	63766
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	40859
	.word	15091
	.word	60713
	.word	58161
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	1004
	.word	12255
	.word	49061
	.word	43226
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	52096
	.word	41479
	.word	59455
	.word	37399
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_tgammal_T1,@object
	.size	_tgammal_T1,960
	.align 2
_tgammal_B0:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_tgammal_B0,@object
	.size	_tgammal_B0,16
	.align 2
_TWO_POW_16383:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.type	_TWO_POW_16383,@object
	.size	_TWO_POW_16383,16
	.align 2
_TWO_POW_M16382:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.type	_TWO_POW_M16382,@object
	.size	_TWO_POW_M16382,16
	.align 2
_TWO_POW_M16000:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	383
	.word	0
	.word	0
	.word	0
	.type	_TWO_POW_M16000,@object
	.size	_TWO_POW_M16000,16
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
