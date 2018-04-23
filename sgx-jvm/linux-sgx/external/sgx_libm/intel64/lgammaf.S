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
	.file "lgammaf.c"
	.text
..TXTST0:
# -- Begin  lgammaf
	.text
       .align    16,0x90
	.globl lgammaf
lgammaf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_lgammaf.1:
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
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        xorb      %r12b, %r12b
        movss     %xmm0, 112(%rsp)
        xorb      %bpl, %bpl
        movzwl    114(%rsp), %r15d
        andl      $32640, %r15d
        movd      %xmm0, %eax
        shrl      $7, %r15d
        andl      $8388607, %eax
        movl      %r15d, %r14d
        shll      $23, %r14d
        movb      115(%rsp), %r13b
        orl       %eax, %r14d
        andb      $-128, %r13b
..B1.2:
        fnstcw    110(%rsp)
..B1.3:
..___tag_value_lgammaf.16:
        call      fegetround@PLT
..___tag_value_lgammaf.17:
..B1.156:
        movl      %eax, %ebx
..B1.4:
        testl     %ebx, %ebx
        je        ..B1.6
..B1.5:
        xorl      %edi, %edi
        movb      $1, %r12b
..___tag_value_lgammaf.18:
        call      fesetround@PLT
..___tag_value_lgammaf.19:
..B1.6:
        movzwl    110(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.10
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 108(%rsp)
..B1.8:
        fldcw     108(%rsp)
..B1.9:
        movb      $1, %bpl
..B1.10:
        cmpl      $255, %r15d
        jne       ..B1.16
..B1.11:
        testb     %bpl, %bpl
        je        ..B1.13
..B1.12:
        fldcw     110(%rsp)
..B1.13:
        testb     %r12b, %r12b
        je        ..B1.15
..B1.14:
        movl      %ebx, %edi
..___tag_value_lgammaf.20:
        call      fesetround@PLT
..___tag_value_lgammaf.21:
..B1.15:
        movss     112(%rsp), %xmm0
        mulss     %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.16:
        testl     %r14d, %r14d
        je        ..B1.149
..B1.17:
        testb     %r13b, %r13b
        jne       ..B1.74
..B1.18:
        cmpl      $2084876174, %r14d
        jae       ..B1.136
..B1.19:
        movss     112(%rsp), %xmm0
        ucomiss   .L_2il0floatpacket.6(%rip), %xmm0
        jp        ..B1.20
        je        ..B1.131
..B1.20:
        cmpl      $1174405120, %r14d
        jb        ..B1.26
..B1.21:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm0, 56(%rsp)
        pxor      %xmm0, %xmm0
        flds      56(%rsp)
        cvtss2sd  56(%rsp), %xmm0
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fstps     32(%rsp)
        fldt      (%rax)
        fstpt     16(%rsp)
        call      log@PLT
..B1.157:
        fldt      (%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fmull     56(%rsp)
        fstpt     (%rsp)
        je        ..B1.23
..B1.22:
        movl      %ebx, %edi
..___tag_value_lgammaf.42:
        call      fesetround@PLT
..___tag_value_lgammaf.43:
..B1.23:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fsubs     32(%rsp)
        fstps     104(%rsp)
        je        ..B1.124
..B1.24:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.26:
        cmpl      $1082130432, %r14d
        jb        ..B1.33
..B1.27:
        addl      $-1082130432, %r14d
        lea       _A(%rip), %rax
        sarl      $23, %r14d
        lea       _B(%rip), %rdx
        movslq    %r14d, %r14
        lea       _C5(%rip), %r8
        movss     %xmm0, 56(%rsp)
        lea       (,%r14,8), %ecx
        flds      56(%rsp)
        lea       (%r14,%r14,2), %esi
        movslq    %ecx, %rcx
        lea       _C5_0(%rip), %r9
        movslq    %esi, %rsi
        testb     %r12b, %r12b
        fsubs     (%rax,%r14,4)
        fmuls     (%rdx,%r14,4)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8(%r8,%rcx,8)
        fmul      %st(1), %st
        fldl      (%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24(%r8,%rcx,8)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(2)
        faddl     16(%r8,%rcx,8)
        fmulp     %st, %st(1)
        faddl     16(%r9,%rsi,8)
        fstpt     (%rsp)
        je        ..B1.30
..B1.28:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.44:
        call      fesetround@PLT
..___tag_value_lgammaf.45:
..B1.29:
        fldt      80(%rsp)
..B1.30:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.31:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.33:
        ucomiss   .L_2il0floatpacket.4(%rip), %xmm0
        jp        ..B1.34
        je        ..B1.126
..B1.34:
        movss     %xmm0, 56(%rsp)
        cmpl      $1073741824, %r14d
        flds      56(%rsp)
        jbe       ..B1.40
..B1.35:
        fldt      .L_2il0floatpacket.5(%rip)
        fld       %st(1)
        lea       _C4(%rip), %r9
        lea       8+_C4(%rip), %rax
        fmul      %st(2), %st
        lea       32+_C4(%rip), %r10
        fldl      (%rax)
        lea       40+_C4(%rip), %rdx
        fmul      %st(1), %st
        lea       64+_C4(%rip), %r11
        fldl      (%r9)
        lea       72+_C4(%rip), %rcx
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        lea       48+_C4(%rip), %r13
        lea       56+_C4(%rip), %rsi
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        lea       16+_C4(%rip), %r14
        lea       24+_C4(%rip), %r8
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r13)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r8)
        lea       80+_C4(%rip), %r15
        testb     %r12b, %r12b
        fmul      %st(4), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        faddl     (%r14)
        fmulp     %st, %st(1)
        faddl     (%r15)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.37
..B1.36:
        movl      %ebx, %edi
..___tag_value_lgammaf.46:
        call      fesetround@PLT
..___tag_value_lgammaf.47:
..B1.37:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B1.124
..B1.38:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.40:
        cmpl      $1071644672, %r14d
        jb        ..B1.47
..B1.41:
        fld       %st(0)
        lea       16+_C3(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C3(%rip), %rdx
        fldl      (%rax)
        lea       32+_C3(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C3(%rip), %rsi
        lea       8+_C3(%rip), %r8
        lea       40+_C3(%rip), %r9
        lea       24+_C3(%rip), %r10
        lea       56+_C3(%rip), %r11
        lea       _C3(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B1.44
..B1.42:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.48:
        call      fesetround@PLT
..___tag_value_lgammaf.49:
..B1.43:
        fldt      80(%rsp)
..B1.44:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.45:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.47:
        cmpl      $1069547520, %r14d
        jb        ..B1.54
..B1.48:
        fld       %st(0)
        lea       16+_LGAMMAF_C2(%rip), %rax
        fmul      %st(1), %st
        lea       48+_LGAMMAF_C2(%rip), %rdx
        fldl      (%rax)
        lea       32+_LGAMMAF_C2(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_LGAMMAF_C2(%rip), %rsi
        lea       8+_LGAMMAF_C2(%rip), %r8
        lea       40+_LGAMMAF_C2(%rip), %r9
        lea       24+_LGAMMAF_C2(%rip), %r10
        lea       56+_LGAMMAF_C2(%rip), %r11
        lea       _LGAMMAF_C2(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B1.51
..B1.49:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.50:
        call      fesetround@PLT
..___tag_value_lgammaf.51:
..B1.50:
        fldt      80(%rsp)
..B1.51:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.52:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.54:
        cmpl      $1067450368, %r14d
        jb        ..B1.61
..B1.55:
        lea       _LM(%rip), %rax
        lea       16+_C1(%rip), %rdx
        lea       48+_C1(%rip), %rcx
        lea       32+_C1(%rip), %rsi
        lea       64+_C1(%rip), %r8
        lea       8+_C1(%rip), %r9
        lea       40+_C1(%rip), %r10
        lea       24+_C1(%rip), %r11
        fldl      (%rdx)
        lea       56+_C1(%rip), %r13
        lea       _C1(%rip), %r14
        testb     %r12b, %r12b
        fxch      %st(1)
        fsubl     (%rax)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmul      %st(2), %st
        faddl     (%r13)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r14)
        fstpt     (%rsp)
        je        ..B1.58
..B1.56:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.52:
        call      fesetround@PLT
..___tag_value_lgammaf.53:
..B1.57:
        fldt      80(%rsp)
..B1.58:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.59:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.61:
        cmpl      $1065353216, %r14d
        jb        ..B1.68
..B1.62:
        fld       %st(0)
        lea       16+_C0(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C0(%rip), %rdx
        fldl      (%rax)
        lea       32+_C0(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C0(%rip), %rsi
        lea       8+_C0(%rip), %r8
        lea       40+_C0(%rip), %r9
        lea       24+_C0(%rip), %r10
        lea       56+_C0(%rip), %r11
        lea       _C0(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B1.65
..B1.63:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.54:
        call      fesetround@PLT
..___tag_value_lgammaf.55:
..B1.64:
        fldt      80(%rsp)
..B1.65:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.66:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.68:
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fadd      %st(1), %st
        fstpt     (%rsp)
        fstpt     32(%rsp)
..___tag_value_lgammaf.57:
        call      lgammaf_pos
..___tag_value_lgammaf.58:
..B1.159:
        fldt      32(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B1.158:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        je        ..B1.71
..B1.69:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.60:
        call      fesetround@PLT
..___tag_value_lgammaf.61:
..B1.70:
        fldt      80(%rsp)
..B1.71:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.72:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.74:
        movss     112(%rsp), %xmm0
        call      nearbyintf@PLT
..B1.160:
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        movss     112(%rsp), %xmm4
        ucomiss   %xmm1, %xmm4
        jp        ..B1.75
        je        ..B1.144
..B1.75:
        cmpl      $1073741824, %r14d
        jae       ..B1.86
..B1.76:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B1.141
        jp        ..B1.141
..B1.77:
        fldt      16(%rsp)
..B1.78:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B1.79
        jb        ..B1.78
..B1.79:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B1.80:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B1.162:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_lgammaf.63:
        call      lgammaf_pos
..___tag_value_lgammaf.64:
..B1.161:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B1.83
..B1.81:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.66:
        call      fesetround@PLT
..___tag_value_lgammaf.67:
..B1.82:
        fldt      80(%rsp)
..B1.83:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.84:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.86:
        cmpl      $1082130432, %r14d
        jae       ..B1.103
..B1.87:
        movss     .L_2il0floatpacket.2(%rip), %xmm0
        lea       _LRIB(%rip), %rdx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        subss     %xmm4, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        addss     %xmm2, %xmm1
        movss     %xmm1, 104(%rsp)
        pxor      %xmm1, %xmm1
        cvtss2sd  %xmm4, %xmm1
        movl      104(%rsp), %ecx
        andl      $1, %ecx
        movss     104(%rsp), %xmm3
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        lea       (%rcx,%rcx,2), %eax
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B1.90
..B1.88:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B1.90
..B1.89:
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _LRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B1.98
..B1.90:
        lea       _RRIB(%rip), %rdx
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B1.93
..B1.91:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B1.93
..B1.92:
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _RRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B1.98
..B1.93:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fcomip    %st(2), %st
        jae       ..B1.142
        jp        ..B1.142
..B1.94:
        fldt      (%rsp)
..B1.95:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B1.96
        jb        ..B1.95
..B1.96:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     80(%rsp)
..B1.97:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B1.164:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstl      16(%rsp)
        fstpt     80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      16(%rsp)
        fstpt     (%rsp)
..___tag_value_lgammaf.69:
        call      lgammaf_pos
..___tag_value_lgammaf.70:
..B1.163:
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fsubl     16(%rsp)
        fstpt     (%rsp)
..B1.98:
        testb     %r12b, %r12b
        je        ..B1.100
..B1.99:
        movl      %ebx, %edi
..___tag_value_lgammaf.72:
        call      fesetround@PLT
..___tag_value_lgammaf.73:
..B1.100:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B1.124
..B1.101:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.103:
        cmpl      $1091567616, %r14d
        jae       ..B1.114
..B1.104:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B1.143
        jp        ..B1.143
..B1.105:
        fldt      16(%rsp)
..B1.106:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B1.107
        jb        ..B1.106
..B1.107:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B1.108:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B1.166:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_lgammaf.75:
        call      lgammaf_pos
..___tag_value_lgammaf.76:
..B1.165:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B1.111
..B1.109:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf.78:
        call      fesetround@PLT
..___tag_value_lgammaf.79:
..B1.110:
        fldt      80(%rsp)
..B1.111:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.112:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.114:
        cmpl      $1174405120, %r14d
        jb        ..B1.120
..B1.115:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        fsts      (%rsp)
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%rsp)
        fchs      
        fstl      96(%rsp)
        movsd     96(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      (%rax)
        fstpt     24(%rsp)
        call      log@PLT
..B1.168:
        fldt      8(%rsp)
        lea       _S16(%rip), %rax
        fldt      24(%rsp)
        lea       _S14(%rip), %rdx
        fldt      40(%rsp)
        lea       _S12(%rip), %rcx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        lea       _S10(%rip), %rsi
        fldl      (%rax)
        lea       _S08(%rip), %r8
        movss     %xmm2, 104(%rsp)
        lea       _S06(%rip), %r9
        movss     104(%rsp), %xmm1
        lea       _S04(%rip), %r10
        movsd     %xmm0, 56(%rsp)
        lea       _S02(%rip), %r11
        fxch      %st(3)
        fmull     56(%rsp)
        subss     112(%rsp), %xmm1
        fsubp     %st, %st(2)
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm3
        fxch      %st(1)
        fsubs     (%rsp)
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        movss     104(%rsp), %xmm4
        movss     %xmm4, 56(%rsp)
        fxch      %st(1)
        fsubs     56(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fmul      %st, %st(2)
        andb      $127, 73(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        fxch      %st(2)
        faddl     (%rdx)
        fmul      %st(2), %st
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        call      log@PLT
..B1.167:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        je        ..B1.117
..B1.116:
        movl      %ebx, %edi
        fstpl     (%rsp)
..___tag_value_lgammaf.80:
        call      fesetround@PLT
..___tag_value_lgammaf.81:
..B1.172:
        fldl      (%rsp)
..B1.117:
        fldt      8(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.118:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.120:
        flds      112(%rsp)
        movss     .L_2il0floatpacket.3(%rip), %xmm1
        fchs      
        movaps    %xmm1, %xmm0
        fstl      96(%rsp)
        subss     %xmm4, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm2
        movsd     96(%rsp), %xmm0
        subss     %xmm1, %xmm2
        movss     %xmm2, 104(%rsp)
        movss     104(%rsp), %xmm3
        movss     %xmm3, 56(%rsp)
        fstpt     40(%rsp)
        fldt      40(%rsp)
        flds      56(%rsp)
        fsubrp    %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fstpt     24(%rsp)
        call      log@PLT
..B1.171:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fchs      
        fldt      56(%rsp)
        fstpt     (%rsp)
        fstpt     24(%rsp)
..___tag_value_lgammaf.83:
        call      lgammaf_pos
..___tag_value_lgammaf.84:
..B1.170:
        fldt      24(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fldt      24(%rsp)
        lea       _S14(%rip), %rax
        fld       %st(0)
        lea       _S16(%rip), %r8
        fmul      %st(1), %st
        lea       _S10(%rip), %rdx
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldl      (%rax)
        lea       _S12(%rip), %r9
        fmul      %st(3), %st
        lea       _S06(%rip), %rcx
        fldl      (%r8)
        lea       _S08(%rip), %r10
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(4), %st
        lea       _S02(%rip), %rsi
        fxch      %st(1)
        faddl     (%rsi)
        lea       _S04(%rip), %r11
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(3)
        andb      $127, 73(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B1.169:
        fldt      8(%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        je        ..B1.122
..B1.121:
        movl      %ebx, %edi
        fstpl     (%rsp)
        fstpt     8(%rsp)
..___tag_value_lgammaf.86:
        call      fesetround@PLT
..___tag_value_lgammaf.87:
..B1.173:
        fldt      8(%rsp)
        fldl      (%rsp)
..B1.122:
        testb     %bpl, %bpl
        fsubrp    %st, %st(1)
        fstps     104(%rsp)
        je        ..B1.124
..B1.123:
        fldcw     110(%rsp)
..B1.124:
        movss     104(%rsp), %xmm0
..B1.125:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.126:
        testb     %bpl, %bpl
        je        ..B1.128
..B1.127:
        fldcw     110(%rsp)
..B1.128:
        testb     %r12b, %r12b
        je        ..B1.130
..B1.129:
        movl      %ebx, %edi
..___tag_value_lgammaf.108:
        call      fesetround@PLT
..___tag_value_lgammaf.109:
..B1.130:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.131:
        testb     %bpl, %bpl
        je        ..B1.133
..B1.132:
        fldcw     110(%rsp)
..B1.133:
        testb     %r12b, %r12b
        je        ..B1.135
..B1.134:
        movl      %ebx, %edi
..___tag_value_lgammaf.130:
        call      fesetround@PLT
..___tag_value_lgammaf.131:
..B1.135:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.136:
        testb     %r12b, %r12b
        je        ..B1.138
..B1.137:
        movl      %ebx, %edi
..___tag_value_lgammaf.152:
        call      fesetround@PLT
..___tag_value_lgammaf.153:
..B1.138:
        lea       _PBIG(%rip), %rax
        testb     %bpl, %bpl
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 104(%rsp)
        je        ..B1.124
..B1.139:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.141:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B1.80
..B1.142:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B1.97
..B1.143:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B1.108
..B1.144:
        testb     %r12b, %r12b
        je        ..B1.146
..B1.145:
        movl      %ebx, %edi
..___tag_value_lgammaf.154:
        call      fesetround@PLT
..___tag_value_lgammaf.155:
..B1.146:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B1.124
..B1.147:
        fldcw     110(%rsp)
        jmp       ..B1.124
..B1.149:
        testb     %r12b, %r12b
        je        ..B1.151
..B1.150:
        movl      %ebx, %edi
..___tag_value_lgammaf.156:
        call      fesetround@PLT
..___tag_value_lgammaf.157:
..B1.151:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B1.124
..B1.152:
        fldcw     110(%rsp)
        jmp       ..B1.124
        .align    16,0x90
	.cfi_endproc
	.type	lgammaf,@function
	.size	lgammaf,.-lgammaf
	.data
# -- End  lgammaf
	.text
# -- Begin  lgammaf_pos
	.text
       .align    16,0x90
lgammaf_pos:
# parameter 1: 8 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value_lgammaf_pos.159:
..L160:

        fldt      8(%rsp)
        fstl      -8(%rsp)
        fldt      .L_2il0floatpacket.7(%rip)
        movzwl    -2(%rsp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        movl      -4(%rsp), %eax
        shll      $20, %ecx
        andl      $1048575, %eax
        orl       %eax, %ecx
        cmpl      $1074790400, %ecx
        jb        ..B2.3
..B2.2:
        fstp      %st(0)
        addl      $-1074790400, %ecx
        lea       _A(%rip), %rax
        sarl      $20, %ecx
        lea       _B(%rip), %rdx
        movslq    %ecx, %rcx
        lea       _C5(%rip), %r9
        lea       _C5_0(%rip), %r8
        lea       (,%rcx,8), %esi
        movslq    %esi, %rsi
        lea       (%rcx,%rcx,2), %edi
        movslq    %edi, %rdi
        flds      (%rax,%rcx,4)
        fsubrp    %st, %st(1)
        fmuls     (%rdx,%rcx,4)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      (%r9,%rsi,8)
        fmul      %st(1), %st
        fldl      8(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r8,%rdi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8(%r8,%rdi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16(%r9,%rsi,8)
        fmulp     %st, %st(2)
        faddl     24(%r9,%rsi,8)
        fmulp     %st, %st(2)
        faddl     16(%r8,%rdi,8)
        faddp     %st, %st(1)
        ret       
..B2.3:
        cmpl      $1073741824, %ecx
        jne       ..B2.5
..B2.4:
        fstp      %st(1)
        ret       
..B2.5:
        jbe       ..B2.7
..B2.6:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.5(%rip)
        fld       %st(1)
        lea       _C4(%rip), %rax
        lea       32+_C4(%rip), %rdx
        fmul      %st(2), %st
        lea       8+_C4(%rip), %r9
        fldl      (%rax)
        lea       40+_C4(%rip), %r10
        lea       64+_C4(%rip), %rcx
        lea       72+_C4(%rip), %r11
        lea       48+_C4(%rip), %rsi
        lea       56+_C4(%rip), %rax
        lea       16+_C4(%rip), %rdi
        lea       80+_C4(%rip), %r8
        fmul      %st(1), %st
        fldl      (%r9)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdi)
        lea       24+_C4(%rip), %rdx
        fmulp     %st, %st(2)
        faddl     (%rdx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     (%r8)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        ret       
..B2.7:
        cmpl      $1073479680, %ecx
        jb        ..B2.9
..B2.8:
        fstp      %st(0)
        fld       %st(0)
        lea       8+_C3(%rip), %rax
        lea       16+_C3(%rip), %r8
        lea       40+_C3(%rip), %rdx
        lea       48+_C3(%rip), %r9
        lea       24+_C3(%rip), %rcx
        lea       32+_C3(%rip), %r10
        lea       56+_C3(%rip), %rsi
        lea       64+_C3(%rip), %r11
        lea       _C3(%rip), %rdi
        fmul      %st(1), %st
        fldl      (%rax)
        fmul      %st(1), %st
        fldl      (%r8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(2)
        faddl     (%rdi)
        faddp     %st, %st(1)
        ret       
..B2.9:
        cmpl      $1073217536, %ecx
        jb        ..B2.11
..B2.10:
        fstp      %st(0)
        fld       %st(0)
        lea       8+_LGAMMAF_C2(%rip), %rax
        lea       16+_LGAMMAF_C2(%rip), %r8
        lea       40+_LGAMMAF_C2(%rip), %rdx
        lea       48+_LGAMMAF_C2(%rip), %r9
        lea       24+_LGAMMAF_C2(%rip), %rcx
        lea       32+_LGAMMAF_C2(%rip), %r10
        lea       56+_LGAMMAF_C2(%rip), %rsi
        lea       64+_LGAMMAF_C2(%rip), %r11
        lea       _LGAMMAF_C2(%rip), %rdi
        fmul      %st(1), %st
        fldl      (%rax)
        fmul      %st(1), %st
        fldl      (%r8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(2)
        faddl     (%rdi)
        faddp     %st, %st(1)
        ret       
..B2.11:
        cmpl      $1072955392, %ecx
        jb        ..B2.13
..B2.12:
        fstp      %st(0)
        lea       _LM(%rip), %rax
        lea       8+_C1(%rip), %rdx
        lea       16+_C1(%rip), %r9
        lea       40+_C1(%rip), %rcx
        lea       48+_C1(%rip), %r10
        lea       24+_C1(%rip), %rsi
        lea       32+_C1(%rip), %r11
        lea       56+_C1(%rip), %rdi
        fldl      (%rax)
        lea       64+_C1(%rip), %rax
        lea       _C1(%rip), %r8
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      (%rdx)
        fmul      %st(1), %st
        fldl      (%r9)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdi)
        fmulp     %st, %st(2)
        faddl     (%rax)
        fmulp     %st, %st(2)
        faddl     (%r8)
        faddp     %st, %st(1)
        ret       
..B2.13:
        cmpl      $1072693248, %ecx
        jb        ..B2.4
..B2.14:
        fstp      %st(0)
        fld       %st(0)
        lea       8+_C0(%rip), %rax
        lea       16+_C0(%rip), %r8
        lea       40+_C0(%rip), %rdx
        lea       48+_C0(%rip), %r9
        lea       24+_C0(%rip), %rcx
        lea       32+_C0(%rip), %r10
        lea       56+_C0(%rip), %rsi
        lea       64+_C0(%rip), %r11
        lea       _C0(%rip), %rdi
        fmul      %st(1), %st
        fldl      (%rax)
        fmul      %st(1), %st
        fldl      (%r8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(2)
        faddl     (%rdi)
        faddp     %st, %st(1)
..B2.15:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	lgammaf_pos,@function
	.size	lgammaf_pos,.-lgammaf_pos
	.data
# -- End  lgammaf_pos
	.text
# -- Begin  gammaf
	.text
       .align    16,0x90
	.globl gammaf
gammaf:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value_gammaf.162:
..L163:

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
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        xorb      %r12b, %r12b
        movss     %xmm0, 112(%rsp)
        xorb      %bpl, %bpl
        movzwl    114(%rsp), %r15d
        andl      $32640, %r15d
        movd      %xmm0, %eax
        shrl      $7, %r15d
        andl      $8388607, %eax
        movl      %r15d, %r14d
        shll      $23, %r14d
        movb      115(%rsp), %r13b
        orl       %eax, %r14d
        andb      $-128, %r13b
..B3.2:
        fnstcw    110(%rsp)
..B3.3:
..___tag_value_gammaf.177:
        call      fegetround@PLT
..___tag_value_gammaf.178:
..B3.156:
        movl      %eax, %ebx
..B3.4:
        testl     %ebx, %ebx
        je        ..B3.6
..B3.5:
        xorl      %edi, %edi
        movb      $1, %r12b
..___tag_value_gammaf.179:
        call      fesetround@PLT
..___tag_value_gammaf.180:
..B3.6:
        movzwl    110(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B3.10
..B3.7:
        orl       $-64768, %edx
        movw      %dx, 108(%rsp)
..B3.8:
        fldcw     108(%rsp)
..B3.9:
        movb      $1, %bpl
..B3.10:
        cmpl      $255, %r15d
        jne       ..B3.16
..B3.11:
        testb     %bpl, %bpl
        je        ..B3.13
..B3.12:
        fldcw     110(%rsp)
..B3.13:
        testb     %r12b, %r12b
        je        ..B3.15
..B3.14:
        movl      %ebx, %edi
..___tag_value_gammaf.181:
        call      fesetround@PLT
..___tag_value_gammaf.182:
..B3.15:
        movss     112(%rsp), %xmm0
        mulss     %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.16:
        testl     %r14d, %r14d
        je        ..B3.149
..B3.17:
        testb     %r13b, %r13b
        jne       ..B3.74
..B3.18:
        cmpl      $2084876174, %r14d
        jae       ..B3.136
..B3.19:
        movss     112(%rsp), %xmm0
        ucomiss   .L_2il0floatpacket.6(%rip), %xmm0
        jp        ..B3.20
        je        ..B3.131
..B3.20:
        cmpl      $1174405120, %r14d
        jb        ..B3.26
..B3.21:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm0, 56(%rsp)
        pxor      %xmm0, %xmm0
        flds      56(%rsp)
        cvtss2sd  56(%rsp), %xmm0
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fstps     32(%rsp)
        fldt      (%rax)
        fstpt     16(%rsp)
        call      log@PLT
..B3.157:
        fldt      (%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fmull     56(%rsp)
        fstpt     (%rsp)
        je        ..B3.23
..B3.22:
        movl      %ebx, %edi
..___tag_value_gammaf.203:
        call      fesetround@PLT
..___tag_value_gammaf.204:
..B3.23:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fsubs     32(%rsp)
        fstps     104(%rsp)
        je        ..B3.124
..B3.24:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.26:
        cmpl      $1082130432, %r14d
        jb        ..B3.33
..B3.27:
        addl      $-1082130432, %r14d
        lea       _A(%rip), %rax
        sarl      $23, %r14d
        lea       _B(%rip), %rdx
        movslq    %r14d, %r14
        lea       _C5(%rip), %r8
        movss     %xmm0, 56(%rsp)
        lea       (,%r14,8), %ecx
        flds      56(%rsp)
        lea       (%r14,%r14,2), %esi
        movslq    %ecx, %rcx
        lea       _C5_0(%rip), %r9
        movslq    %esi, %rsi
        testb     %r12b, %r12b
        fsubs     (%rax,%r14,4)
        fmuls     (%rdx,%r14,4)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8(%r8,%rcx,8)
        fmul      %st(1), %st
        fldl      (%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24(%r8,%rcx,8)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(2)
        faddl     16(%r8,%rcx,8)
        fmulp     %st, %st(1)
        faddl     16(%r9,%rsi,8)
        fstpt     (%rsp)
        je        ..B3.30
..B3.28:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.205:
        call      fesetround@PLT
..___tag_value_gammaf.206:
..B3.29:
        fldt      80(%rsp)
..B3.30:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.31:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.33:
        ucomiss   .L_2il0floatpacket.4(%rip), %xmm0
        jp        ..B3.34
        je        ..B3.126
..B3.34:
        movss     %xmm0, 56(%rsp)
        cmpl      $1073741824, %r14d
        flds      56(%rsp)
        jbe       ..B3.40
..B3.35:
        fldt      .L_2il0floatpacket.5(%rip)
        fld       %st(1)
        lea       _C4(%rip), %r9
        lea       8+_C4(%rip), %rax
        fmul      %st(2), %st
        lea       32+_C4(%rip), %r10
        fldl      (%rax)
        lea       40+_C4(%rip), %rdx
        fmul      %st(1), %st
        lea       64+_C4(%rip), %r11
        fldl      (%r9)
        lea       72+_C4(%rip), %rcx
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        lea       48+_C4(%rip), %r13
        lea       56+_C4(%rip), %rsi
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        lea       16+_C4(%rip), %r14
        lea       24+_C4(%rip), %r8
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r13)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r8)
        lea       80+_C4(%rip), %r15
        testb     %r12b, %r12b
        fmul      %st(4), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        faddl     (%r14)
        fmulp     %st, %st(1)
        faddl     (%r15)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B3.37
..B3.36:
        movl      %ebx, %edi
..___tag_value_gammaf.207:
        call      fesetround@PLT
..___tag_value_gammaf.208:
..B3.37:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B3.124
..B3.38:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.40:
        cmpl      $1071644672, %r14d
        jb        ..B3.47
..B3.41:
        fld       %st(0)
        lea       16+_C3(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C3(%rip), %rdx
        fldl      (%rax)
        lea       32+_C3(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C3(%rip), %rsi
        lea       8+_C3(%rip), %r8
        lea       40+_C3(%rip), %r9
        lea       24+_C3(%rip), %r10
        lea       56+_C3(%rip), %r11
        lea       _C3(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B3.44
..B3.42:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.209:
        call      fesetround@PLT
..___tag_value_gammaf.210:
..B3.43:
        fldt      80(%rsp)
..B3.44:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.45:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.47:
        cmpl      $1069547520, %r14d
        jb        ..B3.54
..B3.48:
        fld       %st(0)
        lea       16+_LGAMMAF_C2(%rip), %rax
        fmul      %st(1), %st
        lea       48+_LGAMMAF_C2(%rip), %rdx
        fldl      (%rax)
        lea       32+_LGAMMAF_C2(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_LGAMMAF_C2(%rip), %rsi
        lea       8+_LGAMMAF_C2(%rip), %r8
        lea       40+_LGAMMAF_C2(%rip), %r9
        lea       24+_LGAMMAF_C2(%rip), %r10
        lea       56+_LGAMMAF_C2(%rip), %r11
        lea       _LGAMMAF_C2(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B3.51
..B3.49:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.211:
        call      fesetround@PLT
..___tag_value_gammaf.212:
..B3.50:
        fldt      80(%rsp)
..B3.51:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.52:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.54:
        cmpl      $1067450368, %r14d
        jb        ..B3.61
..B3.55:
        lea       _LM(%rip), %rax
        lea       16+_C1(%rip), %rdx
        lea       48+_C1(%rip), %rcx
        lea       32+_C1(%rip), %rsi
        lea       64+_C1(%rip), %r8
        lea       8+_C1(%rip), %r9
        lea       40+_C1(%rip), %r10
        lea       24+_C1(%rip), %r11
        fldl      (%rdx)
        lea       56+_C1(%rip), %r13
        lea       _C1(%rip), %r14
        testb     %r12b, %r12b
        fxch      %st(1)
        fsubl     (%rax)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmul      %st(2), %st
        faddl     (%r13)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r14)
        fstpt     (%rsp)
        je        ..B3.58
..B3.56:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.213:
        call      fesetround@PLT
..___tag_value_gammaf.214:
..B3.57:
        fldt      80(%rsp)
..B3.58:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.59:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.61:
        cmpl      $1065353216, %r14d
        jb        ..B3.68
..B3.62:
        fld       %st(0)
        lea       16+_C0(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C0(%rip), %rdx
        fldl      (%rax)
        lea       32+_C0(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C0(%rip), %rsi
        lea       8+_C0(%rip), %r8
        lea       40+_C0(%rip), %r9
        lea       24+_C0(%rip), %r10
        lea       56+_C0(%rip), %r11
        lea       _C0(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B3.65
..B3.63:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.215:
        call      fesetround@PLT
..___tag_value_gammaf.216:
..B3.64:
        fldt      80(%rsp)
..B3.65:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.66:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.68:
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fadd      %st(1), %st
        fstpt     (%rsp)
        fstpt     32(%rsp)
..___tag_value_gammaf.218:
        call      lgammaf_pos
..___tag_value_gammaf.219:
..B3.159:
        fldt      32(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B3.158:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        je        ..B3.71
..B3.69:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.221:
        call      fesetround@PLT
..___tag_value_gammaf.222:
..B3.70:
        fldt      80(%rsp)
..B3.71:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.72:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.74:
        movss     112(%rsp), %xmm0
        call      nearbyintf@PLT
..B3.160:
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        movss     112(%rsp), %xmm4
        ucomiss   %xmm1, %xmm4
        jp        ..B3.75
        je        ..B3.144
..B3.75:
        cmpl      $1073741824, %r14d
        jae       ..B3.86
..B3.76:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B3.141
        jp        ..B3.141
..B3.77:
        fldt      16(%rsp)
..B3.78:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B3.79
        jb        ..B3.78
..B3.79:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B3.80:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B3.162:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_gammaf.224:
        call      lgammaf_pos
..___tag_value_gammaf.225:
..B3.161:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B3.83
..B3.81:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.227:
        call      fesetround@PLT
..___tag_value_gammaf.228:
..B3.82:
        fldt      80(%rsp)
..B3.83:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.84:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.86:
        cmpl      $1082130432, %r14d
        jae       ..B3.103
..B3.87:
        movss     .L_2il0floatpacket.2(%rip), %xmm0
        lea       _LRIB(%rip), %rdx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        subss     %xmm4, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        addss     %xmm2, %xmm1
        movss     %xmm1, 104(%rsp)
        pxor      %xmm1, %xmm1
        cvtss2sd  %xmm4, %xmm1
        movl      104(%rsp), %ecx
        andl      $1, %ecx
        movss     104(%rsp), %xmm3
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        lea       (%rcx,%rcx,2), %eax
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B3.90
..B3.88:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B3.90
..B3.89:
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _LRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B3.98
..B3.90:
        lea       _RRIB(%rip), %rdx
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B3.93
..B3.91:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B3.93
..B3.92:
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _RRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B3.98
..B3.93:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fcomip    %st(2), %st
        jae       ..B3.142
        jp        ..B3.142
..B3.94:
        fldt      (%rsp)
..B3.95:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B3.96
        jb        ..B3.95
..B3.96:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     80(%rsp)
..B3.97:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B3.164:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstl      16(%rsp)
        fstpt     80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      16(%rsp)
        fstpt     (%rsp)
..___tag_value_gammaf.230:
        call      lgammaf_pos
..___tag_value_gammaf.231:
..B3.163:
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fsubl     16(%rsp)
        fstpt     (%rsp)
..B3.98:
        testb     %r12b, %r12b
        je        ..B3.100
..B3.99:
        movl      %ebx, %edi
..___tag_value_gammaf.233:
        call      fesetround@PLT
..___tag_value_gammaf.234:
..B3.100:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B3.124
..B3.101:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.103:
        cmpl      $1091567616, %r14d
        jae       ..B3.114
..B3.104:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm4, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B3.143
        jp        ..B3.143
..B3.105:
        fldt      16(%rsp)
..B3.106:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B3.107
        jb        ..B3.106
..B3.107:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B3.108:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B3.166:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_gammaf.236:
        call      lgammaf_pos
..___tag_value_gammaf.237:
..B3.165:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B3.111
..B3.109:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf.239:
        call      fesetround@PLT
..___tag_value_gammaf.240:
..B3.110:
        fldt      80(%rsp)
..B3.111:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.112:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.114:
        cmpl      $1174405120, %r14d
        jb        ..B3.120
..B3.115:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm4, 56(%rsp)
        flds      56(%rsp)
        fsts      (%rsp)
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%rsp)
        fchs      
        fstl      96(%rsp)
        movsd     96(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      (%rax)
        fstpt     24(%rsp)
        call      log@PLT
..B3.168:
        fldt      8(%rsp)
        lea       _S16(%rip), %rax
        fldt      24(%rsp)
        lea       _S14(%rip), %rdx
        fldt      40(%rsp)
        lea       _S12(%rip), %rcx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        lea       _S10(%rip), %rsi
        fldl      (%rax)
        lea       _S08(%rip), %r8
        movss     %xmm2, 104(%rsp)
        lea       _S06(%rip), %r9
        movss     104(%rsp), %xmm1
        lea       _S04(%rip), %r10
        movsd     %xmm0, 56(%rsp)
        lea       _S02(%rip), %r11
        fxch      %st(3)
        fmull     56(%rsp)
        subss     112(%rsp), %xmm1
        fsubp     %st, %st(2)
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm3
        fxch      %st(1)
        fsubs     (%rsp)
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        movss     104(%rsp), %xmm4
        movss     %xmm4, 56(%rsp)
        fxch      %st(1)
        fsubs     56(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fmul      %st, %st(2)
        andb      $127, 73(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        fxch      %st(2)
        faddl     (%rdx)
        fmul      %st(2), %st
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        call      log@PLT
..B3.167:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        je        ..B3.117
..B3.116:
        movl      %ebx, %edi
        fstpl     (%rsp)
..___tag_value_gammaf.241:
        call      fesetround@PLT
..___tag_value_gammaf.242:
..B3.172:
        fldl      (%rsp)
..B3.117:
        fldt      8(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.118:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.120:
        flds      112(%rsp)
        movss     .L_2il0floatpacket.3(%rip), %xmm1
        fchs      
        movaps    %xmm1, %xmm0
        fstl      96(%rsp)
        subss     %xmm4, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm2
        movsd     96(%rsp), %xmm0
        subss     %xmm1, %xmm2
        movss     %xmm2, 104(%rsp)
        movss     104(%rsp), %xmm3
        movss     %xmm3, 56(%rsp)
        fstpt     40(%rsp)
        fldt      40(%rsp)
        flds      56(%rsp)
        fsubrp    %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fstpt     24(%rsp)
        call      log@PLT
..B3.171:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fchs      
        fldt      56(%rsp)
        fstpt     (%rsp)
        fstpt     24(%rsp)
..___tag_value_gammaf.244:
        call      lgammaf_pos
..___tag_value_gammaf.245:
..B3.170:
        fldt      24(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fldt      24(%rsp)
        lea       _S14(%rip), %rax
        fld       %st(0)
        lea       _S16(%rip), %r8
        fmul      %st(1), %st
        lea       _S10(%rip), %rdx
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldl      (%rax)
        lea       _S12(%rip), %r9
        fmul      %st(3), %st
        lea       _S06(%rip), %rcx
        fldl      (%r8)
        lea       _S08(%rip), %r10
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(4), %st
        lea       _S02(%rip), %rsi
        fxch      %st(1)
        faddl     (%rsi)
        lea       _S04(%rip), %r11
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(3)
        andb      $127, 73(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B3.169:
        fldt      8(%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        je        ..B3.122
..B3.121:
        movl      %ebx, %edi
        fstpl     (%rsp)
        fstpt     8(%rsp)
..___tag_value_gammaf.247:
        call      fesetround@PLT
..___tag_value_gammaf.248:
..B3.173:
        fldt      8(%rsp)
        fldl      (%rsp)
..B3.122:
        testb     %bpl, %bpl
        fsubrp    %st, %st(1)
        fstps     104(%rsp)
        je        ..B3.124
..B3.123:
        fldcw     110(%rsp)
..B3.124:
        movss     104(%rsp), %xmm0
..B3.125:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.126:
        testb     %bpl, %bpl
        je        ..B3.128
..B3.127:
        fldcw     110(%rsp)
..B3.128:
        testb     %r12b, %r12b
        je        ..B3.130
..B3.129:
        movl      %ebx, %edi
..___tag_value_gammaf.269:
        call      fesetround@PLT
..___tag_value_gammaf.270:
..B3.130:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.131:
        testb     %bpl, %bpl
        je        ..B3.133
..B3.132:
        fldcw     110(%rsp)
..B3.133:
        testb     %r12b, %r12b
        je        ..B3.135
..B3.134:
        movl      %ebx, %edi
..___tag_value_gammaf.291:
        call      fesetround@PLT
..___tag_value_gammaf.292:
..B3.135:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.136:
        testb     %r12b, %r12b
        je        ..B3.138
..B3.137:
        movl      %ebx, %edi
..___tag_value_gammaf.313:
        call      fesetround@PLT
..___tag_value_gammaf.314:
..B3.138:
        lea       _PBIG(%rip), %rax
        testb     %bpl, %bpl
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 104(%rsp)
        je        ..B3.124
..B3.139:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.141:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B3.80
..B3.142:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B3.97
..B3.143:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B3.108
..B3.144:
        testb     %r12b, %r12b
        je        ..B3.146
..B3.145:
        movl      %ebx, %edi
..___tag_value_gammaf.315:
        call      fesetround@PLT
..___tag_value_gammaf.316:
..B3.146:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B3.124
..B3.147:
        fldcw     110(%rsp)
        jmp       ..B3.124
..B3.149:
        testb     %r12b, %r12b
        je        ..B3.151
..B3.150:
        movl      %ebx, %edi
..___tag_value_gammaf.317:
        call      fesetround@PLT
..___tag_value_gammaf.318:
..B3.151:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B3.124
..B3.152:
        fldcw     110(%rsp)
        jmp       ..B3.124
        .align    16,0x90
	.cfi_endproc
	.type	gammaf,@function
	.size	gammaf,.-gammaf
	.data
# -- End  gammaf
	.text
# -- Begin  lgammaf_r
	.text
       .align    16,0x90
	.globl lgammaf_r
lgammaf_r:
# parameter 1: %xmm0
# parameter 2: %rdi
..B4.1:
	.cfi_startproc
..___tag_value_lgammaf_r.320:
..L321:

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
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        xorb      %r12b, %r12b
        movss     %xmm0, 112(%rsp)
        movq      %rdi, %r15
        movb      115(%rsp), %dl
        xorb      %bpl, %bpl
        andb      $-128, %dl
        shrb      $7, %dl
        movd      %xmm0, %eax
        movzwl    114(%rsp), %r14d
        andl      $8388607, %eax
        andl      $32640, %r14d
        shrl      $7, %r14d
        movl      %r14d, %r13d
        shll      $23, %r13d
        movb      %dl, (%rsp)
        orl       %eax, %r13d
..B4.2:
        fnstcw    110(%rsp)
..B4.3:
..___tag_value_lgammaf_r.335:
        call      fegetround@PLT
..___tag_value_lgammaf_r.336:
..B4.162:
        movl      %eax, %ebx
..B4.4:
        testl     %ebx, %ebx
        je        ..B4.6
..B4.5:
        xorl      %edi, %edi
        movb      $1, %r12b
..___tag_value_lgammaf_r.337:
        call      fesetround@PLT
..___tag_value_lgammaf_r.338:
..B4.6:
        movzwl    110(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B4.10
..B4.7:
        orl       $-64768, %edx
        movw      %dx, 108(%rsp)
..B4.8:
        fldcw     108(%rsp)
..B4.9:
        movb      $1, %bpl
..B4.10:
        cmpl      $255, %r14d
        jne       ..B4.16
..B4.11:
        movl      $1, (%r15)
        testb     %bpl, %bpl
        je        ..B4.13
..B4.12:
        fldcw     110(%rsp)
..B4.13:
        testb     %r12b, %r12b
        je        ..B4.15
..B4.14:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.339:
        call      fesetround@PLT
..___tag_value_lgammaf_r.340:
..B4.15:
        movss     112(%rsp), %xmm0
        mulss     %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.16:
        testl     %r13d, %r13d
        je        ..B4.152
..B4.17:
        movb      (%rsp), %al
        testb     %al, %al
        movl      $1, (%r15)
        jne       ..B4.74
..B4.18:
        cmpl      $2084876174, %r13d
        jae       ..B4.139
..B4.19:
        movss     112(%rsp), %xmm0
        ucomiss   .L_2il0floatpacket.6(%rip), %xmm0
        jp        ..B4.20
        je        ..B4.134
..B4.20:
        cmpl      $1174405120, %r13d
        jb        ..B4.26
..B4.21:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm0, 56(%rsp)
        pxor      %xmm0, %xmm0
        flds      56(%rsp)
        cvtss2sd  56(%rsp), %xmm0
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fstps     32(%rsp)
        fldt      (%rax)
        fstpt     16(%rsp)
        call      log@PLT
..B4.163:
        fldt      (%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fmull     56(%rsp)
        fstpt     (%rsp)
        je        ..B4.23
..B4.22:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.361:
        call      fesetround@PLT
..___tag_value_lgammaf_r.362:
..B4.23:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fsubs     32(%rsp)
        fstps     104(%rsp)
        je        ..B4.127
..B4.24:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.26:
        cmpl      $1082130432, %r13d
        jb        ..B4.33
..B4.27:
        addl      $-1082130432, %r13d
        lea       _A(%rip), %rax
        sarl      $23, %r13d
        lea       _B(%rip), %rdx
        movslq    %r13d, %r13
        lea       _C5(%rip), %r8
        movss     %xmm0, 56(%rsp)
        lea       (,%r13,8), %ecx
        flds      56(%rsp)
        lea       (%r13,%r13,2), %esi
        movslq    %ecx, %rcx
        lea       _C5_0(%rip), %r9
        movslq    %esi, %rsi
        testb     %r12b, %r12b
        fsubs     (%rax,%r13,4)
        fmuls     (%rdx,%r13,4)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8(%r8,%rcx,8)
        fmul      %st(1), %st
        fldl      (%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24(%r8,%rcx,8)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(2)
        faddl     16(%r8,%rcx,8)
        fmulp     %st, %st(1)
        faddl     16(%r9,%rsi,8)
        fstpt     (%rsp)
        je        ..B4.30
..B4.28:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.363:
        call      fesetround@PLT
..___tag_value_lgammaf_r.364:
..B4.29:
        fldt      80(%rsp)
..B4.30:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.31:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.33:
        ucomiss   .L_2il0floatpacket.4(%rip), %xmm0
        jp        ..B4.34
        je        ..B4.129
..B4.34:
        movss     %xmm0, 56(%rsp)
        cmpl      $1073741824, %r13d
        flds      56(%rsp)
        jbe       ..B4.40
..B4.35:
        fldt      .L_2il0floatpacket.5(%rip)
        fld       %st(1)
        lea       _C4(%rip), %r9
        lea       8+_C4(%rip), %rax
        fmul      %st(2), %st
        lea       32+_C4(%rip), %r10
        fldl      (%rax)
        lea       40+_C4(%rip), %rdx
        fmul      %st(1), %st
        lea       64+_C4(%rip), %r11
        fldl      (%r9)
        lea       72+_C4(%rip), %rcx
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        lea       48+_C4(%rip), %r13
        lea       56+_C4(%rip), %rsi
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        lea       16+_C4(%rip), %r14
        lea       24+_C4(%rip), %r8
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r13)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r8)
        lea       80+_C4(%rip), %r15
        testb     %r12b, %r12b
        fmul      %st(4), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        faddl     (%r14)
        fmulp     %st, %st(1)
        faddl     (%r15)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B4.37
..B4.36:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.365:
        call      fesetround@PLT
..___tag_value_lgammaf_r.366:
..B4.37:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B4.127
..B4.38:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.40:
        cmpl      $1071644672, %r13d
        jb        ..B4.47
..B4.41:
        fld       %st(0)
        lea       16+_C3(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C3(%rip), %rdx
        fldl      (%rax)
        lea       32+_C3(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C3(%rip), %rsi
        lea       8+_C3(%rip), %r8
        lea       40+_C3(%rip), %r9
        lea       24+_C3(%rip), %r10
        lea       56+_C3(%rip), %r11
        lea       _C3(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B4.44
..B4.42:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.367:
        call      fesetround@PLT
..___tag_value_lgammaf_r.368:
..B4.43:
        fldt      80(%rsp)
..B4.44:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.45:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.47:
        cmpl      $1069547520, %r13d
        jb        ..B4.54
..B4.48:
        fld       %st(0)
        lea       16+_LGAMMAF_C2(%rip), %rax
        fmul      %st(1), %st
        lea       48+_LGAMMAF_C2(%rip), %rdx
        fldl      (%rax)
        lea       32+_LGAMMAF_C2(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_LGAMMAF_C2(%rip), %rsi
        lea       8+_LGAMMAF_C2(%rip), %r8
        lea       40+_LGAMMAF_C2(%rip), %r9
        lea       24+_LGAMMAF_C2(%rip), %r10
        lea       56+_LGAMMAF_C2(%rip), %r11
        lea       _LGAMMAF_C2(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B4.51
..B4.49:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.369:
        call      fesetround@PLT
..___tag_value_lgammaf_r.370:
..B4.50:
        fldt      80(%rsp)
..B4.51:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.52:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.54:
        cmpl      $1067450368, %r13d
        jb        ..B4.61
..B4.55:
        lea       _LM(%rip), %rax
        lea       16+_C1(%rip), %rdx
        lea       48+_C1(%rip), %rcx
        lea       32+_C1(%rip), %rsi
        lea       64+_C1(%rip), %r8
        lea       8+_C1(%rip), %r9
        lea       40+_C1(%rip), %r10
        lea       24+_C1(%rip), %r11
        fldl      (%rdx)
        lea       56+_C1(%rip), %r13
        lea       _C1(%rip), %r14
        testb     %r12b, %r12b
        fxch      %st(1)
        fsubl     (%rax)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmul      %st(2), %st
        faddl     (%r13)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r14)
        fstpt     (%rsp)
        je        ..B4.58
..B4.56:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.371:
        call      fesetround@PLT
..___tag_value_lgammaf_r.372:
..B4.57:
        fldt      80(%rsp)
..B4.58:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.59:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.61:
        cmpl      $1065353216, %r13d
        jb        ..B4.68
..B4.62:
        fld       %st(0)
        lea       16+_C0(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C0(%rip), %rdx
        fldl      (%rax)
        lea       32+_C0(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C0(%rip), %rsi
        lea       8+_C0(%rip), %r8
        lea       40+_C0(%rip), %r9
        lea       24+_C0(%rip), %r10
        lea       56+_C0(%rip), %r11
        lea       _C0(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B4.65
..B4.63:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.373:
        call      fesetround@PLT
..___tag_value_lgammaf_r.374:
..B4.64:
        fldt      80(%rsp)
..B4.65:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.66:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.68:
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fadd      %st(1), %st
        fstpt     (%rsp)
        fstpt     32(%rsp)
..___tag_value_lgammaf_r.376:
        call      lgammaf_pos
..___tag_value_lgammaf_r.377:
..B4.165:
        fldt      32(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B4.164:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        je        ..B4.71
..B4.69:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.379:
        call      fesetround@PLT
..___tag_value_lgammaf_r.380:
..B4.70:
        fldt      80(%rsp)
..B4.71:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.72:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.74:
        movss     112(%rsp), %xmm0
        call      nearbyintf@PLT
..B4.166:
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        movss     112(%rsp), %xmm2
        ucomiss   %xmm1, %xmm2
        jp        ..B4.75
        je        ..B4.147
..B4.75:
        comiss    .L_2il0floatpacket.8(%rip), %xmm2
        jbe       ..B4.78
..B4.76:
        movaps    %xmm2, %xmm0
        movss     %xmm2, (%rsp)
        call      ceilf@PLT
..B4.167:
        cvttss2si %xmm0, %eax
        movss     (%rsp), %xmm2
        testb     $1, %al
        jne       ..B4.78
..B4.77:
        movl      $-1, (%r15)
..B4.78:
        cmpl      $1073741824, %r13d
        jae       ..B4.89
..B4.79:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B4.144
        jp        ..B4.144
..B4.80:
        fldt      16(%rsp)
..B4.81:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B4.82
        jb        ..B4.81
..B4.82:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B4.83:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B4.169:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_lgammaf_r.382:
        call      lgammaf_pos
..___tag_value_lgammaf_r.383:
..B4.168:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B4.86
..B4.84:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.385:
        call      fesetround@PLT
..___tag_value_lgammaf_r.386:
..B4.85:
        fldt      80(%rsp)
..B4.86:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.87:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.89:
        cmpl      $1082130432, %r13d
        jae       ..B4.106
..B4.90:
        movss     .L_2il0floatpacket.2(%rip), %xmm1
        lea       _LRIB(%rip), %rdx
        subss     %xmm2, %xmm1
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        movss     %xmm1, 104(%rsp)
        pxor      %xmm1, %xmm1
        movss     104(%rsp), %xmm0
        addss     %xmm2, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm3
        movl      104(%rsp), %ecx
        subss     %xmm2, %xmm3
        movss     112(%rsp), %xmm2
        andl      $1, %ecx
        cvtss2sd  %xmm2, %xmm1
        movss     %xmm3, 104(%rsp)
        lea       (%rcx,%rcx,2), %eax
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B4.93
..B4.91:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B4.93
..B4.92:
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _LRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B4.101
..B4.93:
        lea       _RRIB(%rip), %rdx
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B4.96
..B4.94:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B4.96
..B4.95:
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _RRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B4.101
..B4.96:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fcomip    %st(2), %st
        jae       ..B4.145
        jp        ..B4.145
..B4.97:
        fldt      (%rsp)
..B4.98:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B4.99
        jb        ..B4.98
..B4.99:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     80(%rsp)
..B4.100:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B4.171:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstl      16(%rsp)
        fstpt     80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      16(%rsp)
        fstpt     (%rsp)
..___tag_value_lgammaf_r.388:
        call      lgammaf_pos
..___tag_value_lgammaf_r.389:
..B4.170:
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fsubl     16(%rsp)
        fstpt     (%rsp)
..B4.101:
        testb     %r12b, %r12b
        je        ..B4.103
..B4.102:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.391:
        call      fesetround@PLT
..___tag_value_lgammaf_r.392:
..B4.103:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B4.127
..B4.104:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.106:
        cmpl      $1091567616, %r13d
        jae       ..B4.117
..B4.107:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B4.146
        jp        ..B4.146
..B4.108:
        fldt      16(%rsp)
..B4.109:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B4.110
        jb        ..B4.109
..B4.110:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B4.111:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B4.173:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_lgammaf_r.394:
        call      lgammaf_pos
..___tag_value_lgammaf_r.395:
..B4.172:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B4.114
..B4.112:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_lgammaf_r.397:
        call      fesetround@PLT
..___tag_value_lgammaf_r.398:
..B4.113:
        fldt      80(%rsp)
..B4.114:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.115:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.117:
        cmpl      $1174405120, %r13d
        jb        ..B4.123
..B4.118:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        fsts      (%rsp)
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%rsp)
        fchs      
        fstl      96(%rsp)
        movsd     96(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      (%rax)
        fstpt     24(%rsp)
        call      log@PLT
..B4.175:
        fldt      8(%rsp)
        lea       _S16(%rip), %rax
        fldt      24(%rsp)
        lea       _S14(%rip), %rdx
        fldt      40(%rsp)
        lea       _S12(%rip), %rcx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        lea       _S10(%rip), %rsi
        fldl      (%rax)
        lea       _S08(%rip), %r8
        movss     %xmm2, 104(%rsp)
        lea       _S06(%rip), %r9
        movss     104(%rsp), %xmm1
        lea       _S04(%rip), %r10
        movsd     %xmm0, 56(%rsp)
        lea       _S02(%rip), %r11
        fxch      %st(3)
        fmull     56(%rsp)
        subss     112(%rsp), %xmm1
        fsubp     %st, %st(2)
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm3
        fxch      %st(1)
        fsubs     (%rsp)
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        movss     104(%rsp), %xmm4
        movss     %xmm4, 56(%rsp)
        fxch      %st(1)
        fsubs     56(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fmul      %st, %st(2)
        andb      $127, 73(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        fxch      %st(2)
        faddl     (%rdx)
        fmul      %st(2), %st
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        call      log@PLT
..B4.174:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        je        ..B4.120
..B4.119:
        movl      %ebx, %edi
        fstpl     (%rsp)
..___tag_value_lgammaf_r.399:
        call      fesetround@PLT
..___tag_value_lgammaf_r.400:
..B4.179:
        fldl      (%rsp)
..B4.120:
        fldt      8(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.121:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.123:
        flds      112(%rsp)
        movss     .L_2il0floatpacket.3(%rip), %xmm3
        fchs      
        movaps    %xmm3, %xmm1
        fstl      96(%rsp)
        subss     %xmm2, %xmm1
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      40(%rsp)
        subss     %xmm3, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm2
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        movsd     96(%rsp), %xmm0
        fsubrp    %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fstpt     24(%rsp)
        call      log@PLT
..B4.178:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fchs      
        fldt      56(%rsp)
        fstpt     (%rsp)
        fstpt     24(%rsp)
..___tag_value_lgammaf_r.402:
        call      lgammaf_pos
..___tag_value_lgammaf_r.403:
..B4.177:
        fldt      24(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fldt      24(%rsp)
        lea       _S14(%rip), %rax
        fld       %st(0)
        lea       _S16(%rip), %r8
        fmul      %st(1), %st
        lea       _S10(%rip), %rdx
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldl      (%rax)
        lea       _S12(%rip), %r9
        fmul      %st(3), %st
        lea       _S06(%rip), %rcx
        fldl      (%r8)
        lea       _S08(%rip), %r10
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(4), %st
        lea       _S02(%rip), %rsi
        fxch      %st(1)
        faddl     (%rsi)
        lea       _S04(%rip), %r11
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(3)
        andb      $127, 73(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B4.176:
        fldt      8(%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        je        ..B4.125
..B4.124:
        movl      %ebx, %edi
        fstpl     (%rsp)
        fstpt     8(%rsp)
..___tag_value_lgammaf_r.405:
        call      fesetround@PLT
..___tag_value_lgammaf_r.406:
..B4.180:
        fldt      8(%rsp)
        fldl      (%rsp)
..B4.125:
        testb     %bpl, %bpl
        fsubrp    %st, %st(1)
        fstps     104(%rsp)
        je        ..B4.127
..B4.126:
        fldcw     110(%rsp)
..B4.127:
        movss     104(%rsp), %xmm0
..B4.128:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.129:
        testb     %bpl, %bpl
        je        ..B4.131
..B4.130:
        fldcw     110(%rsp)
..B4.131:
        testb     %r12b, %r12b
        je        ..B4.133
..B4.132:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.427:
        call      fesetround@PLT
..___tag_value_lgammaf_r.428:
..B4.133:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.134:
        testb     %bpl, %bpl
        je        ..B4.136
..B4.135:
        fldcw     110(%rsp)
..B4.136:
        testb     %r12b, %r12b
        je        ..B4.138
..B4.137:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.449:
        call      fesetround@PLT
..___tag_value_lgammaf_r.450:
..B4.138:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.139:
        testb     %r12b, %r12b
        je        ..B4.141
..B4.140:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.471:
        call      fesetround@PLT
..___tag_value_lgammaf_r.472:
..B4.141:
        lea       _PBIG(%rip), %rax
        testb     %bpl, %bpl
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 104(%rsp)
        je        ..B4.127
..B4.142:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.144:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B4.83
..B4.145:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B4.100
..B4.146:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B4.111
..B4.147:
        testb     %r12b, %r12b
        je        ..B4.149
..B4.148:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.473:
        call      fesetround@PLT
..___tag_value_lgammaf_r.474:
..B4.149:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B4.127
..B4.150:
        fldcw     110(%rsp)
        jmp       ..B4.127
..B4.152:
        movb      (%rsp), %al
        testb     %al, %al
        je        ..B4.154
..B4.153:
        movl      $-1, (%r15)
        jmp       ..B4.155
..B4.154:
        movl      $1, (%r15)
..B4.155:
        testb     %r12b, %r12b
        je        ..B4.157
..B4.156:
        movl      %ebx, %edi
..___tag_value_lgammaf_r.475:
        call      fesetround@PLT
..___tag_value_lgammaf_r.476:
..B4.157:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B4.127
..B4.158:
        fldcw     110(%rsp)
        jmp       ..B4.127
        .align    16,0x90
	.cfi_endproc
	.type	lgammaf_r,@function
	.size	lgammaf_r,.-lgammaf_r
	.data
# -- End  lgammaf_r
	.text
# -- Begin  gammaf_r
	.text
       .align    16,0x90
	.globl gammaf_r
gammaf_r:
# parameter 1: %xmm0
# parameter 2: %rdi
..B5.1:
	.cfi_startproc
..___tag_value_gammaf_r.478:
..L479:

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
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        xorb      %r12b, %r12b
        movss     %xmm0, 112(%rsp)
        movq      %rdi, %r15
        movb      115(%rsp), %dl
        xorb      %bpl, %bpl
        andb      $-128, %dl
        shrb      $7, %dl
        movd      %xmm0, %eax
        movzwl    114(%rsp), %r14d
        andl      $8388607, %eax
        andl      $32640, %r14d
        shrl      $7, %r14d
        movl      %r14d, %r13d
        shll      $23, %r13d
        movb      %dl, (%rsp)
        orl       %eax, %r13d
..B5.2:
        fnstcw    110(%rsp)
..B5.3:
..___tag_value_gammaf_r.493:
        call      fegetround@PLT
..___tag_value_gammaf_r.494:
..B5.162:
        movl      %eax, %ebx
..B5.4:
        testl     %ebx, %ebx
        je        ..B5.6
..B5.5:
        xorl      %edi, %edi
        movb      $1, %r12b
..___tag_value_gammaf_r.495:
        call      fesetround@PLT
..___tag_value_gammaf_r.496:
..B5.6:
        movzwl    110(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B5.10
..B5.7:
        orl       $-64768, %edx
        movw      %dx, 108(%rsp)
..B5.8:
        fldcw     108(%rsp)
..B5.9:
        movb      $1, %bpl
..B5.10:
        cmpl      $255, %r14d
        jne       ..B5.16
..B5.11:
        movl      $1, (%r15)
        testb     %bpl, %bpl
        je        ..B5.13
..B5.12:
        fldcw     110(%rsp)
..B5.13:
        testb     %r12b, %r12b
        je        ..B5.15
..B5.14:
        movl      %ebx, %edi
..___tag_value_gammaf_r.497:
        call      fesetround@PLT
..___tag_value_gammaf_r.498:
..B5.15:
        movss     112(%rsp), %xmm0
        mulss     %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B5.16:
        testl     %r13d, %r13d
        je        ..B5.152
..B5.17:
        movb      (%rsp), %al
        testb     %al, %al
        movl      $1, (%r15)
        jne       ..B5.74
..B5.18:
        cmpl      $2084876174, %r13d
        jae       ..B5.139
..B5.19:
        movss     112(%rsp), %xmm0
        ucomiss   .L_2il0floatpacket.6(%rip), %xmm0
        jp        ..B5.20
        je        ..B5.134
..B5.20:
        cmpl      $1174405120, %r13d
        jb        ..B5.26
..B5.21:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm0, 56(%rsp)
        pxor      %xmm0, %xmm0
        flds      56(%rsp)
        cvtss2sd  56(%rsp), %xmm0
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fstps     32(%rsp)
        fldt      (%rax)
        fstpt     16(%rsp)
        call      log@PLT
..B5.163:
        fldt      (%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fmull     56(%rsp)
        fstpt     (%rsp)
        je        ..B5.23
..B5.22:
        movl      %ebx, %edi
..___tag_value_gammaf_r.519:
        call      fesetround@PLT
..___tag_value_gammaf_r.520:
..B5.23:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fsubs     32(%rsp)
        fstps     104(%rsp)
        je        ..B5.127
..B5.24:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.26:
        cmpl      $1082130432, %r13d
        jb        ..B5.33
..B5.27:
        addl      $-1082130432, %r13d
        lea       _A(%rip), %rax
        sarl      $23, %r13d
        lea       _B(%rip), %rdx
        movslq    %r13d, %r13
        lea       _C5(%rip), %r8
        movss     %xmm0, 56(%rsp)
        lea       (,%r13,8), %ecx
        flds      56(%rsp)
        lea       (%r13,%r13,2), %esi
        movslq    %ecx, %rcx
        lea       _C5_0(%rip), %r9
        movslq    %esi, %rsi
        testb     %r12b, %r12b
        fsubs     (%rax,%r13,4)
        fmuls     (%rdx,%r13,4)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8(%r8,%rcx,8)
        fmul      %st(1), %st
        fldl      (%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8(%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r9,%rsi,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%r8,%rcx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24(%r8,%rcx,8)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(2)
        faddl     16(%r8,%rcx,8)
        fmulp     %st, %st(1)
        faddl     16(%r9,%rsi,8)
        fstpt     (%rsp)
        je        ..B5.30
..B5.28:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.521:
        call      fesetround@PLT
..___tag_value_gammaf_r.522:
..B5.29:
        fldt      80(%rsp)
..B5.30:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.31:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.33:
        ucomiss   .L_2il0floatpacket.4(%rip), %xmm0
        jp        ..B5.34
        je        ..B5.129
..B5.34:
        movss     %xmm0, 56(%rsp)
        cmpl      $1073741824, %r13d
        flds      56(%rsp)
        jbe       ..B5.40
..B5.35:
        fldt      .L_2il0floatpacket.5(%rip)
        fld       %st(1)
        lea       _C4(%rip), %r9
        lea       8+_C4(%rip), %rax
        fmul      %st(2), %st
        lea       32+_C4(%rip), %r10
        fldl      (%rax)
        lea       40+_C4(%rip), %rdx
        fmul      %st(1), %st
        lea       64+_C4(%rip), %r11
        fldl      (%r9)
        lea       72+_C4(%rip), %rcx
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rcx)
        lea       48+_C4(%rip), %r13
        lea       56+_C4(%rip), %rsi
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rsi)
        lea       16+_C4(%rip), %r14
        lea       24+_C4(%rip), %r8
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r13)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%r8)
        lea       80+_C4(%rip), %r15
        testb     %r12b, %r12b
        fmul      %st(4), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        faddl     (%r14)
        fmulp     %st, %st(1)
        faddl     (%r15)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B5.37
..B5.36:
        movl      %ebx, %edi
..___tag_value_gammaf_r.523:
        call      fesetround@PLT
..___tag_value_gammaf_r.524:
..B5.37:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B5.127
..B5.38:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.40:
        cmpl      $1071644672, %r13d
        jb        ..B5.47
..B5.41:
        fld       %st(0)
        lea       16+_C3(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C3(%rip), %rdx
        fldl      (%rax)
        lea       32+_C3(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C3(%rip), %rsi
        lea       8+_C3(%rip), %r8
        lea       40+_C3(%rip), %r9
        lea       24+_C3(%rip), %r10
        lea       56+_C3(%rip), %r11
        lea       _C3(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B5.44
..B5.42:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.525:
        call      fesetround@PLT
..___tag_value_gammaf_r.526:
..B5.43:
        fldt      80(%rsp)
..B5.44:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.45:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.47:
        cmpl      $1069547520, %r13d
        jb        ..B5.54
..B5.48:
        fld       %st(0)
        lea       16+_LGAMMAF_C2(%rip), %rax
        fmul      %st(1), %st
        lea       48+_LGAMMAF_C2(%rip), %rdx
        fldl      (%rax)
        lea       32+_LGAMMAF_C2(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_LGAMMAF_C2(%rip), %rsi
        lea       8+_LGAMMAF_C2(%rip), %r8
        lea       40+_LGAMMAF_C2(%rip), %r9
        lea       24+_LGAMMAF_C2(%rip), %r10
        lea       56+_LGAMMAF_C2(%rip), %r11
        lea       _LGAMMAF_C2(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B5.51
..B5.49:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.527:
        call      fesetround@PLT
..___tag_value_gammaf_r.528:
..B5.50:
        fldt      80(%rsp)
..B5.51:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.52:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.54:
        cmpl      $1067450368, %r13d
        jb        ..B5.61
..B5.55:
        lea       _LM(%rip), %rax
        lea       16+_C1(%rip), %rdx
        lea       48+_C1(%rip), %rcx
        lea       32+_C1(%rip), %rsi
        lea       64+_C1(%rip), %r8
        lea       8+_C1(%rip), %r9
        lea       40+_C1(%rip), %r10
        lea       24+_C1(%rip), %r11
        fldl      (%rdx)
        lea       56+_C1(%rip), %r13
        lea       _C1(%rip), %r14
        testb     %r12b, %r12b
        fxch      %st(1)
        fsubl     (%rax)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmul      %st(2), %st
        faddl     (%r13)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r14)
        fstpt     (%rsp)
        je        ..B5.58
..B5.56:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.529:
        call      fesetround@PLT
..___tag_value_gammaf_r.530:
..B5.57:
        fldt      80(%rsp)
..B5.58:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.59:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.61:
        cmpl      $1065353216, %r13d
        jb        ..B5.68
..B5.62:
        fld       %st(0)
        lea       16+_C0(%rip), %rax
        fmul      %st(1), %st
        lea       48+_C0(%rip), %rdx
        fldl      (%rax)
        lea       32+_C0(%rip), %rcx
        fmul      %st(1), %st
        lea       64+_C0(%rip), %rsi
        lea       8+_C0(%rip), %r8
        lea       40+_C0(%rip), %r9
        lea       24+_C0(%rip), %r10
        lea       56+_C0(%rip), %r11
        lea       _C0(%rip), %r13
        testb     %r12b, %r12b
        faddl     (%rdx)
        fmul      %st(1), %st
        faddl     (%rcx)
        fmul      %st(1), %st
        faddl     (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     (%r13)
        fstpt     (%rsp)
        je        ..B5.65
..B5.63:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.531:
        call      fesetround@PLT
..___tag_value_gammaf_r.532:
..B5.64:
        fldt      80(%rsp)
..B5.65:
        fldt      (%rsp)
        testb     %bpl, %bpl
        faddp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.66:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.68:
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fadd      %st(1), %st
        fstpt     (%rsp)
        fstpt     32(%rsp)
..___tag_value_gammaf_r.534:
        call      lgammaf_pos
..___tag_value_gammaf_r.535:
..B5.165:
        fldt      32(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B5.164:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        je        ..B5.71
..B5.69:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.537:
        call      fesetround@PLT
..___tag_value_gammaf_r.538:
..B5.70:
        fldt      80(%rsp)
..B5.71:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.72:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.74:
        movss     112(%rsp), %xmm0
        call      nearbyintf@PLT
..B5.166:
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm1
        movss     112(%rsp), %xmm2
        ucomiss   %xmm1, %xmm2
        jp        ..B5.75
        je        ..B5.147
..B5.75:
        comiss    .L_2il0floatpacket.8(%rip), %xmm2
        jbe       ..B5.78
..B5.76:
        movaps    %xmm2, %xmm0
        movss     %xmm2, (%rsp)
        call      ceilf@PLT
..B5.167:
        cvttss2si %xmm0, %eax
        movss     (%rsp), %xmm2
        testb     $1, %al
        jne       ..B5.78
..B5.77:
        movl      $-1, (%r15)
..B5.78:
        cmpl      $1073741824, %r13d
        jae       ..B5.89
..B5.79:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B5.144
        jp        ..B5.144
..B5.80:
        fldt      16(%rsp)
..B5.81:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B5.82
        jb        ..B5.81
..B5.82:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B5.83:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B5.169:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_gammaf_r.540:
        call      lgammaf_pos
..___tag_value_gammaf_r.541:
..B5.168:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B5.86
..B5.84:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.543:
        call      fesetround@PLT
..___tag_value_gammaf_r.544:
..B5.85:
        fldt      80(%rsp)
..B5.86:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.87:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.89:
        cmpl      $1082130432, %r13d
        jae       ..B5.106
..B5.90:
        movss     .L_2il0floatpacket.2(%rip), %xmm1
        lea       _LRIB(%rip), %rdx
        subss     %xmm2, %xmm1
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        movss     %xmm1, 104(%rsp)
        pxor      %xmm1, %xmm1
        movss     104(%rsp), %xmm0
        addss     %xmm2, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm3
        movl      104(%rsp), %ecx
        subss     %xmm2, %xmm3
        movss     112(%rsp), %xmm2
        andl      $1, %ecx
        cvtss2sd  %xmm2, %xmm1
        movss     %xmm3, 104(%rsp)
        lea       (%rcx,%rcx,2), %eax
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B5.93
..B5.91:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B5.93
..B5.92:
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _LRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B5.101
..B5.93:
        lea       _RRIB(%rip), %rdx
        comisd    (%rdx,%rax,8), %xmm1
        jbe       ..B5.96
..B5.94:
        movsd     16(%rdx,%rax,8), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B5.96
..B5.95:
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        shll      $2, %ecx
        fsubl     8(%rdx,%rax,8)
        lea       _RRP(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     8(%rax,%rcx,8)
        fmul      %st(1), %st
        faddl     16(%rax,%rcx,8)
        fmulp     %st, %st(1)
        faddl     24(%rax,%rcx,8)
        fstpt     (%rsp)
        jmp       ..B5.101
..B5.96:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fcomip    %st(2), %st
        jae       ..B5.145
        jp        ..B5.145
..B5.97:
        fldt      (%rsp)
..B5.98:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B5.99
        jb        ..B5.98
..B5.99:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     80(%rsp)
..B5.100:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B5.171:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstl      16(%rsp)
        fstpt     80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      16(%rsp)
        fstpt     (%rsp)
..___tag_value_gammaf_r.546:
        call      lgammaf_pos
..___tag_value_gammaf_r.547:
..B5.170:
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fsubl     16(%rsp)
        fstpt     (%rsp)
..B5.101:
        testb     %r12b, %r12b
        je        ..B5.103
..B5.102:
        movl      %ebx, %edi
..___tag_value_gammaf_r.549:
        call      fesetround@PLT
..___tag_value_gammaf_r.550:
..B5.103:
        fldt      (%rsp)
        testb     %bpl, %bpl
        fstps     104(%rsp)
        je        ..B5.127
..B5.104:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.106:
        cmpl      $1091567616, %r13d
        jae       ..B5.117
..B5.107:
        fldt      .L_2il0floatpacket.0(%rip)
        movss     %xmm2, 56(%rsp)
        fld       %st(0)
        flds      56(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fcomip    %st(2), %st
        jae       ..B5.146
        jp        ..B5.146
..B5.108:
        fldt      16(%rsp)
..B5.109:
        fmul      %st, %st(1)
        fadd      %st(2), %st
        fcomi     %st(2), %st
        jp        ..B5.110
        jb        ..B5.109
..B5.110:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..B5.111:
        andb      $127, 89(%rsp)
        fldt      80(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B5.173:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fldt      32(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_gammaf_r.552:
        call      lgammaf_pos
..___tag_value_gammaf_r.553:
..B5.172:
        fldt      16(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        testb     %r12b, %r12b
        je        ..B5.114
..B5.112:
        fstp      %st(0)
        movl      %ebx, %edi
..___tag_value_gammaf_r.555:
        call      fesetround@PLT
..___tag_value_gammaf_r.556:
..B5.113:
        fldt      80(%rsp)
..B5.114:
        fldt      16(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.115:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.117:
        cmpl      $1174405120, %r13d
        jb        ..B5.123
..B5.118:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _LN_SQRT_TWO_PI(%rip), %rax
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        fsts      (%rsp)
        fsub      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%rsp)
        fchs      
        fstl      96(%rsp)
        movsd     96(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      (%rax)
        fstpt     24(%rsp)
        call      log@PLT
..B5.175:
        fldt      8(%rsp)
        lea       _S16(%rip), %rax
        fldt      24(%rsp)
        lea       _S14(%rip), %rdx
        fldt      40(%rsp)
        lea       _S12(%rip), %rcx
        movss     .L_2il0floatpacket.3(%rip), %xmm2
        lea       _S10(%rip), %rsi
        fldl      (%rax)
        lea       _S08(%rip), %r8
        movss     %xmm2, 104(%rsp)
        lea       _S06(%rip), %r9
        movss     104(%rsp), %xmm1
        lea       _S04(%rip), %r10
        movsd     %xmm0, 56(%rsp)
        lea       _S02(%rip), %r11
        fxch      %st(3)
        fmull     56(%rsp)
        subss     112(%rsp), %xmm1
        fsubp     %st, %st(2)
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm3
        fxch      %st(1)
        fsubs     (%rsp)
        subss     %xmm2, %xmm3
        movss     %xmm3, 104(%rsp)
        movss     104(%rsp), %xmm4
        movss     %xmm4, 56(%rsp)
        fxch      %st(1)
        fsubs     56(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fmul      %st, %st(2)
        andb      $127, 73(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        fxch      %st(2)
        faddl     (%rdx)
        fmul      %st(2), %st
        faddl     (%rcx)
        fmul      %st(2), %st
        faddl     (%rsi)
        fmul      %st(2), %st
        faddl     (%r8)
        fmul      %st(2), %st
        faddl     (%r9)
        fmul      %st(2), %st
        faddl     (%r10)
        fmul      %st(2), %st
        faddl     (%r11)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        call      log@PLT
..B5.174:
        movsd     %xmm0, 56(%rsp)
        testb     %r12b, %r12b
        fldl      56(%rsp)
        je        ..B5.120
..B5.119:
        movl      %ebx, %edi
        fstpl     (%rsp)
..___tag_value_gammaf_r.557:
        call      fesetround@PLT
..___tag_value_gammaf_r.558:
..B5.179:
        fldl      (%rsp)
..B5.120:
        fldt      8(%rsp)
        testb     %bpl, %bpl
        fsubp     %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.121:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.123:
        flds      112(%rsp)
        movss     .L_2il0floatpacket.3(%rip), %xmm3
        fchs      
        movaps    %xmm3, %xmm1
        fstl      96(%rsp)
        subss     %xmm2, %xmm1
        movss     %xmm1, 104(%rsp)
        movss     104(%rsp), %xmm0
        fstpt     40(%rsp)
        fldt      40(%rsp)
        subss     %xmm3, %xmm0
        movss     %xmm0, 104(%rsp)
        movss     104(%rsp), %xmm2
        movss     %xmm2, 56(%rsp)
        flds      56(%rsp)
        movsd     96(%rsp), %xmm0
        fsubrp    %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fstpt     24(%rsp)
        call      log@PLT
..B5.178:
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fchs      
        fldt      56(%rsp)
        fstpt     (%rsp)
        fstpt     24(%rsp)
..___tag_value_gammaf_r.560:
        call      lgammaf_pos
..___tag_value_gammaf_r.561:
..B5.177:
        fldt      24(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        fldt      24(%rsp)
        lea       _S14(%rip), %rax
        fld       %st(0)
        lea       _S16(%rip), %r8
        fmul      %st(1), %st
        lea       _S10(%rip), %rdx
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldl      (%rax)
        lea       _S12(%rip), %r9
        fmul      %st(3), %st
        lea       _S06(%rip), %rcx
        fldl      (%r8)
        lea       _S08(%rip), %r10
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rdx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r9)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%rcx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     (%r10)
        fmul      %st(4), %st
        lea       _S02(%rip), %rsi
        fxch      %st(1)
        faddl     (%rsi)
        lea       _S04(%rip), %r11
        fmulp     %st, %st(2)
        faddl     (%r11)
        fmulp     %st, %st(3)
        andb      $127, 73(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        fldt      64(%rsp)
        fstpl     96(%rsp)
        movsd     96(%rsp), %xmm0
        call      log@PLT
..B5.176:
        fldt      8(%rsp)
        testb     %r12b, %r12b
        movsd     %xmm0, 56(%rsp)
        fldl      56(%rsp)
        je        ..B5.125
..B5.124:
        movl      %ebx, %edi
        fstpl     (%rsp)
        fstpt     8(%rsp)
..___tag_value_gammaf_r.563:
        call      fesetround@PLT
..___tag_value_gammaf_r.564:
..B5.180:
        fldt      8(%rsp)
        fldl      (%rsp)
..B5.125:
        testb     %bpl, %bpl
        fsubrp    %st, %st(1)
        fstps     104(%rsp)
        je        ..B5.127
..B5.126:
        fldcw     110(%rsp)
..B5.127:
        movss     104(%rsp), %xmm0
..B5.128:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B5.129:
        testb     %bpl, %bpl
        je        ..B5.131
..B5.130:
        fldcw     110(%rsp)
..B5.131:
        testb     %r12b, %r12b
        je        ..B5.133
..B5.132:
        movl      %ebx, %edi
..___tag_value_gammaf_r.585:
        call      fesetround@PLT
..___tag_value_gammaf_r.586:
..B5.133:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B5.134:
        testb     %bpl, %bpl
        je        ..B5.136
..B5.135:
        fldcw     110(%rsp)
..B5.136:
        testb     %r12b, %r12b
        je        ..B5.138
..B5.137:
        movl      %ebx, %edi
..___tag_value_gammaf_r.607:
        call      fesetround@PLT
..___tag_value_gammaf_r.608:
..B5.138:
        pxor      %xmm0, %xmm0
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B5.139:
        testb     %r12b, %r12b
        je        ..B5.141
..B5.140:
        movl      %ebx, %edi
..___tag_value_gammaf_r.629:
        call      fesetround@PLT
..___tag_value_gammaf_r.630:
..B5.141:
        lea       _PBIG(%rip), %rax
        testb     %bpl, %bpl
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 104(%rsp)
        je        ..B5.127
..B5.142:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.144:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B5.83
..B5.145:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B5.100
..B5.146:
        fstp      %st(0)
        fstpt     80(%rsp)
        jmp       ..B5.111
..B5.147:
        testb     %r12b, %r12b
        je        ..B5.149
..B5.148:
        movl      %ebx, %edi
..___tag_value_gammaf_r.631:
        call      fesetround@PLT
..___tag_value_gammaf_r.632:
..B5.149:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B5.127
..B5.150:
        fldcw     110(%rsp)
        jmp       ..B5.127
..B5.152:
        movb      (%rsp), %al
        testb     %al, %al
        je        ..B5.154
..B5.153:
        movl      $-1, (%r15)
        jmp       ..B5.155
..B5.154:
        movl      $1, (%r15)
..B5.155:
        testb     %r12b, %r12b
        je        ..B5.157
..B5.156:
        movl      %ebx, %edi
..___tag_value_gammaf_r.633:
        call      fesetround@PLT
..___tag_value_gammaf_r.634:
..B5.157:
        movss     .L_2il0floatpacket.6(%rip), %xmm1
        testb     %bpl, %bpl
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        je        ..B5.127
..B5.158:
        fldcw     110(%rsp)
        jmp       ..B5.127
        .align    16,0x90
	.cfi_endproc
	.type	gammaf_r,@function
	.size	gammaf_r,.-gammaf_r
	.data
# -- End  gammaf_r
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 16
_LN_SQRT_TWO_PI:
	.word	42293
	.word	9717
	.word	36419
	.word	60223
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_LN_SQRT_TWO_PI,@object
	.size	_LN_SQRT_TWO_PI,16
	.align 16
_A:
	.long	1082130432
	.long	1090519040
	.long	1098907648
	.long	1107296256
	.long	1115684864
	.long	1124073472
	.long	1132462080
	.long	1140850688
	.long	1149239296
	.long	1157627904
	.long	1166016512
	.type	_A,@object
	.size	_A,44
	.space 4, 0x00 	# pad
	.align 16
_B:
	.long	1048576000
	.long	1040187392
	.long	1031798784
	.long	1023410176
	.long	1015021568
	.long	1006632960
	.long	998244352
	.long	989855744
	.long	981467136
	.long	973078528
	.long	964689920
	.type	_B,@object
	.size	_B,44
	.space 4, 0x00 	# pad
	.align 16
_C5:
	.long	2219950285
	.long	1064025046
	.long	657069815
	.long	3214307011
	.long	2744833897
	.long	1073883684
	.long	3746154693
	.long	1075058958
	.long	24044097
	.long	1068577021
	.long	1852002888
	.long	3217117620
	.long	426096126
	.long	1071550305
	.long	3945160253
	.long	3219869968
	.long	889741930
	.long	1064529657
	.long	2918550257
	.long	3214927323
	.long	3306758232
	.long	1074858655
	.long	2801363753
	.long	1076895752
	.long	216130741
	.long	1069082446
	.long	2022127190
	.long	3217667546
	.long	4043317980
	.long	1072274701
	.long	948933764
	.long	3220711872
	.long	1837011561
	.long	1065353117
	.long	614133169
	.long	3215620106
	.long	128884864
	.long	1075872424
	.long	1749057555
	.long	1078324632
	.long	1278187959
	.long	1069867493
	.long	1878987920
	.long	3218464301
	.long	2508500615
	.long	1073176520
	.long	3716705454
	.long	3221664954
	.long	1971591775
	.long	1066198339
	.long	3489274850
	.long	3216507307
	.long	2978044890
	.long	1076904104
	.long	399657850
	.long	1079744937
	.long	2176842243
	.long	1070795947
	.long	1642919051
	.long	3219397359
	.long	325839939
	.long	1074155724
	.long	1189620011
	.long	3222667808
	.long	583706808
	.long	1067149757
	.long	1974968851
	.long	3217478792
	.long	3433705325
	.long	1077944360
	.long	3946626575
	.long	1081121452
	.long	1495561758
	.long	1071787114
	.long	4062466621
	.long	3220390645
	.long	1556755231
	.long	1075170624
	.long	75679593
	.long	3223694033
	.long	3504606470
	.long	1068150172
	.long	1348305099
	.long	3218489288
	.long	3580634696
	.long	1078988808
	.long	1259291510
	.long	1082352761
	.long	652214916
	.long	1072807425
	.long	4143445962
	.long	3221412067
	.long	2123749472
	.long	1076202613
	.long	4248403985
	.long	3224731560
	.long	1507106795
	.long	1069174188
	.long	3519298167
	.long	3219518582
	.long	3633366982
	.long	1080035328
	.long	2867629808
	.long	1083583554
	.long	3975256969
	.long	1073841792
	.long	2533063588
	.long	3222447083
	.long	3711656185
	.long	1077242956
	.long	3206083648
	.long	3225774644
	.long	4129446490
	.long	1070209876
	.long	3121668168
	.long	3220557159
	.long	3645659330
	.long	1081082878
	.long	1254870306
	.long	1084814091
	.long	1589013900
	.long	1074883091
	.long	485315198
	.long	3223488795
	.long	402512060
	.long	1078287430
	.long	1922980292
	.long	3226820482
	.long	157052617
	.long	1071251449
	.long	2992469705
	.long	3221600396
	.long	1492808525
	.long	1082130942
	.long	3131814131
	.long	1086044499
	.long	2683002247
	.long	1075927855
	.long	2649378904
	.long	3224533843
	.long	3018380877
	.long	1079333956
	.long	653917322
	.long	3227867691
	.long	2713966257
	.long	1072296037
	.long	4209563533
	.long	3222646006
	.long	943575735
	.long	1083179262
	.long	512478091
	.long	1087274844
	.long	3856346814
	.long	1076974372
	.long	1324813592
	.long	3225580569
	.long	1409229319
	.long	1080381507
	.long	3787450294
	.long	3228915583
	.long	3333539597
	.long	1073342203
	.long	2943809629
	.long	3223692845
	.long	794946536
	.long	1084227710
	.long	1068889795
	.long	1088463522
	.long	3231716378
	.long	1078021787
	.long	4072054083
	.long	3226628144
	.long	1998042203
	.long	1081429569
	.long	1148344549
	.long	3229963818
	.type	_C5,@object
	.size	_C5,704
	.align 16
_C5_0:
	.long	2415979356
	.long	1070304454
	.long	2697119297
	.long	3218348439
	.long	4195263586
	.long	1073523467
	.long	3725211212
	.long	1070931684
	.long	2560716363
	.long	3219131775
	.long	4079479268
	.long	1075907809
	.long	1732432466
	.long	1071796064
	.long	3508892908
	.long	3219947991
	.long	2788858919
	.long	1077667382
	.long	2647999156
	.long	1072759450
	.long	547175205
	.long	3220886396
	.long	4254853098
	.long	1079215590
	.long	2099688739
	.long	1073767129
	.long	1386213411
	.long	3221881825
	.long	1373988558
	.long	1080631372
	.long	3012547841
	.long	1074795638
	.long	3012018428
	.long	3222904295
	.long	3968063321
	.long	1082046682
	.long	2372211290
	.long	1075829504
	.long	1852200321
	.long	3223939920
	.long	822603622
	.long	1083320025
	.long	2940107565
	.long	1076868074
	.long	1118670349
	.long	3224982033
	.long	4034033966
	.long	1084551076
	.long	3328910691
	.long	1077911588
	.long	4065388177
	.long	3226027369
	.long	3374537656
	.long	1085781831
	.long	3604631852
	.long	1078957575
	.long	786281660
	.long	3227074315
	.long	3320933858
	.long	1087012393
	.long	4128496013
	.long	1080004805
	.long	820316847
	.long	3228122065
	.long	596318476
	.long	1088242837
	.type	_C5_0,@object
	.size	_C5_0,264
	.space 8, 0x00 	# pad
	.align 16
_C4:
	.long	951661919
	.long	3199388794
	.long	406044492
	.long	1057185581
	.long	2144499589
	.long	3220511658
	.long	1533861631
	.long	1073519965
	.long	4190848711
	.long	3208779382
	.long	4195628742
	.long	1064581106
	.long	501584638
	.long	3218581252
	.long	4276665641
	.long	1072296199
	.long	2679252139
	.long	3214810152
	.long	1146917984
	.long	1069511714
	.long	3597634954
	.long	3220184694
	.type	_C4,@object
	.size	_C4,88
	.space 8, 0x00 	# pad
	.align 16
_C3:
	.long	953601802
	.long	1073782987
	.long	53392282
	.long	1061851682
	.long	2280564131
	.long	3213671573
	.long	126074441
	.long	1073190208
	.long	1099663909
	.long	3221770684
	.long	2017365083
	.long	1069271442
	.long	1709742889
	.long	3219105406
	.long	4029656968
	.long	1074967560
	.long	3888843833
	.long	3222480972
	.type	_C3,@object
	.size	_C3,72
	.space 8, 0x00 	# pad
	.align 16
_LGAMMAF_C2:
	.long	4192182476
	.long	1073863432
	.long	3945858175
	.long	1063638439
	.long	2616885214
	.long	3215216300
	.long	3886441432
	.long	1074077679
	.long	3758761250
	.long	3222447567
	.long	984616456
	.long	1070653784
	.long	2508733349
	.long	3220210863
	.long	2272977058
	.long	1075337602
	.long	3074119607
	.long	3222665277
	.type	_LGAMMAF_C2,@object
	.size	_LGAMMAF_C2,72
	.space 8, 0x00 	# pad
	.align 16
_LM:
	.long	1666629183
	.long	1073177304
	.type	_LM,@object
	.size	_LM,8
	.space 8, 0x00 	# pad
	.align 16
_C1:
	.long	3166931522
	.long	3216972217
	.long	900077338
	.long	1065586189
	.long	3591574455
	.long	3213040571
	.long	425993680
	.long	1068534592
	.long	2772351024
	.long	3217220647
	.long	3693139651
	.long	1066567133
	.long	2619486276
	.long	3214985484
	.long	3371509884
	.long	1071576875
	.long	2931564028
	.long	1032391485
	.type	_C1,@object
	.size	_C1,72
	.space 8, 0x00 	# pad
	.align 16
_C0:
	.long	561403216
	.long	1074066324
	.long	3155481408
	.long	1068272863
	.long	4228834524
	.long	3219267730
	.long	2107372888
	.long	1076349696
	.long	3392182390
	.long	3224133636
	.long	3282054928
	.long	1074040136
	.long	4253763640
	.long	3223020498
	.long	1528000942
	.long	1076400076
	.long	4059292707
	.long	3223275314
	.type	_C0,@object
	.size	_C0,72
	.space 8, 0x00 	# pad
	.align 16
_LRIB:
	.long	2838661661
	.long	3221617475
	.long	169597185
	.long	3221617473
	.long	1795500005
	.long	3221617470
	.long	3503748181
	.long	3222250619
	.long	1417424869
	.long	3222250609
	.long	2147483648
	.long	3222250589
	.type	_LRIB,@object
	.size	_LRIB,48
	.align 16
_LRP:
	.long	3272807679
	.long	3224639578
	.long	1009311715
	.long	1076045439
	.long	2835657108
	.long	3221135661
	.long	817526409
	.long	1017707397
	.long	2658191496
	.long	3232572725
	.long	2929834182
	.long	1081046750
	.long	2523682372
	.long	3224680861
	.long	915934040
	.long	3166559235
	.type	_LRP,@object
	.size	_LRP,64
	.align 16
_RRIB:
	.long	890743176
	.long	3221465087
	.long	2516645996
	.long	3221465084
	.long	4142548816
	.long	3221465081
	.long	1538591435
	.long	3221825040
	.long	3164494255
	.long	3221825037
	.long	495429779
	.long	3221825035
	.type	_RRIB,@object
	.size	_RRIB,48
	.align 16
_RRP:
	.long	93164946
	.long	1073124518
	.long	2953029931
	.long	1075015403
	.long	1722766176
	.long	1073233897
	.long	1830807661
	.long	1016083003
	.long	308861496
	.long	1079775543
	.long	2541562915
	.long	1077531858
	.long	1596959842
	.long	1075781798
	.long	3819862626
	.long	1021220357
	.type	_RRP,@object
	.size	_RRP,64
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 4
.L_2il0floatpacket.2:
	.long	0xc0200000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x4b400000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0x40000000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
.L_2il0floatpacket.6:
	.long	0x3f800000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,4
	.align 4
_S16:
	.long	2760304672
	.long	3218442033
	.type	_S16,@object
	.size	_S16,8
	.align 4
_S14:
	.long	3264091561
	.long	3215213868
	.type	_S14,@object
	.size	_S14,8
	.align 4
_S12:
	.long	1381164920
	.long	3217582605
	.type	_S12,@object
	.size	_S12,8
	.align 4
_S10:
	.long	3387828299
	.long	3217636872
	.type	_S10,@object
	.size	_S10,8
	.align 4
_S08:
	.long	3786959951
	.long	3218084854
	.type	_S08,@object
	.size	_S08,8
	.align 4
_S06:
	.long	2675586079
	.long	3218453489
	.type	_S06,@object
	.size	_S06,8
	.align 4
_S04:
	.long	1432133341
	.long	3219214642
	.type	_S04,@object
	.size	_S04,8
	.align 4
_S02:
	.long	1648889397
	.long	3220853158
	.type	_S02,@object
	.size	_S02,8
	.align 4
_PBIG:
	.long	2139095039
	.long	2139095039
	.type	_PBIG,@object
	.size	_PBIG,8
	.align 4
.L_2il0floatpacket.8:
	.long	0xcb400000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
