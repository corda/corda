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
	.file "expm1_gen.c"
	.text
..TXTST0:
# -- Begin  expm1
	.text
       .align    16,0x90
	.globl expm1
expm1:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_expm1.1:
..L2:

        lea       _range(%rip), %rsi
        movsd     %xmm0, -24(%rsp)
        movl      -20(%rsp), %edx
        movl      %edx, %eax
        shrl      $31, %edx
        andl      $2147483647, %eax
        cmpl      4(%rsi,%rdx,8), %eax
        jb        ..B1.4
..B1.2:
        jne       ..B1.20
..B1.3:
        movl      -24(%rsp), %ecx
        cmpl      (%rsi,%rdx,8), %ecx
        ja        ..B1.20
..B1.4:
        cmpl      $1064304640, %eax
        jae       ..B1.13
..B1.5:
        cmpl      $1012924416, %eax
        jae       ..B1.12
..B1.6:
        cmpl      $1048576, %eax
        jb        ..B1.8
..B1.7:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        addsd     -24(%rsp), %xmm0
        ret       
..B1.8:
        movl      -24(%rsp), %edx
        orl       %edx, %eax
        je        ..B1.10
..B1.9:
        lea       _small_value_64(%rip), %rax
        movsd     -24(%rsp), %xmm0
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm1
        movsd     %xmm1, -16(%rsp)
        addsd     %xmm1, %xmm0
        ret       
..B1.10:
        movsd     -24(%rsp), %xmm0
..B1.11:
        ret       
..B1.12:
        movsd     -24(%rsp), %xmm0
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movsd     .L_2il0floatpacket.4(%rip), %xmm4
        movsd     .L_2il0floatpacket.6(%rip), %xmm1
        mulsd     %xmm3, %xmm4
        mulsd     %xmm3, %xmm1
        addsd     .L_2il0floatpacket.5(%rip), %xmm4
        addsd     .L_2il0floatpacket.7(%rip), %xmm1
        mulsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm1
        movsd     .L_2il0floatpacket.8(%rip), %xmm2
        addsd     %xmm1, %xmm4
        movsd     %xmm3, -8(%rsp)
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm0
        ret       
..B1.13:
        movsd     -24(%rsp), %xmm1
        lea       _TWO_52H(%rip), %rcx
        movaps    %xmm1, %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     (%rcx), %xmm2
        movsd     .L_2il0floatpacket.1(%rip), %xmm3
        addsd     %xmm2, %xmm0
        movsd     %xmm0, -16(%rsp)
        movsd     -16(%rsp), %xmm5
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        subsd     %xmm2, %xmm5
        mulsd     %xmm5, %xmm3
        mulsd     %xmm4, %xmm5
        subsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm8
        movsd     .L_2il0floatpacket.9(%rip), %xmm6
        subsd     %xmm5, %xmm8
        movsd     .L_2il0floatpacket.3(%rip), %xmm7
        movaps    %xmm8, %xmm9
        movl      -16(%rsp), %esi
        movl      %esi, %edi
        shll      $25, %edi
        addsd     %xmm8, %xmm6
        subsd     %xmm8, %xmm1
        movsd     %xmm6, -8(%rsp)
        subsd     %xmm5, %xmm1
        movl      $0, -8(%rsp)
        addsd     %xmm1, %xmm9
        movsd     -8(%rsp), %xmm6
        sarl      $25, %edi
        addsd     %xmm6, %xmm7
        subl      %edi, %esi
        subsd     %xmm7, %xmm8
        movslq    %edi, %rdi
        addsd     %xmm8, %xmm1
        shrl      $7, %esi
        shlq      $4, %rdi
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r8
        cmpl      $1078204908, -20(%rsp)
        jle       ..B1.15
..B1.14:
        movaps    %xmm9, %xmm3
        lea       SC2_BIAS(%rip), %rax
        movsd     .L_2il0floatpacket.4(%rip), %xmm4
        movsd     .L_2il0floatpacket.6(%rip), %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm2
        mulsd     %xmm9, %xmm3
        mulsd     %xmm3, %xmm4
        mulsd     %xmm3, %xmm0
        addsd     .L_2il0floatpacket.5(%rip), %xmm4
        addsd     .L_2il0floatpacket.7(%rip), %xmm0
        mulsd     %xmm3, %xmm4
        mulsd     %xmm9, %xmm0
        movsd     1032(%r8,%rdi), %xmm5
        addsd     %xmm0, %xmm4
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm3
        movslq    (%rax,%rdx,4), %rcx
        addsd     %xmm3, %xmm4
        addq      %rsi, %rcx
        addsd     %xmm4, %xmm1
        movaps    %xmm1, %xmm0
        addsd     %xmm6, %xmm1
        mulsd     %xmm5, %xmm0
        mulsd     %xmm6, %xmm5
        mulsd     1024(%r8,%rdi), %xmm1
        shlq      $52, %rcx
        addsd     %xmm1, %xmm0
        movq      %rcx, -24(%rsp)
        addsd     %xmm5, %xmm0
        mulsd     -24(%rsp), %xmm0
        lea       _SC2(%rip), %rsi
        mulsd     (%rsi,%rdx,8), %xmm0
        ret       
..B1.15:
        movaps    %xmm9, %xmm3
        movq      $0x3ff0000000000000, %rdx
        mulsd     %xmm9, %xmm3
        addl      $-1078081678, %eax
        movsd     .L_2il0floatpacket.4(%rip), %xmm4
        movsd     .L_2il0floatpacket.6(%rip), %xmm0
        mulsd     %xmm3, %xmm4
        mulsd     %xmm3, %xmm0
        addsd     .L_2il0floatpacket.5(%rip), %xmm4
        addsd     .L_2il0floatpacket.7(%rip), %xmm0
        mulsd     %xmm3, %xmm4
        mulsd     %xmm9, %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm2
        addsd     %xmm0, %xmm4
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm3
        movsd     1032(%r8,%rdi), %xmm5
        addsd     %xmm3, %xmm4
        shlq      $52, %rsi
        addsd     %xmm4, %xmm1
        movaps    %xmm1, %xmm0
        addq      %rdx, %rsi
        mulsd     %xmm5, %xmm0
        addsd     %xmm6, %xmm1
        mulsd     %xmm6, %xmm5
        mulsd     1024(%r8,%rdi), %xmm1
        movq      %rsi, -24(%rsp)
        cmpl      $216675, %eax
        addsd     %xmm1, %xmm0
        movsd     -24(%rsp), %xmm1
        mulsd     %xmm1, %xmm5
        mulsd     %xmm1, %xmm0
        ja        ..B1.17
..B1.16:
        movsd     .L_2il0floatpacket.9(%rip), %xmm1
        subsd     %xmm1, %xmm0
        jmp       ..B1.18
..B1.17:
        movsd     .L_2il0floatpacket.9(%rip), %xmm1
        movaps    %xmm5, %xmm2
        movaps    %xmm1, %xmm3
        subsd     %xmm1, %xmm5
        movsd     %xmm5, -8(%rsp)
        addsd     %xmm5, %xmm3
        subsd     %xmm2, %xmm3
        subsd     %xmm3, %xmm0
..B1.18:
        addsd     %xmm5, %xmm0
..B1.19:
        ret       
..B1.20:
        cmpl      $2146435072, %eax
        jae       ..B1.24
..B1.21:
        testq     %rdx, %rdx
        je        ..B1.28
..B1.22:
        lea       _small_value_64(%rip), %rax
        movsd     .L_2il0floatpacket.3(%rip), %xmm0
        addsd     (%rax), %xmm0
..B1.23:
        ret       
..B1.24:
        addl      $-2146435072, %eax
        orl       -24(%rsp), %eax
        jne       ..B1.26
..B1.25:
        lea       _inf_none(%rip), %rax
        movsd     (%rax,%rdx,8), %xmm0
        ret       
..B1.26:
        movsd     -24(%rsp), %xmm0
..B1.27:
        ret       
..B1.28:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	expm1,@function
	.size	expm1,.-expm1
	.data
# -- End  expm1
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
_range:
	.long	4277811695
	.long	1082535490
	.long	2669343409
	.long	1078159482
	.type	_range,@object
	.size	_range,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
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
	.align 4
_inf_none:
	.long	0
	.long	2146435072
	.long	0
	.long	3220176896
	.type	_inf_none,@object
	.size	_inf_none,16
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
