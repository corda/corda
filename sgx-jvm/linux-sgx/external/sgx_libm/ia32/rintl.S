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
	.file "rintl.c"
	.text
..TXTST0:
# -- Begin  rintl
	.text
       .align    16,0x90
	.globl rintl
rintl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzbl    17(%ebp), %edx
        movzwl    16(%ebp), %ecx
        andl      $128, %edx
        andl      $32767, %ecx
        shrl      $7, %edx
        movzwl    14(%esp), %eax
        cmpl      $16446, %ecx
        jge       ..B1.13
..B1.4:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.20
..B1.5:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.6:
        fldcw     12(%esp)
..B1.7:
        movl      $1, %eax
..B1.8:
        fldt      8(%ebp)
        testl     %eax, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        fldl      _TWO_63@GOTOFF(%ecx,%edx,8)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     8(%ebp)
        je        ..B1.10
..B1.9:
        fldcw     14(%esp)
..B1.10:
        fldt      8(%ebp)
        fldl      _zeros@GOTOFF(%ecx)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.12
        je        ..B1.19
..B1.12:
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.21
..B1.14:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.15:
        fldcw     12(%esp)
..B1.16:
        fldt      8(%ebp)
        fstpt     (%esp)
..B1.17:
        fldcw     14(%esp)
..B1.18:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        fstp      %st(0)
        fldl      _zeros@GOTOFF(%ecx,%edx,8)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        xorl      %eax, %eax
        jmp       ..B1.8
..B1.21:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.18
        .align    16,0x90
	.type	rintl,@function
	.size	rintl,.-rintl
	.data
# -- End  rintl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63:
	.long	0
	.long	1138753536
	.long	0
	.long	3286237184
	.type	_TWO_63,@object
	.size	_TWO_63,16
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
