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
	.file "lrintl.c"
	.text
..TXTST0:
# -- Begin  lrintl
	.text
       .align    16,0x90
	.globl lrintl
lrintl:
# parameter 1: 80 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_lrintl.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        subq      $56, %rsp
	.cfi_def_cfa_offset 80
        fldt      80(%rsp)
        movzbl    89(%rsp), %edi
        movzwl    88(%rsp), %r8d
        andl      $128, %edi
        andl      $32767, %r8d
        shrl      $7, %edi
        fstpt     32(%rsp)
        cmpl      $16445, %r8d
        jge       ..B1.8
..B1.2:
        testl     %r8d, %r8d
        jne       ..B1.6
..B1.3:
        cmpl      $0, 84(%rsp)
        jne       ..B1.5
..B1.4:
        cmpl      $0, 80(%rsp)
        je        ..B1.6
..B1.5:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 8(%rsp)
..B1.6:
        fldt      32(%rsp)
        fistpq    16(%rsp)
..B1.7:
        movq      16(%rsp), %rax
        addq      $56, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.8:
        cmpl      $16447, %r8d
        jge       ..B1.21
..B1.9:
        movl      %edi, %esi
        movq      $0x7fffffffffffffff, %rax
        addq      %rax, %rsi
        cmpl      $16446, %r8d
        jge       ..B1.16
..B1.10:
        fnstcw    4(%rsp)
..B1.11:
        movl      80(%rsp), %ebx
        movl      %ebx, %ebp
        movzwl    4(%rsp), %r11d
        andl      $1, %ebp
        movl      84(%rsp), %ecx
        andl      $3072, %r11d
        jne       ..B1.13
..B1.12:
        movl      %ebx, %eax
        andl      $3, %ebx
        movl      %ecx, %edx
        movl      $1, %r9d
        cmpl      $3, %ebx
        movl      $0, %ebx
        cmovne    %ebx, %r9d
        shrl      $1, %edx
        shll      $31, %ecx
        shrl      $1, %eax
        orl       %eax, %ecx
        shlq      $32, %rdx
        orq       %rcx, %rdx
        addq      %r9, %rdx
        jmp       ..B1.17
..B1.13:
        movl      $1, %eax
        xorl      %edx, %edx
        cmpl      $3072, %r11d
        cmove     %eax, %edx
        xorl      %r10d, %r10d
        cmpl      $2048, %r11d
        movl      %eax, %r9d
        cmove     %eax, %r10d
        testl     %edi, %edi
        cmove     %edi, %r9d
        andl      %r9d, %r10d
        xorl      %r9d, %r9d
        cmpl      $1024, %r11d
        cmove     %eax, %r9d
        xorl      %r11d, %r11d
        testl     %edi, %edi
        cmovne    %r11d, %eax
        orl       %r10d, %edx
        andl      %eax, %r9d
        orl       %r9d, %edx
        je        ..B1.15
..B1.14:
        movl      %ecx, %edx
        shrl      $1, %edx
        shll      $31, %ecx
        shrl      $1, %ebx
        orl       %ebx, %ecx
        shlq      $32, %rdx
        orq       %rcx, %rdx
        jmp       ..B1.17
..B1.15:
        movl      %ecx, %edx
        movl      %ebx, %eax
        shrl      $1, %edx
        shll      $31, %ecx
        shrl      $1, %eax
        orl       %eax, %ecx
        shlq      $32, %rdx
        orq       %rcx, %rdx
        andq      $1, %rbx
        addq      %rbx, %rdx
        jmp       ..B1.17
..B1.16:
        movl      84(%rsp), %edx
        xorl      %ebp, %ebp
        shlq      $32, %rdx
        movl      80(%rsp), %eax
        orq       %rax, %rdx
..B1.17:
        movq      %rdx, %rax
        xorl      %ecx, %ecx
        negq      %rax
        testl     %edi, %edi
        cmove     %rdx, %rax
        cmpq      %rdx, %rax
        setae     %cl
        cmpq      %rsi, %rdx
        movl      $0, %edx
        setbe     %dl
        testl     %edx, %ecx
        je        ..B1.21
..B1.18:
        testl     %ebp, %ebp
        je        ..B1.20
..B1.19:
        lea       _ones(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movq      %rax, 16(%rsp)
        movsd     (%rdx), %xmm0
        addsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
..B1.20:
        addq      $56, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.21:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movq      $0x8000000000000000, %rcx
        cmpl      $32767, %r8d
        movq      %rcx, 16(%rsp)
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 8(%rsp)
        jne       ..B1.24
..B1.22:
        cmpl      $-2147483648, 84(%rsp)
        jne       ..B1.29
..B1.23:
        cmpl      $0, 80(%rsp)
        jne       ..B1.29
..B1.24:
        fnstcw    2(%rsp)
..B1.25:
        movzwl    2(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.26:
        orl       $-64768, %edx
        movw      %dx, (%rsp)
..B1.27:
        fldcw     (%rsp)
..B1.28:
        fldcw     2(%rsp)
..B1.29:
        movq      16(%rsp), %rax
        addq      $56, %rsp
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
	.type	lrintl,@function
	.size	lrintl,.-lrintl
	.data
# -- End  lrintl
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
