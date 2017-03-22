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
	.file "y1f.c"
	.text
..TXTST0:
# -- Begin  y1f
	.text
       .align    16,0x90
	.globl y1f
y1f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_y1f.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        movd      %xmm0, %edx
        movss     %xmm0, 48(%rsp)
        lea       -1(%rdx), %eax
        cmpl      $2139095039, %eax
        jae       ..B1.17
..B1.2:
        cmpl      $1100816743, %edx
        jae       ..B1.25
..B1.3:
        cmpl      $1099170276, %edx
        jae       ..B1.16
..B1.4:
        cmpl      $1096140558, %edx
        jae       ..B1.15
..B1.5:
        cmpl      $1092849338, %edx
        jae       ..B1.14
..B1.6:
        cmpl      $1088602350, %edx
        jae       ..B1.13
..B1.7:
        cmpl      $1081952922, %edx
        jae       ..B1.12
..B1.8:
        cmpl      $1071812444, %edx
        jb        ..B1.10
..B1.9:
        cvtss2sd  %xmm0, %xmm0
        lea       128+_P1(%rip), %rax
        lea       120+_P1(%rip), %rdx
        lea       88+_P1(%rip), %rcx
        lea       56+_P1(%rip), %rsi
        lea       104+_P1(%rip), %r8
        lea       24+_P1(%rip), %rdi
        lea       72+_P1(%rip), %r9
        lea       40+_P1(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       80+_P1(%rip), %rdx
        lea       8+_P1(%rip), %r11
        subsd     (%rax), %xmm0
        movaps    %xmm0, %xmm2
        lea       112+_P1(%rip), %rax
        mulsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm3
        mulsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm6
        movsd     (%r8), %xmm1
        lea       64+_P1(%rip), %r8
        movsd     (%rax), %xmm5
        mulsd     %xmm3, %xmm1
        addsd     (%rcx), %xmm6
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     (%r9), %xmm1
        addsd     (%rdx), %xmm5
        mulsd     %xmm3, %xmm1
        addsd     (%rsi), %xmm6
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     (%r10), %xmm1
        lea       48+_P1(%rip), %rcx
        lea       32+_P1(%rip), %r9
        mulsd     %xmm3, %xmm1
        addsd     (%rdi), %xmm6
        addsd     (%rcx), %xmm5
        mulsd     %xmm2, %xmm6
        addsd     (%r11), %xmm1
        mulsd     %xmm3, %xmm5
        mulsd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm1
        lea       96+_P1(%rip), %rdi
        lea       16+_P1(%rip), %rsi
        lea       _P1(%rip), %r10
        addsd     %xmm1, %xmm6
        addsd     (%rsi), %xmm5
        movsd     (%rdi), %xmm4
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm5
        addsd     (%r8), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     (%r9), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     (%r10), %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.10:
        pxor      %xmm4, %xmm4
        cvtss2sd  48(%rsp), %xmm4
        movaps    %xmm4, %xmm0
        movsd     %xmm4, (%rsp)
..___tag_value_y1f.6:
        call      __libm_log_k32@PLT
..___tag_value_y1f.7:
..B1.29:
        movsd     (%rsp), %xmm4
..B1.11:
        lea       _tonpi(%rip), %rax
        movaps    %xmm4, %xmm2
        mulsd     %xmm4, %xmm2
        lea       40+_Q2(%rip), %r10
        movsd     (%rax), %xmm7
        movaps    %xmm2, %xmm3
        mulsd     %xmm7, %xmm0
        lea       40+_Q1(%rip), %rdx
        divsd     %xmm4, %xmm7
        mulsd     %xmm2, %xmm3
        lea       24+_Q2(%rip), %r11
        movsd     (%r10), %xmm6
        lea       24+_Q1(%rip), %rcx
        movsd     (%rdx), %xmm8
        lea       32+_Q2(%rip), %rdx
        mulsd     %xmm3, %xmm6
        lea       32+_Q1(%rip), %rdi
        mulsd     %xmm3, %xmm8
        addsd     (%r11), %xmm6
        addsd     (%rcx), %xmm8
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm8
        movsd     (%rdx), %xmm5
        lea       16+_Q2(%rip), %rcx
        mulsd     %xmm3, %xmm5
        lea       8+_Q2(%rip), %rax
        addsd     (%rax), %xmm6
        addsd     (%rcx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm5
        mulsd     %xmm4, %xmm6
        lea       8+_Q1(%rip), %rsi
        lea       16+_Q1(%rip), %r8
        movsd     (%rdi), %xmm1
        lea       _Q1(%rip), %r9
        mulsd     %xmm3, %xmm1
        addsd     (%rsi), %xmm8
        addsd     (%r8), %xmm1
        mulsd     %xmm2, %xmm8
        mulsd     %xmm3, %xmm1
        mulsd     %xmm4, %xmm8
        addsd     (%r9), %xmm1
        lea       _Q2(%rip), %rsi
        mulsd     %xmm4, %xmm1
        addsd     (%rsi), %xmm5
        addsd     %xmm1, %xmm8
        mulsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm0
        subsd     %xmm7, %xmm0
        addsd     %xmm0, %xmm8
        cvtsd2ss  %xmm8, %xmm8
        movaps    %xmm8, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.12:
        pxor      %xmm0, %xmm0
        lea       112+_P2(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        lea       104+_P2(%rip), %rdx
        lea       72+_P2(%rip), %rcx
        lea       40+_P2(%rip), %rsi
        lea       88+_P2(%rip), %r8
        lea       96+_P2(%rip), %r11
        lea       56+_P2(%rip), %r9
        lea       8+_P2(%rip), %rdi
        lea       24+_P2(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       32+_P2(%rip), %rdx
        movsd     (%r8), %xmm1
        lea       16+_P2(%rip), %r8
        movsd     (%r11), %xmm5
        subsd     (%rax), %xmm0
        lea       64+_P2(%rip), %rax
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r9), %xmm1
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r10), %xmm1
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rdi), %xmm6
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm6
        lea       80+_P2(%rip), %rsi
        lea       48+_P2(%rip), %rdi
        lea       _P2(%rip), %rcx
        addsd     %xmm1, %xmm6
        addsd     (%rcx), %xmm5
        movsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.13:
        pxor      %xmm3, %xmm3
        lea       104+_P3(%rip), %rax
        cvtss2sd  48(%rsp), %xmm3
        lea       96+_P3(%rip), %rdx
        lea       64+_P3(%rip), %rcx
        lea       80+_P3(%rip), %rdi
        lea       88+_P3(%rip), %r11
        lea       48+_P3(%rip), %r8
        lea       32+_P3(%rip), %rsi
        lea       16+_P3(%rip), %r9
        lea       _P3(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       24+_P3(%rip), %rdx
        movsd     (%rdi), %xmm0
        lea       8+_P3(%rip), %rdi
        movsd     (%r11), %xmm5
        subsd     (%rax), %xmm3
        lea       56+_P3(%rip), %rax
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r8), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r9), %xmm0
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm0, %xmm6
        mulsd     %xmm3, %xmm5
        addsd     (%r10), %xmm6
        lea       72+_P3(%rip), %rcx
        lea       40+_P3(%rip), %rsi
        movsd     (%rcx), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.14:
        pxor      %xmm0, %xmm0
        lea       112+_P4(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        lea       104+_P4(%rip), %rdx
        lea       72+_P4(%rip), %rcx
        lea       40+_P4(%rip), %rsi
        lea       88+_P4(%rip), %r8
        lea       96+_P4(%rip), %r11
        lea       56+_P4(%rip), %r9
        lea       8+_P4(%rip), %rdi
        lea       24+_P4(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       32+_P4(%rip), %rdx
        movsd     (%r8), %xmm1
        lea       16+_P4(%rip), %r8
        movsd     (%r11), %xmm5
        subsd     (%rax), %xmm0
        lea       64+_P4(%rip), %rax
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r9), %xmm1
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r10), %xmm1
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rdi), %xmm6
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm6
        lea       80+_P4(%rip), %rsi
        lea       48+_P4(%rip), %rdi
        lea       _P4(%rip), %rcx
        addsd     %xmm1, %xmm6
        addsd     (%rcx), %xmm5
        movsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.15:
        pxor      %xmm3, %xmm3
        lea       104+_P5(%rip), %rax
        cvtss2sd  48(%rsp), %xmm3
        lea       96+_P5(%rip), %rdx
        lea       64+_P5(%rip), %rcx
        lea       80+_P5(%rip), %rdi
        lea       88+_P5(%rip), %r11
        lea       48+_P5(%rip), %r8
        lea       32+_P5(%rip), %rsi
        lea       16+_P5(%rip), %r9
        lea       _P5(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       24+_P5(%rip), %rdx
        movsd     (%rdi), %xmm0
        lea       8+_P5(%rip), %rdi
        movsd     (%r11), %xmm5
        subsd     (%rax), %xmm3
        lea       56+_P5(%rip), %rax
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r8), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r9), %xmm0
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm0, %xmm6
        mulsd     %xmm3, %xmm5
        addsd     (%r10), %xmm6
        lea       72+_P5(%rip), %rcx
        lea       40+_P5(%rip), %rsi
        movsd     (%rcx), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.16:
        pxor      %xmm0, %xmm0
        lea       112+_P6(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        lea       104+_P6(%rip), %rdx
        lea       72+_P6(%rip), %rcx
        lea       40+_P6(%rip), %rsi
        lea       88+_P6(%rip), %r8
        lea       96+_P6(%rip), %r11
        lea       56+_P6(%rip), %r9
        lea       8+_P6(%rip), %rdi
        lea       24+_P6(%rip), %r10
        movsd     (%rdx), %xmm6
        lea       32+_P6(%rip), %rdx
        movsd     (%r8), %xmm1
        lea       16+_P6(%rip), %r8
        movsd     (%r11), %xmm5
        subsd     (%rax), %xmm0
        lea       64+_P6(%rip), %rax
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r9), %xmm1
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r10), %xmm1
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     (%rdi), %xmm6
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm6
        lea       80+_P6(%rip), %rsi
        lea       48+_P6(%rip), %rdi
        lea       _P6(%rip), %rcx
        addsd     %xmm1, %xmm6
        addsd     (%rcx), %xmm5
        movsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.17:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.24
..B1.18:
        testl     %eax, %eax
        je        ..B1.23
..B1.19:
        testl     $-2147483648, %edx
        pxor      %xmm0, %xmm0
        je        ..B1.22
..B1.20:
        movss     .L_2il0floatpacket.1(%rip), %xmm1
        mulss     %xmm1, %xmm0
..B1.22:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.23:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.24:
        movss     48(%rsp), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.25:
        pxor      %xmm0, %xmm0
        lea       _val_16_0(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        movsd     .L_2il0floatpacket.2(%rip), %xmm1
        lea       (%rsp), %rdi
        divsd     %xmm0, %xmm1
        movsd     (%rax), %xmm2
        lea       8(%rsp), %rsi
        mulsd     %xmm1, %xmm2
        movl      $-3, %edx
        movsd     %xmm2, 8(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm2, 24(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm1, 16(%rsi)
        movsd     %xmm2, 32(%rsi)
..___tag_value_y1f.26:
        call      __libm_sincos_k32@PLT
..___tag_value_y1f.27:
..B1.26:
        lea       40+_QP(%rip), %r10
        lea       40+_PP(%rip), %rdx
        movsd     40(%rsp), %xmm2
        lea       24+_QP(%rip), %r11
        lea       24+_PP(%rip), %rcx
        lea       32+_PP(%rip), %rdi
        lea       _tonpi(%rip), %rax
        lea       16+_PP(%rip), %r8
        movsd     (%r10), %xmm5
        lea       8+_PP(%rip), %rsi
        movsd     (%rdx), %xmm6
        lea       32+_QP(%rip), %rdx
        mulsd     %xmm2, %xmm5
        lea       _PP(%rip), %r9
        mulsd     %xmm2, %xmm6
        addsd     (%r11), %xmm5
        addsd     (%rcx), %xmm6
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm6
        movsd     (%rdx), %xmm4
        lea       16+_QP(%rip), %rcx
        mulsd     %xmm2, %xmm4
        addsd     (%rsi), %xmm6
        movsd     (%rdi), %xmm1
        lea       _QP(%rip), %rsi
        mulsd     %xmm2, %xmm1
        addsd     (%rcx), %xmm4
        movsd     (%rax), %xmm0
        lea       8+_QP(%rip), %rax
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm1
        mulsd     24(%rsp), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm1
        addsd     (%rsi), %xmm4
        sqrtsd    %xmm0, %xmm0
        addsd     (%r9), %xmm1
        movsd     32(%rsp), %xmm3
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm6
        mulsd     16(%rsp), %xmm5
        mulsd     (%rsp), %xmm6
        mulsd     8(%rsp), %xmm5
        addsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	y1f,@function
	.size	y1f,.-y1f
	.data
# -- End  y1f
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x7f800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_P1:
	.long	3588334709
	.long	1014823416
	.long	1143617537
	.long	1071688264
	.long	543393268
	.long	3216922360
	.long	1079832790
	.long	3214987951
	.long	1415988155
	.long	3212027618
	.long	2493495630
	.long	1065248537
	.long	1666720954
	.long	3211084359
	.long	543676354
	.long	1062304274
	.long	4191589146
	.long	3208689738
	.long	2170605073
	.long	1060053158
	.long	3542706363
	.long	3206309752
	.long	3704389215
	.long	1057513307
	.long	2063423420
	.long	3203511534
	.long	3202091398
	.long	1054023270
	.long	2674374220
	.long	3198818668
	.long	2780141937
	.long	1047561374
	.long	3571446339
	.long	1073845182
	.type	_P1,@object
	.size	_P1,136
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_Q2:
	.long	4294700894
	.long	1071644671
	.long	4245872852
	.long	3215982591
	.long	2793978972
	.long	1063605588
	.long	1731774639
	.long	3205263755
	.long	824990963
	.long	1051113520
	.long	1573521749
	.long	3191272273
	.type	_Q2,@object
	.size	_Q2,48
	.align 4
_Q1:
	.long	330682653
	.long	3217627238
	.long	1311559603
	.long	1068225431
	.long	1711886415
	.long	3211277751
	.long	3301707240
	.long	1058195349
	.long	1381458166
	.long	3199245881
	.long	361785213
	.long	1044511851
	.type	_Q1,@object
	.size	_Q1,48
	.align 4
_P2:
	.long	2989758108
	.long	1017408064
	.long	1458640109
	.long	3218458565
	.long	2240523794
	.long	1067453343
	.long	773815536
	.long	1068111321
	.long	2743923681
	.long	3211856434
	.long	3232814431
	.long	3210853997
	.long	2451601872
	.long	1059272649
	.long	1805313442
	.long	1057512342
	.long	1838988672
	.long	3200779931
	.long	3139341808
	.long	3198309937
	.long	844655003
	.long	1046186753
	.long	3216852589
	.long	1043859826
	.long	2659303056
	.long	3187506951
	.long	2880143704
	.long	1034033463
	.long	1317515310
	.long	1075165182
	.type	_P2,@object
	.size	_P2,120
	.align 4
_P3:
	.long	280256326
	.long	3163962304
	.long	1070207352
	.long	1070686105
	.long	878012560
	.long	3213896505
	.long	4238571140
	.long	3215341917
	.long	3113338666
	.long	1063505618
	.long	910161668
	.long	1063287399
	.long	1478416875
	.long	3206238429
	.long	3015160168
	.long	3204904859
	.long	3974776004
	.long	1052866426
	.long	579855125
	.long	1050851686
	.long	261378128
	.long	3193590641
	.long	798233178
	.long	3191027115
	.long	1498363474
	.long	1038832298
	.long	2925619636
	.long	1075917095
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	738390343
	.long	1012897491
	.long	2706267969
	.long	3217932622
	.long	4014538048
	.long	1065632415
	.long	3590329970
	.long	1067673559
	.long	671215353
	.long	3210335979
	.long	2374781669
	.long	3210593529
	.long	1890699418
	.long	1058215403
	.long	1890155166
	.long	1057321344
	.long	1713507622
	.long	3199871308
	.long	2862637523
	.long	3198281783
	.long	1122846306
	.long	1045733660
	.long	622299794
	.long	1043564297
	.long	2771483851
	.long	3185854918
	.long	1632198108
	.long	3183235242
	.long	953430534
	.long	1076330385
	.type	_P4,@object
	.size	_P4,120
	.align 4
_P5:
	.long	342107947
	.long	1017254688
	.long	3187585117
	.long	1070231586
	.long	1823227594
	.long	3212600587
	.long	444207028
	.long	3215024921
	.long	1802122913
	.long	1062365956
	.long	1966738039
	.long	1062934367
	.long	3649894624
	.long	3205237410
	.long	1726294277
	.long	3204703097
	.long	1880085380
	.long	1051980794
	.long	1949762981
	.long	1050731607
	.long	2695921962
	.long	3192939649
	.long	1096030507
	.long	3190939319
	.long	3356663664
	.long	1038219909
	.long	2296284299
	.long	1076743037
	.type	_P5,@object
	.size	_P5,112
	.align 4
_P6:
	.long	1104511903
	.long	3165887820
	.long	3273918356
	.long	3217557377
	.long	1767833442
	.long	1064652506
	.long	4164235278
	.long	1067433581
	.long	3499046825
	.long	3209421592
	.long	1038038689
	.long	3210276846
	.long	3702826228
	.long	1057329476
	.long	3651609877
	.long	1057129169
	.long	1779967631
	.long	3199161987
	.long	3308825176
	.long	3198147781
	.long	897891533
	.long	1045052198
	.long	2444111938
	.long	1043454098
	.long	144575532
	.long	3185257015
	.long	1701846534
	.long	3183129264
	.long	1767841339
	.long	1077021468
	.type	_P6,@object
	.size	_P6,120
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_QP:
	.long	4294954956
	.long	1066926079
	.long	4141388976
	.long	3204071423
	.long	1500123100
	.long	1049740228
	.long	3264213437
	.long	3191856517
	.long	1489731078
	.long	1039824630
	.long	1807021260
	.long	3183130166
	.type	_QP,@object
	.size	_QP,48
	.align 4
_PP:
	.long	551
	.long	1072693248
	.long	4267608614
	.long	1061027839
	.long	3690881252
	.long	3200414971
	.long	3933039373
	.long	1046848465
	.long	3246008603
	.long	3189439465
	.long	1689113401
	.long	1037483563
	.type	_PP,@object
	.size	_PP,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
