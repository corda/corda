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
	.file "fmodl.c"
	.text
..TXTST0:
# -- Begin  fmodl
	.text
       .align    16,0x90
	.globl fmodl
fmodl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        subl      $28, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    28(%ebp), %edx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        andl      $32767, %edx
        jne       ..B1.6
..B1.4:
        cmpl      $0, 24(%ebp)
        jne       ..B1.6
..B1.5:
        cmpl      $0, 20(%ebp)
        je        ..B1.45
..B1.6:
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.42
..B1.7:
        cmpl      $32767, %edx
        je        ..B1.17
..B1.8:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.16
..B1.9:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.10:
        fldcw     12(%esp)
..B1.11:
        movl      $1, %esi
..B1.12:
        addl      $-16, %esp
        lea       8(%ebp), %eax
        movl      %eax, (%esp)
        call      __fmodl
..B1.60:
        fstpt     16(%esp)
        addl      $16, %esp
..B1.13:
        testl     %esi, %esi
        je        ..B1.15
..B1.14:
        fldcw     14(%esp)
..B1.15:
        fldt      (%esp)
        addl      $28, %esp
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        xorl      %esi, %esi
        jmp       ..B1.12
..B1.17:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.19
..B1.18:
        cmpl      $0, 20(%ebp)
        je        ..B1.25
..B1.19:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.31
..B1.20:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.21:
        fldcw     12(%esp)
..B1.22:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.23:
        fldcw     14(%esp)
..B1.24:
        fldt      (%esp)
        addl      $28, %esp
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        cmpl      $32767, %ecx
        je        ..B1.32
..B1.26:
        testl     %ecx, %ecx
        jne       ..B1.30
..B1.27:
        cmpl      $0, 12(%ebp)
        jne       ..B1.29
..B1.28:
        cmpl      $0, 8(%ebp)
        je        ..B1.30
..B1.29:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.30:
        fldt      8(%ebp)
        addl      $28, %esp
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.24
..B1.32:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.30
..B1.33:
        cmpl      $0, 8(%ebp)
        jne       ..B1.30
..B1.34:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.41
..B1.35:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.36:
        fldcw     12(%esp)
..B1.37:
        movl      $1, %edx
..B1.38:
        fldl      _infs@GOTOFF(%eax)
        testl     %edx, %edx
        fmull     _zeros@GOTOFF(%eax)
        fstpl     (%esp)
        je        ..B1.40
..B1.39:
        fldcw     14(%esp)
..B1.40:
        fldl      (%esp)
        addl      $28, %esp
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        xorl      %edx, %edx
        jmp       ..B1.38
..B1.42:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.19
..B1.43:
        cmpl      $0, 8(%ebp)
        jne       ..B1.19
..B1.44:
        cmpl      $32767, %edx
        je        ..B1.17
        jmp       ..B1.25
..B1.45:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.57
..B1.46:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.47:
        fldcw     12(%esp)
..B1.48:
        movl      $1, %ecx
..B1.49:
        movzwl    16(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jne       ..B1.53
..B1.50:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.52
..B1.51:
        cmpl      $0, 8(%ebp)
        je        ..B1.53
..B1.52:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        jmp       ..B1.54
..B1.53:
        fldl      _infs@GOTOFF(%eax)
        fmull     _zeros@GOTOFF(%eax)
        fstpt     (%esp)
..B1.54:
        testl     %ecx, %ecx
        je        ..B1.56
..B1.55:
        fldcw     14(%esp)
..B1.56:
        fldt      (%esp)
        addl      $28, %esp
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        xorl      %ecx, %ecx
        jmp       ..B1.49
        .align    16,0x90
	.type	fmodl,@function
	.size	fmodl,.-fmodl
	.data
# -- End  fmodl
	.text
# -- Begin  __fmodl
	.text
       .align    16,0x90
__fmodl:
# parameter 1: 4 + %esp
..B2.1:
..L3:

..B2.2:
        movl      4(%esp), %ecx
        fldt      12(%ecx)
        fldt      (%ecx)
.L_2TAG_PACKET_0.0.2:
        fprem     
        fstsw     %ax
        sahf      
        jp        .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        ret       
..B2.3:
        .align    16,0x90
	.type	__fmodl,@function
	.size	__fmodl,.-__fmodl
	.data
# -- End  __fmodl
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
