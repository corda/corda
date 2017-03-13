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
	.file "catanh.c"
	.text
..TXTST0:
# -- Begin  catanh
	.text
       .align    16,0x90
	.globl catanh
catanh:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        fldz      
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 192(%esp)
        fstl      176(%esp)
        fstpl     184(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    18(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %edx
        jge       ..B1.19
..B1.4:
        testl     %edx, %edx
        jle       ..B1.74
..B1.5:
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $2047, %ecx
        jge       ..B1.20
..B1.6:
        testl     %ecx, %ecx
        jle       ..B1.72
..B1.7:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.71
..B1.8:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movl      $1, %edi
..B1.11:
        fldl      12(%ebp)
        fstl      160(%esp)
        fmul      %st(0), %st
        fstpt     80(%esp)
        fldt      80(%esp)
        fldl      20(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     144(%esp)
        fldt      144(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fstpt     128(%esp)
        fldt      128(%esp)
        fsubp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
        fabs      
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fstpt     96(%esp)
        fldt      96(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.13
..B1.12:
        fldl      160(%esp)
        fldt      128(%esp)
        faddp     %st, %st(1)
        fstpt     112(%esp)
        fldt      112(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(1)
        fstpt     12(%esp)
        fstpt     48(%esp)
        call      atan2l@PLT
..B1.106:
        fldt      48(%esp)
        addl      $32, %esp
        fchs      
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      128(%esp)
        fsubl     160(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpt     32(%esp)
        call      atan2l@PLT
..B1.105:
        fldt      32(%esp)
        addl      $32, %esp
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fstpt     16(%esp)
        fldt      16(%esp)
        fxch      %st(2)
        fstpt     52(%esp)
        fldt      40(%esp)
        fldt      52(%esp)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     184(%esp)
        jmp       ..B1.14
..B1.13:
        fldt      28(%esp)
        addl      $-32, %esp
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B1.107:
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        addl      $32, %esp
        fstpt     16(%esp)
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fstpl     184(%esp)
        fldl      160(%esp)
        fld       %st(0)
        fldt      128(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     112(%esp)
        fsubp     %st, %st(1)
..B1.14:
        fldt      112(%esp)
        fstpt     40(%esp)
        fchs      
        fldt      40(%esp)
        fldt      40(%esp)
        fmulp     %st, %st(1)
        fldt      144(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%esp)
        fxch      %st(1)
        fstpt     52(%esp)
        fldt      52(%esp)
        fldt      52(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     28(%esp)
        fldt      64(%esp)
        fldt      28(%esp)
        fsubrp    %st, %st(1)
        fabs      
        fldt      28(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.16
        jp        ..B1.16
..B1.15:
        fldt      64(%esp)
        fldt      28(%esp)
        addl      $-16, %esp
        fdivrp    %st, %st(1)
        fstpt     (%esp)
        call      logl@PLT
..B1.108:
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     176(%esp)
        jmp       ..B1.17
..B1.16:
        fldt      80(%esp)
        fldt      144(%esp)
        faddp     %st, %st(1)
        fldt      128(%esp)
        faddp     %st, %st(1)
        fstpt     28(%esp)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmull     160(%esp)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      28(%esp)
        addl      $-16, %esp
        fdivrp    %st, %st(1)
        fstpt     (%esp)
        call      atanhl@PLT
..B1.109:
        addl      $16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fstpl     176(%esp)
..B1.17:
        testl     %edi, %edi
        je        ..B1.62
..B1.18:
        fldcw     14(%esp)
        jmp       ..B1.62
..B1.19:
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
..B1.20:
        cmpl      $2047, %edx
        je        ..B1.90
..B1.21:
        cmpl      $2047, %ecx
        je        ..B1.83
..B1.22:
        cmpl      $2047, %ecx
        jge       ..B1.54
..B1.23:
        testl     %edx, %edx
        jle       ..B1.80
..B1.24:
        cmpl      $1023, %edx
        jge       ..B1.28
..B1.25:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      atanh@PLT
..B1.110:
        addl      $16, %esp
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        fstpl     176(%esp)
        je        ..B1.27
..B1.26:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     184(%esp)
        jmp       ..B1.62
..B1.27:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     184(%esp)
        jmp       ..B1.62
..B1.28:
        je        ..B1.78
..B1.29:
        cmpl      $2047, %edx
        jge       ..B1.43
..B1.30:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.77
..B1.31:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.32:
        fldcw     12(%esp)
..B1.33:
        movl      $1, %edx
..B1.34:
        fldl      12(%ebp)
        fld       %st(0)
        fld       %st(1)
        fmul      %st(2), %st
        fldl      20(%ebp)
        fmul      %st(0), %st
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fld       %st(0)
        fsub      %st(5), %st
        fchs      
        fxch      %st(1)
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldt      40(%esp)
        fmulp     %st, %st(1)
        fadd      %st(2), %st
        fstpt     64(%esp)
        fstpt     52(%esp)
        fldt      52(%esp)
        fldt      52(%esp)
        fmulp     %st, %st(1)
        fadd      %st(1), %st
        fstpt     28(%esp)
        fldt      64(%esp)
        fldt      28(%esp)
        fsubrp    %st, %st(1)
        fabs      
        fldt      28(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.36
        jp        ..B1.36
..B1.35:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        fldt      64(%esp)
        fldt      28(%esp)
        addl      $-16, %esp
        fdivrp    %st, %st(1)
        fstpt     (%esp)
        movl      %edx, 16(%esp)
        call      logl@PLT
..B1.111:
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        movl      16(%esp), %edx
        addl      $16, %esp
        fstpl     176(%esp)
        jmp       ..B1.37
..B1.36:
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     28(%esp)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      28(%esp)
        addl      $-16, %esp
        fdivrp    %st, %st(1)
        fstpt     (%esp)
        movl      %edx, 16(%esp)
        call      atanhl@PLT
..B1.112:
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        movl      16(%esp), %edx
        addl      $16, %esp
        fstpl     176(%esp)
..B1.37:
        testl     %edx, %edx
        je        ..B1.39
..B1.38:
        fldcw     14(%esp)
..B1.39:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.41
..B1.40:
        fldl      _DBL_NPIBY2@GOTOFF(%ebx)
        jmp       ..B1.42
..B1.41:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
..B1.42:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fxch      %st(1)
        fstl      184(%esp)
        faddp     %st, %st(1)
        fstpl     168(%esp)
        jmp       ..B1.62
..B1.43:
        testl     %ecx, %ecx
        jne       ..B1.47
..B1.44:
        testl     $1048575, 24(%ebp)
        jne       ..B1.46
..B1.45:
        cmpl      $0, 20(%ebp)
        je        ..B1.47
..B1.46:
        fldl      .L_2il0floatpacket.12@GOTOFF(%ebx)
        faddl     20(%ebp)
        fstpl     168(%esp)
..B1.47:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.49
..B1.48:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
        jmp       ..B1.50
..B1.49:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
..B1.50:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.52
..B1.51:
        fldl      _DBL_NPIBY2@GOTOFF(%ebx)
        jmp       ..B1.53
..B1.52:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
..B1.53:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fxch      %st(1)
        fstl      184(%esp)
        faddp     %st, %st(1)
        fstpl     168(%esp)
        jmp       ..B1.62
..B1.54:
        testl     %edx, %edx
        jne       ..B1.58
..B1.55:
        testl     $1048575, 16(%ebp)
        jne       ..B1.57
..B1.56:
        cmpl      $0, 12(%ebp)
        je        ..B1.58
..B1.57:
        fldl      .L_2il0floatpacket.12@GOTOFF(%ebx)
        faddl     12(%ebp)
        fstpl     168(%esp)
..B1.58:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.60
..B1.59:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
        jmp       ..B1.61
..B1.60:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
..B1.61:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      atan@PLT
..B1.113:
        addl      $16, %esp
        fstpl     184(%esp)
..B1.62:
        movzwl    182(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.65
..B1.63:
        testl     $1048575, 180(%esp)
        jne       ..B1.68
..B1.64:
        cmpl      $0, 176(%esp)
        jne       ..B1.68
..B1.65:
        movzwl    190(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.69
..B1.66:
        testl     $1048575, 188(%esp)
        jne       ..B1.68
..B1.67:
        cmpl      $0, 184(%esp)
        je        ..B1.69
..B1.68:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     168(%esp)
..B1.69:
        movl      176(%esp), %edx
        movl      %edx, (%esi)
        movl      180(%esp), %ecx
        movl      184(%esp), %edi
        movl      188(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      192(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.82
..B1.70:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.71:
        xorl      %edi, %edi
        jmp       ..B1.11
..B1.72:
        testl     $1048575, 24(%ebp)
        jne       ..B1.7
..B1.73:
        cmpl      $0, 20(%ebp)
        jne       ..B1.7
        jmp       ..B1.20
..B1.74:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.75:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.19
..B1.77:
        xorl      %edx, %edx
        jmp       ..B1.34
..B1.78:
        testl     $1048575, 16(%ebp)
        jne       ..B1.29
..B1.79:
        cmpl      $0, 12(%ebp)
        je        ..B1.25
        jmp       ..B1.29
..B1.80:
        testl     $1048575, 16(%ebp)
        jne       ..B1.24
..B1.81:
        cmpl      $0, 12(%ebp)
        jne       ..B1.24
        jmp       ..B1.54
..B1.82:
        call      __stack_chk_fail@PLT
..B1.83:
        testl     $1048575, 24(%ebp)
        jne       ..B1.85
..B1.84:
        cmpl      $0, 20(%ebp)
        je        ..B1.22
..B1.85:
        cmpl      $2047, %edx
        jge       ..B1.22
..B1.86:
        testl     %edx, %edx
        jle       ..B1.88
..B1.87:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.62
..B1.88:
        testl     $1048575, 16(%ebp)
        jne       ..B1.87
..B1.89:
        cmpl      $0, 12(%ebp)
        jne       ..B1.87
        jmp       ..B1.22
..B1.90:
        testl     $1048575, 16(%ebp)
        jne       ..B1.92
..B1.91:
        cmpl      $0, 12(%ebp)
        je        ..B1.21
..B1.92:
        fldl      12(%ebp)
        cmpl      $2047, %ecx
        fmul      %st(0), %st
        jge       ..B1.94
..B1.93:
        fstl      12(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.62
..B1.94:
        testl     $1048575, 24(%ebp)
        jne       ..B1.101
..B1.95:
        cmpl      $0, 20(%ebp)
        jne       ..B1.101
..B1.96:
        fmul      %st(0), %st
        fstpl     12(%ebp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        fstpl     176(%esp)
        je        ..B1.98
..B1.97:
        fldl      _DBL_NPIBY2@GOTOFF(%ebx)
        jmp       ..B1.99
..B1.98:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
..B1.99:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fxch      %st(1)
        fstl      184(%esp)
        faddp     %st, %st(1)
        fstpl     168(%esp)
        jmp       ..B1.62
..B1.101:
        fstl      12(%ebp)
..B1.102:
        fmul      %st(0), %st
        fstpl     176(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
        jmp       ..B1.62
        .align    16,0x90
	.type	catanh,@function
	.size	catanh,.-catanh
	.data
# -- End  catanh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0xc0,0xf5,0x28,0x5c,0x8f,0xc2,0xf5,0xf9,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,12
	.space 4, 0x00 	# pad
	.align 16
_DBL_NZERO:
	.long	0
	.long	-2147483648
	.type	_DBL_NZERO,@object
	.size	_DBL_NZERO,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PZERO:
	.long	0
	.long	0
	.type	_DBL_PZERO,@object
	.size	_DBL_PZERO,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_NPIBY2:
	.long	1413754136
	.long	-1074191877
	.type	_DBL_NPIBY2,@object
	.size	_DBL_NPIBY2,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPIBY2:
	.long	1413754136
	.long	1073291771
	.type	_DBL_PPIBY2,@object
	.size	_DBL_PPIBY2,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.data
	.section .note.GNU-stack, ""
# End
