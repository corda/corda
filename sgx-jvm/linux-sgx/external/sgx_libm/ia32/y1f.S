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
	.file "y1f.c"
	.text
..TXTST0:
# -- Begin  y1f
	.text
       .align    16,0x90
	.globl y1f
y1f:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %ebx
        subl      $124, %esp
        movl      8(%ebp), %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        lea       -1(%edx), %eax
        cmpl      $2139095039, %eax
        jae       ..B1.19
..B1.2:
        cmpl      $1100816743, %edx
        jae       ..B1.17
..B1.3:
        cmpl      $1099170276, %edx
        jae       ..B1.16
..B1.4:
        cmpl      $1096140558, %edx
        jae       ..B1.15
..B1.5:
        cmpl      $1092849338, %edx
        jae       ..B1.14
..B1.6:
        cmpl      $1088602350, %edx
        jae       ..B1.13
..B1.7:
        cmpl      $1081952922, %edx
        jae       ..B1.12
..B1.8:
        cmpl      $1071812444, %edx
        jae       ..B1.11
..B1.9:
        flds      8(%ebp)
        fstl      (%esp)
        fstps     32(%esp)
        call      __libm_log_k32
..B1.29:
        flds      32(%esp)
..B1.10:
        fldl      _tonpi@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fdiv      %st(3), %st
        fldl      40+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldl      32+_Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     24+_Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     16+_Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     8+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     _Q1@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      40+_Q2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldl      32+_Q2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     24+_Q2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     16+_Q2@GOTOFF(%ebx)
        fmulp     %st, %st(5)
        faddl     8+_Q2@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     _Q2@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        flds      8(%ebp)
        fldl      120+_P1@GOTOFF(%ebx)
        fldl      104+_P1@GOTOFF(%ebx)
        fldl      112+_P1@GOTOFF(%ebx)
        fldl      96+_P1@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     128+_P1@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     88+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     72+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24+_P1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(2), %st
        fxch      %st(4)
        faddl     8+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(4)
        faddl     64+_P1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     80+_P1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     32+_P1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     48+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        faddl     _P1@GOTOFF(%ebx)
        fxch      %st(2)
        faddl     16+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.12:
        flds      8(%ebp)
        fldl      104+_P2@GOTOFF(%ebx)
        fldl      88+_P2@GOTOFF(%ebx)
        fldl      96+_P2@GOTOFF(%ebx)
        fldl      80+_P2@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     112+_P2@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     72+_P2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56+_P2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40+_P2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24+_P2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(2), %st
        fxch      %st(4)
        faddl     8+_P2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_P2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     48+_P2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     32+_P2@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddl     16+_P2@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     _P2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        flds      8(%ebp)
        fldl      96+_P3@GOTOFF(%ebx)
        fldl      80+_P3@GOTOFF(%ebx)
        fldl      88+_P3@GOTOFF(%ebx)
        fldl      72+_P3@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     104+_P3@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     64+_P3@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     48+_P3@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     32+_P3@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     16+_P3@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     56+_P3@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     _P3@GOTOFF(%ebx)
        fxch      %st(5)
        faddl     40+_P3@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     24+_P3@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     8+_P3@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        flds      8(%ebp)
        fldl      104+_P4@GOTOFF(%ebx)
        fldl      88+_P4@GOTOFF(%ebx)
        fldl      96+_P4@GOTOFF(%ebx)
        fldl      80+_P4@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     112+_P4@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     72+_P4@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56+_P4@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40+_P4@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24+_P4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(2), %st
        fxch      %st(4)
        faddl     8+_P4@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_P4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     48+_P4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     32+_P4@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddl     16+_P4@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     _P4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        flds      8(%ebp)
        fldl      96+_P5@GOTOFF(%ebx)
        fldl      80+_P5@GOTOFF(%ebx)
        fldl      88+_P5@GOTOFF(%ebx)
        fldl      72+_P5@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     104+_P5@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     64+_P5@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     48+_P5@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     32+_P5@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     16+_P5@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     56+_P5@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     _P5@GOTOFF(%ebx)
        fxch      %st(5)
        faddl     40+_P5@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     24+_P5@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     8+_P5@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        flds      8(%ebp)
        fldl      104+_P6@GOTOFF(%ebx)
        fldl      88+_P6@GOTOFF(%ebx)
        fldl      96+_P6@GOTOFF(%ebx)
        fldl      80+_P6@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     112+_P6@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     72+_P6@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56+_P6@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40+_P6@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24+_P6@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(2), %st
        fxch      %st(4)
        faddl     8+_P6@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_P6@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     48+_P6@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     32+_P6@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddl     16+_P6@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     _P6@GOTOFF(%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        flds      8(%ebp)
        lea       32(%esp), %eax
        fld1      
        fdiv      %st(1), %st
        lea       40(%esp), %edx
        fstl      16(%eax)
        fldl      _val_16_0@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstl      24(%eax)
        fmul      %st(0), %st
        fstl      32(%eax)
        fmul      %st(0), %st
        fxch      %st(1)
        fstpl     (%esp)
        movl      %eax, 8(%esp)
        movl      %edx, 12(%esp)
        movl      $-3, 16(%esp)
        fstpl     40(%eax)
        call      __libm_sincos_k32
..B1.18:
        fldl      72(%esp)
        fldl      64(%esp)
        fldl      _tonpi@GOTOFF(%ebx)
        fmull     48(%esp)
        fsqrt     
        fldl      40+_PP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldl      32+_PP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     24+_PP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     16+_PP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     8+_PP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     _PP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldl      40+_QP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        fmull     32(%esp)
        fxch      %st(1)
        faddl     24+_QP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldl      32+_QP@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     8+_QP@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddl     16+_QP@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddl     _QP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmull     56(%esp)
        fmull     40(%esp)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.25
..B1.20:
        testl     %eax, %eax
        je        ..B1.26
..B1.21:
        fldz      
        testl     $-2147483648, %edx
        je        ..B1.24
..B1.23:
        flds      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.24:
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        flds      8(%ebp)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        fldz      
        fdivrs    .L_2il0floatpacket.0@GOTOFF(%ebx)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	y1f,@function
	.size	y1f,.-y1f
	.data
# -- End  y1f
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x7f800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_Q1:
	.long	330682653
	.long	3217627238
	.long	1311559603
	.long	1068225431
	.long	1711886415
	.long	3211277751
	.long	3301707240
	.long	1058195349
	.long	1381458166
	.long	3199245881
	.long	361785213
	.long	1044511851
	.type	_Q1,@object
	.size	_Q1,48
	.align 4
_Q2:
	.long	4294700894
	.long	1071644671
	.long	4245872852
	.long	3215982591
	.long	2793978972
	.long	1063605588
	.long	1731774639
	.long	3205263755
	.long	824990963
	.long	1051113520
	.long	1573521749
	.long	3191272273
	.type	_Q2,@object
	.size	_Q2,48
	.align 4
_P1:
	.long	3588334709
	.long	1014823416
	.long	1143617537
	.long	1071688264
	.long	543393268
	.long	3216922360
	.long	1079832790
	.long	3214987951
	.long	1415988155
	.long	3212027618
	.long	2493495630
	.long	1065248537
	.long	1666720954
	.long	3211084359
	.long	543676354
	.long	1062304274
	.long	4191589146
	.long	3208689738
	.long	2170605073
	.long	1060053158
	.long	3542706363
	.long	3206309752
	.long	3704389215
	.long	1057513307
	.long	2063423420
	.long	3203511534
	.long	3202091398
	.long	1054023270
	.long	2674374220
	.long	3198818668
	.long	2780141937
	.long	1047561374
	.long	3571446339
	.long	1073845182
	.type	_P1,@object
	.size	_P1,136
	.align 4
_P2:
	.long	2989758108
	.long	1017408064
	.long	1458640109
	.long	3218458565
	.long	2240523794
	.long	1067453343
	.long	773815536
	.long	1068111321
	.long	2743923681
	.long	3211856434
	.long	3232814431
	.long	3210853997
	.long	2451601872
	.long	1059272649
	.long	1805313442
	.long	1057512342
	.long	1838988672
	.long	3200779931
	.long	3139341808
	.long	3198309937
	.long	844655003
	.long	1046186753
	.long	3216852589
	.long	1043859826
	.long	2659303056
	.long	3187506951
	.long	2880143704
	.long	1034033463
	.long	1317515310
	.long	1075165182
	.type	_P2,@object
	.size	_P2,120
	.align 4
_P3:
	.long	280256326
	.long	3163962304
	.long	1070207352
	.long	1070686105
	.long	878012560
	.long	3213896505
	.long	4238571140
	.long	3215341917
	.long	3113338666
	.long	1063505618
	.long	910161668
	.long	1063287399
	.long	1478416875
	.long	3206238429
	.long	3015160168
	.long	3204904859
	.long	3974776004
	.long	1052866426
	.long	579855125
	.long	1050851686
	.long	261378128
	.long	3193590641
	.long	798233178
	.long	3191027115
	.long	1498363474
	.long	1038832298
	.long	2925619636
	.long	1075917095
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	738390343
	.long	1012897491
	.long	2706267969
	.long	3217932622
	.long	4014538048
	.long	1065632415
	.long	3590329970
	.long	1067673559
	.long	671215353
	.long	3210335979
	.long	2374781669
	.long	3210593529
	.long	1890699418
	.long	1058215403
	.long	1890155166
	.long	1057321344
	.long	1713507622
	.long	3199871308
	.long	2862637523
	.long	3198281783
	.long	1122846306
	.long	1045733660
	.long	622299794
	.long	1043564297
	.long	2771483851
	.long	3185854918
	.long	1632198108
	.long	3183235242
	.long	953430534
	.long	1076330385
	.type	_P4,@object
	.size	_P4,120
	.align 4
_P5:
	.long	342107947
	.long	1017254688
	.long	3187585117
	.long	1070231586
	.long	1823227594
	.long	3212600587
	.long	444207028
	.long	3215024921
	.long	1802122913
	.long	1062365956
	.long	1966738039
	.long	1062934367
	.long	3649894624
	.long	3205237410
	.long	1726294277
	.long	3204703097
	.long	1880085380
	.long	1051980794
	.long	1949762981
	.long	1050731607
	.long	2695921962
	.long	3192939649
	.long	1096030507
	.long	3190939319
	.long	3356663664
	.long	1038219909
	.long	2296284299
	.long	1076743037
	.type	_P5,@object
	.size	_P5,112
	.align 4
_P6:
	.long	1104511903
	.long	3165887820
	.long	3273918356
	.long	3217557377
	.long	1767833442
	.long	1064652506
	.long	4164235278
	.long	1067433581
	.long	3499046825
	.long	3209421592
	.long	1038038689
	.long	3210276846
	.long	3702826228
	.long	1057329476
	.long	3651609877
	.long	1057129169
	.long	1779967631
	.long	3199161987
	.long	3308825176
	.long	3198147781
	.long	897891533
	.long	1045052198
	.long	2444111938
	.long	1043454098
	.long	144575532
	.long	3185257015
	.long	1701846534
	.long	3183129264
	.long	1767841339
	.long	1077021468
	.type	_P6,@object
	.size	_P6,120
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_PP:
	.long	551
	.long	1072693248
	.long	4267608614
	.long	1061027839
	.long	3690881252
	.long	3200414971
	.long	3933039373
	.long	1046848465
	.long	3246008603
	.long	3189439465
	.long	1689113401
	.long	1037483563
	.type	_PP,@object
	.size	_PP,48
	.align 4
_QP:
	.long	4294954956
	.long	1066926079
	.long	4141388976
	.long	3204071423
	.long	1500123100
	.long	1049740228
	.long	3264213437
	.long	3191856517
	.long	1489731078
	.long	1039824630
	.long	1807021260
	.long	3183130166
	.type	_QP,@object
	.size	_QP,48
	.data
	.hidden __libm_sincos_k32
	.hidden __libm_log_k32
	.section .note.GNU-stack, ""
# End
