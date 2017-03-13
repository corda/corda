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
	.file "cpowl.c"
	.text
..TXTST0:
# -- Begin  cpowl
	.text
       .align    16,0x90
	.globl cpowl
cpowl:
# parameter 1: 48 + %rsp
# parameter 2: 80 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cpowl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 32(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 80
        movq      %rsp, %r8
        movq      80(%rsp), %rdx
        movq      88(%rsp), %rcx
        movq      96(%rsp), %rsi
        movq      104(%rsp), %rdi
        movq      %rdx, (%r8)
        movq      %rcx, 8(%r8)
        movq      %rsi, 16(%r8)
        movq      %rdi, 24(%r8)
        call      clogl@PLT
..B1.9:
        fxch      %st(1)
        fxch      %st(1)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
        fldt      80(%rsp)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      96(%rsp)
        fld       %st(0)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 80
        movq      %rsp, %rdi
        movq      32(%rsp), %rax
        movq      40(%rsp), %rdx
        movq      48(%rsp), %rcx
        movq      56(%rsp), %rsi
        movq      %rax, (%rdi)
        movq      %rdx, 8(%rdi)
        movq      %rcx, 16(%rdi)
        movq      %rsi, 24(%rdi)
        call      cexpl@PLT
..B1.8:
        fxch      %st(1)
        addq      $32, %rsp
	.cfi_def_cfa_offset 48
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.4
..B1.2:
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.4
..B1.3:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 48
..B1.4:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	cpowl,@function
	.size	cpowl,.-cpowl
	.data
# -- End  cpowl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
