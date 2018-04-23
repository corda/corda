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
	.file "clog10.c"
	.text
..TXTST0:
# -- Begin  clog10f
	.text
       .align    16,0x90
	.globl clog10f
clog10f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_clog10f.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_clog10f.4:
        call      clog10@PLT
..___tag_value_clog10f.5:
..B1.5:
        unpcklpd  %xmm1, %xmm0
..B1.2:
        cvtpd2ps  %xmm0, %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	clog10f,@function
	.size	clog10f,.-clog10f
	.data
# -- End  clog10f
	.text
# -- Begin  clog10
	.text
       .align    16,0x90
	.globl clog10
clog10:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_clog10.8:
..L9:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        movq      %fs:40, %rax
        subq      $128, %rsp
	.cfi_def_cfa_offset 160
        movsd     %xmm0, 16(%rsp)
        xorq      %rsp, %rax
        movsd     %xmm1, 24(%rsp)
        movzwl    22(%rsp), %edx
        andl      $32752, %edx
        movq      %rax, 112(%rsp)
        movzwl    30(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %edx
        shrl      $4, %eax
        cmpl      $2047, %edx
        jge       ..B2.26
..B2.2:
        cmpl      $2047, %eax
        jge       ..B2.62
..B2.3:
        testl     %edx, %edx
        jne       ..B2.9
..B2.4:
        testl     $1048575, 20(%rsp)
        jne       ..B2.9
..B2.5:
        cmpl      $0, 16(%rsp)
        jne       ..B2.9
..B2.6:
        testl     %eax, %eax
        jne       ..B2.9
..B2.7:
        testl     $1048575, 28(%rsp)
        jne       ..B2.9
..B2.8:
        cmpl      $0, 24(%rsp)
        je        ..B2.25
..B2.9:
        movsd     16(%rsp), %xmm0
        lea       24(%rsp), %r15
        movsd     (%r15), %xmm1
        movaps    %xmm0, %xmm12
        movaps    %xmm1, %xmm11
        andps     .L_2il0floatpacket.10(%rip), %xmm11
        andps     .L_2il0floatpacket.10(%rip), %xmm12
        movaps    %xmm11, %xmm6
        movaps    %xmm12, %xmm7
        movaps    %xmm6, %xmm2
        comisd    %xmm12, %xmm11
        jbe       ..B2.11
..B2.10:
        movaps    %xmm7, %xmm6
        movaps    %xmm2, %xmm12
        movaps    %xmm11, %xmm7
        movaps    %xmm6, %xmm11
..B2.11:
        lea       8+_CONSTANTS(%rip), %rax
        comisd    (%rax), %xmm12
        jb        ..B2.22
..B2.12:
        lea       16+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm2
        comisd    %xmm12, %xmm2
        jb        ..B2.22
..B2.13:
        lea       24+_CONSTANTS(%rip), %rdx
        lea       _CONSTANTS(%rip), %rax
        movaps    %xmm12, %xmm5
        mulsd     %xmm12, %xmm5
        comisd    (%rdx), %xmm11
        movsd     .L_2il0floatpacket.11(%rip), %xmm2
        movsd     (%rax), %xmm4
        jbe       ..B2.16
..B2.14:
        movaps    %xmm11, %xmm3
        mulsd     %xmm11, %xmm3
        addsd     %xmm3, %xmm5
        subsd     %xmm2, %xmm5
        andps     .L_2il0floatpacket.10(%rip), %xmm5
        comisd    %xmm5, %xmm4
        ja        ..B2.17
        jmp       ..B2.22
..B2.16:
        subsd     %xmm2, %xmm5
        andps     .L_2il0floatpacket.10(%rip), %xmm5
        comisd    %xmm5, %xmm4
        jbe       ..B2.22
..B2.17:
        movsd     %xmm6, 8(%rsp)
..B2.18:
        movzwl    14(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $913, %edx
        jl        ..B2.20
..B2.19:
        movsd     .L_2il0floatpacket.7(%rip), %xmm9
        movaps    %xmm12, %xmm10
        mulsd     %xmm9, %xmm10
        movaps    %xmm11, %xmm8
        mulsd     %xmm9, %xmm8
        movaps    %xmm10, %xmm13
        movaps    %xmm8, %xmm14
        movsd     .L_2il0floatpacket.9(%rip), %xmm15
        lea       32+_Q1(%rip), %rbx
        lea       56+_Q1(%rip), %rax
        subsd     %xmm12, %xmm13
        subsd     %xmm11, %xmm14
        subsd     %xmm13, %xmm10
        subsd     %xmm14, %xmm8
        subsd     %xmm10, %xmm12
        subsd     %xmm8, %xmm11
        movaps    %xmm10, %xmm4
        movaps    %xmm8, %xmm7
        mulsd     %xmm10, %xmm4
        lea       48+_Q1(%rip), %rdx
        mulsd     %xmm8, %xmm7
        mulsd     %xmm15, %xmm10
        mulsd     %xmm15, %xmm8
        mulsd     %xmm12, %xmm10
        mulsd     %xmm11, %xmm8
        mulsd     %xmm12, %xmm12
        mulsd     %xmm11, %xmm11
        movaps    %xmm4, %xmm1
        lea       88(%rsp), %r14
        lea       40+_Q1(%rip), %rcx
        addsd     %xmm7, %xmm1
        movaps    %xmm1, %xmm5
        subsd     %xmm2, %xmm1
        subsd     %xmm4, %xmm5
        movaps    %xmm1, %xmm6
        subsd     %xmm5, %xmm7
        movsd     (%rax), %xmm5
        addsd     %xmm7, %xmm6
        movaps    %xmm6, %xmm3
        movaps    %xmm6, %xmm2
        lea       24+_Q1(%rip), %rsi
        subsd     %xmm1, %xmm3
        lea       16+_Q1(%rip), %rdi
        subsd     %xmm3, %xmm2
        subsd     %xmm3, %xmm7
        subsd     %xmm2, %xmm1
        lea       8+_Q1(%rip), %r8
        addsd     %xmm1, %xmm7
        movaps    %xmm6, %xmm1
        lea       _Q1(%rip), %r9
        lea       48+_CONSTANTS(%rip), %r10
        addsd     %xmm10, %xmm1
        movaps    %xmm1, %xmm2
        movaps    %xmm1, %xmm14
        lea       56+_CONSTANTS(%rip), %r11
        addsd     %xmm8, %xmm2
        subsd     %xmm6, %xmm14
        movaps    %xmm2, %xmm3
        subsd     %xmm14, %xmm10
        addsd     %xmm12, %xmm3
        movaps    %xmm3, %xmm4
        addsd     %xmm11, %xmm4
        mulsd     %xmm4, %xmm5
        movaps    %xmm4, %xmm13
        mulsd     %xmm4, %xmm13
        mulsd     %xmm4, %xmm9
        addsd     (%rdx), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%rcx), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%rbx), %xmm5
        mulsd     %xmm4, %xmm5
        lea       104(%rsp), %rbx
        addsd     (%rsi), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%rdi), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%r8), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%r9), %xmm5
        mulsd     %xmm13, %xmm5
        movaps    %xmm1, %xmm13
        subsd     %xmm14, %xmm13
        subsd     %xmm13, %xmm6
        addsd     %xmm6, %xmm10
        movaps    %xmm2, %xmm6
        subsd     %xmm1, %xmm6
        addsd     %xmm10, %xmm7
        subsd     %xmm6, %xmm8
        movaps    %xmm2, %xmm10
        subsd     %xmm6, %xmm10
        subsd     %xmm10, %xmm1
        addsd     %xmm1, %xmm8
        movaps    %xmm3, %xmm1
        addsd     %xmm8, %xmm7
        movaps    %xmm3, %xmm8
        subsd     %xmm2, %xmm8
        subsd     %xmm8, %xmm1
        subsd     %xmm8, %xmm12
        subsd     %xmm1, %xmm2
        movaps    %xmm4, %xmm1
        addsd     %xmm2, %xmm12
        addsd     %xmm12, %xmm7
        movaps    %xmm4, %xmm12
        subsd     %xmm3, %xmm12
        subsd     %xmm12, %xmm1
        subsd     %xmm12, %xmm11
        subsd     %xmm1, %xmm3
        movsd     (%r10), %xmm1
        addsd     %xmm3, %xmm11
        movaps    %xmm1, %xmm3
        addsd     %xmm11, %xmm7
        movaps    %xmm9, %xmm11
        addsd     %xmm7, %xmm5
        subsd     %xmm4, %xmm11
        subsd     %xmm11, %xmm9
        mulsd     %xmm9, %xmm3
        subsd     %xmm9, %xmm4
        addsd     %xmm5, %xmm4
        movsd     (%r11), %xmm5
        mulsd     %xmm5, %xmm9
        movaps    %xmm5, %xmm2
        mulsd     %xmm4, %xmm2
        mulsd     %xmm1, %xmm4
        addsd     %xmm9, %xmm2
        pxor      %xmm1, %xmm1
        addsd     %xmm4, %xmm2
        addsd     %xmm2, %xmm3
        mulsd     .L_2il0floatpacket.6(%rip), %xmm3
        movsd     %xmm3, -72(%rbx)
        jmp       ..B2.29
..B2.20:
        movsd     %xmm7, (%rsp)
        movzwl    6(%rsp), %eax
        andl      $32752, %eax
        cmpl      $16368, %eax
        je        ..B2.35
..B2.21:
        movsd     .L_2il0floatpacket.7(%rip), %xmm8
        movaps    %xmm12, %xmm3
        mulsd     %xmm8, %xmm3
        lea       56+_Q1(%rip), %rax
        movaps    %xmm3, %xmm1
        lea       32+_Q1(%rip), %rbx
        movsd     (%rax), %xmm7
        lea       88(%rsp), %r14
        lea       48+_Q1(%rip), %rdx
        subsd     %xmm12, %xmm1
        lea       40+_Q1(%rip), %rcx
        subsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm5
        subsd     %xmm3, %xmm12
        mulsd     %xmm3, %xmm5
        lea       24+_Q1(%rip), %rsi
        subsd     %xmm2, %xmm5
        movsd     .L_2il0floatpacket.9(%rip), %xmm2
        movaps    %xmm5, %xmm14
        mulsd     %xmm3, %xmm2
        lea       16+_Q1(%rip), %rdi
        mulsd     %xmm12, %xmm2
        lea       8+_Q1(%rip), %r8
        mulsd     %xmm12, %xmm12
        movaps    %xmm2, %xmm10
        lea       _Q1(%rip), %r9
        lea       48+_CONSTANTS(%rip), %r10
        addsd     %xmm12, %xmm10
        movaps    %xmm10, %xmm4
        addsd     %xmm10, %xmm14
        subsd     %xmm2, %xmm4
        mulsd     %xmm14, %xmm7
        subsd     %xmm4, %xmm12
        mulsd     %xmm14, %xmm8
        addsd     (%rdx), %xmm7
        mulsd     %xmm14, %xmm7
        movaps    %xmm14, %xmm6
        subsd     %xmm5, %xmm6
        addsd     (%rcx), %xmm7
        subsd     %xmm6, %xmm10
        mulsd     %xmm14, %xmm7
        addsd     %xmm12, %xmm10
        addsd     (%rbx), %xmm7
        mulsd     %xmm14, %xmm7
        movaps    %xmm14, %xmm12
        mulsd     %xmm14, %xmm12
        addsd     (%rsi), %xmm7
        mulsd     %xmm14, %xmm7
        movaps    %xmm8, %xmm9
        subsd     %xmm14, %xmm9
        addsd     (%rdi), %xmm7
        subsd     %xmm9, %xmm8
        mulsd     %xmm14, %xmm7
        lea       56+_CONSTANTS(%rip), %r11
        addsd     (%r8), %xmm7
        mulsd     %xmm14, %xmm7
        subsd     %xmm8, %xmm14
        movsd     (%r10), %xmm13
        lea       104(%rsp), %rbx
        movsd     (%r11), %xmm11
        movaps    %xmm13, %xmm1
        movaps    %xmm11, %xmm15
        addsd     (%r9), %xmm7
        mulsd     %xmm8, %xmm1
        mulsd     %xmm11, %xmm8
        mulsd     %xmm12, %xmm7
        addsd     %xmm7, %xmm10
        addsd     %xmm10, %xmm14
        mulsd     %xmm14, %xmm15
        mulsd     %xmm13, %xmm14
        addsd     %xmm8, %xmm15
        addsd     %xmm14, %xmm15
        addsd     %xmm15, %xmm1
        mulsd     .L_2il0floatpacket.6(%rip), %xmm1
        movsd     %xmm1, -72(%rbx)
        pxor      %xmm1, %xmm1
        jmp       ..B2.29
..B2.22:
        lea       48(%rsp), %rdi
..___tag_value_clog10.17:
        call      __libm_hypot2_k64@PLT
..___tag_value_clog10.18:
..B2.23:
        lea       72+_CONSTANTS(%rip), %rax
        lea       80+_CONSTANTS(%rip), %rdx
        mulsd     .L_2il0floatpacket.6(%rip), %xmm0
        lea       64(%rsp), %rdi
        movsd     (%rax), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     (%rdx), %xmm2
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -56(%rdi)
        movsd     -16(%rdi), %xmm0
        movsd     -8(%rdi), %xmm1
        movsd     %xmm2, (%rsp)
..___tag_value_clog10.19:
        call      __libm_log_k64@PLT
..___tag_value_clog10.20:
..B2.24:
        movsd     64(%rsp), %xmm6
        lea       88(%rsp), %r14
        movsd     .L_2il0floatpacket.6(%rip), %xmm0
        lea       104(%rsp), %rbx
        mulsd     %xmm0, %xmm6
        lea       48+_CONSTANTS(%rip), %rax
        movsd     .L_2il0floatpacket.7(%rip), %xmm4
        lea       56+_CONSTANTS(%rip), %rdx
        movsd     -32(%rbx), %xmm2
        movsd     (%rax), %xmm5
        movsd     -96(%rbx), %xmm9
        mulsd     %xmm6, %xmm4
        movaps    %xmm9, %xmm10
        mulsd     %xmm0, %xmm2
        movaps    %xmm4, %xmm1
        movsd     (%rdx), %xmm3
        subsd     %xmm6, %xmm1
        movaps    %xmm3, %xmm8
        subsd     %xmm1, %xmm4
        movaps    %xmm4, %xmm7
        pxor      %xmm1, %xmm1
        mulsd     %xmm5, %xmm7
        subsd     %xmm4, %xmm6
        mulsd     %xmm3, %xmm4
        addsd     %xmm2, %xmm6
        addsd     %xmm7, %xmm10
        mulsd     %xmm6, %xmm8
        subsd     %xmm10, %xmm9
        mulsd     %xmm5, %xmm6
        addsd     %xmm4, %xmm8
        addsd     %xmm7, %xmm9
        addsd     %xmm6, %xmm8
        movsd     -88(%rbx), %xmm0
        addsd     %xmm8, %xmm9
        addsd     (%rsp), %xmm9
        addsd     %xmm9, %xmm10
        movsd     %xmm10, -72(%rbx)
        jmp       ..B2.29
..B2.25:
        movsd     .L_2il0floatpacket.8(%rip), %xmm0
        lea       24(%rsp), %r15
        pxor      %xmm1, %xmm1
        lea       88(%rsp), %r14
        divsd     %xmm1, %xmm0
        movsd     %xmm0, -56(%r14)
        lea       104(%rsp), %rbx
        movsd     -88(%rbx), %xmm0
        jmp       ..B2.29
..B2.26:
        je        ..B2.51
..B2.27:
        cmpl      $2047, %eax
        je        ..B2.44
..B2.28:
        lea       32+_CONSTANTS(%rip), %rax
        lea       24(%rsp), %r15
        pxor      %xmm1, %xmm1
        lea       88(%rsp), %r14
        lea       104(%rsp), %rbx
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -72(%rbx)
        movsd     -88(%rbx), %xmm0
..B2.29:
        movq      (%r15), %rax
        lea       (%rsp), %rdi
        movq      %rax, 80(%rdi)
        lea       80(%rsp), %rsi
        movsd     %xmm1, (%r14)
        lea       96(%rsp), %rdx
        movsd     %xmm0, (%rdx)
        movsd     %xmm1, (%rbx)
..___tag_value_clog10.21:
        call      __libm_atan2_k64@PLT
..___tag_value_clog10.22:
..B2.61:
        movl      %eax, %edi
..B2.30:
        movzwl    6(%rsp), %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        jl        ..B2.32
..B2.31:
        movq      (%rsp), %rax
        movq      %rax, 40(%rsp)
        jmp       ..B2.33
..B2.32:
        movsd     (%rsp), %xmm2
        lea       48+_CONSTANTS(%rip), %rax
        movsd     .L_2il0floatpacket.7(%rip), %xmm1
        lea       56+_CONSTANTS(%rip), %rcx
        mulsd     %xmm2, %xmm1
        movaps    %xmm1, %xmm0
        movq      (%rax), %rdx
        subsd     %xmm2, %xmm0
        movq      (%rcx), %rsi
        lea       40(%rsp), %rcx
        movq      %rdx, 56(%rcx)
        lea       96(%rsp), %rdx
        movq      %rsi, (%rbx)
        lea       80(%rsp), %rsi
        subsd     %xmm0, %xmm1
        movsd     %xmm1, -16(%rdx)
        subsd     %xmm1, %xmm2
        addsd     -88(%rdx), %xmm2
        movsd     %xmm2, (%r14)
..___tag_value_clog10.23:
        call      __libm_mul_k64@PLT
..___tag_value_clog10.24:
..B2.33:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        movsd     32(%rsp), %xmm0
        movsd     40(%rsp), %xmm1
        cmpq      %fs:40, %rax
        jne       ..B2.43
..B2.34:
        addq      $128, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B2.35:
        testl     $1048575, 4(%rsp)
        jne       ..B2.21
..B2.36:
        cmpl      $0, (%rsp)
        jne       ..B2.21
..B2.37:
        testl     %edx, %edx
        jne       ..B2.41
..B2.38:
        testl     $1048575, 12(%rsp)
        jne       ..B2.41
..B2.39:
        cmpl      $0, 8(%rsp)
        jne       ..B2.41
..B2.40:
        lea       88(%rsp), %r14
        pxor      %xmm1, %xmm1
        lea       104(%rsp), %rbx
        movsd     %xmm1, -72(%rbx)
        jmp       ..B2.29
..B2.41:
        lea       64+_CONSTANTS(%rip), %r14
        lea       48+_CONSTANTS(%rip), %r8
        movsd     .L_2il0floatpacket.7(%rip), %xmm5
        lea       56+_CONSTANTS(%rip), %r10
        movaps    %xmm5, %xmm2
        lea       80(%rsp), %rsi
        lea       96(%rsp), %rdx
        mulsd     (%r14), %xmm11
        lea       88(%rsp), %r14
        movsd     %xmm11, -88(%rdx)
        lea       32(%rsp), %rcx
        movzwl    -82(%rdx), %eax
        movl      %eax, %ebx
        andl      $-32753, %eax
        andl      $32752, %ebx
        orl       $-49168, %eax
        movw      %ax, -82(%rdx)
        movsd     -88(%rdx), %xmm1
        mulsd     %xmm1, %xmm2
        movaps    %xmm2, %xmm0
        shrl      $4, %ebx
        subsd     %xmm1, %xmm0
        movq      (%r8), %r9
        lea       -2447(%rbx,%rbx), %edi
        movq      (%r10), %r11
        lea       104(%rsp), %rbx
        movq      %r9, (%rdx)
        subsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm6
        subsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm6
        mulsd     %xmm1, %xmm2
        mulsd     %xmm6, %xmm5
        mulsd     .L_2il0floatpacket.9(%rip), %xmm2
        movaps    %xmm5, %xmm3
        movaps    %xmm1, %xmm4
        mulsd     %xmm1, %xmm4
        subsd     %xmm6, %xmm3
        movq      %r11, (%rbx)
        subsd     %xmm3, %xmm5
        addsd     %xmm2, %xmm4
        subsd     %xmm5, %xmm6
        movsd     %xmm5, -16(%rdx)
        addsd     %xmm4, %xmm6
        movsd     %xmm6, (%r14)
..___tag_value_clog10.36:
        call      __libm_mul_k64@PLT
..___tag_value_clog10.37:
..B2.42:
        movsd     16(%rsp), %xmm0
        pxor      %xmm1, %xmm1
        jmp       ..B2.29
..B2.43:
        call      __stack_chk_fail@PLT
..B2.44:
        testl     $1048575, 28(%rsp)
        jne       ..B2.46
..B2.45:
        cmpl      $0, 24(%rsp)
        je        ..B2.28
..B2.46:
        cmpl      $2047, %edx
        jne       ..B2.57
..B2.48:
        testl     $1048575, 20(%rsp)
        jne       ..B2.57
..B2.49:
        cmpl      $0, 16(%rsp)
        jne       ..B2.57
..B2.50:
        movsd     16(%rsp), %xmm0
        lea       24(%rsp), %r15
        movaps    %xmm0, %xmm1
        lea       88(%rsp), %r14
        mulsd     %xmm0, %xmm1
        lea       104(%rsp), %rbx
        movsd     %xmm1, -72(%rbx)
        pxor      %xmm1, %xmm1
        jmp       ..B2.29
..B2.51:
        testl     $1048575, 20(%rsp)
        jne       ..B2.53
..B2.52:
        cmpl      $0, 16(%rsp)
        je        ..B2.27
..B2.53:
        cmpl      $2047, %eax
        je        ..B2.55
..B2.54:
        movsd     16(%rsp), %xmm0
        lea       24(%rsp), %r15
        movsd     (%r15), %xmm1
        lea       88(%rsp), %r14
        mulsd     %xmm0, %xmm1
        lea       104(%rsp), %rbx
        movsd     %xmm1, -72(%rbx)
        pxor      %xmm1, %xmm1
        jmp       ..B2.29
..B2.55:
        testl     $1048575, 28(%rsp)
        jne       ..B2.54
..B2.56:
        cmpl      $0, 24(%rsp)
        jne       ..B2.54
..B2.57:
        lea       24(%rsp), %r15
        movsd     (%r15), %xmm0
        lea       88(%rsp), %r14
        mulsd     %xmm0, %xmm0
        pxor      %xmm1, %xmm1
        movsd     %xmm0, -56(%r14)
        lea       104(%rsp), %rbx
        movsd     -88(%rbx), %xmm0
        jmp       ..B2.29
..B2.62:
        cmpl      $2047, %edx
        je        ..B2.51
        jmp       ..B2.27
        .align    16,0x90
	.cfi_endproc
	.type	clog10,@function
	.size	clog10,.-clog10
	.data
# -- End  clog10
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.10:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
_CONSTANTS:
	.long	0
	.long	1065353216
	.long	1818952144
	.long	1072073202
	.long	133565931
	.long	1072697336
	.long	0
	.long	592445440
	.long	0
	.long	2146435072
	.long	0
	.long	1048576
	.long	402653184
	.long	1071369083
	.long	2389363364
	.long	3190212823
	.long	0
	.long	1282408448
	.long	1342177280
	.long	1070810131
	.long	3730980389
	.long	1040445247
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,88
	.space 8, 0x00 	# pad
	.align 16
_Q1:
	.long	0
	.long	3219128320
	.long	1431655765
	.long	1070945621
	.long	193
	.long	3218079744
	.long	2576980913
	.long	1070176665
	.long	1408920289
	.long	3217380693
	.long	2428535925
	.long	1069697316
	.long	1830244366
	.long	3217031283
	.long	3038314627
	.long	1069314732
	.type	_Q1,@object
	.size	_Q1,64
	.align 8
.L_2il0floatpacket.6:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x02000000,0x41a00000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
