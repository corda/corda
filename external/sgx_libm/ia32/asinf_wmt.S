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
	.file "asinf_wmt.c"
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
# -- Begin  asinf
	.text
       .align    16,0x90
	.globl asinf
asinf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 40(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     112(%esp), %xmm0
        cvtps2pd  %xmm0, %xmm1
        movd      %xmm0, %eax
        movsd     1040(%ebx), %xmm3
        movl      $2139095040, %ecx
        movl      $8388608, %edx
        andl      %eax, %ecx
        subl      $1015021568, %ecx
        cmpl      $50331648, %ecx
        jae       .L_2TAG_PACKET_0.0.3
        mulsd     %xmm1, %xmm1
        cvtps2pd  %xmm0, %xmm2
        shrl      $23, %ecx
        addl      $1, %ecx
        shrl      %cl, %edx
        subsd     %xmm1, %xmm3
        orl       %edx, %eax
        negl      %edx
        andl      %edx, %eax
        movd      %eax, %xmm4
        sqrtsd    %xmm3, %xmm3
        addl      %edx, %edx
        andl      $16777215, %edx
        andl      %edx, %eax
        negl      %ecx
        addl      $24, %ecx
        orl       $8388608, %eax
        shrl      %cl, %eax
        addl      %eax, %eax
        movsd     (%ebx,%eax,8), %xmm5
        psrlq     $31, %xmm0
        movsd     8(%ebx,%eax,8), %xmm1
        psllq     $63, %xmm0
        cvtps2pd  %xmm4, %xmm4
        mulsd     %xmm5, %xmm2
        orpd      %xmm1, %xmm0
        movsd     1056(%ebx), %xmm5
        movsd     1072(%ebx), %xmm1
        mulsd     %xmm3, %xmm4
        movsd     1088(%ebx), %xmm3
        subsd     %xmm4, %xmm2
        pshufd    $68, %xmm2, %xmm4
        mulsd     %xmm2, %xmm2
        mulsd     %xmm4, %xmm5
        mulsd     %xmm4, %xmm1
        mulsd     %xmm4, %xmm3
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        addsd     %xmm5, %xmm3
        addsd     %xmm1, %xmm4
        mulsd     %xmm3, %xmm2
        addsd     %xmm4, %xmm0
        addsd     %xmm2, %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_0.0.3:
        jge       .L_2TAG_PACKET_2.0.3
        addl      $1015021568, %ecx
        cmpl      $964689920, %ecx
        jb        .L_2TAG_PACKET_3.0.3
        movsd     1072(%ebx), %xmm2
        movapd    %xmm1, %xmm0
        movsd     1088(%ebx), %xmm3
        mulsd     %xmm1, %xmm1
        mulsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm1
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        mulsd     %xmm3, %xmm1
        addsd     %xmm2, %xmm0
        addsd     %xmm0, %xmm1
        cvtpd2ps  %xmm1, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_3.0.3:
        movsd     1104(%ebx), %xmm5
        mulsd     %xmm5, %xmm1
        cvtpd2ps  %xmm1, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_2.0.3:
        addl      %eax, %eax
        cmpl      $2130706432, %eax
        jne       .L_2TAG_PACKET_4.0.3
        movapd    1024(%ebx), %xmm0
        psrlq     $63, %xmm1
        psllq     $63, %xmm1
        orpd      %xmm1, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_4.0.3:
        cmpl      $-16777216, %eax
        ja        .L_2TAG_PACKET_5.0.3
        xorps     %xmm0, %xmm0
        movl      $2139095040, %edx
        movd      %edx, %xmm1
        mulss     %xmm1, %xmm0
        movss     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       112(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $62, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        flds      (%esp)
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_5.0.3:
        addss     %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
.L_2TAG_PACKET_1.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	asinf,@function
	.size	asinf,.-asinf
	.data
# -- End  asinf
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
	.long	4290772864
	.long	1072693183
	.long	2864569868
	.long	1065353226
	.long	3955135328
	.long	1072692671
	.long	152932388
	.long	1066926224
	.long	1671525387
	.long	1072691647
	.long	2415589551
	.long	1067712845
	.long	2799290704
	.long	1072690109
	.long	3864800403
	.long	1068237715
	.long	2477707161
	.long	1072688057
	.long	725093587
	.long	1068630990
	.long	2787892653
	.long	1072685489
	.long	2536557765
	.long	1068893940
	.long	4138691444
	.long	1072682403
	.long	62552236
	.long	1069157247
	.long	941089134
	.long	1072678798
	.long	165833141
	.long	1069420976
	.long	3045724825
	.long	1072674669
	.long	2611259588
	.long	1069616357
	.long	1338788729
	.long	1072670015
	.long	2616012614
	.long	1069748746
	.long	2049955026
	.long	1072664831
	.long	11397492
	.long	1069881450
	.long	922854000
	.long	1072659114
	.long	2157235516
	.long	1070014503
	.long	326738075
	.long	1072652859
	.long	2788743992
	.long	1070147943
	.long	589211462
	.long	1072646061
	.long	3920198733
	.long	1070281807
	.long	4203970809
	.long	1072638714
	.long	3416546056
	.long	1070416134
	.long	2847908959
	.long	1072630814
	.long	4071425947
	.long	1070550963
	.long	3041144817
	.long	1072622353
	.long	623140044
	.long	1070641216
	.long	254499552
	.long	1072613325
	.long	307519074
	.long	1070709195
	.long	247322296
	.long	1072603721
	.long	2945179265
	.long	1070777488
	.long	1734768715
	.long	1072593533
	.long	3301227932
	.long	1070846119
	.long	512284341
	.long	1072582753
	.long	148694291
	.long	1070915112
	.long	2150320922
	.long	1072571370
	.long	959165711
	.long	1070984490
	.long	307214965
	.long	1072559375
	.long	862254873
	.long	1071054280
	.long	2261822985
	.long	1072546755
	.long	303820077
	.long	1071124509
	.long	2878424532
	.long	1072533499
	.long	1279667396
	.long	1071195205
	.long	1489389340
	.long	1072519594
	.long	3626865975
	.long	1071266398
	.long	1561634021
	.long	1072505025
	.long	1381197995
	.long	1071338121
	.long	1731593006
	.long	1072489777
	.long	685577004
	.long	1071410406
	.long	3972360633
	.long	1072473833
	.long	3426639945
	.long	1071483288
	.long	231016948
	.long	1072457177
	.long	3858097791
	.long	1071556806
	.long	1951983052
	.long	1072439787
	.long	2522397359
	.long	1071631000
	.long	806900869
	.long	1072421644
	.long	1565390008
	.long	1071675292
	.long	2352519275
	.long	1072402724
	.long	2655228975
	.long	1071713130
	.long	3752547712
	.long	1072383003
	.long	3066292715
	.long	1071751375
	.long	1444584515
	.long	1072362455
	.long	933027783
	.long	1071790054
	.long	2960036461
	.long	1072341049
	.long	3589869088
	.long	1071829194
	.long	4214933698
	.long	1072318754
	.long	2973206241
	.long	1071868828
	.long	957213757
	.long	1072295536
	.long	2756129430
	.long	1071908989
	.long	4263431727
	.long	1072271354
	.long	2767348050
	.long	1071949714
	.long	4197201246
	.long	1072246168
	.long	874994578
	.long	1071991044
	.long	1821133236
	.long	1072219931
	.long	3810301671
	.long	1072033022
	.long	2102004657
	.long	1072192590
	.long	4064603671
	.long	1072075699
	.long	1957725919
	.long	1072164088
	.long	276553332
	.long	1072119130
	.long	3365782140
	.long	1072134360
	.long	1120469626
	.long	1072163374
	.long	4096932877
	.long	1072103334
	.long	4291572900
	.long	1072208500
	.long	177757517
	.long	1072070929
	.long	2376322938
	.long	1072254587
	.long	4029755771
	.long	1072037049
	.long	3060434712
	.long	1072301721
	.long	497611374
	.long	1072001591
	.long	4010165530
	.long	1072350003
	.long	3306304935
	.long	1071964429
	.long	388249839
	.long	1072399550
	.long	539435036
	.long	1071925423
	.long	165779504
	.long	1072450495
	.long	2497352827
	.long	1071884403
	.long	1343287957
	.long	1072502997
	.long	884164153
	.long	1071841172
	.long	1828157318
	.long	1072557245
	.long	3653025069
	.long	1071795489
	.long	2128712154
	.long	1072613466
	.long	3056817116
	.long	1071747064
	.long	1489283833
	.long	1072671938
	.long	1840990109
	.long	1071695534
	.long	2059782629
	.long	1072713127
	.long	3931971716
	.long	1071636206
	.long	1027831985
	.long	1072745180
	.long	3187721039
	.long	1071517691
	.long	3329524973
	.long	1072779038
	.long	3649449467
	.long	1071389244
	.long	2110923290
	.long	1072815090
	.long	4055205277
	.long	1071248617
	.long	2992048670
	.long	1072853879
	.long	2715653706
	.long	1071092488
	.long	419466969
	.long	1072896214
	.long	2030806678
	.long	1070915559
	.long	1069790871
	.long	1072943387
	.long	2084655462
	.long	1070708358
	.long	3811755941
	.long	1072997722
	.long	3549768261
	.long	1070304457
	.long	884757291
	.long	1073064302
	.long	4261330719
	.long	1069543419
	.long	3631318613
	.long	1073160613
	.long	1413754136
	.long	1073291771
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	3067833783
	.long	1067899757
	.long	0
	.long	0
	.long	1431655765
	.long	1069897045
	.long	0
	.long	0
	.long	858993459
	.long	1068708659
	.long	0
	.long	0
	.long	1
	.long	1072693248
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,1120
	.data
	.section .note.GNU-stack, ""
# End
