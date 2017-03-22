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
	.file "acosf_gen.c"
	.text
..TXTST0:
# -- Begin  acosf
	.text
       .align    16,0x90
	.globl acosf
acosf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_acosf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1065353216, %eax
        jae       ..B1.10
..B1.2:
        cmpl      $1056964608, %eax
        jae       ..B1.6
..B1.3:
        cmpl      $838860800, %eax
        jae       ..B1.5
..B1.4:
        cvtss2sd  %xmm0, %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm1
        subsd     %xmm0, %xmm1
        cvtsd2ss  %xmm1, %xmm1
        movaps    %xmm1, %xmm0
        ret       
..B1.5:
        pxor      %xmm5, %xmm5
        cvtss2sd  -8(%rsp), %xmm5
        movaps    %xmm5, %xmm1
        mulsd     %xmm5, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.0(%rip), %xmm4
        movsd     .L_2il0floatpacket.4(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.1(%rip), %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.2(%rip), %xmm4
        addsd     .L_2il0floatpacket.6(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.3(%rip), %xmm4
        addsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        movsd     .L_2il0floatpacket.8(%rip), %xmm3
        addsd     %xmm2, %xmm4
        subsd     %xmm5, %xmm3
        mulsd     %xmm4, %xmm5
        subsd     %xmm5, %xmm3
        jmp       ..B1.9
..B1.6:
        pxor      %xmm3, %xmm3
        testl     $-2147483648, %edx
        cvtss2sd  -8(%rsp), %xmm3
        movsd     .L_2il0floatpacket.1(%rip), %xmm6
        movsd     .L_2il0floatpacket.2(%rip), %xmm9
        movsd     .L_2il0floatpacket.3(%rip), %xmm10
        movsd     .L_2il0floatpacket.5(%rip), %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm1
        movsd     .L_2il0floatpacket.7(%rip), %xmm2
        je        ..B1.8
..B1.7:
        movsd     .L_2il0floatpacket.13(%rip), %xmm5
        pxor      %xmm8, %xmm8
        movsd     .L_2il0floatpacket.0(%rip), %xmm7
        addsd     %xmm3, %xmm5
        mulsd     .L_2il0floatpacket.9(%rip), %xmm5
        movaps    %xmm5, %xmm4
        mulsd     %xmm5, %xmm4
        sqrtsd    %xmm5, %xmm8
        mulsd     %xmm4, %xmm7
        mulsd     .L_2il0floatpacket.10(%rip), %xmm8
        addsd     %xmm6, %xmm7
        movsd     .L_2il0floatpacket.4(%rip), %xmm6
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm0, %xmm6
        addsd     %xmm9, %xmm7
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm1, %xmm6
        addsd     %xmm10, %xmm7
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm2, %xmm6
        mulsd     %xmm5, %xmm6
        movsd     .L_2il0floatpacket.11(%rip), %xmm3
        addsd     %xmm6, %xmm7
        subsd     %xmm8, %xmm3
        mulsd     %xmm7, %xmm8
        subsd     %xmm8, %xmm3
        jmp       ..B1.9
..B1.8:
        movsd     .L_2il0floatpacket.13(%rip), %xmm5
        movsd     .L_2il0floatpacket.0(%rip), %xmm7
        subsd     %xmm3, %xmm5
        mulsd     .L_2il0floatpacket.9(%rip), %xmm5
        pxor      %xmm3, %xmm3
        movaps    %xmm5, %xmm4
        mulsd     %xmm5, %xmm4
        sqrtsd    %xmm5, %xmm3
        mulsd     %xmm4, %xmm7
        mulsd     .L_2il0floatpacket.10(%rip), %xmm3
        addsd     %xmm6, %xmm7
        movsd     .L_2il0floatpacket.4(%rip), %xmm6
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm0, %xmm6
        addsd     %xmm9, %xmm7
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm1, %xmm6
        addsd     %xmm10, %xmm7
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm7
        addsd     %xmm2, %xmm6
        mulsd     %xmm5, %xmm6
        addsd     %xmm6, %xmm7
        mulsd     %xmm3, %xmm7
        addsd     %xmm7, %xmm3
..B1.9:
        cvtsd2ss  %xmm3, %xmm3
        movaps    %xmm3, %xmm0
        ret       
..B1.10:
        cmpl      $2139095040, %eax
        ja        ..B1.16
..B1.11:
        cmpl      $1065353216, %eax
        jne       ..B1.17
..B1.12:
        testl     $-2147483648, %edx
        je        ..B1.14
..B1.13:
        lea       _small_value_64(%rip), %rax
        movsd     .L_2il0floatpacket.11(%rip), %xmm0
        addsd     (%rax), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.14:
        pxor      %xmm0, %xmm0
..B1.15:
        ret       
..B1.16:
        movss     -8(%rsp), %xmm0
        ret       
..B1.17:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.12(%rip), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	acosf,@function
	.size	acosf,.-acosf
	.data
# -- End  acosf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0xa6220783,0x3f9e3be5
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0xb5dc5888,0x3f93d64d
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x5290ac8c,0x3f9f20d0
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x4fa3bc7b,0x3fb33333
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xa9336e0b,0x3f758206
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0xecc3fecb,0x3f96a6f2
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x9e7be0cc,0x3fa6db5b
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x554dfea4,0x3fc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x54442d18,0x3ff921fb
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x54442d18,0x400921fb
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 4
.L_2il0floatpacket.12:
	.long	0x7f800000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
