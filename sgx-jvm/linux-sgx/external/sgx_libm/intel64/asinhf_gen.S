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
	.file "asinhf_gen.c"
	.text
..TXTST0:
# -- Begin  asinhf
	.text
       .align    16,0x90
	.globl asinhf
asinhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_asinhf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        jge       ..B1.15
..B1.2:
        cmpl      $1056964608, %eax
        jge       ..B1.11
..B1.3:
        cmpl      $838860800, %eax
        jge       ..B1.10
..B1.4:
        shrl      $31, %edx
        cmpl      $8388608, %eax
        jl        ..B1.6
..B1.5:
        movss     .L_2il0floatpacket.8(%rip), %xmm1
        lea       _MINNORM(%rip), %rax
        mulss     %xmm1, %xmm0
        movl      %edx, %edx
        subss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.9(%rip), %xmm0
        ret       
..B1.6:
        testl     %eax, %eax
        je        ..B1.8
..B1.7:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        cvtss2sd  -8(%rsp), %xmm2
        movss     (%rax,%rdx,4), %xmm0
        mulss     .L_2il0floatpacket.9(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm1
        subsd     %xmm0, %xmm2
        pxor      %xmm0, %xmm0
        movss     %xmm1, -24(%rsp)
        cvtsd2ss  %xmm2, %xmm0
        ret       
..B1.8:
        movss     -8(%rsp), %xmm0
..B1.9:
        ret       
..B1.10:
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
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
        movsd     %xmm3, -16(%rsp)
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        movaps    %xmm4, %xmm0
        ret       
..B1.11:
        pxor      %xmm1, %xmm1
        cmpl      $1199570944, %eax
        cvtss2sd  -8(%rsp), %xmm1
        andps     .L_2il0floatpacket.15(%rip), %xmm1
        jge       ..B1.13
..B1.12:
        movaps    %xmm1, %xmm0
        movl      $-1023, %r8d
        mulsd     %xmm1, %xmm0
        addsd     .L_2il0floatpacket.14(%rip), %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -16(%rsp)
        jmp       ..B1.14
..B1.13:
        movsd     %xmm1, -16(%rsp)
        movl      $-1022, %r8d
..B1.14:
        movzwl    -10(%rsp), %ecx
        movl      %ecx, %eax
        andl      $-32753, %ecx
        pxor      %xmm0, %xmm0
        orl       $-49168, %ecx
        andl      $32752, %eax
        movw      %cx, -10(%rsp)
        pxor      %xmm1, %xmm1
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        movl      -12(%rsp), %esi
        andl      $1048575, %esi
        shrl      $12, %esi
        cvtss2sd  (%rdi,%rsi,4), %xmm0
        mulsd     -16(%rsp), %xmm0
        movsd     .L_2il0floatpacket.11(%rip), %xmm2
        shrl      $4, %eax
        addsd     .L_2il0floatpacket.10(%rip), %xmm0
        mulsd     %xmm0, %xmm2
        addl      %eax, %r8d
        cvtsi2sd  %r8d, %xmm1
        addsd     .L_2il0floatpacket.12(%rip), %xmm2
        mulsd     .L_2il0floatpacket.13(%rip), %xmm1
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm2
        testl     $-2147483648, %edx
        movq      __libm_logf_table_256@GOTPCREL(%rip), %r9
        addsd     %xmm0, %xmm2
        addsd     (%r9,%rsi,8), %xmm1
        addsd     %xmm1, %xmm2
        movaps    %xmm2, %xmm0
        xorps     .L_2il0floatpacket.16(%rip), %xmm0
        jne       ..L3
        movaps    %xmm2, %xmm0
..L3:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.15:
        movss     -8(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	asinhf,@function
	.size	asinhf,.-asinhf
	.data
# -- End  asinhf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.15:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.16:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 8
.L_2il0floatpacket.0:
	.long	0xd28ee596,0x3f73cb17
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x9b9f611c,0x3f91287a
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0xac0cf195,0x3f9f1aa4
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x1236cb49,0x3fb33333
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xd27f5e7d,0xbf874400
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x4197b1a0,0xbf96d307
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xdacdeace,0xbfa6db62
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x552ea1bd,0xbfc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x6890dd10,0x3fd5555a
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0xe7362feb,0xbfe00001
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0xfefa39ef,0x3fe62e42
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
.L_2il0floatpacket.14:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,8
	.align 4
.L_2il0floatpacket.8:
	.long	0x71800000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,4
	.align 4
.L_2il0floatpacket.9:
	.long	0x0d800000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,4
	.align 4
_MINNORM:
	.long	8388608
	.long	2155872256
	.type	_MINNORM,@object
	.size	_MINNORM,8
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
