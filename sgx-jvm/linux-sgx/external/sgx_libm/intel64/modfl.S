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
	.file "modfl.c"
	.text
..TXTST0:
# -- Begin  modfl
	.text
       .align    16,0x90
	.globl modfl
modfl:
# parameter 1: 48 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_modfl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    56(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $16446, %ecx
        jge       ..B1.21
..B1.4:
        cmpl      $16383, %ecx
        jl        ..B1.16
..B1.5:
        fldt      48(%rsp)
        cmpl      $16415, %ecx
        fstpt     (%rsp)
        jge       ..B1.7
..B1.6:
        negl      %ecx
        movl      $-1, %eax
        addl      $30, %ecx
        shll      %cl, %eax
        movl      %eax, %ecx
        notl      %ecx
        movl      52(%rsp), %edx
        andl      %edx, %ecx
        andl      %eax, %edx
        orl       48(%rsp), %ecx
        movl      %edx, 52(%rsp)
        xorl      %edx, %edx
        jmp       ..B1.8
..B1.7:
        negl      %ecx
        movl      $-1, %edx
        addl      $30, %ecx
        shll      %cl, %edx
        movl      %edx, %ecx
        notl      %ecx
        movl      48(%rsp), %eax
        andl      %eax, %ecx
        andl      %eax, %edx
..B1.8:
        movl      %edx, 48(%rsp)
        testl     %ecx, %ecx
        je        ..B1.14
..B1.9:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.31
..B1.10:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.11:
        fldcw     24(%rsp)
..B1.12:
        fldt      48(%rsp)
        fstpt     (%rdi)
        fldt      (%rdi)
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
..B1.13:
        fldcw     26(%rsp)
        jmp       ..B1.15
..B1.14:
        fldt      48(%rsp)
        lea       _zeros(%rip), %rdx
        movb      57(%rsp), %al
        andb      $-128, %al
        fstpt     (%rdi)
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fstpt     (%rsp)
..B1.15:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.16:
        testl     %ecx, %ecx
        jne       ..B1.20
..B1.17:
        cmpl      $0, 52(%rsp)
        jne       ..B1.19
..B1.18:
        cmpl      $0, 48(%rsp)
        je        ..B1.20
..B1.19:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.20:
        movb      57(%rsp), %al
        lea       _zeros(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fstpt     (%rdi)
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.21:
        cmpl      $32767, %ecx
        jne       ..B1.24
..B1.22:
        cmpl      $-2147483648, 52(%rsp)
        jne       ..B1.25
..B1.23:
        cmpl      $0, 48(%rsp)
        jne       ..B1.25
..B1.24:
        fldt      48(%rsp)
        lea       _zeros(%rip), %rdx
        movb      57(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fstpt     (%rdi)
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
        je        ..B1.32
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
        fldt      (%rsp)
        fstpt     (%rdi)
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
        fstpt     (%rdi)
        fldt      (%rdi)
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.32:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     (%rdi)
        jmp       ..B1.30
        .align    16,0x90
	.cfi_endproc
	.type	modfl,@function
	.size	modfl,.-modfl
	.data
# -- End  modfl
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
