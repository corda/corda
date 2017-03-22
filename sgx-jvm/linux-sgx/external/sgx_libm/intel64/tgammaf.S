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
	.file "tgammaf.c"
	.text
..TXTST0:
# -- Begin  tgammaf
	.text
       .align    16,0x90
	.globl tgammaf
tgammaf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_tgammaf.1:
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
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $32, %rsp
	.cfi_def_cfa_offset 80
        xorb      %r12b, %r12b
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        movss     %xmm0, 24(%rsp)
        movd      %xmm0, %r14d
        movss     %xmm1, (%rsp)
        movsd     %xmm2, 8(%rsp)
..___tag_value_tgammaf.14:
        call      fegetround@PLT
..___tag_value_tgammaf.15:
..B1.94:
        movsd     8(%rsp), %xmm2
        movl      %eax, %ebp
..B1.2:
        testl     %ebp, %ebp
        je        ..B1.4
..B1.3:
        xorl      %edi, %edi
        movb      $1, %r12b
        movsd     %xmm2, 8(%rsp)
..___tag_value_tgammaf.16:
        call      fesetround@PLT
..___tag_value_tgammaf.17:
..B1.95:
        movsd     8(%rsp), %xmm2
..B1.4:
        movzwl    26(%rsp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        movl      24(%rsp), %ebx
        shrl      $31, %ebx
        cmpl      $255, %eax
        je        ..B1.84
..B1.5:
        movss     24(%rsp), %xmm5
        andl      $2147483647, %r14d
        movl      %r14d, 8(%rsp)
        ucomiss   (%rsp), %xmm5
        jp        ..B1.6
        je        ..B1.81
..B1.6:
        cmpl      $2097152, %r14d
        jle       ..B1.78
..B1.7:
        testl     %ebx, %ebx
        je        ..B1.16
..B1.8:
        cmpl      $150, %eax
        jge       ..B1.72
..B1.9:
        movss     .L_2il0floatpacket.2(%rip), %xmm3
        movss     8(%rsp), %xmm4
        movaps    %xmm3, %xmm1
        movss     (%rsp), %xmm0
        addss     %xmm4, %xmm1
        movss     %xmm1, 20(%rsp)
        movss     %xmm0, 16(%rsp)
        movss     20(%rsp), %xmm0
        movl      20(%rsp), %r14d
        subss     %xmm3, %xmm0
        comiss    %xmm4, %xmm0
        jbe       ..B1.11
..B1.10:
        incl      %r14d
        subss     .L_2il0floatpacket.11(%rip), %xmm0
..B1.11:
        ucomiss   %xmm0, %xmm4
        jp        ..B1.12
        je        ..B1.69
..B1.12:
        movss     .L_2il0floatpacket.3(%rip), %xmm0
        comiss    %xmm5, %xmm0
        jbe       ..B1.17
..B1.13:
        testb     %r12b, %r12b
        je        ..B1.15
..B1.14:
        movl      %ebp, %edi
..___tag_value_tgammaf.18:
        call      fesetround@PLT
..___tag_value_tgammaf.19:
..B1.15:
        lea       _small_value_32(%rip), %rax
        notl      %r14d
        andl      $1, %r14d
        movss     (%rax,%r14,4), %xmm0
        mulss     .L_2il0floatpacket.4(%rip), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.16:
        movss     (%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
..B1.17:
        cvtss2sd  %xmm5, %xmm5
        lea       _overflow_boundary(%rip), %rdx
        comisd    (%rdx), %xmm5
        jae       ..B1.75
..B1.18:
        movss     .L_2il0floatpacket.2(%rip), %xmm9
        testl     %ebx, %ebx
        je        ..B1.20
..B1.19:
        movss     8(%rsp), %xmm8
        movaps    %xmm9, %xmm2
        movaps    %xmm8, %xmm1
        addss     %xmm8, %xmm2
        movss     %xmm2, 20(%rsp)
        pxor      %xmm2, %xmm2
        movss     20(%rsp), %xmm0
        lea       8+_tgamma_sin_table(%rip), %rcx
        lea       24+_tgamma_sin_table(%rip), %r8
        subss     %xmm9, %xmm0
        movsd     (%rcx), %xmm7
        subss     %xmm0, %xmm1
        movd      %xmm1, %edx
        lea       40+_tgamma_sin_table(%rip), %r10
        movsd     (%r8), %xmm3
        andl      $2147483647, %edx
        lea       _tgamma_sin_table(%rip), %rsi
        movl      %edx, (%rsp)
        lea       56+_tgamma_sin_table(%rip), %r14
        movsd     (%r10), %xmm4
        lea       16+_tgamma_sin_table(%rip), %r9
        lea       32+_tgamma_sin_table(%rip), %r11
        lea       48+_tgamma_sin_table(%rip), %rdx
        cvtss2sd  (%rsp), %xmm2
        movaps    %xmm2, %xmm6
        mulsd     %xmm2, %xmm6
        mulsd     %xmm6, %xmm7
        addsd     %xmm6, %xmm3
        addsd     %xmm6, %xmm4
        mulsd     %xmm6, %xmm3
        addsd     (%rsi), %xmm7
        mulsd     %xmm6, %xmm4
        mulsd     %xmm6, %xmm7
        addsd     (%r9), %xmm3
        addsd     (%r11), %xmm4
        mulsd     %xmm3, %xmm7
        movsd     (%r14), %xmm5
        mulsd     %xmm4, %xmm7
        addsd     %xmm6, %xmm5
        mulsd     %xmm5, %xmm6
        addsd     (%rdx), %xmm6
        mulsd     %xmm6, %xmm7
        mulsd     %xmm2, %xmm7
        addsd     %xmm7, %xmm2
        jmp       ..B1.21
..B1.20:
        movss     8(%rsp), %xmm8
..B1.21:
        movaps    %xmm8, %xmm1
        addss     %xmm9, %xmm8
        movss     %xmm8, 20(%rsp)
        movss     20(%rsp), %xmm0
        movl      20(%rsp), %ecx
        subss     %xmm9, %xmm0
        andl      $1048575, %ecx
        comiss    %xmm1, %xmm0
        jbe       ..B1.23
..B1.22:
        decl      %ecx
        subss     .L_2il0floatpacket.11(%rip), %xmm0
..B1.23:
        comiss    .L_2il0floatpacket.5(%rip), %xmm1
        lea       1(%rcx), %r14d
        jb        ..B1.39
..B1.24:
        movaps    %xmm1, %xmm3
        movl      %ecx, %esi
        andl      $-8, %esi
        movl      %ecx, %edx
        andl      $7, %edx
        cmpl      $8, %ecx
        movl      %esi, %ecx
        lea       _tgamma_A_table(%rip), %r8
        subss     %xmm0, %xmm3
        cvtss2sd  %xmm3, %xmm3
        movaps    %xmm3, %xmm8
        lea       -2(%rdx), %eax
        mulsd     %xmm3, %xmm8
        cmovl     %eax, %edx
        sarl      $1, %ecx
        addl      %esi, %ecx
        sarl      $3, %esi
        addl      %esi, %ecx
        movslq    %ecx, %rcx
        testl     %edx, %edx
        movsd     (%r8,%rcx,8), %xmm9
        mulsd     %xmm3, %xmm9
        movsd     8(%r8,%rcx,8), %xmm0
        addsd     %xmm8, %xmm9
        mulsd     %xmm3, %xmm0
        addsd     48(%r8,%rcx,8), %xmm9
        addsd     %xmm8, %xmm0
        mulsd     96(%r8,%rcx,8), %xmm9
        addsd     56(%r8,%rcx,8), %xmm0
        movsd     16(%r8,%rcx,8), %xmm4
        mulsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm9
        addsd     %xmm8, %xmm4
        movsd     24(%r8,%rcx,8), %xmm5
        mulsd     %xmm3, %xmm5
        addsd     64(%r8,%rcx,8), %xmm4
        movsd     32(%r8,%rcx,8), %xmm6
        addsd     %xmm8, %xmm5
        mulsd     %xmm3, %xmm6
        mulsd     %xmm4, %xmm9
        addsd     72(%r8,%rcx,8), %xmm5
        addsd     %xmm8, %xmm6
        mulsd     %xmm5, %xmm9
        addsd     80(%r8,%rcx,8), %xmm6
        movsd     40(%r8,%rcx,8), %xmm7
        mulsd     %xmm3, %xmm7
        mulsd     %xmm6, %xmm9
        addsd     %xmm7, %xmm8
        movsd     .L_2il0floatpacket.10(%rip), %xmm3
        movsd     %xmm3, 8(%rsp)
        addsd     88(%r8,%rcx,8), %xmm8
        mulsd     %xmm8, %xmm9
        movsd     %xmm9, (%rsp)
        je        ..B1.29
..B1.25:
        movl      $1, %eax
        jle       ..B1.29
..B1.26:
        pxor      %xmm0, %xmm0
        cvtss2sd  %xmm1, %xmm0
..B1.27:
        pxor      %xmm4, %xmm4
        movaps    %xmm0, %xmm5
        cvtsi2sd  %eax, %xmm4
        incl      %eax
        subsd     %xmm4, %xmm5
        mulsd     %xmm5, %xmm3
        cmpl      %edx, %eax
        jle       ..B1.27
..B1.29:
        testl     %ebx, %ebx
        je        ..B1.35
..B1.30:
        cvtss2sd  %xmm1, %xmm1
        mulsd     %xmm2, %xmm1
        mulsd     (%rsp), %xmm1
        mulsd     %xmm3, %xmm1
        movsd     8(%rsp), %xmm0
        divsd     %xmm1, %xmm0
        movss     .L_2il0floatpacket.6(%rip), %xmm2
        movsd     %xmm0, 8(%rsp)
        comiss    24(%rsp), %xmm2
        jbe       ..B1.32
..B1.31:
        lea       _tgamma_A40_inv(%rip), %rax
        mulsd     (%rax), %xmm0
        movsd     %xmm0, 8(%rsp)
..B1.32:
        testb     %r12b, %r12b
        je        ..B1.34
..B1.33:
        movl      %ebp, %edi
..___tag_value_tgammaf.37:
        call      fesetround@PLT
..___tag_value_tgammaf.38:
..B1.34:
        movsd     8(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movd      %xmm0, %eax
        shll      $31, %r14d
        xorl      %r14d, %eax
        movl      %eax, 16(%rsp)
        movss     16(%rsp), %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.35:
        movsd     (%rsp), %xmm0
        testb     %r12b, %r12b
        mulsd     %xmm3, %xmm0
        movsd     %xmm0, (%rsp)
        je        ..B1.37
..B1.36:
        movl      %ebp, %edi
..___tag_value_tgammaf.56:
        call      fesetround@PLT
..___tag_value_tgammaf.57:
..B1.37:
        movsd     (%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 16(%rsp)
..B1.38:
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.39:
        movss     .L_2il0floatpacket.11(%rip), %xmm6
        pxor      %xmm0, %xmm0
        cvtss2sd  %xmm1, %xmm0
        comiss    %xmm1, %xmm6
        jbe       ..B1.41
..B1.40:
        movsd     .L_2il0floatpacket.10(%rip), %xmm3
        addsd     %xmm0, %xmm3
        jmp       ..B1.42
..B1.41:
        movaps    %xmm0, %xmm3
..B1.42:
        comisd    .L_2il0floatpacket.7(%rip), %xmm3
        jb        ..B1.44
..B1.43:
        lea       _tgamma_A175_table(%rip), %r13
        subsd     .L_2il0floatpacket.10(%rip), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movss     %xmm3, (%rsp)
        jmp       ..B1.50
..B1.44:
        comisd    .L_2il0floatpacket.8(%rip), %xmm3
        jb        ..B1.46
..B1.45:
        lea       _tgamma_A150_table(%rip), %r13
        subsd     .L_2il0floatpacket.10(%rip), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movss     %xmm3, (%rsp)
        jmp       ..B1.50
..B1.46:
        movsd     .L_2il0floatpacket.9(%rip), %xmm4
        comisd    %xmm4, %xmm3
        jb        ..B1.48
..B1.47:
        lea       _local_minimum(%rip), %rdx
        lea       _tgamma_A125_table(%rip), %r13
        movsd     .L_2il0floatpacket.10(%rip), %xmm4
        addsd     (%rdx), %xmm4
        subsd     %xmm4, %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movss     %xmm3, (%rsp)
        jmp       ..B1.50
..B1.48:
        comisd    %xmm3, %xmm4
        jbe       ..B1.50
..B1.49:
        lea       _tgamma_A100_table(%rip), %r13
        subsd     .L_2il0floatpacket.10(%rip), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movss     %xmm3, (%rsp)
..B1.50:
        movsd     (%r13), %xmm3
        testl     %eax, %eax
        movsd     %xmm3, 8(%rsp)
        je        ..B1.53
..B1.51:
        pxor      %xmm4, %xmm4
        cvtss2sd  (%rsp), %xmm4
        movsd     56(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        movsd     8(%rsp), %xmm5
        addsd     48(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        addsd     40(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        addsd     32(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        addsd     24(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        addsd     16(%r13), %xmm3
        mulsd     %xmm4, %xmm3
        addsd     8(%r13), %xmm3
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        movsd     %xmm5, 8(%rsp)
..B1.53:
        comiss    %xmm1, %xmm6
        jbe       ..B1.61
..B1.54:
        testl     %ebx, %ebx
        je        ..B1.58
..B1.55:
        mulsd     8(%rsp), %xmm2
        testb     %r12b, %r12b
        movsd     .L_2il0floatpacket.10(%rip), %xmm0
        divsd     %xmm2, %xmm0
        movsd     %xmm0, (%rsp)
        je        ..B1.57
..B1.56:
        movl      %ebp, %edi
..___tag_value_tgammaf.75:
        call      fesetround@PLT
..___tag_value_tgammaf.76:
..B1.57:
        movsd     (%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movd      %xmm0, %eax
        shll      $31, %r14d
        xorl      %r14d, %eax
        movl      %eax, 16(%rsp)
        movss     16(%rsp), %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.58:
        movsd     8(%rsp), %xmm1
        testb     %r12b, %r12b
        divsd     %xmm0, %xmm1
        movsd     %xmm1, 8(%rsp)
        je        ..B1.67
..B1.59:
        movl      %ebp, %edi
..___tag_value_tgammaf.94:
        call      fesetround@PLT
..___tag_value_tgammaf.95:
        jmp       ..B1.67
..B1.61:
        testl     %ebx, %ebx
        je        ..B1.65
..B1.62:
        mulsd     %xmm0, %xmm2
        testb     %r12b, %r12b
        mulsd     8(%rsp), %xmm2
        movsd     .L_2il0floatpacket.10(%rip), %xmm1
        divsd     %xmm2, %xmm1
        movsd     %xmm1, (%rsp)
        je        ..B1.64
..B1.63:
        movl      %ebp, %edi
..___tag_value_tgammaf.96:
        call      fesetround@PLT
..___tag_value_tgammaf.97:
..B1.64:
        movsd     (%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.65:
        testb     %r12b, %r12b
        je        ..B1.67
..B1.66:
        movl      %ebp, %edi
..___tag_value_tgammaf.115:
        call      fesetround@PLT
..___tag_value_tgammaf.116:
..B1.67:
        movsd     8(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 16(%rsp)
..B1.68:
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.69:
        testb     %r12b, %r12b
        je        ..B1.71
..B1.70:
        movl      %ebp, %edi
..___tag_value_tgammaf.134:
        call      fesetround@PLT
..___tag_value_tgammaf.135:
..B1.71:
        movss     (%rsp), %xmm0
        divss     %xmm0, %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.72:
        testb     %r12b, %r12b
        je        ..B1.74
..B1.73:
        movl      %ebp, %edi
..___tag_value_tgammaf.153:
        call      fesetround@PLT
..___tag_value_tgammaf.154:
..B1.74:
        movss     (%rsp), %xmm0
        divss     %xmm0, %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.75:
        testb     %r12b, %r12b
        je        ..B1.77
..B1.76:
        movl      %ebp, %edi
..___tag_value_tgammaf.172:
        call      fesetround@PLT
..___tag_value_tgammaf.173:
..B1.77:
        movl      $1904214016, 20(%rsp)
        movss     20(%rsp), %xmm1
        movss     20(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, 20(%rsp)
        movss     20(%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.78:
        movss     (%rsp), %xmm0
        testb     %r12b, %r12b
        movss     %xmm0, 16(%rsp)
        je        ..B1.80
..B1.79:
        movl      %ebp, %edi
..___tag_value_tgammaf.191:
        call      fesetround@PLT
..___tag_value_tgammaf.192:
..B1.80:
        movl      %ebx, %ebx
        lea       _large_value_32(%rip), %rax
        movl      (%rax,%rbx,4), %edx
        movl      %edx, 20(%rsp)
        movss     20(%rsp), %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, 20(%rsp)
        movss     20(%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.81:
        testb     %r12b, %r12b
        je        ..B1.83
..B1.82:
        movl      %ebp, %edi
..___tag_value_tgammaf.210:
        call      fesetround@PLT
..___tag_value_tgammaf.211:
..B1.83:
        lea       _ones(%rip), %rax
        movss     (%rax,%rbx,4), %xmm0
        divss     (%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.84:
        testb     %r12b, %r12b
        je        ..B1.86
..B1.85:
        movl      %ebp, %edi
..___tag_value_tgammaf.229:
        call      fesetround@PLT
..___tag_value_tgammaf.230:
..B1.86:
        testl     %ebx, %ebx
        je        ..B1.89
..B1.87:
        testl     $8388607, 24(%rsp)
        je        ..B1.91
..B1.89:
        movss     (%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        movl      %r14d, 8(%rsp)
..B1.90:
        movl      24(%rsp), %eax
        movl      %eax, 20(%rsp)
        movss     20(%rsp), %xmm1
        movss     20(%rsp), %xmm0
        addss     %xmm0, %xmm1
        movss     %xmm1, 20(%rsp)
        movss     20(%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.91:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        divss     (%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.type	tgammaf,@function
	.size	tgammaf,.-tgammaf
	.data
# -- End  tgammaf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0x3ffc0000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3ff80000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3ff40000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x71800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x80000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x4b400000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0xc22c0000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0x0d800000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
.L_2il0floatpacket.5:
	.long	0x40000000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,4
	.align 4
.L_2il0floatpacket.6:
	.long	0xc2200000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,4
	.align 4
.L_2il0floatpacket.11:
	.long	0x3f800000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_overflow_boundary:
	.long	0
	.long	1078035746
	.type	_overflow_boundary,@object
	.size	_overflow_boundary,8
	.align 4
_tgamma_sin_table:
	.long	1980114658
	.long	3200337074
	.long	804494867
	.long	1049689241
	.long	2884431818
	.long	1079329239
	.long	930502348
	.long	3223780109
	.long	1245824743
	.long	1080878479
	.long	2510431012
	.long	1073721695
	.long	1661126174
	.long	1078219640
	.long	1205599443
	.long	3223573914
	.type	_tgamma_sin_table,@object
	.size	_tgamma_sin_table,64
	.align 4
_tgamma_A_table:
	.long	3121394612
	.long	1074393041
	.long	2029996066
	.long	1073722195
	.long	4137005464
	.long	1071238760
	.long	3372276227
	.long	3220751114
	.long	68401302
	.long	3222286887
	.long	1275947047
	.long	3223263002
	.long	4031994882
	.long	1074665426
	.long	1089993416
	.long	1074817170
	.long	427005612
	.long	1075089294
	.long	1508975772
	.long	1075601433
	.long	2111623053
	.long	1076192020
	.long	75538972
	.long	1077100653
	.long	2171869559
	.long	1055005400
	.long	2919987515
	.long	1074143787
	.long	860307162
	.long	1073890599
	.long	2407232453
	.long	1073038722
	.long	1468490975
	.long	3216757264
	.long	846192051
	.long	3221294835
	.long	402234383
	.long	3222573752
	.long	2566208764
	.long	1073763360
	.long	162360449
	.long	1073949715
	.long	1512348533
	.long	1074371728
	.long	2920174397
	.long	1074968946
	.long	3785085665
	.long	1075671857
	.long	1922428759
	.long	1076496000
	.long	2446382284
	.long	1072101083
	.long	42366358
	.long	1073746704
	.long	3587290866
	.long	1073390434
	.long	1169958761
	.long	1072607731
	.long	3891474799
	.long	3217275878
	.long	1452792685
	.long	3221010072
	.long	1006385979
	.long	3222363118
	.long	3663632771
	.long	1072778759
	.long	3212960448
	.long	1073036150
	.long	2202365811
	.long	1073636095
	.long	2023822532
	.long	1074258740
	.long	40477660
	.long	1075053854
	.long	658935515
	.long	1076010306
	.long	932572503
	.long	1105834679
	.long	1233355998
	.long	1073412029
	.long	3033646034
	.long	1073097432
	.long	3149182759
	.long	1072206908
	.long	1726027606
	.long	3217766868
	.long	3439348786
	.long	3220880557
	.long	2080997065
	.long	3222253033
	.long	843307201
	.long	1072200111
	.long	3138702878
	.long	1072613342
	.long	3610734718
	.long	1073142430
	.long	2651617324
	.long	1073914526
	.long	2611481547
	.long	1074796471
	.long	3284459136
	.long	1075745821
	.long	1365755587
	.long	1144167771
	.long	2699624028
	.long	1073212595
	.long	2232824171
	.long	1072922970
	.long	3659224963
	.long	1071959670
	.long	3026238542
	.long	3218100813
	.long	4240441539
	.long	3220810712
	.long	1772362075
	.long	3222132889
	.long	3742687744
	.long	1071863890
	.long	56227394
	.long	1072220001
	.long	969667939
	.long	1072882737
	.long	880059910
	.long	1073714908
	.long	247554419
	.long	1074515829
	.long	48918556
	.long	1075495246
	.long	2601377264
	.long	1185936586
	.long	3625529495
	.long	1073077476
	.long	3298250746
	.long	1072804056
	.long	2971501690
	.long	1071788510
	.long	2950162741
	.long	3218231788
	.long	26528490
	.long	3220765509
	.long	3761284804
	.long	3222050392
	.long	3800626879
	.long	1071658634
	.long	1108715276
	.long	1071977390
	.long	286829382
	.long	1072720005
	.long	3141975933
	.long	1073477575
	.long	4145290123
	.long	1074329952
	.long	2167683451
	.long	1075329952
	.long	0
	.long	1072693248
	.type	_tgamma_A_table,@object
	.size	_tgamma_A_table,624
	.align 4
_tgamma_A40_inv:
	.long	3988571200
	.long	914969834
	.type	_tgamma_A40_inv,@object
	.size	_tgamma_A40_inv,8
	.align 4
_tgamma_A175_table:
	.long	1682645026
	.long	1072688670
	.long	3753006229
	.long	3219243438
	.long	177700005
	.long	1072453288
	.long	3289039035
	.long	3219374666
	.long	2492560830
	.long	1071326765
	.long	123078934
	.long	3217486055
	.long	3499570675
	.long	1068068407
	.long	1362583934
	.long	3212265128
	.type	_tgamma_A175_table,@object
	.size	_tgamma_A175_table,64
	.align 4
_tgamma_A150_table:
	.long	988281800
	.long	1072692321
	.long	502740408
	.long	3219277207
	.long	2058512241
	.long	1072588117
	.long	2109547492
	.long	3219675970
	.long	3371209031
	.long	1071892748
	.long	3191518250
	.long	3218447665
	.long	3816909947
	.long	1069498883
	.long	1366906983
	.long	3214177035
	.type	_tgamma_A150_table,@object
	.size	_tgamma_A150_table,64
	.align 4
_local_minimum:
	.long	2371549438
	.long	1071483745
	.type	_local_minimum,@object
	.size	_local_minimum,8
	.align 4
_tgamma_A125_table:
	.long	2191760034
	.long	1072453340
	.long	2642694891
	.long	3186525859
	.long	1706815263
	.long	1071344724
	.long	1896485049
	.long	3217079006
	.long	441733475
	.long	1069848224
	.long	978611902
	.long	3216500728
	.long	351495825
	.long	1068442485
	.long	1680249708
	.long	3216253219
	.type	_tgamma_A125_table,@object
	.size	_tgamma_A125_table,64
	.align 4
_tgamma_A100_table:
	.long	4293468318
	.long	1072693247
	.long	3444857508
	.long	3219290252
	.long	3510931740
	.long	1072670280
	.long	3644082616
	.long	3219982336
	.long	2685454907
	.long	1072646231
	.long	4151446349
	.long	3220059936
	.long	4004008463
	.long	1072263166
	.long	1217534056
	.long	3218706582
	.type	_tgamma_A100_table,@object
	.size	_tgamma_A100_table,64
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
