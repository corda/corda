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
	.file "llrintf.c"
	.text
..TXTST0:
# -- Begin  llrintf
	.text
       .align    16,0x90
	.globl llrintf
llrintf:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        pushl     %ebx
        pushl     %ebp
        subl      $20, %esp
        movl      32(%esp), %ebp
        movl      %ebp, %ebx
        andl      $2147483647, %ebx
        cmpl      $1585446912, %ebx
        jae       ..B1.4
..B1.2:
        flds      32(%esp)
        fistpll   8(%esp)
..B1.3:
        movl      8(%esp), %eax
        movl      12(%esp), %edx
        addl      $20, %esp
        popl      %ebp
        popl      %ebx
        ret       
..B1.4:
        cmpl      $1602224128, %ebx
        jae       ..B1.7
..B1.5:
        shrl      $31, %ebp
        movl      %ebx, %ecx
        shrl      $23, %ecx
        andl      $8388607, %ebx
        movl      %ebp, %eax
        orl       $8388608, %ebx
        xorl      %edx, %edx
        addl      $-1, %eax
        adcl      $2147483647, %edx
        addl      $10, %ecx
        shll      %cl, %ebx
        xorl      %ecx, %ecx
        subl      %eax, %ecx
        movl      %ebx, %eax
        sbbl      %edx, %eax
        jb        ..B1.6
..B1.10:
        orl       %eax, %ecx
        jne       ..B1.7
..B1.6:
        movl      %ebx, %edx
        xorl      %eax, %eax
        negl      %edx
        testl     %ebp, %ebp
        jne       ..L2
        movl      %ebx, %edx
..L2:
        addl      $20, %esp
        popl      %ebp
        popl      %ebx
        ret       
..B1.7:
        call      ..L3
..L3:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L3](%eax), %eax
        movl      $-2147483648, %edx
        movl      $0, 8(%esp)
        movl      $-2147483648, 12(%esp)
        fldl      _infs@GOTOFF(%eax)
        fmull     _zeros@GOTOFF(%eax)
        xorl      %eax, %eax
        fstpl     (%esp)
        addl      $20, %esp
        popl      %ebp
        popl      %ebx
        ret       
        .align    16,0x90
	.type	llrintf,@function
	.size	llrintf,.-llrintf
	.data
# -- End  llrintf
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
