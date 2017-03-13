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
	.file "csinh.c"
	.text
..TXTST0:
# -- Begin  csinhf
	.text
       .align    16,0x90
	.globl csinhf
csinhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_csinhf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_csinhf.4:
        call      csinh@PLT
..___tag_value_csinhf.5:
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
	.type	csinhf,@function
	.size	csinhf,.-csinhf
	.data
# -- End  csinhf
	.text
# -- Begin  csinh
	.text
       .align    16,0x90
	.globl csinh
csinh:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_csinh.8:
..L9:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        movq      %fs:40, %rax
        subq      $112, %rsp
	.cfi_def_cfa_offset 144
        movsd     %xmm0, 48(%rsp)
        xorq      %rsp, %rax
        movzwl    54(%rsp), %r14d
        andl      $32752, %r14d
        shrl      $4, %r14d
        movsd     %xmm1, 56(%rsp)
        cmpl      $2047, %r14d
        movq      %rax, 96(%rsp)
        jge       ..B2.23
..B2.2:
        testl     %r14d, %r14d
        jle       ..B2.42
..B2.3:
        movzwl    62(%rsp), %r13d
        andl      $32752, %r13d
        shrl      $4, %r13d
        cmpl      $2047, %r13d
        jge       ..B2.24
..B2.4:
        testl     %r13d, %r13d
        jle       ..B2.40
..B2.5:
        cmpl      $1034, %r14d
        jl        ..B2.11
..B2.6:
        lea       _CONSTANTS(%rip), %rax
        movsd     48(%rsp), %xmm1
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm1
        movsd     %xmm0, 32(%rsp)
        movsd     56(%rsp), %xmm0
        movsd     %xmm1, 24(%rsp)
        movsd     %xmm0, 16(%rsp)
        call      __libm_sse2_sincos@PLT
..B2.52:
        movaps    %xmm0, %xmm2
        testl     %r13d, %r13d
        movsd     24(%rsp), %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, (%rsp)
        jle       ..B2.8
..B2.7:
        movsd     32(%rsp), %xmm0
        mulsd     %xmm2, %xmm0
        movsd     %xmm0, 32(%rsp)
        jmp       ..B2.9
..B2.8:
        movsd     32(%rsp), %xmm0
        mulsd     16(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
..B2.9:
        movsd     %xmm0, 8(%rsp)
        movq      96(%rsp), %rax
        xorq      %rsp, %rax
        movsd     (%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B2.39
..B2.10:
        movsd     32(%rsp), %xmm1
        addq      $112, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B2.11:
        movsd     48(%rsp), %xmm0
        lea       64(%rsp), %rdi
..___tag_value_csinh.28:
        call      __libm_cosh_k64@PLT
..___tag_value_csinh.29:
..B2.53:
        movl      %eax, %r15d
..B2.12:
        movsd     64(%rsp), %xmm0
        movsd     72(%rsp), %xmm4
        movaps    %xmm0, %xmm5
        movsd     .L_2il0floatpacket.11(%rip), %xmm3
        addsd     %xmm4, %xmm5
        mulsd     %xmm5, %xmm3
        movaps    %xmm5, %xmm1
        subsd     %xmm0, %xmm1
        movaps    %xmm3, %xmm2
        subsd     %xmm1, %xmm4
        subsd     %xmm5, %xmm2
        movzwl    54(%rsp), %eax
        subsd     %xmm2, %xmm3
        andl      $32752, %eax
        subsd     %xmm3, %xmm5
        movsd     %xmm3, 64(%rsp)
        cmpl      $13168, %eax
        addsd     %xmm4, %xmm5
        movsd     %xmm5, 72(%rsp)
        jge       ..B2.14
..B2.13:
        lea       16+_CONSTANTS(%rip), %r14
        lea       40(%rsp), %rdx
        movsd     8(%rdx), %xmm0
        pxor      %xmm5, %xmm5
        mulsd     (%r14), %xmm0
        movsd     %xmm0, -8(%rdx)
        movzwl    -2(%rdx), %eax
        movl      %eax, %r14d
        andl      $32752, %r14d
        andl      $-32753, %eax
        shrl      $4, %r14d
        orl       $-49168, %eax
        movw      %ax, -2(%rdx)
        addl      $-1279, %r14d
        jmp       ..B2.16
..B2.14:
        movsd     48(%rsp), %xmm0
        lea       32(%rsp), %rdi
..___tag_value_csinh.30:
        call      __libm_sinh_k64@PLT
..___tag_value_csinh.31:
..B2.54:
        movl      %eax, %r14d
..B2.15:
        lea       40(%rsp), %rdx
        movsd     (%rdx), %xmm5
..B2.16:
        movsd     32(%rsp), %xmm0
        movaps    %xmm5, %xmm4
        movsd     .L_2il0floatpacket.11(%rip), %xmm3
        lea       16(%rsp), %rdi
        addsd     %xmm0, %xmm4
        mulsd     %xmm4, %xmm3
        movaps    %xmm4, %xmm1
        subsd     %xmm0, %xmm1
        movaps    %xmm3, %xmm2
        lea       80(%rsp), %rsi
        movsd     -24(%rsi), %xmm0
        subsd     %xmm4, %xmm2
        subsd     %xmm1, %xmm5
        subsd     %xmm2, %xmm3
        movsd     %xmm3, -48(%rsi)
        subsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        movsd     %xmm5, (%rdx)
..___tag_value_csinh.32:
        call      __libm_sincos_k64@PLT
..___tag_value_csinh.33:
..B2.17:
        movsd     80(%rsp), %xmm0
        lea       8+_CONSTANTS(%rip), %rax
        movsd     88(%rsp), %xmm4
        movaps    %xmm0, %xmm3
        movsd     .L_2il0floatpacket.11(%rip), %xmm6
        addsd     %xmm4, %xmm3
        mulsd     %xmm3, %xmm6
        movaps    %xmm3, %xmm1
        subsd     %xmm0, %xmm1
        movaps    %xmm6, %xmm2
        subsd     %xmm1, %xmm4
        subsd     %xmm3, %xmm2
        movsd     (%rax), %xmm5
        subsd     %xmm2, %xmm6
        movzwl    62(%rsp), %edx
        subsd     %xmm6, %xmm3
        addsd     %xmm5, %xmm6
        addsd     %xmm3, %xmm4
        andl      $32752, %edx
        movsd     %xmm4, 88(%rsp)
        cmpl      $13168, %edx
        movsd     %xmm6, 80(%rsp)
        jl        ..B2.19
..B2.18:
        xorl      %r13d, %r13d
        lea       24(%rsp), %rdx
        movsd     (%rdx), %xmm5
        jmp       ..B2.20
..B2.19:
        lea       16+_CONSTANTS(%rip), %r13
        lea       24(%rsp), %rdx
        movsd     32(%rdx), %xmm0
        pxor      %xmm5, %xmm5
        mulsd     (%r13), %xmm0
        movsd     %xmm0, -8(%rdx)
        movzwl    -2(%rdx), %eax
        movl      %eax, %r13d
        andl      $32752, %r13d
        andl      $-32753, %eax
        shrl      $4, %r13d
        orl       $-49168, %eax
        movw      %ax, -2(%rdx)
        addl      $-1279, %r13d
..B2.20:
        movsd     16(%rsp), %xmm0
        movaps    %xmm5, %xmm4
        movsd     .L_2il0floatpacket.11(%rip), %xmm3
        lea       32(%rsp), %rsi
        movl      %r14d, %edi
        lea       (%rsp), %rcx
        addsd     %xmm0, %xmm4
        mulsd     %xmm4, %xmm3
        movaps    %xmm4, %xmm1
        subsd     %xmm0, %xmm1
        movaps    %xmm3, %xmm2
        subsd     %xmm1, %xmm5
        subsd     %xmm4, %xmm2
        subsd     %xmm2, %xmm3
        movsd     %xmm3, 16(%rcx)
        subsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        movsd     %xmm5, (%rdx)
        lea       80(%rsp), %rdx
..___tag_value_csinh.34:
        call      __libm_mul_k64@PLT
..___tag_value_csinh.35:
..B2.21:
        addl      %r15d, %r13d
        lea       64(%rsp), %rsi
        movl      %r13d, %edi
        lea       16(%rsp), %rdx
        lea       8(%rsp), %rcx
..___tag_value_csinh.36:
        call      __libm_mul_k64@PLT
..___tag_value_csinh.37:
..B2.22:
        movsd     (%rsp), %xmm0
        movhpd    8(%rsp), %xmm0
        jmp       ..B2.37
..B2.23:
        movzwl    62(%rsp), %r13d
        andl      $32752, %r13d
        shrl      $4, %r13d
..B2.24:
        testl     %r13d, %r13d
        jne       ..B2.28
..B2.25:
        testl     $1048575, 60(%rsp)
        jne       ..B2.28
..B2.26:
        cmpl      $0, 56(%rsp)
        jne       ..B2.28
..B2.27:
        movsd     48(%rsp), %xmm0
        call      sinh@PLT
..B2.55:
        movhpd    56(%rsp), %xmm0
        movsd     %xmm0, (%rsp)
        movhpd    %xmm0, 8(%rsp)
        jmp       ..B2.37
..B2.28:
        cmpl      $2047, %r13d
        jge       ..B2.31
..B2.29:
        movsd     48(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        call      sinh@PLT
..B2.57:
        movsd     %xmm0, 40(%rsp)
        movsd     56(%rsp), %xmm0
        movsd     %xmm0, 16(%rsp)
        call      __libm_sse2_sincos@PLT
..B2.56:
        movsd     %xmm0, 24(%rsp)
        testl     %r13d, %r13d
        movsd     40(%rsp), %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, (%rsp)
        jle       ..B2.45
..B2.30:
        movsd     32(%rsp), %xmm0
        call      cosh@PLT
..B2.58:
        mulsd     24(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        movsd     (%rsp), %xmm0
        movhpd    8(%rsp), %xmm0
        jmp       ..B2.37
..B2.31:
        movsd     56(%rsp), %xmm0
        call      sin@PLT
..B2.59:
        movsd     %xmm0, 8(%rsp)
        cmpl      $2047, %r14d
        je        ..B2.35
..B2.32:
        testl     %r14d, %r14d
        jne       ..B2.36
..B2.33:
        testl     $1048575, 52(%rsp)
        jne       ..B2.36
..B2.34:
        cmpl      $0, 48(%rsp)
        jne       ..B2.36
..B2.35:
        movsd     48(%rsp), %xmm0
        call      sinh@PLT
..B2.60:
        movsd     %xmm0, (%rsp)
        movhpd    8(%rsp), %xmm0
        jmp       ..B2.37
..B2.36:
        movsd     48(%rsp), %xmm2
        mulsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm0
        movhpd    8(%rsp), %xmm0
        movsd     %xmm2, (%rsp)
..B2.37:
        movq      96(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.39
..B2.38:
        movaps    %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm1
        addq      $112, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B2.39:
        call      __stack_chk_fail@PLT
..B2.40:
        testl     $1048575, 60(%rsp)
        jne       ..B2.5
..B2.41:
        cmpl      $0, 56(%rsp)
        jne       ..B2.5
        jmp       ..B2.24
..B2.42:
        testl     $1048575, 52(%rsp)
        jne       ..B2.3
..B2.43:
        cmpl      $0, 48(%rsp)
        jne       ..B2.3
        jmp       ..B2.23
..B2.45:
        testl     %r14d, %r14d
        jne       ..B2.48
..B2.46:
        testl     $1048575, 52(%rsp)
        jne       ..B2.48
..B2.47:
        cmpl      $0, 48(%rsp)
        je        ..B2.30
..B2.48:
        movsd     32(%rsp), %xmm0
        call      cosh@PLT
..B2.61:
        mulsd     16(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        movsd     (%rsp), %xmm0
        movhpd    8(%rsp), %xmm0
        jmp       ..B2.37
        .align    16,0x90
	.cfi_endproc
	.type	csinh,@function
	.size	csinh,.-csinh
	.data
# -- End  csinh
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.11:
	.long	0x02000000,0x41a00000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 4
.L_2il0floatpacket.10:
	.long	0x00800000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
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
