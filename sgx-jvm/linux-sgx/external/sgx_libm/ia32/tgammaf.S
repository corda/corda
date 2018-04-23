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
	.file "tgammaf.c"
	.text
..TXTST0:
# -- Begin  tgammaf
	.text
       .align    16,0x90
	.globl tgammaf
tgammaf:
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
        movl      8(%ebp), %edi
        call      fegetround
..B1.96:
        movl      %eax, 32(%esp)
..B1.2:
        cmpl      $0, 32(%esp)
        jne       ..B1.4
..B1.3:
        xorl      %ecx, %ecx
        jmp       ..B1.6
..B1.4:
        addl      $4, %esp
        pushl     $0
        call      fesetround
..B1.5:
        movl      $1, %ecx
..B1.6:
        movzwl    10(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        movl      8(%ebp), %esi
        shrl      $31, %esi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %edx
        je        ..B1.88
..B1.7:
        flds      8(%ebp)
        fldz      
        andl      $2147483647, %edi
        fucom     %st(1)
        fnstsw    %ax
        movl      %edi, 20(%esp)
        sahf      
        jp        ..B1.8
        je        ..B1.85
..B1.8:
        cmpl      $2097152, %edi
        jle       ..B1.82
..B1.9:
        testl     %esi, %esi
        je        ..B1.18
..B1.10:
        cmpl      $150, %edx
        jge       ..B1.76
..B1.11:
        flds      .L_2il0floatpacket.2@GOTOFF(%ebx)
        flds      20(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstps     24(%esp)
        flds      24(%esp)
        movl      24(%esp), %edi
        fsubp     %st, %st(2)
        movl      %edi, 16(%esp)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B1.13
        jp        ..B1.13
..B1.12:
        fld1      
        incl      16(%esp)
        fsubrp    %st, %st(2)
..B1.13:
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B1.14
        je        ..B1.73
..B1.14:
        flds      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B1.18
..B1.15:
        fstp      %st(0)
        fstp      %st(0)
        testl     %ecx, %ecx
        je        ..B1.17
..B1.16:
        addl      $4, %esp
        pushl     28(%esp)
        call      fesetround
..B1.17:
        movl      16(%esp), %eax
        notl      %eax
        andl      $1, %eax
        flds      _small_value_32@GOTOFF(%ebx,%eax,4)
        fmuls     .L_2il0floatpacket.5@GOTOFF(%ebx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        fxch      %st(1)
        fcompl    _overflow_boundary@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jae       ..B1.79
..B1.19:
        flds      .L_2il0floatpacket.2@GOTOFF(%ebx)
        testl     %esi, %esi
        je        ..B1.21
..B1.20:
        fstp      %st(1)
        flds      20(%esp)
        fld       %st(1)
        fadd      %st(1), %st
        fstps     24(%esp)
        flds      24(%esp)
        fsub      %st(2), %st
        fsubr     %st(1), %st
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8+_tgamma_sin_table@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      24+_tgamma_sin_table@GOTOFF(%ebx)
        fadd      %st(2), %st
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _tgamma_sin_table@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_tgamma_sin_table@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fldl      40+_tgamma_sin_table@GOTOFF(%ebx)
        fadd      %st(2), %st
        fmul      %st(2), %st
        fldl      56+_tgamma_sin_table@GOTOFF(%ebx)
        fadd      %st(3), %st
        fmulp     %st, %st(3)
        faddl     32+_tgamma_sin_table@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     48+_tgamma_sin_table@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        fadd      %st(1), %st
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.22
..B1.21:
        flds      20(%esp)
        fld       %st(2)
..B1.22:
        fld       %st(1)
        fxch      %st(2)
        fadd      %st(3), %st
        fstps     24(%esp)
        flds      24(%esp)
        movl      24(%esp), %edi
        fsubp     %st, %st(3)
        andl      $1048575, %edi
        fxch      %st(2)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.97
..B1.23:
        fld1      
        decl      %edi
        fsubr     %st, %st(1)
..B1.24:
        lea       1(%edi), %eax
        andl      $1, %eax
        movl      %eax, 28(%esp)
        fxch      %st(2)
        fcoms     .L_2il0floatpacket.4@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jb        ..B1.41
..B1.25:
        fstp      %st(3)
        movl      %edi, %eax
        fsubr     %st(2), %st
        movl      %esi, 16(%esp)
        andl      $-8, %eax
        movl      %edi, %esi
        fld       %st(0)
        andl      $7, %esi
        cmpl      $8, %edi
        movl      %eax, %edi
        fmul      %st(1), %st
        lea       -2(%esi), %edx
        jl        ..L3
        movl      %esi, %edx
..L3:
        sarl      $1, %edi
        addl      %eax, %edi
        sarl      $3, %eax
        addl      %eax, %edi
        movl      16(%esp), %esi
        testl     %edx, %edx
        fldl      _tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fldl      8+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldl      16+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(2)
        faddl     48+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fxch      %st(1)
        faddl     56+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fxch      %st(2)
        faddl     64+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fxch      %st(1)
        fmull     96+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fldl      32+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldl      24+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(1)
        faddl     80+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fxch      %st(1)
        faddl     72+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fldl      40+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddl     88+_tgamma_A_table@GOTOFF(%ebx,%edi,8)
        fmulp     %st, %st(1)
        je        ..B1.30
..B1.26:
        fld       %st(1)
        jle       ..B1.31
..B1.27:
        movl      $1, %eax
..B1.28:
        movl      %eax, 16(%esp)
        incl      %eax
        fildl     16(%esp)
        cmpl      %edx, %eax
        fsubr     %st(4), %st
        fmulp     %st, %st(1)
        jle       ..B1.28
        jmp       ..B1.31
..B1.30:
        fld       %st(1)
..B1.31:
        testl     %esi, %esi
        je        ..B1.38
..B1.32:
        fxch      %st(4)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fdivrp    %st, %st(1)
        flds      .L_2il0floatpacket.7@GOTOFF(%ebx)
        fcomps    8(%ebp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.34
..B1.33:
        fldt      _tgamma_A40_inv@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.34:
        testl     %ecx, %ecx
        je        ..B1.36
..B1.35:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.103:
        fldl      16(%esp)
..B1.36:
        cmpl      $0, 28(%esp)
        je        ..B1.40
..B1.37:
        fchs      
        jmp       ..B1.40
..B1.38:
        fstp      %st(4)
        fstp      %st(2)
        fstp      %st(0)
        testl     %ecx, %ecx
        fmulp     %st, %st(1)
        je        ..B1.40
..B1.39:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.102:
        fldl      16(%esp)
..B1.40:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        fstp      %st(1)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B1.43
        jp        ..B1.43
..B1.42:
        fld       %st(0)
        fadd      %st(2), %st
        jmp       ..B1.44
..B1.43:
        fld       %st(0)
..B1.44:
        fcoml     .L_2il0floatpacket.6@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jb        ..B1.46
..B1.45:
        fstp      %st(3)
        lea       _tgamma_A175_table@GOTOFF(%ebx), %eax
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.53
..B1.46:
        fcoml     .L_2il0floatpacket.8@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jb        ..B1.48
..B1.47:
        fstp      %st(3)
        lea       _tgamma_A150_table@GOTOFF(%ebx), %eax
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.53
..B1.48:
        fldl      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.50
        jp        ..B1.50
..B1.49:
        fstp      %st(0)
        fstp      %st(3)
        fld       %st(1)
        lea       _tgamma_A125_table@GOTOFF(%ebx), %eax
        faddl     _local_minimum@GOTOFF(%ebx)
        fsubrp    %st, %st(3)
        jmp       ..B1.53
..B1.50:
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.52
..B1.51:
        fstp      %st(3)
        lea       _tgamma_A100_table@GOTOFF(%ebx), %eax
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.53
..B1.52:
        fstp      %st(0)
..B1.53:
        fldl      (%eax)
        testl     %edx, %edx
        je        ..B1.55
..B1.54:
        fldl      56(%eax)
        fmul      %st(4), %st
        faddl     48(%eax)
        fmul      %st(4), %st
        faddl     40(%eax)
        fmul      %st(4), %st
        faddl     32(%eax)
        fmul      %st(4), %st
        faddl     24(%eax)
        fmul      %st(4), %st
        faddl     16(%eax)
        fmul      %st(4), %st
        faddl     8(%eax)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        jmp       ..B1.56
..B1.55:
        fstp      %st(3)
..B1.56:
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B1.65
        jp        ..B1.65
..B1.57:
        testl     %esi, %esi
        je        ..B1.62
..B1.58:
        fstp      %st(0)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fdivp     %st, %st(1)
        testl     %ecx, %ecx
        je        ..B1.60
..B1.59:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.101:
        fldl      16(%esp)
..B1.60:
        cmpl      $0, 28(%esp)
        je        ..B1.72
..B1.61:
        fchs      
        jmp       ..B1.72
..B1.62:
        fstp      %st(3)
        fstp      %st(0)
        fdivp     %st, %st(1)
        testl     %ecx, %ecx
        jne       ..B1.70
        jmp       ..B1.72
..B1.65:
        testl     %esi, %esi
        je        ..B1.69
..B1.66:
        fmulp     %st, %st(3)
        testl     %ecx, %ecx
        fxch      %st(1)
        fmulp     %st, %st(2)
        fdivp     %st, %st(1)
        jne       ..B1.70
        jmp       ..B1.72
..B1.69:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        testl     %ecx, %ecx
        je        ..B1.72
..B1.70:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.98:
        fldl      16(%esp)
..B1.72:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.73:
        fstp      %st(1)
        testl     %ecx, %ecx
        je        ..B1.75
..B1.74:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.106:
        fldl      16(%esp)
..B1.75:
        fdiv      %st(0), %st
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.76:
        fstp      %st(1)
        testl     %ecx, %ecx
        je        ..B1.78
..B1.77:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.105:
        fldl      16(%esp)
..B1.78:
        fdiv      %st(0), %st
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.79:
        fstp      %st(0)
        testl     %ecx, %ecx
        je        ..B1.81
..B1.80:
        addl      $4, %esp
        pushl     28(%esp)
        call      fesetround
..B1.81:
        flds      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmul      %st(0), %st
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.82:
        fstp      %st(0)
        fstp      %st(0)
        testl     %ecx, %ecx
        je        ..B1.84
..B1.83:
        addl      $4, %esp
        pushl     28(%esp)
        call      fesetround
..B1.84:
        flds      _large_value_32@GOTOFF(%ebx,%esi,4)
        fmuls     .L_2il0floatpacket.0@GOTOFF(%ebx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.85:
        fstp      %st(1)
        testl     %ecx, %ecx
        je        ..B1.87
..B1.86:
        addl      $4, %esp
        pushl     28(%esp)
        fstpl     16(%esp)
        call      fesetround
..B1.104:
        fldl      16(%esp)
..B1.87:
        fdivrs    _ones@GOTOFF(%ebx,%esi,4)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.88:
        testl     %ecx, %ecx
        je        ..B1.90
..B1.89:
        addl      $4, %esp
        pushl     28(%esp)
        call      fesetround
..B1.90:
        testl     %esi, %esi
        je        ..B1.92
..B1.91:
        testl     $8388607, 8(%ebp)
        je        ..B1.93
..B1.92:
        flds      8(%ebp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.93:
        fldz      
        fdivrs    .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.97:
        fld1      
        jmp       ..B1.24
        .align    16,0x90
	.type	tgammaf,@function
	.size	tgammaf,.-tgammaf
	.data
# -- End  tgammaf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.6:
	.long	0x00000000,0x3ffc0000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3ff80000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3ff40000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x71800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x80000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x4b400000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0xc22c0000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0x40000000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
.L_2il0floatpacket.5:
	.long	0x0d800000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,4
	.align 4
.L_2il0floatpacket.7:
	.long	0xc2200000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_overflow_boundary:
	.long	0
	.long	1078035746
	.type	_overflow_boundary,@object
	.size	_overflow_boundary,8
	.align 4
_tgamma_sin_table:
	.long	1980114658
	.long	3200337074
	.long	804494867
	.long	1049689241
	.long	2884431818
	.long	1079329239
	.long	930502348
	.long	3223780109
	.long	1245824743
	.long	1080878479
	.long	2510431012
	.long	1073721695
	.long	1661126174
	.long	1078219640
	.long	1205599443
	.long	3223573914
	.type	_tgamma_sin_table,@object
	.size	_tgamma_sin_table,64
	.align 4
_tgamma_A_table:
	.long	3121394612
	.long	1074393041
	.long	2029996066
	.long	1073722195
	.long	4137005464
	.long	1071238760
	.long	3372276227
	.long	3220751114
	.long	68401302
	.long	3222286887
	.long	1275947047
	.long	3223263002
	.long	4031994882
	.long	1074665426
	.long	1089993416
	.long	1074817170
	.long	427005612
	.long	1075089294
	.long	1508975772
	.long	1075601433
	.long	2111623053
	.long	1076192020
	.long	75538972
	.long	1077100653
	.long	2171869559
	.long	1055005400
	.long	2919987515
	.long	1074143787
	.long	860307162
	.long	1073890599
	.long	2407232453
	.long	1073038722
	.long	1468490975
	.long	3216757264
	.long	846192051
	.long	3221294835
	.long	402234383
	.long	3222573752
	.long	2566208764
	.long	1073763360
	.long	162360449
	.long	1073949715
	.long	1512348533
	.long	1074371728
	.long	2920174397
	.long	1074968946
	.long	3785085665
	.long	1075671857
	.long	1922428759
	.long	1076496000
	.long	2446382284
	.long	1072101083
	.long	42366358
	.long	1073746704
	.long	3587290866
	.long	1073390434
	.long	1169958761
	.long	1072607731
	.long	3891474799
	.long	3217275878
	.long	1452792685
	.long	3221010072
	.long	1006385979
	.long	3222363118
	.long	3663632771
	.long	1072778759
	.long	3212960448
	.long	1073036150
	.long	2202365811
	.long	1073636095
	.long	2023822532
	.long	1074258740
	.long	40477660
	.long	1075053854
	.long	658935515
	.long	1076010306
	.long	932572503
	.long	1105834679
	.long	1233355998
	.long	1073412029
	.long	3033646034
	.long	1073097432
	.long	3149182759
	.long	1072206908
	.long	1726027606
	.long	3217766868
	.long	3439348786
	.long	3220880557
	.long	2080997065
	.long	3222253033
	.long	843307201
	.long	1072200111
	.long	3138702878
	.long	1072613342
	.long	3610734718
	.long	1073142430
	.long	2651617324
	.long	1073914526
	.long	2611481547
	.long	1074796471
	.long	3284459136
	.long	1075745821
	.long	1365755587
	.long	1144167771
	.long	2699624028
	.long	1073212595
	.long	2232824171
	.long	1072922970
	.long	3659224963
	.long	1071959670
	.long	3026238542
	.long	3218100813
	.long	4240441539
	.long	3220810712
	.long	1772362075
	.long	3222132889
	.long	3742687744
	.long	1071863890
	.long	56227394
	.long	1072220001
	.long	969667939
	.long	1072882737
	.long	880059910
	.long	1073714908
	.long	247554419
	.long	1074515829
	.long	48918556
	.long	1075495246
	.long	2601377264
	.long	1185936586
	.long	3625529495
	.long	1073077476
	.long	3298250746
	.long	1072804056
	.long	2971501690
	.long	1071788510
	.long	2950162741
	.long	3218231788
	.long	26528490
	.long	3220765509
	.long	3761284804
	.long	3222050392
	.long	3800626879
	.long	1071658634
	.long	1108715276
	.long	1071977390
	.long	286829382
	.long	1072720005
	.long	3141975933
	.long	1073477575
	.long	4145290123
	.long	1074329952
	.long	2167683451
	.long	1075329952
	.long	0
	.long	1072693248
	.type	_tgamma_A_table,@object
	.size	_tgamma_A_table,624
	.align 4
_tgamma_A175_table:
	.long	1682645026
	.long	1072688670
	.long	3753006229
	.long	3219243438
	.long	177700005
	.long	1072453288
	.long	3289039035
	.long	3219374666
	.long	2492560830
	.long	1071326765
	.long	123078934
	.long	3217486055
	.long	3499570675
	.long	1068068407
	.long	1362583934
	.long	3212265128
	.type	_tgamma_A175_table,@object
	.size	_tgamma_A175_table,64
	.align 4
_tgamma_A150_table:
	.long	988281800
	.long	1072692321
	.long	502740408
	.long	3219277207
	.long	2058512241
	.long	1072588117
	.long	2109547492
	.long	3219675970
	.long	3371209031
	.long	1071892748
	.long	3191518250
	.long	3218447665
	.long	3816909947
	.long	1069498883
	.long	1366906983
	.long	3214177035
	.type	_tgamma_A150_table,@object
	.size	_tgamma_A150_table,64
	.align 4
_tgamma_A125_table:
	.long	2191760034
	.long	1072453340
	.long	2642694891
	.long	3186525859
	.long	1706815263
	.long	1071344724
	.long	1896485049
	.long	3217079006
	.long	441733475
	.long	1069848224
	.long	978611902
	.long	3216500728
	.long	351495825
	.long	1068442485
	.long	1680249708
	.long	3216253219
	.type	_tgamma_A125_table,@object
	.size	_tgamma_A125_table,64
	.align 4
_local_minimum:
	.long	2371549438
	.long	1071483745
	.type	_local_minimum,@object
	.size	_local_minimum,8
	.align 4
_tgamma_A100_table:
	.long	4293468318
	.long	1072693247
	.long	3444857508
	.long	3219290252
	.long	3510931740
	.long	1072670280
	.long	3644082616
	.long	3219982336
	.long	2685454907
	.long	1072646231
	.long	4151446349
	.long	3220059936
	.long	4004008463
	.long	1072263166
	.long	1217534056
	.long	3218706582
	.type	_tgamma_A100_table,@object
	.size	_tgamma_A100_table,64
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 2
_tgamma_A40_inv:
	.word	64725
	.word	58913
	.word	22381
	.word	51879
	.word	16232
	.word	0
	.type	_tgamma_A40_inv,@object
	.size	_tgamma_A40_inv,12
	.data
	.hidden fesetround
	.hidden fegetround
	.section .note.GNU-stack, ""
# End
