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
	.file "llroundl.c"
	.text
..TXTST0:
# -- Begin  llroundl
	.text
       .align    16,0x90
	.globl llroundl
llroundl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        movzbl    17(%ebp), %ebx
        movzwl    16(%ebp), %eax
        andl      $128, %ebx
        andl      $32767, %eax
        shrl      $7, %ebx
        call      ..L2
..L2:
        popl      %edi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edi), %edi
        cmpl      $16445, %eax
        jge       ..B1.17
..B1.2:
        cmpl      $16415, %eax
        jge       ..B1.16
..B1.3:
        cmpl      $16414, %eax
        jge       ..B1.15
..B1.4:
        cmpl      $16413, %eax
        jge       ..B1.14
..B1.5:
        cmpl      $16383, %eax
        jge       ..B1.13
..B1.6:
        cmpl      $16382, %eax
        jge       ..B1.12
..B1.7:
        testl     %eax, %eax
        jne       ..B1.11
..B1.8:
        cmpl      $0, 12(%ebp)
        jne       ..B1.10
..B1.9:
        cmpl      $0, 8(%ebp)
        je        ..B1.11
..B1.10:
        fldl      _smallest_value_64@GOTOFF(%edi)
        fstpl     8(%esp)
..B1.11:
        xorl      %eax, %eax
        xorl      %edx, %edx
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.12:
        movl      iones@GOTOFF(%edi,%ebx,4), %eax
        cltd      
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        negl      %eax
        movl      12(%ebp), %esi
        movl      %esi, %edi
        lea       30(%eax), %ecx
        addl      $29, %eax
        shrl      %cl, %edi
        movl      %eax, %ecx
        shrl      %cl, %esi
        andl      $1, %esi
        addl      %esi, %edi
        movl      %edi, %eax
        negl      %eax
        testl     %ebx, %ebx
        jne       ..L3
        movl      %edi, %eax
..L3:
        cltd      
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        negl      %eax
        addl      $30, %eax
        movl      12(%ebp), %edx
        movl      %edx, %esi
        movl      %eax, %ecx
        andl      $1, %esi
        shrl      %cl, %edx
        xorl      %eax, %eax
        addl      %edx, %esi
        xorl      %edx, %edx
        subl      %esi, %eax
        sbbl      $0, %edx
        testl     %ebx, %ebx
        jne       ..L4
        movl      %esi, %eax
..L4:
        jne       ..L5
        movl      $0, %edx
..L5:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        movl      8(%ebp), %ecx
        xorl      %esi, %esi
        shrl      $31, %ecx
        xorl      %eax, %eax
        addl      12(%ebp), %ecx
        adcl      $0, %esi
        xorl      %edx, %edx
        subl      %ecx, %eax
        sbbl      %esi, %edx
        testl     %ebx, %ebx
        jne       ..L6
        movl      %ecx, %eax
..L6:
        jne       ..L7
        movl      %esi, %edx
..L7:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        movl      %eax, %esi
        addl      $2, %eax
        negl      %esi
        movl      12(%ebp), %edi
        movl      %esi, (%esp)
        lea       30(%esi), %edx
        movl      %edi, %esi
        movl      %edx, %ecx
        shrl      %cl, %esi
        movl      %eax, %ecx
        shll      %cl, %edi
        movl      %edx, %ecx
        movl      8(%ebp), %edx
        movl      %edx, %eax
        shrl      %cl, %eax
        movl      (%esp), %ecx
        orl       %eax, %edi
        addl      $29, %ecx
        xorl      %eax, %eax
        shrl      %cl, %edx
        andl      $1, %edx
        addl      %edx, %edi
        adcl      $0, %esi
        xorl      %edx, %edx
        subl      %edi, %eax
        sbbl      %esi, %edx
        testl     %ebx, %ebx
        jne       ..L8
        movl      %edi, %eax
..L8:
        jne       ..L9
        movl      %esi, %edx
..L9:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        cmpl      $16447, %eax
        jge       ..B1.29
..B1.18:
        movl      %ebx, %edx
        xorl      %esi, %esi
        addl      $-1, %edx
        movl      %edx, 20(%esp)
        adcl      $2147483647, %esi
        movl      %esi, 16(%esp)
        cmpl      $16446, %eax
        movl      12(%ebp), %edx
        movl      8(%ebp), %esi
        jge       ..B1.21
..B1.19:
        movl      %ebx, (%esp)
        movl      %esi, %ebx
        shrl      $1, %ebx
        movl      %edx, %ecx
        shll      $31, %edx
        andl      $1, %esi
        orl       %ebx, %edx
        shrl      $1, %ecx
        addl      %edx, %esi
        movl      %ecx, %edx
        movl      (%esp), %ebx
        adcl      $0, %edx
..B1.21:
        xorl      %ecx, %ecx
        subl      %esi, %ecx
        movl      %eax, 28(%esp)
        movl      $0, %eax
        sbbl      %edx, %eax
        testl     %ebx, %ebx
        jne       ..L10
        movl      %esi, %ecx
..L10:
        jne       ..L11
        movl      %edx, %eax
..L11:
        movl      %ecx, 24(%esp)
        subl      %esi, %ecx
        movl      %eax, (%esp)
        sbbl      %edx, %eax
        movl      28(%esp), %eax
        jb        ..B1.23
..B1.22:
        movl      $1, %ebx
        jmp       ..B1.24
..B1.23:
        xorl      %ebx, %ebx
..B1.24:
        subl      20(%esp), %esi
        sbbl      16(%esp), %edx
        jb        ..B1.25
..B1.40:
        orl       %edx, %esi
        jne       ..B1.26
..B1.25:
        movl      $1, %edx
        jmp       ..B1.27
..B1.26:
        xorl      %edx, %edx
..B1.27:
        testl     %edx, %ebx
        je        ..B1.29
..B1.28:
        movl      24(%esp), %eax
        movl      (%esp), %edx
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        fldl      _infs@GOTOFF(%edi)
        cmpl      $32767, %eax
        fmull     _zeros@GOTOFF(%edi)
        fstpl     8(%esp)
        jne       ..B1.32
..B1.30:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.37
..B1.31:
        cmpl      $0, 8(%ebp)
        jne       ..B1.37
..B1.32:
        fnstcw    6(%esp)
..B1.33:
        movzwl    6(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.37
..B1.34:
        orl       $-64768, %edx
        movw      %dx, 4(%esp)
..B1.35:
        fldcw     4(%esp)
..B1.36:
        fldcw     6(%esp)
..B1.37:
        xorl      %eax, %eax
        movl      $-2147483648, %edx
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	llroundl,@function
	.size	llroundl,.-llroundl
	.data
# -- End  llroundl
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
