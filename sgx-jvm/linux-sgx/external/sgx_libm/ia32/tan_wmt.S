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
	.file "tan_wmt.c"
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
# -- Begin  tan
	.text
       .align    16,0x90
	.globl tan
tan:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 56(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     128(%esp), %xmm0
        pextrw    $3, %xmm0, %eax
        andl      $32767, %eax
        subl      $14368, %eax
        cmpl      $2216, %eax
        ja        .L_2TAG_PACKET_0.0.2
        movapd    5840(%ebx), %xmm5
        movapd    5856(%ebx), %xmm6
        unpcklpd  %xmm0, %xmm0
        movapd    5712(%ebx), %xmm4
        andpd     %xmm0, %xmm4
        movapd    5632(%ebx), %xmm1
        mulpd     %xmm0, %xmm1
        orpd      %xmm4, %xmm5
        addpd     %xmm5, %xmm1
        movapd    %xmm1, %xmm7
        unpckhpd  %xmm7, %xmm7
        cvttsd2si %xmm7, %edx
        cvttpd2dq %xmm1, %xmm1
        cvtdq2pd  %xmm1, %xmm1
        mulpd     %xmm6, %xmm1
        movapd    5664(%ebx), %xmm3
        movsd     5728(%ebx), %xmm5
        addl      $469248, %edx
        movapd    5680(%ebx), %xmm4
        mulpd     %xmm1, %xmm3
        andl      $31, %edx
        mulsd     %xmm1, %xmm5
        movl      %edx, %ecx
        mulpd     %xmm1, %xmm4
        shll      $1, %ecx
        subpd     %xmm3, %xmm0
        mulpd     5696(%ebx), %xmm1
        addl      %ecx, %edx
        shll      $2, %ecx
        addl      %ecx, %edx
        addsd     %xmm0, %xmm5
        movapd    %xmm0, %xmm2
        subpd     %xmm4, %xmm0
        movsd     5744(%ebx), %xmm6
        shll      $4, %edx
        lea       (%ebx), %eax
        andpd     5776(%ebx), %xmm5
        movapd    %xmm0, %xmm3
        addl      %edx, %eax
        subpd     %xmm0, %xmm2
        unpckhpd  %xmm0, %xmm0
        divsd     %xmm5, %xmm6
        subpd     %xmm4, %xmm2
        movapd    16(%eax), %xmm7
        subsd     %xmm5, %xmm3
        mulpd     %xmm0, %xmm7
        subpd     %xmm1, %xmm2
        movapd    48(%eax), %xmm1
        mulpd     %xmm0, %xmm1
        movapd    96(%eax), %xmm4
        mulpd     %xmm0, %xmm4
        addsd     %xmm3, %xmm2
        movapd    %xmm0, %xmm3
        mulpd     %xmm0, %xmm0
        addpd     (%eax), %xmm7
        addpd     32(%eax), %xmm1
        mulpd     %xmm0, %xmm1
        addpd     80(%eax), %xmm4
        addpd     %xmm1, %xmm7
        movapd    112(%eax), %xmm1
        mulpd     %xmm0, %xmm1
        mulpd     %xmm0, %xmm0
        addpd     %xmm1, %xmm4
        movapd    64(%eax), %xmm1
        mulpd     %xmm0, %xmm1
        addpd     %xmm1, %xmm7
        movapd    %xmm3, %xmm1
        mulpd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm0
        mulpd     144(%eax), %xmm1
        mulpd     %xmm3, %xmm4
        movapd    %xmm1, %xmm3
        addpd     %xmm4, %xmm7
        movapd    %xmm1, %xmm4
        mulsd     %xmm7, %xmm0
        unpckhpd  %xmm7, %xmm7
        addsd     %xmm7, %xmm0
        unpckhpd  %xmm1, %xmm1
        addsd     %xmm1, %xmm3
        subsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm1
        movapd    %xmm2, %xmm4
        movsd     144(%eax), %xmm7
        unpckhpd  %xmm2, %xmm2
        addsd     152(%eax), %xmm7
        mulsd     %xmm2, %xmm7
        addsd     136(%eax), %xmm7
        addsd     %xmm1, %xmm7
        addsd     %xmm7, %xmm0
        movsd     5744(%ebx), %xmm7
        mulsd     %xmm6, %xmm4
        movsd     168(%eax), %xmm2
        andpd     %xmm6, %xmm2
        mulsd     %xmm2, %xmm5
        mulsd     160(%eax), %xmm6
        subsd     %xmm5, %xmm7
        subsd     128(%eax), %xmm2
        subsd     %xmm4, %xmm7
        mulsd     %xmm6, %xmm7
        movapd    %xmm3, %xmm4
        subsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm2
        subsd     %xmm2, %xmm4
        addsd     %xmm4, %xmm0
        subsd     %xmm7, %xmm0
        addsd     %xmm3, %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_0.0.2:
        jg        .L_2TAG_PACKET_2.0.2
        shrl      $4, %eax
        cmpl      $268434558, %eax
        jne       .L_2TAG_PACKET_3.0.2
        movapd    %xmm0, %xmm3
        mulsd     5808(%ebx), %xmm3
.L_2TAG_PACKET_3.0.2:
        movsd     5792(%ebx), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     %xmm0, %xmm3
        mulsd     5808(%ebx), %xmm3
        movsd     %xmm3, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_2.0.2:
        movq      5712(%ebx), %xmm7
        andpd     %xmm0, %xmm7
        xorpd     %xmm0, %xmm7
        ucomisd   5760(%ebx), %xmm7
        je        .L_2TAG_PACKET_4.0.2
        subl      $32, %esp
        movsd     %xmm0, (%esp)
        lea       40(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $2, %eax
        movl      %eax, 12(%esp)
        call      __libm_tancot_huge
        addl      $32, %esp
        fldl      8(%esp)
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_4.0.2:
        movq      %xmm0, (%esp)
        fldl      (%esp)
        fsubl     (%esp)
.L_2TAG_PACKET_1.0.2:
        movl      56(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	tan,@function
	.size	tan,.-tan
	.data
# -- End  tan
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2284589306
	.long	1066820852
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1441186365
	.long	1065494243
	.long	1431655765
	.long	1070945621
	.long	0
	.long	0
	.long	0
	.long	0
	.long	236289504
	.long	1064135997
	.long	286331153
	.long	1069617425
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1160476131
	.long	1062722102
	.long	463583772
	.long	1068212666
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1313038235
	.long	1066745731
	.long	0
	.long	0
	.long	1013878342
	.long	1067152618
	.long	0
	.long	0
	.long	3663426833
	.long	1065725283
	.long	3693284251
	.long	1069118808
	.long	650852232
	.long	1065882376
	.long	1996245381
	.long	1071000265
	.long	2008746170
	.long	1064664197
	.long	3055842593
	.long	1068578846
	.long	1495406348
	.long	1064652437
	.long	2269530157
	.long	1069711235
	.long	285563696
	.long	1063576465
	.long	1046897440
	.long	1067705865
	.long	233429731
	.long	1063453151
	.long	522045958
	.long	1068476590
	.long	2354785698
	.long	1069102779
	.long	1317599141
	.long	1012432133
	.long	0
	.long	1072693248
	.long	2828230105
	.long	1065606626
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1512545955
	.long	1068119047
	.long	0
	.long	0
	.long	1127048698
	.long	1067909459
	.long	0
	.long	0
	.long	2300200450
	.long	1067254767
	.long	3593250296
	.long	1070233561
	.long	3009365544
	.long	1066902117
	.long	1127373050
	.long	1071173457
	.long	3046103305
	.long	1066371299
	.long	24583402
	.long	1069723988
	.long	4082511758
	.long	1065914199
	.long	3223889699
	.long	1070020367
	.long	548927984
	.long	1065415756
	.long	558065897
	.long	1068949418
	.long	680073315
	.long	1064940726
	.long	388873200
	.long	1068944270
	.long	3763679576
	.long	1070167541
	.long	1497360404
	.long	1009710547
	.long	0
	.long	1072693248
	.long	64931152
	.long	1067729411
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2467582782
	.long	1069256389
	.long	0
	.long	0
	.long	162150096
	.long	1068946420
	.long	0
	.long	0
	.long	3702794237
	.long	1068579152
	.long	3631919291
	.long	1070936926
	.long	3456821413
	.long	1068217218
	.long	2031366438
	.long	1071495745
	.long	1596664020
	.long	1067799281
	.long	1509038701
	.long	1070601643
	.long	583171477
	.long	1067510148
	.long	3785344682
	.long	1070618476
	.long	2402036048
	.long	1067075736
	.long	3233018412
	.long	1069913186
	.long	411280568
	.long	1066710556
	.long	1065584192
	.long	1069747896
	.long	895247324
	.long	1070819848
	.long	500078909
	.long	3161288781
	.long	0
	.long	1072693248
	.long	729983843
	.long	1068994194
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1458794562
	.long	1070398550
	.long	0
	.long	0
	.long	2857777489
	.long	1070137637
	.long	0
	.long	0
	.long	1024359517
	.long	1069876531
	.long	2616040238
	.long	1071582937
	.long	1609024636
	.long	1069675088
	.long	2529240549
	.long	1071836633
	.long	1510128600
	.long	1069440113
	.long	2251697184
	.long	1071253687
	.long	1262761453
	.long	1069142850
	.long	1263091857
	.long	1071190461
	.long	3043383486
	.long	1068885191
	.long	2476932470
	.long	1070842002
	.long	3659995028
	.long	1068669200
	.long	855891755
	.long	1070696894
	.long	2583490354
	.long	1071284857
	.long	3062633575
	.long	1014008623
	.long	0
	.long	1072693248
	.long	2550940471
	.long	1069938201
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3422807297
	.long	1071640847
	.long	0
	.long	0
	.long	1151658053
	.long	1071494715
	.long	0
	.long	0
	.long	929607071
	.long	1071346340
	.long	1037049034
	.long	1072037305
	.long	2786928657
	.long	1071215282
	.long	1447406859
	.long	1072265209
	.long	3490952107
	.long	1071090851
	.long	3205232916
	.long	1071968658
	.long	1297344304
	.long	1070977120
	.long	1066110976
	.long	1071946035
	.long	3803721480
	.long	1070871082
	.long	1496754229
	.long	1071807201
	.long	2982550683
	.long	1070773243
	.long	4014441989
	.long	1071736222
	.long	419968236
	.long	1071717047
	.long	3451266538
	.long	3163444811
	.long	0
	.long	1072693248
	.long	2960267235
	.long	1070745841
	.long	0
	.long	0
	.long	0
	.long	0
	.long	724322768
	.long	1072881308
	.long	0
	.long	0
	.long	643153048
	.long	1072905816
	.long	0
	.long	0
	.long	4285079458
	.long	1072928558
	.long	3912524733
	.long	1072622983
	.long	118362272
	.long	1072952754
	.long	4107767972
	.long	1072827408
	.long	2689502883
	.long	1072976922
	.long	946523347
	.long	1072772766
	.long	573204189
	.long	1073001761
	.long	581531518
	.long	1072826391
	.long	1386236526
	.long	1073026959
	.long	3718905905
	.long	1072832823
	.long	1145558140
	.long	1073052673
	.long	513572637
	.long	1072861969
	.long	716700048
	.long	1071997368
	.long	547126769
	.long	1015523525
	.long	0
	.long	1072693248
	.long	1097907398
	.long	1071420120
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3349892442
	.long	1074290212
	.long	0
	.long	0
	.long	3913197405
	.long	1074501181
	.long	0
	.long	0
	.long	2494034522
	.long	1074739170
	.long	1264738763
	.long	1073084804
	.long	1520293906
	.long	1074899632
	.long	1958936600
	.long	1073411493
	.long	2133649635
	.long	1075052171
	.long	4270740730
	.long	1073574708
	.long	1728930189
	.long	1075224844
	.long	1303998552
	.long	1073799186
	.long	618611933
	.long	1075420255
	.long	1769828046
	.long	1073938542
	.long	2200537986
	.long	1075641421
	.long	433361110
	.long	1074105369
	.long	719595600
	.long	1072317184
	.long	294527206
	.long	3162140088
	.long	0
	.long	1073741824
	.long	3811788216
	.long	3218400550
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1704352102
	.long	1075943001
	.long	0
	.long	0
	.long	2284589306
	.long	1076258036
	.long	0
	.long	0
	.long	2211264291
	.long	1076659010
	.long	0
	.long	1073741824
	.long	1441186365
	.long	1077028579
	.long	1431655765
	.long	1074091349
	.long	876943673
	.long	1077353622
	.long	2863311531
	.long	1074440874
	.long	236289504
	.long	1077767485
	.long	286331153
	.long	1074860305
	.long	2805473311
	.long	1078115278
	.long	95443718
	.long	1075163227
	.long	1160476131
	.long	1078450742
	.long	463583772
	.long	1075552698
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	1073741824
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1330165971
	.long	3207850745
	.long	0
	.long	0
	.long	217536623
	.long	1059109098
	.long	0
	.long	0
	.long	3492120849
	.long	3205151475
	.long	602185705
	.long	3215678092
	.long	760422958
	.long	1056312597
	.long	555127889
	.long	1067545266
	.long	3139784124
	.long	3202470837
	.long	3690544014
	.long	3213150171
	.long	95707915
	.long	1053635428
	.long	4003114407
	.long	1064581412
	.long	2034926231
	.long	3199711161
	.long	3759536023
	.long	3210559989
	.long	3826928214
	.long	1050893819
	.long	3837960785
	.long	1061790379
	.long	1526325248
	.long	3217967566
	.long	2356426521
	.long	1025423456
	.long	0
	.long	0
	.long	457728975
	.long	1071088276
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1398462608
	.long	3207303968
	.long	0
	.long	0
	.long	26205983
	.long	1058461213
	.long	0
	.long	0
	.long	56226238
	.long	3204528612
	.long	2754706541
	.long	3215359511
	.long	2187799823
	.long	1055634437
	.long	790323742
	.long	1067402587
	.long	1372385848
	.long	3201651479
	.long	4097292716
	.long	3212856302
	.long	3348210357
	.long	1052830099
	.long	2442796466
	.long	1064337602
	.long	862608142
	.long	3198830754
	.long	170296152
	.long	3210060867
	.long	3755571428
	.long	1049933343
	.long	3614866008
	.long	1061361670
	.long	719978496
	.long	3217669096
	.long	1998842465
	.long	3174703977
	.long	0
	.long	0
	.long	3749156607
	.long	1071048258
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	3120498638
	.long	3206749304
	.long	0
	.long	0
	.long	2773578114
	.long	1058009312
	.long	0
	.long	0
	.long	2030783676
	.long	3203817873
	.long	2223654598
	.long	3215071936
	.long	2976134650
	.long	1054987244
	.long	706390066
	.long	1067217386
	.long	4258437615
	.long	3200900378
	.long	1066252975
	.long	3212391267
	.long	815777514
	.long	1051989462
	.long	3202745457
	.long	1064010682
	.long	2493556375
	.long	3198004753
	.long	1046243251
	.long	3209678971
	.long	2593078846
	.long	1049017717
	.long	2763962276
	.long	1060970161
	.long	701480960
	.long	3217377742
	.long	3205862232
	.long	3174660915
	.long	0
	.long	0
	.long	2267016812
	.long	1071015664
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	2107155798
	.long	3206166872
	.long	0
	.long	0
	.long	2642992129
	.long	1057424578
	.long	0
	.long	0
	.long	1936992811
	.long	3203204426
	.long	1485063559
	.long	3214682643
	.long	1432914553
	.long	1054319398
	.long	3996381654
	.long	1067075828
	.long	2833029256
	.long	3200223545
	.long	2866066872
	.long	3211982662
	.long	2432888737
	.long	1051234178
	.long	3669764559
	.long	1063748136
	.long	2458496952
	.long	3197170774
	.long	1948234989
	.long	3209098147
	.long	2843698787
	.long	1048163519
	.long	3398041407
	.long	1060559728
	.long	2829230080
	.long	3217092115
	.long	1034046433
	.long	3174271903
	.long	0
	.long	0
	.long	298675305
	.long	1070989821
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	437603223
	.long	3205589761
	.long	0
	.long	0
	.long	759330352
	.long	1057048511
	.long	0
	.long	0
	.long	3107463368
	.long	3202507988
	.long	3144465176
	.long	3214191500
	.long	2290961810
	.long	1053841035
	.long	1618153340
	.long	1066971547
	.long	3836869393
	.long	3199400272
	.long	584032116
	.long	3211469261
	.long	1245704358
	.long	1050626462
	.long	4247487438
	.long	1063561943
	.long	1669034927
	.long	3196274812
	.long	3844233498
	.long	3208626322
	.long	2706958524
	.long	1047411374
	.long	3857199098
	.long	1060281647
	.long	3593904128
	.long	3216590719
	.long	3267547836
	.long	3172163321
	.long	0
	.long	0
	.long	4076712227
	.long	1070970214
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	3290090340
	.long	3204793485
	.long	0
	.long	0
	.long	3685760367
	.long	1056668370
	.long	0
	.long	0
	.long	2655163949
	.long	3201674917
	.long	628750575
	.long	3213566872
	.long	680140505
	.long	1053299777
	.long	2954464709
	.long	1066900026
	.long	803201619
	.long	3198516435
	.long	1466315631
	.long	3210837162
	.long	1611220163
	.long	1049972438
	.long	2766187256
	.long	1063437894
	.long	1804579484
	.long	3195331491
	.long	3695969289
	.long	3207854418
	.long	2617238373
	.long	1046675948
	.long	3095830084
	.long	1060095334
	.long	3789570048
	.long	3216034914
	.long	23826559
	.long	3172048060
	.long	0
	.long	0
	.long	3870939386
	.long	1070956467
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1571758758
	.long	3203672535
	.long	0
	.long	0
	.long	113026373
	.long	1056416381
	.long	0
	.long	0
	.long	1913766298
	.long	3200523326
	.long	2507068734
	.long	3212502004
	.long	4000648818
	.long	1053003803
	.long	2446607349
	.long	1066858259
	.long	912662124
	.long	3197333001
	.long	1349489537
	.long	3209765608
	.long	3412972607
	.long	1049641401
	.long	1721283327
	.long	1063366855
	.long	1466691883
	.long	3194116746
	.long	3852528092
	.long	3206760861
	.long	285443293
	.long	1046158380
	.long	1758739894
	.long	1059895449
	.long	1858781184
	.long	3214984212
	.long	3447575948
	.long	1024675855
	.long	0
	.long	0
	.long	2242038011
	.long	1070948320
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	0
	.long	0
	.long	0
	.long	0
	.long	737611454
	.long	1056336527
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3594790527
	.long	1052911621
	.long	381774871
	.long	1066844524
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3303051618
	.long	1049456050
	.long	3154187623
	.long	1063343722
	.long	0
	.long	0
	.long	0
	.long	0
	.long	528061788
	.long	1045944910
	.long	2469719819
	.long	1059831159
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1431655765
	.long	1070945621
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1571758758
	.long	1056188887
	.long	0
	.long	0
	.long	113026373
	.long	1056416381
	.long	0
	.long	0
	.long	1913766298
	.long	1053039678
	.long	2507068734
	.long	1065018356
	.long	4000648818
	.long	1053003803
	.long	2446607349
	.long	1066858259
	.long	912662124
	.long	1049849353
	.long	1349489537
	.long	1062281960
	.long	3412972607
	.long	1049641401
	.long	1721283327
	.long	1063366855
	.long	1466691883
	.long	1046633098
	.long	3852528092
	.long	1059277213
	.long	285443293
	.long	1046158380
	.long	1758739894
	.long	1059895449
	.long	1858781184
	.long	1067500564
	.long	3447575948
	.long	3172159503
	.long	0
	.long	0
	.long	2242038011
	.long	1070948320
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	3290090340
	.long	1057309837
	.long	0
	.long	0
	.long	3685760367
	.long	1056668370
	.long	0
	.long	0
	.long	2655163949
	.long	1054191269
	.long	628750575
	.long	1066083224
	.long	680140505
	.long	1053299777
	.long	2954464709
	.long	1066900026
	.long	803201619
	.long	1051032787
	.long	1466315631
	.long	1063353514
	.long	1611220163
	.long	1049972438
	.long	2766187256
	.long	1063437894
	.long	1804579484
	.long	1047847843
	.long	3695969289
	.long	1060370770
	.long	2617238373
	.long	1046675948
	.long	3095830084
	.long	1060095334
	.long	3789570048
	.long	1068551266
	.long	23826559
	.long	1024564412
	.long	0
	.long	0
	.long	3870939386
	.long	1070956467
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	437603223
	.long	1058106113
	.long	0
	.long	0
	.long	759330352
	.long	1057048511
	.long	0
	.long	0
	.long	3107463368
	.long	1055024340
	.long	3144465176
	.long	1066707852
	.long	2290961810
	.long	1053841035
	.long	1618153340
	.long	1066971547
	.long	3836869393
	.long	1051916624
	.long	584032116
	.long	1063985613
	.long	1245704358
	.long	1050626462
	.long	4247487438
	.long	1063561943
	.long	1669034927
	.long	1048791164
	.long	3844233498
	.long	1061142674
	.long	2706958524
	.long	1047411374
	.long	3857199098
	.long	1060281647
	.long	3593904128
	.long	1069107071
	.long	3267547836
	.long	1024679673
	.long	0
	.long	0
	.long	4076712227
	.long	1070970214
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	2107155798
	.long	1058683224
	.long	0
	.long	0
	.long	2642992129
	.long	1057424578
	.long	0
	.long	0
	.long	1936992811
	.long	1055720778
	.long	1485063559
	.long	1067198995
	.long	1432914553
	.long	1054319398
	.long	3996381654
	.long	1067075828
	.long	2833029256
	.long	1052739897
	.long	2866066872
	.long	1064499014
	.long	2432888737
	.long	1051234178
	.long	3669764559
	.long	1063748136
	.long	2458496952
	.long	1049687126
	.long	1948234989
	.long	1061614499
	.long	2843698787
	.long	1048163519
	.long	3398041407
	.long	1060559728
	.long	2829230080
	.long	1069608467
	.long	1034046433
	.long	1026788255
	.long	0
	.long	0
	.long	298675305
	.long	1070989821
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	3120498638
	.long	1059265656
	.long	0
	.long	0
	.long	2773578114
	.long	1058009312
	.long	0
	.long	0
	.long	2030783676
	.long	1056334225
	.long	2223654598
	.long	1067588288
	.long	2976134650
	.long	1054987244
	.long	706390066
	.long	1067217386
	.long	4258437615
	.long	1053416730
	.long	1066252975
	.long	1064907619
	.long	815777514
	.long	1051989462
	.long	3202745457
	.long	1064010682
	.long	2493556375
	.long	1050521105
	.long	1046243251
	.long	1062195323
	.long	2593078846
	.long	1049017717
	.long	2763962276
	.long	1060970161
	.long	701480960
	.long	1069894094
	.long	3205862232
	.long	1027177267
	.long	0
	.long	0
	.long	2267016812
	.long	1071015664
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1398462608
	.long	1059820320
	.long	0
	.long	0
	.long	26205983
	.long	1058461213
	.long	0
	.long	0
	.long	56226238
	.long	1057044964
	.long	2754706541
	.long	1067875863
	.long	2187799823
	.long	1055634437
	.long	790323742
	.long	1067402587
	.long	1372385848
	.long	1054167831
	.long	4097292716
	.long	1065372654
	.long	3348210357
	.long	1052830099
	.long	2442796466
	.long	1064337602
	.long	862608142
	.long	1051347106
	.long	170296152
	.long	1062577219
	.long	3755571428
	.long	1049933343
	.long	3614866008
	.long	1061361670
	.long	719978496
	.long	1070185448
	.long	1998842465
	.long	1027220329
	.long	0
	.long	0
	.long	3749156607
	.long	1071048258
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1330165971
	.long	1060367097
	.long	0
	.long	0
	.long	217536623
	.long	1059109098
	.long	0
	.long	0
	.long	3492120849
	.long	1057667827
	.long	602185705
	.long	1068194444
	.long	760422958
	.long	1056312597
	.long	555127889
	.long	1067545266
	.long	3139784124
	.long	1054987189
	.long	3690544014
	.long	1065666523
	.long	95707915
	.long	1053635428
	.long	4003114407
	.long	1064581412
	.long	2034926231
	.long	1052227513
	.long	3759536023
	.long	1063076341
	.long	3826928214
	.long	1050893819
	.long	3837960785
	.long	1061790379
	.long	1526325248
	.long	1070483918
	.long	2356426521
	.long	3172907104
	.long	0
	.long	0
	.long	457728975
	.long	1071088276
	.long	0
	.long	1072693248
	.long	0
	.long	4294967288
	.long	1704352102
	.long	3223426649
	.long	0
	.long	0
	.long	2284589306
	.long	1076258036
	.long	0
	.long	0
	.long	2211264291
	.long	3224142658
	.long	0
	.long	3221225472
	.long	1441186365
	.long	1077028579
	.long	1431655765
	.long	1074091349
	.long	876943673
	.long	3224837270
	.long	2863311531
	.long	3221924522
	.long	236289504
	.long	1077767485
	.long	286331153
	.long	1074860305
	.long	2805473311
	.long	3225598926
	.long	95443718
	.long	3222646875
	.long	1160476131
	.long	1078450742
	.long	463583772
	.long	1075552698
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	1073741824
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3349892442
	.long	3221773860
	.long	0
	.long	0
	.long	3913197405
	.long	1074501181
	.long	0
	.long	0
	.long	2494034522
	.long	3222222818
	.long	1264738763
	.long	3220568452
	.long	1520293906
	.long	1074899632
	.long	1958936600
	.long	1073411493
	.long	2133649635
	.long	3222535819
	.long	4270740730
	.long	3221058356
	.long	1728930189
	.long	1075224844
	.long	1303998552
	.long	1073799186
	.long	618611933
	.long	3222903903
	.long	1769828046
	.long	3221422190
	.long	2200537986
	.long	1075641421
	.long	433361110
	.long	1074105369
	.long	719595600
	.long	3219800832
	.long	294527206
	.long	1014656440
	.long	0
	.long	1073741824
	.long	3811788216
	.long	3218400550
	.long	0
	.long	0
	.long	0
	.long	0
	.long	724322768
	.long	3220364956
	.long	0
	.long	0
	.long	643153048
	.long	1072905816
	.long	0
	.long	0
	.long	4285079458
	.long	3220412206
	.long	3912524733
	.long	3220106631
	.long	118362272
	.long	1072952754
	.long	4107767972
	.long	1072827408
	.long	2689502883
	.long	3220460570
	.long	946523347
	.long	3220256414
	.long	573204189
	.long	1073001761
	.long	581531518
	.long	1072826391
	.long	1386236526
	.long	3220510607
	.long	3718905905
	.long	3220316471
	.long	1145558140
	.long	1073052673
	.long	513572637
	.long	1072861969
	.long	716700048
	.long	3219481016
	.long	547126769
	.long	3163007173
	.long	0
	.long	1072693248
	.long	1097907398
	.long	1071420120
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3422807297
	.long	3219124495
	.long	0
	.long	0
	.long	1151658053
	.long	1071494715
	.long	0
	.long	0
	.long	929607071
	.long	3218829988
	.long	1037049034
	.long	3219520953
	.long	2786928657
	.long	1071215282
	.long	1447406859
	.long	1072265209
	.long	3490952107
	.long	3218574499
	.long	3205232916
	.long	3219452306
	.long	1297344304
	.long	1070977120
	.long	1066110976
	.long	1071946035
	.long	3803721480
	.long	3218354730
	.long	1496754229
	.long	3219290849
	.long	2982550683
	.long	1070773243
	.long	4014441989
	.long	1071736222
	.long	419968236
	.long	3219200695
	.long	3451266538
	.long	1015961163
	.long	0
	.long	1072693248
	.long	2960267235
	.long	1070745841
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1458794562
	.long	3217882198
	.long	0
	.long	0
	.long	2857777489
	.long	1070137637
	.long	0
	.long	0
	.long	1024359517
	.long	3217360179
	.long	2616040238
	.long	3219066585
	.long	1609024636
	.long	1069675088
	.long	2529240549
	.long	1071836633
	.long	1510128600
	.long	3216923761
	.long	2251697184
	.long	3218737335
	.long	1262761453
	.long	1069142850
	.long	1263091857
	.long	1071190461
	.long	3043383486
	.long	3216368839
	.long	2476932470
	.long	3218325650
	.long	3659995028
	.long	1068669200
	.long	855891755
	.long	1070696894
	.long	2583490354
	.long	3218768505
	.long	3062633575
	.long	3161492271
	.long	0
	.long	1072693248
	.long	2550940471
	.long	1069938201
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2467582782
	.long	3216740037
	.long	0
	.long	0
	.long	162150096
	.long	1068946420
	.long	0
	.long	0
	.long	3702794237
	.long	3216062800
	.long	3631919291
	.long	3218420574
	.long	3456821413
	.long	1068217218
	.long	2031366438
	.long	1071495745
	.long	1596664020
	.long	3215282929
	.long	1509038701
	.long	3218085291
	.long	583171477
	.long	1067510148
	.long	3785344682
	.long	1070618476
	.long	2402036048
	.long	3214559384
	.long	3233018412
	.long	3217396834
	.long	411280568
	.long	1066710556
	.long	1065584192
	.long	1069747896
	.long	895247324
	.long	3218303496
	.long	500078909
	.long	1013805133
	.long	0
	.long	1072693248
	.long	729983843
	.long	1068994194
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1512545955
	.long	3215602695
	.long	0
	.long	0
	.long	1127048698
	.long	1067909459
	.long	0
	.long	0
	.long	2300200450
	.long	3214738415
	.long	3593250296
	.long	3217717209
	.long	3009365544
	.long	1066902117
	.long	1127373050
	.long	1071173457
	.long	3046103305
	.long	3213854947
	.long	24583402
	.long	3217207636
	.long	4082511758
	.long	1065914199
	.long	3223889699
	.long	1070020367
	.long	548927984
	.long	3212899404
	.long	558065897
	.long	3216433066
	.long	680073315
	.long	1064940726
	.long	388873200
	.long	1068944270
	.long	3763679576
	.long	3217651189
	.long	1497360404
	.long	3157194195
	.long	0
	.long	1072693248
	.long	64931152
	.long	1067729411
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1313038235
	.long	3214229379
	.long	0
	.long	0
	.long	1013878342
	.long	1067152618
	.long	0
	.long	0
	.long	3663426833
	.long	3213208931
	.long	3693284251
	.long	3216602456
	.long	650852232
	.long	1065882376
	.long	1996245381
	.long	1071000265
	.long	2008746170
	.long	3212147845
	.long	3055842593
	.long	3216062494
	.long	1495406348
	.long	1064652437
	.long	2269530157
	.long	1069711235
	.long	285563696
	.long	3211060113
	.long	1046897440
	.long	3215189513
	.long	233429731
	.long	1063453151
	.long	522045958
	.long	1068476590
	.long	2354785698
	.long	3216586427
	.long	1317599141
	.long	3159915781
	.long	0
	.long	1072693248
	.long	2828230105
	.long	1065606626
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1841940611
	.long	1071931184
	.long	1841940611
	.long	1076125488
	.long	0
	.long	1131937792
	.long	0
	.long	1127743488
	.long	1413758976
	.long	1069097467
	.long	1413742592
	.long	1069097467
	.long	1734819840
	.long	3174229945
	.long	1280049152
	.long	1028033571
	.long	923219018
	.long	984130272
	.long	57701189
	.long	988383790
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	1734816687
	.long	1026746297
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	4294705152
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	1130364928
	.long	0
	.long	0
	.long	0
	.long	1015021568
	.long	0
	.long	0
	.long	0
	.long	1017118720
	.long	0
	.long	0
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	0
	.long	1076887552
	.long	0
	.long	1072693248
	.type	static_const_table,@object
	.size	static_const_table,5872
	.data
	.hidden __libm_tancot_huge
	.section .note.GNU-stack, ""
# End
