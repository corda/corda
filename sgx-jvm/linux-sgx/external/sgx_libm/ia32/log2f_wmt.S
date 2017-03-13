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
	.file "log2f_wmt.c"
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
# -- Begin  log2f
	.text
       .align    16,0x90
	.globl log2f
log2f:
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
        movl      $16384, %ecx
        movd      %ecx, %xmm4
        movl      $-32768, %eax
        movd      %eax, %xmm2
        xorpd     %xmm3, %xmm3
        movl      $16368, %edx
        pinsrw    $3, %edx, %xmm3
        xorpd     %xmm5, %xmm5
.L_2TAG_PACKET_0.0.3:
        movd      %xmm0, %edx
        rcpss     %xmm0, %xmm1
        movsd     2080(%ebx), %xmm7
        pshuflw   $68, %xmm0, %xmm0
        paddd     %xmm4, %xmm1
        subl      $8388608, %edx
        cmpl      $2113929216, %edx
        jae       .L_2TAG_PACKET_1.0.3
        andps     %xmm1, %xmm2
        cvtps2pd  %xmm0, %xmm0
        cvtps2pd  %xmm2, %xmm1
        movd      %xmm2, %eax
        andl      $-8388608, %edx
        subl      $1056964608, %edx
        movl      %edx, %ecx
        sarl      $23, %edx
        cvtsi2sdl %edx, %xmm6
        mulsd     %xmm1, %xmm0
        subl      $1056964608, %ecx
        addl      %ecx, %eax
        shrl      $12, %eax
        movsd     (%ebx,%eax), %xmm4
        subsd     %xmm3, %xmm0
        movsd     2088(%ebx), %xmm2
        movsd     2096(%ebx), %xmm1
        mulsd     %xmm0, %xmm7
        addsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm0
        addsd     %xmm4, %xmm6
        addsd     %xmm7, %xmm1
        mulsd     %xmm2, %xmm5
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm0
        addsd     %xmm6, %xmm5
        addsd     %xmm0, %xmm5
        cvtpd2ps  %xmm5, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        addl      $8388608, %edx
        js        .L_2TAG_PACKET_3.0.3
        cmpl      $8388608, %edx
        jb        .L_2TAG_PACKET_4.0.3
        cmpl      $2139095040, %edx
        jae       .L_2TAG_PACKET_5.0.3
        movl      $528482304, %eax
        movd      %eax, %xmm6
        movsd     2064(%ebx), %xmm5
        mulss     %xmm6, %xmm0
        jmp       .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_5.0.3:
        addss     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        cmpl      $-2147483648, %edx
        je        .L_2TAG_PACKET_6.0.3
        addl      %edx, %edx
        cmpl      $-16777216, %edx
        ja        .L_2TAG_PACKET_5.0.3
        xorps     %xmm0, %xmm0
        divss     %xmm0, %xmm0
        movl      $173, %edx
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
.L_2TAG_PACKET_4.0.3:
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_6.0.3
        movl      $1602224128, %eax
        movd      %eax, %xmm7
        mulss     %xmm7, %xmm0
        movsd     2072(%ebx), %xmm5
        jmp       .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_6.0.3:
        movl      $-1082130432, %eax
        movd      %eax, %xmm0
        xorps     %xmm1, %xmm1
        divss     %xmm1, %xmm0
        movl      $172, %edx
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_2.0.3:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
.L_2TAG_PACKET_8.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	log2f,@function
	.size	log2f,.-log2f
	.data
# -- End  log2f
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
	.long	0
	.long	1072693248
	.long	1998916263
	.long	1072681452
	.long	3173270495
	.long	1072669702
	.long	2000860199
	.long	1072657998
	.long	1272047984
	.long	1072646339
	.long	3794524037
	.long	1072634724
	.long	3803106495
	.long	1072623154
	.long	4139350621
	.long	1072611628
	.long	3366391848
	.long	1072600146
	.long	63662823
	.long	1072588708
	.long	1416582474
	.long	1072577312
	.long	1741478598
	.long	1072565959
	.long	3960187916
	.long	1072554648
	.long	2419954872
	.long	1072543380
	.long	73073096
	.long	1072532154
	.long	4181694601
	.long	1072520968
	.long	547806622
	.long	1072509825
	.long	757656496
	.long	1072498722
	.long	3526834581
	.long	1072487659
	.long	3290001261
	.long	1072476637
	.long	3085585033
	.long	1072465655
	.long	1670680485
	.long	1072454713
	.long	2110786378
	.long	1072443810
	.long	3189678060
	.long	1072432946
	.long	3704185224
	.long	1072422121
	.long	2464005749
	.long	1072411335
	.long	2586490145
	.long	1072400587
	.long	2906527345
	.long	1072389877
	.long	2271335546
	.long	1072379205
	.long	3835256167
	.long	1072368570
	.long	2174681645
	.long	1072357973
	.long	467757278
	.long	1072347413
	.long	1904282206
	.long	1072336889
	.long	1095613242
	.long	1072326402
	.long	1254375265
	.long	1072315951
	.long	1309403273
	.long	1072305536
	.long	200556308
	.long	1072295157
	.long	1173533989
	.long	1072284813
	.long	3189793736
	.long	1072274504
	.long	926405093
	.long	1072264231
	.long	1955775707
	.long	1072253992
	.long	975706757
	.long	1072243788
	.long	1284091012
	.long	1072233618
	.long	1893874830
	.long	1072223482
	.long	1827891621
	.long	1072213380
	.long	118730237
	.long	1072203312
	.long	103572845
	.long	1072193277
	.long	834133047
	.long	1072183275
	.long	1371497981
	.long	1072173306
	.long	786005198
	.long	1072163370
	.long	2452088827
	.long	1072153466
	.long	1163258553
	.long	1072143595
	.long	311851589
	.long	1072133756
	.long	3298982784
	.long	1072123948
	.long	649529288
	.long	1072114173
	.long	76790007
	.long	1072104429
	.long	712572003
	.long	1072094716
	.long	1697017023
	.long	1072085034
	.long	2178495145
	.long	1072075383
	.long	1313500133
	.long	1072065763
	.long	2561513738
	.long	1072056173
	.long	800002435
	.long	1072046614
	.long	3799154139
	.long	1072037084
	.long	2157008926
	.long	1072027585
	.long	3659100731
	.long	1072018115
	.long	3213591115
	.long	1072008675
	.long	31044775
	.long	1071999265
	.long	1919304603
	.long	1071989883
	.long	3808564412
	.long	1071980530
	.long	636181417
	.long	1071971207
	.long	231490093
	.long	1071961912
	.long	1840879012
	.long	1071952645
	.long	422672785
	.long	1071943407
	.long	3826917183
	.long	1071934196
	.long	2730525283
	.long	1071925014
	.long	701967006
	.long	1071915860
	.long	1316286934
	.long	1071906733
	.long	3860057913
	.long	1071897633
	.long	3331303149
	.long	1071888561
	.long	3324321351
	.long	1071879516
	.long	3144709244
	.long	1071870498
	.long	2104254386
	.long	1071861507
	.long	3815829096
	.long	1071852542
	.long	3308416273
	.long	1071843604
	.long	4206907352
	.long	1071834692
	.long	1552162950
	.long	1071825807
	.long	3275780188
	.long	1071816947
	.long	135253478
	.long	1071808114
	.long	73645744
	.long	1071799306
	.long	2449718467
	.long	1071790523
	.long	2332833768
	.long	1071781766
	.long	3387792013
	.long	1071773034
	.long	694899285
	.long	1071764328
	.long	2224741414
	.long	1071755646
	.long	3068318658
	.long	1071746989
	.long	2616854210
	.long	1071738357
	.long	266767097
	.long	1071729750
	.long	4009547812
	.long	1071721166
	.long	366929114
	.long	1071712608
	.long	1635468980
	.long	1071704073
	.long	2936821143
	.long	1071695562
	.long	3692515865
	.long	1071687075
	.long	3328937712
	.long	1071678612
	.long	1277271392
	.long	1071670173
	.long	1268415641
	.long	1071661757
	.long	2742995963
	.long	1071653364
	.long	851312691
	.long	1071644995
	.long	2971350593
	.long	1071628625
	.long	3905998082
	.long	1071611978
	.long	3424346780
	.long	1071595377
	.long	453058438
	.long	1071578822
	.long	2517515273
	.long	1071562311
	.long	4266887860
	.long	1071545845
	.long	358942687
	.long	1071529425
	.long	2639818283
	.long	1071513048
	.long	1489227762
	.long	1071496716
	.long	180106617
	.long	1071480428
	.long	1993621493
	.long	1071464183
	.long	1629147435
	.long	1071447982
	.long	2384050109
	.long	1071431824
	.long	3268698085
	.long	1071415709
	.long	3301345460
	.long	1071399637
	.long	1508048308
	.long	1071383608
	.long	1217549516
	.long	1071367621
	.long	1471262833
	.long	1071351676
	.long	1318159864
	.long	1071335773
	.long	4109658129
	.long	1071319911
	.long	319673664
	.long	1071304092
	.long	1904282206
	.long	1071288313
	.long	3646937343
	.long	1071272575
	.long	338264500
	.long	1071256879
	.long	3955855899
	.long	1071241222
	.long	714524339
	.long	1071225607
	.long	2605838414
	.long	1071210031
	.long	153410875
	.long	1071194496
	.long	1067533806
	.long	1071179000
	.long	180337971
	.long	1071163544
	.long	920560604
	.long	1071148127
	.long	2428575699
	.long	1071132749
	.long	3851294344
	.long	1071117410
	.long	47131309
	.long	1071102111
	.long	3060776247
	.long	1071086849
	.long	3468423567
	.long	1071071626
	.long	442479858
	.long	1071056442
	.long	1751501034
	.long	1071041295
	.long	2285293781
	.long	1071026186
	.long	1234723448
	.long	1071011115
	.long	2091653508
	.long	1070996081
	.long	4058951182
	.long	1070981084
	.long	2050428397
	.long	1070966125
	.long	3870652645
	.long	1070951202
	.long	150118312
	.long	1070936317
	.long	2999895448
	.long	1070921467
	.long	3061900616
	.long	1070906654
	.long	3868579735
	.long	1070891877
	.long	368016775
	.long	1070877137
	.long	398716067
	.long	1070862432
	.long	3214712303
	.long	1070847762
	.long	3780484976
	.long	1070833128
	.long	1360840737
	.long	1070818530
	.long	3815829096
	.long	1070803966
	.long	1830887666
	.long	1070789438
	.long	3276530156
	.long	1070774944
	.long	3143525540
	.long	1070760485
	.long	722718060
	.long	1070746061
	.long	3899945937
	.long	1070731670
	.long	3386189576
	.long	1070717314
	.long	2782295200
	.long	1070702992
	.long	1399058777
	.long	1070688704
	.long	2847114277
	.long	1070674449
	.long	2151986001
	.long	1070660228
	.long	2933912513
	.long	1070646040
	.long	227932728
	.long	1070631886
	.long	1958678199
	.long	1070617764
	.long	3170525653
	.long	1070603675
	.long	2119878685
	.long	1070583143
	.long	2835758645
	.long	1070555096
	.long	2899780145
	.long	1070527114
	.long	1026954088
	.long	1070499197
	.long	236130878
	.long	1070471344
	.long	3554951635
	.long	1070443554
	.long	1134865757
	.long	1070415829
	.long	315822346
	.long	1070388167
	.long	4151355006
	.long	1070360567
	.long	2818569420
	.long	1070333031
	.long	3682837470
	.long	1070305557
	.long	1232950086
	.long	1070278146
	.long	2850845811
	.long	1070250796
	.long	3041732649
	.long	1070223508
	.long	613883718
	.long	1070196282
	.long	2973531841
	.long	1070169116
	.long	354993871
	.long	1070142012
	.long	180337971
	.long	1070114968
	.long	1289509543
	.long	1070087984
	.long	2530196301
	.long	1070061060
	.long	2757759525
	.long	1070034196
	.long	835166085
	.long	1070007392
	.long	4222855792
	.long	1069980646
	.long	3208871137
	.long	1069953960
	.long	973562605
	.long	1069927333
	.long	704621746
	.long	1069900764
	.long	1302049555
	.long	1069874253
	.long	1673060149
	.long	1069847800
	.long	732017834
	.long	1069821405
	.long	1695342161
	.long	1069795067
	.long	3491511767
	.long	1069768786
	.long	761003473
	.long	1069742563
	.long	1036101224
	.long	1069716396
	.long	3266000023
	.long	1069690285
	.long	2111714268
	.long	1069664231
	.long	830921328
	.long	1069638233
	.long	2687969258
	.long	1069612290
	.long	2363885129
	.long	1069586403
	.long	3136187749
	.long	1069560571
	.long	3692892517
	.long	1069522069
	.long	3570253499
	.long	1069470625
	.long	3908238678
	.long	1069419290
	.long	2723302721
	.long	1069368064
	.long	2339434949
	.long	1069316946
	.long	798118803
	.long	1069265936
	.long	448161615
	.long	1069215033
	.long	3650623571
	.long	1069164236
	.long	4188780490
	.long	1069113546
	.long	152924137
	.long	1069062963
	.long	2530196301
	.long	1069012484
	.long	844750970
	.long	1068962111
	.long	1812361559
	.long	1068911842
	.long	3570519722
	.long	1068861677
	.long	4268273545
	.long	1068811616
	.long	2066132126
	.long	1068761659
	.long	3725905754
	.long	1068711804
	.long	3135775963
	.long	1068662052
	.long	2785039517
	.long	1068612402
	.long	879114973
	.long	1068562854
	.long	4224353950
	.long	1068513406
	.long	621328030
	.long	1068429177
	.long	522185727
	.long	1068330685
	.long	354069551
	.long	1068232393
	.long	930992923
	.long	1068134300
	.long	3088087314
	.long	1068036405
	.long	3386464469
	.long	1067938708
	.long	2702982241
	.long	1067841208
	.long	1935110224
	.long	1067743904
	.long	2000764363
	.long	1067646795
	.long	3838143225
	.long	1067549880
	.long	4110598596
	.long	1067453159
	.long	3297851897
	.long	1067262895
	.long	3492345695
	.long	1067070223
	.long	2250192214
	.long	1066877934
	.long	1645815860
	.long	1066686026
	.long	3792308805
	.long	1066494497
	.long	207414749
	.long	1066204903
	.long	2718921511
	.long	1065823355
	.long	2393819184
	.long	1065442558
	.long	3228205918
	.long	1064771801
	.long	2754502619
	.long	1063721742
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	4277811695
	.long	1078341186
	.long	4277811695
	.long	3225824834
	.long	1431655765
	.long	1070945621
	.long	1697350398
	.long	1073157447
	.long	1107282704
	.long	3219128321
	.long	354870542
	.long	1071369083
	.type	static_const_table,@object
	.size	static_const_table,2112
	.data
	.section .note.GNU-stack, ""
# End
