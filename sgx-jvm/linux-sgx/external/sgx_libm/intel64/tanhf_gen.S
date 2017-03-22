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
	.file "tanhf_gen.c"
	.text
..TXTST0:
# -- Begin  tanhf
	.text
       .align    16,0x90
	.globl tanhf
tanhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_tanhf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1098907648, %eax
        jae       ..B1.15
..B1.2:
        cmpl      $1065353216, %eax
        jae       ..B1.14
..B1.3:
        cmpl      $1048576000, %eax
        jae       ..B1.12
..B1.4:
        cmpl      $838860800, %eax
        jae       ..B1.11
..B1.5:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jb        ..B1.7
..B1.6:
        movl      %edx, %edx
        lea       _small_value_32(%rip), %rax
        movss     (%rax,%rdx,4), %xmm1
        mulss     .L_2il0floatpacket.28(%rip), %xmm1
        subss     %xmm1, %xmm0
        ret       
..B1.7:
        testl     %eax, %eax
        je        ..B1.9
..B1.8:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        cvtss2sd  -8(%rsp), %xmm2
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.28(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        subsd     %xmm0, %xmm2
        pxor      %xmm0, %xmm0
        movss     %xmm1, -16(%rsp)
        cvtsd2ss  %xmm2, %xmm0
        ret       
..B1.9:
        movss     -8(%rsp), %xmm0
..B1.10:
        ret       
..B1.11:
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.22(%rip), %xmm4
        movsd     .L_2il0floatpacket.25(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.23(%rip), %xmm4
        addsd     .L_2il0floatpacket.26(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.24(%rip), %xmm4
        addsd     .L_2il0floatpacket.27(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
        jmp       ..B1.13
..B1.12:
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.12(%rip), %xmm4
        movsd     .L_2il0floatpacket.17(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.13(%rip), %xmm4
        addsd     .L_2il0floatpacket.18(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.14(%rip), %xmm4
        addsd     .L_2il0floatpacket.19(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.15(%rip), %xmm4
        addsd     .L_2il0floatpacket.20(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.16(%rip), %xmm4
        addsd     .L_2il0floatpacket.21(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
..B1.13:
        cvtsd2ss  %xmm4, %xmm4
        movaps    %xmm4, %xmm0
        ret       
..B1.14:
        pxor      %xmm5, %xmm5
        movq      $0x3ff0000000000000, %rax
        cvtss2sd  -8(%rsp), %xmm5
        andps     .L_2il0floatpacket.29(%rip), %xmm5
        mulsd     .L_2il0floatpacket.0(%rip), %xmm5
        movsd     .L_2il0floatpacket.1(%rip), %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm7
        addsd     %xmm5, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm8
        movsd     .L_2il0floatpacket.6(%rip), %xmm2
        movsd     .L_2il0floatpacket.10(%rip), %xmm6
        movsd     .L_2il0floatpacket.30(%rip), %xmm9
        movl      -16(%rsp), %edx
        shlq      $52, %rdx
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        addq      %rax, %rdx
        subsd     %xmm1, %xmm5
        movaps    %xmm5, %xmm3
        mulsd     %xmm5, %xmm3
        movaps    %xmm3, %xmm4
        mulsd     %xmm3, %xmm4
        mulsd     %xmm4, %xmm7
        mulsd     %xmm4, %xmm8
        mulsd     %xmm4, %xmm2
        addsd     .L_2il0floatpacket.9(%rip), %xmm7
        mulsd     %xmm4, %xmm6
        addsd     .L_2il0floatpacket.5(%rip), %xmm8
        addsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm3, %xmm7
        addsd     .L_2il0floatpacket.11(%rip), %xmm6
        mulsd     %xmm4, %xmm8
        mulsd     %xmm3, %xmm2
        mulsd     %xmm5, %xmm7
        mulsd     %xmm5, %xmm6
        addsd     %xmm2, %xmm8
        addsd     %xmm6, %xmm7
        movq      %rdx, -24(%rsp)
        addsd     %xmm7, %xmm8
        movsd     .L_2il0floatpacket.3(%rip), %xmm10
        addsd     %xmm9, %xmm8
        mulsd     -24(%rsp), %xmm8
        testl     $-2147483648, -8(%rsp)
        addsd     %xmm9, %xmm8
        divsd     %xmm8, %xmm10
        subsd     %xmm10, %xmm9
        movaps    %xmm9, %xmm0
        xorps     .L_2il0floatpacket.31(%rip), %xmm0
        jne       ..L3
        movaps    %xmm9, %xmm0
..L3:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.15:
        shrl      $31, %edx
        cmpl      $2139095040, %eax
        jb        ..B1.20
..B1.16:
        jne       ..B1.18
..B1.17:
        movl      %edx, %edx
        lea       ones(%rip), %rax
        movss     (%rax,%rdx,4), %xmm0
        ret       
..B1.18:
        movss     -8(%rsp), %xmm0
..B1.19:
        ret       
..B1.20:
        lea       ones(%rip), %rax
        lea       _small_value_32(%rip), %rcx
        movss     (%rax,%rdx,4), %xmm0
        subss     (%rcx,%rdx,4), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	tanhf,@function
	.size	tanhf,.-tanhf
	.data
# -- End  tanhf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.29:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.29,@object
	.size	.L_2il0floatpacket.29,16
	.align 16
.L_2il0floatpacket.31:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.31,@object
	.size	.L_2il0floatpacket.31,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x652b82fe,0x40071547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x41600000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x55f6ce44,0x3eb67839
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x17eb7c1b,0x3f83b2ac
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x5a9238ae,0x3f24300f
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0xfe8df8b5,0x3fcebfbd
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x97a846d7,0x3ef0355a
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0xb399a1da,0x3fac6b09
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x2d719ac7,0x3f55d7f6
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0xfe8bdf78,0x3fe62e42
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x8d21df9d,0x3eed02fa
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x4a9bd1ce,0x3f3d92d7
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x4b00cfe8,0x3f6d0411
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.15:
	.long	0x616f3508,0x3f96640f
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,8
	.align 8
.L_2il0floatpacket.16:
	.long	0xd4e5c3b2,0x3fc11110
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,8
	.align 8
.L_2il0floatpacket.17:
	.long	0x112b299f,0xbf1cd749
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,8
	.align 8
.L_2il0floatpacket.18:
	.long	0x6388aa74,0xbf563031
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,8
	.align 8
.L_2il0floatpacket.19:
	.long	0xd81e0536,0xbf821e4b
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,8
	.align 8
.L_2il0floatpacket.20:
	.long	0xdc3e3757,0xbfaba1ab
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,8
	.align 8
.L_2il0floatpacket.21:
	.long	0x547e4ef2,0xbfd55555
	.type	.L_2il0floatpacket.21,@object
	.size	.L_2il0floatpacket.21,8
	.align 8
.L_2il0floatpacket.22:
	.long	0x7be81b29,0x3f6ab4a5
	.type	.L_2il0floatpacket.22,@object
	.size	.L_2il0floatpacket.22,8
	.align 8
.L_2il0floatpacket.23:
	.long	0x9dbd8fad,0x3f966488
	.type	.L_2il0floatpacket.23,@object
	.size	.L_2il0floatpacket.23,8
	.align 8
.L_2il0floatpacket.24:
	.long	0x0f94d287,0x3fc11111
	.type	.L_2il0floatpacket.24,@object
	.size	.L_2il0floatpacket.24,8
	.align 8
.L_2il0floatpacket.25:
	.long	0xa5e0a14a,0xbf821629
	.type	.L_2il0floatpacket.25,@object
	.size	.L_2il0floatpacket.25,8
	.align 8
.L_2il0floatpacket.26:
	.long	0x983e34ea,0xbfaba1b8
	.type	.L_2il0floatpacket.26,@object
	.size	.L_2il0floatpacket.26,8
	.align 8
.L_2il0floatpacket.27:
	.long	0x5553e1cf,0xbfd55555
	.type	.L_2il0floatpacket.27,@object
	.size	.L_2il0floatpacket.27,8
	.align 8
.L_2il0floatpacket.30:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.30,@object
	.size	.L_2il0floatpacket.30,8
	.align 4
.L_2il0floatpacket.2:
	.long	0x4b000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.28:
	.long	0x0d800000
	.type	.L_2il0floatpacket.28,@object
	.size	.L_2il0floatpacket.28,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
