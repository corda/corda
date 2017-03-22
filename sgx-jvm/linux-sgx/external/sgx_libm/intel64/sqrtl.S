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
	.file "sqrtl.c"
	.text
..TXTST0:
# -- Begin  sqrtl
	.text
       .align    16,0x90
	.globl sqrtl
sqrtl:
# parameter 1: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_sqrtl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %cl, %cl
        fldt      64(%rsp)
        fstpt     32(%rsp)
..B1.2:
        fnstcw    10(%rsp)
..B1.3:
        movzwl    72(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.23
..B1.4:
        testb     $-128, 73(%rsp)
        jne       ..B1.13
..B1.5:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.7:
        fldcw     8(%rsp)
..B1.8:
        movb      $1, %cl
..B1.9:
        fldt      32(%rsp)
        fsqrt     
        fstpt     16(%rsp)
..B1.10:
        testb     %cl, %cl
        je        ..B1.12
..B1.11:
        fldcw     10(%rsp)
..B1.12:
        fldt      16(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.13:
        testl     %eax, %eax
        jne       ..B1.16
..B1.14:
        cmpq      $0, 64(%rsp)
        jne       ..B1.16
..B1.15:
        fldt      64(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.16:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.17:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.18:
        fldcw     8(%rsp)
..B1.19:
        movb      $1, %cl
..B1.20:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        testb     %cl, %cl
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     16(%rsp)
        je        ..B1.22
..B1.21:
        fldcw     10(%rsp)
..B1.22:
        fldt      16(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.23:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.25
..B1.24:
        cmpl      $0, 64(%rsp)
        je        ..B1.4
..B1.25:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.31
..B1.26:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.27:
        fldcw     8(%rsp)
..B1.28:
        fldt      64(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
..B1.29:
        fldcw     10(%rsp)
..B1.30:
        fldt      16(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.31:
        fldt      64(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
        jmp       ..B1.30
        .align    16,0x90
	.cfi_endproc
	.type	sqrtl,@function
	.size	sqrtl,.-sqrtl
	.data
# -- End  sqrtl
	.section .rodata, "a"
	.align 4
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
