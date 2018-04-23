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
	.file "log_gen.c"
	.text
..TXTST0:
# -- Begin  log
	.text
       .align    16,0x90
	.globl log
log:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        movq      $0x3ff0000000000000, %rax
        movd      %rax, %xmm2
        movq      $0x77f0000000000000, %rdx
        movd      %rdx, %xmm3
        movl      $32768, %ecx
        movd      %rcx, %xmm4
        movq      $0xffffe00000000000, %r8
        movd      %r8, %xmm5
        movq      %xmm0, %xmm1
        pextrw    $3, %xmm0, %eax
        orpd      %xmm2, %xmm0
        movl      $16352, %ecx
        psrlq     $27, %xmm0
        lea       L_tbl(%rip), %r11
        psrld     $2, %xmm0
        rcpps     %xmm0, %xmm0
        psllq     $12, %xmm1
        pshufd    $228, %xmm5, %xmm6
        psrlq     $12, %xmm1
        subl      $16, %eax
        cmpl      $32736, %eax
        jae       .L_2TAG_PACKET_0.0.2
.L_2TAG_PACKET_1.0.2:
        paddd     %xmm4, %xmm0
        orpd      %xmm3, %xmm1
        movd      %xmm0, %edx
        psllq     $29, %xmm0
        andpd     %xmm1, %xmm5
        andpd     %xmm6, %xmm0
        subsd     %xmm5, %xmm1
        mulpd     %xmm0, %xmm5
        andl      $32752, %eax
        subl      %ecx, %eax
        cvtsi2sd  %eax, %xmm7
        mulsd     %xmm0, %xmm1
        movq      log2(%rip), %xmm6
        movapd    coeff(%rip), %xmm3
        subsd     %xmm2, %xmm5
        andl      $16711680, %edx
        shrl      $12, %edx
        movapd    (%r11,%rdx), %xmm0
        movapd    16+coeff(%rip), %xmm4
        addsd     %xmm5, %xmm1
        movapd    32+coeff(%rip), %xmm2
        mulsd     %xmm7, %xmm6
        movapd    %xmm1, %xmm5
        unpcklpd  %xmm1, %xmm5
        mulsd     8+log2(%rip), %xmm7
        mulsd     %xmm1, %xmm3
        addsd     %xmm6, %xmm0
        mulpd     %xmm5, %xmm4
        mulpd     %xmm5, %xmm5
        movapd    %xmm0, %xmm6
        unpcklpd  %xmm0, %xmm6
        addsd     %xmm1, %xmm0
        addpd     %xmm2, %xmm4
        mulpd     %xmm5, %xmm3
        subsd     %xmm0, %xmm6
        mulsd     %xmm1, %xmm4
        pshufd    $238, %xmm0, %xmm2
        addsd     %xmm6, %xmm1
        mulsd     %xmm5, %xmm5
        addsd     %xmm2, %xmm7
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
        movq      %xmm0, %xmm1
        pextrw    $3, %xmm0, %eax
        orpd      %xmm2, %xmm0
        psrlq     $27, %xmm0
        movl      $18416, %ecx
        psrld     $2, %xmm0
        rcpps     %xmm0, %xmm0
        psllq     $12, %xmm1
        pshufd    $228, %xmm5, %xmm6
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
        movl      $3, 16(%rsp)
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_7.0.2:
        xorpd     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        movl      $49136, %eax
        pinsrw    $3, %eax, %xmm0
        divsd     %xmm1, %xmm0
        movl      $2, 16(%rsp)
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
	.type	log,@function
	.size	log,.-log
	.data
# -- End  log
	.section .rodata, "a"
	.align 16
	.align 16
L_tbl:
	.long	4277811200
	.long	1072049730
	.long	2479318832
	.long	1026487127
	.long	2854492160
	.long	1072033410
	.long	215631550
	.long	1025638968
	.long	1547061248
	.long	1072017216
	.long	2886781435
	.long	1026423395
	.long	649825280
	.long	1072001146
	.long	4281533405
	.long	1024038923
	.long	646346752
	.long	1071985198
	.long	1562735921
	.long	1023790276
	.long	2203734016
	.long	1071969370
	.long	1838397691
	.long	3173936209
	.long	1872169984
	.long	1071953661
	.long	3981202460
	.long	1022325013
	.long	669557760
	.long	1071938069
	.long	4182597802
	.long	3173174122
	.long	4076413952
	.long	1071922591
	.long	1209029111
	.long	3170736207
	.long	556125184
	.long	1071907228
	.long	821086028
	.long	3173437049
	.long	204914688
	.long	1071891976
	.long	2097025986
	.long	3171071798
	.long	387545088
	.long	1071876834
	.long	3142936996
	.long	3173092218
	.long	2912783360
	.long	1071861800
	.long	2502420140
	.long	1024505919
	.long	1144260608
	.long	1071846874
	.long	3315658140
	.long	3173469843
	.long	1471209472
	.long	1071832053
	.long	129621009
	.long	3172443877
	.long	1829683200
	.long	1071817336
	.long	3885467693
	.long	1025535275
	.long	288676864
	.long	1071802722
	.long	86139472
	.long	3171639793
	.long	3636378624
	.long	1071788208
	.long	1850238587
	.long	1024654342
	.long	1606817792
	.long	1071773795
	.long	3388899795
	.long	3173675586
	.long	1236164608
	.long	1071759480
	.long	3983599207
	.long	1020046558
	.long	1089616896
	.long	1071745262
	.long	4171974224
	.long	1024773198
	.long	4143093760
	.long	1071731139
	.long	2727587401
	.long	3173965207
	.long	600267776
	.long	1071717112
	.long	3147685042
	.long	3173353031
	.long	2249313280
	.long	1071703177
	.long	125835074
	.long	1025255832
	.long	3805303808
	.long	1071689334
	.long	2289991207
	.long	1025460331
	.long	87278592
	.long	1071675583
	.long	1106114045
	.long	1025933602
	.long	3195405312
	.long	1071661920
	.long	3885316576
	.long	3171206239
	.long	3853649920
	.long	1071648346
	.long	2977069852
	.long	3171236771
	.long	2944026624
	.long	1071625048
	.long	1008093493
	.long	1023444474
	.long	3993180160
	.long	1071598247
	.long	1862355595
	.long	1024642533
	.long	1454641152
	.long	1071571617
	.long	1514603089
	.long	1026500596
	.long	3286085632
	.long	1071545154
	.long	1400028424
	.long	3173279056
	.long	438773760
	.long	1071518858
	.long	120727864
	.long	3172148914
	.long	1212979200
	.long	1071492725
	.long	1625055594
	.long	3172901933
	.long	1189017600
	.long	1071466754
	.long	3920062376
	.long	1025727407
	.long	403064832
	.long	1071440943
	.long	1053271728
	.long	3171391427
	.long	3343210496
	.long	1071415289
	.long	3243395502
	.long	3173627613
	.long	1765777408
	.long	1071389792
	.long	2145968512
	.long	1026354304
	.long	461430784
	.long	1071364449
	.long	4094322285
	.long	1026021467
	.long	71706624
	.long	1071339258
	.long	763632021
	.long	1024496933
	.long	1380503552
	.long	1071314217
	.long	1383547992
	.long	3173088453
	.long	1015732224
	.long	1071289325
	.long	3198646877
	.long	1025390322
	.long	35977216
	.long	1071264580
	.long	2141026805
	.long	1025754693
	.long	3927306240
	.long	1071239979
	.long	282116272
	.long	3173394334
	.long	1125341184
	.long	1071215523
	.long	2768427504
	.long	3172279059
	.long	1666971648
	.long	1071191208
	.long	786837629
	.long	3172427445
	.long	2827694080
	.long	1071167033
	.long	3857122416
	.long	3173014241
	.long	2003683328
	.long	1071142997
	.long	859010954
	.long	1026545007
	.long	1004017664
	.long	1071119098
	.long	3356644970
	.long	3173458064
	.long	1753020416
	.long	1071095334
	.long	788338552
	.long	1026157693
	.long	1992718336
	.long	1071071704
	.long	1239179443
	.long	1026394889
	.long	3870234624
	.long	1071048206
	.long	2082614663
	.long	1024926053
	.long	1050437632
	.long	1071024840
	.long	660007840
	.long	1025548499
	.long	188395520
	.long	1071001603
	.long	3878792704
	.long	3173889571
	.long	3747176448
	.long	1070978493
	.long	144991708
	.long	3171552042
	.long	1405669376
	.long	1070955511
	.long	3999088879
	.long	1025486317
	.long	121151488
	.long	1070932654
	.long	2170865497
	.long	1026473584
	.long	2652319744
	.long	1070909920
	.long	453695652
	.long	3173916809
	.long	3262236672
	.long	1070887309
	.long	157800053
	.long	3173984206
	.long	601221120
	.long	1070864820
	.long	3968917661
	.long	1023992886
	.long	1999843328
	.long	1070842450
	.long	3053895004
	.long	1024998228
	.long	1992167424
	.long	1070820199
	.long	2968614856
	.long	1024552653
	.long	3788726272
	.long	1070798065
	.long	3542170808
	.long	3173573242
	.long	2094829568
	.long	1070776048
	.long	1246758132
	.long	1026202874
	.long	288675840
	.long	1070754146
	.long	3747328950
	.long	1026331585
	.long	1829681152
	.long	1070732357
	.long	3125197546
	.long	1024100318
	.long	1666869248
	.long	1070710681
	.long	1363656119
	.long	1026336493
	.long	3417110528
	.long	1070689116
	.long	4154791553
	.long	1026267853
	.long	2183653376
	.long	1070667662
	.long	1671819292
	.long	3173785870
	.long	1734434816
	.long	1070646317
	.long	373091049
	.long	1025972363
	.long	1615681536
	.long	1070625080
	.long	384650897
	.long	1022926043
	.long	1445382144
	.long	1070603950
	.long	344320330
	.long	3172397196
	.long	1823715328
	.long	1070569756
	.long	3389841200
	.long	1025231852
	.long	3839688704
	.long	1070527917
	.long	1706790417
	.long	3167363349
	.long	4293332992
	.long	1070486286
	.long	1614935088
	.long	1019351591
	.long	2966720512
	.long	1070444861
	.long	4145393717
	.long	3173711658
	.long	4066729984
	.long	1070403639
	.long	1974925028
	.long	3171437182
	.long	3337621504
	.long	1070362619
	.long	3314953170
	.long	3169971314
	.long	943448064
	.long	1070321799
	.long	1498682038
	.long	3173862340
	.long	1465634816
	.long	1070281176
	.long	1319952810
	.long	3171693965
	.long	1015734272
	.long	1070240749
	.long	1347821929
	.long	3173544515
	.long	118001664
	.long	1070200516
	.long	1751482746
	.long	1026134093
	.long	3707174912
	.long	1070160474
	.long	1486946159
	.long	1023930920
	.long	3946381312
	.long	1070120623
	.long	2867408081
	.long	3171368276
	.long	1699848192
	.long	1070080961
	.long	2590187139
	.long	1025379803
	.long	2235846656
	.long	1070041485
	.long	1888568069
	.long	3172754960
	.long	2339729408
	.long	1070002194
	.long	3852214753
	.long	3173323149
	.long	3196850176
	.long	1069963086
	.long	742141560
	.long	1025101707
	.long	1800683520
	.long	1069924160
	.long	3949500444
	.long	3172102179
	.long	3835801600
	.long	1069885413
	.long	3848895943
	.long	1025913832
	.long	2201202688
	.long	1069846845
	.long	1425913464
	.long	1025868665
	.long	2778279936
	.long	1069808453
	.long	2120889677
	.long	3173831128
	.long	2954203136
	.long	1069770236
	.long	592147081
	.long	1019621288
	.long	210141184
	.long	1069732193
	.long	3414275233
	.long	1023647084
	.long	709476352
	.long	1069694321
	.long	2413027164
	.long	1024462115
	.long	2116284416
	.long	1069656619
	.long	1144559924
	.long	1026336654
	.long	2183651328
	.long	1069619086
	.long	3459057650
	.long	1025634168
	.long	3047047168
	.long	1069581720
	.long	1879674924
	.long	3173508573
	.long	970711040
	.long	1069541521
	.long	1335954173
	.long	3173332182
	.long	2198478848
	.long	1069467449
	.long	2951103968
	.long	3173892200
	.long	1669611520
	.long	1069393703
	.long	531044147
	.long	1025149248
	.long	29114368
	.long	1069320280
	.long	3327831251
	.long	1025918673
	.long	2376949760
	.long	1069247176
	.long	737634533
	.long	3172176000
	.long	1085390848
	.long	1069174390
	.long	3108243400
	.long	3171828406
	.long	1566130176
	.long	1069101918
	.long	985483226
	.long	1025708380
	.long	792780800
	.long	1069029758
	.long	4184866295
	.long	1024426204
	.long	183156736
	.long	1068957907
	.long	2845699378
	.long	1022107277
	.long	1301782528
	.long	1068886362
	.long	1012735262
	.long	3173804294
	.long	1562411008
	.long	1068815121
	.long	2197086703
	.long	3170187813
	.long	2815549440
	.long	1068744181
	.long	2782613207
	.long	1026345054
	.long	2756124672
	.long	1068673540
	.long	2929486205
	.long	3173037800
	.long	3511050240
	.long	1068603195
	.long	1443733147
	.long	3173331549
	.long	3047047168
	.long	1068533144
	.long	1879674924
	.long	3172459997
	.long	3221667840
	.long	1068427825
	.long	1338588027
	.long	3171815742
	.long	3453861888
	.long	1068288883
	.long	1205348359
	.long	3172624626
	.long	3506110464
	.long	1068150514
	.long	893105198
	.long	1025571866
	.long	346013696
	.long	1068012714
	.long	3495569021
	.long	3172563349
	.long	4074029056
	.long	1067875476
	.long	3961106338
	.long	3171065595
	.long	3559784448
	.long	1067738798
	.long	1975385384
	.long	3173783155
	.long	797769728
	.long	1067602675
	.long	3760305787
	.long	1026047642
	.long	2313633792
	.long	1067467101
	.long	1559353171
	.long	1023480256
	.long	3960766464
	.long	1067213778
	.long	1067365107
	.long	1025865926
	.long	684261376
	.long	1066944805
	.long	844762164
	.long	3173687482
	.long	630718464
	.long	1066676905
	.long	2458269694
	.long	1024033081
	.long	1486061568
	.long	1066410070
	.long	115537874
	.long	3173243995
	.long	2743664640
	.long	1065886792
	.long	3665098304
	.long	3173471607
	.long	1971912704
	.long	1065357333
	.long	2577214440
	.long	3171993451
	.long	1498939392
	.long	1064306693
	.long	3409036923
	.long	1025599151
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	L_tbl,@object
	.size	L_tbl,2064
	.align 16
log2:
	.long	4277811200
	.long	1067855426
	.long	2479318832
	.long	1022292823
	.type	log2,@object
	.size	log2,16
	.align 16
coeff:
	.long	2454267026
	.long	1069697316
	.long	0
	.long	3218079744
	.long	1030730101
	.long	3217380702
	.long	1431655765
	.long	1070945621
	.long	2576980378
	.long	1070176665
	.long	0
	.long	3219128320
	.type	coeff,@object
	.size	coeff,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
