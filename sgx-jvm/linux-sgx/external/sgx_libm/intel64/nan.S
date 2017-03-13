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
	.file "nan.c"
	.text
..TXTST0:
# -- Begin  nanf
	.text
       .align    16,0x90
	.globl nanf
nanf:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_nanf.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
..___tag_value_nanf.4:
        call      __libm_conv_strtoull@PLT
..___tag_value_nanf.5:
..B1.2:
        andl      $4194303, %eax
        orl       $2143289344, %eax
        movl      %eax, (%rsp)
        movss     (%rsp), %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nanf,@function
	.size	nanf,.-nanf
	.data
# -- End  nanf
	.text
# -- Begin  nan
	.text
       .align    16,0x90
	.globl nan
nan:
# parameter 1: %rdi
..B2.1:
	.cfi_startproc
..___tag_value_nan.8:
..L9:

        pushq     %rsi
	.cfi_def_cfa_offset 16
..___tag_value_nan.11:
        call      __libm_conv_strtoull@PLT
..___tag_value_nan.12:
..B2.2:
        movq      $0x7ffffffffffff, %rdx
        andq      %rax, %rdx
        movq      $0x7ff8000000000000, %rax
        orq       %rdx, %rax
        movq      %rax, (%rsp)
        movsd     (%rsp), %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nan,@function
	.size	nan,.-nan
	.data
# -- End  nan
	.text
# -- Begin  nanl
	.text
       .align    16,0x90
	.globl nanl
nanl:
# parameter 1: %rdi
..B3.1:
	.cfi_startproc
..___tag_value_nanl.15:
..L16:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
..___tag_value_nanl.18:
        call      __libm_conv_strtoull@PLT
..___tag_value_nanl.19:
..B3.5:
        movq      %rax, %rdx
..B3.2:
        movq      $0xc000000000000000, %rax
        orq       %rax, %rdx
        movq      %rdx, (%rsp)
        movl      $32767, %edx
        movw      %dx, 8(%rsp)
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nanl,@function
	.size	nanl,.-nanl
	.data
# -- End  nanl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
