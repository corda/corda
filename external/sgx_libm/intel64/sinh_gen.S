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
	.file "sinh_gen.c"
	.text
..TXTST0:
# -- Begin  sinh
	.text
       .align    16,0x90
	.globl sinh
sinh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_sinh.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $1077338037, %ecx
        jae       ..B1.12
..B1.2:
        cmpl      $1066695393, %ecx
        jae       ..B1.11
..B1.3:
        cmpl      $1012924416, %ecx
        jae       ..B1.10
..B1.4:
        shrl      $31, %eax
        cmpl      $1048576, %ecx
        jb        ..B1.6
..B1.5:
        movsd     .L_2il0floatpacket.13(%rip), %xmm1
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -24(%rsp)
        movsd     -24(%rsp), %xmm2
        mulsd     %xmm2, %xmm0
        ret       
..B1.6:
        movl      -8(%rsp), %edx
        orl       %edx, %ecx
        je        ..B1.8
..B1.7:
        lea       _small_value_64(%rip), %rdx
        movsd     (%rdx,%rax,8), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -24(%rsp), %xmm0
        addsd     -8(%rsp), %xmm0
        ret       
..B1.8:
        movsd     -8(%rsp), %xmm0
..B1.9:
        ret       
..B1.10:
        movsd     -8(%rsp), %xmm4
        movaps    %xmm4, %xmm2
        mulsd     %xmm4, %xmm2
        movaps    %xmm2, %xmm1
        mulsd     %xmm2, %xmm1
        movsd     .L_2il0floatpacket.9(%rip), %xmm0
        movsd     .L_2il0floatpacket.11(%rip), %xmm3
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm3
        addsd     .L_2il0floatpacket.10(%rip), %xmm0
        addsd     .L_2il0floatpacket.12(%rip), %xmm3
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm0
        mulsd     %xmm4, %xmm0
        addsd     %xmm4, %xmm0
        ret       
..B1.11:
        movsd     -8(%rsp), %xmm1
        lea       _TWO_52(%rip), %rdx
        andps     .L_2il0floatpacket.14(%rip), %xmm1
        lea       _ptwo_32p1(%rip), %rsi
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
        lea       _ntwo_32p1(%rip), %rdi
        mulsd     %xmm1, %xmm2
        lea       _two_32(%rip), %r8
        movsd     .L_2il0floatpacket.1(%rip), %xmm3
        andl      $-2147483648, %eax
        movsd     %xmm1, -8(%rsp)
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        movsd     (%rsi), %xmm6
        movsd     (%rdi), %xmm7
        movsd     .L_2il0floatpacket.4(%rip), %xmm13
        movsd     .L_2il0floatpacket.6(%rip), %xmm14
        movsd     .L_2il0floatpacket.3(%rip), %xmm8
        movsd     .L_2il0floatpacket.3(%rip), %xmm9
        addsd     (%rdx), %xmm2
        movsd     %xmm2, -24(%rsp)
        movsd     -24(%rsp), %xmm5
        movl      -24(%rsp), %r10d
        movl      %r10d, %ecx
        shll      $25, %ecx
        subsd     (%rdx), %xmm5
        mulsd     %xmm5, %xmm3
        mulsd     %xmm4, %xmm5
        subsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm10
        sarl      $25, %ecx
        subsd     %xmm5, %xmm10
        movaps    %xmm10, %xmm11
        movaps    %xmm10, %xmm15
        movslq    %ecx, %rcx
        subsd     %xmm10, %xmm1
        addsd     %xmm10, %xmm6
        subsd     %xmm10, %xmm7
        subsd     %xmm5, %xmm1
        movsd     %xmm6, -40(%rsp)
        subl      %ecx, %r10d
        movsd     %xmm7, -32(%rsp)
        pxor      %xmm5, %xmm5
        movsd     -40(%rsp), %xmm2
        addsd     %xmm1, %xmm11
        subsd     (%r8), %xmm2
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
        addsd     (%r8), %xmm0
        shrl      $7, %r10d
        addsd     %xmm0, %xmm9
        shll      $23, %r10d
        addsd     %xmm9, %xmm10
        shlq      $4, %rcx
        lea       1056964608(%r10), %r9d
        negl      %r10d
        orl       %eax, %r9d
        addl      $1056964608, %r10d
        addsd     %xmm10, %xmm1
        orl       %r10d, %eax
        movl      %eax, -12(%rsp)
        movq      __libm_exp_table_128@GOTPCREL(%rip), %rax
        xorps     .L_2il0floatpacket.15(%rip), %xmm1
        movl      %r9d, -16(%rsp)
        subsd     %xmm13, %xmm1
        cvtss2sd  -16(%rsp), %xmm5
        addsd     %xmm14, %xmm1
        movsd     1032(%rax,%rcx), %xmm8
        movaps    %xmm8, %xmm4
        mulsd     %xmm15, %xmm4
        addsd     %xmm2, %xmm15
        mulsd     %xmm8, %xmm2
        mulsd     1024(%rax,%rcx), %xmm15
        negq      %rcx
        addsd     %xmm15, %xmm4
        movsd     1032(%rax,%rcx), %xmm15
        movaps    %xmm15, %xmm3
        mulsd     %xmm1, %xmm3
        addsd     %xmm0, %xmm1
        mulsd     %xmm15, %xmm0
        mulsd     %xmm5, %xmm4
        mulsd     1024(%rax,%rcx), %xmm1
        mulsd     %xmm2, %xmm5
        addsd     %xmm1, %xmm3
        pxor      %xmm1, %xmm1
        cvtss2sd  -12(%rsp), %xmm1
        mulsd     %xmm1, %xmm3
        mulsd     %xmm0, %xmm1
        subsd     %xmm3, %xmm4
        movaps    %xmm5, %xmm0
        subsd     %xmm1, %xmm0
        subsd     %xmm0, %xmm5
        subsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm0
        ret       
..B1.12:
        cmpl      $1082536910, %ecx
        jb        ..B1.18
..B1.13:
        jne       ..B1.15
..B1.14:
        cmpl      $-1883637635, -8(%rsp)
        jbe       ..B1.18
..B1.15:
        cmpl      $2146435072, %ecx
        jb        ..B1.19
..B1.16:
        movsd     -8(%rsp), %xmm0
        addsd     %xmm0, %xmm0
..B1.17:
        ret       
..B1.18:
        movsd     -8(%rsp), %xmm12
        lea       _TWO_52(%rip), %rdx
        andps     .L_2il0floatpacket.14(%rip), %xmm12
        lea       _ptwo_32p1(%rip), %rcx
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        lea       _two_32(%rip), %rsi
        mulsd     %xmm12, %xmm0
        andl      $-2147483648, %eax
        movsd     .L_2il0floatpacket.1(%rip), %xmm1
        lea       _TWO_512(%rip), %r9
        movsd     %xmm12, -8(%rsp)
        movsd     .L_2il0floatpacket.2(%rip), %xmm2
        movsd     .L_2il0floatpacket.6(%rip), %xmm11
        movsd     .L_2il0floatpacket.4(%rip), %xmm8
        movsd     (%rcx), %xmm4
        movsd     .L_2il0floatpacket.3(%rip), %xmm5
        movsd     .L_2il0floatpacket.8(%rip), %xmm10
        movl      $0, -16(%rsp)
        addsd     (%rdx), %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -24(%rsp), %xmm3
        movl      -24(%rsp), %edi
        movl      %edi, %r8d
        shll      $25, %r8d
        subsd     (%rdx), %xmm3
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm3
        subsd     %xmm1, %xmm12
        movaps    %xmm12, %xmm6
        sarl      $25, %r8d
        subsd     %xmm3, %xmm6
        movaps    %xmm6, %xmm7
        subl      %r8d, %edi
        shrl      $7, %edi
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
        subsd     (%rsi), %xmm0
        mulsd     %xmm9, %xmm11
        addsd     %xmm0, %xmm5
        addsd     %xmm10, %xmm11
        subsd     %xmm5, %xmm6
        shll      $20, %edi
        addsd     %xmm6, %xmm12
        movslq    %r8d, %r8
        addl      $534773760, %edi
        shlq      $4, %r8
        orl       %edi, %eax
        movl      %eax, -12(%rsp)
        addsd     %xmm11, %xmm12
        movq      __libm_exp_table_128@GOTPCREL(%rip), %rax
        movsd     1032(%rax,%r8), %xmm13
        movaps    %xmm13, %xmm14
        mulsd     %xmm12, %xmm14
        addsd     %xmm0, %xmm12
        mulsd     %xmm13, %xmm0
        mulsd     1024(%rax,%r8), %xmm12
        addsd     %xmm12, %xmm14
        addsd     %xmm14, %xmm0
        mulsd     -16(%rsp), %xmm0
        mulsd     (%r9), %xmm0
        ret       
..B1.19:
        lea       _large_value_64(%rip), %rdx
        shrl      $31, %eax
        movsd     (%rdx,%rax,8), %xmm0
        mulsd     (%rdx), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sinh,@function
	.size	sinh,.-sinh
	.data
# -- End  sinh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.14:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,16
	.align 16
.L_2il0floatpacket.15:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
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
	.long	0xb9ff12f0,0x3ec71e39
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x111111c1,0x3f811111
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x1940cd88,0x3f2a01a0
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x55555555,0x3fc55555
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
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
