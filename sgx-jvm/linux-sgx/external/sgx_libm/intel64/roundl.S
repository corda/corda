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
	.file "roundl.c"
	.text
..TXTST0:
# -- Begin  roundl
	.text
       .align    16,0x90
	.globl roundl
roundl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_roundl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %al, %al
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    56(%rsp), %edi
        andl      $32767, %edi
        cmpl      $16446, %edi
        jge       ..B1.25
..B1.4:
        cmpl      $16383, %edi
        jl        ..B1.19
..B1.5:
        cmpl      $16414, %edi
        jge       ..B1.7
..B1.6:
        movl      52(%rsp), %edx
        lea       2(%rdi), %ecx
        negl      %edi
        movl      %edx, %esi
        addl      $30, %edi
        shll      %cl, %esi
        movl      %edi, %ecx
        shrl      %cl, %edx
        movl      %edi, %ecx
        shll      %cl, %edx
        movl      %edx, 52(%rsp)
        movl      $0, 48(%rsp)
        jmp       ..B1.10
..B1.7:
        jle       ..B1.9
..B1.8:
        movl      48(%rsp), %edx
        lea       2(%rdi), %ecx
        negl      %edi
        movl      %edx, %esi
        addl      $30, %edi
        shll      %cl, %esi
        movl      %edi, %ecx
        shrl      %cl, %edx
        movl      %edi, %ecx
        shll      %cl, %edx
        movl      %edx, 48(%rsp)
        jmp       ..B1.10
..B1.9:
        movl      48(%rsp), %esi
        movl      $0, 48(%rsp)
..B1.10:
        testl     %esi, %esi
        jge       ..B1.18
..B1.11:
        movzwl    26(%rsp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.15
..B1.12:
        orl       $-64768, %ecx
        movw      %cx, 24(%rsp)
..B1.13:
        fldcw     24(%rsp)
..B1.14:
        movb      $1, %al
..B1.15:
        fldt      48(%rsp)
        lea       _ones(%rip), %rcx
        movb      57(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        testb     %al, %al
        faddl     (%rcx,%rsi,8)
        fstpt     (%rsp)
        je        ..B1.17
..B1.16:
        fldcw     26(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.18:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.19:
        cmpl      $16382, %edi
        je        ..B1.32
..B1.20:
        testl     %edi, %edi
        jne       ..B1.24
..B1.21:
        cmpl      $0, 52(%rsp)
        jne       ..B1.23
..B1.22:
        cmpl      $0, 48(%rsp)
        je        ..B1.24
..B1.23:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.24:
        movb      57(%rsp), %al
        lea       _zeros(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.25:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.31
..B1.26:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.27:
        fldcw     24(%rsp)
..B1.28:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.29:
        fldcw     26(%rsp)
..B1.30:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.31:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.30
..B1.32:
        movb      57(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	roundl,@function
	.size	roundl,.-roundl
	.data
# -- End  roundl
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
