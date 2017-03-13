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
	.file "exp10_gen.c"
	.text
..TXTST0:
# -- Begin  exp10
	.text
       .align    16,0x90
	.globl exp10
exp10:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_exp10.1:
..L2:

        movsd     %xmm0, -40(%rsp)
        movl      -36(%rsp), %esi
        movl      %esi, %edi
        movl      %esi, %edx
        andl      $2147483647, %edi
        shrl      $31, %edx
        cmpl      $1012924416, %edi
        jb        ..B1.24
..B1.2:
        lea       _range(%rip), %rcx
        cmpl      4(%rcx,%rdx,8), %edi
        jb        ..B1.5
..B1.3:
        jne       ..B1.16
..B1.4:
        movl      -40(%rsp), %eax
        cmpl      (%rcx,%rdx,8), %eax
        ja        ..B1.16
..B1.5:
        addl      $-1072693248, %esi
        cmpl      $4587520, %esi
        ja        ..B1.8
..B1.6:
        lea       _TWO_52H(%rip), %rax
        movsd     -40(%rsp), %xmm0
        movaps    %xmm0, %xmm1
        movsd     (%rax), %xmm2
        addsd     (%rax), %xmm1
        movsd     %xmm1, -32(%rsp)
        movsd     -32(%rsp), %xmm1
        movslq    -32(%rsp), %rcx
        subsd     %xmm2, %xmm1
        ucomisd   %xmm0, %xmm1
        jp        ..B1.7
        je        ..B1.26
..B1.7:
        movsd     %xmm1, -24(%rsp)
        jmp       ..B1.9
..B1.8:
        lea       _TWO_52H(%rip), %rax
        movsd     -40(%rsp), %xmm0
        movsd     (%rax), %xmm2
..B1.9:
        movaps    %xmm0, %xmm1
        lea       _TWO_32H(%rip), %rcx
        mulsd     .L_2il0floatpacket.0(%rip), %xmm1
        lea       SC2_BIAS(%rip), %rdi
        movsd     .L_2il0floatpacket.2(%rip), %xmm3
        addsd     %xmm2, %xmm1
        movsd     %xmm1, -32(%rsp)
        movsd     -32(%rsp), %xmm6
        movsd     (%rcx), %xmm5
        subsd     %xmm2, %xmm6
        movsd     .L_2il0floatpacket.1(%rip), %xmm2
        mulsd     %xmm6, %xmm2
        mulsd     %xmm3, %xmm6
        subsd     %xmm2, %xmm0
        movaps    %xmm0, %xmm4
        movl      -32(%rsp), %eax
        movl      %eax, %r9d
        movsd     .L_2il0floatpacket.4(%rip), %xmm12
        movl      %eax, %esi
        movsd     .L_2il0floatpacket.8(%rip), %xmm2
        subsd     %xmm6, %xmm4
        movsd     .L_2il0floatpacket.3(%rip), %xmm8
        addsd     %xmm5, %xmm4
        movsd     %xmm4, -32(%rsp)
        movsd     -32(%rsp), %xmm9
        movsd     .L_2il0floatpacket.12(%rip), %xmm7
        subsd     %xmm5, %xmm9
        shll      $25, %r9d
        subsd     %xmm9, %xmm0
        sarl      $25, %r9d
        subsd     %xmm6, %xmm0
        movaps    %xmm0, %xmm11
        subl      %r9d, %esi
        mulsd     %xmm8, %xmm0
        addsd     %xmm9, %xmm11
        mulsd     %xmm8, %xmm9
        movaps    %xmm11, %xmm10
        addsd     %xmm9, %xmm7
        mulsd     %xmm11, %xmm10
        mulsd     %xmm10, %xmm12
        mulsd     %xmm10, %xmm2
        addsd     .L_2il0floatpacket.5(%rip), %xmm12
        addsd     .L_2il0floatpacket.9(%rip), %xmm2
        mulsd     %xmm10, %xmm12
        mulsd     %xmm10, %xmm2
        addsd     .L_2il0floatpacket.6(%rip), %xmm12
        addsd     .L_2il0floatpacket.10(%rip), %xmm2
        mulsd     %xmm10, %xmm12
        mulsd     %xmm10, %xmm2
        addsd     %xmm0, %xmm12
        addsd     .L_2il0floatpacket.11(%rip), %xmm2
        movsd     %xmm7, -24(%rsp)
        movl      $0, -24(%rsp)
        movsd     -24(%rsp), %xmm1
        movsd     .L_2il0floatpacket.7(%rip), %xmm0
        mulsd     %xmm11, %xmm2
        addsd     %xmm1, %xmm0
        movslq    %r9d, %r9
        subsd     %xmm0, %xmm9
        shlq      $4, %r9
        addsd     %xmm9, %xmm12
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r10
        addsd     %xmm12, %xmm2
        shrl      $7, %esi
        movsd     1032(%r10,%r9), %xmm4
        movslq    (%rdi,%rdx,4), %r8
        movaps    %xmm4, %xmm0
        addq      %rsi, %r8
        shlq      $52, %r8
        movq      %r8, -16(%rsp)
        cmpl      $-130815, %eax
        movsd     1024(%r10,%r9), %xmm3
        mulsd     %xmm2, %xmm0
        mulsd     %xmm1, %xmm4
        jle       ..B1.11
..B1.10:
        lea       _SC2(%rip), %rax
        addsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm0
        addsd     %xmm4, %xmm0
        mulsd     -16(%rsp), %xmm0
        mulsd     (%rax,%rdx,8), %xmm0
        movsd     %xmm0, -40(%rsp)
        ret       
..B1.11:
        lea       _SC2(%rip), %rax
        addsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm3
        movaps    %xmm4, %xmm1
        movsd     (%rax,%rdx,8), %xmm2
        addsd     %xmm3, %xmm0
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -24(%rsp)
        movl      $0, -24(%rsp)
        movsd     -24(%rsp), %xmm3
        movsd     -16(%rsp), %xmm1
        subsd     %xmm3, %xmm4
        addsd     %xmm0, %xmm4
        movaps    %xmm3, %xmm0
        addsd     %xmm4, %xmm0
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm0
        movsd     %xmm0, -40(%rsp)
        movl      -36(%rsp), %eax
        cmpl      $1048576, %eax
        jge       ..B1.15
..B1.12:
        testl     %eax, %eax
        jle       ..B1.14
..B1.13:
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm3
        mulsd     %xmm2, %xmm4
        addsd     %xmm4, %xmm3
        movsd     %xmm3, -40(%rsp)
..B1.14:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -40(%rsp), %xmm0
..B1.15:
        ret       
..B1.16:
        cmpl      $2146435072, %edi
        jae       ..B1.20
..B1.17:
        testl     $-2147483648, %esi
        je        ..B1.25
..B1.18:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -40(%rsp)
..B1.19:
        ret       
..B1.20:
        addl      $-2146435072, %edi
        orl       -40(%rsp), %edi
        jne       ..B1.22
..B1.21:
        lea       _inf_zero(%rip), %rax
        movsd     (%rax,%rdx,8), %xmm0
        ret       
..B1.22:
        movsd     -40(%rsp), %xmm0
        addsd     %xmm0, %xmm0
..B1.23:
        ret       
..B1.24:
        movsd     .L_2il0floatpacket.12(%rip), %xmm0
        addsd     -40(%rsp), %xmm0
        ret       
..B1.25:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -40(%rsp)
        ret       
..B1.26:
        lea       exact_values(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	exp10,@function
	.size	exp10,.-exp10
	.data
# -- End  exp10
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x0979a371,0x407a934f
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3f634413
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x7fbcc47c,0x3e0427de
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x40026bb1
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xd9d41e9c,0x3fca7ed8
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x09fd8b95,0x3ff2bd76
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xc73cea69,0x40053524
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x25f8c2cc,0x3fb16e4f
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0xfd1d41fe,0x3fe1429f
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x91de2ca4,0x40004705
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0xa2b05ba9,0x3eb776aa
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
exact_values:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x40240000
	.long	0x00000000,0x40590000
	.long	0x00000000,0x408f4000
	.long	0x00000000,0x40c38800
	.long	0x00000000,0x40f86a00
	.long	0x00000000,0x412e8480
	.long	0x00000000,0x416312d0
	.long	0x00000000,0x4197d784
	.long	0x00000000,0x41cdcd65
	.long	0x20000000,0x4202a05f
	.long	0xe8000000,0x42374876
	.long	0xa2000000,0x426d1a94
	.long	0xe5400000,0x42a2309c
	.long	0x1e900000,0x42d6bcc4
	.long	0x26340000,0x430c6bf5
	.long	0x37e08000,0x4341c379
	.long	0x85d8a000,0x43763457
	.long	0x674ec800,0x43abc16d
	.long	0x60913d00,0x43e158e4
	.long	0x78b58c40,0x4415af1d
	.long	0xd6e2ef50,0x444b1ae4
	.long	0x064dd592,0x4480f0cf
	.type	exact_values,@object
	.size	exact_values,184
	.align 4
_range:
	.long	1352628734
	.long	1081295891
	.long	1189309266
	.long	1081358775
	.type	_range,@object
	.size	_range,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
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
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_inf_zero:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zero,@object
	.size	_inf_zero,16
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

	.globl pow10
	.equ pow10, exp10

