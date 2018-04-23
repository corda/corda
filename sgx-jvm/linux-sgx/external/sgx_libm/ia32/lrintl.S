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
	.file "lrintl.c"
	.text
..TXTST0:
# -- Begin  lrintl
	.text
       .align    16,0x90
	.globl lrintl
lrintl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        movzbl    17(%ebp), %edi
        movzwl    16(%ebp), %ebx
        andl      $128, %edi
        andl      $32767, %ebx
        shrl      $7, %edi
        call      ..L2
..L2:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%esi), %esi
        cmpl      $16413, %ebx
        jge       ..B1.8
..B1.2:
        testl     %ebx, %ebx
        jne       ..B1.6
..B1.3:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
..B1.4:
        cmpl      $0, 8(%ebp)
        je        ..B1.6
..B1.5:
        fldl      _smallest_value_64@GOTOFF(%esi)
        fstpl     16(%esp)
..B1.6:
        fldt      8(%ebp)
        fistpl    36(%esp)
..B1.7:
        movl      36(%esp), %eax
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        cmpl      $16415, %ebx
        jge       ..B1.28
..B1.9:
        lea       2147483647(%edi), %eax
        movl      %eax, 12(%esp)
..B1.10:
        fnstcw    32(%esp)
..B1.11:
        movzwl    32(%esp), %eax
        andl      $3072, %eax
        movl      %eax, 28(%esp)
        cmpl      $16414, %ebx
        movl      8(%ebp), %eax
        movl      12(%ebp), %edx
        movl      %eax, 24(%esp)
        jge       ..B1.14
..B1.12:
        cmpl      $0, 24(%esp)
        movl      %edx, %ecx
        movl      $0, %eax
        setne     %al
        shll      $31, %ecx
        orl       %ecx, %eax
        movl      %eax, 24(%esp)
..B1.14:
        movl      %ebx, %ecx
        negl      %ecx
        addl      $30, %ecx
        shrl      %cl, %edx
        cmpl      $0, 28(%esp)
        jne       ..B1.16
..B1.15:
        testl     $2147483647, %eax
        movl      %edi, (%esp)
        movl      %eax, %edi
        movl      $0, %eax
        movl      %eax, %ecx
        setne     %cl
        shrl      $31, %edi
        orl       %edx, %ecx
        andl      %ecx, %edi
        xorl      %ecx, %ecx
        addl      %edi, %edx
        movl      (%esp), %edi
        adcl      $0, %ecx
        jmp       ..B1.24
..B1.16:
        cmpl      $2048, 28(%esp)
        jne       ..B1.18
..B1.17:
        testl     %edi, %edi
        movl      $0, %eax
        setne     %al
        movl      %eax, (%esp)
        jmp       ..B1.19
..B1.18:
        movl      $0, (%esp)
        cmpl      $1024, 28(%esp)
        je        ..B1.20
..B1.19:
        movl      $0, 4(%esp)
        jmp       ..B1.21
..B1.20:
        testl     %edi, %edi
        movl      $0, %eax
        sete      %al
        movl      %eax, 4(%esp)
..B1.21:
        cmpl      $3072, 28(%esp)
        movl      $0, %eax
        sete      %al
        xorl      %ecx, %ecx
        orl       (%esp), %eax
        orl       4(%esp), %eax
        jne       ..B1.24
..B1.23:
        cmpl      $0, 24(%esp)
        movl      $0, %eax
        setne     %al
        addl      %eax, %edx
        adcl      $0, %ecx
..B1.24:
        movl      %edx, %eax
        subl      12(%esp), %eax
        movl      %eax, (%esp)
        movl      $0, %eax
        sbbl      %eax, %ecx
        jb        ..B1.25
..B1.39:
        movl      (%esp), %eax
        orl       %ecx, %eax
        jne       ..B1.28
..B1.25:
        cmpl      $0, 24(%esp)
        je        ..B1.27
..B1.26:
        fldl      _ones@GOTOFF(%esi)
        movl      %edx, 36(%esp)
        faddl     _small_value_64@GOTOFF(%esi)
        fstpl     16(%esp)
..B1.27:
        movl      %edx, %eax
        negl      %eax
        testl     %edi, %edi
        jne       ..L3
        movl      %edx, %eax
..L3:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        fldl      _infs@GOTOFF(%esi)
        cmpl      $32767, %ebx
        fmull     _zeros@GOTOFF(%esi)
        fstpl     16(%esp)
        movl      $-2147483648, 36(%esp)
        jne       ..B1.31
..B1.29:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.36
..B1.30:
        cmpl      $0, 8(%ebp)
        jne       ..B1.36
..B1.31:
        fnstcw    10(%esp)
..B1.32:
        movzwl    10(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.33:
        orl       $-64768, %edx
        movw      %dx, 8(%esp)
..B1.34:
        fldcw     8(%esp)
..B1.35:
        fldcw     10(%esp)
..B1.36:
        movl      36(%esp), %eax
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	lrintl,@function
	.size	lrintl,.-lrintl
	.data
# -- End  lrintl
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
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
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
