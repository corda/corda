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
	.file "nexttowardf_gen.c"
	.text
..TXTST0:
# -- Begin  nexttowardf
	.text
       .align    16,0x90
	.globl nexttowardf
nexttowardf:
# parameter 1: %xmm0
# parameter 2: 8 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_nexttowardf.1:
..L2:

        movss     %xmm0, -16(%rsp)
        movzwl    -14(%rsp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        je        ..B1.22
..B1.2:
        movzwl    16(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        je        ..B1.20
..B1.3:
        flds      -16(%rsp)
        fldt      8(%rsp)
        fucomi    %st(1), %st
        jp        ..B1.4
        je        ..B1.23
..B1.4:
        fstp      %st(0)
        movl      -16(%rsp), %ecx
        testl     $2147483647, %ecx
        je        ..B1.19
..B1.5:
        fstpt     -24(%rsp)
        movb      -15(%rsp), %dl
        movb      17(%rsp), %al
        andb      $-128, %dl
        andb      $-128, %al
        shrb      $7, %dl
        shrb      $7, %al
        cmpb      %dl, %al
        jne       ..B1.11
..B1.6:
        movzwl    -16(%rsp), %eax
        andl      $32767, %eax
        cmpl      %esi, %eax
        jg        ..B1.11
..B1.7:
        jne       ..B1.12
..B1.8:
        movl      -20(%rsp), %eax
        movl      12(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B1.11
..B1.9:
        jne       ..B1.12
..B1.10:
        movl      -24(%rsp), %eax
        cmpl      8(%rsp), %eax
        jbe       ..B1.12
..B1.11:
        decl      %ecx
        movl      %ecx, -24(%rsp)
        jmp       ..B1.13
..B1.12:
        incl      %ecx
        movl      %ecx, -24(%rsp)
..B1.13:
        andl      $2147483647, %ecx
        lea       -8388608(%rcx), %eax
        cmpl      $2130706432, %eax
        jb        ..B1.18
..B1.14:
        cmpl      $8388608, %ecx
        jae       ..B1.16
..B1.15:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        movss     %xmm1, -8(%rsp)
        movss     -8(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     -24(%rsp), %xmm0
        movss     %xmm1, -8(%rsp)
        ret       
..B1.16:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        movss     %xmm0, -8(%rsp)
        movss     -8(%rsp), %xmm1
        mulss     %xmm0, %xmm1
        movss     -24(%rsp), %xmm0
        movss     %xmm1, -8(%rsp)
..B1.17:
        ret       
..B1.18:
        movss     -24(%rsp), %xmm0
        ret       
..B1.19:
        fstp      %st(0)
        movb      17(%rsp), %al
        lea       _smallest_value_32(%rip), %rdx
        andb      $-128, %al
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, -8(%rsp)
        shrb      $7, %al
        movss     -8(%rsp), %xmm1
        mulss     %xmm0, %xmm1
        movss     %xmm1, -8(%rsp)
        movzbl    %al, %ecx
        movl      (%rdx,%rcx,4), %esi
        movl      %esi, -24(%rsp)
        jmp       ..B1.18
..B1.20:
        movq      $0x8000000000000000, %rax
        cmpq      8(%rsp), %rax
        je        ..B1.3
..B1.21:
        flds      -16(%rsp)
        fldt      8(%rsp)
        fmulp     %st, %st(1)
        fstps     -24(%rsp)
        movss     -24(%rsp), %xmm0
        ret       
..B1.22:
        testl     $8388607, -16(%rsp)
        jne       ..B1.21
        jmp       ..B1.2
..B1.23:
        fstp      %st(1)
        fstps     -24(%rsp)
        movss     -24(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nexttowardf,@function
	.size	nexttowardf,.-nexttowardf
	.data
# -- End  nexttowardf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x0d800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_smallest_value_32:
	.long	1
	.long	2147483649
	.type	_smallest_value_32,@object
	.size	_smallest_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
