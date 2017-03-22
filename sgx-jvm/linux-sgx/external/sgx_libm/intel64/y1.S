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
	.file "y1.c"
	.text
..TXTST0:
# -- Begin  y1
	.text
       .align    16,0x90
	.globl y1
y1:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_y1.1:
..L2:

        pushq     %r15
	.cfi_def_cfa_offset 16
	.cfi_offset 15, -16
        subq      $16, %rsp
	.cfi_def_cfa_offset 32
        xorb      %r15b, %r15b
        movsd     %xmm0, (%rsp)
..B1.2:
        fnstcw    10(%rsp)
..B1.3:
        movl      4(%rsp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $2146435072, %ecx
        jge       ..B1.13
..B1.4:
        cmpq      $0, (%rsp)
        jle       ..B1.19
..B1.5:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.7:
        fldcw     8(%rsp)
..B1.8:
        movb      $1, %r15b
..B1.9:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 48
        fldl      16(%rsp)
        fstpt     (%rsp)
..___tag_value_y1.7:
        call      __y1l@PLT
..___tag_value_y1.8:
..B1.25:
        addq      $16, %rsp
	.cfi_def_cfa_offset 32
..B1.10:
        fstpl     (%rsp)
        testb     %r15b, %r15b
        movsd     (%rsp), %xmm0
        je        ..B1.12
..B1.11:
        fldcw     10(%rsp)
..B1.12:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -16
..B1.13:
        andl      $-2146435073, %ecx
        orl       (%rsp), %ecx
        jne       ..B1.18
..B1.14:
        testl     $-2147483648, %eax
        je        ..B1.16
..B1.15:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -16
..B1.16:
        lea       _zeros(%rip), %rdx
        shrl      $31, %eax
        movsd     (%rdx,%rax,8), %xmm0
..B1.17:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -16
..B1.18:
        movsd     (%rsp), %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -16
..B1.19:
        lea       _zeros(%rip), %rax
        movl      (%rsp), %edx
        orl       %edx, %ecx
        movsd     (%rax), %xmm1
        je        ..B1.21
..B1.20:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -16
..B1.21:
        lea       8+_ones(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     %xmm1, %xmm0
..B1.22:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	y1,@function
	.size	y1,.-y1
	.data
# -- End  y1
	.text
# -- Begin  __y1l
	.text
       .align    16,0x90
	.globl __y1l
__y1l:
# parameter 1: 96 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___y1l.39:
..L40:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        lea       176+_S(%rip), %rax
        fldt      96(%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        jb        ..B2.14
..B2.2:
        lea       _val_1_77L(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        ja        ..B2.12
..B2.3:
        lea       _S(%rip), %rcx
        fldt      (%rcx)
        fcomip    %st(1), %st
        jbe       ..B2.5
..B2.4:
        lea       _Z(%rip), %rax
        lea       _P1(%rip), %rdx
        lea       16+_P1(%rip), %rcx
        lea       _Q1(%rip), %rsi
        lea       32+_P1(%rip), %rdi
        lea       48+_P1(%rip), %r8
        lea       16+_Q1(%rip), %r9
        fldt      (%rax)
        lea       64+_P1(%rip), %r10
        lea       80+_P1(%rip), %r11
        lea       32+_Q1(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       96+_P1(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       112+_P1(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       48+_Q1(%rip), %rsi
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       128+_P1(%rip), %rdi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r8)
        lea       144+_P1(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        lea       64+_Q1(%rip), %r9
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%r10)
        lea       160+_P1(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       176+_P1(%rip), %r11
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       80+_Q1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%rdx)
        lea       _ones(%rip), %rdx
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rcx)
        lea       192+_P1(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        lea       208+_P1(%rip), %rsi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       224+_P1(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        lea       240+_P1(%rip), %r8
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(3)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fdivrl    (%rdx)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B2.19
..B2.5:
        lea       16+_S(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        jbe       ..B2.7
..B2.6:
        lea       16+_Z(%rip), %rax
        lea       _P2(%rip), %rdx
        lea       16+_P2(%rip), %rcx
        lea       _Q2(%rip), %rsi
        lea       32+_P2(%rip), %rdi
        lea       48+_P2(%rip), %r8
        lea       16+_Q2(%rip), %r9
        fldt      (%rax)
        lea       64+_P2(%rip), %r10
        lea       80+_P2(%rip), %r11
        lea       32+_Q2(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       96+_P2(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       112+_P2(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       48+_Q2(%rip), %rsi
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       128+_P2(%rip), %rdi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r8)
        lea       144+_P2(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        lea       64+_Q2(%rip), %r9
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%r10)
        lea       160+_P2(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       176+_P2(%rip), %r11
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       80+_Q2(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%rdx)
        lea       _ones(%rip), %rdx
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rcx)
        lea       192+_P2(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        lea       208+_P2(%rip), %rsi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       224+_P2(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        lea       240+_P2(%rip), %r8
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(3)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fdivrl    (%rdx)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B2.19
..B2.7:
        movl      $2, %edx
        movl      $32, %eax
..B2.8:
        fldt      (%rax,%rcx)
        fcomip    %st(1), %st
        ja        ..B2.11
        jp        ..B2.11
..B2.9:
        incq      %rdx
        addq      $16, %rax
        cmpq      $11, %rdx
        jl        ..B2.8
..B2.10:
        movq      %rdx, %rax
        shlq      $4, %rax
..B2.11:
        lea       _Z(%rip), %rcx
        fldt      (%rax,%rcx)
        lea       (%rdx,%rdx,4), %rax
        shlq      $6, %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        lea       _P(%rip), %rdx
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      -640(%rax,%rdx)
        fmul      %st(1), %st
        fldt      -624(%rax,%rdx)
        fmul      %st(2), %st
        fldt      -608(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -592(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -576(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -560(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -544(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -528(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -512(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -496(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -480(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -464(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -448(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -432(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -416(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -400(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -384(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -368(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      -352(%rax,%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      -336(%rax,%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.19
..B2.12:
        fld       %st(0)
        lea       _P0(%rip), %rax
        fmul      %st(1), %st
        lea       16+_P0(%rip), %rdx
        fxch      %st(1)
        fld       %st(1)
        lea       32+_P0(%rip), %rcx
        fmul      %st(2), %st
        lea       48+_P0(%rip), %rsi
        lea       64+_P0(%rip), %rdi
        lea       80+_P0(%rip), %r8
        lea       96+_P0(%rip), %r9
        lea       112+_P0(%rip), %r10
        lea       128+_P0(%rip), %r11
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rax)
        lea       144+_P0(%rip), %rax
        fmul      %st(3), %st
        fldt      (%rdx)
        fmul      %st(4), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value___y1l.43:
        call      __j1l@PLT
..___tag_value___y1l.44:
..B2.23:
        fldt      112(%rsp)
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.13:
        fxch      %st(1)
        fstl      40(%rsp)
        fstp      %st(0)
        movsd     40(%rsp), %xmm0
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     (%rsp)
        call      log@PLT
..B2.24:
        fldt      96(%rsp)
        lea       _val_N1_0L(%rip), %rdx
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      16(%rsp)
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fldt      (%rdx)
        fdivp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        jmp       ..B2.19
..B2.14:
        lea       _ones(%rip), %rax
        lea       _val_36_0L(%rip), %rdx
        lea       _PP(%rip), %rcx
        lea       _QP(%rip), %rsi
        lea       16+_PP(%rip), %rdi
        lea       16+_QP(%rip), %r8
        lea       32+_PP(%rip), %r9
        lea       32+_QP(%rip), %r10
        fldl      (%rax)
        lea       48+_PP(%rip), %r11
        fdiv      %st(1), %st
        lea       48+_QP(%rip), %rax
        fxch      %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdx)
        lea       64+_PP(%rip), %rdx
        fmulp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rcx)
        lea       64+_QP(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       80+_PP(%rip), %rsi
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80+_QP(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       96+_PP(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       96+_QP(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       112+_PP(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       112+_QP(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_PP(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       128+_QP(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       144+_PP(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       144+_QP(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80(%rsp), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        movl      $-3, %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fstpt     -48(%rdi)
        fldt      (%rsi)
        lea       64(%rsp), %rsi
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -16(%rsi)
..___tag_value___y1l.47:
        call      __libm_sincos_pi4l@PLT
..___tag_value___y1l.48:
..B2.25:
        fldt      112(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.15:
        fldt      64(%rsp)
        lea       _val_0_03L(%rip), %rax
        fmulp     %st, %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        fadd      %st, %st(1)
        fld       %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fldt      (%rax)
        fmulp     %st, %st(1)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B2.17
        jp        ..B2.17
..B2.16:
        fstp      %st(1)
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fsqrt     
        fmulp     %st, %st(1)
        jmp       ..B2.19
..B2.17:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
        call      __ltoq@PLT
..B2.27:
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
        movaps    (%rsp), %xmm0
..___tag_value___y1l.52:
        call      __y1q@PLT
..___tag_value___y1l.53:
..B2.18:
        movaps    %xmm0, (%rsp)
        lea       (%rsp), %rdi
        call      __qtol@PLT
..B2.28:
        fstpl     40(%rsp)
        movsd     40(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
..B2.19:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__y1l,@function
	.size	__y1l,.-__y1l
	.data
# -- End  __y1l
	.section .rodata, "a"
	.align 4
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 2
_S:
	.word	56848
	.word	49844
	.word	39532
	.word	64842
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	5940
	.word	14600
	.word	60967
	.word	58048
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	48233
	.word	9194
	.word	47533
	.word	41870
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	19716
	.word	3898
	.word	3621
	.word	54727
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	41547
	.word	400
	.word	58272
	.word	33793
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	46312
	.word	65374
	.word	26362
	.word	40225
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	15253
	.word	48480
	.word	38891
	.word	46657
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12158
	.word	16394
	.word	14239
	.word	53090
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	31253
	.word	2914
	.word	8779
	.word	59523
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	29606
	.word	9567
	.word	8455
	.word	32978
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	51148
	.word	41491
	.word	50276
	.word	36194
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	1224
	.word	58769
	.word	30330
	.word	39411
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_S,@object
	.size	_S,192
	.align 2
_val_1_77L:
	.word	36700
	.word	62914
	.word	23592
	.word	57999
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_val_1_77L,@object
	.size	_val_1_77L,16
	.align 2
_Z:
	.word	5921
	.word	65426
	.word	63142
	.word	35997
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	29632
	.word	15745
	.word	62068
	.word	44479
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	41288
	.word	2893
	.word	15731
	.word	35209
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	12322
	.word	41360
	.word	35270
	.word	48124
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	21828
	.word	62500
	.word	60486
	.word	61019
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	54460
	.word	63729
	.word	58186
	.word	36952
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	27250
	.word	3142
	.word	10825
	.word	43393
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	20649
	.word	49414
	.word	53633
	.word	49831
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	59160
	.word	26587
	.word	26484
	.word	56269
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	57283
	.word	10561
	.word	16432
	.word	62706
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	44243
	.word	51162
	.word	18495
	.word	34571
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	63781
	.word	54231
	.word	15770
	.word	37789
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_Z,@object
	.size	_Z,192
	.align 2
_P1:
	.word	21770
	.word	62666
	.word	46899
	.word	35378
	.word	16344
	.word	0
	.word	0
	.word	0
	.word	28454
	.word	23396
	.word	41885
	.word	48017
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	61344
	.word	9332
	.word	29378
	.word	47284
	.word	49120
	.word	0
	.word	0
	.word	0
	.word	60127
	.word	25512
	.word	35054
	.word	43181
	.word	16353
	.word	0
	.word	0
	.word	0
	.word	25808
	.word	25324
	.word	9989
	.word	53047
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	46582
	.word	21062
	.word	5476
	.word	44690
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	61332
	.word	1419
	.word	18476
	.word	34785
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	51513
	.word	63307
	.word	40855
	.word	38307
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	22560
	.word	55588
	.word	5655
	.word	48627
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	56966
	.word	1018
	.word	54245
	.word	62436
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	17453
	.word	15035
	.word	65421
	.word	57312
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	53367
	.word	52143
	.word	3664
	.word	58921
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	16321
	.word	41163
	.word	52734
	.word	35978
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	15288
	.word	52738
	.word	13594
	.word	53174
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	42685
	.word	25071
	.word	48779
	.word	62301
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	7354
	.word	8216
	.word	60720
	.word	54923
	.word	49085
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,256
	.align 2
_Q1:
	.word	64227
	.word	6706
	.word	76
	.word	58690
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	8371
	.word	16695
	.word	56692
	.word	45251
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	9179
	.word	20845
	.word	29706
	.word	43997
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	44943
	.word	15045
	.word	18794
	.word	45614
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	10671
	.word	41768
	.word	56247
	.word	64663
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	25530
	.word	4231
	.word	4140
	.word	59815
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,96
	.align 2
_P2:
	.word	42420
	.word	39002
	.word	59647
	.word	60050
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	9209
	.word	30875
	.word	32003
	.word	38109
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	63337
	.word	29381
	.word	46828
	.word	35859
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	25340
	.word	63034
	.word	61574
	.word	39960
	.word	16351
	.word	0
	.word	0
	.word	0
	.word	21587
	.word	1531
	.word	17186
	.word	56230
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	35654
	.word	56865
	.word	61897
	.word	49746
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	6701
	.word	41291
	.word	18798
	.word	55141
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	35596
	.word	2936
	.word	34861
	.word	35797
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	59382
	.word	44988
	.word	46794
	.word	33373
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	39208
	.word	51898
	.word	38063
	.word	56363
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	34426
	.word	35681
	.word	546
	.word	45071
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	27055
	.word	35919
	.word	19182
	.word	39238
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	11379
	.word	16934
	.word	10723
	.word	57120
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	30778
	.word	48366
	.word	51582
	.word	60462
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	38504
	.word	65531
	.word	34807
	.word	43722
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	52867
	.word	16075
	.word	29885
	.word	33161
	.word	49086
	.word	0
	.word	0
	.word	0
	.type	_P2,@object
	.size	_P2,256
	.align 2
_Q2:
	.word	24596
	.word	8363
	.word	28671
	.word	51312
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	22889
	.word	23763
	.word	37225
	.word	46345
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	24119
	.word	47441
	.word	54379
	.word	48688
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	1240
	.word	59184
	.word	48701
	.word	46748
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	22888
	.word	65215
	.word	58633
	.word	45869
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	31023
	.word	50278
	.word	49158
	.word	64237
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,96
	.align 2
_P:
	.word	61722
	.word	43505
	.word	23994
	.word	37346
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	30291
	.word	24028
	.word	22863
	.word	34750
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	1126
	.word	901
	.word	35659
	.word	64778
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	24961
	.word	30514
	.word	59096
	.word	39096
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	35564
	.word	61687
	.word	10716
	.word	35707
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	46848
	.word	13986
	.word	10777
	.word	37699
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	25983
	.word	4211
	.word	22533
	.word	63008
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	41622
	.word	55080
	.word	16927
	.word	53017
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	59504
	.word	42891
	.word	54413
	.word	41203
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	12571
	.word	13616
	.word	30559
	.word	53698
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	24504
	.word	22188
	.word	22875
	.word	38390
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	45455
	.word	51567
	.word	14687
	.word	35767
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	2489
	.word	10124
	.word	54664
	.word	47037
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	18722
	.word	39166
	.word	48910
	.word	55942
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	52167
	.word	5
	.word	15467
	.word	33747
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	64650
	.word	13451
	.word	38167
	.word	40566
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	40279
	.word	20454
	.word	61426
	.word	45514
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	34995
	.word	30989
	.word	51614
	.word	33113
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	57629
	.word	30042
	.word	51710
	.word	35580
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	22801
	.word	35092
	.word	8589
	.word	55177
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	18213
	.word	9889
	.word	16490
	.word	42684
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	35117
	.word	3709
	.word	30056
	.word	56315
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	23979
	.word	26668
	.word	51699
	.word	63315
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	37328
	.word	63574
	.word	8303
	.word	34466
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	46391
	.word	17329
	.word	18036
	.word	35570
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	30054
	.word	21312
	.word	25061
	.word	63482
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	2570
	.word	34880
	.word	30954
	.word	61846
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	48934
	.word	61799
	.word	17650
	.word	43599
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	9996
	.word	54252
	.word	12364
	.word	40067
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	26028
	.word	41231
	.word	49512
	.word	42301
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	36582
	.word	15370
	.word	8472
	.word	36675
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	63654
	.word	5080
	.word	49106
	.word	53579
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	31057
	.word	54537
	.word	2640
	.word	43916
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	21142
	.word	46896
	.word	24614
	.word	39087
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	3964
	.word	40668
	.word	52365
	.word	60967
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	39332
	.word	9457
	.word	22878
	.word	52919
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	54210
	.word	18213
	.word	48816
	.word	39742
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	59971
	.word	56061
	.word	65402
	.word	41492
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	50867
	.word	29775
	.word	29962
	.word	60938
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	39008
	.word	10804
	.word	60432
	.word	53771
	.word	49083
	.word	0
	.word	0
	.word	0
	.word	8797
	.word	29964
	.word	61128
	.word	42619
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	49667
	.word	64253
	.word	56687
	.word	49119
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	12708
	.word	20574
	.word	59517
	.word	62457
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	61123
	.word	30114
	.word	53775
	.word	59072
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	12480
	.word	24376
	.word	40478
	.word	34829
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	31610
	.word	2541
	.word	29797
	.word	53230
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	57351
	.word	51530
	.word	20151
	.word	60005
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	27929
	.word	50140
	.word	46261
	.word	35597
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	13266
	.word	42539
	.word	17988
	.word	38413
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	60482
	.word	30188
	.word	54757
	.word	33461
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	28412
	.word	55589
	.word	45206
	.word	34637
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	32067
	.word	62816
	.word	60815
	.word	40858
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	5401
	.word	9100
	.word	50782
	.word	40732
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	54621
	.word	58390
	.word	59249
	.word	57428
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	35825
	.word	1375
	.word	322
	.word	55483
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	16432
	.word	16110
	.word	8852
	.word	37720
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	5000
	.word	34131
	.word	51425
	.word	35608
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	53929
	.word	6276
	.word	23388
	.word	58152
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	51579
	.word	17004
	.word	5616
	.word	54145
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	7103
	.word	16103
	.word	63732
	.word	61608
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	53540
	.word	14563
	.word	28490
	.word	42103
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	50538
	.word	7801
	.word	54621
	.word	42667
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	21289
	.word	53272
	.word	50234
	.word	61084
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	53615
	.word	53374
	.word	57363
	.word	50528
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	54514
	.word	9539
	.word	36475
	.word	33817
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	31658
	.word	4746
	.word	1163
	.word	44700
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	52867
	.word	45166
	.word	52684
	.word	57761
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	48341
	.word	5188
	.word	19803
	.word	58551
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	18455
	.word	50865
	.word	10646
	.word	36603
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	16819
	.word	38651
	.word	15489
	.word	53797
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	57587
	.word	29612
	.word	6330
	.word	65256
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	60926
	.word	53369
	.word	5356
	.word	64182
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	26295
	.word	42515
	.word	37715
	.word	37910
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	26848
	.word	34589
	.word	11687
	.word	44170
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	52547
	.word	41404
	.word	29188
	.word	51071
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	59720
	.word	37814
	.word	50864
	.word	57112
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	63754
	.word	1059
	.word	28610
	.word	65011
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	48135
	.word	53252
	.word	54091
	.word	43638
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	14971
	.word	8471
	.word	3609
	.word	49212
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	4052
	.word	57147
	.word	31484
	.word	54446
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	11824
	.word	2112
	.word	5588
	.word	41288
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	30937
	.word	56824
	.word	10791
	.word	37103
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	13479
	.word	25038
	.word	44704
	.word	59408
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	6837
	.word	41457
	.word	42451
	.word	43375
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	53800
	.word	46435
	.word	51001
	.word	65352
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	15811
	.word	55852
	.word	5902
	.word	37814
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	19663
	.word	64639
	.word	1883
	.word	55408
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	5332
	.word	44968
	.word	15414
	.word	48765
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	24635
	.word	46043
	.word	59114
	.word	34832
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	15088
	.word	55241
	.word	15044
	.word	44102
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	6405
	.word	38383
	.word	37570
	.word	61587
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	22931
	.word	45450
	.word	10147
	.word	51835
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	64299
	.word	50831
	.word	27180
	.word	35495
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	23200
	.word	7806
	.word	1364
	.word	35222
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	45807
	.word	4679
	.word	55327
	.word	47491
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	30764
	.word	65323
	.word	55387
	.word	45120
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	15039
	.word	21256
	.word	11217
	.word	60156
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	57173
	.word	14361
	.word	2624
	.word	34299
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	56410
	.word	62508
	.word	42699
	.word	45420
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	50446
	.word	42024
	.word	63781
	.word	38435
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	10333
	.word	60348
	.word	17128
	.word	40300
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	43838
	.word	25371
	.word	62467
	.word	64794
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	26845
	.word	6377
	.word	43437
	.word	57595
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	7442
	.word	5283
	.word	26036
	.word	37481
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	15454
	.word	29184
	.word	54291
	.word	63010
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	5297
	.word	38874
	.word	4456
	.word	64615
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	12966
	.word	61910
	.word	53167
	.word	53110
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	20395
	.word	4102
	.word	12283
	.word	41188
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	19094
	.word	29300
	.word	12828
	.word	33187
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	28120
	.word	22694
	.word	2680
	.word	36839
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	25698
	.word	43431
	.word	60429
	.word	58329
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	59006
	.word	57475
	.word	62067
	.word	42866
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	60088
	.word	17714
	.word	44332
	.word	33435
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	3844
	.word	12917
	.word	11965
	.word	57777
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	17310
	.word	43236
	.word	29566
	.word	44535
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	38891
	.word	11594
	.word	35001
	.word	36788
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	62784
	.word	19866
	.word	27725
	.word	56232
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	26552
	.word	62874
	.word	50518
	.word	55747
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	41449
	.word	54733
	.word	7288
	.word	42389
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	25436
	.word	16965
	.word	21076
	.word	40960
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	39083
	.word	3258
	.word	61438
	.word	39229
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	62602
	.word	12963
	.word	51303
	.word	56904
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	41532
	.word	46799
	.word	27449
	.word	55757
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	25508
	.word	35836
	.word	14539
	.word	65290
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	34796
	.word	56106
	.word	58112
	.word	60724
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	52331
	.word	50671
	.word	56071
	.word	55789
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	50430
	.word	49256
	.word	15625
	.word	50948
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	15753
	.word	56169
	.word	3376
	.word	35261
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	47489
	.word	43658
	.word	29070
	.word	63380
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	14447
	.word	45157
	.word	5
	.word	62576
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	14392
	.word	17120
	.word	59854
	.word	55457
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	2389
	.word	54610
	.word	7938
	.word	36154
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	24225
	.word	54966
	.word	22944
	.word	63335
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	6246
	.word	36346
	.word	2616
	.word	48455
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	61678
	.word	13662
	.word	7713
	.word	42050
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	4329
	.word	54820
	.word	31411
	.word	61458
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	48026
	.word	57812
	.word	53661
	.word	52979
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	47545
	.word	24071
	.word	10224
	.word	46463
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	51405
	.word	19517
	.word	26454
	.word	39893
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	2714
	.word	52847
	.word	64285
	.word	36922
	.word	49088
	.word	0
	.word	0
	.word	0
	.word	51895
	.word	36793
	.word	33802
	.word	38136
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	2488
	.word	31490
	.word	30800
	.word	50300
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	13623
	.word	29920
	.word	59063
	.word	53960
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	4071
	.word	44015
	.word	56417
	.word	57333
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	57861
	.word	52773
	.word	20389
	.word	58553
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	64934
	.word	37948
	.word	51829
	.word	48662
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	56954
	.word	19807
	.word	167
	.word	48948
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	28104
	.word	12997
	.word	51038
	.word	61119
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	3409
	.word	4305
	.word	3650
	.word	60679
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	43193
	.word	24597
	.word	44129
	.word	53916
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	17521
	.word	14667
	.word	10096
	.word	52923
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	55445
	.word	18677
	.word	32228
	.word	61988
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	19057
	.word	4855
	.word	59096
	.word	60271
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	13479
	.word	55645
	.word	42570
	.word	41372
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	1282
	.word	28535
	.word	31957
	.word	39927
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	572
	.word	61602
	.word	36341
	.word	52326
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	28923
	.word	3577
	.word	27552
	.word	50228
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	55167
	.word	15365
	.word	46318
	.word	39497
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	19602
	.word	36437
	.word	16591
	.word	37792
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	46605
	.word	39778
	.word	56315
	.word	52295
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	30298
	.word	45364
	.word	48554
	.word	37056
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	36916
	.word	47157
	.word	55353
	.word	44757
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	36754
	.word	52515
	.word	12207
	.word	52242
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	22625
	.word	1809
	.word	14640
	.word	50749
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	16563
	.word	52625
	.word	13365
	.word	56520
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	27329
	.word	9102
	.word	21835
	.word	42848
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	25313
	.word	26147
	.word	37973
	.word	47112
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	62093
	.word	47762
	.word	2878
	.word	53553
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	45304
	.word	46632
	.word	22697
	.word	58245
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	52304
	.word	22429
	.word	48300
	.word	47033
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	1240
	.word	8778
	.word	3434
	.word	50676
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	64382
	.word	35832
	.word	24068
	.word	53872
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	9372
	.word	49794
	.word	11542
	.word	57592
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	29118
	.word	43193
	.word	8003
	.word	35849
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	3582
	.word	16932
	.word	42429
	.word	38089
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	26846
	.word	61869
	.word	11277
	.word	45246
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	12490
	.word	64508
	.word	30037
	.word	47862
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	36625
	.word	65383
	.word	13633
	.word	34114
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	6011
	.word	54183
	.word	37651
	.word	35991
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	43024
	.word	51442
	.word	35220
	.word	35244
	.word	16321
	.word	0
	.word	0
	.word	0
	.word	2338
	.word	9974
	.word	58099
	.word	36011
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	54231
	.word	56678
	.word	56840
	.word	40082
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	47406
	.word	57592
	.word	65286
	.word	50617
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	15992
	.word	46142
	.word	20381
	.word	45257
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	17722
	.word	6937
	.word	55940
	.word	54630
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	44953
	.word	7303
	.word	1203
	.word	38053
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	9155
	.word	41724
	.word	2745
	.word	45433
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	23208
	.word	13836
	.word	51920
	.word	47377
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	46053
	.word	58453
	.word	7516
	.word	56049
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	51100
	.word	31216
	.word	1872
	.word	41469
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	24653
	.word	32384
	.word	37643
	.word	48672
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	61101
	.word	35176
	.word	37213
	.word	47362
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	62301
	.word	22066
	.word	41591
	.word	55226
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	37791
	.word	15476
	.word	41140
	.word	62892
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	18919
	.word	33505
	.word	35758
	.word	36479
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	58610
	.word	16753
	.word	52674
	.word	39626
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	19653
	.word	21667
	.word	11428
	.word	45800
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	51212
	.word	10491
	.word	15737
	.word	59703
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	54759
	.word	31668
	.word	63308
	.word	34425
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	62523
	.word	11996
	.word	50486
	.word	33912
	.word	49089
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,3200
	.align 2
_P0:
	.word	10891
	.word	2847
	.word	4043
	.word	50135
	.word	16324
	.word	0
	.word	0
	.word	0
	.word	8878
	.word	10523
	.word	36094
	.word	35357
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	12191
	.word	2098
	.word	38631
	.word	38182
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	58416
	.word	39431
	.word	6152
	.word	63591
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	18600
	.word	1852
	.word	53867
	.word	39282
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	37377
	.word	45824
	.word	1379
	.word	34108
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	58815
	.word	28468
	.word	57342
	.word	38462
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	35703
	.word	42482
	.word	52164
	.word	49581
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	14326
	.word	44453
	.word	47843
	.word	56988
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	21045
	.word	58852
	.word	12449
	.word	51395
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_P0,@object
	.size	_P0,160
	.align 2
_val_N1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.type	_val_N1_0L,@object
	.size	_val_N1_0L,16
	.align 2
_tonpi:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_tonpi,@object
	.size	_tonpi,16
	.align 2
_val_36_0L:
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_val_36_0L,@object
	.size	_val_36_0L,16
	.align 2
_PP:
	.word	52008
	.word	23245
	.word	2457
	.word	38658
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	61876
	.word	57278
	.word	25234
	.word	61346
	.word	49090
	.word	0
	.word	0
	.word	0
	.word	33834
	.word	23040
	.word	39409
	.word	49003
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	54525
	.word	6554
	.word	40935
	.word	51421
	.word	49100
	.word	0
	.word	0
	.word	0
	.word	9848
	.word	9769
	.word	18248
	.word	38344
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	45790
	.word	60252
	.word	47538
	.word	43957
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	27022
	.word	41654
	.word	23333
	.word	43744
	.word	16351
	.word	0
	.word	0
	.word	0
	.word	12816
	.word	40723
	.word	46387
	.word	47196
	.word	49127
	.word	0
	.word	0
	.word	0
	.word	4849
	.word	19418
	.word	12136
	.word	48545
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_PP,@object
	.size	_PP,160
	.align 2
_QP:
	.word	65402
	.word	6822
	.word	45128
	.word	37300
	.word	49084
	.word	0
	.word	0
	.word	0
	.word	41532
	.word	52948
	.word	50360
	.word	54021
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	21320
	.word	30785
	.word	26870
	.word	37955
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	55382
	.word	38611
	.word	29746
	.word	34115
	.word	16330
	.word	0
	.word	0
	.word	0
	.word	37194
	.word	6554
	.word	16235
	.word	42314
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	17886
	.word	15840
	.word	33918
	.word	38666
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	62905
	.word	55876
	.word	18072
	.word	57284
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	28423
	.word	64693
	.word	26579
	.word	40379
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	40135
	.word	58793
	.word	24002
	.word	37757
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_QP,@object
	.size	_QP,160
	.align 2
_val_0_03L:
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_val_0_03L,@object
	.size	_val_0_03L,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
