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
	.file "sinhf_gen.c"
	.text
..TXTST0:
# -- Begin  sinhf
	.text
       .align    16,0x90
	.globl sinhf
sinhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_sinhf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1107296256, %eax
        jae       ..B1.14
..B1.2:
        cmpl      $1056964608, %eax
        jae       ..B1.13
..B1.3:
        cmpl      $1031798784, %eax
        jae       ..B1.12
..B1.4:
        cmpl      $838860800, %eax
        jae       ..B1.11
..B1.5:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jb        ..B1.7
..B1.6:
        movss     .L_2il0floatpacket.16(%rip), %xmm1
        addss     %xmm0, %xmm1
        movss     %xmm1, -16(%rsp)
        ret       
..B1.7:
        testl     %eax, %eax
        je        ..B1.9
..B1.8:
        lea       _small_value_32(%rip), %rax
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.15(%rip), %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm0
        addss     -8(%rsp), %xmm0
        ret       
..B1.9:
        movss     -8(%rsp), %xmm0
..B1.10:
        ret       
..B1.11:
        pxor      %xmm1, %xmm1
        cvtss2sd  -8(%rsp), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.13(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.14(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        mulsd     %xmm1, %xmm2
        addsd     %xmm1, %xmm2
        cvtsd2ss  %xmm2, %xmm2
        movaps    %xmm2, %xmm0
        ret       
..B1.12:
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm4
        movsd     .L_2il0floatpacket.10(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.9(%rip), %xmm4
        addsd     .L_2il0floatpacket.11(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        movaps    %xmm4, %xmm0
        ret       
..B1.13:
        pxor      %xmm3, %xmm3
        andl      $-2147483648, %edx
        cvtss2sd  -8(%rsp), %xmm3
        andps     .L_2il0floatpacket.17(%rip), %xmm3
        pxor      %xmm4, %xmm4
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm6, %xmm6
        mulsd     %xmm3, %xmm0
        movsd     .L_2il0floatpacket.4(%rip), %xmm7
        movsd     .L_2il0floatpacket.6(%rip), %xmm5
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movl      -16(%rsp), %esi
        movl      %esi, %eax
        shll      $26, %eax
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        mulsd     .L_2il0floatpacket.3(%rip), %xmm1
        sarl      $26, %eax
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm7
        mulsd     %xmm2, %xmm5
        addsd     .L_2il0floatpacket.5(%rip), %xmm7
        addsd     .L_2il0floatpacket.7(%rip), %xmm5
        mulsd     %xmm2, %xmm7
        mulsd     %xmm3, %xmm5
        addsd     .L_2il0floatpacket.18(%rip), %xmm7
        movslq    %eax, %rax
        movaps    %xmm7, %xmm0
        subl      %eax, %esi
        addsd     %xmm5, %xmm0
        subsd     %xmm5, %xmm7
        shrl      $6, %esi
        shll      $23, %esi
        shlq      $3, %rax
        lea       1056964608(%rsi), %ecx
        negl      %esi
        addl      $1056964608, %esi
        orl       %edx, %ecx
        orl       %esi, %edx
        movl      %edx, -20(%rsp)
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rdx
        movl      %ecx, -24(%rsp)
        cvtss2sd  -24(%rsp), %xmm4
        cvtss2sd  -20(%rsp), %xmm6
        mulsd     256(%rdx,%rax), %xmm0
        negq      %rax
        mulsd     %xmm4, %xmm0
        mulsd     256(%rdx,%rax), %xmm7
        mulsd     %xmm6, %xmm7
        subsd     %xmm7, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.14:
        cmpl      $1119016188, %eax
        jbe       ..B1.18
..B1.15:
        cmpl      $2139095040, %eax
        jb        ..B1.19
..B1.16:
        movss     -8(%rsp), %xmm0
        addss     %xmm0, %xmm0
..B1.17:
        ret       
..B1.18:
        pxor      %xmm3, %xmm3
        andl      $-2147483648, %edx
        cvtss2sd  -8(%rsp), %xmm3
        andps     .L_2il0floatpacket.17(%rip), %xmm3
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        mulsd     %xmm3, %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movl      $0, -24(%rsp)
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        movl      -16(%rsp), %eax
        movl      %eax, %ecx
        shll      $26, %ecx
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        mulsd     .L_2il0floatpacket.3(%rip), %xmm1
        sarl      $26, %ecx
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        subl      %ecx, %eax
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm4
        shrl      $6, %eax
        addsd     %xmm4, %xmm0
        shll      $20, %eax
        addsd     .L_2il0floatpacket.18(%rip), %xmm0
        addl      $1071644672, %eax
        movslq    %ecx, %rcx
        orl       %eax, %edx
        movl      %edx, -20(%rsp)
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rdx
        mulsd     256(%rdx,%rcx,8), %xmm0
        mulsd     -24(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.19:
        lea       _large_value_32(%rip), %rax
        shrl      $31, %edx
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.12(%rip), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sinhf,@function
	.size	sinhf,.-sinhf
	.data
# -- End  sinhf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.17:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x652b82fe,0x40571547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x41680000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.3:
	.long	0xfefa39ef,0xbf862e42
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xeb78fa85,0x3fa56420
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x008d6118,0x3fe00000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xda752d4f,0x3fc55550
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0xffffe7c6,0x3fefffff
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x4bc96d7d,0x3ec749d8
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x326ed15a,0x3f811111
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0xd09557cf,0x3f2a0168
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x552ed1e2,0x3fc55555
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x1fc8e65a,0x3f8111c7
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x4fa4d5d9,0x3fc55555
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.18:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,8
	.align 4
.L_2il0floatpacket.2:
	.long	0x4b400000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.12:
	.long	0x71800000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.align 4
.L_2il0floatpacket.15:
	.long	0x0d800000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,4
	.align 4
.L_2il0floatpacket.16:
	.long	0x3f800000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
