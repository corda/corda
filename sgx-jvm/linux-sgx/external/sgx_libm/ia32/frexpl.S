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
	.file "frexpl.c"
	.text
..TXTST0:
# -- Begin  frexpl
	.text
       .align    16,0x90
	.globl frexpl
frexpl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        movl      20(%ebp), %edx
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %ecx
        movl      %ecx, %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.16
..B1.4:
        testl     %eax, %eax
        jne       ..B1.14
..B1.5:
        cmpl      $0, 12(%ebp)
        jne       ..B1.8
..B1.6:
        cmpl      $0, 8(%ebp)
        jne       ..B1.8
..B1.7:
        fldt      8(%ebp)
        movl      $0, (%edx)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        movzwl    14(%esp), %eax
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        je        ..B1.15
..B1.9:
        orl       $-64768, %eax
        movl      $0, (%edx)
        movw      %ax, 12(%esp)
..B1.10:
        fldcw     12(%esp)
..B1.11:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
..B1.12:
        fldcw     14(%esp)
..B1.13:
        movzwl    16(%ebp), %ecx
        movl      %ecx, %eax
        andl      $-32768, %ecx
        andl      $32767, %eax
        orl       $-49154, %ecx
        addl      $-16457, %eax
        movw      %cx, 16(%ebp)
        fldt      8(%ebp)
        movl      %eax, (%edx)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        andl      $-32768, %ecx
        addl      $-16382, %eax
        orl       $-49154, %ecx
        movw      %cx, 16(%ebp)
        fldt      8(%ebp)
        movl      %eax, (%edx)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
        jmp       ..B1.13
..B1.16:
        movl      $0, (%edx)
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.22
..B1.17:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.18:
        fldcw     12(%esp)
..B1.19:
        fldt      8(%ebp)
        fstpt     (%esp)
..B1.20:
        fldcw     14(%esp)
..B1.21:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.21
        .align    16,0x90
	.type	frexpl,@function
	.size	frexpl,.-frexpl
	.data
# -- End  frexpl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.data
	.section .note.GNU-stack, ""
# End
