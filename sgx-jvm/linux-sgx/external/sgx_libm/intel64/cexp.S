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
	.file "cexp.c"
	.text
..TXTST0:
# -- Begin  cexp
	.text
       .align    16,0x90
	.globl cexp
cexp:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cexp.1:
..L2:

..B1.2:
        movq      %rsp, %rax
        subq      $280, %rsp
	.cfi_def_cfa_offset 288
        movq      %rax, 184(%rsp)
        movq      %xmm0, 160(%rsp)
        movq      %xmm1, 168(%rsp)
        movq      %rbx, 192(%rsp)
        movq      160(%rsp), %xmm0
        movq      168(%rsp), %xmm5
        movdqa    %xmm0, %xmm7
        lea       cexp_table(%rip), %r8
        movdqa    (%r8), %xmm1
        shufps    $85, %xmm5, %xmm7
        movdqa    16(%r8), %xmm2
        pand      %xmm1, %xmm7
        pcmpgtd   %xmm7, %xmm2
        movmskps  %xmm2, %edx
        cmpl      $5, %edx
        unpcklpd  %xmm0, %xmm0
        jne       .L_2TAG_PACKET_0.0.1
.L_2TAG_PACKET_1.0.1:
        movapd    32(%r8), %xmm1
        movapd    48(%r8), %xmm6
        movapd    64(%r8), %xmm2
        movapd    80(%r8), %xmm3
        mulpd     %xmm0, %xmm1
        unpcklpd  %xmm5, %xmm5
        addpd     %xmm6, %xmm1
        movapd    96(%r8), %xmm7
        movd      %xmm1, %edx
        subpd     %xmm6, %xmm1
        movq      $63, %rax
        mulpd     %xmm5, %xmm7
        mulpd     %xmm1, %xmm2
        movapd    112(%r8), %xmm4
        andq      %rdx, %rax
        mulpd     %xmm1, %xmm3
        movapd    128(%r8), %xmm1
        subpd     %xmm2, %xmm0
        cvtsd2si  %xmm7, %ecx
        lea       (,%rax,8), %rax
        subpd     %xmm3, %xmm0
        addpd     %xmm6, %xmm7
        movapd    4512(%r8,%rax,2), %xmm2
        mulpd     %xmm0, %xmm4
        addl      $1865216, %ecx
        movapd    %xmm0, %xmm3
        subpd     %xmm6, %xmm7
        mulpd     %xmm0, %xmm0
        movapd    144(%r8), %xmm6
        andq      $63, %rcx
        addpd     %xmm4, %xmm1
        mulsd     %xmm0, %xmm0
        movapd    160(%r8), %xmm4
        addsd     %xmm2, %xmm3
        lea       (,%rcx,8), %rcx
        unpckhpd  %xmm2, %xmm2
        mulpd     %xmm1, %xmm0
        movapd    %xmm5, %xmm1
        lea       416(%r8,%rcx,8), %rcx
        addsd     %xmm0, %xmm3
        mulpd     %xmm7, %xmm4
        unpckhpd  %xmm0, %xmm0
        mulpd     %xmm7, %xmm6
        subpd     %xmm4, %xmm5
        mulpd     176(%r8), %xmm7
        subpd     %xmm4, %xmm1
        addsd     %xmm3, %xmm0
        movapd    192(%r8), %xmm3
        unpcklpd  %xmm0, %xmm0
        movapd    %xmm1, %xmm4
        subpd     %xmm6, %xmm1
        mulpd     %xmm2, %xmm0
        mulpd     %xmm5, %xmm3
        movapd    %xmm2, (%rsp)
        movapd    %xmm0, 16(%rsp)
        movapd    16(%rcx), %xmm0
        subpd     %xmm6, %xmm5
        movapd    208(%r8), %xmm2
        subpd     %xmm1, %xmm4
        mulpd     %xmm5, %xmm3
        mulpd     %xmm5, %xmm5
        subpd     %xmm6, %xmm4
        mulpd     %xmm1, %xmm0
        movapd    (%rcx), %xmm6
        subpd     %xmm4, %xmm7
        movapd    48(%rcx), %xmm4
        addpd     %xmm4, %xmm6
        subpd     %xmm6, %xmm0
        mulpd     %xmm0, %xmm7
        movapd    16(%rcx), %xmm0
        mulpd     %xmm1, %xmm6
        mulpd     %xmm5, %xmm2
        mulpd     %xmm1, %xmm4
        mulpd     %xmm5, %xmm6
        mulpd     %xmm5, %xmm0
        mulpd     %xmm5, %xmm5
        addpd     224(%r8), %xmm3
        mulpd     (%rcx), %xmm1
        addpd     240(%r8), %xmm2
        mulpd     %xmm5, %xmm3
        movapd    %xmm4, %xmm5
        addpd     16(%rcx), %xmm4
        addpd     %xmm3, %xmm2
        movsd     %xmm2, %xmm3
        unpckhpd  %xmm2, %xmm2
        andl      $-64, %edx
        unpcklpd  %xmm3, %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm3, %xmm6
        movapd    %xmm1, %xmm0
        addpd     %xmm4, %xmm1
        movapd    16(%rcx), %xmm3
        subpd     %xmm4, %xmm3
        subpd     %xmm1, %xmm4
        addpd     32(%rcx), %xmm7
        addpd     %xmm5, %xmm3
        movapd    (%rsp), %xmm5
        addpd     %xmm0, %xmm4
        addpd     %xmm3, %xmm7
        movapd    16(%rsp), %xmm3
        movd      %edx, %xmm0
        addpd     %xmm4, %xmm7
        movapd    %xmm1, %xmm4
        addpd     %xmm6, %xmm7
        movapd    256(%r8), %xmm6
        pshufd    $17, %xmm0, %xmm0
        addpd     %xmm2, %xmm7
.L_2TAG_PACKET_2.0.1:
        movapd    %xmm5, %xmm2
        addpd     %xmm3, %xmm5
        addpd     %xmm7, %xmm1
        andpd     %xmm6, %xmm5
        andpd     %xmm1, %xmm6
        pand      272(%r8), %xmm1
        subpd     %xmm5, %xmm2
        subpd     %xmm6, %xmm4
        addpd     %xmm3, %xmm2
        movdqa    288(%r8), %xmm3
        addpd     %xmm7, %xmm4
        por       304(%r8), %xmm6
        psubd     %xmm1, %xmm3
        movapd    320(%r8), %xmm7
        psrld     $14, %xmm1
        mulpd     %xmm3, %xmm4
        paddd     %xmm1, %xmm0
        paddd     %xmm0, %xmm7
        movapd    %xmm2, %xmm1
        pcmpgtd   320(%r8), %xmm7
        mulpd     %xmm4, %xmm2
        movmskps  %xmm7, %eax
        cmpl      $10, %eax
        jne       .L_2TAG_PACKET_3.0.1
        mulpd     %xmm5, %xmm4
        pslld     $14, %xmm0
        mulpd     %xmm6, %xmm1
        addpd     %xmm4, %xmm2
        mulpd     %xmm5, %xmm6
        addpd     %xmm1, %xmm2
        addpd     %xmm6, %xmm2
        mulpd     %xmm2, %xmm0
        movq      %xmm0, %xmm1
        pshufd    $14, %xmm0, %xmm0
        movq      192(%rsp), %rbx
        movq      184(%rsp), %rsp
        ret       
.L_2TAG_PACKET_3.0.1:
        psrad     $6, %xmm0
        psubd     336(%r8), %xmm0
        pshufd    $245, %xmm0, %xmm2
        psrad     $32, %xmm0
        pand      400(%r8), %xmm0
        pand      384(%r8), %xmm2
        por       %xmm2, %xmm0
        movdqa    %xmm0, 64(%rsp)
        movq      %xmm5, 112(%rsp)
        movq      %xmm1, 120(%rsp)
        movhpd    %xmm6, 128(%rsp)
        movhpd    %xmm4, 136(%rsp)
        movq      %xmm6, 144(%rsp)
        movq      %xmm4, 152(%rsp)
        movq      72(%rsp), %rdi
        lea       128(%rsp), %rsi
        lea       112(%rsp), %rdx
        lea       96(%rsp), %rcx
        call      __libm_mul_k64@PLT
        movq      64(%rsp), %rdi
        lea       144(%rsp), %rsi
        lea       112(%rsp), %rdx
        lea       104(%rsp), %rcx
        call      __libm_mul_k64@PLT
        movq      96(%rsp), %xmm0
        movq      104(%rsp), %xmm1
        movq      192(%rsp), %rbx
        movq      184(%rsp), %rsp
        ret       
.L_2TAG_PACKET_0.0.1:
        pextrw    $1, %xmm7, %eax
        testl     $3, %edx
        jne       .L_2TAG_PACKET_4.0.1
        movapd    352(%r8), %xmm1
        cmpl      $32752, %eax
        jae       .L_2TAG_PACKET_5.0.1
        cmpltpd   %xmm0, %xmm1
        movmskpd  %xmm1, %edx
        cmpl      $1, %edx
        jne       .L_2TAG_PACKET_5.0.1
        jmp       .L_2TAG_PACKET_6.0.1
.L_2TAG_PACKET_4.0.1:
        testl     $2, %edx
        je        .L_2TAG_PACKET_6.0.1
        xorpd     %xmm0, %xmm0
.L_2TAG_PACKET_6.0.1:
        movdqa    %xmm5, %xmm1
        testl     $12, %edx
        psllq     $1, %xmm1
        je        .L_2TAG_PACKET_5.0.1
        pxor      %xmm2, %xmm2
        pextrw    $3, %xmm5, %ecx
        testl     $8, %edx
        pcmpeqd   %xmm2, %xmm1
        je        .L_2TAG_PACKET_1.0.1
        movmskps  %xmm1, %eax
        xorl      %edx, %edx
        movapd    368(%r8), %xmm6
        andl      $3, %eax
        movapd    304(%r8), %xmm4
        andl      $32752, %ecx
        cmpl      $864, %ecx
        jl        .L_2TAG_PACKET_5.0.1
        testl     $32752, %ecx
        jne       .L_2TAG_PACKET_7.0.1
        cmpl      $3, %eax
        je        .L_2TAG_PACKET_5.0.1
        andpd     %xmm5, %xmm6
        orpd      %xmm4, %xmm5
        orpd      %xmm4, %xmm6
        movl      $-65408, %edx
        subsd     %xmm6, %xmm5
.L_2TAG_PACKET_7.0.1:
        movsd     %xmm5, %xmm4
        movapd    32(%r8), %xmm3
        movapd    48(%r8), %xmm6
        movapd    64(%r8), %xmm5
        movapd    80(%r8), %xmm1
        mulpd     %xmm0, %xmm3
        addpd     %xmm6, %xmm3
        movd      %xmm3, %ecx
        subpd     %xmm6, %xmm3
        movq      $63, %rax
        mulpd     %xmm3, %xmm5
        movd      %edx, %xmm6
        movapd    112(%r8), %xmm7
        andq      %rcx, %rax
        mulpd     %xmm3, %xmm1
        movapd    128(%r8), %xmm3
        subpd     %xmm5, %xmm0
        lea       (,%rax,8), %rax
        subpd     %xmm1, %xmm0
        movapd    4512(%r8,%rax,2), %xmm5
        mulpd     %xmm0, %xmm7
        movapd    %xmm0, %xmm1
        mulpd     %xmm0, %xmm0
        andl      $-64, %ecx
        addpd     %xmm7, %xmm3
        pshufd    $243, %xmm6, %xmm6
        mulsd     %xmm0, %xmm0
        addsd     %xmm5, %xmm1
        unpckhpd  %xmm5, %xmm5
        mulpd     %xmm0, %xmm3
        xorpd     %xmm7, %xmm7
        addsd     %xmm3, %xmm1
        movd      %ecx, %xmm0
        unpckhpd  %xmm3, %xmm3
        addsd     %xmm1, %xmm3
        pshufd    $17, %xmm0, %xmm0
        unpcklpd  %xmm3, %xmm3
        paddd     %xmm6, %xmm0
        mulpd     %xmm5, %xmm3
        movapd    256(%r8), %xmm6
        movapd    %xmm4, %xmm1
        jmp       .L_2TAG_PACKET_2.0.1
.L_2TAG_PACKET_5.0.1:
        movq      160(%rsp), %xmm0
        movq      168(%rsp), %xmm1
        call      __libm_internal_cexp
        movq      192(%rsp), %rbx
        movq      184(%rsp), %rsp
        ret       
	.cfi_def_cfa_offset 8
..B1.3:
        .align    16,0x90
	.cfi_endproc
	.type	cexp,@function
	.size	cexp,.-cexp
	.data
# -- End  cexp
	.text
# -- Begin  __libm_internal_cexp
	.text
       .align    16,0x90
__libm_internal_cexp:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value___libm_internal_cexp.6:
..L7:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        movq      %fs:40, %rax
        subq      $120, %rsp
	.cfi_def_cfa_offset 144
        movsd     %xmm0, 40(%rsp)
        xorq      %rsp, %rax
        movzwl    46(%rsp), %ebp
        andl      $32752, %ebp
        shrl      $4, %ebp
        movsd     %xmm1, 48(%rsp)
        cmpl      $2047, %ebp
        movq      %rax, 112(%rsp)
        jge       ..B2.13
..B2.2:
        testl     %ebp, %ebp
        jle       ..B2.41
..B2.3:
        movzwl    54(%rsp), %ebx
        andl      $32752, %ebx
        shrl      $4, %ebx
        cmpl      $2047, %ebx
        jge       ..B2.14
..B2.4:
        testl     %ebx, %ebx
        jle       ..B2.39
..B2.5:
        cmpl      $1034, %ebp
        jl        ..B2.8
..B2.6:
        movb      47(%rsp), %al
        lea       _CONSTANTS(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movq      (%rdx,%rcx,8), %rbx
        movq      %rbx, 56(%rsp)
        movsd     56(%rsp), %xmm0
        movsd     56(%rsp), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 56(%rsp)
        movsd     56(%rsp), %xmm2
        movsd     48(%rsp), %xmm0
        movsd     %xmm2, (%rsp)
        call      cos@PLT
..B2.51:
        movsd     (%rsp), %xmm1
        mulsd     %xmm0, %xmm1
        movzwl    54(%rsp), %eax
        testl     $32752, %eax
        movsd     %xmm1, 24(%rsp)
        jle       ..B2.38
..B2.7:
        movsd     56(%rsp), %xmm0
        movsd     %xmm0, (%rsp)
        movsd     48(%rsp), %xmm0
        call      sin@PLT
..B2.52:
        movsd     (%rsp), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 32(%rsp)
        jmp       ..B2.29
..B2.8:
        movsd     40(%rsp), %xmm0
        lea       16(%rsp), %rdi
        lea       64(%rsp), %rsi
..___tag_value___libm_internal_cexp.13:
        call      __libm_exp_k64@PLT
..___tag_value___libm_internal_cexp.14:
..B2.9:
        movsd     48(%rsp), %xmm0
        lea       96(%rsp), %rdi
        lea       80(%rsp), %rsi
..___tag_value___libm_internal_cexp.15:
        call      __libm_sincos_k64@PLT
..___tag_value___libm_internal_cexp.16:
..B2.10:
        movl      16(%rsp), %edi
        lea       64(%rsp), %rsi
        lea       80(%rsp), %rdx
        lea       (%rsp), %rcx
..___tag_value___libm_internal_cexp.17:
        call      __libm_mul_k64@PLT
..___tag_value___libm_internal_cexp.18:
..B2.11:
        movq      (%rsp), %rax
        lea       64(%rsp), %rsi
        movl      -48(%rsi), %edi
        lea       96(%rsp), %rdx
        movq      %rax, -72(%rdx)
        lea       8(%rsp), %rcx
..___tag_value___libm_internal_cexp.19:
        call      __libm_mul_k64@PLT
..___tag_value___libm_internal_cexp.20:
..B2.12:
        movq      8(%rsp), %rax
        movq      %rax, 32(%rsp)
        jmp       ..B2.29
..B2.13:
        movzwl    54(%rsp), %ebx
        andl      $32752, %ebx
        shrl      $4, %ebx
..B2.14:
        testl     %ebx, %ebx
        jne       ..B2.18
..B2.15:
        testl     $1048575, 52(%rsp)
        jne       ..B2.18
..B2.16:
        cmpl      $0, 48(%rsp)
        jne       ..B2.18
..B2.17:
        movsd     40(%rsp), %xmm0
        call      exp@PLT
..B2.53:
        movq      48(%rsp), %rax
        movsd     %xmm0, 24(%rsp)
        movq      %rax, 32(%rsp)
        jmp       ..B2.29
..B2.18:
        cmpl      $2047, %ebx
        jge       ..B2.21
..B2.19:
        movsd     40(%rsp), %xmm0
        call      exp@PLT
..B2.55:
        movsd     %xmm0, 8(%rsp)
        movsd     48(%rsp), %xmm0
        movsd     %xmm0, (%rsp)
        call      __libm_sse2_sincos@PLT
..B2.54:
        mulsd     8(%rsp), %xmm1
        movaps    %xmm0, %xmm2
        movsd     %xmm1, 24(%rsp)
        testl     %ebx, %ebx
        jle       ..B2.44
..B2.20:
        movsd     8(%rsp), %xmm0
        mulsd     %xmm2, %xmm0
        movsd     %xmm0, 32(%rsp)
        jmp       ..B2.29
..B2.21:
        cmpl      $2047, %ebp
        jge       ..B2.23
..B2.22:
        movsd     48(%rsp), %xmm0
        call      sin@PLT
..B2.56:
        movsd     %xmm0, 24(%rsp)
        movsd     %xmm0, 32(%rsp)
        jmp       ..B2.29
..B2.23:
        testl     $1048575, 44(%rsp)
        jne       ..B2.25
..B2.24:
        cmpl      $0, 40(%rsp)
        je        ..B2.26
..B2.25:
        movsd     40(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     48(%rsp), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm0, 24(%rsp)
        movsd     %xmm1, 32(%rsp)
        jmp       ..B2.29
..B2.26:
        testb     $-128, 47(%rsp)
        je        ..B2.28
..B2.27:
        movsd     48(%rsp), %xmm0
        lea       zeros(%rip), %rax
        mulsd     %xmm0, %xmm0
        movq      (%rax), %rdx
        movsd     %xmm0, 48(%rsp)
        movq      %rdx, 24(%rsp)
        movq      %rdx, 32(%rsp)
        jmp       ..B2.29
..B2.28:
        movsd     40(%rsp), %xmm0
        call      exp@PLT
..B2.58:
        movsd     %xmm0, 24(%rsp)
        movsd     48(%rsp), %xmm0
        call      sin@PLT
..B2.57:
        movsd     %xmm0, 32(%rsp)
..B2.29:
        movzwl    30(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.32
..B2.30:
        testl     $1048575, 28(%rsp)
        jne       ..B2.35
..B2.31:
        cmpl      $0, 24(%rsp)
        jne       ..B2.35
..B2.32:
        movzwl    38(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.36
..B2.33:
        testl     $1048575, 36(%rsp)
        jne       ..B2.35
..B2.34:
        cmpl      $0, 32(%rsp)
        je        ..B2.36
..B2.35:
        lea       _DBL_MIN_NORMAL(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 56(%rsp)
..B2.36:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        movsd     24(%rsp), %xmm0
        movsd     32(%rsp), %xmm1
        cmpq      %fs:40, %rax
        jne       ..B2.48
..B2.37:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B2.38:
        movsd     56(%rsp), %xmm0
        mulsd     48(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        jmp       ..B2.29
..B2.39:
        testl     $1048575, 52(%rsp)
        jne       ..B2.5
..B2.40:
        cmpl      $0, 48(%rsp)
        jne       ..B2.5
        jmp       ..B2.14
..B2.41:
        testl     $1048575, 44(%rsp)
        jne       ..B2.3
..B2.42:
        cmpl      $0, 40(%rsp)
        jne       ..B2.3
        jmp       ..B2.13
..B2.44:
        testl     %ebp, %ebp
        jne       ..B2.47
..B2.45:
        testl     $1048575, 44(%rsp)
        jne       ..B2.47
..B2.46:
        cmpl      $0, 40(%rsp)
        je        ..B2.20
..B2.47:
        movsd     (%rsp), %xmm0
        mulsd     8(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        jmp       ..B2.29
..B2.48:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__libm_internal_cexp,@function
	.size	__libm_internal_cexp,.-__libm_internal_cexp
	.data
# -- End  __libm_internal_cexp
	.section .rodata, "a"
	.align 16
	.align 16
cexp_table:
	.long	2147483647
	.long	2147483647
	.long	2147483647
	.long	2147483647
	.long	1082607888
	.long	810549248
	.long	1089863680
	.long	808452095
	.long	1697350398
	.long	1079448903
	.long	1697350398
	.long	1079448903
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	4277796864
	.long	1065758274
	.long	4277796864
	.long	1065758274
	.long	3164486458
	.long	1025308570
	.long	3164486458
	.long	1025308570
	.long	1841940611
	.long	1076125488
	.long	1841940611
	.long	1076125488
	.long	2983266533
	.long	1065422964
	.long	1432790106
	.long	1069897045
	.long	653061504
	.long	1067799895
	.long	4294959483
	.long	1071644671
	.long	442499072
	.long	1032893537
	.long	442499072
	.long	1032893537
	.long	1413480448
	.long	1069097467
	.long	1413480448
	.long	1069097467
	.long	771977331
	.long	996350346
	.long	771977331
	.long	996350346
	.long	2773927732
	.long	1053236707
	.long	436314138
	.long	1056571808
	.long	286331153
	.long	1065423121
	.long	1431655765
	.long	1067799893
	.long	436314138
	.long	3207201184
	.long	381774871
	.long	3210133868
	.long	1431655765
	.long	3217380693
	.long	0
	.long	3219128320
	.long	4160749568
	.long	4294967295
	.long	4160749568
	.long	4294967295
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	0
	.long	2145386496
	.long	0
	.long	2145386496
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	2147352640
	.long	0
	.long	2147352640
	.long	0
	.long	1023
	.long	0
	.long	1023
	.long	3576508498
	.long	3230091536
	.long	565433817
	.long	1083619556
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	4294967295
	.long	0
	.long	4294967295
	.long	0
	.long	0
	.long	4294967295
	.long	0
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	3217031168
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	3220176896
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	3219128320
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	3217031168
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	1069547520
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	1071644672
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	1072693248
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	1069547520
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	235107661
	.long	1018002367
	.long	1048019040
	.long	1072704666
	.long	896005651
	.long	1015861842
	.long	3541402996
	.long	1072716208
	.long	1642514529
	.long	1012987726
	.long	410360776
	.long	1072727877
	.long	1568897900
	.long	1016568486
	.long	1828292879
	.long	1072739672
	.long	1882168529
	.long	1010744893
	.long	852742562
	.long	1072751596
	.long	509852888
	.long	1017336174
	.long	3490863952
	.long	1072763649
	.long	653277307
	.long	1017431380
	.long	2930322911
	.long	1072775834
	.long	1649557430
	.long	1017729363
	.long	1014845818
	.long	1072788152
	.long	1058231231
	.long	1015777676
	.long	3949972341
	.long	1072800603
	.long	1044000607
	.long	1016786167
	.long	828946858
	.long	1072813191
	.long	1151779725
	.long	1015705409
	.long	2288159958
	.long	1072825915
	.long	3819481236
	.long	1016499965
	.long	1853186616
	.long	1072838778
	.long	2552227826
	.long	1015039787
	.long	1709341917
	.long	1072851781
	.long	1829350193
	.long	1015216097
	.long	4112506593
	.long	1072864925
	.long	1913391795
	.long	1015756674
	.long	2799960843
	.long	1072878213
	.long	1303423926
	.long	1015238005
	.long	171030293
	.long	1072891646
	.long	1574172746
	.long	1016061241
	.long	2992903935
	.long	1072905224
	.long	3424156969
	.long	1017196428
	.long	926591434
	.long	1072918951
	.long	1938513547
	.long	1017631273
	.long	887463926
	.long	1072932827
	.long	2804567149
	.long	1015390024
	.long	1276261410
	.long	1072946854
	.long	631083525
	.long	1017690182
	.long	569847337
	.long	1072961034
	.long	1623370770
	.long	1011049453
	.long	1617004845
	.long	1072975368
	.long	3667985273
	.long	1013894369
	.long	3049340112
	.long	1072989858
	.long	3145379760
	.long	1014403278
	.long	3577096743
	.long	1073004506
	.long	2603100681
	.long	1017152460
	.long	1990012070
	.long	1073019314
	.long	3249202951
	.long	1017448880
	.long	1453150081
	.long	1073034283
	.long	419288974
	.long	1016280325
	.long	917841882
	.long	1073049415
	.long	3793507337
	.long	1016095713
	.long	3712504873
	.long	1073064711
	.long	728023093
	.long	1016345318
	.long	363667784
	.long	1073080175
	.long	2582678538
	.long	1017123460
	.long	2956612996
	.long	1073095806
	.long	7592966
	.long	1016721543
	.long	2186617380
	.long	1073111608
	.long	228611441
	.long	1016696141
	.long	1719614412
	.long	1073127582
	.long	2261665670
	.long	1017457593
	.long	1013258798
	.long	1073143730
	.long	544148907
	.long	1017323666
	.long	3907805043
	.long	1073160053
	.long	2383914918
	.long	1017143586
	.long	1447192520
	.long	1073176555
	.long	1176412038
	.long	1017267372
	.long	1944781190
	.long	1073193236
	.long	2882956373
	.long	1013312481
	.long	919555682
	.long	1073210099
	.long	3154077648
	.long	1016528543
	.long	2571947538
	.long	1073227145
	.long	348651999
	.long	1016405780
	.long	2604962540
	.long	1073244377
	.long	3253791412
	.long	1015920431
	.long	1110089947
	.long	1073261797
	.long	1509121860
	.long	1014756995
	.long	2568320822
	.long	1073279406
	.long	2617649212
	.long	1017340090
	.long	2966275556
	.long	1073297207
	.long	553214634
	.long	1016457425
	.long	2682146383
	.long	1073315202
	.long	730975783
	.long	1014083580
	.long	2191782032
	.long	1073333393
	.long	1486499517
	.long	1016818996
	.long	2069751140
	.long	1073351782
	.long	2595788928
	.long	1016407932
	.long	2990417244
	.long	1073370371
	.long	1853053619
	.long	1015310724
	.long	1434058175
	.long	1073389163
	.long	2462790535
	.long	1015814775
	.long	2572866477
	.long	1073408159
	.long	3693944214
	.long	1017259110
	.long	3092190714
	.long	1073427362
	.long	2979333550
	.long	1017188654
	.long	4076559942
	.long	1073446774
	.long	174054861
	.long	1014300631
	.long	2420883922
	.long	1073466398
	.long	816778419
	.long	1014197934
	.long	3716502172
	.long	1073486235
	.long	3507050924
	.long	1015341199
	.long	777507147
	.long	1073506289
	.long	1821514088
	.long	1013410604
	.long	3706687593
	.long	1073526560
	.long	920623539
	.long	1016295433
	.long	1242007931
	.long	1073547053
	.long	2789017511
	.long	1014276997
	.long	3707479175
	.long	1073567768
	.long	3586233004
	.long	1015962192
	.long	64696965
	.long	1073588710
	.long	474650514
	.long	1016642419
	.long	863738718
	.long	1073609879
	.long	1614448851
	.long	1014281732
	.long	3884662774
	.long	1073631278
	.long	2450082086
	.long	1016164135
	.long	2728693977
	.long	1073652911
	.long	1101668360
	.long	1015989180
	.long	3999357479
	.long	1073674779
	.long	835814894
	.long	1015702697
	.long	1533953344
	.long	1073696886
	.long	1301400989
	.long	1014466875
	.long	2174652632
	.long	1073719233
	.type	cexp_table,@object
	.size	cexp_table,5536
	.align 16
_CONSTANTS:
	.long	0
	.long	2145386496
	.long	0
	.long	1048576
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,16
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.align 8
zeros:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.type	zeros,@object
	.size	zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
