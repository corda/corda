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
	.file "cosf_gen.c"
	.text
..TXTST0:
# -- Begin  cosf
	.text
       .align    16,0x90
	.globl cosf
cosf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cosf.1:
..L2:

..B1.2:
        subq      $32, %rsp
	.cfi_def_cfa_offset 40
        stmxcsr   8(%rsp)
        cmpl      $-24577, 8(%rsp)
        jne       .L_2TAG_PACKET_0.0.1
.L_2TAG_PACKET_1.0.1:
        pshuflw   $68, %xmm0, %xmm1
        movd      %xmm0, %ecx
        cvtps2pd  %xmm1, %xmm1
        movq      $0x40445f306e000000, %rax
        movd      %rax, %xmm2
        movl      $2147483647, %r10d
        movl      $1241513983, %r8d
        andl      %ecx, %r10d
        subl      %r10d, %r8d
        subl      $964689920, %r10d
        orl       %r10d, %r8d
        jl        .L_2TAG_PACKET_2.0.1
        movq      $0x4338000000000000, %r11
        movd      %r11, %xmm4
        mulsd     %xmm1, %xmm2
        movq      $0xbe5b1bbead603d8b, %rdx
        movd      %rdx, %xmm3
        movapd    %xmm2, %xmm5
        addsd     %xmm4, %xmm2
        movd      %xmm2, %r9d
        subsd     %xmm4, %xmm2
        mulsd     %xmm3, %xmm1
.L_2TAG_PACKET_3.0.1:
        lea       Ctable(%rip), %r10
        movl      $384, %eax
        movq      $0x40c37423899a1558, %rdx
        movd      %rdx, %xmm4
        subsd     %xmm2, %xmm5
        addl      %r9d, %r9d
        movl      %r9d, %r8d
        sarb      $7, %r9b
        andl      %r8d, %eax
        addb      %r9b, %r8b
        xorb      %r9b, %r8b
        addsd     %xmm5, %xmm1
        andl      $254, %r8d
        movsd     (%r10,%r8,8), %xmm3
        movq      $0x40a9f02f6222c720, %r11
        movd      %r11, %xmm0
        movapd    %xmm1, %xmm2
        unpcklpd  %xmm1, %xmm2
        mulsd     %xmm1, %xmm1
        movsd     8(%r10,%r8,8), %xmm5
        movl      %eax, %r9d
        andl      $256, %r9d
        shlq      $55, %r9
        mulsd     %xmm2, %xmm3
        movd      %r9, %xmm2
        subsd     %xmm1, %xmm0
        addl      $128, %eax
        andl      $256, %eax
        shlq      $55, %rax
        subsd     %xmm1, %xmm4
        movd      %rax, %xmm1
        xorpd     %xmm2, %xmm4
        mulsd     %xmm5, %xmm0
        mulsd     %xmm4, %xmm3
        xorpd     %xmm1, %xmm0
        addsd     %xmm3, %xmm0
        movl      8(%rsp), %eax
        andl      $-24577, %eax
        cmpl      8(%rsp), %eax
        je        .L_2TAG_PACKET_4.0.1
        stmxcsr   16(%rsp)
        movl      8(%rsp), %eax
        andl      $24576, %eax
        orl       %eax, 16(%rsp)
        ldmxcsr   16(%rsp)
.L_2TAG_PACKET_4.0.1:
        cvtpd2ps  %xmm0, %xmm0
        addq      $32, %rsp
        ret       
.L_2TAG_PACKET_2.0.1:
        andl      $2147483647, %ecx
        cmpl      $1040187392, %ecx
        ja        .L_2TAG_PACKET_5.0.1
        movl      $1065353216, %eax
        movd      %rax, %xmm0
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_6.0.1
        movl      $805306368, %eax
        movd      %rax, %xmm1
        subss     %xmm1, %xmm0
.L_2TAG_PACKET_6.0.1:
        movl      8(%rsp), %eax
        andl      $-24577, %eax
        cmpl      8(%rsp), %eax
        jne       .L_2TAG_PACKET_7.0.1
.L_2TAG_PACKET_8.0.1:
        addq      $32, %rsp
        ret       
.L_2TAG_PACKET_5.0.1:
        movd      %xmm0, %eax
        andl      $2139095040, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_9.0.1
        shrl      $23, %r10d
        subl      $27, %r10d
        andl      $65528, %r10d
        lea       Pitable(%rip), %rax
        movsd     (%rax,%r10,2), %xmm3
        movsd     8(%rax,%r10,2), %xmm5
        movq      $0xffffffffff000000, %r9
        movd      %r9, %xmm2
        movq      $0x4338000000000000, %r11
        movd      %r11, %xmm4
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
        addsd     %xmm0, %xmm3
        movd      %xmm2, %r9d
        subsd     %xmm4, %xmm2
        addsd     %xmm3, %xmm1
        jmp       .L_2TAG_PACKET_3.0.1
.L_2TAG_PACKET_9.0.1:
        subss     %xmm0, %xmm0
        movl      8(%rsp), %eax
        andl      $-24577, %eax
        cmpl      8(%rsp), %eax
        jne       .L_2TAG_PACKET_7.0.1
        jmp       .L_2TAG_PACKET_8.0.1
.L_2TAG_PACKET_0.0.1:
        movl      8(%rsp), %eax
        andl      $-24577, %eax
        movl      %eax, 16(%rsp)
        ldmxcsr   16(%rsp)
        jmp       .L_2TAG_PACKET_1.0.1
.L_2TAG_PACKET_7.0.1:
        stmxcsr   16(%rsp)
        movl      8(%rsp), %eax
        andl      $24576, %eax
        orl       %eax, 16(%rsp)
        ldmxcsr   16(%rsp)
        jmp       .L_2TAG_PACKET_8.0.1
	.cfi_def_cfa_offset 8
..B1.3:
        .align    16,0x90
	.cfi_endproc
	.type	cosf,@function
	.size	cosf,.-cosf
	.data
# -- End  cosf
	.section .rodata, "a"
	.align 16
	.align 16
Ctable:
	.long	0
	.long	0
	.long	3384690142
	.long	1060355388
	.long	918535681
	.long	3195026356
	.long	735532773
	.long	1060354999
	.long	3431923622
	.long	3196074611
	.long	2385983684
	.long	1060353830
	.long	3670374716
	.long	3196605580
	.long	2769466396
	.long	1060351883
	.long	3087185898
	.long	3197121906
	.long	2627920248
	.long	1060349159
	.long	624330694
	.long	3197386271
	.long	418831766
	.long	1060345660
	.long	1991825541
	.long	3197649836
	.long	900924147
	.long	1060341387
	.long	3917367244
	.long	3197912443
	.long	2243801771
	.long	1060336343
	.long	2803392399
	.long	3198165367
	.long	316258674
	.long	1060330532
	.long	2429158325
	.long	3198295476
	.long	1563320407
	.long	1060323956
	.long	1382541649
	.long	3198424870
	.long	1522351063
	.long	1060316620
	.long	4207787592
	.long	3198553470
	.long	1992674941
	.long	1060308528
	.long	321119318
	.long	3198681201
	.long	2434209605
	.long	1060299685
	.long	2865853451
	.long	3198807983
	.long	4249822861
	.long	1060290096
	.long	1666812989
	.long	3198933742
	.long	2181617495
	.long	1060279768
	.long	2082609563
	.long	3199058401
	.long	1475857720
	.long	1060268706
	.long	3726787968
	.long	3199181885
	.long	687004205
	.long	1060256917
	.long	2478530408
	.long	3199254748
	.long	250409509
	.long	1060244408
	.long	3681356269
	.long	3199315204
	.long	2463945945
	.long	1060231186
	.long	1535093810
	.long	3199374963
	.long	2878576948
	.long	1060217260
	.long	350661592
	.long	3199433988
	.long	3162654741
	.long	1060202638
	.long	2042383238
	.long	3199492243
	.long	2490283769
	.long	1060187329
	.long	1925003623
	.long	3199549694
	.long	1813501338
	.long	1060171342
	.long	1689375324
	.long	3199606306
	.long	3838419993
	.long	1060154686
	.long	902054988
	.long	3199662045
	.long	115411415
	.long	1060137373
	.long	1388494065
	.long	3199716877
	.long	1077921708
	.long	1060119411
	.long	3024934267
	.long	3199770769
	.long	3311502568
	.long	1060091288
	.long	3823857331
	.long	3199823689
	.long	1148832537
	.long	1060052839
	.long	17970311
	.long	3199875606
	.long	652328457
	.long	1060013161
	.long	3322544997
	.long	3199926486
	.long	1395127179
	.long	1059972278
	.long	2361617751
	.long	3199976301
	.long	1772140623
	.long	1059930215
	.long	1401987205
	.long	3200025020
	.long	3231311734
	.long	1059886997
	.long	3250975870
	.long	3200072613
	.long	1618129506
	.long	1059842651
	.long	742418353
	.long	3200119053
	.long	4288148241
	.long	1059797202
	.long	2580634211
	.long	3200164310
	.long	4268074870
	.long	1059750679
	.long	3348027550
	.long	3200208358
	.long	1659779181
	.long	1059703110
	.long	755484892
	.long	3200251171
	.long	3567792879
	.long	1059654522
	.long	2993330
	.long	3200273337
	.long	2550341159
	.long	1059604946
	.long	488211833
	.long	3200293469
	.long	2313428161
	.long	1059554411
	.long	3436142021
	.long	3200312945
	.long	453708735
	.long	1059502948
	.long	1407782048
	.long	3200331755
	.long	1264637048
	.long	1059450587
	.long	1575056534
	.long	3200349886
	.long	2771364651
	.long	1059397360
	.long	4275292648
	.long	3200367327
	.long	944109265
	.long	1059343300
	.long	3039475318
	.long	3200384069
	.long	2500108843
	.long	1059288438
	.long	1799297257
	.long	3200400101
	.long	3344537919
	.long	1059232808
	.long	2028347177
	.long	3200415413
	.long	1369913724
	.long	1059176444
	.long	2767093429
	.long	3200429996
	.long	664370609
	.long	1059119379
	.long	646746141
	.long	3200443842
	.long	1372808103
	.long	1059061535
	.long	2796885356
	.long	3200456941
	.long	941816458
	.long	1058944809
	.long	1097233945
	.long	3200469287
	.long	1373383079
	.long	1058826890
	.long	2262842797
	.long	3200480871
	.long	2796081240
	.long	1058707849
	.long	2093526290
	.long	3200491687
	.long	3945677176
	.long	1058587758
	.long	2671784286
	.long	3200501728
	.long	1979618421
	.long	1058466690
	.long	3789723003
	.long	3200510988
	.long	879820317
	.long	1058344717
	.long	2964703016
	.long	3200519462
	.long	2674059679
	.long	1058221912
	.long	4043704933
	.long	3200527144
	.long	2950925715
	.long	1058098350
	.long	36665700
	.long	3200534031
	.long	2811721921
	.long	1057935027
	.long	3193221610
	.long	3200540116
	.long	3297207428
	.long	1057685321
	.long	2063766514
	.long	3200545398
	.long	544295963
	.long	1057434550
	.long	163641427
	.long	3200549873
	.long	3381119115
	.long	1057182863
	.long	3096463640
	.long	3200553537
	.long	3054827835
	.long	1056896220
	.long	1381230637
	.long	3200556390
	.long	3419039295
	.long	1056390099
	.long	522802171
	.long	3200558429
	.long	1693671239
	.long	1055850091
	.long	3836476539
	.long	3200559652
	.long	1466910230
	.long	1054802127
	.long	3861233235
	.long	3200560060
	.long	0
	.long	0
	.type	Ctable,@object
	.size	Ctable,1040
	.align 16
Pitable:
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
	.type	Pitable,@object
	.size	Pitable,240
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
