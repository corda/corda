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
	.file "asinh_wmt.c"
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
# -- Begin  asinh
	.text
       .align    16,0x90
	.globl asinh
asinh:
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
        movsd     112(%esp), %xmm0
        movapd    %xmm0, %xmm5
        movsd     %xmm0, 8(%esp)
        pand      240(%ebx), %xmm0
        pextrw    $3, %xmm0, %eax
        cmpl      $32752, %eax
        jge       .L_2TAG_PACKET_0.0.2
        cmpl      $16304, %eax
        jle       .L_2TAG_PACKET_1.0.2
        pand      144(%ebx), %xmm5
        movq      %xmm5, (%esp)
        cmpl      $16403, %eax
        jle       .L_2TAG_PACKET_2.0.2
        cmpl      $16816, %eax
        jge       .L_2TAG_PACKET_3.0.2
        movq      112(%ebx), %xmm1
        movapd    %xmm0, %xmm2
        mulsd     %xmm0, %xmm0
        addsd     112(%ebx), %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm2, %xmm0
        paddw     256(%ebx), %xmm2
        divsd     %xmm0, %xmm1
        movapd    %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_2.0.2:
        movapd    %xmm0, %xmm1
        movapd    %xmm0, %xmm3
        pand      176(%ebx), %xmm0
        movapd    %xmm0, %xmm2
        subsd     %xmm0, %xmm1
        addsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm3
        addsd     112(%ebx), %xmm0
        movapd    %xmm3, %xmm4
        addsd     %xmm0, %xmm3
        sqrtsd    %xmm3, %xmm3
        movapd    %xmm3, %xmm5
        pand      176(%ebx), %xmm3
        subsd     %xmm3, %xmm5
        movapd    %xmm3, %xmm6
        movapd    %xmm3, %xmm7
        addsd     %xmm3, %xmm2
        mulsd     %xmm3, %xmm3
        mulsd     %xmm5, %xmm6
        addsd     %xmm5, %xmm7
        subsd     %xmm3, %xmm0
        subsd     %xmm6, %xmm0
        addsd     %xmm0, %xmm4
        movapd    %xmm2, %xmm0
        divsd     %xmm7, %xmm4
        addsd     %xmm5, %xmm4
        mulsd     96(%ebx), %xmm4
        addsd     %xmm4, %xmm1
.L_2TAG_PACKET_4.0.2:
        movq      208(%ebx), %xmm3
        addsd     %xmm1, %xmm0
        movapd    %xmm0, %xmm5
        psrlq     $41, %xmm5
        pextrw    $0, %xmm5, %ecx
        pand      176(%ebx), %xmm0
        subsd     %xmm0, %xmm2
        addsd     %xmm2, %xmm1
        pshufd    $68, %xmm0, %xmm4
        pand      160(%ebx), %xmm0
        pand      192(%ebx), %xmm4
        psubd     %xmm4, %xmm3
        psubw     112(%ebx), %xmm4
        psrlq     $52, %xmm4
        por       112(%ebx), %xmm0
        por       128(%ebx), %xmm4
        mulsd     %xmm3, %xmm1
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_3.0.2:
        shrl      $4, %eax
        subl      $1022, %eax
        movd      %eax, %xmm4
        unpcklpd  %xmm4, %xmm4
        movl      12(%esp), %ecx
        por       128(%ebx), %xmm4
        pand      160(%ebx), %xmm0
        por       112(%ebx), %xmm0
        movapd    %xmm0, %xmm1
        pand      176(%ebx), %xmm0
        subsd     %xmm0, %xmm1
        shrl      $9, %ecx
.L_2TAG_PACKET_5.0.2:
        subpd     128(%ebx), %xmm4
        movq      80(%ebx), %xmm2
        andl      $2040, %ecx
        mulpd     224(%ebx), %xmm4
        mulsd     4384(%ebx,%ecx), %xmm0
        mulsd     4384(%ebx,%ecx), %xmm1
        subsd     112(%ebx), %xmm0
        movapd    %xmm0, %xmm5
        addsd     %xmm1, %xmm0
        movapd    %xmm0, %xmm3
        unpcklpd  %xmm0, %xmm0
        addpd     288(%ebx,%ecx,2), %xmm4
        movhlps   %xmm4, %xmm6
        mulpd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm2
        addpd     64(%ebx), %xmm2
        mulpd     %xmm0, %xmm2
        addpd     48(%ebx), %xmm2
        mulpd     %xmm0, %xmm2
        movhlps   %xmm2, %xmm0
        mulsd     %xmm3, %xmm0
        addsd     %xmm2, %xmm0
        addsd     %xmm1, %xmm0
        movsd     (%esp), %xmm1
        addsd     %xmm4, %xmm0
        addsd     %xmm6, %xmm5
        addsd     %xmm5, %xmm0
        por       %xmm1, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_1.0.2:
        cmpl      $15456, %eax
        jle       .L_2TAG_PACKET_7.0.2
        fldl      8(%esp)
        fldl      8(%esp)
        fmul      %st(0), %st
        fldl      32(%ebx)
        fmul      %st(1), %st
        faddl     24(%ebx)
        fmul      %st(1), %st
        faddl     16(%ebx)
        fmul      %st(1), %st
        faddl     8(%ebx)
        fmul      %st(1), %st
        faddl     (%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_7.0.2:
        andl      $32752, %eax
        je        .L_2TAG_PACKET_8.0.2
        pextrw    $3, %xmm5, %ecx
        shrl      $15, %ecx
        fldl      8(%esp)
        fldl      272(%ebx,%ecx,8)
        fmul      %st(0), %st
        fsubrp    %st, %st(1)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_8.0.2:
        fldl      8(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     (%esp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_0.0.2:
        addsd     %xmm5, %xmm5
        movq      %xmm5, (%esp)
        fldl      (%esp)
.L_2TAG_PACKET_6.0.2:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	asinh,@function
	.size	asinh,.-asinh
	.data
# -- End  asinh
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1431655746
	.long	3217380693
	.long	858875226
	.long	1068708659
	.long	2809361764
	.long	3215383405
	.long	1241898124
	.long	1067392054
	.long	966918340
	.long	3214324669
	.long	0
	.long	0
	.long	0
	.long	3219128320
	.long	1431621855
	.long	1070945621
	.long	4294842013
	.long	3218079743
	.long	1289448124
	.long	1070176674
	.long	2077359316
	.long	3217380703
	.long	0
	.long	0
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.long	0
	.long	4294967295
	.long	0
	.long	4294967295
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	0
	.long	2145386496
	.long	0
	.long	2145386496
	.long	897137782
	.long	1038760431
	.long	4276092928
	.long	1072049730
	.long	4294967295
	.long	2147483647
	.long	0
	.long	0
	.long	0
	.long	1048576
	.long	0
	.long	0
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.long	662290844
	.long	1040545122
	.long	0
	.long	1063257089
	.long	1410206301
	.long	1039423595
	.long	0
	.long	1064833554
	.long	1576129908
	.long	1044327686
	.long	0
	.long	1065621801
	.long	3771466512
	.long	1043372267
	.long	0
	.long	1066152307
	.long	2250061482
	.long	1041942323
	.long	0
	.long	1066510009
	.long	241025648
	.long	1043818160
	.long	0
	.long	1066777242
	.long	3481293707
	.long	1039994007
	.long	0
	.long	1067045544
	.long	17391197
	.long	1042467788
	.long	0
	.long	1067281191
	.long	1429304634
	.long	1045705663
	.long	0
	.long	1067500943
	.long	2258946786
	.long	1045312243
	.long	0
	.long	1067619660
	.long	725099158
	.long	1043780790
	.long	0
	.long	1067755853
	.long	1795694751
	.long	1046370967
	.long	0
	.long	1067875476
	.long	218276363
	.long	1045874206
	.long	0
	.long	1067995528
	.long	3918058606
	.long	1045941877
	.long	0
	.long	1068133258
	.long	2029669416
	.long	1040964778
	.long	0
	.long	1068254238
	.long	111041497
	.long	1042505837
	.long	0
	.long	1068375655
	.long	9214008
	.long	1042394003
	.long	0
	.long	1068497513
	.long	3222421290
	.long	1046711220
	.long	0
	.long	1068559379
	.long	1120482910
	.long	1046024006
	.long	0
	.long	1068620754
	.long	3817590987
	.long	1046519755
	.long	0
	.long	1068682354
	.long	3431328350
	.long	1046805053
	.long	0
	.long	1068744181
	.long	3088376696
	.long	1046390788
	.long	0
	.long	1068806237
	.long	3229576382
	.long	1046428373
	.long	0
	.long	1068859611
	.long	1328753044
	.long	1046554315
	.long	0
	.long	1068922096
	.long	1186666727
	.long	1044788651
	.long	0
	.long	1068984815
	.long	603190068
	.long	1044621365
	.long	0
	.long	1069047769
	.long	1647019864
	.long	1045911122
	.long	0
	.long	1069101918
	.long	4200935885
	.long	1044407410
	.long	0
	.long	1069165314
	.long	3581720604
	.long	1046840144
	.long	0
	.long	1069219844
	.long	2217841072
	.long	1046325234
	.long	0
	.long	1069283688
	.long	1963222525
	.long	1047002562
	.long	0
	.long	1069338605
	.long	2828272122
	.long	1046012177
	.long	0
	.long	1069393703
	.long	3730942205
	.long	1046476867
	.long	0
	.long	1069458213
	.long	3166604115
	.long	1047518079
	.long	0
	.long	1069513705
	.long	2095461845
	.long	1046790089
	.long	0
	.long	1069558451
	.long	322388698
	.long	1046865736
	.long	0
	.long	1069586382
	.long	2407253617
	.long	1047545084
	.long	0
	.long	1069619086
	.long	3437791416
	.long	1047490483
	.long	0
	.long	1069647220
	.long	2693469775
	.long	1045625134
	.long	0
	.long	1069675449
	.long	1454696571
	.long	1045327327
	.long	0
	.long	1069703773
	.long	1940756848
	.long	1043926274
	.long	0
	.long	1069732193
	.long	2244096130
	.long	1047717922
	.long	0
	.long	1069760709
	.long	2856509870
	.long	1047123452
	.long	0
	.long	1069789323
	.long	116200689
	.long	1043361536
	.long	0
	.long	1069813243
	.long	4018367049
	.long	1048385867
	.long	0
	.long	1069842036
	.long	3162755613
	.long	1048496333
	.long	0
	.long	1069870929
	.long	2730655796
	.long	1048315252
	.long	0
	.long	1069899922
	.long	892380602
	.long	1046868829
	.long	0
	.long	1069929016
	.long	3139480334
	.long	1046190510
	.long	0
	.long	1069953338
	.long	1886171545
	.long	1048270714
	.long	0
	.long	1069982617
	.long	2203106387
	.long	1048291228
	.long	0
	.long	1070007095
	.long	659271154
	.long	1044549473
	.long	0
	.long	1070036564
	.long	377790372
	.long	1045759361
	.long	0
	.long	1070066136
	.long	1269824164
	.long	1047357396
	.long	0
	.long	1070090859
	.long	1643831617
	.long	1048231604
	.long	0
	.long	1070115655
	.long	57265969
	.long	1047663307
	.long	0
	.long	1070145508
	.long	2640032722
	.long	1046479978
	.long	0
	.long	1070170467
	.long	2420992102
	.long	1043079769
	.long	0
	.long	1070200516
	.long	2711573521
	.long	1045731911
	.long	0
	.long	1070225639
	.long	1973048145
	.long	1048077010
	.long	0
	.long	1070250837
	.long	4093691059
	.long	1046627963
	.long	0
	.long	1070276112
	.long	4182783767
	.long	1047690348
	.long	0
	.long	1070306542
	.long	2883525439
	.long	1048055018
	.long	0
	.long	1070331985
	.long	3917130655
	.long	1046818760
	.long	0
	.long	1070357506
	.long	2320146059
	.long	1048132455
	.long	0
	.long	1070383104
	.long	3464331364
	.long	1047738634
	.long	0
	.long	1070408781
	.long	674627443
	.long	1046468986
	.long	0
	.long	1070434537
	.long	2152289494
	.long	1046214007
	.long	0
	.long	1070460372
	.long	37897452
	.long	1048575202
	.long	0
	.long	1070486286
	.long	1899477568
	.long	1044793529
	.long	0
	.long	1070512282
	.long	2077140689
	.long	1048534003
	.long	0
	.long	1070538357
	.long	448558287
	.long	1045911230
	.long	0
	.long	1070564515
	.long	3867352000
	.long	1046082819
	.long	0
	.long	1070590754
	.long	2206537816
	.long	1049157739
	.long	0
	.long	1070606585
	.long	1799603998
	.long	1049369388
	.long	0
	.long	1070619787
	.long	4075457018
	.long	1048703324
	.long	0
	.long	1070630379
	.long	572844178
	.long	1049388451
	.long	0
	.long	1070643656
	.long	540240475
	.long	1048063456
	.long	0
	.long	1070656976
	.long	2413664112
	.long	1047739393
	.long	0
	.long	1070670338
	.long	2592177285
	.long	1048727389
	.long	0
	.long	1070681058
	.long	1541931453
	.long	1048698417
	.long	0
	.long	1070694497
	.long	3345398163
	.long	1049085432
	.long	0
	.long	1070707979
	.long	2241984547
	.long	1049164260
	.long	0
	.long	1070718796
	.long	890223989
	.long	1048265646
	.long	0
	.long	1070732357
	.long	1226890683
	.long	1048781783
	.long	0
	.long	1070743237
	.long	1600520378
	.long	1048834273
	.long	0
	.long	1070756877
	.long	1081324478
	.long	1048934847
	.long	0
	.long	1070767821
	.long	1928471339
	.long	1042640487
	.long	0
	.long	1070781542
	.long	982084959
	.long	1048971733
	.long	0
	.long	1070792550
	.long	2380239162
	.long	1047711062
	.long	0
	.long	1070806352
	.long	3609542249
	.long	1048031507
	.long	0
	.long	1070817426
	.long	3466499103
	.long	1049126511
	.long	0
	.long	1070828529
	.long	1626165976
	.long	1048431820
	.long	0
	.long	1070842450
	.long	261635432
	.long	1048113242
	.long	0
	.long	1070853620
	.long	1670437810
	.long	1046604532
	.long	0
	.long	1070864820
	.long	3910575981
	.long	1049365107
	.long	0
	.long	1070876049
	.long	727294688
	.long	1048407484
	.long	0
	.long	1070890129
	.long	3260008354
	.long	1047549258
	.long	0
	.long	1070901427
	.long	872084085
	.long	1048711456
	.long	0
	.long	1070912755
	.long	754632403
	.long	1048687679
	.long	0
	.long	1070924114
	.long	1033413261
	.long	1048105461
	.long	0
	.long	1070935504
	.long	1155773876
	.long	1047412165
	.long	0
	.long	1070946925
	.long	746670859
	.long	1046841493
	.long	0
	.long	1070961245
	.long	1820254373
	.long	1048237327
	.long	0
	.long	1070972736
	.long	3605217861
	.long	1047440922
	.long	0
	.long	1070984259
	.long	2212317823
	.long	1049212421
	.long	0
	.long	1070995813
	.long	2826670683
	.long	1047678316
	.long	0
	.long	1071007400
	.long	3532575187
	.long	1049339451
	.long	0
	.long	1071019018
	.long	413443603
	.long	1049028939
	.long	0
	.long	1071030669
	.long	2964472999
	.long	1043732931
	.long	0
	.long	1071042353
	.long	389771102
	.long	1049430622
	.long	0
	.long	1071051136
	.long	1005132876
	.long	1048342053
	.long	0
	.long	1071062877
	.long	2877512173
	.long	1049492173
	.long	0
	.long	1071074650
	.long	1874411204
	.long	1048724858
	.long	0
	.long	1071086457
	.long	1011596427
	.long	1048646627
	.long	0
	.long	1071098297
	.long	838498113
	.long	1043068341
	.long	0
	.long	1071110171
	.long	409945993
	.long	1047118137
	.long	0
	.long	1071122078
	.long	2035722984
	.long	1049263601
	.long	0
	.long	1071131030
	.long	4272650650
	.long	1048435570
	.long	0
	.long	1071142997
	.long	867757828
	.long	1047923871
	.long	0
	.long	1071154998
	.long	433900304
	.long	1048766295
	.long	0
	.long	1071164021
	.long	2626749065
	.long	1049334020
	.long	0
	.long	1071176082
	.long	2721103389
	.long	1049448370
	.long	0
	.long	1071188178
	.long	3401674810
	.long	1049424769
	.long	0
	.long	1071197273
	.long	3030733799
	.long	1047804815
	.long	0
	.long	1071209431
	.long	1532130487
	.long	1045537774
	.long	0
	.long	1071221624
	.long	225486125
	.long	1049520040
	.long	0
	.long	1071230791
	.long	1326263423
	.long	1044918825
	.long	0
	.long	1071243047
	.long	3029302589
	.long	1049469599
	.long	0
	.long	1071252261
	.long	3326443421
	.long	1042360275
	.long	0
	.long	1071264580
	.long	3770244744
	.long	1048027184
	.long	0
	.long	1071273842
	.long	2568976454
	.long	1045847505
	.long	0
	.long	1071286224
	.long	628040571
	.long	1048248574
	.long	0
	.long	1071295534
	.long	2688775955
	.long	1048311770
	.long	0
	.long	1071307980
	.long	3357093336
	.long	1047484659
	.long	0
	.long	1071317339
	.long	2753529189
	.long	1047343532
	.long	0
	.long	1071329850
	.long	625560417
	.long	1043404964
	.long	0
	.long	1071339258
	.long	2456374941
	.long	1048890167
	.long	0
	.long	1071351834
	.long	748255530
	.long	1049487286
	.long	0
	.long	1071361291
	.long	2246865993
	.long	1048768259
	.long	0
	.long	1071370770
	.long	205672663
	.long	1047230532
	.long	0
	.long	1071383442
	.long	4242870741
	.long	1046367538
	.long	0
	.long	1071392971
	.long	2016709797
	.long	1049037028
	.long	0
	.long	1071402521
	.long	2569308411
	.long	1049159850
	.long	0
	.long	1071415289
	.long	3242472523
	.long	1048132214
	.long	0
	.long	1071424891
	.long	1602055934
	.long	1044691415
	.long	0
	.long	1071434515
	.long	1307177952
	.long	1049244134
	.long	0
	.long	1071444160
	.long	2152237414
	.long	1048407400
	.long	0
	.long	1071457056
	.long	2598207309
	.long	1047639998
	.long	0
	.long	1071466754
	.long	3115077937
	.long	1048719780
	.long	0
	.long	1071476474
	.long	1272724396
	.long	1048321721
	.long	0
	.long	1071486217
	.long	2860867658
	.long	1049612888
	.long	0
	.long	1071495982
	.long	1467300368
	.long	1047143154
	.long	0
	.long	1071509039
	.long	3449085837
	.long	1046095654
	.long	0
	.long	1071518858
	.long	2146871948
	.long	1044681726
	.long	0
	.long	1071528700
	.long	3066750144
	.long	1046492840
	.long	0
	.long	1071538565
	.long	1416401583
	.long	1048453263
	.long	0
	.long	1071548453
	.long	2249840317
	.long	1046959692
	.long	0
	.long	1071558365
	.long	4172904720
	.long	1048039642
	.long	0
	.long	1071568300
	.long	1549017192
	.long	1046702261
	.long	0
	.long	1071578259
	.long	3220736011
	.long	1048855214
	.long	0
	.long	1071588241
	.long	630967006
	.long	1049477219
	.long	0
	.long	1071598247
	.long	3624057028
	.long	1046733189
	.long	0
	.long	1071608278
	.long	3730794064
	.long	1048259820
	.long	0
	.long	1071618332
	.long	570378657
	.long	1049316661
	.long	0
	.long	1071628410
	.long	2814556357
	.long	1048696327
	.long	0
	.long	1071638513
	.long	194866917
	.long	1048899904
	.long	0
	.long	1071646656
	.long	3497355923
	.long	1047579730
	.long	0
	.long	1071651732
	.long	984418162
	.long	1048576793
	.long	0
	.long	1071656820
	.long	1973276684
	.long	1050136256
	.long	0
	.long	1071661920
	.long	3329497336
	.long	1049987327
	.long	0
	.long	1071667033
	.long	1374214759
	.long	1047073231
	.long	0
	.long	1071672159
	.long	2689110139
	.long	1044696853
	.long	0
	.long	1071675583
	.long	3281519615
	.long	1049239454
	.long	0
	.long	1071680729
	.long	106060705
	.long	1049441252
	.long	0
	.long	1071685888
	.long	994445709
	.long	1048383906
	.long	0
	.long	1071691060
	.long	968365306
	.long	1050219142
	.long	0
	.long	1071696244
	.long	1127183666
	.long	1048063436
	.long	0
	.long	1071701442
	.long	1176023037
	.long	1048767474
	.long	0
	.long	1071704914
	.long	494386159
	.long	1048610025
	.long	0
	.long	1071710133
	.long	3484359724
	.long	1048499390
	.long	0
	.long	1071715365
	.long	3217980016
	.long	1048823654
	.long	0
	.long	1071720610
	.long	422622404
	.long	1048883138
	.long	0
	.long	1071724114
	.long	1262752531
	.long	1048980778
	.long	0
	.long	1071729381
	.long	3412819100
	.long	1049899918
	.long	0
	.long	1071734661
	.long	4248496658
	.long	1048338572
	.long	0
	.long	1071738189
	.long	644483909
	.long	1050165690
	.long	0
	.long	1071743491
	.long	3881343467
	.long	1050118499
	.long	0
	.long	1071748807
	.long	2653622880
	.long	1048337361
	.long	0
	.long	1071752359
	.long	2812002981
	.long	1050155308
	.long	0
	.long	1071757697
	.long	224311686
	.long	1050482530
	.long	0
	.long	1071763049
	.long	679701015
	.long	1049869325
	.long	0
	.long	1071766625
	.long	3027506890
	.long	1049908641
	.long	0
	.long	1071772000
	.long	3751375892
	.long	1049481244
	.long	0
	.long	1071777389
	.long	1920294497
	.long	1050102004
	.long	0
	.long	1071780989
	.long	1483524208
	.long	1050119127
	.long	0
	.long	1071786401
	.long	97441208
	.long	1049658186
	.long	0
	.long	1071790017
	.long	3008178484
	.long	1050468097
	.long	0
	.long	1071795452
	.long	3633428993
	.long	1048933205
	.long	0
	.long	1071799084
	.long	272261604
	.long	1048795103
	.long	0
	.long	1071804543
	.long	1886467610
	.long	1049658772
	.long	0
	.long	1071810016
	.long	3703763111
	.long	1048691245
	.long	0
	.long	1071813673
	.long	3272554440
	.long	1049215679
	.long	0
	.long	1071819170
	.long	1615609844
	.long	1047938702
	.long	0
	.long	1071822843
	.long	2101526408
	.long	1049159037
	.long	0
	.long	1071826522
	.long	3501340145
	.long	1048964151
	.long	0
	.long	1071832053
	.long	3398848880
	.long	1050181422
	.long	0
	.long	1071835748
	.long	3113670222
	.long	1047639078
	.long	0
	.long	1071841304
	.long	3113807767
	.long	1050490134
	.long	0
	.long	1071845015
	.long	2638078905
	.long	1050508198
	.long	0
	.long	1071850595
	.long	3402790998
	.long	1048094059
	.long	0
	.long	1071854324
	.long	2677940860
	.long	1046276297
	.long	0
	.long	1071859929
	.long	2914178933
	.long	1050613895
	.long	0
	.long	1071863673
	.long	1395015268
	.long	1049805645
	.long	0
	.long	1071867425
	.long	3443880395
	.long	1049880454
	.long	0
	.long	1071873065
	.long	3880277067
	.long	1046944118
	.long	0
	.long	1071876834
	.long	4190954257
	.long	1048963456
	.long	0
	.long	1071880609
	.long	975315090
	.long	1050660599
	.long	0
	.long	1071886284
	.long	3981445309
	.long	1048841548
	.long	0
	.long	1071890077
	.long	340978156
	.long	1049630357
	.long	0
	.long	1071893876
	.long	3056282856
	.long	1048349819
	.long	0
	.long	1071899588
	.long	3637866925
	.long	1050038234
	.long	0
	.long	1071903404
	.long	1233925752
	.long	1047565029
	.long	0
	.long	1071907228
	.long	3365276628
	.long	1049120293
	.long	0
	.long	1071912976
	.long	1364708917
	.long	1048863613
	.long	0
	.long	1071916817
	.long	2949393003
	.long	1048806573
	.long	0
	.long	1071920665
	.long	2588933741
	.long	1049057850
	.long	0
	.long	1071924520
	.long	1317344960
	.long	1048679202
	.long	0
	.long	1071930316
	.long	4264843208
	.long	1047591051
	.long	0
	.long	1071934189
	.long	1772398646
	.long	1047786577
	.long	0
	.long	1071938069
	.long	855212881
	.long	1049083903
	.long	0
	.long	1071941956
	.long	111728933
	.long	1050121939
	.long	0
	.long	1071947800
	.long	2934994658
	.long	1046035147
	.long	0
	.long	1071951706
	.long	3504420384
	.long	1049933751
	.long	0
	.long	1071955618
	.long	2295312686
	.long	1049747989
	.long	0
	.long	1071959538
	.long	185383186
	.long	1050276648
	.long	0
	.long	1071963465
	.long	2699459344
	.long	1049336105
	.long	0
	.long	1071967400
	.long	3020028019
	.long	1048472693
	.long	0
	.long	1071973316
	.long	365843238
	.long	1049205247
	.long	0
	.long	1071977269
	.long	1597522098
	.long	1045226127
	.long	0
	.long	1071981230
	.long	731482298
	.long	1047741244
	.long	0
	.long	1071985198
	.long	19057756
	.long	1050243827
	.long	0
	.long	1071989173
	.long	2890838867
	.long	1050654615
	.long	0
	.long	1071993156
	.long	2027053605
	.long	1050184234
	.long	0
	.long	1071997147
	.long	858789886
	.long	1047748038
	.long	0
	.long	1072001146
	.long	4288580762
	.long	1046326129
	.long	0
	.long	1072007158
	.long	3861103191
	.long	1049816977
	.long	0
	.long	1072011175
	.long	1902054808
	.long	1050330775
	.long	0
	.long	1072015200
	.long	568089353
	.long	1050301118
	.long	0
	.long	1072019233
	.long	3743438430
	.long	1049790563
	.long	0
	.long	1072023274
	.long	2063211846
	.long	1047622979
	.long	0
	.long	1072027323
	.long	1911797542
	.long	1049675962
	.long	0
	.long	1072031379
	.long	3660869822
	.long	1050198853
	.long	0
	.long	1072035443
	.long	616847508
	.long	1050494731
	.long	0
	.long	1072039515
	.long	225141268
	.long	1050627992
	.long	0
	.long	1072043595
	.long	3898487111
	.long	1050663410
	.long	0
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
	.size	static_const_table,6432
	.data
	.section .note.GNU-stack, ""
# End
