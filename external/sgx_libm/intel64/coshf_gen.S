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
	.file "coshf_gen.c"
	.text
..TXTST0:
# -- Begin  coshf
	.text
       .align    16,0x90
	.globl coshf
coshf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_coshf.1:
..L2:

        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        andl      $2147483647, %eax
        cmpl      $1107296256, %eax
        jae       ..B1.10
..B1.2:
        cmpl      $1056964608, %eax
        jae       ..B1.9
..B1.3:
        cmpl      $1031798784, %eax
        jae       ..B1.8
..B1.4:
        cmpl      $838860800, %eax
        jae       ..B1.6
..B1.5:
        andps     .L_2il0floatpacket.15(%rip), %xmm0
        addss     .L_2il0floatpacket.16(%rip), %xmm0
        movss     %xmm0, -16(%rsp)
        ret       
..B1.6:
        pxor      %xmm0, %xmm0
        cvtss2sd  -8(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     .L_2il0floatpacket.13(%rip), %xmm1
        mulsd     %xmm0, %xmm1
        addsd     .L_2il0floatpacket.14(%rip), %xmm1
        mulsd     %xmm0, %xmm1
        pxor      %xmm0, %xmm0
        addsd     .L_2il0floatpacket.17(%rip), %xmm1
        cvtsd2ss  %xmm1, %xmm0
..B1.7:
        ret       
..B1.8:
        pxor      %xmm1, %xmm1
        cvtss2sd  -8(%rsp), %xmm1
        mulsd     %xmm1, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.8(%rip), %xmm3
        movsd     .L_2il0floatpacket.10(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.9(%rip), %xmm3
        addsd     .L_2il0floatpacket.11(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.17(%rip), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movaps    %xmm3, %xmm0
        ret       
..B1.9:
        pxor      %xmm3, %xmm3
        pxor      %xmm4, %xmm4
        cvtss2sd  -8(%rsp), %xmm3
        andps     .L_2il0floatpacket.18(%rip), %xmm3
        pxor      %xmm6, %xmm6
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        mulsd     %xmm3, %xmm0
        movsd     .L_2il0floatpacket.4(%rip), %xmm7
        movsd     .L_2il0floatpacket.6(%rip), %xmm5
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rsi
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movl      -16(%rsp), %ecx
        movl      %ecx, %eax
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
        addsd     .L_2il0floatpacket.17(%rip), %xmm7
        movslq    %eax, %rax
        movaps    %xmm7, %xmm0
        subl      %eax, %ecx
        addsd     %xmm5, %xmm0
        subsd     %xmm5, %xmm7
        shrl      $6, %ecx
        shlq      $3, %rax
        shll      $23, %ecx
        mulsd     256(%rsi,%rax), %xmm0
        negq      %rax
        mulsd     256(%rsi,%rax), %xmm7
        lea       1056964608(%rcx), %edx
        negl      %ecx
        addl      $1056964608, %ecx
        movl      %edx, -24(%rsp)
        movl      %ecx, -20(%rsp)
        cvtss2sd  -24(%rsp), %xmm4
        cvtss2sd  -20(%rsp), %xmm6
        mulsd     %xmm4, %xmm0
        mulsd     %xmm6, %xmm7
        addsd     %xmm7, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.10:
        cmpl      $1119016188, %eax
        jbe       ..B1.14
..B1.11:
        cmpl      $2139095040, %eax
        jb        ..B1.15
..B1.12:
        movss     -8(%rsp), %xmm0
        mulss     %xmm0, %xmm0
..B1.13:
        ret       
..B1.14:
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm3
        andps     .L_2il0floatpacket.18(%rip), %xmm3
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        mulsd     %xmm3, %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rcx
        movl      $0, -24(%rsp)
        addsd     .L_2il0floatpacket.1(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        movl      -16(%rsp), %eax
        movl      %eax, %edx
        shll      $26, %edx
        subss     .L_2il0floatpacket.2(%rip), %xmm1
        cvtss2sd  %xmm1, %xmm1
        mulsd     .L_2il0floatpacket.3(%rip), %xmm1
        sarl      $26, %edx
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        subl      %edx, %eax
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm4
        movslq    %edx, %rdx
        addsd     %xmm4, %xmm0
        shrl      $6, %eax
        addsd     .L_2il0floatpacket.17(%rip), %xmm0
        mulsd     256(%rcx,%rdx,8), %xmm0
        shll      $20, %eax
        addl      $1071644672, %eax
        movl      %eax, -20(%rsp)
        mulsd     -24(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.15:
        movss     .L_2il0floatpacket.12(%rip), %xmm0
        mulss     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	coshf,@function
	.size	coshf,.-coshf
	.data
# -- End  coshf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.15:
	.long	0x7fffffff,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.18:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,16
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
	.long	0xdcf5905b,0x3efa3001
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x72601504,0x3fa55555
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x7543543e,0x3f56c143
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0xffe2f097,0x3fdfffff
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x6e6d0908,0x3fa55666
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0xf3327c54,0x3fdfffff
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.17:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,8
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
.L_2il0floatpacket.16:
	.long	0x3f800000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
