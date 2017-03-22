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
	.file "fmod_gen.c"
	.text
..TXTST0:
# -- Begin  fmod
	.text
       .align    16,0x90
	.globl fmod
fmod:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_fmod.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movq      $0x8000000000000000, %rsi
        movd      %xmm1, %rcx
        movq      $0x7fffffffffffffff, %rdi
        movd      %xmm0, %rax
        movsd     %xmm0, 16(%rsp)
        movsd     %xmm1, 24(%rsp)
        btrq      $63, %rcx
        movq      $0x3ff0000000000000, %rdx
        andq      %rax, %rsi
        andq      %rax, %rdi
        cmpq      %rdx, %rcx
        jne       ..B1.30
..B1.2:
        cmpq      %rcx, %rdi
        ja        ..B1.7
..B1.3:
        jne       ..B1.6
..B1.4:
        pxor      %xmm1, %xmm1
        mulsd     %xmm1, %xmm0
..B1.6:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.7:
        movq      $0x4330000000000000, %rax
        cmpq      %rax, %rdi
        jae       ..B1.9
..B1.8:
        negq      %rcx
        addq      %rdi, %rcx
        shrq      $52, %rcx
        negq      %rcx
        addq      $52, %rcx
        movsd     16(%rsp), %xmm0
        shrq      %cl, %rdi
        shlq      %cl, %rdi
        orq       %rsi, %rdi
        movq      %rdi, (%rsp)
        subsd     (%rsp), %xmm0
        movd      %xmm0, %rax
        orq       %rsi, %rax
        movq      %rax, (%rsp)
        movsd     (%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.9:
        movq      $0x7ff0000000000000, %rax
        cmpq      %rax, %rdi
        jae       ..B1.11
..B1.10:
        pxor      %xmm0, %xmm0
        mulsd     16(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.30:
        movq      $0x7ff0000000000000, %rax
        cmpq      %rax, %rdi
..B1.11:
        ja        ..B1.25
..B1.12:
        cmpq      %rax, %rcx
        ja        ..B1.24
..B1.13:
        testq     %rcx, %rcx
        je        ..B1.23
..B1.14:
        cmpq      %rax, %rdi
        je        ..B1.23
..B1.15:
        cmpq      %rax, %rcx
        je        ..B1.22
..B1.16:
        cmpq      %rcx, %rdi
        jb        ..B1.21
..B1.17:
        je        ..B1.27
..B1.18:
        movq      %rdi, (%rsp)
..B1.19:
        fldl      24(%rsp)
        fldl      16(%rsp)
.L_2TAG_PACKET_0.0.2:
        fprem     
        fstsw     %ax
        andw      $1024, %ax
        jne       .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        fstpl     8(%rsp)
..B1.20:
        movsd     8(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.21:
        movsd     16(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.22:
        movsd     16(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.23:
        pxor      %xmm0, %xmm0
        pxor      %xmm1, %xmm1
        divsd     %xmm1, %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.24:
        movsd     24(%rsp), %xmm0
        mulsd     16(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.25:
        movsd     16(%rsp), %xmm0
        mulsd     24(%rsp), %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.27:
        pxor      %xmm0, %xmm0
        mulsd     16(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fmod,@function
	.size	fmod,.-fmod
	.data
# -- End  fmod
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
