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
	.file "truncl.c"
	.text
..TXTST0:
# -- Begin  truncl
	.text
       .align    16,0x90
	.globl truncl
truncl:
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
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $16446, %ecx
        jge       ..B1.14
..B1.4:
        cmpl      $16383, %ecx
        jl        ..B1.9
..B1.5:
        cmpl      $16415, %ecx
        jge       ..B1.7
..B1.6:
        negl      %ecx
        addl      $30, %ecx
        movl      12(%ebp), %eax
        shrl      %cl, %eax
        shll      %cl, %eax
        movl      %eax, 12(%ebp)
        xorl      %eax, %eax
        jmp       ..B1.8
..B1.7:
        negl      %ecx
        addl      $30, %ecx
        movl      8(%ebp), %eax
        shrl      %cl, %eax
        shll      %cl, %eax
..B1.8:
        movl      %eax, 8(%ebp)
        fldt      8(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        testl     %ecx, %ecx
        jne       ..B1.13
..B1.10:
        cmpl      $0, 12(%ebp)
        jne       ..B1.12
..B1.11:
        cmpl      $0, 8(%ebp)
        je        ..B1.13
..B1.12:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     (%esp)
..B1.13:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _zeros@GOTOFF(%eax,%edx,8)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        movzwl    22(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.20
..B1.15:
        orl       $-64768, %ecx
        movw      %cx, 20(%esp)
..B1.16:
        fldcw     20(%esp)
..B1.17:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     8(%esp)
..B1.18:
        fldcw     22(%esp)
..B1.19:
        fldt      8(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     8(%esp)
        jmp       ..B1.19
        .align    16,0x90
	.type	truncl,@function
	.size	truncl,.-truncl
	.data
# -- End  truncl
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.section .note.GNU-stack, ""
# End
