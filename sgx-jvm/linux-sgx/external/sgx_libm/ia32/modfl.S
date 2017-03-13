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
	.file "modfl.c"
	.text
..TXTST0:
# -- Begin  modfl
	.text
       .align    16,0x90
	.globl modfl
modfl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $24, %esp
        movl      20(%ebp), %edx
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $16446, %ecx
        jge       ..B1.21
..B1.4:
        cmpl      $16383, %ecx
        jl        ..B1.16
..B1.5:
        fldt      8(%ebp)
        cmpl      $16415, %ecx
        fstpt     (%esp)
        jge       ..B1.7
..B1.6:
        negl      %ecx
        movl      $-1, %ebx
        addl      $30, %ecx
        shll      %cl, %ebx
        movl      %ebx, %ecx
        notl      %ecx
        movl      12(%ebp), %esi
        andl      %esi, %ecx
        andl      %ebx, %esi
        xorl      %ebx, %ebx
        orl       8(%ebp), %ecx
        movl      %esi, 12(%ebp)
        jmp       ..B1.8
..B1.7:
        negl      %ecx
        movl      $-1, %ebx
        addl      $30, %ecx
        shll      %cl, %ebx
        movl      %ebx, %ecx
        notl      %ecx
        movl      8(%ebp), %esi
        andl      %esi, %ecx
        andl      %esi, %ebx
..B1.8:
        movl      %ebx, 8(%ebp)
        testl     %ecx, %ecx
        je        ..B1.14
..B1.9:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.23
..B1.10:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.11:
        fldcw     12(%esp)
..B1.12:
        fldt      8(%ebp)
        fstpt     (%edx)
        fldt      (%edx)
        fldt      (%esp)
        fsubp     %st, %st(1)
        fstpt     (%esp)
..B1.13:
        fldcw     14(%esp)
        jmp       ..B1.15
..B1.14:
        fldt      8(%ebp)
        fstpt     (%edx)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _zeros@GOTOFF(%eax,%edx,8)
        fstpt     (%esp)
..B1.15:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        testl     %ecx, %ecx
        jne       ..B1.20
..B1.17:
        cmpl      $0, 12(%ebp)
        jne       ..B1.19
..B1.18:
        cmpl      $0, 8(%ebp)
        je        ..B1.20
..B1.19:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.20:
        movzbl    17(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fldl      _zeros@GOTOFF(%eax,%ecx,8)
        fstpt     (%edx)
        fldt      8(%ebp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        cmpl      $32767, %ecx
        je        ..B1.24
..B1.22:
        fldt      8(%ebp)
        fstpt     (%edx)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _zeros@GOTOFF(%eax,%edx,8)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        fldt      8(%ebp)
        fstpt     (%edx)
        fldt      (%edx)
        fldt      (%esp)
        fsubp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.24:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.26
..B1.25:
        cmpl      $0, 8(%ebp)
        je        ..B1.22
..B1.26:
        movzwl    14(%esp), %ebx
        movl      %ebx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.32
..B1.27:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.28:
        fldcw     12(%esp)
..B1.29:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     (%edx)
..B1.30:
        fldcw     14(%esp)
..B1.31:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     (%edx)
        jmp       ..B1.31
        .align    16,0x90
	.type	modfl,@function
	.size	modfl,.-modfl
	.data
# -- End  modfl
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
