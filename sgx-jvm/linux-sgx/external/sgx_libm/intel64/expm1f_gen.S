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
	.file "expm1f_gen.c"
	.text
..TXTST0:
# -- Begin  expm1f
	.text
       .align    16,0x90
	.globl expm1f
expm1f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_expm1f.1:
..L2:

        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $1118652779, %ecx
        jae       ..B1.12
..B1.2:
        cmpl      $1040187392, %ecx
        jae       ..B1.11
..B1.3:
        cmpl      $838860800, %ecx
        jae       ..B1.10
..B1.4:
        cmpl      $8388608, %ecx
        jb        ..B1.6
..B1.5:
        movss     .L_2il0floatpacket.15(%rip), %xmm1
        mulss     %xmm1, %xmm1
        addss     %xmm1, %xmm0
        ret       
..B1.6:
        testl     %ecx, %ecx
        je        ..B1.8
..B1.7:
        movss     .L_2il0floatpacket.15(%rip), %xmm0
        pxor      %xmm2, %xmm2
        mulss     %xmm0, %xmm0
        pxor      %xmm1, %xmm1
        cvtss2sd  -8(%rsp), %xmm2
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        addsd     %xmm0, %xmm2
        pxor      %xmm0, %xmm0
        movss     %xmm1, -16(%rsp)
        cvtsd2ss  %xmm2, %xmm0
        ret       
..B1.8:
        movss     -8(%rsp), %xmm0
..B1.9:
        ret       
..B1.10:
        movss     -8(%rsp), %xmm1
        movaps    %xmm1, %xmm0
        mulss     %xmm1, %xmm0
        cvtss2sd  %xmm1, %xmm1
        cvtss2sd  %xmm0, %xmm0
        movsd     .L_2il0floatpacket.9(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        movsd     .L_2il0floatpacket.12(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.10(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.13(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.11(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.14(%rip), %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     %xmm2, %xmm3
        addsd     %xmm1, %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movaps    %xmm3, %xmm0
        ret       
..B1.11:
        pxor      %xmm3, %xmm3
        pxor      %xmm6, %xmm6
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rcx
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        movl      -16(%rsp), %edx
        movl      %edx, %eax
        shll      $26, %eax
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        mulsd     .L_2il0floatpacket.3(%rip), %xmm1
        sarl      $26, %eax
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm4
        movslq    %eax, %rax
        addsd     %xmm4, %xmm0
        movsd     256(%rcx,%rax,8), %xmm5
        subl      %eax, %edx
        mulsd     %xmm5, %xmm0
        shrl      $6, %edx
        addsd     %xmm5, %xmm0
        shll      $23, %edx
        addl      $1065353216, %edx
        movl      %edx, -24(%rsp)
        cvtss2sd  -24(%rsp), %xmm6
        mulsd     %xmm6, %xmm0
        addsd     .L_2il0floatpacket.8(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.12:
        lea       range(%rip), %rdx
        shrl      $31, %eax
        cmpl      (%rdx,%rax,4), %ecx
        jb        ..B1.21
..B1.13:
        cmpl      $2139095040, %ecx
        jae       ..B1.17
..B1.14:
        testq     %rax, %rax
        je        ..B1.22
..B1.15:
        movss     .L_2il0floatpacket.16(%rip), %xmm0
..B1.16:
        ret       
..B1.17:
        jne       ..B1.19
..B1.18:
        lea       _inf_none(%rip), %rdx
        movss     (%rdx,%rax,4), %xmm0
        ret       
..B1.19:
        movss     -8(%rsp), %xmm0
..B1.20:
        ret       
..B1.21:
        pxor      %xmm3, %xmm3
        movq      $0x3ff0000000000000, %rcx
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rsi
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        movl      -16(%rsp), %edx
        andl      $4194303, %edx
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        mulsd     .L_2il0floatpacket.3(%rip), %xmm1
        movl      %edx, %eax
        shll      $26, %eax
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm4
        sarl      $26, %eax
        addsd     %xmm4, %xmm0
        movslq    %eax, %rax
        subl      %eax, %edx
        shrl      $6, %edx
        movsd     256(%rsi,%rax,8), %xmm5
        mulsd     %xmm5, %xmm0
        shlq      $52, %rdx
        addsd     %xmm5, %xmm0
        addq      %rcx, %rdx
        movq      %rdx, -24(%rsp)
        mulsd     -24(%rsp), %xmm0
        addsd     .L_2il0floatpacket.8(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.22:
        movss     .L_2il0floatpacket.17(%rip), %xmm0
        mulss     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	expm1f,@function
	.size	expm1f,.-expm1f
	.data
# -- End  expm1f
	.section .rodata, "a"
	.align 8
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
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0xd2809cf8,0x3f2a04e1
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0xeb5ecfe9,0x3f811110
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x55579a3a,0x3fc55555
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x9843cb2c,0x3f56c445
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x27a9b288,0x3fa55555
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x00016df6,0x3fe00000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 4
.L_2il0floatpacket.2:
	.long	0x4b400000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.15:
	.long	0x0d800000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,4
	.align 4
.L_2il0floatpacket.16:
	.long	0xbf800000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,4
	.align 4
.L_2il0floatpacket.17:
	.long	0x71800000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,4
	.align 4
range:
	.long	1118925336
	.long	1107296256
	.type	range,@object
	.size	range,8
	.align 4
_inf_none:
	.long	2139095040
	.long	3212836864
	.type	_inf_none,@object
	.size	_inf_none,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
