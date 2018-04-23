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
	.file "scalblnl.c"
	.text
..TXTST0:
# -- Begin  scalblnl
	.text
       .align    16,0x90
	.globl scalblnl
scalblnl:
# parameter 1: 32 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_scalblnl.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movzwl    40(%rsp), %eax
        movl      %eax, %r9d
        andq      $32767, %r9
        cmpq      $32767, %r9
        je        ..B1.39
..B1.4:
        testq     %r9, %r9
        jne       ..B1.12
..B1.5:
        cmpq      $0, 32(%rsp)
        je        ..B1.15
..B1.6:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.8:
        fldcw     16(%rsp)
..B1.9:
        fldt      32(%rsp)
        lea       _TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     32(%rsp)
..B1.10:
        fldcw     18(%rsp)
..B1.11:
        movzwl    40(%rsp), %eax
        movl      %eax, %ecx
        andl      $32767, %ecx
        movl      %ecx, %r9d
        addq      $-75, %r9
..B1.12:
        movl      $65536, %esi
        cmpq      $65536, %rdi
        cmovg     %rsi, %rdi
        movq      $-65536, %r8
        xorb      %dl, %dl
        cmpq      $-65536, %rdi
        cmovl     %r8, %rdi
        lea       (%r9,%rdi), %rcx
        testq     %rcx, %rcx
        jle       ..B1.23
..B1.13:
        cmpq      $32767, %rcx
        jge       ..B1.16
..B1.14:
        andl      $-32768, %eax
        andl      $32767, %ecx
        orl       %ecx, %eax
        movw      %ax, 40(%rsp)
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.15:
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.16:
        movzwl    18(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.17:
        orl       $-64768, %ecx
        movw      %cx, 16(%rsp)
..B1.18:
        fldcw     16(%rsp)
..B1.19:
        movb      $1, %dl
..B1.20:
        movb      41(%rsp), %al
        lea       _large_value_80(%rip), %rsi
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %ecx
        shlq      $4, %rcx
        testb     %dl, %dl
        fldt      (%rsi,%rcx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.22
..B1.21:
        fldcw     18(%rsp)
..B1.22:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.23:
        cmpq      $-63, %rcx
        jl        ..B1.31
..B1.24:
        andl      $-32768, %eax
        lea       75(%r9,%rdi), %rdx
        andl      $32767, %edx
        orl       %edx, %eax
        movzwl    18(%rsp), %edx
        movw      %ax, 40(%rsp)
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.30
..B1.25:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.26:
        fldcw     16(%rsp)
..B1.27:
        fldt      32(%rsp)
        lea       8+_TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.28:
        fldcw     18(%rsp)
..B1.29:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.30:
        fldt      32(%rsp)
        lea       8+_TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.29
..B1.31:
        movzwl    18(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.35
..B1.32:
        orl       $-64768, %ecx
        movw      %cx, 16(%rsp)
..B1.33:
        fldcw     16(%rsp)
..B1.34:
        movb      $1, %dl
..B1.35:
        movb      41(%rsp), %al
        lea       _small_value_80(%rip), %rsi
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %ecx
        shlq      $4, %rcx
        testb     %dl, %dl
        fldt      (%rsi,%rcx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.37
..B1.36:
        fldcw     18(%rsp)
..B1.37:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.38:
        fldt      32(%rsp)
        lea       _TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     32(%rsp)
        jmp       ..B1.11
..B1.39:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.40:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.41:
        fldcw     16(%rsp)
..B1.42:
        fldt      32(%rsp)
        fstpt     (%rsp)
..B1.43:
        fldcw     18(%rsp)
..B1.44:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.45:
        fldt      32(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.44
        .align    16,0x90
	.cfi_endproc
	.type	scalblnl,@function
	.size	scalblnl,.-scalblnl
	.data
# -- End  scalblnl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.word	0
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
