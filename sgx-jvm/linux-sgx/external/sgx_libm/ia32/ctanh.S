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
	.file "ctanh.c"
	.text
..TXTST0:
# -- Begin  ctanh
	.text
       .align    16,0x90
	.globl ctanh
ctanh:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        fldz      
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 80(%esp)
        fstl      64(%esp)
        fstpl     72(%esp)
..B1.2:
        fnstcw    50(%esp)
..B1.3:
        movzwl    18(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %edx
        jge       ..B1.31
..B1.4:
        testl     %edx, %edx
        jle       ..B1.54
..B1.5:
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $2047, %ecx
        jge       ..B1.32
..B1.6:
        cmpl      $1032, %edx
        jl        ..B1.23
..B1.7:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        movzbl    19(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ecx, %ecx
        fldl      ones@GOTOFF(%ebx,%edx,8)
        fsub      %st(1), %st
        fstpl     64(%esp)
        jne       ..B1.15
..B1.8:
        testl     $1048575, 24(%ebp)
        jne       ..B1.13
..B1.9:
        cmpl      $0, 20(%ebp)
        jne       ..B1.13
..B1.10:
        fstp      %st(0)
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.12
..B1.11:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.12:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.13:
        fldl      20(%ebp)
        jmp       ..B1.16
..B1.15:
        fldl      20(%ebp)
        jne       ..B1.19
..B1.16:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        fadd      %st(0), %st
        fstpl     56(%esp)
        jne       ..B1.18
..B1.17:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.22
..B1.18:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.22
..B1.19:
        addl      $-16, %esp
        fstl      (%esp)
        fstpl     24(%esp)
        fstpl     40(%esp)
        call      sin@PLT
..B1.104:
        fldl      40(%esp)
        fldl      24(%esp)
        addl      $16, %esp
        fxch      %st(2)
        fstpl     (%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpl     (%esp)
        fstpl     40(%esp)
        call      cos@PLT
..B1.103:
        fldl      40(%esp)
        addl      $16, %esp
        fldl      (%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     16(%esp)
        movzbl    23(%esp), %edx
        testl     $128, %edx
        jne       ..B1.21
..B1.20:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.22
..B1.21:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
..B1.22:
        fmul      %st(0), %st
        fstpl     56(%esp)
        jmp       ..B1.44
..B1.23:
        movzwl    50(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.53
..B1.24:
        orl       $-64768, %ecx
        movw      %cx, 48(%esp)
..B1.25:
        fldcw     48(%esp)
..B1.26:
        movl      $1, %edi
..B1.27:
        fldl      12(%ebp)
        addl      $-32, %esp
        lea       32(%esp), %edx
        lea       44(%esp), %ecx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        movl      %ecx, 16(%esp)
        call      sinhcoshl@PLT
..B1.28:
        fldl      20(%ebp)
        lea       56(%esp), %edx
        lea       68(%esp), %ecx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        movl      %ecx, 16(%esp)
        call      sincosl@PLT
..B1.106:
        addl      $32, %esp
..B1.29:
        fldt      (%esp)
        testl     %edi, %edi
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldt      12(%esp)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fdivr     %st, %st(2)
        fxch      %st(2)
        fstpl     64(%esp)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fdivp     %st, %st(1)
        fstpl     72(%esp)
        je        ..B1.44
..B1.30:
        fldcw     50(%esp)
        jmp       ..B1.44
..B1.31:
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
..B1.32:
        cmpl      $2047, %edx
        je        ..B1.62
..B1.33:
        cmpl      $2047, %ecx
        je        ..B1.58
..B1.34:
        testl     %edx, %edx
        jne       ..B1.43
..B1.35:
        testl     $1048575, 16(%ebp)
        jne       ..B1.43
..B1.36:
        cmpl      $0, 12(%ebp)
        jne       ..B1.43
..B1.37:
        testl     %ecx, %ecx
        jne       ..B1.41
..B1.38:
        testl     $1048575, 24(%ebp)
        jne       ..B1.41
..B1.39:
        cmpl      $0, 20(%ebp)
        jne       ..B1.41
..B1.40:
        fldl      12(%ebp)
        fstpl     64(%esp)
        fldl      20(%ebp)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.41:
        cmpl      $2047, %ecx
        jge       ..B1.44
..B1.42:
        fldl      12(%ebp)
        fstpl     64(%esp)
        addl      $-16, %esp
        fldl      20(%ebp)
        fstpl     (%esp)
        call      tan@PLT
..B1.107:
        addl      $16, %esp
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.43:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      tanh@PLT
..B1.108:
        fldl      20(%ebp)
        addl      $16, %esp
        fstpl     72(%esp)
        fstpl     64(%esp)
..B1.44:
        movzwl    70(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.47
..B1.45:
        testl     $1048575, 68(%esp)
        jne       ..B1.50
..B1.46:
        cmpl      $0, 64(%esp)
        jne       ..B1.50
..B1.47:
        movzwl    78(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.51
..B1.48:
        testl     $1048575, 76(%esp)
        jne       ..B1.50
..B1.49:
        cmpl      $0, 72(%esp)
        je        ..B1.51
..B1.50:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     56(%esp)
..B1.51:
        movl      64(%esp), %edx
        movl      %edx, (%esi)
        movl      68(%esp), %ecx
        movl      72(%esp), %edi
        movl      76(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      80(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.57
..B1.52:
        movl      %esi, %eax
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.53:
        xorl      %edi, %edi
        jmp       ..B1.27
..B1.54:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.55:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.31
..B1.57:
        call      __stack_chk_fail@PLT
..B1.58:
        testl     $1048575, 24(%ebp)
        jne       ..B1.61
..B1.59:
        cmpl      $0, 20(%ebp)
        jne       ..B1.61
..B1.60:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      tan@PLT
..B1.109:
        addl      $16, %esp
        fstl      64(%esp)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.61:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      64(%esp)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.62:
        testl     $1048575, 16(%ebp)
        jne       ..B1.80
..B1.63:
        cmpl      $0, 12(%ebp)
        jne       ..B1.112
..B1.64:
        movzbl    19(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        testl     %ecx, %ecx
        fldl      ones@GOTOFF(%ebx,%edx,8)
        fstpl     64(%esp)
        jne       ..B1.70
..B1.65:
        testl     $1048575, 24(%ebp)
        jne       ..B1.71
..B1.66:
        cmpl      $0, 20(%ebp)
        jne       ..B1.71
..B1.67:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.69
..B1.68:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.69:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.70:
        cmpl      $2047, %ecx
        je        ..B1.92
..B1.71:
        cmpl      $1023, %ecx
        jge       ..B1.77
..B1.72:
        testl     %ecx, %ecx
        jne       ..B1.74
..B1.73:
        fldl      20(%ebp)
        fadd      %st(0), %st
        fstpl     56(%esp)
..B1.74:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.76
..B1.75:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.76:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.77:
        fldl      20(%ebp)
        fstl      8(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      sin@PLT
..B1.111:
        addl      $16, %esp
        fstpl     (%esp)
        addl      $-16, %esp
        fldl      24(%esp)
        fstpl     (%esp)
        call      cos@PLT
..B1.110:
        addl      $16, %esp
        fldl      (%esp)
        fmulp     %st, %st(1)
        fstpl     16(%esp)
        movzbl    23(%esp), %edx
        testl     $128, %edx
        jne       ..B1.79
..B1.78:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.79:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.80:
        jne       ..B1.82
..B1.81:
        cmpl      $0, 12(%ebp)
        je        ..B1.33
..B1.82:
        fldl      12(%ebp)
        testl     %ecx, %ecx
        fmul      %st(0), %st
        fstl      64(%esp)
        jne       ..B1.90
..B1.83:
        testl     $1048575, 24(%ebp)
        jne       ..B1.91
..B1.84:
        cmpl      $0, 20(%ebp)
        jne       ..B1.91
..B1.85:
        fstp      %st(0)
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.87
..B1.86:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.87:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.90:
        cmpl      $2047, %ecx
        je        ..B1.98
..B1.91:
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.92:
        testl     $1048575, 24(%ebp)
        jne       ..B1.97
..B1.93:
        cmpl      $0, 20(%ebp)
        jne       ..B1.97
..B1.94:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.96
..B1.95:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.96:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.97:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     72(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     56(%esp)
        jmp       ..B1.44
..B1.98:
        testl     $1048575, 24(%ebp)
        jne       ..B1.100
..B1.99:
        cmpl      $0, 20(%ebp)
        je        ..B1.91
..B1.100:
        fstp      %st(0)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     72(%esp)
        jmp       ..B1.44
..B1.112:
        testl     $1048575, 16(%ebp)
        jne       ..B1.82
        jmp       ..B1.81
        .align    16,0x90
	.type	ctanh,@function
	.size	ctanh,.-ctanh
	.data
# -- End  ctanh
	.section .rodata, "a"
	.align 16
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.space 8, 0x00 	# pad
	.align 16
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 16
_DBL_PZERO:
	.long	0
	.long	0
	.type	_DBL_PZERO,@object
	.size	_DBL_PZERO,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_NZERO:
	.long	0
	.long	-2147483648
	.type	_DBL_NZERO,@object
	.size	_DBL_NZERO,8
	.data
	.section .note.GNU-stack, ""
# End
