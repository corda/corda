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
	.file "log1pf_wmt.c"
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
# -- Begin  log1pf
	.text
       .align    16,0x90
	.globl log1pf
log1pf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 40(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     112(%esp), %xmm0
        movl      $1065353216, %edx
        movd      %edx, %xmm1
        movl      $16384, %ecx
        movd      %ecx, %xmm4
        movl      $-32768, %eax
        movd      %eax, %xmm2
        xorpd     %xmm3, %xmm3
        movl      $16368, %edx
        pinsrw    $3, %edx, %xmm3
        xorpd     %xmm5, %xmm5
        addss     %xmm0, %xmm1
.L_2TAG_PACKET_0.0.3:
        movd      %xmm0, %ecx
        movd      %xmm1, %edx
        rcpss     %xmm1, %xmm1
        movsd     2080(%ebx), %xmm7
        pshuflw   $68, %xmm0, %xmm0
        paddd     %xmm4, %xmm1
        andl      $2147483647, %ecx
        cmpl      $864026624, %ecx
        jb        .L_2TAG_PACKET_1.0.3
        subl      $8388608, %edx
        cmpl      $2113929216, %edx
        jae       .L_2TAG_PACKET_2.0.3
        andps     %xmm1, %xmm2
        cvtps2pd  %xmm0, %xmm0
        cvtps2pd  %xmm2, %xmm1
        movd      %xmm2, %eax
        andl      $-8388608, %edx
        subl      $1056964608, %edx
        cvtsi2sdl %edx, %xmm6
        mulsd     %xmm1, %xmm0
        subsd     %xmm3, %xmm1
        subl      $1056964608, %edx
        addl      %edx, %eax
        shrl      $12, %eax
        movsd     (%ebx,%eax), %xmm4
        addsd     %xmm1, %xmm0
        movsd     2088(%ebx), %xmm2
        movsd     2096(%ebx), %xmm1
        mulsd     %xmm0, %xmm7
        addsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm0
        mulsd     %xmm2, %xmm6
        addsd     %xmm7, %xmm1
        addsd     %xmm4, %xmm5
        mulsd     %xmm1, %xmm0
        addsd     %xmm6, %xmm5
        addsd     %xmm5, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_2.0.3:
        addl      $8388608, %edx
        js        .L_2TAG_PACKET_4.0.3
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_5.0.3
        cmpl      $2139095040, %edx
        jae       .L_2TAG_PACKET_6.0.3
        movl      $528482304, %eax
        movd      %eax, %xmm6
        movsd     2064(%ebx), %xmm5
        mulss     %xmm6, %xmm0
        movss     %xmm0, %xmm1
        jmp       .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_6.0.3:
        addss     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_4.0.3:
        cmpl      $-2147483648, %edx
        je        .L_2TAG_PACKET_5.0.3
        addl      %edx, %edx
        cmpl      $-16777216, %edx
        ja        .L_2TAG_PACKET_6.0.3
        xorps     %xmm0, %xmm0
        divss     %xmm0, %xmm0
        movl      $143, %edx
.L_2TAG_PACKET_7.0.3:
        movss     %xmm0, (%esp)
        movss     112(%esp), %xmm0
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       112(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        flds      (%esp)
        jmp       .L_2TAG_PACKET_8.0.3
.L_2TAG_PACKET_1.0.3:
        cmpl      $8388608, %ecx
        jb        .L_2TAG_PACKET_9.0.3
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_9.0.3:
        movss     %xmm0, %xmm1
        mulss     %xmm1, %xmm1
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_5.0.3:
        movl      $-1082130432, %eax
        movd      %eax, %xmm0
        xorps     %xmm1, %xmm1
        divss     %xmm1, %xmm0
        movl      $142, %edx
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_3.0.3:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
.L_2TAG_PACKET_8.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	log1pf,@function
	.size	log1pf,.-log1pf
	.data
# -- End  log1pf
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
	.long	4277811695
	.long	1072049730
	.long	4099377379
	.long	1072041554
	.long	2854492448
	.long	1072033410
	.long	3783013829
	.long	1072025297
	.long	1547061728
	.long	1072017216
	.long	3705668804
	.long	1072009165
	.long	649825382
	.long	1072001146
	.long	4257004898
	.long	1071993156
	.long	646346839
	.long	1071985198
	.long	1718090839
	.long	1071977269
	.long	2203733529
	.long	1071969370
	.long	1140698018
	.long	1071961501
	.long	1872170015
	.long	1071953661
	.long	3457002416
	.long	1071945850
	.long	669557459
	.long	1071938069
	.long	1179420986
	.long	1071930316
	.long	4076413893
	.long	1071922591
	.long	4165410145
	.long	1071914895
	.long	556124819
	.long	1071907228
	.long	957937516
	.long	1071899588
	.long	204914613
	.long	1071891976
	.long	1730507025
	.long	1071884391
	.long	387544807
	.long	1071876834
	.long	3922939863
	.long	1071869303
	.long	2912783494
	.long	1071861800
	.long	826988438
	.long	1071854324
	.long	1144260235
	.long	1071846874
	.long	3057005434
	.long	1071839450
	.long	1471209283
	.long	1071832053
	.long	4186184763
	.long	1071824681
	.long	1829683463
	.long	1071817336
	.long	2217517956
	.long	1071810016
	.long	288676754
	.long	1071802722
	.long	3875016090
	.long	1071795452
	.long	3636378776
	.long	1071788208
	.long	3125257108
	.long	1071780989
	.long	1606817369
	.long	1071773795
	.long	2648729924
	.long	1071766625
	.long	1236164615
	.long	1071759480
	.long	951558972
	.long	1071752359
	.long	1089617062
	.long	1071745262
	.long	952179194
	.long	1071738189
	.long	4143093266
	.long	1071731139
	.long	1388251237
	.long	1071724114
	.long	600267432
	.long	1071717112
	.long	1108583542
	.long	1071710133
	.long	2249313505
	.long	1071703177
	.long	3365155278
	.long	1071696244
	.long	3805304058
	.long	1071689334
	.long	2925366939
	.long	1071682447
	.long	87278952
	.long	1071675583
	.long	3249155078
	.long	1071668740
	.long	3195405229
	.long	1071661920
	.long	3601425396
	.long	1071655122
	.long	3853649835
	.long	1071648346
	.long	2393914686
	.long	1071638513
	.long	2944026756
	.long	1071625048
	.long	3870651933
	.long	1071611626
	.long	3993180461
	.long	1071598247
	.long	2142264402
	.long	1071584911
	.long	1454642149
	.long	1071571617
	.long	783063308
	.long	1071558365
	.long	3286084979
	.long	1071545154
	.long	3543033726
	.long	1071531985
	.long	438773454
	.long	1071518858
	.long	1458540784
	.long	1071505771
	.long	1212978710
	.long	1071492725
	.long	2912845216
	.long	1071479719
	.long	1189018219
	.long	1071466754
	.long	3567208110
	.long	1071453828
	.long	403064643
	.long	1071440943
	.long	3536762435
	.long	1071428096
	.long	3343209673
	.long	1071415289
	.long	3091669954
	.long	1071402521
	.long	1765778334
	.long	1071389792
	.long	2653362897
	.long	1071377101
	.long	461431547
	.long	1071364449
	.long	2790898835
	.long	1071351834
	.long	71706889
	.long	1071339258
	.long	4217424686
	.long	1071326718
	.long	1380502992
	.long	1071314217
	.long	3491778671
	.long	1071301752
	.long	1015732707
	.long	1071289325
	.long	1605095660
	.long	1071276934
	.long	35977849
	.long	1071264580
	.long	3977575844
	.long	1071252261
	.long	3927305531
	.long	1071239979
	.long	3275478714
	.long	1071227733
	.long	1125340846
	.long	1071215523
	.long	882913881
	.long	1071203348
	.long	1666971274
	.long	1071191208
	.long	2603916163
	.long	1071179103
	.long	2827693557
	.long	1071167033
	.long	1479703759
	.long	1071154998
	.long	2003684347
	.long	1071142997
	.long	3555691467
	.long	1071131030
	.long	1004016924
	.long	1071119098
	.long	2108975630
	.long	1071107199
	.long	1753021246
	.long	1071095334
	.long	3415503221
	.long	1071083502
	.long	1992719281
	.long	1071071704
	.long	977707388
	.long	1071059939
	.long	3870234994
	.long	1071048206
	.long	1291822077
	.long	1071036507
	.long	1050438164
	.long	1071024840
	.long	2370625547
	.long	1071013205
	.long	188394569
	.long	1071001603
	.long	2331021804
	.long	1070990032
	.long	3747176240
	.long	1070978493
	.long	3686719393
	.long	1070966986
	.long	1405669883
	.long	1070955511
	.long	461103529
	.long	1070944067
	.long	121152472
	.long	1070932654
	.long	3954874384
	.long	1070921271
	.long	2652318780
	.long	1070909920
	.long	4089234463
	.long	1070898599
	.long	3262235675
	.long	1070887309
	.long	3763576649
	.long	1070876049
	.long	601221319
	.long	1070864820
	.long	1673619518
	.long	1070853620
	.long	1999843716
	.long	1070842450
	.long	899399508
	.long	1070831310
	.long	1992167703
	.long	1070820199
	.long	313445281
	.long	1070809118
	.long	3788725476
	.long	1070798065
	.long	3168871046
	.long	1070787042
	.long	2094830420
	.long	1070776048
	.long	4212681596
	.long	1070765082
	.long	288676754
	.long	1070754146
	.long	2568927817
	.long	1070743237
	.long	1829681364
	.long	1070732357
	.long	1737005556
	.long	1070721505
	.long	1666870165
	.long	1070710681
	.long	1000063738
	.long	1070699885
	.long	3417111411
	.long	1070689116
	.long	4013324163
	.long	1070678375
	.long	2183652476
	.long	1070667662
	.long	1622638721
	.long	1070656976
	.long	1734435555
	.long	1070646317
	.long	1927726804
	.long	1070635685
	.long	1615681634
	.long	1070625080
	.long	215909304
	.long	1070614502
	.long	1445381777
	.long	1070603950
	.long	870975437
	.long	1070590754
	.long	1823716217
	.long	1070569756
	.long	323875369
	.long	1070548811
	.long	3839688678
	.long	1070527917
	.long	2667883253
	.long	1070507076
	.long	4293333010
	.long	1070486286
	.long	3324205749
	.long	1070465548
	.long	2966718784
	.long	1070444861
	.long	2140157205
	.long	1070424225
	.long	4066729595
	.long	1070403639
	.long	3386588242
	.long	1070383104
	.long	3337621361
	.long	1070362619
	.long	2870475188
	.long	1070342184
	.long	943446189
	.long	1070321799
	.long	817374162
	.long	1070301463
	.long	1465634365
	.long	1070281176
	.long	1869032404
	.long	1070260938
	.long	1015732707
	.long	1070240749
	.long	2196155141
	.long	1070220608
	.long	118003300
	.long	1070200516
	.long	2381031992
	.long	1070180471
	.long	3707175295
	.long	1070160474
	.long	3120348377
	.long	1070140525
	.long	3946380940
	.long	1070120623
	.long	928049556
	.long	1070100769
	.long	1699849154
	.long	1070080961
	.long	1018125008
	.long	1070061200
	.long	2235845747
	.long	1070041485
	.long	122671437
	.long	1070021817
	.long	2339728060
	.long	1070002194
	.long	3669742467
	.long	1069982617
	.long	3196851002
	.long	1069963086
	.long	11572342
	.long	1069943601
	.long	1800682930
	.long	1069924160
	.long	3372322013
	.long	1069904764
	.long	3835803021
	.long	1069885413
	.long	2306589128
	.long	1069866107
	.long	2201204065
	.long	1069846845
	.long	2647241692
	.long	1069827627
	.long	2778278092
	.long	1069808453
	.long	1733817001
	.long	1069789323
	.long	2954203158
	.long	1069770236
	.long	1295667077
	.long	1069751193
	.long	210141498
	.long	1069732193
	.long	3155274661
	.long	1069713235
	.long	709476866
	.long	1069694321
	.long	636640577
	.long	1069675449
	.long	2116286250
	.long	1069656619
	.long	38479793
	.long	1069637832
	.long	2183652476
	.long	1069619086
	.long	3452748429
	.long	1069600382
	.long	3047045639
	.long	1069581720
	.long	173141005
	.long	1069563100
	.long	970708326
	.long	1069541521
	.long	2106590683
	.long	1069504444
	.long	2198475040
	.long	1069467449
	.long	3996141691
	.long	1069430535
	.long	1669613218
	.long	1069393703
	.long	2283901796
	.long	1069356951
	.long	29117219
	.long	1069320280
	.long	1990183441
	.long	1069283688
	.long	2376948508
	.long	1069247176
	.long	3998935692
	.long	1069210743
	.long	1085389880
	.long	1069174390
	.long	760030536
	.long	1069138115
	.long	1566132617
	.long	1069101918
	.long	2056379433
	.long	1069065799
	.long	792781808
	.long	1069029758
	.long	641565395
	.long	1068993794
	.long	183156961
	.long	1068957907
	.long	2302040698
	.long	1068922096
	.long	1301778891
	.long	1068886362
	.long	84804484
	.long	1068850704
	.long	1562410668
	.long	1068815121
	.long	64741293
	.long	1068779614
	.long	2815553124
	.long	1068744181
	.long	4162338635
	.long	1068708823
	.long	2756122533
	.long	1068673540
	.long	1551389854
	.long	1068638331
	.long	3511047527
	.long	1068603195
	.long	3016419377
	.long	1068568133
	.long	3047045639
	.long	1068533144
	.long	296456961
	.long	1068497513
	.long	3221665916
	.long	1068427825
	.long	3697798734
	.long	1068358282
	.long	3453858508
	.long	1068288883
	.long	4234736672
	.long	1068219627
	.long	3506114812
	.long	1068150514
	.long	3044269692
	.long	1068081543
	.long	346010435
	.long	1068012714
	.long	1513453498
	.long	1067944025
	.long	4074027864
	.long	1067875476
	.long	1275317999
	.long	1067807068
	.long	3559777258
	.long	1067738798
	.long	4204867691
	.long	1067670667
	.long	797775935
	.long	1067602675
	.long	3825228429
	.long	1067534819
	.long	2313634884
	.long	1067467101
	.long	672386641
	.long	1067348671
	.long	3960777458
	.long	1067213778
	.long	1220920383
	.long	1067079157
	.long	684247743
	.long	1066944805
	.long	2020150230
	.long	1066810721
	.long	630721729
	.long	1066676905
	.long	535440936
	.long	1066543355
	.long	1486051399
	.long	1066410070
	.long	2227658502
	.long	1066152307
	.long	2743640746
	.long	1065886792
	.long	4184355568
	.long	1065621801
	.long	1971904113
	.long	1065357333
	.long	341338964
	.long	1064833554
	.long	1498975029
	.long	1064306693
	.long	1440057502
	.long	1063257089
	.long	0
	.long	0
	.long	0
	.long	0
	.long	4277811695
	.long	1078341186
	.long	4277811695
	.long	3225824834
	.long	1431655765
	.long	1070945621
	.long	4277811695
	.long	1047932482
	.long	1107282704
	.long	3219128321
	.long	4277811695
	.long	1047932482
	.type	static_const_table,@object
	.size	static_const_table,2112
	.data
	.section .note.GNU-stack, ""
# End
