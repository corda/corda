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
	.file "nexttowardl.c"
	.text
..TXTST0:
# -- Begin  nexttowardl
	.text
       .align    16,0x90
	.globl nexttowardl
nexttowardl:
# parameter 1: 48 + %esp
# parameter 2: 60 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        subl      $36, %esp
..B1.2:
        fnstcw    26(%esp)
..B1.3:
        movzwl    56(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.54
..B1.4:
        movzwl    68(%esp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.45
..B1.5:
        fldt      48(%esp)
        fldt      60(%esp)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.6
        je        ..B1.56
..B1.6:
        fstp      %st(0)
        call      ..L2
..L2:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%esi), %esi
        testl     %ecx, %ecx
        jne       ..B1.9
..B1.7:
        cmpl      $0, 52(%esp)
        jne       ..B1.9
..B1.8:
        cmpl      $0, 48(%esp)
        je        ..B1.44
..B1.9:
        movzbl    69(%esp), %eax
        movzbl    57(%esp), %edi
        andl      $128, %eax
        andl      $128, %edi
        shrl      $7, %eax
        shrl      $7, %edi
        fstpt     12(%esp)
        cmpl      %edi, %eax
        jne       ..B1.15
..B1.10:
        cmpl      %edx, %ecx
        jg        ..B1.15
..B1.11:
        jne       ..B1.29
..B1.12:
        movl      52(%esp), %eax
        movl      64(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B1.15
..B1.13:
        jne       ..B1.29
..B1.14:
        movl      48(%esp), %eax
        cmpl      60(%esp), %eax
        jbe       ..B1.29
..B1.15:
        movzwl    20(%esp), %ecx
        movl      %ecx, %edx
        movl      12(%esp), %eax
        andl      $32767, %edx
        decl      %eax
        movl      %eax, 12(%esp)
        cmpl      $-1, %eax
        jne       ..B1.34
..B1.16:
        movl      16(%esp), %eax
        decl      %eax
        testl     %edx, %edx
        je        ..B1.22
..B1.17:
        cmpl      $2147483647, %eax
        jne       ..B1.21
..B1.18:
        decl      %edx
        andl      $-32768, %ecx
        andl      $32767, %edx
        orl       %edx, %ecx
        movw      %cx, 20(%esp)
        testl     %edx, %edx
        je        ..B1.20
..B1.19:
        movl      %ecx, %edx
        orl       $-2147483648, %eax
        movl      %eax, 16(%esp)
        andl      $32767, %edx
        jmp       ..B1.34
..B1.20:
        movl      %ecx, %edx
        movl      %eax, 16(%esp)
        andl      $32767, %edx
        jmp       ..B1.34
..B1.21:
        movl      %eax, 16(%esp)
        jmp       ..B1.35
..B1.22:
        movl      %eax, 16(%esp)
..B1.23:
        movzwl    26(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.24:
        orl       $-64768, %edx
        movw      %dx, 24(%esp)
..B1.25:
        fldcw     24(%esp)
..B1.26:
        fldt      _small_value_80@GOTOFF(%esi)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.27:
        fldcw     26(%esp)
..B1.28:
        fldt      12(%esp)
        addl      $36, %esp
        popl      %edi
        popl      %esi
        ret       
..B1.29:
        movzwl    20(%esp), %eax
        movl      %eax, %edx
        movl      12(%esp), %ecx
        andl      $32767, %edx
        incl      %ecx
        movl      %ecx, 12(%esp)
        jne       ..B1.33
..B1.30:
        movl      $-2147483648, %ecx
        testl     %edx, %edx
        movl      16(%esp), %edi
        je        ..L3
        movl      $0, %ecx
..L3:
        incl      %edi
        cmpl      %ecx, %edi
        jne       ..B1.32
..B1.31:
        incl      %edx
        andl      $-32768, %eax
        andl      $32767, %edx
        orl       $-2147483648, %edi
        orl       %edx, %eax
        movl      %eax, %edx
        movl      %edi, 16(%esp)
        andl      $32767, %edx
        movw      %ax, 20(%esp)
        jmp       ..B1.33
..B1.32:
        movl      %edi, 16(%esp)
..B1.33:
        cmpl      $32767, %edx
        je        ..B1.37
..B1.34:
        testl     %edx, %edx
        je        ..B1.23
..B1.35:
        fldt      12(%esp)
        addl      $36, %esp
        popl      %edi
        popl      %esi
        ret       
..B1.36:
        fldt      _small_value_80@GOTOFF(%esi)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.28
..B1.37:
        movzwl    26(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.43
..B1.38:
        orl       $-64768, %edx
        movw      %dx, 24(%esp)
..B1.39:
        fldcw     24(%esp)
..B1.40:
        fldt      _large_value_80@GOTOFF(%esi)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.41:
        fldcw     26(%esp)
..B1.42:
        fldt      12(%esp)
        addl      $36, %esp
        popl      %edi
        popl      %esi
        ret       
..B1.43:
        fldt      _large_value_80@GOTOFF(%esi)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.42
..B1.44:
        fstp      %st(0)
        movzbl    69(%esp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _smallest_value_80@GOTOFF(%esi,%ecx)
        movzwl    8+_smallest_value_80@GOTOFF(%esi,%ecx), %edx
        fstpt     12(%esp)
        andl      $32767, %edx
        jmp       ..B1.34
..B1.45:
        cmpl      $-2147483648, 64(%esp)
        jne       ..B1.47
..B1.46:
        cmpl      $0, 60(%esp)
        je        ..B1.5
..B1.47:
        movzwl    26(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.53
..B1.48:
        orl       $-64768, %edx
        movw      %dx, 24(%esp)
..B1.49:
        fldcw     24(%esp)
..B1.50:
        fldt      48(%esp)
        fldt      60(%esp)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
..B1.51:
        fldcw     26(%esp)
..B1.52:
        fldt      12(%esp)
        addl      $36, %esp
        popl      %edi
        popl      %esi
        ret       
..B1.53:
        fldt      48(%esp)
        fldt      60(%esp)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        jmp       ..B1.52
..B1.54:
        cmpl      $-2147483648, 52(%esp)
        jne       ..B1.47
..B1.55:
        cmpl      $0, 48(%esp)
        jne       ..B1.47
        jmp       ..B1.4
..B1.56:
        fstp      %st(1)
        addl      $36, %esp
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	nexttowardl,@function
	.size	nexttowardl,.-nexttowardl
	.data
# -- End  nexttowardl
	.section .rodata, "a"
	.align 4
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
_smallest_value_80:
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	1
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.type	_smallest_value_80,@object
	.size	_smallest_value_80,24
	.data
	.section .note.GNU-stack, ""
# End
