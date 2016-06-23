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
	.file "log10f_wmt.c"
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
# -- Begin  log10f
	.text
       .align    16,0x90
	.globl log10f
log10f:
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
        cvtsi2sdl %edx, %xmm6
        mulsd     %xmm1, %xmm0
        subl      $1056964608, %edx
        addl      %edx, %eax
        shrl      $12, %eax
        movsd     (%ebx,%eax), %xmm4
        subsd     %xmm3, %xmm0
        movsd     2088(%ebx), %xmm2
        movsd     2096(%ebx), %xmm1
        mulsd     %xmm0, %xmm7
        addsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm0
        movsd     2104(%ebx), %xmm3
        mulsd     %xmm2, %xmm6
        addsd     %xmm7, %xmm1
        mulsd     %xmm3, %xmm5
        addsd     %xmm4, %xmm6
        mulsd     %xmm1, %xmm0
        mulsd     %xmm3, %xmm0
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
        movl      $11, %edx
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
        movl      $10, %edx
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
	.type	log10f,@function
	.size	log10f,.-log10f
	.data
# -- End  log10f
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
	.long	1352628734
	.long	1070810131
	.long	2987063119
	.long	1070803029
	.long	2814064164
	.long	1070795955
	.long	4212141943
	.long	1070788908
	.long	1980466875
	.long	1070781889
	.long	3813543508
	.long	1070774896
	.long	236279882
	.long	1070767931
	.long	3258536649
	.long	1070760991
	.long	3425300546
	.long	1070754078
	.long	4176272103
	.long	1070747191
	.long	370881335
	.long	1070740331
	.long	4057946465
	.long	1070733495
	.long	1525858624
	.long	1070726686
	.long	547082415
	.long	1070719902
	.long	313247743
	.long	1070713143
	.long	24949840
	.long	1070706409
	.long	3186584478
	.long	1070699699
	.long	426349118
	.long	1070693015
	.long	3855853989
	.long	1070686354
	.long	4120324153
	.long	1070679718
	.long	463247870
	.long	1070673107
	.long	726256111
	.long	1070666519
	.long	4169135061
	.long	1070659954
	.long	1469709924
	.long	1070653414
	.long	493534586
	.long	1070646897
	.long	523975765
	.long	1070640403
	.long	852037507
	.long	1070633932
	.long	776253054
	.long	1070627484
	.long	3897545900
	.long	1070621058
	.long	939256246
	.long	1070614656
	.long	4106776832
	.long	1070608275
	.long	4137779223
	.long	1070601917
	.long	733771778
	.long	1070595068
	.long	1451145848
	.long	1070582440
	.long	534475932
	.long	1070569856
	.long	988918143
	.long	1070557315
	.long	1537935568
	.long	1070544817
	.long	918084032
	.long	1070532362
	.long	2173800955
	.long	1070519949
	.long	4067295355
	.long	1070507578
	.long	1078375409
	.long	1070495250
	.long	584148105
	.long	1070482963
	.long	1384016078
	.long	1070470717
	.long	2289448314
	.long	1070458512
	.long	2123818995
	.long	1070446348
	.long	4017216324
	.long	1070434224
	.long	2521384792
	.long	1070422141
	.long	789441093
	.long	1070410098
	.long	1985788785
	.long	1070398094
	.long	696035438
	.long	1070386130
	.long	106715969
	.long	1070374205
	.long	3415214590
	.long	1070362318
	.long	944721792
	.long	1070350471
	.long	208866570
	.long	1070338662
	.long	141776059
	.long	1070326891
	.long	3982843004
	.long	1070315157
	.long	2096724351
	.long	1070303462
	.long	2037982183
	.long	1070291804
	.long	2781151897
	.long	1070280183
	.long	3310550777
	.long	1070268599
	.long	2620153983
	.long	1070257052
	.long	4008439778
	.long	1070245541
	.long	2193367512
	.long	1070234067
	.long	492128552
	.long	1070222629
	.long	2231095287
	.long	1070211226
	.long	2155771957
	.long	1070199859
	.long	3610551033
	.long	1070188527
	.long	1358732975
	.long	1070177231
	.long	3057253384
	.long	1070165969
	.long	3486771566
	.long	1070154742
	.long	1731433722
	.long	1070143550
	.long	1178768569
	.long	1070132392
	.long	929649969
	.long	1070121268
	.long	93163010
	.long	1070110178
	.long	2081471614
	.long	1070099121
	.long	1724818481
	.long	1070088098
	.long	2451297579
	.long	1070077108
	.long	3401857016
	.long	1070066151
	.long	3725172527
	.long	1070055227
	.long	2577555067
	.long	1070044336
	.long	3417827070
	.long	1070033477
	.long	1122330889
	.long	1070022651
	.long	3459676923
	.long	1070011856
	.long	1025885499
	.long	1070001094
	.long	1604039477
	.long	1069990363
	.long	99428678
	.long	1069979664
	.long	14303095
	.long	1069968996
	.long	562888951
	.long	1069958359
	.long	966275143
	.long	1069947753
	.long	452333559
	.long	1069937178
	.long	2550607841
	.long	1069926633
	.long	2207334103
	.long	1069916119
	.long	2965233831
	.long	1069905635
	.long	4078536805
	.long	1069895181
	.long	512906990
	.long	1069884758
	.long	125238661
	.long	1069874364
	.long	2188747907
	.long	1069863999
	.long	1687868933
	.long	1069853664
	.long	2203085958
	.long	1069843358
	.long	3025962321
	.long	1069833081
	.long	3454039745
	.long	1069822833
	.long	2790771261
	.long	1069812614
	.long	345455060
	.long	1069802424
	.long	4023103876
	.long	1069792261
	.long	259609597
	.long	1069782128
	.long	1266320102
	.long	1069772022
	.long	2080303760
	.long	1069761944
	.long	2039124234
	.long	1069751894
	.long	485812347
	.long	1069741872
	.long	1063773386
	.long	1069731877
	.long	3126793337
	.long	1069721909
	.long	1738980508
	.long	1069711969
	.long	559609852
	.long	1069702056
	.long	3253131575
	.long	1069692169
	.long	604213228
	.long	1069682310
	.long	582455508
	.long	1069672477
	.long	2572533957
	.long	1069662670
	.long	1669112468
	.long	1069652890
	.long	1561692097
	.long	1069643136
	.long	1649656812
	.long	1069633408
	.long	1337189125
	.long	1069623706
	.long	33219107
	.long	1069614030
	.long	1446341380
	.long	1069604379
	.long	699863590
	.long	1069594754
	.long	1511626603
	.long	1069585154
	.long	3309054272
	.long	1069575579
	.long	1229105731
	.long	1069566030
	.long	3298097489
	.long	1069556505
	.long	733771778
	.long	1069546492
	.long	941119219
	.long	1069527542
	.long	1822213925
	.long	1069508641
	.long	2284546201
	.long	1069489789
	.long	1244064507
	.long	1069470986
	.long	1920055672
	.long	1069452231
	.long	3245124337
	.long	1069433524
	.long	4160075380
	.long	1069414865
	.long	3613830132
	.long	1069396254
	.long	563343667
	.long	1069377691
	.long	2563457724
	.long	1069359174
	.long	4291983602
	.long	1069340704
	.long	434524432
	.long	1069322282
	.long	2864265762
	.long	1069303905
	.long	1987192266
	.long	1069285575
	.long	1101749477
	.long	1069267291
	.long	3513866209
	.long	1069249052
	.long	3946945232
	.long	1069230859
	.long	1426691339
	.long	1069212712
	.long	3576005763
	.long	1069194609
	.long	845110406
	.long	1069176552
	.long	871215109
	.long	1069158539
	.long	2708643652
	.long	1069140570
	.long	1123731696
	.long	1069122646
	.long	3774627455
	.long	1069104765
	.long	1146452958
	.long	1069086929
	.long	910975575
	.long	1069069136
	.long	2156738207
	.long	1069051386
	.long	3978928660
	.long	1069033679
	.long	1184347907
	.long	1069016016
	.long	1471215619
	.long	1068998395
	.long	3954270793
	.long	1068980816
	.long	3459676923
	.long	1068963280
	.long	3409862493
	.long	1068945786
	.long	2938558446
	.long	1068928334
	.long	1185705548
	.long	1068910924
	.long	1592362480
	.long	1068893555
	.long	3310712728
	.long	1068876227
	.long	1204006778
	.long	1068858941
	.long	3026374165
	.long	1068841695
	.long	3652963247
	.long	1068824490
	.long	2259754590
	.long	1068807326
	.long	2323505846
	.long	1068790202
	.long	3031762664
	.long	1068773118
	.long	3577772136
	.long	1068756074
	.long	3160429577
	.long	1068739070
	.long	984225922
	.long	1068722106
	.long	554163028
	.long	1068705181
	.long	1085767694
	.long	1068688295
	.long	1800008162
	.long	1068671448
	.long	1923243906
	.long	1068654640
	.long	687176001
	.long	1068637871
	.long	1623765358
	.long	1068621140
	.long	3975249632
	.long	1068604447
	.long	2694095274
	.long	1068587793
	.long	1327852021
	.long	1068571177
	.long	3429171449
	.long	1068554598
	.long	3965826047
	.long	1068538057
	.long	2205565303
	.long	1068521554
	.long	1716070423
	.long	1068505088
	.long	3549949915
	.long	1068478374
	.long	3329575661
	.long	1068445590
	.long	1345699131
	.long	1068412880
	.long	477545290
	.long	1068380243
	.long	3613830132
	.long	1068347678
	.long	767774141
	.long	1068315187
	.long	3436756955
	.long	1068282767
	.long	1652561627
	.long	1068250420
	.long	2635998437
	.long	1068218144
	.long	732052851
	.long	1068185940
	.long	3179609247
	.long	1068153806
	.long	46600657
	.long	1068121744
	.long	2884636118
	.long	1068089751
	.long	1779250255
	.long	1068057829
	.long	4004532339
	.long	1068025976
	.long	3958279445
	.long	1067994193
	.long	341790679
	.long	1067962480
	.long	454760347
	.long	1067930835
	.long	3015335440
	.long	1067899258
	.long	2455010382
	.long	1067867750
	.long	1803457173
	.long	1067836310
	.long	4098519806
	.long	1067804937
	.long	3796209455
	.long	1067773632
	.long	3950504195
	.long	1067742394
	.long	3328378381
	.long	1067711223
	.long	704701956
	.long	1067680119
	.long	3452107772
	.long	1067649080
	.long	1771121268
	.long	1067618108
	.long	3049832979
	.long	1067587201
	.long	1799062323
	.long	1067556360
	.long	1127129613
	.long	1067525584
	.long	4149857684
	.long	1067494872
	.long	1105606892
	.long	1067464226
	.long	3134934760
	.long	1067416919
	.long	166917834
	.long	1067355883
	.long	3892547049
	.long	1067294973
	.long	3436756955
	.long	1067234191
	.long	823651223
	.long	1067173536
	.long	2386449519
	.long	1067113006
	.long	1587501031
	.long	1067052602
	.long	493004867
	.long	1066992323
	.long	1182960573
	.long	1066932168
	.long	1456087142
	.long	1066872137
	.long	3419645072
	.long	1066812229
	.long	604423097
	.long	1066752445
	.long	3734431729
	.long	1066692782
	.long	2072088478
	.long	1066633242
	.long	2072815517
	.long	1066573823
	.long	1615129006
	.long	1066514525
	.long	2885435162
	.long	1066455347
	.long	3281014557
	.long	1066390787
	.long	184567846
	.long	1066272911
	.long	930630719
	.long	1066155272
	.long	1403389293
	.long	1066037870
	.long	1806815988
	.long	1065920704
	.long	2369503088
	.long	1065803773
	.long	3344465584
	.long	1065687076
	.long	713978687
	.long	1065570613
	.long	3369253777
	.long	1065454381
	.long	1795984717
	.long	1065323547
	.long	182837330
	.long	1065092008
	.long	2643543972
	.long	1064860927
	.long	1379209260
	.long	1064630304
	.long	1522032624
	.long	1064400136
	.long	3615182786
	.long	1064036203
	.long	2033862474
	.long	1063577678
	.long	494771387
	.long	1062984042
	.long	1170198564
	.long	1061933680
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
	.long	1352628734
	.long	1046692883
	.long	1107282704
	.long	3219128321
	.long	354870542
	.long	1071369083
	.type	static_const_table,@object
	.size	static_const_table,2112
	.data
	.section .note.GNU-stack, ""
# End
