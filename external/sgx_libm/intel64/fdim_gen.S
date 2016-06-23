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
	.file "fdim_gen.c"
	.text
..TXTST0:
# -- Begin  fdim
	.text
       .align    16,0x90
	.globl fdim
fdim:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_fdim.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movsd     %xmm1, -8(%rsp)
        movzwl    -10(%rsp), %edx
        andl      $32752, %edx
        movzwl    -2(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %edx
        shrl      $4, %eax
        cmpl      $2047, %edx
        je        ..B1.14
..B1.2:
        cmpl      $2047, %eax
        je        ..B1.11
..B1.3:
        movsd     -16(%rsp), %xmm0
        movsd     -8(%rsp), %xmm2
        comisd    %xmm2, %xmm0
        jbe       ..B1.10
..B1.4:
        cmpl      $2047, %edx
        je        ..B1.8
..B1.5:
        cmpl      $2047, %eax
        je        ..B1.8
..B1.6:
        cmpl      $2046, %edx
        je        ..B1.9
..B1.7:
        cmpl      $2046, %eax
        je        ..B1.9
..B1.8:
        subsd     %xmm2, %xmm0
        ret       
..B1.9:
        movsd     .L_2il0floatpacket.0(%rip), %xmm1
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm2
        subsd     %xmm2, %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -24(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.1(%rip), %xmm0
        ret       
..B1.10:
        lea       _zeros(%rip), %rax
        movsd     (%rax), %xmm0
        ret       
..B1.11:
        testl     $1048575, -4(%rsp)
        jne       ..B1.13
..B1.12:
        cmpl      $0, -8(%rsp)
        je        ..B1.3
..B1.13:
        movsd     -8(%rsp), %xmm0
        ret       
..B1.14:
        testl     $1048575, -12(%rsp)
        jne       ..B1.16
..B1.15:
        cmpl      $0, -16(%rsp)
        je        ..B1.2
..B1.16:
        movsd     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fdim,@function
	.size	fdim,.-fdim
	.data
# -- End  fdim
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
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
