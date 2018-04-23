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
	.file "ilogb_gen.c"
	.text
..TXTST0:
# -- Begin  ilogb
	.text
       .align    16,0x90
	.globl ilogb
ilogb:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_ilogb.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movl      -12(%rsp), %ecx
        andl      $2147483647, %ecx
        lea       -1048576(%rcx), %eax
        cmpl      $2145386496, %eax
        jb        ..B1.4
..B1.2:
        testl     %eax, %eax
        jl        ..B1.5
..B1.3:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        addl      $-2146435072, %ecx
        orl       -16(%rsp), %ecx
        movl      $2147483647, %ecx
        movsd     (%rax), %xmm0
        movl      $-2147483648, %eax
        mulsd     (%rdx), %xmm0
        cmove     %ecx, %eax
        movsd     %xmm0, -24(%rsp)
        ret       
..B1.4:
        shrl      $20, %eax
        addl      $-1022, %eax
        ret       
..B1.5:
        movl      -16(%rsp), %eax
        orl       %eax, %ecx
        je        ..B1.7
..B1.6:
        lea       _TWO_55(%rip), %rax
        movsd     -16(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -16(%rsp)
        movl      -12(%rsp), %eax
        andl      $2147483647, %eax
        shrl      $20, %eax
        addl      $-1078, %eax
        ret       
..B1.7:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        movl      $-2147483648, %eax
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, -24(%rsp)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	ilogb,@function
	.size	ilogb,.-ilogb
	.data
# -- End  ilogb
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
