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
	.file "feholdexcept.c"
	.text
..TXTST0:
# -- Begin  feholdexcept
	.text
       .align    16,0x90
	.globl feholdexcept
feholdexcept:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_feholdexcept.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 40(%rsp)
..B1.2:
        fnstenv   4(%rsp)
..B1.3:
        movzwl    8(%rsp), %eax
        movzwl    4(%rsp), %edx
        movw      %ax, 4(%rdi)
        movw      %dx, (%rdi)
..B1.4:
        stmxcsr   (%rsp)
..B1.5:
        movl      (%rsp), %eax
        movzwl    8(%rsp), %edx
        movzwl    4(%rsp), %ecx
        andl      $-64, %edx
        orl       $63, %ecx
        movl      %eax, 28(%rdi)
        movw      %dx, 8(%rsp)
        movw      %cx, 4(%rsp)
..B1.6:
        fnclex    
..B1.7:
        fldenv    4(%rsp)
..B1.8:
        movl      (%rsp), %eax
        andl      $-64, %eax
        orl       $8064, %eax
        movl      %eax, (%rsp)
..B1.9:
        ldmxcsr   (%rsp)
..B1.10:
        movq      40(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.12
..B1.11:
        xorl      %eax, %eax
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.12:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	feholdexcept,@function
	.size	feholdexcept,.-feholdexcept
	.data
# -- End  feholdexcept
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
