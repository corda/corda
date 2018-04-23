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
	.file "cosdl.c"
	.text
..TXTST0:
# -- Begin  cosdl
	.text
       .align    16,0x90
	.globl cosdl
cosdl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
..B1.2:
        fnstcw    30(%esp)
..B1.3:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $3840, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.4:
        andl      $-3841, %edx
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.5:
        fldcw     28(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    16(%ebp), %esi
        andl      $32767, %esi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $16446, %esi
        jge       ..B1.19
..B1.8:
        cmpl      $16318, %esi
        jge       ..B1.12
..B1.9:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fldt      8(%ebp)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        testl     %edi, %edi
        fmuls     ones@GOTOFF(%ebx,%eax,4)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
        je        ..B1.11
..B1.10:
        fldcw     30(%esp)
..B1.11:
        fldt      (%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.12:
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fldt      _Rcp90@GOTOFF(%ebx)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fxch      %st(4)
        fmuls     ones@GOTOFF(%ebx,%edx,4)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(3), %st
        fstpt     92(%esp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      92(%esp)
        movl      92(%esp), %edx
        fsubp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        incl      %edx
        movl      %edx, %ecx
        fsubrp    %st, %st(1)
        andl      $2, %ecx
        fucom     %st(1)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        shrl      $1, %ecx
        sahf      
        jp        ..B1.13
        je        ..B1.31
..B1.13:
        fldt      .L_2il0floatpacket.4@GOTOFF(%ebx)
        testb     $1, %dl
        fmul      %st(1), %st
        fld       %st(1)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(2)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fld       %st(1)
        fld       %st(4)
        fldl      _TWO_53H@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fld       %st(5)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fstpt     80(%esp)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        flds      ones@GOTOFF(%ebx,%ecx,4)
        fstps     12(%esp)
        je        ..B1.15
..B1.14:
        fstp      %st(6)
        fstp      %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fmul      %st, %st(1)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(4)
        fstpt     (%esp)
        fldt      (%esp)
        fld       %st(3)
        fmul      %st(7), %st
        fxch      %st(1)
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fldt      84+_cosdl_poly_coeff@GOTOFF(%ebx)
        fmul      %st(5), %st
        fldt      60+_cosdl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      36+_cosdl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_cosdl_poly_coeff@GOTOFF(%ebx)
        fmul      %st(6), %st
        fldt      48+_cosdl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      24+_cosdl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      24+_cosdl_mp_poly_coeff@GOTOFF(%ebx)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fldl      8+_cosdl_mp_poly_coeff@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      16+_cosdl_mp_poly_coeff@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      _cosdl_mp_poly_coeff@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmulp     %st, %st(2)
        fld       %st(1)
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        flds      12(%esp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.16
..B1.15:
        fld       %st(6)
        fmul      %st(2), %st
        fstpt     32(%esp)
        fld       %st(4)
        fmul      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(6)
        fmul      %st(7), %st
        fxch      %st(2)
        fstpt     16(%esp)
        fxch      %st(3)
        fstpt     (%esp)
        fldt      80(%esp)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(4)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      96+_sindl_poly_coeff@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      72+_sindl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48+_sindl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_sindl_poly_coeff@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      60+_sindl_poly_coeff@GOTOFF(%ebx)
        fldt      36+_sindl_poly_coeff@GOTOFF(%ebx)
        fldt      16(%esp)
        fmulp     %st, %st(3)
        fldt      84+_sindl_poly_coeff@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fmul      %st, %st(1)
        fldl      24+_sindl_mp_poly_coeff@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      8+_sindl_mp_poly_coeff@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldl      16+_sindl_mp_poly_coeff@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      _sindl_mp_poly_coeff@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmulp     %st, %st(2)
        fld       %st(1)
        fldt      48(%esp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        flds      12(%esp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     64(%esp)
..B1.16:
        testl     %edi, %edi
        je        ..B1.18
..B1.17:
        fldcw     30(%esp)
..B1.18:
        fldt      64(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        cmpl      $32767, %esi
        je        ..B1.37
..B1.20:
        lea       -16446(%esi), %ecx
        cmpl      $14, %ecx
        jle       ..B1.22
..B1.21:
        addl      $-16449, %esi
        movl      $715827883, %eax
        imull     %esi
        movl      %esi, %ecx
        sarl      $1, %edx
        sarl      $31, %ecx
        subl      %ecx, %edx
        lea       (,%edx,8), %ecx
        lea       (%ecx,%edx,4), %eax
        subl      %eax, %esi
        lea       3(%esi), %ecx
..B1.22:
        movl      %edi, (%esp)
        movl      $381774871, %eax
        movl      12(%ebp), %edi
        movl      %edi, %esi
        shll      $8, %esi
        andl      $-16777216, %edi
        movl      %esi, %edx
        shrl      $3, %edx
        mull      %edx
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        movl      %edi, %edx
        shrl      $19, %edi
        addl      %eax, %esi
        movl      $381774871, %eax
        shrl      $16, %edx
        movl      %edx, 4(%esp)
        mull      %edi
        shrl      $2, %edx
        imull     $-360, %edx, %edi
        movl      4(%esp), %eax
        addl      %edi, %eax
        movl      8(%ebp), %edi
        movl      %edi, %edx
        shrl      $3, %edx
        addl      %eax, %esi
        movl      $381774871, %eax
        mull      %edx
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %edi
        movl      $-1240768329, %eax
        addl      %edi, %esi
        shll      %cl, %esi
        imull     %esi
        addl      %esi, %edx
        movl      %esi, %ecx
        sarl      $8, %edx
        sarl      $31, %ecx
        subl      %ecx, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %esi
        movl      (%esp), %edi
        cmpl      $180, %esi
        jl        ..B1.24
..B1.23:
        addl      $-180, %esi
        movl      $3, %eax
        jmp       ..B1.25
..B1.24:
        movl      $1, %eax
..B1.25:
        cmpl      $90, %esi
        jl        ..B1.27
..B1.26:
        incl      %eax
        addl      $-90, %esi
..B1.27:
        movl      %eax, %edx
        andl      $1, %eax
        shll      $4, %eax
        andl      $2, %edx
        shll      $5, %esi
        addl      %esi, %eax
        shrl      $1, %edx
        fldl      __libm_sindl_cosdl_table@GOTOFF(%eax,%ebx)
        faddl     8+__libm_sindl_cosdl_table@GOTOFF(%ebx,%eax)
        fmuls     ones@GOTOFF(%ebx,%edx,4)
        fstpt     (%esp)
..B1.28:
        testl     %edi, %edi
        je        ..B1.30
..B1.29:
        fldcw     30(%esp)
..B1.30:
        fldt      (%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        fstp      %st(0)
        testb     $1, %dl
        je        ..B1.33
..B1.32:
        flds      ones@GOTOFF(%ebx,%ecx,4)
        fstps     (%esp)
        jmp       ..B1.34
..B1.33:
        fldz      
        fstps     (%esp)
..B1.34:
        testl     %edi, %edi
        je        ..B1.36
..B1.35:
        fldcw     30(%esp)
..B1.36:
        flds      (%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.37:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.28
..B1.38:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	cosdl,@function
	.size	cosdl,.-cosdl
	.data
# -- End  cosdl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x3e,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xb4,0x05,0x40,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
_cosdl_poly_coeff:
	.word	52350
	.word	41213
	.word	3800
	.word	40885
	.word	49138
	.word	0
	.word	28613
	.word	7908
	.word	35668
	.word	34008
	.word	16355
	.word	0
	.word	19927
	.word	58728
	.word	49885
	.word	45261
	.word	49106
	.word	0
	.word	32884
	.word	22035
	.word	23267
	.word	64541
	.word	16320
	.word	0
	.word	36401
	.word	51897
	.word	8309
	.word	57265
	.word	49070
	.word	0
	.word	34286
	.word	2728
	.word	41564
	.word	34642
	.word	16284
	.word	0
	.word	63248
	.word	18030
	.word	35596
	.word	60796
	.word	49032
	.word	0
	.word	52149
	.word	21294
	.word	63985
	.word	40123
	.word	16245
	.word	0
	.type	_cosdl_poly_coeff,@object
	.size	_cosdl_poly_coeff,96
	.align 16
_cosdl_mp_poly_coeff:
	.long	3675529145
	.long	3206805153
	.long	2134983071
	.long	3151100167
	.long	1787026573
	.long	1043372817
	.long	205083639
	.long	988746860
	.type	_cosdl_mp_poly_coeff,@object
	.size	_cosdl_mp_poly_coeff,32
	.align 16
_sindl_poly_coeff:
	.word	51374
	.word	38121
	.word	13586
	.word	36602
	.word	16377
	.word	0
	.word	50116
	.word	41339
	.word	4204
	.word	60892
	.word	49130
	.word	0
	.word	33704
	.word	2155
	.word	42839
	.word	60780
	.word	16346
	.word	0
	.word	21250
	.word	19076
	.word	27901
	.word	57780
	.word	49097
	.word	0
	.word	9076
	.word	49244
	.word	613
	.word	64083
	.word	16311
	.word	0
	.word	40572
	.word	30418
	.word	36251
	.word	46520
	.word	49061
	.word	0
	.word	3227
	.word	25505
	.word	5540
	.word	47626
	.word	16274
	.word	0
	.word	60933
	.word	3300
	.word	57416
	.word	36218
	.word	49023
	.word	0
	.word	45811
	.word	42646
	.word	37125
	.word	42185
	.word	16235
	.word	0
	.type	_sindl_poly_coeff,@object
	.size	_sindl_poly_coeff,108
	.space 4, 0x00 	# pad
	.align 16
_sindl_mp_poly_coeff:
	.long	2723323193
	.long	1066524486
	.long	2863989530
	.long	1008058840
	.long	227815288
	.long	3199056770
	.long	3752327299
	.long	3142458725
	.type	_sindl_mp_poly_coeff,@object
	.size	_sindl_mp_poly_coeff,32
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 2
_Rcp90:
	.word	46603
	.word	2912
	.word	24758
	.word	46603
	.word	16376
	.word	0
	.type	_Rcp90,@object
	.size	_Rcp90,12
	.data
	.hidden __libm_sindl_cosdl_table
	.section .note.GNU-stack, ""
# End
