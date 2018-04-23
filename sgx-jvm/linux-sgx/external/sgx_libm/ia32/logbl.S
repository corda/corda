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
	.file "logbl.c"
	.text
..TXTST0:
# -- Begin  logbl
	.text
       .align    16,0x90
	.globl logbl
logbl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
..B1.2:
        fnstcw    22(%esp)
..B1.3:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.18
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.5:
        fldcw     20(%esp)
..B1.6:
        movl      $1, %edx
..B1.7:
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $32767, %ecx
        je        ..B1.17
..B1.8:
        testl     %ecx, %ecx
        jne       ..B1.12
..B1.9:
        cmpl      $0, 12(%ebp)
        jne       ..B1.11
..B1.10:
        cmpl      $0, 8(%ebp)
        je        ..B1.16
..B1.11:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%eax)
        fstpt     8(%ebp)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        addl      $-16458, %eax
        movl      %eax, (%esp)
        fildl     (%esp)
        fstpt     8(%esp)
        jmp       ..B1.13
..B1.12:
        addl      $-16383, %ecx
        movl      %ecx, (%esp)
        fildl     (%esp)
        fstpt     8(%esp)
..B1.13:
        testl     %edx, %edx
        je        ..B1.15
..B1.14:
        fldcw     22(%esp)
..B1.15:
        fldt      8(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        fldl      8+_ones@GOTOFF(%eax)
        fdivl     _zeros@GOTOFF(%eax)
        fstpt     8(%esp)
        jmp       ..B1.13
..B1.17:
        fldt      8(%ebp)
        movzbl    17(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fmull     _ones@GOTOFF(%eax,%ecx,8)
        fstpt     8(%esp)
        jmp       ..B1.13
..B1.18:
        xorl      %edx, %edx
        jmp       ..B1.7
        .align    16,0x90
	.type	logbl,@function
	.size	logbl,.-logbl
	.data
# -- End  logbl
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
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
