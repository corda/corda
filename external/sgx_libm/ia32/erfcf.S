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
	.file "erfcf.c"
	.text
..TXTST0:
# -- Begin  erfcf
	.text
       .align    16,0x90
	.globl erfcf
erfcf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %edi
        pushl     %ebx
        subl      $56, %esp
        movl      8(%ebp), %edi
        movl      %edi, %eax
        andl      $2147483647, %eax
        shrl      $31, %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2139095040, %eax
        jae       ..B1.24
..B1.2:
        cmpl      range@GOTOFF(%ebx,%edi,4), %eax
        jae       ..B1.21
..B1.3:
        cmpl      $1081081856, %eax
        jae       ..B1.15
..B1.4:
        cmpl      $1073741824, %eax
        jae       ..B1.11
..B1.5:
        cmpl      $1056964608, %eax
        jae       ..B1.10
..B1.6:
        flds      8(%ebp)
        cmpl      $847249408, %eax
        jae       ..B1.8
..B1.7:
        fld1      
        fsubp     %st, %st(1)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        fldl      40+_A@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fld1      
        fxch      %st(3)
        faddl     24+_A@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      48+_A@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_A@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddl     32+_A@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     16+_A@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddl     _A@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
..B1.9:
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        flds      8(%ebp)
        fabs      
        fld1      
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      48+_AP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      56+_AP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_AP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_AP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_AP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_AP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _AP@GOTOFF(%ebx)
        fldl      48+_AQ@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     8+_AP@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fldl      56+_AQ@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     32+_AQ@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     40+_AQ@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     16+_AQ@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     24+_AQ@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddl     _AQ@GOTOFF(%ebx)
        fxch      %st(2)
        faddl     8+_AQ@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fdivrp    %st, %st(1)
        fmuls     _ones@GOTOFF(%ebx,%edi,4)
        faddl     _erfc1@GOTOFF(%ebx,%edi,8)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        flds      8(%ebp)
        testl     %edi, %edi
        fabs      
        faddl     .L_2il0floatpacket.2@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        je        ..B1.13
..B1.12:
        fldl      48+_BP1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      40+_BP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_BP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_BP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_BP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_BP1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     _BP1@GOTOFF(%ebx)
        fldl      48+_BQ1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldl      40+_BQ1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     32+_BQ1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     24+_BQ1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     16+_BQ1@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddl     8+_BQ1@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     _BQ1@GOTOFF(%ebx)
        fxch      %st(2)
        jmp       ..B1.14
..B1.13:
        fldl      80+_BP2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      88+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     64+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     72+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_BP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _BP2@GOTOFF(%ebx)
        fldl      64+_BQ2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        faddl     8+_BP2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     48+_BQ2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldl      56+_BQ2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     32+_BQ2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     40+_BQ2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     16+_BQ2@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     24+_BQ2@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddl     _BQ2@GOTOFF(%ebx)
        fxch      %st(3)
        faddl     8+_BQ2@GOTOFF(%ebx)
        fmulp     %st, %st(4)
..B1.14:
        faddp     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fdivrp    %st, %st(1)
        fmuls     _ones@GOTOFF(%ebx,%edi,4)
        faddl     _erfc4@GOTOFF(%ebx,%edi,8)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        flds      8(%ebp)
        fabs      
        fld1      
        fdiv      %st(1), %st
        fstpl     24(%esp)
        fmul      %st(0), %st
        fchs      
        fstpl     (%esp)
        call      __libm_exp_k32
..B1.16:
        fldl      24(%esp)
        testl     %edi, %edi
        fmul      %st, %st(1)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      56+_R1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      48+_R1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_R1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_R1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_R1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_R1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddl     8+_R1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddl     _R1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        jne       ..B1.20
..B1.17:
        fsts      20(%esp)
        cmpl      $8388608, 20(%esp)
        jl        ..B1.28
..B1.19:
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        fsubrs    .L_2il0floatpacket.0@GOTOFF(%ebx)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        testl     %edi, %edi
        je        ..B1.29
..B1.22:
        flds      .L_2il0floatpacket.0@GOTOFF(%ebx)
..B1.23:
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        jne       ..B1.26
..B1.25:
        flds      zero_two@GOTOFF(%ebx,%edi,4)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        flds      8(%ebp)
..B1.27:
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        fstp      %st(0)
        flds      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     16(%esp)
        flds      20(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        flds      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmul      %st(0), %st
        fsts      20(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	erfcf,@function
	.size	erfcf,.-erfcf
	.data
# -- End  erfcf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0xc0000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x40000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x0d800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
range:
	.long	1092825907
	.long	1081791557
	.type	range,@object
	.size	range,8
	.align 4
_A:
	.long	1346541978
	.long	3220311511
	.long	1794662342
	.long	1071125108
	.long	250944106
	.long	3216827122
	.long	215131671
	.long	1067156170
	.long	548330146
	.long	3212141823
	.long	76350191
	.long	1061937114
	.long	2772654316
	.long	3206307213
	.type	_A,@object
	.size	_A,56
	.align 4
_AP:
	.long	4064916629
	.long	3190878451
	.long	2877165718
	.long	3218164236
	.long	2764139057
	.long	1068232842
	.long	168293639
	.long	3215329394
	.long	881203975
	.long	3215292554
	.long	342818617
	.long	1065638313
	.long	817546319
	.long	3212004140
	.long	1510911299
	.long	3202390104
	.type	_AP,@object
	.size	_AP,64
	.align 4
_AQ:
	.long	1883560946
	.long	1071960885
	.long	853592313
	.long	1071685196
	.long	191605458
	.long	1071250674
	.long	2183121159
	.long	1070390698
	.long	3347411101
	.long	1068928985
	.long	273856425
	.long	1067608207
	.long	671820230
	.long	1065177551
	.long	2414119437
	.long	1063328696
	.type	_AQ,@object
	.size	_AQ,64
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_erfc1:
	.long	1879048192
	.long	1069818465
	.long	3523215360
	.long	1073576883
	.type	_erfc1,@object
	.size	_erfc1,16
	.align 4
_BP1:
	.long	4019088381
	.long	1060143686
	.long	4231883845
	.long	3209038463
	.long	1510730124
	.long	1061755778
	.long	2929202078
	.long	3208591153
	.long	1409179897
	.long	1059475758
	.long	439372548
	.long	3204538649
	.long	3357266387
	.long	1053229132
	.type	_BP1,@object
	.size	_BP1,56
	.align 4
_BQ1:
	.long	3444570556
	.long	1068208773
	.long	4145425017
	.long	1069029647
	.long	1145740212
	.long	1069192522
	.long	2413502193
	.long	1068797491
	.long	2712383784
	.long	1068007659
	.long	3941762314
	.long	1066743858
	.long	3272105283
	.long	1065447630
	.type	_BQ1,@object
	.size	_BQ1,56
	.align 4
_BP2:
	.long	3138848853
	.long	1062211012
	.long	544119287
	.long	3211644465
	.long	2662071917
	.long	1064935492
	.long	2106254088
	.long	3212505578
	.long	377059754
	.long	1064517936
	.long	3523110848
	.long	3210978388
	.long	64932799
	.long	1062023575
	.long	2232373525
	.long	3207568747
	.long	3004568351
	.long	1057518327
	.long	1553969795
	.long	3201939481
	.long	865068135
	.long	1050709866
	.long	348213498
	.long	3193161330
	.type	_BP2,@object
	.size	_BP2,96
	.align 4
_BQ2:
	.long	2220299070
	.long	1070256111
	.long	1607355279
	.long	1069737707
	.long	3222185356
	.long	1069664669
	.long	1988590515
	.long	1068489586
	.long	2612211906
	.long	1067468794
	.long	3802943557
	.long	1065572613
	.long	4286646379
	.long	1063895282
	.long	45248763
	.long	1061257684
	.long	3494068347
	.long	1058887001
	.type	_BQ2,@object
	.size	_BQ2,72
	.align 4
_erfc4:
	.long	0
	.long	1045463040
	.long	4225761280
	.long	1073741823
	.type	_erfc4,@object
	.size	_erfc4,16
	.align 4
_R1:
	.long	1340517714
	.long	1071779287
	.long	1760660317
	.long	3218214358
	.long	3277598851
	.long	1071322187
	.long	1776282588
	.long	3220235438
	.long	2229586924
	.long	1074588991
	.long	2042215417
	.long	3224150558
	.long	1374528972
	.long	1078534706
	.long	1825320511
	.long	3227067102
	.type	_R1,@object
	.size	_R1,64
	.align 4
zero_two:
	.long	0x00000000
	.long	0x40000000
	.type	zero_two,@object
	.size	zero_two,8
	.data
	.hidden __libm_exp_k32
	.section .note.GNU-stack, ""
# End
