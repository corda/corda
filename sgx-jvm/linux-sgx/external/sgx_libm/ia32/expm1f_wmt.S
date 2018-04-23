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
	.file "expm1f_wmt.c"
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
# -- Begin  expm1f
	.text
       .align    16,0x90
	.globl expm1f
expm1f:
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
        movl      112(%esp), %ecx
        movsd     2112(%ebx), %xmm3
        xorpd     %xmm7, %xmm7
        movl      $17208, %eax
        pinsrw    $3, %eax, %xmm7
        cvtps2pd  %xmm0, %xmm1
        movl      %ecx, %edx
        andl      $2147483647, %ecx
        subl      $1043662067, %ecx
        cmpl      $74990712, %ecx
        jae       .L_2TAG_PACKET_0.0.3
        movsd     2096(%ebx), %xmm6
        mulsd     %xmm1, %xmm3
        movapd    %xmm7, %xmm2
        xorpd     %xmm1, %xmm1
        addsd     %xmm3, %xmm7
        movsd     2104(%ebx), %xmm5
        movd      %xmm7, %eax
        subsd     %xmm7, %xmm2
        movl      $16368, %edx
        pinsrw    $3, %edx, %xmm1
        xorpd     %xmm4, %xmm4
        movl      %eax, %ecx
        andl      $255, %eax
        movsd     (%ebx,%eax,8), %xmm0
        addsd     %xmm2, %xmm3
        sarl      $4, %ecx
        andl      $-16, %ecx
        pinsrw    $3, %ecx, %xmm4
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm1, %xmm5
        paddd     %xmm4, %xmm0
        addsd     %xmm6, %xmm5
        mulsd     %xmm5, %xmm0
        subsd     %xmm1, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_0.0.3:
        addl      $1043662067, %ecx
        cmpl      $1043662067, %ecx
        jb        .L_2TAG_PACKET_2.0.3
        cmpl      $0, %edx
        jl        .L_2TAG_PACKET_3.0.3
        cmpl      $1124073472, %ecx
        jae       .L_2TAG_PACKET_4.0.3
        movsd     2096(%ebx), %xmm6
        mulsd     %xmm1, %xmm3
        movapd    %xmm7, %xmm2
        xorpd     %xmm1, %xmm1
        addsd     %xmm3, %xmm7
        movsd     2104(%ebx), %xmm5
        movd      %xmm7, %eax
        subsd     %xmm7, %xmm2
        movl      $16368, %edx
        pinsrw    $3, %edx, %xmm1
        xorpd     %xmm4, %xmm4
        movl      %eax, %ecx
        andl      $255, %eax
        movsd     (%ebx,%eax,8), %xmm0
        addsd     %xmm2, %xmm3
        sarl      $4, %ecx
        andl      $-16, %ecx
        pinsrw    $3, %ecx, %xmm4
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm1, %xmm5
        paddd     %xmm4, %xmm0
        addsd     %xmm6, %xmm5
        mulsd     %xmm5, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        movd      %xmm0, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_5.0.3
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_4.0.3:
        cmpl      $2139095040, %ecx
        jae       .L_2TAG_PACKET_6.0.3
        movl      $2130706432, %eax
        movd      %eax, %xmm0
        mulss     %xmm0, %xmm0
.L_2TAG_PACKET_5.0.3:
        movl      $43, %edx
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
.L_2TAG_PACKET_2.0.3:
        cmpl      $855638016, %ecx
        jb        .L_2TAG_PACKET_9.0.3
        cmpl      $998244352, %ecx
        jb        .L_2TAG_PACKET_10.0.3
        movapd    2048(%ebx), %xmm2
        pshufd    $68, %xmm1, %xmm0
        pshufd    $68, %xmm1, %xmm1
        movapd    2064(%ebx), %xmm4
        mulpd     %xmm0, %xmm2
        mulpd     %xmm1, %xmm1
        pshufd    $68, %xmm0, %xmm7
        addpd     %xmm4, %xmm2
        movapd    2080(%ebx), %xmm6
        mulsd     %xmm1, %xmm7
        mulpd     %xmm0, %xmm2
        mulsd     %xmm7, %xmm1
        addpd     %xmm6, %xmm2
        mulpd     %xmm1, %xmm2
        pshufd    $238, %xmm2, %xmm3
        addsd     %xmm3, %xmm2
        addsd     %xmm2, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_10.0.3:
        movl      $1042983595, %eax
        movd      %eax, %xmm2
        movss     %xmm0, %xmm1
        movl      $1056964608, %edx
        movd      %edx, %xmm3
        mulss     %xmm0, %xmm2
        mulss     %xmm1, %xmm1
        addss     %xmm3, %xmm2
        mulss     %xmm1, %xmm2
        addss     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_9.0.3:
        cmpl      $8388608, %ecx
        jb        .L_2TAG_PACKET_11.0.3
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_11.0.3:
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_1.0.3
        movl      $8388608, %eax
        movd      %eax, %xmm2
        mulss     %xmm2, %xmm2
        movl      $44, %edx
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_3.0.3:
        cmpl      $2139095040, %ecx
        ja        .L_2TAG_PACKET_6.0.3
        movl      $-1082130432, %edx
        movd      %edx, %xmm0
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_6.0.3:
        addss     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_1.0.3:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
.L_2TAG_PACKET_8.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	expm1f,@function
	.size	expm1f,.-expm1f
	.data
# -- End  expm1f
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
	.long	4200250559
	.long	1072696090
	.long	2851812149
	.long	1072698941
	.long	339411585
	.long	1072701800
	.long	1048019041
	.long	1072704666
	.long	772914124
	.long	1072707540
	.long	3899555717
	.long	1072710421
	.long	1928746161
	.long	1072713311
	.long	3541402996
	.long	1072716208
	.long	238821257
	.long	1072719114
	.long	702412510
	.long	1072722027
	.long	728934454
	.long	1072724948
	.long	410360776
	.long	1072727877
	.long	4133881824
	.long	1072730813
	.long	3402036099
	.long	1072733758
	.long	2602514713
	.long	1072736711
	.long	1828292879
	.long	1072739672
	.long	1172597893
	.long	1072742641
	.long	728909815
	.long	1072745618
	.long	590962156
	.long	1072748603
	.long	852742562
	.long	1072751596
	.long	1608493509
	.long	1072754597
	.long	2952712987
	.long	1072757606
	.long	685187902
	.long	1072760624
	.long	3490863953
	.long	1072763649
	.long	2875075254
	.long	1072766683
	.long	3228316108
	.long	1072769725
	.long	351405227
	.long	1072772776
	.long	2930322912
	.long	1072775834
	.long	2471440686
	.long	1072778901
	.long	3366293073
	.long	1072781976
	.long	1416741826
	.long	1072785060
	.long	1014845819
	.long	1072788152
	.long	2257959872
	.long	1072791252
	.long	948735466
	.long	1072794361
	.long	1480023343
	.long	1072797478
	.long	3949972341
	.long	1072800603
	.long	4162030108
	.long	1072803737
	.long	2214878420
	.long	1072806880
	.long	2502433899
	.long	1072810031
	.long	828946858
	.long	1072813191
	.long	1588871207
	.long	1072816359
	.long	586995997
	.long	1072819536
	.long	2218315341
	.long	1072822721
	.long	2288159958
	.long	1072825915
	.long	897099801
	.long	1072829118
	.long	2440944790
	.long	1072832329
	.long	2725843665
	.long	1072835549
	.long	1853186616
	.long	1072838778
	.long	4219606026
	.long	1072842015
	.long	1337108031
	.long	1072845262
	.long	1897844341
	.long	1072848517
	.long	1709341917
	.long	1072851781
	.long	874372905
	.long	1072855054
	.long	3790955393
	.long	1072858335
	.long	1972484976
	.long	1072861626
	.long	4112506593
	.long	1072864925
	.long	1724976915
	.long	1072868234
	.long	3504003472
	.long	1072871551
	.long	964107055
	.long	1072874878
	.long	2799960843
	.long	1072878213
	.long	526652809
	.long	1072881558
	.long	2839424854
	.long	1072884911
	.long	1253935211
	.long	1072888274
	.long	171030293
	.long	1072891646
	.long	3991843581
	.long	1072895026
	.long	4232894513
	.long	1072898416
	.long	1000925746
	.long	1072901816
	.long	2992903935
	.long	1072905224
	.long	1726216749
	.long	1072908642
	.long	1603444721
	.long	1072912069
	.long	2732492859
	.long	1072915505
	.long	926591435
	.long	1072918951
	.long	589198666
	.long	1072922406
	.long	1829099622
	.long	1072925870
	.long	460407023
	.long	1072929344
	.long	887463927
	.long	1072932827
	.long	3219942644
	.long	1072936319
	.long	3272845541
	.long	1072939821
	.long	1156440435
	.long	1072943333
	.long	1276261410
	.long	1072946854
	.long	3743175029
	.long	1072950384
	.long	78413852
	.long	1072953925
	.long	3278348324
	.long	1072957474
	.long	569847338
	.long	1072961034
	.long	654919306
	.long	1072964603
	.long	3645941911
	.long	1072968181
	.long	1065662932
	.long	1072971770
	.long	1617004845
	.long	1072975368
	.long	1118294578
	.long	1072978976
	.long	3978100823
	.long	1072982593
	.long	1720398391
	.long	1072986221
	.long	3049340112
	.long	1072989858
	.long	3784486610
	.long	1072993505
	.long	4040676318
	.long	1072997162
	.long	3933059031
	.long	1073000829
	.long	3577096743
	.long	1073004506
	.long	3088564500
	.long	1073008193
	.long	2583551245
	.long	1073011890
	.long	2178460671
	.long	1073015597
	.long	1990012071
	.long	1073019314
	.long	2135241198
	.long	1073023041
	.long	2731501122
	.long	1073026778
	.long	3896463087
	.long	1073030525
	.long	1453150082
	.long	1073034283
	.long	4109806887
	.long	1073038050
	.long	3395129871
	.long	1073041828
	.long	3723038930
	.long	1073045616
	.long	917841882
	.long	1073049415
	.long	3689071823
	.long	1073053223
	.long	3566716925
	.long	1073057042
	.long	671025100
	.long	1073060872
	.long	3712504873
	.long	1073064711
	.long	4222122499
	.long	1073068561
	.long	2321106615
	.long	1073072422
	.long	2425981843
	.long	1073076293
	.long	363667784
	.long	1073080175
	.long	551349105
	.long	1073084067
	.long	3111574537
	.long	1073087969
	.long	3872257780
	.long	1073091882
	.long	2956612997
	.long	1073095806
	.long	488188413
	.long	1073099741
	.long	885834528
	.long	1073103686
	.long	4273770423
	.long	1073107641
	.long	2186617381
	.long	1073111608
	.long	3339203574
	.long	1073115585
	.long	3561793907
	.long	1073119573
	.long	2979960120
	.long	1073123572
	.long	1719614413
	.long	1073127582
	.long	4201977662
	.long	1073131602
	.long	1963711167
	.long	1073135634
	.long	3721688645
	.long	1073139676
	.long	1013258799
	.long	1073143730
	.long	2555984613
	.long	1073147794
	.long	4182873220
	.long	1073151869
	.long	1727278727
	.long	1073155956
	.long	3907805044
	.long	1073160053
	.long	2263535754
	.long	1073164162
	.long	1218806132
	.long	1073168282
	.long	903334909
	.long	1073172413
	.long	1447192521
	.long	1073176555
	.long	2980802057
	.long	1073180708
	.long	1339972927
	.long	1073184873
	.long	950803702
	.long	1073189049
	.long	1944781191
	.long	1073193236
	.long	158781403
	.long	1073197435
	.long	19972402
	.long	1073201645
	.long	1660913392
	.long	1073205866
	.long	919555682
	.long	1073210099
	.long	2224145553
	.long	1073214343
	.long	1413356050
	.long	1073218599
	.long	2916157145
	.long	1073222866
	.long	2571947539
	.long	1073227145
	.long	515457527
	.long	1073231436
	.long	1176749997
	.long	1073235738
	.long	396319521
	.long	1073240052
	.long	2604962541
	.long	1073244377
	.long	3643909174
	.long	1073248714
	.long	3649726105
	.long	1073253063
	.long	2759350287
	.long	1073257424
	.long	1110089947
	.long	1073261797
	.long	3134592888
	.long	1073266181
	.long	380978316
	.long	1073270578
	.long	1577608921
	.long	1073274986
	.long	2568320822
	.long	1073279406
	.long	3492293770
	.long	1073283838
	.long	194117574
	.long	1073288283
	.long	1403662306
	.long	1073292739
	.long	2966275557
	.long	1073297207
	.long	727685349
	.long	1073301688
	.long	3418903055
	.long	1073306180
	.long	2591453363
	.long	1073310685
	.long	2682146384
	.long	1073315202
	.long	3833209506
	.long	1073319731
	.long	1892288442
	.long	1073324273
	.long	1297350157
	.long	1073328827
	.long	2191782032
	.long	1073333393
	.long	424392917
	.long	1073337972
	.long	434316067
	.long	1073342563
	.long	2366108318
	.long	1073347166
	.long	2069751141
	.long	1073351782
	.long	3985553595
	.long	1073356410
	.long	3964284211
	.long	1073361051
	.long	2152073944
	.long	1073365705
	.long	2990417245
	.long	1073370371
	.long	2331271250
	.long	1073375050
	.long	321958744
	.long	1073379742
	.long	1405169241
	.long	1073384446
	.long	1434058175
	.long	1073389163
	.long	557149882
	.long	1073393893
	.long	3218338682
	.long	1073398635
	.long	977020788
	.long	1073403391
	.long	2572866477
	.long	1073408159
	.long	3861050111
	.long	1073412940
	.long	697153126
	.long	1073417735
	.long	1822067026
	.long	1073422542
	.long	3092190715
	.long	1073427362
	.long	364333489
	.long	1073432196
	.long	2380618042
	.long	1073437042
	.long	703710506
	.long	1073441902
	.long	4076559943
	.long	1073446774
	.long	4062661092
	.long	1073451660
	.long	815859274
	.long	1073456560
	.long	3080351519
	.long	1073461472
	.long	2420883922
	.long	1073466398
	.long	3287523847
	.long	1073471337
	.long	1540824585
	.long	1073476290
	.long	1631695677
	.long	1073481256
	.long	3716502172
	.long	1073486235
	.long	3657065772
	.long	1073491228
	.long	1610600570
	.long	1073496235
	.long	2029714210
	.long	1073501255
	.long	777507147
	.long	1073506289
	.long	2307442995
	.long	1073511336
	.long	2483480501
	.long	1073516397
	.long	1464976603
	.long	1073521472
	.long	3706687593
	.long	1073526560
	.long	778901109
	.long	1073531663
	.long	1432208378
	.long	1073536779
	.long	1532734324
	.long	1073541909
	.long	1242007932
	.long	1073547053
	.long	721996136
	.long	1073552211
	.long	135105010
	.long	1073557383
	.long	3939148246
	.long	1073562568
	.long	3707479175
	.long	1073567768
	.long	3898795731
	.long	1073572982
	.long	382305176
	.long	1073578211
	.long	1912561781
	.long	1073583453
	.long	64696965
	.long	1073588710
	.long	3594158869
	.long	1073593980
	.long	4076975200
	.long	1073599265
	.long	1679558232
	.long	1073604565
	.long	863738719
	.long	1073609879
	.long	1796832535
	.long	1073615207
	.long	351641897
	.long	1073620550
	.long	991358482
	.long	1073625907
	.long	3884662774
	.long	1073631278
	.long	610758006
	.long	1073636665
	.long	4224142467
	.long	1073642065
	.long	2009970496
	.long	1073647481
	.long	2728693978
	.long	1073652911
	.long	2256325230
	.long	1073658356
	.long	764307441
	.long	1073663816
	.long	2719515920
	.long	1073669290
	.long	3999357479
	.long	1073674779
	.long	481706282
	.long	1073680284
	.long	929806999
	.long	1073685803
	.long	1222472308
	.long	1073691337
	.long	1533953344
	.long	1073696886
	.long	2038973688
	.long	1073702450
	.long	2912730644
	.long	1073708029
	.long	35929225
	.long	1073713624
	.long	2174652632
	.long	1073719233
	.long	915592468
	.long	1073724858
	.long	730821105
	.long	1073730498
	.long	1797923801
	.long	1073736153
	.long	436314138
	.long	1059717536
	.long	1431655765
	.long	1067799893
	.long	872628277
	.long	1062650688
	.long	1431655765
	.long	1069897045
	.long	286331153
	.long	1065423121
	.long	0
	.long	1071644672
	.long	4286760334
	.long	1053736893
	.long	4277826245
	.long	1063661122
	.long	1697350398
	.long	1081546055
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,2136
	.data
	.section .note.GNU-stack, ""
# End
