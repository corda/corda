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
	.file "frexp_gen.c"
	.text
..TXTST0:
# -- Begin  frexp
	.text
       .align    16,0x90
	.globl frexp
frexp:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_frexp.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        movl      %eax, %edx
        andl      $2147483647, %edx
        lea       -1048576(%rdx), %ecx
        cmpl      $2145386496, %ecx
        jb        ..B1.6
..B1.2:
        testl     %ecx, %ecx
        jge       ..B1.5
..B1.3:
        movl      -8(%rsp), %eax
        orl       %eax, %edx
        je        ..B1.5
..B1.4:
        lea       _TWO_55(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        andl      $-2146435073, %edx
        shrl      $20, %ecx
        orl       $1071644672, %edx
        movl      %edx, -4(%rsp)
        addl      $-1077, %ecx
        movl      %ecx, (%rdi)
        movsd     -8(%rsp), %xmm0
        ret       
..B1.5:
        movl      $0, (%rdi)
        movsd     -8(%rsp), %xmm0
        ret       
..B1.6:
        andl      $-2146435073, %eax
        shrl      $20, %ecx
        orl       $1071644672, %eax
        movl      %eax, -4(%rsp)
        addl      $-1021, %ecx
        movl      %ecx, (%rdi)
        movsd     -8(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	frexp,@function
	.size	frexp,.-frexp
	.data
# -- End  frexp
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_55:
	.long	0
	.long	1130364928
	.type	_TWO_55,@object
	.size	_TWO_55,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
