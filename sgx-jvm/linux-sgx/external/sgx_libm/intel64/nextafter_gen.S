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
	.file "nextafter_gen.c"
	.text
..TXTST0:
# -- Begin  nextafter
	.text
       .align    16,0x90
	.globl nextafter
nextafter:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_nextafter.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movzwl    -10(%rsp), %r8d
        andl      $32752, %r8d
        shrl      $4, %r8d
        movsd     %xmm1, -8(%rsp)
        cmpl      $2047, %r8d
        je        ..B1.23
..B1.2:
        movzwl    -2(%rsp), %edi
        andl      $32752, %edi
        shrl      $4, %edi
        cmpl      $2047, %edi
        je        ..B1.20
..B1.3:
        movsd     -8(%rsp), %xmm0
        movsd     -16(%rsp), %xmm1
        ucomisd   %xmm0, %xmm1
        jp        ..B1.4
        je        ..B1.17
..B1.4:
        movl      -12(%rsp), %edx
        movl      %edx, %eax
        andl      $2147483647, %eax
        orl       -16(%rsp), %eax
        je        ..B1.19
..B1.5:
        movl      %edx, %eax
        movl      -4(%rsp), %ecx
        xorl      %ecx, %eax
        movq      -16(%rsp), %rsi
        testl     $-2147483648, %eax
        jne       ..B1.11
..B1.6:
        cmpl      %edi, %r8d
        jg        ..B1.11
..B1.7:
        jne       ..B1.12
..B1.8:
        andl      $1048575, %edx
        andl      $1048575, %ecx
        cmpl      %ecx, %edx
        jg        ..B1.11
..B1.9:
        jne       ..B1.12
..B1.10:
        movl      -16(%rsp), %eax
        cmpl      -8(%rsp), %eax
        jbe       ..B1.12
..B1.11:
        decq      %rsi
        movq      %rsi, -16(%rsp)
        jmp       ..B1.13
..B1.12:
        incq      %rsi
        movq      %rsi, -16(%rsp)
..B1.13:
        movl      -12(%rsp), %edx
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
        movsd     %xmm0, -24(%rsp)
        movsd     -16(%rsp), %xmm0
        ret       
..B1.16:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -16(%rsp), %xmm0
..B1.17:
        ret       
..B1.18:
        movsd     -16(%rsp), %xmm0
        ret       
..B1.19:
        movb      -1(%rsp), %al
        lea       _small_value_64(%rip), %rdi
        andb      $-128, %al
        lea       _smallest_value_64(%rip), %rdx
        shrb      $7, %al
        movsd     (%rdi), %xmm0
        mulsd     %xmm0, %xmm0
        movzbl    %al, %ecx
        movsd     %xmm0, -24(%rsp)
        movq      (%rdx,%rcx,8), %rsi
        movq      %rsi, -16(%rsp)
        jmp       ..B1.18
..B1.20:
        testl     $1048575, -4(%rsp)
        jne       ..B1.22
..B1.21:
        cmpl      $0, -8(%rsp)
        je        ..B1.3
..B1.22:
        movsd     -16(%rsp), %xmm0
        addsd     -8(%rsp), %xmm0
        ret       
..B1.23:
        testl     $1048575, -12(%rsp)
        jne       ..B1.22
..B1.24:
        cmpl      $0, -16(%rsp)
        jne       ..B1.22
        jmp       ..B1.2
        .align    16,0x90
	.cfi_endproc
	.type	nextafter,@function
	.size	nextafter,.-nextafter
	.data
# -- End  nextafter
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
