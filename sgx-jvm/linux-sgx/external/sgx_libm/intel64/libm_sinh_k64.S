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
	.file "libm_sinh_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_sinh_k64
	.text
       .align    16,0x90
	.globl __libm_sinh_k64
__libm_sinh_k64:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_sinh_k64.1:
..L2:

        movaps    %xmm0, %xmm3
        movsd     %xmm3, -32(%rsp)
        movzwl    -26(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $1045, %eax
        jge       ..B1.11
..B1.2:
        movl      -28(%rsp), %edx
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $1066695393, %ecx
        jae       ..B1.7
..B1.3:
        cmpl      $1012924416, %ecx
        jae       ..B1.5
..B1.4:
        movsd     %xmm3, (%rdi)
        movq      $0, 8(%rdi)
        jmp       ..B1.6
..B1.5:
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.3(%rip), %xmm4
        movsd     .L_2il0floatpacket.5(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.4(%rip), %xmm4
        addsd     .L_2il0floatpacket.6(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        movsd     %xmm1, -40(%rsp)
        addsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        movsd     %xmm3, (%rdi)
        movsd     %xmm4, 8(%rdi)
..B1.6:
        xorl      %eax, %eax
        ret       
..B1.7:
        movsd     -32(%rsp), %xmm2
        lea       _TWO_52(%rip), %rax
        andps     .L_2il0floatpacket.13(%rip), %xmm2
        movsd     .L_2il0floatpacket.0(%rip), %xmm4
        mulsd     %xmm2, %xmm4
        movsd     (%rax), %xmm0
        movsd     .L_2il0floatpacket.1(%rip), %xmm1
        addsd     %xmm0, %xmm4
        movsd     %xmm4, -40(%rsp)
        subsd     %xmm0, %xmm4
        mulsd     %xmm4, %xmm1
        movsd     %xmm2, -32(%rsp)
        subsd     %xmm1, %xmm2
        movsd     .L_2il0floatpacket.2(%rip), %xmm3
        movaps    %xmm2, %xmm6
        mulsd     %xmm3, %xmm4
        movl      -40(%rsp), %eax
        movl      %eax, %r10d
        shll      $25, %r10d
        subsd     %xmm4, %xmm6
        sarl      $25, %r10d
        subsd     %xmm6, %xmm2
        subl      %r10d, %eax
        subsd     %xmm4, %xmm2
        movsd     .L_2il0floatpacket.15(%rip), %xmm1
        movaps    %xmm2, %xmm0
        sarl      $7, %eax
        addsd     %xmm6, %xmm0
        addsd     %xmm6, %xmm1
        cmpl      $1077338037, %ecx
        jae       ..B1.9
..B1.8:
        movaps    %xmm0, %xmm7
        xorl      %ecx, %ecx
        mulsd     %xmm0, %xmm7
        movaps    %xmm6, %xmm9
        movsd     .L_2il0floatpacket.8(%rip), %xmm8
        movl      %eax, %esi
        mulsd     %xmm7, %xmm8
        negl      %esi
        movsd     .L_2il0floatpacket.15(%rip), %xmm3
        addl      $1023, %esi
        movsd     %xmm1, -32(%rsp)
        subl      %eax, %esi
        movl      %ecx, -32(%rsp)
        andl      $2047, %esi
        movsd     -32(%rsp), %xmm10
        decl      %eax
        movsd     .L_2il0floatpacket.7(%rip), %xmm4
        subsd     %xmm6, %xmm3
        addsd     .L_2il0floatpacket.9(%rip), %xmm8
        addsd     %xmm10, %xmm4
        mulsd     %xmm7, %xmm8
        subsd     %xmm4, %xmm9
        mulsd     %xmm0, %xmm8
        addsd     %xmm2, %xmm9
        movsd     .L_2il0floatpacket.10(%rip), %xmm0
        addsd     %xmm8, %xmm9
        mulsd     %xmm7, %xmm0
        movsd     %xmm3, -24(%rsp)
        lea       ones(%rip), %r11
        movl      %ecx, -24(%rsp)
        movsd     -24(%rsp), %xmm1
        movsd     .L_2il0floatpacket.7(%rip), %xmm5
        movslq    %r10d, %r10
        addsd     .L_2il0floatpacket.11(%rip), %xmm0
        addsd     %xmm1, %xmm5
        mulsd     %xmm7, %xmm0
        addsd     %xmm5, %xmm6
        addsd     .L_2il0floatpacket.12(%rip), %xmm0
        addsd     %xmm6, %xmm2
        mulsd     %xmm7, %xmm0
        xorps     .L_2il0floatpacket.14(%rip), %xmm2
        addsd     %xmm0, %xmm9
        subsd     %xmm8, %xmm2
        shlq      $4, %r10
        addsd     %xmm0, %xmm2
        movaps    %xmm9, %xmm0
        movaps    %xmm2, %xmm12
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r9
        addsd     %xmm10, %xmm9
        addsd     %xmm1, %xmm2
        mulsd     1024(%r10,%r9), %xmm9
        movsd     1032(%r10,%r9), %xmm13
        negq      %r10
        movzwl    6+.L_2il0floatpacket.15(%rip), %r8d
        movsd     .L_2il0floatpacket.15(%rip), %xmm6
        andl      $-32753, %r8d
        shll      $4, %esi
        movsd     1032(%r9,%r10), %xmm11
        orl       %esi, %r8d
        mulsd     1024(%r9,%r10), %xmm2
        mulsd     %xmm11, %xmm12
        mulsd     %xmm11, %xmm1
        mulsd     %xmm13, %xmm0
        addsd     %xmm2, %xmm12
        mulsd     %xmm10, %xmm13
        addsd     %xmm9, %xmm0
        movsd     %xmm6, -16(%rsp)
        movaps    %xmm13, %xmm14
        movw      %r8w, -10(%rsp)
        movsd     -16(%rsp), %xmm2
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm12
        subsd     %xmm1, %xmm14
        subsd     %xmm12, %xmm0
        subsd     %xmm14, %xmm13
        shrl      $31, %edx
        subsd     %xmm1, %xmm13
        movsd     (%r11,%rdx,8), %xmm15
        addsd     %xmm13, %xmm0
        movsd     %xmm14, -40(%rsp)
        mulsd     %xmm15, %xmm14
        mulsd     %xmm15, %xmm0
        movsd     %xmm1, -24(%rsp)
        movsd     %xmm13, -32(%rsp)
        movsd     %xmm14, (%rdi)
        movsd     %xmm0, 8(%rdi)
        ret       
..B1.9:
        movsd     %xmm1, -40(%rsp)
        movaps    %xmm0, %xmm4
        movl      $0, -40(%rsp)
        decl      %eax
        movsd     -40(%rsp), %xmm7
        lea       ones(%rip), %rsi
        movsd     .L_2il0floatpacket.7(%rip), %xmm1
        movsd     .L_2il0floatpacket.10(%rip), %xmm5
        addsd     %xmm7, %xmm1
        mulsd     %xmm0, %xmm4
        subsd     %xmm1, %xmm6
        mulsd     %xmm4, %xmm5
        addsd     %xmm6, %xmm2
        addsd     .L_2il0floatpacket.11(%rip), %xmm5
        movsd     .L_2il0floatpacket.8(%rip), %xmm6
        mulsd     %xmm4, %xmm6
        mulsd     %xmm4, %xmm5
        addsd     .L_2il0floatpacket.9(%rip), %xmm6
        mulsd     %xmm6, %xmm0
        movsd     .L_2il0floatpacket.12(%rip), %xmm3
        addsd     %xmm0, %xmm5
        mulsd     %xmm4, %xmm5
        mulsd     %xmm3, %xmm4
        movslq    %r10d, %r10
        addsd     %xmm4, %xmm5
        shlq      $4, %r10
        addsd     %xmm5, %xmm2
        movaps    %xmm2, %xmm8
        addsd     %xmm7, %xmm2
        movq      __libm_exp_table_128@GOTPCREL(%rip), %rcx
        shrl      $31, %edx
        movsd     1032(%r10,%rcx), %xmm0
        mulsd     1024(%r10,%rcx), %xmm2
        mulsd     %xmm0, %xmm8
        mulsd     %xmm0, %xmm7
        addsd     %xmm2, %xmm8
        movsd     (%rsi,%rdx,8), %xmm2
        movsd     %xmm7, -40(%rsp)
        mulsd     %xmm2, %xmm7
        mulsd     %xmm8, %xmm2
        movsd     %xmm7, (%rdi)
        movsd     %xmm2, 8(%rdi)
..B1.10:
        ret       
..B1.11:
        movb      -25(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        cmpl      $2047, %eax
        je        ..B1.14
..B1.12:
        movzbl    %dl, %edx
        lea       infs(%rip), %rax
        pxor      %xmm0, %xmm0
        movq      $0, 8(%rdi)
        movl      (%rax,%rdx,4), %ecx
        cvtsi2sdq %rcx, %xmm0
        movsd     %xmm0, (%rdi)
..B1.13:
        xorl      %eax, %eax
        ret       
..B1.14:
        movq      -32(%rsp), %rax
        movq      %rax, (%rdi)
        movq      $0, 8(%rdi)
        jmp       ..B1.13
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sinh_k64,@function
	.size	__libm_sinh_k64,.-__libm_sinh_k64
	.data
# -- End  __libm_sinh_k64
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.13:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,16
	.align 16
.L_2il0floatpacket.14:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x652b82fe,0x40671547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3f762e42
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x3de6af28,0x3e2fdf47
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0xb9ff12f0,0x3ec71e39
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x111111c1,0x3f811111
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x1940cd88,0x3f2a01a0
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x55555555,0x3fc55555
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.15:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
_TWO_52:
	.long	0
	.long	1127219200
	.type	_TWO_52,@object
	.size	_TWO_52,8
	.align 4
infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	infs,@object
	.size	infs,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
