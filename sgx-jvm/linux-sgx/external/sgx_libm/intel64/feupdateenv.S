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
	.file "feupdateenv.c"
	.text
..TXTST0:
# -- Begin  feupdateenv
	.text
       .align    16,0x90
	.globl feupdateenv
feupdateenv:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_feupdateenv.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %rsi
	.cfi_def_cfa_offset 32
        movq      %rdi, %r12
        movl      $63, %edi
..___tag_value_feupdateenv.8:
        call      fetestexcept@PLT
..___tag_value_feupdateenv.9:
..B1.7:
        movl      %eax, %r13d
..B1.2:
        movq      %r12, %rdi
..___tag_value_feupdateenv.10:
        call      fesetenv@PLT
..___tag_value_feupdateenv.11:
..B1.3:
        movl      %r13d, %edi
..___tag_value_feupdateenv.12:
        call      feraiseexcept@PLT
..___tag_value_feupdateenv.13:
..B1.4:
        xorl      %eax, %eax
        popq      %rcx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	feupdateenv,@function
	.size	feupdateenv,.-feupdateenv
	.data
# -- End  feupdateenv
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
