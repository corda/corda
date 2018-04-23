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
	.file "y0f.c"
	.text
..TXTST0:
# -- Begin  y0f
	.text
       .align    16,0x90
	.globl y0f
y0f:
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
        jae       ..B1.21
..B1.2:
        cmpl      $1099978979, %edx
        jae       ..B1.19
..B1.3:
        cmpl      $1094450314, %edx
        jae       ..B1.15
..B1.4:
        cmpl      $1085128690, %edx
        jae       ..B1.11
..B1.5:
        cmpl      $1074568695, %edx
        jae       ..B1.10
..B1.6:
        cmpl      $1058642330, %edx
        jae       ..B1.9
..B1.7:
        flds      8(%ebp)
        fstl      (%esp)
        fstpl     32(%esp)
        call      __libm_log_k32
..B1.31:
        fldl      32(%esp)
..B1.8:
        fldl      _tonpi@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      32+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      24+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_Q1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
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
        fmul      %st(2), %st
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        flds      8(%ebp)
        fldl      160+_P1@GOTOFF(%ebx)
        fldl      144+_P1@GOTOFF(%ebx)
        fldl      152+_P1@GOTOFF(%ebx)
        fldl      136+_P1@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     168+_P1@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     128+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     112+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     96+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     80+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     64+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     48+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     32+_P1@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     16+_P1@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     120+_P1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     104+_P1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     88+_P1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     72+_P1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     56+_P1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     _P1@GOTOFF(%ebx)
        fxch      %st(5)
        faddl     40+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     24+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     8+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        flds      8(%ebp)
        fldl      136+_P2@GOTOFF(%ebx)
        fldl      120+_P2@GOTOFF(%ebx)
        fldl      128+_P2@GOTOFF(%ebx)
        fldl      112+_P2@GOTOFF(%ebx)
        fxch      %st(4)
        fsubl     144+_P2@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     104+_P2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     88+_P2@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
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
        faddl     96+_P2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     80+_P2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(4)
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
..B1.11:
        cmpl      $1091143997, %edx
        jae       ..B1.13
..B1.12:
        lea       _P3@GOTOFF(%ebx), %eax
        jmp       ..B1.14
..B1.13:
        lea       _P4@GOTOFF(%ebx), %eax
..B1.14:
        flds      8(%ebp)
        fldl      104(%eax)
        fldl      88(%eax)
        fldl      96(%eax)
        fldl      80(%eax)
        fxch      %st(4)
        fsubl     112(%eax)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     72(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     56(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     40(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     24(%eax)
        fmul      %st(1), %st
        fmul      %st(2), %st
        fxch      %st(4)
        faddl     8(%eax)
        fmulp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        faddl     64(%eax)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     48(%eax)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     32(%eax)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddl     16(%eax)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     (%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        cmpl      $1097751532, %edx
        jae       ..B1.17
..B1.16:
        lea       _P5@GOTOFF(%ebx), %eax
        jmp       ..B1.18
..B1.17:
        lea       _P6@GOTOFF(%ebx), %eax
..B1.18:
        flds      8(%ebp)
        fldl      80(%eax)
        fldl      96(%eax)
        fldl      88(%eax)
        fldl      72(%eax)
        fxch      %st(4)
        fsubl     104(%eax)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fmul      %st, %st(3)
        fmul      %st, %st(4)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddl     48(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     64(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     16(%eax)
        fmul      %st(1), %st
        fxch      %st(4)
        faddl     32(%eax)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     (%eax)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     56(%eax)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     40(%eax)
        fmulp     %st, %st(4)
        fxch      %st(4)
        faddl     24(%eax)
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     8(%eax)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
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
        movl      $-1, 16(%esp)
        fstpl     40(%eax)
        call      __libm_sincos_k32
..B1.20:
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
..B1.21:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.27
..B1.22:
        testl     %eax, %eax
        je        ..B1.28
..B1.23:
        fldz      
        testl     $-2147483648, %edx
        je        ..B1.26
..B1.25:
        flds      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.26:
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.27:
        flds      8(%ebp)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        fldz      
        fdivrs    .L_2il0floatpacket.0@GOTOFF(%ebx)
        addl      $124, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	y0f,@function
	.size	y0f,.-y0f
	.data
# -- End  y0f
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
	.long	2580172613
	.long	3216172246
	.long	1084453148
	.long	1069988811
	.long	2056290550
	.long	3213915569
	.long	1236657422
	.long	1061266913
	.long	2321867150
	.long	3202594720
	.type	_Q1,@object
	.size	_Q1,40
	.align 4
_Q2:
	.long	4294648676
	.long	3218079743
	.long	3841535415
	.long	1066401791
	.long	664070481
	.long	3208409496
	.long	1981210199
	.long	1054621065
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_P1:
	.long	4195291908
	.long	3162175307
	.long	406986291
	.long	1072440375
	.long	2776221165
	.long	3219095096
	.long	1133938471
	.long	1070349075
	.long	2954828429
	.long	3217879437
	.long	2763399148
	.long	1070335612
	.long	808830809
	.long	3217701246
	.long	1570268664
	.long	1070154462
	.long	3296077481
	.long	3217609508
	.long	420330990
	.long	1070118798
	.long	773510171
	.long	3217614074
	.long	4056659129
	.long	1070174592
	.long	3219431954
	.long	3217750970
	.long	3785879815
	.long	1070358232
	.long	574751405
	.long	3217779900
	.long	4260549547
	.long	1069957964
	.long	2938320462
	.long	3216782740
	.long	3378329138
	.long	1068120728
	.long	1915664650
	.long	3213929269
	.long	3426344990
	.long	1063895342
	.long	349103460
	.long	3207738231
	.long	3100907498
	.long	1072470062
	.type	_P1,@object
	.size	_P1,176
	.align 4
_P2:
	.long	1454683265
	.long	3163093641
	.long	1453379447
	.long	3218719554
	.long	687601567
	.long	1068108233
	.long	2612704149
	.long	1068365525
	.long	3658933664
	.long	3212579183
	.long	2932711330
	.long	3210863403
	.long	1642366143
	.long	1059690534
	.long	3271323791
	.long	1057665714
	.long	1907282597
	.long	3201598976
	.long	379771377
	.long	3197347542
	.long	4245487245
	.long	3193415108
	.long	3608497393
	.long	1045674492
	.long	2931231697
	.long	3190739602
	.long	990015633
	.long	1041359383
	.long	1316300206
	.long	3186258158
	.long	3640313785
	.long	3183477336
	.long	2399178872
	.long	3182342144
	.long	1279629276
	.long	1034489495
	.long	1301829276
	.long	1074768211
	.type	_P2,@object
	.size	_P2,152
	.align 4
_P3:
	.long	2564005090
	.long	1014927793
	.long	2691273771
	.long	1070806220
	.long	300870104
	.long	3214257910
	.long	1691761700
	.long	3215496860
	.long	2795355208
	.long	1063989012
	.long	2286168356
	.long	1063375710
	.long	3010782666
	.long	3206704598
	.long	629642940
	.long	3204978559
	.long	4085313989
	.long	1053206786
	.long	1695842824
	.long	1050888907
	.long	399213285
	.long	3194002865
	.long	3012936744
	.long	3191097259
	.long	3874678520
	.long	1039019405
	.long	2821019721
	.long	1035940102
	.long	3303481603
	.long	1075599389
	.type	_P3,@object
	.size	_P3,120
	.align 4
_P4:
	.long	3357785889
	.long	3165432791
	.long	3430075659
	.long	3218077237
	.long	1374220014
	.long	1065943908
	.long	2803440381
	.long	1067771495
	.long	3788987270
	.long	3210752606
	.long	378839180
	.long	3210732089
	.long	3385429871
	.long	1058535500
	.long	2609792408
	.long	1057402845
	.long	2316098656
	.long	3200270393
	.long	2785606124
	.long	3198332594
	.long	1489685130
	.long	1046034133
	.long	973177683
	.long	1043605688
	.long	113866720
	.long	3186126026
	.long	1355313597
	.long	3183357512
	.long	899972440
	.long	1076130263
	.type	_P4,@object
	.size	_P4,120
	.align 4
_P5:
	.long	1219042543
	.long	1017436670
	.long	659497129
	.long	1070330666
	.long	2213395954
	.long	3212885037
	.long	920472610
	.long	3215092907
	.long	483400048
	.long	1062596670
	.long	3012498212
	.long	1063037606
	.long	1375970636
	.long	3205547500
	.long	3836089736
	.long	3204771982
	.long	199192965
	.long	1052233910
	.long	194857414
	.long	1050781561
	.long	2407988550
	.long	3193126966
	.long	3806101946
	.long	3190982557
	.long	3164279536
	.long	1038193510
	.long	3298944842
	.long	1076541665
	.type	_P5,@object
	.size	_P5,112
	.align 4
_P6:
	.long	4139807906
	.long	1017960487
	.long	1501500641
	.long	3217630659
	.long	2447579003
	.long	1064854100
	.long	511348540
	.long	1067492433
	.long	1810619958
	.long	3209696179
	.long	1806624601
	.long	3210355872
	.long	4035698547
	.long	1057552651
	.long	467530437
	.long	1057185016
	.long	2985801821
	.long	3199346510
	.long	2460583618
	.long	3198194818
	.long	4117830692
	.long	1045310355
	.long	1133672721
	.long	1043441078
	.long	2717688296
	.long	3185336108
	.long	1946169876
	.long	1076920380
	.type	_P6,@object
	.size	_P6,112
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
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
	.hidden __libm_log_k32
	.section .note.GNU-stack, ""
# End
