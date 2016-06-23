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
	.file "tanh_wmt.c"
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
# -- Begin  tanh
	.text
       .align    16,0x90
	.globl tanh
tanh:
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
        movsd     112(%esp), %xmm0
        movsd     4256(%ebx), %xmm3
        xorpd     %xmm4, %xmm4
        movsd     4112(%ebx), %xmm1
        movsd     4120(%ebx), %xmm2
        movl      $32768, %eax
        pinsrw    $3, %eax, %xmm4
        movsd     4096(%ebx), %xmm6
        pextrw    $3, %xmm0, %ecx
        andpd     %xmm0, %xmm3
        andnpd    %xmm0, %xmm4
        pshufd    $68, %xmm4, %xmm5
        movl      $32768, %edx
        andl      %ecx, %edx
        andl      $32767, %ecx
        subl      $16304, %ecx
        cmpl      $144, %ecx
        jae       .L_2TAG_PACKET_0.0.2
        subsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm3
        mulsd     %xmm5, %xmm2
        cvtsd2si  %xmm3, %eax
        movapd    %xmm3, %xmm7
        addsd     %xmm6, %xmm3
        mulsd     %xmm4, %xmm1
        movsd     4264(%ebx), %xmm4
        subsd     %xmm6, %xmm3
        xorpd     %xmm0, %xmm0
        addsd     %xmm1, %xmm2
        subsd     %xmm3, %xmm7
        movapd    4128(%ebx), %xmm6
        addsd     %xmm7, %xmm2
        movl      $255, %ecx
        andl      %eax, %ecx
        addl      %ecx, %ecx
        movapd    (%ebx,%ecx,8), %xmm5
        shrl      $4, %eax
        andl      $65520, %eax
        subl      $16368, %eax
        negl      %eax
        pinsrw    $3, %eax, %xmm0
        movapd    4144(%ebx), %xmm1
        pshufd    $68, %xmm0, %xmm0
        mulpd     %xmm5, %xmm0
        movsd     4160(%ebx), %xmm7
        pshufd    $68, %xmm2, %xmm2
        movapd    %xmm4, %xmm5
        addsd     %xmm0, %xmm4
        mulpd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm7
        mulpd     %xmm2, %xmm2
        addpd     %xmm6, %xmm1
        mulsd     %xmm2, %xmm2
        movsd     4264(%ebx), %xmm3
        mulpd     %xmm2, %xmm1
        pshufd    $78, %xmm1, %xmm6
        addsd     %xmm6, %xmm1
        movapd    %xmm1, %xmm6
        addsd     %xmm7, %xmm1
        mulsd     %xmm0, %xmm1
        addsd     %xmm4, %xmm1
        andpd     4224(%ebx), %xmm4
        divsd     %xmm1, %xmm5
        subsd     %xmm4, %xmm3
        pshufd    $238, %xmm0, %xmm1
        addsd     %xmm0, %xmm3
        movapd    %xmm4, %xmm2
        addsd     %xmm1, %xmm3
        mulsd     %xmm7, %xmm1
        mulsd     %xmm0, %xmm7
        addsd     %xmm1, %xmm3
        addsd     %xmm7, %xmm4
        movsd     4240(%ebx), %xmm1
        mulsd     %xmm0, %xmm6
        andpd     4224(%ebx), %xmm4
        addsd     %xmm6, %xmm3
        movapd    %xmm4, %xmm6
        subsd     %xmm4, %xmm2
        addsd     %xmm7, %xmm2
        movsd     4264(%ebx), %xmm7
        andpd     %xmm1, %xmm5
        addsd     %xmm2, %xmm3
        mulsd     %xmm5, %xmm4
        xorpd     %xmm2, %xmm2
        mulsd     %xmm5, %xmm3
        subsd     4272(%ebx), %xmm6
        subsd     %xmm7, %xmm4
        xorl      $32768, %edx
        pinsrw    $3, %edx, %xmm2
        addsd     %xmm3, %xmm4
        mulsd     %xmm5, %xmm6
        movapd    %xmm3, %xmm1
        mulsd     %xmm4, %xmm3
        movapd    %xmm6, %xmm0
        mulsd     %xmm4, %xmm6
        subsd     %xmm3, %xmm1
        subsd     %xmm6, %xmm1
        addsd     %xmm1, %xmm0
        xorpd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_0.0.2:
        addl      $960, %ecx
        cmpl      $1104, %ecx
        jae       .L_2TAG_PACKET_2.0.2
        movapd    4176(%ebx), %xmm2
        pshufd    $68, %xmm0, %xmm1
        movapd    4192(%ebx), %xmm3
        mulpd     %xmm1, %xmm1
        movapd    4208(%ebx), %xmm4
        mulpd     %xmm1, %xmm2
        pshufd    $68, %xmm1, %xmm5
        addpd     %xmm3, %xmm2
        mulsd     %xmm5, %xmm5
        mulpd     %xmm1, %xmm2
        mulsd     %xmm5, %xmm5
        addpd     %xmm4, %xmm2
        mulpd     %xmm5, %xmm2
        pshufd    $238, %xmm2, %xmm5
        addsd     %xmm5, %xmm2
        mulsd     %xmm0, %xmm2
        addsd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_2.0.2:
        addl      $15344, %ecx
        cmpl      $16448, %ecx
        jae       .L_2TAG_PACKET_3.0.2
        cmpl      $16, %ecx
        jb        .L_2TAG_PACKET_4.0.2
        xorpd     %xmm2, %xmm2
        movl      $17392, %eax
        pinsrw    $3, %eax, %xmm2
        mulsd     %xmm0, %xmm2
        addsd     %xmm0, %xmm2
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_4.0.2:
        movapd    %xmm0, %xmm2
        mulsd     %xmm2, %xmm2
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_3.0.2:
        cmpl      $32752, %ecx
        jae       .L_2TAG_PACKET_5.0.2
        xorpd     %xmm2, %xmm2
        movl      $15344, %ecx
        pinsrw    $3, %ecx, %xmm2
        movapd    %xmm2, %xmm3
        mulsd     %xmm2, %xmm2
        addsd     %xmm3, %xmm2
.L_2TAG_PACKET_6.0.2:
        xorpd     %xmm0, %xmm0
        orl       $16368, %edx
        pinsrw    $3, %edx, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_5.0.2:
        movapd    %xmm0, %xmm2
        movd      %xmm0, %eax
        psrlq     $20, %xmm2
        movd      %xmm2, %ecx
        orl       %eax, %ecx
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_6.0.2
        addsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_1.0.2:
        movsd     %xmm0, 24(%esp)
        fldl      24(%esp)
.L_2TAG_PACKET_7.0.2:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	tanh,@function
	.size	tanh,.-tanh
	.data
# -- End  tanh
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1797923801
	.long	1072687577
	.long	1950547427
	.long	1013229059
	.long	730821105
	.long	1072681922
	.long	2523232743
	.long	1012067188
	.long	915592468
	.long	1072676282
	.long	352947894
	.long	3161024371
	.long	2174652632
	.long	1072670657
	.long	4087714590
	.long	1014450259
	.long	35929225
	.long	1072665048
	.long	2809788041
	.long	3159436968
	.long	2912730644
	.long	1072659453
	.long	3490067722
	.long	3163405074
	.long	2038973688
	.long	1072653874
	.long	892941374
	.long	1016046459
	.long	1533953344
	.long	1072648310
	.long	769171851
	.long	1015665633
	.long	1222472308
	.long	1072642761
	.long	1054357470
	.long	3161021018
	.long	929806999
	.long	1072637227
	.long	3205336643
	.long	1015259557
	.long	481706282
	.long	1072631708
	.long	1696079173
	.long	3162710528
	.long	3999357479
	.long	1072626203
	.long	2258941616
	.long	1015924724
	.long	2719515920
	.long	1072620714
	.long	2760332941
	.long	1015137933
	.long	764307441
	.long	1072615240
	.long	3021057420
	.long	3163329523
	.long	2256325230
	.long	1072609780
	.long	580117746
	.long	1015317295
	.long	2728693978
	.long	1072604335
	.long	396109971
	.long	3163462691
	.long	2009970496
	.long	1072598905
	.long	2159039665
	.long	3162572948
	.long	4224142467
	.long	1072593489
	.long	3389820386
	.long	1015207202
	.long	610758006
	.long	1072588089
	.long	1965209397
	.long	3161866232
	.long	3884662774
	.long	1072582702
	.long	2158611599
	.long	1014210185
	.long	991358482
	.long	1072577331
	.long	838715019
	.long	3163157668
	.long	351641897
	.long	1072571974
	.long	2172261526
	.long	3163010599
	.long	1796832535
	.long	1072566631
	.long	3176955716
	.long	3160585513
	.long	863738719
	.long	1072561303
	.long	1326992220
	.long	3162613197
	.long	1679558232
	.long	1072555989
	.long	2390342287
	.long	3163333970
	.long	4076975200
	.long	1072550689
	.long	2029000899
	.long	1015208535
	.long	3594158869
	.long	1072545404
	.long	2456521700
	.long	3163256561
	.long	64696965
	.long	1072540134
	.long	1768797490
	.long	1015816960
	.long	1912561781
	.long	1072534877
	.long	3147495102
	.long	1015678253
	.long	382305176
	.long	1072529635
	.long	2347622376
	.long	3162578625
	.long	3898795731
	.long	1072524406
	.long	1249994144
	.long	1011869818
	.long	3707479175
	.long	1072519192
	.long	3613079303
	.long	1014164738
	.long	3939148246
	.long	1072513992
	.long	3210352148
	.long	1015274323
	.long	135105010
	.long	1072508807
	.long	1906148728
	.long	3163375739
	.long	721996136
	.long	1072503635
	.long	563754734
	.long	1015371318
	.long	1242007932
	.long	1072498477
	.long	1132034716
	.long	3163339831
	.long	1532734324
	.long	1072493333
	.long	3094216535
	.long	3163162857
	.long	1432208378
	.long	1072488203
	.long	1401068914
	.long	3162363963
	.long	778901109
	.long	1072483087
	.long	2248183955
	.long	3161268751
	.long	3706687593
	.long	1072477984
	.long	3521726940
	.long	1013253067
	.long	1464976603
	.long	1072472896
	.long	3507292405
	.long	3161977534
	.long	2483480501
	.long	1072467821
	.long	1216371780
	.long	1013034172
	.long	2307442995
	.long	1072462760
	.long	3190117721
	.long	3162404539
	.long	777507147
	.long	1072457713
	.long	4282924205
	.long	1015187533
	.long	2029714210
	.long	1072452679
	.long	613660079
	.long	1015099143
	.long	1610600570
	.long	1072447659
	.long	3766732298
	.long	1015760183
	.long	3657065772
	.long	1072442652
	.long	399025623
	.long	3162957078
	.long	3716502172
	.long	1072437659
	.long	2303740125
	.long	1014042725
	.long	1631695677
	.long	1072432680
	.long	2717633076
	.long	3162344026
	.long	1540824585
	.long	1072427714
	.long	1064017011
	.long	3163487690
	.long	3287523847
	.long	1072422761
	.long	1625971539
	.long	3157009955
	.long	2420883922
	.long	1072417822
	.long	2049810052
	.long	1014119888
	.long	3080351519
	.long	1072412896
	.long	3379126788
	.long	3157218001
	.long	815859274
	.long	1072407984
	.long	240396590
	.long	3163487443
	.long	4062661092
	.long	1072403084
	.long	1422616006
	.long	3163255318
	.long	4076559943
	.long	1072398198
	.long	2119478331
	.long	3160758351
	.long	703710506
	.long	1072393326
	.long	1384660846
	.long	1015195891
	.long	2380618042
	.long	1072388466
	.long	3149557219
	.long	3163320799
	.long	364333489
	.long	1072383620
	.long	3923737744
	.long	3161421373
	.long	3092190715
	.long	1072378786
	.long	814012168
	.long	3159523422
	.long	1822067026
	.long	1072373966
	.long	1241994956
	.long	1015340290
	.long	697153126
	.long	1072369159
	.long	1283515429
	.long	3163283189
	.long	3861050111
	.long	1072364364
	.long	254893773
	.long	3162813180
	.long	2572866477
	.long	1072359583
	.long	878562433
	.long	1015521741
	.long	977020788
	.long	1072354815
	.long	3065100517
	.long	1015541563
	.long	3218338682
	.long	1072350059
	.long	3404164304
	.long	3162477108
	.long	557149882
	.long	1072345317
	.long	3672720709
	.long	1014537265
	.long	1434058175
	.long	1072340587
	.long	251133233
	.long	1015085769
	.long	1405169241
	.long	1072335870
	.long	2998539689
	.long	3162830951
	.long	321958744
	.long	1072331166
	.long	3401933767
	.long	1015794558
	.long	2331271250
	.long	1072326474
	.long	812057446
	.long	1012207446
	.long	2990417245
	.long	1072321795
	.long	3683467745
	.long	3163369326
	.long	2152073944
	.long	1072317129
	.long	1486860576
	.long	3163203456
	.long	3964284211
	.long	1072312475
	.long	2111583915
	.long	1015427164
	.long	3985553595
	.long	1072307834
	.long	4002146062
	.long	1015834136
	.long	2069751141
	.long	1072303206
	.long	1562170675
	.long	3162724681
	.long	2366108318
	.long	1072298590
	.long	2867985102
	.long	3161762254
	.long	434316067
	.long	1072293987
	.long	2028358766
	.long	1013458122
	.long	424392917
	.long	1072289396
	.long	2749202995
	.long	3162838718
	.long	2191782032
	.long	1072284817
	.long	2960257726
	.long	1013742662
	.long	1297350157
	.long	1072280251
	.long	1308022040
	.long	3163412558
	.long	1892288442
	.long	1072275697
	.long	2446255666
	.long	3162600381
	.long	3833209506
	.long	1072271155
	.long	2722920684
	.long	1013754842
	.long	2682146384
	.long	1072266626
	.long	2082178513
	.long	3163363419
	.long	2591453363
	.long	1072262109
	.long	2132396182
	.long	3159074198
	.long	3418903055
	.long	1072257604
	.long	2527457337
	.long	3160820604
	.long	727685349
	.long	1072253112
	.long	2038246809
	.long	3162358742
	.long	2966275557
	.long	1072248631
	.long	2176155324
	.long	3159842759
	.long	1403662306
	.long	1072244163
	.long	2788809599
	.long	3161671007
	.long	194117574
	.long	1072239707
	.long	777528612
	.long	3163412089
	.long	3492293770
	.long	1072235262
	.long	2248032210
	.long	1015386826
	.long	2568320822
	.long	1072230830
	.long	2732824428
	.long	1014352915
	.long	1577608921
	.long	1072226410
	.long	1875489510
	.long	3162968394
	.long	380978316
	.long	1072222002
	.long	854188970
	.long	3160462686
	.long	3134592888
	.long	1072217605
	.long	4232266862
	.long	1015991134
	.long	1110089947
	.long	1072213221
	.long	1451641639
	.long	1015474673
	.long	2759350287
	.long	1072208848
	.long	1148526634
	.long	1015894933
	.long	3649726105
	.long	1072204487
	.long	4085036346
	.long	1015649474
	.long	3643909174
	.long	1072200138
	.long	3537586109
	.long	1014354647
	.long	2604962541
	.long	1072195801
	.long	2614425274
	.long	3163539192
	.long	396319521
	.long	1072191476
	.long	4172420816
	.long	3159074632
	.long	1176749997
	.long	1072187162
	.long	2738998779
	.long	3162035844
	.long	515457527
	.long	1072182860
	.long	836709333
	.long	1015651226
	.long	2571947539
	.long	1072178569
	.long	3558159064
	.long	3163376669
	.long	2916157145
	.long	1072174290
	.long	219487565
	.long	1015309367
	.long	1413356050
	.long	1072170023
	.long	1651349291
	.long	3162668166
	.long	2224145553
	.long	1072165767
	.long	3482522030
	.long	3161489169
	.long	919555682
	.long	1072161523
	.long	3121969534
	.long	1012948226
	.long	1660913392
	.long	1072157290
	.long	4218599604
	.long	1015135707
	.long	19972402
	.long	1072153069
	.long	3507899862
	.long	1016009292
	.long	158781403
	.long	1072148859
	.long	2221464712
	.long	3163286453
	.long	1944781191
	.long	1072144660
	.long	3993278767
	.long	3161724279
	.long	950803702
	.long	1072140473
	.long	1655364926
	.long	1015237032
	.long	1339972927
	.long	1072136297
	.long	167908909
	.long	1015572152
	.long	2980802057
	.long	1072132132
	.long	378619896
	.long	1015773303
	.long	1447192521
	.long	1072127979
	.long	1462857171
	.long	3162514521
	.long	903334909
	.long	1072123837
	.long	1636462108
	.long	1015039997
	.long	1218806132
	.long	1072119706
	.long	1818613052
	.long	3162548441
	.long	2263535754
	.long	1072115586
	.long	752233586
	.long	3162639008
	.long	3907805044
	.long	1072111477
	.long	2257091225
	.long	3161550407
	.long	1727278727
	.long	1072107380
	.long	3562710623
	.long	1011471940
	.long	4182873220
	.long	1072103293
	.long	629542646
	.long	3161996303
	.long	2555984613
	.long	1072099218
	.long	2652555442
	.long	3162552692
	.long	1013258799
	.long	1072095154
	.long	1748797611
	.long	3160129082
	.long	3721688645
	.long	1072091100
	.long	3069276937
	.long	1015839401
	.long	1963711167
	.long	1072087058
	.long	1744767757
	.long	3160574294
	.long	4201977662
	.long	1072083026
	.long	748330254
	.long	1013594357
	.long	1719614413
	.long	1072079006
	.long	330458198
	.long	3163282740
	.long	2979960120
	.long	1072074996
	.long	2599109725
	.long	1014498493
	.long	3561793907
	.long	1072070997
	.long	1157054053
	.long	1011890350
	.long	3339203574
	.long	1072067009
	.long	1483497780
	.long	3162408754
	.long	2186617381
	.long	1072063032
	.long	2270764084
	.long	3163272713
	.long	4273770423
	.long	1072059065
	.long	3383180809
	.long	3163218901
	.long	885834528
	.long	1072055110
	.long	1973258547
	.long	3162261564
	.long	488188413
	.long	1072051165
	.long	3199821029
	.long	1015564048
	.long	2956612997
	.long	1072047230
	.long	2118169751
	.long	3162735553
	.long	3872257780
	.long	1072043306
	.long	1253592103
	.long	1015958334
	.long	3111574537
	.long	1072039393
	.long	2606161479
	.long	3162759746
	.long	551349105
	.long	1072035491
	.long	3821916050
	.long	3162106589
	.long	363667784
	.long	1072031599
	.long	813753950
	.long	1015785209
	.long	2425981843
	.long	1072027717
	.long	2830390851
	.long	3163346599
	.long	2321106615
	.long	1072023846
	.long	2171176610
	.long	1009535771
	.long	4222122499
	.long	1072019985
	.long	1277378074
	.long	3163256737
	.long	3712504873
	.long	1072016135
	.long	88491949
	.long	1015427660
	.long	671025100
	.long	1072012296
	.long	3832014351
	.long	3163022030
	.long	3566716925
	.long	1072008466
	.long	1536826856
	.long	1014142433
	.long	3689071823
	.long	1072004647
	.long	2321004996
	.long	3162552716
	.long	917841882
	.long	1072000839
	.long	18715565
	.long	1015659308
	.long	3723038930
	.long	1071997040
	.long	378465264
	.long	3162569582
	.long	3395129871
	.long	1071993252
	.long	4025345435
	.long	3162335388
	.long	4109806887
	.long	1071989474
	.long	422403966
	.long	1014469229
	.long	1453150082
	.long	1071985707
	.long	498154669
	.long	3161488062
	.long	3896463087
	.long	1071981949
	.long	1139797873
	.long	3161233805
	.long	2731501122
	.long	1071978202
	.long	1774031855
	.long	3162470021
	.long	2135241198
	.long	1071974465
	.long	1236747871
	.long	1013589147
	.long	1990012071
	.long	1071970738
	.long	3529070563
	.long	3162813193
	.long	2178460671
	.long	1071967021
	.long	777878098
	.long	3162842493
	.long	2583551245
	.long	1071963314
	.long	3161094195
	.long	1015606491
	.long	3088564500
	.long	1071959617
	.long	1762311517
	.long	1015045673
	.long	3577096743
	.long	1071955930
	.long	2951496418
	.long	1013793687
	.long	3933059031
	.long	1071952253
	.long	2133366768
	.long	3161531832
	.long	4040676318
	.long	1071948586
	.long	4090609238
	.long	1015663458
	.long	3784486610
	.long	1071944929
	.long	1581883040
	.long	3161698953
	.long	3049340112
	.long	1071941282
	.long	3062915824
	.long	1013170595
	.long	1720398391
	.long	1071937645
	.long	3980678963
	.long	3163300080
	.long	3978100823
	.long	1071934017
	.long	3513027190
	.long	1015845963
	.long	1118294578
	.long	1071930400
	.long	2197495694
	.long	3159909401
	.long	1617004845
	.long	1071926792
	.long	82804944
	.long	1010342778
	.long	1065662932
	.long	1071923194
	.long	2533670915
	.long	1014530238
	.long	3645941911
	.long	1071919605
	.long	3814685081
	.long	3161573341
	.long	654919306
	.long	1071916027
	.long	3232961757
	.long	3163047469
	.long	569847338
	.long	1071912458
	.long	472945272
	.long	3159290729
	.long	3278348324
	.long	1071908898
	.long	3069497416
	.long	1014750712
	.long	78413852
	.long	1071905349
	.long	4183226867
	.long	3163017251
	.long	3743175029
	.long	1071901808
	.long	2072812490
	.long	3162175075
	.long	1276261410
	.long	1071898278
	.long	300981948
	.long	1014684169
	.long	1156440435
	.long	1071894757
	.long	2351451249
	.long	1013967056
	.long	3272845541
	.long	1071891245
	.long	928852419
	.long	3163488248
	.long	3219942644
	.long	1071887743
	.long	3798990616
	.long	1015368806
	.long	887463927
	.long	1071884251
	.long	3596744163
	.long	3160794166
	.long	460407023
	.long	1071880768
	.long	4237175092
	.long	3163138469
	.long	1829099622
	.long	1071877294
	.long	1016661181
	.long	3163461005
	.long	589198666
	.long	1071873830
	.long	2664346172
	.long	3163157962
	.long	926591435
	.long	1071870375
	.long	3208833762
	.long	3162913514
	.long	2732492859
	.long	1071866929
	.long	2691479646
	.long	3162255684
	.long	1603444721
	.long	1071863493
	.long	1548633640
	.long	3162201326
	.long	1726216749
	.long	1071860066
	.long	2466808228
	.long	3161676405
	.long	2992903935
	.long	1071856648
	.long	2218154406
	.long	1015228193
	.long	1000925746
	.long	1071853240
	.long	1018491672
	.long	3163309544
	.long	4232894513
	.long	1071849840
	.long	2383938684
	.long	1014668519
	.long	3991843581
	.long	1071846450
	.long	4092853457
	.long	1014585763
	.long	171030293
	.long	1071843070
	.long	3526460132
	.long	1014428778
	.long	1253935211
	.long	1071839698
	.long	1395382931
	.long	3159702613
	.long	2839424854
	.long	1071836335
	.long	1171596163
	.long	1013041679
	.long	526652809
	.long	1071832982
	.long	4223459736
	.long	1015879375
	.long	2799960843
	.long	1071829637
	.long	1423655381
	.long	1015022151
	.long	964107055
	.long	1071826302
	.long	2800439588
	.long	3162833221
	.long	3504003472
	.long	1071822975
	.long	3594001060
	.long	3157330652
	.long	1724976915
	.long	1071819658
	.long	420909223
	.long	3163117379
	.long	4112506593
	.long	1071816349
	.long	2947355221
	.long	1014371048
	.long	1972484976
	.long	1071813050
	.long	675290301
	.long	3161640050
	.long	3790955393
	.long	1071809759
	.long	2352942462
	.long	3163180090
	.long	874372905
	.long	1071806478
	.long	100263788
	.long	1015940732
	.long	1709341917
	.long	1071803205
	.long	2571168217
	.long	1014152499
	.long	1897844341
	.long	1071799941
	.long	1254300460
	.long	1015275938
	.long	1337108031
	.long	1071796686
	.long	3203724452
	.long	1014677845
	.long	4219606026
	.long	1071793439
	.long	2434574742
	.long	1014681548
	.long	1853186616
	.long	1071790202
	.long	3066496371
	.long	1015656574
	.long	2725843665
	.long	1071786973
	.long	1433917087
	.long	1014838523
	.long	2440944790
	.long	1071783753
	.long	2492769774
	.long	1014147454
	.long	897099801
	.long	1071780542
	.long	754756297
	.long	1015241005
	.long	2288159958
	.long	1071777339
	.long	2169144469
	.long	1014876021
	.long	2218315341
	.long	1071774145
	.long	2694295388
	.long	3163288868
	.long	586995997
	.long	1071770960
	.long	41662348
	.long	3162627992
	.long	1588871207
	.long	1071767783
	.long	143439582
	.long	3162963416
	.long	828946858
	.long	1071764615
	.long	10642492
	.long	1015939438
	.long	2502433899
	.long	1071761455
	.long	2148595913
	.long	1015023991
	.long	2214878420
	.long	1071758304
	.long	892270087
	.long	3163116422
	.long	4162030108
	.long	1071755161
	.long	2763428480
	.long	1015529349
	.long	3949972341
	.long	1071752027
	.long	2068408548
	.long	1014913868
	.long	1480023343
	.long	1071748902
	.long	2247196168
	.long	1015327453
	.long	948735466
	.long	1071745785
	.long	3516338028
	.long	3162574883
	.long	2257959872
	.long	1071742676
	.long	3802946148
	.long	1012964927
	.long	1014845819
	.long	1071739576
	.long	3117910646
	.long	3161559105
	.long	1416741826
	.long	1071736484
	.long	2196380210
	.long	1011413563
	.long	3366293073
	.long	1071733400
	.long	3119426314
	.long	1014120554
	.long	2471440686
	.long	1071730325
	.long	968836267
	.long	3162214888
	.long	2930322912
	.long	1071727258
	.long	2599499422
	.long	3162714047
	.long	351405227
	.long	1071724200
	.long	3125337328
	.long	3159822479
	.long	3228316108
	.long	1071721149
	.long	3010241991
	.long	3158422804
	.long	2875075254
	.long	1071718107
	.long	4144233330
	.long	3163333716
	.long	3490863953
	.long	1071715073
	.long	960797498
	.long	3162948880
	.long	685187902
	.long	1071712048
	.long	378731989
	.long	1014843115
	.long	2952712987
	.long	1071709030
	.long	3293494651
	.long	3160120301
	.long	1608493509
	.long	1071706021
	.long	3159622171
	.long	3162807737
	.long	852742562
	.long	1071703020
	.long	667253586
	.long	1009793559
	.long	590962156
	.long	1071700027
	.long	3829346666
	.long	3163275597
	.long	728909815
	.long	1071697042
	.long	383930225
	.long	1015029468
	.long	1172597893
	.long	1071694065
	.long	114433263
	.long	1015347593
	.long	1828292879
	.long	1071691096
	.long	1255956747
	.long	1015588398
	.long	2602514713
	.long	1071688135
	.long	2268929336
	.long	1014354284
	.long	3402036099
	.long	1071685182
	.long	405889334
	.long	1015105656
	.long	4133881824
	.long	1071682237
	.long	2148155345
	.long	3162931299
	.long	410360776
	.long	1071679301
	.long	1269990655
	.long	1011975870
	.long	728934454
	.long	1071676372
	.long	1413842688
	.long	1014178612
	.long	702412510
	.long	1071673451
	.long	3803266087
	.long	3162280415
	.long	238821257
	.long	1071670538
	.long	1469694871
	.long	3162884987
	.long	3541402996
	.long	1071667632
	.long	2759177317
	.long	1014854626
	.long	1928746161
	.long	1071664735
	.long	983617676
	.long	1014285177
	.long	3899555717
	.long	1071661845
	.long	427280750
	.long	3162546972
	.long	772914124
	.long	1071658964
	.long	4004372762
	.long	1012230161
	.long	1048019041
	.long	1071656090
	.long	1398474845
	.long	3160510595
	.long	339411585
	.long	1071653224
	.long	264588982
	.long	3161636657
	.long	2851812149
	.long	1071650365
	.long	2595802551
	.long	1015767337
	.long	4200250559
	.long	1071647514
	.long	2808127345
	.long	3161781938
	.long	0
	.long	1127743488
	.long	0
	.long	3275227136
	.long	1610612736
	.long	1082594631
	.long	4166901572
	.long	1055174155
	.long	3884607281
	.long	3168131199
	.long	3607404735
	.long	3190582024
	.long	1874480759
	.long	1032041131
	.long	4286760334
	.long	1053736893
	.long	4277811695
	.long	3211144770
	.long	0
	.long	0
	.long	236289503
	.long	1064135997
	.long	463583772
	.long	3215696314
	.long	1441186365
	.long	3212977891
	.long	286331153
	.long	1069617425
	.long	2284589306
	.long	1066820852
	.long	1431655765
	.long	3218429269
	.long	0
	.long	4294967280
	.long	0
	.long	4294967280
	.long	4294705152
	.long	4294967295
	.long	4294705152
	.long	4294967295
	.long	4160749568
	.long	2147483647
	.long	0
	.long	1072693248
	.long	0
	.long	1073741824
	.type	static_const_table,@object
	.size	static_const_table,4280
	.data
	.section .note.GNU-stack, ""
# End
