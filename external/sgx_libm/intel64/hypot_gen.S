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
	.file "hypot_gen.c"
	.text
..TXTST0:
# -- Begin  hypot
	.text
       .align    16,0x90
	.globl hypot
hypot:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_hypot.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %cl, %cl
        movsd     %xmm0, 8(%rsp)
        movsd     %xmm1, 16(%rsp)
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.5:
        fldcw     32(%rsp)
..B1.6:
        movb      $1, %cl
..B1.7:
        fldl      8(%rsp)
        fmull     8(%rsp)
        fldl      16(%rsp)
        fmull     16(%rsp)
        faddp     
        fsqrt     
        fstpl     24(%rsp)
..B1.8:
        testb     %cl, %cl
        je        ..B1.10
..B1.9:
        fldcw     34(%rsp)
..B1.10:
        movq      $0x7ff0000000000000, %rcx
        movq      24(%rsp), %rax
        cmpq      %rcx, %rax
        jb        ..B1.19
..B1.11:
        movq      8(%rsp), %rdx
        movq      16(%rsp), %rsi
        btrq      $63, %rdx
        btrq      $63, %rsi
        cmpq      %rcx, %rax
        je        ..B1.17
..B1.12:
        cmpq      %rcx, %rdx
        jne       ..B1.14
..B1.13:
        movq      %rdx, (%rsp)
        movsd     (%rsp), %xmm0
        movsd     %xmm0, 24(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.14:
        cmpq      %rcx, %rsi
        jne       ..B1.17
..B1.15:
        movq      %rsi, (%rsp)
        movsd     (%rsp), %xmm0
        movsd     %xmm0, 24(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.17:
        movsd     24(%rsp), %xmm0
..B1.18:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.19:
        movsd     24(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	hypot,@function
	.size	hypot,.-hypot
	.data
# -- End  hypot
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
