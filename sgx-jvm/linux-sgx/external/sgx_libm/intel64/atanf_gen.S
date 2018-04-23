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
	.file "atanf_gen.c"
	.text
..TXTST0:
# -- Begin  atanf
	.text
       .align    16,0x90
	.globl atanf
atanf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_atanf.1:
..L2:

        pxor      %xmm5, %xmm5
        movd      %xmm0, %edx
        cvtss2sd  %xmm0, %xmm5
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1300234240, %eax
        jae       ..B1.14
..B1.2:
        cmpl      $1073741824, %eax
        jae       ..B1.13
..B1.3:
        cmpl      $1056964608, %eax
        jae       ..B1.12
..B1.4:
        cmpl      $964689920, %eax
        jae       ..B1.11
..B1.5:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jb        ..B1.7
..B1.6:
        movl      %edx, %edx
        lea       _small_value_32(%rip), %rax
        movss     (%rax,%rdx,4), %xmm1
        mulss     .L_2il0floatpacket.17(%rip), %xmm1
        subss     %xmm1, %xmm0
        ret       
..B1.7:
        testl     %eax, %eax
        je        ..B1.10
..B1.8:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm1, %xmm1
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.17(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        subsd     %xmm0, %xmm5
        pxor      %xmm0, %xmm0
        movss     %xmm1, -8(%rsp)
        cvtsd2ss  %xmm5, %xmm0
..B1.10:
        ret       
..B1.11:
        movaps    %xmm5, %xmm1
        mulsd     %xmm5, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.4(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.5(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.1(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.6(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.2(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.3(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     .L_2il0floatpacket.8(%rip), %xmm2
        mulsd     %xmm1, %xmm2
        pxor      %xmm0, %xmm0
        addsd     %xmm2, %xmm3
        mulsd     %xmm5, %xmm3
        subsd     %xmm3, %xmm5
        cvtsd2ss  %xmm5, %xmm0
        ret       
..B1.12:
        andps     .L_2il0floatpacket.19(%rip), %xmm5
        testl     $-2147483648, %edx
        movsd     .L_2il0floatpacket.9(%rip), %xmm4
        movsd     .L_2il0floatpacket.18(%rip), %xmm1
        addsd     %xmm5, %xmm4
        addsd     %xmm1, %xmm5
        divsd     %xmm5, %xmm4
        movaps    %xmm4, %xmm0
        mulsd     %xmm4, %xmm0
        movaps    %xmm0, %xmm2
        mulsd     %xmm0, %xmm2
        movsd     .L_2il0floatpacket.10(%rip), %xmm5
        mulsd     %xmm2, %xmm5
        movsd     .L_2il0floatpacket.13(%rip), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.11(%rip), %xmm5
        mulsd     %xmm2, %xmm5
        addsd     .L_2il0floatpacket.14(%rip), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.12(%rip), %xmm5
        mulsd     %xmm0, %xmm5
        addsd     .L_2il0floatpacket.15(%rip), %xmm3
        addsd     %xmm3, %xmm5
        mulsd     %xmm4, %xmm5
        addsd     .L_2il0floatpacket.16(%rip), %xmm5
        movaps    %xmm5, %xmm0
        xorps     .L_2il0floatpacket.20(%rip), %xmm0
        jne       ..L3
        movaps    %xmm5, %xmm0
..L3:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.13:
        movsd     .L_2il0floatpacket.18(%rip), %xmm2
        lea       pi_2d(%rip), %rax
        divsd     %xmm5, %xmm2
        movaps    %xmm2, %xmm0
        mulsd     %xmm2, %xmm0
        movaps    %xmm0, %xmm5
        mulsd     %xmm0, %xmm5
        movsd     .L_2il0floatpacket.4(%rip), %xmm1
        mulsd     %xmm5, %xmm1
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.5(%rip), %xmm1
        mulsd     %xmm5, %xmm1
        addsd     .L_2il0floatpacket.1(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.6(%rip), %xmm1
        mulsd     %xmm5, %xmm1
        addsd     .L_2il0floatpacket.2(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.7(%rip), %xmm1
        mulsd     %xmm5, %xmm1
        addsd     .L_2il0floatpacket.3(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.8(%rip), %xmm1
        mulsd     %xmm0, %xmm1
        shrl      $31, %edx
        addsd     %xmm1, %xmm3
        movsd     (%rax,%rdx,8), %xmm0
        mulsd     %xmm2, %xmm3
        subsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.14:
        cmpl      $2139095040, %eax
        ja        ..B1.17
..B1.15:
        lea       pi_2f(%rip), %rax
        lea       _small_value_32(%rip), %rcx
        shrl      $31, %edx
        movss     (%rax,%rdx,4), %xmm0
        subss     (%rcx,%rdx,4), %xmm0
..B1.17:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atanf,@function
	.size	atanf,.-atanf
	.data
# -- End  atanf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.19:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,16
	.align 16
.L_2il0floatpacket.20:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,16
	.align 8
.L_2il0floatpacket.0:
	.long	0xd091d944,0xbf9ca14d
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x12effb80,0xbfb28de9
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0xcda40e6e,0xbfbc6a7e
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x1185f36d,0xbfc99997
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x3cb73089,0x3f7fa335
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x44053df6,0x3faac31f
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x1cf194fa,0x3fb70b87
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0xb177868e,0x3fc248e0
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x509fdfb1,0x3fd55555
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0xd9d9aa33,0xbfb1c1c0
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x04ba093e,0xbfc24485
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x312dd43c,0xbfd55555
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x947e6edc,0x3fbbcbeb
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x700fa0b3,0x3fc9997b
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 8
.L_2il0floatpacket.15:
	.long	0xfff8f7db,0x3fefffff
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,8
	.align 8
.L_2il0floatpacket.16:
	.long	0x54442d18,0x3fe921fb
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,8
	.align 8
.L_2il0floatpacket.18:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,8
	.align 8
pi_2d:
	.long	0x54442d18,0x3ff921fb
	.long	0x54442d18,0xbff921fb
	.type	pi_2d,@object
	.size	pi_2d,16
	.align 4
.L_2il0floatpacket.17:
	.long	0x0d800000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,4
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
