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
	.file "libm_exp_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_exp_k64
	.text
       .align    16,0x90
	.globl __libm_exp_k64
__libm_exp_k64:
# parameter 1: %xmm0
# parameter 2: %rdi
# parameter 3: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___libm_exp_k64.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movzwl    -2(%rsp), %eax
        andl      $32752, %eax
        movb      -1(%rsp), %dl
        andb      $-128, %dl
        shrl      $4, %eax
        shrb      $7, %dl
        cmpl      $2047, %eax
        je        ..B1.10
..B1.2:
        cmpl      $966, %eax
        jge       ..B1.6
..B1.3:
        movl      $0, (%rdi)
        cmpl      $917, %eax
        jge       ..B1.5
..B1.4:
        movq      $0x3ff0000000000000, %rax
        movq      %rax, (%rsi)
        movq      $0, 8(%rsi)
        ret       
..B1.5:
        movsd     .L_2il0floatpacket.9(%rip), %xmm0
        movsd     -8(%rsp), %xmm2
        movaps    %xmm0, %xmm1
        addsd     %xmm2, %xmm1
        movsd     %xmm1, (%rsi)
        subsd     %xmm0, %xmm1
        movsd     %xmm1, -24(%rsp)
        subsd     %xmm1, %xmm2
        movsd     %xmm2, 8(%rsi)
        ret       
..B1.6:
        cmpl      $1035, %eax
        jge       ..B1.8
..B1.7:
        movsd     -8(%rsp), %xmm0
        lea       _TWO_52H(%rip), %rax
        movsd     .L_2il0floatpacket.0(%rip), %xmm4
        movsd     .L_2il0floatpacket.1(%rip), %xmm2
        movsd     .L_2il0floatpacket.2(%rip), %xmm3
        mulsd     %xmm0, %xmm4
        movsd     (%rax), %xmm1
        movsd     .L_2il0floatpacket.4(%rip), %xmm12
        addsd     %xmm1, %xmm4
        movsd     %xmm4, -24(%rsp)
        subsd     %xmm1, %xmm4
        mulsd     %xmm4, %xmm2
        mulsd     %xmm3, %xmm4
        subsd     %xmm2, %xmm0
        movaps    %xmm0, %xmm7
        movsd     %xmm0, -8(%rsp)
        subsd     %xmm4, %xmm7
        movaps    %xmm7, %xmm8
        subsd     %xmm7, %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm9
        subsd     %xmm4, %xmm0
        movsd     .L_2il0floatpacket.9(%rip), %xmm5
        addsd     %xmm0, %xmm8
        addsd     %xmm7, %xmm5
        movaps    %xmm8, %xmm10
        mulsd     %xmm8, %xmm10
        mulsd     %xmm10, %xmm12
        mulsd     %xmm10, %xmm9
        addsd     .L_2il0floatpacket.5(%rip), %xmm12
        addsd     .L_2il0floatpacket.7(%rip), %xmm9
        mulsd     %xmm10, %xmm12
        mulsd     %xmm8, %xmm9
        movsd     %xmm5, -16(%rsp)
        addsd     %xmm9, %xmm12
        movl      $0, -16(%rsp)
        movsd     -16(%rsp), %xmm14
        movsd     .L_2il0floatpacket.3(%rip), %xmm6
        movsd     .L_2il0floatpacket.8(%rip), %xmm11
        addsd     %xmm14, %xmm6
        mulsd     %xmm10, %xmm12
        mulsd     %xmm10, %xmm11
        subsd     %xmm6, %xmm7
        addsd     %xmm11, %xmm12
        addsd     %xmm7, %xmm0
        movl      -24(%rsp), %r8d
        movl      %r8d, %edx
        shll      $25, %edx
        addsd     %xmm12, %xmm0
        sarl      $25, %edx
        subl      %edx, %r8d
        movslq    %edx, %rdx
        shlq      $4, %rdx
        movq      __libm_exp_table_128@GOTPCREL(%rip), %rcx
        sarl      $7, %r8d
        movsd     1032(%rcx,%rdx), %xmm1
        movaps    %xmm1, %xmm13
        mulsd     %xmm0, %xmm1
        addsd     %xmm14, %xmm0
        mulsd     %xmm14, %xmm13
        movsd     1024(%rcx,%rdx), %xmm15
        mulsd     %xmm0, %xmm15
        movl      %r8d, (%rdi)
        addsd     %xmm15, %xmm1
        movsd     %xmm13, (%rsi)
        movsd     %xmm1, 8(%rsi)
        ret       
..B1.8:
        movzbl    %dl, %edx
        lea       _inf_zeros(%rip), %rax
        movl      $0, (%rdi)
        movq      $0, 8(%rsi)
        movq      (%rax,%rdx,8), %rcx
        movq      %rcx, (%rsi)
..B1.9:
        ret       
..B1.10:
        testl     $1048575, -4(%rsp)
        jne       ..B1.13
..B1.11:
        cmpl      $0, -8(%rsp)
        jne       ..B1.13
..B1.12:
        movzbl    %dl, %edx
        lea       _inf_zeros(%rip), %rax
        movsd     (%rax,%rdx,8), %xmm0
        jmp       ..B1.14
..B1.13:
        movsd     -8(%rsp), %xmm0
..B1.14:
        movl      $0, (%rdi)
        movsd     %xmm0, (%rsi)
        movq      $0, 8(%rsi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_exp_k64,@function
	.size	__libm_exp_k64,.-__libm_exp_k64
	.data
# -- End  __libm_exp_k64
	.section .rodata, "a"
	.align 8
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
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_inf_zeros:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
