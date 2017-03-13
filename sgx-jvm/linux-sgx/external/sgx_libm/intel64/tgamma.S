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
	.file "tgamma.c"
	.text
..TXTST0:
# -- Begin  tgamma
	.text
       .align    16,0x90
	.globl tgamma
tgamma:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_tgamma.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $104, %rsp
	.cfi_def_cfa_offset 160
        fldz      
        lea       _zeros(%rip), %r15
        xorl      %r12d, %r12d
        movd      %xmm0, %rax
        xorb      %r13b, %r13b
        movsd     %xmm0, 72(%rsp)
        fstpt     48(%rsp)
        movq      (%r15), %rdx
        movq      %rax, 16(%rsp)
        movq      %rdx, 80(%rsp)
..B1.2:
        fnstcw    90(%rsp)
..B1.3:
        movzwl    90(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 88(%rsp)
..B1.5:
        fldcw     88(%rsp)
..B1.6:
        movb      $1, %r13b
..B1.7:
..___tag_value_tgamma.16:
        call      fegetround@PLT
..___tag_value_tgamma.17:
..B1.121:
        movl      %eax, %r14d
..B1.8:
        testl     %r14d, %r14d
        je        ..B1.10
..B1.9:
        xorl      %edi, %edi
        movl      $1, %r12d
..___tag_value_tgamma.18:
        call      fesetround@PLT
..___tag_value_tgamma.19:
..B1.10:
        movzwl    78(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        movl      76(%rsp), %ebx
        shrl      $31, %ebx
        cmpl      $2047, %edx
        je        ..B1.108
..B1.11:
        movsd     72(%rsp), %xmm2
        movsd     (%r15), %xmm0
        movl      20(%rsp), %eax
        andl      $2147483647, %eax
        movl      16(%rsp), %ecx
        movl      %eax, 20(%rsp)
        movsd     %xmm0, (%rsp)
        ucomisd   %xmm0, %xmm2
        jp        ..B1.12
        je        ..B1.103
..B1.12:
        cmpl      $262144, %eax
        jl        ..B1.98
..B1.13:
        je        ..B1.97
..B1.14:
        testl     %ebx, %ebx
        je        ..B1.25
..B1.15:
        cmpl      $1075, %edx
        jge       ..B1.87
..B1.16:
        lea       _TWO_52H(%rip), %rax
        movsd     16(%rsp), %xmm1
        movaps    %xmm1, %xmm0
        addsd     (%rax), %xmm0
        movsd     %xmm0, 40(%rsp)
        movsd     40(%rsp), %xmm0
        movl      40(%rsp), %r15d
        subsd     (%rax), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B1.18
..B1.17:
        lea       _ones(%rip), %rax
        incl      %r15d
        subsd     (%rax), %xmm0
..B1.18:
        ucomisd   %xmm0, %xmm1
        jp        ..B1.19
        je        ..B1.82
..B1.19:
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        comisd    %xmm2, %xmm0
        jbe       ..B1.25
..B1.20:
        testl     %r12d, %r12d
        je        ..B1.22
..B1.21:
        movl      %r14d, %edi
..___tag_value_tgamma.20:
        call      fesetround@PLT
..___tag_value_tgamma.21:
..B1.22:
        lea       _small_value_64(%rip), %rax
        notl      %r15d
        andl      $1, %r15d
        testb     %r13b, %r13b
        movsd     (%rax,%r15,8), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.24
..B1.23:
        fldcw     90(%rsp)
..B1.24:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.25:
        lea       _overflow_boundary(%rip), %rax
        comisd    (%rax), %xmm2
        jae       ..B1.92
..B1.26:
        testl     %ebx, %ebx
        je        ..B1.28
..B1.27:
        lea       _TWO_52H(%rip), %rax
        lea       144+_tgamma_sin_table(%rip), %rcx
        movsd     16(%rsp), %xmm0
        lea       128+_tgamma_sin_table(%rip), %rsi
        movaps    %xmm0, %xmm1
        movaps    %xmm0, %xmm3
        lea       112+_tgamma_sin_table(%rip), %r8
        lea       96+_tgamma_sin_table(%rip), %r9
        fldt      (%rcx)
        lea       80+_tgamma_sin_table(%rip), %r10
        fldt      (%rsi)
        lea       64+_tgamma_sin_table(%rip), %r11
        lea       48+_tgamma_sin_table(%rip), %r15
        addsd     (%rax), %xmm1
        fldt      (%r8)
        lea       32+_tgamma_sin_table(%rip), %rdi
        fldt      (%r9)
        lea       16+_tgamma_sin_table(%rip), %rcx
        fldt      (%r10)
        lea       _tgamma_sin_table(%rip), %rsi
        movsd     %xmm1, 40(%rsp)
        movsd     40(%rsp), %xmm2
        movsd     (%rax), %xmm1
        subsd     %xmm1, %xmm2
        subsd     %xmm2, %xmm3
        movsd     %xmm3, 24(%rsp)
        andl      $2147483647, 28(%rsp)
        fldl      24(%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fld       %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r15)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%rsi)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     48(%rsp)
        jmp       ..B1.29
..B1.28:
        lea       _TWO_52H(%rip), %rax
        movsd     16(%rsp), %xmm0
        movsd     (%rax), %xmm1
..B1.29:
        addsd     %xmm0, %xmm1
        movsd     %xmm1, 40(%rsp)
        movsd     40(%rsp), %xmm1
        movl      40(%rsp), %ecx
        subsd     (%rax), %xmm1
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm3
        comisd    %xmm0, %xmm1
        jbe       ..B1.31
..B1.30:
        decl      %ecx
        subsd     %xmm3, %xmm1
..B1.31:
        lea       1(%rcx), %r15d
        andl      $1, %r15d
        comisd    .L_2il0floatpacket.1(%rip), %xmm0
        jb        ..B1.54
..B1.32:
        movl      %ecx, %ebp
        movl      %ecx, %edx
        andl      $-16, %ebp
        andl      $15, %edx
        movl      %ebp, %esi
        cmpl      $16, %ecx
        movl      %ebp, %ecx
        movaps    %xmm0, %xmm2
        lea       _tgamma_A_table(%rip), %r8
        subsd     %xmm1, %xmm2
        movsd     %xmm2, 64(%rsp)
        lea       -2(%rdx), %eax
        fldl      64(%rsp)
        cmovl     %eax, %edx
        sarl      $2, %esi
        fld       %st(0)
        sarl      $3, %ecx
        addl      %ebp, %esi
        sarl      $4, %ebp
        addl      %ebp, %ecx
        addl      %ecx, %esi
        movslq    %esi, %rsi
        shlq      $4, %rsi
        fmul      %st(1), %st
        testl     %edx, %edx
        fldt      352(%r8,%rsi)
        fldt      (%r8,%rsi)
        fmul      %st(3), %st
        movsd     %xmm3, 64(%rsp)
        fadd      %st(2), %st
        fldt      176(%r8,%rsi)
        movsd     %xmm2, 32(%rsp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      16(%r8,%rsi)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      32(%r8,%rsi)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fldt      192(%r8,%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      208(%r8,%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      224(%r8,%rsi)
        fldt      240(%r8,%rsi)
        fldt      256(%r8,%rsi)
        fldt      48(%r8,%rsi)
        fmul      %st(6), %st
        fadd      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fldt      64(%r8,%rsi)
        fmul      %st(5), %st
        fadd      %st(4), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      80(%r8,%rsi)
        fmul      %st(4), %st
        fadd      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      96(%r8,%rsi)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      128(%r8,%rsi)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fldt      272(%r8,%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      288(%r8,%rsi)
        fldt      304(%r8,%rsi)
        faddp     %st, %st(2)
        fldt      112(%r8,%rsi)
        fmul      %st(5), %st
        fadd      %st(4), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      144(%r8,%rsi)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      160(%r8,%rsi)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      320(%r8,%rsi)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fldt      336(%r8,%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     8(%rsp)
        fldl      64(%rsp)
        fld       %st(0)
        je        ..B1.37
..B1.33:
        movl      $1, %eax
        jle       ..B1.37
..B1.34:
        movsd     %xmm0, 64(%rsp)
        fldl      64(%rsp)
..B1.35:
        movl      %eax, 64(%rsp)
        incl      %eax
        fildl     64(%rsp)
        cmpl      %edx, %eax
        fsubr     %st(1), %st
        fmulp     %st, %st(2)
        jle       ..B1.35
..B1.36:
        fstp      %st(0)
..B1.37:
        testl     %ebx, %ebx
        je        ..B1.48
..B1.38:
        fldt      48(%rsp)
        fldt      8(%rsp)
        movsd     %xmm0, 64(%rsp)
        fldl      64(%rsp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fdivrp    %st, %st(1)
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        fstpl     (%rsp)
        movsd     (%rsp), %xmm1
        comisd    72(%rsp), %xmm0
        jbe       ..B1.45
..B1.39:
        movaps    %xmm1, %xmm0
        testl     %r15d, %r15d
        xorps     .L_2il0floatpacket.5(%rip), %xmm0
        jne       ..L42
        movaps    %xmm1, %xmm0
..L42:
        testl     %r12d, %r12d
        je        ..B1.42
..B1.40:
        movl      %r14d, %edi
        movsd     %xmm0, 80(%rsp)
..___tag_value_tgamma.43:
        call      fesetround@PLT
..___tag_value_tgamma.44:
..B1.41:
        movsd     80(%rsp), %xmm0
        xorl      %r12d, %r12d
..B1.42:
        lea       _tgamma_A176_inv(%rip), %rax
        testl     %r15d, %r15d
        movsd     %xmm0, 64(%rsp)
        fldl      64(%rsp)
        fldt      (%rax)
        fmulp     %st, %st(1)
        je        ..B1.44
..B1.43:
        fchs      
        fstpl     80(%rsp)
        jmp       ..B1.46
..B1.44:
        fstpl     80(%rsp)
        jmp       ..B1.46
..B1.45:
        movsd     %xmm1, 80(%rsp)
..B1.46:
        shll      $31, %r15d
        xorl      %r15d, 84(%rsp)
        testl     %r12d, %r12d
        je        ..B1.51
..B1.47:
        movl      %r14d, %edi
..___tag_value_tgamma.45:
        call      fesetround@PLT
..___tag_value_tgamma.46:
        jmp       ..B1.51
..B1.48:
        fstp      %st(1)
        fldt      8(%rsp)
        testl     %r12d, %r12d
        fmulp     %st, %st(1)
        fstpt     8(%rsp)
        je        ..B1.50
..B1.49:
        movl      %r14d, %edi
..___tag_value_tgamma.47:
        call      fesetround@PLT
..___tag_value_tgamma.48:
..B1.50:
        fldt      8(%rsp)
        fstpl     80(%rsp)
..B1.51:
        testb     %r13b, %r13b
        je        ..B1.53
..B1.52:
        movsd     32(%rsp), %xmm0
        movsd     %xmm0, 24(%rsp)
..B1.122:
        fldcw     90(%rsp)
..B1.53:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.54:
        fldt      .L_2il0floatpacket.2(%rip)
        comisd    %xmm0, %xmm3
        movsd     %xmm0, 64(%rsp)
        fldl      64(%rsp)
        movsd     %xmm3, 64(%rsp)
        fldl      64(%rsp)
        fadd      %st(1), %st
        fcmovbe   %st(1), %st
        fcomi     %st(2), %st
        fxch      %st(2)
        fstp      %st(0)
        jb        ..B1.56
..B1.55:
        movsd     %xmm3, 64(%rsp)
        lea       _tgamma_A150_table(%rip), %rbp
        fldl      64(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstl      24(%rsp)
        fxch      %st(1)
        jmp       ..B1.60
..B1.56:
        fldt      .L_2il0floatpacket.3(%rip)
        fcomi     %st(2), %st
        ja        ..B1.58
        jp        ..B1.58
..B1.57:
        fstp      %st(0)
        lea       _local_minimum(%rip), %rax
        lea       _tgamma_A125_table(%rip), %rbp
        movsd     %xmm3, 64(%rsp)
        fldl      64(%rsp)
        faddl     (%rax)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstl      24(%rsp)
        fxch      %st(1)
        jmp       ..B1.60
..B1.58:
        fcomip    %st(2), %st
        jbe       ..B1.60
..B1.59:
        movsd     %xmm3, 64(%rsp)
        lea       _tgamma_A100_table(%rip), %rbp
        fldl      64(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstl      24(%rsp)
        fxch      %st(1)
..B1.60:
        fstp      %st(1)
        fldt      (%rbp)
        testl     %edx, %edx
        je        ..B1.63
..B1.61:
        fldl      24(%rsp)
        fldt      240(%rbp)
        fmul      %st(1), %st
        fldt      224(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      208(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      192(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      176(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      160(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      144(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      128(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      112(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      96(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      80(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      64(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      32(%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      16(%rbp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
..B1.63:
        comisd    %xmm0, %xmm3
        jbe       ..B1.72
..B1.64:
        fstp      %st(1)
        testl     %ebx, %ebx
        je        ..B1.68
..B1.65:
        fldt      48(%rsp)
        testl     %r12d, %r12d
        fmulp     %st, %st(1)
        movsd     %xmm3, 64(%rsp)
        fldl      64(%rsp)
        fdivp     %st, %st(1)
        fchs      
        fstpt     (%rsp)
        je        ..B1.67
..B1.66:
        movl      %r14d, %edi
..___tag_value_tgamma.69:
        call      fesetround@PLT
..___tag_value_tgamma.70:
..B1.67:
        fldt      (%rsp)
        fchs      
        fstpl     80(%rsp)
        shll      $31, %r15d
        xorl      %r15d, 84(%rsp)
        jmp       ..B1.79
..B1.68:
        testl     %r12d, %r12d
        je        ..B1.71
..B1.69:
        movl      %r14d, %edi
        fstpt     (%rsp)
..___tag_value_tgamma.71:
        call      fesetround@PLT
..___tag_value_tgamma.72:
..B1.70:
        fldt      (%rsp)
        movsd     16(%rsp), %xmm0
..B1.71:
        movsd     %xmm0, 64(%rsp)
        fldl      64(%rsp)
        fdivrp    %st, %st(1)
        fstpl     80(%rsp)
        jmp       ..B1.79
..B1.72:
        testl     %ebx, %ebx
        je        ..B1.76
..B1.73:
        fldt      48(%rsp)
        testl     %r12d, %r12d
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        movsd     %xmm3, 64(%rsp)
        fldl      64(%rsp)
        fdivp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.75
..B1.74:
        movl      %r14d, %edi
..___tag_value_tgamma.73:
        call      fesetround@PLT
..___tag_value_tgamma.74:
..B1.75:
        fldt      (%rsp)
        fstpl     80(%rsp)
        jmp       ..B1.79
..B1.76:
        fstp      %st(1)
        testl     %r12d, %r12d
        je        ..B1.78
..B1.77:
        movl      %r14d, %edi
        fstpt     (%rsp)
..___tag_value_tgamma.75:
        call      fesetround@PLT
..___tag_value_tgamma.76:
..B1.123:
        fldt      (%rsp)
..B1.78:
        fstpl     80(%rsp)
..B1.79:
        testb     %r13b, %r13b
        je        ..B1.81
..B1.80:
        fldcw     90(%rsp)
..B1.81:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.82:
        testl     %r12d, %r12d
        je        ..B1.84
..B1.83:
        movl      %r14d, %edi
..___tag_value_tgamma.97:
        call      fesetround@PLT
..___tag_value_tgamma.98:
..B1.84:
        movsd     (%rsp), %xmm0
        testb     %r13b, %r13b
        divsd     %xmm0, %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.86
..B1.85:
        fldcw     90(%rsp)
..B1.86:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.87:
        testl     %r12d, %r12d
        je        ..B1.89
..B1.88:
        movl      %r14d, %edi
..___tag_value_tgamma.119:
        call      fesetround@PLT
..___tag_value_tgamma.120:
..B1.89:
        movsd     (%rsp), %xmm0
        testb     %r13b, %r13b
        divsd     %xmm0, %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.91
..B1.90:
        fldcw     90(%rsp)
..B1.91:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.92:
        testl     %r12d, %r12d
        je        ..B1.94
..B1.93:
        movl      %r14d, %edi
..___tag_value_tgamma.141:
        call      fesetround@PLT
..___tag_value_tgamma.142:
..B1.94:
        lea       _large_value_64(%rip), %rax
        testb     %r13b, %r13b
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.96
..B1.95:
        fldcw     90(%rsp)
..B1.96:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.97:
        testl     %ecx, %ecx
        jne       ..B1.14
..B1.98:
        testl     %r12d, %r12d
        je        ..B1.100
..B1.99:
        movl      %r14d, %edi
..___tag_value_tgamma.163:
        call      fesetround@PLT
..___tag_value_tgamma.164:
..B1.100:
        movl      %ebx, %ebx
        lea       _large_value_64(%rip), %rax
        testb     %r13b, %r13b
        movsd     (%rax,%rbx,8), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.102
..B1.101:
        fldcw     90(%rsp)
..B1.102:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.103:
        testl     %r12d, %r12d
        je        ..B1.105
..B1.104:
        movl      %r14d, %edi
..___tag_value_tgamma.185:
        call      fesetround@PLT
..___tag_value_tgamma.186:
..B1.105:
        lea       _ones(%rip), %rax
        testb     %r13b, %r13b
        movsd     (%rax,%rbx,8), %xmm0
        divsd     (%rsp), %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.107
..B1.106:
        fldcw     90(%rsp)
..B1.107:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.108:
        testl     %r12d, %r12d
        je        ..B1.110
..B1.109:
        movl      %r14d, %edi
..___tag_value_tgamma.207:
        call      fesetround@PLT
..___tag_value_tgamma.208:
..B1.110:
        testl     %ebx, %ebx
        je        ..B1.113
..B1.111:
        testl     $1048575, 76(%rsp)
        jne       ..B1.113
..B1.112:
        cmpl      $0, 72(%rsp)
        je        ..B1.116
..B1.113:
        lea       _ones(%rip), %rax
        testb     %r13b, %r13b
        movsd     72(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.115
..B1.114:
        fldcw     90(%rsp)
..B1.115:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.116:
        lea       8+_zeros(%rip), %rax
        testb     %r13b, %r13b
        movsd     (%rax), %xmm0
        divsd     (%r15), %xmm0
        movsd     %xmm0, 80(%rsp)
        je        ..B1.118
..B1.117:
        fldcw     90(%rsp)
..B1.118:
        movsd     80(%rsp), %xmm0
        addq      $104, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	tgamma,@function
	.size	tgamma,.-tgamma
	.data
# -- End  tgamma
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xa0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.5:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xc0672000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0xc0660000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_overflow_boundary:
	.long	3848402504
	.long	1080390650
	.type	_overflow_boundary,@object
	.size	_overflow_boundary,8
	.align 4
_local_minimum:
	.long	2371549438
	.long	1071483745
	.type	_local_minimum,@object
	.size	_local_minimum,8
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 2
_tgamma_sin_table:
	.word	39192
	.word	38974
	.word	13074
	.word	53901
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	25233
	.word	24099
	.word	23041
	.word	53198
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	14010
	.word	1755
	.word	29241
	.word	50004
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	12661
	.word	39557
	.word	743
	.word	54836
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	23329
	.word	12349
	.word	46418
	.word	39360
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	43549
	.word	22984
	.word	36093
	.word	39843
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	50635
	.word	59140
	.word	19560
	.word	59922
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	39826
	.word	40176
	.word	51583
	.word	34788
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	63061
	.word	25910
	.word	60600
	.word	64252
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	21154
	.word	4399
	.word	17218
	.word	48316
	.word	16350
	.word	0
	.word	0
	.word	0
	.type	_tgamma_sin_table,@object
	.size	_tgamma_sin_table,160
	.align 2
_tgamma_A_table:
	.word	44002
	.word	44608
	.word	34249
	.word	61198
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	28120
	.word	46089
	.word	18909
	.word	51744
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	6796
	.word	56365
	.word	948
	.word	39330
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	21872
	.word	3113
	.word	40348
	.word	48989
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	15275
	.word	60291
	.word	893
	.word	59613
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	41018
	.word	41834
	.word	14754
	.word	46766
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	10107
	.word	10320
	.word	24796
	.word	32841
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	40832
	.word	16832
	.word	38712
	.word	55795
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	8761
	.word	28554
	.word	35431
	.word	39961
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	12838
	.word	702
	.word	32174
	.word	51595
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	442
	.word	50835
	.word	62234
	.word	59850
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	53618
	.word	2208
	.word	54662
	.word	64445
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	33537
	.word	7687
	.word	12653
	.word	64976
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	5604
	.word	18060
	.word	46944
	.word	34352
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	5422
	.word	18393
	.word	57870
	.word	37869
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	27417
	.word	51069
	.word	14904
	.word	43119
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	16515
	.word	36952
	.word	46206
	.word	50255
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	22913
	.word	57796
	.word	61047
	.word	59491
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	55054
	.word	14521
	.word	23522
	.word	35521
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	3828
	.word	37429
	.word	19301
	.word	42457
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	18611
	.word	8353
	.word	56126
	.word	50041
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	63177
	.word	14708
	.word	18871
	.word	56010
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	51349
	.word	6910
	.word	18794
	.word	33038
	.word	16353
	.word	0
	.word	0
	.word	0
	.word	59411
	.word	13918
	.word	30079
	.word	63615
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	40356
	.word	37493
	.word	20410
	.word	60584
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	59667
	.word	1960
	.word	42334
	.word	54496
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	24443
	.word	19594
	.word	17897
	.word	45291
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	24957
	.word	63432
	.word	54964
	.word	32848
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	7770
	.word	43625
	.word	45329
	.word	33905
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	34249
	.word	63845
	.word	17962
	.word	44463
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	49963
	.word	34728
	.word	51109
	.word	54055
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	57585
	.word	19701
	.word	6443
	.word	57077
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	61186
	.word	8282
	.word	25787
	.word	47837
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	26720
	.word	42621
	.word	41546
	.word	37680
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	18366
	.word	28044
	.word	60147
	.word	62846
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	6968
	.word	10789
	.word	37601
	.word	32880
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	60700
	.word	57063
	.word	36736
	.word	35909
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	61862
	.word	56439
	.word	50993
	.word	40752
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	41843
	.word	39363
	.word	59360
	.word	47812
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	63511
	.word	43763
	.word	855
	.word	57724
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	64510
	.word	20731
	.word	32839
	.word	35752
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	60398
	.word	46667
	.word	43836
	.word	45454
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	49807
	.word	23281
	.word	46849
	.word	59658
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	37270
	.word	40485
	.word	38862
	.word	41131
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	55072
	.word	194
	.word	57504
	.word	62944
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	6484
	.word	60537
	.word	3975
	.word	55679
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	11759
	.word	24340
	.word	39489
	.word	49925
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	22650
	.word	18020
	.word	56002
	.word	47553
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	65458
	.word	2450
	.word	7569
	.word	42768
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	2740
	.word	58538
	.word	8888
	.word	35477
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	24012
	.word	19369
	.word	10014
	.word	51050
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	32754
	.word	14555
	.word	56874
	.word	50646
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	6322
	.word	39288
	.word	8326
	.word	56228
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	51606
	.word	9409
	.word	56084
	.word	47341
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	19293
	.word	17746
	.word	29435
	.word	49011
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	22538
	.word	37169
	.word	56949
	.word	41021
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	65507
	.word	8897
	.word	64576
	.word	64934
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	21309
	.word	38834
	.word	44023
	.word	38745
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	52933
	.word	53016
	.word	24774
	.word	40865
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	4045
	.word	21262
	.word	61923
	.word	45295
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	26065
	.word	18177
	.word	3420
	.word	52438
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	45505
	.word	1968
	.word	16227
	.word	62949
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	1037
	.word	13974
	.word	10559
	.word	38924
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	39502
	.word	38026
	.word	65467
	.word	49371
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	34978
	.word	59813
	.word	17957
	.word	64181
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	4031
	.word	54789
	.word	33142
	.word	42976
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	2719
	.word	21031
	.word	60489
	.word	60330
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	20734
	.word	53765
	.word	40154
	.word	46919
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	63028
	.word	7231
	.word	17811
	.word	43725
	.word	16468
	.word	0
	.word	0
	.word	0
	.word	32606
	.word	48355
	.word	22399
	.word	43710
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	29042
	.word	46559
	.word	51950
	.word	41588
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	47396
	.word	18113
	.word	2918
	.word	37305
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	22233
	.word	40098
	.word	6225
	.word	61543
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	611
	.word	49379
	.word	57729
	.word	43678
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	10450
	.word	9675
	.word	56147
	.word	41083
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	37401
	.word	8271
	.word	47219
	.word	34504
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	34774
	.word	15943
	.word	11357
	.word	45081
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	6639
	.word	15692
	.word	42534
	.word	45536
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	51630
	.word	6378
	.word	12961
	.word	37818
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	59527
	.word	55742
	.word	41884
	.word	59714
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	15629
	.word	31501
	.word	45205
	.word	59448
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	53044
	.word	1871
	.word	3840
	.word	62990
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	49829
	.word	44544
	.word	39222
	.word	35206
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	8319
	.word	42513
	.word	20106
	.word	41214
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	56062
	.word	65309
	.word	8860
	.word	50097
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	39637
	.word	65
	.word	3840
	.word	62744
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	8819
	.word	49083
	.word	32007
	.word	40272
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	33763
	.word	5027
	.word	16250
	.word	52917
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	4751
	.word	2633
	.word	61342
	.word	35759
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	8844
	.word	54174
	.word	63762
	.word	50574
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	8334
	.word	8890
	.word	4484
	.word	39560
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	4811
	.word	30700
	.word	3620
	.word	48492
	.word	16556
	.word	0
	.word	0
	.word	0
	.word	24963
	.word	23772
	.word	56823
	.word	39936
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	33598
	.word	3946
	.word	44700
	.word	37958
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	46964
	.word	13072
	.word	50440
	.word	33963
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	60803
	.word	30641
	.word	14857
	.word	55738
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	54016
	.word	37905
	.word	6345
	.word	39067
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	52847
	.word	8984
	.word	26176
	.word	34926
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	36664
	.word	26865
	.word	35876
	.word	39170
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	52449
	.word	52544
	.word	53629
	.word	43890
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	12025
	.word	58412
	.word	44187
	.word	43481
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	31442
	.word	43543
	.word	55683
	.word	35857
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	3846
	.word	7199
	.word	40600
	.word	56439
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	59606
	.word	22885
	.word	19144
	.word	49661
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	40642
	.word	40248
	.word	11664
	.word	52797
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	62936
	.word	44294
	.word	39296
	.word	59383
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	2909
	.word	51304
	.word	40819
	.word	35037
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	28573
	.word	45780
	.word	52012
	.word	42966
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	18650
	.word	49015
	.word	44104
	.word	54288
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	55601
	.word	45035
	.word	39824
	.word	35129
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	34144
	.word	30699
	.word	57231
	.word	46486
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	46246
	.word	1151
	.word	17498
	.word	63193
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	4951
	.word	25890
	.word	61661
	.word	44882
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	6537
	.word	64648
	.word	46171
	.word	35209
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	3418
	.word	59105
	.word	44678
	.word	47100
	.word	16651
	.word	0
	.word	0
	.word	0
	.word	12496
	.word	39220
	.word	65358
	.word	37319
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	42985
	.word	15941
	.word	31416
	.word	35438
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	48809
	.word	58407
	.word	25474
	.word	63277
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	16086
	.word	46002
	.word	58617
	.word	51682
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	55157
	.word	44549
	.word	61270
	.word	35823
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	21856
	.word	27160
	.word	26283
	.word	61078
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	57421
	.word	61358
	.word	44892
	.word	42671
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	51328
	.word	61945
	.word	60158
	.word	43127
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	51733
	.word	1166
	.word	54323
	.word	42075
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	52270
	.word	46964
	.word	5686
	.word	34493
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	2083
	.word	1761
	.word	7136
	.word	54130
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	16904
	.word	37253
	.word	64186
	.word	43390
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	30519
	.word	46073
	.word	2795
	.word	46255
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	6923
	.word	13957
	.word	9241
	.word	52280
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	60036
	.word	4102
	.word	42534
	.word	62082
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	23471
	.word	7124
	.word	59867
	.word	38328
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	43652
	.word	58293
	.word	38720
	.word	48757
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	30217
	.word	2002
	.word	31515
	.word	63491
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	32079
	.word	61591
	.word	63175
	.word	42230
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	708
	.word	13247
	.word	38402
	.word	57649
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	48098
	.word	14626
	.word	62525
	.word	41069
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	41824
	.word	29269
	.word	27314
	.word	64550
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	61231
	.word	60847
	.word	45074
	.word	55796
	.word	16751
	.word	0
	.word	0
	.word	0
	.word	41222
	.word	6965
	.word	41751
	.word	35359
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	3873
	.word	17289
	.word	23432
	.word	33549
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	31267
	.word	5735
	.word	3862
	.word	59787
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	54508
	.word	24423
	.word	13439
	.word	48632
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	1373
	.word	13548
	.word	5598
	.word	33375
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	51134
	.word	47274
	.word	24966
	.word	54404
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	6577
	.word	45385
	.word	2362
	.word	45409
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	3828
	.word	42112
	.word	22766
	.word	42583
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	12956
	.word	14074
	.word	46709
	.word	41030
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	36968
	.word	52126
	.word	60443
	.word	33467
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	6923
	.word	15273
	.word	58034
	.word	52381
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	32660
	.word	35575
	.word	5652
	.word	38971
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	58845
	.word	37118
	.word	55534
	.word	41636
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	18460
	.word	14335
	.word	18170
	.word	47252
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	64257
	.word	33925
	.word	29284
	.word	56405
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	4762
	.word	47478
	.word	23933
	.word	35021
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	60070
	.word	43233
	.word	33997
	.word	44796
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	15498
	.word	56159
	.word	37136
	.word	58624
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	45618
	.word	14567
	.word	62599
	.word	39157
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	56427
	.word	8703
	.word	58958
	.word	53630
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	17868
	.word	58209
	.word	63399
	.word	38294
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	23727
	.word	3602
	.word	56293
	.word	60260
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	11566
	.word	31203
	.word	21261
	.word	41104
	.word	16856
	.word	0
	.word	0
	.word	0
	.word	50659
	.word	54826
	.word	2447
	.word	33815
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	40123
	.word	27511
	.word	38472
	.word	64120
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	60369
	.word	44894
	.word	35604
	.word	57033
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	46499
	.word	27632
	.word	5436
	.word	46222
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	30082
	.word	48277
	.word	45112
	.word	62871
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	22164
	.word	19009
	.word	61576
	.word	49094
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	46565
	.word	37565
	.word	23059
	.word	47623
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	32180
	.word	46156
	.word	40449
	.word	42167
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	43791
	.word	4543
	.word	64119
	.word	40210
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	35508
	.word	36368
	.word	50300
	.word	65316
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	24682
	.word	25954
	.word	24264
	.word	50994
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	2360
	.word	57697
	.word	56537
	.word	35655
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	35000
	.word	46863
	.word	45329
	.word	38168
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	34434
	.word	1782
	.word	6514
	.word	43468
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	42662
	.word	49367
	.word	10572
	.word	52120
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	20865
	.word	43286
	.word	18750
	.word	65033
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	9813
	.word	41323
	.word	59938
	.word	41786
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	37980
	.word	55255
	.word	47419
	.word	54914
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	3267
	.word	59107
	.word	21702
	.word	36808
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	56442
	.word	35583
	.word	19842
	.word	50549
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	14786
	.word	14705
	.word	8555
	.word	36161
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	5861
	.word	33921
	.word	55110
	.word	56952
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	12507
	.word	56326
	.word	38818
	.word	47266
	.word	16964
	.word	0
	.word	0
	.word	0
	.word	3472
	.word	16935
	.word	53300
	.word	65107
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	37860
	.word	60441
	.word	22458
	.word	61688
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	12243
	.word	56252
	.word	28016
	.word	54783
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	4212
	.word	39051
	.word	42484
	.word	44250
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	23380
	.word	35543
	.word	1695
	.word	59694
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	16948
	.word	23513
	.word	25700
	.word	44731
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	46140
	.word	40761
	.word	17956
	.word	49463
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	31263
	.word	46633
	.word	29941
	.word	41835
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	63205
	.word	61824
	.word	34317
	.word	39543
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	2572
	.word	33857
	.word	17789
	.word	63992
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	34199
	.word	18923
	.word	17606
	.word	49855
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	56334
	.word	35260
	.word	24180
	.word	33058
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	60689
	.word	52750
	.word	35337
	.word	35448
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	55452
	.word	53681
	.word	28550
	.word	40494
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	52405
	.word	7414
	.word	26290
	.word	48744
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	27310
	.word	49480
	.word	17006
	.word	61076
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	58002
	.word	7573
	.word	241
	.word	39403
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	30618
	.word	4587
	.word	23737
	.word	51968
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	41850
	.word	57331
	.word	43567
	.word	34937
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	44076
	.word	54284
	.word	21386
	.word	48090
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	47582
	.word	54033
	.word	55369
	.word	34454
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	6559
	.word	5249
	.word	7172
	.word	54302
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	25070
	.word	13550
	.word	42045
	.word	60321
	.word	17075
	.word	0
	.word	0
	.word	0
	.word	25751
	.word	29482
	.word	15881
	.word	62993
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	20424
	.word	12720
	.word	54361
	.word	59648
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	47709
	.word	29790
	.word	54671
	.word	52895
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	19590
	.word	27782
	.word	43363
	.word	42595
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	58921
	.word	35180
	.word	21269
	.word	57023
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30519
	.word	24253
	.word	59969
	.word	41055
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	16433
	.word	48540
	.word	61714
	.word	51024
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	28524
	.word	50460
	.word	14901
	.word	41561
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	3212
	.word	32692
	.word	57818
	.word	38984
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	39499
	.word	42357
	.word	61063
	.word	62879
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	23069
	.word	2532
	.word	8249
	.word	48896
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	10213
	.word	8193
	.word	57687
	.word	61912
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	54407
	.word	51976
	.word	21399
	.word	33245
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	30319
	.word	6795
	.word	56359
	.word	38081
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	56783
	.word	23918
	.word	44955
	.word	45999
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	48670
	.word	51841
	.word	19543
	.word	57851
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	40765
	.word	30890
	.word	33317
	.word	37455
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	26976
	.word	54195
	.word	64249
	.word	49555
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	53450
	.word	46734
	.word	58278
	.word	33402
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	16139
	.word	61138
	.word	10791
	.word	46069
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	19833
	.word	46025
	.word	1951
	.word	33050
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	63698
	.word	28854
	.word	16724
	.word	52116
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	51628
	.word	52030
	.word	9741
	.word	32928
	.word	17190
	.word	0
	.word	0
	.word	0
	.word	12285
	.word	10951
	.word	58642
	.word	61183
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	21683
	.word	21165
	.word	28859
	.word	57903
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	16106
	.word	65177
	.word	61473
	.word	51279
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	10066
	.word	60251
	.word	8712
	.word	41178
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	11765
	.word	63661
	.word	54012
	.word	54733
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	6745
	.word	52609
	.word	60525
	.word	37899
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	48001
	.word	64846
	.word	20212
	.word	52373
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	17748
	.word	43200
	.word	11817
	.word	41329
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	21018
	.word	45635
	.word	21983
	.word	38507
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	3148
	.word	52957
	.word	41657
	.word	61926
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	4827
	.word	784
	.word	31692
	.word	48072
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	34179
	.word	53889
	.word	29926
	.word	58425
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	50615
	.word	27492
	.word	10833
	.word	62832
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	38001
	.word	13025
	.word	51995
	.word	36075
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	57519
	.word	47991
	.word	3845
	.word	43713
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	52312
	.word	45573
	.word	61386
	.word	55158
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	13085
	.word	59531
	.word	16529
	.word	35826
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	20993
	.word	34790
	.word	57361
	.word	47533
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	9726
	.word	28278
	.word	20623
	.word	64228
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	29543
	.word	44397
	.word	26216
	.word	44369
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	63031
	.word	4436
	.word	65303
	.word	63733
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	3202
	.word	26777
	.word	12818
	.word	50273
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	59134
	.word	17584
	.word	37875
	.word	50102
	.word	17306
	.word	0
	.word	0
	.word	0
	.word	7773
	.word	6529
	.word	24897
	.word	59610
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	9176
	.word	34994
	.word	24066
	.word	56385
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	38670
	.word	17644
	.word	9278
	.word	49874
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	31722
	.word	46384
	.word	26212
	.word	39944
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	4438
	.word	35654
	.word	55200
	.word	52739
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	27356
	.word	43702
	.word	58298
	.word	35147
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	11597
	.word	6768
	.word	60887
	.word	53553
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	2200
	.word	56289
	.word	1997
	.word	41129
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	51208
	.word	51045
	.word	15577
	.word	38092
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	64525
	.word	30741
	.word	24355
	.word	61096
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	16273
	.word	9188
	.word	1200
	.word	47354
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	32505
	.word	64701
	.word	51161
	.word	55474
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	58255
	.word	43751
	.word	28307
	.word	59734
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	597
	.word	44015
	.word	59274
	.word	34374
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	17221
	.word	11824
	.word	60854
	.word	41770
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	32240
	.word	33006
	.word	58370
	.word	52867
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	22765
	.word	17058
	.word	17899
	.word	34437
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	3694
	.word	59578
	.word	1196
	.word	45807
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	37424
	.word	8934
	.word	53804
	.word	62023
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	6800
	.word	58264
	.word	43923
	.word	42913
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	27007
	.word	15916
	.word	20851
	.word	61705
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	33570
	.word	44306
	.word	22975
	.word	48691
	.word	16386
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
	.type	_tgamma_A_table,@object
	.size	_tgamma_A_table,4416
	.space 192, 0x00 	# pad
	.align 2
_tgamma_A176_inv:
	.word	38655
	.word	8470
	.word	22633
	.word	47709
	.word	15340
	.word	0
	.word	0
	.word	0
	.type	_tgamma_A176_inv,@object
	.size	_tgamma_A176_inv,16
	.align 2
_tgamma_A150_table:
	.word	61060
	.word	8155
	.word	61816
	.word	65535
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	47752
	.word	30749
	.word	4057
	.word	37827
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	24760
	.word	16820
	.word	52631
	.word	64803
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	36324
	.word	24890
	.word	11532
	.word	59368
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	63381
	.word	49585
	.word	20519
	.word	63827
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	54086
	.word	22318
	.word	4776
	.word	62483
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	24236
	.word	16506
	.word	21666
	.word	59746
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	44402
	.word	44414
	.word	37650
	.word	53079
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	34766
	.word	8695
	.word	24359
	.word	42592
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30542
	.word	64415
	.word	14059
	.word	59378
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	36037
	.word	62568
	.word	21045
	.word	34628
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	39650
	.word	28877
	.word	62299
	.word	64900
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	43413
	.word	4175
	.word	59877
	.word	46549
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	13603
	.word	64648
	.word	43425
	.word	47701
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	48047
	.word	22044
	.word	23868
	.word	61943
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	17548
	.word	29243
	.word	63955
	.word	38195
	.word	49138
	.word	0
	.word	0
	.word	0
	.type	_tgamma_A150_table,@object
	.size	_tgamma_A150_table,256
	.align 2
_tgamma_A125_table:
	.word	29804
	.word	14935
	.word	58389
	.word	58038
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	54039
	.word	49353
	.word	64455
	.word	62712
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	7246
	.word	48092
	.word	40889
	.word	56162
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	5238
	.word	25576
	.word	20384
	.word	34263
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	38009
	.word	2645
	.word	35759
	.word	42176
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	32410
	.word	34387
	.word	48471
	.word	48379
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	9060
	.word	33571
	.word	56968
	.word	36683
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	26873
	.word	9749
	.word	59107
	.word	49208
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	61297
	.word	63742
	.word	25286
	.word	34210
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	6011
	.word	50033
	.word	7349
	.word	46841
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	30597
	.word	45929
	.word	271
	.word	64276
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	60120
	.word	8046
	.word	54753
	.word	44175
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	32461
	.word	45691
	.word	56111
	.word	58214
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	38908
	.word	23877
	.word	33636
	.word	50495
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	35983
	.word	59115
	.word	59685
	.word	60013
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	38519
	.word	97
	.word	2340
	.word	53304
	.word	49143
	.word	0
	.word	0
	.word	0
	.type	_tgamma_A125_table,@object
	.size	_tgamma_A125_table,256
	.align 2
_tgamma_A100_table:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	50486
	.word	32176
	.word	26595
	.word	37828
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	33232
	.word	55464
	.word	50705
	.word	64818
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	56000
	.word	37024
	.word	35961
	.word	59472
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	6308
	.word	2395
	.word	34858
	.word	64338
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	22779
	.word	60710
	.word	1868
	.word	64356
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	33054
	.word	60854
	.word	741
	.word	65087
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	46387
	.word	11445
	.word	51950
	.word	65273
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	6735
	.word	17127
	.word	21437
	.word	65409
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	40596
	.word	22792
	.word	61241
	.word	65445
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	7521
	.word	26365
	.word	15230
	.word	65303
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	33891
	.word	37638
	.word	54039
	.word	64371
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	37411
	.word	53884
	.word	56799
	.word	60663
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30408
	.word	30767
	.word	55068
	.word	50398
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	49794
	.word	38369
	.word	16703
	.word	62901
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	52540
	.word	1662
	.word	21259
	.word	41989
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_tgamma_A100_table,@object
	.size	_tgamma_A100_table,256
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
