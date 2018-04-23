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
	.file "trunc_gen.c"
	.text
..TXTST0:
# -- Begin  trunc
	.text
       .align    16,0x90
	.globl trunc
trunc:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_trunc.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        movl      %edx, %eax
        andl      $2147483647, %eax
        addl      $-1072693248, %eax
        cmpl      $54525952, %eax
        jae       ..B1.6
..B1.2:
        movl      %eax, %ecx
        shrl      $20, %ecx
        cmpl      $22020096, %eax
        jae       ..B1.4
..B1.3:
        negl      %ecx
        movl      $-1, %eax
        addl      $20, %ecx
        shll      %cl, %eax
        andl      %eax, %edx
        movl      %edx, -4(%rsp)
        movl      $0, -8(%rsp)
        movsd     -8(%rsp), %xmm0
        ret       
..B1.4:
        negl      %ecx
        movl      $-1, %eax
        addl      $20, %ecx
        shll      %cl, %eax
        andl      %eax, -8(%rsp)
        movsd     -8(%rsp), %xmm0
..B1.5:
        ret       
..B1.6:
        testl     %eax, %eax
        jl        ..B1.9
..B1.7:
        lea       _ones(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
..B1.8:
        ret       
..B1.9:
        lea       _zeros(%rip), %rax
        shrl      $31, %edx
        movsd     (%rax,%rdx,8), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	trunc,@function
	.size	trunc,.-trunc
	.data
# -- End  trunc
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
