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
	.file "erf.c"
	.text
..TXTST0:
# -- Begin  erf
	.text
       .align    16,0x90
	.globl erf
erf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        subl      $24, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movl      12(%ebp), %ecx
        movl      %ecx, %eax
        andl      $-2147483648, %eax
        andl      $2147483647, %ecx
        orl       $1065353216, %eax
        movl      %eax, 16(%esp)
        cmpl      $1075298304, %ecx
        flds      16(%esp)
        movl      8(%ebp), %esi
        fstpt     (%esp)
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        jge       ..B1.11
..B1.4:
        movzwl    14(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.15
..B1.5:
        orl       $-64768, %esi
        movw      %si, 12(%esp)
..B1.6:
        fldcw     12(%esp)
..B1.7:
        movl      $1, %eax
..B1.8:
        fldl      8(%ebp)
        lea       -1074331648(%ecx), %esi
        shrl      $20, %ecx
        fabs      
        fldt      (%esp)
        addl      $-1021, %ecx
        cmpl      $458751, %esi
        ja        ..L3
        movl      $5, %ecx
..L3:
        testl     %ecx, %ecx
        jg        ..L4
        movl      $0, %ecx
..L4:
        movl      %ecx, %edi
        lea       (%ecx,%ecx,2), %esi
        shll      $4, %edi
        shll      $7, %ecx
        shll      $4, %esi
        testl     %eax, %eax
        fxch      %st(1)
        fmull     erfRRTable@GOTOFF(%edi,%edx)
        fldl      120+_dbA@GOTOFF(%edx,%ecx)
        fldt      36+_ldbA@GOTOFF(%edx,%esi)
        fldt      24+_ldbA@GOTOFF(%edx,%esi)
        fldt      12+_ldbA@GOTOFF(%edx,%esi)
        fldt      _ldbA@GOTOFF(%edx,%esi)
        fxch      %st(5)
        faddl     8+erfRRTable@GOTOFF(%edx,%edi)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddl     112+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     104+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     96+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     88+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     80+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     72+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     64+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     56+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     48+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     40+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     32+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     24+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     16+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     8+_dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddl     _dbA@GOTOFF(%edx,%ecx)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.10
..B1.9:
        fldcw     14(%esp)
..B1.10:
        fldt      (%esp)
        addl      $24, %esp
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        cmpl      $2146435072, %ecx
        jl        ..B1.14
..B1.12:
        je        ..B1.16
..B1.13:
        fldl      8(%ebp)
        fadd      %st(0), %st
        addl      $24, %esp
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        fldl      768+_dbA@GOTOFF(%edx)
        fldl      776+_dbA@GOTOFF(%edx)
        fldt      (%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        addl      $24, %esp
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        xorl      %eax, %eax
        jmp       ..B1.8
..B1.16:
        testl     %esi, %esi
        jne       ..B1.13
..B1.17:
        fldt      (%esp)
        addl      $24, %esp
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	erf,@function
	.size	erf,.-erf
	.data
# -- End  erf
	.section .rodata, "a"
	.align 16
	.align 16
erfRRTable:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3fd00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xc00d0000
	.type	erfRRTable,@object
	.size	erfRRTable,96
	.align 16
_dbA:
	.long	0
	.long	0
	.long	436480805
	.long	1069343474
	.long	0
	.long	0
	.long	824702180
	.long	3214639822
	.long	0
	.long	0
	.long	3500778474
	.long	1064658364
	.long	0
	.long	0
	.long	596585212
	.long	3209429723
	.long	0
	.long	0
	.long	1470240973
	.long	1059035690
	.long	0
	.long	0
	.long	718843426
	.long	3203353528
	.long	0
	.long	0
	.long	2710426178
	.long	1052473157
	.long	0
	.long	0
	.long	3010232270
	.long	3196296923
	.long	3936437656
	.long	1065568693
	.long	2299323409
	.long	3210429089
	.long	2523607943
	.long	3208348791
	.long	2965083678
	.long	1059250351
	.long	798914934
	.long	1055054643
	.long	3446493953
	.long	3202367749
	.long	3957742900
	.long	1049125428
	.long	3166330044
	.long	1049915994
	.long	2437497479
	.long	3193689582
	.long	3701813711
	.long	3192118067
	.long	4254294173
	.long	1041707319
	.long	3041595886
	.long	1039011503
	.long	177055818
	.long	3184215269
	.long	4076941640
	.long	3180389574
	.long	3283211378
	.long	1031330936
	.long	2299430385
	.long	1025878376
	.long	1560943562
	.long	3215381929
	.long	649600356
	.long	3213783607
	.long	1925487767
	.long	1066650173
	.long	2787104668
	.long	3212012915
	.long	3819886810
	.long	3210959588
	.long	3292869653
	.long	1062994816
	.long	595535285
	.long	3206044511
	.long	2833470226
	.long	3207638255
	.long	3150087827
	.long	1058299310
	.long	4226975388
	.long	1056101349
	.long	3511121228
	.long	3203239297
	.long	4191453678
	.long	1050931364
	.long	2398545468
	.long	1052356106
	.long	1036696996
	.long	3197521914
	.long	406902450
	.long	3195737434
	.long	932124879
	.long	1046963798
	.long	2984826674
	.long	3213958277
	.long	1416337143
	.long	1067493301
	.long	1528223327
	.long	3215470371
	.long	2077734896
	.long	1068079284
	.long	500891590
	.long	3215043166
	.long	1614001607
	.long	1065167414
	.long	397732724
	.long	1066426356
	.long	245285475
	.long	3214320360
	.long	2942435727
	.long	1065970365
	.long	2515085614
	.long	1062242098
	.long	2059023426
	.long	3212710123
	.long	3590542062
	.long	1064841017
	.long	1069940521
	.long	1062119991
	.long	329470371
	.long	1062390329
	.long	2904189456
	.long	1064709094
	.long	1072026573
	.long	1063487818
	.long	2999885720
	.long	3188887951
	.long	3321998034
	.long	3194165661
	.long	778559078
	.long	3198831619
	.long	4123899724
	.long	3202964980
	.long	708728771
	.long	3206666523
	.long	2191638927
	.long	3209872513
	.long	2647513773
	.long	3212706538
	.long	349076715
	.long	3215092788
	.long	3464000864
	.long	3217125251
	.long	2469419808
	.long	3218694220
	.long	396951367
	.long	3219923315
	.long	545534675
	.long	3220654754
	.long	3101724493
	.long	3220952863
	.long	496398401
	.long	3220607311
	.long	1221104108
	.long	3219519496
	.long	4256169036
	.long	3217191486
	.long	2904280702
	.long	3204470624
	.long	1218817182
	.long	1057276414
	.long	54035796
	.long	3204757890
	.long	4162943463
	.long	1056998848
	.long	3226000836
	.long	3203731619
	.long	1250945068
	.long	1055200853
	.long	1745105165
	.long	3201249698
	.long	1720233031
	.long	1051395895
	.long	2751609749
	.long	1047829350
	.long	3766093227
	.long	3196386836
	.long	2511730864
	.long	1047725585
	.long	1042659329
	.long	3192914318
	.long	2981264223
	.long	3189366070
	.long	673045009
	.long	1042560833
	.long	421746295
	.long	3188353310
	.long	3454229363
	.long	3184015574
	.long	4294967295
	.long	1072693247
	.long	0
	.long	1017102336
	.type	_dbA,@object
	.size	_dbA,784
	.align 16
_ldbA:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	26765
	.word	5339
	.word	47746
	.word	36974
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	13107
	.word	7119
	.word	41816
	.word	49299
	.word	49149
	.word	0
	.word	64355
	.word	59506
	.word	19372
	.word	46606
	.word	16382
	.word	0
	.word	9448
	.word	50838
	.word	8555
	.word	42135
	.word	16381
	.word	0
	.word	14173
	.word	43489
	.word	45601
	.word	63202
	.word	49147
	.word	0
	.word	56288
	.word	45938
	.word	11407
	.word	56180
	.word	16374
	.word	0
	.word	64911
	.word	39894
	.word	43704
	.word	63314
	.word	16382
	.word	0
	.word	18164
	.word	49445
	.word	47413
	.word	62353
	.word	16379
	.word	0
	.word	62471
	.word	20699
	.word	19176
	.word	46765
	.word	49148
	.word	0
	.word	45860
	.word	23381
	.word	351
	.word	36373
	.word	16380
	.word	0
	.word	52190
	.word	2479
	.word	36194
	.word	65534
	.word	16382
	.word	0
	.word	53234
	.word	6547
	.word	29917
	.word	37380
	.word	16371
	.word	0
	.word	61321
	.word	42597
	.word	44875
	.word	56070
	.word	49141
	.word	0
	.word	35023
	.word	36587
	.word	42382
	.word	52955
	.word	16375
	.word	0
	.word	65130
	.word	65535
	.word	65535
	.word	65535
	.word	16382
	.word	0
	.word	49987
	.word	10941
	.word	20175
	.word	44229
	.word	16331
	.word	0
	.word	45301
	.word	36641
	.word	152
	.word	62959
	.word	49108
	.word	0
	.word	7729
	.word	55572
	.word	4515
	.word	43787
	.word	49115
	.word	0
	.word	12218
	.word	25069
	.word	64268
	.word	65535
	.word	16382
	.word	0
	.word	39514
	.word	2562
	.word	14458
	.word	38069
	.word	16364
	.word	0
	.word	36514
	.word	39186
	.word	15150
	.word	34500
	.word	49134
	.word	0
	.word	9125
	.word	14475
	.word	36819
	.word	40101
	.word	16367
	.word	0
	.type	_ldbA,@object
	.size	_ldbA,288
	.data
	.section .note.GNU-stack, ""
# End
