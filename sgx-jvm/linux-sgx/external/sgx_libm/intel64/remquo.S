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
	.file "remquo.c"
	.text
..TXTST0:
# -- Begin  remquo
	.text
       .align    16,0x90
	.globl remquo
remquo:
# parameter 1: %xmm0
# parameter 2: %xmm1
# parameter 3: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_remquo.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        movq      $0x7fffffffffffffff, %r8
        movd      %xmm0, %rdx
        movq      $0x7fffffffffffffff, %rsi
        movd      %xmm1, %r9
        movsd     %xmm0, -32(%rsp)
        movsd     %xmm1, -40(%rsp)
        andq      %rdx, %r8
        andq      %r9, %rsi
        sarq      $63, %rdx
        movq      $0x7ff0000000000000, %rcx
        sarq      $63, %r9
        andq      %rsi, %rcx
        movl      $-1, %eax
        movl      $1, %ebx
        cmpl      %edx, %r9d
        movq      $0x7ff0000000000000, %r9
        movq      %r8, -16(%rsp)
        movq      %rsi, -8(%rsp)
        cmovne    %eax, %ebx
        xorb      %bpl, %bpl
        andq      %r8, %r9
        shrq      $52, %r9
        shrq      $52, %rcx
        addl      $-1023, %r9d
        addl      $-1023, %ecx
        movq      $0, -24(%rsp)
        cmpl      $1024, %r9d
        movsd     -16(%rsp), %xmm2
        movsd     -8(%rsp), %xmm0
        je        ..B1.5
..B1.2:
        cmpl      $1024, %ecx
        je        ..B1.5
..B1.3:
        testq     %r8, %r8
        je        ..B1.5
..B1.4:
        testq     %rsi, %rsi
        jne       ..B1.6
..B1.5:
        movb      $1, %bpl
..B1.6:
        movq      $0xfffffffffffff, %r11
        movq      %r8, %rax
        movq      %rsi, %r10
        andq      %r11, %rax
        andq      %r11, %r10
        testb     %bpl, %bpl
        je        ..B1.18
..B1.7:
        movl      $0, (%rdi)
        testq     %rax, %rax
        je        ..B1.9
..B1.8:
        cmpl      $1024, %r9d
        je        ..B1.47
..B1.9:
        testq     %r10, %r10
        je        ..B1.11
..B1.10:
        cmpl      $1024, %ecx
        je        ..B1.48
..B1.11:
        testq     %rsi, %rsi
        je        ..B1.49
..B1.12:
        testq     %r8, %r8
        je        ..B1.16
..B1.14:
        testq     %rax, %rax
        jne       ..B1.16
..B1.15:
        cmpl      $1024, %r9d
        je        ..B1.49
..B1.16:
        movsd     -32(%rsp), %xmm0
..B1.17:
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.18:
        comisd    %xmm2, %xmm0
        jb        ..B1.25
..B1.19:
        ucomisd   %xmm0, %xmm2
        jp        ..B1.20
        je        ..B1.62
..B1.20:
        cmpl      $1023, %r9d
        je        ..B1.22
..B1.21:
        movaps    %xmm2, %xmm1
        addsd     %xmm2, %xmm1
        comisd    %xmm1, %xmm0
        jae       ..B1.24
..B1.22:
        movl      %ebx, (%rdi)
..B1.23:
        testl     %edx, %edx
        subsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm0
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        jne       ..L14
        movaps    %xmm2, %xmm0
..L14:
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.24:
        movl      $0, (%rdi)
        movsd     -32(%rsp), %xmm0
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.25:
        cmpl      $-1023, %r9d
        je        ..B1.58
..B1.26:
        movq      $0x10000000000000, %rbp
        orq       %rbp, %rax
..B1.27:
        cmpl      $-1023, %ecx
        je        ..B1.54
..B1.28:
        movq      $0x10000000000000, %rbp
        orq       %rbp, %r10
..B1.29:
        subl      %ecx, %r9d
        xorl      %ebp, %ebp
        xorl      %esi, %esi
        incl      %r9d
        je        ..B1.35
..B1.31:
        addl      %ebp, %ebp
        cmpq      %r10, %rax
        jb        ..B1.33
..B1.32:
        subq      %r10, %rax
        incl      %ebp
..B1.33:
        incl      %esi
        addq      %rax, %rax
        cmpl      %r9d, %esi
        jb        ..B1.31
..B1.35:
        testq     %rax, %rax
        je        ..B1.63
..B1.36:
        shrq      $1, %rax
        movq      $0xfffffffffffff, %rsi
        cmpq      %rsi, %rax
        ja        ..B1.40
..B1.38:
        addq      %rax, %rax
        decl      %ecx
        cmpq      %rsi, %rax
        jbe       ..B1.38
..B1.40:
        cmpl      $-1022, %ecx
        jl        ..B1.42
..B1.41:
        movslq    %ecx, %rcx
        movq      $0xfffffffffffff, %rsi
        andq      %rax, %rsi
        addq      $1023, %rcx
        jmp       ..B1.43
..B1.42:
        negl      %ecx
        movq      %rax, %rsi
        addl      $2, %ecx
        shrq      %cl, %rsi
        xorl      %ecx, %ecx
..B1.43:
        shlq      $52, %rcx
        orq       %rsi, %rcx
        movq      %rcx, -40(%rsp)
        movsd     -40(%rsp), %xmm2
        movaps    %xmm2, %xmm1
        addsd     %xmm2, %xmm1
        comisd    %xmm0, %xmm1
        jb        ..B1.46
..B1.44:
        ucomisd   %xmm1, %xmm0
        jp        ..B1.45
        je        ..B1.52
..B1.45:
        incl      %ebp
        subsd     %xmm0, %xmm2
..B1.46:
        andl      $2147483647, %ebp
        movaps    %xmm2, %xmm0
        imull     %ebp, %ebx
        testl     %edx, %edx
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        movl      %ebx, (%rdi)
        jne       ..L29
        movaps    %xmm2, %xmm0
..L29:
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.47:
        movsd     -32(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.48:
        movsd     -40(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.49:
        movsd     -24(%rsp), %xmm0
        movsd     -24(%rsp), %xmm1
        divsd     %xmm1, %xmm0
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.52:
        testl     $1, %ebp
        je        ..B1.46
..B1.53:
        xorps     .L_2il0floatpacket.1(%rip), %xmm2
        incl      %ebp
        jmp       ..B1.46
..B1.54:
        testq     %r10, %r10
        je        ..B1.28
..B1.55:
        movl      $-1022, %ecx
        movq      $0xfffffffffffff, %rbp
..B1.56:
        addq      %r10, %r10
        decl      %ecx
        cmpq      %rbp, %r10
        jbe       ..B1.56
        jmp       ..B1.29
..B1.58:
        testq     %rax, %rax
        je        ..B1.26
..B1.59:
        movl      $-1022, %r9d
        movq      $0xfffffffffffff, %rbp
..B1.60:
        addq      %rax, %rax
        decl      %r9d
        cmpq      %rbp, %rax
        jbe       ..B1.60
        jmp       ..B1.27
..B1.62:
        movl      %ebx, (%rdi)
        movsd     -24(%rsp), %xmm0
        mulsd     -32(%rsp), %xmm0
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.63:
        andl      $2147483647, %ebp
        imull     %ebp, %ebx
        movl      %ebx, (%rdi)
        movsd     -24(%rsp), %xmm0
        mulsd     -32(%rsp), %xmm0
        movq      %rax, -40(%rsp)
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	remquo,@function
	.size	remquo,.-remquo
	.data
# -- End  remquo
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
