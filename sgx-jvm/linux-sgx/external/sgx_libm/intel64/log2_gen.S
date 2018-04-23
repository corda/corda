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
	.file "log2_gen.c"
	.text
..TXTST0:
# -- Begin  log2
	.text
       .align    16,0x90
	.globl log2
log2:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log2.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        xorpd     %xmm2, %xmm2
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm2
        movl      $1069088768, %ecx
        movd      %rcx, %xmm7
        movapd    HIGHSIGMASK(%rip), %xmm5
        movq      $0x77f0000000000000, %rdx
        movd      %rdx, %xmm3
        movl      $32768, %r9d
        movd      %r9, %xmm4
        movq      %xmm0, %xmm1
        pextrw    $3, %xmm0, %eax
        orpd      %xmm2, %xmm0
        movl      $16352, %ecx
        psrlq     $27, %xmm0
        movq      LOG2_E(%rip), %xmm2
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
        sarl      $4, %eax
        cvtsi2sd  %eax, %xmm7
        mulpd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm1
        movapd    coeff(%rip), %xmm3
        subsd     %xmm2, %xmm5
        andl      $16711680, %edx
        shrl      $12, %edx
        movapd    -912(%r11,%rdx), %xmm0
        movapd    16+coeff(%rip), %xmm4
        addsd     %xmm5, %xmm1
        movapd    32+coeff(%rip), %xmm2
        movq      $0xbf5dabe1161bb241, %r8
        movd      %r8, %xmm6
        movapd    %xmm1, %xmm5
        unpcklpd  %xmm1, %xmm5
        mulsd     %xmm1, %xmm3
        addsd     %xmm7, %xmm0
        mulpd     %xmm5, %xmm4
        mulpd     %xmm5, %xmm5
        addpd     %xmm2, %xmm4
        mulpd     %xmm5, %xmm3
        movapd    %xmm0, %xmm7
        unpcklpd  %xmm0, %xmm7
        addsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm4
        subsd     %xmm0, %xmm7
        mulsd     %xmm1, %xmm6
        pshufd    $238, %xmm0, %xmm2
        addsd     %xmm7, %xmm1
        mulsd     %xmm5, %xmm5
        addpd     %xmm3, %xmm4
        addsd     %xmm6, %xmm2
        mulpd     %xmm5, %xmm4
        addsd     %xmm2, %xmm1
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
        movl      $171, 16(%rsp)
        mulsd     %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_7.0.2:
        xorpd     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        movl      $49136, %eax
        pinsrw    $3, %eax, %xmm0
        divsd     %xmm1, %xmm0
        movl      $170, 16(%rsp)
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_3.0.2:
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
        movq      LOG2_E(%rip), %xmm2
        psrld     $2, %xmm0
        rcpps     %xmm0, %xmm0
        psllq     $12, %xmm1
        pshufd    $78, %xmm5, %xmm6
        psrlq     $12, %xmm1
        jmp       .L_2TAG_PACKET_1.0.2
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
	.type	log2,@function
	.size	log2,.-log2
	.data
# -- End  log2
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
LOG2_E:
	.long	0
	.long	1073160192
	.long	370913857
	.long	3210587105
	.type	LOG2_E,@object
	.size	LOG2_E,16
	.align 16
L_tbl:
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	3065110528
	.long	1072676937
	.long	969155058
	.long	1027421928
	.long	3789631488
	.long	1072660714
	.long	1061361078
	.long	1026032326
	.long	2461925376
	.long	1072644578
	.long	1624763829
	.long	3175910818
	.long	3729080320
	.long	1072628527
	.long	1845599454
	.long	1028591843
	.long	3710689280
	.long	1072612561
	.long	3776236642
	.long	1025648432
	.long	2882404352
	.long	1072596679
	.long	3683226528
	.long	3174591570
	.long	1779716096
	.long	1072580880
	.long	1916484889
	.long	3173934989
	.long	996708352
	.long	1072565163
	.long	716999656
	.long	3175673444
	.long	1184837632
	.long	1072549527
	.long	1973599317
	.long	3174949482
	.long	3051761664
	.long	1072533971
	.long	1437348984
	.long	1028530411
	.long	3065225216
	.long	1072518495
	.long	1219343899
	.long	1028525753
	.long	2041864192
	.long	1072503098
	.long	388496599
	.long	3175919551
	.long	851140608
	.long	1072487779
	.long	2822909360
	.long	3173772990
	.long	414302208
	.long	1072472537
	.long	1306322068
	.long	1026235748
	.long	1703325696
	.long	1072457371
	.long	1651655805
	.long	3174368281
	.long	1444937728
	.long	1072442281
	.long	1145646243
	.long	3175573309
	.long	709566464
	.long	1072427266
	.long	3608295147
	.long	3168585112
	.long	615415808
	.long	1072412325
	.long	2957847316
	.long	1027961637
	.long	2327523328
	.long	1072397457
	.long	3266875511
	.long	3175163169
	.long	2761863168
	.long	1072382662
	.long	3329930550
	.long	1027790656
	.long	3174416384
	.long	1072367939
	.long	3349298596
	.long	3172501429
	.long	570335232
	.long	1072353288
	.long	905028281
	.long	3175970581
	.long	588005376
	.long	1072338707
	.long	40558280
	.long	1022275054
	.long	318357504
	.long	1072324196
	.long	3000963593
	.long	3175644723
	.long	1188933632
	.long	1072309754
	.long	1152643439
	.long	3171407768
	.long	373186560
	.long	1072295381
	.long	3440409283
	.long	3174861647
	.long	3674587136
	.long	1072281075
	.long	1985407462
	.long	1026124611
	.long	4051050496
	.long	1072266837
	.long	2734481544
	.long	1026856954
	.long	3089022976
	.long	1072252666
	.long	3365453112
	.long	1028135364
	.long	2412838912
	.long	1072238561
	.long	1203709499
	.long	3175656792
	.long	3683999744
	.long	1072224521
	.long	4237943120
	.long	3176014210
	.long	10571776
	.long	1072210547
	.long	1358548904
	.long	3174161579
	.long	1716314112
	.long	1072196636
	.long	1192519078
	.long	3175777045
	.long	1980272640
	.long	1072182789
	.long	1567013979
	.long	3174472130
	.long	2605957120
	.long	1072169005
	.long	1671600287
	.long	1028158963
	.long	1135812608
	.long	1072155284
	.long	1547709291
	.long	3174179085
	.long	3735490560
	.long	1072141624
	.long	2225392115
	.long	1027056729
	.long	3718443008
	.long	1072128026
	.long	3797315894
	.long	3175978381
	.long	3020144640
	.long	1072114489
	.long	569231379
	.long	3176080301
	.long	3607605248
	.long	1072101012
	.long	2931542967
	.long	1028585043
	.long	3183849472
	.long	1072087595
	.long	1779595699
	.long	1025078612
	.long	3777277952
	.long	1072074237
	.long	3836888569
	.long	3176101128
	.long	3151212544
	.long	1072060938
	.long	4204238866
	.long	1027628024
	.long	3393323008
	.long	1072047697
	.long	3590952967
	.long	1024914744
	.long	2325151744
	.long	1072034514
	.long	2081953065
	.long	1024436445
	.long	2091569152
	.long	1072021388
	.long	1869579321
	.long	3176039801
	.long	570331136
	.long	1072008319
	.long	1169272038
	.long	1028145343
	.long	4256530432
	.long	1071995305
	.long	3897289024
	.long	3171837174
	.long	2492289024
	.long	1071982348
	.long	3892405903
	.long	3175484647
	.long	1826058240
	.long	1071969446
	.long	4079999923
	.long	3175326622
	.long	242364416
	.long	1071956599
	.long	850820968
	.long	3175983841
	.long	46260224
	.long	1071943806
	.long	2164938638
	.long	1028426857
	.long	3567947776
	.long	1071931066
	.long	2011723452
	.long	1028083988
	.long	277430272
	.long	1071918381
	.long	2531435800
	.long	3169601318
	.long	1143848960
	.long	1071905748
	.long	294923185
	.long	1023738369
	.long	4275343360
	.long	1071893167
	.long	2459435756
	.long	1028412742
	.long	3508592640
	.long	1071880639
	.long	503755275
	.long	3173140530
	.long	1293312000
	.long	1071868163
	.long	3333822795
	.long	1026993594
	.long	101961728
	.long	1071855738
	.long	2904005851
	.long	1027338469
	.long	2429362176
	.long	1071843363
	.long	1235892196
	.long	3175679811
	.long	2202386432
	.long	1071831039
	.long	2295142960
	.long	1028565357
	.long	1959501824
	.long	1071818765
	.long	254808788
	.long	1028157588
	.long	4260454400
	.long	1071806540
	.long	1281464327
	.long	3173548784
	.long	3096002560
	.long	1071794365
	.long	2126040068
	.long	3168834094
	.long	1067458560
	.long	1071782239
	.long	957416402
	.long	3171516564
	.long	796413952
	.long	1071770161
	.long	3663270514
	.long	1026937343
	.long	629456896
	.long	1071758131
	.long	2081311685
	.long	1026861414
	.long	3227787264
	.long	1071746148
	.long	325239760
	.long	3175812466
	.long	2681995264
	.long	1071734213
	.long	692600417
	.long	1028316258
	.long	1691656192
	.long	1071722325
	.long	2854288619
	.long	3174069820
	.long	2975059968
	.long	1071710483
	.long	2357806657
	.long	3175896595
	.long	1853362176
	.long	1071686938
	.long	182026883
	.long	3174676055
	.long	4247105536
	.long	1071663574
	.long	1264810745
	.long	1026738550
	.long	671424512
	.long	1071636109
	.long	321222784
	.long	1028039985
	.long	4092968960
	.long	1071590092
	.long	3658154599
	.long	3175856095
	.long	193347584
	.long	1071544424
	.long	723354347
	.long	3173824323
	.long	985587712
	.long	1071499097
	.long	2344377442
	.long	1027857554
	.long	1801297920
	.long	1071454107
	.long	2398211919
	.long	3175430015
	.long	2750758912
	.long	1071409449
	.long	223148174
	.long	1028267694
	.long	118882304
	.long	1071365119
	.long	1958423088
	.long	1026816298
	.long	3531350016
	.long	1071321110
	.long	875023512
	.long	1027316085
	.long	1581752320
	.long	1071277420
	.long	345754833
	.long	1027657950
	.long	473587712
	.long	1071234043
	.long	3024467474
	.long	3175496105
	.long	2533163008
	.long	1071190974
	.long	3279280870
	.long	3173991880
	.long	1902854144
	.long	1071148210
	.long	3170372974
	.long	3174359141
	.long	1709547520
	.long	1071105746
	.long	3312617382
	.long	3175326532
	.long	1168744448
	.long	1071063578
	.long	2799128425
	.long	1027228825
	.long	4163903488
	.long	1071021701
	.long	1285964057
	.long	3176060221
	.long	2056257536
	.long	1070980113
	.long	2706516695
	.long	1028650734
	.long	3739770880
	.long	1070938808
	.long	246392277
	.long	3174722093
	.long	1271734272
	.long	1070897784
	.long	3679462403
	.long	3174159366
	.long	4223328256
	.long	1070857035
	.long	2433294629
	.long	1027980203
	.long	1015963648
	.long	1070816560
	.long	4253198713
	.long	1026847814
	.long	1862295552
	.long	1070776353
	.long	1509191037
	.long	3173682029
	.long	103178240
	.long	1070736412
	.long	3582688163
	.long	3173089500
	.long	2264358912
	.long	1070696732
	.long	1779381280
	.long	1025292403
	.long	2278850560
	.long	1070657311
	.long	3240888728
	.long	1027177644
	.long	2954207232
	.long	1070618145
	.long	3564986365
	.long	1024870970
	.long	1865613312
	.long	1070562367
	.long	3892585078
	.long	1027946215
	.long	3434348544
	.long	1070485036
	.long	3011363162
	.long	3175199985
	.long	671449088
	.long	1070408197
	.long	1145012811
	.long	1026506981
	.long	1327038464
	.long	1070331842
	.long	227553652
	.long	1028331265
	.long	766836736
	.long	1070255966
	.long	518075456
	.long	3175312400
	.long	3434479616
	.long	1070180562
	.long	1452680450
	.long	3174267422
	.long	1364688896
	.long	1070105626
	.long	2571031723
	.long	3174222501
	.long	4236263424
	.long	1070031150
	.long	3514507731
	.long	3175326685
	.long	706019328
	.long	1069957131
	.long	2980398698
	.long	1026808442
	.long	1347502080
	.long	1069883561
	.long	1818423398
	.long	3175271900
	.long	4280483840
	.long	1069810435
	.long	3925170731
	.long	3175415416
	.long	3750608896
	.long	1069737749
	.long	710015584
	.long	3170118559
	.long	3004235776
	.long	1069665497
	.long	3823542833
	.long	3174354681
	.long	1393737728
	.long	1069593674
	.long	4238122296
	.long	1028475778
	.long	1621164032
	.long	1069497029
	.long	2232087921
	.long	3175937870
	.long	2762080256
	.long	1069355067
	.long	3580752115
	.long	3175802838
	.long	2914779136
	.long	1069213933
	.long	1251526596
	.long	1028548772
	.long	3808428032
	.long	1069073617
	.long	1491573749
	.long	1025131695
	.long	3589865472
	.long	1068934110
	.long	2320751647
	.long	3175285520
	.long	1102381056
	.long	1068795403
	.long	2369472545
	.long	3174735692
	.long	164790272
	.long	1068657486
	.long	3958705468
	.long	1027545055
	.long	966098944
	.long	1068520350
	.long	91754714
	.long	3174161762
	.long	100794368
	.long	1068269030
	.long	3205869737
	.long	3173509296
	.long	2079653888
	.long	1067997831
	.long	930310477
	.long	1028614386
	.long	3471048704
	.long	1067728143
	.long	1091811766
	.long	1028000848
	.long	1092419584
	.long	1067459950
	.long	629649056
	.long	3175844743
	.long	3079012352
	.long	1066936100
	.long	3939938999
	.long	3172112142
	.long	3666608128
	.long	1066405591
	.long	4185504407
	.long	3175959388
	.long	526647296
	.long	1065354156
	.long	1469727419
	.long	3173760470
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	L_tbl,@object
	.size	L_tbl,2064
	.align 16
coeff:
	.long	1367832035
	.long	1066403058
	.long	2677381210
	.long	3216320731
	.long	3610762001
	.long	3214607116
	.long	1215221452
	.long	1069835102
	.long	2894285243
	.long	1067936923
	.long	3011779882
	.long	3218479542
	.type	coeff,@object
	.size	coeff,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
