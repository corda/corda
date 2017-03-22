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
	.file "tanf_wmt.c"
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
# -- Begin  tanf
	.text
       .align    16,0x90
	.globl tanf
tanf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 56(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     128(%esp), %xmm0
        subl      $16, %esp
        stmxcsr   4(%esp)
        movl      4(%esp), %eax
        andl      $-24577, %eax
        cmpl      %eax, 4(%esp)
        jne       .L_2TAG_PACKET_0.0.2
.L_2TAG_PACKET_1.0.2:
        pextrw    $1, %xmm0, %eax
        andl      $32767, %eax
        subl      $128, %eax
        cmpl      $18175, %eax
        ja        .L_2TAG_PACKET_2.0.2
        movsd     2656(%ebx), %xmm1
        mulss     %xmm0, %xmm1
        movapd    2544(%ebx), %xmm7
        cvtss2si  %xmm1, %edx
        andpd     %xmm0, %xmm7
        cvtss2sd  %xmm0, %xmm0
.L_2TAG_PACKET_3.0.2:
        movsd     2672(%ebx), %xmm4
        addss     %xmm1, %xmm4
        addss     2704(%ebx), %xmm1
        subss     2672(%ebx), %xmm4
        subss     2704(%ebx), %xmm1
        movapd    %xmm7, %xmm5
        psllq     $32, %xmm7
        psllq     $29, %xmm5
        xorpd     %xmm5, %xmm7
        addl      $1865216, %edx
        andl      $31, %edx
        movsd     2608(%ebx), %xmm5
        xorpd     %xmm7, %xmm5
        movsd     2576(%ebx), %xmm2
        xorpd     %xmm7, %xmm2
        movsd     2592(%ebx), %xmm6
        xorpd     %xmm7, %xmm6
        movsd     2560(%ebx), %xmm3
        xorpd     %xmm7, %xmm3
        lea       (%ebx), %eax
        shll      $3, %edx
        addl      %edx, %eax
        shll      $3, %edx
        addl      %edx, %eax
        psllq     $29, %xmm4
        psllq     $29, %xmm1
        mulsd     %xmm4, %xmm5
        mulsd     %xmm1, %xmm2
        mulsd     %xmm4, %xmm6
        mulsd     %xmm1, %xmm3
        addsd     %xmm0, %xmm5
        addsd     %xmm0, %xmm2
        addsd     %xmm6, %xmm5
        addsd     %xmm3, %xmm2
        movsd     2624(%ebx), %xmm7
        divsd     %xmm5, %xmm7
        mulsd     64(%eax), %xmm7
        movsd     56(%eax), %xmm3
        mulsd     %xmm2, %xmm3
        movsd     24(%eax), %xmm4
        mulsd     %xmm2, %xmm4
        movsd     40(%eax), %xmm5
        mulsd     %xmm2, %xmm5
        movsd     8(%eax), %xmm6
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm2
        addsd     48(%eax), %xmm3
        addsd     16(%eax), %xmm4
        addsd     32(%eax), %xmm5
        addsd     (%eax), %xmm6
        mulsd     %xmm2, %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm2
        addsd     %xmm5, %xmm3
        addsd     %xmm6, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm4, %xmm3
        subsd     %xmm7, %xmm3
        movl      4(%esp), %eax
        andl      $-24577, %eax
        cmpl      4(%esp), %eax
        je        .L_2TAG_PACKET_4.0.2
        stmxcsr   8(%esp)
        movl      4(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 8(%esp)
        ldmxcsr   8(%esp)
.L_2TAG_PACKET_4.0.2:
        movsd     %xmm3, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_2.0.2:
        jg        .L_2TAG_PACKET_6.0.2
        cvtss2sd  %xmm0, %xmm0
        mulsd     2640(%ebx), %xmm0
        movl      4(%esp), %eax
        andl      $-24577, %eax
        cmpl      4(%esp), %eax
        je        .L_2TAG_PACKET_7.0.2
        stmxcsr   8(%esp)
        movl      4(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 8(%esp)
        ldmxcsr   8(%esp)
.L_2TAG_PACKET_7.0.2:
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_0.0.2:
        movl      4(%esp), %eax
        andl      $-24577, %eax
        movl      %eax, 8(%esp)
        ldmxcsr   8(%esp)
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_6.0.2:
        movd      %xmm0, %eax
        andl      $2139095040, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_8.0.2
        pshuflw   $68, %xmm0, %xmm1
        movd      %xmm0, %ecx
        cvtps2pd  %xmm1, %xmm1
        andl      $2147483647, %ecx
        subl      $964689920, %ecx
        shrl      $23, %ecx
        subl      $27, %ecx
        andl      $65528, %ecx
        movsd     2304(%ebx,%ecx,2), %xmm3
        movsd     2312(%ebx,%ecx,2), %xmm5
        movsd     2736(%ebx), %xmm2
        xorpd     %xmm4, %xmm4
        movl      $17352, %edx
        pinsrw    $3, %edx, %xmm4
        andpd     %xmm3, %xmm2
        psllq     $40, %xmm3
        mulsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm5, %xmm1
        movapd    %xmm2, %xmm0
        addsd     %xmm3, %xmm2
        movapd    %xmm2, %xmm5
        subsd     %xmm2, %xmm0
        addsd     %xmm4, %xmm2
        addsd     %xmm3, %xmm0
        movsd     2720(%ebx), %xmm6
        subsd     %xmm4, %xmm2
        addsd     %xmm1, %xmm0
        subsd     %xmm2, %xmm5
        movsd     %xmm5, %xmm1
        andpd     2768(%ebx), %xmm5
        subsd     %xmm5, %xmm1
        addsd     %xmm1, %xmm0
        movsd     %xmm5, %xmm6
        mulsd     2752(%ebx), %xmm5
        mulsd     2760(%ebx), %xmm6
        mulsd     2720(%ebx), %xmm0
        addsd     %xmm6, %xmm0
        movsd     %xmm5, 8(%esp)
        movsd     %xmm0, 16(%esp)
        cvtpd2ps  %xmm5, %xmm2
        movss     2656(%ebx), %xmm1
        mulss     %xmm2, %xmm1
        movaps    2544(%ebx), %xmm7
        cvtss2si  %xmm1, %edx
        andps     %xmm2, %xmm7
        movsd     2672(%ebx), %xmm4
        addss     %xmm1, %xmm4
        addss     2704(%ebx), %xmm1
        subss     2672(%ebx), %xmm4
        subss     2704(%ebx), %xmm1
        movapd    %xmm7, %xmm5
        psllq     $32, %xmm7
        psllq     $29, %xmm5
        xorpd     %xmm5, %xmm7
        addl      $1865216, %edx
        andl      $31, %edx
        movsd     2608(%ebx), %xmm5
        xorpd     %xmm7, %xmm5
        movsd     2576(%ebx), %xmm2
        xorpd     %xmm7, %xmm2
        movsd     2592(%ebx), %xmm6
        xorpd     %xmm7, %xmm6
        movsd     2560(%ebx), %xmm3
        xorpd     %xmm7, %xmm3
        lea       (%ebx), %eax
        shll      $3, %edx
        addl      %edx, %eax
        shll      $3, %edx
        addl      %edx, %eax
        psllq     $29, %xmm4
        psllq     $29, %xmm1
        mulsd     %xmm4, %xmm5
        mulsd     %xmm1, %xmm2
        mulsd     %xmm4, %xmm6
        mulsd     %xmm1, %xmm3
        addsd     8(%esp), %xmm5
        addsd     16(%esp), %xmm5
        addsd     8(%esp), %xmm2
        addsd     16(%esp), %xmm2
        addsd     %xmm6, %xmm5
        addsd     %xmm3, %xmm2
        movsd     2624(%ebx), %xmm7
        divsd     %xmm5, %xmm7
        mulsd     64(%eax), %xmm7
        movsd     56(%eax), %xmm3
        mulsd     %xmm2, %xmm3
        movsd     24(%eax), %xmm4
        mulsd     %xmm2, %xmm4
        movsd     40(%eax), %xmm5
        mulsd     %xmm2, %xmm5
        movsd     8(%eax), %xmm6
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm2
        addsd     48(%eax), %xmm3
        addsd     16(%eax), %xmm4
        addsd     32(%eax), %xmm5
        addsd     (%eax), %xmm6
        mulsd     %xmm2, %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm2
        addsd     %xmm5, %xmm3
        addsd     %xmm6, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm4, %xmm3
        subsd     %xmm7, %xmm3
        movl      4(%esp), %eax
        andl      $-24577, %eax
        cmpl      4(%esp), %eax
        je        .L_2TAG_PACKET_9.0.2
        stmxcsr   8(%esp)
        movl      4(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 8(%esp)
        ldmxcsr   8(%esp)
.L_2TAG_PACKET_9.0.2:
        movsd     %xmm3, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_8.0.2:
        movl      4(%esp), %eax
        andl      $-24577, %eax
        cmpl      4(%esp), %eax
        je        .L_2TAG_PACKET_10.0.2
        stmxcsr   8(%esp)
        movl      4(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 8(%esp)
        ldmxcsr   8(%esp)
.L_2TAG_PACKET_10.0.2:
        movss     %xmm0, (%esp)
        flds      (%esp)
        fsubs     (%esp)
.L_2TAG_PACKET_5.0.2:
        addl      $16, %esp
        movl      56(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	tanf,@function
	.size	tanf,.-tanf
	.data
# -- End  tanf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1431655765
	.long	1070945621
	.long	0
	.long	0
	.long	286331153
	.long	1069617425
	.long	0
	.long	0
	.long	463583772
	.long	1068212666
	.long	0
	.long	0
	.long	2354785698
	.long	1069102779
	.long	3310429884
	.long	1072703419
	.long	3693284251
	.long	1069118808
	.long	1996245381
	.long	1071000265
	.long	3055842593
	.long	1068578846
	.long	2269530157
	.long	1069711235
	.long	1046897440
	.long	1067705865
	.long	522045958
	.long	1068476590
	.long	0
	.long	0
	.long	3763679576
	.long	1070167541
	.long	404682282
	.long	1072734736
	.long	3593250296
	.long	1070233561
	.long	1127373050
	.long	1071173457
	.long	24583402
	.long	1069723988
	.long	3223889699
	.long	1070020367
	.long	558065897
	.long	1068949418
	.long	388873200
	.long	1068944270
	.long	0
	.long	0
	.long	895247324
	.long	1070819848
	.long	582494902
	.long	1072789737
	.long	3631919291
	.long	1070936926
	.long	2031366438
	.long	1071495745
	.long	1509038701
	.long	1070601643
	.long	3785344682
	.long	1070618476
	.long	3233018412
	.long	1069913186
	.long	1065584192
	.long	1069747896
	.long	0
	.long	0
	.long	2583490354
	.long	1071284857
	.long	855738471
	.long	1072873155
	.long	2616040238
	.long	1071582937
	.long	2529240549
	.long	1071836633
	.long	2251697184
	.long	1071253687
	.long	1263091857
	.long	1071190461
	.long	2476932470
	.long	1070842002
	.long	855891755
	.long	1070696894
	.long	0
	.long	0
	.long	419968236
	.long	1071717047
	.long	1813808633
	.long	1072992828
	.long	1037049034
	.long	1072037305
	.long	1447406859
	.long	1072265209
	.long	3205232916
	.long	1071968658
	.long	1066110976
	.long	1071946035
	.long	1496754229
	.long	1071807201
	.long	4014441989
	.long	1071736222
	.long	0
	.long	0
	.long	716700048
	.long	1071997368
	.long	274476850
	.long	1073161398
	.long	3912524733
	.long	1072622983
	.long	4107767972
	.long	1072827408
	.long	946523347
	.long	1072772766
	.long	581531518
	.long	1072826391
	.long	3718905905
	.long	1072832823
	.long	513572637
	.long	1072861969
	.long	0
	.long	0
	.long	719595600
	.long	1072317184
	.long	1194536594
	.long	1073399478
	.long	1264738763
	.long	1073084804
	.long	1958936600
	.long	1073411493
	.long	4270740730
	.long	1073574708
	.long	1303998552
	.long	1073799186
	.long	1769828046
	.long	1073938542
	.long	433361110
	.long	1074105369
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	1073741824
	.long	0
	.long	1073741824
	.long	1431655765
	.long	1074091349
	.long	2863311531
	.long	1074440874
	.long	286331153
	.long	1074860305
	.long	95443718
	.long	1075163227
	.long	463583772
	.long	1075552698
	.long	0
	.long	0
	.long	1526324265
	.long	3217967566
	.long	457728975
	.long	1071088276
	.long	602185705
	.long	3215678092
	.long	555127889
	.long	1067545266
	.long	3690544014
	.long	3213150171
	.long	4003114407
	.long	1064581412
	.long	3759536023
	.long	3210559989
	.long	3837960785
	.long	1061790379
	.long	0
	.long	1072693248
	.long	719981842
	.long	3217669096
	.long	3749156607
	.long	1071048258
	.long	2754706541
	.long	3215359511
	.long	790323742
	.long	1067402587
	.long	4097292716
	.long	3212856302
	.long	2442796466
	.long	1064337602
	.long	170296152
	.long	3210060867
	.long	3614866008
	.long	1061361670
	.long	0
	.long	1072693248
	.long	701484222
	.long	3217377742
	.long	2267016812
	.long	1071015664
	.long	2223654598
	.long	3215071936
	.long	706390066
	.long	1067217386
	.long	1066252975
	.long	3212391267
	.long	3202745457
	.long	1064010682
	.long	1046243251
	.long	3209678971
	.long	2763962276
	.long	1060970161
	.long	0
	.long	1072693248
	.long	2829232582
	.long	3217092115
	.long	298675305
	.long	1070989821
	.long	1485063559
	.long	3214682643
	.long	3996381654
	.long	1067075828
	.long	2866066872
	.long	3211982662
	.long	3669764559
	.long	1063748136
	.long	1948234989
	.long	3209098147
	.long	3398041407
	.long	1060559728
	.long	0
	.long	1072693248
	.long	3593905368
	.long	3216590719
	.long	4076712227
	.long	1070970214
	.long	3144465176
	.long	3214191500
	.long	1618153340
	.long	1066971547
	.long	584032116
	.long	3211469261
	.long	4247487438
	.long	1063561943
	.long	3844233498
	.long	3208626322
	.long	3857199098
	.long	1060281647
	.long	0
	.long	1072693248
	.long	3789571175
	.long	3216034914
	.long	3870939386
	.long	1070956467
	.long	628750575
	.long	3213566872
	.long	2954464709
	.long	1066900026
	.long	1466315631
	.long	3210837162
	.long	2766187256
	.long	1063437894
	.long	3695969289
	.long	3207854418
	.long	3095830084
	.long	1060095334
	.long	0
	.long	1072693248
	.long	1858778712
	.long	3214984212
	.long	2242038011
	.long	1070948320
	.long	2507068734
	.long	3212502004
	.long	2446607349
	.long	1066858259
	.long	1349489537
	.long	3209765608
	.long	1721283327
	.long	1063366855
	.long	3852528092
	.long	3206760861
	.long	1758739894
	.long	1059895449
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1431655765
	.long	1070945621
	.long	0
	.long	0
	.long	381774871
	.long	1066844524
	.long	0
	.long	0
	.long	3154187623
	.long	1063343722
	.long	0
	.long	0
	.long	2469719819
	.long	1059831159
	.long	0
	.long	1072693248
	.long	1858778712
	.long	1067500564
	.long	2242038011
	.long	1070948320
	.long	2507068734
	.long	1065018356
	.long	2446607349
	.long	1066858259
	.long	1349489537
	.long	1062281960
	.long	1721283327
	.long	1063366855
	.long	3852528092
	.long	1059277213
	.long	1758739894
	.long	1059895449
	.long	0
	.long	1072693248
	.long	3789571175
	.long	1068551266
	.long	3870939386
	.long	1070956467
	.long	628750575
	.long	1066083224
	.long	2954464709
	.long	1066900026
	.long	1466315631
	.long	1063353514
	.long	2766187256
	.long	1063437894
	.long	3695969289
	.long	1060370770
	.long	3095830084
	.long	1060095334
	.long	0
	.long	1072693248
	.long	3593905368
	.long	1069107071
	.long	4076712227
	.long	1070970214
	.long	3144465176
	.long	1066707852
	.long	1618153340
	.long	1066971547
	.long	584032116
	.long	1063985613
	.long	4247487438
	.long	1063561943
	.long	3844233498
	.long	1061142674
	.long	3857199098
	.long	1060281647
	.long	0
	.long	1072693248
	.long	2829232582
	.long	1069608467
	.long	298675305
	.long	1070989821
	.long	1485063559
	.long	1067198995
	.long	3996381654
	.long	1067075828
	.long	2866066872
	.long	1064499014
	.long	3669764559
	.long	1063748136
	.long	1948234989
	.long	1061614499
	.long	3398041407
	.long	1060559728
	.long	0
	.long	1072693248
	.long	701484222
	.long	1069894094
	.long	2267016812
	.long	1071015664
	.long	2223654598
	.long	1067588288
	.long	706390066
	.long	1067217386
	.long	1066252975
	.long	1064907619
	.long	3202745457
	.long	1064010682
	.long	1046243251
	.long	1062195323
	.long	2763962276
	.long	1060970161
	.long	0
	.long	1072693248
	.long	719981842
	.long	1070185448
	.long	3749156607
	.long	1071048258
	.long	2754706541
	.long	1067875863
	.long	790323742
	.long	1067402587
	.long	4097292716
	.long	1065372654
	.long	2442796466
	.long	1064337602
	.long	170296152
	.long	1062577219
	.long	3614866008
	.long	1061361670
	.long	0
	.long	1072693248
	.long	1526324265
	.long	1070483918
	.long	457728975
	.long	1071088276
	.long	602185705
	.long	1068194444
	.long	555127889
	.long	1067545266
	.long	3690544014
	.long	1065666523
	.long	4003114407
	.long	1064581412
	.long	3759536023
	.long	1063076341
	.long	3837960785
	.long	1061790379
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.long	0
	.long	1073741824
	.long	0
	.long	3221225472
	.long	1431655765
	.long	1074091349
	.long	2863311531
	.long	3221924522
	.long	286331153
	.long	1074860305
	.long	95443718
	.long	3222646875
	.long	463583772
	.long	1075552698
	.long	0
	.long	0
	.long	719595600
	.long	3219800832
	.long	1194536594
	.long	1073399478
	.long	1264738763
	.long	3220568452
	.long	1958936600
	.long	1073411493
	.long	4270740730
	.long	3221058356
	.long	1303998552
	.long	1073799186
	.long	1769828046
	.long	3221422190
	.long	433361110
	.long	1074105369
	.long	0
	.long	0
	.long	716700048
	.long	3219481016
	.long	274476850
	.long	1073161398
	.long	3912524733
	.long	3220106631
	.long	4107767972
	.long	1072827408
	.long	946523347
	.long	3220256414
	.long	581531518
	.long	1072826391
	.long	3718905905
	.long	3220316471
	.long	513572637
	.long	1072861969
	.long	0
	.long	0
	.long	419968236
	.long	3219200695
	.long	1813808633
	.long	1072992828
	.long	1037049034
	.long	3219520953
	.long	1447406859
	.long	1072265209
	.long	3205232916
	.long	3219452306
	.long	1066110976
	.long	1071946035
	.long	1496754229
	.long	3219290849
	.long	4014441989
	.long	1071736222
	.long	0
	.long	0
	.long	2583490354
	.long	3218768505
	.long	855738471
	.long	1072873155
	.long	2616040238
	.long	3219066585
	.long	2529240549
	.long	1071836633
	.long	2251697184
	.long	3218737335
	.long	1263091857
	.long	1071190461
	.long	2476932470
	.long	3218325650
	.long	855891755
	.long	1070696894
	.long	0
	.long	0
	.long	895247324
	.long	3218303496
	.long	582494902
	.long	1072789737
	.long	3631919291
	.long	3218420574
	.long	2031366438
	.long	1071495745
	.long	1509038701
	.long	3218085291
	.long	3785344682
	.long	1070618476
	.long	3233018412
	.long	3217396834
	.long	1065584192
	.long	1069747896
	.long	0
	.long	0
	.long	3763679576
	.long	3217651189
	.long	404682282
	.long	1072734736
	.long	3593250296
	.long	3217717209
	.long	1127373050
	.long	1071173457
	.long	24583402
	.long	3217207636
	.long	3223889699
	.long	1070020367
	.long	558065897
	.long	3216433066
	.long	388873200
	.long	1068944270
	.long	0
	.long	0
	.long	2354785698
	.long	3216586427
	.long	3310429884
	.long	1072703419
	.long	3693284251
	.long	3216602456
	.long	1996245381
	.long	1071000265
	.long	3055842593
	.long	3216062494
	.long	2269530157
	.long	1069711235
	.long	1046897440
	.long	3215189513
	.long	522045958
	.long	1068476590
	.long	0
	.long	0
	.long	1832810809
	.long	1078222640
	.long	4036845384
	.long	1030771879
	.long	1832810809
	.long	1078222640
	.long	4036845384
	.long	1030771879
	.long	1916669064
	.long	1072155675
	.long	660066805
	.long	1024797180
	.long	3829231700
	.long	1062745828
	.long	2884172442
	.long	1017445907
	.long	540874025
	.long	1055617650
	.long	4198409884
	.long	1011844330
	.long	2839315448
	.long	1046639136
	.long	4204424928
	.long	1000582120
	.long	1329339431
	.long	1038229673
	.long	1295478839
	.long	993391741
	.long	658191860
	.long	1024797180
	.long	3691886121
	.long	979325773
	.long	1966800710
	.long	1020436418
	.long	1882642597
	.long	977095991
	.long	2100977488
	.long	1012908661
	.long	1840335565
	.long	967470816
	.long	4198093645
	.long	1000582120
	.long	3307942521
	.long	957055414
	.long	2839123165
	.long	996851343
	.long	1387472776
	.long	952638316
	.long	2788761024
	.long	988712873
	.long	3005712498
	.long	944467026
	.long	3694676832
	.long	979325773
	.long	1326507024
	.long	934847846
	.long	221775557
	.long	970149340
	.long	1133527550
	.long	924162364
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	3409656221
	.long	1972279101
	.long	0
	.long	0
	.long	1414004736
	.long	4156105211
	.long	0
	.long	0
	.long	1280075305
	.long	4115041315
	.long	0
	.long	0
	.long	1413742592
	.long	4156105211
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	4194304
	.long	1072693248
	.long	0
	.long	0
	.long	1092811139
	.long	0
	.long	0
	.long	0
	.long	1296039936
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	1262485504
	.long	0
	.long	0
	.long	0
	.long	1413754136
	.long	1067000315
	.long	0
	.long	0
	.long	4278190080
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	1067000315
	.long	1175561766
	.long	1043665163
	.long	4290772992
	.long	4294967295
	.long	4290772992
	.long	4294967295
	.type	static_const_table,@object
	.size	static_const_table,2784
	.data
	.section .note.GNU-stack, ""
# End
