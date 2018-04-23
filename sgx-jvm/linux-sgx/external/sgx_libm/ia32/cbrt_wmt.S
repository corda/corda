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
	.file "cbrt_wmt.c"
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
# -- Begin  cbrt
	.text
       .align    16,0x90
	.globl cbrt
cbrt:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %esi, 52(%esp)
        call      static_func
        movl      %eax, %esi
        movsd     128(%esp), %xmm0
        movapd    %xmm0, %xmm7
        movsd     %xmm0, 8(%esp)
        movl      $524032, %edx
        movsd     64(%esi), %xmm5
        movsd     80(%esi), %xmm3
        psrlq     $44, %xmm7
        pextrw    $0, %xmm7, %ecx
        movd      %xmm7, %eax
        movsd     96(%esi), %xmm1
        movsd     112(%esi), %xmm2
        movl      %ebx, 16(%esp)
        andl      $248, %ecx
        movsd     128(%ecx,%esi), %xmm4
        movl      %eax, %ebx
        andl      %eax, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_0.0.2
        cmpl      $524032, %edx
        je        .L_2TAG_PACKET_1.0.2
        shrl      $8, %edx
        shrl      $8, %ebx
        andpd     %xmm0, %xmm2
        andpd     %xmm5, %xmm0
        orpd      %xmm2, %xmm3
        orpd      %xmm0, %xmm1
        movapd    (%esi), %xmm5
        movl      $5462, %eax
        movapd    16(%esi), %xmm6
        mull      %edx
        movl      %ebx, %edx
        andl      $2047, %ebx
        shrl      $14, %eax
        andl      $2048, %edx
        subl      %eax, %ebx
        subl      %eax, %ebx
        subl      %eax, %ebx
        shll      $8, %ebx
        addl      $682, %eax
        orl       %edx, %eax
        movd      %eax, %xmm7
        addl      %ebx, %ecx
        psllq     $52, %xmm7
.L_2TAG_PACKET_2.0.2:
        movapd    32(%esi), %xmm2
        movapd    48(%esi), %xmm0
        subsd     %xmm3, %xmm1
        movq      %xmm7, %xmm3
        mulsd     384(%ecx,%esi), %xmm7
        mulsd     %xmm4, %xmm1
        mulsd     1152(%ecx,%esi), %xmm3
        movapd    %xmm1, %xmm4
        unpcklpd  %xmm1, %xmm1
        mulpd     %xmm1, %xmm5
        mulpd     %xmm1, %xmm6
        mulpd     %xmm1, %xmm1
        addpd     %xmm5, %xmm2
        addpd     %xmm6, %xmm0
        mulpd     %xmm1, %xmm2
        mulpd     %xmm1, %xmm1
        mulsd     %xmm7, %xmm4
        addpd     %xmm2, %xmm0
        movl      16(%esp), %ebx
        mulsd     %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm0
        addsd     %xmm1, %xmm0
        mulsd     %xmm4, %xmm0
        addsd     %xmm3, %xmm0
        addsd     %xmm7, %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_0.0.2:
        mulsd     1984(%esi), %xmm0
        movq      %xmm0, %xmm7
        movl      $524032, %edx
        psrlq     $44, %xmm7
        pextrw    $0, %xmm7, %ecx
        movd      %xmm7, %eax
        andl      $248, %ecx
        movsd     128(%ecx,%esi), %xmm4
        movl      %eax, %ebx
        andl      %eax, %edx
        shrl      $8, %edx
        shrl      $8, %ebx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_4.0.2
        andpd     %xmm0, %xmm2
        andpd     %xmm5, %xmm0
        orpd      %xmm2, %xmm3
        orpd      %xmm0, %xmm1
        movapd    (%esi), %xmm5
        movl      $5462, %eax
        movapd    16(%esi), %xmm6
        mull      %edx
        movl      %ebx, %edx
        andl      $2047, %ebx
        shrl      $14, %eax
        andl      $2048, %edx
        subl      %eax, %ebx
        subl      %eax, %ebx
        subl      %eax, %ebx
        shll      $8, %ebx
        addl      $661, %eax
        orl       %edx, %eax
        movd      %eax, %xmm7
        addl      %ebx, %ecx
        psllq     $52, %xmm7
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_4.0.2:
        cmpl      $0, %ebx
        jne       .L_2TAG_PACKET_5.0.2
        movl      16(%esp), %ebx
        fldl      1952(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_5.0.2:
        movl      16(%esp), %ebx
        fldl      1968(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_1.0.2:
        movl      16(%esp), %ebx
        movl      132(%esp), %eax
        movl      128(%esp), %edx
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $2146435072, %ecx
        ja        .L_2TAG_PACKET_6.0.2
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_6.0.2
        cmpl      $2146435072, %eax
        jne       .L_2TAG_PACKET_7.0.2
        fldl      1920(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_7.0.2:
        fldl      1936(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_6.0.2:
        movsd     8(%esp), %xmm0
        addsd     %xmm0, %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
.L_2TAG_PACKET_3.0.2:
        movl      52(%esp), %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	cbrt,@function
	.size	cbrt,.-cbrt
	.data
# -- End  cbrt
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1553778919
	.long	3213899486
	.long	3534952507
	.long	3215266280
	.long	1646371399
	.long	3214412045
	.long	477218588
	.long	3216798151
	.long	3582521621
	.long	1066628362
	.long	1007461464
	.long	1068473053
	.long	889629714
	.long	1067378449
	.long	1431655765
	.long	1070945621
	.long	4294967295
	.long	1048575
	.long	0
	.long	0
	.long	0
	.long	3220193280
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	1032192
	.long	0
	.long	0
	.long	528611360
	.long	3220144632
	.long	2884679527
	.long	3220082993
	.long	1991868891
	.long	3220024928
	.long	2298714891
	.long	3219970134
	.long	58835168
	.long	3219918343
	.long	3035110223
	.long	3219869313
	.long	1617585086
	.long	3219822831
	.long	2500867033
	.long	3219778702
	.long	4241943008
	.long	3219736752
	.long	258732970
	.long	3219696825
	.long	404232216
	.long	3219658776
	.long	2172167368
	.long	3219622476
	.long	1544257904
	.long	3219587808
	.long	377579543
	.long	3219554664
	.long	1616385542
	.long	3219522945
	.long	813783277
	.long	3219492562
	.long	3940743189
	.long	3219463431
	.long	2689777499
	.long	3219435478
	.long	1700977147
	.long	3219408632
	.long	3169102082
	.long	3219382828
	.long	327235604
	.long	3219358008
	.long	1244336319
	.long	3219334115
	.long	1300311200
	.long	3219311099
	.long	3095471925
	.long	3219288912
	.long	2166487928
	.long	3219267511
	.long	2913108253
	.long	3219246854
	.long	293672978
	.long	3219226904
	.long	288737297
	.long	3219207624
	.long	1810275472
	.long	3219188981
	.long	174592167
	.long	3219170945
	.long	3539053052
	.long	3219153485
	.long	2164392968
	.long	3219136576
	.long	572345495
	.long	1072698681
	.long	1998204467
	.long	1072709382
	.long	3861501553
	.long	1072719872
	.long	2268192434
	.long	1072730162
	.long	2981979308
	.long	1072740260
	.long	270859143
	.long	1072750176
	.long	2958651392
	.long	1072759916
	.long	313113243
	.long	1072769490
	.long	919449400
	.long	1072778903
	.long	2809328903
	.long	1072788162
	.long	2222981587
	.long	1072797274
	.long	2352530781
	.long	1072806244
	.long	594152517
	.long	1072815078
	.long	1555767199
	.long	1072823780
	.long	4282421314
	.long	1072832355
	.long	2355578597
	.long	1072840809
	.long	1162590619
	.long	1072849145
	.long	797864051
	.long	1072857367
	.long	431273680
	.long	1072865479
	.long	2669831148
	.long	1072873484
	.long	733477752
	.long	1072881387
	.long	4280220604
	.long	1072889189
	.long	801961634
	.long	1072896896
	.long	2915370760
	.long	1072904508
	.long	1159613482
	.long	1072912030
	.long	2689944798
	.long	1072919463
	.long	1248687822
	.long	1072926811
	.long	2967951030
	.long	1072934075
	.long	630170432
	.long	1072941259
	.long	3760898254
	.long	1072948363
	.long	0
	.long	1072955392
	.long	2370273294
	.long	1072962345
	.long	1261754802
	.long	1072972640
	.long	546334065
	.long	1072986123
	.long	1054893830
	.long	1072999340
	.long	1571187597
	.long	1073012304
	.long	1107975175
	.long	1073025027
	.long	3606909377
	.long	1073037519
	.long	1113616747
	.long	1073049792
	.long	4154744632
	.long	1073061853
	.long	3358931423
	.long	1073073713
	.long	4060702372
	.long	1073085379
	.long	747576176
	.long	1073096860
	.long	3023138255
	.long	1073108161
	.long	1419988548
	.long	1073119291
	.long	1914185305
	.long	1073130255
	.long	294389948
	.long	1073141060
	.long	3761802570
	.long	1073151710
	.long	978281566
	.long	1073162213
	.long	823148820
	.long	1073172572
	.long	2420954441
	.long	1073182792
	.long	3815449908
	.long	1073192878
	.long	2046058587
	.long	1073202835
	.long	1807524753
	.long	1073212666
	.long	2628681401
	.long	1073222375
	.long	3225667357
	.long	1073231966
	.long	1555307421
	.long	1073241443
	.long	3454043099
	.long	1073250808
	.long	1208137896
	.long	1073260066
	.long	3659916772
	.long	1073269218
	.long	1886261264
	.long	1073278269
	.long	3593647839
	.long	1073287220
	.long	3086012205
	.long	1073296075
	.long	2769796922
	.long	1073304836
	.long	888716057
	.long	1073317807
	.long	2201465623
	.long	1073334794
	.long	164369365
	.long	1073351447
	.long	3462666733
	.long	1073367780
	.long	2773905457
	.long	1073383810
	.long	1342879088
	.long	1073399550
	.long	2543933975
	.long	1073415012
	.long	1684477781
	.long	1073430209
	.long	3532178543
	.long	1073445151
	.long	1147747300
	.long	1073459850
	.long	1928031793
	.long	1073474314
	.long	2079717015
	.long	1073488553
	.long	4016765315
	.long	1073502575
	.long	3670431139
	.long	1073516389
	.long	3549227225
	.long	1073530002
	.long	11637607
	.long	1073543422
	.long	588220169
	.long	1073556654
	.long	2635407503
	.long	1073569705
	.long	2042029317
	.long	1073582582
	.long	1925128962
	.long	1073595290
	.long	4136375664
	.long	1073607834
	.long	759964600
	.long	1073620221
	.long	4257606771
	.long	1073632453
	.long	297278907
	.long	1073644538
	.long	3655053093
	.long	1073656477
	.long	2442253172
	.long	1073668277
	.long	1111876799
	.long	1073679941
	.long	3330973139
	.long	1073691472
	.long	3438879452
	.long	1073702875
	.long	3671565478
	.long	1073714153
	.long	1317849547
	.long	1073725310
	.long	1642364115
	.long	1073736348
	.long	4050900474
	.long	1014427190
	.long	1157977860
	.long	1016444461
	.long	1374568199
	.long	1017271387
	.long	2809163288
	.long	1016882676
	.long	3742377377
	.long	1013168191
	.long	3101606597
	.long	1017541672
	.long	65224358
	.long	1017217597
	.long	2691591250
	.long	1017266643
	.long	4020758549
	.long	1017689313
	.long	1316310992
	.long	1018030788
	.long	1031537856
	.long	1014090882
	.long	3261395239
	.long	1016413641
	.long	886424999
	.long	1016313335
	.long	3114776834
	.long	1014195875
	.long	1681120620
	.long	1017825416
	.long	1329600273
	.long	1016625740
	.long	465474623
	.long	1017097119
	.long	4251633980
	.long	1017169077
	.long	1986990133
	.long	1017710645
	.long	752958613
	.long	1017159641
	.long	2216216792
	.long	1018020163
	.long	4282860129
	.long	1015924861
	.long	1557627859
	.long	1016039538
	.long	3889219754
	.long	1018086237
	.long	3684996408
	.long	1017353275
	.long	723532103
	.long	1017717141
	.long	2951149676
	.long	1012528470
	.long	831890937
	.long	1017830553
	.long	1031212645
	.long	1017387331
	.long	2741737450
	.long	1017604974
	.long	2863311531
	.long	1003776682
	.long	4276736099
	.long	1013153088
	.long	4111778382
	.long	1015673686
	.long	1728065769
	.long	1016413986
	.long	2708718031
	.long	1018078833
	.long	1069335005
	.long	1015291224
	.long	700037144
	.long	1016482032
	.long	2904566452
	.long	1017226861
	.long	4074156649
	.long	1017622651
	.long	25019565
	.long	1015245366
	.long	3601952608
	.long	1015771755
	.long	3267129373
	.long	1017904664
	.long	503203103
	.long	1014921629
	.long	2122011730
	.long	1018027866
	.long	3927295461
	.long	1014189456
	.long	2790625147
	.long	1016024251
	.long	1330460186
	.long	1016940346
	.long	4033568463
	.long	1015538390
	.long	3695818227
	.long	1017509621
	.long	257573361
	.long	1017208868
	.long	3227697852
	.long	1017337964
	.long	234118548
	.long	1017169577
	.long	4009025803
	.long	1017278524
	.long	1948343394
	.long	1017749310
	.long	678398162
	.long	1018144239
	.long	3083864863
	.long	1016669086
	.long	2415453452
	.long	1017890370
	.long	175467344
	.long	1017330033
	.long	3197359580
	.long	1010339928
	.long	2071276951
	.long	1015941358
	.long	268372543
	.long	1016737773
	.long	938132959
	.long	1017389108
	.long	1816750559
	.long	1017337448
	.long	4119203749
	.long	1017152174
	.long	2578653878
	.long	1013108497
	.long	2470331096
	.long	1014678606
	.long	123855735
	.long	1016553320
	.long	1265650889
	.long	1014782687
	.long	3414398172
	.long	1017182638
	.long	1040773369
	.long	1016158401
	.long	3483628886
	.long	1016886550
	.long	4140499405
	.long	1016191425
	.long	3893477850
	.long	1016964495
	.long	3935319771
	.long	1009634717
	.long	2978982660
	.long	1015027112
	.long	2452709923
	.long	1017990229
	.long	3190365712
	.long	1015835149
	.long	4237588139
	.long	1015832925
	.long	2610678389
	.long	1017962711
	.long	2127316774
	.long	1017405770
	.long	824267502
	.long	1017959463
	.long	2165924042
	.long	1017912225
	.long	2774007076
	.long	1013257418
	.long	4123916326
	.long	1017582284
	.long	1976417958
	.long	1016959909
	.long	4092806412
	.long	1017711279
	.long	119251817
	.long	1015363631
	.long	3475418768
	.long	1017675415
	.long	1972580503
	.long	1015470684
	.long	815541017
	.long	1017517969
	.long	2429917451
	.long	1017397776
	.long	4062888482
	.long	1016749897
	.long	68284153
	.long	1017925678
	.long	2207779246
	.long	1016320298
	.long	1183466520
	.long	1017408657
	.long	143326427
	.long	1017060403
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	4293918720
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	1138753536
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,2000
	.data
	.section .note.GNU-stack, ""
# End
