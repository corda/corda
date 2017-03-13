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
	.file "csinhf.c"
	.text
..TXTST0:
# -- Begin  csinhf
	.text
       .align    16,0x90
	.globl csinhf
csinhf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        xorl      %ecx, %ecx
        movl      %ecx, 48(%esp)
        movl      %ecx, 52(%esp)
..B1.2:
        fnstcw    38(%esp)
..B1.3:
        movzwl    10(%ebp), %esi
        andl      $32640, %esi
        shrl      $7, %esi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %esi
        jge       ..B1.18
..B1.4:
        testl     %esi, %esi
        jle       ..B1.47
..B1.5:
        movzwl    14(%ebp), %edi
        andl      $32640, %edi
        shrl      $7, %edi
        cmpl      $255, %edi
        jge       ..B1.19
..B1.6:
        testl     %edi, %edi
        jle       ..B1.46
..B1.7:
        cmpl      $138, %esi
        jl        ..B1.11
..B1.8:
        fldl      _DBL_MAX_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     (%esp)
        flds      12(%ebp)
        fsts      8(%esp)
        addl      $-16, %esp
        fstps     (%esp)
        call      cosf@PLT
..B1.51:
        addl      $16, %esp
        fmull     (%esp)
        testl     %edi, %edi
        fmuls     8(%ebp)
        fstps     48(%esp)
        jle       ..B1.10
..B1.9:
        addl      $-16, %esp
        flds      24(%esp)
        fstps     (%esp)
        call      sinf@PLT
..B1.52:
        addl      $16, %esp
        fldl      (%esp)
        fmulp     %st, %st(1)
        fstps     52(%esp)
        jmp       ..B1.39
..B1.10:
        fldl      (%esp)
        fmuls     8(%esp)
        fstps     52(%esp)
        jmp       ..B1.39
..B1.11:
        movzwl    38(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.12:
        orl       $-64768, %edx
        movw      %dx, 36(%esp)
..B1.13:
        fldcw     36(%esp)
..B1.14:
        movl      $1, %esi
..B1.15:
        flds      8(%ebp)
        fstps     32(%esp)
        flds      12(%ebp)
        fsts      24(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      sin@PLT
..B1.55:
        addl      $16, %esp
        fstpl     16(%esp)
        addl      $-16, %esp
        flds      40(%esp)
        fstpl     (%esp)
        call      cos@PLT
..B1.54:
        addl      $16, %esp
        fstpl     24(%esp)
        addl      $-16, %esp
        flds      48(%esp)
        lea       16(%esp), %eax
        lea       24(%esp), %edx
        fstpl     (%esp)
        movl      %eax, 8(%esp)
        movl      %edx, 12(%esp)
        call      sinhcosh@PLT
..B1.53:
        addl      $16, %esp
..B1.16:
        fldl      (%esp)
        testl     %esi, %esi
        fmull     24(%esp)
        fstps     48(%esp)
        fldl      8(%esp)
        fmull     16(%esp)
        fstps     52(%esp)
        je        ..B1.39
..B1.17:
        fldcw     38(%esp)
        jmp       ..B1.39
..B1.18:
        movzwl    14(%ebp), %edi
        andl      $32640, %edi
        shrl      $7, %edi
..B1.19:
        testl     %edi, %edi
        jne       ..B1.22
..B1.20:
        testl     $8388607, 12(%ebp)
        jne       ..B1.22
..B1.21:
        addl      $-16, %esp
        movl      8(%ebp), %eax
        movl      %eax, (%esp)
        call      sinhf@PLT
..B1.56:
        movl      12(%ebp), %eax
        addl      $16, %esp
        fstps     48(%esp)
        movl      %eax, 52(%esp)
        jmp       ..B1.39
..B1.22:
        cmpl      $255, %edi
        jge       ..B1.34
..B1.23:
        movl      8(%ebp), %eax
        andl      $8388607, %eax
        testl     %esi, %esi
        jne       ..B1.63
..B1.24:
        testl     %eax, %eax
        jne       ..B1.29
..B1.25:
        flds      12(%ebp)
        addl      $-16, %esp
        fsts      (%esp)
        fstps     16(%esp)
        call      cosf@PLT
..B1.57:
        flds      16(%esp)
        addl      $16, %esp
        fxch      %st(1)
        fmuls     8(%ebp)
        testl     %edi, %edi
        fstps     48(%esp)
        jle       ..B1.28
..B1.26:
        addl      $-16, %esp
        fstps     (%esp)
        call      sinf@PLT
..B1.58:
        addl      $16, %esp
..B1.28:
        fldl      _DBL_MAX_NORMAL@GOTOFF(%ebx)
        fxch      %st(1)
        fstps     52(%esp)
        fsubl     _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fstpl     40(%esp)
        jmp       ..B1.39
..B1.63:
        testl     %eax, %eax
..B1.29:
        flds      8(%ebp)
        jne       ..B1.33
..B1.30:
        fld       %st(0)
        cmpl      $127, %edi
        fmul      %st(1), %st
        fstps     8(%esp)
        jge       ..B1.32
..B1.31:
        flds      8(%esp)
        fxch      %st(1)
        fstps     48(%esp)
        fmuls     12(%ebp)
        fstps     52(%esp)
        jmp       ..B1.39
..B1.32:
        addl      $-16, %esp
        movl      12(%ebp), %esi
        movl      %esi, (%esp)
        fstps     20(%esp)
        call      sinf@PLT
..B1.60:
        flds      20(%esp)
        addl      $16, %esp
        fxch      %st(1)
        fstps     (%esp)
        addl      $-16, %esp
        movl      %esi, (%esp)
        fstps     20(%esp)
        call      cosf@PLT
..B1.59:
        flds      20(%esp)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstps     48(%esp)
        flds      8(%esp)
        fmuls     (%esp)
        fstps     52(%esp)
        jmp       ..B1.39
..B1.33:
        fmul      %st(0), %st
        fsts      48(%esp)
        fstps     52(%esp)
        jmp       ..B1.39
..B1.34:
        addl      $-16, %esp
        movl      12(%ebp), %eax
        movl      %eax, (%esp)
        call      sinf@PLT
..B1.61:
        addl      $16, %esp
        fsts      52(%esp)
        cmpl      $255, %esi
        je        ..B1.37
..B1.35:
        testl     %esi, %esi
        jne       ..B1.38
..B1.36:
        testl     $8388607, 8(%ebp)
        jne       ..B1.38
..B1.37:
        fstp      %st(0)
        addl      $-16, %esp
        movl      8(%ebp), %eax
        movl      %eax, (%esp)
        call      sinhf@PLT
..B1.62:
        addl      $16, %esp
        fstps     48(%esp)
        jmp       ..B1.39
..B1.38:
        flds      8(%ebp)
        fmulp     %st, %st(1)
        fstps     48(%esp)
..B1.39:
        movzwl    50(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.41
..B1.40:
        testl     $8388607, 48(%esp)
        jne       ..B1.43
..B1.41:
        movzwl    54(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.44
..B1.42:
        testl     $8388607, 52(%esp)
        je        ..B1.44
..B1.43:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     40(%esp)
..B1.44:
        movl      48(%esp), %eax
        movl      52(%esp), %edx
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        movl      %ecx, %esi
        jmp       ..B1.15
..B1.46:
        testl     $8388607, 12(%ebp)
        jne       ..B1.7
        jmp       ..B1.19
..B1.47:
        testl     $8388607, 8(%ebp)
        jne       ..B1.5
        jmp       ..B1.18
        .align    16,0x90
	.type	csinhf,@function
	.size	csinhf,.-csinhf
	.data
# -- End  csinhf
	.section .rodata, "a"
	.align 16
	.align 16
_DBL_MAX_NORMAL:
	.long	-1
	.long	2146435071
	.type	_DBL_MAX_NORMAL,@object
	.size	_DBL_MAX_NORMAL,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.data
	.section .note.GNU-stack, ""
# End
