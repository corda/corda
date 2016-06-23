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
	.file "l2q.c"
	.text
..TXTST0:
# -- Begin  __ltoq
	.text
       .align    16,0x90
	.globl __ltoq
__ltoq:
# parameter 1: 32 + %esp
# parameter 2: 36 + %esp
..B1.1:
..L1:

        pushl     %edi
        subl      $24, %esp
..B1.2:
        fnstcw    12(%esp)
..B1.3:
        movzwl    12(%esp), %eax
        orl       $-64768, %eax
        movw      %ax, 14(%esp)
..B1.4:
        fldcw     14(%esp)
..B1.5:
        fldt      36(%esp)
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        fldt      __l_one@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.6:
        fldcw     12(%esp)
..B1.7:
        movl      32(%esp), %edx
        movl      (%esp), %ecx
        movl      %ecx, %edi
        shll      $17, %edi
        movl      %edi, 4(%edx)
        movl      4(%esp), %edi
        movl      %edi, %eax
        shll      $17, %eax
        shrl      $15, %ecx
        shrl      $15, %edi
        orl       %ecx, %eax
        movl      8(%esp), %ecx
        movzwl    %di, %edi
        shll      $16, %ecx
        orl       %ecx, %edi
        movl      $0, (%edx)
        movl      %eax, 8(%edx)
        movl      %edi, 12(%edx)
        addl      $24, %esp
        popl      %edi
        ret       
        .align    16,0x90
	.type	__ltoq,@function
	.size	__ltoq,.-__ltoq
	.data
# -- End  __ltoq
	.section .rodata, "a"
	.align 4
	.align 4
__l_one:
	.long	0
	.long	2147483648
	.long	16383
	.type	__l_one,@object
	.size	__l_one,12
	.data
	.section .note.GNU-stack, ""
# End
