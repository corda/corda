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
	.file "modff.c"
	.text
..TXTST0:
# -- Begin  modff
	.text
       .align    16,0x90
	.globl modff
modff:
# parameter 1: 12 + %esp
# parameter 2: 16 + %esp
..B1.1:
..L1:

        pushl     %ebx
        pushl     %ebp
        movl      12(%esp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        movl      16(%esp), %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        lea       -1065353216(%ecx), %ebp
        cmpl      $192937984, %ebp
        jae       ..B1.6
..B1.2:
        shrl      $23, %ecx
        movl      $-1, %ebp
        negl      %ecx
        addl      $22, %ecx
        shll      %cl, %ebp
        movl      %ebp, %ecx
        andl      %eax, %ebp
        notl      %ecx
        movl      %ebp, (%edx)
        testl     %ecx, %eax
        je        ..B1.4
..B1.3:
        flds      12(%esp)
        fsubs     (%edx)
        popl      %ebp
        popl      %ebx
        ret       
..B1.4:
        shrl      $31, %eax
        flds      _zeros@GOTOFF(%ebx,%eax,4)
..B1.5:
        popl      %ebp
        popl      %ebx
        ret       
..B1.6:
        movl      %eax, %ebp
        shrl      $31, %ebp
        cmpl      $2139095040, %ecx
        ja        ..B1.11
..B1.7:
        cmpl      $1065353216, %ecx
        jbe       ..B1.9
..B1.8:
        flds      _zeros@GOTOFF(%ebx,%ebp,4)
        movl      %eax, (%edx)
        popl      %ebp
        popl      %ebx
        ret       
..B1.9:
        flds      12(%esp)
        movl      _zeros@GOTOFF(%ebx,%ebp,4), %eax
        movl      %eax, (%edx)
..B1.10:
        popl      %ebp
        popl      %ebx
        ret       
..B1.11:
        flds      12(%esp)
        fadd      %st(0), %st
        fsts      (%edx)
        popl      %ebp
        popl      %ebx
        ret       
        .align    16,0x90
	.type	modff,@function
	.size	modff,.-modff
	.data
# -- End  modff
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.data
	.section .note.GNU-stack, ""
# End
