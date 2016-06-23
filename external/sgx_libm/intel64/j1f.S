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
	.file "j1f.c"
	.text
..TXTST0:
# -- Begin  j1f
	.text
       .align    16,0x90
	.globl j1f
j1f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_j1f.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        subq      $64, %rsp
	.cfi_def_cfa_offset 80
        movd      %xmm0, %r13d
        movss     %xmm0, 56(%rsp)
        movl      %r13d, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jae       ..B1.24
..B1.2:
        cmpl      $1099993482, %edx
        jae       ..B1.22
..B1.3:
        cmpl      $1075439785, %edx
        jae       ..B1.16
..B1.4:
        cmpl      $1048576000, %edx
        jae       ..B1.15
..B1.5:
        cmpl      $998244352, %edx
        jae       ..B1.14
..B1.6:
        cmpl      $838860800, %edx
        jae       ..B1.13
..B1.7:
        shrl      $31, %r13d
        cmpl      $8388608, %edx
        jbe       ..B1.9
..B1.8:
        movl      %r13d, %r13d
        lea       _small_value_32(%rip), %rax
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        mulss     %xmm1, %xmm0
        movss     (%rax,%r13,4), %xmm2
        mulss     .L_2il0floatpacket.1(%rip), %xmm2
        subss     %xmm2, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.9:
        testl     %edx, %edx
        je        ..B1.11
..B1.10:
        lea       _small_value_32(%rip), %rax
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        movss     (%rax,%r13,4), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm2
        movss     56(%rsp), %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        cvtsd2ss  %xmm2, %xmm1
        cvtss2sd  %xmm0, %xmm0
        movss     %xmm1, (%rsp)
        subsd     %xmm2, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.11:
        movss     56(%rsp), %xmm0
..B1.12:
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.13:
        movss     56(%rsp), %xmm0
        lea       8+_Q2(%rip), %rax
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        lea       _Q2(%rip), %rdx
        cvtss2sd  %xmm0, %xmm0
        movaps    %xmm0, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     (%rax), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm0, %xmm2
        addsd     %xmm0, %xmm2
        cvtsd2ss  %xmm2, %xmm2
        movaps    %xmm2, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.14:
        movss     56(%rsp), %xmm0
        lea       24+_Q2(%rip), %rax
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        lea       16+_Q2(%rip), %rcx
        cvtss2sd  %xmm0, %xmm0
        movaps    %xmm0, %xmm2
        lea       8+_Q2(%rip), %rdx
        mulsd     %xmm0, %xmm2
        lea       _Q2(%rip), %rsi
        movaps    %xmm2, %xmm1
        mulsd     %xmm2, %xmm1
        movsd     (%rax), %xmm4
        movsd     (%rcx), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm1, %xmm3
        addsd     (%rdx), %xmm4
        addsd     (%rsi), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm4
        addsd     %xmm0, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        movaps    %xmm4, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.15:
        pxor      %xmm3, %xmm3
        lea       80+_Q1(%rip), %r9
        cvtss2sd  56(%rsp), %xmm3
        andps     .L_2il0floatpacket.2(%rip), %xmm3
        testl     $-2147483648, %r13d
        movaps    %xmm3, %xmm1
        lea       88+_Q1(%rip), %rax
        mulsd     %xmm3, %xmm1
        lea       72+_Q1(%rip), %rsi
        movaps    %xmm1, %xmm2
        lea       64+_Q1(%rip), %r13
        mulsd     %xmm1, %xmm2
        lea       48+_Q1(%rip), %r10
        movsd     (%r9), %xmm5
        lea       56+_Q1(%rip), %rdx
        mulsd     %xmm2, %xmm5
        lea       40+_Q1(%rip), %rdi
        movsd     (%rax), %xmm6
        lea       32+_Q1(%rip), %rax
        movsd     (%rsi), %xmm0
        lea       16+_Q1(%rip), %r11
        movsd     (%r13), %xmm4
        lea       24+_Q1(%rip), %rcx
        mulsd     %xmm2, %xmm6
        addsd     (%r10), %xmm5
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     (%rdx), %xmm6
        mulsd     %xmm2, %xmm5
        addsd     (%rdi), %xmm0
        addsd     (%rax), %xmm4
        mulsd     %xmm2, %xmm6
        addsd     (%r11), %xmm5
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm4
        addsd     (%rcx), %xmm6
        mulsd     %xmm1, %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm5
        lea       8+_Q1(%rip), %r8
        lea       _Q1(%rip), %rdx
        addsd     (%r8), %xmm0
        addsd     (%rdx), %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm3, %xmm4
        addsd     %xmm0, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        movaps    %xmm6, %xmm0
        xorps     .L_2il0floatpacket.3(%rip), %xmm0
        jne       ..L31
        movaps    %xmm6, %xmm0
..L31:
        cvtsd2ss  %xmm0, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.16:
        pxor      %xmm3, %xmm3
        andl      $-2147483648, %r13d
        cvtss2sd  56(%rsp), %xmm3
        andps     .L_2il0floatpacket.2(%rip), %xmm3
        cmpl      $1097786634, %edx
        jae       ..B1.20
..B1.17:
        cmpl      $1091204523, %edx
        jae       ..B1.19
..B1.18:
        cmpl      $1085318267, %edx
        lea       _P1(%rip), %rcx
        lea       _P2(%rip), %rax
        jb        ..B1.21
        movq      %rax, %rcx
        jmp       ..B1.21
..B1.19:
        cmpl      $1094494752, %edx
        lea       _P3(%rip), %rcx
        lea       _P4(%rip), %rax
        jb        ..B1.21
        movq      %rax, %rcx
        jmp       ..B1.21
..B1.20:
        lea       _P5(%rip), %rcx
..B1.21:
        testl     %r13d, %r13d
        movsd     96(%rcx), %xmm6
        movsd     80(%rcx), %xmm0
        movsd     88(%rcx), %xmm5
        movsd     72(%rcx), %xmm4
        subsd     104(%rcx), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     64(%rcx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     48(%rcx), %xmm0
        addsd     56(%rcx), %xmm5
        mulsd     %xmm2, %xmm6
        addsd     40(%rcx), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     32(%rcx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     16(%rcx), %xmm0
        addsd     24(%rcx), %xmm5
        mulsd     %xmm2, %xmm6
        addsd     8(%rcx), %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        mulsd     %xmm3, %xmm4
        addsd     %xmm0, %xmm6
        mulsd     %xmm3, %xmm5
        addsd     (%rcx), %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        movaps    %xmm6, %xmm0
        xorps     .L_2il0floatpacket.3(%rip), %xmm0
        jne       ..L37
        movaps    %xmm6, %xmm0
..L37:
        cvtsd2ss  %xmm0, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.22:
        pxor      %xmm0, %xmm0
        lea       _val_16_0(%rip), %rax
        cvtss2sd  56(%rsp), %xmm0
        andps     .L_2il0floatpacket.2(%rip), %xmm0
        lea       8(%rsp), %rdi
        movsd     .L_2il0floatpacket.4(%rip), %xmm1
        lea       16(%rsp), %rsi
        divsd     %xmm0, %xmm1
        movsd     (%rax), %xmm2
        movl      $-3, %edx
        mulsd     %xmm1, %xmm2
        andl      $-2147483648, %r13d
        movsd     %xmm2, 8(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm2, 24(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm1, 16(%rsi)
        movsd     %xmm2, 32(%rsi)
..___tag_value_j1f.43:
        call      __libm_sincos_k32@PLT
..___tag_value_j1f.44:
..B1.23:
        lea       40+_QP(%rip), %r10
        lea       40+_PP(%rip), %rdx
        movsd     48(%rsp), %xmm2
        lea       24+_QP(%rip), %r11
        lea       24+_PP(%rip), %rcx
        lea       32+_PP(%rip), %rdi
        lea       _tonpi(%rip), %rax
        lea       16+_PP(%rip), %r8
        movsd     (%r10), %xmm5
        lea       8+_PP(%rip), %rsi
        movsd     (%rdx), %xmm6
        lea       32+_QP(%rip), %rdx
        mulsd     %xmm2, %xmm5
        lea       _PP(%rip), %r9
        mulsd     %xmm2, %xmm6
        addsd     (%r11), %xmm5
        addsd     (%rcx), %xmm6
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm6
        movsd     (%rdx), %xmm4
        lea       16+_QP(%rip), %rcx
        mulsd     %xmm2, %xmm4
        addsd     (%rsi), %xmm6
        movsd     (%rdi), %xmm1
        lea       _QP(%rip), %rsi
        mulsd     %xmm2, %xmm1
        addsd     (%rcx), %xmm4
        movsd     (%rax), %xmm0
        lea       8+_QP(%rip), %rax
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm1
        mulsd     32(%rsp), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm1
        addsd     (%rsi), %xmm4
        sqrtsd    %xmm0, %xmm0
        addsd     (%r9), %xmm1
        movsd     40(%rsp), %xmm3
        testl     %r13d, %r13d
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm6
        mulsd     24(%rsp), %xmm5
        mulsd     16(%rsp), %xmm6
        mulsd     8(%rsp), %xmm5
        subsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm0
        movaps    %xmm0, %xmm2
        xorps     .L_2il0floatpacket.3(%rip), %xmm2
        jne       ..L45
        movaps    %xmm0, %xmm2
..L45:
        cvtsd2ss  %xmm2, %xmm2
        movaps    %xmm2, %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.24:
        ja        ..B1.26
..B1.25:
        lea       _zeros(%rip), %rax
        shrl      $31, %r13d
        movss     (%rax,%r13,4), %xmm0
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 13, -16
..B1.26:
        movss     56(%rsp), %xmm0
..B1.27:
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	j1f,@function
	.size	j1f,.-j1f
	.data
# -- End  j1f
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x3f000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x0d800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.align 4
_Q2:
	.long	4294942533
	.long	3219128319
	.long	1380922587
	.long	1068848469
	.long	4229217063
	.long	3212603831
	.long	2485211372
	.long	1060546867
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_Q1:
	.long	4294875887
	.long	1071644671
	.long	4170968197
	.long	1038133990
	.long	82133904
	.long	3215982592
	.long	2332230043
	.long	1043853917
	.long	2564340116
	.long	1063605580
	.long	220691470
	.long	1046506888
	.long	254563561
	.long	3205265147
	.long	694808779
	.long	1046622203
	.long	4019568744
	.long	1051070668
	.long	1556554770
	.long	1044540217
	.long	1786312313
	.long	3191946521
	.long	1750363188
	.long	1039907362
	.type	_Q1,@object
	.size	_Q1,96
	.align 4
_P1:
	.long	3288017007
	.long	3163666881
	.long	1479173198
	.long	3218720463
	.long	2681037136
	.long	1068165283
	.long	482223942
	.long	1068193949
	.long	1641242500
	.long	3212130132
	.long	3602486439
	.long	3210890035
	.long	1295520927
	.long	1059513544
	.long	2893642677
	.long	1057503730
	.long	2144568350
	.long	3200875225
	.long	914896231
	.long	3198366754
	.long	1774974526
	.long	1046581392
	.long	3090631217
	.long	1043566169
	.long	3310618599
	.long	3186783429
	.long	1974431497
	.long	1074702165
	.type	_P1,@object
	.size	_P1,112
	.align 4
_P2:
	.long	216484910
	.long	1015040375
	.long	3011708428
	.long	1070806296
	.long	3332724636
	.long	3214272269
	.long	3160644008
	.long	3215461507
	.long	2925435221
	.long	1063888050
	.long	725350898
	.long	1063337916
	.long	297499451
	.long	3206574940
	.long	3278099830
	.long	3204945545
	.long	2222993240
	.long	1053042193
	.long	2059286351
	.long	1050869046
	.long	306569625
	.long	3193793299
	.long	95256688
	.long	3191038309
	.long	616794992
	.long	1039029534
	.long	4088689232
	.long	1075580917
	.type	_P2,@object
	.size	_P2,112
	.align 4
_P3:
	.long	1314465059
	.long	1017127215
	.long	1413823668
	.long	3218077268
	.long	4087546795
	.long	1065951807
	.long	3559351807
	.long	1067757765
	.long	338531188
	.long	3210711466
	.long	2516776276
	.long	3210695105
	.long	1548953411
	.long	1058452686
	.long	3819184955
	.long	1057373236
	.long	1262833741
	.long	3200137243
	.long	325214128
	.long	3198310046
	.long	1253465703
	.long	1045913252
	.long	3453826356
	.long	1043528319
	.long	2667154656
	.long	3186140571
	.long	3502111785
	.long	1076123856
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	1657852450
	.long	3163577804
	.long	2020212262
	.long	1070330675
	.long	774106997
	.long	3212888122
	.long	2119717923
	.long	3215085957
	.long	2098754573
	.long	1062575793
	.long	2137093530
	.long	1063017421
	.long	2437037263
	.long	3205513537
	.long	2698759453
	.long	3204752976
	.long	1998212634
	.long	1052165362
	.long	2817762251
	.long	1050764623
	.long	357372240
	.long	3193071576
	.long	3891599306
	.long	3190966182
	.long	2865549426
	.long	1038348895
	.long	4077970850
	.long	1076536762
	.type	_P4,@object
	.size	_P4,112
	.align 4
_P5:
	.long	1537387514
	.long	3166104464
	.long	4237757176
	.long	3217630662
	.long	789391682
	.long	1064857043
	.long	3122547501
	.long	1067488355
	.long	3303098147
	.long	3209681410
	.long	2944525948
	.long	3210343613
	.long	1468487066
	.long	1057519518
	.long	285546763
	.long	1057172625
	.long	830693909
	.long	3199311383
	.long	1284260698
	.long	3198182585
	.long	1823688212
	.long	1045254801
	.long	1962790020
	.long	1043427779
	.long	1867031598
	.long	3185590079
	.long	906299589
	.long	1076918395
	.type	_P5,@object
	.size	_P5,112
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_QP:
	.long	4294954956
	.long	1066926079
	.long	4141388976
	.long	3204071423
	.long	1500123100
	.long	1049740228
	.long	3264213437
	.long	3191856517
	.long	1489731078
	.long	1039824630
	.long	1807021260
	.long	3183130166
	.type	_QP,@object
	.size	_QP,48
	.align 4
_PP:
	.long	551
	.long	1072693248
	.long	4267608614
	.long	1061027839
	.long	3690881252
	.long	3200414971
	.long	3933039373
	.long	1046848465
	.long	3246008603
	.long	3189439465
	.long	1689113401
	.long	1037483563
	.type	_PP,@object
	.size	_PP,48
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
