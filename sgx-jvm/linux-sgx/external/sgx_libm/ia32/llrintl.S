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
	.file "llrintl.c"
	.text
..TXTST0:
# -- Begin  llrintl
	.text
       .align    16,0x90
	.globl llrintl
llrintl:
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
        movzbl    17(%ebp), %eax
        movzwl    16(%ebp), %edx
        andl      $128, %eax
        andl      $32767, %edx
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $16445, %edx
        jge       ..B1.8
..B1.2:
        testl     %edx, %edx
        jne       ..B1.6
..B1.3:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
..B1.4:
        cmpl      $0, 8(%ebp)
        je        ..B1.6
..B1.5:
        fldl      _smallest_value_64@GOTOFF(%ecx)
        fstpl     24(%esp)
..B1.6:
        fldt      8(%ebp)
        fistpll   40(%esp)
..B1.7:
        movl      40(%esp), %eax
        movl      44(%esp), %edx
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        cmpl      $16447, %edx
        jge       ..B1.35
..B1.9:
        movl      %eax, %ebx
        xorl      %esi, %esi
        addl      $-1, %ebx
        movl      %ebx, 36(%esp)
        adcl      $2147483647, %esi
        movl      %esi, 32(%esp)
        cmpl      $16446, %edx
        jge       ..B1.21
..B1.10:
        fnstcw    20(%esp)
..B1.11:
        movl      8(%ebp), %ebx
        movzwl    20(%esp), %esi
        movl      %ebx, 12(%esp)
        andl      $1, %ebx
        movl      %ebx, 8(%esp)
        andl      $3072, %esi
        jne       ..B1.13
..B1.12:
        movl      12(%esp), %esi
        movl      %eax, (%esp)
        movl      %esi, %eax
        movl      12(%ebp), %edi
        movl      %edi, %ebx
        shll      $31, %edi
        andl      $3, %esi
        shrl      $1, %eax
        orl       %eax, %edi
        cmpl      $3, %esi
        movl      $0, %eax
        sete      %al
        shrl      $1, %ebx
        addl      %eax, %edi
        movl      (%esp), %eax
        adcl      $0, %ebx
        jmp       ..B1.22
..B1.13:
        cmpl      $2048, %esi
        jne       ..B1.15
..B1.14:
        xorl      %ebx, %ebx
        testl     %eax, %eax
        setne     %bl
        movl      %ebx, (%esp)
        jmp       ..B1.16
..B1.15:
        movl      $0, (%esp)
        cmpl      $1024, %esi
        je        ..B1.17
..B1.16:
        movl      $0, 4(%esp)
        jmp       ..B1.18
..B1.17:
        xorl      %ebx, %ebx
        testl     %eax, %eax
        sete      %bl
        movl      %ebx, 4(%esp)
..B1.18:
        cmpl      $3072, %esi
        movl      $0, %ebx
        sete      %bl
        orl       (%esp), %ebx
        movl      12(%ebp), %edi
        orl       4(%esp), %ebx
        je        ..B1.20
..B1.19:
        movl      12(%esp), %esi
        movl      %edi, %ebx
        shll      $31, %edi
        shrl      $1, %esi
        shrl      $1, %ebx
        orl       %esi, %edi
        jmp       ..B1.22
..B1.20:
        movl      12(%esp), %esi
        movl      %edi, %ebx
        movl      %eax, (%esp)
        movl      %esi, %eax
        shll      $31, %edi
        andl      $1, %esi
        shrl      $1, %eax
        orl       %eax, %edi
        shrl      $1, %ebx
        addl      %esi, %edi
        movl      (%esp), %eax
        adcl      $0, %ebx
        jmp       ..B1.22
..B1.21:
        movl      12(%ebp), %ebx
        movl      8(%ebp), %edi
        movl      $0, 8(%esp)
..B1.22:
        testl     %eax, %eax
        je        ..B1.24
..B1.23:
        xorl      %eax, %eax
        xorl      %esi, %esi
        subl      %edi, %eax
        movl      %eax, (%esp)
        sbbl      %ebx, %esi
        movl      %esi, 12(%esp)
        movl      %esi, 4(%esp)
        jmp       ..B1.25
..B1.24:
        movl      %ebx, 12(%esp)
        movl      %edi, %eax
        movl      %edi, (%esp)
        movl      %ebx, 4(%esp)
..B1.25:
        movl      %eax, %esi
        subl      %edi, %esi
        movl      12(%esp), %esi
        sbbl      %ebx, %esi
        jb        ..B1.27
..B1.26:
        movl      $1, %esi
        jmp       ..B1.28
..B1.27:
        xorl      %esi, %esi
..B1.28:
        subl      36(%esp), %edi
        sbbl      32(%esp), %ebx
        jb        ..B1.29
..B1.46:
        orl       %ebx, %edi
        jne       ..B1.30
..B1.29:
        movl      $1, %ebx
        jmp       ..B1.31
..B1.30:
        xorl      %ebx, %ebx
..B1.31:
        testl     %ebx, %esi
        je        ..B1.35
..B1.32:
        cmpl      $0, 8(%esp)
        je        ..B1.34
..B1.33:
        fldl      _ones@GOTOFF(%ecx)
        movl      (%esp), %eax
        movl      4(%esp), %edx
        movl      %eax, 40(%esp)
        movl      %edx, 44(%esp)
        movl      %edx, 12(%esp)
        faddl     _small_value_64@GOTOFF(%ecx)
        fstpl     24(%esp)
..B1.34:
        movl      12(%esp), %edx
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.35:
        fldl      _infs@GOTOFF(%ecx)
        cmpl      $32767, %edx
        fmull     _zeros@GOTOFF(%ecx)
        fstpl     24(%esp)
        movl      $0, 40(%esp)
        movl      $-2147483648, 44(%esp)
        jne       ..B1.38
..B1.36:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.43
..B1.37:
        cmpl      $0, 8(%ebp)
        jne       ..B1.43
..B1.38:
        fnstcw    18(%esp)
..B1.39:
        movzwl    18(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.43
..B1.40:
        orl       $-64768, %edx
        movw      %dx, 16(%esp)
..B1.41:
        fldcw     16(%esp)
..B1.42:
        fldcw     18(%esp)
..B1.43:
        movl      40(%esp), %eax
        movl      44(%esp), %edx
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	llrintl,@function
	.size	llrintl,.-llrintl
	.data
# -- End  llrintl
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
