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
	.file "casinh.c"
	.text
..TXTST0:
# -- Begin  casinh
	.text
       .align    16,0x90
	.globl casinh
casinh:
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
        jle       ..B1.73
..B1.5:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B1.39
..B1.6:
        testl     %edx, %edx
        jle       ..B1.71
..B1.7:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.70
..B1.8:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movl      $1, %edi
..B1.11:
        fldl      20(%ebp)
        fstl      160(%esp)
        fabs      
        fldl      12(%ebp)
        fstl      152(%esp)
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     104(%esp)
        fldt      104(%esp)
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
        fstpt     120(%esp)
        fldt      120(%esp)
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
        jb        ..B1.16
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
        call      asinl@PLT
..B1.107:
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      32(%esp)
        fldt      16(%esp)
        fldl      80(%esp)
        addl      $16, %esp
        fxch      %st(5)
        fstpl     184(%esp)
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
        fxch      %st(6)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      152(%esp)
        faddp     %st, %st(2)
        fldt      136(%esp)
        fdivp     %st, %st(2)
        fldt      104(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fsqrt     
        fstpt     12(%esp)
        fxch      %st(4)
        fstpl     96(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     48(%esp)
        fstpt     64(%esp)
        fstpt     80(%esp)
        call      atan2l@PLT
        jmp       ..B1.109
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
        fxch      %st(5)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      152(%esp)
        faddp     %st, %st(4)
        fxch      %st(3)
        fdivr     %st(5), %st
        fldt      168(%esp)
        fldt      120(%esp)
        faddp     %st, %st(1)
        fdivrp    %st, %st(6)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsqrt     
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     12(%esp)
        fstpl     96(%esp)
        fxch      %st(1)
        fstpt     48(%esp)
        fstpt     64(%esp)
        fldt      32(%esp)
        fstpt     32(%esp)
        fldt      80(%esp)
        fstpt     80(%esp)
        call      atan2l@PLT
..B1.109:
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      32(%esp)
        fldl      96(%esp)
        addl      $32, %esp
        fxch      %st(5)
        fstpl     184(%esp)
..B1.19:
        fldt      _Across@GOTOFF(%ebx)
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jb        ..B1.24
..B1.20:
        fldt      120(%esp)
        faddp     %st, %st(1)
        fxch      %st(4)
        fcomp     %st(2)
        fnstsw    %ax
        fldt      104(%esp)
        fdivp     %st, %st(4)
        sahf      
        jae       ..B1.22
        jp        ..B1.22
..B1.21:
        fldt      72(%esp)
        fldt      104(%esp)
        fdivp     %st, %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
        jmp       ..B1.23
..B1.22:
        fldt      88(%esp)
        fldt      136(%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
..B1.23:
        addl      $-16, %esp
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      log1pl@PLT
        jmp       ..B1.111
..B1.24:
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
..B1.111:
        addl      $16, %esp
        fstpl     176(%esp)
..B1.25:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fcoml     160(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.27
..B1.26:
        movzbl    191(%esp), %ecx
        movl      %ecx, %edx
        shrl      $7, %ecx
        andl      $127, %edx
        notl      %ecx
        shll      $7, %ecx
        orl       %ecx, %edx
        movb      %dl, 191(%esp)
..B1.27:
        fcompl    152(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.29
..B1.28:
        movzbl    183(%esp), %ecx
        movl      %ecx, %edx
        shrl      $7, %ecx
        andl      $127, %edx
        notl      %ecx
        shll      $7, %ecx
        orl       %ecx, %edx
        movb      %dl, 183(%esp)
..B1.29:
        movzwl    182(%esp), %edx
        testl     $32752, %edx
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
        fstpl     168(%esp)
..B1.36:
        testl     %edi, %edi
        je        ..B1.68
..B1.37:
        fldcw     14(%esp)
        jmp       ..B1.68
..B1.38:
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
..B1.39:
        testl     %edx, %edx
        jne       ..B1.43
..B1.40:
        testl     $1048575, 24(%ebp)
        jne       ..B1.43
..B1.41:
        cmpl      $0, 20(%ebp)
        jne       ..B1.43
..B1.42:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      asinh@PLT
..B1.112:
        fldl      20(%ebp)
        addl      $16, %esp
        fstpl     184(%esp)
        fstpl     176(%esp)
        jmp       ..B1.68
..B1.43:
        testl     %ecx, %ecx
        jne       ..B1.56
..B1.44:
        testl     $1048575, 16(%ebp)
        jne       ..B1.57
..B1.45:
        cmpl      $0, 12(%ebp)
        jne       ..B1.57
..B1.46:
        cmpl      $2047, %edx
        jl        ..B1.49
..B1.47:
        testl     $1048575, 24(%ebp)
        jne       ..B1.57
..B1.48:
        cmpl      $0, 20(%ebp)
        jne       ..B1.57
..B1.49:
        cmpl      $1023, %edx
        jl        ..B1.54
..B1.50:
        fldl      20(%ebp)
        movzbl    27(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        movl      %edx, (%esp)
        addl      $-16, %esp
        fmull     ones@GOTOFF(%ebx,%edx,8)
        fstpl     (%esp)
        call      acosh@PLT
..B1.113:
        addl      $16, %esp
        fstpl     176(%esp)
        cmpl      $0, (%esp)
        je        ..B1.52
..B1.51:
        fldl      _DBL_NPIBY2@GOTOFF(%ebx)
        jmp       ..B1.53
..B1.52:
        fldl      _DBL_PPIBY2@GOTOFF(%ebx)
..B1.53:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fadd      %st(1), %st
        fstpl     168(%esp)
        fstpl     184(%esp)
        fldl      176(%esp)
        jmp       ..B1.55
..B1.54:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        addl      $-16, %esp
        fldl      20(%ebp)
        fstpl     (%esp)
        fstpl     16(%esp)
        call      asin@PLT
..B1.114:
        fldl      16(%esp)
        addl      $16, %esp
        fxch      %st(1)
        fstpl     184(%esp)
..B1.55:
        movzbl    19(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fmull     ones@GOTOFF(%ebx,%edx,8)
        fstpl     176(%esp)
        jmp       ..B1.68
..B1.56:
        cmpl      $2047, %ecx
        je        ..B1.95
..B1.57:
        cmpl      $2047, %edx
        je        ..B1.91
..B1.118:
        cmpl      $2047, %ecx
..B1.58:
        je        ..B1.77
..B1.59:
        jge       ..B1.67
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
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        fstl      184(%esp)
        je        ..B1.65
..B1.64:
        fldl      _DBL_NINF@GOTOFF(%ebx)
        jmp       ..B1.66
..B1.65:
        fldl      _DBL_PINF@GOTOFF(%ebx)
..B1.66:
        fstpl     176(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fxch      %st(1)
        faddl     _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fstpl     168(%esp)
        faddl     12(%ebp)
        fstpl     168(%esp)
        jmp       ..B1.68
..B1.67:
        fldl      20(%ebp)
        fstpl     176(%esp)
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
..B1.68:
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
        jne       ..B1.76
..B1.69:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.70:
        xorl      %edi, %edi
        jmp       ..B1.11
..B1.71:
        testl     $1048575, 24(%ebp)
        jne       ..B1.7
..B1.72:
        cmpl      $0, 20(%ebp)
        jne       ..B1.7
        jmp       ..B1.39
..B1.73:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.74:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.38
..B1.76:
        call      __stack_chk_fail@PLT
..B1.77:
        testl     $1048575, 16(%ebp)
        jne       ..B1.116
..B1.78:
        cmpl      $0, 12(%ebp)
        jne       ..B1.116
..B1.79:
        fldl      12(%ebp)
        cmpl      $2047, %edx
        fstpl     176(%esp)
        jge       ..B1.84
..B1.80:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.82
..B1.81:
        fldl      _DBL_NZERO@GOTOFF(%ebx)
        fstpl     184(%esp)
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        jmp       ..B1.83
..B1.82:
        fldl      _DBL_PZERO@GOTOFF(%ebx)
        fstl      184(%esp)
..B1.83:
        faddl     20(%ebp)
        fstpl     168(%esp)
        jmp       ..B1.68
..B1.84:
        testl     $1048575, 24(%ebp)
        jne       ..B1.90
..B1.85:
        cmpl      $0, 20(%ebp)
        jne       ..B1.90
..B1.86:
        movzbl    27(%ebp), %edx
        testl     $128, %edx
        je        ..B1.88
..B1.87:
        fldl      _DBL_NPIBY4@GOTOFF(%ebx)
        jmp       ..B1.89
..B1.88:
        fldl      _DBL_PPIBY4@GOTOFF(%ebx)
..B1.89:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fadd      %st(1), %st
        fstpl     168(%esp)
        fstpl     184(%esp)
        jmp       ..B1.68
..B1.90:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
        jmp       ..B1.68
..B1.91:
        testl     $1048575, 24(%ebp)
        jne       ..B1.93
..B1.92:
        cmpl      $0, 20(%ebp)
        je        ..B1.115
..B1.93:
        cmpl      $2047, %ecx
        jge       ..B1.58
..B1.94:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.68
..B1.95:
        testl     $1048575, 16(%ebp)
        jne       ..B1.97
..B1.96:
        cmpl      $0, 12(%ebp)
        je        ..B1.98
..B1.97:
        cmpl      $2047, %edx
        je        ..B1.102
..B1.98:
        testl     $1048575, 16(%ebp)
        jne       ..B1.100
..B1.99:
        cmpl      $0, 12(%ebp)
        je        ..B1.57
..B1.100:
        cmpl      $2047, %edx
        jge       ..B1.57
..B1.101:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstl      176(%esp)
        fstpl     184(%esp)
        jmp       ..B1.68
..B1.102:
        testl     $1048575, 24(%ebp)
        jne       ..B1.104
..B1.103:
        cmpl      $0, 20(%ebp)
        je        ..B1.98
..B1.104:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     176(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     184(%esp)
        jmp       ..B1.68
..B1.115:
        cmpl      $2047, %ecx
        je        ..B1.77
        jmp       ..B1.59
..B1.116:
        cmpl      $2047, %ecx
        jl        ..B1.60
        jmp       ..B1.67
        .align    16,0x90
	.type	casinh,@function
	.size	casinh,.-casinh
	.data
# -- End  casinh
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
	.word	26214
	.word	26214
	.word	26214
	.word	-6554
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
_DBL_NINF:
	.long	0
	.long	-1048576
	.type	_DBL_NINF,@object
	.size	_DBL_NINF,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PINF:
	.long	0
	.long	2146435072
	.type	_DBL_PINF,@object
	.size	_DBL_PINF,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_NZERO:
	.long	0
	.long	-2147483648
	.type	_DBL_NZERO,@object
	.size	_DBL_NZERO,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_NPIBY4:
	.long	1413754136
	.long	-1075240453
	.type	_DBL_NPIBY4,@object
	.size	_DBL_NPIBY4,8
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
