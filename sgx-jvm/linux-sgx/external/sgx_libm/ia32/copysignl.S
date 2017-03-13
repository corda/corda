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
	.file "copysignl.c"
	.text
..TXTST0:
# -- Begin  copysignl
	.text
       .align    16,0x90
	.globl copysignl
copysignl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $48, %esp
        fldt      8(%ebp)
        fstpt     24(%esp)
..B1.2:
        fnstcw    38(%esp)
..B1.3:
        movzwl    38(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.10
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 36(%esp)
..B1.5:
        fldcw     36(%esp)
..B1.6:
        movl      $1, %eax
..B1.7:
        fldt      20(%ebp)
        fldt      24(%esp)
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        movzbl    29(%ebp), %ecx
        andl      $-128, %ecx
        fldl      _ones@GOTOFF(%edx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     12(%esp)
        fmulp     %st, %st(1)
        movzbl    21(%esp), %edx
        andl      $127, %edx
        orl       %ecx, %edx
        fstpt     (%esp)
        testl     %eax, %eax
        movb      %dl, 21(%esp)
        je        ..B1.9
..B1.8:
        fldcw     38(%esp)
..B1.9:
        fldt      12(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        xorl      %eax, %eax
        jmp       ..B1.7
        .align    16,0x90
	.type	copysignl,@function
	.size	copysignl,.-copysignl
	.data
# -- End  copysignl
	.section .rodata, "a"
	.align 4
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
