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
	.file "llrint.c"
	.text
..TXTST0:
# -- Begin  llrint
	.text
       .align    16,0x90
	.globl llrint
llrint:
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
        movl      12(%ebp), %ebx
        movl      %ebx, %edi
        andl      $2147483647, %edi
        cmpl      $1137704960, %edi
        jae       ..B1.4
..B1.2:
        fldl      8(%ebp)
        fistpll   16(%esp)
..B1.3:
        movl      16(%esp), %eax
        movl      20(%esp), %edx
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.4:
        cmpl      $1139802112, %edi
        jae       ..B1.7
..B1.5:
        movl      %edi, %edx
        andl      $1048575, %edi
        shrl      $20, %edx
        orl       $1048576, %edi
        movl      8(%ebp), %esi
        xorl      %eax, %eax
        shrl      $31, %ebx
        movl      %ebx, (%esp)
        addl      $-1, %ebx
        lea       13(%edx), %ecx
        movl      %ecx, 4(%esp)
        adcl      $2147483647, %eax
        negl      %edx
        addl      $19, %edx
        shll      %cl, %edi
        movl      %edx, %ecx
        movl      %esi, %edx
        shrl      %cl, %edx
        movl      4(%esp), %ecx
        orl       %edx, %edi
        shll      %cl, %esi
        movl      %edi, %ecx
        movl      %esi, %edx
        subl      %ebx, %edx
        movl      (%esp), %ebx
        sbbl      %eax, %ecx
        jb        ..B1.6
..B1.10:
        orl       %ecx, %edx
        jne       ..B1.7
..B1.6:
        xorl      %eax, %eax
        xorl      %edx, %edx
        subl      %esi, %eax
        sbbl      %edi, %edx
        testl     %ebx, %ebx
        jne       ..L2
        movl      %esi, %eax
..L2:
        jne       ..L3
        movl      %edi, %edx
..L3:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.7:
        call      ..L4
..L4:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%eax), %eax
        movl      $-2147483648, %edx
        movl      $0, 16(%esp)
        movl      $-2147483648, 20(%esp)
        fldl      _infs@GOTOFF(%eax)
        fmull     _zeros@GOTOFF(%eax)
        xorl      %eax, %eax
        fstpl     8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	llrint,@function
	.size	llrint,.-llrint
	.data
# -- End  llrint
	.section .rodata, "a"
	.align 4
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
