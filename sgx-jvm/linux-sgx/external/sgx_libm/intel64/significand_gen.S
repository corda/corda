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
	.file "significand_gen.c"
	.text
..TXTST0:
# -- Begin  significand
	.text
       .align    16,0x90
	.globl significand
significand:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_significand.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %ecx
        movl      %ecx, %edx
        andl      $2147483647, %edx
        lea       -1048576(%rdx), %eax
        cmpl      $2145386496, %eax
        jb        ..B1.8
..B1.2:
        testl     %eax, %eax
        jge       ..B1.7
..B1.3:
        movl      -8(%rsp), %eax
        orl       %eax, %edx
        jne       ..B1.5
..B1.4:
        movsd     -8(%rsp), %xmm0
        ret       
..B1.5:
        lea       _TWO_55(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $-2146435073, %edx
        orl       $1072693248, %edx
        movl      %edx, -4(%rsp)
        movsd     -8(%rsp), %xmm0
..B1.6:
        ret       
..B1.7:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        ret       
..B1.8:
        andl      $-2146435073, %ecx
        orl       $1072693248, %ecx
        movl      %ecx, -4(%rsp)
        movsd     -8(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	significand,@function
	.size	significand,.-significand
	.data
# -- End  significand
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_55:
	.long	0
	.long	1130364928
	.type	_TWO_55,@object
	.size	_TWO_55,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
