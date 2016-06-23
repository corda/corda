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
	.file "cosh_wmt.c"
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
# -- Begin  cosh
	.text
       .align    16,0x90
	.globl cosh
cosh:
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
        movsd     4240(%ebx), %xmm3
        xorpd     %xmm4, %xmm4
        movsd     4192(%ebx), %xmm1
        movsd     4200(%ebx), %xmm2
        movl      $32768, %eax
        pinsrw    $3, %eax, %xmm4
        movsd     4096(%ebx), %xmm6
        pextrw    $3, %xmm0, %ecx
        andpd     %xmm0, %xmm3
        andnpd    %xmm0, %xmm4
        pshufd    $68, %xmm4, %xmm5
        andl      $32767, %ecx
        subl      $16320, %ecx
        cmpl      $200, %ecx
        jae       .L_2TAG_PACKET_0.0.3
        subsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm3
        mulsd     %xmm5, %xmm2
        cvtsd2si  %xmm3, %eax
        movapd    %xmm3, %xmm7
        addsd     %xmm6, %xmm3
        mulsd     %xmm4, %xmm1
        xorpd     %xmm5, %xmm5
        subsd     %xmm6, %xmm3
        movapd    4112(%ebx), %xmm4
        addsd     %xmm1, %xmm2
        movapd    4128(%ebx), %xmm6
        subsd     %xmm3, %xmm7
        movl      $32704, %edx
        pinsrw    $3, %edx, %xmm5
        movapd    4144(%ebx), %xmm1
        addsd     %xmm7, %xmm2
        movl      $127, %edx
        andl      %eax, %edx
        addl      %edx, %edx
        shrl      $3, %eax
        andl      $65520, %eax
        addl      $16352, %eax
        xorpd     %xmm0, %xmm0
        cmpl      $184, %ecx
        jae       .L_2TAG_PACKET_1.0.3
        pshufd    $68, %xmm5, %xmm5
        pinsrw    $3, %eax, %xmm0
        pshufd    $68, %xmm0, %xmm0
        psubw     %xmm0, %xmm5
        mulpd     (%ebx,%edx,8), %xmm0
        mulpd     2048(%ebx,%edx,8), %xmm5
        pshufd    $68, %xmm2, %xmm3
        movapd    4160(%ebx), %xmm7
        pshufd    $68, %xmm2, %xmm2
        mulpd     %xmm3, %xmm3
        mulpd     %xmm2, %xmm4
        mulpd     %xmm2, %xmm6
        mulpd     4176(%ebx), %xmm2
        mulpd     %xmm3, %xmm1
        mulpd     %xmm3, %xmm7
        mulpd     %xmm3, %xmm4
        mulpd     %xmm3, %xmm1
        addpd     %xmm7, %xmm6
        movapd    %xmm0, %xmm7
        addpd     %xmm1, %xmm4
        shufpd    $0, %xmm5, %xmm7
        addpd     %xmm5, %xmm0
        mulpd     %xmm7, %xmm2
        addpd     %xmm6, %xmm4
        subsd     %xmm0, %xmm7
        mulpd     %xmm2, %xmm4
        pshufd    $238, %xmm0, %xmm6
        addsd     %xmm5, %xmm7
        addpd     %xmm2, %xmm4
        addsd     %xmm6, %xmm7
        pshufd    $238, %xmm4, %xmm2
        addsd     %xmm7, %xmm2
        addsd     %xmm4, %xmm2
        addsd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        addl      $16320, %ecx
        cmpl      $16320, %ecx
        ja        .L_2TAG_PACKET_3.0.3
        cmpl      $15952, %ecx
        jae       .L_2TAG_PACKET_4.0.3
        addsd     %xmm2, %xmm6
        movsd     4248(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        subl      $16352, %eax
        movl      %eax, %ecx
        andl      $32752, %eax
        shrl      $1, %eax
        andl      $65520, %eax
        subl      %eax, %ecx
        addl      $16352, %eax
        pinsrw    $3, %eax, %xmm0
        pshufd    $68, %xmm0, %xmm0
        mulpd     (%ebx,%edx,8), %xmm0
        pshufd    $68, %xmm2, %xmm3
        movsd     4160(%ebx), %xmm7
        mulsd     %xmm3, %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm6
        mulsd     4176(%ebx), %xmm2
        mulsd     %xmm3, %xmm1
        mulsd     %xmm3, %xmm7
        mulsd     %xmm3, %xmm4
        addl      $16368, %ecx
        pinsrw    $3, %ecx, %xmm5
        mulsd     %xmm3, %xmm1
        addsd     %xmm7, %xmm6
        addsd     %xmm1, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     %xmm6, %xmm4
        mulsd     %xmm2, %xmm4
        pshufd    $238, %xmm0, %xmm6
        addsd     %xmm6, %xmm4
        addsd     %xmm4, %xmm2
        addsd     %xmm2, %xmm0
        mulsd     %xmm5, %xmm0
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        movl      $64, %edx
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_5.0.3
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_4.0.3:
        movapd    4208(%ebx), %xmm1
        mulpd     %xmm5, %xmm5
        movapd    4224(%ebx), %xmm2
        xorpd     %xmm3, %xmm3
        movapd    %xmm5, %xmm0
        mulpd     %xmm5, %xmm1
        movsd     4248(%ebx), %xmm6
        mulpd     %xmm5, %xmm5
        movl      $16352, %eax
        pinsrw    $3, %eax, %xmm3
        addpd     %xmm2, %xmm1
        mulpd     %xmm5, %xmm1
        pshufd    $238, %xmm1, %xmm2
        mulsd     %xmm1, %xmm5
        mulsd     %xmm3, %xmm0
        addsd     %xmm5, %xmm2
        addsd     %xmm2, %xmm0
        addsd     %xmm6, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        cmpl      $32752, %ecx
        jae       .L_2TAG_PACKET_6.0.3
        xorpd     %xmm0, %xmm0
        movl      $32736, %eax
        pinsrw    $3, %eax, %xmm0
        mulsd     %xmm0, %xmm0
        movl      $64, %edx
.L_2TAG_PACKET_5.0.3:
        movsd     %xmm0, (%esp)
        movsd     112(%esp), %xmm0
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
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_6.0.3:
        mulsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_2.0.3:
        movsd     %xmm0, 24(%esp)
        fldl      24(%esp)
.L_2TAG_PACKET_7.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	cosh,@function
	.size	cosh,.-cosh
	.data
# -- End  cosh
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
	.long	0
	.long	0
	.long	2851812149
	.long	1072698941
	.long	2595802551
	.long	1016815913
	.long	1048019041
	.long	1072704666
	.long	1398474845
	.long	3161559171
	.long	3899555717
	.long	1072710421
	.long	427280750
	.long	3163595548
	.long	3541402996
	.long	1072716208
	.long	2759177317
	.long	1015903202
	.long	702412510
	.long	1072722027
	.long	3803266087
	.long	3163328991
	.long	410360776
	.long	1072727877
	.long	1269990655
	.long	1013024446
	.long	3402036099
	.long	1072733758
	.long	405889334
	.long	1016154232
	.long	1828292879
	.long	1072739672
	.long	1255956747
	.long	1016636974
	.long	728909815
	.long	1072745618
	.long	383930225
	.long	1016078044
	.long	852742562
	.long	1072751596
	.long	667253586
	.long	1010842135
	.long	2952712987
	.long	1072757606
	.long	3293494651
	.long	3161168877
	.long	3490863953
	.long	1072763649
	.long	960797498
	.long	3163997456
	.long	3228316108
	.long	1072769725
	.long	3010241991
	.long	3159471380
	.long	2930322912
	.long	1072775834
	.long	2599499422
	.long	3163762623
	.long	3366293073
	.long	1072781976
	.long	3119426314
	.long	1015169130
	.long	1014845819
	.long	1072788152
	.long	3117910646
	.long	3162607681
	.long	948735466
	.long	1072794361
	.long	3516338028
	.long	3163623459
	.long	3949972341
	.long	1072800603
	.long	2068408548
	.long	1015962444
	.long	2214878420
	.long	1072806880
	.long	892270087
	.long	3164164998
	.long	828946858
	.long	1072813191
	.long	10642492
	.long	1016988014
	.long	586995997
	.long	1072819536
	.long	41662348
	.long	3163676568
	.long	2288159958
	.long	1072825915
	.long	2169144469
	.long	1015924597
	.long	2440944790
	.long	1072832329
	.long	2492769774
	.long	1015196030
	.long	1853186616
	.long	1072838778
	.long	3066496371
	.long	1016705150
	.long	1337108031
	.long	1072845262
	.long	3203724452
	.long	1015726421
	.long	1709341917
	.long	1072851781
	.long	2571168217
	.long	1015201075
	.long	3790955393
	.long	1072858335
	.long	2352942462
	.long	3164228666
	.long	4112506593
	.long	1072864925
	.long	2947355221
	.long	1015419624
	.long	3504003472
	.long	1072871551
	.long	3594001060
	.long	3158379228
	.long	2799960843
	.long	1072878213
	.long	1423655381
	.long	1016070727
	.long	2839424854
	.long	1072884911
	.long	1171596163
	.long	1014090255
	.long	171030293
	.long	1072891646
	.long	3526460132
	.long	1015477354
	.long	4232894513
	.long	1072898416
	.long	2383938684
	.long	1015717095
	.long	2992903935
	.long	1072905224
	.long	2218154406
	.long	1016276769
	.long	1603444721
	.long	1072912069
	.long	1548633640
	.long	3163249902
	.long	926591435
	.long	1072918951
	.long	3208833762
	.long	3163962090
	.long	1829099622
	.long	1072925870
	.long	1016661181
	.long	3164509581
	.long	887463927
	.long	1072932827
	.long	3596744163
	.long	3161842742
	.long	3272845541
	.long	1072939821
	.long	928852419
	.long	3164536824
	.long	1276261410
	.long	1072946854
	.long	300981948
	.long	1015732745
	.long	78413852
	.long	1072953925
	.long	4183226867
	.long	3164065827
	.long	569847338
	.long	1072961034
	.long	472945272
	.long	3160339305
	.long	3645941911
	.long	1072968181
	.long	3814685081
	.long	3162621917
	.long	1617004845
	.long	1072975368
	.long	82804944
	.long	1011391354
	.long	3978100823
	.long	1072982593
	.long	3513027190
	.long	1016894539
	.long	3049340112
	.long	1072989858
	.long	3062915824
	.long	1014219171
	.long	4040676318
	.long	1072997162
	.long	4090609238
	.long	1016712034
	.long	3577096743
	.long	1073004506
	.long	2951496418
	.long	1014842263
	.long	2583551245
	.long	1073011890
	.long	3161094195
	.long	1016655067
	.long	1990012071
	.long	1073019314
	.long	3529070563
	.long	3163861769
	.long	2731501122
	.long	1073026778
	.long	1774031855
	.long	3163518597
	.long	1453150082
	.long	1073034283
	.long	498154669
	.long	3162536638
	.long	3395129871
	.long	1073041828
	.long	4025345435
	.long	3163383964
	.long	917841882
	.long	1073049415
	.long	18715565
	.long	1016707884
	.long	3566716925
	.long	1073057042
	.long	1536826856
	.long	1015191009
	.long	3712504873
	.long	1073064711
	.long	88491949
	.long	1016476236
	.long	2321106615
	.long	1073072422
	.long	2171176610
	.long	1010584347
	.long	363667784
	.long	1073080175
	.long	813753950
	.long	1016833785
	.long	3111574537
	.long	1073087969
	.long	2606161479
	.long	3163808322
	.long	2956612997
	.long	1073095806
	.long	2118169751
	.long	3163784129
	.long	885834528
	.long	1073103686
	.long	1973258547
	.long	3163310140
	.long	2186617381
	.long	1073111608
	.long	2270764084
	.long	3164321289
	.long	3561793907
	.long	1073119573
	.long	1157054053
	.long	1012938926
	.long	1719614413
	.long	1073127582
	.long	330458198
	.long	3164331316
	.long	1963711167
	.long	1073135634
	.long	1744767757
	.long	3161622870
	.long	1013258799
	.long	1073143730
	.long	1748797611
	.long	3161177658
	.long	4182873220
	.long	1073151869
	.long	629542646
	.long	3163044879
	.long	3907805044
	.long	1073160053
	.long	2257091225
	.long	3162598983
	.long	1218806132
	.long	1073168282
	.long	1818613052
	.long	3163597017
	.long	1447192521
	.long	1073176555
	.long	1462857171
	.long	3163563097
	.long	1339972927
	.long	1073184873
	.long	167908909
	.long	1016620728
	.long	1944781191
	.long	1073193236
	.long	3993278767
	.long	3162772855
	.long	19972402
	.long	1073201645
	.long	3507899862
	.long	1017057868
	.long	919555682
	.long	1073210099
	.long	3121969534
	.long	1013996802
	.long	1413356050
	.long	1073218599
	.long	1651349291
	.long	3163716742
	.long	2571947539
	.long	1073227145
	.long	3558159064
	.long	3164425245
	.long	1176749997
	.long	1073235738
	.long	2738998779
	.long	3163084420
	.long	2604962541
	.long	1073244377
	.long	2614425274
	.long	3164587768
	.long	3649726105
	.long	1073253063
	.long	4085036346
	.long	1016698050
	.long	1110089947
	.long	1073261797
	.long	1451641639
	.long	1016523249
	.long	380978316
	.long	1073270578
	.long	854188970
	.long	3161511262
	.long	2568320822
	.long	1073279406
	.long	2732824428
	.long	1015401491
	.long	194117574
	.long	1073288283
	.long	777528612
	.long	3164460665
	.long	2966275557
	.long	1073297207
	.long	2176155324
	.long	3160891335
	.long	3418903055
	.long	1073306180
	.long	2527457337
	.long	3161869180
	.long	2682146384
	.long	1073315202
	.long	2082178513
	.long	3164411995
	.long	1892288442
	.long	1073324273
	.long	2446255666
	.long	3163648957
	.long	2191782032
	.long	1073333393
	.long	2960257726
	.long	1014791238
	.long	434316067
	.long	1073342563
	.long	2028358766
	.long	1014506698
	.long	2069751141
	.long	1073351782
	.long	1562170675
	.long	3163773257
	.long	3964284211
	.long	1073361051
	.long	2111583915
	.long	1016475740
	.long	2990417245
	.long	1073370371
	.long	3683467745
	.long	3164417902
	.long	321958744
	.long	1073379742
	.long	3401933767
	.long	1016843134
	.long	1434058175
	.long	1073389163
	.long	251133233
	.long	1016134345
	.long	3218338682
	.long	1073398635
	.long	3404164304
	.long	3163525684
	.long	2572866477
	.long	1073408159
	.long	878562433
	.long	1016570317
	.long	697153126
	.long	1073417735
	.long	1283515429
	.long	3164331765
	.long	3092190715
	.long	1073427362
	.long	814012168
	.long	3160571998
	.long	2380618042
	.long	1073437042
	.long	3149557219
	.long	3164369375
	.long	4076559943
	.long	1073446774
	.long	2119478331
	.long	3161806927
	.long	815859274
	.long	1073456560
	.long	240396590
	.long	3164536019
	.long	2420883922
	.long	1073466398
	.long	2049810052
	.long	1015168464
	.long	1540824585
	.long	1073476290
	.long	1064017011
	.long	3164536266
	.long	3716502172
	.long	1073486235
	.long	2303740125
	.long	1015091301
	.long	1610600570
	.long	1073496235
	.long	3766732298
	.long	1016808759
	.long	777507147
	.long	1073506289
	.long	4282924205
	.long	1016236109
	.long	2483480501
	.long	1073516397
	.long	1216371780
	.long	1014082748
	.long	3706687593
	.long	1073526560
	.long	3521726940
	.long	1014301643
	.long	1432208378
	.long	1073536779
	.long	1401068914
	.long	3163412539
	.long	1242007932
	.long	1073547053
	.long	1132034716
	.long	3164388407
	.long	135105010
	.long	1073557383
	.long	1906148728
	.long	3164424315
	.long	3707479175
	.long	1073567768
	.long	3613079303
	.long	1015213314
	.long	382305176
	.long	1073578211
	.long	2347622376
	.long	3163627201
	.long	64696965
	.long	1073588710
	.long	1768797490
	.long	1016865536
	.long	4076975200
	.long	1073599265
	.long	2029000899
	.long	1016257111
	.long	863738719
	.long	1073609879
	.long	1326992220
	.long	3163661773
	.long	351641897
	.long	1073620550
	.long	2172261526
	.long	3164059175
	.long	3884662774
	.long	1073631278
	.long	2158611599
	.long	1015258761
	.long	4224142467
	.long	1073642065
	.long	3389820386
	.long	1016255778
	.long	2728693978
	.long	1073652911
	.long	396109971
	.long	3164511267
	.long	764307441
	.long	1073663816
	.long	3021057420
	.long	3164378099
	.long	3999357479
	.long	1073674779
	.long	2258941616
	.long	1016973300
	.long	929806999
	.long	1073685803
	.long	3205336643
	.long	1016308133
	.long	1533953344
	.long	1073696886
	.long	769171851
	.long	1016714209
	.long	2912730644
	.long	1073708029
	.long	3490067722
	.long	3164453650
	.long	2174652632
	.long	1073719233
	.long	4087714590
	.long	1015498835
	.long	730821105
	.long	1073730498
	.long	2523232743
	.long	1013115764
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	730821105
	.long	1072681922
	.long	2523232743
	.long	1012067188
	.long	2174652632
	.long	1072670657
	.long	4087714590
	.long	1014450259
	.long	2912730644
	.long	1072659453
	.long	3490067722
	.long	3163405074
	.long	1533953344
	.long	1072648310
	.long	769171851
	.long	1015665633
	.long	929806999
	.long	1072637227
	.long	3205336643
	.long	1015259557
	.long	3999357479
	.long	1072626203
	.long	2258941616
	.long	1015924724
	.long	764307441
	.long	1072615240
	.long	3021057420
	.long	3163329523
	.long	2728693978
	.long	1072604335
	.long	396109971
	.long	3163462691
	.long	4224142467
	.long	1072593489
	.long	3389820386
	.long	1015207202
	.long	3884662774
	.long	1072582702
	.long	2158611599
	.long	1014210185
	.long	351641897
	.long	1072571974
	.long	2172261526
	.long	3163010599
	.long	863738719
	.long	1072561303
	.long	1326992220
	.long	3162613197
	.long	4076975200
	.long	1072550689
	.long	2029000899
	.long	1015208535
	.long	64696965
	.long	1072540134
	.long	1768797490
	.long	1015816960
	.long	382305176
	.long	1072529635
	.long	2347622376
	.long	3162578625
	.long	3707479175
	.long	1072519192
	.long	3613079303
	.long	1014164738
	.long	135105010
	.long	1072508807
	.long	1906148728
	.long	3163375739
	.long	1242007932
	.long	1072498477
	.long	1132034716
	.long	3163339831
	.long	1432208378
	.long	1072488203
	.long	1401068914
	.long	3162363963
	.long	3706687593
	.long	1072477984
	.long	3521726940
	.long	1013253067
	.long	2483480501
	.long	1072467821
	.long	1216371780
	.long	1013034172
	.long	777507147
	.long	1072457713
	.long	4282924205
	.long	1015187533
	.long	1610600570
	.long	1072447659
	.long	3766732298
	.long	1015760183
	.long	3716502172
	.long	1072437659
	.long	2303740125
	.long	1014042725
	.long	1540824585
	.long	1072427714
	.long	1064017011
	.long	3163487690
	.long	2420883922
	.long	1072417822
	.long	2049810052
	.long	1014119888
	.long	815859274
	.long	1072407984
	.long	240396590
	.long	3163487443
	.long	4076559943
	.long	1072398198
	.long	2119478331
	.long	3160758351
	.long	2380618042
	.long	1072388466
	.long	3149557219
	.long	3163320799
	.long	3092190715
	.long	1072378786
	.long	814012168
	.long	3159523422
	.long	697153126
	.long	1072369159
	.long	1283515429
	.long	3163283189
	.long	2572866477
	.long	1072359583
	.long	878562433
	.long	1015521741
	.long	3218338682
	.long	1072350059
	.long	3404164304
	.long	3162477108
	.long	1434058175
	.long	1072340587
	.long	251133233
	.long	1015085769
	.long	321958744
	.long	1072331166
	.long	3401933767
	.long	1015794558
	.long	2990417245
	.long	1072321795
	.long	3683467745
	.long	3163369326
	.long	3964284211
	.long	1072312475
	.long	2111583915
	.long	1015427164
	.long	2069751141
	.long	1072303206
	.long	1562170675
	.long	3162724681
	.long	434316067
	.long	1072293987
	.long	2028358766
	.long	1013458122
	.long	2191782032
	.long	1072284817
	.long	2960257726
	.long	1013742662
	.long	1892288442
	.long	1072275697
	.long	2446255666
	.long	3162600381
	.long	2682146384
	.long	1072266626
	.long	2082178513
	.long	3163363419
	.long	3418903055
	.long	1072257604
	.long	2527457337
	.long	3160820604
	.long	2966275557
	.long	1072248631
	.long	2176155324
	.long	3159842759
	.long	194117574
	.long	1072239707
	.long	777528612
	.long	3163412089
	.long	2568320822
	.long	1072230830
	.long	2732824428
	.long	1014352915
	.long	380978316
	.long	1072222002
	.long	854188970
	.long	3160462686
	.long	1110089947
	.long	1072213221
	.long	1451641639
	.long	1015474673
	.long	3649726105
	.long	1072204487
	.long	4085036346
	.long	1015649474
	.long	2604962541
	.long	1072195801
	.long	2614425274
	.long	3163539192
	.long	1176749997
	.long	1072187162
	.long	2738998779
	.long	3162035844
	.long	2571947539
	.long	1072178569
	.long	3558159064
	.long	3163376669
	.long	1413356050
	.long	1072170023
	.long	1651349291
	.long	3162668166
	.long	919555682
	.long	1072161523
	.long	3121969534
	.long	1012948226
	.long	19972402
	.long	1072153069
	.long	3507899862
	.long	1016009292
	.long	1944781191
	.long	1072144660
	.long	3993278767
	.long	3161724279
	.long	1339972927
	.long	1072136297
	.long	167908909
	.long	1015572152
	.long	1447192521
	.long	1072127979
	.long	1462857171
	.long	3162514521
	.long	1218806132
	.long	1072119706
	.long	1818613052
	.long	3162548441
	.long	3907805044
	.long	1072111477
	.long	2257091225
	.long	3161550407
	.long	4182873220
	.long	1072103293
	.long	629542646
	.long	3161996303
	.long	1013258799
	.long	1072095154
	.long	1748797611
	.long	3160129082
	.long	1963711167
	.long	1072087058
	.long	1744767757
	.long	3160574294
	.long	1719614413
	.long	1072079006
	.long	330458198
	.long	3163282740
	.long	3561793907
	.long	1072070997
	.long	1157054053
	.long	1011890350
	.long	2186617381
	.long	1072063032
	.long	2270764084
	.long	3163272713
	.long	885834528
	.long	1072055110
	.long	1973258547
	.long	3162261564
	.long	2956612997
	.long	1072047230
	.long	2118169751
	.long	3162735553
	.long	3111574537
	.long	1072039393
	.long	2606161479
	.long	3162759746
	.long	363667784
	.long	1072031599
	.long	813753950
	.long	1015785209
	.long	2321106615
	.long	1072023846
	.long	2171176610
	.long	1009535771
	.long	3712504873
	.long	1072016135
	.long	88491949
	.long	1015427660
	.long	3566716925
	.long	1072008466
	.long	1536826856
	.long	1014142433
	.long	917841882
	.long	1072000839
	.long	18715565
	.long	1015659308
	.long	3395129871
	.long	1071993252
	.long	4025345435
	.long	3162335388
	.long	1453150082
	.long	1071985707
	.long	498154669
	.long	3161488062
	.long	2731501122
	.long	1071978202
	.long	1774031855
	.long	3162470021
	.long	1990012071
	.long	1071970738
	.long	3529070563
	.long	3162813193
	.long	2583551245
	.long	1071963314
	.long	3161094195
	.long	1015606491
	.long	3577096743
	.long	1071955930
	.long	2951496418
	.long	1013793687
	.long	4040676318
	.long	1071948586
	.long	4090609238
	.long	1015663458
	.long	3049340112
	.long	1071941282
	.long	3062915824
	.long	1013170595
	.long	3978100823
	.long	1071934017
	.long	3513027190
	.long	1015845963
	.long	1617004845
	.long	1071926792
	.long	82804944
	.long	1010342778
	.long	3645941911
	.long	1071919605
	.long	3814685081
	.long	3161573341
	.long	569847338
	.long	1071912458
	.long	472945272
	.long	3159290729
	.long	78413852
	.long	1071905349
	.long	4183226867
	.long	3163017251
	.long	1276261410
	.long	1071898278
	.long	300981948
	.long	1014684169
	.long	3272845541
	.long	1071891245
	.long	928852419
	.long	3163488248
	.long	887463927
	.long	1071884251
	.long	3596744163
	.long	3160794166
	.long	1829099622
	.long	1071877294
	.long	1016661181
	.long	3163461005
	.long	926591435
	.long	1071870375
	.long	3208833762
	.long	3162913514
	.long	1603444721
	.long	1071863493
	.long	1548633640
	.long	3162201326
	.long	2992903935
	.long	1071856648
	.long	2218154406
	.long	1015228193
	.long	4232894513
	.long	1071849840
	.long	2383938684
	.long	1014668519
	.long	171030293
	.long	1071843070
	.long	3526460132
	.long	1014428778
	.long	2839424854
	.long	1071836335
	.long	1171596163
	.long	1013041679
	.long	2799960843
	.long	1071829637
	.long	1423655381
	.long	1015022151
	.long	3504003472
	.long	1071822975
	.long	3594001060
	.long	3157330652
	.long	4112506593
	.long	1071816349
	.long	2947355221
	.long	1014371048
	.long	3790955393
	.long	1071809759
	.long	2352942462
	.long	3163180090
	.long	1709341917
	.long	1071803205
	.long	2571168217
	.long	1014152499
	.long	1337108031
	.long	1071796686
	.long	3203724452
	.long	1014677845
	.long	1853186616
	.long	1071790202
	.long	3066496371
	.long	1015656574
	.long	2440944790
	.long	1071783753
	.long	2492769774
	.long	1014147454
	.long	2288159958
	.long	1071777339
	.long	2169144469
	.long	1014876021
	.long	586995997
	.long	1071770960
	.long	41662348
	.long	3162627992
	.long	828946858
	.long	1071764615
	.long	10642492
	.long	1015939438
	.long	2214878420
	.long	1071758304
	.long	892270087
	.long	3163116422
	.long	3949972341
	.long	1071752027
	.long	2068408548
	.long	1014913868
	.long	948735466
	.long	1071745785
	.long	3516338028
	.long	3162574883
	.long	1014845819
	.long	1071739576
	.long	3117910646
	.long	3161559105
	.long	3366293073
	.long	1071733400
	.long	3119426314
	.long	1014120554
	.long	2930322912
	.long	1071727258
	.long	2599499422
	.long	3162714047
	.long	3228316108
	.long	1071721149
	.long	3010241991
	.long	3158422804
	.long	3490863953
	.long	1071715073
	.long	960797498
	.long	3162948880
	.long	2952712987
	.long	1071709030
	.long	3293494651
	.long	3160120301
	.long	852742562
	.long	1071703020
	.long	667253586
	.long	1009793559
	.long	728909815
	.long	1071697042
	.long	383930225
	.long	1015029468
	.long	1828292879
	.long	1071691096
	.long	1255956747
	.long	1015588398
	.long	3402036099
	.long	1071685182
	.long	405889334
	.long	1015105656
	.long	410360776
	.long	1071679301
	.long	1269990655
	.long	1011975870
	.long	702412510
	.long	1071673451
	.long	3803266087
	.long	3162280415
	.long	3541402996
	.long	1071667632
	.long	2759177317
	.long	1014854626
	.long	3899555717
	.long	1071661845
	.long	427280750
	.long	3162546972
	.long	1048019041
	.long	1071656090
	.long	1398474845
	.long	3160510595
	.long	2851812149
	.long	1071650365
	.long	2595802551
	.long	1015767337
	.long	0
	.long	1127743488
	.long	0
	.long	3275227136
	.long	3607404736
	.long	1044146952
	.long	3607404736
	.long	3191630600
	.long	4277811695
	.long	1063661122
	.long	4277811695
	.long	3211144770
	.long	2140175755
	.long	1033864261
	.long	2140175755
	.long	1033864261
	.long	4289495988
	.long	1054113747
	.long	4289495988
	.long	1054113747
	.long	4277811695
	.long	1064709698
	.long	4277811695
	.long	3212193346
	.long	1610612736
	.long	1080497479
	.long	4166901572
	.long	1053077003
	.long	3078135644
	.long	1049787983
	.long	381774870
	.long	1062650220
	.long	436314137
	.long	1056571808
	.long	1431655765
	.long	1067799893
	.long	4160749568
	.long	2147483647
	.long	0
	.long	1072693248
	.type	static_const_table,@object
	.size	static_const_table,4256
	.data
	.section .note.GNU-stack, ""
# End
