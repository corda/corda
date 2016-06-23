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
	.file "remainder_gen.c"
	.text
..TXTST0:
# -- Begin  remainder
	.text
       .align    16,0x90
	.globl remainder
remainder:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_remainder.1:
..L2:

        movq      $0x7fffffffffffffff, %rdi
        movd      %xmm0, %r9
        movq      $0x7ff0000000000000, %rax
        movd      %xmm1, %rdx
        movsd     %xmm0, -32(%rsp)
        movsd     %xmm1, -40(%rsp)
        andq      %r9, %rdi
        btrq      $63, %rdx
        movq      $0x7ff0000000000000, %rcx
        andq      %rdi, %rax
        andq      %rdx, %rcx
        xorb      %r8b, %r8b
        shrq      $52, %rax
        shrq      $52, %rcx
        addl      $-1023, %eax
        movq      %rdi, -16(%rsp)
        addl      $-1023, %ecx
        movq      %rdx, -8(%rsp)
        sarq      $63, %r9
        movq      $0, -24(%rsp)
        cmpl      $1024, %eax
        movsd     -16(%rsp), %xmm2
        movsd     -8(%rsp), %xmm0
        je        ..B1.5
..B1.2:
        cmpl      $1024, %ecx
        je        ..B1.5
..B1.3:
        testq     %rdi, %rdi
        je        ..B1.5
..B1.4:
        testq     %rdx, %rdx
        jne       ..B1.6
..B1.5:
        movb      $1, %r8b
..B1.6:
        movq      $0xfffffffffffff, %rsi
        andq      %rsi, %rdi
        andq      %rsi, %rdx
        testb     %r8b, %r8b
        je        ..B1.17
..B1.7:
        testq     %rdi, %rdi
        je        ..B1.9
..B1.8:
        cmpl      $1024, %eax
        je        ..B1.43
..B1.9:
        testq     %rdx, %rdx
        je        ..B1.11
..B1.10:
        cmpl      $1024, %ecx
        je        ..B1.44
..B1.11:
        movsd     -24(%rsp), %xmm1
        ucomisd   %xmm1, %xmm0
        jp        ..B1.12
        je        ..B1.45
..B1.12:
        movsd     -24(%rsp), %xmm0
        ucomisd   %xmm0, %xmm2
        jp        ..B1.13
        je        ..B1.15
..B1.13:
        testq     %rdi, %rdi
        jne       ..B1.15
..B1.14:
        cmpl      $1024, %eax
        je        ..B1.45
..B1.15:
        movsd     -32(%rsp), %xmm0
..B1.16:
        ret       
..B1.17:
        comisd    %xmm2, %xmm0
        jb        ..B1.23
..B1.18:
        ucomisd   %xmm0, %xmm2
        jp        ..B1.19
        je        ..B1.57
..B1.19:
        cmpl      $1023, %eax
        je        ..B1.21
..B1.20:
        movaps    %xmm2, %xmm1
        addsd     %xmm2, %xmm1
        comisd    %xmm1, %xmm0
        jae       ..B1.22
..B1.21:
        testl     %r9d, %r9d
        subsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm0
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        jne       ..L3
        movaps    %xmm2, %xmm0
..L3:
        ret       
..B1.22:
        movsd     -32(%rsp), %xmm0
        ret       
..B1.23:
        cmpl      $-1023, %eax
        je        ..B1.53
..B1.24:
        movq      $0x10000000000000, %r8
        orq       %r8, %rdi
..B1.25:
        cmpl      $-1023, %ecx
        je        ..B1.49
..B1.26:
        movq      $0x10000000000000, %r8
        orq       %r8, %rdx
..B1.27:
        subl      %ecx, %eax
        xorl      %r8d, %r8d
        xorl      %r11d, %r11d
        incl      %eax
        je        ..B1.31
..B1.28:
        movl      $1, %r10d
..B1.29:
        movq      %rdi, %r8
        incl      %r11d
        subq      %rdx, %r8
        cmpq      %rdx, %rdi
        cmovae    %r8, %rdi
        movl      $0, %r8d
        cmovae    %r10, %r8
        addq      %rdi, %rdi
        cmpl      %eax, %r11d
        jb        ..B1.29
..B1.31:
        testq     %rdi, %rdi
        je        ..B1.58
..B1.32:
        shrq      $1, %rdi
        cmpq      %rsi, %rdi
        ja        ..B1.36
..B1.34:
        addq      %rdi, %rdi
        decl      %ecx
        cmpq      %rsi, %rdi
        jbe       ..B1.34
..B1.36:
        cmpl      $-1022, %ecx
        jl        ..B1.38
..B1.37:
        movslq    %ecx, %rcx
        andq      %rdi, %rsi
        addq      $1023, %rcx
        jmp       ..B1.39
..B1.38:
        negl      %ecx
        movq      %rdi, %rsi
        addl      $2, %ecx
        shrq      %cl, %rsi
        xorl      %ecx, %ecx
..B1.39:
        shlq      $52, %rcx
        orq       %rsi, %rcx
        movq      %rcx, -40(%rsp)
        movsd     -40(%rsp), %xmm2
        movaps    %xmm2, %xmm1
        addsd     %xmm2, %xmm1
        comisd    %xmm0, %xmm1
        jb        ..B1.42
..B1.40:
        ucomisd   %xmm1, %xmm0
        jp        ..B1.41
        je        ..B1.48
..B1.41:
        subsd     %xmm0, %xmm2
..B1.42:
        movaps    %xmm2, %xmm0
        testl     %r9d, %r9d
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        jne       ..L4
        movaps    %xmm2, %xmm0
..L4:
        ret       
..B1.43:
        movsd     -32(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        ret       
..B1.44:
        movsd     -40(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        ret       
..B1.45:
        movsd     -24(%rsp), %xmm0
        movsd     -24(%rsp), %xmm1
        divsd     %xmm1, %xmm0
        ret       
..B1.48:
        movaps    %xmm2, %xmm0
        testq     %r8, %r8
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        je        ..B1.42
        movaps    %xmm0, %xmm2
        jmp       ..B1.42
..B1.49:
        testq     %rdx, %rdx
        je        ..B1.26
..B1.50:
        movl      $-1022, %ecx
..B1.51:
        addq      %rdx, %rdx
        decl      %ecx
        cmpq      %rsi, %rdx
        jbe       ..B1.51
        jmp       ..B1.27
..B1.53:
        testq     %rdi, %rdi
        je        ..B1.24
..B1.54:
        movl      $-1022, %eax
..B1.55:
        addq      %rdi, %rdi
        decl      %eax
        cmpq      %rsi, %rdi
        jbe       ..B1.55
        jmp       ..B1.25
..B1.57:
        movsd     -24(%rsp), %xmm0
        mulsd     -32(%rsp), %xmm0
        ret       
..B1.58:
        movsd     -24(%rsp), %xmm0
        mulsd     -32(%rsp), %xmm0
        movq      %rdi, -40(%rsp)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	remainder,@function
	.size	remainder,.-remainder
	.data
# -- End  remainder
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x33333333,0x3ffb3333
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End

	.globl drem
	.equ drem, remainder

