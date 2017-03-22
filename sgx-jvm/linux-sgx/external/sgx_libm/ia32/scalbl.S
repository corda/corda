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
	.file "scalbl.c"
	.text
..TXTST0:
# -- Begin  scalbl
	.text
       .align    16,0x90
	.globl scalbl
scalbl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
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
        fnstcw    22(%esp)
..B1.3:
        movzwl    16(%ebp), %edi
        andl      $32767, %edi
        movzwl    28(%ebp), %ecx
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%esi), %esi
        cmpl      $32767, %edi
        je        ..B1.107
..B1.4:
        cmpl      $32767, %ecx
        je        ..B1.74
..B1.5:
        cmpl      $16383, %ecx
        jl        ..B1.33
..B1.6:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.57
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.8:
        fldcw     20(%esp)
..B1.9:
        movl      $1, %ebx
..B1.10:
        cmpl      $16414, %ecx
        jge       ..B1.15
..B1.11:
        fldt      20(%ebp)
        fldl      _TWO_63H@GOTOFF(%esi)
        fadd      %st(1), %st
        fstpt     8(%esp)
        fildl     8(%esp)
        movl      8(%esp), %edx
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B1.12
        je        ..B1.20
..B1.12:
        fldl      _infs@GOTOFF(%esi)
        testl     %ebx, %ebx
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        je        ..B1.14
..B1.13:
        fldcw     22(%esp)
..B1.14:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        movzbl    29(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        cmpl      $16446, %ecx
        movl      large_integers@GOTOFF(%esi,%eax,4), %edx
        jge       ..B1.20
..B1.16:
        addl      $2, %ecx
        movl      20(%ebp), %eax
        shll      %cl, %eax
        testl     %eax, %eax
        je        ..B1.20
..B1.17:
        fldl      _infs@GOTOFF(%esi)
        testl     %ebx, %ebx
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        je        ..B1.19
..B1.18:
        fldcw     22(%esp)
..B1.19:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        testl     %edi, %edi
        jne       ..B1.27
..B1.21:
        cmpl      $0, 12(%ebp)
        jne       ..B1.26
..B1.22:
        cmpl      $0, 8(%ebp)
        jne       ..B1.26
..B1.23:
        testl     %ebx, %ebx
        je        ..B1.25
..B1.24:
        fldcw     22(%esp)
..B1.25:
        fldt      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%esi)
        fstpt     8(%ebp)
        movzwl    16(%ebp), %edi
        andl      $32767, %edi
        addl      $-75, %edi
..B1.27:
        cmpl      $65536, %edx
        jle       ..L3
        movl      $65536, %edx
..L3:
        cmpl      $-65536, %edx
        jge       ..L4
        movl      $-65536, %edx
..L4:
        lea       (%edi,%edx), %ecx
        testl     %ecx, %ecx
        jle       ..B1.51
..B1.28:
        cmpl      $32767, %ecx
        jge       ..B1.50
..B1.29:
        movzwl    16(%ebp), %eax
        andl      $32767, %ecx
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 16(%ebp)
        fldt      8(%ebp)
..B1.30:
        fstpt     8(%esp)
        testl     %ebx, %ebx
        je        ..B1.32
..B1.31:
        fldcw     22(%esp)
..B1.32:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.33:
        testl     %ecx, %ecx
        jne       ..B1.42
..B1.34:
        cmpl      $0, 24(%ebp)
        jne       ..B1.42
..B1.35:
        cmpl      $0, 20(%ebp)
        jne       ..B1.42
..B1.36:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.58
..B1.37:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.38:
        fldcw     20(%esp)
..B1.39:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%esi)
        fstpt     8(%esp)
..B1.40:
        fldcw     22(%esp)
..B1.41:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.42:
        cmpl      $32767, %edi
        je        ..B1.60
..B1.43:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.59
..B1.44:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.45:
        fldcw     20(%esp)
..B1.46:
        movl      $1, %eax
..B1.47:
        fldl      _infs@GOTOFF(%esi)
        testl     %eax, %eax
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        je        ..B1.49
..B1.48:
        fldcw     22(%esp)
..B1.49:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        fldt      _large_value_80@GOTOFF(%esi)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%esi,%ecx)
        fmulp     %st, %st(1)
        jmp       ..B1.30
..B1.51:
        cmpl      $-63, %ecx
        jge       ..B1.53
..B1.52:
        fldt      _small_value_80@GOTOFF(%esi)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _small_value_80@GOTOFF(%esi,%ecx)
        fmulp     %st, %st(1)
        jmp       ..B1.54
..B1.53:
        fldt      8(%ebp)
        lea       75(%edi,%edx), %edx
        movzwl    16(%ebp), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        fstpt     8(%esp)
        orl       %edx, %eax
        movw      %ax, 16(%esp)
        fldt      8(%esp)
        fmull     8+_TWO_75@GOTOFF(%esi)
..B1.54:
        fstpt     8(%esp)
        testl     %ebx, %ebx
        je        ..B1.56
..B1.55:
        fldcw     22(%esp)
..B1.56:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        xorl      %ebx, %ebx
        jmp       ..B1.10
..B1.58:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%esi)
        fstpt     8(%esp)
        jmp       ..B1.41
..B1.59:
        xorl      %eax, %eax
        jmp       ..B1.47
..B1.60:
        testl     %ecx, %ecx
        jg        ..B1.68
..B1.61:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.62:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.63:
        fldcw     20(%esp)
..B1.64:
        movl      $1, %eax
..B1.65:
        fldl      _infs@GOTOFF(%esi)
        testl     %eax, %eax
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        je        ..B1.67
..B1.66:
        fldcw     22(%esp)
..B1.67:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.68:
        cmpl      $1, %ecx
        je        ..B1.71
..B1.69:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      _infs@GOTOFF(%esi,%eax,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.70:
        xorl      %eax, %eax
        jmp       ..B1.65
..B1.71:
        movl      24(%ebp), %eax
        cmpl      $-2147483648, %eax
        jb        ..B1.61
..B1.72:
        jne       ..B1.69
..B1.73:
        cmpl      $0, 20(%ebp)
        jbe       ..B1.61
        jmp       ..B1.69
..B1.74:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.76
..B1.75:
        cmpl      $0, 20(%ebp)
        je        ..B1.82
..B1.76:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.102
..B1.77:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.78:
        fldcw     20(%esp)
..B1.79:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     8(%esp)
..B1.80:
        fldcw     22(%esp)
..B1.81:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.82:
        movzwl    22(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.106
..B1.83:
        orl       $-64768, %edx
        movw      %dx, 20(%esp)
..B1.84:
        fldcw     20(%esp)
..B1.85:
        movl      $1, %eax
..B1.86:
        testl     %edi, %edi
        jne       ..B1.94
..B1.87:
        cmpl      $0, 12(%ebp)
        jne       ..B1.89
..B1.88:
        cmpl      $0, 8(%ebp)
        je        ..B1.90
..B1.112:
        cmpl      $0, 12(%ebp)
..B1.89:
        fldl      _smallest_value_64@GOTOFF(%esi)
        fstpl     (%esp)
        jne       ..B1.95
..B1.113:
        cmpl      $0, 8(%ebp)
..B1.90:
        jne       ..B1.95
..B1.91:
        movzbl    29(%ebp), %edx
        testl     $128, %edx
        je        ..B1.93
..B1.92:
        fldt      8(%ebp)
        fstpt     8(%esp)
        jmp       ..B1.99
..B1.93:
        fldl      _infs@GOTOFF(%esi)
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        jmp       ..B1.99
..B1.94:
        cmpl      $32767, %edi
        je        ..B1.103
..B1.95:
        movzbl    17(%ebp), %ecx
        andl      $128, %ecx
        movzbl    29(%ebp), %edx
        shrl      $7, %ecx
        testl     $128, %edx
        je        ..B1.97
..B1.96:
        fldl      _zeros@GOTOFF(%esi,%ecx,8)
        jmp       ..B1.98
..B1.97:
        fldl      _infs@GOTOFF(%esi,%ecx,8)
..B1.98:
        fstpt     8(%esp)
..B1.99:
        testl     %eax, %eax
        je        ..B1.101
..B1.100:
        fldcw     22(%esp)
..B1.101:
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.102:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     8(%esp)
        jmp       ..B1.81
..B1.103:
        movzbl    29(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.105
..B1.104:
        fldt      8(%ebp)
        fstpt     8(%esp)
        jmp       ..B1.99
..B1.105:
        fldl      _infs@GOTOFF(%esi)
        fmull     _zeros@GOTOFF(%esi)
        fstpt     8(%esp)
        jmp       ..B1.99
..B1.106:
        xorl      %eax, %eax
        jmp       ..B1.86
..B1.107:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.76
..B1.108:
        cmpl      $0, 8(%ebp)
        jne       ..B1.76
..B1.109:
        cmpl      $32767, %ecx
        je        ..B1.74
        jmp       ..B1.33
        .align    16,0x90
	.type	scalbl,@function
	.size	scalbl,.-scalbl
	.data
# -- End  scalbl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
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
large_integers:
	.long	65536
	.long	-65536
	.type	large_integers,@object
	.size	large_integers,8
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
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
