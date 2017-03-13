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
	.file "asinf_gen.c"
	.text
..TXTST0:
# -- Begin  asinf
	.text
       .align    16,0x90
	.globl asinf
asinf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_asinf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -16(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1065353216, %eax
        jae       ..B1.14
..B1.2:
        pxor      %xmm5, %xmm5
        cmpl      $1056964608, %eax
        cvtss2sd  %xmm0, %xmm5
        jae       ..B1.11
..B1.3:
        cmpl      $947912704, %eax
        jae       ..B1.10
..B1.4:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jb        ..B1.6
..B1.5:
        movl      %edx, %edx
        lea       _small_value_32(%rip), %rax
        movss     (%rax,%rdx,4), %xmm1
        mulss     .L_2il0floatpacket.26(%rip), %xmm1
        addss     %xmm1, %xmm0
        ret       
..B1.6:
        testl     %eax, %eax
        je        ..B1.9
..B1.7:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        cvtss2sd  -16(%rsp), %xmm2
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.26(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        addsd     %xmm2, %xmm0
        movss     %xmm1, -24(%rsp)
        cvtsd2ss  %xmm0, %xmm0
..B1.9:
        ret       
..B1.10:
        movaps    %xmm5, %xmm1
        mulsd     %xmm5, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        movsd     .L_2il0floatpacket.4(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.1(%rip), %xmm3
        addsd     .L_2il0floatpacket.5(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.2(%rip), %xmm3
        addsd     .L_2il0floatpacket.6(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.3(%rip), %xmm3
        addsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm1, %xmm2
        pxor      %xmm0, %xmm0
        addsd     %xmm2, %xmm3
        mulsd     %xmm5, %xmm3
        addsd     %xmm3, %xmm5
        cvtsd2ss  %xmm5, %xmm0
        ret       
..B1.11:
        cmpl      $1061997772, %eax
        jb        ..B1.13
..B1.12:
        andps     .L_2il0floatpacket.28(%rip), %xmm5
        pxor      %xmm4, %xmm4
        movsd     .L_2il0floatpacket.27(%rip), %xmm1
        testl     %edx, %edx
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        subsd     %xmm5, %xmm1
        mulsd     .L_2il0floatpacket.22(%rip), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        sqrtsd    %xmm1, %xmm4
        mulsd     %xmm0, %xmm3
        movsd     .L_2il0floatpacket.4(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.1(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.5(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.2(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.6(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.3(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm3
        mulsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm4
        mulsd     .L_2il0floatpacket.24(%rip), %xmm4
        addsd     .L_2il0floatpacket.23(%rip), %xmm4
        movaps    %xmm4, %xmm0
        xorps     .L_2il0floatpacket.29(%rip), %xmm0
        jl        ..L3
        movaps    %xmm4, %xmm0
..L3:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.13:
        movaps    %xmm5, %xmm1
        mulsd     %xmm5, %xmm1
        movsd     .L_2il0floatpacket.9(%rip), %xmm4
        movsd     .L_2il0floatpacket.15(%rip), %xmm3
        addsd     .L_2il0floatpacket.8(%rip), %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.10(%rip), %xmm4
        addsd     .L_2il0floatpacket.16(%rip), %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.11(%rip), %xmm4
        addsd     .L_2il0floatpacket.17(%rip), %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.12(%rip), %xmm4
        addsd     .L_2il0floatpacket.18(%rip), %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.13(%rip), %xmm4
        addsd     .L_2il0floatpacket.19(%rip), %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.14(%rip), %xmm4
        addsd     .L_2il0floatpacket.20(%rip), %xmm3
        mulsd     %xmm1, %xmm4
        addsd     %xmm3, %xmm4
        addsd     .L_2il0floatpacket.21(%rip), %xmm4
        mulsd     %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        cvtsd2ss  %xmm5, %xmm5
        movaps    %xmm5, %xmm0
        ret       
..B1.14:
        cmpl      $2139095040, %eax
        ja        ..B1.18
..B1.15:
        cmpl      $1065353216, %eax
        jne       ..B1.19
..B1.16:
        lea       pi_2f(%rip), %rax
        lea       _small_value_32(%rip), %rcx
        shrl      $31, %edx
        movss     (%rax,%rdx,4), %xmm0
        subss     (%rcx,%rdx,4), %xmm0
..B1.17:
        ret       
..B1.18:
        movss     -16(%rsp), %xmm0
        ret       
..B1.19:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.25(%rip), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	asinf,@function
	.size	asinf,.-asinf
	.data
# -- End  asinf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.28:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.28,@object
	.size	.L_2il0floatpacket.28,16
	.align 16
.L_2il0floatpacket.29:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.29,@object
	.size	.L_2il0floatpacket.29,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x32950b15,0x3fa10044
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x0f078e5a,0x3f9547bd
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x282596ea,0x3f9f2932
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x3b07aed0,0x3fb33334
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x15b528b3,0x3f5b565b
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x1acf0c57,0x3f965bd0
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x8a6efcbd,0x3fa6db1b
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x541021e2,0x3fc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0xbfd00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x7c6f097b,0x4014f3d9
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x617c2eba,0x4018ed42
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0xb9e8c253,0x3fea66bf
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x7e9f31d2,0x3fb9a89d
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x74fb38bf,0x3fb8e90d
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0xe950ab19,0x3fcb8550
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.15:
	.long	0x8c8953a2,0xc0202794
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,8
	.align 8
.L_2il0floatpacket.16:
	.long	0x94b6927c,0xc0047249
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,8
	.align 8
.L_2il0floatpacket.17:
	.long	0x3a6dbb00,0xbfa654a1
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,8
	.align 8
.L_2il0floatpacket.18:
	.long	0x020f8f24,0x3fb60e1d
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,8
	.align 8
.L_2il0floatpacket.19:
	.long	0x726ca18f,0x3fbff1ac
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,8
	.align 8
.L_2il0floatpacket.20:
	.long	0xc94e1724,0x3f352382
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,8
	.align 8
.L_2il0floatpacket.21:
	.long	0x00000000,0x3fa80000
	.type	.L_2il0floatpacket.21,@object
	.size	.L_2il0floatpacket.21,8
	.align 8
.L_2il0floatpacket.22:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.22,@object
	.size	.L_2il0floatpacket.22,8
	.align 8
.L_2il0floatpacket.23:
	.long	0x54442d18,0x3ff921fb
	.type	.L_2il0floatpacket.23,@object
	.size	.L_2il0floatpacket.23,8
	.align 8
.L_2il0floatpacket.24:
	.long	0x00000000,0xc0000000
	.type	.L_2il0floatpacket.24,@object
	.size	.L_2il0floatpacket.24,8
	.align 8
.L_2il0floatpacket.27:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.27,@object
	.size	.L_2il0floatpacket.27,8
	.align 4
.L_2il0floatpacket.25:
	.long	0x7f800000
	.type	.L_2il0floatpacket.25,@object
	.size	.L_2il0floatpacket.25,4
	.align 4
.L_2il0floatpacket.26:
	.long	0x0d800000
	.type	.L_2il0floatpacket.26,@object
	.size	.L_2il0floatpacket.26,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
pi_2f:
	.long	0x3fc90fdb
	.long	0xbfc90fdb
	.type	pi_2f,@object
	.size	pi_2f,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
