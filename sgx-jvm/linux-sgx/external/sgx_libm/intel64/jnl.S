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
	.file "jnl.c"
	.text
..TXTST0:
# -- Begin  jnl
	.text
       .align    16,0x90
	.globl jnl
jnl:
# parameter 1: %edi
# parameter 2: 32 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_jnl.1:
..L2:

        pushq     %rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
        subq      $16, %rsp
	.cfi_def_cfa_offset 32
        movl      %edi, %ebp
        fldt      32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 48
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
        call      __ltoq@PLT
..B1.6:
        addq      $16, %rsp
	.cfi_def_cfa_offset 32
        movl      %ebp, %edi
        movaps    (%rsp), %xmm0
..___tag_value_jnl.8:
        call      __jnq@PLT
..___tag_value_jnl.9:
..B1.2:
        movaps    %xmm0, (%rsp)
        lea       (%rsp), %rdi
        call      __qtol@PLT
..B1.7:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	jnl,@function
	.size	jnl,.-jnl
	.data
# -- End  jnl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
