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
	.file "exp2_wmt.c"
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
# -- Begin  exp2
	.text
       .align    16,0x90
	.globl exp2
exp2:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 64(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     128(%esp), %xmm0
        stmxcsr   16(%esp)
        movl      16(%esp), %edx
        andl      $-24577, %edx
        cmpl      %edx, 16(%esp)
        jne       .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_1.0.3:
        movsd     2112(%ebx), %xmm1
        movsd     (%ebx), %xmm2
        paddd     %xmm0, %xmm1
        pextrw    $3, %xmm0, %ecx
        andl      $32767, %ecx
        movl      $16527, %edx
        subl      %ecx, %edx
        subl      $15504, %ecx
        orl       %ecx, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        cvtsd2si  %xmm1, %eax
        movq      %xmm1, %xmm0
        addsd     %xmm2, %xmm1
        movapd    2064(%ebx), %xmm6
        subsd     %xmm2, %xmm1
        movapd    2080(%ebx), %xmm7
        subsd     %xmm1, %xmm0
        movsd     2096(%ebx), %xmm2
        unpcklpd  %xmm0, %xmm0
        movapd    %xmm0, %xmm1
        movl      %eax, %ecx
        sarl      $7, %ecx
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        andl      $2032, %eax
        movsd     16(%ebx,%eax), %xmm3
        movsd     24(%ebx,%eax), %xmm4
        addl      $1023, %ecx
        pinsrw    $0, %ecx, %xmm5
        psllq     $52, %xmm5
        mulpd     %xmm0, %xmm6
        mulpd     %xmm0, %xmm0
        orpd      %xmm5, %xmm3
        mulsd     %xmm1, %xmm2
        addpd     %xmm7, %xmm6
        movl      $2046, %edx
        subl      %ecx, %edx
        subl      $60, %ecx
        orl       %ecx, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_4.0.3
        movapd    %xmm3, %xmm7
        mulsd     %xmm0, %xmm3
        mulsd     %xmm6, %xmm0
        unpckhpd  %xmm6, %xmm6
        mulsd     %xmm7, %xmm2
        mulsd     %xmm5, %xmm4
        addsd     %xmm6, %xmm0
        addsd     %xmm4, %xmm2
        mulsd     %xmm3, %xmm0
        addsd     %xmm2, %xmm0
        movl      16(%esp), %eax
        andl      $-24577, %eax
        cmpl      16(%esp), %eax
        je        .L_2TAG_PACKET_5.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_5.0.3:
        addsd     %xmm7, %xmm0
        jmp       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_4.0.3:
        xorpd     %xmm5, %xmm3
        addl      $60, %ecx
        movl      %ecx, %edx
        subl      $1023, %ecx
        movl      %ecx, %eax
        sarl      $1, %ecx
        subl      %ecx, %eax
        addl      $1023, %ecx
        addl      $1023, %eax
        movd      %eax, %xmm7
        psllq     $52, %xmm7
        movl      $1, %eax
        subl      %edx, %eax
        movd      %eax, %xmm5
        movsd     2160(%ebx), %xmm1
        psllq     %xmm5, %xmm1
        orpd      %xmm7, %xmm3
        mulsd     %xmm7, %xmm4
        movapd    %xmm3, %xmm7
        movd      %ecx, %xmm5
        psllq     $52, %xmm5
        andpd     %xmm3, %xmm1
        mulsd     %xmm0, %xmm3
        mulsd     %xmm6, %xmm0
        unpckhpd  %xmm6, %xmm6
        mulsd     %xmm7, %xmm2
        addsd     %xmm6, %xmm0
        addsd     %xmm4, %xmm2
        subsd     %xmm1, %xmm7
        mulsd     %xmm3, %xmm0
        addsd     %xmm2, %xmm0
        addsd     %xmm7, %xmm0
        cmpl      $2047, %edx
        jge       .L_2TAG_PACKET_7.0.3
        mulsd     %xmm5, %xmm1
        mulsd     %xmm5, %xmm0
        movl      16(%esp), %eax
        andl      $-24577, %eax
        cmpl      16(%esp), %eax
        je        .L_2TAG_PACKET_8.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_8.0.3:
        addsd     %xmm1, %xmm0
        pextrw    $3, %xmm0, %eax
        movl      $162, %edx
        andl      $32752, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_9.0.3
        jmp       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_7.0.3:
        movl      16(%esp), %eax
        andl      $-24577, %eax
        cmpl      16(%esp), %eax
        je        .L_2TAG_PACKET_10.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_10.0.3:
        addsd     %xmm1, %xmm0
        mulsd     %xmm5, %xmm0
        pextrw    $3, %xmm0, %eax
        movl      $161, %edx
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_9.0.3
        jmp       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_0.0.3:
        movl      16(%esp), %edx
        andl      $-24577, %edx
        movl      %edx, 24(%esp)
        ldmxcsr   24(%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_9.0.3:
        movsd     %xmm0, (%esp)
        movsd     128(%esp), %xmm0
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_11.0.3
.L_2TAG_PACKET_12.0.3:
        cmpl      $32752, %ecx
        jae       .L_2TAG_PACKET_13.0.3
        movl      132(%esp), %ecx
        cmpl      $-2147483648, %ecx
        jae       .L_2TAG_PACKET_14.0.3
        movl      16(%esp), %eax
        andl      $-24577, %eax
        cmpl      16(%esp), %eax
        je        .L_2TAG_PACKET_15.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_15.0.3:
        movsd     2144(%ebx), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $161, %edx
        jmp       .L_2TAG_PACKET_9.0.3
.L_2TAG_PACKET_14.0.3:
        cmpl      $-1064253440, %ecx
        jbe       .L_2TAG_PACKET_3.0.3
        shrl      $4, %ecx
        subl      $201919680, %ecx
        shrl      $31, %ecx
        xorpd     %xmm0, %xmm0
        movd      %ecx, %xmm0
        movl      16(%esp), %eax
        andl      $-24577, %eax
        cmpl      16(%esp), %eax
        je        .L_2TAG_PACKET_16.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %eax
        andl      $24576, %eax
        orl       %eax, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_16.0.3:
        movsd     2152(%ebx), %xmm1
        mulsd     %xmm1, %xmm1
        movl      $162, %edx
        jmp       .L_2TAG_PACKET_9.0.3
.L_2TAG_PACKET_13.0.3:
        movl      128(%esp), %edx
        movl      132(%esp), %eax
        movl      $2147483647, %ecx
        andl      %eax, %ecx
        cmpl      $2146435072, %ecx
        ja        .L_2TAG_PACKET_17.0.3
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_17.0.3
        cmpl      $2146435072, %eax
        jne       .L_2TAG_PACKET_18.0.3
        movsd     2128(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_19.0.3
.L_2TAG_PACKET_18.0.3:
        movsd     2136(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_19.0.3
.L_2TAG_PACKET_17.0.3:
        movsd     128(%esp), %xmm0
        addsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_19.0.3
.L_2TAG_PACKET_2.0.3:
        addl      $15504, %ecx
        cmpl      $16527, %ecx
        jae       .L_2TAG_PACKET_12.0.3
        movsd     128(%esp), %xmm0
        addsd     2120(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_19.0.3
.L_2TAG_PACKET_6.0.3:
        movsd     %xmm0, 48(%esp)
        fldl      48(%esp)
        jmp       .L_2TAG_PACKET_11.0.3
.L_2TAG_PACKET_19.0.3:
        movsd     %xmm0, 48(%esp)
        fldl      48(%esp)
        movl      16(%esp), %edx
        andl      $-24577, %edx
        cmpl      16(%esp), %edx
        je        .L_2TAG_PACKET_11.0.3
        stmxcsr   24(%esp)
        movl      16(%esp), %edx
        andl      $24576, %edx
        orl       %edx, 24(%esp)
        ldmxcsr   24(%esp)
.L_2TAG_PACKET_11.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	exp2,@function
	.size	exp2,.-exp2
	.data
# -- End  exp2
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
	.long	1127743488
	.long	0
	.long	1127743488
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2851812149
	.long	5693
	.long	2595802551
	.long	1016815913
	.long	1048019041
	.long	11418
	.long	1398474845
	.long	3161559171
	.long	3899555717
	.long	17173
	.long	427280750
	.long	3163595548
	.long	3541402996
	.long	22960
	.long	2759177317
	.long	1015903202
	.long	702412510
	.long	28779
	.long	3803266086
	.long	3163328991
	.long	410360776
	.long	34629
	.long	1269990655
	.long	1013024446
	.long	3402036099
	.long	40510
	.long	405889333
	.long	1016154232
	.long	1828292879
	.long	46424
	.long	1255956746
	.long	1016636974
	.long	728909815
	.long	52370
	.long	383930225
	.long	1016078044
	.long	852742562
	.long	58348
	.long	667253586
	.long	1010842135
	.long	2952712987
	.long	64358
	.long	3293494651
	.long	3161168877
	.long	3490863953
	.long	70401
	.long	960797497
	.long	3163997456
	.long	3228316108
	.long	76477
	.long	3010241991
	.long	3159471380
	.long	2930322912
	.long	82586
	.long	2599499422
	.long	3163762623
	.long	3366293073
	.long	88728
	.long	3119426313
	.long	1015169130
	.long	1014845819
	.long	94904
	.long	3117910645
	.long	3162607681
	.long	948735466
	.long	101113
	.long	3516338027
	.long	3163623459
	.long	3949972341
	.long	107355
	.long	2068408548
	.long	1015962444
	.long	2214878420
	.long	113632
	.long	892270087
	.long	3164164998
	.long	828946858
	.long	119943
	.long	10642492
	.long	1016988014
	.long	586995997
	.long	126288
	.long	41662347
	.long	3163676568
	.long	2288159958
	.long	132667
	.long	2169144468
	.long	1015924597
	.long	2440944790
	.long	139081
	.long	2492769773
	.long	1015196030
	.long	1853186616
	.long	145530
	.long	3066496370
	.long	1016705150
	.long	1337108031
	.long	152014
	.long	3203724452
	.long	1015726421
	.long	1709341917
	.long	158533
	.long	2571168217
	.long	1015201075
	.long	3790955393
	.long	165087
	.long	2352942461
	.long	3164228666
	.long	4112506593
	.long	171677
	.long	2947355221
	.long	1015419624
	.long	3504003472
	.long	178303
	.long	3594001059
	.long	3158379228
	.long	2799960843
	.long	184965
	.long	1423655380
	.long	1016070727
	.long	2839424854
	.long	191663
	.long	1171596163
	.long	1014090255
	.long	171030293
	.long	198398
	.long	3526460132
	.long	1015477354
	.long	4232894513
	.long	205168
	.long	2383938684
	.long	1015717095
	.long	2992903935
	.long	211976
	.long	2218154405
	.long	1016276769
	.long	1603444721
	.long	218821
	.long	1548633640
	.long	3163249902
	.long	926591435
	.long	225703
	.long	3208833761
	.long	3163962090
	.long	1829099622
	.long	232622
	.long	1016661180
	.long	3164509581
	.long	887463927
	.long	239579
	.long	3596744162
	.long	3161842742
	.long	3272845541
	.long	246573
	.long	928852419
	.long	3164536824
	.long	1276261410
	.long	253606
	.long	300981947
	.long	1015732745
	.long	78413852
	.long	260677
	.long	4183226867
	.long	3164065827
	.long	569847338
	.long	267786
	.long	472945272
	.long	3160339305
	.long	3645941911
	.long	274933
	.long	3814685080
	.long	3162621917
	.long	1617004845
	.long	282120
	.long	82804943
	.long	1011391354
	.long	3978100823
	.long	289345
	.long	3513027190
	.long	1016894539
	.long	3049340112
	.long	296610
	.long	3062915824
	.long	1014219171
	.long	4040676318
	.long	303914
	.long	4090609238
	.long	1016712034
	.long	3577096743
	.long	311258
	.long	2951496418
	.long	1014842263
	.long	2583551245
	.long	318642
	.long	3161094195
	.long	1016655067
	.long	1990012071
	.long	326066
	.long	3529070563
	.long	3163861769
	.long	2731501122
	.long	333530
	.long	1774031854
	.long	3163518597
	.long	1453150082
	.long	341035
	.long	498154668
	.long	3162536638
	.long	3395129871
	.long	348580
	.long	4025345434
	.long	3163383964
	.long	917841882
	.long	356167
	.long	18715564
	.long	1016707884
	.long	3566716925
	.long	363794
	.long	1536826855
	.long	1015191009
	.long	3712504873
	.long	371463
	.long	88491948
	.long	1016476236
	.long	2321106615
	.long	379174
	.long	2171176610
	.long	1010584347
	.long	363667784
	.long	386927
	.long	813753949
	.long	1016833785
	.long	3111574537
	.long	394721
	.long	2606161479
	.long	3163808322
	.long	2956612997
	.long	402558
	.long	2118169750
	.long	3163784129
	.long	885834528
	.long	410438
	.long	1973258546
	.long	3163310140
	.long	2186617381
	.long	418360
	.long	2270764083
	.long	3164321289
	.long	3561793907
	.long	426325
	.long	1157054052
	.long	1012938926
	.long	1719614413
	.long	434334
	.long	330458197
	.long	3164331316
	.long	1963711167
	.long	442386
	.long	1744767756
	.long	3161622870
	.long	1013258799
	.long	450482
	.long	1748797610
	.long	3161177658
	.long	4182873220
	.long	458621
	.long	629542646
	.long	3163044879
	.long	3907805044
	.long	466805
	.long	2257091225
	.long	3162598983
	.long	1218806132
	.long	475034
	.long	1818613051
	.long	3163597017
	.long	1447192521
	.long	483307
	.long	1462857171
	.long	3163563097
	.long	1339972927
	.long	491625
	.long	167908908
	.long	1016620728
	.long	1944781191
	.long	499988
	.long	3993278767
	.long	3162772855
	.long	19972402
	.long	508397
	.long	3507899861
	.long	1017057868
	.long	919555682
	.long	516851
	.long	3121969534
	.long	1013996802
	.long	1413356050
	.long	525351
	.long	1651349290
	.long	3163716742
	.long	2571947539
	.long	533897
	.long	3558159063
	.long	3164425245
	.long	1176749997
	.long	542490
	.long	2738998779
	.long	3163084420
	.long	2604962541
	.long	551129
	.long	2614425274
	.long	3164587768
	.long	3649726105
	.long	559815
	.long	4085036346
	.long	1016698050
	.long	1110089947
	.long	568549
	.long	1451641638
	.long	1016523249
	.long	380978316
	.long	577330
	.long	854188970
	.long	3161511262
	.long	2568320822
	.long	586158
	.long	2732824428
	.long	1015401491
	.long	194117574
	.long	595035
	.long	777528611
	.long	3164460665
	.long	2966275557
	.long	603959
	.long	2176155323
	.long	3160891335
	.long	3418903055
	.long	612932
	.long	2527457337
	.long	3161869180
	.long	2682146384
	.long	621954
	.long	2082178512
	.long	3164411995
	.long	1892288442
	.long	631025
	.long	2446255666
	.long	3163648957
	.long	2191782032
	.long	640145
	.long	2960257726
	.long	1014791238
	.long	434316067
	.long	649315
	.long	2028358766
	.long	1014506698
	.long	2069751141
	.long	658534
	.long	1562170674
	.long	3163773257
	.long	3964284211
	.long	667803
	.long	2111583915
	.long	1016475740
	.long	2990417245
	.long	677123
	.long	3683467745
	.long	3164417902
	.long	321958744
	.long	686494
	.long	3401933766
	.long	1016843134
	.long	1434058175
	.long	695915
	.long	251133233
	.long	1016134345
	.long	3218338682
	.long	705387
	.long	3404164304
	.long	3163525684
	.long	2572866477
	.long	714911
	.long	878562433
	.long	1016570317
	.long	697153126
	.long	724487
	.long	1283515428
	.long	3164331765
	.long	3092190715
	.long	734114
	.long	814012167
	.long	3160571998
	.long	2380618042
	.long	743794
	.long	3149557219
	.long	3164369375
	.long	4076559943
	.long	753526
	.long	2119478330
	.long	3161806927
	.long	815859274
	.long	763312
	.long	240396590
	.long	3164536019
	.long	2420883922
	.long	773150
	.long	2049810052
	.long	1015168464
	.long	1540824585
	.long	783042
	.long	1064017010
	.long	3164536266
	.long	3716502172
	.long	792987
	.long	2303740125
	.long	1015091301
	.long	1610600570
	.long	802987
	.long	3766732298
	.long	1016808759
	.long	777507147
	.long	813041
	.long	4282924204
	.long	1016236109
	.long	2483480501
	.long	823149
	.long	1216371780
	.long	1014082748
	.long	3706687593
	.long	833312
	.long	3521726939
	.long	1014301643
	.long	1432208378
	.long	843531
	.long	1401068914
	.long	3163412539
	.long	1242007932
	.long	853805
	.long	1132034716
	.long	3164388407
	.long	135105010
	.long	864135
	.long	1906148727
	.long	3164424315
	.long	3707479175
	.long	874520
	.long	3613079302
	.long	1015213314
	.long	382305176
	.long	884963
	.long	2347622376
	.long	3163627201
	.long	64696965
	.long	895462
	.long	1768797490
	.long	1016865536
	.long	4076975200
	.long	906017
	.long	2029000898
	.long	1016257111
	.long	863738719
	.long	916631
	.long	1326992219
	.long	3163661773
	.long	351641897
	.long	927302
	.long	2172261526
	.long	3164059175
	.long	3884662774
	.long	938030
	.long	2158611599
	.long	1015258761
	.long	4224142467
	.long	948817
	.long	3389820385
	.long	1016255778
	.long	2728693978
	.long	959663
	.long	396109971
	.long	3164511267
	.long	764307441
	.long	970568
	.long	3021057420
	.long	3164378099
	.long	3999357479
	.long	981531
	.long	2258941616
	.long	1016973300
	.long	929806999
	.long	992555
	.long	3205336643
	.long	1016308133
	.long	1533953344
	.long	1003638
	.long	769171850
	.long	1016714209
	.long	2912730644
	.long	1014781
	.long	3490067721
	.long	3164453650
	.long	2174652632
	.long	1025985
	.long	4087714590
	.long	1015498835
	.long	730821105
	.long	1037250
	.long	2523232743
	.long	1013115764
	.long	3884607281
	.long	1025890431
	.long	3607404735
	.long	1046244104
	.long	1874480759
	.long	1036235435
	.long	4286760334
	.long	1055834045
	.long	4277811695
	.long	1064709698
	.long	0
	.long	0
	.long	0
	.long	7340032
	.long	0
	.long	1072693248
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	4294967295
	.long	2146435071
	.long	0
	.long	508559360
	.long	4294967295
	.long	4294967295
	.type	static_const_table,@object
	.size	static_const_table,2168
	.data
	.section .note.GNU-stack, ""
# End
