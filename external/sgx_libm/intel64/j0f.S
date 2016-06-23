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
	.file "j0f.c"
	.text
..TXTST0:
# -- Begin  j0f
	.text
       .align    16,0x90
	.globl j0f
j0f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_j0f.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        movd      %xmm0, %edx
        movss     %xmm0, 48(%rsp)
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jae       ..B1.18
..B1.2:
        cmpl      $1099154394, %edx
        jae       ..B1.19
..B1.3:
        cmpl      $1081424556, %edx
        jae       ..B1.14
..B1.4:
        cmpl      $1048576000, %edx
        jae       ..B1.13
..B1.5:
        cmpl      $998244352, %edx
        jae       ..B1.12
..B1.6:
        cmpl      $947912704, %edx
        jae       ..B1.11
..B1.7:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        cmpl      $838860800, %edx
        jae       ..B1.9
..B1.8:
        andps     .L_2il0floatpacket.1(%rip), %xmm0
        subss     %xmm0, %xmm1
        jmp       ..B1.10
..B1.9:
        mulss     %xmm0, %xmm0
        subss     %xmm0, %xmm1
..B1.10:
        movaps    %xmm1, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.11:
        pxor      %xmm0, %xmm0
        lea       8+_Q3(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        lea       _Q3(%rip), %rdx
        movsd     (%rax), %xmm1
        mulsd     %xmm0, %xmm1
        addsd     (%rdx), %xmm1
        mulsd     %xmm0, %xmm1
        addsd     .L_2il0floatpacket.2(%rip), %xmm1
        cvtsd2ss  %xmm1, %xmm1
        movaps    %xmm1, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.12:
        pxor      %xmm1, %xmm1
        lea       24+_Q2(%rip), %rax
        cvtss2sd  48(%rsp), %xmm1
        mulsd     %xmm1, %xmm1
        lea       16+_Q2(%rip), %rcx
        movaps    %xmm1, %xmm0
        lea       8+_Q2(%rip), %rdx
        mulsd     %xmm1, %xmm0
        lea       _Q2(%rip), %rsi
        movsd     (%rax), %xmm3
        movsd     (%rcx), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     (%rdx), %xmm3
        addsd     (%rsi), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm3
        addsd     .L_2il0floatpacket.2(%rip), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        movaps    %xmm3, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.13:
        pxor      %xmm1, %xmm1
        lea       112+_Q1(%rip), %rax
        cvtss2sd  48(%rsp), %xmm1
        andps     .L_2il0floatpacket.3(%rip), %xmm1
        lea       104+_Q1(%rip), %rdx
        lea       72+_Q1(%rip), %rcx
        lea       88+_Q1(%rip), %rdi
        lea       96+_Q1(%rip), %r11
        lea       56+_Q1(%rip), %r8
        lea       40+_Q1(%rip), %rsi
        lea       24+_Q1(%rip), %r9
        movsd     (%rdx), %xmm6
        lea       32+_Q1(%rip), %rdx
        movsd     (%rdi), %xmm0
        lea       16+_Q1(%rip), %rdi
        movsd     (%r11), %xmm5
        lea       8+_Q1(%rip), %r10
        subsd     (%rax), %xmm1
        lea       64+_Q1(%rip), %rax
        movaps    %xmm1, %xmm3
        mulsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rcx), %xmm6
        addsd     (%r8), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm6
        addsd     (%r9), %xmm0
        addsd     (%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm0
        mulsd     %xmm2, %xmm5
        addsd     %xmm0, %xmm6
        lea       80+_Q1(%rip), %rcx
        lea       48+_Q1(%rip), %rsi
        lea       _Q1(%rip), %r8
        addsd     (%r10), %xmm6
        movsd     (%rcx), %xmm4
        mulsd     %xmm2, %xmm4
        mulsd     %xmm1, %xmm6
        addsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm4
        addsd     (%rdi), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm5
        addsd     (%r8), %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.14:
        cmpl      $1092798087, %edx
        jae       ..B1.16
..B1.15:
        cmpl      $1088454576, %edx
        lea       _P1(%rip), %rcx
        lea       _P2(%rip), %rax
        jb        ..B1.17
        movq      %rax, %rcx
        jmp       ..B1.17
..B1.16:
        cmpl      $1096101336, %edx
        lea       _P3(%rip), %rcx
        lea       _P4(%rip), %rax
        jb        ..L12
        movq      %rax, %rcx
..L12:
..B1.17:
        pxor      %xmm3, %xmm3
        cvtss2sd  48(%rsp), %xmm3
        andps     .L_2il0floatpacket.3(%rip), %xmm3
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
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.18:
        pxor      %xmm0, %xmm0
        jbe       ..L15
        movss     48(%rsp), %xmm0
..L15:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.19:
        pxor      %xmm0, %xmm0
        lea       _val_16_0(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        andps     .L_2il0floatpacket.3(%rip), %xmm0
        lea       (%rsp), %rdi
        movsd     .L_2il0floatpacket.2(%rip), %xmm1
        lea       8(%rsp), %rsi
        divsd     %xmm0, %xmm1
        movsd     (%rax), %xmm2
        movl      $-1, %edx
        mulsd     %xmm1, %xmm2
        movsd     %xmm2, 8(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm2, 24(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm1, 16(%rsi)
        movsd     %xmm2, 32(%rsi)
..___tag_value_j0f.18:
        call      __libm_sincos_k32@PLT
..___tag_value_j0f.19:
..B1.20:
        lea       40+_QP(%rip), %r10
        lea       40+_PP(%rip), %rdx
        movsd     40(%rsp), %xmm2
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
        mulsd     24(%rsp), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm1
        addsd     (%rsi), %xmm4
        sqrtsd    %xmm0, %xmm0
        addsd     (%r9), %xmm1
        movsd     32(%rsp), %xmm3
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm6
        mulsd     16(%rsp), %xmm5
        mulsd     8(%rsp), %xmm6
        mulsd     (%rsp), %xmm5
        subsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	j0f,@function
	.size	j0f,.-j0f
	.data
# -- End  j0f
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x7fffffff,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.3:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x3f800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
_Q3:
	.long	4294966841
	.long	3218079743
	.long	477219499
	.long	1066401791
	.type	_Q3,@object
	.size	_Q3,16
	.align 4
_Q2:
	.long	4294967005
	.long	3218079743
	.long	4292583532
	.long	1066401791
	.long	2963922746
	.long	3208409541
	.long	1698509326
	.long	1054632110
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_Q1:
	.long	569392270
	.long	3163659129
	.long	911539783
	.long	3219168475
	.long	3927550863
	.long	1069261278
	.long	1680954783
	.long	1068301032
	.long	3247602706
	.long	3212950300
	.long	1528576945
	.long	3210869138
	.long	2120930432
	.long	1060197251
	.long	3147753376
	.long	1057418557
	.long	3445667889
	.long	3201446637
	.long	160313185
	.long	3198274950
	.long	2157358765
	.long	1047004568
	.long	1405517519
	.long	1043512506
	.long	3055202941
	.long	3187001715
	.long	562966967
	.long	3183555197
	.long	781654848
	.long	1073954069
	.type	_Q1,@object
	.size	_Q1,120
	.align 4
_P1:
	.long	2556390964
	.long	3162487616
	.long	168113101
	.long	1070974694
	.long	3865632959
	.long	3214905202
	.long	3384884860
	.long	3215663440
	.long	3461368592
	.long	1064499199
	.long	2947775703
	.long	1063419441
	.long	1964351934
	.long	3207001659
	.long	1081690493
	.long	3204998104
	.long	2315304668
	.long	1053480462
	.long	3655541446
	.long	1050880364
	.long	988219424
	.long	3194139699
	.long	2647984383
	.long	3191029766
	.long	3459760917
	.long	1038956170
	.long	1529622085
	.long	1075188879
	.type	_P1,@object
	.size	_P1,112
	.align 4
_P2:
	.long	2989381656
	.long	3164006638
	.long	2007135498
	.long	3218169721
	.long	3461465629
	.long	1066405759
	.long	1292000183
	.long	1067879320
	.long	1048188593
	.long	3211038307
	.long	2811900586
	.long	3210797269
	.long	275024305
	.long	1058896216
	.long	2179928921
	.long	1057456107
	.long	2316750355
	.long	3200463196
	.long	1626245786
	.long	3198355713
	.long	2429961783
	.long	1046264853
	.long	3081665457
	.long	1043557342
	.long	3760684269
	.long	3186121773
	.long	1825365706
	.long	1075924661
	.type	_P2,@object
	.size	_P2,112
	.align 4
_P3:
	.long	915277891
	.long	3163740397
	.long	1721892699
	.long	1070448958
	.long	3433548142
	.long	3213111280
	.long	3603643349
	.long	3215166749
	.long	2193444562
	.long	1062884841
	.long	1036402988
	.long	1063136790
	.long	2039602830
	.long	3205750717
	.long	2039806946
	.long	3204828766
	.long	3757243411
	.long	1052487712
	.long	2819184679
	.long	1050816211
	.long	1638789445
	.long	3193302656
	.long	96077275
	.long	3191008024
	.long	34207204
	.long	1038327735
	.long	8549046
	.long	1076335940
	.type	_P3,@object
	.size	_P3,112
	.align 4
_P4:
	.long	4010378302
	.long	3164933090
	.long	260862994
	.long	3217715229
	.long	793353311
	.long	1065112761
	.long	2823013001
	.long	1067546520
	.long	4006575139
	.long	3209863700
	.long	3390737311
	.long	3210433566
	.long	1793352299
	.long	1057800372
	.long	3528876835
	.long	1057234714
	.long	2505438989
	.long	3199512871
	.long	2785865938
	.long	3198229698
	.long	565130724
	.long	1045495083
	.long	410977742
	.long	1043470609
	.long	4266013081
	.long	3185543354
	.long	1056076242
	.long	1076747425
	.type	_P4,@object
	.size	_P4,112
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_QP:
	.long	4294921804
	.long	3212836863
	.long	4153409851
	.long	1056096255
	.long	2226361937
	.long	3196916170
	.long	3849695089
	.long	1044094312
	.long	663042994
	.long	3187124278
	.long	3336948587
	.long	1035486718
	.type	_QP,@object
	.size	_QP,48
	.align 4
_PP:
	.long	4294966288
	.long	1072693247
	.long	4269912424
	.long	3207725055
	.long	1780799603
	.long	1052554744
	.long	2285933488
	.long	3194113879
	.long	3892950982
	.long	1041746526
	.long	150212775
	.long	3184818833
	.type	_PP,@object
	.size	_PP,48
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
