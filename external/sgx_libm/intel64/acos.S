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
	.file "acos.c"
	.text
..TXTST0:
# -- Begin  acos
	.text
       .align    16,0x90
	.globl acos
acos:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_acos.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        movsd     ABSVALMASK(%rip), %xmm4
        movsd     ONEMASK(%rip), %xmm3
        xorpd     %xmm5, %xmm5
        movsd     TMASK(%rip), %xmm2
        movq      %xmm0, %xmm1
        psrlq     $44, %xmm0
        movd      %xmm0, %edx
        movq      %xmm1, %xmm7
        movl      $8192, %ecx
        pinsrw    $2, %ecx, %xmm5
        movq      %xmm1, %xmm0
        movl      $524287, %eax
        andl      %edx, %eax
        subl      $260864, %eax
        cmpl      $955, %eax
        jae       .L_2TAG_PACKET_0.0.2
        mulsd     %xmm1, %xmm1
        andl      $65535, %edx
        subsd     %xmm1, %xmm3
        sqrtsd    %xmm3, %xmm3
        andpd     %xmm7, %xmm2
        andl      $-4, %edx
        subl      $64256, %edx
        lea       T_table(%rip), %r8
        movsd     (%r8,%rdx,2), %xmm1
        orpd      %xmm5, %xmm2
        lea       Tbl_addr(%rip), %r8
        movapd    (%r8,%rdx,4), %xmm4
        movq      %xmm7, %xmm6
        addsd     %xmm2, %xmm7
        subsd     %xmm2, %xmm0
        mulsd     %xmm0, %xmm7
        mulsd     %xmm1, %xmm6
        mulsd     %xmm2, %xmm3
        movq      %xmm6, %xmm1
        addsd     %xmm3, %xmm6
        divsd     %xmm6, %xmm7
        movsd     24+cv(%rip), %xmm0
        movsd     8+cv(%rip), %xmm5
        subsd     %xmm3, %xmm1
        psrlq     $63, %xmm2
        movq      %xmm1, %xmm3
        psllq     $63, %xmm2
        mulsd     %xmm1, %xmm1
        pshufd    $68, %xmm2, %xmm2
        movsd     16+cv(%rip), %xmm6
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm0
        xorpd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm5
        subpd     PI_BY_2(%rip), %xmm4
        mulsd     %xmm1, %xmm3
        addsd     %xmm6, %xmm0
        mulsd     %xmm3, %xmm0
        subsd     %xmm4, %xmm5
        pshufd    $238, %xmm4, %xmm4
        addsd     %xmm5, %xmm0
        subsd     %xmm7, %xmm0
        subsd     %xmm4, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
        subl      $955, %eax
        cmpl      $65, %eax
        jae       .L_2TAG_PACKET_1.0.2
        psrlq     $38, %xmm7
        psllq     $38, %xmm7
        pmovmskb  %xmm0, %eax
        andnpd    %xmm0, %xmm4
        subsd     %xmm7, %xmm1
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        addsd     %xmm6, %xmm0
        orpd      %xmm4, %xmm5
        subsd     %xmm7, %xmm3
        mulsd     %xmm1, %xmm0
        movq      %xmm3, %xmm4
        subsd     %xmm0, %xmm3
        sqrtsd    %xmm3, %xmm3
        andl      $128, %eax
        shrl      $7, %eax
        negl      %eax
        movq      %xmm3, %xmm7
        andpd     %xmm3, %xmm2
        psllq     $2, %xmm3
        pextrw    $3, %xmm3, %edx
        orpd      %xmm5, %xmm2
        movd      %eax, %xmm3
        pshufd    $0, %xmm3, %xmm3
        subl      $65216, %edx
        addl      %edx, %edx
        lea       T_table(%rip), %r8
        mulsd     (%r8,%rdx,4), %xmm7
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        subsd     %xmm7, %xmm6
        andpd     NEG_PI(%rip), %xmm3
        addsd     %xmm1, %xmm6
        subsd     %xmm2, %xmm4
        addsd     %xmm7, %xmm7
        movsd     8+cv(%rip), %xmm5
        subsd     %xmm0, %xmm4
        addsd     %xmm6, %xmm7
        movsd     24+cv(%rip), %xmm0
        divsd     %xmm7, %xmm4
        movsd     16+cv(%rip), %xmm2
        lea       Tbl_addr(%rip), %r8
        addpd     (%r8,%rdx,8), %xmm3
        movq      %xmm6, %xmm1
        mulsd     %xmm6, %xmm6
        mulsd     %xmm6, %xmm0
        mulsd     %xmm6, %xmm1
        mulsd     %xmm1, %xmm5
        mulsd     %xmm6, %xmm1
        addsd     %xmm2, %xmm0
        pxor      %xmm6, %xmm6
        mulsd     %xmm1, %xmm0
        addsd     %xmm3, %xmm5
        addsd     %xmm5, %xmm0
        andl      $32768, %eax
        pinsrw    $3, %eax, %xmm6
        movq      %xmm4, %xmm5
        pshufd    $238, %xmm3, %xmm3
        addsd     %xmm3, %xmm4
        subsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm5
        addsd     %xmm5, %xmm0
        addsd     %xmm4, %xmm0
        xorpd     %xmm6, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_1.0.2:
        addl      $15291, %eax
        cmpl      $14336, %eax
        jae       .L_2TAG_PACKET_2.0.2
        unpcklpd  %xmm0, %xmm0
        movapd    cv2(%rip), %xmm6
        unpcklpd  %xmm0, %xmm1
        movapd    16+cv2(%rip), %xmm2
        movapd    32+cv2(%rip), %xmm4
        mulpd     %xmm0, %xmm0
        movapd    PI_BY_2(%rip), %xmm5
        mulpd     %xmm0, %xmm1
        mulpd     %xmm0, %xmm6
        mulpd     %xmm0, %xmm0
        movq      %xmm1, %xmm3
        mulsd     %xmm1, %xmm1
        addpd     %xmm2, %xmm6
        mulpd     %xmm0, %xmm4
        mulsd     %xmm3, %xmm1
        addpd     %xmm4, %xmm6
        pshufd    $238, %xmm5, %xmm0
        mulpd     %xmm6, %xmm1
        pshufd    $238, %xmm5, %xmm6
        subsd     %xmm7, %xmm0
        pshufd    $238, %xmm1, %xmm2
        subsd     %xmm1, %xmm5
        subsd     %xmm0, %xmm6
        subsd     %xmm2, %xmm5
        subsd     %xmm6, %xmm7
        subsd     %xmm7, %xmm5
        addsd     %xmm5, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_2.0.2:
        subl      $15356, %eax
        cmpl      $4, %eax
        jae       .L_2TAG_PACKET_3.0.2
        xorpd     %xmm6, %xmm6
        andpd     ABSVALMASK(%rip), %xmm7
        movsd     ONE_BY_2(%rip), %xmm4
        movapd    cv2(%rip), %xmm1
        mulsd     %xmm4, %xmm7
        movapd    16+cv2(%rip), %xmm2
        subsd     %xmm7, %xmm4
        movapd    32+cv2(%rip), %xmm3
        pshufd    $68, %xmm4, %xmm7
        sqrtsd    %xmm4, %xmm4
        mulpd     %xmm7, %xmm1
        pshufd    $68, %xmm7, %xmm5
        pextrw    $3, %xmm0, %eax
        mulpd     %xmm7, %xmm7
        addpd     %xmm1, %xmm2
        movsd     HALFMASK(%rip), %xmm1
        mulpd     %xmm7, %xmm3
        cmpsd     $1, %xmm6, %xmm0
        mulsd     %xmm5, %xmm7
        addpd     %xmm3, %xmm2
        pshufd    $68, %xmm0, %xmm0
        mulsd     %xmm7, %xmm2
        andpd     NEG_PI(%rip), %xmm0
        mulpd     %xmm5, %xmm2
        andpd     %xmm4, %xmm1
        pshufd    $68, %xmm4, %xmm3
        subsd     %xmm1, %xmm4
        addsd     %xmm3, %xmm3
        mulsd     %xmm1, %xmm1
        subsd     %xmm4, %xmm3
        subsd     %xmm1, %xmm5
        mulsd     %xmm3, %xmm4
        pshufd    $238, %xmm3, %xmm3
        subsd     %xmm4, %xmm5
        divsd     %xmm3, %xmm5
        addpd     %xmm3, %xmm3
        mulpd     %xmm3, %xmm2
        pshufd    $238, %xmm2, %xmm4
        addsd     %xmm0, %xmm2
        andl      $32768, %eax
        pinsrw    $3, %eax, %xmm6
        pshufd    $238, %xmm0, %xmm0
        addsd     %xmm4, %xmm2
        addsd     %xmm5, %xmm2
        addsd     %xmm3, %xmm2
        addsd     %xmm2, %xmm0
        xorpd     %xmm6, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_3.0.2:
        addl      $261884, %eax
        cmpl      $261888, %eax
        jb        .L_2TAG_PACKET_4.0.2
        movd      %xmm7, %ecx
        psrlq     $32, %xmm7
        movd      %xmm7, %edx
        andl      $2147483647, %edx
        movl      $1072693248, %eax
        subl      %edx, %eax
        orl       %ecx, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_5.0.2
        movsd     (%rsp), %xmm2
        movd      %xmm2, %edx
        psrlq     $32, %xmm2
        movd      %xmm2, %ecx
        andl      $2147483647, %ecx
        subl      $1, %edx
        sbbl      $2146435072, %ecx
        cmpl      $0, %ecx
        jge       .L_2TAG_PACKET_6.0.2
        xorpd     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        movl      $32752, %edx
        pinsrw    $3, %edx, %xmm1
        mulsd     %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_7.0.2
.L_2TAG_PACKET_5.0.2:
        pextrw    $1, %xmm7, %edx
        shrl      $15, %edx
        negl      %edx
        movd      %edx, %xmm7
        pshufd    $0, %xmm7, %xmm7
        movsd     PI(%rip), %xmm2
        movsd     8+PI(%rip), %xmm0
        andpd     %xmm7, %xmm2
        andpd     %xmm7, %xmm0
        addsd     %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_4.0.2:
        movsd     PI_BY_2(%rip), %xmm2
        movsd     8+PI_BY_2(%rip), %xmm0
        addsd     %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_6.0.2:
        xorpd     %xmm6, %xmm6
        addsd     %xmm6, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_7.0.2:
        movq      %xmm0, 8(%rsp)
..B1.3:
        movq      8(%rsp), %xmm0
.L_2TAG_PACKET_8.0.2:
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	acos,@function
	.size	acos,.-acos
	.data
# -- End  acos
	.section .rodata, "a"
	.align 16
	.align 16
ABSVALMASK:
	.long	4294967295
	.long	2147483647
	.long	0
	.long	0
	.type	ABSVALMASK,@object
	.size	ABSVALMASK,16
	.align 16
T_table:
	.long	2642784509
	.long	1072689083
	.long	1514442531
	.long	1072688953
	.long	333108933
	.long	1072688821
	.long	3392112024
	.long	1072688686
	.long	2099852862
	.long	1072688550
	.long	749609004
	.long	1072688412
	.long	3634632596
	.long	1072688271
	.long	2163248461
	.long	1072688129
	.long	628657846
	.long	1072687985
	.long	3324036511
	.long	1072687838
	.long	1657632815
	.long	1072687690
	.long	4217538760
	.long	1072687539
	.long	2411951597
	.long	1072687387
	.long	533944872
	.long	1072687233
	.long	2876566508
	.long	1072687076
	.long	847936891
	.long	1072686918
	.long	3036019913
	.long	1072686757
	.long	848884575
	.long	1072686595
	.long	2874443326
	.long	1072686430
	.long	520713666
	.long	1072686264
	.long	2375556481
	.long	1072686095
	.long	4141904948
	.long	1072685924
	.long	1522666382
	.long	1072685752
	.long	3105624104
	.long	1072685577
	.long	298666327
	.long	1072685401
	.long	1689524500
	.long	1072685222
	.long	2981002200
	.long	1072685041
	.long	4170844284
	.long	1072684858
	.long	961802263
	.long	1072684674
	.long	1941503454
	.long	1072684487
	.long	2812647170
	.long	1072684298
	.long	3572873869
	.long	1072684107
	.long	4219797823
	.long	1072683914
	.long	456039788
	.long	1072683720
	.long	869096151
	.long	1072683523
	.long	1161535119
	.long	1072683324
	.long	1330865866
	.long	1072683123
	.long	1374571204
	.long	1072682920
	.long	1290107538
	.long	1072682715
	.long	1074904836
	.long	1072682508
	.long	726366587
	.long	1072682299
	.long	241869763
	.long	1072682088
	.long	3913732079
	.long	1072681874
	.long	3149342765
	.long	1072681659
	.long	2240966306
	.long	1072681442
	.long	1185873216
	.long	1072681223
	.long	4276274591
	.long	1072681001
	.long	2919452883
	.long	1072680778
	.long	1407565635
	.long	1072680553
	.long	4032743551
	.long	1072680325
	.long	2202188565
	.long	1072680096
	.long	207977577
	.long	1072679865
	.long	2342160518
	.long	1072679631
	.long	11858423
	.long	1072679396
	.long	1804034453
	.long	1072679158
	.long	3420722787
	.long	1072678918
	.long	563930456
	.long	1072678677
	.long	1820539192
	.long	1072678433
	.long	2892501606
	.long	1072678187
	.long	3776710320
	.long	1072677939
	.long	175063337
	.long	1072677690
	.long	674333171
	.long	1072677438
	.long	976363026
	.long	1072677184
	.long	1077935934
	.long	1072676928
	.long	1921075490
	.long	1072676540
	.long	881493302
	.long	1072676016
	.long	3275752439
	.long	1072675483
	.long	486855588
	.long	1072674943
	.long	1077229111
	.long	1072674394
	.long	723950308
	.long	1072673837
	.long	3693582199
	.long	1072673271
	.long	1367335316
	.long	1072672698
	.long	2305837020
	.long	1072672116
	.long	2184358641
	.long	1072671526
	.long	972682840
	.long	1072670928
	.long	2935101762
	.long	1072670321
	.long	3745513263
	.long	1072669706
	.long	3372320886
	.long	1072669083
	.long	1783464620
	.long	1072668452
	.long	3241386215
	.long	1072667812
	.long	3418125284
	.long	1072667164
	.long	2280219148
	.long	1072666508
	.long	4088700758
	.long	1072665843
	.long	219227400
	.long	1072665171
	.long	3521816918
	.long	1072664489
	.long	1076205279
	.long	1072663800
	.long	1436484616
	.long	1072663102
	.long	271362610
	.long	1072662396
	.long	1838996688
	.long	1072661681
	.long	1807122518
	.long	1072660958
	.long	137953542
	.long	1072660227
	.long	1088178584
	.long	1072659487
	.long	324057537
	.long	1072658739
	.long	2101288076
	.long	1072657982
	.long	2085133974
	.long	1072657217
	.long	235324451
	.long	1072656444
	.long	806051592
	.long	1072655662
	.long	3756033140
	.long	1072654871
	.long	453542543
	.long	1072654073
	.long	3741177327
	.long	1072653265
	.long	691216109
	.long	1072652450
	.long	4145223372
	.long	1072651625
	.long	1174439091
	.long	1072650793
	.long	324416139
	.long	1072649952
	.long	1550246310
	.long	1072649102
	.long	511524674
	.long	1072648244
	.long	1457248482
	.long	1072647377
	.long	45944955
	.long	1072646502
	.long	525537397
	.long	1072645618
	.long	2848440188
	.long	1072644725
	.long	2671555633
	.long	1072643824
	.long	4241172637
	.long	1072642914
	.long	3213094278
	.long	1072641996
	.long	3832503688
	.long	1072641069
	.long	1754091534
	.long	1072640134
	.long	1221921804
	.long	1072639190
	.long	2184526489
	.long	1072638237
	.long	294902089
	.long	1072637276
	.long	4090375270
	.long	1072636305
	.long	632860906
	.long	1072635327
	.long	2753498702
	.long	1072634339
	.long	1808009252
	.long	1072633343
	.long	2036428672
	.long	1072632338
	.long	3383235626
	.long	1072631324
	.long	1497347484
	.long	1072630302
	.long	617018317
	.long	1072629271
	.long	684933058
	.long	1072628231
	.long	1643170798
	.long	1072627182
	.long	3011066360
	.long	1072625592
	.long	957158713
	.long	1072623442
	.long	1390907941
	.long	1072621256
	.long	3819155270
	.long	1072619034
	.long	3443571196
	.long	1072616777
	.long	4045412458
	.long	1072614484
	.long	805503923
	.long	1072612156
	.long	1778922015
	.long	1072609791
	.long	2125033665
	.long	1072607390
	.long	1287203863
	.long	1072604953
	.long	2992629568
	.long	1072602479
	.long	2367267127
	.long	1072599969
	.long	3115526047
	.long	1072597422
	.long	340219539
	.long	1072594839
	.long	2017215719
	.long	1072592218
	.long	3225443424
	.long	1072589560
	.long	3326565673
	.long	1072586865
	.long	1669811211
	.long	1072584133
	.long	1886735022
	.long	1072581363
	.long	3301071171
	.long	1072578555
	.long	928514283
	.long	1072575710
	.long	2656364059
	.long	1072572826
	.long	3473490507
	.long	1072569904
	.long	2649965606
	.long	1072566944
	.long	3736819052
	.long	1072563945
	.long	1680885175
	.long	1072560908
	.long	4413771
	.long	1072557832
	.long	2214869753
	.long	1072554716
	.long	3214725184
	.long	1072551561
	.long	2186079903
	.long	1072548367
	.long	2590372131
	.long	1072545133
	.long	3578146079
	.long	1072541859
	.long	4283712755
	.long	1072538545
	.long	3824834510
	.long	1072535191
	.long	1302400298
	.long	1072531797
	.long	95058636
	.long	1072528362
	.long	3563906063
	.long	1072524885
	.long	2167230730
	.long	1072521368
	.long	3524918334
	.long	1072517809
	.long	2353304918
	.long	1072514209
	.long	1939625839
	.long	1072510567
	.long	1256714581
	.long	1072506883
	.long	3552525848
	.long	1072503156
	.long	3464809522
	.long	1072499387
	.long	4200542593
	.long	1072495575
	.long	355609124
	.long	1072491721
	.long	3684139099
	.long	1072487822
	.long	148355918
	.long	1072483881
	.long	1457689242
	.long	1072479895
	.long	2118591596
	.long	1072475865
	.long	908848089
	.long	1072471791
	.long	877032689
	.long	1072467672
	.long	752012304
	.long	1072463508
	.long	3532301749
	.long	1072459298
	.long	3600563221
	.long	1072455043
	.long	3902857084
	.long	1072450742
	.long	3063101036
	.long	1072446395
	.long	3972344374
	.long	1072442001
	.long	903183549
	.long	1072437561
	.long	983892938
	.long	1072433073
	.long	2722858568
	.long	1072428537
	.long	302790515
	.long	1072423954
	.long	759811057
	.long	1072419322
	.long	2507809922
	.long	1072414641
	.long	2388408813
	.long	1072407528
	.long	2084492942
	.long	1072397870
	.long	2435703301
	.long	1072388010
	.long	1935433360
	.long	1072377945
	.long	2742047290
	.long	1072367671
	.long	2053284205
	.long	1072357185
	.long	657783367
	.long	1072346483
	.long	2893664841
	.long	1072335560
	.long	3718906405
	.long	1072324413
	.long	1547896303
	.long	1072313038
	.long	2494058440
	.long	1072301429
	.long	3133238742
	.long	1072289582
	.long	3327000086
	.long	1072277492
	.long	1860667274
	.long	1072265154
	.long	665340747
	.long	1072252562
	.long	443347841
	.long	1072239710
	.long	581282618
	.long	1072226592
	.long	3349780465
	.long	1072213201
	.long	914217606
	.long	1072199532
	.long	989797661
	.long	1072185576
	.long	945436416
	.long	1072171326
	.long	549291300
	.long	1072156774
	.long	1814636389
	.long	1072141911
	.long	239092858
	.long	1072126729
	.long	1794680724
	.long	1072111217
	.long	1241534678
	.long	1072095366
	.long	3366566214
	.long	1072079164
	.long	1244090828
	.long	1072062601
	.long	1708448120
	.long	1072045663
	.long	3544260650
	.long	1072028337
	.long	1402741403
	.long	1072010610
	.long	2551936888
	.long	1071992465
	.long	617669739
	.long	1071973887
	.long	794002186
	.long	1071954857
	.long	2021237693
	.long	1071935356
	.long	540450384
	.long	1071915364
	.long	1920555537
	.long	1071894857
	.long	2879585206
	.long	1071873811
	.long	3000237455
	.long	1071852199
	.long	3352974346
	.long	1071829991
	.long	569629937
	.long	1071807155
	.long	2077237208
	.long	1071783653
	.long	2284891805
	.long	1071759446
	.long	1226651784
	.long	1071734489
	.long	1102047405
	.long	1071708731
	.long	2009896384
	.long	1071682115
	.long	927419082
	.long	1071654577
	.long	85010366
	.long	1071607413
	.long	696431025
	.long	1071548180
	.long	2611410541
	.long	1071486585
	.long	2612593658
	.long	1071422396
	.long	3548155306
	.long	1071355336
	.long	3887997484
	.long	1071285073
	.long	244854763
	.long	1071211202
	.long	4214445648
	.long	1071133216
	.long	2303966727
	.long	1071050478
	.long	3991040013
	.long	1070962152
	.long	3126952278
	.long	1070867118
	.long	1817448378
	.long	1070763804
	.long	1793814864
	.long	1070649884
	.long	3507224072
	.long	1070447193
	.long	4027609105
	.long	1070148772
	.long	577507993
	.long	1069779414
	.long	2310232419
	.long	1068931829
	.type	T_table,@object
	.size	T_table,2048
	.align 16
Tbl_addr:
	.long	3822952792
	.long	1021639372
	.long	182792448
	.long	1068507836
	.long	2264213271
	.long	1019558908
	.long	649052928
	.long	1068524253
	.long	1797139609
	.long	1022295143
	.long	1243095296
	.long	1068540671
	.long	1415938756
	.long	1021439537
	.long	2033294592
	.long	1068557090
	.long	2356809978
	.long	1021777916
	.long	3088063744
	.long	1068573510
	.long	2669055318
	.long	1022124482
	.long	180888576
	.long	1068589932
	.long	3566445325
	.long	1021358712
	.long	1970196992
	.long	1068606354
	.long	896980323
	.long	1021319659
	.long	4229555456
	.long	1068622777
	.long	436049712
	.long	1021319758
	.long	2732572160
	.long	1068639202
	.long	583123209
	.long	1020797960
	.long	1842831872
	.long	1068655628
	.long	1370449804
	.long	1021429270
	.long	1628994560
	.long	1068672055
	.long	2411391464
	.long	1021057980
	.long	2159763712
	.long	1068688483
	.long	1208692749
	.long	1021943903
	.long	3503886336
	.long	1068704912
	.long	538793309
	.long	1019744063
	.long	1435187200
	.long	1068721343
	.long	4085087612
	.long	1020608419
	.long	317469952
	.long	1068737775
	.long	144386942
	.long	1021440732
	.long	219617280
	.long	1068754208
	.long	2940088361
	.long	1019981122
	.long	1210558208
	.long	1068770642
	.long	2176850347
	.long	1018373705
	.long	3359268352
	.long	1068787077
	.long	2395611454
	.long	1021889042
	.long	2439803648
	.long	1068803514
	.long	1650705253
	.long	1020227966
	.long	2816203520
	.long	1068819952
	.long	3702166386
	.long	1019379914
	.long	262620672
	.long	1068836392
	.long	1855649370
	.long	1020453124
	.long	3438159616
	.long	1068852832
	.long	923063860
	.long	1019273834
	.long	3822105856
	.long	1068869274
	.long	4289947947
	.long	1019434249
	.long	1483729920
	.long	1068885718
	.long	787455814
	.long	1020738379
	.long	787321088
	.long	1068902163
	.long	3321653337
	.long	1021842569
	.long	1802253312
	.long	1068918609
	.long	2653633526
	.long	1021821525
	.long	302985984
	.long	1068935057
	.long	161272028
	.long	1021655149
	.long	653966080
	.long	1068951506
	.long	2566098667
	.long	1020066219
	.long	2924727296
	.long	1068967956
	.long	3646493722
	.long	1014292285
	.long	2889890304
	.long	1068984408
	.long	1081009196
	.long	1022189620
	.long	619098112
	.long	1069000862
	.long	4011643355
	.long	1021773297
	.long	477017600
	.long	1069017317
	.long	4030305534
	.long	1021292252
	.long	2533403904
	.long	1069033773
	.long	2645187591
	.long	1019527099
	.long	2563102208
	.long	1069050231
	.long	3857293792
	.long	1022311697
	.long	635982336
	.long	1069066691
	.long	3625936637
	.long	1017511744
	.long	1116940800
	.long	1069083152
	.long	3653872993
	.long	1022016631
	.long	4075964160
	.long	1069099614
	.long	2468900271
	.long	1021769532
	.long	993165568
	.long	1069116079
	.long	1358104224
	.long	1021199776
	.long	528586752
	.long	1069132545
	.long	2200950332
	.long	1022024872
	.long	2752395776
	.long	1069149012
	.long	3197072454
	.long	1017751319
	.long	3439855616
	.long	1069165481
	.long	1651081806
	.long	1020809338
	.long	2661257728
	.long	1069181952
	.long	539032752
	.long	1021728805
	.long	486957312
	.long	1069198425
	.long	3136045149
	.long	1016888671
	.long	1282340352
	.long	1069214899
	.long	2593963259
	.long	1018956103
	.long	822921728
	.long	1069231375
	.long	2146032737
	.long	1022306465
	.long	3474216192
	.long	1069247852
	.long	3976811625
	.long	1021350207
	.long	716902656
	.long	1069264332
	.long	718267222
	.long	1018624727
	.long	1211594496
	.long	1069280813
	.long	1485641389
	.long	1018447451
	.long	734070272
	.long	1069297296
	.long	354455128
	.long	1021341291
	.long	3650110720
	.long	1069313780
	.long	682185947
	.long	1021651853
	.long	1440663040
	.long	1069330267
	.long	3558574550
	.long	1021615110
	.long	2766612224
	.long	1069346755
	.long	874607978
	.long	1017746872
	.long	3404011008
	.long	1069363245
	.long	4154988502
	.long	1021439906
	.long	3423949056
	.long	1069379737
	.long	2263202309
	.long	1021479615
	.long	2897587712
	.long	1069396231
	.long	2562065031
	.long	1022090363
	.long	1896159232
	.long	1069412727
	.long	3836237663
	.long	1019867288
	.long	490968576
	.long	1069429225
	.long	3322056743
	.long	1006752762
	.long	3048360192
	.long	1069445724
	.long	1152314833
	.long	1013122252
	.long	1049850624
	.long	1069462226
	.long	3601590727
	.long	1022214610
	.long	3156899584
	.long	1069478729
	.long	1855169970
	.long	1019487271
	.long	851173376
	.long	1069495235
	.long	312649594
	.long	1020868604
	.long	2794281728
	.long	1069511742
	.long	1093490181
	.long	1020777577
	.long	468042496
	.long	1069528252
	.long	1152540679
	.long	1021403732
	.long	2534219264
	.long	1069544763
	.long	2292126035
	.long	1021872430
	.long	1376146432
	.long	1069558527
	.long	3293753641
	.long	1020500454
	.long	4175442432
	.long	1069575044
	.long	3626347564
	.long	1021610969
	.long	3523113472
	.long	1069591566
	.long	339956500
	.long	1021119039
	.long	4003350528
	.long	1069608092
	.long	3429333082
	.long	1022813542
	.long	1611067392
	.long	1069624623
	.long	2298017544
	.long	1021977587
	.long	931782144
	.long	1069641158
	.long	2164684743
	.long	1021250988
	.long	2256725504
	.long	1069657697
	.long	1138762335
	.long	1021443776
	.long	1582853120
	.long	1069674241
	.long	1084010382
	.long	1022994693
	.long	3497758720
	.long	1069690789
	.long	406366244
	.long	1022713586
	.long	3999816960
	.long	1069707342
	.long	1488723042
	.long	1023381290
	.long	3383096064
	.long	1069723900
	.long	2541558953
	.long	1019137887
	.long	1942403584
	.long	1069740463
	.long	1879620343
	.long	1022653642
	.long	4268263680
	.long	1069757030
	.long	3039077047
	.long	1022252545
	.long	2067062272
	.long	1069773603
	.long	4190670677
	.long	1020725863
	.long	4225828096
	.long	1069790180
	.long	1998567321
	.long	1022014385
	.long	2452507136
	.long	1069806763
	.long	1511628873
	.long	1021900300
	.long	1340746240
	.long	1069823351
	.long	788367341
	.long	1022726208
	.long	1190035456
	.long	1069839944
	.long	3856337230
	.long	1021834118
	.long	2300688384
	.long	1069856542
	.long	3211396579
	.long	1022621365
	.long	678886400
	.long	1069873146
	.long	4001011887
	.long	1022042646
	.long	921594112
	.long	1069889755
	.long	557811968
	.long	1023065533
	.long	3331668992
	.long	1069906369
	.long	1877060679
	.long	1022419742
	.long	3917875200
	.long	1069922989
	.long	1181055171
	.long	1022752712
	.long	2984829696
	.long	1069939615
	.long	4294526932
	.long	1021499988
	.long	838049024
	.long	1069956247
	.long	3658081878
	.long	1022957952
	.long	2078928384
	.long	1069972884
	.long	820353701
	.long	1019391107
	.long	2719854336
	.long	1069989527
	.long	1644022489
	.long	1023378240
	.long	3069117696
	.long	1070006176
	.long	2771393702
	.long	1019319954
	.long	3435962368
	.long	1070022831
	.long	3876394145
	.long	1023024433
	.long	4130595328
	.long	1070039492
	.long	1630447748
	.long	1021465882
	.long	1169236224
	.long	1070056160
	.long	2828355997
	.long	1020458120
	.long	3453997312
	.long	1070072833
	.long	164091641
	.long	1020388279
	.long	2708127744
	.long	1070089513
	.long	3036550223
	.long	1023328684
	.long	3540797696
	.long	1070106199
	.long	3710949463
	.long	1022568805
	.long	1972276736
	.long	1070122892
	.long	3885277950
	.long	1019761674
	.long	2613815552
	.long	1070139591
	.long	2764165077
	.long	1022921023
	.long	1487791616
	.long	1070156297
	.long	1330644769
	.long	1023162679
	.long	3207593472
	.long	1070173009
	.long	3911007221
	.long	1022993496
	.long	3797764608
	.long	1070189728
	.long	979712598
	.long	1022554580
	.long	3578920448
	.long	1070206454
	.long	2825738223
	.long	1020223708
	.long	2872795648
	.long	1070223187
	.long	392451124
	.long	1022666279
	.long	2002258432
	.long	1070239927
	.long	3730407632
	.long	1023148291
	.long	1291326464
	.long	1070256674
	.long	3723802980
	.long	1022514089
	.long	1065180928
	.long	1070273428
	.long	2635617463
	.long	1022654470
	.long	1650181632
	.long	1070290189
	.long	2061982883
	.long	1022853411
	.long	3373882880
	.long	1070306957
	.long	319732785
	.long	1022017175
	.long	2270081280
	.long	1070323733
	.long	2237757411
	.long	1023064087
	.long	2963732736
	.long	1070340516
	.long	468839165
	.long	1023293774
	.long	1491099904
	.long	1070357307
	.long	1502657946
	.long	1021533479
	.long	2479636480
	.long	1070374105
	.long	482913562
	.long	1021986286
	.long	1968133632
	.long	1070390911
	.long	3281474337
	.long	1022646400
	.long	291639040
	.long	1070407725
	.long	2453320259
	.long	1022812423
	.long	2081472512
	.long	1070424546
	.long	2939989570
	.long	1023091888
	.long	3380340480
	.long	1070441375
	.long	2850707499
	.long	1021921109
	.long	232287488
	.long	1070458213
	.long	3674625342
	.long	1020725130
	.long	1567614208
	.long	1070475058
	.long	9347334
	.long	1022024009
	.long	3433091072
	.long	1070491911
	.long	282524999
	.long	1021433523
	.long	1876877312
	.long	1070508773
	.long	3470449440
	.long	1019309721
	.long	1538472192
	.long	1070525643
	.long	2089486825
	.long	1019698916
	.long	2763830784
	.long	1070542521
	.long	443498115
	.long	1020505194
	.long	1605381632
	.long	1070559408
	.long	3018871601
	.long	1022869913
	.long	2706946048
	.long	1070576303
	.long	3936260892
	.long	1023175875
	.long	2123887360
	.long	1070593207
	.long	2994220655
	.long	1022825948
	.long	104015104
	.long	1070603108
	.long	335054493
	.long	1023441853
	.long	2904568832
	.long	1070615800
	.long	1451215633
	.long	1023853857
	.long	3456197120
	.long	1070632739
	.long	436334733
	.long	1024026432
	.long	252452352
	.long	1070649697
	.long	34596167
	.long	1024031396
	.long	3328018432
	.long	1070666672
	.long	2644547073
	.long	1024296758
	.long	1255829248
	.long	1070683667
	.long	552832586
	.long	1023763122
	.long	4097058560
	.long	1070700680
	.long	1955640623
	.long	1021394654
	.long	451770112
	.long	1070717714
	.long	3428903777
	.long	1022941142
	.long	408920832
	.long	1070734767
	.long	165503263
	.long	1023894958
	.long	1186960640
	.long	1070751840
	.long	435826450
	.long	1024026134
	.long	19078656
	.long	1070768934
	.long	1834169749
	.long	1022899284
	.long	2743490304
	.long	1070786048
	.long	494581074
	.long	1018818479
	.long	2328961024
	.long	1070803184
	.long	2987908834
	.long	1022581110
	.long	350011392
	.long	1070820342
	.long	240771184
	.long	1024143083
	.long	2692326912
	.long	1070837521
	.long	666056837
	.long	1022394776
	.long	2373274368
	.long	1070854723
	.long	2484337770
	.long	1024228156
	.long	1017131520
	.long	1070871948
	.long	3285648279
	.long	1024025789
	.long	265558272
	.long	1070889196
	.long	392241896
	.long	1024252809
	.long	1778008064
	.long	1070906467
	.long	1536107943
	.long	1023949300
	.long	2937184768
	.long	1070923762
	.long	3541062251
	.long	1019448646
	.long	1144442880
	.long	1070941082
	.long	3691683781
	.long	1022123948
	.long	2410165504
	.long	1070958426
	.long	1804181960
	.long	1023945221
	.long	4174350848
	.long	1070975795
	.long	2016094861
	.long	1021716585
	.long	3897012480
	.long	1070993190
	.long	175294410
	.long	1023703404
	.long	3353623040
	.long	1071010611
	.long	167973242
	.long	1023240839
	.long	45671168
	.long	1071028059
	.long	2166856113
	.long	1021565413
	.long	86063872
	.long	1071045533
	.long	2676254727
	.long	1023985299
	.long	1019772672
	.long	1071063034
	.long	989043593
	.long	1021549587
	.long	414297344
	.long	1071080563
	.long	3960972046
	.long	1024307251
	.long	155173120
	.long	1071098120
	.long	1830919291
	.long	1021592251
	.long	2151562240
	.long	1071115705
	.long	405408666
	.long	1023423128
	.long	4041854720
	.long	1071133319
	.long	2043497827
	.long	1024411503
	.long	3489224192
	.long	1071150963
	.long	3072215864
	.long	1022698635
	.long	2477196288
	.long	1071168637
	.long	1812195139
	.long	1022689192
	.long	3015298816
	.long	1071186341
	.long	764841969
	.long	1021027331
	.long	2844731136
	.long	1071204076
	.long	2878117321
	.long	1019116513
	.long	4028950528
	.long	1071221842
	.long	698911452
	.long	1023265602
	.long	69441536
	.long	1071239641
	.long	3253467847
	.long	1020795075
	.long	1676209920
	.long	1071257471
	.long	4272431167
	.long	1022873982
	.long	2408752384
	.long	1071275334
	.long	648519100
	.long	1024385717
	.long	151623680
	.long	1071293231
	.long	345257017
	.long	1019561408
	.long	1410154240
	.long	1071311161
	.long	197863993
	.long	1023224207
	.long	4131351552
	.long	1071329125
	.long	2620801789
	.long	1024411169
	.long	1999664384
	.long	1071347125
	.long	3952692616
	.long	1024168086
	.long	1617668864
	.long	1071365160
	.long	3019889809
	.long	1021907692
	.long	1032074240
	.long	1071383231
	.long	59469899
	.long	1023656194
	.long	2619492096
	.long	1071401338
	.long	1417526820
	.long	1021457783
	.long	202429440
	.long	1071419483
	.long	2927667935
	.long	1019175447
	.long	525044224
	.long	1071437665
	.long	38166811
	.long	1023981879
	.long	1779258880
	.long	1071455885
	.long	481252500
	.long	1023310234
	.long	2195673600
	.long	1071474144
	.long	3962395981
	.long	1021339088
	.long	44573696
	.long	1071492443
	.long	3936281395
	.long	1023014829
	.long	2226905344
	.long	1071510781
	.long	1515320476
	.long	1024320623
	.long	2800512512
	.long	1071529160
	.long	1225403697
	.long	1021081846
	.long	161113600
	.long	1071547581
	.long	3064809733
	.long	1024173917
	.long	1338410240
	.long	1071566043
	.long	2027604973
	.long	1024362526
	.long	522433280
	.long	1071584548
	.long	2055171723
	.long	1023858825
	.long	539595776
	.long	1071603096
	.long	3868820135
	.long	1022936424
	.long	4264017664
	.long	1071621687
	.long	3228065145
	.long	1023479578
	.long	1733924096
	.long	1071640324
	.long	3511934475
	.long	1022496355
	.long	108880384
	.long	1071651839
	.long	615880967
	.long	1023519706
	.long	3517856512
	.long	1071661202
	.long	3113108559
	.long	1025190289
	.long	4043153152
	.long	1071670589
	.long	1571836218
	.long	1023106116
	.long	3251299072
	.long	1071680000
	.long	3444076102
	.long	1022187841
	.long	2736921600
	.long	1071689435
	.long	272771483
	.long	1025095280
	.long	3897698560
	.long	1071703633
	.long	2075390188
	.long	1022489022
	.long	3209485056
	.long	1071722652
	.long	1438094065
	.long	1021844944
	.long	3781432064
	.long	1071741774
	.long	1675017145
	.long	1024143828
	.long	2684184064
	.long	1071761003
	.long	2259963753
	.long	1024731393
	.long	1840489728
	.long	1071780342
	.long	3372883597
	.long	1023431408
	.long	3764087808
	.long	1071799794
	.long	3307523102
	.long	1024485788
	.long	3006232320
	.long	1071819364
	.long	3088971966
	.long	1025213251
	.long	3374881280
	.long	1071839055
	.long	834437749
	.long	1025236452
	.long	797284864
	.long	1071858872
	.long	3122663941
	.long	1025320473
	.long	545765120
	.long	1071878818
	.long	826539625
	.long	1022450955
	.long	107562240
	.long	1071898898
	.long	339584600
	.long	1022481255
	.long	2123649024
	.long	1071919116
	.long	3912959833
	.long	1024321009
	.long	1562385664
	.long	1071939478
	.long	2846067230
	.long	1023343981
	.long	2963085824
	.long	1071959988
	.long	954548627
	.long	1021475211
	.long	3325550592
	.long	1071980652
	.long	3459651155
	.long	1025305573
	.long	775752448
	.long	1072001476
	.long	3582746667
	.long	1023859460
	.long	3238590720
	.long	1072022464
	.long	634636162
	.long	1024472353
	.long	2758801920
	.long	1072043624
	.long	3078216319
	.long	1025304516
	.long	1370319104
	.long	1072064962
	.long	2570569078
	.long	1025099442
	.long	2615805184
	.long	1072086484
	.long	3729933412
	.long	1024605112
	.long	3077336576
	.long	1072108198
	.long	1948916066
	.long	1024781603
	.long	1099528192
	.long	1072130112
	.long	3139143157
	.long	1023729360
	.long	1231903232
	.long	1072152233
	.long	1349513477
	.long	1024737515
	.long	1507504128
	.long	1072174570
	.long	3484516322
	.long	1024000959
	.long	2214659840
	.long	1072197132
	.long	2563820917
	.long	1025225535
	.long	1804739840
	.long	1072219929
	.long	760038746
	.long	1024482855
	.long	1413746688
	.long	1072242971
	.long	3401734714
	.long	1025129838
	.long	821409536
	.long	1072266269
	.long	3729772551
	.long	1025484796
	.long	3031825664
	.long	1072289834
	.long	122256749
	.long	1024752594
	.long	1710784256
	.long	1072313680
	.long	1518205483
	.long	1024724809
	.long	3025265152
	.long	1072337819
	.long	409951989
	.long	1022835555
	.long	287769088
	.long	1072362267
	.long	800355594
	.long	1022484850
	.long	198179840
	.long	1072387038
	.long	3502926213
	.long	1024209373
	.long	1909130496
	.long	1072412149
	.long	3064694319
	.long	1025380823
	.long	1941732096
	.long	1072437619
	.long	4112930390
	.long	1024294679
	.long	3492010496
	.long	1072463467
	.long	2684918107
	.long	1023220233
	.long	81959680
	.long	1072489716
	.long	220021366
	.long	1020635131
	.long	2297837056
	.long	1072516387
	.long	4027683826
	.long	1021041185
	.long	270404096
	.long	1072543508
	.long	2012766065
	.long	1021780753
	.long	3667376896
	.long	1072571105
	.long	2727981522
	.long	1023009874
	.long	330400256
	.long	1072599212
	.long	2940017003
	.long	1025393439
	.long	1119293952
	.long	1072627861
	.long	1608550416
	.long	1022675612
	.long	3536155904
	.long	1072657091
	.long	349665778
	.long	1025156751
	.long	3078046720
	.long	1072686946
	.long	2016159996
	.long	1022193169
	.long	455228416
	.long	1072705361
	.long	1908539328
	.long	1026126332
	.long	1871505664
	.long	1072720988
	.long	2784700894
	.long	1025922277
	.long	1630994432
	.long	1072737010
	.long	361107678
	.long	1022887244
	.long	2084558336
	.long	1072753462
	.type	Tbl_addr,@object
	.size	Tbl_addr,3840
	.space 768, 0x00 	# pad
	.align 16
cv:
	.long	0
	.long	0
	.long	1431655765
	.long	3217380693
	.long	858993459
	.long	3216192307
	.long	3067833783
	.long	3215383405
	.type	cv,@object
	.size	cv,32
	.align 16
PI_BY_2:
	.long	856972295
	.long	1016178214
	.long	1413754136
	.long	1073291771
	.type	PI_BY_2,@object
	.size	PI_BY_2,16
	.align 16
NEG_PI:
	.long	856972295
	.long	3164710438
	.long	1413754136
	.long	3221823995
	.type	NEG_PI,@object
	.size	NEG_PI,16
	.align 16
cv2:
	.long	780903145
	.long	1066854586
	.long	858993459
	.long	1068708659
	.long	3340530119
	.long	1067392113
	.long	1431655765
	.long	1069897045
	.long	1321528399
	.long	1066517740
	.long	3067833783
	.long	1067899757
	.long	2021159460
	.long	1065855096
	.long	2576980378
	.long	1066178969
	.type	cv2,@object
	.size	cv2,64
	.align 16
HALFMASK:
	.long	4160749568
	.long	4294967295
	.long	4160749568
	.long	4294967295
	.type	HALFMASK,@object
	.size	HALFMASK,16
	.align 16
PI:
	.long	856972295
	.long	1017226790
	.long	1413754136
	.long	1074340347
	.type	PI,@object
	.size	PI,16
	.align 4
ONEMASK:
	.long	0
	.long	1072693248
	.type	ONEMASK,@object
	.size	ONEMASK,8
	.align 4
TMASK:
	.long	0
	.long	4294950912
	.type	TMASK,@object
	.size	TMASK,8
	.align 4
ONE_BY_2:
	.long	0
	.long	1071644672
	.type	ONE_BY_2,@object
	.size	ONE_BY_2,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
