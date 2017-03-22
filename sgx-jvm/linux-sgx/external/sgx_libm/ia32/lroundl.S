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
	.file "lroundl.c"
	.text
..TXTST0:
# -- Begin  lroundl
	.text
       .align    16,0x90
	.globl lroundl
lroundl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $20, %esp
        movzbl    17(%ebp), %edi
        movzwl    16(%ebp), %esi
        andl      $128, %edi
        andl      $32767, %esi
        shrl      $7, %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $16413, %esi
        jge       ..B1.11
..B1.2:
        cmpl      $16383, %esi
        jge       ..B1.10
..B1.3:
        cmpl      $16382, %esi
        jge       ..B1.9
..B1.4:
        testl     %esi, %esi
        jne       ..B1.8
..B1.5:
        cmpl      $0, 12(%ebp)
        jne       ..B1.7
..B1.6:
        cmpl      $0, 8(%ebp)
        je        ..B1.8
..B1.7:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     8(%esp)
..B1.8:
        xorl      %eax, %eax
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        movl      iones@GOTOFF(%ebx,%edi,4), %eax
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        negl      %esi
        movl      12(%ebp), %eax
        movl      %eax, %edx
        lea       30(%esi), %ecx
        addl      $29, %esi
        shrl      %cl, %edx
        movl      %esi, %ecx
        shrl      %cl, %eax
        andl      $1, %eax
        addl      %eax, %edx
        movl      %edx, %eax
        negl      %eax
        testl     %edi, %edi
        jne       ..L3
        movl      %edx, %eax
..L3:
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        cmpl      $16415, %esi
        jge       ..B1.17
..B1.12:
        movl      %esi, %ecx
        lea       2147483647(%edi), %edx
        negl      %ecx
        movl      %edx, 4(%esp)
        addl      $30, %ecx
        movl      12(%ebp), %edx
        movl      %edx, %eax
        shrl      %cl, %eax
        cmpl      $16414, %esi
        jge       ..B1.14
..B1.13:
        andl      $1, %edx
        addl      %eax, %edx
        movl      $0, %eax
        adcl      $0, %eax
        jmp       ..B1.15
..B1.14:
        movl      8(%ebp), %edx
        shrl      $31, %edx
        addl      %eax, %edx
        movl      $0, %eax
        adcl      $0, %eax
..B1.15:
        movl      %edx, %ecx
        subl      4(%esp), %ecx
        movl      %ecx, 16(%esp)
        movl      $0, %ecx
        sbbl      %ecx, %eax
        jb        ..B1.16
..B1.28:
        movl      16(%esp), %ecx
        orl       %eax, %ecx
        jne       ..B1.17
..B1.16:
        movl      %edx, %eax
        negl      %eax
        testl     %edi, %edi
        jne       ..L4
        movl      %edx, %eax
..L4:
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        fldl      _infs@GOTOFF(%ebx)
        cmpl      $32767, %esi
        fmull     _zeros@GOTOFF(%ebx)
        fstpl     8(%esp)
        jne       ..B1.20
..B1.18:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.25
..B1.19:
        cmpl      $0, 8(%ebp)
        jne       ..B1.25
..B1.20:
        fnstcw    2(%esp)
..B1.21:
        movzwl    2(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.25
..B1.22:
        orl       $-64768, %edx
        movw      %dx, (%esp)
..B1.23:
        fldcw     (%esp)
..B1.24:
        fldcw     2(%esp)
..B1.25:
        movl      $-2147483648, %eax
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	lroundl,@function
	.size	lroundl,.-lroundl
	.data
# -- End  lroundl
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
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
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
