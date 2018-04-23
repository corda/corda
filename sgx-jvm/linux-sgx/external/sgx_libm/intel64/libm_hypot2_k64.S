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
	.file "libm_hypot2_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_hypot2_k64
	.text
       .align    16,0x90
	.globl __libm_hypot2_k64
__libm_hypot2_k64:
# parameter 1: %rdi
# parameter 2: %xmm0
# parameter 3: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value___libm_hypot2_k64.1:
..L2:

        movsd     %xmm0, -24(%rsp)
        movzwl    -18(%rsp), %eax
        testl     $32752, %eax
        movsd     %xmm1, -16(%rsp)
        jne       ..B1.8
..B1.2:
        testl     $1048575, -20(%rsp)
        jne       ..B1.8
..B1.3:
        cmpl      $0, -24(%rsp)
        jne       ..B1.8
..B1.4:
        movzwl    -10(%rsp), %eax
        testl     $32752, %eax
        jne       ..B1.8
..B1.5:
        testl     $1048575, -12(%rsp)
        jne       ..B1.8
..B1.6:
        cmpl      $0, -16(%rsp)
        jne       ..B1.8
..B1.7:
        movq      -24(%rsp), %rax
        movq      -16(%rsp), %rdx
        pxor      %xmm0, %xmm0
        movq      %rax, (%rdi)
        movq      %rdx, 8(%rdi)
        ret       
..B1.8:
        movsd     -24(%rsp), %xmm0
        movsd     -16(%rsp), %xmm2
        andps     .L_2il0floatpacket.6(%rip), %xmm0
        andps     .L_2il0floatpacket.6(%rip), %xmm2
        comisd    %xmm2, %xmm0
        jbe       ..B1.10
..B1.9:
        movsd     %xmm0, -8(%rsp)
        movaps    %xmm0, %xmm1
        jmp       ..B1.11
..B1.10:
        movsd     %xmm2, -8(%rsp)
        movaps    %xmm2, %xmm1
        movaps    %xmm0, %xmm2
..B1.11:
        movsd     %xmm2, -16(%rsp)
        movzwl    -10(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        testl     %eax, %eax
        je        ..B1.13
..B1.12:
        addl      $-1023, %eax
        jmp       ..B1.17
..B1.13:
        testl     $1048575, -12(%rsp)
        jne       ..B1.16
..B1.14:
        cmpl      $0, -16(%rsp)
        jne       ..B1.16
..B1.15:
        movl      $-10000, %eax
        jmp       ..B1.17
..B1.16:
        lea       48+_CONSTANTS(%rip), %rax
        movaps    %xmm2, %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -24(%rsp)
        movzwl    -18(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        addl      $-2046, %eax
..B1.17:
        movzwl    -2(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        testl     %edx, %edx
        je        ..B1.19
..B1.18:
        addl      $-1023, %edx
        jmp       ..B1.20
..B1.19:
        lea       48+_CONSTANTS(%rip), %rax
        movaps    %xmm1, %xmm0
        addl      $-2046, %edx
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -24(%rsp)
        movzwl    -18(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
..B1.20:
        addl      $-56, %edx
        cmpl      %edx, %eax
        lea       _CONSTANTS(%rip), %rax
        pxor      %xmm0, %xmm0
        jg        ..L3
        movaps    %xmm0, %xmm2
..L3:
        comisd    (%rax), %xmm1
        jb        ..B1.22
..B1.21:
        lea       40+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -8(%rsp)
        movsd     %xmm2, -16(%rsp)
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        jmp       ..B1.28
..B1.22:
        lea       8+_CONSTANTS(%rip), %rax
        comisd    (%rax), %xmm1
        jb        ..B1.24
..B1.23:
        lea       40+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -8(%rsp)
        movsd     %xmm2, -16(%rsp)
        movsd     .L_2il0floatpacket.3(%rip), %xmm0
        jmp       ..B1.28
..B1.24:
        lea       16+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm3
        comisd    %xmm1, %xmm3
        jb        ..B1.26
..B1.25:
        lea       32+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -8(%rsp)
        movsd     %xmm2, -16(%rsp)
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        jmp       ..B1.28
..B1.26:
        lea       24+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm3
        comisd    %xmm1, %xmm3
        jb        ..B1.28
..B1.27:
        lea       32+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -8(%rsp)
        movsd     %xmm2, -16(%rsp)
        movsd     .L_2il0floatpacket.5(%rip), %xmm0
..B1.28:
        movsd     .L_2il0floatpacket.1(%rip), %xmm9
        mulsd     %xmm1, %xmm9
        movaps    %xmm9, %xmm3
        movsd     .L_2il0floatpacket.1(%rip), %xmm14
        subsd     %xmm1, %xmm3
        mulsd     %xmm2, %xmm14
        subsd     %xmm3, %xmm9
        movsd     .L_2il0floatpacket.2(%rip), %xmm5
        movaps    %xmm9, %xmm6
        mulsd     %xmm9, %xmm6
        subsd     %xmm9, %xmm1
        mulsd     %xmm5, %xmm9
        movaps    %xmm14, %xmm4
        movaps    %xmm6, %xmm8
        mulsd     %xmm1, %xmm9
        subsd     %xmm2, %xmm4
        mulsd     %xmm1, %xmm1
        subsd     %xmm4, %xmm14
        addsd     %xmm9, %xmm8
        subsd     %xmm14, %xmm2
        movaps    %xmm14, %xmm12
        movaps    %xmm8, %xmm7
        mulsd     %xmm14, %xmm12
        subsd     %xmm6, %xmm7
        mulsd     %xmm5, %xmm14
        subsd     %xmm7, %xmm9
        mulsd     %xmm2, %xmm14
        mulsd     %xmm2, %xmm2
        addsd     %xmm1, %xmm9
        movaps    %xmm8, %xmm10
        addsd     %xmm12, %xmm10
        movaps    %xmm10, %xmm13
        movaps    %xmm10, %xmm1
        addsd     %xmm14, %xmm13
        subsd     %xmm8, %xmm1
        movaps    %xmm13, %xmm11
        subsd     %xmm1, %xmm12
        subsd     %xmm10, %xmm11
        addsd     %xmm9, %xmm12
        subsd     %xmm11, %xmm14
        movsd     %xmm13, (%rdi)
        addsd     %xmm12, %xmm14
        addsd     %xmm2, %xmm14
        movsd     %xmm14, 8(%rdi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_hypot2_k64,@function
	.size	__libm_hypot2_k64,.-__libm_hypot2_k64
	.data
# -- End  __libm_hypot2_k64
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.6:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x40a00800
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x02000000,0x41a00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x40900800
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0xc0a00800
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x00000000,0xc0900800
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 4
_CONSTANTS:
	.long	0
	.long	1727004672
	.long	4294967295
	.long	1608515583
	.long	4294967295
	.long	481296383
	.long	0
	.long	651165696
	.long	0
	.long	1610612736
	.long	0
	.long	534773760
	.long	0
	.long	2145386496
	.long	0
	.long	1126170624
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,64
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
