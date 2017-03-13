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
	.file "libm_exp_k32.c"
	.text
..TXTST0:
# -- Begin  __libm_exp_k32
	.text
       .align    16,0x90
	.globl __libm_exp_k32
__libm_exp_k32:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___libm_exp_k32.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movl      -12(%rsp), %eax
        movl      %eax, %edx
        andl      $2147483647, %edx
        shrl      $31, %eax
        cmpl      $1012924416, %edx
        jb        ..B1.3
..B1.2:
        movsd     .L_2il0floatpacket.0(%rip), %xmm1
        lea       _TWO_52H(%rip), %rdx
        movsd     .L_2il0floatpacket.1(%rip), %xmm3
        lea       SC2_BIAS(%rip), %rsi
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        lea       _SC2(%rip), %r10
        movsd     .L_2il0floatpacket.4(%rip), %xmm13
        movsd     (%rdx), %xmm2
        movsd     .L_2il0floatpacket.6(%rip), %xmm10
        mulsd     %xmm0, %xmm1
        movsd     .L_2il0floatpacket.9(%rip), %xmm6
        addsd     %xmm2, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        movsd     .L_2il0floatpacket.3(%rip), %xmm7
        subsd     %xmm2, %xmm5
        mulsd     %xmm5, %xmm3
        mulsd     %xmm4, %xmm5
        subsd     %xmm3, %xmm0
        movaps    %xmm0, %xmm8
        movsd     %xmm0, -16(%rsp)
        subsd     %xmm5, %xmm8
        movaps    %xmm8, %xmm9
        subsd     %xmm8, %xmm0
        addsd     %xmm8, %xmm6
        subsd     %xmm5, %xmm0
        movsd     %xmm6, -32(%rsp)
        addsd     %xmm0, %xmm9
        movaps    %xmm9, %xmm11
        mulsd     %xmm9, %xmm11
        mulsd     %xmm11, %xmm13
        mulsd     %xmm11, %xmm10
        addsd     .L_2il0floatpacket.5(%rip), %xmm13
        addsd     .L_2il0floatpacket.7(%rip), %xmm10
        mulsd     %xmm11, %xmm13
        mulsd     %xmm9, %xmm10
        movl      $0, -32(%rsp)
        addsd     %xmm10, %xmm13
        movsd     -32(%rsp), %xmm14
        movsd     .L_2il0floatpacket.8(%rip), %xmm12
        addsd     %xmm14, %xmm7
        mulsd     %xmm11, %xmm13
        mulsd     %xmm11, %xmm12
        subsd     %xmm7, %xmm8
        addsd     %xmm12, %xmm13
        addsd     %xmm8, %xmm0
        movl      -40(%rsp), %ecx
        movl      %ecx, %r8d
        shll      $25, %r8d
        addsd     %xmm13, %xmm0
        sarl      $25, %r8d
        subl      %r8d, %ecx
        movslq    %r8d, %r8
        shlq      $4, %r8
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r9
        shrl      $7, %ecx
        movslq    (%rsi,%rax,4), %rdi
        movsd     1032(%r9,%r8), %xmm15
        addq      %rcx, %rdi
        movaps    %xmm15, %xmm1
        mulsd     %xmm0, %xmm1
        addsd     %xmm14, %xmm0
        mulsd     %xmm14, %xmm15
        mulsd     1024(%r9,%r8), %xmm0
        shlq      $52, %rdi
        addsd     %xmm0, %xmm1
        movq      %rdi, -24(%rsp)
        addsd     %xmm15, %xmm1
        mulsd     -24(%rsp), %xmm1
        mulsd     (%r10,%rax,8), %xmm1
        jmp       ..B1.4
..B1.3:
        movsd     .L_2il0floatpacket.9(%rip), %xmm1
        addsd     -16(%rsp), %xmm1
..B1.4:
        movaps    %xmm1, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_exp_k32,@function
	.size	__libm_exp_k32,.-__libm_exp_k32
	.data
# -- End  __libm_exp_k32
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x652b82fe,0x40671547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3f762e42
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x3de6af28,0x3e2fdf47
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
SC2_BIAS:
	.long	511
	.long	1535
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_SC2:
	.long	0
	.long	1609564160
	.long	0
	.long	535822336
	.type	_SC2,@object
	.size	_SC2,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
