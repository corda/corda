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
	.file "log10_gen.c"
	.text
..TXTST0:
# -- Begin  log10
	.text
       .align    16,0x90
	.globl log10
log10:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log10.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        xorpd     %xmm2, %xmm2
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm2
        movl      $1054736384, %ecx
        movd      %ecx, %xmm7
        xorpd     %xmm3, %xmm3
        movl      $30704, %edx
        pinsrw    $3, %edx, %xmm3
        movq      %xmm0, %xmm1
        movl      $32768, %edx
        movd      %edx, %xmm4
        movapd    HIGHSIGMASK(%rip), %xmm5
        pextrw    $3, %xmm0, %eax
        orpd      %xmm2, %xmm0
        movl      $16352, %ecx
        psrlq     $27, %xmm0
        movq      LOG10_E(%rip), %xmm2
        psrld     $2, %xmm0
        rcpps     %xmm0, %xmm0
        psllq     $12, %xmm1
        pshufd    $78, %xmm5, %xmm6
        psrlq     $12, %xmm1
        subl      $16, %eax
        cmpl      $32736, %eax
        jae       .L_2TAG_PACKET_0.0.2
.L_2TAG_PACKET_1.0.2:
        mulss     %xmm7, %xmm0
        orpd      %xmm3, %xmm1
        lea       L_tbl(%rip), %r11
        andpd     %xmm1, %xmm5
        paddd     %xmm4, %xmm0
        subsd     %xmm5, %xmm1
        movd      %xmm0, %edx
        psllq     $29, %xmm0
        andpd     %xmm6, %xmm0
        andl      $32752, %eax
        subl      %ecx, %eax
        cvtsi2sd  %eax, %xmm7
        mulpd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm1
        movq      log2(%rip), %xmm6
        movapd    coeff(%rip), %xmm3
        subsd     %xmm2, %xmm5
        andl      $16711680, %edx
        shrl      $12, %edx
        movapd    -1504(%r11,%rdx), %xmm0
        movapd    16+coeff(%rip), %xmm4
        addsd     %xmm5, %xmm1
        movapd    32+coeff(%rip), %xmm2
        mulsd     %xmm7, %xmm6
        pshufd    $68, %xmm1, %xmm5
        mulsd     8+log2(%rip), %xmm7
        mulsd     %xmm1, %xmm3
        addsd     %xmm6, %xmm0
        mulpd     %xmm5, %xmm4
        movq      8+LOG10_E(%rip), %xmm6
        mulpd     %xmm5, %xmm5
        addpd     %xmm2, %xmm4
        mulpd     %xmm5, %xmm3
        pshufd    $228, %xmm0, %xmm2
        addsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm4
        subsd     %xmm0, %xmm2
        mulsd     %xmm1, %xmm6
        addsd     %xmm2, %xmm1
        pshufd    $238, %xmm0, %xmm2
        mulsd     %xmm5, %xmm5
        addsd     %xmm2, %xmm7
        addsd     %xmm6, %xmm1
        addpd     %xmm3, %xmm4
        addsd     %xmm7, %xmm1
        mulpd     %xmm5, %xmm4
        addsd     %xmm4, %xmm1
        pshufd    $238, %xmm4, %xmm5
        addsd     %xmm5, %xmm1
        addsd     %xmm1, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
        movq      (%rsp), %xmm0
        movq      (%rsp), %xmm1
        addl      $16, %eax
        cmpl      $32768, %eax
        jae       .L_2TAG_PACKET_2.0.2
        cmpl      $16, %eax
        jb        .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_4.0.2:
        addsd     %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_5.0.2:
        ja        .L_2TAG_PACKET_4.0.2
        cmpl      $0, %edx
        ja        .L_2TAG_PACKET_4.0.2
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_3.0.2:
        xorpd     %xmm1, %xmm1
        addsd     %xmm0, %xmm1
        movd      %xmm1, %edx
        psrlq     $32, %xmm1
        movd      %xmm1, %ecx
        orl       %ecx, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_7.0.2
        xorpd     %xmm1, %xmm1
        movl      $18416, %eax
        pinsrw    $3, %eax, %xmm1
        mulsd     %xmm1, %xmm0
        xorpd     %xmm2, %xmm2
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm2
        movq      %xmm0, %xmm1
        pextrw    $3, %xmm0, %eax
        orpd      %xmm2, %xmm0
        movl      $18416, %ecx
        psrlq     $27, %xmm0
        movq      LOG10_E(%rip), %xmm2
        psrld     $2, %xmm0
        rcpps     %xmm0, %xmm0
        psllq     $12, %xmm1
        pshufd    $78, %xmm5, %xmm6
        psrlq     $12, %xmm1
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_2.0.2:
        movd      %xmm1, %edx
        psrlq     $32, %xmm1
        movd      %xmm1, %ecx
        addl      %ecx, %ecx
        cmpl      $-2097152, %ecx
        jae       .L_2TAG_PACKET_5.0.2
        orl       %ecx, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_7.0.2
.L_2TAG_PACKET_6.0.2:
        xorpd     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        movl      $32752, %eax
        pinsrw    $3, %eax, %xmm1
        mulsd     %xmm1, %xmm0
        movl      $9, 16(%rsp)
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_7.0.2:
        xorpd     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        movl      $49136, %eax
        pinsrw    $3, %eax, %xmm0
        divsd     %xmm1, %xmm0
        movl      $8, 16(%rsp)
.L_2TAG_PACKET_8.0.2:
        movq      %xmm0, 8(%rsp)
..B1.3:
        movq      8(%rsp), %xmm0
.L_2TAG_PACKET_9.0.2:
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	log10,@function
	.size	log10,.-log10
	.data
# -- End  log10
	.section .rodata, "a"
	.align 16
	.align 16
HIGHSIGMASK:
	.long	4160749568
	.long	4294967295
	.long	0
	.long	4294959104
	.type	HIGHSIGMASK,@object
	.size	HIGHSIGMASK,16
	.align 16
LOG10_E:
	.long	0
	.long	1071366144
	.long	3207479560
	.long	1062894188
	.type	LOG10_E,@object
	.size	LOG10_E,16
	.align 16
L_tbl:
	.long	1352628224
	.long	1070810131
	.long	521319256
	.long	1025503025
	.long	2150839296
	.long	1070801944
	.long	3329350096
	.long	3170190015
	.long	1360613376
	.long	1070793794
	.long	2024059075
	.long	1024991594
	.long	1875350528
	.long	1070785680
	.long	2163882141
	.long	3163564137
	.long	2312126464
	.long	1070777602
	.long	1975711076
	.long	1023674196
	.long	1306336256
	.long	1070769560
	.long	3524899523
	.long	3170508164
	.long	1806334976
	.long	1070761553
	.long	4254777025
	.long	1025238739
	.long	2483193856
	.long	1070753581
	.long	3800671317
	.long	3172916830
	.long	2025350144
	.long	1070745644
	.long	1731514745
	.long	1025501083
	.long	3433285632
	.long	1070737741
	.long	2551857336
	.long	3169662186
	.long	1134317568
	.long	1070729873
	.long	3426297655
	.long	3172637891
	.long	2457152512
	.long	1070722038
	.long	63549415
	.long	1025415416
	.long	1861803008
	.long	1070714237
	.long	1910171636
	.long	1023977580
	.long	2414140416
	.long	1070706469
	.long	4002514337
	.long	3170841618
	.long	2900726784
	.long	1070698734
	.long	3268064083
	.long	1022459609
	.long	2123517952
	.long	1070691032
	.long	1767031218
	.long	1022448156
	.long	3194569728
	.long	1070683362
	.long	3402332618
	.long	3171671160
	.long	650882048
	.long	1070675725
	.long	4146023905
	.long	3171023038
	.long	1928988672
	.long	1070668119
	.long	1438617867
	.long	1016360491
	.long	1594908672
	.long	1070660545
	.long	971389377
	.long	1024763979
	.long	2818746368
	.long	1070653002
	.long	3555925341
	.long	3172434821
	.long	194584576
	.long	1070645491
	.long	943919215
	.long	3172950063
	.long	1215096832
	.long	1070638010
	.long	2283358588
	.long	1022335098
	.long	501519360
	.long	1070630560
	.long	480904295
	.long	1024437959
	.long	1278266368
	.long	1070623140
	.long	2755806066
	.long	3172342012
	.long	2487812096
	.long	1070615750
	.long	2489653202
	.long	3172481099
	.long	3085451264
	.long	1070608390
	.long	3759184951
	.long	3172574892
	.long	2039090176
	.long	1070601060
	.long	1361176676
	.long	3172355319
	.long	953057280
	.long	1070591423
	.long	1176587546
	.long	3166422018
	.long	3370524672
	.long	1070576879
	.long	3669570051
	.long	1025376630
	.long	749742080
	.long	1070562394
	.long	707700964
	.long	3170814058
	.long	4008353792
	.long	1070547965
	.long	3247327652
	.long	1022431400
	.long	2612455424
	.long	1070533594
	.long	2453457344
	.long	3172322969
	.long	3230920704
	.long	1070519279
	.long	1296781801
	.long	1025115335
	.long	3965253632
	.long	1070505020
	.long	373075289
	.long	1017938528
	.long	2593157120
	.long	1070476669
	.long	1068054086
	.long	1021616576
	.long	925962240
	.long	1070448537
	.long	850121213
	.long	1023928989
	.long	1732556800
	.long	1070420620
	.long	1305206740
	.long	3172665570
	.long	3815630848
	.long	1070392915
	.long	192642943
	.long	3172699907
	.long	2001758208
	.long	1070365420
	.long	2820786683
	.long	1024704867
	.long	16746496
	.long	1070338131
	.long	1399573110
	.long	3171372773
	.long	1886492672
	.long	1070311044
	.long	3621428075
	.long	3172974358
	.long	3338196992
	.long	1070284157
	.long	3793882035
	.long	1025124701
	.long	381769728
	.long	1070257468
	.long	3877933342
	.long	3170195490
	.long	2186491904
	.long	1070230972
	.long	1838687089
	.long	1017927292
	.long	1008330752
	.long	1070204668
	.long	2228321664
	.long	1025352196
	.long	2247065600
	.long	1070178552
	.long	1413900906
	.long	3170902532
	.long	2964070400
	.long	1070152622
	.long	3590454629
	.long	1025016844
	.long	465154048
	.long	1070126876
	.long	2079688550
	.long	3172268183
	.long	883615744
	.long	1070101310
	.long	989244452
	.long	3171900485
	.long	1993768960
	.long	1070075922
	.long	1124327841
	.long	3172964992
	.long	1794471936
	.long	1070050710
	.long	1140575046
	.long	1022673726
	.long	2797932544
	.long	1070025671
	.long	1894836933
	.long	3172544059
	.long	3433797632
	.long	1070000803
	.long	3221831166
	.long	3171921685
	.long	2338371584
	.long	1069976104
	.long	3732461053
	.long	3164513518
	.long	2644013056
	.long	1069951571
	.long	2519460462
	.long	3172548740
	.long	3383814144
	.long	1069927202
	.long	2290997657
	.long	1025499649
	.long	3781380096
	.long	1069902995
	.long	380479405
	.long	1025184136
	.long	3245785088
	.long	1069878948
	.long	1096398261
	.long	3169885192
	.long	1366712320
	.long	1069855059
	.long	2218343715
	.long	3170281628
	.long	2204717056
	.long	1069831325
	.long	2668334011
	.long	1025264524
	.long	1401772032
	.long	1069807745
	.long	4103993159
	.long	1022925721
	.long	3356721152
	.long	1069784316
	.long	3573790772
	.long	3172186527
	.long	4041148416
	.long	1069761037
	.long	4027691910
	.long	3171276990
	.long	3880151040
	.long	1069737906
	.long	4087118786
	.long	3172710734
	.long	3453364224
	.long	1069714921
	.long	99014299
	.long	3172003077
	.long	3491092480
	.long	1069692080
	.long	3801836701
	.long	3172989287
	.long	575580160
	.long	1069669382
	.long	1920406012
	.long	3170874125
	.long	22282240
	.long	1069646824
	.long	964193370
	.long	1019363159
	.long	2991429632
	.long	1069624404
	.long	3372589890
	.long	1023425053
	.long	2189645824
	.long	1069602122
	.long	2610503872
	.long	1023652442
	.long	3341467648
	.long	1069579975
	.long	1190292004
	.long	1022425665
	.long	3711293440
	.long	1069557962
	.long	1104795356
	.long	1023625829
	.long	1380401152
	.long	1069524644
	.long	1156998217
	.long	1025100499
	.long	765710336
	.long	1069481144
	.long	1736649113
	.long	1024999439
	.long	849412096
	.long	1069437902
	.long	2618178330
	.long	3170853629
	.long	1433104384
	.long	1069394915
	.long	43477267
	.long	3170378811
	.long	2548596736
	.long	1069352180
	.long	3967367063
	.long	1025246584
	.long	157577216
	.long	1069309695
	.long	100402533
	.long	3172825502
	.long	3326238720
	.long	1069267455
	.long	1176892909
	.long	1025464099
	.long	4155494400
	.long	1069225459
	.long	3713707617
	.long	3172630046
	.long	3545804800
	.long	1069183704
	.long	857007315
	.long	1024965777
	.long	2602520576
	.long	1069142187
	.long	2588758347
	.long	1022463131
	.long	2631196672
	.long	1069100905
	.long	2118424235
	.long	1022490989
	.long	838135808
	.long	1069059856
	.long	4117002727
	.long	1024874520
	.long	3210903552
	.long	1069019036
	.long	650070125
	.long	3172012966
	.long	3039211520
	.long	1068978444
	.long	438055812
	.long	1017743757
	.long	2385633280
	.long	1068938077
	.long	3011990369
	.long	3171312044
	.long	3491618816
	.long	1068897932
	.long	712813818
	.long	3172720400
	.long	183644160
	.long	1068858008
	.long	4287006742
	.long	1022379728
	.long	3639214080
	.long	1068818300
	.long	353762279
	.long	3172980009
	.long	3728416768
	.long	1068778808
	.long	1851367730
	.long	1025486574
	.long	3370094592
	.long	1068739529
	.long	4046594913
	.long	3172567047
	.long	1348407296
	.long	1068700461
	.long	143189675
	.long	1025397632
	.long	899403776
	.long	1068661601
	.long	3753687842
	.long	3170772772
	.long	1117708288
	.long	1068622947
	.long	1857340812
	.long	3170782678
	.long	1248276480
	.long	1068584497
	.long	1289858203
	.long	1025222289
	.long	683237376
	.long	1068546249
	.long	2356679608
	.long	3171629170
	.long	3253764096
	.long	1068508200
	.long	3267136556
	.long	1018554987
	.long	94478336
	.long	1068441756
	.long	1927868814
	.long	3169378180
	.long	3233144832
	.long	1068366445
	.long	2682188854
	.long	1023964004
	.long	2940297216
	.long	1068291522
	.long	275301289
	.long	1023944679
	.long	3677708288
	.long	1068216982
	.long	302658771
	.long	1024465567
	.long	1576968192
	.long	1068142822
	.long	3672035940
	.long	3172254610
	.long	1614069760
	.long	1068069037
	.long	480052905
	.long	3172692062
	.long	424435712
	.long	1067995624
	.long	2207869657
	.long	3170965436
	.long	3477782528
	.long	1067922578
	.long	2980661858
	.long	3164990018
	.long	3598401536
	.long	1067849897
	.long	1974393034
	.long	3171357083
	.long	2435235840
	.long	1067777577
	.long	1385289011
	.long	1024615823
	.long	1867333632
	.long	1067705614
	.long	3442236633
	.long	1025334384
	.long	3999301632
	.long	1067634004
	.long	3506472073
	.long	1025132546
	.long	2566971392
	.long	1067562745
	.long	1425757592
	.long	3172358463
	.long	112943104
	.long	1067491833
	.long	1693407156
	.long	3172426603
	.long	3079929856
	.long	1067392159
	.long	3999942455
	.long	1018549369
	.long	2443837440
	.long	1067251701
	.long	974534460
	.long	1023963412
	.long	359366656
	.long	1067111917
	.long	2204915018
	.long	1013514416
	.long	3564519424
	.long	1066972799
	.long	3977441659
	.long	3170879860
	.long	2011086848
	.long	1066834343
	.long	590145514
	.long	1025390011
	.long	3216982016
	.long	1066696541
	.long	3629120110
	.long	1024330313
	.long	2194128896
	.long	1066559388
	.long	2367098512
	.long	3172260338
	.long	2916220928
	.long	1066422877
	.long	2262431886
	.long	1021229446
	.long	2263941120
	.long	1066172214
	.long	3118507287
	.long	1021484970
	.long	3076292608
	.long	1065901726
	.long	1411737803
	.long	3172957147
	.long	1186136064
	.long	1065632488
	.long	3109349337
	.long	1025397383
	.long	3085303808
	.long	1065364487
	.long	584715031
	.long	3172596519
	.long	1821048832
	.long	1064842211
	.long	2182246895
	.long	3172536214
	.long	697368576
	.long	1064311094
	.long	3157561765
	.long	3172716357
	.long	894042112
	.long	1063260131
	.long	3237958154
	.long	3172587292
	.long	0
	.long	0
	.long	0
	.long	0
	.type	L_tbl,@object
	.size	L_tbl,2064
	.align 16
log2:
	.long	1352628224
	.long	1066615827
	.long	521319256
	.long	1021308721
	.type	log2,@object
	.size	log2,16
	.align 16
coeff:
	.long	3248877870
	.long	1077250164
	.long	1691676429
	.long	3221787401
	.long	945132465
	.long	3223701783
	.long	3700831335
	.long	1073506818
	.long	2141010593
	.long	1075227551
	.long	3698831637
	.long	3220339442
	.type	coeff,@object
	.size	coeff,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
