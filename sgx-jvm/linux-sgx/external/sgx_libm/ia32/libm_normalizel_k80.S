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
	.file "libm_normalizel_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_normalizel_k80
	.text
       .align    16,0x90
	.hidden __libm_normalizel_k80
	.globl __libm_normalizel_k80
__libm_normalizel_k80:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        movl      16(%esp), %ecx
        cmpl      $0, 4(%ecx)
        jne       ..B1.3
..B1.2:
        cmpl      $0, (%ecx)
        je        ..B1.14
..B1.3:
        movzwl    8(%ecx), %ebx
        movl      %ebx, %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.14
..B1.4:
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        testl     %eax, %eax
        jle       ..B1.16
..B1.5:
        andl      $-32768, %ebx
        addl      $-16383, %eax
        orl       $-49153, %ebx
..B1.6:
        movw      %bx, 8(%ecx)
        cmpl      $0, 16(%ecx)
        jne       ..B1.8
..B1.7:
        cmpl      $0, 12(%ecx)
        je        ..B1.13
..B1.8:
        movzwl    20(%ecx), %ebp
        movl      %ebp, %edi
        andl      $32767, %edi
        jle       ..B1.15
..B1.9:
        andl      $-32768, %ebp
        addl      $-16383, %edi
        orl       $-49153, %ebp
..B1.10:
        lea       -256(%eax), %ebx
        cmpl      %ebx, %edi
        jle       ..B1.12
..B1.11:
        subl      %eax, %edi
        andl      $-32768, %ebp
        addl      $16383, %edi
        andl      $32767, %edi
        orl       %edi, %ebp
        movw      %bp, 20(%ecx)
        jmp       ..B1.13
..B1.12:
        fldt      _CONSTANTS@GOTOFF(%edx)
        fstpt     12(%ecx)
..B1.13:
        addl      %eax, 24(%ecx)
..B1.14:
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.15:
        fldt      12(%ecx)
        fldt      12+_CONSTANTS@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstpt     12(%ecx)
        movzwl    20(%ecx), %ebp
        movl      %ebp, %edi
        andl      $32767, %edi
        andl      $-32768, %ebp
        addl      $-16639, %edi
        orl       $-49153, %ebp
        jmp       ..B1.10
..B1.16:
        fldt      (%ecx)
        fldt      12+_CONSTANTS@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstpt     (%ecx)
        movzwl    8(%ecx), %ebx
        movl      %ebx, %eax
        andl      $32767, %eax
        andl      $-32768, %ebx
        addl      $-16639, %eax
        orl       $-49153, %ebx
        jmp       ..B1.6
        .align    16,0x90
	.type	__libm_normalizel_k80,@function
	.size	__libm_normalizel_k80,.-__libm_normalizel_k80
	.data
# -- End  __libm_normalizel_k80
	.section .rodata, "a"
	.align 16
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16639
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,24
	.data
	.section .note.GNU-stack, ""
# End
