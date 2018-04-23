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
	.file "feraiseexcept.c"
	.text
..TXTST0:
# -- Begin  feraiseexcept
	.text
       .align    16,0x90
	.globl feraiseexcept
feraiseexcept:
# parameter 1: %edi
..B1.1:
	.cfi_startproc
..___tag_value_feraiseexcept.1:
..L2:

        testl     $1, %edi
        je        ..B1.3
..B1.2:
        lea       d_zero(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.3:
        testl     $4, %edi
        je        ..B1.5
..B1.4:
        movq      p_one(%rip), %rax
        movq      p_zero(%rip), %rdx
        movsd     (%rax), %xmm0
        divsd     (%rdx), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.5:
        testl     $2, %edi
        je        ..B1.7
..B1.6:
        lea       d_tiny(%rip), %rax
        movsd     (%rax), %xmm0
        addsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.7:
        testl     $8, %edi
        je        ..B1.9
..B1.8:
        lea       d_huge(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.9:
        testl     $16, %edi
        je        ..B1.11
..B1.10:
        lea       d_norm(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.11:
        testl     $32, %edi
        je        ..B1.13
..B1.12:
        movq      p_one(%rip), %rax
        movq      p_norm(%rip), %rdx
        movsd     (%rax), %xmm0
        addsd     (%rdx), %xmm0
        movsd     %xmm0, -8(%rsp)
..B1.13:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	feraiseexcept,@function
	.size	feraiseexcept,.-feraiseexcept
	.data
# -- End  feraiseexcept
	.section .rodata, "a"
	.align 8
	.align 8
d_zero:
	.long	0x00000000,0x00000000
	.type	d_zero,@object
	.size	d_zero,8
	.align 8
d_one:
	.long	0x00000000,0x3ff00000
	.type	d_one,@object
	.size	d_one,8
	.align 8
d_tiny:
	.long	0xffffffff,0x000fffff
	.type	d_tiny,@object
	.size	d_tiny,8
	.align 8
d_huge:
	.long	0xffffffff,0x7fefffff
	.type	d_huge,@object
	.size	d_huge,8
	.align 8
d_norm:
	.long	0x00000000,0x00100000
	.type	d_norm,@object
	.size	d_norm,8
	.section .data.rel.ro.local, "aw",@progbits
	.align 8
	.align 8
p_one:
	.quad	d_one
	.type	p_one,@object
	.size	p_one,8
	.align 8
p_zero:
	.quad	d_zero
	.type	p_zero,@object
	.size	p_zero,8
	.align 8
p_norm:
	.quad	d_norm
	.type	p_norm,@object
	.size	p_norm,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
