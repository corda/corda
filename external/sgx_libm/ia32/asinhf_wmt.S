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
	.file "asinhf_wmt.c"
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
# -- Begin  asinhf
	.text
       .align    16,0x90
	.globl asinhf
asinhf:
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
        movss     112(%esp), %xmm0
        movl      112(%esp), %eax
        movl      %eax, %ecx
        movss     %xmm0, 8(%esp)
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        jge       .L_2TAG_PACKET_0.0.2
        cmpl      $1056964608, %eax
        jle       .L_2TAG_PACKET_1.0.2
        cvtps2pd  %xmm0, %xmm0
        movapd    %xmm0, %xmm3
        andpd     128(%ebx), %xmm0
        andpd     112(%ebx), %xmm3
        cmpl      $1199570944, %eax
        jge       .L_2TAG_PACKET_2.0.2
        movapd    %xmm0, %xmm1
        mulsd     %xmm0, %xmm0
        addsd     96(%ebx), %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm1, %xmm0
        pshufd    $85, %xmm0, %xmm1
        movd      %xmm1, %eax
        movl      %eax, %ecx
        shrl      $20, %eax
        shrl      $9, %ecx
        subl      $1023, %eax
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
        movl      %eax, %ecx
        shrl      $23, %eax
        subl      $126, %eax
        shrl      $12, %ecx
.L_2TAG_PACKET_3.0.2:
        cvtsi2sdl %eax, %xmm2
        andpd     144(%ebx), %xmm0
        orpd      96(%ebx), %xmm0
        andl      $2040, %ecx
        movq      72(%ebx), %xmm1
        mulsd     2224(%ebx,%ecx), %xmm0
        subsd     96(%ebx), %xmm0
        mulsd     80(%ebx), %xmm2
        mulsd     %xmm0, %xmm1
        addsd     64(%ebx), %xmm1
        mulsd     %xmm0, %xmm1
        addsd     176(%ebx,%ecx), %xmm2
        mulsd     %xmm0, %xmm1
        addsd     %xmm1, %xmm0
        addsd     %xmm2, %xmm0
        orpd      %xmm3, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_1.0.2:
        cmpl      $838860800, %eax
        jle       .L_2TAG_PACKET_5.0.2
        cvtps2pd  %xmm0, %xmm0
        movapd    48(%ebx), %xmm3
        movapd    %xmm0, %xmm1
        mulsd     %xmm0, %xmm0
        movapd    %xmm0, %xmm2
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm2
        unpcklpd  %xmm0, %xmm0
        mulpd     %xmm0, %xmm3
        addpd     32(%ebx), %xmm3
        mulpd     %xmm0, %xmm3
        addpd     16(%ebx), %xmm3
        mulpd     %xmm0, %xmm3
        addpd     (%ebx), %xmm3
        mulpd     %xmm2, %xmm3
        pshufd    $238, %xmm3, %xmm0
        addsd     %xmm3, %xmm0
        mulsd     %xmm1, %xmm0
        addsd     %xmm1, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_5.0.2:
        andl      $2139095040, %eax
        je        .L_2TAG_PACKET_6.0.2
        shrl      $31, %ecx
        flds      8(%esp)
        fsubl     160(%ebx,%ecx,8)
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_6.0.2:
        flds      8(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fstps     (%esp)
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_0.0.2:
        addss     %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
.L_2TAG_PACKET_4.0.2:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	asinhf,@function
	.size	asinhf,.-asinhf
	.data
# -- End  asinhf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1429119421
	.long	3217380693
	.long	305580873
	.long	1068708659
	.long	3670928078
	.long	3215383394
	.long	2886529429
	.long	1067391652
	.long	1100460448
	.long	3214332679
	.long	2610913564
	.long	1066477690
	.long	3531562621
	.long	3213313024
	.long	3532580246
	.long	1064553239
	.long	3879088107
	.long	3219128321
	.long	1754324240
	.long	1070945626
	.long	4277811695
	.long	1072049730
	.long	4277811695
	.long	1072049730
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.long	0
	.long	862978048
	.long	0
	.long	3010461696
	.long	1440057502
	.long	1063257089
	.long	341338964
	.long	1064833554
	.long	4184355568
	.long	1065621801
	.long	2227658503
	.long	1066152307
	.long	449262470
	.long	1066510009
	.long	1570422842
	.long	1066777242
	.long	121841140
	.long	1067045544
	.long	630691842
	.long	1067281191
	.long	2711485097
	.long	1067500943
	.long	2026622491
	.long	1067619660
	.long	766078039
	.long	1067755853
	.long	4074027864
	.long	1067875476
	.long	3056660584
	.long	1067995528
	.long	3195252556
	.long	1068133258
	.long	116861598
	.long	1068254238
	.long	325086471
	.long	1068375655
	.long	296456961
	.long	1068497513
	.long	2623383041
	.long	1068559379
	.long	1681725707
	.long	1068620754
	.long	2231263004
	.long	1068682354
	.long	2815553124
	.long	1068744181
	.long	2057310944
	.long	1068806237
	.long	2095798018
	.long	1068859611
	.long	2302040698
	.long	1068922096
	.long	745232013
	.long	1068984815
	.long	659581512
	.long	1069047769
	.long	1566132617
	.long	1069101918
	.long	550036981
	.long	1069165314
	.long	2887419564
	.long	1069219844
	.long	1990183441
	.long	1069283688
	.long	3220050856
	.long	1069338605
	.long	1669613218
	.long	1069393703
	.long	2145455994
	.long	1069458213
	.long	4275830246
	.long	1069513705
	.long	1392453108
	.long	1069558451
	.long	1469915213
	.long	1069586382
	.long	2183652476
	.long	1069619086
	.long	2109656884
	.long	1069647220
	.long	636640577
	.long	1069675449
	.long	510517079
	.long	1069703773
	.long	210141498
	.long	1069732193
	.long	2537624622
	.long	1069760709
	.long	1733817001
	.long	1069789323
	.long	137854980
	.long	1069813243
	.long	3905576828
	.long	1069842036
	.long	4131810788
	.long	1069870929
	.long	3760956694
	.long	1069899922
	.long	1473082581
	.long	1069929016
	.long	926113142
	.long	1069953338
	.long	3669742467
	.long	1069982617
	.long	3711755290
	.long	1070007095
	.long	311386407
	.long	1070036564
	.long	705364525
	.long	1070066136
	.long	1973375279
	.long	1070090859
	.long	3589645072
	.long	1070115655
	.long	2425772059
	.long	1070145508
	.long	1074899574
	.long	1070170467
	.long	118003300
	.long	1070200516
	.long	691310403
	.long	1070225639
	.long	3273036717
	.long	1070250837
	.long	1226436560
	.long	1070276112
	.long	2481153995
	.long	1070306542
	.long	3227997535
	.long	1070331985
	.long	1421812646
	.long	1070357506
	.long	3386588242
	.long	1070383104
	.long	2580043380
	.long	1070408781
	.long	1068692560
	.long	1070434537
	.long	938143489
	.long	1070460372
	.long	4293333010
	.long	1070486286
	.long	373864817
	.long	1070512282
	.long	4208958430
	.long	1070538357
	.long	783121462
	.long	1070564515
	.long	870975437
	.long	1070590754
	.long	3338886172
	.long	1070606585
	.long	3772343130
	.long	1070619787
	.long	2408245143
	.long	1070630379
	.long	3811383569
	.long	1070643656
	.long	1622638721
	.long	1070656976
	.long	1290798655
	.long	1070670338
	.long	2457529556
	.long	1070681058
	.long	2398194399
	.long	1070694497
	.long	3190801979
	.long	1070707979
	.long	3352241197
	.long	1070718796
	.long	1829681364
	.long	1070732357
	.long	2568927817
	.long	1070743237
	.long	2676427515
	.long	1070756877
	.long	2882402820
	.long	1070767821
	.long	44944622
	.long	1070781542
	.long	2957945300
	.long	1070792550
	.long	1261787704
	.long	1070806352
	.long	1589923677
	.long	1070817426
	.long	3274931829
	.long	1070828529
	.long	1999843716
	.long	1070842450
	.long	1673619518
	.long	1070853620
	.long	601221319
	.long	1070864820
	.long	3763576649
	.long	1070876049
	.long	1974923437
	.long	1070890129
	.long	1096100617
	.long	1070901427
	.long	2424897952
	.long	1070912755
	.long	2376202600
	.long	1070924114
	.long	1665651958
	.long	1070935504
	.long	1014729354
	.long	1070946925
	.long	722545241
	.long	1070961245
	.long	1800682930
	.long	1070972736
	.long	1029453230
	.long	1070984259
	.long	3450874911
	.long	1070995813
	.long	1228255906
	.long	1071007400
	.long	3711032981
	.long	1071019018
	.long	3075102917
	.long	1071030669
	.long	92696812
	.long	1071042353
	.long	3897749690
	.long	1071051136
	.long	1907922160
	.long	1071062877
	.long	4023807324
	.long	1071074650
	.long	2452345726
	.long	1071086457
	.long	2292128226
	.long	1071098297
	.long	58635942
	.long	1071110171
	.long	864186929
	.long	1071122078
	.long	3555691467
	.long	1071131030
	.long	2003684347
	.long	1071142997
	.long	1479703759
	.long	1071154998
	.long	2537208015
	.long	1071164021
	.long	3699909861
	.long	1071176082
	.long	3934098706
	.long	1071188178
	.long	3885764182
	.long	1071197273
	.long	1357790931
	.long	1071209431
	.long	295956059
	.long	1071221624
	.long	4080877676
	.long	1071230791
	.long	202970280
	.long	1071243047
	.long	3977575845
	.long	1071252261
	.long	35977849
	.long	1071264580
	.long	1585496963
	.long	1071273842
	.long	375247257
	.long	1071286224
	.long	1812199574
	.long	1071295534
	.long	1876912769
	.long	1071307980
	.long	1051846544
	.long	1071317339
	.long	979589448
	.long	1071329850
	.long	71706889
	.long	1071339258
	.long	2790898835
	.long	1071351834
	.long	4013797733
	.long	1071361291
	.long	2541231151
	.long	1071370770
	.long	921733145
	.long	1071383442
	.long	508375805
	.long	1071392971
	.long	3091669954
	.long	1071402521
	.long	3343209673
	.long	1071415289
	.long	1693047557
	.long	1071424891
	.long	173861808
	.long	1071434515
	.long	3515822703
	.long	1071444160
	.long	1974837761
	.long	1071457056
	.long	1189018219
	.long	1071466754
	.long	2441946573
	.long	1071476474
	.long	1887102255
	.long	1071486217
	.long	4271031636
	.long	1071495982
	.long	876995759
	.long	1071509039
	.long	438773454
	.long	1071518858
	.long	172621632
	.long	1071528700
	.long	544035182
	.long	1071538565
	.long	2021801298
	.long	1071548453
	.long	783063308
	.long	1071558365
	.long	1598254051
	.long	1071568300
	.long	651258553
	.long	1071578259
	.long	2719315456
	.long	1071588241
	.long	3993180461
	.long	1071598247
	.long	667093936
	.long	1071608278
	.long	1823716217
	.long	1071618332
	.long	3664357648
	.long	1071628410
	.long	2393914686
	.long	1071638513
	.long	1405419566
	.long	1071646656
	.long	563652001
	.long	1071651732
	.long	1074554091
	.long	1071656820
	.long	3195405229
	.long	1071661920
	.long	2890399284
	.long	1071667033
	.long	420597586
	.long	1071672159
	.long	87278952
	.long	1071675583
	.long	1753119502
	.long	1071680729
	.long	1959759897
	.long	1071685888
	.long	975389815
	.long	1071691060
	.long	3365155278
	.long	1071696244
	.long	811309190
	.long	1071701442
	.long	1269811480
	.long	1071704914
	.long	1108583542
	.long	1071710133
	.long	1034517919
	.long	1071715365
	.long	1327340287
	.long	1071720610
	.long	1388251237
	.long	1071724114
	.long	1488234797
	.long	1071729381
	.long	2711385691
	.long	1071734661
	.long	952179194
	.long	1071738189
	.long	3255685427
	.long	1071743491
	.long	3159039803
	.long	1071748807
	.long	951558972
	.long	1071752359
	.long	3234424125
	.long	1071757697
	.long	3904573547
	.long	1071763049
	.long	2648729924
	.long	1071766625
	.long	2729250212
	.long	1071772000
	.long	2000712574
	.long	1071777389
	.long	3125257108
	.long	1071780989
	.long	3160324803
	.long	1071786401
	.long	2216316974
	.long	1071790017
	.long	3875016090
	.long	1071795452
	.long	1439520610
	.long	1071799084
	.long	1298103361
	.long	1071804543
	.long	2217517956
	.long	1071810016
	.long	1191753587
	.long	1071813673
	.long	1728773900
	.long	1071819170
	.long	747445441
	.long	1071822843
	.long	1670772213
	.long	1071826522
	.long	1471209283
	.long	1071832053
	.long	3287905877
	.long	1071835748
	.long	594038131
	.long	1071841304
	.long	3920147917
	.long	1071845015
	.long	3957142762
	.long	1071850595
	.long	826988438
	.long	1071854324
	.long	242509008
	.long	1071859929
	.long	4173610350
	.long	1071863673
	.long	2518313625
	.long	1071867425
	.long	2671523434
	.long	1071873065
	.long	387544807
	.long	1071876834
	.long	1470497767
	.long	1071880609
	.long	4269259217
	.long	1071886284
	.long	1345663925
	.long	1071890077
	.long	2159323299
	.long	1071893876
	.long	957937516
	.long	1071899588
	.long	2994656967
	.long	1071903404
	.long	556124819
	.long	1071907228
	.long	1631098658
	.long	1071912976
	.long	1368257861
	.long	1071916817
	.long	1309849279
	.long	1071920665
	.long	1567156841
	.long	1071924520
	.long	1179420986
	.long	1071930316
	.long	569448444
	.long	1071934189
	.long	669557459
	.long	1071938069
	.long	1593834700
	.long	1071941956
	.long	3166083125
	.long	1071947800
	.long	211641815
	.long	1071951706
	.long	2780675719
	.long	1071955618
	.long	2400234567
	.long	1071959538
	.long	3482927192
	.long	1071963465
	.long	1852089988
	.long	1071967400
	.long	1020849000
	.long	1071973316
	.long	1718090839
	.long	1071977269
	.long	121152472
	.long	1071981230
	.long	646346839
	.long	1071985198
	.long	3415709705
	.long	1071989173
	.long	4257004898
	.long	1071993156
	.long	3293664199
	.long	1071997147
	.long	649825382
	.long	1072001146
	.long	248887552
	.long	1072007158
	.long	2541522737
	.long	1072011175
	.long	3593780107
	.long	1072015200
	.long	3533041935
	.long	1072019233
	.long	2487426809
	.long	1072023274
	.long	585795318
	.long	1072027323
	.long	2252723088
	.long	1072031379
	.long	3323604690
	.long	1072035443
	.long	3929561382
	.long	1072039515
	.long	4202479723
	.long	1072043595
	.long	4275017539
	.long	1072047683
	.long	0
	.long	1072689152
	.long	0
	.long	1072680960
	.long	0
	.long	1072672768
	.long	0
	.long	1072664576
	.long	0
	.long	1072657408
	.long	0
	.long	1072649216
	.long	0
	.long	1072641024
	.long	0
	.long	1072633856
	.long	0
	.long	1072625664
	.long	0
	.long	1072618496
	.long	0
	.long	1072610304
	.long	0
	.long	1072603136
	.long	0
	.long	1072595968
	.long	0
	.long	1072587776
	.long	0
	.long	1072580608
	.long	0
	.long	1072573440
	.long	0
	.long	1072566272
	.long	0
	.long	1072559104
	.long	0
	.long	1072551936
	.long	0
	.long	1072544768
	.long	0
	.long	1072537600
	.long	0
	.long	1072530432
	.long	0
	.long	1072524288
	.long	0
	.long	1072517120
	.long	0
	.long	1072509952
	.long	0
	.long	1072502784
	.long	0
	.long	1072496640
	.long	0
	.long	1072489472
	.long	0
	.long	1072483328
	.long	0
	.long	1072476160
	.long	0
	.long	1072470016
	.long	0
	.long	1072463872
	.long	0
	.long	1072456704
	.long	0
	.long	1072450560
	.long	0
	.long	1072444416
	.long	0
	.long	1072438272
	.long	0
	.long	1072431104
	.long	0
	.long	1072424960
	.long	0
	.long	1072418816
	.long	0
	.long	1072412672
	.long	0
	.long	1072406528
	.long	0
	.long	1072400384
	.long	0
	.long	1072394240
	.long	0
	.long	1072389120
	.long	0
	.long	1072382976
	.long	0
	.long	1072376832
	.long	0
	.long	1072370688
	.long	0
	.long	1072364544
	.long	0
	.long	1072359424
	.long	0
	.long	1072353280
	.long	0
	.long	1072348160
	.long	0
	.long	1072342016
	.long	0
	.long	1072335872
	.long	0
	.long	1072330752
	.long	0
	.long	1072325632
	.long	0
	.long	1072319488
	.long	0
	.long	1072314368
	.long	0
	.long	1072308224
	.long	0
	.long	1072303104
	.long	0
	.long	1072297984
	.long	0
	.long	1072292864
	.long	0
	.long	1072286720
	.long	0
	.long	1072281600
	.long	0
	.long	1072276480
	.long	0
	.long	1072271360
	.long	0
	.long	1072266240
	.long	0
	.long	1072261120
	.long	0
	.long	1072256000
	.long	0
	.long	1072250880
	.long	0
	.long	1072245760
	.long	0
	.long	1072240640
	.long	0
	.long	1072235520
	.long	0
	.long	1072230400
	.long	0
	.long	1072225280
	.long	0
	.long	1072220160
	.long	0
	.long	1072216064
	.long	0
	.long	1072210944
	.long	0
	.long	1072205824
	.long	0
	.long	1072200704
	.long	0
	.long	1072196608
	.long	0
	.long	1072191488
	.long	0
	.long	1072186368
	.long	0
	.long	1072182272
	.long	0
	.long	1072177152
	.long	0
	.long	1072173056
	.long	0
	.long	1072167936
	.long	0
	.long	1072163840
	.long	0
	.long	1072158720
	.long	0
	.long	1072154624
	.long	0
	.long	1072149504
	.long	0
	.long	1072145408
	.long	0
	.long	1072141312
	.long	0
	.long	1072136192
	.long	0
	.long	1072132096
	.long	0
	.long	1072128000
	.long	0
	.long	1072123904
	.long	0
	.long	1072118784
	.long	0
	.long	1072114688
	.long	0
	.long	1072110592
	.long	0
	.long	1072106496
	.long	0
	.long	1072102400
	.long	0
	.long	1072098304
	.long	0
	.long	1072093184
	.long	0
	.long	1072089088
	.long	0
	.long	1072084992
	.long	0
	.long	1072080896
	.long	0
	.long	1072076800
	.long	0
	.long	1072072704
	.long	0
	.long	1072068608
	.long	0
	.long	1072064512
	.long	0
	.long	1072061440
	.long	0
	.long	1072057344
	.long	0
	.long	1072053248
	.long	0
	.long	1072049152
	.long	0
	.long	1072045056
	.long	0
	.long	1072040960
	.long	0
	.long	1072036864
	.long	0
	.long	1072033792
	.long	0
	.long	1072029696
	.long	0
	.long	1072025600
	.long	0
	.long	1072022528
	.long	0
	.long	1072018432
	.long	0
	.long	1072014336
	.long	0
	.long	1072011264
	.long	0
	.long	1072007168
	.long	0
	.long	1072003072
	.long	0
	.long	1072000000
	.long	0
	.long	1071995904
	.long	0
	.long	1071992832
	.long	0
	.long	1071988736
	.long	0
	.long	1071985664
	.long	0
	.long	1071981568
	.long	0
	.long	1071978496
	.long	0
	.long	1071974400
	.long	0
	.long	1071971328
	.long	0
	.long	1071967232
	.long	0
	.long	1071964160
	.long	0
	.long	1071960064
	.long	0
	.long	1071956992
	.long	0
	.long	1071953920
	.long	0
	.long	1071949824
	.long	0
	.long	1071946752
	.long	0
	.long	1071943680
	.long	0
	.long	1071939584
	.long	0
	.long	1071936512
	.long	0
	.long	1071933440
	.long	0
	.long	1071930368
	.long	0
	.long	1071926272
	.long	0
	.long	1071923200
	.long	0
	.long	1071920128
	.long	0
	.long	1071917056
	.long	0
	.long	1071913984
	.long	0
	.long	1071909888
	.long	0
	.long	1071906816
	.long	0
	.long	1071903744
	.long	0
	.long	1071900672
	.long	0
	.long	1071897600
	.long	0
	.long	1071894528
	.long	0
	.long	1071891456
	.long	0
	.long	1071888384
	.long	0
	.long	1071885312
	.long	0
	.long	1071882240
	.long	0
	.long	1071879168
	.long	0
	.long	1071876096
	.long	0
	.long	1071873024
	.long	0
	.long	1071869952
	.long	0
	.long	1071866880
	.long	0
	.long	1071863808
	.long	0
	.long	1071860736
	.long	0
	.long	1071857664
	.long	0
	.long	1071854592
	.long	0
	.long	1071851520
	.long	0
	.long	1071849472
	.long	0
	.long	1071846400
	.long	0
	.long	1071843328
	.long	0
	.long	1071840256
	.long	0
	.long	1071837184
	.long	0
	.long	1071834112
	.long	0
	.long	1071832064
	.long	0
	.long	1071828992
	.long	0
	.long	1071825920
	.long	0
	.long	1071822848
	.long	0
	.long	1071820800
	.long	0
	.long	1071817728
	.long	0
	.long	1071814656
	.long	0
	.long	1071812608
	.long	0
	.long	1071809536
	.long	0
	.long	1071806464
	.long	0
	.long	1071804416
	.long	0
	.long	1071801344
	.long	0
	.long	1071798272
	.long	0
	.long	1071796224
	.long	0
	.long	1071793152
	.long	0
	.long	1071790080
	.long	0
	.long	1071788032
	.long	0
	.long	1071784960
	.long	0
	.long	1071782912
	.long	0
	.long	1071779840
	.long	0
	.long	1071777792
	.long	0
	.long	1071774720
	.long	0
	.long	1071771648
	.long	0
	.long	1071769600
	.long	0
	.long	1071766528
	.long	0
	.long	1071764480
	.long	0
	.long	1071762432
	.long	0
	.long	1071759360
	.long	0
	.long	1071757312
	.long	0
	.long	1071754240
	.long	0
	.long	1071752192
	.long	0
	.long	1071749120
	.long	0
	.long	1071747072
	.long	0
	.long	1071744000
	.long	0
	.long	1071741952
	.long	0
	.long	1071739904
	.long	0
	.long	1071736832
	.long	0
	.long	1071734784
	.long	0
	.long	1071732736
	.long	0
	.long	1071729664
	.long	0
	.long	1071727616
	.long	0
	.long	1071725568
	.long	0
	.long	1071722496
	.long	0
	.long	1071720448
	.long	0
	.long	1071718400
	.long	0
	.long	1071715328
	.long	0
	.long	1071713280
	.long	0
	.long	1071711232
	.long	0
	.long	1071709184
	.long	0
	.long	1071706112
	.long	0
	.long	1071704064
	.long	0
	.long	1071702016
	.long	0
	.long	1071699968
	.long	0
	.long	1071696896
	.long	0
	.long	1071694848
	.long	0
	.long	1071692800
	.long	0
	.long	1071690752
	.long	0
	.long	1071688704
	.long	0
	.long	1071686656
	.long	0
	.long	1071683584
	.long	0
	.long	1071681536
	.long	0
	.long	1071679488
	.long	0
	.long	1071677440
	.long	0
	.long	1071675392
	.long	0
	.long	1071673344
	.long	0
	.long	1071671296
	.long	0
	.long	1071669248
	.long	0
	.long	1071666176
	.long	0
	.long	1071664128
	.long	0
	.long	1071662080
	.long	0
	.long	1071660032
	.long	0
	.long	1071657984
	.long	0
	.long	1071655936
	.long	0
	.long	1071653888
	.long	0
	.long	1071651840
	.long	0
	.long	1071649792
	.long	0
	.long	1071647744
	.long	0
	.long	1071645696
	.type	static_const_table,@object
	.size	static_const_table,4272
	.data
	.section .note.GNU-stack, ""
# End
