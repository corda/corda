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
	.file "jnf.c"
	.text
..TXTST0:
# -- Begin  jnf
	.text
       .align    16,0x90
	.globl jnf
jnf:
# parameter 1: %edi
# parameter 2: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_jnf.1:
..L2:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        subq      $72, %rsp
	.cfi_def_cfa_offset 96
        movl      %edi, %r14d
        movd      %xmm0, %r15d
        movss     %xmm0, (%rsp)
        movl      %r15d, %ecx
        andl      $2147483647, %ecx
        cmpl      $2139095040, %ecx
        jae       ..B1.41
..B1.2:
        testl     %r14d, %r14d
        je        ..B1.40
..B1.3:
        xorl      %r14d, %r15d
        shrl      $31, %r15d
        andl      %r14d, %r15d
        testl     %ecx, %ecx
        je        ..B1.39
..B1.4:
        movl      %r14d, %eax
        cltd      
        xorl      %edx, %r14d
        subl      %edx, %r14d
        andps     .L_2il0floatpacket.1(%rip), %xmm0
        cmpl      $1, %r14d
        movss     %xmm0, (%rsp)
        je        ..B1.58
..B1.5:
        movl      %r14d, %esi
        sarl      $3, %esi
        cmpl      $320, %esi
        jg        ..B1.7
..B1.6:
        movslq    %esi, %rsi
        lea       function_zeros(%rip), %rcx
        movss     (%rcx,%rsi,4), %xmm2
        jmp       ..B1.8
..B1.7:
        pxor      %xmm2, %xmm2
        lea       -320(%rsi), %ecx
        lea       -2560(,%rsi,8), %esi
        subl      %ecx, %esi
        cvtsi2ss  %esi, %xmm2
        addss     .L_2il0floatpacket.0(%rip), %xmm2
..B1.8:
        comiss    %xmm0, %xmm2
        ja        ..B1.38
..B1.9:
        pxor      %xmm1, %xmm1
        pxor      %xmm3, %xmm3
        cvtss2sd  %xmm0, %xmm1
        cvtsi2sd  %r14d, %xmm3
        cmpl      $170, %r14d
        jg        ..B1.22
..B1.10:
        lea       _val_0_6(%rip), %rcx
        movsd     (%rcx), %xmm2
        mulsd     %xmm3, %xmm2
        comisd    %xmm1, %xmm2
        jb        ..B1.22
..B1.11:
        lea       _val_2_0(%rip), %rcx
        testl     %r14d, %r14d
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        movaps    %xmm4, %xmm3
        movaps    %xmm4, %xmm0
        movaps    %xmm4, %xmm2
        divsd     (%rcx), %xmm1
        movl      $0, %ecx
        jle       ..B1.15
..B1.13:
        incl      %ecx
        mulsd     %xmm1, %xmm0
        cmpl      %r14d, %ecx
        jb        ..B1.13
..B1.14:
        testl     %r14d, %r14d
..B1.15:
        movl      $0, %ecx
        jle       ..B1.19
..B1.17:
        incl      %ecx
        mulsd     %xmm2, %xmm3
        addsd     %xmm4, %xmm2
        cmpl      %r14d, %ecx
        jb        ..B1.17
..B1.19:
        mulsd     %xmm1, %xmm1
        lea       _TWO_19H(%rip), %rsi
        movsd     %xmm4, 16(%rsp)
        xorl      %ecx, %ecx
        movsd     %xmm4, 56(%rsp)
        xorps     .L_2il0floatpacket.4(%rip), %xmm1
        movsd     (%rsi), %xmm2
        .align    16,0x90
..B1.20:
        incl      %ecx
        incl      %r14d
        movl      %ecx, %esi
        movaps    %xmm4, %xmm8
        imull     %r14d, %esi
        pxor      %xmm4, %xmm4
        movsd     16(%rsp), %xmm5
        movaps    %xmm2, %xmm7
        movsd     56(%rsp), %xmm6
        mulsd     %xmm1, %xmm5
        cvtsi2sd  %esi, %xmm4
        divsd     %xmm4, %xmm5
        movsd     %xmm5, 16(%rsp)
        addsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm7
        movsd     %xmm6, 56(%rsp)
        addsd     %xmm7, %xmm6
        movsd     %xmm6, 64(%rsp)
        movsd     64(%rsp), %xmm4
        subsd     %xmm7, %xmm4
        ucomisd   %xmm4, %xmm8
        jne       ..B1.20
        jp        ..B1.20
..B1.21:
        mulsd     56(%rsp), %xmm0
        divsd     %xmm3, %xmm0
        jmp       ..B1.34
..B1.22:
        comisd    %xmm1, %xmm3
        ja        ..B1.35
..B1.23:
        pxor      %xmm2, %xmm2
        lea       (%r14,%r14,4), %ecx
        lea       1000(,%rcx,4), %esi
        cvtsi2sd  %esi, %xmm2
        comisd    %xmm1, %xmm2
        ja        ..B1.47
..B1.24:
        lea       _val_4_0(%rip), %rcx
        movaps    %xmm3, %xmm10
        lea       _val_8_0(%rip), %rdi
        lea       _val_2_0(%rip), %r8
        movsd     .L_2il0floatpacket.2(%rip), %xmm12
        lea       _val_3_0(%rip), %r9
        lea       _val_5_0(%rip), %r10
        movl      $-1, %esi
        movsd     (%rcx), %xmm11
        movaps    %xmm12, %xmm2
        movsd     (%rdi), %xmm8
        movaps    %xmm12, %xmm9
        movsd     (%r8), %xmm7
        xorb      %cl, %cl
        mulsd     %xmm11, %xmm10
        movaps    %xmm7, %xmm6
        mulsd     %xmm1, %xmm8
        mulsd     %xmm3, %xmm10
        movaps    %xmm10, %xmm13
        movsd     (%r9), %xmm5
        subsd     %xmm12, %xmm13
        divsd     %xmm8, %xmm13
        movsd     %xmm13, 16(%rsp)
        movaps    %xmm5, %xmm4
        movsd     (%r10), %xmm3
..B1.25:
        movaps    %xmm4, %xmm14
        movaps    %xmm6, %xmm15
        mulsd     %xmm4, %xmm14
        testl     %esi, %esi
        mulsd     %xmm8, %xmm15
        movsd     %xmm2, 56(%rsp)
        movaps    %xmm10, %xmm2
        subsd     %xmm14, %xmm2
        movaps    %xmm3, %xmm14
        mulsd     %xmm3, %xmm14
        divsd     %xmm15, %xmm2
        movaps    %xmm10, %xmm15
        mulsd     16(%rsp), %xmm2
        subsd     %xmm14, %xmm15
        movaps    %xmm8, %xmm14
        mulsd     %xmm5, %xmm14
        divsd     %xmm14, %xmm15
        mulsd     %xmm2, %xmm15
        jle       ..B1.46
..B1.26:
        addsd     %xmm2, %xmm12
        addsd     %xmm15, %xmm13
..B1.27:
        movsd     %xmm15, 16(%rsp)
        addsd     %xmm7, %xmm6
        divsd     %xmm12, %xmm15
        andps     .L_2il0floatpacket.3(%rip), %xmm15
        addsd     %xmm7, %xmm5
        addsd     %xmm11, %xmm4
        addsd     %xmm11, %xmm3
        comisd    %xmm15, %xmm9
        jbe       ..B1.29
..B1.28:
        movaps    %xmm15, %xmm9
        movb      $1, %cl
        jmp       ..B1.30
..B1.29:
        testb     %cl, %cl
        jne       ..B1.32
..B1.30:
        lea       _TWO_19H(%rip), %rdi
        movaps    %xmm12, %xmm2
        negl      %esi
        movsd     (%rdi), %xmm14
        mulsd     %xmm12, %xmm14
        addsd     %xmm14, %xmm2
        movsd     %xmm2, 64(%rsp)
        movsd     64(%rsp), %xmm2
        subsd     %xmm14, %xmm2
        ucomisd   56(%rsp), %xmm2
        jne       ..B1.25
        jp        ..B1.25
..B1.32:
        addl      %r14d, %r14d
        lea       56(%rsp), %rdi
        negl      %r14d
        lea       16(%rsp), %rsi
        decl      %r14d
        movaps    %xmm1, %xmm0
        movl      %r14d, %edx
        movsd     %xmm13, (%rsp)
        movsd     %xmm12, -8(%rsi)
        movsd     %xmm1, 32(%rsi)
..___tag_value_jnf.8:
        call      __libm_sincos_k32@PLT
..___tag_value_jnf.9:
..B1.33:
        lea       _tonpi(%rip), %rax
        movsd     48(%rsp), %xmm1
        movsd     8(%rsp), %xmm12
        movsd     (%rsp), %xmm13
        movsd     (%rax), %xmm0
        divsd     %xmm1, %xmm0
        sqrtsd    %xmm0, %xmm0
        mulsd     16(%rsp), %xmm12
        mulsd     56(%rsp), %xmm13
        subsd     %xmm13, %xmm12
        mulsd     %xmm12, %xmm0
..B1.34:
        movaps    %xmm0, %xmm1
        testl     %r15d, %r15d
        xorps     .L_2il0floatpacket.4(%rip), %xmm1
        jne       ..L10
        movaps    %xmm0, %xmm1
..L10:
        cvtsd2ss  %xmm1, %xmm1
        movaps    %xmm1, %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.35:
        pxor      %xmm8, %xmm8
        lea       (%r14,%r14), %esi
        cvtsi2sd  %esi, %xmm8
        movaps    %xmm1, %xmm7
        xorl      %ecx, %ecx
        mulsd     %xmm1, %xmm7
        lea       _val_2_0(%rip), %rdi
        lea       _TWO_19H(%rip), %r8
        pxor      %xmm11, %xmm11
        movsd     .L_2il0floatpacket.2(%rip), %xmm3
        movaps    %xmm1, %xmm9
        movq      %rcx, 56(%rsp)
        movaps    %xmm3, %xmm10
        xorps     .L_2il0floatpacket.4(%rip), %xmm7
        movaps    %xmm8, %xmm6
        movsd     %xmm3, 16(%rsp)
        movsd     (%rdi), %xmm4
        movsd     (%r8), %xmm5
        jmp       ..B1.36
..B1.37:
        movsd     %xmm12, 56(%rsp)
        movsd     %xmm12, 16(%rsp)
..B1.36:
        movaps    %xmm9, %xmm2
        movaps    %xmm8, %xmm12
        mulsd     %xmm7, %xmm11
        addsd     %xmm4, %xmm6
        mulsd     %xmm7, %xmm10
        mulsd     %xmm6, %xmm2
        mulsd     %xmm6, %xmm12
        addsd     %xmm11, %xmm2
        addsd     %xmm10, %xmm12
        movaps    %xmm9, %xmm11
        movaps    %xmm2, %xmm9
        divsd     %xmm12, %xmm2
        movaps    %xmm5, %xmm14
        movaps    %xmm8, %xmm10
        mulsd     %xmm2, %xmm14
        movaps    %xmm12, %xmm8
        movaps    %xmm14, %xmm13
        movsd     16(%rsp), %xmm15
        addsd     %xmm2, %xmm13
        movsd     %xmm13, 64(%rsp)
        movsd     64(%rsp), %xmm12
        subsd     %xmm14, %xmm12
        ucomisd   %xmm12, %xmm15
        jne       ..B1.37
        jp        ..B1.37
        jmp       ..B1.55
..B1.38:
        pxor      %xmm0, %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.39:
        lea       _zeros(%rip), %rax
        movss     (%rax,%r15,4), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.40:
        movss     (%rsp), %xmm0
        call      j0f@PLT
..B1.61:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.41:
        ja        ..B1.43
..B1.42:
        lea       _zeros(%rip), %rax
        xorl      %r14d, %r15d
        shrl      $31, %r15d
        andl      %r14d, %r15d
        movss     (%rax,%r15,4), %xmm0
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.43:
        movss     (%rsp), %xmm0
..B1.44:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.46:
        subsd     %xmm2, %xmm12
        subsd     %xmm15, %xmm13
        jmp       ..B1.27
..B1.47:
        lea       _val_17_0(%rip), %rcx
        movsd     (%rcx), %xmm2
        comisd    %xmm1, %xmm2
        jbe       ..B1.49
..B1.48:
        movaps    %xmm1, %xmm0
        movsd     %xmm1, 48(%rsp)
        call      j0@PLT
..B1.63:
        movsd     48(%rsp), %xmm1
        movaps    %xmm0, %xmm2
        movaps    %xmm1, %xmm0
        movsd     %xmm2, (%rsp)
        call      j1@PLT
..B1.62:
        movsd     48(%rsp), %xmm1
        movsd     (%rsp), %xmm2
        jmp       ..B1.51
..B1.49:
        movsd     .L_2il0floatpacket.2(%rip), %xmm2
        lea       _val_16_0(%rip), %rcx
        divsd     %xmm1, %xmm2
        movsd     (%rcx), %xmm8
        movaps    %xmm1, %xmm0
        mulsd     %xmm2, %xmm8
        lea       (%rsp), %rdi
        movaps    %xmm8, %xmm6
        lea       8(%rsp), %rsi
        mulsd     %xmm8, %xmm6
        movl      $-1, %edx
        movaps    %xmm6, %xmm7
        mulsd     %xmm6, %xmm7
        movq      $0, 48(%rsi)
        movsd     %xmm2, 8(%rsi)
        movsd     %xmm6, 16(%rsi)
        movsd     %xmm7, 24(%rsi)
        movsd     %xmm8, 32(%rsi)
        movsd     %xmm1, 40(%rsi)
..___tag_value_jnf.59:
        call      __libm_sincos_k32@PLT
..___tag_value_jnf.60:
..B1.50:
        lea       40+_PP1(%rip), %r11
        lea       40+_PP0(%rip), %rcx
        movsd     32(%rsp), %xmm7
        lea       24+_PP0(%rip), %rsi
        lea       8+_PP0(%rip), %rdi
        lea       32+_PP0(%rip), %r8
        movsd     24(%rsp), %xmm6
        lea       16+_PP0(%rip), %r9
        movsd     (%r11), %xmm10
        lea       _PP0(%rip), %r10
        movsd     (%rcx), %xmm2
        lea       24+_PP1(%rip), %rcx
        movsd     (%r8), %xmm3
        movsd     40(%rsp), %xmm8
        mulsd     %xmm7, %xmm10
        mulsd     %xmm7, %xmm2
        mulsd     %xmm7, %xmm3
        addsd     (%rcx), %xmm10
        addsd     (%rsi), %xmm2
        addsd     (%r9), %xmm3
        mulsd     %xmm7, %xmm10
        mulsd     %xmm7, %xmm2
        mulsd     %xmm7, %xmm3
        addsd     (%rdi), %xmm2
        addsd     (%r10), %xmm3
        mulsd     %xmm6, %xmm2
        lea       8+_PP1(%rip), %rcx
        addsd     %xmm3, %xmm2
        addsd     (%rcx), %xmm10
        lea       32+_PP1(%rip), %rcx
        mulsd     %xmm6, %xmm10
        movsd     (%rcx), %xmm4
        lea       16+_PP1(%rip), %rcx
        mulsd     %xmm7, %xmm4
        movsd     48(%rsp), %xmm1
        addsd     (%rcx), %xmm4
        lea       _PP1(%rip), %rcx
        mulsd     %xmm7, %xmm4
        addsd     (%rcx), %xmm4
        lea       40+_QP0(%rip), %rcx
        movsd     (%rcx), %xmm9
        lea       24+_QP0(%rip), %rcx
        mulsd     %xmm7, %xmm9
        addsd     %xmm4, %xmm10
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
        movsd     (%rcx), %xmm11
        lea       24+_QP1(%rip), %rcx
        mulsd     %xmm7, %xmm11
        mulsd     %xmm8, %xmm5
        addsd     (%rcx), %xmm11
        addsd     %xmm5, %xmm9
        mulsd     %xmm7, %xmm11
        lea       8+_QP1(%rip), %rcx
        addsd     (%rcx), %xmm11
        lea       32+_QP1(%rip), %rcx
        mulsd     %xmm6, %xmm11
        movsd     (%rcx), %xmm6
        lea       16+_QP1(%rip), %rcx
        mulsd     %xmm7, %xmm6
        mulsd     %xmm8, %xmm11
        addsd     (%rcx), %xmm6
        mulsd     %xmm7, %xmm6
        lea       _QP1(%rip), %rcx
        movsd     8(%rsp), %xmm7
        mulsd     %xmm7, %xmm2
        addsd     (%rcx), %xmm6
        lea       _tonpi(%rip), %rcx
        mulsd     %xmm8, %xmm6
        movsd     (%rcx), %xmm0
        addsd     %xmm6, %xmm11
        mulsd     16(%rsp), %xmm0
        mulsd     %xmm11, %xmm7
        sqrtsd    %xmm0, %xmm0
        movsd     (%rsp), %xmm8
        mulsd     %xmm8, %xmm9
        mulsd     %xmm10, %xmm8
        subsd     %xmm9, %xmm2
        addsd     %xmm7, %xmm8
        mulsd     %xmm0, %xmm2
        mulsd     %xmm8, %xmm0
..B1.51:
        lea       _val_2_0(%rip), %rcx
        cmpl      $1, %r14d
        movsd     (%rcx), %xmm3
        movl      $0, %ecx
        divsd     %xmm1, %xmm3
        jle       ..B1.34
..B1.52:
        decl      %r14d
..B1.53:
        incl      %ecx
        pxor      %xmm1, %xmm1
        cvtsi2sd  %ecx, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm0, %xmm4
        mulsd     %xmm0, %xmm1
        cmpl      %r14d, %ecx
        movaps    %xmm1, %xmm0
        subsd     %xmm2, %xmm0
        movaps    %xmm4, %xmm2
        jb        ..B1.53
        jmp       ..B1.34
..B1.55:
        movaps    %xmm3, %xmm6
        lea       -2(%r14,%r14), %ecx
        divsd     %xmm2, %xmm6
        pxor      %xmm5, %xmm5
        movaps    %xmm3, %xmm2
        cvtsi2sd  %ecx, %xmm5
        divsd     %xmm1, %xmm2
        xorl      %ecx, %ecx
        decl      %r14d
..B1.56:
        movaps    %xmm6, %xmm7
        incl      %ecx
        mulsd     %xmm5, %xmm7
        subsd     %xmm4, %xmm5
        mulsd     %xmm1, %xmm3
        cmpl      %r14d, %ecx
        subsd     %xmm3, %xmm7
        mulsd     %xmm2, %xmm7
        movaps    %xmm6, %xmm3
        movaps    %xmm7, %xmm6
        jl        ..B1.56
..B1.57:
        movaps    %xmm1, %xmm0
        movsd     %xmm7, (%rsp)
        call      j0@PLT
..B1.64:
        divsd     (%rsp), %xmm0
        jmp       ..B1.34
..B1.58:
        call      j1f@PLT
..B1.65:
        movaps    %xmm0, %xmm1
        testl     %r15d, %r15d
        xorps     .L_2il0floatpacket.5(%rip), %xmm1
        je        ..L61
        movaps    %xmm1, %xmm0
..L61:
        addq      $72, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	jnf,@function
	.size	jnf,.-jnf
	.data
# -- End  jnf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x7fffffff,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.3:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.long	0x80000000,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x450ea000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
function_zeros:
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x3f000000
	.long	0x3fcccccd
	.long	0x40600000
	.long	0x40c33333
	.long	0x4111999a
	.long	0x414b3333
	.long	0x4184cccd
	.long	0x41a66666
	.long	0x41ca6666
	.long	0x41f0cccd
	.long	0x420c0000
	.long	0x42206666
	.long	0x4235999a
	.long	0x424b3333
	.long	0x42613333
	.long	0x42780000
	.long	0x42876666
	.long	0x42930000
	.long	0x429ecccd
	.long	0x42aacccd
	.long	0x42b6cccd
	.long	0x42c30000
	.long	0x42cf6666
	.long	0x42dbcccd
	.long	0x42e86666
	.long	0x42f50000
	.long	0x4300e666
	.long	0x43074ccd
	.long	0x430db333
	.long	0x43143333
	.long	0x431ab333
	.long	0x43214ccd
	.long	0x4327e666
	.long	0x432e8000
	.long	0x4335199a
	.long	0x433bcccd
	.long	0x43428000
	.long	0x43493333
	.long	0x43500000
	.long	0x43560000
	.long	0x435d0000
	.long	0x43640000
	.long	0x436b0000
	.long	0x43720000
	.long	0x43780000
	.long	0x437f0000
	.long	0x43830000
	.long	0x43868000
	.long	0x438a0000
	.long	0x438d8000
	.long	0x43910000
	.long	0x43948000
	.long	0x43980000
	.long	0x439b8000
	.long	0x439f0000
	.long	0x43a28000
	.long	0x43a60000
	.long	0x43a98000
	.long	0x43ad0000
	.long	0x43b08000
	.long	0x43b40000
	.long	0x43b78000
	.long	0x43bb0000
	.long	0x43bf0000
	.long	0x43c28000
	.long	0x43c60000
	.long	0x43c98000
	.long	0x43cd0000
	.long	0x43d08000
	.long	0x43d40000
	.long	0x43d80000
	.long	0x43db8000
	.long	0x43df0000
	.long	0x43e28000
	.long	0x43e60000
	.long	0x43ea0000
	.long	0x43ed8000
	.long	0x43f10000
	.long	0x43f48000
	.long	0x43f88000
	.long	0x43fc0000
	.long	0x43ff8000
	.long	0x44018000
	.long	0x44038000
	.long	0x44054000
	.long	0x44070000
	.long	0x44090000
	.long	0x440ac000
	.long	0x440c8000
	.long	0x440e4000
	.long	0x44104000
	.long	0x44120000
	.long	0x4413c000
	.long	0x4415c000
	.long	0x44178000
	.long	0x44194000
	.long	0x441b4000
	.long	0x441d0000
	.long	0x441ec000
	.long	0x4420c000
	.long	0x44228000
	.long	0x44244000
	.long	0x44264000
	.long	0x44280000
	.long	0x442a0000
	.long	0x442bc000
	.long	0x442d8000
	.long	0x442f8000
	.long	0x44314000
	.long	0x44334000
	.long	0x44350000
	.long	0x4436c000
	.long	0x4438c000
	.long	0x443a8000
	.long	0x443c8000
	.long	0x443e4000
	.long	0x44400000
	.long	0x44420000
	.long	0x4443c000
	.long	0x4445c000
	.long	0x44478000
	.long	0x44498000
	.long	0x444b4000
	.long	0x444d0000
	.long	0x444f0000
	.long	0x4450c000
	.long	0x4452c000
	.long	0x44548000
	.long	0x44568000
	.long	0x44584000
	.long	0x445a4000
	.long	0x445c0000
	.long	0x445e0000
	.long	0x445fc000
	.long	0x4461c000
	.long	0x44638000
	.long	0x44658000
	.long	0x44674000
	.long	0x44694000
	.long	0x446b0000
	.long	0x446cc000
	.long	0x446ec000
	.long	0x44708000
	.long	0x44728000
	.long	0x44744000
	.long	0x44764000
	.long	0x44784000
	.long	0x447a0000
	.long	0x447c0000
	.long	0x447dc000
	.long	0x447fc000
	.long	0x4480c000
	.long	0x4481c000
	.long	0x4482a000
	.long	0x4483a000
	.long	0x44848000
	.long	0x44858000
	.long	0x44866000
	.long	0x44876000
	.long	0x44884000
	.long	0x44894000
	.long	0x448a2000
	.long	0x448b2000
	.long	0x448c0000
	.long	0x448d0000
	.long	0x448e0000
	.long	0x448ee000
	.long	0x448fe000
	.long	0x4490c000
	.long	0x4491c000
	.long	0x4492a000
	.long	0x4493a000
	.long	0x44948000
	.long	0x44958000
	.long	0x44968000
	.long	0x44976000
	.long	0x44986000
	.long	0x44994000
	.long	0x449a4000
	.long	0x449b2000
	.long	0x449c2000
	.long	0x449d2000
	.long	0x449e0000
	.long	0x449f0000
	.long	0x449fe000
	.long	0x44a0e000
	.long	0x44a1c000
	.long	0x44a2c000
	.long	0x44a3c000
	.long	0x44a4a000
	.long	0x44a5a000
	.long	0x44a68000
	.long	0x44a78000
	.long	0x44a88000
	.long	0x44a96000
	.long	0x44aa6000
	.long	0x44ab4000
	.long	0x44ac4000
	.long	0x44ad4000
	.long	0x44ae2000
	.long	0x44af2000
	.long	0x44b00000
	.long	0x44b10000
	.long	0x44b20000
	.long	0x44b2e000
	.long	0x44b3e000
	.long	0x44b4c000
	.long	0x44b5c000
	.long	0x44b6c000
	.long	0x44b7a000
	.long	0x44b8a000
	.long	0x44b9a000
	.long	0x44ba8000
	.long	0x44bb8000
	.long	0x44bc6000
	.long	0x44bd6000
	.long	0x44be6000
	.long	0x44bf4000
	.long	0x44c04000
	.long	0x44c14000
	.long	0x44c22000
	.long	0x44c32000
	.long	0x44c40000
	.long	0x44c50000
	.long	0x44c60000
	.long	0x44c6e000
	.long	0x44c7e000
	.long	0x44c8e000
	.long	0x44c9c000
	.long	0x44cac000
	.long	0x44cba000
	.long	0x44cca000
	.long	0x44cda000
	.long	0x44ce8000
	.long	0x44cf8000
	.long	0x44d08000
	.long	0x44d16000
	.long	0x44d26000
	.long	0x44d36000
	.long	0x44d44000
	.long	0x44d54000
	.long	0x44d64000
	.long	0x44d72000
	.long	0x44d82000
	.long	0x44d92000
	.long	0x44da0000
	.long	0x44db0000
	.long	0x44dc0000
	.long	0x44dce000
	.long	0x44dde000
	.long	0x44dec000
	.long	0x44dfc000
	.long	0x44e0c000
	.long	0x44e1a000
	.long	0x44e2a000
	.long	0x44e3a000
	.long	0x44e48000
	.long	0x44e58000
	.long	0x44e68000
	.long	0x44e76000
	.long	0x44e86000
	.long	0x44e96000
	.long	0x44ea4000
	.long	0x44eb4000
	.long	0x44ec4000
	.long	0x44ed4000
	.long	0x44ee2000
	.long	0x44ef2000
	.long	0x44f02000
	.long	0x44f10000
	.long	0x44f20000
	.long	0x44f30000
	.long	0x44f3e000
	.long	0x44f4e000
	.long	0x44f5e000
	.long	0x44f6c000
	.long	0x44f7c000
	.long	0x44f8c000
	.long	0x44f9a000
	.long	0x44faa000
	.long	0x44fba000
	.long	0x44fc8000
	.long	0x44fd8000
	.long	0x44fe8000
	.long	0x44ff6000
	.long	0x45003000
	.long	0x4500b000
	.long	0x45013000
	.long	0x4501a000
	.long	0x45022000
	.long	0x4502a000
	.long	0x45031000
	.long	0x45039000
	.long	0x45041000
	.long	0x45048000
	.long	0x45050000
	.long	0x45058000
	.long	0x4505f000
	.long	0x45067000
	.long	0x4506f000
	.long	0x45077000
	.long	0x4507e000
	.long	0x45086000
	.long	0x4508e000
	.long	0x45095000
	.long	0x4509d000
	.long	0x450a5000
	.long	0x450ac000
	.long	0x450b4000
	.long	0x450bc000
	.long	0x450c4000
	.long	0x450cb000
	.long	0x450d3000
	.long	0x450db000
	.long	0x450e2000
	.long	0x450ea000
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
_val_4_0:
	.long	0
	.long	1074790400
	.type	_val_4_0,@object
	.size	_val_4_0,8
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
_val_5_0:
	.long	0
	.long	1075052544
	.type	_val_5_0,@object
	.size	_val_5_0,8
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_val_17_0:
	.long	0
	.long	1076953088
	.type	_val_17_0,@object
	.size	_val_17_0,8
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
