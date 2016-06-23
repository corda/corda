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
	.file "j0f.c"
	.text
..TXTST0:
# -- Begin  j0f
	.text
       .align    16,0x90
	.globl j0f
j0f:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %ebx
        subl      $124, %esp
        movl      8(%ebp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        jae       ..B1.24
..B1.2:
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $1099154394, %eax
        jae       ..B1.22
..B1.3:
        cmpl      $1081424556, %eax
        jae       ..B1.14
..B1.4:
        cmpl      $1048576000, %eax
        jae       ..B1.13
..B1.5:
        cmpl      $998244352, %eax
        jae       ..B1.12
..B1.6:
        cmpl      $947912704, %eax
        jae       ..B1.11
..B1.7:
        flds      8(%ebp)
        cmpl      $838860800, %eax
        fld1      
        jae       ..B1.9
..B1.8:
        fxch      %st(1)
        fabs      
        fsubrp    %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        fxch      %st(1)
        fmul      %st(0), %st
        fsubrp    %st, %st(1)
..B1.10:
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        flds      8(%ebp)
        fmul      %st(0), %st
        fld1      
        fldl      8+_Q3@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     _Q3@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.12:
        flds      8(%ebp)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld1      
        fldl      24+_Q2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldl      16+_Q2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     8+_Q2@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        faddl     _Q2@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        flds      8(%ebp)
        fabs      
        fldl      104+_Q1@GOTOFF(%ebx)
        fldl      88+_Q1@GOTOFF(%ebx)
        fldl      96+_Q1@GOTOFF(%ebx)
        fldl      80+_Q1@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     112+_Q1@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     72+_Q1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56+_Q1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40+_Q1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     64+_Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     8+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fxch      %st(2)
        faddl     32+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     48+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddl     16+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddl     _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        cmpl      $1092798087, %eax
        jae       ..B1.18
..B1.15:
        cmpl      $1088454576, %eax
        jae       ..B1.17
..B1.16:
        lea       _P1@GOTOFF(%ebx), %eax
        jmp       ..B1.21
..B1.17:
        lea       _P2@GOTOFF(%ebx), %eax
        jmp       ..B1.21
..B1.18:
        cmpl      $1096101336, %eax
        jae       ..B1.20
..B1.19:
        lea       _P3@GOTOFF(%ebx), %eax
        jmp       ..B1.21
..B1.20:
        lea       _P4@GOTOFF(%ebx), %eax
..B1.21:
        flds      8(%ebp)
        fabs      
        fldl      96(%eax)
        fldl      80(%eax)
        fldl      88(%eax)
        fldl      72(%eax)
        fxch      %st(4)
        fsubl     104(%eax)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     64(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     48(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     32(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     16(%eax)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     56(%eax)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     (%eax)
        fxch      %st(5)
        faddl     40(%eax)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     24(%eax)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     8(%eax)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        flds      8(%ebp)
        lea       32(%esp), %eax
        fabs      
        lea       40(%esp), %edx
        fld1      
        fdiv      %st(1), %st
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
        movl      $-1, 16(%esp)
        fstpl     40(%eax)
        call      __libm_sincos_k32
..B1.23:
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
        fmull     40(%esp)
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
        fmull     32(%esp)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        ja        ..B1.26
..B1.25:
        fldz      
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        flds      8(%ebp)
..B1.27:
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	j0f,@function
	.size	j0f,.-j0f
	.data
# -- End  j0f
	.section .rodata, "a"
	.align 4
	.align 4
_Q3:
	.long	4294966841
	.long	3218079743
	.long	477219499
	.long	1066401791
	.type	_Q3,@object
	.size	_Q3,16
	.align 4
_Q2:
	.long	4294967005
	.long	3218079743
	.long	4292583532
	.long	1066401791
	.long	2963922746
	.long	3208409541
	.long	1698509326
	.long	1054632110
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_Q1:
	.long	569392270
	.long	3163659129
	.long	911539783
	.long	3219168475
	.long	3927550863
	.long	1069261278
	.long	1680954783
	.long	1068301032
	.long	3247602706
	.long	3212950300
	.long	1528576945
	.long	3210869138
	.long	2120930432
	.long	1060197251
	.long	3147753376
	.long	1057418557
	.long	3445667889
	.long	3201446637
	.long	160313185
	.long	3198274950
	.long	2157358765
	.long	1047004568
	.long	1405517519
	.long	1043512506
	.long	3055202941
	.long	3187001715
	.long	562966967
	.long	3183555197
	.long	781654848
	.long	1073954069
	.type	_Q1,@object
	.size	_Q1,120
	.align 4
_P1:
	.long	2556390964
	.long	3162487616
	.long	168113101
	.long	1070974694
	.long	3865632959
	.long	3214905202
	.long	3384884860
	.long	3215663440
	.long	3461368592
	.long	1064499199
	.long	2947775703
	.long	1063419441
	.long	1964351934
	.long	3207001659
	.long	1081690493
	.long	3204998104
	.long	2315304668
	.long	1053480462
	.long	3655541446
	.long	1050880364
	.long	988219424
	.long	3194139699
	.long	2647984383
	.long	3191029766
	.long	3459760917
	.long	1038956170
	.long	1529622085
	.long	1075188879
	.type	_P1,@object
	.size	_P1,112
	.align 4
_P2:
	.long	2989381656
	.long	3164006638
	.long	2007135498
	.long	3218169721
	.long	3461465629
	.long	1066405759
	.long	1292000183
	.long	1067879320
	.long	1048188593
	.long	3211038307
	.long	2811900586
	.long	3210797269
	.long	275024305
	.long	1058896216
	.long	2179928921
	.long	1057456107
	.long	2316750355
	.long	3200463196
	.long	1626245786
	.long	3198355713
	.long	2429961783
	.long	1046264853
	.long	3081665457
	.long	1043557342
	.long	3760684269
	.long	3186121773
	.long	1825365706
	.long	1075924661
	.type	_P2,@object
	.size	_P2,112
	.align 4
_P3:
	.long	915277891
	.long	3163740397
	.long	1721892699
	.long	1070448958
	.long	3433548142
	.long	3213111280
	.long	3603643349
	.long	3215166749
	.long	2193444562
	.long	1062884841
	.long	1036402988
	.long	1063136790
	.long	2039602830
	.long	3205750717
	.long	2039806946
	.long	3204828766
	.long	3757243411
	.long	1052487712
	.long	2819184679
	.long	1050816211
	.long	1638789445
	.long	3193302656
	.long	96077275
	.long	3191008024
	.long	34207204
	.long	1038327735
	.long	8549046
	.long	1076335940
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	4010378302
	.long	3164933090
	.long	260862994
	.long	3217715229
	.long	793353311
	.long	1065112761
	.long	2823013001
	.long	1067546520
	.long	4006575139
	.long	3209863700
	.long	3390737311
	.long	3210433566
	.long	1793352299
	.long	1057800372
	.long	3528876835
	.long	1057234714
	.long	2505438989
	.long	3199512871
	.long	2785865938
	.long	3198229698
	.long	565130724
	.long	1045495083
	.long	410977742
	.long	1043470609
	.long	4266013081
	.long	3185543354
	.long	1056076242
	.long	1076747425
	.type	_P4,@object
	.size	_P4,112
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
	.long	4294966288
	.long	1072693247
	.long	4269912424
	.long	3207725055
	.long	1780799603
	.long	1052554744
	.long	2285933488
	.long	3194113879
	.long	3892950982
	.long	1041746526
	.long	150212775
	.long	3184818833
	.type	_PP,@object
	.size	_PP,48
	.align 4
_QP:
	.long	4294921804
	.long	3212836863
	.long	4153409851
	.long	1056096255
	.long	2226361937
	.long	3196916170
	.long	3849695089
	.long	1044094312
	.long	663042994
	.long	3187124278
	.long	3336948587
	.long	1035486718
	.type	_QP,@object
	.size	_QP,48
	.data
	.hidden __libm_sincos_k32
	.section .note.GNU-stack, ""
# End
