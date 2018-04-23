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
	.file "truncl.c"
	.text
..TXTST0:
# -- Begin  truncl
	.text
       .align    16,0x90
	.globl truncl
truncl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_truncl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    56(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $16446, %ecx
        jge       ..B1.14
..B1.4:
        cmpl      $16383, %ecx
        jl        ..B1.9
..B1.5:
        cmpl      $16415, %ecx
        jge       ..B1.7
..B1.6:
        negl      %ecx
        addl      $30, %ecx
        movl      52(%rsp), %eax
        shrl      %cl, %eax
        shll      %cl, %eax
        movl      %eax, 52(%rsp)
        xorl      %eax, %eax
        jmp       ..B1.8
..B1.7:
        negl      %ecx
        addl      $30, %ecx
        movl      48(%rsp), %eax
        shrl      %cl, %eax
        shll      %cl, %eax
..B1.8:
        movl      %eax, 48(%rsp)
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.9:
        testl     %ecx, %ecx
        jne       ..B1.13
..B1.10:
        cmpl      $0, 52(%rsp)
        jne       ..B1.12
..B1.11:
        cmpl      $0, 48(%rsp)
        je        ..B1.13
..B1.12:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, (%rsp)
..B1.13:
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
..B1.14:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.16:
        fldcw     24(%rsp)
..B1.17:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
..B1.18:
        fldcw     26(%rsp)
..B1.19:
        fldt      8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.20:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.19
        .align    16,0x90
	.cfi_endproc
	.type	truncl,@function
	.size	truncl,.-truncl
	.data
# -- End  truncl
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
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
