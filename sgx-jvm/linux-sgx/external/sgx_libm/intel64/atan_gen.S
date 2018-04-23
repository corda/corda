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
	.file "atan_gen.c"
	.text
..TXTST0:
# -- Begin  atan
	.text
       .align    16,0x90
	.globl atan
atan:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_atan.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        movsd     %xmm0, (%rsp)
..B1.2:
        movlpd    BMASK1(%rip), %xmm3
        xorpd     %xmm2, %xmm2
        movlpd    BMASK2(%rip), %xmm6
        movlpd    SGNMASK(%rip), %xmm7
        pextrw    $3, %xmm0, %edx
        movl      %edx, %eax
        andl      $32767, %edx
        movl      $16448, %ecx
        pinsrw    $3, %ecx, %xmm2
        andpd     %xmm7, %xmm0
        xorpd     %xmm7, %xmm7
        movq      %xmm0, %xmm1
        andpd     %xmm0, %xmm3
        pinsrw    $3, %ecx, %xmm7
        orpd      %xmm6, %xmm3
        movlpd    ONEMASK(%rip), %xmm5
        subl      $16288, %edx
        cmpl      $1119, %edx
        ja        .L_2TAG_PACKET_0.0.1
        cmpsd     $6, %xmm0, %xmm2
        minsd     %xmm7, %xmm3
        andpd     %xmm2, %xmm5
        andpd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm1
        subsd     %xmm3, %xmm0
        addsd     %xmm5, %xmm1
        divsd     %xmm1, %xmm0
        pextrw    $3, %xmm3, %edx
        subl      $16287, %edx
        movlpd    a2(%rip), %xmm2
        movlpd    b2(%rip), %xmm4
        andl      $32768, %eax
        pinsrw    $3, %eax, %xmm7
        addl      %edx, %edx
        lea       atan_tbl(%rip), %r8
        movlpd    (%r8,%rdx,8), %xmm6
        addl      $1, %edx
        movlpd    (%r8,%rdx,8), %xmm5
        xorpd     %xmm7, %xmm5
        xorpd     %xmm7, %xmm6
        movq      %xmm0, %xmm1
        xorpd     %xmm7, %xmm1
        movq      %xmm1, %xmm3
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm2
        addsd     %xmm0, %xmm4
        addsd     %xmm6, %xmm1
        subsd     %xmm1, %xmm6
        addsd     %xmm3, %xmm6
        addsd     8+a2(%rip), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm3, %xmm0
        addsd     %xmm5, %xmm6
        mulsd     %xmm2, %xmm0
        addsd     8+b2(%rip), %xmm4
        mulsd     %xmm4, %xmm0
        addsd     %xmm6, %xmm0
        addsd     %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_1.0.1
.L_2TAG_PACKET_0.0.1:
        addl      $944, %edx
        cmpl      $2063, %edx
        ja        .L_2TAG_PACKET_2.0.1
        movlpd    a2(%rip), %xmm4
        movlpd    b2(%rip), %xmm7
        movlpd    (%rsp), %xmm0
        mulsd     %xmm1, %xmm1
        movlpd    8+a2(%rip), %xmm2
        movlpd    8+b2(%rip), %xmm5
        mulsd     %xmm1, %xmm4
        addsd     %xmm1, %xmm7
        movq      %xmm1, %xmm6
        mulsd     %xmm0, %xmm1
        addsd     %xmm4, %xmm2
        mulsd     %xmm6, %xmm7
        mulsd     %xmm1, %xmm2
        addsd     %xmm5, %xmm7
        mulsd     %xmm7, %xmm2
        addsd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_1.0.1
.L_2TAG_PACKET_2.0.1:
        addl      $15344, %edx
        cmpl      $17392, %edx
        jae       .L_2TAG_PACKET_3.0.1
        movlpd    (%rsp), %xmm0
        movlpd    (%rsp), %xmm1
        cmpl      $16, %edx
        jae       .L_2TAG_PACKET_1.0.1
        mulsd     %xmm0, %xmm1
        jmp       .L_2TAG_PACKET_1.0.1
.L_2TAG_PACKET_3.0.1:
        movlpd    (%rsp), %xmm4
        movlpd    SGNMASK(%rip), %xmm0
        movlpd    pi_table(%rip), %xmm2
        movlpd    8+pi_table(%rip), %xmm3
        movd      %xmm1, %eax
        psrlq     $32, %xmm1
        movd      %xmm1, %edx
        cmpl      $2146435072, %edx
        jae       .L_2TAG_PACKET_4.0.1
.L_2TAG_PACKET_5.0.1:
        andnpd    %xmm4, %xmm0
        orpd      %xmm0, %xmm2
        orpd      %xmm3, %xmm0
        addsd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_1.0.1
.L_2TAG_PACKET_4.0.1:
        subl      $2146435072, %edx
        orl       %edx, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_5.0.1
        movq      %xmm4, %xmm0
        addsd     %xmm0, %xmm0
.L_2TAG_PACKET_1.0.1:
..B1.3:
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atan,@function
	.size	atan,.-atan
	.data
# -- End  atan
	.section .rodata, "a"
	.align 4
	.align 4
BMASK1:
	.long	0
	.long	4294901760
	.type	BMASK1,@object
	.size	BMASK1,8
	.align 4
BMASK2:
	.long	0
	.long	32768
	.type	BMASK2,@object
	.size	BMASK2,8
	.align 4
SGNMASK:
	.long	4294967295
	.long	2147483647
	.type	SGNMASK,@object
	.size	SGNMASK,8
	.align 4
ONEMASK:
	.long	0
	.long	1072693248
	.type	ONEMASK,@object
	.size	ONEMASK,8
	.align 4
a2:
	.long	2006262985
	.long	1069310863
	.long	2358449471
	.long	3217342131
	.type	a2,@object
	.size	a2,16
	.align 4
b2:
	.long	3845454352
	.long	1069952297
	.long	2829679149
	.long	1073771565
	.type	b2,@object
	.size	b2,16
	.align 4
atan_tbl:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3819695742
	.long	1067482761
	.long	2398680355
	.long	3155462074
	.long	2998791009
	.long	1067548225
	.long	3868465248
	.long	3157182472
	.long	3339424991
	.long	1067613680
	.long	3296670360
	.long	1010752543
	.long	2710002256
	.long	1067679126
	.long	3403896007
	.long	1010910768
	.long	3275701428
	.long	1067744562
	.long	119959933
	.long	1011482843
	.long	2908636881
	.long	1067809988
	.long	2464489612
	.long	1011545526
	.long	3777889398
	.long	1067875403
	.long	3262682165
	.long	1009703919
	.long	3759667419
	.long	1067940807
	.long	1838130851
	.long	3157373556
	.long	732369940
	.long	1068006200
	.long	1203428313
	.long	1010055371
	.long	1166616461
	.long	1068071580
	.long	2901274051
	.long	3158549977
	.long	2945472892
	.long	1068136947
	.long	3726120658
	.long	1009762715
	.long	3954480976
	.long	1068202301
	.long	1289173457
	.long	1009429861
	.long	2081752829
	.long	1068267642
	.long	1836909874
	.long	1006212095
	.long	3807999788
	.long	1068332968
	.long	2172459940
	.long	3156162078
	.long	2731789884
	.long	1068398280
	.long	3450718392
	.long	3159216547
	.long	1044477961
	.long	1068463577
	.long	2230553229
	.long	1011424339
	.long	1486930287
	.long	1068530218
	.long	2861547474
	.long	1012041376
	.long	2293016881
	.long	1068595466
	.long	136843272
	.long	1012684797
	.long	201518157
	.long	1068660680
	.long	63231984
	.long	1012427198
	.long	4054234584
	.long	1068725856
	.long	3927006960
	.long	1011878955
	.long	1246477213
	.long	1068790995
	.long	1494265652
	.long	3155219350
	.long	678186699
	.long	1068856093
	.long	1264361424
	.long	3159256693
	.long	2690594995
	.long	1068921148
	.long	3906996379
	.long	1009288267
	.long	3362611517
	.long	1068986159
	.long	1650970041
	.long	3158331771
	.long	3102162111
	.long	1069051124
	.long	365917035
	.long	3160264153
	.long	2352611067
	.long	1069116041
	.long	4008970190
	.long	3159478182
	.long	1594134794
	.long	1069180908
	.long	466690178
	.long	1012526501
	.long	1345079306
	.long	1069245723
	.long	2268273568
	.long	3160164092
	.long	2163300970
	.long	1069310484
	.long	2750834800
	.long	3158113482
	.long	352522716
	.long	1069375190
	.long	1750411372
	.long	1011790845
	.long	848541647
	.long	1069439838
	.long	2164207573
	.long	1011698350
	.long	40647312
	.long	1069504427
	.long	2949165434
	.long	3159107267
	.long	2216766270
	.long	1069574357
	.long	2197920765
	.long	3161055954
	.long	1090914384
	.long	1069638757
	.long	2330454674
	.long	1013365998
	.long	387601244
	.long	1069703022
	.long	3185681168
	.long	1013434071
	.long	3991640484
	.long	1069767144
	.long	1313211590
	.long	3161087959
	.long	3322489502
	.long	1069831118
	.long	3013977995
	.long	1013053011
	.long	3121698570
	.long	1069894936
	.long	4069015667
	.long	1013023362
	.long	4289964660
	.long	1069958591
	.long	1736191156
	.long	3158266731
	.long	3903312386
	.long	1070022077
	.long	1833592413
	.long	3159731471
	.long	3818449864
	.long	1070085387
	.long	851036429
	.long	3159730451
	.long	2097480306
	.long	1070148515
	.long	3506390884
	.long	3160462302
	.long	1611694502
	.long	1070211454
	.long	2785735540
	.long	3160465144
	.long	1464694796
	.long	1070274198
	.long	4229277299
	.long	3159907000
	.long	1299612775
	.long	1070336741
	.long	4116653788
	.long	3160427739
	.long	1310544789
	.long	1070399077
	.long	1064430331
	.long	1013218202
	.long	2253168030
	.long	1070461200
	.long	1405044609
	.long	3157623179
	.long	1159567373
	.long	1070523105
	.long	2353445521
	.long	3159992176
	.long	1359373750
	.long	1070605818
	.long	1748171336
	.long	3161879263
	.long	908341706
	.long	1070667034
	.long	3372710815
	.long	3161775245
	.long	1743027350
	.long	1070727765
	.long	687089934
	.long	3160507171
	.long	2055355646
	.long	1070787992
	.long	2392855242
	.long	1013682469
	.long	690426164
	.long	1070847697
	.long	1103926666
	.long	1014052810
	.long	1483247847
	.long	1070906862
	.long	2082645847
	.long	3161345479
	.long	392040270
	.long	1070965472
	.long	2407720023
	.long	1014053754
	.long	2673846014
	.long	1071023511
	.long	1293605532
	.long	3158464385
	.long	1384215810
	.long	1071080967
	.long	2446095872
	.long	3159216407
	.long	3101660631
	.long	1071137826
	.long	698040758
	.long	1014855328
	.long	2094057058
	.long	1071194078
	.long	2282048339
	.long	1014040385
	.long	1712750594
	.long	1071249712
	.long	1204372378
	.long	3162276464
	.long	1411515787
	.long	1071304719
	.long	949080808
	.long	1015006403
	.long	931538085
	.long	1071359091
	.long	3027127039
	.long	1014307233
	.long	179139065
	.long	1071412821
	.long	4285547492
	.long	3161934731
	.long	3387721259
	.long	1071465902
	.long	373225773
	.long	1013486625
	.long	2132236852
	.long	1071544299
	.long	3250533429
	.long	1014031677
	.long	1942070284
	.long	1071645596
	.long	1237964179
	.long	3163239113
	.long	1532707802
	.long	1071695380
	.long	330645583
	.long	1012495610
	.long	2294184979
	.long	1071743834
	.long	3959472897
	.long	1015833116
	.long	3805060714
	.long	1071790961
	.long	2671256142
	.long	1013727772
	.long	2215037898
	.long	1071836770
	.long	2683359117
	.long	1015831902
	.long	483661594
	.long	1071881273
	.long	836288326
	.long	3162648643
	.long	1534679894
	.long	1071924486
	.long	373258696
	.long	3162470096
	.long	1538714628
	.long	1071966430
	.long	3199433068
	.long	1015325501
	.long	527642555
	.long	1072007128
	.long	3636832592
	.long	3161843145
	.long	291339150
	.long	1072046605
	.long	890169537
	.long	3160586117
	.long	2450210201
	.long	1072084888
	.long	1636353294
	.long	3163193400
	.long	2411367951
	.long	1072122007
	.long	374899873
	.long	1011331750
	.long	681549971
	.long	1072157992
	.long	506411689
	.long	1015373954
	.long	1466745541
	.long	1072192873
	.long	2143860931
	.long	1013364334
	.long	2845622366
	.long	1072226682
	.long	2869178209
	.long	3162423682
	.long	2838871438
	.long	1072275456
	.long	3742223599
	.long	1014338577
	.long	4200275274
	.long	1072337034
	.long	1566539915
	.long	3161839550
	.long	3034733530
	.long	1072394897
	.long	652621408
	.long	3162261964
	.long	3207412993
	.long	1072449290
	.long	3206124665
	.long	1014408733
	.long	624461478
	.long	1072500450
	.long	932437485
	.long	1015204343
	.long	767665908
	.long	1072548600
	.long	1037911952
	.long	3163527627
	.long	1110773639
	.long	1072593952
	.long	2371517912
	.long	3160465741
	.long	1940828530
	.long	1072636704
	.long	2731408428
	.long	3162895795
	.long	1911329388
	.long	1072677041
	.long	1773089615
	.long	3159569267
	.long	1764715788
	.long	1072704191
	.long	691346949
	.long	3164069946
	.long	3332979233
	.long	1072722195
	.long	3550733983
	.long	1014770628
	.long	1321870254
	.long	1072739231
	.long	1415315820
	.long	1016224052
	.long	3657429030
	.long	1072755365
	.long	3910539033
	.long	1015966402
	.long	4197624557
	.long	1072770661
	.long	2333399254
	.long	3164546480
	.long	1512059493
	.long	1072785177
	.long	2701510318
	.long	1016178092
	.long	453379037
	.long	1072798965
	.long	4046344253
	.long	3162814364
	.long	1942345162
	.long	1072818388
	.long	621134147
	.long	1016335195
	.long	4210176273
	.long	1072842164
	.long	2701013387
	.long	3164326619
	.long	4185644010
	.long	1072863795
	.long	4163699341
	.long	1016203112
	.long	679688788
	.long	1072883543
	.long	4147276762
	.long	1014066750
	.long	29432865
	.long	1072901630
	.long	970415797
	.long	1016902063
	.long	4070721092
	.long	1072918247
	.long	2539004411
	.long	3163736096
	.long	2252468843
	.long	1072933561
	.long	3424082887
	.long	3163407177
	.long	2929724825
	.long	1072947712
	.long	3661482235
	.long	3163846989
	.long	1377513368
	.long	1072960824
	.long	3987926680
	.long	1013647908
	.long	1031632908
	.long	1072973003
	.long	3672217151
	.long	1016614619
	.long	2516508130
	.long	1072984342
	.long	545855020
	.long	3162728930
	.long	3792452178
	.long	1072994923
	.long	3420119467
	.long	1016471430
	.long	3147791459
	.long	1073004818
	.long	1342204979
	.long	1013937254
	.long	999189752
	.long	1073014090
	.long	1006335472
	.long	3162850919
	.long	711011011
	.long	1073022794
	.long	4633488
	.long	3162966895
	.long	15640363
	.long	1073030980
	.long	1686389560
	.long	3164376226
	.long	1218463589
	.long	1073042382
	.long	1526837110
	.long	3163533985
	.long	2538470555
	.long	1073056144
	.long	2273304406
	.long	3163784996
	.long	1229720947
	.long	1073068489
	.long	2971628206
	.long	3162356540
	.long	3115427016
	.long	1073079621
	.long	4215132957
	.long	3164282762
	.long	4030612557
	.long	1073089709
	.long	1913251691
	.long	3163671292
	.long	2728521257
	.long	1073098892
	.long	2861089500
	.long	1015454459
	.long	1118696283
	.long	1073107285
	.long	1628948053
	.long	1016179658
	.long	2682711255
	.long	1073114984
	.long	2906306266
	.long	1014142643
	.long	2073898081
	.long	1073122072
	.long	1322740454
	.long	3164497217
	.long	1403700297
	.long	1073128618
	.long	416137895
	.long	3162781466
	.long	2502685617
	.long	1073134681
	.long	3242008732
	.long	1014593495
	.long	1531926851
	.long	1073140313
	.long	1362708094
	.long	1016517604
	.long	3572814411
	.long	1073145557
	.long	3709790527
	.long	1012646874
	.long	1695536111
	.long	1073150453
	.long	3980346340
	.long	1016705136
	.long	2363057203
	.long	1073155033
	.long	2551194792
	.long	1012569695
	.long	2873365682
	.long	1073159327
	.long	3181154748
	.long	1017041450
	.long	1053384691
	.long	1073165288
	.long	3074536879
	.long	1016965660
	.long	3270542712
	.long	1073172451
	.long	2535319415
	.long	3163051778
	.long	1353631484
	.long	1073178850
	.long	1173833755
	.long	1015534537
	.long	3511218460
	.long	1073184599
	.long	1243608109
	.long	3161592122
	.long	4121259284
	.long	1073189793
	.long	398584912
	.long	3163829923
	.long	1193862106
	.long	1073194509
	.long	1873745539
	.long	3163802819
	.long	3861949790
	.long	1073198808
	.long	3841261147
	.long	1015587248
	.long	1486904578
	.long	1073202745
	.long	1634726776
	.long	3163847886
	.long	2879153715
	.long	1073206362
	.long	200456242
	.long	3164138657
	.long	385353253
	.long	1073209698
	.long	1186355517
	.long	1014887155
	.long	1125865839
	.long	1073212783
	.long	203561262
	.long	3161244927
	.long	1221361475
	.long	1073215645
	.long	3382476563
	.long	1014936138
	.long	2077323573
	.long	1073218307
	.long	1005121005
	.long	3164430752
	.long	215611373
	.long	1073220790
	.long	353198764
	.long	3164485137
	.long	2347419265
	.long	1073223110
	.long	1103143360
	.long	1016542137
	.long	1379112765
	.long	1073225284
	.long	381583533
	.long	3162870833
	.long	3891198463
	.long	1073228298
	.long	1771275754
	.long	1014654681
	.long	3395914051
	.long	1073231917
	.long	2350900914
	.long	3164013978
	.long	2799919478
	.long	1073235146
	.long	2893950164
	.long	3163260901
	.long	1138673476
	.long	1073238045
	.long	2622204785
	.long	3164174388
	.long	3408855940
	.long	1073240661
	.long	2800881650
	.long	1016008624
	.long	2044858738
	.long	1073243035
	.long	604544785
	.long	1017022901
	.long	2578795176
	.long	1073245198
	.long	2557332925
	.long	1016135165
	.long	4196285314
	.long	1073247177
	.long	2032365307
	.long	1016194735
	.long	224877747
	.long	1073248996
	.long	497926916
	.long	1016947111
	.long	3271386490
	.long	1073250671
	.long	2689994846
	.long	1016631513
	.long	813635989
	.long	1073252221
	.long	747035277
	.long	3164530136
	.long	369829519
	.long	1073253658
	.long	2182033858
	.long	3163190340
	.long	1187679052
	.long	1073254994
	.long	673954443
	.long	1016149821
	.long	4232586098
	.long	1073256239
	.long	497775200
	.long	3162179015
	.long	426690558
	.long	1073257404
	.long	3063343247
	.long	1016865578
	.long	1624065902
	.long	1073258494
	.long	1354224996
	.long	3163503778
	.long	1413754136
	.long	1073291771
	.long	856972295
	.long	1016178214
	.type	atan_tbl,@object
	.size	atan_tbl,2592
	.align 4
pi_table:
	.long	1413754136
	.long	1073291771
	.long	856972295
	.long	1016178214
	.type	pi_table,@object
	.size	pi_table,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
