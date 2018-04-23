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
	.file "sqrtl.c"
	.text
..TXTST0:
# -- Begin  sqrtl
	.text
       .align    16,0x90
	.globl sqrtl
sqrtl:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        subl      $28, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    40(%esp), %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $32767, %ecx
        je        ..B1.26
..B1.4:
        movzbl    41(%esp), %edx
        testl     $128, %edx
        jne       ..B1.14
..B1.5:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.13
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.7:
        fldcw     12(%esp)
..B1.8:
        movl      $1, %eax
..B1.9:
        fldt      32(%esp)
        fsqrt     
        fstpt     (%esp)
..B1.10:
        testl     %eax, %eax
        je        ..B1.12
..B1.11:
        fldcw     14(%esp)
..B1.12:
        fldt      (%esp)
        addl      $28, %esp
        ret       
..B1.13:
        xorl      %eax, %eax
        jmp       ..B1.9
..B1.14:
        testl     %ecx, %ecx
        jne       ..B1.18
..B1.15:
        cmpl      $0, 36(%esp)
        jne       ..B1.18
..B1.16:
        cmpl      $0, 32(%esp)
        jne       ..B1.18
..B1.17:
        fldt      32(%esp)
        addl      $28, %esp
        ret       
..B1.18:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.25
..B1.19:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.20:
        fldcw     12(%esp)
..B1.21:
        movl      $1, %edx
..B1.22:
        fldl      _infs@GOTOFF(%eax)
        testl     %edx, %edx
        fmull     _zeros@GOTOFF(%eax)
        fstpt     (%esp)
        je        ..B1.24
..B1.23:
        fldcw     14(%esp)
..B1.24:
        fldt      (%esp)
        addl      $28, %esp
        ret       
..B1.25:
        xorl      %edx, %edx
        jmp       ..B1.22
..B1.26:
        cmpl      $-2147483648, 36(%esp)
        jne       ..B1.28
..B1.27:
        cmpl      $0, 32(%esp)
        je        ..B1.4
..B1.28:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.34
..B1.29:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.30:
        fldcw     12(%esp)
..B1.31:
        fldt      32(%esp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
..B1.32:
        fldcw     14(%esp)
..B1.33:
        fldt      (%esp)
        addl      $28, %esp
        ret       
..B1.34:
        fldt      32(%esp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        jmp       ..B1.33
        .align    16,0x90
	.type	sqrtl,@function
	.size	sqrtl,.-sqrtl
	.data
# -- End  sqrtl
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
