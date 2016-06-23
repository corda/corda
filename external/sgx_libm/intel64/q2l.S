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
	.file "q2l.c"
	.text
..TXTST0:
# -- Begin  __qtol
	.text
       .align    16,0x90
	.globl __qtol
__qtol:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___qtol.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movq      %rdi, %rdx
        movq      $0xffffffffffff, %rcx
        movq      8(%rdx), %rax
        movq      %rax, %rsi
        shrq      $48, %rsi
        andq      %rax, %rcx
        movq      (%rdx), %rdi
        movq      %rdi, %r8
        movl      %esi, %edx
        shlq      $15, %rcx
        andl      $32767, %edx
        shrq      $49, %r8
        shlq      $15, %rdi
        orq       %r8, %rcx
        cmpl      $32767, %edx
        jne       ..B1.7
..B1.2:
        orq       %rcx, %rdi
        je        ..B1.6
..B1.3:
        movq      $0x4000000000000000, %rdx
        testq     %rcx, %rdx
        jne       ..B1.5
..B1.4:
        movq      p_zero(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
..B1.5:
        orq       %rdx, %rcx
..B1.6:
        btsq      $63, %rcx
        movl      %esi, 24(%rsp)
        movq      %rcx, 16(%rsp)
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.7:
        testl     %edx, %edx
        je        ..B1.9
..B1.8:
        movq      $0x8000000000000000, %rax
        orq       %rax, %rcx
        jmp       ..B1.11
..B1.9:
        movq      %rcx, %rax
        orq       %rdi, %rax
        je        ..B1.22
..B1.10:
        movq      p_tiny(%rip), %rax
        movq      p_one(%rip), %r8
        movsd     (%rax), %xmm0
        mulsd     (%r8), %xmm0
        movsd     %xmm0, (%rsp)
..B1.11:
        testq     %rdi, %rdi
        je        ..B1.21
..B1.12:
        stmxcsr   8(%rsp)
..B1.13:
        movl      8(%rsp), %eax
        shrl      $3, %eax
        andl      $3072, %eax
        jne       ..B1.15
..B1.14:
        testq     %rdi, %rdi
        jl        ..B1.23
        jmp       ..B1.18
..B1.15:
        testl     $32768, %esi
        je        ..B1.17
..B1.16:
        cmpl      $1024, %eax
        je        ..B1.23
        jmp       ..B1.18
..B1.17:
        cmpl      $2048, %eax
        je        ..B1.23
..B1.18:
        testl     %edx, %edx
        jne       ..B1.20
..B1.19:
        movq      p_norm(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
        jmp       ..B1.21
..B1.20:
        movq      p_one(%rip), %rax
        movq      p_norm(%rip), %rdx
        movsd     (%rax), %xmm0
        addsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
..B1.21:
        movl      %esi, 24(%rsp)
        movq      %rcx, 16(%rsp)
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.22:
        movl      %esi, 24(%rsp)
        movq      $0, 16(%rsp)
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.23:
        incq      %rcx
        testl     %edx, %edx
        je        ..B1.29
..B1.24:
        testq     %rcx, %rcx
        jne       ..B1.28
..B1.25:
        incl      %esi
        movq      $0x8000000000000000, %rcx
        movl      %esi, %edx
        andl      $32767, %edx
..B1.26:
        cmpl      $32767, %edx
        jne       ..B1.18
..B1.27:
        movq      p_huge(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
        jmp       ..B1.21
..B1.28:
        movq      $0x8000000000000000, %rdx
        movl      $1, %r8d
        cmpq      %rdi, %rdx
        movl      $0, %edi
        movq      p_one(%rip), %r9
        movq      p_norm(%rip), %r10
        cmove     %r8d, %edi
        testl     %eax, %eax
        movl      $0, %eax
        movsd     (%r9), %xmm0
        cmove     %r8d, %eax
        addsd     (%r10), %xmm0
        andl      %eax, %edi
        notl      %edi
        movslq    %edi, %rdi
        movsd     %xmm0, (%rsp)
        andq      %rdi, %rcx
        jmp       ..B1.21
..B1.29:
        movq      $0x8000000000000000, %rdx
        movl      $1, %r8d
        cmpq      %rdi, %rdx
        movl      $0, %edi
        cmove     %r8d, %edi
        testl     %eax, %eax
        movl      $0, %eax
        cmove     %r8d, %eax
        andl      %eax, %edi
        notl      %edi
        movslq    %edi, %rdi
        andq      %rdi, %rcx
        jge       ..B1.19
..B1.30:
        incl      %esi
        movl      %esi, %edx
        andl      $32767, %edx
        jmp       ..B1.26
        .align    16,0x90
	.cfi_endproc
	.type	__qtol,@function
	.size	__qtol,.-__qtol
	.data
# -- End  __qtol
	.data
	.align 8
	.align 8
p_zero:
	.quad	d_zero
	.type	p_zero,@object
	.size	p_zero,8
	.align 8
p_tiny:
	.quad	d_tiny
	.type	p_tiny,@object
	.size	p_tiny,8
	.align 8
p_one:
	.quad	d_one
	.type	p_one,@object
	.size	p_one,8
	.align 8
p_norm:
	.quad	d_norm
	.type	p_norm,@object
	.size	p_norm,8
	.align 8
p_huge:
	.quad	d_huge
	.type	p_huge,@object
	.size	p_huge,8
	.section .rodata, "a"
	.align 8
	.align 8
d_zero:
	.long	0x00000000,0x00000000
	.type	d_zero,@object
	.size	d_zero,8
	.align 8
d_tiny:
	.long	0x00000001,0x00000000
	.type	d_tiny,@object
	.size	d_tiny,8
	.align 8
d_one:
	.long	0x00000000,0x3ff00000
	.type	d_one,@object
	.size	d_one,8
	.align 8
d_norm:
	.long	0x00000000,0x00100000
	.type	d_norm,@object
	.size	d_norm,8
	.align 8
d_huge:
	.long	0xffffffff,0x7fefffff
	.type	d_huge,@object
	.size	d_huge,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
