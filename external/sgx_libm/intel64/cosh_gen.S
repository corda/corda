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
	.file "cosh_gen.c"
	.text
..TXTST0:
# -- Begin  cosh
	.text
       .align    16,0x90
	.globl cosh
cosh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cosh.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        andl      $2147483647, %eax
        cmpl      $1077338037, %eax
        jae       ..B1.8
..B1.2:
        cmpl      $1065646817, %eax
        jae       ..B1.7
..B1.3:
        movsd     .L_2il0floatpacket.12(%rip), %xmm4
        cmpl      $1012924416, %eax
        jae       ..B1.5
..B1.4:
        andps     .L_2il0floatpacket.11(%rip), %xmm0
        addsd     %xmm4, %xmm0
        movsd     %xmm0, -24(%rsp)
        ret       
..B1.5:
        movsd     -8(%rsp), %xmm1
        mulsd     %xmm1, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        movsd     .L_2il0floatpacket.9(%rip), %xmm0
        mulsd     %xmm2, %xmm0
        movsd     .L_2il0floatpacket.10(%rip), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.8(%rip), %xmm0
        mulsd     %xmm1, %xmm0
        addsd     %xmm3, %xmm0
        addsd     %xmm4, %xmm0
..B1.6:
        ret       
..B1.7:
        movsd     -8(%rsp), %xmm1
        lea       _TWO_52(%rip), %rax
        andps     .L_2il0floatpacket.11(%rip), %xmm1
        lea       _ptwo_32p1(%rip), %rcx
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
        lea       _ntwo_32p1(%rip), %rsi
        mulsd     %xmm1, %xmm2
        lea       _two_32(%rip), %rdi
        movsd     .L_2il0floatpacket.1(%rip), %xmm3
        movsd     %xmm1, -8(%rsp)
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        movsd     (%rcx), %xmm6
        movsd     (%rsi), %xmm7
        movsd     .L_2il0floatpacket.4(%rip), %xmm13
        movsd     .L_2il0floatpacket.6(%rip), %xmm14
        movsd     .L_2il0floatpacket.3(%rip), %xmm8
        movsd     .L_2il0floatpacket.3(%rip), %xmm9
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r10
        addsd     (%rax), %xmm2
        movsd     %xmm2, -24(%rsp)
        movsd     -24(%rsp), %xmm5
        movl      -24(%rsp), %r9d
        movl      %r9d, %edx
        shll      $25, %edx
        subsd     (%rax), %xmm5
        mulsd     %xmm5, %xmm3
        mulsd     %xmm4, %xmm5
        subsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm10
        sarl      $25, %edx
        subsd     %xmm5, %xmm10
        movaps    %xmm10, %xmm11
        movaps    %xmm10, %xmm15
        movslq    %edx, %rdx
        subsd     %xmm10, %xmm1
        addsd     %xmm10, %xmm6
        subsd     %xmm10, %xmm7
        subsd     %xmm5, %xmm1
        movsd     %xmm6, -40(%rsp)
        subl      %edx, %r9d
        movsd     %xmm7, -32(%rsp)
        pxor      %xmm5, %xmm5
        movsd     -40(%rsp), %xmm2
        addsd     %xmm1, %xmm11
        subsd     (%rdi), %xmm2
        movaps    %xmm11, %xmm12
        addsd     %xmm2, %xmm8
        mulsd     %xmm11, %xmm12
        subsd     %xmm8, %xmm15
        mulsd     %xmm12, %xmm13
        mulsd     %xmm12, %xmm14
        addsd     %xmm1, %xmm15
        addsd     .L_2il0floatpacket.5(%rip), %xmm13
        addsd     .L_2il0floatpacket.7(%rip), %xmm14
        mulsd     %xmm12, %xmm13
        mulsd     %xmm12, %xmm14
        mulsd     %xmm11, %xmm13
        addsd     .L_2il0floatpacket.8(%rip), %xmm14
        addsd     %xmm13, %xmm15
        mulsd     %xmm12, %xmm14
        movsd     -32(%rsp), %xmm0
        addsd     %xmm14, %xmm15
        addsd     (%rdi), %xmm0
        shlq      $4, %rdx
        addsd     %xmm0, %xmm9
        movsd     1032(%r10,%rdx), %xmm8
        addsd     %xmm9, %xmm10
        movaps    %xmm8, %xmm4
        addsd     %xmm10, %xmm1
        mulsd     %xmm15, %xmm4
        addsd     %xmm2, %xmm15
        mulsd     %xmm8, %xmm2
        mulsd     1024(%r10,%rdx), %xmm15
        xorps     .L_2il0floatpacket.13(%rip), %xmm1
        negq      %rdx
        shrl      $7, %r9d
        subsd     %xmm13, %xmm1
        addsd     %xmm15, %xmm4
        addsd     %xmm14, %xmm1
        movsd     1032(%r10,%rdx), %xmm15
        movaps    %xmm15, %xmm3
        mulsd     %xmm1, %xmm3
        addsd     %xmm0, %xmm1
        mulsd     %xmm15, %xmm0
        mulsd     1024(%r10,%rdx), %xmm1
        shll      $23, %r9d
        addsd     %xmm1, %xmm3
        pxor      %xmm1, %xmm1
        lea       1056964608(%r9), %r8d
        negl      %r9d
        addl      $1056964608, %r9d
        movl      %r9d, -12(%rsp)
        movl      %r8d, -16(%rsp)
        cvtss2sd  -12(%rsp), %xmm5
        cvtss2sd  -16(%rsp), %xmm1
        mulsd     %xmm5, %xmm3
        mulsd     %xmm0, %xmm5
        mulsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm1
        addsd     %xmm3, %xmm4
        movaps    %xmm5, %xmm0
        addsd     %xmm1, %xmm0
        subsd     %xmm0, %xmm1
        addsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm0
        ret       
..B1.8:
        cmpl      $1082536910, %eax
        jb        ..B1.14
..B1.9:
        jne       ..B1.11
..B1.10:
        cmpl      $-1883637635, -8(%rsp)
        jbe       ..B1.14
..B1.11:
        cmpl      $2146435072, %eax
        jb        ..B1.15
..B1.12:
        movsd     -8(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
..B1.13:
        ret       
..B1.14:
        movsd     -8(%rsp), %xmm12
        lea       _TWO_52(%rip), %rax
        andps     .L_2il0floatpacket.11(%rip), %xmm12
        lea       _ptwo_32p1(%rip), %rdx
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        lea       _two_32(%rip), %rcx
        mulsd     %xmm12, %xmm0
        lea       _TWO_512(%rip), %r9
        movsd     .L_2il0floatpacket.1(%rip), %xmm1
        movsd     %xmm12, -8(%rsp)
        movsd     .L_2il0floatpacket.2(%rip), %xmm2
        movsd     .L_2il0floatpacket.6(%rip), %xmm11
        movsd     .L_2il0floatpacket.4(%rip), %xmm8
        movsd     (%rdx), %xmm4
        movsd     .L_2il0floatpacket.3(%rip), %xmm5
        movsd     .L_2il0floatpacket.8(%rip), %xmm10
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r8
        movl      $0, -16(%rsp)
        addsd     (%rax), %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -24(%rsp), %xmm3
        movl      -24(%rsp), %esi
        movl      %esi, %edi
        shll      $25, %edi
        subsd     (%rax), %xmm3
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm3
        subsd     %xmm1, %xmm12
        movaps    %xmm12, %xmm6
        sarl      $25, %edi
        subsd     %xmm3, %xmm6
        movaps    %xmm6, %xmm7
        subl      %edi, %esi
        movslq    %edi, %rdi
        subsd     %xmm6, %xmm12
        addsd     %xmm6, %xmm4
        subsd     %xmm3, %xmm12
        movsd     %xmm4, -24(%rsp)
        addsd     %xmm12, %xmm7
        movaps    %xmm7, %xmm9
        mulsd     %xmm7, %xmm9
        mulsd     %xmm9, %xmm11
        mulsd     %xmm9, %xmm8
        mulsd     %xmm9, %xmm10
        addsd     .L_2il0floatpacket.7(%rip), %xmm11
        addsd     .L_2il0floatpacket.5(%rip), %xmm8
        mulsd     %xmm9, %xmm11
        mulsd     %xmm7, %xmm8
        movsd     -24(%rsp), %xmm0
        addsd     %xmm8, %xmm11
        subsd     (%rcx), %xmm0
        mulsd     %xmm9, %xmm11
        addsd     %xmm0, %xmm5
        addsd     %xmm10, %xmm11
        subsd     %xmm5, %xmm6
        shlq      $4, %rdi
        addsd     %xmm6, %xmm12
        movsd     1032(%r8,%rdi), %xmm13
        addsd     %xmm11, %xmm12
        movaps    %xmm13, %xmm14
        mulsd     %xmm12, %xmm14
        addsd     %xmm0, %xmm12
        mulsd     %xmm13, %xmm0
        mulsd     1024(%r8,%rdi), %xmm12
        shrl      $7, %esi
        addsd     %xmm12, %xmm14
        shll      $20, %esi
        addsd     %xmm14, %xmm0
        addl      $534773760, %esi
        movl      %esi, -12(%rsp)
        mulsd     -16(%rsp), %xmm0
        mulsd     (%r9), %xmm0
        ret       
..B1.15:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	cosh,@function
	.size	cosh,.-cosh
	.data
# -- End  cosh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.11:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 16
.L_2il0floatpacket.13:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,16
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
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x6a3d464e,0x3f56c171
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x5554a6cf,0x3fa55555
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 4
_TWO_52:
	.long	0
	.long	1127219200
	.type	_TWO_52,@object
	.size	_TWO_52,8
	.align 4
_ptwo_32p1:
	.long	1048576
	.long	1106247680
	.type	_ptwo_32p1,@object
	.size	_ptwo_32p1,8
	.align 4
_ntwo_32p1:
	.long	4292870144
	.long	3253731327
	.type	_ntwo_32p1,@object
	.size	_ntwo_32p1,8
	.align 4
_two_32:
	.long	0
	.long	1106247680
	.type	_two_32,@object
	.size	_two_32,8
	.align 4
_TWO_512:
	.long	0
	.long	1609564160
	.type	_TWO_512,@object
	.size	_TWO_512,8
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
