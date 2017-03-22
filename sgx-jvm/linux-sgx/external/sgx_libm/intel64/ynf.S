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
	.file "ynf.c"
	.text
..TXTST0:
# -- Begin  ynf
	.text
       .align    16,0x90
	.globl ynf
ynf:
# parameter 1: %edi
# parameter 2: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_ynf.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        subq      $72, %rsp
	.cfi_def_cfa_offset 96
        movl      %edi, %ebx
        movd      %xmm0, %ecx
        movss     %xmm0, (%rsp)
        lea       -1(%rcx), %esi
        cmpl      $2139095039, %esi
        jae       ..B1.48
..B1.2:
        testl     %ebx, %ebx
        je        ..B1.59
..B1.3:
        movl      %ecx, %ebp
        movl      %ebx, %eax
        cltd      
        xorl      %ebx, %ebp
        shrl      $31, %ebp
        andl      %ebx, %ebp
        xorl      %edx, %ebx
        subl      %edx, %ebx
        cmpl      $1, %ebx
        je        ..B1.58
..B1.4:
        movl      %ebx, %edi
        sarl      $3, %edi
        cmpl      $320, %edi
        jg        ..B1.6
..B1.5:
        movslq    %edi, %rdi
        lea       function_zeros(%rip), %rsi
        movss     (%rsi,%rdi,4), %xmm0
        jmp       ..B1.7
..B1.6:
        pxor      %xmm0, %xmm0
        lea       -320(%rdi), %esi
        lea       -2560(,%rdi,8), %edi
        subl      %esi, %edi
        cvtsi2ss  %edi, %xmm0
        addss     .L_2il0floatpacket.0(%rip), %xmm0
..B1.7:
        movss     (%rsp), %xmm3
        comiss    %xmm3, %xmm0
        ja        ..B1.47
..B1.8:
        cvtss2sd  %xmm3, %xmm3
        cmpl      $6, %ebx
        jge       ..B1.26
..B1.9:
        lea       _val_0_6(%rip), %rcx
        movsd     (%rcx), %xmm1
        comisd    %xmm3, %xmm1
        jb        ..B1.27
..B1.10:
        lea       _val_2_0(%rip), %rsi
        pxor      %xmm5, %xmm5
        lea       _zeros(%rip), %rcx
        movaps    %xmm3, %xmm6
        pxor      %xmm4, %xmm4
        testl     %ebx, %ebx
        pxor      %xmm1, %xmm1
        movsd     (%rsi), %xmm7
        lea       _ones(%rip), %rsi
        movsd     %xmm1, 8(%rsp)
        divsd     %xmm7, %xmm6
        cvtss2sd  (%rsi), %xmm5
        cvtss2sd  (%rcx), %xmm4
        divsd     %xmm3, %xmm7
        movsd     %xmm5, (%rsp)
        movaps    %xmm5, %xmm2
        movaps    %xmm5, %xmm1
        movaps    %xmm5, %xmm8
        movl      $0, %ecx
        jle       ..B1.14
..B1.11:
        movaps    %xmm1, %xmm9
..B1.12:
        incl      %ecx
        mulsd     %xmm6, %xmm1
        cmpl      %ebx, %ecx
        mulsd     %xmm7, %xmm9
        jb        ..B1.12
..B1.13:
        movsd     %xmm9, (%rsp)
        testl     %ebx, %ebx
..B1.14:
        movl      $0, %ecx
        jle       ..B1.18
..B1.16:
        movaps    %xmm5, %xmm7
        incl      %ecx
        divsd     %xmm8, %xmm7
        movaps    %xmm2, %xmm9
        cmpl      %ebx, %ecx
        mulsd     %xmm8, %xmm2
        addsd     %xmm7, %xmm4
        addsd     %xmm5, %xmm8
        jb        ..B1.16
..B1.17:
        movsd     %xmm9, 8(%rsp)
..B1.18:
        mulsd     %xmm6, %xmm6
        lea       _TWO_19H(%rip), %rcx
        movsd     %xmm5, 32(%rsp)
        movaps    %xmm4, %xmm8
        movsd     %xmm4, 64(%rsp)
        xorl      %r8d, %r8d
        xorps     .L_2il0floatpacket.3(%rip), %xmm6
        movl      %ebx, %edi
        movsd     (%rcx), %xmm7
        movl      %ebx, %ecx
..B1.19:
        incl      %r8d
        incl      %edi
        movl      %r8d, %r9d
        movaps    %xmm8, %xmm13
        imull     %edi, %r9d
        pxor      %xmm8, %xmm8
        movss     (%rsi), %xmm9
        pxor      %xmm10, %xmm10
        movsd     32(%rsp), %xmm11
        movaps    %xmm7, %xmm12
        mulsd     %xmm6, %xmm11
        cvtsi2ss  %r9d, %xmm8
        divss     %xmm8, %xmm9
        addl      $2, %ecx
        cvtsi2sd  %ecx, %xmm10
        cvtss2sd  %xmm9, %xmm9
        mulsd     %xmm9, %xmm10
        mulsd     %xmm9, %xmm11
        addsd     %xmm10, %xmm4
        addsd     %xmm11, %xmm5
        movsd     %xmm11, 32(%rsp)
        mulsd     %xmm4, %xmm11
        addsd     64(%rsp), %xmm11
        mulsd     %xmm11, %xmm12
        movsd     %xmm11, 64(%rsp)
        addsd     %xmm12, %xmm11
        movsd     %xmm11, 56(%rsp)
        movsd     56(%rsp), %xmm8
        subsd     %xmm12, %xmm8
        ucomisd   %xmm13, %xmm8
        jne       ..B1.19
        jp        ..B1.19
..B1.20:
        divsd     %xmm2, %xmm1
        lea       _eulon2(%rip), %rcx
        movsd     %xmm1, 32(%rsp)
        mulsd     %xmm1, %xmm5
        movsd     (%rcx), %xmm1
        mulsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm0
        movsd     %xmm5, 16(%rsp)
        movsd     %xmm6, 24(%rsp)
..___tag_value_ynf.8:
        call      __libm_log_k32@PLT
..___tag_value_ynf.9:
..B1.62:
        movsd     24(%rsp), %xmm6
        movaps    %xmm0, %xmm1
        movsd     16(%rsp), %xmm5
..B1.21:
        lea       _tonpi(%rip), %rsi
        lea       _oonpi(%rip), %rcx
        xorps     .L_2il0floatpacket.3(%rip), %xmm6
        cmpl      $1, %ebx
        movsd     (%rsi), %xmm0
        movl      $0, %esi
        mulsd     %xmm5, %xmm0
        movsd     (%rcx), %xmm2
        lea       -1(%rbx), %ecx
        mulsd     %xmm1, %xmm0
        movsd     64(%rsp), %xmm1
        mulsd     %xmm2, %xmm1
        mulsd     32(%rsp), %xmm1
        subsd     %xmm1, %xmm0
        movsd     8(%rsp), %xmm1
        jle       ..B1.25
..B1.22:
        movaps    %xmm1, %xmm4
        decl      %ebx
..B1.23:
        incl      %esi
        lea       -1(%rcx), %edi
        imull     %esi, %ecx
        mulsd     %xmm6, %xmm4
        cvtsi2sd  %ecx, %xmm3
        divsd     %xmm3, %xmm4
        movl      %edi, %ecx
        cmpl      %ebx, %esi
        addsd     %xmm4, %xmm1
        jb        ..B1.23
..B1.25:
        mulsd     %xmm2, %xmm1
        mulsd     (%rsp), %xmm1
        subsd     %xmm1, %xmm0
        jmp       ..B1.46
..B1.26:
        cmpl      $80, %ebx
        jge       ..B1.35
..B1.27:
        lea       _val_18_0(%rip), %rcx
        movsd     (%rcx), %xmm1
        comisd    %xmm3, %xmm1
        jbe       ..B1.29
..B1.28:
        movaps    %xmm3, %xmm0
        movsd     %xmm3, 48(%rsp)
        call      y0@PLT
..B1.64:
        movsd     48(%rsp), %xmm3
        movaps    %xmm0, %xmm1
        movaps    %xmm3, %xmm0
        movsd     %xmm1, (%rsp)
        call      y1@PLT
..B1.63:
        movsd     48(%rsp), %xmm3
        movsd     (%rsp), %xmm1
        jmp       ..B1.31
..B1.29:
        lea       _ones(%rip), %rcx
        pxor      %xmm1, %xmm1
        lea       _val_16_0(%rip), %r8
        lea       (%rsp), %rdi
        movaps    %xmm3, %xmm0
        lea       8(%rsp), %rsi
        movl      $-1, %edx
        cvtss2sd  (%rcx), %xmm1
        divsd     %xmm3, %xmm1
        movsd     (%r8), %xmm8
        mulsd     %xmm1, %xmm8
        movaps    %xmm8, %xmm6
        mulsd     %xmm8, %xmm6
        movaps    %xmm6, %xmm7
        mulsd     %xmm6, %xmm7
        movq      $0, 56(%rsi)
        movsd     %xmm1, 8(%rsi)
        movsd     %xmm6, 16(%rsi)
        movsd     %xmm7, 24(%rsi)
        movsd     %xmm8, 32(%rsi)
        movsd     %xmm3, 40(%rsi)
..___tag_value_ynf.10:
        call      __libm_sincos_k32@PLT
..___tag_value_ynf.11:
..B1.30:
        lea       40+_PP1(%rip), %r11
        lea       40+_PP0(%rip), %rcx
        movsd     32(%rsp), %xmm7
        lea       24+_PP0(%rip), %rsi
        lea       8+_PP0(%rip), %rdi
        lea       32+_PP0(%rip), %r8
        movsd     24(%rsp), %xmm6
        lea       16+_PP0(%rip), %r9
        movsd     (%r11), %xmm11
        lea       _PP0(%rip), %r10
        movsd     (%rcx), %xmm1
        lea       24+_PP1(%rip), %rcx
        movsd     (%r8), %xmm2
        movsd     40(%rsp), %xmm8
        mulsd     %xmm7, %xmm11
        mulsd     %xmm7, %xmm1
        mulsd     %xmm7, %xmm2
        addsd     (%rcx), %xmm11
        addsd     (%rsi), %xmm1
        addsd     (%r9), %xmm2
        mulsd     %xmm7, %xmm11
        mulsd     %xmm7, %xmm1
        mulsd     %xmm7, %xmm2
        addsd     (%rdi), %xmm1
        addsd     (%r10), %xmm2
        mulsd     %xmm6, %xmm1
        lea       8+_PP1(%rip), %rcx
        addsd     %xmm2, %xmm1
        addsd     (%rcx), %xmm11
        lea       32+_PP1(%rip), %rcx
        mulsd     %xmm6, %xmm11
        movsd     (%rcx), %xmm4
        lea       16+_PP1(%rip), %rcx
        mulsd     %xmm7, %xmm4
        movsd     48(%rsp), %xmm3
        addsd     (%rcx), %xmm4
        lea       _PP1(%rip), %rcx
        mulsd     %xmm7, %xmm4
        addsd     (%rcx), %xmm4
        lea       40+_QP0(%rip), %rcx
        movsd     (%rcx), %xmm9
        lea       24+_QP0(%rip), %rcx
        mulsd     %xmm7, %xmm9
        addsd     %xmm4, %xmm11
        addsd     (%rcx), %xmm9
        lea       8+_QP0(%rip), %rcx
        mulsd     %xmm7, %xmm9
        addsd     (%rcx), %xmm9
        lea       32+_QP0(%rip), %rcx
        movsd     (%rcx), %xmm5
        lea       16+_QP0(%rip), %rcx
        mulsd     %xmm7, %xmm5
        mulsd     %xmm6, %xmm9
        addsd     (%rcx), %xmm5
        mulsd     %xmm8, %xmm9
        mulsd     %xmm7, %xmm5
        lea       _QP0(%rip), %rcx
        addsd     (%rcx), %xmm5
        lea       40+_QP1(%rip), %rcx
        movsd     (%rcx), %xmm10
        lea       24+_QP1(%rip), %rcx
        mulsd     %xmm7, %xmm10
        mulsd     %xmm8, %xmm5
        addsd     (%rcx), %xmm10
        addsd     %xmm5, %xmm9
        mulsd     %xmm7, %xmm10
        lea       8+_QP1(%rip), %rcx
        addsd     (%rcx), %xmm10
        lea       32+_QP1(%rip), %rcx
        mulsd     %xmm6, %xmm10
        movsd     (%rcx), %xmm6
        lea       16+_QP1(%rip), %rcx
        mulsd     %xmm7, %xmm6
        mulsd     %xmm8, %xmm10
        addsd     (%rcx), %xmm6
        mulsd     %xmm7, %xmm6
        lea       _QP1(%rip), %rcx
        movsd     8(%rsp), %xmm7
        mulsd     %xmm7, %xmm9
        addsd     (%rcx), %xmm6
        mulsd     %xmm11, %xmm7
        mulsd     %xmm8, %xmm6
        lea       _tonpi(%rip), %rcx
        addsd     %xmm6, %xmm10
        movsd     (%rcx), %xmm0
        mulsd     16(%rsp), %xmm0
        movsd     (%rsp), %xmm8
        sqrtsd    %xmm0, %xmm0
        mulsd     %xmm8, %xmm1
        mulsd     %xmm10, %xmm8
        addsd     %xmm9, %xmm1
        subsd     %xmm7, %xmm8
        mulsd     %xmm0, %xmm1
        mulsd     %xmm8, %xmm0
..B1.31:
        lea       _val_2_0(%rip), %rcx
        cmpl      $1, %ebx
        movsd     (%rcx), %xmm2
        movl      $0, %ecx
        divsd     %xmm3, %xmm2
        jle       ..B1.46
..B1.32:
        decl      %ebx
..B1.33:
        incl      %ecx
        pxor      %xmm3, %xmm3
        cvtsi2sd  %ecx, %xmm3
        mulsd     %xmm2, %xmm3
        movaps    %xmm0, %xmm4
        mulsd     %xmm0, %xmm3
        cmpl      %ebx, %ecx
        movaps    %xmm3, %xmm0
        subsd     %xmm1, %xmm0
        movaps    %xmm4, %xmm1
        jb        ..B1.33
        jmp       ..B1.46
..B1.35:
        pxor      %xmm1, %xmm1
        lea       5000(%rbx,%rbx,4), %ecx
        cvtsi2sd  %ecx, %xmm1
        comisd    %xmm3, %xmm1
        ja        ..B1.27
..B1.36:
        pxor      %xmm1, %xmm1
        lea       _val_4_0(%rip), %rsi
        cvtsi2sd  %ebx, %xmm1
        movsd     (%rsi), %xmm15
        pxor      %xmm2, %xmm2
        movaps    %xmm15, %xmm14
        lea       _val_5_0(%rip), %rsi
        mulsd     %xmm1, %xmm14
        lea       4+_ones(%rip), %r9
        cvtss2sd  (%r9), %xmm2
        mulsd     %xmm1, %xmm14
        lea       _val_8_0(%rip), %rdi
        addsd     %xmm14, %xmm2
        movsd     (%rdi), %xmm11
        pxor      %xmm1, %xmm1
        mulsd     %xmm3, %xmm11
        lea       _ones(%rip), %r8
        cvtss2sd  (%r8), %xmm1
        divsd     %xmm11, %xmm2
        lea       _val_2_0(%rip), %r10
        lea       _val_3_0(%rip), %r11
        movaps    %xmm1, %xmm12
        movaps    %xmm1, %xmm4
        movsd     %xmm2, 32(%rsp)
        movaps    %xmm1, %xmm13
        movsd     (%rsi), %xmm6
        movl      $-1, %ecx
        movsd     (%r10), %xmm10
        xorb      %sil, %sil
        movsd     (%r11), %xmm8
        movaps    %xmm10, %xmm9
        divsd     %xmm1, %xmm12
        movaps    %xmm8, %xmm7
..B1.37:
        movsd     %xmm4, 64(%rsp)
        movaps    %xmm7, %xmm4
        mulsd     %xmm7, %xmm4
        movaps    %xmm14, %xmm5
        movaps    %xmm6, %xmm0
        testl     %ecx, %ecx
        mulsd     %xmm6, %xmm0
        subsd     %xmm4, %xmm5
        movaps    %xmm9, %xmm4
        mulsd     %xmm11, %xmm4
        divsd     %xmm4, %xmm5
        movaps    %xmm14, %xmm4
        mulsd     32(%rsp), %xmm5
        subsd     %xmm0, %xmm4
        movaps    %xmm11, %xmm0
        mulsd     %xmm8, %xmm0
        divsd     %xmm0, %xmm4
        mulsd     %xmm5, %xmm4
        jle       ..B1.57
..B1.38:
        addsd     %xmm5, %xmm1
        addsd     %xmm4, %xmm2
..B1.39:
        movsd     %xmm4, 32(%rsp)
        addsd     %xmm10, %xmm9
        mulsd     %xmm12, %xmm4
        addsd     %xmm10, %xmm8
        addsd     %xmm15, %xmm7
        addsd     %xmm15, %xmm6
        andps     .L_2il0floatpacket.2(%rip), %xmm4
        comisd    %xmm4, %xmm13
        jbe       ..B1.41
..B1.40:
        movaps    %xmm4, %xmm13
        movb      $1, %sil
        jmp       ..B1.42
..B1.41:
        testb     %sil, %sil
        jne       ..B1.44
..B1.42:
        lea       _TWO_19H(%rip), %rdi
        movaps    %xmm1, %xmm4
        negl      %ecx
        movsd     (%rdi), %xmm5
        mulsd     %xmm1, %xmm5
        addsd     %xmm5, %xmm4
        movsd     %xmm4, 56(%rsp)
        movsd     56(%rsp), %xmm4
        subsd     %xmm5, %xmm4
        ucomisd   64(%rsp), %xmm4
        jne       ..B1.37
        jp        ..B1.37
..B1.44:
        addl      %ebx, %ebx
        lea       64(%rsp), %rdi
        negl      %ebx
        lea       32(%rsp), %rsi
        decl      %ebx
        movaps    %xmm3, %xmm0
        movl      %ebx, %edx
        movsd     %xmm2, (%rsp)
        movsd     %xmm1, -24(%rsi)
        movsd     %xmm3, 16(%rsi)
..___tag_value_ynf.12:
        call      __libm_sincos_k32@PLT
..___tag_value_ynf.13:
..B1.45:
        lea       _tonpi(%rip), %rax
        movsd     48(%rsp), %xmm3
        movsd     8(%rsp), %xmm1
        movsd     (%rsp), %xmm2
        movsd     (%rax), %xmm0
        divsd     %xmm3, %xmm0
        sqrtsd    %xmm0, %xmm0
        mulsd     64(%rsp), %xmm1
        mulsd     32(%rsp), %xmm2
        addsd     %xmm2, %xmm1
        mulsd     %xmm1, %xmm0
..B1.46:
        movaps    %xmm0, %xmm1
        testl     %ebp, %ebp
        xorps     .L_2il0floatpacket.3(%rip), %xmm1
        jne       ..L14
        movaps    %xmm0, %xmm1
..L14:
        cvtsd2ss  %xmm1, %xmm1
        movaps    %xmm1, %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.47:
        lea       _large_value_32(%rip), %rax
        shrl      $31, %ecx
        movss     (%rax,%rcx,4), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.48:
        movl      %ecx, %ebp
        andl      $2147483647, %ebp
        cmpl      $2139095040, %ebp
        ja        ..B1.55
..B1.49:
        testl     %ebp, %ebp
        je        ..B1.54
..B1.50:
        testl     $-2147483648, %ecx
        je        ..B1.52
..B1.51:
        lea       _zeros(%rip), %rax
        lea       _infs(%rip), %rdx
        movss     (%rax), %xmm0
        mulss     (%rdx), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.52:
        lea       _zeros(%rip), %rax
        movl      %ebx, %ecx
        shrl      $31, %ecx
        andl      %ebx, %ecx
        movss     (%rax,%rcx,4), %xmm0
..B1.53:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.54:
        lea       4+_ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        movss     (%rax), %xmm0
        divss     (%rdx), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.55:
        lea       _ones(%rip), %rax
        movss     (%rsp), %xmm0
        mulss     (%rax), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.57:
        subsd     %xmm5, %xmm1
        subsd     %xmm4, %xmm2
        jmp       ..B1.39
..B1.58:
        movss     (%rsp), %xmm0
        call      y1f@PLT
..B1.65:
        movaps    %xmm0, %xmm1
        testl     %ebp, %ebp
        xorps     .L_2il0floatpacket.4(%rip), %xmm1
        je        ..L63
        movaps    %xmm1, %xmm0
..L63:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.59:
        movss     (%rsp), %xmm0
        call      y0f@PLT
..B1.66:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	ynf,@function
	.size	ynf,.-ynf
	.data
# -- End  ynf
	.data
	.align 4
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_infs:
	.long	2139095040
	.long	4286578688
	.type	_infs,@object
	.size	_infs,8
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.long	0x80000000,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 4
.L_2il0floatpacket.0:
	.long	0x450e6000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
function_zeros:
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x3ecccccd
	.long	0x3fa66666
	.long	0x40400000
	.long	0x40a9999a
	.long	0x4101999a
	.long	0x41366666
	.long	0x4171999a
	.long	0x4198cccd
	.long	0x41ba6666
	.long	0x41df3333
	.long	0x42026666
	.long	0x42160000
	.long	0x422a0000
	.long	0x423f3333
	.long	0x42546666
	.long	0x426a0000
	.long	0x42803333
	.long	0x428b999a
	.long	0x42970000
	.long	0x42a2999a
	.long	0x42ae6666
	.long	0x42ba6666
	.long	0x42c66666
	.long	0x42d2999a
	.long	0x42decccd
	.long	0x42eb3333
	.long	0x42f7999a
	.long	0x4302199a
	.long	0x43086666
	.long	0x430ecccd
	.long	0x43153333
	.long	0x431b999a
	.long	0x4322199a
	.long	0x4328999a
	.long	0x432f199a
	.long	0x4335b333
	.long	0x433c3333
	.long	0x4342e666
	.long	0x43498000
	.long	0x43503333
	.long	0x4356e666
	.long	0x435d999a
	.long	0x43644ccd
	.long	0x436b0000
	.long	0x4371cccd
	.long	0x4378999a
	.long	0x437f6666
	.long	0x4383199a
	.long	0x43868ccd
	.long	0x4389f333
	.long	0x438d6666
	.long	0x4390d99a
	.long	0x43944ccd
	.long	0x4397c000
	.long	0x439b3333
	.long	0x439eb333
	.long	0x43a23333
	.long	0x43a5a666
	.long	0x43a92666
	.long	0x43aca666
	.long	0x43b02666
	.long	0x43b3a666
	.long	0x43b73333
	.long	0x43bab333
	.long	0x43be4000
	.long	0x43c1c000
	.long	0x43c54ccd
	.long	0x43c8d99a
	.long	0x43cc6666
	.long	0x43cff333
	.long	0x43d38000
	.long	0x43d70ccd
	.long	0x43da999a
	.long	0x43de2666
	.long	0x43e1c000
	.long	0x43e54ccd
	.long	0x43e8e666
	.long	0x43ec8000
	.long	0x43f00ccd
	.long	0x43f3a666
	.long	0x43f74000
	.long	0x43fad99a
	.long	0x43fe7333
	.long	0x44010666
	.long	0x4402d333
	.long	0x4404a000
	.long	0x44067333
	.long	0x44084000
	.long	0x440a0ccd
	.long	0x440be000
	.long	0x440daccd
	.long	0x440f8000
	.long	0x44114ccd
	.long	0x44132000
	.long	0x4414f333
	.long	0x4416c666
	.long	0x4418999a
	.long	0x441a6666
	.long	0x441c399a
	.long	0x441e0ccd
	.long	0x441fe000
	.long	0x4421b333
	.long	0x44238ccd
	.long	0x44256000
	.long	0x44273333
	.long	0x44290666
	.long	0x442ae000
	.long	0x442cb333
	.long	0x442e8666
	.long	0x44306000
	.long	0x44323333
	.long	0x44340ccd
	.long	0x4435e000
	.long	0x4437b99a
	.long	0x44398ccd
	.long	0x443b6666
	.long	0x443d4000
	.long	0x443f199a
	.long	0x4440eccd
	.long	0x4442c666
	.long	0x4444a000
	.long	0x4446799a
	.long	0x44485333
	.long	0x444a2ccd
	.long	0x444c0666
	.long	0x444de000
	.long	0x444fb99a
	.long	0x44519333
	.long	0x44536ccd
	.long	0x44554666
	.long	0x44572000
	.long	0x44590000
	.long	0x445ad99a
	.long	0x445cb333
	.long	0x445e9333
	.long	0x44606ccd
	.long	0x44624666
	.long	0x44642666
	.long	0x44660000
	.long	0x4467d99a
	.long	0x4469b99a
	.long	0x446b9333
	.long	0x446d7333
	.long	0x446f5333
	.long	0x44712ccd
	.long	0x44730ccd
	.long	0x4474e666
	.long	0x4476c666
	.long	0x4478a666
	.long	0x447a8000
	.long	0x447c6000
	.long	0x447e4000
	.long	0x44801000
	.long	0x4480fccd
	.long	0x4481eccd
	.long	0x4482dccd
	.long	0x4483cccd
	.long	0x4484bccd
	.long	0x4485accd
	.long	0x44869ccd
	.long	0x44878ccd
	.long	0x44887ccd
	.long	0x44896ccd
	.long	0x448a5ccd
	.long	0x448b4ccd
	.long	0x448c3ccd
	.long	0x448d2ccd
	.long	0x448e1ccd
	.long	0x448f0ccd
	.long	0x448ffccd
	.long	0x4490eccd
	.long	0x4491e000
	.long	0x4492d000
	.long	0x4493c000
	.long	0x4494b000
	.long	0x4495a333
	.long	0x44969333
	.long	0x44978333
	.long	0x44987333
	.long	0x44996666
	.long	0x449a5666
	.long	0x449b499a
	.long	0x449c399a
	.long	0x449d299a
	.long	0x449e1ccd
	.long	0x449f0ccd
	.long	0x44a00000
	.long	0x44a0f000
	.long	0x44a1e333
	.long	0x44a2d333
	.long	0x44a3c666
	.long	0x44a4b666
	.long	0x44a5a99a
	.long	0x44a6999a
	.long	0x44a78ccd
	.long	0x44a87ccd
	.long	0x44a97000
	.long	0x44aa6333
	.long	0x44ab5333
	.long	0x44ac4666
	.long	0x44ad399a
	.long	0x44ae299a
	.long	0x44af1ccd
	.long	0x44b01000
	.long	0x44b10333
	.long	0x44b1f333
	.long	0x44b2e666
	.long	0x44b3d99a
	.long	0x44b4cccd
	.long	0x44b5bccd
	.long	0x44b6b000
	.long	0x44b7a333
	.long	0x44b89666
	.long	0x44b9899a
	.long	0x44ba7ccd
	.long	0x44bb6ccd
	.long	0x44bc6000
	.long	0x44bd5333
	.long	0x44be4666
	.long	0x44bf399a
	.long	0x44c02ccd
	.long	0x44c12000
	.long	0x44c21333
	.long	0x44c30666
	.long	0x44c3f99a
	.long	0x44c4eccd
	.long	0x44c5e000
	.long	0x44c6d333
	.long	0x44c7c666
	.long	0x44c8b99a
	.long	0x44c9accd
	.long	0x44caa000
	.long	0x44cb9333
	.long	0x44cc899a
	.long	0x44cd7ccd
	.long	0x44ce7000
	.long	0x44cf6333
	.long	0x44d05666
	.long	0x44d1499a
	.long	0x44d23ccd
	.long	0x44d33333
	.long	0x44d42666
	.long	0x44d5199a
	.long	0x44d60ccd
	.long	0x44d70000
	.long	0x44d7f666
	.long	0x44d8e99a
	.long	0x44d9dccd
	.long	0x44dad000
	.long	0x44dbc666
	.long	0x44dcb99a
	.long	0x44ddaccd
	.long	0x44dea333
	.long	0x44df9666
	.long	0x44e0899a
	.long	0x44e18000
	.long	0x44e27333
	.long	0x44e36666
	.long	0x44e45ccd
	.long	0x44e55000
	.long	0x44e64333
	.long	0x44e7399a
	.long	0x44e82ccd
	.long	0x44e92333
	.long	0x44ea1666
	.long	0x44eb099a
	.long	0x44ec0000
	.long	0x44ecf333
	.long	0x44ede99a
	.long	0x44eedccd
	.long	0x44efd333
	.long	0x44f0c666
	.long	0x44f1bccd
	.long	0x44f2b000
	.long	0x44f3a666
	.long	0x44f4999a
	.long	0x44f59000
	.long	0x44f68333
	.long	0x44f7799a
	.long	0x44f86ccd
	.long	0x44f96333
	.long	0x44fa599a
	.long	0x44fb4ccd
	.long	0x44fc4333
	.long	0x44fd3666
	.long	0x44fe2ccd
	.long	0x44ff2000
	.long	0x45000b33
	.long	0x45008666
	.long	0x45010000
	.long	0x45017000
	.long	0x4501f000
	.long	0x45027000
	.long	0x4502e000
	.long	0x45036000
	.long	0x4503e000
	.long	0x45045000
	.long	0x4504d000
	.long	0x45055000
	.long	0x4505c000
	.long	0x45064000
	.long	0x4506c000
	.long	0x45073000
	.long	0x4507b000
	.long	0x45083000
	.long	0x4508a000
	.long	0x45092000
	.long	0x4509a000
	.long	0x450a1000
	.long	0x450a9000
	.long	0x450b1000
	.long	0x450b8000
	.long	0x450c0000
	.long	0x450c8000
	.long	0x450cf000
	.long	0x450d7000
	.long	0x450df000
	.long	0x450e6000
	.type	function_zeros,@object
	.size	function_zeros,1284
	.align 4
_val_0_6:
	.long	858993459
	.long	1071854387
	.type	_val_0_6,@object
	.size	_val_0_6,8
	.align 4
_val_2_0:
	.long	0
	.long	1073741824
	.type	_val_2_0,@object
	.size	_val_2_0,8
	.align 4
_TWO_19H:
	.long	0
	.long	1093140480
	.type	_TWO_19H,@object
	.size	_TWO_19H,8
	.align 4
_eulon2:
	.long	3400611180
	.long	1072463685
	.type	_eulon2,@object
	.size	_eulon2,8
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_oonpi:
	.long	1841940611
	.long	1070882608
	.type	_oonpi,@object
	.size	_oonpi,8
	.align 4
_val_18_0:
	.long	0
	.long	1077018624
	.type	_val_18_0,@object
	.size	_val_18_0,8
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_PP1:
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
	.type	_PP1,@object
	.size	_PP1,48
	.align 4
_PP0:
	.long	4294966288
	.long	1072693247
	.long	4269912424
	.long	3207725055
	.long	1780799603
	.long	1052554744
	.long	2285933488
	.long	3194113879
	.long	3892950982
	.long	1041746526
	.long	150212775
	.long	3184818833
	.type	_PP0,@object
	.size	_PP0,48
	.align 4
_QP0:
	.long	4294921804
	.long	3212836863
	.long	4153409851
	.long	1056096255
	.long	2226361937
	.long	3196916170
	.long	3849695089
	.long	1044094312
	.long	663042994
	.long	3187124278
	.long	3336948587
	.long	1035486718
	.type	_QP0,@object
	.size	_QP0,48
	.align 4
_QP1:
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
	.type	_QP1,@object
	.size	_QP1,48
	.align 4
_val_4_0:
	.long	0
	.long	1074790400
	.type	_val_4_0,@object
	.size	_val_4_0,8
	.align 4
_val_5_0:
	.long	0
	.long	1075052544
	.type	_val_5_0,@object
	.size	_val_5_0,8
	.align 4
_val_8_0:
	.long	0
	.long	1075838976
	.type	_val_8_0,@object
	.size	_val_8_0,8
	.align 4
_val_3_0:
	.long	0
	.long	1074266112
	.type	_val_3_0,@object
	.size	_val_3_0,8
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
