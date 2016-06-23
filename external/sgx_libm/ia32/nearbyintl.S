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
	.file "nearbyintl.c"
	.text
..TXTST0:
# -- Begin  nearbyintl
	.text
       .align    16,0x90
	.globl nearbyintl
nearbyintl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %ebx
        andl      $32767, %ebx
        movzwl    14(%esp), %edx
        cmpl      $16446, %ebx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        jge       ..B1.97
..B1.4:
        movl      %edx, %esi
        andl      $3072, %esi
        cmpl      $1024, %esi
        je        ..B1.78
..B1.5:
        cmpl      $2048, %esi
        jne       ..B1.25
..B1.6:
        cmpl      $16383, %ebx
        jl        ..B1.19
..B1.7:
        cmpl      $16415, %ebx
        jl        ..B1.9
..B1.8:
        negl      %ebx
        movl      $-1, %esi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %esi
        movl      8(%ebp), %edi
        andl      %edi, %esi
        movl      %esi, 8(%ebp)
        xorl      %edi, %esi
        jmp       ..B1.10
..B1.9:
        negl      %ebx
        movl      $-1, %esi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %esi
        movl      12(%ebp), %edi
        andl      %edi, %esi
        movl      %esi, 12(%ebp)
        xorl      %edi, %esi
        orl       8(%ebp), %esi
        movl      $0, 8(%ebp)
..B1.10:
        movzbl    17(%ebp), %ecx
        testl     $128, %ecx
        jne       ..B1.18
..B1.11:
        testl     %esi, %esi
        je        ..B1.18
..B1.12:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.103
..B1.13:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.14:
        fldcw     12(%esp)
..B1.15:
        fldt      8(%ebp)
        faddl     _ones@GOTOFF(%eax)
        fstpt     (%esp)
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        testl     %ebx, %ebx
        jne       ..B1.24
..B1.20:
        cmpl      $0, 12(%ebp)
        jne       ..B1.23
..B1.21:
        cmpl      $0, 8(%ebp)
        jne       ..B1.23
..B1.22:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.24:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _pone_nzero@GOTOFF(%eax,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        cmpl      $3072, %esi
        jne       ..B1.36
..B1.26:
        cmpl      $16383, %ebx
        jl        ..B1.31
..B1.27:
        cmpl      $16415, %ebx
        jl        ..B1.29
..B1.28:
        negl      %ebx
        movl      $-1, %eax
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %eax
        andl      8(%ebp), %eax
        jmp       ..B1.30
..B1.29:
        negl      %ebx
        movl      $-1, %eax
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %eax
        andl      %eax, 12(%ebp)
        xorl      %eax, %eax
..B1.30:
        movl      %eax, 8(%ebp)
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        testl     %ebx, %ebx
        jne       ..B1.35
..B1.32:
        cmpl      $0, 12(%ebp)
        jne       ..B1.34
..B1.33:
        cmpl      $0, 8(%ebp)
        je        ..B1.35
..B1.34:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.35:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _zeros@GOTOFF(%eax,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        cmpl      $16383, %ebx
        jl        ..B1.70
..B1.37:
        movl      8(%ebp), %edi
        cmpl      $16414, %ebx
        jl        ..B1.39
..B1.38:
        movl      %edi, %esi
        lea       2(%ebx), %ecx
        shll      %cl, %esi
        movl      %esi, 4(%esp)
        movl      $0, (%esp)
        jmp       ..B1.40
..B1.39:
        movl      12(%ebp), %esi
        lea       2(%ebx), %ecx
        shll      %cl, %esi
        movl      %esi, 4(%esp)
        movl      %edi, (%esp)
..B1.40:
        cmpl      $16415, %ebx
        jl        ..B1.42
..B1.41:
        movl      %ebx, %ecx
        movl      $-1, %esi
        negl      %ecx
        addl      $30, %ecx
        shll      %cl, %esi
        andl      %esi, %edi
        movl      %edi, 8(%ebp)
        jmp       ..B1.43
..B1.42:
        movl      %ebx, %ecx
        movl      $-1, %esi
        negl      %ecx
        xorl      %edi, %edi
        addl      $30, %ecx
        shll      %cl, %esi
        movl      %edi, 8(%ebp)
        andl      %esi, 12(%ebp)
..B1.43:
        cmpl      $0, 4(%esp)
        jge       ..B1.69
..B1.44:
        movl      4(%esp), %esi
        andl      $2147483647, %esi
        orl       (%esp), %esi
        je        ..B1.52
..B1.45:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.104
..B1.46:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.47:
        fldcw     12(%esp)
..B1.48:
        movl      $1, %ecx
..B1.49:
        fldt      8(%ebp)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ecx, %ecx
        faddl     _ones@GOTOFF(%eax,%edx,8)
        fstpt     (%esp)
        je        ..B1.51
..B1.50:
        fldcw     14(%esp)
..B1.51:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.52:
        cmpl      $16415, %ebx
        jl        ..B1.61
..B1.53:
        negl      %ebx
        movl      $1, %esi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %esi
        testl     %esi, %edi
        je        ..B1.69
..B1.54:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.105
..B1.55:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.56:
        fldcw     12(%esp)
..B1.57:
        movl      $1, %ecx
..B1.58:
        fldt      8(%ebp)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ecx, %ecx
        faddl     _ones@GOTOFF(%eax,%edx,8)
        fstpt     (%esp)
        je        ..B1.60
..B1.59:
        fldcw     14(%esp)
..B1.60:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        negl      %ebx
        movl      $1, %esi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %esi
        testl     %esi, 12(%ebp)
        je        ..B1.69
..B1.62:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.106
..B1.63:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.64:
        fldcw     12(%esp)
..B1.65:
        movl      $1, %ecx
..B1.66:
        fldt      8(%ebp)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ecx, %ecx
        faddl     _ones@GOTOFF(%eax,%edx,8)
        fstpt     (%esp)
        je        ..B1.68
..B1.67:
        fldcw     14(%esp)
..B1.68:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.69:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.70:
        cmpl      $16382, %ebx
        jle       ..B1.72
..B1.71:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _ones@GOTOFF(%eax,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.72:
        je        ..B1.107
..B1.73:
        testl     %ebx, %ebx
        jne       ..B1.77
..B1.74:
        cmpl      $0, 12(%ebp)
        jne       ..B1.76
..B1.75:
        cmpl      $0, 8(%ebp)
        je        ..B1.77
..B1.76:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.77:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _zeros@GOTOFF(%eax,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        cmpl      $16383, %ebx
        jl        ..B1.91
..B1.79:
        cmpl      $16415, %ebx
        jl        ..B1.81
..B1.80:
        negl      %ebx
        movl      $-1, %edi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %edi
        movl      8(%ebp), %esi
        andl      %esi, %edi
        movl      %edi, 8(%ebp)
        xorl      %edi, %esi
        jmp       ..B1.82
..B1.81:
        negl      %ebx
        movl      $-1, %edi
        addl      $30, %ebx
        movl      %ebx, %ecx
        shll      %cl, %edi
        movl      12(%ebp), %esi
        andl      %esi, %edi
        xorl      %edi, %esi
        orl       8(%ebp), %esi
        movl      %edi, 12(%ebp)
        movl      $0, 8(%ebp)
..B1.82:
        movzbl    17(%ebp), %ecx
        testl     $128, %ecx
        je        ..B1.90
..B1.83:
        testl     %esi, %esi
        je        ..B1.90
..B1.84:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.110
..B1.85:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.86:
        fldcw     12(%esp)
..B1.87:
        fldt      8(%ebp)
        faddl     8+_ones@GOTOFF(%eax)
        fstpt     (%esp)
..B1.88:
        fldcw     14(%esp)
..B1.89:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.90:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.91:
        testl     %ebx, %ebx
        jne       ..B1.96
..B1.92:
        cmpl      $0, 12(%ebp)
        jne       ..B1.95
..B1.93:
        cmpl      $0, 8(%ebp)
        jne       ..B1.95
..B1.94:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.95:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     16(%esp)
..B1.96:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      _pzero_none@GOTOFF(%eax,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.97:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.111
..B1.98:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.99:
        fldcw     12(%esp)
..B1.100:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
..B1.101:
        fldcw     14(%esp)
..B1.102:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.103:
        fldt      8(%ebp)
        faddl     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        jmp       ..B1.17
..B1.104:
        xorl      %ecx, %ecx
        jmp       ..B1.49
..B1.105:
        xorl      %ecx, %ecx
        jmp       ..B1.58
..B1.106:
        xorl      %ecx, %ecx
        jmp       ..B1.66
..B1.107:
        movl      12(%ebp), %edx
        cmpl      $-2147483648, %edx
        ja        ..B1.71
..B1.108:
        jne       ..B1.77
..B1.109:
        cmpl      $0, 8(%ebp)
        ja        ..B1.71
        jmp       ..B1.77
..B1.110:
        fldt      8(%ebp)
        faddl     8+_ones@GOTOFF(%eax)
        fstpt     (%esp)
        jmp       ..B1.89
..B1.111:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%eax)
        fstpt     (%esp)
        jmp       ..B1.102
        .align    16,0x90
	.type	nearbyintl,@function
	.size	nearbyintl,.-nearbyintl
	.data
# -- End  nearbyintl
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
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_pone_nzero:
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_pzero_none:
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.type	_pzero_none,@object
	.size	_pzero_none,16
	.data
	.section .note.GNU-stack, ""
# End
