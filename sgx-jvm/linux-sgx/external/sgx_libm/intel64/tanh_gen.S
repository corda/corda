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
	.file "tanh_gen.c"
	.text
..TXTST0:
# -- Begin  tanh
	.text
       .align    16,0x90
	.globl tanh
tanh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_tanh.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $2146435072, %ecx
        jae       ..B1.16
..B1.2:
        cmpl      $1077088193, %ecx
        jae       ..B1.15
..B1.3:
        cmpl      $1075078759, %ecx
        jae       ..B1.14
..B1.4:
        cmpl      $1066695393, %ecx
        jae       ..B1.13
..B1.5:
        cmpl      $1012924416, %ecx
        jae       ..B1.12
..B1.6:
        shrl      $31, %eax
        cmpl      $1048576, %ecx
        jb        ..B1.8
..B1.7:
        movl      %eax, %eax
        lea       _small_value_64(%rip), %rdx
        movsd     (%rdx,%rax,8), %xmm1
        mulsd     (%rdx), %xmm1
        subsd     %xmm1, %xmm0
        ret       
..B1.8:
        movl      -8(%rsp), %edx
        orl       %edx, %ecx
        je        ..B1.10
..B1.9:
        lea       _small_value_64(%rip), %rdx
        movsd     -8(%rsp), %xmm0
        movsd     (%rdx,%rax,8), %xmm1
        mulsd     (%rdx), %xmm1
        movsd     %xmm1, -32(%rsp)
        subsd     %xmm1, %xmm0
        ret       
..B1.10:
        movsd     -8(%rsp), %xmm0
..B1.11:
        ret       
..B1.12:
        movsd     -8(%rsp), %xmm4
        movaps    %xmm4, %xmm2
        mulsd     %xmm4, %xmm2
        movaps    %xmm2, %xmm1
        mulsd     %xmm2, %xmm1
        movsd     .L_2il0floatpacket.20(%rip), %xmm0
        movsd     .L_2il0floatpacket.22(%rip), %xmm3
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm3
        addsd     .L_2il0floatpacket.21(%rip), %xmm0
        addsd     .L_2il0floatpacket.23(%rip), %xmm3
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm0
        mulsd     %xmm4, %xmm0
        addsd     %xmm4, %xmm0
        ret       
..B1.13:
        movsd     -8(%rsp), %xmm0
        lea       _TWO_52(%rip), %rdx
        andps     .L_2il0floatpacket.24(%rip), %xmm0
        xorl      %r10d, %r10d
        mulsd     .L_2il0floatpacket.10(%rip), %xmm0
        movq      $0x3ff0000000000000, %rsi
        movsd     .L_2il0floatpacket.11(%rip), %xmm4
        lea       _TWO_32(%rip), %r9
        mulsd     %xmm0, %xmm4
        lea       ones(%rip), %r11
        movsd     .L_2il0floatpacket.12(%rip), %xmm5
        movsd     .L_2il0floatpacket.13(%rip), %xmm6
        movsd     .L_2il0floatpacket.25(%rip), %xmm3
        movaps    %xmm3, %xmm8
        addsd     (%rdx), %xmm4
        movsd     %xmm4, -32(%rsp)
        movsd     -32(%rsp), %xmm7
        movsd     .L_2il0floatpacket.15(%rip), %xmm12
        movsd     .L_2il0floatpacket.17(%rip), %xmm14
        movsd     .L_2il0floatpacket.14(%rip), %xmm2
        movaps    %xmm2, %xmm9
        subsd     (%rdx), %xmm7
        mulsd     %xmm7, %xmm5
        mulsd     %xmm6, %xmm7
        subsd     %xmm5, %xmm0
        movaps    %xmm0, %xmm10
        movaps    %xmm3, %xmm6
        movl      -32(%rsp), %ecx
        movl      %ecx, %edi
        shll      $25, %edi
        subsd     %xmm7, %xmm10
        movaps    %xmm10, %xmm11
        subsd     %xmm10, %xmm0
        addsd     %xmm10, %xmm8
        subsd     %xmm7, %xmm0
        movsd     %xmm8, -40(%rsp)
        addsd     %xmm0, %xmm11
        movaps    %xmm11, %xmm13
        mulsd     %xmm11, %xmm13
        mulsd     %xmm13, %xmm12
        mulsd     %xmm13, %xmm14
        addsd     .L_2il0floatpacket.16(%rip), %xmm12
        addsd     .L_2il0floatpacket.18(%rip), %xmm14
        mulsd     %xmm13, %xmm12
        mulsd     %xmm13, %xmm14
        mulsd     %xmm11, %xmm12
        addsd     .L_2il0floatpacket.19(%rip), %xmm14
        movl      %r10d, -40(%rsp)
        movsd     -40(%rsp), %xmm4
        mulsd     %xmm13, %xmm14
        addsd     %xmm4, %xmm9
        sarl      $25, %edi
        subsd     %xmm9, %xmm10
        subl      %edi, %ecx
        addsd     %xmm10, %xmm0
        movslq    %edi, %rdi
        addsd     %xmm12, %xmm0
        shlq      $4, %rdi
        addsd     %xmm14, %xmm0
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r8
        shrl      $7, %ecx
        movsd     (%r9), %xmm5
        movsd     1032(%r8,%rdi), %xmm15
        movaps    %xmm15, %xmm1
        mulsd     %xmm0, %xmm1
        addsd     %xmm4, %xmm0
        mulsd     %xmm15, %xmm4
        mulsd     1024(%r8,%rdi), %xmm0
        shlq      $52, %rcx
        addsd     %xmm0, %xmm1
        addq      %rsi, %rcx
        movaps    %xmm2, %xmm0
        movq      %rcx, -16(%rsp)
        movsd     -16(%rsp), %xmm7
        mulsd     %xmm7, %xmm1
        mulsd     %xmm4, %xmm7
        movaps    %xmm7, %xmm10
        addsd     %xmm7, %xmm0
        addsd     %xmm7, %xmm6
        addsd     (%r9), %xmm0
        addsd     %xmm5, %xmm6
        movsd     %xmm0, -32(%rsp)
        movsd     -32(%rsp), %xmm0
        movsd     %xmm6, -32(%rsp)
        subsd     %xmm5, %xmm0
        movsd     -32(%rsp), %xmm9
        subsd     %xmm0, %xmm10
        subsd     (%r9), %xmm9
        addsd     %xmm2, %xmm10
        movsd     %xmm7, -40(%rsp)
        movaps    %xmm9, %xmm8
        shrl      $31, %eax
        subsd     %xmm9, %xmm7
        addsd     %xmm1, %xmm10
        addsd     %xmm3, %xmm7
        movsd     (%r11,%rax,8), %xmm12
        addsd     %xmm7, %xmm1
        addsd     %xmm1, %xmm8
        divsd     %xmm8, %xmm3
        movsd     %xmm3, -24(%rsp)
        movl      %r10d, -24(%rsp)
        movsd     -24(%rsp), %xmm11
        mulsd     %xmm11, %xmm1
        subsd     %xmm11, %xmm3
        mulsd     %xmm11, %xmm9
        mulsd     %xmm3, %xmm8
        addsd     %xmm2, %xmm9
        addsd     %xmm8, %xmm1
        movaps    %xmm11, %xmm2
        addsd     %xmm1, %xmm9
        addsd     %xmm3, %xmm2
        mulsd     %xmm9, %xmm2
        movaps    %xmm0, %xmm1
        mulsd     %xmm11, %xmm0
        subsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm1
        addsd     %xmm11, %xmm3
        mulsd     %xmm3, %xmm10
        movsd     %xmm0, -8(%rsp)
        addsd     %xmm10, %xmm1
        mulsd     %xmm12, %xmm0
        mulsd     %xmm1, %xmm12
        addsd     %xmm12, %xmm0
        ret       
..B1.14:
        lea       _TWO_32(%rip), %rdx
        lea       _TWO_52(%rip), %rcx
        movsd     -8(%rsp), %xmm1
        movq      $0x3ff0000000000000, %rsi
        andps     .L_2il0floatpacket.24(%rip), %xmm1
        lea       twos(%rip), %r8
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        movaps    %xmm1, %xmm7
        movsd     (%rdx), %xmm0
        lea       ones(%rip), %r9
        movsd     (%rcx), %xmm4
        addsd     %xmm1, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -32(%rsp), %xmm6
        movsd     .L_2il0floatpacket.1(%rip), %xmm2
        mulsd     %xmm1, %xmm2
        subsd     (%rdx), %xmm6
        movsd     .L_2il0floatpacket.2(%rip), %xmm12
        subsd     %xmm6, %xmm7
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm7
        addsd     %xmm6, %xmm4
        addsd     %xmm2, %xmm7
        movsd     %xmm4, -32(%rsp)
        movsd     -32(%rsp), %xmm5
        movsd     .L_2il0floatpacket.6(%rip), %xmm9
        movsd     .L_2il0floatpacket.25(%rip), %xmm11
        movslq    -32(%rsp), %rdi
        shlq      $52, %rdi
        subsd     (%rcx), %xmm5
        addq      %rsi, %rdi
        subsd     %xmm5, %xmm6
        movq      %rdi, -16(%rsp)
        addsd     %xmm6, %xmm7
        movaps    %xmm7, %xmm8
        mulsd     %xmm7, %xmm8
        mulsd     %xmm8, %xmm12
        mulsd     %xmm8, %xmm9
        addsd     .L_2il0floatpacket.3(%rip), %xmm12
        addsd     .L_2il0floatpacket.7(%rip), %xmm9
        mulsd     %xmm8, %xmm12
        mulsd     %xmm8, %xmm9
        addsd     .L_2il0floatpacket.4(%rip), %xmm12
        addsd     .L_2il0floatpacket.8(%rip), %xmm9
        mulsd     %xmm8, %xmm12
        mulsd     %xmm8, %xmm9
        addsd     .L_2il0floatpacket.5(%rip), %xmm12
        addsd     .L_2il0floatpacket.9(%rip), %xmm9
        mulsd     %xmm7, %xmm12
        movsd     -16(%rsp), %xmm10
        subsd     %xmm12, %xmm9
        divsd     %xmm9, %xmm12
        mulsd     .L_2il0floatpacket.10(%rip), %xmm12
        shrl      $31, %eax
        addsd     %xmm11, %xmm12
        movl      %eax, %r10d
        movsd     %xmm12, -24(%rsp)
        xorl      $1, %eax
        mulsd     %xmm10, %xmm12
        movsd     (%r8,%rax,8), %xmm0
        addsd     %xmm11, %xmm12
        divsd     %xmm12, %xmm0
        movsd     %xmm7, -8(%rsp)
        addsd     (%r9,%r10,8), %xmm0
        ret       
..B1.15:
        lea       ones(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        shrl      $31, %eax
        movsd     (%rdx,%rax,8), %xmm0
        subsd     (%rcx,%rax,8), %xmm0
        ret       
..B1.16:
        andl      $-2146435073, %ecx
        orl       -8(%rsp), %ecx
        jne       ..B1.18
..B1.17:
        lea       ones(%rip), %rdx
        shrl      $31, %eax
        movsd     (%rdx,%rax,8), %xmm0
        ret       
..B1.18:
        movsd     -8(%rsp), %xmm0
..B1.19:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	tanh,@function
	.size	tanh,.-tanh
	.data
# -- End  tanh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.24:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.24,@object
	.size	.L_2il0floatpacket.24,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x60000000,0x40071547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0xf85ddf44,0x3e64ae0b
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0xfc0798c2,0x3fb3adff
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x0e243699,0x406e3d92
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xefbe8c98,0x40f689a6
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x1ba09c9e,0x4156de47
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xa57ad784,0x4018d7c2
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x63529aa4,0x40b6b90b
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0xafc4d247,0x412d4376
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x41707ef8
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x652b82fe,0x40671547
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0x3f762e42
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x3de6af28,0x3e2fdf47
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.15:
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,8
	.align 8
.L_2il0floatpacket.16:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,8
	.align 8
.L_2il0floatpacket.17:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,8
	.align 8
.L_2il0floatpacket.18:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,8
	.align 8
.L_2il0floatpacket.19:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,8
	.align 8
.L_2il0floatpacket.20:
	.long	0xbe3b77e0,0x3f9662a1
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,8
	.align 8
.L_2il0floatpacket.21:
	.long	0x1110d2a4,0x3fc11111
	.type	.L_2il0floatpacket.21,@object
	.size	.L_2il0floatpacket.21,8
	.align 8
.L_2il0floatpacket.22:
	.long	0x00fa2d61,0xbfaba1ba
	.type	.L_2il0floatpacket.22,@object
	.size	.L_2il0floatpacket.22,8
	.align 8
.L_2il0floatpacket.23:
	.long	0x55555555,0xbfd55555
	.type	.L_2il0floatpacket.23,@object
	.size	.L_2il0floatpacket.23,8
	.align 8
.L_2il0floatpacket.25:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.25,@object
	.size	.L_2il0floatpacket.25,8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
twos:
	.long	0x00000000,0x40000000
	.long	0x00000000,0xc0000000
	.type	twos,@object
	.size	twos,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_TWO_52:
	.long	0
	.long	1127219200
	.type	_TWO_52,@object
	.size	_TWO_52,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
