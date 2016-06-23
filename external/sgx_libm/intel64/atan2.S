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
	.file "atan2.c"
	.text
..TXTST0:
# -- Begin  atan2
	.text
       .align    16,0x90
	.globl atan2
atan2:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_atan2.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
        movsd     %xmm1, 8(%rsp)
..B1.2:
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        subl      $14448, %eax
        cmpl      $3840, %eax
        ja        .L_2TAG_PACKET_0.0.2
        pextrw    $3, %xmm1, %eax
        andl      $32752, %eax
        subl      $14448, %eax
        cmpl      $3840, %eax
        ja        .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_2.0.2:
        unpcklpd  %xmm1, %xmm0
        xorpd     %xmm5, %xmm5
        xorpd     %xmm3, %xmm3
        movl      $2048, %eax
        pinsrw    $3, %eax, %xmm5
        paddw     %xmm1, %xmm5
        psrlq     $29, %xmm5
        rcpss     %xmm5, %xmm3
        xorpd     %xmm4, %xmm4
        movl      $14336, %ecx
        pinsrw    $3, %ecx, %xmm4
        psllq     $29, %xmm3
        paddw     %xmm4, %xmm3
        mulsd     %xmm0, %xmm3
        xorpd     %xmm2, %xmm2
        xorpd     %xmm6, %xmm6
        xorpd     %xmm7, %xmm7
        movl      $32768, %eax
        pinsrw    $2, %eax, %xmm6
        movl      $32767, %ecx
        pinsrw    $3, %ecx, %xmm7
        paddd     %xmm6, %xmm3
        andpd     %xmm7, %xmm3
        movq      %xmm3, %xmm5
        pextrw    $3, %xmm3, %eax
        movl      $16448, %ecx
        pinsrw    $3, %ecx, %xmm2
        minsd     %xmm2, %xmm3
        movmskpd  %xmm0, %edx
        psllq     $1, %xmm0
        psrlq     $1, %xmm0
        cmpsd     $2, %xmm2, %xmm5
        psllq     $1, %xmm1
        psrlq     $1, %xmm1
        movq      %xmm1, %xmm6
        movq      %xmm1, %xmm7
        movq      %xmm0, %xmm2
        movl      $0, %ecx
        pinsrw    $0, %ecx, %xmm6
        subsd     %xmm6, %xmm7
        movq      %xmm0, %xmm4
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm4
        mulsd     %xmm3, %xmm7
        andpd     %xmm5, %xmm0
        subsd     %xmm6, %xmm0
        andpd     %xmm5, %xmm1
        addsd     %xmm1, %xmm4
        subsd     %xmm7, %xmm0
        andl      $32752, %eax
        subl      $16286, %eax
        cmpl      $1121, %eax
        ja        .L_2TAG_PACKET_3.0.2
        divsd     %xmm4, %xmm0
        pextrw    $3, %xmm3, %ecx
        movsd     a2(%rip), %xmm2
        movsd     b2(%rip), %xmm3
        pextrw    $0, %xmm5, %eax
        addl      %edx, %edx
        lea       P_TBL(%rip), %r8
        movapd    (%r8,%rdx,8), %xmm6
        lea       SGN_TBL(%rip), %r8
        movapd    (%r8,%rdx,8), %xmm1
        subl      $16286, %ecx
        notl      %eax
        andl      $1, %eax
        addl      %eax, %ecx
        addl      %ecx, %ecx
        lea       ATAN_TBL(%rip), %r8
        movapd    (%r8,%rcx,8), %xmm5
        xorpd     %xmm1, %xmm5
        addpd     %xmm6, %xmm5
        movq      %xmm5, %xmm6
        unpckhpd  %xmm5, %xmm5
        xorpd     %xmm0, %xmm1
        movq      %xmm1, %xmm4
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm2
        addsd     %xmm0, %xmm3
        addsd     %xmm6, %xmm1
        subsd     %xmm1, %xmm6
        addsd     %xmm4, %xmm6
        addsd     8+a2(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm4, %xmm0
        addsd     %xmm5, %xmm6
        mulsd     %xmm2, %xmm0
        addsd     8+b2(%rip), %xmm3
        mulsd     %xmm3, %xmm0
        addsd     %xmm6, %xmm0
        addsd     %xmm1, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_3.0.2:
        addl      $942, %eax
        cmpl      $942, %eax
        ja        .L_2TAG_PACKET_4.0.2
        xorpd     %xmm4, %xmm4
        movl      $16368, %ecx
        pinsrw    $3, %ecx, %xmm4
        divsd     %xmm1, %xmm4
        addl      %edx, %edx
        lea       SGN_TBL(%rip), %r8
        movapd    (%r8,%rdx,8), %xmm6
        unpcklpd  %xmm3, %xmm3
        xorpd     %xmm6, %xmm0
        xorpd     %xmm6, %xmm2
        xorpd     %xmm6, %xmm3
        lea       P_TBL2(%rip), %r8
        movapd    (%r8,%rdx,8), %xmm7
        movsd     a2(%rip), %xmm1
        movsd     b2(%rip), %xmm5
        lea       SELECT_B(%rip), %r8
        andpd     (%r8,%rdx,8), %xmm3
        mulsd     %xmm4, %xmm2
        mulsd     %xmm4, %xmm0
        movq      %xmm2, %xmm6
        mulsd     %xmm2, %xmm2
        mulsd     %xmm2, %xmm1
        addsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm6
        addsd     8+a2(%rip), %xmm1
        mulsd     %xmm2, %xmm5
        addsd     %xmm0, %xmm7
        addpd     %xmm3, %xmm7
        mulsd     %xmm6, %xmm1
        addsd     8+b2(%rip), %xmm5
        mulsd     %xmm1, %xmm5
        addsd     %xmm7, %xmm5
        pshufd    $238, %xmm7, %xmm0
        addsd     %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_4.0.2:
        movsd     8(%rsp), %xmm1
        movsd     (%rsp), %xmm0
        pextrw    $3, %xmm1, %eax
        andl      $32752, %eax
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      %eax, %ecx
        jg        .L_2TAG_PACKET_5.0.2
        pextrw    $3, %xmm1, %ecx
        cmpl      $32767, %ecx
        jg        .L_2TAG_PACKET_6.0.2
        divsd     %xmm1, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_6.0.2:
        andpd     SGNMASK(%rip), %xmm0
        movsd     pi_table(%rip), %xmm2
        xorpd     %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_5.0.2:
        andpd     SGNMASK(%rip), %xmm0
        movsd     pi2_table(%rip), %xmm2
        xorpd     %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
.L_2TAG_PACKET_1.0.2:
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        pextrw    $3, %xmm1, %eax
        andl      $32752, %eax
        cmpl      $32752, %ecx
        je        .L_2TAG_PACKET_7.0.2
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_8.0.2
        movsd     POW55(%rip), %xmm3
        movl      $1024, %edx
        movsd     INVEXPMASK(%rip), %xmm4
        xorpd     %xmm6, %xmm6
        movsd     EXPMASK(%rip), %xmm7
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_9.0.2
.L_2TAG_PACKET_10.0.2:
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_11.0.2
.L_2TAG_PACKET_12.0.2:
        addl      %ecx, %edx
        subl      %eax, %edx
        cmpl      $2048, %edx
        ja        .L_2TAG_PACKET_4.0.2
        addl      $15344, %edx
        pinsrw    $3, %edx, %xmm6
        andpd     %xmm4, %xmm0
        andpd     %xmm4, %xmm1
        orpd      %xmm6, %xmm0
        orpd      %xmm7, %xmm1
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_9.0.2:
        subl      $880, %edx
        mulsd     %xmm3, %xmm0
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_13.0.2
        jmp       .L_2TAG_PACKET_10.0.2
.L_2TAG_PACKET_11.0.2:
        addl      $880, %edx
        mulsd     %xmm3, %xmm1
        pextrw    $3, %xmm1, %eax
        andl      $32752, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_14.0.2
        jmp       .L_2TAG_PACKET_12.0.2
.L_2TAG_PACKET_7.0.2:
        movd      %xmm0, %edx
        movq      %xmm0, %xmm2
        psrlq     $32, %xmm2
        movd      %xmm2, %ecx
        andl      $1048575, %ecx
        orl       %edx, %ecx
        cmpl      $0, %ecx
        jne       .L_2TAG_PACKET_15.0.2
        psrlq     $63, %xmm0
        psllq     $63, %xmm0
        cmpl      $32752, %eax
        jae       .L_2TAG_PACKET_16.0.2
        movapd    pi2_table(%rip), %xmm5
        pshufd    $238, %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        orpd      %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_15.0.2:
        addsd     %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_16.0.2:
        movd      %xmm1, %eax
        movq      %xmm1, %xmm2
        psrlq     $32, %xmm2
        movd      %xmm2, %ecx
        movl      $-2147483648, %edx
        andl      %ecx, %edx
        andl      $1048575, %ecx
        orl       %eax, %ecx
        cmpl      $0, %ecx
        jne       .L_2TAG_PACKET_17.0.2
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_18.0.2
        movapd    pi4_table(%rip), %xmm5
        pshufd    $238, %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        orpd      %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_17.0.2:
        movq      %xmm1, %xmm0
        addsd     %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_18.0.2:
        movapd    pi4_table(%rip), %xmm5
        movapd    pi2_table(%rip), %xmm6
        addpd     %xmm6, %xmm5
        pshufd    $238, %xmm5, %xmm6
        addpd     %xmm6, %xmm5
        orpd      %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_8.0.2:
        movd      %xmm1, %eax
        movq      %xmm1, %xmm2
        psrlq     $32, %xmm2
        movd      %xmm2, %ecx
        movl      $-2147483648, %edx
        andl      %ecx, %edx
        andl      $1048575, %ecx
        orl       %eax, %ecx
        cmpl      $0, %ecx
        jne       .L_2TAG_PACKET_17.0.2
        psrlq     $63, %xmm0
        psllq     $63, %xmm0
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_19.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_19.0.2:
        movapd    pi_table(%rip), %xmm5
        pshufd    $238, %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        orpd      %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_13.0.2:
        pextrw    $3, %xmm1, %edx
        andl      $32768, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_20.0.2
        movapd    pi_table(%rip), %xmm5
        pshufd    $238, %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        comisd    %xmm0, %xmm1
        orpd      %xmm5, %xmm0
        je        .L_2TAG_PACKET_21.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_20.0.2:
        comisd    %xmm0, %xmm1
        je        .L_2TAG_PACKET_21.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_14.0.2:
        movapd    pi2_table(%rip), %xmm5
        psrlq     $63, %xmm0
        psllq     $63, %xmm0
        pshufd    $238, %xmm5, %xmm4
        addsd     %xmm4, %xmm5
        orpd      %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_21.0.2:
        movq      %xmm0, 16(%rsp)
..B1.3:
        movq      16(%rsp), %xmm0
.L_2TAG_PACKET_22.0.2:
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atan2,@function
	.size	atan2,.-atan2
	.data
# -- End  atan2
	.section .rodata, "a"
	.align 16
	.align 16
a2:
	.long	2006262985
	.long	1069310863
	.long	2358449471
	.long	3217342131
	.type	a2,@object
	.size	a2,16
	.align 16
b2:
	.long	3845454352
	.long	1069952297
	.long	2829679149
	.long	1073771565
	.type	b2,@object
	.size	b2,16
	.align 16
P_TBL:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1413754136
	.long	1074340347
	.long	856972295
	.long	1017226790
	.long	1413754136
	.long	3221823995
	.long	856972295
	.long	3164710438
	.type	P_TBL,@object
	.size	P_TBL,64
	.align 16
SGN_TBL:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	0
	.type	SGN_TBL,@object
	.size	SGN_TBL,64
	.align 16
ATAN_TBL:
	.long	3390881280
	.long	1067318733
	.long	1411116779
	.long	1018950063
	.long	2985987840
	.long	1067384211
	.long	2088903695
	.long	1018086027
	.long	3148445184
	.long	1067449685
	.long	2044163806
	.long	1017271335
	.long	3667629184
	.long	1067515494
	.long	2353092775
	.long	1019967309
	.long	1546568832
	.long	1067580954
	.long	611991315
	.long	1017602584
	.long	3815996800
	.long	1067646404
	.long	466038598
	.long	1019686426
	.long	4050241920
	.long	1067711845
	.long	3265026328
	.long	1019626952
	.long	120454912
	.long	1067777277
	.long	1542207696
	.long	1020155608
	.long	2784639744
	.long	1067842697
	.long	3883834623
	.long	1018602870
	.long	1328010624
	.long	1067908107
	.long	1791097456
	.long	1019053126
	.long	2217794048
	.long	1067973505
	.long	551619938
	.long	1018494194
	.long	3333520000
	.long	1068038891
	.long	2390331823
	.long	1019033022
	.long	2557052032
	.long	1068104265
	.long	2423976108
	.long	1019728674
	.long	2067649536
	.long	1068169626
	.long	3757397745
	.long	1018672362
	.long	4047094784
	.long	1068234973
	.long	481613184
	.long	1019275104
	.long	2089853184
	.long	1068300307
	.long	1733914374
	.long	1020124677
	.long	2678003840
	.long	1068365626
	.long	1373600282
	.long	1013935474
	.long	3706496128
	.long	1068430930
	.long	1000610902
	.long	1019673285
	.long	3073179008
	.long	1068496219
	.long	1497143008
	.long	1019900342
	.long	2803716736
	.long	1068562846
	.long	1476677416
	.long	1019444094
	.long	3204984128
	.long	1068628077
	.long	1192335905
	.long	1018748628
	.long	831146624
	.long	1068693273
	.long	2733586224
	.long	1018823295
	.long	243029376
	.long	1068758431
	.long	950106081
	.long	1019046675
	.long	1735561920
	.long	1068823549
	.long	3546440856
	.long	1020104712
	.long	1339217792
	.long	1068888626
	.long	3028812387
	.long	1019818321
	.long	3706342144
	.long	1068953659
	.long	3814564029
	.long	1017763871
	.long	637726976
	.long	1069018648
	.long	3584007699
	.long	1017976868
	.long	1148779264
	.long	1069083589
	.long	2282532133
	.long	1019483954
	.long	1406131392
	.long	1069148481
	.long	1547359113
	.long	1019786342
	.long	1908875904
	.long	1069213322
	.long	1315508410
	.long	1020009473
	.long	3194947520
	.long	1069278110
	.long	3845393201
	.long	1015803761
	.long	1547487744
	.long	1069342844
	.long	3863107865
	.long	1019810104
	.long	1881061952
	.long	1069407521
	.long	4288343548
	.long	1019687581
	.long	563086336
	.long	1069472140
	.long	2582230241
	.long	1020099350
	.long	2594975552
	.long	1069536698
	.long	2306443764
	.long	1019667244
	.long	3438545024
	.long	1069606573
	.long	957455549
	.long	1015587735
	.long	4211357472
	.long	1069670906
	.long	2611778754
	.long	1017877214
	.long	3002835424
	.long	1069735101
	.long	235580458
	.long	1020211685
	.long	3905315424
	.long	1069799150
	.long	3630647617
	.long	1018736849
	.long	2849656576
	.long	1069863047
	.long	2412165062
	.long	1019693004
	.long	507429472
	.long	1069926785
	.long	1397750723
	.long	1018412717
	.long	2307470272
	.long	1069990356
	.long	1796470904
	.long	1019796181
	.long	1271814912
	.long	1070053755
	.long	189761565
	.long	1016149115
	.long	3800538144
	.long	1070116974
	.long	2524871582
	.long	1018263353
	.long	3916203552
	.long	1070180008
	.long	127848658
	.long	1017672664
	.long	457192032
	.long	1070242851
	.long	4020400938
	.long	1019823010
	.long	1385324704
	.long	1070305495
	.long	564511179
	.long	1016079094
	.long	2322869856
	.long	1070367935
	.long	2347103319
	.long	1018927760
	.long	3743438624
	.long	1070430165
	.long	877973862
	.long	1019638162
	.long	2392255552
	.long	1070492180
	.long	2432782267
	.long	1018872629
	.long	4180443328
	.long	1070553973
	.long	3102990015
	.long	1020093101
	.long	2547540832
	.long	1070636485
	.long	3877738253
	.long	1017300424
	.long	2735468912
	.long	1070697461
	.long	2446470256
	.long	1019235378
	.long	542633792
	.long	1070757943
	.long	583606328
	.long	1018624131
	.long	923265984
	.long	1070817911
	.long	1793926708
	.long	1019714161
	.long	918728448
	.long	1070877348
	.long	3726463586
	.long	1019433296
	.long	2572275008
	.long	1070936237
	.long	1845354238
	.long	1019459238
	.long	50974688
	.long	1070994564
	.long	983808064
	.long	1016685418
	.long	1105518320
	.long	1071052313
	.long	2357496692
	.long	1015139882
	.long	1264825328
	.long	1071109472
	.long	2244129354
	.long	1019046344
	.long	961157920
	.long	1071166029
	.long	3124185339
	.long	1018541776
	.long	1162701584
	.long	1071221973
	.long	1279780948
	.long	1019268918
	.long	3284935664
	.long	1071277294
	.long	2670033472
	.long	1019833744
	.long	497441888
	.long	1071331985
	.long	1032737410
	.long	1019795212
	.long	3377383904
	.long	1071386036
	.long	2356897182
	.long	1020205553
	.long	1126962000
	.long	1071439443
	.long	3723724586
	.long	1015212418
	.long	90291008
	.long	1071492199
	.long	4178672431
	.long	1020186971
	.long	190059536
	.long	1071595741
	.long	1763589807
	.long	1019162163
	.long	2497392840
	.long	1071670654
	.long	3036997041
	.long	1020204325
	.long	2616971944
	.long	1071719773
	.long	300151069
	.long	1017041957
	.long	2883518128
	.long	1071767563
	.long	2203981414
	.long	1019190108
	.long	1496354352
	.long	1071814030
	.long	332287966
	.long	1016846435
	.long	483276728
	.long	1071859184
	.long	653845024
	.long	1018830914
	.long	3097401072
	.long	1071903039
	.long	1514746408
	.long	1019278972
	.long	2737217248
	.long	1071945615
	.long	1358845067
	.long	1017268275
	.long	2072577560
	.long	1071986933
	.long	3041024735
	.long	1019929672
	.long	2266405656
	.long	1072027017
	.long	1271261130
	.long	1012925070
	.long	958652544
	.long	1072065894
	.long	2158017058
	.long	1019955372
	.long	3312993840
	.long	1072103591
	.long	765809169
	.long	1019114443
	.long	3177001304
	.long	1072140139
	.long	144180084
	.long	1019822186
	.long	3071642184
	.long	1072175568
	.long	4004602424
	.long	1019420740
	.long	4283953648
	.long	1072209909
	.long	1511950430
	.long	1020176966
	.long	1413754136
	.long	1072243195
	.long	856972295
	.long	1015129638
	.long	4073202944
	.long	1072306725
	.long	4068194804
	.long	1019714860
	.long	946117760
	.long	1072366415
	.long	694980733
	.long	1020150135
	.long	3980632032
	.long	1072422512
	.long	1313251280
	.long	1019948709
	.long	1468297112
	.long	1072475260
	.long	330111143
	.long	1019809198
	.long	3478063816
	.long	1072524887
	.long	2930067044
	.long	1017784081
	.long	1153979856
	.long	1072571613
	.long	2225786102
	.long	1017634481
	.long	2089828808
	.long	1072615641
	.long	474621367
	.long	1017043414
	.long	3531732632
	.long	1072657163
	.long	2276396220
	.long	1018757240
	.long	775214612
	.long	1072694803
	.long	3209744818
	.long	1019963015
	.long	662307284
	.long	1072713319
	.long	1381696763
	.long	1019763781
	.long	1192776652
	.long	1072730830
	.long	3017932994
	.long	1015179769
	.long	744202396
	.long	1072747407
	.long	2073854034
	.long	1019512292
	.long	8337908
	.long	1072763115
	.long	16004448
	.long	1019599514
	.long	3589868768
	.long	1072778013
	.long	1374369804
	.long	1018019237
	.long	121647320
	.long	1072792159
	.long	128481634
	.long	1018115438
	.long	2464923204
	.long	1072805601
	.long	1787331214
	.long	1016798022
	.long	4093304372
	.long	1072830562
	.long	3306868969
	.long	1019384078
	.long	1436891684
	.long	1072853231
	.long	676347266
	.long	1017302183
	.long	1104571840
	.long	1072873890
	.long	2870400285
	.long	1019938149
	.long	2037009832
	.long	1072892781
	.long	2956702105
	.long	1016472908
	.long	3139037960
	.long	1072910111
	.long	916057147
	.long	1018364335
	.long	1826698064
	.long	1072926058
	.long	2171961098
	.long	1019669816
	.long	1353941060
	.long	1072940774
	.long	1722928782
	.long	1019926215
	.long	1803191644
	.long	1072954391
	.long	1547878639
	.long	1020259262
	.long	1092591296
	.long	1072967024
	.long	3070107923
	.long	1018320401
	.long	2205372832
	.long	1072978772
	.long	787328196
	.long	1014621351
	.long	1291577100
	.long	1072989723
	.long	2964757301
	.long	1020242528
	.long	4234512804
	.long	1072999952
	.long	3136030038
	.long	1017522144
	.long	3248069132
	.long	1073009528
	.long	1506192355
	.long	1018050472
	.long	3932628500
	.long	1073018509
	.long	1045823554
	.long	1019946655
	.long	4195697848
	.long	1073026948
	.long	233443322
	.long	1018917447
	.long	2501811452
	.long	1073034892
	.long	901427976
	.long	1017333852
	.long	866379428
	.long	1073049455
	.long	2437443742
	.long	1019678792
	.long	1376865888
	.long	1073062480
	.long	3365790232
	.long	1014547152
	.long	3290094268
	.long	1073074195
	.long	3898947415
	.long	1018683566
	.long	354764884
	.long	1073084787
	.long	3854322404
	.long	1019662058
	.long	3332975496
	.long	1073094406
	.long	3171701655
	.long	1017830922
	.long	1141460088
	.long	1073103181
	.long	3946082701
	.long	1020032019
	.long	745761284
	.long	1073111216
	.long	1347210591
	.long	1019106121
	.long	1673304508
	.long	1073118600
	.long	1760606642
	.long	1017324577
	.long	983388240
	.long	1073125409
	.long	3740651204
	.long	1019514104
	.long	3895509100
	.long	1073131706
	.long	2409629983
	.long	1020069322
	.long	2128523668
	.long	1073137548
	.long	3045605368
	.long	1018579174
	.long	2075485692
	.long	1073142981
	.long	3720571789
	.long	1017557436
	.long	121855976
	.long	1073148047
	.long	2391744767
	.long	1020160645
	.long	4181733780
	.long	1073152780
	.long	995028816
	.long	1019681295
	.long	2887813280
	.long	1073157214
	.long	218733247
	.long	1020003509
	.long	2862180896
	.long	1073161375
	.long	2043806490
	.long	1018602288
	.long	3909375184
	.long	1073168973
	.long	1559903412
	.long	1020103444
	.long	3533966292
	.long	1073175738
	.long	734884149
	.long	1018462962
	.long	3815044608
	.long	1073181799
	.long	3630523428
	.long	1017250093
	.long	739639376
	.long	1073187261
	.long	4167476661
	.long	1020008277
	.long	1068309648
	.long	1073192207
	.long	2110061437
	.long	1019295858
	.long	2350566352
	.long	1073196707
	.long	582596516
	.long	1018568821
	.long	2529520024
	.long	1073200819
	.long	745552787
	.long	1019053165
	.long	1841667508
	.long	1073204591
	.long	3982568700
	.long	1016503327
	.long	2242261080
	.long	1073208063
	.long	3433582258
	.long	1016196763
	.long	715134328
	.long	1073211270
	.long	355901358
	.long	1020087916
	.long	2700735876
	.long	1073214240
	.long	3640957736
	.long	1019780205
	.long	141607580
	.long	1073217000
	.long	2488245051
	.long	1020262395
	.long	287934404
	.long	1073219570
	.long	2392691085
	.long	1019883292
	.long	2363373988
	.long	1073221969
	.long	4194561737
	.long	1019237447
	.long	3829340424
	.long	1073224214
	.long	429455526
	.long	1019490975
	.long	1988805928
	.long	1073226320
	.long	3029848706
	.long	1018104889
	.long	1647572320
	.long	1073230161
	.long	10289938
	.long	1017394880
	.long	3988000624
	.long	1073233576
	.long	1957559169
	.long	1019434816
	.long	4263843944
	.long	1073236633
	.long	204710264
	.long	1019908761
	.long	663197724
	.long	1073239386
	.long	1921757578
	.long	1019778948
	.long	3560800700
	.long	1073241876
	.long	3994348896
	.long	1019230192
	.long	2441785656
	.long	1073244141
	.long	871468611
	.long	1014800505
	.long	3277400272
	.long	1073246209
	.long	4092218139
	.long	1020040842
	.long	3951990120
	.long	1073248105
	.long	4276546478
	.long	1019763677
	.long	2737338540
	.long	1073249850
	.long	252776012
	.long	1018794951
	.long	1511361316
	.long	1073251461
	.long	3119653999
	.long	1018514803
	.long	3969162516
	.long	1073252952
	.long	1037069016
	.long	1016792900
	.long	413985240
	.long	1073254338
	.long	4110171432
	.long	1020001345
	.long	3681283576
	.long	1073255627
	.long	1463092818
	.long	1020260354
	.long	3146455488
	.long	1073256831
	.long	1031209123
	.long	1016554799
	.long	95214512
	.long	1073257958
	.long	1373808632
	.long	1019493031
	.long	4250240828
	.long	1073259013
	.long	3891047882
	.long	1020108730
	.long	1413754136
	.long	1073291771
	.long	856972295
	.long	1016178214
	.type	ATAN_TBL,@object
	.size	ATAN_TBL,2624
	.align 16
P_TBL2:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	856972295
	.long	1017226790
	.long	1413754136
	.long	1074340347
	.long	856972295
	.long	3164710438
	.long	1413754136
	.long	3221823995
	.type	P_TBL2,@object
	.size	P_TBL2,64
	.align 16
SELECT_B:
	.long	0
	.long	0
	.long	4294967295
	.long	4294967295
	.long	0
	.long	0
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	0
	.long	0
	.long	4294967295
	.long	4294967295
	.long	0
	.long	0
	.type	SELECT_B,@object
	.size	SELECT_B,64
	.align 16
SGNMASK:
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.type	SGNMASK,@object
	.size	SGNMASK,16
	.align 16
pi_table:
	.long	1413754136
	.long	1074340347
	.long	856972295
	.long	1017226790
	.type	pi_table,@object
	.size	pi_table,16
	.align 16
pi2_table:
	.long	1413754136
	.long	1073291771
	.long	856972295
	.long	1016178214
	.type	pi2_table,@object
	.size	pi2_table,16
	.align 16
pi4_table:
	.long	1413754136
	.long	1072243195
	.long	856972295
	.long	1015129638
	.type	pi4_table,@object
	.size	pi4_table,16
	.align 4
POW55:
	.long	0
	.long	1130364928
	.type	POW55,@object
	.size	POW55,8
	.align 4
INVEXPMASK:
	.long	4294967295
	.long	2148532223
	.type	INVEXPMASK,@object
	.size	INVEXPMASK,8
	.align 4
EXPMASK:
	.long	0
	.long	1072693248
	.type	EXPMASK,@object
	.size	EXPMASK,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
