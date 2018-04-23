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
	.file "ldexpl.c"
	.text
..TXTST0:
# -- Begin  ldexpl
	.text
       .align    16,0x90
	.globl ldexpl
ldexpl:
# parameter 1: 32 + %esp
# parameter 2: 44 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $16, %esp
        movl      44(%esp), %ebx
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    40(%esp), %eax
        movl      %eax, %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %ebp
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebp), %ebp
        cmpl      $32767, %ecx
        je        ..B1.43
..B1.4:
        testl     %ecx, %ecx
        jne       ..B1.14
..B1.5:
        cmpl      $0, 36(%esp)
        jne       ..B1.8
..B1.6:
        cmpl      $0, 32(%esp)
        jne       ..B1.8
..B1.7:
        fldt      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.8:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.42
..B1.9:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.10:
        fldcw     12(%esp)
..B1.11:
        fldt      32(%esp)
        fmull     _TWO_75@GOTOFF(%ebp)
        fstpt     32(%esp)
..B1.12:
        fldcw     14(%esp)
..B1.13:
        movzwl    40(%esp), %eax
        movl      %eax, %ecx
        andl      $32767, %ecx
        addl      $-75, %ecx
..B1.14:
        cmpl      $65536, %ebx
        jle       ..L3
        movl      $65536, %ebx
..L3:
        cmpl      $-65536, %ebx
        jg        ..L4
        movl      $-65536, %ebx
..L4:
        lea       (%ecx,%ebx), %edi
        testl     %edi, %edi
        jle       ..B1.25
..B1.15:
        cmpl      $32767, %edi
        jge       ..B1.17
..B1.16:
        andl      $-32768, %eax
        andl      $32767, %edi
        orl       %edi, %eax
        movw      %ax, 40(%esp)
        fldt      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.17:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.24
..B1.18:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.19:
        fldcw     12(%esp)
..B1.20:
        movl      $1, %ebx
..B1.21:
        fldt      _large_value_80@GOTOFF(%ebp)
        movzbl    41(%esp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ebx, %ebx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%ebp,%ecx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.23
..B1.22:
        fldcw     14(%esp)
..B1.23:
        fldt      (%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.24:
        xorl      %ebx, %ebx
        jmp       ..B1.21
..B1.25:
        movzwl    14(%esp), %edx
        cmpl      $-63, %edi
        jl        ..B1.34
..B1.26:
        movl      %edx, %edi
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.33
..B1.27:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.28:
        fldcw     12(%esp)
..B1.29:
        movzwl    40(%esp), %eax
        movl      $1, %edi
..B1.30:
        fldt      32(%esp)
        lea       75(%ecx,%ebx), %edx
        andl      $-32768, %eax
        andl      $32767, %edx
        fstpt     (%esp)
        orl       %edx, %eax
        movw      %ax, 8(%esp)
        testl     %edi, %edi
        fldt      (%esp)
        fmull     8+_TWO_75@GOTOFF(%ebp)
        fstpt     (%esp)
        je        ..B1.32
..B1.31:
        fldcw     14(%esp)
..B1.32:
        fldt      (%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.33:
        xorl      %edi, %edi
        jmp       ..B1.30
..B1.34:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.41
..B1.35:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.36:
        fldcw     12(%esp)
..B1.37:
        movl      $1, %ebx
..B1.38:
        fldt      _small_value_80@GOTOFF(%ebp)
        movzbl    41(%esp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ebx, %ebx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _small_value_80@GOTOFF(%ebp,%ecx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.40
..B1.39:
        fldcw     14(%esp)
..B1.40:
        fldt      (%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.41:
        xorl      %ebx, %ebx
        jmp       ..B1.38
..B1.42:
        fldt      32(%esp)
        fmull     _TWO_75@GOTOFF(%ebp)
        fstpt     32(%esp)
        jmp       ..B1.13
..B1.43:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.49
..B1.44:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.45:
        fldcw     12(%esp)
..B1.46:
        fldt      32(%esp)
        fmull     _ones@GOTOFF(%ebp)
        fstpt     (%esp)
..B1.47:
        fldcw     14(%esp)
..B1.48:
        fldt      (%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.49:
        fldt      32(%esp)
        fmull     _ones@GOTOFF(%ebp)
        fstpt     (%esp)
        jmp       ..B1.48
        .align    16,0x90
	.type	ldexpl,@function
	.size	ldexpl,.-ldexpl
	.data
# -- End  ldexpl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,24
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,24
	.data
	.section .note.GNU-stack, ""
# End
