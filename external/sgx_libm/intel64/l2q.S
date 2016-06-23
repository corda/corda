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
	.file "l2q.c"
	.text
..TXTST0:
# -- Begin  __ltoq
	.text
       .align    16,0x90
	.globl __ltoq
__ltoq:
# parameter 1: %rdi
# parameter 2: 32 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___ltoq.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
..B1.2:
        fnstcw    16(%rsp)
..B1.3:
        movzwl    16(%rsp), %eax
        orl       $-64768, %eax
        movw      %ax, 18(%rsp)
..B1.4:
        fldcw     18(%rsp)
..B1.5:
        fldt      32(%rsp)
        lea       __l_one(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.6:
        fldcw     16(%rsp)
..B1.7:
        movq      (%rsp), %rsi
        movq      %rsi, %rax
        shrq      $15, %rsi
        movq      $0xffffffffffff, %rdx
        movl      8(%rsp), %ecx
        andq      %rdx, %rsi
        shlq      $48, %rcx
        shlq      $49, %rax
        orq       %rcx, %rsi
        movq      %rax, (%rdi)
        movq      %rsi, 8(%rdi)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__ltoq,@function
	.size	__ltoq,.-__ltoq
	.data
# -- End  __ltoq
	.section .rodata, "a"
	.align 4
	.align 4
__l_one:
	.long	0
	.long	2147483648
	.long	16383
	.type	__l_one,@object
	.size	__l_one,12
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
