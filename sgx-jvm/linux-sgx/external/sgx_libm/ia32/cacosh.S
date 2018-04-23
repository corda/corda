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
	.file "cacosh.c"
	.text
..TXTST0:
# -- Begin  cacosh
	.text
       .align    16,0x90
	.globl cacosh
cacosh:
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
        movl      %edx, 184(%esp)
        fstl      168(%esp)
        fstpl     176(%esp)
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
        jge       ..B1.36
..B1.4:
        testl     %ecx, %ecx
        jle       ..B1.71
..B1.5:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B1.37
..B1.6:
        testl     %edx, %edx
        jle       ..B1.69
..B1.7:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.68
..B1.8:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movl      $1, %edi
..B1.11:
        fldl      12(%ebp)
        fstl      152(%esp)
        fabs      
        fldl      20(%ebp)
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
..B1.109:
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
        jmp       ..B1.111
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
..B1.111:
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      32(%esp)
        fldl      96(%esp)
        addl      $32, %esp
..B1.19:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fcompl    152(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.21
..B1.20:
        fldt      _LDBL_PPI@GOTOFF(%ebx)
        fsubp     %st, %st(6)
..B1.21:
        fldt      _Across@GOTOFF(%ebx)
        movzbl    27(%ebp), %ecx
        fcomp     %st(5)
        fnstsw    %ax
        fxch      %st(5)
        fstpl     176(%esp)
        andl      $-128, %ecx
        movzbl    183(%esp), %edx
        andl      $127, %edx
        orl       %ecx, %edx
        movb      %dl, 183(%esp)
        sahf      
        jb        ..B1.26
..B1.22:
        fldt      104(%esp)
        faddp     %st, %st(1)
        fxch      %st(4)
        fcomp     %st(2)
        fnstsw    %ax
        fldt      120(%esp)
        fdivp     %st, %st(4)
        sahf      
        jae       ..B1.24
        jp        ..B1.24
..B1.23:
        fldt      72(%esp)
        fldt      120(%esp)
        fdivp     %st, %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
        jmp       ..B1.25
..B1.24:
        fldt      88(%esp)
        fldt      136(%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
..B1.25:
        addl      $-16, %esp
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      log1pl@PLT
        jmp       ..B1.113
..B1.26:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(2)
        fld       %st(0)
        addl      $-16, %esp
        fmul      %st(1), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      logl@PLT
..B1.113:
        addl      $16, %esp
        fstpl     168(%esp)
..B1.27:
        movzwl    174(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.30
..B1.28:
        testl     $1048575, 172(%esp)
        jne       ..B1.33
..B1.29:
        cmpl      $0, 168(%esp)
        jne       ..B1.33
..B1.30:
        movzwl    182(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.34
..B1.31:
        testl     $1048575, 180(%esp)
        jne       ..B1.33
..B1.32:
        cmpl      $0, 176(%esp)
        je        ..B1.34
..B1.33:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     160(%esp)
..B1.34:
        testl     %edi, %edi
        je        ..B1.66
..B1.35:
        fldcw     14(%esp)
        jmp       ..B1.66
..B1.36:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
..B1.37:
        testl     %edx, %edx
        jne       ..B1.49
..B1.38:
        testl     $1048575, 24(%ebp)
        jne       ..B1.49
..B1.39:
        cmpl      $0, 20(%ebp)
        jne       ..B1.49
..B1.40:
        cmpl      $2047, %ecx
        jl        ..B1.43
..B1.41:
        testl     $1048575, 16(%ebp)
        jne       ..B1.49
..B1.42:
        cmpl      $0, 12(%ebp)
        jne       ..B1.49
..B1.43:
        cmpl      $1023, %ecx
        jl        ..B1.47
..B1.44:
        fldl      12(%ebp)
        addl      $-16, %esp
        fabs      
        fstpl     (%esp)
        call      acosh@PLT
..B1.114:
        addl      $16, %esp
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        fstpl     168(%esp)
        je        ..B1.46
..B1.45:
        fldl      _DBL_PPI@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     160(%esp)
        jmp       ..B1.48
..B1.46:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
        jmp       ..B1.48
..B1.47:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstpl     168(%esp)
        addl      $-16, %esp
        fldl      12(%ebp)
        fstpl     (%esp)
        call      acos@PLT
..B1.115:
        addl      $16, %esp
        fstpl     176(%esp)
..B1.48:
        movzbl    183(%esp), %edx
        movzbl    27(%ebp), %edi
        andl      $127, %edx
        andl      $-128, %edi
        orl       %edi, %edx
        movb      %dl, 183(%esp)
        jmp       ..B1.66
..B1.49:
        testl     %ecx, %ecx
        jne       ..B1.56
..B1.50:
        testl     $1048575, 16(%ebp)
        jne       ..B1.57
..B1.51:
        cmpl      $0, 12(%ebp)
        jne       ..B1.57
..B1.52:
        cmpl      $2047, %edx
        jl        ..B1.55
..B1.53:
        testl     $1048575, 24(%ebp)
        jne       ..B1.57
..B1.54:
        cmpl      $0, 20(%ebp)
        jne       ..B1.57
..B1.55:
        fldl      20(%ebp)
        fstl      (%esp)
        addl      $-16, %esp
        fabs      
        fstpl     (%esp)
        call      asinh@PLT
..B1.117:
        fldl      12(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpl     168(%esp)
        addl      $-16, %esp
        fstpl     8(%esp)
        fldl      16(%esp)
        fstpl     (%esp)
        call      atan2@PLT
..B1.116:
        addl      $16, %esp
        fstpl     176(%esp)
        jmp       ..B1.66
..B1.56:
        cmpl      $2047, %ecx
        je        ..B1.97
..B1.57:
        cmpl      $2047, %edx
        je        ..B1.93
..B1.121:
        cmpl      $2047, %ecx
..B1.58:
        je        ..B1.75
..B1.59:
        jge       ..B1.64
..B1.60:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.62
..B1.61:
        fldl      _DBL_NPIBY2@GOTOFF(%ebx)
        jmp       ..B1.63
..B1.62:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
..B1.63:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fadd      %st(1), %st
        fstpl     160(%esp)
        fstpl     176(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        faddl     12(%ebp)
        fstpl     160(%esp)
        jmp       ..B1.65
..B1.64:
        fldl      12(%ebp)
        faddl     _DBL_PZERO@GOTOFF(%ebx)
        fstpl     176(%esp)
..B1.65:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     168(%esp)
..B1.66:
        movl      168(%esp), %edx
        movl      %edx, (%esi)
        movl      172(%esp), %ecx
        movl      176(%esp), %edi
        movl      180(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      184(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.74
..B1.67:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.68:
        xorl      %edi, %edi
        jmp       ..B1.11
..B1.69:
        testl     $1048575, 24(%ebp)
        jne       ..B1.7
..B1.70:
        cmpl      $0, 20(%ebp)
        jne       ..B1.7
        jmp       ..B1.37
..B1.71:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.72:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.36
..B1.74:
        call      __stack_chk_fail@PLT
..B1.75:
        testl     $1048575, 16(%ebp)
        jne       ..B1.119
..B1.76:
        cmpl      $0, 12(%ebp)
        jne       ..B1.119
..B1.77:
        fldl      12(%ebp)
        cmpl      $2047, %edx
        fmul      %st(0), %st
        fstpl     168(%esp)
        jge       ..B1.84
..B1.78:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.80
..B1.79:
        fldl      _DBL_PPI@GOTOFF(%ebx)
        fstl      176(%esp)
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpl     160(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        jmp       ..B1.81
..B1.80:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstl      176(%esp)
..B1.81:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.83
..B1.82:
        movzbl    183(%esp), %edi
        movl      %edi, %edx
        shrl      $7, %edi
        andl      $127, %edx
        notl      %edi
        shll      $7, %edi
        orl       %edi, %edx
        movb      %dl, 183(%esp)
..B1.83:
        faddl     20(%ebp)
        fstpl     160(%esp)
        jmp       ..B1.66
..B1.84:
        testl     $1048575, 24(%ebp)
        jne       ..B1.92
..B1.85:
        cmpl      $0, 20(%ebp)
        jne       ..B1.92
..B1.86:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.88
..B1.87:
        fldl      _DBL_P3PIBY4@GOTOFF(%ebx)
        jmp       ..B1.89
..B1.88:
        fldl      _DBL_PPIBY4@GOTOFF(%ebx)
..B1.89:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        fstpl     176(%esp)
        je        ..B1.91
..B1.90:
        movzbl    183(%esp), %edi
        movl      %edi, %edx
        shrl      $7, %edi
        andl      $127, %edx
        notl      %edi
        shll      $7, %edi
        orl       %edi, %edx
        movb      %dl, 183(%esp)
..B1.91:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        faddl     176(%esp)
        fstpl     160(%esp)
        jmp       ..B1.66
..B1.92:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        jmp       ..B1.66
..B1.93:
        testl     $1048575, 24(%ebp)
        jne       ..B1.95
..B1.94:
        cmpl      $0, 20(%ebp)
        je        ..B1.118
..B1.95:
        cmpl      $2047, %ecx
        jge       ..B1.58
..B1.96:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      168(%esp)
        fstpl     176(%esp)
        jmp       ..B1.66
..B1.97:
        testl     $1048575, 16(%ebp)
        jne       ..B1.99
..B1.98:
        cmpl      $0, 12(%ebp)
        je        ..B1.100
..B1.99:
        cmpl      $2047, %edx
        je        ..B1.104
..B1.100:
        testl     $1048575, 16(%ebp)
        jne       ..B1.102
..B1.101:
        cmpl      $0, 12(%ebp)
        je        ..B1.57
..B1.102:
        cmpl      $2047, %edx
        jge       ..B1.57
..B1.103:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstl      168(%esp)
        fstpl     176(%esp)
        jmp       ..B1.66
..B1.104:
        testl     $1048575, 24(%ebp)
        jne       ..B1.106
..B1.105:
        cmpl      $0, 20(%ebp)
        je        ..B1.100
..B1.106:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     168(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        jmp       ..B1.66
..B1.118:
        cmpl      $2047, %ecx
        je        ..B1.75
        jmp       ..B1.59
..B1.119:
        cmpl      $2047, %ecx
        jl        ..B1.60
        jmp       ..B1.64
        .align    16,0x90
	.type	cacosh,@function
	.size	cacosh,.-cacosh
	.data
# -- End  cacosh
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
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPI:
	.long	1413754136
	.long	1074340347
	.type	_DBL_PPI,@object
	.size	_DBL_PPI,8
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
