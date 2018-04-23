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
	.file "ceill.c"
	.text
..TXTST0:
# -- Begin  ceill
	.text
       .align    16,0x90
	.globl ceill
ceill:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_ceill.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %cl, %cl
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16445, %eax
        jge       ..B1.20
..B1.4:
        cmpl      $16383, %eax
        jl        ..B1.15
..B1.5:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.7:
        fldcw     24(%rsp)
..B1.8:
        movb      $1, %cl
..B1.9:
        fldt      48(%rsp)
        lea       _TWO_63H(%rip), %rax
        fld       %st(0)
        fldl      (%rax)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        fldt      8(%rsp)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B1.12
        jp        ..B1.12
..B1.10:
        fldt      8(%rsp)
        lea       _ones(%rip), %rax
        faddl     (%rax)
        fstpt     8(%rsp)
..B1.12:
        testb     %cl, %cl
        je        ..B1.14
..B1.13:
        fldcw     26(%rsp)
..B1.14:
        fldt      8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.15:
        testl     %eax, %eax
        jne       ..B1.19
..B1.16:
        cmpq      $0, 48(%rsp)
        jne       ..B1.18
..B1.17:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.18:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, (%rsp)
..B1.19:
        movb      57(%rsp), %al
        lea       _pone_nzero(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.20:
        cmpl      $16446, %eax
        jge       ..B1.29
..B1.21:
        testb     $1, 48(%rsp)
        je        ..B1.28
..B1.22:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.35
..B1.23:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.24:
        fldcw     24(%rsp)
..B1.25:
        fldt      48(%rsp)
        lea       _halfs(%rip), %rax
        faddl     (%rax)
        fstpt     8(%rsp)
..B1.26:
        fldcw     26(%rsp)
..B1.27:
        fldt      8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.28:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.29:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.30:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.31:
        fldcw     24(%rsp)
..B1.32:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
..B1.33:
        fldcw     26(%rsp)
..B1.34:
        fldt      8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.35:
        fldt      48(%rsp)
        lea       _halfs(%rip), %rax
        faddl     (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.27
..B1.36:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.34
        .align    16,0x90
	.cfi_endproc
	.type	ceill,@function
	.size	ceill,.-ceill
	.data
# -- End  ceill
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
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
_pone_nzero:
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,16
	.align 4
_halfs:
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.type	_halfs,@object
	.size	_halfs,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
