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
	.file "expm1_wmt.c"
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
# -- Begin  expm1
	.text
       .align    16,0x90
	.globl expm1
expm1:
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
        unpcklpd  %xmm0, %xmm0
        movapd    64(%ebx), %xmm1
        movapd    48(%ebx), %xmm6
        movapd    80(%ebx), %xmm2
        movapd    96(%ebx), %xmm3
        pextrw    $3, %xmm0, %eax
        andl      $32767, %eax
        movl      $16527, %edx
        subl      %eax, %edx
        subl      $16304, %eax
        orl       %eax, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_0.0.3
        mulpd     %xmm0, %xmm1
        addpd     %xmm6, %xmm1
        movapd    %xmm1, %xmm7
        subpd     %xmm6, %xmm1
        mulpd     %xmm1, %xmm2
        movapd    112(%ebx), %xmm4
        mulpd     %xmm1, %xmm3
        movapd    128(%ebx), %xmm5
        subpd     %xmm2, %xmm0
        movd      %xmm7, %eax
        movl      %eax, %ecx
        andl      $63, %ecx
        shll      $4, %ecx
        sarl      $6, %eax
        movl      %eax, %edx
        subpd     %xmm3, %xmm0
        movapd    160(%ebx,%ecx), %xmm2
        movsd     144(%ebx), %xmm3
        mulpd     %xmm0, %xmm4
        movapd    %xmm0, %xmm1
        mulpd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm3
        addpd     %xmm4, %xmm5
        mulsd     %xmm0, %xmm0
        movapd    %xmm2, %xmm4
        unpckhpd  %xmm2, %xmm2
        movdqa    16(%ebx), %xmm6
        pand      %xmm6, %xmm7
        movdqa    32(%ebx), %xmm6
        paddq     %xmm6, %xmm7
        psllq     $46, %xmm7
        mulsd     %xmm0, %xmm3
        mulpd     %xmm5, %xmm0
        addsd     %xmm3, %xmm0
        addl      $894, %edx
        cmpl      $1916, %edx
        ja        .L_2TAG_PACKET_1.0.3
        xorpd     %xmm3, %xmm3
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm3
        orpd      %xmm7, %xmm2
        mulsd     %xmm4, %xmm7
        movapd    %xmm3, %xmm6
        addsd     %xmm1, %xmm3
        pextrw    $3, %xmm2, %edx
        pshufd    $238, %xmm0, %xmm5
        psrlq     $38, %xmm3
        psllq     $38, %xmm3
        movapd    %xmm2, %xmm4
        subsd     %xmm3, %xmm6
        addsd     %xmm5, %xmm0
        addsd     %xmm6, %xmm1
        addsd     %xmm7, %xmm4
        mulsd     %xmm3, %xmm7
        mulsd     %xmm2, %xmm3
        xorpd     %xmm5, %xmm5
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm5
        addsd     %xmm1, %xmm0
        movl      $17184, %ecx
        subl      %edx, %ecx
        subl      $16256, %edx
        orl       %edx, %ecx
        jl        .L_2TAG_PACKET_2.0.3
        mulsd     %xmm4, %xmm0
        subsd     %xmm5, %xmm3
        addsd     %xmm7, %xmm0
        addsd     %xmm3, %xmm0
.L_2TAG_PACKET_3.0.3:
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_2.0.3:
        cmpl      $0, %edx
        jl        .L_2TAG_PACKET_5.0.3
        mulsd     %xmm4, %xmm0
        subsd     %xmm5, %xmm7
        addsd     %xmm7, %xmm0
        addsd     %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_5.0.3:
        mulsd     %xmm4, %xmm0
        addsd     %xmm7, %xmm0
        addsd     %xmm3, %xmm0
        subsd     %xmm5, %xmm0
        jmp       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_1.0.3:
        movl      132(%esp), %ecx
        addsd     %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm0
        addsd     %xmm1, %xmm0
        cmpl      $0, %ecx
        jl        .L_2TAG_PACKET_6.0.3
        fstcw     24(%esp)
        movzwl    24(%esp), %edx
        orl       $768, %edx
        movw      %dx, 28(%esp)
        fldcw     28(%esp)
        movl      %eax, %edx
        sarl      $1, %eax
        subl      %eax, %edx
        movdqa    (%ebx), %xmm6
        pandn     %xmm2, %xmm6
        addl      $1023, %eax
        movd      %eax, %xmm3
        psllq     $52, %xmm3
        orpd      %xmm3, %xmm6
        mulsd     %xmm3, %xmm4
        movsd     %xmm0, 8(%esp)
        fldl      8(%esp)
        movsd     %xmm6, 16(%esp)
        fldl      16(%esp)
        movsd     %xmm4, 16(%esp)
        fldl      16(%esp)
        addl      $1023, %edx
        movd      %edx, %xmm4
        psllq     $52, %xmm4
        faddp     %st, %st(1)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        movsd     %xmm4, 8(%esp)
        fldl      8(%esp)
        fmulp     %st, %st(1)
        fstpl     8(%esp)
        movsd     8(%esp), %xmm0
        fldcw     24(%esp)
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      $32752, %ecx
        jae       .L_2TAG_PACKET_7.0.3
        jmp       .L_2TAG_PACKET_4.0.3
        cmpl      $-2147483648, %ecx
        jb        .L_2TAG_PACKET_7.0.3
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_7.0.3:
        movl      $41, %edx
.L_2TAG_PACKET_8.0.3:
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
        jmp       .L_2TAG_PACKET_9.0.3
.L_2TAG_PACKET_10.0.3:
        cmpl      $2146435072, %eax
        jae       .L_2TAG_PACKET_11.0.3
        movsd     1272(%ebx), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $41, %edx
        jmp       .L_2TAG_PACKET_8.0.3
.L_2TAG_PACKET_11.0.3:
        movl      132(%esp), %eax
        movl      128(%esp), %edx
        movl      %eax, %ecx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        ja        .L_2TAG_PACKET_12.0.3
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_12.0.3
        cmpl      $0, %ecx
        jl        .L_2TAG_PACKET_13.0.3
        movsd     1256(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_13.0.3:
        jmp       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_12.0.3:
        movsd     128(%esp), %xmm0
        addsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_14.0.3:
        addl      $16304, %eax
        cmpl      $15504, %eax
        jb        .L_2TAG_PACKET_15.0.3
        movapd    1184(%ebx), %xmm2
        pshufd    $68, %xmm0, %xmm1
        movapd    1200(%ebx), %xmm3
        movapd    1216(%ebx), %xmm4
        movsd     1232(%ebx), %xmm5
        mulsd     %xmm1, %xmm1
        xorpd     %xmm6, %xmm6
        movl      $16352, %eax
        pinsrw    $3, %eax, %xmm6
        mulpd     %xmm0, %xmm2
        xorpd     %xmm7, %xmm7
        movl      $16368, %edx
        pinsrw    $3, %edx, %xmm7
        addpd     %xmm3, %xmm2
        mulsd     %xmm1, %xmm5
        pshufd    $228, %xmm1, %xmm3
        mulpd     %xmm1, %xmm1
        mulsd     %xmm0, %xmm6
        mulpd     %xmm0, %xmm2
        addpd     %xmm4, %xmm2
        movapd    %xmm7, %xmm4
        addsd     %xmm6, %xmm7
        mulpd     %xmm3, %xmm1
        psrlq     $27, %xmm7
        psllq     $27, %xmm7
        movsd     1288(%ebx), %xmm3
        subsd     %xmm7, %xmm4
        mulpd     %xmm1, %xmm2
        addsd     %xmm4, %xmm6
        pshufd    $238, %xmm2, %xmm1
        addsd     %xmm2, %xmm6
        andpd     %xmm0, %xmm3
        movapd    %xmm0, %xmm4
        addsd     %xmm6, %xmm1
        subsd     %xmm3, %xmm0
        addsd     %xmm5, %xmm1
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm0
        mulsd     %xmm1, %xmm4
        addsd     %xmm4, %xmm0
        addsd     %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_15.0.3:
        cmpl      $16, %eax
        jae       .L_2TAG_PACKET_3.0.3
        movapd    %xmm0, %xmm2
        movd      %xmm0, %eax
        psrlq     $31, %xmm2
        movd      %xmm2, %ecx
        orl       %ecx, %eax
        je        .L_2TAG_PACKET_3.0.3
        movl      $16, %edx
        xorpd     %xmm1, %xmm1
        pinsrw    $3, %edx, %xmm1
        mulsd     %xmm1, %xmm1
        movl      $42, %edx
        jmp       .L_2TAG_PACKET_8.0.3
.L_2TAG_PACKET_0.0.3:
        cmpl      $0, %eax
        jl        .L_2TAG_PACKET_14.0.3
        movl      132(%esp), %eax
        cmpl      $1083179008, %eax
        jge       .L_2TAG_PACKET_10.0.3
        cmpl      $-1048576, %eax
        jae       .L_2TAG_PACKET_11.0.3
.L_2TAG_PACKET_6.0.3:
        xorpd     %xmm0, %xmm0
        movl      $49136, %eax
        pinsrw    $3, %eax, %xmm0
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_4.0.3:
        movsd     %xmm0, 48(%esp)
        fldl      48(%esp)
.L_2TAG_PACKET_9.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	expm1,@function
	.size	expm1,.-expm1
	.data
# -- End  expm1
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
	.long	4293918720
	.long	0
	.long	4293918720
	.long	4294967232
	.long	0
	.long	4294967232
	.long	0
	.long	65472
	.long	0
	.long	65472
	.long	0
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	1697350398
	.long	1079448903
	.long	1697350398
	.long	1079448903
	.long	4277796864
	.long	1065758274
	.long	4277796864
	.long	1065758274
	.long	3164486458
	.long	1025308570
	.long	3164486458
	.long	1025308570
	.long	1963358694
	.long	1065423121
	.long	1431655765
	.long	1069897045
	.long	1431655765
	.long	1067799893
	.long	0
	.long	1071644672
	.long	381774871
	.long	1062650220
	.long	381774871
	.long	1062650220
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1000070955
	.long	1042145304
	.long	1040187392
	.long	11418
	.long	988267849
	.long	1039500660
	.long	3539992576
	.long	22960
	.long	36755401
	.long	1042114290
	.long	402653184
	.long	34629
	.long	3634769483
	.long	1042178627
	.long	1820327936
	.long	46424
	.long	2155991225
	.long	1041560680
	.long	847249408
	.long	58348
	.long	2766913307
	.long	1039293264
	.long	3489660928
	.long	70401
	.long	3651174602
	.long	1040488175
	.long	2927624192
	.long	82586
	.long	3073892131
	.long	1042240606
	.long	1006632960
	.long	94904
	.long	1328391742
	.long	1042019037
	.long	3942645760
	.long	107355
	.long	2650893825
	.long	1041903210
	.long	822083584
	.long	119943
	.long	2397289153
	.long	1041802037
	.long	2281701376
	.long	132667
	.long	430997175
	.long	1042110606
	.long	1845493760
	.long	145530
	.long	1230936525
	.long	1041801015
	.long	1702887424
	.long	158533
	.long	740675935
	.long	1040178913
	.long	4110417920
	.long	171677
	.long	3489810261
	.long	1041825986
	.long	2793406464
	.long	184965
	.long	2532600530
	.long	1040767882
	.long	167772160
	.long	198398
	.long	3542557060
	.long	1041827263
	.long	2986344448
	.long	211976
	.long	1401563777
	.long	1041061093
	.long	922746880
	.long	225703
	.long	3129406026
	.long	1041852413
	.long	880803840
	.long	239579
	.long	900993572
	.long	1039283234
	.long	1275068416
	.long	253606
	.long	2115029358
	.long	1042140042
	.long	562036736
	.long	267786
	.long	1086643152
	.long	1041785419
	.long	1610612736
	.long	282120
	.long	82864366
	.long	1041256244
	.long	3045064704
	.long	296610
	.long	2392968152
	.long	1040913683
	.long	3573547008
	.long	311258
	.long	2905856183
	.long	1040002214
	.long	1988100096
	.long	326066
	.long	3742008261
	.long	1040011137
	.long	1451229184
	.long	341035
	.long	863393794
	.long	1040880621
	.long	914358272
	.long	356167
	.long	1446136837
	.long	1041372426
	.long	3707764736
	.long	371463
	.long	927855201
	.long	1040617636
	.long	360710144
	.long	386927
	.long	1492679939
	.long	1041050306
	.long	2952790016
	.long	402558
	.long	608827001
	.long	1041582217
	.long	2181038080
	.long	418360
	.long	606260204
	.long	1042271987
	.long	1711276032
	.long	434334
	.long	3163044019
	.long	1041843851
	.long	1006632960
	.long	450482
	.long	4148747325
	.long	1041962972
	.long	3900702720
	.long	466805
	.long	802924201
	.long	1041275378
	.long	1442840576
	.long	483307
	.long	3052749833
	.long	1041940577
	.long	1937768448
	.long	499988
	.long	2216116399
	.long	1041486744
	.long	914358272
	.long	516851
	.long	2729697836
	.long	1041445764
	.long	2566914048
	.long	533897
	.long	540608356
	.long	1041310907
	.long	2600468480
	.long	551129
	.long	2916344493
	.long	1040535661
	.long	1107296256
	.long	568549
	.long	731391814
	.long	1039497014
	.long	2566914048
	.long	586158
	.long	1024722704
	.long	1041461625
	.long	2961178624
	.long	603959
	.long	3806831748
	.long	1041732499
	.long	2675965952
	.long	621954
	.long	238953304
	.long	1040316488
	.long	2189426688
	.long	640145
	.long	749123235
	.long	1041725785
	.long	2063597568
	.long	658534
	.long	1168187977
	.long	1041175214
	.long	2986344448
	.long	677123
	.long	3506096399
	.long	1042186095
	.long	1426063360
	.long	695915
	.long	1470221620
	.long	1041675499
	.long	2566914048
	.long	714911
	.long	3182425146
	.long	1041483134
	.long	3087007744
	.long	734114
	.long	3131698208
	.long	1042208657
	.long	4068474880
	.long	753526
	.long	2300504125
	.long	1041428596
	.long	2415919104
	.long	773150
	.long	2290297931
	.long	1037388400
	.long	3716153344
	.long	792987
	.long	3532148223
	.long	1041626194
	.long	771751936
	.long	813041
	.long	1161884404
	.long	1042015258
	.long	3699376128
	.long	833312
	.long	876383176
	.long	1037968878
	.long	1241513984
	.long	853805
	.long	3379986796
	.long	1042213153
	.long	3699376128
	.long	874520
	.long	1545797737
	.long	1041681569
	.long	58720256
	.long	895462
	.long	2925146801
	.long	1042212567
	.long	855638016
	.long	916631
	.long	1316627971
	.long	1038516204
	.long	3883925504
	.long	938030
	.long	3267869137
	.long	1040337004
	.long	2726297600
	.long	959663
	.long	3720868999
	.long	1041782409
	.long	3992977408
	.long	981531
	.long	433316142
	.long	1041994064
	.long	1526726656
	.long	1003638
	.long	781232103
	.long	1040093400
	.long	2172649472
	.long	1025985
	.long	2773927732
	.long	1053236707
	.long	381774871
	.long	1062650220
	.long	379653899
	.long	1056571845
	.long	286331153
	.long	1065423121
	.long	436314138
	.long	1059717536
	.long	1431655765
	.long	1067799893
	.long	1431655765
	.long	1069897045
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	4294967295
	.long	2146435071
	.long	0
	.long	1048576
	.long	4227858432
	.long	4294967295
	.type	static_const_table,@object
	.size	static_const_table,1296
	.data
	.section .note.GNU-stack, ""
# End
