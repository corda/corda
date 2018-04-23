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
	.file "atanhf_gen.c"
	.text
..TXTST0:
# -- Begin  atanhf
	.text
       .align    16,0x90
	.globl atanhf
atanhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_atanhf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -16(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1065353216, %eax
        jae       ..B1.12
..B1.2:
        cmpl      $1056964608, %eax
        jae       ..B1.11
..B1.3:
        cmpl      $838860800, %eax
        jae       ..B1.10
..B1.4:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jb        ..B1.6
..B1.5:
        movss     .L_2il0floatpacket.15(%rip), %xmm1
        addss     %xmm0, %xmm1
        mulss     %xmm1, %xmm0
        ret       
..B1.6:
        testl     %eax, %eax
        je        ..B1.8
..B1.7:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        cvtss2sd  -16(%rsp), %xmm2
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.14(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        addsd     %xmm0, %xmm2
        pxor      %xmm0, %xmm0
        movss     %xmm1, -40(%rsp)
        cvtsd2ss  %xmm2, %xmm0
        ret       
..B1.8:
        movss     -16(%rsp), %xmm0
..B1.9:
        ret       
..B1.10:
        pxor      %xmm3, %xmm3
        cvtss2sd  -16(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.5(%rip), %xmm4
        movsd     .L_2il0floatpacket.9(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.6(%rip), %xmm4
        addsd     .L_2il0floatpacket.10(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        addsd     .L_2il0floatpacket.11(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.8(%rip), %xmm4
        addsd     .L_2il0floatpacket.12(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        movaps    %xmm4, %xmm0
        ret       
..B1.11:
        pxor      %xmm1, %xmm1
        pxor      %xmm4, %xmm4
        cvtss2sd  -16(%rsp), %xmm1
        movsd     .L_2il0floatpacket.16(%rip), %xmm2
        pxor      %xmm6, %xmm6
        movaps    %xmm2, %xmm0
        pxor      %xmm8, %xmm8
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r9
        addsd     %xmm1, %xmm0
        subsd     %xmm1, %xmm2
        movsd     %xmm0, -32(%rsp)
        movsd     %xmm2, -24(%rsp)
        movzwl    -26(%rsp), %ecx
        movl      %ecx, %eax
        movzwl    -18(%rsp), %esi
        movl      %esi, %edx
        andl      $-32753, %ecx
        andl      $-32753, %esi
        orl       $-49168, %ecx
        orl       $-49168, %esi
        movw      %cx, -26(%rsp)
        andl      $32752, %eax
        movw      %si, -18(%rsp)
        andl      $32752, %edx
        movl      -28(%rsp), %edi
        movl      -20(%rsp), %r8d
        andl      $1048575, %edi
        andl      $1048575, %r8d
        shrl      $12, %edi
        shrl      $12, %r8d
        cvtss2sd  (%r9,%rdi,4), %xmm4
        cvtss2sd  (%r9,%r8,4), %xmm6
        mulsd     -32(%rsp), %xmm4
        mulsd     -24(%rsp), %xmm6
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        movsd     .L_2il0floatpacket.1(%rip), %xmm7
        addsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm6
        movaps    %xmm7, %xmm0
        mulsd     %xmm4, %xmm0
        mulsd     %xmm6, %xmm7
        movsd     .L_2il0floatpacket.2(%rip), %xmm5
        shrl      $4, %eax
        addsd     %xmm5, %xmm0
        addsd     %xmm5, %xmm7
        mulsd     %xmm4, %xmm0
        mulsd     %xmm6, %xmm7
        mulsd     %xmm4, %xmm0
        mulsd     %xmm6, %xmm7
        addsd     %xmm4, %xmm0
        addsd     %xmm6, %xmm7
        shrl      $4, %edx
        addl      $-1023, %eax
        addl      $-1023, %edx
        subsd     %xmm7, %xmm0
        subl      %edx, %eax
        cvtsi2sd  %eax, %xmm8
        movq      __libm_logf_table_256@GOTPCREL(%rip), %r10
        mulsd     .L_2il0floatpacket.3(%rip), %xmm8
        movsd     (%r10,%rdi,8), %xmm9
        movsd     %xmm4, -32(%rsp)
        movsd     %xmm6, -24(%rsp)
        subsd     (%r10,%r8,8), %xmm9
        addsd     %xmm8, %xmm9
        addsd     %xmm9, %xmm0
        mulsd     .L_2il0floatpacket.4(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.12:
        cmpl      $2139095040, %eax
        ja        ..B1.17
..B1.13:
        cmpl      $1065353216, %eax
        jne       ..B1.15
..B1.14:
        lea       _ones(%rip), %rax
        shrl      $31, %edx
        pxor      %xmm1, %xmm1
        movss     (%rax,%rdx,4), %xmm0
        divss     %xmm1, %xmm0
        ret       
..B1.15:
        movss     .L_2il0floatpacket.13(%rip), %xmm1
        pxor      %xmm0, %xmm0
        mulss     %xmm1, %xmm0
..B1.16:
        ret       
..B1.17:
        movss     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atanhf,@function
	.size	atanhf,.-atanhf
	.data
# -- End  atanhf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x6890dd10,0x3fd5555a
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0xe7362feb,0xbfe00001
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0xfefa39ef,0x3fe62e42
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x1c66784b,0x3fc717fd
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xdc2b5888,0x3fb8b0df
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x5a00faf0,0x3fbc8408
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x9016c8e5,0x3fc9999c
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x10091234,0xbf68c8ed
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x64e392ec,0x3fb67c91
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0xb0fd5c67,0x3fc248ae
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x51abeffb,0x3fd55555
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.16:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,8
	.align 4
.L_2il0floatpacket.13:
	.long	0x7f800000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,4
	.align 4
.L_2il0floatpacket.14:
	.long	0x0d800000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,4
	.align 4
.L_2il0floatpacket.15:
	.long	0x3f800000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
