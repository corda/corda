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
	.file "nexttoward_gen.c"
	.text
..TXTST0:
# -- Begin  nexttoward
	.text
       .align    16,0x90
	.globl nexttoward
nexttoward:
# parameter 1: %xmm0
# parameter 2: 8 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_nexttoward.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movzwl    -2(%rsp), %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        ..B1.22
..B1.2:
        movzwl    16(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        je        ..B1.20
..B1.3:
        fldl      -8(%rsp)
        fldt      8(%rsp)
        fucomi    %st(1), %st
        jp        ..B1.4
        je        ..B1.24
..B1.4:
        fstp      %st(0)
        movl      -4(%rsp), %eax
        andl      $2147483647, %eax
        orl       -8(%rsp), %eax
        je        ..B1.19
..B1.5:
        fstpt     -24(%rsp)
        movb      -15(%rsp), %dl
        movb      17(%rsp), %al
        andb      $-128, %dl
        andb      $-128, %al
        shrb      $7, %dl
        shrb      $7, %al
        movq      -8(%rsp), %rcx
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
        decq      %rcx
        movq      %rcx, -40(%rsp)
        jmp       ..B1.13
..B1.12:
        incq      %rcx
        movq      %rcx, -40(%rsp)
..B1.13:
        movl      -36(%rsp), %edx
        andl      $2147483647, %edx
        lea       -1048576(%rdx), %eax
        cmpl      $2145386496, %eax
        jb        ..B1.18
..B1.14:
        cmpl      $1048576, %edx
        jae       ..B1.16
..B1.15:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -40(%rsp), %xmm0
        ret       
..B1.16:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -40(%rsp), %xmm0
..B1.17:
        ret       
..B1.18:
        movsd     -40(%rsp), %xmm0
        ret       
..B1.19:
        fstp      %st(0)
        movb      17(%rsp), %al
        lea       _small_value_64(%rip), %rdi
        andb      $-128, %al
        lea       _smallest_value_64(%rip), %rdx
        shrb      $7, %al
        movsd     (%rdi), %xmm0
        mulsd     %xmm0, %xmm0
        movzbl    %al, %ecx
        movsd     %xmm0, -32(%rsp)
        movq      (%rdx,%rcx,8), %rsi
        movq      %rsi, -40(%rsp)
        jmp       ..B1.18
..B1.20:
        movq      $0x8000000000000000, %rax
        cmpq      8(%rsp), %rax
        je        ..B1.3
..B1.21:
        fldl      -8(%rsp)
        fldt      8(%rsp)
        fmulp     %st, %st(1)
        fstpl     -40(%rsp)
        movsd     -40(%rsp), %xmm0
        ret       
..B1.22:
        testl     $1048575, -4(%rsp)
        jne       ..B1.21
..B1.23:
        cmpl      $0, -8(%rsp)
        jne       ..B1.21
        jmp       ..B1.2
..B1.24:
        fstp      %st(1)
        fstpl     -40(%rsp)
        movsd     -40(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nexttoward,@function
	.size	nexttoward,.-nexttoward
	.data
# -- End  nexttoward
	.section .rodata, "a"
	.align 4
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
