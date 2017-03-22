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
	.file "ctanh.c"
	.text
..TXTST0:
# -- Begin  ctanhf
	.text
       .align    16,0x90
	.globl ctanhf
ctanhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_ctanhf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_ctanhf.4:
        call      ctanh@PLT
..___tag_value_ctanhf.5:
..B1.10:
        unpcklpd  %xmm1, %xmm0
..B1.2:
        cvtpd2ps  %xmm0, %xmm0
        movsd     %xmm0, 4(%rsp)
        movzwl    6(%rsp), %eax
        testl     $32640, %eax
        jne       ..B1.4
..B1.3:
        testl     $8388607, 4(%rsp)
        jne       ..B1.6
..B1.4:
        movzwl    10(%rsp), %eax
        testl     $32640, %eax
        jne       ..B1.7
..B1.5:
        testl     $8388607, 8(%rsp)
        je        ..B1.7
..B1.6:
        movl      $8388608, (%rsp)
        movss     (%rsp), %xmm1
        movss     (%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movsd     4(%rsp), %xmm0
        movss     %xmm1, (%rsp)
..B1.7:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	ctanhf,@function
	.size	ctanhf,.-ctanhf
	.data
# -- End  ctanhf
	.text
# -- Begin  ctanh
	.text
       .align    16,0x90
	.globl ctanh
ctanh:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_ctanh.8:
..L9:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        movq      %fs:40, %rax
        subq      $160, %rsp
	.cfi_def_cfa_offset 192
        movsd     %xmm0, 40(%rsp)
        xorq      %rsp, %rax
        movzwl    46(%rsp), %r12d
        andl      $32752, %r12d
        shrl      $4, %r12d
        movsd     %xmm1, 48(%rsp)
        cmpl      $2047, %r12d
        movq      %rax, 152(%rsp)
        jge       ..B2.33
..B2.2:
        testl     %r12d, %r12d
        jle       ..B2.61
..B2.3:
        movzwl    54(%rsp), %edi
        movl      %edi, %ebx
        andl      $32752, %ebx
        shrl      $4, %ebx
        cmpl      $2047, %ebx
        jge       ..B2.34
..B2.4:
        cmpl      $1032, %r12d
        jl        ..B2.8
..B2.5:
        movb      47(%rsp), %dl
        lea       _DBL_MIN_NORMAL(%rip), %rax
        andb      $-128, %dl
        lea       ones(%rip), %rcx
        shrb      $7, %dl
        movsd     (%rax), %xmm1
        movzbl    %dl, %esi
        testl     %ebx, %ebx
        movsd     (%rcx,%rsi,8), %xmm0
        subsd     %xmm1, %xmm0
        movsd     %xmm0, 24(%rsp)
        jle       ..B2.57
..B2.6:
        movsd     %xmm1, 16(%rsp)
        testl     $32752, %edi
        movsd     16(%rsp), %xmm1
        movsd     16(%rsp), %xmm0
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 16(%rsp)
        movsd     16(%rsp), %xmm0
        jle       ..B2.56
..B2.7:
        movsd     48(%rsp), %xmm0
        call      tan@PLT
..B2.71:
        lea       32(%rsp), %rax
        pxor      %xmm1, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, (%rax)
        movsd     -8(%rax), %xmm0
        movhpd    (%rax), %xmm0
        jmp       ..B2.54
..B2.8:
        movsd     40(%rsp), %xmm0
        lea       104(%rsp), %rdi
..___tag_value_ctanh.17:
        call      __libm_cosh_k64@PLT
..___tag_value_ctanh.18:
..B2.72:
        movl      %eax, %ebx
..B2.9:
        movsd     104(%rsp), %xmm2
        movsd     112(%rsp), %xmm3
        movaps    %xmm2, %xmm4
        movzwl    46(%rsp), %eax
        addsd     %xmm3, %xmm4
        movaps    %xmm4, %xmm1
        andl      $32752, %eax
        cmpl      $13168, %eax
        subsd     %xmm4, %xmm2
        addsd     %xmm3, %xmm2
        movsd     .L_2il0floatpacket.13(%rip), %xmm3
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        subsd     %xmm4, %xmm0
        subsd     %xmm0, %xmm1
        movsd     %xmm1, 104(%rsp)
        subsd     %xmm1, %xmm4
        addsd     %xmm2, %xmm4
        movsd     %xmm4, 112(%rsp)
        jge       ..B2.11
..B2.10:
        lea       16+_CONSTANTS(%rip), %r12
        lea       80(%rsp), %r14
        movsd     -40(%r14), %xmm0
        mulsd     (%r12), %xmm0
        movsd     %xmm0, -8(%r14)
        movzwl    -2(%r14), %eax
        movl      %eax, %r12d
        andl      $32752, %r12d
        andl      $-32753, %eax
        shrl      $4, %r12d
        orl       $-49168, %eax
        movw      %ax, -2(%r14)
        addl      $-1279, %r12d
        pxor      %xmm0, %xmm0
        jmp       ..B2.13
..B2.11:
        movsd     40(%rsp), %xmm0
        lea       72(%rsp), %rdi
..___tag_value_ctanh.19:
        call      __libm_sinh_k64@PLT
..___tag_value_ctanh.20:
..B2.73:
        movsd     .L_2il0floatpacket.13(%rip), %xmm3
        movl      %eax, %r12d
..B2.12:
        lea       80(%rsp), %r14
        movsd     (%r14), %xmm0
..B2.13:
        movsd     72(%rsp), %xmm4
        movaps    %xmm0, %xmm5
        movaps    %xmm3, %xmm2
        lea       56(%rsp), %rdi
        addsd     %xmm4, %xmm5
        mulsd     %xmm5, %xmm2
        subsd     %xmm5, %xmm4
        movaps    %xmm2, %xmm1
        lea       88(%rsp), %rsi
        subsd     %xmm5, %xmm1
        addsd     %xmm0, %xmm4
        subsd     %xmm1, %xmm2
        movsd     -40(%rsi), %xmm0
        subsd     %xmm2, %xmm5
        movsd     %xmm2, -16(%rsi)
        addsd     %xmm4, %xmm5
        movsd     %xmm5, (%r14)
..___tag_value_ctanh.21:
        call      __libm_sincos_k64@PLT
..___tag_value_ctanh.22:
..B2.14:
        movsd     88(%rsp), %xmm4
        lea       8+_CONSTANTS(%rip), %rax
        movsd     96(%rsp), %xmm2
        movaps    %xmm4, %xmm1
        movsd     .L_2il0floatpacket.13(%rip), %xmm3
        addl      %r12d, %ebx
        movsd     112(%rsp), %xmm6
        movaps    %xmm3, %xmm10
        movsd     (%r14), %xmm13
        movaps    %xmm6, %xmm11
        mulsd     %xmm13, %xmm11
        addsd     %xmm2, %xmm1
        movsd     104(%rsp), %xmm7
        lea       (%r12,%r12), %r14d
        movaps    %xmm7, %xmm8
        subsd     %xmm1, %xmm4
        mulsd     %xmm13, %xmm7
        addsd     %xmm2, %xmm4
        movaps    %xmm3, %xmm2
        mulsd     %xmm1, %xmm2
        movaps    %xmm2, %xmm0
        movsd     (%rax), %xmm5
        subsd     %xmm1, %xmm0
        movsd     %xmm5, 8(%rsp)
        subsd     %xmm0, %xmm2
        movsd     72(%rsp), %xmm0
        subsd     %xmm2, %xmm1
        mulsd     %xmm0, %xmm6
        addsd     %xmm5, %xmm2
        mulsd     %xmm0, %xmm8
        addsd     %xmm4, %xmm1
        addsd     %xmm6, %xmm11
        movaps    %xmm8, %xmm12
        movaps    %xmm0, %xmm5
        mulsd     %xmm0, %xmm5
        addsd     %xmm7, %xmm11
        mulsd     %xmm13, %xmm0
        mulsd     %xmm13, %xmm13
        addsd     %xmm11, %xmm12
        addsd     %xmm0, %xmm0
        mulsd     %xmm12, %xmm10
        subsd     %xmm12, %xmm8
        addsd     %xmm13, %xmm0
        addsd     %xmm8, %xmm11
        movaps    %xmm10, %xmm9
        movaps    %xmm2, %xmm6
        movsd     %xmm5, 120(%rsp)
        subsd     %xmm12, %xmm9
        mulsd     %xmm2, %xmm6
        subsd     %xmm9, %xmm10
        movzwl    126(%rsp), %edx
        subsd     %xmm10, %xmm12
        andl      $32752, %edx
        addsd     %xmm11, %xmm12
        shrl      $4, %edx
        movsd     %xmm2, 88(%rsp)
        mulsd     %xmm1, %xmm2
        movsd     %xmm6, 24(%rsp)
        lea       (%rdx,%r12,2), %eax
        movzwl    30(%rsp), %edx
        addsd     %xmm2, %xmm2
        andl      $32752, %edx
        shrl      $4, %edx
        movsd     %xmm1, 96(%rsp)
        mulsd     %xmm1, %xmm1
        movsd     %xmm10, 136(%rsp)
        lea       -112(%rdx), %ecx
        movsd     %xmm12, 144(%rsp)
        cmpl      %ecx, %eax
        movsd     %xmm0, 128(%rsp)
        addsd     %xmm1, %xmm2
        jg        ..B2.16
..B2.15:
        movsd     %xmm2, 32(%rsp)
        xorl      %r14d, %r14d
        jmp       ..B2.22
..B2.16:
        addl      $112, %edx
        cmpl      %edx, %eax
        jge       ..B2.21
..B2.17:
        movl      %r14d, %eax
        negl      %eax
        movsd     .L_2il0floatpacket.14(%rip), %xmm1
        addl      $1023, %eax
        movsd     %xmm1, (%rsp)
        andl      $2047, %eax
        movzwl    6(%rsp), %edx
        movaps    %xmm5, %xmm1
        shll      $4, %eax
        andl      $-32753, %edx
        orl       %eax, %edx
        movw      %dx, 6(%rsp)
        movsd     (%rsp), %xmm4
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm2
        comisd    %xmm6, %xmm5
        addsd     %xmm6, %xmm1
        movsd     %xmm6, 24(%rsp)
        movsd     %xmm2, 32(%rsp)
        jbe       ..B2.19
..B2.18:
        subsd     %xmm1, %xmm5
        addsd     %xmm6, %xmm5
        addsd     %xmm2, %xmm5
        addsd     %xmm0, %xmm5
        jmp       ..B2.20
..B2.19:
        subsd     %xmm1, %xmm6
        addsd     %xmm5, %xmm6
        addsd     %xmm0, %xmm6
        addsd     %xmm2, %xmm6
        movaps    %xmm6, %xmm5
..B2.20:
        movaps    %xmm5, %xmm6
        addsd     %xmm1, %xmm6
        subsd     %xmm6, %xmm1
        addsd     %xmm1, %xmm5
        movaps    %xmm5, %xmm2
        jmp       ..B2.22
..B2.21:
        movsd     %xmm2, 32(%rsp)
        movaps    %xmm5, %xmm6
        movaps    %xmm0, %xmm2
..B2.22:
        movaps    %xmm6, %xmm0
        movaps    %xmm3, %xmm5
        movsd     .L_2il0floatpacket.14(%rip), %xmm1
        movaps    %xmm3, %xmm7
        movsd     .L_2il0floatpacket.14(%rip), %xmm8
        negl      %r14d
        addl      %r14d, %ebx
        lea       136(%rsp), %rsi
        movl      %ebx, %edi
        lea       120(%rsp), %rdx
        addsd     %xmm2, %xmm0
        mulsd     %xmm0, %xmm5
        subsd     %xmm0, %xmm6
        addsd     %xmm2, %xmm6
        movaps    %xmm5, %xmm2
        subsd     %xmm0, %xmm2
        subsd     %xmm2, %xmm5
        divsd     %xmm5, %xmm1
        mulsd     %xmm1, %xmm7
        subsd     %xmm5, %xmm0
        movaps    %xmm7, %xmm4
        lea       24(%rsp), %rcx
        subsd     %xmm1, %xmm4
        addsd     %xmm0, %xmm6
        subsd     %xmm4, %xmm7
        mulsd     %xmm7, %xmm5
        mulsd     %xmm7, %xmm6
        subsd     %xmm5, %xmm8
        movsd     %xmm7, (%rdx)
        subsd     %xmm6, %xmm8
        movsd     .L_2il0floatpacket.14(%rip), %xmm6
        addsd     %xmm8, %xmm6
        mulsd     %xmm6, %xmm8
        mulsd     %xmm7, %xmm8
        movsd     %xmm8, 8(%rdx)
..___tag_value_ctanh.23:
        call      __libm_mul_k64@PLT
..___tag_value_ctanh.24:
..B2.23:
        movzwl    54(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        movsd     .L_2il0floatpacket.13(%rip), %xmm3
        testl     %eax, %eax
        jne       ..B2.28
..B2.24:
        testl     $1048575, 52(%rsp)
        jne       ..B2.28
..B2.25:
        cmpl      $0, 48(%rsp)
        jne       ..B2.28
..B2.26:
        movq      48(%rsp), %rax
        movq      %rax, 32(%rsp)
        movq      152(%rsp), %rdx
        xorq      %rsp, %rdx
        movsd     48(%rsp), %xmm1
        movsd     24(%rsp), %xmm0
        cmpq      %fs:40, %rdx
        jne       ..B2.60
..B2.27:
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -32
	.cfi_offset 12, -16
	.cfi_offset 14, -24
..B2.28:
        cmpl      $823, %eax
        jl        ..B2.30
..B2.29:
        xorl      %ecx, %ecx
        lea       64(%rsp), %rax
        movsd     (%rax), %xmm0
        jmp       ..B2.31
..B2.30:
        lea       16+_CONSTANTS(%rip), %rax
        movsd     48(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        lea       64(%rsp), %rax
        movsd     %xmm0, -8(%rax)
        movzwl    -2(%rax), %edx
        movl      %edx, %ecx
        andl      $32752, %ecx
        andl      $-32753, %edx
        shrl      $4, %ecx
        orl       $-49168, %edx
        movw      %dx, -2(%rax)
        addl      $-1279, %ecx
        pxor      %xmm0, %xmm0
..B2.31:
        movsd     56(%rsp), %xmm1
        movaps    %xmm0, %xmm6
        movaps    %xmm3, %xmm2
        addl      %ecx, %r14d
        movsd     8(%rsp), %xmm4
        lea       136(%rsp), %rsi
        movsd     -40(%rsi), %xmm5
        lea       120(%rsp), %rdx
        movsd     -32(%rdx), %xmm7
        lea       32(%rsp), %rcx
        movl      %r14d, %edi
        addsd     %xmm1, %xmm6
        mulsd     %xmm6, %xmm2
        subsd     %xmm6, %xmm1
        addsd     %xmm0, %xmm1
        movaps    %xmm2, %xmm0
        subsd     %xmm6, %xmm0
        subsd     %xmm0, %xmm2
        subsd     %xmm2, %xmm6
        addsd     %xmm2, %xmm4
        addsd     %xmm1, %xmm6
        movaps    %xmm6, %xmm8
        movaps    %xmm4, %xmm10
        mulsd     %xmm5, %xmm8
        mulsd     %xmm4, %xmm5
        mulsd     %xmm7, %xmm10
        mulsd     %xmm6, %xmm7
        addsd     %xmm5, %xmm8
        movaps    %xmm10, %xmm11
        addsd     %xmm7, %xmm8
        movsd     %xmm6, (%rax)
        addsd     %xmm8, %xmm11
        mulsd     %xmm11, %xmm3
        subsd     %xmm11, %xmm10
        movaps    %xmm3, %xmm9
        addsd     %xmm8, %xmm10
        subsd     %xmm11, %xmm9
        movsd     %xmm4, -64(%rdx)
        subsd     %xmm9, %xmm3
        movsd     %xmm3, 16(%rdx)
        subsd     %xmm3, %xmm11
        addsd     %xmm10, %xmm11
        movsd     %xmm11, 24(%rdx)
..___tag_value_ctanh.36:
        call      __libm_mul_k64@PLT
..___tag_value_ctanh.37:
..B2.32:
        movsd     24(%rsp), %xmm0
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.33:
        movzwl    54(%rsp), %ebx
        andl      $32752, %ebx
        shrl      $4, %ebx
..B2.34:
        testl     %ebx, %ebx
        jne       ..B2.37
..B2.35:
        testl     $1048575, 52(%rsp)
        jne       ..B2.37
..B2.36:
        cmpl      $0, 48(%rsp)
        je        ..B2.39
..B2.37:
        cmpl      $2047, %ebx
        jge       ..B2.45
..B2.38:
        cmpl      $2047, %r12d
        je        ..B2.65
..B2.39:
        movsd     40(%rsp), %xmm0
        call      tanh@PLT
..B2.74:
        movsd     %xmm0, 24(%rsp)
        testl     %r12d, %r12d
        jne       ..B2.42
..B2.40:
        testl     $1048575, 44(%rsp)
        jne       ..B2.42
..B2.41:
        cmpl      $0, 40(%rsp)
        je        ..B2.44
..B2.42:
        testl     %ebx, %ebx
        jle       ..B2.64
..B2.43:
        movsd     48(%rsp), %xmm0
        call      tan@PLT
..B2.75:
        pxor      %xmm1, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 32(%rsp)
        movsd     24(%rsp), %xmm0
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.44:
        movsd     48(%rsp), %xmm0
        call      tan@PLT
..B2.76:
        movsd     %xmm0, 32(%rsp)
        movsd     24(%rsp), %xmm0
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.45:
        cmpl      $2047, %r12d
        jge       ..B2.47
..B2.46:
        movsd     48(%rsp), %xmm0
        call      tan@PLT
..B2.77:
        movsd     %xmm0, 32(%rsp)
        movsd     %xmm0, 24(%rsp)
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.47:
        testl     $1048575, 44(%rsp)
        jne       ..B2.49
..B2.48:
        cmpl      $0, 40(%rsp)
        je        ..B2.50
..B2.49:
        movsd     40(%rsp), %xmm0
        call      tanh@PLT
..B2.78:
        movsd     48(%rsp), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 32(%rsp)
        movsd     %xmm0, 24(%rsp)
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.50:
        testl     $1048575, 52(%rsp)
        jne       ..B2.52
..B2.51:
        cmpl      $0, 48(%rsp)
        je        ..B2.53
..B2.52:
        movsd     48(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 48(%rsp)
        movsd     40(%rsp), %xmm0
        call      tanh@PLT
..B2.79:
        lea       zeros(%rip), %rax
        movhpd    (%rax), %xmm0
        movsd     %xmm0, 24(%rsp)
        movhpd    %xmm0, 32(%rsp)
        jmp       ..B2.54
..B2.53:
        movsd     40(%rsp), %xmm0
        call      tanh@PLT
..B2.80:
        movb      55(%rsp), %dl
        lea       zeros(%rip), %rax
        andb      $-128, %dl
        lea       ones(%rip), %rcx
        shrb      $7, %dl
        movsd     (%rax), %xmm1
        movzbl    %dl, %ebx
        mulsd     (%rcx,%rbx,8), %xmm1
        unpcklpd  %xmm1, %xmm0
        movsd     %xmm0, 24(%rsp)
        movhpd    %xmm0, 32(%rsp)
..B2.54:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.60
..B2.55:
        movaps    %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm1
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -32
	.cfi_offset 12, -16
	.cfi_offset 14, -24
..B2.56:
        movb      55(%rsp), %dl
        lea       32(%rsp), %rax
        movsd     %xmm0, (%rax)
        andb      $-128, %dl
        movb      7(%rax), %cl
        andb      $127, %cl
        orb       %dl, %cl
        movsd     -8(%rax), %xmm0
        movb      %cl, 7(%rax)
        movhpd    (%rax), %xmm0
        jmp       ..B2.54
..B2.57:
        testl     $1048575, 52(%rsp)
        jne       ..B2.6
..B2.58:
        cmpl      $0, 48(%rsp)
        jne       ..B2.6
..B2.59:
        pxor      %xmm0, %xmm0
        jmp       ..B2.56
..B2.60:
        call      __stack_chk_fail@PLT
..B2.61:
        testl     $1048575, 44(%rsp)
        jne       ..B2.3
..B2.62:
        cmpl      $0, 40(%rsp)
        jne       ..B2.3
        jmp       ..B2.33
..B2.64:
        pxor      %xmm0, %xmm0
        mulsd     48(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        movsd     24(%rsp), %xmm0
        movhpd    32(%rsp), %xmm0
        jmp       ..B2.54
..B2.65:
        testl     $1048575, 44(%rsp)
        jne       ..B2.45
..B2.66:
        cmpl      $0, 40(%rsp)
        jne       ..B2.45
..B2.67:
        movsd     40(%rsp), %xmm0
        call      tanh@PLT
..B2.81:
        movsd     %xmm0, 24(%rsp)
        jmp       ..B2.42
        .align    16,0x90
	.cfi_endproc
	.type	ctanh,@function
	.size	ctanh,.-ctanh
	.data
# -- End  ctanh
	.section .rodata, "a"
	.align 16
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x02000000,0x41a00000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
zeros:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.type	zeros,@object
	.size	zeros,16
	.align 4
.L_2il0floatpacket.12:
	.long	0x00800000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.align 4
_CONSTANTS:
	.long	0
	.long	2145386496
	.long	0
	.long	1048576
	.long	0
	.long	1341128704
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,24
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
