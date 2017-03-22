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
	.file "significandl.c"
	.text
..TXTST0:
# -- Begin  significandl
	.text
       .align    16,0x90
	.globl significandl
significandl:
# parameter 1: 32 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_significandl.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movzwl    40(%rsp), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.15
..B1.4:
        testl     %eax, %eax
        jne       ..B1.13
..B1.5:
        cmpq      $0, 32(%rsp)
        je        ..B1.12
..B1.6:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.14
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
        andl      $-32768, %eax
        orl       $-49153, %eax
        movw      %ax, 40(%rsp)
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.12:
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.13:
        andl      $-32768, %edx
        orl       $-49153, %edx
        movw      %dx, 40(%rsp)
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.14:
        fldt      32(%rsp)
        lea       _TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     32(%rsp)
        jmp       ..B1.11
..B1.15:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.21
..B1.16:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.17:
        fldcw     16(%rsp)
..B1.18:
        fldt      32(%rsp)
        fstpt     (%rsp)
..B1.19:
        fldcw     18(%rsp)
..B1.20:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.21:
        fldt      32(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.20
        .align    16,0x90
	.cfi_endproc
	.type	significandl,@function
	.size	significandl,.-significandl
	.data
# -- End  significandl
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
