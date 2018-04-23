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
	.file "ilogbl.c"
	.text
..TXTST0:
# -- Begin  ilogbl
	.text
       .align    16,0x90
	.globl ilogbl
ilogbl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $32767, %eax
        je        ..B1.20
..B1.4:
        testl     %eax, %eax
        jne       ..B1.13
..B1.5:
        cmpl      $0, 12(%ebp)
        jne       ..B1.7
..B1.6:
        cmpl      $0, 8(%ebp)
        je        ..B1.14
..B1.7:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.19
..B1.8:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%edx)
        fstpt     8(%ebp)
..B1.11:
        fldcw     14(%esp)
..B1.12:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        addl      $-16458, %eax
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        addl      $-16383, %eax
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.18
..B1.15:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        fldcw     14(%esp)
..B1.18:
        fldl      _infs@GOTOFF(%edx)
        movl      $-2147483648, %eax
        fmull     _zeros@GOTOFF(%edx)
        fstpl     16(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%edx)
        fstpt     8(%ebp)
        jmp       ..B1.12
..B1.20:
        cmpl      $-2147483648, 12(%ebp)
        fldl      _infs@GOTOFF(%edx)
        fmull     _zeros@GOTOFF(%edx)
        fstpl     16(%esp)
        jne       ..B1.23
..B1.21:
        cmpl      $0, 8(%ebp)
        jne       ..B1.23
..B1.22:
        movl      $2147483647, %eax
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.24:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.25:
        fldcw     12(%esp)
..B1.26:
        fldt      8(%ebp)
        fstpt     (%esp)
..B1.27:
        fldcw     14(%esp)
..B1.28:
        movl      $-2147483648, %eax
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.28
        .align    16,0x90
	.type	ilogbl,@function
	.size	ilogbl,.-ilogbl
	.data
# -- End  ilogbl
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
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
# End
