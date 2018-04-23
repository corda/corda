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
	.file "atanh_wmt.c"
	.text
..TXTST0:
# -- Begin  static_func
	.text
       .align    16,0x90
static_func:
..B1.1:
..L1:

        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        lea       static_const_table@GOTOFF(%eax), %eax
        ret       
        .align    16,0x90
	.type	static_func,@function
	.size	static_func,.-static_func
	.data
# -- End  static_func
	.text
# -- Begin  atanh
	.text
       .align    16,0x90
	.globl atanh
atanh:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 48(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     112(%esp), %xmm0
        movl      116(%esp), %eax
        movl      %eax, %ecx
        movq      %xmm0, 8(%esp)
        movl      %eax, %edx
        andl      $2147483647, %eax
        cmpl      $1072693248, %eax
        jge       .L_2TAG_PACKET_0.0.3
        cmpl      $1068498944, %eax
        jl        .L_2TAG_PACKET_1.0.3
        movsd     96(%ebx), %xmm2
        movsd     112(%ebx), %xmm4
        movsd     112(%ebx), %xmm3
        movsd     144(%ebx), %xmm6
        andpd     %xmm2, %xmm0
        movq      %xmm0, %xmm1
        movq      %xmm0, %xmm7
        subsd     %xmm4, %xmm0
        addsd     %xmm4, %xmm1
        divsd     %xmm0, %xmm4
        movq      %xmm0, %xmm5
        movq      %xmm1, %xmm2
        andpd     128(%ebx), %xmm5
        addsd     %xmm0, %xmm3
        addsd     %xmm2, %xmm6
        subsd     %xmm5, %xmm0
        subsd     %xmm7, %xmm3
        subsd     %xmm7, %xmm6
        mulsd     %xmm4, %xmm1
        andpd     128(%ebx), %xmm1
        mulsd     %xmm1, %xmm5
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm0
        subsd     %xmm5, %xmm2
        subsd     %xmm3, %xmm6
        cvtsd2ss  %xmm1, %xmm7
        subsd     %xmm0, %xmm2
        rcpss     %xmm7, %xmm7
        subsd     %xmm6, %xmm2
        mulsd     %xmm4, %xmm2
        cvtss2sd  %xmm7, %xmm3
        paddd     160(%ebx), %xmm3
        andpd     176(%ebx), %xmm3
        mulsd     %xmm3, %xmm1
        subsd     192(%ebx), %xmm1
        mulsd     %xmm3, %xmm2
        psrlq     $40, %xmm3
        movq      %xmm1, %xmm4
        addsd     %xmm2, %xmm1
        movd      %xmm3, %eax
        movl      $4080, %ecx
        andl      %eax, %ecx
        andl      $8384512, %eax
        cvtsi2sdl %eax, %xmm3
        movapd    272(%ebx,%ecx), %xmm5
        pshufd    $68, %xmm3, %xmm3
        mulpd     208(%ebx), %xmm3
        subpd     %xmm3, %xmm5
        pshufd    $68, %xmm1, %xmm6
        movapd    224(%ebx), %xmm0
        mulpd     %xmm6, %xmm0
        mulsd     %xmm1, %xmm1
        addpd     240(%ebx), %xmm0
        pshufd    $238, %xmm5, %xmm3
        addsd     %xmm2, %xmm5
        movq      %xmm3, %xmm7
        addsd     %xmm4, %xmm3
        subsd     %xmm3, %xmm7
        pshufd    $238, %xmm0, %xmm2
        mulsd     %xmm1, %xmm0
        addsd     %xmm2, %xmm0
        andl      $-2147483648, %edx
        mulsd     %xmm1, %xmm0
        addsd     %xmm7, %xmm4
        subsd     %xmm6, %xmm0
        addsd     %xmm4, %xmm5
        mulsd     %xmm6, %xmm0
        movd      %edx, %xmm2
        psllq     $32, %xmm2
        addsd     %xmm5, %xmm0
        addsd     %xmm3, %xmm0
        orpd      %xmm2, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        cmpl      $1003487232, %eax
        jl        .L_2TAG_PACKET_3.0.3
        movapd    32(%ebx), %xmm1
        movapd    %xmm0, %xmm2
        mulsd     %xmm0, %xmm0
        movapd    %xmm0, %xmm3
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm3
        mulpd     %xmm0, %xmm1
        addpd     16(%ebx), %xmm1
        mulpd     %xmm0, %xmm1
        addpd     (%ebx), %xmm1
        mulpd     %xmm3, %xmm1
        movhlps   %xmm1, %xmm0
        addsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm0
        addsd     %xmm2, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        andl      $2146435072, %eax
        je        .L_2TAG_PACKET_4.0.3
        fldl      8(%esp)
        fld1      
        fld       %st(1)
        faddp     
        fmulp     
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_4.0.3:
        fldl      8(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     (%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        movl      8(%esp), %ecx
        jne       .L_2TAG_PACKET_5.0.3
        testl     %ecx, %ecx
        je        .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_5.0.3:
        cmpl      $2146435072, %eax
        jg        .L_2TAG_PACKET_7.0.3
        je        .L_2TAG_PACKET_8.0.3
.L_2TAG_PACKET_9.0.3:
        xorpd     %xmm0, %xmm0
        mulsd     256(%ebx), %xmm0
        movsd     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       112(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $131, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_6.0.3:
        xorpd     %xmm1, %xmm1
        divsd     %xmm1, %xmm0
        movsd     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       112(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $132, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_8.0.3:
        testl     %ecx, %ecx
        je        .L_2TAG_PACKET_9.0.3
.L_2TAG_PACKET_7.0.3:
        addsd     %xmm0, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
.L_2TAG_PACKET_2.0.3:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	atanh,@function
	.size	atanh,.-atanh
	.data
# -- End  atanh
	.text
# -- Begin  __libm_error_support
	.text
       .align    16,0x90
__libm_error_support:
# parameter 1: 4 + %esp
# parameter 2: 8 + %esp
# parameter 3: 12 + %esp
# parameter 4: 16 + %esp
..B3.1:
..L4:

        ret       
        .align    16,0x90
	.type	__libm_error_support,@function
	.size	__libm_error_support,.-__libm_error_support
	.data
# -- End  __libm_error_support
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1431655765
	.long	1070945621
	.long	2576980801
	.long	1070176665
	.long	2453616913
	.long	1069697316
	.long	1427436931
	.long	1069314503
	.long	2129349532
	.long	1068975486
	.long	1629438381
	.long	1068756329
	.long	0
	.long	3219128320
	.long	1431621855
	.long	1070945621
	.long	4294842013
	.long	3218079743
	.long	1289448124
	.long	1070176674
	.long	2077359316
	.long	3217380703
	.long	0
	.long	0
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	4026531840
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	3220176896
	.long	0
	.long	4293918976
	.long	0
	.long	4293918976
	.long	0
	.long	4294963200
	.long	0
	.long	4294963200
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	3164486458
	.long	1017968538
	.long	4277796864
	.long	1058418242
	.long	2640218915
	.long	3222623584
	.long	4294812536
	.long	3221225471
	.long	1158309786
	.long	1074370979
	.long	1431613876
	.long	1073042773
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	3961250687
	.long	1024388927
	.long	3715808466
	.long	1081484075
	.long	2810985123
	.long	3168028234
	.long	3782743078
	.long	1081484067
	.long	2030471786
	.long	1021588483
	.long	3982853962
	.long	1081484059
	.long	724716906
	.long	1026766458
	.long	20143440
	.long	1081484052
	.long	481208597
	.long	1024261464
	.long	483527638
	.long	1081484044
	.long	769498347
	.long	1026449044
	.long	1077032516
	.long	1081484036
	.long	662383902
	.long	3174188397
	.long	1799662888
	.long	1081484028
	.long	4195513590
	.long	3173820974
	.long	2650434932
	.long	1081484020
	.long	216019017
	.long	3171160333
	.long	3628376034
	.long	1081484012
	.long	3037216435
	.long	3174203912
	.long	437557316
	.long	1081484005
	.long	2242287414
	.long	3173446660
	.long	1666962648
	.long	1081483997
	.long	488659010
	.long	3174389563
	.long	3020684720
	.long	1081483989
	.long	2411277324
	.long	3174261033
	.long	202826760
	.long	1081483982
	.long	1552614370
	.long	3174374647
	.long	1802404272
	.long	1081483974
	.long	1761159654
	.long	1025358045
	.long	3523541100
	.long	1081483966
	.long	1227029918
	.long	1022400056
	.long	1070371174
	.long	1081483959
	.long	1017266216
	.long	1025927327
	.long	3031940236
	.long	1081483951
	.long	541289881
	.long	3173823840
	.long	817434636
	.long	1081483944
	.long	827704132
	.long	1025095119
	.long	3015919542
	.long	1081483936
	.long	12934210
	.long	3172513362
	.long	1036600450
	.long	1081483929
	.long	1022949985
	.long	3172215173
	.long	3468561396
	.long	1081483921
	.long	217728403
	.long	1020883592
	.long	1721026464
	.long	1081483914
	.long	1773946939
	.long	1027063347
	.long	88130720
	.long	1081483907
	.long	3351615536
	.long	3172077507
	.long	2864018200
	.long	1081483899
	.long	4106838151
	.long	3174156261
	.long	1457939880
	.long	1081483892
	.long	2465439606
	.long	3172344168
	.long	164057338
	.long	1081483885
	.long	657024829
	.long	1026968941
	.long	3276540740
	.long	1081483877
	.long	239284496
	.long	1025773300
	.long	2204666832
	.long	1081483870
	.long	563503993
	.long	3172683562
	.long	1242622588
	.long	1081483863
	.long	610254749
	.long	3174232732
	.long	389635916
	.long	1081483856
	.long	1002592152
	.long	1020145289
	.long	3939910132
	.long	1081483848
	.long	3079709802
	.long	1025793649
	.long	3302754668
	.long	1081483841
	.long	4156588929
	.long	3174405388
	.long	2772388732
	.long	1081483834
	.long	2177005711
	.long	3173706385
	.long	2348072008
	.long	1081483827
	.long	430264461
	.long	3174137709
	.long	2029071860
	.long	1081483820
	.long	3379887614
	.long	1025826683
	.long	1814663212
	.long	1081483813
	.long	1712456239
	.long	1026917505
	.long	1704128456
	.long	1081483806
	.long	3085449310
	.long	1022564341
	.long	1696757342
	.long	1081483799
	.long	1141334958
	.long	3171815944
	.long	1791846876
	.long	1081483792
	.long	1462201563
	.long	1025647829
	.long	1988701228
	.long	1081483785
	.long	140047506
	.long	3174122852
	.long	2286631636
	.long	1081483778
	.long	2759845912
	.long	1024469925
	.long	2684956298
	.long	1081483771
	.long	804042514
	.long	1026845100
	.long	3183000296
	.long	1081483764
	.long	3646267136
	.long	3171049236
	.long	3780095494
	.long	1081483757
	.long	2796072414
	.long	1025179136
	.long	180613146
	.long	1081483751
	.long	2935558853
	.long	1026289947
	.long	973833004
	.long	1081483744
	.long	3867040372
	.long	3174062522
	.long	1864139448
	.long	1081483737
	.long	3192294445
	.long	1024114853
	.long	2850890576
	.long	1081483730
	.long	2309059177
	.long	1026558495
	.long	3933450840
	.long	1081483723
	.long	3300904614
	.long	3173180909
	.long	816223656
	.long	1081483717
	.long	2836209699
	.long	1026670938
	.long	2088520500
	.long	1081483710
	.long	3053174336
	.long	3174287073
	.long	3454757072
	.long	1081483703
	.long	875601420
	.long	1025878424
	.long	619355076
	.long	1081483697
	.long	4096202184
	.long	1026024994
	.long	2171644060
	.long	1081483690
	.long	2316702776
	.long	3172256416
	.long	3816057542
	.long	1081483683
	.long	3057023477
	.long	3173172612
	.long	1257034828
	.long	1081483677
	.long	2024148590
	.long	1026183907
	.long	3083922828
	.long	1081483670
	.long	3658022801
	.long	3174540653
	.long	706204916
	.long	1081483664
	.long	965620747
	.long	3174139185
	.long	2713239208
	.long	1081483657
	.long	3558523119
	.long	3173414325
	.long	514520144
	.long	1081483651
	.long	2031819201
	.long	1024415865
	.long	2699416774
	.long	1081483644
	.long	2356933624
	.long	3174155910
	.long	677434328
	.long	1081483638
	.long	62190434
	.long	1027016638
	.long	3037952504
	.long	1081483631
	.long	3933781504
	.long	1025915529
	.long	1190487056
	.long	1081483625
	.long	2941696762
	.long	1025734775
	.long	3724428072
	.long	1081483618
	.long	2414317168
	.long	3170887182
	.long	2049301558
	.long	1081483612
	.long	2475288969
	.long	3174544243
	.long	459540440
	.long	1081483606
	.long	2871120870
	.long	3173072625
	.long	3249582612
	.long	1081483599
	.long	3658024676
	.long	3173912579
	.long	1828968996
	.long	1081483593
	.long	3145428063
	.long	1025702909
	.long	492147244
	.long	1081483587
	.long	3377090674
	.long	1025855275
	.long	3533569804
	.long	1081483580
	.long	441975505
	.long	3174521119
	.long	2362791964
	.long	1081483574
	.long	2700361953
	.long	1024904554
	.long	1274275566
	.long	1081483568
	.long	384688735
	.long	1024271063
	.long	267519784
	.long	1081483562
	.long	3294550047
	.long	1024123281
	.long	3636995640
	.long	1081483555
	.long	1629023504
	.long	3173808292
	.long	2792276776
	.long	1081483549
	.long	2896290296
	.long	1023457608
	.long	2027843168
	.long	1081483543
	.long	4214876422
	.long	3173347505
	.long	1343211900
	.long	1081483537
	.long	2089044909
	.long	1026320237
	.long	737904392
	.long	1081483531
	.long	1248769281
	.long	1026719018
	.long	211446364
	.long	1081483525
	.long	3287536014
	.long	1027064288
	.long	4058335068
	.long	1081483518
	.long	139482549
	.long	3171419957
	.long	3688170062
	.long	1081483512
	.long	2409197073
	.long	3174045711
	.long	3395456928
	.long	1081483506
	.long	469604718
	.long	3170908568
	.long	3179738046
	.long	1081483500
	.long	252575166
	.long	3173898937
	.long	3040559844
	.long	1081483494
	.long	659008259
	.long	3173067756
	.long	2977472740
	.long	1081483488
	.long	2489152120
	.long	1026987818
	.long	2990031104
	.long	1081483482
	.long	1288476519
	.long	3174460243
	.long	3077793216
	.long	1081483476
	.long	1544468634
	.long	3174466384
	.long	3240321200
	.long	1081483470
	.long	1563709080
	.long	1022000721
	.long	3477181002
	.long	1081483464
	.long	2370511724
	.long	3174484932
	.long	3787942340
	.long	1081483458
	.long	1487230717
	.long	3173625849
	.long	4172178648
	.long	1081483452
	.long	2208332257
	.long	3172980377
	.long	334499754
	.long	1081483447
	.long	3734805764
	.long	3173286476
	.long	864421012
	.long	1081483441
	.long	3848175750
	.long	1026823789
	.long	1466559484
	.long	1081483435
	.long	3339650886
	.long	1026799228
	.long	2140503092
	.long	1081483429
	.long	2587112486
	.long	1025074103
	.long	2885843270
	.long	1081483423
	.long	4237329462
	.long	1026571343
	.long	3702174928
	.long	1081483417
	.long	928919292
	.long	3173697437
	.long	294129124
	.long	1081483412
	.long	3350657575
	.long	3169758933
	.long	1251242194
	.long	1081483406
	.long	703498847
	.long	3173578839
	.long	2278151956
	.long	1081483400
	.long	1741773245
	.long	3169646323
	.long	3374466840
	.long	1081483394
	.long	938088363
	.long	3174103745
	.long	244831272
	.long	1081483389
	.long	1870507923
	.long	1024486077
	.long	1478794808
	.long	1081483383
	.long	3798424972
	.long	1026574662
	.long	2781008336
	.long	1081483377
	.long	1376042227
	.long	3171831111
	.long	4151093216
	.long	1081483371
	.long	516241188
	.long	1026864009
	.long	1293706648
	.long	1081483366
	.long	1238744632
	.long	3164368610
	.long	2798410838
	.long	1081483360
	.long	534941416
	.long	3172335234
	.long	74901872
	.long	1081483355
	.long	4279792400
	.long	3172103338
	.long	1712748064
	.long	1081483349
	.long	1565180338
	.long	1018613718
	.long	3416618846
	.long	1081483343
	.long	3951527485
	.long	3168909417
	.long	891186624
	.long	1081483338
	.long	3344344018
	.long	1024361841
	.long	2726028632
	.long	1081483332
	.long	1292300217
	.long	3174285210
	.long	330855832
	.long	1081483327
	.long	3519999099
	.long	1026857199
	.long	2295251240
	.long	1081483321
	.long	4255457731
	.long	1024555694
	.long	28931548
	.long	1081483316
	.long	1490990106
	.long	1024547962
	.long	2121485436
	.long	1081483310
	.long	3260941522
	.long	3172283917
	.long	4277602488
	.long	1081483304
	.long	246767963
	.long	1026252459
	.long	2201975040
	.long	1081483299
	.long	128194825
	.long	1026112424
	.long	189232752
	.long	1081483294
	.long	363902483
	.long	3169513578
	.long	2534007978
	.long	1081483288
	.long	3008515840
	.long	3172261022
	.long	646033852
	.long	1081483283
	.long	352511539
	.long	3170570563
	.long	3114915334
	.long	1081483277
	.long	1843039765
	.long	1026777816
	.long	1350390812
	.long	1081483272
	.long	946690324
	.long	3173265177
	.long	3942070448
	.long	1081483266
	.long	3545145670
	.long	1022546257
	.long	2299697768
	.long	1081483261
	.long	1496129287
	.long	1023820421
	.long	717920726
	.long	1081483256
	.long	3170447711
	.long	1026758832
	.long	3491389776
	.long	1081483250
	.long	3744391389
	.long	3173610490
	.long	2029855968
	.long	1081483245
	.long	394239962
	.long	3173947840
	.long	627974684
	.long	1081483240
	.long	4224315822
	.long	3174184559
	.long	3580403740
	.long	1081483234
	.long	2728751382
	.long	3174034570
	.long	2296901464
	.long	1081483229
	.long	1826853394
	.long	1026771661
	.long	1072130448
	.long	1081483224
	.long	976251207
	.long	1023454847
	.long	4200755644
	.long	1081483218
	.long	3540660088
	.long	3173642356
	.long	3092542436
	.long	1081483213
	.long	642222050
	.long	3174357226
	.long	2042160404
	.long	1081483208
	.long	4271938163
	.long	1024746297
	.long	1049314112
	.long	1081483203
	.long	2763050982
	.long	3171347092
	.long	113710388
	.long	1081483198
	.long	3792557536
	.long	1023308621
	.long	3530025584
	.long	1081483192
	.long	311855242
	.long	1025741510
	.long	2708036380
	.long	1081483187
	.long	246870760
	.long	3174242823
	.long	1942423536
	.long	1081483182
	.long	3741324184
	.long	1024230514
	.long	1232902676
	.long	1081483177
	.long	738314997
	.long	1025672705
	.long	579191580
	.long	1081483172
	.long	2156805174
	.long	1024422082
	.long	4275977444
	.long	1081483166
	.long	1311555958
	.long	3174065520
	.long	3733047680
	.long	1081483161
	.long	3764263691
	.long	3174151698
	.long	3245093668
	.long	1081483156
	.long	3869296466
	.long	3171928183
	.long	2811841556
	.long	1081483151
	.long	690806523
	.long	1025267317
	.long	2433019534
	.long	1081483146
	.long	2145634608
	.long	1026057800
	.long	2108357812
	.long	1081483141
	.long	3978517915
	.long	1027003985
	.long	1837588600
	.long	1081483136
	.long	1798363739
	.long	1024820509
	.long	1620446092
	.long	1081483131
	.long	1050593265
	.long	3173881020
	.long	1456666440
	.long	1081483126
	.long	992424745
	.long	3172306519
	.long	1345987738
	.long	1081483121
	.long	3429191021
	.long	3172872803
	.long	1288150008
	.long	1081483116
	.long	80339595
	.long	1025975133
	.long	1282895172
	.long	1081483111
	.long	1099626523
	.long	3173274851
	.long	1329967044
	.long	1081483106
	.long	37527030
	.long	1026870817
	.long	1429111296
	.long	1081483101
	.long	4119125584
	.long	3165241791
	.long	1580075462
	.long	1081483096
	.long	2270013714
	.long	1025817458
	.long	1782608896
	.long	1081483091
	.long	3203429680
	.long	1025496563
	.long	2036462774
	.long	1081483086
	.long	1148983882
	.long	1026950273
	.long	2341390064
	.long	1081483081
	.long	4068830577
	.long	1025284407
	.long	2697145518
	.long	1081483076
	.long	1923810050
	.long	1026721061
	.long	3103485644
	.long	1081483071
	.long	3762557359
	.long	1025074748
	.long	3560168702
	.long	1081483066
	.long	253913897
	.long	3171090011
	.long	4066954676
	.long	1081483061
	.long	1497751894
	.long	3172734642
	.long	328637968
	.long	1081483057
	.long	3363029157
	.long	3172289292
	.long	934916564
	.long	1081483052
	.long	3896109434
	.long	1024906782
	.long	1590588242
	.long	1081483047
	.long	570002366
	.long	3166515828
	.long	2295419742
	.long	1081483042
	.long	1386275374
	.long	1027010807
	.long	3049179448
	.long	1081483037
	.long	352253063
	.long	1025583264
	.long	3851637384
	.long	1081483032
	.long	606289825
	.long	1026218875
	.long	407597888
	.long	1081483028
	.long	2091542702
	.long	1025209018
	.long	1306768794
	.long	1081483023
	.long	1253650337
	.long	1026509039
	.long	2253957632
	.long	1081483018
	.long	4231155942
	.long	1026471430
	.long	3248940804
	.long	1081483013
	.long	3016566426
	.long	1026594534
	.long	4291496268
	.long	1081483008
	.long	2473443710
	.long	3172512713
	.long	1086436232
	.long	1081483004
	.long	3621506366
	.long	1025637579
	.long	2223476316
	.long	1081482999
	.long	1546124245
	.long	3173819367
	.long	3407431776
	.long	1081482994
	.long	906095450
	.long	3170941588
	.long	343119360
	.long	1081482990
	.long	610131836
	.long	3173841951
	.long	1620259200
	.long	1081482985
	.long	989279629
	.long	3174040346
	.long	2943671008
	.long	1081482980
	.long	3822867778
	.long	1024701039
	.long	18175958
	.long	1081482976
	.long	1622666714
	.long	3174118882
	.long	1433498564
	.long	1081482971
	.long	2171198509
	.long	1026059907
	.long	2894462876
	.long	1081482966
	.long	348671320
	.long	1025985459
	.long	105894376
	.long	1081482962
	.long	3585216657
	.long	3171231928
	.long	1657521836
	.long	1081482957
	.long	3446444124
	.long	1025447652
	.long	3254173534
	.long	1081482952
	.long	1463686921
	.long	3170751639
	.long	600679136
	.long	1081482948
	.long	3946680458
	.long	3172628318
	.long	2286771562
	.long	1081482943
	.long	2898292254
	.long	3173607956
	.long	4017283204
	.long	1081482938
	.long	378425754
	.long	1024364086
	.long	1497047798
	.long	1081482934
	.long	2208076810
	.long	1021550089
	.long	3315802306
	.long	1081482929
	.long	518466868
	.long	3170182275
	.long	883415824
	.long	1081482925
	.long	2262926030
	.long	3173232842
	.long	2789627944
	.long	1081482920
	.long	1857642286
	.long	3174551967
	.long	444310372
	.long	1081482916
	.long	312835987
	.long	1025199986
	.long	2437205282
	.long	1081482911
	.long	1932061261
	.long	3169727498
	.long	178186948
	.long	1081482907
	.long	2992653487
	.long	1024639967
	.long	2257000084
	.long	1081482902
	.long	2345620679
	.long	1020767444
	.long	83521478
	.long	1081482898
	.long	1865371320
	.long	3168909500
	.long	2247498342
	.long	1081482893
	.long	3458599018
	.long	1024937135
	.long	158809936
	.long	1081482889
	.long	2726616459
	.long	1023093864
	.long	2407205926
	.long	1081482884
	.long	3206755402
	.long	1022531411
	.long	402568002
	.long	1081482880
	.long	3974974073
	.long	3174453501
	.long	2734648240
	.long	1081482875
	.long	3634353085
	.long	3173855629
	.long	813330716
	.long	1081482871
	.long	1275754339
	.long	3172404325
	.long	3228369874
	.long	1081482866
	.long	2459942624
	.long	1026394723
	.long	1389652140
	.long	1081482862
	.long	3894317897
	.long	1022143780
	.long	3886934286
	.long	1081482857
	.long	2409656812
	.long	3174419085
	.long	2130105044
	.long	1081482853
	.long	399990141
	.long	3174164192
	.long	413956172
	.long	1081482849
	.long	1897372421
	.long	3171133891
	.long	3033280560
	.long	1081482844
	.long	2252287291
	.long	3171644317
	.long	1397970330
	.long	1081482840
	.long	1092600795
	.long	3174474273
	.long	4097787896
	.long	1081482835
	.long	636054898
	.long	1024727874
	.long	2542627584
	.long	1081482831
	.long	893978442
	.long	3170953397
	.long	1027286708
	.long	1081482827
	.long	1310200238
	.long	3172082428
	.long	3846563656
	.long	1081482822
	.long	1929717761
	.long	3174303818
	.long	2410356004
	.long	1081482818
	.long	2079265613
	.long	3173603756
	.long	1013464276
	.long	1081482814
	.long	138096117
	.long	3172000995
	.long	3950690054
	.long	1081482809
	.long	3014271250
	.long	1025494367
	.long	2631934076
	.long	1081482805
	.long	1516771853
	.long	1025097570
	.long	1352000006
	.long	1081482801
	.long	205057013
	.long	1025200619
	.long	110725238
	.long	1081482797
	.long	4146523933
	.long	3174010180
	.long	3202915484
	.long	1081482792
	.long	1438530950
	.long	3172691622
	.long	2038475574
	.long	1081482788
	.long	3556059382
	.long	1022109351
	.long	912213234
	.long	1081482784
	.long	3429560596
	.long	3174416385
	.long	4118937184
	.long	1081482779
	.long	21605584
	.long	3173029554
	.long	3068555236
	.long	1081482775
	.long	812212829
	.long	3174509580
	.long	2055878076
	.long	1081482771
	.long	3822203782
	.long	3172686714
	.long	1080750056
	.long	1081482767
	.long	2015620057
	.long	1020790313
	.long	143016496
	.long	1081482763
	.long	497128390
	.long	3173548042
	.long	3537490964
	.long	1081482758
	.long	2709103148
	.long	3174501920
	.long	2674086084
	.long	1081482754
	.long	567743127
	.long	3172450622
	.long	1847617306
	.long	1081482750
	.long	2788161622
	.long	3174369501
	.long	1057933720
	.long	1081482746
	.long	3612383344
	.long	3174048564
	.long	304885332
	.long	1081482742
	.long	2310781260
	.long	3173370133
	.long	3883290364
	.long	1081482737
	.long	2417832061
	.long	1025537953
	.long	3203066056
	.long	1081482733
	.long	2228257300
	.long	1023509958
	.long	2559032440
	.long	1081482729
	.long	3413175845
	.long	1026737228
	.long	1951043140
	.long	1081482725
	.long	1019314226
	.long	1026188380
	.long	1378952672
	.long	1081482721
	.long	1939366236
	.long	3173263748
	.long	842616428
	.long	1081482717
	.long	2826522465
	.long	1026654019
	.long	341890668
	.long	1081482713
	.long	510868637
	.long	3173428756
	.long	4171599824
	.long	1081482708
	.long	2348490647
	.long	1026914531
	.long	3741667284
	.long	1081482704
	.long	1992702016
	.long	3171098997
	.long	3346919190
	.long	1081482700
	.long	1915735778
	.long	3171038930
	.long	2987215220
	.long	1081482696
	.long	708866615
	.long	1023303854
	.long	2662415896
	.long	1081482692
	.long	1533252285
	.long	1025288173
	.long	2372382570
	.long	1081482688
	.long	3419174070
	.long	3173768463
	.long	2116977420
	.long	1081482684
	.long	2278659151
	.long	1025060560
	.long	1896063436
	.long	1081482680
	.long	2525715938
	.long	3169189535
	.long	1709504428
	.long	1081482676
	.long	1253833294
	.long	1025773641
	.long	1557165004
	.long	1081482672
	.long	3815681768
	.long	3168831918
	.long	1438910576
	.long	1081482668
	.long	3615821994
	.long	1021226882
	.long	1354607344
	.long	1081482664
	.long	3031132768
	.long	1026704265
	.long	1304122296
	.long	1081482660
	.long	997386977
	.long	3171874671
	.long	1287323204
	.long	1081482656
	.type	static_const_table,@object
	.size	static_const_table,4384
	.data
	.section .note.GNU-stack, ""
# End
