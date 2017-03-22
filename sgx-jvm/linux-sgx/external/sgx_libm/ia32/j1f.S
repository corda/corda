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
	.file "j1f.c"
	.text
..TXTST0:
# -- Begin  j1f
	.text
       .align    16,0x90
	.globl j1f
j1f:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %ebx
        subl      $120, %esp
        movl      8(%ebp), %esi
        movl      %esi, %eax
        andl      $2147483647, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2139095040, %eax
        jae       ..B1.37
..B1.2:
        cmpl      $1099993482, %eax
        jae       ..B1.32
..B1.3:
        cmpl      $1075439785, %eax
        jae       ..B1.19
..B1.4:
        cmpl      $1048576000, %eax
        jae       ..B1.15
..B1.5:
        cmpl      $998244352, %eax
        jae       ..B1.14
..B1.6:
        cmpl      $838860800, %eax
        jae       ..B1.13
..B1.7:
        shrl      $31, %esi
        cmpl      $16777216, %eax
        jb        ..B1.9
..B1.8:
        flds      8(%ebp)
        fmuls     .L_2il0floatpacket.1@GOTOFF(%ebx)
        flds      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fstps     32(%esp)
        flds      32(%esp)
        fmuls     .L_2il0floatpacket.3@GOTOFF(%ebx)
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        testl     %eax, %eax
        je        ..B1.11
..B1.10:
        flds      _small_value_32@GOTOFF(%ebx,%esi,4)
        fmuls     .L_2il0floatpacket.4@GOTOFF(%ebx)
        fsts      32(%esp)
        flds      8(%ebp)
        fmuls     .L_2il0floatpacket.0@GOTOFF(%ebx)
        fsubp     %st, %st(1)
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        flds      8(%ebp)
..B1.12:
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        flds      8(%ebp)
        fmuls     .L_2il0floatpacket.0@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8+_Q2@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _Q2@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        flds      8(%ebp)
        fmuls     .L_2il0floatpacket.0@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      24+_Q2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      16+_Q2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_Q2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddl     _Q2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        flds      8(%ebp)
        testl     $-2147483648, %esi
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      88+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      72+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldl      64+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldl      80+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     32+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     48+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        faddl     _Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     16+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        je        ..B1.18
..B1.16:
        fchs      
..B1.18:
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        flds      8(%ebp)
        andl      $-2147483648, %esi
        fabs      
        cmpl      $1097786634, %eax
        jae       ..B1.27
..B1.20:
        cmpl      $1091204523, %eax
        jae       ..B1.24
..B1.21:
        cmpl      $1085318267, %eax
        jae       ..B1.23
..B1.22:
        lea       _P1@GOTOFF(%ebx), %eax
        jmp       ..B1.28
..B1.23:
        lea       _P2@GOTOFF(%ebx), %eax
        jmp       ..B1.28
..B1.24:
        cmpl      $1094494752, %eax
        jae       ..B1.26
..B1.25:
        lea       _P3@GOTOFF(%ebx), %eax
        jmp       ..B1.28
..B1.26:
        lea       _P4@GOTOFF(%ebx), %eax
        jmp       ..B1.28
..B1.27:
        lea       _P5@GOTOFF(%ebx), %eax
..B1.28:
        fldl      104(%eax)
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      96(%eax)
        fmul      %st(1), %st
        fldl      80(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     64(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16(%eax)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldl      72(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%eax)
        fxch      %st(1)
        faddl     40(%eax)
        fmul      %st(2), %st
        fldl      88(%eax)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     8(%eax)
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     56(%eax)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddl     24(%eax)
        fmulp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        je        ..B1.31
..B1.29:
        fchs      
..B1.31:
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        flds      8(%ebp)
        lea       40(%esp), %eax
        fabs      
        lea       48(%esp), %edx
        fld1      
        fdiv      %st(1), %st
        andl      $-2147483648, %esi
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
..B1.33:
        fldl      80(%esp)
        testl     %esi, %esi
        fldl      72(%esp)
        fldl      _tonpi@GOTOFF(%ebx)
        fmull     56(%esp)
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
        fmull     48(%esp)
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
        fmull     64(%esp)
        fmull     40(%esp)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        je        ..B1.36
..B1.34:
        fchs      
..B1.36:
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.37:
        ja        ..B1.39
..B1.38:
        shrl      $31, %esi
        flds      _zeros@GOTOFF(%ebx,%esi,4)
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        flds      8(%ebp)
..B1.40:
        addl      $120, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	j1f,@function
	.size	j1f,.-j1f
	.data
# -- End  j1f
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3e500000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x3f000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x4d800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x31800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0x0d800000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_Q2:
	.long	4294942533
	.long	3219128319
	.long	1380922587
	.long	1068848469
	.long	4229217063
	.long	3212603831
	.long	2485211372
	.long	1060546867
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_Q1:
	.long	4294875887
	.long	1071644671
	.long	4170968197
	.long	1038133990
	.long	82133904
	.long	3215982592
	.long	2332230043
	.long	1043853917
	.long	2564340116
	.long	1063605580
	.long	220691470
	.long	1046506888
	.long	254563561
	.long	3205265147
	.long	694808779
	.long	1046622203
	.long	4019568744
	.long	1051070668
	.long	1556554770
	.long	1044540217
	.long	1786312313
	.long	3191946521
	.long	1750363188
	.long	1039907362
	.type	_Q1,@object
	.size	_Q1,96
	.align 4
_P1:
	.long	3288017007
	.long	3163666881
	.long	1479173198
	.long	3218720463
	.long	2681037136
	.long	1068165283
	.long	482223942
	.long	1068193949
	.long	1641242500
	.long	3212130132
	.long	3602486439
	.long	3210890035
	.long	1295520927
	.long	1059513544
	.long	2893642677
	.long	1057503730
	.long	2144568350
	.long	3200875225
	.long	914896231
	.long	3198366754
	.long	1774974526
	.long	1046581392
	.long	3090631217
	.long	1043566169
	.long	3310618599
	.long	3186783429
	.long	1974431497
	.long	1074702165
	.type	_P1,@object
	.size	_P1,112
	.align 4
_P2:
	.long	216484910
	.long	1015040375
	.long	3011708428
	.long	1070806296
	.long	3332724636
	.long	3214272269
	.long	3160644008
	.long	3215461507
	.long	2925435221
	.long	1063888050
	.long	725350898
	.long	1063337916
	.long	297499451
	.long	3206574940
	.long	3278099830
	.long	3204945545
	.long	2222993240
	.long	1053042193
	.long	2059286351
	.long	1050869046
	.long	306569625
	.long	3193793299
	.long	95256688
	.long	3191038309
	.long	616794992
	.long	1039029534
	.long	4088689232
	.long	1075580917
	.type	_P2,@object
	.size	_P2,112
	.align 4
_P3:
	.long	1314465059
	.long	1017127215
	.long	1413823668
	.long	3218077268
	.long	4087546795
	.long	1065951807
	.long	3559351807
	.long	1067757765
	.long	338531188
	.long	3210711466
	.long	2516776276
	.long	3210695105
	.long	1548953411
	.long	1058452686
	.long	3819184955
	.long	1057373236
	.long	1262833741
	.long	3200137243
	.long	325214128
	.long	3198310046
	.long	1253465703
	.long	1045913252
	.long	3453826356
	.long	1043528319
	.long	2667154656
	.long	3186140571
	.long	3502111785
	.long	1076123856
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	1657852450
	.long	3163577804
	.long	2020212262
	.long	1070330675
	.long	774106997
	.long	3212888122
	.long	2119717923
	.long	3215085957
	.long	2098754573
	.long	1062575793
	.long	2137093530
	.long	1063017421
	.long	2437037263
	.long	3205513537
	.long	2698759453
	.long	3204752976
	.long	1998212634
	.long	1052165362
	.long	2817762251
	.long	1050764623
	.long	357372240
	.long	3193071576
	.long	3891599306
	.long	3190966182
	.long	2865549426
	.long	1038348895
	.long	4077970850
	.long	1076536762
	.type	_P4,@object
	.size	_P4,112
	.align 4
_P5:
	.long	1537387514
	.long	3166104464
	.long	4237757176
	.long	3217630662
	.long	789391682
	.long	1064857043
	.long	3122547501
	.long	1067488355
	.long	3303098147
	.long	3209681410
	.long	2944525948
	.long	3210343613
	.long	1468487066
	.long	1057519518
	.long	285546763
	.long	1057172625
	.long	830693909
	.long	3199311383
	.long	1284260698
	.long	3198182585
	.long	1823688212
	.long	1045254801
	.long	1962790020
	.long	1043427779
	.long	1867031598
	.long	3185590079
	.long	906299589
	.long	1076918395
	.type	_P5,@object
	.size	_P5,112
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
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
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.data
	.hidden __libm_sincos_k32
	.section .note.GNU-stack, ""
# End
