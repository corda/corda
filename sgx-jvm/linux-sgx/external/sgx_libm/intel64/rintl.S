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
	.file "rintl.c"
	.text
..TXTST0:
# -- Begin  rintl
	.text
       .align    16,0x90
	.globl rintl
rintl:
# parameter 1: 32 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_rintl.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        xorb      %cl, %cl
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movb      41(%rsp), %dl
        movzwl    40(%rsp), %eax
        andb      $-128, %dl
        andl      $32767, %eax
        shrb      $7, %dl
        movzwl    18(%rsp), %esi
        cmpl      $16446, %eax
        jge       ..B1.13
..B1.4:
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.8
..B1.5:
        orl       $-64768, %esi
        movw      %si, 16(%rsp)
..B1.6:
        fldcw     16(%rsp)
..B1.7:
        movb      $1, %cl
..B1.8:
        fldt      32(%rsp)
        lea       _TWO_63(%rip), %rax
        movzbl    %dl, %edx
        testb     %cl, %cl
        fldl      (%rax,%rdx,8)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     32(%rsp)
        je        ..B1.10
..B1.9:
        fldcw     18(%rsp)
..B1.10:
        fldt      32(%rsp)
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        fucomip   %st(1), %st
        jp        ..B1.12
        je        ..B1.19
..B1.12:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.13:
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.14:
        orl       $-64768, %esi
        movw      %si, 16(%rsp)
..B1.15:
        fldcw     16(%rsp)
..B1.16:
        fldt      32(%rsp)
        fstpt     (%rsp)
..B1.17:
        fldcw     18(%rsp)
..B1.18:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.19:
        fstp      %st(0)
        fldl      (%rax,%rdx,8)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.20:
        fldt      32(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.18
        .align    16,0x90
	.cfi_endproc
	.type	rintl,@function
	.size	rintl,.-rintl
	.data
# -- End  rintl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63:
	.long	0
	.long	1138753536
	.long	0
	.long	3286237184
	.type	_TWO_63,@object
	.size	_TWO_63,16
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
