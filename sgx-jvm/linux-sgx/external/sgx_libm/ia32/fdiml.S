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
	.file "fdiml.c"
	.text
..TXTST0:
# -- Begin  fdiml
	.text
       .align    16,0x90
	.globl fdiml
fdiml:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $24, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        movzwl    28(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %ecx
        je        ..B1.39
..B1.4:
        cmpl      $32767, %edx
        je        ..B1.30
..B1.5:
        movzwl    14(%esp), %ebx
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.6:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.7:
        fldcw     12(%esp)
..B1.8:
        movl      $1, %edi
..B1.9:
        fldt      20(%ebp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fldl      _zeros@GOTOFF(%ebx)
        fstpt     (%esp)
        fldt      8(%ebp)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.43
..B1.10:
        cmpl      $32767, %ecx
        je        ..B1.14
..B1.11:
        cmpl      $32767, %edx
        je        ..B1.14
..B1.12:
        cmpl      $32766, %ecx
        je        ..B1.26
..B1.13:
        cmpl      $32766, %edx
        je        ..B1.18
..B1.14:
        fsubp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.43:
        fstp      %st(0)
        fstp      %st(0)
..B1.15:
        testl     %edi, %edi
        je        ..B1.17
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        testl     %ecx, %ecx
        jne       ..B1.22
..B1.19:
        cmpl      $0, 12(%ebp)
        jne       ..B1.21
..B1.20:
        cmpl      $0, 8(%ebp)
        je        ..B1.22
..B1.21:
        fldt      _ranges@GOTOFF(%ebx)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fucompp   
        fnstsw    %ax
        sahf      
        jne       ..B1.15
        jp        ..B1.15
        jmp       ..B1.24
..B1.22:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fldt      12+_ranges@GOTOFF(%ebx)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.23
        jbe       ..B1.25
..B1.23:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.24:
        fldt      _large_value_80@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.15
..B1.25:
        fstp      %st(0)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.15
..B1.26:
        testl     %edx, %edx
        jne       ..B1.22
..B1.27:
        cmpl      $0, 24(%ebp)
        jne       ..B1.21
..B1.28:
        cmpl      $0, 20(%ebp)
        jne       ..B1.21
        jmp       ..B1.22
..B1.29:
        xorl      %edi, %edi
        jmp       ..B1.9
..B1.30:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.32
..B1.31:
        cmpl      $0, 20(%ebp)
        je        ..B1.5
..B1.32:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.33:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.34:
        fldcw     12(%esp)
..B1.35:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.36:
        fldcw     14(%esp)
..B1.37:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.37
..B1.39:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.32
..B1.40:
        cmpl      $0, 8(%ebp)
        je        ..B1.4
        jmp       ..B1.32
        .align    16,0x90
	.type	fdiml,@function
	.size	fdiml,.-fdiml
	.data
# -- End  fdiml
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
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
	.data
	.section .note.GNU-stack, ""
# End
