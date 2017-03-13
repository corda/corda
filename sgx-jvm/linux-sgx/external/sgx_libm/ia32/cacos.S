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
	.file "cacos.c"
	.text
..TXTST0:
# -- Begin  cacos
	.text
       .align    16,0x90
	.globl cacos
cacos:
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
        movzwl    18(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %ecx
        jge       ..B1.38
..B1.4:
        testl     %ecx, %ecx
        jle       ..B1.64
..B1.5:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B1.38
..B1.6:
        testl     %edx, %edx
        jle       ..B1.62
..B1.7:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.61
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
        fabs      
        fldl      20(%ebp)
        fstl      168(%esp)
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     120(%esp)
        fldt      120(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fld       %st(0)
        fsubr     %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fadd      %st(3), %st
        fsqrt     
        fstpt     88(%esp)
        fldt      88(%esp)
        fld       %st(2)
        fadd      %st(6), %st
        fld       %st(0)
        fmul      %st(1), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        fsqrt     
        fstpt     104(%esp)
        fldt      104(%esp)
        fxch      %st(1)
        fucom     %st(2)
        fnstsw    %ax
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     136(%esp)
        sahf      
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmul      %st, %st(1)
        jp        ..B1.12
        je        ..B1.13
..B1.12:
        fldt      88(%esp)
        fldt      136(%esp)
        fsubrp    %st, %st(1)
        fstpt     72(%esp)
        jmp       ..B1.14
..B1.13:
        fxch      %st(4)
        fstpt     72(%esp)
        fldt      72(%esp)
        fxch      %st(4)
..B1.14:
        fldt      _Bcross@GOTOFF(%ebx)
        fmul      %st(2), %st
        fcomp     %st(6)
        fnstsw    %ax
        sahf      
        jbe       ..B1.16
..B1.15:
        fstp      %st(4)
        fld       %st(4)
        addl      $-16, %esp
        fdiv      %st(1), %st
        fstpt     (%esp)
        fxch      %st(4)
        fstpl     80(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     48(%esp)
        fstpt     64(%esp)
        call      acosl@PLT
..B1.104:
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      32(%esp)
        fldt      16(%esp)
        fldl      80(%esp)
        addl      $16, %esp
        jmp       ..B1.19
..B1.16:
        fxch      %st(2)
        fcom      %st(5)
        fnstsw    %ax
        sahf      
        jb        ..B1.18
..B1.17:
        fstp      %st(4)
        addl      $-32, %esp
        fld       %st(2)
        fld       %st(1)
        fadd      %st(6), %st
        fmul      %st(3), %st
        fldt      136(%esp)
        faddp     %st, %st(2)
        fldt      152(%esp)
        fdivp     %st, %st(2)
        fldt      104(%esp)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fsqrt     
        fstpt     (%esp)
        fxch      %st(4)
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpl     96(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     48(%esp)
        fstpt     64(%esp)
        fstpt     80(%esp)
        call      atan2l@PLT
        jmp       ..B1.106
..B1.18:
        fxch      %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        fxch      %st(3)
        fstpt     (%esp)
        fldt      (%esp)
        addl      $-32, %esp
        fxch      %st(3)
        fadd      %st(5), %st
        fldt      136(%esp)
        faddp     %st, %st(4)
        fdiv      %st, %st(3)
        fldt      168(%esp)
        fldt      120(%esp)
        faddp     %st, %st(1)
        fdivrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsqrt     
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%esp)
        fxch      %st(2)
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpl     96(%esp)
        fxch      %st(1)
        fstpt     48(%esp)
        fstpt     64(%esp)
        fldt      32(%esp)
        fstpt     32(%esp)
        fldt      80(%esp)
        fstpt     80(%esp)
        call      atan2l@PLT
..B1.106:
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      32(%esp)
        fldl      96(%esp)
        addl      $32, %esp
..B1.19:
        fldt      _Across@GOTOFF(%ebx)
        fcomp     %st(5)
        fnstsw    %ax
        sahf      
        jb        ..B1.24
..B1.20:
        fldt      104(%esp)
        faddp     %st, %st(2)
        fcomp     %st(3)
        fnstsw    %ax
        fldt      120(%esp)
        fdivp     %st, %st(1)
        sahf      
        jae       ..B1.22
        jp        ..B1.22
..B1.21:
        fldt      72(%esp)
        fldt      120(%esp)
        fdivp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        jmp       ..B1.23
..B1.22:
        fldt      88(%esp)
        fldt      136(%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.23:
        addl      $-16, %esp
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        call      log1pl@PLT
        jmp       ..B1.108
..B1.24:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        fld       %st(1)
        addl      $-16, %esp
        fmul      %st(2), %st
        fsubp     %st, %st(1)
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        call      logl@PLT
..B1.108:
        fldt      16(%esp)
        addl      $16, %esp
..B1.25:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fcoml     160(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.27
..B1.26:
        fldt      _LDBL_PPI@GOTOFF(%ebx)
        fsubp     %st, %st(2)
..B1.27:
        fldl      168(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.29
..B1.28:
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fmulp     %st, %st(2)
..B1.29:
        fstpl     176(%esp)
        movzwl    182(%esp), %edx
        testl     $32752, %edx
        fstpl     184(%esp)
        jne       ..B1.32
..B1.30:
        testl     $1048575, 180(%esp)
        jne       ..B1.35
..B1.31:
        cmpl      $0, 176(%esp)
        jne       ..B1.35
..B1.32:
        movzwl    190(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.36
..B1.33:
        testl     $1048575, 188(%esp)
        jne       ..B1.35
..B1.34:
        cmpl      $0, 184(%esp)
        je        ..B1.36
..B1.35:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     152(%esp)
..B1.36:
        testl     %edi, %edi
        je        ..B1.59
..B1.37:
        fldcw     14(%esp)
        jmp       ..B1.59
..B1.38:
        testl     %ecx, %ecx
        jne       ..B1.42
..B1.39:
        testl     $1048575, 16(%ebp)
        jne       ..B1.42
..B1.40:
        cmpl      $0, 12(%ebp)
        jne       ..B1.42
..B1.41:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     152(%esp)
        addl      $-16, %esp
        fldl      20(%ebp)
        fstpl     (%esp)
        call      asinh@PLT
..B1.109:
        addl      $16, %esp
        fchs      
        fstpl     184(%esp)
        jmp       ..B1.59
..B1.42:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        je        ..B1.98
..B1.115:
        cmpl      $2047, %ecx
..B1.43:
        je        ..B1.88
..B1.44:
        cmpl      $2047, %edx
        je        ..B1.85
..B1.45:
        testl     %edx, %edx
        jne       ..B1.54
..B1.46:
        testl     $1048575, 24(%ebp)
        jne       ..B1.54
..B1.47:
        cmpl      $0, 20(%ebp)
        jne       ..B1.54
..B1.48:
        cmpl      $1023, %ecx
        jl        ..B1.52
..B1.49:
        fldl      12(%ebp)
        addl      $-16, %esp
        fabs      
        fstpl     (%esp)
        call      acosh@PLT
..B1.110:
        addl      $16, %esp
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        fstpl     184(%esp)
        je        ..B1.51
..B1.50:
        fldl      _DBL_PPI@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     152(%esp)
        jmp       ..B1.53
..B1.51:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
        jmp       ..B1.53
..B1.52:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     184(%esp)
        addl      $-16, %esp
        fldl      12(%ebp)
        fstpl     (%esp)
        call      acos@PLT
..B1.111:
        addl      $16, %esp
        fstpl     176(%esp)
..B1.53:
        movzbl    27(%ebp), %edi
        shrl      $7, %edi
        notl      %edi
        movzbl    191(%esp), %edx
        shll      $7, %edi
        andl      $127, %edx
        orl       %edi, %edx
        movb      %dl, 191(%esp)
        jmp       ..B1.59
..B1.54:
        cmpl      $2047, %ecx
        je        ..B1.67
..B1.55:
        jge       ..B1.57
..B1.56:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     152(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        faddl     12(%ebp)
        fstpl     152(%esp)
        jmp       ..B1.58
..B1.57:
        fldl      12(%ebp)
        faddl     _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
..B1.58:
        fldl      20(%ebp)
        fchs      
        fstpl     184(%esp)
..B1.59:
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
        jne       ..B1.66
..B1.60:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.61:
        xorl      %edi, %edi
        jmp       ..B1.11
..B1.62:
        testl     $1048575, 24(%ebp)
        jne       ..B1.7
..B1.63:
        cmpl      $0, 20(%ebp)
        jne       ..B1.7
        jmp       ..B1.38
..B1.64:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.65:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.38
..B1.66:
        call      __stack_chk_fail@PLT
..B1.67:
        testl     $1048575, 16(%ebp)
        jne       ..B1.113
..B1.68:
        cmpl      $0, 12(%ebp)
        jne       ..B1.113
..B1.69:
        fldl      12(%ebp)
        cmpl      $2047, %edx
        fmul      %st(0), %st
        fstpl     184(%esp)
        jge       ..B1.76
..B1.70:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.72
..B1.71:
        fldl      _DBL_PPI@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     152(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        jmp       ..B1.73
..B1.72:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstl      176(%esp)
..B1.73:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        jne       ..B1.75
..B1.74:
        movzbl    191(%esp), %edi
        movl      %edi, %edx
        shrl      $7, %edi
        andl      $127, %edx
        notl      %edi
        shll      $7, %edi
        orl       %edi, %edx
        movb      %dl, 191(%esp)
..B1.75:
        faddl     20(%ebp)
        fstpl     152(%esp)
        jmp       ..B1.59
..B1.76:
        testl     $1048575, 24(%ebp)
        jne       ..B1.84
..B1.77:
        cmpl      $0, 20(%ebp)
        jne       ..B1.84
..B1.78:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.80
..B1.79:
        fldl      _DBL_P3PIBY4@GOTOFF(%ebx)
        jmp       ..B1.81
..B1.80:
        fldl      _DBL_PPIBY4@GOTOFF(%ebx)
..B1.81:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        fstl      176(%esp)
        jne       ..B1.83
..B1.82:
        movzbl    191(%esp), %edi
        movl      %edi, %edx
        shrl      $7, %edi
        andl      $127, %edx
        notl      %edi
        shll      $7, %edi
        orl       %edi, %edx
        movb      %dl, 191(%esp)
..B1.83:
        faddl     _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fstpl     152(%esp)
        jmp       ..B1.59
..B1.84:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        jmp       ..B1.59
..B1.85:
        testl     $1048575, 24(%ebp)
        jne       ..B1.87
..B1.86:
        cmpl      $0, 20(%ebp)
        je        ..B1.54
..B1.87:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
        jmp       ..B1.59
..B1.88:
        testl     $1048575, 16(%ebp)
        jne       ..B1.90
..B1.89:
        cmpl      $0, 12(%ebp)
        je        ..B1.92
..B1.90:
        cmpl      $2047, %edx
        jge       ..B1.92
..B1.91:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.59
..B1.92:
        testl     $1048575, 16(%ebp)
        jne       ..B1.94
..B1.93:
        cmpl      $0, 12(%ebp)
        je        ..B1.44
..B1.94:
        cmpl      $2047, %edx
        jne       ..B1.45
..B1.95:
        testl     $1048575, 24(%ebp)
        jne       ..B1.97
..B1.96:
        cmpl      $0, 20(%ebp)
        je        ..B1.85
..B1.97:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
        jmp       ..B1.59
..B1.98:
        testl     $1048575, 24(%ebp)
        jne       ..B1.100
..B1.99:
        cmpl      $0, 20(%ebp)
        je        ..B1.112
..B1.100:
        cmpl      $2047, %ecx
        jge       ..B1.43
..B1.101:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.59
..B1.112:
        cmpl      $2047, %ecx
        je        ..B1.88
        jmp       ..B1.44
..B1.113:
        cmpl      $2047, %ecx
        jl        ..B1.56
        jmp       ..B1.57
        .align    16,0x90
	.type	cacos,@function
	.size	cacos,.-cacos
	.data
# -- End  cacos
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
_Bcross:
	.word	-2936
	.word	-10277
	.word	29569
	.word	-23482
	.word	16382
	.word	0
	.type	_Bcross,@object
	.size	_Bcross,12
	.space 4, 0x00 	# pad
	.align 16
_Across:
	.word	0
	.word	0
	.word	0
	.word	-16384
	.word	16383
	.word	0
	.type	_Across,@object
	.size	_Across,12
	.space 4, 0x00 	# pad
	.align 16
_DBL_PZERO:
	.long	0
	.long	0
	.type	_DBL_PZERO,@object
	.size	_DBL_PZERO,8
	.space 8, 0x00 	# pad
	.align 16
_LDBL_PPI:
	.word	-15819
	.word	8552
	.word	-9566
	.word	-14065
	.word	16384
	.word	0
	.type	_LDBL_PPI,@object
	.size	_LDBL_PPI,12
	.space 4, 0x00 	# pad
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPIBY2:
	.long	1413754136
	.long	1073291771
	.type	_DBL_PPIBY2,@object
	.size	_DBL_PPIBY2,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPI:
	.long	1413754136
	.long	1074340347
	.type	_DBL_PPI,@object
	.size	_DBL_PPI,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_P3PIBY4:
	.long	2134057426
	.long	1073928572
	.type	_DBL_P3PIBY4,@object
	.size	_DBL_P3PIBY4,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPIBY4:
	.long	1413754136
	.long	1072243195
	.type	_DBL_PPIBY4,@object
	.size	_DBL_PPIBY4,8
	.data
	.section .note.GNU-stack, ""
# End
